import SwiftUI
import SharedLogic

struct ScannerView: UIViewControllerRepresentable {
    let mode: ScanMode
    let detectorFilter: DetectorFilter
    let onResult: (ScannedDocument) -> Void
    let onCancel: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let options = IdScannerOptions()
        options.mode = mode
        options.detectorFilter = detectorFilter
        return IdScanner.shared.viewController(
            options: options,
            onResult: onResult,
            onCancel: onCancel
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
