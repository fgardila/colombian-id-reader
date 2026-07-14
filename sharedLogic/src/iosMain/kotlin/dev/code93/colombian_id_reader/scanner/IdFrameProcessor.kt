@file:OptIn(ExperimentalForeignApi::class)

package dev.code93.colombian_id_reader.scanner

import dev.code93.colombian_id_reader.model.IdCardData
import dev.code93.colombian_id_reader.model.ScanMode
import dev.code93.colombian_id_reader.model.ScanResult
import dev.code93.colombian_id_reader.scan.ScanFrameRouter
import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import dev.code93.colombian_id_reader.ColombianIdParser
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVImageBufferRef
import platform.Foundation.NSProcessInfo
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Capture delegate that feeds each frame through [ScanFrameRouter]
 * (iOS twin of IdFrameAnalyzer).
 *
 * Frames are processed synchronously on the serial capture queue: with
 * alwaysDiscardsLateVideoFrames, blocking the queue IS the throttling —
 * AVFoundation drops intermediate frames and delivers the freshest one
 * when we return. Synchronous processing is also the CMSampleBuffer
 * lifetime guarantee: the buffer is only valid for the duration of the
 * callback, so nothing here may outlive it (if processing ever becomes
 * async, the buffer must be CFRetain'd).
 *
 * The heavy text recognizer gets an extra time gate so AUTO keeps the
 * cheap barcode detector hot without cooking the device.
 */
internal class IdFrameProcessor(
    private val mode: ScanMode,
    private val detectors: VisionDetectors,
    private val onSuccess: (IdCardData) -> Unit,
    private val mrzThrottleMs: Long = 250
) : NSObject(),
    AVCaptureVideoDataOutputSampleBufferDelegateProtocol,
    AVCaptureMetadataOutputObjectsDelegateProtocol {

    private val delivered = AtomicInt(0)
    private var lastMrzAttemptAt = 0L // capture queue only

    private val router = ScanFrameRouter<CVImageBufferRef>(
        mode = mode,
        pdf417 = { detectors.pdf417(it) },
        mrzOcr = { buffer ->
            val now = uptimeMs()
            if (now - lastMrzAttemptAt < mrzThrottleMs) {
                emptyList()
            } else {
                lastMrzAttemptAt = now
                detectors.mrzLines(buffer)
            }
        }
    )

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection
    ) {
        if (delivered.value != 0) return
        val pixelBuffer = didOutputSampleBuffer?.let { CMSampleBufferGetImageBuffer(it) } ?: return
        val result = runBlocking { router.process(pixelBuffer) }
        if (result is ScanResult.Success && delivered.compareAndSet(0, 1)) {
            dispatch_async(dispatch_get_main_queue()) { onSuccess(result.data) }
        }
        // Error / null: expected on partial frames — keep scanning.
    }

    /**
     * PDF417 via AVCaptureMetadataOutput — the primary barcode path on
     * iOS: Vision's PDF417 decoder proved unable to lock onto the
     * cédula's dense barcode on real cards, while AVFoundation's
     * metadata detector (the boarding-pass reader) handles it.
     */
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        if (delivered.value != 0 || mode == ScanMode.MRZ_ONLY) return
        val raw = didOutputMetadataObjects
            .filterIsInstance<AVMetadataMachineReadableCodeObject>()
            .firstNotNullOfOrNull { it.stringValue }
            ?: return
        val result = ColombianIdParser.parsePdf417(raw)
        if (result is ScanResult.Success && delivered.compareAndSet(0, 1)) {
            dispatch_async(dispatch_get_main_queue()) { onSuccess(result.data) }
        }
    }

    private fun uptimeMs(): Long =
        (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
}
