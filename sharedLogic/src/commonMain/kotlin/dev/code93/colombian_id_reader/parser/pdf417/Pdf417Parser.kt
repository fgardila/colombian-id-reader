package dev.code93.colombian_id_reader.parser.pdf417

import dev.code93.colombian_id_reader.model.ErrorReason
import dev.code93.colombian_id_reader.model.ScanResult

/**
 * PDF417 parser for the cédula amarilla: normalize → locate fields by
 * pattern → map to IdCardData (ARCHITECTURE.md §6.2).
 */
internal object Pdf417Parser {

    /** Same gate as the 2020 code: anything shorter cannot be a full payload. */
    private const val MIN_RAW_LENGTH = 150

    fun parse(raw: String): ScanResult {
        if (raw.length < MIN_RAW_LENGTH) {
            return ScanResult.Error(ErrorReason.INPUT_TOO_SHORT)
        }
        val tokens = Pdf417Normalizer.tokenize(raw)
        val fields = Pdf417FieldLocator.locate(tokens)
            ?: return ScanResult.Error(ErrorReason.PATTERN_NOT_FOUND)
        return ScanResult.Success(Pdf417FieldMapper.map(fields))
    }
}
