package dev.nanobot.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckerTest {
    @Test
    fun `reads the tag and apk asset from a release`() {
        val release = parseLatestRelease(
            """
            {
              "tag_name": "v0.2.0",
              "html_url": "https://github.com/bingqilinweimaotai/Nanobot-Mobile/releases/tag/v0.2.0",
              "assets": [
                {
                  "name": "nanobot-link-windows-amd64.exe",
                  "browser_download_url": "https://example.com/nanobot-link.exe"
                },
                {
                  "name": "nanobot-mobile-v0.2.0.apk",
                  "browser_download_url": "https://example.com/nanobot-mobile-v0.2.0.apk"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("0.2.0", release?.version)
        assertEquals("https://example.com/nanobot-mobile-v0.2.0.apk", release?.downloadUrl)
        assertEquals(
            "https://github.com/bingqilinweimaotai/Nanobot-Mobile/releases/tag/v0.2.0",
            release?.releaseUrl,
        )
    }

    @Test
    fun `falls back to the release page when there is no apk asset`() {
        val release = parseLatestRelease(
            """
            {
              "tag_name": "0.3.0",
              "html_url": "https://example.com/releases/0.3.0",
              "assets": []
            }
            """.trimIndent(),
        )

        assertEquals("0.3.0", release?.version)
        assertNull(release?.downloadUrl)
        assertEquals("https://example.com/releases/0.3.0", release?.releaseUrl)
    }

    @Test
    fun `ignores payloads without a tag`() {
        assertNull(parseLatestRelease("""{"message":"Not Found"}"""))
        assertNull(parseLatestRelease("not json"))
    }

    @Test
    fun `compares dotted versions`() {
        assertTrue(isNewerVersion("0.2.0", "0.1.0"))
        assertTrue(isNewerVersion("v0.1.10", "0.1.9"))
        assertTrue(isNewerVersion("0.1", "0.0.9"))
        assertFalse(isNewerVersion("v0.1.0", "0.1.0"))
        assertFalse(isNewerVersion("0.1", "0.1.0"))
        assertFalse(isNewerVersion("0.0.9", "0.1.0"))
    }

    @Test
    fun `a final release outranks its pre-release`() {
        assertTrue(isNewerVersion("1.0.0", "1.0.0-rc1"))
        assertFalse(isNewerVersion("1.0.0-rc1", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0-rc1", "1.0.0-rc1"))
    }

    @Test
    fun `build metadata does not change the version`() {
        assertFalse(isNewerVersion("1.0.0+build2", "1.0.0+build1"))
    }
}
