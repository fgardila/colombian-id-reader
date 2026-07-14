package dev.code93.colombian_id_reader.parser.pdf417

import dev.code93.colombian_id_reader.fixtures.Pdf417Fixtures
import dev.code93.colombian_id_reader.legacy.LegacyPdf417Oracle
import dev.code93.colombian_id_reader.model.ScanResult
import dev.code93.colombian_id_reader.model.Sex
import dev.code93.colombian_id_reader.parser.DateParsing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Locks the golden set: every hand-derived legacy expectation in
 * [Pdf417Fixtures] must match what the transliterated 2020 code actually
 * produces. If this test fails, the *fixture* (or the transliteration) is
 * wrong — not the new parser.
 */
class Pdf417OracleAgreementTest {

    @Test
    fun oracleReproducesFrozenGoldenExpectations() {
        for (fixture in Pdf417Fixtures.all) {
            assertEquals(
                fixture.legacy,
                LegacyPdf417Oracle.parse(fixture.raw),
                "fixture: ${fixture.name}"
            )
        }
    }

    /**
     * Field-by-field agreement between the new parser and the legacy
     * oracle on every input the legacy code could read. Blood type is
     * excluded here: legacy mangled "AB±" and negative types (divergences
     * 1 and 5); its exact new-parser value is frozen per fixture in
     * Pdf417CharacterizationTest instead.
     */
    @Test
    fun newParserAgreesWithOracleWhereLegacyCouldRead() {
        for (fixture in Pdf417Fixtures.all) {
            val legacy = fixture.legacy ?: continue
            val name = "fixture: ${fixture.name}"
            val data = assertIs<ScanResult.Success>(Pdf417Parser.parse(fixture.raw), name).data

            assertEquals(legacy.cedula.trimStart('0'), data.documentNumber, name)
            assertEquals(legacy.primerApellido, data.firstSurname, name)
            assertEquals(legacy.segundoApellido.takeIf { it.isNotBlank() }, data.secondSurname, name)
            assertEquals(legacy.primerNombre, data.firstName, name)
            assertEquals(legacy.segundoNombre.takeIf { it.isNotBlank() }, data.secondName, name)
            // Legacy sex heuristic was contains("M") over its sexo value.
            assertEquals(
                if (legacy.sexo.contains("M")) Sex.MALE else Sex.FEMALE,
                data.sex,
                name
            )
            assertEquals(DateParsing.parseYyyyMmDd(legacy.fechaNacimiento), data.birthDate, name)
        }
    }
}
