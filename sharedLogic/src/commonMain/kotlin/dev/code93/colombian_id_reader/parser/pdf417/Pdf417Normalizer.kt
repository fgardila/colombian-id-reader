package dev.code93.colombian_id_reader.parser.pdf417

/**
 * Cleans the raw PDF417 payload into FIELDS.
 *
 * Real cards lay the text out in fixed-width fields padded with runs of
 * separator bytes, while a compound name ("DE LA OSSA") carries single
 * spaces inside its field. Each disallowed character therefore maps to
 * one space (1:1, never collapsing), and a run of 2+ spaces is a field
 * boundary — single spaces survive as part of the field.
 *
 * Two deliberate divergences from the legacy normalizer: '-' is kept
 * (negative blood types) and separator runs are preserved instead of
 * collapsed (field boundaries).
 */
internal object Pdf417Normalizer {

    private val disallowedChar = Regex("[^A-Za-z0-9+_-]")
    private val fieldBoundary = Regex(" {2,}")

    fun fields(raw: String): List<String> =
        raw.replace(disallowedChar, " ")
            .split(fieldBoundary)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
