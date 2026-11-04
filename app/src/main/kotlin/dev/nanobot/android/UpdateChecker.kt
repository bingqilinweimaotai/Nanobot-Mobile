package dev.nanobot.android

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/** GitHub repository that publishes the phone APK in its releases. */
internal const val UPDATE_REPOSITORY = "bingqilinweimaotai/Nanobot-Mobile"

/** Result of the About page's release check, held in [UiState.updateStatus]. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus

    /** The repository is public but has no published release yet, or is still private. */
    data object NoRelease : UpdateStatus
    data class UpToDate(val version: String) : UpdateStatus
    data class Available(
        val version: String,
        val downloadUrl: String,
        val releaseUrl: String,
    ) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

internal data class LatestRelease(
    val version: String,
    /** Direct APK asset URL when the release publishes one. */
    val downloadUrl: String?,
    val releaseUrl: String,
)

/**
 * Reads the latest release from the public GitHub API. The app itself has no token, so a private
 * repository (or one without a release) answers 404; that is reported as "no release" rather than
 * an error.
 */
internal object UpdateChecker {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun fetchLatest(): LatestRelease? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$UPDATE_REPOSITORY/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "nanobot-android")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) {
                throw IOException("GitHub returned HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return parseLatestRelease(body)
                ?: throw IOException("Could not read the latest release")
        }
    }
}

/** Extracts the tag and APK (or release page) from a GitHub `releases/latest` payload. */
internal fun parseLatestRelease(body: String): LatestRelease? {
    val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    val tag = root.string("tag_name")?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val assets = (root["assets"] as? JsonArray).orEmpty()
    val downloadUrl = assets.firstNotNullOfOrNull { element ->
        val asset = element as? JsonObject ?: return@firstNotNullOfOrNull null
        val name = asset.string("name").orEmpty()
        if (!name.endsWith(".apk", ignoreCase = true)) return@firstNotNullOfOrNull null
        asset.string("browser_download_url")?.takeIf { it.isNotBlank() }
    }
    val releaseUrl = root.string("html_url")?.takeIf { it.isNotBlank() }
        ?: downloadUrl
        ?: return null
    return LatestRelease(
        version = normalizeVersion(tag),
        downloadUrl = downloadUrl,
        releaseUrl = releaseUrl,
    )
}

internal fun normalizeVersion(version: String): String =
    version.trim().removePrefix("v").removePrefix("V")

internal fun isNewerVersion(latest: String, current: String): Boolean =
    compareVersions(latest, current) > 0

/**
 * Compares dotted version numbers, ignoring a leading `v`. Missing components count as zero, and
 * a release without a pre-release suffix outranks the same version with one (`1.0.0` > `1.0.0-rc1`).
 */
internal fun compareVersions(left: String, right: String): Int {
    val (leftNumbers, leftPre) = splitVersion(left)
    val (rightNumbers, rightPre) = splitVersion(right)
    val size = maxOf(leftNumbers.size, rightNumbers.size)
    for (index in 0 until size) {
        val a = leftNumbers.getOrElse(index) { 0 }
        val b = rightNumbers.getOrElse(index) { 0 }
        if (a != b) return a.compareTo(b)
    }
    return when {
        leftPre == rightPre -> 0
        leftPre.isEmpty() -> 1
        rightPre.isEmpty() -> -1
        else -> leftPre.compareTo(rightPre)
    }
}

private fun splitVersion(version: String): Pair<List<Int>, String> {
    val withoutBuild = normalizeVersion(version).substringBefore('+')
    val core = withoutBuild.substringBefore('-')
    val numbers = core.split('.').map { part ->
        part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }
    val pre = if ('-' in withoutBuild) withoutBuild.substringAfter('-') else ""
    return numbers to pre
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
