package dev.code93.colombian_id_reader.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.code93.colombian_id_reader.model.DetectorFilter
import dev.code93.colombian_id_reader.model.GateHint
import dev.code93.colombian_id_reader.model.ScannedDocument
import dev.code93.colombian_id_reader.model.ScanMode
import dev.code93.colombian_id_reader.scan.ScanDebug
import dev.code93.colombian_id_reader.scanner.IdFrameAnalyzer
import dev.code93.colombian_id_reader.scanner.MlKitDetectors
import dev.code93.colombian_id_reader.scanner.bindScanner
import dev.code93.colombian_id_reader.sharedLogic.R
import java.util.concurrent.Executors

/**
 * Scanning screen for both cédula generations (ARCHITECTURE.md §5,
 * ARCHITECTURE-0.2.0.md §4). Every frame passes the capture gate; its
 * framing hints drive the overlay text and, optionally, [onGateHint]
 * for clients with their own guidance UI.
 *
 * Requests the camera permission itself and delivers exactly one
 * [ScannedDocument] via [onResult]. The library never persists, transmits or
 * logs what the camera sees (§7). [detectorFilter] is a development
 * aid — leave it at [DetectorFilter.ALL] in production.
 */
@Composable
fun IdScannerScreen(
    mode: ScanMode = ScanMode.ColombianId,
    detectorFilter: DetectorFilter = DetectorFilter.ALL,
    onGateHint: ((GateHint) -> Unit)? = null,
    onResult: (ScannedDocument) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }
    BackHandler(onBack = onCancel)

    if (hasPermission) {
        ScannerContent(detectorFilter, onGateHint, onResult, onCancel)
    } else {
        PermissionRationale(
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            onCancel = onCancel
        )
    }
}

@Composable
private fun ScannerContent(
    detectorFilter: DetectorFilter,
    onGateHint: ((GateHint) -> Unit)?,
    onResult: (ScannedDocument) -> Unit,
    onCancel: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val detectors = remember { MlKitDetectors() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var hint by remember { mutableStateOf<GateHint?>(null) }

    val analyzer = remember(detectorFilter) {
        IdFrameAnalyzer(
            filter = detectorFilter,
            detectors = detectors,
            onSuccess = { data ->
                // Stop frames at the source before handing the result over.
                provider?.unbindAll()
                onResult(data)
            },
            onHint = { newHint ->
                hint = newHint
                onGateHint?.invoke(newHint)
            }
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PreviewView(context).also { previewView ->
                    bindScanner(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        previewView = previewView,
                        analyzer = analyzer,
                        analysisExecutor = executor
                    ) { provider = it }
                }
            }
        )
        ScannerOverlay(
            instruction = stringResource(hint.instructionRes()),
            cancelLabel = stringResource(R.string.colombian_id_scanner_cancel),
            highlight = hint == GateHint.PASS,
            onCancel = onCancel
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            ScanDebug.log { analyzer.stats.summary() }
            provider?.unbindAll()
            detectors.close()
            executor.shutdown()
        }
    }
}

private fun GateHint?.instructionRes(): Int = when (this) {
    null -> R.string.colombian_id_scanner_instruction
    GateHint.NO_DOCUMENT -> R.string.colombian_id_scanner_hint_no_document
    GateHint.TOO_SMALL -> R.string.colombian_id_scanner_hint_too_small
    GateHint.SKEWED -> R.string.colombian_id_scanner_hint_skewed
    GateHint.HOLD_STEADY -> R.string.colombian_id_scanner_hint_hold_steady
    GateHint.PASS -> R.string.colombian_id_scanner_hint_pass
}

@Composable
private fun PermissionRationale(
    onRequest: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.colombian_id_scanner_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.colombian_id_scanner_grant_permission))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.colombian_id_scanner_cancel))
        }
    }
}
