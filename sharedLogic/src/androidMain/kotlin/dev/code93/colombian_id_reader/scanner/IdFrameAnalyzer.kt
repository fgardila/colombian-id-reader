package dev.code93.colombian_id_reader.scanner

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import dev.code93.colombian_id_reader.model.DetectorFilter
import dev.code93.colombian_id_reader.model.GateHint
import dev.code93.colombian_id_reader.model.ScanCapture
import dev.code93.colombian_id_reader.model.ScanMode
import dev.code93.colombian_id_reader.model.acceptedFormats
import dev.code93.colombian_id_reader.model.ScanResult
import dev.code93.colombian_id_reader.scan.CaptureFlowController
import dev.code93.colombian_id_reader.scan.CaptureGate
import dev.code93.colombian_id_reader.scan.GateObservation
import dev.code93.colombian_id_reader.scan.GateStats
import dev.code93.colombian_id_reader.scan.ScanDebug
import dev.code93.colombian_id_reader.scan.ScanFrameRouter
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX analyzer implementing the two-stage capture pipeline
 * (ARCHITECTURE-0.2.0.md §4): the capture gate evaluates every frame
 * (object present, framed, steady, in focus → UX hint) and only an open
 * gate lets the expensive OCR leg run; the PDF417 leg stays hot — it is
 * cheap and self-validating.
 *
 * With [captureImages] (ARCHITECTURE-1.0.0.md §5) the scan becomes a
 * two-side flow: a FRONT phase driven by the gate alone (retain frame +
 * best-effort OCR, then flip guidance via [onPhase]), then the BACK
 * phase above. Since `analyze()` is synchronous, the winning frame is
 * still open when the parser succeeds — the JPEG is encoded right
 * there, only ever for frames that produced something (§4.2–4.3).
 *
 * Runs on a dedicated single-thread executor and blocks it until the
 * ML Kit tasks finish: with STRATEGY_KEEP_ONLY_LATEST that *is* the
 * throttling, and it guarantees the ImageProxy is only closed after
 * ML Kit is done with its buffer.
 */
internal class IdFrameAnalyzer(
    private val mode: ScanMode,
    filter: DetectorFilter,
    private val detectors: MlKitDetectors,
    captureImages: Boolean = false,
    private val onSuccess: (ScanCapture) -> Unit,
    private val onHint: (GateHint) -> Unit = {},
    private val onPhase: (CaptureFlowController.Phase) -> Unit = {},
    private val mrzThrottleMs: Long = 250
) : ImageAnalysis.Analyzer {

    val stats = GateStats()

    private val delivered = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastMrzAttemptAt = 0L
    private var lastHint: GateHint? = null

    // Passports stay data-page only (1.0.0 scope): no image capture.
    private val effectiveCapture = captureImages && mode == ScanMode.ColombianId

    init {
        if (captureImages && !effectiveCapture) {
            ScanDebug.log { "captureImages ignored: passport mode is data-only (1.0.0 §3)" }
        }
    }

    private val flow = CaptureFlowController(effectiveCapture)

    // Streak/tracking state belongs to one side: fresh gate per phase.
    private var gate = newGate()

    private fun newGate() = CaptureGate(accepts = mode.acceptedFormats, stats = stats)

    private val router = ScanFrameRouter<InputImage>(
        mode = mode,
        filter = filter,
        pdf417 = detectors::pdf417,
        mrzOcr = { image ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastMrzAttemptAt < mrzThrottleMs) {
                emptyList()
            } else {
                lastMrzAttemptAt = now
                detectors.mrzLines(image)
            }
        }
    )

    override fun analyze(proxy: ImageProxy) {
        if (delivered.get()) {
            proxy.close()
            return
        }
        try {
            val mediaImage = proxy.image ?: return
            val rotation = proxy.imageInfo.rotationDegrees
            val input = InputImage.fromMediaImage(mediaImage, rotation)

            val observation = observe(proxy, input, rotation)
            val verdict = gate.evaluate(observation)
            emitHint(verdict.hint)

            if (flow.phase == CaptureFlowController.Phase.FRONT) {
                if (flow.shouldCaptureFront(verdict)) {
                    // Same frame for image and OCR: proxy is still open.
                    val frontJpeg = JpegEncoder.encode(proxy, observation.bbox)
                    val frontLines = runBlocking { detectors.mrzLines(input) }
                    flow.onFrontCaptured(frontJpeg, frontLines)
                    gate = newGate()
                    mainHandler.post {
                        if (!delivered.get()) onPhase(CaptureFlowController.Phase.BACK)
                    }
                }
                return // front frames never reach the recognizers
            }

            stats.onOcrDecision(verdict.pass)
            val result = runBlocking { router.process(input, allowOcr = verdict.pass) }
            if (result is ScanResult.Success && delivered.compareAndSet(false, true)) {
                // Encode before analyze() returns — this IS the frame
                // the data came from (§4.1), open until the finally.
                val backJpeg =
                    if (effectiveCapture) JpegEncoder.encode(proxy, observation.bbox) else null
                ScanDebug.log { stats.summary() }
                val capture = flow.assemble(result.data, backJpeg)
                mainHandler.post { onSuccess(capture) }
            }
            // Error / null: expected on partial frames — keep scanning.
        } finally {
            proxy.close()
        }
    }

    private fun observe(proxy: ImageProxy, input: InputImage, rotation: Int): GateObservation {
        val detected = runBlocking { detectors.detectObject(input) }
        val plane = proxy.planes[0]
        val sharpness = LumaSharpness.measureCenter(
            plane.buffer, plane.rowStride, plane.pixelStride, proxy.width, proxy.height
        )
        // ML Kit boxes live in the ROTATED coordinate space.
        val rotated = rotation == 90 || rotation == 270
        val frameWidth = if (rotated) proxy.height else proxy.width
        val frameHeight = if (rotated) proxy.width else proxy.height
        return GateObservation(
            timestampMs = SystemClock.elapsedRealtime(),
            frameWidth = frameWidth.toFloat(),
            frameHeight = frameHeight.toFloat(),
            bbox = detected?.boundingBox?.let {
                GateObservation.Box(
                    it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()
                )
            },
            sharpness = sharpness,
            trackingId = detected?.trackingId
        )
    }

    private fun emitHint(hint: GateHint) {
        if (hint == lastHint) return
        lastHint = hint
        ScanDebug.log { "gate hint -> $hint" }
        mainHandler.post {
            if (!delivered.get()) onHint(hint)
        }
    }
}
