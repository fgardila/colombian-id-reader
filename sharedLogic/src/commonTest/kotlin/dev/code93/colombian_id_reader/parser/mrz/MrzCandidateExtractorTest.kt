package dev.code93.colombian_id_reader.parser.mrz

import dev.code93.colombian_id_reader.fixtures.MrzFixtures
import dev.code93.colombian_id_reader.fixtures.Td3Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MrzCandidateExtractorTest {

    private val card = MrzFixtures.validCard

    @Test
    fun extractsCleanMrzLines() {
        assertEquals(card, MrzCandidateExtractor.extractTd1(card))
    }

    @Test
    fun ignoresSurroundingCardText() {
        val ocr = listOf(
            "REPUBLICA DE COLOMBIA",
            "Cedula de Ciudadania",
            card[0],
            card[1],
            card[2],
            "15/08/1993"
        )
        assertEquals(card, MrzCandidateExtractor.extractTd1(ocr))
    }

    @Test
    fun normalizesSpacesCaseAndOcrLigature() {
        val noisy = listOf(
            " ${card[0].lowercase().chunked(6).joinToString(" ")} ",
            card[1],
            card[2].replace("<<", "«")
        )
        assertEquals(card, MrzCandidateExtractor.extractTd1(noisy))
    }

    @Test
    fun repairsTrailingFillerDrift() {
        // OCR rarely counts a trailing '<' run right: lengths drift both
        // ways (observed 24-36 on real scans). The run carries no data,
        // so the extractor re-pads to exactly 30.
        val drifted = listOf(card[0].dropLast(3), card[1], card[2] + "<<<<")
        assertEquals(card, MrzCandidateExtractor.extractTd1(drifted))
    }

    @Test
    fun rejectsLinesWhoseContentOverflows() {
        // More than 30 chars of real (non-filler) content is not drift.
        val overflow = "A".repeat(31)
        assertNull(MrzCandidateExtractor.extractTd1(listOf(overflow, card[1], card[2])))
    }

    @Test
    fun rejectsWindowsWithWrongShape() {
        // First line must start with 'I', second with 6 digits.
        assertNull(MrzCandidateExtractor.extractTd1(listOf(card[1], card[2], card[0])))
        assertNull(MrzCandidateExtractor.extractTd1(listOf(card[0], card[2], card[1])))
    }

    @Test
    fun noWindowReturnsNull() {
        assertNull(MrzCandidateExtractor.extractTd1(emptyList()))
        assertNull(MrzCandidateExtractor.extractTd1(listOf(card[0], card[1])))
        assertNull(MrzCandidateExtractor.extractTd1(listOf("REPUBLICA DE COLOMBIA")))
    }

    // --- TD3 (passports) ---

    private val passport = Td3Fixtures.icaoSpecimen

    @Test
    fun extractsTd3AmongDataPageNoise() {
        val ocr = listOf(
            "PASSPORT",
            "REPUBLIC OF UTOPIA",
            "ERIKSSON, ANNA MARIA",
            passport[0],
            passport[1]
        )
        assertEquals(passport, MrzCandidateExtractor.extractTd3(ocr))
    }

    @Test
    fun repairsTd3TrailingFillerDrift() {
        val drifted = listOf(passport[0].dropLast(4), passport[1])
        assertEquals(passport, MrzCandidateExtractor.extractTd3(drifted))
    }

    @Test
    fun shapesDoNotCrossMatch() {
        // A TD1 triple is not a TD3 window and vice versa.
        assertNull(MrzCandidateExtractor.extractTd3(card))
        assertNull(MrzCandidateExtractor.extractTd1(passport))
    }
}
