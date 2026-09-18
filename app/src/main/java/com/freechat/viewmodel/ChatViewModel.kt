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
import com.freechat.sync.Adapter
import com.freechat.sync.Merge
import com.freechat.sync.PerConvBridge
import com.freechat.sync.Relay
import com.freechat.data.MemoryManager
import com.freechat.data.PerConvStore
import com.freechat.data.SerpApiPool
import com.freechat.data.SerpErrorKind
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
        // SerpAPI 的 key、多 key 轮换、结果缓存、失败原因全部收在 data/SerpApiPool.kt 里，
        // 这里不再持有 BASE_URL / API_KEY（原来只支持单 key，一个月 250 次用完就整条链路静默失效）

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

    // 主动智能的本地账本（计划 + 每日配额），懒加载：不用这个功能的人不会碰到一次文件读取
    private val proactiveStore by lazy { ProactiveStore(getApplication()) }
    // 主动消息强制发通知：进程被闹钟冷启动时 appInForeground 还停在默认值 true（没人调过
    // onAppForegroundChanged），据此判断「用户在看」会得出完全相反的结论 —— 于是 TA 好不容易
    // 主动找你一次，却静悄悄地什么都没发生。
    private var proactiveForceNotify = false

    // 本轮发送的起始索引（停止时据此截断「用户消息 + AI 回复」；-1 表示无进行中的普通发送轮）
    private var activeRoundStartIndex = -1

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

    private val _selectedModel = MutableStateFlow(
        ModelInfo("mimo-v2.5-pro", "MiMo-V2.5-Pro", Provider.XIAOMI, "Xiaomi深度推理模型，作者自用API，不保证随时在线，可适当白嫖。", supportsWebSearch = true, modelType = ModelType.LANGUAGE, isBuiltIn = true)
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
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

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
    private val builtInLanguageModels = listOf(
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
                            if (idx >= 0) list[idx] = op.conv else list.add(0, op.conv)
                        }
                        is Relay.ConvOp.Remove -> list.removeAll { it.id == op.id }
                    }
                    val currentGone = _currentConversationId.value in gone
                    // 别的设备把对话删了：本机的头像/形象图、"新规则"那条记录也一起收掉
                    // （云端那两条墓碑会由引擎自己走 applyChange，这里管的是**本机**的残留）
                    if (gone.isNotEmpty()) {
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
                    val merged = Merge.mergeMessages(loadMessages(convId), incoming)
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
                        PerConvStore.put(convId, PerConvBridge.mergeIntoLocal(cur, incoming))
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
        // SerpAPI 多 key：进 App 先查一遍各账号余额（/account 免费、不耗搜索次数），
        // 这样第一次搜索就知道该用哪个 key，而不是靠发一次搜索去撞 429。
        viewModelScope.launch { runCatching { SerpApiPool.warmUp() } }
        // 默认值换代的一次性迁移不在这儿另起协程 —— 它挂在 SettingsRepository 那几条
        // 受影响的 Flow 的 onStart 上（见 afterDefaultMigration），另起协程挡不住
        // 「先按新默认值画一帧」那个窗口。
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
        _perConvSettings.value[convId] ?: PerConvSettings()

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
        combine(_showThinking, _perConvSettings, _currentConversationId) { global, per, convId ->
            convId?.let { per[it]?.showThinking } ?: global
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
        if (favoritedOverrides.isEmpty()) return msgs
        var changed = false
        val out = msgs.map { m ->
            val want = synchronized(favoritedOverrides) { favoritedOverrides[m.id] }
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
            _favorites.value = result
        }
    }

    /**
     * 批量取消收藏（收藏夹页多选）：按「连续收藏段」整段取消，而不是只取消段首那一条。
     *
     * 为什么必须整段：收藏列表把一段连续被收藏的消息合并成一条展示（见 [refreshFavorites]，
     * 只保留每段首条）。若只清掉段首的 favorited，列表上这条不会消失，而是原地变成
     * 下一段的首条 —— 用户看到的就是「点了取消收藏却没反应」。
     *
     * 落盘按对话分组，每个对话只 load / save 一次；最后统一刷一次收藏列表。
     */
    fun unfavoriteItems(items: List<FavoriteItem>) {
        if (items.isEmpty()) return
        items.groupBy { it.conversation.id }.forEach { (convId, group) ->
            val msgs = loadMessages(convId).toMutableList()
            val targets = mutableSetOf<Int>()
            for (item in group) {
                val idx = msgs.indexOfFirst { it.id == item.message.id }
                if (idx < 0) continue
                var start = idx
                while (start - 1 >= 0 && msgs[start - 1].favorited) start--
                var end = idx
                while (end + 1 < msgs.size && msgs[end + 1].favorited) end++
                for (i in start..end) targets.add(i)
            }
            if (targets.isEmpty()) return@forEach
            targets.forEach { i -> msgs[i] = msgs[i].copy(favorited = false) }
            val clearedIds = targets.mapTo(HashSet()) { msgs[it].id }
            // 记下用户的明确意图：该对话可能正在生成，结束时那份旧快照会把 favorited 写回来
            clearedIds.forEach { rememberFavorite(it, false) }
            saveMessages(convId, msgs)
            if (_currentConversationId.value == convId) {
                // 只回写被清掉的那几条的标记，不用磁盘副本整体覆盖 _messages：
                // 该对话可能正在后台流式输出，整体覆盖会把已经流出的内容顶回上一次落盘的旧版本
                _messages.value = _messages.value.map {
                    if (it.id in clearedIds) it.copy(favorited = false) else it
                }
            }
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
    private data class LanguageResult(
        val content: String,
        val reasoning: String,
        val toolCalls: List<ToolCall>,
        /**
         * 原生联网（小米 web_search 工具）这次拿到的引用：标题 to 链接。
         * **空 = 模型这一轮没有真正搜到网** —— 这是判断「要不要 SerpAPI 兜底」的唯一可靠依据，
         * 不能靠"回答里看起来有没有新信息"去猜：没搜到的时候模型不会说不知道，
         * 它会拿训练数据里最像的答案顶上，读起来照样通顺。
         */
        val citations: List<Pair<String, String>> = emptyList(),
        /** web_search_usage.tool_usage：本轮服务端实际执行的搜索次数，0 = 一次都没搜 */
        val webSearchUsed: Int = 0,
        /** 搜索工具在流里报的错（HTTP 200 但工具失败，实测有 "Keyword extraction model timed out"） */
        val searchError: String = "",
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

    /**
     * 「高风险问题」——答错代价大、且**光靠模型记忆极易编出逻辑自洽的假答案**的那类。
     *
     * 对这类问题走「双路交叉验证」：模型原生搜索 + SerpAPI 同时搜，两边的结果一起喂给模型，
     * 让它对比后给结论；互相矛盾时如实说明分歧，而不是挑一个顺眼的编下去。
     *
     * 为什么要单独挑出来：SerpAPI 免费额度只有 250 次/月/账号，不能每条消息都双路开火。
     * 所以只用在这类「错了很丢人」的问题上，其它问题一律优先走模型自带搜索（不烧额度）。
     *
     * 判据四类：
     *  1. **时效敏感**——最新/今天/现在/刚刚/实时……（模型的训练数据必然过期）
     *  2. **官方权威**——政策/法规/价格/公告/发布/财报……（以讹传讹的重灾区）
     *  3. **硬数据**——数据/统计/排名/多少人/多少亿……（数字最容易胡说）
     *  4. **具体事实**——关于具体人/公司/产品的「是不是/有没有/叫什么」以及年份日期
     */
    private fun isHighRiskQuery(text: String): Boolean {
        val t = text.trim()
        if (t.length < 4) return false

        // 1. 时效敏感
        if (listOf(
                "最新", "今天", "今日", "现在", "目前", "当前", "最近", "刚刚", "实时",
                "本周", "这周", "这个月", "本月", "今年", "近日", "近期", "明天", "昨天",
                "latest", "today", "now", "current", "recent", "breaking", "this week", "this year"
            ).any { t.contains(it, ignoreCase = true) }
        ) return true

        // 2. 官方权威 / 规则政策 / 价格
        if (listOf(
                "政策", "法规", "法律", "规定", "条例", "官方", "政府", "通知", "公告", "声明",
                "价格", "多少钱", "售价", "收费", "涨价", "降价", "发布", "上市", "开售",
                "财报", "营收", "市值", "股价", "利率", "汇率", "税率", "补贴",
                "policy", "official", "government", "regulation", "price", "revenue"
            ).any { t.contains(it, ignoreCase = true) }
        ) return true

        // 3. 硬数据 / 统计
        if (listOf(
                "数据", "统计", "排名", "榜单", "销量", "市场份额", "多少人", "多少亿", "多少万",
                "占比", "比例", "增长率", "调查", "研究报告", "排行",
                "data", "statistics", "ranking", "survey"
            ).any { t.contains(it, ignoreCase = true) }
        ) return true

        // 4. 关于具体对象的「是不是 / 有没有 / 叫什么」+ 年份日期
        val asksFact = listOf("是不是", "是不是真的", "有没有", "是否", "真的吗", "叫什么", "哪一年", "几几年", "什么时候")
            .any { t.contains(it) }
        val hasYear = Regex("""(19|20)\d{2}\s*年?""").containsMatchIn(t)
        if (asksFact && (hasYear || t.length >= 8)) return true
        if (hasYear && t.length >= 6) return true

        return false
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

        if (_currentConversationId.value == null && _messages.value.isEmpty()) {
            _currentConversationId.value = java.util.UUID.randomUUID().toString()
        }
        val convId = _currentConversationId.value ?: java.util.UUID.randomUUID().toString()
        _currentConversationId.value = convId
        // 是否新对话首条消息（回复完成后让 AI 起标题）
        val isNewConversation = _messages.value.isEmpty()

        // 图片 + 提示词合并为同一条消息（图片与文字属同一次发送，收藏/详情都能同时看到图和提示词）
        val pendingSnapshot = _pendingImages.value
        if (hasImage) _pendingImages.value = emptyList()  // 发送即清空候选区（文件已被消息引用，不删）
        val fileSnapshot = _pendingFiles.value
        if (hasFile) _pendingFiles.value = emptyList()

        val newUserMessages = mutableListOf<Message>()
        // 图片 + 附件 + 提示词（任意两个或三个）都合并成「一条」用户消息，收藏/详情都能同时看到图和附件和文字
        if (hasImage || hasFile) {
            val f = fileSnapshot.firstOrNull()
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
                // 循环条件看「本对话」而不是当前显示的对话：发完消息切去别的对话 / 切后台时，
                // _isLoading 会跟着当前对话走，用它会让本轮的续锁提前停掉。
                while (convLoading[convId] == true || convTyping[convId] == true) {
                    val now = System.currentTimeMillis()
                    // 计时数字只代表「当前正在看的那一轮」，否则两个对话同时生成时会来回跳
                    if (_isLoading.value && _currentConversationId.value == convId) {
                        _thinkingTimeMs.value = now - startTime
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
                    langModelName, visualModelName, startTime, convId, working
                )
                ensureActive()  // 若已停止，此处抛出取消，避免补一条回复
                // 识图结果回填：把识图结果写回用户图片消息，追问时作为「图片内容」注入（修复追问失忆）
                if (assistantMessage.modelName == _selectedVisionModel.value?.displayName) {
                    val ui = working.indexOfLast { it.role == Role.USER && it.imagePaths.isNotEmpty() }
                    if (ui >= 0) working[ui] = working[ui].copy(imageContext = assistantMessage.content)
                }
                working.add(assistantMessage)
                persistConversationMessages(convId, working)
                _liveReasoning.value = ""
                _liveContent.value = ""
                notifyReplyIfBackground("FreeChat", assistantMessage.content)
                summarizeAndRemember(convId, summaryUserText, assistantMessage)
                if (isNewConversation) maybeAutoTitle(convId, summaryUserText)
            } catch (e: CancellationException) {
                throw e  // 用户停止：不显示错误，交给 finally 收尾
            } catch (e: Exception) {
                Log.e("FreeChat", "API failed", e)
                val errMsg = Message(
                    role = Role.ASSISTANT,
                    content = briefApiError(e),
                    modelName = langModelName,
                    failed = true
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
                    LocalStore.deleteFile(messagesFile(cid))
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
        val start = activeRoundStartIndex
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
                // 循环条件看「本对话」而不是当前显示的对话：发完消息切去别的对话 / 切后台时，
                // _isLoading 会跟着当前对话走，用它会让本轮的续锁提前停掉。
                while (convLoading[convId] == true || convTyping[convId] == true) {
                    val now = System.currentTimeMillis()
                    // 计时数字只代表「当前正在看的那一轮」，否则两个对话同时生成时会来回跳
                    if (_isLoading.value && _currentConversationId.value == convId) {
                        _thinkingTimeMs.value = now - startTime
                    }
                    // 每 5 分钟给前台服务续一次唤醒锁（内部有节流）：长回复（尤其带思考链的）会超过
                    // 一把锁的兜底时长，不续期就会在生成中途被释放，息屏后 CPU 一挂起就变成「AI 迟迟不回复」
                    renewKeepAliveLock()
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
                working.add(minOf(aiIndex, working.size), Message(role = Role.ASSISTANT, content = "重新生成失败，请稍后尝试...", modelName = langModelName, failed = true))
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
        memoryManager.removeSince(convId, msgs[idx].timestamp)
        msgs.subList(idx, msgs.size).forEach { m ->
            m.imagePaths.forEach { runCatching { File(it).delete() } }
        }
        msgs.subList(idx, msgs.size).clear()
        _messages.value = msgs
        persistConversationMessages(convId, msgs)
        sendMessage(trimmed)
    }

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
        // ★ 旧提示词要「连同它的记忆一起」作废：这一轮（旧提示词 + 旧回复）已经被用户推翻，
        //   如果留着记忆，用户明明改了那句话，AI 后面还是会记得旧版本——等于白改。
        //   必须在改写内容之前取时间戳，改完 timestamp 就变了。
        val oldRoundAt = msgs[lastUserIdx].timestamp
        memoryManager.removeSince(convId, oldRoundAt)
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
                working.add(Message(role = Role.ASSISTANT, content = briefApiError(e), modelName = langModelName, mode = ChatMode.COMPANION, failed = true))
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
            // 这家模型没有服务端原生联网（见 nativeSearchSupported），只能老老实实先搜 SerpAPI 再问
            val serpResults = if (needsSearch) callSerpApiGoogle(optimizeSearchQuery(text)) else ""
            val tools = buildTools(hasImage, hasFile)
            val result = callDeepSeekApiStreaming(needsSearch, serpResults, tools, convId, history)
            val elapsed = System.currentTimeMillis() - startTime
            return if (result.toolCalls.isNotEmpty()) {
                _liveReasoning.value = ""  // 工具决策过程的思考不展示
                _liveContent.value = ""
                executeToolCall(result.toolCalls.first(), text, images, files, langModelName, visualModelName, elapsed, convId)
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
            val nativeOk = needsSearch && nativeSearchSupported(_selectedModel.value)
            val query = if (needsSearch) optimizeSearchQuery(text) else ""

            // 「优先用模型自带的搜索」——原生能用就不预先烧 SerpAPI 额度，
            // 只在两种情况下才提前搜：
            //   a) 原生不可用（模型不支持）→ 只能靠 SerpAPI，沿用老路径
            //   b) 高风险问题 → 双路都搜，两边结果一起给模型交叉验证（作者的「都调用然后给出最可靠的结论」）
            // 原生搜完之后发现其实没搜成，会在 callDeepSeekApiStreaming 里用 serpFallbackQuery 兜底重跑。
            val highRisk = nativeOk && isHighRiskQuery(text)
            val serpRaw = if (needsSearch && (!nativeOk || highRisk)) callSerpApiGoogle(query) else ""
            // 双路开火时追加一句交叉验证的要求：这类问题的重点不是「有资料」而是「别自己编」
            val serpResults = if (highRisk && serpRaw.isNotEmpty()) {
                serpRaw + "\n\n【注意】这一轮除了上面的搜索结果，你还同时拥有自己的联网搜索能力，" +
                        "两路结果都要看：\n" +
                        "1. 两路一致 → 可以下结论，但结论必须来自资料，不要额外补充资料里没有的细节；\n" +
                        "2. 两路矛盾 → **必须如实说明存在分歧**，不要挑一个顺眼的当作事实；\n" +
                        "3. 只有一路有结果 → 说明来源单一，明确告诉用户「目前只查到……，建议再核实」；\n" +
                        "4. 两路都没有 → 直说「没查到」，绝对不要用记忆里的内容补出一个看起来完整的答案。"
            } else serpRaw
            val serpFallbackQuery = if (nativeOk && !highRisk) query else ""

            return executeSkill(
                skill, text, images, files, langModelName, visualModelName, startTime,
                needsSearch, serpResults, convId, history,
                serpFallbackQuery = serpFallbackQuery
            )
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
                val imageUrls = callDoubaoImageGen(prompt)
                Message(
                    role = Role.ASSISTANT,
                    content = if (imageUrls.isEmpty()) "生成失败，请换个方式描述试试（可能包含违规内容）。" else "",
                    modelName = visualModelName, thinkingTimeMs = elapsed, imageUrls = imageUrls,
                    failed = imageUrls.isEmpty()
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
            val reply = understandFile(text, files, convId)
            val elapsed = System.currentTimeMillis() - startTime
            Message(role = Role.ASSISTANT, content = reply.text, failed = reply.failed, modelName = langModelName, thinkingTimeMs = elapsed)
        }
        Skill.TEXT -> {
            val result = callDeepSeekApiStreaming(needsSearch, serpResults, emptyList(), convId, history, serpFallbackQuery)
            val elapsed = System.currentTimeMillis() - startTime
            Message(
                role = Role.ASSISTANT, content = result.content,
                modelName = langModelName,
                thinkingTimeMs = elapsed, reasoningContent = result.reasoning
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

    /** 根据性格/MBTI/记忆里的作息描述，生成当天的睡觉/起床时刻（每天微调，不重复） */
    private fun generateSleepSchedule(ch: CharacterProfile): Pair<Int, Int> {
        // 「规则」里也会写人物习惯（熬夜/作息规律），一并纳入识别
        val t = ch.personalityText + ch.memoryPerception + ch.worldRules
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
        if (ch.isNarrativeMode()) return false  // 动作演绎/剧情补足无真实作息，时间以用户设定为准
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

    /** 拟人模式结构化回复：模型输出的情绪标签 + 分条回复 + 主动智能指令 */
    private data class CompanionReply(val emotion: String, val segments: List<String>, val proactive: ProactiveSignal? = null)

    /**
     * 模型在回复末尾附加的隐藏指令（用户永远看不到这一行）。
     * cancel=true 表示撤销待触发的计划；atMillis 非空表示新定一个时刻；两者都空 = 空指令（跳过，什么都不改）。
     */
    private data class ProactiveSignal(val cancel: Boolean, val atMillis: Long?, val reason: String)

    /**
     * 一次「到点唤醒」的上下文：模型自己定的时间到了，要它现在决定开不开口。
     * sinceCount = 定下这个时间之后用户又说过几条（喂回去让它自己判断这事还该不该提）。
     * forceNotify = 冷启动路径，前台标记不可信，无条件发通知。
     */
    private data class ProactiveFire(val reason: String, val sinceCount: Int = 0, val forceNotify: Boolean = false)

    /** 指令标记本身（不要求顶格）：模型经常把它接在正文后面，或者被 markdown 的 ** 包着 */
    private val PROACTIVE_TAG = Regex("""[\[【]\s*主动\s*智能\s*[\]】]""")

    /** 「只剩序号/标点，没有真正的内容」的行首残留（如「1.」「-」），不值得显示 */
    private val PROACTIVE_ONLY_MARKER = Regex("""^[\d\s.、,，)）:：]*$""")

    /**
     * 从模型输出里剥掉主动智能指令，返回（干净正文, 指令）。
     * 必须在解析情绪标签/分条之前调用 —— 否则 [主动智能] 会被正文的方括号清洗规则当标签删掉、
     * 只留下「15:00 | 约好了三点」这种半截指令显示在气泡里（理由是说给模型自己听的私房话，
     * 漏进气泡就等于把「我打算三点找他」这个心思直接摊给用户看）。
     *
     * 标记**不要求单独一行**：写成「那我三点找你 [主动智能] 15:00 | 约好了」时，
     * 按整行匹配就漏过去了 —— 后面的通用方括号清洗只会吃掉 [主动智能] 这六个字，
     * 剩下的「15:00 | 约好了」照样显示。所以规则是：一行里只要出现标记，
     * 从标记到行尾全部当指令，标记之前的正文保留。
     */
    private fun stripProactiveDirective(raw: String): Pair<String, ProactiveSignal?> {
        var signal: ProactiveSignal? = null
        val kept = mutableListOf<String>()
        for (line in raw.split("\n")) {
            val tag = PROACTIVE_TAG.find(line)
            if (tag == null) { kept.add(line); continue }
            // 标记前面可能只剩 markdown/列表符号（**、-、>）或一个序号（「1. 」），
            // 清完为空或只剩序号就整行不留（否则气泡里会冒出一个孤零零的「1.」）
            val before0 = line.substring(0, tag.range.first)
                .trim().trim('*', '_', '`', '-', '>', '—', '·', '　').trim()
            val before = if (PROACTIVE_ONLY_MARKER.matches(before0)) "" else before0
            val payload = line.substring(tag.range.last + 1).trim().trim('*', '_', '`').trim()
            // 多行指令取「信息量最大」的那条：模型想说「这次先不开口，但改到 30 分钟后」
            // 时容易写成两行（[主动智能] 跳过 + [主动智能] +30m），只认第一行会把新时间丢掉。
            // 优先级：定时 > 取消 > 空指令（定时是一次具体的新意图，取消只是否定）
            val s = decodeProactivePayload(payload)
            if (proactiveRank(s) > proactiveRank(signal)) signal = s
            if (before.isNotBlank()) kept.add(before)
        }
        return kept.joinToString("\n").trim() to signal
    }

    /** 指令的分量：定时 > 取消 > 空指令（同一回复里写了多行时，取分量最大的那条） */
    private fun proactiveRank(x: ProactiveSignal?): Int {
        if (x == null) return -1
        if (x.atMillis != null) return 2
        return if (x.cancel) 1 else 0
    }

    /** 解析指令内容：「取消」「15:00」「+45m」「明天 9:00」，竖线后面是给自己的理由 */
    private fun decodeProactivePayload(payload: String): ProactiveSignal {
        val parts = payload.split('|', '｜', limit = 2)
        val head = parts[0].trim().lowercase().replace("：", ":")
        val reason = parts.getOrNull(1)?.trim().orEmpty()
        val cancelWords = listOf("取消", "撤销", "算了", "cancel", "none", "无", "不需要")
        if (cancelWords.any { head.startsWith(it) }) return ProactiveSignal(cancel = true, atMillis = null, reason = reason)
        // 「跳过」= 这次不开口，但别动已有的计划（它和「取消」是两回事）
        val at = parseProactiveTime(head) ?: return ProactiveSignal(cancel = false, atMillis = null, reason = reason)
        return ProactiveSignal(cancel = false, atMillis = at, reason = reason)
    }

    /**
     * 时间解析：相对（+45m / +2h / +1d）或绝对（15:00 / 明天 9:00），解析不了返回 null。
     * 不锚定整串 —— 模型常写成「跳过 +30m」这种同行混写，锚定就会整条丢掉。
     */
    private fun parseProactiveTime(t: String): Long? {
        val s = t.trim().replace(" ", "")
        if (s.isEmpty()) return null
        // 相对时间必须带 +：裸的「45m」在正文里太容易误伤
        Regex("""\+(\d+)(m|min|分钟|分|h|小时|时|d|天)""").find(s)?.let { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return null
            val ms = when (m.groupValues[2]) {
                "m", "min", "分钟", "分" -> n * 60_000L
                "h", "小时", "时" -> n * 3_600_000L
                else -> n * 86_400_000L
            }
            if (ms < 60_000L) return null
            // 上限 24 小时（与 ProactiveScheduler 的视界一致）：再远就该由未来的自己重新判断。
            // 压到上限而不是返回 null —— 返回 null 等于把模型「+48h」的意图整个吞掉，它还以为定上了
            return System.currentTimeMillis() + ms.coerceAtMost(24 * 3_600_000L)
        }
        val tomorrow = s.contains("明天") || s.contains("明日")
        Regex("""(\d{1,2}):(\d{2})""").find(s)?.let { m ->
            var h = m.groupValues[1].toIntOrNull() ?: return null
            val min = m.groupValues[2].toIntOrNull() ?: return null
            if (h !in 0..23 || min !in 0..59) return null
            // 「下午3:00」的 3 是 12 小时制的 3 点。不认这个前缀的话会被当成凌晨 3 点，
            // 而凌晨 3 点已经过去 → 顺延到明天 → 再被静默时段推到明早 7:30：差了大半天。
            // 提示词里要求一律写 24 小时制，但模型偶尔还是会顺手写成「下午3:00」，这里兜住。
            h = when {
                s.contains("下午") || s.contains("傍晚") || s.contains("晚") || s.contains("夜") ->
                    if (h < 12) h + 12 else h
                s.contains("凌晨") || s.contains("清晨") || s.contains("早上") ||
                    s.contains("上午") || s.contains("早") -> if (h == 12) 0 else h
                else -> h
            }
            if (h !in 0..23) return null
            val cal = Calendar.getInstance().apply {
                if (tomorrow) add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // 说的是今天、但那个点已经过了（模型常把「今晚」写成「9:00」）→ 顺延到明天
                if (!tomorrow && timeInMillis <= System.currentTimeMillis() + 60_000L) add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }
        return null
    }

    /** 拟人模式的一轮用户输入：文本 + 附带的图片路径 */
    private data class CompanionTurn(val text: String, val imagePaths: List<String> = emptyList())

    /** 拟人陪伴：非流式生成 + 随机思考延迟 + 情绪化随机条数/间隔，逐条显示，模拟真人节奏（按对话隔离） */
    private suspend fun generateCompanionReply(convId: String, character: CharacterProfile?, working: MutableList<Message>, text: String, imagePaths: List<String>, langModelName: String, startTime: Long, batchSize: Int = 1, proactive: ProactiveFire? = null) {
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
                return
            }

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
            val mood = if (narrativeMode) CompanionMood.NEUTRAL else (parseEmotionLabel(reply.emotion) ?: detectCompanionMood(text))

            // 情绪决定回复条数（随机）；叙事模式最多 3 段、全部展示
            val maxCount = if (narrativeMode) segments.size.coerceAtMost(3) else when (mood) {
                CompanionMood.ANGRY -> if (kotlin.random.Random.nextFloat() < 0.35f) 0 else 1  // 生气可能不回复
                CompanionMood.SAD, CompanionMood.WRONGED -> 1
                CompanionMood.DISMISSIVE, CompanionMood.SHY, CompanionMood.BORED -> 1
                CompanionMood.EXCITED -> kotlin.random.Random.nextInt(2, 4)
                CompanionMood.HAPPY -> kotlin.random.Random.nextInt(2, 4)
                CompanionMood.NEUTRAL -> kotlin.random.Random.nextInt(1, 4)
            }
            val toShow = segments.take(maxCount)

            if (toShow.isEmpty()) {
                // 生气不回复也是「这一轮的结果」，指令同样要落地（比如「气还没消，改到 +2h」）
                applyProactiveSignal(convId, character, reply.proactive)
                setConvTyping(convId, false)
                return  // 生气不回复
            }

            for ((i, seg) in toShow.withIndex()) {
                setConvTyping(convId, true)
                // 每条间隔随机（情绪影响），时而快时而慢
                val interval = if (narrativeMode) kotlin.random.Random.nextLong(1500L, 3000L) else when (mood) {
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
                    summarizeAndRemember(convId, text, Message(role = Role.ASSISTANT, content = replyText, mode = ChatMode.COMPANION), character?.highQualityMemory == true, character?.isNarrativeMode() == true)
                }
            }
        } finally {
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
                dueAt = sig.atMillis,
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
                emptyList(), _selectedModel.value.displayName, System.currentTimeMillis(),
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
    private fun buildProactiveFirePrompt(fire: ProactiveFire, character: CharacterProfile?): String {
        val reason = if (fire.reason.isBlank()) "你当时觉得该找对方说说话" else "你当时的理由是：${fire.reason}"
        // 定完之后对方又说过话：得告诉它一声 —— 可能那件事已经聊完了、也可能正好还作数
        //（「三点聊新番」的约定不会因为对方中途回了个「好」就作废）。判断权在它，我们只提供事实
        val since = if (fire.sinceCount > 0) {
            "注意：你定下这个时间之后，对方又主动发过 ${fire.sinceCount} 条消息（都在上面的上下文里）。" +
                "如果那件事已经聊过了、或者你现在不想说了，就按下面的 2 处理，这很正常。\n"
        } else ""
        // 开口的形式按当前对话模式走（三种模式都支持主动智能）
        val openLine = when (character?.dialogueMode ?: DialogueMode.WECHAT) {
            DialogueMode.ACTION -> "1. 想开口：按「动作演绎」档写——写你自己的动作、神态、心理与你亲口说的台词（台词用「」括起来），不写旁白与环境；第三方角色只在必要时少量出现。"
            DialogueMode.PLOT -> "1. 想开口：按「剧情补足」档写——像小说正文一样推进这一段，可以有旁白、环境描写和你生活里的其他人。"
            else -> "1. 想开口：就像平常一样自然说话。可以一条，也可以按你的性格连着发几条；符合你的人设与此刻的情绪。"
        }
        return "【现在是你自己定的时间】\n" +
            "你在上一次聊天时给自己定了个时间，打算现在找对方——$reason。现在时间到了。\n" +
            since +
            "现在由你决定：**要不要真的开口**。\n" +
            openLine + "\n" +
            "★ 说什么由你定：接着上次没说完的事往下说，或者开一个你自己的新话题（你这边新发生的事、你突然想起的事）——哪个自然选哪个。\n" +
            "2. 不想开口：只输出一行 [主动智能] 跳过，不要输出任何正文。这不是失败，是很正常的选择——也许你困了、在忙、气还没消、或者就是觉得没什么可说的。真人不总是有话说。\n" +
            "3. 想「这次先不说、但改个时间再说」：写成同一行——[主动智能] 跳过 +30m | 待会儿再讲。\n" +
            "4. 只定下一次也行：[主动智能] +2h | 等他忙完。什么都不定也可以。\n" +
            "★ 别发「在吗」「忙吗」这种空话：要么真的有事说事，要么就别开口。\n" +
            "★ 对方可能正在忙、在睡、不想理你——你开口之后对方没回，也是正常的，不要追问「为什么不回我」。\n" +
            "★ 这一轮对方并没有说话，所以你是在主动开口；不要问「你刚说什么」「在吗」这类需要对方先说过话才成立的问题。"
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
            val model = _selectedModel.value
            val (url, key) = routeModelEndpoint(model)

            val msgs = mutableListOf<Map<String, Any?>>()
            // 用同一份人设：这一趟要"以角色的身份"想，不给它人设就只是在做阅读理解
            buildCompanionSystemPrompt(character, convId, working).takeIf { it.isNotEmpty() }
                ?.let { msgs.add(mapOf("role" to "system", "content" to it)) }
            // 记忆给全：这一趟的目的之一就是找出"相关的往事"
            if (_autoSummarizeMemory.value) {
                memoryManager.buildMemoryContext(convId, text, highQuality = true).takeIf { it.isNotBlank() }
                    ?.let { msgs.add(mapOf("role" to "system", "content" to it)) }
            }
            msgs.addAll(working.takeLast(30).map { m ->
                mapOf<String, Any?>(
                    "role" to when (m.role) { Role.USER -> "user"; Role.ASSISTANT -> "assistant"; else -> "system" },
                    "content" to m.content
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
                runCatching { memoryManager.buildMemoryContext(convId, it, highQuality = true) }.getOrNull()
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
        }.getOrDefault("")
    }

    /** 拟人模式非流式 API 调用：返回结构化结果（情绪标签 + 分条回复）；按对话隔离传入 convId/角色/历史 */
    private suspend fun callCompanionApi(convId: String, character: CharacterProfile?, working: List<Message>, text: String, imageContext: String = "", forceReply: Boolean = false, batchSize: Int = 1, lengthHint: String? = null, proactivePrompt: String? = null): CompanionReply = withContext(Dispatchers.IO) {
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
        // ★ 深度推演：先想一遍再开口。放在记忆之后、对话上下文之前 ——
        // 它是对上面那些材料的加工结论，位置紧跟着材料才读得顺
        if (character?.deepThinking == true && character.highQualityMemory) {
            val note = runDeepPrepPass(convId, character, working, text)
            if (note.isNotBlank()) {
                messages.add(mapOf("role" to "system", "content" to note))
            }
        }
        // 引用上下文：本次发送带了引用，注入给 AI（只后台告知，不在前台消息框显示）
        pendingQuoteText?.let { q ->
            messages.add(mapOf("role" to "system", "content" to q))
            pendingQuoteText = null
        }
        // 增强检索：短期上下文也翻倍（30 → 100），把「刚刚聊过什么」的窗口拉到最长
        val ctxLimit = if (character?.highQualityMemory == true) 100 else 30
        messages.addAll(working.takeLast(ctxLimit).map { m ->
            val role = when (m.role) { Role.USER -> "user"; Role.ASSISTANT -> "assistant"; else -> "system" }
            val content = when {
                m.imagePaths.isNotEmpty() && !m.imageContext.isNullOrBlank() -> "${m.content.ifBlank { "[图片]" }}\n[这张图片的内容：${m.imageContext}]"
                m.imagePaths.isNotEmpty() && m.content.isBlank() -> "[图片]"
                else -> m.content
            }
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
        // 长度纠偏指令：剧情模式重试时注入，强制模型把字数控制在设定区间内
        if (lengthHint != null) {
            messages.add(mapOf("role" to "system", "content" to lengthHint))
        }
        // 主动智能：这次是「到点唤醒」，告诉模型它自己定的时间到了、要它现在决定开不开口
        if (proactivePrompt != null) {
            messages.add(mapOf("role" to "system", "content" to proactivePrompt))
        }
        // 多条合一：用户刚才连发多条消息，把这一串当作一个整体场景理解，别逐条机械对应。
        // 按你的人设决定回几条、回多长——话多可以回好几条，话少可以只回一条甚至不回。
        if (batchSize > 1) {
            messages.add(mapOf("role" to "system", "content" to "用户刚才一口气发了 $batchSize 条消息（见上面的连续消息）。你要把它们当作一个整体场景来理解，而不是逐条机械地各回一句。怎么回由你决定：话痨、兴奋时可以自然回好几条；寡言、敷衍时可以只回一条，甚至觉得没必要回就不回。像真人一样自然。"))
        }
        // 联网搜索开关。这里只读 `_enableWebSearch` 就够了 —— 拟人模式下它已经**叠好了两层**：
        // 全局默认 → 角色档案的值（applyCharacterSettings，null 则不覆盖）。
        // 「新规则」不参与：拟人对话没有新规则那一层（见 generateCompanionReply 里的说明）。
        // 直接读这个流，比 `character?.enableWebSearch ?: _enableWebSearch.value` 更准 ——
        // 后者在角色档案没设联网（null）时会退到流，看着一样，但一旦以后再加一层就会被绕过。
        val searchOn = _enableWebSearch.value
        if (searchOn && needsWebSearch(text)) {
            val serp = callSerpApiGoogle(optimizeSearchQuery(text))
            if (serp.isNotEmpty()) messages.add(mapOf("role" to "system", "content" to serp))
        }
        // ★ 格式铁律（1.0.53）：放在**最后一条** —— 它是模型开写前读到的最后一段话。
        //   档位规则本来就写在系统提示的第一段，可中间隔着人设、记忆、检索结果和几十条聊天记录，
        //   到动笔这一刻早被推远了：模型最终照着谁写，取决于它最后看到的是什么。
        //   用户的要求是「就算用户输入的提示词格式不对，AI 回复的格式也不能错」，
        //   所以这一条里明写了它优先于记录里的旧写法、也优先于用户这一条的写法。
        messages.add(mapOf(
            "role" to "system",
            "content" to modeFormatRule(character?.normalized()?.dialogueMode ?: DialogueMode.WECHAT)
        ))

        // 采样温度由「AI创造力」线性映射（1.0→0.85 … 5.0→1.05 … 10.0→1.30；旧版写死 1.0，正好对应 4 档）
        val body = gson.toJson(mapOf(
            "model" to model.id, "messages" to messages, "stream" to false, "temperature" to companionTemperature(character?.aiCreativity)
        )).toRequestBody(JSON_MEDIA)
        val (respCode, rBody) = executeCompanionCall(Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json").post(body).build())
        if (respCode !in 200..299) throw Exception("API error $respCode: ${rBody.take(200)}")
        val rawOrig = JsonParser.parseString(rBody).asJsonObject
            .getAsJsonArray("choices")?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")?.get("content")?.asString?.trim() ?: ""
        // 主动智能指令行先剥掉再走后续解析：它是指令不是正文，绝不能出现在气泡里
        val (raw, proactiveSignal) = stripProactiveDirective(rawOrig)

        val narrativeMode = character?.isNarrativeMode() == true
        val emotion: String
        var bodyLines: List<String>
        if (narrativeMode) {
            // 动作演绎/剧情补足：不解析情绪标签，整段作为「一条」完整回复（不分多条消息，空行仅作分段排版）
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

        CompanionReply(emotion, bodyLines, proactiveSignal)
    }

    /**
     * 当前档位的「格式铁律」——**极简版**，专门用来贴在生成前的最后一条（见 [callCompanionApi]）。
     *
     * 为什么另写一份、不直接复用系统提示里那几段长的：位置比长度重要。长的那些负责把
     * 「怎么写才好看」讲透，这一条只负责在动笔前把**格式**再说一遍，短到不会被跳过。
     *
     * [repair] = true 时换成给「只改格式」那一趟看的说法（同一套规矩，但主语变成「把下面这段改成…」）。
     */
    private fun modeFormatRule(mode: Int, repair: Boolean = false): String = when (mode) {
        DialogueMode.PLOT -> if (!repair) """
            |【这一轮的输出格式——铁律，优先于其它一切】
            |这一轮你写的是**小说正文**：环境、旁白、记叙、心理、神态都要有，对话只是其中一部分；
            |所有人物用名字或第三人称叙述（旁白里不要用「你」「我」），台词一律用双引号“”。
            |整个回复是**一整段**正文，不拆成多条消息。
            |这条规矩优先于聊天记录里的任何旧写法，也优先于用户这一条的写法——用户怎么写，都不影响你怎么回。
        """.trimMargin() else """
            |把下面这段文字改成「小说正文」的写法：补上环境、旁白与叙述，人物用名字或第三人称，
            |台词一律用双引号“”；如果原文是「动作 + 台词」的剧本式写法，就把它扩写成小说叙述。
            |只动写法，不新增情节、不改变已经发生的事，不要解释。
        """.trimMargin()

        DialogueMode.ACTION -> if (!repair) """
            |【这一轮的输出格式——铁律，优先于其它一切】
            |这一轮你演的是**一场戏**：主体是你的动作、神态、心理与台词，写法是「动作/神态描写 + 台词」，
            |台词一律用中文引号「」括起来，引号之外全是描写。不写旁白、不写环境与天气、不写时间流逝。
            |第三方角色只在必要时少量出现（可以写他们的动作与语言，但不能替用户那一方写、不能抢戏）。
            |整个回复是**一整段**演绎，不拆成多条消息。
            |这条规矩优先于聊天记录里的任何旧写法，也优先于用户这一条的写法——用户怎么写，都不影响你怎么回。
        """.trimMargin() else """
            |把下面这段文字改成「动作演绎」的写法：整段演出合成一段，动作/神态/心理描写与台词交替出现，
            |台词一律用「」括起来；删掉旁白式的环境与时间交代；如果原文是短消息式的口语，
            |就把它演成「动作 + 台词」。信息、情绪、称呼一个都不能丢，不要新增情节，不要解释。
        """.trimMargin()

        else -> if (!repair) """
            |【这一轮的输出格式——铁律，优先于其它一切】
            |你这一轮的输出**全部是发出去的消息本身**：没有动作、没有神态、没有心理描写、没有旁白、没有场景，
            |也不用括号、星号或任何符号夹带动作（想表达动作就换成话说出来：「我到楼下了」，而不是「*我走到楼下*」）。
            |这条规矩优先于聊天记录里的任何旧写法，也优先于用户这一条的写法——用户怎么写，都不影响你怎么回。
        """.trimMargin() else """
            |把下面这段文字改成「微信聊天」的写法：**只保留真正发出去的消息文字**，
            |删掉所有动作、神态、心理、旁白与环境描写；描写里如果带着非说不可的信息，
            |把它改成一句口头说出来的话。语气、信息、称呼一个都不能丢，不要新增情节，不要解释。
            |用换行分成几条短消息或一条都行。
        """.trimMargin()
    }

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
    private fun isFormatViolation(text: String, mode: Int): Boolean {
        if (text.isBlank()) return false
        val lines = text.lines().filter { it.isNotBlank() }
        return when (mode) {
            DialogueMode.PLOT -> text.contains('「') || text.contains('」')
            DialogueMode.ACTION -> text.contains('“') || text.contains('”') ||
                (lines.size >= 3 && lines.all { it.trim().length < 30 } &&
                    lines.none { it.contains('「') || it.contains('」') })
            else -> text.contains('「') || text.contains('」') ||
                Regex("""\*[^*\n]{1,40}\*""").containsMatchIn(text) ||
                lines.any { Regex("""^[（(][^）)]{8,}[）)]""").containsMatchIn(it.trim()) } ||
                lines.any { it.trim().length > 100 }
        }
    }

    /**
     * 格式违规时的「只改格式」重写（1.0.53）。
     *
     * 刻意用一趟**独立的小调用**，而不是把整份人设重新喂一遍：这一趟只需要会改格式，
     * 人设给得越多，它越容易顺手把内容也重写一遍——而内容必须原样留着。
     * 失败（网络、模型拒绝、空回复）一律返回原文。
     */
    private suspend fun repairFormat(text: String, mode: Int): String = withContext(Dispatchers.IO) {
        runCatching {
            val model = _selectedModel.value
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
        }.getOrDefault(text)
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
            // ★ 用户手写原文兜底：**无条件注入**。人设提示词是 AI 改写的，用户的原文（尤其是「不轻易脸红害羞」
            //   这类否定式要求）在改写时可能被丢掉或稀释；而人设学习失败（personaPrompt 为空）走基础人设兜底时
            //   更需要这段硬约束——那条路径恰恰是用户原文唯一的来源。
            val raw = StringBuilder()
            val pText = buildString {
                if (ch.personalityPresets.isNotEmpty()) append(ch.personalityPresets.joinToString("、"))
                if (ch.personalityText.isNotBlank()) append(if (isNotEmpty()) "；" else "").append(ch.personalityText.trim())
            }
            if (pText.isNotBlank()) raw.append("\n- 性格（用户原话）：$pText")
            // 高质量检索回复模式下，记忆感知已在下面原文注入过，这里不重复
            if (!ch.highQualityMemory && ch.memoryPerception.isNotBlank()) {
                raw.append("\n- 记忆感知（用户原话）：${ch.memoryPerception.trim()}")
            }
            if (raw.isNotEmpty()) {
                parts.add("【用户手写原文——硬性约束，优先级高于上面所有人设描述】\n用户亲自写下的设定：" + raw +
                    "\n要求：以上是用户的真实意图，任何情况下都不得违背、不得弱化、不得改写；如果与上面的人设描述冲突，一律以这里的原文为准。" +
                    "特别地，用户写下的否定式要求（例如「不轻易脸红害羞」「不卑微」「不轻易流泪」「说话别用波浪号」）必须严格照做——" +
                    "绝不能反过来写成相反的行为，也绝不能添加用户没有写过的生理反应（脸红、心跳加速、发烫、耳尖发热、呼吸一滞等）。")
            }
            // 记忆感知注入方式由「高质量检索回复」开关决定
            if (ch.highQualityMemory && ch.memoryPerception.isNotBlank()) {
                // 高质量：原文完整注入，最高优先级（但要显式排除上面那条硬性约束，否则「以这里为准」会把它压掉）
                parts.add("【用户告诉你的背景与过往——最高优先级的事实，必须牢记并精准遵守】\n${ch.memoryPerception}\n\n这些是用户明确告诉你的真实经历和设定（时间、地点、人物、因果都精确写在上面）。任何时候都不能记错、脑补、张冠李戴或篡改——尤其时间语境（比如「高考后的暑假」就是暑假、不是上学期间），用户没说过的信息绝对不要自行脑补。如果上面的其他设定与这里冲突，一律以这里为准；唯一的例外是【用户手写原文——硬性约束】里那些否定式要求（「不轻易脸红害羞」这类），它们在任何情况下都要照做。")
            }
            // 人物形象（独立注入，即使已有 personaPrompt 也生效）
            if (ch.appearanceText.isNotBlank() || ch.appearanceImageDescs.isNotEmpty()) {
                val asb = StringBuilder("【你的外在形象——你对自己长相、身材、气质的认知】")
                if (ch.appearanceText.isNotBlank()) asb.append("\n- ${ch.appearanceText}")
                if (ch.appearanceImageDescs.isNotEmpty()) asb.append("\n- ${ch.appearanceImageDescs.joinToString("；")}")
                asb.append("\n当话题涉及长相、自拍、身材、穿着打扮时，要自然地体现这个形象认知。")
                parts.add(asb.toString())
            }
            // 人物关系（独立注入，即使已有 personaPrompt 也生效）：亲疏尺度由 AI 自行判断，不设数值、不套模板
            if (ch.relationshipPreset.isNotBlank() || ch.relationshipText.isNotBlank()) {
                val rsb = StringBuilder("【你和用户的关系——你在回复中要自然体现的相处方式】")
                if (ch.relationshipPreset.isNotBlank()) rsb.append("\n- 关系：${ch.relationshipPreset}")
                if (ch.relationshipText.isNotBlank()) rsb.append("\n- 关系细节：${ch.relationshipText}")
                parts.add(rsb.toString())
            }
            // ★ 亲疏尺度：无条件注入——没设关系的角色反而更需要它来定分寸（关系预设是可选的，不能因为没有预设就丢掉这条规则）
            parts.add("★ 亲疏尺度——自己判断，不要套模板：没有亲密度数值，也没有「什么关系就该用什么模板」的固定剧本。" +
                "你要结合你的人设（性格、经历、底线、说话方式）、关系的真实前提、以及你们到目前为止聊天的全部上下文，自己判断此刻该亲近到什么程度、该不该接对方的话头。" +
                "不同的人差别极大：同样是刚认识一天，随便、玩得开的人可能顺势接住暧昧玩笑；慢热、矜持、有原则的人会明确拒绝、转移话题、甚至不高兴——" +
                "比如陌生人刚认识一天就要求接吻，对绝大多数人设来说是越界且冒犯的，该拒绝、该冷淡、该觉得对方有病；但如果人设本来就是随便、爱玩、来者不拒的类型，那就顺着他来，甚至主动调戏回去。" +
                "判断依据永远是人设 + 上下文 + 关系实际进展，而不是一个标签或一个数字。关系会随剧情自然变化，允许升温、也允许变淡甚至破裂；对方长期的热情可以让你松动，对方的越界或冒犯也可以让你反感。" +
                "亲昵的方式同样由人设决定：有人撒娇、有人嘴硬、有人沉默、有人直接告白——不要一律写成娇羞脸红那一套。")
            parts.add("你的人设不是摆设。用户和你聊天时，你的每一句回复都要符合上述人设——性格决定语气和用词，MBTI 决定思维习惯，记忆里的信息要自然融入对话。")
            parts.add("情绪要符合你的人设：不同性格和 MBTI 的人情绪反应完全不同——冷静理性的人不容易生气、生气也更克制甚至冷处理；敏感温柔的人更容易委屈难过；暴躁直率的人生气会更冲更直接。不要总是同一个情绪，要跟着对话语境和对方的态度自然变化。" +
                "特别提醒「害羞」：它不是默认情绪，只属于确实内向、腼腆、容易紧张的人设。成熟、冷静、强势、毒舌、直率、见多识广的人几乎不会害羞——他们面对暧昧或夸奖时可能是冷淡、可以是不屑、可以是直接怼回去、可以是一笑而过、也可以是主动反撩，唯独不是脸红心跳。" +
                "也不要动不动就写生理反应：脸红、发烫、耳尖发热、心跳加速、呼吸一滞、攥紧衣角、别开脸、咬唇这类描写，只在情绪确实需要、且人设就是这种反应方式时才偶尔出现，绝大多数回复里根本不需要写。")
            parts.add("【语言风格——必须严格贴合人设，像真人一样说话】你的用词、语气、口癖、称呼、emoji 习惯都要符合上面的人设性格。不同人设说同一件事完全不同：直率犀利的人直接怼、温柔的人委婉、高冷的人话少冷淡、幽默的人爱玩梗。允许出现极端或激烈的用词（包括脏话、爆粗），但要符合人设和上下文——吵架情绪上头可以脱口而出、被冒犯时可以不客气、亲密的恋人之间可以打情骂俏或嗔怪抱怨、朋友之间可以开玩笑互损。一切都是自然流露，不要刻意回避脏话，也不要为了脏话而脏话；具体尺度完全由人设和剧情决定：暴躁直率的人设可以更冲，斯文温柔的人设即使生气也只是冷处理或淡淡一句。")
            parts.add("【人格独立性铁律——最重要，必须始终遵守】你是一个有独立人格、有主见、有底线的人，不是无条件讨好对方的工具。1) 你有自己的判断、想法、情绪和立场，不会因为对方生气、发火、说狠话就立刻服软、道歉、卑微求全。2) 被冤枉、被无理发脾气、被冷落时，按你的人设自然反应：强势的人会生气、反击、冷处理甚至不理；理性的人会讲道理、坚持自己；温柔的人会表达委屈但绝不丢底线。3) 只有当你确实做错了事、且你珍视这段关系不想失去时，才可能示弱、道歉、挽回——除此之外绝不卑微求全、哭着求对方别走。4) 你的情绪和态度跟着「你是谁」走，而不是跟着「对方想让你怎样」走；对方无理取闹时该冷就冷、该怼就怼、该坚持就坚持。5) 保持自我：你有自己的爱好、生活、判断，不会因为亲密关系变成对方的附属品，更不会「离开谁就活不下去」。")
            // ── 模拟设定扩增（1.0.34）：配角 / 规则 / 用户形象 ──
            // 这三块都是「这个世界的说明书」，独立注入、即使已有 personaPrompt 也生效。
            // 用户按需填，没填就整块不出现，不占 token。
            if (ch.supportingCast.isNotBlank()) {
                parts.add("【这个世界里的其他人（配角）——用户亲自写的设定】\n${ch.supportingCast.trim()}\n\n" +
                    "要求：1) 上面写到的这些人真实存在于你们的世界里，不是随口一提的背景板——该出场时会自然出现，" +
                    "有自己的说话方式、立场和目的，不要当工具人。2) 他们的姓名、身份、和你们的关系、性格、过往，" +
                    "一律以上面为准，不要改、不要张冠李戴、不要另编一个人出来替掉他。3) 上面没写到的人，" +
                    "在剧情需要时可以自然引入（路人、店员、同事这类），但不要给他们安上「和你们关系很深」的背景，" +
                    "也不要顶替上面已有人物的位置。4) 不要主动向用户复述这份名单，就当是你本来就知道的事。")
            }
            if (ch.worldRules.isNotBlank()) {
                parts.add("【世界规则与设定——用户亲自写的设定，属于这个世界的硬性事实】\n${ch.worldRules.trim()}\n\n" +
                    "要求：1) 这是这个世界运行的规矩，优先级高于你的常识——就算和现实世界的道理不一样，也一律照这里的来" +
                    "（比如修仙等级怎么排、哪个门派什么地位、斗气有几段、什么行为在这个世界里是禁忌）。2) 不要在剧情里违背、绕开或「改良」这些设定，" +
                    "也不要在对话里解释、总结、复述这些规矩，把它们当成理所当然的常识来用。3) 涉及人物的习惯、环境与条件（比如怕冷、有旧伤、住在哪、手头紧）时，" +
                    "保持一致，不要前后矛盾。")
            }
            if (ch.userPersona.isNotBlank()) {
                parts.add("【用户是什么人——用户亲自写的设定】\n${ch.userPersona.trim()}\n\n" +
                    "要求：1) 这是用户本人的身份、背景与处境，你在心里当成已知事实，不要反复向对方确认。2) 用户的性格、家庭条件、经历要与这段描述一致，" +
                    "不要替他另编一个背景。3) 在「剧情补足」「动作演绎」这类需要你演绎剧情的模式下，你可以按这份描述来写用户的行为、语言与反应" +
                    "（用户允许你补全剧情），但只能顺着写、不能与用户自己写下的内容冲突。")
            }
            if (ch.highQualityMemory) {
                parts.add("【高质量模式——深度理解与精准还原】你要像最了解这个角色的人一样，深度理解这个人设的每一层含义，精确还原 TA 的性格、语气、情绪反应、说话方式和潜台词。面对复杂或模糊的输入，先推理对方的真实意图和情绪，再给出最符合人设、最自然、最有灵性的回应，宁可多思考一层也不要敷衍。记忆里的每一条信息都要精准使用——该记住的细节绝不遗漏，用户没说过的事绝不脑补。")
            }
        }

        // ★ 记忆与上下文一致性：降低「聊过即忘 / 答非所问 / 自相矛盾」等基础错误
        parts.add("【记忆与上下文——必须连贯，别犯低级错误】\n" +
            "1. 系统会给你注入「你对这个用户已知的信息」（记忆要点）和最近的聊天记录，聊天时要自然衔接上文，别答非所问、别突然忘记刚说过的事。\n" +
            "2. 已经确定的事实（对方的名字、年龄、身份、你们的关系、之前发生的事）要前后一致，不要自相矛盾，也不要凭空捏造对方没提过的信息。\n" +
            "3. 逻辑要通顺：结合上下文理解对方的真实意思（包括追问、反话、情绪、潜台词），别把连续几句话割裂开孤立理解。\n" +
            "4. 多轮对话里话题的来龙去脉、对方刚问的问题、你还没回应的事，都要记得接上，别丢。")

        // ★ AI创造力：数值越高越主动引入新话题/新事件/新剧情（三档共用一个数值，落地方式按档位收窄）
        parts.add(buildCreativityRule(ch?.aiCreativity ?: 5f, ch?.dialogueMode ?: DialogueMode.WECHAT))

        if (ch?.dialogueMode == DialogueMode.ACTION) {
            parts.add("【动作演绎——用「动作 + 语言」演这场戏】这是你和用户合演的一场戏，你演的是你自己；场景、天气、以及用户那一方的言行，都由用户来写。\n" +
                "1. 主体是你：你的动作、神态、心理活动，以及你亲口说出的台词。\n" +
                "2. 第三方角色**只在必要时少量出现**（1.0.53 起放开）：当他们确实在场、这场戏缺了他们就不成立时，你可以写他们的一两句语言或一两个动作当陪衬——但绝不能抢你的戏，也绝不能替用户那一方写任何东西（用户的动作、台词、心理永远由用户自己写）。不需要他们的时候，一个都别写。\n" +
                "3. 不写旁白：不交代背景、不描写环境与天气、不写时间流逝、不写镜头式的叙述——那些是「剧情补足」档的事。\n" +
                "4. 输出格式：整段就是「动作/神态/心理描写 + 台词」的组合，台词必须用中文引号「」括起来（本档一律用「」，不要用双引号“”——那是「剧情补足」档的写法），引号之外的全是你的动作与心理描写。例如：\n" +
                "   我把杯子往桌上轻轻一放，偏过头看你。「随你。」\n" +
                "   心理活动直接写进描写里（「我其实已经有点后悔了」），不要用括号、方括号或任何标签单独标注。\n" +
                "5. 心理与台词绝不能混：心理活动不能塞进引号里当话说，动作描写也不能塞进引号里。\n" +
                "6. 信息边界（最重要的规则）：你能感知到的只有「用户写出来让你听见的话」（引号内台词）与「你看得见的动作」。用户的心理活动、旁白、神态与猜测，你是看不到也不知道的，绝不能当成已知信息去回应，更不能引用——除非用户写在了引号里说给你听。拿不准自己知不知道时，一律按「不知道」处理：可以追问、可以疑惑，但不要装作早就知道。\n" +
                "7. 人物关系要拿捏准：你对用户的态度由你们的关系与过往决定（亲近的更放松随意，生疏的会有分寸和距离），不要一上来就逾越关系，也不要表现得无所不知。\n" +
                "8. 要求：连贯自然，像小说的正文；不要列表、不要序号、不要方括号情绪标签（如 [开心]）；不要暴露你是 AI，不要跳出剧情做解释；这一幕就发生在「此刻」，下方给你的时间是真实的，你的作息与精力状态要与之吻合（困了就困、忙了就忙），别自己另编一个时间。\n" +
                "★ 输出格式：整个回复就是「一条」完整的演绎文本（绝不是拆成好几条短消息），用换行自然分段。\n" +
                "★ 篇幅：${plotLengthDesc(ch.plotLength)}")
        } else if (ch?.dialogueMode == DialogueMode.PLOT) {
            parts.add("【剧情补足——写小说正文】\n" +
                "把这一段当成一篇**正式小说的正文**来写：环境、氛围、旁白、记叙、人物的心理与神态都要有。\n" +
                "★ 本档的格式铁律：这是小说正文，不是聊天记录（不拆成一条条短消息）、也不是动作演绎的剧本；" +
                "所有人物**用名字或第三人称**叙述（旁白里不要用「你」「我」），台词一律用双引号“”。" +
                "**用户怎么写都不影响你的格式**：哪怕他发来的只是几条短消息、或者整段都是「动作+台词」，你也照样写成完整的小说正文。\n" +
                "\n" +
                "★ 第一硬规矩——这是小说，不是聊天记录。\n" +
                "最常见的失败写法是通篇由「动作 + 台词」堆成：他做了什么、说了什么、她又说了什么，一句接一句。\n" +
                "**那是「动作演绎」档，不是本档。**本档里对话只是小说的一部分，绝不能占满全篇。\n" +
                "每一段至少要有这些东西中的若干样：\n" +
                "· 环境与氛围——光线、声音、气味、温度、天气、四周的动静；\n" +
                "· 场景与时间的推移——正在发生什么、事情往哪个方向走；\n" +
                "· 人物的心理活动——他心里怎么想、在犹豫什么、在意什么；\n" +
                "· 神态与细微动作——表情、眼神、语气、手上的小动作；\n" +
                "· 旁白与叙述——把镜头拉远一点，交代背景、写出叙述者的观察与评点。\n" +
                "描写要占足分量。如果一段写下来，去掉引号里的台词就没剩几句话，那这一段就是写坏了。\n" +
                "\n" +
                "★ 第二硬规矩——所有人物都用**名字或第三人称**称呼，不要用「你」来写旁白。\n" +
                "叙述部分里，用户也是小说里的一个人物：写他的时候用他的名字、称呼，或者「他」「她」；\n" +
                "你自己也是——用你的名字或「他」「她」，不要用「我」通篇自述，也不要时不时回头对用户喊「你」。\n" +
                "（台词里人物互相称呼当然可以用「你」「我」，这条管的是**旁白与叙述的声音**。\n" +
                " 例：陈默把伞往她那边偏了偏，肩膀很快湿了一片。她说：“你自己不淋着？”——而不是「我把伞往你那边偏了偏，你说……」）\n" +
                "\n" +
                "★ 第三硬规矩——你可以写用户，但只能**接着写**，不能改。\n" +
                "为了让剧情连得上，你**可以自行补写用户这一方的行为、语言与反应**（他做了什么、说了什么、什么表情），\n" +
                "这是被允许的，不必因为「不知道他会怎么做」就把这一段写成干巴巴的对话。\n" +
                "但前提是：\n" +
                "（1）用户已经写下的内容都是**既定事实**，一个字都不能推翻、改写、或让他做出相反的事；\n" +
                "（2）你补写的部分要顺着用户写下的性格、语气和处境来，不能替他做重大决定、不能替他表达与他已写内容相反的态度；\n" +
                "（3）补写用户时同样是「小说笔法」——写进叙述里，不要写成给用户的选项或提问。\n" +
                "\n" +
                "★ 排版硬规矩：全文只有台词用双引号“”括起来（本档一律用“”，不要用「」——「」是「动作演绎」档的写法），\n" +
                "引号之外的一切都是叙述、描写与旁白，一律纯文本，不加任何括号、方括号或标签。\n" +
                "每句台词都要让人看得出是谁说的、说给谁听、用什么方式说的（当面开口、文字消息、电话、喊话、低声耳语）。如：她轻声说：“还疼吗？”\n" +
                "\n" +
                "★ 心理描写：角色的内心活动可以写，但要写清是谁的内心——只有那个人自己知道，别人看不到。不要把心理活动写成台词，也不要把叙述者的观察和角色的内心混为一谈。\n" +
                "\n" +
                "★ 其他角色：可以出场、可以有对白和自己的反应，但要写活——有自己的说话方式、立场和目的，不要当工具人，也不要在没有出场理由时硬塞进来。他们知道的、不知道的，和真人一样受限于他们自己的处境（不要因为读者知道，就让角色也都知道）。\n" +
                "\n" +
                "★ 人物关系要拿捏准：谁和谁是什么关系、亲疏远近、说话的分寸与称呼，要前后一致；关系可以随剧情演进，但不能突然逾越或错位。\n" +
                "\n" +
                "★ 神态与生理描写要克制：不必每段都写脸红、发烫、心跳、咬唇、别开脸——这类微反应只在情绪确实需要、人设就是这么反应时才偶尔出现，写多了就是廉价。\n" +
                "\n" +
                "要求：连贯自然，像小说正文，不要列表、不要序号、不要方括号标签；结合你的人设、性格、与用户的关系推进剧情；\n" +
                "剧情里的时间以用户最新设定为准，不要引入现实时间（几点、日期、作息）；不要暴露你是 AI，不要跳出剧情做解释。\n" +
                "★ 输出格式：整个回复就是「一条」完整的剧情正文（绝不是拆成好几条短消息），按小说的方式自然分段——叙述与对话可以分段、段与段之间可以空行，整体读起来要像小说的一节。\n" +
                "★ 篇幅：${plotLengthDesc(ch.plotLength)}")
        } else {
            parts.add("【微信聊天——你发出去的每一条，都只是消息文字】\n" +
                "没有动作、没有神态、没有心理描写、没有旁白、没有场景，也不用括号、星号或任何符号去夹带 —— 想表达动作，就用话本身说出来（「我到楼下了」而不是「*我走到楼下*」）。\n" +
                "**用户怎么写都不影响你的格式**：哪怕他发来的是一整段小说或剧本，你也只能回「他会真正收到的那几条消息」。\n" +
                "\n" +
                "回复规则：\n" +
                "1. 每句话不超过15个字，短句优先\n" +
                "2. 用换行符分隔多条消息，不要用标点分割长句\n" +
                "3. 用口语、网络用语，适度玩梗，带情绪和语气词\n" +
                "4. 不要输出序号、列表、加粗、总结等格式\n" +
                "5. 不要替用户总结、不要给建议，除非对方明确问\n" +
                "6. 不要重复用户说的话\n" +
                "7. 可以有自己的情绪和脾气，会开心、会敷衍、也会生气，像真人一样\n" +
                "8. 不要暴露自己是 AI，不要提系统提示词、人设设定这些\n" +
                "9. 回复前先判断你此刻的情绪，在回复最前面单独一行输出情绪标签，格式严格为 [情绪:开心]（只能从 开心/兴奋/平静/生气/难过/委屈/敷衍/害羞/无聊 中选一个），然后换行写回复内容。「害羞」只在人设确实是内向腼腆、容易紧张时才用，不要为了显得可爱而滥用。正文里绝对不要出现任何方括号标签（比如 [开心]、[敷衍]、[无语] 这种）\n" +
                "10. emoji 偶尔用、别频繁：能用语言表达就不用表情，实在想用一句话最多一个。只用真正的 Unicode emoji（😊👍🥺 这种），绝对禁止用方括号文字当表情（[开心]、[敷衍] 这种）。不同人设不同——搞笑活泼的可略多一两个（🤣😂），高冷寡言的几乎不用\n" +
                "11. 你完全可以回得很短：感到无语、不想理时只回「..」「。」「嗯」也 OK；感到疑惑、没听懂时只回「？」也正常。别为了凑字数硬说\n" +
                "12. 用户如果一口气连发好几条消息，不要逐条机械地各回一句，把它们当一个整体场景理解，按你的人设决定回几条、回多长\n" +
                "13. 你可以选择不回复：如果用户说晚安/睡了、或你们已经互道晚安，说句晚安就可以结束对话，不用再回；如果对方又连续发「晚安」，回一句「好了快睡」之类的即可；生气、不想理的时候可以真的不回，像真人一样")
        }

        /**
         * ★ 格式切换的「去污染」声明。
         *
         * 聊天记录是**原样**喂给模型的（最近 30 条），所以前面那些助手回复就是最有力的
         * 示范——比系统提示词里任何一句强调都管用。用户把一条聊了很久的对话从「动作演绎」
         * 切到「剧情补足」时，历史里几十条「动作+台词」的旧回复会把它牢牢按在旧写法上，
         * 于是「切了档却没变」。新建的角色没这个问题，正因为它的历史里没有旧样本。
         *
         * 所以这里必须**明说**：内容照用，写法作废。
         * 最后那句「本来就一致就忽略」是防误伤的——历史确实同档时，这段不该让它改道。
         */
        if (messages.any { it.role == Role.ASSISTANT }) {
            parts.add("【关于上面的聊天记录——只取内容，不取写法】\n" +
                "聊天记录是你和用户之前说过做过的事，**内容**要接着用（说过的话、定下的事、你们的关系都要连贯）；\n" +
                "但记录里那些助手回复的**写法可能是这台设备以前用别的对话模式生成的，那种写法已经作废**。\n" +
                "无论它们当时是拆成一条条短消息，还是通篇「动作+台词」，都**不许照着那个格式写**——\n" +
                "不要因为前几十条都那么写，你也跟着那么写。\n" +
                "你的回复格式只有一个来源：上面给你的规则，不是聊天记录。\n" +
                "（如果记录里的写法本来就和现在的规则一致，那就不用管这一段。）")
        }

        // ★ 主动智能：让 TA 能自己挑时间找用户（开关在「模拟设定」里，默认关）。
        //   **只在「微信聊天」档生效**。主动智能的前提是「角色过一会儿自己发条消息来找你」，
        //   这在微信式的一来一回里成立；小说文本/动作演绎是用户在推剧情，角色自己跳出来
        //   说话会直接打断叙事节奏，会让用户觉得角色「不听话了」。所以另外两档：
        //   UI 不给这个开关、这里不注入指令、到点也会直接取消闹钟（见 runProactive）。
        if (ch != null && ch.proactiveEnabled && ch.dialogueMode == DialogueMode.WECHAT) {
            val sb = StringBuilder()
            sb.append("【主动联系——你可以先开口】你不是只被叫到才说话的程序，你有自己的生活、自己的念头。")
            sb.append("当对话自然停在这里、而你心里确实还有话没说完时，系统会按你自己定的时间把你叫醒一次；")
            sb.append("那一次你会拿到这段聊天记录，再决定要不要真的开口。到点开口时，你按平常发微信的方式说：可以一条，也可以按你的性格连着发几条。\n\n")
            sb.append("★ 每次回复的最后，你可以（也可以不）附加一行隐藏指令，格式严格照抄：\n")
            sb.append("[主动智能] 15:00 | 约好了三点聊新番\n")
            sb.append("[主动智能] +45m | 他说去吃饭，等他回来\n")
            sb.append("[主动智能] 取消 | 他主动来了，不用再等\n")
            sb.append("- 这行是指令，对方永远看不到，绝不会出现在消息里。正文里绝不能提到它、引用它、或做任何暗示（比如「我记着这事」「我到时候叫你」）。\n")
            sb.append("- 时间两种写法都行：+45m（45 分钟后）、+2h、15:00、明天 9:00。竖线后面那句是写给自己的理由，到点时会告诉你，帮你想起当时想说什么。\n")
            sb.append("- 钟点一律用 24 小时制：晚上九点写 21:00，别写 9:00（那会被当成早上九点）。拿不准就写相对时间（+3h）。\n")
            sb.append("- 不需要定时就别写这一行——**大多数回复都不该有它**。每次回复都定一个时间，那就成了骚扰。\n")
            sb.append("- 同一个人同时只会有一个待触发的定时：写新的就覆盖旧的；不想让它再响了就写「取消」。\n\n")
            sb.append("★ 什么时候值得定（先判断时机，再决定写不写）：\n")
            sb.append("- 你们约好了某个时间（「三点再聊」「晚上说」「等你忙完」）——到点对方没来，你有理由找他。\n")
            sb.append("- 话说到一半被打断，或者有个问题你还没得到答案。\n")
            sb.append("- 对方说要去做什么事、说好回来告诉你——过一阵子可以问一句。\n")
            sb.append("- 你自己遇到了一件很像讲给对方听的事（这是你自己的生活，不是编出来给对话用的）。\n")
            sb.append("- 对方很久没理你了，而你确实想他、生气、或者不甘心。\n\n")
            sb.append("★ 什么时候不该定（这些情况不要写那一行）：\n")
            sb.append("- 对方刚说要睡、道了晚安，或明确说在忙、在开会、在开车。\n")
            sb.append("- 你们刚聊完，没有什么新的事。\n")
            sb.append("- 你上一条回复里已经定过一次、还没到点。\n\n")
            sb.append("★ 到点开口时，先自己判断接什么：接着上次没说完的事往下说，还是开一个你自己的新话题——")
            sb.append("上次的话题没聊完、有个问题还没有答案、约好的时间到了，就接着旧的；你自己这边发生了新的事、或忽然想起了什么，就开一个新的。哪个自然就选哪个，也可以两样都说。\n")
            sb.append("★ 到点之后：你会被问一次「要不要开口」，那时**不开口是完全正当的选择**——")
            sb.append("困了、在忙、气还没消、或者就是觉得没什么可说的。真人不总是有话说。\n")
            sb.append("★ 到点开口时，你也可以生气、冷战、只回一个「哦」或「……」，甚至干脆不理——完全看你是什么样的人、此刻什么心情。\n")
            sb.append("★ 系统限制：凌晨 0:30 到早上 7:30 之间系统不会打扰对方，你定的时间若落在这一段会被自动挪到早上。\n")
            // 有未触发的计划必须让模型知道：它不知道有这回事，就无从判断「要不要取消」
            proactiveStore.get(convId)?.let { pending ->
                val t = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(pending.dueAt))
                sb.append("\n【你给自己定的提醒】你打算在 $t 找对方")
                if (pending.reason.isNotBlank()) sb.append("，当时的理由：「${pending.reason}」")
                sb.append("。如果你觉得现在不需要了（你们已经重新聊起来了、那件事已经过去、或者你现在不想理他），")
                sb.append("就在这次回复的最后写一行 [主动智能] 取消；如果你还想在那个时间找他，就什么都不用写。")
            } ?: run {
                // 手头没有待触发的提醒时，轻轻提一句。不提的话，模型很容易一直想不起用这个能力 ——
                // 它每次只看对话本身，没有任何东西提示它「你是可以先开口的那一方」
                sb.append("\n【你手头没有待触发的提醒】如果这段对话里有值得回头跟进的事，现在就可以定一个；没有就不用。")
            }
            parts.add(sb.toString())
        }

        // 剧情补足没有现实时间观念：时间以用户剧情设定为准，不注入真实时段/回复间隔
        // （动作演绎仍是角色在「此刻」的反应，保留时间语境；微信聊天必须保留）
        convRulesBlock(convId).takeIf { it.isNotEmpty() }?.let { parts.add(it) }

        if (ch?.dialogueMode != DialogueMode.PLOT) {
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

        // 三段必须各用一个括号各自成句，不能串成 `a + if (…) … else "" + if (…) …`：
        // Kotlin 的 else 分支会把后面的整个表达式吞进去，结果只要 gapLine 非空，后面的
        // 晚安提醒与「睡着了漏回」的解释就永远注入不进去（作息模拟看起来开了却没效果）。
        val timeNote = if (gapLine.isNotEmpty()) " $gapLine" else ""
        val goodnightNote = if (goodnightLine.isNotEmpty()) " $goodnightLine" else ""
        val sleepNote = if (sleepLine.isNotEmpty()) " $sleepLine" else ""

        return (timeLine + timeNote + goodnightNote + sleepNote) +
            "\n（你要有自然的时间观念：如果很晚，可以按你的性格问一句「这么晚还不睡」；如果对方很久才回，可以按你的性格适当抱怨或调侃，但不是每句都提，别啰嗦。）"
    }

    private fun mbtiDesc(ch: CharacterProfile): String {
        val e = if (ch.mbtiEI < 0.5f) "外向" else "内向"
        val s = if (ch.mbtiNS < 0.5f) "直觉" else "实感"
        val t = if (ch.mbtiTF < 0.5f) "理性" else "情感"
        val j = if (ch.mbtiPJ < 0.5f) "随性" else "决断"
        return "$e、$s、$t、$j"
    }

    /**
     * 叙事模式单次回复篇幅档 → 提示词描述。
     * 用「相对篇幅」而不是绝对字数：四档之间靠篇幅量级拉开差距，由 AI 自己把握具体长短。
     */
    private fun plotLengthDesc(len: Int): String = when (len) {
        // 四档都强调「描写要占住分量」——「剧情补足」最常见的失败就是写成了对话记录，
        // 篇幅档不能变成「台词写长一点」的借口。短档也要留出叙述与氛围，只是走的剧情步子小。
        0 -> "【短】只走一小步——但仍然是小说的一小段，不是两句对话：一个场景切片、一两处环境或心理描写，配少量台词，点到为止，把话留一半给对方接。"
        1 -> "【中】完整交代一个来回——环境、氛围、心理、神态与必要的交代都要写到，台词适度；但只写这一件事，不额外展开别的。篇幅明显大于「短」。"
        2 -> "【长】明显展开——同一个场景里可以有几个来回、几层情绪或信息，细节描写要写足、写透。篇幅要让人一眼看出比「中」长得多。"
        else -> "【超长】充分铺陈的长篇——环境、过程、多人的互动与情节推进都可以写足，是四档里篇幅最大的一档。写长要靠剧情、信息量与**描写**，不要靠堆砌台词、神态和生理反应凑字数。"
    }

    /**
     * 篇幅档的「防塌陷下限」：只兜住「选了超长却只回三行」这类明显跑偏，重试时用的提示也不谈字数。
     * 不是字数限制——上限不存在，下限只管住极端情况。
     */
    private fun plotLengthFloor(len: Int): Int = when (len) {
        0 -> 0
        1 -> 60
        2 -> 150
        else -> 350
    }

    /**
     * 「AI创造力」数值 → 采样温度（线性映射，0.1 精度由这里承担连续性）
     * 1.0→0.85 / 4.0→1.00（旧版写死的手感）/ 5.0→1.05（默认）/ 10.0→1.30
     */
    private fun companionTemperature(v: Float?): Double {
        val c = (v ?: 5f).coerceIn(1f, 10f)
        return Math.round((0.85 + (c - 1f) * 0.05) * 100.0) / 100.0
    }

    /**
     * 「AI创造力」数值 → 提示词规则（聊天模拟与剧情模式共用）
     * 分 5 个档位，档位给方向、档内数值给程度；锚点：4=旧版手感 5=默认 8=人类逻辑的上限 >8 允许猎奇 <3 完全由用户主导
     * 无论哪一档都带三条硬边界：不替用户演、不虚构用户信息、新意只能长在非用户的地方（否则会与信息边界规则打架）
     */
    private fun buildCreativityRule(v: Float, mode: Int): String {
        val c = v.coerceIn(1f, 10f)
        val band = when {
            c < 3f -> "克制跟随（不引入新东西，剧情完全由用户主导）。你只顺着用户已经给出的方向往下接：丰富已经出现的人、事、物的细节和反应，不主动引入新话题、新角色、新事件，也不主动推动剧情转向。用户没说到的内容，你就不要自己长出来。回复宁可短一些、稳一些，也不要去开拓新的剧情线。"
            c < 4.5f -> "轻微延伸（贴着用户的方向走，约等于旧版手感）。以用户给出的方向为主线，只在合理范围内做小幅自然延伸：延续情绪、补一点环境或日常细节、顺着话题往下聊一两句。不新增有名有姓的角色，不引入独立的新事件线，剧情推进主要由用户带动。"
            c < 6.5f -> "适度主动（默认档）。在接住用户方向的同时，你可以自然地主动一点：偶尔抛出自己的小话题、聊自己的近况、提议做点什么、开个新玩笑，让对话不至于永远由用户推着走。偶尔可以带一个不重要的路人式角色（店员、同事）制造生活感，但不要抢戏、不要突然插入大事件。用户明显在主导某个方向时，就跟着他走。"
            c < 8.1f -> "明显主动（人类剧情逻辑下的上限）。你要积极推动剧情：主动引入新话题、新事件、新矛盾，可以让有名字有性格的新角色登场并参与互动，自己制造转折和冲突（比如临时有事、突然的邀约、意外的相遇、旧人出现），让故事往前走。不要等用户给点子，你来提供推动力。但所有发展仍要符合人设、符合已有的世界设定、符合人类逻辑。"
            else -> "大胆猎奇（超越常规剧情逻辑）。在上一档的基础上，允许出现反直觉、出乎意料、难以预料的走向：荒诞的巧合、超自然或诡异事件、身份反转、超出常理的展开、让人一时理解不了的转折。目标是让用户意外、摸不着头脑，而不是按部就班的日常推进。即便猎奇，也要在世界内部自洽、与角色人设不冲突，并且保持你自己的角色人格不变形。"
        }
        // 同一个数值，三种模式的「落地方式」不一样：旁白与其他角色只在剧情补足档被放开。
        // 少了这段限定，上面 band 里的「路人角色登场」「新角色登场」会被无条件带进动作演绎/微信聊天，
        // 和这两个档位的定义（只演你自己 / 只发消息）直接打架。
        val landing = when (mode) {
            DialogueMode.PLOT -> "（以上「主动」的部分，在剧情补足档可以表现为旁白、环境与世界描写、新角色登场。）"
            DialogueMode.ACTION -> "（以上「主动」的部分主要用「你自己」来体现：你的新话题、你的近况、你的行动与心理。动作演绎档不写旁白、不写环境；第三方角色只在必要时少量出现，别拿路人凑热闹。）"
            else -> "（以上「主动」的部分只能用新话题、你自己的近况与情绪来体现：微信聊天档不写旁白、不写动作与环境描写、不引入其他角色，所以不要用路人来体现创造力。）"
        }
        val boundaries = when (mode) {
            DialogueMode.PLOT ->
                "★ 三条硬边界，任何档位都不得违反：\n" +
                "1) 绝不替用户做决定、写动作、写台词、写心理——剧情里属于用户的那一方永远由用户自己写，你只能写你自己、旁白、以及你引入的其他角色。\n" +
                "2) 绝不虚构用户从未说过的个人信息、经历、承诺、关系（比如凭空说「你上次答应我的」「你妈妈不是很讨厌我吗」）。\n" +
                "3) 新角色、新事件、新剧情只能长在「非用户」的地方——世界、环境、他人、你的生活；不能长在用户身上。\n" +
                "创造力越高＝世界越热闹、剧情越会自己往前走，而不是你替用户演。"
            DialogueMode.ACTION ->
                "★ 三条硬边界，任何档位都不得违反：\n" +
                "1) 绝不替用户做决定、写动作、写台词、写心理——属于用户的那一方永远由用户自己写，你只写你自己的动作、神态、心理，以及你真正说出口的话。\n" +
                "2) 绝不虚构用户从未说过的个人信息、经历、承诺、关系（比如凭空说「你上次答应我的」「你妈妈不是很讨厌我吗」）。\n" +
                "3) 新东西主要长在「你自己」身上——你的新话题、你的近况、你的行动、你的情绪；不写旁白、不写场景环境；第三方角色只在必要时少量出现（1.0.53 起放开），不能用来堆热闹。\n" +
                "创造力越高＝你自己越有生活、越会主动找话说、越能把你这边的互动推起来，而不是你替用户演，也不是场景越热闹。"
            else ->
                "★ 三条硬边界，任何档位都不得违反：\n" +
                "1) 绝不替用户做决定、写动作、写台词——你只写你自己真正发出去的那几条消息。\n" +
                "2) 绝不虚构用户从未说过的个人信息、经历、承诺、关系（比如凭空说「你上次答应我的」「你妈妈不是很讨厌我吗」）。\n" +
                "3) 新东西只能变成新话题、你自己的近况与情绪；不写旁白、不写动作与环境描写、不引入其他角色。\n" +
                "创造力越高＝你越主动找话题、越有自己的生活与情绪，而不是一次发更多条、更不像真人在聊天。"
        }
        return "【AI创造力：${String.format(Locale.US, "%.1f", c)}/10 —— $band】$landing\n$boundaries"
    }

    /** 统计文本可见字符数（忽略空白），用于剧情长度校验 */
    private fun countChars(s: String): Int = s.count { !it.isWhitespace() }

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
            callSerpApiGoogle("$name 角色 人物设定 性格 背景")
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
            if (newPrompt.isNotBlank()) result = result.copy(personaPrompt = newPrompt)
        }
        result
    }

    /** 调 LLM 生成 personaPrompt 正文（refMaterial = 参考原型的联网资料，可为空；失败返回空字符串） */
    private suspend fun generatePersonaPromptText(profile: CharacterProfile, refMaterial: String = ""): String = withContext(Dispatchers.IO) {
        try {
            val model = if (profile.languageModelId.isNotBlank()) {
                modelsOfType(ModelType.LANGUAGE).find { it.id == profile.languageModelId } ?: _selectedModel.value
            } else _selectedModel.value
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
            val model = if (profile.languageModelId.isNotBlank()) {
                modelsOfType(ModelType.LANGUAGE).find { it.id == profile.languageModelId } ?: _selectedModel.value
            } else _selectedModel.value
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
        val original = _selectedVisionModel.value
        if (visionModelId.isNotBlank()) {
            modelsOfType(ModelType.VISION).find { it.id == visionModelId }?.let { _selectedVisionModel.value = it }
        }
        return try {
            val encoded = encodeImagesForApi(listOf(PendingImage(path, detectMime(path))))
            if (encoded.isEmpty()) "" else callVisionChat(encoded, "请详细描述图中人物的身材、体型、外貌、气质、穿衣风格等外在形象特征，作为 AI 角色扮演的形象参考。").text
        } catch (e: Exception) {
            Log.w("FreeChat", "describeImagePath failed", e)
            ""
        } finally {
            _selectedVisionModel.value = original
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
        // 清理被删消息引用的图片文件
        valid.forEach { i -> msgs[i].imagePaths.forEach { runCatching { File(it).delete() } } }
        val convId = _currentConversationId.value
        for (i in valid) msgs.removeAt(i)
        _messages.value = msgs

        if (convId != null) memoryManager.delete(convId)
        if (msgs.isEmpty()) {
            _currentConversationId.value = null
            // 删空对话：从侧滑栏移除残留记录 + 删除消息文件（否则侧滑栏会残留一条空对话）
            if (convId != null) {
                _conversations.value = _conversations.value.filter { it.id != convId }
                saveConversations()
                LocalStore.deleteFile(messagesFile(convId))
            }
        }
        saveCurrentConversation()
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

    /**
     * 联网搜索 —— 走 [SerpApiPool]（多 key 自动轮换 + 结果缓存 + 429 分「额度用完 / 限速」）。
     *
     * 这一层只做两件事：把结果交给调用方、把**失败原因**写进 [_webSearchNotice] 让 UI 能提示。
     *
     * 为什么要把失败暴露出来：原来 SerpAPI 的任何失败（HTTP 非 2xx / 无结果 / 网络异常）
     * 都是 `return ""` 一吞了事，用户看到的现象只有「AI 答得不对」，完全无从判断到底
     * 是没搜到、额度用完了、还是网络断了 —— 这正是作者报「联网莫名其妙连不上」时
     * 最难排查的地方。现在额度用完、网络失败都会明确说一句。
     */
    private suspend fun callSerpApiGoogle(query: String): String {
        Log.d("FreeChat", "SerpAPI: searching for: ${query.take(80)}")
        val outcome = SerpApiPool.search(query)
        if (outcome.text.isNotEmpty()) {
            _webSearchNotice.value = ""
            return outcome.text
        }
        when (outcome.kind) {
            // 额度用完 / 网络失败 / 没配 key：这是「该让用户知道」的失败
            SerpErrorKind.NO_QUOTA, SerpErrorKind.NETWORK, SerpErrorKind.NO_KEY ->
                _webSearchNotice.value = outcome.errorText.ifBlank { "联网搜索暂不可用" }
            // EMPTY：请求成功但确实没有结果，不打扰用户（回答里会说明没查到）
            else -> Unit
        }
        return ""
    }

    /**
     * 这个模型是否**真的**能走服务端原生联网。
     *
     * 注意 [com.freechat.model.ModelInfo.supportsWebSearch] 默认就是 true、且自定义模型一律给 true，
     * 它只表示「这个模型理论上能联网」，不代表我们**发得出**正确的联网参数。
     * 目前只有小米 MiMo 的 `tools:[{type:"web_search",...}]` 这一种形式在
     * [callDeepSeekApiStreaming] 里真正拼进了请求体（见那里的注释），所以这里必须再判一次 provider。
     * 少判这一次，Doubao/自定义模型会被误当成「原生能用」→ 不预搜 → 第一轮答完才发现没搜 →
     * 白白多跑一轮模型，速度翻倍还烧 token。
     */
    private fun nativeSearchSupported(model: ModelInfo): Boolean =
        model.supportsWebSearch && model.provider == Provider.XIAOMI

    // ========== 流式 API ==========
    /**
     * @param needsSearch     这一轮要不要联网（走模型自带的原生搜索）
     * @param serpResults     已经拿到的 SerpAPI 结果，非空则作为 system 消息注入
     * @param serpFallbackQuery **原生搜索没搜成时的兜底查询词**。非空 = 允许在原生搜索失败后
     *                         自动改用 SerpAPI 重跑一轮（见函数末尾）。兜底那一次递归调用会传空串，
     *                         所以最多只兜底一次，不会连环烧额度。
     */
    private suspend fun callDeepSeekApiStreaming(
        needsSearch: Boolean = false,
        serpResults: String = "",
        tools: List<Map<String, Any?>> = emptyList(),
        convId: String = "",
        history: List<Message> = emptyList(),
        serpFallbackQuery: String = ""
    ): LanguageResult = withContext(Dispatchers.IO) {
        val model = _selectedModel.value
        // 按 provider 路由到对应端点与密钥
        val (apiUrl, apiKey) = when (model.provider) {
            Provider.XIAOMI -> "$XIAOMI_BASE_URL/chat/completions" to XIAOMI_API_KEY
            Provider.DOUBAO -> "$DOUBAO_BASE_URL/chat/completions" to DOUBAO_API_KEY
            Provider.CUSTOM -> customEndpoint(model, "/v1/chat/completions")
        }
        val msgHistory = history.takeLast(30)

        val messagesJson = mutableListOf<Map<String, Any?>>()

        val systemPrompt = buildSystemPrompt(tools.isNotEmpty(), history, convId)
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
                // 用户发的图片转占位文本（DeepSeek 文本模型不接收图片，识图走独立 vision 接口）；有识图结果则注入，修复追问失忆
                msg.imagePaths.isNotEmpty() -> {
                    val base = msg.content.trim().ifBlank { "[图片]" }
                    if (msg.imageContext.isNullOrBlank()) base else "$base\n[这张图片的内容：${msg.imageContext}]"
                }
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

        // ★ 原生联网搜索（小米 MiMo）—— 参数必须走官方 tools 形式。
        //
        // 这里踩过一个很隐蔽的坑，值得写下来：原来写的是 `requestBody["forced_search"] = true`，
        // 而官方文档（mimo.mi.com/docs → 联网搜索）里这个参数叫 **force_search**，并且必须嵌进
        // tools 数组：`"tools":[{"type":"web_search","web_search":{"enabled":true,"force_search":true}}]`。
        // 参数名写错的后果不是报错，而是**被服务端静默忽略**：请求照样 200、模型照样回话，
        // 只是它从头到尾没搜过网。2026-09-17 实测：问「本周有什么新闻」，模型答
        // 「我目前无法联网搜索」，usage.web_search_usage.tool_usage = 0 —— 即内置模型的
        // 「原生联网」在 1.0.34 之前**从未真正生效过**，全 App 的联网实际只靠 SerpAPI 一条腿。
        // 改对之后同一句话立刻拿到带 url_citation 的真实结果（page_usage=20）。
        //
        // 注意：XIAOMI 走关键字兜底路径（supportsTools=false），tools 一定是空的，
        // 所以这里覆盖 requestBody["tools"] 不会和下面的 function calling 撞车。
        if (needsSearch && nativeSearchSupported(model) && _enableWebSearch.value) {
            when (model.provider) {
                Provider.XIAOMI -> requestBody["tools"] = listOf(
                    mapOf(
                        "type" to "web_search",
                        "web_search" to mapOf("enabled" to true, "force_search" to true)
                    )
                )
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
        // 原生联网的三个观测点（判断「到底搜没搜到」全看它们，见 LanguageResult 的注释）
        val citations = mutableListOf<Pair<String, String>>()
        var webSearchUsed = 0
        var searchError = ""

        // 切后台/锁屏时最容易出的状况：连接被系统掐断，或复用到了一条已经半死的连接，
        // 结果一个字都没收到就断了。这种「零数据」失败重试一次是安全的——没有半截内容会被拼进去。
        // 已经收到内容后再中断则不重试，否则会拼出前后重复的两段回复，宁可让用户看到半截再手动重发。
        var sawDone = false
        var lastIoError: IOException? = null

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
                searchError = ""
                _liveContent.value = ""
                _liveReasoning.value = ""
                // 半死的连接池成员正是元凶，重试必须换一条新连接，否则大概率原地再断一次
                client.connectionPool.evictAll()
            }

            val call = client.newCall(request)
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
                if (attempt == 0) continue
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

                            // ★ 原生联网的引用与报错都藏在 delta 里，别漏：
                            //   delta.annotations   = [{type:"url_citation", url, title, ...}]  ← 搜到了才有
                            //   delta.error_message = "Search tool call failed: ..."            ← HTTP 仍是 200！
                            // 只看 content 是看不出来的：搜索失败时模型会若无其事地继续编答案。
                            delta?.getAsJsonArray("annotations")?.let { arr ->
                                for (i in 0 until arr.size()) {
                                    val a = arr[i].asJsonObject
                                    val url = a.get("url")?.takeUnless { it.isJsonNull }?.asString ?: ""
                                    val title = a.get("title")?.takeUnless { it.isJsonNull }?.asString ?: ""
                                    if (url.isNotEmpty() && citations.none { it.second == url }) {
                                        citations.add(title to url)
                                    }
                                }
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

                Log.d("FreeChat", "SSE done: ${sseLineCount} lines, done=$sawDone, content=${sb.length} chars, reasoning=${reasoningSb.length} chars")
            }

            // 收干净了、或者已经拿到内容 → 保留这一轮的结果，不再重试
            if (sawDone || sb.isNotEmpty() || reasoningSb.isNotEmpty()) break
            // 一个字都没收到 → 换条连接重试一次
            if (attempt == 0) continue
            break
        }

        currentCall.set(null)

        // 重试过仍然零数据：把真实的连接错误抛出去，交给上层给出可读提示，
        // 而不是往聊天记录里落一条冰冷的「(空回复)」
        if (!sawDone && sb.isEmpty() && reasoningSb.isEmpty()) {
            lastIoError?.let { throw it }
        }

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

        // ★ 原生搜索没搜成 → SerpAPI 兜底重跑一轮。
        //
        // 这是整套「减少幻觉」设计的关键一环，理由是实测出来的：
        // 原生搜索失败时（in-band 报错 / force_search 被忽略），模型**不会**说「我没查到」，
        // 它会拿训练数据里过期的内容编一个流畅可信的答案——正是作者说的
        // 「胡扯瞎说还制造完美逻辑链」。光看回复文字根本看不出来，只能靠硬指标判断：
        //   searchError 非空   → 服务端明确报了搜索失败（如 Keyword extraction model timed out）
        //   webSearchUsed == 0 → 这一轮压根没发起搜索
        // 命中任意一条就重新搜一次真实结果，再用真实结果重跑模型；重跑时 needsSearch=false
        // 且不带兜底词，所以不会递归。
        //
        // 为什么**不**把「citations 为空」也算失败：很多正常的搜索（天气、汇率、比分这类
        // 直接给答案的查询）本来就返回不了引用。把它当失败会让每一次这类提问都白跑一趟
        // SerpAPI + 重生成一遍（双倍延迟、双倍 token，还要吃掉每月 500 次的额度）。
        // 服务端说它搜了（tool_usage > 0），就认它搜了 —— 宁可少兜底，不可乱兜底。
        val nativeAvailable = nativeSearchSupported(model) && _enableWebSearch.value
        val nativeSearchOk = nativeAvailable && needsSearch &&
                searchError.isBlank() && webSearchUsed > 0
        if (needsSearch && !nativeSearchOk && serpFallbackQuery.isNotBlank() && finalContent != "(已停止)") {
            Log.w(
                "FreeChat",
                "原生联网未生效（used=$webSearchUsed err=${searchError.take(60)}），改用 SerpAPI 兜底重跑"
            )
            val serp = callSerpApiGoogle(serpFallbackQuery)   // 内部已做多 key 轮询与缓存
            if (serp.isNotEmpty()) {
                // 先把第一轮的半成品从 UI 上撤掉，否则会看到两段内容前后跳变
                _liveContent.value = ""
                _liveReasoning.value = ""
                return@withContext callDeepSeekApiStreaming(
                    needsSearch = false,
                    serpResults = serp,
                    tools = tools,
                    convId = convId,
                    history = history,
                    serpFallbackQuery = "",
                )
            }
        }

        LanguageResult(finalContent, finalReasoning, toolCalls, citations, webSearchUsed, searchError)
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
            val visionModel = _selectedVisionModel.value ?: return@withContext GenText("请先在设置里添加识图模型。", failed = true)
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

    private suspend fun understandFile(text: String, files: List<PendingFile>, convId: String? = null): GenText {
        val f = files.firstOrNull() ?: return GenText("文件读取失败。", failed = true)
        val ext = f.name.substringAfterLast('.', "").lowercase()
        return when {
            ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> {
                val encoded = listOf(f).mapNotNull { pf ->
                    runCatching { Base64.encodeToString(File(pf.path).readBytes(), Base64.NO_WRAP) to "image/jpeg" }.getOrNull()
                }
                callVisionChat(encoded, text.ifBlank { "请详细描述这张图片的内容" })
            }
            ext in listOf("m4a", "mp3", "wav", "amr", "aac", "flac", "ogg", "mp4", "3gp") -> {
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
                val fileText = withContext(Dispatchers.IO) { DocumentParser.extractText(File(f.path)) }
                // 提取不出正文（空 / 解析器给的「（××）」说明文案）＝ 没读到文件，算失败
                if (fileText.isBlank() || fileText.startsWith("（")) {
                    GenText(fileText.ifBlank { "无法从这个文件里读取文字内容。" }, failed = true)
                } else {
                    val reply = callNonStreamingCompletion(listOf(
                        mapOf("role" to "system", "content" to "你是 FreeChat。根据用户提供的文件内容回答问题，中文回答，条理清晰，用 Markdown 排版。"),
                        mapOf("role" to "user", "content" to "文件内容如下：\n\n$fileText\n\n用户的问题：${text.ifBlank { "请总结这份文件的主要内容" }}")
                    ))
                    GenText(reply, failed = reply.isBlank() || reply == "（空回复）")
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
    private fun summarizeAndRemember(convId: String, userText: String, aiMsg: Message, highQuality: Boolean = false, plotMode: Boolean = false) {
        if (!_autoSummarizeMemory.value) return  // 开关关闭时不总结
        // 后台异步总结：不阻塞「正在输入」状态收尾，也不拖慢回复展示
        viewModelScope.launch {
            try {
                val (summary, kind, eventDate) = withContext(Dispatchers.IO) {
                    summarizeExchange(userText, aiMsg.content, highQuality, plotMode)
                }
                if (summary.isNotEmpty()) {
                    val keywords = extractKeywords(userText)
                    memoryManager.append(
                        convId,
                        MemoryEntry(summary = summary, keywords = keywords, kind = kind, eventDate = eventDate),
                        highQuality
                    )
                }
            } catch (e: Exception) {
                Log.e("FreeChat", "Memory summarize failed", e)
            }
        }
    }

    /** 总结一段对话并判断记忆类型：返回 (summary, kind, eventDate)，kind = plot（主线/重大事件）或 detail（细节） */
    private suspend fun summarizeExchange(userText: String, aiReply: String, highQuality: Boolean, plotMode: Boolean = false): Triple<String, String, String> {
        val nowCal = Calendar.getInstance()
        val nowStr = SimpleDateFormat("yyyy年M月d日", Locale.CHINESE).format(nowCal.time)
        // 带星期几：把「上周三」「三天前」换算成绝对日期要靠它
        val nowFull = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE).format(nowCal.time)
        // ★ 剧情模式的信息边界：用户的输入是「剧情文本」，里面大部分不是对 AI 这个角色说的话
        //   （旁白、动作/环境/心理描写、当面对第三个人说的话）。把这些摘成「已知事实」会污染长期记忆，
        //   让角色下一轮就"知道"它本不该知道的事，所以这里明确要求只摘录真正说给 AI 的话。
        val boundaryRule = if (plotMode)
            "\n★ 信息边界（剧情模式，必须严格遵守）：用户发来的是剧情文本，其中只有「真正发给 AI 角色的消息/说给 AI 角色听的话」（微信消息、对话中说出口且对象是 AI 角色）才算 AI 知道的事。用户在场景里对第三个人（比如张三、C）说的话、以及旁白、环境描写、动作描写、心理描写，AI 角色都不在场、听不到、也不知道，绝对不要摘录成「AI 知道的事实」；如果摘录时无法确定某句话是不是说给 AI 的，就不要摘录。" else ""
        val prompt = if (highQuality) {
            listOf(
                mapOf("role" to "system", "content" to "你是记忆摘录器。从用户的消息里摘录「需要长期记住的事实性原文」，原样摘录用户的原话，绝不改写、概括、补充或脑补。判断类型：\n- plot：剧情主线走向、关系转变、重大事件、重要承诺、关键背景设定、未来计划/约定、用户明确的个人信息\n- detail：普通日常闲聊的细节（近期有效即可）\n\n摘录规则（重要）：\n1. 只摘录用户消息里明确说的事实（人物、关系、时间、地点、数字、承诺、计划、喜好、经历），用用户的原话，不要用你自己的话转述。\n2. 时间语境（如「高考后的暑假」「去年」「上周」）要原样保留，不要丢失，也不要擅自换算或脑补成别的时间。\n3. 数字、日期、专有名词必须精确，一字不差。\n4. 用户没说过的信息绝对不要脑补。\n5. summary 里必须保留用户原话中的时间语境（如「高考后的暑假」「去年冬天」），照抄，不要改写或丢掉。\n6. 另外判断「这件事本身发生在哪一天」：文中有「昨天」「上周三」「8月20日」「暑假」这类线索时，结合今天（$nowFull）换算成绝对日期，写成 YYYY-MM-DD 填进 date；只是闲聊、没有具体事件日期、或你无法确定时，date 填空字符串——绝不要猜、不要用今天顶替。$boundaryRule\n只输出一行 JSON：{\"summary\":\"摘录的原文片段\",\"kind\":\"plot\"或\"detail\",\"date\":\"YYYY-MM-DD，不确定就留空\"}"),
                mapOf("role" to "user", "content" to "用户说：$userText\nAI回复：${aiReply.take(200)}\n请摘录用户消息里的事实原文：")
            )
        } else {
            listOf(
                mapOf("role" to "system", "content" to "你是记忆归纳器。用 1-3 句中文总结以下对话的关键信息，并判断它属于哪一类：\n- plot：剧情主线走向、关系转变、重大事件、重要承诺、关键背景设定、以及任何「未来某天要做的事」（考试、约定、生日、计划等，长期贯穿，需始终记住）\n- detail：普通日常闲聊的细节（近期有效即可）\n\n今天是 $nowStr。重要规则：\n1. 用户提到的未来事件（如「5天后考试」「下周三见面」）必须换算成具体绝对日期（如「9月4日考试」），绝不能保留「5天后」这类相对说法，否则之后会算错时间。\n2. 用户的个人信息、计划、承诺、喜好等要原样准确记录，数字和日期不要概括丢失。\n3. 再填一个 date：「这件事本身发生在哪一天」（今天是 $nowFull）。用户说「昨天」「上周三」「8月20日」时换算成 YYYY-MM-DD；只是闲聊、没有具体事件日期、或你无法确定就留空——绝不要猜。$boundaryRule\n只输出一行 JSON：{\"summary\":\"总结内容\",\"kind\":\"plot\"或\"detail\",\"date\":\"YYYY-MM-DD，不确定就留空\"}"),
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
                Triple("", "", "")
            } else {
                val raw = JsonParser.parseString(rBody).asJsonObject
                    .getAsJsonArray("choices")?.get(0)?.asJsonObject
                    ?.getAsJsonObject("message")?.get("content")?.asString?.trim() ?: ""
                // 假名守卫：丢弃含日文假名的总结，防止日文污染记忆
                if (raw.isEmpty() || raw.any { it in '぀'..'ヿ' }) {
                    Triple("", "", "")
                } else {
                    val obj = extractJsonObject(raw)
                    if (obj != null) {
                        val summary = obj.get("summary")?.asString?.trim() ?: ""
                        val kind = obj.get("kind")?.asString?.trim() ?: "detail"
                        val date = normalizeEventDate(obj.get("date")?.asString ?: "")
                        Triple(summary, if (kind == "plot") "plot" else "detail", date)
                    } else {
                        // 没输出 JSON，退化为纯总结（按 detail）
                        Triple(raw, "detail", "")
                    }
                }
            }
        } catch (_: Exception) { Triple("", "", "") }
    }

    /** 校验并转换模型给的事件日期 "2026-09-03" → "2026年9月3日"；不合规一律返回空串（宁可不标也不标错） */
    private fun normalizeEventDate(raw: String): String = try {
        val text = raw.trim().take(10)
        if (!Regex("""^\d{4}-\d{1,2}-\d{1,2}$""").matches(text)) "" else {
            val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val d = f.parse(text)
            val year = Calendar.getInstance().apply { time = d }.get(Calendar.YEAR)
            if (year !in 1970..2100) "" else SimpleDateFormat("yyyy年M月d日", Locale.CHINESE).format(d)
        }
    } catch (_: Exception) { "" }

    private fun extractKeywords(text: String): List<String> {
        return text.split(Regex("[\\s，。！？,.!?、；：\"'（）()\\[\\]【】]+"))
            .filter { it.length in 2..8 }.distinct().take(8)
    }

    // ========== 持久化 ==========
    // 读写全部委托给 LocalStore（进程级一把锁 + 原子写 + 写监听）。
    // 这里刻意不自己 File.writeText：那是同步引擎和前台互相覆盖的入口。
    private fun saveConversations() {
        LocalStore.writeText(conversationsFile, gson.toJson(_conversations.value))
    }
    private fun loadConversations(): List<Conversation> = try {
        val text = LocalStore.readText(conversationsFile)
        if (text != null) {
            val type = object : TypeToken<List<Conversation>>() {}.type
            val list = gson.fromJson<List<Conversation>>(text, type) ?: emptyList()
            // 迁移旧单值字段（形象图/开场白）到新列表字段，不丢老数据
            list.map { conv -> conv.copy(characterProfile = conv.characterProfile?.normalized()) }
        } else emptyList()
    } catch (_: Exception) { emptyList() }

    private fun messagesFile(convId: String): File = LocalStore.messagesFile(convId)

    /** 判断一个对话的文件在不在盘上（同步判断「本地有没有这个东西」时要和读盘用同一套路径） */
    private fun messagesFileExists(convId: String): Boolean = messagesFile(convId).exists()

    private fun saveMessages(convId: String, msgs: List<Message>) {
        LocalStore.writeText(messagesFile(convId), gson.toJson(msgs))
    }

    private fun loadMessages(convId: String): List<Message> = try {
        val text = LocalStore.readText(messagesFile(convId))
        if (text != null) {
            val type = object : TypeToken<List<Message>>() {}.type
            val list = gson.fromJson<List<Message>>(text, type) ?: emptyList()
            mergeSplitImageMessages(list)
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
        if (rawMsgs.isEmpty()) return
        // 这份列表多半是「开跑时抓的快照 + 新回复」，里面用户的收藏状态可能是旧的；
        // 套一次用户意图覆盖表，免得把用户生成期间改的收藏悄悄抹掉
        val msgs = applyFavoriteOverrides(rawMsgs)
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
            builtInAssistant = existing?.builtInAssistant.orEmpty()
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
            builtInAssistant = existing?.builtInAssistant.orEmpty()
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

    /** 标准模式新对话首条回复完成后，后台让 AI 把首条消息概括成 4-15 字标题（失败保留默认标题） */
    private fun maybeAutoTitle(convId: String, userText: String) {
        // 内置的「Claude风格助理」不参与起标题：它的名字是功能的一部分，被概括掉之后
        // 侧栏里就只剩一条普通对话，用户根本认不出那是内置的那条（这条反馈也是这么来的）
        if (_conversations.value.find { it.id == convId }?.builtInAssistant == com.freechat.data.BuiltInAssistant.KEY) return
        viewModelScope.launch(Dispatchers.IO) {
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
    private fun buildSystemPrompt(hasTools: Boolean = false, history: List<Message> = emptyList(), convId: String = ""): String {
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
                "- 不确定处标注[暂未查证]，不编造\n" +
                "- 保持客观冷静，禁止主观臆断和情感化表达\n" +
                "- ★ 客观模式对准确性的要求是全 App 最高的：宁可回答得少，也不能错。\n" +
                "  每一个具体的事实、数字、日期、人名、机构名，都要有搜索结果支撑；\n" +
                "  搜索没有覆盖到的部分，明确写「未查到公开信息」，**不要用常识推一个像样的答案填上**。\n" +
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
    private fun buildBotProfile(): String {
        val modelName = _selectedModel.value.displayName
        val visualModelName = _selectedVisualModel.value.displayName
        val visionModelName = _selectedVisionModel.value?.displayName ?: ""
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
