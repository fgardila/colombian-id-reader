package dev.code93.colombian_id_reader.ui

import dev.code93.colombian_id_reader.model.IdCardData
import platform.UIKit.UIViewController

/**
 * Swift-friendly entry point to the scanning screen (ARCHITECTURE.md
 * §5). Returns a plain UIViewController for the client to present or
 * wrap in a UIViewControllerRepresentable:
 *
 * ```swift
 * IdScanner.shared.viewController(
 *     onResult: { data in ... },
 *     onCancel: { ... }
 * )
 * ```
 *
 * The scanned category is ColombianId (the only ScanMode in 0.2.0, so
 * it is implied). A factory (rather than a public class) because Kotlin
 * subclasses of Objective-C classes cannot be exported to the framework
 * header.
 */
public object IdScanner {

    /** Scanner with default options (Spanish UI strings, all detectors). */
    public fun viewController(
        onResult: (IdCardData) -> Unit,
        onCancel: () -> Unit
    ): UIViewController = viewController(IdScannerOptions(), onResult, onCancel)

    /** Scanner with custom [IdScannerOptions]. */
    public fun viewController(
        options: IdScannerOptions,
        onResult: (IdCardData) -> Unit,
        onCancel: () -> Unit
    ): UIViewController = IdScannerViewController(options, onResult, onCancel)
}
