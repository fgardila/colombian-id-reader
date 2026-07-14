package dev.code93.colombian_id_reader

import dev.code93.colombian_id_reader.model.ScanResult
import dev.code93.colombian_id_reader.parser.mrz.Td1MrzParser
import dev.code93.colombian_id_reader.parser.pdf417.Pdf417Parser
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Public parsing API (ARCHITECTURE.md §5): pure functions from raw
 * scanner output to [ScanResult]. No I/O, no persistence, no logging.
 */
object ColombianIdParser {

    /** Parses the raw PDF417 payload of a cédula amarilla. */
    fun parsePdf417(raw: String): ScanResult = Pdf417Parser.parse(raw)

    /**
     * Parses the three MRZ (TD1) lines of a cédula digital. Lines may
     * arrive with OCR noise (case, spaces) or as fewer strings containing
     * newlines; both are normalized before validation.
     */
    fun parseMrz(rawLines: List<String>): ScanResult =
        Td1MrzParser.parse(rawLines, currentYear())

    @OptIn(ExperimentalTime::class)
    private fun currentYear(): Int =
        Clock.System.todayIn(TimeZone.currentSystemDefault()).year
}
