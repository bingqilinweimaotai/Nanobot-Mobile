package dev.nanobot.protocol

import kotlinx.serialization.json.JsonObject

/** Server -> client WebSocket events, modelled only as far as the Android client needs. */
sealed interface InboundEvent {
    data class Ready(
        val chatId: String,
        val clientId: String,
        val upload: UploadCapability?,
        val capabilities: Set<String>,
    ) : InboundEvent

    data class Attached(val chatId: String) : InboundEvent

    data class MessageAccepted(val chatId: String, val turnId: String) : InboundEvent

    data class UserMessage(
        val chatId: String,
        val text: String,
        val turnId: String?,
        val mediaUrls: List<MediaRef>,
        val createdAtMs: Long?,
        val startsTurn: Boolean,
    ) : InboundEvent

    data class Message(
        val chatId: String,
        val text: String,
        val turnId: String?,
        val mediaUrls: List<MediaRef>,
        val kind: String?,
        val latencyMs: Long?,
        val createdAtMs: Long?,
    ) : InboundEvent

    data class Delta(
        val chatId: String,
        val text: String,
        val streamId: String?,
    ) : InboundEvent

    data class StreamEnd(
        val chatId: String,
        val streamId: String?,
        val text: String?,
        val resuming: Boolean,
    ) : InboundEvent

    data class ReasoningDelta(
        val chatId: String,
        val text: String,
        val streamId: String?,
    ) : InboundEvent

    data class ReasoningEnd(
        val chatId: String,
        val streamId: String?,
        val text: String?,
    ) : InboundEvent

    data class TurnEnd(
        val chatId: String,
        val turnId: String?,
        val usage: Usage?,
        val latencyMs: Long?,
        val outcome: String?,
        /** Context window the gateway used for this turn, when it advertises one. */
        val contextWindowTokens: Int = 0,
    ) : InboundEvent

    /**
     * The runtime picked for a chat's turn (including after `/model <preset>`). The composer
     * uses it to keep its model capsule and context ring in sync with the gateway.
     */
    data class TurnModelUpdated(
        val chatId: String,
        val modelName: String,
        val modelPreset: String?,
        val contextWindowTokens: Int = 0,
    ) : InboundEvent

    /**
     * The gateway's default runtime changed, usually after a WebUI model-config edit. Unlike
     * [TurnModelUpdated] this is not chat-scoped: every client should refresh its model catalog.
     */
    data class RuntimeModelUpdated(
        val modelName: String,
        val modelPreset: String?,
    ) : InboundEvent

    data class SessionUpdated(
        val chatId: String,
        val scope: String?,
    ) : InboundEvent

    /** The gateway's saved sidebar document, broadcast after any WebUI pin/archive change. */
    data class SidebarStateUpdated(
        val state: JsonObject?,
    ) : InboundEvent

    data class ErrorEvent(
        val chatId: String?,
        val turnId: String?,
        val detail: String?,
        val reason: String?,
    ) : InboundEvent

    data class WebUiResponse(
        val requestId: String,
        val ok: Boolean,
        val result: JsonObject?,
        val error: WebUiError?,
    ) : InboundEvent

    /** Events this client does not model yet; kept so callers can log or inspect them. */
    data class Unknown(
        val event: String?,
        val raw: JsonObject,
    ) : InboundEvent
}

object InboundEvents {
    fun decode(text: String): InboundEvent {
        val element = runCatching { NanobotJson.parseToJsonElement(text) }.getOrNull()
        return if (element is JsonObject) decode(element) else InboundEvent.Unknown(null, JsonObject(emptyMap()))
    }

    fun decode(obj: JsonObject): InboundEvent {
        return when (val event = obj.textOrNull("event")) {
            "ready" -> InboundEvent.Ready(
                chatId = obj.textOrNull("chat_id").orEmpty(),
                clientId = obj.textOrNull("client_id").orEmpty(),
                upload = obj.objectOrNull("upload")?.let { upload ->
                    val path = upload.textOrNull("path")
                    val token = upload.textOrNull("token")
                    if (path != null && token != null) UploadCapability(path, token) else null
                },
                capabilities = obj.objectOrNull("terminal")
                    ?.objectOrNull("webui")
                    ?.arrayOrNull("capabilities")
                    ?.strings()
                    ?.toSet()
                    .orEmpty(),
            )

            "attached" -> InboundEvent.Attached(obj.textOrNull("chat_id").orEmpty())

            "message_accepted" -> InboundEvent.MessageAccepted(
                chatId = obj.textOrNull("chat_id").orEmpty(),
                turnId = obj.textOrNull("turn_id").orEmpty(),
            )

            "user_message" -> InboundEvent.UserMessage(
                chatId = obj.textOrNull("chat_id").orEmpty(),
                text = obj.textOrNull("text").orEmpty(),
                turnId = obj.textOrNull("turn_id"),
                mediaUrls = obj.mediaUrls(),
                createdAtMs = obj.longOrNull("created_at_ms"),
                startsTurn = obj.booleanOrNull("starts_turn") ?: false,
            )

            "message" -> InboundEvent.Message(
                chatId = obj.textOrNull("chat_id").orEmpty(),
                text = obj.textOrNull("text").orEmpty(),
                turnId = obj.textOrNull("turn_id"),
                mediaUrls = obj.mediaUrls(),
                kind = obj.textOrNull("kind"),
                latencyMs = obj.longOrNull("latency_ms"),
                createdAtMs = obj.longOrNull("created_at_ms"),
            )

            "delta" -> InboundEvent.Delta(
                chatId = obj.textOrNull("chat_id").orEmpty(),
                text = obj.textOrNull("text").orEmpty(),
                streamId = obj.textOrNull("stream_id"),
            )

            "stream_end" -> InboundEvent.StreamEnd(
                chatId = obj.textOrNull("chat_id").orEmpty(),
                streamId = obj.textOrNull("stream_id"),
                text = obj.textOrNull("text"),
                resuming = obj.booleanOrNull("resuming") ?: false,
            )

            "reasoning_delta" -> InboundEvent.ReasoningDelta(
                chatId = obj.textOrNull("chat_id").orEmpty(),
                text = obj.textOrNull("text").orEmpty(),
                streamId = obj.textOrNull("stream_id"),
            )

            "reasoning_end" -> InboundEvent.ReasoningEnd(
                chatId = obj.textOrNull("chat_id").orEmpty(),
                streamId = obj.textOrNull("stream_id"),
                text = obj.textOrNull("text"),
            )

            "turn_end" -> InboundEvent.TurnEnd(
                chatId = obj.textOrNull("chat_id").orEmpty(),
                turnId = obj.textOrNull("turn_id"),
                usage = obj.objectOrNull("usage")?.let { usage ->
                    runCatching { NanobotJson.decodeFromJsonElement(Usage.serializer(), usage) }.getOrNull()
                },
                latencyMs = obj.longOrNull("latency_ms"),
                outcome = obj.textOrNull("outcome"),
                contextWindowTokens = obj.intOrNull("context_window_tokens") ?: 0,
            )

            "turn_model_updated" -> InboundEvent.TurnModelUpdated(
                chatId = obj.textOrNull("chat_id").orEmpty(),
                modelName = obj.textOrNull("model_name").orEmpty(),
                modelPreset = obj.textOrNull("model_preset"),
                contextWindowTokens = obj.intOrNull("context_window_tokens") ?: 0,
            )

            "runtime_model_updated" -> InboundEvent.RuntimeModelUpdated(
                modelName = obj.textOrNull("model_name").orEmpty(),
                modelPreset = obj.textOrNull("model_preset"),
            )

            "session_updated" -> InboundEvent.SessionUpdated(
                chatId = obj.textOrNull("chat_id").orEmpty(),
                scope = obj.textOrNull("scope"),
            )

            "sidebar_state_updated" -> InboundEvent.SidebarStateUpdated(
                state = obj.objectOrNull("state"),
            )

            "error" -> InboundEvent.ErrorEvent(
                chatId = obj.textOrNull("chat_id"),
                turnId = obj.textOrNull("turn_id"),
                detail = obj.textOrNull("detail"),
                reason = obj.textOrNull("reason"),
            )

            "webui_response" -> InboundEvent.WebUiResponse(
                requestId = obj.textOrNull("request_id").orEmpty(),
                ok = obj.booleanOrNull("ok") ?: false,
                result = obj.objectOrNull("result"),
                error = obj.objectOrNull("error")?.let { error ->
                    WebUiError(
                        status = error.intOrNull("status"),
                        message = error.textOrNull("message"),
                    )
                },
            )

            else -> InboundEvent.Unknown(event, obj)
        }
    }
}

private fun JsonObject.mediaUrls(): List<MediaRef> =
    arrayOrNull("media_urls")?.mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        val url = row.textOrNull("url") ?: return@mapNotNull null
        MediaRef(url = url, name = row.textOrNull("name"), kind = row.textOrNull("kind"))
    }.orEmpty()
