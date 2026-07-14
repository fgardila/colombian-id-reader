package dev.code93.colombian_id_reader.model

/**
 * Which detectors the scanning screen runs on each camera frame.
 *
 * [AUTO] serves both document generations: PDF417 first and, if no
 * barcode is found on the frame, the text recognizer looking for the
 * TD1 MRZ pattern.
 */
enum class ScanMode { AUTO, PDF417_ONLY, MRZ_ONLY }
