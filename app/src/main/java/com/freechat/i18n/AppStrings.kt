package com.freechat.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.freechat.model.ChangelogEntry
import com.freechat.model.ChangelogSection
import com.freechat.data.GenerationPhase

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
    var attachmentMenu: String = ""
    var imagePreview: String = ""
    var previousImage: String = ""
    var nextImage: String = ""
    var jumpToFunctionalSettings: String = ""
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
    var headerBarStyle: String = ""
    var back: String = ""
    var openDrawer: String = ""
    var headerBarStyleCard: String = ""
    var headerBarStyleCutout: String = ""
    var headerBarStyleCardDesc: String = ""
    var headerBarStyleCutoutDesc: String = ""
    var systemDarkTheme: String = ""
    var liquidBackdrop: String = ""
    var liquidBackdropDesc: String = ""
    var liquidBackdropConfirm: String = ""
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
    var colorThemeCustom: String = ""
    var colorThemePine: String = ""
    var colorThemeCoral: String = ""
    var customHue: String = ""
    var customSaturation: String = ""
    var customBrightness: String = ""
    var customOpacity: String = ""
    var customColorPreview: String = ""
    var customColorApplied: String = ""
    var customColorHint: String = ""
    var customColorApply: String = ""
    var customPresetColors: String = ""

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
    var timePerception: String = ""
    var timePerceptionDesc: String = ""
    var timePerceptionConfirm: String = ""
    var chooseDialogueMode: String = ""
    var deepThinkingMode: String = ""
    var deepThinkingModeDesc: String = ""
    var modelDeepThinkingLabel: String = ""
    var modelCapabilitiesLabel: String = ""
    var modelCapabilitiesDesc: String = ""
    var modelDeepThinkingDefault: String = ""
    // ===== 1.0.75 =====
    /** 全局设置-深度思考开关的小字（模型支持时） */
    var deepThinkSettingDesc: String = ""
    /** 深度思考开关的小字（所选模型不支持时，开关灰色） */
    var deepThinkUnsupported: String = ""
    /** 新规则三态菜单的「开 / 关」两项（「跟随全局」已有 followGlobal 系列） */
    var optionOn: String = ""
    var optionOff: String = ""
    /** 模型编辑页：内置联网搜索能力位 */
    var nativeSearchLabel: String = ""
    var nativeSearchDesc: String = ""
    var searchSource: String = ""
    var showSearchSources: String = ""
    var showSearchSourcesDesc: String = ""
    var informationSources: String = ""
    var disclosureExpanded: String = ""
    var disclosureCollapsed: String = ""
    var searchFree: String = ""
    var searchSourceDesc: String = ""
    var searchAnySearchDesc: String = ""
    var searchEndpoint: String = ""
    var searchOptionalKey: String = ""
    var searchTest: String = ""
    var searchTesting: String = ""
    var searchTestCost: String = ""
    var searchTestOk: String = ""
    var searchSaveFailed: String = ""
    var originalText: String = ""
    var copyOriginal: String = ""
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
    var shareAsLink: String = ""
    var shareLinkCreating: String = ""
    var shareLinkDialogTitle: String = ""
    var shareLinkHint: String = ""
    var shareLinkCopy: String = ""
    var shareLinkCopied: String = ""
    var shareLinkNeedLogin: String = ""
    var shareLinkCreateFailed: String = ""
    var shareLinkTooMuch: String = ""
    var myShares: String = ""
    var mySharesEmpty: String = ""
    var shareRevoke: String = ""
    var shareRevokeConfirm: String = ""
    var shareRevoked: String = ""
    var wechatBind: String = ""
    var modeNext: String = ""
    var modeRepick: String = ""
    var importModeMismatchTitle: String = ""
    var importModeMismatchDesc: String = ""
    var importSwitchToMode: String = ""
    var importKeepMode: String = ""
    var wechatBindDesc: String = ""
    var wechatBindQrTitle: String = ""
    var wechatBindWaiting: String = ""
    var wechatBindScanned: String = ""
    var wechatBindConfirmed: String = ""
    var wechatBindExpired: String = ""
    var wechatBindNeedVerify: String = ""
    var wechatBindConnected: String = ""
    var wechatBindDisconnected: String = ""
    var wechatBindPaused: String = ""
    var wechatBindUnbind: String = ""
    var wechatBindFailed: String = ""
    var wechatConnectedTo: (String) -> String = { "" }
    var wechatTakeoverDesc: (String) -> String = { "" }
    var wechatNoticeTitle: String = ""
    var wechatBetaHint: String = ""
    var wechatNotice1: String = ""
    var wechatNotice2: String = ""
    var wechatNotice3: String = ""
    var wechatNotice4: String = ""
    var wechatNotice5: String = ""
    var wechatLoginUse: String = ""
    var wechatInputNotice: String = ""
    var wechatInputReveal: String = ""
    var wechatVerifyPlaceholder: String = ""
    var wechatVerifySubmit: String = ""
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
    // —— 1.0.69：模型上下文声明 / 生图参数 / 强制生图 ——
    var fetchModelsLabel: String = ""
    var fetchingModels: String = ""
    var fetchFailed: String = ""
    var pickModel: String = ""
    var imageGenParams: String = ""
    var imageRatio: String = ""
    var imageResolution: String = ""
    var imageStyle: String = ""
    var imageStyleNone: String = ""
    var imageSize: String = ""
    var genParamsDisclaimer: String = ""
    var generateImage: String = ""
    var generateSceneImage: String = ""
    var sceneImageVisualOnly: String = ""
    var enhancedSceneContinuity: String = ""
    var enhancedSceneContinuityDesc: String = ""
    var sceneErrorNoModel: String = ""
    var sceneErrorAuth: String = ""
    var sceneErrorEndpoint: String = ""
    var sceneErrorQuota: String = ""
    var sceneErrorRateLimit: String = ""
    var sceneErrorSafety: String = ""
    var sceneErrorParameters: String = ""
    var sceneErrorServer: String = ""
    var sceneErrorEmpty: String = ""
    var sceneErrorTimeout: String = ""
    var sceneErrorNetwork: String = ""
    var sceneErrorReferences: String = ""
    var sceneErrorUnknown: String = ""
    var sceneErrorRequestId: String = ""
    var sceneErrorAddress: String = ""
    var sceneErrorTls: String = ""
    var sceneErrorConfiguration: String = ""
    var sceneErrorModel: String = ""
    var originalLearning: String = ""
    var originalLearningHint: String = ""
    var originalLearningDescription: String = ""
    var appIcon: String = ""
    var appIconBlue: String = ""
    var appIconClassic: String = ""
    var appIconLunhui: String = ""
    var appIconGongming: String = ""
    var appIconHuanmeng: String = ""
    var appIconXinsheng: String = ""
    var appIconRixiang: String = ""
    var appIconHailuo: String = ""
    var appIconRestart: String = ""
    var appIconActive: String = ""
    var appIconSelected: String = ""
    var appIconSaveFailed: String = ""
    var appIconRestartWarning: String = ""
    var appIconDoNotChange: String = ""
    var appIconRestartNow: String = ""
    var appIconRestarting: String = ""
    var quickLocate: String = ""
    var locateStart: String = ""
    var locateEnd: String = ""
    var feedbackEntry: String = ""
    var feedbackTitle: String = ""
    var feedbackLabel: String = ""
    var feedbackHint: String = ""
    var feedbackDescription: String = ""
    var feedbackSubmit: String = ""
    var feedbackSending: String = ""
    var feedbackSent: String = ""
    var feedbackSendFailed: String = ""
    var feedbackMy: String = ""
    var feedbackMyEmpty: String = ""
    var unfavoriteKeepsOriginal: String = ""
    var quickLocateTimeFormat: String = ""
    var generatedImageLoading: String = ""
    var generatedImageUnavailable: String = ""
    var generatedImageMissing: String = ""
    var generatedImageNoResult: String = ""
    var retryImageLoading: String = ""
    // ─────────── 1.0.70 输入框样式 + 生成期思考状态 ───────────
    var inputBox: String = ""
    var inputStyle: String = ""
    var inputStyleCompact: String = ""
    var inputStyleComplete: String = ""
    var inputStyleCompactDesc: String = ""
    var inputStyleCompleteDesc: String = ""
    var inputBarState: String = ""
    var inputBarStatePinned: String = ""
    var inputBarStateAutoHide: String = ""
    var inputBarStatePinnedDesc: String = ""
    var inputBarStateAutoHideDesc: String = ""
    var generationPhaseLabels: Map<GenerationPhase, String> = emptyMap()
    // 分辨率档："短边像素" → 展示名（标清/高清/超清）
    var resolutionLabels: List<Pair<String, String>> = emptyList()
    // 预设风格：key → 展示名（key 存进档案，提示词用 ModelCatalog.STYLE_PROMPTS）
    var styleLabels: List<Pair<String, String>> = emptyList()
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
    var syncConflictCopiesCleaned: (Int) -> String = { "" }
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
    var attachmentMissing: String = ""
    var attachmentUnsupported: String = ""
    var attachmentTooLarge: String = ""
    var attachmentEmpty: String = ""
    var attachmentReadFailed: String = ""
    var attachmentTruncated: String = ""
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
    var notEntered: String = ""
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

    // ===== 免责声明（用户协议与使用须知，1.1.0 统一命名） =====
    /** 协议页正文：章节 = 标题 + 若干段落。段落里 `**…**` 之间的字加粗，以 `· ` 开头的当条目 */
    var agreementSections: List<AgreementSectionText> = emptyList()
    /** 首次进入的勾选弹窗 */
    var agreementGateHead: String = ""
    var agreementGateItems: List<String> = emptyList()
    var agreementAgreePrefix: String = ""
    var agreementDocName: String = ""
    var agreementContinue: String = ""
    var agreementReleasePage: String = ""
    var agreementWebLabel: String = ""

    /** 更新日志正文（简中直接复用 ChangelogData，其余语言各写各的） */
    var changelogEntries: List<com.freechat.model.ChangelogEntry> = emptyList()

    // ===== 关于 FreeChat（聚合页 + 在线检查更新）=====
    var aboutFreeChat: String = ""
    var checkUpdate: String = ""
    var updateChecking: String = ""
    var updateLatest: String = ""
    var updateDownloading: String = ""
    var updateInstalling: String = ""
    var updateFailed: String = ""
    var updateVerifyFailed: String = ""

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
    var authorCsdn: String = ""
    var authorCsdnNote: String = ""
    var authorContact: String = ""
    /** 「作者的话」（1.1.0）：问答区里的长文入口，点击进二级页看全文 */
    var authorWordsTitle: String = ""
    var authorWordsOpen: String = ""
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
    attachmentMenu = "附件与工具"
    imagePreview = "图片预览"
    previousImage = "上一张图片"
    nextImage = "下一张图片"
    jumpToFunctionalSettings = "跳转到功能设置"
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
    sectionSystemTheme = "个性化"
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
    headerBarStyle = "状态栏样式"
    back = "返回"
    openDrawer = "打开侧栏"
    headerBarStyleCard = "卡片"
    headerBarStyleCutout = "镂空"
    headerBarStyleCardDesc = "标题栏图标显示在新拟态圆形卡片上"
    headerBarStyleCutoutDesc = "标题栏图标直接显示在模糊图层上"
    systemDarkTheme = "系统暗色主题"
    liquidBackdrop = "流光炫彩"
    liquidBackdropDesc = "柔光渐变背景，提升观感"
    liquidBackdropConfirm = "开启动态渐变会增加性能占用、增加发热与功耗，确定开启？"
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
    colorThemeBrown = "浅棕"
    colorThemeBlue = "浅蓝"
    colorThemeWhite = "黑白"
    colorThemeCustom = "自定义颜色"
    colorThemePine = "松绿"
    colorThemeCoral = "珊瑚红"
    customHue = "色相"
    customSaturation = "饱和度"
    customBrightness = "亮度"
    customOpacity = "透明度"
    customColorPreview = "所选颜色"
    customColorApplied = "界面效果"
    customColorHint = "背景与正文保持中性；按钮和选中项会自动调整对比度。"
    customColorApply = "应用自定义颜色"
    customPresetColors = "返回预设颜色"
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
        "还没睡呀？正好，我也没睡", "凌晨好，这个点安静得刚刚好",
        "这个时间，世界好像只属于你", "凌晨了，夜还很长呢",
        "还没睡？别太累了哦", "在想什么呢，这个点还在",
        "凌晨好，陪你说说话？", "夜还不深，再待会儿",
        "这个点醒着，有点酷", "凌晨了，我随时都在"
    )
    greetingsEarlyMorning = listOf(
        "早。", "早上好", "早安，新的一天",
        "早呀，想聊点什么？", "早上好～", "早，我在呢",
        "早安", "早上好呀，今天也要好好的"
    )
    greetingsMorningWeekday = listOf(
        "上午好", "上午好呀", "上午好，想聊点什么？",
        "上午好，今天也要加油", "好好的上午", "上午好，状态怎么样？",
        "上午好，我在呢", "上午好，有什么新鲜事？"
    )
    greetingsMorningWeekend = listOf(
        "周末上午好", "周末好，今天悠闲一下",
        "周末的上午，不用赶", "上午好，周末快乐",
        "周末好呀", "周末上午好，打算做什么？",
        "周末好，放松一下", "周末的上午好时光"
    )
    greetingsNoon = listOf(
        "中午好", "中午好，吃饭了吗？",
        "午安", "中午好呀",
        "中午好，吃点好的", "午安～休息一下",
        "中午好，该吃饭了吧", "中午好，下午继续"
    )
    greetingsAfternoon = listOf(
        "下午好", "下午好呀", "下午好，我在呢",
        "下午好，今天过得怎么样？", "下午好，想聊点什么？",
        "下午好～", "下午好，状态还好吗",
        "下午好，有什么新鲜事？"
    )
    greetingsEvening = listOf(
        "晚上好", "晚上好呀", "晚上好，吃饭了吗？",
        "晚上好，今天辛苦了", "晚上好，放松一下",
        "晚上好，今天过得怎么样？", "晚上好，想聊点什么？",
        "晚上好，我在呢"
    )
    greetingsNight = listOf(
        "晚上好，这个点是自己的时间", "还没睡呀，正好",
        "这个时间，安静又自在", "夜深了，这个点刚好",
        "深夜好，还没睡呢", "这个点安静，适合想事情",
        "夜猫子好", "还没睡？我也在",
        "属于夜晚的时间", "这个点，刚刚好"
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
    dialogueModePickWarn = "创建并对话后 无法修改对话模式，请确定选择。"
    dialogueModeWechat = "微信聊天"
    dialogueModeWechatDesc = "模拟实时线上交谈，纯语言沟通，碎片化且口语化"
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
    wechatExampleUser = listOf("吃饭了吗？", "猪脚饭！", "超好吃！")
    wechatExampleAi = listOf("刚到食堂", "你说我是吃螺狮粉还是猪脚饭？", "好的那我就吃螺狮粉了")
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
    timePerception = "时间感知"
    timePerceptionDesc = "角色拥有独立人格、独立生活与作息，与现实时间轴所匹配"
    timePerceptionConfirm = "AI拥有自己的作息，意味着AI可能不回复消息。"
    chooseDialogueMode = "选择对话模式"
    deepThinkingMode = "深度思考"
    deepThinkingModeDesc = "开启后模型会进行更深入的推理；关闭时优先快速回复。是否联网仍由问题需要决定。"
    modelDeepThinkingLabel = "支持深度思考"
    modelCapabilitiesLabel = "模型能力"
    modelCapabilitiesDesc = "这里只声明 API 支持的能力，不会直接开启。是否使用由全局设置或对话开关控制。"
    modelDeepThinkingDefault = "默认开启深度思考"
    deepThinkSettingDesc = "用于完成复杂问题，可能大幅提高回复时长"
    deepThinkUnsupported = "所选模型不支持此功能"
    optionOn = "开"
    optionOff = "关"
    nativeSearchLabel = "支持原生联网搜索"
    nativeSearchDesc = "仅适用于已适配的小米或百炼搜索接口；其他接口使用所选搜索源。请按接口实际能力填写，不要只凭模型名称勾选"
    searchSource = "搜索源"
    showSearchSources = "显示信息源"
    showSearchSourcesDesc = "在回复底部显示可折叠的网页来源，默认关闭"
    informationSources = "信息源"
    disclosureExpanded = "已展开"
    disclosureCollapsed = "已收起"
    searchFree = "免费公开搜索"
    searchSourceDesc = "默认优先使用已适配模型的原生搜索；也可选择 AnySearch 免 Key 搜索或自己的 Tavily、Brave、SearXNG、Firecrawl 接口。第三方服务的额度与费用以其规则为准。Key 加密保存于本机，不参与同步或系统备份"
    searchAnySearchDesc = "可直接使用官方匿名接口，无需填写 Key；按 IP 限流并受免费额度限制。也可填写自己的 Key，费用由本人承担。检索问题会发送给 AnySearch；相关性与时效性仍需核验。额度不足时不会自动使用返回的账户凭据或切换付费调用"
    searchEndpoint = "完整搜索接口 URL（HTTPS）"
    searchOptionalKey = "API Key（匿名或无鉴权接口可留空）"
    searchTest = "测试搜索"
    searchTesting = "正在测试…"
    searchTestCost = "测试会发送一条查询，可能消耗服务商额度。SearXNG 需启用 JSON 输出；接口应兼容所选服务协议"
    searchTestOk = "检索成功"
    searchSaveFailed = "保存失败，请重试"
    originalText = "正文"
    copyOriginal = "复制正文"
    paste = "粘贴"
    fullscreenInput = "全屏输入"
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
    shareAsLink = "生成在线网页链接"
    shareLinkCreating = "正在生成分享链接…"
    shareLinkDialogTitle = "分享链接已生成"
    shareLinkHint = "任何拿到链接的人都可以在线查看，无需安装 FreeChat"
    shareLinkCopy = "复制链接"
    shareLinkCopied = "链接已复制"
    shareLinkNeedLogin = "生成在线链接需登录账号"
    shareLinkCreateFailed = "生成失败，请稍后重试"
    shareLinkTooMuch = "在线分享最多 20 条消息、3 万字，请减少一些"
    myShares = "我的分享"
    mySharesEmpty = "还没有生成过分享链接"
    shareRevoke = "撤销"
    shareRevokeConfirm = "撤销后链接立即失效，对方将无法查看"
    shareRevoked = "已撤销"
    wechatBind = "连接微信"
    modeNext = "下一步"
    modeRepick = "重新选择"
    importModeMismatchTitle = "对话模式不一致"
    importModeMismatchDesc = "导入的角色与当前所选的对话模式不同。可切换到导入角色的模式；或以当前模式导入——能写入的设定照写，冲突与多余项舍弃，空缺项依旧空缺。"
    importSwitchToMode = "切换到导入的模式"
    importKeepMode = "以当前模式导入"
    wechatBindDesc = "把这个角色接到微信：好友在微信里发消息，她会像真人一样回复。扫码即授权，消息经 FreeChat 云端生成，手机无需常驻后台。仅登录可用。"
    wechatBindQrTitle = "用微信扫码确认授权"
    wechatBindWaiting = "等待扫码…"
    wechatBindScanned = "已扫码，请在手机上确认"
    wechatBindConfirmed = "已连接"
    wechatBindExpired = "二维码已过期，请重新打开"
    wechatBindNeedVerify = "需要输入手机上显示的验证码"
    wechatBindConnected = "已连接微信"
    wechatBindDisconnected = "未连接"
    wechatBindPaused = "会话被微信暂时限制，稍后自动恢复"
    wechatBindUnbind = "解除绑定"
    wechatBindFailed = "连接失败，请稍后重试"
    wechatConnectedTo = { x -> "已连接到${x}微信账号" }
    wechatTakeoverDesc = { x -> "Clawbot 仅允许单独存在，「${x}」对话已经接入微信。若将本对话连接微信，「${x}」对话将会断开，是否继续？" }
    wechatNoticeTitle = "功能使用须知"
    wechatBetaHint = "本功能处于测试阶段，并不稳定。"
    wechatNotice1 = "本功能通过云同步实现数据互传，不依赖 FreeChat 本地后台运行，仅限登录后使用。"
    wechatNotice2 = "本功能当前仅可使用内置模型（mimo-v2.6-flash）。"
    wechatNotice3 = "角色头像与备注名可在微信 ClawBot 对话的设置中修改。"
    wechatNotice4 = "微信仅作为连接用户与服务器的桥梁，不会将 FreeChat 的历史聊天记录同步至微信；角色的模拟设置仍需在 FreeChat 客户端中调整。"
    wechatNotice5 = "经微信连接后，回复时长可能增加，并存在一定的不稳定因素。"
    wechatLoginUse = "登录使用"
    wechatInputNotice = "微信无法获取 FreeChat 客户端聊天记录，为保证对话连贯性，建议前往微信 ClawBot 与该角色对话。"
    wechatInputReveal = "若仍要对话请点击这里"
    wechatVerifyPlaceholder = "输入手机上显示的验证码"
    wechatVerifySubmit = "提交"
    addModel = "添加模型"
    editModel = "编辑模型"
    modelName = "模型名称"
    modelNameHint = "自定义名称，便于区分（默认与模型 ID 一致）"
    apiKeyLabel = "API Key"
    apiKeyHint = "例如 sk-xxxxxxxx（必填）"
    modelIdLabel = "模型 ID"
    modelIdHint = "例如 deepseek-v4-flash（必填）"
    apiUrlLabel = "URL 地址"
    apiUrlHint = "例如 https://api.deepseek.com（必填）"
    modelNote = "备注"
    modelNoteHint = "补充说明（选填）"
    saveModel = "保存"
    fetchModelsLabel = "获取模型列表"
    fetchingModels = "获取中…"
    fetchFailed = "获取失败，可手动填写（不保证生效）"
    pickModel = "选择模型"
    imageGenParams = "生成参数"
    imageRatio = "比例"
    imageResolution = "分辨率"
    imageStyle = "预设风格"
    imageStyleNone = "无"
    imageSize = "尺寸"
    genParamsDisclaimer = "比例与分辨率以 size 参数随请求发送，预设风格附加于提示词；是否生效取决于模型服务。获取预设失败时可手动填写。"
    generateImage = "生成图片"
    generateSceneImage = "生成当前场景图"
    sceneImageVisualOnly = "当前场景图 · 仅供视觉呈现，不写入剧情记忆"
    enhancedSceneContinuity = "增强同元延续"
    enhancedSceneContinuityDesc = "使用最近保留的场景图延续服装、配饰与道具；最新剧情文字和人物设定优先。删除的场景图不再参与参考。"
    sceneErrorNoModel = "未配置可用的生图模型，请检查角色的生图模型或全局生图设置。"
    sceneErrorAuth = "生图服务的 API Key 无效或没有模型权限，请检查配置。"
    sceneErrorEndpoint = "生图接口或模型不存在；有参考图时，请确认服务支持 images/edits。"
    sceneErrorQuota = "生图服务额度不足，请检查该服务的余额或配额。"
    sceneErrorRateLimit = "生图服务已限流，请稍后再试；不会自动重复提交付费请求。"
    sceneErrorSafety = "内容触及生图模型的安全限制，无法生成。请调整提示词或参考图后再试。"
    sceneErrorParameters = "生图服务拒绝了请求参数，请检查模型的生图协议、参考图支持及提示词长度；这不等同于内容违规。"
    sceneErrorServer = "生图服务端出错，请稍后再试。"
    sceneErrorEmpty = "服务未返回有效图片，请检查该接口是否兼容 images 生图协议。"
    sceneErrorTimeout = "等待生图超时。服务可能仍在处理，请确认服务状态后再手动重试。"
    sceneErrorNetwork = "无法连接生图服务，请检查网络、代理和接口地址。"
    sceneErrorReferences = "角色参考图无法读取，请重新选择参考图后再试。"
    sceneErrorUnknown = "生图未完成，服务没有给出可识别的失败原因。请核对生图模型与接口。"
    sceneErrorRequestId = "服务请求编号"
    sceneErrorAddress = "无法解析生图 API 地址，请检查 URL 主机名是否填写正确，以及网络、代理或 DNS。"
    sceneErrorTls = "与生图服务建立安全连接失败，请检查 HTTPS 地址、证书、设备时间或代理。"
    sceneErrorConfiguration = "生图 API 地址或请求配置无效，请检查模型 ID、API URL 和 API Key 的填写。"
    sceneErrorModel = "生图模型不存在或服务当前没有可用渠道，请检查模型 ID 和服务商的模型支持。"
    originalLearning = "原文学习"
    originalLearningHint = "粘贴小说原文，尤其是该角色的语言、动作与叙述片段（可选）"
    originalLearningDescription = "重点学习角色语气、性格细节和原文笔法；剧情补足还会参考叙事与描写风格。结合已有设定，不将原文当作当前剧情。长篇素材会增加模型的上下文用量。"
    appIcon = "图标"
    appIconBlue = "简F"
    appIconClassic = "菱星"
    appIconLunhui = "轮回"
    appIconGongming = "共鸣"
    appIconHuanmeng = "幻梦"
    appIconXinsheng = "新生"
    appIconRixiang = "日象"
    appIconHailuo = "海螺"
    appIconRestart = "选择图标后点击保存，FreeChat 将立即重启并加载新图标。"
    appIconActive = "正在使用"
    appIconSelected = "已选择"
    appIconSaveFailed = "图标选择未保存，请重试。"
    appIconRestartWarning = "更换图标需要重启软件，当前有对话正在思考/回复，可能会导致该条思考/回复中断，确定现在重启？"
    appIconDoNotChange = "暂不更换"
    appIconRestartNow = "立即重启"
    appIconRestarting = "正在重启…"
    quickLocate = "快速定位"
    locateStart = "定位到对话开头"
    locateEnd = "定位到最新消息"
    feedbackEntry = "问题反馈或建议意见"
    feedbackTitle = "反馈与建议"
    feedbackLabel = "反馈或建议"
    feedbackHint = "描述遇到的问题、复现步骤，或你希望加入的功能…"
    feedbackDescription = "无需登录。反馈内容会发送给开发者，并附带设备型号、系统版本、应用版本等排查信息；登录后还会关联你的账号。请勿填写 API Key、密码等敏感信息。"
    feedbackSubmit = "提交"
    feedbackSending = "发送中…"
    feedbackSent = "已收到你的反馈，感谢！"
    feedbackSendFailed = "发送失败，请检查网络后重试。内容已保留。"
    feedbackMy = "我的反馈"
    feedbackMyEmpty = "还没有提交过反馈"
    unfavoriteKeepsOriginal = "仅取消收藏，原聊天消息、图片及相关记忆会保留。收藏状态会同步到云端。"
    quickLocateTimeFormat = "yyyy年M月d日 HH:mm"
    generatedImageLoading = "图片加载中…"
    generatedImageUnavailable = "图片无法加载，可能是网络异常或图片链接已失效。可重新加载；不会再次提交生图请求。"
    generatedImageMissing = "本地图片文件已丢失或无法读取，请重新生成图片。"
    generatedImageNoResult = "这条图片记录没有可显示的文件或链接。请在原设备查看，或重新生成图片。"
    retryImageLoading = "重新加载"
    inputBox = "输入框"
    inputStyle = "输入框样式"
    inputStyleCompact = "简洁"
    inputStyleComplete = "完整"
    inputStyleCompactDesc = "单行输入框，长文本可展开全屏输入"
    inputStyleCompleteDesc = "两行完整输入框，换行自动长高，状态快捷键常驻底行"
    inputBarState = "输入框状态"
    inputBarStatePinned = "永久固定"
    inputBarStateAutoHide = "自动隐藏"
    inputBarStatePinnedDesc = "输入框固定在屏幕下方，不随滑动隐藏"
    inputBarStateAutoHideDesc = "上滑回看消息时自动隐藏，回到最新时恢复"
    generationPhaseLabels = mapOf(
        GenerationPhase.CONNECTING to "连接模型…", GenerationPhase.UNDERSTANDING to "理解内容…",
        GenerationPhase.SEARCHING to "搜索信息…", GenerationPhase.DEEP_RETRIEVAL to "深度检索…",
        GenerationPhase.DRAFTING to "拟草回复…", GenerationPhase.IMAGE_CONTEXT to "理解场景…",
        GenerationPhase.IMAGE_REFERENCES to "准备角色参考图…", GenerationPhase.IMAGE_CONNECTING to "连接生图模型…",
        GenerationPhase.IMAGE_GENERATING to "模型生成中…", GenerationPhase.IMAGE_RECEIVING to "接收图片…"
    )
    resolutionLabels = listOf("1024" to "标清", "1536" to "高清", "2048" to "超清")
    styleLabels = listOf(
        "photo" to "写实照片", "anime" to "动漫插画", "oil" to "油画",
        "watercolor" to "水彩", "threeD" to "3D渲染", "flat" to "扁平插画",
        "pixel" to "像素风", "cyber" to "赛博朋克", "ink" to "水墨国风"
    )
    deleteModel = "删除"
    unsavedTitle = "未保存更改"
    unsavedMessage = "当前有未保存的更改，确定要放弃吗？"
    userAgreement = "用户协议与使用条款"
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
    syncConflictCopiesCleaned = { "已自动清理 $it 条重复的冲突副本，保留原对话和一份副本" }
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
    attachmentMissing = "附件的本地文件已丢失或不可访问，请重新上传。"
    attachmentUnsupported = "暂不能提取此格式的正文；可改为 TXT、JSON、CSV、DOCX、XLSX、PPTX，或粘贴文字。"
    attachmentTooLarge = "附件超出本地安全读取大小，请拆分后上传（文本不超过 2 MB，Office 文件不超过 16 MB）。"
    attachmentEmpty = "附件为空或未提取到文字；扫描件和图片型文档需要先识别文字。"
    attachmentReadFailed = "附件读取失败，文件可能损坏或格式与扩展名不符，请检查后重新上传。"
    attachmentTruncated = "附件正文较长，本轮仅提供前部节选；未提供的部分不得猜测。"
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
    notEntered = "未输入"
    followGlobal = "跟随全局"
    followGlobalDefault = "跟随全局（默认）"
    listSeparator = "、"
    genderKeys = listOf("男", "女")
    genderLabels = listOf("男", "女")
    personalityPresetLabels = listOf("温柔随和", "理性冷静", "幽默搞怪", "直率犀利")
    relationshipPresetLabels = listOf("女朋友", "男朋友", "闺蜜", "朋友", "同学", "同事", "网友", "助理", "老师", "学生", "兄弟", "陌生人")
    builtInModelDesc = { id ->
        when (id) {
            "mimo-v2.6-flash" -> "Xiaomi 深度推理模型，作者自用 API，不保证随时在线。"
            "ep-20260629143810-ffvjl" -> "Volcano Engine 轻量级生图模型，作者自用 API，不保证随时在线。"
            "MiMo-V2.5-TTS" -> "小米语音合成模型，作者自用 API，不保证随时在线。"
            else -> ""
        }
    }

    agreementSections = listOf(
        AgreementSectionText("一、关于本软件", listOf(
            "FreeChat 是一款开源的 AI 聊天客户端，本身**不提供任何 AI 模型服务**，亦不承诺功能永久可用。",
            "本软件按**「现状」**提供，作者将持续维护，但无法保证不存在缺陷或服务不中断。"
        )),
        AgreementSectionText("二、模型需要自备", listOf(
            "除作者内置的少量模型外，AI 能力需您自行配置第三方 API Key。第三方服务的可用性、价格与内容政策由服务商独立决定，与作者无关。",
            "内置模型为作者本人使用的 API，**不保证随时保有额度**，可能随时失效。",
            "「自定义语音音色」等语音功能会把您上传或录制的音频样本、音色描述发送至第三方语音服务（小米 MiMo）进行合成处理；音频样本仅保存在本机，不参与云同步。"
        )),
        AgreementSectionText("三、账号与服务器（可选）", listOf(
            "您**可以不注册**。不注册时全部功能均可正常使用，数据仅保存在本设备。",
            "注册后，服务器提供跨设备同步服务（含网页版访问），仅存储您主动同步的对话、记忆与图片等资料，不作他用。第三方 API Key **仅保存于设备本地，不会上传至服务器**。",
            "「连接微信」功能的对话经由微信 ClawBot 与服务器转发处理。云端并非保险箱，请勿将服务器作为唯一备份。"
        )),
        AgreementSectionText("四、您的责任", listOf(
            "通过本软件发出的全部请求均视为**您本人的行为**，您需对输入与输出的内容负责，包括：",
            "· 确保输入内容不违反法律法规",
            "· 自行判断输出内容的合法性与准确性",
            "· 不利用本软件生成、传播违法或不良信息",
            "· 「在线网页分享」生成的公开链接可被任何持有链接者访问，请自行斟酌分享内容",
            "· 「自定义语音音色」仅限使用您本人或已获授权的声音样本，严禁克隆、模仿他人声音用于冒充身份、欺诈、诽谤或任何违法用途",
            "AI 的输出可能包含错误，请自行判断，不应视为专业意见。"
        )),
        AgreementSectionText("五、未成年人", listOf(
            "若您未满 18 周岁，请在监护人陪同下使用本软件。"
        )),
        AgreementSectionText("六、开源声明", listOf(
            "本项目基于 MIT License 开源，您可自由使用、修改与分发，但须保留原始版权声明。"
        )),
        AgreementSectionText("七、责任范围", listOf(
            "若您将本软件用于违法用途，作者**不承担连带责任**。因使用本软件产生的其他直接或间接后果，作者亦不作出超出法律要求的承诺。"
        ))
    )
    agreementGateHead = "使用前请阅读："
    agreementGateItems = listOf(
        "1. FreeChat 为开源 AI 聊天客户端，不提供模型服务，需自行配置第三方模型 API。",
        "2. 内置模型为作者自备的 API，不保证随时可用或保有额度。",
        "3. 无需注册即可完整使用；注册仅用于跨设备同步，同步内容仅限您主动同步的资料。API Key 仅保存于本地，不会上传至服务器。",
        "4. 请勿利用本软件生成、存储或传播违反法律法规的内容。",
        "5. 「自定义语音音色」仅限使用您本人或已获授权的声音样本，严禁克隆他人声音用于冒充、欺诈等违法用途；音频样本会发送至第三方语音服务处理。",
        "6. 本软件仅供学习与研究使用，商用请自行评估相关法律风险。"
    )
    agreementAgreePrefix = "我已阅读并同意"
    agreementDocName = "《用户协议与使用条款》"
    agreementContinue = "确定并继续"
    agreementReleasePage = "发布页："
    agreementWebLabel = "FreeChat官网："
    changelogEntries = com.freechat.model.ChangelogData.entries
    aboutFreeChat = "关于FreeChat"
    checkUpdate = "检查更新"
    updateChecking = "检查中…"
    updateLatest = "已是最新版本"
    updateDownloading = "发现新版本，正在下载…"
    updateInstalling = "下载完成，正在拉起安装…"
    updateFailed = "检查更新失败，请稍后重试"
    updateVerifyFailed = "安装包校验失败，已取消安装"

    // ===== 关于作者 =====
    // 这一页是「作者本人回答你」，语气按真人说话写，不做简报腔。
    authorAboutTitle = "关于作者"
    authorPageTitle = "你想知道什么?"
    authorQa = listOf(
        AuthorQa("叫什么？", "Belate / 胡勃阳"),
        AuthorQa("男女？", "男"),
        AuthorQa("几岁？", "刚 20")
    )
    authorWordsTitle = "作者的话"
    authorWordsOpen = "点击阅读"
    authorWebAddress = "官方网页："
    authorWebNote = "域名已过审，直接用域名 —— 无毒无公害。"
    authorGithubRepo = "GitHub 仓库："
    authorGithubNote = "觉得不错的话，点个 Star。"
    authorCsdn = "CSDN 主页："
    authorCsdnNote = "技术笔记与开发记录。"
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
    attachmentMenu = "附件與工具"
    imagePreview = "圖片預覽"
    previousImage = "上一張圖片"
    nextImage = "下一張圖片"
    jumpToFunctionalSettings = "跳轉至功能設定"
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
    sectionSystemTheme = "個性化"
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
    headerBarStyle = "狀態列樣式"
    back = "返回"
    openDrawer = "開啟側欄"
    headerBarStyleCard = "卡片"
    headerBarStyleCutout = "鏤空"
    headerBarStyleCardDesc = "標題列圖示顯示在新擬態圓形卡片上"
    headerBarStyleCutoutDesc = "標題列圖示直接顯示在模糊圖層上"
    systemDarkTheme = "系統暗色主題"
    liquidBackdrop = "流光炫彩"
    liquidBackdropDesc = "柔光漸變背景，提升觀感"
    liquidBackdropConfirm = "開啟動態漸變會增加效能佔用、增加發熱與功耗，確定開啟？"
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
    colorThemeBrown = "淺棕"
    colorThemeBlue = "淺藍"
    colorThemeWhite = "黑白"
    colorThemeCustom = "自訂顏色"
    colorThemePine = "松綠"
    colorThemeCoral = "珊瑚紅"
    customHue = "色相"
    customSaturation = "飽和度"
    customBrightness = "亮度"
    customOpacity = "透明度"
    customColorPreview = "所選顏色"
    customColorApplied = "介面效果"
    customColorHint = "背景與內文維持中性；按鈕和選取項目會自動調整對比度。"
    customColorApply = "套用自訂顏色"
    customPresetColors = "返回預設顏色"
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
        "還沒睡呀？正好，我也沒睡", "凌晨好，這個點安靜得剛剛好",
        "這個時間，世界好像只屬於你", "凌晨了，夜還很長呢",
        "還沒睡？別太累了哦", "在想什麼呢，這個點還在",
        "凌晨好，陪你說說話？", "夜還不深，再待會兒",
        "這個點醒著，有點酷", "凌晨了，我隨時都在"
    )
    greetingsEarlyMorning = listOf(
        "早。", "早上好", "早安，新的一天",
        "早呀，想聊點什麼？", "早上好～", "早，我在呢",
        "早安", "早上好呀，今天也要好好的"
    )
    greetingsMorningWeekday = listOf(
        "上午好", "上午好呀", "上午好，想聊點什麼？",
        "上午好，今天也要加油", "好好的上午", "上午好，狀態怎麼樣？",
        "上午好，我在呢", "上午好，有什麼新鮮事？"
    )
    greetingsMorningWeekend = listOf(
        "週末上午好", "週末好，今天悠閒一下",
        "週末的上午，不用趕", "上午好，週末快樂",
        "週末好呀", "週末上午好，打算做什麼？",
        "週末好，放鬆一下", "週末的上午好時光"
    )
    greetingsNoon = listOf(
        "中午好", "中午好，吃飯了嗎？",
        "午安", "中午好呀",
        "中午好，吃點好的", "午安～休息一下",
        "中午好，該吃飯了吧", "中午好，下午繼續"
    )
    greetingsAfternoon = listOf(
        "下午好", "下午好呀", "下午好，我在呢",
        "下午好，今天過得怎麼樣？", "下午好，想聊點什麼？",
        "下午好～", "下午好，狀態還好嗎",
        "下午好，有什麼新鮮事？"
    )
    greetingsEvening = listOf(
        "晚上好", "晚上好呀", "晚上好，吃飯了嗎？",
        "晚上好，今天辛苦了", "晚上好，放鬆一下",
        "晚上好，今天過得怎麼樣？", "晚上好，想聊點什麼？",
        "晚上好，我在呢"
    )
    greetingsNight = listOf(
        "晚上好，這個點是自己的時間", "還沒睡呀，正好",
        "這個時間，安靜又自在", "夜深了，這個點剛好",
        "深夜好，還沒睡呢", "這個點安靜，適合想事情",
        "夜貓子好", "還沒睡？我也在",
        "屬於夜晚的時間", "這個點，剛剛好"
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
    dialogueModePickWarn = "建立並對話後 無法修改對話模式，請確定選擇。"
    dialogueModeWechat = "微信聊天"
    dialogueModeWechatDesc = "模擬即時線上交談，純語言溝通，碎片化且口語化"
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
    wechatExampleUser = listOf("吃飯了嗎？", "豬腳飯！", "超好吃！")
    wechatExampleAi = listOf("剛到食堂", "你說我是吃螺獅粉還是豬腳飯？", "好的那我就吃螺獅粉了")
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
    timePerception = "時間感知"
    timePerceptionDesc = "角色擁有獨立人格、獨立生活與作息，與現實時間軸相匹配"
    timePerceptionConfirm = "AI擁有自己的作息，意味著AI可能不回覆訊息。"
    chooseDialogueMode = "選擇對話模式"
    deepThinkingMode = "深度思考"
    deepThinkingModeDesc = "開啟後模型會進行更深入的推理；關閉時優先快速回覆。是否聯網仍由問題需要決定。"
    modelDeepThinkingLabel = "支援深度思考"
    modelCapabilitiesLabel = "模型能力"
    modelCapabilitiesDesc = "這裡只聲明 API 支援的能力，不會直接開啟。是否使用由全域設定或對話開關控制。"
    modelDeepThinkingDefault = "默認開啟深度思考"
    deepThinkSettingDesc = "用於完成複雜問題，可能大幅提高回覆時長"
    deepThinkUnsupported = "所選模型不支援此功能"
    optionOn = "開"
    optionOff = "關"
    nativeSearchLabel = "支援原生聯網搜尋"
    nativeSearchDesc = "僅適用於已適配的小米或百煉搜尋介面；其他介面使用所選搜尋來源。請按介面實際能力填寫，不要只憑模型名稱勾選"
    searchSource = "搜尋來源"
    showSearchSources = "顯示資訊來源"
    showSearchSourcesDesc = "在回覆底部顯示可摺疊的網頁來源，預設關閉"
    informationSources = "資訊來源"
    disclosureExpanded = "已展開"
    disclosureCollapsed = "已收起"
    searchFree = "免費公開搜尋"
    searchSourceDesc = "預設優先使用已適配模型的原生搜尋；也可選擇 AnySearch 免 Key 搜尋或自己的 Tavily、Brave、SearXNG、Firecrawl 介面。第三方服務的額度與費用以其規則為準。Key 加密儲存於本機，不參與同步或系統備份"
    searchAnySearchDesc = "可直接使用官方匿名介面，無需填寫 Key；按 IP 限流並受免費額度限制。也可填寫自己的 Key，費用由本人承擔。檢索問題會傳送給 AnySearch；相關性與時效性仍需核驗。額度不足時不會自動使用回傳的帳戶憑證或切換付費呼叫"
    searchEndpoint = "完整搜尋介面 URL（HTTPS）"
    searchOptionalKey = "API Key（匿名或無驗證介面可留空）"
    searchTest = "測試搜尋"
    searchTesting = "正在測試…"
    searchTestCost = "測試會發送一條查詢，可能消耗服務商額度。SearXNG 需啟用 JSON 輸出；介面應相容所選服務協定"
    searchTestOk = "檢索成功"
    searchSaveFailed = "儲存失敗，請重試"
    originalText = "正文"
    copyOriginal = "複製正文"
    paste = "貼上"
    fullscreenInput = "全螢幕輸入"
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
    shareAsLink = "產生線上網頁連結"
    shareLinkCreating = "正在產生分享連結…"
    shareLinkDialogTitle = "分享連結已產生"
    shareLinkHint = "任何拿到連結的人都可以在線查看，無需安裝 FreeChat"
    shareLinkCopy = "複製連結"
    shareLinkCopied = "連結已複製"
    shareLinkNeedLogin = "產生線上連結需登入帳號"
    shareLinkCreateFailed = "產生失敗，請稍後重試"
    shareLinkTooMuch = "線上分享最多 20 則訊息、3 萬字，請減少一些"
    myShares = "我的分享"
    mySharesEmpty = "還沒有產生過分享連結"
    shareRevoke = "撤銷"
    shareRevokeConfirm = "撤銷後連結立即失效，對方將無法查看"
    shareRevoked = "已撤銷"
    wechatBind = "連接微信"
    modeNext = "下一步"
    modeRepick = "重新選擇"
    importModeMismatchTitle = "對話模式不一致"
    importModeMismatchDesc = "導入的角色與當前所選的對話模式不同。可切換到導入角色的模式；或以當前模式導入——能寫入的設定照寫，衝突與多餘項捨棄，空缺項依舊空缺。"
    importSwitchToMode = "切換到導入的模式"
    importKeepMode = "以當前模式導入"
    wechatBindDesc = "把這個角色接到微信：好友在微信裡發訊息，她會像真人一樣回覆。掃碼即授權，訊息經 FreeChat 雲端生成，手機無需常駐背景。僅登入可用。"
    wechatBindQrTitle = "用微信掃碼確認授權"
    wechatBindWaiting = "等待掃碼…"
    wechatBindScanned = "已掃碼，請在手機上確認"
    wechatBindConfirmed = "已連接"
    wechatBindExpired = "二維碼已過期，請重新打開"
    wechatBindNeedVerify = "需要輸入手機上顯示的驗證碼"
    wechatBindConnected = "已連接微信"
    wechatBindDisconnected = "未連接"
    wechatBindPaused = "會話被微信暫時限制，稍後自動恢復"
    wechatBindUnbind = "解除綁定"
    wechatBindFailed = "連接失敗，請稍後重試"
    wechatConnectedTo = { x -> "已連接到${x}微信帳號" }
    wechatTakeoverDesc = { x -> "Clawbot 僅允許單獨存在，「${x}」對話已經接入微信。若將本對話連接微信，「${x}」對話將會斷開，是否繼續？" }
    wechatNoticeTitle = "功能使用須知"
    wechatBetaHint = "本功能處於測試階段，並不安定。"
    wechatNotice1 = "本功能透過雲端同步實現資料互傳，不依賴 FreeChat 本地背景運行，僅限登入後使用。"
    wechatNotice2 = "本功能目前僅可使用內建模型（mimo-v2.6-flash）。"
    wechatNotice3 = "角色頭像與備註名可在微信 ClawBot 對話的設定中修改。"
    wechatNotice4 = "微信僅作為連接用戶與伺服器的橋樑，不會將 FreeChat 的歷史聊天記錄同步至微信；角色的模擬設定仍需在 FreeChat 用戶端中調整。"
    wechatNotice5 = "經微信連接後，回覆時長可能增加，並存在一定的不穩定因素。"
    wechatLoginUse = "登入使用"
    wechatInputNotice = "微信無法取得 FreeChat 用戶端聊天記錄，為保證對話連貫性，建議前往微信 ClawBot 與該角色對話。"
    wechatInputReveal = "若仍要對話請點擊這裡"
    wechatVerifyPlaceholder = "輸入手機上顯示的驗證碼"
    wechatVerifySubmit = "提交"
    addModel = "新增模型"
    editModel = "編輯模型"
    modelName = "模型名稱"
    modelNameHint = "自訂名稱，便於區分（預設與模型 ID 一致）"
    apiKeyLabel = "API Key"
    apiKeyHint = "例如 sk-xxxxxxxx（必填）"
    modelIdLabel = "模型 ID"
    modelIdHint = "例如 deepseek-v4-flash（必填）"
    apiUrlLabel = "URL 位址"
    apiUrlHint = "例如 https://api.deepseek.com（必填）"
    modelNote = "備註"
    modelNoteHint = "補充說明（選填）"
    saveModel = "儲存"
    fetchModelsLabel = "取得模型列表"
    fetchingModels = "取得中…"
    fetchFailed = "取得失敗，可手動填寫（不保證生效）"
    pickModel = "選擇模型"
    imageGenParams = "生成參數"
    imageRatio = "比例"
    imageResolution = "解析度"
    imageStyle = "預設風格"
    imageStyleNone = "無"
    imageSize = "尺寸"
    genParamsDisclaimer = "比例與解析度以 size 參數隨請求傳送，預設風格附加於提示詞；是否生效取決於模型服務。取得預設失敗時可手動填寫。"
    generateImage = "生成圖片"
    generateSceneImage = "生成目前場景圖"
    sceneImageVisualOnly = "目前場景圖 · 僅供視覺呈現，不寫入劇情記憶"
    enhancedSceneContinuity = "增強同元延續"
    enhancedSceneContinuityDesc = "使用最近保留的場景圖延續服裝、配飾與道具；最新劇情文字和人物設定優先。刪除的場景圖不再參與參考。"
    sceneErrorNoModel = "未設定可用的生圖模型，請檢查角色或全域生圖設定。"
    sceneErrorAuth = "生圖服務的 API Key 無效或沒有模型權限，請檢查設定。"
    sceneErrorEndpoint = "生圖介面或模型不存在；有參考圖時，請確認服務支援 images/edits。"
    sceneErrorQuota = "生圖服務額度不足，請檢查服務餘額或配額。"
    sceneErrorRateLimit = "生圖服務已限流，請稍後再試；不會自動重複提交付費請求。"
    sceneErrorSafety = "內容觸及生圖模型的安全限制，無法生成。請調整提示詞或參考圖後再試。"
    sceneErrorParameters = "生圖服務拒絕了請求參數，請檢查生圖協定、參考圖支援及提示詞長度；這不等同於內容違規。"
    sceneErrorServer = "生圖服務端出錯，請稍後再試。"
    sceneErrorEmpty = "服務未回傳有效圖片，請檢查介面是否相容 images 生圖協定。"
    sceneErrorTimeout = "等待生圖逾時。服務可能仍在處理，請確認服務狀態後再手動重試。"
    sceneErrorNetwork = "無法連線生圖服務，請檢查網路、代理和介面位址。"
    sceneErrorReferences = "角色參考圖無法讀取，請重新選擇參考圖後再試。"
    sceneErrorUnknown = "生圖未完成，服務沒有給出可識別的失敗原因。請核對模型與介面。"
    sceneErrorRequestId = "服務請求編號"
    sceneErrorAddress = "無法解析生圖 API 位址，請檢查 URL 主機名稱是否正確，以及網路、代理或 DNS。"
    sceneErrorTls = "與生圖服務建立安全連線失敗，請檢查 HTTPS 位址、憑證、裝置時間或代理。"
    sceneErrorConfiguration = "生圖 API 位址或請求設定無效，請檢查模型 ID、API URL 和 API Key 的填寫。"
    sceneErrorModel = "生圖模型不存在或服務目前沒有可用渠道，請檢查模型 ID 和服務商支援。"
    originalLearning = "原文學習"
    originalLearningHint = "貼上小說原文，尤其是該角色的語言、動作與敘述片段（可選）"
    originalLearningDescription = "重點學習角色語氣、性格細節和原文筆法；劇情補足也會參考敘事與描寫風格。結合既有設定，不將原文當作目前劇情。長篇素材會增加模型的上下文用量。"
    appIcon = "圖示"
    appIconBlue = "簡F"
    appIconClassic = "菱星"
    appIconLunhui = "輪迴"
    appIconGongming = "共鳴"
    appIconHuanmeng = "幻夢"
    appIconXinsheng = "新生"
    appIconRixiang = "日象"
    appIconHailuo = "海螺"
    appIconRestart = "選擇圖示後點擊儲存，FreeChat 將立即重新啟動並載入新圖示。"
    appIconActive = "正在使用"
    appIconSelected = "已選擇"
    appIconSaveFailed = "圖示選擇未儲存，請重試。"
    appIconRestartWarning = "更換圖示需要重新啟動軟體，目前有對話正在思考/回覆，可能會導致該條思考/回覆中斷，確定現在重新啟動？"
    appIconDoNotChange = "暫不更換"
    appIconRestartNow = "立即重新啟動"
    appIconRestarting = "正在重新啟動…"
    quickLocate = "快速定位"
    locateStart = "定位到對話開頭"
    locateEnd = "定位到最新訊息"
    feedbackEntry = "問題回報或建議意見"
    feedbackTitle = "回饋與建議"
    feedbackLabel = "回饋或建議"
    feedbackHint = "描述遇到的問題、重現步驟，或你希望加入的功能…"
    feedbackDescription = "無需登入。回饋內容會傳送給開發者，並附帶裝置型號、系統版本、應用版本等排查資訊；登入後還會關聯你的帳號。請勿填寫 API Key、密碼等敏感資訊。"
    feedbackSubmit = "提交"
    feedbackSending = "傳送中…"
    feedbackSent = "已收到你的回饋，感謝！"
    feedbackSendFailed = "傳送失敗，請檢查網路後重試。內容已保留。"
    feedbackMy = "我的回饋"
    feedbackMyEmpty = "還沒有提交過回饋"
    unfavoriteKeepsOriginal = "僅取消收藏，原聊天訊息、圖片及相關記憶會保留。收藏狀態會同步到雲端。"
    quickLocateTimeFormat = "yyyy年M月d日 HH:mm"
    generatedImageLoading = "圖片載入中…"
    generatedImageUnavailable = "圖片無法載入，可能是網路異常或圖片連結已失效。可重新載入；不會再次提交生圖請求。"
    generatedImageMissing = "本機圖片檔案已遺失或無法讀取，請重新生成圖片。"
    generatedImageNoResult = "這筆圖片記錄沒有可顯示的檔案或連結。請在原裝置查看，或重新生成圖片。"
    retryImageLoading = "重新載入"
    inputBox = "輸入框"
    inputStyle = "輸入框樣式"
    inputStyleCompact = "簡潔"
    inputStyleComplete = "完整"
    inputStyleCompactDesc = "單行輸入框，長文本可展開全螢幕輸入"
    inputStyleCompleteDesc = "兩行完整輸入框，換行自動長高，狀態快捷鍵常駐底行"
    inputBarState = "輸入框狀態"
    inputBarStatePinned = "永久固定"
    inputBarStateAutoHide = "自動隱藏"
    inputBarStatePinnedDesc = "輸入框固定在螢幕下方，不隨滑動隱藏"
    inputBarStateAutoHideDesc = "上滑回看訊息時自動隱藏，回到最新時恢復"
    generationPhaseLabels = mapOf(
        GenerationPhase.CONNECTING to "連接模型…", GenerationPhase.UNDERSTANDING to "理解內容…",
        GenerationPhase.SEARCHING to "搜尋資訊…", GenerationPhase.DEEP_RETRIEVAL to "深度檢索…",
        GenerationPhase.DRAFTING to "擬草回覆…", GenerationPhase.IMAGE_CONTEXT to "理解場景…",
        GenerationPhase.IMAGE_REFERENCES to "準備角色參考圖…", GenerationPhase.IMAGE_CONNECTING to "連接生圖模型…",
        GenerationPhase.IMAGE_GENERATING to "模型生成中…", GenerationPhase.IMAGE_RECEIVING to "接收圖片…"
    )
    resolutionLabels = listOf("1024" to "標清", "1536" to "高清", "2048" to "超清")
    styleLabels = listOf(
        "photo" to "寫實照片", "anime" to "動漫插畫", "oil" to "油畫",
        "watercolor" to "水彩", "threeD" to "3D渲染", "flat" to "扁平插畫",
        "pixel" to "像素風", "cyber" to "賽博朋克", "ink" to "水墨國風"
    )
    deleteModel = "刪除"
    unsavedTitle = "未儲存變更"
    unsavedMessage = "目前有未儲存的變更，確定要放棄嗎？"
    userAgreement = "用戶協議與使用條款"
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
    syncConflictCopiesCleaned = { "已自動清理 $it 條重複的衝突副本，保留原對話和一份副本" }
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
    attachmentMissing = "附件的本機檔案已遺失或無法存取，請重新上傳。"
    attachmentUnsupported = "暫無法擷取此格式的正文；可改為 TXT、JSON、CSV、DOCX、XLSX、PPTX，或貼上文字。"
    attachmentTooLarge = "附件超出本機安全讀取大小，請分割後上傳（文字不超過 2 MB，Office 檔案不超過 16 MB）。"
    attachmentEmpty = "附件為空或未擷取到文字；掃描件及圖片型文件需要先辨識文字。"
    attachmentReadFailed = "附件讀取失敗，檔案可能損壞或格式與副檔名不符，請檢查後重新上傳。"
    attachmentTruncated = "附件正文較長，本輪僅提供前部節選；未提供的部分不得猜測。"
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
    notEntered = "未輸入"
    followGlobal = "跟隨全域"
    followGlobalDefault = "跟隨全域（預設）"
    listSeparator = "、"
    genderKeys = listOf("男", "女")
    genderLabels = listOf("男", "女")
    personalityPresetLabels = listOf("溫柔隨和", "理性冷靜", "幽默搞怪", "直率犀利")
    relationshipPresetLabels = listOf("女朋友", "男朋友", "閨蜜", "朋友", "同學", "同事", "網友", "助理", "老師", "學生", "兄弟", "陌生人")
    builtInModelDesc = { id ->
        when (id) {
            "mimo-v2.6-flash" -> "Xiaomi 深度推理模型，作者自用 API，不保證隨時在線。"
            "ep-20260629143810-ffvjl" -> "Volcano Engine 輕量級生圖模型，作者自用 API，不保證隨時在線。"
            "MiMo-V2.5-TTS" -> "小米語音合成模型，作者自用 API，不保證隨時在線。"
            else -> ""
        }
    }

    agreementSections = listOf(
        AgreementSectionText("一、關於本軟體", listOf(
            "FreeChat 是一款開源的 AI 聊天客戶端，本身**不提供任何 AI 模型服務**，亦不承諾功能永久可用。",
            "本軟體按**「現狀」**提供，作者將持續維護，但無法保證不存在缺陷或服務不中斷。"
        )),
        AgreementSectionText("二、模型需自備", listOf(
            "除作者內建的少量模型外，AI 能力需您自行設定第三方 API Key。第三方服務的可用性、價格與內容政策由服務商獨立決定，與作者無關。",
            "內建模型為作者本人使用的 API，**不保證隨時保有額度**，可能隨時失效。",
            "「自訂語音音色」等語音功能會把您上傳或錄製的音訊樣本、音色描述傳送至第三方語音服務（小米 MiMo）進行合成處理；音訊樣本僅保存在本機，不參與雲同步。"
        )),
        AgreementSectionText("三、帳號與伺服器（可選）", listOf(
            "您**可以不註冊**。不註冊時全部功能均可正常使用，資料僅保存在本裝置。",
            "註冊後，伺服器提供跨裝置同步服務（含網頁版訪問），僅儲存您主動同步的對話、記憶與圖片等資料，不作他用。第三方 API Key **僅保存於裝置本地，不會上傳至伺服器**。",
            "「連接微信」功能的對話經由微信 ClawBot 與伺服器轉發處理。雲端並非保險箱，請勿將伺服器作為唯一備份。"
        )),
        AgreementSectionText("四、您的責任", listOf(
            "透過本軟體發出的全部請求均視為**您本人的行為**，您需對輸入與輸出的內容負責，包括：",
            "· 確保輸入內容不違反法律法規",
            "· 自行判斷輸出內容的合法性與準確性",
            "· 不利用本軟體生成、傳播違法或不良資訊",
            "· 「線上網頁分享」生成的公開連結可被任何持有連結者訪問，請自行斟酌分享內容",
            "· 「自訂語音音色」僅限使用您本人或已獲授權的聲音樣本，嚴禁複製、模仿他人聲音用於冒充身分、詐欺、誹謗或任何違法用途",
            "AI 的輸出可能包含錯誤，請自行判斷，不應視為專業意見。"
        )),
        AgreementSectionText("五、未成年人", listOf(
            "若您未滿 18 歲，請在監護人陪同下使用本軟體。"
        )),
        AgreementSectionText("六、開源聲明", listOf(
            "本專案基於 MIT License 開源，您可自由使用、修改與散布，但須保留原始著作權聲明。"
        )),
        AgreementSectionText("七、責任範圍", listOf(
            "若您將本軟體用於違法用途，作者**不承擔連帶責任**。因使用本軟體產生的其他直接或間接後果，作者亦不作出超出法律要求的承諾。"
        ))
    )
    agreementGateHead = "使用前請閱讀："
    agreementGateItems = listOf(
        "1. FreeChat 為開源 AI 聊天客戶端，不提供模型服務，需自行設定第三方模型 API。",
        "2. 內建模型為作者自備的 API，不保證隨時可用或保有額度。",
        "3. 無需註冊即可完整使用；註冊僅用於跨裝置同步，同步內容僅限您主動同步的資料。API Key 僅保存於本地，不會上傳至伺服器。",
        "4. 請勿利用本軟體生成、儲存或傳播違反法律法規的內容。",
        "5. 「自訂語音音色」僅限使用您本人或已獲授權的聲音樣本，嚴禁複製他人聲音用於冒充、詐欺等違法用途；音訊樣本會傳送至第三方語音服務處理。",
        "6. 本軟體僅供學習與研究使用，商用請自行評估相關法律風險。"
    )
    agreementAgreePrefix = "我已閱讀並同意"
    agreementDocName = "《用戶協議與使用條款》"
    agreementContinue = "確定並繼續"
    agreementReleasePage = "發布頁："
    agreementWebLabel = "FreeChat官網："

    // ===== 關於作者 =====
    authorAboutTitle = "關於作者"
    authorPageTitle = "你想知道什麼?"
    authorQa = listOf(
        AuthorQa("叫什麼？", "Belate / 胡勃陽"),
        AuthorQa("男女？", "男"),
        AuthorQa("幾歲？", "剛 20")
    )
    authorWordsTitle = "作者的話"
    authorWordsOpen = "點擊閱讀"
    authorWebAddress = "官方網頁："
    authorWebNote = "網域已過審，直接用網域 —— 無毒無害。"
    authorGithubRepo = "GitHub 倉庫："
    authorGithubNote = "覺得不錯的話，點個 Star。"
    authorCsdn = "CSDN 主頁："
    authorCsdnNote = "技術筆記與開發記錄。"
    authorContact = "任何建議和意見，直接與我溝通："
    authorQrHint = "長按 QR Code 可儲存至相簿"
    saveQr = "儲存 QR Code"
    saveToGallery = "儲存至相簿"
    aboutFreeChat = "關於FreeChat"
    checkUpdate = "檢查更新"
    updateChecking = "檢查中…"
    updateLatest = "已是最新版本"
    updateDownloading = "發現新版本，正在下載…"
    updateInstalling = "下載完成，正在拉起安裝…"
    updateFailed = "檢查更新失敗，請稍後重試"
    updateVerifyFailed = "安裝包驗證失敗，已取消安裝"
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
    attachmentMenu = "Attachments and tools"
    imagePreview = "Image Preview"
    previousImage = "Previous image"
    nextImage = "Next image"
    jumpToFunctionalSettings = "Jump to functional settings"
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
    sectionSystemTheme = "Personalization"
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
    headerBarStyle = "Status bar style"
    back = "Back"
    openDrawer = "Open sidebar"
    headerBarStyleCard = "Card"
    headerBarStyleCutout = "Cutout"
    headerBarStyleCardDesc = "Title-bar icons sit on raised circular cards"
    headerBarStyleCutoutDesc = "Title-bar icons sit directly on the blurred backdrop"
    systemDarkTheme = "System Dark Theme"
    liquidBackdrop = "Flowing Aurora"
    liquidBackdropDesc = "A soft-light gradient background for a nicer look"
    liquidBackdropConfirm = "Dynamic gradients use more processing power and can increase heat and battery drain. Enable them?"
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
    colorThemeBrown = "Light Brown"
    colorThemeBlue = "Light Blue"
    colorThemeWhite = "Black & White"
    colorThemeCustom = "Custom color"
    colorThemePine = "Pine green"
    colorThemeCoral = "Coral red"
    customHue = "Hue"
    customSaturation = "Saturation"
    customBrightness = "Brightness"
    customOpacity = "Opacity"
    customColorPreview = "Chosen color"
    customColorApplied = "Interface preview"
    customColorHint = "Backgrounds and text stay neutral; controls adjust contrast automatically."
    customColorApply = "Apply custom color"
    customPresetColors = "Back to presets"
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
        "Still up? Same here", "Hey, quiet hour — just you and me",
        "This time of night is all yours", "Late night vibes, no rush",
        "Can't sleep? That's okay", "What's on your mind at this hour?",
        "Hey, I'm up too", "The night's still young",
        "Quiet hours are the best hours", "Still here? Me too"
    )
    greetingsEarlyMorning = listOf(
        "Morning.", "Hey, good morning", "Morning — new day ahead",
        "Early start. What's up?", "Good morning!", "Morning, I'm here",
        "Rise and... well, whenever you're ready", "Morning. Coffee first?"
    )
    greetingsMorningWeekday = listOf(
        "Good morning!", "Morning!", "Hey, good morning — what's up?",
        "Morning, let's make it a good one", "Morning, feeling good?",
        "Good morning, I'm here", "Morning! Anything on your mind?",
        "Hey morning, ready to go?"
    )
    greetingsMorningWeekend = listOf(
        "Weekend morning!", "Happy weekend — take it easy",
        "Weekend vibes, no rush", "Good morning, happy weekend",
        "Weekend! What are you up to?", "Enjoying the weekend?",
        "Weekend morning, relax a bit", "Weekend mornings are the best"
    )
    greetingsNoon = listOf(
        "Hey, noon already", "Lunchtime! Eaten yet?",
        "Midday break — how's it going?", "Noon! What's up?",
        "Lunchtime, grab a bite", "Hey, midday check-in",
        "Noon! Take a break", "Lunchtime, I'm here"
    )
    greetingsAfternoon = listOf(
        "Afternoon!", "Hey, good afternoon", "How's the afternoon going?",
        "Afternoon, what's new?", "Good afternoon — I'm here",
        "Afternoon! What are you up to?", "Hey, afternoon vibes",
        "Good afternoon, feeling alright?"
    )
    greetingsEvening = listOf(
        "Good evening!", "Hey, evening!", "Evening — had dinner yet?",
        "Good evening, how was your day?", "Evening! Time to unwind",
        "Hey, good evening — I'm here", "Evening, what's up?",
        "Good evening, take it easy"
    )
    greetingsNight = listOf(
        "Hey, this hour is yours", "Still up? Nice",
        "Late night, quiet and chill", "This time of night hits different",
        "Night owl hours", "Late night — what's on your mind?",
        "The quiet hours, I like it", "Still awake? Same",
        "Night vibes", "This time, just right"
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
    dialogueModeWechatDesc = "Real-time online conversation: spoken language, short bursts and a casual tone"
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
    wechatExampleUser = listOf("Have you eaten?", "Pork rice!", "It's so good!")
    wechatExampleAi = listOf("Just got to the cafeteria", "Snail noodles or pork rice, what do you think?", "Okay, snail noodles it is then")
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
    timePerception = "Time Perception"
    timePerceptionDesc = "The character has an independent personality, life and daily routine aligned with real-world time"
    timePerceptionConfirm = "The AI has its own daily routine, so it may not reply to messages."
    chooseDialogueMode = "Choose Dialogue Mode"
    deepThinkingMode = "Deep Thinking"
    deepThinkingModeDesc = "Enabling this allows deeper reasoning; turning it off favors faster replies. Web search is still used only when the question needs it."
    modelDeepThinkingLabel = "Supports deep thinking"
    modelCapabilitiesLabel = "Model capabilities"
    modelCapabilitiesDesc = "Declare what the API supports here; this does not enable it. Global settings or conversation controls decide whether to use it."
    modelDeepThinkingDefault = "Enable deep thinking by default"
    deepThinkSettingDesc = "For complex questions; may significantly increase reply time"
    deepThinkUnsupported = "Selected model doesn't support this"
    optionOn = "On"
    optionOff = "Off"
    nativeSearchLabel = "Supports native web search"
    nativeSearchDesc = "Only supported MiMo or DashScope search endpoints use this capability. Other endpoints use your selected search source. Declare the API's actual capabilities, not assumptions based on its model name"
    searchSource = "Search source"
    showSearchSources = "Show information sources"
    showSearchSourcesDesc = "Show collapsible web sources below replies; off by default"
    informationSources = "Information sources"
    disclosureExpanded = "Expanded"
    disclosureCollapsed = "Collapsed"
    searchFree = "Free public search"
    searchSourceDesc = "Uses supported native model search by default. You can also select key-free AnySearch or your own Tavily, Brave, SearXNG or Firecrawl API. Third-party quotas and fees apply. Keys are encrypted locally and excluded from sync and backups"
    searchAnySearchDesc = "Use the official anonymous API without a key, subject to per-IP limits and free quota. You may supply your own key and pay any provider charges. Queries are sent to AnySearch; relevance and freshness still need verification. If quota runs out, the app does not automatically use returned credentials or switch to paid requests"
    searchEndpoint = "Full search endpoint URL (HTTPS)"
    searchOptionalKey = "API Key (optional for anonymous or unauthenticated APIs)"
    searchTest = "Test search"
    searchTesting = "Testing…"
    searchTestCost = "Testing sends a query and may use provider credits. SearXNG requires JSON output. The endpoint must support the selected protocol"
    searchTestOk = "Search succeeded"
    searchSaveFailed = "Could not save. Please try again"
    originalText = "Text"
    copyOriginal = "Copy text"
    paste = "Paste"
    fullscreenInput = "Fullscreen input"
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
    shareAsLink = "Share as online link"
    shareLinkCreating = "Creating share link…"
    shareLinkDialogTitle = "Share link ready"
    shareLinkHint = "Anyone with the link can view it online — no FreeChat install needed"
    shareLinkCopy = "Copy link"
    shareLinkCopied = "Link copied"
    shareLinkNeedLogin = "Sign in to create an online link"
    shareLinkCreateFailed = "Could not create the link, try again later"
    shareLinkTooMuch = "Online sharing supports up to 20 messages and 30,000 characters"
    myShares = "My shares"
    mySharesEmpty = "No share links yet"
    shareRevoke = "Revoke"
    shareRevokeConfirm = "The link stops working immediately after revoking"
    shareRevoked = "Revoked"
    wechatBind = "Connect WeChat"
    modeNext = "Next"
    modeRepick = "Choose again"
    importModeMismatchTitle = "Dialogue mode mismatch"
    importModeMismatchDesc = "The imported character uses a different dialogue mode. Switch to the imported mode, or import under the current mode — applicable settings are written, conflicting/extra fields dropped, missing fields stay empty."
    importSwitchToMode = "Switch to imported mode"
    importKeepMode = "Import under current mode"
    wechatBindDesc = "Attach this character to WeChat: friends message her in WeChat and she replies like a real person. Scanning the QR grants access; replies are generated via FreeChat cloud — no background app needed. Sign-in required."
    wechatBindQrTitle = "Scan with WeChat to authorize"
    wechatBindWaiting = "Waiting for scan…"
    wechatBindScanned = "Scanned — confirm on your phone"
    wechatBindConfirmed = "Connected"
    wechatBindExpired = "QR code expired — reopen to refresh"
    wechatBindNeedVerify = "Enter the verification code shown on your phone"
    wechatBindConnected = "WeChat connected"
    wechatBindDisconnected = "Not connected"
    wechatBindPaused = "Temporarily limited by WeChat — auto-recovers shortly"
    wechatBindUnbind = "Disconnect"
    wechatBindFailed = "Connection failed, try again later"
    wechatConnectedTo = { x -> "Connected to WeChat account $x" }
    wechatTakeoverDesc = { x -> "Only one ClawBot is allowed. \"$x\" is already connected to WeChat. Connecting this chat will disconnect \"$x\". Continue?" }
    wechatNoticeTitle = "Usage Notes"
    wechatBetaHint = "This feature is in beta and may be unstable."
    wechatNotice1 = "This feature syncs data through the cloud and does not rely on FreeChat running in the background; you must be signed in to use it."
    wechatNotice2 = "Currently this feature only supports the built-in model (mimo-v2.6-flash)."
    wechatNotice3 = "The character's avatar and display name can be changed in the WeChat ClawBot chat settings."
    wechatNotice4 = "WeChat only bridges you and the server; FreeChat chat history is not mirrored into WeChat. Character simulation settings must still be adjusted in the FreeChat app."
    wechatNotice5 = "Replies via WeChat may take longer and may be unstable."
    wechatLoginUse = "Sign in to use"
    wechatInputNotice = "WeChat cannot access FreeChat client chat history. For a coherent conversation, it is recommended to chat with this character in WeChat ClawBot."
    wechatInputReveal = "To chat here anyway, tap here"
    wechatVerifyPlaceholder = "Enter the code shown on your phone"
    wechatVerifySubmit = "Submit"
    addModel = "Add Model"
    editModel = "Edit Model"
    modelName = "Model Name"
    modelNameHint = "Custom name (defaults to model ID)"
    apiKeyLabel = "API Key"
    apiKeyHint = "e.g. sk-xxxxxxxx (required)"
    modelIdLabel = "Model ID"
    modelIdHint = "e.g. deepseek-v4-flash (required)"
    apiUrlLabel = "URL"
    apiUrlHint = "e.g. https://api.deepseek.com (required)"
    modelNote = "Note"
    modelNoteHint = "Optional description"
    saveModel = "Save"
    fetchModelsLabel = "Fetch model list"
    fetchingModels = "Fetching…"
    fetchFailed = "Fetch failed — fill in manually (effect not guaranteed)"
    pickModel = "Pick a model"
    imageGenParams = "Generation params"
    imageRatio = "Aspect ratio"
    imageResolution = "Resolution"
    imageStyle = "Style preset"
    imageStyleNone = "None"
    imageSize = "Size"
    genParamsDisclaimer = "Ratio and resolution are sent as the size parameter; style presets are appended to the prompt. Whether they take effect depends on the model service. If preset fetch fails, fill in manually."
    generateImage = "Generate image"
    generateSceneImage = "Generate current scene"
    sceneImageVisualOnly = "Scene illustration · Visual only, excluded from story memory"
    enhancedSceneContinuity = "Enhanced scene continuity"
    enhancedSceneContinuityDesc = "Use recent retained scenes to keep clothing, accessories, and props consistent. Current story text and character settings take priority. Deleted scenes are excluded."
    sceneErrorNoModel = "No image model configured. Check the character or global image model."
    sceneErrorAuth = "The image service rejected the API key or model permissions. Check configuration."
    sceneErrorEndpoint = "Image endpoint or model not found. With references, the service must support images/edits."
    sceneErrorQuota = "The image service quota or balance is exhausted."
    sceneErrorRateLimit = "The image service is rate-limiting requests. Retry later; paid requests are not retried automatically."
    sceneErrorSafety = "The image model blocked this content under its safety policy. Adjust the prompt or references before retrying."
    sceneErrorParameters = "The image service rejected request parameters. Check image API compatibility, reference support and prompt length; this does not imply a safety violation."
    sceneErrorServer = "The image service returned a server error. Try again later."
    sceneErrorEmpty = "No valid image returned. Check compatibility with the images API."
    sceneErrorTimeout = "Image generation timed out. The service may still be processing; check its status before retrying."
    sceneErrorNetwork = "Cannot connect to the image service. Check network, proxy and endpoint."
    sceneErrorReferences = "Cannot read the character reference images. Select them again and retry."
    sceneErrorUnknown = "Image generation did not complete, with no recognized error reason. Check model and endpoint."
    sceneErrorRequestId = "Service request ID"
    sceneErrorAddress = "Cannot resolve the image API host. Check the URL spelling, network, proxy or DNS."
    sceneErrorTls = "Cannot establish a secure connection. Check HTTPS endpoint, certificates, device time or proxy."
    sceneErrorConfiguration = "Invalid image API address or request configuration. Check model ID, API URL and API key."
    sceneErrorModel = "Image model not found or no provider channel available. Check the model ID and provider support."
    originalLearning = "Original text learning"
    originalLearningHint = "Paste novel excerpts featuring the character's dialogue, actions and narration (optional)"
    originalLearningDescription = "Prioritizes the character's voice, nuanced behavior and prose style; plot completion also learns narration and descriptions. Combined with your settings, not treated as current story events. Long excerpts increase context usage."
    appIcon = "App icon"
    appIconBlue = "Simple F"
    appIconClassic = "Diamond Star"
    appIconLunhui = "Samsara"
    appIconGongming = "Resonance"
    appIconHuanmeng = "Dreamscape"
    appIconXinsheng = "Newborn"
    appIconRixiang = "Sun"
    appIconHailuo = "Conch"
    appIconRestart = "Choose an icon and tap Save. FreeChat will restart immediately with the new icon."
    appIconActive = "In use"
    appIconSelected = "Selected"
    appIconSaveFailed = "Couldn't save the icon choice. Please retry."
    appIconRestartWarning = "Changing the icon restarts FreeChat. A conversation is currently thinking or replying and may be interrupted. Restart now?"
    appIconDoNotChange = "Don't change now"
    appIconRestartNow = "Restart now"
    appIconRestarting = "Restarting…"
    quickLocate = "Quick locate"
    locateStart = "Go to conversation start"
    locateEnd = "Go to latest message"
    feedbackEntry = "Report a problem or suggest an idea"
    feedbackTitle = "Feedback & suggestions"
    feedbackLabel = "Your feedback or suggestion"
    feedbackHint = "Describe the problem, steps to reproduce it, or a feature you'd like…"
    feedbackDescription = "No sign-in required. Your feedback is sent to the developer together with device model, OS version and app version for troubleshooting; if you are signed in it is also linked to your account. Do not include sensitive information such as API keys or passwords."
    feedbackSubmit = "Submit"
    feedbackSending = "Sending…"
    feedbackSent = "Feedback received. Thank you!"
    feedbackSendFailed = "Failed to send. Check your network and try again. Your text is kept."
    feedbackMy = "My feedback"
    feedbackMyEmpty = "No feedback submitted yet"
    unfavoriteKeepsOriginal = "Only the favorite will be removed. Original messages, images and related memories are retained. The favorite state syncs to the cloud."
    quickLocateTimeFormat = "MMM d, yyyy HH:mm"
    generatedImageLoading = "Loading image…"
    generatedImageUnavailable = "Couldn't load this image. The network may be unavailable or the image link may have expired. Reloading will not submit another generation request."
    generatedImageMissing = "The local image is missing or unreadable. Please regenerate it."
    generatedImageNoResult = "This image record has no displayable file or link. View it on the original device or regenerate it."
    retryImageLoading = "Reload image"
    inputBox = "Input box"
    inputStyle = "Input Box Style"
    inputStyleCompact = "Compact"
    inputStyleComplete = "Complete"
    inputStyleCompactDesc = "Single-line box; long text expands to fullscreen input"
    inputStyleCompleteDesc = "Two-line full box that grows in place; status keys live on the bottom row"
    inputBarState = "Input Box State"
    inputBarStatePinned = "Always Pinned"
    inputBarStateAutoHide = "Auto Hide"
    inputBarStatePinnedDesc = "Pin the box to the bottom; never auto-hides"
    inputBarStateAutoHideDesc = "Hide while scrolling up through history; restore at the latest"
    generationPhaseLabels = mapOf(
        GenerationPhase.CONNECTING to "Connecting to model…", GenerationPhase.UNDERSTANDING to "Understanding…",
        GenerationPhase.SEARCHING to "Searching…", GenerationPhase.DEEP_RETRIEVAL to "Reading search results…",
        GenerationPhase.DRAFTING to "Drafting reply…", GenerationPhase.IMAGE_CONTEXT to "Preparing scene context…",
        GenerationPhase.IMAGE_REFERENCES to "Preparing character references…", GenerationPhase.IMAGE_CONNECTING to "Connecting to image model…",
        GenerationPhase.IMAGE_GENERATING to "Generating image…", GenerationPhase.IMAGE_RECEIVING to "Receiving image…"
    )
    resolutionLabels = listOf("1024" to "SD", "1536" to "HD", "2048" to "UHD")
    styleLabels = listOf(
        "photo" to "Photorealistic", "anime" to "Anime", "oil" to "Oil painting",
        "watercolor" to "Watercolor", "threeD" to "3D render", "flat" to "Flat illustration",
        "pixel" to "Pixel art", "cyber" to "Cyberpunk", "ink" to "Ink wash"
    )
    deleteModel = "Delete"
    unsavedTitle = "Unsaved Changes"
    unsavedMessage = "You have unsaved changes. Discard them?"
    userAgreement = "User Agreement and Terms of Use"
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
    syncConflictCopiesCleaned = { "Automatically removed $it duplicate conflict copies, keeping the original and one copy." }
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
    attachmentMissing = "The local attachment is missing or inaccessible. Please upload it again."
    attachmentUnsupported = "Text extraction is not supported for this format. Use TXT, JSON, CSV, DOCX, XLSX or PPTX, or paste the text."
    attachmentTooLarge = "The attachment exceeds the safe local reading limit. Split it first (text: 2 MB; Office files: 16 MB)."
    attachmentEmpty = "The attachment is empty or contains no extractable text. Scans and image-only documents need text recognition first."
    attachmentReadFailed = "The attachment could not be read. Check for corruption or an incorrect file extension, then upload it again."
    attachmentTruncated = "Only the beginning of this long attachment is supplied in this request. Do not guess omitted content."
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
    notEntered = "Not entered"
    followGlobal = "Use global"
    followGlobalDefault = "Use global (default)"
    listSeparator = ", "
    genderKeys = listOf("男", "女")
    genderLabels = listOf("Male", "Female")
    personalityPresetLabels = listOf("Gentle", "Rational", "Humorous", "Blunt")
    relationshipPresetLabels = listOf("Girlfriend", "Boyfriend", "Best friend", "Friend", "Classmate", "Colleague", "Online friend", "Assistant", "Teacher", "Student", "Brother", "Stranger")
    builtInModelDesc = { id ->
        when (id) {
            "mimo-v2.6-flash" -> "Xiaomi deep-reasoning model. Author's own API — not guaranteed to be online."
            "ep-20260629143810-ffvjl" -> "Volcano Engine lightweight image model. Author's own API — not guaranteed to be online."
            "MiMo-V2.5-TTS" -> "Xiaomi text-to-speech model. Author's own API — not guaranteed to be online."
            else -> ""
        }
    }

    agreementSections = listOf(
        AgreementSectionText("1. About this app", listOf(
            "FreeChat is an open-source AI chat client. It **does not provide any AI model service**, and no feature is promised to be available forever.",
            "The app is provided **\"as is\"**. The author maintains it on a best-effort basis, but cannot guarantee it is free of defects or interruptions."
        )),
        AgreementSectionText("2. You supply the models", listOf(
            "Apart from the few models built in by the author, every AI capability requires you to configure a third-party API key. The availability, pricing and content policy of those services are determined independently by the provider and are not related to the author.",
            "The built-in models are the author's own APIs and are **not guaranteed to carry quota at any time**. They may stop working without notice.",
            "Voice features such as \"Custom voice tone\" send the audio samples you upload or record, together with your voice description, to a third-party speech service (Xiaomi MiMo) for synthesis. Audio samples are stored on this device only and never take part in cloud sync."
        )),
        AgreementSectionText("3. Account & server (optional)", listOf(
            "You **do not have to sign up**. All features work without an account, and your data stays on this device only.",
            "With an account, the server provides cross-device synchronization (including web access) and stores only the conversations, memories and images you actively sync. Nothing else is done with them. Third-party API keys are **stored on your device only and are never uploaded to the server**.",
            "Conversations conducted through the \"Connect to WeChat\" feature are relayed via WeChat ClawBot and the server. The cloud is not a safe deposit box — please do not treat the server as your only backup."
        )),
        AgreementSectionText("4. Your responsibility", listOf(
            "Every request sent from this app counts as **your own action**. You are responsible for what you enter and what you receive, including:",
            "· keeping your input lawful",
            "· judging for yourself whether the output is lawful and accurate",
            "· not using the app to create or spread illegal or harmful content",
            "· remembering that public links created via \"Web sharing\" can be opened by anyone who holds the link — share judiciously",
            "· using \"Custom voice tone\" only with your own voice or voices you are authorized to use — never cloning or imitating someone else's voice to impersonate, defraud, defame or break the law",
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
        "1. FreeChat is an open-source AI chat client. It provides no model service — configure your own third-party model APIs.",
        "2. The built-in models are the author's own APIs and are not guaranteed to be available or to carry quota.",
        "3. The app is fully usable without an account; signing up is only for cross-device synchronization of the data you actively sync. API keys are stored locally and are never uploaded to the server.",
        "4. Do not use this app to create, store or spread unlawful content.",
        "5. \"Custom voice tone\" may only use your own voice or voices you are authorized to use. Cloning others' voices for impersonation, fraud or any unlawful purpose is forbidden; audio samples are sent to a third-party speech service for processing.",
        "6. This app is for study and research. Assess the legal risk yourself before commercial use."
    )
    agreementAgreePrefix = "I have read and agree to"
    agreementDocName = "the User Agreement and Terms of Use"
    agreementContinue = "Agree and continue"
    agreementReleasePage = "Release page:"
    agreementWebLabel = "FreeChat website:"

    // ===== About the author =====
    authorAboutTitle = "About the author"
    authorPageTitle = "What do you want to know?"
    authorQa = listOf(
        AuthorQa("Name?", "Belate / 胡勃阳 (Hu Boyang)"),
        AuthorQa("Gender?", "Male"),
        AuthorQa("Age?", "Just turned 20")
    )
    authorWordsTitle = "Author's Words"
    authorWordsOpen = "Tap to read"
    authorWebAddress = "Official website:"
    authorWebNote = "The domain has cleared review — use it directly. Harmless, honestly."
    authorGithubRepo = "GitHub repo:"
    authorGithubNote = "A Star would be appreciated."
    authorCsdn = "CSDN blog:"
    authorCsdnNote = "Technical notes and development records."
    authorContact = "For any suggestion or feedback, talk to me directly:"
    authorQrHint = "Long-press the QR code to save it"
    saveQr = "Save QR code"
    saveToGallery = "Save to gallery"
    aboutFreeChat = "About FreeChat"
    checkUpdate = "Check for updates"
    updateChecking = "Checking…"
    updateLatest = "You are up to date"
    updateDownloading = "New version found, downloading…"
    updateInstalling = "Download complete, opening installer…"
    updateFailed = "Update check failed, please try again later"
    updateVerifyFailed = "Package verification failed, install cancelled"
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
