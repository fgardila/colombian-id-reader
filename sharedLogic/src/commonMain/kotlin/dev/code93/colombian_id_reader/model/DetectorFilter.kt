package dev.code93.colombian_id_reader.model

/**
 * Development aid: restricts which detectors run during a scan session.
 * Useful to isolate one leg of the pipeline while debugging a document
 * that won't read. This is NOT routing API — the declared category is
 * [ScanMode]; production clients should leave this at [ALL].
 */
enum class DetectorFilter { ALL, PDF417_ONLY, MRZ_ONLY }
