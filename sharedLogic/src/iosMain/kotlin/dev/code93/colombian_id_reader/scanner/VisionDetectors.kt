@file:OptIn(ExperimentalForeignApi::class)

package dev.code93.colombian_id_reader.scanner

import dev.code93.colombian_id_reader.scan.GateObservation
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreVideo.CVImageBufferRef
import platform.Foundation.NSData
import platform.ImageIO.kCGImagePropertyOrientationRight
import platform.Vision.VNBarcodeObservation
import platform.Vision.VNBarcodeSymbologyPDF417
import platform.Vision.VNDetectBarcodesRequest
import platform.Vision.VNDetectDocumentSegmentationRequest
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRectangleObservation
import platform.Vision.VNRequest
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.posix.memcpy

/**
 * Vision detector requests (iOS twin of MlKitDetectors), created lazily
 * so each ScanMode only pays for what it uses. VNImageRequestHandler is
 * built per frame (it wraps the pixel buffer); the requests are reused.
 *
 * Orientation is fixed to .right: the scanning UI is portrait and the
 * sensor delivers landscape buffers.
 *
 * Privacy (§7): payloads and text exist only as return values in
 * memory — nothing is logged, stored, or transmitted. Vision failures
 * degrade to "nothing found on this frame".
 */
internal class VisionDetectors {

    private val barcodeRequest by lazy {
        VNDetectBarcodesRequest(completionHandler = null).apply {
            symbologies = listOf(VNBarcodeSymbologyPDF417)
        }
    }

    private val textRequest by lazy {
        VNRecognizeTextRequest(completionHandler = null).apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            // MRZ is OCR-B, not natural language: correction would "fix"
            // filler '<' runs and codes into dictionary words.
            usesLanguageCorrection = false
        }
    }

    private val documentRequest by lazy {
        // Built-in document detector (iOS 15+): quad + confidence, made
        // for exactly this — unlike VNDetectRectangles it doesn't fire on
        // every high-contrast rectangle in view.
        VNDetectDocumentSegmentationRequest(completionHandler = null)
    }

    /**
     * Quad of the most confident document on the frame, in PIXEL
     * coordinates of the oriented (portrait) frame, y-down — or null.
     * Vision reports normalized bottom-left-origin coordinates; both
     * conversions happen here.
     */
    fun documentQuad(
        buffer: CVImageBufferRef,
        frameWidth: Float,
        frameHeight: Float
    ): GateObservation.Quad? {
        if (!perform(buffer, documentRequest)) return null
        val observation = documentRequest.results
            ?.filterIsInstance<VNRectangleObservation>()
            ?.maxByOrNull { it.confidence }
            ?: return null

        fun point(x: Double, y: Double) = GateObservation.Quad.Point(
            (x * frameWidth).toFloat(),
            ((1.0 - y) * frameHeight).toFloat()
        )
        return GateObservation.Quad(
            tl = observation.topLeft.useContents { point(x, y) },
            tr = observation.topRight.useContents { point(x, y) },
            br = observation.bottomRight.useContents { point(x, y) },
            bl = observation.bottomLeft.useContents { point(x, y) }
        )
    }

    /** ISO-8859-1 payload of the first PDF417 on the frame, or null. */
    fun pdf417(buffer: CVImageBufferRef): String? {
        if (!perform(buffer, barcodeRequest)) return null
        return barcodeRequest.results
            ?.filterIsInstance<VNBarcodeObservation>()
            ?.firstNotNullOfOrNull { observation ->
                // The cédula's PDF417 carries binary sections, so the
                // text payload is lossy or nil — read the raw bytes.
                observation.payloadData?.toIso88591String()
                    ?: observation.payloadStringValue
            }
    }

    /** Recognized text lines in reading order (top to bottom), or empty. */
    fun mrzLines(buffer: CVImageBufferRef): List<String> {
        if (!perform(buffer, textRequest)) return emptyList()
        return textRequest.results
            ?.filterIsInstance<VNRecognizedTextObservation>()
            // Vision coordinates are bottom-left origin: the top of the
            // image is the LARGEST y, hence descending order.
            ?.sortedByDescending { it.boundingBox.useContents { origin.y } }
            ?.mapNotNull { observation ->
                (observation.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string
            }
            ?: emptyList()
    }

    private fun perform(buffer: CVImageBufferRef, request: VNRequest): Boolean {
        val handler = VNImageRequestHandler(
            cVPixelBuffer = buffer,
            orientation = kCGImagePropertyOrientationRight,
            options = emptyMap<Any?, Any?>()
        )
        return handler.performRequests(listOf(request), error = null)
    }
}

/** ISO-8859-1 is a 1:1 byte-to-codepoint mapping — decode by hand. */
private fun NSData.toIso88591String(): String {
    val size = length.toInt()
    if (size == 0) return ""
    val buffer = ByteArray(size)
    buffer.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return buildString(size) {
        for (byte in buffer) append(Char(byte.toInt() and 0xFF))
    }
}
