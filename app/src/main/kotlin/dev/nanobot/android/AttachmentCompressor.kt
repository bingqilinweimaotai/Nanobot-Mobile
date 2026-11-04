package dev.nanobot.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Re-encodes a raster image so it fits the gateway's per-file attachment limit. The long edge is
 * capped at [TARGET_LONG_EDGE]; quality drops before the image is scaled further, so ordinary
 * camera photos usually finish in one or two encodes.
 */
internal object AttachmentCompressor {
    private const val TARGET_LONG_EDGE = 1920
    private const val MIN_LONG_EDGE = 640
    private val QUALITY_STEPS = intArrayOf(85, 75, 65, 55)

    fun compress(bytes: ByteArray, mime: String, maxBytes: Long): CompressedAttachment? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = AttachmentPreparer.sampleSize(
            bounds.outWidth,
            bounds.outHeight,
            TARGET_LONG_EDGE,
        )
        var bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null

        val oriented = orient(bitmap, exifOrientation(bytes))
        if (oriented !== bitmap) {
            bitmap.recycle()
            bitmap = oriented
        }
        val scaled = scaleToLongEdge(bitmap, TARGET_LONG_EDGE)
        if (scaled !== bitmap) {
            bitmap.recycle()
            bitmap = scaled
        }

        // WebP keeps transparency and does better on screenshots; JPEG is the safer choice for
        // camera photos and is what the gateway expects for image/jpeg.
        val useWebp = mime != "image/jpeg"
        val format = if (useWebp) webpFormat() else Bitmap.CompressFormat.JPEG
        val outMime = if (useWebp) "image/webp" else "image/jpeg"

        try {
            var quality = 0
            while (true) {
                val output = ByteArrayOutputStream()
                bitmap.compress(format, QUALITY_STEPS[quality], output)
                val encoded = output.toByteArray()
                if (encoded.size <= maxBytes) return CompressedAttachment(encoded, outMime)

                if (quality < QUALITY_STEPS.lastIndex) {
                    quality++
                    continue
                }
                val longEdge = maxOf(bitmap.width, bitmap.height)
                if (longEdge <= MIN_LONG_EDGE) return null
                val smaller = scaleToLongEdge(bitmap, longEdge * 4 / 5)
                if (smaller !== bitmap) {
                    bitmap.recycle()
                    bitmap = smaller
                }
                quality = 1
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun scaleToLongEdge(bitmap: Bitmap, target: Int): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= target) return bitmap
        val ratio = target.toFloat() / longEdge
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun exifOrientation(bytes: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
}
