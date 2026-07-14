package dev.code93.colombian_id_reader.parser.pdf417

import dev.code93.colombian_id_reader.fixtures.Pdf417Fixtures
import dev.code93.colombian_id_reader.legacy.LegacyPdf417Oracle
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
