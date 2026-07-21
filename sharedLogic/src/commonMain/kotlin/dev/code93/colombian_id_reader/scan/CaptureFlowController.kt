package dev.code93.colombian_id_reader.scan

import dev.code93.colombian_id_reader.model.DocumentImages
import dev.code93.colombian_id_reader.model.GateHint
import dev.code93.colombian_id_reader.model.NameMatch
import dev.code93.colombian_id_reader.model.ScanCapture
import dev.code93.colombian_id_reader.model.ScannedDocument

/**
 * Two-side capture state machine (ARCHITECTURE-1.0.0.md §5): FRONT →
 * BACK → DONE. Image capture is a *phase*, not a flag (D15) — the front
 * is a face the recognizers have never seen, with no conclusive
 * evidence available, so its capture is driven by the gate alone.
 *
 * When capture is disabled the controller starts directly in [Phase.BACK]
 * and [assemble] returns no images — the platform analyzers then behave
 * byte-identically to 0.3.0 (single phase, zero cost, §4.3).
 *
 * Stateful, one instance per scan session, called only from the single
 * capture thread (same contract as [CaptureGate]).
 */
internal class CaptureFlowController(private val captureEnabled: Boolean) {

    enum class Phase { FRONT, BACK, DONE }

    var phase: Phase = if (captureEnabled) Phase.FRONT else Phase.BACK
        private set

    private var gracePassStreak = 0
    private var frontJpeg: ByteArray? = null
    private var frontOcrLines: List<String> = emptyList()

    /**
     * FRONT phase, per frame: is this the frame to retain as the front?
     * True on a natural gate PASS (stable streak). Grace escape hatch:
     * the front has no recognizer to bail it out of a miscalibrated
     * gate, so [GRACE_PASS_FRAMES] consecutive grace passes (pass
     * without PASS hint, the gate's 4-second valve) capture anyway —
     * degrading to "slower", never to "stuck on the front step".
     */
    fun shouldCaptureFront(verdict: GateVerdict): Boolean {
        if (phase != Phase.FRONT) return false
        if (verdict.hint == GateHint.PASS) return true
        if (!verdict.pass) {
            gracePassStreak = 0
            return false
        }
        gracePassStreak++
        return gracePassStreak >= GRACE_PASS_FRAMES
    }

    /**
     * Store the front artifacts and advance to BACK — unconditionally:
     * both the JPEG and the OCR text are best-effort (§6.4), and a
     * failed front must never block reading the side that actually
     * carries the data. The caller must start a fresh [CaptureGate] for
     * the back phase (streak/tracking state belongs to one side).
     */
    fun onFrontCaptured(frontJpeg: ByteArray?, frontOcrLines: List<String>) {
        check(phase == Phase.FRONT) { "front already captured (phase=$phase)" }
        if (frontJpeg == null) {
            ScanDebug.log { "front JPEG encoding failed - continuing without front image" }
        }
        this.frontJpeg = frontJpeg
        this.frontOcrLines = frontOcrLines
        phase = Phase.BACK
    }

    /**
     * Back side recognized: assemble the final result. [backJpeg] is the
     * encoding of the frame the data came from (null = encode failure —
     * then no [DocumentImages] at all, since a back image is the one
     * mandatory piece per §4.4, but the name cross-check still runs).
     */
    fun assemble(document: ScannedDocument, backJpeg: ByteArray?): ScanCapture {
        phase = Phase.DONE
        if (!captureEnabled) {
            return ScanCapture(document, images = null, nameMatch = NameMatch.NOT_CHECKED)
        }
        if (backJpeg == null) {
            ScanDebug.log { "back JPEG encoding failed - delivering data without images" }
        }
        val images = backJpeg?.let { DocumentImages(front = frontJpeg, back = it) }
        val nameMatch = NameMatcher.check(
            surnames = document.surnames,
            givenNames = document.givenNames,
            frontLines = frontOcrLines
        ).match
        return ScanCapture(document, images, nameMatch)
    }

    private companion object {
        const val GRACE_PASS_FRAMES = 10
    }
}
