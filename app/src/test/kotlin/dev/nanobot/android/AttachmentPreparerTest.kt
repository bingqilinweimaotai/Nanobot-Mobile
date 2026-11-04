package dev.nanobot.android

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AttachmentPreparerTest {
    private val limit = 100L

    @Test
    fun `a file under the limit is sent unchanged`() {
        val bytes = ByteArray(99)
        val result = AttachmentPreparer.prepare(bytes, "application/pdf", limit) { _, _, _ ->
            error("compression must not run")
        }

        val ready = assertIs<AttachmentPreparation.Ready>(result)
        assertContentEquals(bytes, ready.bytes)
        assertEquals("application/pdf", ready.mime)
    }

    @Test
    fun `an oversized pdf is rejected without compression`() {
        val result = AttachmentPreparer.prepare(ByteArray(101), "application/pdf", limit) { _, _, _ ->
            error("compression must not run")
        }

        val tooLarge = assertIs<AttachmentPreparation.TooLarge>(result)
        assertEquals(101L, tooLarge.size)
        assertEquals(limit, tooLarge.limit)
        assertEquals(false, tooLarge.afterCompression)
    }

    @Test
    fun `an oversized image is compressed`() {
        val compressed = ByteArray(50)
        val result = AttachmentPreparer.prepare(ByteArray(101), "image/jpeg", limit) { _, _, _ ->
            CompressedAttachment(compressed, "image/webp")
        }

        val ready = assertIs<AttachmentPreparation.Ready>(result)
        assertContentEquals(compressed, ready.bytes)
        assertEquals("image/webp", ready.mime)
    }

    @Test
    fun `an oversized gif is not re-encoded`() {
        val result = AttachmentPreparer.prepare(ByteArray(101), "image/gif", limit) { _, _, _ ->
            error("compression must not run")
        }

        assertIs<AttachmentPreparation.TooLarge>(result)
    }

    @Test
    fun `compression that still does not fit is reported as compressed`() {
        val result = AttachmentPreparer.prepare(ByteArray(101), "image/png", limit) { _, _, _ ->
            CompressedAttachment(ByteArray(101), "image/webp")
        }

        val tooLarge = assertIs<AttachmentPreparation.TooLarge>(result)
        assertEquals(true, tooLarge.afterCompression)
    }

    @Test
    fun `sample size keeps the decoded long edge at or above the target`() {
        assertEquals(1, AttachmentPreparer.sampleSize(1920, 1080, 1920))
        assertEquals(2, AttachmentPreparer.sampleSize(4000, 3000, 1920))
        assertEquals(4, AttachmentPreparer.sampleSize(8000, 6000, 1920))
    }
}
