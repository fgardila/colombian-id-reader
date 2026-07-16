package dev.code93.colombian_id_reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.util.Log
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.code93.colombian_id_reader.model.DetectorFilter
import dev.code93.colombian_id_reader.model.IdCardData
import dev.code93.colombian_id_reader.scan.ScanDebug
import dev.code93.colombian_id_reader.ui.IdScannerScreen

private sealed interface DemoScreen {
    data object Home : DemoScreen
    data class Scanning(val filter: DetectorFilter) : DemoScreen
    data class Result(val data: IdCardData) : DemoScreen
}

@Composable
fun DemoApp() {
    MaterialTheme {
        var screen by remember { mutableStateOf<DemoScreen>(DemoScreen.Home) }

        when (val current = screen) {
            is DemoScreen.Home -> HomeScreen(onScan = { screen = DemoScreen.Scanning(it) })
            is DemoScreen.Scanning -> IdScannerScreen(
                detectorFilter = current.filter,
                onResult = { screen = DemoScreen.Result(it) },
                onCancel = { screen = DemoScreen.Home }
            )
            is DemoScreen.Result -> ResultScreen(
                data = current.data,
                onScanAgain = { screen = DemoScreen.Home }
            )
        }
    }
}

@Composable
private fun HomeScreen(onScan: (DetectorFilter) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("colombian-id-reader", style = MaterialTheme.typography.headlineSmall)
        Text("Demo", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { onScan(DetectorFilter.ALL) }, modifier = Modifier.fillMaxWidth()) {
            Text("Escanear cédula")
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onScan(DetectorFilter.PDF417_ONLY) }) { Text("Solo PDF417") }
            OutlinedButton(onClick = { onScan(DetectorFilter.MRZ_ONLY) }) { Text("Solo MRZ") }
        }

        Spacer(Modifier.height(24.dp))
        // Herramienta de desarrollo: vuelca el diagnóstico del pipeline de
        // escaneo (incluye datos del documento) a Logcat, tag ColombianIdScan.
        var diagnostics by remember { mutableStateOf(ScanDebug.listener != null) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = diagnostics,
                onCheckedChange = { enabled ->
                    diagnostics = enabled
                    ScanDebug.listener =
                        if (enabled) { message -> Log.d("ColombianIdScan", message) } else null
                }
            )
            Text("Diagnóstico en Logcat", style = MaterialTheme.typography.bodyMedium)
        }
        if (diagnostics) {
            Text(
                "adb logcat -s ColombianIdScan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun ResultScreen(data: IdCardData, onScanAgain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Documento leído", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Field("Número de documento", data.documentNumber)
        Field("Nombres", data.givenNames)
        Field("Apellidos", data.surnames)
        Field("Fecha de nacimiento", data.birthDate?.toString())
        Field("Sexo", data.sex.name)
        Field("Tipo de sangre (RH)", data.bloodType)
        Field("Vencimiento", data.expirationDate?.toString())
        Field("Fuente", data.source.name)

        Spacer(Modifier.height(24.dp))
        Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Escanear otra")
        }
    }
}

@Composable
private fun Field(label: String, value: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value ?: "—", style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}
