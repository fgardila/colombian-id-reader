package dev.code93.colombian_id_reader.model

import kotlinx.datetime.LocalDate

/**
 * Unified data object for both document generations.
 *
 * Names are exposed as two merged strings because a first/second split
 * is not recoverable from either encoding: the MRZ writes the space
 * inside a compound surname ("DE LA OSSA") and the separator between
 * surnames with the same '<' character, and the PDF417 only marks the
 * boundary between the first surname and the rest. Splitting further
 * would silently corrupt compound names.
 *
 * Nullability is honest: fields a given source cannot provide are
 * `null`. The digital card's MRZ does not carry blood type; the yellow
 * card's PDF417 does not carry an expiration date.
 */
data class IdCardData(
    /** NUIP, normalized (no leading zeros). */
    val documentNumber: String,
    /** e.g. "FABIAN GUILLERMO" or "MARIA DEL MAR". */
    val givenNames: String,
    /** e.g. "ARDILA CASTRO" or "DE LA OSSA TOVAR". */
    val surnames: String,
    val birthDate: LocalDate?,
    val sex: Sex,
    /** e.g. "O+"; PDF417 only, null for MRZ. */
    val bloodType: String?,
    /** MRZ only, null for PDF417. */
    val expirationDate: LocalDate?,
    val source: DocumentSource
) {
    /** The resolved document type, derived from the reading evidence. */
    val documentType: DocumentType
        get() = when (source) {
            DocumentSource.PDF417 -> DocumentType.CEDULA_AMARILLA
            DocumentSource.MRZ -> DocumentType.CEDULA_DIGITAL
        }
}
