package dev.code93.colombian_id_reader.parser.mrz

import dev.code93.colombian_id_reader.model.DocumentSource
import dev.code93.colombian_id_reader.model.ErrorReason
import dev.code93.colombian_id_reader.model.IdCardData
import dev.code93.colombian_id_reader.model.ScanResult
import dev.code93.colombian_id_reader.model.Sex
import dev.code93.colombian_id_reader.parser.DateParsing
import dev.code93.colombian_id_reader.scan.ScanDebug

/**
 * ICAO 9303 TD1 parser for the cédula digital (2021+): three lines of
 * 30 characters.
 *
 * ```
 * Line 1: doc code [0-1], issuing state [2-4], document serial [5-13],
 *         serial check digit [14], optional data [15-29]
 * Line 2: birth YYMMDD [0-5] + CD [6], sex [7], expiry YYMMDD [8-13]
 *         + CD [14], nationality [15-17], optional data [18-28] = NUIP,
 *         composite CD [29]
 * Line 3: SURNAME<SURNAME<<GIVEN<GIVEN, '<'-padded to 30
 * ```
 *
 * The real cédula number (NUIP) is the optional-data field of line 2 —
 * not the document serial on line 1. The MRZ carries no blood type, so
 * [IdCardData.bloodType] is always null for this source.
 */
internal object Td1MrzParser {

    private const val LINE_LENGTH = 30

    fun parse(rawLines: List<String>, currentYear: Int): ScanResult {
        // OCR engines deliver lines with stray spaces and mixed case,
        // sometimes as a single newline-joined blob.
        val lines = rawLines
            .flatMap { it.split('\n', '\r') }
            .map { it.trim().replace(" ", "").uppercase() }
            .filter { it.isNotEmpty() }

        if (lines.size != 3 || lines.any { it.length != LINE_LENGTH }) {
            ScanDebug.log {
                "MRZ parse: wrong shape — ${lines.size} line(s), " +
                    "lengths ${lines.map { it.length }}"
            }
            return ScanResult.Error(ErrorReason.INPUT_TOO_SHORT)
        }
        val (line1, line2, line3) = lines

        if (line1[0] != 'I') {
            ScanDebug.log { "MRZ parse: doc code '${line1.take(2)}' is not an ID card" }
            return ScanResult.Error(ErrorReason.UNKNOWN_FORMAT)
        }

        val serial = line1.substring(5, 14)
        val birth = line2.substring(0, 6)
        val sexChar = line2[7]
        val expiry = line2.substring(8, 14)
        val nuipField = line2.substring(18, 29)
        val compositeField =
            line1.substring(5, 30) + line2.substring(0, 7) + line2.substring(8, 15) + nuipField

        val failedDigits = buildList {
            if (!MrzCheckDigit.validate(serial, line1[14])) add("serial '$serial' cd '${line1[14]}'")
            if (!MrzCheckDigit.validate(birth, line2[6])) add("birth '$birth' cd '${line2[6]}'")
            if (!MrzCheckDigit.validate(expiry, line2[14])) add("expiry '$expiry' cd '${line2[14]}'")
            if (!MrzCheckDigit.validate(compositeField, line2[29])) add("composite cd '${line2[29]}'")
        }
        if (failedDigits.isNotEmpty()) {
            ScanDebug.log { "MRZ parse: check digit(s) failed — ${failedDigits.joinToString("; ")}" }
            return ScanResult.Error(ErrorReason.CHECK_DIGIT_FAILED)
        }

        val nuip = nuipField.replace("<", "")
        if (nuip.isEmpty() || !nuip.all { it.isDigit() }) {
            ScanDebug.log { "MRZ parse: NUIP field '$nuipField' is empty or non-numeric" }
            return ScanResult.Error(ErrorReason.PATTERN_NOT_FOUND)
        }
        val documentNumber = nuip.trimStart('0')
            .takeIf { it.isNotEmpty() }
            ?: return ScanResult.Error(ErrorReason.PATTERN_NOT_FOUND)

        val birthDate = DateParsing.parseBirthYyMmDd(birth, currentYear)
            ?: return ScanResult.Error(ErrorReason.PATTERN_NOT_FOUND)
        val expirationDate = DateParsing.parseExpiryYyMmDd(expiry)
            ?: return ScanResult.Error(ErrorReason.PATTERN_NOT_FOUND)

        val names = parseNameLine(line3)
            ?: run {
                ScanDebug.log { "MRZ parse: name line has no '<<' separator or empty groups: $line3" }
                return ScanResult.Error(ErrorReason.PATTERN_NOT_FOUND)
            }

        return ScanResult.Success(
            IdCardData(
                documentNumber = documentNumber,
                firstName = names.firstName,
                secondName = names.secondName,
                firstSurname = names.firstSurname,
                secondSurname = names.secondSurname,
                birthDate = birthDate,
                sex = when (sexChar) {
                    'M' -> Sex.MALE
                    'F' -> Sex.FEMALE
                    else -> Sex.UNSPECIFIED
                },
                bloodType = null,
                expirationDate = expirationDate,
                source = DocumentSource.MRZ
            )
        )
    }

    private class NameParts(
        val firstSurname: String,
        val secondSurname: String?,
        val firstName: String,
        val secondName: String?
    )

    private fun parseNameLine(line3: String): NameParts? {
        val content = line3.trimEnd('<')
        val separator = content.indexOf("<<")
        if (separator < 0) return null

        val surnames = content.substring(0, separator).split('<').filter { it.isNotEmpty() }
        val givenNames = content.substring(separator + 2).split('<').filter { it.isNotEmpty() }
        if (surnames.isEmpty() || givenNames.isEmpty()) return null

        return NameParts(
            firstSurname = surnames.first(),
            secondSurname = surnames.drop(1).joinToString(" ").takeIf { it.isNotEmpty() },
            firstName = givenNames.first(),
            secondName = givenNames.drop(1).joinToString(" ").takeIf { it.isNotEmpty() }
        )
    }
}
