package dev.code93.colombian_id_reader.model

import kotlinx.datetime.LocalDate

/**
 * Unified data object for both document generations.
 *
 * Nullability is honest: fields a given source cannot provide are `null`.
 * The digital card's MRZ does not carry blood type; the yellow card's
 * PDF417 does not carry an expiration date.
 */
data class IdCardData(
    /** NUIP, normalized (no leading zeros). */
    val documentNumber: String,
    val firstName: String,
    val secondName: String?,
    val firstSurname: String,
    val secondSurname: String?,
    val birthDate: LocalDate?,
    val sex: Sex,
    /** e.g. "O+"; PDF417 only, null for MRZ. */
    val bloodType: String?,
    /** MRZ only, null for PDF417. */
    val expirationDate: LocalDate?,
    val source: DocumentSource
)
