package dev.nanobot.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nanobot.protocol.AutomationChat
import dev.nanobot.protocol.AutomationChatsResponse
import dev.nanobot.protocol.AutomationJob
import dev.nanobot.protocol.AutomationOrigin
import dev.nanobot.protocol.AutomationPayload
import dev.nanobot.protocol.AutomationSchedule
import dev.nanobot.protocol.AutomationUpdate
import dev.nanobot.protocol.GatewayAgentSettings
import dev.nanobot.protocol.GatewayModelPreset
import dev.nanobot.protocol.GatewayRuntimeSettings
import dev.nanobot.protocol.GatewaySettings
import dev.nanobot.protocol.GatewayVersionInfo
import dev.nanobot.protocol.GatewayWebSettings
import dev.nanobot.protocol.McpPresetField
import dev.nanobot.protocol.McpPresetSummary
import dev.nanobot.protocol.SkillSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val skill = SkillSummary(
        name = "git",
        description = "Git helpers",
        source = "workspace",
        enabled = true,
    )

    private val mcpPreset = McpPresetSummary(
        name = "notion",
        displayName = "Notion",
        category = "Productivity",
        description = "Search and read Notion pages",
        transport = "streamableHttp",
        auth = "oauth",
        installed = true,
        configured = true,
        status = "configured",
        connectionSummary = "https://mcp.notion.com/mcp",
        toolCount = 2,
        toolNames = listOf("mcp_notion_search", "mcp_notion_fetch"),
        enabledTools = listOf("mcp_notion_search"),
        requiredFields = listOf(
            McpPresetField(name = "token", label = "API token", secret = true),
        ),
    )

    private val state = UiState(
        gatewaySettings = GatewaySettings(
            agent = GatewayAgentSettings(
                model = "claude-sonnet-5-5",
                provider = "anthropic",
                resolvedProvider = "anthropic",
            ),
            version = GatewayVersionInfo(current = "1.4.0"),
            web = GatewayWebSettings(enable = true),
        ),
        skills = listOf(skill),
        mcpPresets = listOf(mcpPreset),
        automations = listOf(
            AutomationJob(
                id = "job-1",
                name = "日报",
                enabled = true,
                schedule = AutomationSchedule(kind = "cron", expr = "0 9 * * *"),
                payload = AutomationPayload(message = "总结今天的对话"),
                origin = AutomationOrigin(
                    sessionKey = "websocket:chat-1",
                    channel = "websocket",
                    title = "日报",
                ),
                chatBindingRevision = "rev-1",
            ),
        ),
    )

    private val presetState = state.copy(
        gatewaySettings = GatewaySettings(
            agent = GatewayAgentSettings(
                model = "claude-haiku-5-5",
                provider = "anthropic",
                modelPreset = "fast",
            ),
            modelPresets = listOf(
                GatewayModelPreset(
                    name = "default",
                    isDefault = true,
                    model = "claude-sonnet-5-5",
                    provider = "anthropic",
                ),
                GatewayModelPreset(
                    name = "fast",
                    active = true,
                    model = "claude-haiku-5-5",
                    provider = "anthropic",
                    maxTokens = 4096,
                    temperature = 0.2,
                ),
            ),
            modelCallOrder = listOf("fast"),
            modelCallOrderEditable = true,
        ),
    )

    private fun setContent(
        value: UiState = state,
        onThemeChange: (ThemeMode) -> Unit = {},
        onAccentChange: (AccentTheme) -> Unit = {},
        onCustomAccentHueChange: (Float) -> Unit = {},
        onLanguageChange: (AppLanguage) -> Unit = {},
        onFontSizeChange: (AppFontSize) -> Unit = {},
        onRefreshCapabilities: () -> Unit = {},
        onSetSkillEnabled: (String, Boolean) -> Unit = { _, _ -> },
        onLoadSkillDetail: (String, Boolean) -> Unit = { _, _ -> },
        onDeleteSkill: (String) -> Unit = {},
        onRunAutomation: (String) -> Unit = {},
        onDeleteAutomation: (String) -> Unit = {},
        onUpdateAutomation: (String, AutomationUpdate) -> Unit = { _, _ -> },
        onLoadAutomationChats: (String) -> Unit = {},
        onChangeAutomationChat: (String, String, String, String) -> Unit = { _, _, _, _ -> },
        onRefreshGatewaySettings: () -> Unit = {},
        gatewayActions: GatewaySettingsActions = GatewaySettingsActions(),
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalStrings provides ChineseStrings) {
                MaterialTheme {
                    SettingsScreen(
                        state = value,
                        onDismiss = {},
                        onThemeChange = onThemeChange,
                        onAccentChange = onAccentChange,
                        onCustomAccentHueChange = onCustomAccentHueChange,
                        onLanguageChange = onLanguageChange,
                        onFontSizeChange = onFontSizeChange,
                        onTurnNotificationsChange = {},
                        onNotificationSoundChange = {},
                        onRefreshGatewaySettings = onRefreshGatewaySettings,
                        onReconnect = {},
                        onManageHosts = {},
                        onAddHost = {},
                        onRefreshCapabilities = onRefreshCapabilities,
                        onSetSkillEnabled = onSetSkillEnabled,
                        gatewayActions = gatewayActions,
                        onLoadSkillDetail = onLoadSkillDetail,
                        onClearSkillDetail = {},
                        onDeleteSkill = onDeleteSkill,
                        onMcpAction = { _, _ -> },
                        onSetMcpFieldValue = { _, _, _ -> },
                        onSaveMcpServer = {},
                        onImportMcpConfig = {},
                        onUpdateMcpTools = { _, _ -> },
                        onStartMcpOAuth = { _, _ -> },
                        onCompleteMcpOAuth = {},
                        onCancelMcpOAuth = {},
                        onSetAutomationEnabled = { _, _ -> },
                        onRunAutomation = onRunAutomation,
                        onDeleteAutomation = onDeleteAutomation,
                        onUpdateAutomation = onUpdateAutomation,
                        onLoadAutomationChats = onLoadAutomationChats,
                        onChangeAutomationChat = onChangeAutomationChat,
                        onClearAutomationAction = {},
                        onClearAutomationChats = {},
                        onClearMessage = {},
                    )
                }
            }
        }
    }

    private fun openFromRoot(label: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        composeRule.onNodeWithText(label).performClick()
    }

    @Test
    fun `root lists the settings categories`() {
        var refreshes = 0
        setContent(onRefreshGatewaySettings = { refreshes += 1 })

        composeRule.onNodeWithText("设置").assertExists()
        composeRule.onNodeWithText("外观").assertExists()
        composeRule.onNodeWithText("通知").assertExists()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("网关"))
        composeRule.onNodeWithText("网关").assertExists()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("模型"))
        composeRule.onNodeWithText("claude-sonnet-5-5").assertExists()
        assertTrue(refreshes >= 1)
    }

    @Test
    fun `appearance page changes the theme`() {
        var chosen: ThemeMode? = null
        setContent(onThemeChange = { chosen = it })

        openFromRoot("外观")
        composeRule.onNodeWithText("跟随系统").assertExists()
        composeRule.onNodeWithText("深色").performClick()
        assertEquals(ThemeMode.Dark, chosen)
    }

    @Test
    fun `appearance page changes the accent`() {
        var chosen: AccentTheme? = null
        setContent(onAccentChange = { chosen = it })

        openFromRoot("外观")
        composeRule.onNodeWithText("暮紫").performClick()
        assertEquals(AccentTheme.Dusk, chosen)
    }

    @Test
    fun `appearance page changes the language and text size`() {
        var language: AppLanguage? = null
        var fontSize: AppFontSize? = null
        setContent(
            onLanguageChange = { language = it },
            onFontSizeChange = { fontSize = it },
        )

        openFromRoot("外观")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("简体中文"))
        composeRule.onNodeWithText("简体中文").assertExists()
        composeRule.onNodeWithText("English").performClick()
        assertEquals(AppLanguage.English, language)

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("特大"))
        composeRule.onNodeWithText("特大").performClick()
        assertEquals(AppFontSize.ExtraLarge, fontSize)
    }

    @Test
    fun `gateway page shows runtime and version details`() {
        setContent()

        openFromRoot("网关")
        composeRule.onNodeWithText("运行环境").assertExists()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("1.4.0"))
        composeRule.onNodeWithText("1.4.0").assertExists()
        composeRule.onNodeWithText("最大输出").assertDoesNotExist()
    }

    @Test
    fun `model page shows model details`() {
        setContent()

        openFromRoot("模型")
        composeRule.onNodeWithText("最大输出").assertExists()
        composeRule.onNodeWithText("claude-sonnet-5-5").assertExists()
        composeRule.onNodeWithText("anthropic").assertExists()
        composeRule.onNodeWithText("运行环境").assertDoesNotExist()
    }

    @Test
    fun `model page saves edits to the active model`() {
        var saved: List<Any?>? = null
        setContent(
            gatewayActions = GatewaySettingsActions(
                updateAgent = { preset, model, provider, context, timezone ->
                    saved = listOf(preset, model, provider, context, timezone)
                },
            ),
        )

        openFromRoot("模型")
        composeRule.onNodeWithText("claude-sonnet-5-5")
            .performTextReplacement("claude-opus-5-5")
        composeRule.onNodeWithText("保存").performScrollTo()
        composeRule.onNodeWithText("保存").assertIsDisplayed()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals(
            listOf("default", "claude-opus-5-5", "anthropic", null, null),
            saved,
        )
    }

    @Test
    fun `model editor follows the active preset when it changes`() {
        val current = mutableStateOf(presetState)
        composeRule.setContent {
            CompositionLocalProvider(LocalStrings provides ChineseStrings) {
                MaterialTheme {
                    ModelPage(
                        state = current.value,
                        strings = ChineseStrings,
                        onRefresh = {},
                        actions = GatewaySettingsActions(),
                    )
                }
            }
        }

        // "fast" is active, so the editor starts on its model.
        composeRule.onNodeWithText("claude-haiku-5-5").assertExists()

        val settings = presetState.gatewaySettings!!
        current.value = presetState.copy(
            gatewaySettings = settings.copy(
                agent = settings.agent.copy(
                    model = "claude-sonnet-5-5",
                    modelPreset = "default",
                ),
                modelPresets = listOf(
                    GatewayModelPreset(
                        name = "default",
                        isDefault = true,
                        active = true,
                        model = "claude-sonnet-5-5",
                        provider = "anthropic",
                    ),
                    GatewayModelPreset(
                        name = "fast",
                        model = "claude-haiku-5-5",
                        provider = "anthropic",
                        maxTokens = 4096,
                        temperature = 0.2,
                    ),
                ),
            ),
        )
        composeRule.waitForIdle()

        // The editor must follow the server-side active preset instead of keeping "fast".
        composeRule.onNodeWithText("claude-sonnet-5-5").assertExists()
    }

    @Test
    fun `model page creates a named preset`() {
        var created: List<Any?>? = null
        setContent(
            value = presetState,
            gatewayActions = GatewaySettingsActions(
                createModelConfiguration = { name, model, provider, max, context, temp, reasoning ->
                    created = listOf(name, model, provider, max, context, temp, reasoning)
                },
            ),
        )

        openFromRoot("模型")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("新建预设"))
        composeRule.onNodeWithText("新建预设").performClick()
        composeRule.onNodeWithTag(MODEL_PRESET_NAME_TAG).performTextInput("balanced")
        composeRule.onAllNodesWithText("保存").onLast().performClick()

        assertEquals(
            listOf("balanced", "claude-haiku-5-5", "anthropic", 4096, null, 0.2, ""),
            created,
        )
    }

    @Test
    fun `model page toggles a preset in the call order`() {
        var toggled: Pair<String, Boolean>? = null
        setContent(
            value = presetState,
            gatewayActions = GatewaySettingsActions(
                setModelPresetEnabled = { name, enabled -> toggled = name to enabled },
            ),
        )

        openFromRoot("模型")
        // The preset list is a separate lazy item; compose it before looking for the switch.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("新建预设"))
        composeRule.onNode(isToggleable()).performScrollTo().performClick()

        assertEquals("fast" to false, toggled)
    }

    @Test
    fun `gateway page toggles the shared session`() {
        var shared: Boolean? = null
        setContent(
            value = state.copy(
                gatewaySettings = state.gatewaySettings!!.copy(
                    runtime = GatewayRuntimeSettings(unifiedSession = false),
                ),
            ),
            gatewayActions = GatewaySettingsActions(setUnifiedSession = { shared = it }),
        )

        openFromRoot("网关")
        composeRule.onNode(isToggleable()).performClick()

        assertEquals(true, shared)
    }

    @Test
    fun `skills page opens a detail page and toggles the skill`() {
        var toggled: Pair<String, Boolean>? = null
        var loaded: Pair<String, Boolean>? = null
        setContent(
            onSetSkillEnabled = { name, enabled -> toggled = name to enabled },
            onLoadSkillDetail = { name, force -> loaded = name to force },
        )

        openFromRoot("技能")
        composeRule.onNodeWithText("git").performClick()
        composeRule.onNodeWithText("使用此技能").assertExists()
        assertEquals("git" to false, loaded)
        composeRule.onNode(isToggleable()).performClick()
        assertEquals("git" to false, toggled)
    }

    @Test
    fun `skills page search filters the catalog`() {
        val twoSkills = state.copy(
            skills = listOf(
                skill,
                SkillSummary(name = "pdf", description = "PDF helpers", source = "builtin"),
            ),
        )
        setContent(value = twoSkills)

        openFromRoot("技能")
        composeRule.onNode(hasContentDescription("搜索")).performClick()
        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("pdf")
        composeRule.onNodeWithText("PDF helpers").assertExists()
        composeRule.onNodeWithText("Git helpers").assertDoesNotExist()
    }

    @Test
    fun `mcp page opens management and switches to the tool scope`() {
        setContent()

        openFromRoot("MCP 预设")
        composeRule.onNodeWithText("Notion").performClick()
        // The tab and the overview section share this label.
        composeRule.onAllNodesWithText("概览").onFirst().assertExists()
        composeRule.onNodeWithText("Streamable HTTP").assertExists()
        // The tab and the connection row share this label; the tab comes first.
        composeRule.onAllNodesWithText("工具").onFirst().performClick()
        composeRule.onNodeWithText("search").assertExists()
        composeRule.onNodeWithText("已选 1 / 2").assertExists()
    }

    @Test
    fun `automation page opens details and runs a job`() {
        var ran: String? = null
        setContent(onRunAutomation = { ran = it })

        openFromRoot("自动化")
        composeRule.onNodeWithText("日报").performClick()
        composeRule.onNodeWithText("任务说明").assertExists()
        composeRule.onNodeWithText("总结今天的对话").assertExists()
        composeRule.onNodeWithText("下次运行").assertExists()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("立即运行"))
        composeRule.onNodeWithText("运行").performClick()
        assertEquals("job-1", ran)
    }

    @Test
    fun `automation detail edits the task name`() {
        var saved: AutomationUpdate? = null
        setContent(onUpdateAutomation = { _, update -> saved = update })

        openFromRoot("自动化")
        composeRule.onNodeWithText("日报").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("编辑自动化"))
        composeRule.onNodeWithText("编辑").performClick()

        composeRule.onNodeWithText("日报").performTextReplacement("周报")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("保存"))
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("周报", saved?.name)
        assertEquals("总结今天的对话", saved?.message)
    }

    @Test
    fun `automation detail moves the task to another chat`() {
        var target: String? = null
        var revision: String? = null
        var movedMessage: String? = null
        val chats = state.copy(
            automationChatsFor = "job-1",
            automationChats = AutomationChatsResponse(
                revision = "rev-1",
                current = AutomationChat("ws-1", "日报", "websocket"),
                chats = listOf(
                    AutomationChat("ws-1", "日报", "websocket"),
                    AutomationChat("ws-2", "项目群", "websocket"),
                ),
            ),
        )
        setContent(
            value = chats,
            onChangeAutomationChat = { _, targetId, chatRevision, message ->
                target = targetId
                revision = chatRevision
                movedMessage = message
            },
        )

        openFromRoot("自动化")
        composeRule.onNodeWithText("日报").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("更换关联会话"))
        composeRule.onNodeWithText("更换会话").performClick()

        composeRule.onNodeWithText("项目群").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("确认更换"))
        composeRule.onNodeWithText("确认更换").performClick()

        assertEquals("ws-2", target)
        assertEquals("rev-1", revision)
        assertEquals("总结今天的对话", movedMessage)
    }

    @Test
    fun `about page offers an update when a newer release exists`() {
        setContent(
            value = state.copy(
                updateStatus = UpdateStatus.Available(
                    version = "0.2.0",
                    downloadUrl = "https://example.com/nanobot-mobile-v0.2.0.apk",
                    releaseUrl = "https://example.com/releases/v0.2.0",
                ),
            ),
        )

        openFromRoot("关于")

        composeRule.onNodeWithText("发现新版本 0.2.0").assertIsDisplayed()
        composeRule.onNodeWithText("下载更新").assertIsDisplayed()
    }

    @Test
    fun `about page shows the latest state without an update button`() {
        setContent(value = state.copy(updateStatus = UpdateStatus.UpToDate("0.1.0")))

        openFromRoot("关于")

        composeRule.onNodeWithText("已是最新版本").assertIsDisplayed()
        composeRule.onAllNodesWithText("下载更新").assertCountEquals(0)
    }

    @Test
    fun `about page checks for updates on first open`() {
        var checks = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalStrings provides ChineseStrings) {
                MaterialTheme {
                    SettingsScreen(
                        state = state,
                        onDismiss = {},
                        onThemeChange = {},
                        onAccentChange = {},
                        onCustomAccentHueChange = {},
                        onLanguageChange = {},
                        onFontSizeChange = {},
                        onTurnNotificationsChange = {},
                        onNotificationSoundChange = {},
                        onRefreshGatewaySettings = {},
                        onReconnect = {},
                        onManageHosts = {},
                        onAddHost = {},
                        onRefreshCapabilities = {},
                        onSetSkillEnabled = { _, _ -> },
                        onLoadSkillDetail = { _, _ -> },
                        onClearSkillDetail = {},
                        onDeleteSkill = {},
                        onMcpAction = { _, _ -> },
                        onSetMcpFieldValue = { _, _, _ -> },
                        onSaveMcpServer = {},
                        onImportMcpConfig = {},
                        onUpdateMcpTools = { _, _ -> },
                        onStartMcpOAuth = { _, _ -> },
                        onCompleteMcpOAuth = {},
                        onCancelMcpOAuth = {},
                        onSetAutomationEnabled = { _, _ -> },
                        onRunAutomation = {},
                        onDeleteAutomation = {},
                        onUpdateAutomation = { _, _ -> },
                        onLoadAutomationChats = {},
                        onChangeAutomationChat = { _, _, _, _ -> },
                        onClearAutomationAction = {},
                        onClearAutomationChats = {},
                        onClearMessage = {},
                        onCheckForUpdates = { checks += 1 },
                    )
                }
            }
        }

        openFromRoot("关于")
        composeRule.waitForIdle()

        assertEquals(1, checks)
    }
}
