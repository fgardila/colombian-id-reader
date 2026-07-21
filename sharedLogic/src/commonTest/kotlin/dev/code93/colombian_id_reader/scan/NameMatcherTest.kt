package dev.code93.colombian_id_reader.scan

import dev.code93.colombian_id_reader.model.NameMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NameMatcherTest {

    // --- normalization (§6.3: convention folding must be free) ---

    @Test
    fun normalizeFoldsDiacriticsLikeTheMrzDoes() {
        assertEquals("MARIA", NameMatcher.normalize("MARÍA"))
        assertEquals("MUNOZ VELEZ", NameMatcher.normalize("Muñoz Vélez"))
        assertEquals("PENA LUCIA", NameMatcher.normalize("peña LUCÍA"))
    }

    @Test
    fun normalizeDropsNonLettersAndCollapsesWhitespace() {
        assertEquals("ARDILA CASTRO", NameMatcher.normalize("  ARDILA-  CASTRO. "))
        assertEquals("NUIP", NameMatcher.normalize("NUIP: 1.098.741.992"))
        assertEquals("", NameMatcher.normalize("1.098.741.992"))
    }

    // --- Levenshtein / similarity ---

    @Test
    fun levenshteinPinnedCases() {
        assertEquals(0, NameMatcher.levenshtein("MARIA", "MARIA"))
        assertEquals(1, NameMatcher.levenshtein("MARIA", "MARIO"))   // spec §6.3 example
        assertEquals(5, NameMatcher.levenshtein("MARIA", "PEDRO"))   // spec §6.3 example
        assertEquals(5, NameMatcher.levenshtein("", "PEDRO"))
        assertEquals(3, NameMatcher.levenshtein("ABC", ""))
        assertEquals(1, NameMatcher.levenshtein("MARTA", "MARIA"))
    }

    @Test
    fun similarityIsLengthNormalized() {
        // Same 2-edit distance, opposite meanings (§6.3): enormous in a
        // short string, trivial in a long one.
        assertEquals(0f, NameMatcher.similarity("LI", "XY"))
        assertTrue(NameMatcher.similarity("MARTINEZ GARCIA", "MARTINEZ GARCIX") > 0.9f)
        assertEquals(1f, NameMatcher.similarity("", ""))
        assertEquals(1f, NameMatcher.similarity("IGUAL", "IGUAL"))
    }

    // --- check(): the actual cross-check ---

    private val frontOfFabian = listOf(
        "REPÚBLICA DE COLOMBIA",
        "CÉDULA DE CIUDADANÍA",
        "1.098.741.992",
        "ARDILA CASTRO",
        "APELLIDOS",
        "FABIAN GUILLERMO",
        "NOMBRES"
    )

    @Test
    fun matchesDespiteOcrNoise() {
        // 'l' for 'I' and '0' for 'O' — classic OCR slips over guilloches.
        val noisy = listOf("ARDlLA CASTRO", "FABIAN GUILLERM0", "REPUBLICA DE COLOMBIA")
        val result = NameMatcher.check("ARDILA CASTRO", "FABIAN GUILLERMO", noisy)
        assertEquals(NameMatch.MATCH, result.match)
        assertTrue(result.surnameSimilarity!! >= 0.9f)
    }

    @Test
    fun matchesCleanFrontWithLabelDistractors() {
        val result = NameMatcher.check("ARDILA CASTRO", "FABIAN GUILLERMO", frontOfFabian)
        assertEquals(NameMatch.MATCH, result.match)
        assertEquals(1f, result.surnameSimilarity)
        assertEquals(1f, result.givenSimilarity)
    }

    @Test
    fun matchesNameSplitAcrossTwoOcrLines() {
        // OCR often breaks a long compound surname into two lines; the
        // adjacent-line concatenation candidate recovers it.
        val split = listOf("DE LA", "OSSA TOVAR", "OSWALDO")
        val result = NameMatcher.check("DE LA OSSA TOVAR", "OSWALDO", split)
        assertEquals(NameMatch.MATCH, result.match)
        assertEquals(1f, result.surnameSimilarity)
    }

    @Test
    fun differentPersonIsMismatch() {
        val frontOfSomeoneElse = listOf("VELEZ RUIZ", "GERONIMO", "REPUBLICA DE COLOMBIA")
        val result = NameMatcher.check("ARDILA CASTRO", "FABIAN GUILLERMO", frontOfSomeoneElse)
        assertEquals(NameMatch.MISMATCH, result.match)
    }

    @Test
    fun surnameAloneIsNotEnough() {
        // min-of-both strictness: a matching surname with garbage given
        // names must not pass — that is exactly the wrong-card scenario.
        val partial = listOf("ARDILA CASTRO", "REPUBLICA DE COLOMBIA")
        assertEquals(
            NameMatch.MISMATCH,
            NameMatcher.check("ARDILA CASTRO", "FABIAN GUILLERMO", partial).match
        )
    }

    @Test
    fun unusableFrontTextIsNotChecked() {
        for (lines in listOf(
            emptyList(),
            listOf("", "  "),
            listOf("12", "9.8", "--")  // fewer than 3 letters everywhere
        )) {
            val result = NameMatcher.check("ARDILA CASTRO", "FABIAN GUILLERMO", lines)
            assertEquals(NameMatch.NOT_CHECKED, result.match)
            assertNull(result.surnameSimilarity)
        }
    }

    @Test
    fun blankDecodedNamesAreNotChecked() {
        assertEquals(
            NameMatch.NOT_CHECKED,
            NameMatcher.check("", "FABIAN", frontOfFabian).match
        )
    }

    @Test
    fun thresholdBoundaryIsInclusive() {
        // Distance 3 over length 10 = similarity 0.70 exactly -> MATCH.
        val result = NameMatcher.check("ABCDEFGHIJ", "ABCDEFGHIJ", listOf("ABCDEFGXYZ"))
        assertEquals(0.7f, result.surnameSimilarity!!, absoluteTolerance = 0.0001f)
        assertEquals(NameMatch.MATCH, result.match)
    }
}
