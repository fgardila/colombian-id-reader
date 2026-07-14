package dev.code93.colombian_id_reader.parser.mrz

/**
 * ICAO 9303 check digit: each character is mapped to a value
 * (0-9 → face value, A-Z → 10-35, '<' → 0), multiplied by the cyclic
 * weights 7, 3, 1, and the sum is taken modulo 10.
 */
internal object MrzCheckDigit {

    private val WEIGHTS = intArrayOf(7, 3, 1)

    /** Returns null if [field] contains a character outside the MRZ alphabet. */
    fun compute(field: String): Int? {
        var sum = 0
        for ((index, char) in field.withIndex()) {
            val value = when (char) {
                in '0'..'9' -> char - '0'
                in 'A'..'Z' -> char - 'A' + 10
                '<' -> 0
                else -> return null
            }
            sum += value * WEIGHTS[index % 3]
        }
        return sum % 10
    }

    fun validate(field: String, checkDigit: Char): Boolean =
        checkDigit in '0'..'9' && compute(field) == checkDigit - '0'
}
