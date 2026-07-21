package dev.code93.colombian_id_reader.scan

import dev.code93.colombian_id_reader.model.DocumentType
import dev.code93.colombian_id_reader.model.GateHint
import dev.code93.colombian_id_reader.model.NameMatch
import dev.code93.colombian_id_reader.model.ScannedDocument
import dev.code93.colombian_id_reader.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CaptureFlowControllerTest {

    private val document = ScannedDocument.ColombianId(
        documentType = DocumentType.CEDULA_DIGITAL,
        givenNames = "MARIA DANIELA",
        surnames = "MARTINEZ GARCIA",
        birthDate = null,
        sex = Sex.FEMALE,
        nuip = "1032456789",
        bloodType = null,
        expirationDate = null
    )

    private val pass = GateVerdict(pass = true, hint = GateHint.PASS)
    private val gracePass = GateVerdict(pass = true, hint = GateHint.HOLD_STEADY)
    private val fail = GateVerdict(pass = false, hint = GateHint.HOLD_STEADY)

    // --- capture disabled: byte-identical 0.3.0 behavior ---

    @Test
    fun disabledStartsInBackAndNeverCapturesFront() {
        val flow = CaptureFlowController(captureEnabled = false)
        assertEquals(CaptureFlowController.Phase.BACK, flow.phase)
        assertFalse(flow.shouldCaptureFront(pass))

        val capture = flow.assemble(document, backJpeg = byteArrayOf(1, 2))
        assertSame(document, capture.document)
        assertNull(capture.images)
        assertEquals(NameMatch.NOT_CHECKED, capture.nameMatch)
        assertEquals(CaptureFlowController.Phase.DONE, flow.phase)
    }

    // --- front capture trigger ---

    @Test
    fun naturalPassCapturesImmediately() {
        val flow = CaptureFlowController(captureEnabled = true)
        assertEquals(CaptureFlowController.Phase.FRONT, flow.phase)
        assertTrue(flow.shouldCaptureFront(pass))
    }

    @Test
    fun gracePassesCaptureOnlyAfterAStreakOfTen() {
        val flow = CaptureFlowController(captureEnabled = true)
        repeat(9) { assertFalse(flow.shouldCaptureFront(gracePass), "frame ${it + 1}") }
        assertTrue(flow.shouldCaptureFront(gracePass))
    }

    @Test
    fun failedFrameResetsTheGraceStreak() {
        val flow = CaptureFlowController(captureEnabled = true)
        repeat(9) { flow.shouldCaptureFront(gracePass) }
        assertFalse(flow.shouldCaptureFront(fail))
        repeat(9) { assertFalse(flow.shouldCaptureFront(gracePass)) }
        assertTrue(flow.shouldCaptureFront(gracePass))
    }

    @Test
    fun afterFrontCaptureThePhaseIsBackAndTriggerIsOff() {
        val flow = CaptureFlowController(captureEnabled = true)
        flow.onFrontCaptured(byteArrayOf(1), listOf("MARTINEZ GARCIA"))
        assertEquals(CaptureFlowController.Phase.BACK, flow.phase)
        assertFalse(flow.shouldCaptureFront(pass))
    }

    @Test
    fun frontCannotBeCapturedTwice() {
        val flow = CaptureFlowController(captureEnabled = true)
        flow.onFrontCaptured(null, emptyList())
        assertFailsWith<IllegalStateException> { flow.onFrontCaptured(null, emptyList()) }
    }

    // --- assembly ---

    private fun captureFlowWithFront(
        frontJpeg: ByteArray? = byteArrayOf(10),
        frontLines: List<String> = listOf("MARTINEZ GARCIA", "MARIA DANIELA")
    ) = CaptureFlowController(captureEnabled = true).apply {
        onFrontCaptured(frontJpeg, frontLines)
    }

    @Test
    fun assembleBundlesImagesAndMatch() {
        val back = byteArrayOf(20, 21)
        val capture = captureFlowWithFront().assemble(document, back)

        val images = assertNotNull(capture.images)
        assertNotNull(images.front)
        assertSame(back, images.back)
        assertEquals(NameMatch.MATCH, capture.nameMatch)
    }

    @Test
    fun wrongFrontYieldsMismatch() {
        val flow = captureFlowWithFront(frontLines = listOf("VELEZ RUIZ", "GERONIMO"))
        assertEquals(NameMatch.MISMATCH, flow.assemble(document, byteArrayOf(1)).nameMatch)
    }

    @Test
    fun emptyFrontOcrYieldsNotChecked() {
        val flow = captureFlowWithFront(frontLines = emptyList())
        assertEquals(NameMatch.NOT_CHECKED, flow.assemble(document, byteArrayOf(1)).nameMatch)
    }

    @Test
    fun failedFrontJpegStillDeliversBackImage() {
        val capture = captureFlowWithFront(frontJpeg = null).assemble(document, byteArrayOf(1))
        val images = assertNotNull(capture.images)
        assertNull(images.front)
        assertEquals(NameMatch.MATCH, capture.nameMatch)
    }

    @Test
    fun failedBackJpegDeliversDataWithoutImagesButStillChecksNames() {
        // A back image is the one mandatory piece of DocumentImages
        // (§4.4) — without it there is no images object, but the
        // cross-check already has everything it needs.
        val capture = captureFlowWithFront().assemble(document, backJpeg = null)
        assertNull(capture.images)
        assertEquals(NameMatch.MATCH, capture.nameMatch)
    }
}
