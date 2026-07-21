package dev.code93.colombian_id_reader

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.code93.colombian_id_reader.model.CapturePhase
import dev.code93.colombian_id_reader.model.DetectorFilter
import dev.code93.colombian_id_reader.model.GateHint
import dev.code93.colombian_id_reader.model.NameMatch
import dev.code93.colombian_id_reader.model.ScanCapture
import dev.code93.colombian_id_reader.model.ScanMode
import dev.code93.colombian_id_reader.model.ScannedDocument
import dev.code93.colombian_id_reader.scan.ScanDebug
import dev.code93.colombian_id_reader.ui.IdScannerScreen

/** Which cédula generation the demo user says they hold — drives the
 *  ghost wireframe only; the scanner itself decides by evidence. */
private enum class DemoGeneration { AMARILLA, DIGITAL }

private sealed interface DemoScreen {
    data object Home : DemoScreen
    data class Scanning(
        val mode: ScanMode,
        val filter: DetectorFilter,
        val captureImages: Boolean,
        val generation: DemoGeneration?
    ) : DemoScreen
    data class Result(val capture: ScanCapture) : DemoScreen
}

@Composable
fun DemoApp() {
    MaterialTheme {
        var screen by remember { mutableStateOf<DemoScreen>(DemoScreen.Home) }

        when (val current = screen) {
            is DemoScreen.Home -> HomeScreen(
                onScan = { mode, filter, captureImages, generation ->
                    screen = DemoScreen.Scanning(mode, filter, captureImages, generation)
                }
            )
            is DemoScreen.Scanning -> ScanningScreen(
                screen = current,
                onResult = { screen = DemoScreen.Result(it) },
                onCancel = { screen = DemoScreen.Home }
            )
            is DemoScreen.Result -> ResultScreen(
                capture = current.capture,
                onScanAgain = { screen = DemoScreen.Home }
            )
        }
    }
}

@Composable
private fun HomeScreen(onScan: (ScanMode, DetectorFilter, Boolean, DemoGeneration?) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("colombian-id-reader", style = MaterialTheme.typography.headlineSmall)
        Text("Demo", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))

        var captureImages by remember { mutableStateOf(false) }
        var generation by remember { mutableStateOf(DemoGeneration.DIGITAL) }

        Text("¿Cuál cédula vas a escanear?", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = generation == DemoGeneration.AMARILLA,
                onClick = { generation = DemoGeneration.AMARILLA },
                label = { Text("Amarilla") }
            )
            FilterChip(
                selected = generation == DemoGeneration.DIGITAL,
                onClick = { generation = DemoGeneration.DIGITAL },
                label = { Text("Digital") }
            )
        }
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { onScan(ScanMode.ColombianId, DetectorFilter.ALL, captureImages, generation) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Escanear cédula")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            // Pasaporte: solo página de datos, sin captura de imágenes (1.0.0 §3).
            onClick = { onScan(ScanMode.Passport, DetectorFilter.ALL, false, null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Escanear pasaporte")
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onScan(ScanMode.ColombianId, DetectorFilter.PDF417_ONLY, captureImages, generation) }) { Text("Solo PDF417") }
            OutlinedButton(onClick = { onScan(ScanMode.ColombianId, DetectorFilter.MRZ_ONLY, captureImages, generation) }) { Text("Solo MRZ") }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = captureImages, onCheckedChange = { captureImages = it })
            Text("Capturar imágenes (frente y reverso)", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(16.dp))
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

/**
 * Wraps the library's scanner with the demo's ghost guidance: a
 * wireframe of the side to present, drawn inside the same card window
 * the library overlay cuts (85% width, ID-1 aspect, centered). It
 * fades out as soon as the gate sees a document, and swaps front→back
 * when the flow flips — both signals come from the library's public
 * callbacks (onGateHint / onCapturePhase).
 */
@Composable
private fun ScanningScreen(
    screen: DemoScreen.Scanning,
    onResult: (ScanCapture) -> Unit,
    onCancel: () -> Unit
) {
    val twoSided = screen.captureImages && screen.mode == ScanMode.ColombianId
    var phase by remember {
        mutableStateOf(if (twoSided) CapturePhase.FRONT else CapturePhase.BACK)
    }
    var hint by remember { mutableStateOf<GateHint?>(null) }

    Box(Modifier.fillMaxSize()) {
        IdScannerScreen(
            mode = screen.mode,
            detectorFilter = screen.filter,
            captureImages = screen.captureImages,
            onGateHint = { hint = it },
            onCapturePhase = { newPhase ->
                phase = newPhase
                hint = null // fresh side, show the ghost again
            },
            onResult = onResult,
            onCancel = onCancel
        )

        val ghost = screen.generation?.let { generation ->
            when (generation to phase) {
                DemoGeneration.AMARILLA to CapturePhase.FRONT -> R.drawable.cedula_amarilla_front
                DemoGeneration.AMARILLA to CapturePhase.BACK -> R.drawable.cedula_amarilla_back
                DemoGeneration.DIGITAL to CapturePhase.FRONT -> R.drawable.cedula_digital_front
                else -> R.drawable.cedula_digital_back
            }
        }
        if (ghost != null) {
            // Strong while the user is aiming; nearly gone once the gate
            // has a candidate, so it never competes with the document.
            val alpha by animateFloatAsState(
                targetValue = when (hint) {
                    null, GateHint.NO_DOCUMENT -> 0.45f
                    GateHint.PASS -> 0f
                    else -> 0.12f
                },
                label = "ghostAlpha"
            )
            Image(
                painter = painterResource(ghost),
                contentDescription = null,
                alpha = alpha,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.85f)
                    .aspectRatio(85.6f / 54f)
            )
        }
    }
}

@Composable
private fun ResultScreen(capture: ScanCapture, onScanAgain: () -> Unit) {
    val data = capture.document
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Documento leído", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

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

        capture.images?.let { images ->
            Field(
                "Verificación de nombres (frente vs. reverso)",
                when (capture.nameMatch) {
                    NameMatch.MATCH -> "Coinciden"
                    NameMatch.MISMATCH -> "NO coinciden"
                    NameMatch.NOT_CHECKED -> "No verificado"
                }
            )
            images.front?.let { CapturedImage("Frente", it) }
            CapturedImage("Reverso", images.back)
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Escanear otra")
        }
    }

    // Ciclo de vida §7: al salir de la pantalla las imágenes se
    // desechan explícitamente — no deben quedar vivas en el heap.
    DisposableEffect(capture) {
        onDispose { capture.images?.dispose() }
    }
}

@Composable
private fun CapturedImage(label: String, jpeg: ByteArray) {
    val bitmap = remember(jpeg) {
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = label,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        } else {
            Text("(imagen no decodificable)", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
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
