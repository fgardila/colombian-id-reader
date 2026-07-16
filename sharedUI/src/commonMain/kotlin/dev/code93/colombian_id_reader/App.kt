package dev.code93.colombian_id_reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.code93.colombian_id_reader.model.ScanResult
import dev.code93.colombian_id_reader.model.ScannedDocument

/**
 * Parser demo harness (no camera): paste a raw PDF417 payload or the
 * three MRZ lines and inspect the resulting ScannedDocument. Useful on
 * desktop for exercising the shared parsing core with real strings.
 */
@Composable
fun App() {
    MaterialTheme {
        var input by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<ScanResult?>(null) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("colombian-id-reader — parser demo", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Pegue el PDF417 crudo, las 3 líneas MRZ (cédula) o las 2 líneas TD3 (pasaporte)") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { result = parse(input) }, enabled = input.isNotBlank()) {
                Text("Parsear")
            }
            result?.let { ResultView(it) }
        }
    }
}

private fun parse(input: String): ScanResult {
    val lines = input.lines().map { it.trim().replace(" ", "") }.filter { it.isNotEmpty() }
    return when {
        lines.size == 3 && lines.all { it.length == 30 } -> ColombianIdParser.parseMrz(lines)
        lines.size == 2 && lines.all { it.length == 44 } -> ColombianIdParser.parseMrzTd3(lines)
        else -> ColombianIdParser.parsePdf417(input)
    }
}

@Composable
private fun ResultView(result: ScanResult) {
    when (result) {
        is ScanResult.Error -> Text(
            "Error: ${result.reason}",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
        is ScanResult.Success -> Column {
            val data = result.data
            Field("Tipo de documento", data.documentType.name)
            Field("Nombres", data.givenNames)
            Field("Apellidos", data.surnames)
            Field("Fecha de nacimiento", data.birthDate?.toString())
            Field("Sexo", data.sex.name)
            when (data) {
                is ScannedDocument.ColombianId -> {
                    Field("NUIP", data.nuip)
                    Field("Tipo de sangre (RH)", data.bloodType)
                    Field("Vencimiento", data.expirationDate?.toString())
                }
                is ScannedDocument.Passport -> {
                    Field("Número de pasaporte", data.passportNumber)
                    Field("Estado emisor", data.issuingState)
                    Field("Nacionalidad", data.nationality)
                    Field("Vencimiento", data.expirationDate.toString())
                    Field("Número personal", data.personalNumber)
                    Field("Nombre posiblemente truncado", if (data.namesTruncated) "Sí" else "No")
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value ?: "—", style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
    Spacer(Modifier.height(2.dp))
}
