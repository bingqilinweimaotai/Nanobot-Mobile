package dev.nanobot.android

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.nanobot.android.data.DraftEntity
import dev.nanobot.android.data.NanobotDatabase
import dev.nanobot.android.data.SessionEntity
import dev.nanobot.android.data.ThreadCacheEntity
import dev.nanobot.android.host.HostProfile
import dev.nanobot.gateway.GatewayState
import dev.nanobot.protocol.AutomationUpdate
import dev.nanobot.protocol.GatewaySettings
import dev.nanobot.protocol.InboundEvent
import dev.nanobot.protocol.McpOAuthFlowPayload
import dev.nanobot.protocol.McpPresetsResponse
import dev.nanobot.protocol.MediaRef
import dev.nanobot.protocol.NanobotJson
import dev.nanobot.protocol.SessionAutomationSummary
import dev.nanobot.protocol.SessionSummary
import dev.nanobot.protocol.SidebarState
import dev.nanobot.protocol.ThreadPage
import dev.nanobot.protocol.ThreadPageParser
import dev.nanobot.protocol.WorkspaceDirectoriesResponse
import dev.nanobot.protocol.WorkspaceScope
import dev.nanobot.tunnel.PairingLinks
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = NanobotDatabase.get(application).dao()
    private val preferences = SettingsStore(application)
    private val mediaCache = MediaCache(application)

    /** In-flight media downloads keyed by remote URL, so a recomposition cannot start a second. */
    private val mediaLoads = ConcurrentHashMap<String, Job>()

    /** URLs that failed this connection; retried after the next `ready`. */
    private val failedMedia = ConcurrentHashMap.newKeySet<String>()

    /** Keeps a history screen from opening one relay stream per image at once. */
    private val mediaSemaphore = Semaphore(2)

    private val _ui = MutableStateFlow(
        UiState(
            configured = GatewayRepository.configured,
            endpoint = GatewayRepository.endpoint,
            chatId = GatewayRepository.lastChatId,
            collapsedGroups = GatewayRepository.collapsedGroups,
            hosts = GatewayRepository.hosts(),
            currentHostId = GatewayRepository.currentHostId(),
            themeMode = preferences.themeMode,
            accentTheme = preferences.accentTheme,
            customAccentHue = preferences.customAccentHue,
            language = preferences.language,
            fontSize = preferences.fontSize,
            turnNotifications = preferences.turnNotifications,
            notificationSound = preferences.notificationSound,
        ),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var eventJob: Job? = null
    private var stateJob: Job? = null
    private var updateJob: Job? = null
    private var tunnelJob: Job? = null
    private var searchJob: Job? = null
    private var searchGeneration = 0

    /** Uploads still in flight, keyed by the composer attachment id, so removing one can cancel it. */
    private val uploadJobs = ConcurrentHashMap<String, Job>()

    /** Serializes sidebar document writes so two quick taps cannot overwrite each other. */
    private val sidebarMutex = Mutex()

    /** Serializes feature refreshes, so a reconnect-triggered reload is never dropped. */
    private val featuresMutex = Mutex()

    /** Last sidebar document fetched from or saved to the gateway; unknown fields are preserved. */
    private var sidebarRaw: JsonObject? = null

    /** True while this client is saving, so its own broadcast echo does not replace newer edits. */
    private var sidebarWriteInFlight = false

    /** Bumped for every local sidebar write; older reads must not overwrite a newer edit. */
    private var sidebarWriteGeneration = 0

    /** Server timestamp of the newest sidebar document applied, used to reject stale broadcasts. */
    private var sidebarUpdatedAt: String? = null

    /**
     * Optimistic sidebar rows for chats the server has not listed yet, keyed by chat id. They keep
     * a just-started conversation visible (and keep it from vanishing on a chat switch) until the
     * gateway's next session list confirms it.
     */
    private val localSessions = mutableMapOf<String, SessionSummary>()

    /** Turn whose events should still be applied to the transcript. */
    private var activeTurnId: String? = null

    /** Bubble receiving reasoning chunks; reasoning frames arrive without a gateway stream id. */
    private val reasoningIds = ReasoningBubbleIds()

    /** Increments on every thread load so an older, slower fetch cannot overwrite a newer one. */
    private var threadLoadGeneration = 0

    /**
     * A turn the user paused locally. Its late frames are ignored until the gateway reports the
     * turn ended; an empty id matches any turn for gateways that omit `turn_id`.
     */
    private var pausedTurnId: String? = null

    /** Streams already frozen by a pause, so a late delta cannot recreate a stopped answer. */
    private val frozenStreams = mutableSetOf<String>()

    /** Local turn ids of the `/stop` frames we send; their command replies are not transcript. */
    private val stopTurnIds = mutableSetOf<String>()

    /** Optimistic model switches, keyed by the silent `/model` turn, so failures can revert. */
    private val modelSwitchRollbacks = mutableMapOf<String, ModelSwitchRollback>()

    /** A refresh requested while one was in flight runs again with the newer gateway state. */
    private var settingsRefreshQueued = false

    init {
        AppLocale.language = preferences.language
        viewModelScope.launch {
            PairingLaunch.links.collect { link ->
                if (link != null) {
                    addHostFromLink(link)
                    PairingLaunch.consume()
                }
            }
        }
        DebugLaunch.chatId?.let { chatId ->
            GatewayRepository.lastChatId = chatId
            _ui.update { it.copy(chatId = chatId) }
            DebugLaunch.chatId = null
        }
        val debugDraft = DebugLaunch.draft
        DebugLaunch.draft = null
        val debugEndpoint = DebugLaunch.endpoint
        if (!debugEndpoint.isNullOrBlank()) {
            val debugSecret = DebugLaunch.secret.orEmpty()
            DebugLaunch.endpoint = null
            DebugLaunch.secret = null
            connect(debugEndpoint, debugSecret)
            debugDraft?.let { draft -> _ui.update { it.copy(draft = draft) } }
        } else {
            collectFlows()
            if (GatewayRepository.configured) {
                GatewayRepository.connectConfigured()
                loadCachedSessions()
                loadFeatures()
                syncFromRepository()
                debugDraft?.let { draft -> _ui.update { it.copy(draft = draft) } }
            }
        }
    }

    private fun syncFromRepository() {
        if (GatewayRepository.state.value !is GatewayState.Ready) return
        val preferred = GatewayRepository.lastChatId
        val active = GatewayRepository.chatId
        val target = preferred ?: active ?: return
        _ui.update { it.copy(chatId = target) }
        if (active != null && active != target) {
            viewModelScope.launch {
                runCatching { GatewayRepository.attach(target) }
                    .onFailure { error -> _ui.update { it.copy(error = error.message) } }
                loadThread(target)
            }
        } else {
            loadThread(target)
        }
        refreshSessions()
        loadFeatures()
    }

    fun connect(endpoint: String, secret: String) {
        addDirectHost("", endpoint, secret)
    }

    /**
     * Parses a pasted or scanned pairing link, saves the host, and connects it.
     * Returns false and publishes [UiState.pairingError] when the link cannot be used.
     */
    fun addHostFromLink(link: String): Boolean {
        val offer = runCatching { PairingLinks.parse(link) }.getOrElse { error ->
            _ui.update { it.copy(pairingError = error.message ?: AppLocale.strings.invalidPairingLink) }
            return false
        }
        val host = runCatching { GatewayRepository.savePairing(offer) }.getOrElse { error ->
            _ui.update { it.copy(pairingError = error.message ?: AppLocale.strings.couldNotSaveHost) }
            return false
        } ?: run {
            _ui.update { it.copy(pairingError = AppLocale.strings.couldNotSaveHost) }
            return false
        }
        enterHost(host)
        return true
    }

    /** Adds a LAN or manually forwarded gateway. */
    fun addDirectHost(name: String, endpoint: String, secret: String): Boolean {
        val normalized = normalizeEndpoint(endpoint)
        if (normalized.isBlank()) {
            _ui.update { it.copy(pairingError = AppLocale.strings.enterGatewayAddress) }
            return false
        }
        val host = runCatching {
            GatewayRepository.saveDirectHost(name, normalized, secret)
        }.getOrElse { error ->
            _ui.update { it.copy(pairingError = error.message ?: AppLocale.strings.couldNotSaveHost) }
            return false
        } ?: run {
            _ui.update { it.copy(pairingError = AppLocale.strings.couldNotSaveHost) }
            return false
        }
        enterHost(host)
        return true
    }

    fun selectHost(id: String): Boolean {
        val host = GatewayRepository.hosts().firstOrNull { it.id == id } ?: return false
        if (id == _ui.value.currentHostId) {
            GatewayRepository.reconnect()
            return true
        }
        runCatching { GatewayRepository.connectHost(host) }.getOrElse { error ->
            _ui.update { it.copy(error = error.message ?: AppLocale.strings.couldNotConnectHost) }
            return false
        }
        enterHost(host)
        return true
    }

    fun removeHost(id: String) {
        val wasCurrent = id == _ui.value.currentHostId
        GatewayRepository.removeHost(id)
        val next = GatewayRepository.currentHost()
        if (next == null) {
            eventJob?.cancel()
            stateJob?.cancel()
            tunnelJob?.cancel()
            eventJob = null
            stateJob = null
            tunnelJob = null
            localSessions.clear()
            _ui.value = UiState()
            return
        }
        if (wasCurrent) {
            enterHost(next)
        } else {
            _ui.update {
                it.copy(
                    hosts = GatewayRepository.hosts(),
                    currentHostId = GatewayRepository.currentHostId(),
                    configured = GatewayRepository.configured,
                    endpoint = GatewayRepository.endpoint,
                )
            }
        }
    }

    fun clearPairingError() {
        _ui.update { it.copy(pairingError = null) }
    }

    private fun enterHost(host: HostProfile) {
        resetTurnTracking()
        clearSearchResults()
        sidebarRaw = null
        sidebarUpdatedAt = null
        localSessions.clear()
        _ui.update {
            it.copy(
                configured = true,
                endpoint = GatewayRepository.endpoint,
                hosts = GatewayRepository.hosts(),
                currentHostId = host.id,
                connection = GatewayState.Connecting,
                chatId = host.lastChatId,
                sessions = emptyList(),
                runningChatIds = emptySet(),
                unreadChatIds = emptySet(),
                messages = emptyList(),
                draft = "",
                attachments = emptyList(),
                attachedCliApps = emptySet(),
                attachedMcpPresets = emptySet(),
                workspaces = null,
                pendingSearchTarget = null,
                streaming = false,
                turnActive = false,
                modelPreset = null,
                modelName = "",
                contextTokens = null,
                contextWindowTokens = 0,
                error = null,
                pairingError = null,
            )
        }
        collectFlows()
        loadFeatures()
        syncFromRepository()
    }

    fun disconnect() {
        GatewayRepository.disconnect()
    }

    fun resetConfiguration() {
        val current = _ui.value.currentHostId
        if (current != null) {
            removeHost(current)
        } else {
            eventJob?.cancel()
            stateJob?.cancel()
            tunnelJob?.cancel()
            localSessions.clear()
            GatewayRepository.reset()
            _ui.value = UiState()
        }
    }

    fun setDraft(value: String) {
        _ui.update { it.copy(draft = value) }
        val chatId = _ui.value.chatId ?: return
        viewModelScope.launch {
            if (value.isBlank()) {
                dao.deleteDraft(chatId)
            } else {
                dao.upsertDraft(DraftEntity(chatId, value, System.currentTimeMillis()))
            }
        }
    }

    fun attach(uri: Uri) {
        val id = UUID.randomUUID().toString()
        val chatId = _ui.value.chatId
        val job = viewModelScope.launch {
            try {
                val name = withContext(Dispatchers.IO) { GatewayRepository.displayName(uri) }
                val mime = withContext(Dispatchers.IO) { GatewayRepository.mimeType(uri) }
                val size = withContext(Dispatchers.IO) { GatewayRepository.sizeBytes(uri) }
                // Show the tile immediately so the upload is visible from the first byte. If the
                // user moved to another chat meanwhile, leave that composer alone.
                _ui.update { current ->
                    if (current.chatId != chatId) {
                        current
                    } else {
                        current.copy(
                            attachments = current.attachments +
                                ComposerAttachment(id, uri, name, mime, size),
                            error = null,
                        )
                    }
                }
                val prepared = GatewayRepository.prepareAttachment(uri, mime)
                // The picker URI is only readable for this process. Keep our own copy so the
                // thumbnail survives a restart and can be found again after a host re-add.
                val staged = withContext(Dispatchers.IO) { mediaCache.stage(prepared.bytes, name) }
                _ui.update { current ->
                    current.copy(
                        attachments = current.attachments.map {
                            if (it.id == id) {
                                it.copy(
                                    sizeBytes = prepared.bytes.size.toLong(),
                                    mime = prepared.mime,
                                    uri = staged?.let { file -> Uri.fromFile(file) } ?: it.uri,
                                )
                            } else {
                                it
                            }
                        },
                    )
                }
                val onProgress: (Float) -> Unit =
                    if (GatewayRepository.uploadProgressIsReal()) {
                        { progress ->
                            _ui.update { current ->
                                current.copy(
                                    attachments = current.attachments.map {
                                        if (it.id == id && it.state == AttachmentState.Uploading) {
                                            it.copy(progress = progress.coerceIn(0f, 1f))
                                        } else {
                                            it
                                        }
                                    },
                                )
                            }
                        }
                    } else {
                        // Relay uploads buffer locally, so only an indeterminate state is honest.
                        {}
                    }
                val reference = GatewayRepository.uploadAttachment(name, prepared.mime, prepared.bytes, onProgress)
                _ui.update { current ->
                    current.copy(
                        attachments = current.attachments.map {
                            if (it.id == id) {
                                it.copy(
                                    state = AttachmentState.Uploaded,
                                    progress = 1f,
                                    reference = reference.reference,
                                    error = null,
                                )
                            } else {
                                it
                            }
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { current ->
                    current.copy(
                        attachments = current.attachments.map {
                            if (it.id == id) {
                                it.copy(
                                    state = AttachmentState.Failed,
                                    error = error.message ?: AppLocale.strings.attachmentUploadFailed,
                                )
                            } else {
                                it
                            }
                        },
                    )
                }
            } finally {
                uploadJobs.remove(id)
            }
        }
        uploadJobs[id] = job
    }

    fun removeAttachment(id: String) {
        uploadJobs.remove(id)?.cancel()
        mediaCache.delete(_ui.value.attachments.firstOrNull { it.id == id }?.uri)
        _ui.update { current ->
            current.copy(attachments = current.attachments.filterNot { it.id == id })
        }
    }

    fun toggleCliApp(name: String) {
        _ui.update { current ->
            val next = current.attachedCliApps.toMutableSet()
            if (!next.add(name)) next.remove(name)
            current.copy(attachedCliApps = next)
        }
    }

    fun toggleMcpPreset(name: String) {
        _ui.update { current ->
            val next = current.attachedMcpPresets.toMutableSet()
            if (!next.add(name)) next.remove(name)
            current.copy(attachedMcpPresets = next)
        }
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.themeMode = mode
        _ui.update { it.copy(themeMode = mode) }
    }

    fun setAccentTheme(accent: AccentTheme) {
        preferences.accentTheme = accent
        _ui.update { it.copy(accentTheme = accent) }
    }

    fun setCustomAccentHue(hue: Float) {
        preferences.customAccentHue = hue
        _ui.update { it.copy(customAccentHue = hue) }
    }

    fun setLanguage(language: AppLanguage) {
        preferences.language = language
        AppLocale.language = language
        _ui.update { it.copy(language = language) }
    }

    fun setFontSize(size: AppFontSize) {
        preferences.fontSize = size
        _ui.update { it.copy(fontSize = size) }
    }

    fun setTurnNotifications(enabled: Boolean) {
        preferences.turnNotifications = enabled
        _ui.update { it.copy(turnNotifications = enabled) }
    }

    fun setNotificationSound(enabled: Boolean) {
        preferences.notificationSound = enabled
        _ui.update { it.copy(notificationSound = enabled) }
    }

    /** Reconnects the current host, matching the WebUI's "restart/reconnect" affordance. */
    fun reconnect() {
        GatewayRepository.reconnect()
    }

    /** Loads the read-only gateway summary shown on the settings page. */
    fun refreshGatewaySettings() {
        if (_ui.value.settingsLoading) {
            settingsRefreshQueued = true
            return
        }
        if (_ui.value.connection !is GatewayState.Ready) {
            _ui.update {
                it.copy(
                    gatewaySettings = null,
                    settingsLoading = false,
                    settingsError = AppLocale.strings.notConnectedToGateway,
                )
            }
            return
        }
        _ui.update { it.copy(settingsLoading = true, settingsError = null) }
        viewModelScope.launch {
            runCatching { GatewayRepository.fetchSettings() }
                .onSuccess { settings ->
                    _ui.update {
                        it.copy(gatewaySettings = settings, settingsLoading = false)
                    }
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            settingsLoading = false,
                            settingsError = error.message ?: AppLocale.strings.couldNotLoadGatewaySettings,
                        )
                    }
                }
            if (settingsRefreshQueued) {
                settingsRefreshQueued = false
                refreshGatewaySettings()
            }
        }
    }

    fun clearSettingsMessage() {
        _ui.update { it.copy(settingsMessage = null) }
    }

    /**
     * Checks the public GitHub release feed for a newer phone build. Called when the About page
     * opens; a second call while one is in flight is ignored.
     */
    fun checkForUpdates() {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            _ui.update { it.copy(updateStatus = UpdateStatus.Checking) }
            val current = currentVersionName()
            runCatching {
                withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() }
            }
                .onSuccess { release ->
                    _ui.update {
                        it.copy(
                            updateStatus = when {
                                release == null -> UpdateStatus.NoRelease
                                isNewerVersion(release.version, current) -> UpdateStatus.Available(
                                    version = release.version,
                                    downloadUrl = release.downloadUrl ?: release.releaseUrl,
                                    releaseUrl = release.releaseUrl,
                                )

                                else -> UpdateStatus.UpToDate(release.version)
                            },
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _ui.update {
                        it.copy(
                            updateStatus = UpdateStatus.Failed(error.message.orEmpty()),
                        )
                    }
                }
        }
    }

    private fun currentVersionName(): String = runCatching {
        val packageName = getApplication<Application>().packageName
        @Suppress("DEPRECATION")
        getApplication<Application>().packageManager
            .getPackageInfo(packageName, 0)
            .versionName
    }.getOrNull().orEmpty()

    /**
     * Applies the WebUI's `settings.agent.update`: the default model, provider, context window,
     * timezone, and active preset. The gateway returns the canonical settings payload.
     */
    fun updateAgentSettings(
        modelPreset: String? = null,
        model: String? = null,
        provider: String? = null,
        contextWindowTokens: Int? = null,
        timezone: String? = null,
    ) {
        val payload = buildJsonObject {
            modelPreset?.takeIf { it.isNotBlank() }?.let { put("model_preset", it) }
            model?.takeIf { it.isNotBlank() }?.let { put("model", it) }
            provider?.takeIf { it.isNotBlank() }?.let { put("provider", it) }
            contextWindowTokens?.takeIf { it > 0 }?.let { put("context_window_tokens", it) }
            timezone?.takeIf { it.isNotBlank() }?.let { put("timezone", it) }
        }
        if (payload.isEmpty()) return
        mutateSettings { GatewayRepository.updateAgentSettings(payload) }
    }

    /**
     * Saves the selected model preset and makes it the active one. Named presets use
     * `model_configuration.update`; the built-in `default` preset only exposes the agent-level
     * fields the WebUI also allows. Activation is part of the same mutation because the settings
     * page presents the selection as "the current model", and a save that only edited an inactive
     * preset would report success without changing what the gateway actually runs.
     */
    fun saveModelConfiguration(
        name: String,
        model: String,
        provider: String,
        maxTokens: Int? = null,
        contextWindowTokens: Int? = null,
        temperature: Double? = null,
        reasoningEffort: String? = null,
        timezone: String? = null,
    ) {
        if (name == DEFAULT_MODEL_PRESET) {
            updateAgentSettings(
                modelPreset = DEFAULT_MODEL_PRESET,
                model = model,
                provider = provider,
                contextWindowTokens = contextWindowTokens,
                timezone = timezone,
            )
            return
        }
        val payload = buildJsonObject {
            put("name", name)
            put("model", model)
            put("provider", provider)
            maxTokens?.takeIf { it > 0 }?.let { put("max_tokens", it) }
            contextWindowTokens?.takeIf { it > 0 }?.let { put("context_window_tokens", it) }
            temperature?.let { put("temperature", it) }
            reasoningEffort?.let { put("reasoning_effort", it) }
        }
        mutateSettings {
            var updated = GatewayRepository.updateModelConfiguration(payload)
            if (timezone != null && timezone != updated.agent.timezone) {
                updated = GatewayRepository.updateAgentSettings(
                    buildJsonObject { put("timezone", timezone) },
                )
            }
            if (updated.agent.modelPreset.orEmpty() != name) {
                updated = GatewayRepository.updateAgentSettings(
                    buildJsonObject { put("model_preset", name) },
                )
            }
            updated
        }
    }

    fun createModelConfiguration(
        name: String,
        model: String,
        provider: String,
        maxTokens: Int? = null,
        contextWindowTokens: Int? = null,
        temperature: Double? = null,
        reasoningEffort: String? = null,
    ) {
        val payload = buildJsonObject {
            put("name", name)
            put("model", model)
            put("provider", provider)
            maxTokens?.takeIf { it > 0 }?.let { put("max_tokens", it) }
            contextWindowTokens?.takeIf { it > 0 }?.let { put("context_window_tokens", it) }
            temperature?.let { put("temperature", it) }
            reasoningEffort?.let { put("reasoning_effort", it) }
        }
        mutateSettings { GatewayRepository.createModelConfiguration(payload) }
    }

    fun renameModelConfiguration(name: String, newName: String) {
        if (name.isBlank() || newName.isBlank() || name == newName) return
        mutateSettings {
            GatewayRepository.updateModelConfiguration(
                buildJsonObject {
                    put("name", name)
                    put("new_name", newName)
                },
            )
        }
    }

    fun deleteModelConfiguration(name: String) {
        if (name.isBlank()) return
        mutateSettings { GatewayRepository.deleteModelConfiguration(name) }
    }

    /** Makes [name] the primary model preset, matching the WebUI's preset activation. */
    fun setActiveModelPreset(name: String) {
        if (name.isBlank()) return
        mutateSettings {
            GatewayRepository.updateAgentSettings(
                buildJsonObject { put("model_preset", name) },
            )
        }
    }

    /**
     * Adds or removes a preset from the WebUI's model call order. Removing the last enabled
     * preset is rejected locally because the gateway requires at least one.
     */
    fun setModelPresetEnabled(name: String, enabled: Boolean) {
        val settings = _ui.value.gatewaySettings ?: return
        val current = settings.modelCallOrder.filter { it.isNotBlank() }
        val next = if (enabled) {
            if (name in current) return
            current + name
        } else {
            current - name
        }
        if (next.isEmpty()) {
            _ui.update { it.copy(settingsMessage = AppLocale.strings.keepOneModelPreset) }
            return
        }
        mutateSettings { GatewayRepository.updateModelCallOrder(next) }
    }

    /** Mirrors the WebUI's `agents.defaults.unified_session` runtime toggle. */
    fun setUnifiedSession(enabled: Boolean) {
        mutateSettings {
            GatewayRepository.updateRuntimeConfig(
                buildJsonObject { put("agents.defaults.unified_session", enabled) },
            )
        }
    }

    private fun mutateSettings(block: suspend () -> GatewaySettings) {
        if (_ui.value.settingsSaving) return
        _ui.update { it.copy(settingsSaving = true, settingsMessage = null, settingsError = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { settings ->
                    _ui.update {
                        it.copy(
                            gatewaySettings = settings,
                            settingsSaving = false,
                            settingsMessage = AppLocale.strings.settingsSaved,
                        )
                    }
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            settingsSaving = false,
                            settingsMessage = error.message
                                ?: AppLocale.strings.couldNotSaveSettings,
                        )
                    }
                }
        }
    }

    /** Refreshes only the capability lists shown by the settings drill-down pages. */
    fun refreshCapabilities() {
        if (_ui.value.loadingFeatures) return
        _ui.update { it.copy(loadingFeatures = true) }
        viewModelScope.launch {
            val current = _ui.value
            val skills = runCatching { GatewayRepository.fetchSkills() }
                .getOrDefault(current.skills)
            val cliApps = runCatching { GatewayRepository.fetchCliApps() }
                .getOrDefault(current.cliApps)
            val mcp = runCatching { GatewayRepository.fetchMcpPresets() }.getOrNull()
            val automations = runCatching { GatewayRepository.fetchAutomations() }
                .getOrDefault(current.automations)
            _ui.update {
                it.copy(
                    skills = skills,
                    cliApps = cliApps,
                    mcpPresets = mcp?.presets ?: current.mcpPresets,
                    mcpProxyConfigured = mcp?.proxyConfigured ?: current.mcpProxyConfigured,
                    mcpInstalledCount = mcp?.installedCount ?: current.mcpInstalledCount,
                    mcpRequiresRestart = mcp?.requiresRestart ?: current.mcpRequiresRestart,
                    automations = automations,
                    loadingFeatures = false,
                )
            }
        }
    }

    fun setSkillEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { GatewayRepository.setSkillEnabled(name, enabled) }
                .onSuccess {
                    _ui.update { current ->
                        current.copy(
                            skills = current.skills.map {
                                if (it.name == name) it.copy(enabled = enabled) else it
                            },
                            skillDetail = current.skillDetail?.let {
                                if (it.name == name) it.copy(enabled = enabled) else it
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _ui.update { it.copy(settingsMessage = error.message ?: AppLocale.strings.couldNotUpdateSkills) }
                }
        }
    }

    /** Loads the detail sheet for [name]; a cached copy is shown while [force] refreshes it. */
    fun loadSkillDetail(name: String, force: Boolean = false) {
        val current = _ui.value
        if (!force && current.skillDetailName == name && current.skillDetail != null) return
        _ui.update {
            it.copy(
                skillDetail = it.skillDetail?.takeIf { detail -> detail.name == name },
                skillDetailName = name,
                skillDetailLoading = true,
                skillDetailError = null,
            )
        }
        viewModelScope.launch {
            runCatching { GatewayRepository.fetchSkillDetail(name) }
                .onSuccess { detail ->
                    _ui.update { state ->
                        if (state.skillDetailName == name) {
                            state.copy(skillDetail = detail, skillDetailLoading = false)
                        } else {
                            state
                        }
                    }
                }
                .onFailure { error ->
                    _ui.update { state ->
                        if (state.skillDetailName == name) {
                            state.copy(
                                skillDetailLoading = false,
                                skillDetailError = error.message
                                    ?: AppLocale.strings.couldNotLoadSkillDetail,
                            )
                        } else {
                            state
                        }
                    }
                }
        }
    }

    fun clearSkillDetail() {
        _ui.update {
            it.copy(
                skillDetail = null,
                skillDetailName = null,
                skillDetailLoading = false,
                skillDetailError = null,
            )
        }
    }

    fun deleteSkill(name: String) {
        viewModelScope.launch {
            runCatching { GatewayRepository.deleteSkill(name) }
                .onSuccess {
                    _ui.update { current ->
                        current.copy(
                            skills = current.skills.filterNot { it.name == name },
                            skillDetail = current.skillDetail?.takeIf { it.name != name },
                            settingsMessage = AppLocale.strings.skillDeletedNamed(name),
                        )
                    }
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            settingsMessage = error.message
                                ?: AppLocale.strings.couldNotDeleteSkill,
                        )
                    }
                }
        }
    }

    fun setMcpFieldValue(preset: String, field: String, value: String) {
        _ui.update { current ->
            val fields = current.mcpFieldValues[preset].orEmpty().toMutableMap()
            fields[field] = value
            current.copy(mcpFieldValues = current.mcpFieldValues + (preset to fields))
        }
    }

    /**
     * Runs one of the WebUI's MCP actions. Credential drafts are read synchronously before the
     * request starts, so a field edit immediately followed by Connect carries the new value.
     */
    fun runMcpAction(action: String, name: String) {
        val values = _ui.value.mcpFieldValues[name].orEmpty()
        _ui.update { it.copy(mcpAction = "$action:$name", settingsMessage = null) }
        viewModelScope.launch {
            runCatching { GatewayRepository.mcpPresetAction(action, name, values) }
                .onSuccess { payload ->
                    applyMcpPayload(payload)
                    if (action == "enable" && payload.lastAction?.ok != false) {
                        _ui.update { it.copy(mcpFieldValues = it.mcpFieldValues - name) }
                    }
                    if (action == "remove") {
                        _ui.update {
                            it.copy(
                                mcpFieldValues = it.mcpFieldValues - name,
                                mcpOAuthFlow = it.mcpOAuthFlow?.takeIf { flow -> flow.name != name },
                            )
                        }
                    }
                    if (action == "proxy" && payload.lastAction?.ok != false) {
                        _ui.update { current ->
                            val fields = current.mcpFieldValues[name].orEmpty() - "use_proxy"
                            current.copy(
                                mcpFieldValues = if (fields.isEmpty()) {
                                    current.mcpFieldValues - name
                                } else {
                                    current.mcpFieldValues + (name to fields)
                                },
                            )
                        }
                    }
                }
                .onFailure { error -> failMcpAction(error) }
        }
    }

    fun saveMcpServer(form: McpCustomForm) {
        val values = form.toValues()
        val key = "custom:${form.name.ifBlank { "new" }}"
        _ui.update { it.copy(mcpAction = key, settingsMessage = null) }
        viewModelScope.launch {
            runCatching { GatewayRepository.saveCustomMcpServer(values) }
                .onSuccess { payload ->
                    applyMcpPayload(payload)
                    if (payload.lastAction?.ok != false) {
                        _ui.update { it.copy(mcpFieldValues = it.mcpFieldValues - values["name"].orEmpty()) }
                    }
                }
                .onFailure { error -> failMcpAction(error) }
        }
    }

    fun importMcpConfig(config: String) {
        _ui.update { it.copy(mcpAction = "import", settingsMessage = null) }
        viewModelScope.launch {
            runCatching { GatewayRepository.importMcpConfig(config) }
                .onSuccess { applyMcpPayload(it) }
                .onFailure { error -> failMcpAction(error) }
        }
    }

    fun updateMcpTools(name: String, enabledTools: List<String>) {
        _ui.update { it.copy(mcpAction = "tools:$name", settingsMessage = null) }
        viewModelScope.launch {
            runCatching { GatewayRepository.updateMcpTools(name, enabledTools) }
                .onSuccess { applyMcpPayload(it) }
                .onFailure { error -> failMcpAction(error) }
        }
    }

    fun startMcpOAuth(name: String, reset: Boolean = false) {
        val values = _ui.value.mcpFieldValues[name].orEmpty()
        mcpOAuthJob?.cancel()
        _ui.update {
            it.copy(
                mcpAction = "oauth:$name",
                mcpOAuthFlow = null,
                mcpOAuthCallbackError = null,
                settingsMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { GatewayRepository.startMcpOAuth(name, values, reset) }
                .onSuccess { flow ->
                    _ui.update { it.copy(mcpOAuthFlow = flow) }
                    monitorMcpOAuth(flow)
                }
                .onFailure { error -> failMcpAction(error) }
        }
    }

    fun completeMcpOAuth(callbackUrl: String) {
        val flow = _ui.value.mcpOAuthFlow ?: return
        _ui.update { it.copy(mcpOAuthCallbackError = null) }
        viewModelScope.launch {
            runCatching { GatewayRepository.completeMcpOAuth(flow.flowId, callbackUrl) }
                .onSuccess { next ->
                    if (_ui.value.mcpOAuthFlow?.flowId != flow.flowId) return@onSuccess
                    _ui.update { it.copy(mcpOAuthFlow = next) }
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            mcpOAuthCallbackError = error.message
                                ?: AppLocale.strings.mcpOAuthCallbackFailed,
                        )
                    }
                }
        }
    }

    fun cancelMcpOAuth() {
        val flow = _ui.value.mcpOAuthFlow ?: return
        mcpOAuthJob?.cancel()
        _ui.update {
            it.copy(
                mcpOAuthFlow = null,
                mcpOAuthCallbackError = null,
                mcpAction = null,
                settingsMessage = AppLocale.strings.mcpOAuthCancelled,
            )
        }
        viewModelScope.launch {
            runCatching { GatewayRepository.cancelMcpOAuth(flow.flowId) }
                .onFailure { error ->
                    _ui.update {
                        it.copy(settingsMessage = error.message ?: AppLocale.strings.couldNotUpdateMcp)
                    }
                }
        }
    }

    private var mcpOAuthJob: Job? = null

    private fun monitorMcpOAuth(initial: McpOAuthFlowPayload) {
        mcpOAuthJob?.cancel()
        mcpOAuthJob = viewModelScope.launch {
            var current = initial
            while (true) {
                if (current.terminal) {
                    finishMcpOAuth(current)
                    return@launch
                }
                delay(800)
                if (_ui.value.mcpOAuthFlow?.flowId != current.flowId) return@launch
                current = runCatching { GatewayRepository.fetchMcpOAuthStatus(current.flowId) }
                    .getOrElse { error ->
                        _ui.update {
                            it.copy(
                                mcpOAuthFlow = null,
                                mcpAction = null,
                                settingsMessage = error.message
                                    ?: AppLocale.strings.mcpOAuthCallbackFailed,
                            )
                        }
                        return@launch
                    }
                if (_ui.value.mcpOAuthFlow?.flowId != current.flowId) return@launch
                _ui.update { it.copy(mcpOAuthFlow = current) }
            }
        }
    }

    private suspend fun finishMcpOAuth(flow: McpOAuthFlowPayload) {
        val payload = runCatching { GatewayRepository.fetchMcpPresets() }.getOrNull()
        val connected = flow.status == "connected" ||
            (flow.status == "authorized" && flow.hotReload?.ok == true)
        _ui.update { current ->
            current.copy(
                mcpPresets = payload?.presets ?: current.mcpPresets,
                mcpProxyConfigured = payload?.proxyConfigured ?: current.mcpProxyConfigured,
                mcpInstalledCount = payload?.installedCount ?: current.mcpInstalledCount,
                mcpRequiresRestart = payload?.requiresRestart ?: current.mcpRequiresRestart,
                mcpOAuthFlow = null,
                mcpAction = null,
                settingsMessage = when {
                    connected -> AppLocale.strings.mcpOAuthConnected
                    flow.status == "cancelled" -> AppLocale.strings.mcpOAuthCancelled
                    else -> flow.error ?: flow.hotReload?.message
                        ?: AppLocale.strings.mcpOAuthFailed
                },
            )
        }
    }

    private val McpOAuthFlowPayload.terminal: Boolean
        get() = status == "connected" || status == "failed" || status == "cancelled" ||
            (status == "authorized" && hotReload != null)

    private fun applyMcpPayload(payload: McpPresetsResponse) {
        val action = payload.lastAction
        val hotReload = payload.hotReload
        val error = when {
            action?.ok == false -> action.error ?: action.message
            hotReload?.ok == false -> hotReload.message
            else -> null
        }
        _ui.update { current ->
            current.copy(
                mcpPresets = payload.presets,
                mcpProxyConfigured = payload.proxyConfigured,
                mcpInstalledCount = payload.installedCount,
                mcpRequiresRestart = payload.requiresRestart,
                mcpAction = null,
                settingsMessage = error
                    ?: action?.message?.takeIf { it.isNotBlank() }
                    ?: AppLocale.strings.mcpSettingsUpdated,
            )
        }
    }

    private fun failMcpAction(error: Throwable) {
        _ui.update {
            it.copy(
                mcpAction = null,
                settingsMessage = error.message ?: AppLocale.strings.couldNotUpdateMcp,
            )
        }
    }

    fun setAutomationEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { GatewayRepository.setAutomationEnabled(id, enabled) }
                .onSuccess {
                    _ui.update { current ->
                        current.copy(
                            automations = current.automations.map {
                                if (it.id == id) it.copy(enabled = enabled) else it
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _ui.update { it.copy(settingsMessage = error.message ?: AppLocale.strings.couldNotUpdateAutomation) }
                }
        }
    }

    fun deleteAutomation(id: String) {
        viewModelScope.launch {
            runCatching { GatewayRepository.deleteAutomation(id) }
                .onSuccess {
                    _ui.update { current ->
                        current.copy(automations = current.automations.filterNot { it.id == id })
                    }
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            settingsMessage = error.message
                                ?: AppLocale.strings.couldNotDeleteAutomation,
                        )
                    }
                }
        }
    }

    fun runAutomation(id: String) {
        viewModelScope.launch {
            val name = _ui.value.automations.firstOrNull { it.id == id }?.name.orEmpty()
            runCatching { GatewayRepository.runAutomation(id) }
                .onSuccess {
                    _ui.update {
                        it.copy(
                            settingsMessage = if (name.isBlank()) {
                                AppLocale.strings.startedAutomation
                            } else {
                                AppLocale.strings.startedAutomationNamed(name)
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _ui.update { it.copy(settingsMessage = error.message ?: AppLocale.strings.couldNotRunAutomation) }
                }
        }
    }

    /** Saves the WebUI's edit dialog fields; the gateway replies with the refreshed list. */
    fun updateAutomation(id: String, update: AutomationUpdate) {
        val key = "update:$id"
        _ui.update {
            it.copy(
                automationAction = key,
                automationActionError = null,
                automationActionDone = null,
            )
        }
        viewModelScope.launch {
            runCatching { GatewayRepository.updateAutomation(id, update) }
                .onSuccess { jobs ->
                    _ui.update {
                        it.copy(
                            automations = jobs,
                            automationAction = null,
                            automationActionDone = key,
                            settingsMessage = AppLocale.strings.automationSaved,
                        )
                    }
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            automationAction = null,
                            automationActionError = automationUpdateError(error),
                        )
                    }
                }
        }
    }

    private fun automationUpdateError(error: Throwable): String = when (error.message) {
        "name cannot be empty" -> AppLocale.strings.automationNameRequired
        "message cannot be empty" -> AppLocale.strings.automationMessageRequired
        "every schedule requires positive every_ms" -> AppLocale.strings.automationIntervalRequired
        "cron schedule requires expr" -> AppLocale.strings.automationCronRequired
        "cron schedule is invalid" -> AppLocale.strings.automationCronInvalid
        "one-time schedule must be in the future" -> AppLocale.strings.automationFutureRequired
        else -> error.message ?: AppLocale.strings.couldNotUpdateAutomation
    }

    /** Loads the chat picker for one automation; [UiState.automationChatsFor] guards stale replies. */
    fun loadAutomationChats(id: String) {
        if (_ui.value.automationChatsLoading && _ui.value.automationChatsFor == id) return
        _ui.update {
            it.copy(
                automationChatsFor = id,
                automationChats = null,
                automationChatsLoading = true,
                automationChatsError = null,
            )
        }
        viewModelScope.launch {
            runCatching { GatewayRepository.fetchAutomationChats(id) }
                .onSuccess { payload ->
                    _ui.update { current ->
                        if (current.automationChatsFor != id) {
                            current
                        } else {
                            current.copy(
                                automationChats = payload,
                                automationChatsLoading = false,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _ui.update { current ->
                        if (current.automationChatsFor != id) {
                            current
                        } else {
                            current.copy(
                                automationChatsLoading = false,
                                automationChatsError = error.message
                                    ?: AppLocale.strings.couldNotLoadAutomationChats,
                            )
                        }
                    }
                }
        }
    }

    /** Moves a scheduled task to another chat, carrying the picker revision as the WebUI does. */
    fun changeAutomationChat(id: String, targetId: String, revision: String, message: String) {
        val key = "chat:$id"
        val target = _ui.value.automationChats?.chats?.firstOrNull { it.id == targetId }
        _ui.update {
            it.copy(
                automationAction = key,
                automationActionError = null,
                automationActionDone = null,
            )
        }
        viewModelScope.launch {
            runCatching { GatewayRepository.changeAutomationChat(id, targetId, revision, message) }
                .onSuccess { jobs ->
                    _ui.update {
                        it.copy(
                            automations = jobs,
                            automationAction = null,
                            automationActionDone = key,
                            settingsMessage = AppLocale.strings.automationChatChanged(
                                target?.title.orEmpty(),
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            automationAction = null,
                            automationActionError = automationChatError(error),
                        )
                    }
                }
        }
    }

    private fun automationChatError(error: Throwable): String = when (error.message) {
        "automation_chat_conflict" -> AppLocale.strings.automationChatConflict
        "automation_chat_busy" -> AppLocale.strings.automationChatBusy
        "automation_chat_unavailable" -> AppLocale.strings.automationChatUnavailable
        else -> error.message ?: AppLocale.strings.couldNotChangeAutomationChat
    }

    /** Clears a finished edit/move result after the editor has closed itself. */
    fun clearAutomationAction() {
        _ui.update {
            it.copy(
                automationAction = null,
                automationActionError = null,
                automationActionDone = null,
            )
        }
    }

    fun clearAutomationChats() {
        _ui.update {
            it.copy(
                automationChatsFor = null,
                automationChats = null,
                automationChatsLoading = false,
                automationChatsError = null,
            )
        }
    }

    fun send() {
        val state = _ui.value
        if (state.turnActive) return
        val chatId = state.chatId ?: return
        val text = state.draft.trim()
        if (state.attachmentsUploading) {
            _ui.update { it.copy(error = "Wait for attachments to finish uploading") }
            return
        }
        val media = state.uploadedAttachments
        if (text.isEmpty() && media.isEmpty()) return
        if (state.connection !is GatewayState.Ready) {
            _ui.update { it.copy(error = "gateway is not connected") }
            return
        }

        val turnId = UUID.randomUUID().toString()
        val messageId = "user-$turnId"
        // Keep the local URIs on the optimistic bubble so the sent message can show thumbnails
        // immediately; the gateway's echo only carries remote URLs.
        val messageAttachments = state.attachments
            .filter { it.state == AttachmentState.Uploaded }
            .map { MessageAttachment(it.name, it.mime, it.uri) }
        activeTurnId = turnId
        // A new turn must never inherit the previous turn's reasoning bubble.
        reasoningIds.close()
        // A conversation started in this session may not exist on the server yet. Keep an
        // optimistic row so the sidebar shows it at once and a chat switch cannot drop it.
        if (state.sessions.none { it.chatId == chatId }) {
            val now = Instant.now().toString()
            localSessions[chatId] = SessionSummary(
                key = "websocket:$chatId",
                preview = text,
                createdAt = now,
                updatedAt = now,
                workspaceScope = state.workspaceScope,
            )
        }
        _ui.update { current ->
            current.copy(
                draft = "",
                error = null,
                turnActive = true,
                runningChatIds = current.runningChatIds + chatId,
                unreadChatIds = current.unreadChatIds - chatId,
                sessions = mergeSidebarSessions(current.sessions, localSessions.values),
                messages = current.messages + ChatMessage(
                    id = messageId,
                    role = MessageRole.User,
                    text = text,
                    attachments = messageAttachments,
                    pending = true,
                ),
            )
        }

        viewModelScope.launch {
            try {
                GatewayRepository.sendMessage(
                    chatId = chatId,
                    text = text,
                    turnId = turnId,
                    media = media,
                    cliApps = state.attachedCliApps.toList(),
                    mcpPresets = state.attachedMcpPresets.toList(),
                    workspaceScope = state.workspaceScope,
                )
                GatewayRepository.lastChatId = chatId
                dao.deleteDraft(chatId)
                _ui.update { current ->
                    current.copy(
                        attachments = emptyList(),
                        attachedCliApps = emptySet(),
                        attachedMcpPresets = emptySet(),
                        messages = current.messages.map {
                            if (it.id == messageId) it.copy(pending = false) else it
                        },
                    )
                }
            } catch (error: Exception) {
                if (activeTurnId == turnId) activeTurnId = null
                if (pausedTurnId == turnId) pausedTurnId = null
                _ui.update { current ->
                    current.copy(
                        draft = if (current.draft.isBlank()) text else current.draft,
                        attachments = state.attachments,
                        attachedCliApps = state.attachedCliApps,
                        attachedMcpPresets = state.attachedMcpPresets,
                        error = error.message ?: AppLocale.strings.couldNotSend,
                        turnActive = false,
                        messages = current.messages.map {
                            if (it.id == messageId) it.copy(pending = false, failed = true) else it
                        },
                    )
                }
            }
        }
    }

    /**
     * Switches the open chat's model preset with the same silent `/model <preset>` command the
     * WebUI sends. The command's user echo, reply, and turn end stay out of the transcript; the
     * gateway's `turn_model_updated` event confirms the runtime that actually ran.
     */
    fun selectModelPreset(name: String) {
        val presetName = name.trim()
        if (presetName.isEmpty()) return
        val state = _ui.value
        val chatId = state.chatId ?: return
        if (state.connection !is GatewayState.Ready) {
            _ui.update { it.copy(error = AppLocale.strings.notConnectedToGateway) }
            return
        }
        if (state.modelPreset.orEmpty() == presetName) return
        val preset = state.gatewaySettings?.modelPresets
            ?.firstOrNull { it.name == presetName }
        val previousPreset = state.modelPreset
        val previousWindow = state.contextWindowTokens
        _ui.update {
            it.copy(
                modelPreset = presetName,
                contextWindowTokens = preset?.contextWindowTokens
                    ?.takeIf { tokens -> tokens > 0 }
                    ?: it.contextWindowTokens,
                error = null,
            )
        }
        val turnId = SYSTEM_TURN_PREFIX + UUID.randomUUID()
        modelSwitchRollbacks[turnId] =
            ModelSwitchRollback(previousPreset, previousWindow, presetName)
        viewModelScope.launch {
            runCatching {
                GatewayRepository.sendMessage(
                    chatId,
                    "/model $presetName",
                    turnId,
                    media = emptyList(),
                    cliApps = emptyList(),
                    mcpPresets = emptyList(),
                )
            }
                .onFailure { error ->
                    modelSwitchRollbacks.remove(turnId)
                    // Another switch may have superseded this one while it was in flight.
                    _ui.update { current ->
                        if (current.modelPreset.orEmpty() != presetName) {
                            current
                        } else {
                            current.copy(
                                modelPreset = previousPreset,
                                contextWindowTokens = previousWindow,
                                error = error.message ?: AppLocale.strings.couldNotSwitchModel,
                            )
                        }
                    }
                }
        }
    }

    /**
     * Freezes the running turn in place and asks the gateway to cancel it, matching the WebUI's
     * stop button: local state settles first, then `/stop` cancels the server-side task.
     */
    fun pauseTurn() {
        val state = _ui.value
        if (!state.turnActive && !state.streaming) return
        frozenStreams += state.messages.filter { it.streaming }.mapNotNull(::streamIdOf)
        pausedTurnId = activeTurnId ?: ""
        activeTurnId = null
        reasoningIds.close()
        _ui.update { current ->
            current.copy(
                turnActive = false,
                streaming = false,
                runningChatIds = current.chatId
                    ?.let { current.runningChatIds - it }
                    ?: current.runningChatIds,
                messages = current.messages.map {
                    if (it.streaming) it.copy(streaming = false, stopped = true) else it
                },
            )
        }

        val chatId = state.chatId ?: return
        val stopTurnId = STOP_TURN_PREFIX + UUID.randomUUID()
        stopTurnIds += stopTurnId
        viewModelScope.launch {
            runCatching { GatewayRepository.sendStop(chatId, stopTurnId) }
                .onFailure { stopTurnIds -= stopTurnId }
            // The reply may never name this turn; forget the id so the set cannot grow forever.
            delay(30_000)
            stopTurnIds -= stopTurnId
        }
    }

    fun newChat() {
        resetTurnTracking()
        viewModelScope.launch {
            try {
                val chatId = GatewayRepository.newChat()
                GatewayRepository.lastChatId = chatId
                _ui.update {
                    it.copy(
                        chatId = chatId,
                        messages = emptyList(),
                        draft = "",
                        attachments = emptyList(),
                        attachedCliApps = emptySet(),
                        attachedMcpPresets = emptySet(),
                        pendingSearchTarget = null,
                        streaming = false,
                        turnActive = false,
                        modelPreset = null,
                        modelName = "",
                        contextTokens = null,
                        contextWindowTokens = 0,
                        error = null,
                    )
                }
                refreshSessions()
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: AppLocale.strings.couldNotCreateChat) }
            }
        }
    }

    fun toggleGroup(groupId: String) {
        val collapsed = _ui.value.collapsedGroups.toMutableSet().apply {
            if (!remove(groupId)) add(groupId)
        }.toSet()
        GatewayRepository.collapsedGroups = collapsed
        _ui.update { it.copy(collapsedGroups = collapsed) }
        viewModelScope.launch {
            sidebarMutex.withLock {
                val current = sidebarRaw ?: runCatching {
                    GatewayRepository.fetchSidebarState()
                }.getOrNull()?.also(::applySidebarState)?.raw ?: return@withLock
                val next = current.withCollapsedGroup(groupId, groupId in collapsed)
                sidebarWriteGeneration += 1
                sidebarWriteInFlight = true
                try {
                    runCatching { GatewayRepository.updateSidebarState(next) }
                        .onSuccess(::applySidebarState)
                } finally {
                    sidebarWriteInFlight = false
                }
            }
        }
    }

    fun togglePin(key: String) {
        viewModelScope.launch {
            updateSidebar { raw ->
                val pinned = raw.stringSet("pinned_keys")
                if (!pinned.remove(key)) pinned.add(key)
                raw.withStringList("pinned_keys", pinned)
            }
        }
    }

    fun setArchived(keys: List<String>, archived: Boolean) {
        val selected = keys.filter { it.isNotBlank() }.toSet()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val saved = updateSidebar { raw ->
                val next = raw.stringSet("archived_keys")
                val pinned = raw.stringSet("pinned_keys")
                if (archived) {
                    next.addAll(selected)
                    pinned.removeAll(selected)
                } else {
                    next.removeAll(selected)
                }
                raw.withStringList("archived_keys", next).withStringList("pinned_keys", pinned)
            }
            if (saved && archived) moveAwayFrom(selected)
        }
    }

    fun toggleShowArchived() {
        val show = !_ui.value.showArchived
        viewModelScope.launch {
            updateSidebar { raw -> raw.withShowArchived(show) }
        }
    }

    fun renameSession(key: String, title: String) {
        val cleaned = title.trim().take(160)
        viewModelScope.launch {
            updateSidebar { raw ->
                val titles = raw.stringMap("title_overrides")
                if (cleaned.isEmpty()) titles.remove(key) else titles[key] = cleaned
                raw.withStringMap("title_overrides", titles)
            }
        }
    }

    /** Deletes conversations exactly like the WebUI, reporting automation blockers for a retry. */
    internal suspend fun deleteSessions(
        keys: List<String>,
        deleteAutomations: Boolean = false,
    ): DeleteSessionsResult {
        val unique = keys.filter { it.isNotBlank() }.distinct()
        if (unique.isEmpty()) return DeleteSessionsResult.Deleted(emptyList())
        val deleted = mutableListOf<String>()
        val deletedChatIds = mutableSetOf<String>()

        for (key in unique) {
            val session = _ui.value.sessions.firstOrNull { it.key == key }
            try {
                val result = GatewayRepository.deleteSession(key, deleteAutomations)
                if (result.blockedByAutomations) {
                    val remaining = unique.drop(deleted.size)
                    return DeleteSessionsResult.Blocked(remaining, result.automations)
                }
                deleted += key
                session?.chatId?.takeIf { it.isNotBlank() }?.let { chatId ->
                    deletedChatIds += chatId
                    localSessions.remove(chatId)
                    runCatching { dao.deleteDraft(chatId) }
                    runCatching { dao.deleteThread(chatId) }
                }
            } catch (error: Exception) {
                val message = error.message ?: AppLocale.strings.couldNotDelete
                _ui.update { it.copy(error = message) }
                return DeleteSessionsResult.Failed(message)
            }
        }

        if (deleted.isNotEmpty()) {
            val deletedKeys = deleted.toSet()
            _ui.update { current ->
                current.copy(
                    sessions = current.sessions.filterNot { it.key in deletedKeys },
                    runningChatIds = current.runningChatIds - deletedChatIds,
                    unreadChatIds = current.unreadChatIds - deletedChatIds,
                )
            }
            if (_ui.value.chatId in deletedChatIds) {
                val next = _ui.value.sessions.firstOrNull()
                if (next != null) selectSession(next) else clearActiveChat()
            }
            pruneSidebarKeys(deletedKeys)
            refreshSessions()
        }
        return DeleteSessionsResult.Deleted(deleted)
    }

    private fun moveAwayFrom(keys: Set<String>) {
        val state = _ui.value
        val activeKey = state.sessions.firstOrNull { it.chatId == state.chatId }?.key ?: return
        if (activeKey !in keys) return
        val next = state.sessions.firstOrNull { it.key !in keys }
        if (next != null) selectSession(next) else clearActiveChat()
    }

    private fun clearActiveChat() {
        GatewayRepository.lastChatId = null
        resetTurnTracking()
        _ui.update {
            it.copy(
                chatId = null,
                messages = emptyList(),
                draft = "",
                attachments = emptyList(),
                attachedCliApps = emptySet(),
                attachedMcpPresets = emptySet(),
                pendingSearchTarget = null,
                streaming = false,
                turnActive = false,
                loadingThread = false,
                error = null,
            )
        }
    }

    /** Removes sidebar metadata for deleted conversations; open WebUIs get the same broadcast. */
    private fun pruneSidebarKeys(keys: Set<String>) {
        if (sidebarRaw == null) return
        viewModelScope.launch {
            updateSidebar { raw ->
                val pinned = raw.stringSet("pinned_keys").apply { removeAll(keys) }
                val archived = raw.stringSet("archived_keys").apply { removeAll(keys) }
                val titles = raw.stringMap("title_overrides").apply { keys.forEach { remove(it) } }
                val order = raw.stringList("session_order").filterNot { it in keys }
                raw.withStringList("pinned_keys", pinned)
                    .withStringList("archived_keys", archived)
                    .withStringMap("title_overrides", titles)
                    .withStringList("session_order", order)
            }
        }
    }

    /**
     * Applies a local change to the sidebar document and saves it before keeping the result. The
     * gateway replaces the whole document, so [sidebarRaw] is the base rather than typed fields.
     */
    private suspend fun updateSidebar(block: (JsonObject) -> JsonObject): Boolean {
        sidebarMutex.withLock {
            val current = sidebarRaw ?: runCatching { GatewayRepository.fetchSidebarState() }
                .getOrNull()?.also(::applySidebarState)?.raw
            if (current == null) {
                _ui.update { it.copy(error = AppLocale.strings.sidebarNotLoaded) }
                return false
            }
            val before = sidebarRaw
            val next = block(current)
            sidebarWriteGeneration += 1
            sidebarWriteInFlight = true
            applySidebarJson(next)
            try {
                val saved = runCatching { GatewayRepository.updateSidebarState(next) }
                return saved.fold(
                    onSuccess = {
                        applySidebarState(it)
                        true
                    },
                    onFailure = { error ->
                        before?.let(::applySidebarJson)
                        _ui.update {
                            it.copy(error = error.message ?: AppLocale.strings.couldNotUpdateSidebar)
                        }
                        false
                    },
                )
            } finally {
                sidebarWriteInFlight = false
            }
        }
    }

    private fun applySidebarJson(root: JsonObject) {
        runCatching { SidebarState.fromJson(root) }.onSuccess(::applySidebarState)
    }

    private fun applySidebarState(state: SidebarState) {
        val incoming = state.updatedAt
        val known = sidebarUpdatedAt
        // Reads and broadcasts can arrive out of order; an older document must not undo an edit.
        if (incoming != null && known != null && incoming < known) return
        if (incoming != null && (known == null || incoming > known)) {
            sidebarUpdatedAt = incoming
        }
        sidebarRaw = state.raw
        val collapsed = state.collapsedGroups.filterValues { it }.keys
        GatewayRepository.collapsedGroups = collapsed
        _ui.update {
            it.copy(
                titleOverrides = state.titleOverrides,
                projectNameOverrides = state.projectNameOverrides,
                pinnedKeys = state.pinnedKeys.toSet(),
                archivedKeys = state.archivedKeys.toSet(),
                showArchived = state.view.showArchived,
                collapsedGroups = collapsed,
            )
        }
    }

    fun forkMessage(beforeUserIndex: Int) {
        val state = _ui.value
        val sourceChatId = state.chatId ?: return
        val sourceTitle = state.sessions
            .firstOrNull { it.chatId == sourceChatId }
            ?.title
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            try {
                val forkId = GatewayRepository.forkChat(
                    sourceChatId = sourceChatId,
                    beforeUserIndex = beforeUserIndex,
                    title = sourceTitle?.let { AppLocale.strings.forkChatTitle(it) },
                )
                GatewayRepository.lastChatId = forkId
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: AppLocale.strings.couldNotForkChat) }
            }
        }
    }

    fun selectSession(session: SessionSummary) {
        val chatId = session.chatId
        if (chatId.isBlank()) return
        GatewayRepository.lastChatId = chatId
        resetTurnTracking()
        _ui.update {
            it.copy(
                chatId = chatId,
                unreadChatIds = it.unreadChatIds - chatId,
                messages = emptyList(),
                draft = "",
                attachments = emptyList(),
                attachedCliApps = emptySet(),
                attachedMcpPresets = emptySet(),
                workspaceScope = session.workspaceScope ?: it.defaultWorkspaceScope ?: it.workspaceScope,
                pendingSearchTarget = null,
                streaming = false,
                turnActive = false,
                modelPreset = session.modelPreset?.takeIf { preset -> preset.isNotBlank() },
                modelName = "",
                contextTokens = null,
                contextWindowTokens = 0,
            )
        }
        viewModelScope.launch {
            restoreDraft(chatId)
            runCatching { GatewayRepository.attach(chatId) }
                .onFailure { error -> _ui.update { it.copy(error = error.message) } }
            loadThread(chatId)
        }
    }

    fun refreshSessions() {
        if (_ui.value.loadingSessions) return
        _ui.update { it.copy(loadingSessions = true) }
        viewModelScope.launch {
            try {
                val sessions = GatewayRepository.listSessions()
                val serverKeys = sessions.mapTo(HashSet()) { it.key }
                // The server's copy takes over from any optimistic row for the same chat.
                localSessions.keys.removeAll { chatId -> "websocket:$chatId" in serverKeys }
                // A chat with a turn still running must not drop out while the server's list
                // catches up, even if an earlier refresh had already confirmed the chat.
                val snapshot = _ui.value
                snapshot.sessions
                    .filter { it.chatId in snapshot.runningChatIds && it.key !in serverKeys }
                    .forEach { localSessions[it.chatId] = it }
                dao.clearSessions()
                dao.upsertSessions(sessions.map { it.toEntity() })
                _ui.update { current ->
                    val runs = reconcileSidebarRuns(
                        previousSessions = current.sessions,
                        serverSessions = sessions,
                        runningChatIds = current.runningChatIds,
                        unreadChatIds = current.unreadChatIds,
                        currentChatId = current.chatId,
                    )
                    current.copy(
                        sessions = mergeSidebarSessions(sessions, localSessions.values),
                        runningChatIds = runs.runningChatIds,
                        unreadChatIds = runs.unreadChatIds,
                        loadingSessions = false,
                    )
                }
            } catch (error: Exception) {
                val cached = runCatching { dao.sessions() }.getOrDefault(emptyList())
                _ui.update {
                    it.copy(
                        sessions = if (it.sessions.isEmpty()) cached.map { row -> row.toSummary() } else it.sessions,
                        loadingSessions = false,
                        error = if (cached.isEmpty()) error.message else null,
                    )
                }
            }
        }
    }

    /**
     * Refreshes the lists behind `/`, `$`, and `@`. A reload runs on every reconnect, and the
     * tunnel can drop one of these GETs; a failed fetch keeps the last good list instead of
     * blanking the composer's autocomplete until the next reconnect succeeds.
     */
    fun loadFeatures() {
        viewModelScope.launch {
            featuresMutex.withLock {
                _ui.update { it.copy(loadingFeatures = true) }
                val commands = runCatching { GatewayRepository.fetchCommands() }.getOrNull()
                val skills = runCatching { GatewayRepository.fetchSkills() }.getOrNull()
                val cliApps = runCatching { GatewayRepository.fetchCliApps() }.getOrNull()
                val mcp = runCatching { GatewayRepository.fetchMcpPresets() }.getOrNull()
                val automations = runCatching { GatewayRepository.fetchAutomations() }.getOrNull()
                val workspaces = runCatching { GatewayRepository.fetchWorkspaces() }.getOrNull()
                val sidebarGeneration = sidebarWriteGeneration
                val sidebar = runCatching { GatewayRepository.fetchSidebarState() }.getOrNull()
                val currentSidebar = sidebar?.takeIf { sidebarGeneration == sidebarWriteGeneration }
                _ui.update { current ->
                    val sessionScope = current.sessions
                        .firstOrNull { it.chatId == current.chatId }
                        ?.workspaceScope
                    current.copy(
                        commands = commands ?: current.commands,
                        skills = skills ?: current.skills,
                        cliApps = cliApps ?: current.cliApps,
                        mcpPresets = mcp?.presets ?: current.mcpPresets,
                        mcpProxyConfigured = mcp?.proxyConfigured ?: current.mcpProxyConfigured,
                        mcpInstalledCount = mcp?.installedCount ?: current.mcpInstalledCount,
                        mcpRequiresRestart = mcp?.requiresRestart ?: current.mcpRequiresRestart,
                        automations = automations ?: current.automations,
                        loadingFeatures = false,
                        workspaces = workspaces ?: current.workspaces,
                        hostName = workspaces?.host?.name ?: current.hostName,
                        hostPlatform = workspaces?.host?.platform ?: current.hostPlatform,
                        defaultWorkspaceScope = workspaces?.defaultScope ?: current.defaultWorkspaceScope,
                        workspaceScope = current.workspaceScope ?: sessionScope ?: workspaces?.defaultScope,
                    )
                }
                if (!sidebarWriteInFlight) currentSidebar?.let(::applySidebarState)
            }
        }
    }

    fun setAccessMode(mode: String) {
        _ui.update { current ->
            val scope = current.workspaceScope ?: current.defaultWorkspaceScope
                ?: return@update current
            current.copy(workspaceScope = scope.copy(accessMode = mode))
        }
    }

    /** Reloads the folders the gateway's WebUI offers in its workspace picker. */
    fun refreshWorkspaces() {
        viewModelScope.launch {
            val workspaces = runCatching { GatewayRepository.fetchWorkspaces() }.getOrNull()
                ?: return@launch
            _ui.update {
                it.copy(
                    workspaces = workspaces,
                    hostName = workspaces.host?.name ?: it.hostName,
                    hostPlatform = workspaces.host?.platform ?: it.hostPlatform,
                    defaultWorkspaceScope = workspaces.defaultScope ?: it.defaultWorkspaceScope,
                    workspaceScope = it.workspaceScope ?: workspaces.defaultScope,
                )
            }
        }
    }

    /** Lists folders under [path] for the workspace picker. Throws when the gateway is offline. */
    suspend fun browseWorkspace(path: String): WorkspaceDirectoriesResponse =
        GatewayRepository.fetchWorkspaceDirectories(path)

    /**
     * Opens a new chat bound to [projectPath]. The gateway remembers the folder as a recent
     * project, exactly like choosing it in the WebUI, so both pickers stay in sync.
     */
    fun addWorkspace(projectPath: String, projectName: String?) {
        val path = projectPath.trim()
        if (path.isBlank()) return
        val base = _ui.value.workspaceScope ?: _ui.value.defaultWorkspaceScope ?: WorkspaceScope()
        val scope = base.copy(
            projectPath = path,
            projectName = projectName?.trim()?.takeIf { it.isNotEmpty() }
                ?: projectNameFromPath(path),
        )
        resetTurnTracking()
        // Remember the folder as a recent project on the host, but never block opening the chat on
        // it: older gateways may not support the workspace mutations.
        if (_ui.value.workspaces?.controls?.canResolveProject == true) {
            viewModelScope.launch {
                runCatching { GatewayRepository.resolveWorkspaceProject(path) }
            }
        }
        viewModelScope.launch {
            try {
                val chatId = GatewayRepository.newChat()
                GatewayRepository.lastChatId = chatId
                _ui.update {
                    it.copy(
                        chatId = chatId,
                        messages = emptyList(),
                        draft = "",
                        attachments = emptyList(),
                        attachedCliApps = emptySet(),
                        attachedMcpPresets = emptySet(),
                        workspaceScope = scope,
                        streaming = false,
                        turnActive = false,
                        loadingThread = false,
                        error = null,
                    )
                }
                refreshSessions()
            } catch (error: Exception) {
                _ui.update {
                    it.copy(error = error.message ?: AppLocale.strings.couldNotOpenWorkspace)
                }
            }
        }
    }

    /**
     * Searches titles, previews, and the transcripts already cached on this device. The current
     * chat uses its live messages so an answer that is still streaming is searchable too.
     */
    fun searchConversations(query: String) {
        searchJob?.cancel()
        val needle = query.trim()
        val generation = ++searchGeneration
        if (needle.isEmpty()) {
            _ui.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        val snapshot = _ui.value
        _ui.update { it.copy(searching = true) }
        searchJob = viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                runCatching { dao.threads() }.getOrDefault(emptyList())
            }
            val transcripts = withContext(Dispatchers.Default) {
                cached.mapNotNull { row ->
                    parseCached(row.raw)?.events?.toMessages()?.let { row.chatId to it }
                }.toMap()
            }
            val results = withContext(Dispatchers.Default) {
                searchConversationHits(
                    query = needle,
                    sessions = snapshot.sessions,
                    titleOverrides = snapshot.titleOverrides,
                    cachedMessages = transcripts,
                    liveChatId = snapshot.chatId,
                    liveMessages = snapshot.messages,
                )
            }
            if (generation != searchGeneration) return@launch
            _ui.update { it.copy(searchResults = results, searching = false) }
        }
    }

    /** Clears the result list without touching a transcript scroll that is still pending. */
    fun clearSearchResults() {
        searchJob?.cancel()
        searchGeneration += 1
        _ui.update { it.copy(searchResults = emptyList(), searching = false) }
    }

    /** Opens a search result and, when a message matched, reveals that bubble after loading. */
    fun openSearchHit(hit: ConversationSearchHit) {
        val state = _ui.value
        val session = state.sessions.firstOrNull { it.chatId == hit.chatId }
        val target = hit.scrollTarget
        val switching = session != null && hit.chatId != state.chatId
        if (switching) {
            selectSession(session)
        }
        // Keep the target alive while the transcript loads; otherwise a chat switch would emit an
        // empty, not-loading state and the scroll request would be dropped.
        _ui.update {
            it.copy(
                pendingSearchTarget = target,
                loadingThread = it.loadingThread || switching,
                unreadChatIds = it.unreadChatIds - hit.chatId,
            )
        }
    }

    fun clearSearchScrollTarget() {
        _ui.update { it.copy(pendingSearchTarget = null) }
    }

    private fun collectFlows() {
        eventJob?.cancel()
        stateJob?.cancel()
        tunnelJob?.cancel()
        eventJob = viewModelScope.launch { GatewayRepository.events.collect(::handleEvent) }
        stateJob = viewModelScope.launch {
            GatewayRepository.state.collect { state ->
                // A fresh connection may have a different base URL (host re-add), so let failed
                // images try once more.
                if (state is GatewayState.Ready) failedMedia.clear()
                _ui.update {
                    it.copy(
                        connection = state,
                        mediaEpoch = if (state is GatewayState.Ready) {
                            it.mediaEpoch + 1
                        } else {
                            it.mediaEpoch
                        },
                    )
                }
            }
        }
        tunnelJob = viewModelScope.launch {
            GatewayRepository.tunnelState.collect { tunnel ->
                _ui.update { it.copy(tunnelState = tunnel) }
            }
        }
    }

    private fun loadCachedSessions() {
        viewModelScope.launch {
            val rows = runCatching { dao.sessions() }.getOrDefault(emptyList())
            if (rows.isNotEmpty() && _ui.value.sessions.isEmpty()) {
                _ui.update { it.copy(sessions = rows.map { row -> row.toSummary() }) }
            }
        }
    }

    private fun loadThread(chatId: String) {
        val generation = ++threadLoadGeneration
        resetTurnTracking()
        _ui.update {
            it.copy(
                loadingThread = true,
                streaming = false,
                turnActive = false,
                contextTokens = null,
                contextWindowTokens = 0,
            )
        }
        viewModelScope.launch {
            try {
                val page = GatewayRepository.fetchThread(chatId)
                page.raw?.let { raw ->
                    dao.upsertThread(ThreadCacheEntity(chatId, raw, System.currentTimeMillis()))
                }
                applyLoadedThread(chatId, page, generation)
            } catch (error: Exception) {
                val cached = runCatching { dao.thread(chatId) }.getOrNull()
                val page = cached?.raw?.let(::parseCached)
                if (page != null) {
                    applyLoadedThread(chatId, page, generation)
                } else if (isLatestThreadLoad(chatId, generation)) {
                    _ui.update { it.copy(loadingThread = false, error = error.message) }
                }
            }
        }
    }

    /**
     * Applies a fetched transcript only while its chat is still on screen, and only for the newest
     * load. Two fast session switches can finish out of order; an older response must not replace
     * the newer chat.
     */
    private suspend fun applyLoadedThread(chatId: String, page: ThreadPage, generation: Int) {
        val running = page.activeTurnId != null || page.hasPendingToolCalls ||
            chatId in _ui.value.runningChatIds
        val messages = withContext(Dispatchers.IO) { page.events.toMessages() }
            .let { if (running) it.markRunningReasoning() else it }
        if (!isLatestThreadLoad(chatId, generation)) return
        if (running && page.activeTurnId != null) activeTurnId = page.activeTurnId
        val (contextTokens, contextWindow) = page.events.latestContextUsage()
        _ui.update {
            it.copy(
                messages = messages,
                loadingThread = false,
                turnActive = running,
                contextTokens = contextTokens,
                contextWindowTokens = contextWindow
                    .takeIf { tokens -> tokens > 0 }
                    ?: it.contextWindowTokens,
            )
        }
    }

    /** The newest completed turn that reported context tokens, like the WebUI's composer ring. */
    private fun List<InboundEvent>.latestContextUsage(): Pair<Long?, Int> {
        var windowTokens = 0
        for (event in asReversed()) {
            if (event !is InboundEvent.TurnEnd) continue
            if (windowTokens <= 0 && event.contextWindowTokens > 0) {
                windowTokens = event.contextWindowTokens
            }
            val tokens = event.usage?.contextTokens
            if (tokens != null && tokens >= 0) return tokens to windowTokens
        }
        return null to windowTokens
    }

    private fun isLatestThreadLoad(chatId: String, generation: Int): Boolean =
        generation == threadLoadGeneration && _ui.value.chatId == chatId


    private suspend fun restoreDraft(chatId: String) {
        val draft = runCatching { dao.draft(chatId) }.getOrNull()
        _ui.update {
            if (it.draft.isBlank()) it.copy(draft = draft?.text.orEmpty()) else it
        }
    }

    private fun parseCached(raw: String): ThreadPage? =
        runCatching {
            ThreadPageParser.parse(NanobotJson.parseToJsonElement(raw) as JsonObject)
        }.getOrNull()

    private fun resetTurnTracking() {
        activeTurnId = null
        pausedTurnId = null
        reasoningIds.close()
        frozenStreams.clear()
        stopTurnIds.clear()
    }

    /** Closes whichever turn [turnId] names; a null id means the gateway's only running turn. */
    private fun finishTurn(turnId: String?) {
        // A turn end for the running turn, for the only turn, or for a turn we are not tracking
        // all mean no thought is still being written, so the next reasoning chunk starts fresh.
        if (turnId == null || activeTurnId == null || activeTurnId == turnId) {
            activeTurnId = null
            reasoningIds.close()
        }
        if (turnId == null || pausedTurnId == turnId) pausedTurnId = null
    }

    /** The stream id a local assistant/reasoning bubble was built from. */
    private fun streamIdOf(message: ChatMessage): String? {
        val prefix = when {
            message.id.startsWith(ASSISTANT_MESSAGE_PREFIX) -> ASSISTANT_MESSAGE_PREFIX
            message.id.startsWith(REASONING_MESSAGE_PREFIX) -> REASONING_MESSAGE_PREFIX
            else -> return null
        }
        return message.id.removePrefix(prefix).takeIf { it.isNotBlank() }
    }

    /** A paused turn still accepts nothing: no new text and no late final answer. */
    private fun shouldIgnoreStream(streamId: String?): Boolean {
        val key = streamId ?: CURRENT_STREAM_ID
        if (key in frozenStreams) return true
        return pausedTurnId != null && activeTurnId == null
    }

    /**
     * True when [turnId] belongs to a paused turn. An empty paused id matches every turn because
     * some gateways omit `turn_id`; a null [turnId] is treated the same way.
     */
    private fun isPausedTurn(turnId: String?): Boolean {
        val paused = pausedTurnId ?: return false
        return paused.isEmpty() || turnId == null || paused == turnId
    }

    /** True for the reply to a `/stop` frame we sent, which is command output, not a reply. */
    private fun isStopReply(turnId: String?): Boolean {
        if (turnId == null) return false
        return stopTurnIds.remove(turnId)
    }

    /** The gateway treats this prefix as a silent WebUI system-command turn. */
    private fun isSystemTurn(turnId: String?): Boolean =
        turnId != null && turnId.startsWith(SYSTEM_TURN_PREFIX)

    /** Turn id carried by a transcript frame, if it has one. */
    private fun InboundEvent.turnIdOrNull(): String? = when (this) {
        is InboundEvent.UserMessage -> turnId
        is InboundEvent.Message -> turnId
        is InboundEvent.MessageAccepted -> turnId.takeIf { it.isNotBlank() }
        is InboundEvent.TurnEnd -> turnId
        is InboundEvent.ErrorEvent -> turnId
        else -> null
    }

    /** Keeps a silent `/model` turn out of the transcript while still learning from its outcome. */
    private fun handleSystemTurnEvent(event: InboundEvent) {
        when (event) {
            is InboundEvent.TurnEnd -> modelSwitchRollbacks.remove(event.turnId)

            is InboundEvent.ErrorEvent -> {
                val rollback = modelSwitchRollbacks.remove(event.turnId)
                val message = event.reason ?: event.detail ?: AppLocale.strings.couldNotSwitchModel
                _ui.update { current ->
                    if (rollback != null && current.modelPreset.orEmpty() == rollback.requested) {
                        current.copy(
                            modelPreset = rollback.previousPreset,
                            contextWindowTokens = rollback.previousWindow,
                            error = message,
                        )
                    } else {
                        current.copy(error = message)
                    }
                }
            }

            else -> Unit
        }
    }

    /** The gateway's canned answer to a `/stop` frame. */
    private fun isStopCommandReply(text: String): Boolean =
        text == "No active task to stop." || STOP_REPLY_PATTERN.matches(text.trim())

    /** The chat a transcript frame belongs to; null for connection- or sidebar-level frames. */
    private fun InboundEvent.transcriptChatId(): String? = when (this) {
        is InboundEvent.UserMessage -> chatId
        is InboundEvent.Message -> chatId
        is InboundEvent.Delta -> chatId
        is InboundEvent.StreamEnd -> chatId
        is InboundEvent.ReasoningDelta -> chatId
        is InboundEvent.ReasoningEnd -> chatId
        is InboundEvent.TurnEnd -> chatId
        is InboundEvent.TurnModelUpdated -> chatId
        is InboundEvent.ErrorEvent -> chatId
        is InboundEvent.MessageAccepted -> chatId
        else -> null
    }

    /**
     * The socket keeps delivering the turn that is still running in another chat after the user
     * switches sessions. Painting those frames into the open transcript put one chat's thoughts
     * and reply under another chat's messages, so every transcript frame is scoped by chat id.
     */
    private fun isForCurrentChat(event: InboundEvent): Boolean {
        val chatId = event.transcriptChatId()?.takeIf { it.isNotBlank() } ?: return true
        return _ui.value.chatId == chatId
    }

    /** Keeps the sidebar's run/unread state current without touching the open transcript. */
    private fun handleBackgroundEvent(event: InboundEvent) {
        when (event) {
            is InboundEvent.TurnEnd -> {
                if (isStopReply(event.turnId)) return
                finishTurn(event.turnId)
                _ui.update { current ->
                    current.copy(
                        runningChatIds = current.runningChatIds - event.chatId,
                        unreadChatIds = current.unreadChatIds + event.chatId,
                    )
                }
            }

            is InboundEvent.ErrorEvent -> {
                if (isStopReply(event.turnId)) return
                finishTurn(event.turnId)
                val chatId = event.chatId.orEmpty()
                _ui.update { current ->
                    current.copy(
                        runningChatIds = if (chatId.isBlank()) {
                            current.runningChatIds
                        } else {
                            current.runningChatIds - chatId
                        },
                        unreadChatIds = if (chatId.isBlank()) {
                            current.unreadChatIds
                        } else {
                            current.unreadChatIds + chatId
                        },
                    )
                }
            }

            else -> Unit
        }
    }

    private fun handleEvent(event: InboundEvent) {
        // A `/model` switch is a silent command turn: its echo and reply must never enter the
        // transcript, and it must not flip the composer into the running state.
        if (isSystemTurn(event.turnIdOrNull())) {
            handleSystemTurnEvent(event)
            return
        }
        if (!isForCurrentChat(event)) {
            handleBackgroundEvent(event)
            return
        }
        when (event) {
            is InboundEvent.Ready -> {
                val current = _ui.value
                val target = current.chatId ?: GatewayRepository.lastChatId ?: event.chatId
                _ui.update { it.copy(chatId = target, unreadChatIds = it.unreadChatIds - target) }
                if (target == event.chatId) {
                    loadThread(target)
                    viewModelScope.launch { restoreDraft(target) }
                } else {
                    viewModelScope.launch {
                        runCatching { GatewayRepository.attach(target) }
                            .onFailure { error -> _ui.update { it.copy(error = error.message) } }
                    }
                }
                refreshSessions()
                loadFeatures()
                // The composer's model capsule needs the preset catalog before Settings opens.
                if (_ui.value.gatewaySettings == null) refreshGatewaySettings()
            }

            is InboundEvent.Attached -> {
                GatewayRepository.lastChatId = event.chatId
                _ui.update {
                    it.copy(chatId = event.chatId, unreadChatIds = it.unreadChatIds - event.chatId)
                }
                viewModelScope.launch { restoreDraft(event.chatId) }
                loadThread(event.chatId)
                refreshSessions()
            }

            is InboundEvent.MessageAccepted -> markAccepted(event.turnId)

            is InboundEvent.UserMessage -> {
                if (isStopReply(event.turnId)) return
                if (isPausedTurn(event.turnId)) return
                if (event.text.trim() == "/stop" && pausedTurnId != null) return
                if (event.text.isBlank() && event.mediaUrls.isEmpty()) return
                val id = event.turnId?.let { "user-$it" }
                val existingAttachments = id
                    ?.let { key -> _ui.value.messages.firstOrNull { it.id == key }?.attachments }
                    .orEmpty()
                val echoAttachments = event.mediaUrls.toMessageAttachments()
                // The echo is the first point where a picked file learns its gateway URL. Renaming
                // it now means a later thread reload can find the local copy instead of refetching.
                val adoptedAttachments = if (existingAttachments.isEmpty()) {
                    echoAttachments
                } else {
                    adoptMedia(existingAttachments, event.mediaUrls)
                }
                _ui.update { current ->
                    val existing = id?.let { key -> current.messages.any { it.id == key } } == true
                    if (existing) {
                        current.copy(
                            messages = current.messages.map {
                                if (it.id == id) {
                                    it.copy(
                                        text = event.text,
                                        mediaUrls = event.mediaUrls,
                                        // The local thumbnails are better than the URL-only echo.
                                        attachments = when {
                                            it.attachments.isEmpty() -> echoAttachments
                                            it.attachments == existingAttachments -> adoptedAttachments
                                            else -> it.attachments
                                        },
                                        pending = false,
                                    )
                                } else {
                                    it
                                }
                            },
                        )
                    } else {
                        current.copy(
                            messages = current.messages + ChatMessage(
                                id = id ?: "user-${UUID.randomUUID()}",
                                role = MessageRole.User,
                                text = event.text,
                                mediaUrls = event.mediaUrls,
                                attachments = echoAttachments,
                            ),
                        )
                    }
                }
            }

            is InboundEvent.Delta -> appendDelta(event.streamId ?: "current", event.text)

            is InboundEvent.StreamEnd -> finalizeStreamEnd(event.streamId, event.text)

            is InboundEvent.ReasoningDelta -> appendReasoningDelta(event.streamId, event.text)

            is InboundEvent.ReasoningEnd -> {
                finalizeReasoning(event.streamId, event.text)
                // The next block starts its own bubble even when no text came with the end marker.
                reasoningIds.close()
            }

            is InboundEvent.Message -> {
                if (isStopReply(event.turnId) || isStopCommandReply(event.text)) return
                if (isPausedTurn(event.turnId)) return
                if (event.text.isBlank()) return
                when {
                    event.text == "New session started." -> addSystemMessage(event.text)
                    event.kind == "reasoning" -> finalizeReasoning(null, event.text)
                    event.kind in TOOL_KINDS -> return
                    else -> finalizeAssistant(event.text, event.mediaUrls)
                }
            }

            is InboundEvent.TurnEnd -> {
                if (isStopReply(event.turnId)) return
                finishTurn(event.turnId)
                val chatId = event.chatId.ifBlank { _ui.value.chatId.orEmpty() }
                _ui.update { current ->
                    current.copy(
                        streaming = false,
                        turnActive = activeTurnId != null,
                        runningChatIds = current.runningChatIds - chatId,
                        unreadChatIds = if (chatId.isNotBlank() && chatId != current.chatId) {
                            current.unreadChatIds + chatId
                        } else {
                            current.unreadChatIds - chatId
                        },
                        contextTokens = event.usage?.contextTokens ?: current.contextTokens,
                        contextWindowTokens = event.contextWindowTokens
                            .takeIf { tokens -> tokens > 0 }
                            ?: current.contextWindowTokens,
                        messages = current.messages.map {
                            when {
                                it.pending -> it.copy(pending = false)
                                it.streaming -> it.copy(streaming = false)
                                else -> it
                            }
                        },
                    )
                }
            }

            is InboundEvent.TurnModelUpdated -> {
                _ui.update { current ->
                    current.copy(
                        modelName = event.modelName.takeIf { it.isNotBlank() }
                            ?: current.modelName,
                        // An omitted preset means the gateway's default is active again; store
                        // that explicitly so a stale session row cannot keep an old name.
                        modelPreset = event.modelPreset?.takeIf { it.isNotBlank() }
                            ?: DEFAULT_MODEL_PRESET,
                        contextWindowTokens = event.contextWindowTokens
                            .takeIf { tokens -> tokens > 0 }
                            ?: current.contextWindowTokens,
                    )
                }
            }

            is InboundEvent.RuntimeModelUpdated -> {
                // A WebUI model edit (or config reload) lands on every connection. Re-read the
                // catalog so Settings and the composer stop showing the pre-edit values.
                refreshGatewaySettings()
                val current = _ui.value
                val sessionPreset = current.sessions
                    .firstOrNull { it.chatId == current.chatId }
                    ?.modelPreset
                    ?.takeIf { it.isNotBlank() }
                // A `/model` choice belongs to the session and still wins over the new default.
                if (sessionPreset == null) {
                    val preset = event.modelPreset?.takeIf { it.isNotBlank() }
                        ?: DEFAULT_MODEL_PRESET
                    val modelName = event.modelName.takeIf { it.isNotBlank() }
                        ?: current.modelName
                    if (preset != current.modelPreset.orEmpty() ||
                        modelName != current.modelName
                    ) {
                        _ui.update {
                            it.copy(
                                modelPreset = preset,
                                modelName = modelName,
                                // The old window belonged to the runtime that just changed.
                                contextWindowTokens = 0,
                            )
                        }
                    }
                }
            }

            is InboundEvent.SessionUpdated -> refreshSessions()

            is InboundEvent.SidebarStateUpdated -> {
                val state = event.state ?: return
                if (sidebarWriteInFlight) return
                applySidebarJson(state)
            }

            is InboundEvent.ErrorEvent -> {
                if (isStopReply(event.turnId)) return
                if (isPausedTurn(event.turnId)) {
                    finishTurn(event.turnId)
                    _ui.update { current ->
                        current.copy(
                            streaming = false,
                            turnActive = activeTurnId != null,
                            runningChatIds = current.chatId
                                ?.let { current.runningChatIds - it }
                                ?: current.runningChatIds,
                        )
                    }
                    return
                }
                finishTurn(event.turnId)
                val chatId = event.chatId.orEmpty().ifBlank { _ui.value.chatId.orEmpty() }
                _ui.update { current ->
                    current.copy(
                        error = event.reason ?: event.detail ?: "gateway error",
                        streaming = false,
                        turnActive = activeTurnId != null,
                        runningChatIds = current.runningChatIds - chatId,
                        unreadChatIds = if (chatId.isNotBlank() && chatId != current.chatId) {
                            current.unreadChatIds + chatId
                        } else {
                            current.unreadChatIds - chatId
                        },
                        messages = current.messages.map {
                            when {
                                it.pending -> it.copy(pending = false)
                                it.streaming -> it.copy(streaming = false)
                                else -> it
                            }
                        },
                    )
                }
            }

            is InboundEvent.WebUiResponse -> Unit
            is InboundEvent.Unknown -> Unit
        }
    }

    private fun markAccepted(turnId: String) {
        if (turnId.isBlank()) return
        val id = "user-$turnId"
        _ui.update { current ->
            current.copy(
                messages = current.messages.map {
                    if (it.id == id) it.copy(pending = false) else it
                },
            )
        }
    }


    private fun addSystemMessage(text: String) {
        _ui.update { current ->
            if (current.messages.lastOrNull()?.text == text) {
                current
            } else {
                current.copy(
                    messages = current.messages + ChatMessage(
                        id = "system-${UUID.randomUUID()}",
                        role = MessageRole.System,
                        text = text,
                    ),
                )
            }
        }
    }
    private fun appendDelta(streamId: String, text: String) {
        if (text.isEmpty() || shouldIgnoreStream(streamId)) return
        val id = "assistant-$streamId"
        _ui.update { current ->
            val index = current.messages.indexOfLast { it.id == id }
            val messages = if (index >= 0) {
                current.messages.toMutableList().also {
                    it[index] = it[index].copy(text = it[index].text + text, streaming = true)
                }
            } else {
                // The answer started, so any thought left marked active has settled. A reloaded
                // thread has no reasoning-end frame, and this is the first sign its turn moved on.
                val settled = current.messages.map {
                    if (it.reasoning && it.streaming) it.copy(streaming = false) else it
                }
                settled + ChatMessage(
                    id = id,
                    role = MessageRole.Assistant,
                    text = text,
                    streaming = true,
                )
            }
            current.copy(messages = messages, streaming = true)
        }
    }

    private fun finalizeStreamEnd(streamId: String?, text: String?) {
        if (text.isNullOrBlank() || shouldIgnoreStream(streamId)) return
        val id = "assistant-${streamId ?: "current"}"
        _ui.update { current ->
            val index = current.messages.indexOfLast { it.id == id }
            val messages = if (index >= 0) {
                current.messages.toMutableList().also {
                    it[index] = it[index].copy(text = text, streaming = false)
                }
            } else {
                current.messages + ChatMessage(
                    id = id,
                    role = MessageRole.Assistant,
                    text = text,
                )
            }
            current.copy(messages = messages, streaming = false)
        }
    }

    private fun finalizeAssistant(text: String, mediaUrls: List<MediaRef>) {
        _ui.update { current ->
            val index = current.messages.indexOfLast {
                it.role == MessageRole.Assistant && it.streaming
            }
            val messages = if (index >= 0) {
                current.messages.toMutableList().also {
                    it[index] = it[index].copy(text = text, mediaUrls = mediaUrls, streaming = false)
                }
            } else {
                current.messages + ChatMessage(
                    id = "assistant-${UUID.randomUUID()}",
                    role = MessageRole.Assistant,
                    text = text,
                    mediaUrls = mediaUrls,
                )
            }
            current.copy(messages = messages, streaming = false)
        }
    }

    private fun appendReasoningDelta(streamId: String?, text: String) {
        if (text.isEmpty() || shouldIgnoreStream(streamId)) return
        val id = reasoningIds.idFor(streamId)
        _ui.update { current ->
            val index = current.messages.indexOfLast { it.id == id }
            val messages = if (index >= 0) {
                current.messages.toMutableList().also {
                    it[index] = it[index].copy(text = it[index].text + text, streaming = true, reasoning = true)
                }
            } else {
                current.messages + ChatMessage(
                    id = id,
                    role = MessageRole.Assistant,
                    text = text,
                    streaming = true,
                    reasoning = true,
                )
            }
            current.copy(messages = messages)
        }
    }

    private fun finalizeReasoning(streamId: String?, text: String?) {
        if (text.isNullOrBlank() || shouldIgnoreStream(streamId)) return
        val id = reasoningIds.idFor(streamId)
        _ui.update { current ->
            val index = current.messages.indexOfLast { it.id == id }
            val messages = if (index >= 0) {
                current.messages.toMutableList().also {
                    it[index] = it[index].copy(text = text, streaming = false, reasoning = true)
                }
            } else {
                current.messages + ChatMessage(
                    id = id,
                    role = MessageRole.Assistant,
                    text = text,
                    reasoning = true,
                )
            }
            current.copy(messages = messages)
        }
        reasoningIds.close()
    }

    /** Stable identity of the current PC, so media from two hosts never shares a cache entry. */
    private fun mediaScope(): String =
        GatewayRepository.currentHost()?.let { host ->
            host.serverId.ifBlank { host.endpoint }
        }.orEmpty()

    /**
     * History and gateway echoes only carry a URL. Use the cached copy when this device has one;
     * otherwise keep [MessageAttachment.remoteUrl] so the tile can fetch it when it becomes visible.
     */
    private fun List<MediaRef>.toMessageAttachments(): List<MessageAttachment> {
        val scope = mediaScope()
        return map { media ->
            val name = media.name?.takeIf { it.isNotBlank() }
                ?: media.url.substringAfterLast('/').substringBefore('?')
                    .ifBlank { "attachment" }
            MessageAttachment(
                name = name,
                mime = media.kind.orEmpty(),
                localUri = mediaCache.lookup(media.url, name, scope),
                remoteUrl = media.url,
            )
        }
    }

    /** Renames staged picked files to their URL-keyed cache names after the gateway echoes them. */
    private fun adoptMedia(
        attachments: List<MessageAttachment>,
        mediaUrls: List<MediaRef>,
    ): List<MessageAttachment> {
        if (attachments.isEmpty() || mediaUrls.isEmpty()) return attachments
        val scope = mediaScope()
        val adopted = attachments.toMutableList()
        val unused = attachments.toMutableList()
        mediaUrls.forEach { media ->
            val index = unused.indexOfFirst { it.name == media.name && it.localUri != null }
                .takeIf { it >= 0 }
                ?: unused.indexOfFirst { it.localUri != null }
            if (index < 0) return@forEach
            val attachment = unused.removeAt(index)
            val target = mediaCache.adopt(
                file = attachment.localUri?.path?.let(::File),
                url = media.url,
                name = media.name,
                scope = scope,
            )
            if (target != null) {
                val position = adopted.indexOf(attachment)
                if (position >= 0) {
                    adopted[position] = attachment.copy(localUri = Uri.fromFile(target))
                }
            }
        }
        return adopted
    }

    /**
     * Fetches one history image on demand. Called when an image tile without a local copy becomes
     * visible; the relay only carries the first view, not every reconnect.
     */
    fun loadMedia(messageId: String, index: Int) {
        val attachment = _ui.value.messages
            .firstOrNull { it.id == messageId }
            ?.attachments
            ?.getOrNull(index)
            ?: return
        if (attachment.localUri != null) return
        val url = attachment.remoteUrl ?: return
        if (url in failedMedia || mediaLoads.containsKey(url)) return
        if (_ui.value.connection !is GatewayState.Ready) return
        val scope = mediaScope()
        val job = viewModelScope.launch {
            try {
                val bytes = mediaSemaphore.withPermit { GatewayRepository.fetchMedia(url) }
                val uri = withContext(Dispatchers.IO) {
                    mediaCache.store(bytes, url, attachment.name, scope)
                } ?: return@launch
                _ui.update { current ->
                    current.copy(
                        messages = current.messages.map { message ->
                            if (message.id != messageId) {
                                message
                            } else {
                                message.copy(
                                    attachments = message.attachments.mapIndexed { i, item ->
                                        if (i == index && item.localUri == null) {
                                            item.copy(localUri = uri)
                                        } else {
                                            item
                                        }
                                    },
                                )
                            }
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the placeholder; a reconnect clears this and lets the tile try again.
                failedMedia.add(url)
            } finally {
                mediaLoads.remove(url)
            }
        }
        mediaLoads[url] = job
    }

    private fun List<InboundEvent>.toMessages(): List<ChatMessage> =
        mapIndexedNotNull { index, event ->
            when (event) {
                is InboundEvent.UserMessage ->
                    if (event.text.isBlank() && event.mediaUrls.isEmpty()) {
                        null
                    } else if (event.text.trim() == "/stop") {
                        null
                    } else {
                        ChatMessage(
                            id = "thread-user-$index",
                            role = MessageRole.User,
                            text = event.text,
                            mediaUrls = event.mediaUrls,
                            attachments = event.mediaUrls.toMessageAttachments(),
                        )
                    }

                is InboundEvent.Message ->
                    if (event.text.isBlank() || event.kind in TOOL_KINDS ||
                        isStopCommandReply(event.text)
                    ) {
                        null
                    } else {
                        ChatMessage(
                            id = "thread-assistant-$index",
                            role = MessageRole.Assistant,
                            text = event.text,
                            mediaUrls = event.mediaUrls,
                            reasoning = event.kind == "reasoning",
                        )
                    }

                is InboundEvent.StreamEnd ->
                    event.text?.takeIf { it.isNotBlank() }?.let { text ->
                        ChatMessage(
                            id = "thread-assistant-$index",
                            role = MessageRole.Assistant,
                            text = text,
                        )
                    }

                is InboundEvent.ReasoningEnd ->
                    event.text?.takeIf { it.isNotBlank() }?.let { text ->
                        ChatMessage(
                            id = "thread-reasoning-$index",
                            role = MessageRole.Assistant,
                            text = text,
                            reasoning = true,
                        )
                    }

                else -> null
            }
        }

    private fun SessionSummary.toEntity(): SessionEntity = SessionEntity(
        key = key,
        chatId = chatId,
        title = title,
        preview = preview,
        updatedAt = updatedAt,
        projectPath = workspaceScope?.projectPath,
        projectName = workspaceScope?.projectName,
        accessMode = workspaceScope?.accessMode,
        restrictToWorkspace = workspaceScope?.restrictToWorkspace,
    )

    private fun SessionEntity.toSummary(): SessionSummary = SessionSummary(
        key = key,
        title = title,
        preview = preview,
        updatedAt = updatedAt,
        workspaceScope = projectPath?.takeIf { it.isNotBlank() }?.let { path ->
            WorkspaceScope(
                projectPath = path,
                projectName = projectName,
                accessMode = accessMode ?: "restricted",
                restrictToWorkspace = restrictToWorkspace,
            )
        },
    )

    private fun normalizeEndpoint(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    override fun onCleared() {
        eventJob?.cancel()
        stateJob?.cancel()
        tunnelJob?.cancel()
        searchJob?.cancel()
        super.onCleared()
    }

    private companion object {
        val TOOL_KINDS = setOf("tool_hint", "progress")
        val STOP_REPLY_PATTERN = Regex("""Stopped \d+ task\(s\)\.?""")

        const val ASSISTANT_MESSAGE_PREFIX = "assistant-"
        const val REASONING_MESSAGE_PREFIX = "reasoning-"
        const val CURRENT_STREAM_ID = "current"
        const val STOP_TURN_PREFIX = "stop-"

        /** Matches the WebUI's silent command turn id, so the gateway scopes it as metadata. */
        const val SYSTEM_TURN_PREFIX = "webui-system:"

        /** Name the gateway uses for its unsaved, default model configuration. */
        const val DEFAULT_MODEL_PRESET = "default"
    }
}

/** What the composer showed before an optimistic model switch, restored if it fails. */
private data class ModelSwitchRollback(
    val previousPreset: String?,
    val previousWindow: Int,
    val requested: String,
)

/** Outcome of a sidebar delete, mirroring the WebUI's automation-blocked retry flow. */
internal sealed interface DeleteSessionsResult {
    data class Deleted(val keys: List<String>) : DeleteSessionsResult
    data class Blocked(
        val remainingKeys: List<String>,
        val automations: List<SessionAutomationSummary>,
    ) : DeleteSessionsResult
    data class Failed(val message: String) : DeleteSessionsResult
}

private fun JsonObject.stringList(name: String): List<String> =
    (this[name] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }
        .orEmpty()

private fun JsonObject.stringSet(name: String): MutableSet<String> =
    stringList(name).toMutableSet()

private fun JsonObject.stringMap(name: String): MutableMap<String, String> =
    (this[name] as? JsonObject)
        ?.mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { key to it }
        }
        ?.toMap()
        ?.toMutableMap()
        ?: mutableMapOf()

private fun JsonObject.withStringList(name: String, values: Collection<String>): JsonObject =
    JsonObject(this + (name to JsonArray(values.map { JsonPrimitive(it) })))

private fun JsonObject.withStringMap(name: String, values: Map<String, String>): JsonObject =
    JsonObject(this + (name to JsonObject(values.mapValues { JsonPrimitive(it.value) })))

private fun JsonObject.withCollapsedGroup(groupId: String, collapsed: Boolean): JsonObject {
    val groups = (this["collapsed_groups"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
    groups[groupId] = JsonPrimitive(collapsed)
    return JsonObject(this + ("collapsed_groups" to JsonObject(groups)))
}

private fun JsonObject.withShowArchived(show: Boolean): JsonObject {
    val view = (this["view"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
    view["show_archived"] = JsonPrimitive(show)
    return JsonObject(this + ("view" to JsonObject(view)))
}
