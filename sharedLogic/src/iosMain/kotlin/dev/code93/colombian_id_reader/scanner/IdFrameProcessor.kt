@file:OptIn(ExperimentalForeignApi::class)

package dev.code93.colombian_id_reader.scanner

import dev.code93.colombian_id_reader.ColombianIdParser
import dev.code93.colombian_id_reader.model.DetectorFilter
import dev.code93.colombian_id_reader.model.DocumentFormat
import dev.code93.colombian_id_reader.model.GateHint
import dev.code93.colombian_id_reader.model.ScannedDocument
import dev.code93.colombian_id_reader.model.ScanResult
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
 * Frames are processed synchronously on the serial capture queue —
 * that is both the backpressure model and the CMSampleBuffer lifetime
 * guarantee (the buffer is only valid during the callback).
 */
internal class IdFrameProcessor(
    private val filter: DetectorFilter,
    private val detectors: VisionDetectors,
    private val onSuccess: (ScannedDocument) -> Unit,
    private val onHint: (GateHint) -> Unit = {},
    private val mrzThrottleMs: Long = 250
) : NSObject(),
    AVCaptureVideoDataOutputSampleBufferDelegateProtocol,
    AVCaptureMetadataOutputObjectsDelegateProtocol {

    val stats = GateStats()

    private val delivered = AtomicInt(0)
    private var lastMrzAttemptAt = 0L // capture queue only
    private var lastHint: GateHint? = null

    private val gate = CaptureGate(
        accepts = setOf(DocumentFormat.Id1), // ScanMode.ColombianId geometry
        stats = stats
    )

    private val router = ScanFrameRouter<CVImageBufferRef>(
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

        val verdict = gate.evaluate(observe(pixelBuffer))
        emitHint(verdict.hint)
        stats.onOcrDecision(verdict.pass)

        val result = runBlocking { router.process(pixelBuffer, allowOcr = verdict.pass) }
        deliverIfSuccess(result)
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
        if (delivered.value != 0 || filter == DetectorFilter.MRZ_ONLY) return
        val raw = didOutputMetadataObjects
            .filterIsInstance<AVMetadataMachineReadableCodeObject>()
            .firstNotNullOfOrNull { it.stringValue }
            ?: return
        deliverIfSuccess(ColombianIdParser.parsePdf417(raw))
    }

    private fun deliverIfSuccess(result: ScanResult?) {
        if (result is ScanResult.Success && delivered.compareAndSet(0, 1)) {
            ScanDebug.log { stats.summary() }
            dispatch_async(dispatch_get_main_queue()) { onSuccess(result.data) }
        }
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
