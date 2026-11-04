package dev.nanobot.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentCompressorTest {
    @Test
    fun compressesALargePhotoUnderTheGatewayLimit() {
        val width = 3200
        val height = 2400
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Noise defeats JPEG's compression, so the fixture really is larger than 6 MiB.
        val pixels = IntArray(width * height)
        var state = 0x12345678
        for (i in pixels.indices) {
            state = state * 1103515245 + 12345
            pixels[i] = state or 0xFF000000.toInt()
        }
        source.setPixels(pixels, 0, width, 0, 0, width, height)
        val sourceBytes = ByteArrayOutputStream()
            .also { source.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            .toByteArray()
        source.recycle()

        val limit = 6L * 1024 * 1024
        assertTrue("the fixture should exceed the limit", sourceBytes.size > limit)

        val compressed = AttachmentCompressor.compress(sourceBytes, "image/jpeg", limit)
            ?: error("compression returned null")
        assertTrue("compressed size ${compressed.bytes.size}", compressed.bytes.size <= limit)
        assertEquals("image/jpeg", compressed.mime)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(compressed.bytes, 0, compressed.bytes.size, bounds)
        assertTrue("long edge ${maxOf(bounds.outWidth, bounds.outHeight)}", maxOf(bounds.outWidth, bounds.outHeight) <= 1920)
    }
}
