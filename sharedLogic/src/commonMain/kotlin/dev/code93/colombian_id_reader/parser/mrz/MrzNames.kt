package dev.code93.colombian_id_reader.parser.mrz

/**
 * Shared MRZ name-field parsing: '<<' separates the surname group from
 * the given-names group; a single '<' is BOTH the word separator inside
 * a group and the space inside a compound name ("DE<LA<OSSA") —
 * indistinguishable by design, which is why the groups are returned
 * merged (D8). Used by both TD1 (line 3) and TD3 (line 1 tail).
 */
internal object MrzNames {

    class Parts(val surnames: String, val givenNames: String)

    fun parse(field: String): Parts? {
        val content = field.trimEnd('<')
        val separator = content.indexOf("<<")
        if (separator < 0) return null

        val surnames = content.substring(0, separator).split('<').filter { it.isNotEmpty() }
        val givenNames = content.substring(separator + 2).split('<').filter { it.isNotEmpty() }
        if (surnames.isEmpty() || givenNames.isEmpty()) return null

        return Parts(
            surnames = surnames.joinToString(" "),
            givenNames = givenNames.joinToString(" ")
        )
    }
}
