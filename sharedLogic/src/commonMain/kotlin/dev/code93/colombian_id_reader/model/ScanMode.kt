package dev.code93.colombian_id_reader.model

/**
 * The document CATEGORY the client declares before scanning
 * (ARCHITECTURE-0.2.0.md §4.2). The declared mode resolves what a
 * person knows without thinking ("I have a cédula"); the evidence
 * resolves what the machine knows better (amarilla vs digital) — the
 * user is never asked a technical question.
 *
 * The capture gate is configured from the mode (ColombianId → ID-1
 * geometry). For detector-level debugging see [DetectorFilter].
 */
sealed interface ScanMode {

    /** Cédula amarilla or digital — resolved by evidence, not by the user. */
    data object ColombianId : ScanMode

    // data object Passport : ScanMode   // Reserved for 0.3.0 (TD3, Id3).
}
