package dev.code93.colombian_id_reader.parser.mrz

import dev.code93.colombian_id_reader.fixtures.MrzFixtureBuilder
import dev.code93.colombian_id_reader.fixtures.MrzFixtures
import dev.code93.colombian_id_reader.fixtures.MrzFixtures.CURRENT_YEAR
import dev.code93.colombian_id_reader.model.DocumentSource
import dev.code93.colombian_id_reader.model.ErrorReason
import dev.code93.colombian_id_reader.model.ScanResult
import dev.code93.colombian_id_reader.model.Sex
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class Td1MrzParserTest {

    private fun parse(lines: List<String>) = Td1MrzParser.parse(lines, CURRENT_YEAR)

    private fun successData(lines: List<String>) =
        assertIs<ScanResult.Success>(parse(lines)).data

    private fun errorReason(lines: List<String>) =
        assertIs<ScanResult.Error>(parse(lines)).reason

    @Test
    fun parsesCanonicalDigitalCedula() {
        val data = successData(MrzFixtures.validCard)

        assertEquals("1032456789", data.documentNumber)
        assertEquals("MARIA", data.firstName)
        assertEquals("DANIELA", data.secondName)
        assertEquals("MARTINEZ", data.firstSurname)
        assertEquals("GARCIA", data.secondSurname)
        assertEquals(LocalDate(1988, 8, 21), data.birthDate)
        assertEquals(Sex.FEMALE, data.sex)
        assertEquals(LocalDate(2031, 1, 30), data.expirationDate)
        assertEquals(DocumentSource.MRZ, data.source)
        // Availability matrix (§4): the MRZ never carries blood type.
        assertNull(data.bloodType)
    }

    @Test
    fun parsesSingleNameAndSingleSurname() {
        val data = successData(
            MrzFixtureBuilder.buildTd1(
                birth = "990228", sex = 'M', expiry = "330501",
                nuip = "0056789123",
                surnames = listOf("SALAZAR"), givenNames = listOf("CAMILO")
            )
        )
        assertEquals("CAMILO", data.firstName)
        assertNull(data.secondName)
        assertEquals("SALAZAR", data.firstSurname)
        assertNull(data.secondSurname)
        assertEquals(Sex.MALE, data.sex)
        assertEquals("56789123", data.documentNumber) // leading zeros stripped
    }

    @Test
    fun centuryPivot() {
        fun birthOf(yyMmDd: String) = successData(
            MrzFixtureBuilder.buildTd1(
                birth = yyMmDd, sex = 'F', expiry = "310130", nuip = "1032456789",
                surnames = listOf("MARTINEZ"), givenNames = listOf("MARIA")
            )
        ).birthDate

        assertEquals(LocalDate(1988, 8, 21), birthOf("880821"))
        assertEquals(LocalDate(2005, 6, 21), birthOf("050621"))
        // 2000 + 26 == currentYear (2026): not in the future, stays 2026
        assertEquals(LocalDate(2026, 1, 2), birthOf("260102"))
        assertEquals(LocalDate(1927, 1, 2), birthOf("270102"))
    }

    @Test
    fun unspecifiedSex() {
        for (sexChar in listOf('<', 'X')) {
            val data = successData(
                MrzFixtureBuilder.buildTd1(
                    birth = "880821", sex = sexChar, expiry = "310130", nuip = "1032456789",
                    surnames = listOf("MARTINEZ"), givenNames = listOf("MARIA")
                )
            )
            assertEquals(Sex.UNSPECIFIED, data.sex)
        }
    }

    @Test
    fun normalizesOcrNoise() {
        // lowercase and stray spaces, as OCR engines often deliver
        val noisy = MrzFixtures.validCard.map { "  ${it.lowercase().chunked(10).joinToString(" ")} " }
        assertEquals("1032456789", successData(noisy).documentNumber)
    }

    @Test
    fun eachCorruptedCheckDigitFails() {
        val card = MrzFixtures.validCard
        // serial digit (breaks serial CD), birth digit, expiry digit, composite CD
        val corruptions = listOf(
            MrzFixtureBuilder.corrupt(card, 0, 5),   // document serial
            MrzFixtureBuilder.corrupt(card, 1, 0),   // birth date
            MrzFixtureBuilder.corrupt(card, 1, 8),   // expiry date
            MrzFixtureBuilder.corrupt(card, 1, 29)   // composite check digit alone
        )
        for (corrupted in corruptions) {
            assertEquals(ErrorReason.CHECK_DIGIT_FAILED, errorReason(corrupted))
        }
    }

    @Test
    fun wrongShapeIsRejected() {
        val card = MrzFixtures.validCard
        assertEquals(ErrorReason.INPUT_TOO_SHORT, errorReason(card.take(2)))
        assertEquals(ErrorReason.INPUT_TOO_SHORT, errorReason(card + "EXTRALINE<<<<<<<<<<<<<<<<<<<<<C"))
        assertEquals(
            ErrorReason.INPUT_TOO_SHORT,
            errorReason(listOf(card[0].dropLast(1), card[1], card[2]))
        )
        assertEquals(ErrorReason.INPUT_TOO_SHORT, errorReason(emptyList()))
    }

    @Test
    fun nonIdDocumentCodeIsRejected() {
        val passportish = MrzFixtureBuilder.buildTd1(
            birth = "880821", sex = 'F', expiry = "310130", nuip = "1032456789",
            surnames = listOf("MARTINEZ"), givenNames = listOf("MARIA"),
            docCode = "P<"
        )
        assertEquals(ErrorReason.UNKNOWN_FORMAT, errorReason(passportish))
    }

    @Test
    fun missingOrNonNumericNuipIsRejected() {
        fun cardWithNuip(nuip: String) = MrzFixtureBuilder.buildTd1(
            birth = "880821", sex = 'F', expiry = "310130", nuip = nuip,
            surnames = listOf("MARTINEZ"), givenNames = listOf("MARIA")
        )
        assertEquals(ErrorReason.PATTERN_NOT_FOUND, errorReason(cardWithNuip("")))
        // Letters outside the OCR confusion map (a mapped letter like 'B'
        // gets repaired to a digit and is caught by the composite instead).
        assertEquals(ErrorReason.PATTERN_NOT_FOUND, errorReason(cardWithNuip("AX12345678")))
        assertEquals(ErrorReason.PATTERN_NOT_FOUND, errorReason(cardWithNuip("00000000000")))
    }

    @Test
    fun invalidCalendarBirthDateIsRejected() {
        val card = MrzFixtureBuilder.buildTd1(
            birth = "881332", sex = 'F', expiry = "310130", nuip = "1032456789",
            surnames = listOf("MARTINEZ"), givenNames = listOf("MARIA")
        )
        assertEquals(ErrorReason.PATTERN_NOT_FOUND, errorReason(card))
    }

    @Test
    fun repairsOcrLetterForDigitConfusions() {
        // Observed on real scans: 'O' read where a '0' is printed. The
        // repair happens before validation, so the check digits still
        // pass — and still reject any genuinely wrong substitution.
        val card = MrzFixtures.validCard.toMutableList()
        card[1] = card[1].replaceRange(2, 3, "O") // birth 880821 -> 88O821
        assertEquals("1032456789", successData(card).documentNumber)

        val corrupted = MrzFixtureBuilder.corrupt(MrzFixtures.validCard, 1, 0)
        assertEquals(ErrorReason.CHECK_DIGIT_FAILED, errorReason(corrupted))
    }

    @Test
    fun acceptsMissingSerialCheckDigit() {
        // Some cédulas print '<' instead of a serial check digit (the
        // Registraduría specimen does); the composite still covers the
        // serial, so only a printed digit is validated.
        val card = MrzFixtureBuilder.buildTd1(
            birth = "880821", sex = 'F', expiry = "310130", nuip = "1032456789",
            surnames = listOf("MARTINEZ"), givenNames = listOf("MARIA"),
            omitSerialCheckDigit = true
        )
        assertEquals("1032456789", successData(card).documentNumber)
    }

    @Test
    fun registraduriaSpecimenIsInternallyInvalid() {
        // The public specimen (registraduria.gov.co back-ccd.png) prints
        // composite check digit 9 where the ICAO computation over its
        // own characters gives 8 — real cards validate correctly with
        // the same segmentation. Rejecting it is the misread defense
        // (D5) working, not a scanner bug.
        val specimen = listOf(
            "ICCOL000000012<<<<<<<<<<<<<<<<",
            "8808213F3101300COL1234567890<9",
            "VELEZ<RUIZ<<GERONIMO<<<<<<<<<<"
        )
        assertEquals(ErrorReason.CHECK_DIGIT_FAILED, errorReason(specimen))

        // With the composite corrected to 8, everything else parses.
        val corrected = specimen.toMutableList()
        corrected[1] = corrected[1].dropLast(1) + "8"
        val data = successData(corrected)
        assertEquals("1234567890", data.documentNumber)
        assertEquals("GERONIMO", data.firstName)
        assertEquals("VELEZ", data.firstSurname)
        assertEquals("RUIZ", data.secondSurname)
    }

    @Test
    fun nameLineWithoutSeparatorIsRejected() {
        val card = MrzFixtures.validCard.toMutableList()
        card[2] = "MARTINEZ<GARCIA<MARIA<DANIELA<" // single '<' everywhere, no '<<'
        assertEquals(ErrorReason.PATTERN_NOT_FOUND, errorReason(card))
    }
}
