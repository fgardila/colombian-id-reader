package dev.code93.colombian_id_reader.model

sealed interface ScanResult {
    data class Success(val data: ScannedDocument) : ScanResult
    data class Error(val reason: ErrorReason) : ScanResult
}
