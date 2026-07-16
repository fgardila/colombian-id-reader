package dev.code93.colombian_id_reader.parser.mrz

import dev.code93.colombian_id_reader.fixtures.MrzFixtureBuilder
import dev.code93.colombian_id_reader.fixtures.Td3FixtureBuilder
import dev.code93.colombian_id_reader.fixtures.Td3Fixtures
import dev.code93.colombian_id_reader.fixtures.Td3Fixtures.CURRENT_YEAR
import dev.code93.colombian_id_reader.model.ErrorReason
import dev.code93.colombian_id_reader.model.Sex
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Td3MrzParserTest {

    private fun parse(lines: List<String>) = Td3MrzParser.parse(lines, CURRENT_YEAR)

    private fun successData(lines: List<String>) =
        assertIs<Td3Outcome.Success>(parse(lines)).passport

    private fun errorReason(lines: List<String>) =
        assertIs<Td3Outcome.Error>(parse(lines)).reason

    @Test
    fun builderReproducesTheIcaoSpecimen() {
        // Pins the composite segmentation and all five check digits
        // against the public ICAO 9303 Part 4 specimen, byte for byte.
        val built = Td3FixtureBuilder.buildTd3(
            passportNumber = "L898902C3",
            birth = "740812", sex = 'F', expiry = "120415",
            surnames = listOf("ERIKSSON"),
            givenNames = listOf("ANNA", "MARIA"),
            issuingState = "UTO", nationality = "UTO",
            personalNumber = "ZE184226B"
        )
        assertEquals(Td3Fixtures.icaoSpecimen, built)
    }

    @Test
    fun parsesTheIcaoSpecimen() {
        val passport = successData(Td3Fixtures.icaoSpecimen)
        assertEquals("L898902C3", passport.passportNumber)
        assertEquals("UTO", passport.issuingState)
        assertEquals("UTO", passport.nationality)
        assertEquals("ERIKSSON", passport.surnames)
        assertEquals("ANNA MARIA", passport.givenNames)
        assertEquals(LocalDate(1974, 8, 12), passport.birthDate)
        assertEquals(Sex.FEMALE, passport.sex)
        assertEquals(LocalDate(2012, 4, 15), passport.expirationDate)
        assertEquals("ZE184226B", passport.personalNumber)
        assertFalse(passport.namesTruncated)
    }

    @Test
    fun parsesAColombianPassport() {
        val passport = successData(Td3Fixtures.colombianPassport)
        assertEquals("AB1234567", passport.passportNumber)
        assertEquals("COL", passport.issuingState)
        assertEquals("MARTINEZ GARCIA", passport.surnames)
        assertEquals("MARIA DANIELA", passport.givenNames)
        assertNull(passport.personalNumber) // optional data empty
    }

    @Test
    fun fullNameFieldFlagsPossibleTruncation() {
        // Name field filled to exactly 39 chars — no trailing filler at
        // line1[43] means the name MAY be incomplete (ICAO rule).
        val nameField = "WOLFESCHLEGELSTEINHAUSEN<<MAXIMILIANOSS"
        assertEquals(39, nameField.length)
        val longName = Td3FixtureBuilder.buildTd3(
            passportNumber = "X12345678",
            birth = "900101", sex = 'M', expiry = "300101",
            surnames = listOf("WOLFESCHLEGELSTEINHAUSEN"),
            givenNames = listOf("MAXIMILIANOSS")
        )
        assertTrue(successData(longName).namesTruncated)
        assertFalse(successData(Td3Fixtures.icaoSpecimen).namesTruncated)
    }

    @Test
    fun emptyPersonalNumberAcceptsFillerOrZeroCheckDigit() {
        val withZeroCd = Td3FixtureBuilder.buildTd3(
            passportNumber = "X12345678", birth = "900101", sex = 'M', expiry = "300101",
            surnames = listOf("PEREZ"), givenNames = listOf("JUAN")
        )
        assertNull(successData(withZeroCd).personalNumber)

        val withFillerCd = Td3FixtureBuilder.buildTd3(
            passportNumber = "X12345678", birth = "900101", sex = 'M', expiry = "300101",
            surnames = listOf("PEREZ"), givenNames = listOf("JUAN"),
            omitPersonalCheckDigit = true
        )
        assertNull(successData(withFillerCd).personalNumber)
    }

    @Test
    fun nonIsoIssuingStateIsKeptVerbatim() {
        val german = Td3FixtureBuilder.buildTd3(
            passportNumber = "C01X00T47", birth = "800101", sex = 'F', expiry = "300101",
            surnames = listOf("MUSTERMANN"), givenNames = listOf("ERIKA"),
            issuingState = "D", nationality = "D"
        )
        val passport = successData(german)
        assertEquals("D", passport.issuingState)
        assertEquals("D", passport.nationality)
    }

    @Test
    fun sexXOrFillerIsUnspecified() {
        for (sexChar in listOf('X', '<')) {
            val lines = Td3FixtureBuilder.buildTd3(
                passportNumber = "X12345678", birth = "900101", sex = sexChar, expiry = "300101",
                surnames = listOf("PEREZ"), givenNames = listOf("JUAN")
            )
            assertEquals(Sex.UNSPECIFIED, successData(lines).sex)
        }
    }

    @Test
    fun eachCorruptedCheckDigitFails() {
        val lines = Td3Fixtures.icaoSpecimen
        val corruptions = listOf(
            MrzFixtureBuilder.corrupt(lines, 1, 0),   // passport number
            MrzFixtureBuilder.corrupt(lines, 1, 13),  // birth
            MrzFixtureBuilder.corrupt(lines, 1, 21),  // expiry
            MrzFixtureBuilder.corrupt(lines, 1, 28),  // personal number
            MrzFixtureBuilder.corrupt(lines, 1, 43)   // composite cd itself
        )
        for (corrupted in corruptions) {
            assertEquals(ErrorReason.CHECK_DIGIT_FAILED, errorReason(corrupted))
        }
    }

    @Test
    fun wrongShapeIsRejected() {
        val lines = Td3Fixtures.icaoSpecimen
        assertEquals(ErrorReason.INPUT_TOO_SHORT, errorReason(lines.take(1)))
        assertEquals(ErrorReason.INPUT_TOO_SHORT, errorReason(lines + lines[0]))
        assertEquals(
            ErrorReason.INPUT_TOO_SHORT,
            errorReason(listOf(lines[0].dropLast(1), lines[1]))
        )
    }

    @Test
    fun nonPassportDocCodeIsRejected() {
        val idCard = Td3FixtureBuilder.buildTd3(
            passportNumber = "X12345678", birth = "900101", sex = 'M', expiry = "300101",
            surnames = listOf("PEREZ"), givenNames = listOf("JUAN"),
            docCode = "I<"
        )
        assertEquals(ErrorReason.UNKNOWN_FORMAT, errorReason(idCard))
    }

    @Test
    fun centuryPivot() {
        fun birthOf(yyMmDd: String) = successData(
            Td3FixtureBuilder.buildTd3(
                passportNumber = "X12345678", birth = yyMmDd, sex = 'M', expiry = "300101",
                surnames = listOf("PEREZ"), givenNames = listOf("JUAN")
            )
        ).birthDate

        assertEquals(LocalDate(1974, 8, 12), birthOf("740812"))
        assertEquals(LocalDate(2005, 6, 21), birthOf("050621"))
    }

    @Test
    fun ocrRepairFixesDigitZonesButNeverThePassportNumber() {
        // 'O' misread where birth prints '0' (740812 -> 74O812) gets
        // repaired and the check digit then passes...
        val card = Td3Fixtures.icaoSpecimen.toMutableList()
        card[1] = card[1].replaceRange(15, 16, "O")
        assertEquals("L898902C3", successData(card).passportNumber)

        // ...while a legitimate 'O' inside the passport number is left
        // alone (its own check digit validates the real letter).
        val withLetterO = Td3FixtureBuilder.buildTd3(
            passportNumber = "O12345678", birth = "900101", sex = 'M', expiry = "300101",
            surnames = listOf("PEREZ"), givenNames = listOf("JUAN")
        )
        assertEquals("O12345678", successData(withLetterO).passportNumber)
    }

    @Test
    fun fillerExpiryIsRejected() {
        // Expiry is mandatory in TD3; build with valid CDs over fillers.
        val lines = Td3FixtureBuilder.buildTd3(
            passportNumber = "X12345678", birth = "900101", sex = 'M', expiry = "300101",
            surnames = listOf("PEREZ"), givenNames = listOf("JUAN")
        ).toMutableList()
        val fillerExpiry = "<<<<<<"
        val cd = MrzCheckDigit.compute(fillerExpiry)!!.toString()
        val prefix = lines[1].substring(0, 21)
        val suffix = lines[1].substring(28, 43)
        val rebuilt = prefix + fillerExpiry + cd + suffix
        val composite = MrzCheckDigit.compute(
            rebuilt.substring(0, 10) + rebuilt.substring(13, 20) + rebuilt.substring(21, 43)
        )!!.toString()
        lines[1] = rebuilt + composite
        assertEquals(ErrorReason.PATTERN_NOT_FOUND, errorReason(lines))
    }
}
