package dev.code93.colombian_id_reader.ui

import dev.code93.colombian_id_reader.model.ScanCapture
import platform.UIKit.UIViewController

/**
 * Swift-friendly entry point to the scanning screen (ARCHITECTURE.md
 * §5). Returns a plain UIViewController for the client to present or
 * wrap in a UIViewControllerRepresentable:
 *
 * ```swift
 * IdScanner.shared.viewController(
 *     onResult: { capture in ... },   // ScanCapture: document + images? + nameMatch
 *     onCancel: { ... }
 * )
 * ```
 *
 * A factory (rather than a public class) because Kotlin subclasses of
 * Objective-C classes cannot be exported to the framework header.
 */
public object IdScanner {

    /** Scanner with default options (Spanish UI strings, all detectors). */
    public fun viewController(
        onResult: (ScanCapture) -> Unit,
        onCancel: () -> Unit
    ): UIViewController = viewController(IdScannerOptions(), onResult, onCancel)

    /** Scanner with custom [IdScannerOptions]. */
    public fun viewController(
        options: IdScannerOptions,
        onResult: (ScanCapture) -> Unit,
        onCancel: () -> Unit
    ): UIViewController = IdScannerViewController(options, onResult, onCancel)
}
