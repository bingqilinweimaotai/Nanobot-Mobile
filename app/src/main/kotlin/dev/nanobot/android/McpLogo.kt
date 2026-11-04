package dev.nanobot.android

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Loads the WebUI's MCP brand icons.
 *
 * `logo_url` points at the same favicon service the WebUI uses (or an inline `data:image` for a
 * packaged logo), so the app shows the same marks. Fetched bytes are kept in a small on-disk cache
 * under `filesDir/mcp-logos`; a reconnect or process restart shows the cached mark immediately
 * instead of fetching it again.
 */
internal class McpLogoCache(
    private val root: File,
    private val fetch: (String) -> ByteArray? = McpLogoHttp::fetch,
) {
    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_ENTRIES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = 1
    }
    private val failed = Collections.synchronizedSet(mutableSetOf<String>())
    private val trimLock = Any()

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        loadBlocking(url)
    }

    internal fun loadBlocking(url: String): ImageBitmap? {
        val key = url.trim()
        if (key.isEmpty()) return null
        memory.get(key)?.let { return it }
        if (key in failed) return null

        if (key.startsWith(DATA_PREFIX, ignoreCase = true)) {
            val decoded = decodeDataUrl(key)
            if (decoded == null) {
                failed.add(key)
            } else {
                memory.put(key, decoded)
            }
            return decoded
        }

        val file = fileFor(key)
        if (file.isFile) {
            val cached = decodeBytes(runCatching { file.readBytes() }.getOrNull())
            if (cached != null) {
                memory.put(key, cached)
                return cached
            }
            runCatching { file.delete() }
        }

        val bytes = fetchImage(candidatesFor(key))
        if (bytes == null) {
            failed.add(key)
            return null
        }
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeBytes(bytes.data)
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            file.setLastModified(System.currentTimeMillis())
            trim()
        }
        memory.put(key, bytes.bitmap)
        return bytes.bitmap
    }

    private fun fetchImage(candidates: List<String>): FetchedLogo? {
        for (candidate in candidates) {
            val bytes = runCatching { fetch(candidate) }.getOrNull() ?: continue
            val bitmap = decodeBytes(bytes) ?: continue
            return FetchedLogo(bytes, bitmap)
        }
        return null
    }

    /**
     * The WebUI points `logo_url` at Google's favicon service, which is unreachable on some
     * networks (mainland China). Fall back to the site's own icon files and, last, a mirror of the
     * same service, so the grid still shows the brand mark instead of falling back to initials.
     */
    private fun candidatesFor(url: String): List<String> {
        val domain = faviconDomain(url) ?: return listOf(url)
        return listOf(
            url,
            "https://$domain/favicon.ico",
            "https://$domain/apple-touch-icon.png",
            MIRROR_PREFIX + domain,
        )
    }

    private fun faviconDomain(url: String): String? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (!uri.host.equals("www.google.com", ignoreCase = true)) return null
        if (uri.path?.startsWith("/s2/favicons") != true) return null
        val domain = uri.getQueryParameter("domain")?.trim() ?: return null
        return domain.takeIf { value ->
            value.isNotEmpty() && value.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        }
    }

    private fun fileFor(url: String): File =
        File(root, MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) } + ".img")

    private fun trim() = synchronized(trimLock) {
        val files = root.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_DISK_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= MAX_DISK_BYTES) return@forEach
            total -= file.length()
            file.delete()
        }
    }

    private fun decodeDataUrl(url: String): ImageBitmap? {
        val comma = url.indexOf(',')
        if (comma <= 0) return null
        if (!url.substring(0, comma).contains(";base64", ignoreCase = true)) return null
        val bytes = runCatching {
            Base64.decode(url.substring(comma + 1), Base64.DEFAULT)
        }.getOrNull()
        return decodeBytes(bytes)
    }

    private fun decodeBytes(bytes: ByteArray?): ImageBitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return@runCatching null
            var sample = 1
            while (width / sample > MAX_EDGE_PX || height / sample > MAX_EDGE_PX) sample *= 2
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )?.asImageBitmap()
        }.getOrNull()
    }

    private class FetchedLogo(val data: ByteArray, val bitmap: ImageBitmap)

    private companion object {
        const val DATA_PREFIX = "data:image/"
        const val MIRROR_PREFIX = "https://favicon.cccyun.cc/"
        const val MAX_EDGE_PX = 192
        const val MEMORY_ENTRIES = 64
        const val MAX_DISK_BYTES = 4L * 1024 * 1024
    }
}

private object McpLogoHttp {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Hosts that failed at the transport layer (DNS, connect, TLS). A blocked favicon service such
     * as Google's must not cost every icon its own timeout, so later requests skip it outright.
     */
    private val downHosts = ConcurrentHashMap.newKeySet<String>()

    fun fetch(url: String): ByteArray? {
        val request = runCatching {
            Request.Builder().url(url).header("Accept", "image/*").build()
        }.getOrNull() ?: return null
        val host = request.url.host
        if (host in downHosts) return null
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > MAX_IMAGE_BYTES) return@use null
                body.bytes().takeIf { it.size <= MAX_IMAGE_BYTES }
            }
        } catch (io: java.io.IOException) {
            downHosts.add(host)
            null
        }
    }

    private const val MAX_IMAGE_BYTES = 1024 * 1024
}

private object McpLogoCaches {
    @Volatile
    private var cache: McpLogoCache? = null

    fun forContext(context: Context): McpLogoCache =
        cache ?: synchronized(this) {
            cache ?: McpLogoCache(
                File(context.applicationContext.filesDir, "mcp-logos"),
            ).also { cache = it }
        }
}

/** The cached WebUI brand mark for [url], or null while it loads and when no logo exists. */
@Composable
internal fun rememberMcpLogo(url: String?): ImageBitmap? {
    val context = LocalContext.current
    val key = url?.trim().orEmpty()
    return produceState<ImageBitmap?>(initialValue = null, key) {
        value = if (key.isEmpty()) {
            null
        } else {
            McpLogoCaches.forContext(context).load(key)
        }
    }.value
}
