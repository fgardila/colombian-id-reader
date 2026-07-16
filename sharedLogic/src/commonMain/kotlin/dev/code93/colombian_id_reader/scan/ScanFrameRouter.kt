package dev.code93.colombian_id_reader.scan

import dev.code93.colombian_id_reader.ColombianIdParser
import dev.code93.colombian_id_reader.model.DetectorFilter
import dev.code93.colombian_id_reader.model.ScanMode
import dev.code93.colombian_id_reader.model.ScanResult
import dev.code93.colombian_id_reader.parser.mrz.MrzCandidateExtractor

/**
 * Stage 2 of the capture pipeline: per-frame detector sequencing.
 *
 * [ScanMode.ColombianId]: PDF417 first and, only when no barcode is
 * decoded, the text recognizer looking for the TD1 pattern.
 * [ScanMode.Passport]: passports carry no PDF417 — the barcode leg is
 * skipped entirely and OCR lines route to TD3 extraction.
 *
 * The router is pure: the capture gate (Stage 1) decides elsewhere and
 * hands its verdict in as [process]'s `allowOcr` — the cheap,
 * self-validating PDF417 leg stays hot regardless (a wall never
 * decodes), while the expensive OCR leg only runs behind an open gate.
 *
 * Generic over the frame type [F] so the routing logic is shared:
 * Android plugs ML Kit detectors, iOS plugs Vision.
 *
 * Return contract of [process]: a [ScanResult.Success] means done;
 * [ScanResult.Error] and null both mean "nothing usable on this frame,
 * keep scanning" — errors are expected on partial frames and misreads.
 */
internal class ScanFrameRouter<F>(
    private val mode: ScanMode,
    private val filter: DetectorFilter,
    private val pdf417: suspend (F) -> String?,
    private val mrzOcr: suspend (F) -> List<String>
) {

    private var warnedUselessFilter = false

    suspend fun process(frame: F, allowOcr: Boolean = true): ScanResult? {
        return when (mode) {
            ScanMode.ColombianId -> processColombianId(frame, allowOcr)
            ScanMode.Passport -> processPassport(frame, allowOcr)
        }
    }

    private suspend fun processColombianId(frame: F, allowOcr: Boolean): ScanResult? {
        if (filter != DetectorFilter.MRZ_ONLY) {
            val raw = pdf417(frame)
            if (raw != null) {
                val result = ColombianIdParser.parsePdf417(raw)
                if (result is ScanResult.Success) return result
                // A decoded barcode that fails to parse is a partial or
                // foreign PDF417 — fall through and keep scanning.
            }
            if (filter == DetectorFilter.PDF417_ONLY) return null
        }
        if (!allowOcr) return null
        val candidate = MrzCandidateExtractor.extractTd1(mrzOcr(frame)) ?: return null
        return ColombianIdParser.parseMrz(candidate)
    }

    private suspend fun processPassport(frame: F, allowOcr: Boolean): ScanResult? {
        if (filter == DetectorFilter.PDF417_ONLY) {
            // Honor the dev flag literally rather than making it lie.
            if (!warnedUselessFilter) {
                warnedUselessFilter = true
                ScanDebug.log {
                    "PDF417_ONLY with ScanMode.Passport: passports have no PDF417; " +
                        "nothing will be read"
                }
            }
            return null
        }
        if (!allowOcr) return null
        val candidate = MrzCandidateExtractor.extractTd3(mrzOcr(frame)) ?: return null
        return ColombianIdParser.parseMrzTd3(candidate)
    }
}
