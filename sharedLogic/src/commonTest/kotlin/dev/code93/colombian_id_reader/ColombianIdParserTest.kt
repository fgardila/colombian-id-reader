package dev.code93.colombian_id_reader

import dev.code93.colombian_id_reader.fixtures.MrzFixtureBuilder
import dev.code93.colombian_id_reader.fixtures.MrzFixtures
import dev.code93.colombian_id_reader.fixtures.Pdf417Fixtures
import dev.code93.colombian_id_reader.fixtures.Td3Fixtures
import dev.code93.colombian_id_reader.model.DocumentType
import dev.code93.colombian_id_reader.model.ScannedDocument
import dev.code93.colombian_id_reader.model.ErrorReason
import dev.code93.colombian_id_reader.model.ScanResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ColombianIdParserTest {

    private val validPdf417 = Pdf417Fixtures.all.first { it.expected != null }

    @Test
    fun pdf417HappyPath() {
        val result = ColombianIdParser.parsePdf417(validPdf417.raw)
        val data = assertIs<ScannedDocument.ColombianId>(assertIs<ScanResult.Success>(result).data)
        assertEquals(validPdf417.expected, data)
    }

    @Test
    fun mrzHappyPath() {
        val result = ColombianIdParser.parseMrz(MrzFixtures.validCard)
        val data = assertIs<ScannedDocument.ColombianId>(assertIs<ScanResult.Success>(result).data)
        assertEquals("1032456789", data.nuip)
        assertEquals(DocumentType.CEDULA_DIGITAL, data.documentType)
    }

    @Test
    fun mrzTd3HappyPath() {
        val result = ColombianIdParser.parseMrzTd3(Td3Fixtures.icaoSpecimen)
        val data = assertIs<ScannedDocument.Passport>(assertIs<ScanResult.Success>(result).data)
        assertEquals("L898902C3", data.passportNumber)
        assertEquals(DocumentType.PASSPORT, data.documentType)
    }

    @Test
    fun mrzAcceptsSingleNewlineJoinedBlob() {
        val blob = MrzFixtures.validCard.joinToString("\n")
        val result = ColombianIdParser.parseMrz(listOf(blob))
        assertIs<ScanResult.Success>(result)
    }

    @Test
    fun documentTypeIsDerivedFromEvidence() {
        val amarilla = assertIs<ScanResult.Success>(ColombianIdParser.parsePdf417(validPdf417.raw)).data
        assertEquals(DocumentType.CEDULA_AMARILLA, amarilla.documentType)

        val digital = assertIs<ScanResult.Success>(ColombianIdParser.parseMrz(MrzFixtures.validCard)).data
        assertEquals(DocumentType.CEDULA_DIGITAL, digital.documentType)
    }

    @Test
    fun availabilityMatrixBySource() {
        // §4: PDF417 has blood type but no expiration; MRZ the inverse.
        val pdf417 = assertIs<ScannedDocument.ColombianId>(
            assertIs<ScanResult.Success>(ColombianIdParser.parsePdf417(validPdf417.raw)).data
        )
        assertNotNull(pdf417.bloodType)
        assertNull(pdf417.expirationDate)

        val mrz = assertIs<ScannedDocument.ColombianId>(
            assertIs<ScanResult.Success>(ColombianIdParser.parseMrz(MrzFixtures.validCard)).data
        )
        assertNull(mrz.bloodType)
        assertNotNull(mrz.expirationDate)
    }

    @Test
    fun everyErrorReasonIsReachableThroughTheFacade() {
        assertEquals(
            ErrorReason.INPUT_TOO_SHORT,
            assertIs<ScanResult.Error>(ColombianIdParser.parsePdf417("too short")).reason
        )
        assertEquals(
            ErrorReason.PATTERN_NOT_FOUND,
            assertIs<ScanResult.Error>(ColombianIdParser.parsePdf417("9".repeat(160))).reason
        )
        assertEquals(
            ErrorReason.CHECK_DIGIT_FAILED,
            assertIs<ScanResult.Error>(
                ColombianIdParser.parseMrz(MrzFixtureBuilder.corrupt(MrzFixtures.validCard, 1, 0))
            ).reason
        )
        assertEquals(
            ErrorReason.UNKNOWN_FORMAT,
            assertIs<ScanResult.Error>(
                ColombianIdParser.parseMrz(
                    MrzFixtureBuilder.buildTd1(
                        birth = "880821", sex = 'F', expiry = "310130", nuip = "1032456789",
                        surnames = listOf("MARTINEZ"), givenNames = listOf("MARIA"),
                        docCode = "P<"
                    )
                )
            ).reason
        )
    }
}
