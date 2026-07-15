package dev.code93.colombian_id_reader.parser.mrz

import dev.code93.colombian_id_reader.fixtures.MrzFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MrzCandidateExtractorTest {

    private val card = MrzFixtures.validCard

    @Test
    fun extractsCleanMrzLines() {
        assertEquals(card, MrzCandidateExtractor.extract(card))
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
        assertEquals(card, MrzCandidateExtractor.extract(ocr))
    }

    @Test
    fun normalizesSpacesCaseAndOcrLigature() {
        val noisy = listOf(
            " ${card[0].lowercase().chunked(6).joinToString(" ")} ",
            card[1],
            card[2].replace("<<", "«")
        )
        assertEquals(card, MrzCandidateExtractor.extract(noisy))
    }

    @Test
    fun repairsTrailingFillerDrift() {
        // OCR rarely counts a trailing '<' run right: lengths drift both
        // ways (observed 24-36 on real scans). The run carries no data,
        // so the extractor re-pads to exactly 30.
        val drifted = listOf(card[0].dropLast(3), card[1], card[2] + "<<<<")
        assertEquals(card, MrzCandidateExtractor.extract(drifted))
    }

    @Test
    fun rejectsLinesWhoseContentOverflows() {
        // More than 30 chars of real (non-filler) content is not drift.
        val overflow = "A".repeat(31)
        assertNull(MrzCandidateExtractor.extract(listOf(overflow, card[1], card[2])))
    }

    @Test
    fun rejectsWindowsWithWrongShape() {
        // First line must start with 'I', second with 6 digits.
        assertNull(MrzCandidateExtractor.extract(listOf(card[1], card[2], card[0])))
        assertNull(MrzCandidateExtractor.extract(listOf(card[0], card[2], card[1])))
    }

    @Test
    fun noWindowReturnsNull() {
        assertNull(MrzCandidateExtractor.extract(emptyList()))
        assertNull(MrzCandidateExtractor.extract(listOf(card[0], card[1])))
        assertNull(MrzCandidateExtractor.extract(listOf("REPUBLICA DE COLOMBIA")))
    }
}
