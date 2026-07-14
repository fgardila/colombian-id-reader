package dev.code93.colombian_id_reader.legacy

/**
 * Test-only, faithful transliteration of the 2020 parser
 * (doc/IngresoActivityVision.java, parseDataCode) used as the behavioral
 * oracle for characterization tests. Warts preserved on purpose:
 * positional indexing, the `corrimiento` shift, the PubDSK branch with its
 * three name/surname cases, `substring(lastCapitalIndex - 10, ...)`.
 *
 * Deliberate transliteration notes:
 * - Java's `\p{Alpha}`/`\p{Digit}` without UNICODE_CHARACTER_CLASS are
 *   exactly ASCII `[a-zA-Z]`/`[0-9]`, so the normalizer regex is written
 *   with explicit ASCII classes (POSIX classes are unreliable on
 *   Kotlin/Native).
 * - Java's String.split drops trailing empty strings but keeps a leading
 *   one; Kotlin's split keeps both, so [javaSplit] trims the tail.
 * - The legacy Activity caught StringIndexOutOfBoundsException around the
 *   call (error dialog) and crashed on any other exception; the oracle
 *   maps every index error to `null` ("legacy could not read this").
 */
internal object LegacyPdf417Oracle {

    data class LegacyInfo(
        val primerApellido: String,
        val segundoApellido: String,
        val primerNombre: String,
        val segundoNombre: String,
        val cedula: String,
        val rh: String,
        val fechaNacimiento: String,
        val sexo: String
    )

    private val nonAlphanumeric = Regex("[^A-Za-z0-9+_]+")
    private val capitalLetter = Regex("[A-Z]")

    fun parse(barcode: String): LegacyInfo? {
        if (barcode.length < 150) return null
        return try {
            doParse(barcode)
        } catch (e: IndexOutOfBoundsException) {
            null
        }
    }

    private fun doParse(barcode: String): LegacyInfo {
        var primerApellido = ""
        var segundoApellido = ""
        var primerNombre = ""
        var segundoNombre = ""
        val cedula: String
        val rh: String
        val fechaNacimiento: String
        val sexo: String

        val alphaAndDigits = barcode.replace(nonAlphanumeric, " ")
        val splitStr = javaSplit(alphaAndDigits)

        if (!alphaAndDigits.contains("PubDSK")) {
            var corrimiento = 0

            val lastCapitalIndex = capitalLetter.find(splitStr[2 + corrimiento])?.range?.first ?: -1
            cedula = splitStr[2 + corrimiento].substring(lastCapitalIndex - 10, lastCapitalIndex)
            primerApellido = splitStr[2 + corrimiento].substring(lastCapitalIndex)
            segundoApellido = splitStr[3 + corrimiento]
            primerNombre = splitStr[4 + corrimiento]
            // Se verifica que contenga segundo nombre
            if (splitStr[5 + corrimiento][0].isDigit()) {
                corrimiento--
            } else {
                segundoNombre = splitStr[5 + corrimiento]
            }

            sexo = splitStr[6 + corrimiento]
            rh = splitStr[6 + corrimiento].let { it.substring(it.length - 2) }
            fechaNacimiento = splitStr[6 + corrimiento].substring(2, 10)
        } else {
            var corrimiento = 0
            if (splitStr[2 + corrimiento].length > 7) {
                corrimiento--
            }

            val lastCapitalIndex = capitalLetter.find(splitStr[3 + corrimiento])?.range?.first ?: -1
            cedula = splitStr[3 + corrimiento].substring(lastCapitalIndex - 10, lastCapitalIndex)
            primerApellido = splitStr[3 + corrimiento].substring(lastCapitalIndex)
            segundoApellido = splitStr[4 + corrimiento]
            if (splitStr[5 + corrimiento].startsWith("0")) { // UN NOMBRE UN APELLIDO
                segundoApellido = " "
                primerNombre = splitStr[4 + corrimiento]
                sexo = if (splitStr[5 + corrimiento].contains("M")) "M" else "F"
                rh = splitStr[5 + corrimiento].let { it.substring(it.length - 2) }
                fechaNacimiento = splitStr[5 + corrimiento].substring(2, 10)
            } else if (splitStr[6 + corrimiento].startsWith("0")) { // DOS APELLIDOS UN NOMBRE
                primerNombre = splitStr[5 + corrimiento]
                segundoNombre = " "
                sexo = if (splitStr[6 + corrimiento].contains("M")) "M" else "F"
                rh = splitStr[6 + corrimiento].let { it.substring(it.length - 2) }
                fechaNacimiento = splitStr[6 + corrimiento].substring(2, 10)
            } else { // DOS APELLIDOS DOS NOMBRES
                primerNombre = splitStr[5 + corrimiento]
                segundoNombre = splitStr[6 + corrimiento]
                sexo = if (splitStr[7 + corrimiento].contains("M")) "M" else "F"
                rh = splitStr[7 + corrimiento].let { it.substring(it.length - 2) }
                fechaNacimiento = splitStr[7 + corrimiento].substring(2, 10)
            }
        }

        return LegacyInfo(
            primerApellido = primerApellido,
            segundoApellido = segundoApellido,
            primerNombre = primerNombre,
            segundoNombre = segundoNombre,
            cedula = cedula,
            rh = rh,
            fechaNacimiento = fechaNacimiento,
            sexo = sexo
        )
    }

    private fun javaSplit(s: String): List<String> {
        val parts = s.split(Regex("\\s+"))
        var end = parts.size
        while (end > 0 && parts[end - 1].isEmpty()) end--
        return parts.subList(0, end)
    }
}
