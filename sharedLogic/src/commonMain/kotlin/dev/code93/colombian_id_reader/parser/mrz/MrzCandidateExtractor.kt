package dev.code93.colombian_id_reader.parser.mrz

/**
 * Picks the three TD1-looking lines out of raw OCR output.
 *
 * It selects, it does not repair: a plausible-looking but corrupt read
 * is passed through and rejected downstream by the MRZ check digits
 * (CHECK_DIGIT_FAILED), which is the designed misread defense (D5).
 */
internal object MrzCandidateExtractor {

    private val td1Line = Regex("^[A-Z0-9<]{30}$")
    private val sixDigitsPrefix = Regex("^\\d{6}")

    /**
     * @param ocrLines recognized text lines in reading order (top to bottom).
     * @return the first contiguous window of 3 normalized TD1 lines whose
     *   first line starts with 'I' and whose second starts with a date,
     *   or null if no such window exists on this frame.
     */
    fun extract(ocrLines: List<String>): List<String>? {
        val candidates = ocrLines.map(::normalize).filter { td1Line.matches(it) }
        for (start in 0..candidates.size - 3) {
            val window = candidates.subList(start, start + 3)
            if (window[0].startsWith('I') && sixDigitsPrefix.containsMatchIn(window[1])) {
                return window
            }
        }
        return null
    }

    private fun normalize(line: String): String =
        line.trim()
            .replace(" ", "")
            .replace("«", "<<")   // common OCR ligature for double filler
            .uppercase()
}
