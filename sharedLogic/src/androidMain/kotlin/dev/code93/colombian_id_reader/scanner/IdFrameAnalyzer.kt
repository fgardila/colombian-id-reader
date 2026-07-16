package dev.code93.colombian_id_reader.scanner

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import dev.code93.colombian_id_reader.model.DetectorFilter
import dev.code93.colombian_id_reader.model.GateHint
import dev.code93.colombian_id_reader.model.ScanMode
import dev.code93.colombian_id_reader.model.acceptedFormats
import dev.code93.colombian_id_reader.model.ScannedDocument
import dev.code93.colombian_id_reader.model.ScanResult
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
 * Runs on a dedicated single-thread executor and blocks it until the
 * ML Kit tasks finish: with STRATEGY_KEEP_ONLY_LATEST that *is* the
 * throttling, and it guarantees the ImageProxy is only closed after
 * ML Kit is done with its buffer.
 */
internal class IdFrameAnalyzer(
    mode: ScanMode,
    filter: DetectorFilter,
    private val detectors: MlKitDetectors,
    private val onSuccess: (ScannedDocument) -> Unit,
    private val onHint: (GateHint) -> Unit = {},
    private val mrzThrottleMs: Long = 250
) : ImageAnalysis.Analyzer {

    val stats = GateStats()

    private val delivered = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastMrzAttemptAt = 0L
    private var lastHint: GateHint? = null

    private val gate = CaptureGate(
        accepts = mode.acceptedFormats,
        stats = stats
    )

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

            val verdict = gate.evaluate(observe(proxy, input, rotation))
            emitHint(verdict.hint)
            stats.onOcrDecision(verdict.pass)

            val result = runBlocking { router.process(input, allowOcr = verdict.pass) }
            if (result is ScanResult.Success && delivered.compareAndSet(false, true)) {
                ScanDebug.log { stats.summary() }
                mainHandler.post { onSuccess(result.data) }
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
