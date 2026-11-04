package dev.nanobot.android

import android.net.Uri
import dev.nanobot.android.host.HostProfile
import dev.nanobot.android.tunnel.TunnelState
import dev.nanobot.gateway.GatewayState
import dev.nanobot.protocol.AttachmentReference
import dev.nanobot.protocol.AutomationChatsResponse
import dev.nanobot.protocol.AutomationJob
import dev.nanobot.protocol.CliAppSummary
import dev.nanobot.protocol.GatewaySettings
import dev.nanobot.protocol.McpOAuthFlowPayload
import dev.nanobot.protocol.McpPresetSummary
import dev.nanobot.protocol.MediaRef
import dev.nanobot.protocol.SessionSummary
import dev.nanobot.protocol.SkillDetail
import dev.nanobot.protocol.SkillSummary
import dev.nanobot.protocol.SlashCommand
import dev.nanobot.protocol.WorkspaceScope
import dev.nanobot.protocol.WorkspacesResponse

enum class MessageRole { User, Assistant, System }

enum class AttachmentState { Uploading, Uploaded, Failed }

/**
 * One file the user attached to the draft. The local [uri] is kept so the composer can show an
 * image thumbnail before and after the upload; [reference] only exists once the gateway accepted it.
 */
data class ComposerAttachment(
    val id: String,
    val uri: Uri,
    val name: String,
    val mime: String,
    val sizeBytes: Long = 0L,
    /** `null` while the size is unknown (reading the file or an untracked relay upload). */
    val progress: Float? = null,
    val state: AttachmentState = AttachmentState.Uploading,
    val reference: String? = null,
    val error: String? = null,
) {
    val isImage: Boolean
        get() = mime.startsWith("image/")

    val displayName: String
        get() = name.ifBlank { "attachment" }
}

/**
 * A file shown inside a sent message. [localUri] is the on-device copy (picked or cached);
 * [remoteUrl] is the gateway-relative media URL used to fetch it again after a reload.
 */
data class MessageAttachment(
    val name: String,
    val mime: String = "",
    val localUri: Uri? = null,
    val remoteUrl: String? = null,
) {
    val isImage: Boolean
        get() = mime.startsWith("image") ||
            IMAGE_EXTENSIONS.any { name.lowercase().endsWith(it) }
}

private val IMAGE_EXTENSIONS =
    listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".heic", ".bmp", ".avif")

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val mediaUrls: List<MediaRef> = emptyList(),
    val attachments: List<MessageAttachment> = emptyList(),
    val pending: Boolean = false,
    val failed: Boolean = false,
    val streaming: Boolean = false,
    val reasoning: Boolean = false,
    /** A partial answer the user stopped; it stays visible but accepts no more text. */
    val stopped: Boolean = false,
)

/** Fields for the custom-server form, mirroring the WebUI's `McpCustomServerPanel`. */
data class McpCustomForm(
    val name: String = "",
    val transport: String = "stdio",
    val auth: String = "none",
    val command: String = "",
    val args: String = "",
    val url: String = "",
    val env: String = "",
    val headers: String = "",
    val toolTimeout: String = "30",
    val useProxy: Boolean = false,
) {
    val remote: Boolean
        get() = transport != "stdio"

    val canSave: Boolean
        get() = name.isNotBlank() && (if (remote) url.isNotBlank() else command.isNotBlank())

    /** Query values accepted by `settings.mcp.custom`; blank optional fields are dropped. */
    fun toValues(): Map<String, String> = buildMap {
        put("name", name.trim())
        put("transport", transport)
        if (remote && auth == "oauth") put("auth", "oauth")
        put("command", command)
        put("args", args)
        put("url", url)
        put("env", env)
        if (remote && auth == "headers") put("headers", headers)
        put("tool_timeout", toolTimeout)
        if (remote) put("use_proxy", useProxy.toString())
    }
}

data class UiState(
    val configured: Boolean = false,
    val endpoint: String = "",
    val connection: GatewayState = GatewayState.Disconnected,
    /** Bumped on every `ready`, so image tiles retry a download that failed on the old socket. */
    val mediaEpoch: Int = 0,
    val sessions: List<SessionSummary> = emptyList(),
    /** Chats with a turn this client started that has not finished yet; drives the sidebar spinner. */
    val runningChatIds: Set<String> = emptySet(),
    /** Chats whose turn finished while the user was elsewhere; drives the sidebar green dot. */
    val unreadChatIds: Set<String> = emptySet(),
    val chatId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val streaming: Boolean = false,
    /** A sent turn is still running, so the composer offers pause instead of send. */
    val turnActive: Boolean = false,
    /** Model preset selected for the open chat; null means the gateway's default preset. */
    val modelPreset: String? = null,
    /** Model name the gateway reported for this chat, used when no preset name matches. */
    val modelName: String = "",
    /** Context tokens reported by the last completed turn in the open chat. */
    val contextTokens: Long? = null,
    /** Context window for the active model, from the gateway or the selected preset. */
    val contextWindowTokens: Int = 0,
    val draft: String = "",
    val error: String? = null,
    val loadingSessions: Boolean = false,
    val loadingThread: Boolean = false,
    val attachments: List<ComposerAttachment> = emptyList(),
    val attachedCliApps: Set<String> = emptySet(),
    val attachedMcpPresets: Set<String> = emptySet(),
    val commands: List<SlashCommand> = emptyList(),
    val skills: List<SkillSummary> = emptyList(),
    /** Detail for the skill opened from the settings list; cleared when the page closes. */
    val skillDetail: SkillDetail? = null,
    val skillDetailName: String? = null,
    val skillDetailLoading: Boolean = false,
    val skillDetailError: String? = null,
    val cliApps: List<CliAppSummary> = emptyList(),
    val mcpPresets: List<McpPresetSummary> = emptyList(),
    /** `GET /api/settings/mcp-presets` metadata shown by the MCP pages. */
    val mcpProxyConfigured: Boolean = false,
    val mcpInstalledCount: Int = 0,
    val mcpRequiresRestart: Boolean = false,
    /** `action:name` while an MCP mutation is in flight, matching the WebUI's action key. */
    val mcpAction: String? = null,
    /** Credential drafts keyed by preset name; blank values mean "keep the stored value". */
    val mcpFieldValues: Map<String, Map<String, String>> = emptyMap(),
    /** Browser authorization currently in progress, if any. */
    val mcpOAuthFlow: McpOAuthFlowPayload? = null,
    val mcpOAuthCallbackError: String? = null,
    val automations: List<AutomationJob> = emptyList(),
    /** `id` while an automation edit or chat move is in flight, matching the WebUI's action key. */
    val automationAction: String? = null,
    val automationActionError: String? = null,
    /** Action key that just finished successfully, so its editor can close. */
    val automationActionDone: String? = null,
    /** Chat picker payload for [automationChatsFor]; loaded on demand by the detail page. */
    val automationChatsFor: String? = null,
    val automationChats: AutomationChatsResponse? = null,
    val automationChatsLoading: Boolean = false,
    val automationChatsError: String? = null,
    val loadingFeatures: Boolean = false,
    val hostName: String = "",
    val hostPlatform: String = "",
    val workspaceScope: WorkspaceScope? = null,
    val defaultWorkspaceScope: WorkspaceScope? = null,
    /** Last `GET /api/workspaces` payload: the folders the WebUI picker offers. */
    val workspaces: WorkspacesResponse? = null,
    val searchResults: List<ConversationSearchHit> = emptyList(),
    val searching: Boolean = false,
    /** Set by a search result so the transcript scrolls to that bubble once it is loaded. */
    val pendingSearchTarget: SearchScrollTarget? = null,
    val titleOverrides: Map<String, String> = emptyMap(),
    val projectNameOverrides: Map<String, String> = emptyMap(),
    /** Sidebar keys (`websocket:<chat_id>`) that the WebUI document marks as pinned. */
    val pinnedKeys: Set<String> = emptySet(),
    /** Sidebar keys hidden from the default list until "show archived" is on. */
    val archivedKeys: Set<String> = emptySet(),
    val showArchived: Boolean = false,
    val collapsedGroups: Set<String> = emptySet(),
    val hosts: List<HostProfile> = emptyList(),
    val currentHostId: String? = null,
    val tunnelState: TunnelState? = null,
    val pairingError: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val accentTheme: AccentTheme = AccentTheme.Mist,
    val customAccentHue: Float = DEFAULT_CUSTOM_HUE,
    val language: AppLanguage = AppLanguage.English,
    val fontSize: AppFontSize = AppFontSize.Standard,
    val turnNotifications: Boolean = true,
    val notificationSound: Boolean = false,
    /** `GET /api/settings` summary shown on the settings pages. */
    val gatewaySettings: GatewaySettings? = null,
    val settingsLoading: Boolean = false,
    /** True while a model or gateway settings mutation is in flight. */
    val settingsSaving: Boolean = false,
    val settingsError: String? = null,
    /** Inline feedback for settings-page actions such as skill toggles or automation runs. */
    val settingsMessage: String? = null,
    /** Latest-release check shown on the About page. */
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
) {
    val currentHost: HostProfile?
        get() = hosts.firstOrNull { it.id == currentHostId }

    /** References the gateway has accepted; only these may be sent with the message. */
    val uploadedAttachments: List<AttachmentReference>
        get() = attachments.mapNotNull { attachment ->
            attachment.reference?.let { AttachmentReference(it, attachment.name) }
        }

    val attachmentsUploading: Boolean
        get() = attachments.any { it.state == AttachmentState.Uploading }

    val canSend: Boolean
        get() = connection is GatewayState.Ready && chatId != null && !attachmentsUploading &&
            (draft.isNotBlank() || uploadedAttachments.isNotEmpty())
}
