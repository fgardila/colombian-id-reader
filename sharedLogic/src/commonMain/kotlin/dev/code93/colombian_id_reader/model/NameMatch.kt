package dev.code93.colombian_id_reader.model

/**
 * Result of cross-checking the names OCR'd from the FRONT of the card
 * against the names decoded from the back (PDF417 / MRZ) — a *linkage*
 * signal that front and back belong to the same document
 * (ARCHITECTURE-1.0.0.md §6).
 *
 * The SDK measures; the client decides. This is a signal, not an
 * anti-fraud control: it runs OCR over a security-printed background
 * and will sometimes fail on perfectly genuine documents (§6.4).
 */
enum class NameMatch {
    /** Front names match the decoded names within OCR tolerance. */
    MATCH,

    /** Usable front text was read, but it does not match the decoded names. */
    MISMATCH,

    /**
     * No usable front text (OCR yielded nothing over the guilloches),
     * image capture was not requested, or the mode does not support the
     * cross-check (passports are data-page only).
     */
    NOT_CHECKED
}
