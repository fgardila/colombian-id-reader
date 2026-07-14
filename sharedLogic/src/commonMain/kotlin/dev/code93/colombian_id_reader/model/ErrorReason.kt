package dev.code93.colombian_id_reader.model

enum class ErrorReason {
    INPUT_TOO_SHORT,
    PATTERN_NOT_FOUND,
    CHECK_DIGIT_FAILED,
    UNKNOWN_FORMAT
}
