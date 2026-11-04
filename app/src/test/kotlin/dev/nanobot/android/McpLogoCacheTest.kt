package dev.nanobot.android

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class McpLogoCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk" +
        "+A8AAQUBAScY42YAAAAASUVORK5CYII="

    @Test
    fun `a data url decodes without network`() {
        var requests = 0
        val cache = McpLogoCache(folder.newFolder("data")) {
            requests += 1
            null
        }

        assertNotNull(cache.loadBlocking("data:image/png;base64,$png"))
        assertEquals(0, requests)
    }

    @Test
    fun `a blocked favicon service falls back to the site icon`() {
        val google = "https://www.google.com/s2/favicons?domain=example.test&sz=64"
        val requested = mutableListOf<String>()
        val cache = McpLogoCache(folder.newFolder("fallback")) { url ->
            requested += url
            if (url == "https://example.test/favicon.ico") Base64.decode(png, Base64.DEFAULT) else null
        }

        assertNotNull(cache.loadBlocking(google))
        assertTrue(requested.contains("https://example.test/favicon.ico"))
    }

    @Test
    fun `a non-image fallback response is skipped`() {
        val cache = McpLogoCache(folder.newFolder("html")) { url ->
            when (url) {
                "https://example.test/favicon.ico" -> "<html>not found</html>".toByteArray()
                "https://example.test/apple-touch-icon.png" -> Base64.decode(png, Base64.DEFAULT)
                else -> null
            }
        }

        assertNotNull(
            cache.loadBlocking("https://www.google.com/s2/favicons?domain=example.test&sz=64"),
        )
    }

    @Test
    fun `a fetched logo is served from disk after a restart`() {        val root = folder.newFolder("logos")
        val bytes = Base64.decode(png, Base64.DEFAULT)
        var requests = 0
        val first = McpLogoCache(root) {
            requests += 1
            bytes
        }

        assertNotNull(first.loadBlocking("https://example.test/icon.png"))
        assertEquals(1, requests)
        assertTrue(root.listFiles().orEmpty().any { it.isFile && it.length() > 0 })

        // A new cache instance stands in for the next process: the disk copy must win.
        val restarted = McpLogoCache(root) {
            requests += 1
            null
        }
        assertNotNull(restarted.loadBlocking("https://example.test/icon.png"))
        assertEquals(1, requests)
    }
}
