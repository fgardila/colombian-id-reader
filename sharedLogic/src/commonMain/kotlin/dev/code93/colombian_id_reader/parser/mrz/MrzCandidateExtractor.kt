package dev.code93.colombian_id_reader.parser.mrz

import dev.code93.colombian_id_reader.scan.ScanDebug

/**
 * Picks the MRZ-looking lines out of raw OCR output — TD1 (3×30, the
 * cédula digital) or TD3 (2×44, passports).
 *
 * It selects, it does not repair content: a plausible-looking but
 * corrupt read is passed through and rejected downstream by the MRZ
 * check digits (CHECK_DIGIT_FAILED), which is the designed misread
 * defense (D5). The one repair it does perform is structural: OCR
 * rarely counts a trailing '<' run correctly, so drifting fillers are
 * re-padded to the exact line length.
 */
internal object MrzCandidateExtractor {

    private val td1Line = Regex("^[A-Z0-9<]{30}$")
    private val td3Line = Regex("^[A-Z0-9<]{44}$")
    private val sixDigitsPrefix = Regex("^\\d{6}")

    /** MRZ-ish enough to be worth describing in the diagnostics. */
    private val nearMissTd1 = Regex("^[A-Z0-9<]{24,36}$")

    /** MRZ alphabet with room for OCR-drifted trailing filler runs. */
    private val td1Alphabet = Regex("^[A-Z0-9<]{20,40}$")
    private val td3Alphabet = Regex("^[A-Z0-9<]{34,54}$")

    /** Digits or their usual OCR letter confusions (repair runs later). */
    private val digitLike = Regex("^[0-9OQIZSGB]{6}")

    /**
     * TD1: first contiguous window of 3 normalized 30-char lines whose
     * first line starts with 'I' and whose second starts with a date.
     */
    fun extractTd1(ocrLines: List<String>): List<String>? {
        val normalized = ocrLines.map { normalize(it, 30, td1Alphabet) }
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

    /**
     * TD3: first contiguous window of 2 normalized 44-char lines whose
     * first line starts with 'P' and whose second carries a digit-like
     * birth-date zone (repair hasn't run yet — the check digits remain
     * the misread defense).
     */
    fun extractTd3(ocrLines: List<String>): List<String>? {
        val candidates = ocrLines
            .map { normalize(it, 44, td3Alphabet) }
            .filter { td3Line.matches(it) }

        for (start in 0..candidates.size - 2) {
            val window = candidates.subList(start, start + 2)
            if (window[0].startsWith('P') &&
                digitLike.containsMatchIn(window[1].substring(13))
            ) {
                ScanDebug.log { "TD3 window accepted:\n  ${window.joinToString("\n  ")}" }
                return window
            }
        }
        return null
    }

    private fun describeRejections(normalized: List<String>, candidates: List<String>) {
        val nearMisses = normalized.filter { !td1Line.matches(it) && nearMissTd1.matches(it) }
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

    private fun normalize(line: String, targetLength: Int, alphabet: Regex): String {
        val cleaned = line.trim()
            .replace(" ", "")
            .replace("«", "<<")   // common OCR ligature for double filler
            .uppercase()
        return repairTrailingFiller(cleaned, targetLength, alphabet)
    }

    /**
     * OCR rarely counts a trailing '<' run correctly (fillers are
     * low-information glyphs). The run itself carries no data: trim it
     * and re-pad to the exact target length. Data integrity is
     * unaffected — the substantive zones stay covered by check digits.
     */
    private fun repairTrailingFiller(line: String, targetLength: Int, alphabet: Regex): String {
        if (line.length == targetLength || !alphabet.matches(line)) return line
        val core = line.trimEnd('<')
        if (core.length > targetLength) return line // real overflow, not filler drift
        return core.padEnd(targetLength, '<')
    }
}
