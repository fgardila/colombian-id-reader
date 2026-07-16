package dev.code93.colombian_id_reader.scan

import dev.code93.colombian_id_reader.model.DocumentFormat
import dev.code93.colombian_id_reader.model.GateHint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Per-frame evidence produced by the platform detectors, in PIXEL
 * coordinates of the analyzed frame (a normalized box would distort
 * aspect ratios). Android supplies [bbox] (+ [trackingId]); iOS supplies
 * [quad]. [sharpness] is a variance-of-Laplacian score computed the same
 * way on both platforms.
 */
data class GateObservation(
    val timestampMs: Long,
    val frameWidth: Float,
    val frameHeight: Float,
    val bbox: Box? = null,
    val quad: Quad? = null,
    val sharpness: Float? = null,
    val trackingId: Int? = null
) {
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    data class Quad(val tl: Point, val tr: Point, val br: Point, val bl: Point) {
        data class Point(val x: Float, val y: Float)

        fun boundingBox(): Box {
            val xs = listOf(tl.x, tr.x, br.x, bl.x)
            val ys = listOf(tl.y, tr.y, br.y, bl.y)
            return Box(xs.min(), ys.min(), xs.max(), ys.max())
        }
    }
}

data class GateVerdict(val pass: Boolean, val hint: GateHint)

/**
 * Single tuning point for the gate (ARCHITECTURE-0.2.0.md §9 — values
 * are provisional until the field-evaluation phase).
 */
data class GateThresholds(
    /** Candidate must cover at least this fraction of the frame. */
    val minAreaFraction: Float = 0.20f,
    /** Opposite-side length ratio below this reads as skewed (quad only). */
    val maxSkewSideRatio: Float = 0.85f,
    /** Variance-of-Laplacian below this reads as out of focus. */
    val minSharpness: Float = 60f,
    /** Consecutive qualifying frames before the gate opens. */
    val stableFramesRequired: Int = 3,
    /** Max per-frame center drift, as a fraction of the frame diagonal. */
    val maxCenterDrift: Float = 0.03f,
    /** Displayed hint changes at most this often (except entering PASS). */
    val hintMinHoldMs: Long = 500,
    /**
     * False-negative safety valve: if a candidate has been continuously
     * present this long without the gate ever opening, the gate starts
     * passing anyway — a miscalibrated gate degrades to 0.1.x behavior
     * (slower, but it reads), never to "the app won't read".
     */
    val graceAfterMs: Long = 4_000
)

/**
 * Stage 1 of the capture pipeline (ARCHITECTURE-0.2.0.md §4.0): decides
 * WHEN to read, never WHAT the document is. Platform detectors produce
 * [GateObservation]s; this shared core decides pass/fail plus the UX
 * hint, with hysteresis so hints don't flicker.
 *
 * Stateful — one instance per scan session, called from the single
 * capture thread.
 */
internal class CaptureGate(
    private val accepts: Set<DocumentFormat>,
    private val thresholds: GateThresholds = GateThresholds(),
    private val stats: GateStats? = null
) {

    private var stableStreak = 0
    private var lastCenter: Pair<Float, Float>? = null
    private var lastTrackingId: Int? = null
    private var candidateSinceMs: Long? = null
    private var everPassed = false

    private var displayedHint: GateHint? = null
    private var displayedHintSinceMs = 0L

    fun evaluate(obs: GateObservation): GateVerdict {
        val rawVerdict = decide(obs)
        val hint = applyHintHold(rawVerdict.hint, obs.timestampMs)
        val verdict = GateVerdict(rawVerdict.pass, hint)
        stats?.onEvaluated(verdict, grace = rawVerdict.pass && !naturalPass, obs.timestampMs)
        return verdict
    }

    /** True when the last decide() pass came from the streak, not grace. */
    private var naturalPass = false

    private fun decide(obs: GateObservation): GateVerdict {
        val box = obs.bbox ?: obs.quad?.boundingBox()
        naturalPass = false

        if (box == null || box.width <= 0f || box.height <= 0f) {
            resetCandidate()
            return GateVerdict(pass = false, hint = GateHint.NO_DOCUMENT)
        }
        if (candidateSinceMs == null) candidateSinceMs = obs.timestampMs
        val grace = !everPassed &&
            obs.timestampMs - (candidateSinceMs ?: obs.timestampMs) >= thresholds.graceAfterMs

        val hint = classify(obs, box)
        if (hint == GateHint.PASS) {
            everPassed = true
            naturalPass = true
            return GateVerdict(pass = true, hint = GateHint.PASS)
        }
        // Grace mode: keep guiding the user, but let recognition try.
        return GateVerdict(pass = grace, hint = hint)
    }

    private fun classify(obs: GateObservation, box: GateObservation.Box): GateHint {
        val frameArea = obs.frameWidth * obs.frameHeight
        val areaFraction = if (frameArea > 0f) (box.width * box.height) / frameArea else 0f
        if (areaFraction < thresholds.minAreaFraction) {
            stableStreak = 0
            return GateHint.TOO_SMALL
        }

        val aspect = max(box.width, box.height) / min(box.width, box.height)
        val skewed = obs.quad?.let { quadSkewRatio(it) < thresholds.maxSkewSideRatio }
            // Android approximation: enough area but the axis-aligned box
            // collapsed below the accepted band reads as a yawed card.
            ?: accepts.all { aspect < it.aspectRatioRange.start }
        if (skewed) {
            stableStreak = 0
            return GateHint.SKEWED
        }
        if (accepts.none { aspect in it.aspectRatioRange }) {
            stableStreak = 0
            return GateHint.NO_DOCUMENT
        }

        val sharpEnough = obs.sharpness?.let { it >= thresholds.minSharpness } ?: true
        val stable = isStable(obs, box)
        if (!sharpEnough || !stable) return GateHint.HOLD_STEADY

        stableStreak++
        return if (stableStreak >= thresholds.stableFramesRequired) {
            GateHint.PASS
        } else {
            GateHint.HOLD_STEADY
        }
    }

    private fun isStable(obs: GateObservation, box: GateObservation.Box): Boolean {
        val center = box.centerX to box.centerY
        val previous = lastCenter
        lastCenter = center

        val sameTrack = obs.trackingId == null || lastTrackingId == null ||
            obs.trackingId == lastTrackingId
        lastTrackingId = obs.trackingId

        if (previous == null || !sameTrack) {
            stableStreak = 0
            return false
        }
        val diagonal = sqrt(
            obs.frameWidth * obs.frameWidth + obs.frameHeight * obs.frameHeight
        )
        val drift = sqrt(
            (center.first - previous.first).let { it * it } +
                (center.second - previous.second).let { it * it }
        ) / diagonal
        if (drift > thresholds.maxCenterDrift) {
            stableStreak = 0
            return false
        }
        return true
    }

    /** Opposite-side length ratio: 1.0 = perfectly parallel, lower = skewed. */
    private fun quadSkewRatio(quad: GateObservation.Quad): Float {
        fun length(a: GateObservation.Quad.Point, b: GateObservation.Quad.Point): Float =
            sqrt((a.x - b.x).let { it * it } + (a.y - b.y).let { it * it })

        val top = length(quad.tl, quad.tr)
        val bottom = length(quad.bl, quad.br)
        val left = length(quad.tl, quad.bl)
        val right = length(quad.tr, quad.br)
        val horizontal = min(top, bottom) / max(top, bottom)
        val vertical = min(left, right) / max(left, right)
        return min(horizontal, vertical)
    }

    private fun applyHintHold(hint: GateHint, nowMs: Long): GateHint {
        val current = displayedHint
        if (current == null || hint == current) {
            if (current == null) {
                displayedHint = hint
                displayedHintSinceMs = nowMs
            }
            return hint
        }
        // Entering PASS is never delayed — success feedback is immediate.
        if (hint != GateHint.PASS && nowMs - displayedHintSinceMs < thresholds.hintMinHoldMs) {
            return current
        }
        displayedHint = hint
        displayedHintSinceMs = nowMs
        stats?.onHintTransition()
        return hint
    }

    private fun resetCandidate() {
        stableStreak = 0
        lastCenter = null
        lastTrackingId = null
        candidateSinceMs = null
    }
}
