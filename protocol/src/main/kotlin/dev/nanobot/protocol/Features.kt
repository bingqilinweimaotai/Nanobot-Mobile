package dev.nanobot.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

@Serializable
data class SlashCommand(
    val command: String,
    val title: String = "",
    val description: String = "",
    val icon: String = "",
    @SerialName("arg_hint") val argHint: String = "",
    val lifecycle: String = "side_channel",
    @SerialName("accepts_args") val acceptsArgs: Boolean = false,
)

@Serializable
data class CommandsResponse(
    val commands: List<SlashCommand> = emptyList(),
)

@Serializable
data class SkillSummary(
    val name: String,
    val description: String = "",
    val source: String = "",
    val enabled: Boolean = true,
    val deletable: Boolean = false,
    val available: Boolean = true,
    @SerialName("unavailable_reason") val unavailableReason: String? = null,
)

@Serializable
data class SkillsResponse(
    val skills: List<SkillSummary> = emptyList(),
)

/** Command and environment requirements reported for one skill. */
@Serializable
data class SkillRequirements(
    val bins: List<String> = emptyList(),
    val env: List<String> = emptyList(),
    @SerialName("missing_bins") val missingBins: List<String> = emptyList(),
    @SerialName("missing_env") val missingEnv: List<String> = emptyList(),
) {
    val hasMissing: Boolean
        get() = missingBins.isNotEmpty() || missingEnv.isNotEmpty()
}

/** One install command the WebUI offers when a skill's requirements are missing. */
@Serializable
data class SkillInstallOption(
    val id: String = "",
    val kind: String = "",
    val label: String = "",
    val command: String = "",
)

/** `GET /api/webui/skills/{name}`: the summary plus requirements and the raw `SKILL.md`. */
@Serializable
data class SkillDetail(
    val name: String,
    val description: String = "",
    val source: String = "",
    val enabled: Boolean = true,
    val deletable: Boolean = false,
    val available: Boolean = true,
    @SerialName("unavailable_reason") val unavailableReason: String? = null,
    val requirements: SkillRequirements = SkillRequirements(),
    @SerialName("install_options") val installOptions: List<SkillInstallOption> = emptyList(),
    @SerialName("raw_markdown") val rawMarkdown: String = "",
)

@Serializable
data class CliAppSummary(
    val name: String,
    @SerialName("display_name") val displayName: String = "",
    val category: String = "",
    val description: String = "",
    val installed: Boolean = false,
    val available: Boolean = false,
    val status: String = "",
)

@Serializable
data class CliAppsResponse(
    val apps: List<CliAppSummary> = emptyList(),
)

/** One credential or connection field a preset asks for before it can connect. */
@Serializable
data class McpPresetField(
    val name: String,
    val label: String = "",
    val secret: Boolean = false,
    val required: Boolean = false,
    val configured: Boolean = false,
    val placeholder: String = "",
    @SerialName("env_var") val envVar: String? = null,
)

/**
 * One row of `GET /api/settings/mcp-presets`. The WebUI renders the same fields in its MCP
 * management dialog: status, transport, credentials, proxy, tool scope, and runtime state.
 */
@Serializable
data class McpPresetSummary(
    val name: String,
    @SerialName("display_name") val displayName: String = "",
    val category: String = "",
    val description: String = "",
    @SerialName("docs_url") val docsUrl: String = "",
    val transport: String = "",
    val auth: String? = null,
    @SerialName("use_proxy") val useProxy: Boolean = false,
    val requires: String = "",
    val note: String = "",
    @SerialName("install_supported") val installSupported: Boolean = false,
    val installed: Boolean = false,
    val configured: Boolean = false,
    /** Present only for agent-plugin rows; MCP servers are enabled by being installed. */
    val enabled: Boolean? = null,
    val available: Boolean = false,
    val status: String = "",
    @SerialName("runtime_status") val runtimeStatus: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("brand_color") val brandColor: String? = null,
    @SerialName("required_fields") val requiredFields: List<McpPresetField> = emptyList(),
    @SerialName("connection_summary") val connectionSummary: String = "",
    @SerialName("tool_count") val toolCount: Int? = null,
    @SerialName("tool_names") val toolNames: List<String> = emptyList(),
    @SerialName("checked_at") val checkedAt: String? = null,
    val error: String? = null,
    @SerialName("enabled_tools") val enabledTools: List<String> = listOf("*"),
    val source: String = "preset",
) {
    val isPlugin: Boolean
        get() = source == "agent-plugin" || enabled != null

    val remote: Boolean
        get() = transport != "stdio"

    /** A preset that should be offered a connection form rather than a one-tap enable. */
    val needsConnectionForm: Boolean
        get() = remote || requiredFields.any { it.required && !it.configured } ||
            (installed && !configured)
}

/** Result of every `settings.mcp.*` mutation, matching the WebUI's payload. */
@Serializable
data class McpPresetsResponse(
    val presets: List<McpPresetSummary> = emptyList(),
    @SerialName("proxy_configured") val proxyConfigured: Boolean = false,
    @SerialName("installed_count") val installedCount: Int = 0,
    @SerialName("requires_restart") val requiresRestart: Boolean = false,
    @SerialName("hot_reload") val hotReload: McpHotReload? = null,
    @SerialName("last_action") val lastAction: McpActionFeedback? = null,
)

@Serializable
data class McpHotReload(
    val ok: Boolean = false,
    val message: String = "",
    @SerialName("requires_restart") val requiresRestart: Boolean = false,
)

@Serializable
data class McpActionFeedback(
    val ok: Boolean = false,
    val message: String = "",
    val error: String? = null,
    @SerialName("tool_count") val toolCount: Int? = null,
    @SerialName("tool_names") val toolNames: List<String> = emptyList(),
    @SerialName("checked_at") val checkedAt: String? = null,
)

/** Response of `settings.mcp.oauth_start` and the OAuth status endpoint. */
@Serializable
data class McpOAuthFlowPayload(
    @SerialName("flow_id") val flowId: String,
    val name: String = "",
    val status: String = "",
    @SerialName("expires_in") val expiresIn: Int = 0,
    @SerialName("authorization_url") val authorizationUrl: String? = null,
    @SerialName("completion_input") val completionInput: String? = null,
    val error: String? = null,
    @SerialName("hot_reload") val hotReload: McpHotReload? = null,
)

/** One scheduled automation or local trigger, as `GET /api/webui/automations` reports it. */
@Serializable
data class AutomationJob(
    val id: String,
    val name: String = "",
    val enabled: Boolean = false,
    /** System-managed jobs are read-only in the WebUI's automations view. */
    val protected: Boolean = false,
    val kind: String = "",
    @SerialName("delete_after_run") val deleteAfterRun: Boolean = false,
    @SerialName("created_at_ms") val createdAtMs: Long? = null,
    @SerialName("updated_at_ms") val updatedAtMs: Long? = null,
    val schedule: AutomationSchedule = AutomationSchedule(),
    val payload: AutomationPayload = AutomationPayload(),
    val state: AutomationState = AutomationState(),
    val origin: AutomationOrigin? = null,
    val trigger: AutomationTrigger? = null,
    @SerialName("chat_binding_revision") val chatBindingRevision: String? = null,
) {
    /** The WebUI treats local triggers as their own kind even when the schedule says `local`. */
    val isLocalTrigger: Boolean
        get() = kind == "local_trigger" || payload.kind == "local_trigger" || schedule.kind == "local"
}

@Serializable
data class AutomationSchedule(
    val kind: String = "",
    @SerialName("at_ms") val atMs: Long? = null,
    @SerialName("every_ms") val everyMs: Long? = null,
    val expr: String? = null,
    val tz: String? = null,
)

@Serializable
data class AutomationPayload(
    val message: String = "",
    val kind: String = "",
    val command: String? = null,
)

@Serializable
data class AutomationState(
    @SerialName("next_run_at_ms") val nextRunAtMs: Long? = null,
    @SerialName("last_run_at_ms") val lastRunAtMs: Long? = null,
    @SerialName("last_status") val lastStatus: String? = null,
    @SerialName("last_error") val lastError: String? = null,
    val pending: Boolean = false,
    @SerialName("run_history") val runHistory: List<AutomationRun> = emptyList(),
)

@Serializable
data class AutomationRun(
    @SerialName("run_at_ms") val runAtMs: Long = 0,
    val status: String = "",
    @SerialName("duration_ms") val durationMs: Long? = null,
    val error: String? = null,
    @SerialName("webui_session_key") val webuiSessionKey: String? = null,
)

@Serializable
data class AutomationOrigin(
    @SerialName("session_key") val sessionKey: String? = null,
    val channel: String = "",
    @SerialName("chat_id") val chatId: String? = null,
    val title: String = "",
    val preview: String = "",
)

@Serializable
data class AutomationTrigger(
    val id: String = "",
    val command: String = "",
)

@Serializable
data class AutomationsResponse(
    val jobs: List<AutomationJob> = emptyList(),
)

/** One chat a scheduled automation can be moved to, from `GET /api/webui/automations/chats`. */
@Serializable
data class AutomationChat(
    val id: String,
    val title: String = "",
    val channel: String = "",
    /** False when the channel is no longer running; the WebUI hides those targets. */
    val unavailable: Boolean = false,
)

/**
 * The WebUI's chat picker payload. [revision] must travel back with a change so a scheduler edit
 * made in another client is not silently overwritten.
 */
@Serializable
data class AutomationChatsResponse(
    val revision: String = "",
    val current: AutomationChat? = null,
    val chats: List<AutomationChat> = emptyList(),
)

/** The partial `values` object of `automation.update`; null members are omitted on the wire. */
@Serializable
data class AutomationUpdate(
    val name: String? = null,
    val message: String? = null,
    val schedule: AutomationScheduleUpdate? = null,
)

@Serializable
data class AutomationScheduleUpdate(
    val kind: String,
    @SerialName("at_ms") val atMs: Long? = null,
    @SerialName("every_ms") val everyMs: Long? = null,
    val expr: String? = null,
    val tz: String? = null,
)

@Serializable
data class AttachmentReference(
    val reference: String,
    val name: String? = null,
)

@Serializable
data class AttachmentUploadResponse(
    val reference: String,
)

@Serializable
data class WorkspaceHost(
    val name: String = "",
    val platform: String = "",
)

@Serializable
data class WorkspaceScope(
    @SerialName("project_path") val projectPath: String = "",
    @SerialName("project_name") val projectName: String? = null,
    @SerialName("access_mode") val accessMode: String = "restricted",
    @SerialName("restrict_to_workspace") val restrictToWorkspace: Boolean? = null,
)

/** One selectable folder in the WebUI's workspace picker. */
@Serializable
data class WorkspaceProject(
    val name: String = "",
    val path: String = "",
)

@Serializable
data class WorkspaceControls(
    @SerialName("can_change_project") val canChangeProject: Boolean = false,
    @SerialName("can_browse_directories") val canBrowseDirectories: Boolean = false,
    @SerialName("can_resolve_project") val canResolveProject: Boolean = false,
    @SerialName("can_manage_favorites") val canManageFavorites: Boolean = false,
)

@Serializable
data class WorkspacesResponse(
    val host: WorkspaceHost? = null,
    @SerialName("default_scope") val defaultScope: WorkspaceScope? = null,
    @SerialName("default_access_mode") val defaultAccessMode: String? = null,
    @SerialName("favorite_projects") val favoriteProjects: List<WorkspaceProject> = emptyList(),
    @SerialName("recent_projects") val recentProjects: List<WorkspaceProject> = emptyList(),
    val controls: WorkspaceControls = WorkspaceControls(),
)

/** Response of `GET /api/workspaces/directories`. */
@Serializable
data class WorkspaceDirectoriesResponse(
    val path: String = "",
    val parent: String? = null,
    val entries: List<WorkspaceProject> = emptyList(),
    val partial: Boolean = false,
    val truncated: Boolean = false,
    val host: String = "",
    val platform: String = "",
)

@Serializable
data class SidebarState(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("pinned_keys") val pinnedKeys: List<String> = emptyList(),
    @SerialName("archived_keys") val archivedKeys: List<String> = emptyList(),
    @SerialName("session_order") val sessionOrder: List<String> = emptyList(),
    @SerialName("title_overrides") val titleOverrides: Map<String, String> = emptyMap(),
    @SerialName("project_name_overrides") val projectNameOverrides: Map<String, String> = emptyMap(),
    @SerialName("tags_by_key") val tagsByKey: Map<String, List<String>> = emptyMap(),
    @SerialName("collapsed_groups") val collapsedGroups: Map<String, Boolean> = emptyMap(),
    val view: SidebarViewState = SidebarViewState(),
    @SerialName("updated_at") val updatedAt: String? = null,
    /**
     * The exact JSON this state was decoded from. Saving replaces the whole sidebar document on the
     * gateway, so unknown fields (the WebUI workbench, tags, future additions) must survive a
     * round-trip through this client.
     */
    @Transient val raw: JsonObject? = null,
) {
    companion object {
        fun fromJson(root: JsonObject): SidebarState =
            NanobotJson.decodeFromJsonElement(serializer(), root).copy(raw = root)
    }
}

@Serializable
data class SidebarViewState(
    val density: String = "comfortable",
    @SerialName("show_archived") val showArchived: Boolean = false,
    val sort: String = "updated_desc",
)

@Serializable
data class SessionDeleteResult(
    val deleted: Boolean = false,
    @SerialName("blocked_by_automations") val blockedByAutomations: Boolean = false,
    val automations: List<SessionAutomationSummary> = emptyList(),
)

@Serializable
data class SessionAutomationSummary(
    val id: String = "",
    val name: String = "",
)

/**
 * The subset of `GET /api/settings` the Android settings page shows. The WebUI payload is much
 * larger (providers, presets, capability forms); unknown fields are ignored.
 */
@Serializable
data class GatewaySettings(
    val agent: GatewayAgentSettings = GatewayAgentSettings(),
    val runtime: GatewayRuntimeSettings = GatewayRuntimeSettings(),
    val web: GatewayWebSettings = GatewayWebSettings(),
    @SerialName("image_generation") val imageGeneration: GatewayImageSettings =
        GatewayImageSettings(),
    val transcription: GatewayTranscriptionSettings = GatewayTranscriptionSettings(),
    /** Switchable model presets; the WebUI's composer builds its model picker from these. */
    @SerialName("model_presets") val modelPresets: List<GatewayModelPreset> = emptyList(),
    /** Preferred order for [modelPresets]; names missing here keep their payload order. */
    @SerialName("model_call_order") val modelCallOrder: List<String> = emptyList(),
    /** Whether the WebUI (and this client) may change [modelCallOrder]. */
    @SerialName("model_call_order_editable") val modelCallOrderEditable: Boolean = false,
    /** Provider families the gateway knows about; used to pick a provider for a model preset. */
    val providers: List<GatewayProviderSettings> = emptyList(),
    val version: GatewayVersionInfo? = null,
    @SerialName("requires_restart") val requiresRestart: Boolean = false,
)

@Serializable
data class GatewayAgentSettings(
    val model: String = "",
    val provider: String = "",
    @SerialName("resolved_provider") val resolvedProvider: String? = null,
    @SerialName("model_preset") val modelPreset: String? = null,
    @SerialName("max_tokens") val maxTokens: Int = 0,
    @SerialName("context_window_tokens") val contextWindowTokens: Int = 0,
    val temperature: Double = 0.0,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    val timezone: String = "",
)

@Serializable
data class GatewayModelPreset(
    val name: String = "",
    /** Compatibility alias kept on the wire; [name] is authoritative. */
    val label: String? = null,
    val active: Boolean = false,
    @SerialName("is_default") val isDefault: Boolean = false,
    val model: String = "",
    val provider: String = "",
    @SerialName("resolved_provider") val resolvedProvider: String? = null,
    @SerialName("max_tokens") val maxTokens: Int = 0,
    @SerialName("context_window_tokens") val contextWindowTokens: Int = 0,
    val temperature: Double = 0.0,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    /** Values the gateway accepts for [reasoningEffort] for this preset's provider and model. */
    @SerialName("reasoning_effort_values") val reasoningEffortValues: List<String> = emptyList(),
)

/** One row of the gateway's provider catalog, used by the model-preset editor. */
@Serializable
data class GatewayProviderSettings(
    val name: String = "",
    val label: String = "",
    val configured: Boolean = false,
)

@Serializable
data class GatewayRuntimeSettings(
    @SerialName("config_path") val configPath: String = "",
    @SerialName("workspace_path") val workspacePath: String = "",
    @SerialName("gateway_host") val gatewayHost: String = "",
    @SerialName("gateway_port") val gatewayPort: Int = 0,
    @SerialName("unified_session") val unifiedSession: Boolean = false,
)

@Serializable
data class GatewayVersionInfo(
    val current: String = "",
    val commit: String? = null,
)

@Serializable
data class GatewayWebSettings(
    val enable: Boolean = false,
)

@Serializable
data class GatewayImageSettings(
    val enabled: Boolean = false,
    val provider: String = "",
    val model: String = "",
)

@Serializable
data class GatewayTranscriptionSettings(
    val enabled: Boolean = false,
    val provider: String = "",
    val model: String = "",
)
