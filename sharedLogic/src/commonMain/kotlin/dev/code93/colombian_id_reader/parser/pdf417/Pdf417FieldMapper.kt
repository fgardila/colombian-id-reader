package dev.code93.colombian_id_reader.parser.pdf417

import dev.code93.colombian_id_reader.model.DocumentSource
import dev.code93.colombian_id_reader.model.IdCardData
import dev.code93.colombian_id_reader.model.Sex
import dev.code93.colombian_id_reader.parser.DateParsing
import dev.code93.colombian_id_reader.parser.pdf417.Pdf417FieldLocator.LocatedFields

internal object Pdf417FieldMapper {

    fun map(fields: LocatedFields): IdCardData {
        val tokens = fields.nameTokens
        // 1 token: first name only; 2: second surname + first name;
        // 3+: second surname + first name + second name(s) — extra tokens
        // (a name split by a stripped Ñ/accent) are re-joined.
        val secondSurname = if (tokens.size >= 2) tokens[0] else null
        val firstName = if (tokens.size >= 2) tokens[1] else tokens[0]
        val secondName = tokens.drop(2).joinToString(" ").takeIf { it.isNotEmpty() }

        return IdCardData(
            documentNumber = fields.cedula.trimStart('0'),
            firstName = firstName,
            secondName = secondName,
            firstSurname = fields.firstSurname,
            secondSurname = secondSurname,
            // The barcode has no check digits; a corrupt date is not proof
            // of a bad read, so it degrades to null instead of failing.
            birthDate = DateParsing.parseYyyyMmDd(fields.birthDateRaw),
            sex = if (fields.sex == 'M') Sex.MALE else Sex.FEMALE,
            bloodType = fields.bloodType,
            expirationDate = null,
            source = DocumentSource.PDF417
        )
    }
}
