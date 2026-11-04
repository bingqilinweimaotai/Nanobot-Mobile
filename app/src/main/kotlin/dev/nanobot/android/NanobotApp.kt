package dev.nanobot.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import dev.nanobot.android.host.HostKind
import dev.nanobot.android.host.HostProfile
import dev.nanobot.android.tunnel.TunnelState
import dev.nanobot.gateway.GatewayState
import dev.nanobot.protocol.GatewayModelPreset
import dev.nanobot.protocol.SessionSummary
import dev.nanobot.protocol.WorkspaceScope
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun NanobotApp(viewModel: ChatViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    if (!state.configured) {
        SetupScreen(
            initialEndpoint = state.endpoint,
            pairingError = state.pairingError,
            onConnect = viewModel::connect,
            onPairingLink = viewModel::addHostFromLink,
            onClearPairingError = viewModel::clearPairingError,
        )
    } else {
        MainScreen(state = state, viewModel = viewModel)
    }
}

@Composable
private fun SetupScreen(
    initialEndpoint: String,
    pairingError: String?,
    onConnect: (String, String) -> Unit,
    onPairingLink: (String) -> Boolean,
    onClearPairingError: () -> Unit,
) {
    var endpoint by rememberSaveable {
        mutableStateOf(initialEndpoint.ifBlank { "http://10.0.2.2:8765" })
    }
    var secret by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val launchScanner = rememberQrScanner { scanned ->
        link = scanned
        onPairingLink(scanned)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Nanobot-Mobile", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            strings.setupIntro,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = link,
            onValueChange = {
                link = it
                onClearPairingError()
            },
            label = { Text(strings.pairingLink) },
            minLines = 1,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let {
                        link = it
                        onClearPairingError()
                    }
                },
            ) {
                Text(strings.paste)
            }
            TextButton(onClick = launchScanner) { Text(strings.scanQr) }
        }
        if (pairingError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                pairingError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onPairingLink(link) },
            enabled = link.isNotBlank(),
        ) {
            Text(strings.pair)
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text(strings.orConnectDirectly, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            strings.runGatewayHint,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text(strings.gatewayAddress) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it },
            label = { Text(strings.tokenIssueSecret) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                onClearPairingError()
                onConnect(endpoint, secret)
            },
            enabled = endpoint.isNotBlank(),
        ) {
            Text(strings.connect)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            strings.cleartextHint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Returns a callback that requests camera access (if needed) and scans one QR code. */
@Composable
private fun rememberQrScanner(onScanned: (String) -> Unit): () -> Unit {
    val scan = rememberLauncherForActivityResult(QrScanContract()) { scanned ->
        scanned?.let(onScanned)
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) scan.launch(Unit)
    }
    val context = LocalContext.current
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            scan.launch(Unit)
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(state: UiState, viewModel: ChatViewModel) {
    val strings = LocalStrings.current
    val drawerState = rememberDrawerState(
        if (DebugLaunch.openDrawer) DrawerValue.Open else DrawerValue.Closed,
    )
    LaunchedEffect(Unit) { DebugLaunch.openDrawer = false }
    val focusManager = LocalFocusManager.current
    // Opening the sidebar should dismiss the composer's caret and keyboard.
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open) {
            focusManager.clearFocus()
        }
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val chatListState = rememberLazyListState()
    var showHistory by remember { mutableStateOf(false) }
    var showHosts by remember { mutableStateOf(false) }
    var showAddHost by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showWorkspacePicker by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<List<SessionSummary>>(emptyList()) }
    var deleteAutomations by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(showWorkspacePicker) {
        if (showWorkspacePicker) viewModel.refreshWorkspaces()
    }

    // The drawer search reads transcripts from the local cache, so debounce keystrokes.
    LaunchedEffect(searchOpen, searchQuery) {
        if (!searchOpen || searchQuery.isBlank()) {
            viewModel.clearSearchResults()
            return@LaunchedEffect
        }
        delay(220)
        viewModel.searchConversations(searchQuery)
    }

    // A pairing link that failed to parse (for example after a scan) opens the add-host sheet with
    // the reason attached to the link field.
    LaunchedEffect(state.pairingError) {
        if (state.pairingError != null && state.configured) {
            showHosts = false
            showAddHost = true
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(error)
        viewModel.clearError()
    }

    val session = state.sessions.firstOrNull { it.chatId == state.chatId }
    val title = session?.let { sessionDisplayTitle(it, state.titleOverrides) } ?: strings.newChat

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxSize()) {
                    SidebarQuickActions(
                        searchOpen = searchOpen,
                        onAddWorkspace = { showWorkspacePicker = true },
                        onToggleSearch = {
                            searchOpen = !searchOpen
                            if (!searchOpen) searchQuery = ""
                        },
                        archivedCount = state.archivedKeys.size,
                        showArchived = state.showArchived,
                        onToggleArchived = viewModel::toggleShowArchived,
                    )
                    if (searchOpen) {
                        SidebarSearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                        )
                    }
                    if (searchOpen && searchQuery.isNotBlank()) {
                        ConversationSearchResults(
                            results = state.searchResults,
                            searching = state.searching,
                            onSelect = { hit ->
                                focusManager.clearFocus()
                                searchOpen = false
                                searchQuery = ""
                                viewModel.openSearchHit(hit)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        SessionList(
                            state = state,
                            onSelect = { selected ->
                                viewModel.selectSession(selected)
                                scope.launch { drawerState.close() }
                            },
                            onToggleGroup = viewModel::toggleGroup,
                            onTogglePin = viewModel::togglePin,
                            onSetArchived = viewModel::setArchived,
                            onRename = viewModel::renameSession,
                            onRequestDelete = { sessions -> pendingDelete = sessions },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HostCapsule(
                        state = state,
                        onOpen = { showHosts = true },
                        onSettings = { showSettings = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            ConnectionLine(state.connection, state.hostName)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = strings.sessions)
                        }
                    },
                    actions = {
                        TextButton(onClick = viewModel::newChat) { Text(strings.new) }
                        IconButton(onClick = { showHistory = true }) {
                            Icon(
                                painterResource(R.drawable.ic_history),
                                contentDescription = strings.myMessages,
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            ChatPane(
                state = state,
                viewModel = viewModel,
                listState = chatListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            )
        }
    }

    if (pendingDelete.isNotEmpty()) {
        val blocked = deleteAutomations.isNotEmpty()
        val dismiss = {
            pendingDelete = emptyList()
            deleteAutomations = emptyList()
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = {
                Text(
                    if (pendingDelete.size == 1) {
                        strings.deleteChatTitle
                    } else {
                        strings.deleteChatsTitle(pendingDelete.size)
                    },
                )
            },
            text = {
                Column {
                    Text(
                        if (blocked) strings.deleteAutomationsNote else strings.deleteDescription,
                    )
                    deleteAutomations.take(4).forEach { name ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• $name",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val keys = pendingDelete.map { it.key }
                        val deleteWithAutomations = blocked
                        scope.launch {
                            when (val result = viewModel.deleteSessions(keys, deleteWithAutomations)) {
                                is DeleteSessionsResult.Deleted -> dismiss()
                                is DeleteSessionsResult.Failed -> dismiss()
                                is DeleteSessionsResult.Blocked -> {
                                    pendingDelete = pendingDelete.filter {
                                        it.key in result.remainingKeys
                                    }
                                    deleteAutomations = result.automations.map {
                                        it.name.ifBlank { it.id }
                                    }
                                }
                            }
                        }
                    },
                ) {
                    Text(
                        if (blocked) strings.deleteWithAutomations else strings.delete,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss) {
                    Text(strings.cancel)
                }
            },
        )
    }

    if (showWorkspacePicker) {
        val workspaces = state.workspaces
        AddWorkspaceSheet(
            hostName = workspaces?.host?.name ?: state.hostName,
            defaultPath = state.defaultWorkspaceScope?.projectPath
                ?: workspaces?.defaultScope?.projectPath.orEmpty(),
            canBrowse = workspaces?.controls?.canBrowseDirectories != false,
            favoriteProjects = workspaces?.favoriteProjects.orEmpty(),
            recentProjects = workspaces?.recentProjects.orEmpty(),
            browse = viewModel::browseWorkspace,
            onSelect = { project ->
                showWorkspacePicker = false
                viewModel.addWorkspace(project.path, project.name)
                scope.launch { drawerState.close() }
            },
            onDismiss = { showWorkspacePicker = false },
        )
    }

    if (showHistory) {
        MessageHistorySheet(
            messages = state.messages,
            onDismiss = { showHistory = false },
            onSelect = { message ->
                showHistory = false
                // Reasoning runs fold into one row, so a message index is not a list index.
                val itemIndex = chatListIndexForMessageId(state.messages, message.id)
                if (itemIndex >= 0) {
                    scope.launch { chatListState.animateScrollToItem(itemIndex) }
                }
            },
        )
    }

    if (showSettings) {
        SettingsScreen(
            state = state,
            onDismiss = { showSettings = false },
            onThemeChange = viewModel::setThemeMode,
            onAccentChange = viewModel::setAccentTheme,
            onCustomAccentHueChange = viewModel::setCustomAccentHue,
            onLanguageChange = viewModel::setLanguage,
            onFontSizeChange = viewModel::setFontSize,
            onTurnNotificationsChange = viewModel::setTurnNotifications,
            onNotificationSoundChange = viewModel::setNotificationSound,
            onRefreshGatewaySettings = viewModel::refreshGatewaySettings,
            onReconnect = viewModel::reconnect,
            onManageHosts = {
                showSettings = false
                showHosts = true
            },
            onAddHost = {
                showSettings = false
                showAddHost = true
            },
            onRefreshCapabilities = viewModel::refreshCapabilities,
            onSetSkillEnabled = viewModel::setSkillEnabled,
            gatewayActions = GatewaySettingsActions(
                updateAgent = viewModel::updateAgentSettings,
                saveModelConfiguration = viewModel::saveModelConfiguration,
                createModelConfiguration = viewModel::createModelConfiguration,
                renameModelConfiguration = viewModel::renameModelConfiguration,
                deleteModelConfiguration = viewModel::deleteModelConfiguration,
                setActiveModelPreset = viewModel::setActiveModelPreset,
                setModelPresetEnabled = viewModel::setModelPresetEnabled,
                setUnifiedSession = viewModel::setUnifiedSession,
            ),
            onLoadSkillDetail = viewModel::loadSkillDetail,
            onClearSkillDetail = viewModel::clearSkillDetail,
            onDeleteSkill = viewModel::deleteSkill,
            onMcpAction = viewModel::runMcpAction,
            onSetMcpFieldValue = viewModel::setMcpFieldValue,
            onSaveMcpServer = viewModel::saveMcpServer,
            onImportMcpConfig = viewModel::importMcpConfig,
            onUpdateMcpTools = viewModel::updateMcpTools,
            onStartMcpOAuth = { name, reset -> viewModel.startMcpOAuth(name, reset) },
            onCompleteMcpOAuth = viewModel::completeMcpOAuth,
            onCancelMcpOAuth = viewModel::cancelMcpOAuth,
            onSetAutomationEnabled = viewModel::setAutomationEnabled,
            onRunAutomation = viewModel::runAutomation,
            onDeleteAutomation = viewModel::deleteAutomation,
            onUpdateAutomation = viewModel::updateAutomation,
            onLoadAutomationChats = viewModel::loadAutomationChats,
            onChangeAutomationChat = viewModel::changeAutomationChat,
            onClearAutomationAction = viewModel::clearAutomationAction,
            onClearAutomationChats = viewModel::clearAutomationChats,
            onClearMessage = viewModel::clearSettingsMessage,
            onCheckForUpdates = viewModel::checkForUpdates,
        )
    }

    if (showHosts) {
        HostsDialog(
            state = state,
            onSelect = { id ->
                val connected = viewModel.selectHost(id)
                if (connected) {
                    showHosts = false
                    scope.launch { drawerState.close() }
                }
                connected
            },
            onRemove = viewModel::removeHost,
            onAdd = {
                showHosts = false
                showAddHost = true
            },
            onDismiss = { showHosts = false },
        )
    }

    if (showAddHost) {
        AddHostDialog(
            state = state,
            onAddFromLink = viewModel::addHostFromLink,
            onAddDirect = viewModel::addDirectHost,
            onClearError = viewModel::clearPairingError,
            onDismiss = { showAddHost = false },
        )
    }
}

@Composable
private fun ConnectionLine(state: GatewayState, hostName: String = "") {
    val strings = LocalStrings.current
    val (color, label) = when (state) {
        GatewayState.Disconnected -> Color(0xFF9AA4B2) to strings.offline
        GatewayState.Connecting -> Color(0xFFF2B84B) to strings.connecting
        is GatewayState.Ready -> Color(0xFF3FB950) to strings.connected
        is GatewayState.Reconnecting -> Color(0xFFF2B84B) to strings.reconnecting(state.attempt)
        is GatewayState.Failed -> Color(0xFFE5534B) to state.message
    }
    val fullLabel = if (hostName.isBlank()) label else "$hostName · $label"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(color, CircleShape),
        )
        Text(
            fullLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val SESSION_SPINNER_PERIOD_MILLIS = 900

/** Marks a conversation whose answer arrived while the user was reading another one. */
private val UNREAD_DOT_COLOR = Color(0xFF4CAF50)

/** The drawer actions that sit above every workspace group. */
@Composable
internal fun SidebarQuickActions(
    searchOpen: Boolean,
    onAddWorkspace: () -> Unit,
    onToggleSearch: () -> Unit,
    archivedCount: Int = 0,
    showArchived: Boolean = false,
    onToggleArchived: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        SidebarActionRow(
            icon = Icons.Default.Add,
            label = strings.addWorkspace,
            selected = false,
            onClick = onAddWorkspace,
        )
        SidebarActionRow(
            icon = Icons.Default.Search,
            label = strings.search,
            selected = searchOpen,
            onClick = onToggleSearch,
        )
        if (archivedCount > 0) {
            SidebarActionRow(
                painter = painterResource(R.drawable.ic_archive),
                label = if (showArchived) strings.hideArchived else strings.showArchived,
                selected = showArchived,
                onClick = onToggleArchived,
            )
        }
    }
}

@Composable
private fun SidebarActionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
) {
    Surface(
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (painter != null) {
                Icon(
                    painter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            } else if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
internal fun SidebarSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val strings = LocalStrings.current
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .focusRequester(focusRequester),
        placeholder = { Text(strings.searchConversations) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = strings.clearSearch)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
    Spacer(Modifier.height(4.dp))
}

/** Cross-conversation results: one row per conversation, showing the matching message when known. */
@Composable
internal fun ConversationSearchResults(
    results: List<ConversationSearchHit>,
    searching: Boolean,
    onSelect: (ConversationSearchHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (searching) {
            item(key = "searching") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        if (results.isEmpty() && !searching) {
            item(key = "empty") {
                Text(
                    strings.noMatchingConversations,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        items(results, key = { it.sessionKey }) { hit ->
            Surface(
                onClick = { onSelect(hit) },
                color = Color.Transparent,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        hit.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (hit.preview.isNotBlank()) {
                        Row {
                            when (hit.role) {
                                MessageRole.User -> Text(
                                    strings.mePrefix,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )

                                MessageRole.Assistant -> Text(
                                    strings.assistantPrefix,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )

                                else -> Unit
                            }
                            Text(
                                hit.preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionList(
    state: UiState,
    onSelect: (SessionSummary) -> Unit,
    onToggleGroup: (String) -> Unit,
    onTogglePin: (String) -> Unit = {},
    onSetArchived: (List<String>, Boolean) -> Unit = { _, _ -> },
    onRename: (String, String) -> Unit = { _, _ -> },
    onRequestDelete: (List<SessionSummary>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val topicGroupLabel = strings.topics
    val labels = SidebarGroupLabels(
        pinned = strings.pinned,
        today = strings.today,
        yesterday = strings.yesterday,
        earlier = strings.earlier,
        archived = strings.archived,
    )
    val groups = remember(
        state.sessions,
        state.defaultWorkspaceScope,
        state.projectNameOverrides,
        state.pinnedKeys,
        state.archivedKeys,
        state.showArchived,
        labels,
        topicGroupLabel,
    ) {
        groupSessionsForSidebar(
            sessions = state.sessions,
            defaultWorkspacePath = state.defaultWorkspaceScope?.projectPath,
            projectNameOverrides = state.projectNameOverrides,
            topicGroupLabel = topicGroupLabel,
            pinnedKeys = state.pinnedKeys,
            archivedKeys = state.archivedKeys,
            showArchived = state.showArchived,
            labels = labels,
        )
    }
    val sessionCount = groups.sumOf { it.sessions.size }
    val anyRunning = groups.any { group ->
        group.sessions.any { session ->
            session.runStartedAt != null ||
                session.chatId in state.runningChatIds ||
                (session.chatId == state.chatId && state.streaming)
        }
    }
    val spinnerAngle: State<Float>? = if (anyRunning) {
        rememberInfiniteTransition(label = "session-spinner").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(SESSION_SPINNER_PERIOD_MILLIS, easing = LinearEasing),
            ),
            label = "session-spinner-angle",
        )
    } else {
        null
    }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var renameTarget by remember { mutableStateOf<SessionSummary?>(null) }
    var archiveRequest by remember { mutableStateOf<Pair<List<String>, Boolean>?>(null) }

    // A refresh can remove sessions this selection still names; drop those keys with it.
    LaunchedEffect(state.sessions) {
        val valid = state.sessions.map { it.key }.toSet()
        selectedKeys = selectedKeys.intersect(valid)
    }

    val beginSelection = { session: SessionSummary ->
        selectionMode = true
        selectedKeys = setOf(session.key)
    }
    val toggleSelection = { key: String ->
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
    }
    fun closeSelection() {
        selectionMode = false
        selectedKeys = emptySet()
    }
    val selectedSessions = state.sessions.filter { it.key in selectedKeys }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (selectionMode) 88.dp else 16.dp),
        ) {
            if (state.loadingSessions && sessionCount == 0) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }

            groups.forEachIndexed { groupIndex, group ->
                val collapsed = group.kind == SessionGroupKind.Project &&
                    group.id in state.collapsedGroups
                item(key = "group-${group.id}") {
                    if (group.kind == SessionGroupKind.Project) {
                        ProjectGroupHeader(
                            group = group,
                            collapsed = collapsed,
                            first = groupIndex == 0,
                            onToggle = { onToggleGroup(group.id) },
                        )
                    } else {
                        TextGroupHeader(group = group, first = groupIndex == 0)
                    }
                }

                if (!collapsed) {
                    items(group.sessions, key = { it.key }) { session ->
                        SessionRow(
                            session = session,
                            title = sessionDisplayTitle(session, state.titleOverrides),
                            preview = visibleSessionPreview(session.preview),
                            active = session.chatId == state.chatId,
                            running = session.runStartedAt != null ||
                                session.chatId in state.runningChatIds ||
                                (session.chatId == state.chatId && state.streaming),
                            unread = session.chatId in state.unreadChatIds &&
                                session.chatId != state.chatId,
                            pinned = session.key in state.pinnedKeys,
                            archived = session.key in state.archivedKeys,
                            selectionMode = selectionMode,
                            selectedForDelete = session.key in selectedKeys,
                            spinnerAngle = spinnerAngle,
                            onSelect = { onSelect(session) },
                            onLongPress = { beginSelection(session) },
                            onToggleSelection = { toggleSelection(session.key) },
                            onTogglePin = { onTogglePin(session.key) },
                            onToggleArchive = {
                                onSetArchived(listOf(session.key), session.key !in state.archivedKeys)
                            },
                            onStartSelection = { beginSelection(session) },
                            onRename = { renameTarget = session },
                            onRequestDelete = { onRequestDelete(listOf(session)) },
                        )
                    }
                }
            }

            if (state.loadingSessions && sessionCount > 0) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            if (sessionCount == 0 && !state.loadingSessions) {
                item {
                    Text(
                        strings.emptyChats,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }

        if (selectionMode) {
            SessionSelectionBar(
                count = selectedKeys.size,
                allArchived = selectedKeys.isNotEmpty() &&
                    selectedKeys.all { it in state.archivedKeys },
                onArchive = {
                    val archive = !(selectedKeys.isNotEmpty() &&
                        selectedKeys.all { it in state.archivedKeys })
                    archiveRequest = selectedKeys.toList() to archive
                },
                onDelete = {
                    onRequestDelete(selectedSessions)
                    closeSelection()
                },
                onCancel = ::closeSelection,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }

    renameTarget?.let { target ->
        var title by remember(target.key) {
            mutableStateOf(sessionDisplayTitle(target, state.titleOverrides))
        }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(strings.renameChatTitle) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    placeholder = { Text(strings.renameChatPlaceholder) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(target.key, title)
                        renameTarget = null
                    },
                ) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(strings.cancel)
                }
            },
        )
    }

    archiveRequest?.let { (keys, archive) ->
        val many = keys.size > 1
        AlertDialog(
            onDismissRequest = { archiveRequest = null },
            title = {
                Text(
                    when {
                        archive && many -> strings.archiveChatsTitle(keys.size)
                        archive -> strings.archiveChatTitle
                        many -> strings.unarchiveChatsTitle(keys.size)
                        else -> strings.unarchiveChatTitle
                    },
                )
            },
            text = {
                Text(
                    if (archive) strings.archiveDescription else strings.unarchiveDescription,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetArchived(keys, archive)
                        archiveRequest = null
                        closeSelection()
                    },
                ) { Text(if (archive) strings.archive else strings.unarchive) }
            },
            dismissButton = {
                TextButton(onClick = { archiveRequest = null }) {
                    Text(strings.cancel)
                }
            },
        )
    }
}

@Composable
private fun ProjectGroupHeader(
    group: SessionGroupUi,
    collapsed: Boolean,
    first: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = if (first) 12.dp else 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(workspaceAccentColor(group.id), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    workspaceInitial(group.label),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                group.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = painterResource(
                    if (collapsed) R.drawable.ic_chevron_right else R.drawable.ic_chevron_down,
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TextGroupHeader(group: SessionGroupUi, first: Boolean) {
    Text(
        group.label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = if (first) 14.dp else 18.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionSummary,
    title: String,
    preview: String,
    active: Boolean,
    running: Boolean,
    unread: Boolean,
    pinned: Boolean,
    archived: Boolean,
    selectionMode: Boolean,
    selectedForDelete: Boolean,
    spinnerAngle: State<Float>?,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelection: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onStartSelection: () -> Unit,
    onRename: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val actionsLabel = LocalStrings.current.actionsFor(title)
    val rowColor = when {
        active -> MaterialTheme.colorScheme.primaryContainer
        selectionMode && selectedForDelete -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    Surface(
        color = rowColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelection() else onSelect() },
                    onLongClick = { if (!selectionMode) onLongPress() },
                )
                .padding(start = 12.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Icon(
                    painter = painterResource(
                        if (selectedForDelete) R.drawable.ic_check_box
                        else R.drawable.ic_check_box_outline,
                    ),
                    contentDescription = null,
                    tint = if (selectedForDelete) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
            } else {
                SessionStatusMark(
                    running = running,
                    unread = unread,
                    angle = spinnerAngle,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        // Hug the title so the pin reads as part of it instead of floating next
                        // to the overflow menu; long titles still take the full line and truncate.
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (pinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_pin),
                            contentDescription = LocalStrings.current.pinned,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }
                if (preview.isNotEmpty()) {
                    Text(
                        preview.take(80),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!selectionMode) {
                SessionActionMenu(
                    actionsLabel = actionsLabel,
                    pinned = pinned,
                    archived = archived,
                    onTogglePin = onTogglePin,
                    onToggleArchive = onToggleArchive,
                    onStartSelection = onStartSelection,
                    onRename = onRename,
                    onRequestDelete = onRequestDelete,
                )
            }
        }
    }
}

@Composable
private fun SessionActionMenu(
    actionsLabel: String,
    pinned: Boolean,
    archived: Boolean,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onStartSelection: () -> Unit,
    onRename: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_more_horiz),
                contentDescription = actionsLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (pinned) strings.unpin else strings.pin) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_pin), contentDescription = null) },
                onClick = {
                    open = false
                    onTogglePin()
                },
            )
            DropdownMenuItem(
                text = { Text(strings.rename) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    open = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(if (archived) strings.unarchive else strings.archive)
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_archive), contentDescription = null) },
                onClick = {
                    open = false
                    onToggleArchive()
                },
            )
            DropdownMenuItem(
                text = { Text(strings.select) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_check_box_outline), contentDescription = null) },
                onClick = {
                    open = false
                    onStartSelection()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        strings.delete,
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    open = false
                    onRequestDelete()
                },
            )
        }
    }
}

@Composable
private fun SessionSelectionBar(
    count: Int,
    allArchived: Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                strings.selectedCount(count),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onArchive, enabled = count > 0) {
                Icon(
                    painter = painterResource(R.drawable.ic_archive),
                    contentDescription = if (allArchived) strings.unarchive else strings.archive,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onDelete, enabled = count > 0) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = strings.delete,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Clear, contentDescription = strings.cancel)
            }
        }
    }
}

@Composable
internal fun HostCapsule(
    state: UiState,
    onOpen: () -> Unit,
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val host = state.currentHost
    val strings = LocalStrings.current
    val name = host?.displayName ?: state.hostName.ifBlank { strings.noHost }
    val (dot, status) = hostStatus(state, host, strings)
    Column(modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsButton(onClick = onSettings)
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = onOpen,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(strings.hosts, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Settings,
                contentDescription = LocalStrings.current.settingsContentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

internal fun hostStatus(
    state: UiState,
    host: HostProfile?,
    strings: AppStrings = AppLocale.strings,
): Pair<Color, String> {
    if (host == null) return Color(0xFF9AA4B2) to strings.noHost
    val connection = when (val current = state.connection) {
        GatewayState.Disconnected -> Color(0xFF9AA4B2) to strings.offline
        GatewayState.Connecting -> Color(0xFFF2B84B) to strings.connecting
        is GatewayState.Ready -> Color(0xFF3FB950) to strings.connected
        is GatewayState.Reconnecting -> Color(0xFFF2B84B) to strings.reconnecting(current.attempt)
        is GatewayState.Failed -> Color(0xFFE5534B) to current.message
    }
    if (host.kind != HostKind.Relay) return connection
    return when (val tunnel = state.tunnelState) {
        null, TunnelState.Stopped -> Color(0xFF9AA4B2) to strings.relayStopped
        TunnelState.Connecting -> Color(0xFFF2B84B) to strings.relayConnecting
        TunnelState.Ready -> connection
        is TunnelState.Reconnecting -> Color(0xFFF2B84B) to strings.relayReconnecting(tunnel.attempt)
        is TunnelState.Failed -> Color(0xFFE5534B) to strings.relayFailed(tunnel.reason)
    }
}

@Composable
private fun HostsDialog(
    state: UiState,
    onSelect: (String) -> Boolean,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.hosts) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.hosts.isEmpty()) {
                    Text(
                        strings.noHostsSaved,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.hosts.forEach { host ->
                    val selected = host.id == state.currentHostId
                    val detail = if (selected) hostStatus(state, host, strings).second else host.targetLabel
                    Surface(
                        onClick = { onSelect(host.id) },
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    host.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = strings.currentHostCheck,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            TextButton(onClick = { onRemove(host.id) }) { Text(strings.remove) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text(strings.addHost) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

@Composable
private fun AddHostDialog(
    state: UiState,
    onAddFromLink: (String) -> Boolean,
    onAddDirect: (String, String, String) -> Boolean,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var link by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var endpoint by rememberSaveable { mutableStateOf("http://10.0.2.2:8765") }
    var secret by rememberSaveable { mutableStateOf("") }
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val launchScanner = rememberQrScanner { scanned ->
        link = scanned
        if (onAddFromLink(scanned)) onDismiss()
    }
    val dismiss = {
        onClearError()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(strings.addHost) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TabRow(selectedTabIndex = mode) {
                    Tab(
                        selected = mode == 0,
                        onClick = {
                            mode = 0
                            onClearError()
                        },
                        text = { Text(strings.pairingLink) },
                    )
                    Tab(
                        selected = mode == 1,
                        onClick = {
                            mode = 1
                            onClearError()
                        },
                        text = { Text(strings.manual) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                when (mode) {
                    0 -> {
                        Text(
                            strings.addHostPairIntro,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = link,
                            onValueChange = {
                                link = it
                                onClearError()
                            },
                            label = { Text(strings.pairingLink) },
                            minLines = 1,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let {
                                        link = it
                                        onClearError()
                                    }
                                },
                            ) { Text(strings.paste) }
                            TextButton(onClick = launchScanner) { Text(strings.scanQr) }
                        }
                    }

                    else -> {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(strings.nameOptional) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = {
                                endpoint = it
                                onClearError()
                            },
                            label = { Text(strings.gatewayAddress) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = secret,
                            onValueChange = { secret = it },
                            label = { Text(strings.tokenIssueSecret) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                state.pairingError?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val added = if (mode == 0) {
                        onAddFromLink(link)
                    } else {
                        onAddDirect(name, endpoint, secret)
                    }
                    if (added) onDismiss()
                },
                enabled = if (mode == 0) link.isNotBlank() else endpoint.isNotBlank(),
            ) {
                Text(if (mode == 0) strings.pair else strings.add)
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(strings.cancel) } },
    )
}

@Composable
private fun SessionStatusMark(running: Boolean, unread: Boolean, angle: State<Float>?) {
    val tone = MaterialTheme.colorScheme.onSurfaceVariant
    val strings = LocalStrings.current
    val runningLabel = strings.running
    val unreadLabel = strings.unread
    Box(
        modifier = Modifier.size(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (running) {
            CircularProgressIndicator(
                progress = { 0.72f },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = angle?.value ?: 0f }
                    .semantics { contentDescription = runningLabel },
                color = tone,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round,
            )
        } else {
            Box(
                Modifier
                    .size(if (unread) 8.dp else 6.dp)
                    .background(
                        if (unread) UNREAD_DOT_COLOR else tone.copy(alpha = 0.4f),
                        CircleShape,
                    )
                    .then(
                        if (unread) {
                            Modifier.semantics { contentDescription = unreadLabel }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

@Composable
private fun ChatPane(
    state: UiState,
    viewModel: ChatViewModel,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    // The software keyboard shrinks this pane but leaves the list's scroll position alone, so the
    // tail slides under the composer and the jump-to-latest button flashes even though the reader
    // never scrolled. Remember whether the reader was at the tail while the keyboard is closed,
    // then let each shrink carry the list with it. Someone reading older messages keeps their
    // place; following their viewport would yank the page under them.
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var pinnedToTail by remember { mutableStateOf(true) }
    LaunchedEffect(imeBottom == 0) {
        if (imeBottom != 0) return@LaunchedEffect
        snapshotFlow { !listState.canScrollForward }.collect { pinnedToTail = it }
    }
    var lastListHeight by remember { mutableStateOf(0) }

    // A finished message should land with its tail in view, not its first line. During a live
    // turn the follow effect below owns scrolling; doing both made every reasoning step animate
    // the whole page to the bottom. A settled history screen still lands at the bottom, even when
    // its last message happens to be reasoning.
    LaunchedEffect(state.messages.size, state.pendingSearchTarget) {
        if (state.pendingSearchTarget != null) return@LaunchedEffect
        val last = state.messages.lastOrNull() ?: return@LaunchedEffect
        if (state.streaming || state.turnActive || (last.reasoning && last.streaming)) {
            return@LaunchedEffect
        }
        listState.animateScrollToBottom()
    }

    // While a turn runs, follow the growing tail unless the reader scrolled up. This includes
    // reasoning: otherwise new thoughts land below the fold and the page looks stuck.
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var followStream by remember { mutableStateOf(true) }
    LaunchedEffect(isDragged) {
        if (isDragged) {
            followStream = false
            pinnedToTail = false
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }.collect { canScrollForward ->
            if (!canScrollForward) followStream = true
        }
    }
    // Another conversation opens at its newest message; it should not inherit the previous
    // thread's "the reader scrolled away" state.
    LaunchedEffect(state.chatId) {
        followStream = true
        pinnedToTail = true
    }
    val lastMessage = state.messages.lastOrNull()
    val turnLive = state.turnActive || state.streaming || lastMessage?.streaming == true
    // The "jump to the tail" affordance appears once the reader has moved away from the newest
    // content. The threshold absorbs a one-pixel nudge and a tail that is momentarily below the
    // fold before the follow scroll lands (during a live turn the follow effect owns scrolling).
    val jumpThresholdPx = with(LocalDensity.current) { JUMP_TO_LATEST_THRESHOLD.toPx() }
    val showJumpToLatest by remember(listState, jumpThresholdPx, turnLive) {
        derivedStateOf {
            if (turnLive && followStream) return@derivedStateOf false
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            // A whole item below the fold is always far enough away.
            if (lastVisible.index < layout.totalItemsCount - 1) return@derivedStateOf true
            val viewportEnd = layout.viewportEndOffset - layout.afterContentPadding
            (lastVisible.offset + lastVisible.size - viewportEnd).toFloat() > jumpThresholdPx
        }
    }
    // The size changes when a new reasoning step starts; the length keeps the tail moving while
    // it streams. Tracking both means a step is followed even before it has any text.
    val tailSignal = state.messages.size to (lastMessage?.text?.length ?: 0)
    val latestTail by rememberUpdatedState(tailSignal)
    LaunchedEffect(turnLive, state.pendingSearchTarget) {
        if (!turnLive || state.pendingSearchTarget != null) return@LaunchedEffect
        // One long-lived collector, so deltas that arrive faster than a frame are
        // conflated instead of cancelling the scroll before it runs.
        snapshotFlow { latestTail }
            .conflate()
            .collect {
                if (!followStream) return@collect
                // Let the appended text be measured before chasing the bottom.
                withFrameNanos { }
                listState.scrollBy(STREAM_FOLLOW_DISTANCE_PX)
            }
    }

    // A drawer search result asks for one bubble; scroll there instead of to the newest message.
    val listItems = remember(state.messages) { chatListItems(state.messages) }
    LaunchedEffect(state.pendingSearchTarget, state.messages, state.loadingThread) {
        val target = state.pendingSearchTarget ?: return@LaunchedEffect
        val messageIndex = findSearchTarget(state.messages, target)
        val itemIndex = if (messageIndex >= 0) {
            chatListIndexForMessage(listItems, messageIndex)
        } else {
            -1
        }
        when {
            itemIndex >= 0 -> {
                listState.animateScrollToItem(itemIndex)
                viewModel.clearSearchScrollTarget()
            }

            !state.loadingThread -> viewModel.clearSearchScrollTarget()
        }
    }

    Column(modifier = modifier) {
        if (state.loadingThread) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.messages.isEmpty() && !state.loadingThread) {
            EmptyState(
                hasSession = state.chatId != null,
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            val previous = lastListHeight
                            lastListHeight = size.height
                            if (previous <= 0) return@onSizeChanged
                            val shrink = previous - size.height
                            if (shrink > 0) {
                                // The keyboard (or a growing composer) ate some height: carry the
                                // tail up by the same amount so it stays above the input.
                                if (pinnedToTail) scope.launch { listState.scrollBy(shrink.toFloat()) }
                            } else if (shrink < 0) {
                                // The viewport grew. A list that was pinned can be left with blank
                                // space past the last item; pull the tail back down onto its edge.
                                scope.launch { listState.closeTailGap() }
                            }
                        },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(listItems, key = { _, item -> item.key }) { index, item ->
                        Box(
                            Modifier.animateItem(
                                fadeInSpec = tween(200),
                                placementSpec = tween(180),
                                fadeOutSpec = tween(120),
                            ),
                        ) {
                            when (item) {
                                is ChatListItem.Message -> MessageBubble(
                                    message = item.message,
                                    forkUserIndex = forkUserIndex(state.messages, item.index),
                                    onFork = viewModel::forkMessage,
                                    onLoadMedia = viewModel::loadMedia,
                                    mediaEpoch = state.mediaEpoch,
                                )

                                is ChatListItem.AssistantTurn -> AssistantTurnBlock(
                                    messages = item.messages,
                                    // Only the newest turn can still be running; older rows are history.
                                    isLive = index == listItems.lastIndex &&
                                        (state.turnActive || state.streaming),
                                    forkUserIndex = item.messages
                                        .indexOfLast { !it.reasoning }
                                        .takeIf { it >= 0 }
                                        ?.let { forkUserIndex(state.messages, item.startIndex + it) },
                                    onFork = viewModel::forkMessage,
                                )
                            }
                        }
                    }
                    // Before the first reasoning or answer frame arrives there is no turn row yet;
                    // keep the cat visible so sending a message never looks like a dead tap.
                    if (state.turnActive && listItems.lastOrNull() !is ChatListItem.AssistantTurn) {
                        item(key = "pending-activity") {
                            WorkingRow(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(180),
                                    placementSpec = tween(160),
                                    fadeOutSpec = tween(120),
                                ),
                            )
                        }
                    }
                }
                // Floats over the tail without taking layout height from the list. Tapping it
                // re-arms the follow effect and lands on the newest text, then keeps following.
                // The BoxScope receiver is inside a ColumnScope, so name the top-level overload
                // explicitly; otherwise the ColumnScope extension is picked and cannot be called.
                androidx.compose.animation.AnimatedVisibility(
                    visible = showJumpToLatest,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(90)),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            followStream = true
                            scope.launch { listState.animateScrollToBottom() }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = strings.jumpToLatest,
                        )
                    }
                }
            }
        }
        Composer(state = state, viewModel = viewModel)
    }
}

/**
 * Scrolls the final item's start into view and then continues to its end, so a
 * long assistant answer opens on its last line instead of its first.
 */
private suspend fun LazyListState.animateScrollToBottom() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    animateScrollToItem(lastIndex)
    withFrameNanos { }
    val lastItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    val viewportEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
    val overshoot = (lastItem.offset + lastItem.size) - viewportEnd
    if (overshoot > 0) animateScrollBy(overshoot.toFloat())
}

/**
 * When the viewport grows, a list that was pinned to the tail can end up scrolled past the
 * content, leaving blank space below the last item. Pull it back onto the viewport edge. A reader
 * who is mid-history has the tail below the fold, so there is no gap and nothing moves.
 */
private suspend fun LazyListState.closeTailGap() {
    val layout = layoutInfo
    val lastItem = layout.visibleItemsInfo.lastOrNull() ?: return
    if (lastItem.index != layout.totalItemsCount - 1) return
    val viewportEnd = layout.viewportEndOffset - layout.afterContentPadding
    val gap = viewportEnd - (lastItem.offset + lastItem.size)
    if (gap > 0) scrollBy(gap.toFloat())
}

private const val STREAM_FOLLOW_DISTANCE_PX = 100_000f

/** How far the reader must move from the tail before the jump-to-latest button appears. */
private val JUMP_TO_LATEST_THRESHOLD = 72.dp

@Composable
private fun EmptyState(hasSession: Boolean, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PixelCat()
        Spacer(Modifier.height(4.dp))
        Text("Nanobot-Mobile", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            if (hasSession) {
                strings.emptyConversation
            } else {
                strings.createChat
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            strings.composerHint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CopyActionButton(text: String, key: String) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    var copied by remember(key) { mutableStateOf(false) }
    LaunchedEffect(copied, key) {
        if (copied) {
            delay(1_000)
            copied = false
        }
    }
    IconButton(
        onClick = {
            clipboard.setText(AnnotatedString(text))
            copied = true
        },
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            painter = painterResource(
                if (copied) R.drawable.ic_check else R.drawable.ic_copy,
            ),
            contentDescription = if (copied) strings.copied else strings.copy,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
internal fun MessageBubble(
    message: ChatMessage,
    forkUserIndex: Int?,
    onFork: (Int) -> Unit,
    onLoadMedia: (String, Int) -> Unit = { _, _ -> },
    mediaEpoch: Int = 0,
) {
    var previewImage by remember { mutableStateOf<Uri?>(null) }
    val strings = LocalStrings.current
    when {
        message.role == MessageRole.System -> {
            Text(
                message.text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }

        message.role == MessageRole.User -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.widthIn(max = 520.dp),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (message.attachments.isNotEmpty()) {
                            MessageAttachmentStrip(
                                attachments = message.attachments,
                                onPreview = { previewImage = it },
                                onLoad = { index -> onLoadMedia(message.id, index) },
                                mediaEpoch = mediaEpoch,
                            )
                            if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                        }
                        if (message.text.isNotBlank()) {
                            Text(message.text, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (message.attachments.isEmpty() && message.mediaUrls.isNotEmpty()) {
                            Text(
                                strings.attachmentCount(message.mediaUrls.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (message.pending) {
                            Text(
                                strings.sending,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (message.failed) {
                            Text(
                                strings.notConfirmed,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        else -> AssistantTurnBlock(
            messages = listOf(message),
            isLive = message.streaming,
            forkUserIndex = forkUserIndex,
            onFork = onFork,
        )
    }
    previewImage?.let { uri ->
        ImagePreviewDialog(uri = uri, onDismiss = { previewImage = null })
    }
}

/**
 * One assistant turn: a single activity strip on top and one continuous answer body below.
 *
 * A model inside one turn can alternate text, reasoning, and tool calls many times. Rendering
 * each segment as its own bubble stacked action rows and "Thought" headers between paragraphs.
 * Here all reasoning folds into the strip and all text joins into one body, so the turn reads as
 * one answer. The strip stays above the body: when the answer is still streaming the strip
 * already shows its settled check instead of sitting below the growing text.
 *
 * Actions belong to the turn, not to a segment: they appear only after the turn stops running,
 * and copy takes the whole body rather than whichever slice happened to finish last.
 */
@Composable
internal fun AssistantTurnBlock(
    messages: List<ChatMessage>,
    isLive: Boolean,
    forkUserIndex: Int?,
    onFork: (Int) -> Unit,
) {
    val reasoning = remember(messages) { messages.filter { it.reasoning } }
    val body = remember(messages) { messages.filter { !it.reasoning } }
    val bodyText = remember(body) {
        body.map { it.text }.filter { it.isNotBlank() }.joinToString("\n\n")
    }
    val mediaCount = remember(body) { body.sumOf { it.mediaUrls.size } }
    val bodyStopped = body.any { it.stopped }
    val canCopy = bodyText.isNotBlank() && !isLive
    val forkIndex = forkUserIndex.takeIf { !isLive }
    val strings = LocalStrings.current

    Column(Modifier.fillMaxWidth()) {
        if (reasoning.isNotEmpty()) {
            ReasoningBlock(reasoning)
            if (bodyText.isNotBlank() || mediaCount > 0) Spacer(Modifier.height(6.dp))
        }
        if (bodyText.isNotBlank()) {
            SelectionContainer {
                MarkdownText(bodyText)
            }
        }
        if (mediaCount > 0) {
            Text(
                strings.attachmentCount(mediaCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (bodyStopped) {
            Text(
                strings.stopped,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (canCopy || forkIndex != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canCopy) {
                    CopyActionButton(text = bodyText, key = messages.first().id)
                }
                if (forkIndex != null) {
                    IconButton(
                        onClick = { onFork(forkIndex) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_fork),
                            contentDescription = strings.forkFromHere,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

internal fun forkUserIndex(messages: List<ChatMessage>, index: Int): Int? {
    val message = messages.getOrNull(index) ?: return null
    if (message.role != MessageRole.Assistant || message.reasoning ||
        message.streaming || message.stopped
    ) {
        return null
    }
    if (message.text.isBlank()) return null
    for (nextIndex in index + 1 until messages.size) {
        val next = messages[nextIndex]
        if (next.role == MessageRole.User) break
        if (next.role == MessageRole.Assistant && !next.reasoning) return null
    }
    return messages.take(index + 1).count { it.role == MessageRole.User }.takeIf { it > 0 }
}

private val LOW_INFORMATION_TITLES = setOf(
    "hi", "hello", "hey", "hello nano", "hello nanobot", "hi nano", "hi nanobot",
    "你好", "您好", "嗨", "哈喽", "哈啰", "在吗",
)

internal fun sessionDisplayTitle(
    session: SessionSummary,
    titleOverrides: Map<String, String>,
): String {
    titleOverrides[session.key]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    session.title.trim().takeIf { it.isNotEmpty() }?.let { return it }

    val preview = visibleSessionPreview(session.preview)
    val oneLine = preview.replace(Regex("\\s+"), " ").trim()
    if (oneLine.isEmpty()) return AppLocale.strings.newChat
    val normalized = oneLine.lowercase().trimEnd('.', '!', '?', '。', '！', '？', '~', '～').trim()
    if (normalized.startsWith("/") || normalized in LOW_INFORMATION_TITLES) {
        return AppLocale.strings.newChat
    }
    return if (oneLine.length > 60) oneLine.take(57) + "…" else oneLine
}

internal enum class SessionGroupKind { Project, Date, Pinned, Archived }

internal data class SessionGroupUi(
    val id: String,
    val label: String,
    val sessions: List<SessionSummary>,
    val kind: SessionGroupKind = SessionGroupKind.Project,
)

/** Localized headings the WebUI uses for its date, pinned, and archived buckets. */
internal data class SidebarGroupLabels(
    val pinned: String = "Pinned",
    val today: String = "Today",
    val yesterday: String = "Yesterday",
    val earlier: String = "Earlier",
    val archived: String = "Archived",
)

internal fun groupSessionsForSidebar(
    sessions: List<SessionSummary>,
    defaultWorkspacePath: String?,
    projectNameOverrides: Map<String, String>,
    topicGroupLabel: String,
    pinnedKeys: Set<String> = emptySet(),
    archivedKeys: Set<String> = emptySet(),
    showArchived: Boolean = false,
    labels: SidebarGroupLabels = SidebarGroupLabels(),
    nowMillis: Long = System.currentTimeMillis(),
): List<SessionGroupUi> {
    // The WebUI switches to project groups as soon as any conversation has a project path.
    val projectMode = sessions.any { !it.workspaceScope?.projectPath.isNullOrBlank() }
    return if (projectMode) {
        groupSessionsByProject(
            sessions, defaultWorkspacePath, projectNameOverrides, topicGroupLabel,
            pinnedKeys, archivedKeys, showArchived,
        )
    } else {
        groupSessionsByDate(
            sessions, pinnedKeys, archivedKeys, showArchived, labels, nowMillis,
        )
    }
}

private fun groupSessionsByProject(
    sessions: List<SessionSummary>,
    defaultWorkspacePath: String?,
    projectNameOverrides: Map<String, String>,
    topicGroupLabel: String,
    pinnedKeys: Set<String>,
    archivedKeys: Set<String>,
    showArchived: Boolean,
): List<SessionGroupUi> {
    val defaultKey = defaultWorkspacePath
        ?.takeIf { it.isNotBlank() }
        ?.let(::normalizeWorkspacePath)
    val buckets = LinkedHashMap<String, Pair<String, MutableList<SessionSummary>>>()

    for (session in sessions) {
        if (session.key in archivedKeys && !showArchived) continue
        val path = session.workspaceScope?.projectPath.orEmpty()
        val normalized = normalizeWorkspacePath(path)
        val isDefault = path.isBlank() || (defaultKey != null && normalized == defaultKey)
        val key = if (isDefault) "workspace:chats" else normalized
        val label = if (isDefault) {
            topicGroupLabel
        } else {
            sessionProjectLabel(session, projectNameOverrides) ?: AppLocale.strings.chats
        }
        val bucket = buckets.getOrPut(key) { label to mutableListOf() }
        bucket.second.add(session)
    }

    return buckets.map { (key, bucket) ->
        SessionGroupUi(
            id = key,
            label = bucket.first,
            sessions = sortProjectSessions(bucket.second, pinnedKeys, archivedKeys),
        )
    }.sortedWith(
        compareByDescending<SessionGroupUi> { group ->
            group.sessions.maxOfOrNull(::sessionTimeMillis) ?: 0L
        }.thenBy { it.label },
    )
}

private fun groupSessionsByDate(
    sessions: List<SessionSummary>,
    pinnedKeys: Set<String>,
    archivedKeys: Set<String>,
    showArchived: Boolean,
    labels: SidebarGroupLabels,
    nowMillis: Long,
): List<SessionGroupUi> {
    val zone = ZoneId.systemDefault()
    val startOfToday = Instant.ofEpochMilli(nowMillis)
        .atZone(zone)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
    val startOfYesterday = startOfToday - 24 * 60 * 60 * 1000L
    val pinned = mutableListOf<SessionSummary>()
    val today = mutableListOf<SessionSummary>()
    val yesterday = mutableListOf<SessionSummary>()
    val earlier = mutableListOf<SessionSummary>()
    val archived = mutableListOf<SessionSummary>()

    for (session in sessions) {
        if (session.key in archivedKeys) {
            if (showArchived) archived += session
            continue
        }
        if (session.key in pinnedKeys) {
            pinned += session
            continue
        }
        val time = sessionTimeMillis(session)
        when {
            time >= startOfToday -> today += session
            time >= startOfYesterday -> yesterday += session
            else -> earlier += session
        }
    }

    val timeOrder = compareByDescending<SessionSummary>(::sessionTimeMillis).thenBy { it.key }
    return buildList {
        if (pinned.isNotEmpty()) {
            add(SessionGroupUi("pinned", labels.pinned, pinned.sortedWith(timeOrder), SessionGroupKind.Pinned))
        }
        if (today.isNotEmpty()) {
            add(SessionGroupUi("date:today", labels.today, today.sortedWith(timeOrder), SessionGroupKind.Date))
        }
        if (yesterday.isNotEmpty()) {
            add(SessionGroupUi("date:yesterday", labels.yesterday, yesterday.sortedWith(timeOrder), SessionGroupKind.Date))
        }
        if (earlier.isNotEmpty()) {
            add(SessionGroupUi("date:earlier", labels.earlier, earlier.sortedWith(timeOrder), SessionGroupKind.Date))
        }
        if (archived.isNotEmpty()) {
            add(SessionGroupUi("archived", labels.archived, archived.sortedWith(timeOrder), SessionGroupKind.Archived))
        }
    }
}

private fun sortProjectSessions(
    sessions: List<SessionSummary>,
    pinnedKeys: Set<String>,
    archivedKeys: Set<String>,
): List<SessionSummary> = sessions.sortedWith(
    compareByDescending<SessionSummary> { it.key in pinnedKeys }
        .thenBy { it.key in archivedKeys }
        .thenByDescending(::sessionTimeMillis)
        .thenBy { it.key },
)

private fun sessionTimeMillis(session: SessionSummary): Long =
    parseWireMillis(session.updatedAt) ?: parseWireMillis(session.createdAt) ?: 0L

private fun parseWireMillis(value: String?): Long? {
    val text = value?.trim().orEmpty()
    if (text.isEmpty()) return null
    val zone = ZoneId.systemDefault()
    return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()
        ?: runCatching {
            LocalDate.parse(text).atStartOfDay(zone).toInstant().toEpochMilli()
        }.getOrNull()
}

private fun sessionProjectLabel(
    session: SessionSummary,
    projectNameOverrides: Map<String, String>,
): String? {
    val scope = session.workspaceScope ?: return null
    val path = scope.projectPath
    if (path.isBlank()) return null
    val normalized = normalizeWorkspacePath(path)
    return projectNameOverrides[normalized]?.trim()?.takeIf { it.isNotEmpty() }
        ?: scope.projectName?.trim()?.takeIf { it.isNotEmpty() }
        ?: projectNameFromPath(normalized)
}

internal val workspacePalette = listOf(
    0xFFE06C75.toInt(),
    0xFFD19A66.toInt(),
    0xFF98C379.toInt(),
    0xFF56B6C2.toInt(),
    0xFF61AFEF.toInt(),
    0xFFC678DD.toInt(),
    0xFFBE5046.toInt(),
    0xFFABB2BF.toInt(),
)

internal fun workspaceAccentColor(groupId: String): Color =
    Color(workspacePalette[groupId.hashCode().and(Int.MAX_VALUE) % workspacePalette.size])

internal fun workspaceInitial(label: String): String =
    label.trim().ifBlank { "?" }.take(1).uppercase()

internal fun visibleSessionPreview(raw: String): String {
    val text = raw.trim().replace(Regex("\\s+"), " ")
    if (text.isEmpty() || text.startsWith("/model")) return ""
    if (text.startsWith("## Model - Current") ||
        text.startsWith("Switched model preset to ") ||
        text.startsWith("Could not switch model preset:") ||
        text == "Usage: `/model [preset]`"
    ) {
        return ""
    }
    return text
}

private fun normalizeWorkspacePath(path: String): String =
    path.replace('\\', '/').trimEnd('/')

internal fun projectNameFromPath(path: String): String {
    val trimmed = path.trimEnd('/', '\\')
    val name = trimmed.substringAfterLast('/').substringAfterLast('\\')
    return name.ifBlank { AppLocale.strings.workspace }
}

/** What a suggestion inserts, used to pick its icon and accent color. */
private enum class SuggestionKind { Command, Skill, CliApp, McpPreset }

private data class Suggestion(
    val key: String,
    val title: String,
    val subtitle: String,
    val kind: SuggestionKind,
    /** Muted mono hint showing what picking the row inserts, e.g. `/help`. */
    val trailing: String = "",
    val onPick: () -> Unit,
)

/** Selectable presets in the gateway's preferred order, mirroring the WebUI's model picker. */
private fun modelPresetChoices(state: UiState): List<GatewayModelPreset> {
    val settings = state.gatewaySettings ?: return emptyList()
    val order = settings.modelCallOrder
        .mapIndexed { index, name -> name.trim() to index }
        .toMap()
    return settings.modelPresets
        .filter { it.name.isNotBlank() }
        .sortedBy { order[it.name] ?: Int.MAX_VALUE }
}

/**
 * The WebUI's composer picker lists named presets only; the built-in `default` preset is the
 * implicit fallback and would otherwise show up as a confusing "Default · <base model>" row.
 */
internal fun modelPickerChoices(presets: List<GatewayModelPreset>): List<GatewayModelPreset> =
    presets.filterNot { it.isDefault || it.name == DEFAULT_MODEL_PRESET }

/** The preset in force for this chat: a local switch, the session row, then the default. */
private fun modelPresetName(state: UiState): String {
    val scoped = state.modelPreset
        ?: state.sessions.firstOrNull { it.chatId == state.chatId }?.modelPreset
        ?: state.gatewaySettings?.agent?.modelPreset
    return scoped?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL_PRESET
}

private fun modelPillLabel(
    state: UiState,
    selectedName: String,
    presets: List<GatewayModelPreset>,
): String {
    val preset = presets.firstOrNull { it.name == selectedName }
        ?: presets.firstOrNull { it.active }
    if (preset != null) {
        return if (preset.isDefault || preset.name == DEFAULT_MODEL_PRESET) {
            AppLocale.strings.defaultModelPreset
        } else {
            preset.name
        }
    }
    val model = state.modelName.ifBlank { state.gatewaySettings?.agent?.model.orEmpty() }
    return compactModelName(model) ?: AppLocale.strings.switchModel
}

private fun modelPillInitial(
    selectedName: String,
    presets: List<GatewayModelPreset>,
): String {
    val preset = presets.firstOrNull { it.name == selectedName }
    val provider = preset?.resolvedProvider
        ?: preset?.provider?.takeIf { it != "auto" }
    val source = provider?.takeIf { it.isNotBlank() }
        ?: selectedName.takeIf { it != DEFAULT_MODEL_PRESET && it.isNotBlank() }
        ?: "M"
    return source.take(1).uppercase(Locale.US)
}

private fun compactModelName(model: String?): String? {
    val value = model?.trim().orEmpty()
    if (value.isEmpty()) return null
    return value.substringAfterLast('/').ifBlank { value }
}

private const val DEFAULT_MODEL_PRESET = "default"

@Composable
private fun ModelCapsule(
    label: String,
    initial: String,
    expanded: Boolean,
    enabled: Boolean,
    presets: List<GatewayModelPreset>,
    selectedName: String,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val strings = LocalStrings.current
    val shape = CircleShape
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    var anchorTopPx by remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            anchorTopPx = coordinates.positionInWindow().y.roundToInt()
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .height(30.dp)
                .width(106.dp)
                .clip(shape)
                .background(
                    if (expanded) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                )
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(horizontal = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    initial,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                modifier = Modifier
                    .size(12.dp)
                    .rotate(if (expanded) 180f else 0f),
                tint = tint,
            )
        }
        if (expanded) {
            ModelMenuPopup(
                anchorTopPx = anchorTopPx,
                presets = presets,
                selectedName = selectedName,
                strings = strings,
                onDismiss = onDismiss,
                onSelect = onSelect,
            )
        }
    }
}

/**
 * A menu whose bottom edge sits on the capsule. Material's [DropdownMenu] falls back to aligning
 * the menu 48dp above the window bottom whenever the anchor is near the bottom edge, which is
 * exactly where the composer lives, so it always floats above the capsule.
 */
@Composable
private fun ModelMenuPopup(
    anchorTopPx: Int,
    presets: List<GatewayModelPreset>,
    selectedName: String,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val density = LocalDensity.current
    val marginPx = with(density) { 8.dp.roundToPx() }
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val positionProvider = remember(marginPx, statusBarPx) {
        AboveAnchorMenuPositionProvider(marginPx = marginPx, topMarginPx = marginPx + statusBarPx)
    }
    val maxHeight = if (anchorTopPx > 0) {
        with(density) {
            (anchorTopPx - marginPx - statusBarPx).coerceAtLeast(0).toDp()
        }.coerceAtMost(360.dp)
    } else {
        360.dp
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = MenuDefaults.shape,
            color = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation,
            modifier = Modifier.widthIn(min = 220.dp, max = 300.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                // The built-in `default` preset mirrors `agents.defaults` rather than a named
                // preset. The WebUI's composer picker hides it for the same reason, so keep the
                // two clients' model lists identical.
                val choices = modelPickerChoices(presets)
                if (choices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(strings.noModelPresets) },
                        onClick = {},
                        enabled = false,
                    )
                } else {
                    choices.forEach { preset ->
                        val title = if (preset.isDefault || preset.name == DEFAULT_MODEL_PRESET) {
                            strings.defaultModelPreset
                        } else {
                            preset.name
                        }
                        val detail = compactModelName(preset.model)
                        val selected = preset.name == selectedName
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!detail.isNullOrBlank() && detail != title) {
                                        Text(
                                            detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            },
                            trailingIcon = {
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                            onClick = { onSelect(preset.name) },
                        )
                    }
                }
            }
        }
    }
}

/** Places a menu flush above its anchor, only flipping below when there is no room above. */
private class AboveAnchorMenuPositionProvider(
    private val marginPx: Int,
    private val topMarginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val endAlignedX = anchorBounds.right - popupContentSize.width
        val x = when {
            anchorBounds.left + popupContentSize.width <= windowSize.width - marginPx ->
                anchorBounds.left
            endAlignedX >= marginPx -> endAlignedX
            else -> anchorBounds.left.coerceIn(
                marginPx,
                (windowSize.width - marginPx - popupContentSize.width).coerceAtLeast(marginPx),
            )
        }
        val above = anchorBounds.top - popupContentSize.height
        val below = anchorBounds.bottom
        val y = when {
            above >= topMarginPx -> above
            below + popupContentSize.height <= windowSize.height - marginPx -> below
            else -> topMarginPx
        }
        return IntOffset(x, y)
    }
}

/** The WebUI's compact ring: empty until a turn reports usage, amber near full, red past 90%. */
@Composable
private fun ContextUsageRing(
    tokens: Long?,
    windowTokens: Int,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val percent = if (tokens != null && tokens >= 0 && windowTokens > 0) {
        (tokens.toDouble() / windowTokens * 100.0).coerceIn(0.0, 100.0)
    } else {
        null
    }
    val color = when {
        percent == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        percent >= 90 -> MaterialTheme.colorScheme.error
        percent >= 75 -> Color(0xFFD59A2A)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = if (percent == null) {
        strings.contextUsageUnknown
    } else {
        strings.contextUsage(percent.roundToInt())
    }
    Canvas(
        modifier = modifier
            .size(22.dp)
            .semantics { contentDescription = description },
    ) {
        val stroke = 1.8.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = color.copy(alpha = 0.20f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        val sweep = 360f * ((percent ?: 0.0) / 100.0).toFloat()
        if (sweep > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun Composer(
    state: UiState,
    viewModel: ChatViewModel,
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::attach)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::attach)
    }
    // Strings survive the camera activity taking over the process, unlike a Uri object.
    var pendingCaptureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCapturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCaptureUri?.let(Uri::parse)
        val path = pendingCapturePath
        pendingCaptureUri = null
        pendingCapturePath = null
        if (saved && uri != null) {
            // The FileProvider URI resolves its own display name and image MIME type.
            viewModel.attach(uri)
        } else {
            path?.let { File(it).delete() }
        }
    }
    val launchCamera: () -> Unit = {
        runCatching {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File.createTempFile("photo-", ".jpg", dir)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            pendingCaptureUri = uri.toString()
            pendingCapturePath = file.absolutePath
            takePicture.launch(uri)
        }.onFailure {
            Toast.makeText(context, strings.couldNotOpenCamera, Toast.LENGTH_SHORT).show()
        }
    }
    // Declaring CAMERA in the manifest makes the system camera intent require the grant.
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(context, strings.couldNotOpenCamera, Toast.LENGTH_SHORT).show()
        }
    }
    val openCamera: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    var showAttachPicker by rememberSaveable { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<Uri?>(null) }
    val focusManager = LocalFocusManager.current
    val token = findAutocompleteToken(state.draft)
    val suggestions = buildSuggestions(state, token, viewModel)
    // The String overload of OutlinedTextField keeps the old cursor offset when the draft grows
    // externally (e.g. picking a suggestion), which strands the caret after the trigger. Track the
    // selection ourselves so programmatic edits move the caret to the end of the new text.
    var draftField by remember { mutableStateOf(TextFieldValue(state.draft)) }
    LaunchedEffect(state.draft) { draftField = syncDraftField(draftField, state.draft) }
    val scope = state.workspaceScope ?: state.defaultWorkspaceScope
    var showAccessPicker by rememberSaveable { mutableStateOf(false) }
    var showModelPicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.chatId) {
        showAccessPicker = false
        showModelPicker = false
    }
    val modelPresets = modelPresetChoices(state)
    val selectedPreset = modelPresetName(state)
    val modelLabel = modelPillLabel(state, selectedPreset, modelPresets)
    val modelInitial = modelPillInitial(selectedPreset, modelPresets)
    val contextWindow = state.contextWindowTokens
        .takeIf { it > 0 }
        ?: modelPresets.firstOrNull { it.name == selectedPreset }
            ?.contextWindowTokens
            ?.takeIf { it > 0 }
        ?: state.gatewaySettings?.agent?.contextWindowTokens?.takeIf { it > 0 }
        ?: 0

    Surface(
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (state.attachments.isNotEmpty() || state.attachedCliApps.isNotEmpty() || state.attachedMcpPresets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.attachments.forEach { attachment ->
                        ComposerAttachmentTile(
                            attachment = attachment,
                            onRemove = { viewModel.removeAttachment(attachment.id) },
                            onPreview = { previewImage = attachment.uri },
                        )
                    }
                    state.attachedCliApps.forEach { name ->
                        AssistChip(
                            onClick = { viewModel.toggleCliApp(name) },
                            label = { Text("@$name ×") },
                        )
                    }
                    state.attachedMcpPresets.forEach { name ->
                        AssistChip(
                            onClick = { viewModel.toggleMcpPreset(name) },
                            label = { Text("@$name ×") },
                        )
                    }
                }
            }

            if (token != null && suggestions.isNotEmpty()) {
                AutocompletePanel(suggestions)
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = draftField,
                onValueChange = { value ->
                    draftField = value
                    if (value.text != state.draft) viewModel.setDraft(value.text)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(strings.composerHint) },
                maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
            )

            if (showAccessPicker && scope != null) {
                AccessModePicker(
                    scope = scope,
                    onSelect = { mode ->
                        viewModel.setAccessMode(mode)
                        showAccessPicker = false
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        showAccessPicker = false
                        showAttachPicker = true
                    },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_attach),
                        contentDescription = strings.attach,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }
                AccessModeChip(
                    scope = scope,
                    expanded = showAccessPicker,
                    onClick = {
                        showModelPicker = false
                        showAccessPicker = !showAccessPicker
                    },
                )
                // The trailing group takes the remaining width and ends with the send button, so
                // the composer's right edge stays put. The capsule has a fixed width, so a long
                // model name ellipsizes instead of squeezing the context ring.
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModelCapsule(
                        label = modelLabel,
                        initial = modelInitial,
                        expanded = showModelPicker,
                        enabled = state.connection is GatewayState.Ready && state.chatId != null,
                        presets = modelPresets,
                        selectedName = selectedPreset,
                        onToggle = {
                            showAccessPicker = false
                            if (state.gatewaySettings == null) viewModel.refreshGatewaySettings()
                            showModelPicker = !showModelPicker
                        },
                        onDismiss = { showModelPicker = false },
                        onSelect = { name ->
                            showModelPicker = false
                            viewModel.selectModelPreset(name)
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                    ContextUsageRing(tokens = state.contextTokens, windowTokens = contextWindow)
                    Spacer(Modifier.width(6.dp))
                    if (state.turnActive || state.streaming) {
                        FilledIconButton(
                            onClick = viewModel::pauseTurn,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_pause),
                                contentDescription = strings.pause,
                            )
                        }
                    } else {
                        FilledIconButton(
                            onClick = viewModel::send,
                            enabled = state.canSend,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_up),
                                contentDescription = strings.send,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAttachPicker) {
        AttachmentPickerSheet(
            onDismiss = { showAttachPicker = false },
            onTakePhoto = openCamera,
            onPickPhoto = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onPickFile = { filePicker.launch("*/*") },
        )
    }

    previewImage?.let { uri ->
        ImagePreviewDialog(uri = uri, onDismiss = { previewImage = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
) {
    val strings = LocalStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text(
                strings.addAttachment,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AttachmentOption(
                    iconRes = R.drawable.ic_camera,
                    label = strings.camera,
                    subtitle = strings.takePhoto,
                    modifier = Modifier
                        .weight(1f)
                        .height(132.dp),
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onTakePhoto()
                        }
                    },
                )
                AttachmentOption(
                    iconRes = R.drawable.ic_photo,
                    label = strings.photos,
                    subtitle = strings.chooseFromGallery,
                    modifier = Modifier
                        .weight(1f)
                        .height(132.dp),
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onPickPhoto()
                        }
                    },
                )
                AttachmentOption(
                    iconRes = R.drawable.ic_file,
                    label = strings.files,
                    subtitle = strings.browseFiles,
                    modifier = Modifier
                        .weight(1f)
                        .height(132.dp),
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onPickFile()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AttachmentOption(
    iconRes: Int,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ComposerAttachmentTile(
    attachment: ComposerAttachment,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
) {
    if (attachment.isImage) {
        ImageAttachmentTile(
            attachment = attachment,
            onRemove = onRemove,
            onPreview = onPreview,
        )
    } else {
        FileAttachmentTile(attachment = attachment, onRemove = onRemove)
    }
}

/** Thumbnail tile: the image itself is the attachment, with upload progress overlaid on it. */
@Composable
private fun ImageAttachmentTile(
    attachment: ComposerAttachment,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
) {
    val strings = LocalStrings.current
    val thumbnail = rememberUriImage(attachment.uri, maxSize = 360)
    val uploading = attachment.state == AttachmentState.Uploading
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                enabled = attachment.state == AttachmentState.Uploaded,
                onClick = onPreview,
            ),
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = attachment.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_photo),
                contentDescription = attachment.displayName,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(26.dp),
            )
        }
        if (uploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val progress = attachment.progress
                    if (progress != null && progress < 1f) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.5.dp,
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f),
                        )
                    } else {
                        // No byte count yet, or the body is out and we are waiting for the reply.
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.5.dp,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            progress == null -> strings.uploading
                            progress >= 1f -> strings.sending
                            else -> "${(progress * 100).toInt()}%"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
        if (attachment.state == AttachmentState.Failed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    strings.uploadFailed,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
        RemoveBadge(onRemove = onRemove, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/** Document tile in the WeChat/QQ style: file icon, name, and size/status in a rounded block. */
@Composable
private fun FileAttachmentTile(
    attachment: ComposerAttachment,
    onRemove: () -> Unit,
) {
    val strings = LocalStrings.current
    val failed = attachment.state == AttachmentState.Failed
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(
            1.dp,
            if (failed) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = Modifier
            .width(196.dp)
            .height(64.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(fileIconRes(attachment)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    attachment.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                when (attachment.state) {
                    AttachmentState.Uploading -> {
                        val progress = attachment.progress
                        if (progress != null && progress < 1f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                progress == null -> strings.uploading
                                progress >= 1f -> strings.sending
                                else -> strings.uploadingPercent((progress * 100).toInt())
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }

                    AttachmentState.Failed -> Text(
                        strings.uploadFailed,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    AttachmentState.Uploaded -> Text(
                        attachmentMeta(attachment),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = strings.removeAttachment,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun RemoveBadge(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Clear,
            contentDescription = LocalStrings.current.removeAttachment,
            tint = Color.White,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun ImagePreviewDialog(
    uri: Uri,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = rememberUriImage(uri, maxSize = 2048)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = strings.attachmentPreview,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                )
            } else {
                CircularProgressIndicator(color = Color.White)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = strings.closePreview,
                    tint = Color.White,
                )
            }
        }
    }
}

/** Attachments inside a sent bubble: thumbnails for images, compact cards for documents. */
@Composable
private fun MessageAttachmentStrip(
    attachments: List<MessageAttachment>,
    onPreview: (Uri) -> Unit,
    onLoad: (Int) -> Unit,
    mediaEpoch: Int,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEachIndexed { index, attachment ->
            if (attachment.isImage) {
                MessageImageTile(
                    attachment = attachment,
                    onPreview = onPreview,
                    onLoad = { onLoad(index) },
                    mediaEpoch = mediaEpoch,
                )
            } else {
                MessageFileTile(attachment = attachment)
            }
        }
    }
}

@Composable
private fun MessageImageTile(
    attachment: MessageAttachment,
    onPreview: (Uri) -> Unit,
    onLoad: () -> Unit,
    mediaEpoch: Int,
) {
    val uri = attachment.localUri
    // LazyColumn only composes visible bubbles, so this fetches images the user actually sees.
    // mediaEpoch retries a failed download after the next `ready`.
    LaunchedEffect(uri, attachment.remoteUrl, mediaEpoch) {
        if (uri == null && attachment.remoteUrl != null) onLoad()
    }
    val thumbnail = rememberUriImage(uri, maxSize = 300)
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (uri != null) Modifier.clickable { onPreview(uri) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_photo),
                contentDescription = attachment.name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun MessageFileTile(attachment: MessageAttachment) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(176.dp)
            .height(56.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(messageFileIcon(attachment)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    attachment.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val extension = fileExtension(attachment.name)
                if (extension.isNotBlank()) {
                    Text(
                        extension,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun messageFileIcon(attachment: MessageAttachment): Int {
    val name = attachment.name.lowercase(Locale.US)
    return if (attachment.mime.contains("zip") || name.endsWith(".zip") ||
        name.endsWith(".rar") || name.endsWith(".7z")
    ) {
        R.drawable.ic_archive
    } else {
        R.drawable.ic_file
    }
}

/** Decodes a downsampled bitmap off the main thread; [maxSize] is the longest edge in pixels. */
@Composable
private fun rememberUriImage(uri: Uri?, maxSize: Int): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri, maxSize) {
        value = uri?.let { target ->
            withContext(Dispatchers.IO) {
                decodeSampledBitmap(context, target, maxSize)?.asImageBitmap()
            }
        }
    }.value
}

private fun decodeSampledBitmap(context: Context, uri: Uri, maxSize: Int): Bitmap? {
    val resolver = context.contentResolver
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return@runCatching null
        var sample = 1
        while (width / sample > maxSize || height / sample > maxSize) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}

private fun fileIconRes(attachment: ComposerAttachment): Int {
    val name = attachment.name.lowercase(Locale.US)
    return when {
        attachment.isImage -> R.drawable.ic_photo
        attachment.mime.contains("zip") || name.endsWith(".zip") ||
            name.endsWith(".rar") || name.endsWith(".7z") -> R.drawable.ic_archive
        else -> R.drawable.ic_file
    }
}

private fun attachmentMeta(attachment: ComposerAttachment): String {
    val size = formatFileSize(attachment.sizeBytes)
    val extension = fileExtension(attachment.name)
    return when {
        size.isNotBlank() && extension.isNotBlank() -> "$size · $extension"
        size.isNotBlank() -> size
        extension.isNotBlank() -> extension
        else -> AppLocale.strings.file
    }
}

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[unit])
}

private fun fileExtension(name: String): String =
    name.substringAfterLast('.', "").take(5).uppercase(Locale.US)

@Composable
private fun AccessModeChip(
    scope: WorkspaceScope?,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val full = scope?.accessMode == "full"
    val tint = when {
        scope == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        full -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = CircleShape
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .background(
                if (expanded) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .clickable(enabled = scope != null, onClick = onClick)
            .padding(horizontal = 9.dp),
    ) {
        Icon(
            painterResource(if (full) R.drawable.ic_lock_open else R.drawable.ic_lock),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = tint,
        )
        Text(
            if (full) strings.fullAccess else strings.defaultAccess,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
        Icon(
            painterResource(R.drawable.ic_chevron_down),
            contentDescription = null,
            modifier = Modifier
                .size(12.dp)
                .rotate(if (expanded) 180f else 0f),
            tint = tint,
        )
    }
}

@Composable
private fun AccessModePicker(
    scope: WorkspaceScope,
    onSelect: (String) -> Unit,
) {
    val strings = LocalStrings.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(6.dp)) {
            AccessModeOption(
                title = strings.defaultAccess,
                subtitle = strings.defaultAccessDescription,
                iconRes = R.drawable.ic_lock,
                selected = scope.accessMode != "full",
                onClick = { onSelect("restricted") },
            )
            AccessModeOption(
                title = strings.fullAccess,
                subtitle = strings.fullAccessDescription,
                iconRes = R.drawable.ic_lock_open,
                iconTint = MaterialTheme.colorScheme.error,
                selected = scope.accessMode == "full",
                onClick = { onSelect("full") },
            )
        }
    }
}

@Composable
private fun AccessModeOption(
    title: String,
    subtitle: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = iconTint,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AutocompletePanel(suggestions: List<Suggestion>) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 248.dp),
            contentPadding = PaddingValues(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(suggestions, key = { it.key }) { suggestion ->
                SuggestionRow(suggestion)
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: Suggestion) {
    val colors = MaterialTheme.colorScheme
    val iconRes = when (suggestion.kind) {
        SuggestionKind.Command -> R.drawable.ic_terminal
        SuggestionKind.Skill -> R.drawable.ic_skills
        SuggestionKind.CliApp -> R.drawable.ic_terminal
        SuggestionKind.McpPreset -> R.drawable.ic_plug
    }
    val iconBackground = when (suggestion.kind) {
        SuggestionKind.Skill -> colors.primaryContainer
        SuggestionKind.Command -> colors.secondaryContainer
        SuggestionKind.CliApp, SuggestionKind.McpPreset -> colors.surfaceVariant
    }
    val iconTint = when (suggestion.kind) {
        SuggestionKind.Skill -> colors.onPrimaryContainer
        SuggestionKind.Command -> colors.onSecondaryContainer
        SuggestionKind.CliApp, SuggestionKind.McpPreset -> colors.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = suggestion.onPick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = iconTint,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                suggestion.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (suggestion.subtitle.isNotBlank()) {
                Text(
                    suggestion.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (suggestion.trailing.isNotBlank()) {
            Text(
                suggestion.trailing,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

private fun buildSuggestions(
    state: UiState,
    token: AutocompleteToken?,
    viewModel: ChatViewModel,
): List<Suggestion> {
    if (token == null) return emptyList()
    val query = token.query.lowercase()
    val draft = state.draft

    fun replace(text: String): String = replaceAutocompleteToken(draft, token, text)

    return when (token.trigger) {
        AutocompleteTrigger.Command -> state.commands
            .filter { it.command.removePrefix("/").lowercase().startsWith(query) }
            .take(8)
            .map { command ->
                val name = command.command.removePrefix("/")
                Suggestion(
                    key = "command-${command.command}",
                    title = command.title.ifBlank { name },
                    subtitle = command.description,
                    kind = SuggestionKind.Command,
                    trailing = "/$name",
                    onPick = { viewModel.setDraft(replace("/$name ")) },
                )
            }

        AutocompleteTrigger.Skill -> state.skills
            .filter { it.enabled && it.available && it.name.lowercase().startsWith(query) }
            .take(8)
            .map { skill ->
                Suggestion(
                    key = "skill-${skill.name}",
                    title = skill.name,
                    subtitle = skill.description,
                    kind = SuggestionKind.Skill,
                    onPick = { viewModel.setDraft(replace("\$${skill.name} ")) },
                )
            }

        AutocompleteTrigger.Mention -> buildList {
            state.cliApps
                .filter { it.name.lowercase().startsWith(query) || it.displayName.lowercase().startsWith(query) }
                .take(6)
                .forEach { app ->
                    add(
                        Suggestion(
                            key = "cli-${app.name}",
                            title = app.displayName.ifBlank { app.name },
                            subtitle = app.description.ifBlank { app.category },
                            kind = SuggestionKind.CliApp,
                            trailing = "@${app.name}",
                            onPick = {
                                viewModel.toggleCliApp(app.name)
                                viewModel.setDraft(replace(""))
                            },
                        ),
                    )
                }
            state.mcpPresets
                .filter { it.name.lowercase().startsWith(query) || it.displayName.lowercase().startsWith(query) }
                .take(6)
                .forEach { preset ->
                    add(
                        Suggestion(
                            key = "mcp-${preset.name}",
                            title = preset.displayName.ifBlank { preset.name },
                            subtitle = preset.category,
                            kind = SuggestionKind.McpPreset,
                            trailing = "@${preset.name}",
                            onPick = {
                                viewModel.toggleMcpPreset(preset.name)
                                viewModel.setDraft(replace(""))
                            },
                        ),
                    )
                }
        }
    }
}
