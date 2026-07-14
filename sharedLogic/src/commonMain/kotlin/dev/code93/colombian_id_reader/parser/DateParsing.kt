package dev.code93.colombian_id_reader.parser

import kotlinx.datetime.LocalDate

internal object DateParsing {

    /** "19880821" → 1988-08-21, or null if not 8 digits / not a real date. */
    fun parseYyyyMmDd(text: String): LocalDate? {
        if (text.length != 8 || !text.all { it.isDigit() }) return null
        return localDateOrNull(
            text.substring(0, 4).toInt(),
            text.substring(4, 6).toInt(),
            text.substring(6, 8).toInt()
        )
    }

    /**
     * MRZ birth date "YYMMDD" with a century pivot: a two-digit year that
     * would land in the future belongs to the 1900s (e.g. 88 → 1988,
     * 05 → 2005 when currentYear is 2026).
     */
    fun parseBirthYyMmDd(text: String, currentYear: Int): LocalDate? {
        if (text.length != 6 || !text.all { it.isDigit() }) return null
        val yy = text.substring(0, 2).toInt()
        val year = if (2000 + yy > currentYear) 1900 + yy else 2000 + yy
        return localDateOrNull(year, text.substring(2, 4).toInt(), text.substring(4, 6).toInt())
    }

    /** MRZ expiry "YYMMDD"; no Colombian digital card expires in 19xx. */
    fun parseExpiryYyMmDd(text: String): LocalDate? {
        if (text.length != 6 || !text.all { it.isDigit() }) return null
        return localDateOrNull(
            2000 + text.substring(0, 2).toInt(),
            text.substring(2, 4).toInt(),
            text.substring(4, 6).toInt()
        )
    }

    private fun localDateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate(year, month, day)
        } catch (e: IllegalArgumentException) {
            null
        }
}
