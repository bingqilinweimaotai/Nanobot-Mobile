package dev.nanobot.android

import androidx.compose.runtime.staticCompositionLocalOf

/** The language the interface is drawn in, chosen in the Appearance settings. */
enum class AppLanguage(val storageValue: String, val nativeName: String) {
    SimplifiedChinese("zh-CN", "简体中文"),
    English("en", "English"),
    ;

    companion object {
        fun fromStorage(value: String?): AppLanguage? =
            entries.firstOrNull { it.storageValue == value }

        /** Pairing and first launch default to Simplified Chinese; the user can override it afterwards. */
        fun default(): AppLanguage = SimplifiedChinese
    }
}

/** Text size multiplier applied on top of the system font scale. */
enum class AppFontSize(val storageValue: String, val scale: Float) {
    Small("small", 0.9f),
    Standard("standard", 1.0f),
    Large("large", 1.15f),
    ExtraLarge("extra_large", 1.3f),
    ;

    companion object {
        fun fromStorage(value: String?): AppFontSize =
            entries.firstOrNull { it.storageValue == value } ?: Standard
    }
}

/**
 * Every user-facing string in the app. Both implementations must stay complete, so adding a
 * string here is a compile error until it is translated.
 */
internal interface AppStrings {
    // Appearance and settings
    val settings: String
    val appearance: String
    val notifications: String
    val hostsAndConnection: String
    val gateway: String
    val skills: String
    val mcpPresets: String
    val cliApps: String
    val automations: String
    val about: String
    val general: String
    val connection: String
    val capabilities: String
    val other: String
    val localPrefsAndGatewayInfo: String
    val closeSettings: String
    val back: String
    val dismissMessage: String
    val theme: String
    val themeDescription: String
    val light: String
    val dark: String
    val followSystem: String
    val colorScheme: String
    val customColor: String
    val dragToChooseHue: String
    val followWallpaper: String
    val followWallpaperDescription: String
    val appearanceNote: String
    val textSize: String
    val textSizeDescription: String
    val textSizeSmall: String
    val textSizeStandard: String
    val textSizeLarge: String
    val textSizeExtraLarge: String
    val language: String
    val languageDescription: String

    // Root summaries
    val noHost: String
    val tapForGatewayDetails: String
    val tapForModelDetails: String
    val turnNotificationsOn: String
    val turnNotificationsOff: String
    fun countEnabled(total: Int, count: Int): String
    fun countConfigured(total: Int, count: Int): String
    fun countInstalled(total: Int, count: Int): String
    fun versionLabel(version: String): String

    // Notifications
    val turnNotifications: String
    val turnNotificationsDescription: String
    val notificationSound: String
    val notificationSoundDescription: String

    // Host page
    val currentHost: String
    val type: String
    val direct: String
    val relay: String
    val address: String
    val actions: String
    val reconnect: String
    val reconnectDescription: String
    val addHost: String
    val addHostDescription: String
    val manageHosts: String
    val manageHostsDescription: String
    val noHostsSaved: String
    val remove: String
    val close: String
    val pairingLink: String
    val manual: String
    val pair: String
    val add: String
    val cancel: String
    val nameOptional: String
    val gatewayAddress: String
    val tokenIssueSecret: String
    val runGatewayHint: String
    val pairIntro: String
    val addHostPairIntro: String
    val currentHostCheck: String
    val gatewayHostDefault: String

    // Gateway page
    val refresh: String
    val connectToView: String
    val model: String
    val provider: String
    val modelPreset: String
    val maxOutput: String
    val contextWindow: String
    val temperature: String
    val reasoningEffort: String
    val timezone: String
    val runtime: String
    val workspace: String
    val configFile: String
    val sharedSession: String
    val yes: String
    val no: String
    val version: String
    val gatewayVersion: String
    val commit: String
    val restartPending: String
    val changesPendingRestart: String
    val none: String

    // Model editing and preset management
    val currentModel: String
    val selectPreset: String
    val newPreset: String
    val presetName: String
    val makeActive: String
    val activePreset: String
    val defaultPreset: String
    val defaultPresetHint: String
    val settingsSaved: String
    val couldNotSaveSettings: String
    val keepOneModelPreset: String
    val modelRequired: String
    val providerRequired: String
    val presetNameRequired: String
    val invalidNumber: String
    val invalidTemperature: String
    val reasoningDefault: String
    val sharedSessionDescription: String
    fun deletePresetConfirmation(name: String): String

    // Capability pages
    val noSkills: String
    val unavailable: String
    val noMcpPresets: String
    fun toolCount(count: Int): String
    val notConfigured: String

    // MCP management, mirroring the WebUI's overview / tools / connection dialog
    val mcpOverview: String
    val mcpTools: String
    val mcpConnection: String
    val mcpSearch: String
    val mcpNoMatchingPresets: String
    val mcpOpenDocs: String
    val mcpStatus: String
    val mcpTransport: String
    val mcpToolScope: String
    val mcpConfigured: String
    val mcpNeedsKey: String
    val mcpNeedsDependency: String
    val mcpNeedsAuthorization: String
    val mcpComingSoon: String
    val mcpNotEnabled: String
    val mcpEnabled: String
    val mcpDisabled: String
    val mcpConnected: String
    val mcpConnecting: String
    val mcpConnectionFailed: String
    val mcpManage: String
    val mcpConfigure: String
    val mcpEnable: String
    val mcpConnect: String
    val mcpReconnect: String
    val mcpRemove: String
    fun mcpRemoveTitle(name: String): String
    val mcpRemoveDescription: String
    val mcpTestTools: String
    val mcpReloadTools: String
    val mcpNoToolsAvailable: String
    val mcpSearchTools: String
    val mcpNoMatchingTools: String
    val mcpAllTools: String
    val mcpClearTools: String
    val mcpApplyChanges: String
    fun mcpSelectedTools(selected: Int, total: Int): String
    val mcpConnectionDetails: String
    val mcpCredentials: String
    val mcpConfiguredField: String
    val mcpKeepExisting: String
    val mcpUseProxy: String
    val mcpUseProxyDescription: String
    val mcpAuthorization: String
    val mcpSignIn: String
    val mcpReauthorize: String
    val mcpContinueSignIn: String
    val mcpCallbackUrl: String
    val mcpCallbackHelp: String
    val mcpFinishSignIn: String
    val mcpCancelSignIn: String
    val mcpSignInPreparing: String
    val mcpSignInWaiting: String
    val mcpSignInConnecting: String
    val mcpSignInAuthorized: String
    val mcpAddServer: String
    val mcpAddServerDescription: String
    val mcpCustomServer: String
    val mcpImportConfig: String
    val mcpServerName: String
    val mcpServerUrl: String
    val mcpCommand: String
    val mcpArguments: String
    val mcpEnvironment: String
    val mcpHeaders: String
    val mcpHeadersHelp: String
    val mcpAdvancedOptions: String
    val mcpHideAdvanced: String
    val mcpToolTimeout: String
    val mcpSaveServer: String
    val mcpImport: String
    val mcpConfigPlaceholder: String
    val mcpAuthNone: String
    val mcpAuthHeaders: String
    val mcpPluginManageOnly: String
    val mcpRestartPending: String
    val mcpOAuthConnected: String
    val mcpOAuthCancelled: String
    val mcpOAuthFailed: String
    val mcpOAuthCallbackFailed: String
    val mcpSettingsUpdated: String
    val noCliApps: String
    val installed: String
    val installable: String
    val noAutomations: String
    val paused: String
    val run: String
    val scheduled: String
    val periodic: String
    val once: String
    val localTrigger: String
    fun everyHours(hours: Long): String
    fun everyMinutes(minutes: Long): String
    fun lastStatus(status: String): String

    // Automation detail, mirroring the WebUI's task information dialog
    val automationTaskInfo: String
    val automationSchedule: String
    val automationNextRun: String
    val automationLastRun: String
    val automationLastStatus: String
    val automationTimezone: String
    val automationOneTime: String
    val automationOneTimeHint: String
    val automationCreated: String
    val automationUpdated: String
    val automationIdentifier: String
    val automationInstructions: String
    val automationCommand: String
    val automationLinkedChat: String
    val automationLastError: String
    val automationRecentRuns: String
    val automationNeverRun: String
    val automationNoNextRun: String
    val automationWaitingForTrigger: String
    val automationRunning: String
    val automationCompleted: String
    val automationFailed: String
    val automationSkipped: String
    val automationSystem: String
    val automationSystemHint: String
    val automationNotFound: String
    val automationEnable: String
    val automationDisable: String
    val automationRunNow: String
    val automationDelete: String
    val automationDeleteTitle: String
    fun automationDeleteDescription(name: String): String
    fun automationAt(time: String): String
    fun automationEvery(millis: Long): String
    fun automationCronSchedule(expr: String): String
    fun automationRelative(deltaMs: Long): String
    fun automationDuration(millis: Long): String

    // Automation editing and chat binding, mirroring the WebUI's edit and change-chat dialogs
    val automationEdit: String
    val automationEditTitle: String
    val automationName: String
    val automationMessage: String
    val automationScheduleType: String
    val automationEveryLabel: String
    val automationUnit: String
    val automationCronExpression: String
    val automationRunAt: String
    val automationChooseTime: String
    val automationSave: String
    val automationNameRequired: String
    val automationMessageRequired: String
    val automationIntervalRequired: String
    val automationCronRequired: String
    val automationCronInvalid: String
    val automationFutureRequired: String
    val automationSaved: String
    val automationScheduleEveryType: String
    val automationScheduleCronType: String
    val automationScheduleAtType: String
    val automationUnitSecond: String
    val automationUnitMinute: String
    val automationUnitHour: String
    val automationUnitDay: String
    val automationChangeChat: String
    val automationChatTitle: String
    val automationCurrentChat: String
    val automationNewChat: String
    val automationChatHint: String
    val automationChatMessage: String
    val automationChatConfirm: String
    val automationChatsEmpty: String
    val automationChatsLoadFailed: String
    val automationChatConflict: String
    val automationChatBusy: String
    val automationChatUnavailable: String
    val automationChatRevision: String
    val automationChatWeb: String
    val automationRetry: String
    fun automationChatChanged(title: String): String

    // Skills catalog and detail
    val searchSkills: String
    val closeSearch: String
    val noMatchingSkills: String
    val skillEnabled: String
    val skillDisabled: String
    val needsSetup: String
    val sourceCustom: String
    val sourceBuiltin: String
    val useSkill: String
    val skillInstructions: String
    val showMore: String
    val showLess: String
    val setupRequired: String
    val setupDescription: String
    val missingCommands: String
    val missingEnvironment: String
    val checkAgain: String
    val noSkillInstructions: String
    val loadingSkillDetail: String
    val skillNotFound: String
    val deleteSkill: String
    val deleteSkillDescription: String
    val deleteSkillConfirmDescription: String
    fun deleteSkillTitle(name: String): String
    fun skillDeletedNamed(name: String): String
    val couldNotLoadSkillDetail: String
    val couldNotDeleteSkill: String

    // About
    val client: String
    val protocol: String
    val storage: String
    val storedOnDeviceOnly: String
    val updates: String
    val checkForUpdates: String
    val checkingForUpdates: String
    val upToDate: String
    fun updateAvailable(version: String): String
    val downloadUpdate: String
    val noPublishedRelease: String
    val couldNotCheckUpdates: String

    // Setup
    val setupIntro: String
    val paste: String
    val scanQr: String
    val orConnectDirectly: String
    val cleartextHint: String
    val connect: String

    // Shell, connection, and search
    val sessions: String
    val new: String
    val newChat: String
    val myMessages: String
    val searchConversations: String
    val clearSearch: String
    val jumpToLatest: String
    val noMatchingConversations: String
    val mePrefix: String
    val assistantPrefix: String
    val hosts: String
    val addWorkspace: String
    val search: String
    val showArchived: String
    val hideArchived: String
    val offline: String
    val connecting: String
    val connected: String
    fun reconnecting(attempt: Int): String
    val relayStopped: String
    val relayConnecting: String
    fun relayReconnecting(attempt: Int): String
    fun relayFailed(reason: String): String

    // Sidebar
    val pin: String
    val unpin: String
    val rename: String
    val archive: String
    val unarchive: String
    val select: String
    val delete: String
    val running: String
    val unread: String
    val topics: String
    val chats: String
    val pinned: String
    val today: String
    val yesterday: String
    val earlier: String
    val archived: String
    val emptyChats: String
    fun actionsFor(title: String): String
    fun selectedCount(count: Int): String
    val deleteChatTitle: String
    fun deleteChatsTitle(count: Int): String
    val deleteDescription: String
    val deleteAutomationsNote: String
    val deleteWithAutomations: String
    val archiveChatTitle: String
    fun archiveChatsTitle(count: Int): String
    val archiveDescription: String
    val unarchiveChatTitle: String
    fun unarchiveChatsTitle(count: Int): String
    val unarchiveDescription: String
    val renameChatTitle: String
    val renameChatPlaceholder: String
    val save: String
    fun forkChatTitle(title: String): String

    // Chat
    val emptyConversation: String
    val createChat: String
    val composerHint: String
    val copied: String
    val copy: String
    fun attachmentCount(count: Int): String
    val sending: String
    val notConfirmed: String
    val stopped: String
    val forkFromHere: String
    val attach: String
    val pause: String
    val send: String
    val addAttachment: String
    val photos: String
    val chooseFromGallery: String
    val files: String
    val browseFiles: String
    val uploading: String
    val uploadFailed: String
    fun uploadingPercent(percent: Int): String
    val removeAttachment: String
    val attachmentPreview: String
    val closePreview: String
    val file: String
    val fullAccess: String
    val defaultAccess: String
    val defaultAccessDescription: String
    val fullAccessDescription: String
    val attachmentPlaceholder: String
    val camera: String
    val takePhoto: String
    val switchModel: String
    val defaultModelPreset: String
    val noModelPresets: String
    val contextUsageUnknown: String
    fun contextUsage(percent: Int): String
    val couldNotSwitchModel: String
    val couldNotOpenCamera: String

    // Workspace picker
    val backToSavedWorkspaces: String
    val favorites: String
    val recent: String
    val noSavedWorkspaces: String
    val browseFolders: String
    val upOneLevel: String
    val filterFolders: String
    val noSubfolders: String
    val noMatchingFolders: String
    val showingFirst500: String
    val cannotReadFolder: String

    // Message history
    val searchMessages: String
    fun messageCount(count: Int): String
    fun matchCount(count: Int): String
    val noMessagesYet: String
    val noMessagesMatch: String

    // Reasoning
    val thinkingStopped: String
    val thinking: String
    val thought: String
    val working: String
    fun stepsLabel(count: Int): String
    fun reasoningDescription(label: String, count: Int, expanded: Boolean): String

    // QR scanner
    val closeScanner: String
    val scanQrCode: String
    val alignQrInFrame: String
    val turnOnTorch: String
    val turnOffTorch: String
    val torch: String
    val torchOn: String

    // Notifications emitted by the background service
    val connectedToGateway: String
    val turnCompleted: String
    val turnFailed: String
    val turnCancelled: String
    val gatewayError: String
    val nanobotError: String
    val channelGatewayConnection: String
    val channelTurnResults: String

    // Pixel cat and system chrome
    val pixelCat: String
    val tapMe: String
    val settingsContentDescription: String

    // Errors written by this client
    val enterGatewayAddress: String
    val invalidPairingLink: String
    val couldNotSaveHost: String
    val couldNotConnectHost: String
    val attachmentUploadFailed: String
    fun attachmentTooLarge(size: String, limit: String): String
    fun attachmentTooLargeAfterCompression(size: String, limit: String): String
    val couldNotLoadGatewaySettings: String
    val notConnectedToGateway: String
    val couldNotUpdateSkills: String
    val couldNotUpdateMcp: String
    val couldNotUpdateAutomation: String
    val couldNotRunAutomation: String
    val couldNotDeleteAutomation: String
    val couldNotLoadAutomationChats: String
    val couldNotChangeAutomationChat: String
    val couldNotSend: String
    val couldNotCreateChat: String
    val couldNotDelete: String
    val couldNotUpdateSidebar: String
    val sidebarNotLoaded: String
    val couldNotForkChat: String
    val couldNotOpenWorkspace: String
    val startedAutomation: String
    fun startedAutomationNamed(name: String): String

    // Relay failure reasons shown under the host status
    val relayUnreachable: String
    val relayConnectionFailed: String
    val relayRefusedPhone: String
    val relayClosedConnection: String
    val relayDidNotAcceptPhone: String
    val relayInvalidFrame: String
    val pcClosedStream: String
    val relayClosedDataChannel: String
    val relayDataChannelFailed: String
    val relayDidNotOpenDataChannel: String
    val relayNotConnected: String
    val pcDidNotAcceptStream: String
    val pcSentUndecryptableFrame: String
    val phoneTooSlow: String
    val relayTooSlow: String
    val phoneClosedStream: String

    fun accentName(accent: AccentTheme): String
    fun themeModeName(mode: ThemeMode): String
    fun fontSizeName(size: AppFontSize): String
}

internal object ChineseStrings : AppStrings {
    override val settings = "设置"
    override val appearance = "外观"
    override val notifications = "通知"
    override val hostsAndConnection = "主机与连接"
    override val gateway = "网关"
    override val skills = "技能"
    override val mcpPresets = "MCP 预设"
    override val cliApps = "命令行应用"
    override val automations = "自动化"
    override val about = "关于"
    override val general = "通用"
    override val connection = "连接"
    override val capabilities = "能力"
    override val other = "其他"
    override val localPrefsAndGatewayInfo = "本地偏好与网关信息"
    override val closeSettings = "关闭设置"
    override val back = "返回"
    override val dismissMessage = "关闭提示"
    override val theme = "明暗"
    override val themeDescription = "选择应用的明暗外观。"
    override val light = "浅色"
    override val dark = "深色"
    override val followSystem = "跟随系统"
    override val colorScheme = "配色"
    override val customColor = "自定义颜色"
    override val dragToChooseHue = "拖动选择色相。"
    override val followWallpaper = "跟随壁纸"
    override val followWallpaperDescription = "使用系统壁纸的颜色（Android 12 及以上）"
    override val appearanceNote = "外观设置只保存在这台设备上，不会改动网关配置。"
    override val textSize = "字号"
    override val textSizeDescription = "调整应用内的文字大小。"
    override val textSizeSmall = "小"
    override val textSizeStandard = "标准"
    override val textSizeLarge = "大"
    override val textSizeExtraLarge = "特大"
    override val language = "语言"
    override val languageDescription = "选择应用界面使用的语言。"

    override val noHost = "未选择主机"
    override val tapForGatewayDetails = "点击查看网关信息"
    override val tapForModelDetails = "点击查看模型参数"
    override val turnNotificationsOn = "回合完成通知 · 开"
    override val turnNotificationsOff = "回合完成通知 · 关"
    override fun countEnabled(total: Int, count: Int) = "$total 个 · $count 已启用"
    override fun countConfigured(total: Int, count: Int) = "$total 个 · $count 已配置"
    override fun countInstalled(total: Int, count: Int) = "$total 个 · $count 已安装"
    override fun versionLabel(version: String) = "版本 $version"

    override val turnNotifications = "回合完成通知"
    override val turnNotificationsDescription = "应用在后台时，回合结束或出错会发一条通知"
    override val notificationSound = "提示音"
    override val notificationSoundDescription = "收到完成通知时播放系统提示音"

    override val currentHost = "当前主机"
    override val type = "类型"
    override val direct = "直连"
    override val relay = "中继"
    override val address = "地址"
    override val actions = "操作"
    override val reconnect = "重新连接"
    override val reconnectDescription = "断开后重新连接当前主机"
    override val addHost = "添加主机"
    override val addHostDescription = "扫描二维码或粘贴配对链接"
    override val manageHosts = "管理主机"
    override val manageHostsDescription = "切换或移除已保存的主机"
    override val noHostsSaved = "还没有保存的主机。"
    override val remove = "移除"
    override val close = "关闭"
    override val pairingLink = "配对链接"
    override val manual = "手动"
    override val pair = "配对"
    override val add = "添加"
    override val cancel = "取消"
    override val nameOptional = "名称（可选）"
    override val gatewayAddress = "网关地址"
    override val tokenIssueSecret = "Token 签发密钥"
    override val runGatewayHint = "在主机上运行 `nanobot gateway` 并设置 `channels.websocket.tokenIssueSecret`。"
    override val pairIntro = "在电脑上运行 nanobot-link，用它打印的配对链接或二维码进行配对；也可以直接连接网关。"
    override val addHostPairIntro = "在电脑上运行 nanobot-link，使用它打印的链接或二维码。链接中包含中继地址和此设备的密钥。"
    override val currentHostCheck = "当前主机"
    override val gatewayHostDefault = "网关"

    override val refresh = "刷新"
    override val connectToView = "连接网关后可以查看模型与运行环境。"
    override val model = "模型"
    override val provider = "提供方"
    override val modelPreset = "模型预设"
    override val maxOutput = "最大输出"
    override val contextWindow = "上下文窗口"
    override val temperature = "温度"
    override val reasoningEffort = "推理强度"
    override val timezone = "时区"
    override val runtime = "运行环境"
    override val workspace = "工作区"
    override val configFile = "配置文件"
    override val sharedSession = "共享会话"
    override val yes = "是"
    override val no = "否"
    override val version = "版本"
    override val gatewayVersion = "网关版本"
    override val commit = "提交"
    override val restartPending = "待重启"
    override val changesPendingRestart = "有改动待重启"
    override val none = "无"

    override val currentModel = "当前模型"
    override val selectPreset = "选择预设"
    override val newPreset = "新建预设"
    override val presetName = "预设名称"
    override val makeActive = "设为当前"
    override val activePreset = "当前"
    override val defaultPreset = "默认预设"
    override val defaultPresetHint = "默认预设需另存为具名预设后才能编辑最大输出、温度和推理强度。"
    override val settingsSaved = "设置已保存"
    override val couldNotSaveSettings = "无法保存设置"
    override val keepOneModelPreset = "至少需要保留一个启用的模型预设"
    override val modelRequired = "模型不能为空"
    override val providerRequired = "提供方不能为空"
    override val presetNameRequired = "预设名称不能为空"
    override val invalidNumber = "请输入有效的正整数"
    override val invalidTemperature = "温度需在 0 到 2 之间"
    override val reasoningDefault = "默认"
    override val sharedSessionDescription = "所有对话共用同一个会话上下文"
    override fun deletePresetConfirmation(name: String) = "确定删除预设「$name」吗？"

    override val noSkills = "还没有安装技能"
    override val unavailable = "不可用"
    override val noMcpPresets = "还没有 MCP 预设"
    override fun toolCount(count: Int) = "$count 个工具"
    override val notConfigured = "未配置"

    override val mcpOverview = "概览"
    override val mcpTools = "工具"
    override val mcpConnection = "连接"
    override val mcpSearch = "搜索 MCP"
    override val mcpNoMatchingPresets = "没有匹配的 MCP 预设"
    override val mcpOpenDocs = "打开文档"
    override val mcpStatus = "状态"
    override val mcpTransport = "传输方式"
    override val mcpToolScope = "工具范围"
    override val mcpConfigured = "已配置"
    override val mcpNeedsKey = "需要密钥"
    override val mcpNeedsDependency = "缺少依赖"
    override val mcpNeedsAuthorization = "需要授权"
    override val mcpComingSoon = "即将支持"
    override val mcpNotEnabled = "未启用"
    override val mcpEnabled = "已启用"
    override val mcpDisabled = "已停用"
    override val mcpConnected = "已连接"
    override val mcpConnecting = "连接中…"
    override val mcpConnectionFailed = "连接失败"
    override val mcpManage = "管理"
    override val mcpConfigure = "配置"
    override val mcpEnable = "启用"
    override val mcpConnect = "连接"
    override val mcpReconnect = "重新连接"
    override val mcpRemove = "移除"
    override fun mcpRemoveTitle(name: String) = "移除 $name？"
    override val mcpRemoveDescription = "这会从 nanobot 配置中删除此 MCP 服务器，并清除它的授权凭据。"
    override val mcpTestTools = "检测工具"
    override val mcpReloadTools = "重新加载工具"
    override val mcpNoToolsAvailable = "还没有可用的工具"
    override val mcpSearchTools = "搜索工具"
    override val mcpNoMatchingTools = "没有匹配的工具。"
    override val mcpAllTools = "全部"
    override val mcpClearTools = "清空"
    override val mcpApplyChanges = "应用更改"
    override fun mcpSelectedTools(selected: Int, total: Int) = "已选 $selected / $total"
    override val mcpConnectionDetails = "连接详情"
    override val mcpCredentials = "凭据"
    override val mcpConfiguredField = "已配置"
    override val mcpKeepExisting = "留空以保留当前值"
    override val mcpUseProxy = "使用共享代理"
    override val mcpUseProxyDescription = "通过 nanobot 的网络代理访问这个远程 MCP 服务器。"
    override val mcpAuthorization = "授权"
    override val mcpSignIn = "登录授权"
    override val mcpReauthorize = "重新授权"
    override val mcpContinueSignIn = "继续登录"
    override val mcpCallbackUrl = "回调地址"
    override val mcpCallbackHelp = "授权完成后 localhost 页面可能无法打开。请复制浏览器地址栏里的完整 URL 并粘贴到这里。"
    override val mcpFinishSignIn = "完成登录"
    override val mcpCancelSignIn = "取消登录"
    override val mcpSignInPreparing = "正在准备安全登录…"
    override val mcpSignInWaiting = "请在浏览器中完成登录。"
    override val mcpSignInConnecting = "正在完成连接…"
    override val mcpSignInAuthorized = "正在启用工具…"
    override val mcpAddServer = "添加 MCP 服务器"
    override val mcpAddServerDescription = "连接自定义 MCP 服务器，或导入现有配置。"
    override val mcpCustomServer = "自定义"
    override val mcpImportConfig = "导入 mcp.json"
    override val mcpServerName = "服务器名称"
    override val mcpServerUrl = "URL"
    override val mcpCommand = "命令"
    override val mcpArguments = "参数（JSON）"
    override val mcpEnvironment = "环境变量（JSON）"
    override val mcpHeaders = "请求头（JSON）"
    override val mcpHeadersHelp = "填写这个服务器需要的请求头。"
    override val mcpAdvancedOptions = "高级选项"
    override val mcpHideAdvanced = "隐藏高级选项"
    override val mcpToolTimeout = "工具超时（秒）"
    override val mcpSaveServer = "保存 MCP 服务器"
    override val mcpImport = "导入"
    override val mcpConfigPlaceholder = "{\"mcpServers\":{\"docs\":{\"command\":\"npx\",\"args\":[\"-y\",\"docs-mcp\"]}}}"
    override val mcpAuthNone = "无"
    override val mcpAuthHeaders = "请求头"
    override val mcpPluginManageOnly = "Agent 插件只能启用或停用。"
    override val mcpRestartPending = "MCP 配置已保存，可能需要重启 nanobot 才能生效。"
    override val mcpOAuthConnected = "MCP 授权完成并已连接"
    override val mcpOAuthCancelled = "已取消 MCP 授权"
    override val mcpOAuthFailed = "MCP 授权失败"
    override val mcpOAuthCallbackFailed = "无法完成 MCP 授权"
    override val mcpSettingsUpdated = "已更新 MCP 设置"
    override val noCliApps = "还没有可用的命令行应用"
    override val installed = "已安装"
    override val installable = "可安装"
    override val noAutomations = "还没有自动化任务"
    override val paused = "已暂停"
    override val run = "运行"
    override val scheduled = "定时"
    override val periodic = "周期"
    override val once = "单次"
    override val localTrigger = "本地触发"
    override fun everyHours(hours: Long) = "每 $hours 小时"
    override fun everyMinutes(minutes: Long) = "每 $minutes 分钟"
    override fun lastStatus(status: String) = "上次 $status"

    override val automationTaskInfo = "任务信息"
    override val automationSchedule = "计划"
    override val automationNextRun = "下次运行"
    override val automationLastRun = "上次运行"
    override val automationLastStatus = "上次状态"
    override val automationTimezone = "时区"
    override val automationOneTime = "一次性"
    override val automationOneTimeHint = "运行后自动移除"
    override val automationCreated = "创建时间"
    override val automationUpdated = "更新时间"
    override val automationIdentifier = "ID"
    override val automationInstructions = "任务说明"
    override val automationCommand = "命令"
    override val automationLinkedChat = "关联会话"
    override val automationLastError = "上次错误"
    override val automationRecentRuns = "最近运行"
    override val automationNeverRun = "尚未运行"
    override val automationNoNextRun = "没有后续运行计划"
    override val automationWaitingForTrigger = "等待触发"
    override val automationRunning = "运行中"
    override val automationCompleted = "已完成"
    override val automationFailed = "失败"
    override val automationSkipped = "已跳过"
    override val automationSystem = "系统"
    override val automationSystemHint = "由 nanobot 管理，在这里只能查看。"
    override val automationNotFound = "找不到这个自动化"
    override val automationEnable = "启用自动化"
    override val automationDisable = "停用自动化"
    override val automationRunNow = "立即运行"
    override val automationDelete = "删除自动化"
    override val automationDeleteTitle = "删除这个自动化？"
    override fun automationDeleteDescription(name: String) =
        "将「$name」从自动化中删除。过去的对话消息仍会保留在会话中。"
    override fun automationAt(time: String) = "在 $time"
    override fun automationEvery(millis: Long): String {
        val (value, unit) = automationIntervalParts(millis)
        val label = when (unit) {
            AutomationIntervalUnit.Day -> "天"
            AutomationIntervalUnit.Hour -> "小时"
            AutomationIntervalUnit.Minute -> "分钟"
            AutomationIntervalUnit.Second -> "秒"
        }
        return "每 $value $label"
    }
    override fun automationCronSchedule(expr: String) = "Cron $expr"
    override fun automationRelative(deltaMs: Long): String {
        val future = deltaMs >= 0
        val abs = kotlin.math.abs(deltaMs)
        return when {
            abs < 60_000L -> if (future) "稍后" else "刚刚"
            abs < 3_600_000L -> {
                val minutes = abs / 60_000L
                if (future) "$minutes 分钟后" else "$minutes 分钟前"
            }
            abs < 86_400_000L -> {
                val hours = abs / 3_600_000L
                if (future) "$hours 小时后" else "$hours 小时前"
            }
            else -> {
                val days = abs / 86_400_000L
                if (future) "$days 天后" else "$days 天前"
            }
        }
    }
    override fun automationDuration(millis: Long): String = when {
        millis < 1_000L -> "$millis 毫秒"
        millis < 60_000L -> "${(millis + 500L) / 1_000L} 秒"
        millis < 3_600_000L -> "${(millis + 30_000L) / 60_000L} 分钟"
        else -> "${(millis + 1_800_000L) / 3_600_000L} 小时"
    }

    override val automationEdit = "编辑"
    override val automationEditTitle = "编辑自动化"
    override val automationName = "名称"
    override val automationMessage = "任务说明"
    override val automationScheduleType = "计划类型"
    override val automationEveryLabel = "间隔"
    override val automationUnit = "单位"
    override val automationCronExpression = "Cron 表达式"
    override val automationRunAt = "运行时间"
    override val automationChooseTime = "选择时间"
    override val automationSave = "保存"
    override val automationNameRequired = "请输入任务名称。"
    override val automationMessageRequired = "请输入任务说明。"
    override val automationIntervalRequired = "请输入大于 0 的整数间隔。"
    override val automationCronRequired = "请输入 Cron 表达式。"
    override val automationCronInvalid = "Cron 表达式无效。"
    override val automationFutureRequired = "请选择未来的时间。"
    override val automationSaved = "自动化已保存"
    override val automationScheduleEveryType = "间隔"
    override val automationScheduleCronType = "Cron"
    override val automationScheduleAtType = "单次"
    override val automationUnitSecond = "秒"
    override val automationUnitMinute = "分钟"
    override val automationUnitHour = "小时"
    override val automationUnitDay = "天"
    override val automationChangeChat = "更换会话"
    override val automationChatTitle = "更换关联会话"
    override val automationCurrentChat = "当前会话"
    override val automationNewChat = "新会话"
    override val automationChatHint = "自动化的后续结果将发送到新会话；过去的消息仍留在原会话。"
    override val automationChatMessage = "任务说明"
    override val automationChatConfirm = "确认更换"
    override val automationChatsEmpty = "没有可用的会话"
    override val automationChatsLoadFailed = "无法加载可更换的会话"
    override val automationChatConflict = "会话绑定已在别处修改，请重新加载后再试。"
    override val automationChatBusy = "任务正在运行，暂时无法更换会话。"
    override val automationChatUnavailable = "这个会话当前不可用。"
    override val automationChatRevision = "会话列表需要刷新，请重新加载。"
    override val automationChatWeb = "Web"
    override val automationRetry = "重试"
    override fun automationChatChanged(title: String) =
        if (title.isBlank()) "已更换关联会话" else "已更换到「$title」"

    override val searchSkills = "搜索技能"
    override val closeSearch = "关闭搜索"
    override val noMatchingSkills = "没有匹配的技能"
    override val skillEnabled = "已启用"
    override val skillDisabled = "已停用"
    override val needsSetup = "需要配置"
    override val sourceCustom = "自定义"
    override val sourceBuiltin = "内置"
    override val useSkill = "使用此技能"
    override val skillInstructions = "技能指令"
    override val showMore = "展开"
    override val showLess = "收起"
    override val setupRequired = "需要配置"
    override val setupDescription = "在运行 nanobot 的机器上安装缺少的依赖，然后重新检查。"
    override val missingCommands = "缺少命令行工具"
    override val missingEnvironment = "缺少环境变量"
    override val checkAgain = "重新检查"
    override val noSkillInstructions = "没有可用的技能文件内容。"
    override val loadingSkillDetail = "正在加载技能详情…"
    override val skillNotFound = "这个技能不存在"
    override val deleteSkill = "删除技能"
    override val deleteSkillDescription = "从当前工作区移除此技能。"
    override val deleteSkillConfirmDescription = "这会从当前工作区移除技能文件，且无法撤销。"
    override fun deleteSkillTitle(name: String) = "删除 $name？"
    override fun skillDeletedNamed(name: String) = "已删除技能 $name"
    override val couldNotLoadSkillDetail = "无法加载技能详情"
    override val couldNotDeleteSkill = "无法删除技能"

    override val client = "客户端"
    override val protocol = "协议"
    override val storage = "存储"
    override val storedOnDeviceOnly = "仅保存在本机"
    override val updates = "更新"
    override val checkForUpdates = "检查更新"
    override val checkingForUpdates = "正在检查更新…"
    override val upToDate = "已是最新版本"
    override fun updateAvailable(version: String) = "发现新版本 $version"
    override val downloadUpdate = "下载更新"
    override val noPublishedRelease = "暂无发布版本"
    override val couldNotCheckUpdates = "无法检查更新"

    override val setupIntro = "在电脑上运行 nanobot-link，用它打印的配对链接或二维码进行配对；也可以直接连接网关。"
    override val paste = "粘贴"
    override val scanQr = "扫描二维码"
    override val orConnectDirectly = "或直接连接"
    override val cleartextHint = "局域网开发允许明文 HTTP；任何可从外网访问的部署请使用 TLS。"
    override val connect = "连接"

    override val sessions = "会话"
    override val new = "新建"
    override val newChat = "新对话"
    override val myMessages = "我的消息"
    override val searchConversations = "搜索对话"
    override val clearSearch = "清除搜索"
    override val jumpToLatest = "跳到最新"
    override val noMatchingConversations = "没有匹配的对话"
    override val mePrefix = "我："
    override val assistantPrefix = "nanobot："
    override val hosts = "主机"
    override val addWorkspace = "添加工作区"
    override val search = "搜索"
    override val showArchived = "显示归档"
    override val hideArchived = "隐藏归档"
    override val offline = "离线"
    override val connecting = "连接中…"
    override val connected = "已连接"
    override fun reconnecting(attempt: Int) = "重新连接中（$attempt）"
    override val relayStopped = "中继已停止"
    override val relayConnecting = "中继连接中…"
    override fun relayReconnecting(attempt: Int) = "中继重新连接中（$attempt）"
    override fun relayFailed(reason: String) = "中继：$reason"

    override val pin = "置顶"
    override val unpin = "取消置顶"
    override val rename = "重命名"
    override val archive = "归档"
    override val unarchive = "取消归档"
    override val select = "选择"
    override val delete = "删除"
    override val running = "运行中"
    override val unread = "未读"
    override val topics = "话题"
    override val chats = "对话"
    override val pinned = "置顶"
    override val today = "今天"
    override val yesterday = "昨天"
    override val earlier = "更早"
    override val archived = "已归档"
    override val emptyChats = "还没有保存的会话"
    override fun actionsFor(title: String) = "“$title”的会话操作"
    override fun selectedCount(count: Int) = "已选择 $count 项"
    override val deleteChatTitle = "删除这个话题？"
    override fun deleteChatsTitle(count: Int) = "删除这 $count 个对话？"
    override val deleteDescription = "此操作无法撤销。"
    override val deleteAutomationsNote = "关联的自动任务也会一并删除。"
    override val deleteWithAutomations = "删除（含自动任务）"
    override val archiveChatTitle = "归档此话题？"
    override fun archiveChatsTitle(count: Int) = "归档 $count 个话题？"
    override val archiveDescription = "聊天记录会保留，可随时取消归档。"
    override val unarchiveChatTitle = "取消归档此话题？"
    override fun unarchiveChatsTitle(count: Int) = "取消归档 $count 个话题？"
    override val unarchiveDescription = "这些话题将重新显示在侧栏中。"
    override val renameChatTitle = "重命名会话"
    override val renameChatPlaceholder = "会话名称"
    override val save = "保存"
    override fun forkChatTitle(title: String) = "分叉：$title"

    override val emptyConversation = "这个对话还是空的，问点什么开始吧。"
    override val createChat = "新建一个对话开始吧。"
    override val composerHint = "输入 / 命令，\$ 技能，@ 工具"
    override val copied = "已复制"
    override val copy = "复制"
    override fun attachmentCount(count: Int) = "$count 个附件"
    override val sending = "发送中…"
    override val notConfirmed = "未确认"
    override val stopped = "已停止"
    override val forkFromHere = "从这里分叉"
    override val attach = "添加附件"
    override val pause = "暂停"
    override val send = "发送"
    override val addAttachment = "添加附件"
    override val photos = "照片"
    override val chooseFromGallery = "从图库选择"
    override val files = "文件"
    override val browseFiles = "浏览文件"
    override val uploading = "上传中…"
    override val uploadFailed = "上传失败"
    override fun uploadingPercent(percent: Int) = "上传中 $percent%"
    override val removeAttachment = "移除附件"
    override val attachmentPreview = "附件预览"
    override val closePreview = "关闭预览"
    override val file = "文件"
    override val fullAccess = "完全访问"
    override val defaultAccess = "默认权限"
    override val defaultAccessDescription = "工具只在当前工作区内运行"
    override val fullAccessDescription = "工具可以读写工作区之外的文件"
    override val attachmentPlaceholder = "（附件）"
    override val camera = "拍照"
    override val takePhoto = "使用相机拍摄"
    override val switchModel = "切换模型"
    override val defaultModelPreset = "默认"
    override val noModelPresets = "暂无可用模型"
    override val contextUsageUnknown = "上下文用量未知"
    override fun contextUsage(percent: Int) = "上下文已使用 $percent%"
    override val couldNotSwitchModel = "无法切换模型"
    override val couldNotOpenCamera = "无法打开相机"

    override val backToSavedWorkspaces = "返回已保存的工作区"
    override val favorites = "收藏"
    override val recent = "最近"
    override val noSavedWorkspaces = "这台终端上还没有收藏或最近使用的工作区。"
    override val browseFolders = "浏览文件夹…"
    override val upOneLevel = "上一级"
    override val filterFolders = "筛选文件夹"
    override val noSubfolders = "这个文件夹里没有子文件夹。"
    override val noMatchingFolders = "没有匹配的文件夹。"
    override val showingFirst500 = "只显示了前 500 个文件夹。"
    override val cannotReadFolder = "无法读取这个文件夹"

    override val searchMessages = "搜索消息"
    override fun messageCount(count: Int) = "$count 条消息"
    override fun matchCount(count: Int) = "$count 条匹配"
    override val noMessagesYet = "这个对话里你还没有发送过消息。"
    override val noMessagesMatch = "没有匹配的消息。"

    override val thinkingStopped = "思考已停止"
    override val thinking = "思考中…"
    override val thought = "思考"
    override val working = "处理中…"
    override fun stepsLabel(count: Int) = "· $count 步"
    override fun reasoningDescription(label: String, count: Int, expanded: Boolean): String {
        val suffix = if (count > 1) "，共 $count 步" else ""
        return "$label$suffix，${if (expanded) "收起" else "展开"}思考"
    }

    override val closeScanner = "关闭扫描"
    override val scanQrCode = "扫描二维码"
    override val alignQrInFrame = "将二维码对准框内"
    override val turnOnTorch = "打开手电筒"
    override val turnOffTorch = "关闭手电筒"
    override val torch = "手电筒"
    override val torchOn = "手电筒已开"

    override val connectedToGateway = "已连接到网关"
    override val turnCompleted = "回合已完成"
    override val turnFailed = "回合失败"
    override val turnCancelled = "回合已取消"
    override val gatewayError = "网关错误"
    override val nanobotError = "nanobot 错误"
    override val channelGatewayConnection = "网关连接"
    override val channelTurnResults = "回合结果"

    override val pixelCat = "像素猫"
    override val tapMe = "tap me!"
    override val settingsContentDescription = "设置"

    override val enterGatewayAddress = "请输入网关地址"
    override val invalidPairingLink = "配对链接无效"
    override val couldNotSaveHost = "无法保存这台主机"
    override val couldNotConnectHost = "无法连接这台主机"
    override val attachmentUploadFailed = "附件上传失败"
    override fun attachmentTooLarge(size: String, limit: String) =
        "附件有 $size，超过网关 $limit 的单文件上限"
    override fun attachmentTooLargeAfterCompression(size: String, limit: String) =
        "压缩后仍有 $size，超过网关 $limit 的单文件上限"
    override val couldNotLoadGatewaySettings = "无法加载网关设置"
    override val notConnectedToGateway = "未连接网关"
    override val couldNotUpdateSkills = "无法更新技能"
    override val couldNotUpdateMcp = "无法更新 MCP 预设"
    override val couldNotUpdateAutomation = "无法更新自动化"
    override val couldNotRunAutomation = "无法运行自动化"
    override val couldNotDeleteAutomation = "无法删除自动化"
    override val couldNotLoadAutomationChats = "无法加载可更换的会话"
    override val couldNotChangeAutomationChat = "无法更换关联会话"
    override val couldNotSend = "发送失败"
    override val couldNotCreateChat = "无法创建对话"
    override val couldNotDelete = "删除失败"
    override val couldNotUpdateSidebar = "侧边栏更新失败"
    override val sidebarNotLoaded = "侧边栏状态尚未加载"
    override val couldNotForkChat = "无法分叉这个对话"
    override val couldNotOpenWorkspace = "无法打开这个工作区"
    override val startedAutomation = "已开始运行自动化"
    override fun startedAutomationNamed(name: String) = "已开始运行：$name"

    override val relayUnreachable = "无法连接中继"
    override val relayConnectionFailed = "中继连接失败"
    override val relayRefusedPhone = "中继拒绝了此设备"
    override val relayClosedConnection = "中继关闭了连接"
    override val relayDidNotAcceptPhone = "中继没有接受此设备"
    override val relayInvalidFrame = "中继发送了无效数据"
    override val pcClosedStream = "电脑关闭了数据流"
    override val relayClosedDataChannel = "中继关闭了数据通道"
    override val relayDataChannelFailed = "中继数据通道失败"
    override val relayDidNotOpenDataChannel = "中继没有打开数据通道"
    override val relayNotConnected = "中继未连接"
    override val pcDidNotAcceptStream = "电脑没有接受数据流"
    override val pcSentUndecryptableFrame = "电脑发送了无法解密的数据"
    override val phoneTooSlow = "手机处理过慢"
    override val relayTooSlow = "中继响应过慢"
    override val phoneClosedStream = "手机关闭了数据流"

    override fun accentName(accent: AccentTheme) = when (accent) {
        AccentTheme.Mist -> "雾蓝"
        AccentTheme.Daiqing -> "黛青"
        AccentTheme.Dusk -> "暮紫"
        AccentTheme.Turquoise -> "松石"
        AccentTheme.Amber -> "琥珀"
        AccentTheme.Graphite -> "石墨"
        AccentTheme.Custom -> "自定义"
        AccentTheme.Wallpaper -> "跟随壁纸"
    }

    override fun themeModeName(mode: ThemeMode) = when (mode) {
        ThemeMode.System -> followSystem
        ThemeMode.Light -> light
        ThemeMode.Dark -> dark
    }

    override fun fontSizeName(size: AppFontSize) = when (size) {
        AppFontSize.Small -> textSizeSmall
        AppFontSize.Standard -> textSizeStandard
        AppFontSize.Large -> textSizeLarge
        AppFontSize.ExtraLarge -> textSizeExtraLarge
    }
}

internal object EnglishStrings : AppStrings {
    override val settings = "Settings"
    override val appearance = "Appearance"
    override val notifications = "Notifications"
    override val hostsAndConnection = "Hosts & connection"
    override val gateway = "Gateway"
    override val skills = "Skills"
    override val mcpPresets = "MCP presets"
    override val cliApps = "CLI apps"
    override val automations = "Automations"
    override val about = "About"
    override val general = "General"
    override val connection = "Connection"
    override val capabilities = "Capabilities"
    override val other = "Other"
    override val localPrefsAndGatewayInfo = "Local preferences and gateway info"
    override val closeSettings = "Close settings"
    override val back = "Back"
    override val dismissMessage = "Dismiss message"
    override val theme = "Theme"
    override val themeDescription = "Choose the app's light or dark appearance."
    override val light = "Light"
    override val dark = "Dark"
    override val followSystem = "Follow system"
    override val colorScheme = "Color"
    override val customColor = "Custom color"
    override val dragToChooseHue = "Drag to choose a hue."
    override val followWallpaper = "Match wallpaper"
    override val followWallpaperDescription = "Use colors from the system wallpaper (Android 12+)"
    override val appearanceNote = "Appearance settings are stored on this device only; gateway configuration is unchanged."
    override val textSize = "Text size"
    override val textSizeDescription = "Adjust text size in the app."
    override val textSizeSmall = "Small"
    override val textSizeStandard = "Standard"
    override val textSizeLarge = "Large"
    override val textSizeExtraLarge = "Extra large"
    override val language = "Language"
    override val languageDescription = "Choose the language used by the app."

    override val noHost = "No host"
    override val tapForGatewayDetails = "Tap for gateway details"
    override val tapForModelDetails = "Tap for model settings"
    override val turnNotificationsOn = "Turn notifications · On"
    override val turnNotificationsOff = "Turn notifications · Off"
    override fun countEnabled(total: Int, count: Int) = "$total total · $count enabled"
    override fun countConfigured(total: Int, count: Int) = "$total total · $count configured"
    override fun countInstalled(total: Int, count: Int) = "$total total · $count installed"
    override fun versionLabel(version: String) = "Version $version"

    override val turnNotifications = "Turn notifications"
    override val turnNotificationsDescription = "Notify when a turn finishes or fails while the app is in the background"
    override val notificationSound = "Sound"
    override val notificationSoundDescription = "Play the system sound for completion notifications"

    override val currentHost = "Current host"
    override val type = "Type"
    override val direct = "Direct"
    override val relay = "Relay"
    override val address = "Address"
    override val actions = "Actions"
    override val reconnect = "Reconnect"
    override val reconnectDescription = "Reconnect the current host after a disconnect"
    override val addHost = "Add host"
    override val addHostDescription = "Scan a QR code or paste a pairing link"
    override val manageHosts = "Manage hosts"
    override val manageHostsDescription = "Switch between or remove saved hosts"
    override val noHostsSaved = "No hosts saved yet."
    override val remove = "Remove"
    override val close = "Close"
    override val pairingLink = "Pairing link"
    override val manual = "Manual"
    override val pair = "Pair"
    override val add = "Add"
    override val cancel = "Cancel"
    override val nameOptional = "Name (optional)"
    override val gatewayAddress = "Gateway address"
    override val tokenIssueSecret = "Token-issue secret"
    override val runGatewayHint = "Run `nanobot gateway` on the host and set `channels.websocket.tokenIssueSecret`."
    override val pairIntro = "Pair with the link or QR code printed by the nanobot sidecar on your PC, or connect to a gateway directly."
    override val addHostPairIntro = "Run the nanobot sidecar on your PC and use the link or QR code it prints. The link contains the relay address and this device's key."
    override val currentHostCheck = "Current host"
    override val gatewayHostDefault = "Gateway"

    override val refresh = "Refresh"
    override val connectToView = "Connect to a gateway to see the model and runtime."
    override val model = "Model"
    override val provider = "Provider"
    override val modelPreset = "Model preset"
    override val maxOutput = "Max output"
    override val contextWindow = "Context window"
    override val temperature = "Temperature"
    override val reasoningEffort = "Reasoning effort"
    override val timezone = "Time zone"
    override val runtime = "Runtime"
    override val workspace = "Workspace"
    override val configFile = "Config file"
    override val sharedSession = "Shared session"
    override val yes = "Yes"
    override val no = "No"
    override val version = "Version"
    override val gatewayVersion = "Gateway version"
    override val commit = "Commit"
    override val restartPending = "Restart"
    override val changesPendingRestart = "Changes pending restart"
    override val none = "None"

    override val currentModel = "Current model"
    override val selectPreset = "Select preset"
    override val newPreset = "New preset"
    override val presetName = "Preset name"
    override val makeActive = "Make active"
    override val activePreset = "Active"
    override val defaultPreset = "Default preset"
    override val defaultPresetHint = "Save the default preset as a named preset to edit max output, temperature, and reasoning effort."
    override val settingsSaved = "Settings saved"
    override val couldNotSaveSettings = "Could not save settings"
    override val keepOneModelPreset = "Keep at least one model preset enabled"
    override val modelRequired = "Model is required"
    override val providerRequired = "Provider is required"
    override val presetNameRequired = "Preset name is required"
    override val invalidNumber = "Enter a positive whole number"
    override val invalidTemperature = "Temperature must be between 0 and 2"
    override val reasoningDefault = "Default"
    override val sharedSessionDescription = "Share one session context across conversations"
    override fun deletePresetConfirmation(name: String) = "Delete preset \"$name\"?"

    override val noSkills = "No skills installed yet"
    override val unavailable = "Unavailable"
    override val noMcpPresets = "No MCP presets yet"
    override fun toolCount(count: Int) = if (count == 1) "1 tool" else "$count tools"
    override val notConfigured = "Not configured"

    override val mcpOverview = "Overview"
    override val mcpTools = "Tools"
    override val mcpConnection = "Connection"
    override val mcpSearch = "Search MCP"
    override val mcpNoMatchingPresets = "No MCP presets match this search"
    override val mcpOpenDocs = "Open docs"
    override val mcpStatus = "Status"
    override val mcpTransport = "Transport"
    override val mcpToolScope = "Tools"
    override val mcpConfigured = "Configured"
    override val mcpNeedsKey = "Needs key"
    override val mcpNeedsDependency = "Needs dependency"
    override val mcpNeedsAuthorization = "Needs sign-in"
    override val mcpComingSoon = "Coming soon"
    override val mcpNotEnabled = "Not enabled"
    override val mcpEnabled = "Enabled"
    override val mcpDisabled = "Disabled"
    override val mcpConnected = "Connected"
    override val mcpConnecting = "Connecting…"
    override val mcpConnectionFailed = "Connection failed"
    override val mcpManage = "Manage"
    override val mcpConfigure = "Configure"
    override val mcpEnable = "Enable"
    override val mcpConnect = "Connect"
    override val mcpReconnect = "Reconnect"
    override val mcpRemove = "Remove"
    override fun mcpRemoveTitle(name: String) = "Remove $name?"
    override val mcpRemoveDescription =
        "This removes the MCP server from the nanobot config and clears its authorization credentials."
    override val mcpTestTools = "Inspect tools"
    override val mcpReloadTools = "Reload tools"
    override val mcpNoToolsAvailable = "No tools available yet"
    override val mcpSearchTools = "Search tools"
    override val mcpNoMatchingTools = "No tools match this search."
    override val mcpAllTools = "All"
    override val mcpClearTools = "None"
    override val mcpApplyChanges = "Apply changes"
    override fun mcpSelectedTools(selected: Int, total: Int) = "$selected of $total selected"
    override val mcpConnectionDetails = "Connection details"
    override val mcpCredentials = "Credentials"
    override val mcpConfiguredField = "Configured"
    override val mcpKeepExisting = "Leave blank to keep the current value"
    override val mcpUseProxy = "Use the shared proxy"
    override val mcpUseProxyDescription =
        "Reach this remote MCP server through nanobot's network proxy."
    override val mcpAuthorization = "Authorization"
    override val mcpSignIn = "Sign in"
    override val mcpReauthorize = "Reauthorize"
    override val mcpContinueSignIn = "Continue sign-in"
    override val mcpCallbackUrl = "Callback URL"
    override val mcpCallbackHelp =
        "After approving access, the localhost page may not load. Copy the full URL from the browser address bar and paste it here."
    override val mcpFinishSignIn = "Finish sign-in"
    override val mcpCancelSignIn = "Cancel sign-in"
    override val mcpSignInPreparing = "Preparing secure sign-in…"
    override val mcpSignInWaiting = "Complete sign-in in the browser."
    override val mcpSignInConnecting = "Finishing connection…"
    override val mcpSignInAuthorized = "Activating tools…"
    override val mcpAddServer = "Add MCP server"
    override val mcpAddServerDescription =
        "Connect a custom MCP server or import an existing configuration."
    override val mcpCustomServer = "Custom"
    override val mcpImportConfig = "Import mcp.json"
    override val mcpServerName = "Server name"
    override val mcpServerUrl = "URL"
    override val mcpCommand = "Command"
    override val mcpArguments = "Arguments (JSON)"
    override val mcpEnvironment = "Environment variables (JSON)"
    override val mcpHeaders = "Headers (JSON)"
    override val mcpHeadersHelp = "Add the request headers used by this server."
    override val mcpAdvancedOptions = "Advanced options"
    override val mcpHideAdvanced = "Hide advanced options"
    override val mcpToolTimeout = "Tool timeout (seconds)"
    override val mcpSaveServer = "Save MCP server"
    override val mcpImport = "Import"
    override val mcpConfigPlaceholder =
        "{\"mcpServers\":{\"docs\":{\"command\":\"npx\",\"args\":[\"-y\",\"docs-mcp\"]}}}"
    override val mcpAuthNone = "None"
    override val mcpAuthHeaders = "Headers"
    override val mcpPluginManageOnly = "Agent Plugins support enable and disable only."
    override val mcpRestartPending =
        "MCP changes were saved; restart nanobot if the new connection does not appear."
    override val mcpOAuthConnected = "MCP authorization completed"
    override val mcpOAuthCancelled = "MCP authorization cancelled"
    override val mcpOAuthFailed = "MCP authorization failed"
    override val mcpOAuthCallbackFailed = "Could not complete MCP authorization"
    override val mcpSettingsUpdated = "MCP settings updated"
    override val noCliApps = "No CLI apps available"
    override val installed = "Installed"
    override val installable = "Available"
    override val noAutomations = "No automations yet"
    override val paused = "Paused"
    override val run = "Run"
    override val scheduled = "Scheduled"
    override val periodic = "Periodic"
    override val once = "Once"
    override val localTrigger = "Local trigger"
    override fun everyHours(hours: Long) = if (hours == 1L) "Every hour" else "Every $hours hours"
    override fun everyMinutes(minutes: Long) = if (minutes == 1L) "Every minute" else "Every $minutes minutes"
    override fun lastStatus(status: String) = "Last: $status"

    override val automationTaskInfo = "Task information"
    override val automationSchedule = "Schedule"
    override val automationNextRun = "Next run"
    override val automationLastRun = "Last run"
    override val automationLastStatus = "Last status"
    override val automationTimezone = "Timezone"
    override val automationOneTime = "One-time"
    override val automationOneTimeHint = "Removed after running"
    override val automationCreated = "Created"
    override val automationUpdated = "Updated"
    override val automationIdentifier = "ID"
    override val automationInstructions = "Instructions"
    override val automationCommand = "Command"
    override val automationLinkedChat = "Linked chat"
    override val automationLastError = "Last error"
    override val automationRecentRuns = "Recent runs"
    override val automationNeverRun = "Not run yet"
    override val automationNoNextRun = "No next run"
    override val automationWaitingForTrigger = "Waiting for trigger"
    override val automationRunning = "Running"
    override val automationCompleted = "Completed"
    override val automationFailed = "Failed"
    override val automationSkipped = "Skipped"
    override val automationSystem = "System"
    override val automationSystemHint = "Managed by nanobot; read-only here."
    override val automationNotFound = "Automation not found"
    override val automationEnable = "Enable automation"
    override val automationDisable = "Disable automation"
    override val automationRunNow = "Run now"
    override val automationDelete = "Delete automation"
    override val automationDeleteTitle = "Delete this automation?"
    override fun automationDeleteDescription(name: String) =
        "This removes \"$name\" from automations. Past chat messages stay in the session."
    override fun automationAt(time: String) = "At $time"
    override fun automationEvery(millis: Long): String {
        val (value, unit) = automationIntervalParts(millis)
        val label = when (unit) {
            AutomationIntervalUnit.Day -> countLabel(value, "day", "days")
            AutomationIntervalUnit.Hour -> countLabel(value, "hour", "hours")
            AutomationIntervalUnit.Minute -> countLabel(value, "minute", "minutes")
            AutomationIntervalUnit.Second -> countLabel(value, "second", "seconds")
        }
        return "Every $label"
    }
    override fun automationCronSchedule(expr: String) = "Cron $expr"
    override fun automationRelative(deltaMs: Long): String {
        val future = deltaMs >= 0
        val abs = kotlin.math.abs(deltaMs)
        return when {
            abs < 60_000L -> if (future) "in a moment" else "just now"
            abs < 3_600_000L -> {
                val label = countLabel(abs / 60_000L, "minute", "minutes")
                if (future) "in $label" else "$label ago"
            }
            abs < 86_400_000L -> {
                val label = countLabel(abs / 3_600_000L, "hour", "hours")
                if (future) "in $label" else "$label ago"
            }
            else -> {
                val label = countLabel(abs / 86_400_000L, "day", "days")
                if (future) "in $label" else "$label ago"
            }
        }
    }
    override fun automationDuration(millis: Long): String = when {
        millis < 1_000L -> "$millis ms"
        millis < 60_000L -> "${(millis + 500L) / 1_000L} s"
        millis < 3_600_000L -> "${(millis + 30_000L) / 60_000L} min"
        else -> "${(millis + 1_800_000L) / 3_600_000L} h"
    }

    override val automationEdit = "Edit"
    override val automationEditTitle = "Edit automation"
    override val automationName = "Name"
    override val automationMessage = "Instructions"
    override val automationScheduleType = "Schedule type"
    override val automationEveryLabel = "Every"
    override val automationUnit = "Unit"
    override val automationCronExpression = "Cron expression"
    override val automationRunAt = "Run time"
    override val automationChooseTime = "Choose time"
    override val automationSave = "Save"
    override val automationNameRequired = "Enter a task name."
    override val automationMessageRequired = "Enter the task instructions."
    override val automationIntervalRequired = "Enter a positive whole number for the interval."
    override val automationCronRequired = "Enter a Cron expression."
    override val automationCronInvalid = "The Cron expression is invalid."
    override val automationFutureRequired = "Choose a time in the future."
    override val automationSaved = "Automation saved"
    override val automationScheduleEveryType = "Interval"
    override val automationScheduleCronType = "Cron"
    override val automationScheduleAtType = "Once"
    override val automationUnitSecond = "Sec"
    override val automationUnitMinute = "Min"
    override val automationUnitHour = "Hour"
    override val automationUnitDay = "Day"
    override val automationChangeChat = "Change chat"
    override val automationChatTitle = "Change linked chat"
    override val automationCurrentChat = "Current chat"
    override val automationNewChat = "New chat"
    override val automationChatHint =
        "Future results go to the new chat. Past messages stay in the original conversation."
    override val automationChatMessage = "Instructions"
    override val automationChatConfirm = "Change chat"
    override val automationChatsEmpty = "No chats available"
    override val automationChatsLoadFailed = "Could not load chats"
    override val automationChatConflict = "The chat binding changed elsewhere. Reload and try again."
    override val automationChatBusy = "The task is running; its chat cannot be changed right now."
    override val automationChatUnavailable = "That chat is unavailable."
    override val automationChatRevision = "The chat list is out of date. Reload it first."
    override val automationChatWeb = "Web"
    override val automationRetry = "Retry"
    override fun automationChatChanged(title: String) =
        if (title.isBlank()) "Linked chat changed" else "Linked chat changed to \"$title\""

    override val searchSkills = "Search skills"
    override val closeSearch = "Close search"
    override val noMatchingSkills = "No matching skills"
    override val skillEnabled = "Enabled"
    override val skillDisabled = "Disabled"
    override val needsSetup = "Needs setup"
    override val sourceCustom = "Custom"
    override val sourceBuiltin = "Built-in"
    override val useSkill = "Use this skill"
    override val skillInstructions = "Skill instructions"
    override val showMore = "Show more"
    override val showLess = "Show less"
    override val setupRequired = "Setup required"
    override val setupDescription = "Install the missing dependency on the machine running nanobot, then check again."
    override val missingCommands = "Missing command-line tools"
    override val missingEnvironment = "Missing environment variables"
    override val checkAgain = "Check again"
    override val noSkillInstructions = "No skill file content available."
    override val loadingSkillDetail = "Loading skill details…"
    override val skillNotFound = "This skill is not available"
    override val deleteSkill = "Delete skill"
    override val deleteSkillDescription = "Remove this skill from the current workspace."
    override val deleteSkillConfirmDescription = "This removes the skill files from the current workspace. This action cannot be undone."
    override fun deleteSkillTitle(name: String) = "Delete $name?"
    override fun skillDeletedNamed(name: String) = "Deleted skill $name"
    override val couldNotLoadSkillDetail = "could not load skill details"
    override val couldNotDeleteSkill = "could not delete the skill"

    override val client = "Client"
    override val protocol = "Protocol"
    override val storage = "Storage"
    override val storedOnDeviceOnly = "Stored on this device only"
    override val updates = "Updates"
    override val checkForUpdates = "Check for updates"
    override val checkingForUpdates = "Checking for updates…"
    override val upToDate = "You're on the latest version"
    override fun updateAvailable(version: String) = "Version $version is available"
    override val downloadUpdate = "Download update"
    override val noPublishedRelease = "No published release yet"
    override val couldNotCheckUpdates = "Couldn't check for updates"

    override val setupIntro = "Pair with the link or QR code printed by the nanobot sidecar on your PC, or connect to a gateway directly."
    override val paste = "Paste"
    override val scanQr = "Scan QR"
    override val orConnectDirectly = "Or connect directly"
    override val cleartextHint = "Cleartext HTTP is allowed for LAN development. Use TLS for anything reachable outside your network."
    override val connect = "Connect"

    override val sessions = "Sessions"
    override val new = "New"
    override val newChat = "New chat"
    override val myMessages = "My messages"
    override val searchConversations = "Search conversations"
    override val clearSearch = "Clear search"
    override val jumpToLatest = "Jump to latest"
    override val noMatchingConversations = "No matching conversations"
    override val mePrefix = "Me: "
    override val assistantPrefix = "nanobot: "
    override val hosts = "Hosts"
    override val addWorkspace = "Add workspace"
    override val search = "Search"
    override val showArchived = "Show archived"
    override val hideArchived = "Hide archived"
    override val offline = "offline"
    override val connecting = "connecting…"
    override val connected = "connected"
    override fun reconnecting(attempt: Int) = "reconnecting ($attempt)"
    override val relayStopped = "relay stopped"
    override val relayConnecting = "relay connecting…"
    override fun relayReconnecting(attempt: Int) = "relay reconnecting ($attempt)"
    override fun relayFailed(reason: String) = "relay: $reason"

    override val pin = "Pin"
    override val unpin = "Unpin"
    override val rename = "Rename"
    override val archive = "Archive"
    override val unarchive = "Unarchive"
    override val select = "Select"
    override val delete = "Delete"
    override val running = "Running"
    override val unread = "Unread"
    override val topics = "Topics"
    override val chats = "Chats"
    override val pinned = "Pinned"
    override val today = "Today"
    override val yesterday = "Yesterday"
    override val earlier = "Earlier"
    override val archived = "Archived"
    override val emptyChats = "No saved chats yet"
    override fun actionsFor(title: String) = "Conversation actions for $title"
    override fun selectedCount(count: Int) = "$count selected"
    override val deleteChatTitle = "Delete this conversation?"
    override fun deleteChatsTitle(count: Int) = "Delete $count conversations?"
    override val deleteDescription = "This action cannot be undone."
    override val deleteAutomationsNote = "Linked automations will also be deleted."
    override val deleteWithAutomations = "Delete with automations"
    override val archiveChatTitle = "Archive this conversation?"
    override fun archiveChatsTitle(count: Int) = "Archive $count conversations?"
    override val archiveDescription = "Conversations are kept; you can unarchive them at any time."
    override val unarchiveChatTitle = "Unarchive this conversation?"
    override fun unarchiveChatsTitle(count: Int) = "Unarchive $count conversations?"
    override val unarchiveDescription = "These conversations will return to the sidebar."
    override val renameChatTitle = "Rename conversation"
    override val renameChatPlaceholder = "Conversation name"
    override val save = "Save"
    override fun forkChatTitle(title: String) = "Fork: $title"

    override val emptyConversation = "This conversation is empty. Ask anything to start."
    override val createChat = "Create a chat to get started."
    override val composerHint = "Message, @tools, /commands, \$skills"
    override val copied = "Copied"
    override val copy = "Copy"
    override fun attachmentCount(count: Int) =
        if (count == 1) "1 attachment" else "$count attachments"
    override val sending = "Sending…"
    override val notConfirmed = "Not confirmed"
    override val stopped = "Stopped"
    override val forkFromHere = "Fork from here"
    override val attach = "Attach"
    override val pause = "Pause"
    override val send = "Send"
    override val addAttachment = "Add attachment"
    override val photos = "Photos"
    override val chooseFromGallery = "Choose from gallery"
    override val files = "Files"
    override val browseFiles = "Browse your files"
    override val uploading = "Uploading…"
    override val uploadFailed = "Upload failed"
    override fun uploadingPercent(percent: Int) = "Uploading $percent%"
    override val removeAttachment = "Remove attachment"
    override val attachmentPreview = "Attachment preview"
    override val closePreview = "Close preview"
    override val file = "File"
    override val fullAccess = "Full access"
    override val defaultAccess = "Default access"
    override val defaultAccessDescription = "Tools run only inside the current workspace"
    override val fullAccessDescription = "Tools can read and write files outside the workspace"
    override val attachmentPlaceholder = "(attachment)"
    override val camera = "Camera"
    override val takePhoto = "Take a photo"
    override val switchModel = "Switch model"
    override val defaultModelPreset = "Default"
    override val noModelPresets = "No model presets"
    override val contextUsageUnknown = "Context usage unknown"
    override fun contextUsage(percent: Int) = "Context $percent% used"
    override val couldNotSwitchModel = "Could not switch model"
    override val couldNotOpenCamera = "Could not open the camera"

    override val backToSavedWorkspaces = "Back to saved workspaces"
    override val favorites = "Favorites"
    override val recent = "Recent"
    override val noSavedWorkspaces = "No favorite or recent workspaces on this host yet."
    override val browseFolders = "Browse folders…"
    override val upOneLevel = "Up one level"
    override val filterFolders = "Filter folders"
    override val noSubfolders = "This folder has no subfolders."
    override val noMatchingFolders = "No matching folders."
    override val showingFirst500 = "Showing only the first 500 folders."
    override val cannotReadFolder = "Could not read this folder"

    override val searchMessages = "Search messages"
    override fun messageCount(count: Int) = if (count == 1) "1 message" else "$count messages"
    override fun matchCount(count: Int) = if (count == 1) "1 match" else "$count matches"
    override val noMessagesYet = "You haven't sent any messages in this chat yet."
    override val noMessagesMatch = "No messages match your search."

    override val thinkingStopped = "Thinking · stopped"
    override val thinking = "Thinking…"
    override val thought = "Thought"
    override val working = "Working…"
    override fun stepsLabel(count: Int) = if (count == 1) "· 1 step" else "· $count steps"
    override fun reasoningDescription(label: String, count: Int, expanded: Boolean): String {
        val suffix = if (count > 1) ", $count steps" else ""
        return "$label$suffix, ${if (expanded) "collapse" else "expand"} reasoning"
    }

    override val closeScanner = "Close scanner"
    override val scanQrCode = "Scan QR code"
    override val alignQrInFrame = "Align the QR code within the frame"
    override val turnOnTorch = "Turn on torch"
    override val turnOffTorch = "Turn off torch"
    override val torch = "Torch"
    override val torchOn = "Torch on"

    override val connectedToGateway = "Connected to your gateway"
    override val turnCompleted = "A turn completed"
    override val turnFailed = "A turn failed"
    override val turnCancelled = "A turn was cancelled"
    override val gatewayError = "Gateway error"
    override val nanobotError = "nanobot error"
    override val channelGatewayConnection = "Gateway connection"
    override val channelTurnResults = "Turn results"

    override val pixelCat = "Pixel cat"
    override val tapMe = "tap me!"
    override val settingsContentDescription = "Settings"

    override val enterGatewayAddress = "Enter a gateway address"
    override val invalidPairingLink = "invalid pairing link"
    override val couldNotSaveHost = "could not save this host"
    override val couldNotConnectHost = "could not connect this host"
    override val attachmentUploadFailed = "attachment upload failed"
    override fun attachmentTooLarge(size: String, limit: String) =
        "the attachment is $size, over the gateway's $limit per-file limit"
    override fun attachmentTooLargeAfterCompression(size: String, limit: String) =
        "still $size after compression, over the gateway's $limit per-file limit"
    override val couldNotLoadGatewaySettings = "could not load gateway settings"
    override val notConnectedToGateway = "Not connected to a gateway"
    override val couldNotUpdateSkills = "could not update skills"
    override val couldNotUpdateMcp = "could not update MCP presets"
    override val couldNotUpdateAutomation = "could not update automation"
    override val couldNotRunAutomation = "could not run automation"
    override val couldNotDeleteAutomation = "could not delete automation"
    override val couldNotLoadAutomationChats = "could not load chats"
    override val couldNotChangeAutomationChat = "could not change the linked chat"
    override val couldNotSend = "send failed"
    override val couldNotCreateChat = "could not create a chat"
    override val couldNotDelete = "delete failed"
    override val couldNotUpdateSidebar = "could not update the sidebar"
    override val sidebarNotLoaded = "the sidebar has not loaded yet"
    override val couldNotForkChat = "could not fork the chat"
    override val couldNotOpenWorkspace = "could not open that workspace"
    override val startedAutomation = "Automation started"
    override fun startedAutomationNamed(name: String) = "Started: $name"

    override val relayUnreachable = "relay unreachable"
    override val relayConnectionFailed = "relay connection failed"
    override val relayRefusedPhone = "the relay refused this phone"
    override val relayClosedConnection = "the relay closed the connection"
    override val relayDidNotAcceptPhone = "the relay did not accept this phone"
    override val relayInvalidFrame = "the relay sent an invalid frame"
    override val pcClosedStream = "the PC closed the stream"
    override val relayClosedDataChannel = "the relay closed the data channel"
    override val relayDataChannelFailed = "the relay data channel failed"
    override val relayDidNotOpenDataChannel = "the relay did not open the data channel"
    override val relayNotConnected = "the relay is not connected"
    override val pcDidNotAcceptStream = "the PC did not accept the stream"
    override val pcSentUndecryptableFrame = "the PC sent an undecryptable frame"
    override val phoneTooSlow = "the phone is too slow"
    override val relayTooSlow = "the relay is too slow"
    override val phoneClosedStream = "the phone closed the stream"

    override fun accentName(accent: AccentTheme) = when (accent) {
        AccentTheme.Mist -> "Mist blue"
        AccentTheme.Daiqing -> "Daiqing"
        AccentTheme.Dusk -> "Dusk violet"
        AccentTheme.Turquoise -> "Turquoise"
        AccentTheme.Amber -> "Amber"
        AccentTheme.Graphite -> "Graphite"
        AccentTheme.Custom -> "Custom"
        AccentTheme.Wallpaper -> "Match wallpaper"
    }

    override fun themeModeName(mode: ThemeMode) = when (mode) {
        ThemeMode.System -> followSystem
        ThemeMode.Light -> light
        ThemeMode.Dark -> dark
    }

    override fun fontSizeName(size: AppFontSize) = when (size) {
        AppFontSize.Small -> textSizeSmall
        AppFontSize.Standard -> textSizeStandard
        AppFontSize.Large -> textSizeLarge
        AppFontSize.ExtraLarge -> textSizeExtraLarge
    }
}

/** Units used to phrase an interval schedule like the WebUI's "Every 30 minutes". */
private enum class AutomationIntervalUnit { Second, Minute, Hour, Day }

private fun automationIntervalParts(millis: Long): Pair<Long, AutomationIntervalUnit> {
    val safe = millis.coerceAtLeast(1_000L)
    val units = listOf(
        AutomationIntervalUnit.Day to 86_400_000L,
        AutomationIntervalUnit.Hour to 3_600_000L,
        AutomationIntervalUnit.Minute to 60_000L,
        AutomationIntervalUnit.Second to 1_000L,
    )
    for ((unit, size) in units) {
        if (safe >= size && safe % size == 0L) return safe / size to unit
    }
    return (safe / 1_000L).coerceAtLeast(1L) to AutomationIntervalUnit.Second
}

private fun countLabel(value: Long, singular: String, plural: String): String =
    "$value ${if (value == 1L) singular else plural}"

/** Current strings for code that runs outside composition (view models, services, tunnels). */
internal object AppLocale {
    @Volatile
    var language: AppLanguage = AppLanguage.English
        set(value) {
            field = value
            strings = if (value == AppLanguage.SimplifiedChinese) ChineseStrings else EnglishStrings
        }

    @Volatile
    var strings: AppStrings = EnglishStrings
        private set
}

internal fun stringsFor(language: AppLanguage): AppStrings =
    if (language == AppLanguage.SimplifiedChinese) ChineseStrings else EnglishStrings

/** The strings currently provided to composables; English is the safe preview/test default. */
internal val LocalStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }
