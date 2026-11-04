package dev.nanobot.android

/** An image re-encoded to fit the gateway's per-file attachment budget. */
internal data class CompressedAttachment(val bytes: ByteArray, val mime: String)

/** What to do with a selected file before it is staged and uploaded. */
internal sealed interface AttachmentPreparation {
    data class Ready(val bytes: ByteArray, val mime: String) : AttachmentPreparation

    data class TooLarge(
        val size: Long,
        val limit: Long,
        /** True when the file was an image and compression still did not bring it under [limit]. */
        val afterCompression: Boolean,
    ) : AttachmentPreparation
}

/**
 * Decides whether a selected file can be uploaded as-is, must be compressed first, or cannot be
 * sent at all. Kept free of Android types so the size policy can be tested on the JVM.
 */
internal object AttachmentPreparer {
    /**
     * [compress] is only consulted for oversized raster images. GIFs are left alone because
     * re-encoding them would drop the animation, and documents cannot be shrunk generically.
     */
    fun prepare(
        bytes: ByteArray,
        mime: String,
        maxBytes: Long,
        compress: (ByteArray, String, Long) -> CompressedAttachment?,
    ): AttachmentPreparation {
        if (bytes.size <= maxBytes) return AttachmentPreparation.Ready(bytes, mime)
        if (!mime.startsWith("image/") || mime == "image/gif") {
            return AttachmentPreparation.TooLarge(bytes.size.toLong(), maxBytes, afterCompression = false)
        }
        val compressed = compress(bytes, mime, maxBytes)
            ?: return AttachmentPreparation.TooLarge(bytes.size.toLong(), maxBytes, afterCompression = false)
        return if (compressed.bytes.size <= maxBytes) {
            AttachmentPreparation.Ready(compressed.bytes, compressed.mime)
        } else {
            AttachmentPreparation.TooLarge(
                compressed.bytes.size.toLong(),
                maxBytes,
                afterCompression = true,
            )
        }
    }

    /**
     * The power-of-two sample that decodes at or above [targetLongEdge]. Decoding at a smaller
     * sample and scaling up would lose detail; decoding much larger wastes memory.
     */
    fun sampleSize(width: Int, height: Int, targetLongEdge: Int): Int {
        var sample = 1
        val longEdge = maxOf(width, height)
        while (longEdge / (sample * 2) >= targetLongEdge) sample *= 2
        return sample
    }
}
