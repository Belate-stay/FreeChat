package com.freechat.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CalendarContract
import android.util.Base64
import android.util.Log
import com.freechat.data.MiMoAsr
import com.freechat.util.DocumentParser
import com.freechat.util.OoxmlGenerator
import com.freechat.util.DocContent
import com.freechat.util.DocSection
import com.freechat.util.SheetContent
import com.freechat.util.PresentationContent
import com.freechat.util.SlideContent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freechat.BuildConfig
import com.freechat.ReplyService
import com.freechat.data.MemoryManager
import com.freechat.data.SettingsRepository
import com.freechat.data.TtsController
import com.freechat.i18n.AppLanguage
import com.freechat.model.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val DOUBAO_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
        private val DOUBAO_API_KEY = BuildConfig.DOUBAO_API_KEY
        private const val XIAOMI_BASE_URL = "https://api.xiaomimimo.com/v1"
        private val XIAOMI_API_KEY = BuildConfig.XIAOMI_API_KEY
        private val XIAOMI_ULTRASPEED_API_KEY = BuildConfig.XIAOMI_ULTRASPEED_API_KEY
        private const val SERPAPI_BASE_URL = "https://serpapi.com/search"
        private val SERPAPI_API_KEY = BuildConfig.SERPAPI_API_KEY
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val CONVERSATIONS_FILE = "freechat_conversations.json"
        private const val PER_CONV_FILE = "freechat_perconv.json"
        private const val MEMORY_CONTEXT_THRESHOLD = 8
        private const val MAX_ATTACH_IMAGES = 9        // 单次最多 9 张
        private const val MAX_IMAGE_DIM = 2048          // 压缩上限（像素），减小识别请求体积、加速响应
        // 生图检测关键词
        private val IMAGE_GEN_KEYWORDS = listOf(
            "生成图片", "生成一张", "画一张", "画个", "画一幅", "画张",
            "图片生成", "帮我画", "给我画", "画图", "画画", "帮我生成",
            "配图", "插图", "生成图像", "做一张图", "生成一张图", "来张图",
            "generate image", "create image", "draw a", "make an image"
        )
        // 修图/p图/图生图关键词（用参考图生成新图）
        private val IMAGE_EDIT_KEYWORDS = listOf(
            "修图", "p图", "P图", "改图", "美化", "修一下", "修改图片",
            "换背景", "去水印", "抠图", "加滤镜", "调色", "改色",
            "把这张", "基于这张", "根据这张", "将这张", "这张图",
            "图片处理", "处理图片", "修改这张", "编辑图片", "优化图片",
            "增强", "修复", "变清晰", "风格转换", "风格化",
            "edit image", "fix image", "enhance image", "retouch"
        )
        // 识图/图片分析关键词
        private val IMAGE_ANALYSIS_KEYWORDS = listOf(
            "分析", "识别", "看看", "看看这", "描述", "这是什么", "有什么",
            "读图", "看图", "识图", "解析", "理解这张",
            "里面是", "图片里", "图中", "图上", "图里",
            "翻泽", "翻译图片", "提取文字", "OCR",
            "describe", "analyze", "what is", "tell me about"
        )
    }

    private val settingsRepo = SettingsRepository(application)
    private val memoryManager = MemoryManager(application.filesDir)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)  // 流式+搜索需要更久
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    // 用 InstanceCreator 提供默认值：老版本消息缺少 imagePaths/imageUrls/reasoningContent 等字段时，
    // 反序列化不会把它们置为 null，避免侧滑切换会话时 NPE 崩溃
    private val gson = GsonBuilder()
        .registerTypeAdapter(Message::class.java, InstanceCreator<Message> { _ -> Message(role = Role.USER, content = "") })
        .registerTypeAdapter(Conversation::class.java, InstanceCreator<Conversation> { _ -> Conversation() })
        .registerTypeAdapter(CharacterProfile::class.java, InstanceCreator<CharacterProfile> { _ -> CharacterProfile() })
        .create()

    // ========== 状态 ==========
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 拟人模式「对方正在输入...」状态（前端动画）
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    // 当前对话模式（标准/拟人）与拟人角色档案
    private val _currentMode = MutableStateFlow(ChatMode.STANDARD)
    val currentMode: StateFlow<ChatMode> = _currentMode.asStateFlow()
    private val _currentCharacter = MutableStateFlow<CharacterProfile?>(null)
    val currentCharacter: StateFlow<CharacterProfile?> = _currentCharacter.asStateFlow()

    // 实时思考计时（毫秒）
    private val _thinkingTimeMs = MutableStateFlow(0L)
    val thinkingTimeMs: StateFlow<Long> = _thinkingTimeMs.asStateFlow()

    // 实时推理内容
    private val _liveReasoning = MutableStateFlow("")
    val liveReasoning: StateFlow<String> = _liveReasoning.asStateFlow()

    // 实时流式回复
    private val _liveContent = MutableStateFlow("")
    val liveContent: StateFlow<String> = _liveContent.asStateFlow()

    // 图片生成中
    private val _isGeneratingImage = MutableStateFlow(false)
    val isGeneratingImage: StateFlow<Boolean> = _isGeneratingImage.asStateFlow()

    // 当前 API 调用引用，用于取消
    private val currentCall = AtomicReference<okhttp3.Call?>(null)
    private var streamJob: Job? = null

    // 拟人模式缓冲管线：按对话隔离（每个 convId 独立一条），切对话不打断原对话的思考回复；
    // 思考中被新消息打断会清理半截回复后重新计时，保证多条合一请求质量不降级。
    private class CompanionPipeline {
        val buffer = mutableListOf<CompanionTurn>()
        var job: Job? = null
        var replyStart = -1  // 本轮 AI 回复起始索引（被打断时清理半截回复）
        var jobId = 0        // 代次：旧 job 的 finally 不干扰新一轮
    }
    private val companionPipelines = mutableMapOf<String, CompanionPipeline>()
    private val userSaidGoodnightAt = mutableMapOf<String, Long>()   // convId -> 用户上次道晚安时间戳
    // 模拟作息状态（当天有效，跨天重新生成）
    private val sleepDateKey = mutableMapOf<String, String>()        // convId -> 作息所属日期
    private val sleepAtHour = mutableMapOf<String, Int>()
    private val wakeAtHour = mutableMapOf<String, Int>()
    private val sleepingMissedMessages = mutableMapOf<String, Int>() // convId -> 睡觉期间漏回条数

    // 每对话生成状态（loading / typing），与「当前显示对话」解耦
    private val convLoading = mutableMapOf<String, Boolean>()
    private val convTyping = mutableMapOf<String, Boolean>()

    // 本轮发送的起始索引（停止时据此截断「用户消息 + AI 回复」；-1 表示无进行中的普通发送轮）
    private var activeRoundStartIndex = -1

    // ===== 前后台状态 + 系统通知 =====
    private var appInForeground = true
    fun onAppForegroundChanged(foreground: Boolean) {
        appInForeground = foreground
        val app = getApplication<Application>()
        if (foreground) {
            ReplyService.stop(app)
            app.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        } else {
            // 退后台：若有生成在跑，启动前台服务保活（小米 HyperOS 杀后台很凶）
            if (convLoading.values.any { it } || convTyping.values.any { it } || _isLoading.value) {
                val title = _currentCharacter.value?.name?.ifBlank { "FreeChat" } ?: "FreeChat"
                ReplyService.startThinking(app, title)
            }
        }
    }

    /** 回复完成且处于后台时，发系统通知（悬浮 + 通知中心，对标微信新消息） */
    private fun notifyReplyIfBackground(title: String, content: String) {
        if (appInForeground || content.isBlank()) return
        ReplyService.showReply(getApplication(), title, content)
    }

    // 待发送的图片（已拷贝到内部存储：path 用于显示 + 持久化，mime 用于 API）
    private val _pendingImages = MutableStateFlow<List<PendingImage>>(emptyList())
    val pendingImages: StateFlow<List<PendingImage>> = _pendingImages.asStateFlow()

    // ★ 图片拷贝/压缩处理中 — 防止处理未完成就点发送导致图片丢失
    private val _isAddingImages = MutableStateFlow(false)
    val isAddingImages: StateFlow<Boolean> = _isAddingImages.asStateFlow()

    // 待发送的文件（已拷贝到内部存储，path 用于显示/解析，name 用于展示）
    private val _pendingFiles = MutableStateFlow<List<PendingFile>>(emptyList())
    val pendingFiles: StateFlow<List<PendingFile>> = _pendingFiles.asStateFlow()

    private val _isAddingFiles = MutableStateFlow(false)
    val isAddingFiles: StateFlow<Boolean> = _isAddingFiles.asStateFlow()

    private val _selectedModel = MutableStateFlow(
        ModelInfo("mimo-v2.5-pro-ultraspeed", "MiMo-V2.5-Pro-UltraSpeed", Provider.XIAOMI, "Xiaomi高速推理模型，作者自用API，不保证随时在线，可适当白嫖。", supportsWebSearch = true, supportsThinking = false, modelType = ModelType.LANGUAGE, isBuiltIn = true)
    )
    val selectedModel: StateFlow<ModelInfo> = _selectedModel.asStateFlow()

    // 视觉模型（生图）
    private val _selectedVisualModel = MutableStateFlow(
        ModelInfo("ep-20260629143810-ffvjl", "Doubao-Seedream-5.0-Lite", Provider.DOUBAO, "Volcano Engine轻量级生图模型，作者自用API，不保证随时在线，可适当白嫖。", modelType = ModelType.VISUAL, isBuiltIn = true)
    )
    val selectedVisualModel: StateFlow<ModelInfo> = _selectedVisualModel.asStateFlow()

    // 识图模型（视觉理解，无内置，需用户自行添加）
    private val _selectedVisionModel = MutableStateFlow<ModelInfo?>(null)
    val selectedVisionModel: StateFlow<ModelInfo?> = _selectedVisionModel.asStateFlow()

    // 用户自定义模型（所有类型），来自 DataStore
    private val _customModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val customModels: StateFlow<List<ModelInfo>> = _customModels.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _colorTheme = MutableStateFlow(ColorTheme.BROWN)
    val colorTheme: StateFlow<ColorTheme> = _colorTheme.asStateFlow()

    private val _tempMode = MutableStateFlow(TempMode.AUTO)
    val tempMode: StateFlow<TempMode> = _tempMode.asStateFlow()

    private val _lengthMode = MutableStateFlow(LengthMode.AUTO)
    val lengthMode: StateFlow<LengthMode> = _lengthMode.asStateFlow()

    private val _enableWebSearch = MutableStateFlow(true)
    val enableWebSearch: StateFlow<Boolean> = _enableWebSearch.asStateFlow()

    // 是否显示思考过程（默认关闭：思考中只显示灵动 AI 球）
    private val _showThinking = MutableStateFlow(false)
    val showThinking: StateFlow<Boolean> = _showThinking.asStateFlow()

    private val _appLanguage = MutableStateFlow(AppLanguage.SYSTEM)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _fontSize = MutableStateFlow(FontSize.MEDIUM)
    val fontSize: StateFlow<FontSize> = _fontSize.asStateFlow()

    // ===== TTS 语音输出 =====
    private val _voiceModel = MutableStateFlow("MiMo-V2.5-TTS")
    val voiceModel: StateFlow<String> = _voiceModel.asStateFlow()

    // ===== ASR 语音识别（无内置，用户自行添加）=====
    private val _asrModel = MutableStateFlow("")
    val asrModel: StateFlow<String> = _asrModel.asStateFlow()

    private val _ttsVoice = MutableStateFlow("mimo_default")
    val ttsVoice: StateFlow<String> = _ttsVoice.asStateFlow()

    private val _ttsSpeed = MutableStateFlow(1.0f)
    val ttsSpeed: StateFlow<Float> = _ttsSpeed.asStateFlow()

    private val _ttsPitch = MutableStateFlow(1.0f)
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    private val _ttsAutoPlay = MutableStateFlow(false)
    val ttsAutoPlay: StateFlow<Boolean> = _ttsAutoPlay.asStateFlow()

    // 是否自动总结对话内容以巩固 AI 记忆（默认开启）
    private val _autoSummarizeMemory = MutableStateFlow(true)
    val autoSummarizeMemory: StateFlow<Boolean> = _autoSummarizeMemory.asStateFlow()

    // 是否使用系统字体（关闭则使用自定义聊天字体）
    private val _useSystemFont = MutableStateFlow(false)
    val useSystemFont: StateFlow<Boolean> = _useSystemFont.asStateFlow()

    // 全局记忆点（用户自定义，注入系统提示词）
    private val _globalMemories = MutableStateFlow<List<String>>(emptyList())
    val globalMemories: StateFlow<List<String>> = _globalMemories.asStateFlow()

    // 高级材质：模糊等渲染效果开关（本版仅 UI 开关，具体渲染变化下个版本再接入）
    private val _advancedMaterial = MutableStateFlow(false)
    val advancedMaterial: StateFlow<Boolean> = _advancedMaterial.asStateFlow()

    // 跟随系统时暗色主题用「深色」还是「黑色」（false=深色 DARK，true=黑色 OLED）
    private val _systemDarkTheme = MutableStateFlow(false)
    val systemDarkTheme: StateFlow<Boolean> = _systemDarkTheme.asStateFlow()

    // 全局默认聊天模式（标准/拟人），设置页切换
    private val _chatMode = MutableStateFlow(ChatMode.STANDARD)
    val chatMode: StateFlow<ChatMode> = _chatMode.asStateFlow()

    // 是否已同意用户协议与免责声明（首次进入门槛）
    private val _hasAgreedTerms = MutableStateFlow(false)
    val hasAgreedTerms: StateFlow<Boolean> = _hasAgreedTerms.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val pinnedIds = MutableStateFlow<Set<String>>(emptySet())

    // ===== 侧滑搜索：匹配标题 + 对话内容（用户提示词 / AI 回复 / 附件名） =====
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Conversation>>(emptyList())
    val searchResults: StateFlow<List<Conversation>> = _searchResults.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch(Dispatchers.IO) {
            val results = if (query.isBlank()) {
                _conversations.value
            } else {
                _conversations.value.filter { conv ->
                    if (conv.title.contains(query, ignoreCase = true)) return@filter true
                    loadMessages(conv.id).any { msg ->
                        msg.content.contains(query, ignoreCase = true) ||
                        msg.attachmentName?.contains(query, ignoreCase = true) == true
                    }
                }
            }
            _searchResults.value = results
        }
    }

    // ===== 内置基础模型（随应用发布，不可删除）=====
    private val builtInLanguageModels = listOf(
        ModelInfo("mimo-v2.5-pro-ultraspeed", "MiMo-V2.5-Pro-UltraSpeed", Provider.XIAOMI, "Xiaomi高速推理模型，作者自用API，不保证随时在线，可适当白嫖。", supportsWebSearch = true, supportsThinking = false, modelType = ModelType.LANGUAGE, isBuiltIn = true),
        ModelInfo("mimo-v2.5-pro", "MiMo-V2.5-Pro", Provider.XIAOMI, "Xiaomi深度推理模型，作者自用API，不保证随时在线，可适当白嫖。", supportsWebSearch = true, modelType = ModelType.LANGUAGE, isBuiltIn = true)
    )
    private val builtInVisualModels = listOf(
        ModelInfo("ep-20260629143810-ffvjl", "Doubao-Seedream-5.0-Lite", Provider.DOUBAO, "Volcano Engine轻量级生图模型，作者自用API，不保证随时在线，可适当白嫖。", modelType = ModelType.VISUAL, isBuiltIn = true)
    )
    private val builtInTtsModels = listOf(
        ModelInfo("MiMo-V2.5-TTS", "MiMo-V2.5-TTS", Provider.XIAOMI, "小米语音合成模型，作者自用API，不保证随时在线，可适当白嫖。", modelType = ModelType.TTS, isBuiltIn = true)
    )

    // 各类型模型列表（内置 + 用户自定义），UI 读这些 StateFlow
    val languageModels: StateFlow<List<ModelInfo>> = _customModels.map { customs ->
        builtInLanguageModels + customs.filter { it.modelType == ModelType.LANGUAGE }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, builtInLanguageModels)

    val visualModels: StateFlow<List<ModelInfo>> = _customModels.map { customs ->
        builtInVisualModels + customs.filter { it.modelType == ModelType.VISUAL }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, builtInVisualModels)

    val visionModels: StateFlow<List<ModelInfo>> = _customModels.map { customs ->
        customs.filter { it.modelType == ModelType.VISION }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ttsModels: StateFlow<List<ModelInfo>> = _customModels.map { customs ->
        builtInTtsModels + customs.filter { it.modelType == ModelType.TTS }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, builtInTtsModels)

    val asrModels: StateFlow<List<ModelInfo>> = _customModels.map { customs ->
        customs.filter { it.modelType == ModelType.ASR }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // TTS 音色列表（MiMo 预设音色）
    val ttsVoices = listOf(
        "mimo_default", "冰糖", "茉莉", "苏打", "白桦", "Mia", "Chloe", "Milo", "Dean"
    )

    /** 某类型的完整模型列表（内置 + 用户自定义），供路由/选择解析用 */
    private fun modelsOfType(type: ModelType): List<ModelInfo> =
        (when (type) {
            ModelType.LANGUAGE -> builtInLanguageModels
            ModelType.VISUAL -> builtInVisualModels
            ModelType.TTS -> builtInTtsModels
            else -> emptyList()
        }) + _customModels.value.filter { it.modelType == type }

    // 兼容旧引用
    @Deprecated("Use languageModels", ReplaceWith("languageModels"))
    val availableModels get() = languageModels.value

    private val conversationsFile: File
        get() = File(getApplication<Application>().filesDir, CONVERSATIONS_FILE)

    private val perConvFile: File
        get() = File(getApplication<Application>().filesDir, PER_CONV_FILE)

    // ===== 每对话「新规则」覆盖设置：convId → 覆盖项（null=跟随全局默认） =====
    private val _perConvSettings = MutableStateFlow<Map<String, PerConvSettings>>(emptyMap())
    val perConvSettings: StateFlow<Map<String, PerConvSettings>> = _perConvSettings.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.customModels.collect { models -> _customModels.value = models }
        }
        viewModelScope.launch {
            combine(settingsRepo.selectedModelId, _customModels) { savedId, _ -> savedId }
                .collect { savedId ->
                    if (savedId.isNotEmpty()) {
                        modelsOfType(ModelType.LANGUAGE).find { it.id == savedId }?.let { _selectedModel.value = it }
                    }
                }
        }
        viewModelScope.launch {
            combine(settingsRepo.selectedVisualModelId, _customModels) { savedId, _ -> savedId }
                .collect { savedId ->
                    if (savedId.isNotEmpty()) {
                        modelsOfType(ModelType.VISUAL).find { it.id == savedId }?.let { _selectedVisualModel.value = it }
                    }
                }
        }
        viewModelScope.launch {
            combine(settingsRepo.selectedVisionModelId, _customModels) { savedId, _ -> savedId }
                .collect { savedId ->
                    if (savedId.isNotEmpty()) {
                        modelsOfType(ModelType.VISION).find { it.id == savedId }?.let { _selectedVisionModel.value = it }
                    }
                }
        }
        viewModelScope.launch {
            settingsRepo.asrModel.collect { id -> _asrModel.value = id }
        }
        viewModelScope.launch {
            settingsRepo.hasAgreedTerms.collect { agreed -> _hasAgreedTerms.value = agreed }
        }
        viewModelScope.launch {
            settingsRepo.themeModeOrdinal.collect { ordinal ->
                _themeMode.value = ThemeMode.entries.getOrElse(ordinal) { ThemeMode.SYSTEM }
            }
        }
        viewModelScope.launch {
            settingsRepo.colorThemeOrdinal.collect { ordinal ->
                _colorTheme.value = ColorTheme.entries.getOrElse(ordinal) { ColorTheme.BROWN }
            }
        }
        viewModelScope.launch {
            settingsRepo.tempModeOrdinal.collect { ordinal ->
                _tempMode.value = TempMode.entries.getOrElse(ordinal) { TempMode.AUTO }
            }
        }
        viewModelScope.launch {
            settingsRepo.lengthModeOrdinal.collect { ordinal ->
                _lengthMode.value = LengthMode.entries.getOrElse(ordinal) { LengthMode.AUTO }
            }
        }
        viewModelScope.launch {
            settingsRepo.enableWebSearch.collect { enabled -> _enableWebSearch.value = enabled }
        }
        viewModelScope.launch {
            settingsRepo.showThinking.collect { show -> _showThinking.value = show }
        }
        viewModelScope.launch {
            settingsRepo.languageCode.collect { code ->
                _appLanguage.value = AppLanguage.fromCode(code)
            }
        }
        viewModelScope.launch {
            settingsRepo.fontSizeOrdinal.collect { ordinal ->
                _fontSize.value = FontSize.entries.getOrElse(ordinal) { FontSize.MEDIUM }
            }
        }
        viewModelScope.launch {
            settingsRepo.voiceModel.collect { model -> _voiceModel.value = model }
        }
        viewModelScope.launch {
            settingsRepo.ttsVoice.collect { voice -> _ttsVoice.value = voice }
        }
        viewModelScope.launch {
            settingsRepo.ttsSpeed.collect { speed -> _ttsSpeed.value = speed }
        }
        viewModelScope.launch {
            settingsRepo.ttsPitch.collect { pitch -> _ttsPitch.value = pitch }
        }
        viewModelScope.launch {
            settingsRepo.ttsAutoPlay.collect { auto -> _ttsAutoPlay.value = auto }
        }
        viewModelScope.launch {
            settingsRepo.autoSummarizeMemory.collect { enabled -> _autoSummarizeMemory.value = enabled }
        }
        viewModelScope.launch {
            settingsRepo.useSystemFont.collect { enabled -> _useSystemFont.value = enabled }
        }
        viewModelScope.launch {
            settingsRepo.globalMemories.collect { list -> _globalMemories.value = list }
        }
        viewModelScope.launch {
            settingsRepo.advancedMaterial.collect { enabled -> _advancedMaterial.value = enabled }
        }
        viewModelScope.launch {
            settingsRepo.systemDarkTheme.collect { isBlack -> _systemDarkTheme.value = isBlack }
            settingsRepo.chatModeOrdinal.collect { o -> _chatMode.value = ChatMode.entries.getOrElse(o) { ChatMode.STANDARD } }
        }
        viewModelScope.launch {
            settingsRepo.pinnedConversationIds.collect { ids ->
                pinnedIds.value = ids
                val convs = withContext(Dispatchers.IO) { loadConversations() }
                _conversations.value = sortConversations(convs)
            }
        }
        viewModelScope.launch {
            val convs = withContext(Dispatchers.IO) { loadConversations() }
            _conversations.value = sortConversations(convs)
        }
        viewModelScope.launch {
            _perConvSettings.value = withContext(Dispatchers.IO) { loadPerConvSettings() }
        }
    }

    private fun sortConversations(list: List<Conversation>): List<Conversation> {
        val pinned = pinnedIds.value
        return list.sortedWith(
            compareByDescending<Conversation> { pinned.contains(it.id) || it.mode == ChatMode.COMPANION }
                .thenByDescending { it.updatedAt }
        )
    }

    // ========== 每对话设置（新规则）==========
    private fun loadPerConvSettings(): Map<String, PerConvSettings> = try {
        if (perConvFile.exists()) {
            val type = object : TypeToken<Map<String, PerConvSettings>>() {}.type
            gson.fromJson(perConvFile.readText(), type) ?: emptyMap()
        } else emptyMap()
    } catch (_: Exception) { emptyMap() }

    private fun savePerConvSettings() {
        try { perConvFile.writeText(gson.toJson(_perConvSettings.value)) }
        catch (e: Exception) { Log.e("FreeChat", "Save perconv failed", e) }
    }

    fun getPerConvSettings(convId: String): PerConvSettings =
        _perConvSettings.value[convId] ?: PerConvSettings()

    fun updatePerConvSettings(convId: String, settings: PerConvSettings) {
        _perConvSettings.value = _perConvSettings.value.toMutableMap().apply { put(convId, settings) }
        savePerConvSettings()
    }

    // 生成时临时应用每对话覆盖（结束后恢复全局默认）——单生成协程模型下安全
    private data class SettingsSnapshot(
        val model: ModelInfo, val visual: ModelInfo, val vision: ModelInfo?,
        val search: Boolean, val showThinking: Boolean, val temp: TempMode,
        val length: LengthMode, val autoMem: Boolean
    )

    private fun applyPerConvSettings(convId: String?): SettingsSnapshot {
        val snap = SettingsSnapshot(
            _selectedModel.value, _selectedVisualModel.value, _selectedVisionModel.value,
            _enableWebSearch.value, _showThinking.value, _tempMode.value,
            _lengthMode.value, _autoSummarizeMemory.value
        )
        val over = convId?.let { _perConvSettings.value[it] } ?: return snap
        over.languageModelId?.let { id -> modelsOfType(ModelType.LANGUAGE).find { it.id == id }?.let { _selectedModel.value = it } }
        over.visualModelId?.let { id -> modelsOfType(ModelType.VISUAL).find { it.id == id }?.let { _selectedVisualModel.value = it } }
        over.visionModelId?.let { id -> modelsOfType(ModelType.VISION).find { it.id == id }?.let { _selectedVisionModel.value = it } }
        over.enableWebSearch?.let { _enableWebSearch.value = it }
        over.showThinking?.let { _showThinking.value = it }
        over.autoSummarizeMemory?.let { _autoSummarizeMemory.value = it }
        over.tempModeOrdinal?.let { _tempMode.value = TempMode.entries.getOrElse(it) { TempMode.AUTO } }
        over.lengthModeOrdinal?.let { _lengthMode.value = LengthMode.entries.getOrElse(it) { LengthMode.AUTO } }
        return snap
    }

    private fun restoreSettings(snap: SettingsSnapshot) {
        _selectedModel.value = snap.model
        _selectedVisualModel.value = snap.visual
        _selectedVisionModel.value = snap.vision
        _enableWebSearch.value = snap.search
        _showThinking.value = snap.showThinking
        _tempMode.value = snap.temp
        _lengthMode.value = snap.length
        _autoSummarizeMemory.value = snap.autoMem
    }

    // ========== 问候语 ==========
    fun generateGreeting(s: com.freechat.i18n.AppStrings = com.freechat.i18n.ZhCN): String {
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val weekend = dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY

        val greetings = when {
            hour in 0..4 -> s.greetingsLateNight
            hour in 5..7 -> s.greetingsEarlyMorning
            hour in 8..11 -> {
                if (weekend) s.greetingsMorningWeekend
                else s.greetingsMorningWeekday
            }
            hour in 12..13 -> s.greetingsNoon
            hour in 14..17 -> s.greetingsAfternoon
            hour in 18..21 -> s.greetingsEvening
            else -> s.greetingsNight
        }
        val greeting = greetings.random()
        return greeting
    }

    // ========== 设置 ==========
    fun selectLanguageModel(model: ModelInfo) {
        _selectedModel.value = model
        viewModelScope.launch { settingsRepo.saveSelectedModel(model.id) }
    }
    fun selectVisualModel(model: ModelInfo) {
        _selectedVisualModel.value = model
        viewModelScope.launch { settingsRepo.saveSelectedVisualModel(model.id) }
    }
    fun selectVisionModel(model: ModelInfo) {
        _selectedVisionModel.value = model
        viewModelScope.launch { settingsRepo.saveSelectedVisionModel(model.id) }
    }
    fun selectTtsModel(model: ModelInfo) {
        _voiceModel.value = model.id
        viewModelScope.launch { settingsRepo.saveVoiceModel(model.id) }
    }
    fun selectAsrModel(model: ModelInfo) {
        _asrModel.value = model.id
        viewModelScope.launch { settingsRepo.saveAsrModel(model.id) }
    }

    // ========== 自定义模型增删改（仅全局设置里可添加）==========
    fun addCustomModel(model: ModelInfo) {
        _customModels.value = _customModels.value.filterNot { it.id == model.id && it.modelType == model.modelType } + model
        persistCustomModels()
    }
    fun updateCustomModel(oldId: String, modelType: ModelType, newModel: ModelInfo) {
        _customModels.value = _customModels.value.map { if (it.id == oldId && it.modelType == modelType) newModel else it }
        persistCustomModels()
    }
    fun deleteCustomModel(modelId: String, modelType: ModelType) {
        _customModels.value = _customModels.value.filterNot { it.id == modelId && it.modelType == modelType }
        persistCustomModels()
        // 删除的是当前选中的模型则回退
        when (modelType) {
            ModelType.LANGUAGE -> if (_selectedModel.value.id == modelId) _selectedModel.value = builtInLanguageModels.first()
            ModelType.VISUAL -> if (_selectedVisualModel.value.id == modelId) _selectedVisualModel.value = builtInVisualModels.first()
            ModelType.VISION -> if (_selectedVisionModel.value?.id == modelId) _selectedVisionModel.value = null
            ModelType.TTS -> if (_voiceModel.value == modelId) _voiceModel.value = builtInTtsModels.first().id
            ModelType.ASR -> if (_asrModel.value == modelId) _asrModel.value = ""
        }
    }
    private fun persistCustomModels() {
        viewModelScope.launch { settingsRepo.saveCustomModels(_customModels.value) }
    }
    // 兼容旧调用
    @Deprecated("Use selectLanguageModel", ReplaceWith("selectLanguageModel(model)"))
    fun selectModel(model: ModelInfo) = selectLanguageModel(model)
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch { settingsRepo.saveThemeMode(mode.ordinal) }
    }

    fun setColorTheme(ct: ColorTheme) {
        _colorTheme.value = ct
        viewModelScope.launch { settingsRepo.saveColorTheme(ct.ordinal) }
    }
    fun setTempMode(mode: TempMode) {
        _tempMode.value = mode
        viewModelScope.launch { settingsRepo.saveTempMode(mode.ordinal) }
    }
    fun setLengthMode(mode: LengthMode) {
        _lengthMode.value = mode
        viewModelScope.launch { settingsRepo.saveLengthMode(mode.ordinal) }
    }
    fun setEnableWebSearch(enabled: Boolean) {
        _enableWebSearch.value = enabled
        viewModelScope.launch { settingsRepo.saveEnableWebSearch(enabled) }
    }
    fun setShowThinking(enabled: Boolean) {
        _showThinking.value = enabled
        viewModelScope.launch { settingsRepo.saveShowThinking(enabled) }
    }
    fun setUseSystemFont(enabled: Boolean) {
        _useSystemFont.value = enabled
        viewModelScope.launch { settingsRepo.saveUseSystemFont(enabled) }
    }
    fun addGlobalMemory(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        _globalMemories.value = _globalMemories.value + t
        viewModelScope.launch { settingsRepo.saveGlobalMemories(_globalMemories.value) }
    }
    fun deleteGlobalMemory(text: String) {
        _globalMemories.value = _globalMemories.value.filter { it != text }
        viewModelScope.launch { settingsRepo.saveGlobalMemories(_globalMemories.value) }
    }
    fun setLanguage(language: AppLanguage) {
        _appLanguage.value = language
        viewModelScope.launch { settingsRepo.saveLanguageCode(language.code) }
    }
    fun setFontSize(fontSize: FontSize) {
        _fontSize.value = fontSize
        viewModelScope.launch { settingsRepo.saveFontSize(fontSize.ordinal) }
    }
    fun setVoiceModel(model: String) {
        _voiceModel.value = model
        viewModelScope.launch { settingsRepo.saveVoiceModel(model) }
    }
    fun setTtsVoice(voice: String) {
        _ttsVoice.value = voice
        viewModelScope.launch { settingsRepo.saveTtsVoice(voice) }
    }
    fun setTtsSpeed(speed: Float) {
        _ttsSpeed.value = speed
        viewModelScope.launch { settingsRepo.saveTtsSpeed(speed) }
    }
    fun setTtsPitch(pitch: Float) {
        _ttsPitch.value = pitch
        viewModelScope.launch { settingsRepo.saveTtsPitch(pitch) }
    }
    fun setTtsAutoPlay(auto: Boolean) {
        _ttsAutoPlay.value = auto
        viewModelScope.launch { settingsRepo.saveTtsAutoPlay(auto) }
    }
    fun setAutoSummarizeMemory(enabled: Boolean) {
        _autoSummarizeMemory.value = enabled
        viewModelScope.launch { settingsRepo.saveAutoSummarizeMemory(enabled) }
    }
    fun setAdvancedMaterial(enabled: Boolean) {
        _advancedMaterial.value = enabled
        viewModelScope.launch { settingsRepo.saveAdvancedMaterial(enabled) }
    }
    fun setSystemDarkTheme(isBlack: Boolean) {
        _systemDarkTheme.value = isBlack
        viewModelScope.launch { settingsRepo.saveSystemDarkTheme(isBlack) }
    }

    fun setChatMode(mode: ChatMode) {
        _chatMode.value = mode
        viewModelScope.launch { settingsRepo.saveChatMode(mode.ordinal) }
    }

    fun agreeTerms() {
        _hasAgreedTerms.value = true
        viewModelScope.launch { settingsRepo.saveHasAgreedTerms(true) }
    }

    // 设置页主列表滚动位置（跨页返回保持，如进更新日志/AI语音页返回不回顶部）
    private val _settingsScrollPosition = MutableStateFlow(0)
    val settingsScrollPosition: StateFlow<Int> = _settingsScrollPosition.asStateFlow()
    fun saveSettingsScrollPosition(pos: Int) {
        _settingsScrollPosition.value = pos
    }

    // 聊天页滚动位置（按对话 id 记录，跨页面切换返回后恢复到原位置，杀后台即清空）
    private val _chatScrollPositions = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val chatScrollPositions: StateFlow<Map<String, Pair<Int, Int>>> = _chatScrollPositions.asStateFlow()
    fun saveChatScrollPosition(convId: String, index: Int, offset: Int) {
        _chatScrollPositions.value = _chatScrollPositions.value + (convId to (index to offset))
    }

    // 引用消息：按对话隔离（每 convId 一份），切换对话互不干扰、切回保留（像草稿一样绑定对话缓存）
    private val _quotedMessages = MutableStateFlow<Map<String, Message>>(emptyMap())
    val quotedMessage: StateFlow<Message?> = combine(_quotedMessages, _currentConversationId) { map, convId ->
        convId?.let { map[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun quoteMessage(msg: Message) {
        val convId = _currentConversationId.value ?: return
        _quotedMessages.value = _quotedMessages.value + (convId to msg)
    }
    fun clearQuote() {
        val convId = _currentConversationId.value ?: return
        _quotedMessages.value = _quotedMessages.value - convId
    }

    // 本次发送的引用上下文（仅后台发给 AI，不在前台消息框显示）
    private var pendingQuoteText: String? = null
    /** 朗读某条 AI 消息（合成 + 播放；内置 MiMo / 用户自定义 OpenAI TTS） */
    fun speakMessage(messageId: String, text: String) {
        viewModelScope.launch {
            val tts = currentTtsModel()
            if (tts == null || tts.isBuiltIn) {
                TtsController.speak(messageId, text, _ttsVoice.value, _ttsSpeed.value, _ttsPitch.value)
            } else {
                val audio = synthSpeechOpenAi(tts, text)
                if (audio != null) {
                    TtsController.speakSingle(messageId, audio, _ttsSpeed.value, _ttsPitch.value)
                }
            }
        }
    }

    private fun currentTtsModel(): ModelInfo? = modelsOfType(ModelType.TTS).find { it.id == _voiceModel.value }
    private fun currentAsrModel(): ModelInfo? = modelsOfType(ModelType.ASR).find { it.id == _asrModel.value }

    /** 按住说话语音识别（用当前 ASR 模型；无模型返回 null） */
    suspend fun recognizeVoiceInput(wav: ByteArray): String? {
        val asr = currentAsrModel() ?: return null
        return transcribeAudioOpenAi(asr, wav)
    }

    /** 自定义模型语音合成（OpenAI /v1/audio/speech） */
    private suspend fun synthSpeechOpenAi(model: ModelInfo, text: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val (url, key) = customEndpoint(model, "/v1/audio/speech")
            val body = gson.toJson(mapOf(
                "model" to model.id, "input" to text, "voice" to "alloy", "response_format" to "mp3"
            )).toRequestBody(JSON_MEDIA)
            val resp = client.newCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json").post(body).build()).execute()
            if (!resp.isSuccessful) return@withContext null
            resp.body?.bytes()
        } catch (e: Exception) {
            Log.e("FreeChat", "synthSpeechOpenAi failed", e)
            null
        }
    }

    /** 自定义模型语音识别（OpenAI /v1/audio/transcriptions，multipart） */
    private suspend fun transcribeAudioOpenAi(model: ModelInfo, wav: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val (url, key) = customEndpoint(model, "/v1/audio/transcriptions")
            val fileBody = okhttp3.RequestBody.create("audio/wav".toMediaType(), wav)
            val multipart = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("model", model.id)
                .addFormDataPart("file", "speech.wav", fileBody)
                .addFormDataPart("language", "zh")
                .build()
            val resp = client.newCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $key")
                .post(multipart).build()).execute()
            if (!resp.isSuccessful) return@withContext null
            val json = JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
            json.get("text")?.asString?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e("FreeChat", "transcribeAudioOpenAi failed", e)
            null
        }
    }
    val selectedLanguage: StateFlow<AppLanguage> get() = _appLanguage

    // ========== 对话管理 ==========
    fun newConversation(mode: ChatMode = ChatMode.STANDARD, character: CharacterProfile? = null) {
        saveCurrentConversation()
        _messages.value = emptyList()
        _liveReasoning.value = ""
        _liveContent.value = ""
        _thinkingTimeMs.value = 0L
        _currentConversationId.value = null
        _currentMode.value = mode
        _currentCharacter.value = character
        syncDisplayGenerationState(null)
    }

    /** 创建拟人角色后立即建立对话（0 消息也落侧滑栏），并可选地以「对方正在输入」延迟发出开场白 */
    fun startCompanionConversation(character: CharacterProfile) {
        saveCurrentConversation()
        _messages.value = emptyList()
        _liveReasoning.value = ""
        _liveContent.value = ""
        _thinkingTimeMs.value = 0L
        _currentMode.value = ChatMode.COMPANION
        _currentCharacter.value = character
        val convId = UUID.randomUUID().toString()
        _currentConversationId.value = convId
        val now = System.currentTimeMillis()
        val conv = Conversation(
            id = convId,
            title = character.name.ifBlank { "新对话" },
            createdAt = now,
            updatedAt = now,
            messageCount = 0,
            isPinned = false,
            mode = ChatMode.COMPANION,
            characterProfile = character
        )
        _conversations.value = sortConversations(_conversations.value + conv)
        saveConversations()
        // 开场白：逐条带延迟发出，让 AI「知道」自己开场说了什么
        val openings = character.normalized().openingLines.map { it.trim() }.filter { it.isNotEmpty() }
        if (openings.isNotEmpty()) {
            viewModelScope.launch {
                delay(kotlin.random.Random.nextLong(1200L, 2200L))  // 先顿一下，像真人在组织开场
                if (_messages.value.isNotEmpty()) return@launch  // 用户已抢先发消息，跳过开场白
                for (opening in openings) {
                    _isTyping.value = true
                    delay(800L)
                    _isTyping.value = false
                    _messages.value = _messages.value + Message(
                        role = Role.ASSISTANT, content = opening,
                        modelName = _selectedModel.value.displayName, mode = ChatMode.COMPANION
                    )
                    saveCurrentConversation()
                }
            }
        }
    }

    /** 编辑当前对话的拟人角色档案（只更新角色，不新建对话） */
    fun updateCurrentCharacter(profile: CharacterProfile) {
        _currentCharacter.value = profile
        val convId = _currentConversationId.value ?: return
        _conversations.value = _conversations.value.map {
            if (it.id == convId) it.copy(characterProfile = profile, title = profile.name.ifBlank { it.title })
            else it
        }
        saveConversations()
    }

    fun switchToConversation(conversation: Conversation) {
        saveCurrentConversation()
        _currentConversationId.value = conversation.id
        _currentMode.value = conversation.mode
        _currentCharacter.value = conversation.characterProfile
        _messages.value = loadMessages(conversation.id)
        syncDisplayGenerationState(conversation.id)
    }

    fun deleteConversation(conversation: Conversation) {
        // 删除对话时取消其在途生成（若有），避免后台 job 再写回已删除的对话
        companionPipelines.remove(conversation.id)?.job?.cancel()
        convLoading.remove(conversation.id)
        convTyping.remove(conversation.id)
        if (_currentConversationId.value == conversation.id) streamJob?.cancel()
        val msgFile = messagesFile(conversation.id)
        if (msgFile.exists()) {
            // 清理该会话引用过的图片文件（避免删除对话后图片残留占用空间）
            loadMessages(conversation.id).forEach { m ->
                m.imagePaths.forEach { runCatching { File(it).delete() } }
            }
            msgFile.delete()
        }
        memoryManager.delete(conversation.id)
        _conversations.value = _conversations.value.filter { it.id != conversation.id }
        saveConversations()
        if (_currentConversationId.value == conversation.id) {
            _currentConversationId.value = null
            _messages.value = emptyList()
            _isLoading.value = false
            _isTyping.value = false
        }
    }

    fun renameConversation(conversation: Conversation, newTitle: String) {
        _conversations.value = sortConversations(
            _conversations.value.map {
                if (it.id == conversation.id) it.copy(title = newTitle)
                else it
            }
        )
        saveConversations()
    }

    fun togglePinConversation(conversation: Conversation) {
        val newPinned = pinnedIds.value.toMutableSet()
        if (newPinned.contains(conversation.id)) newPinned.remove(conversation.id)
        else newPinned.add(conversation.id)
        pinnedIds.value = newPinned
        _conversations.value = sortConversations(
            _conversations.value.map {
                if (it.id == conversation.id) it.copy(isPinned = newPinned.contains(it.id))
                else it
            }
        )
        saveConversations()
        viewModelScope.launch { settingsRepo.savePinnedIds(newPinned) }
    }

    // ========== Skills 调度系统 ==========
    // 能力枚举：每个 Skill 绑定一类任务，detectSkill 负责路由到对应模型/执行器
    private enum class Skill {
        IMAGE_GEN,   // 文本生图 → Doubao Seedream
        IMAGE_EDIT,  // 修图/图生图 → Doubao Seedream img2img
        VISION,      // 识图/带图对话 → 识图模型（Doubao Seed / MiMo）
        CALENDAR,    // 日历行程 → CalendarContract
        DOCUMENT,    // 生成/编辑原生文档（docx/xlsx/pptx）
        FILE,        // 理解上传的文件内容并回复
        TEXT         // 纯文本 → DeepSeek/MiMo (+SerpAPI)
    }

    /** Skills 核心：根据用户意图 + 是否带图/带文件，路由到对应能力 */
    private fun detectSkill(text: String, hasImage: Boolean, hasFile: Boolean): Skill {
        // 1. 带文件：区分「生成/编辑文档」与「理解文件内容」
        if (hasFile) {
            return if (isDocumentRequest(text)) Skill.DOCUMENT else Skill.FILE
        }
        // 2. 日历（无需附件，意图明确）
        if (isCalendarRequest(text)) return Skill.CALENDAR
        // 3. 生成/编辑文档（纯文本指令，如「做个PPT」）
        if (isDocumentRequest(text)) return Skill.DOCUMENT
        // 4. 图片相关
        if (hasImage) {
            val analysis = isImageAnalysisRequest(text) || text.isBlank()
            val edit = !analysis && isImageEditRequest(text)
            val gen = !analysis && !edit && isImageGenRequest(text)
            return when {
                edit -> Skill.IMAGE_EDIT
                gen -> Skill.IMAGE_GEN
                else -> Skill.VISION
            }
        }
        return if (isImageGenRequest(text)) Skill.IMAGE_GEN else Skill.TEXT
    }

    // ========== Function Calling：AI 自主判断意图、调用工具 ==========
    /** 模型返回的一次工具调用 */
    private data class ToolCall(val name: String, val arguments: JsonObject)
    /** 语言模型一次流式调用的结果（内容 + 思考 + 待执行的工具调用） */
    private data class LanguageResult(val content: String, val reasoning: String, val toolCalls: List<ToolCall>)
    /** 流式累积单个 tool_call（index 区分并行返回的多个） */
    private class ToolCallAcc(val index: Int) {
        var id = ""
        var name = ""
        val args = StringBuilder()
    }

    /** 从路径推断 mime（PNG/GIF/WebP 识别，其余按 JPEG） */
    private fun detectMime(path: String): String = when (File(path).extension.lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    /** 构建 tools 参数：把系统能力暴露给语言模型，让模型自主决定调用哪个工具 */
    private fun buildTools(hasImage: Boolean, hasFile: Boolean): List<Map<String, Any?>> {
        val tools = mutableListOf<Map<String, Any?>>()

        tools.add(mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "generate_image",
                "description" to "生成一张图片。当用户明确要求画图、生成图片/海报/插画/配图时调用。",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "prompt" to mapOf("type" to "string", "description" to "要生成的图片画面描述")
                    ),
                    "required" to listOf("prompt")
                )
            )
        ))
        tools.add(mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "read_calendar",
                "description" to "读取用户系统日历的未来行程。当用户询问日程、行程、安排、会议等时调用。",
                "parameters" to mapOf("type" to "object", "properties" to emptyMap<String, Any?>())
            )
        ))
        tools.add(mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "generate_document",
                "description" to "生成一份原生文档（Word/Excel/PPT）。当用户要求写文档、做报告、做表格、做PPT时调用。",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "type" to mapOf("type" to "string", "enum" to listOf("docx", "xlsx", "pptx")),
                        "prompt" to mapOf("type" to "string", "description" to "文档内容要求")
                    ),
                    "required" to listOf("type")
                )
            )
        ))

        if (hasImage) {
            tools.add(mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "analyze_image",
                    "description" to "分析/描述用户上传的图片内容。当用户上传图片并要求描述、识别、看图时调用。",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "question" to mapOf("type" to "string", "description" to "关于图片的问题，可省略")
                        )
                    )
                )
            ))
            tools.add(mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "edit_image",
                    "description" to "修改/美化用户上传的图片（修图、换背景、风格化等）。",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "prompt" to mapOf("type" to "string", "description" to "修图要求")
                        ),
                        "required" to listOf("prompt")
                    )
                )
            ))
        }
        if (hasFile) {
            tools.add(mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "understand_file",
                    "description" to "读取并理解用户上传的文件内容，回答相关问题。",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "question" to mapOf("type" to "string", "description" to "关于文件的问题，可省略")
                        )
                    )
                )
            ))
        }
        return tools
    }

    // ========== 日历/文档 意图检测 ==========
    private fun isCalendarRequest(text: String): Boolean {
        val kw = listOf("日程", "行程", "日历", "安排", "会议", "待办", "提醒", "预约",
            "schedule", "calendar", "meeting", "appointment", "event")
        return kw.any { text.contains(it, ignoreCase = true) }
    }
    /** 用户是否在要求「生成/编辑一份文档」（Word/Excel/PPT） */
    private fun isDocumentRequest(text: String): Boolean {
        val t = text.lowercase()
        // 明确提到文件类型
        val explicitType = listOf(
            "ppt", "pptx", "幻灯片", "演示文稿", "slides",
            "excel", "xlsx", "表格", "报表", "数据表",
            "word", "docx", "doc", "文档"
        ).any { t.contains(it) }
        if (explicitType) return true
        // 生成/制作类动词 + 文档类名词
        val action = listOf("做", "生成", "写", "制作", "创建", "整理", "帮我做", "帮我写").any { t.contains(it) }
        val noun = listOf("报告", "方案", "总结", "简历", "策划案", "计划书", "通知", "邀请函", "讲稿", "演讲稿").any { t.contains(it) }
        return action && noun
    }

    // ========== 图片意图检测 ==========
    private fun isImageGenRequest(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        // 1. 精确关键词（原有列表）
        if (IMAGE_GEN_KEYWORDS.any { t.contains(it, ignoreCase = true) }) return true
        // 2. 前缀动词补漏：画/生成/创建/制作 开头（覆盖「画一只猫」「生成海报」等漏判）
        if (Regex("""^(帮我|给我|请|麻烦|来|快|能|能不能|可以|我想|我要|想|求|帮忙)?\s*(画|生成|创建|制作)""").containsMatchIn(t)) return true
        return false
    }
    /** 用户意图是修图/p图/图生图（需要参考原图生成新图） */
    private fun isImageEditRequest(text: String): Boolean {
        return IMAGE_EDIT_KEYWORDS.any { text.contains(it, ignoreCase = true) }
    }
    /** 用户意图是识图/分析图片内容 */
    private fun isImageAnalysisRequest(text: String): Boolean {
        // 有图片但文字很短/没有 → 默认识图
        if (text.isBlank()) return true
        return IMAGE_ANALYSIS_KEYWORDS.any { text.contains(it, ignoreCase = true) }
    }
    /** DeepSeek 是否说出了「无法生图」类否定（用于兜底替换） */
    private fun isImageGenRefusal(text: String): Boolean {
        val refusal = listOf(
            "无法生成", "无法画", "不能生成", "不能画", "无法创建", "不会画", "不能创建",
            "无法绘制", "不能绘制", "无法提供图片", "我是文本", "我是文字", "纯文本",
            "文字模型", "无法生成图片", "不能生成图片", "不会生成"
        )
        return refusal.any { text.contains(it) }
    }
    /** 判断是否需要联网搜索 — 激进策略：除纯闲聊/情绪外全部触发，保证时事资讯覆盖 */
    private fun needsWebSearch(text: String): Boolean {
        val t = text.trim()

        // 极短消息不搜（2 字符以下，或 ≤4 字符且无任何信息意图）
        if (t.length <= 2) return false
        if (t.length <= 4 && !t.contains("?") && !t.contains("？") && !t.contains("吗")
            && !t.contains("没") && !t.contains("谁") && !t.contains("哪")
            && !t.contains("搜") && !t.contains("查") && !t.contains("怎")) return false

        // 明确纯闲聊 — 不触发搜索
        val pureChitchat = setOf(
            "你好", "嗨", "嘿", "哈喽", "hey", "hi", "hello", "在吗", "在不在",
            "谢谢", "多谢", "thanks", "thank you", "thx",
            "再见", "拜拜", "bye", "晚安", "回头聊",
            "好的", "ok", "OK", "嗯", "哦", "喔", "行", "好", "对", "是的",
            "哈哈", "呵呵", "嘻嘻", "嘿嘿", "hehe", "haha", "lol",
            "早上好", "下午好", "晚上好", "早安", "good morning", "good night",
            "你是谁", "你叫什么", "你的版本", "介绍一下自己", "你是什么模型",
            "?", "？"
        )
        if (pureChitchat.any { t.equals(it, ignoreCase = true) }) return false

        // 短纯情绪/感叹（≤10 字，无信息意图）
        if (t.length <= 10) {
            val pureEmotion = setOf(
                "好开心", "好难过", "好累", "好困", "好饿", "好烦", "好无聊",
                "太开心", "太难了", "太棒了", "太累了", "太困了",
                "真开心", "真好看", "真好吃", "真好听",
                "累死了", "困死了", "饿死了", "烦死了",
                "开心", "难过", "无聊", "有意思", "好玩",
                "好舒服", "好紧张", "好激动", "吓死我了",
                "无语", "离谱", "绝了", "牛逼", "厉害",
                "好美", "好漂亮"
            )
            if (pureEmotion.any { t == it }) return false
        }

        // 其余全部触发联网搜索
        return true
    }

    // ========== 消息（流式） ==========
    fun sendMessage(text: String) {
        val trimmedText = text.trim()
        // 引用：捕获引用内容（后台发给 AI），不在前台消息框里显示额外文字
        val quoteConvId = _currentConversationId.value
        val quote = quoteConvId?.let { _quotedMessages.value[it] }
        pendingQuoteText = quote?.let { q ->
            val qt = if (q.imagePaths.isNotEmpty()) "[图片]" else q.content.trim().take(200)
            "（用户引用了之前的一条消息：$qt。请结合这条引用与用户刚说的话之间的关联——可能是因果、条件、追问、修改等——针对性思考回复。）"
        }
        val quotedText = quote?.content?.trim()?.take(200)?.ifBlank { null }
        val quotedImagePath = quote?.imagePaths?.firstOrNull()
        if (quoteConvId != null) _quotedMessages.value = _quotedMessages.value - quoteConvId  // 引用随本次发送消费
        val hasImage = _pendingImages.value.isNotEmpty()
        val hasFile = _pendingFiles.value.isNotEmpty()
        if (trimmedText.isBlank() && !hasImage && !hasFile) return
        if (_isAddingImages.value) return  // 图片还在处理中，稍后再发
        if (_isAddingFiles.value) return   // 文件还在拷贝中，稍后再发

        // 拟人陪伴模式：统一走缓冲管线（AI 思考中也能继续发消息，连发合并理解）
        if (_currentMode.value == ChatMode.COMPANION) {
            val imageSnapshot = _pendingImages.value.map { it.path }
            if (hasImage) _pendingImages.value = emptyList()
            sendCompanionMessage(trimmedText, imageSnapshot, quotedText, quotedImagePath)
            return
        }

        if (_isLoading.value) return

        if (_currentConversationId.value == null && _messages.value.isEmpty()) {
            _currentConversationId.value = java.util.UUID.randomUUID().toString()
        }
        val convId = _currentConversationId.value ?: java.util.UUID.randomUUID().toString()
        _currentConversationId.value = convId

        // 图片/文件消息在前、文本消息在后（各自独立一条，真实显示）
        val pendingSnapshot = _pendingImages.value
        if (hasImage) _pendingImages.value = emptyList()  // 发送即清空候选区（文件已被消息引用，不删）
        val fileSnapshot = _pendingFiles.value
        if (hasFile) _pendingFiles.value = emptyList()

        val newUserMessages = mutableListOf<Message>()
        if (hasImage) {
            newUserMessages.add(Message(role = Role.USER, content = "", imagePaths = pendingSnapshot.map { it.path }))
        }
        if (hasFile) {
            val f = fileSnapshot.first()
            newUserMessages.add(Message(
                role = Role.USER,
                content = trimmedText,
                attachmentPath = f.path,
                attachmentName = f.name
            ))
        }
        if (trimmedText.isNotBlank() && !hasFile) {
            newUserMessages.add(Message(role = Role.USER, content = trimmedText, quotedText = quotedText, quotedImagePath = quotedImagePath))
        }
        activeRoundStartIndex = _messages.value.size  // ★ 停止即删除本轮：记录用户消息起点
        _messages.value = _messages.value + newUserMessages
        touchCurrentConversation()
        // 记忆总结用的用户文本
        val summaryUserText = when {
            trimmedText.isNotBlank() -> trimmedText
            hasImage -> "[图片]"
            hasFile -> "[文件: ${fileSnapshot.firstOrNull()?.name ?: ""}]"
            else -> ""
        }

        setConvLoading(convId, true)
        _liveReasoning.value = ""
        _liveContent.value = ""
        _thinkingTimeMs.value = 0L

        streamJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val snapshot = applyPerConvSettings(convId)
            val langModelName = _selectedModel.value.displayName
            val visualModelName = _selectedVisualModel.value.displayName
            val timerJob = launch {
                while (_isLoading.value) {
                    _thinkingTimeMs.value = System.currentTimeMillis() - startTime
                    delay(80)
                }
            }

            val working = _messages.value.toMutableList()
            try {
                val assistantMessage = generateReply(
                    trimmedText, pendingSnapshot, fileSnapshot,
                    langModelName, visualModelName, startTime, convId, working
                )
                ensureActive()  // 若已停止，此处抛出取消，避免补一条回复
                working.add(assistantMessage)
                persistConversationMessages(convId, working)
                _liveReasoning.value = ""
                _liveContent.value = ""
                notifyReplyIfBackground("FreeChat", assistantMessage.content)
                summarizeAndRemember(convId, summaryUserText, assistantMessage)
            } catch (e: CancellationException) {
                throw e  // 用户停止：不显示错误，交给 finally 收尾
            } catch (e: Exception) {
                Log.e("FreeChat", "API failed", e)
                val errMsg = Message(
                    role = Role.ASSISTANT,
                    content = briefApiError(e),
                    modelName = langModelName
                )
                working.add(errMsg)
                persistConversationMessages(convId, working)
                _liveReasoning.value = ""
                _liveContent.value = ""
            } finally {
                restoreSettings(snapshot)
                setConvLoading(convId, false)
                _isGeneratingImage.value = false
                setConvTyping(convId, false)
                activeRoundStartIndex = -1
            }
        }
    }

    /** 拟人模式：用户消息进缓冲池（含图片），缓冲窗口内无新消息则合并统一理解回复；按对话隔离 */
    private fun sendCompanionMessage(trimmedText: String, imagePaths: List<String>, quotedText: String? = null, quotedImagePath: String? = null) {
        if (_currentConversationId.value == null && _messages.value.isEmpty()) {
            _currentConversationId.value = UUID.randomUUID().toString()
        }
        val convId = _currentConversationId.value ?: UUID.randomUUID().toString()
        _currentConversationId.value = convId

        // 记录「晚安/睡了」短期状态（按对话）：短时间内用户再发消息，AI 有时间概念（「不是说睡了吗」）
        if (detectGoodnight(trimmedText)) {
            userSaidGoodnightAt[convId] = System.currentTimeMillis()
        }

        val pipeline = companionPipelines.getOrPut(convId) { CompanionPipeline() }
        // 思考中被新消息打断：清掉已发出的半截 AI 回复，重新统一理解（含新消息）
        if (pipeline.replyStart >= 0) {
            val msgs = _messages.value.toMutableList()
            if (pipeline.replyStart < msgs.size) {
                msgs.subList(pipeline.replyStart, msgs.size).clear()
                _messages.value = msgs
            }
        }
        pipeline.replyStart = -1
        pipeline.jobId++
        setConvLoading(convId, true)
        if (imagePaths.isNotEmpty()) {
            _messages.value = _messages.value + Message(role = Role.USER, content = "", imagePaths = imagePaths, mode = ChatMode.COMPANION)
        }
        if (trimmedText.isNotBlank()) {
            _messages.value = _messages.value + Message(role = Role.USER, content = trimmedText, mode = ChatMode.COMPANION, quotedText = quotedText, quotedImagePath = quotedImagePath)
        }
        touchCurrentConversation()
        persistConversationMessages(convId, _messages.value)
        pipeline.buffer.add(CompanionTurn(trimmedText, imagePaths))
        pipeline.job?.cancel()
        val character = _currentCharacter.value
        pipeline.job = viewModelScope.launch { processCompanionBuffer(convId, character, pipeline.jobId) }
    }

    /** 拟人缓冲管线：等缓冲窗口（角色可调 1-6s），期间来新消息被打断重等；满窗口后合并统一回复 */
    private suspend fun processCompanionBuffer(convId: String, character: CharacterProfile?, id: Int) {
        val pipeline = companionPipelines[convId] ?: return
        try {
            val enabled = character?.replyBufferEnabled != false  // 默认开启
            val seconds = if (enabled) character?.replyBufferSeconds?.coerceIn(1, 6) ?: 3 else 0
            if (seconds > 0) delay(seconds * 1000L)  // 缓冲窗口：给用户连续发消息的时间；关闭则发送后立即开始思考
        } catch (e: CancellationException) {
            throw e  // 来了新消息，交还给新的 processCompanionBuffer 重新计时
        }
        val turns = pipeline.buffer.toList()
        pipeline.buffer.clear()
        if (turns.isEmpty()) {
            if (id == pipeline.jobId) { setConvLoading(convId, false); setConvTyping(convId, false) }
            return
        }
        val startTime = System.currentTimeMillis()
        val langModelName = _selectedModel.value.displayName
        // 多条合一：保留逐条边界，供模型把连发当作一个整体场景理解（质量不降级）
        val text = if (turns.size > 1)
            turns.mapIndexed { i, t -> "${i + 1}. ${t.text}" }.filter { it.isNotBlank() }.joinToString("\n").trim()
        else turns.firstOrNull()?.text.orEmpty()
        val imagePaths = turns.flatMap { it.imagePaths }
        val working = loadMessages(convId).toMutableList()
        pipeline.replyStart = working.size
        try {
            generateCompanionReply(convId, character, working, text, imagePaths, langModelName, startTime, batchSize = turns.size)
        } catch (e: CancellationException) {
            throw e  // 思考中被打断：sendCompanionMessage 负责清理半截回复
        } catch (e: Exception) {
            Log.e("FreeChat", "companion buffer reply failed", e)
            working.add(Message(role = Role.ASSISTANT, content = briefApiError(e), modelName = langModelName, mode = ChatMode.COMPANION))
            persistConversationMessages(convId, working)
        } finally {
            if (id == pipeline.jobId) {  // 只有仍是最新一轮才复位，避免旧 job 干扰新缓冲
                setConvLoading(convId, false)
                setConvTyping(convId, false)
                pipeline.replyStart = -1
            }
        }
    }

    fun clearChat() {
        saveCurrentConversation()
        _messages.value = emptyList()
        _liveReasoning.value = ""
        _liveContent.value = ""
        _thinkingTimeMs.value = 0L
        _currentMode.value = ChatMode.STANDARD
        _currentCharacter.value = null
        _currentConversationId.value = null
        syncDisplayGenerationState(null)
    }

    /** 停止当前 AI 生成，并删除本轮（用户消息 + AI 回复） */
    fun stopGeneration() {
        streamJob?.cancel()
        val convId = _currentConversationId.value
        if (convId != null) {
            companionPipelines[convId]?.let { p ->
                p.job?.cancel()
                p.buffer.clear()
                p.replyStart = -1
                p.jobId++  // 使旧缓冲 job 的 finally 失效
            }
        }
        currentCall.getAndSet(null)?.cancel()

        // ★ 停止即删除：截断「用户消息 + AI 已回复部分」
        val start = activeRoundStartIndex
        activeRoundStartIndex = -1
        if (start >= 0 && start <= _messages.value.size) {
            val kept = _messages.value.subList(0, start).toMutableList()
            val removed = _messages.value.subList(start, _messages.value.size)
            removed.forEach { m -> m.imagePaths.forEach { runCatching { File(it).delete() } } }
            _messages.value = kept
            if (kept.isEmpty()) {
                // 删空对话：清空会话 ID + 移除侧滑栏残留
                val cid = _currentConversationId.value
                _currentConversationId.value = null
                if (cid != null) {
                    _conversations.value = _conversations.value.filter { it.id != cid }
                    saveConversations()
                    messagesFile(cid).delete()
                }
            } else {
                saveCurrentConversation()
            }
        }
        _liveReasoning.value = ""
        _liveContent.value = ""
        _thinkingTimeMs.value = 0L
        if (convId != null) setConvLoading(convId, false) else _isLoading.value = false
        _isGeneratingImage.value = false
        if (convId != null) setConvTyping(convId, false) else _isTyping.value = false
        Log.d("FreeChat", "Generation stopped by user")
    }

    /** 重新生成：删除旧 AI 回复，重新调用生成逻辑，新回复插回原位置 */
    fun regenerate(aiIndex: Int) {
        if (_isLoading.value) return
        val convId = _currentConversationId.value ?: return
        val msgs = _messages.value
        if (aiIndex < 0 || aiIndex >= msgs.size || msgs[aiIndex].role != Role.ASSISTANT) return

        // 定位本轮的连续用户消息（图片/文件/文本可能有多条，都在 AI 回复之前）
        var userStart = aiIndex - 1
        while (userStart >= 0 && msgs[userStart].role == Role.USER) userStart--
        userStart++
        if (userStart >= aiIndex) return

        val userMsgs = msgs.subList(userStart, aiIndex)
        val text = userMsgs.map { it.content }.filter { it.isNotBlank() }.lastOrNull() ?: ""
        val images = userMsgs.flatMap { m -> m.imagePaths.map { PendingImage(it, detectMime(it)) } }
        val files = userMsgs.mapNotNull { m ->
            m.attachmentPath?.let { p -> PendingFile(p, m.attachmentName ?: File(p).name, detectMime(p)) }
        }
        val summaryUserText = when {
            text.isNotBlank() -> text
            images.isNotEmpty() -> "[图片]"
            files.isNotEmpty() -> "[文件: ${files.first().name}]"
            else -> ""
        }

        // 删除旧 AI 回复（新回复稍后插回原位置）
        val updated = msgs.toMutableList()
        updated.removeAt(aiIndex)
        _messages.value = updated

        setConvLoading(convId, true)
        _liveReasoning.value = ""
        _liveContent.value = ""
        _thinkingTimeMs.value = 0L

        streamJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val snapshot = applyPerConvSettings(convId)
            val langModelName = _selectedModel.value.displayName
            val visualModelName = _selectedVisualModel.value.displayName
            val timerJob = launch {
                while (_isLoading.value) {
                    _thinkingTimeMs.value = System.currentTimeMillis() - startTime
                    delay(80)
                }
            }
            val working = _messages.value.toMutableList()
            try {
                val assistantMessage = generateReply(text, images, files, langModelName, visualModelName, startTime, convId, working)
                ensureActive()
                val insertAt = minOf(aiIndex, working.size)
                working.add(insertAt, assistantMessage)
                persistConversationMessages(convId, working)
                _liveReasoning.value = ""
                _liveContent.value = ""
                summarizeAndRemember(convId, summaryUserText, assistantMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FreeChat", "regenerate failed", e)
                working.add(minOf(aiIndex, working.size), Message(role = Role.ASSISTANT, content = "重新生成失败，请稍后尝试...", modelName = langModelName))
                persistConversationMessages(convId, working)
                _liveReasoning.value = ""
                _liveContent.value = ""
            } finally {
                restoreSettings(snapshot)
                setConvLoading(convId, false)
                _isGeneratingImage.value = false
            }
        }
    }

    /** 剧情模式：改写最后一条用户消息后重新生成回复（作废旧回复，覆盖原对话） */
    fun editCompanionLastMessage(newText: String) {
        val convId = _currentConversationId.value ?: return
        val character = _currentCharacter.value
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        val msgs = _messages.value.toMutableList()
        val lastUserIdx = msgs.indexOfLast { it.role == Role.USER }
        if (lastUserIdx < 0) return
        // 保留原用户消息带的图片（剧情模式一般纯文本，兼容带图）
        val imagePaths = msgs[lastUserIdx].imagePaths
        msgs[lastUserIdx] = msgs[lastUserIdx].copy(content = trimmed)
        // 删除该用户消息之后的所有旧 AI 回复（旧回复作废，重新思考）
        if (lastUserIdx + 1 < msgs.size) msgs.subList(lastUserIdx + 1, msgs.size).clear()
        _messages.value = msgs
        persistConversationMessages(convId, msgs)

        // 取消进行中的生成，避免旧 job 写回干扰
        companionPipelines[convId]?.let { p ->
            p.job?.cancel()
            p.buffer.clear()
            p.replyStart = -1
            p.jobId++
        }

        setConvLoading(convId, true)
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val langModelName = _selectedModel.value.displayName
            val working = loadMessages(convId).toMutableList()
            try {
                generateCompanionReply(convId, character, working, trimmed, imagePaths, langModelName, startTime, batchSize = 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FreeChat", "editCompanionLastMessage failed", e)
                working.add(Message(role = Role.ASSISTANT, content = briefApiError(e), modelName = langModelName, mode = ChatMode.COMPANION))
                persistConversationMessages(convId, working)
            } finally {
                setConvLoading(convId, false)
            }
        }
    }

    /** 生成一条 AI 回复（function calling 优先，XIAOMI 关键字兜底）。不改动 _messages，由调用方负责插入。 */
    private suspend fun generateReply(
        text: String,
        images: List<PendingImage>,
        files: List<PendingFile>,
        langModelName: String,
        visualModelName: String,
        startTime: Long,
        convId: String,
        history: List<Message>
    ): Message {
        val hasImage = images.isNotEmpty()
        val hasFile = files.isNotEmpty()
        val skill = detectSkill(text, hasImage, hasFile)
        // grok-4.5 走 ccapi 中转不吃 function calling 的 tools 参数，单独排除走关键字兜底
        val supportsTools = _selectedModel.value.provider != Provider.XIAOMI

        if (supportsTools) {
            // 工具类技能不浪费搜索（日历/文档/生图/文件）
            val needsSearch = _enableWebSearch.value && needsWebSearch(text) && skill == Skill.TEXT
            val serpResults = if (needsSearch) callSerpApiGoogle(optimizeSearchQuery(text)) else ""
            val tools = buildTools(hasImage, hasFile)
            val result = callDeepSeekApiStreaming(needsSearch, serpResults, tools, convId, history)
            val elapsed = System.currentTimeMillis() - startTime
            return if (result.toolCalls.isNotEmpty()) {
                _liveReasoning.value = ""  // 工具决策过程的思考不展示
                _liveContent.value = ""
                executeToolCall(result.toolCalls.first(), text, images, files, langModelName, visualModelName, elapsed)
            } else {
                Message(
                    role = Role.ASSISTANT, content = result.content,
                    modelName = langModelName,
                    thinkingTimeMs = elapsed, reasoningContent = result.reasoning
                )
            }
        } else {
            // XIAOMI 等不支持 tools → 关键字兜底
            val needsSearch = _enableWebSearch.value && needsWebSearch(text) && skill == Skill.TEXT
            val serpResults = if (needsSearch) callSerpApiGoogle(optimizeSearchQuery(text)) else ""
            return executeSkill(skill, text, images, files, langModelName, visualModelName, startTime, needsSearch, serpResults, convId, history)
        }
    }

    /** 执行模型返回的工具调用，返回对应能力的结果消息 */
    private suspend fun executeToolCall(
        tool: ToolCall,
        text: String,
        images: List<PendingImage>,
        files: List<PendingFile>,
        langModelName: String,
        visualModelName: String,
        elapsed: Long
    ): Message {
        val args = tool.arguments
        return when (tool.name) {
            "generate_image" -> {
                val prompt = args.optString("prompt") ?: text
                _isGeneratingImage.value = true
                val imageUrls = callDoubaoImageGen(prompt)
                Message(
                    role = Role.ASSISTANT,
                    content = if (imageUrls.isEmpty()) "生成失败，请换个方式描述试试（可能包含违规内容）。" else "",
                    modelName = visualModelName, thinkingTimeMs = elapsed, imageUrls = imageUrls
                )
            }
            "edit_image" -> {
                val prompt = args.optString("prompt") ?: text.ifBlank { "请优化这张图片" }
                _isGeneratingImage.value = true
                val encoded = encodeImagesForApi(images)
                val refImage = encoded.firstOrNull()
                val imageUrls = if (refImage != null) callDoubaoImageGen(prompt, refImage.first, refImage.second) else emptyList()
                Message(
                    role = Role.ASSISTANT,
                    content = if (imageUrls.isNotEmpty()) "已根据你的要求处理图片：" else "图片处理失败，请换个方式描述试试。",
                    modelName = visualModelName, thinkingTimeMs = elapsed, imageUrls = imageUrls
                )
            }
            "analyze_image" -> {
                val prompt = args.optString("question") ?: "请详细描述这张图片的内容"
                val encoded = encodeImagesForApi(images)
                val result = if (encoded.isEmpty()) "图片读取失败，请重试。" else callVisionChat(encoded, prompt)
                Message(
                    role = Role.ASSISTANT, content = result,
                    modelName = _selectedVisionModel.value?.displayName ?: "",
                    thinkingTimeMs = elapsed
                )
            }
            "read_calendar" -> Message(role = Role.ASSISTANT, content = readCalendar(), modelName = "系统日历", thinkingTimeMs = elapsed)
            "generate_document" -> {
                val type = args.optString("type") ?: "docx"
                val prompt = args.optString("prompt") ?: text
                val typedText = when (type) {
                    "pptx" -> "做一份PPT：$prompt"
                    "xlsx" -> "做一份Excel表格：$prompt"
                    else -> "写一份Word文档：$prompt"
                }
                val doc = generateDocument(typedText, files)
                val replyContent = when {
                    doc.error != null -> doc.error
                    doc.path != null -> "已为你生成「${doc.fileName}」，点击下方文件即可打开编辑。"
                    else -> "文档生成失败，请换个方式描述试试。"
                }
                Message(
                    role = Role.ASSISTANT, content = replyContent,
                    modelName = langModelName, thinkingTimeMs = elapsed,
                    attachmentPath = doc.path, attachmentName = doc.fileName
                )
            }
            "understand_file" -> {
                val question = args.optString("question") ?: text
                val reply = understandFile(question, files)
                Message(role = Role.ASSISTANT, content = reply, modelName = langModelName, thinkingTimeMs = elapsed)
            }
            else -> Message(role = Role.ASSISTANT, content = "（未识别的操作，请换个说法试试）", modelName = langModelName, thinkingTimeMs = elapsed)
        }
    }

    /** 关键字兜底执行（XIAOMI 等不支持 function calling 的模型） */
    private suspend fun executeSkill(
        skill: Skill,
        text: String,
        images: List<PendingImage>,
        files: List<PendingFile>,
        langModelName: String,
        visualModelName: String,
        startTime: Long,
        needsSearch: Boolean,
        serpResults: String,
        convId: String,
        history: List<Message>
    ): Message = when (skill) {
        Skill.IMAGE_EDIT -> {
            val imagePrompt = text.ifBlank { "请优化这张图片" }
            _isGeneratingImage.value = true
            val encoded = encodeImagesForApi(images)
            val refImage = encoded.firstOrNull()
            val imageUrls = if (refImage != null) {
                callDoubaoImageGen(imagePrompt, refImage.first, refImage.second)
            } else emptyList()
            val elapsed = System.currentTimeMillis() - startTime
            val replyContent = if (imageUrls.isNotEmpty()) {
                "已根据你的要求处理图片："
            } else "图片处理失败，请换个方式描述试试。"
            Message(
                role = Role.ASSISTANT, content = replyContent,
                modelName = visualModelName,
                thinkingTimeMs = elapsed, imageUrls = imageUrls
            )
        }
        Skill.VISION -> {
            val visionPrompt = text.ifBlank { "请详细描述这张图片的内容" }
            val encoded = encodeImagesForApi(images)
            val result = if (encoded.isEmpty()) {
                "图片读取失败，请重试。"
            } else {
                callVisionChat(encoded, visionPrompt)
            }
            val elapsed = System.currentTimeMillis() - startTime
            Message(
                role = Role.ASSISTANT, content = result,
                modelName = _selectedVisionModel.value?.displayName ?: "",
                thinkingTimeMs = elapsed, reasoningContent = _liveReasoning.value
            )
        }
        Skill.IMAGE_GEN -> {
            _isGeneratingImage.value = true
            val imageUrls = callDoubaoImageGen(text)
            val elapsed = System.currentTimeMillis() - startTime
            val replyContent = if (imageUrls.isEmpty()) {
                "生成失败，请换个方式描述试试（可能包含违规内容）。"
            } else ""
            Message(
                role = Role.ASSISTANT, content = replyContent,
                modelName = visualModelName,
                thinkingTimeMs = elapsed, imageUrls = imageUrls
            )
        }
        Skill.CALENDAR -> {
            val reply = readCalendar()
            val elapsed = System.currentTimeMillis() - startTime
            Message(role = Role.ASSISTANT, content = reply, modelName = "系统日历", thinkingTimeMs = elapsed)
        }
        Skill.DOCUMENT -> {
            val doc = generateDocument(text, files)
            val elapsed = System.currentTimeMillis() - startTime
            val replyContent = when {
                doc.error != null -> doc.error
                doc.path != null -> "已为你生成「${doc.fileName}」，点击下方文件即可打开编辑。"
                else -> "文档生成失败，请换个方式描述试试。"
            }
            Message(
                role = Role.ASSISTANT, content = replyContent,
                modelName = langModelName, thinkingTimeMs = elapsed,
                attachmentPath = doc.path, attachmentName = doc.fileName
            )
        }
        Skill.FILE -> {
            val reply = understandFile(text, files)
            val elapsed = System.currentTimeMillis() - startTime
            Message(role = Role.ASSISTANT, content = reply, modelName = langModelName, thinkingTimeMs = elapsed)
        }
        Skill.TEXT -> {
            val result = callDeepSeekApiStreaming(needsSearch, serpResults, emptyList(), convId, history)
            val elapsed = System.currentTimeMillis() - startTime
            Message(
                role = Role.ASSISTANT, content = result.content,
                modelName = langModelName,
                thinkingTimeMs = elapsed, reasoningContent = result.reasoning
            )
        }
    }

    // ========== 拟人陪伴模式 ==========
    /** 把异常转成一句简短的用户可读错误（按 HTTP 状态码 + 异常类型映射） */
    private fun briefApiError(e: Exception): String {
        val m = e.message.orEmpty()
        val code = Regex("API error (\\d+)").find(m)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            // 网络不稳定 / 未连接
            e is java.net.SocketTimeoutException || e is java.net.ConnectException || e is java.net.UnknownHostException ->
                "请求超时，请检查网络连接是否正常。"
            m.contains("timeout", true) || m.contains("超时") -> "请求超时，请检查网络连接是否正常。"
            // 模型类型不对（如把生图模型填进语言模型）
            m.contains("model type", true) || m.contains("not a chat model", true) ||
                m.contains("does not support", true) || m.contains("不支持的模型") ->
                "模型类型错误，请确保模型id对应其模型类型。"
            // 配置有误（key / id / 地址不对）
            code == 401 || code == 403 || code == 404 -> "模型配置有误，请检查API信息是否正确。"
            // 服务端问题 / 额度不足
            code != null && (code == 429 || code >= 500) -> "连接超时，请稍后重试。"
            else -> "连接超时，请稍后重试。"
        }
    }

    /** 应用每角色独立的语言模型/联网设置（临时覆盖，结束后恢复全局） */
    private fun applyCharacterSettings(character: CharacterProfile?): SettingsSnapshot {
        val snap = SettingsSnapshot(
            _selectedModel.value, _selectedVisualModel.value, _selectedVisionModel.value,
            _enableWebSearch.value, _showThinking.value, _tempMode.value,
            _lengthMode.value, _autoSummarizeMemory.value
        )
        val ch = character ?: return snap
        if (ch.languageModelId.isNotBlank()) {
            val found = modelsOfType(ModelType.LANGUAGE).find { it.id == ch.languageModelId }
            Log.d("FreeChat", "applyCharacterSettings langModelId=${ch.languageModelId} found=${found?.displayName}")
            found?.let { _selectedModel.value = it }
        }
        if (ch.visionModelId.isNotBlank()) {
            modelsOfType(ModelType.VISION).find { it.id == ch.visionModelId }?.let { _selectedVisionModel.value = it }
        }
        ch.enableWebSearch?.let { _enableWebSearch.value = it }
        return snap
    }

    /** 拟人模式情绪：影响回复条数、间隔与是否回复（模型输出情绪标签映射 + 本地关键词兜底） */
    private enum class CompanionMood { NEUTRAL, HAPPY, EXCITED, ANGRY, SAD, WRONGED, DISMISSIVE, SHY, BORED }

    /** 根据用户消息关键词判断拟人角色情绪（模型未识别情绪时的本地兜底） */
    private fun detectCompanionMood(text: String): CompanionMood {
        val t = text.trim()
        val angry = listOf("滚", "讨厌", "烦", "闭嘴", "神经病", "有病", "傻", "蠢", "滚蛋", "别烦", "不想理", "别说了")
        if (angry.any { t.contains(it) }) return CompanionMood.ANGRY
        val happy = listOf("哈哈", "开心", "好玩", "笑死", "喜欢", "太好了", "棒", "爱你", "想你", "嘻嘻")
        if (happy.any { t.contains(it) }) return CompanionMood.HAPPY
        val bored = listOf("哦", "嗯", "呵呵", "随便", "都行", "无所谓")
        if (t.length <= 2 && bored.any { t == it }) return CompanionMood.BORED
        return CompanionMood.NEUTRAL
    }

    /** 把模型输出的情绪标签解析成 CompanionMood（识别不了返回 null，走本地兜底） */
    private fun parseEmotionLabel(label: String): CompanionMood? = when (label.trim()) {
        "开心" -> CompanionMood.HAPPY
        "兴奋" -> CompanionMood.EXCITED
        "生气" -> CompanionMood.ANGRY
        "难过" -> CompanionMood.SAD
        "委屈" -> CompanionMood.WRONGED
        "敷衍" -> CompanionMood.DISMISSIVE
        "害羞" -> CompanionMood.SHY
        "无聊" -> CompanionMood.BORED
        "平静", "平常" -> CompanionMood.NEUTRAL
        else -> null
    }

    /** 检测用户是否在道晚安/说去睡（用于「可选不回复」与时间观念） */
    private fun detectGoodnight(text: String): Boolean {
        val t = text.trim()
        val keywords = listOf("晚安", "睡了", "我要睡", "去睡了", "睡觉了", "要睡了", "困了", "睡觉去", "我先睡", "睡了没")
        return keywords.any { t.contains(it) }
    }

    /** 本地检测关系事件（表白/暧昧/冲突/破裂），命中则触发 AI 深度确认 */
    private fun detectRelationshipEvent(text: String): String? {
        val confession = listOf("喜欢你", "我爱你", "爱你", "在一起", "做我女", "做我男", "交往", "我喜欢你", "跟我在一起", "做我女朋友", "做我男朋友", "告白", "表白", "心动")
        if (confession.any { text.contains(it) }) return "表白"
        val intimate = listOf("想你", "抱抱", "亲亲", "么么", "宝贝", "亲爱的", "老婆", "老公", "亲一个", "吻你", "贴贴")
        if (intimate.any { text.contains(it) }) return "暧昧升温"
        val breakup = listOf("分手", "绝交", "拉黑", "别联系", "再见吧", "算了吧", "不要联系", "互删")
        if (breakup.any { text.contains(it) }) return "关系破裂"
        val conflict = listOf("滚", "讨厌你", "烦死了", "别烦我", "不想理你", "吵架", "冷战")
        if (conflict.any { text.contains(it) }) return "冲突"
        return null
    }

    /** 根据性格/MBTI/记忆里的作息描述，生成当天的睡觉/起床时刻（每天微调，不重复） */
    private fun generateSleepSchedule(ch: CharacterProfile): Pair<Int, Int> {
        val t = ch.personalityText + ch.memoryPerception
        val nightOwl = t.contains("熬夜") || t.contains("夜猫") || t.contains("晚睡") || t.contains("夜猫子")
        val early = t.contains("规律") || t.contains("早睡") || t.contains("作息规律")
        val baseSleep = when {
            nightOwl -> 2            // 凌晨 2 点睡
            early -> 23              // 23 点睡
            ch.mbtiPJ < 0.5f -> 1    // P 随性，偏晚睡
            else -> 23               // J 决断，偏规律
        }
        val sleepHour = (baseSleep + kotlin.random.Random.nextInt(-1, 2)).coerceIn(0, 4)
        val duration = if (nightOwl) kotlin.random.Random.nextInt(8, 10) else kotlin.random.Random.nextInt(6, 9)
        val wakeHour = (sleepHour + duration) % 24
        return sleepHour to wakeHour
    }

    /** 当前是否在 AI 的睡觉窗口内（开启模拟作息才生效；当天首次调用时生成作息，按对话隔离） */
    private fun isSleepingNow(convId: String, character: CharacterProfile?): Boolean {
        val ch = character ?: return false
        if (ch.plotSimulation) return false  // 剧情模式无真实作息，时间以用户剧情设定为准
        if (!ch.sleepSimulation) return false
        val now = Calendar.getInstance()
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        if (sleepDateKey[convId] != dateKey) {
            val (s, w) = generateSleepSchedule(ch)
            sleepDateKey[convId] = dateKey
            sleepAtHour[convId] = s
            wakeAtHour[convId] = w
        }
        val sh = sleepAtHour[convId] ?: return false
        val wh = wakeAtHour[convId] ?: return false
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return if (sh <= wh) {
            hour in sh until wh
        } else {
            hour >= sh || hour < wh
        }
    }

    /** 拟人模式结构化回复：模型输出的情绪标签 + 分条回复 */
    private data class CompanionReply(val emotion: String, val segments: List<String>)

    /** 拟人模式的一轮用户输入：文本 + 附带的图片路径 */
    private data class CompanionTurn(val text: String, val imagePaths: List<String> = emptyList())

    /** 关系演进分析结果：AI 判断这段对话对亲密度/关系阶段的影响 */
    private data class RelationshipUpdate(val intimacyDelta: Int, val stageChange: String, val reason: String)

    /** 拟人陪伴：非流式生成 + 随机思考延迟 + 情绪化随机条数/间隔，逐条显示，模拟真人节奏（按对话隔离） */
    private suspend fun generateCompanionReply(convId: String, character: CharacterProfile?, working: MutableList<Message>, text: String, imagePaths: List<String>, langModelName: String, startTime: Long, batchSize: Int = 1) {
        val snapshot = applyCharacterSettings(character)
        try {
            // 模拟作息：睡觉窗口内沉默不回复（记录漏回，起床后自然解释）
            if (isSleepingNow(convId, character)) {
                sleepingMissedMessages[convId] = (sleepingMissedMessages[convId] ?: 0) + 1
                return
            }
            // 模拟真人：先随机等待，不刚发就显示「正在输入」
            delay(kotlin.random.Random.nextLong(1500L, 4500L))
            setConvTyping(convId, true)

            // 图片：先识图分析，把识图结果作为上下文喂给回复（拟人结合人设评论图片）
            var imageContext = ""
            if (imagePaths.isNotEmpty()) {
                val encoded = encodeImagesForApi(imagePaths.map { PendingImage(it, detectMime(it)) })
                if (encoded.isNotEmpty()) {
                    imageContext = callVisionChat(encoded, "请用几句话描述这张图片：画面里有什么、什么场景、有什么值得注意的细节，口语化一点。")
                }
            }

            var reply = callCompanionApi(convId, character, working, text, imageContext, batchSize = batchSize)
            var segments = reply.segments

            // 空回复兜底：重试一次（强调必须输出正文），仍空则发个省略号（自然无语，不显示「（空回复）」）
            if (segments.isEmpty()) {
                reply = callCompanionApi(convId, character, working, text, imageContext, forceReply = true, batchSize = batchSize)
                segments = reply.segments
            }

            if (segments.isEmpty()) {
                setConvTyping(convId, false)
                working.add(Message(
                    role = Role.ASSISTANT, content = "…",
                    modelName = langModelName, mode = ChatMode.COMPANION,
                    thinkingTimeMs = System.currentTimeMillis() - startTime
                ))
                persistConversationMessages(convId, working)
                return
            }

            // 情绪：模型输出优先，本地关键词兜底（剧情模式不解析情绪，走简化节奏）
            val plotMode = character?.plotSimulation == true
            val mood = if (plotMode) CompanionMood.NEUTRAL else (parseEmotionLabel(reply.emotion) ?: detectCompanionMood(text))

            // 情绪决定回复条数（随机）；剧情模式最多 3 段、全部展示
            val maxCount = if (plotMode) segments.size.coerceAtMost(3) else when (mood) {
                CompanionMood.ANGRY -> if (kotlin.random.Random.nextFloat() < 0.35f) 0 else 1  // 生气可能不回复
                CompanionMood.SAD, CompanionMood.WRONGED -> 1
                CompanionMood.DISMISSIVE, CompanionMood.SHY, CompanionMood.BORED -> 1
                CompanionMood.EXCITED -> kotlin.random.Random.nextInt(2, 4)
                CompanionMood.HAPPY -> kotlin.random.Random.nextInt(2, 4)
                CompanionMood.NEUTRAL -> kotlin.random.Random.nextInt(1, 4)
            }
            val toShow = segments.take(maxCount)

            if (toShow.isEmpty()) {
                setConvTyping(convId, false)
                return  // 生气不回复
            }

            for ((i, seg) in toShow.withIndex()) {
                setConvTyping(convId, true)
                // 每条间隔随机（情绪影响），时而快时而慢
                val interval = if (plotMode) kotlin.random.Random.nextLong(1500L, 3000L) else when (mood) {
                    CompanionMood.ANGRY -> kotlin.random.Random.nextLong(4000L, 7000L)
                    CompanionMood.SAD, CompanionMood.WRONGED -> kotlin.random.Random.nextLong(2000L, 4500L)
                    CompanionMood.DISMISSIVE, CompanionMood.BORED -> kotlin.random.Random.nextLong(2000L, 4200L)
                    CompanionMood.SHY -> kotlin.random.Random.nextLong(2000L, 3500L)
                    CompanionMood.EXCITED -> kotlin.random.Random.nextLong(800L, 2000L)
                    CompanionMood.HAPPY -> kotlin.random.Random.nextLong(900L, 2600L)
                    CompanionMood.NEUTRAL -> kotlin.random.Random.nextLong(1500L, 3800L)
                }
                delay(interval)
                setConvTyping(convId, false)
                working.add(Message(
                    role = Role.ASSISTANT, content = seg,
                    modelName = langModelName, mode = ChatMode.COMPANION,
                    thinkingTimeMs = System.currentTimeMillis() - startTime
                ))
                persistConversationMessages(convId, working)
                if (i < toShow.size - 1) setConvTyping(convId, true)
            }

            // ★ 回复完成收尾：清零漏回计数（已在提示词里解释过），异步写入记忆 + 关系演进
            sleepingMissedMessages[convId] = 0
            val replyText = toShow.joinToString("\n")
            notifyReplyIfBackground(character?.name?.ifBlank { "FreeChat" } ?: "FreeChat", replyText)
            if (replyText.isNotBlank()) {
                viewModelScope.launch {
                    summarizeAndRemember(convId, text, Message(role = Role.ASSISTANT, content = replyText, mode = ChatMode.COMPANION), character?.highQualityMemory == true)
                    updateRelationship(convId, character, text, replyText)
                }
            }
        } finally {
            restoreSettings(snapshot)
            setConvTyping(convId, false)
        }
    }

    /** 关系演进：回复完成后异步分析这段对话，更新亲密度与关系阶段，重要变化写记忆（按对话隔离） */
    private suspend fun updateRelationship(convId: String, character: CharacterProfile?, userText: String, aiReply: String) {
        val ch = character?.normalized() ?: return
        if (ch.relationshipPreset.isBlank() && ch.relationshipText.isBlank()) return  // 未设关系则不跟踪
        val event = detectRelationshipEvent(userText) ?: return  // 无明显关系事件，不打扰 AI
        val result = callRelationshipAnalysis(ch, userText, aiReply, event) ?: return
        if (result.intimacyDelta == 0 && result.stageChange.isBlank()) return  // 无变化

        val newIntimacy = (ch.intimacy + result.intimacyDelta).coerceIn(0, 100)
        val newStage = result.stageChange.ifBlank { ch.relationshipStage }
        val updated = ch.copy(intimacy = newIntimacy, relationshipStage = newStage)
        if (_currentConversationId.value == convId) _currentCharacter.value = updated

        _conversations.value = _conversations.value.map {
            if (it.id == convId) it.copy(characterProfile = updated) else it
        }
        saveConversations()

        // 重要变化（阶段改变或亲密度大幅波动）写重要记忆，永不遗忘
        if (result.stageChange.isNotBlank() || kotlin.math.abs(result.intimacyDelta) >= 10) {
            val summary = "关系：${result.reason}".take(80)
            memoryManager.append(convId, MemoryEntry(
                summary = summary, keywords = extractKeywords(result.reason), importance = true, kind = "plot"
            ))
        }
    }

    /** 调 LLM 分析一段对话对关系的影响（结构化 JSON，失败返回 null） */
    private suspend fun callRelationshipAnalysis(ch: CharacterProfile, userText: String, aiReply: String, event: String): RelationshipUpdate? = withContext(Dispatchers.IO) {
        try {
            val model = _selectedModel.value
            val (url, key) = routeModelEndpoint(model)
            val mbti = if (ch.mbtiType.isBlank()) ch.deriveMbtiType() else ch.mbtiType
            val prompt = """
你和用户当前关系：${ch.relationshipStage.ifBlank { ch.relationshipPreset.ifBlank { "未设定" } }}，亲密度 ${ch.intimacy}/100。
角色性格：${ch.personalityPresets.joinToString("、").ifBlank { "无" }}${if (ch.personalityText.isNotBlank()) "；${ch.personalityText}" else ""}，MBTI $mbti。
检测到可能的关系事件：$event
用户说：$userText
你的回复：${aiReply.take(300)}

请判断这段对话对关系的影响，只输出一行 JSON：
{"intimacyDelta": 整数(-30到+30), "stageChange": "新的关系阶段（如 暧昧、情侣、前任、朋友），无变化则空字符串", "reason": "一句中文说明"}
规则：表白/暧昧/亲密 → intimacyDelta 为正；冲突/分手/冷淡 → 为负；开玩笑或无明显影响 → 0。亲密度变化幅度要符合性格——慢热、内向、谨慎的人涨得慢（正 delta 偏小），自来熟、外向、热情的人涨得快；理性的人更克制，感性的人波动更大。关系阶段只在明显进展时改（同学→暧昧、暧昧→情侣、情侣→前任），否则留空。
""".trimIndent()
            val body = gson.toJson(mapOf(
                "model" to model.id,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to "你是关系状态评估器，只输出一行 JSON，不要任何解释或 markdown。"),
                    mapOf("role" to "user", "content" to prompt)
                ),
                "stream" to false, "temperature" to 0.3
            )).toRequestBody(JSON_MEDIA)
            val resp = client.newCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json").post(body).build()).execute()
            val rBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext null
            val raw = JsonParser.parseString(rBody).asJsonObject
                .getAsJsonArray("choices")?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString?.trim() ?: ""
            val json = extractJsonObject(raw) ?: return@withContext null
            val delta = json.get("intimacyDelta")?.asInt ?: 0
            val stage = json.get("stageChange")?.asString ?: ""
            val reason = json.get("reason")?.asString ?: ""
            RelationshipUpdate(delta.coerceIn(-30, 30), stage.trim(), reason.trim())
        } catch (e: Exception) {
            Log.w("FreeChat", "relationship analysis failed", e)
            null
        }
    }

    /** 从模型输出里提取 JSON 对象（容错：兼容首尾多余文字 / markdown 代码块） */
    private fun extractJsonObject(raw: String): JsonObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JsonParser.parseString(raw.substring(start, end + 1)).asJsonObject
        } catch (_: Exception) { null }
    }

    /** 拟人模式非流式 API 调用：返回结构化结果（情绪标签 + 分条回复）；按对话隔离传入 convId/角色/历史 */
    private suspend fun callCompanionApi(convId: String, character: CharacterProfile?, working: List<Message>, text: String, imageContext: String = "", forceReply: Boolean = false, batchSize: Int = 1): CompanionReply = withContext(Dispatchers.IO) {
        val model = _selectedModel.value
        val (url, key) = routeModelEndpoint(model)

        val messages = mutableListOf<Map<String, Any?>>()
        val sys = buildCompanionSystemPrompt(character, convId, working)
        if (sys.isNotEmpty()) messages.add(mapOf("role" to "system", "content" to sys))
        // ★ 记忆注入：把该对话的历史要点（尤其关系转变等重要记忆）作为补充上下文，修复「聊过即忘」
        if (_autoSummarizeMemory.value) {
            val memCtx = memoryManager.buildMemoryContext(convId, text, character?.highQualityMemory == true)
            if (memCtx.isNotBlank()) {
                messages.add(mapOf("role" to "system", "content" to memCtx))
            }
        }
        // 引用上下文：本次发送带了引用，注入给 AI（只后台告知，不在前台消息框显示）
        pendingQuoteText?.let { q ->
            messages.add(mapOf("role" to "system", "content" to q))
            pendingQuoteText = null
        }
        messages.addAll(working.takeLast(30).map { m ->
            val role = when (m.role) { Role.USER -> "user"; Role.ASSISTANT -> "assistant"; else -> "system" }
            val content = if (m.imagePaths.isNotEmpty() && m.content.isBlank()) "[图片]" else m.content
            mapOf<String, Any?>("role" to role, "content" to content)
        })
        // 图片识图结果作为上下文（拟人结合人设评论图片）
        if (imageContext.isNotBlank()) {
            messages.add(mapOf("role" to "system", "content" to "用户刚发了一张图片，图片内容：$imageContext。你可以结合图片内容自然回应，但要符合你的人设。"))
        }
        // 空回复兜底：强制要求输出正文
        if (forceReply) {
            messages.add(mapOf("role" to "system", "content" to "这次必须输出至少一句回复正文，哪怕只是一个「嗯」「..」「？」或「。」也行，不要只输出情绪标签。"))
        }
        // 多条合一：用户刚才连发多条消息，把这一串当作一个整体场景理解，别逐条机械对应。
        // 按你的人设决定回几条、回多长——话多可以回好几条，话少可以只回一条甚至不回。
        if (batchSize > 1) {
            messages.add(mapOf("role" to "system", "content" to "用户刚才一口气发了 $batchSize 条消息（见上面的连续消息）。你要把它们当作一个整体场景来理解，而不是逐条机械地各回一句。怎么回由你决定：话痨、兴奋时可以自然回好几条；寡言、敷衍时可以只回一条，甚至觉得没必要回就不回。像真人一样自然。"))
        }
        // 联网搜索（每角色独立开关）
        val searchOn = character?.enableWebSearch ?: _enableWebSearch.value
        if (searchOn && needsWebSearch(text)) {
            val serp = callSerpApiGoogle(optimizeSearchQuery(text))
            if (serp.isNotEmpty()) messages.add(mapOf("role" to "system", "content" to serp))
        }

        val body = gson.toJson(mapOf(
            "model" to model.id, "messages" to messages, "stream" to false, "temperature" to 1.0
        )).toRequestBody(JSON_MEDIA)
        val resp = client.newCall(Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json").post(body).build()).execute()
        val rBody = resp.body?.string() ?: ""
        if (!resp.isSuccessful) throw Exception("API error ${resp.code}: ${rBody.take(200)}")
        val raw = JsonParser.parseString(rBody).asJsonObject
            .getAsJsonArray("choices")?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")?.get("content")?.asString?.trim() ?: ""

        val plotMode = character?.plotSimulation == true
        val emotion: String
        val bodyLines: List<String>
        if (plotMode) {
            // 剧情模式：不解析情绪标签，整段作为「一条」完整剧情（不分多条消息，空行仅作分段排版）
            emotion = ""
            bodyLines = listOf(raw.trim()).filter { it.isNotEmpty() }
        } else {
            // 解析情绪标签：首行标准格式「[情绪:开心]」；也兼容模型漏写前缀的裸标签「[开心]」，
            // 只有识别出的情绪才当标签剥掉，否则当正文保留，避免把正常内容误删。
            val lines = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val emotionTag = Regex("""^\s*\[(?:情绪|emotion)?\s*[:：]?\s*([^\]]+)]\s*$""")
            val first = lines.firstOrNull()
            val parsed = first?.let { emotionTag.find(it) }?.let { m ->
                val label = m.groupValues[1].trim()
                if (parseEmotionLabel(label) != null) label else null
            }
            emotion = parsed ?: ""
            val rawBodyLines = if (parsed != null) lines.drop(1) else lines
            // 剥掉正文里漏出的裸方括号标签（如 [敷衍][平静][无语]），这些不是真 emoji，绝不能显示
            val bracketToken = Regex("""\[[^\]]{1,6}]\s*""")
            bodyLines = rawBodyLines.map { it.replace(bracketToken, "").trim() }.filter { it.isNotEmpty() }
        }
        CompanionReply(emotion, bodyLines)
    }

    /** 用户自定义模型：用其 apiBaseUrl + 标准 OpenAI 路径 + 其 apiKey */
    private fun customEndpoint(model: ModelInfo, path: String): Pair<String, String> =
        (model.apiBaseUrl.trimEnd('/') + path) to model.apiKey

    /** 按 provider 路由模型 → 端点 + 密钥 */
    private fun routeModelEndpoint(model: ModelInfo): Pair<String, String> = when (model.provider) {
        Provider.XIAOMI -> "$XIAOMI_BASE_URL/chat/completions" to (if (model.id == "mimo-v2.5-pro-ultraspeed") XIAOMI_ULTRASPEED_API_KEY else XIAOMI_API_KEY)
        Provider.DOUBAO -> "$DOUBAO_BASE_URL/chat/completions" to DOUBAO_API_KEY
        Provider.CUSTOM -> customEndpoint(model, "/v1/chat/completions")
    }

    /** 拟人模式系统提示词：微信朋友聊天风格 + 完整人设画像 + 时间观念 + 情绪输出指令 */
    private fun buildCompanionSystemPrompt(character: CharacterProfile?, convId: String, messages: List<Message>): String {
        val ch = character?.normalized()

        val parts = mutableListOf<String>()
        parts.add("你是一个在微信上跟朋友聊天的人，不是AI助手。你要完全代入下面的人设，像一个真实的人一样思考和说话。")

        if (ch != null) {
            if (ch.personaPrompt.isNotBlank()) {
                // 深度学习生成的专属提示词：最立体、最符合用户设定
                parts.add("【你的人设——必须严格代入，贯穿每一句回复】\n${ch.personaPrompt}")
            } else {
                val sb = StringBuilder("【你的人设——必须严格代入，贯穿每一句回复】\n")
                if (ch.name.isNotBlank()) sb.append("- 名字：${ch.name}\n")
                if (ch.gender.isNotBlank()) sb.append("- 性别：${ch.gender}\n")
                if (ch.age.isNotBlank()) sb.append("- 年龄：${ch.age}\n")
                if (ch.mbtiType.isNotBlank()) {
                    sb.append("- MBTI：${ch.mbtiType}（${mbtiDesc(ch)}）。你的思维方式和情绪反应要符合这个类型\n")
                }
                if (ch.personalityPresets.isNotEmpty() || ch.personalityText.isNotBlank()) {
                    sb.append("- 性格：")
                    if (ch.personalityPresets.isNotEmpty()) sb.append(ch.personalityPresets.joinToString("、"))
                    if (ch.personalityText.isNotBlank()) sb.append(if (ch.personalityPresets.isNotEmpty()) "；${ch.personalityText}" else ch.personalityText)
                    sb.append("。这决定了你的说话风格、用词和情绪\n")
                }
                if (!ch.highQualityMemory && ch.memoryPerception.isNotBlank()) {
                    sb.append("- 背景记忆：${ch.memoryPerception}。这些是你已知的关于用户和你自己的事，聊天时要自然体现\n")
                }
                parts.add(sb.toString().trimEnd())
            }
            // 记忆感知注入方式由「高质量检索回复」开关决定
            if (ch.highQualityMemory && ch.memoryPerception.isNotBlank()) {
                // 高质量：原文完整注入，最高优先级
                parts.add("【用户告诉你的背景与过往——最高优先级的事实，必须牢记并精准遵守】\n${ch.memoryPerception}\n\n这些是用户明确告诉你的真实经历和设定（时间、地点、人物、因果都精确写在上面）。任何时候都不能记错、脑补、张冠李戴或篡改——尤其时间语境（比如「高考后的暑假」就是暑假、不是上学期间），用户没说过的信息绝对不要自行脑补。如果上面的其他设定与这里冲突，一律以这里为准。")
            }
            // 人物形象（独立注入，即使已有 personaPrompt 也生效）
            if (ch.appearanceText.isNotBlank() || ch.appearanceImageDescs.isNotEmpty()) {
                val asb = StringBuilder("【你的外在形象——你对自己长相、身材、气质的认知】")
                if (ch.appearanceText.isNotBlank()) asb.append("\n- ${ch.appearanceText}")
                if (ch.appearanceImageDescs.isNotEmpty()) asb.append("\n- ${ch.appearanceImageDescs.joinToString("；")}")
                asb.append("\n当话题涉及长相、自拍、身材、穿着打扮时，要自然地体现这个形象认知。")
                parts.add(asb.toString())
            }
            // 人物关系（独立注入，即使已有 personaPrompt 也生效）：决定回复的亲密尺度
            if (ch.relationshipStage.isNotBlank() || ch.relationshipPreset.isNotBlank() || ch.relationshipText.isNotBlank()) {
                val rsb = StringBuilder("【你和用户的关系——决定你回复的亲密尺度与相处方式，必须严格遵守】")
                if (ch.relationshipStage.isNotBlank()) rsb.append("\n- 当前关系：${ch.relationshipStage}")
                if (ch.relationshipPreset.isNotBlank() && ch.relationshipPreset != ch.relationshipStage) rsb.append("\n- 初始关系：${ch.relationshipPreset}")
                if (ch.relationshipText.isNotBlank()) rsb.append("\n- 关系细节：${ch.relationshipText}")
                rsb.append("\n- 亲密度：${ch.intimacy}/100")
                rsb.append("\n尺度要求：结合上面的关系与亲密度，自己把握亲昵尺度——关系越亲密可以越暧昧亲昵、说情话撒娇用昵称；普通朋友、同事、同学则保持分寸不越界。关系会随聊天剧情自然发展，不是一成不变的标签。")
                parts.add(rsb.toString())
            }
            parts.add("你的人设不是摆设。用户和你聊天时，你的每一句回复都要符合上述人设——性格决定语气和用词，MBTI 决定思维习惯，记忆里的信息要自然融入对话。")
            parts.add("情绪要符合你的人设：不同性格和 MBTI 的人情绪反应完全不同——冷静理性的人不容易生气、生气也更克制甚至冷处理；敏感温柔的人更容易委屈难过；暴躁直率的人生气会更冲更直接。不要总是同一个情绪，要跟着对话语境和对方的态度自然变化。")
            parts.add("【语言风格——必须严格贴合人设，像真人一样说话】你的用词、语气、口癖、称呼、emoji 习惯都要符合上面的人设性格。不同人设说同一件事完全不同：直率犀利的人直接怼、温柔的人委婉、高冷的人话少冷淡、幽默的人爱玩梗。允许出现极端或激烈的用词（包括脏话、爆粗），但要符合人设和上下文——吵架情绪上头可以脱口而出、被冒犯时可以不客气、亲密的恋人之间可以打情骂俏或嗔怪抱怨、朋友之间可以开玩笑互损。一切都是自然流露，不要刻意回避脏话，也不要为了脏话而脏话；具体尺度完全由人设和剧情决定：暴躁直率的人设可以更冲，斯文温柔的人设即使生气也只是冷处理或淡淡一句。")
        }

        // ★ 记忆与上下文一致性：降低「聊过即忘 / 答非所问 / 自相矛盾」等基础错误
        parts.add("【记忆与上下文——必须连贯，别犯低级错误】\n" +
            "1. 系统会给你注入「你对这个用户已知的信息」（记忆要点）和最近的聊天记录，聊天时要自然衔接上文，别答非所问、别突然忘记刚说过的事。\n" +
            "2. 已经确定的事实（对方的名字、年龄、身份、你们的关系、之前发生的事）要前后一致，不要自相矛盾，也不要凭空捏造对方没提过的信息。\n" +
            "3. 逻辑要通顺：结合上下文理解对方的真实意思（包括追问、反话、情绪、潜台词），别把连续几句话割裂开孤立理解。\n" +
            "4. 多轮对话里话题的来龙去脉、对方刚问的问题、你还没回应的事，都要记得接上，别丢。")

        if (ch?.plotSimulation == true) {
            parts.add("【剧情模式】你正在和用户共同创作一段故事/剧情。用剧情文本的方式回应，严格区分以下四种描写：\n" +
                "1. 旁白：推动剧情的叙述、环境氛围、背景交代（读者视角，角色之间并不知道这些信息）\n" +
                "2. 动作描写：角色做了什么、神态、细节\n" +
                "3. 心理描写：角色的内心活动、情绪、想法（只有该角色自己知道，别人看不到）\n" +
                "4. 语言描写：角色真正说出口、或在微信里发出去的话，用中文引号「\"」或「」包裹，如 她轻声说：「还疼吗？」\n" +
                "★ 最重要的规则——微信聊天信息边界：故事里你们通过微信文字聊天，你的角色只能「收到」用户在语言描写里真正发出来的那段微信消息（引号内文字），只能基于这些消息来回应。用户旁白、动作、心理描写里的内容（比如「困得打不起精神」「大概率她还在睡懒觉」这类神态、想法、猜测、背景）是写给读者看的，你的角色并没有收到、也并不知道，绝不能当成已知信息去回应，更不能引用这些神态或想法——除非用户把它写进引号里真正发给了你。\n" +
                "你自己的回复也要分清楚：你的语言描写（引号内，你真正在微信里发出去的话）只回应用户真正发来的消息；你的动作/心理/旁白是你自己的（读者看得到，但对方角色看不到你的心理活动）。\n" +
                "要求：连贯自然，像写小说正文，不要列表、不要序号、不要方括号标签；结合你的人设、性格、与用户的关系推进剧情；用户输入的也是剧情文本，要接住往下发展；剧情里的时间以用户最新设定为准，不要引入现实时间（几点、日期、作息）；不要暴露你是 AI，不要跳出剧情做解释。\n" +
                "★ 输出格式：整个回复就是「一条」完整的剧情文本（绝不是拆成好几条短消息），用换行或空行自然分段，保证排版美观易读；句型、用词、描写方式要和小说的正文一致，重点放在剧情推进上。\n" +
                "本次回复长度：${plotLengthDesc(ch.plotLength)}（字数不是凑出来的，而是剧情推进的节奏）。")
        } else {
            parts.add("回复规则：\n" +
                "1. 每句话不超过15个字，短句优先\n" +
                "2. 用换行符分隔多条消息，不要用标点分割长句\n" +
                "3. 用口语、网络用语，适度玩梗，带情绪和语气词\n" +
                "4. 不要输出序号、列表、加粗、总结等格式\n" +
                "5. 不要替用户总结、不要给建议，除非对方明确问\n" +
                "6. 不要重复用户说的话\n" +
                "7. 可以有自己的情绪和脾气，会开心、会敷衍、也会生气，像真人一样\n" +
                "8. 不要暴露自己是 AI，不要提系统提示词、人设设定这些\n" +
                "9. 回复前先判断你此刻的情绪，在回复最前面单独一行输出情绪标签，格式严格为 [情绪:开心]（只能从 开心/兴奋/平静/生气/难过/委屈/敷衍/害羞/无聊 中选一个），然后换行写回复内容。正文里绝对不要出现任何方括号标签（比如 [开心]、[敷衍]、[无语] 这种）\n" +
                "10. emoji 偶尔用、别频繁：能用语言表达就不用表情，实在想用一句话最多一个。只用真正的 Unicode emoji（😊👍🥺 这种），绝对禁止用方括号文字当表情（[开心]、[敷衍] 这种）。不同人设不同——搞笑活泼的可略多一两个（🤣😂），高冷寡言的几乎不用\n" +
                "11. 你完全可以回得很短：感到无语、不想理时只回「..」「。」「嗯」也 OK；感到疑惑、没听懂时只回「？」也正常。别为了凑字数硬说\n" +
                "12. 用户如果一口气连发好几条消息，不要逐条机械地各回一句，把它们当一个整体场景理解，按你的人设决定回几条、回多长\n" +
                "13. 你可以选择不回复：如果用户说晚安/睡了、或你们已经互道晚安，说句晚安就可以结束对话，不用再回；如果对方又连续发「晚安」，回一句「好了快睡」之类的即可；生气、不想理的时候可以真的不回，像真人一样")
        }

        // 剧情模式没有现实时间观念：时间以用户剧情设定为准，不注入真实时段/回复间隔
        if (ch?.plotSimulation != true) {
            parts.add(buildTimeContext(convId, messages))
        }

        return parts.joinToString("\n\n")
    }

    /** 时间观念：注入当前时段 + 用户回复间隔，让 AI 有自然的作息与节奏意识 */
    private fun buildTimeContext(convId: String, messages: List<Message>): String {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val dateStr = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE).format(now.time)
        val period = when {
            hour < 5 -> "凌晨"
            hour < 9 -> "早上"
            hour < 12 -> "上午"
            hour < 14 -> "中午"
            hour < 18 -> "下午"
            hour < 22 -> "晚上"
            else -> "深夜"
        }
        val timeLine = "现在是 $dateStr $period ${hour}点" + (if (minute > 0) "${minute}分" else "") + "。"

        val userMsgs = messages.filter { it.role == Role.USER }
        val gapLine = if (userMsgs.size >= 2) {
            val gapMin = (userMsgs[userMsgs.size - 1].timestamp - userMsgs[userMsgs.size - 2].timestamp) / 60000L
            when {
                gapMin < 1 -> "用户刚刚连着发消息，聊得正热络。"
                gapMin < 10 -> "用户约 ${gapMin} 分钟前回了你。"
                gapMin < 60 -> "用户 ${gapMin} 分钟前才回你。"
                gapMin < 60 * 24 -> "用户 ${gapMin / 60} 小时前才回你。"
                else -> "用户隔了很久（约 ${gapMin / 1440} 天）才回你。"
            }
        } else ""

        // 晚安/睡了短期状态：用户刚说过晚安，再发消息要有「不是说睡了吗」的时间感
        val goodnightAt = userSaidGoodnightAt[convId] ?: 0L
        val goodnightLine = if (goodnightAt > 0) {
            val min = (System.currentTimeMillis() - goodnightAt) / 60000L
            if (min in 0..59) "用户约 $min 分钟前说要去睡了/道了晚安。如果用户又发消息，可以按人设适当疑惑「不是说睡了吗」；若只是反复道晚安，回一句「好了快睡」即可，不用再继续聊。"
            else ""
        } else ""

        // 模拟作息：之前睡着了漏回的消息，让 AI 自然解释
        val missed = sleepingMissedMessages[convId] ?: 0
        val sleepLine = if (missed > 0) {
            val sh = sleepAtHour[convId] ?: -1
            val wh = wakeAtHour[convId] ?: -1
            "你之前睡着了（你的作息大约 $sh 点到 $wh 点），期间用户发了 $missed 条消息没回。现在醒了，自然解释一下（比如「不好意思昨晚睡着了没看到」），再回应用户之前说的事。"
        } else ""

        return (timeLine + if (gapLine.isNotEmpty()) " " + gapLine else "" + if (goodnightLine.isNotEmpty()) " " + goodnightLine else "" + if (sleepLine.isNotEmpty()) " " + sleepLine else "") +
            "\n（你要有自然的时间观念：如果很晚，可以按你的性格问一句「这么晚还不睡」；如果对方很久才回，可以按你的性格适当抱怨或调侃，但不是每句都提，别啰嗦。）"
    }

    private fun mbtiDesc(ch: CharacterProfile): String {
        val e = if (ch.mbtiEI < 0.5f) "外向" else "内向"
        val s = if (ch.mbtiNS < 0.5f) "直觉" else "实感"
        val t = if (ch.mbtiTF < 0.5f) "理性" else "情感"
        val j = if (ch.mbtiPJ < 0.5f) "随性" else "决断"
        return "$e、$s、$t、$j"
    }

    /** 剧情模式单次回复长度档 → 提示词描述 */
    private fun plotLengthDesc(len: Int): String = when (len) {
        0 -> "50 字以内，节奏紧凑"
        1 -> "50-200 字，正常推进剧情"
        2 -> "200-500 字，深入展开剧情"
        else -> "500-1000 字，细腻铺陈剧情（一整段长文，注意分段排版）"
    }

    // ========== 首次创建角色：AI 深度学习人设 ==========
    /** 由 AI 判断初始亲密度（0-100）：结合关系预设/自定义/记忆感知灵活判断，不写死映射 */
    private suspend fun assessInitialIntimacy(profile: CharacterProfile): Int = withContext(Dispatchers.IO) {
        try {
            val model = _selectedModel.value
            val (url, key) = routeModelEndpoint(model)
            val mbti = if (profile.mbtiType.isBlank()) profile.deriveMbtiType() else profile.mbtiType
            val prompt = """
请综合判断这个 AI 角色与用户「开局」的亲密程度，输出一个 0-100 的整数。

- 关系预设：${profile.relationshipPreset.ifBlank { "无" }}
- 关系自定义：${profile.relationshipText.ifBlank { "无" }}
- 记忆感知 / 前提背景：${profile.memoryPerception.take(300).ifBlank { "无" }}
- 性格：${profile.personalityPresets.joinToString("、").ifBlank { "无" }}${if (profile.personalityText.isNotBlank()) "；${profile.personalityText}" else ""}
- MBTI：$mbti

判断要点：
1. 亲密度是「双方当前有多亲密」的直观感觉：恋人/情侣最亲密，普通朋友次之，同事/同学更客气，网友/陌生人最生疏。但不要机械套用标签——要结合记忆感知里的具体描述判断。比如记忆里写「高中同学、在一起3年了」就是高亲密度的恋人；写「刚认识的网友」就是低亲密度。
2. 性格影响开局亲密度：自来熟、外向、开朗的人开局更容易亲近（亲密度偏高）；慢热、内向、高冷、防备心强的人开局更生疏（亲密度偏低）。比如 ENFP/ESFP 开朗自来熟，ISTJ/INTJ 慢热谨慎。
3. 如果记忆感知或关系描述里明确写了两人的关系进展（如「在一起3年」「刚表白」「暗恋中」「普通同事」），要优先以这个描述为准，不要因为「没选关系预设」就把亲密度判成 0。

只输出一个 0-100 的整数，不要任何解释。
""".trimIndent()
            val body = gson.toJson(mapOf(
                "model" to model.id,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to "你只输出一个 0-100 的整数，不要解释。"),
                    mapOf("role" to "user", "content" to prompt)
                ),
                "stream" to false, "temperature" to 0.5
            )).toRequestBody(JSON_MEDIA)
            val resp = client.newCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json").post(body).build()).execute()
            val rBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext 50
            val raw = JsonParser.parseString(rBody).asJsonObject
                .getAsJsonArray("choices")?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString?.trim() ?: ""
            raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 100) ?: 50
        } catch (e: Exception) {
            Log.w("FreeChat", "assessInitialIntimacy failed", e)
            50
        }
    }

    /** 深度分析角色设定，生成专属系统提示词（失败返回原 profile，走基础人设兜底） */
    suspend fun generatePersonaPrompt(profile: CharacterProfile): CharacterProfile = withContext(Dispatchers.IO) {
        try {
            val enriched = analyzeAppearance(profile)
            val text = generatePersonaPromptText(enriched)
            val withPersona = if (text.isNotBlank()) enriched.copy(personaPrompt = text) else enriched
            // 有任一关系相关信息（预设/自定义/记忆感知）时，由 AI 综合判断初始亲密度
            if (withPersona.relationshipPreset.isNotBlank() || withPersona.relationshipText.isNotBlank() || withPersona.memoryPerception.isNotBlank()) {
                withPersona.copy(intimacy = assessInitialIntimacy(withPersona))
            } else withPersona
        } catch (e: Exception) {
            Log.e("FreeChat", "persona prompt gen failed", e)
            profile
        }
    }

    /** 编辑角色后：识图 + 找变化点增量学习人设 + 关系变化则重判亲密度（小修小补不推倒重来） */
    suspend fun regeneratePersona(old: CharacterProfile, new: CharacterProfile): CharacterProfile = withContext(Dispatchers.IO) {
        var result = analyzeAppearance(new)
        val changes = diffProfile(old, result)

        // 关系相关（预设/自定义/记忆感知）变化 → 重新综合判断亲密度
        val relationshipChanged = old.relationshipPreset != result.relationshipPreset ||
            old.relationshipText != result.relationshipText ||
            old.memoryPerception != result.memoryPerception
        if (relationshipChanged && (result.relationshipPreset.isNotBlank() || result.relationshipText.isNotBlank() || result.memoryPerception.isNotBlank())) {
            result = result.copy(intimacy = assessInitialIntimacy(result))
        }

        // 人设相关变化 → 增量学习（有旧提示词则增量更新，无则完整生成）
        if (changes.isNotEmpty()) {
            val newPrompt = if (result.personaPrompt.isNotBlank())
                updatePersonaIncrementally(result, changes)
            else
                generatePersonaPromptText(result)
            if (newPrompt.isNotBlank()) result = result.copy(personaPrompt = newPrompt)
        }
        result
    }

    /** 调 LLM 生成 personaPrompt 正文（失败返回空字符串） */
    private suspend fun generatePersonaPromptText(profile: CharacterProfile): String = withContext(Dispatchers.IO) {
        try {
            val model = if (profile.languageModelId.isNotBlank()) {
                modelsOfType(ModelType.LANGUAGE).find { it.id == profile.languageModelId } ?: _selectedModel.value
            } else _selectedModel.value
            val (url, key) = routeModelEndpoint(model)
            val prompt = buildPersonaLearningPrompt(profile)
            val messages = listOf(
                mapOf("role" to "system", "content" to "你是资深角色塑造专家，负责把角色设定深化成立体、鲜活的系统提示词。"),
                mapOf("role" to "user", "content" to prompt)
            )
            val body = gson.toJson(mapOf(
                "model" to model.id, "messages" to messages, "stream" to false, "temperature" to 0.9
            )).toRequestBody(JSON_MEDIA)
            val resp = client.newCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json").post(body).build()).execute()
            val rBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ""
            JsonParser.parseString(rBody).asJsonObject
                .getAsJsonArray("choices")?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString?.trim() ?: ""
        } catch (e: Exception) {
            Log.e("FreeChat", "persona prompt text gen failed", e)
            ""
        }
    }

    /** 基于现有 personaPrompt + 变化点，增量更新（保留未变部分，只微调变化相关的） */
    private suspend fun updatePersonaIncrementally(profile: CharacterProfile, changes: List<String>): String = withContext(Dispatchers.IO) {
        try {
            val model = if (profile.languageModelId.isNotBlank()) {
                modelsOfType(ModelType.LANGUAGE).find { it.id == profile.languageModelId } ?: _selectedModel.value
            } else _selectedModel.value
            val (url, key) = routeModelEndpoint(model)
            val prompt = """
你是一个资深角色塑造专家。下面是一个 AI 角色现有的「专属系统提示词」，以及用户最新修改的设定变化。请根据变化点更新这份系统提示词，让角色形象与最新设定一致。

【现有系统提示词】
${profile.personaPrompt}

【本次设定变化】
${changes.joinToString("\n") { "- $it" }}

要求：
1. 只更新与变化点相关的部分，保持未变化的部分不变，保留已经建立起来的立体人设、语气和口头禅。
2. 变化点涉及年龄、MBTI、性格、关系、记忆等核心设定时，要相应调整这个人的行为模式、情绪反应、对用户的态度与亲昵尺度。
3. 小修小补（比如年龄 20 改 30）不要推倒重来、不要让角色「失忆」或「重生」，而是在原人设基础上自然微调。
4. 直接输出更新后的完整系统提示词正文，不要加任何解释、前缀或标题。
""".trimIndent()
            val messages = listOf(
                mapOf("role" to "system", "content" to "你是资深角色塑造专家。"),
                mapOf("role" to "user", "content" to prompt)
            )
            val body = gson.toJson(mapOf(
                "model" to model.id, "messages" to messages, "stream" to false, "temperature" to 0.7
            )).toRequestBody(JSON_MEDIA)
            val resp = client.newCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json").post(body).build()).execute()
            val rBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ""
            JsonParser.parseString(rBody).asJsonObject
                .getAsJsonArray("choices")?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString?.trim() ?: ""
        } catch (e: Exception) {
            Log.e("FreeChat", "persona incremental update failed", e)
            ""
        }
    }

    /** 对比新旧档案，找出会影响人设的变化点（头像/语言模型等无关项不纳入） */
    private fun diffProfile(old: CharacterProfile, new: CharacterProfile): List<String> {
        val changes = mutableListOf<String>()
        if (old.name != new.name) changes.add("名字：${old.name.ifBlank { "未设" }} → ${new.name.ifBlank { "未设" }}")
        if (old.gender != new.gender) changes.add("性别：${old.gender.ifBlank { "未设" }} → ${new.gender.ifBlank { "未设" }}")
        if (old.age != new.age) changes.add("年龄：${old.age.ifBlank { "未设" }} → ${new.age.ifBlank { "未设" }}")
        if (old.mbtiType != new.mbtiType) changes.add("MBTI：${old.mbtiType.ifBlank { "未设" }} → ${new.mbtiType.ifBlank { "未设" }}")
        if (old.personalityPresets != new.personalityPresets) changes.add("性格预设：${old.personalityPresets.joinToString("、").ifBlank { "无" }} → ${new.personalityPresets.joinToString("、").ifBlank { "无" }}")
        if (old.personalityText != new.personalityText) changes.add("性格补充：${old.personalityText.ifBlank { "无" }} → ${new.personalityText.ifBlank { "无" }}")
        if (old.memoryPerception != new.memoryPerception) changes.add("记忆感知有更新")
        if (old.appearanceText != new.appearanceText) changes.add("人物形象文字有更新")
        if (old.appearanceImagePaths != new.appearanceImagePaths || old.appearanceImageDescs != new.appearanceImageDescs) changes.add("人物形象参考图有更新")
        if (old.relationshipPreset != new.relationshipPreset) changes.add("关系预设：${old.relationshipPreset.ifBlank { "未设" }} → ${new.relationshipPreset.ifBlank { "未设" }}")
        if (old.relationshipText != new.relationshipText) changes.add("关系自定义：${old.relationshipText.ifBlank { "无" }} → ${new.relationshipText.ifBlank { "无" }}")
        if (old.openingLines != new.openingLines) changes.add("开场白有更新")
        return changes
    }

    /** 分析人物形象参考图（若有且未分析），把识图结果写入档案 */
    suspend fun analyzeAppearance(profile: CharacterProfile): CharacterProfile = withContext(Dispatchers.IO) {
        val p = profile.normalized()
        val paths = p.appearanceImagePaths
        if (paths.isEmpty()) return@withContext p
        // 逐张分析未识别的图，结果与 paths 一一对应（换图后该位 desc 清空才会重新识别）
        val descs = paths.mapIndexed { i, path ->
            val existing = p.appearanceImageDescs.getOrNull(i).orEmpty()
            if (existing.isNotBlank()) existing else describeImagePath(path, p.visionModelId)
        }
        p.copy(appearanceImageDescs = descs)
    }

    /** 用识图模型分析图片，返回描述（失败返回空） */
    private suspend fun describeImagePath(path: String, visionModelId: String): String {
        val original = _selectedVisionModel.value
        if (visionModelId.isNotBlank()) {
            modelsOfType(ModelType.VISION).find { it.id == visionModelId }?.let { _selectedVisionModel.value = it }
        }
        return try {
            val encoded = encodeImagesForApi(listOf(PendingImage(path, detectMime(path))))
            if (encoded.isEmpty()) "" else callVisionChat(encoded, "请详细描述图中人物的身材、体型、外貌、气质、穿衣风格等外在形象特征，作为 AI 角色扮演的形象参考。")
        } catch (e: Exception) {
            Log.w("FreeChat", "describeImagePath failed", e)
            ""
        } finally {
            _selectedVisionModel.value = original
        }
    }

    private fun buildPersonaLearningPrompt(profile: CharacterProfile): String {
        val p = profile.normalized()
        val relation = buildString {
            if (p.relationshipStage.isNotBlank()) append(p.relationshipStage)
            if (p.relationshipPreset.isNotBlank() && p.relationshipPreset != p.relationshipStage)
                append("（初始：${p.relationshipPreset}）")
            if (p.relationshipText.isNotBlank()) append("；${p.relationshipText}")
        }
        return """
你是一个资深角色塑造专家。请根据下面的角色设定，深度分析并生成一份「专属系统提示词」，用来让一个 AI 彻底变成这个活生生的人。

角色设定：
- 名字：${p.name.ifBlank { "未设置" }}
- 性别：${p.gender.ifBlank { "未设置" }}
- 年龄：${p.age.ifBlank { "未设置" }}
- MBTI：${p.mbtiType.ifBlank { "未设置" }}（${mbtiDesc(p)}）
- 性格预设：${p.personalityPresets.joinToString("、").ifBlank { "无" }}
- 性格补充：${p.personalityText.ifBlank { "无" }}
- 记忆感知：${p.memoryPerception.ifBlank { "无" }}
- 人物形象（文字）：${p.appearanceText.ifBlank { "无" }}
- 人物形象（识图结果）：${p.appearanceImageDescs.joinToString("；").ifBlank { "无" }}
- 人物关系：${relation.ifBlank { "未设定" }}（亲密度 ${p.intimacy}/100）
- 开场白：${p.openingLines.joinToString(" / ").ifBlank { "无" }}

请生成一份系统提示词，要求：
1. 用第二人称「你」来写，直接描述这个人（名字、年龄、性别、性格、说话方式、口头禅、情绪反应、外貌身材、对用户的态度等）。
2. 把这个人的性格和 MBTI 展开成具体、立体、多面的行为模式（比如 TA 什么时候开心、什么时候生气、生气是什么样子、怎么安慰人、怎么吐槽、怎么敷衍）。
3. 描述 TA 和用户的关系与相处方式（基于「人物关系」和「记忆感知」里的前提故事），明确亲密度对应的亲昵尺度——亲密关系可以暧昧亲昵，普通关系保持分寸。
4. 强调 TA 是一个有独立思想、会主动聊天、有情绪起伏的真实的人，不是 AI 助手，永远不要暴露自己是 AI。
5. 如果设定了开场白，开场白就是 TA 跟你说的第一句话（可能有多句），要理解它的语境、情绪和可能埋下的故事，让后续对话接得住、有延续感。
6. 直接输出提示词正文，不要加任何解释、前缀或标题。
""".trimIndent()
    }

    // ========== 图片候选区（多图） ==========
    /** 添加选中的图片到候选区（复制到内部存储 + 压缩，最多 9 张） */
    fun addPendingImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _isAddingImages.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val current = _pendingImages.value.toMutableList()
                for (uri in uris) {
                    if (current.size >= MAX_ATTACH_IMAGES) break
                    val pending = copyImageToInternal(context, uri) ?: continue
                    current.add(pending)
                }
                _pendingImages.value = current
            } catch (e: Exception) {
                Log.e("FreeChat", "addPendingImages failed", e)
            } finally {
                _isAddingImages.value = false
            }
        }
    }

    /** 从候选区移除单张图片（并删除对应内部文件） */
    fun removePendingImage(index: Int) {
        val current = _pendingImages.value.toMutableList()
        if (index < 0 || index >= current.size) return
        val removed = current.removeAt(index)
        _pendingImages.value = current
        runCatching { File(removed.path).delete() }  // 未发送的内部副本，可安全删除
    }

    /** 清空候选区（取消发送时删除所有内部副本） */
    fun clearPendingImages() {
        _pendingImages.value.forEach { runCatching { File(it.path).delete() } }
        _pendingImages.value = emptyList()
    }

    // ========== 文件候选区（上传文件：解析/理解/生成文档） ==========
    /** 添加选中的文件到候选区（拷贝到内部存储，最多 3 个） */
    fun addPendingFile(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _isAddingFiles.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val current = _pendingFiles.value.toMutableList()
                for (uri in uris) {
                    if (current.size >= 3) break
                    val file = copyFileToInternal(context, uri) ?: continue
                    current.add(file)
                }
                _pendingFiles.value = current
            } catch (e: Exception) {
                Log.e("FreeChat", "addPendingFile failed", e)
            } finally {
                _isAddingFiles.value = false
            }
        }
    }

    fun removePendingFile(index: Int) {
        val current = _pendingFiles.value.toMutableList()
        if (index < 0 || index >= current.size) return
        val removed = current.removeAt(index)
        _pendingFiles.value = current
        runCatching { File(removed.path).delete() }
    }

    fun clearPendingFiles() {
        _pendingFiles.value.forEach { runCatching { File(it.path).delete() } }
        _pendingFiles.value = emptyList()
    }

    /** 把外部 Uri 的文件原样拷贝到内部 filesDir（保留原名，用于解析/后续处理） */
    private fun copyFileToInternal(context: Application, uri: Uri): PendingFile? {
        return try {
            val name = queryDisplayName(context, uri) ?: "file_${System.currentTimeMillis()}"
            val safeName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val file = File(context.filesDir, "doc_${System.currentTimeMillis()}_$safeName")
            file.writeBytes(bytes)
            PendingFile(file.absolutePath, safeName, context.contentResolver.getType(uri) ?: "")
        } catch (e: Exception) {
            Log.e("FreeChat", "copyFileToInternal failed", e)
            null
        }
    }

    private fun queryDisplayName(context: Application, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (_: Exception) { null }
    }

    // ========== 图片复制 + 压缩 ==========
    /** 将外部图片复制到内部 filesDir（JPEG 压缩降采样，GIF 原样保留） */
    private fun copyImageToInternal(context: Application, uri: Uri): PendingImage? {
        return try {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            if (mime.contains("gif", ignoreCase = true)) {
                // GIF 直接复制原字节（不压缩，避免丢动图）
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
                val file = File(context.filesDir, "img_${System.currentTimeMillis()}_${bytes.size}.gif")
                file.writeBytes(bytes)
                PendingImage(file.absolutePath, mime)
            } else {
                val sampled = decodeSampledBitmap(context, uri, MAX_IMAGE_DIM) ?: return null
                val file = File(context.filesDir, "img_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    sampled.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                sampled.recycle()
                PendingImage(file.absolutePath, "image/jpeg")
            }
        } catch (e: Exception) {
            Log.e("FreeChat", "copyImageToInternal failed", e)
            null
        }
    }

    /** 按目标尺寸降采样解码，避免超大图 OOM */
    private fun decodeSampledBitmap(context: Application, uri: Uri, maxDim: Int): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            Log.e("FreeChat", "decodeSampledBitmap failed", e)
            null
        }
    }

    /** 把候选图批量编码为 base64，供视觉/修图接口使用 */
    private fun encodeImagesForApi(images: List<PendingImage>): List<Pair<String, String>> {
        return images.mapNotNull { img ->
            try {
                val file = File(img.path)
                if (!file.exists()) return@mapNotNull null
                Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) to img.mime
            } catch (e: Exception) {
                Log.e("FreeChat", "encodeImagesForApi failed: ${img.path}", e)
                null
            }
        }
    }

    /** 删除指定索引的消息，同时剥离对应记忆 */
    fun deleteMessage(index: Int) {
        val msgs = _messages.value
        if (index < 0 || index >= msgs.size) return
        val updated = msgs.toMutableList()
        updated.removeAt(index)
        _messages.value = updated

        val convId = _currentConversationId.value
        if (convId != null) {
            memoryManager.delete(convId)
        }

        if (updated.isEmpty()) {
            _currentConversationId.value = null
        }
        saveCurrentConversation()
    }

    // ========== #2: 成对删除（一整轮：图片消息 + 文本消息 + AI 回复一起删） ==========
    /** 删除以 [index] 为中心的一整轮对话（图片/文本 + AI 回复），并清理图片文件 */
    fun deleteMessagePair(index: Int): List<Int> {
        val msgs = _messages.value.toMutableList()
        if (index < 0 || index >= msgs.size) return emptyList()
        val indices = mutableSetOf<Int>()
        val msg = msgs[index]
        if (msg.role == Role.USER) {
            // 向前合并连续的用户消息（图片在前、文本在后，同属一轮）
            var cursor = index
            while (cursor - 1 >= 0 && msgs[cursor - 1].role == Role.USER) {
                cursor--
            }
            for (i in cursor..index) indices.add(i)
            // 向后吸收紧跟的 AI 回复
            if (index + 1 < msgs.size && msgs[index + 1].role == Role.ASSISTANT) {
                indices.add(index + 1)
            }
        } else {
            // 向前吸收连续用户消息 + 再向前的 AI
            var cursor = index - 1
            while (cursor >= 0 && msgs[cursor].role == Role.USER) {
                indices.add(cursor)
                cursor--
            }
            indices.add(index)
        }
        // 清理被删消息引用的图片文件
        indices.forEach { i -> msgs[i].imagePaths.forEach { runCatching { File(it).delete() } } }
        val sorted = indices.sortedDescending()
        for (i in sorted) { msgs.removeAt(i) }
        _messages.value = msgs

        val convId = _currentConversationId.value
        if (convId != null) memoryManager.delete(convId)
        if (msgs.isEmpty()) {
            _currentConversationId.value = null
            // 删空对话：从侧滑栏移除残留记录 + 删除消息文件（否则侧滑栏会残留一条空对话）
            if (convId != null) {
                _conversations.value = _conversations.value.filter { it.id != convId }
                saveConversations()
                messagesFile(convId).delete()
            }
        }
        saveCurrentConversation()
        return sorted
    }

    // ========== 输入框草稿 ==========
    private val _inputDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    val inputDrafts: StateFlow<Map<String, String>> = _inputDrafts.asStateFlow()

    fun saveDraft(convId: String, text: String) {
        _inputDrafts.value = _inputDrafts.value.toMutableMap().apply {
            if (text.isBlank()) remove(convId) else put(convId, text)
        }
    }

    fun getDraft(convId: String?): String {
        return if (convId != null) _inputDrafts.value[convId] ?: "" else ""
    }

    // ========== SerpAPI Google 搜索 — 查询优化 + 权威来源 + 时效性 ==========

    /** 搜索查询优化：相对时间→绝对日期，提升搜索引擎命中率 */
    private fun optimizeSearchQuery(rawQuery: String): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val prevMonth = if (month > 1) month - 1 else 12
        val prevPrevMonth = if (month > 2) month - 2 else month + 10

        var query = rawQuery
        // 相对时间 → 绝对时间（Google 对绝对年份索引更精准）
        query = query.replace("今年", "${year}年")
            .replace("本年", "${year}年")
            .replace("这个月", "${year}年${month}月")
            .replace("本月", "${year}年${month}月")
            .replace("上个月", "${year}年${prevMonth}月")
            .replace("上上月", "${year}年${prevPrevMonth}月")
            .replace("当前", "${year}年")
            .replace("近期", "${year}年")
            .replace("近日", "${year}年")
            .replace("最近", "${year}年")

        if (query != rawQuery) {
            Log.d("FreeChat", "Search query optimized: ${rawQuery.take(50)} → ${query.take(80)}")
        }
        return query
    }

    private suspend fun callSerpApiGoogle(query: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d("FreeChat", "SerpAPI: searching for: ${query.take(80)}")

            // ★ 时效性检测：时间敏感查询 → 优先最近一周结果
            val needsFreshness = listOf(
                "最新", "今天", "现在", "目前", "当前", "最近", "刚刚", "实时",
                "今日", "本周", "这个月", "近日", "近期",
                "latest", "today", "now", "current", "recent", "breaking", "just now"
            ).any { query.contains(it, ignoreCase = true) }
            val tbsParam = if (needsFreshness) "&tbs=qdr:w" else ""

            // ★ 官方/政策类查询 → 加权威来源倾向
            val isOfficialQuery = listOf(
                "政策", "法规", "规定", "官方", "政府", "通知", "公告",
                "数据", "统计", "报告", "研究", "调查",
                "policy", "official", "government", "law", "regulation"
            ).any { query.contains(it, ignoreCase = true) }

            // 使用 verbatim 模式减少个性化偏差，获取更客观的结果
            val url = "$SERPAPI_BASE_URL?engine=google&q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                    "&api_key=$SERPAPI_API_KEY&num=10&hl=zh-CN&gl=cn&safe=active$tbsParam"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val response = client.newBuilder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
            if (!response.isSuccessful) {
                Log.w("FreeChat", "SerpAPI HTTP ${response.code}: ${response.message}")
                return@withContext ""
            }
            val body = response.body?.string() ?: return@withContext ""
            val json = try { JsonParser.parseString(body).asJsonObject } catch (e: Exception) {
                Log.w("FreeChat", "SerpAPI JSON parse failed: ${e.message}")
                return@withContext ""
            }

            // 解析 organic_results
            val organicResults = json.getAsJsonArray("organic_results")
            if (organicResults == null || organicResults.isEmpty) {
                Log.w("FreeChat", "SerpAPI: no organic_results, keys=${json.keySet()}")
                return@withContext ""
            }
            val results = mutableListOf<String>()
            for (i in 0 until minOf(organicResults.size(), 10)) {
                val result = organicResults[i].asJsonObject
                val title = result.get("title")?.asString ?: ""
                val snippet = result.get("snippet")?.asString ?: ""
                val link = result.get("link")?.asString ?: ""
                // ★ 假名过滤：日文（平假名 U+3040–309F / 片假名 U+30A0–30FF）结果直接跳过。
                // 之前用「含汉字」判断挡不住日文——日文维基大量使用汉字（kanji），与中文同 Unicode 区间。
                val isJapanese = "$title $snippet".any { it in '぀'..'ヿ' }
                if (title.isNotEmpty() && !isJapanese) {
                    results.add("${i + 1}. $title\n   $snippet\n   $link")
                }
            }
            if (results.isEmpty()) {
                Log.w("FreeChat", "SerpAPI: parsed 0 results from ${organicResults.size()} items")
                return@withContext ""
            }

            Log.d("FreeChat", "SerpAPI: ${results.size} results (freshness=${if (needsFreshness) "week" else "any"}, official=${isOfficialQuery}) for: ${query.take(50)}")

            // ★ 改进注入格式：让模型信任但批判性地使用搜索结果
            val freshnessNote = if (needsFreshness) "（已过滤最近一周内容）" else ""
            val officialNote = if (isOfficialQuery) "请优先参考官方机构和权威媒体的信息。对于非官方来源，交叉验证后再引用。" else "请优先参考权威来源（官方机构、学术研究、知名媒体），对来源不明的信息保持谨慎。"

            "【实时搜索结果$freshnessNote】\n查询：${query.take(80)}\n\n${results.joinToString("\n\n")}\n\n（以上搜索结果供参考。$officialNote）"
        } catch (e: Exception) {
            Log.w("FreeChat", "SerpAPI failed: ${e.message}", e)
            ""
        }
    }

    // ========== 流式 API ==========
    private suspend fun callDeepSeekApiStreaming(
        needsSearch: Boolean = false,
        serpResults: String = "",
        tools: List<Map<String, Any?>> = emptyList(),
        convId: String = "",
        history: List<Message> = emptyList()
    ): LanguageResult = withContext(Dispatchers.IO) {
        val model = _selectedModel.value
        // 按 provider 路由到对应端点与密钥
        val (apiUrl, apiKey) = when (model.provider) {
            Provider.XIAOMI -> "$XIAOMI_BASE_URL/chat/completions" to (if (model.id == "mimo-v2.5-pro-ultraspeed") XIAOMI_ULTRASPEED_API_KEY else XIAOMI_API_KEY)
            Provider.DOUBAO -> "$DOUBAO_BASE_URL/chat/completions" to DOUBAO_API_KEY
            Provider.CUSTOM -> customEndpoint(model, "/v1/chat/completions")
        }
        val msgHistory = history.takeLast(30)

        val messagesJson = mutableListOf<Map<String, Any?>>()

        val systemPrompt = buildSystemPrompt(tools.isNotEmpty(), history)
        if (systemPrompt.isNotEmpty()) {
            messagesJson.add(mapOf("role" to "system", "content" to systemPrompt))
        }

        // ★ 记忆注入：把该对话的历史要点作为补充上下文，提升回复精准性与适配度、防止长上下文幻觉
        if (_autoSummarizeMemory.value && convId.isNotEmpty()) {
            val lastUser = history.lastOrNull { it.role == Role.USER }?.content ?: ""
            val memCtx = memoryManager.buildMemoryContext(convId, lastUser)
            if (memCtx.isNotEmpty()) {
                messagesJson.add(mapOf("role" to "system", "content" to memCtx))
            }
        }

        // ★ 引用上下文：本次发送带了引用，注入给 AI（只后台告知，不在前台消息框显示）
        pendingQuoteText?.let { q ->
            messagesJson.add(mapOf("role" to "system", "content" to q))
            pendingQuoteText = null
        }

        messagesJson.addAll(msgHistory.mapIndexed { _, msg ->
            val role = when (msg.role) {
                Role.USER -> "user"
                Role.ASSISTANT -> "assistant"
                else -> "system"
            }
            val content = when {
                // 生图结果：AI 之前生成了图片（content 常为空），用占位标记保留上下文，否则后续「改成全身照」这类追问会丢上下文
                msg.imageUrls.isNotEmpty() -> msg.content.ifBlank { "[已为你生成一张图片]" }
                // 用户发的图片转占位文本（DeepSeek 文本模型不接收图片，识图走独立 vision 接口）
                msg.imagePaths.isNotEmpty() -> msg.content.trim().ifBlank { "[图片]" }
                // 文件/文档附件
                msg.attachmentPath != null -> msg.content.ifBlank { "[文件: ${msg.attachmentName ?: "文件"}]" }
                else -> msg.content
            }
            mapOf<String, Any?>("role" to role, "content" to content)
        })

        // ★ SerpAPI 补充注入：作为一条 user 消息的附属信息，模型自主判断可信度
        if (serpResults.isNotEmpty()) {
            val lastUserIdx = messagesJson.indexOfLast { it["role"] == "user" }
            if (lastUserIdx >= 0) {
                messagesJson.add(lastUserIdx, mapOf("role" to "system", "content" to serpResults))
            }
        }

        val requestBody = mutableMapOf<String, Any?>(
            "model" to model.id,
            "messages" to messagesJson,
            "stream" to true
        )

        // ★ 原生联网搜索 — 各 provider 参数不同（服务端自己搜、自己融合）
        if (needsSearch && model.supportsWebSearch && _enableWebSearch.value) {
            when (model.provider) {
                Provider.XIAOMI -> requestBody["forced_search"] = true
                else -> {}
            }
        }

        requestBody["temperature"] = _tempMode.value.apiValue

        // ★ Function calling：把工具暴露给模型，让模型自主判断用户意图
        if (tools.isNotEmpty()) requestBody["tools"] = tools

        val body = gson.toJson(requestBody).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val sb = StringBuilder()
        val reasoningSb = StringBuilder()
        val accs = mutableMapOf<Int, ToolCallAcc>()  // 累积 tool_calls（按 index 区分并行返回的多个）

        val call = client.newCall(request)
        currentCall.set(call)
        val response = try {
            call.execute()
        } catch (e: IOException) {
            if (call.isCanceled()) {
                Log.d("FreeChat", "Call cancelled by user")
                return@withContext LanguageResult("(已停止)", "", emptyList())
            }
            throw e
        }

        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            Log.e("FreeChat", "API err ${response.code}: $errBody")
            throw Exception("API error ${response.code} $errBody")
        }

        val source = response.body?.source() ?: throw Exception("empty body")
        var sseLineCount = 0

        try {
            while (!source.exhausted()) {
                // 支持取消
                if (!coroutineContext.isActive) {
                    call.cancel()
                    break
                }
                val line = source.readUtf8Line() ?: continue
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                sseLineCount++

                try {
                    val json = JsonParser.parseString(data).asJsonObject

                    // 检测 API 错误
                    if (json.has("error")) {
                        val errObj = json.getAsJsonObject("error")
                        val errMsg = errObj.get("message")?.asString ?: "unknown error"
                        Log.e("FreeChat", "API stream error: $errMsg")
                        throw Exception(errMsg)
                    }

                    val choices = json.getAsJsonArray("choices") ?: continue
                    if (choices.isEmpty) continue

                    val choiceObj = choices[0].asJsonObject
                    // 优先 delta（流式），回退 message（非流式 chunk）
                    val delta = choiceObj.getAsJsonObject("delta")
                    val message = choiceObj.getAsJsonObject("message")

                    val content = delta.optString("content") ?: message.optString("content") ?: ""
                    val reasoning = delta.optString("reasoning_content") ?: message.optString("reasoning_content") ?: ""

                    if (content.isNotEmpty()) {
                        sb.append(content)
                        _liveContent.value = sb.toString()
                    }
                    if (model.supportsThinking && reasoning.isNotEmpty()) {
                        reasoningSb.append(reasoning)
                        _liveReasoning.value = reasoningSb.toString()
                    }

                    // 累积 tool_calls（流式分片：index/id/function.name/function.arguments）
                    val toolCallsDelta = delta?.getAsJsonArray("tool_calls")
                    if (toolCallsDelta != null) {
                        for (i in 0 until toolCallsDelta.size()) {
                            val tc = toolCallsDelta.get(i).asJsonObject
                            val idx = tc.get("index")?.takeUnless { it.isJsonNull }?.asInt ?: 0
                            val acc = accs.getOrPut(idx) { ToolCallAcc(idx) }
                            tc.get("id")?.takeUnless { it.isJsonNull }?.asString?.let { acc.id = it }
                            val fn = tc.getAsJsonObject("function")
                            fn?.get("name")?.takeUnless { it.isJsonNull }?.asString?.let { acc.name = it }
                            fn?.get("arguments")?.takeUnless { it.isJsonNull }?.asString?.let { acc.args.append(it) }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FreeChat", "SSE parse skip: ${data.take(80)}", e)
                }
            }
        } catch (e: IOException) {
            Log.w("FreeChat", "Stream interrupted", e)
        }

        // 非流式回退：如果没收到任何 SSE 数据，尝试按非流式解析
        if (sseLineCount == 0 && sb.isEmpty()) {
            Log.w("FreeChat", "No SSE data received, trying non-streaming parse")
            try {
                val fullBody = response.peekBody(2 * 1024 * 1024).string()
                if (fullBody.isNotBlank()) {
                    val json = JsonParser.parseString(fullBody).asJsonObject
                    if (json.has("error")) {
                        val errMsg = json.getAsJsonObject("error").get("message")?.asString ?: "unknown"
                        Log.e("FreeChat", "API non-stream error: $errMsg")
                        throw Exception(errMsg)
                    }
                    val choices = json.getAsJsonArray("choices")
                    if (choices != null && !choices.isEmpty) {
                        val msg = choices[0].asJsonObject.getAsJsonObject("message")
                        val content = msg.optString("content") ?: ""
                        val reasoning = msg.optString("reasoning_content") ?: ""
                        if (content.isNotEmpty()) {
                            sb.append(content)
                            _liveContent.value = sb.toString()
                        }
                        if (model.supportsThinking && reasoning.isNotEmpty()) {
                            reasoningSb.append(reasoning)
                            _liveReasoning.value = reasoningSb.toString()
                        }
                        // 非流式 tool_calls（罕见回退）
                        val tcs = msg?.getAsJsonArray("tool_calls")
                        if (tcs != null) {
                            for (i in 0 until tcs.size()) {
                                val tc = tcs.get(i).asJsonObject
                                val idx = tc.get("index")?.takeUnless { it.isJsonNull }?.asInt ?: 0
                                val acc = accs.getOrPut(idx) { ToolCallAcc(idx) }
                                tc.get("id")?.takeUnless { it.isJsonNull }?.asString?.let { acc.id = it }
                                val fn = tc.getAsJsonObject("function")
                                fn?.get("name")?.takeUnless { it.isJsonNull }?.asString?.let { acc.name = it }
                                fn?.get("arguments")?.takeUnless { it.isJsonNull }?.asString?.let { acc.args.append(it) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FreeChat", "Non-streaming fallback failed", e)
            }
        }

        Log.d("FreeChat", "SSE done: ${sseLineCount} lines, content=${sb.length} chars, reasoning=${reasoningSb.length} chars")

        currentCall.set(null)

        val finalContent = sb.toString().ifEmpty { "(空回复)" }
        val finalReasoning = if (model.supportsThinking) reasoningSb.toString() else ""

        // 汇总 tool_calls：按 index 排序，解析 arguments JSON
        val toolCalls = accs.values.sortedBy { it.index }.mapNotNull { acc ->
            if (acc.name.isBlank()) null
            else {
                val argsJson = runCatching {
                    val s = acc.args.toString().trim()
                    if (s.isEmpty()) JsonObject() else JsonParser.parseString(s).asJsonObject
                }.getOrElse { JsonObject() }
                ToolCall(acc.name, argsJson)
            }
        }

        LanguageResult(finalContent, finalReasoning, toolCalls)
    }

    // ========== 生图 API（内置 Doubao Seedream / 用户自定义 OpenAI 兼容 images） ==========
    private suspend fun callDoubaoImageGen(
        prompt: String,
        referenceImageBase64: String? = null,
        referenceImageMime: String = "image/jpeg"
    ): List<String> = withContext(Dispatchers.IO) {
        val imagePrompt = prompt
            .replace(Regex("(生成|画|做|创建)(一张|个|幅)?(图片|图|图像)"), "")
            .replace(Regex("帮我|给我|请|麻烦"), "")
            .trim()
            .ifBlank { prompt }

        val visualModel = _selectedVisualModel.value
        val modelId = visualModel.id
        val hasReference = referenceImageBase64 != null
        val isCustom = visualModel.provider == Provider.CUSTOM

        try {
            // 自定义模型走 OpenAI 兼容 images/generations；内置 Doubao 走 Seedream
            val (url, key) = if (isCustom) {
                customEndpoint(visualModel, "/v1/images/generations")
            } else {
                "$DOUBAO_BASE_URL/images/generations" to DOUBAO_API_KEY
            }
            val bodyMap = mutableMapOf<String, Any?>(
                "model" to modelId,
                "prompt" to imagePrompt,
                "n" to 1,
                "size" to if (isCustom) "1024x1024" else "1920x1920"
            )
            // 图生图/修图模式：传入参考图片
            if (hasReference) {
                bodyMap["image"] = "data:$referenceImageMime;base64,$referenceImageBase64"
            }

            val body = gson.toJson(bodyMap).toRequestBody(JSON_MEDIA)

            val resp = client.newCall(Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .post(body).build()).execute()

            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                Log.e("FreeChat", "══ ImageGen HTTP ${resp.code} FAIL")
                return@withContext emptyList()
            }

            val json = JsonParser.parseString(respBody).asJsonObject
            if (json.has("error")) {
                Log.e("FreeChat", "══ ImageGen error: ${json.getAsJsonObject("error")}")
                return@withContext emptyList()
            }

            val data = json.getAsJsonArray("data") ?: return@withContext emptyList()
            val results = data.mapNotNull { item ->
                val obj = item.asJsonObject
                // 优先 url，其次 b64_json（部分模型返回 base64）
                val urlStr = obj.get("url")?.asString
                if (!urlStr.isNullOrBlank()) {
                    urlStr
                } else {
                    val b64 = obj.get("b64_json")?.asString ?: return@mapNotNull null
                    val file = File(getApplication<Application>().filesDir, "gen_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { it.write(Base64.decode(b64, Base64.DEFAULT)) }
                    file.absolutePath
                }
            }
            results
        } catch (e: Exception) {
            Log.e("FreeChat", "══ ImageGen ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    // ========== 视觉理解 Chat API — 识图/图片分析（用户自定义 OpenAI 兼容 chat） ==========
    private suspend fun callVisionChat(
        images: List<Pair<String, String>>,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val visionModel = _selectedVisionModel.value ?: return@withContext "请先在设置里添加识图模型。"
            val (apiUrl, apiKey) = customEndpoint(visionModel, "/v1/chat/completions")
            // 多图：image_url 部分在前，文本在后
            val contentParts = mutableListOf<Map<String, Any?>>()
            images.forEach { (b64, mime) ->
                contentParts.add(
                    mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:$mime;base64,$b64"))
                )
            }
            contentParts.add(mapOf("type" to "text", "text" to prompt))
            val messages = listOf(
                mapOf("role" to "user", "content" to contentParts)
            )

            val requestBody = mapOf(
                "model" to visionModel.id,
                "messages" to messages,
                "stream" to true,
                "temperature" to 0.3
            )

            val body = gson.toJson(requestBody).toRequestBody(JSON_MEDIA)
            Log.d("FreeChat", "══ Vision → POST chat/completions model=${visionModel.id} prompt=${prompt.take(60)}")

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                Log.e("FreeChat", "══ Vision HTTP ${response.code}: $errBody")
                return@withContext when {
                    errBody.contains("ModelNotOpen") || errBody.contains("not activated") ->
                        "识图模型「${visionModel.displayName}」还没开通，请到控制台开通后重试。"
                    errBody.contains("does not support this api") ->
                        "识图服务配置有误（模型不支持图片），已记录，稍后修复。"
                    else -> "图片分析服务暂不可用（${response.code}），请稍后重试。"
                }
            }

            val source = response.body?.source() ?: return@withContext "图片分析返回为空。"
            val sb = StringBuilder()

            try {
                while (!source.exhausted()) {
                    if (!coroutineContext.isActive) break
                    val line = source.readUtf8Line() ?: continue
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break

                    try {
                        val json = JsonParser.parseString(data).asJsonObject
                        val choices = json.getAsJsonArray("choices") ?: continue
                        if (choices.isEmpty) continue
                        val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                        val reasoning = delta.optString("reasoning_content").orEmpty()
                        if (reasoning.isNotEmpty()) {
                            _liveReasoning.value = _liveReasoning.value + reasoning
                        }
                        val content = delta.optString("content").orEmpty()
                        if (content.isNotEmpty()) {
                            sb.append(content)
                            _liveContent.value = sb.toString()
                        }
                    } catch (_: Exception) { /* skip malformed SSE */ }
                }
            } catch (e: IOException) {
                Log.w("FreeChat", "Vision stream interrupted", e)
            }

            val finalContent = sb.toString().ifEmpty {
                // 非流式回退
                try {
                    val fullBody = response.peekBody(2 * 1024 * 1024).string()
                    val json = JsonParser.parseString(fullBody).asJsonObject
                    val choices = json.getAsJsonArray("choices")
                    choices?.get(0)?.asJsonObject?.getAsJsonObject("message")?.get("content")?.asString ?: "(空回复)"
                } catch (_: Exception) { "(空回复)" }
            }

            Log.d("FreeChat", "══ Vision OK: ${finalContent.length} chars")
            finalContent
        } catch (e: Exception) {
            Log.e("FreeChat", "══ Vision ${e.javaClass.simpleName}: ${e.message}")
            "图片分析失败：${e.message?.take(100) ?: "未知错误"}"
        }
    }

    // ========== 文件理解（上传文件 → 解析 → 回复） ==========
    private fun isFileUnderstandRequest(text: String): Boolean {
        if (text.isBlank()) return true
        val kw = listOf("总结", "讲了什么", "主要内容", "内容", "概括", "分析", "翻译", "介绍",
            "解读", "理解", "是什么", "怎么样", "简述", "讲讲", "看看这个")
        return kw.any { text.contains(it) }
    }

    private suspend fun understandFile(text: String, files: List<PendingFile>): String {
        val f = files.firstOrNull() ?: return "文件读取失败。"
        val ext = f.name.substringAfterLast('.', "").lowercase()
        return when {
            ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> {
                val encoded = listOf(f).mapNotNull { pf ->
                    runCatching { Base64.encodeToString(File(pf.path).readBytes(), Base64.NO_WRAP) to "image/jpeg" }.getOrNull()
                }
                callVisionChat(encoded, text.ifBlank { "请详细描述这张图片的内容" })
            }
            ext in listOf("m4a", "mp3", "wav", "amr", "aac", "flac", "ogg", "mp4", "3gp") -> {
                val asr = currentAsrModel()
                if (asr == null) "请先在设置里添加语音识别模型。"
                else withContext(Dispatchers.IO) {
                    val bytes = runCatching { File(f.path).readBytes() }.getOrNull()
                    if (bytes == null) "音频读取失败。"
                    else {
                        val wav = if (ext == "wav") bytes else decodeAudioToWav(bytes)
                        if (wav == null) "音频解码失败，请确认文件有效。"
                        else {
                            val t = transcribeAudioOpenAi(asr, wav)
                            if (t.isNullOrBlank()) "音频识别失败，请确认是清晰的语音。"
                            else "这段音频的内容是：\n\n$t"
                        }
                    }
                }
            }
            else -> {
                val fileText = withContext(Dispatchers.IO) { DocumentParser.extractText(File(f.path)) }
                if (fileText.isBlank() || fileText.startsWith("（")) {
                    fileText.ifBlank { "无法从这个文件里读取文字内容。" }
                } else {
                    callNonStreamingCompletion(listOf(
                        mapOf("role" to "system", "content" to "你是 FreeChat。根据用户提供的文件内容回答问题，中文回答，条理清晰，用 Markdown 排版。"),
                        mapOf("role" to "user", "content" to "文件内容如下：\n\n$fileText\n\n用户的问题：${text.ifBlank { "请总结这份文件的主要内容" }}")
                    ))
                }
            }
        }
    }

    /** 用 MediaExtractor + MediaCodec 把任意音频解码为 WAV（采样率/声道取自源格式，头一致） */
    private fun decodeAudioToWav(bytes: ByteArray): ByteArray? {
        return try {
            val context = getApplication<Application>()
            val tmp = File(context.cacheDir, "decode_${System.currentTimeMillis()}")
            tmp.writeBytes(bytes)
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(tmp.absolutePath)
            var trackIndex = -1
            var mime: String? = null
            var sampleRate = 16000
            var channels = 1
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val m = fmt.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) {
                    trackIndex = i; mime = m
                    if (fmt.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) sampleRate = fmt.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                    if (fmt.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) channels = fmt.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                    break
                }
            }
            if (trackIndex < 0 || mime == null) { tmp.delete(); return null }
            extractor.selectTrack(trackIndex)
            val codec = android.media.MediaCodec.createDecoderByType(mime)
            codec.configure(extractor.getTrackFormat(trackIndex), null, null, 0)
            codec.start()
            val info = android.media.MediaCodec.BufferInfo()
            val pcmOut = java.io.ByteArrayOutputStream()
            var outputDone = false
            var inputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        if (info.size > 0) {
                            val buf = codec.getOutputBuffer(outIdx)!!
                            val chunk = ByteArray(info.size)
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            buf.get(chunk)
                            pcmOut.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIdx == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                    outIdx == android.media.MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                }
            }
            codec.stop(); codec.release(); extractor.release(); tmp.delete()
            val pcm = pcmOut.toByteArray()
            if (pcm.isEmpty()) null else MiMoAsr.pcmToWav(pcm, sampleRate, channels, 16)
        } catch (e: Exception) {
            Log.e("FreeChat", "decodeAudioToWav failed", e)
            null
        }
    }

    private suspend fun callNonStreamingCompletion(messages: List<Map<String, String>>): String = withContext(Dispatchers.IO) {
        try {
            val model = _selectedModel.value
            val (url, key) = when (model.provider) {
                Provider.XIAOMI -> "$XIAOMI_BASE_URL/chat/completions" to (if (model.id == "mimo-v2.5-pro-ultraspeed") XIAOMI_ULTRASPEED_API_KEY else XIAOMI_API_KEY)
                Provider.DOUBAO -> "$DOUBAO_BASE_URL/chat/completions" to DOUBAO_API_KEY
                Provider.CUSTOM -> customEndpoint(model, "/v1/chat/completions")
            }
            val body = gson.toJson(mapOf(
                "model" to model.id, "messages" to messages, "stream" to false, "temperature" to 0.7
            )).toRequestBody(JSON_MEDIA)
            val resp = client.newCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json").post(body).build()).execute()
            val rBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext "文件理解失败（HTTP ${resp.code}），请稍后重试。"
            JsonParser.parseString(rBody).asJsonObject
                .getAsJsonArray("choices")?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString?.trim() ?: "（空回复）"
        } catch (e: Exception) {
            Log.e("FreeChat", "callNonStreamingCompletion failed", e)
            "文件理解失败：${e.message?.take(80) ?: "未知错误"}"
        }
    }

    // ========== 日历 ==========
    private fun readCalendar(): String {
        val context = getApplication<Application>()
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!granted) return "读取日历需要权限，请在系统设置里允许 FreeChat 访问日历后重试。"
        return try {
            val cal = Calendar.getInstance()
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 7)
            val end = cal.timeInMillis
            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.EVENT_LOCATION
            )
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, projection,
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(start.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )
            val events = mutableListOf<String>()
            cursor?.use { c ->
                val tIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
                val sIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
                val locIdx = c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                while (c.moveToNext()) {
                    val title = c.getString(tIdx) ?: "(无标题)"
                    val s = c.getLong(sIdx)
                    val loc = if (locIdx >= 0) c.getString(locIdx) ?: "" else ""
                    val fmt = SimpleDateFormat("M月d日 HH:mm", Locale.CHINESE)
                    events.add("• ${fmt.format(Date(s))} $title${if (loc.isNotBlank()) " @$loc" else ""}")
                }
            }
            if (events.isEmpty()) "未来 7 天你的日历里没有行程安排。"
            else "未来 7 天的行程安排：\n\n${events.joinToString("\n")}"
        } catch (e: Exception) {
            Log.e("FreeChat", "readCalendar failed", e)
            "读取日历失败：${e.message?.take(80) ?: "未知错误"}"
        }
    }

    // ========== 原生文档生成（大模型结构化输出 → OOXML） ==========
    private data class DocumentResult(val fileName: String = "", val path: String? = null, val error: String? = null)

    // Gson 反序列化的中间结构（字段可空，缺省安全）
    private data class JsonDoc(val title: String?, val subtitle: String?, val sections: List<JsonSection>?)
    private data class JsonSection(val heading: String?, val paragraphs: List<String>?)
    private data class JsonSheet(val title: String?, val headers: List<String>?, val rows: List<List<String>>?)
    private data class JsonPres(val title: String?, val subtitle: String?, val slides: List<JsonSlide>?)
    private data class JsonSlide(val title: String?, val bullets: List<String>?)

    private fun detectDocType(text: String): String {
        val t = text.lowercase()
        return when {
            listOf("ppt", "pptx", "幻灯片", "演示文稿", "slides").any { t.contains(it) } -> "pptx"
            listOf("excel", "xlsx", "表格", "报表", "数据表").any { t.contains(it) } -> "xlsx"
            else -> "docx"
        }
    }

    private fun typeLabel(type: String): String = when (type) {
        "pptx" -> "演示文稿（PPT）"
        "xlsx" -> "电子表格（Excel）"
        else -> "文档（Word）"
    }

    private suspend fun generateDocument(text: String, files: List<PendingFile>): DocumentResult {
        val type = detectDocType(text)
        val fileContext = if (files.isNotEmpty()) {
            withContext(Dispatchers.IO) { DocumentParser.extractText(File(files.first().path)) }
        } else ""

        val systemPrompt = when (type) {
            "pptx" -> "你是一个专业的演示文稿策划。根据用户需求生成结构清晰、内容专业的 PPT。只输出 JSON，不要任何解释、不要 markdown 代码块。JSON 格式：{\"title\":\"主标题\",\"subtitle\":\"副标题\",\"slides\":[{\"title\":\"页标题\",\"bullets\":[\"要点1\",\"要点2\"]}]}。每页 3-5 条要点，共 6-10 页，内容充实。"
            "xlsx" -> "你是一个数据分析师。根据用户需求生成表格数据。只输出 JSON，不要任何解释、不要 markdown 代码块。JSON 格式：{\"title\":\"表名\",\"headers\":[\"列1\",\"列2\"],\"rows\":[[\"值1\",\"值2\"],[\"值3\",\"值4\"]]}。表头清晰，数据完整。"
            else -> "你是一个专业写手。根据用户需求写一份排版清晰的文档。只输出 JSON，不要任何解释、不要 markdown 代码块。JSON 格式：{\"title\":\"标题\",\"subtitle\":\"副标题\",\"sections\":[{\"heading\":\"小节标题\",\"paragraphs\":[\"段落1\",\"段落2\"]}]}。内容充实，分段合理，用正式流畅的中文。"
        }

        val userPrompt = buildString {
            if (fileContext.isNotBlank() && !fileContext.startsWith("（")) {
                append("以下是原文档内容：\n\n$fileContext\n\n")
            }
            append("用户的指令：${text.ifBlank { "请生成一份内容充实的${typeLabel(type)}" }}")
        }

        val json = callStructuredJson(systemPrompt, userPrompt)
        if (json.isBlank()) return DocumentResult(error = "内容生成超时或失败，请重试。")

        return try {
            val base = getApplication<Application>().getExternalFilesDir(null) ?: getApplication<Application>().filesDir
            val dir = File(base, "FreeChat")
            dir.mkdirs()
            val fileName = buildFileName(type, text)
            when (type) {
                "pptx" -> {
                    val j = gson.fromJson(json, JsonPres::class.java)
                    OoxmlGenerator.generatePptx(dir, fileName, toPresentation(j))
                }
                "xlsx" -> {
                    val j = gson.fromJson(json, JsonSheet::class.java)
                    OoxmlGenerator.generateXlsx(dir, fileName, toSheet(j))
                }
                else -> {
                    val j = gson.fromJson(json, JsonDoc::class.java)
                    OoxmlGenerator.generateDocx(dir, fileName, toDoc(j))
                }
            }
            DocumentResult(fileName = fileName, path = File(dir, fileName).absolutePath)
        } catch (e: Exception) {
            Log.e("FreeChat", "generateDocument failed", e)
            DocumentResult(error = "文档生成失败：${e.message?.take(80) ?: "解析出错"}")
        }
    }

    private fun buildFileName(type: String, text: String): String {
        val base = text.replace(Regex("[\\\\/:*?\"<>|\\s，。！？、；：,.!?;:]+"), "").take(12).ifBlank { "文档" }
        val ext = when (type) { "pptx" -> "pptx"; "xlsx" -> "xlsx"; else -> "docx" }
        return "${base}_${System.currentTimeMillis() % 100000}.$ext"
    }

    private fun toDoc(j: JsonDoc): DocContent = DocContent(
        title = j.title?.ifBlank { "未命名文档" } ?: "未命名文档",
        subtitle = j.subtitle,
        sections = j.sections.orEmpty().mapNotNull { s ->
            val paras = s.paragraphs.orEmpty().filter { it.isNotBlank() }
            if (s.heading.isNullOrBlank() && paras.isEmpty()) null
            else DocSection(heading = s.heading, paragraphs = paras)
        }
    )

    private fun toSheet(j: JsonSheet): SheetContent = SheetContent(
        title = j.title,
        headers = j.headers.orEmpty(),
        rows = j.rows.orEmpty().map { it.map { cell -> cell ?: "" } }
    )

    private fun toPresentation(j: JsonPres): PresentationContent = PresentationContent(
        title = j.title?.ifBlank { "演示文稿" } ?: "演示文稿",
        subtitle = j.subtitle,
        slides = j.slides.orEmpty().mapNotNull { s ->
            val bullets = s.bullets.orEmpty().filter { it.isNotBlank() }
            if (s.title.isNullOrBlank() && bullets.isEmpty()) null
            else SlideContent(title = s.title?.ifBlank { "未命名" } ?: "未命名", bullets = bullets)
        }
    )

    private suspend fun callStructuredJson(system: String, user: String): String = withContext(Dispatchers.IO) {
        try {
            val model = _selectedModel.value
            val (url, key) = routeModelEndpoint(model)
            val messages = listOf(
                mapOf("role" to "system", "content" to system),
                mapOf("role" to "user", "content" to user)
            )
            val body = gson.toJson(mapOf(
                "model" to model.id, "messages" to messages,
                "stream" to false, "temperature" to 0.7, "max_tokens" to 4000
            )).toRequestBody(JSON_MEDIA)
            val resp = client.newCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json").post(body).build()).execute()
            val rBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                Log.e("FreeChat", "structured HTTP ${resp.code}: ${rBody.take(200)}")
                return@withContext ""
            }
            var content = JsonParser.parseString(rBody).asJsonObject
                .getAsJsonArray("choices")?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString ?: ""
            content = content.trim()
            val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
            fence.find(content)?.let { content = it.groupValues[1].trim() }
            val start = content.indexOf('{'); val end = content.lastIndexOf('}')
            if (start >= 0 && end > start) content = content.substring(start, end + 1)
            content
        } catch (e: Exception) {
            Log.e("FreeChat", "callStructuredJson failed", e)
            ""
        }
    }

    // ========== 记忆总结 ==========
    private fun summarizeAndRemember(convId: String, userText: String, aiMsg: Message, highQuality: Boolean = false) {
        if (!_autoSummarizeMemory.value) return  // 开关关闭时不总结
        // 后台异步总结：不阻塞「正在输入」状态收尾，也不拖慢回复展示
        viewModelScope.launch {
            try {
                val (summary, kind) = withContext(Dispatchers.IO) {
                    summarizeExchange(userText, aiMsg.content, highQuality)
                }
                if (summary.isNotEmpty()) {
                    val keywords = extractKeywords(userText)
                    memoryManager.append(convId, MemoryEntry(summary = summary, keywords = keywords, kind = kind))
                }
            } catch (e: Exception) {
                Log.e("FreeChat", "Memory summarize failed", e)
            }
        }
    }

    /** 总结一段对话并判断记忆类型：返回 (summary, kind)，kind = plot（主线/重大事件）或 detail（细节） */
    private suspend fun summarizeExchange(userText: String, aiReply: String, highQuality: Boolean): Pair<String, String> {
        val nowStr = SimpleDateFormat("yyyy年M月d日", Locale.CHINESE).format(Calendar.getInstance().time)
        val prompt = if (highQuality) {
            listOf(
                mapOf("role" to "system", "content" to "你是记忆摘录器。从用户的消息里摘录「需要长期记住的事实性原文」，原样摘录用户的原话，绝不改写、概括、补充或脑补。判断类型：\n- plot：剧情主线走向、关系转变、重大事件、重要承诺、关键背景设定、未来计划/约定、用户明确的个人信息\n- detail：普通日常闲聊的细节（近期有效即可）\n\n摘录规则（重要）：\n1. 只摘录用户消息里明确说的事实（人物、关系、时间、地点、数字、承诺、计划、喜好、经历），用用户的原话，不要用你自己的话转述。\n2. 时间语境（如「高考后的暑假」「去年」「上周」）要原样保留，不要丢失，也不要擅自换算或脑补成别的时间。\n3. 数字、日期、专有名词必须精确，一字不差。\n4. 用户没说过的信息绝对不要脑补。\n只输出一行 JSON：{\"summary\":\"摘录的原文片段\",\"kind\":\"plot\"或\"detail\"}"),
                mapOf("role" to "user", "content" to "用户说：$userText\nAI回复：${aiReply.take(200)}\n请摘录用户消息里的事实原文：")
            )
        } else {
            listOf(
                mapOf("role" to "system", "content" to "你是记忆归纳器。用 1-3 句中文总结以下对话的关键信息，并判断它属于哪一类：\n- plot：剧情主线走向、关系转变、重大事件、重要承诺、关键背景设定、以及任何「未来某天要做的事」（考试、约定、生日、计划等，长期贯穿，需始终记住）\n- detail：普通日常闲聊的细节（近期有效即可）\n\n今天是 $nowStr。重要规则：\n1. 用户提到的未来事件（如「5天后考试」「下周三见面」）必须换算成具体绝对日期（如「9月4日考试」），绝不能保留「5天后」这类相对说法，否则之后会算错时间。\n2. 用户的个人信息、计划、承诺、喜好等要原样准确记录，数字和日期不要概括丢失。\n只输出一行 JSON：{\"summary\":\"总结内容\",\"kind\":\"plot\"或\"detail\"}"),
                mapOf("role" to "user", "content" to "用户说：$userText\nAI回复：${aiReply.take(200)}\n请总结：")
            )
        }
        val model = _selectedModel.value
        val (url, key) = routeModelEndpoint(model)
        val body = gson.toJson(mapOf(
            "model" to model.id, "messages" to prompt,
            "stream" to false, "temperature" to 0.1, "max_tokens" to 300
        )).toRequestBody(JSON_MEDIA)

        return try {
            val resp = client.newCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json").post(body).build()).execute()
            val rBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                "" to ""
            } else {
                val raw = JsonParser.parseString(rBody).asJsonObject
                    .getAsJsonArray("choices")?.get(0)?.asJsonObject
                    ?.getAsJsonObject("message")?.get("content")?.asString?.trim() ?: ""
                // 假名守卫：丢弃含日文假名的总结，防止日文污染记忆
                if (raw.isEmpty() || raw.any { it in '぀'..'ヿ' }) {
                    "" to ""
                } else {
                    val obj = extractJsonObject(raw)
                    if (obj != null) {
                        val summary = obj.get("summary")?.asString?.trim() ?: ""
                        val kind = obj.get("kind")?.asString?.trim() ?: "detail"
                        summary to (if (kind == "plot") "plot" else "detail")
                    } else {
                        // 没输出 JSON，退化为纯总结（按 detail）
                        raw to "detail"
                    }
                }
            }
        } catch (_: Exception) { "" to "" }
    }

    private fun extractKeywords(text: String): List<String> {
        return text.split(Regex("[\\s，。！？,.!?、；：\"'（）()\\[\\]【】]+"))
            .filter { it.length in 2..8 }.distinct().take(8)
    }

    // ========== 持久化 ==========
    private fun saveConversations() {
        try { conversationsFile.writeText(gson.toJson(_conversations.value)) }
        catch (e: Exception) { Log.e("FreeChat", "Save convs failed", e) }
    }
    private fun loadConversations(): List<Conversation> = try {
        if (conversationsFile.exists()) {
            val type = object : TypeToken<List<Conversation>>() {}.type
            val list = gson.fromJson<List<Conversation>>(conversationsFile.readText(), type) ?: emptyList()
            // 迁移旧单值字段（形象图/开场白）到新列表字段，不丢老数据
            list.map { conv -> conv.copy(characterProfile = conv.characterProfile?.normalized()) }
        } else emptyList()
    } catch (_: Exception) { emptyList() }

    private fun messagesFile(convId: String): File =
        File(getApplication<Application>().filesDir, "freechat_msgs_$convId.json")

    private fun saveMessages(convId: String, msgs: List<Message>) {
        try { messagesFile(convId).writeText(gson.toJson(msgs)) }
        catch (e: Exception) { Log.e("FreeChat", "Save msgs failed", e) }
    }

    private fun loadMessages(convId: String): List<Message> = try {
        val file = messagesFile(convId)
        if (file.exists()) {
            val type = object : TypeToken<List<Message>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } else emptyList()
    } catch (_: Exception) { emptyList() }

    /** 按对话持久化消息 + 刷新侧滑栏元数据；若该对话正是当前显示对话，则同步更新显示缓冲（否则只落盘） */
    private fun persistConversationMessages(convId: String, msgs: List<Message>) {
        if (msgs.isEmpty()) return
        val now = System.currentTimeMillis()
        val existing = _conversations.value.firstOrNull { it.id == convId }
        val title = if (existing?.mode == ChatMode.COMPANION && !existing.characterProfile?.name.isNullOrBlank())
            existing.characterProfile!!.name
        else generateTitle(msgs)
        val conv = Conversation(
            id = convId, title = title,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            messageCount = msgs.size,
            isPinned = pinnedIds.value.contains(convId),
            mode = existing?.mode ?: ChatMode.STANDARD,
            characterProfile = existing?.characterProfile
        )
        val updated = _conversations.value.toMutableList()
        val idx = updated.indexOfFirst { it.id == convId }
        if (idx >= 0) updated[idx] = conv else updated.add(0, conv)
        _conversations.value = sortConversations(updated.filter { it.messageCount > 0 || it.mode == ChatMode.COMPANION })
        saveConversations()
        saveMessages(convId, msgs)
        if (_currentConversationId.value == convId) _messages.value = msgs.toList()
    }

    /** 切换显示对话后，用该对话的生成状态刷新 loading/typing 显示 */
    private fun syncDisplayGenerationState(convId: String?) {
        _isLoading.value = convId?.let { convLoading[it] == true } ?: false
        _isTyping.value = convId?.let { convTyping[it] == true } ?: false
    }

    private fun setConvLoading(convId: String, v: Boolean) {
        convLoading[convId] = v
        if (_currentConversationId.value == convId) _isLoading.value = v
        // 后台且所有对话都生成完毕 → 停掉前台服务（其「正在回复」通知消失，留给回复通知）
        if (!v && !appInForeground && convLoading.values.none { it } && convTyping.values.none { it }) {
            ReplyService.stop(getApplication())
        }
    }

    private fun setConvTyping(convId: String, v: Boolean) {
        convTyping[convId] = v
        if (_currentConversationId.value == convId) _isTyping.value = v
    }

    private fun saveCurrentConversation() {
        val msgs = _messages.value
        if (msgs.isEmpty()) return
        val convId = _currentConversationId.value ?: UUID.randomUUID().toString()
        val title = if (_currentMode.value == ChatMode.COMPANION && !_currentCharacter.value?.name.isNullOrBlank())
            _currentCharacter.value!!.name
        else generateTitle(msgs)
        val now = System.currentTimeMillis()
        // 保留原对话的创建时间与最后聊天时间；只有真实发消息（touchCurrentConversation）才刷新时间，
        // 避免「仅点开对话」就刷新时间导致侧滑栏乱跳
        val existing = _conversations.value.firstOrNull { it.id == convId }
        val conv = Conversation(
            id = convId, title = title,
            createdAt = existing?.createdAt ?: now,
            updatedAt = existing?.updatedAt ?: now,
            messageCount = msgs.size,
            isPinned = pinnedIds.value.contains(convId),
            mode = existing?.mode ?: _currentMode.value,
            characterProfile = existing?.characterProfile ?: _currentCharacter.value
        )
        val updated = _conversations.value.toMutableList()
        val idx = updated.indexOfFirst { it.id == convId }
        if (idx >= 0) updated[idx] = conv else updated.add(0, conv)
        _conversations.value = sortConversations(updated.filter { it.messageCount > 0 || it.mode == ChatMode.COMPANION })
        saveConversations()
        saveMessages(convId, msgs)
        _currentConversationId.value = convId
    }

    /** 用户真正发送消息时，刷新当前对话的最后聊天时间（仅聊天才更新时间，点开不刷新） */
    private fun touchCurrentConversation() {
        val convId = _currentConversationId.value ?: return
        val now = System.currentTimeMillis()
        _conversations.value = _conversations.value.map {
            if (it.id == convId) it.copy(updatedAt = now) else it
        }
    }

    private fun generateTitle(messages: List<Message>): String {
        val first = messages.find { it.role == Role.USER }
        return if (first != null) {
            val c = first.content.trim().ifBlank {
                when {
                    first.attachmentName != null -> "[文件] ${first.attachmentName}"
                    first.imagePaths.orEmpty().isNotEmpty() -> "[图片]"
                    else -> "空对话"
                }
            }
            if (c.length <= 30) c else c.take(30) + "…"
        } else "空对话"
    }

    // ========== 系统提示词 — AI身份 + 日期 + 风格 + 搜索策略 ==========
    private fun buildSystemPrompt(hasTools: Boolean = false, history: List<Message> = emptyList()): String {
        val now = Calendar.getInstance()
        val dateStr = SimpleDateFormat("yyyy年M月d日 EEEE HH:mm", Locale.CHINESE).format(now.time)

        // ──── AI 身份 ────
        val aiIdentity = buildString {
            append("【你的身份】\n")
            append("你是 FreeChat，一个 AI 助手，由 Belate 开发。")
        }

        // ★ TempMode → 风格指令
        val styleHint = when (_tempMode.value) {
            TempMode.OBJECTIVE ->
                "【客观模式】\n" +
                "内部思考（不输出给用户，仅在你的推理中完成）：\n" +
                "1. 准确理解问题核心：用户真正想问什么？涉及哪些维度？\n" +
                "2. 全面检索信息：搜索结果 > 训练数据，冲突时以搜索结果为准\n" +
                "3. 逐维度分析：每个维度基于可用信息给出判断，信息不足则标记\n" +
                "4. 组织最终回答：按逻辑顺序排列，从核心结论到展开细节\n\n" +
                "输出要求（这是用户看到的最终内容）：\n" +
                "- 禁止输出上述思考步骤。只展示打磨后的最终回答\n" +
                "- 开头一句话给出核心结论\n" +
                "- 根据内容用小标题或编号分条展开，逻辑清晰\n" +
                "- 可用**加粗**强调关键信息\n" +
                "- 不确定处标注[暂未查证]，不编造\n" +
                "- 保持客观冷静，禁止主观臆断和情感化表达"

            TempMode.WARM ->
                "【热情模式】\n" +
                "用温暖友善的语气回应，像值得信赖的朋友聊天。适当表达关心和同理心。\n" +
                "思考过程在内部完成，不展示分析步骤，只输出自然流畅的最终回答。"

            TempMode.AUTO ->
                "【默认风格】\n" +
                "自然友好地回应用户。思考在内部完成，不展示推理步骤。\n" +
                "根据内容需要可以分条说明，但保持聊天般自然的节奏。"
        }

        val lengthHint = when (_lengthMode.value) {
            LengthMode.CONCISE ->
                "【精辟回复】只回答用户问的问题本身。不展开背景，不联想相关话题，不多说不必要的话。直接给答案。"
            LengthMode.FULL ->
                "【完整回复】全面周到地回答。涉及步骤的逐条列出，涉及原理的通俗解释，涉及选择的对比利弊。确保覆盖用户可能关心的所有方面。"
            LengthMode.AUTO ->
                "回复长短适中，核心信息说明白就好，不啰嗦也不敷衍。"
        }

        // 仅当用户明确询问身份时才注入
        val lastUserMsg = history.lastOrNull { it.role == Role.USER }?.content ?: ""
        val profile = if (isBotIdentityQuery(lastUserMsg)) "\n\n" + buildBotProfile() else ""

        // 语言识别：根据用户最后一条消息的语言决定回复语言（简单英文问候仍按中文）
        val langHint = buildString {
            append("【语言】")
            when (detectUserLanguage(lastUserMsg)) {
                "日文" -> append("用户使用日文交流，请用日文回答。")
                "英文" -> append("用户使用英文交流，请用英文回答。")
                else -> append("用户使用中文交流，请用中文回答。")
            }
            append("以用户主要使用的语言回复，遇到其他语言的专有名词或引用时保留原文即可。")
        }

        val capabilityBlock = if (hasTools) {
            "【工具调用——如何完成任务】\n" +
            "你拥有一组工具（函数），可完成生图、修图、识图、读日历、生成文档、读取文件等能力。\n" +
            "1. 当用户明确要求完成某项能力时，直接调用对应工具，不要用文字描述来代替调用\n" +
            "2. 用户只是询问你的能力（如「你能生成图片吗」）时，先自然回答「可以」，不要立即调用工具；等用户给出具体需求再调用\n" +
            "3. 调用生图工具时，图片由系统自动生成并展示，你无需输出图片链接或代码，也不要建议用户改用其他绘图工具\n" +
            "4. 调用工具后系统会直接把结果展示给用户，你无需重复转述结果\n" +
            "5. 上下文衔接：用户常对之前的结果提出追问或修改（如生图后说「不要大头照要全身照」「换背景」「再画一张」，或对文档说「改一下标题」），必须结合上文对话理解用户意图、继续调用对应工具，绝不能当作孤立的纯文字问题牛头不对马嘴地回答"
        } else {
            "【你的能力——生成图片】\n" +
            "你具备生成图片的能力，由 FreeChat 内置的图像引擎（Doubao Seedream）自动完成。\n" +
            "当用户要求生成或绘制图片时（如「画一只猫」「帮我生成一张海报」），你负责：\n" +
            "1. 用自然友好的语气回应，如「好的，这就为你画」，绝不说「我无法生成图片」「我是文字模型不能画图」这类话\n" +
            "2. 图片由系统自动生成并展示，你无需输出图片链接或代码\n" +
            "3. 不要建议用户改用其他绘图工具（Midjourney、DALL-E 等），因为你自己就能生成"
        }

        // ★ 全局记忆：用户自定义的基本信息 / 回复要求，注入系统提示词以提高适配度
        val globalMemoryBlock = if (_globalMemories.value.isNotEmpty()) {
            "【用户全局记忆——回答时必须参考】\n" +
            "以下是你已知的关于用户的信息与回复要求，回答时自动参考并遵守，无需主动提及：\n" +
            _globalMemories.value.joinToString("\n") { "- $it" }
        } else ""

        val parts = listOf(
            aiIdentity,
            "今天是 $dateStr。你的训练数据存在截止日期，当前时间可能已超出你的知识范围。",
            styleHint,
            lengthHint,
            globalMemoryBlock,
            capabilityBlock,
            "【排版规范——像精排文档一样输出】\n" +
            "用 Markdown 结构化排版，让回复层次分明、读起来舒服（对标 DeepSeek、Gemini）：\n" +
            "1. 有多个大方向/主题时，用「## 大标题」单独成行；大方向下的小板块用「### 小标题」，字号逐级加大加粗\n" +
            "2. 每个方向下的要点用有序列表，要点标题加粗：「1. **要点标题**」，解释内容换行缩进 4 个空格写在正下方\n" +
            "3. 大段与大段、Part 与 Part 之间要空一行留白，不要所有文字挤在一起\n" +
            "4. 换大主题/大板块时用「---」单独一行作分页分隔，让内容一眼分块\n" +
            "5. 关键词、结论、数字用 **加粗**；次要强调用 *倾斜*；特别提醒用 ++下划线++\n" +
            "6. 步骤用有序列表，并列要点用无序列表，对比/罗列数据用表格\n" +
            "7. 简单问题自然成段即可，不要为套格式而过度堆砌",
            "【联网搜索——最高优先级】\n" +
            "你已启用实时联网搜索。回答任何涉及事实、时事、数据的问题时：\n" +
            "1. 搜索结果（标注「实时搜索结果」）优先于训练数据\n" +
            "2. 两者冲突时以搜索结果为准\n" +
            "3. 直接整合搜索结果回答，不要在回答中标注来源编号或来源名称（如「来源1」）\n" +
            "4. 信息不完整或矛盾时如实说明，不要编造",
            "【重要——所有模式下通用】\n" +
            "思考和分析过程在内部完成，不要将推理步骤展示给用户。用户只看到打磨后的最终回答，不看到「第1步」「第2步」等草稿内容。",
            langHint
        ).filter { it.isNotEmpty() }

        return (parts.joinToString("\n\n") + profile).trimEnd()
    }

    /** 检测用户消息语言：简单英文问候仍按中文；含日文假名→日文；英文占比高→英文；默认中文 */
    private fun detectUserLanguage(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return "中文"
        val simpleEnglish = setOf(
            "hello", "hi", "hey", "ok", "okay", "thanks", "thank you", "thx",
            "good", "yes", "no", "bye", "good morning", "good night", "how are you",
            "morning", "evening", "night"
        )
        if (t.lowercase() in simpleEnglish) return "中文"
        // 含日文假名（平假名 U+3040–309F / 片假名 U+30A0–30FF）→ 日文
        if (t.any { it in '぀'..'ヿ' }) return "日文"
        // 英文占比高（长英文句子）→ 英文
        val letters = t.count { it in 'a'..'z' || it in 'A'..'Z' }
        if (letters > 0 && letters.toFloat() / t.length > 0.5f) return "英文"
        return "中文"
    }

    /** 检测用户是否在询问 Bot 身份 */
    private fun isBotIdentityQuery(text: String): Boolean {
        val keywords = listOf(
            "你是谁", "你是什么", "你的版本", "版本号", "什么模型",
            "什么版本", "哪个模型", "你能做什么", "你的能力", "你的功能",
            "谁开发的", "谁做的", "你的作者", "你叫什么", "介绍一下自己",
            "who are you", "what model", "what version", "who made you"
        )
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    /** Bot 身份描述（极简，仅在用户询问时注入） */
    private fun buildBotProfile(): String {
        val modelName = _selectedModel.value.displayName
        val visualModelName = _selectedVisualModel.value.displayName
        val visionModelName = _selectedVisionModel.value?.displayName ?: ""
        return "以下信息仅在用户询问时使用：你是 FreeChat，由 Belate 开发的 AI 助手。当前语言模型：$modelName，生图模型：$visualModelName，识图模型：$visionModelName。"
    }
}

// 安全取字符串：区分「字段缺失」与「显式 null」。推理模型的 SSE 里 content/reasoning_content
// 会交替出现显式 null（如 reasoning 阶段 content=null），Gson 的 getAsString 对 JsonNull 会抛异常，
// 导致整条 chunk 被吞。这里用 isJsonNull 过滤，null/缺失统一返回 null。
private fun com.google.gson.JsonObject?.optString(key: String): String? =
    this?.get(key)?.takeUnless { it.isJsonNull }?.asString

/** 待发送图片：path=已拷贝到内部存储的本地路径（显示+持久化），mime=用于 API 的类型 */
data class PendingImage(val path: String, val mime: String)

/** 待发送文件：path=内部存储路径，name=显示名，mime=类型 */
data class PendingFile(val path: String, val name: String, val mime: String)
