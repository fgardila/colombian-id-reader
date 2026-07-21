package dev.code93.colombian_id_reader.model

/**
 * Everything one scan produced (ARCHITECTURE-1.0.0.md): the parsed
 * document, optionally the captured images, and the front/back name
 * cross-check verdict. This is what the scanner UIs deliver via
 * `onResult` since 1.0.0.
 *
 * Not a data class: [DocumentImages] compares by identity, and a
 * generated copy/equals over image-bearing state adds surface without
 * value.
 */
class ScanCapture(
    val document: ScannedDocument,
    /**
     * Null unless image capture was requested — and always null in
     * passport mode, which stays data-page-only (no image capture).
     */
    val images: DocumentImages?,
    /** [NameMatch.NOT_CHECKED] whenever [images] is not requested. */
    val nameMatch: NameMatch
) {
    override fun toString(): String =
        "ScanCapture(document=$document, images=$images, nameMatch=$nameMatch)"
}
