package dev.code93.colombian_id_reader.model

/**
 * The captured document images (ARCHITECTURE-1.0.0.md §4.4), JPEG bytes
 * of the frames the recognizer worked on — cropped to the document and
 * rotated upright. Raw [ByteArray], never Base64 (D14): Base64 is +33%
 * larger and leaks trivially into logs and crash reports.
 *
 * **Privacy (§7):** these bytes include the holder's facial photograph
 * and signature — biometric data, a reinforced sensitive category under
 * Ley 1581 de 2012. The SDK hands them over and forgets them; consent
 * and retention are the client's responsibility. Drop the reference as
 * soon as the bytes are persisted or discarded, call [dispose] to
 * zero-fill the buffers, and never log them.
 *
 * Deliberately NOT a data class: ByteArray equality is identity-based,
 * and generated toString/equals on image bytes invite accidental leaks.
 */
class DocumentImages(
    /** Front of the document; null when front encoding failed (best effort). */
    val front: ByteArray?,
    /** Back of the document — the frame the parsed data came from. */
    val back: ByteArray,
    val format: ImageFormat = ImageFormat.JPEG
) {

    /** True after [dispose]; the arrays then contain only zeros. */
    var isDisposed: Boolean = false
        private set

    /**
     * Zero-fills both buffers in place (idempotent). The images are
     * unrecoverable afterwards; call once the client has persisted or
     * discarded them so the face/signature stops lingering on the heap
     * until GC (§7).
     */
    fun dispose() {
        if (isDisposed) return
        front?.fill(0)
        back.fill(0)
        isDisposed = true
    }

    /** Sizes only — image bytes must never reach logs (§7). */
    override fun toString(): String =
        "DocumentImages(front=${front?.size?.let { "$it bytes" } ?: "null"}, " +
            "back=${back.size} bytes, format=$format, disposed=$isDisposed)"
}
