package dev.code93.colombian_id_reader.fixtures

import dev.code93.colombian_id_reader.legacy.LegacyPdf417Oracle.LegacyInfo
import dev.code93.colombian_id_reader.model.DocumentType
import dev.code93.colombian_id_reader.model.ErrorReason
import dev.code93.colombian_id_reader.model.ScannedDocument
import dev.code93.colombian_id_reader.model.Sex
import kotlinx.datetime.LocalDate

/**
 * Golden set for the PDF417 parser (ARCHITECTURE.md §8, Phase 1a).
 *
 * The raw strings are synthetic, built to the exact anatomy the legacy
 * parser demands — plus one real donated card (the last success
 * fixture), which validates the synthetic anatomy against reality. Each
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
 *   3. Legacy exposed four positional name fields (with " "/""
 *      placeholders); the new model exposes two merged strings —
 *      compound names ("DE LA OSSA") make a 4-way split unrecoverable.
 *   4. Names with Ñ/accents: the character degrades to an in-field
 *      space ("NI O") — legacy crashed on these.
 *   5. Negative blood types: the legacy normalizer's allowed set kept '+'
 *      but not '-', so "A-" lost its sign and legacy rh became the last
 *      two surviving chars (e.g. "1A"). The new normalizer keeps '-' and
 *      returns the real "A-".
 */
internal data class Pdf417Fixture(
    val name: String,
    val raw: String,
    val legacy: LegacyInfo?,
    val expected: ScannedDocument.ColombianId?,
    val expectedError: ErrorReason? = null
)

internal object Pdf417Fixtures {

    /**
     * Joins fields with a 3-space run (real cards pad fixed-width fields
     * with separator runs; single spaces stay inside a field) and
     * left-pads with a filler field of '9's so the total length passes
     * the legacy `< 150` gate. The run is invisible to the legacy
     * oracle, which collapses any separator run.
     */
    private fun rawOf(vararg fields: String): String {
        val body = fields.joinToString("   ")
        val padding = (150 - body.length - 3).coerceAtLeast(4)
        return "9".repeat(padding) + "   " + body
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
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "1234567",
                givenNames = "JUAN CARLOS",
                surnames = "GARCIA RODRIGUEZ",
                birthDate = LocalDate(1988, 8, 21), sex = Sex.MALE,
                bloodType = "O+", expirationDate = null
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
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "52147896",
                givenNames = "ANA",
                surnames = "LOPEZ TORRES",
                birthDate = LocalDate(1991, 3, 5), sex = Sex.FEMALE,
                bloodType = "A-", expirationDate = null
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
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "9876543",
                givenNames = "LUIS MIGUEL",
                surnames = "MARTINEZ PEREZ",
                birthDate = LocalDate(1975, 12, 1), sex = Sex.MALE,
                bloodType = "AB+", expirationDate = null
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
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "1023456789",
                givenNames = "MARIA JOSE",
                surnames = "DIAZ CASTRO",
                birthDate = LocalDate(2002, 4, 15), sex = Sex.FEMALE,
                bloodType = "B-", expirationDate = null
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
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "1234567",
                givenNames = "JUAN CARLOS",
                surnames = "GARCIA RODRIGUEZ",
                birthDate = null, sex = Sex.MALE,
                bloodType = "O+", expirationDate = null
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
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "34567891",
                givenNames = "PEDRO PABLO",
                surnames = "HERNANDEZ GOMEZ",
                birthDate = LocalDate(1967, 7, 24), sex = Sex.MALE,
                bloodType = "O+", expirationDate = null
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
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "45678912",
                givenNames = "DIANA",
                surnames = "RUIZ VARGAS",
                birthDate = LocalDate(1985, 6, 12), sex = Sex.FEMALE,
                bloodType = "O-", expirationDate = null
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
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "56789123",
                givenNames = "CAMILO",
                surnames = "SALAZAR",
                birthDate = LocalDate(1999, 2, 28), sex = Sex.MALE,
                bloodType = "A+", expirationDate = null
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
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "67891234",
                givenNames = "ANDRES FELIPE",
                surnames = "MORENO JIMENEZ",
                birthDate = LocalDate(1973, 3, 17), sex = Sex.MALE,
                bloodType = "B+", expirationDate = null
            )
        ),

        Pdf417Fixture(
            name = "nonPubDsk enye in second name (legacy crashed; degrades to in-field space)",
            raw = rawOf(
                "22", "04910078912345ACOSTA", "BELLO", "MARIO", "NIÑO",
                "0M199408190022O+"
            ),
            legacy = null,
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "78912345",
                givenNames = "MARIO NI O",
                surnames = "ACOSTA BELLO",
                birthDate = LocalDate(1994, 8, 19), sex = Sex.MALE,
                bloodType = "O+", expirationDate = null
            )
        ),

        Pdf417Fixture(
            name = "nonPubDsk compound first surname (De La Ossa Tovar)",
            // Single spaces INSIDE the field are part of the compound
            // surname; the legacy positional code garbled this shape
            // (and its oracle crashes on it), the field-aware pipeline
            // keeps it whole.
            raw = rawOf(
                "22", "0462001034567890DE LA OSSA", "TOVAR", "OSWALDO",
                "0M198808210060O+"
            ),
            legacy = null,
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "1034567890",
                givenNames = "OSWALDO",
                surnames = "DE LA OSSA TOVAR",
                birthDate = LocalDate(1988, 8, 21), sex = Sex.MALE,
                bloodType = "O+", expirationDate = null
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
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "1234567",
                givenNames = "JUAN CARLOS",
                surnames = "GARCIA RODRIGUEZ",
                birthDate = LocalDate(1988, 8, 21), sex = Sex.MALE,
                bloodType = "O+", expirationDate = null
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
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "52147896",
                givenNames = "ANA",
                surnames = "LOPEZ TORRES",
                birthDate = LocalDate(1991, 3, 5), sex = Sex.FEMALE,
                bloodType = "A+", expirationDate = null
            )
        ),

        Pdf417Fixture(
            name = "owner-verified card: PubDSK_1, two surnames two names, male O+",
            // The field values below were verified by the project owner
            // against a real scan of their own card. The raw document
            // itself is NOT committed (its binary sections carry
            // sensitive payload); this raw string is a synthetic
            // reconstruction of the exact anatomy the real scan showed:
            // PubDSK_1 marker with underscore, a short (<=7 chars) token
            // before the cedula token (no pre-shift), field separators
            // arriving as runs of spaces, sex-first demographic block,
            // and binary junk after the demographic block (rendered here
            // as harmless filler, including a newline and a tab).
            raw = "0123456789              PubDSK_1        123456  " +
                "1098741992ARDILA                 CASTRO                 " +
                "FABIAN                 GUILLERMO              " +
                "0M19930815000000O+ 2C _ab 3Cs xY9 t-w q'&] =[<\nz-~a\tE",
            legacy = LegacyInfo(
                primerApellido = "ARDILA", segundoApellido = "CASTRO",
                primerNombre = "FABIAN", segundoNombre = "GUILLERMO",
                cedula = "1098741992", rh = "O+",
                fechaNacimiento = "19930815", sexo = "M"
            ),
            expected = ScannedDocument.ColombianId(
                documentType = DocumentType.CEDULA_AMARILLA,
                nuip = "1098741992",
                givenNames = "FABIAN GUILLERMO",
                surnames = "ARDILA CASTRO",
                birthDate = LocalDate(1993, 8, 15), sex = Sex.MALE,
                bloodType = "O+", expirationDate = null
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
