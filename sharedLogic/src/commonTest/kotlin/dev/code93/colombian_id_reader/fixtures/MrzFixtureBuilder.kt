package dev.code93.colombian_id_reader.fixtures

import dev.code93.colombian_id_reader.parser.mrz.MrzCheckDigit

/**
 * Assembles valid TD1 triples for tests, computing the four check digits
 * with [MrzCheckDigit]. Safe as a fixture generator because MrzCheckDigit
 * itself is pinned to hand-verified vectors in MrzCheckDigitTest.
 */
internal object MrzFixtureBuilder {

    fun buildTd1(
        birth: String,
        sex: Char,
        expiry: String,
        nuip: String,
        surnames: List<String>,
        givenNames: List<String>,
        docCode: String = "I<",
        issuingState: String = "COL",
        serial: String = "123456789",
        nationality: String = "COL",
        optional1: String = ""
    ): List<String> {
        require(docCode.length == 2 && issuingState.length == 3 && serial.length == 9)
        require(birth.length == 6 && expiry.length == 6 && nationality.length == 3)
        require(nuip.length <= 11)

        val line1 = docCode + issuingState + serial +
            MrzCheckDigit.compute(serial)!! + optional1.padEnd(15, '<')

        val nuipField = nuip.padEnd(11, '<')
        val line2Prefix = "" + birth + MrzCheckDigit.compute(birth)!! + sex +
            expiry + MrzCheckDigit.compute(expiry)!! + nationality + nuipField
        val compositeField =
            line1.substring(5, 30) + line2Prefix.substring(0, 7) +
                line2Prefix.substring(8, 15) + line2Prefix.substring(18, 29)
        val line2 = line2Prefix + MrzCheckDigit.compute(compositeField)!!

        val line3 =
            (surnames.joinToString("<") + "<<" + givenNames.joinToString("<")).padEnd(30, '<')

        check(line1.length == 30 && line2.length == 30 && line3.length == 30)
        return listOf(line1, line2, line3)
    }

    /** Returns a copy with one character replaced (to break a check digit). */
    fun corrupt(lines: List<String>, lineIndex: Int, charIndex: Int): List<String> {
        val line = lines[lineIndex]
        val replacement = if (line[charIndex] == '9') '8' else '9'
        val corrupted = line.substring(0, charIndex) + replacement + line.substring(charIndex + 1)
        return lines.mapIndexed { i, l -> if (i == lineIndex) corrupted else l }
    }
}
