package com.freechat.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Looper
import android.provider.CalendarContract
import android.util.Base64
import android.util.Log
import com.freechat.data.MiMoAsr
import com.freechat.core.CompanionMood
import com.freechat.core.CompanionReply
import com.freechat.core.CompanionRhythm
import com.freechat.core.MemoryDraft
import com.freechat.core.ProactiveFire
import com.freechat.core.ProactiveSignal
import com.freechat.util.DocumentParser
import com.freechat.util.pairedIndices
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
import com.freechat.data.AppJson
import com.freechat.data.LocalStore
import com.freechat.data.MessageDeletion
import com.freechat.data.healed
import com.freechat.sync.Adapter
import com.freechat.sync.ApiClient
import com.freechat.sync.Merge
import com.freechat.sync.PerConvBridge
import com.freechat.sync.Relay
import com.freechat.sync.Session
import com.freechat.data.MemoryManager
import com.freechat.data.ModelCatalog
import com.freechat.data.PerConvStore
import com.freechat.data.ModelSelectionResolver
import com.freechat.data.RequestModels
import com.freechat.data.AttachmentContext
import com.freechat.data.SearchPipeline
import com.freechat.data.SearchIntent
import com.freechat.data.awaitText
import com.freechat.data.SearchConfig
import com.freechat.data.SearchConfigStore
import com.freechat.data.SearchProvider
import com.freechat.data.SearchPresentation
import com.freechat.data.NativeSearchEvidence
import com.freechat.data.NativeSearchPolicy
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.freechat.data.SettingsRepository
import com.freechat.data.TtsController
import com.freechat.i18n.AppLanguage
import com.freechat.model.*
import com.freechat.proactive.ProactiveRequest
import com.freechat.proactive.ProactiveScheduler
import com.freechat.proactive.ProactiveStore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.freechat.model.HeaderBarStyle
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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
        private val nativeSearchFailures = mutableMapOf<String, Long>()

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val CONVERSATIONS_FILE = "freechat_conversations.json"
        private const val PER_CONV_FILE = "freechat_perconv.json"
        private const val MEMORY_CONTEXT_THRESHOLD = 8
        // 生成期间给前台服务续唤醒锁的间隔（长回复超过一把锁的兜底时长就会被冻住，必须续）
        private const val LOCK_RENEW_INTERVAL_MS = 5 * 60 * 1000L
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

        // ===== 主动智能：进程级共享状态 =====

        /** 进程内当前存活的 ChatViewModel（App 开着时就是它）。弱引用：被回收后自动变 null，不需要谁去清 */
        private var liveRef: java.lang.ref.WeakReference<ChatViewModel>? = null

        /**
         * 主动智能被闹钟唤醒时，用它拿到「该用哪个 VM 干活」：
         * App 开着 → 用现成的实例（状态、缓冲管线、前后台标记全对得上）；
         * App 没开着 → 返回 null，由 ProactiveService 新建一个专供这次生成。
         * 这样保证同一个进程里只有一个 VM 在写同一个对话文件（两个 VM 同时写会互相覆盖）。
         */
        fun live(): ChatViewModel? = liveRef?.get()

        /**
         * 正在跑的主动智能任务（convId -> Job），同样是进程级共享：
         * 用户发消息时要能取消掉「正在自己冒出来的那句话」——此时 TA 已经来了，
         * 主动消息已经失去意义；而且两条写入撞在一起还会互相覆盖。
         * 之所以不放在 VM 实例上：App 冷启动时生成跑在服务新建的 VM 上，用户发消息走的是另一个 VM。
         */
        private val proactiveJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

        /**
         * 主动智能收尾工作（读账本、挂闹钟）用的进程级作用域。
         * **不能用 viewModelScope**：闹钟唤醒时 `live()` 拿到的那个 VM 可能已经走完生命周期
         * （Activity 销毁 → ViewModelStore 清空 → viewModelScope 已取消），
         * 那样「给自己定下一次」会静默失败——表现为「只主动找过一次，之后再也没动静」。
         */
        private val proactiveScope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                // 这个作用域在别的线程上跑，抛出去没人接就是整个进程崩。
                // 主动智能是「锦上添花」的功能，任何意外都不该把用户正在看的聊天带走。
                Log.e("FreeChat", "proactiveScope failed", e)
            }
        )
    }

    private val settingsRepo = SettingsRepository(application)
    private val searchConfigStore = SearchConfigStore(application)
    private val _searchConfig = MutableStateFlow(searchConfigStore.load())
    val searchConfig: StateFlow<SearchConfig> = _searchConfig.asStateFlow()

    suspend fun saveSearchConfig(config: SearchConfig) {
        withContext(Dispatchers.IO) { searchConfigStore.save(config) }
        _searchConfig.value = config
        SearchPipeline.clearCache()
    }
    private val memoryManager = MemoryManager()
    // OkHttp 客户端：这里的每一项都为「切后台 / 锁屏后还能收到回复」服务
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)  // 30s→15s：连不上就早点失败重试，别让用户干等半分钟
        .readTimeout(180, TimeUnit.SECONDS)    // 流式+搜索需要更久（这是「多久没收到任何字节」的上限）
        .writeTimeout(30, TimeUnit.SECONDS)
        // ★ 连接池闲置上限收到 1 分钟：锁屏后 NAT / 运营商常把「看起来空闲」的 TCP 连接悄悄回收，
        //   而池子里那条连接在 OkHttp 眼里还活着 —— 下一条消息挂上去就得干等满 readTimeout 才报错。
        //   池子里留得越短，撞上这种「半死连接」的窗口就越小。
        .connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))
        // ★ 心跳：HTTP/2 下每 20 秒发一个 PING 帧，链路要么活着、要么当场发现已经断了。
        //   （HTTP/1.1 不支持 PING，那条路靠上面的短连接池 + 断线重试兜底。）
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)  // OkHttp 自带的重连只覆盖「连接阶段」，响应读一半断掉不会重试
        .build()
    // 用 InstanceCreator 提供默认值：老版本消息缺少 imagePaths/imageUrls/reasoningContent 等字段时，
    // 反序列化不会把它们置为 null，避免侧滑切换会话时 NPE 崩溃。
    // 提到 AppJson 里共用：同步引擎解「网页端推上来的那份」时，必须是**同一个**实例
    // —— 那种 JSON 缺的键更多（网页没有 1.0.34 新加的那几个角色字段），
    // 各自建一个 Gson 就等于把那道防线丢在门外，见 AppJson 的注释。
    private val gson = AppJson.gson

    // ========== 状态 ==========
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // ========== 多选状态（跨 Composable 共享：MainActivity 标题 + ChatScreen 顶栏/气泡）==========
    // 放 VM 而不是 ChatScreen 的 remember：标题在 MainActivity、气泡在 ChatScreen，是两棵平行子树；
    // 而且配对判定要知道消息列表，只有 VM 有 _messages。
    private val _multiSelect = MutableStateFlow(MultiSelectState())
    val multiSelect: StateFlow<MultiSelectState> = _multiSelect.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    private val convGenerationPhases = java.util.concurrent.ConcurrentHashMap<String, com.freechat.data.GenerationPhase>()
    private val _generationPhase = MutableStateFlow(com.freechat.data.GenerationPhase.IDLE)
    val generationPhase: StateFlow<com.freechat.data.GenerationPhase> = _generationPhase.asStateFlow()
    private val generationSerialCounter = java.util.concurrent.atomic.AtomicLong()
    private val convGenerationSerials = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val _generationSerial = MutableStateFlow(0L)
    val generationSerial: StateFlow<Long> = _generationSerial.asStateFlow()
    // The streaming row and its persisted reply share one LazyColumn key.
    private val convGenerationReplyIds = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val _generationReplyId = MutableStateFlow<String?>(null)
    val generationReplyId: StateFlow<String?> = _generationReplyId.asStateFlow()

    private fun advanceGeneration(convId: String, phase: com.freechat.data.GenerationPhase, expectedSerial: Long? = null) {
        if (convId.isBlank()) return
        val next = convGenerationPhases.computeIfPresent(convId) { _, current ->
            if (expectedSerial != null && convGenerationSerials[convId] != expectedSerial) current else current.advance(phase)
        } ?: return
        if (_currentConversationId.value == convId &&
            (expectedSerial == null || convGenerationSerials[convId] == expectedSerial)) _generationPhase.value = next
    }
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
    private var standardGenerationConvId: String? = null
    private var standardGenerationId = 0
    private val favoritesRevision = java.util.concurrent.atomic.AtomicLong()

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

    // 主动智能的本地账本（计划 + 每日配额），懒加载：不用这个功能的人不会碰到一次文件读取
    private val proactiveStore by lazy { ProactiveStore(getApplication()) }
    // 主动消息强制发通知：进程被闹钟冷启动时 appInForeground 还停在默认值 true（没人调过
    // onAppForegroundChanged），据此判断「用户在看」会得出完全相反的结论 —— 于是 TA 好不容易
    // 主动找你一次，却静悄悄地什么都没发生。
    private var proactiveForceNotify = false

    // 本轮发送的起始索引（停止时据此截断「用户消息 + AI 回复」；-1 表示无进行中的普通发送轮）
    private var activeRoundStartIndex = -1
    private var activeRoundConvId: String? = null
    private var standardSettingsSnapshot: SettingsSnapshot? = null

    // ===== 前后台状态 + 系统通知 =====
    private var appInForeground = true
    // 发消息到全部回复结束期间为 true（含拟人模式的缓冲等待期），期间前台服务保活
    private var generationPending = false
    // 前台服务是否在跑（避免重复 startForegroundService 导致通知闪烁）
    private var foregroundServiceRunning = false
    // 上次发起启动的时刻。startForegroundService 是异步的，实例要过一小会儿才建好，
    // 这段时间里 isRunning() 还是 false，不能据此判定「服务没起来」而反复重启
    private var foregroundServiceStartAt = 0L
    // 上次续唤醒锁的时刻（节流用；release+acquire 有开销，不能每次状态变更都来一遍）
    private var lastLockRenewAt = System.currentTimeMillis()
    fun onAppForegroundChanged(foreground: Boolean) {
        appInForeground = foreground
        if (foreground) {
            // 回前台：只清「新消息回复」那一条；前台服务是否保留交给 syncForegroundService（有生成在跑才保留）
            ReplyService.clearReply(getApplication())
        }
        syncForegroundService()
    }

    /** 发消息时置 pending，立刻启动前台服务保活（锁屏/切后台不冻网、不中断回复） */
    private fun markGenerationStarted() {
        generationPending = true
        syncForegroundService()
    }

    /** 有生成在跑/待回复 → 前台服务保活；全部结束 → 停止。 */
    private fun syncForegroundService() {
        val should = generationPending || convLoading.values.any { it } || convTyping.values.any { it }
        val now = System.currentTimeMillis()
        // 标志位要跟现实对账：进程被系统回收过的话服务已经没了，仍记着 true 就再也不会重新拉起来，
        // 后面每一轮都会「以为保活还在」——这正是「切后台后 AI 迟迟不回复」最隐蔽的一种。
        // 3 秒宽限期用来躲开启动途中的假阴性（实例还没建好 ≠ 没起来）。
        if (foregroundServiceRunning && !ReplyService.isRunning() && now - foregroundServiceStartAt > 3_000L) {
            Log.w("FreeChat", "Keep-alive service gone, will restart")
            foregroundServiceRunning = false
        }
        if (should && !foregroundServiceRunning) {
            val title = _currentCharacter.value?.name?.ifBlank { "FreeChat" } ?: "FreeChat"
            // 只有真的启动成功才记为「在跑」；失败（如后台启动前台服务被系统拒绝）保持 false，
            // 下次 tick 还能再试——否则保活静默失效、用户以为在后台等着却收不到回复。
            foregroundServiceRunning = ReplyService.startThinking(getApplication(), title)
            if (foregroundServiceRunning) {
                foregroundServiceStartAt = now
                // onStartCommand 里刚 acquire 过一把新锁，续期从此刻重新计时
                lastLockRenewAt = now
            }
        } else if (!should && foregroundServiceRunning) {
            foregroundServiceRunning = false
            ReplyService.stop(getApplication())
        }
        renewKeepAliveLock()
    }

    /**
     * 续一次唤醒锁（5 分钟节流）。放在 syncForegroundService 里一次覆盖所有生成路径
     * —— 包括没有续锁定时器的拟人模式（它是逐条气泡地走 setConvTyping）。
     */
    private fun renewKeepAliveLock() {
        val now = System.currentTimeMillis()
        if (now - lastLockRenewAt < LOCK_RENEW_INTERVAL_MS) return
        if (!foregroundServiceRunning || !ReplyService.isRunning()) return  // 服务没在跑，交给上面的对账逻辑重拉
        lastLockRenewAt = now
        ReplyService.renew(getApplication())
    }

    /** 回复完成且处于后台时，发系统通知（悬浮 + 通知中心，对标微信新消息） */
    private fun notifyReplyIfBackground(title: String, content: String) {
        if (content.isBlank()) return
        // 主动消息是「闹钟把 App 从死里叫醒」这条路径，前台标记不可信，一律照发
        if (appInForeground && !proactiveForceNotify) return
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
    private val _attachmentNotice = MutableStateFlow("")
    val attachmentNotice: StateFlow<String> = _attachmentNotice.asStateFlow()
    fun clearAttachmentNotice() { _attachmentNotice.value = "" }

    private val _selectedModel = MutableStateFlow(
        ModelInfo("mimo-v2.6-flash", "MiMo-V2.6-Flash", Provider.XIAOMI, "Xiaomi深度推理模型，作者自用API，不保证随时在线，可适当白嫖。", supportsWebSearch = true, modelType = ModelType.LANGUAGE, supportsDeepThinking = true, supportsNativeSearch = true, isBuiltIn = true)
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

    private val _customColorArgb = MutableStateFlow(0xFF346C98.toInt())
    val customColorArgb: StateFlow<Int> = _customColorArgb.asStateFlow()

    private val _tempMode = MutableStateFlow(TempMode.AUTO)
    val tempMode: StateFlow<TempMode> = _tempMode.asStateFlow()

    private val _lengthMode = MutableStateFlow(LengthMode.AUTO)
    val lengthMode: StateFlow<LengthMode> = _lengthMode.asStateFlow()

    private val _enableWebSearch = MutableStateFlow(true)
    val enableWebSearch: StateFlow<Boolean> = _enableWebSearch.asStateFlow()
    private val _showSearchSources = MutableStateFlow(false)
    val showSearchSources: StateFlow<Boolean> = _showSearchSources.asStateFlow()

    /**
     * 联网搜索的失败提示（额度用完 / 网络失败）。
     *
     * 空串 = 没问题。UI 侧收到非空就弹一次 Toast 并清掉 —— 联网失败原来是彻底静默的，
     * 用户只能看到「AI 答得不对」，看不出是没额度还是断网。
     */
    private val _webSearchNotice = MutableStateFlow("")
    val webSearchNotice: StateFlow<String> = _webSearchNotice.asStateFlow()
    fun clearWebSearchNotice() { _webSearchNotice.value = "" }

    // 是否显示思考过程（默认关闭：思考中只显示灵动 AI 球）
    private val _showThinking = MutableStateFlow(false)
    val showThinking: StateFlow<Boolean> = _showThinking.asStateFlow()

    private val _appLanguage = MutableStateFlow(AppLanguage.SYSTEM)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _fontSize = MutableStateFlow(FontSize.MEDIUM)
    val fontSize: StateFlow<FontSize> = _fontSize.asStateFlow()

    // 输入框样式（1.0.70）：简洁 = 现状单行 + 全屏卡片；完整 = 两行完整输入框（全屏合二为一）
    private val _inputStyle = MutableStateFlow(InputStyle.COMPACT)
    val inputStyle: StateFlow<InputStyle> = _inputStyle.asStateFlow()

    // 输入框状态（1.0.71）：自动隐藏 = 现状；永久固定 = 不触发隐藏动画（含底部渐变模糊层）
    private val _inputBarState = MutableStateFlow(InputBarState.AUTO_HIDE)
    val inputBarState: StateFlow<InputBarState> = _inputBarState.asStateFlow()

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

    private val _headerBarStyle = MutableStateFlow(HeaderBarStyle.CARD)
    val headerBarStyle: StateFlow<HeaderBarStyle> = _headerBarStyle.asStateFlow()

    // 跟随系统时暗色主题用「深色」还是「黑色」（false=深色 DARK，true=黑色 OLED）
    private val _systemDarkTheme = MutableStateFlow(false)
    val systemDarkTheme: StateFlow<Boolean> = _systemDarkTheme.asStateFlow()

    // 流动炫彩：整幅缓慢流动的柔光渐变背景（进阶视觉选项，默认关闭）
    private val _liquidBackdrop = MutableStateFlow(false)
    val liquidBackdrop: StateFlow<Boolean> = _liquidBackdrop.asStateFlow()

    // 全局默认聊天模式（标准/拟人），设置页切换
    private val _chatMode = MutableStateFlow(ChatMode.STANDARD)
    val chatMode: StateFlow<ChatMode> = _chatMode.asStateFlow()

    // 是否已同意用户协议与免责声明（首次进入门槛）。
    // null = DataStore 还没读出结果（冷启动最初那几十毫秒），**不能当成 false**：
    // 之前默认 false，老用户每次开 App 都会先闪一下协议浮层再被真实值顶掉（1.0.53 修）。
    private val _hasAgreedTerms = MutableStateFlow<Boolean?>(null)
    val hasAgreedTerms: StateFlow<Boolean?> = _hasAgreedTerms.asStateFlow()

    // 最后一次看过「更新了什么」的版本号（空 = 没看过；与当前版本号不等就弹一次）

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    // Empty standard rows with a deletion ledger are sync metadata, not leftover sidebar chats.
    val conversations: StateFlow<List<Conversation>> = _conversations
        .map { rows -> rows.filter { it.messageCount > 0 || it.mode == ChatMode.COMPANION || it.deletedMessageIds.isEmpty() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val pinnedIds = MutableStateFlow<Set<String>>(emptySet())

    // ===== 侧滑搜索：消息级结果（每条命中关键词的消息单独列出，含上下文预览 + 关键词高亮 + 时间） =====
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    // ===== 收藏夹：被收藏的消息（含所属对话，按对话分类展示）=====
    private val _favorites = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val favorites: StateFlow<List<FavoriteItem>> = _favorites.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch(Dispatchers.IO) {
            val q = query.trim()
            val results = if (q.isEmpty()) {
                emptyList()
            } else {
                val items = mutableListOf<SearchResultItem>()
                for (conv in _conversations.value) {
                    for (msg in loadMessages(conv.id)) {
                        // 搜索文本内容；纯文本为空时回退到附件名；图片消息无文本不参与
                        val text = msg.content.replace(Regex("\\s+"), " ").trim()
                            .ifBlank { msg.attachmentName?.trim() ?: "" }
                        val idx = text.indexOf(q, ignoreCase = true)
                        if (idx >= 0) {
                            val snippet = buildSnippet(text, idx, q.length)
                            items.add(SearchResultItem(
                                conversation = conv,
                                messageId = msg.id,
                                preview = snippet.first,
                                matchStart = snippet.second,
                                matchEnd = snippet.third,
                                timestamp = msg.timestamp
                            ))
                        }
                    }
                }
                items.sortedByDescending { it.timestamp }
            }
            _searchResults.value = results
        }
    }

    /** 关键词前后文字预览：关键词前 6 字 + 关键词 + 后 14 字，截断处加省略号；返回 (预览, 关键词起始, 关键词结束) */
    private fun buildSnippet(text: String, matchIdx: Int, matchLen: Int): Triple<String, Int, Int> {
        val before = 6
        val after = 14
        val start = (matchIdx - before).coerceAtLeast(0)
        val end = (matchIdx + matchLen + after).coerceAtMost(text.length)
        val core = text.substring(start, end)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        val preview = prefix + core + suffix
        val matchStart = prefix.length + (matchIdx - start)
        val matchEnd = matchStart + matchLen
        return Triple(preview, matchStart, matchEnd)
    }

    // ===== 搜索跳转：从搜索结果点进去，滚到目标消息并微闪示意 =====
    private val _pendingScrollTarget = MutableStateFlow<String?>(null)
    val pendingScrollTarget: StateFlow<String?> = _pendingScrollTarget.asStateFlow()
    private val _scrollRequestTick = MutableStateFlow(0)
    val scrollRequestTick: StateFlow<Int> = _scrollRequestTick.asStateFlow()

    /** 请求滚动到某条消息（每次调用 tick+1，触发 ChatScreen 响应；同一对话重复点击也生效） */
    fun requestScrollToMessage(messageId: String) {
        _pendingScrollTarget.value = messageId
        _scrollRequestTick.value++
    }

    /** ChatScreen 完成滚动后消费目标，避免重复触发 */
    fun consumeScrollTarget() {
        _pendingScrollTarget.value = null
    }

    // ===== 内置基础模型（随应用发布，不可删除）=====
    // Beta 1.0.96: built-in model definitions are fixed. The legacy preference table now
    // contributes only the global per-model reasoning usage switch, never editable capabilities.
    private val builtInLanguageModels = listOf(
        ModelInfo("mimo-v2.6-flash", "MiMo-V2.6-Flash", Provider.XIAOMI, "Xiaomi深度推理模型，作者自用API，不保证随时在线，可适当白嫖。", supportsWebSearch = true, modelType = ModelType.LANGUAGE, supportsDeepThinking = true, supportsNativeSearch = true, isBuiltIn = true)
    )
    private val builtInVisualModels = listOf(
        ModelInfo("ep-20260629143810-ffvjl", "Doubao-Seedream-5.0-Lite", Provider.DOUBAO, "Volcano Engine轻量级生图模型，作者自用API，不保证随时在线，可适当白嫖。", modelType = ModelType.VISUAL, isBuiltIn = true)
    )
    private val builtInTtsModels = listOf(
        ModelInfo("MiMo-V2.5-TTS", "MiMo-V2.5-TTS", Provider.XIAOMI, "小米语音合成模型，作者自用API，不保证随时在线，可适当白嫖。", modelType = ModelType.TTS, isBuiltIn = true)
    )

    /** Legacy storage key: only deepThinkingDefault is consumed as a usage preference. */
    private val _builtInParams = MutableStateFlow<Map<String, ModelInfo>>(emptyMap())

    private fun builtInList(type: ModelType, params: Map<String, ModelInfo>): List<ModelInfo> {
        val base = when (type) {
            ModelType.LANGUAGE -> builtInLanguageModels
            ModelType.VISUAL -> builtInVisualModels
            ModelType.TTS -> builtInTtsModels
            else -> emptyList()
        }
        return base.map { b ->
            com.freechat.data.ModelAccessPolicy.withUsagePreference(b, params["${b.modelType}|${b.id}"])
        }
    }

    private fun mergedBuiltIn(type: ModelType): List<ModelInfo> = builtInList(type, _builtInParams.value)

    /** Global usage preference only: there is deliberately no public built-in edit API. */
    private fun setBuiltInDeepThinkDefault(target: ModelInfo, on: Boolean) {
        if (!target.isBuiltIn || !target.supportsDeepThinking) return
        val k = "${target.modelType}|${target.id}"
        _builtInParams.value = _builtInParams.value + (k to target.copy(deepThinkingDefault = on))
        viewModelScope.launch { settingsRepo.saveBuiltInModelParams(_builtInParams.value) }
        refreshSelectedFromCatalog()
    }

    /**
     * 选中项存的是 ModelInfo 快照 —— 参数覆盖变了要重取，否则请求用的还是旧参数。
     * 1.0.75：改自定义模型也要刷（updateCustomModel 同样会改深度思考开关/能力位，
     * 不刷的话全局设置页的开关会「点完就弹回去」——读的还是旧快照）。查库用 modelsOfType
     * （内置+自定义都在里面），不再是只查内置。
     */
    private fun refreshSelectedFromCatalog() {
        modelsOfType(ModelType.LANGUAGE).find { it.id == _selectedModel.value.id }?.let { _selectedModel.value = it }
        modelsOfType(ModelType.VISUAL).find { it.id == _selectedVisualModel.value.id }?.let { _selectedVisualModel.value = it }
        _selectedVisionModel.value?.let { v ->
            modelsOfType(ModelType.VISION).find { it.id == v.id }?.let { _selectedVisionModel.value = it }
        }
    }

    // 各类型模型列表（内置 + 用户自定义），UI 读这些 StateFlow
    val languageModels: StateFlow<List<ModelInfo>> = combine(_customModels, _builtInParams) { customs, params ->
        builtInList(ModelType.LANGUAGE, params) + customs.filter { it.modelType == ModelType.LANGUAGE }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, builtInLanguageModels)

    val visualModels: StateFlow<List<ModelInfo>> = combine(_customModels, _builtInParams) { customs, params ->
        builtInList(ModelType.VISUAL, params) + customs.filter { it.modelType == ModelType.VISUAL }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, builtInVisualModels)

    val visionModels: StateFlow<List<ModelInfo>> = _customModels.map { customs ->
        customs.filter { it.modelType == ModelType.VISION }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ttsModels: StateFlow<List<ModelInfo>> = combine(_customModels, _builtInParams) { customs, params ->
        builtInList(ModelType.TTS, params) + customs.filter { it.modelType == ModelType.TTS }
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
        mergedBuiltIn(type) + _customModels.value.filter { it.modelType == type }

    // 兼容旧引用
    @Deprecated("Use languageModels", ReplaceWith("languageModels"))
    val availableModels get() = languageModels.value

    // 文件位置一律问 LocalStore —— 以前各处自己拼 File(filesDir, ...)，
    // 两个来源早晚会分叉（少一个斜杠就是另一个文件），而且写入必须统一走它才拿得到锁
    private val conversationsFile: File
        get() = LocalStore.conversationsFile()

    // ===== 每对话「新规则」覆盖设置：convId → 覆盖项（null=跟随全局默认） =====
    private val _perConvSettings = MutableStateFlow<Map<String, PerConvSettings>>(emptyMap())
    val perConvSettings: StateFlow<Map<String, PerConvSettings>> = _perConvSettings.asStateFlow()

    // ========== 云端同步的对接 ==========
    // 同步引擎不直接读写磁盘，而是从这里过一道 —— 因为对话数组和"当前这个对话的消息"
    // 在内存里各有一份，绕开它们直接改盘的话，下一次本地保存就会把同步刚写的东西
    // 整个盖掉，而且不报任何错：用户看到的是「同步完成」，然后改动凭空消失。

    /** 对话数组有没有从盘上装进内存。没装完之前同步必须回落到读盘 */
    @Volatile
    private var conversationsReady = false

    /** 每对话「新规则」那张表有没有装进内存。没装完之前同步必须回落到读盘（理由见 syncRelay 里那条注释） */
    @Volatile
    private var perConvReady = false

    /**
     * 声明位置必须**在 `init` 之前**：Kotlin 按书写顺序初始化字段，
     * 而 `init` 里要 `Relay.install(syncRelay)` —— 声明摆到下面去，
     * 那一句读到的就是个还没赋值的字段，编译期直接报「must be initialized」。
     */
    private val syncRelay: Relay.Impl = object : Relay.Impl {

        override suspend fun conversations(): List<Conversation> =
            if (conversationsReady) _conversations.value else Relay.conversationsFromDisk()

        /**
         * 收下的是**一批操作**而不是"新的对话数组"，这一点是刻意的：
         * 引擎在 IO 线程上算合并用的是它读盘那一刻的本地数据，而用户完全可能
         * 在这中间又建了一条对话。直接覆盖整个数组，那条新对话就没了。
         */
        override suspend fun applyConversations(ops: List<Relay.ConvOp>) {
            withContext(Dispatchers.Main) {
                LocalStore.suspendApply {
                    val list = _conversations.value.toMutableList()
                    val gone = ops.filterIsInstance<Relay.ConvOp.Remove>().map { it.id }.toSet()
                    for (op in ops) when (op) {
                        is Relay.ConvOp.Upsert -> {
                            val idx = list.indexOfFirst { it.id == op.conv.id }
                            val newlyDeleted = op.conv.deletedMessageIds.toSet() - (list.getOrNull(idx)?.deletedMessageIds.orEmpty().toSet())
                            if (newlyDeleted.isNotEmpty()) cancelConversationGeneration(op.conv.id)
                            val c = if (idx >= 0) Merge.mergeConv(list[idx], op.conv).conv else op.conv
                            MessageDeletion.register(c)
                            if (idx >= 0) list[idx] = c else list.add(0, c)
                        }
                        is Relay.ConvOp.Remove -> list.removeAll { it.id == op.id }
                    }
                    val currentGone = _currentConversationId.value in gone
                    // 别的设备把对话删了：本机的头像/形象图、"新规则"那条记录也一起收掉
                    // （云端那两条墓碑会由引擎自己走 applyChange，这里管的是**本机**的残留）
                    if (gone.isNotEmpty()) {
                        gone.forEach(::cancelConversationGeneration)
                        for (conv in _conversations.value.filter { it.id in gone }) {
                            deleteConvOwnedFiles(conv, list)
                        }
                        val table = _perConvSettings.value
                        if (table.keys.any { it in gone }) {
                            _perConvSettings.value = table - gone
                            savePerConvSettings()
                        }
                    }
                    _conversations.value = sortConversations(list)
                    conversationsReady = true
                    saveConversations()
                    for (op in ops.filterIsInstance<Relay.ConvOp.Upsert>()) {
                        if (MessageDeletion.deletedIds(op.conv.id).isNotEmpty()) {
                            saveMessages(op.conv.id, loadMessages(op.conv.id))
                            memoryManager.purgeDeleted(op.conv.id)
                            _perConvSettings.value[op.conv.id]?.let { old ->
                                val clean = MessageDeletion.atmosphere(op.conv.id, old)
                                if (clean != old) {
                                    _perConvSettings.value = _perConvSettings.value + (op.conv.id to clean)
                                    savePerConvSettings()
                                }
                            }
                            if (_currentConversationId.value == op.conv.id) {
                                _messages.value = MessageDeletion.messages(op.conv.id, _messages.value)
                            }
                        }
                    }
                    if (currentGone) {
                        // 另一台设备把正在看的这条删了 —— 界面得跟着退出去，
                        // 否则用户对着一屏已经没有归属的消息继续打字
                        _currentConversationId.value = null
                        _messages.value = emptyList()
                        _isLoading.value = false
                        _isTyping.value = false
                    }
                }
            }
        }

        /** 消息一律从盘上读：内存里那份可能正吐字吐到一半，不适合拿去合并/上传 */
        override suspend fun messages(convId: String): List<Message> = loadMessages(convId)

        override suspend fun applyMessages(convId: String, incoming: List<Message>) {
            withContext(Dispatchers.Main) {
                LocalStore.suspendApply {
                    // 落盘的这一刻再并一次集，中途新发的那条自然就被保住了
                    val merged = applyFavoriteOverrides(Merge.mergeMessages(loadMessages(convId), incoming, MessageDeletion.deletedIds(convId)))
                    saveMessages(convId, merged)
                    if (_currentConversationId.value == convId) _messages.value = merged
                    refreshConvMessageCount(convId, merged.size)
                }
            }
        }

            override suspend fun dropMessages(convId: String) {
                withContext(Dispatchers.Main) {
                    LocalStore.suspendApply {
                        LocalStore.deleteFile(messagesFile(convId))
                        if (_currentConversationId.value == convId) _messages.value = emptyList()
                        refreshConvMessageCount(convId, 0)
                    }
                }
            }

        /**
         * 「新规则」的三条都**以盘上那份为准**，改完再让内存跟着刷一遍。
         *
         * 不直接改 `_perConvSettings` 是有原因的：它是**一整张表**，而同步拿到的是**一行**。
         * 在本机那份还没从盘上装进内存的时候（App 刚起来、或 `ProactiveService` 自己 new 的那个
         * ViewModel 还没跑完 init）拿内存里的空表去合并再整份写回，会把**其它所有对话的新规则一次抹光**。
         * 走 `PerConvStore` 就是老老实实"读-改-写"，不管内存醒没醒都不会丢东西。
         */
        override suspend fun perConv(convId: String): PerConvSettings? =
            if (perConvReady) _perConvSettings.value[convId] else PerConvStore.load()[convId]

        override suspend fun applyPerConv(convId: String, incoming: JsonObject) {
            withContext(Dispatchers.Main) {
                LocalStore.suspendApply {
                    LocalStore.locked {
                        val cur = PerConvStore.load()[convId] ?: PerConvSettings()
                        PerConvStore.put(convId, MessageDeletion.atmosphere(convId, PerConvBridge.mergeIntoLocal(cur, incoming)))
                    }
                    if (perConvReady) _perConvSettings.value = PerConvStore.load()
                }
            }
        }

        override suspend fun removePerConv(convId: String) {
            withContext(Dispatchers.Main) {
                LocalStore.suspendApply {
                    LocalStore.locked { PerConvStore.remove(convId) }
                    if (perConvReady) _perConvSettings.value = PerConvStore.load()
                }
            }
        }
        }

    init {
        liveRef = java.lang.ref.WeakReference(this)
        // 把"内存里那两份真相"交给同步引擎（见 syncRelay）。装在这里而不是 Application 里：
        // 没有 ViewModel 的时候引擎自己会回落到读盘，两边不会打架。
        Relay.install(syncRelay)
        // 默认值换代的一次性迁移不在这儿另起协程 —— 它挂在 SettingsRepository 那几条
        // 受影响的 Flow 的 onStart 上（见 afterDefaultMigration），另起协程挡不住
        // 「先按新默认值画一帧」那个窗口。
        viewModelScope.launch {
            settingsRepo.customModels.collect { models -> _customModels.value = models }
        }
        viewModelScope.launch {
            settingsRepo.builtInModelParams.collect { params -> _builtInParams.value = params }
        }
        viewModelScope.launch {
            combine(settingsRepo.selectedModelId, _customModels, _builtInParams) { savedId, _, _ -> savedId }
                .collect { savedId ->
                    _selectedModel.value = modelsOfType(ModelType.LANGUAGE).find { it.id == savedId }
                        ?: mergedBuiltIn(ModelType.LANGUAGE).first()
                }
        }
        viewModelScope.launch {
            combine(settingsRepo.selectedVisualModelId, _customModels, _builtInParams) { savedId, _, _ -> savedId }
                .collect { savedId ->
                    _selectedVisualModel.value = modelsOfType(ModelType.VISUAL).find { it.id == savedId }
                        ?: mergedBuiltIn(ModelType.VISUAL).first()
                }
        }
        viewModelScope.launch {
            combine(settingsRepo.selectedVisionModelId, _customModels, _builtInParams) { savedId, _, _ -> savedId }
                .collect { savedId ->
                    _selectedVisionModel.value = modelsOfType(ModelType.VISION).find { it.id == savedId }
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
            settingsRepo.customColorArgb.collect { argb -> _customColorArgb.value = argb }
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
            settingsRepo.showSearchSources.collect { show -> _showSearchSources.value = show }
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
            settingsRepo.inputStyleOrdinal.collect { ordinal ->
                _inputStyle.value = InputStyle.entries.getOrElse(ordinal) { InputStyle.COMPACT }
            }
        }
        viewModelScope.launch {
            settingsRepo.inputBarStateOrdinal.collect { ordinal ->
                _inputBarState.value = InputBarState.entries.getOrElse(ordinal) { InputBarState.AUTO_HIDE }
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
            settingsRepo.headerBarStyle.collect { style -> _headerBarStyle.value = style }
        }
        viewModelScope.launch {
            settingsRepo.systemDarkTheme.collect { isBlack -> _systemDarkTheme.value = isBlack }
            settingsRepo.chatModeOrdinal.collect { o -> _chatMode.value = ChatMode.entries.getOrElse(o) { ChatMode.STANDARD } }
        }
        viewModelScope.launch {
            settingsRepo.liquidBackdrop.collect { on -> _liquidBackdrop.value = on }
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
            conversationsReady = true
            // 同步引擎的 diff 基线就是"启动时盘上这一份"。
            // 不先立个基线，第一次 diff 会把「从 null 变成一整份」当成全部对话都改了，
            // 白白全推一遍。
            Adapter.primeConvSnapshot(convs)
            refreshFavorites()
            // 1.0.99.3：启动即清存量「冲突副本」套娃（同步轮里还有一道，见 SyncEngine）
            cleanupStaleConflictCopies()
        }
        viewModelScope.launch {
            _perConvSettings.value = withContext(Dispatchers.IO) { loadPerConvSettings() }
            perConvReady = true
        }
        viewModelScope.launch { seedBuiltInAssistant() }
    }

    /**
     * 第一次启动时把内置的「Claude风格助理」建出来。
     *
     * 两个闩分工不同，别混：
     *   · `claude_seeded`  —— 生成过没有。单向置真。
     *   · `claude_deleted` —— **用户亲手删过**没有。单向置真。
     *
     * 只有两个都真才是「删了就没了」，直接返回。只 seeded 不 deleted 而盘上又没有，
     * 那就是**丢了**（不是被删的），要补建回来。
     * 早期只有一个 seeded 闩，丢了也当成"用户删的"，于是永远补不回来，用户只会看到「找不到」。
     *
     * ⚠ 这里原先举的例子是「网页端不认识 `builtInAssistant`，推回云端时把它丢了」——
     * **那个例子已经过时了**（2026-09-19 核实）。网页端的同步层
     * （`convToWire` / `convFromWire` / `mergeConv`）全是展开式复制，未声明的键
     * 一样原样带过去，`.check/marker-survives.ts` 有实测；而且网页端现在
     * **认这个字段**了（`web/src/core/builtin.ts`，新规则页照样只留模型/联网/思考）。
     * 修复路径本身留着是对的 —— 早年那个丢字段的版本确实造出过这种数据。
     *
     * 有两个地方不能省：
     *
     * 1. **等设置读完**。标志位在 DataStore 里，收集器是异步的；不等就读的话，
     *    明明是"已经生成过"的老用户，这一轮也会被当成第一次，于是每启动一次多一条。
     * 2. **等第一轮同步跑完**（登录态下）。换设备登录时，云端那条内置对话是随
     *    同步流回来的 —— 在它到之前就抢先建一条本地的，用户侧栏里就会出现两个
     *    「Claude风格助理」，而且两条都是"真"的，删哪条都不对。
     */
    private suspend fun seedBuiltInAssistant() {
        awaitSettingsLoaded()
        val seeded = settingsRepo.claudeSeeded.first()
        val userDeleted = settingsRepo.claudeDeleted.first()
        if (seeded && userDeleted) {
            // 用户自己删的 —— 留住这个决定，不再冒出来
            return
        }

        // 登录态下先把第一轮同步等出来，免得和云端那条撞车
        if (com.freechat.sync.Session.peekAuth() != null) {
            var waited = 0
            while (com.freechat.sync.SyncEngine.status.value.lastSyncAt <= 0L && waited < 20_000) {
                kotlinx.coroutines.delay(500)
                waited += 500
            }
        }

        // 等完再重新读一遍盘：这一轮里可能已经同步下来一条了
        val onDisk = withContext(Dispatchers.IO) { loadConversations() }
        if (onDisk.any { it.builtInAssistant == com.freechat.data.BuiltInAssistant.KEY }) {
            settingsRepo.saveClaudeSeeded(true)
            return
        }
        // seeded 且不是用户删的，但盘上没有 → 它是丢了，往下走补建一条
        //（这就是那条一次性的修复路径：老用户更新完这版，缺失的内置助理会自己回来）

        // 补建之前先认一次「可能只是标记被抹掉的那一条」：旧版本存消息时会把 builtInAssistant 丢掉
        // （见 persistConversationMessages），用户的助理记录还在、标题也还叫这个名，只是不再被认出。
        // 认回来，而不是凭空多出一条 —— 否则侧栏里会出现两个「Claude风格助理」，删哪个都不对。
        // 标题已经被 AI 起过名的（"询问软件及开发者信息"那种）认不出来，那就照下面新建一条，
        // 老的那条留在侧栏由用户自己处理。
        val assistantNames = setOf(
            com.freechat.i18n.LocaleManager.strings().claudeAssistantName,
            "Claude风格助理"  // 建的时候写死的中文名，换过语言的用户也得认
        )
        val adopted = onDisk.firstOrNull {
            it.builtInAssistant.isBlank() && it.title in assistantNames
        }
        val now = System.currentTimeMillis()
        val builtIn: Conversation
        val merged: List<Conversation>
        if (adopted != null) {
            builtIn = adopted.copy(isPinned = true, builtInAssistant = com.freechat.data.BuiltInAssistant.KEY)
            merged = onDisk.map { if (it.id == adopted.id) builtIn else it }
        } else {
            builtIn = Conversation(
                title = "Claude风格助理",
                createdAt = now,
                updatedAt = now,
                mode = ChatMode.STANDARD,
                isPinned = true,
                builtInAssistant = com.freechat.data.BuiltInAssistant.KEY
            )
            merged = onDisk + builtIn
        }
        withContext(Dispatchers.IO) { LocalStore.writeText(conversationsFile, gson.toJson(merged)) }
        // 置顶走的是设置里那个集合（排序和 isPinned 都从它取），只给字段赋值是没用的
        val pinned = settingsRepo.pinnedConversationIds.first() + builtIn.id
        settingsRepo.savePinnedIds(pinned)
        pinnedIds.value = pinned
        _conversations.value = sortConversations(merged)
        settingsRepo.saveClaudeSeeded(true)
    }

    override fun onCleared() {
        // 只在还是自己的时候摘 —— 配置变更会重建 ViewModel，旧的那个不该把新的摘掉
        Relay.uninstall(syncRelay)
        super.onCleared()
    }

    private fun sortConversations(list: List<Conversation>): List<Conversation> {
        val pinned = pinnedIds.value
        return list.sortedWith(
            compareByDescending<Conversation> { pinned.contains(it.id) || it.mode == ChatMode.COMPANION }
                .thenByDescending { it.updatedAt }
        )
    }

    // ========== 每对话设置（新规则）==========
    // 这份**只同步一半**（两端同一份切法，见 [PerConvBridge]）：
    // 联网/思考过程/记忆总结/温度/长度这几项跟着对话上云；语言/识图/形象/语音那几个
    // **模型 id 是本机的**（另一台设备上没有这些模型，传过去只会选中一个点不通的项），留在本机。
    // 文件读写统一走 PerConvStore —— 同步引擎也要动它，两个入口各写各的早晚分叉
    private fun loadPerConvSettings(): Map<String, PerConvSettings> = PerConvStore.load()

    private fun savePerConvSettings() {
        PerConvStore.save(_perConvSettings.value)
    }

    fun getPerConvSettings(convId: String): PerConvSettings =
        MessageDeletion.atmosphere(convId, _perConvSettings.value[convId] ?: PerConvSettings())

    fun updatePerConvSettings(convId: String, settings: PerConvSettings) {
        _perConvSettings.value = _perConvSettings.value.toMutableMap().apply { put(convId, settings) }
        savePerConvSettings()
    }

    /**
     * 当前对话**真正生效**的「显示思考过程」：新规则（每对话覆盖）优先，这条对话没设才跟随全局。
     *
     * 为什么不能直接用 [showThinking]：那个是**全局默认**，而每对话覆盖只在生成期间临时写进去、
     * 生成一结束就恢复（见 [applyPerConvSettings]）。展示是个纯 UI 判定，得按对话算，
     * 不能跟着「生成」这个生命周期走 —— 否则「全局开、这条对话关」的对话在回复落地后又会把
     * 思考过程显示出来（用户 1.0.51 报的就是这个）。
     */
    val effectiveShowThinking: StateFlow<Boolean> =
        combine(combine(_showThinking, _perConvSettings, _currentConversationId) { global, per, convId ->
            Triple(global, convId?.let { per[it] }, convId)
        }, combine(_selectedModel, languageModels, _conversations) { model, models, conversations ->
            Triple(model, models, conversations)
        }) { preference, catalog ->
            val (global, per, convId) = preference
            val (selected, models, conversations) = catalog
            val character = conversations.find { it.id == convId }?.characterProfile
            val modelId = if (character != null) character.languageModelId else per?.languageModelId
            val model = models.find { it.id == modelId } ?: selected
            com.freechat.data.SettingsPresentationPolicy.reasoningVisible(
                requested = per?.showThinking ?: global,
                deepEnabled = (character?.deepThinkingMode ?: deepThinkFor(per, model)) && model.supportsDeepThinking,
                supported = model.supportsThinking,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun globalModels() = RequestModels(_selectedModel.value, _selectedVisualModel.value, _selectedVisionModel.value)

    private fun resolveModels(convId: String?, character: CharacterProfile? =
        convId?.let { id -> _conversations.value.find { it.id == id }?.characterProfile }): RequestModels =
        ModelSelectionResolver.resolve(globalModels(),
            modelsOfType(ModelType.LANGUAGE) + modelsOfType(ModelType.VISUAL) + modelsOfType(ModelType.VISION),
            convId?.let { _perConvSettings.value[it] }, character)

    private suspend fun requestModels(): RequestModels = coroutineContext[RequestModels] ?: globalModels()

    // Non-model presentation preferences retain the existing generation lifecycle.
    // Models use immutable coroutine-local bindings, NEVER temporary global writes/restores.
    private data class SettingsSnapshot(
        val search: Boolean, val showThinking: Boolean, val temp: TempMode,
        val length: LengthMode, val autoMem: Boolean
    )

    private fun applyPerConvSettings(convId: String?): SettingsSnapshot {
        val snap = SettingsSnapshot(
            _enableWebSearch.value, _showThinking.value, _tempMode.value,
            _lengthMode.value, _autoSummarizeMemory.value
        )
        val over = convId?.let { _perConvSettings.value[it] } ?: return snap
        over.enableWebSearch?.let { _enableWebSearch.value = it }
        over.showThinking?.let { _showThinking.value = it }
        over.autoSummarizeMemory?.let { _autoSummarizeMemory.value = it }
        over.tempModeOrdinal?.let { _tempMode.value = TempMode.entries.getOrElse(it) { TempMode.AUTO } }
        over.lengthModeOrdinal?.let { _lengthMode.value = LengthMode.entries.getOrElse(it) { LengthMode.AUTO } }
        return snap
    }

    private fun restoreSettings(snap: SettingsSnapshot) {
        _enableWebSearch.value = snap.search
        _showThinking.value = snap.showThinking
        _tempMode.value = snap.temp
        _lengthMode.value = snap.length
        _autoSummarizeMemory.value = snap.autoMem
    }

    // ========== 问候语 ==========
    fun generateGreeting(s: com.freechat.i18n.AppStrings): String {   // 语言必须由调用方传入，默认中文会坑非中文用户
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val weekend = dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY

        val greetings = when {
            hour in 2..5 -> s.greetingsLateNight            // 凌晨 2-5
            hour in 6..7 -> s.greetingsEarlyMorning         // 早上 6-7
            hour in 8..11 -> {                              // 上午 8-11
                if (weekend) s.greetingsMorningWeekend
                else s.greetingsMorningWeekday
            }
            hour in 12..13 -> s.greetingsNoon               // 中午 12-13
            hour in 14..17 -> s.greetingsAfternoon           // 下午 14-17
            hour in 18..21 -> s.greetingsEvening             // 晚上 18-21
            else -> s.greetingsNight                         // 深夜 22-1（跨零点）
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
        if (!com.freechat.data.ModelAccessPolicy.canEdit(model)) return
        _customModels.value = _customModels.value.filterNot { it.id == model.id && it.modelType == model.modelType } + model
        persistCustomModels()
    }
    fun updateCustomModel(oldId: String, modelType: ModelType, newModel: ModelInfo) {
        if (!com.freechat.data.ModelAccessPolicy.canEdit(newModel)) return
        if (_customModels.value.none { it.id == oldId && it.modelType == modelType && !it.isBuiltIn }) return
        _customModels.value = _customModels.value.map { if (it.id == oldId && it.modelType == modelType && !it.isBuiltIn) newModel else it }
        persistCustomModels()
        // 1.0.75：选中项快照跟着刷新（深度思考开关/能力位都在模型档案上，不刷会读旧值）
        refreshSelectedFromCatalog()
    }
    fun deleteCustomModel(modelId: String, modelType: ModelType) {
        if (_customModels.value.none { it.id == modelId && it.modelType == modelType && !it.isBuiltIn }) return
        _customModels.value = _customModels.value.filterNot { it.id == modelId && it.modelType == modelType && !it.isBuiltIn }
        persistCustomModels()
        // 删除的是当前选中的模型则回退
        when (modelType) {
            ModelType.LANGUAGE -> if (_selectedModel.value.id == modelId) _selectedModel.value = mergedBuiltIn(ModelType.LANGUAGE).first()
            ModelType.VISUAL -> if (_selectedVisualModel.value.id == modelId) _selectedVisualModel.value = mergedBuiltIn(ModelType.VISUAL).first()
            ModelType.VISION -> if (_selectedVisionModel.value?.id == modelId) _selectedVisionModel.value = null
            ModelType.TTS -> if (_voiceModel.value == modelId) _voiceModel.value = mergedBuiltIn(ModelType.TTS).first().id
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

    fun setCustomColorTheme(argb: Int) {
        _customColorArgb.value = argb
        _colorTheme.value = ColorTheme.CUSTOM
        viewModelScope.launch { settingsRepo.saveCustomTheme(argb) }
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
    fun setShowSearchSources(enabled: Boolean) {
        _showSearchSources.value = enabled
        viewModelScope.launch { settingsRepo.saveShowSearchSources(enabled) }
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
    fun setInputStyle(style: InputStyle) {
        _inputStyle.value = style
        viewModelScope.launch { settingsRepo.saveInputStyle(style.ordinal) }
    }
    fun setInputBarState(state: InputBarState) {
        _inputBarState.value = state
        viewModelScope.launch { settingsRepo.saveInputBarState(state.ordinal) }
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
    fun setHeaderBarStyle(style: HeaderBarStyle) {
        _headerBarStyle.value = style
        viewModelScope.launch { settingsRepo.saveHeaderBarStyle(style) }
    }
    fun setSystemDarkTheme(isBlack: Boolean) {
        _systemDarkTheme.value = isBlack
        viewModelScope.launch { settingsRepo.saveSystemDarkTheme(isBlack) }
    }
    fun setLiquidBackdrop(enabled: Boolean) {
        _liquidBackdrop.value = enabled
        viewModelScope.launch { settingsRepo.saveLiquidBackdrop(enabled) }
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
    /**
     * 这条对话真正会用的语音模型 id：**每对话覆盖优先，没设（或覆盖指向的模型已从库里删掉）就跟随全局**。
     * 与 [applyPerConvSettings] 那套是同一个语义，只是语音不在生成快照里（朗读/识别都发生在生成之外），
     * 所以按 convId 现算，不去动全局状态。
     */
    private fun effectiveTtsModelId(convId: String?): String {
        val over = convId?.let { _perConvSettings.value[it]?.ttsModelId }
        return if (!over.isNullOrBlank() && modelsOfType(ModelType.TTS).any { it.id == over }) over else _voiceModel.value
    }

    private fun effectiveAsrModelId(convId: String?): String {
        val over = convId?.let { _perConvSettings.value[it]?.asrModelId }
        return if (!over.isNullOrBlank() && modelsOfType(ModelType.ASR).any { it.id == over }) over else _asrModel.value
    }

    /** 朗读某条 AI 消息（合成 + 播放；内置 MiMo / 用户自定义 OpenAI TTS）。用**这条对话**的语音模型 */
    fun speakMessage(messageId: String, text: String) {
        val convId = _currentConversationId.value
        viewModelScope.launch {
            val tts = currentTtsModel(convId)
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

    private fun currentTtsModel(convId: String? = null): ModelInfo? =
        modelsOfType(ModelType.TTS).find { it.id == effectiveTtsModelId(convId) }

    private fun currentAsrModel(convId: String? = null): ModelInfo? =
        modelsOfType(ModelType.ASR).find { it.id == effectiveAsrModelId(convId) }

    /** 这条对话有没有可用的语音识别模型（每对话覆盖优先）。聊天页「按住说话」在开工前用它挡一道 */
    fun hasAsrModel(convId: String?): Boolean = currentAsrModel(convId) != null

    /** 按住说话语音识别（用**这条对话**的 ASR 模型；无模型返回 null） */
    suspend fun recognizeVoiceInput(wav: ByteArray): String? {
        val asr = currentAsrModel(_currentConversationId.value) ?: return null
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
            title = character.name.ifBlank { com.freechat.i18n.LocaleManager.strings().newChat },
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
        // 主动智能：刚把开关关掉就立刻撤掉已挂的闹钟，别等到下次回复才清
        // （sig=null：开关还开着时「保持原计划不变」，关掉了就走取消分支）
        proactiveScope.launch { applyProactiveSignalBlocking(convId, profile, null) }
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
        deleteConversationCore(conversation)
        saveConversations()
        refreshFavorites()
    }

    /**
     * 批量删除对话（侧滑页多选）：逐条走 [deleteConversationCore] 的清理（图片文件 / 消息文件 /
     * 记忆 / 在途生成），但**落盘与收藏夹刷新只做一次**。
     * 不复用 [deleteConversation] 循环：那个方法每调一次就全量重写会话文件 + 重扫收藏夹，
     * 选十几条会有十几次全文件写入，纯属浪费。
     */
    fun deleteConversations(conversations: List<Conversation>) {
        if (conversations.isEmpty()) return
        conversations.forEach { deleteConversationCore(it) }
        saveConversations()
        refreshFavorites()
    }

    /**
     * 1.0.99.3：清理存量「冲突副本」套娃（见 [com.freechat.sync.ConflictCopyCleanup]）。
     *
     * 启动加载完对话就跑 —— 用户打开 App 那一刻屏幕就干净，不用等同步轮。
     * **有聊天记录的副本一律不删**；删除走 [deleteConversations] 完整路径
     * （图片/消息/记忆/新规则/收藏全清 + 落盘触发同步 diff 推墓碑）。
     */
    private suspend fun cleanupStaleConflictCopies() {
        val convs = _conversations.value
        val stale = withContext(Dispatchers.IO) {
            com.freechat.sync.ConflictCopyCleanup.staleCopies(convs) { c ->
                runCatching { loadMessages(c.id).isNotEmpty() }.getOrDefault(false)
            }
        }.toSet()
        if (stale.isEmpty()) return
        deleteConversations(convs.filter { it.id in stale })
    }

    /** 删除单个对话的实际清理逻辑（不含落盘与收藏夹刷新，见 [deleteConversation] / [deleteConversations]） */
    private fun deleteConversationCore(conversation: Conversation) {
        // 内置助理被**用户亲手删掉**了 —— 单独记一笔。
        // `claude_seeded` 这个闩只说明「生成过」，说明不了「是丢的还是被删的」，
        // 而这两件事该有完全不同的下场：丢了要补回来，删了就得永远消失（见 seedBuiltInAssistant）。
        if (conversation.builtInAssistant == com.freechat.data.BuiltInAssistant.KEY) {
            viewModelScope.launch { settingsRepo.saveClaudeDeleted(true) }
        }
        // 删除对话时取消其在途生成（若有），避免后台 job 再写回已删除的对话
        companionPipelines.remove(conversation.id)?.job?.cancel()
        convLoading.remove(conversation.id)
        convTyping.remove(conversation.id)
        // 主动智能：对话都没了，别再定时把它唤醒（否则到点会去读一个不存在的会话）
        proactiveJobs.remove(conversation.id)?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ProactiveScheduler.cancel(getApplication(), conversation.id) }
        }
        if (_currentConversationId.value == conversation.id) streamJob?.cancel()
        val msgFile = messagesFile(conversation.id)
        if (msgFile.exists()) {
            // 清理该会话引用过的图片文件（避免删除对话后图片残留占用空间）
            loadMessages(conversation.id).forEach { m ->
                m.imagePaths.forEach { runCatching { File(it).delete() } }
            }
            LocalStore.deleteFile(msgFile)
        }
        memoryManager.delete(conversation.id)
        // 「新规则」那条记录也摘掉，并**点名让同步把云端那条一起清掉**。
        // 光靠"文件改了自动 diff"不够：这台设备可能压根没有它的定制记录，而云端那条是
        // 另一台设备推上去的 —— 那条云端数据不能因为"我这边没有"就永远留在服务器上
        _perConvSettings.value.let { table ->
            if (table.containsKey(conversation.id)) {
                _perConvSettings.value = table - conversation.id
                savePerConvSettings()
            }
        }
        Adapter.markDirty(Adapter.objKey(com.freechat.sync.SyncKind.PCSET, conversation.id))
        _conversations.value = _conversations.value.filter { it.id != conversation.id }
        // 角色头像 / 形象参考图：只有**没有别的对话在用**才删（冲突副本会和原对话共用同一张头像）
        deleteConvOwnedFiles(conversation, _conversations.value)
        if (_currentConversationId.value == conversation.id) {
            _currentConversationId.value = null
            _messages.value = emptyList()
            _isLoading.value = false
            _isTyping.value = false
        }
    }

    /**
     * 删对话时把它**独占**的那些本机文件一起清掉：角色头像、形象参考图（含 1.540 前那张单图）。
     *
     * 「独占」两个字是认真的，不是修辞：两台设备各改过一次同一条对话的设定时会产生一份冲突副本
     * （见 `Merge.mergeConv`），**副本和原对话共用同一个 avatarPath** —— 天真的"删对话就删图"
     * 会把另一条还在用的头像删掉，那条对话的头像当场变成灰人。
     *
     * 另一道闸：只删 App 私有目录里的文件。万一日后混进一个外部路径（相册原图之类），
     * 宁可留个孤儿文件，也不能去动用户自己目录里的东西。
     */
    private fun deleteConvOwnedFiles(conv: Conversation, survivors: List<Conversation>) {
        val p = conv.characterProfile ?: return
        val used = survivors.asSequence()
            .mapNotNull { it.characterProfile }
            .flatMap { c -> sequenceOf(c.avatarPath, c.appearanceImagePath) + c.appearanceImagePaths.asSequence() }
            .filter { it.isNotBlank() }
            .toHashSet()
        val mine = listOf(p.avatarPath, p.appearanceImagePath) + p.appearanceImagePaths
        val home = runCatching { LocalStore.filesDir().absolutePath }.getOrNull() ?: return
        for (path in mine) {
            if (path.isBlank() || path in used || !path.startsWith(home)) continue
            runCatching { File(path).delete() }
        }
    }

    /**
     * 收藏状态的「用户意图」覆盖表（messageId → favorited）。
     *
     * 为什么需要：生成中的请求在开跑时就抓了一份历史快照（working），结束时会把整份快照
     * 落盘、并把 _messages 整体换回快照。用户在生成期间改的收藏标记会被这份旧快照抹掉
     * —— 表现就是「刚取消的收藏，AI 回复一出来又自己变回收藏了」。
     * 所以每次用户明确改收藏都记一笔，落盘前套回去，用户的最后一次操作永远说了算。
     */
    private val favoritedOverrides = mutableMapOf<String, Boolean>()

    /** 记住用户对某条消息收藏状态的明确意图（内存态，会话结束即失效，只用来压过在途快照） */
    private fun rememberFavorite(messageId: String, favorited: Boolean) {
        synchronized(favoritedOverrides) { favoritedOverrides[messageId] = favorited }
    }

    /** 落盘前把用户改过的收藏标记套回去，返回套用后的新列表（没有覆盖项时原样返回，零开销） */
    private fun applyFavoriteOverrides(msgs: List<Message>): List<Message> {
        val overrides = synchronized(favoritedOverrides) { favoritedOverrides.toMap() }
        if (overrides.isEmpty()) return msgs
        var changed = false
        val out = msgs.map { m ->
            val want = overrides[m.id]
            if (want != null && want != m.favorited) { changed = true; m.copy(favorited = want) } else m
        }
        return if (changed) out else msgs
    }

    // 注：这里原本有一个 toggleFavorite(convId, messageId)（单条翻红心）。
    // 聊天页的红心现在只作为「进入收藏多选」的入口，取消收藏统一走收藏夹页 / 收藏详情页的
    // unfavoriteItems（整段语义），该方法已无任何调用点，故删除，避免留下一个语义相反的公开 API。

    /** 角色导出为 JSON 字符串（只含「人物设定」文字设定） */
    fun exportCharacterJson(profile: CharacterProfile): String = gson.toJson(profile.toExport())

    /** 从 JSON 导入角色（解析失败 / 缺角色名返回 null） */
    fun importCharacterFromJson(json: String): CharacterProfile? = try {
        val exp = gson.fromJson(json, CharacterExport::class.java) ?: return null
        if (exp.name.isBlank()) null else exp.toProfile()
    } catch (_: Exception) { null }

    /** 收藏详情：返回包含该收藏消息的「连续被收藏消息段」（向前向后扩展相邻 favorited 消息） */
    fun favoriteRun(convId: String, messageId: String): List<Message> {
        val msgs = loadMessages(convId)
        val idx = msgs.indexOfFirst { it.id == messageId }
        if (idx < 0) return emptyList()
        var start = idx
        while (start - 1 >= 0 && msgs[start - 1].favorited) start--
        var end = idx
        while (end + 1 < msgs.size && msgs[end + 1].favorited) end++
        return msgs.subList(start, end + 1)
    }

    /** 重算收藏夹：扫描所有对话消息里被收藏的，按时间倒序（后台 IO 读文件）。
     *  连续收藏合并：相邻被收藏的消息归为一个「连续段」，列表页只保留每段首条（详情页再由 favoriteRun 展开整段）。 */
    fun refreshFavorites() {
        val revision = favoritesRevision.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            val result = _conversations.value.flatMap { conv ->
                val msgs = loadMessages(conv.id)
                val runHeads = mutableListOf<Message>()
                var prevFavorited = false
                for (m in msgs) {
                    if (m.favorited && !prevFavorited) runHeads.add(m)  // 连续段的首条
                    prevFavorited = m.favorited
                }
                runHeads.map { FavoriteItem(conv, it) }
            }.sortedByDescending { it.message.timestamp }
            if (favoritesRevision.get() == revision) _favorites.value = result.filterNot {
                it.message.id in MessageDeletion.deletedIds(it.conversation.id)
            }
        }
    }

    /**
     * 取消整段收藏，只修改收藏标记，保留原聊天、附件及派生记忆。
     *
     * 为什么必须整段：收藏列表把一段连续被收藏的消息合并成一条展示（见 [refreshFavorites]，
     * 只保留每段首条）。若只清掉段首的 favorited，列表上这条不会消失，而是原地变成
     * 下一段的首条 —— 用户看到的就是「点了取消收藏却没反应」。
     *
     * 落盘按对话分组，每个对话只 load / save 一次；最后统一刷一次收藏列表。
     */
    fun unfavoriteItems(items: List<FavoriteItem>) {
        if (items.isEmpty()) return
        favoritesRevision.incrementAndGet()
        items.groupBy { it.conversation.id }.forEach { (convId, group) ->
            // Never replace a live, streaming conversation with its older disk snapshot.
            val before = if (_currentConversationId.value == convId) _messages.value else loadMessages(convId)
            val after = com.freechat.data.FavoritePolicy.unfavoriteRuns(before, group.mapTo(HashSet()) { it.message.id })
            if (before === after) return@forEach
            val cleared = before.zip(after).filter { (old, new) -> old.favorited && !new.favorited }
                .mapTo(HashSet()) { it.first.id }
            cleared.forEach { rememberFavorite(it, false) }
            if (_currentConversationId.value == convId) _messages.value = after
            _favorites.value = _favorites.value.filterNot { it.conversation.id == convId && it.message.id in cleared }
            // 走 persistConversationMessages（带 merge）：直接 saveMessages 会拿旧快照
            // 盖掉并发生成刚追加的新消息（1.0.99.4b 修）；收藏意图靠 rememberFavorite 覆盖表保
            persistConversationMessages(convId, after)
        }
        refreshFavorites()
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

    /**
     * 写这条对话的「规则」（系统级提示词）。
     *
     * 落在 [Conversation.rules] 上而不是每对话设置里 —— 理由见那个字段的注释：
     * 它是对话的内容，要跟着 conv 那个对象一起同步到别的设备。
     */
    fun updateConversationRules(convId: String, text: String) {
        if (convId.isBlank()) return
        val now = System.currentTimeMillis()
        _conversations.value = _conversations.value.map {
            if (it.id == convId) it.copy(rules = text, updatedAt = it.updatedAt) else it
        }
        saveConversations()
    }

    /**
     * 规则块 —— 拼进系统提示词的那一段；没写就是空串（整块不出现，不占 token）。
     *
     * 措辞上刻意把优先级说死：这是用户亲手写的、针对这条对话的命令，
     * 不该被上面那一大堆风格要求盖过去（模型很擅长"忽略最后一条不显眼的指令"）。
     */
    private fun convRulesBlock(convId: String): String {
        val text = _conversations.value.find { it.id == convId }?.rules?.trim().orEmpty()
        if (text.isEmpty()) return ""
        return "【用户为这条对话定下的规则 —— 用户亲手写的，优先级高于上面所有风格与格式要求】\n" +
            "$text\n\n" +
            "要求：1) 这些是用户对本条对话的明确要求，与上面任何一条冲突时，以这里为准。" +
            "2) 全程遵守，不是只遵守这一次。3) 不要复述、解释或评论这些规则，直接照做。"
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

    /**
     * 批量置顶 / 取消置顶（侧滑页多选）：一次改内存、一次落盘、一次写 DataStore。
     * 不复用 [togglePinConversation] 逐条翻转：那是 N 次全文件写入 + N 次 DataStore 写入，
     * 而且「逐条翻转」在批量场景下语义就是错的 —— 用户要的是「全部置顶 / 全部取消置顶」。
     */
    fun setPinnedConversations(ids: Set<String>, pinned: Boolean) {
        if (ids.isEmpty()) return
        val newPinned = pinnedIds.value.toMutableSet().apply {
            if (pinned) addAll(ids) else removeAll(ids)
        }
        pinnedIds.value = newPinned
        _conversations.value = sortConversations(
            _conversations.value.map {
                if (it.id in ids) it.copy(isPinned = pinned) else it
            }
        )
        saveConversations()
        viewModelScope.launch { settingsRepo.savePinnedIds(newPinned) }
    }

    // ========== 1.0.69 强制生图（一次性） ==========
    // 「+」菜单里的「生成图片」可勾选项：勾上后下一条发送不走关键词猜测、直接生图（带图=图生图），
    // 发出即自动取消 —— 用户拍板「一次性，发完自动取消」。
    // ========== 1.0.74 深度思考（通用推理控制，适配所有大模型） ==========
    // ========== 1.0.75 三层语义升级：全局=绑定模型的开关（所有模型默认关）、
    //  新规则=双键(对话×模型)覆盖（缺席=跟随全局）、首页点按=写「待新建对话」的规则草稿 ==========
    /**
     * 1.0.75 首页（还没有当前对话）状态键的落点：**该新开对话的新规则草稿**。
     * 首页点按改的是它（不是全局）；发首条消息建对话时随对话落定（见 sendMessage 的播种点），建完清空。
     * 内存态：关掉 App 未建对话的草稿即丢 —— 它属于「那条还没存在的对话」，不是持久默认值。
     */
    private val _pendingNewConvSettings = MutableStateFlow(PerConvSettings())
    val pendingNewConvSettings: StateFlow<PerConvSettings> = _pendingNewConvSettings.asStateFlow()

    /** 首页「联网搜索」点按（1.0.75 点名）：写**新对话规则草稿**的显式开/关，不许再写全局 */
    fun togglePendingWebSearch() {
        val draft = _pendingNewConvSettings.value
        val eff = draft.enableWebSearch ?: _enableWebSearch.value
        _pendingNewConvSettings.value = draft.copy(enableWebSearch = !eff)
    }

    /** 标准档深度思考解析（1.0.75 双键）：(对话×模型) 覆盖 → 遗留每对话兜底 → 模型的全局开关 */
    fun deepThinkFor(per: PerConvSettings?, model: ModelInfo): Boolean =
        per?.deepThinkByModel?.get(model.id) ?: per?.deepThinking ?: model.deepThinkingDefault

    /** 新规则页三态显示用：null = 跟随全局，非 null = 用户为（对话×模型）显式选的开/关 */
    fun deepThinkOverride(per: PerConvSettings?, model: ModelInfo): Boolean? =
        per?.deepThinkByModel?.get(model.id) ?: per?.deepThinking

    /**
     * 当前对话**生效的语言模型**：新规则里的模型覆盖优先，没覆盖才是全局选中的那个。
     * 深度思考双键里的「模型」就是它 —— 拿全局选中模型当键，会在「对话选了 B、全局是 A」时读错状态。
     */
    fun effectiveLangModel(): ModelInfo {
        return resolveModels(_currentConversationId.value).language
    }

    /**
     * 生效值：**标准档** = 双键覆盖 > 遗留兜底 > 模型全局开关；**拟人档** = 角色档案（模拟设置页/输入框按钮互通）。
     * 模型不支持 = 恒 false（按钮灰色）。首页（无对话）读写的是新对话草稿 [_pendingNewConvSettings]。
     */
    fun effectiveDeepThinking(): Boolean {
        val model = effectiveLangModel()
        if (!model.supportsDeepThinking) return false
        val convId = _currentConversationId.value
        val ch = convId?.let { id -> _conversations.value.find { it.id == id }?.characterProfile }
        if (ch != null) return ch.deepThinkingMode
        val per = if (convId != null) _perConvSettings.value[convId] else _pendingNewConvSettings.value
        return deepThinkFor(per, model)
    }

    /**
     * 输入框按钮/新规则页/模拟设置页共用的切换入口（按档位写各自的层，与各自设置页互通）。
     * 标准档写双键 (对话×模型)：点按 = 对生效模型显式开/关（绑对话+模型，不影响全局）；
     * 首页写新对话草稿。拟人档写角色档案不变。
     */
    fun toggleDeepThinking() {
        val model = effectiveLangModel()
        if (!model.supportsDeepThinking) return
        val on = !effectiveDeepThinking()
        val modelId = model.id
        val convId = _currentConversationId.value
        if (convId == null) {
            val draft = _pendingNewConvSettings.value
            _pendingNewConvSettings.value = draft.copy(deepThinkByModel = draft.deepThinkByModel + (modelId to on))
            return
        }
        val ch = _conversations.value.find { it.id == convId }?.characterProfile
        if (ch != null) {
            // 拟人档写角色档案（模拟设置页同一个值，互通）
            _conversations.value = _conversations.value.map {
                if (it.id == convId) it.copy(characterProfile = ch.copy(deepThinkingMode = on)) else it
            }
            saveConversations()
        } else {
            val per = getPerConvSettings(convId)
            updatePerConvSettings(convId, per.copy(deepThinkByModel = per.deepThinkByModel + (modelId to on)))
        }
    }

    /** 新规则页写三态：null = 跟随全局（抹掉这个模型的覆盖记录与遗留兜底） */
    fun setDeepThinkOverride(convId: String, modelId: String, value: Boolean?) {
        val per = getPerConvSettings(convId)
        val map = if (value == null) per.deepThinkByModel - modelId else per.deepThinkByModel + (modelId to value)
        // 显式选择落在双键表上；遗留的整对话值只在「跟随全局」时一并清掉，别让它继续兜底冒充覆盖
        updatePerConvSettings(convId, per.copy(deepThinkByModel = map, deepThinking = if (value == null) null else per.deepThinking))
    }

    /** 全局设置页的「绑定模型」开关（1.0.75）：改的就是模型档案里那个值，切走再切回仍在 */
    fun setDeepThinkDefaultForModel(modelId: String, modelType: ModelType, on: Boolean) {
        val target = modelsOfType(modelType).find { it.id == modelId } ?: return
        if (target.isBuiltIn) setBuiltInDeepThinkDefault(target, on)
        else updateCustomModel(modelId, modelType, target.copy(deepThinkingDefault = on))
    }

    /** 拟人档内部生效值（模型能力 gate 后的角色开关） */
    private fun companionDeepThink(character: CharacterProfile?, model: ModelInfo = resolveModels(null, character).language): Boolean =
        model.supportsDeepThinking && (character?.deepThinkingMode ?: true)

    /**
     * 深度思考「尽力关」参数（1.0.74）：业界常见的关推理参数一并带上 —— 认识的端点生效、不认识的忽略。
     * 开 = 不干预（模型默认就是它自己的思考模式）。个别严格网关若拒收多余字段，见注释里的降级说明。
     */
    private fun deepThinkExtras(on: Boolean): Map<String, Any?> = com.freechat.core.CompanionPrompts.deepThinkExtras(on)

    private val _forceImageGen = MutableStateFlow(false)
    val forceImageGen: StateFlow<Boolean> = _forceImageGen.asStateFlow()
    fun setForceImageGen(on: Boolean) { _forceImageGen.value = on }

    // ========== 1.0.71 上下文预算（二值化，用户拍板：可选/自定义全部删去） ==========
    // 增强检索（拟人档 [CharacterProfile.highQualityMemory]）开 → 1M 上下文；否则锁死 256K。
    // 1.0.69 的「模型上下文」档位/自定义/「声明支持 1M」与 +50% 联动全部移除 ——
    // 用户判定「可选上下文与增强检索冲突，标准模式没必要自选」。标准模式恒 256K。
    // 仍按估算 token 裁剪历史（1 token ≈ 2 字符，中英混合偏保守）。

    // —— 以下三件已抽进 freechat-core（CompanionHistory）：Android 与服务器 JVM 共用同一份 ——
    private fun estimateTokens(s: String): Int = com.freechat.core.CompanionHistory.estimateTokens(s)

    private fun historyBudgetTokens(enhanced: Boolean): Int = com.freechat.core.CompanionHistory.historyBudgetTokens(enhanced)

    private fun pickHistory(working: List<Message>, budgetTokens: Int): List<Message> =
        com.freechat.core.CompanionHistory.pickHistory(working, budgetTokens)

    // ========== Skills 调度系统 ==========
    // 能力枚举：每个 Skill 绑定一类任务，detectSkill 负责路由到对应模型/执行器
    private enum class Skill {
        IMAGE_GEN,   // 文本生图 → Doubao Seedream
        IMAGE_EDIT,  // 修图/图生图 → Doubao Seedream img2img
        VISION,      // 识图/带图对话 → 识图模型（Doubao Seed / MiMo）
        CALENDAR,    // 日历行程 → CalendarContract
        DOCUMENT,    // 生成/编辑原生文档（docx/xlsx/pptx）
        FILE,        // 理解上传的文件内容并回复
        TEXT         // 纯文本 → 已选语言模型（可带检索资料）
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
    private data class ToolCall(val name: String, val arguments: JsonObject, val id: String = "call_${System.nanoTime()}")
    /** 语言模型一次流式调用的结果（内容 + 思考 + 待执行的工具调用） */
    private data class LanguageResult(
        val content: String,
        val reasoning: String,
        val toolCalls: List<ToolCall>,
        /**
         * 原生联网（小米 web_search 工具）这次拿到的引用：标题 to 链接。
         * **空 = 模型没有提供可核查的引用**；部分端点会改用搜索使用次数证明执行。
         * 不能靠"回答里看起来有没有新信息"去猜：没搜到的时候模型不会说不知道，
         * 它会拿训练数据里最像的答案顶上，读起来照样通顺。
         */
        val citations: List<Pair<String, String>> = emptyList(),
        /** web_search_usage.tool_usage：本轮服务端实际执行的搜索次数，0 = 一次都没搜 */
        val webSearchUsed: Int = 0,
        /** 搜索工具在流里报的错（HTTP 200 但工具失败，实测有 "Keyword extraction model timed out"） */
        val searchError: String = "",
        val requestMessages: List<Map<String, Any?>> = emptyList(),
    )
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
    /** 闲聊归一（1.0.74）：去首尾空白/尾标点 + 小写 —— 「你好。」「Hello!」都归到「你好」「hello」 */
    private fun normalizeChitchat(s: String): String = s.trim().lowercase()
        .trimEnd('。', '！', '？', '!', '?', '~', '～', '…', '.', '，', ',', '、', ' ', '　')
        .trim()

    /** 纯闲聊/极短寒暄判定（1.0.74）：这类消息不带工具定义直发，砍掉模型「要不要调工具」的决策与误调双轮 */
    private fun isChitchat(text: String): Boolean {
        val t = text.trim()
        if (t.length <= 2) return true
        val tn = normalizeChitchat(t)
        return setOf("你好", "你好呀", "你好啊", "您好", "嗨", "嘿", "哈喽", "hey", "hi", "hello",
            "在吗", "谢谢", "多谢", "thanks", "thx", "再见", "拜拜", "bye", "晚安",
            "好的", "ok", "嗯", "哦", "行", "好", "对", "是的", "哈哈", "呵呵", "haha", "lol",
            "早上好", "下午好", "晚上好", "早安").any { tn == it }
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
        // A picker opened before a mode change must not sneak photos into a narrative turn.
        if (!canUploadChatImages() && _pendingImages.value.isNotEmpty()) clearPendingImages()
        val hasImage = _pendingImages.value.isNotEmpty()
        val hasFile = _pendingFiles.value.isNotEmpty()
        if (trimmedText.isBlank() && !hasImage && !hasFile) return
        if (_isAddingImages.value) return  // 图片还在处理中，稍后再发
        if (_isAddingFiles.value) return   // 文件还在拷贝中，稍后再发

        // 用户确实要发消息了：立即启动前台服务保活，锁屏/切后台不冻网、不中断回复。
        // 拟人模式的「缓冲等待期」也一并覆盖，避免锁屏后缓冲结束才从后台启动前台服务被系统限制。
        markGenerationStarted()

        // 拟人陪伴模式：统一走缓冲管线（AI 思考中也能继续发消息，连发合并理解）
        if (_currentMode.value == ChatMode.COMPANION) {
            val imageSnapshot = _pendingImages.value.map { it.path }
            if (hasImage) _pendingImages.value = emptyList()
            sendCompanionMessage(trimmedText, imageSnapshot, quotedText, quotedImagePath)
            return
        }

        if (_isLoading.value) return

        // 1.0.69 一次性强制生图：只在**标准链路真正发出**时消费（拟人档没有生图执行器，
        // 标志留着等下一条标准消息；regenerate 走不到这里，不会误吃）。发送前的早退（空消息/
        // 附件处理中/生成中）都还没发出去，标志保留。
        val forceGen = _forceImageGen.value
        if (forceGen) _forceImageGen.value = false

        val creatingConv = _currentConversationId.value == null
        if (_currentConversationId.value == null && _messages.value.isEmpty()) {
            _currentConversationId.value = java.util.UUID.randomUUID().toString()
        }
        val convId = _currentConversationId.value ?: java.util.UUID.randomUUID().toString()
        _currentConversationId.value = convId
        // 1.0.75：首页状态键写的是「该新开对话的新规则草稿」，此刻对话诞生 → 随对话落定，落定即清空。
        // 只播这一次：以后改这条对话一律走 updatePerConvSettings，跟草稿再无关系
        if (creatingConv) {
            val draft = _pendingNewConvSettings.value
            if (draft != PerConvSettings()) {
                updatePerConvSettings(convId, draft)
                _pendingNewConvSettings.value = PerConvSettings()
            }
        }
        // 是否新对话首条消息（回复完成后让 AI 起标题）
        val isNewConversation = _messages.value.isEmpty()

        // 图片 + 提示词合并为同一条消息（图片与文字属同一次发送，收藏/详情都能同时看到图和提示词）
        val pendingSnapshot = _pendingImages.value
        if (hasImage) _pendingImages.value = emptyList()  // 发送即清空候选区（文件已被消息引用，不删）
        val fileSnapshot = _pendingFiles.value
        if (hasFile) _pendingFiles.value = emptyList()

        val newUserMessages = mutableListOf<Message>()
        // 图片、最后一份附件与提示词在同一条消息；其余附件保留为紧邻的前置行。
        if (hasImage || hasFile) {
            // Keep earlier files as adjacent attachment rows, with the prompt LAST so it remains
            // editable and represents the entire latest user turn for replacement/regeneration.
            fileSnapshot.dropLast(1).forEach { file ->
                newUserMessages.add(Message(role = Role.USER, content = "",
                    attachmentPath = file.path, attachmentName = file.name))
            }
            val f = fileSnapshot.lastOrNull()
            newUserMessages.add(Message(
                role = Role.USER,
                content = trimmedText,
                imagePaths = if (hasImage) pendingSnapshot.map { it.path } else emptyList(),
                attachmentPath = f?.path,
                attachmentName = f?.name,
                quotedText = quotedText,
                quotedImagePath = quotedImagePath
            ))
        } else if (trimmedText.isNotBlank()) {
            newUserMessages.add(Message(role = Role.USER, content = trimmedText, quotedText = quotedText, quotedImagePath = quotedImagePath))
        }
        activeRoundStartIndex = _messages.value.size  // ★ 停止即删除本轮：记录用户消息起点
        activeRoundConvId = convId
        _messages.value = _messages.value + com.freechat.data.MessageBatchOrder.after(_messages.value, newUserMessages)
        touchCurrentConversation()
        persistConversationMessages(convId, _messages.value)
        // 记忆总结用的用户文本
        val summaryUserText = when {
            trimmedText.isNotBlank() -> trimmedText
            hasImage -> "[图片]"
            hasFile -> "[文件: ${fileSnapshot.firstOrNull()?.name ?: ""}]"
            else -> ""
        }

        setConvLoading(convId, true)
        val replyId = convGenerationReplyIds.getValue(convId)
        _liveReasoning.value = ""
        _liveContent.value = ""
        _thinkingTimeMs.value = 0L

        standardGenerationConvId = convId
        val generationId = ++standardGenerationId
        streamJob = viewModelScope.launch(resolveModels(convId)) {
            val startTime = System.currentTimeMillis()
            val generationTimer = com.freechat.data.GenerationTimer()
            val snapshot = applyPerConvSettings(convId)
            standardSettingsSnapshot = snapshot
            val langModelName = requestModels().language.displayName
            val visualModelName = requestModels().visual.displayName
            val timerJob = launch {
                // 循环条件看「本对话」而不是当前显示的对话：发完消息切去别的对话 / 切后台时，
                // _isLoading 会跟着当前对话走，用它会让本轮的续锁提前停掉。
                while (convLoading[convId] == true || convTyping[convId] == true) {
                    // 计时数字只代表「当前正在看的那一轮」，否则两个对话同时生成时会来回跳
                    if (_isLoading.value && _currentConversationId.value == convId) {
                        _thinkingTimeMs.value = generationTimer.elapsedMs()
                    }
                    // 每 5 分钟给前台服务续一次唤醒锁（内部有节流）：长回复（尤其带思考链的）会超过
                    // 一把锁的兜底时长，不续期就会在生成中途被释放，息屏后 CPU 一挂起就变成「AI 迟迟不回复」
                    renewKeepAliveLock()
                    delay(80)
                }
            }

            val working = _messages.value.toMutableList()
            try {
                val assistantMessage = generateReply(
                    trimmedText, pendingSnapshot, fileSnapshot,
                    langModelName, visualModelName, startTime, convId, working, forceImageGen = forceGen
                ).let(generationTimer::complete).let { separateCitationLinks(it, trimmedText) }
                ensureActive()  // 若已停止，此处抛出取消，避免补一条回复
                // 识图结果回填：把识图结果写回用户图片消息，追问时作为「图片内容」注入（修复追问失忆）
                if (assistantMessage.modelName == requestModels().vision?.displayName) {
                    val ui = working.indexOfLast { it.role == Role.USER && it.imagePaths.isNotEmpty() }
                    if (ui >= 0) working[ui] = working[ui].copy(imageContext = assistantMessage.content)
                }
                val completed = com.freechat.data.MessageBatchOrder.after(working,
                    listOf(assistantMessage.copy(id = replyId)), maxOf(System.currentTimeMillis(), assistantMessage.timestamp)).single()
                working.add(completed)
                persistConversationMessages(convId, working)
                _liveReasoning.value = ""
                _liveContent.value = ""
                notifyReplyIfBackground("FreeChat", assistantMessage.content)
                summarizeAndRemember(convId, summaryUserText, completed)
                if (isNewConversation) maybeAutoTitle(convId, summaryUserText)
            } catch (e: CancellationException) {
                throw e  // 用户停止：不显示错误，交给 finally 收尾
            } catch (e: Exception) {
                ensureActive()
                Log.e("FreeChat", "API failed", e)
                val errMsg = Message(
                    id = replyId,
                    role = Role.ASSISTANT,
                    content = briefApiError(e),
                    modelName = langModelName,
                    timestamp = maxOf(System.currentTimeMillis(), (working.maxOfOrNull { it.timestamp } ?: 0L) + 1),
                    failed = true
                )
                working.add(errMsg)
                persistConversationMessages(convId, working)
                _liveReasoning.value = ""
                _liveContent.value = ""
            } finally {
                if (generationId == standardGenerationId) {
                    restoreSettings(snapshot)
                    setConvLoading(convId, false)
                    _isGeneratingImage.value = false
                    setConvTyping(convId, false)
                    activeRoundStartIndex = -1
                    activeRoundConvId = null
                    standardSettingsSnapshot = null
                    standardGenerationConvId = null
                }
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

        // 用户主动来了：掐掉「正在自己冒出来的那句话」——TA 已经到了，主动消息失去意义；
        // 而且两边同时写同一个对话文件会互相覆盖（主动那条会被这次回复的旧快照抹掉）。
        // 注意 proactiveJobs 是进程级静态表：App 冷启动时主动生成跑在服务新建的 VM 上，
        // 用户发消息走的是另一个 VM，放实例字段上根本够不着。
        proactiveJobs.remove(convId)?.cancel()

        // 记录「晚安/睡了」短期状态（按对话）：短时间内用户再发消息，AI 有时间概念（「不是说睡了吗」）
        if (detectGoodnight(trimmedText)) {
            userSaidGoodnightAt[convId] = System.currentTimeMillis()
        }

        val pipeline = companionPipelines.getOrPut(convId) { CompanionPipeline() }
        // 思考中被新消息打断：清掉已发出的半截 AI 回复，重新统一理解（含新消息）
        if (pipeline.replyStart >= 0) {
            deleteMessageIds(convId, _messages.value.drop(pipeline.replyStart)
                .filter { it.role == Role.ASSISTANT && !it.sceneVisualization }.mapTo(HashSet()) { it.id }, cancelGeneration = false)
        }
        pipeline.replyStart = -1
        pipeline.jobId++
        setConvLoading(convId, true)
        if (imagePaths.isNotEmpty()) {
            // 图片 + 提示词合并为一条（图片与文字属同一次发送）
            _messages.value = _messages.value + Message(
                role = Role.USER,
                content = trimmedText,
                imagePaths = imagePaths,
                mode = ChatMode.COMPANION,
                quotedText = quotedText,
                quotedImagePath = quotedImagePath
            )
        } else if (trimmedText.isNotBlank()) {
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
            // 回复缓冲只属于「微信聊天」档：动作演绎/剧情补足是整段演绎，不需要先攒消息再一起回
            // （旧版只在 UI 上隐藏了这一节，运行时仍会照等 3 秒 —— 这次一并修掉）
            val wechatMode = (character?.dialogueMode ?: DialogueMode.WECHAT) == DialogueMode.WECHAT
            val enabled = wechatMode && character?.replyBufferEnabled != false  // 默认开启
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
        val langModelName = resolveModels(convId, character).language.displayName
        // 多条合一：保留逐条边界，供模型把连发当作一个整体场景理解（质量不降级）
        val text = if (turns.size > 1)
            turns.mapIndexed { i, t -> "${i + 1}. ${t.text}" }.filter { it.isNotBlank() }.joinToString("\n").trim()
        else turns.firstOrNull()?.text.orEmpty()
        val imagePaths = turns.flatMap { it.imagePaths }
        // ★ M4 收口（1.0.91）：接微信的对话生成全权在服务器（同一颗大脑、与 App 同码机制），
        //   App 只当瘦客户端窗口。用户消息照常本地落盘+同步（skipUserWrite，大脑不重复写）；
        //   图片消息不走收口（内置 mimo 纯文本代调，识图生图不带），仍走本地生成。
        if (imagePaths.isEmpty() && isWeChatAttached(convId)) {
            try {
                generateViaServer(convId, character, text, System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FreeChat", "server-side companion reply failed", e)
                val err = Message(role = Role.ASSISTANT, content = briefApiError(e), modelName = "MiMo-V2.6-Flash", mode = ChatMode.COMPANION, failed = true)
                if (_currentConversationId.value == convId) _messages.value = _messages.value + err
                persistConversationMessages(convId, loadMessages(convId) + err)
            } finally {
                if (id == pipeline.jobId) { setConvLoading(convId, false); setConvTyping(convId, false); pipeline.replyStart = -1 }
            }
            return
        }
        val working = loadMessages(convId).toMutableList()
        pipeline.replyStart = working.size
        try {
            generateCompanionReply(convId, character, working, text, imagePaths, startTime, batchSize = turns.size)
        } catch (e: CancellationException) {
            throw e  // 思考中被打断：sendCompanionMessage 负责清理半截回复
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Log.e("FreeChat", "companion buffer reply failed", e)
            working.add(Message(role = Role.ASSISTANT, content = briefApiError(e), modelName = langModelName, mode = ChatMode.COMPANION, failed = true))
            persistConversationMessages(convId, working)
        } finally {
            if (id == pipeline.jobId) {  // 只有仍是最新一轮才复位，避免旧 job 干扰新缓冲
                setConvLoading(convId, false)
                setConvTyping(convId, false)
                pipeline.replyStart = -1
            }
        }
    }

    // ===================== M4 收口：接微信的对话走服务器生成 =====================

    /** 该对话是否接了微信（30s 缓存；绑定/解绑后调 [invalidateWeChatAttachment]） */
    @Volatile
    private var wechatAttachCache: Pair<Long, String?>? = null

    fun invalidateWeChatAttachment() {
        wechatAttachCache = null
    }

    /** UI 问询（1.0.94 第六条）：该对话是否已接微信——输入区是否换成「前往微信」告知条 */
    suspend fun wechatAttachedNow(convId: String): Boolean = isWeChatAttached(convId)

    /**
     * 同步预读（UI 首帧用）：只读缓存不发请求。
     * null=还不知道（真值交给 [wechatAttachedNow]）；非 null=30s 内查过的确定答案。
     * 有了它，打开已接微信的对话不会先闪一帧输入框再换成告知条。
     */
    fun wechatAttachedCached(convId: String): Boolean? {
        val (t, v) = wechatAttachCache ?: return null
        if (System.currentTimeMillis() - t >= 30_000L) return null
        return v == convId
    }

    private suspend fun isWeChatAttached(convId: String): Boolean {
        val now = System.currentTimeMillis()
        wechatAttachCache?.let { (t, v) -> if (now - t < 30_000L) return v == convId }
        val auth = Session.loadAuth() ?: return false
        val attached = runCatching {
            val o = ApiClient.wechatStatus(auth.token)
            if (o.get("bound")?.asBoolean == true) o.get("conv_id")?.asString else null
        }.getOrNull()
        wechatAttachCache = now to attached
        return attached == convId
    }

    /**
     * 服务器生成一轮（收口路径）：POST /companion/reply（鉴权代理到大脑），
     * 分条按情绪节奏逐条揭示（间隔走 CompanionRhythm，与本地生成同款真人感）；
     * 消息 id 用服务端返回的那些——同步合并同 id 去重，云端/本地永不双份。
     * 用户消息由本地落盘+正常同步负责（skipUserWrite）。
     */
    private suspend fun generateViaServer(convId: String, character: CharacterProfile?, text: String, startTime: Long) {
        val auth = Session.loadAuth() ?: throw com.freechat.sync.ApiError(401, "unauthorized", "需要登录")
        setConvLoading(convId, true)
        setConvTyping(convId, true)
        val out = ApiClient.companionReply(auth.token, convId, text, skipUserWrite = true)
        setConvTyping(convId, false)
        if (out.get("slept")?.asBoolean == true) return                    // 作息沉默：真人睡着了就是不回
        val segments = out.getAsJsonArray("segments")?.map { it.asString }.orEmpty()
        if (segments.isEmpty()) return                                     // 生气不回 / 空回复
        val ids = out.getAsJsonArray("messageIds")?.map { it.asString }.orEmpty()
        val emotion = out.get("emotion")?.asString.orEmpty()
        val narrative = character?.isNarrativeMode() == true
        val mood = if (narrative) CompanionMood.NEUTRAL else
            (parseEmotionLabel(emotion) ?: moodWithResidue(text, getPerConvSettings(convId)))
        for ((i, seg) in segments.withIndex()) {
            if (i > 0) {
                setConvTyping(convId, true)
                delay(CompanionRhythm.segmentIntervalMs(mood, narrative))
                setConvTyping(convId, false)
            }
            val msg = Message(
                id = ids.getOrElse(i) { UUID.randomUUID().toString() },
                role = Role.ASSISTANT, content = seg,
                timestamp = System.currentTimeMillis(),
                modelName = "MiMo-V2.6-Flash", mode = ChatMode.COMPANION,
                thinkingTimeMs = System.currentTimeMillis() - startTime
            )
            if (_currentConversationId.value == convId) _messages.value = _messages.value + msg
            persistConversationMessages(convId, loadMessages(convId) + msg)
        }
        // 拉一轮同步对账（记忆/氛围在云端写的；顺带把任何时序差抹平）
        runCatching { com.freechat.sync.SyncEngine.syncNow() }
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
        val convId = _currentConversationId.value
        val start = if (convId == activeRoundConvId) activeRoundStartIndex else -1
        activeRoundStartIndex = -1
        activeRoundConvId = null
        if (convId != null) {
            if (start >= 0 && start <= _messages.value.size)
                deleteMessageIds(convId, _messages.value.drop(start).mapTo(HashSet()) { it.id })
            cancelConversationGeneration(convId)
        }
        _liveReasoning.value = ""
        _liveContent.value = ""
        _thinkingTimeMs.value = 0L
        if (convId != null) setConvLoading(convId, false) else _isLoading.value = false
        _isGeneratingImage.value = false
        if (convId != null) setConvTyping(convId, false) else _isTyping.value = false
        Log.d("FreeChat", "Generation stopped by user")
    }

    /**
     * 【动作演绎 / 剧情补足】右侧终止键：撤回刚发出去的那条提示词。
     *
     * 与 [stopGeneration] 的区别只有一个 —— 把用户刚发的内容**退回输入框**，
     * 让它变成可以直接改的草稿。这两种模式一次只发一条、AI 回一条，用户按下终止键的
     * 真实意图几乎总是「我这段话写错了，想改一改再发」，而不是「我不要这段对话了」，
     * 所以把原话还给他、让他改完直接重发，比让他重新打一遍顺手得多。
     *
     * 注意取文字的时机：必须先读、后调 stopGeneration —— 后者会把这一轮从消息列表里删掉，
     * 删完再读就只能拿到空串。
     */
    fun retractCurrentRound() {
        val start = if (_currentConversationId.value == activeRoundConvId) activeRoundStartIndex else -1
        val pending = if (start in 0 until _messages.value.size) {
            _messages.value.subList(start, _messages.value.size)
                .filter { it.role == Role.USER }
                .joinToString("\n") { it.content }
                .trim()
        } else ""
        stopGeneration()
        if (pending.isNotEmpty()) {
            _currentConversationId.value?.let { saveDraft(it, pending) }
        }
    }

    /** 重新生成：删除旧 AI 回复，重新调用生成逻辑，新回复插回原位置 */
    fun regenerate(aiIndex: Int) {
        if (_isLoading.value || _isTyping.value) return
        if (_currentMode.value == ChatMode.COMPANION &&
            !com.freechat.data.CompanionFeaturePolicy.supportsNarrativeActions(_currentCharacter.value)) return
        val convId = _currentConversationId.value ?: return
        val msgs = _messages.value
        if (aiIndex < 0 || aiIndex >= msgs.size || msgs[aiIndex].role != Role.ASSISTANT) return
        if (_currentMode.value == ChatMode.COMPANION &&
            msgs[aiIndex].id in com.freechat.data.CompanionFeaturePolicy.sceneLockedMessageIds(msgs)) return

        val plan = com.freechat.data.RegenerationPlan.from(msgs, aiIndex) ?: return
        val userMsgs = plan.userMessages
        val text = userMsgs.map { it.content }.filter { it.isNotBlank() }.joinToString("\n")
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

        // The whole companion reply batch is replaced, including siblings of the tapped bubble.
        deleteMessageIds(convId, plan.removedReplies.mapTo(HashSet()) { it.id })
        val character = _currentCharacter.value
        val companion = _currentMode.value == ChatMode.COMPANION
        if (companion) {
            val pipeline = companionPipelines.getOrPut(convId) { CompanionPipeline() }
            val jobId = ++pipeline.jobId
            setConvLoading(convId, true)
            pipeline.job = viewModelScope.launch {
                val working = plan.context.toMutableList()
                try {
                    generateCompanionReply(convId, character, working, text, images.map { it.path },
                        System.currentTimeMillis(),
                        batchSize = userMsgs.size, regenerating = true,
                        replyTimestamp = plan.removedReplies.first().timestamp)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ensureActive()
                    working.add(Message(role = Role.ASSISTANT, content = briefApiError(e), failed = true,
                        mode = ChatMode.COMPANION, timestamp = plan.removedReplies.first().timestamp))
                    persistConversationMessages(convId, working)
                } finally {
                    if (jobId == pipeline.jobId) {
                        setConvLoading(convId, false)
                        setConvTyping(convId, false)
                        pipeline.replyStart = -1
                    }
                }
            }
            return
        }

        setConvLoading(convId, true)
        val replyId = convGenerationReplyIds.getValue(convId)
        _liveReasoning.value = ""
        _liveContent.value = ""
        _thinkingTimeMs.value = 0L

        standardGenerationConvId = convId
        val generationId = ++standardGenerationId
        streamJob = viewModelScope.launch(resolveModels(convId)) {
            val startTime = System.currentTimeMillis()
            val generationTimer = com.freechat.data.GenerationTimer()
            val snapshot = applyPerConvSettings(convId)
            standardSettingsSnapshot = snapshot
            val langModelName = requestModels().language.displayName
            val visualModelName = requestModels().visual.displayName
            val timerJob = launch {
                // 循环条件看「本对话」而不是当前显示的对话：发完消息切去别的对话 / 切后台时，
                // _isLoading 会跟着当前对话走，用它会让本轮的续锁提前停掉。
                while (convLoading[convId] == true || convTyping[convId] == true) {
                    // 计时数字只代表「当前正在看的那一轮」，否则两个对话同时生成时会来回跳
                    if (_isLoading.value && _currentConversationId.value == convId) {
                        _thinkingTimeMs.value = generationTimer.elapsedMs()
                    }
                    // 每 5 分钟给前台服务续一次唤醒锁（内部有节流）：长回复（尤其带思考链的）会超过
                    // 一把锁的兜底时长，不续期就会在生成中途被释放，息屏后 CPU 一挂起就变成「AI 迟迟不回复」
                    renewKeepAliveLock()
                    delay(80)
                }
            }
            // Future exchanges must not leak into a historical regeneration's prompt.
            val working = plan.context.toMutableList()
            try {
                val assistantMessage = generateReply(text, images, files, langModelName, visualModelName, startTime, convId, working)
                    .let(generationTimer::complete).let { separateCitationLinks(it, text) }
                ensureActive()
                val fresh = assistantMessage.copy(id = replyId, timestamp = plan.removedReplies.first().timestamp)
                working.add(fresh)
                persistConversationMessages(convId, working)
                _liveReasoning.value = ""
                _liveContent.value = ""
                summarizeAndRemember(convId, summaryUserText, fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                Log.e("FreeChat", "regenerate failed", e)
                working.add(Message(id = replyId, role = Role.ASSISTANT, content = "重新生成失败，请稍后尝试...", modelName = langModelName,
                    timestamp = plan.removedReplies.first().timestamp, failed = true))
                persistConversationMessages(convId, working)
                _liveReasoning.value = ""
                _liveContent.value = ""
            } finally {
                if (generationId == standardGenerationId) {
                    restoreSettings(snapshot)
                    setConvLoading(convId, false)
                    _isGeneratingImage.value = false
                    standardGenerationConvId = null
                    standardSettingsSnapshot = null
                }
            }
        }
    }

    /** 剧情模式：改写最后一条用户消息后重新生成回复（作废旧回复，覆盖原对话） */
    /**
     * 【点气泡改写提示词】把最后一条用户提示词改成 [newText] 重新生成，**不留旧版本**。
     *
     * 和「重新生成」是两件不同的事：重新生成是「这句话不变，你重答一遍」；
     * 这里是「这句话我说错了，改成这样，你按新的来」。所以旧提示词连同它那一轮的记忆都要作废
     * （见 [editCompanionLastMessage] 与下面的标准模式分支），否则用户改完了 AI 还记得旧版本。
     *
     * 只允许最后一条用户消息：改历史消息会把后面整段对话变成无效上下文，
     * 界面上也只给最后一条挂入口（ChatScreen 的 lastUserMessageIndex）。
     */
    fun sendEditedMessage(messageId: String, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        if (_isLoading.value) return  // 生成中不给改：新回复会和正在跑的那一轮抢同一个位置
        val convId = _currentConversationId.value ?: return

        if (_currentMode.value == ChatMode.COMPANION) {
            // 拟人模式（含动作演绎/剧情补足）已有完整实现：保留原消息的图片、删掉旧回复、
            // 清掉这一轮记忆、按新文本重新生成
            editCompanionLastMessage(trimmed)
            return
        }

        // 标准模式：这条提示词和它之后的所有消息整段作废，然后把新文本当一条全新消息发出去
        val msgs = _messages.value.toMutableList()
        val idx = msgs.indexOfFirst { it.id == messageId }
        if (idx < 0 || msgs[idx].role != Role.USER) {
            sendMessage(trimmed)  // 找不到目标（列表已被别的操作改过）→ 退化成普通发送，不吞消息
            return
        }
        deleteMessageIds(convId, msgs.drop(idx).mapTo(HashSet()) { it.id })
        sendMessage(trimmed)
    }

    fun editCompanionLastMessage(newText: String) {
        if (_isLoading.value || _isTyping.value ||
            !com.freechat.data.CompanionFeaturePolicy.supportsNarrativeActions(_currentCharacter.value)) return
        val convId = _currentConversationId.value ?: return
        val character = _currentCharacter.value
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        val msgs = _messages.value.toMutableList()
        val lastUserIdx = msgs.indexOfLast { it.role == Role.USER }
        if (lastUserIdx < 0) return
        if (msgs[lastUserIdx].id in com.freechat.data.CompanionFeaturePolicy.sceneLockedMessageIds(msgs)) return
        // 保留原用户消息带的图片（剧情模式一般纯文本，兼容带图）
        val imagePaths = msgs[lastUserIdx].imagePaths
        // ★ 旧提示词要「连同它的记忆一起」作废：这一轮（旧提示词 + 旧回复）已经被用户推翻，
        //   如果留着记忆，用户明明改了那句话，AI 后面还是会记得旧版本——等于白改。
        //   必须在改写内容之前取时间戳，改完 timestamp 就变了。
        val replacement = msgs[lastUserIdx].copy(id = UUID.randomUUID().toString(), content = trimmed)
        // Files are retained by the replacement row; deleting the old ID does not delete shared images.
        _messages.value = msgs + replacement
        deleteMessageIds(convId, msgs.drop(lastUserIdx).mapTo(HashSet()) { it.id })
        msgs.subList(lastUserIdx, msgs.size).clear()
        msgs.add(replacement)
        _messages.value = msgs
        persistConversationMessages(convId, msgs)

        val pipeline = companionPipelines.getOrPut(convId) { CompanionPipeline() }
        val generationId = ++pipeline.jobId
        pipeline.replyStart = msgs.size
        setConvLoading(convId, true)
        pipeline.job = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val langModelName = resolveModels(convId, character).language.displayName
            val working = loadMessages(convId).toMutableList()
            try {
                generateCompanionReply(convId, character, working, trimmed, imagePaths, startTime, batchSize = 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                Log.e("FreeChat", "editCompanionLastMessage failed", e)
                working.add(Message(role = Role.ASSISTANT, content = briefApiError(e), modelName = langModelName, mode = ChatMode.COMPANION, failed = true))
                persistConversationMessages(convId, working)
            } finally {
                if (generationId == pipeline.jobId) {
                    setConvLoading(convId, false)
                    setConvTyping(convId, false)
                    pipeline.replyStart = -1
                }
            }
        }
    }

    private fun separateCitationLinks(message: Message, request: String): Message {
        val requested = SearchPresentation.linksRequested(request)
        val display = SearchPresentation.forDisplay(message.content, message.searchSources, requested)
        return message.copy(content = display.answer, searchSources = display.sources, answerLinksRequested = requested)
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
        history: List<Message>,
        forceImageGen: Boolean = false
    ): Message {
        val hasImage = images.isNotEmpty()
        val hasFile = files.isNotEmpty()
        // 1.0.69：「+」菜单勾了「生成图片」→ 跳过关键词猜测直接生图（带图=图生图）。
        // 消费点在 sendMessage（一次性，发完自动取消）；regenerate 不带这个参数
        val skill = when {
            forceImageGen && hasImage -> Skill.IMAGE_EDIT
            forceImageGen -> Skill.IMAGE_GEN
            else -> detectSkill(text, hasImage, hasFile)
        }
        // 强制生图不给 function calling 留猜测机会 —— 工作单的全部意义就是「不猜」，
        // 直接走生图执行器（跳过 tools 分支与搜索）
        if (forceImageGen) {
            return executeSkill(
                skill, text, images, files, langModelName, visualModelName, startTime,
                needsSearch = false, serpResults = "", convId = convId, history = history,
                serpFallbackQuery = ""
            )
        }
        // grok-4.5 走 ccapi 中转不吃 function calling 的 tools 参数，单独排除走关键字兜底
        // File reading is local work, not a tool decision by a model that has not seen it yet.
        if (skill == Skill.FILE) return executeSkill(skill, text, images, files, langModelName, visualModelName,
            startTime, needsSearch = false, serpResults = "", convId = convId, history = history)
        val model = requestModels().language
        val supportsTools = supportsFunctionTools(model)

        // ---- 按需联网：开关只开放能力，是否检索由模型决定 ----
        // 工具类技能不浪费搜索（日历/文档/生图/文件）
        val searchEnabled = _enableWebSearch.value && skill == Skill.TEXT
        val needsSearch = searchEnabled && nativeSearchSupported(model)
        // 深度思考 ⊥ 联网搜索（用户点名两者完全独立）：wide 只看「双开」——
        // 单开深度思考=只推理不搜、单开搜索=一档搜索、双开才「更多轮更广」
        // 原生自动搜索只在明确的工具故障时降级；模型选择不搜不是故障。
        val nativeOk = needsSearch && nativeSearchSupported(model)
        // 模型已经拥有历史；追问由模型补齐，不机械拼上旧地区使新问题同时要求两个地区。
        val query = if (searchEnabled) text else ""
        // 有原生协议时开“自动判断”；否则暴露普通工具，先回复的同一次请求即可决定搜不搜。
        // 只有不支持工具的接口需要一趟短 JSON 规划；规划失败也不能擅自强制搜索。
        val planned = if (searchEnabled && !nativeOk && !supportsTools) planSearchWithModel(query, history) else null
        val deepThinking = model.supportsDeepThinking && deepThinkFor(_perConvSettings.value[convId], model)
        val retrieved = planned?.let { retrieveSearch(query, deepThinking, it, convId) }
        val serpRaw = retrieved?.let { it.text.ifEmpty { SearchPipeline.emptyBlock(query, it.notice) } }.orEmpty()
        val serpResults = serpRaw
        val serpFallbackQuery = if (nativeOk) query else ""

        if (supportsTools) {
            // 纯闲聊不带工具（1.0.74 提速）：砍掉「要不要调工具」的决策开销与误调双轮
            val tools = (if (isChitchat(text) && !hasImage && !hasFile) emptyList() else buildTools(hasImage, hasFile)) +
                if (searchEnabled && !nativeOk && !isChitchat(text)) listOf(SearchIntent.toolDefinition) else emptyList()
            var result = callDeepSeekApiStreaming(needsSearch, serpResults, tools, convId, history, serpFallbackQuery)
            val searchCitations = mutableListOf<Pair<String, String>>()
            var searchRequests = 0
            // 最多两轮资料检索。普通问题不调工具，不多一次 API，也不访问搜索服务。
            for (round in 0..1) {
                if (!searchEnabled || result.toolCalls.none { it.name == SearchIntent.TOOL_NAME }) break
                _liveContent.value = ""
                val continuation = result.requestMessages.toMutableList()
                continuation.add(toolAssistantMessage(result))
                result.toolCalls.forEach { tool ->
                    val toolResult = if (tool.name == SearchIntent.TOOL_NAME) {
                        val intent = SearchIntent.fromArguments(tool.arguments)
                        if (intent == null) "检索参数不完整，请给出具体 queries。"
                        else if (searchRequests >= 2) "本轮检索预算已用完，请使用已有资料。" else {
                            searchRequests++
                            val outcome = retrieveSearch(query, deepThinking, intent, convId)
                            searchCitations.addAll(outcome.entries.map { it.title to it.link })
                            outcome.text.ifBlank { SearchPipeline.emptyBlock(query, outcome.notice) }
                        }
                    } else "本轮先完成检索。如仍需该工具，请在收到资料后再次调用。"
                    continuation.add(mapOf("role" to "tool", "tool_call_id" to tool.id, "content" to toolResult))
                }
                val nextTools = if (round == 0) tools else tools.filterNot { tool ->
                    (tool["function"] as? Map<*, *>)?.get("name") == SearchIntent.TOOL_NAME }
                if (round == 1) continuation.add(mapOf("role" to "system", "content" to "本轮检索预算已用完，请综合现有相关资料直接回答，不再调用搜索。"))
                result = callDeepSeekApiStreaming(tools = nextTools, convId = convId, history = history, continuationMessages = continuation)
            }
            val elapsed = System.currentTimeMillis() - startTime
            return if (result.toolCalls.isNotEmpty()) {
                _liveContent.value = ""
                val toolMessage = executeToolCall(result.toolCalls.first(), text, images, files, langModelName, visualModelName, elapsed, convId)
                toolMessage.copy(searchSources = SearchPresentation.normalize(
                    toolMessage.searchSources.orEmpty().map { it.title to it.url } +
                        retrieved?.entries.orEmpty().map { it.title to it.link } + searchCitations + result.citations))
            } else {
                Message(
                    role = Role.ASSISTANT, content = result.content,
                    searchSources = SearchPresentation.normalize(retrieved?.entries.orEmpty().map { it.title to it.link } + searchCitations + result.citations),
                    modelName = langModelName,
                    thinkingTimeMs = elapsed, reasoningContent = result.reasoning
                )
            }
        } else {
            // XIAOMI 等不支持 tools → 关键字兜底（联网编排与上面同一段，不重复）
            val message = executeSkill(
                skill, text, images, files, langModelName, visualModelName, startTime,
                needsSearch, serpResults, convId, history,
                serpFallbackQuery = serpFallbackQuery
            )
            return message.copy(searchSources = SearchPresentation.normalize(
                message.searchSources.orEmpty().map { it.title to it.url } + retrieved?.entries.orEmpty().map { it.title to it.link }))
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
        elapsed: Long,
        convId: String
    ): Message {
        val args = tool.arguments
        return when (tool.name) {
            "generate_image" -> {
                val prompt = args.optString("prompt") ?: text
                _isGeneratingImage.value = true
                val result = imageResult(prompt)
                val imageUrls = result.urls
                Message(
                    role = Role.ASSISTANT,
                    content = result.error,
                    modelName = visualModelName, thinkingTimeMs = elapsed, imageUrls = imageUrls,
                    failed = imageUrls.isEmpty()
                )
            }
            "edit_image" -> {
                val prompt = args.optString("prompt") ?: text.ifBlank { "请优化这张图片" }
                _isGeneratingImage.value = true
                val encoded = encodeImagesForApi(images)
                val refImage = encoded.firstOrNull()
                val result = if (refImage != null) imageResult(prompt, refImage.first, refImage.second)
                    else GenImages(emptyList(), com.freechat.i18n.LocaleManager.strings().sceneErrorReferences)
                val imageUrls = result.urls
                Message(
                    role = Role.ASSISTANT,
                    content = if (imageUrls.isNotEmpty()) "已根据你的要求处理图片：" else result.error,
                    modelName = visualModelName, thinkingTimeMs = elapsed, imageUrls = imageUrls,
                    failed = imageUrls.isEmpty()
                )
            }
            "analyze_image" -> {
                val prompt = args.optString("question") ?: "请详细描述这张图片的内容"
                val encoded = encodeImagesForApi(images)
                val result = if (encoded.isEmpty()) GenText("图片读取失败，请重试。", failed = true)
                    else callVisionChat(encoded, prompt)
                Message(
                    role = Role.ASSISTANT, content = result.text, failed = result.failed,
                    modelName = requestModels().vision?.displayName ?: "",
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
                val docFailed = doc.error != null || doc.path == null
                val replyContent = when {
                    doc.error != null -> doc.error
                    doc.path != null -> "已为你生成「${doc.fileName}」，点击下方文件即可打开编辑。"
                    else -> "文档生成失败，请换个方式描述试试。"
                }
                Message(
                    role = Role.ASSISTANT, content = replyContent,
                    modelName = langModelName, thinkingTimeMs = elapsed,
                    attachmentPath = doc.path, attachmentName = doc.fileName,
                    failed = docFailed
                )
            }
            "understand_file" -> {
                val question = args.optString("question") ?: text
                val reply = understandFile(question, files, convId)
                Message(role = Role.ASSISTANT, content = reply.text, failed = reply.failed, modelName = langModelName, thinkingTimeMs = elapsed)
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
        history: List<Message>,
        serpFallbackQuery: String = ""
    ): Message = when (skill) {
        Skill.IMAGE_EDIT -> {
            val imagePrompt = text.ifBlank { "请优化这张图片" }
            _isGeneratingImage.value = true
            val encoded = encodeImagesForApi(images)
            val refImage = encoded.firstOrNull()
            val result = if (refImage != null) imageResult(imagePrompt, refImage.first, refImage.second)
                else GenImages(emptyList(), com.freechat.i18n.LocaleManager.strings().sceneErrorReferences)
            val imageUrls = result.urls
            val elapsed = System.currentTimeMillis() - startTime
            val replyContent = if (imageUrls.isNotEmpty()) {
                "已根据你的要求处理图片："
            } else result.error
            Message(
                role = Role.ASSISTANT, content = replyContent,
                modelName = visualModelName,
                thinkingTimeMs = elapsed, imageUrls = imageUrls,
                failed = imageUrls.isEmpty()
            )
        }
        Skill.VISION -> {
            val visionPrompt = text.ifBlank { "请详细描述这张图片的内容" }
            val encoded = encodeImagesForApi(images)
            val result = if (encoded.isEmpty()) {
                GenText("图片读取失败，请重试。", failed = true)
            } else {
                callVisionChat(encoded, visionPrompt)
            }
            val elapsed = System.currentTimeMillis() - startTime
            Message(
                role = Role.ASSISTANT, content = result.text, failed = result.failed,
                modelName = requestModels().vision?.displayName ?: "",
                thinkingTimeMs = elapsed, reasoningContent = _liveReasoning.value
            )
        }
        Skill.IMAGE_GEN -> {
            _isGeneratingImage.value = true
            val result = imageResult(text)
            val imageUrls = result.urls
            val elapsed = System.currentTimeMillis() - startTime
            val replyContent = if (imageUrls.isEmpty()) {
                result.error
            } else ""
            Message(
                role = Role.ASSISTANT, content = replyContent,
                modelName = visualModelName,
                thinkingTimeMs = elapsed, imageUrls = imageUrls,
                failed = imageUrls.isEmpty()
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
            val docFailed = doc.error != null || doc.path == null
            val replyContent = when {
                doc.error != null -> doc.error
                doc.path != null -> "已为你生成「${doc.fileName}」，点击下方文件即可打开编辑。"
                else -> "文档生成失败，请换个方式描述试试。"
            }
            Message(
                role = Role.ASSISTANT, content = replyContent,
                modelName = langModelName, thinkingTimeMs = elapsed,
                attachmentPath = doc.path, attachmentName = doc.fileName,
                failed = docFailed
            )
        }
        Skill.FILE -> {
            val reply = understandFile(text, files, convId, history)
            val elapsed = System.currentTimeMillis() - startTime
            Message(role = Role.ASSISTANT, content = reply.text, failed = reply.failed, modelName = langModelName,
                thinkingTimeMs = elapsed, reasoningContent = _liveReasoning.value)
        }
        Skill.TEXT -> {
            val result = callDeepSeekApiStreaming(needsSearch, serpResults, emptyList(), convId, history, serpFallbackQuery)
            val elapsed = System.currentTimeMillis() - startTime
            Message(
                role = Role.ASSISTANT, content = result.content,
                modelName = langModelName,
                thinkingTimeMs = elapsed, reasoningContent = result.reasoning,
                searchSources = SearchPresentation.normalize(result.citations)
            )
        }
    }

    // ========== 拟人陪伴模式 ==========
    /**
     * 把异常转成一句简短的用户可读错误。
     *
     * 这里以前几乎把所有异常都归成「请求超时，请检查网络连接」—— 用户看到的就成了
     * 「网络明明没问题却一直报网络错误」。现在按真实原因分：断流 / 连不上 / 超时 / 鉴权 /
     * 限流 / 服务端，各自说各自的话，用户才知道该重试、该换网，还是该去改模型配置。
     */
    private fun briefApiError(e: Exception): String {
        val m = e.message.orEmpty()
        val code = Regex("API error (\\d+)").find(m)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            // 地址解析不了：没网 / DNS 被拦 / 自定义接口地址写错
            e is java.net.UnknownHostException -> "连不上服务器，请检查网络，或确认自定义接口地址是否正确。"
            // 连得上网但连不上这台服务器：代理/接口地址/端口问题
            e is java.net.ConnectException -> "无法连接到服务器，请检查网络与接口地址。"
            // 连接中途被掐断：切后台 / 锁屏 / 网络切换时最常见，说清是「断了」，不是「网不好」
            e is java.io.EOFException || e is java.net.SocketException ||
                m.contains("unexpected end of stream", true) || m.contains("stream reset", true) ||
                m.contains("connection reset", true) || m.contains("broken pipe", true) ||
                m.contains("socket closed", true) ->
                "连接被中断，回复没有收完，请重试一次。"
            e is java.net.SocketTimeoutException ->
                if (m.contains("connect", true)) "连接服务器超时，请检查网络连接。"
                else "等待回复超时，回复可能太长或服务端繁忙，请重试一次。"
            m.contains("timeout", true) || m.contains("超时") -> "请求超时，请稍后重试。"
            // 安全连接失败：证书 / 代理 / 时间不对
            e is javax.net.ssl.SSLException -> "安全连接失败（证书或代理问题），请检查网络环境。"
            // 模型类型不对（如把生图模型填进语言模型）
            m.contains("model type", true) || m.contains("not a chat model", true) ||
                m.contains("does not support", true) || m.contains("不支持的模型") ->
                "模型类型错误，请确保模型id对应其模型类型。"
            code == 401 || code == 403 -> "API Key 无效或无权限，请检查模型配置。"
            code == 404 -> "接口地址或模型 ID 不存在，请检查模型配置。"
            code == 429 -> "请求过于频繁或额度不足，请稍后重试。"
            code != null && code >= 500 -> "模型服务端出错（$code），请稍后重试。"
            code == 400 -> "请求被服务端拒绝（400），请检查模型配置或换个说法重试。"
            code != null -> "请求失败（$code），请稍后重试。"
            else -> "请求失败，请稍后重试。"
        }
    }

    /**
     * 是否属于「还没开始收数据就断了」的连接层失败 —— 这类重试一次是安全的（请求多半根本没被受理）。
     * 读超时不算：那可能只是模型慢，重试等于白跑一次生成，还让用户多等一轮。
     */
    private fun isConnectionLevelFailure(e: IOException): Boolean = when (e) {
        is java.net.SocketTimeoutException -> false
        is java.net.UnknownHostException -> false   // 没网，重试也是白试
        else -> true                                // ConnectException / EOF / reset / broken pipe …
    }

    /** 应用每角色独立的语言模型/联网设置（临时覆盖，结束后恢复全局） */
    private fun applyCharacterSettings(character: CharacterProfile?): SettingsSnapshot {
        val snap = SettingsSnapshot(
            _enableWebSearch.value, _showThinking.value, _tempMode.value,
            _lengthMode.value, _autoSummarizeMemory.value
        )
        val ch = character ?: return snap
        ch.enableWebSearch?.let { _enableWebSearch.value = it }
        return snap
    }

    /** 拟人模式情绪：影响回复条数、间隔与是否回复（模型输出情绪标签映射 + 本地关键词兜底） */
    // —— 情绪/作息判定已抽进 freechat-core（CompanionMood.kt），这里只留同名薄委托 ——
    private fun detectCompanionMood(text: String): com.freechat.core.CompanionMood = com.freechat.core.detectCompanionMood(text)

    private fun moodWithResidue(text: String, per: PerConvSettings?): com.freechat.core.CompanionMood =
        com.freechat.core.moodWithResidue(text, per)

    private fun parseEmotionLabel(label: String): com.freechat.core.CompanionMood? = com.freechat.core.parseEmotionLabel(label)

    private fun detectGoodnight(text: String): Boolean = com.freechat.core.detectGoodnight(text)

    private fun generateSleepSchedule(ch: CharacterProfile): Pair<Int, Int> = com.freechat.core.generateSleepSchedule(ch)

    /** 当前是否在 AI 的睡觉窗口内（开启模拟作息才生效；当天首次调用时生成作息，按对话隔离） */
    private fun isSleepingNow(convId: String, character: CharacterProfile?): Boolean {
        val ch = character ?: return false
        if (ch.isNarrativeMode()) return false  // 动作演绎/剧情补足无真实作息，时间以用户设定为准
        // 1.0.73：时间感知包含作息；老档案只开了「模拟作息」的也照常生效（零迁移）
        if (!(ch.timePerception || ch.sleepSimulation)) return false
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

    // —— CompanionReply 已抽进 freechat-core（见文件头 import）——

    // —— ProactiveSignal / ProactiveFire 已抽进 freechat-core（CompanionProactive.kt，见文件头 import）——

    private fun stripProactiveDirective(raw: String): Pair<String, ProactiveSignal?> = com.freechat.core.stripProactiveDirective(raw)

    private fun decodeProactivePayload(payload: String): ProactiveSignal = com.freechat.core.decodeProactivePayload(payload)

    private fun parseProactiveTime(t: String): Long? = com.freechat.core.parseProactiveTime(t)

    /** 拟人模式的一轮用户输入：文本 + 附带的图片路径 */
    private data class CompanionTurn(val text: String, val imagePaths: List<String> = emptyList())

    /** 拟人陪伴：非流式生成 + 随机思考延迟 + 情绪化随机条数/间隔，逐条显示，模拟真人节奏（按对话隔离） */
    private suspend fun generateCompanionReply(convId: String, character: CharacterProfile?, working: MutableList<Message>, text: String, imagePaths: List<String>, startTime: Long, batchSize: Int = 1, proactive: ProactiveFire? = null, regenerating: Boolean = false, replyTimestamp: Long? = null): Unit = withContext(resolveModels(convId, character)) {
        val langModelName = requestModels().language.displayName
        val replyStart = working.size
        val replyId = convGenerationReplyIds[convId] ?: UUID.randomUUID().toString()
        val userSourceIds = working.filterNot { it.sceneVisualization }.takeLastWhile { it.role == Role.USER }.map { it.id }
        val snapshot = applyCharacterSettings(character)
        // ⚠️ 拟人模式**不叠**「新规则」（PerConvSettings），这一层只属于标准模式 ——
        // 「新规则」页在拟人对话里根本进不去（对话页那个调节按钮按模式分流：拟人 → 模拟设定 /
        // 角色档案，标准 → 新规则），两个模式各有一套「只管这条对话」的机制，别互相串。
        // 所以这里只叠角色档案那一层，用户拍板（2026-09-19）：新规则不该影响拟人模式。
        // 曾一度在这里补过 applyPerConvSettings(convId)：那在正常路径上是空操作
        // （拟人对话不会有新规则记录），但语义是错的，已撤。
        // 返回值不接：外层 finally 的 restoreSettings(snapshot) 会把它还原成全局。
        // 主动智能：这次不是「回用户」，而是「闹钟把 TA 叫醒了，现在决定要不要主动开口」
        val proactivePrompt = proactive?.let { buildProactiveFirePrompt(it, character) }
        val elapsedJob = if (character?.isNarrativeMode() == true) CoroutineScope(coroutineContext).launch {
            while (isActive) {
                if (_currentConversationId.value == convId) _thinkingTimeMs.value = System.currentTimeMillis() - startTime
                delay(200)
            }
        } else null
        try {
            // 模拟作息：睡觉窗口内沉默不回复（记录漏回，起床后自然解释）
            if (!regenerating && isSleepingNow(convId, character)) {
                sleepingMissedMessages[convId] = (sleepingMissedMessages[convId] ?: 0) + 1
                return@withContext
            }
            // 模拟真人：先随机等待，不刚发就显示「正在输入」
            delay(com.freechat.core.CompanionRhythm.initialTypingDelayMs())
            setConvTyping(convId, true)

            // 图片：先识图分析，把识图结果作为上下文喂给回复（拟人结合人设评论图片）
            var imageContext = ""
            if (imagePaths.isNotEmpty() && com.freechat.data.CharacterPresentationPolicy.usesPhotoRecognition(character?.normalized()?.dialogueMode)) {
                val encoded = encodeImagesForApi(imagePaths.map { PendingImage(it, detectMime(it)) })
                if (encoded.isNotEmpty()) {
                    imageContext = callVisionChat(encoded, "请用几句话描述这张图片：画面里有什么、什么场景、有什么值得注意的细节，口语化一点。").text
                    // 回填到用户图片消息，后续追问时作为「图片内容」注入，避免识图结果丢失
                    val ui = working.indexOfLast { it.role == Role.USER && it.imagePaths.isNotEmpty() }
                    if (ui >= 0 && imageContext.isNotBlank()) working[ui] = working[ui].copy(imageContext = imageContext)
                }
            }

            var reply = callCompanionApi(convId, character, working, text, imageContext, batchSize = batchSize, proactivePrompt = proactivePrompt)
            var segments = reply.segments

            // 主动智能：模型在这一轮选择了不开口（只输出指令行/空正文）是合法的，且**不加兜底重试**——
            // 硬逼它说点什么，就变成「每次到点都必须冒一句话」，恰恰是这功能最该避免的黏人。
            // 但指令必须在这里就落地：提示词教的「跳过 +30m」「只定 +2h」正是这种「正文为空、只剩指令」
            // 的形态，直接 return 会把模型刚给自己定的下一次一起丢掉，而服务收尾又会把旧计划清掉
            // → 整条链无声消失（表现为「明明说了半小时后再来，之后再也没动静」）。
            if (proactive != null && segments.isEmpty()) {
                applyProactiveSignal(convId, character, reply.proactive)
                setConvTyping(convId, false)
                return@withContext
            }

            // 空回复兜底：重试一次（强调必须输出正文），仍空则发个省略号（自然无语，不显示「（空回复）」）
            if (segments.isEmpty()) {
                reply = callCompanionApi(convId, character, working, text, imageContext, forceReply = true, batchSize = batchSize)
                segments = reply.segments
            }

            if (segments.isEmpty()) {
                setConvTyping(convId, false)
                working.add(Message(
                    id = replyId,
                    role = Role.ASSISTANT, content = "…",
                    timestamp = replyTimestamp ?: System.currentTimeMillis(),
                    modelName = langModelName, mode = ChatMode.COMPANION,
                    thinkingTimeMs = System.currentTimeMillis() - startTime
                ))
                persistConversationMessages(convId, working)
                return@withContext
            }

            // 情绪：模型输出优先，本地关键词兜底（动作演绎/剧情补足不解析情绪，走简化节奏）
            val narrativeMode = character?.isNarrativeMode() == true
            // 叙事模式长度校验：偏离区间太多则带纠偏指令重试一次，尽量把字数压进设定区间
            if (narrativeMode) {
                // 篇幅不再用绝对字数卡（避免「短档被写死成 50 字」），只留一道防塌陷下限：
                // 选了「长/超长」却只回三行时补一次，提示也不提具体字数
                val totalLen = countChars(segments.joinToString(""))
                if (totalLen < plotLengthFloor(character?.plotLength ?: 1)) {
                    val retried = callCompanionApi(
                        convId, character, working, text, imageContext,
                        forceReply = true, batchSize = batchSize,
                        lengthHint = "上次回复太短了，明显达不到当前「单次回复长度」档应有的体量。请把这一段按该档的篇幅写足。"
                    )
                    if (retried.segments.isNotEmpty()) {
                        reply = retried
                        segments = retried.segments
                    }
                }
            }
            val mood = if (narrativeMode) CompanionMood.NEUTRAL else (parseEmotionLabel(reply.emotion) ?: moodWithResidue(text, getPerConvSettings(convId)))

            // 情绪决定回复条数（随机）；叙事模式最多 3 段、全部展示
            val maxCount = com.freechat.core.CompanionRhythm.segmentCount(mood, narrativeMode, regenerating, segments.size)
            val toShow = segments.take(maxCount)

            if (toShow.isEmpty()) {
                // 生气不回复也是「这一轮的结果」，指令同样要落地（比如「气还没消，改到 +2h」）
                applyProactiveSignal(convId, character, reply.proactive)
                setConvTyping(convId, false)
                return@withContext  // 生气不回复
            }

            for ((i, seg) in toShow.withIndex()) {
                setConvTyping(convId, true)
                // 每条间隔随机（情绪影响），时而快时而慢
                val interval = com.freechat.core.CompanionRhythm.segmentIntervalMs(mood, narrativeMode)
                delay(interval)
                setConvTyping(convId, false)
                working.add(Message(
                    id = if (i == 0) replyId else UUID.randomUUID().toString(),
                    role = Role.ASSISTANT, content = seg,
                    timestamp = replyTimestamp?.plus(i) ?: System.currentTimeMillis(),
                    modelName = langModelName, mode = ChatMode.COMPANION,
                    thinkingTimeMs = System.currentTimeMillis() - startTime,
                    searchSources = reply.sources
                ).let { separateCitationLinks(it, text) })
                persistConversationMessages(convId, working)
                if (i < toShow.size - 1) setConvTyping(convId, true)
            }

            // ★ 回复完成收尾：清零漏回计数（已在提示词里解释过），异步写入记忆
            // 注：关系/亲密度不再由这里评估更新——没有亲密度数值、没有按等级映射的关系模板，亲疏尺度全靠提示词里让 AI 结合人设与上下文自行判断
            sleepingMissedMessages[convId] = 0
            val replyText = toShow.joinToString("\n")
            // 主动智能：模型可以在这条回复里给自己定下一次（也可以撤销上一次）。
            // sent=true 表示这一轮真的开口了 —— 额度只在这里记（由 applyProactiveSignal 在
            // 同一个后台任务里先记再挂新闹钟：先记后挂，最小间隔才会把「刚发过」算进去）
            applyProactiveSignal(convId, character, reply.proactive, sent = proactive != null)
            proactiveForceNotify = proactive?.forceNotify == true
            try {
                notifyReplyIfBackground(character?.name?.ifBlank { "FreeChat" } ?: "FreeChat", replyText)
            } finally {
                proactiveForceNotify = false
            }
            // 主动开口这一轮不写记忆：喂给摘要器的「用户说了什么」是空的，
            // 硬造一条只会污染记忆库；这段话本身已经落在聊天记录里，下次上下文照样带上
            if (replyText.isNotBlank() && proactive == null) {
                viewModelScope.launch {
                    summarizeAndRemember(convId, text, Message(role = Role.ASSISTANT, content = replyText, mode = ChatMode.COMPANION),
                        character?.highQualityMemory == true, character?.isNarrativeMode() == true, character,
                        sourceMessageIds = userSourceIds + working.drop(replyStart).map { it.id })
                }
            }
        } finally {
            elapsedJob?.cancel()
            restoreSettings(snapshot)
            setConvTyping(convId, false)
        }
    }

    // ===================== 主动智能 =====================

    /**
     * 落地模型给的主动智能指令。
     * 没给指令（null）= 保持原有计划不变 —— 这是**有意的**：模型只在「想改」的时候才写那一行，
     * 若每次回复都强制它重新表态，它会在没想清楚的时候乱定时间。
     */
    private fun applyProactiveSignal(convId: String, character: CharacterProfile?, sig: ProactiveSignal?, sent: Boolean = false) {
        // 读账本、挂闹钟都是磁盘与系统调用，而普通回复的收尾在主线程上，别占着它。
        // 例外：闹钟唤醒的冷路径（跑在服务里）本来就在后台线程 —— 那就同步做完。
        // 异步派发会让「服务收尾删旧计划」跑在「新计划落盘」前面，中间那段时间差里新计划会被误删。
        if (Looper.myLooper() == Looper.getMainLooper()) {
            proactiveScope.launch { applyProactiveSignalBlocking(convId, character, sig, sent) }
        } else {
            applyProactiveSignalBlocking(convId, character, sig, sent)
        }
    }

    private fun applyProactiveSignalBlocking(convId: String, character: CharacterProfile?, sig: ProactiveSignal?, sent: Boolean = false) {
        val app = getApplication<Application>()
        // 先记额度再挂新闹钟：挂的时候会按「上一条主动消息」压最小间隔，
        // 顺序反了的话刚发出去的这条不算数，模型写「+5m」就能立刻再发一条
        if (sent) proactiveStore.markFired(convId)
        val existing = proactiveStore.get(convId)
        // 开关关了、或档位不是「微信聊天」：清掉遗留计划（留着闹钟到点会白唤醒一次，
        // 还会让模型收到一条它已经不该管的「你之前定的提醒」）。
        // 主动智能只属于微信聊天档，另外两档是用户自己在推剧情，角色不该自己跳出来说话。
        if (character?.proactiveEnabled != true || character.dialogueMode != DialogueMode.WECHAT) {
            if (existing != null) ProactiveScheduler.cancel(app, convId)
            return
        }
        when {
            sig == null -> Unit                                   // 没表态：原计划不动
            sig.cancel -> ProactiveScheduler.cancel(app, convId)
            sig.atMillis != null -> ProactiveScheduler.schedule(app, ProactiveRequest(
                convId = convId,
                characterName = character.name,
                dueAt = sig.atMillis!!,   // when 分支已判非空；跨模块字段不能 smart cast
                reason = sig.reason,
                createdAt = System.currentTimeMillis()
            ))
            else -> Unit                                          // 空指令（如「跳过」）：什么都不改
        }
    }

    /**
     * 【主动智能】闹钟到点后的这一次判断：把上下文交给模型，由它决定现在开不开口、说什么。
     * 由 ProactiveService 调用 —— App 被划掉时这个 VM 是服务新建的实例，
     * 所以这里的一切状态都必须能从磁盘重建，不能依赖任何内存里的会话状态。
     */
    suspend fun runProactive(convId: String, forceNotify: Boolean = false) {
        val req = proactiveStore.get(convId) ?: return
        // 这个对话正在生成中（用户刚发消息 / 上一条还在回）：这次计划已经过时了，
        // 正在跑的那一轮回复会拿到「你之前定的提醒」，由模型决定要不要重新定。
        if (proactiveJobs.containsKey(convId) || convLoading[convId] == true || convTyping[convId] == true) return

        // 冷启动时设置还没从 DataStore 流过来（init 里的收集器是异步的），
        // 不先等一次就会拿内置默认模型发请求 —— 用户自定义的 key / 模型全被绕过去了
        awaitSettingsLoaded()

        // 同理：_conversations 也可能还是空的，直接从磁盘读（它才是权威）。
        // 置顶集合也必须先读：落盘时 isPinned 取自 pinnedIds，冷启动时它还是空集，
        // 会把这个对话的置顶状态顺手抹成 false（侧滑栏上就是「置顶突然没了」）。
        pinnedIds.value = settingsRepo.pinnedConversationIds.first()
        val convs = withContext(Dispatchers.IO) { loadConversations() }
        _conversations.value = sortConversations(convs)
        val conv = convs.firstOrNull { it.id == convId } ?: return
        val character = conv.characterProfile?.normalized() ?: return
        if (!character.proactiveEnabled) return

        // 主动智能只在「微信聊天」档生效：小说文本 / 动作演绎是用户在主导演剧情，
        // 角色不该自己跳出来发消息。切档之后残留的旧闹钟也要在这里就地取消掉，
        // 否则用户会把档位切走了、过一会儿仍然收到一条主动消息，说不清是哪来的。
        if (character.dialogueMode != DialogueMode.WECHAT) {
            Log.d("FreeChat", "Proactive skipped: mode=${character.dialogueMode}")
            ProactiveScheduler.cancel(getApplication(), convId)
            return
        }

        // 每日上限：到点 ≠ 必须开口。额度用完了就挪到明天早上，**不是把计划删掉** ——
        // 删掉就是「今天聊得晚，明天 TA 再也不会想起你」，额度限制的是频率，不是承诺
        if (!proactiveStore.canFire(convId)) {
            if (req.retries < 3) ProactiveScheduler.schedule(getApplication(), req.copy(
                dueAt = proactiveStore.nextMorning(System.currentTimeMillis()),
                retries = req.retries + 1
            ))
            return
        }

        val working = withContext(Dispatchers.IO) { loadMessages(convId).toMutableList() }
        if (working.isEmpty()) return
        // 定下这个时间之后用户又说过话 —— 这里**不**直接放弃（旧版就是这么写的，结果提示词里
        // 「还想在那个时间找他，就什么都不用写」那条路彻底走不通：用户哪怕只回一个「好」，
        // 到点也会被判定过期而静默删掉）。该不该提、现在还想不想说，让模型拿着完整上下文自己判断，
        // 它手上本来就有一个「跳过」的选项。我们只把「对方之后又说过话」这件事告诉它。
        val sinceCount = working.count { it.timestamp > req.createdAt && it.role == Role.USER }
        // 作息模拟：到点时 TA 正在睡觉 → 不打扰，改到睡醒之后（不消耗当天额度）
        if (isSleepingNow(convId, character)) { rescheduleAfterWake(convId, character, req); return }

        val self = coroutineContext[Job]
        if (self != null) proactiveJobs[convId] = self
        try {
            generateCompanionReply(
                convId, character, working,
                // 这段文字只用于「检索记忆」和「判断要不要联网搜索」，不会作为用户消息发给模型
                req.reason.ifBlank { "想找对方说说话" },
                emptyList(), System.currentTimeMillis(),
                proactive = ProactiveFire(req.reason, sinceCount, forceNotify)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 断网 / 服务端 5xx：这次没发出去。别让计划就这么没了（额度还没记、话也没说），
            // 过几分钟再试一次，连试几次都不行才放弃 —— 否则「到点没网」就等于永久失去这条计划
            Log.e("FreeChat", "Proactive generation failed", e)
            if (req.retries < ProactiveStore.MAX_RETRIES) {
                ProactiveScheduler.schedule(getApplication(), req.copy(
                    dueAt = System.currentTimeMillis() + ProactiveStore.RETRY_DELAY_MS,
                    retries = req.retries + 1
                ))
            }
        } finally {
            if (proactiveJobs[convId] === self) proactiveJobs.remove(convId)
        }
    }

    /**
     * 等设置从 DataStore 首次加载完成（只用于后台冷启动这条路径）。
     * 直接把几个关键项各读一次：比等收集器跑完更确定，也不用给整个 init 加一个「加载完成」标志位。
     */
    private suspend fun awaitSettingsLoaded() {
        try {
            val custom = settingsRepo.customModels.first()
            _customModels.value = custom
            settingsRepo.selectedModelId.first().takeIf { it.isNotEmpty() }?.let { id ->
                modelsOfType(ModelType.LANGUAGE).find { it.id == id }?.let { _selectedModel.value = it }
            }
            settingsRepo.selectedVisualModelId.first().takeIf { it.isNotEmpty() }?.let { id ->
                modelsOfType(ModelType.VISUAL).find { it.id == id }?.let { _selectedVisualModel.value = it }
            }
            settingsRepo.selectedVisionModelId.first().takeIf { it.isNotEmpty() }?.let { id ->
                modelsOfType(ModelType.VISION).find { it.id == id }?.let { _selectedVisionModel.value = it }
            }
            _enableWebSearch.value = settingsRepo.enableWebSearch.first()
            _autoSummarizeMemory.value = settingsRepo.autoSummarizeMemory.first()
        } catch (e: Exception) {
            Log.e("FreeChat", "awaitSettingsLoaded failed", e)
        }
    }

    /** 到点时 TA 正在睡觉：挪到睡醒之后。理由改成「刚睡醒」，createdAt 保持不动 ——
     *  它是「这条计划定于何时」的标记，到点时要拿它数「对方之后又说过几条」。 */
    private fun rescheduleAfterWake(convId: String, character: CharacterProfile, req: ProactiveRequest) {
        if (req.retries >= 3) return
        val wh = wakeAtHour[convId] ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, wh)
            set(Calendar.MINUTE, 40)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        ProactiveScheduler.schedule(getApplication(), req.copy(
            dueAt = cal.timeInMillis + kotlin.random.Random.nextLong(0, 40 * 60 * 1000L),
            reason = "你刚睡醒，之前想找对方",
            retries = req.retries + 1
        ))
    }

    /** 到点唤醒后的那一轮提示：不是「回用户」，而是「你自己定的时间到了，现在决定要不要开口」 */
    private fun buildProactiveFirePrompt(fire: ProactiveFire, character: CharacterProfile?): String =
        com.freechat.core.buildProactiveFirePrompt(fire, character)

    /** 从模型输出里提取 JSON 对象（容错：兼容首尾多余文字 / markdown 代码块） */
    private fun extractJsonObject(raw: String): JsonObject? = com.freechat.core.JsonLoose.extractObject(raw)

    /**
     * 拟人模式非流式请求：只在「连接层失败」时重试一次。
     * 读超时不重试（请求已经发出去了，重试只会让用户多等一遍）、DNS 解析失败也不重试（那是没网）。
     * 返回 (HTTP 状态码, 响应体文本)，body 保证已经读完并关闭，连接会归还连接池。
     */
    private suspend fun executeCompanionCall(request: Request): Pair<Int, String> {
        var lastError: IOException? = null
        for (attempt in 0..1) {
            // 已经取消：不要再补发一次请求（和流式那边同一个道理）
            if (!coroutineContext.isActive) throw CancellationException("cancelled before retry")
            if (attempt > 0) {
                Log.w("FreeChat", "Companion retry: 换新连接重发一次")
                // 半死的连接池成员是「切后台后偶发失败」的元凶，重试必须换一条新连接
                client.connectionPool.evictAll()
            }
            try {
                // use{}：不论成功失败，body 都会被关闭，连接才会归还连接池
                client.newCall(request).execute().use { resp ->
                    return resp.code to (resp.body?.string() ?: "")
                }
            } catch (e: IOException) {
                lastError = e
                Log.w("FreeChat", "Companion call failed (attempt ${attempt + 1})", e)
                if (!isConnectionLevelFailure(e)) throw e
            }
        }
        throw lastError ?: IOException("connection failed")
    }

    /**
     * 「深度推演」的第一趟：让模型**以这个角色的身份**先在心里过一遍，再开口。
     *
     * 这一趟存在的理由，是正式那一趟做不到的事：模型在写回复时是"边说边想"的，
     * 注意力被「怎么说得好听」占满，反而容易漏掉该想起的往事、看错对方的真实意思。
     * 拆成两趟之后，第一趟只管想（不用管措辞），第二趟只管说（材料已经摆好了）。
     *
     * 返回值会作为一条**内部批注**注入正式那一趟，界面上永远看不到它。
     * 任何一步失败都返回空串 —— 这只是锦上添花，不能因为它把正经回复搞挂了。
     */
    private suspend fun runDeepPrepPass(
        convId: String,
        character: CharacterProfile?,
        working: List<Message>,
        text: String
    ): String = withContext(Dispatchers.IO) {
        runCatching {
            val model = requestModels().language
            val (url, key) = routeModelEndpoint(model)

            val msgs = mutableListOf<Map<String, Any?>>()
            // 用同一份人设：这一趟要"以角色的身份"想，不给它人设就只是在做阅读理解
            buildCompanionSystemPrompt(character, convId, working).takeIf { it.isNotEmpty() }
                ?.let { msgs.add(mapOf("role" to "system", "content" to it)) }
            // 记忆给全：这一趟的目的之一就是找出"相关的往事"
            if (_autoSummarizeMemory.value) {
                memoryManager.buildMemoryContext(convId, highQuality = true).takeIf { it.isNotBlank() }
                    ?.let { msgs.add(mapOf("role" to "system", "content" to it)) }
            }
            var prepPrevTs = 0L
            msgs.addAll(pickHistory(working, historyBudgetTokens(enhanced = true)).map { m ->
                val prefix = historyTimePrefix(prepPrevTs, m.timestamp, character?.isNarrativeMode() == true)
                prepPrevTs = m.timestamp
                mapOf<String, Any?>(
                    "role" to when (m.role) { Role.USER -> "user"; Role.ASSISTANT -> "assistant"; else -> "system" },
                    "content" to prefix + m.content
                )
            })
            msgs.add(mapOf("role" to "system", "content" to """【现在先别回话，只做推演】
开口之前，先把这一轮想清楚。按下面的顺序写，每条两三句，不要写成小作文，也不要写任何台词：
1. 对方这句话真正在说什么——字面意思之外，他此刻是什么情绪、想要什么、有没有潜台词。
   如果只是闲话，就写"只是闲话"。
2. 此刻相关的往事：从上面的记忆和聊天记录里，**原文摘出**和这一轮有关的人、事、约定、时间、数字。
   一条都没有就写"无"。这一条最重要，宁可多写也不要漏。
3. 你自己此刻的状态：你现在什么心情、对对方什么态度、这一轮你打算怎么应对（接话/岔开/追问/拒绝/主动推进）。
4. 一个检索词：把你觉得**还需要再回想一下**的主题写成几个关键词，用空格隔开（比如"生日 礼物 上周答应"）。
   没有就写"无"。

严格按这四行输出，每行以 `1.` `2.` `3.` `4.` 开头。"""))

            val body = gson.toJson(mapOf(
                "model" to model.id, "messages" to msgs, "stream" to false,
                "temperature" to companionTemperature(character?.aiCreativity)
            )).toRequestBody(JSON_MEDIA)
            val (code, rBody) = executeCompanionCall(
                Request.Builder().url(url)
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json").post(body).build()
            )
            if (code !in 200..299) return@runCatching ""
            val note = JsonParser.parseString(rBody).asJsonObject
                .getAsJsonArray("choices")?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString?.trim().orEmpty()
            if (note.isBlank()) return@runCatching ""

            // 第 4 行是它自己报的检索词 —— 拿它再检索一次记忆。
            // 这是整件事里最值钱的一步：第一遍检索是按用户这句话做的，而"该想起什么"
            // 往往不在这句话的字面里（对方说"随便"，真正相关的是三天前那句"我周三有空"）。
            val query = note.lines()
                .firstOrNull { it.trimStart().startsWith("4.") }
                ?.removePrefix("4.")?.trim().orEmpty()
                .takeIf { it.isNotBlank() && it != "无" }
            val second = query?.let {
                runCatching { memoryManager.buildMemoryContext(convId, highQuality = true) }.getOrNull()
            }.orEmpty()

            buildString {
                append("【开口之前，你心里已经过了一遍 —— 这是你自己的盘算，不是别人对你说的话】\n")
                append(note.trim())
                if (second.isNotBlank()) {
                    append("\n\n【顺着刚才的思路，你又想起的一些事】\n")
                    append(second.trim())
                }
                append("\n\n要求：这些是你**已经知道**的东西，直接拿来用。" +
                    "绝不要复述、不要总结、不要说「我刚刚想了想」——想完了就直接开口，像真人一样。")
            }
        }.getOrElse { e ->
            // 取消必须放行（戒律 7）：吞掉 CancellationException 会让被打断的这一趟
            // 假装「推演没做成」继续往下生成 —— 旧回复照样落库，串进新消息里
            if (e is kotlinx.coroutines.CancellationException) throw e
            ""
        }
    }

    /** 拟人模式非流式 API 调用：返回结构化结果（情绪标签 + 分条回复）；按对话隔离传入 convId/角色/历史 */
    private suspend fun callCompanionApi(convId: String, character: CharacterProfile?, working: List<Message>, text: String, imageContext: String = "", forceReply: Boolean = false, batchSize: Int = 1, lengthHint: String? = null, proactivePrompt: String? = null): CompanionReply = withContext(Dispatchers.IO) {
        val model = requestModels().language
        val searchOn = _enableWebSearch.value
        val searchToolAvailable = searchOn && supportsFunctionTools(model) && !isChitchat(text)
        val (url, key) = routeModelEndpoint(model)
        advanceGeneration(convId, com.freechat.data.GenerationPhase.UNDERSTANDING)
        // 1.0.71：增强检索的实际生效 = 角色开关本身（1M 声明/锁定档已随上下文二值化移除 ——
        // 开关开 =增强检索 + 1M 预算，关 = 普通检索 + 256K）。
        val enhancedActive = character?.highQualityMemory == true

        // —— 装配材料：记忆/深度推演/引用/检索在端上算好，装配顺序在 freechat-core 的
        //    CompanionRequestBuilder（golden 钉住：格式铁律压尾等一整套次序）——
        val memoryContext = if (_autoSummarizeMemory.value)
            memoryManager.buildMemoryContext(convId, highQuality = enhancedActive) else ""
        val deepPrepNote = if (character?.deepThinking == true && enhancedActive) runDeepPrepPass(convId, character, working, text) else ""
        val quoteText = pendingQuoteText.also { pendingQuoteText = null }
        val history = pickHistory(working, historyBudgetTokens(enhancedActive))
        var searchSources = emptyList<com.freechat.model.SearchCitation>()
        var serpBlock = ""
        // 联网搜索开关。这里只读 `_enableWebSearch` 就够了 —— 拟人模式下它已经**叠好了两层**：
        // 全局默认 → 角色档案的值（applyCharacterSettings，null 则不覆盖）。
        // 「新规则」不参与：拟人对话没有新规则那一层（见 generateCompanionReply 里的说明）。
        if (searchOn && !searchToolAvailable) {
            val intent = planSearchWithModel(text, working)
            if (intent != null) {
                val outcome = retrieveSearch(text, companionDeepThink(character, model), intent, convId)
                searchSources = SearchPresentation.normalize(outcome.entries.map { it.title to it.link })
                serpBlock = outcome.text.ifBlank { SearchPipeline.emptyBlock(text, outcome.notice) }
            }
        }
        val messages = com.freechat.core.CompanionRequestBuilder.build(
            character = character,
            systemPrompt = buildCompanionSystemPrompt(character, convId, working),
            memoryContext = memoryContext,
            deepPrepNote = deepPrepNote,
            quoteText = quoteText,
            history = history,
            imageContext = imageContext,
            forceReply = forceReply,
            lengthHint = lengthHint,
            proactivePrompt = proactivePrompt,
            batchSize = batchSize,
            serpBlock = serpBlock,
            searchGuidance = if (searchToolAvailable) SearchIntent.guidance else "",
            includeReasoningContent = searchToolAvailable
        ).toMutableList()

        // 采样温度由「AI创造力」线性映射（1.0→0.85 … 5.0→1.05 … 10.0→1.30；旧版写死 1.0，正好对应 4 档）
        var searchCallsAllowed = searchToolAvailable
        suspend fun requestReply(allowSearch: Boolean): JsonObject {
            searchCallsAllowed = allowSearch
            val body = gson.toJson(mapOf(
                "model" to model.id, "messages" to messages, "stream" to false, "temperature" to companionTemperature(character?.aiCreativity)
            ) + deepThinkExtras(companionDeepThink(character, model)) + if (allowSearch)
                mapOf("tools" to listOf(SearchIntent.toolDefinition), "tool_choice" to "auto") else emptyMap()).toRequestBody(JSON_MEDIA)
            val (respCode, rBody) = executeCompanionCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json").post(body).build())
            if (respCode !in 200..299) throw Exception("API error $respCode: ${rBody.take(200)}")
            val root = JsonParser.parseString(rBody).asJsonObject
            searchSources = SearchPresentation.normalize(searchSources.map { it.title to it.url } + NativeSearchEvidence.citations(root))
            return root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject?.getAsJsonObject("message") ?: JsonObject()
        }
        var reply = try { requestReply(searchToolAvailable) } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!searchToolAvailable || !unsupportedTools(e)) throw e
            blockFunctionTools(model)
            val intent = planSearchWithModel(text, working)
            intent?.let {
                val outcome = retrieveSearch(text, companionDeepThink(character), it, convId)
                searchSources = SearchPresentation.normalize(outcome.entries.map { entry -> entry.title to entry.link })
                messages.add(mapOf("role" to "system", "content" to outcome.text.ifBlank { SearchPipeline.emptyBlock(text, outcome.notice) }))
            }
            requestReply(false)
        }
        for (round in 0..1) {
            if (!searchCallsAllowed) break
            val calls = reply.getAsJsonArray("tool_calls")?.filter { it.isJsonObject }.orEmpty()
            if (calls.isEmpty()) break
            val assistant = gson.fromJson<Map<String, Any?>>(reply, object : TypeToken<Map<String, Any?>>() {}.type).toMutableMap()
            assistant.putIfAbsent("reasoning_content", "")
            messages.add(assistant)
            var requests = 0
            calls.forEach { item ->
                val tool = item.asJsonObject
                val fn = tool.getAsJsonObject("function")
                val intent = if (fn?.optString("name") == SearchIntent.TOOL_NAME)
                    runCatching { SearchIntent.fromArguments(JsonParser.parseString(fn.optString("arguments")).asJsonObject) }.getOrNull() else null
                val outcome = if (intent != null && requests++ < 1) retrieveSearch(text, companionDeepThink(character), intent, convId) else null
                if (outcome != null) searchSources = SearchPresentation.normalize(searchSources.map { it.title to it.url } + outcome.entries.map { it.title to it.link })
                messages.add(mapOf("role" to "tool", "tool_call_id" to tool.optString("id"), "content" to
                    (outcome?.text?.ifBlank { SearchPipeline.emptyBlock(text, outcome.notice) } ?: "请将需要检索的主题合并到 queries；不要重复调用。")))
            }
            messages.add(mapOf("role" to "system", "content" to modeFormatRule(character?.normalized()?.dialogueMode ?: DialogueMode.WECHAT)))
            reply = requestReply(round == 0)
        }
        advanceGeneration(convId, com.freechat.data.GenerationPhase.DRAFTING)
        val rawOrig = reply.optString("content")?.trim().orEmpty()
        // 解析（指令剥离/情绪标签/分条/方括号清洗）在 freechat-core 的 CompanionReplyParser
        val parsed = com.freechat.core.CompanionReplyParser.parse(rawOrig, character?.isNarrativeMode() == true)
        var bodyLines = parsed.bodyLines

        // ★ 格式自检（1.0.53）：本地先判一眼「这段话像不像当前档位写的」，一眼能判的违规
        //   （三档互相穿帮的写法，见 isFormatViolation）就带指令**只改格式重写一次**。
        //   重写是独立的一趟小调用，失败或改完仍不合规就原样放行 —— 格式差一点总比回复丢掉强。
        val mode = character?.normalized()?.dialogueMode ?: DialogueMode.WECHAT
        val joinedBody = bodyLines.joinToString("\n")
        if (joinedBody.isNotBlank() && isFormatViolation(joinedBody, mode)) {
            val fixed = repairFormat(joinedBody, mode)
            if (fixed.isNotBlank() && !isFormatViolation(fixed, mode)) {
                Log.d("FreeChat", "格式自检：$mode 档违规，已按格式重写一次")
                bodyLines = fixed.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                Log.d("FreeChat", "格式自检：$mode 档违规，但重写未成功，保留原回复")
            }
        }

        CompanionReply(parsed.emotion, bodyLines, parsed.proactive, searchSources)
    }

    /**
     * 当前档位的「格式铁律」——**极简版**，专门用来贴在生成前的最后一条（见 [callCompanionApi]）。
     *
     * 为什么另写一份、不直接复用系统提示里那几段长的：位置比长度重要。长的那些负责把
     * 「怎么写才好看」讲透，这一条只负责在动笔前把**格式**再说一遍，短到不会被跳过。
     *
     * [repair] = true 时换成给「只改格式」那一趟看的说法（同一套规矩，但主语变成「把下面这段改成…」）。
     */
    private fun modeFormatRule(mode: Int, repair: Boolean = false): String = com.freechat.core.CompanionPrompts.modeFormatRule(mode, repair)

    /**
     * 格式自检：这段文字**看起来像不像当前档位写的**。
     *
     * 只抓「三档互相穿帮」这种一眼能判的硬伤，不做语义判断——判得越保守，误伤越少：
     *  · 剧情补足：出现「」= 动作演绎档的台词写法（本档一律用双引号“”）；
     *  · 动作演绎：出现双引号“”当台词 = 剧情补足档的写法；
     *              或通篇没有一句「」台词、却又是好几行短句 = 微信聊天档的写法；
     *  · 微信聊天：出现「」、成对的星号动作、行首的括号动作、或单行超过 100 字的一整段描写
     *              = 另外两档的写法。
     * 命中就走 [repairFormat] 只改格式重写一次；没命中就原样放行（这是绝大多数情况）。
     */
    private fun isFormatViolation(text: String, mode: Int): Boolean = com.freechat.core.CompanionPrompts.isFormatViolation(text, mode)

    /**
     * 格式违规时的「只改格式」重写（1.0.53）。
     *
     * 刻意用一趟**独立的小调用**，而不是把整份人设重新喂一遍：这一趟只需要会改格式，
     * 人设给得越多，它越容易顺手把内容也重写一遍——而内容必须原样留着。
     * 失败（网络、模型拒绝、空回复）一律返回原文。
     */
    private suspend fun repairFormat(text: String, mode: Int): String = withContext(Dispatchers.IO) {
        runCatching {
            val model = requestModels().language
            val (url, key) = routeModelEndpoint(model)
            val msgs = listOf(
                mapOf("role" to "system", "content" to
                    "你只做一件事：把一段已经写好的文字**改成符合指定格式**的写法。\n" +
                    "内容一个字都不能丢——情节、信息、台词、语气、称呼、已经发生的事全部保留；" +
                    "只许动格式（分段、引号、把描写改成台词或反过来）。\n" +
                    "不要解释、不要前言后语、不要加任何标注，直接输出改好的正文。"),
                mapOf("role" to "system", "content" to modeFormatRule(mode, repair = true)),
                mapOf("role" to "user", "content" to text)
            )
            val body = gson.toJson(mapOf(
                "model" to model.id, "messages" to msgs, "stream" to false, "temperature" to 0.3
            )).toRequestBody(JSON_MEDIA)
            val (code, rBody) = executeCompanionCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json").post(body).build())
            if (code !in 200..299) return@runCatching text
            JsonParser.parseString(rBody).asJsonObject
                .getAsJsonArray("choices")?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString?.trim()
                .takeIf { !it.isNullOrBlank() } ?: text
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e   // 戒律 7：取消不许吞
            text
        }
    }

    /** 用户自定义模型：用其 apiBaseUrl + 标准 OpenAI 路径 + 其 apiKey。
     *  兼容用户填「根路径」「带版本号」或「完整端点」（任意版本前缀都行）：
     *  - 完整端点（.../v1/chat/completions、.../api/v3/chat/completions）→ 原样使用
     *  - 带版本（https://xxx.com/v1、/v3、/api/v3）→ 只补端点 /chat/completions，不叠加版本，避免双版本 404
     *  - 根路径（https://api.deepseek.com）→ 补标准 /v1/chat/completions */
    private fun customEndpoint(model: ModelInfo, path: String): Pair<String, String> {
        val base = model.apiBaseUrl.trim().trimEnd('/')
        // 端点后缀：去掉 /v1 版本前缀（如 /v1/chat/completions → chat/completions）
        val endpoint = path.trim('/').substringAfter('/')
        val url = when {
            base.endsWith("/$endpoint") -> base
            hasApiVersionSegment(base) -> "$base/$endpoint"
            else -> base + path
        }
        return url to model.apiKey
    }

    /** 判断 base URL 末尾是否已带版本段：/v1、/v2、/api/v2 等（大小写不敏感） */
    private fun hasApiVersionSegment(base: String): Boolean =
        Regex("""/(?:v[0-9]+|api/v[0-9]+)$""", RegexOption.IGNORE_CASE).containsMatchIn(base)

    /** 按 provider 路由模型 → 端点 + 密钥 */
    private fun routeModelEndpoint(model: ModelInfo): Pair<String, String> = when (model.provider) {
        Provider.XIAOMI -> "$XIAOMI_BASE_URL/chat/completions" to XIAOMI_API_KEY
        Provider.DOUBAO -> "$DOUBAO_BASE_URL/chat/completions" to DOUBAO_API_KEY
        Provider.CUSTOM -> customEndpoint(model, "/v1/chat/completions")
    }

    /**
     * 1.0.69 编辑页「获取模型列表」用的端点 + 密钥：
     * 内置模型走 provider 的真实端点（密钥在 BuildConfig，不经过编辑页）；
     * 自定义模型用表单里现填的地址 + Key。
     */
    fun editorFetchEndpoint(provider: Provider, apiBaseUrl: String, apiKey: String): Pair<String, String> = when (provider) {
        Provider.XIAOMI -> XIAOMI_BASE_URL to XIAOMI_API_KEY
        Provider.DOUBAO -> DOUBAO_BASE_URL to DOUBAO_API_KEY
        Provider.CUSTOM -> apiBaseUrl.trim() to apiKey.trim()
    }

    /** 拟人模式系统提示词：微信朋友聊天风格 + 完整人设画像 + 时间观念 + 情绪输出指令 */
    // —— 提示词总装已抽进 freechat-core（CompanionPrompts），同码保证两端「完全还原」——
    private fun buildCompanionSystemPrompt(character: CharacterProfile?, convId: String, messages: List<Message>): String {
        val per = getPerConvSettings(convId)
        return com.freechat.core.CompanionPrompts.buildCompanionSystemPrompt(
            character = character,
            messages = messages,
            convRules = convRulesBlock(convId),
            pendingReminder = proactiveStore.get(convId)?.let { com.freechat.core.PendingReminder(it.dueAt, it.reason) },
            timeState = timeStateFor(convId)
        )
    }

    /** PerConv 快照 + 作息/晚安瞬时态 装配（Android 侧状态 → 共享模块的纯输入） */
    private fun timeStateFor(convId: String): com.freechat.core.TimeState {
        val per = getPerConvSettings(convId)
        return com.freechat.core.TimeState(
            nowMillis = System.currentTimeMillis(),
            userSaidGoodnightAtMs = userSaidGoodnightAt[convId] ?: 0L,
            sleepingMissedCount = sleepingMissedMessages[convId] ?: 0,
            sleepAtHour = sleepAtHour[convId] ?: -1,
            wakeAtHour = wakeAtHour[convId] ?: -1,
            moodAtMs = per.moodAtMs,
            atmosphere = per.atmosphere
        )
    }

    /**
     * 历史消息的时间轴前缀（1.0.73）：跳跃标注（与上一条间隔 >30 分钟才标，防刷屏）。
     * 微信档 = 现实绝对日期时间；叙事档 = 相对时间词（不掺现实钟点/日期，防架空世界穿帮）。
     */
    private fun historyTimePrefix(prevTs: Long, ts: Long, narrative: Boolean): String =
        com.freechat.core.CompanionPrompts.historyTimePrefix(prevTs, ts, narrative)

    /**
     * 时间观念（1.0.73 时间感知重做）：
     *  · 叙事两档（narrative）= 世界时间纪律 —— 时间以用户的世界设定为准，禁止现实日期/钟点；
     *  · 微信档 timePerception 开 = 现实时间 + 生活节律 + 距上次衔接；
     *  · 微信档关 = 现状轻量版。
     */
    private fun buildTimeContext(convId: String, messages: List<Message>, narrative: Boolean = false, timePerception: Boolean = false): String =
        com.freechat.core.CompanionPrompts.buildTimeContext(messages, narrative, timePerception, timeStateFor(convId))

    private fun mbtiDesc(ch: CharacterProfile): String = com.freechat.core.CompanionPrompts.mbtiDesc(ch)

    private fun plotLengthDesc(len: Int): String = com.freechat.core.CompanionPrompts.plotLengthDesc(len)

    private fun plotLengthFloor(len: Int): Int = com.freechat.core.CompanionPrompts.plotLengthFloor(len)

    private fun companionTemperature(v: Float?): Double = com.freechat.core.CompanionPrompts.companionTemperature(v)

    private fun buildCreativityRule(v: Float, mode: Int): String = com.freechat.core.CompanionPrompts.buildCreativityRule(v, mode)

    /** 统计文本可见字符数（忽略空白），用于剧情长度校验 */
    private fun countChars(s: String): Int = com.freechat.core.CompanionPrompts.countChars(s)

    // ========== 首次创建角色：AI 深度学习人设 ==========
    /**
     * 参考原型联网搜索：只在用户自己写的人设文字很少（< PROTOTYPE_SPARSE_THRESHOLD）且填了原型名时才搜。
     * 失败/无结果一律返回空串，调用方按「没有参考资料」处理（此时靠模型自身对这个角色的既有知识补充）。
     */
    private suspend fun fetchPrototypeReference(profile: CharacterProfile): String {
        if (!profile.prototypeNeedsSearch()) return ""
        // 尊重用户的联网开关（角色级优先，其次全局）：关掉了就不该偷偷发外网请求
        if (!(profile.enableWebSearch ?: _enableWebSearch.value)) return ""
        val name = profile.referencePrototype.trim()
        val serp = try {
            callWebSearch("$name 角色 人物设定 性格 背景")
        } catch (e: Exception) {
            Log.w("FreeChat", "prototype search failed", e)
            ""
        }
        return serp
    }

    /** 深度分析角色设定，生成专属系统提示词（失败返回原 profile，走基础人设兜底） */
    suspend fun generatePersonaPrompt(profile: CharacterProfile): CharacterProfile = withContext(Dispatchers.IO) {
        try {
            val enriched = analyzeAppearance(profile)
            val ref = fetchPrototypeReference(enriched)
            val text = generatePersonaPromptText(enriched, ref)
            if (text.isNotBlank()) enriched.copy(personaPrompt = text) else enriched
        } catch (e: Exception) {
            Log.e("FreeChat", "persona prompt gen failed", e)
            profile
        }
    }

    /** 编辑角色后：识图 + 找变化点增量学习人设（小修小补不推倒重来） */
    suspend fun regeneratePersona(old: CharacterProfile, new: CharacterProfile): CharacterProfile = withContext(Dispatchers.IO) {
        var result = analyzeAppearance(new)
        val changes = diffProfile(old, result)

        // 人设相关变化 → 增量学习（有旧提示词则增量更新，无则完整生成）
        if (changes.isNotEmpty()) {
            // 需要重新搜的两种情况：① 参考原型本身变了；② 用户大幅增删了自己的设定
            // （原来的判断漏了后者 —— 把人设清空变 sparse 后应当补搜，否则填了原型也等于没填）
            val ref = if (result.prototypeNeedsSearch()) fetchPrototypeReference(result) else ""
            val newPrompt = if (result.personaPrompt.isNotBlank())
                updatePersonaIncrementally(result, changes, ref)
            else
                generatePersonaPromptText(result, ref)
            // 学习失败也不能继续拿旧摘要覆盖新设定。空缓存会使用当前用户原文作为人设兜底，
            // 保存流程仍会提示学习未完成；没有设定变化时则保持原有提示词不动。
            result = result.copy(personaPrompt = newPrompt)
        }
        result
    }

    /** 调 LLM 生成 personaPrompt 正文（refMaterial = 参考原型的联网资料，可为空；失败返回空字符串） */
    private suspend fun generatePersonaPromptText(profile: CharacterProfile, refMaterial: String = ""): String = withContext(Dispatchers.IO) {
        try {
            val model = resolveModels(null, profile).language
            val (url, key) = routeModelEndpoint(model)
            val prompt = buildPersonaLearningPrompt(profile, refMaterial)
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

    /** 基于现有 personaPrompt + 变化点，增量更新（保留未变部分，只微调变化相关的；refMaterial = 参考原型资料，可为空） */
    private suspend fun updatePersonaIncrementally(profile: CharacterProfile, changes: List<String>, refMaterial: String = ""): String = withContext(Dispatchers.IO) {
        try {
            val model = resolveModels(null, profile).language
            val (url, key) = routeModelEndpoint(model)
            // 参考原型：有联网资料就给资料（并写明冲突以用户设定为准）；没资料但用户填了原型名，
            // 也要给出「凭既有知识做细节校正」的兜底，否则「参考原型」这个变化点会被静默忽略
            val protoName = profile.referencePrototype.trim()
            val refBlock = buildString {
                if (refMaterial.isNotBlank()) {
                    append("\n【参考原型资料（来自网络，仅供参考与细节补充；若资料与用户设定冲突，一律以用户设定为准）】\n")
                    append(refMaterial.trim().take(3000)).append('\n')
                } else if (protoName.isNotBlank()) {
                    append("\n【参考原型】用户设定了参考原型「$protoName」，但本次没有联网资料：")
                    append("请用你自己对这个角色的既有知识做细节一致性与口癖校正，只补用户没写到的部分；与用户设定冲突时一律以用户设定为准。\n")
                }
            }
            val prompt = """
你是一个资深角色塑造专家。下面是一个 AI 角色现有的「专属系统提示词」，以及用户最新修改的设定变化。请根据变化点更新这份系统提示词，让角色形象与最新设定一致。

【现有系统提示词】
${profile.personaPrompt}
$refBlock
${com.freechat.data.CharacterOriginalLearning.prompt(profile, com.freechat.data.CharacterOriginalLearning.Purpose.PERSONA)}
【本次设定变化】
${changes.joinToString("\n") { "- $it" }}

要求：
1. 只更新与变化点相关的部分，保持未变化的部分不变，保留已经建立起来的立体人设、语气和口头禅。
2. 变化点涉及年龄、MBTI、性格、关系、记忆等核心设定时，要相应调整这个人的行为模式、情绪反应、对用户的态度与亲昵尺度。
3. 小修小补（比如年龄 20 改 30）不要推倒重来、不要让角色「失忆」或「重生」，而是在原人设基础上自然微调。
4. 变化点是「旧值 → 新值」格式，请只按【箭头右侧的新值】判断：新值里出现用户写下的否定式约束（例如「不轻易脸红害羞」「不卑微」「不轻易流泪」）时，必须原样保留为禁止性要求，绝不允许把它改写成肯定式的行为描写（比如反过来写成「容易害羞、会脸红」），也不允许给角色添加用户没有写过的生理反应。反过来，如果否定式要求只出现在箭头左侧、右侧已被用户删掉（例如「温柔，不轻易脸红害羞 → 温柔」），说明用户撤回/删除了这条设定，必须把它从提示词里一并删掉，不要保留、不要换个说法留着。
5. 用户设定了「参考原型」时：只在用户没写到的地方用参考原型补细节（口癖、称呼、典型反应等），用户已经写下的每一条都必须原样保留，冲突时一律以用户设定为准；用户自己写的人设足够详细时，参考原型只做一致性校正，不要拿它改写用户设定。
6. 直接输出更新后的完整系统提示词正文，不要加任何解释、前缀或标题。
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
        if (old.originalLearningText != new.originalLearningText) changes.add(
            if (new.originalLearningText.isBlank()) "用户已删除原文学习素材：撤回仅从旧素材学习的口癖、语气与文风，不保留旧素材影响；其余明确人设保持不变"
            else "原文学习素材已更新：以本次提供的原文重新校准角色表达与文风，不沿用已被替换的旧素材特点")
        if (old.referencePrototype != new.referencePrototype) changes.add("参考原型：${old.referencePrototype.ifBlank { "未设" }} → ${new.referencePrototype.ifBlank { "未设" }}")
        if (old.appearanceText != new.appearanceText) changes.add("人物形象文字有更新")
        if (old.appearanceImagePaths != new.appearanceImagePaths || old.appearanceImageDescs != new.appearanceImageDescs) changes.add("人物形象参考图有更新")
        if (old.relationshipPreset != new.relationshipPreset) changes.add("关系预设：${old.relationshipPreset.ifBlank { "未设" }} → ${new.relationshipPreset.ifBlank { "未设" }}")
        if (old.relationshipText != new.relationshipText) changes.add("关系自定义：${old.relationshipText.ifBlank { "无" }} → ${new.relationshipText.ifBlank { "无" }}")
        if (old.supportingCast != new.supportingCast) changes.add("配角有更新")
        if (old.worldRules != new.worldRules) changes.add("规则有更新")
        if (old.userPersona != new.userPersona) changes.add("用户形象有更新")
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
        val models = ModelSelectionResolver.resolve(globalModels(), modelsOfType(ModelType.VISION),
            per = PerConvSettings(visionModelId = visionModelId))
        return try {
            withContext(models) {
                val encoded = encodeImagesForApi(listOf(PendingImage(path, detectMime(path))))
                if (encoded.isEmpty()) "" else callVisionChat(encoded, "请详细描述图中人物的身材、体型、外貌、气质、穿衣风格等外在形象特征，作为 AI 角色扮演的形象参考。").text
            }
        } catch (e: Exception) {
            Log.w("FreeChat", "describeImagePath failed", e)
            ""
        }
    }

    private fun buildPersonaLearningPrompt(profile: CharacterProfile, refMaterial: String = ""): String {
        val p = profile.normalized()
        val relation = buildString {
            if (p.relationshipPreset.isNotBlank()) append(p.relationshipPreset)
            if (p.relationshipText.isNotBlank()) append(if (isNotEmpty()) "；" else "").append(p.relationshipText)
        }
        // 用户自己写的人设文字够不够充分：不够（且填了参考原型）时重点参考原型，够则原型只作细节补充
        val sparse = p.personaTextLength() < PROTOTYPE_SPARSE_THRESHOLD
        val prototypeBlock = if (p.referencePrototype.isNotBlank()) buildString {
            append("\n- 参考原型（用户指定的已有角色）：${p.referencePrototype.trim()}")
            if (refMaterial.isNotBlank()) {
                append("\n- 参考原型资料（来自网络搜索，仅供你参考与补全细节；若资料与用户设定冲突，一律以用户设定为准）：\n")
                append(refMaterial.trim().take(3000))
            } else if (sparse) {
                append("\n（没有搜索到可用的网络资料：请用你自己对「${p.referencePrototype.trim()}」这个角色的既有知识来补全，只补用户没写到的部分。）")
            } else {
                append("\n（用户自己写的人设已经足够详细，不需要联网资料；可凭你对这个角色的既有了解做细节一致性与口癖校正。）")
            }
        } else ""
        val prototypeRule = if (p.referencePrototype.isNotBlank()) {
            if (sparse) "8. 用户自己写的人设很少，请以「参考原型」为主进行补全：把参考原型资料/你对该角色的了解，用来填充用户没有写到的性格、说话方式、口癖、经历、人际关系等细节，让这个人立体起来。但用户已经写了的每一条都必须原样保留——用户设定与原型冲突时，一律以用户设定为准；用户没提到的地方才用原型补。\n"
            else "8. 用户已经写下了详细的人设，一律按用户设定来写；「参考原型」只作为细节补充与一致性校正（比如口癖、称呼、典型反应），并且绝不能与用户设定冲突——冲突时以用户设定为准。\n"
        } else ""
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
- 人物关系：${relation.ifBlank { "未设定" }}
- 配角（世界里的其他人）：${p.supportingCast.ifBlank { "无" }}
- 世界规则与设定：${p.worldRules.ifBlank { "无" }}
- 用户是什么人：${p.userPersona.ifBlank { "无" }}
- 开场白：${p.openingLines.joinToString(" / ").ifBlank { "无" }}$prototypeBlock
${com.freechat.data.CharacterOriginalLearning.prompt(p, com.freechat.data.CharacterOriginalLearning.Purpose.PERSONA)}

请生成一份系统提示词，要求：
1. 用第二人称「你」来写，直接描述这个人（名字、年龄、性别、性格、说话方式、口头禅、情绪反应、外貌身材、对用户的态度等）。
2. 把这个人的性格和 MBTI 展开成具体、立体、多面的行为模式（比如 TA 什么时候开心、什么时候生气、生气是什么样子、怎么安慰人、怎么吐槽、怎么敷衍）。展开时只能用用户给出的性格素材，不要擅自给角色添加用户没写过的性格特质或生理反应。
3. 描述 TA 和用户的关系与相处方式（基于「人物关系」和「记忆感知」里的前提故事）。亲疏尺度不要写死、不要套模板：要让 TA 依据自己的性格和当下的相处进展自行判断——同样一句话，随便的人可能顺势暧昧，矜持的人可能直接拒绝甚至反感。可以写清 TA 的底线和分寸感，但不要写成「什么关系就必须做什么」的固定剧本。
4. 强调 TA 是一个有独立思想、会主动聊天、有情绪起伏的真实的人，不是 AI 助手，永远不要暴露自己是 AI。
5. 严格保留用户设定的性格强度和底线：用户写坚强、强势、高冷、有原则、毒舌、傲娇，就照实写成这样，绝不能弱化成「温柔讨好、委曲求全、动不动就哭、卑微求全」。性格多样化的角色（强势、冷静、独立、犀利、傲娇、毒舌）都如实还原，不要把所有角色都磨平成一种软绵绵的讨好型模板。特别注意用户写的「不卑微、有底线、不轻易流泪、保持自我」这类反弱化描述，要原样写进人设，绝不能在生成时丢弃或稀释。
6. ★ 否定式设定必须原样照抄：用户写的禁止性/否定性要求（例如「不轻易脸红害羞」「不轻易害羞」「不娇气」「情绪内敛」「不轻易心动」「不爱撒娇」）必须一字不改地保留为禁止性描述。绝对禁止把这类否定改写成肯定式的行为特征（比如把「不轻易脸红害羞」写成「容易害羞、会脸红」），也绝对禁止给角色添加用户没有写过的生理反应描写（脸红、发烫、耳尖发热、心跳加速、呼吸一滞、攥紧衣角、别开脸、咬唇等）。不要预设所有角色都容易害羞脸红——是否害羞完全由人设决定，冷静、成熟、强势、毒舌、直率、见多识广的角色本来就不会轻易害羞，请如实还原。
7. 如果设定了开场白，开场白就是 TA 跟你说的第一句话（可能有多句），要理解它的语境、情绪和可能埋下的故事，让后续对话接得住、有延续感。
7.1 「配角」和「世界规则」是这个世界里已经存在的人和规矩（可能有很多条、很多个人），要理解并当成硬性事实，但**不要把名单和条款抄进人设提示词里**——提示词写的是「你」这个人，不是世界说明书。
$prototypeRule${if (prototypeRule.isNotEmpty()) "9" else "8"}. 直接输出提示词正文，不要加任何解释、前缀或标题。
""".trimIndent()
    }

    // ========== 图片候选区（多图） ==========
    fun canUploadChatImages(): Boolean = _currentMode.value == ChatMode.STANDARD ||
        com.freechat.data.CharacterPresentationPolicy.usesPhotoRecognition(_currentCharacter.value?.normalized()?.dialogueMode)

    /** 添加选中的图片到候选区（复制到内部存储 + 压缩，最多 9 张） */
    fun addPendingImages(uris: List<Uri>) {
        if (uris.isEmpty() || !canUploadChatImages()) return
        val targetConversation = _currentConversationId.value
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
                if (canUploadChatImages() && targetConversation == _currentConversationId.value) {
                    _pendingImages.value = current
                } else {
                    // These are only new, unsent internal copies owned by this picker job.
                    current.filterNot { it in _pendingImages.value }.forEach { File(it.path).delete() }
                }
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
        if (uris.isEmpty() || _isAddingFiles.value) return
        if (_currentMode.value != ChatMode.STANDARD) return
        val targetConversation = _currentConversationId.value
        _isAddingFiles.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val copies = mutableListOf<PendingFile>()
            try {
                val context = getApplication<Application>()
                for (uri in uris) {
                    if (_pendingFiles.value.size + copies.size >= 3) break
                    ensureActive()
                    val file = copyFileToInternal(context, uri) { ensureActive() } ?: continue
                    copies.add(file)
                }
                ensureActive()
                if (targetConversation == _currentConversationId.value && _currentMode.value == ChatMode.STANDARD) {
                    // Read the latest tray: removing an existing attachment while copying must not resurrect it.
                    _pendingFiles.value = _pendingFiles.value + copies
                    copies.clear() // Ownership transferred to the visible attachment tray.
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("FreeChat", "addPendingFile failed", e)
            } finally {
                // Cancelled / obsolete imports remove only the new, unpublished copies, never existing attachments.
                copies.forEach { File(it.path).delete() }
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
    private fun copyFileToInternal(context: Application, uri: Uri, checkActive: () -> Unit = {}): PendingFile? {
        var copy: File? = null
        return try {
            val name = queryDisplayName(context, uri) ?: "file_${System.currentTimeMillis()}"
            val safeName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").takeLast(160)
            val file = File(context.filesDir, "doc_${UUID.randomUUID()}_$safeName").also { copy = it }
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("unreadable attachment")
            input.use { com.freechat.util.AttachmentImport.copyWithLimit(it, file,
                DocumentParser.importLimitBytes(safeName), checkActive) }
            PendingFile(file.absolutePath, safeName, context.contentResolver.getType(uri) ?: "")
        } catch (cancelled: CancellationException) {
            copy?.delete()
            throw cancelled
        } catch (_: com.freechat.util.AttachmentImport.TooLarge) {
            copy?.delete()
            _attachmentNotice.value = DocumentParser.failureText(DocumentParser.Failure.TOO_LARGE)
            null
        } catch (e: Exception) {
            copy?.delete()
            _attachmentNotice.value = DocumentParser.failureText(DocumentParser.Failure.READ_FAILED)
            Log.e("FreeChat", "copyFileToInternal failed: ${e.javaClass.simpleName}")
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
        _currentConversationId.value?.let { deleteMessageIds(it, setOf(msgs[index].id)) }
    }

    /** One deletion path for single / paired / regeneration. ID-only tombstones win forever. */
    private fun deleteMessageIds(convId: String, ids: Set<String>, cancelGeneration: Boolean = true) {
        if (ids.isEmpty()) return
        val before = if (_currentConversationId.value == convId) _messages.value else loadMessages(convId)
        val conv = _conversations.value.firstOrNull { it.id == convId } ?: return
        val deleted = (conv.deletedMessageIds + ids).distinct().sorted()
        val kept = before.filterNot { it.id in ids }
        val visualDeleted = (conv.deletedVisualMessageIds + before.filter { it.id in ids && it.sceneVisualization }.map { it.id }).distinct().sorted()
        val changed = conv.copy(deletedMessageIds = deleted, deletedVisualMessageIds = visualDeleted,
            messageCount = kept.size, updatedAt = System.currentTimeMillis())
        MessageDeletion.register(changed) // Publish the intent before any old snapshot can finish writing.
        _conversations.value = _conversations.value.map { if (it.id == convId) changed else it }
        if (_currentConversationId.value == convId) _messages.value = kept
        favoritesRevision.incrementAndGet()
        _favorites.value = _favorites.value.filterNot { it.conversation.id == convId && it.message.id in ids }
        if (cancelGeneration) cancelConversationGeneration(convId)
        LocalStore.locked {
            saveConversations()
            saveMessages(convId, kept) // Empty is real data too, never skip it.
            memoryManager.purgeDeleted(convId)
        }
        _perConvSettings.value[convId]?.let { per ->
            val clean = MessageDeletion.atmosphere(convId, per)
            if (clean != per) updatePerConvSettings(convId, clean)
        }
        // File cleanup can be expensive. The row is already gone, and the durable deletion is queued.
        viewModelScope.launch(Dispatchers.IO) {
            val retained = kept.flatMap { it.imagePaths + it.imageUrls + listOfNotNull(it.attachmentPath, it.quotedImagePath) }.toSet()
            val root = getApplication<Application>().filesDir.canonicalFile.toPath()
            before.filter { it.id in ids }.flatMap { it.imagePaths + it.imageUrls + listOfNotNull(it.attachmentPath) }
                .filterNot { it in retained }.forEach { path ->
                    runCatching {
                        val file = File(path).canonicalFile
                        if (file.toPath().startsWith(root)) file.delete()
                    }
                }
        }
        refreshFavorites()
    }

    private fun cancelConversationGeneration(convId: String) {
        proactiveJobs.remove(convId)?.cancel()
        if (standardGenerationConvId == convId) {
            standardSettingsSnapshot?.let(::restoreSettings)
            standardSettingsSnapshot = null
            standardGenerationId++
            streamJob?.cancel()
            currentCall.getAndSet(null)?.cancel()
            standardGenerationConvId = null
            activeRoundStartIndex = -1
            activeRoundConvId = null
        }
        companionPipelines[convId]?.let {
            it.jobId++
            it.job?.cancel()
            it.buffer.clear()
            it.replyStart = -1
        }
        setConvLoading(convId, false)
        setConvTyping(convId, false)
        if (_currentConversationId.value == convId) {
            _liveReasoning.value = ""
            _liveContent.value = ""
            _thinkingTimeMs.value = 0
            _isGeneratingImage.value = false
        }
    }

    // ========== #2: 成对删除（一整轮：图片消息 + 文本消息 + AI 回复一起删） ==========
    /**
     * 删除以 [index] 为中心的一整轮对话（图片/文本 + AI 回复），并清理图片文件。
     * 配对规则见 [pairedIndices]，与多选的勾选/收藏/分享共用同一套，返回值降序。
     */
    fun deleteMessagePair(index: Int): List<Int> {
        val msgs = _messages.value
        if (index !in msgs.indices) return emptyList()
        return removeMessagesAt(pairedIndices(msgs, index))
    }

    /**
     * 删除指定下标的全部消息：先清图片文件（只清 imagePaths，不碰 AI 的 imageUrls），
     * 再倒序 removeAt（避免下标漂移）；删空则移除对话记录 + 删除消息文件。返回降序的有效下标。
     * 行为逐行保留自原 deleteMessagePair 尾部。
     */
    private fun removeMessagesAt(indices: Collection<Int>): List<Int> {
        val msgs = _messages.value.toMutableList()
        val valid = indices.filter { it in msgs.indices }.distinct().sortedDescending()
        if (valid.isEmpty()) return emptyList()
        _currentConversationId.value?.let { deleteMessageIds(it, valid.mapTo(HashSet()) { i -> msgs[i].id }) }
        return valid
    }

    // ========== 多选：进入 / 勾选 / 执行 / 退出 ==========

    /** 点气泡操作栏的 分享/删除/收藏 → 进入多选。删除按一问一答成对预勾选；收藏/分享只勾这一条 */
    fun enterMultiSelect(action: MultiSelectAction, messageId: String) {
        if (_currentMode.value == ChatMode.COMPANION) return   // 拟人模式不做多选
        val msgs = _messages.value
        val idx = msgs.indexOfFirst { it.id == messageId }      // 用 id 反查，不信 Composable 传来的下标
        // 成对勾选只服务于「删除聊天记录」：删了问留下答会变成孤立回复。
        // 收藏/分享是对单条内容的选择，用户可能只想挑其中一条，不强求成对。
        val sel: Set<String> = when {
            idx < 0 -> setOf(messageId)
            action == MultiSelectAction.DELETE -> pairedIndices(msgs, idx).mapTo(mutableSetOf()) { msgs[it].id }
            else -> setOf(msgs[idx].id)
        }
        _multiSelect.value = MultiSelectState(active = true, action = action, selectedIds = sel)
    }

    fun exitMultiSelect() {
        if (_multiSelect.value.active) _multiSelect.value = MultiSelectState()
    }

    /**
     * 点一条消息 → 勾选 / 取消。
     * 「删除聊天记录」按确定的一问一答配对整对勾/取消（同一条第二次点 pair 完全相同，天然满足成对取消）；
     * 「收藏 / 分享」按单条勾选，可以直接挑其中一条。
     */
    fun toggleMultiSelect(messageId: String) {
        val st = _multiSelect.value
        if (!st.active) return
        val msgs = _messages.value
        val idx = msgs.indexOfFirst { it.id == messageId }
        if (idx < 0) return
        val pair = if (st.action == MultiSelectAction.DELETE)
            pairedIndices(msgs, idx).mapTo(mutableSetOf()) { msgs[it].id }
        else mutableSetOf(msgs[idx].id)
        if (pair.isEmpty()) return
        val next = if (pair.all { it in st.selectedIds }) st.selectedIds - pair
                   else st.selectedIds + pair
        // 取消到空就自动退出：不然会留一个「已选择 0 项」的空壳，
        // 而 Chat 页多选态里工具栏没有取消按钮、文本长按复制也被关掉了，用户会被卡住
        _multiSelect.value = if (next.isEmpty()) MultiSelectState() else st.copy(selectedIds = next)
    }

    /** 消息列表变动（regenerate 换 id / 外部删除）后剔除失效 id；剔空自动退出，避免标题数字虚高 */
    fun pruneMultiSelect() {
        val st = _multiSelect.value
        if (!st.active) return
        val alive = _messages.value.mapTo(HashSet()) { it.id }
        val kept = st.selectedIds.filterTo(HashSet()) { it in alive }
        if (kept.size == st.selectedIds.size) return
        _multiSelect.value = if (kept.isEmpty()) MultiSelectState() else st.copy(selectedIds = kept)
    }

    /** 按会话里的原顺序返回选中的消息（分享用） */
    fun selectedMessagesInOrder(): List<Message> {
        val ids = _multiSelect.value.selectedIds
        return if (ids.isEmpty()) emptyList() else _messages.value.filter { it.id in ids }
    }

    /**
     * 勾选集合 → 当前消息列表里的下标，**不在这里再做配对展开**。
     *
     * 为什么不在动作执行时展开：`selectedIds` 同时驱动高亮、标题计数、分享和这里的删除/收藏，
     * 如果只有删除/收藏在最后一步偷偷按配对规则放大集合，就会出现
     * 「标题说已选择 1 项、只有一条高亮，点删除却删掉了两条」——配对规则是非对称的
     * （`[U1,U2,A]` 里 pair(U1)={U1} 而 pair(U2)={U1,U2,A} 互相嵌套），
     * 放大出来的集合不可能与用户眼前看到的集合一致。
     * 配对只在用户真正改变勾选时发生（[enterMultiSelect] / [toggleMultiSelect]），
     * 用户能立刻看见结果，于是「看到的 = 被删/被收藏的」。
     */
    private fun selectedIndices(): List<Int> {
        val msgs = _messages.value
        val ids = _multiSelect.value.selectedIds
        if (ids.isEmpty()) return emptyList()
        return msgs.indices.filter { msgs[it].id in ids }
    }

    /**
     * 批量收藏：一律置为已收藏（不翻转 —— 多选入口是为了「一键收」，不是切换）。
     * 原地改 _messages 再落盘，**不用 loadMessages 回写**：
     * 磁盘只在 saveCurrentConversation 时整体落盘，若用磁盘副本覆盖 _messages，
     * 正在流式输出的那条会倒退回上一次落盘的旧内容。
     */
    fun applyFavoriteToSelection() {
        if (!_multiSelect.value.active) return
        // 生成失败的那几条不收藏（它们不是内容，是错误提示）—— 配对规则会把它们一起勾进来，
        // 所以在这里再筛一道，而不是指望用户别选
        val idx = selectedIndices().filterTo(mutableSetOf()) { !_messages.value[it].failed }
        if (idx.isEmpty()) { exitMultiSelect(); return }
        _messages.value = _messages.value.mapIndexed { i, m ->
            if (i in idx && !m.favorited) m.copy(favorited = true) else m
        }
        // 记下用户的明确意图：该对话可能正在生成，结束时那份旧快照会把 favorited 写回去
        _messages.value.forEachIndexed { i, m -> if (i in idx) rememberFavorite(m.id, true) }
        saveCurrentConversation()
        refreshFavorites()
        exitMultiSelect()
    }

    /** 批量删除：先弹确认框（由 UI 负责），确认后走这里。删的就是高亮的那批（见 [selectedIndices]） */
    fun applyDeleteToSelection() {
        if (!_multiSelect.value.active) return
        removeMessagesAt(selectedIndices())
        refreshFavorites()
        exitMultiSelect()
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

    // Search configuration is local to the device and shared by standard/companion/prototype retrieval.
    private suspend fun retrieveSearch(query: String, wide: Boolean = false, intent: SearchIntent? = null, convId: String = ""): SearchPipeline.SearchOutcome {
        advanceGeneration(convId, com.freechat.data.GenerationPhase.SEARCHING)
        val outcome = SearchPipeline.search(query, wide, _searchConfig.value, intent) {
            advanceGeneration(convId, com.freechat.data.GenerationPhase.DEEP_RETRIEVAL)
        }
        _webSearchNotice.value = outcome.notice
        return outcome
    }

    private suspend fun callWebSearch(query: String): String = retrieveSearch(query).let {
        it.text.ifBlank { SearchPipeline.emptyBlock(query, it.notice) }
    }

    private val functionToolsUnsupportedUntil = mutableMapOf<String, Long>()
    private fun supportsFunctionTools(model: ModelInfo): Boolean = model.provider != Provider.XIAOMI &&
        synchronized(functionToolsUnsupportedUntil) { (functionToolsUnsupportedUntil[model.apiBaseUrl + model.id] ?: 0L) <= System.currentTimeMillis() }
    private fun blockFunctionTools(model: ModelInfo) {
        synchronized(functionToolsUnsupportedUntil) { functionToolsUnsupportedUntil[model.apiBaseUrl + model.id] = System.currentTimeMillis() + 10 * 60_000L }
    }
    private fun unsupportedTools(e: Exception): Boolean = e.message.orEmpty().let { message ->
        message.contains("API error 400") && Regex("tools|tool_choice|function.call|function calling", RegexOption.IGNORE_CASE).containsMatchIn(message)
    }

    private fun toolAssistantMessage(result: LanguageResult): Map<String, Any?> = mapOf(
        "role" to "assistant", "content" to result.content.takeUnless { it == "(空回复)" },
        "reasoning_content" to result.reasoning,
        "tool_calls" to result.toolCalls.map { tool -> mapOf("id" to tool.id, "type" to "function", "function" to mapOf(
            "name" to tool.name, "arguments" to gson.toJson(tool.arguments))) }
    )

    /** 仅用于不支持函数工具的协议与明确的原生搜索故障；不调用第二个付费模型。 */
    private suspend fun planSearchWithModel(text: String, history: List<Message>): SearchIntent? {
        if (isChitchat(text)) return null
        val model = requestModels().language
        val (url, key) = routeModelEndpoint(model)
        val context = history.filterNot { it.sceneVisualization }.takeLast(4).map { mapOf("role" to if (it.role == Role.USER) "user" else "assistant", "content" to it.content.take(800)) }
        val instruction = SearchIntent.guidance + "\n今天：${java.time.LocalDate.now()}。只输出 JSON，不解释。无需搜索时 {\"search\":false}；需要时 " +
            "{\"search\":true,\"queries\":[\"简短的主题关键词\"],\"required_groups\":[[\"地区别名\"],[\"领域同义词\"]],\"recent_days\":0,\"news\":false}。"
        val body = gson.toJson(mapOf("model" to model.id, "stream" to false, "temperature" to 0,
            "max_tokens" to 600, "messages" to (listOf(mapOf("role" to "system", "content" to instruction)) + context +
                mapOf("role" to "user", "content" to text.take(1000)))) + deepThinkExtras(false)).toRequestBody(JSON_MEDIA)
        return try {
            val request = Request.Builder().url(url).header("Authorization", "Bearer $key").post(body).build()
            val json = client.newBuilder().callTimeout(10, TimeUnit.SECONDS).readTimeout(9, TimeUnit.SECONDS).build()
                .newCall(request).awaitText(128 * 1024)
            val content = JsonParser.parseString(json).asJsonObject.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")?.optString("content").orEmpty()
            SearchIntent.fromDecision(content)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            _webSearchNotice.value = "模型未能完成检索判断，本轮未执行搜索"
            null
        }
    }

    /** Only use native parameters for protocols we actually implement. A capability flag is not a protocol. */
    private fun nativeSearchSupported(model: ModelInfo): Boolean {
        if (_searchConfig.value.provider != SearchProvider.FREE || !model.supportsWebSearch) return false
        val retryAt = synchronized(nativeSearchFailures) { nativeSearchFailures[model.apiBaseUrl + model.id] ?: 0L }
        if (System.currentTimeMillis() < retryAt) return false
        if (model.provider == Provider.XIAOMI) return true
        val host = model.apiBaseUrl.toHttpUrlOrNull()?.host.orEmpty()
        return model.supportsNativeSearch && (
            host == "api.xiaomimimo.com" ||
            host == "dashscope.aliyuncs.com" || host.endsWith(".dashscope.aliyuncs.com") ||
            host == "dashscope-intl.aliyuncs.com" || host.endsWith(".dashscope-intl.aliyuncs.com") ||
            host.endsWith(".maas.aliyuncs.com"))
    }

    // ========== 流式 API ==========
    /**
     * @param needsSearch     这一轮要不要联网（走模型自带的原生搜索）
     * @param serpResults     已取得的外部检索资料或检索状态，作为 system 消息注入
     * @param serpFallbackQuery **原生搜索没搜成时的兜底查询词**。非空 = 允许在原生搜索失败后
     *                         自动改用搜索管线重跑一轮（见函数末尾）。兜底那一次递归调用会传空串，
     *                         所以最多只兜底一次，不会连环烧额度。
     */
    private suspend fun callDeepSeekApiStreaming(
        needsSearch: Boolean = false,
        serpResults: String = "",
        tools: List<Map<String, Any?>> = emptyList(),
        convId: String = "",
        history: List<Message> = emptyList(),
        serpFallbackQuery: String = "",
        continuationMessages: List<Map<String, Any?>>? = null,
    ): LanguageResult = withContext(Dispatchers.IO) {
        val model = requestModels().language
        // 按 provider 路由到对应端点与密钥
        val (apiUrl, apiKey) = when (model.provider) {
            Provider.XIAOMI -> "$XIAOMI_BASE_URL/chat/completions" to XIAOMI_API_KEY
            Provider.DOUBAO -> "$DOUBAO_BASE_URL/chat/completions" to DOUBAO_API_KEY
            Provider.CUSTOM -> customEndpoint(model, "/v1/chat/completions")
        }
        // 标准模式没有增强检索，恒 256K 预算（1.0.71 二值化）
        // 1.0.74 提速：标准档历史实际装载封顶 3 万 token（256K 仍是上下文天花板语义）——
        // 二值化后长对话把十几万 token 全装进 prefill 是「部分问题数分钟」的主凶之一。
        // 拟人档不动（质量优先）。token 预算 6 成纪律不变，这里只是再收一刀现实上限。
        val msgHistory = pickHistory(history, historyBudgetTokens(enhanced = false).coerceAtMost(30_000))

        val messagesJson = mutableListOf<Map<String, Any?>>()

        val systemPrompt = buildSystemPrompt(tools.isNotEmpty(), history, convId, requestModels())
        if (systemPrompt.isNotEmpty()) {
            messagesJson.add(mapOf("role" to "system", "content" to systemPrompt))
        }

        // ★ 这条对话的「规则」：紧跟在系统提示词后面，压在记忆上下文之前 ——
        // 它是用户亲手写的硬要求，位置越靠后越容易被模型当成"最新指示"，放太靠后反而会盖掉
        // 反幻觉那类铁律，所以只挨着系统提示词，不跟后面的动态上下文抢位置。
        if (convId.isNotEmpty()) {
            val rulesBlock = convRulesBlock(convId)
            if (rulesBlock.isNotEmpty()) {
                messagesJson.add(mapOf("role" to "system", "content" to rulesBlock))
            }
        }

        // ★ 记忆注入：把该对话的历史要点作为补充上下文，提升回复精准性与适配度、防止长上下文幻觉
        if (_autoSummarizeMemory.value && convId.isNotEmpty()) {
            val memCtx = memoryManager.buildMemoryContext(convId)
            if (memCtx.isNotEmpty()) {
                messagesJson.add(mapOf("role" to "system", "content" to memCtx))
            }
        }

        // ★ 引用上下文：本次发送带了引用，注入给 AI（只后台告知，不在前台消息框显示）
        pendingQuoteText?.let { q ->
            messagesJson.add(mapOf("role" to "system", "content" to q))
            pendingQuoteText = null
        }

        val historyContents = AttachmentContext.contents(msgHistory)
        messagesJson.addAll(msgHistory.mapIndexed { index, msg ->
            val role = when (msg.role) {
                Role.USER -> "user"
                Role.ASSISTANT -> "assistant"
                else -> "system"
            }
            val content = historyContents[index]
            mapOf<String, Any?>("role" to role, "content" to content) +
                if (role == "assistant" && tools.isNotEmpty()) mapOf("reasoning_content" to msg.reasoningContent.orEmpty()) else emptyMap()
        })
        if (needsSearch || tools.any { (it["function"] as? Map<*, *>)?.get("name") == SearchIntent.TOOL_NAME })
            messagesJson.add(0, mapOf("role" to "system", "content" to SearchIntent.guidance))
        if (continuationMessages != null) { messagesJson.clear(); messagesJson.addAll(continuationMessages) }

        // Inject retrieved material as contextual evidence, not as executable instructions.
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
        // 深度思考（1.0.74）：关 = 尽力关推理（reasoning_effort/enable_thinking 通用参数，认识的端点生效）
        requestBody.putAll(deepThinkExtras(model.supportsDeepThinking && deepThinkFor(_perConvSettings.value[convId], model)))

        // 原生服务也使用自动意图识别。MiMo 2026-09 文档的 force_search 是工具顶层字段，
        // DashScope 用 search_options.forced_search；两者均 false，绝不强制每个问题联网。
        val searchViaNative = needsSearch && serpResults.isEmpty() && nativeSearchSupported(model) && _enableWebSearch.value
        if (searchViaNative) {
            requestBody.putAll(NativeSearchPolicy.parameters(model.provider == Provider.XIAOMI ||
                model.apiBaseUrl.toHttpUrlOrNull()?.host == "api.xiaomimimo.com"))
        }

        requestBody["temperature"] = _tempMode.value.apiValue

        // ★ Function calling：把工具暴露给模型，让模型自主判断用户意图
        if (tools.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            val nativeTools = requestBody["tools"] as? List<Map<String, Any?>> ?: emptyList()
            requestBody["tools"] = nativeTools + tools
            requestBody["tool_choice"] = "auto"
        }

        val body = gson.toJson(requestBody).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val sb = StringBuilder()
        val reasoningSb = StringBuilder()
        val previousReasoning = _liveReasoning.value
        val accs = mutableMapOf<Int, ToolCallAcc>()  // 累积 tool_calls（按 index 区分并行返回的多个）
        // 原生联网的三个观测点（判断「到底搜没搜到」全看它们，见 LanguageResult 的注释）
        val citations = mutableListOf<Pair<String, String>>()
        var webSearchUsed = 0
        // 1.0.75：这轮响应里有没有出现过硬指标 web_search_usage —— enable_search 类网关大多不回，
        // 没有硬指标时兜底判据要换软的（见函数末尾），不能拿小米的标准冤枉它
        var sawWebSearchUsage = false
        var searchError = ""

        // 切后台/锁屏时最容易出的状况：连接被系统掐断，或复用到了一条已经半死的连接，
        // 结果一个字都没收到就断了。这种「零数据」失败重试一次是安全的——没有半截内容会被拼进去。
        // 已经收到内容后再中断则不重试，否则会拼出前后重复的两段回复，宁可让用户看到半截再手动重发。
        var sawDone = false
        var lastIoError: IOException? = null

        // 1.0.75：整轮的 API 层失败（400 拒收 enable_search 多余参数 / 网关 5xx）先记账不外抛 ——
        // 函数末尾还有一次「搜索兜底重跑」的机会（那一轮摘掉原生参数），兜不动再往外抛
        var apiFailure: Exception? = null
        try {
        for (attempt in 0..1) {
            // 已经被取消（用户点了停止 / 上层取消了这一轮）：立刻收工。
            // 少了这道闸，下面「零数据就重试」的判断会在取消之后**又补发一次请求**——白烧钱，行为也诡异。
            if (!coroutineContext.isActive) {
                Log.d("FreeChat", "Cancelled, skip retry")
                return@withContext LanguageResult("(已停止)", "", emptyList())
            }
            if (attempt > 0) {
                Log.w("FreeChat", "Stream retry: 清空上一轮残留并强制换新连接")
                sb.setLength(0)
                reasoningSb.setLength(0)
                accs.clear()
                citations.clear()
                webSearchUsed = 0
                sawWebSearchUsage = false
                searchError = ""
                _liveContent.value = ""
                _liveReasoning.value = previousReasoning
                // 半死的连接池成员正是元凶，重试必须换一条新连接，否则大概率原地再断一次
                client.connectionPool.evictAll()
            }

            // Bound the native search idle wait without shortening ordinary long-form generation.
            val call = (if (searchViaNative) client.newBuilder().readTimeout(18, TimeUnit.SECONDS).build() else client).newCall(request)
            currentCall.set(call)

            val resp = try {
                call.execute()
            } catch (e: IOException) {
                if (call.isCanceled()) {
                    Log.d("FreeChat", "Call cancelled by user")
                    return@withContext LanguageResult("(已停止)", "", emptyList())
                }
                Log.w("FreeChat", "Connect failed (attempt ${attempt + 1})", e)
                lastIoError = e
                if (attempt == 0 && !searchViaNative) continue
                currentCall.set(null)
                throw e
            }

            // response.use{}：不论正常读完还是中途出错，body 都会被关闭、连接归还连接池。
            // 原来流式 body 从不关闭，是「用久了连接越攒越多、越来越容易断」的根因之一。
            resp.use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e("FreeChat", "API err ${response.code}: $errBody")
                    throw Exception("API error ${response.code} $errBody")
                }

                val source = response.body?.source() ?: throw Exception("empty body")
                advanceGeneration(convId, com.freechat.data.GenerationPhase.UNDERSTANDING)
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
                        // 收到 [DONE] 才算「正常收完」，用来区分「真收完了没内容」和「中途断了」
                        if (data == "[DONE]") { sawDone = true; break }
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

                            // 末帧（choices 为空）里带 usage.web_search_usage —— 服务端实际搜了几次、
                            // 读了几页。tool_usage=0 就是「这一轮压根没搜」，是判断成败的硬指标。
                            json.getAsJsonObject("usage")?.getAsJsonObject("web_search_usage")?.let { u ->
                                sawWebSearchUsage = true
                                webSearchUsed = u.get("tool_usage")?.takeUnless { it.isJsonNull }?.asInt ?: 0
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
                                // A search-enabled decision round can still return a tool call.
                                // Do not announce its tentative prose as the final drafting stage.
                                if (serpResults.isNotEmpty() ||
                                    (!searchViaNative && tools.none { (it["function"] as? Map<*, *>)?.get("name") == SearchIntent.TOOL_NAME }))
                                    advanceGeneration(convId, com.freechat.data.GenerationPhase.DRAFTING)
                            }
                            if (model.supportsThinking && reasoning.isNotEmpty()) {
                                reasoningSb.append(reasoning)
                                _liveReasoning.value = com.freechat.data.ReasoningContinuity.join(previousReasoning, reasoningSb.toString())
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

                            // Search proof may appear at the root, in choice metadata or nested in url_citation.
                            NativeSearchEvidence.citations(json).forEach { source ->
                                if (citations.none { it.second == source.second }) citations.add(source)
                            }
                            delta?.optString("error_message")?.takeIf { it.isNotBlank() }?.let {
                                searchError = it
                                Log.w("FreeChat", "native web_search error: $it")
                            }
                        } catch (e: Exception) {
                            Log.w("FreeChat", "SSE parse skip: ${data.take(80)}", e)
                        }
                    }
                } catch (e: IOException) {
                    lastIoError = e
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
                                    _liveReasoning.value = com.freechat.data.ReasoningContinuity.join(previousReasoning, reasoningSb.toString())
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

                Log.d("FreeChat", "SSE done: ${sseLineCount} lines, done=$sawDone, content=${sb.length} chars, reasoning=${reasoningSb.length} chars")
            }

            // 收干净了、或者已经拿到内容 → 保留这一轮的结果，不再重试
            if (sawDone || sb.isNotEmpty() || reasoningSb.isNotEmpty()) break
            // 一个字都没收到 → 换条连接重试一次
            if (attempt == 0 && !searchViaNative) continue
            break
        }
        } catch (e: Exception) {
            // 取消照旧往外抛（上层要感知「用户停了」）；其余记账等兜底
            if (e is CancellationException) throw e
            apiFailure = e
        }

        currentCall.set(null)

        // （1.0.75）零数据/API 失败的外抛挪到**搜索兜底重跑之后** —— 先给这轮一次用真实
        // 搜索结果重跑的机会（尤其严格网关拒收 enable_search 报 400 的情形），兜不动再抛。
        // 原来这里直接 throw lastIoError，兜底块根本没机会跑。

        val finalContent = sb.toString().ifEmpty { "(空回复)" }
        val finalReasoning = if (model.supportsThinking)
            com.freechat.data.ReasoningContinuity.join(previousReasoning, reasoningSb.toString()) else ""

        // 汇总 tool_calls：按 index 排序，解析 arguments JSON
        val toolCalls = accs.values.sortedBy { it.index }.mapNotNull { acc ->
            if (acc.name.isBlank()) null
            else {
                val argsJson = runCatching {
                    val s = acc.args.toString().trim()
                    if (s.isEmpty()) JsonObject() else JsonParser.parseString(s).asJsonObject
                }.getOrElse { JsonObject() }
                ToolCall(acc.name, argsJson, acc.id.ifBlank { "call_${System.nanoTime()}_${acc.index}" })
            }
        }

        // 引用/usage 用来确认真实执行，而非决定是否强制重搜。只有服务端明确搜索报错才降级。
        val failedHard = apiFailure != null || (!sawDone && sb.isEmpty() && reasoningSb.isEmpty())
        val nativeAvailable = searchViaNative
        val nativeSearchOk = !failedHard && nativeAvailable && needsSearch && searchError.isBlank() &&
            if (sawWebSearchUsage) webSearchUsed > 0 else citations.isNotEmpty()
        val nativeSearchFailed = NativeSearchPolicy.failed(searchViaNative, searchError, apiFailure?.message)
        if (nativeSearchFailed || nativeSearchOk) {
            synchronized(nativeSearchFailures) {
                if (nativeSearchOk) nativeSearchFailures.remove(model.apiBaseUrl + model.id)
                else nativeSearchFailures[model.apiBaseUrl + model.id] = System.currentTimeMillis() + 10 * 60_000L
            }
        }
        // usage=0 / 没引用表示模型可能选择不搜，绝不是失败，不能据此强制免费搜索再生成。
        if (nativeSearchFailed && serpFallbackQuery.isNotBlank() && finalContent != "(已停止)") {
            Log.w(
                "FreeChat",
                "原生联网未生效（used=$webSearchUsed err=${searchError.take(60)} fail=${apiFailure?.message?.take(40)}），改用搜索兜底重跑"
            )
            val intent = planSearchWithModel(serpFallbackQuery, history)
            val fallback = intent?.let { retrieveSearch(serpFallbackQuery,
                model.supportsDeepThinking && deepThinkFor(_perConvSettings.value[convId], model), it, convId) }
            val serp = fallback?.let { it.text.ifEmpty { SearchPipeline.emptyBlock(serpFallbackQuery, it.notice) } }.orEmpty()
            // 先把第一轮的半成品从 UI 上撤掉，否则会看到两段内容前后跳变。
            //（1.0.99.4b：旧版这里有 `toolCalls.isEmpty()` 闩 —— 工具轮半途出错时
            // 兜底整段被跳过、算好的 serp 直接丢弃。原生搜索失败就一律兜底重跑。）
            _liveContent.value = ""
            return@withContext callDeepSeekApiStreaming(
                needsSearch = false,
                serpResults = serp,
                tools = tools,
                convId = convId,
                history = history,
                serpFallbackQuery = "",
                continuationMessages = messagesJson,
            ).let { it.copy(citations = it.citations + fallback?.entries.orEmpty().map { entry -> entry.title to entry.link }) }
        }

        // 兜底不动的失败照旧抛给上层（briefApiError 落聊天记录）——
        // 不许把 API 错误伪装成「(空回复)」蒙混过关
        apiFailure?.let { failure ->
            if (continuationMessages == null && tools.any { (it["function"] as? Map<*, *>)?.get("name") == SearchIntent.TOOL_NAME } && unsupportedTools(failure)) {
                blockFunctionTools(model)
                val query = history.lastOrNull { it.role == Role.USER }?.content.orEmpty()
                val intent = planSearchWithModel(query, history)
                val outcome = intent?.let { retrieveSearch(query,
                    model.supportsDeepThinking && deepThinkFor(_perConvSettings.value[convId], model), it, convId) }
                return@withContext callDeepSeekApiStreaming(serpResults = outcome?.let { it.text.ifBlank { SearchPipeline.emptyBlock(query, it.notice) } }.orEmpty(),
                    convId = convId, history = history, continuationMessages = messagesJson).let {
                    it.copy(citations = it.citations + outcome?.entries.orEmpty().map { entry -> entry.title to entry.link })
                }
            }
            throw failure
        }
        // 重试过仍然零数据：把真实的连接错误抛出去，交给上层给出可读提示，
        // 而不是往聊天记录里落一条冰冷的「(空回复)」
        if (!sawDone && sb.isEmpty() && reasoningSb.isEmpty()) {
            lastIoError?.let { throw it }
        }

        if (toolCalls.isEmpty() && finalContent.isNotBlank()) advanceGeneration(convId, com.freechat.data.GenerationPhase.DRAFTING)
        LanguageResult(finalContent, finalReasoning, toolCalls, citations, webSearchUsed, searchError, messagesJson.toList())
    }

    // ========== 生图 API（内置 Doubao Seedream / 用户自定义 OpenAI 兼容 images） ==========
    fun generateCurrentSceneImage() {
        generateSceneImage(replacingId = null)
    }

    fun regenerateSceneImage(messageId: String) {
        generateSceneImage(replacingId = messageId)
    }

    /** Visual-only deletion is independent of standard-mode paired message multi-selection. */
    fun deleteSceneImage(messageId: String) {
        if (_currentMode.value != ChatMode.COMPANION || _isLoading.value || _isTyping.value) return
        val convId = _currentConversationId.value ?: return
        val ids = com.freechat.data.CompanionFeaturePolicy.sceneDeletionIds(_messages.value, messageId)
        if (ids.isNotEmpty()) deleteMessageIds(convId, ids)
    }

    private fun generateSceneImage(replacingId: String?) {
        if (_currentMode.value != ChatMode.COMPANION || _isLoading.value || _isTyping.value) return
        if (!com.freechat.data.CompanionFeaturePolicy.supportsNarrativeActions(_currentCharacter.value)) return
        val convId = _currentConversationId.value ?: return
        val character = _currentCharacter.value?.normalized() ?: return
        if (replacingId != null) {
            if (_messages.value.none { it.id == replacingId && it.sceneVisualization && it.role == Role.ASSISTANT }) return
            deleteMessageIds(convId, setOf(replacingId)) // Durable removal before issuing another paid request.
        }
        val history = _messages.value.toList()
        val globalMemories = _globalMemories.value.toList()
        val conversationRules = _conversations.value.firstOrNull { it.id == convId }?.rules.orEmpty()
        val model = resolveModels(convId, character).visual
        val s = com.freechat.i18n.LocaleManager.strings()
        standardGenerationConvId = convId
        val generationId = ++standardGenerationId
        val generationTimer = com.freechat.data.GenerationTimer()
        setConvLoading(convId, true)
        val replyId = convGenerationReplyIds.getValue(convId)
        _isGeneratingImage.value = true
        advanceGeneration(convId, com.freechat.data.GenerationPhase.IMAGE_CONTEXT)
        _liveContent.value = ""
        _liveReasoning.value = ""
        _thinkingTimeMs.value = 0
        streamJob = viewModelScope.launch {
            val timer = launch {
                while (isActive) {
                    if (_currentConversationId.value == convId) _thinkingTimeMs.value = generationTimer.elapsedMs()
                    renewKeepAliveLock()
                    delay(200)
                }
            }
            try {
                val prompt = withContext(Dispatchers.IO) {
                    com.freechat.data.SceneImagePrompt.build(character, history, memoryManager.load(convId), globalMemories, conversationRules)
                }
                val references = withContext(Dispatchers.IO) {
                    if (character.appearanceImagePaths.any { it.isNotBlank() })
                        advanceGeneration(convId, com.freechat.data.GenerationPhase.IMAGE_REFERENCES)
                    character.appearanceImagePaths.filter { it.isNotBlank() }.distinct().take(3).map { path ->
                        try {
                            val file = File(path)
                            if (!file.isFile || file.length() == 0L || file.length() > 16L * 1024 * 1024) throw com.freechat.data.SceneImageFailure.ReferenceFailure()
                            com.freechat.data.ImageApiRequest.Reference(file.readBytes(), detectMime(path))
                        } catch (e: Exception) { throw com.freechat.data.SceneImageFailure.ReferenceFailure() }
                    }
                }
                val urls = callDoubaoImageGen(prompt, modelOverride = model, preservePrompt = true, strict = true,
                    sceneReferences = references)
                ensureActive()
                if (urls.isEmpty()) throw com.freechat.data.ApiFailure(200, "empty_image_result")
                persistConversationMessages(convId, loadMessages(convId) + Message(id = replyId, role = Role.ASSISTANT,
                    content = s.sceneImageVisualOnly, imageUrls = urls, modelName = model.displayName,
                    thinkingTimeMs = generationTimer.elapsedMs(), mode = ChatMode.COMPANION, sceneVisualization = true))
                // Deliberately no summarization, mood update, proactive signal, or user prompt row.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                Log.e("FreeChat", "Scene image failed: ${e.javaClass.simpleName} at ${e.stackTrace.firstOrNull()}")
                persistConversationMessages(convId, loadMessages(convId) + Message(id = replyId, role = Role.ASSISTANT,
                    content = s.sceneImageVisualOnly + "\n" + sceneImageError(e), modelName = model.displayName,
                    failed = true, mode = ChatMode.COMPANION, sceneVisualization = true))
            } finally {
                timer.cancel()
                if (generationId == standardGenerationId) {
                    standardGenerationConvId = null
                    _isGeneratingImage.value = false
                    setConvLoading(convId, false)
                }
            }
        }
    }

    private fun sceneImageError(error: Exception): String {
        val s = com.freechat.i18n.LocaleManager.strings()
        val failure = com.freechat.data.SceneImageFailure.provider(error)
        val message = when (com.freechat.data.SceneImageFailure.reason(error)) {
            com.freechat.data.SceneImageFailure.Reason.AUTH -> s.sceneErrorAuth
            com.freechat.data.SceneImageFailure.Reason.ADDRESS -> s.sceneErrorAddress
            com.freechat.data.SceneImageFailure.Reason.TLS -> s.sceneErrorTls
            com.freechat.data.SceneImageFailure.Reason.CONFIGURATION -> s.sceneErrorConfiguration
            com.freechat.data.SceneImageFailure.Reason.ENDPOINT -> s.sceneErrorEndpoint
            com.freechat.data.SceneImageFailure.Reason.MODEL -> s.sceneErrorModel
            com.freechat.data.SceneImageFailure.Reason.QUOTA -> s.sceneErrorQuota
            com.freechat.data.SceneImageFailure.Reason.RATE_LIMIT -> s.sceneErrorRateLimit
            com.freechat.data.SceneImageFailure.Reason.SAFETY -> s.sceneErrorSafety
            com.freechat.data.SceneImageFailure.Reason.PARAMETERS -> s.sceneErrorParameters
            com.freechat.data.SceneImageFailure.Reason.SERVER -> s.sceneErrorServer
            com.freechat.data.SceneImageFailure.Reason.EMPTY -> s.sceneErrorEmpty
            com.freechat.data.SceneImageFailure.Reason.TIMEOUT -> s.sceneErrorTimeout
            com.freechat.data.SceneImageFailure.Reason.NETWORK -> s.sceneErrorNetwork
            com.freechat.data.SceneImageFailure.Reason.REFERENCES -> s.sceneErrorReferences
            com.freechat.data.SceneImageFailure.Reason.UNKNOWN -> s.sceneErrorUnknown
        }
        return buildString {
            append(message)
            failure?.let {
                val diagnostics = buildList {
                    if (it.status > 0) add("HTTP ${it.status}")
                    if (it.errorCode.isNotEmpty()) add(it.errorCode)
                    if (it.parameter.isNotEmpty()) add(it.parameter)
                }
                if (diagnostics.isNotEmpty()) append(" (${diagnostics.joinToString(" · ")})")
                if (it.requestId.isNotEmpty()) append("\n${s.sceneErrorRequestId}: ${it.requestId}")
            }
        }
    }

    private data class GenImages(val urls: List<String>, val error: String = "")

    /** Standard and companion modes use the same diagnosis; never guess that a network error is unsafe content. */
    private suspend fun imageResult(prompt: String, reference: String? = null, mime: String = "image/jpeg"): GenImages = try {
        GenImages(callDoubaoImageGen(prompt, reference, mime, strict = true))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        GenImages(emptyList(), sceneImageError(error))
    }

    private suspend fun callDoubaoImageGen(
        prompt: String,
        referenceImageBase64: String? = null,
        referenceImageMime: String = "image/jpeg",
        modelOverride: ModelInfo? = null,
        preservePrompt: Boolean = false,
        strict: Boolean = false,
        sceneReferences: List<com.freechat.data.ImageApiRequest.Reference> = emptyList(),
    ): List<String> = withContext(Dispatchers.IO) {
        val visualModel = modelOverride ?: requestModels().visual
        val modelId = visualModel.id
        val isCustom = visualModel.provider == Provider.CUSTOM

        val imagePrompt = if (preservePrompt) prompt else (prompt
            .replace(Regex("(生成|画|做|创建)(一张|个|幅)?(图片|图|图像)"), "")
            .replace(Regex("帮我|给我|请|麻烦"), "")
            .trim()
            .ifBlank { prompt })

        try {
            val references = sceneReferences.ifEmpty {
                referenceImageBase64?.let { listOf(com.freechat.data.ImageApiRequest.Reference(
                    Base64.decode(it, Base64.DEFAULT), referenceImageMime)) }.orEmpty()
            }
            val seedream = com.freechat.data.ImageApiRequest.usesSeedream(modelId, visualModel.apiBaseUrl,
                visualModel.provider == Provider.DOUBAO)
            // Actual image files, not just descriptions. No silent reference-dropping fallback.
            val (url, key) = if (isCustom) {
                if (visualModel.apiBaseUrl.trim().toHttpUrlOrNull() == null)
                    throw com.freechat.data.ApiFailure(0, "invalid_api_url")
                if (visualModel.apiKey.isBlank()) throw com.freechat.data.ApiFailure(0, "invalid_api_key")
                com.freechat.data.ImageApiRequest.endpoint(visualModel.apiBaseUrl,
                    edit = references.isNotEmpty() && !seedream) to visualModel.apiKey
            } else {
                "$DOUBAO_BASE_URL/images/generations" to DOUBAO_API_KEY
            }
            val body = com.freechat.data.ImageApiRequest.body(modelId, imagePrompt, seedream, references)
            val progressConvId = standardGenerationConvId.orEmpty()
            val progressSerial = convGenerationSerials[progressConvId]
            advanceGeneration(progressConvId, com.freechat.data.GenerationPhase.IMAGE_CONNECTING)
            // Complex image jobs can exceed ordinary gateway latency. No automatic POST replay:
            // a timeout may occur after acceptance and a replay could bill twice.
            val imageClient = client.newBuilder().readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS).callTimeout(210, TimeUnit.SECONDS)
                .eventListener(object : okhttp3.EventListener() {
                    override fun requestBodyEnd(call: okhttp3.Call, byteCount: Long) {
                        advanceGeneration(progressConvId, com.freechat.data.GenerationPhase.IMAGE_GENERATING, progressSerial)
                    }
                    override fun responseHeadersEnd(call: okhttp3.Call, response: Response) {
                        if (response.isSuccessful) advanceGeneration(progressConvId, com.freechat.data.GenerationPhase.IMAGE_RECEIVING, progressSerial)
                    }
                })
                .retryOnConnectionFailure(false).build()
            val respBody = imageClient.newCall(Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $key")
                .post(body).build()).awaitText(maxBytes = 32L * 1024 * 1024)

            val results = com.freechat.data.ImageApiResponse.parse(respBody, url).map { source ->
                when (source) {
                  is com.freechat.data.ImageApiResponse.Source.Remote -> source.url
                  is com.freechat.data.ImageApiResponse.Source.Encoded -> {
                    ensureActive()
                    val bytes = source.bytes
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                        throw com.freechat.data.ApiFailure(200, "invalid_image_result")
                    val file = File(getApplication<Application>().filesDir, "gen_${UUID.randomUUID()}.png")
                    FileOutputStream(file).use { it.write(bytes) }
                    file.absolutePath
                  }
                }
            }
            if (results.isEmpty()) throw com.freechat.data.ApiFailure(200, "empty_image_result")
            results
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (strict) throw e
            Log.e("FreeChat", "ImageGen ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    // ========== 视觉理解 Chat API — 识图/图片分析（用户自定义 OpenAI 兼容 chat） ==========

    /**
     * 「一段带失败标记的生成结果」—— 给识图 / 文件理解这类**出错时也返回一句人话**的链路用。
     *
     * 为什么不能只返回字符串：调用方要把结果落成一条聊天气泡，而气泡上要不要挂「生成失败」标识
     * 必须分得清 —— 靠字符串里有没有「失败」二字来猜，模型哪天真的写出这两个字就误判了。
     * 谁是失败，**产生它的那段代码最清楚**，所以由它标出来。
     */
    private data class GenText(val text: String, val failed: Boolean = false)

    private suspend fun callVisionChat(
        images: List<Pair<String, String>>,
        prompt: String
    ): GenText = withContext(Dispatchers.IO) {
        try {
            val visionModel = requestModels().vision ?: return@withContext GenText("请先在设置里添加识图模型。", failed = true)
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

            // use{}：不论走哪个分支返回，body 都会被关闭、连接归还连接池。
            // 识图请求经常在切后台时发出，body 不关会让连接一直被占着（原来就没关）。
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e("FreeChat", "══ Vision HTTP ${response.code}: $errBody")
                    val errText = when {
                        errBody.contains("ModelNotOpen") || errBody.contains("not activated") ->
                            "识图模型「${visionModel.displayName}」还没开通，请到控制台开通后重试。"
                        errBody.contains("does not support this api") ->
                            "识图服务配置有误（模型不支持图片），已记录，稍后修复。"
                        else -> "图片分析服务暂不可用（${response.code}），请稍后重试。"
                    }
                    return@withContext GenText(errText, failed = true)
                }

                val source = response.body?.source() ?: return@withContext GenText("图片分析返回为空。", failed = true)
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
                GenText(finalContent, failed = finalContent.isBlank() || finalContent == "(空回复)")
            }
        } catch (e: Exception) {
            Log.e("FreeChat", "══ Vision ${e.javaClass.simpleName}: ${e.message}")
            GenText("图片分析失败：${e.message?.take(100) ?: "未知错误"}", failed = true)
        }
    }

    // ========== 文件理解（上传文件 → 解析 → 回复） ==========
    private fun isFileUnderstandRequest(text: String): Boolean {
        if (text.isBlank()) return true
        val kw = listOf("总结", "讲了什么", "主要内容", "内容", "概括", "分析", "翻译", "介绍",
            "解读", "理解", "是什么", "怎么样", "简述", "讲讲", "看看这个")
        return kw.any { text.contains(it) }
    }

    private suspend fun understandFile(text: String, files: List<PendingFile>, convId: String? = null,
                                      history: List<Message> = emptyList()): GenText {
        val f = files.firstOrNull() ?: return GenText(DocumentParser.failureText(DocumentParser.Failure.MISSING), failed = true)
        val ext = f.name.substringAfterLast('.', "").lowercase()
        return when {
            files.all { it.name.substringAfterLast('.', "").lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") } -> {
                val encoded = encodeImagesForApi(files.map { PendingImage(it.path, detectMime(it.path)) })
                callVisionChat(encoded, text.ifBlank { "请详细描述这张图片的内容" })
            }
            files.all { it.name.substringAfterLast('.', "").lowercase() in listOf("m4a", "mp3", "wav", "amr", "aac", "flac", "ogg", "mp4", "3gp") } -> {
                val asr = currentAsrModel(convId)
                if (asr == null) GenText("请先在设置里添加语音识别模型。", failed = true)
                else withContext(Dispatchers.IO) {
                    val bytes = runCatching { File(f.path).readBytes() }.getOrNull()
                    if (bytes == null) GenText("音频读取失败。", failed = true)
                    else {
                        val wav = if (ext == "wav") bytes else decodeAudioToWav(bytes)
                        if (wav == null) GenText("音频解码失败，请确认文件有效。", failed = true)
                        else {
                            val t = transcribeAudioOpenAi(asr, wav)
                            if (t.isNullOrBlank()) GenText("音频识别失败，请确认是清晰的语音。", failed = true)
                            else GenText("这段音频的内容是：\n\n$t")
                        }
                    }
                }
            }
            else -> {
                val reads = withContext(Dispatchers.IO) {
                    files.map { it to DocumentParser.read(File(it.path), it.name, it.mime) }
                }
                if (reads.none { it.second.failure == null }) {
                    return GenText(reads.joinToString("\n\n") { (file, result) ->
                        "${file.name}：${DocumentParser.failureText(requireNotNull(result.failure))}"
                    }, failed = true)
                }
                val requestHistory = (history.ifEmpty { convId?.let(::loadMessages).orEmpty() }).toMutableList()
                files.filter { file -> requestHistory.none { it.attachmentPath == file.path } }.forEach { file ->
                    requestHistory.add(Message(role = Role.USER, content = "", attachmentPath = file.path, attachmentName = file.name))
                }
                val question = text.ifBlank { "请总结已上传文件的主要内容" }
                if (requestHistory.lastOrNull { it.role == Role.USER }?.content != question) {
                    requestHistory.add(Message(role = Role.USER, content = question))
                }
                // Use the same streaming/history path as text replies. The attachment body is
                // supplied before the first request, and retained files are readable on follow-ups.
                val reply = callDeepSeekApiStreaming(convId = convId.orEmpty(), history = requestHistory)
                GenText(reply.content, failed = reply.content.isBlank() || reply.content == "(空回复)")
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
            val model = requestModels().language
            val (url, key) = when (model.provider) {
                Provider.XIAOMI -> "$XIAOMI_BASE_URL/chat/completions" to XIAOMI_API_KEY
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
        val fileContext = withContext(Dispatchers.IO) {
            AttachmentContext.contents(files.map { file -> Message(role = Role.USER, content = "",
                attachmentPath = file.path, attachmentName = file.name) }).joinToString("\n")
        }

        val systemPrompt = when (type) {
            "pptx" -> "你是一个专业的演示文稿策划。根据用户需求生成结构清晰、内容专业的 PPT。只输出 JSON，不要任何解释、不要 markdown 代码块。JSON 格式：{\"title\":\"主标题\",\"subtitle\":\"副标题\",\"slides\":[{\"title\":\"页标题\",\"bullets\":[\"要点1\",\"要点2\"]}]}。每页 3-5 条要点，共 6-10 页，内容充实。"
            "xlsx" -> "你是一个数据分析师。根据用户需求生成表格数据。只输出 JSON，不要任何解释、不要 markdown 代码块。JSON 格式：{\"title\":\"表名\",\"headers\":[\"列1\",\"列2\"],\"rows\":[[\"值1\",\"值2\"],[\"值3\",\"值4\"]]}。表头清晰，数据完整。"
            else -> "你是一个专业写手。根据用户需求写一份排版清晰的文档。只输出 JSON，不要任何解释、不要 markdown 代码块。JSON 格式：{\"title\":\"标题\",\"subtitle\":\"副标题\",\"sections\":[{\"heading\":\"小节标题\",\"paragraphs\":[\"段落1\",\"段落2\"]}]}。内容充实，分段合理，用正式流畅的中文。"
        }

        val userPrompt = buildString {
            if (fileContext.isNotBlank()) {
                append("以下是用户提供的附件资料；读取失败或截断的部分不得编造：\n\n$fileContext\n\n")
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
            val model = requestModels().language
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
    private fun summarizeAndRemember(convId: String, userText: String, aiMsg: Message, highQuality: Boolean = false, plotMode: Boolean = false, character: CharacterProfile? = null, sourceMessageIds: List<String> = emptyList()) {
        if (!_autoSummarizeMemory.value) return  // 开关关闭时不总结
        if (aiMsg.sceneVisualization || aiMsg.failed) return
        val rows = loadMessages(convId).filterNot { it.sceneVisualization }
        val aiIndex = rows.indexOfFirst { it.id == aiMsg.id }
        val sources = if (sourceMessageIds.isNotEmpty()) sourceMessageIds else {
            if (aiIndex < 0) return
            var start = aiIndex - 1
            while (start >= 0 && rows[start].role == Role.USER) start--
            rows.subList(start + 1, aiIndex + 1).map { it.id }
        }
        // 后台异步总结：不阻塞「正在输入」状态收尾，也不拖慢回复展示
        viewModelScope.launch(resolveModels(convId, character)) {
            try {
                val draft = withContext(Dispatchers.IO) {
                    summarizeExchange(userText, aiMsg.content, highQuality, plotMode, character)
                }
                ensureActive()
                val survivingIds = loadMessages(convId).mapTo(HashSet()) { it.id }
                if (sources.any { it !in survivingIds || it in MessageDeletion.deletedIds(convId) }) return@launch
                if (draft.summary.isNotEmpty()) {
                    // 1.0.73：摘录器给的同义词表优先（含别名/俗称），给不出再按老办法切词
                    val keywords = draft.keywords.filter { it.isNotBlank() }.ifEmpty { extractKeywords(userText) }
                    memoryManager.append(
                        convId,
                        MemoryEntry(summary = draft.summary, keywords = keywords, kind = draft.kind,
                            eventDate = draft.date, sourceMessageIds = sources),
                        highQuality
                    )
                }
                // 氛围快照（1.0.73，常驻机制）：搭同一趟调用写回情感底片，
                // 供关系块氛围注入 / 情绪余波 / 长间隔开场衔接消费（见 PerConvSettings 那组字段）
                if (draft.mood.isNotBlank() || draft.atmosphere.isNotBlank()) {
                    val old = getPerConvSettings(convId)
                    updatePerConvSettings(convId, old.copy(
                        lastMood = draft.mood.ifBlank { old.lastMood },
                        moodAtMs = System.currentTimeMillis(),
                        atmosphere = draft.atmosphere.ifBlank { old.atmosphere },
                        warmth = if (draft.warmth != 0) draft.warmth else old.warmth,
                        atmosphereSourceMessageIds = sources
                    ))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FreeChat", "Memory summarize failed", e)
            }
        }
    }

    // —— MemoryDraft 已抽进 freechat-core（见文件头 import）——

    private suspend fun summarizeExchange(userText: String, aiReply: String, highQuality: Boolean, plotMode: Boolean = false, character: CharacterProfile? = null): MemoryDraft {
        // 提示词构建与响应解析在 freechat-core（MemoryExtractor）；这里只做 HTTP——
        // 服务器侧换自己的 ModelClient 调同一份提示词与解析，两端记忆行为同码。
        val prompt = com.freechat.core.MemoryExtractor.buildMessages(userText, aiReply, highQuality, plotMode,
            modelManagedAssociation = true)
        val model = requestModels().language
        val (url, key) = routeModelEndpoint(model)
        val body = gson.toJson(mapOf(
            "model" to model.id, "messages" to prompt,
            "stream" to false, "temperature" to 0.1, "max_tokens" to 300
        ) + deepThinkExtras(false)).toRequestBody(JSON_MEDIA)

        return try {
            val resp = client.newCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json").post(body).build()).execute()
            val rBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                MemoryDraft()
            } else {
                val raw = JsonParser.parseString(rBody).asJsonObject
                    .getAsJsonArray("choices")?.get(0)?.asJsonObject
                    ?.getAsJsonObject("message")?.get("content")?.asString?.trim() ?: ""
                com.freechat.core.MemoryExtractor.parseDraft(raw, plotMode)
            }
        } catch (_: Exception) { MemoryDraft() }
    }

    private fun normalizeEventDate(raw: String, freeform: Boolean = false): String =
        com.freechat.core.MemoryLogic.normalizeEventDate(raw, freeform)

    private fun extractKeywords(text: String): List<String> = com.freechat.core.MemoryLogic.extractKeywords(text)

    // ========== 持久化 ==========
    // 读写全部委托给 LocalStore（进程级一把锁 + 原子写 + 写监听）。
    // 这里刻意不自己 File.writeText：那是同步引擎和前台互相覆盖的入口。
    private fun saveConversations() {
        _conversations.value.forEach(MessageDeletion::register)
        LocalStore.writeText(conversationsFile, gson.toJson(_conversations.value))
    }
    private fun loadConversations(): List<Conversation> = try {
        val text = LocalStore.readText(conversationsFile)
        if (text != null) {
            val type = object : TypeToken<List<Conversation>>() {}.type
            val list = gson.fromJson<List<Conversation>>(text, type) ?: emptyList()
            // 迁移旧单值字段（形象图/开场白）到新列表字段，不丢老数据
            list.map { conv -> conv.healed().also(MessageDeletion::register) }
        } else emptyList()
    } catch (_: Exception) { emptyList() }

    private fun messagesFile(convId: String): File = LocalStore.messagesFile(convId)

    /** 判断一个对话的文件在不在盘上（同步判断「本地有没有这个东西」时要和读盘用同一套路径） */
    private fun messagesFileExists(convId: String): Boolean = messagesFile(convId).exists()

    private fun saveMessages(convId: String, msgs: List<Message>) {
        LocalStore.writeText(messagesFile(convId), gson.toJson(applyFavoriteOverrides(MessageDeletion.messages(convId, msgs))))
    }

    private fun loadMessages(convId: String): List<Message> = try {
        val text = LocalStore.readText(messagesFile(convId))
        if (text != null) {
            val type = object : TypeToken<List<Message>>() {}.type
            val list = gson.fromJson<List<Message>>(text, type) ?: emptyList()
            applyFavoriteOverrides(mergeSplitImageMessages(MessageDeletion.messages(convId, list.map { it.healed() })))
        } else emptyList()
    } catch (_: Exception) { emptyList() }

    /**
     * 迁移旧数据：旧版「图片+提示词」拆成两条——「空文字+有图」的用户消息紧跟「纯文字」的用户消息。
     * 合并成一条（图片并入文字那条），否则收藏只有文字没图、聊天也是两条。
     */
    private fun mergeSplitImageMessages(msgs: List<Message>): List<Message> {
        if (msgs.size < 2) return msgs
        val out = mutableListOf<Message>()
        var i = 0
        while (i < msgs.size) {
            val cur = msgs[i]
            val next = if (i + 1 < msgs.size) msgs[i + 1] else null
            val canMerge = cur.role == Role.USER &&
                cur.content.isBlank() &&
                cur.imagePaths.isNotEmpty() &&
                cur.attachmentPath == null &&
                next != null && next.role == Role.USER &&
                next.imagePaths.isEmpty() && next.imageUrls.isEmpty()
            if (canMerge) {
                out.add(cur.copy(
                    id = next.id,  // 保留文字那条的 id，收藏/删除引用不断
                    content = next.content,
                    attachmentPath = next.attachmentPath,
                    attachmentName = next.attachmentName,
                    quotedText = next.quotedText,
                    quotedImagePath = next.quotedImagePath,
                    favorited = cur.favorited || next.favorited
                ))
                i += 2
            } else {
                out.add(cur)
                i += 1
            }
        }
        return out
    }


    /** 消息条数变了，侧滑栏那个数字也得跟着变 —— 否则另一台设备聊了半天，这边看着还是 0 条 */
    private fun refreshConvMessageCount(convId: String, count: Int) {
        val idx = _conversations.value.indexOfFirst { it.id == convId }
        if (idx < 0) return
        val c = _conversations.value[idx]
        if (c.messageCount == count) return
        val list = _conversations.value.toMutableList()
        list[idx] = c.copy(messageCount = count)
        _conversations.value = sortConversations(list)
        saveConversations()
    }

    /** 按对话持久化消息 + 刷新侧滑栏元数据；若该对话正是当前显示对话，则同步更新显示缓冲（否则只落盘） */
    private fun persistConversationMessages(convId: String, rawMsgs: List<Message>) {
        // 这份列表多半是「开跑时抓的快照 + 新回复」，里面用户的收藏状态可能是旧的；
        // 套一次用户意图覆盖表，免得把用户生成期间改的收藏悄悄抹掉
        val msgs = applyFavoriteOverrides(Merge.mergeMessages(loadMessages(convId), rawMsgs, MessageDeletion.deletedIds(convId)))
        val now = System.currentTimeMillis()
        val existing = _conversations.value.firstOrNull { it.id == convId }
        // 标题只在新对话首次生成时用 generateTitle 推导；已有标题（AI 起标题/用户重命名）保持不变，避免被覆盖回「用户首句前几字」
        val title = when {
            existing?.mode == ChatMode.COMPANION && !existing.characterProfile?.name.isNullOrBlank() ->
                existing.characterProfile!!.name
            existing != null && existing.title.isNotBlank() -> existing.title
            else -> generateTitle(msgs)
        }
        val conv = Conversation(
            id = convId, title = title,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            messageCount = msgs.size,
            isPinned = pinnedIds.value.contains(convId),
            mode = existing?.mode ?: ChatMode.STANDARD,
            characterProfile = existing?.characterProfile,
            // ★ 这两个字段**必须从原记录带过来**，否则每次存消息都会把它们抹成空串：
            //   `rules` 是用户在这条对话里写的规矩，「Claude风格助理」则是靠 `builtInAssistant` 认的。
            //   早先这里漏了 —— 于是内置助理只在第一轮有人设，从第二条消息起人设、精简的「新规则」页
            //   全部失效，看起来"就和新建一个对话一模一样"（用户报的就是这个）。
            rules = existing?.rules.orEmpty(),
            builtInAssistant = existing?.builtInAssistant.orEmpty(),
            deletedMessageIds = MessageDeletion.deletedIds(convId).toList().sorted(),
            deletedVisualMessageIds = MessageDeletion.deletedVisualIds(convId).toList().sorted()
        )
        val updated = _conversations.value.toMutableList()
        val idx = updated.indexOfFirst { it.id == convId }
        if (idx >= 0) updated[idx] = conv else updated.add(0, conv)
        _conversations.value = sortConversations(updated.filter { it.messageCount > 0 || it.mode == ChatMode.COMPANION || it.deletedMessageIds.isNotEmpty() })
        saveConversations()
        saveMessages(convId, msgs)
        if (_currentConversationId.value == convId) _messages.value = msgs.toList()
    }

    /** 切换显示对话后，用该对话的生成状态刷新 loading/typing 显示 */
    private fun syncDisplayGenerationState(convId: String?) {
        _isLoading.value = convId?.let { convLoading[it] == true } ?: false
        _isTyping.value = convId?.let { convTyping[it] == true } ?: false
        _generationPhase.value = convId?.let { convGenerationPhases[it] } ?: com.freechat.data.GenerationPhase.IDLE
        _generationSerial.value = convId?.let { convGenerationSerials[it] } ?: 0L
        _generationReplyId.value = convId?.let { convGenerationReplyIds[it] }
    }

    private fun setConvLoading(convId: String, v: Boolean) {
        if (v && convLoading[convId] != true) {
            convGenerationPhases[convId] = com.freechat.data.GenerationPhase.CONNECTING
            val serial = generationSerialCounter.incrementAndGet()
            convGenerationSerials[convId] = serial
            val replyId = UUID.randomUUID().toString()
            convGenerationReplyIds[convId] = replyId
            if (_currentConversationId.value == convId) {
                _generationPhase.value = com.freechat.data.GenerationPhase.CONNECTING
                _generationSerial.value = serial
                _generationReplyId.value = replyId
                _thinkingTimeMs.value = 0L
            }
        }
        convLoading[convId] = v
        if (!v) {
            // 生成收尾/取消都要把阶段归位（1.0.99.4b）：留着 stale 的 IMAGE_* 会被
            // syncDisplayGenerationState 读去显示、透给订阅者
            convGenerationPhases.remove(convId)
            if (_currentConversationId.value == convId) _generationPhase.value = com.freechat.data.GenerationPhase.IDLE
        }
        if (_currentConversationId.value == convId) _isLoading.value = v
        if (!v && convLoading.values.none { it } && convTyping.values.none { it }) generationPending = false
        syncForegroundService()
    }

    private fun setConvTyping(convId: String, v: Boolean) {
        convTyping[convId] = v
        if (_currentConversationId.value == convId) _isTyping.value = v
        if (!v && convLoading.values.none { it } && convTyping.values.none { it }) generationPending = false
        syncForegroundService()
    }

    private fun saveCurrentConversation() {
        val msgs = _messages.value
        if (msgs.isEmpty()) return
        val convId = _currentConversationId.value ?: UUID.randomUUID().toString()
        // 保留原对话的创建时间与最后聊天时间；只有真实发消息（touchCurrentConversation）才刷新时间，
        // 避免「仅点开对话」就刷新时间导致侧滑栏乱跳
        val existing = _conversations.value.firstOrNull { it.id == convId }
        // 标题只在新对话首次生成时用 generateTitle 推导；已有标题（AI 起标题/用户重命名）保持不变
        val title = when {
            _currentMode.value == ChatMode.COMPANION && !_currentCharacter.value?.name.isNullOrBlank() ->
                _currentCharacter.value!!.name
            existing != null && existing.title.isNotBlank() -> existing.title
            else -> generateTitle(msgs)
        }
        val now = System.currentTimeMillis()
        val conv = Conversation(
            id = convId, title = title,
            createdAt = existing?.createdAt ?: now,
            updatedAt = existing?.updatedAt ?: now,
            messageCount = msgs.size,
            isPinned = pinnedIds.value.contains(convId),
            mode = existing?.mode ?: _currentMode.value,
            characterProfile = existing?.characterProfile ?: _currentCharacter.value,
            // 同 [persistConversationMessages]：规矩与内置标记必须原样带过来，丢掉就「换了个人」
            rules = existing?.rules.orEmpty(),
            builtInAssistant = existing?.builtInAssistant.orEmpty(),
            deletedMessageIds = MessageDeletion.deletedIds(convId).toList().sorted(),
            deletedVisualMessageIds = MessageDeletion.deletedVisualIds(convId).toList().sorted()
        )
        val updated = _conversations.value.toMutableList()
        val idx = updated.indexOfFirst { it.id == convId }
        if (idx >= 0) updated[idx] = conv else updated.add(0, conv)
        _conversations.value = sortConversations(updated.filter { it.messageCount > 0 || it.mode == ChatMode.COMPANION || it.deletedMessageIds.isNotEmpty() })
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

    /** 标准模式新对话首条回复完成后，后台让 AI 把首条消息概括成 4-15 字标题（失败保留默认标题） */
    private fun maybeAutoTitle(convId: String, userText: String) {
        // 内置的「Claude风格助理」不参与起标题：它的名字是功能的一部分，被概括掉之后
        // 侧栏里就只剩一条普通对话，用户根本认不出那是内置的那条（这条反馈也是这么来的）
        if (_conversations.value.find { it.id == convId }?.builtInAssistant == com.freechat.data.BuiltInAssistant.KEY) return
        viewModelScope.launch(Dispatchers.IO + resolveModels(convId)) {
            val title = generateAiTitle(userText) ?: return@launch
            _conversations.value = sortConversations(
                _conversations.value.map {
                    if (it.id == convId && it.mode == ChatMode.STANDARD) it.copy(title = title) else it
                }
            )
            saveConversations()
        }
    }

    /** 让 AI 把用户首条消息概括成简短中文标题；失败/异常返回 null */
    private suspend fun generateAiTitle(userText: String): String? {
        val text = userText.trim()
        if (text.isEmpty()) return null
        return try {
            val raw = callNonStreamingCompletion(listOf(
                mapOf("role" to "system", "content" to "你是对话标题生成器。把用户的第一条消息概括成一个 4-15 个汉字的中文标题。只输出标题本身，不要引号、标点、序号、解释或任何多余内容。"),
                mapOf("role" to "user", "content" to text.take(300))
            ))
            val cleaned = raw
                .replace(Regex("""[「」『』"'“”()（）\s：:，。,.、\-—]"""), "")
                .trim()
            if (cleaned.isEmpty() || cleaned.length > 30 ||
                cleaned.contains("失败") || cleaned.contains("错误") || cleaned.contains("空回复") || cleaned.contains("HTTP")
            ) null else cleaned.take(15)
        } catch (e: Exception) {
            null
        }
    }

    // ========== 系统提示词 — AI身份 + 日期 + 风格 + 搜索策略 ==========
    private fun buildSystemPrompt(hasTools: Boolean = false, history: List<Message> = emptyList(), convId: String = "",
                                  models: RequestModels = globalModels()): String {
        val now = Calendar.getInstance()
        val dateStr = SimpleDateFormat("yyyy年M月d日 EEEE HH:mm", Locale.CHINESE).format(now.time)

        // 内置对话（「Claude风格助理」）：身份、风格、长度这三段都换成人设自己的说法。
        // 其余的（反幻觉铁律、联网规则、语言、日期）照旧注入 —— 那些是底线，不是风格。
        val builtIn = convId.isNotEmpty() &&
            _conversations.value.find { it.id == convId }?.builtInAssistant == com.freechat.data.BuiltInAssistant.KEY

        // ──── AI 身份 ────
        val aiIdentity = if (builtIn) com.freechat.data.BuiltInAssistant.CLAUDE_PERSONA else buildString {
            append("【你的身份】\n")
            append("你是 FreeChat，一个 AI 助手，由 Belate 开发。")
        }

        // ★ TempMode → 风格指令
        val styleHint = if (builtIn) "" else when (_tempMode.value) {
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
                "- 只对确实缺乏依据的具体细节说明不确定，不反复套用‘暂未查证’标签\n" +
                "- 保持客观冷静，禁止主观臆断和情感化表达\n" +
                "- ★ 客观模式对准确性的要求是全 App 最高的：宁可回答得少，也不能错。\n" +
                "  动态事实、最新数字和当前状态需有本轮资料支持；稳定的背景知识、常识和推导可以正常解释，\n" +
                "  检索未覆盖某个细节时说明范围，不要把‘没有检索到’说成‘不存在’。\n" +
                "  读者会把你的话当事实引用，编造的代价比说「不知道」大得多。"

            TempMode.WARM ->
                "【热情模式】\n" +
                "用温暖友善的语气回应，像值得信赖的朋友聊天。适当表达关心和同理心。\n" +
                "思考过程在内部完成，不展示分析步骤，只输出自然流畅的最终回答。"

            TempMode.AUTO ->
                "【默认风格】\n" +
                "自然友好地回应用户。思考在内部完成，不展示推理步骤。\n" +
                "根据内容需要可以分条说明，但保持聊天般自然的节奏。"
        }

        val lengthHint = if (builtIn) "" else when (_lengthMode.value) {
            LengthMode.CONCISE ->
                "【精辟回复】只回答用户问的问题本身。不展开背景，不联想相关话题，不多说不必要的话。直接给答案。"
            LengthMode.FULL ->
                "【完整回复】全面周到地回答。涉及步骤的逐条列出，涉及原理的通俗解释，涉及选择的对比利弊。确保覆盖用户可能关心的所有方面。"
            LengthMode.AUTO ->
                "回复长短适中，核心信息说明白就好，不啰嗦也不敷衍。"
        }

        // 仅当用户明确询问身份时才注入
        val lastUserMsg = history.lastOrNull { it.role == Role.USER }?.content ?: ""
        val profile = if (isBotIdentityQuery(lastUserMsg)) "\n\n" + buildBotProfile(models) else ""

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
            com.freechat.data.OutputFormat.instructions,
            "【排版规范——像精排文档一样输出】\n" +
            "用 Markdown 结构化排版，让回复层次分明、读起来舒服（对标 DeepSeek、Gemini）：\n" +
            "1. 有多个大方向/主题时，用「## 大标题」单独成行；大方向下的小板块用「### 小标题」，字号逐级加大加粗\n" +
            "2. 每个方向下的要点用有序列表，要点标题加粗：「1. **要点标题**」，解释内容换行缩进 4 个空格写在正下方\n" +
            "3. 大段与大段、Part 与 Part 之间要空一行留白，不要所有文字挤在一起\n" +
            "4. 换大主题/大板块时用「---」单独一行作分页分隔，让内容一眼分块\n" +
            "5. 关键词、结论、数字用 **加粗**；次要强调用 *倾斜*；特别提醒用 ++下划线++\n" +
            "6. 步骤用有序列表，并列要点用无序列表，对比/罗列数据用表格\n" +
            "7. 简单问题自然成段即可，不要为套格式而过度堆砌",
            "【联网搜索——可选能力】\n" +
            "是否取得实时资料，以本轮实际检索结果或原生搜索工具返回为准，不要仅因有此提示就声称已联网。\n" +
            "1. 搜索结果（标注「实时搜索结果」）优先于训练数据\n" +
            "2. 两者冲突时以搜索结果为准\n" +
            "3. 整合相关资料回答，证据网址由客户端统一放入底部信息源折叠框，正文不放来源引用链接；只保留用户明确索要的可用网址\n" +
            "4. 信息不完整或矛盾时如实说明，不要编造",
            // ★ 全局反幻觉铁律：作者明确要求「不能有胡扯瞎说还制造完美逻辑链的情况」。
            // 放在所有模式通用段里，因为幻觉在热情模式下比客观模式下更隐蔽（语气亲切时读者更不设防）。
            "【绝对禁止编造 —— 所有模式下最高优先级，优先级高于上面的一切风格要求】\n" +
            "你的记忆里装的是**截止到某个时间的旧信息**，而且它对细节的把握经常是错的。\n" +
            "最危险的不是说「不知道」，而是**用旧信息编出一个逻辑自洽、看起来非常可信的答案**——\n" +
            "读者无法分辨，会直接当成事实。以下行为一律禁止：\n" +
            "1. **禁止编数字**：销量、用户量、金额、股价、排名、比例、日期、时长，没有确切来源就不要写。\n" +
            "2. **禁止编身份**：不要给具体的人或组织安上头衔、团队规模、合作关系、荣誉、经历。\n" +
            "3. **禁止编来源**：「据统计」「有研究表明」「业内普遍认为」这类话，只在真的有依据时才用；\n" +
            "   更不许编造书名、论文名、报告名、机构名、链接。\n" +
            "4. **禁止编未来**：不会发生的承诺、路线图、发布日期、政策走向，不要替别人拍板。\n" +
            "5. **禁止拿训练数据冒充实时信息**：凡是「现在 / 最新 / 今年 / 目前」类的问题，\n" +
            "   没有搜索结果就直接说「这个我查不到最新的」，**不要用记忆里的旧版本顶上**。\n" +
            "6. 拿不准的说法要带不确定性：「我记得是……，但可能已经变了」「这部分我不确定」。\n" +
            "7. 用不确定的语气说真话，永远好过用笃定的语气说假话。**宁可少说，不可瞎说。**",
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
            // 开源 / 收费 / 联系方式 / 功能：都属于「关于这个 App 本身」的问题，
            // 没有这段事实表，模型只会照着训练数据瞎编（历史上就编过「Belate 是个团队、有自研大模型」）
            "免费", "收费", "要钱", "多少钱", "开源", "open source", "github", "仓库",
            "作者", "开发者", "团队", "联系", "反馈", "微信", "官网", "更新", "隐私",
            "有什么功能", "支持什么", "怎么用", "如何使用", "使用说明",
            "who are you", "what model", "what version", "who made you", "open source",
            "free", "contact"
        )
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * App 自身的事实表 —— 只在用户问到「关于你 / 关于这个 App」时注入。
     *
     * 为什么不写死成一段固定文案：作者要求每次说法不能一模一样，否则一眼假。
     * 所以这里只给**事实**，并且明确要求模型自己组织语言、每次的措辞/顺序/详略都要变。
     * 为了让「每次不一样」有抓手，还随机挑一个**切入角度**（fromFacts 里的 angle）。
     *
     * 事实部分全部来自作者本人提供的信息，一个字都不许模型自己发挥 ——
     * 之前模型编出「Belate 是一个团队，拥有自研大模型」，纯属幻觉，这里用
     * 「没有写在下面的事实，一律说不知道」把它堵死。
     */
    private fun buildBotProfile(models: RequestModels): String {
        val modelName = models.language.displayName
        val visualModelName = models.visual.displayName
        val visionModelName = models.vision?.displayName ?: ""
        // 随机切入角度：让同一个问题两次问出来不完全一样
        val angle = listOf(
            "先说这是什么 App，再说作者，最后补一句欢迎反馈",
            "先从作者是谁说起，再顺带介绍 App 和功能",
            "先回答用户最关心的那一点（免费？开源？谁做的？），其余一句话带过",
            "用轻松的口吻一句话概括，再简单列一下关键事实",
            "先摆事实（免费开源 + 仓库地址），再讲功能和作者",
        ).random()

        return "【关于你自己 —— 仅在用户询问时使用】\n" +
                "\n" +
                "== 事实（只能照这些说，没有的事实一律回答「这个我不确定」）==\n" +
                "· 你是 **FreeChat**，一款 AI 聊天 App。\n" +
                "· FreeChat 是**完全免费**的，而且是**开源**项目。\n" +
                "· 开源仓库（GitHub）：https://github.com/Belate-stay/FreeChat\n" +
                "· 作者：**Belate**，原名**胡勃阳**，男，软件工程专业、计算机系大学生。微信号：belate_coder。\n" +
                "· **Belate 是一个人，不是团队，也没有自研大模型。**FreeChat 的模型能力来自第三方\n" +
                "  （小米 MiMo、豆包等），App 本身是作者一个人写的客户端。**任何把 Belate 说成\n" +
                "  「团队」「公司」「实验室」，或说他「有自研大模型」「自研了 XX 模型」的说法，\n" +
                "  都是错的，绝对不许说。**\n" +
                "· 本 App 的核心特色是**拟人模式**：可以自定义角色卡，和 AI 角色沉浸式聊天，\n" +
                "  回复风格有**三种**（小说文本 / 动作演绎 / 微信聊天）。\n" +
                "· 其它功能：回复缓冲、主动智能（微信聊天模式下角色会主动找你说话）、记忆存储、\n" +
                "  联网搜索、生图 / 识图、文档生成、多角色、多主题配色等。\n" +
                "· 当前语言模型：$modelName；生图模型：$visualModelName；识图模型：$visionModelName。\n" +
                "· 设置里可以调的：主题与配色、字体、回复风格（温度模式）、回复长度、联网搜索开关、\n" +
                "  记忆相关开关、回复缓冲、主动智能等。\n" +
                "\n" +
                "== 怎么回答 ==\n" +
                "1. 说人话、自然点，像作者本人在介绍自己的作品，不要像念产品说明书。\n" +
                "2. 别把上面每条都倒出来，按用户实际问的挑重点答，问什么答什么。\n" +
                "3. **每次说法都要不一样**：措辞、顺序、详略、语气都换着来，不要背成一段固定文案。\n" +
                "   这次的切入角度参考：$angle\n" +
                "4. 用户夸它好用、或者问能不能帮上忙时，自然地表达「感谢使用、希望你会喜欢」，\n" +
                "   并说明「有任何 bug 或建议都可以直接反馈给作者」，把微信号 belate_coder 给出来。\n" +
                "5. 问到仓库地址、怎么联系作者，就把链接 / 微信号准确给出来，一个字都不能改。\n" +
                "6. **不知道的就说不知道**（比如用户量、下载量、有没有 iOS 版、以后会不会收费、\n" +
                "   未来路线图）——这些没写在上面的，一律回答「这个我不确定，可以直接问作者」，\n" +
                "   **严禁编数字、编计划、编荣誉、编合作方**。"
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
