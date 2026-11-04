package dev.nanobot.gateway

import dev.nanobot.protocol.BootstrapResponse
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Builds the authenticated WebSocket URL advertised by `/webui/bootstrap`. */
internal fun buildWebSocketUrl(
    baseUrl: String,
    boot: BootstrapResponse,
    clientId: String,
): String {
    val raw = boot.wsUrl?.takeIf { it.isNotBlank() } ?: run {
        val base = baseUrl.trimEnd('/')
        val wsBase = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> base
        }
        val path = boot.wsPath.ifBlank { "/" }.let { if (it.startsWith("/")) it else "/$it" }
        wsBase + path
    }
    val httpUrl = when {
        raw.startsWith("wss://") -> "https://" + raw.removePrefix("wss://")
        raw.startsWith("ws://") -> "http://" + raw.removePrefix("ws://")
        else -> raw
    }
    val withQuery = httpUrl.toHttpUrl().newBuilder()
        .addQueryParameter("token", boot.token)
        .addQueryParameter("client_id", clientId)
        .build()
    return withQuery.toString()
        .replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://")
}
