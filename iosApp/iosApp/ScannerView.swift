import SwiftUI
import SharedLogic

struct ScannerView: UIViewControllerRepresentable {
    let mode: ScanMode
    let detectorFilter: DetectorFilter
    let captureImages: Bool
    let onResult: (ScanCapture) -> Void
    let onCancel: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let options = IdScannerOptions()
        options.mode = mode
        options.detectorFilter = detectorFilter
        options.captureImages = captureImages
        return IdScanner.shared.viewController(
            options: options,
            onResult: onResult,
            onCancel: onCancel
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
