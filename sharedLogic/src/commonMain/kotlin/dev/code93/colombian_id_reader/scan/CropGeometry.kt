package dev.code93.colombian_id_reader.scan

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure crop-rectangle math for document image capture
 * (ARCHITECTURE-1.0.0.md §4.1). The gate already located the document —
 * [GateObservation.bbox]/[GateObservation.quad] in ROTATED (upright)
 * pixel coordinates; these helpers turn that into the rectangle the
 * platform encoders actually cut.
 *
 * Kept platform-free so the rotation mapping — the classic silent
 * off-by-image bug — is pinned by unit tests instead of discovered on a
 * device.
 */
internal object CropGeometry {

    data class Rect(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
    }

    /**
     * Pads [box] by [paddingFraction] of its own size on every side (a
     * hair of context so the crop never shaves the document edge) and
     * clamps to the rotated frame bounds.
     */
    fun padded(
        box: GateObservation.Box,
        frameWidth: Int,
        frameHeight: Int,
        paddingFraction: Float = 0.04f
    ): Rect {
        val padX = box.width * paddingFraction
        val padY = box.height * paddingFraction
        val left = max(0, (box.left - padX).roundToInt())
        val top = max(0, (box.top - padY).roundToInt())
        val right = min(frameWidth, (box.right + padX).roundToInt())
        val bottom = min(frameHeight, (box.bottom + padY).roundToInt())
        return Rect(left, top, max(1, right - left), max(1, bottom - top))
    }

    /**
     * Maps a rect from ROTATED (upright) space back to the UNROTATED
     * sensor frame ([unrotatedWidth]×[unrotatedHeight]), for
     * [rotationDegrees] ∈ {0, 90, 180, 270} — CameraX semantics: the
     * clockwise rotation that turns the sensor frame upright. Needed on
     * Android, where `ImageProxy.toBitmap()` yields the UNROTATED bitmap
     * while the gate box lives in rotated space.
     */
    fun toUnrotated(
        rect: Rect,
        rotationDegrees: Int,
        unrotatedWidth: Int,
        unrotatedHeight: Int
    ): Rect = when (((rotationDegrees % 360) + 360) % 360) {
        0 -> rect
        90 -> Rect(
            left = rect.top,
            top = unrotatedHeight - rect.right,
            width = rect.height,
            height = rect.width
        )
        180 -> Rect(
            left = unrotatedWidth - rect.right,
            top = unrotatedHeight - rect.bottom,
            width = rect.width,
            height = rect.height
        )
        270 -> Rect(
            left = unrotatedWidth - rect.bottom,
            top = rect.left,
            width = rect.height,
            height = rect.width
        )
        else -> error("rotationDegrees must be a multiple of 90, got $rotationDegrees")
    }
}
