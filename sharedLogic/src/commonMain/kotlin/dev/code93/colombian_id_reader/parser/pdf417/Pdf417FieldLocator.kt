package dev.code93.colombian_id_reader.parser.pdf417

/**
 * Identifies each field by *what it looks like*, not by *where it falls*
 * in the token list (ARCHITECTURE.md D4). This replaces the legacy
 * positional indexing, the `corrimiento` shift and all three PubDSK
 * name/surname branches with two anchors and one interval:
 *
 * - the **cédula token**: the token whose last 10 digits are followed by
 *   an uppercase word (the first surname);
 * - the **demographic block**: the token encoding sex + birth date + RH,
 *   in either observed shape (`0M19880821…O+` or `0219880821M…O+`);
 * - the **name tokens**: every alphabetic token strictly between the two
 *   anchors — 1 of them means first name only, 2 mean second surname +
 *   first name, 3+ mean second surname + first name + second name(s).
 */
internal object Pdf417FieldLocator {

    class LocatedFields(
        val cedula: String,
        val firstSurname: String,
        val nameTokens: List<String>,
        val sex: Char,
        val birthDateRaw: String,
        val bloodType: String
    )

    private class Demographic(val sex: Char, val birthDateRaw: String, val bloodType: String)

    /** e.g. "04620001234567GARCIA": junk digits + 10-digit cédula + first surname. */
    private val cedulaToken = Regex("^\\d*?(\\d{10})([A-Z][A-Za-z]*)$")

    /** Sex-first demographic block: 0M19880821…O+ */
    private val demographicSexFirst = Regex("^\\d([MF])(\\d{8})\\d*(AB|A|B|O)([+-])$")

    /** Date-first demographic block: 0219880821M…O+ */
    private val demographicDateFirst = Regex("^\\d{2}(\\d{8})([MF])\\d*(AB|A|B|O)([+-])$")

    private val alphabeticToken = Regex("^[A-Za-z]+$")

    fun locate(tokens: List<String>): LocatedFields? {
        var cedulaIndex = -1
        var cedulaMatch: MatchResult? = null
        for (index in tokens.indices) {
            val match = cedulaToken.matchEntire(tokens[index]) ?: continue
            cedulaIndex = index
            cedulaMatch = match
            break
        }
        if (cedulaMatch == null) return null

        val cedula = cedulaMatch.groupValues[1]
        if (cedula.all { it == '0' }) return null

        var demographicIndex = -1
        var demographic: Demographic? = null
        for (index in cedulaIndex + 1 until tokens.size) {
            demographic = demographicOf(tokens[index]) ?: continue
            demographicIndex = index
            break
        }
        if (demographic == null) return null

        val nameTokens = tokens.subList(cedulaIndex + 1, demographicIndex)
            .filter { alphabeticToken.matches(it) }
        if (nameTokens.isEmpty()) return null

        return LocatedFields(
            cedula = cedula,
            firstSurname = cedulaMatch.groupValues[2],
            nameTokens = nameTokens,
            sex = demographic.sex,
            birthDateRaw = demographic.birthDateRaw,
            bloodType = demographic.bloodType
        )
    }

    private fun demographicOf(token: String): Demographic? {
        demographicSexFirst.matchEntire(token)?.let {
            return Demographic(
                sex = it.groupValues[1][0],
                birthDateRaw = it.groupValues[2],
                bloodType = it.groupValues[3] + it.groupValues[4]
            )
        }
        demographicDateFirst.matchEntire(token)?.let {
            return Demographic(
                sex = it.groupValues[2][0],
                birthDateRaw = it.groupValues[1],
                bloodType = it.groupValues[3] + it.groupValues[4]
            )
        }
        return null
    }
}
