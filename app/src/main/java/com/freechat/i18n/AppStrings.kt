package com.freechat.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.freechat.model.ChangelogEntry
import com.freechat.model.ChangelogSection

/** All UI strings organized by locale */
class AppStrings(val localeCode: String) {  // "zh-CN" | "zh-TW" | "en"

    // Drawer
    var newChat: String = ""
    var newRules: String = ""
    var newRulesTitle: String = ""
    var searchHistory: String = ""
    var noConversations: String = ""
    var noSearchResults: String = ""
    var settings: String = ""
    var renameConversation: String = ""
    var deleteConversation: String = ""
    var confirmDeleteConv: (String) -> String = { "" }
    // 侧滑页多选：批量删除确认（n = 选中的对话数）
    var confirmDeleteConvs: (Int) -> String = { "" }
    var deleteWarning: String = ""
    var delete: String = ""
    var cancel: String = ""
    var confirm: String = ""
    var pinConversation: String = ""
    var unpinConversation: String = ""
    // 侧滑页每行「···」菜单键的无障碍描述
    var moreActions: String = ""
    var groupPinned: String = ""
    var groupToday: String = ""
    var groupYesterday: String = ""
    var groupWithin7Days: String = ""
    var groupWithin30Days: String = ""
    var groupEarlier: String = ""

    // Input
    var inputPlaceholder: String = ""

    // Chat
    var thinking: String = ""
    var thinkingProcess: String = ""
    var expand: String = ""
    var collapse: String = ""
    var apiError: String = ""
    var imageGenerated: String = ""
    var generatingImage: String = ""
    var copyMessage: String = ""
    var deleteMessage: String = ""
    var regenerate: String = ""
    var deleteMessageConfirm: String = ""
    var copied: String = ""
    var stopGenerating: String = ""
    var addImage: String = ""
    var imagePreview: String = ""
    var downloadImage: String = ""
    var imageDownloaded: String = ""
    var uploadImage: String = ""
    var uploadFile: String = ""
    var editMessage: String = ""
    var editMessageHint: String = ""
    var editingPromptHint: String = ""
    // 多选：标题「已选择 x 项」+ 批量删除确认
    var selectedCount: (Int) -> String = { "" }
    var batchDeleteConfirm: String = ""
    var mbtiIncompleteTitle: String = ""
    var mbtiIncompleteMessage: String = ""

    // Settings
    var languageModel: String = ""
    var visualModel: String = ""
    var visionModel: String = ""
    var selectLanguageModel: String = ""
    var selectVisualModel: String = ""
    var selectVisionModel: String = ""
    var webSearch: String = ""
    var webSearchOn: String = ""
    var webSearchOff: String = ""
    var webSearchUnsupported: String = ""
    var showThinking: String = ""
    var showThinkingDesc: String = ""
    var autoSummarizeMemory: String = ""
    var autoSummarizeMemoryDesc: String = ""
    var useSystemFont: String = ""
    var useSystemFontDesc: String = ""
    var globalMemory: String = ""
    var globalMemoryDesc: String = ""
    var globalMemoryEmpty: String = ""
    var globalMemoryHint: String = ""
    var globalMemoryAdd: String = ""
    var thinkingUnsupported: String = ""
    var closeWebSearchTitle: String = ""
    var closeWebSearchDesc: String = ""
    var stillClose: String = ""
    var keepOn: String = ""
    // 「仍然开启」+ 代价提示三段（红字那段单独一条，弹层里要用 ErrorRed 上色）——1.0.50
    var stillEnable: String = ""
    var heavyWarnHead: String = ""
    var heavyWarnRed: String = ""
    var heavyWarnTail: String = ""
    var replyTemp: String = ""
    var replyLength: String = ""
    var theme: String = ""
    var themeMode: String = ""
    var language: String = ""
    var selectLanguage: String = ""
    var version: String = ""
    var changelog: String = ""
    var api: String = ""
    var author: String = ""
    var close: String = ""
    var sectionModels: String = ""
    var sectionAiOptimize: String = ""
    var sectionSystemTheme: String = ""
    var sectionGeneral: String = ""
    var sectionAbout: String = ""
    var aiMemory: String = ""
    var memorySummary: String = ""
    var memorySummaryDesc: String = ""
    var font: String = ""
    var fontOptimize: String = ""
    var fontOptimizeDesc: String = ""
    var advancedMaterial: String = ""
    var advancedMaterialDesc: String = ""
    var systemDarkTheme: String = ""
    var liquidBackdrop: String = ""
    var liquidBackdropDesc: String = ""
    var imageGenModel: String = ""

    // Temp modes
    var tempAuto: String = ""
    var tempAutoDesc: String = ""
    var tempWarm: String = ""
    var tempWarmDesc: String = ""
    var tempObjective: String = ""
    var tempObjectiveDesc: String = ""

    // Length modes
    var lengthAuto: String = ""
    var lengthAutoDesc: String = ""
    var lengthFull: String = ""
    var lengthFullDesc: String = ""
    var lengthConcise: String = ""
    var lengthConciseDesc: String = ""

    // Theme modes
    var themeSystem: String = ""
    var themeSystemDesc: String = ""
    var themeLight: String = ""
    var themeLightDesc: String = ""
    var themeDark: String = ""
    var themeDarkDesc: String = ""
    var themeDarkOled: String = ""
    var themeDarkOledDesc: String = ""

    // Color theme
    var colorTheme: String = ""
    var colorThemeBrown: String = ""
    var colorThemeBlue: String = ""
    var colorThemeWhite: String = ""

    // Language options
    var langSystem: String = ""
    var langSystemDesc: String = ""
    var langZhCN: String = ""
    var langZhTW: String = ""
    var langEn: String = ""

    // Font size
    var fontSize: String = ""
    var fontSizeSmall: String = ""
    var fontSizeMedium: String = ""
    var fontSizeLarge: String = ""
    var fontSizeXlarge: String = ""
    var fontSizeXxlarge: String = ""

    // Voice (TTS)
    var voiceModel: String = ""
    var selectVoiceModel: String = ""
    /** 「语音合成模型」/「语音识别模型」——1.0.50 起语音模型拆成两行，这是那两行的行标题 */
    var voiceTtsLabel: String = ""
    var voiceAsrLabel: String = ""
    var voiceTtsDesc: String = ""
    var voiceAsrDesc: String = ""
    var voiceTtsModelDesc: String = ""
    var voiceAsrModelDesc: String = ""
    var voiceDebug: String = ""
    var aiVoice: String = ""
    var voiceAutoPlay: String = ""
    var voiceAutoPlayDesc: String = ""
    var voiceTone: String = ""
    var voiceSpeed: String = ""
    var voicePitch: String = ""
    var voiceDefault: String = ""
    var speak: String = ""
    var stopSpeaking: String = ""
    var pause: String = ""
    var resume: String = ""

    // Greetings (time-based, arrays)
    var greetingsLateNight: List<String> = emptyList()
    var greetingsEarlyMorning: List<String> = emptyList()
    var greetingsMorningWeekday: List<String> = emptyList()
    var greetingsMorningWeekend: List<String> = emptyList()
    var greetingsNoon: List<String> = emptyList()
    var greetingsAfternoon: List<String> = emptyList()
    var greetingsEvening: List<String> = emptyList()
    var greetingsNight: List<String> = emptyList()

    // Companion mode (拟人陪伴)
    var chatMode: String = ""
    var chatModeDesc: String = ""
    var standardMode: String = ""
    var companionMode: String = ""
    var companionModeDesc: String = ""
    var chooseChatMode: String = ""
    var characterSetup: String = ""
    var characterName: String = ""
    var characterNameHint: String = ""
    var mbtiType: String = ""
    var personality: String = ""
    var personalityHint: String = ""
    var personalityPresetLabel: String = ""
    var memoryPerception: String = ""
    var memoryPerceptionHint: String = ""
    // 1.0.34 模拟设定扩增：配角 / 规则 / 用户形象
    var supportingCast: String = ""
    var supportingCastHint: String = ""
    var worldRules: String = ""
    var worldRulesHint: String = ""
    var userPersona: String = ""
    var userPersonaHint: String = ""
    var friendTyping: String = ""
    var companionPlaceholder: String = ""
    var companionSwitch: String = ""
    var startChat: String = ""
    var basicInfo: String = ""
    var gender: String = ""
    var age: String = ""
    var ageHint: String = ""

    // 回复缓冲
    var replyBuffer: String = ""
    var replyBufferDesc: String = ""
    var replyBufferEnable: String = ""

    // 开场白
    var openingLine: String = ""
    var openingLineHint: String = ""

    // 保存设置 / 未保存返回
    var saveSettings: String = ""
    var unsavedBackTitle: String = ""
    var unsavedBackMessage: String = ""
    var discard: String = ""

    // 学习人设加载页
    var learningPersonaTitle: String = ""
    var learningPersonaDesc: String = ""
    var learningPersonaLongWait: String = ""

    // 人物形象 / emoji
    var appearance: String = ""
    var appearanceHint: String = ""
    var appearanceImage: String = ""
    var emoji: String = ""

    // 人物关系 / 开场白多条
    var relationship: String = ""
    var relationshipPresetLabel: String = ""
    var relationshipHint: String = ""
    var relationshipReadonlyHint: String = ""
    var referencePrototypeLabel: String = ""
    var referencePrototypeHint: String = ""
    var referencePrototypeDesc: String = ""
    var openingLineAdd: String = ""

    // 对话模式（1.0.28 取代旧「剧情模式」开关）/ 模拟作息
    var dialogueMode: String = ""
    var dialogueModeDesc: String = ""
    // 1.0.53：档位创建时必选、聊起来之后锁死 —— 两个提示 + 一行锁定说明
    var dialogueModeRequiredTitle: String = ""
    var dialogueModeRequiredDesc: String = ""
    var dialogueModeLockedTitle: String = ""
    var dialogueModeLockedDesc: String = ""
    var dialogueModeLockedHint: String = ""
    // 创建时每选一次档位就提醒一次的浮层正文（只说「改了不能变」，不解释为什么）
    var dialogueModePickWarn: String = ""
    var dialogueModeWechat: String = ""
    var dialogueModeWechatDesc: String = ""
    var dialogueModeAction: String = ""
    var dialogueModeActionDesc: String = ""
    var dialogueModePlot: String = ""
    var dialogueModePlotDesc: String = ""
    var plotLength: String = ""
    var plotLengthShort: String = ""
    var plotLengthMid: String = ""
    var plotLengthLong: String = ""
    var plotLengthExtraLong: String = ""
    var plotExampleTitle: String = ""
    var plotExampleUser: String = ""
    var plotExampleAi: String = ""
    var actionExampleUser: String = ""
    var actionExampleAi: String = ""
    // 微信聊天示例：两边都是一条条短消息，所以用列表而不是单段文本
    var wechatExampleUser: List<String> = emptyList()
    var wechatExampleAi: List<String> = emptyList()
    // 叙事模式示例里的「你 / AI」前缀（原先硬编码在界面里，切英文后会露出中文）
    var chatExampleYou: String = "你"
    var chatExampleAi: String = "AI"
    var highQualityMemory: String = ""
    var highQualityMemoryDesc: String = ""
    var deepThinking: String = ""
    var deepThinkingDesc: String = ""
    var aiCreativity: String = ""
    var aiCreativityDesc: String = ""
    var aiCreativityHint: String = ""
    var paste: String = ""
    var fullscreenInput: String = ""
    var sleepSimulation: String = ""
    var sleepSimulationDesc: String = ""
    var proactive: String = ""
    var proactiveDesc: String = ""
    var proactiveExactHint: String = ""
    // 功能名后面的小角标：表示该功能还在测试阶段（三语同形，不翻译）
    var betaTag: String = "Beta"

    // 编辑模式 / 头像 / 大类标题 / 更新学习页
    var edit: String = ""
    var save: String = ""
    var editConfirmTitle: String = ""
    var editConfirmMessage: String = ""
    var saveConfirmTitle: String = ""
    var saveConfirmMessage: String = ""
    var unsavedChangesTitle: String = ""
    var unsavedChangesMessage: String = ""
    var discardChanges: String = ""
    var avatar: String = ""
    var viewAvatar: String = ""
    var editAvatar: String = ""
    var changeAvatar: String = ""
    var updatingPersonaTitle: String = ""
    var updatingPersonaDesc: String = ""
    var sectionPersona: String = ""
    var sectionSimulation: String = ""
    // —— 消息分享 / 角色导入导出 ——
    var share: String = ""
    var exportCharacter: String = ""
    var importCharacter: String = ""
    var exportSuccess: String = ""
    var importSuccess: String = ""
    var importFailed: String = ""
    var exportFailed: String = ""
    var shareAsImage: String = ""
    var saveImage: String = ""
    var shareAsMarkdown: String = ""
    var saveMarkdown: String = ""
    // —— 用户自定义模型编辑器 ——
    var addModel: String = ""
    var editModel: String = ""
    var modelName: String = ""
    var modelNameHint: String = ""
    var apiKeyLabel: String = ""
    var apiKeyHint: String = ""
    var modelIdLabel: String = ""
    var modelIdHint: String = ""
    var apiUrlLabel: String = ""
    var apiUrlHint: String = ""
    var modelNote: String = ""
    var modelNoteHint: String = ""
    var saveModel: String = ""
    var deleteModel: String = ""
    var unsavedTitle: String = ""
    var unsavedMessage: String = ""
    var userAgreement: String = ""
    var addModelFirst: String = ""
    // —— 收藏夹 ——
    var favorites: String = ""
    var favorite: String = ""
    var unfavorite: String = ""
    var noFavorites: String = ""
    var favoriteDetail: String = ""
    var openOriginal: String = ""
    var deleteFavoritesWarning: String = ""
    var confirmUnfavorite: String = ""
    /** 详情页展示的是「一整段连续收藏」时，取消收藏会整段移除，弹窗文案要说明条数 */
    var confirmUnfavoriteRun: (Int) -> String = { "" }
    var roleUser: String = ""
    var roleAi: String = ""
    var filterAll: String = ""
    var sortFavoriteTime: String = ""
    var sortConvTime: String = ""
    var filterByConversation: String = ""
    var sortBy: String = ""
    // —— 账号与同步 ——
    var account: String = ""
    var accountLocalOnly: String = ""
    /** 未登录时告诉用户这台设备上已有多少东西（n = "12 条对话 · 340 条消息"） */
    var accountLocalData: (String) -> String = { "" }
    var accountTabLogin: String = ""
    var accountTabRegister: String = ""
    var accountTabRecover: String = ""
    var accountUsername: String = ""
    var accountPassword: String = ""
    var accountPasswordConfirm: String = ""
    var accountOldPassword: String = ""
    var accountNewPassword: String = ""
    var accountRecoveryCode: String = ""
    var accountLogin: String = ""
    var accountRegister: String = ""
    var accountResetPassword: String = ""
    var accountWorking: String = ""
    var accountRecoveryTitle: String = ""
    var accountRecoveryHint: String = ""
    var accountCopy: String = ""
    var accountCopied: String = ""
    var accountSavedIt: String = ""
    var accountSignedInAs: String = ""
    var accountUsage: String = ""
    var accountSyncNow: String = ""
    var accountSyncIdle: String = ""
    var accountSyncSyncing: String = ""
    var accountSyncOff: String = ""
    var accountSyncError: String = ""
    var accountSyncNever: String = ""
    /** n = 待推送条数 */
    var accountSyncPending: (Int) -> String = { "" }
    /** t = 上次同步时间 */
    var accountSyncLast: (String) -> String = { "" }
    var accountDevices: String = ""
    var accountRevoke: String = ""
    var accountChangePassword: String = ""
    var accountRegenerate: String = ""
    var accountLogout: String = ""
    var accountLogoutHint: String = ""
    var accountDelete: String = ""
    var accountDeleteWarn: String = ""
    var accountDeleteConfirm: String = ""
    var accountErrFillAll: String = ""
    var accountErrMismatch: String = ""
    var accountErrPasswordShort: String = ""
    var accountErrNetwork: String = ""
    var accountNotices: String = ""
    var accountPasswordChanged: String = ""
    var accountCodeRegenerated: String = ""
    // —— 头像与用户名 ——
    /** 裁切弹窗底部那行小字：告诉用户怎么操作 */
    var avatarCropHint: String = ""
    var accountChangeAvatar: String = ""
    /** 还没有头像时用这个 —— 那时候点它是「第一次上传」，说「更换」用户会以为自己眼花了 */
    var accountUploadAvatar: String = ""
    var accountRemoveAvatar: String = ""
    var accountChangeUsername: String = ""
    /** 用户名的格式要求 —— 与服务端同一套规则（3–20 位字母、数字、下划线） */
    var accountUsernameRule: String = ""
    var accountUsernameChanged: String = ""
    var accountAvatarChanged: String = ""
    var accountAvatarRemoved: String = ""
    /** 头像这一路任何一步没走通时的统一提示 —— 宁可啰嗦一句，也别让用户觉得「点了没反应」 */
    var accountAvatarFailed: String = ""
    /** 挑中的那张图读不出来（被删了 / 格式不认）时的提示 */
    var avatarCropFailed: String = ""
    /** 用户名格式不对时的提示，参数是那条规则 */
    var accountErrUsername: (String) -> String = { "" }
    /**
     * 内置「Claude风格助理」显示给用户的名字。
     *
     * 存进对话里的标题是简中那份（数据），显示时按当前语言换掉 ——
     * 否则切到英文之后，侧栏里会突兀地挂着一条中文标题。
     */
    var claudeAssistantName: String = ""
    // —— 对话的「规则」（系统级提示词）——
    var convRules: String = ""
    var convRulesDesc: String = ""
    var convRulesHint: String = ""
    /** 生成失败标识（网络/连接/API 配置/模型限制…任何原因导致这一轮没生成出来） */
    var genFailed: String = ""
    /** 账号头像菜单里的「编辑头像」—— 拿同一张图重新裁一次取景范围 */
    var accountEditAvatar: String = ""

    // ─────────── 1.0.49 文案补漏 ───────────
    // 下面这些原先都是硬编码在界面里的中文字面量，切到英文/繁體之后会露出中文，
    // 于是出现「中英交替」。统一收到这里，三语齐全。
    var collapseInput: String = ""
    var fullscreenInputting: String = ""
    var stopAction: String = ""
    var holdToTalk: String = ""
    var sendAction: String = ""
    /** 已选图片/文件数（n / 上限） */
    var imageCountLabel: (Int, Int) -> String = { _, _ -> "" }
    var fileCountLabel: (Int, Int) -> String = { _, _ -> "" }
    var removeAction: String = ""
    var quoteAction: String = ""
    var cancelQuote: String = ""
    var quotedImage: String = ""
    var imageLabel: String = ""
    var fileLabel: String = ""
    var docLabel: String = ""
    var releaseToCancel: String = ""
    var attachmentLabel: String = ""
    var tapToOpenEdit: String = ""
    var tapToOpen: String = ""
    /** 图片落盘成功（带目标目录） */
    var imageSavedTo: (String) -> String = { "" }
    var storageSaveFailed: String = ""
    var saveFailedWith: (String) -> String = { "" }
    var downloadFailedWith: (String) -> String = { "" }
    var imageMissing: String = ""
    var fileMissing: String = ""
    var openFileFailed: (String) -> String = { "" }
    var shareTooMuch: String = ""
    var imageTag: String = ""
    var fileTag: String = ""
    var shareImageTooLarge: String = ""
    var shareFailed: String = ""
    var savedOk: String = ""
    var saveFailedShort: String = ""
    var noAsrModelTitle: String = ""
    var noAsrModelDesc: String = ""
    var goAddAction: String = ""
    var searchAction: String = ""
    var clearAction: String = ""
    var notAdded: String = ""
    var notSelected: String = ""
    var emptyMessage: String = ""
    var settingsSlogan: String = ""
    var welcomePrompt: String = ""
    // 日期格式按语言给：中文「3月5日」，英文「Mar 5」——用 SimpleDateFormat 的 pattern
    var dateMd: String = ""
    var dateYmd: String = ""
    var dateMdTime: String = ""
    var dateYmdTime: String = ""
    // 同步引擎的提示（显示在账号页），以及账号/网络的报错话术
    var syncLoginExpired: String = ""
    var syncFailed: String = ""
    /** 同一角色在两台设备上各改各的，云端留副本 */
    var syncConflictCopySaved: (String) -> String = { "" }
    var syncObjectTooLarge: (String) -> String = { "" }
    var syncResurrected: (String) -> String = { "" }
    // 上面三条提示里指代对象用的量词（"「一条对话」太大…"）
    var syncKindConv: String = ""
    var syncKindMsgs: String = ""
    var syncKindMems: String = ""
    var syncKindSettings: String = ""
    var notLoggedIn: String = ""
    /** 登录页告诉用户"本地这些东西会跟着账号走"（对话数 / 消息数） */
    var localDataSummary: (Int, Int) -> String = { _, _ -> "" }
    var networkUnreachable: String = ""
    var requestFailedWith: (Int) -> String = { "" }
    var conflictCopySuffix: String = ""
    var shareAction: String = ""
    // 文档解析（贴进聊天框的可读文本）
    var legacyBinaryFile: String = ""
    var readFileFailed: (String) -> String = { "" }
    var pageMarker: (Int) -> String = { "" }
    // 通知渠道名（会出现在系统通知设置里）与前台服务文案
    var channelProactive: String = ""
    var channelProactiveDesc: String = ""
    var proactiveThinking: String = ""
    var channelKeepAlive: String = ""
    var channelKeepAliveDesc: String = ""
    var channelReply: String = ""
    var channelReplyDesc: String = ""
    var backgroundRunning: String = ""
    var roleLearningRejected: String = ""
    // 联网搜索额度（设置页那一行 + 搜不到时给的准确原因）
    var webQuotaExhausted: (Int, String) -> String = { _, _ -> "" }
    var webQuotaExhaustedNoDate: String = ""
    var webQuotaRemaining: (Int, Int) -> String = { _, _ -> "" }
    var webQuotaNoKey: String = ""
    var webQuotaUnknown: String = ""
    var webQuotaCountUnknown: String = ""
    var webQuotaCount: (Int) -> String = { "" }
    var webNoKeyConfigured: String = ""
    var webRequestFailed: (String) -> String = { "" }
    var webTooFrequent: String = ""
    var webUnavailable: String = ""
    var notSet: String = ""
    var followGlobal: String = ""
    var followGlobalDefault: String = ""
    // 中文用顿号、英文用逗号 —— 列举预设时用
    var listSeparator: String = ""
    /**
     * 性别与预设的**显示**文案。
     *
     * 键（[genderKeys] 和各预设常量）仍然是中文，因为它们是**存进角色档案里的数据**，
     * 而且会跟着同步走。这里只换显示：否则英文界面下用户会看到「男 / 女」，
     * 而把存盘值一起改掉的话，老档案里的中文值就再也匹配不上、预设会全部显示成未选中。
     */
    var genderKeys: List<String> = emptyList()
    var genderLabels: List<String> = emptyList()
    var personalityPresetLabels: List<String> = emptyList()
    var relationshipPresetLabels: List<String> = emptyList()
    /** 内置模型的说明文字（按模型 id 取；用户自建模型走各自的说明） */
    var builtInModelDesc: (String) -> String = { "" }

    // ===== 用户协议 =====
    /** 协议页正文：章节 = 标题 + 若干段落。段落里 `**…**` 之间的字加粗，以 `· ` 开头的当条目 */
    var agreementTitle: String = ""
    var agreementSections: List<AgreementSectionText> = emptyList()
    /** 首次进入的勾选弹窗 */
    var agreementGateHead: String = ""
    var agreementGateItems: List<String> = emptyList()
    var agreementAgreePrefix: String = ""
    var agreementDocName: String = ""
    var agreementContinue: String = ""
    var agreementReleasePage: String = ""

    /** 更新日志正文（简中直接复用 ChangelogData，其余语言各写各的） */
    var changelogEntries: List<com.freechat.model.ChangelogEntry> = emptyList()

    // ===== 关于作者（设置 → 作者）=====
    /** 顶部标题栏的标题（1.0.49 起是「关于作者」；设置列表里那一行仍叫「作者」） */
    var authorAboutTitle: String = ""
    /** 页首大标题 */
    var authorPageTitle: String = ""
    /** 一问一答：问题小字加粗配主题色，回答大字 */
    var authorQa: List<AuthorQa> = emptyList()
    /** 三个「答案里带链接 / 二维码」的问题，文字单独放 —— 链接本身在代码里是常量 */
    var authorWebAddress: String = ""
    var authorWebNote: String = ""
    var authorGithubRepo: String = ""
    var authorGithubNote: String = ""
    var authorContact: String = ""
    /** 二维码下面那行小字：长按能存（不然没人知道要长按） */
    var authorQrHint: String = ""
    /** 长按二维码弹出的弹层标题 */
    var saveQr: String = ""
    /** 弹层主按钮 */
    var saveToGallery: String = ""
}

/** 「关于作者」页的一问一答 */
data class AuthorQa(val q: String, val a: String)

/** 协议的一节：标题 + 段落列表 */
data class AgreementSectionText(val title: String, val paragraphs: List<String>)

/**
 * 内置模型的说明文案要跟着界面语言走。
 *
 * 但 [com.freechat.model.ModelInfo] 是 ViewModel 初始化时就建好的、description 还会随自定义模型一起同步，
 * 所以翻译不能写进数据里 —— 显示时按 id 查一次就行；查不到（用户自建模型）就用用户自己写的备注。
 */
fun localizedModelDesc(m: com.freechat.model.ModelInfo, s: AppStrings): String =
    if (!m.isBuiltIn) m.description else s.builtInModelDesc(m.id).ifBlank { m.description }

/**
 * 对话显示给用户的名字。
 *
 * 只有内置的「Claude风格助理」会被换掉 —— 别的对话用的都是用户自己起的标题（或者模型生成的），
 * 那是用户的内容，一个字都不能动。内置那条的标题虽然也落在磁盘上，但它是**代码写的**，
 * 所以显示时该按当前语言取一遍；不然切到英文后侧栏里会杵着一条中文。
 */
fun localizedConvTitle(conv: com.freechat.model.Conversation, s: AppStrings): String =
    if (conv.builtInAssistant.isNotBlank()) s.claudeAssistantName else conv.title

/** Build strings for a given locale */
fun buildStrings(localeCode: String): AppStrings = when (localeCode) {
    "zh-TW" -> ZhTW
    "en" -> En
    else -> ZhCN
}

// ========== 简体中文 ==========
val ZhCN = AppStrings("zh-CN").apply {
    newChat = "新对话"
    newRules = "调节"
    newRulesTitle = "新规则"
    searchHistory = "搜索历史记录..."
    noConversations = "暂无历史对话"
    noSearchResults = "未找到匹配的对话"
    settings = "设置"
    renameConversation = "重命名对话"
    deleteConversation = "删除对话"
    confirmDeleteConv = { "确定要删除「$it」吗？删除后无法恢复。" }
    confirmDeleteConvs = { "确定要删除选中的 $it 个对话吗？每个对话的聊天记录都会一并删除，删除后无法恢复。" }
    deleteWarning = "删除后无法恢复"
    delete = "删除"
    cancel = "取消"
    confirm = "确定"
    pinConversation = "置顶"
    unpinConversation = "取消置顶"
    moreActions = "更多操作"
    groupPinned = "置顶"
    groupToday = "今天"
    groupYesterday = "昨天"
    groupWithin7Days = "7天内"
    groupWithin30Days = "30天内"
    groupEarlier = "更早之前"
    inputPlaceholder = "想聊点什么？"
    thinking = "思考中"
    thinkingProcess = "思考过程"
    expand = "展开"
    collapse = "收起"
    apiError = "请求失败，请稍后尝试..."
    imageGenerated = "已为你生成图片："
    generatingImage = "图片生成中..."
    copyMessage = "复制"
    deleteMessage = "删除"
    regenerate = "重新生成"
    deleteMessageConfirm = "确定删除这条消息吗？"
    copied = "已复制"
    stopGenerating = "停止生成"
    addImage = "添加图片"
    imagePreview = "图片预览"
    downloadImage = "下载图片"
    imageDownloaded = "图片已保存"
    uploadImage = "上传图片"
    uploadFile = "上传文件"
    editMessage = "编辑消息"
    editMessageHint = "输入新的内容…"
    editingPromptHint = "正在改写这条提示词"
    selectedCount = { "已选择 $it 项" }
    batchDeleteConfirm = "确定删除选中的消息吗？此操作不可撤销。"
    mbtiIncompleteTitle = "MBTI 未完整"
    mbtiIncompleteMessage = "MBTI 要么四个维度都选，要么都不选，不能只选一部分。"
    languageModel = "语言模型"
    visualModel = "视觉模型"
    visionModel = "识图模型"
    selectLanguageModel = "选择语言模型"
    selectVisualModel = "选择视觉模型"
    selectVisionModel = "选择识图模型"
    webSearch = "联网搜索"
    webSearchOn = "获取实时信息"
    webSearchOff = "已关闭"
    webSearchUnsupported = "当前模型不支持联网搜索"
    showThinking = "显示思考过程"
    showThinkingDesc = "展示AI推理过程"
    autoSummarizeMemory = "自动总结对话以巩固AI记忆"
    autoSummarizeMemoryDesc = "每轮对话自动提炼要点写入记忆，回复时结合记忆提升精准度"
    useSystemFont = "使用系统字体"
    useSystemFontDesc = "勾选后聊天文字使用系统默认字体"
    globalMemory = "全局记忆"
    globalMemoryDesc = "告诉 AI 你的基本信息和回复要求，回答时自动参考"
    globalMemoryEmpty = "暂无记忆点，在下方输入后点击添加"
    globalMemoryHint = "例如：我叫小王，喜欢简洁的回答"
    globalMemoryAdd = "添加"
    thinkingUnsupported = "不支持显示思考过程"
    closeWebSearchTitle = "关闭联网搜索？"
    closeWebSearchDesc = "关闭后 AI 将无法获取实时信息，回复可能基于过时数据。建议保持开启以获得更好的体验。"
    stillClose = "仍然关闭"
    stillEnable = "仍然开启"
    heavyWarnHead = "该功能会"
    heavyWarnRed = "大幅提高token消耗、增加回复时长"
    heavyWarnTail = "，确认开启？"
    keepOn = "保持开启"
    replyTemp = "回复温度"
    replyLength = "回复长度"
    theme = "主题"
    themeMode = "主题模式"
    language = "语言"
    selectLanguage = "选择语言"
    version = "版本"
    changelog = "更新日志"
    api = "模型"
    author = "作者"
    close = "关闭"
    sectionModels = "模型选择"
    sectionAiOptimize = "AI系统优化"
    sectionSystemTheme = "系统主题"
    sectionGeneral = "通用"
    sectionAbout = "关于"
    aiMemory = "AI记忆"
    memorySummary = "记忆总结"
    memorySummaryDesc = "提炼对话内容写入固定文件，提升AI长久记忆力"
    font = "字体"
    fontOptimize = "字体优化"
    fontOptimizeDesc = "使用内置字体优化排版，提升文字可读性"
    advancedMaterial = "高级材质"
    advancedMaterialDesc = "增加模糊等渲染效果，提升质感"
    systemDarkTheme = "系统暗色主题"
    liquidBackdrop = "流光炫彩"
    liquidBackdropDesc = "柔光渐变背景，提升观感"
    imageGenModel = "生图模型"
    tempAuto = "自动"
    tempAutoDesc = "平衡情感与逻辑"
    tempWarm = "热情"
    tempWarmDesc = "注重情感共情，温暖回应"
    tempObjective = "客观"
    tempObjectiveDesc = "注重逻辑严谨，理性分析"
    lengthAuto = "自动"
    lengthAutoDesc = "智能平衡回复长度"
    lengthFull = "完整"
    lengthFullDesc = "完整详细，步骤清晰"
    lengthConcise = "精辟"
    lengthConciseDesc = "精辟犀利，一语中的"
    themeSystem = "跟随系统"
    themeSystemDesc = "自动跟随系统"
    themeLight = "浅色"
    themeLightDesc = "始终浅色"
    themeDark = "深色"
    themeDarkDesc = "始终深色"
    themeDarkOled = "黑色"
    themeDarkOledDesc = "始终黑色"
    colorTheme = "色彩"
    colorThemeBrown = "莫兰迪暖棕"
    colorThemeBlue = "莫兰迪浅蓝"
    colorThemeWhite = "纯白"
    langSystem = "跟随系统"
    langSystemDesc = "自动跟随系统语言"
    langZhCN = "简体中文"
    langZhTW = "繁體中文"
    langEn = "English"
    fontSize = "字体大小"
    fontSizeSmall = "小"
    fontSizeMedium = "标准"
    fontSizeLarge = "较大"
    fontSizeXlarge = "大"
    fontSizeXxlarge = "特大"
    voiceModel = "语音模型"
    selectVoiceModel = "选择语音模型"
    voiceTtsLabel = "语音合成模型"
    voiceAsrLabel = "语音识别模型"
    voiceTtsDesc = "语音合成 · 朗读 AI 回复"
    voiceAsrDesc = "语音识别 · 语音输入转文字"
    voiceTtsModelDesc = "Xiaomi语音合成模型"
    voiceAsrModelDesc = "Xiaomi语音识别模型"
    voiceDebug = "语音调试"
    aiVoice = "AI语音"
    voiceAutoPlay = "自动朗读"
    voiceAutoPlayDesc = "AI 回复完成后自动朗读全文"
    voiceTone = "音色"
    voiceSpeed = "语速"
    voicePitch = "音调"
    voiceDefault = "默认"
    speak = "朗读"
    stopSpeaking = "停止朗读"
    pause = "暂停"
    resume = "继续播放"
    greetingsLateNight = listOf(
        "夜深了，还没睡呀～", "凌晨了，在想什么呢？",
        "很晚了，早点休息呀！", "睡不着的话，我陪你说说话～"
    )
    greetingsEarlyMorning = listOf(
        "清晨好！新的一天开始了～", "早安！今天有什么计划吗？",
        "晨光正好，想聊点什么？", "起得真早，今天是好日子！"
    )
    greetingsMorningWeekday = listOf(
        "上午好！想聊点什么？", "早上好呀，今天想探索什么？", "精力充沛的上午，开始吧！"
    )
    greetingsMorningWeekend = listOf(
        "周末早上好呀～今天不用早起真好", "懒洋洋的周末早晨，想聊点什么？", "周末早安！享受悠闲时光吧～"
    )
    greetingsNoon = listOf(
        "中午好！吃过了吗？", "午安～休息一下，聊聊天吧", "正午时分，有什么想聊的？"
    )
    greetingsAfternoon = listOf(
        "下午好！今天过得怎么样？", "午后时光，想聊点什么？",
        "下午好呀，我在呢～", "阳光正好，来聊会儿吧"
    )
    greetingsEvening = listOf(
        "晚上好！今天辛苦啦～", "晚间时光，放松一下？",
        "夜幕降临，聊点什么呢？", "晚上好，今天想聊什么？"
    )
    greetingsNight = listOf(
        "夜深了，早点休息呀！", "还在忙吗？别忘了休息～",
        "夜里安静，适合思考呢", "这么晚了，我还在哦～"
    )
    chatMode = "聊天模式"
    chatModeDesc = "切换 AI 回复风格"
    standardMode = "标准问答"
    companionMode = "拟人陪伴"
    companionModeDesc = "像真人朋友一样陪你聊天"
    chooseChatMode = "选择聊天模式"
    characterSetup = "模拟设置"
    characterName = "角色名称"
    characterNameHint = "用于标识该角色的名称（必填）"
    mbtiType = "MBTI 类型"
    personality = "人物性格"
    personalityHint = "描述角色的性格特征（可选）"
    personalityPresetLabel = "预设"
    memoryPerception = "记忆感知"
    memoryPerceptionHint = "补充你的个人情况与前提故事（可选）"
    supportingCast = "配角"
    supportingCastHint = "补充故事中的其他人物，含身份、关系与背景（可选）"
    worldRules = "规则"
    worldRulesHint = "补充世界观、故事框架与既有设定（可选）"
    userPersona = "用户形象"
    userPersonaHint = "填写你在此设定中的身份与背景，AI 将据此演绎你的言行（可选）"
    friendTyping = "正在输入中..."
    companionPlaceholder = "和朋友聊天..."
    companionSwitch = "拟人模式"
    startChat = "开始聊天"
    basicInfo = "基本信息"
    gender = "性别"
    age = "年龄"
    ageHint = "填写年龄"
    referencePrototypeLabel = "参考原型"
    referencePrototypeHint = "作为参考的角色名或出处（可选）"
    referencePrototypeDesc = "AI 先学习你撰写的人物设定，再以该角色的既有设定补充完善。你的设定优先，冲突时以你的设定为准；仅当你写的人物设定过少时才联网搜索补齐。"
    replyBuffer = "回复缓冲"
    replyBufferDesc = "连续发送多条消息时，TA 会等待该时长后合并回复（1-6 秒）"
    replyBufferEnable = "开启回复缓冲"
    openingLine = "开场白"
    openingLineHint = "角色的开场台词（可选）"
    saveSettings = "保存设置"
    unsavedBackTitle = "未保存"
    unsavedBackMessage = "确定返回吗？你的修改不会被保存。"
    discard = "确定返回"
    learningPersonaTitle = "AI 正在学习人物设定"
    learningPersonaDesc = "正在深度理解并还原 TA 的性格、经历与说话方式…"
    learningPersonaLongWait = "AI 正在深度理解人物设定，请耐心等待一会儿…"
    appearance = "人物形象"
    appearanceHint = "描述角色的外貌与体态（可选）"
    appearanceImage = "形象参考图"
    emoji = "表情"
    relationship = "人物关系"
    relationshipPresetLabel = "预设（仅作大致方向参考，关系随聊天自然变化）"
    relationshipHint = "补充与角色的具体关系（可选）"
    relationshipReadonlyHint = "关系随你们的聊天剧情自然演进，无法手动修改"
    openingLineAdd = "再添一句开场白"
    dialogueMode = "对话模式"
    dialogueModeDesc = "决定 AI 的回复形式；创建时选定，发出第一条消息后不可更改"
    dialogueModeRequiredTitle = "还没选对话模式"
    dialogueModeRequiredDesc = "先给这个角色选一种写法：微信聊天 / 动作演绎 / 剧情补足。它决定 AI 以后每一条回复的格式，创建之后就改不了了。"
    dialogueModeLockedTitle = "对话模式已锁定"
    dialogueModeLockedDesc = "对话已经开始，上下文会导致AI学习对话模式失败，故无法再修改对话模式，如想改变对话模式，请导出角色后导入新建一个。"
    dialogueModeLockedHint = "对话模式发出第一条消息后就锁定了；要改请导出角色后导入新建。"
    dialogueModePickWarn = "创建并对话后无法修改对话模式，请确定选择。"
    dialogueModeWechat = "微信聊天"
    dialogueModeWechatDesc = "模拟即时通讯，一问一答、逐条回复"
    dialogueModeAction = "动作演绎"
    dialogueModeActionDesc = "以该角色的语言、动作、神态、心理为主，必要时可带少量第三方角色的言行，不写旁白"
    dialogueModePlot = "剧情补足"
    dialogueModePlotDesc = "完整的小说正文：环境、旁白、记叙、心理与神态，所有人物用名字或第三人称，台词用双引号"
    plotLength = "单次回复长度"
    plotLengthShort = "短"
    plotLengthMid = "中"
    plotLengthLong = "长"
    plotLengthExtraLong = "超长"
    plotExampleTitle = "发送示例"
    plotExampleUser = "我推开酒吧的门，雨夜的冷风灌进来，一眼就看到了坐在角落里的你。"
    plotExampleAi = "雨点敲在玻璃窗上，像谁在轻轻叩门。林晚把银匙搁回碟边，金属碰上瓷器，叮的一声，很轻，却被这间没几个人的酒吧衬得格外清楚。门口那阵冷风先一步涌了进来，卷着潮湿的柏油气味，然后她才看见他——伞沿还在滴水，肩膀上落了一层细密的水珠。她把杯子往自己这边挪了挪，腾出半张桌子，声音轻得几乎化进雨声里：“我还以为你不会来了。”"
    actionExampleUser = "我把伞收起来靠在门边，抖了抖袖子上的水，问你今天怎么一个人坐在这儿。"
    actionExampleAi = "我把凉透的咖啡往旁边推了推，抬头看你。「等你。」说完自己先愣了一下，指尖在杯沿上转了一圈，「……也没等多久。」"
    wechatExampleUser = listOf("在吗", "今天忙不忙", "晚上一起吃饭？")
    wechatExampleAi = listOf("在的", "刚忙完，还好", "好啊，几点？")
    highQualityMemory = "增强检索"
    highQualityMemoryDesc = "无缩略全文阅读思考，显著提升AI记忆力与逻辑能力"
    deepThinking = "深度推演"
    deepThinkingDesc = "流程化审问，输出前确认记忆与逻辑，进一步提升回复质量"
    aiCreativity = "AI创造力"
    aiCreativityDesc = "AI对剧情发展的主动影响程度\n" +
        "<4 ：AI回复紧贴用户提示词，基本不主动创造新剧情。\n" +
        "4-6：AI回复在角色设定与剧情发展基础之上，创造一定的新剧情走向。\n" +
        "6-8：AI回复积极地创建新内容、新事件、新角色、新场景等。\n" +
        ">8 ：AI回复脑回路新奇，可能脱离常规逻辑，难以理解。"
    aiCreativityHint = "建议 3-7"
    paste = "粘贴"
    fullscreenInput = "全屏输入"
    sleepSimulation = "作息模拟"
    sleepSimulationDesc = "AI 拥有独立作息，睡眠时段不回复，醒后自然说明"
    proactive = "主动智能"
    proactiveDesc = "本地计时延长API缓存，测试阶段效果不确定，可能会大幅增加token消耗。"
    proactiveExactHint = "尚未授予「闹钟和提醒」权限：到点可能延迟数分钟。点击前往授权（不影响功能，仅影响准时程度）"
    betaTag = "Beta"
    edit = "编辑"
    save = "保存"
    editConfirmTitle = "确定启动编辑"
    editConfirmMessage = "编辑模式下可修改人物设定，确定继续？"
    saveConfirmTitle = "确认保存"
    saveConfirmMessage = "保存后将更新角色设定，确定保存？"
    unsavedChangesTitle = "更改未保存"
    unsavedChangesMessage = "有更改尚未保存，确定放弃并退出编辑？"
    discardChanges = "放弃更改"
    avatar = "头像"
    viewAvatar = "查看头像"
    editAvatar = "编辑头像"
    changeAvatar = "更换头像"
    updatingPersonaTitle = "AI 更新理解中"
    updatingPersonaDesc = "正在根据你的修改更新角色形象…"
    sectionPersona = "人物设定"
    sectionSimulation = "模拟设置"
    share = "分享"
    exportCharacter = "导出角色"
    importCharacter = "导入角色"
    exportSuccess = "角色已导出"
    importSuccess = "角色已导入"
    importFailed = "导入失败，文件格式不正确"
    exportFailed = "导出失败，请重试"
    shareAsImage = "分享图片"
    saveImage = "保存图片"
    shareAsMarkdown = "分享 Markdown"
    saveMarkdown = "保存 Markdown"
    addModel = "添加模型"
    editModel = "编辑模型"
    modelName = "模型名称"
    modelNameHint = "自定义名称，便于区分（默认与模型 ID 一致）"
    apiKeyLabel = "API Key"
    apiKeyHint = "例如 sk-xxxxxxxx（必填）"
    modelIdLabel = "模型 ID"
    modelIdHint = "例如 deepseek-v4-flash（必填）"
    apiUrlLabel = "API 地址"
    apiUrlHint = "例如 https://api.deepseek.com（必填）"
    modelNote = "备注"
    modelNoteHint = "补充说明（选填）"
    saveModel = "保存"
    deleteModel = "删除"
    unsavedTitle = "未保存更改"
    unsavedMessage = "当前有未保存的更改，确定要放弃吗？"
    userAgreement = "用户协议与免责声明"
    addModelFirst = "请先在设置里添加模型"
    favorites = "收藏"
    favorite = "收藏"
    unfavorite = "取消收藏"
    noFavorites = "还没有收藏的消息"
    favoriteDetail = "收藏详情"
    openOriginal = "跳转到原对话"
    deleteFavoritesWarning = "同时删除本对话收藏的信息"
    confirmUnfavorite = "确定取消收藏这条消息吗？"
    confirmUnfavoriteRun = { "这一段连续收藏共 $it 条，取消后整段都会从收藏里移除，确定吗？" }
    roleUser = "你"
    roleAi = "FreeChat"
    filterAll = "全部对话"
    sortFavoriteTime = "收藏时间"
    sortConvTime = "最近一次对话时间"
    filterByConversation = "筛选对话"
    sortBy = "排序方式"
    account = "账号"
    accountLocalOnly = "不登录也能照常用，登录只是让这些东西在网页端也能看到"
    accountLocalData = { "这台设备上已经有 $it" }
    accountTabLogin = "登录"
    accountTabRegister = "注册"
    accountTabRecover = "找回"
    accountUsername = "用户名"
    accountPassword = "密码"
    accountPasswordConfirm = "再输一次"
    accountOldPassword = "当前密码"
    accountNewPassword = "新密码"
    accountRecoveryCode = "恢复码"
    accountLogin = "登录"
    accountRegister = "注册"
    accountResetPassword = "重设密码"
    accountWorking = "处理中…"
    accountRecoveryTitle = "这是你的恢复码"
    accountRecoveryHint = "只显示这一次，截图或者抄下来。忘了密码时只有它能把账号找回来。"
    accountCopy = "复制"
    accountCopied = "已复制"
    accountSavedIt = "我存好了"
    accountSignedInAs = "已登录"
    accountUsage = "云端占用"
    accountSyncNow = "立即同步"
    accountSyncIdle = "已同步"
    accountSyncSyncing = "同步中…"
    accountSyncOff = "未开启"
    accountSyncError = "同步出错"
    accountSyncNever = "还没同步过"
    accountSyncPending = { "还有 $it 项等着上传" }
    accountSyncLast = { "上次同步 $it" }
    accountDevices = "登录中的设备"
    accountRevoke = "退出该设备"
    accountChangePassword = "修改密码"
    accountRegenerate = "换一个恢复码"
    accountLogout = "退出登录"
    accountLogoutHint = "本地数据留在设备上，下次登录接着同步"
    accountDelete = "注销账号"
    accountDeleteWarn = "云端存的对话、消息、记忆会全部删掉，找不回来。这台设备上的本地数据不受影响。"
    accountDeleteConfirm = "输入密码确认"
    accountErrFillAll = "用户名和密码都要填"
    accountErrMismatch = "两次密码不一样"
    accountErrPasswordShort = "密码至少 8 位"
    accountErrNetwork = "连不上服务器，检查一下网络"
    accountNotices = "同步提示"
    accountPasswordChanged = "密码已修改"
    accountCodeRegenerated = "新的恢复码已生成，同样只显示这一次"
    avatarCropHint = "拖动调整位置，双指缩放"
    accountChangeAvatar = "更换头像"
    accountUploadAvatar = "上传头像"
    accountRemoveAvatar = "移除头像"
    accountChangeUsername = "修改用户名"
    accountUsernameRule = "3–20 位字母、数字或下划线"
    accountUsernameChanged = "用户名已修改"
    accountAvatarChanged = "头像已更新"
    accountAvatarRemoved = "头像已移除"
    accountAvatarFailed = "头像没能更新，请再试一次"
    avatarCropFailed = "这张图读不出来，换一张试试"
    accountErrUsername = { rule -> "用户名只能是$rule" }
    claudeAssistantName = "Claude风格助理"
    convRules = "规则"
    convRulesDesc = "只在当前对话生效的系统级提示词 —— 两种模式都遵守"
    convRulesHint = "比如：只回英文；每次先给结论再解释"
    genFailed = "生成失败"
    accountEditAvatar = "编辑头像"

    collapseInput = "缩回"
    fullscreenInputting = "全屏输入中..."
    stopAction = "停止"
    holdToTalk = "按住说话"
    sendAction = "发送"
    imageCountLabel = { n, max -> "图片 $n/$max" }
    fileCountLabel = { n, max -> "文件 $n/$max" }
    removeAction = "移除"
    quoteAction = "引用"
    cancelQuote = "取消引用"
    quotedImage = "引用图片"
    imageLabel = "图片"
    fileLabel = "文件"
    docLabel = "文档"
    releaseToCancel = "松开取消"
    attachmentLabel = "附件"
    tapToOpenEdit = "点击打开编辑"
    tapToOpen = "点击打开"
    imageSavedTo = { "图片已保存到 $it" }
    storageSaveFailed = "保存失败，请检查存储空间"
    saveFailedWith = { "保存失败：$it" }
    downloadFailedWith = { "下载失败：$it" }
    imageMissing = "图片不存在或已被删除"
    fileMissing = "文件不存在或已被删除"
    openFileFailed = { "无法打开文件：$it" }
    shareTooMuch = "选中的内容太多，长图会过大，请分几次分享"
    imageTag = "[图片]"
    fileTag = "[文件]"
    shareImageTooLarge = "图片过大，生成失败，请少选几条再试"
    shareFailed = "分享失败"
    savedOk = "已保存"
    saveFailedShort = "保存失败"
    noAsrModelTitle = "未添加语音识别模型"
    noAsrModelDesc = "请先添加语音识别模型后再使用语音输入。"
    goAddAction = "去添加"
    searchAction = "搜索"
    clearAction = "清除"
    notAdded = "未添加"
    notSelected = "未选择"
    emptyMessage = "（空消息）"
    settingsSlogan = "永远相信美好的事情即将发生"
    welcomePrompt = "想聊点什么？"
    dateMd = "M月d日"
    dateYmd = "yyyy年M月d日"
    dateMdTime = "M月d日 HH:mm"
    dateYmdTime = "yyyy年M月d日 HH:mm"

    syncLoginExpired = "登录已失效，请重新登录"
    syncFailed = "同步失败"
    syncConflictCopySaved = { "角色设定在两台设备上改得不一样，这边那份已另存为「$it」" }
    syncObjectTooLarge = { "「$it」太大或云端空间已满，这一条先不同步了" }
    syncResurrected = { "「$it」在另一台设备上删过，这边的改动又把它救回来了" }
    syncKindConv = "一条对话"
    syncKindMsgs = "一个对话的消息"
    syncKindMems = "一个对话的记忆"
    syncKindSettings = "设置"
    notLoggedIn = "还没登录"
    localDataSummary = { c, m -> "$c 条对话 · $m 条消息" }
    networkUnreachable = "连不上服务器，检查一下网络"
    requestFailedWith = { "请求失败（$it）" }
    conflictCopySuffix = "（冲突副本）"
    shareAction = "分享"
    legacyBinaryFile = "（旧版二进制格式，暂不支持直接解析，请另存为 docx/xlsx/pptx 后重试）"
    readFileFailed = { "（读取文件失败：$it）" }
    pageMarker = { "【第 $it 页】" }
    channelProactive = "主动智能"
    channelProactiveDesc = "TA 主动找你时在后台生成内容"
    proactiveThinking = "正在想你的事…"
    channelKeepAlive = "后台保活"
    channelKeepAliveDesc = "AI 后台思考时保持进程存活"
    channelReply = "消息回复"
    channelReplyDesc = "AI 回复通知"
    backgroundRunning = "后台运行中"
    roleLearningRejected = "角色学习未完成：设定可能含敏感内容，被模型拒绝"
    webQuotaExhausted = { n, at -> "联网搜索额度已用完（$n 个账号共 ${n * 250} 次/月），$at 自动重置" }
    webQuotaExhaustedNoDate = "联网搜索额度已用完，暂时无法获取实时信息"
    webQuotaRemaining = { n, acc -> "剩余 $n 次（$acc 个账号）" }
    webQuotaNoKey = "未配置密钥"
    webQuotaUnknown = "额度未知"
    webQuotaCountUnknown = "未知"
    webQuotaCount = { "$it 次" }
    webNoKeyConfigured = "未配置联网搜索密钥"
    webRequestFailed = { "联网搜索请求失败：$it" }
    webTooFrequent = "联网搜索太频繁，请稍后再试"
    webUnavailable = "联网搜索暂不可用"
    notSet = "未设置"
    followGlobal = "跟随全局"
    followGlobalDefault = "跟随全局（默认）"
    listSeparator = "、"
    genderKeys = listOf("男", "女")
    genderLabels = listOf("男", "女")
    personalityPresetLabels = listOf("温柔随和", "理性冷静", "幽默搞怪", "直率犀利")
    relationshipPresetLabels = listOf("女朋友", "男朋友", "闺蜜", "朋友", "同学", "同事", "网友", "助理", "老师", "学生", "兄弟", "陌生人")
    builtInModelDesc = { id ->
        when (id) {
            "mimo-v2.5-pro" -> "Xiaomi 深度推理模型，作者自用 API，不保证随时在线。"
            "ep-20260629143810-ffvjl" -> "Volcano Engine 轻量级生图模型，作者自用 API，不保证随时在线。"
            "MiMo-V2.5-TTS" -> "小米语音合成模型，作者自用 API，不保证随时在线。"
            else -> ""
        }
    }

    agreementTitle = "用户协议与使用须知"
    agreementSections = listOf(
        AgreementSectionText("一、关于本软件", listOf(
            "FreeChat 是一个开源的 AI 聊天客户端。它本身**不提供任何 AI 模型服务**，也不承诺功能永久可用。",
            "软件按**「现状」**提供，作者会尽力维护，但无法保证没有缺陷、不会中断。"
        )),
        AgreementSectionText("二、模型需要自备", listOf(
            "除作者内置的少量模型外，AI 能力都需要你自行配置第三方 API Key。第三方服务的可用性、价格与内容政策由服务商决定，与作者无关。",
            "内置模型是作者本人在用的 API，**不保证随时有额度**，可能随时失效。"
        )),
        AgreementSectionText("三、账号与服务器（可选）", listOf(
            "你**可以不注册**。不注册时所有功能都能正常使用，聊天记录也只保存在这台设备上。",
            "如果你选择注册，服务器只是提供一份方便：换设备时能接着聊，手机上聊的能在网页上看到。服务器只保存你同步过去的文字内容，不会读取、也不会用作他用。",
            "你为自建模型填写的 API Key 会在**你的设备上加密后**才上传，服务器存的是密文，**作者也无法解开**。但账号不是保险箱，请不要把服务器当作唯一的备份。"
        )),
        AgreementSectionText("四、你的责任", listOf(
            "所有通过本软件发出的请求，都视为**你本人的行为**。你需要对输入与输出的内容负责，包括：",
            "· 确保输入内容不违反法律法规",
            "· 自行判断输出内容的合法性与准确性",
            "· 不利用本软件生成、传播违法或不良信息",
            "AI 的输出可能出错，请自行判断，不要当作专业意见。"
        )),
        AgreementSectionText("五、未成年人", listOf(
            "若你未满 18 周岁，请在监护人陪同下使用本软件。"
        )),
        AgreementSectionText("六、开源声明", listOf(
            "本项目基于 MIT License 开源，你可以自由使用、修改、分发，但需保留原始版权声明。"
        )),
        AgreementSectionText("七、责任范围", listOf(
            "若你将本软件用于违法用途，作者**不承担连带责任**。因使用本软件产生的其他直接或间接后果，作者亦不作出超出法律要求的承诺。"
        ))
    )
    agreementGateHead = "使用前请阅读："
    agreementGateItems = listOf(
        "1. FreeChat 是开源客户端，不提供 API 服务，需要自行配置模型。",
        "2. 内置模型是作者自用的 API，不保证随时有额度。",
        "3. 不注册也能完整使用；注册账号只是方便换设备同步，服务器只保存你同步的文字，自建模型的 Key 会在本地加密后才上传。",
        "4. 请勿使用本软件生成、传播违法违规内容。",
        "5. 本软件仅供学习研究使用，商用请自行评估法律风险。"
    )
    agreementAgreePrefix = "我已阅读并同意"
    agreementDocName = "《用户协议》"
    agreementContinue = "确定并继续"
    agreementReleasePage = "6. 发布页："
    changelogEntries = com.freechat.model.ChangelogData.entries

    // ===== 关于作者 =====
    // 这一页是「作者本人回答你」，语气按真人说话写，不做简报腔。
    authorAboutTitle = "关于作者"
    authorPageTitle = "你想知道什么?"
    authorQa = listOf(
        AuthorQa("叫什么？", "Belate / 胡勃阳"),
        AuthorQa("男女？", "男"),
        AuthorQa("几岁？", "刚 20"),
        AuthorQa(
            "为何做 Web 版？",
            "因为有些用户用的是 iOS 设备。苹果对个人开发者不开放，编译环境又强绑 Mac，" +
                "而我的资金有限 —— 这条路我走不起，所以做了网页版。"
        ),
        AuthorQa(
            "为何做服务器？",
            "网页不存记录，关掉浏览器就什么都没了，所以需要云存储；后来为了数据安全，又做了账号系统。" +
                "安卓端不受影响：不强制注册，也能当成一个免费的、开源的本机 App 用。"
        )
    )
    authorWebAddress = "官方网页："
    authorWebNote = "域名还没过审，直接用 IP —— 无毒无公害。"
    authorGithubRepo = "GitHub 仓库："
    authorGithubNote = "觉得不错的话，点个 Star。"
    authorContact = "任何建议和意见，直接与我沟通："
    authorQrHint = "长按二维码可保存到相册"
    saveQr = "保存二维码"
    saveToGallery = "保存到相册"
}

// ========== 繁體中文 ==========
val ZhTW = AppStrings("zh-TW").apply {
    newChat = "新對話"
    newRules = "調節"
    newRulesTitle = "新規則"
    searchHistory = "搜尋歷史記錄..."
    noConversations = "暫無歷史對話"
    noSearchResults = "未找到匹配的對話"
    settings = "設定"
    renameConversation = "重新命名對話"
    deleteConversation = "刪除對話"
    confirmDeleteConv = { "確定要刪除「$it」嗎？刪除後無法復原。" }
    confirmDeleteConvs = { "確定要刪除選中的 $it 個對話嗎？每個對話的聊天記錄都會一併刪除，刪除後無法復原。" }
    deleteWarning = "刪除後無法復原"
    delete = "刪除"
    cancel = "取消"
    confirm = "確定"
    pinConversation = "置頂"
    unpinConversation = "取消置頂"
    moreActions = "更多操作"
    groupPinned = "置頂"
    groupToday = "今天"
    groupYesterday = "昨天"
    groupWithin7Days = "7天內"
    groupWithin30Days = "30天內"
    groupEarlier = "更早之前"
    inputPlaceholder = "想聊點什麼？"
    thinking = "思考中"
    thinkingProcess = "思考過程"
    expand = "展開"
    collapse = "收合"
    apiError = "請求失敗，請稍後嘗試..."
    imageGenerated = "已為你生成圖片："
    generatingImage = "圖片生成中..."
    copyMessage = "複製"
    deleteMessage = "刪除"
    regenerate = "重新生成"
    deleteMessageConfirm = "確定刪除這條訊息嗎？"
    copied = "已複製"
    stopGenerating = "停止生成"
    addImage = "添加圖片"
    imagePreview = "圖片預覽"
    downloadImage = "下載圖片"
    imageDownloaded = "圖片已儲存"
    uploadImage = "上傳圖片"
    uploadFile = "上傳檔案"
    editMessage = "編輯訊息"
    editMessageHint = "輸入新的內容…"
    editingPromptHint = "正在改寫這條提示詞"
    selectedCount = { "已選擇 $it 項" }
    batchDeleteConfirm = "確定刪除選中的訊息嗎？此操作不可撤銷。"
    mbtiIncompleteTitle = "MBTI 未完整"
    mbtiIncompleteMessage = "MBTI 要麼四個維度都選，要麼都不選，不能只選一部分。"
    languageModel = "語言模型"
    visualModel = "視覺模型"
    visionModel = "識圖模型"
    selectLanguageModel = "選擇語言模型"
    selectVisualModel = "選擇視覺模型"
    selectVisionModel = "選擇識圖模型"
    webSearch = "聯網搜尋"
    webSearchOn = "獲取即時資訊"
    webSearchOff = "已關閉"
    webSearchUnsupported = "當前模型不支援聯網搜尋"
    showThinking = "顯示思考過程"
    showThinkingDesc = "展示AI推理過程"
    autoSummarizeMemory = "自動總結對話以鞏固AI記憶"
    autoSummarizeMemoryDesc = "每輪對話自動提煉重點寫入記憶，回覆時結合記憶提升精準度"
    useSystemFont = "使用系統字體"
    useSystemFontDesc = "勾選後聊天文字使用系統默認字體"
    globalMemory = "全局記憶"
    globalMemoryDesc = "告訴 AI 你的基本資訊和回覆要求，回答時自動參考"
    globalMemoryEmpty = "暫無記憶點，在下方輸入後點擊添加"
    globalMemoryHint = "例如：我叫小王，喜歡簡潔的回答"
    globalMemoryAdd = "添加"
    thinkingUnsupported = "不支援顯示思考過程"
    closeWebSearchTitle = "關閉聯網搜尋？"
    closeWebSearchDesc = "關閉後 AI 將無法獲取即時資訊，回覆可能基於過時資料。建議保持開啟以獲得更好的體驗。"
    stillClose = "仍然關閉"
    stillEnable = "仍然開啟"
    heavyWarnHead = "該功能會"
    heavyWarnRed = "大幅提高token消耗、增加回應時長"
    heavyWarnTail = "，確認開啟？"
    keepOn = "保持開啟"
    replyTemp = "回覆溫度"
    replyLength = "回覆長度"
    theme = "主題"
    themeMode = "主題模式"
    language = "語言"
    selectLanguage = "選擇語言"
    version = "版本"
    changelog = "更新日誌"
    api = "模型"
    author = "作者"
    close = "關閉"
    sectionModels = "模型選擇"
    sectionAiOptimize = "AI系統優化"
    sectionSystemTheme = "系統主題"
    sectionGeneral = "通用"
    sectionAbout = "關於"
    aiMemory = "AI記憶"
    memorySummary = "記憶總結"
    memorySummaryDesc = "提煉對話內容寫入固定檔案，提升AI長久記憶力"
    font = "字體"
    fontOptimize = "字體優化"
    fontOptimizeDesc = "使用內置字體優化排版，提升文字可讀性"
    advancedMaterial = "高級材質"
    advancedMaterialDesc = "增加模糊等渲染效果，提升質感"
    systemDarkTheme = "系統暗色主題"
    liquidBackdrop = "流光炫彩"
    liquidBackdropDesc = "柔光漸變背景，提升觀感"
    imageGenModel = "生圖模型"
    tempAuto = "自動"
    tempAutoDesc = "平衡情感與邏輯"
    tempWarm = "熱情"
    tempWarmDesc = "注重情感共情，溫暖回應"
    tempObjective = "客觀"
    tempObjectiveDesc = "注重邏輯嚴謹，理性分析"
    lengthAuto = "自動"
    lengthAutoDesc = "智慧平衡回覆長度"
    lengthFull = "完整"
    lengthFullDesc = "完整詳細，步驟清晰"
    lengthConcise = "精闢"
    lengthConciseDesc = "精闢犀利，一語中的"
    themeSystem = "跟隨系統"
    themeSystemDesc = "自動跟隨系統"
    themeLight = "淺色"
    themeLightDesc = "始終淺色"
    themeDark = "深色"
    themeDarkDesc = "始終深色"
    themeDarkOled = "黑色"
    themeDarkOledDesc = "始終黑色"
    colorTheme = "色彩"
    colorThemeBrown = "莫蘭迪暖棕"
    colorThemeBlue = "莫蘭迪淺藍"
    colorThemeWhite = "純白"
    langSystem = "跟隨系統"
    langSystemDesc = "自動跟隨系統語言"
    langZhCN = "簡體中文"
    langZhTW = "繁體中文"
    langEn = "English"
    fontSize = "字體大小"
    fontSizeSmall = "小"
    fontSizeMedium = "標準"
    fontSizeLarge = "較大"
    fontSizeXlarge = "大"
    fontSizeXxlarge = "特大"
    voiceModel = "語音模型"
    selectVoiceModel = "選擇語音模型"
    voiceTtsLabel = "語音合成模型"
    voiceAsrLabel = "語音辨識模型"
    voiceTtsDesc = "語音合成 · 朗讀 AI 回覆"
    voiceAsrDesc = "語音識別 · 語音輸入轉文字"
    voiceTtsModelDesc = "Xiaomi語音合成模型"
    voiceAsrModelDesc = "Xiaomi語音識別模型"
    voiceDebug = "語音調試"
    aiVoice = "AI語音"
    voiceAutoPlay = "自動朗讀"
    voiceAutoPlayDesc = "AI 回覆完成後自動朗讀全文"
    voiceTone = "音色"
    voiceSpeed = "語速"
    voicePitch = "音調"
    voiceDefault = "預設"
    speak = "朗讀"
    stopSpeaking = "停止朗讀"
    pause = "暫停"
    resume = "繼續播放"
    greetingsLateNight = listOf(
        "夜深了，還沒睡呀～", "凌晨了，在想什麼呢？",
        "很晚了，早點休息呀！", "睡不著的話，我陪你說說話～"
    )
    greetingsEarlyMorning = listOf(
        "清晨好！新的一天開始了～", "早安！今天有什麼計劃嗎？",
        "晨光正好，想聊點什麼？", "起得真早，今天是好日子！"
    )
    greetingsMorningWeekday = listOf(
        "上午好！想聊點什麼？", "早上好呀，今天想探索什麼？", "精力充沛的上午，開始吧！"
    )
    greetingsMorningWeekend = listOf(
        "週末早上好呀～今天不用早起真好", "懶洋洋的週末早晨，想聊點什麼？", "週末早安！享受悠閒時光吧～"
    )
    greetingsNoon = listOf(
        "中午好！吃過了嗎？", "午安～休息一下，聊聊天吧", "正午時分，有什麼想聊的？"
    )
    greetingsAfternoon = listOf(
        "下午好！今天過得怎麼樣？", "午後時光，想聊點什麼？",
        "下午好呀，我在呢～", "陽光正好，來聊會兒吧"
    )
    greetingsEvening = listOf(
        "晚上好！今天辛苦啦～", "晚間時光，放鬆一下？",
        "夜幕降臨，聊點什麼呢？", "晚上好，今天想聊什麼？"
    )
    greetingsNight = listOf(
        "夜深了，早點休息呀！", "還在忙嗎？別忘了休息～",
        "夜裡安靜，適合思考呢", "這麼晚了，我還在哦～"
    )
    chatMode = "聊天模式"
    chatModeDesc = "切換 AI 回覆風格"
    standardMode = "標準問答"
    companionMode = "擬人陪伴"
    companionModeDesc = "像真人朋友一樣陪你聊天"
    chooseChatMode = "選擇聊天模式"
    characterSetup = "模擬設置"
    characterName = "角色名稱"
    characterNameHint = "用於標識該角色的名稱（必填）"
    mbtiType = "MBTI 類型"
    personality = "人物性格"
    personalityHint = "描述角色的性格特徵（可選）"
    personalityPresetLabel = "預設"
    memoryPerception = "記憶感知"
    memoryPerceptionHint = "補充你的個人情況與前提故事（可選）"
    supportingCast = "配角"
    supportingCastHint = "補充故事中的其他人物，含身分、關係與背景（可選）"
    worldRules = "規則"
    worldRulesHint = "補充世界觀、故事框架與既有設定（可選）"
    userPersona = "用戶形象"
    userPersonaHint = "填寫你在此設定中的身分與背景，AI 將據此演繹你的言行（可選）"
    friendTyping = "正在輸入中..."
    companionPlaceholder = "和朋友聊天..."
    companionSwitch = "擬人模式"
    startChat = "開始聊天"
    basicInfo = "基本資訊"
    gender = "性別"
    age = "年齡"
    ageHint = "填寫年齡"
    referencePrototypeLabel = "參考原型"
    referencePrototypeHint = "作為參考的角色名或出處（可選）"
    referencePrototypeDesc = "AI 先學習你撰寫的人物設定，再以該角色的既有設定補充完善。你的設定優先，衝突時以你的設定為準；僅當你寫的人物設定過少時才聯網搜尋補齊。"
    replyBuffer = "回覆緩衝"
    replyBufferDesc = "連續傳送多條訊息時，TA 會等待該時長後合併回覆（1-6 秒）"
    replyBufferEnable = "開啟回覆緩衝"
    openingLine = "開場白"
    openingLineHint = "角色的開場台詞（可選）"
    saveSettings = "儲存設定"
    unsavedBackTitle = "未儲存"
    unsavedBackMessage = "確定返回嗎？你的修改不會被儲存。"
    discard = "確定返回"
    learningPersonaTitle = "AI 正在學習人物設定"
    learningPersonaDesc = "正在深度理解並還原 TA 的性格、經歷與說話方式…"
    learningPersonaLongWait = "AI 正在深度理解人物設定，請耐心等待一會兒…"
    appearance = "人物形象"
    appearanceHint = "描述角色的外貌與體態（可選）"
    appearanceImage = "形象參考圖"
    emoji = "表情"
    relationship = "人物關係"
    relationshipPresetLabel = "預設（僅作大致方向參考，關係隨聊天自然變化）"
    relationshipHint = "補充與角色的具體關係（可選）"
    relationshipReadonlyHint = "關係隨你們的聊天劇情自然演進，無法手動修改"
    openingLineAdd = "再添一句開場白"
    dialogueMode = "對話模式"
    dialogueModeDesc = "決定 AI 的回覆形式；建立時選定，發出第一條訊息後不可更改"
    dialogueModeRequiredTitle = "還沒選對話模式"
    dialogueModeRequiredDesc = "先給這個角色選一種寫法：微信聊天 / 動作演繹 / 劇情補足。它決定 AI 之後每一條回覆的格式，建立之後就改不了了。"
    dialogueModeLockedTitle = "對話模式已鎖定"
    dialogueModeLockedDesc = "對話已經開始，上下文會導致 AI 學習對話模式失敗，故無法再修改對話模式，如想改變對話模式，請匯出角色後匯入新建一個。"
    dialogueModeLockedHint = "對話模式發出第一條訊息後就鎖定了；要改請匯出角色後匯入新建。"
    dialogueModePickWarn = "建立並對話後無法修改對話模式，請確定選擇。"
    dialogueModeWechat = "微信聊天"
    dialogueModeWechatDesc = "模擬即時通訊，一問一答、逐條回覆"
    dialogueModeAction = "動作演繹"
    dialogueModeActionDesc = "以該角色的語言、動作、神態、心理為主，必要時可帶少量第三方角色的言行，不寫旁白"
    dialogueModePlot = "劇情補足"
    dialogueModePlotDesc = "完整的小說正文：環境、旁白、記敘、心理與神態，所有人物用名字或第三人稱，台詞用雙引號"
    plotLength = "單次回覆長度"
    plotLengthShort = "短"
    plotLengthMid = "中"
    plotLengthLong = "長"
    plotLengthExtraLong = "超長"
    plotExampleTitle = "發送示例"
    plotExampleUser = "我推開酒吧的門，雨夜的冷風灌進來，一眼就看到了坐在角落裡的你。"
    plotExampleAi = "雨點敲在玻璃窗上，像誰在輕輕叩門。林晚把銀匙擱回碟邊，金屬碰上瓷器，叮的一聲，很輕，卻被這間沒幾個人的酒吧襯得格外清楚。門口那陣冷風先一步湧了進來，捲著潮濕的柏油氣味，然後她才看見他——傘沿還在滴水，肩膀上落了一層細密的水珠。她把杯子往自己這邊挪了挪，騰出半張桌子，聲音輕得幾乎化進雨聲裡：“我還以為你不會來了。”"
    actionExampleUser = "我把傘收起來靠在門邊，抖了抖袖子上的水，問你今天怎麼一個人坐在這兒。"
    actionExampleAi = "我把涼透的咖啡往旁邊推了推，抬頭看你。「等你。」說完自己先愣了一下，指尖在杯沿上轉了一圈，「……也沒等多久。」"
    wechatExampleUser = listOf("在嗎", "今天忙不忙", "晚上一起吃飯？")
    wechatExampleAi = listOf("在的", "剛忙完，還好", "好啊，幾點？")
    chatExampleYou = "你"
    chatExampleAi = "AI"
    highQualityMemory = "增強檢索"
    highQualityMemoryDesc = "無縮略全文閱讀思考，顯著提升AI記憶力與邏輯能力"
    deepThinking = "深度推演"
    deepThinkingDesc = "流程化審問，輸出前確認記憶與邏輯，進一步提升回覆質量"
    aiCreativity = "AI創造力"
    aiCreativityDesc = "AI對劇情發展的主動影響程度\n" +
        "<4 ：AI回覆緊貼使用者提示詞，基本不主動創造新劇情。\n" +
        "4-6：AI回覆在角色設定與劇情發展基礎之上，創造一定的新劇情走向。\n" +
        "6-8：AI回覆積極地建立新內容、新事件、新角色、新場景等。\n" +
        ">8 ：AI回覆腦迴路新奇，可能脫離常規邏輯，難以理解。"
    aiCreativityHint = "建議 3-7"
    paste = "貼上"
    fullscreenInput = "全螢幕輸入"
    sleepSimulation = "作息模擬"
    sleepSimulationDesc = "AI 擁有獨立作息，睡眠時段不回覆，醒後自然說明"
    proactive = "主動智能"
    proactiveDesc = "本地計時延長 API 快取，測試階段效果不確定，可能會大幅增加 token 消耗。"
    proactiveExactHint = "尚未授予「鬧鐘與提醒」權限：到點可能延遲數分鐘。點擊前往授權（不影響功能，僅影響準時程度）"
    betaTag = "Beta"
    edit = "編輯"
    save = "保存"
    editConfirmTitle = "確定啟動編輯"
    editConfirmMessage = "編輯模式下可修改人物設定，確定繼續？"
    saveConfirmTitle = "確認保存"
    saveConfirmMessage = "保存後將更新角色設定，確定保存？"
    unsavedChangesTitle = "更改未保存"
    unsavedChangesMessage = "有更改尚未保存，確定放棄並退出編輯？"
    discardChanges = "放棄更改"
    avatar = "頭像"
    viewAvatar = "查看頭像"
    editAvatar = "編輯頭像"
    changeAvatar = "更換頭像"
    updatingPersonaTitle = "AI 更新理解中"
    updatingPersonaDesc = "正在根據你的修改更新角色形象…"
    sectionPersona = "人物設定"
    sectionSimulation = "模擬設置"
    share = "分享"
    exportCharacter = "匯出角色"
    importCharacter = "匯入角色"
    exportSuccess = "角色已匯出"
    importSuccess = "角色已匯入"
    importFailed = "匯入失敗，檔案格式不正確"
    exportFailed = "匯出失敗，請重試"
    shareAsImage = "分享圖片"
    saveImage = "儲存圖片"
    shareAsMarkdown = "分享 Markdown"
    saveMarkdown = "儲存 Markdown"
    addModel = "新增模型"
    editModel = "編輯模型"
    modelName = "模型名稱"
    modelNameHint = "自訂名稱，便於區分（預設與模型 ID 一致）"
    apiKeyLabel = "API Key"
    apiKeyHint = "例如 sk-xxxxxxxx（必填）"
    modelIdLabel = "模型 ID"
    modelIdHint = "例如 deepseek-v4-flash（必填）"
    apiUrlLabel = "API 位址"
    apiUrlHint = "例如 https://api.deepseek.com（必填）"
    modelNote = "備註"
    modelNoteHint = "補充說明（選填）"
    saveModel = "儲存"
    deleteModel = "刪除"
    unsavedTitle = "未儲存變更"
    unsavedMessage = "目前有未儲存的變更，確定要放棄嗎？"
    userAgreement = "使用者協議與免責聲明"
    addModelFirst = "請先在設定裡新增模型"
    favorites = "收藏"
    favorite = "收藏"
    unfavorite = "取消收藏"
    noFavorites = "還沒有收藏的訊息"
    favoriteDetail = "收藏詳情"
    openOriginal = "跳轉到原對話"
    deleteFavoritesWarning = "同時刪除本對話收藏的訊息"
    confirmUnfavorite = "確定取消收藏這則訊息嗎？"
    confirmUnfavoriteRun = { "這一段連續收藏共 $it 則，取消後整段都會從收藏中移除，確定嗎？" }
    roleUser = "你"
    roleAi = "FreeChat"
    filterAll = "全部對話"
    sortFavoriteTime = "收藏時間"
    sortConvTime = "最近一次對話時間"
    filterByConversation = "篩選對話"
    sortBy = "排序方式"
    account = "帳號"
    accountLocalOnly = "不登入也能照常用，登入只是讓這些東西在網頁端也看得到"
    accountLocalData = { "這台裝置上已經有 $it" }
    accountTabLogin = "登入"
    accountTabRegister = "註冊"
    accountTabRecover = "找回"
    accountUsername = "使用者名稱"
    accountPassword = "密碼"
    accountPasswordConfirm = "再輸入一次"
    accountOldPassword = "目前密碼"
    accountNewPassword = "新密碼"
    accountRecoveryCode = "救援碼"
    accountLogin = "登入"
    accountRegister = "註冊"
    accountResetPassword = "重設密碼"
    accountWorking = "處理中…"
    accountRecoveryTitle = "這是你的救援碼"
    accountRecoveryHint = "只顯示這一次，截圖或抄下來。忘記密碼時只有它能救回帳號。"
    accountCopy = "複製"
    accountCopied = "已複製"
    accountSavedIt = "我存好了"
    accountSignedInAs = "已登入"
    accountUsage = "雲端使用量"
    accountSyncNow = "立即同步"
    accountSyncIdle = "已同步"
    accountSyncSyncing = "同步中…"
    accountSyncOff = "未開啟"
    accountSyncError = "同步發生錯誤"
    accountSyncNever = "還沒同步過"
    accountSyncPending = { "還有 $it 項待上傳" }
    accountSyncLast = { "上次同步 $it" }
    accountDevices = "登入中的裝置"
    accountRevoke = "登出該裝置"
    accountChangePassword = "修改密碼"
    accountRegenerate = "換一組救援碼"
    accountLogout = "登出"
    accountLogoutHint = "本機資料留在裝置上，下次登入接著同步"
    accountDelete = "刪除帳號"
    accountDeleteWarn = "雲端的對話、訊息、記憶會全部刪除，無法復原。這台裝置上的本機資料不受影響。"
    accountDeleteConfirm = "輸入密碼確認"
    accountErrFillAll = "使用者名稱和密碼都要填"
    accountErrMismatch = "兩次密碼不一樣"
    accountErrPasswordShort = "密碼至少 8 位"
    accountErrNetwork = "連不上伺服器，檢查一下網路"
    accountNotices = "同步提示"
    accountPasswordChanged = "密碼已修改"
    accountCodeRegenerated = "新的救援碼已產生，同樣只顯示這一次"
    avatarCropHint = "拖曳調整位置，雙指縮放"
    accountChangeAvatar = "更換頭像"
    accountUploadAvatar = "上傳頭像"
    accountRemoveAvatar = "移除頭像"
    accountChangeUsername = "修改使用者名稱"
    accountUsernameRule = "3–20 位字母、數字或底線"
    accountUsernameChanged = "使用者名稱已修改"
    accountAvatarChanged = "頭像已更新"
    accountAvatarRemoved = "頭像已移除"
    accountAvatarFailed = "頭像沒能更新，請再試一次"
    avatarCropFailed = "這張圖讀不出來，換一張試試"
    accountErrUsername = { rule -> "使用者名稱只能是$rule" }
    claudeAssistantName = "Claude 風格助理"
    convRules = "規則"
    convRulesDesc = "只在目前對話生效的系統級提示詞 —— 兩種模式都遵守"
    convRulesHint = "例如：只回英文；每次先給結論再解釋"
    genFailed = "生成失敗"
    accountEditAvatar = "編輯頭像"

    collapseInput = "收合"
    fullscreenInputting = "全螢幕輸入中..."
    stopAction = "停止"
    holdToTalk = "按住說話"
    sendAction = "傳送"
    imageCountLabel = { n, max -> "圖片 $n/$max" }
    fileCountLabel = { n, max -> "檔案 $n/$max" }
    removeAction = "移除"
    quoteAction = "引用"
    cancelQuote = "取消引用"
    quotedImage = "引用圖片"
    imageLabel = "圖片"
    fileLabel = "檔案"
    docLabel = "文件"
    releaseToCancel = "鬆開取消"
    attachmentLabel = "附件"
    tapToOpenEdit = "點擊開啟編輯"
    tapToOpen = "點擊開啟"
    imageSavedTo = { "圖片已儲存到 $it" }
    storageSaveFailed = "儲存失敗，請檢查儲存空間"
    saveFailedWith = { "儲存失敗：$it" }
    downloadFailedWith = { "下載失敗：$it" }
    imageMissing = "圖片不存在或已被刪除"
    fileMissing = "檔案不存在或已被刪除"
    openFileFailed = { "無法開啟檔案：$it" }
    shareTooMuch = "選取的內容太多，長圖會過大，請分幾次分享"
    imageTag = "[圖片]"
    fileTag = "[檔案]"
    shareImageTooLarge = "圖片過大，產生失敗，請少選幾條再試"
    shareFailed = "分享失敗"
    savedOk = "已儲存"
    saveFailedShort = "儲存失敗"
    noAsrModelTitle = "尚未新增語音辨識模型"
    noAsrModelDesc = "請先新增語音辨識模型後再使用語音輸入。"
    goAddAction = "去新增"
    searchAction = "搜尋"
    clearAction = "清除"
    notAdded = "未新增"
    notSelected = "未選擇"
    emptyMessage = "（空訊息）"
    settingsSlogan = "永遠相信美好的事情即將發生"
    welcomePrompt = "想聊點什麼？"
    dateMd = "M月d日"
    dateYmd = "yyyy年M月d日"
    dateMdTime = "M月d日 HH:mm"
    dateYmdTime = "yyyy年M月d日 HH:mm"

    syncLoginExpired = "登入已失效，請重新登入"
    syncFailed = "同步失敗"
    syncConflictCopySaved = { "角色設定在兩台裝置上改得不一樣，這邊那份已另存為「$it」" }
    syncObjectTooLarge = { "「$it」太大或雲端空間已滿，這一條先不同步了" }
    syncResurrected = { "「$it」在另一台裝置上刪過，這邊的改動又把它救回來了" }
    syncKindConv = "一條對話"
    syncKindMsgs = "一個對話的訊息"
    syncKindMems = "一個對話的記憶"
    syncKindSettings = "設定"
    notLoggedIn = "還沒登入"
    localDataSummary = { c, m -> "$c 條對話 · $m 條訊息" }
    networkUnreachable = "連不上伺服器，檢查一下網路"
    requestFailedWith = { "請求失敗（$it）" }
    conflictCopySuffix = "（衝突副本）"
    shareAction = "分享"
    legacyBinaryFile = "（舊版二進位格式，暫不支援直接解析，請另存為 docx/xlsx/pptx 後重試）"
    readFileFailed = { "（讀取檔案失敗：$it）" }
    pageMarker = { "【第 $it 頁】" }
    channelProactive = "主動智慧"
    channelProactiveDesc = "TA 主動找你時在背景產生內容"
    proactiveThinking = "正在想你的事…"
    channelKeepAlive = "背景保持運作"
    channelKeepAliveDesc = "AI 在背景思考時保持程序存活"
    channelReply = "訊息回覆"
    channelReplyDesc = "AI 回覆通知"
    backgroundRunning = "背景執行中"
    roleLearningRejected = "角色學習未完成：設定可能含敏感內容，被模型拒絕"
    webQuotaExhausted = { n, at -> "聯網搜尋額度已用完（$n 個帳號共 ${n * 250} 次/月），$at 自動重置" }
    webQuotaExhaustedNoDate = "聯網搜尋額度已用完，暫時無法取得即時資訊"
    webQuotaRemaining = { n, acc -> "剩餘 $n 次（$acc 個帳號）" }
    webQuotaNoKey = "未設定金鑰"
    webQuotaUnknown = "額度未知"
    webQuotaCountUnknown = "未知"
    webQuotaCount = { "$it 次" }
    webNoKeyConfigured = "未設定聯網搜尋金鑰"
    webRequestFailed = { "聯網搜尋請求失敗：$it" }
    webTooFrequent = "聯網搜尋過於頻繁，請稍後再試"
    webUnavailable = "聯網搜尋暫時無法使用"
    notSet = "未設定"
    followGlobal = "跟隨全域"
    followGlobalDefault = "跟隨全域（預設）"
    listSeparator = "、"
    genderKeys = listOf("男", "女")
    genderLabels = listOf("男", "女")
    personalityPresetLabels = listOf("溫柔隨和", "理性冷靜", "幽默搞怪", "直率犀利")
    relationshipPresetLabels = listOf("女朋友", "男朋友", "閨蜜", "朋友", "同學", "同事", "網友", "助理", "老師", "學生", "兄弟", "陌生人")
    builtInModelDesc = { id ->
        when (id) {
            "mimo-v2.5-pro" -> "Xiaomi 深度推理模型，作者自用 API，不保證隨時在線。"
            "ep-20260629143810-ffvjl" -> "Volcano Engine 輕量級生圖模型，作者自用 API，不保證隨時在線。"
            "MiMo-V2.5-TTS" -> "小米語音合成模型，作者自用 API，不保證隨時在線。"
            else -> ""
        }
    }

    agreementTitle = "使用者協議與須知"
    agreementSections = listOf(
        AgreementSectionText("一、關於本軟體", listOf(
            "FreeChat 是一個開源的 AI 聊天客戶端。它本身**不提供任何 AI 模型服務**，也不承諾功能永久可用。",
            "軟體按**「現狀」**提供，作者會盡力維護，但無法保證沒有缺陷、不會中斷。"
        )),
        AgreementSectionText("二、模型需自備", listOf(
            "除作者內建的少量模型外，AI 能力都需要你自行設定第三方 API Key。第三方服務的可用性、價格與內容政策由服務商決定，與作者無關。",
            "內建模型是作者本人在用的 API，**不保證隨時有額度**，可能隨時失效。"
        )),
        AgreementSectionText("三、帳號與伺服器（可選）", listOf(
            "你**可以不註冊**。不註冊時所有功能都能正常使用，聊天記錄也只保存在這台裝置上。",
            "如果你選擇註冊，伺服器只是提供一份方便：換裝置時能接著聊，手機上聊的能在網頁上看到。伺服器只保存你同步過去的文字內容，不會讀取、也不會用作他用。",
            "你為自建模型填寫的 API Key 會在**你的裝置上加密後**才上傳，伺服器存的是密文，**作者也無法解開**。但帳號不是保險箱，請不要把手機當作唯一的備份。"
        )),
        AgreementSectionText("四、你的責任", listOf(
            "所有透過本軟體發出的請求，都視為**你本人的行為**。你需要對輸入與輸出的內容負責，包括：",
            "· 確保輸入內容不違反法律法規",
            "· 自行判斷輸出內容的合法性與準確性",
            "· 不利用本軟體生成、傳播違法或不良資訊",
            "AI 的輸出可能出錯，請自行判斷，不要當作專業意見。"
        )),
        AgreementSectionText("五、未成年人", listOf(
            "若你未滿 18 歲，請在監護人陪同下使用本軟體。"
        )),
        AgreementSectionText("六、開源聲明", listOf(
            "本專案基於 MIT License 開源，你可以自由使用、修改、散布，但需保留原始著作權聲明。"
        )),
        AgreementSectionText("七、責任範圍", listOf(
            "若你將本軟體用於違法用途，作者**不承擔連帶責任**。因使用本軟體產生的其他直接或間接後果，作者亦不作出超出法律要求的承諾。"
        ))
    )
    agreementGateHead = "使用前請閱讀："
    agreementGateItems = listOf(
        "1. FreeChat 是開源客戶端，不提供 API 服務，需要自行設定模型。",
        "2. 內建模型是作者自用的 API，不保證隨時有額度。",
        "3. 不註冊也能完整使用；註冊帳號只是方便換裝置同步，伺服器只保存你同步的文字，自建模型的 Key 會在本機加密後才上傳。",
        "4. 請勿使用本軟體生成、傳播違法違規內容。",
        "5. 本軟體僅供學習研究使用，商用請自行評估法律風險。"
    )
    agreementAgreePrefix = "我已閱讀並同意"
    agreementDocName = "《使用者協議》"
    agreementContinue = "確定並繼續"
    agreementReleasePage = "6. 發布頁："

    // ===== 關於作者 =====
    authorAboutTitle = "關於作者"
    authorPageTitle = "你想知道什麼?"
    authorQa = listOf(
        AuthorQa("叫什麼？", "Belate / 胡勃陽"),
        AuthorQa("男女？", "男"),
        AuthorQa("幾歲？", "剛 20"),
        AuthorQa(
            "為何做 Web 版？",
            "因為有些使用者用的是 iOS 裝置。蘋果對個人開發者不開放，編譯環境又強綁 Mac，" +
                "而我的資金有限 —— 這條路我走不起，所以做了網頁版。"
        ),
        AuthorQa(
            "為何做伺服器？",
            "網頁不存記錄，關掉瀏覽器就什麼都沒了，所以需要雲端儲存；後來為了資料安全，又做了帳號系統。" +
                "安卓端不受影響：不強制註冊，也能當成一個免費的、開源的本機 App 用。"
        )
    )
    authorWebAddress = "官方網頁："
    authorWebNote = "網域還沒過審，直接用 IP —— 無毒無害。"
    authorGithubRepo = "GitHub 倉庫："
    authorGithubNote = "覺得不錯的話，點個 Star。"
    authorContact = "任何建議和意見，直接與我溝通："
    authorQrHint = "長按 QR Code 可儲存至相簿"
    saveQr = "儲存 QR Code"
    saveToGallery = "儲存至相簿"
    changelogEntries = listOf(
        ChangelogEntry("Version 1.0.18", "2026-09-10", listOf(
            ChangelogSection("新增功能", listOf(
                "新增 收藏系統，訊息收藏後可統一查看。",
                "新增 角色設定可匯入／匯出，可一鍵分享角色或採用已設定的角色。",
                "新增 訊息分享功能，可以 jpg／Markdown 格式儲存或分享。"
            )),
            ChangelogSection("體驗最佳化", listOf(
                "最佳化 新標題命名邏輯",
                "最佳化 全拼輸入的喚出條件",
                "最佳化 AI 回覆時聊天位置駐停",
                "最佳化 高品質檢索回覆下的角色理解力、AI 推理能力以及記憶力，提升 AI 回覆品質"
            )),
            ChangelogSection("渲染最佳化", listOf(
                "最佳化 表格內部縱向屬性對齊，提升可讀性。",
                "最佳化 統一進階材質下的視覺效果。"
            )),
            ChangelogSection("漏洞修補", listOf(
                "修復 進階材質下標題列無隔擋的 bug",
                "修復 URL 路徑自動補全導致路徑失效的 bug",
                "修復 部分場景下識圖模型失效的 bug"
            ))
        )),
        ChangelogEntry("Version 1.0.01", "2026-09-01", listOf(
            ChangelogSection("新增功能", listOf(
                "新增 高品質回覆功能，開啟後記憶感知由「AI 概括」改為「原文摘錄」，提高優先級，減少 AI 幻覺並提升記憶力。讓使用者可以在「高效能」與「低收費」之間選擇。"
            )),
            ChangelogSection("其他更新", listOf(
                "新增 開源聲明提示。"
            ))
        )),
        ChangelogEntry("Version 1.0", "2026-09-01", listOf(
            ChangelogSection("體驗最佳化", listOf(
                "重編 Debug 轉為 Release，提升回應速度，減少啟動掉幀。",
                "最佳化 內建字型背景預載入，減少渲染卡頓。",
                "最佳化 app 內過度動畫，提升流暢度。"
            )),
            ChangelogSection("漏洞修補", listOf(
                "修復 全螢幕輸入展開後游標位置重置的 bug。"
            ))
        ))
    )
}

// ========== English ==========
val En = AppStrings("en").apply {
    newChat = "New Chat"
    newRules = "Rules"
    newRulesTitle = "New Rules"
    searchHistory = "Search history..."
    noConversations = "No conversations yet"
    noSearchResults = "No matching conversations"
    settings = "Settings"
    renameConversation = "Rename"
    deleteConversation = "Delete Conversation"
    confirmDeleteConv = { "Are you sure you want to delete \"$it\"? This cannot be undone." }
    confirmDeleteConvs = { n -> "Delete the $n selected conversations? Their chat history will be deleted too. This cannot be undone." }
    deleteWarning = "This cannot be undone"
    delete = "Delete"
    cancel = "Cancel"
    confirm = "OK"
    pinConversation = "Pin"
    unpinConversation = "Unpin"
    moreActions = "More actions"
    groupPinned = "Pinned"
    groupToday = "Today"
    groupYesterday = "Yesterday"
    groupWithin7Days = "Last 7 days"
    groupWithin30Days = "Last 30 days"
    groupEarlier = "Earlier"
    inputPlaceholder = "What do you want to talk about?"
    thinking = "Thinking"
    thinkingProcess = "Thinking process"
    expand = "Expand"
    collapse = "Collapse"
    apiError = "Request failed. Please try again later."
    imageGenerated = "Image generated for you:"
    generatingImage = "Generating image..."
    copyMessage = "Copy"
    deleteMessage = "Delete"
    regenerate = "Regenerate"
    deleteMessageConfirm = "Delete this message?"
    copied = "Copied"
    stopGenerating = "Stop"
    addImage = "Add Image"
    imagePreview = "Image Preview"
    downloadImage = "Download"
    imageDownloaded = "Image saved"
    uploadImage = "Upload Image"
    uploadFile = "Upload File"
    editMessage = "Edit Message"
    editMessageHint = "Enter new message…"
    editingPromptHint = "Editing this prompt"
    selectedCount = { n -> if (n == 1) "1 selected" else "$n selected" }
    batchDeleteConfirm = "Delete the selected messages? This cannot be undone."
    mbtiIncompleteTitle = "Incomplete MBTI"
    mbtiIncompleteMessage = "Please select all four MBTI dimensions, or none of them."
    languageModel = "Language Model"
    visualModel = "Visual Model"
    visionModel = "Vision Model"
    selectLanguageModel = "Select Language Model"
    selectVisualModel = "Select Visual Model"
    selectVisionModel = "Select Vision Model"
    webSearch = "Web Search"
    webSearchOn = "Access real-time info"
    webSearchOff = "Disabled"
    webSearchUnsupported = "Current model doesn't support web search"
    showThinking = "Show thinking process"
    showThinkingDesc = "Show AI reasoning"
    autoSummarizeMemory = "Auto-summarize to build AI memory"
    autoSummarizeMemoryDesc = "Summarize each turn into memory for more accurate, personalized replies"
    useSystemFont = "Use system font"
    useSystemFontDesc = "Use the system default font for chat text"
    globalMemory = "Global memory"
    globalMemoryDesc = "Tell the AI your info and reply preferences"
    globalMemoryEmpty = "No memory yet — type below and add"
    globalMemoryHint = "e.g. My name is Alex, prefer concise answers"
    globalMemoryAdd = "Add"
    thinkingUnsupported = "Thinking display not supported"
    closeWebSearchTitle = "Disable Web Search?"
    closeWebSearchDesc = "AI won't be able to access real-time information without web search. We recommend keeping it on for the best experience."
    stillClose = "Disable Anyway"
    stillEnable = "Enable Anyway"
    heavyWarnHead = "This will "
    heavyWarnRed = "sharply increase token usage and response time"
    heavyWarnTail = ". Enable anyway?"
    keepOn = "Keep On"
    replyTemp = "Response Style"
    replyLength = "Response Length"
    theme = "Theme"
    themeMode = "Theme Mode"
    language = "Language"
    selectLanguage = "Select Language"
    version = "Version"
    changelog = "Changelog"
    api = "Models"
    author = "Developer"
    close = "Close"
    sectionModels = "Model Selection"
    sectionAiOptimize = "AI Optimization"
    sectionSystemTheme = "System Theme"
    sectionGeneral = "General"
    sectionAbout = "About"
    aiMemory = "AI Memory"
    memorySummary = "Memory Summary"
    memorySummaryDesc = "Distills the conversation into a fixed file, sharpening the AI's long-term memory"
    font = "Font"
    fontOptimize = "Font Optimization"
    fontOptimizeDesc = "Use the built-in font for better readability"
    advancedMaterial = "Advanced Material"
    advancedMaterialDesc = "Adds blur and other rendering effects for a finer feel"
    systemDarkTheme = "System Dark Theme"
    liquidBackdrop = "Flowing Aurora"
    liquidBackdropDesc = "A soft-light gradient background for a nicer look"
    imageGenModel = "Image Generation"
    tempAuto = "Auto"
    tempAutoDesc = "Balance emotion & logic"
    tempWarm = "Warm"
    tempWarmDesc = "Empathetic & caring"
    tempObjective = "Objective"
    tempObjectiveDesc = "Logical & analytical"
    lengthAuto = "Auto"
    lengthAutoDesc = "Adaptive length"
    lengthFull = "Full"
    lengthFullDesc = "Detailed & thorough"
    lengthConcise = "Concise"
    lengthConciseDesc = "Sharp & to the point"
    themeSystem = "Follow System"
    themeSystemDesc = "Match system setting"
    themeLight = "Light"
    themeLightDesc = "Always light"
    themeDark = "Dark"
    themeDarkDesc = "Always dark"
    themeDarkOled = "Black"
    themeDarkOledDesc = "Always black"
    colorTheme = "Color"
    colorThemeBrown = "Morandi Brown"
    colorThemeBlue = "Morandi Blue"
    colorThemeWhite = "Pure White"
    langSystem = "Follow System"
    langSystemDesc = "Match system language"
    langZhCN = "简体中文"
    langZhTW = "繁體中文"
    langEn = "English"
    fontSize = "Font Size"
    fontSizeSmall = "Small"
    fontSizeMedium = "Default"
    fontSizeLarge = "Large"
    fontSizeXlarge = "Extra Large"
    fontSizeXxlarge = "Huge"
    voiceModel = "Voice Model"
    selectVoiceModel = "Select Voice Model"
    voiceTtsLabel = "Speech Synthesis"
    voiceAsrLabel = "Speech Recognition"
    voiceTtsDesc = "TTS · Read AI replies aloud"
    voiceAsrDesc = "ASR · Speech-to-text input"
    voiceTtsModelDesc = "Xiaomi speech synthesis model"
    voiceAsrModelDesc = "Xiaomi speech recognition model"
    voiceDebug = "Voice Debug"
    aiVoice = "AI Voice"
    voiceAutoPlay = "Auto Read"
    voiceAutoPlayDesc = "Read AI replies aloud automatically"
    voiceTone = "Voice"
    voiceSpeed = "Speed"
    voicePitch = "Pitch"
    voiceDefault = "Default"
    speak = "Read Aloud"
    stopSpeaking = "Stop"
    pause = "Pause"
    resume = "Resume"
    greetingsLateNight = listOf(
        "Late night～ still up?", "Can't sleep? I'm here with you～",
        "Past midnight, time to rest!", "The night is quiet. What's on your mind?"
    )
    greetingsEarlyMorning = listOf(
        "Good morning! A new day begins～", "Rise and shine! Any plans today?",
        "Early morning light~ what shall we chat about?", "You're up early! It's a good day!"
    )
    greetingsMorningWeekday = listOf(
        "Good morning! What shall we explore?", "Morning～ ready to start the day?",
        "A bright morning! Let's begin!"
    )
    greetingsMorningWeekend = listOf(
        "Weekend morning～ sleep in and relax!", "Lazy weekend morning. What's up?",
        "Happy weekend! Enjoy your day～"
    )
    greetingsNoon = listOf(
        "Good afternoon! Had lunch yet?", "Noon break～ let's chat a bit",
        "Midday! What's on your mind?"
    )
    greetingsAfternoon = listOf(
        "Good afternoon! How's your day?", "Afternoon vibes～ I'm here",
        "Sunshine's good, let's chat～", "Afternoon! What are you up to?"
    )
    greetingsEvening = listOf(
        "Good evening! Hope your day went well～", "Evening time～ time to relax?",
        "Night falls. What shall we talk about?", "Good evening! What's new?"
    )
    greetingsNight = listOf(
        "It's late, get some rest!", "Still working? Don't forget to rest～",
        "The quiet of night, good for thinking", "Up late? I'm still here～"
    )
    chatMode = "Chat Mode"
    chatModeDesc = "Switch AI reply style"
    standardMode = "Standard Q&A"
    companionMode = "Companion Chat"
    companionModeDesc = "Chat like a real friend"
    chooseChatMode = "Choose Chat Mode"
    characterSetup = "Simulation Settings"
    characterName = "Character Name"
    characterNameHint = "Name that identifies this character (required)"
    mbtiType = "MBTI Type"
    personality = "Personality"
    personalityHint = "Describe their personality (optional)"
    personalityPresetLabel = "Presets"
    memoryPerception = "Memory"
    memoryPerceptionHint = "Add your background and premise (optional)"
    supportingCast = "Supporting cast"
    supportingCastHint = "Add other characters, with their role, relationships and background (optional)"
    worldRules = "Rules"
    worldRulesHint = "Add the world setting, framework and established rules (optional)"
    userPersona = "About you"
    userPersonaHint = "Describe who you are in this setting — the AI plays you accordingly (optional)"
    friendTyping = "typing..."
    companionPlaceholder = "Chat with a friend..."
    companionSwitch = "Companion Mode"
    startChat = "Start Chatting"
    basicInfo = "Basic Info"
    gender = "Gender"
    age = "Age"
    ageHint = "Enter age"
    referencePrototypeLabel = "Reference character"
    referencePrototypeHint = "Character name or source work to reference (optional)"
    referencePrototypeDesc = "The AI first learns the persona you wrote, then fills the gaps from this character's established canon. Your version wins on conflicts; web search is only used when your persona text is very short."
    replyBuffer = "Reply Buffer"
    replyBufferDesc = "When you send several messages in a row, they wait this long and answer them together (1–6 s)"
    replyBufferEnable = "Enable reply buffer"
    openingLine = "Opening Line"
    openingLineHint = "The character's opening line (optional)"
    saveSettings = "Save"
    unsavedBackTitle = "Unsaved changes"
    unsavedBackMessage = "Go back without saving? Your changes will be lost."
    discard = "Discard"
    learningPersonaTitle = "AI is learning the persona"
    learningPersonaDesc = "Deeply understanding and recreating their personality, backstory and way of speaking…"
    learningPersonaLongWait = "AI is deeply understanding the persona — please wait a moment…"
    appearance = "Appearance"
    appearanceHint = "Describe their appearance and build (optional)"
    appearanceImage = "Reference photo"
    emoji = "Emoji"
    relationship = "Relationship"
    relationshipPresetLabel = "Preset (rough direction only — the relationship evolves naturally)"
    relationshipHint = "Add details of your relationship with them (optional)"
    relationshipReadonlyHint = "The relationship evolves with your story — it can't be edited manually"
    openingLineAdd = "Add another opening line"
    dialogueMode = "Dialogue mode"
    dialogueModeDesc = "How the AI formats its replies. Chosen at creation; locked once the first message is sent."
    dialogueModeRequiredTitle = "Pick a dialogue mode first"
    dialogueModeRequiredDesc = "Choose how this character writes: Chat / In character / Full story. It decides the format of every reply from now on, and can't be changed after creation."
    dialogueModeLockedTitle = "Dialogue mode locked"
    dialogueModeLockedDesc = "The conversation has already started. The existing context would make the AI fail to adopt the dialogue mode, so it can no longer be changed. To use a different mode, export this character, then import it as a new one."
    dialogueModeLockedHint = "The dialogue mode locks once the first message is sent. To change it, export the character and import it as a new one."
    dialogueModePickWarn = "Once you create the character and start chatting, the dialogue mode can no longer be changed. Please confirm your choice."
    dialogueModeWechat = "Chat"
    dialogueModeWechatDesc = "Simulated instant messaging — one message at a time"
    dialogueModeAction = "In character"
    dialogueModeActionDesc = "Mainly this character's own speech, actions, expressions and thoughts; a few lines from other characters when needed — no narration"
    dialogueModePlot = "Full story"
    dialogueModePlotDesc = "Complete novel prose: setting, narration, psychology and expressions; every character by name or third person, dialogue in double quotes"
    plotLength = "Reply length"
    plotLengthShort = "Short"
    plotLengthMid = "Medium"
    plotLengthLong = "Long"
    plotLengthExtraLong = "Extra long"
    plotExampleTitle = "Example"
    plotExampleUser = "I push the bar door open. The cold night wind rushes in, and I spot you in the corner."
    plotExampleAi = "Rain taps the window like someone knocking. Lin Wan sets the spoon back on the saucer — a soft clink against the china, barely there, yet sharp in a bar this empty. The cold wind pushes in ahead of him, smelling of wet asphalt, and only then does she see him: umbrella still dripping, a fine layer of water beading on his shoulders. She slides her glass closer and frees half the table, her voice so light it almost melts into the rain: \"I didn't think you'd come.\""
    actionExampleUser = "I fold my umbrella by the door, shake the rain off my sleeve, and ask why you're sitting here alone."
    actionExampleAi = "I slide the cold coffee aside and look up at you. \"Waiting for you.\" It comes out before I think, so I turn the cup rim under my fingertip and add, \"...Not that long.\""
    wechatExampleUser = listOf("You there?", "Busy today?", "Dinner tonight?")
    wechatExampleAi = listOf("Yep", "Just finished, not too bad", "Sure, what time?")
    chatExampleYou = "You"
    chatExampleAi = "AI"
    highQualityMemory = "Enhanced retrieval"
    highQualityMemoryDesc = "Reads the full text without summarization, markedly improving memory and logic"
    deepThinking = "Deeper reasoning"
    deepThinkingDesc = "A structured interrogation pass that confirms memory and logic before replying, for better answers"
    aiCreativity = "AI Creativity"
    aiCreativityDesc = "How much the AI drives the plot forward\n" +
        "<4 : The AI sticks closely to your prompt and rarely invents plot of its own.\n" +
        "4-6: The AI builds on the character setup and current plot to create some new developments.\n" +
        "6-8: The AI actively creates new content, events, characters and scenes.\n" +
        ">8 : The AI gets wildly inventive — it may leave logic behind and be hard to follow."
    aiCreativityHint = "3-7 recommended"
    paste = "Paste"
    fullscreenInput = "Fullscreen input"
    sleepSimulation = "Sleep schedule"
    sleepSimulationDesc = "The AI keeps its own schedule — no replies while asleep, explained after waking"
    proactive = "Proactive messages"
    proactiveDesc = "A local timer extends the API cache window. Still in testing, so results are uncertain and it may substantially increase token usage."
    proactiveExactHint = "Permission not granted: without \"Alarms & reminders\" messages may arrive several minutes late. Tap to grant — timing only, the feature still works"
    betaTag = "Beta"
    edit = "Edit"
    save = "Save"
    editConfirmTitle = "Start editing"
    editConfirmMessage = "You can modify persona settings in edit mode. Continue?"
    saveConfirmTitle = "Confirm save"
    saveConfirmMessage = "Saving will update the character. Confirm?"
    unsavedChangesTitle = "Unsaved changes"
    unsavedChangesMessage = "You have unsaved changes. Discard and exit editing?"
    discardChanges = "Discard changes"
    avatar = "Avatar"
    viewAvatar = "View avatar"
    editAvatar = "Edit avatar"
    changeAvatar = "Change avatar"
    updatingPersonaTitle = "AI is updating its understanding"
    updatingPersonaDesc = "Updating the character based on your changes…"
    sectionPersona = "Persona"
    sectionSimulation = "Simulation"
    share = "Share"
    exportCharacter = "Export Character"
    importCharacter = "Import Character"
    exportSuccess = "Character exported"
    importSuccess = "Character imported"
    importFailed = "Import failed, invalid file format"
    exportFailed = "Export failed, please retry"
    shareAsImage = "Share as Image"
    saveImage = "Save Image"
    shareAsMarkdown = "Share Markdown"
    saveMarkdown = "Save Markdown"
    addModel = "Add Model"
    editModel = "Edit Model"
    modelName = "Model Name"
    modelNameHint = "Custom name (defaults to model ID)"
    apiKeyLabel = "API Key"
    apiKeyHint = "e.g. sk-xxxxxxxx (required)"
    modelIdLabel = "Model ID"
    modelIdHint = "e.g. deepseek-v4-flash (required)"
    apiUrlLabel = "API URL"
    apiUrlHint = "e.g. https://api.deepseek.com (required)"
    modelNote = "Note"
    modelNoteHint = "Optional description"
    saveModel = "Save"
    deleteModel = "Delete"
    unsavedTitle = "Unsaved Changes"
    unsavedMessage = "You have unsaved changes. Discard them?"
    userAgreement = "User Agreement & Disclaimer"
    addModelFirst = "Please add a model in Settings first"
    favorites = "Favorites"
    favorite = "Favorite"
    unfavorite = "Unfavorite"
    noFavorites = "No favorited messages yet"
    favoriteDetail = "Favorite Detail"
    openOriginal = "Open in Conversation"
    deleteFavoritesWarning = "This will also delete the favorited messages in this conversation."
    confirmUnfavorite = "Remove this message from favorites?"
    confirmUnfavoriteRun = { n -> "These $n messages were favorited together as one run. Removing will clear the whole run. Continue?" }
    roleUser = "You"
    roleAi = "FreeChat"
    filterAll = "All conversations"
    sortFavoriteTime = "Favorite time"
    sortConvTime = "Last conversation activity"
    filterByConversation = "Filter by conversation"
    sortBy = "Sort by"
    account = "Account"
    accountLocalOnly = "Everything works without an account. Signing in just puts it on the web too."
    accountLocalData = { "Already on this device: $it" }
    accountTabLogin = "Sign in"
    accountTabRegister = "Sign up"
    accountTabRecover = "Recover"
    accountUsername = "Username"
    accountPassword = "Password"
    accountPasswordConfirm = "Repeat password"
    accountOldPassword = "Current password"
    accountNewPassword = "New password"
    accountRecoveryCode = "Recovery code"
    accountLogin = "Sign in"
    accountRegister = "Sign up"
    accountResetPassword = "Reset password"
    accountWorking = "Working…"
    accountRecoveryTitle = "This is your recovery code"
    accountRecoveryHint = "Shown once. Screenshot it or write it down — it is the only way back into the account if you forget the password."
    accountCopy = "Copy"
    accountCopied = "Copied"
    accountSavedIt = "Saved it"
    accountSignedInAs = "Signed in"
    accountUsage = "Cloud usage"
    accountSyncNow = "Sync now"
    accountSyncIdle = "Up to date"
    accountSyncSyncing = "Syncing…"
    accountSyncOff = "Off"
    accountSyncError = "Sync error"
    accountSyncNever = "Never synced"
    accountSyncPending = { "$it item(s) waiting to upload" }
    accountSyncLast = { "Last synced $it" }
    accountDevices = "Signed-in devices"
    accountRevoke = "Sign out that device"
    accountChangePassword = "Change password"
    accountRegenerate = "New recovery code"
    accountLogout = "Sign out"
    accountLogoutHint = "Local data stays on this device and syncs again next time"
    accountDelete = "Delete account"
    accountDeleteWarn = "Every conversation, message and memory in the cloud will be deleted for good. Local data on this device is untouched."
    accountDeleteConfirm = "Enter your password to confirm"
    accountErrFillAll = "Username and password are both required"
    accountErrMismatch = "The two passwords don't match"
    accountErrPasswordShort = "Password must be at least 8 characters"
    accountErrNetwork = "Can't reach the server — check your connection"
    accountNotices = "Sync notices"
    accountPasswordChanged = "Password changed"
    accountCodeRegenerated = "New recovery code generated — also shown only once"
    avatarCropHint = "Drag to reposition, pinch to zoom"
    accountChangeAvatar = "Change avatar"
    accountUploadAvatar = "Upload avatar"
    accountRemoveAvatar = "Remove avatar"
    accountChangeUsername = "Change username"
    accountUsernameRule = "3–20 letters, digits or underscores"
    accountUsernameChanged = "Username changed"
    accountAvatarChanged = "Avatar updated"
    accountAvatarRemoved = "Avatar removed"
    accountAvatarFailed = "Couldn't update your avatar, please try again"
    avatarCropFailed = "Couldn't read that image, try another one"
    accountErrUsername = { rule -> "Username must be $rule" }
    claudeAssistantName = "Claude-style Assistant"
    convRules = "Rules"
    convRulesDesc = "A system prompt for this conversation only — applies in both modes"
    convRulesHint = "e.g. reply in English only; lead with the conclusion"
    genFailed = "Generation failed"
    accountEditAvatar = "Edit avatar"

    collapseInput = "Collapse"
    fullscreenInputting = "Full-screen input..."
    stopAction = "Stop"
    holdToTalk = "Hold to talk"
    sendAction = "Send"
    imageCountLabel = { n, max -> "Images $n/$max" }
    fileCountLabel = { n, max -> "Files $n/$max" }
    removeAction = "Remove"
    quoteAction = "Quote"
    cancelQuote = "Cancel quote"
    quotedImage = "Quoted image"
    imageLabel = "Image"
    fileLabel = "File"
    docLabel = "Document"
    releaseToCancel = "Release to cancel"
    attachmentLabel = "Attachment"
    tapToOpenEdit = "Tap to open and edit"
    tapToOpen = "Tap to open"
    imageSavedTo = { "Image saved to $it" }
    storageSaveFailed = "Save failed — check storage space"
    saveFailedWith = { "Save failed: $it" }
    downloadFailedWith = { "Download failed: $it" }
    imageMissing = "Image does not exist or has been deleted"
    fileMissing = "File does not exist or has been deleted"
    openFileFailed = { "Can't open file: $it" }
    shareTooMuch = "Too much selected — the long image would be huge. Share it in a few batches."
    imageTag = "[Image]"
    fileTag = "[File]"
    shareImageTooLarge = "Image too large to generate — select fewer messages and try again"
    shareFailed = "Share failed"
    savedOk = "Saved"
    saveFailedShort = "Save failed"
    noAsrModelTitle = "No speech recognition model"
    noAsrModelDesc = "Add a speech recognition model before using voice input."
    goAddAction = "Add"
    searchAction = "Search"
    clearAction = "Clear"
    notAdded = "Not added"
    notSelected = "Not selected"
    emptyMessage = "(empty message)"
    settingsSlogan = "Always believe something wonderful is about to happen"
    welcomePrompt = "What's on your mind?"
    dateMd = "MMM d"
    dateYmd = "MMM d, yyyy"
    dateMdTime = "MMM d, HH:mm"
    dateYmdTime = "MMM d, yyyy, HH:mm"

    syncLoginExpired = "Your session expired — please sign in again"
    syncFailed = "Sync failed"
    syncConflictCopySaved = { "This character was edited on two devices. A copy of this version was saved as \"$it\"." }
    syncObjectTooLarge = { "\"$it\" is too large, or cloud storage is full — this item will not sync for now" }
    syncResurrected = { "\"$it\" was deleted on another device — your edit here brought it back" }
    syncKindConv = "a conversation"
    syncKindMsgs = "a conversation's messages"
    syncKindMems = "a conversation's memories"
    syncKindSettings = "settings"
    notLoggedIn = "Not signed in"
    localDataSummary = { c, m -> "$c conversations · $m messages" }
    networkUnreachable = "Can't reach the server — check your connection"
    requestFailedWith = { "Request failed ($it)" }
    conflictCopySuffix = " (conflict copy)"
    shareAction = "Share"
    legacyBinaryFile = "(Legacy binary format — can't be parsed directly. Save as docx/xlsx/pptx and try again.)"
    readFileFailed = { "(Couldn't read the file: $it)" }
    pageMarker = { "[Page $it]" }
    channelProactive = "Proactive AI"
    channelProactiveDesc = "Generates a message in the background when they reach out"
    proactiveThinking = "Thinking about you…"
    channelKeepAlive = "Background keep-alive"
    channelKeepAliveDesc = "Keeps the process alive while the AI thinks in the background"
    channelReply = "Message replies"
    channelReplyDesc = "AI reply notifications"
    backgroundRunning = "Running in background"
    roleLearningRejected = "Character learning failed — the model refused the persona as sensitive"
    webQuotaExhausted = { n, at -> "Web search quota used up ($n accounts, ${n * 250} searches/month). Resets on $at." }
    webQuotaExhaustedNoDate = "Web search quota used up — real-time information is unavailable for now"
    webQuotaRemaining = { n, acc -> "$n searches left ($acc accounts)" }
    webQuotaNoKey = "No API key configured"
    webQuotaUnknown = "Quota unknown"
    webQuotaCountUnknown = "unknown"
    webQuotaCount = { "$it searches" }
    webNoKeyConfigured = "No web search API key configured"
    webRequestFailed = { "Web search request failed: $it" }
    webTooFrequent = "Web search rate-limited — try again in a moment"
    webUnavailable = "Web search is temporarily unavailable"
    notSet = "Not set"
    followGlobal = "Use global"
    followGlobalDefault = "Use global (default)"
    listSeparator = ", "
    genderKeys = listOf("男", "女")
    genderLabels = listOf("Male", "Female")
    personalityPresetLabels = listOf("Gentle", "Rational", "Humorous", "Blunt")
    relationshipPresetLabels = listOf("Girlfriend", "Boyfriend", "Best friend", "Friend", "Classmate", "Colleague", "Online friend", "Assistant", "Teacher", "Student", "Brother", "Stranger")
    builtInModelDesc = { id ->
        when (id) {
            "mimo-v2.5-pro" -> "Xiaomi deep-reasoning model. Author's own API — not guaranteed to be online."
            "ep-20260629143810-ffvjl" -> "Volcano Engine lightweight image model. Author's own API — not guaranteed to be online."
            "MiMo-V2.5-TTS" -> "Xiaomi text-to-speech model. Author's own API — not guaranteed to be online."
            else -> ""
        }
    }

    agreementTitle = "User Agreement & Notes"
    agreementSections = listOf(
        AgreementSectionText("1. About this app", listOf(
            "FreeChat is an open-source AI chat client. It **does not provide any AI model service**, and no feature is promised to be available forever.",
            "The app is provided **\"as is\"**. The author will try to maintain it, but cannot guarantee it is free of defects or interruptions."
        )),
        AgreementSectionText("2. You supply the models", listOf(
            "Apart from the few models built in by the author, every AI capability requires you to configure a third-party API key. The availability, pricing and content policy of those services are decided by the provider, not by the author.",
            "The built-in models are the author's own API keys and are **not guaranteed to have quota at any time**. They may stop working without notice."
        )),
        AgreementSectionText("3. Account & server (optional)", listOf(
            "You **do not have to sign up**. Everything works without an account, and your chat history stays on this device only.",
            "If you do sign up, the server is just a convenience: you can pick up the conversation on another device, and see on the web what you chatted about on your phone. The server stores only the text you choose to sync. It is not read or used for anything else.",
            "Any API key you enter for your own models is **encrypted on your device** before upload; the server holds ciphertext that **even the author cannot decrypt**. An account is not a safe deposit box, though — please do not treat the server as your only backup."
        )),
        AgreementSectionText("4. Your responsibility", listOf(
            "Every request sent from this app counts as **your own action**. You are responsible for what you enter and what you receive, including:",
            "· keeping your input lawful",
            "· judging for yourself whether the output is lawful and accurate",
            "· not using the app to create or spread illegal or harmful content",
            "AI output can be wrong. Use your own judgement; do not treat it as professional advice."
        )),
        AgreementSectionText("5. Minors", listOf(
            "If you are under 18, please use this app with a guardian."
        )),
        AgreementSectionText("6. Open source", listOf(
            "This project is released under the MIT License. You may freely use, modify and redistribute it, provided the original copyright notice is kept."
        )),
        AgreementSectionText("7. Scope of liability", listOf(
            "If you use this app for unlawful purposes, the author **bears no joint liability**. For other direct or indirect consequences of using the app, the author makes no promise beyond what the law requires."
        ))
    )
    agreementGateHead = "Please read before use:"
    agreementGateItems = listOf(
        "1. FreeChat is an open-source client. It does not provide an API service — you configure your own models.",
        "2. The built-in models are the author's own API keys and are not guaranteed to have quota.",
        "3. The app is fully usable without an account. Signing up only makes it easier to sync across devices; the server stores the text you sync, and keys for your own models are encrypted locally before upload.",
        "4. Do not use this app to create or spread unlawful content.",
        "5. This app is for study and research. Assess the legal risk yourself before commercial use."
    )
    agreementAgreePrefix = "I have read and agree to"
    agreementDocName = "the User Agreement"
    agreementContinue = "Agree and continue"
    agreementReleasePage = "6. Release page:"

    // ===== About the author =====
    authorAboutTitle = "About the author"
    authorPageTitle = "What do you want to know?"
    authorQa = listOf(
        AuthorQa("Name?", "Belate / 胡勃阳 (Hu Boyang)"),
        AuthorQa("Gender?", "Male"),
        AuthorQa("Age?", "Just turned 20"),
        AuthorQa(
            "Why a web version?",
            "Because some users are on iOS. Apple doesn't open up to individual developers, and its " +
                "build toolchain is locked to Mac — with my limited budget, that road was simply out of reach. " +
                "So I built the web version instead."
        ),
        AuthorQa(
            "Why a server?",
            "A web page stores nothing — close the browser and it's all gone. So it needed cloud storage; " +
                "and later, for data safety, an account system. Android is unaffected: signing up is never " +
                "required, and it still works as a free, open-source local app."
        )
    )
    authorWebAddress = "Official website:"
    authorWebNote = "The domain hasn't cleared review yet, so it's the IP — harmless, honestly."
    authorGithubRepo = "GitHub repo:"
    authorGithubNote = "A Star would be appreciated."
    authorContact = "For any suggestion or feedback, talk to me directly:"
    authorQrHint = "Long-press the QR code to save it"
    saveQr = "Save QR code"
    saveToGallery = "Save to gallery"
    changelogEntries = listOf(
        ChangelogEntry("Version 1.0.18", "2026-09-10", listOf(
            ChangelogSection("New", listOf(
                "Added a favorites system — starred messages are collected in one place.",
                "Added character import/export, so a character can be shared or reused in one tap.",
                "Added message sharing as jpg or Markdown."
            )),
            ChangelogSection("Improvements", listOf(
                "Improved new-conversation title generation",
                "Improved when the pinyin input panel appears",
                "Improved chat position anchoring while the AI is replying",
                "Improved character comprehension, reasoning and memory under high-quality retrieval, raising reply quality"
            )),
            ChangelogSection("Rendering", listOf(
                "Improved vertical alignment of table cells for readability.",
                "Unified the visual treatment under advanced material."
            )),
            ChangelogSection("Fixes", listOf(
                "Fixed the title bar having no divider under advanced material",
                "Fixed URL paths breaking when auto-completed",
                "Fixed the vision model failing in some cases"
            ))
        )),
        ChangelogEntry("Version 1.0.01", "2026-09-01", listOf(
            ChangelogSection("New", listOf(
                "Added high-quality replies. When on, memory recall switches from an AI summary to verbatim excerpts, which raises its priority, reduces hallucination and improves memory. It lets you choose between speed and cost."
            )),
            ChangelogSection("Other", listOf(
                "Added the open-source notice."
            ))
        )),
        ChangelogEntry("Version 1.0", "2026-09-01", listOf(
            ChangelogSection("Improvements", listOf(
                "Rebuilt as Release instead of Debug — faster response, less stutter at launch.",
                "Built-in fonts now preload in the background, reducing rendering hitches.",
                "Tuned in-app transitions for smoother motion."
            )),
            ChangelogSection("Fixes", listOf(
                "Fixed the cursor position resetting when full-screen input expands."
            ))
        ))
    )
}

// ========== CompositionLocal ==========
val LocalStrings = staticCompositionLocalOf { ZhCN }

/** Resolve the AppStrings for the current app language context */
@Composable
@ReadOnlyComposable
fun currentStrings(): AppStrings = LocalStrings.current
