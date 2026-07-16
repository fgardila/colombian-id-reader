package dev.code93.colombian_id_reader.parser.mrz

import dev.code93.colombian_id_reader.model.ErrorReason
import dev.code93.colombian_id_reader.model.ScanResult
import dev.code93.colombian_id_reader.model.ScannedDocument
import dev.code93.colombian_id_reader.model.Sex
import dev.code93.colombian_id_reader.parser.DateParsing
import dev.code93.colombian_id_reader.scan.ScanDebug

/**
 * ICAO 9303 Part 4 TD3 parser (passports): two lines of 44 characters.
 *
 * ```
 * Line 1: P [1] issuing state [2..4], SURNAMES<<GIVEN<NAMES [5..43]
 * Line 2: passport no [0..8] + cd [9], nationality [10..12],
 *         birth [13..18] + cd [19], sex [20], expiry [21..26] + cd [27],
 *         personal number [28..41] + cd [42] ('<' allowed when empty),
 *         composite cd [43]
 * ```
 *
 * Unlike TD1, TD3 is genuinely universal — the passport number is
 * always at the same position with the same meaning, and '<<' is
 * honoured everywhere. No country profiles (ARCHITECTURE-0.3.0.md §4.2).
 */
internal object Td3MrzParser {

    private const val LINE_LENGTH = 44

    /**
     * birth+cd, expiry+cd, number cd, optional cd + composite. NOT the
     * passport number [0..8] or personal number [28..41]: both are
     * legitimately alphanumeric — "repairing" a real 'O' would corrupt
     * the value and make its own check digit reject good reads.
     */
    private val DIGIT_ZONES_LINE2 = listOf(9..9, 13 until 20, 21 until 28, 42 until 44)

    private val OCR_DIGIT_CONFUSIONS = mapOf(
        'O' to '0', 'Q' to '0', 'I' to '1', 'Z' to '2', 'S' to '5', 'G' to '6', 'B' to '8'
    )

    fun parse(rawLines: List<String>, currentYear: Int): ScanResult {
        val lines = rawLines
            .flatMap { it.split('\n', '\r') }
            .map { it.trim().replace(" ", "").uppercase() }
            .filter { it.isNotEmpty() }

        if (lines.size != 2 || lines.any { it.length != LINE_LENGTH }) {
            ScanDebug.log {
                "TD3 parse: wrong shape — ${lines.size} line(s), lengths ${lines.map { it.length }}"
            }
            return ScanResult.Error(ErrorReason.INPUT_TOO_SHORT)
        }

        val line1 = lines[0]
        if (line1[0] != 'P') {
            ScanDebug.log { "TD3 parse: doc code '${line1.take(2)}' is not a passport" }
            return ScanResult.Error(ErrorReason.UNKNOWN_FORMAT)
        }
        val line2 = repairDigitZones(lines[1])

        val numberField = line2.substring(0, 9)
        val nationality = line2.substring(10, 13).trimEnd('<')
        val birth = line2.substring(13, 19)
        val sexChar = line2[20]
        val expiry = line2.substring(21, 27)
        val personalField = line2.substring(28, 42)
        val compositeField =
            line2.substring(0, 10) + line2.substring(13, 20) + line2.substring(21, 43)

        val failedDigits = buildList {
            if (!MrzCheckDigit.validate(numberField, line2[9])) {
                add("number '$numberField' cd '${line2[9]}'")
            }
            if (!MrzCheckDigit.validate(birth, line2[19])) add("birth '$birth' cd '${line2[19]}'")
            if (!MrzCheckDigit.validate(expiry, line2[27])) add("expiry '$expiry' cd '${line2[27]}'")
            // ICAO allows '<' instead of the optional-data check digit;
            // the composite still covers the field's characters.
            if (line2[42] != '<' && !MrzCheckDigit.validate(personalField, line2[42])) {
                add("personal '$personalField' cd '${line2[42]}'")
            }
            if (!MrzCheckDigit.validate(compositeField, line2[43])) {
                add("composite cd '${line2[43]}'")
            }
        }
        if (failedDigits.isNotEmpty()) {
            ScanDebug.log { "TD3 parse: check digit(s) failed — ${failedDigits.joinToString("; ")}" }
            return ScanResult.Error(ErrorReason.CHECK_DIGIT_FAILED)
        }

        val passportNumber = numberField.trimEnd('<')
            .takeIf { it.isNotEmpty() }
            ?: run {
                ScanDebug.log { "TD3 parse: empty passport number" }
                return ScanResult.Error(ErrorReason.PATTERN_NOT_FOUND)
            }
        val issuingState = line1.substring(2, 5).trimEnd('<')
            .takeIf { it.isNotEmpty() }
            ?: return ScanResult.Error(ErrorReason.PATTERN_NOT_FOUND)

        // ICAO allows an unknown birth date (filler characters).
        val birthDate = if (birth.contains('<')) {
            null
        } else {
            DateParsing.parseBirthYyMmDd(birth, currentYear)
                ?: return ScanResult.Error(ErrorReason.PATTERN_NOT_FOUND)
        }
        // Expiry is mandatory in TD3.
        val expirationDate = DateParsing.parseExpiryYyMmDd(expiry)
            ?: run {
                ScanDebug.log { "TD3 parse: expiry '$expiry' is not a valid date" }
                return ScanResult.Error(ErrorReason.PATTERN_NOT_FOUND)
            }

        val names = MrzNames.parse(line1.substring(5))
            ?: run {
                ScanDebug.log { "TD3 parse: name field has no '<<' separator or empty groups" }
                return ScanResult.Error(ErrorReason.PATTERN_NOT_FOUND)
            }

        return ScanResult.Success(
            ScannedDocument.Passport(
                givenNames = names.givenNames,
                surnames = names.surnames,
                birthDate = birthDate,
                sex = when (sexChar) {
                    'M' -> Sex.MALE
                    'F' -> Sex.FEMALE
                    else -> Sex.UNSPECIFIED
                },
                passportNumber = passportNumber,
                issuingState = issuingState,
                nationality = nationality,
                expirationDate = expirationDate,
                personalNumber = personalField.trimEnd('<').takeIf { it.isNotEmpty() },
                // ICAO signals possible truncation by filling the name
                // field to its last position (no trailing filler).
                namesTruncated = line1[43] != '<'
            )
        )
    }

    private fun repairDigitZones(line: String): String {
        val chars = line.toCharArray()
        for (zone in DIGIT_ZONES_LINE2) {
            for (index in zone) {
                val replacement = OCR_DIGIT_CONFUSIONS[chars[index]] ?: continue
                chars[index] = replacement
            }
        }
        return chars.concatToString()
    }
}
