package dev.code93.colombian_id_reader.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentImagesTest {

    @Test
    fun disposeZeroFillsBothBuffers() {
        val front = byteArrayOf(1, 2, 3)
        val back = byteArrayOf(4, 5, 6, 7)
        val images = DocumentImages(front, back)

        assertFalse(images.isDisposed)
        images.dispose()

        assertTrue(images.isDisposed)
        assertTrue(front.all { it == 0.toByte() })
        assertTrue(back.all { it == 0.toByte() })
    }

    @Test
    fun disposeIsIdempotentAndHandlesNullFront() {
        val images = DocumentImages(front = null, back = byteArrayOf(9, 9))
        images.dispose()
        images.dispose()
        assertTrue(images.isDisposed)
    }

    @Test
    fun toStringNeverLeaksImageBytes() {
        // §7: a DocumentImages in a log line must disclose sizes only.
        val images = DocumentImages(byteArrayOf(0x41, 0x42), byteArrayOf(0x43, 0x44, 0x45))
        val text = images.toString()
        assertEquals(
            "DocumentImages(front=2 bytes, back=3 bytes, format=JPEG, disposed=false)",
            text
        )
    }

    @Test
    fun defaultsToJpeg() {
        assertEquals(ImageFormat.JPEG, DocumentImages(null, byteArrayOf(1)).format)
    }
}
