package dev.code93.colombian_id_reader.model

/**
 * Development aid: restricts which detectors run during a scan session.
 * Useful to isolate one leg of the pipeline while debugging a document
 * that won't read. This is NOT routing API — the declared category is
 * [ScanMode]; production clients should leave this at [ALL].
 *
 * With [ScanMode.Passport] there is no PDF417 leg: [MRZ_ONLY] behaves
 * like [ALL], and [PDF417_ONLY] reads nothing at all (the flag is
 * honored literally; a one-time ScanDebug warning is emitted).
 */
enum class DetectorFilter { ALL, PDF417_ONLY, MRZ_ONLY }
