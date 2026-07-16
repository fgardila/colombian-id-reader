package dev.code93.colombian_id_reader.model

/**
 * The resolved document type, surfaced explicitly to clients
 * (ARCHITECTURE-0.2.0.md §4.4). Always resolved from extracted
 * evidence — a PDF417 that decodes, an MRZ that validates — never from
 * an image classifier (D10).
 */
enum class DocumentType { CEDULA_AMARILLA, CEDULA_DIGITAL, PASSPORT }
