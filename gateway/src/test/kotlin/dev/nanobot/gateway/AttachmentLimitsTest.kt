package dev.nanobot.gateway

import dev.nanobot.protocol.AttachmentLimits
import dev.nanobot.protocol.BootstrapResponse
import dev.nanobot.protocol.NanobotJson
import kotlin.test.Test
import kotlin.test.assertEquals

class AttachmentLimitsTest {
    @Test
    fun `bootstrap advertises the per-file attachment budget`() {
        val boot = NanobotJson.decodeFromString<BootstrapResponse>(
            """
            {"token":"t","limits":{"attachments":{
              "max_count":4,"max_file_bytes":6291456,"max_total_bytes":25165824}}}
            """.trimIndent(),
        )

        assertEquals(6L * 1024 * 1024, boot.limits?.attachments?.maxFileBytes)
        assertEquals(24L * 1024 * 1024, boot.limits?.attachments?.maxTotalBytes)
    }

    @Test
    fun `attachment limit uses the advertised budget and the video budget`() {
        assertEquals(6L * 1024 * 1024, attachmentLimit("image/jpeg", null))
        assertEquals(
            8L * 1024 * 1024,
            attachmentLimit("application/pdf", AttachmentLimits(maxFileBytes = 8L * 1024 * 1024)),
        )
        assertEquals(
            20L * 1024 * 1024,
            attachmentLimit("video/mp4", AttachmentLimits(maxFileBytes = 8L * 1024 * 1024)),
        )
    }

    @Test
    fun `gateway error detail extracts the error field`() {
        assertEquals(
            "Attachment exceeds the 6 MiB upload limit (8,420,000 bytes)",
            attachmentErrorDetail(
                """{"error":"Attachment exceeds the 6 MiB upload limit (8,420,000 bytes)"}""",
            ),
        )
        assertEquals("Unauthorized", attachmentErrorDetail("Unauthorized"))
        assertEquals("", attachmentErrorDetail("  "))
    }
}
