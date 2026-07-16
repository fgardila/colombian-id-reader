package dev.code93.colombian_id_reader.model

import kotlinx.datetime.LocalDate

/**
 * A successfully read document (ARCHITECTURE-0.3.0.md §5). Each subtype
 * exposes only the fields its document actually carries — a flat
 * nullable-everything type would force clients to know which
 * combinations are valid, the exact class of debt this library was
 * created to avoid.
 *
 * Swift note: the sealed hierarchy exports without exhaustive-switch
 * support; branch on [documentType] (an enum) and cast:
 *
 * ```swift
 * switch data.documentType {
 * case .passport:       let p = data as! ScannedDocumentPassport
 * case .cedulaAmarilla,
 *      .cedulaDigital:  let c = data as! ScannedDocumentColombianId
 * default: break
 * }
 * ```
 */
sealed interface ScannedDocument {
    val documentType: DocumentType
    /** Merged (D8): "FABIAN GUILLERMO", "MARIA DEL MAR". */
    val givenNames: String
    /** Merged (D8): "ARDILA CASTRO", "DE LA OSSA TOVAR". */
    val surnames: String
    val birthDate: LocalDate?
    val sex: Sex

    /** Cédula amarilla or digital — see [documentType] for which. */
    data class ColombianId(
        override val documentType: DocumentType,
        override val givenNames: String,
        override val surnames: String,
        override val birthDate: LocalDate?,
        override val sex: Sex,
        /** NUIP, normalized (no leading zeros). */
        val nuip: String,
        /** e.g. "O+"; cédula amarilla (PDF417) only. */
        val bloodType: String?,
        /** Cédula digital (MRZ) only. */
        val expirationDate: LocalDate?
    ) : ScannedDocument {
        init {
            require(documentType != DocumentType.PASSPORT) {
                "ColombianId cannot carry documentType PASSPORT"
            }
        }
    }

    /**
     * Machine-readable passport (MRZ TD3, ICAO 9303 Part 4) — any
     * issuing state. Reading a passport is NOT identity verification:
     * this is what is printed, with no authenticity check (§7).
     */
    data class Passport(
        override val givenNames: String,
        override val surnames: String,
        override val birthDate: LocalDate?,
        override val sex: Sex,
        /** Alphanumeric, verbatim (e.g. "AB1234567"). */
        val passportNumber: String,
        /** ICAO code — may be non-ISO ("D", "XXA", …). */
        val issuingState: String,
        /** ICAO code — may be non-ISO. */
        val nationality: String,
        /** Mandatory in TD3. */
        val expirationDate: LocalDate,
        /** Optional-data field; most states leave it empty. */
        val personalNumber: String?,
        /**
         * True when the MRZ name field is full to its last position —
         * per ICAO the name MAY be incomplete (39-char limit). Clients
         * pre-filling forms must not silently trust a truncated name.
         */
        val namesTruncated: Boolean
    ) : ScannedDocument {
        override val documentType: DocumentType get() = DocumentType.PASSPORT
    }
}
