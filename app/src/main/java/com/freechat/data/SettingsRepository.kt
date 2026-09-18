package com.freechat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.freechat.model.ModelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "freechat_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        /**
         * 这些键从 private 放宽到 internal，是为了让 [com.freechat.sync.SettingsBridge]
         * 能按名字读原值。**放宽的只是可见性，不是所有权**：读写照样只能走
         * 下面那些 `saveXxx`（它们都经过 [edit]）。
         */
        internal val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        internal val KEY_SELECTED_VISUAL_MODEL = stringPreferencesKey("selected_visual_model")
        internal val KEY_SELECTED_VISION_MODEL = stringPreferencesKey("selected_vision_model")
        internal val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        internal val KEY_COLOR_THEME = intPreferencesKey("color_theme")
        internal val KEY_TEMP_MODE = intPreferencesKey("temp_mode")
        internal val KEY_LENGTH_MODE = intPreferencesKey("length_mode")
        internal val KEY_PINNED_IDS = stringSetPreferencesKey("pinned_conv_ids")
        internal val KEY_ENABLE_WEB_SEARCH = booleanPreferencesKey("enable_web_search")
        internal val KEY_SHOW_THINKING = booleanPreferencesKey("show_thinking")
        internal val KEY_LANGUAGE_CODE = stringPreferencesKey("language_code")
        internal val KEY_FONT_SIZE = intPreferencesKey("font_size_v2")
        internal val KEY_VOICE_MODEL = stringPreferencesKey("voice_model")
        internal val KEY_TTS_VOICE = stringPreferencesKey("tts_voice")
        internal val KEY_TTS_SPEED = floatPreferencesKey("tts_speed_v3")
        internal val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        internal val KEY_TTS_AUTO_PLAY = booleanPreferencesKey("tts_auto_play")
        internal val KEY_AUTO_SUMMARIZE_MEMORY = booleanPreferencesKey("auto_summarize_memory")
        internal val KEY_USE_SYSTEM_FONT = booleanPreferencesKey("use_system_font")
        internal val KEY_GLOBAL_MEMORIES = stringPreferencesKey("global_memories")
        internal val KEY_ADVANCED_MATERIAL = booleanPreferencesKey("advanced_material")
        internal val KEY_SYSTEM_DARK_THEME = booleanPreferencesKey("system_dark_theme")
        internal val KEY_LIQUID_BACKDROP = booleanPreferencesKey("liquid_backdrop")
        internal val KEY_CHAT_MODE = intPreferencesKey("chat_mode")
        internal val KEY_CUSTOM_MODELS = stringPreferencesKey("custom_models")
        internal val KEY_HAS_AGREED_TERMS = booleanPreferencesKey("has_agreed_terms")
        /**
         * 最后一次看过「更新了什么」的那个版本号（空 = 从没看过）。
         * 存的是一整个版本号而不是布尔：这样每次发版都会再出现一次，
         * 用户不必自己去设置里翻更新日志。
         * **本机字段，不参与同步** —— 换台设备本来就该看一遍。
         */
        internal val KEY_ASR_MODEL = stringPreferencesKey("asr_model")

        /**
         * 内置「Claude风格助理」是否已经生成过。
         *
         * 只在**第一次**生成时置真，此后永不回退 —— 这就是"用户删掉它之后不会又长出来"的全部机制。
         * 它跟着账号同步，所以换设备不会各生成一条；另一台设备看到云端已经是 true，
         * 而对话本身也在同步流里，来到本地时就是同一条。
         */
        internal val KEY_CLAUDE_SEEDED = booleanPreferencesKey("claude_seeded")

        /**
         * 内置助理是否被**用户亲手删掉**过。单向置真，同样跟着账号同步。
         *
         * 为什么不跟 [KEY_CLAUDE_SEEDED] 合并成一个：那个闩只回答「生成过没有」，
         * 回答不了「现在不在，是丢的还是被删的」。而这两件事必须分开对待 ——
         * 被子账号/网页端覆盖掉（丢）的要补回来，用户自己删的要永远消失。
         */
        internal val KEY_CLAUDE_DELETED = booleanPreferencesKey("claude_deleted")

        /**
         * 「默认值改到第几版了」的一次性闩（1.0.64 引入）。
         *
         * 新装默认值和老用户当前值本来该各走各的 —— 但**没被改过的项在盘上根本没有键**，
         * 它们的值就是下面那些 `?: 默认值`。所以光改代码，会把「从没动过这一项」的老用户
         * 也一起改掉。这个闩就是用来把两类人分开的，见 [migrateDefaultsIfNeeded]。
         */
        internal val KEY_DEFAULTS_VERSION = intPreferencesKey("defaults_version")

        /** 当前这一版默认值。加新默认值就 +1，并在 [migrateDefaultsIfNeeded] 里补一段。 */
        private const val DEFAULTS_VERSION = 1

        // ---- 1.0.64 起的新装默认值 ----
        // 每一对（新默认值 / 老默认值）都成对写在这里，别散落到迁移函数里去 ——
        // 散着写早晚会出现「改了默认值却忘了同步改迁移里的老值」，
        // 那种 bug 的表现是「老用户被静默改设置」，没人会报上来。
        private const val DEFAULT_SHOW_THINKING = true          // 老：false
        private const val DEFAULT_COLOR_THEME_ORDINAL = 2       // 2 = WHITE(纯白)；老：0 = BROWN(莫兰迪暖棕)
        private const val DEFAULT_ADVANCED_MATERIAL = true      // 老：false
        private const val DEFAULT_LIQUID_BACKDROP = true        // 老：false

        // ---- 写入通知：同步引擎靠它知道"设置被用户改了" ----

        private val LOCK = Any()

        /**
         * 装了以后**永不摘**（和网页端 `db.setWriteListener` 一个道理）：
         * 摘掉监听 = 看不见本地改动，那些改动不会报错，只会安静地永远不上传。
         */
        private var onWrite: (() -> Unit)? = null

        @Volatile
        private var suspendDepth = 0

        /**
         * **本地刚改过、还没推上云端**的偏好键 → 改动序号。这是「开关自己弹回去」的唯一解药。
         *
         * 病根：用户拨一下开关 → 落盘 → 写监听通知同步引擎 → 引擎这一轮是**先拉后推**。
         * 而这一轮拉回来的那份，正是**改之前**推上去的旧值（云端还没收到这次改动），
         * 一 apply 就把用户刚关掉的开关又打开了 —— 用户看到的就是「明明刚关掉，它自己又开了，
         * 得点第二次才行」。第二次能成，只因为那一轮的游标已经越过那条旧版本了。
         *
         * 所以：**只要还没推上去，本地的这次改动就不许被任何远端 apply 覆盖**。
         * 推送成功后由 [releasePendingLocal] 放行（见 SyncEngine.pushSettings）。
         */
        private val pendingLocal = LinkedHashMap<String, Long>()
        private var pendingSeq = 0L

        fun setWriteListener(fn: (() -> Unit)?) {
            synchronized(LOCK) { onWrite = fn }
        }

        /** 记下这一笔**用户改动**动到了哪些键（比对前后两份快照，见 [edit]） */
        private fun recordPending(before: Preferences, after: Preferences) {
            synchronized(LOCK) {
                for (key in after.asMap().keys + before.asMap().keys) {
                    if (before[key] != after[key]) {
                        pendingSeq++
                        pendingLocal[key.name] = pendingSeq
                    }
                }
            }
        }

        /**
         * 当前那批「本地改过、还没推上去」的键 + 各自序号。
         *
         * 推设置之前取一份，推成功后交给 [releasePendingLocal] —— 序号是用来认「这一笔还是不是
         * 我推的那一笔」的：推送在飞的时候用户又拨了一下同一个开关，序号会变，那一次必须继续保护着。
         */
        fun pendingLocalSnapshot(): Map<String, Long> = synchronized(LOCK) { LinkedHashMap(pendingLocal) }

        /** 推送成功后放行（只放行推这一份时记下的、之后没再被改过的那些键） */
        fun releasePendingLocal(snapshot: Map<String, Long>) {
            synchronized(LOCK) {
                for ((name, seq) in snapshot) if (pendingLocal[name] == seq) pendingLocal.remove(name)
            }
        }

        /**
         * 包住「同步引擎把云端设置落回来」的那几次写入，免得又被当成用户改的推回去。
         *
         * 加计数器和加锁在**同一个** synchronized 块里完成：先拿锁再自增，
         * 中途插进来的那个用户写入拿不到锁，就不会因为读到还没自增的深度而漏报。
         */
        suspend fun <T> suspendApply(block: suspend () -> T): T {
            synchronized(LOCK) { suspendDepth++ }
            try {
                return block()
            } finally {
                synchronized(LOCK) { suspendDepth-- }
            }
        }
    }

    /**
     * **所有**落盘的唯一入口。
     *
     * 每个 `saveXxx` 都走这里，所以「设置变了」只需要在这一个地方挂钩子，
     * 不用去动设置界面那几十个调用方。同步模块通过 [setWriteListener] 注册，
     * DataStore 本身不认识同步 —— 反过来的依赖会让两边都绕不开对方。
     */
    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        // **必须写全 `context.dataStore.`**。Kotlin 解析调用时成员函数优先于扩展函数，
        // 而 `DataStore.edit` 正是个扩展 —— 裸写 `edit { }` 会绑到**这个方法自己**身上，
        // 无限递归到 StackOverflowError。它又是所有 saveXxx 的唯一出口，
        // 所以表现是「改任何一项设置都闪退」，而不是某一项坏了。
        context.dataStore.edit { prefs ->
            if (suspendDepth == 0) {
                // 用户改的：原样写，同时记下动到了哪几个键，好在推上去之前挡住远端旧值
                val before = prefs.toPreferences()
                block(prefs)
                recordPending(before, prefs)
            } else {
                // 同步引擎落云端值的：照落，但**跳过**本地刚改过、还没推上去的那几个键
                val protect = synchronized(LOCK) { if (pendingLocal.isEmpty()) null else pendingLocal.keys.toHashSet() }
                if (protect == null) {
                    block(prefs)
                } else {
                    val draft = prefs.toMutablePreferences()
                    block(draft)
                    for (key in draft.asMap().keys + prefs.asMap().keys) {
                        if (key.name in protect) draft.setAny(key, prefs[key])
                    }
                    prefs.clear()
                    // 逐个 set 而不是 putAll(draft)：这一版 DataStore 的 putAll 只认
                    // Iterable<Pair<Key<*>, Any?>>，直接塞一份 Preferences 是编不过的
                    for ((k, v) in draft.asMap()) prefs.setAny(k, v)
                }
            }
        }
        // 设备本地字段（选模型、同意条款）也会走到这儿，它们压根不参与同步 ——
        // 于是会多推一次内容没变的设置。服务端对相同内容是字节比对后**不顶版本号**的，
        // 代价就是一次几百毫秒的往返，而换来的代码简单得多：这里不必知道哪个键算"可同步"。
        val notify = synchronized(LOCK) { suspendDepth == 0 }
        if (notify) onWrite?.invoke()
    }

    val selectedModelId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_MODEL] ?: ""
    }

    val selectedVisualModelId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_VISUAL_MODEL] ?: ""
    }

    val selectedVisionModelId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_VISION_MODEL] ?: ""
    }

    val themeModeOrdinal: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: 0
    }

    // 默认 2 = ColorTheme.WHITE（纯白）。1.0.64 起新装默认，老用户由迁移钉在原来的暖棕上。
    val colorThemeOrdinal: Flow<Int> = context.dataStore.data.afterDefaultMigration().map { prefs ->
        prefs[KEY_COLOR_THEME] ?: DEFAULT_COLOR_THEME_ORDINAL
    }

    val tempModeOrdinal: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_TEMP_MODE] ?: 0
    }

    val lengthModeOrdinal: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_LENGTH_MODE] ?: 0
    }

    val pinnedConversationIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_PINNED_IDS] ?: emptySet()
    }

    val enableWebSearch: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_WEB_SEARCH] ?: true  // 默认开启
    }

    val showThinking: Flow<Boolean> = context.dataStore.data.afterDefaultMigration().map { prefs ->
        prefs[KEY_SHOW_THINKING] ?: DEFAULT_SHOW_THINKING
    }

    val languageCode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE_CODE] ?: "system"
    }

    val fontSizeOrdinal: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_FONT_SIZE] ?: 1  // 1 = MEDIUM（标准）
    }

    val voiceModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_VOICE_MODEL] ?: "MiMo-V2.5-TTS"
    }

    val ttsVoice: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTS_VOICE] ?: "mimo_default"
    }

    val ttsSpeed: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTS_SPEED] ?: 1.0f
    }

    val ttsPitch: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTS_PITCH] ?: 1.0f
    }

    val ttsAutoPlay: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTS_AUTO_PLAY] ?: false  // 默认关闭自动朗读
    }

    val autoSummarizeMemory: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_SUMMARIZE_MEMORY] ?: true  // 默认开启：自动总结对话以巩固 AI 记忆
    }

    val useSystemFont: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_USE_SYSTEM_FONT] ?: false  // 默认关闭：使用自定义聊天字体
    }

    // 全局记忆点（多条），存 JSON 数组字符串以保留顺序
    val globalMemories: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_GLOBAL_MEMORIES] ?: "[]"
        runCatching { com.google.gson.Gson().fromJson(raw, Array<String>::class.java).toList() }
            .getOrElse { emptyList() }
    }

    val advancedMaterial: Flow<Boolean> = context.dataStore.data.afterDefaultMigration().map { prefs ->
        prefs[KEY_ADVANCED_MATERIAL] ?: DEFAULT_ADVANCED_MATERIAL
    }

    val systemDarkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SYSTEM_DARK_THEME] ?: false  // 默认深色（false=深色，true=黑色）
    }

    // 流动炫彩：进阶视觉选项（每帧都在动，老机型会掉帧）。1.0.64 起改为默认开。
    val liquidBackdrop: Flow<Boolean> = context.dataStore.data.afterDefaultMigration().map { prefs ->
        prefs[KEY_LIQUID_BACKDROP] ?: DEFAULT_LIQUID_BACKDROP
    }

    val chatModeOrdinal: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_CHAT_MODE] ?: 0  // 0 = STANDARD（标准问答）
    }

    // 用户自定义模型（JSON 数组），5 类模型共用一份列表，按 modelType 区分
    val customModels: Flow<List<ModelInfo>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CUSTOM_MODELS] ?: "[]"
        runCatching {
            val listType = object : com.google.gson.reflect.TypeToken<List<ModelInfo>>() {}.type
            com.google.gson.Gson().fromJson<List<ModelInfo>>(raw, listType)
        }.getOrElse { emptyList() }
    }

    // 是否已同意用户协议与免责声明（首次进入的门槛）
    val hasAgreedTerms: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HAS_AGREED_TERMS] ?: false
    }


    // 语音识别模型 id（无内置，默认空 = 未添加）
    val asrModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ASR_MODEL] ?: ""
    }

    /** 内置「Claude风格助理」是否生成过（置真后不回退，见 [KEY_CLAUDE_SEEDED]） */
    val claudeSeeded: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_CLAUDE_SEEDED] ?: false
    }

    /**
     * 内置「Claude风格助理」是否**被用户亲手删掉过**（置真后不回退）。
     *
     * 与 [claudeSeeded] 分开记，是因为两者合起来才能回答「它现在不在，是丢了还是被删了」：
     * 丢了要补回来，删了就得永远消失。只用一个「生成过」的闩，丢的那次会被误判成删的。
     */
    val claudeDeleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_CLAUDE_DELETED] ?: false
    }

    /**
     * 当前这份设置的原始快照（同步引擎按名字读原值用，比如主题的枚举序号）。
     *
     * 只读 —— 拿它去改是不行的：所有落盘一律走 [edit]，那里才挂着写通知。
     */
    suspend fun currentPreferences(): Preferences = context.dataStore.data.first()

    /**
     * 一次性把「老默认值」钉进盘里，让老用户在默认值换代之后**什么都不变**。
     *
     * 判据是「这台设备的设置盘是不是空的」：
     *  · 空 → 全新安装。一个键都不写，直接吃代码里的新默认值（就是本函数的意义）。
     *  · 非空 → 早就装过（同意协议那一下就会写下第一个键）。把「用户从没动过、
     *          而这一版又刚改了默认值」的那几项，按**老默认值**显式写进去。
     *
     * 只写键不存在的那些 —— 用户自己改过的值一个字都不能碰。
     * 写完落 [KEY_DEFAULTS_VERSION]，此后不再跑（并发调用也安全：读快照与写闩
     * 两头夹着，"全新安装"那条路上先到的那个只会写下闩本身，后来的直接早退）。
     */
    private suspend fun migrateDefaultsIfNeeded() {
        val prefs = context.dataStore.data.first()
        if (prefs[KEY_DEFAULTS_VERSION] == DEFAULTS_VERSION) return
        val freshInstall = prefs.asMap().isEmpty()
        edit { mutable ->
            if (!freshInstall) {
                // 老默认值（= 1.0.64 之前代码里写的那些），成对见 companion 顶部的常量
                if (KEY_SHOW_THINKING !in mutable) mutable[KEY_SHOW_THINKING] = false
                if (KEY_COLOR_THEME !in mutable) mutable[KEY_COLOR_THEME] = 0
                if (KEY_ADVANCED_MATERIAL !in mutable) mutable[KEY_ADVANCED_MATERIAL] = false
                if (KEY_LIQUID_BACKDROP !in mutable) mutable[KEY_LIQUID_BACKDROP] = false
            }
            mutable[KEY_DEFAULTS_VERSION] = DEFAULTS_VERSION
        }
    }

    /**
     * 受 1.0.64 默认值改动影响的几条 Flow 都要先过一遍 [migrateDefaultsIfNeeded]。
     *
     * 写成 `onStart` 而不是在别处另起一个协程去迁移：onStart 是在**本 flow 第一次发值之前**
     * 跑的，所以消费者拿到的第一个值就已经是迁移之后的值 —— 不会先按新默认值画一帧、
     * 等迁移落盘再跳回老值（老用户会看见界面「自己变了」）。另起协程挡不住这个窗口。
     *
     * runCatching 兜住：迁移写失败也只该是「这一次没迁成」，不能让整条设置流跟着断掉。
     */
    private fun <T> Flow<T>.afterDefaultMigration(): Flow<T> =
        onStart { runCatching { migrateDefaultsIfNeeded() } }

    suspend fun saveSelectedModel(modelId: String) {
        edit { prefs -> prefs[KEY_SELECTED_MODEL] = modelId }
    }

    suspend fun saveSelectedVisualModel(modelId: String) {
        edit { prefs -> prefs[KEY_SELECTED_VISUAL_MODEL] = modelId }
    }

    suspend fun saveSelectedVisionModel(modelId: String) {
        edit { prefs -> prefs[KEY_SELECTED_VISION_MODEL] = modelId }
    }

    suspend fun saveThemeMode(ordinal: Int) {
        edit { prefs -> prefs[KEY_THEME_MODE] = ordinal }
    }

    suspend fun saveColorTheme(ordinal: Int) {
        edit { prefs -> prefs[KEY_COLOR_THEME] = ordinal }
    }

    suspend fun saveTempMode(ordinal: Int) {
        edit { prefs -> prefs[KEY_TEMP_MODE] = ordinal }
    }

    suspend fun saveLengthMode(ordinal: Int) {
        edit { prefs -> prefs[KEY_LENGTH_MODE] = ordinal }
    }

    suspend fun savePinnedIds(ids: Set<String>) {
        edit { prefs -> prefs[KEY_PINNED_IDS] = ids }
    }

    suspend fun saveEnableWebSearch(enabled: Boolean) {
        edit { prefs -> prefs[KEY_ENABLE_WEB_SEARCH] = enabled }
    }

    suspend fun saveShowThinking(enabled: Boolean) {
        edit { prefs -> prefs[KEY_SHOW_THINKING] = enabled }
    }

    suspend fun saveLanguageCode(code: String) {
        edit { prefs -> prefs[KEY_LANGUAGE_CODE] = code }
    }

    suspend fun saveFontSize(ordinal: Int) {
        edit { prefs -> prefs[KEY_FONT_SIZE] = ordinal }
    }

    suspend fun saveVoiceModel(model: String) {
        edit { prefs -> prefs[KEY_VOICE_MODEL] = model }
    }

    suspend fun saveTtsVoice(voice: String) {
        edit { prefs -> prefs[KEY_TTS_VOICE] = voice }
    }

    suspend fun saveTtsSpeed(speed: Float) {
        edit { prefs -> prefs[KEY_TTS_SPEED] = speed }
    }

    suspend fun saveTtsPitch(pitch: Float) {
        edit { prefs -> prefs[KEY_TTS_PITCH] = pitch }
    }

    suspend fun saveTtsAutoPlay(auto: Boolean) {
        edit { prefs -> prefs[KEY_TTS_AUTO_PLAY] = auto }
    }

    suspend fun saveAutoSummarizeMemory(enabled: Boolean) {
        edit { prefs -> prefs[KEY_AUTO_SUMMARIZE_MEMORY] = enabled }
    }

    suspend fun saveUseSystemFont(enabled: Boolean) {
        edit { prefs -> prefs[KEY_USE_SYSTEM_FONT] = enabled }
    }

    suspend fun saveGlobalMemories(memories: List<String>) {
        val raw = com.google.gson.Gson().toJson(memories)
        edit { prefs -> prefs[KEY_GLOBAL_MEMORIES] = raw }
    }

    suspend fun saveAdvancedMaterial(enabled: Boolean) {
        edit { prefs -> prefs[KEY_ADVANCED_MATERIAL] = enabled }
    }

    suspend fun saveSystemDarkTheme(isBlack: Boolean) {
        edit { prefs -> prefs[KEY_SYSTEM_DARK_THEME] = isBlack }
    }

    suspend fun saveLiquidBackdrop(enabled: Boolean) {
        edit { prefs -> prefs[KEY_LIQUID_BACKDROP] = enabled }
    }

    suspend fun saveChatMode(ordinal: Int) {
        edit { prefs -> prefs[KEY_CHAT_MODE] = ordinal }
    }

    suspend fun saveCustomModels(models: List<ModelInfo>) {
        val raw = com.google.gson.Gson().toJson(models)
        edit { prefs -> prefs[KEY_CUSTOM_MODELS] = raw }
    }

    suspend fun saveHasAgreedTerms(agreed: Boolean) {
        edit { prefs -> prefs[KEY_HAS_AGREED_TERMS] = agreed }
    }


    suspend fun saveAsrModel(modelId: String) {
        edit { prefs -> prefs[KEY_ASR_MODEL] = modelId }
    }

    suspend fun saveClaudeSeeded(seeded: Boolean) {
        edit { prefs -> prefs[KEY_CLAUDE_SEEDED] = seeded }
    }

    suspend fun saveClaudeDeleted(deleted: Boolean) {
        edit { prefs -> prefs[KEY_CLAUDE_DELETED] = deleted }
    }
}

/**
 * 按**键名**把某一项写成 [value]（null = 这一项本来就没有，那就删掉）。
 *
 * 为什么要绕一道泛型：挡住远端旧值的那条路径上是拿 `Set<String>`（键名）逐个比对的，
 * 而 `MutablePreferences.set` 要求键和值在编译期对上类型 —— 星投影的 `Key<*>` 没法和值配型。
 * 键和值本来就是同一条写入里一起来的，这里的一次强转是安全的那一种。
 */
@Suppress("UNCHECKED_CAST")
private fun MutablePreferences.setAny(key: Preferences.Key<*>, value: Any?) {
    if (value == null) remove(key) else set(key as Preferences.Key<Any>, value)
}
