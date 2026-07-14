package dev.code93.colombian_id_reader.model

sealed interface ScanResult {
    data class Success(val data: IdCardData) : ScanResult
    data class Error(val reason: ErrorReason) : ScanResult
}
