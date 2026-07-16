package dev.code93.colombian_id_reader.fixtures

internal object Td3Fixtures {

    /** Fixed "today" so every test is deterministic. */
    const val CURRENT_YEAR = 2026

    /**
     * The public ICAO 9303 Part 4 specimen (Utopia, ERIKSSON ANNA
     * MARIA) — pinned as literals: it deterministically validates the
     * composite segmentation and all five check digits against the
     * standard itself.
     */
    val icaoSpecimen: List<String> = listOf(
        "P<UTOERIKSSON<<ANNA<MARIA".padEnd(44, '<'),
        "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
    )

    /** A plausible Colombian passport (synthetic, valid check digits). */
    val colombianPassport: List<String> = Td3FixtureBuilder.buildTd3(
        passportNumber = "AB1234567",
        birth = "880821",
        sex = 'F',
        expiry = "310130",
        surnames = listOf("MARTINEZ", "GARCIA"),
        givenNames = listOf("MARIA", "DANIELA")
    )
}
