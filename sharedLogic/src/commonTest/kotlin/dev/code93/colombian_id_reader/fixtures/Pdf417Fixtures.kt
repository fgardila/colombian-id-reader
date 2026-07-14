package dev.code93.colombian_id_reader.fixtures

import dev.code93.colombian_id_reader.legacy.LegacyPdf417Oracle.LegacyInfo
import dev.code93.colombian_id_reader.model.DocumentSource
import dev.code93.colombian_id_reader.model.ErrorReason
import dev.code93.colombian_id_reader.model.IdCardData
import dev.code93.colombian_id_reader.model.Sex
import kotlinx.datetime.LocalDate

/**
 * Golden set for the PDF417 parser (ARCHITECTURE.md §8, Phase 1a).
 *
 * No real 2020 production strings are available, so the raw strings are
 * synthetic, built to the exact anatomy the legacy parser demands. Each
 * fixture freezes two expectations:
 *
 * - [Pdf417Fixture.legacy]: what the 2020 code produced, verified
 *   executable-y against [LegacyPdf417Oracle] (null = legacy rejected or
 *   crashed on this input).
 * - [Pdf417Fixture.expected] / [Pdf417Fixture.expectedError]: the frozen
 *   contract for the new pattern-based parser, including the documented
 *   divergences from legacy behavior:
 *   1. AB blood types: legacy `substring(length - 2)` truncated "AB+" to
 *      "B+"; the new parser returns the full "AB+".
 *   2. Sex: legacy did `token.contains("M")`; the new parser reads the
 *      explicit M/F character of the demographic block.
 *   3. Legacy used " "/"" placeholders for absent second name/surname;
 *      the new parser uses null.
 *   4. Names with Ñ/accents: the normalizer (like the legacy one) strips
 *      them, splitting the name token. Legacy crashed; the new parser
 *      recovers best-effort by joining the extra tokens into secondName.
 *   5. Negative blood types: the legacy normalizer's allowed set kept '+'
 *      but not '-', so "A-" lost its sign and legacy rh became the last
 *      two surviving chars (e.g. "1A"). The new normalizer keeps '-' and
 *      returns the real "A-".
 */
internal data class Pdf417Fixture(
    val name: String,
    val raw: String,
    val legacy: LegacyInfo?,
    val expected: IdCardData?,
    val expectedError: ErrorReason? = null
)

internal object Pdf417Fixtures {

    /**
     * Joins tokens with '#' (normalized to a space by both parsers) and
     * left-pads with a filler token of '9's so the total length passes the
     * legacy `< 150` gate. Callers' tokens land at splitStr[1..n].
     */
    private fun rawOf(vararg tokens: String): String {
        val body = tokens.joinToString("#")
        val padding = (150 - body.length - 1).coerceAtLeast(4)
        return "9".repeat(padding) + "#" + body
    }

    val all: List<Pdf417Fixture> = listOf(

        Pdf417Fixture(
            name = "nonPubDsk twoNames twoSurnames male O+",
            raw = rawOf(
                "22", "04620001234567GARCIA", "RODRIGUEZ", "JUAN", "CARLOS",
                "0M198808210060O+"
            ),
            legacy = LegacyInfo(
                primerApellido = "GARCIA", segundoApellido = "RODRIGUEZ",
                primerNombre = "JUAN", segundoNombre = "CARLOS",
                cedula = "0001234567", rh = "O+",
                fechaNacimiento = "19880821", sexo = "0M198808210060O+"
            ),
            expected = IdCardData(
                documentNumber = "1234567",
                firstName = "JUAN", secondName = "CARLOS",
                firstSurname = "GARCIA", secondSurname = "RODRIGUEZ",
                birthDate = LocalDate(1988, 8, 21), sex = Sex.MALE,
                bloodType = "O+", expirationDate = null,
                source = DocumentSource.PDF417
            )
        ),

        Pdf417Fixture(
            name = "nonPubDsk singleName female A- (corrimiento shift)",
            raw = rawOf(
                "22", "03110052147896LOPEZ", "TORRES", "ANA",
                "0F199103050071A-"
            ),
            legacy = LegacyInfo(
                primerApellido = "LOPEZ", segundoApellido = "TORRES",
                primerNombre = "ANA", segundoNombre = "",
                cedula = "0052147896", rh = "1A",
                fechaNacimiento = "19910305", sexo = "0F199103050071A"
            ),
            expected = IdCardData(
                documentNumber = "52147896",
                firstName = "ANA", secondName = null,
                firstSurname = "LOPEZ", secondSurname = "TORRES",
                birthDate = LocalDate(1991, 3, 5), sex = Sex.FEMALE,
                bloodType = "A-", expirationDate = null,
                source = DocumentSource.PDF417
            )
        ),

        Pdf417Fixture(
            name = "nonPubDsk bloodType AB+ (divergence: legacy truncated to B+)",
            raw = rawOf(
                "22", "05000009876543MARTINEZ", "PEREZ", "LUIS", "MIGUEL",
                "0M197512010045AB+"
            ),
            legacy = LegacyInfo(
                primerApellido = "MARTINEZ", segundoApellido = "PEREZ",
                primerNombre = "LUIS", segundoNombre = "MIGUEL",
                cedula = "0009876543", rh = "B+",
                fechaNacimiento = "19751201", sexo = "0M197512010045AB+"
            ),
            expected = IdCardData(
                documentNumber = "9876543",
                firstName = "LUIS", secondName = "MIGUEL",
                firstSurname = "MARTINEZ", secondSurname = "PEREZ",
                birthDate = LocalDate(1975, 12, 1), sex = Sex.MALE,
                bloodType = "AB+", expirationDate = null,
                source = DocumentSource.PDF417
            )
        ),

        Pdf417Fixture(
            name = "nonPubDsk cedula without leading zeros female B-",
            raw = rawOf(
                "22", "02901023456789DIAZ", "CASTRO", "MARIA", "JOSE",
                "0F200204150033B-"
            ),
            legacy = LegacyInfo(
                primerApellido = "DIAZ", segundoApellido = "CASTRO",
                primerNombre = "MARIA", segundoNombre = "JOSE",
                cedula = "1023456789", rh = "3B",
                fechaNacimiento = "20020415", sexo = "0F200204150033B"
            ),
            expected = IdCardData(
                documentNumber = "1023456789",
                firstName = "MARIA", secondName = "JOSE",
                firstSurname = "DIAZ", secondSurname = "CASTRO",
                birthDate = LocalDate(2002, 4, 15), sex = Sex.FEMALE,
                bloodType = "B-", expirationDate = null,
                source = DocumentSource.PDF417
            )
        ),

        Pdf417Fixture(
            name = "nonPubDsk invalid calendar date -> birthDate null, still Success",
            raw = rawOf(
                "22", "04620001234567GARCIA", "RODRIGUEZ", "JUAN", "CARLOS",
                "0M198813400012O+"
            ),
            legacy = LegacyInfo(
                primerApellido = "GARCIA", segundoApellido = "RODRIGUEZ",
                primerNombre = "JUAN", segundoNombre = "CARLOS",
                cedula = "0001234567", rh = "O+",
                fechaNacimiento = "19881340", sexo = "0M198813400012O+"
            ),
            expected = IdCardData(
                documentNumber = "1234567",
                firstName = "JUAN", secondName = "CARLOS",
                firstSurname = "GARCIA", secondSurname = "RODRIGUEZ",
                birthDate = null, sex = Sex.MALE,
                bloodType = "O+", expirationDate = null,
                source = DocumentSource.PDF417
            )
        ),

        Pdf417Fixture(
            name = "pubDsk twoSurnames twoNames male O+",
            raw = rawOf(
                "PubDSK1", "Ax12", "05770034567891HERNANDEZ", "GOMEZ",
                "PEDRO", "PABLO", "0M196707240088O+"
            ),
            legacy = LegacyInfo(
                primerApellido = "HERNANDEZ", segundoApellido = "GOMEZ",
                primerNombre = "PEDRO", segundoNombre = "PABLO",
                cedula = "0034567891", rh = "O+",
                fechaNacimiento = "19670724", sexo = "M"
            ),
            expected = IdCardData(
                documentNumber = "34567891",
                firstName = "PEDRO", secondName = "PABLO",
                firstSurname = "HERNANDEZ", secondSurname = "GOMEZ",
                birthDate = LocalDate(1967, 7, 24), sex = Sex.MALE,
                bloodType = "O+", expirationDate = null,
                source = DocumentSource.PDF417
            )
        ),

        Pdf417Fixture(
            name = "pubDsk twoSurnames oneName female O-",
            raw = rawOf(
                "PubDSK1", "Ax12", "06230045678912RUIZ", "VARGAS", "DIANA",
                "0F198506120099O-"
            ),
            legacy = LegacyInfo(
                primerApellido = "RUIZ", segundoApellido = "VARGAS",
                primerNombre = "DIANA", segundoNombre = " ",
                cedula = "0045678912", rh = "9O",
                fechaNacimiento = "19850612", sexo = "F"
            ),
            expected = IdCardData(
                documentNumber = "45678912",
                firstName = "DIANA", secondName = null,
                firstSurname = "RUIZ", secondSurname = "VARGAS",
                birthDate = LocalDate(1985, 6, 12), sex = Sex.FEMALE,
                bloodType = "O-", expirationDate = null,
                source = DocumentSource.PDF417
            )
        ),

        Pdf417Fixture(
            name = "pubDsk oneSurname oneName male A+",
            raw = rawOf(
                "PubDSK1", "Ax12", "07090056789123SALAZAR", "CAMILO",
                "0M199902280077A+"
            ),
            legacy = LegacyInfo(
                primerApellido = "SALAZAR", segundoApellido = " ",
                primerNombre = "CAMILO", segundoNombre = "",
                cedula = "0056789123", rh = "A+",
                fechaNacimiento = "19990228", sexo = "M"
            ),
            expected = IdCardData(
                documentNumber = "56789123",
                firstName = "CAMILO", secondName = null,
                firstSurname = "SALAZAR", secondSurname = null,
                birthDate = LocalDate(1999, 2, 28), sex = Sex.MALE,
                bloodType = "A+", expirationDate = null,
                source = DocumentSource.PDF417
            )
        ),

        Pdf417Fixture(
            name = "pubDsk pre-shift (splitStr[2].length > 7) twoNames male B+",
            raw = rawOf(
                "PubDSK1", "08540067891234MORENO", "JIMENEZ", "ANDRES",
                "FELIPE", "0M197303170055B+"
            ),
            legacy = LegacyInfo(
                primerApellido = "MORENO", segundoApellido = "JIMENEZ",
                primerNombre = "ANDRES", segundoNombre = "FELIPE",
                cedula = "0067891234", rh = "B+",
                fechaNacimiento = "19730317", sexo = "M"
            ),
            expected = IdCardData(
                documentNumber = "67891234",
                firstName = "ANDRES", secondName = "FELIPE",
                firstSurname = "MORENO", secondSurname = "JIMENEZ",
                birthDate = LocalDate(1973, 3, 17), sex = Sex.MALE,
                bloodType = "B+", expirationDate = null,
                source = DocumentSource.PDF417
            )
        ),

        Pdf417Fixture(
            name = "nonPubDsk enye in second name (legacy crashed; new parser recovers)",
            raw = rawOf(
                "22", "04910078912345ACOSTA", "BELLO", "MARIO", "NIÑO",
                "0M199408190022O+"
            ),
            legacy = null,
            expected = IdCardData(
                documentNumber = "78912345",
                firstName = "MARIO", secondName = "NI O",
                firstSurname = "ACOSTA", secondSurname = "BELLO",
                birthDate = LocalDate(1994, 8, 19), sex = Sex.MALE,
                bloodType = "O+", expirationDate = null,
                source = DocumentSource.PDF417
            )
        ),

        Pdf417Fixture(
            name = "nonPubDsk date-first demographic block male O+ (D4 shape)",
            raw = rawOf(
                "22", "04620001234567GARCIA", "RODRIGUEZ", "JUAN", "CARLOS",
                "0219880821M0045O+"
            ),
            legacy = LegacyInfo(
                primerApellido = "GARCIA", segundoApellido = "RODRIGUEZ",
                primerNombre = "JUAN", segundoNombre = "CARLOS",
                cedula = "0001234567", rh = "O+",
                fechaNacimiento = "19880821", sexo = "0219880821M0045O+"
            ),
            expected = IdCardData(
                documentNumber = "1234567",
                firstName = "JUAN", secondName = "CARLOS",
                firstSurname = "GARCIA", secondSurname = "RODRIGUEZ",
                birthDate = LocalDate(1988, 8, 21), sex = Sex.MALE,
                bloodType = "O+", expirationDate = null,
                source = DocumentSource.PDF417
            )
        ),

        Pdf417Fixture(
            name = "nonPubDsk date-first demographic block female A+ (corrimiento)",
            raw = rawOf(
                "22", "03110052147896LOPEZ", "TORRES", "ANA",
                "0119910305F0032A+"
            ),
            legacy = LegacyInfo(
                primerApellido = "LOPEZ", segundoApellido = "TORRES",
                primerNombre = "ANA", segundoNombre = "",
                cedula = "0052147896", rh = "A+",
                fechaNacimiento = "19910305", sexo = "0119910305F0032A+"
            ),
            expected = IdCardData(
                documentNumber = "52147896",
                firstName = "ANA", secondName = null,
                firstSurname = "LOPEZ", secondSurname = "TORRES",
                birthDate = LocalDate(1991, 3, 5), sex = Sex.FEMALE,
                bloodType = "A+", expirationDate = null,
                source = DocumentSource.PDF417
            )
        ),

        Pdf417Fixture(
            name = "input shorter than 150 chars",
            raw = "0017#123456789",
            legacy = null,
            expected = null,
            expectedError = ErrorReason.INPUT_TOO_SHORT
        ),

        Pdf417Fixture(
            name = "long input without any recognizable pattern",
            raw = "9".repeat(160),
            legacy = null,
            expected = null,
            expectedError = ErrorReason.PATTERN_NOT_FOUND
        )
    )
}
