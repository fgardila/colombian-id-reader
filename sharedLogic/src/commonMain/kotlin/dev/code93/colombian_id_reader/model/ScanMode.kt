package dev.code93.colombian_id_reader.model

/**
 * The document CATEGORY the client declares before scanning
 * (ARCHITECTURE-0.2.0.md §4.2). The declared mode resolves what a
 * person knows without thinking ("I have a cédula" vs "I have a
 * passport"); the evidence resolves what the machine knows better
 * (amarilla vs digital) — the user is never asked a technical question.
 *
 * The capture gate is configured from the mode via [acceptedFormats].
 * For detector-level debugging see [DetectorFilter].
 */
sealed interface ScanMode {

    /** Cédula amarilla or digital — resolved by evidence, not by the user. */
    data object ColombianId : ScanMode

    /**
     * Machine-readable passport (MRZ TD3), any issuing state. Reading a
     * passport is NOT identity verification — the library returns what
     * is printed, with no authenticity check.
     */
    data object Passport : ScanMode
}

/** Gate geometry for the declared category (D12). */
val ScanMode.acceptedFormats: Set<DocumentFormat>
    get() = when (this) {
        ScanMode.ColombianId -> setOf(DocumentFormat.Id1)
        ScanMode.Passport -> setOf(DocumentFormat.Id3)
    }
