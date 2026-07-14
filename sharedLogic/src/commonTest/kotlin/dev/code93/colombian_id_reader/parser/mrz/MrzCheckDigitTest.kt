package dev.code93.colombian_id_reader.parser.mrz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hand-verified vectors. These pin [MrzCheckDigit] independently, which
 * makes it safe to reuse in MrzFixtureBuilder to generate test cards.
 */
class MrzCheckDigitTest {

    @Test
    fun handVerifiedVectors() {
        // 5·7+2·3+0·1+7·7+2·3+7·1 = 103 → 3
        assertEquals(3, MrzCheckDigit.compute("520727"))
        // ICAO 9303 sample document number
        assertEquals(7, MrzCheckDigit.compute("D23145890"))
        // ARCHITECTURE.md §6.1 sample: birth and expiry
        assertEquals(3, MrzCheckDigit.compute("880821"))
        assertEquals(0, MrzCheckDigit.compute("310130"))
    }

    @Test
    fun fillerCountsAsZero() {
        assertEquals(0, MrzCheckDigit.compute("<<<<<<"))
        assertEquals(MrzCheckDigit.compute("123"), MrzCheckDigit.compute("123<<<"))
    }

    @Test
    fun charactersOutsideMrzAlphabetYieldNull() {
        assertNull(MrzCheckDigit.compute("52a727"))
        assertNull(MrzCheckDigit.compute("880-21"))
    }

    @Test
    fun validate() {
        assertTrue(MrzCheckDigit.validate("520727", '3'))
        assertFalse(MrzCheckDigit.validate("520727", '4'))
        assertFalse(MrzCheckDigit.validate("520727", '<'))
        assertFalse(MrzCheckDigit.validate("52a727", '3'))
    }
}
