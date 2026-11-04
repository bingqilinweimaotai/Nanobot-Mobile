package dev.nanobot.android

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * App-private cache of chat images, keyed by the gateway-relative media URL.
 *
 * The gateway stays the source of truth. This cache only avoids downloading the same image again
 * after a reconnect, a host re-add, or a process restart. Files live under `filesDir/media-cache`
 * (never MediaStore) and are trimmed to [maxBytes] by least-recent use.
 */
class MediaCache(
    context: Context,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private val root = File(context.applicationContext.filesDir, "media-cache")
    private val staging = File(root, "staging")

    /** The local copy of [url], or null when it has not been cached yet. */
    @Synchronized
    fun lookup(url: String, name: String?, scope: String = ""): Uri? {
        val file = fileFor(url, name, scope)
        if (!file.isFile) return null
        file.setLastModified(System.currentTimeMillis())
        return Uri.fromFile(file)
    }

    /** Copies picked bytes aside; [adopt] renames the file once the gateway reports its URL. */
    @Synchronized
    fun stage(bytes: ByteArray, name: String?): File? = runCatching {
        staging.mkdirs()
        val file = File(staging, "${System.nanoTime()}-${sanitize(name)}")
        file.writeBytes(bytes)
        trim()
        file
    }.getOrNull()

    /**
     * Moves a staged [file] to the URL-keyed entry and returns it. Returns null when [file] is not
     * ours, so a `content://` picker URI is never touched.
     */
    @Synchronized
    fun adopt(file: File?, url: String, name: String?, scope: String = ""): File? {
        if (file == null || !file.isFile) return null
        if (!file.canonicalPath.startsWith(root.canonicalPath + File.separator)) return null
        val target = fileFor(url, name, scope)
        if (target == file) return file
        if (target.isFile) {
            file.delete()
            return target
        }
        return runCatching {
            target.parentFile?.mkdirs()
            if (!file.renameTo(target)) {
                file.copyTo(target, overwrite = true)
                file.delete()
            }
            target.setLastModified(System.currentTimeMillis())
            target
        }.getOrNull()
    }

    /** Stores downloaded bytes under [url] and returns the local URI. */
    @Synchronized
    fun store(bytes: ByteArray, url: String, name: String?, scope: String = ""): Uri? = runCatching {
        val target = fileFor(url, name, scope)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        target.setLastModified(System.currentTimeMillis())
        trim()
        Uri.fromFile(target)
    }.getOrNull()

    /** Deletes a cached file; a picker URI outside the cache is ignored. */
    @Synchronized
    fun delete(uri: Uri?) {
        val file = uri?.path?.let(::File) ?: return
        if (file.canonicalPath.startsWith(root.canonicalPath + File.separator)) file.delete()
    }

    private fun fileFor(url: String, name: String?, scope: String): File {
        val path = url.substringBefore('?')
        val extension = extensionOf(name) ?: extensionOf(path)
        // The scope is the PC identity, so two hosts cannot collide on the same media path.
        return File(root, digest("$scope\n$path") + (extension?.let { ".$it" } ?: ""))
    }

    private fun trim() {
        val now = System.currentTimeMillis()
        staging.listFiles()?.forEach { file ->
            if (now - file.lastModified() > STAGING_TTL_MS) file.delete()
        }
        val files = root.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= maxBytes) return@forEach
            total -= file.length()
            file.delete()
        }
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun extensionOf(value: String?): String? {
        val candidate = value?.substringAfterLast('.', "")?.lowercase(Locale.US)
        return candidate?.takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
    }

    private fun sanitize(name: String?): String =
        name?.replace(Regex("[^A-Za-z0-9._-]"), "_")?.takeLast(64)?.ifBlank { "attachment" }
            ?: "attachment"

    private companion object {
        const val DEFAULT_MAX_BYTES = 256L * 1024 * 1024
        const val STAGING_TTL_MS = 24L * 60 * 60 * 1000
    }
}
