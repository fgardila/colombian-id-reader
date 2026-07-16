package dev.code93.colombian_id_reader.model

/**
 * Framing guidance emitted by the capture gate
 * (ARCHITECTURE-0.2.0.md §6). The bundled scanning UIs render these as
 * localized text; clients with their own UI receive them through the
 * optional `onGateHint` callback. Wording is the client's to localize —
 * the library exposes the conditions.
 */
enum class GateHint {
    /** No card-shaped object in view. */
    NO_DOCUMENT,

    /** Candidate present but too small — get closer. */
    TOO_SMALL,

    /** Candidate present but tilted — straighten the document. */
    SKEWED,

    /** Candidate framed but blurry or moving — hold steady. */
    HOLD_STEADY,

    /** Gate open: the document is being read. */
    PASS
}
