package dev.code93.colombian_id_reader.parser.pdf417

/**
 * Identifies each field by *what it looks like*, not by *where it falls*
 * in the field list (ARCHITECTURE.md D4):
 *
 * - the **cédula field**: the field whose last 10 digits are followed by
 *   the first surname (which may be compound: "…7890DE LA OSSA");
 * - the **demographic field**: sex + birth date + RH in either observed
 *   shape, optionally followed by space-glued binary junk;
 * - the **name fields**: alphabetic fields strictly between the two
 *   anchors — [second surname?, given name(s)...], each possibly
 *   containing in-field spaces.
 *
 * Known limitation: junk glued to the cédula digits by a single space
 * is rejected by the anchored prefix — observed anatomy always has a
 * separator run before that field.
 */
internal object Pdf417FieldLocator {

    class LocatedFields(
        val cedula: String,
        val firstSurname: String,
        val nameFields: List<String>,
        val sex: Char,
        val birthDateRaw: String,
        val bloodType: String
    )

    private class Demographic(val sex: Char, val birthDateRaw: String, val bloodType: String)

    /** e.g. "0462001034567890DE LA OSSA": junk digits + 10-digit cédula + first surname. */
    private val cedulaField = Regex("^\\d*?(\\d{10})([A-Z][A-Za-z ]*)$")

    /** Sex-first demographic: 0M19930815270010O+ [space-glued junk tail]. */
    private val demographicSexFirst = Regex("^\\d([MF])(\\d{8})\\d*(AB|A|B|O)([+-])(?: .*)?$")

    /** Date-first demographic: 0219880821M0045O+ [space-glued junk tail]. */
    private val demographicDateFirst = Regex("^\\d{2}(\\d{8})([MF])\\d*(AB|A|B|O)([+-])(?: .*)?$")

    private val nameField = Regex("^[A-Za-z][A-Za-z ]*$")

    fun locate(fields: List<String>): LocatedFields? {
        var cedulaIndex = -1
        var cedulaMatch: MatchResult? = null
        for (index in fields.indices) {
            val match = cedulaField.matchEntire(fields[index]) ?: continue
            cedulaIndex = index
            cedulaMatch = match
            break
        }
        if (cedulaMatch == null) return null

        val cedula = cedulaMatch.groupValues[1]
        if (cedula.all { it == '0' }) return null

        var demographicIndex = -1
        var demographic: Demographic? = null
        for (index in cedulaIndex + 1 until fields.size) {
            demographic = demographicOf(fields[index]) ?: continue
            demographicIndex = index
            break
        }
        if (demographic == null) return null

        val nameFields = fields.subList(cedulaIndex + 1, demographicIndex)
            .filter { nameField.matches(it) }
        if (nameFields.isEmpty()) return null

        return LocatedFields(
            cedula = cedula,
            firstSurname = cedulaMatch.groupValues[2],
            nameFields = nameFields,
            sex = demographic.sex,
            birthDateRaw = demographic.birthDateRaw,
            bloodType = demographic.bloodType
        )
    }

    private fun demographicOf(field: String): Demographic? {
        demographicSexFirst.matchEntire(field)?.let {
            return Demographic(
                sex = it.groupValues[1][0],
                birthDateRaw = it.groupValues[2],
                bloodType = it.groupValues[3] + it.groupValues[4]
            )
        }
        demographicDateFirst.matchEntire(field)?.let {
            return Demographic(
                sex = it.groupValues[2][0],
                birthDateRaw = it.groupValues[1],
                bloodType = it.groupValues[3] + it.groupValues[4]
            )
        }
        return null
    }
}
