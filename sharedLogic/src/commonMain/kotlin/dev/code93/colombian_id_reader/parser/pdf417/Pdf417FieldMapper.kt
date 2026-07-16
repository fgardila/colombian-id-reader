package dev.code93.colombian_id_reader.parser.pdf417

import dev.code93.colombian_id_reader.model.DocumentType
import dev.code93.colombian_id_reader.model.ScannedDocument
import dev.code93.colombian_id_reader.model.Sex
import dev.code93.colombian_id_reader.parser.DateParsing
import dev.code93.colombian_id_reader.parser.pdf417.Pdf417FieldLocator.LocatedFields

internal object Pdf417FieldMapper {

    fun map(fields: LocatedFields): ScannedDocument.ColombianId {
        val nameFields = fields.nameFields
        // Layout between the anchors: [second surname?, given name(s)...].
        // 1 field: given names only; 2: second surname + given names;
        // 3+: second surname + separate given-name fields re-joined.
        val surnames = if (nameFields.size >= 2) {
            "${fields.firstSurname} ${nameFields[0]}"
        } else {
            fields.firstSurname
        }
        val givenNames = if (nameFields.size >= 2) {
            nameFields.drop(1).joinToString(" ")
        } else {
            nameFields[0]
        }

        return ScannedDocument.ColombianId(
            documentType = DocumentType.CEDULA_AMARILLA,
            givenNames = givenNames,
            surnames = surnames,
            // The barcode has no check digits; a corrupt date is not proof
            // of a bad read, so it degrades to null instead of failing.
            birthDate = DateParsing.parseYyyyMmDd(fields.birthDateRaw),
            sex = if (fields.sex == 'M') Sex.MALE else Sex.FEMALE,
            nuip = fields.cedula.trimStart('0'),
            bloodType = fields.bloodType,
            expirationDate = null
        )
    }
}
