@file:OptIn(ExperimentalForeignApi::class)

package dev.code93.colombian_id_reader.scanner

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import platform.CoreVideo.CVImageBufferRef
import platform.CoreVideo.CVPixelBufferGetBaseAddressOfPlane
import platform.CoreVideo.CVPixelBufferGetBytesPerRowOfPlane
import platform.CoreVideo.CVPixelBufferGetHeightOfPlane
import platform.CoreVideo.CVPixelBufferGetWidthOfPlane
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly

/**
 * Focus signal for the capture gate — the iOS twin of the Android
 * LumaSharpness: variance of a subsampled 3x3 Laplacian over the center
 * region of the luma plane (420 BiPlanar plane 0), on a fixed ~96x60
 * grid so the cost is independent of the 4K analysis resolution. Same
 * algorithm on both platforms so GateThresholds.minSharpness is one
 * number.
 */
internal object LumaSharpness {

    private const val GRID_X = 96
    private const val GRID_Y = 60
    private const val CENTER_FRACTION = 0.6

    fun measureCenter(buffer: CVImageBufferRef): Float {
        CVPixelBufferLockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly)
        try {
            val base = CVPixelBufferGetBaseAddressOfPlane(buffer, 0u) ?: return 0f
            val rowStride = CVPixelBufferGetBytesPerRowOfPlane(buffer, 0u).toInt()
            val width = CVPixelBufferGetWidthOfPlane(buffer, 0u).toInt()
            val height = CVPixelBufferGetHeightOfPlane(buffer, 0u).toInt()
            val plane = base.reinterpret<ByteVar>()

            val regionWidth = (width * CENTER_FRACTION).toInt()
            val regionHeight = (height * CENTER_FRACTION).toInt()
            val left = (width - regionWidth) / 2
            val top = (height - regionHeight) / 2
            val stepX = maxOf(1, regionWidth / GRID_X)
            val stepY = maxOf(1, regionHeight / GRID_Y)

            fun luma(x: Int, y: Int): Int = plane[y * rowStride + x].toInt() and 0xFF

            var sum = 0.0
            var sumSq = 0.0
            var count = 0
            var y = top + stepY
            while (y < top + regionHeight - stepY) {
                var x = left + stepX
                while (x < left + regionWidth - stepX) {
                    val laplacian = 4 * luma(x, y) -
                        luma(x - stepX, y) - luma(x + stepX, y) -
                        luma(x, y - stepY) - luma(x, y + stepY)
                    sum += laplacian
                    sumSq += laplacian.toDouble() * laplacian
                    count++
                    x += stepX
                }
                y += stepY
            }
            if (count == 0) return 0f
            val mean = sum / count
            return ((sumSq / count) - mean * mean).toFloat()
        } finally {
            CVPixelBufferUnlockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly)
        }
    }
}
