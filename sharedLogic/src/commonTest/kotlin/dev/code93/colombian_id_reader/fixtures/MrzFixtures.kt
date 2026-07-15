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

    /**
     * Compound surname: in the MRZ, the spaces of "DE LA OSSA" and the
     * separator before "TOVAR" are the same '<' — merged on purpose.
     */
    val compoundSurnameCard: List<String> = MrzFixtureBuilder.buildTd1(
        birth = "900504",
        sex = 'M',
        expiry = "320220",
        nuip = "1020304050",
        surnames = listOf("DE", "LA", "OSSA", "TOVAR"),
        givenNames = listOf("OSWALDO")
    )

    /** One surname, one given name (Figma test document). */
    val walterosCard: List<String> = MrzFixtureBuilder.buildTd1(
        birth = "950310",
        sex = 'F',
        expiry = "330715",
        nuip = "1052478963",
        surnames = listOf("WALTEROS"),
        givenNames = listOf("LAURA")
    )
}
