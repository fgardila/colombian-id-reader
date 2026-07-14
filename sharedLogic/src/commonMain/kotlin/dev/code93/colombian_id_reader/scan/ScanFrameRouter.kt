package dev.code93.colombian_id_reader.scan

import dev.code93.colombian_id_reader.ColombianIdParser
import dev.code93.colombian_id_reader.model.ScanMode
import dev.code93.colombian_id_reader.model.ScanResult
import dev.code93.colombian_id_reader.parser.mrz.MrzCandidateExtractor

/**
 * Per-frame detector sequencing for [ScanMode] (ARCHITECTURE.md §5):
 * PDF417 first and, only when no barcode is decoded, the text
 * recognizer looking for the TD1 pattern.
 *
 * Generic over the frame type [F] so the routing logic is pure and
 * shared: Android plugs ML Kit detectors, iOS (Phase 3) plugs Vision.
 *
 * Return contract of [process]: a [ScanResult.Success] means done;
 * [ScanResult.Error] and null both mean "nothing usable on this frame,
 * keep scanning" — errors are expected on partial frames and misreads.
 */
internal class ScanFrameRouter<F>(
    private val mode: ScanMode,
    private val pdf417: suspend (F) -> String?,
    private val mrzOcr: suspend (F) -> List<String>
) {

    suspend fun process(frame: F): ScanResult? {
        if (mode != ScanMode.MRZ_ONLY) {
            val raw = pdf417(frame)
            if (raw != null) {
                val result = ColombianIdParser.parsePdf417(raw)
                if (result is ScanResult.Success) return result
                // A decoded barcode that fails to parse is a partial or
                // foreign PDF417 — fall through and keep scanning.
            }
            if (mode == ScanMode.PDF417_ONLY) return null
        }
        val candidate = MrzCandidateExtractor.extract(mrzOcr(frame)) ?: return null
        return ColombianIdParser.parseMrz(candidate)
    }
}
