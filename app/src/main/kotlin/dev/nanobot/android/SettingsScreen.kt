package dev.nanobot.android

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nanobot.android.host.HostKind
import dev.nanobot.protocol.AutomationChat
import dev.nanobot.protocol.AutomationJob
import dev.nanobot.protocol.AutomationRun
import dev.nanobot.protocol.AutomationScheduleUpdate
import dev.nanobot.protocol.AutomationUpdate
import dev.nanobot.protocol.GatewaySettings
import dev.nanobot.protocol.McpOAuthFlowPayload
import dev.nanobot.protocol.McpPresetField
import dev.nanobot.protocol.McpPresetSummary
import dev.nanobot.protocol.SkillDetail
import dev.nanobot.protocol.SkillSummary
import java.util.Calendar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Settings modelled on a phone settings app: a root list of categories, each opening a dedicated
 * detail page. Local preferences and host controls are editable; gateway configuration is shown
 * read-only, while skills and MCP presets can be toggled from their list pages.
 * Skills open a detail page with search, setup requirements, the raw instructions, and delete.
 * Automations open the WebUI's task detail: schedule, run state, instructions, linked chat,
 * recent runs, enable/disable, run now, and delete.
 */
@Composable
internal fun SettingsScreen(
    state: UiState,
    onDismiss: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onAccentChange: (AccentTheme) -> Unit,
    onCustomAccentHueChange: (Float) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onFontSizeChange: (AppFontSize) -> Unit,
    onTurnNotificationsChange: (Boolean) -> Unit,
    onNotificationSoundChange: (Boolean) -> Unit,
    onRefreshGatewaySettings: () -> Unit,
    onReconnect: () -> Unit,
    onManageHosts: () -> Unit,
    onAddHost: () -> Unit,
    onRefreshCapabilities: () -> Unit,
    onSetSkillEnabled: (String, Boolean) -> Unit,
    onLoadSkillDetail: (String, Boolean) -> Unit,
    onClearSkillDetail: () -> Unit,
    onDeleteSkill: (String) -> Unit,
    onMcpAction: (String, String) -> Unit,
    onSetMcpFieldValue: (String, String, String) -> Unit,
    onSaveMcpServer: (McpCustomForm) -> Unit,
    onImportMcpConfig: (String) -> Unit,
    onUpdateMcpTools: (String, List<String>) -> Unit,
    onStartMcpOAuth: (String, Boolean) -> Unit,
    onCompleteMcpOAuth: (String) -> Unit,
    onCancelMcpOAuth: () -> Unit,
    onSetAutomationEnabled: (String, Boolean) -> Unit,
    onRunAutomation: (String) -> Unit,
    onDeleteAutomation: (String) -> Unit,
    onUpdateAutomation: (String, AutomationUpdate) -> Unit,
    onLoadAutomationChats: (String) -> Unit,
    onChangeAutomationChat: (String, String, String, String) -> Unit,
    onClearAutomationAction: () -> Unit,
    onClearAutomationChats: () -> Unit,
    onClearMessage: () -> Unit,
    onCheckForUpdates: () -> Unit = {},
    gatewayActions: GatewaySettingsActions = GatewaySettingsActions(),
) {
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.Root.name) }
    val page = SettingsPage.entries.firstOrNull { it.name == pageName } ?: SettingsPage.Root
    var skillDetailName by rememberSaveable { mutableStateOf<String?>(null) }
    var skillSearchOpen by rememberSaveable { mutableStateOf(false) }
    var skillQuery by rememberSaveable { mutableStateOf("") }
    var mcpDetailName by rememberSaveable { mutableStateOf<String?>(null) }
    var mcpTabName by rememberSaveable { mutableStateOf(McpTab.Overview.name) }
    var mcpSearchOpen by rememberSaveable { mutableStateOf(false) }
    var mcpQuery by rememberSaveable { mutableStateOf("") }
    var automationDetailId by rememberSaveable { mutableStateOf<String?>(null) }
    var automationEditOpen by rememberSaveable { mutableStateOf(false) }
    var automationChatOpen by rememberSaveable { mutableStateOf(false) }
    val strings = LocalStrings.current
    val mcpTab = McpTab.entries.firstOrNull { it.name == mcpTabName } ?: McpTab.Overview
    val mcpDetail = state.mcpPresets.firstOrNull { it.name == mcpDetailName }
    val automationDetail = state.automations.firstOrNull { it.id == automationDetailId }

    fun closeSkillDetail() {
        skillDetailName = null
        onClearSkillDetail()
    }

    fun closeMcpDetail() {
        mcpDetailName = null
        mcpTabName = McpTab.Overview.name
    }

    fun closeAutomationEditor() {
        automationEditOpen = false
        onClearAutomationAction()
    }

    fun closeAutomationChat() {
        automationChatOpen = false
        onClearAutomationAction()
        onClearAutomationChats()
    }

    fun closeAutomationDetail() {
        if (automationEditOpen) closeAutomationEditor()
        if (automationChatOpen) closeAutomationChat()
        automationDetailId = null
        onClearAutomationAction()
        onClearAutomationChats()
    }

    // The gateway acknowledges a save with a new list; close the editor it belongs to.
    LaunchedEffect(state.automationActionDone, automationDetailId) {
        val done = state.automationActionDone ?: return@LaunchedEffect
        val id = automationDetailId ?: return@LaunchedEffect
        when (done) {
            "update:$id" -> automationEditOpen = false
            "chat:$id" -> {
                automationChatOpen = false
                onClearAutomationChats()
            }
        }
        onClearAutomationAction()
    }

    LaunchedEffect(automationChatOpen, automationDetailId) {
        if (automationChatOpen) {
            automationDetailId?.let(onLoadAutomationChats)
        }
    }

    // A successful remove drops the preset from the refreshed list; close its detail page then.
    LaunchedEffect(mcpDetailName, state.mcpPresets, state.loadingFeatures) {
        val name = mcpDetailName ?: return@LaunchedEffect
        if (!state.loadingFeatures && state.mcpPresets.none { it.name == name }) {
            closeMcpDetail()
        }
    }

    fun goBack() {
        when {
            automationEditOpen -> closeAutomationEditor()
            automationChatOpen -> closeAutomationChat()
            automationDetailId != null -> closeAutomationDetail()
            mcpDetailName != null -> closeMcpDetail()
            skillDetailName != null -> closeSkillDetail()
            page != SettingsPage.Root -> pageName = SettingsPage.Root.name
            else -> onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        onClearMessage()
        onRefreshGatewaySettings()
    }
    LaunchedEffect(page) {
        when (page) {
            SettingsPage.Skills,
            SettingsPage.Mcp,
            SettingsPage.CliApps,
            SettingsPage.Automations,
            -> onRefreshCapabilities()

            else -> Unit
        }
    }

    // Render as an in-window overlay rather than a Dialog. A full-screen dialog window is
    // positioned below the status bar but still sized to the whole display on Android 15,
    // so the bottom of every list stayed off-screen and could not be scrolled into view.
    BackHandler { goBack() }
    Box(
        Modifier
            .fillMaxSize()
            // Swallow stray touches so they cannot reach the chat screen behind this overlay.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                val searchingSkills = page == SettingsPage.Skills && skillDetailName == null
                val searchingMcp = page == SettingsPage.Mcp && mcpDetailName == null
                SettingsHeader(
                    title = when {
                        automationEditOpen -> strings.automationEditTitle
                        automationChatOpen -> strings.automationChatTitle
                        automationDetailId != null -> automationDetail
                            ?.let { it.name.ifBlank { it.id } }
                            ?: automationDetailId.orEmpty()
                        skillDetailName != null -> skillDetailName.orEmpty()
                        mcpDetailName != null -> mcpDetail?.displayName
                            ?: mcpDetailName.orEmpty()
                        else -> page.title(strings)
                    },
                    isRoot = page == SettingsPage.Root &&
                        skillDetailName == null && mcpDetailName == null &&
                        automationDetailId == null && !automationEditOpen && !automationChatOpen,
                    showSearch = searchingSkills || searchingMcp,
                    searchOpen = if (searchingMcp) mcpSearchOpen else skillSearchOpen,
                    strings = strings,
                    onSearchToggle = {
                        if (searchingMcp) {
                            mcpSearchOpen = !mcpSearchOpen
                            if (!mcpSearchOpen) mcpQuery = ""
                        } else {
                            skillSearchOpen = !skillSearchOpen
                            if (!skillSearchOpen) skillQuery = ""
                        }
                    },
                    onBack = { goBack() },
                )
                if (searchingSkills && skillSearchOpen) {
                    CatalogSearchField(
                        query = skillQuery,
                        onQueryChange = { skillQuery = it },
                        placeholder = strings.searchSkills,
                        strings = strings,
                    )
                }
                if (searchingMcp && mcpSearchOpen) {
                    CatalogSearchField(
                        query = mcpQuery,
                        onQueryChange = { mcpQuery = it },
                        placeholder = strings.mcpSearch,
                        strings = strings,
                    )
                }
                state.settingsMessage?.let { message ->
                    MessageBanner(message = message, onClear = onClearMessage)
                }
                when (page) {
                    SettingsPage.Root -> RootPage(
                        state = state,
                        strings = strings,
                        onOpen = { pageName = it.name },
                    )

                    SettingsPage.Appearance -> AppearancePage(
                        mode = state.themeMode,
                        onModeChange = onThemeChange,
                        accent = state.accentTheme,
                        customHue = state.customAccentHue,
                        onAccentChange = onAccentChange,
                        onCustomHueChange = onCustomAccentHueChange,
                        language = state.language,
                        onLanguageChange = onLanguageChange,
                        fontSize = state.fontSize,
                        onFontSizeChange = onFontSizeChange,
                        strings = strings,
                    )

                    SettingsPage.Notifications -> NotificationsPage(
                        enabled = state.turnNotifications,
                        soundEnabled = state.notificationSound,
                        onEnabledChange = onTurnNotificationsChange,
                        onSoundChange = onNotificationSoundChange,
                        strings = strings,
                    )

                    SettingsPage.Host -> HostPage(
                        state = state,
                        strings = strings,
                        onReconnect = onReconnect,
                        onManageHosts = onManageHosts,
                        onAddHost = onAddHost,
                    )

                    SettingsPage.Gateway -> GatewayPage(
                        state = state,
                        strings = strings,
                        onRefresh = onRefreshGatewaySettings,
                        actions = gatewayActions,
                    )

                    SettingsPage.Model -> ModelPage(
                        state = state,
                        strings = strings,
                        onRefresh = onRefreshGatewaySettings,
                        actions = gatewayActions,
                    )

                    SettingsPage.Skills -> {
                        val detailName = skillDetailName
                        if (detailName != null) {
                            SkillDetailPage(
                                name = detailName,
                                state = state,
                                strings = strings,
                                onToggle = onSetSkillEnabled,
                                onLoad = onLoadSkillDetail,
                                onDelete = {
                                    closeSkillDetail()
                                    onDeleteSkill(it)
                                },
                            )
                        } else {
                            SkillsPage(
                                state = state,
                                strings = strings,
                                query = skillQuery,
                                onSelect = { skill ->
                                    skillDetailName = skill.name
                                },
                            )
                        }
                    }

                    SettingsPage.Mcp -> {
                        val detailName = mcpDetailName
                        if (detailName != null) {
                            McpDetailPage(
                                name = detailName,
                                state = state,
                                strings = strings,
                                tab = mcpTab,
                                onTabChange = { mcpTabName = it.name },
                                onAction = onMcpAction,
                                onFieldChange = onSetMcpFieldValue,
                                onUpdateTools = onUpdateMcpTools,
                                onStartOAuth = onStartMcpOAuth,
                                onCompleteOAuth = onCompleteMcpOAuth,
                                onCancelOAuth = onCancelMcpOAuth,
                            )
                        } else {
                            McpPage(
                                state = state,
                                strings = strings,
                                query = mcpQuery,
                                onSelect = { name, tab ->
                                    mcpDetailName = name
                                    mcpTabName = tab.name
                                },
                                onAction = onMcpAction,
                                onSaveServer = onSaveMcpServer,
                                onImportConfig = onImportMcpConfig,
                                onCancelOAuth = onCancelMcpOAuth,
                            )
                        }
                    }

                    SettingsPage.CliApps -> CliAppsPage(state = state, strings = strings)

                    SettingsPage.Automations -> {
                        val detailId = automationDetailId
                        when {
                            detailId != null && automationEditOpen && automationDetail != null ->
                                AutomationEditPage(
                                    job = automationDetail,
                                    state = state,
                                    strings = strings,
                                    onCancel = { closeAutomationEditor() },
                                    onSave = { onUpdateAutomation(detailId, it) },
                                )

                            detailId != null && automationChatOpen && automationDetail != null ->
                                AutomationChatPage(
                                    job = automationDetail,
                                    state = state,
                                    strings = strings,
                                    onRetry = { onLoadAutomationChats(detailId) },
                                    onCancel = { closeAutomationChat() },
                                    onConfirm = { targetId, revision, message ->
                                        onChangeAutomationChat(detailId, targetId, revision, message)
                                    },
                                )

                            detailId != null -> AutomationDetailPage(
                                job = automationDetail,
                                strings = strings,
                                onToggle = onSetAutomationEnabled,
                                onRun = onRunAutomation,
                                onEdit = { automationEditOpen = true },
                                onChangeChat = { automationChatOpen = true },
                                onDelete = {
                                    closeAutomationDetail()
                                    onDeleteAutomation(it)
                                },
                            )

                            else -> AutomationsPage(
                                state = state,
                                strings = strings,
                                onSelect = { job -> automationDetailId = job.id },
                            )
                        }
                    }

                    SettingsPage.About -> AboutPage(
                        state = state,
                        strings = strings,
                        onCheckForUpdates = onCheckForUpdates,
                    )
                }
            }
        }
    }
}

private enum class SettingsPage {
    Root,
    Appearance,
    Notifications,
    Host,
    Gateway,
    Model,
    Skills,
    Mcp,
    CliApps,
    Automations,
    About,
}

private fun SettingsPage.title(strings: AppStrings): String = when (this) {
    SettingsPage.Root -> strings.settings
    SettingsPage.Appearance -> strings.appearance
    SettingsPage.Notifications -> strings.notifications
    SettingsPage.Host -> strings.hostsAndConnection
    SettingsPage.Gateway -> strings.gateway
    SettingsPage.Model -> strings.model
    SettingsPage.Skills -> strings.skills
    SettingsPage.Mcp -> strings.mcpPresets
    SettingsPage.CliApps -> strings.cliApps
    SettingsPage.Automations -> strings.automations
    SettingsPage.About -> strings.about
}

private val PagePadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)

@Composable
private fun SettingsHeader(
    title: String,
    isRoot: Boolean,
    showSearch: Boolean,
    searchOpen: Boolean,
    strings: AppStrings,
    onSearchToggle: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                if (isRoot) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (isRoot) strings.closeSettings else strings.back,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when {
            showSearch -> IconButton(onClick = onSearchToggle) {
                Icon(
                    if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (searchOpen) strings.closeSearch else strings.search,
                )
            }

            isRoot -> Text(
                strings.localPrefsAndGatewayInfo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The magnifier in the header reveals this field; it filters by name and description. */
@Composable
private fun CatalogSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    strings: AppStrings,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = strings.clearSearch)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .focusRequester(focusRequester),
    )
}

@Composable
private fun MessageBanner(message: String, onClear: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = LocalStrings.current.dismissMessage,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun RootPage(state: UiState, strings: AppStrings, onOpen: (SettingsPage) -> Unit) {
    val versionName = rememberClientVersion()
    val (_, hostLabel) = hostStatus(state, state.currentHost, strings)
    val runtime = state.gatewaySettings?.runtime
    val gatewaySummary = runtime
        ?.takeIf { it.gatewayHost.isNotBlank() }
        ?.let { "${it.gatewayHost}:${it.gatewayPort}" }
        ?: strings.tapForGatewayDetails
    val modelSummary = state.gatewaySettings?.agent?.model
        ?.takeIf { it.isNotBlank() }
        ?: strings.tapForModelDetails
    val enabledSkills = state.skills.count { it.enabled }
    val configuredMcp = state.mcpPresets.count { it.configured }
    val installedApps = state.cliApps.count { it.installed }
    val enabledAutomations = state.automations.count { it.enabled }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            RootGroup(strings.general) {
                SettingsEntry(
                    icon = Icons.Default.Face,
                    title = strings.appearance,
                    summary = "${strings.accentName(state.accentTheme)} · ${strings.themeModeName(state.themeMode)}",
                    onClick = { onOpen(SettingsPage.Appearance) },
                )
                GroupDivider()
                SettingsEntry(
                    icon = Icons.Default.Notifications,
                    title = strings.notifications,
                    summary = if (state.turnNotifications) {
                        strings.turnNotificationsOn
                    } else {
                        strings.turnNotificationsOff
                    },
                    onClick = { onOpen(SettingsPage.Notifications) },
                )
            }
        }
        item {
            RootGroup(strings.connection) {
                SettingsEntry(
                    icon = Icons.Default.Home,
                    title = strings.hostsAndConnection,
                    summary = "${state.currentHost?.displayName ?: strings.noHost} · $hostLabel",
                    onClick = { onOpen(SettingsPage.Host) },
                )
                GroupDivider()
                SettingsEntry(
                    icon = Icons.Default.Refresh,
                    title = strings.gateway,
                    summary = gatewaySummary,
                    onClick = { onOpen(SettingsPage.Gateway) },
                )
                GroupDivider()
                SettingsEntry(
                    icon = Icons.Default.Person,
                    title = strings.model,
                    summary = modelSummary,
                    onClick = { onOpen(SettingsPage.Model) },
                )
            }
        }
        item {
            RootGroup(strings.capabilities) {
                SettingsEntry(
                    icon = Icons.Default.Star,
                    title = strings.skills,
                    summary = strings.countEnabled(state.skills.size, enabledSkills),
                    onClick = { onOpen(SettingsPage.Skills) },
                )
                GroupDivider()
                SettingsEntry(
                    icon = Icons.Default.Build,
                    title = strings.mcpPresets,
                    summary = strings.countConfigured(state.mcpPresets.size, configuredMcp),
                    onClick = { onOpen(SettingsPage.Mcp) },
                )
                GroupDivider()
                SettingsEntry(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = strings.cliApps,
                    summary = strings.countInstalled(state.cliApps.size, installedApps),
                    onClick = { onOpen(SettingsPage.CliApps) },
                )
                GroupDivider()
                SettingsEntry(
                    icon = Icons.Default.PlayArrow,
                    title = strings.automations,
                    summary = strings.countEnabled(state.automations.size, enabledAutomations),
                    onClick = { onOpen(SettingsPage.Automations) },
                )
            }
        }
        item {
            RootGroup(strings.other) {
                SettingsEntry(
                    icon = Icons.Default.Info,
                    title = strings.about,
                    summary = strings.versionLabel(versionName),
                    onClick = { onOpen(SettingsPage.About) },
                )
            }
        }
    }
}

@Composable
private fun RootGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsEntry(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun AppearancePage(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    accent: AccentTheme,
    customHue: Float,
    onAccentChange: (AccentTheme) -> Unit,
    onCustomHueChange: (Float) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    fontSize: AppFontSize,
    onFontSizeChange: (AppFontSize) -> Unit,
    strings: AppStrings,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSection(strings.theme) {
                Text(
                    strings.themeDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.System).forEach { option ->
                        ThemeModeTile(
                            mode = option,
                            strings = strings,
                            accent = accent,
                            customHue = customHue,
                            selected = option == mode,
                            onClick = { onModeChange(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        item {
            SettingsSection(strings.colorScheme) {
                AccentSwatches(
                    accent = accent,
                    customHue = customHue,
                    strings = strings,
                    onAccentChange = onAccentChange,
                )
                if (accent == AccentTheme.Custom) {
                    CustomHuePanel(
                        hue = customHue,
                        strings = strings,
                        onHueChange = onCustomHueChange,
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GroupDivider()
                    WallpaperAccentRow(
                        selected = accent == AccentTheme.Wallpaper,
                        strings = strings,
                        onClick = { onAccentChange(AccentTheme.Wallpaper) },
                    )
                }
            }
        }
        item {
            SettingsSection(strings.textSize) {
                Text(
                    strings.textSizeDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                )
                ChoiceRow(
                    options = AppFontSize.entries,
                    selected = fontSize,
                    label = strings::fontSizeName,
                    onSelect = onFontSizeChange,
                )
            }
        }
        item {
            SettingsSection(strings.language) {
                Text(
                    strings.languageDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                )
                ChoiceRow(
                    options = AppLanguage.entries,
                    selected = language,
                    label = { it.nativeName },
                    onSelect = onLanguageChange,
                )
            }
        }
        item {
            SettingsSection(strings.other) {
                Text(
                    strings.appearanceNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/** A row of equal-width text choices, used for text size and language. */
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(option) },
                        role = Role.RadioButton,
                    ),
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** 三张手机小样：浅色、深色、跟随系统（左浅右深）。选中的外面一圈主色。 */
@Composable
private fun ThemeModeTile(
    mode: ThemeMode,
    strings: AppStrings,
    accent: AccentTheme,
    customHue: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .border(
                    width = 2.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(2.dp)
                .clip(shape),
        ) {
            MiniThemePreview(
                mode = mode,
                accent = accent,
                customHue = customHue,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            strings.themeModeName(mode),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun MiniThemePreview(
    mode: ThemeMode,
    accent: AccentTheme,
    customHue: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val light = remember(accent, customHue) { nanobotScheme(context, accent, customHue, dark = false) }
    val dark = remember(accent, customHue) { nanobotScheme(context, accent, customHue, dark = true) }
    val edge = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        when (mode) {
            ThemeMode.Light -> drawMiniScene(light)
            ThemeMode.Dark -> drawMiniScene(dark)
            ThemeMode.System -> {
                val half = size.width / 2f
                clipRect(0f, 0f, half, size.height) {
                    drawMiniScene(light, originX = 0f, width = half)
                }
                clipRect(half, 0f, size.width, size.height) {
                    drawMiniScene(dark, originX = half, width = half)
                }
                drawLine(
                    color = edge,
                    start = Offset(half, 0f),
                    end = Offset(half, size.height),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
        }
        drawRoundRect(
            color = edge.copy(alpha = 0.6f),
            cornerRadius = CornerRadius(10.dp.toPx()),
            style = Stroke(width = 0.5.dp.toPx()),
        )
    }
}

private fun DrawScope.drawMiniScene(
    scheme: ColorScheme,
    originX: Float = 0f,
    width: Float = size.width,
) {
    val height = size.height
    val pad = width * 0.12f
    drawRect(
        color = scheme.background,
        topLeft = Offset(originX, 0f),
        size = Size(width, height),
    )
    drawRoundRect(
        color = scheme.onSurfaceVariant.copy(alpha = 0.35f),
        topLeft = Offset(originX + pad, height * 0.11f),
        size = Size(width * 0.34f, height * 0.035f),
        cornerRadius = CornerRadius(height * 0.02f),
    )
    drawRoundRect(
        color = scheme.surfaceVariant,
        topLeft = Offset(originX + pad, height * 0.27f),
        size = Size(width * 0.48f, height * 0.14f),
        cornerRadius = CornerRadius(height * 0.04f),
    )
    drawRoundRect(
        color = scheme.primaryContainer,
        topLeft = Offset(originX + width - pad - width * 0.5f, height * 0.47f),
        size = Size(width * 0.5f, height * 0.14f),
        cornerRadius = CornerRadius(height * 0.04f),
    )
    drawRoundRect(
        color = scheme.surface,
        topLeft = Offset(originX + pad, height * 0.72f),
        size = Size(width - 2 * pad, height * 0.14f),
        cornerRadius = CornerRadius(height * 0.07f),
    )
    drawCircle(
        color = scheme.primary,
        radius = width * 0.07f,
        center = Offset(originX + width - pad - width * 0.07f, height * 0.79f),
    )
}

/** 一排色点：六套预设 + 自定义（色轮）。选中的外面一圈同色环，下面是名字。 */
@Composable
private fun AccentSwatches(
    accent: AccentTheme,
    customHue: Float,
    strings: AppStrings,
    onAccentChange: (AccentTheme) -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val wheel = remember(dark) {
        List(25) { index ->
            accentColors(AccentTheme.Custom, index * 15f).primaryFor(dark)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AccentTheme.presets.forEach { preset ->
            AccentSwatch(
                label = strings.accentName(preset),
                color = accentColors(preset, customHue).primaryFor(dark),
                selected = accent == preset,
                onClick = { onAccentChange(preset) },
                modifier = Modifier.weight(1f),
            )
        }
        AccentSwatch(
            label = strings.accentName(AccentTheme.Custom),
            color = accentColors(AccentTheme.Custom, customHue).primaryFor(dark),
            selected = accent == AccentTheme.Custom,
            onClick = { onAccentChange(AccentTheme.Custom) },
            wheel = wheel,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AccentSwatch(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wheel: List<Color>? = null,
) {
    Column(
        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(
                    width = 2.dp,
                    color = if (selected) color else Color.Transparent,
                    shape = CircleShape,
                )
                .padding(5.dp),
        ) {
            if (wheel == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(color, CircleShape),
                )
            } else {
                val hole = MaterialTheme.colorScheme.background
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Brush.sweepGradient(wheel))
                    drawCircle(hole, radius = size.minDimension * 0.31f)
                    drawCircle(color, radius = size.minDimension * 0.22f)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CustomHuePanel(hue: Float, strings: AppStrings, onHueChange: (Float) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val preview = accentColors(AccentTheme.Custom, hue).primaryFor(dark)
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(preview, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                strings.customColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Slider(
            value = hue,
            onValueChange = onHueChange,
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(
                thumbColor = preview,
                activeTrackColor = preview,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
        Text(
            strings.dragToChooseHue,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WallpaperAccentRow(selected: Boolean, strings: AppStrings, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(strings.followWallpaper, style = MaterialTheme.typography.bodyMedium)
            Text(
                strings.followWallpaperDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = strings.currentHostCheck,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun NotificationsPage(
    enabled: Boolean,
    soundEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    strings: AppStrings,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSection(strings.notifications) {
                SettingRow(
                    title = strings.turnNotifications,
                    description = strings.turnNotificationsDescription,
                ) {
                    Switch(checked = enabled, onCheckedChange = onEnabledChange)
                }
                SettingRow(
                    title = strings.notificationSound,
                    description = strings.notificationSoundDescription,
                    showDivider = false,
                ) {
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = onSoundChange,
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun HostPage(
    state: UiState,
    strings: AppStrings,
    onReconnect: () -> Unit,
    onManageHosts: () -> Unit,
    onAddHost: () -> Unit,
) {
    val host = state.currentHost
    val (dot, status) = hostStatus(state, host, strings)
    val kind = when (host?.kind) {
        HostKind.Direct -> strings.direct
        HostKind.Relay -> strings.relay
        null -> "—"
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSection(strings.currentHost) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            host?.displayName ?: state.hostName.ifBlank { strings.noHost },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                GroupDivider()
                InfoRow(strings.type, kind)
                InfoRow(
                    strings.address,
                    when (host?.kind) {
                        HostKind.Direct -> host.endpoint
                        HostKind.Relay -> host.relayUrl
                        null -> ""
                    },
                    showDivider = false,
                )
            }
        }
        item {
            SettingsSection(strings.actions) {
                ActionRow(
                    icon = Icons.Default.Refresh,
                    title = strings.reconnect,
                    description = strings.reconnectDescription,
                    onClick = onReconnect,
                )
                GroupDivider()
                ActionRow(
                    icon = Icons.Default.Add,
                    title = strings.addHost,
                    description = strings.addHostDescription,
                    onClick = onAddHost,
                )
                GroupDivider()
                ActionRow(
                    icon = Icons.Default.Settings,
                    title = strings.manageHosts,
                    description = strings.manageHostsDescription,
                    onClick = onManageHosts,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GatewayPage(
    state: UiState,
    strings: AppStrings,
    onRefresh: () -> Unit,
    actions: GatewaySettingsActions,
) {
    SettingsInfoPage(state, strings, strings.gateway, onRefresh) { settings ->
        item {
            RuntimeSection(
                settings = settings,
                saving = state.settingsSaving,
                strings = strings,
                onSetUnifiedSession = actions.setUnifiedSession,
            )
        }
        item { VersionSection(settings, strings) }
    }
}

/**
 * Shared gateway info page: refresh action plus loading, error, and disconnected states.
 */
@Composable
internal fun SettingsInfoPage(
    state: UiState,
    strings: AppStrings,
    title: String,
    onRefresh: () -> Unit,
    sections: LazyListScope.(GatewaySettings) -> Unit,
) {
    val settings = state.gatewaySettings
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRefresh, enabled = !state.settingsLoading) {
                    if (state.settingsLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(strings.refresh)
                    }
                }
            }
        }
        when {
            state.settingsLoading && settings == null -> item {
                SettingsSection(title) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }

            state.settingsError != null && settings == null -> item {
                SettingsSection(title) {
                    Text(
                        state.settingsError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            settings == null -> item {
                SettingsSection(title) {
                    Text(
                        strings.connectToView,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            else -> sections(settings)
        }
    }
}

@Composable
private fun RuntimeSection(
    settings: GatewaySettings,
    saving: Boolean,
    strings: AppStrings,
    onSetUnifiedSession: (Boolean) -> Unit,
) {
    val runtime = settings.runtime
    val address = if (runtime.gatewayHost.isBlank()) {
        ""
    } else {
        "${runtime.gatewayHost}:${runtime.gatewayPort}"
    }
    SettingsSection(strings.runtime) {
        InfoRow(strings.workspace, runtime.workspacePath)
        InfoRow(strings.configFile, runtime.configPath)
        InfoRow(strings.gatewayAddress, address)
        SettingRow(
            title = strings.sharedSession,
            description = strings.sharedSessionDescription,
            showDivider = false,
        ) {
            Switch(
                checked = runtime.unifiedSession,
                onCheckedChange = onSetUnifiedSession,
                enabled = !saving,
            )
        }
    }
}

@Composable
private fun VersionSection(settings: GatewaySettings, strings: AppStrings) {
    val commit = settings.version?.commit?.take(7)?.takeIf { it.isNotBlank() }
    SettingsSection(strings.version) {
        InfoRow(strings.gatewayVersion, settings.version?.current.orEmpty())
        if (commit != null) InfoRow(strings.commit, commit)
        InfoRow(
            strings.restartPending,
            if (settings.requiresRestart) strings.changesPendingRestart else strings.none,
            showDivider = false,
        )
    }
}

@Composable
private fun SkillsPage(
    state: UiState,
    strings: AppStrings,
    query: String,
    onSelect: (SkillSummary) -> Unit,
) {
    val normalized = query.trim().lowercase()
    val filtered = state.skills.filter { skill ->
        normalized.isEmpty() ||
            skill.name.lowercase().contains(normalized) ||
            skill.description.lowercase().contains(normalized)
    }
    val groups = listOf(
        strings.sourceCustom to filtered.filter { it.source == "workspace" },
        strings.sourceBuiltin to filtered.filter { it.source == "builtin" },
        strings.other to filtered.filter {
            it.source != "workspace" && it.source != "builtin"
        },
    ).filter { it.second.isNotEmpty() }

    Column(Modifier.fillMaxSize()) {
        if (state.loadingFeatures) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        when {
            state.loadingFeatures && state.skills.isEmpty() -> Unit
            filtered.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.skills.isEmpty()) strings.noSkills else strings.noMatchingSkills,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PagePadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { (label, skills) ->
                    item(key = "skill-group-$label") {
                        Text(
                            "$label · ${skills.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(skills, key = { "${it.source}:${it.name}" }) { skill ->
                        SkillRow(
                            skill = skill,
                            strings = strings,
                            onClick = { onSelect(skill) },
                        )
                    }
                }
            }
        }
    }
}

/** One catalog row: the whole row opens the detail page, matching the WebUI's clickable list. */
@Composable
private fun SkillRow(skill: SkillSummary, strings: AppStrings, onClick: () -> Unit) {
    val statusColor = when {
        !skill.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        skill.available -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(statusColor, CircleShape))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    skill.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (skill.description.isNotBlank()) {
                    Text(
                        skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    skillSourceLabel(skill.source, strings),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                skillStatusLabel(skill.enabled, skill.available, strings),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SkillDetailPage(
    name: String,
    state: UiState,
    strings: AppStrings,
    onToggle: (String, Boolean) -> Unit,
    onLoad: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    LaunchedEffect(name) { onLoad(name, false) }
    val summary = state.skills.firstOrNull { it.name == name }
    val detail = state.skillDetail?.takeIf { it.name == name }
    val enabled = detail?.enabled ?: summary?.enabled ?: true
    val available = detail?.available ?: summary?.available ?: true
    val deletable = detail?.deletable ?: summary?.deletable ?: false
    val source = detail?.source ?: summary?.source.orEmpty()
    val description = detail?.description ?: summary?.description.orEmpty()
    val unavailableReason = detail?.unavailableReason ?: summary?.unavailableReason
    var confirmDelete by remember { mutableStateOf(false) }

    if (summary == null && detail == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (state.skillDetailLoading) strings.loadingSkillDetail else strings.skillNotFound,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val statusColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        available -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSection(strings.skills) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkillPill(skillSourceLabel(source, strings))
                        SkillPill(
                            skillStatusLabel(enabled, available, strings),
                            color = statusColor,
                        )
                    }
                    if (description.isNotBlank()) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    if (enabled && !available && !unavailableReason.isNullOrBlank()) {
                        Text(
                            unavailableReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        item {
            SettingsSection(strings.actions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(strings.useSkill, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            skillStatusLabel(enabled, available, strings),
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { onToggle(name, it) },
                        enabled = !state.skillDetailLoading,
                    )
                }
            }
        }
        if (state.skillDetailError != null) {
            item {
                SettingsSection(strings.skills) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            state.skillDetailError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = { onLoad(name, true) }) {
                            Text(strings.refresh)
                        }
                    }
                }
            }
        }
        if (detail != null && detail.enabled && detail.requirements.hasMissing) {
            item {
                SkillRequirementsCard(
                    detail = detail,
                    strings = strings,
                    onReload = { onLoad(name, true) },
                )
            }
        }
        if (detail != null) {
            item {
                SkillInstructionsCard(detail.rawMarkdown, strings)
            }
        }
        if (deletable) {
            item {
                SettingsSection(strings.deleteSkill) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                strings.deleteSkillDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { confirmDelete = true }) {
                            Text(strings.delete, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(strings.deleteSkillTitle(name)) },
            text = { Text(strings.deleteSkillConfirmDescription) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(name)
                    },
                ) {
                    Text(strings.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(strings.cancel)
                }
            },
        )
    }
}

@Composable
private fun SkillPill(label: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun SkillRequirementsCard(
    detail: SkillDetail,
    strings: AppStrings,
    onReload: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copiedCommand by remember { mutableStateOf<String?>(null) }
    val requirements = detail.requirements
    SettingsSection(strings.setupRequired) {
        Column(Modifier.padding(16.dp)) {
            Text(
                strings.setupDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            detail.installOptions.forEach { option ->
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option.command,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(option.command))
                            copiedCommand = option.command
                        },
                    ) {
                        Text(
                            if (copiedCommand == option.command) strings.copied else strings.copy,
                        )
                    }
                }
            }
            if (requirements.missingBins.isNotEmpty()) {
                RequirementLine(strings.missingCommands, requirements.missingBins)
            }
            if (requirements.missingEnv.isNotEmpty()) {
                RequirementLine(strings.missingEnvironment, requirements.missingEnv)
            }
            TextButton(onClick = onReload, modifier = Modifier.padding(top = 4.dp)) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(strings.checkAgain)
            }
        }
    }
}

@Composable
private fun RequirementLine(label: String, items: List<String>) {
    Text(
        "$label: ${items.joinToString(", ")}",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SkillInstructionsCard(markdown: String, strings: AppStrings) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val longEnough = markdown.lines().size > 10 || markdown.length > 600
    SettingsSection(strings.skillInstructions) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "SKILL.md",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Text(
                    markdown.ifBlank { strings.noSkillInstructions },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = if (expanded) Int.MAX_VALUE else 10,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            if (longEnough) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) strings.showLess else strings.showMore)
                }
            }
        }
    }
}

private fun skillSourceLabel(source: String, strings: AppStrings): String = when (source) {
    "workspace" -> strings.sourceCustom
    "builtin" -> strings.sourceBuiltin
    else -> source.ifBlank { strings.other }
}

private fun skillStatusLabel(
    enabled: Boolean,
    available: Boolean,
    strings: AppStrings,
): String = when {
    !enabled -> strings.skillDisabled
    available -> strings.skillEnabled
    else -> strings.needsSetup
}

private enum class McpTab { Overview, Tools, Connection }

private val McpCustomFormSaver = listSaver<McpCustomForm, String>(
    save = {
        listOf(
            it.name,
            it.transport,
            it.auth,
            it.command,
            it.args,
            it.url,
            it.env,
            it.headers,
            it.toolTimeout,
            it.useProxy.toString(),
        )
    },
    restore = {
        McpCustomForm(
            name = it[0],
            transport = it[1],
            auth = it[2],
            command = it[3],
            args = it[4],
            url = it[5],
            env = it[6],
            headers = it[7],
            toolTimeout = it[8],
            useProxy = it[9].toBoolean(),
        )
    },
)

@Composable
private fun McpPage(
    state: UiState,
    strings: AppStrings,
    query: String,
    onSelect: (String, McpTab) -> Unit,
    onAction: (String, String) -> Unit,
    onSaveServer: (McpCustomForm) -> Unit,
    onImportConfig: (String) -> Unit,
    onCancelOAuth: () -> Unit,
) {
    val normalized = query.trim().lowercase()
    val presets = state.mcpPresets
        .filter { preset ->
            normalized.isEmpty() ||
                listOf(
                    preset.name,
                    preset.displayName,
                    preset.category,
                    preset.description,
                    preset.requires,
                    preset.note,
                    preset.transport,
                ).joinToString(" ").lowercase().contains(normalized)
        }
        .sortedWith(
            compareByDescending<McpPresetSummary> { it.installed }
                .thenBy { it.displayName.ifBlank { it.name }.lowercase() },
        )

    Column(Modifier.fillMaxSize()) {
        if (state.loadingFeatures) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PagePadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.mcpRequiresRestart) {
                item(key = "mcp-restart", span = { GridItemSpan(maxLineSpan) }) {
                    McpRestartNotice(strings)
                }
            }
            if (presets.isEmpty() && !state.loadingFeatures) {
                item(key = "mcp-empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (state.mcpPresets.isEmpty()) strings.noMcpPresets else strings.mcpNoMatchingPresets,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            gridItems(presets, key = { it.name }) { preset ->
                McpCard(
                    preset = preset,
                    state = state,
                    strings = strings,
                    onSelect = onSelect,
                    onAction = onAction,
                    onCancelOAuth = onCancelOAuth,
                )
            }
            item(key = "mcp-add", span = { GridItemSpan(maxLineSpan) }) {
                McpAddServerPanel(
                    state = state,
                    strings = strings,
                    onSaveServer = onSaveServer,
                    onImportConfig = onImportConfig,
                )
            }
        }
    }
}

@Composable
private fun McpRestartNotice(strings: AppStrings) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            strings.mcpRestartPending,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun McpCard(
    preset: McpPresetSummary,
    state: UiState,
    strings: AppStrings,
    onSelect: (String, McpTab) -> Unit,
    onAction: (String, String) -> Unit,
    onCancelOAuth: () -> Unit,
) {
    val oauthFlow = state.mcpOAuthFlow?.takeIf { it.name == preset.name }
    val oauthActive = oauthFlow != null
    val busy = state.mcpAction?.endsWith(":${preset.name}") == true
    val status = oauthFlow?.let { mcpOAuthStatusText(it, strings) }
        ?: mcpStatusLabel(preset, strings)
    val statusColor = when {
        oauthActive -> MaterialTheme.colorScheme.primary
        preset.runtimeStatus == "failed" || preset.status == "missing_dependency" ->
            MaterialTheme.colorScheme.error
        preset.runtimeStatus == "connected" || preset.configured ->
            MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = { onSelect(preset.name, McpTab.Overview) },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                McpAvatar(preset, size = 36.dp, corner = 10.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        preset.displayName.ifBlank { preset.name },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(preset.category, mcpTransportLabel(preset.transport))
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                preset.description.ifBlank { preset.note },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                val padding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                when {
                    oauthActive -> TextButton(
                        onClick = onCancelOAuth,
                        contentPadding = padding,
                    ) {
                        Text(strings.mcpCancelSignIn, maxLines = 1)
                    }

                    preset.isPlugin -> Switch(
                        checked = preset.enabled == true,
                        onCheckedChange = { onAction(if (it) "enable" else "disable", preset.name) },
                        enabled = !busy,
                    )

                    preset.installed && preset.configured -> TextButton(
                        onClick = { onSelect(preset.name, McpTab.Overview) },
                        enabled = !busy,
                        contentPadding = padding,
                    ) { Text(strings.mcpManage, maxLines = 1) }

                    !preset.installSupported && !preset.installed -> TextButton(
                        onClick = {},
                        enabled = false,
                        contentPadding = padding,
                    ) { Text(strings.mcpComingSoon, maxLines = 1) }

                    else -> TextButton(
                        onClick = { onSelect(preset.name, McpTab.Connection) },
                        enabled = !busy,
                        contentPadding = padding,
                    ) {
                        Text(
                            if (preset.needsConnectionForm || preset.requiredFields.isNotEmpty()) {
                                strings.mcpConfigure
                            } else {
                                strings.mcpConnect
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun McpAvatar(preset: McpPresetSummary, size: Dp = 40.dp, corner: Dp = 12.dp) {
    val shape = RoundedCornerShape(corner)
    val logo = rememberMcpLogo(preset.logoUrl)
    if (logo != null) {
        Image(
            bitmap = logo,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), shape)
                .padding(4.dp),
        )
        return
    }
    val background = preset.brandColor
        ?.let { raw -> runCatching { Color(android.graphics.Color.parseColor(raw)) }.getOrNull() }
        ?: MaterialTheme.colorScheme.primaryContainer
    val foreground = if (preset.brandColor != null) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Box(
        modifier = Modifier.size(size).background(background, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            mcpInitials(preset),
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
        )
    }
}

@Composable
private fun McpDetailPage(
    name: String,
    state: UiState,
    strings: AppStrings,
    tab: McpTab,
    onTabChange: (McpTab) -> Unit,
    onAction: (String, String) -> Unit,
    onFieldChange: (String, String, String) -> Unit,
    onUpdateTools: (String, List<String>) -> Unit,
    onStartOAuth: (String, Boolean) -> Unit,
    onCompleteOAuth: (String) -> Unit,
    onCancelOAuth: () -> Unit,
) {
    val preset = state.mcpPresets.firstOrNull { it.name == name }
    if (preset == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (state.loadingFeatures) strings.mcpConnecting else strings.mcpNoMatchingPresets,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "mcp-detail-summary") {
            McpDetailSummary(preset, strings)
        }
        if (!preset.isPlugin) {
            item(key = "mcp-detail-tabs") {
                McpTabRow(tab, strings, onTabChange)
            }
            item(key = "mcp-detail-body") {
                when (tab) {
                    McpTab.Overview -> McpOverviewContent(
                        preset = preset,
                        strings = strings,
                        onOpenTools = { onTabChange(McpTab.Tools) },
                    )

                    McpTab.Tools -> McpToolsContent(
                        preset = preset,
                        state = state,
                        strings = strings,
                        onAction = onAction,
                        onUpdateTools = onUpdateTools,
                        onOpenConnection = { onTabChange(McpTab.Connection) },
                    )

                    McpTab.Connection -> McpConnectionContent(
                        preset = preset,
                        state = state,
                        strings = strings,
                        onAction = onAction,
                        onFieldChange = onFieldChange,
                        onStartOAuth = onStartOAuth,
                        onCompleteOAuth = onCompleteOAuth,
                        onCancelOAuth = onCancelOAuth,
                    )
                }
            }
        } else {
            item(key = "mcp-plugin") {
                McpPluginCard(preset, state, strings, onAction)
            }
        }
        if (state.mcpRequiresRestart) {
            item(key = "mcp-detail-restart") { McpRestartNotice(strings) }
        }
    }
}

@Composable
private fun McpDetailSummary(preset: McpPresetSummary, strings: AppStrings) {
    val uriHandler = LocalUriHandler.current
    SettingsSection(strings.mcpPresets) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                McpAvatar(preset)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        preset.displayName.ifBlank { preset.name },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        listOf(preset.category, mcpTransportLabel(preset.transport))
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                McpStatusPill(mcpStatusLabel(preset, strings), preset)
            }
            val description = preset.description.ifBlank { preset.note }
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            preset.error?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (preset.docsUrl.isNotBlank()) {
                TextButton(
                    onClick = { runCatching { uriHandler.openUri(preset.docsUrl) } },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                ) {
                    Text(strings.mcpOpenDocs)
                }
            }
        }
    }
}

@Composable
private fun McpStatusPill(label: String, preset: McpPresetSummary) {
    val color = when {
        preset.runtimeStatus == "failed" || preset.status == "missing_dependency" ->
            MaterialTheme.colorScheme.error
        preset.runtimeStatus == "connected" || preset.configured ->
            MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
private fun McpTabRow(
    selected: McpTab,
    strings: AppStrings,
    onSelect: (McpTab) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        McpTab.entries.forEach { tab ->
            val active = tab == selected
            Surface(
                onClick = { onSelect(tab) },
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                },
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    when (tab) {
                        McpTab.Overview -> strings.mcpOverview
                        McpTab.Tools -> strings.mcpTools
                        McpTab.Connection -> strings.mcpConnection
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun McpOverviewContent(
    preset: McpPresetSummary,
    strings: AppStrings,
    onOpenTools: () -> Unit,
) {
    val typedTotal = preset.toolCount ?: preset.toolNames.size
    val selectedTools = if (preset.enabledTools.contains("*")) {
        typedTotal
    } else {
        preset.enabledTools.size
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSection(strings.mcpOverview) {
            InfoRow(strings.mcpStatus, mcpStatusLabel(preset, strings))
            InfoRow(strings.mcpTransport, mcpTransportLabel(preset.transport))
            if (typedTotal > 0) {
                InfoRow(
                    strings.mcpToolScope,
                    strings.mcpSelectedTools(selectedTools, typedTotal),
                )
            }
            val auth = mcpAuthLabel(preset, strings)
            if (auth.isNotBlank()) {
                InfoRow(strings.mcpAuthorization, auth, showDivider = false)
            }
        }
        if (preset.toolNames.isNotEmpty()) {
            SettingsSection(strings.mcpTools) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        preset.toolNames.take(4)
                            .joinToString("\n") { displayToolName(it, preset.name) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (typedTotal > preset.toolNames.take(4).size) {
                        Text(
                            "+${typedTotal - preset.toolNames.take(4).size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    TextButton(
                        onClick = onOpenTools,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text(strings.mcpManage)
                    }
                }
            }
        }
    }
}

@Composable
private fun McpToolsContent(
    preset: McpPresetSummary,
    state: UiState,
    strings: AppStrings,
    onAction: (String, String) -> Unit,
    onUpdateTools: (String, List<String>) -> Unit,
    onOpenConnection: () -> Unit,
) {
    val toolNames = preset.toolNames
    var query by remember(preset.name) { mutableStateOf("") }
    var draft by remember(preset.name, preset.enabledTools) {
        mutableStateOf(normalizeTools(preset.enabledTools))
    }
    var inspectionRequested by remember(preset.name) { mutableStateOf(false) }
    val allowAll = draft.contains("*")
    val knownCount = preset.toolCount ?: toolNames.size
    val selectedCount = if (allowAll) knownCount else draft.size
    val busy = state.mcpAction == "tools:${preset.name}"
    val testBusy = state.mcpAction == "test:${preset.name}"
    val normalizedQuery = query.trim().lowercase()
    val filtered = toolNames.filter { it.lowercase().contains(normalizedQuery) }
    val dirty = normalizeTools(preset.enabledTools) != normalizeTools(draft)

    LaunchedEffect(preset.name, preset.installed, preset.configured, preset.error, state.mcpAction) {
        if (
            toolNames.isEmpty() &&
            preset.installed &&
            preset.configured &&
            preset.error.isNullOrBlank() &&
            state.mcpAction == null &&
            !inspectionRequested
        ) {
            inspectionRequested = true
            onAction("test", preset.name)
        }
    }

    fun toggle(tool: String) {
        val next = (if (allowAll) toolNames else draft).toMutableSet()
        if (!next.add(tool)) next.remove(tool)
        draft = if (toolNames.isNotEmpty() && next.size == toolNames.size) {
            listOf("*")
        } else {
            next.toList()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (toolNames.isEmpty()) {
            SettingsSection(strings.mcpTools) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        preset.error ?: strings.mcpNoToolsAvailable,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (preset.error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (testBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(
                            onClick = {
                                if (preset.installed && preset.configured) {
                                    onAction("test", preset.name)
                                } else {
                                    onOpenConnection()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                        ) {
                            Text(
                                if (preset.installed && preset.configured) {
                                    strings.mcpReloadTools
                                } else {
                                    strings.mcpConnect
                                },
                            )
                        }
                    }
                }
            }
            return
        }

        SettingsSection(strings.mcpTools) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(strings.mcpSearchTools) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        strings.mcpSelectedTools(selectedCount, knownCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { draft = listOf("*") },
                        enabled = !busy,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text(strings.mcpAllTools) }
                    TextButton(
                        onClick = { draft = emptyList() },
                        enabled = !busy,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text(strings.mcpClearTools) }
                    TextButton(
                        onClick = { onAction("test", preset.name) },
                        enabled = !busy && !testBusy,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text(strings.mcpReloadTools) }
                }
                if (filtered.isEmpty()) {
                    Text(
                        strings.mcpNoMatchingTools,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                filtered.forEach { tool ->
                    val checked = allowAll || draft.contains(tool)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { toggle(tool) },
                            enabled = !busy,
                        )
                        Text(
                            displayToolName(tool, preset.name),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (dirty) {
                    TextButton(
                        onClick = {
                            onUpdateTools(
                                preset.name,
                                if (allowAll) listOf("*") else draft.sorted(),
                            )
                        },
                        enabled = !busy && toolNames.isNotEmpty(),
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(strings.mcpApplyChanges)
                    }
                }
            }
        }
    }
}

@Composable
private fun McpConnectionContent(
    preset: McpPresetSummary,
    state: UiState,
    strings: AppStrings,
    onAction: (String, String) -> Unit,
    onFieldChange: (String, String, String) -> Unit,
    onStartOAuth: (String, Boolean) -> Unit,
    onCompleteOAuth: (String) -> Unit,
    onCancelOAuth: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val values = state.mcpFieldValues[preset.name].orEmpty()
    val flow = state.mcpOAuthFlow?.takeIf { it.name == preset.name }
    val busy = state.mcpAction?.endsWith(":${preset.name}") == true
    var callbackUrl by rememberSaveable(preset.name) { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    val requiredFieldsComplete = preset.requiredFields
        .filter { it.required && !it.configured }
        .all { !values[it.name].isNullOrBlank() }
    val proxyValue = values["use_proxy"] ?: preset.useProxy.toString()

    LaunchedEffect(flow?.flowId, flow?.status) {
        val url = flow?.authorizationUrl
        if (flow?.status == "authorization_required" && !url.isNullOrBlank()) {
            runCatching { uriHandler.openUri(url) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSection(strings.mcpConnectionDetails) {
            Column(Modifier.padding(16.dp)) {
                if (preset.connectionSummary.isNotBlank()) {
                    Text(
                        preset.connectionSummary,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    listOf(
                        mcpTransportLabel(preset.transport),
                        mcpAuthLabel(preset, strings),
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        if (preset.remote) {
            SettingsSection(strings.mcpConnection) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(strings.mcpUseProxy, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            strings.mcpUseProxyDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = proxyValue.toBoolean(),
                        onCheckedChange = { enabled ->
                            onFieldChange(preset.name, "use_proxy", enabled.toString())
                            if (preset.installed && preset.configured) {
                                onAction("proxy", preset.name)
                            }
                        },
                        enabled = !busy,
                    )
                }
            }
        }

        if (preset.requiredFields.isNotEmpty()) {
            SettingsSection(strings.mcpCredentials) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    preset.requiredFields.forEach { field ->
                        McpCredentialField(
                            field = field,
                            value = values[field.name].orEmpty(),
                            strings = strings,
                            onValueChange = { onFieldChange(preset.name, field.name, it) },
                        )
                    }
                }
            }
        }

        if (preset.auth == "oauth") {
            SettingsSection(strings.mcpAuthorization) {
                Column(Modifier.padding(16.dp)) {
                    if (flow == null) {
                        Text(
                            if (preset.status == "authorization_required") {
                                strings.mcpNeedsAuthorization
                            } else {
                                strings.mcpAuthorization
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            mcpOAuthStatusText(flow, strings),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (flow.authorizationUrl != null) {
                            OutlinedTextField(
                                value = callbackUrl,
                                onValueChange = { callbackUrl = it },
                                label = { Text(strings.mcpCallbackUrl) },
                                placeholder = { Text(strings.mcpCallbackHelp) },
                                minLines = 2,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                            )
                            Text(
                                strings.mcpCallbackHelp,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (state.mcpOAuthCallbackError != null) {
                                Text(
                                    state.mcpOAuthCallbackError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = {
                                        flow.authorizationUrl?.let { url ->
                                            runCatching { uriHandler.openUri(url) }
                                        }
                                    },
                                ) { Text(strings.mcpContinueSignIn) }
                                TextButton(
                                    onClick = { onCompleteOAuth(callbackUrl.trim()) },
                                    enabled = callbackUrl.isNotBlank(),
                                ) { Text(strings.mcpFinishSignIn) }
                            }
                        }
                        TextButton(onClick = onCancelOAuth) {
                            Text(strings.mcpCancelSignIn)
                        }
                    }
                    if (flow == null) {
                        TextButton(
                            onClick = {
                                onStartOAuth(
                                    preset.name,
                                    preset.installed && preset.configured,
                                )
                            },
                            enabled = !busy && preset.installSupported,
                        ) {
                            Text(
                                if (preset.installed && preset.configured) {
                                    strings.mcpReauthorize
                                } else {
                                    strings.mcpSignIn
                                },
                            )
                        }
                    }
                }
            }
        }

        SettingsSection(strings.actions) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (preset.auth != "oauth" && (!preset.installed || !preset.configured)) {
                    TextButton(
                        onClick = { onAction("enable", preset.name) },
                        enabled = !busy && preset.installSupported && requiredFieldsComplete,
                    ) {
                        Text(strings.mcpEnable)
                    }
                }
                if (preset.installed && preset.configured && preset.installSupported) {
                    TextButton(
                        onClick = { onAction("reconnect", preset.name) },
                        enabled = !busy,
                    ) { Text(strings.mcpReconnect) }
                }
                if (preset.installed) {
                    TextButton(
                        onClick = { confirmRemove = true },
                        enabled = !busy,
                    ) {
                        Text(strings.mcpRemove, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (!preset.installSupported && !preset.installed) {
                    Text(
                        strings.mcpComingSoon,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(strings.mcpRemoveTitle(preset.displayName.ifBlank { preset.name })) },
            text = { Text(strings.mcpRemoveDescription) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onAction("remove", preset.name)
                    },
                ) {
                    Text(strings.mcpRemove, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(strings.cancel) }
            },
        )
    }
}

@Composable
private fun McpCredentialField(
    field: McpPresetField,
    value: String,
    strings: AppStrings,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(field.label.ifBlank { field.name }) },
        placeholder = {
            Text(if (field.configured) strings.mcpKeepExisting else field.placeholder)
        },
        visualTransformation = if (field.secret) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        supportingText = if (field.configured) {
            { Text(strings.mcpConfiguredField) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun McpPluginCard(
    preset: McpPresetSummary,
    state: UiState,
    strings: AppStrings,
    onAction: (String, String) -> Unit,
) {
    val busy = state.mcpAction?.endsWith(":${preset.name}") == true
    SettingsSection(strings.mcpPresets) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(strings.mcpPluginManageOnly, style = MaterialTheme.typography.bodySmall)
                if (preset.connectionSummary.isNotBlank()) {
                    Text(
                        preset.connectionSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = preset.enabled == true,
                onCheckedChange = { onAction(if (it) "enable" else "disable", preset.name) },
                enabled = !busy,
            )
        }
    }
}

@Composable
private fun McpAddServerPanel(
    state: UiState,
    strings: AppStrings,
    onSaveServer: (McpCustomForm) -> Unit,
    onImportConfig: (String) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf("") }
    var form by rememberSaveable(stateSaver = McpCustomFormSaver) { mutableStateOf(McpCustomForm()) }
    var importText by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val busy = state.mcpAction?.startsWith("custom:") == true || state.mcpAction == "import"

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(strings.mcpAddServer, style = MaterialTheme.typography.titleSmall)
            Text(
                strings.mcpAddServerDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { mode = if (mode == "custom") "" else "custom" },
                ) { Text(strings.mcpCustomServer) }
                TextButton(
                    onClick = { mode = if (mode == "import") "" else "import" },
                ) { Text(strings.mcpImport) }
            }

            if (mode == "custom") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { form = form.copy(name = it) },
                        label = { Text(strings.mcpServerName) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    McpChoiceRow(
                        options = listOf(
                            "stdio" to "STDIO",
                            "streamableHttp" to "HTTP",
                            "sse" to "SSE",
                        ),
                        selected = form.transport,
                        onSelect = { form = form.copy(transport = it) },
                    )
                    if (form.remote) {
                        OutlinedTextField(
                            value = form.url,
                            onValueChange = { form = form.copy(url = it) },
                            label = { Text(strings.mcpServerUrl) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        McpChoiceRow(
                            options = listOf(
                                "none" to strings.mcpAuthNone,
                                "oauth" to "OAuth",
                                "headers" to strings.mcpAuthHeaders,
                            ),
                            selected = form.auth,
                            onSelect = { form = form.copy(auth = it) },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                strings.mcpUseProxy,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = form.useProxy,
                                onCheckedChange = { form = form.copy(useProxy = it) },
                            )
                        }
                        if (form.auth == "headers") {
                            OutlinedTextField(
                                value = form.headers,
                                onValueChange = { form = form.copy(headers = it) },
                                label = { Text(strings.mcpHeaders) },
                                placeholder = { Text("{\"Authorization\":\"Bearer ...\"}") },
                                minLines = 2,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                strings.mcpHeadersHelp,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = form.command,
                            onValueChange = { form = form.copy(command = it) },
                            label = { Text(strings.mcpCommand) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    TextButton(onClick = { advanced = !advanced }) {
                        Text(if (advanced) strings.mcpHideAdvanced else strings.mcpAdvancedOptions)
                    }
                    if (advanced) {
                        if (!form.remote) {
                            OutlinedTextField(
                                value = form.args,
                                onValueChange = { form = form.copy(args = it) },
                                label = { Text(strings.mcpArguments) },
                                placeholder = { Text("[\"-y\", \"docs-mcp\"]") },
                                minLines = 2,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        OutlinedTextField(
                            value = form.env,
                            onValueChange = { form = form.copy(env = it) },
                            label = { Text(strings.mcpEnvironment) },
                            placeholder = { Text("{\"API_KEY\":\"...\"}") },
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = form.toolTimeout,
                            onValueChange = { form = form.copy(toolTimeout = it) },
                            label = { Text(strings.mcpToolTimeout) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TextButton(
                        onClick = { onSaveServer(form) },
                        enabled = form.canSave && !busy,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(strings.mcpSaveServer)
                    }
                }
            }

            if (mode == "import") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text(strings.mcpImportConfig) },
                        placeholder = { Text(strings.mcpConfigPlaceholder) },
                        minLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { onImportConfig(importText) },
                        enabled = importText.isNotBlank() && !busy,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        if (state.mcpAction == "import") {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(strings.mcpImport)
                    }
                }
            }
        }
    }
}

@Composable
private fun McpChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Surface(
                onClick = { onSelect(value) },
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                },
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun normalizeTools(tools: List<String>): List<String> =
    if (tools.contains("*")) listOf("*") else tools.sorted()

private fun displayToolName(tool: String, server: String): String =
    tool.removePrefix("mcp_${server}_")

private fun mcpTransportLabel(transport: String): String = when (transport) {
    "streamableHttp" -> "Streamable HTTP"
    "stdio" -> "STDIO"
    "sse" -> "SSE"
    else -> transport.ifBlank { "MCP" }
}

private fun mcpAuthLabel(preset: McpPresetSummary, strings: AppStrings): String = when {
    preset.auth == "oauth" -> "OAuth"
    preset.requiredFields.isNotEmpty() -> strings.mcpCredentials
    else -> ""
}

private fun mcpStatusLabel(preset: McpPresetSummary, strings: AppStrings): String = when {
    preset.isPlugin -> if (preset.enabled == true) strings.mcpEnabled else strings.mcpDisabled
    preset.runtimeStatus == "connecting" -> strings.mcpConnecting
    preset.runtimeStatus == "failed" -> strings.mcpConnectionFailed
    preset.runtimeStatus == "connected" -> strings.mcpConnected
    preset.status == "configured" -> strings.mcpConfigured
    preset.status == "missing_credentials" -> strings.mcpNeedsKey
    preset.status == "missing_dependency" -> strings.mcpNeedsDependency
    preset.status == "authorization_required" -> strings.mcpNeedsAuthorization
    preset.status == "coming_soon" -> strings.mcpComingSoon
    else -> strings.mcpNotEnabled
}

private fun mcpInitials(preset: McpPresetSummary): String {
    val words = preset.displayName.ifBlank { preset.name }
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    val initials = words.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
    return initials.ifBlank { preset.name.take(2).uppercase() }
}

private fun mcpOAuthStatusText(flow: McpOAuthFlowPayload, strings: AppStrings): String =
    when (flow.status) {
        "starting" -> strings.mcpSignInPreparing
        "authorization_required" -> strings.mcpSignInWaiting
        "connecting" -> strings.mcpSignInConnecting
        "authorized" -> strings.mcpSignInAuthorized
        "connected" -> strings.mcpConnected
        "failed" -> flow.error ?: strings.mcpOAuthFailed
        "cancelled" -> strings.mcpOAuthCancelled
        else -> flow.status
    }

@Composable
private fun CliAppsPage(state: UiState, strings: AppStrings) {
    CapabilityList(
        loading = state.loadingFeatures,
        empty = state.cliApps.isEmpty(),
        emptyText = strings.noCliApps,
    ) {
        items(state.cliApps, key = { it.name }) { app ->
            val label = when {
                app.installed -> strings.installed
                app.available -> strings.installable
                app.status.isNotBlank() -> app.status
                else -> strings.unavailable
            }
            CapabilityCard(
                title = app.displayName.ifBlank { app.name },
                subtitle = listOf(app.category, app.description)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                badge = null,
                badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (app.installed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun AutomationsPage(
    state: UiState,
    strings: AppStrings,
    onSelect: (AutomationJob) -> Unit,
) {
    CapabilityList(
        loading = state.loadingFeatures,
        empty = state.automations.isEmpty(),
        emptyText = strings.noAutomations,
    ) {
        items(state.automations, key = { it.id }) { job ->
            AutomationRow(
                job = job,
                strings = strings,
                onClick = { onSelect(job) },
            )
        }
    }
}

/** One automation row: the whole row opens the detail page, matching the WebUI's task list. */
@Composable
private fun AutomationRow(
    job: AutomationJob,
    strings: AppStrings,
    onClick: () -> Unit,
) {
    val status = automationStatusLabel(job, strings)
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    job.name.ifBlank { job.id },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    automationScheduleText(job, strings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (status != null) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = automationStatusColor(job, status, strings),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The WebUI's task dialog: schedule, run state, instructions, linked chat, and actions. */
@Composable
private fun AutomationDetailPage(
    job: AutomationJob?,
    strings: AppStrings,
    onToggle: (String, Boolean) -> Unit,
    onRun: (String) -> Unit,
    onEdit: () -> Unit,
    onChangeChat: () -> Unit,
    onDelete: (String) -> Unit,
) {
    if (job == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                strings.automationNotFound,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val status = automationStatusLabel(job, strings)
    val canManage = !job.protected
    val canRun = canManage && job.enabled && !job.state.pending &&
        !job.isLocalTrigger && job.origin != null
    // Only a bound scheduled task has a movable chat; local triggers run from the CLI.
    val canChangeChat = canManage && !job.isLocalTrigger && job.origin != null &&
        job.chatBindingRevision != null
    val command = job.trigger?.command ?: job.payload.command.orEmpty()
    val message = if (job.isLocalTrigger) command.ifBlank { job.payload.message } else job.payload.message
    var confirmDelete by remember(job.id) { mutableStateOf(false) }
    var copied by remember(job.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSection(strings.automationTaskInfo) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (status != null) {
                            SkillPill(status, color = automationStatusColor(job, status, strings))
                        }
                        if (job.protected && status != strings.automationSystem) {
                            SkillPill(strings.automationSystem)
                        }
                        if (job.isLocalTrigger) SkillPill(strings.localTrigger)
                        if (job.deleteAfterRun) SkillPill(strings.automationOneTime)
                    }
                    Text(
                        automationScheduleText(job, strings),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
        item {
            SettingsSection(strings.automationSchedule) {
                if (!job.isLocalTrigger) {
                    InfoRow(strings.automationNextRun, automationNextRun(job, strings))
                }
                InfoRow(
                    strings.automationLastRun,
                    job.state.lastRunAtMs?.let { lastRun ->
                        listOf(
                            strings.automationRelative(lastRun - System.currentTimeMillis()),
                            formatAutomationTime(lastRun),
                        ).joinToString(" · ")
                    } ?: strings.automationNeverRun,
                )
                InfoRow(
                    strings.automationLastStatus,
                    automationRunStatus(job.state.lastStatus, strings) ?: "—",
                )
                val timezone = job.schedule.tz?.takeIf { it.isNotBlank() }
                if (job.schedule.kind == "cron" && timezone != null) {
                    InfoRow(strings.automationTimezone, timezone)
                }
                if (job.deleteAfterRun) {
                    InfoRow(strings.automationOneTime, strings.automationOneTimeHint)
                }
                job.createdAtMs?.let {
                    InfoRow(strings.automationCreated, formatAutomationTime(it))
                }
                job.updatedAtMs?.let {
                    InfoRow(strings.automationUpdated, formatAutomationTime(it))
                }
                InfoRow(strings.automationIdentifier, job.id, showDivider = false)
            }
        }
        if (message.isNotBlank()) {
            item {
                SettingsSection(strings.automationInstructions) {
                    Column(Modifier.padding(16.dp)) {
                        SelectionContainer {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = if (job.isLocalTrigger) {
                                    FontFamily.Monospace
                                } else {
                                    null
                                },
                            )
                        }
                        if (job.isLocalTrigger) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(message))
                                        copied = true
                                    },
                                ) {
                                    Text(if (copied) strings.copied else strings.copy)
                                }
                            }
                        }
                    }
                }
            }
        }
        job.origin?.let { origin ->
            item {
                SettingsSection(strings.automationLinkedChat) {
                    Column(Modifier.padding(16.dp)) {
                        val title = origin.title.ifBlank {
                            origin.sessionKey ?: origin.chatId ?: origin.channel
                        }
                        Text(title, style = MaterialTheme.typography.bodyMedium)
                        val detail = origin.sessionKey
                            ?: listOf(origin.channel, origin.chatId.orEmpty())
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                        if (detail.isNotBlank() && detail != title) {
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        val lastError = job.state.lastError
        if (!lastError.isNullOrBlank()) {
            item {
                SettingsSection(strings.automationLastError) {
                    Text(
                        lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        if (job.state.runHistory.isNotEmpty()) {
            item {
                val runs = job.state.runHistory
                SettingsSection(strings.automationRecentRuns) {
                    runs.asReversed().forEachIndexed { index, run ->
                        AutomationRunRow(
                            run = run,
                            strings = strings,
                            showDivider = index < runs.lastIndex,
                        )
                    }
                }
            }
        }
        item {
            if (canManage) {
                SettingsSection(strings.actions) {
                    SettingRow(title = strings.automationEditTitle) {
                        TextButton(onClick = onEdit) {
                            Text(strings.automationEdit)
                        }
                    }
                    SettingRow(
                        title = if (job.enabled) {
                            strings.automationDisable
                        } else {
                            strings.automationEnable
                        },
                        description = if (job.state.pending) strings.automationRunning else null,
                    ) {
                        Switch(
                            checked = job.enabled,
                            onCheckedChange = { onToggle(job.id, it) },
                        )
                    }
                    if (!job.isLocalTrigger) {
                        SettingRow(title = strings.automationRunNow) {
                            TextButton(
                                onClick = { onRun(job.id) },
                                enabled = canRun,
                            ) {
                                Text(strings.run)
                            }
                        }
                    }
                    if (canChangeChat) {
                        SettingRow(title = strings.automationChatTitle) {
                            TextButton(onClick = onChangeChat) {
                                Text(strings.automationChangeChat)
                            }
                        }
                    }
                    SettingRow(title = strings.automationDelete, showDivider = false) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text(strings.delete, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                SettingsSection(strings.automationSystem) {
                    Text(
                        strings.automationSystemHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(strings.automationDeleteTitle) },
            text = {
                Text(
                    strings.automationDeleteDescription(job.name.ifBlank { job.id }),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(job.id)
                    },
                ) {
                    Text(strings.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(strings.cancel)
                }
            },
        )
    }
}

private enum class AutomationEveryUnit(val wire: String, val millis: Long) {
    Second("second", 1_000L),
    Minute("minute", 60_000L),
    Hour("hour", 3_600_000L),
    Day("day", 86_400_000L),
    ;

    companion object {
        fun fromWire(value: String?): AutomationEveryUnit =
            entries.firstOrNull { it.wire == value } ?: Minute
    }
}

private data class AutomationEditDraft(
    val name: String,
    val message: String,
    val scheduleKind: String,
    val everyValue: String,
    val everyUnit: AutomationEveryUnit,
    val cronExpr: String,
    val tz: String,
    val atMs: Long,
)

private fun automationEditDraft(job: AutomationJob): AutomationEditDraft {
    val (everyValue, everyUnit) = automationEveryDraft(job.schedule.everyMs ?: 3_600_000L)
    return AutomationEditDraft(
        name = job.name,
        message = job.payload.message,
        scheduleKind = job.schedule.kind.takeIf { it == "at" || it == "cron" } ?: "every",
        everyValue = everyValue,
        everyUnit = everyUnit,
        cronExpr = job.schedule.expr ?: "0 9 * * *",
        tz = job.schedule.tz.orEmpty(),
        atMs = job.schedule.atMs ?: (System.currentTimeMillis() + 3_600_000L),
    )
}

/** Picks the largest unit that divides [millis] exactly, as the WebUI's edit dialog does. */
private fun automationEveryDraft(millis: Long): Pair<String, AutomationEveryUnit> {
    for (unit in AutomationEveryUnit.entries.reversed()) {
        if (millis >= unit.millis && millis % unit.millis == 0L) {
            return (millis / unit.millis).toString() to unit
        }
    }
    return (millis / 60_000L).coerceAtLeast(1L).toString() to AutomationEveryUnit.Minute
}

private fun automationUnitLabel(unit: AutomationEveryUnit, strings: AppStrings): String = when (unit) {
    AutomationEveryUnit.Second -> strings.automationUnitSecond
    AutomationEveryUnit.Minute -> strings.automationUnitMinute
    AutomationEveryUnit.Hour -> strings.automationUnitHour
    AutomationEveryUnit.Day -> strings.automationUnitDay
}

private fun automationChannelLabel(channel: String, strings: AppStrings): String = when {
    channel == "websocket" -> strings.automationChatWeb
    channel.isBlank() -> ""
    else -> channel.replaceFirstChar { it.uppercase() }
}

private fun automationScheduleChanged(
    job: AutomationJob,
    schedule: AutomationScheduleUpdate?,
): Boolean {
    if (schedule == null) return false
    val current = job.schedule
    if (schedule.kind != current.kind) return true
    return when (schedule.kind) {
        "every" -> schedule.everyMs != current.everyMs
        "cron" -> schedule.expr != (current.expr ?: "") ||
            (schedule.tz ?: "") != (current.tz ?: "")
        "at" -> schedule.atMs != current.atMs
        else -> true
    }
}

private fun showAutomationDateTimePicker(
    context: Context,
    initialMs: Long,
    onPicked: (Long) -> Unit,
) {
    val initial = Calendar.getInstance().apply { timeInMillis = initialMs }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val date = Calendar.getInstance().apply {
                timeInMillis = initialMs
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    date.set(Calendar.HOUR_OF_DAY, hour)
                    date.set(Calendar.MINUTE, minute)
                    date.set(Calendar.SECOND, 0)
                    date.set(Calendar.MILLISECOND, 0)
                    onPicked(date.timeInMillis)
                },
                initial.get(Calendar.HOUR_OF_DAY),
                initial.get(Calendar.MINUTE),
                true,
            ).show()
        },
        initial.get(Calendar.YEAR),
        initial.get(Calendar.MONTH),
        initial.get(Calendar.DAY_OF_MONTH),
    ).show()
}

/** The WebUI's edit dialog: name, instructions, and schedule, with local triggers name-only. */
@Composable
private fun AutomationEditPage(
    job: AutomationJob,
    state: UiState,
    strings: AppStrings,
    onCancel: () -> Unit,
    onSave: (AutomationUpdate) -> Unit,
) {
    val context = LocalContext.current
    val initial = remember(job.id) { automationEditDraft(job) }
    var name by rememberSaveable(job.id) { mutableStateOf(initial.name) }
    var message by rememberSaveable(job.id) { mutableStateOf(initial.message) }
    var scheduleKind by rememberSaveable(job.id) { mutableStateOf(initial.scheduleKind) }
    var everyValue by rememberSaveable(job.id) { mutableStateOf(initial.everyValue) }
    var everyUnitName by rememberSaveable(job.id) { mutableStateOf(initial.everyUnit.wire) }
    var cronExpr by rememberSaveable(job.id) { mutableStateOf(initial.cronExpr) }
    var tz by rememberSaveable(job.id) { mutableStateOf(initial.tz) }
    var atMs by rememberSaveable(job.id) { mutableStateOf(initial.atMs) }

    val localTrigger = job.isLocalTrigger
    val busy = state.automationAction == "update:${job.id}"
    val everyUnit = AutomationEveryUnit.fromWire(everyUnitName)
    val interval = everyValue.trim().toLongOrNull()?.takeIf { it > 0 }
    val everyMs = interval?.let { it * everyUnit.millis }
    val cron = cronExpr.trim()
    val schedule = when (scheduleKind) {
        "every" -> everyMs?.let { AutomationScheduleUpdate(kind = "every", everyMs = it) }
        "cron" -> if (cron.isNotEmpty()) {
            AutomationScheduleUpdate(
                kind = "cron",
                expr = cron,
                tz = tz.trim().takeIf { it.isNotEmpty() },
            )
        } else {
            null
        }
        "at" -> AutomationScheduleUpdate(kind = "at", atMs = atMs)
        else -> null
    }
    val scheduleChanged = !localTrigger && automationScheduleChanged(job, schedule)
    val validation = when {
        name.trim().isEmpty() -> strings.automationNameRequired
        localTrigger -> null
        message.trim().isEmpty() -> strings.automationMessageRequired
        scheduleKind == "every" && everyMs == null -> strings.automationIntervalRequired
        scheduleKind == "cron" && cron.isEmpty() -> strings.automationCronRequired
        scheduleKind == "at" && scheduleChanged && atMs <= System.currentTimeMillis() ->
            strings.automationFutureRequired
        else -> null
    }

    fun currentUpdate(): AutomationUpdate = if (localTrigger) {
        AutomationUpdate(name = name.trim())
    } else {
        AutomationUpdate(
            name = name.trim(),
            message = message.trim(),
            schedule = schedule.takeIf { scheduleChanged },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSection(strings.automationTaskInfo) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.automationName) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
        if (!localTrigger) {
            item {
                SettingsSection(strings.automationMessage) {
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text(strings.automationMessage) },
                        minLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }
            item {
                SettingsSection(strings.automationSchedule) {
                    Column {
                        Text(
                            strings.automationScheduleType,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp),
                        )
                        ChoiceRow(
                            options = listOf("every", "cron", "at"),
                            selected = scheduleKind,
                            label = {
                                when (it) {
                                    "cron" -> strings.automationScheduleCronType
                                    "at" -> strings.automationScheduleAtType
                                    else -> strings.automationScheduleEveryType
                                }
                            },
                            onSelect = { scheduleKind = it },
                        )
                        when (scheduleKind) {
                            "every" -> {
                                OutlinedTextField(
                                    value = everyValue,
                                    onValueChange = { value ->
                                        if (value.all { it.isDigit() }) everyValue = value
                                    },
                                    label = { Text(strings.automationEveryLabel) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                )
                                Text(
                                    strings.automationUnit,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                                )
                                ChoiceRow(
                                    options = AutomationEveryUnit.entries,
                                    selected = everyUnit,
                                    label = { automationUnitLabel(it, strings) },
                                    onSelect = { everyUnitName = it.wire },
                                )
                            }

                            "cron" -> {
                                OutlinedTextField(
                                    value = cronExpr,
                                    onValueChange = { cronExpr = it },
                                    label = { Text(strings.automationCronExpression) },
                                    placeholder = { Text("0 9 * * *") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = tz,
                                    onValueChange = { tz = it },
                                    label = { Text(strings.automationTimezone) },
                                    placeholder = { Text("Asia/Shanghai") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                )
                            }

                            "at" -> {
                                Surface(
                                    onClick = {
                                        showAutomationDateTimePicker(context, atMs) { atMs = it }
                                    },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                strings.automationRunAt,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                formatAutomationTime(atMs),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                        Text(
                                            strings.automationChooseTime,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
        val actionError = state.automationActionError
        if (actionError != null) {
            item {
                Text(
                    actionError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (validation != null) {
            item {
                Text(
                    validation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel, enabled = !busy) {
                    Text(strings.cancel)
                }
                TextButton(
                    onClick = { onSave(currentUpdate()) },
                    enabled = validation == null && !busy,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(strings.automationSave)
                }
            }
        }
    }
}

/** The WebUI's change-chat dialog: pick a destination, edit the instructions, then confirm. */
@Composable
private fun AutomationChatPage(
    job: AutomationJob,
    state: UiState,
    strings: AppStrings,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    val data = state.automationChats.takeIf { state.automationChatsFor == job.id }
    val loadError = state.automationChatsError.takeIf { state.automationChatsFor == job.id }
    var targetId by rememberSaveable(job.id) { mutableStateOf<String?>(null) }
    var message by rememberSaveable(job.id) { mutableStateOf(job.payload.message) }
    val current = data?.current ?: AutomationChat(
        id = "current",
        title = job.origin?.title.orEmpty().ifBlank {
            job.origin?.sessionKey.orEmpty().ifBlank { strings.automationCurrentChat }
        },
        channel = job.origin?.channel.orEmpty(),
    )
    val target = data?.chats?.firstOrNull { it.id == targetId && !it.unavailable }
    val busy = state.automationAction == "chat:${job.id}"
    val revision = data?.revision.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSection(strings.automationCurrentChat) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        current.title.ifBlank { current.id },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    automationChannelLabel(current.channel, strings)
                        .takeIf { it.isNotEmpty() }
                        ?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
            }
        }
        if (data == null) {
            item {
                SettingsSection(strings.automationNewChat) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (loadError == null) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                loadError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(onClick = onRetry) {
                                Text(strings.automationRetry)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                SettingsSection(strings.automationNewChat) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (data.chats.isEmpty()) {
                            Text(
                                strings.automationChatsEmpty,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        data.chats.forEach { chat ->
                            AutomationChatRow(
                                chat = chat,
                                current = chat.id == data.current?.id,
                                selected = chat.id == targetId,
                                strings = strings,
                                onClick = { targetId = chat.id },
                            )
                        }
                    }
                }
            }
            item {
                SettingsSection(strings.automationChatMessage) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            strings.automationChatHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text(strings.automationChatMessage) },
                            minLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                        )
                    }
                }
            }
            val actionError = state.automationActionError
            if (actionError != null) {
                item {
                    Text(
                        actionError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel, enabled = !busy) {
                        Text(strings.cancel)
                    }
                    TextButton(
                        onClick = {
                            target?.let { onConfirm(it.id, revision, message.trim()) }
                        },
                        enabled = target != null && message.trim().isNotEmpty() &&
                            revision.isNotEmpty() && !busy,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(strings.automationChatConfirm)
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationChatRow(
    chat: AutomationChat,
    current: Boolean,
    selected: Boolean,
    strings: AppStrings,
    onClick: () -> Unit,
) {
    val selectable = !chat.unavailable && !current
    Surface(
        onClick = onClick,
        enabled = selectable,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    chat.title.ifBlank { chat.id },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                automationChannelLabel(chat.channel, strings)
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }
            Spacer(Modifier.width(8.dp))
            when {
                current -> SkillPill(strings.automationCurrentChat)
                chat.unavailable -> SkillPill(strings.unavailable)
                selected -> Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AutomationRunRow(
    run: AutomationRun,
    strings: AppStrings,
    showDivider: Boolean,
) {
    val failed = run.status == "error"
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    automationRunStatus(run.status, strings) ?: run.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    formatAutomationTime(run.runAtMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                run.error?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            run.durationMs?.takeIf { it > 0 }?.let {
                Text(
                    strings.automationDuration(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun AboutPage(
    state: UiState,
    strings: AppStrings,
    onCheckForUpdates: () -> Unit,
) {
    val versionName = rememberClientVersion()
    val commit = state.gatewaySettings?.version?.commit?.take(7)?.takeIf { it.isNotBlank() }
    val gatewayVersion = listOf(
        state.gatewaySettings?.version?.current.orEmpty(),
        commit?.let { "($it)" },
    )
        .filterNotNull()
        .filter { it.isNotBlank() }
        .joinToString(" ")

    // Opening the page checks once; the section's own button re-checks after that.
    LaunchedEffect(Unit) {
        if (state.updateStatus is UpdateStatus.Idle) onCheckForUpdates()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSection(strings.version) {
                InfoRow(strings.client, versionName)
                InfoRow(strings.gateway, gatewayVersion)
                InfoRow(strings.protocol, "nanobot WebSocket", showDivider = false)
            }
        }
        item {
            UpdateSection(
                status = state.updateStatus,
                currentVersion = versionName,
                strings = strings,
                onCheck = onCheckForUpdates,
            )
        }
        item {
            SettingsSection(strings.storage) {
                InfoRow(strings.settings, strings.storedOnDeviceOnly, showDivider = false)
            }
        }
    }
}

@Composable
private fun UpdateSection(
    status: UpdateStatus,
    currentVersion: String,
    strings: AppStrings,
    onCheck: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    SettingsSection(strings.updates) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            when (status) {
                UpdateStatus.Idle -> Text(
                    strings.versionLabel(currentVersion),
                    style = MaterialTheme.typography.bodyMedium,
                )

                UpdateStatus.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        strings.checkingForUpdates,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                UpdateStatus.NoRelease -> {
                    Text(strings.noPublishedRelease, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onCheck) { Text(strings.checkForUpdates) }
                }

                is UpdateStatus.UpToDate -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        strings.upToDate,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCheck) { Text(strings.checkForUpdates) }
                }

                is UpdateStatus.Available -> {
                    Text(
                        strings.updateAvailable(status.version),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        strings.versionLabel(currentVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Button(
                        onClick = { runCatching { uriHandler.openUri(status.downloadUrl) } },
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Text(strings.downloadUpdate)
                    }
                }

                is UpdateStatus.Failed -> {
                    Text(
                        strings.couldNotCheckUpdates,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (status.message.isNotBlank()) {
                        Text(
                            status.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    TextButton(onClick = onCheck) { Text(strings.checkForUpdates) }
                }
            }
        }
    }
}

@Composable
private fun CapabilityList(
    loading: Boolean,
    empty: Boolean,
    emptyText: String,
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (empty && !loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PagePadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun CapabilityCard(
    title: String,
    subtitle: String,
    badge: String?,
    badgeColor: Color,
    trailing: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String? = null,
    showDivider: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, showDivider: Boolean = true) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(88.dp),
            )
            Text(
                value.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun rememberClientVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
}

private fun automationScheduleText(job: AutomationJob, strings: AppStrings): String {
    val schedule = job.schedule
    return when (schedule.kind) {
        "cron" -> schedule.expr?.takeIf { it.isNotBlank() }
            ?.let { strings.automationCronSchedule(it) }
            ?: strings.scheduled
        "every" -> schedule.everyMs?.let { strings.automationEvery(it) } ?: strings.periodic
        "at" -> schedule.atMs?.let { strings.automationAt(formatAutomationTime(it)) }
            ?: strings.once
        "local" -> strings.localTrigger
        else -> schedule.kind.ifBlank { strings.scheduled }
    }
}

private fun automationNextRun(job: AutomationJob, strings: AppStrings): String {
    if (!job.enabled) return strings.paused
    if (job.state.pending) return strings.automationRunning
    if (job.isLocalTrigger) return strings.automationWaitingForTrigger
    if (job.deleteAfterRun && job.state.nextRunAtMs == null && job.state.lastStatus == "ok") {
        return strings.automationCompleted
    }
    val next = job.state.nextRunAtMs ?: return strings.automationNoNextRun
    return listOf(
        strings.automationRelative(next - System.currentTimeMillis()),
        formatAutomationTime(next),
    ).joinToString(" · ")
}

/** Mirrors the WebUI's `automationStatusKey`, reduced to the labels this page shows. */
private fun automationStatusLabel(job: AutomationJob, strings: AppStrings): String? = when {
    job.state.pending -> strings.automationRunning
    !job.enabled -> strings.paused
    job.state.lastStatus == "error" -> strings.automationFailed
    job.protected -> strings.automationSystem
    job.deleteAfterRun && job.state.nextRunAtMs == null && job.state.lastStatus == "ok" ->
        strings.automationCompleted
    else -> null
}

@Composable
private fun automationStatusColor(
    job: AutomationJob,
    label: String,
    strings: AppStrings,
): Color = when {
    job.state.pending -> MaterialTheme.colorScheme.primary
    label == strings.automationFailed -> MaterialTheme.colorScheme.error
    label == strings.automationCompleted -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun automationRunStatus(status: String?, strings: AppStrings): String? = when (status) {
    null, "" -> null
    "ok" -> strings.automationCompleted
    "error" -> strings.automationFailed
    "skipped" -> strings.automationSkipped
    else -> status
}

private fun formatAutomationTime(ms: Long): String {
    val locale = if (AppLocale.language == AppLanguage.SimplifiedChinese) {
        Locale.SIMPLIFIED_CHINESE
    } else {
        Locale.ENGLISH
    }
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(ms))
}
