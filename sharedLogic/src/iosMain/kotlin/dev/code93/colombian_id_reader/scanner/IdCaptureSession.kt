@file:OptIn(ExperimentalForeignApi::class)

package dev.code93.colombian_id_reader.scanner

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1920x1080
import platform.AVFoundation.AVCaptureSessionPreset3840x2160
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataObjectTypePDF417Code
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create

/**
 * Owns the AVCaptureSession (iOS twin of CameraBinding).
 *
 * Analysis targets 4K: Android proved 1080p marginal for the cédula's
 * dense PDF417 (slow lock-on), and iOS has no 1440p preset — 4K with a
 * 1080p fallback is the available pair. The discard-late-frames model
 * absorbs the extra per-frame cost.
 *
 * The back camera can be absent (simulator): configuration then simply
 * adds no input and the preview stays black — never crash.
 */
internal class IdCaptureSession(
    private val delegate: IdFrameProcessor,
    private val enablePdf417Metadata: Boolean
) {

    val session = AVCaptureSession()

    private val sessionQueue =
        dispatch_queue_create("dev.code93.colombian_id_reader.capture", attr = null)

    private var configured = false

    /** Configure on first call, then start. Work hops to the session queue. */
    fun start() {
        dispatch_async(sessionQueue) {
            if (!configured) {
                configure()
                configured = true
            }
            if (!session.running) session.startRunning()
        }
    }

    /** Safe to call repeatedly. Never blocks the main thread. */
    fun stop() {
        dispatch_async(sessionQueue) {
            if (session.running) session.stopRunning()
        }
    }

    private fun configure() {
        session.beginConfiguration()

        session.sessionPreset = if (session.canSetSessionPreset(AVCaptureSessionPreset3840x2160)) {
            AVCaptureSessionPreset3840x2160
        } else {
            AVCaptureSessionPreset1920x1080
        }

        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) }
        if (input != null && session.canAddInput(input)) {
            session.addInput(input)
        }

        // videoSettings left at default: modern devices already deliver
        // 420 BiPlanar, which Vision consumes directly.
        val output = AVCaptureVideoDataOutput().apply {
            alwaysDiscardsLateVideoFrames = true
            setSampleBufferDelegate(delegate, queue = sessionQueue)
        }
        if (session.canAddOutput(output)) {
            session.addOutput(output)
        }

        // AVFoundation's metadata detector is the primary PDF417 path:
        // Vision cannot lock onto the cédula's dense barcode.
        if (enablePdf417Metadata) {
            val metadataOutput = AVCaptureMetadataOutput()
            if (session.canAddOutput(metadataOutput)) {
                session.addOutput(metadataOutput)
                metadataOutput.setMetadataObjectsDelegate(delegate, queue = sessionQueue)
                // Available types are only populated after addOutput.
                if (metadataOutput.availableMetadataObjectTypes
                        .contains(AVMetadataObjectTypePDF417Code)
                ) {
                    metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypePDF417Code)
                }
            }
        }

        session.commitConfiguration()
    }
}
