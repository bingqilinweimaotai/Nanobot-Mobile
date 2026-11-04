package dev.nanobot.android

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MediaCacheTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "media-cache").deleteRecursively()
    }

    @Test
    fun `a staged pick is found by its gateway url after adoption`() {
        val cache = MediaCache(context)
        val staged = assertNotNull(cache.stage("photo-bytes".toByteArray(), "photo.png"))

        val adopted = assertNotNull(cache.adopt(staged, "/api/media/abc", "photo.png"))

        assertFalse(staged.exists())
        assertTrue(adopted.isFile)
        assertEquals("photo-bytes", adopted.readText())
        assertEquals(Uri.fromFile(adopted), cache.lookup("/api/media/abc", "photo.png"))
    }

    @Test
    fun `a query string does not split the cache entry`() {
        val cache = MediaCache(context)
        val stored = assertNotNull(
            cache.store("bytes".toByteArray(), "/api/media/abc?token=one", "photo.png"),
        )

        assertEquals(stored, cache.lookup("/api/media/abc?token=two", "photo.png"))
    }

    @Test
    fun `two hosts do not share a cache entry`() {
        val cache = MediaCache(context)
        cache.store("first".toByteArray(), "/api/media/abc", "a.png", scope = "pc-one")

        assertNull(cache.lookup("/api/media/abc", "a.png", scope = "pc-two"))
    }

    @Test
    fun `delete removes a cached file`() {
        val cache = MediaCache(context)
        val stored = assertNotNull(cache.store("bytes".toByteArray(), "/api/media/abc", "a.png"))

        cache.delete(stored)

        assertNull(cache.lookup("/api/media/abc", "a.png"))
    }

    @Test
    fun `adopt ignores a file outside the cache directory`() {
        val cache = MediaCache(context)
        val outside = File(context.cacheDir, "outside.png").apply { writeText("x") }

        assertNull(cache.adopt(outside, "/api/media/abc", "outside.png"))
        assertTrue(outside.exists())
    }
}
