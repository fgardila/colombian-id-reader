package dev.code93.colombian_id_reader.parser.mrz

import dev.code93.colombian_id_reader.scan.ScanDebug

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

    /** MRZ-ish enough to be worth describing in the diagnostics. */
    private val nearMiss = Regex("^[A-Z0-9<]{24,36}$")

    /** MRZ alphabet with room for OCR-drifted trailing filler runs. */
    private val td1Alphabet = Regex("^[A-Z0-9<]{20,40}$")

    /**
     * @param ocrLines recognized text lines in reading order (top to bottom).
     * @return the first contiguous window of 3 normalized TD1 lines whose
     *   first line starts with 'I' and whose second starts with a date,
     *   or null if no such window exists on this frame.
     */
    fun extract(ocrLines: List<String>): List<String>? {
        val normalized = ocrLines.map(::normalize)
        val candidates = normalized.filter { td1Line.matches(it) }

        if (ScanDebug.listener != null && normalized.isNotEmpty()) {
            describeRejections(normalized, candidates)
        }

        for (start in 0..candidates.size - 3) {
            val window = candidates.subList(start, start + 3)
            if (window[0].startsWith('I') && sixDigitsPrefix.containsMatchIn(window[1])) {
                ScanDebug.log { "MRZ window accepted:\n  ${window.joinToString("\n  ")}" }
                return window
            }
        }
        if (candidates.isNotEmpty()) {
            ScanDebug.log {
                "MRZ: ${candidates.size} valid 30-char line(s) but no I/date-shaped " +
                    "window of 3:\n  ${candidates.joinToString("\n  ")}"
            }
        }
        return null
    }

    private fun describeRejections(normalized: List<String>, candidates: List<String>) {
        val nearMisses = normalized.filter { !td1Line.matches(it) && nearMiss.matches(it) }
        if (candidates.isEmpty() && nearMisses.isEmpty()) return
        ScanDebug.log {
            buildString {
                append("MRZ: OCR delivered ${normalized.size} line(s); ")
                append("${candidates.size} match TD1, ${nearMisses.size} near-miss(es)")
                for (line in nearMisses) {
                    append("\n  rejected (len=${line.length}): $line")
                }
            }
        }
    }

    private fun normalize(line: String): String {
        val cleaned = line.trim()
            .replace(" ", "")
            .replace("«", "<<")   // common OCR ligature for double filler
            .uppercase()
        return repairTrailingFiller(cleaned)
    }

    /**
     * OCR rarely counts a trailing '<' run correctly (fillers are
     * low-information glyphs), so lines like the mostly-filler line 1
     * and the name line drift between ~24 and ~36 chars. The run itself
     * carries no data: trim it and re-pad to exactly 30. Data integrity
     * is unaffected — lines 1-2 stay covered by the check digits, and
     * the name line's fillers are padding by definition.
     */
    private fun repairTrailingFiller(line: String): String {
        if (line.length == 30 || !td1Alphabet.matches(line)) return line
        val core = line.trimEnd('<')
        if (core.length > 30) return line // real overflow, not filler drift
        return core.padEnd(30, '<')
    }
}
