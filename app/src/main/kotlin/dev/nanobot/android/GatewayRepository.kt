package dev.nanobot.android

import android.app.Application
import dev.nanobot.android.host.HostKind
import dev.nanobot.android.host.HostProfile
import dev.nanobot.android.host.HostStore
import dev.nanobot.android.tunnel.RelayConfig
import dev.nanobot.android.tunnel.RelayTunnel
import dev.nanobot.android.tunnel.TunnelState
import dev.nanobot.gateway.GatewayException
import dev.nanobot.gateway.GatewayState
import dev.nanobot.protocol.AttachmentReference
import dev.nanobot.protocol.AutomationChatsResponse
import dev.nanobot.protocol.AutomationJob
import dev.nanobot.protocol.AutomationUpdate
import dev.nanobot.protocol.CliAppSummary
import dev.nanobot.protocol.GatewaySettings
import dev.nanobot.protocol.InboundEvent
import dev.nanobot.protocol.McpOAuthFlowPayload
import dev.nanobot.protocol.McpPresetsResponse
import dev.nanobot.protocol.SessionDeleteResult
import dev.nanobot.protocol.SessionSummary
import dev.nanobot.protocol.SkillDetail
import dev.nanobot.protocol.SkillSummary
import dev.nanobot.protocol.SlashCommand
import dev.nanobot.protocol.ThreadPage
import dev.nanobot.protocol.SidebarState
import dev.nanobot.protocol.WorkspaceDirectoriesResponse
import dev.nanobot.protocol.WorkspaceProject
import dev.nanobot.protocol.WorkspaceScope
import dev.nanobot.protocol.WorkspacesResponse
import kotlinx.serialization.json.JsonObject
import dev.nanobot.tunnel.PairingOffer
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NanobotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GatewayRepository.init(this)
        GatewayRepository.connectConfigured()
    }
}

/**
 * Process-wide gateway owner. The foreground service and every screen share one connection, so a
 * turn keeps streaming while the UI is backgrounded.
 */
object GatewayRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<GatewayState>(GatewayState.Disconnected)
    val state: StateFlow<GatewayState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<InboundEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<InboundEvent> = _events.asSharedFlow()

    private val _tunnelState = MutableStateFlow<TunnelState?>(null)
    val tunnelState: StateFlow<TunnelState?> = _tunnelState.asStateFlow()

    @Volatile
    private var context: android.content.Context? = null

    @Volatile
    private var settings: SettingsStore? = null

    @Volatile
    private var hostStore: HostStore? = null

    @Volatile
    private var gateway: dev.nanobot.gateway.NanobotGateway? = null

    @Volatile
    private var tunnel: RelayTunnel? = null

    /** Host id of the gateway currently running in [gateway], not the persisted selection. */
    @Volatile
    private var connectedHostId: String? = null

    private var eventJob: Job? = null
    private var stateJob: Job? = null
    private var tunnelJob: Job? = null

    /** Refuse absurd inputs before decoding them; the gateway's total budget is far smaller. */
    private const val MAX_ATTACHMENT_INPUT_BYTES = 64L * 1024 * 1024

    fun init(context: android.content.Context) {
        if (this.context == null) {
            this.context = context.applicationContext
            settings = SettingsStore(context.applicationContext)
            hostStore = HostStore(context.applicationContext)
        }
    }

    val configured: Boolean
        get() = currentHost() != null

    /** The address shown in settings: the direct endpoint or the relay control URL. */
    val endpoint: String
        get() = currentHost()?.let {
            if (it.kind == HostKind.Relay) it.relayUrl else it.endpoint
        }.orEmpty()

    fun hosts(): List<HostProfile> = hostStore?.hosts().orEmpty()

    fun currentHost(): HostProfile? = hostStore?.currentHost()

    /**
     * Byte progress is meaningful only on a direct connection. A relay upload is written to the
     * loopback tunnel, whose kernel buffers absorb the file long before it crosses the relay, so a
     * percentage there would be invented. The composer shows an indeterminate state instead.
     */
    fun uploadProgressIsReal(): Boolean = currentHost()?.kind != HostKind.Relay

    fun currentHostId(): String? = hostStore?.currentId()

    var lastChatId: String?
        get() = currentHost()?.lastChatId
        set(value) {
            val id = currentHost()?.id ?: return
            hostStore?.updateLastChat(id, value)
        }

    var collapsedGroups: Set<String>
        get() = settings?.collapsedGroups ?: emptySet()
        set(value) {
            settings?.collapsedGroups = value
        }

    val chatId: String?
        get() = gateway?.chatId

    fun connectConfigured() {
        currentHost()?.let(::connectHost)
    }

    @Synchronized
    fun connectHost(host: HostProfile) {
        val store = hostStore ?: return
        if (gateway != null && host.id == connectedHostId) {
            gateway?.reconnect()
            return
        }

        teardown()
        store.setCurrent(host.id)
        connectedHostId = host.id
        val secret = store.gatewaySecret(host.id)
        when (host.kind) {
            HostKind.Direct -> {
                if (host.endpoint.isBlank()) {
                    _state.value = GatewayState.Failed("gateway address is missing")
                    return
                }
                _tunnelState.value = null
                startGateway(host.endpoint, secret, host)
            }

            HostKind.Relay -> {
                val pairKey = store.pairKey(host.id)
                if (pairKey.isBlank() || host.relayUrl.isBlank() ||
                    host.serverId.isBlank() || host.clientToken.isBlank()
                ) {
                    _state.value = GatewayState.Failed("pairing data is incomplete")
                    return
                }
                val relay = runCatching {
                    RelayTunnel(
                        RelayConfig(
                            controlUrl = host.relayUrl,
                            serverId = host.serverId,
                            clientToken = host.clientToken,
                            pairKey = pairKey,
                        ),
                    )
                }.getOrElse {
                    _state.value = GatewayState.Failed(it.message ?: "invalid pairing key")
                    return
                }
                tunnel = relay
                _tunnelState.value = TunnelState.Connecting
                runCatching { relay.start() }.onFailure { error ->
                    tunnel = null
                    _tunnelState.value = TunnelState.Failed(
                        error.message ?: "could not open the local tunnel",
                    )
                    _state.value = GatewayState.Failed(
                        error.message ?: "could not open the local tunnel",
                    )
                    return
                }
                tunnelJob = scope.launch {
                    relay.state.collect { _tunnelState.value = it }
                }
                startGateway(relay.baseUrl, secret, host)
            }
        }
    }

    private fun startGateway(baseUrl: String, secret: String, host: HostProfile) {
        android.util.Log.d(
            "NanobotGateway",
            "connect host=${host.id} endpoint=$baseUrl secretLength=${secret.length}",
        )

        val client = dev.nanobot.gateway.NanobotGateway(
            dev.nanobot.gateway.GatewayConfig(
                baseUrl = baseUrl,
                secret = secret,
                clientId = settings?.clientId ?: "android",
            ),
        )
        client.useChat(host.lastChatId)
        gateway = client
        eventJob = scope.launch {
            client.events.collect { event -> _events.emit(event) }
        }
        stateJob = scope.launch {
            client.state.collect { state ->
                android.util.Log.d("NanobotGateway", "state=$state")
                _state.value = state
            }
        }
        client.connect()
        startService()
    }

    /** Saves a scanned/pasted pairing offer, makes it current, and connects. */
    @Synchronized
    fun savePairing(offer: PairingOffer): HostProfile? {
        val host = hostStore?.saveOffer(offer) ?: return null
        connectHost(host)
        return host
    }

    /** Saves a manually entered direct gateway, makes it current, and connects. */
    @Synchronized
    fun saveDirectHost(name: String, endpoint: String, secret: String): HostProfile? {
        val host = hostStore?.saveDirect(name, endpoint, secret) ?: return null
        connectHost(host)
        return host
    }

    fun reconnect() {
        gateway?.reconnect()
    }

    fun disconnect() {
        gateway?.disconnect()
        tunnel?.stop()
        tunnel = null
        _tunnelState.value = null
    }

    /** Removes the current host. If another host remains, it becomes active. */
    @Synchronized
    fun reset() {
        val current = currentHost()?.id
        if (current != null) removeHost(current) else stopEverything()
    }

    @Synchronized
    fun removeHost(id: String) {
        val store = hostStore ?: return
        val wasCurrent = id == store.currentId() || id == currentHostId()
        if (wasCurrent) teardown()
        store.remove(id)
        if (wasCurrent) {
            val next = store.currentHost()
            if (next != null) connectHost(next) else stopEverything()
        }
    }

    private fun stopEverything() {
        teardown()
        _state.value = GatewayState.Disconnected
        context?.let { ctx ->
            runCatching { ctx.stopService(android.content.Intent(ctx, NanobotService::class.java)) }
        }
    }

    private fun teardown() {
        eventJob?.cancel()
        stateJob?.cancel()
        tunnelJob?.cancel()
        eventJob = null
        stateJob = null
        tunnelJob = null
        gateway?.close()
        gateway = null
        connectedHostId = null
        tunnel?.stop()
        tunnel = null
        _tunnelState.value = null
        _state.value = GatewayState.Disconnected
    }

    private fun startService() {
        val ctx = context ?: return
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                ctx,
                android.content.Intent(ctx, NanobotService::class.java),
            )
        }
    }

    private fun client(): dev.nanobot.gateway.NanobotGateway =
        gateway ?: throw dev.nanobot.gateway.GatewayException("gateway is not connected")

    suspend fun listSessions(): List<SessionSummary> = client().listSessions()

    suspend fun fetchThread(chatId: String): ThreadPage = client().fetchThread(chatId)

    /** Fetches one history image through the same tunnel and token as the REST reads. */
    suspend fun fetchMedia(url: String): ByteArray = client().fetchMedia(url)

    suspend fun attach(chatId: String) {
        client().attach(chatId)
    }

    suspend fun newChat(): String = client().newChat()

    suspend fun forkChat(sourceChatId: String, beforeUserIndex: Int, title: String?): String =
        client().forkChat(sourceChatId, beforeUserIndex, title)

    suspend fun sendMessage(
        chatId: String,
        text: String,
        turnId: String,
        media: List<AttachmentReference>,
        cliApps: List<String>,
        mcpPresets: List<String>,
        workspaceScope: WorkspaceScope? = null,
    ): String = client().sendMessage(chatId, text, turnId, media, cliApps, mcpPresets, workspaceScope)

    /** Cancels the gateway's active turn for [chatId]. */
    fun sendStop(chatId: String, turnId: String) = client().sendStop(chatId, turnId)

    /** Largest single attachment the gateway will accept for [mime]. */
    fun maxAttachmentBytes(mime: String): Long = client().maxAttachmentBytes(mime)

    data class PreparedAttachment(val bytes: ByteArray, val mime: String)

    /**
     * Reads the selected file and, for an oversized raster image, re-encodes it under the
     * gateway's per-file budget. Files that cannot be made to fit throw a user-facing error
     * before any bytes are sent.
     */
    suspend fun prepareAttachment(uri: android.net.Uri, mime: String): PreparedAttachment =
        withContext(Dispatchers.IO) {
            val limit = maxAttachmentBytes(mime)
            val declared = sizeBytes(uri)
            if (declared > MAX_ATTACHMENT_INPUT_BYTES) {
                throw GatewayException(
                    AppLocale.strings.attachmentTooLarge(
                        formatFileSize(declared),
                        formatFileSize(limit),
                    ),
                )
            }
            val bytes = readBytes(uri)
            when (val result = AttachmentPreparer.prepare(bytes, mime, limit) { data, type, max ->
                AttachmentCompressor.compress(data, type, max)
            }) {
                is AttachmentPreparation.Ready -> PreparedAttachment(result.bytes, result.mime)
                is AttachmentPreparation.TooLarge -> throw GatewayException(
                    if (result.afterCompression) {
                        AppLocale.strings.attachmentTooLargeAfterCompression(
                            formatFileSize(result.size),
                            formatFileSize(result.limit),
                        )
                    } else {
                        AppLocale.strings.attachmentTooLarge(
                            formatFileSize(result.size),
                            formatFileSize(result.limit),
                        )
                    },
                )
            }
        }

    suspend fun uploadAttachment(
        name: String,
        mime: String,
        bytes: ByteArray,
        onProgress: (Float) -> Unit = {},
    ): AttachmentReference = client().uploadAttachment(name, mime, bytes, onProgress)

    suspend fun fetchCommands(): List<SlashCommand> = client().fetchCommands()

    suspend fun fetchSkills(): List<SkillSummary> = client().fetchSkills()

    suspend fun fetchSkillDetail(name: String): SkillDetail = client().fetchSkillDetail(name)

    suspend fun fetchCliApps(): List<CliAppSummary> = client().fetchCliApps()

    suspend fun fetchMcpPresets(): McpPresetsResponse = client().fetchMcpPresets()

    suspend fun fetchAutomations(): List<AutomationJob> = client().fetchAutomations()

    suspend fun fetchAutomationChats(id: String): AutomationChatsResponse =
        client().fetchAutomationChats(id)

    suspend fun setSkillEnabled(name: String, enabled: Boolean) =
        client().setSkillEnabled(name, enabled)

    suspend fun deleteSkill(name: String) = client().deleteSkill(name)

    suspend fun mcpPresetAction(
        action: String,
        name: String,
        values: Map<String, String> = emptyMap(),
    ): McpPresetsResponse = client().mcpPresetAction(action, name, values)

    suspend fun saveCustomMcpServer(values: Map<String, String>): McpPresetsResponse =
        client().saveCustomMcpServer(values)

    suspend fun importMcpConfig(config: String): McpPresetsResponse =
        client().importMcpConfig(config)

    suspend fun updateMcpTools(name: String, enabledTools: List<String>): McpPresetsResponse =
        client().updateMcpTools(name, enabledTools)

    suspend fun startMcpOAuth(
        name: String,
        values: Map<String, String> = emptyMap(),
        reset: Boolean = false,
    ): McpOAuthFlowPayload = client().startMcpOAuth(name, values, reset)

    suspend fun fetchMcpOAuthStatus(flowId: String): McpOAuthFlowPayload =
        client().fetchMcpOAuthStatus(flowId)

    suspend fun completeMcpOAuth(flowId: String, callbackUrl: String): McpOAuthFlowPayload =
        client().completeMcpOAuth(flowId, callbackUrl)

    suspend fun cancelMcpOAuth(flowId: String): McpOAuthFlowPayload =
        client().cancelMcpOAuth(flowId)

    suspend fun setAutomationEnabled(id: String, enabled: Boolean) =
        client().setAutomationEnabled(id, enabled)

    suspend fun deleteAutomation(id: String) = client().deleteAutomation(id)

    suspend fun runAutomation(id: String) = client().runAutomation(id)

    suspend fun updateAutomation(id: String, update: AutomationUpdate): List<AutomationJob> =
        client().updateAutomation(id, update)

    suspend fun changeAutomationChat(
        id: String,
        targetId: String,
        revision: String,
        message: String,
    ): List<AutomationJob> = client().changeAutomationChat(id, targetId, revision, message)

    suspend fun fetchWorkspaces(): WorkspacesResponse = client().fetchWorkspaces()

    suspend fun fetchWorkspaceDirectories(
        path: String,
        query: String = "",
        showHidden: Boolean = false,
        allowPartial: Boolean = false,
    ): WorkspaceDirectoriesResponse =
        client().fetchWorkspaceDirectories(path, query, showHidden, allowPartial)

    suspend fun resolveWorkspaceProject(path: String): WorkspaceProject =
        client().resolveWorkspaceProject(path)

    suspend fun fetchSidebarState(): SidebarState = client().fetchSidebarState()

    suspend fun fetchSettings(): GatewaySettings = client().fetchSettings()

    suspend fun updateAgentSettings(payload: JsonObject): GatewaySettings =
        client().updateAgentSettings(payload)

    suspend fun updateModelConfiguration(payload: JsonObject): GatewaySettings =
        client().updateModelConfiguration(payload)

    suspend fun createModelConfiguration(payload: JsonObject): GatewaySettings =
        client().createModelConfiguration(payload)

    suspend fun deleteModelConfiguration(name: String): GatewaySettings =
        client().deleteModelConfiguration(name)

    suspend fun updateModelCallOrder(order: List<String>): GatewaySettings =
        client().updateModelCallOrder(order)

    suspend fun updateRuntimeConfig(values: JsonObject): GatewaySettings =
        client().updateRuntimeConfig(values)

    suspend fun updateSidebarState(state: JsonObject): SidebarState =
        client().updateSidebarState(state)

    suspend fun deleteSession(key: String, deleteAutomations: Boolean = false): SessionDeleteResult =
        client().deleteSession(key, deleteAutomations)

    suspend fun readBytes(uri: android.net.Uri): ByteArray = withContext(Dispatchers.IO) {
        val ctx = context ?: throw IllegalStateException("repository is not initialized")
        val resolver = ctx.contentResolver
        resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            input.copyTo(output)
            output.toByteArray()
        } ?: throw IllegalArgumentException("cannot read the selected file")
    }

    fun displayName(uri: android.net.Uri): String = withContextName(uri, "attachment")

    /** Size reported by the content provider, or 0 when it does not publish one. */
    fun sizeBytes(uri: android.net.Uri): Long {
        val ctx = context ?: return 0L
        return runCatching {
            ctx.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            }
        }.getOrNull() ?: 0L
    }

    private fun withContextName(uri: android.net.Uri, fallback: String): String {
        val ctx = context ?: return fallback
        return runCatching {
            ctx.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: fallback
    }

    fun mimeType(uri: android.net.Uri): String {
        val ctx = context ?: return "application/octet-stream"
        return ctx.contentResolver.getType(uri) ?: "application/octet-stream"
    }
}
