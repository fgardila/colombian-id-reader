package dev.code93.colombian_id_reader.model

/**
 * Physical document geometry the capture gate validates against
 * (ARCHITECTURE-0.2.0.md §4.1). The gate compares the candidate object
 * against a SET of accepted formats supplied at construction — geometry
 * is parametrized, never hardcoded (D12).
 */
sealed interface DocumentFormat {

    /** Acceptable width/height ratios for a candidate object. */
    val aspectRatioRange: ClosedFloatingPointRange<Float>

    /**
     * ID-1 — card: 85.6 × 54 mm (nominal ratio ≈ 1.585). Cédula amarilla
     * and cédula digital. The band is deliberately tolerant: Android's
     * detector reports an axis-aligned box, which squashes toward square
     * when the card is rotated in-plane. Tuned in the 0.2.0 field phase.
     */
    data object Id1 : DocumentFormat {
        override val aspectRatioRange: ClosedFloatingPointRange<Float> = 1.40f..1.78f
    }

    /**
     * ID-3 — passport data page: 125 × 88 mm (nominal ratio ≈ 1.42).
     * Defined so the abstraction is shaped correctly; NOT accepted in
     * 0.2.0 (passport capture is 0.3.0). Tolerances uncalibrated.
     */
    data object Id3 : DocumentFormat {
        override val aspectRatioRange: ClosedFloatingPointRange<Float> = 1.30f..1.55f
    }
}
