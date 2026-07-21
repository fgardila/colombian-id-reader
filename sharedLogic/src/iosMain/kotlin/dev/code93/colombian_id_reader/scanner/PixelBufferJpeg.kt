@file:OptIn(ExperimentalForeignApi::class)

package dev.code93.colombian_id_reader.scanner

import dev.code93.colombian_id_reader.scan.CropGeometry
import dev.code93.colombian_id_reader.scan.GateObservation
import dev.code93.colombian_id_reader.scan.ScanDebug
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreVideo.CVImageBufferRef
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.Foundation.NSData
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

/**
 * Encodes a camera frame as an upright, document-cropped JPEG
 * (ARCHITECTURE-1.0.0.md §4) — iOS twin of the Android JpegEncoder.
 * Called only on the capture queue while the CMSampleBuffer's pixel
 * buffer is valid (the pipeline is synchronous), and only for frames
 * that produced something (§4.2–4.3).
 *
 * A class, not an object: the [CIContext] is expensive and reusable,
 * and its lifetime should match the scan session's.
 */
internal class PixelBufferJpeg {

    private val context = CIContext()

    /**
     * [box] is the gate's document box in ORIENTED (portrait, y-down)
     * pixel coordinates — the space [VisionDetectors.documentQuad]
     * reports in; null crops nothing. Returns null on any failure:
     * image capture is best effort and must never break the data path.
     */
    fun encode(buffer: CVImageBufferRef?, box: GateObservation.Box?, quality: Double = 0.85): ByteArray? {
        if (buffer == null) return null
        return try {
            // The sensor delivers landscape; the scanning UI is portrait
            // (orientation .right everywhere in this pipeline).
            val orientedWidth = CVPixelBufferGetHeight(buffer).toInt()
            val orientedHeight = CVPixelBufferGetWidth(buffer).toInt()
            val rect = box?.let { CropGeometry.padded(it, orientedWidth, orientedHeight) }
                ?: CropGeometry.Rect(0, 0, orientedWidth, orientedHeight)

            // 6 == kCGImagePropertyOrientationRight, matching the Vision
            // requests — the image is upright after this.
            val oriented = CIImage(cVPixelBuffer = buffer).imageByApplyingOrientation(6)

            // CIImage is y-UP (bottom-left origin); the box is y-DOWN in
            // the oriented frame — the inverse of documentQuad's (1 - y).
            val cropRect = oriented.extent.useContents {
                CGRectMake(
                    origin.x + rect.left,
                    origin.y + (orientedHeight - rect.bottom),
                    rect.width.toDouble(),
                    rect.height.toDouble()
                )
            }
            val cgImage = context.createCGImage(oriented, fromRect = cropRect) ?: return null
            try {
                UIImageJPEGRepresentation(UIImage(cGImage = cgImage), quality)?.toByteArray()
            } finally {
                CGImageRelease(cgImage)
            }
        } catch (t: Throwable) {
            // Never the bytes, never the frame — just the failure (§7).
            ScanDebug.log { "jpeg encode failed: ${t::class.simpleName}: ${t.message}" }
            null
        }
    }
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}
