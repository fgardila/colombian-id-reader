package dev.code93.colombian_id_reader.ui

import dev.code93.colombian_id_reader.model.DetectorFilter
import dev.code93.colombian_id_reader.model.GateHint
import dev.code93.colombian_id_reader.model.ScanMode

/**
 * Configuration for the scanning screen. A mutable options object with
 * a no-arg constructor keeps the ObjC surface small (Kotlin default
 * arguments are not exported): configure in Swift as
 *
 * ```swift
 * let options = IdScannerOptions()
 * options.detectorFilter = .pdf417Only
 * options.onGateHint = { hint in ... }
 * ```
 *
 * The scanned category is [dev.code93.colombian_id_reader.model.ScanMode.ColombianId]
 * — the only mode in 0.2.0, so it is implied; a mode-taking factory
 * overload arrives with Passport support (0.3.0) without breaking this.
 */
public class IdScannerOptions {
    /** Declared document category; Swift: `options.mode = ScanModePassport.shared`. */
    public var mode: ScanMode = ScanMode.ColombianId

    /** Development aid — leave at ALL in production. */
    public var detectorFilter: DetectorFilter = DetectorFilter.ALL

    /**
     * Two-side flow with document images in the result
     * (ARCHITECTURE-1.0.0.md): front then back, JPEGs of the frames the
     * recognizers worked on, plus the front/back name cross-check. Off
     * by default (§7 — the images carry biometric data); ignored in
     * passport mode, which stays data-page only.
     */
    public var captureImages: Boolean = false

    /** Capture-gate framing hints, for clients with their own guidance UI. */
    public var onGateHint: ((GateHint) -> Unit)? = null

    /** UI strings; Spanish defaults (a static framework has no bundle). */
    public var texts: IdScannerTexts = IdScannerTexts()
}

/** UI strings for the bundled scanning screen, Spanish by default. */
public class IdScannerTexts {
    public var instruction: String = "Alinee el documento dentro del marco"
    public var instructionPassport: String = "Presente la página de datos del pasaporte"
    public var instructionFront: String = "Muestre el frente del documento"
    public var instructionFlip: String = "Ahora voltee el documento"
    public var cancel: String = "Cancelar"
    public var permissionRationale: String =
        "Para escanear el documento se necesita acceso a la cámara. La imagen se " +
            "procesa únicamente en este dispositivo: no se guarda ni se envía."
    public var grantPermission: String = "Conceder permiso"
    public var hintNoDocument: String = "Apunte al documento"
    public var hintTooSmall: String = "Acérquese al documento"
    public var hintSkewed: String = "Enderece el documento"
    public var hintHoldSteady: String = "Mantenga el dispositivo firme"
    public var hintPass: String = "Leyendo…"

    internal fun forHint(hint: GateHint): String = when (hint) {
        GateHint.NO_DOCUMENT -> hintNoDocument
        GateHint.TOO_SMALL -> hintTooSmall
        GateHint.SKEWED -> hintSkewed
        GateHint.HOLD_STEADY -> hintHoldSteady
        GateHint.PASS -> hintPass
    }
}
