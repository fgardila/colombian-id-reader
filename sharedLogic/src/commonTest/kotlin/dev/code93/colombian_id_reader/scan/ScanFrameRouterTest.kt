package dev.code93.colombian_id_reader.scan

import dev.code93.colombian_id_reader.fixtures.MrzFixtureBuilder
import dev.code93.colombian_id_reader.fixtures.MrzFixtures
import dev.code93.colombian_id_reader.fixtures.Pdf417Fixtures
import dev.code93.colombian_id_reader.model.DetectorFilter
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

        fun router(filter: DetectorFilter) = ScanFrameRouter<Unit>(
            filter = filter,
            pdf417 = { barcodeCalls++; barcode },
            mrzOcr = { ocrCalls++; ocrLines }
        )
    }

    @Test
    fun shortCircuitsOnPdf417Success() = runTest {
        val detectors = Detectors(barcode = validPdf417Raw, ocrLines = MrzFixtures.validCard)
        val result = detectors.router(DetectorFilter.ALL).process(Unit)

        assertIs<ScanResult.Success>(result)
        assertEquals(1, detectors.barcodeCalls)
        assertEquals(0, detectors.ocrCalls) // OCR never runs when the barcode wins
    }

    @Test
    fun fallsThroughToMrzWhenNoBarcode() = runTest {
        val detectors = Detectors(barcode = null, ocrLines = MrzFixtures.validCard)
        val result = detectors.router(DetectorFilter.ALL).process(Unit)

        assertIs<ScanResult.Success>(result)
        assertEquals(1, detectors.ocrCalls)
    }

    @Test
    fun fallsThroughToMrzWhenBarcodeParsesToError() = runTest {
        // A decoded but foreign/partial PDF417: parse fails, MRZ still runs.
        val detectors = Detectors(barcode = "garbage", ocrLines = MrzFixtures.validCard)
        val result = detectors.router(DetectorFilter.ALL).process(Unit)

        assertIs<ScanResult.Success>(result)
        assertEquals(1, detectors.barcodeCalls)
        assertEquals(1, detectors.ocrCalls)
    }

    @Test
    fun closedGateSuppressesOnlyTheOcrLeg() = runTest {
        val detectors = Detectors(barcode = null, ocrLines = MrzFixtures.validCard)
        // Gate closed: the OCR leg must not run even with a readable MRZ...
        assertNull(detectors.router(DetectorFilter.ALL).process(Unit, allowOcr = false))
        assertEquals(0, detectors.ocrCalls)
        // ...but the PDF417 leg stays hot (Stage 2 amendment: barcode is
        // cheap and self-validating).
        assertEquals(1, detectors.barcodeCalls)

        val withBarcode = Detectors(barcode = validPdf417Raw)
        val result = withBarcode.router(DetectorFilter.ALL).process(Unit, allowOcr = false)
        assertIs<ScanResult.Success>(result)
    }

    @Test
    fun nothingUsableOnFrameReturnsNull() = runTest {
        val detectors = Detectors(barcode = null, ocrLines = listOf("REPUBLICA DE COLOMBIA"))
        assertNull(detectors.router(DetectorFilter.ALL).process(Unit))
    }

    @Test
    fun mrzErrorPropagatesSoCallerKeepsScanning() = runTest {
        val corrupted = MrzFixtureBuilder.corrupt(MrzFixtures.validCard, 1, 0)
        val detectors = Detectors(barcode = null, ocrLines = corrupted)
        assertIs<ScanResult.Error>(detectors.router(DetectorFilter.ALL).process(Unit))
    }

    @Test
    fun pdf417OnlyNeverRunsOcr() = runTest {
        val detectors = Detectors(barcode = null, ocrLines = MrzFixtures.validCard)
        assertNull(detectors.router(DetectorFilter.PDF417_ONLY).process(Unit))
        assertEquals(1, detectors.barcodeCalls)
        assertEquals(0, detectors.ocrCalls)
    }

    @Test
    fun mrzOnlyNeverRunsBarcode() = runTest {
        val detectors = Detectors(barcode = validPdf417Raw, ocrLines = MrzFixtures.validCard)
        val result = detectors.router(DetectorFilter.MRZ_ONLY).process(Unit)

        assertIs<ScanResult.Success>(result)
        assertEquals(0, detectors.barcodeCalls)
        assertEquals(1, detectors.ocrCalls)
    }
}
