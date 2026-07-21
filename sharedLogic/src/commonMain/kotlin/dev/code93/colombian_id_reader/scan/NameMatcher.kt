package dev.code93.colombian_id_reader.scan

import dev.code93.colombian_id_reader.model.NameMatch
import kotlin.math.max
import kotlin.math.min

/**
 * Front/back name cross-check (ARCHITECTURE-1.0.0.md §6): compares the
 * names OCR'd from the FRONT of the card against the names decoded from
 * the back (PDF417 / MRZ). Establishes *linkage* — front and back belong
 * to the same document — not authenticity.
 *
 * Normalize first, then measure (§6.3): the MRZ strips diacritics by
 * ICAO convention (MARÍA → MARIA, MUÑOZ → MUNOZ) — that is not OCR
 * noise, so it must not spend the tolerance budget. After normalization
 * the Levenshtein distance reflects OCR uncertainty only, and it is
 * length-normalized (2 edits in "LI" is enormous; in "MARTINEZ GARCIA",
 * trivial).
 *
 * The 0.70 threshold is deliberately internal, not public API: it needs
 * tuning against real front-side OCR over guilloches (§10). [check]
 * reports the exact ratios through [ScanDebug] so field data can drive
 * that tuning without an API change.
 */
internal object NameMatcher {

    /** Minimum similarity (per name group) for [NameMatch.MATCH]. */
    const val MATCH_THRESHOLD = 0.70f

    /** OCR lines with fewer letters than this are unusable noise. */
    private const val MIN_USABLE_LETTERS = 3

    private val DIACRITICS = mapOf(
        'Á' to 'A', 'À' to 'A', 'Â' to 'A', 'Ä' to 'A', 'Ã' to 'A',
        'É' to 'E', 'È' to 'E', 'Ê' to 'E', 'Ë' to 'E',
        'Í' to 'I', 'Ì' to 'I', 'Î' to 'I', 'Ï' to 'I',
        'Ó' to 'O', 'Ò' to 'O', 'Ô' to 'O', 'Ö' to 'O', 'Õ' to 'O',
        'Ú' to 'U', 'Ù' to 'U', 'Û' to 'U', 'Ü' to 'U',
        'Ñ' to 'N', 'Ç' to 'C'
    )

    class Result(
        val match: NameMatch,
        /** Best similarity for surnames / given names; null when NOT_CHECKED. */
        val surnameSimilarity: Float?,
        val givenSimilarity: Float?
    )

    /**
     * Uppercase, fold diacritics, drop everything but letters and
     * spaces, collapse runs of whitespace. No java.text.Normalizer in
     * common code — the map covers the Spanish (and general Latin-1)
     * repertoire the Registraduría actually prints.
     */
    fun normalize(raw: String): String = buildString(raw.length) {
        for (ch in raw.uppercase()) {
            val folded = DIACRITICS[ch] ?: ch
            when {
                folded in 'A'..'Z' -> append(folded)
                folded.isWhitespace() ->
                    if (isNotEmpty() && last() != ' ') append(' ')
            }
        }
    }.trim()

    /** Classic two-row dynamic-programming edit distance. */
    fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(previous[j] + 1, current[j - 1] + 1), substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** Length-normalized: 1.0 identical, 0.0 nothing in common (§6.3). */
    fun similarity(a: String, b: String): Float {
        val longest = max(a.length, b.length)
        if (longest == 0) return 1f
        return 1f - levenshtein(a, b).toFloat() / longest
    }

    /**
     * [surnames] and [givenNames] come from the decoded back (already
     * diacritic-free); [frontLines] is raw front OCR output. Each target
     * is matched against the best candidate line — including
     * concatenations of adjacent lines, because OCR sometimes splits a
     * long name across two — and the verdict takes the WORSE of the two
     * scores: "surname matched, given names garbage" must not pass.
     */
    fun check(surnames: String, givenNames: String, frontLines: List<String>): Result {
        val targetSurnames = normalize(surnames)
        val targetGiven = normalize(givenNames)
        val candidates = candidateLines(frontLines)

        if (targetSurnames.length < MIN_USABLE_LETTERS ||
            targetGiven.length < MIN_USABLE_LETTERS ||
            candidates.isEmpty()
        ) {
            ScanDebug.log { "name cross-check: NOT_CHECKED (no usable front text)" }
            return Result(NameMatch.NOT_CHECKED, null, null)
        }

        val surnameBest = candidates.maxOf { similarity(targetSurnames, it) }
        val givenBest = candidates.maxOf { similarity(targetGiven, it) }
        val verdict = if (min(surnameBest, givenBest) >= MATCH_THRESHOLD) {
            NameMatch.MATCH
        } else {
            NameMatch.MISMATCH
        }
        // Ratios only — the names themselves are PII and never logged (§7).
        ScanDebug.log {
            "name cross-check: surnames=${format(surnameBest)} " +
                "given=${format(givenBest)} threshold=$MATCH_THRESHOLD -> $verdict"
        }
        return Result(verdict, surnameBest, givenBest)
    }

    private fun candidateLines(frontLines: List<String>): List<String> {
        val normalized = frontLines.map(::normalize)
            .filter { line -> line.count { it != ' ' } >= MIN_USABLE_LETTERS }
        val joined = (0 until normalized.size - 1).map {
            "${normalized[it]} ${normalized[it + 1]}"
        }
        return normalized + joined
    }

    private fun format(ratio: Float): String {
        val hundredths = (ratio * 100).toInt()
        return "0.${hundredths.toString().padStart(2, '0')}"
    }
}
