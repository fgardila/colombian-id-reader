@file:OptIn(ExperimentalForeignApi::class)

package dev.code93.colombian_id_reader.scanner

import dev.code93.colombian_id_reader.ColombianIdParser
import dev.code93.colombian_id_reader.model.DetectorFilter
import dev.code93.colombian_id_reader.model.GateHint
import dev.code93.colombian_id_reader.model.ScanCapture
import dev.code93.colombian_id_reader.model.ScanMode
import dev.code93.colombian_id_reader.model.acceptedFormats
import dev.code93.colombian_id_reader.model.ScannedDocument
import dev.code93.colombian_id_reader.model.ScanResult
import dev.code93.colombian_id_reader.scan.CaptureFlowController
import dev.code93.colombian_id_reader.scan.CaptureGate
import dev.code93.colombian_id_reader.scan.GateObservation
import dev.code93.colombian_id_reader.scan.GateStats
import dev.code93.colombian_id_reader.scan.ScanDebug
import dev.code93.colombian_id_reader.scan.ScanFrameRouter
import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVImageBufferRef
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.Foundation.NSProcessInfo
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Capture delegate implementing the two-stage pipeline on iOS
 * (ARCHITECTURE-0.2.0.md §4): the gate evaluates each VIDEO frame
 * (document quad + sharpness → hint) and only an open gate lets the
 * expensive OCR leg run. The PDF417 path via AVCaptureMetadataOutput is
 * an independent, hardware-assisted callback and deliberately stays
 * hot: barcode decoding is cheap and self-validating, and gating it
 * would only add latency to the cédula amarilla.
 *
 * With [captureImages] (ARCHITECTURE-1.0.0.md §5) the scan is a
 * two-side flow (FRONT gate-driven phase, then BACK). The pipeline is
 * synchronous, so the winning frame's pixel buffer is in hand when the
 * parser succeeds — except on the metadata (PDF417) leg, which carries
 * no pixels: there the parsed document is stashed and the NEXT video
 * frame (~33 ms later, same scene — the barcode was just decoded, so
 * the card is in view) supplies the back image and completes delivery.
 * The session keeps running until that delivery, so the frame is
 * guaranteed; cancelling first means nothing is delivered, exactly as
 * today.
 *
 * Both delegates run on the SAME serial capture queue (IdCaptureSession
 * wires them so), which is why the phase/pending state needs no locks.
 * Frames are processed synchronously on that queue — that is both the
 * backpressure model and the CMSampleBuffer lifetime guarantee (the
 * buffer is only valid during the callback).
 */
internal class IdFrameProcessor(
    private val mode: ScanMode,
    private val filter: DetectorFilter,
    private val detectors: VisionDetectors,
    captureImages: Boolean = false,
    private val onSuccess: (ScanCapture) -> Unit,
    private val onHint: (GateHint) -> Unit = {},
    private val onPhase: (CaptureFlowController.Phase) -> Unit = {},
    private val mrzThrottleMs: Long = 250
) : NSObject(),
    AVCaptureVideoDataOutputSampleBufferDelegateProtocol,
    AVCaptureMetadataOutputObjectsDelegateProtocol {

    val stats = GateStats()

    private val delivered = AtomicInt(0)
    private var lastMrzAttemptAt = 0L // capture queue only
    private var lastHint: GateHint? = null

    // Passports stay data-page only (1.0.0 scope): no image capture.
    private val effectiveCapture = captureImages && mode == ScanMode.ColombianId

    init {
        if (captureImages && !effectiveCapture) {
            ScanDebug.log { "captureImages ignored: passport mode is data-only (1.0.0 §3)" }
        }
    }

    private val flow = CaptureFlowController(effectiveCapture)
    private val jpeg by lazy { PixelBufferJpeg() }

    // Capture-queue state for the metadata leg's deferred delivery and
    // for cropping a frame the detectors haven't measured.
    private var pendingBackDocument: ScannedDocument? = null
    private var lastBox: GateObservation.Box? = null

    // Streak/tracking state belongs to one side: fresh gate per phase.
    private var gate = newGate()

    private fun newGate() = CaptureGate(accepts = mode.acceptedFormats, stats = stats)

    private val router = ScanFrameRouter<CVImageBufferRef>(
        mode = mode,
        filter = filter,
        pdf417 = { detectors.pdf417(it) },
        mrzOcr = { buffer ->
            val now = uptimeMs()
            if (now - lastMrzAttemptAt < mrzThrottleMs) {
                emptyList()
            } else {
                lastMrzAttemptAt = now
                detectors.mrzLines(buffer)
            }
        }
    )

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection
    ) {
        if (delivered.value != 0) return
        val pixelBuffer = didOutputSampleBuffer?.let { CMSampleBufferGetImageBuffer(it) } ?: return

        // A barcode won on the metadata leg: this frame exists to supply
        // the back image (the metadata callback has no pixels).
        pendingBackDocument?.let { document ->
            if (delivered.compareAndSet(0, 1)) {
                val box = lastBox ?: observe(pixelBuffer).let { it.quad?.boundingBox() }
                val backJpeg = jpeg.encode(pixelBuffer, box)
                ScanDebug.log { stats.summary() }
                val capture = flow.assemble(document, backJpeg)
                dispatch_async(dispatch_get_main_queue()) { onSuccess(capture) }
            }
            return
        }

        val observation = observe(pixelBuffer)
        lastBox = observation.quad?.boundingBox() ?: lastBox
        val verdict = gate.evaluate(observation)
        emitHint(verdict.hint)

        if (flow.phase == CaptureFlowController.Phase.FRONT) {
            if (flow.shouldCaptureFront(verdict)) {
                // Same frame for image and OCR: the buffer is valid
                // for the duration of this callback.
                val frontJpeg = jpeg.encode(pixelBuffer, observation.quad?.boundingBox())
                val frontLines = detectors.mrzLines(pixelBuffer)
                flow.onFrontCaptured(frontJpeg, frontLines)
                gate = newGate()
                lastBox = null
                dispatch_async(dispatch_get_main_queue()) {
                    if (delivered.value == 0) onPhase(CaptureFlowController.Phase.BACK)
                }
            }
            return // front frames never reach the recognizers
        }

        stats.onOcrDecision(verdict.pass)
        val result = runBlocking { router.process(pixelBuffer, allowOcr = verdict.pass) }
        if (result is ScanResult.Success && delivered.compareAndSet(0, 1)) {
            // Encode inside the callback — this IS the frame the data
            // came from (§4.1), valid only until it returns.
            val backJpeg = if (effectiveCapture) {
                jpeg.encode(pixelBuffer, observation.quad?.boundingBox())
            } else {
                null
            }
            ScanDebug.log { stats.summary() }
            val capture = flow.assemble(result.data, backJpeg)
            dispatch_async(dispatch_get_main_queue()) { onSuccess(capture) }
        }
        // Error / null: expected on partial frames — keep scanning.
    }

    /**
     * PDF417 via AVCaptureMetadataOutput — the primary barcode path on
     * iOS: Vision's PDF417 decoder proved unable to lock onto the
     * cédula's dense barcode on real cards, while AVFoundation's
     * metadata detector (the boarding-pass reader) handles it.
     */
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        if (delivered.value != 0 || pendingBackDocument != null) return
        if (mode != ScanMode.ColombianId || filter == DetectorFilter.MRZ_ONLY) return
        // The barcode is on the BACK: during the front phase this read
        // would capture the wrong side — the flip guidance is coming.
        if (flow.phase == CaptureFlowController.Phase.FRONT) return
        val raw = didOutputMetadataObjects
            .filterIsInstance<AVMetadataMachineReadableCodeObject>()
            .firstNotNullOfOrNull { it.stringValue }
            ?: return
        val result = ColombianIdParser.parsePdf417(raw)
        if (result !is ScanResult.Success) return

        if (!effectiveCapture) {
            if (delivered.compareAndSet(0, 1)) {
                ScanDebug.log { stats.summary() }
                val capture = flow.assemble(result.data, backJpeg = null)
                dispatch_async(dispatch_get_main_queue()) { onSuccess(capture) }
            }
            return
        }
        // No pixels in this callback: defer to the next video frame.
        pendingBackDocument = result.data
    }

    private fun observe(buffer: CVImageBufferRef): GateObservation {
        // Orientation .right: the oriented (portrait) frame swaps the
        // buffer's landscape dimensions.
        val frameWidth = CVPixelBufferGetHeight(buffer).toFloat()
        val frameHeight = CVPixelBufferGetWidth(buffer).toFloat()
        return GateObservation(
            timestampMs = uptimeMs(),
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            quad = detectors.documentQuad(buffer, frameWidth, frameHeight),
            sharpness = LumaSharpness.measureCenter(buffer)
        )
    }

    private fun emitHint(hint: GateHint) {
        if (hint == lastHint) return
        lastHint = hint
        ScanDebug.log { "gate hint -> $hint" }
        dispatch_async(dispatch_get_main_queue()) {
            if (delivered.value == 0) onHint(hint)
        }
    }

    private fun uptimeMs(): Long =
        (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
}
