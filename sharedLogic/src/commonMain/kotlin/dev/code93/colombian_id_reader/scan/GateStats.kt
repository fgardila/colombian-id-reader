package dev.code93.colombian_id_reader.scan

import dev.code93.colombian_id_reader.model.GateHint

/**
 * Measurement tooling for the field-evaluation phase
 * (ARCHITECTURE-0.2.0.md §7): counts what the gate did so false-negative
 * and false-positive rates can be observed on device. Geometry counters
 * only — no document data. One instance per scan session, single
 * capture thread.
 */
public class GateStats {

    private var framesEvaluated = 0
    private var framesPassed = 0
    private var gracePasses = 0
    private var hintTransitions = 0
    private var ocrAllowed = 0
    private var ocrSuppressed = 0
    private val hintCounts = IntArray(GateHint.entries.size)
    private var firstTimestampMs: Long? = null
    private var firstPassAtMs: Long? = null

    internal fun onEvaluated(verdict: GateVerdict, grace: Boolean, timestampMs: Long) {
        framesEvaluated++
        if (firstTimestampMs == null) firstTimestampMs = timestampMs
        if (verdict.pass) {
            framesPassed++
            if (grace) gracePasses++
            if (firstPassAtMs == null) firstPassAtMs = timestampMs
        }
        hintCounts[verdict.hint.ordinal]++
    }

    internal fun onHintTransition() {
        hintTransitions++
    }

    internal fun onOcrDecision(allowed: Boolean) {
        if (allowed) ocrAllowed++ else ocrSuppressed++
    }

    public fun summary(): String {
        val timeToFirstPass = firstPassAtMs?.let { pass ->
            firstTimestampMs?.let { first -> "${pass - first}ms" }
        } ?: "never"
        val hints = GateHint.entries.joinToString(" ") { hint ->
            "${hint.name.take(2)}:${hintCounts[hint.ordinal]}"
        }
        return "gate: $framesEvaluated frames, $framesPassed passed " +
            "(grace $gracePasses), first pass $timeToFirstPass, " +
            "ocr ${ocrAllowed} ran / ${ocrSuppressed} gated, " +
            "$hintTransitions hint changes | $hints"
    }
}
