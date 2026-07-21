@file:OptIn(ExperimentalForeignApi::class)

package dev.code93.colombian_id_reader.ui

import dev.code93.colombian_id_reader.model.CapturePhase
import dev.code93.colombian_id_reader.model.DetectorFilter
import dev.code93.colombian_id_reader.model.GateHint
import dev.code93.colombian_id_reader.model.ScanCapture
import dev.code93.colombian_id_reader.model.ScanMode
import dev.code93.colombian_id_reader.scan.CaptureFlowController
import dev.code93.colombian_id_reader.scan.ScanDebug
import dev.code93.colombian_id_reader.scanner.IdCaptureSession
import dev.code93.colombian_id_reader.scanner.IdFrameProcessor
import dev.code93.colombian_id_reader.scanner.VisionDetectors
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.hasTorch
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIFont
import platform.UIKit.UIImage
import platform.UIKit.UILabel
import platform.UIKit.UIViewController
import platform.UIKit.accessibilityLabel
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Scanning screen for both cédula generations (ARCHITECTURE.md §5),
 * Swift-friendly: wrap in a UIViewControllerRepresentable for SwiftUI.
 *
 * Handles the camera permission flow itself (once denied, iOS never
 * re-prompts, so the grant button deep-links to Settings). Delivers
 * exactly one [ScanCapture] via [onResult] on the main thread. The
 * library never persists, transmits or logs what the camera sees (§7).
 *
 * UI strings and behavior come from [IdScannerOptions] (a static
 * framework carries no resource bundle, so texts are configuration).
 *
 * Internal because Kotlin subclasses of Objective-C classes are not
 * exported to the framework header — Swift reaches this through the
 * [IdScanner] factory, typed as plain UIViewController.
 */
internal class IdScannerViewController(
    private val options: IdScannerOptions,
    private val onResult: (ScanCapture) -> Unit,
    private val onCancel: () -> Unit
) : UIViewController(nibName = null, bundle = null) {

    private val texts: IdScannerTexts get() = options.texts

    private var captureSession: IdCaptureSession? = null
    private var detectors: VisionDetectors? = null
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var overlay: ScannerOverlayView? = null

    private val cancelButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val flashButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private var torchOn = false
    private val rationaleLabel = UILabel()
    private val grantButton = UIButton.buttonWithType(UIButtonTypeSystem)

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor

        cancelButton.setTitle(texts.cancel, forState = UIControlStateNormal)
        cancelButton.setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
        cancelButton.addTarget(
            this, NSSelectorFromString("cancelTapped"), UIControlEventTouchUpInside
        )
        view.addSubview(cancelButton)

        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> showScanner()
            AVAuthorizationStatusNotDetermined ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    // Completion arrives on an arbitrary queue.
                    dispatch_async(dispatch_get_main_queue()) {
                        if (granted) showScanner() else showDenied()
                    }
                }
            else -> showDenied()
        }
    }

    override fun viewWillAppear(animated: Boolean) {
        super.viewWillAppear(animated)
        captureSession?.start()
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)
        captureSession?.stop()
        // Stopping the session turns the lamp off; keep the UI honest.
        torchOn = false
        updateFlashButton()
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        val width = view.bounds.useContents { size.width }
        val height = view.bounds.useContents { size.height }
        val safeTop = view.safeAreaInsets.useContents { top }

        previewLayer?.frame = view.bounds
        overlay?.setFrame(view.bounds)

        cancelButton.sizeToFit()
        val cancelHeight = cancelButton.bounds.useContents { size.height }
        val cancelWidth = cancelButton.bounds.useContents { size.width }
        cancelButton.setFrame(CGRectMake(16.0, safeTop + 8.0, cancelWidth, cancelHeight))

        flashButton.sizeToFit()
        val flashWidth = flashButton.bounds.useContents { size.width }
        val flashHeight = flashButton.bounds.useContents { size.height }
        flashButton.setFrame(
            CGRectMake(width - 16.0 - flashWidth, safeTop + 8.0, flashWidth, flashHeight)
        )

        rationaleLabel.setFrame(CGRectMake(32.0, height / 2.0 - 120.0, width - 64.0, 160.0))
        grantButton.sizeToFit()
        val grantWidth = grantButton.bounds.useContents { size.width }
        val grantHeight = grantButton.bounds.useContents { size.height }
        grantButton.setFrame(
            CGRectMake((width - grantWidth) / 2.0, height / 2.0 + 56.0, grantWidth, grantHeight)
        )
    }

    private fun showScanner() {
        val visionDetectors = VisionDetectors()
        val processor = IdFrameProcessor(
            mode = options.mode,
            filter = options.detectorFilter,
            detectors = visionDetectors,
            captureImages = options.captureImages,
            onSuccess = { capture ->
                // Already on the main queue (the processor dispatches).
                captureSession?.stop()
                onResult(capture)
            },
            onHint = { hint ->
                // Already on the main queue.
                overlay?.setInstruction(texts.forHint(hint))
                overlay?.setHighlighted(hint == GateHint.PASS)
                options.onGateHint?.invoke(hint)
            },
            onPhase = { phase ->
                // Already on the main queue. Front captured: flip
                // guidance; the next gate hint takes over from here.
                if (phase == CaptureFlowController.Phase.BACK) {
                    overlay?.setInstruction(texts.instructionFlip)
                    overlay?.setHighlighted(true)
                    options.onCapturePhase?.invoke(CapturePhase.BACK)
                }
            }
        )
        val session = IdCaptureSession(
            processor,
            enablePdf417Metadata = options.mode == ScanMode.ColombianId &&
                options.detectorFilter != DetectorFilter.MRZ_ONLY
        )

        val layer = AVCaptureVideoPreviewLayer(session = session.session)
        layer.videoGravity = AVLayerVideoGravityResizeAspectFill
        view.layer.insertSublayer(layer, atIndex = 0u)

        val initialInstruction = when {
            options.mode == ScanMode.Passport -> texts.instructionPassport
            options.captureImages -> texts.instructionFront
            else -> texts.instruction
        }
        val overlayView = ScannerOverlayView(initialInstruction)
        view.insertSubview(overlayView, belowSubview = cancelButton)

        detectors = visionDetectors
        captureSession = session
        previewLayer = layer
        overlay = overlayView

        // Torch toggle for dark environments — only when the device
        // actually has one (simulator and some iPads do not).
        val hasTorch = AVCaptureDevice
            .defaultDeviceWithMediaType(AVMediaTypeVideo)?.hasTorch == true
        if (hasTorch) {
            flashButton.tintColor = UIColor.whiteColor
            updateFlashButton()
            flashButton.addTarget(
                this, NSSelectorFromString("flashTapped"), UIControlEventTouchUpInside
            )
            view.addSubview(flashButton)
        }

        view.setNeedsLayout()
        session.start()
    }

    private fun updateFlashButton() {
        val symbol = if (torchOn) "bolt.slash.fill" else "bolt.fill"
        flashButton.setImage(UIImage.systemImageNamed(symbol), forState = UIControlStateNormal)
        flashButton.accessibilityLabel = if (torchOn) texts.flashOff else texts.flashOn
    }

    @ObjCAction
    internal fun flashTapped() {
        torchOn = !torchOn
        ScanDebug.log { "torch: tapped -> $torchOn" }
        captureSession?.setTorch(torchOn)
        updateFlashButton()
        view.setNeedsLayout()
    }

    private fun showDenied() {
        rationaleLabel.text = texts.permissionRationale
        rationaleLabel.textColor = UIColor.whiteColor
        rationaleLabel.textAlignment = NSTextAlignmentCenter
        rationaleLabel.numberOfLines = 0
        rationaleLabel.font = UIFont.systemFontOfSize(16.0)
        view.addSubview(rationaleLabel)

        grantButton.setTitle(texts.grantPermission, forState = UIControlStateNormal)
        grantButton.addTarget(
            this, NSSelectorFromString("grantTapped"), UIControlEventTouchUpInside
        )
        view.addSubview(grantButton)

        view.setNeedsLayout()
    }

    @ObjCAction
    internal fun cancelTapped() {
        captureSession?.stop()
        onCancel()
    }

    @ObjCAction
    internal fun grantTapped() {
        // Once denied, iOS never re-prompts: Settings is the only path.
        // (Granting camera access there relaunches the app.)
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(
            url, options = emptyMap<Any?, Any?>(), completionHandler = null
        )
    }
}
