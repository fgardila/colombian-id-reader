package dev.code93.colombian_id_reader.scan

import dev.code93.colombian_id_reader.model.DocumentFormat
import dev.code93.colombian_id_reader.model.GateHint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureGateTest {

    // Portrait frame 1000x2000. A well-framed ID-1 card:
    // 850x536 px (aspect 1.586), area fraction 0.228.
    private val cardBox = GateObservation.Box(75f, 700f, 925f, 1236f)

    private fun gate(stats: GateStats? = null) =
        CaptureGate(accepts = setOf(DocumentFormat.Id1), stats = stats)

    private fun obs(
        t: Long,
        box: GateObservation.Box? = cardBox,
        quad: GateObservation.Quad? = null,
        sharpness: Float? = 150f,
        trackingId: Int? = 7
    ) = GateObservation(
        timestampMs = t, frameWidth = 1000f, frameHeight = 2000f,
        bbox = box, quad = quad, sharpness = sharpness, trackingId = trackingId
    )

    @Test
    fun noCandidateIsNoDocument() {
        val verdict = gate().evaluate(obs(0, box = null))
        assertEquals(GateHint.NO_DOCUMENT, verdict.hint)
        assertFalse(verdict.pass)
    }

    @Test
    fun smallCandidateAsksToGetCloser() {
        val small = GateObservation.Box(300f, 900f, 700f, 1152f) // area 0.05
        val verdict = gate().evaluate(obs(0, box = small))
        assertEquals(GateHint.TOO_SMALL, verdict.hint)
        assertFalse(verdict.pass)
    }

    @Test
    fun collapsedAspectWithEnoughAreaReadsAsSkewed() {
        // Android approximation: aspect 1.2 < Id1 range start, area OK.
        val squashed = GateObservation.Box(150f, 700f, 850f, 1283f) // 700x583
        assertEquals(GateHint.SKEWED, gate().evaluate(obs(0, box = squashed)).hint)
    }

    @Test
    fun aspectAboveEveryFormatIsNoDocument() {
        // Aspect 2.5 — wider than any accepted format, not a skew collapse.
        val wide = GateObservation.Box(0f, 800f, 1000f, 1200f)
        assertEquals(GateHint.NO_DOCUMENT, gate().evaluate(obs(0, box = wide)).hint)
    }

    @Test
    fun quadSkewIsMeasuredFromOppositeSides() {
        // Trapezoid: top side 500, bottom side 850 → ratio 0.59 < 0.85.
        val skewedQuad = GateObservation.Quad(
            tl = GateObservation.Quad.Point(250f, 700f),
            tr = GateObservation.Quad.Point(750f, 700f),
            br = GateObservation.Quad.Point(925f, 1236f),
            bl = GateObservation.Quad.Point(75f, 1236f)
        )
        val verdict = gate().evaluate(obs(0, box = null, quad = skewedQuad))
        assertEquals(GateHint.SKEWED, verdict.hint)
    }

    @Test
    fun blurryFrameAsksToHoldSteady() {
        val verdict = gate().evaluate(obs(0, sharpness = 5f))
        assertEquals(GateHint.HOLD_STEADY, verdict.hint)
        assertFalse(verdict.pass)
    }

    @Test
    fun passRequiresAStreakOfStableFrames() {
        val g = gate()
        // Frame 1: no previous center — not yet stable.
        assertFalse(g.evaluate(obs(0)).pass)
        // Frames 2-3 build the streak (default requires 3).
        assertFalse(g.evaluate(obs(600)).pass)
        assertFalse(g.evaluate(obs(1200)).pass)
        val fourth = g.evaluate(obs(1800))
        assertTrue(fourth.pass)
        assertEquals(GateHint.PASS, fourth.hint)
    }

    @Test
    fun driftResetsTheStreak() {
        val g = gate()
        g.evaluate(obs(0))
        g.evaluate(obs(600))
        g.evaluate(obs(1200))
        // Jump the card far away: drift >> maxCenterDrift.
        val moved = GateObservation.Box(75f, 100f, 925f, 636f)
        assertFalse(g.evaluate(obs(1800, box = moved)).pass)
        // Needs the full streak again from the new position.
        assertFalse(g.evaluate(obs(2400, box = moved)).pass)
        assertFalse(g.evaluate(obs(3000, box = moved)).pass)
        assertTrue(g.evaluate(obs(3600, box = moved)).pass)
    }

    @Test
    fun displayedHintIsHeldToAvoidFlicker() {
        val g = gate()
        assertEquals(GateHint.NO_DOCUMENT, g.evaluate(obs(0, box = null)).hint)
        // 100ms later the condition changes, but the hint holds (<500ms).
        val small = GateObservation.Box(300f, 900f, 700f, 1152f)
        assertEquals(GateHint.NO_DOCUMENT, g.evaluate(obs(100, box = small)).hint)
        // After the hold expires the new hint shows.
        assertEquals(GateHint.TOO_SMALL, g.evaluate(obs(700, box = small)).hint)
    }

    @Test
    fun enteringPassIsNeverDelayedByTheHold() {
        val g = gate()
        g.evaluate(obs(0))
        g.evaluate(obs(50))
        g.evaluate(obs(100))
        // 4th qualifying frame arrives inside the hold window: PASS anyway.
        val verdict = g.evaluate(obs(150))
        assertTrue(verdict.pass)
        assertEquals(GateHint.PASS, verdict.hint)
    }

    @Test
    fun graceValveOpensTheGateForPersistentCandidates() {
        val stats = GateStats()
        val g = gate(stats)
        // A blurry candidate that never qualifies, held for >4s.
        var t = 0L
        while (t <= 4_200) {
            g.evaluate(obs(t, sharpness = 5f))
            t += 300
        }
        val late = g.evaluate(obs(4_500, sharpness = 5f))
        assertTrue(late.pass, "grace valve should let recognition try")
        assertEquals(GateHint.HOLD_STEADY, late.hint) // guidance continues
        assertTrue(stats.summary().contains("grace"), stats.summary())
    }

    @Test
    fun statsSummarizeTheSession() {
        val stats = GateStats()
        val g = gate(stats)
        g.evaluate(obs(0, box = null))
        g.evaluate(obs(600))
        g.evaluate(obs(1200))
        g.evaluate(obs(1800))
        g.evaluate(obs(2400))
        val summary = stats.summary()
        assertTrue(summary.contains("5 frames"), summary)
        assertTrue(summary.contains("1 passed"), summary)
    }
}
