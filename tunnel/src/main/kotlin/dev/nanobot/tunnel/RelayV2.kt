package dev.nanobot.tunnel

import java.net.URI
import java.net.URLEncoder
import java.nio.ByteBuffer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * nanobot relay protocol v2.
 *
 * Every peer opens two WebSocket connections to the relay:
 *
 * - `/control` carries small JSON messages: `hello`, `ready`, `presence`, `error`.
 * - `/data` carries binary frames for every stream, multiplexed.
 *
 * Authentication happens inside the first control frame, never in a URL, because reverse proxies
 * log URLs and the pairing token is a credential. The relay hands back a short-lived [Control.sid];
 * the data connection presents it in the [SESSION_HEADER] header.
 *
 * A frame is a 9-byte header plus an opaque payload:
 *
 * ```
 * +--------+--------------------+------------------+
 * | type   | streamId (8B, BE)  | payload          |
 * +--------+--------------------+------------------+
 * ```
 *
 * The relay never decrypts the payload; endpoints share an AES-GCM key from the pairing offer.
 */
object RelayV2 {
    const val VERSION = 2

    const val CONTROL_PATH = "/control"
    const val DATA_PATH = "/data"
    const val SESSION_HEADER = "X-Nanobot-Session"

    const val ROLE_AGENT = "agent"
    const val ROLE_CLIENT = "client"

    const val TYPE_HELLO = "hello"
    const val TYPE_READY = "ready"
    const val TYPE_PRESENCE = "presence"
    const val TYPE_ERROR = "error"

    const val ERROR_AUTH = "auth"
    const val ERROR_QUOTA = "quota"
    const val ERROR_BAD_REQUEST = "bad_request"
    const val ERROR_CONFLICT = "conflict"

    const val FRAME_OPEN: Byte = 0x01
    const val FRAME_DATA: Byte = 0x02
    const val FRAME_CLOSE: Byte = 0x03
    const val FRAME_ACK: Byte = 0x04
    const val FRAME_WINDOW: Byte = 0x05

    const val HEADER_SIZE = 9
    const val MAX_FRAME_SIZE = 256 * 1024

    const val CLOSE_NORMAL: Byte = 0
    const val CLOSE_GATEWAY_UNREACHABLE: Byte = 1
    const val CLOSE_AGENT_OFFLINE: Byte = 2
    const val CLOSE_QUOTA: Byte = 3
    const val CLOSE_PROTOCOL: Byte = 4
    const val CLOSE_RELAY_UNAVAILABLE: Byte = 5

    private val EMPTY = ByteArray(0)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    /** A control-channel message. Every field but `t` is optional. */
    @Serializable
    data class Control(
        val t: String,
        val v: Int? = null,
        val role: String? = null,
        val serverId: String? = null,
        val token: String? = null,
        val key: String? = null,
        val sig: String? = null,
        val sid: String? = null,
        val agent: Boolean? = null,
        val code: String? = null,
        val message: String? = null,
    )

    /** One multiplexed frame. [payload] is empty for OPEN, ACK, and a bare CLOSE. */
    class Frame(val type: Byte, val streamId: Long, val payload: ByteArray)

    fun encodeControl(message: Control): String =
        json.encodeToString(Control.serializer(), message)

    fun decodeControl(text: String): Control? =
        runCatching { json.decodeFromString(Control.serializer(), text) }.getOrNull()

    /** Accepts either a bare relay origin or a full control URL, keeping a mount prefix. */
    fun controlUrl(base: String): String {
        val uri = runCatching { URI(base.trim()) }
            .getOrElse { throw IllegalArgumentException("relay address is not a valid URI") }
        if (uri.scheme != "ws" && uri.scheme != "wss") {
            throw IllegalArgumentException("relay address must start with ws:// or wss://")
        }
        if (uri.host.isNullOrBlank()) {
            throw IllegalArgumentException("relay address has no host")
        }
        val path = uri.path.orEmpty()
        val controlPath = when {
            path.isBlank() || path == "/" -> CONTROL_PATH
            path.endsWith(CONTROL_PATH) -> path
            else -> path.trimEnd('/') + CONTROL_PATH
        }
        return URI(uri.scheme, uri.userInfo, uri.host, uri.port, controlPath, null, null).toString()
    }

    /**
     * Builds the data-channel URL next to the control URL, so a relay mounted at
     * `/nanobot/control` gets `/nanobot/data`. The session id is a header, not a query parameter.
     */
    fun dataUrl(controlUrl: String, serverId: String, role: String): String {
        val uri = URI(controlUrl)
        val path = uri.path.orEmpty()
        val dataPath = if (path.endsWith(CONTROL_PATH)) {
            path.removeSuffix(CONTROL_PATH) + DATA_PATH
        } else {
            DATA_PATH
        }
        val base = URI(uri.scheme, uri.userInfo, uri.host, uri.port, dataPath, null, null).toString()
        val query = listOf("serverId" to serverId, "role" to role).joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
        return "$base?$query"
    }

    fun encodeFrame(type: Byte, streamId: Long, payload: ByteArray = EMPTY): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + payload.size)
        buffer.put(type)
        buffer.putLong(streamId)
        buffer.put(payload)
        return buffer.array()
    }

    /** Returns null for a truncated, oversized, or otherwise unusable frame. */
    fun decodeFrame(bytes: ByteArray): Frame? {
        if (bytes.size < HEADER_SIZE || bytes.size > MAX_FRAME_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes)
        val type = buffer.get()
        val streamId = buffer.long
        val payload = ByteArray(buffer.remaining())
        buffer.get(payload)
        return Frame(type, streamId, payload)
    }

    fun closeFrame(streamId: Long, reason: Byte = CLOSE_NORMAL): ByteArray =
        encodeFrame(FRAME_CLOSE, streamId, byteArrayOf(reason))
}
