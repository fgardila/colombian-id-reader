package dev.code93.colombian_id_reader.scanner

import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.code93.colombian_id_reader.model.ScanMode
import kotlinx.coroutines.tasks.await

/**
 * ML Kit detector clients (bundled models), created lazily so each
 * [ScanMode] only pays for what it uses.
 *
 * Privacy (§7): recognized payloads and text exist only as return
 * values in memory — nothing is logged, stored, or transmitted.
 * Detector failures degrade to "nothing found on this frame".
 */
internal class MlKitDetectors : AutoCloseable {

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_PDF417)
                .build()
        )
    }

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private var barcodeUsed = false
    private var textUsed = false

    /** Raw payload of the first PDF417 on the frame, or null. */
    suspend fun pdf417(image: InputImage): String? = try {
        barcodeUsed = true
        barcodeScanner.process(image).await()
            .firstNotNullOfOrNull { it.rawValue }
    } catch (e: Exception) {
        null
    }

    /** Recognized text lines in reading order (top to bottom), or empty. */
    suspend fun mrzLines(image: InputImage): List<String> = try {
        textUsed = true
        textRecognizer.process(image).await()
            .textBlocks
            .flatMap { it.lines }
            .sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }
            .map { it.text }
    } catch (e: Exception) {
        emptyList()
    }

    override fun close() {
        if (barcodeUsed) barcodeScanner.close()
        if (textUsed) textRecognizer.close()
    }
}
