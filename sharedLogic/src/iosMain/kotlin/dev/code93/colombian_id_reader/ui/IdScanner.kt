package dev.code93.colombian_id_reader.ui

import dev.code93.colombian_id_reader.model.IdCardData
import dev.code93.colombian_id_reader.model.ScanMode
import platform.UIKit.UIViewController

/**
 * Swift-friendly entry point to the scanning screen (ARCHITECTURE.md
 * §5). Returns a plain UIViewController for the client to present or
 * wrap in a UIViewControllerRepresentable:
 *
 * ```swift
 * IdScanner.shared.viewController(
 *     mode: .auto,
 *     onResult: { data in ... },
 *     onCancel: { ... }
 * )
 * ```
 *
 * A factory (rather than a public class) because Kotlin subclasses of
 * Objective-C classes cannot be exported to the framework header.
 */
public object IdScanner {

    /** Scanner with the default Spanish UI strings. */
    public fun viewController(
        mode: ScanMode,
        onResult: (IdCardData) -> Unit,
        onCancel: () -> Unit
    ): UIViewController = IdScannerViewController(mode, onResult, onCancel)

    /** Scanner with client-localized UI strings. */
    public fun viewController(
        mode: ScanMode,
        onResult: (IdCardData) -> Unit,
        onCancel: () -> Unit,
        instructionText: String,
        cancelText: String,
        permissionRationaleText: String,
        grantPermissionText: String
    ): UIViewController = IdScannerViewController(
        mode = mode,
        onResult = onResult,
        onCancel = onCancel,
        instructionText = instructionText,
        cancelText = cancelText,
        permissionRationaleText = permissionRationaleText,
        grantPermissionText = grantPermissionText
    )
}
