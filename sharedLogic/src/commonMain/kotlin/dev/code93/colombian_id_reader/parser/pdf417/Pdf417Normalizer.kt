package dev.code93.colombian_id_reader.parser.pdf417

/**
 * Cleans the raw PDF417 payload into tokens, equivalent to the legacy
 * `replaceAll("[^\p{Alpha}\p{Digit}\+\_]+", " ")` + split — with one
 * deliberate divergence: '-' is kept in the alphabet. The legacy set kept
 * '+' but not '-', which destroyed negative blood types ("A-" → "A").
 */
internal object Pdf417Normalizer {

    private val separatorRuns = Regex("[^A-Za-z0-9+_-]+")

    fun tokenize(raw: String): List<String> =
        raw.replace(separatorRuns, " ")
            .split(' ')
            .filter { it.isNotEmpty() }
}
