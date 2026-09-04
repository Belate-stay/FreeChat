package com.freechat.data

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.util.Base64
import android.util.Log
import com.freechat.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

/**
 * AI 语音输出控制器 — 用小米 MiMo TTS 合成语音，再本地播放。
 *
 * 秒读 + 不断流方案：
 * - 文本按句切成小段，首段短、优先合成（秒读开播）；
 * - 其余段「并行合成」（Semaphore 限流并发），后台持续预取缓存，播放不断流。
 *
 * Plan B 进度条：
 * - 进度统一用「字数」度量，轨道总长 = 整段文本字数（固定，不推测时长）；
 * - 白条 = 已播放字数 / 总字数，灰条 = 已连续合成字数 / 总字数（缓存区）；
 * - 播放到缓存边界且还没合成好时进入「缓冲态」停住，等新段补上再续，不读满不回跳；
 * - 拖动 seek 只能在灰条（已合成范围）内跳转。
 *
 * 缓存：同一条消息第二次朗读直接读内存缓存秒播，进程被杀自动清空，不落盘。
 * 所有入口方法（speak/stop/pause/resume/seekTo）必须在主线程调用。
 */
object TtsController {
    private const val TAG = "FreeChatTTS"
    private const val MIMO_URL = "https://api.xiaomimimo.com/v1/chat/completions"
    private val MIMO_KEY = BuildConfig.MIMO_TTS_KEY
    private const val MAX_SEGMENT = 120   // 普通段最大字数
    private const val FIRST_SEGMENT_MAX = 30  // 首段更短，保证秒读（首段合成越快开播越早）
    private const val SYNTH_CONCURRENCY = 3  // 并行合成并发路数（保守值，避免服务端限流）
    private const val MAX_CACHE_ENTRIES = 12  // 内存缓存最多保留的消息条数（LRU）
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var player: MediaPlayer? = null
    private var synthJob: Job? = null
    private var playJob: Job? = null
    private var progressJob: Job? = null
    private var segments = listOf<String>()
    private var voice = "mimo_default"
    private var speed = 1f
    private var pitch = 1f
    private var speakingMessageId: String? = null
    // 自定义模型单段音频：用时间进度（非字数进度）
    private var timeBasedProgress = false
    private var totalDurationMs = 0L

    // 已合成段缓存（按 index，并行合成时未完成的段为 null 占位）
    private var segmentAudios = arrayOfNulls<ByteArray>(0)
    private var segmentDurations = LongArray(0)
    private var totalChars = 0
    private var allSynthesized = false
    // 当前正在播放的段 index（-1 表示无活动段，段间/缓冲时也置 -1）
    private var currentSegmentIndex = -1
    // 当前段之前已播完的累计时长/字数（用于全局进度计算）
    private var playedMsBeforeCurrent = 0L
    private var playedCharsBeforeCurrent = 0

    // 正在播放/暂停的消息 id
    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    // 正在合成首段（加载）的消息 id
    private val _loadingMessageId = MutableStateFlow<String?>(null)
    val loadingMessageId: StateFlow<String?> = _loadingMessageId.asStateFlow()

    // 是否暂停中
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    // 播放进度 0~1（白条，= 已播字数 / 总字数，轨道固定，单调不回跳）
    private val _playFraction = MutableStateFlow(0f)
    val playFraction: StateFlow<Float> = _playFraction.asStateFlow()

    // 已合成缓存进度 0~1（灰条，= 已连续合成字数 / 总字数）
    private val _bufferFraction = MutableStateFlow(0f)
    val bufferFraction: StateFlow<Float> = _bufferFraction.asStateFlow()

    // 是否在缓冲等待（播放追上了合成，等下一段补上）
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    /** 一条已完整合成语音的缓存 */
    private class CachedSpeech(
        val audios: Array<ByteArray?>,
        val durations: LongArray,
        val totalChars: Int
    )

    // LRU 内存缓存（accessOrder=true），进程存活期间有效，杀后台自动释放
    private val speechCache = object : LinkedHashMap<String, CachedSpeech>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSpeech>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    /** 开始朗读：切分 → 后台并行合成并缓存 → 边播边合成 */
    fun speak(messageId: String, text: String, voice: String, speed: Float, pitch: Float) {
        stop()
        this.voice = voice
        this.speed = speed
        this.pitch = pitch

        val segs = splitForSpeech(text)
        if (segs.isEmpty()) return
        this.speakingMessageId = messageId
        segments = segs

        totalChars = segs.sumOf { it.length }
        val n = segs.size
        segmentAudios = arrayOfNulls(n)
        segmentDurations = LongArray(n)
        allSynthesized = false
        currentSegmentIndex = -1
        playedMsBeforeCurrent = 0L
        playedCharsBeforeCurrent = 0
        _playFraction.value = 0f
        _bufferFraction.value = 0f
        _isBuffering.value = false

        // 命中缓存 → 直接秒播，不再合成（key 含文本指纹，防同 id 内容变化误命中）
        val cacheKey = "$messageId|$voice|${text.hashCode()}"
        val cached = speechCache[cacheKey]
        if (cached != null && cached.totalChars == totalChars) {
            segmentAudios = cached.audios
            segmentDurations = cached.durations
            allSynthesized = true
            _bufferFraction.value = 1f
            _playingMessageId.value = messageId
        } else {
            _loadingMessageId.value = messageId
            startSynthesis(cacheKey, segs)
        }

        // 进度轮询：每 100ms 刷新一次播放进度
        progressJob = scope.launch {
            while (isActive) {
                updateProgress()
                delay(100)
            }
        }

        launchPlayback(0, 0L)
    }

    /** 播放一段已合成的完整音频（自定义 TTS 模型），时间进度 */
    fun speakSingle(messageId: String, audio: ByteArray, speed: Float, pitch: Float) {
        stop()
        timeBasedProgress = true
        this.speakingMessageId = messageId
        this.speed = speed
        this.pitch = pitch
        segments = listOf("")
        totalChars = 0
        segmentAudios = arrayOfNulls(1)
        segmentAudios[0] = audio
        segmentDurations = LongArray(1)
        segmentDurations[0] = getAudioDuration(audio)
        totalDurationMs = segmentDurations[0]
        allSynthesized = true
        _bufferFraction.value = 1f
        _playingMessageId.value = messageId

        progressJob = scope.launch {
            while (isActive) {
                updateProgress()
                delay(100)
            }
        }
        launchPlayback(0, 0L)
    }

    /** 后台合成：先独占合成首段（最快响应秒播），首段完成后并行合成其余段。segs 为本地快照。 */
    private fun startSynthesis(cacheKey: String, segs: List<String>) {
        synthJob = scope.launch(Dispatchers.IO) {
            // 第一步：单独合成首段，独占网络/服务端不被并发挤占，实现秒播
            val first = segs.firstOrNull()
            if (first != null) {
                val audio = synthesize(first, voice)
                if (audio != null) {
                    val dur = getAudioDuration(audio)
                    withContext(Dispatchers.Main) {
                        if (!isActive) return@withContext
                        segmentAudios[0] = audio
                        segmentDurations[0] = dur
                        updateBufferFraction()
                    }
                }
            }
            // 第二步：并行合成其余段（Semaphore 限流），播放期间持续预取，缓冲不断流
            if (segs.size > 1) {
                val semaphore = Semaphore(SYNTH_CONCURRENCY)
                coroutineScope {
                    (1 until segs.size).map { i ->
                        async {
                            if (!isActive) return@async
                            val seg = segs[i]
                            val audio = semaphore.withPermit { synthesize(seg, voice) }
                            if (audio != null) {
                                val dur = getAudioDuration(audio)
                                withContext(Dispatchers.Main) {
                                    if (!isActive) return@withContext
                                    segmentAudios[i] = audio
                                    segmentDurations[i] = dur
                                    updateBufferFraction()
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
            withContext(Dispatchers.Main) {
                if (!isActive) return@withContext
                allSynthesized = true
                updateBufferFraction()
                // 全部段合成成功才写入缓存；有失败段则整条不缓存，下次重试
                if (segmentAudios.all { it != null }) {
                    speechCache[cacheKey] = CachedSpeech(
                        segmentAudios, segmentDurations, totalChars
                    )
                }
            }
        }
    }

    /** 暂停（保留进度，可继续） */
    fun pause() {
        if (_isPaused.value) return
        player?.let {
            try { it.pause() } catch (_: Exception) {}
        }
        _isPaused.value = true
    }

    /** 继续播放 */
    fun resume() {
        if (!_isPaused.value) return
        player?.let {
            try { it.start() } catch (_: Exception) {}
        }
        _isPaused.value = false
    }

    /** 停止并释放 */
    fun stop() {
        synthJob?.cancel(); synthJob = null
        playJob?.cancel(); playJob = null
        progressJob?.cancel(); progressJob = null
        player?.run {
            try { stop() } catch (_: Exception) {}
            try { release() } catch (_: Exception) {}
        }
        player = null
        segments = emptyList()
        segmentAudios = arrayOfNulls(0)
        segmentDurations = LongArray(0)
        totalChars = 0
        allSynthesized = false
        currentSegmentIndex = -1
        playedMsBeforeCurrent = 0L
        playedCharsBeforeCurrent = 0
        timeBasedProgress = false
        totalDurationMs = 0L
        speakingMessageId = null
        _playFraction.value = 0f
        _bufferFraction.value = 0f
        _isBuffering.value = false
        _playingMessageId.value = null
        _loadingMessageId.value = null
        _isPaused.value = false
    }

    /**
     * 拖动进度条跳转。fraction ∈ [0,1]，会被 clamp 到当前灰条（已连续合成范围）内，
     * 且不越过最后一个已合成字符（避免落到未合成段）。超出范围跳不过去。
     */
    fun seekTo(fraction: Float) {
        if (totalChars <= 0) return
        val contiguous = contiguousSynthesizedChars()
        if (contiguous <= 0) return
        val maxTarget = (contiguous - 1).coerceAtLeast(0)
        val targetChars = (fraction.coerceIn(0f, 1f) * totalChars).toInt().coerceIn(0, maxTarget)

        // 用真实字数 segments[i].length 定位（未合成段不参与，避免被当 0 字长跳过）
        var idx = 0
        var acc = 0
        for (i in segments.indices) {
            val c = segments[i].length
            if (targetChars < acc + c) { idx = i; break }
            acc += c
            idx = i
        }
        // 段内偏移字数 → 时长（中文每字发音时长近似恒定，字数比例≈时长比例）
        val dur = segmentDurations.getOrElse(idx) { 0L }
        val c = segments[idx].length
        val offsetChars = (targetChars - acc).coerceAtLeast(0)
        val offsetMs = if (c > 0 && dur > 0) dur * offsetChars / c else 0L

        player?.let { try { it.pause() } catch (_: Exception) {} }
        playJob?.cancel(); playJob = null
        launchPlayback(idx, offsetMs)
    }

    /** 从第 [startIndex] 段的 [startOffsetMs] 处开始顺序播放 */
    private fun launchPlayback(startIndex: Int, startOffsetMs: Long) {
        playJob = scope.launch {
            try {
                var idx = startIndex
                var offset = startOffsetMs
                var accMs = 0L
                var accChars = 0
                for (i in 0 until idx) {
                    accMs += segmentDurations.getOrElse(i) { 0L }
                    accChars += segments.getOrElse(i) { "" }.length
                }

                while (idx < segments.size) {
                    // 等待第 idx 段合成完成（未合成则缓冲等待，不中断播放流程）
                    while (segmentAudios.getOrNull(idx) == null && !allSynthesized) {
                        _isBuffering.value = true
                        delay(50)
                    }
                    _isBuffering.value = false
                    val audio = segmentAudios.getOrNull(idx) ?: break  // 合成失败/提前结束

                    if (_isPaused.value) _isPaused.first { !it }  // 暂停则等恢复

                    val dur = segmentDurations.getOrElse(idx) { 0L }
                    playedMsBeforeCurrent = accMs
                    playedCharsBeforeCurrent = accChars
                    currentSegmentIndex = idx
                    _loadingMessageId.value = null
                    _playingMessageId.value = speakingMessageId

                    val ok = playSegmentBlocking(audio, offset)
                    if (!ok) break
                    offset = 0L
                    accMs += dur
                    accChars += segments.getOrElse(idx) { "" }.length
                    // 本段播完立即并入累计，进入段间/缓冲，进度停在段尾边界不回跳
                    playedMsBeforeCurrent = accMs
                    playedCharsBeforeCurrent = accChars
                    currentSegmentIndex = -1
                    idx++
                }
                // 自然播完或合成失败中断，统一复位（停止自己 playJob 无害）
                stop()
            } catch (e: CancellationException) {
                // 停止/seek 时正常退出，静默处理
            } catch (e: Exception) {
                Log.w(TAG, "播放异常: ${e.message}")
                stop()
            }
        }
    }

    /** 播放一段音频（可从 offset 处开始），阻塞直到完成或出错。返回是否自然完成。 */
    private suspend fun playSegmentBlocking(audio: ByteArray, startOffsetMs: Long = 0L): Boolean =
        suspendCancellableCoroutine { cont ->
            var mp: MediaPlayer? = null
            try {
                mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(ByteArrayMediaDataSource(audio))
                    setPlaybackParams(PlaybackParams().setSpeed(speed).setPitch(pitch))
                    setOnCompletionListener {
                        try { release() } catch (_: Exception) {}
                        if (player === this) player = null
                        if (cont.isActive) cont.resume(true)
                    }
                    setOnErrorListener { _, _, _ ->
                        try { release() } catch (_: Exception) {}
                        if (player === this) player = null
                        if (cont.isActive) cont.resume(false)
                        true
                    }
                    prepare()
                    if (startOffsetMs > 0) seekTo(startOffsetMs.toInt())
                    start()
                }
            } catch (e: Exception) {
                Log.w(TAG, "播放段失败: ${e.message}")
                mp?.let { try { it.release() } catch (_: Exception) {} }
                if (cont.isActive) cont.resume(false)
                return@suspendCancellableCoroutine
            }
            player = mp
            cont.invokeOnCancellation {
                mp?.let {
                    try { it.stop() } catch (_: Exception) {}
                    try { it.release() } catch (_: Exception) {}
                }
                if (player === mp) player = null
            }
        }

    /** 调用 MiMo TTS 合成，返回 mp3 字节；失败返回 null */
    private suspend fun synthesize(text: String, voice: String): ByteArray? =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val body = gson.toJson(mapOf(
                    "model" to "mimo-v2.5-tts",
                    "messages" to listOf(mapOf("role" to "assistant", "content" to text)),
                    "audio" to mapOf("format" to "mp3", "voice" to voice)
                )).toRequestBody(JSON_MEDIA)

                val request = Request.Builder()
                    .url(MIMO_URL)
                    .addHeader("Authorization", "Bearer $MIMO_KEY")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "TTS HTTP ${response.code}: ${response.message}")
                    return@withContext null
                }
                val respBody = response.body?.string() ?: return@withContext null
                val json = try {
                    JsonParser.parseString(respBody).asJsonObject
                } catch (e: Exception) {
                    Log.w(TAG, "TTS JSON 解析失败: ${e.message}")
                    return@withContext null
                }
                if (json.has("error")) {
                    Log.w(TAG, "TTS 返回错误: ${json.get("error")}")
                    return@withContext null
                }
                val audioData = json.getAsJsonArray("choices")?.get(0)?.asJsonObject
                    ?.getAsJsonObject("message")?.getAsJsonObject("audio")?.get("data")?.asString
                    ?: return@withContext null
                Base64.decode(audioData, Base64.DEFAULT)
            } catch (e: Exception) {
                Log.w(TAG, "TTS 合成失败: ${e.message}")
                null
            }
        }

    /** 读取 mp3 字节的时长（毫秒），失败返回 0 */
    private fun getAudioDuration(audio: ByteArray): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(ByteArrayMediaDataSource(audio))
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            dur?.toLong() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "读取音频时长失败: ${e.message}")
            0L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /** 已连续合成的字数（从第 0 段起连续非 null 的字数累计） */
    private fun contiguousSynthesizedChars(): Int {
        var contiguous = 0
        for (i in segments.indices) {
            if (segmentAudios.getOrNull(i) == null) break
            contiguous += segments[i].length
        }
        return contiguous
    }

    /** 刷新灰条（已连续合成字数 / 总字数） */
    private fun updateBufferFraction() {
        if (totalChars <= 0) {
            if (_bufferFraction.value != 0f) _bufferFraction.value = 0f
            return
        }
        _bufferFraction.value = (contiguousSynthesizedChars().toFloat() / totalChars).coerceIn(0f, 1f)
    }

    /** 刷新白条（已播字数 / 总字数）。分母固定，进度单调不回跳。 */
    private fun updateProgress() {
        if (timeBasedProgress) {
            val dur = totalDurationMs
            val curMs = try { player?.currentPosition?.toLong() } catch (_: Exception) { null } ?: 0L
            _playFraction.value = if (dur > 0) (curMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
            return
        }
        if (totalChars <= 0) {
            if (_playFraction.value != 0f) _playFraction.value = 0f
            return
        }
        val idx = currentSegmentIndex
        if (idx < 0 || idx >= segments.size) {
            _playFraction.value = (playedCharsBeforeCurrent.toFloat() / totalChars).coerceIn(0f, 1f)
            return
        }
        val curMs = try { player?.currentPosition?.toLong() } catch (_: Exception) { null } ?: 0L
        val dur = segmentDurations.getOrElse(idx) { 0L }
        val chars = segments[idx].length
        val curChars = if (dur > 0 && chars > 0) (chars * curMs / dur).toInt().coerceIn(0, chars) else 0
        val played = playedCharsBeforeCurrent + curChars
        _playFraction.value = (played.toFloat() / totalChars).coerceIn(0f, 1f)
    }

    /** 按句切分文本，控制每段长度，保证每段合成快 */
    private fun splitForSpeech(text: String): List<String> {
        val clean = cleanForSpeech(text)
        if (clean.isBlank()) return emptyList()

        // 先按句末标点切（中英文）
        val rawParts = clean
            .split(Regex("(?<=[。！？；…])|(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val result = mutableListOf<String>()
        for (part in rawParts) {
            if (part.length <= MAX_SEGMENT) {
                result.add(part)
            } else {
                // 超长句按逗号/顿号再切
                val subParts = part.split(Regex("(?<=[，,、])")).map { it.trim() }.filter { it.isNotBlank() }
                val buf = StringBuilder()
                for (s in subParts) {
                    buf.append(s)
                    if (buf.length >= MAX_SEGMENT) {
                        result.add(buf.toString())
                        buf.clear()
                    }
                }
                if (buf.isNotBlank()) result.add(buf.toString())
            }
        }

        // 首段再限短：保证第一段合成快、尽早开播（秒读）
        if (result.isNotEmpty() && result.first().length > FIRST_SEGMENT_MAX) {
            val first = result.first()
            val head = first.take(FIRST_SEGMENT_MAX)
            val tail = first.substring(FIRST_SEGMENT_MAX).trimStart()
            result.removeAt(0)
            result.add(0, head)
            if (tail.isNotEmpty()) result.add(1, tail)
        }
        return result
    }

    /** 去掉 Markdown/格式符号，只保留可朗读的正文文字（保留自然标点以断句） */
    private fun cleanForSpeech(text: String): String {
        var s = text
        s = s.replace(Regex("```[\\s\\S]*?```"), " ")
        s = s.replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
        s = s.replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        s = s.replace("`", "")
        s = s.replace(Regex("(?m)^#{1,6}\\s*"), "")
        s = s.replace("**", "").replace("__", "").replace("~~", "")
        s = s.replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        s = s.replace(Regex("(?m)^\\s*\\d+\\.\\s*"), "")
        s = s.replace(Regex("(?m)^\\s*>\\s?"), "")
        s = s.replace("|", " ").replace(Regex("-{3,}"), " ")
        s = s.replace(Regex("<[^>]+>"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s
    }
}

/** 用 ByteArray 作为 MediaPlayer 数据源，避免写临时文件 */
private class ByteArrayMediaDataSource(private val data: ByteArray) : MediaDataSource() {
    override fun getSize(): Long = data.size.toLong()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= data.size) return -1
        val len = minOf(size, data.size - position.toInt())
        System.arraycopy(data, position.toInt(), buffer, offset, len)
        return len
    }

    override fun close() {}
}
