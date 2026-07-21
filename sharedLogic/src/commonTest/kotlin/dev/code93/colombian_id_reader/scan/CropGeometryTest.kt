package dev.code93.colombian_id_reader.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CropGeometryTest {

    private fun box(left: Float, top: Float, right: Float, bottom: Float) =
        GateObservation.Box(left, top, right, bottom)

    // --- padded ---

    @Test
    fun padsByFractionOfBoxSize() {
        // 200x200 box, 4% padding = 8px per side.
        val rect = CropGeometry.padded(box(100f, 200f, 300f, 400f), 1000, 1000)
        assertEquals(CropGeometry.Rect(92, 192, 216, 216), rect)
    }

    @Test
    fun clampsToFrameBounds() {
        val rect = CropGeometry.padded(box(0f, 0f, 100f, 50f), 1000, 1000)
        assertEquals(CropGeometry.Rect(0, 0, 104, 52), rect)

        val nearEdge = CropGeometry.padded(box(900f, 950f, 1000f, 1000f), 1000, 1000)
        assertEquals(CropGeometry.Rect(896, 948, 104, 52), nearEdge)
    }

    @Test
    fun degenerateBoxYieldsAtLeastOnePixel() {
        val rect = CropGeometry.padded(box(500f, 500f, 500f, 500f), 1000, 1000)
        assertEquals(1, rect.width)
        assertEquals(1, rect.height)
    }

    // --- toUnrotated (the mapping Android's unrotated bitmap needs) ---

    private val rect = CropGeometry.Rect(left = 100, top = 200, width = 300, height = 400)

    @Test
    fun rotation0IsIdentity() {
        assertEquals(rect, CropGeometry.toUnrotated(rect, 0, 1920, 1080))
        assertEquals(rect, CropGeometry.toUnrotated(rect, 360, 1920, 1080))
    }

    @Test
    fun rotation90PortraitCase() {
        // Sensor 1920x1080, rotated (upright) space is 1080x1920.
        // Rotated x' in [100, 400] came from sensor y = 1080 - x'.
        // Rotated y' in [200, 600] came from sensor x = y'.
        val unrotated = CropGeometry.toUnrotated(rect, 90, 1920, 1080)
        assertEquals(CropGeometry.Rect(left = 200, top = 680, width = 400, height = 300), unrotated)
    }

    @Test
    fun rotation180() {
        val small = CropGeometry.Rect(10, 20, 30, 40)
        val unrotated = CropGeometry.toUnrotated(small, 180, 100, 80)
        assertEquals(CropGeometry.Rect(left = 60, top = 20, width = 30, height = 40), unrotated)
    }

    @Test
    fun rotation270() {
        // Rotated y' in [200, 600] came from sensor x = 1920 - y'.
        // Rotated x' in [100, 400] came from sensor y = x'.
        val unrotated = CropGeometry.toUnrotated(rect, 270, 1920, 1080)
        assertEquals(CropGeometry.Rect(left = 1320, top = 100, width = 400, height = 300), unrotated)
    }

    @Test
    fun mappedRectAlwaysFitsInsideTheUnrotatedFrame() {
        // The property Bitmap.createBitmap enforces at runtime.
        val padded = CropGeometry.padded(box(0f, 0f, 1080f, 1920f), 1080, 1920)
        for (rotation in listOf(0, 90, 180, 270)) {
            val (w, h) = if (rotation % 180 == 0) 1080 to 1920 else 1920 to 1080
            val mapped = CropGeometry.toUnrotated(padded, rotation, w, h)
            assertEquals(true, mapped.left >= 0 && mapped.top >= 0)
            assertEquals(true, mapped.right <= w && mapped.bottom <= h)
        }
    }

    @Test
    fun rejectsNonRightAngles() {
        assertFailsWith<IllegalStateException> {
            CropGeometry.toUnrotated(rect, 45, 1920, 1080)
        }
    }
}
