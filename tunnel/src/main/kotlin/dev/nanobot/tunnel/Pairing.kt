package dev.nanobot.tunnel

import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the phone needs to reach one nanobot gateway.
 *
 * A relay offer never contains the gateway's WebSocket port as a connectivity requirement: the PC
 * agent owns the local port and forwards to `127.0.0.1:<port>` itself. [gatewayPort] is advertised
 * for diagnostics and for agents that run more than one gateway. Direct offers do carry the full
 * endpoint because there is no agent in the middle.
 */
@Serializable
data class PairingOffer(
    val v: Int = VERSION,
    val kind: String = KIND_RELAY,
    val name: String = "",
    val relay: String = "",
    val serverId: String = "",
    val clientToken: String = "",
    /** Base64url-encoded 32-byte AES-GCM key shared by the phone and the PC agent. */
    val pairKey: String = "",
    val endpoint: String = "",
    val secret: String = "",
    val gatewayPort: Int? = null,
    val platform: String = "",
) {
    val displayName: String
        get() = name.trim().ifBlank {
            when (kind) {
                KIND_DIRECT -> runCatching { URI(endpoint).host }.getOrNull().orEmpty()
                else -> serverId.take(12)
            }.ifBlank { "nanobot" }
        }

    fun requireValid(): PairingOffer {
        if (v != VERSION) throw PairingException("unsupported pairing version $v")
        val normalizedKind = kind.trim().lowercase()
        if (normalizedKind != KIND_RELAY && normalizedKind != KIND_DIRECT) {
            throw PairingException("unknown pairing kind: $kind")
        }
        if (gatewayPort != null && gatewayPort !in 1..65535) {
            throw PairingException("invalid gateway port: $gatewayPort")
        }
        when (normalizedKind) {
            KIND_RELAY -> {
                if (!relay.startsWith("ws://") && !relay.startsWith("wss://")) {
                    throw PairingException("relay address must start with ws:// or wss://")
                }
                if (serverId.isBlank()) throw PairingException("pairing code has no server id")
                if (clientToken.isBlank()) throw PairingException("pairing code has no client token")
                if (pairKey.isBlank()) throw PairingException("pairing code has no pair key")
                runCatching { TunnelCrypto.decodeKey(pairKey) }
                    .getOrElse { throw PairingException(it.message ?: "invalid pair key") }
            }

            KIND_DIRECT -> {
                if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                    throw PairingException("gateway address must start with http:// or https://")
                }
            }
        }
        return copy(
            kind = normalizedKind,
            name = name.trim().take(64),
            relay = relay.trim(),
            endpoint = endpoint.trim().trimEnd('/'),
        )
    }

    companion object {
        const val VERSION = 1
        const val KIND_RELAY = "relay"
        const val KIND_DIRECT = "direct"
    }
}

class PairingException(message: String) : IllegalArgumentException(message)

/**
 * Encodes the pairing payload as a QR-friendly `nanobot://pair?offer=...` link.
 *
 * The app also accepts plain `offer` fragments (the Paseo convention) and simple query parameters,
 * so a host can print a link without a QR renderer.
 */
object PairingLinks {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(offer: PairingOffer): String {
        val payload = json.encodeToString(PairingOffer.serializer(), offer.requireValid())
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "nanobot://pair?offer=$encoded"
    }

    fun parse(raw: String): PairingOffer {
        val text = raw.trim()
        if (text.isEmpty()) throw PairingException("pairing link is empty")
        if (text.startsWith("{")) return decodeJson(text)

        if (text.startsWith("nanobot://") || text.startsWith("http://") ||
            text.startsWith("https://")
        ) {
            val uri = runCatching { URI(text) }
                .getOrElse { throw PairingException("pairing link is not a valid URI") }
            val params = parseQuery(uri.rawQuery) + parseQuery(uri.rawFragment)
            params["offer"]?.let { offer ->
                val payload = if (offer.trimStart().startsWith("{")) {
                    offer
                } else {
                    decodeBase64(offer)
                }
                return decodeJson(payload)
            }
            if (params.isNotEmpty()) return fromQuery(params)
        }

        // Also accept a bare base64 payload so a link can be copied without the scheme.
        val payload = runCatching { decodeBase64(text) }.getOrNull()
        if (payload != null && payload.trimStart().startsWith("{")) return decodeJson(payload)
        throw PairingException("unsupported pairing link")
    }

    private fun decodeJson(payload: String): PairingOffer {
        val offer = runCatching { json.decodeFromString(PairingOffer.serializer(), payload) }
            .getOrElse { throw PairingException("pairing data is invalid: ${it.message}") }
        return offer.requireValid()
    }

    private fun fromQuery(params: Map<String, String>): PairingOffer {
        val endpoint = params["endpoint"].orEmpty()
        val kind = params["kind"]
            ?: if (endpoint.isNotBlank()) PairingOffer.KIND_DIRECT else PairingOffer.KIND_RELAY
        return PairingOffer(
            kind = kind,
            name = params["name"].orEmpty(),
            relay = params["relay"] ?: params["relayUrl"].orEmpty(),
            serverId = params["server"] ?: params["serverId"].orEmpty(),
            clientToken = params["token"] ?: params["clientToken"].orEmpty(),
            pairKey = params["key"] ?: params["pairKey"].orEmpty(),
            endpoint = endpoint,
            secret = params["secret"].orEmpty(),
            gatewayPort = params["port"]?.toIntOrNull(),
            platform = params["platform"].orEmpty(),
        ).requireValid()
    }

    private fun decodeBase64(value: String): String {
        val normalized = value.trim()
            .replace('-', '+')
            .replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return runCatching { String(Base64.getDecoder().decode(padded), Charsets.UTF_8) }
            .getOrElse { throw PairingException("pairing data is not valid base64") }
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val separator = part.indexOf('=')
                val key = if (separator >= 0) part.substring(0, separator) else part
                val value = if (separator >= 0) part.substring(separator + 1) else ""
                runCatching {
                    URLDecoder.decode(key, Charsets.UTF_8.name()) to
                        URLDecoder.decode(value, Charsets.UTF_8.name())
                }.getOrNull()
            }
            .toMap()
    }
}
