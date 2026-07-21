package dev.code93.colombian_id_reader.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import dev.code93.colombian_id_reader.scan.CropGeometry
import dev.code93.colombian_id_reader.scan.GateObservation
import dev.code93.colombian_id_reader.scan.ScanDebug
import java.io.ByteArrayOutputStream

/**
 * Encodes a camera frame as an upright, document-cropped JPEG
 * (ARCHITECTURE-1.0.0.md §4). Called only inside `analyze()` while the
 * [ImageProxy] is still open — the pipeline is synchronous, so the frame
 * the recognizer succeeded on is literally in hand (no retention buffer,
 * zero cost on every other frame, §4.2–4.3).
 */
internal object JpegEncoder {

    /**
     * [box] is the gate's document box in ROTATED (upright) coordinates;
     * null crops nothing (full frame). Returns null on any failure —
     * image capture is best effort and must never break the data path.
     */
    fun encode(proxy: ImageProxy, box: GateObservation.Box?, quality: Int = 85): ByteArray? =
        try {
            val rotation = proxy.imageInfo.rotationDegrees
            val swapped = rotation == 90 || rotation == 270
            val frameWidth = if (swapped) proxy.height else proxy.width
            val frameHeight = if (swapped) proxy.width else proxy.height

            val rotatedRect = box?.let { CropGeometry.padded(it, frameWidth, frameHeight) }
                ?: CropGeometry.Rect(0, 0, frameWidth, frameHeight)
            val crop = CropGeometry.toUnrotated(rotatedRect, rotation, proxy.width, proxy.height)

            val source = proxy.toBitmap() // unrotated RGB decode of the YUV frame
            val upright = Bitmap.createBitmap(
                source, crop.left, crop.top, crop.width, crop.height,
                Matrix().apply { postRotate(rotation.toFloat()) },
                /* filter = */ true
            )
            val stream = ByteArrayOutputStream()
            upright.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            if (upright !== source) upright.recycle()
            source.recycle()
            stream.toByteArray()
        } catch (t: Throwable) {
            // Never the bytes, never the frame — just the failure (§7).
            ScanDebug.log { "jpeg encode failed: ${t::class.simpleName}: ${t.message}" }
            null
        }
}
