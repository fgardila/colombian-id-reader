package dev.code93.colombian_id_reader.parser.pdf417

import dev.code93.colombian_id_reader.fixtures.Pdf417Fixtures
import dev.code93.colombian_id_reader.model.ScanResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The new parser must reproduce the frozen golden expectations, including
 * the five documented divergences from the 2020 code (see Pdf417Fixtures).
 */
class Pdf417CharacterizationTest {

    @Test
    fun reproducesFrozenExpectations() {
        for (fixture in Pdf417Fixtures.all) {
            val result = Pdf417Parser.parse(fixture.raw)
            if (fixture.expected != null) {
                val success = assertIs<ScanResult.Success>(result, "fixture: ${fixture.name}")
                assertEquals(fixture.expected, success.data, "fixture: ${fixture.name}")
            } else {
                val error = assertIs<ScanResult.Error>(result, "fixture: ${fixture.name}")
                assertEquals(fixture.expectedError, error.reason, "fixture: ${fixture.name}")
            }
        }
    }
}
