package dev.code93.colombian_id_reader.scan

import dev.code93.colombian_id_reader.fixtures.MrzFixtures
import dev.code93.colombian_id_reader.fixtures.Pdf417Fixtures
import dev.code93.colombian_id_reader.model.ScanMode
import dev.code93.colombian_id_reader.model.ScanResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ScanFrameRouterTest {

    private val validPdf417Raw = Pdf417Fixtures.all.first { it.expected != null }.raw

    private class Detectors(
        var barcode: String? = null,
        var ocrLines: List<String> = emptyList()
    ) {
        var barcodeCalls = 0
        var ocrCalls = 0

        fun router(mode: ScanMode) = ScanFrameRouter<Unit>(
            mode = mode,
            pdf417 = { barcodeCalls++; barcode },
            mrzOcr = { ocrCalls++; ocrLines }
        )
    }

    @Test
    fun autoShortCircuitsOnPdf417Success() = runTest {
        val detectors = Detectors(barcode = validPdf417Raw, ocrLines = MrzFixtures.validCard)
        val result = detectors.router(ScanMode.AUTO).process(Unit)

        assertIs<ScanResult.Success>(result)
        assertEquals(1, detectors.barcodeCalls)
        assertEquals(0, detectors.ocrCalls) // OCR never runs when the barcode wins
    }

    @Test
    fun autoFallsThroughToMrzWhenNoBarcode() = runTest {
        val detectors = Detectors(barcode = null, ocrLines = MrzFixtures.validCard)
        val result = detectors.router(ScanMode.AUTO).process(Unit)

        assertIs<ScanResult.Success>(result)
        assertEquals(1, detectors.ocrCalls)
    }

    @Test
    fun autoFallsThroughToMrzWhenBarcodeParsesToError() = runTest {
        // A decoded but foreign/partial PDF417: parse fails, MRZ still runs.
        val detectors = Detectors(barcode = "garbage", ocrLines = MrzFixtures.validCard)
        val result = detectors.router(ScanMode.AUTO).process(Unit)

        assertIs<ScanResult.Success>(result)
        assertEquals(1, detectors.barcodeCalls)
        assertEquals(1, detectors.ocrCalls)
    }

    @Test
    fun nothingUsableOnFrameReturnsNull() = runTest {
        val detectors = Detectors(barcode = null, ocrLines = listOf("REPUBLICA DE COLOMBIA"))
        assertNull(detectors.router(ScanMode.AUTO).process(Unit))
    }

    @Test
    fun mrzErrorPropagatesSoCallerKeepsScanning() = runTest {
        val corrupted = dev.code93.colombian_id_reader.fixtures.MrzFixtureBuilder
            .corrupt(MrzFixtures.validCard, 1, 0)
        val detectors = Detectors(barcode = null, ocrLines = corrupted)
        assertIs<ScanResult.Error>(detectors.router(ScanMode.AUTO).process(Unit))
    }

    @Test
    fun pdf417OnlyNeverRunsOcr() = runTest {
        val detectors = Detectors(barcode = null, ocrLines = MrzFixtures.validCard)
        assertNull(detectors.router(ScanMode.PDF417_ONLY).process(Unit))
        assertEquals(1, detectors.barcodeCalls)
        assertEquals(0, detectors.ocrCalls)
    }

    @Test
    fun mrzOnlyNeverRunsBarcode() = runTest {
        val detectors = Detectors(barcode = validPdf417Raw, ocrLines = MrzFixtures.validCard)
        val result = detectors.router(ScanMode.MRZ_ONLY).process(Unit)

        assertIs<ScanResult.Success>(result)
        assertEquals(0, detectors.barcodeCalls)
        assertEquals(1, detectors.ocrCalls)
    }
}
