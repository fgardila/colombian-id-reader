import SwiftUI
import SharedLogic

struct ScannerView: UIViewControllerRepresentable {
    let mode: ScanMode
    let onResult: (IdCardData) -> Void
    let onCancel: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        IdScanner.shared.viewController(mode: mode, onResult: onResult, onCancel: onCancel)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
