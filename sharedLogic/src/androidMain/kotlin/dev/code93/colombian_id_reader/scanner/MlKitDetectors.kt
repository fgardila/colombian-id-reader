package dev.code93.colombian_id_reader.scanner

import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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

    private val objectDetector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .build() // single most-prominent object, no classification
        )
    }

    private var barcodeUsed = false
    private var textUsed = false
    private var objectUsed = false

    /** Raw payload of the first PDF417 on the frame, or null. */
    suspend fun pdf417(image: InputImage): String? = try {
        barcodeUsed = true
        barcodeScanner.process(image).await()
            .firstNotNullOfOrNull { barcode ->
                // The cédula's PDF417 carries binary sections, so ML Kit's
                // rawValue (text-only) is usually null. Decode the raw
                // bytes as ISO-8859-1: a 1:1 byte-to-char mapping that
                // preserves the ASCII fields the parser locates.
                barcode.rawBytes?.toString(Charsets.ISO_8859_1) ?: barcode.rawValue
            }
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

    /** Most prominent object on the frame (capture-gate candidate), or null. */
    suspend fun detectObject(image: InputImage): DetectedObject? = try {
        objectUsed = true
        objectDetector.process(image).await().firstOrNull()
    } catch (e: Exception) {
        null
    }

    override fun close() {
        if (barcodeUsed) barcodeScanner.close()
        if (textUsed) textRecognizer.close()
        if (objectUsed) objectDetector.close()
    }
}
