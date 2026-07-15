package dev.code93.colombian_id_reader.scanner

import java.nio.ByteBuffer

/**
 * Focus signal for the capture gate: variance of a subsampled 3x3
 * Laplacian over the luma (Y) plane. Sampled on a fixed ~96x60 grid, so
 * the cost is independent of the analysis resolution.
 *
 * Measured over the CENTER region of the unrotated plane rather than
 * the detector's bounding box: the box lives in rotated coordinates
 * while the plane does not, and the framing guide keeps the document
 * centered anyway — center sharpness tracks card sharpness closely
 * enough for a gate threshold.
 */
internal object LumaSharpness {

    private const val GRID_X = 96
    private const val GRID_Y = 60

    /** Fraction of each dimension covered by the measured center region. */
    private const val CENTER_FRACTION = 0.6f

    fun measureCenter(plane: ByteBuffer, rowStride: Int, pixelStride: Int, width: Int, height: Int): Float {
        val regionWidth = (width * CENTER_FRACTION).toInt()
        val regionHeight = (height * CENTER_FRACTION).toInt()
        val left = (width - regionWidth) / 2
        val top = (height - regionHeight) / 2

        val stepX = maxOf(1, regionWidth / GRID_X)
        val stepY = maxOf(1, regionHeight / GRID_Y)

        fun luma(x: Int, y: Int): Int =
            plane.get(y * rowStride + x * pixelStride).toInt() and 0xFF

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
    }
}
