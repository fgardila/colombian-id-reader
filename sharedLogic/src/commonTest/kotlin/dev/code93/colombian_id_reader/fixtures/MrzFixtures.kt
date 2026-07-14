package dev.code93.colombian_id_reader.fixtures

internal object MrzFixtures {

    /** Fixed "today" so every test is deterministic. */
    const val CURRENT_YEAR = 2026

    /** Canonical valid digital cédula: two surnames, two given names. */
    val validCard: List<String> = MrzFixtureBuilder.buildTd1(
        birth = "880821",
        sex = 'F',
        expiry = "310130",
        nuip = "1032456789",
        surnames = listOf("MARTINEZ", "GARCIA"),
        givenNames = listOf("MARIA", "DANIELA")
    )
}
