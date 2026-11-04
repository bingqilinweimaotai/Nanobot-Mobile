package dev.nanobot.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of `GET /webui/bootstrap`. */
@Serializable
data class BootstrapResponse(
    val token: String = "",
    @SerialName("api_token") val apiToken: String? = null,
    @SerialName("ws_path") val wsPath: String = "/",
    @SerialName("ws_url") val wsUrl: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("model_name") val modelName: String? = null,
    val terminal: TerminalInfo? = null,
    val limits: BootstrapLimits? = null,
)

/** Business limits the gateway advertises at bootstrap; older gateways omit the object. */
@Serializable
data class BootstrapLimits(
    val attachments: AttachmentLimits = AttachmentLimits(),
)

@Serializable
data class AttachmentLimits(
    @SerialName("max_count") val maxCount: Int = 4,
    @SerialName("max_file_bytes") val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    @SerialName("max_total_bytes") val maxTotalBytes: Long = 24L * 1024 * 1024,
) {
    companion object {
        /** The gateway's own default when it does not advertise an attachment budget. */
        const val DEFAULT_MAX_FILE_BYTES = 6L * 1024 * 1024
    }
}

@Serializable
data class TerminalInfo(
    val protocolVersion: Int? = null,
    val gatewayId: String? = null,
    val webui: WebUiContract? = null,
)

@Serializable
data class WebUiContract(
    val version: String? = null,
    @SerialName("min_protocol") val minProtocol: Int? = null,
    @SerialName("max_protocol") val maxProtocol: Int? = null,
    val capabilities: List<String> = emptyList(),
)

@Serializable
data class UploadCapability(
    val path: String,
    val token: String,
)

/** One row of `GET /api/sessions`. */
@Serializable
data class SessionSummary(
    val key: String,
    val title: String = "",
    val preview: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("model_preset") val modelPreset: String? = null,
    @SerialName("run_started_at") val runStartedAt: Long? = null,
    val handle: SessionHandle? = null,
    @SerialName("workspace_scope") val workspaceScope: WorkspaceScope? = null,
) {
    /** `websocket:<chat_id>` -> `<chat_id>`; empty for other channels. */
    val chatId: String
        get() = if (key.startsWith("websocket:")) key.removePrefix("websocket:") else ""
}

@Serializable
data class SessionHandle(
    val id: String,
    val name: String,
)

@Serializable
data class SessionsResponse(
    val sessions: List<SessionSummary> = emptyList(),
)

/** A page of persisted transcript events, already decoded into wire events. */
data class ThreadPage(
    val events: List<InboundEvent>,
    val hasMoreBefore: Boolean = false,
    val beforeCursor: String? = null,
    val raw: String? = null,
    /** Turn still running on the gateway, even when this transcript has no live frames yet. */
    val activeTurnId: String? = null,
    /** A tool call is in flight, so the turn is running even if `active_turn_id` is absent. */
    val hasPendingToolCalls: Boolean = false,
)

data class MediaRef(
    val url: String,
    val name: String? = null,
    val kind: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Long? = null,
    @SerialName("completion_tokens") val completionTokens: Long? = null,
    @SerialName("total_tokens") val totalTokens: Long? = null,
    @SerialName("context_tokens") val contextTokens: Long? = null,
)

data class WebUiError(
    val status: Int? = null,
    val message: String? = null,
)
