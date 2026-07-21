import SwiftUI
import SharedLogic

struct ScannerView: UIViewControllerRepresentable {
    let mode: ScanMode
    let detectorFilter: DetectorFilter
    let captureImages: Bool
    var onGateHint: ((GateHint) -> Void)? = nil
    var onCapturePhase: ((CapturePhase) -> Void)? = nil
    let onResult: (ScanCapture) -> Void
    let onCancel: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let options = IdScannerOptions()
        options.mode = mode
        options.detectorFilter = detectorFilter
        options.captureImages = captureImages
        options.onGateHint = onGateHint
        options.onCapturePhase = onCapturePhase
        return IdScanner.shared.viewController(
            options: options,
            onResult: onResult,
            onCancel: onCancel
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
