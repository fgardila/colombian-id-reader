package dev.code93.colombian_id_reader.scanner

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import dev.code93.colombian_id_reader.model.IdCardData
import dev.code93.colombian_id_reader.model.ScanMode
import dev.code93.colombian_id_reader.model.ScanResult
import dev.code93.colombian_id_reader.scan.ScanFrameRouter
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX analyzer that feeds each frame through [ScanFrameRouter].
 *
 * Runs on a dedicated single-thread executor and blocks it until the
 * ML Kit tasks finish: with STRATEGY_KEEP_ONLY_LATEST that *is* the
 * throttling — CameraX drops intermediate frames and hands over the
 * freshest one when the thread frees up. Blocking also guarantees the
 * ImageProxy is only closed after ML Kit is done with its buffer.
 *
 * The heavy text recognizer gets an extra time gate so AUTO keeps the
 * cheap barcode detector hot without cooking the device.
 */
internal class IdFrameAnalyzer(
    mode: ScanMode,
    private val detectors: MlKitDetectors,
    private val onSuccess: (IdCardData) -> Unit,
    private val mrzThrottleMs: Long = 250
) : ImageAnalysis.Analyzer {

    private val delivered = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastMrzAttemptAt = 0L

    private val router = ScanFrameRouter<InputImage>(
        mode = mode,
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
            val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
            val result = runBlocking { router.process(input) }
            if (result is ScanResult.Success && delivered.compareAndSet(false, true)) {
                mainHandler.post { onSuccess(result.data) }
            }
            // Error / null: expected on partial frames — keep scanning.
        } finally {
            proxy.close()
        }
    }
}
