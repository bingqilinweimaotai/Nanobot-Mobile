package dev.nanobot.gateway

import dev.nanobot.protocol.AttachmentLimits
import dev.nanobot.protocol.AttachmentReference
import dev.nanobot.protocol.AttachmentUploadResponse
import dev.nanobot.protocol.AutomationChatsResponse
import dev.nanobot.protocol.AutomationJob
import dev.nanobot.protocol.AutomationUpdate
import dev.nanobot.protocol.AutomationsResponse
import dev.nanobot.protocol.BootstrapResponse
import dev.nanobot.protocol.CliAppSummary
import dev.nanobot.protocol.CliAppsResponse
import dev.nanobot.protocol.CommandsResponse
import dev.nanobot.protocol.GatewaySettings
import dev.nanobot.protocol.InboundEvent
import dev.nanobot.protocol.InboundEvents
import dev.nanobot.protocol.McpOAuthFlowPayload
import dev.nanobot.protocol.McpPresetsResponse
import dev.nanobot.protocol.NanobotJson
import dev.nanobot.protocol.Outbound
import dev.nanobot.protocol.SessionDeleteResult
import dev.nanobot.protocol.SessionSummary
import dev.nanobot.protocol.SessionsResponse
import dev.nanobot.protocol.SkillDetail
import dev.nanobot.protocol.SkillSummary
import dev.nanobot.protocol.SkillsResponse
import dev.nanobot.protocol.SidebarState
import dev.nanobot.protocol.SlashCommand
import dev.nanobot.protocol.ThreadPage
import dev.nanobot.protocol.ThreadPageParser
import dev.nanobot.protocol.UploadCapability
import dev.nanobot.protocol.WorkspaceDirectoriesResponse
import dev.nanobot.protocol.WorkspaceProject
import dev.nanobot.protocol.WorkspaceScope
import dev.nanobot.protocol.WorkspacesResponse
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * One gateway connection: bootstrap credentials, the multiplexed WebSocket, and the REST reads the
 * chat screen needs. The gateway owns execution; this class only speaks its public protocol.
 */
class NanobotGateway private constructor(
    private val config: GatewayConfig,
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    constructor(config: GatewayConfig) : this(config, defaultClient(), NanobotJson)

    internal constructor(config: GatewayConfig, httpClient: OkHttpClient) :
        this(config, httpClient, NanobotJson)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<GatewayState>(GatewayState.Disconnected)
    val state: StateFlow<GatewayState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<InboundEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<InboundEvent> = _events.asSharedFlow()

    @Volatile
    private var closed = false

    @Volatile
    private var loopJob: Job? = null

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var activeChatId: String? = null

    @Volatile
    private var readyChatId: String? = null

    @Volatile
    private var attachWaiter: CompletableDeferred<String>? = null

    @Volatile
    private var readyWaiter: CompletableDeferred<Unit>? = null

    @Volatile
    private var closeWaiter: CompletableDeferred<Unit>? = null

    @Volatile
    private var lastFailure: String = ""

    @Volatile
    private var generation = 0

    private var bootstrap: BootstrapResponse? = null
    private var apiTokenExpiresAtMs: Long = 0

    private val pendingAcceptance = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<InboundEvent.WebUiResponse>>()

    @Volatile
    private var uploadCapability: UploadCapability? = null

    val chatId: String? get() = activeChatId

    fun useChat(chatId: String?) {
        activeChatId = chatId
    }

    val isReady: Boolean get() = state.value is GatewayState.Ready

    @Synchronized
    fun connect() {
        if (loopJob?.isActive == true) return
        closed = false
        val current = ++generation
        loopJob = scope.launch { connectionLoop(current) }
    }

    @Synchronized
    fun reconnect() {
        closed = false
        loopJob?.cancel()
        socket?.cancel()
        socket = null
        val current = ++generation
        loopJob = scope.launch { connectionLoop(current) }
    }

    @Synchronized
    fun disconnect() {
        closed = true
        generation += 1
        loopJob?.cancel()
        loopJob = null
        socket?.cancel()
        socket = null
        uploadCapability = null
        failPending(GatewayException("connection closed before message acceptance"))
        _state.value = GatewayState.Disconnected
    }

    fun close() {
        disconnect()
        scope.cancel()
    }

    // ---------------------------------------------------------------- connection

    private suspend fun connectionLoop(current: Int) {
        var attempt = 0
        while (!closed && current == generation) {
            try {
                _state.value = if (attempt == 0) {
                    GatewayState.Connecting
                } else {
                    GatewayState.Reconnecting(attempt, lastFailure)
                }
                val boot = bootstrap(force = true)
                openSocket(boot)
                attempt = 0
                closeWaiter?.await()
                if (!closed) lastFailure = "connection closed"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastFailure = error.message ?: error.javaClass.simpleName
                if (closed || current != generation) break
                _state.value = GatewayState.Reconnecting(attempt + 1, lastFailure)
            }
            if (closed || current != generation) break
            attempt += 1
            delay(backoffMs(attempt))
        }
        if (current == generation) _state.value = GatewayState.Disconnected
    }

    private suspend fun openSocket(boot: BootstrapResponse) {
        val ready = CompletableDeferred<Unit>()
        val closedSignal = CompletableDeferred<Unit>()
        readyWaiter = ready
        closeWaiter = closedSignal

        val request = Request.Builder().url(buildWsUrl(boot)).build()
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = InboundEvents.decode(text)
                when (event) {
                    is InboundEvent.Ready -> {
                        readyChatId = event.chatId
                        uploadCapability = event.upload
                        if (activeChatId == null) activeChatId = event.chatId
                        _state.value = GatewayState.Ready(
                            chatId = activeChatId ?: event.chatId,
                            capabilities = event.capabilities,
                        )
                        if (!ready.isCompleted) ready.complete(Unit)
                    }

                    is InboundEvent.Attached -> {
                        activeChatId = event.chatId
                        val capabilities = (state.value as? GatewayState.Ready)?.capabilities.orEmpty()
                        _state.value = GatewayState.Ready(event.chatId, capabilities)
                        attachWaiter?.complete(event.chatId)
                    }

                    is InboundEvent.MessageAccepted -> {
                        if (event.turnId.isNotEmpty()) {
                            pendingAcceptance.remove(event.turnId)?.complete(Unit)
                        }
                    }

                    is InboundEvent.UserMessage -> {
                        event.turnId?.let { pendingAcceptance.remove(it)?.complete(Unit) }
                    }

                    is InboundEvent.ErrorEvent -> {
                        event.turnId?.let { turnId ->
                            pendingAcceptance.remove(turnId)?.completeExceptionally(
                                GatewayException(event.reason ?: event.detail ?: "turn rejected"),
                            )
                        }
                    }

                    is InboundEvent.WebUiResponse -> {
                        if (event.requestId.isNotEmpty()) {
                            pendingRequests.remove(event.requestId)?.complete(event)
                        }
                    }

                    else -> Unit
                }
                _events.tryEmit(event)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closedSignal.isCompleted) closedSignal.complete(Unit)
                failPending(GatewayException("connection closed before message acceptance"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!ready.isCompleted) ready.completeExceptionally(t)
                if (!closedSignal.isCompleted) closedSignal.complete(Unit)
                failPending(GatewayException("connection closed before message acceptance"))
            }
        }

        socket = httpClient.newWebSocket(request, listener)
        try {
            withTimeout(READY_TIMEOUT_MS) { ready.await() }
        } catch (timeout: TimeoutCancellationException) {
            socket?.cancel()
            socket = null
            throw GatewayException("gateway did not send ready within ${READY_TIMEOUT_MS / 1000}s")
        }

        val desired = activeChatId
        if (desired != null && desired != readyChatId) {
            sendRaw(Outbound.attach(desired))
        }
    }

    private fun buildWsUrl(boot: BootstrapResponse): String =
        buildWebSocketUrl(config.baseUrl, boot, config.clientId)

    // ---------------------------------------------------------------- bootstrap + REST

    private suspend fun bootstrap(force: Boolean): BootstrapResponse {
        val cached = bootstrap
        if (!force && cached != null && System.currentTimeMillis() < apiTokenExpiresAtMs) return cached

        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/webui/bootstrap")
            .apply {
                if (config.secret.isNotBlank()) header("X-Nanobot-Auth", config.secret)
            }
            .get()
            .build()
        val result = executeRaw(request)
        if (result.code !in 200..299) {
            throw GatewayException("bootstrap failed: HTTP ${result.code}", result.code)
        }
        val parsed = runCatching { json.decodeFromString<BootstrapResponse>(result.body) }
            .getOrElse { throw GatewayException("bootstrap response is invalid") }
        if (parsed.token.isBlank()) throw GatewayException("bootstrap response has no WebSocket token")
        bootstrap = parsed
        apiTokenExpiresAtMs = System.currentTimeMillis() +
            ((parsed.expiresIn ?: 300).toLong() * 1000L) -
            30_000L
        return parsed
    }

    suspend fun listSessions(): List<SessionSummary> {
        val body = apiGet("/api/sessions")
        return runCatching { json.decodeFromString<SessionsResponse>(body).sessions }
            .getOrElse { throw GatewayException("session list response is invalid") }
            .filter { it.key.startsWith("websocket:") && it.chatId.isNotEmpty() }
    }

    suspend fun fetchThread(chatId: String, before: String? = null): ThreadPage {
        val key = encodePathSegment("websocket:$chatId")
        val query = buildString {
            append("?limit=120&direction=latest")
            if (!before.isNullOrBlank()) append("&before=").append(encodeQuery(before))
        }
        val body = try {
            apiGet("/api/sessions/$key/webui-thread$query")
        } catch (error: GatewayException) {
            // A brand-new chat has no persisted thread until its first turn, so the gateway
            // answers 404. The WebUI treats that as empty history; do the same here.
            if (error.statusCode == 404) return ThreadPage(events = emptyList())
            throw error
        }
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw GatewayException("thread response is invalid")
        return runCatching { ThreadPageParser.parse(root, raw = body) }
            .getOrElse { throw GatewayException("thread response is invalid: ${it.message}") }
    }

    suspend fun fetchCommands(): List<SlashCommand> =
        decodeFeature("commands", apiGet("/api/commands")) {
            json.decodeFromString<CommandsResponse>(it).commands
        }

    suspend fun fetchSkills(): List<SkillSummary> =
        decodeFeature("skills", apiGet("/api/webui/skills")) {
            json.decodeFromString<SkillsResponse>(it).skills
        }

    suspend fun fetchSkillDetail(name: String): SkillDetail =
        decodeFeature("skill detail", apiGet("/api/webui/skills/${encodeQuery(name)}")) {
            json.decodeFromString<SkillDetail>(it)
        }

    suspend fun fetchCliApps(): List<CliAppSummary> =
        decodeFeature("CLI apps", apiGet("/api/settings/cli-apps?installed_only=1")) {
            json.decodeFromString<CliAppsResponse>(it).apps
        }

    suspend fun fetchMcpPresets(): McpPresetsResponse =
        decodeFeature("MCP presets", apiGet("/api/settings/mcp-presets")) {
            json.decodeFromString<McpPresetsResponse>(it)
        }

    suspend fun fetchAutomations(): List<AutomationJob> =
        decodeFeature("automations", apiGet("/api/webui/automations")) {
            json.decodeFromString<AutomationsResponse>(it).jobs
        }

    /** Lists the chats a scheduled automation can be moved to, with its binding revision. */
    suspend fun fetchAutomationChats(id: String): AutomationChatsResponse =
        decodeFeature(
            "automation chats",
            apiGet("/api/webui/automations/chats?id=${encodeQuery(id)}"),
        ) {
            json.decodeFromString<AutomationChatsResponse>(it)
        }

    suspend fun fetchWorkspaces(): WorkspacesResponse =
        decodeFeature("workspaces", apiGet("/api/workspaces")) {
            json.decodeFromString<WorkspacesResponse>(it)
        }

    /** Lists folders for the WebUI workspace picker, starting at [path] or the default project. */
    suspend fun fetchWorkspaceDirectories(
        path: String,
        query: String = "",
        showHidden: Boolean = false,
        allowPartial: Boolean = false,
    ): WorkspaceDirectoriesResponse {
        val params = buildString {
            append("?path=").append(encodeQuery(path))
            append("&q=").append(encodeQuery(query))
            append("&hidden=").append(if (showHidden) "1" else "0")
            append("&partial=").append(if (allowPartial) "1" else "0")
        }
        return decodeFeature(
            "workspace directories",
            apiGet("/api/workspaces/directories$params"),
        ) {
            json.decodeFromString<WorkspaceDirectoriesResponse>(it)
        }
    }

    /** Validates [path] and remembers it as a recent project, matching the WebUI picker. */
    suspend fun resolveWorkspaceProject(path: String): WorkspaceProject {
        val result = requestMutation(
            "workspace.resolve_project",
            buildJsonObject { put("path", path) },
        ) ?: throw GatewayException("workspace response is invalid")
        return runCatching {
            json.decodeFromJsonElement<WorkspaceProject>(result)
        }.getOrElse { throw GatewayException("workspace response is invalid") }
    }

    suspend fun fetchSidebarState(): SidebarState {
        val body = apiGet("/api/webui/sidebar-state")
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw GatewayException("sidebar state response is invalid")
        return runCatching { SidebarState.fromJson(root) }
            .getOrElse { throw GatewayException("sidebar state response is invalid") }
    }

    /** Read-only gateway configuration for the Android settings page. */
    suspend fun fetchSettings(): GatewaySettings =
        decodeFeature("settings", apiGet("/api/settings")) {
            json.decodeFromString<GatewaySettings>(it)
        }

    /**
     * Applies `settings.agent.update`: the default model, provider, context window, timezone, and
     * which model preset is active. The gateway returns the canonical settings payload.
     */
    suspend fun updateAgentSettings(payload: JsonObject): GatewaySettings =
        settingsMutation("settings.agent.update", payload)

    /** Edits one named model preset; the `default` preset cannot be updated this way. */
    suspend fun updateModelConfiguration(payload: JsonObject): GatewaySettings =
        settingsMutation("settings.model_configuration.update", payload)

    suspend fun createModelConfiguration(payload: JsonObject): GatewaySettings =
        settingsMutation("settings.model_configuration.create", payload)

    suspend fun deleteModelConfiguration(name: String): GatewaySettings =
        settingsMutation(
            "settings.model_configuration.delete",
            buildJsonObject { put("name", name) },
        )

    /** Reorders the preset list; the first entry becomes the primary model and the rest fall back. */
    suspend fun updateModelCallOrder(order: List<String>): GatewaySettings =
        settingsMutation(
            "settings.model_call_order.update",
            buildJsonObject {
                put("order", buildJsonArray { order.forEach { add(it) } })
            },
        )

    /** Writes one or more `agents.defaults.*` runtime values, such as `unified_session`. */
    suspend fun updateRuntimeConfig(values: JsonObject): GatewaySettings =
        settingsMutation(
            "settings.runtime_config.update",
            buildJsonObject { put("values", values) },
        )

    private suspend fun settingsMutation(action: String, payload: JsonObject): GatewaySettings {
        val result = requestMutation(action, payload)
            ?: throw GatewayException("settings response is invalid")
        return runCatching { json.decodeFromJsonElement<GatewaySettings>(result) }
            .getOrElse { throw GatewayException("settings response is invalid") }
    }

    /**
     * Replaces the gateway's sidebar document. The WebUI owns the same file, so callers pass the
     * previously fetched document with only their keys changed; the reply is the saved canonical
     * state.
     */
    suspend fun updateSidebarState(state: JsonObject): SidebarState {
        val result = requestMutation(
            "sidebar.update",
            buildJsonObject { put("state", state) },
        ) ?: throw GatewayException("sidebar response is invalid")
        return runCatching { SidebarState.fromJson(result) }
            .getOrElse { throw GatewayException("sidebar response is invalid") }
    }

    /**
     * Deletes one conversation, matching the WebUI's `session.delete` mutation. A conversation that
     * still has automations is not deleted unless [deleteAutomations] is true; the reply names the
     * blocking jobs so the caller can ask again.
     */
    suspend fun deleteSession(
        key: String,
        deleteAutomations: Boolean = false,
    ): SessionDeleteResult {
        val result = requestMutation(
            "session.delete",
            buildJsonObject {
                put("key", key)
                if (deleteAutomations) put("delete_automations", true)
            },
        ) ?: throw GatewayException("delete response is invalid")
        return runCatching { json.decodeFromJsonElement<SessionDeleteResult>(result) }
            .getOrElse { throw GatewayException("delete response is invalid") }
    }

    private inline fun <T> decodeFeature(label: String, body: String, decode: (String) -> T): T =
        runCatching { decode(body) }
            .getOrElse { throw GatewayException("$label response is invalid") }

    private suspend fun apiGet(path: String): String {
        var token = ensureApiToken()
        var result = executeRaw(apiRequest(path, token))
        if (result.code == 401) {
            bootstrap = null
            token = ensureApiToken()
            result = executeRaw(apiRequest(path, token))
        }
        if (result.code !in 200..299) {
            throw GatewayException("HTTP ${result.code}: ${result.body.take(300)}", result.code)
        }
        return result.body
    }

    /**
     * Fetches one gateway media URL. Absolute URLs keep only their path and query, so a URL saved
     * before a host re-add still resolves through the current tunnel instead of the old address.
     */
    suspend fun fetchMedia(url: String): ByteArray {
        val path = mediaPath(url)
        var token = ensureApiToken()
        var result = executeBytes(apiRequest(path, token))
        if (result.code == 401) {
            bootstrap = null
            token = ensureApiToken()
            result = executeBytes(apiRequest(path, token))
        }
        if (result.code !in 200..299) {
            throw GatewayException("HTTP ${result.code}: media unavailable", result.code)
        }
        return result.bytes
    }

    private fun mediaPath(url: String): String {
        val trimmed = url.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val parsed = trimmed.toHttpUrlOrNull()
                ?: throw GatewayException("media URL is invalid")
            val query = parsed.encodedQuery?.let { "?$it" }.orEmpty()
            return parsed.encodedPath + query
        }
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    private data class HttpBytesResult(val code: Int, val bytes: ByteArray)

    private suspend fun executeBytes(request: Request): HttpBytesResult =
        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                val declared = response.body?.contentLength() ?: -1L
                if (declared > MAX_MEDIA_BYTES) throw GatewayException("media is too large")
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (bytes.size > MAX_MEDIA_BYTES) throw GatewayException("media is too large")
                HttpBytesResult(response.code, bytes)
            }
        }

    private suspend fun ensureApiToken(): String? {
        val cached = bootstrap
        if (cached == null || System.currentTimeMillis() >= apiTokenExpiresAtMs) {
            return bootstrap(force = true).apiToken
        }
        return cached.apiToken
    }

    private fun apiRequest(path: String, token: String?): Request =
        Request.Builder()
            .url(config.baseUrl.trimEnd('/') + path)
            .apply {
                if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
            }
            .get()
            .build()

    private suspend fun executeRaw(request: Request): HttpResult = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            HttpResult(response.code, response.body?.string().orEmpty())
        }
    }

    // ---------------------------------------------------------------- commands

    suspend fun attach(chatId: String) {
        activeChatId = chatId
        val waiter = CompletableDeferred<String>()
        attachWaiter = waiter
        try {
            sendRaw(Outbound.attach(chatId))
            try {
                withTimeout(ATTACH_TIMEOUT_MS) { waiter.await() }
            } catch (_: TimeoutCancellationException) {
                // The socket stays usable; the server may have attached without a reply.
            }
        } finally {
            if (attachWaiter === waiter) attachWaiter = null
        }
    }

    suspend fun newChat(): String {
        val waiter = CompletableDeferred<String>()
        attachWaiter = waiter
        try {
            sendRaw(Outbound.newChat())
            return withTimeout(ATTACH_TIMEOUT_MS) { waiter.await() }
        } catch (timeout: TimeoutCancellationException) {
            throw GatewayException("gateway did not create a chat in time")
        } finally {
            if (attachWaiter === waiter) attachWaiter = null
        }
    }

    suspend fun forkChat(sourceChatId: String, beforeUserIndex: Int, title: String?): String {
        val waiter = CompletableDeferred<String>()
        attachWaiter = waiter
        try {
            sendRaw(Outbound.forkChat(sourceChatId, beforeUserIndex, title))
            return withTimeout(ATTACH_TIMEOUT_MS) { waiter.await() }
        } catch (timeout: TimeoutCancellationException) {
            throw GatewayException("gateway did not fork the chat in time")
        } finally {
            if (attachWaiter === waiter) attachWaiter = null
        }
    }

    /**
     * Sends one turn and waits for the gateway's acceptance receipt. The caller keeps the draft
     * until this returns; a timeout means delivery is uncertain, not that the draft should vanish.
     */
    suspend fun sendMessage(
        chatId: String,
        text: String,
        turnId: String = UUID.randomUUID().toString(),
        media: List<AttachmentReference> = emptyList(),
        cliApps: List<String> = emptyList(),
        mcpPresets: List<String> = emptyList(),
        workspaceScope: WorkspaceScope? = null,
    ): String {
        val accepted = CompletableDeferred<Unit>()
        pendingAcceptance[turnId] = accepted
        try {
            sendRaw(Outbound.message(chatId, text, turnId, media, cliApps, mcpPresets, workspaceScope))
            withTimeout(ACCEPT_TIMEOUT_MS) { accepted.await() }
        } catch (timeout: TimeoutCancellationException) {
            throw GatewayException("message acceptance not confirmed; the draft was kept")
        } finally {
            pendingAcceptance.remove(turnId)
        }
        return turnId
    }

    /**
     * Asks the gateway to cancel the active turn for [chatId]. The command router handles `/stop`
     * as a side channel, so no user message enters the transcript.
     */
    fun sendStop(chatId: String, turnId: String) {
        sendRaw(Outbound.message(chatId, "/stop", turnId))
    }

    /**
     * Largest single attachment the gateway accepts for [mime]. The per-file budget comes from
     * bootstrap so a gateway that raises it does not need an app update. Videos use the gateway's
     * separate 20 MiB budget, which is not part of the advertised attachment limits.
     */
    fun maxAttachmentBytes(mime: String): Long =
        attachmentLimit(mime, bootstrap?.limits?.attachments)

    suspend fun uploadAttachment(
        name: String,
        mime: String,
        bytes: ByteArray,
        onProgress: (Float) -> Unit = {},
    ): AttachmentReference {
        val upload = uploadCapability
            ?: throw GatewayException("gateway did not advertise attachment upload")
        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + upload.path)
            .header("Authorization", "Bearer ${upload.token}")
            .header("X-Attachment-Name", encodeQuery(name))
            .post(ProgressRequestBody(bytes, mime.toMediaTypeOrNull(), onProgress))
            .build()
        val result = executeRaw(request)
        if (result.code != 201) {
            // The gateway explains rejections (size, MIME, auth) in the body; keep that text so
            // the composer can say why instead of only showing the status code.
            val detail = attachmentErrorDetail(result.body)
            throw GatewayException(
                buildString {
                    append("attachment upload failed: HTTP ${result.code}")
                    if (detail.isNotEmpty()) append(": ").append(detail)
                },
                result.code,
            )
        }
        val parsed = runCatching { json.decodeFromString<AttachmentUploadResponse>(result.body) }
            .getOrElse { throw GatewayException("attachment response is invalid") }
        return AttachmentReference(parsed.reference, name)
    }

    suspend fun setSkillEnabled(name: String, enabled: Boolean) {
        requestMutation("skill.update", buildJsonObject {
            put("name", name)
            put("enabled", enabled)
        })
    }

    suspend fun deleteSkill(name: String) {
        requestMutation("skill.delete", buildJsonObject { put("name", name) })
    }

    /**
     * Runs one MCP preset action exactly like the WebUI. [values] carries the credential fields
     * the form collected plus `use_proxy`; blank values are omitted so the server keeps what it has.
     */
    suspend fun mcpPresetAction(
        action: String,
        name: String,
        values: Map<String, String> = emptyMap(),
    ): McpPresetsResponse = mcpMutation(
        action,
        buildJsonObject {
            put("name", name)
            values.forEach { (key, value) ->
                if (value.isNotBlank()) put(key, value)
            }
        },
    )

    /** Saves a custom MCP server from the same query fields the WebUI's form sends. */
    suspend fun saveCustomMcpServer(values: Map<String, String>): McpPresetsResponse =
        mcpMutation(
            "custom",
            buildJsonObject {
                values.forEach { (key, value) ->
                    if (value.isNotBlank()) put(key, value)
                }
            },
        )

    suspend fun importMcpConfig(config: String): McpPresetsResponse =
        mcpMutation("import", buildJsonObject { put("config", config) })

    suspend fun updateMcpTools(name: String, enabledTools: List<String>): McpPresetsResponse =
        mcpMutation(
            "tools",
            buildJsonObject {
                put("name", name)
                put("enabled_tools", buildJsonArray { enabledTools.forEach { add(it) } })
            },
        )

    suspend fun startMcpOAuth(
        name: String,
        values: Map<String, String> = emptyMap(),
        reset: Boolean = false,
    ): McpOAuthFlowPayload {
        val result = requestMutation(
            "settings.mcp.oauth_start",
            buildJsonObject {
                put("name", name)
                if (reset) put("reset", true)
                values.forEach { (key, value) ->
                    if (value.isNotBlank()) put(key, value)
                }
            },
        ) ?: throw GatewayException("MCP OAuth response is invalid")
        return decodeMcpOAuth(result)
    }

    suspend fun fetchMcpOAuthStatus(flowId: String): McpOAuthFlowPayload =
        decodeFeature(
            "MCP OAuth status",
            apiGet("/api/settings/mcp-oauth/status?flow_id=${encodeQuery(flowId)}"),
        ) {
            json.decodeFromString<McpOAuthFlowPayload>(it)
        }

    suspend fun completeMcpOAuth(flowId: String, callbackUrl: String): McpOAuthFlowPayload {
        val result = requestMutation(
            "settings.mcp.oauth_complete",
            buildJsonObject {
                put("flow_id", flowId)
                put("callback_url", callbackUrl)
            },
        ) ?: throw GatewayException("MCP OAuth response is invalid")
        return decodeMcpOAuth(result)
    }

    suspend fun cancelMcpOAuth(flowId: String): McpOAuthFlowPayload {
        val result = requestMutation(
            "settings.mcp.oauth_cancel",
            buildJsonObject { put("flow_id", flowId) },
        ) ?: throw GatewayException("MCP OAuth response is invalid")
        return decodeMcpOAuth(result)
    }

    private suspend fun mcpMutation(action: String, payload: JsonObject): McpPresetsResponse {
        val result = requestMutation("settings.mcp.$action", payload)
            ?: throw GatewayException("MCP response is invalid")
        return runCatching { json.decodeFromJsonElement<McpPresetsResponse>(result) }
            .getOrElse { throw GatewayException("MCP response is invalid") }
    }

    private fun decodeMcpOAuth(result: JsonObject): McpOAuthFlowPayload =
        runCatching { json.decodeFromJsonElement<McpOAuthFlowPayload>(result) }
            .getOrElse { throw GatewayException("MCP OAuth response is invalid") }

    suspend fun runAutomation(id: String) {
        requestMutation("automation.run", buildJsonObject { put("id", id) })
    }

    suspend fun setAutomationEnabled(id: String, enabled: Boolean) {
        requestMutation(
            if (enabled) "automation.enable" else "automation.disable",
            buildJsonObject { put("id", id) },
        )
    }

    /** Removes a user-created automation; the WebUI's detail dialog offers the same action. */
    suspend fun deleteAutomation(id: String) {
        requestMutation("automation.delete", buildJsonObject { put("id", id) })
    }

    /**
     * Renames a task, rewrites its instructions, or changes its schedule, mirroring
     * `automation.update`. The gateway answers with the refreshed list.
     */
    suspend fun updateAutomation(id: String, values: AutomationUpdate): List<AutomationJob> =
        automationMutation("automation.update", id, json.encodeToJsonElement(values))

    /**
     * Moves a task to another chat, matching the WebUI's chat picker. [revision] is the value
     * returned by [fetchAutomationChats] so a concurrent scheduler edit is rejected as a conflict.
     */
    suspend fun changeAutomationChat(
        id: String,
        targetId: String,
        revision: String,
        message: String,
    ): List<AutomationJob> = automationMutation(
        "automation.change_chat",
        id,
        buildJsonObject {
            put("target_id", targetId)
            put("revision", revision)
            put("message", message)
        },
    )

    private suspend fun automationMutation(
        action: String,
        id: String,
        values: JsonElement,
    ): List<AutomationJob> {
        val result = requestMutation(
            action,
            buildJsonObject {
                put("id", id)
                put("values", values)
            },
        ) ?: throw GatewayException("$action response is invalid")
        return runCatching { json.decodeFromJsonElement<AutomationsResponse>(result).jobs }
            .getOrElse { throw GatewayException("$action response is invalid") }
    }

    private suspend fun requestMutation(action: String, payload: JsonObject): JsonObject? {
        val requestId = UUID.randomUUID().toString()
        val waiter = CompletableDeferred<InboundEvent.WebUiResponse>()
        pendingRequests[requestId] = waiter
        try {
            sendRaw(Outbound.webUiRequest(requestId, action, payload))
            val response = withTimeout(MUTATION_TIMEOUT_MS) { waiter.await() }
            if (!response.ok) {
                throw GatewayException(
                    response.error?.message ?: "gateway rejected $action",
                    response.error?.status,
                )
            }
            return response.result
        } catch (timeout: TimeoutCancellationException) {
            throw GatewayException("gateway did not answer $action in time")
        } finally {
            pendingRequests.remove(requestId)
        }
    }

    private fun sendRaw(frame: String) {
        val ws = socket ?: throw GatewayException("gateway connection is not open")
        if (!ws.send(frame)) throw GatewayException("failed to send frame to gateway")
    }

    private fun failPending(error: GatewayException) {
        for (key in pendingAcceptance.keys.toList()) {
            pendingAcceptance.remove(key)?.completeExceptionally(error)
        }
        for (key in pendingRequests.keys.toList()) {
            pendingRequests.remove(key)?.completeExceptionally(error)
        }
    }

    private fun backoffMs(attempt: Int): Long {
        val steps = longArrayOf(500, 1_000, 2_000, 4_000, 8_000, 16_000, 30_000)
        return steps[min(attempt - 1, steps.lastIndex)] + Random.nextLong(0, 500)
    }

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private fun encodeQuery(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private data class HttpResult(val code: Int, val body: String)

    private companion object {
        const val READY_TIMEOUT_MS = 15_000L
        const val ATTACH_TIMEOUT_MS = 15_000L
        const val ACCEPT_TIMEOUT_MS = 30_000L
        const val MUTATION_TIMEOUT_MS = 30_000L
        const val MAX_MEDIA_BYTES = 32L * 1024 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}

/** The gateway's fixed video budget; unlike the per-file budget it is not advertised. */
private const val MAX_VIDEO_ATTACHMENT_BYTES = 20L * 1024 * 1024

internal fun attachmentLimit(mime: String, limits: AttachmentLimits?): Long =
    if (mime.startsWith("video/")) {
        MAX_VIDEO_ATTACHMENT_BYTES
    } else {
        limits?.maxFileBytes?.takeIf { it > 0 } ?: AttachmentLimits.DEFAULT_MAX_FILE_BYTES
    }

/** Pulls the human-readable message out of a gateway error body, falling back to the raw text. */
internal fun attachmentErrorDetail(body: String): String {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return ""
    val message = runCatching {
        (NanobotJson.parseToJsonElement(trimmed) as? JsonObject)
            ?.get("error")
            ?.let { (it as? JsonPrimitive)?.content }
    }.getOrNull()
    return (message?.takeIf { it.isNotBlank() } ?: trimmed).take(300)
}

/**
 * Uploads [bytes] in chunks and reports how much OkHttp has written, so the composer can show a
 * real percentage instead of an indefinite spinner. Called on OkHttp's I/O thread.
 */
private class ProgressRequestBody(
    private val bytes: ByteArray,
    private val mediaType: MediaType?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val total = bytes.size
        if (total == 0) {
            onProgress(1f)
            return
        }
        var offset = 0
        var lastPercent = -1
        while (offset < total) {
            val chunk = min(UPLOAD_CHUNK_BYTES, total - offset)
            sink.write(bytes, offset, chunk)
            offset += chunk
            val percent = offset * 100 / total
            if (percent != lastPercent) {
                lastPercent = percent
                onProgress(offset.toFloat() / total)
            }
        }
        sink.flush()
    }

    private companion object {
        const val UPLOAD_CHUNK_BYTES = 64 * 1024
    }
}
