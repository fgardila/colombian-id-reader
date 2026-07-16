package dev.code93.colombian_id_reader.fixtures

import dev.code93.colombian_id_reader.parser.mrz.MrzCheckDigit

/**
 * Assembles valid TD3 pairs (2×44) for tests, computing the five check
 * digits with [MrzCheckDigit]. Safe as a fixture generator because
 * MrzCheckDigit is pinned to hand-verified vectors, and the builder
 * itself is pinned by reproducing the public ICAO 9303 Part 4 specimen
 * byte-for-byte (see Td3Fixtures).
 */
internal object Td3FixtureBuilder {

    fun buildTd3(
        passportNumber: String,
        birth: String,
        sex: Char,
        expiry: String,
        surnames: List<String>,
        givenNames: List<String>,
        issuingState: String = "COL",
        nationality: String = "COL",
        personalNumber: String = "",
        omitPersonalCheckDigit: Boolean = false,
        docCode: String = "P<"
    ): List<String> {
        require(docCode.length == 2 && passportNumber.length <= 9)
        require(birth.length == 6 && expiry.length == 6)
        require(issuingState.length <= 3 && nationality.length <= 3)
        require(personalNumber.length <= 14)

        val nameField = (surnames.joinToString("<") + "<<" + givenNames.joinToString("<"))
        require(nameField.length <= 39) { "name field exceeds TD3's 39 chars" }
        val line1 = (docCode + issuingState.padEnd(3, '<') + nameField).padEnd(44, '<')

        val numberField = passportNumber.padEnd(9, '<')
        val personalField = personalNumber.padEnd(14, '<')
        val personalCd =
            if (omitPersonalCheckDigit) "<" else MrzCheckDigit.compute(personalField)!!.toString()

        val line2Prefix = numberField + MrzCheckDigit.compute(numberField)!! +
            nationality.padEnd(3, '<') +
            birth + MrzCheckDigit.compute(birth)!! +
            sex +
            expiry + MrzCheckDigit.compute(expiry)!! +
            personalField + personalCd
        val compositeField =
            line2Prefix.substring(0, 10) + line2Prefix.substring(13, 20) + line2Prefix.substring(21, 43)
        val line2 = line2Prefix + MrzCheckDigit.compute(compositeField)!!

        check(line1.length == 44 && line2.length == 44)
        return listOf(line1, line2)
    }
}
