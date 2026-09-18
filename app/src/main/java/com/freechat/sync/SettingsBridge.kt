package com.freechat.sync

import androidx.datastore.preferences.core.Preferences
import com.freechat.data.SettingsRepository
import com.freechat.data.SettingsRepository.Companion.KEY_ADVANCED_MATERIAL
import com.freechat.data.SettingsRepository.Companion.KEY_AUTO_SUMMARIZE_MEMORY
import com.freechat.data.SettingsRepository.Companion.KEY_CHAT_MODE
import com.freechat.data.SettingsRepository.Companion.KEY_CLAUDE_DELETED
import com.freechat.data.SettingsRepository.Companion.KEY_CLAUDE_SEEDED
import com.freechat.data.SettingsRepository.Companion.KEY_COLOR_THEME
import com.freechat.data.SettingsRepository.Companion.KEY_ENABLE_WEB_SEARCH
import com.freechat.data.SettingsRepository.Companion.KEY_FONT_SIZE
import com.freechat.data.SettingsRepository.Companion.KEY_GLOBAL_MEMORIES
import com.freechat.data.SettingsRepository.Companion.KEY_LANGUAGE_CODE
import com.freechat.data.SettingsRepository.Companion.KEY_LENGTH_MODE
import com.freechat.data.SettingsRepository.Companion.KEY_LIQUID_BACKDROP
import com.freechat.data.SettingsRepository.Companion.KEY_SHOW_THINKING
import com.freechat.data.SettingsRepository.Companion.KEY_SYSTEM_DARK_THEME
import com.freechat.data.SettingsRepository.Companion.KEY_TEMP_MODE
import com.freechat.data.SettingsRepository.Companion.KEY_THEME_MODE
import com.freechat.data.SettingsRepository.Companion.KEY_TTS_AUTO_PLAY
import com.freechat.data.SettingsRepository.Companion.KEY_TTS_PITCH
import com.freechat.data.SettingsRepository.Companion.KEY_TTS_SPEED
import com.freechat.data.SettingsRepository.Companion.KEY_TTS_VOICE
import com.freechat.data.SettingsRepository.Companion.KEY_USE_SYSTEM_FONT
import com.freechat.data.SettingsRepository.Companion.KEY_VOICE_MODEL
import com.freechat.model.ColorTheme
import com.freechat.model.LengthMode
import com.freechat.model.TempMode
import com.freechat.model.ThemeMode
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * 设置 ↔ 线上格式。
 *
 * ## 两边存的根本不是一回事
 *
 * 安卓的设置是 DataStore 里的一堆键，主题/温度/长度这三组存的是**枚举序号**；
 * 网页端存的是一个 `AppSettings` 对象，同一批开关用的是**枚举名字**。
 * 最坑的是序号顺序还**不一样**：
 *
 * | | 安卓 | 网页 |
 * |---|---|---|
 * | 配色 | BROWN(0) BLUE(1) WHITE(2) | WARM BLUE WHITE |
 * | 主题 | SYSTEM(0) LIGHT(1) DARK(2) DARK_OLED(3) | LIGHT DARK OLED SYSTEM |
 *
 * 主题那行：安卓的 0 是「跟随系统」，网页的 SYSTEM 排在第 4 个。**照搬序号必然串档**
 * —— 手机上选了跟随系统，网页上会变成浅色。所以一律**按语义映射**，
 * 序号只用来在本地查表（见 [WIRE_NAME]）。
 *
 * 语言也一样：安卓是 `zh-CN` / `zh-TW`，网页是 `zh-Hans` / `zh-Hant`。
 *
 * ## 哪些同步、哪些不同步
 *
 * 网页端 `DEVICE_LOCAL_SETTINGS` 已经把三条排除理由写死了，安卓照办并补上自己的：
 *
 * - **明文 API Key**（`custom_models`）—— 上传它等于把用户的 Key 交给服务器，
 *   泄漏面从「这台设备」变成「整个账号」。一期不做。
 * - **模型选择**（`selected_model` / `selected_*_model` / `asr_model`）——
 *   指向的是**本机**自定义模型，另一台设备上没有这些 id，同步过去只会
 *   选中一个点不通的项。
 * - **条款同意**（`has_agreed_terms`）—— 「这台设备上的人同意过了」，属于设备级同意。
 * - **置顶**（`pinned_conv_ids`）—— 已经随对话本身的 `isPinned` 走了，再同步一份就会打架。
 *
 * 其余的**全同步**，包括网页端根本没有的那些（字号、TTS 音色语速、流动炫彩…）。
 * 它们放在一个叫 [ANDROID_PREFS] 的子对象里，而不是平铺在顶层：
 * **共有的键用共有的名字（这样两端才是真的在通信），私有的键圈在自己的命名空间里**
 * —— 网页端会把不认识的子对象原样透传，既不会误读，也不会哪天撞上同名的新字段。
 */
object SettingsBridge {

    /** 安卓专属偏好的命名空间：网页端不认识，原样收着再推回来 */
    const val ANDROID_PREFS = "androidPrefs"

    // ============================================================
    //  枚举 ↔ 名字
    // ============================================================

    /**
     * 安卓枚举 → 线上名字。
     *
     * 用显式映射而不是 `enum.name`：`BROWN` 在线上叫 `WARM`、`DARK_OLED` 叫 `OLED`，
     * 直接拿 name 传过去网页端认不得。反过来查用 [fromWire]，按类型限定，
     * 所以线上那个 "WARM" 落到 [ColorTheme] 上就是 BROWN、落到 [TempMode] 上就是 WARM。
     */
    private val WIRE_NAME: Map<Enum<*>, String> = mapOf(
        ColorTheme.BROWN to "WARM", ColorTheme.BLUE to "BLUE", ColorTheme.WHITE to "WHITE",
        ThemeMode.SYSTEM to "SYSTEM", ThemeMode.LIGHT to "LIGHT",
        ThemeMode.DARK to "DARK", ThemeMode.DARK_OLED to "OLED",
        TempMode.AUTO to "AUTO", TempMode.WARM to "WARM", TempMode.OBJECTIVE to "OBJECTIVE",
        LengthMode.AUTO to "AUTO", LengthMode.FULL to "FULL", LengthMode.CONCISE to "CONCISE"
    )

    private inline fun <reified T : Enum<T>> fromWire(name: String): T? =
        enumValues<T>().firstOrNull { WIRE_NAME[it] == name }

    // 每对话「新规则」那边（[PerConvBridge]）也要把同一组枚举换名字 ——
    // 映射只有这一份，两处各写一张表早晚会分叉

    fun tempModeWire(m: TempMode): String = WIRE_NAME.getValue(m)
    fun tempModeFromWire(name: String): TempMode? = fromWire<TempMode>(name)
    fun lengthModeWire(m: LengthMode): String = WIRE_NAME.getValue(m)
    fun lengthModeFromWire(name: String): LengthMode? = fromWire<LengthMode>(name)

    /** 语言：安卓 `zh-CN`/`zh-TW` ⇄ 网页 `zh-Hans`/`zh-Hant`；`system` 不参与同步 */
    private val LOCALE_TO_WIRE = mapOf("zh-CN" to "zh-Hans", "zh-TW" to "zh-Hant", "en" to "en")
    private val LOCALE_FROM_WIRE = LOCALE_TO_WIRE.entries.associate { (k, v) -> v to k }

    // ============================================================
    //  字段表
    // ============================================================

    /**
     * 一个可同步字段。
     *
     * [read] 返回 null 表示「本地没有这个值，别推」；[write] 只管写，写不动就自己 return
     * —— 远端给了个不认识的取值（比如网页端以后加了新主题）时，**保持本地原样比猜一个强**。
     */
    private class Field(
        val wire: String,
        val read: (Preferences) -> JsonElement?,
        val write: suspend (SettingsRepository, JsonElement) -> Unit
    )

    private val FIELDS: List<Field> = listOf(
        Field(
            "themeFamily",
            { p -> p[KEY_COLOR_THEME]?.let { ColorTheme.entries.getOrNull(it) }?.let { JsonPrimitive(WIRE_NAME.getValue(it)) } },
            { repo, el -> fromWire<ColorTheme>(el.asString)?.let { repo.saveColorTheme(it.ordinal) } }
        ),
        Field(
            "themeMode",
            { p -> p[KEY_THEME_MODE]?.let { ThemeMode.entries.getOrNull(it) }?.let { JsonPrimitive(WIRE_NAME.getValue(it)) } },
            { repo, el -> fromWire<ThemeMode>(el.asString)?.let { repo.saveThemeMode(it.ordinal) } }
        ),
        Field(
            "locale",
            { p -> (p[KEY_LANGUAGE_CODE] ?: "system").let { LOCALE_TO_WIRE[it] }?.let { JsonPrimitive(it) } },
            { repo, el -> LOCALE_FROM_WIRE[el.asString]?.let { repo.saveLanguageCode(it) } }
        ),
        Field(
            "useSystemFont",
            { p -> p[KEY_USE_SYSTEM_FONT]?.let { JsonPrimitive(it) } },
            { repo, el -> if (el.isJsonPrimitive) repo.saveUseSystemFont(el.asBoolean) }
        ),
        Field(
            "enableWebSearch",
            { p -> p[KEY_ENABLE_WEB_SEARCH]?.let { JsonPrimitive(it) } },
            { repo, el -> if (el.isJsonPrimitive) repo.saveEnableWebSearch(el.asBoolean) }
        ),
        Field(
            "showThinking",
            { p -> p[KEY_SHOW_THINKING]?.let { JsonPrimitive(it) } },
            { repo, el -> if (el.isJsonPrimitive) repo.saveShowThinking(el.asBoolean) }
        ),
        Field(
            "autoSummarizeMemory",
            { p -> p[KEY_AUTO_SUMMARIZE_MEMORY]?.let { JsonPrimitive(it) } },
            { repo, el -> if (el.isJsonPrimitive) repo.saveAutoSummarizeMemory(el.asBoolean) }
        ),
        Field(
            "tempMode",
            { p -> p[KEY_TEMP_MODE]?.let { TempMode.entries.getOrNull(it) }?.let { JsonPrimitive(WIRE_NAME.getValue(it)) } },
            { repo, el -> fromWire<TempMode>(el.asString)?.let { repo.saveTempMode(it.ordinal) } }
        ),
        Field(
            "lengthMode",
            { p -> p[KEY_LENGTH_MODE]?.let { LengthMode.entries.getOrNull(it) }?.let { JsonPrimitive(WIRE_NAME.getValue(it)) } },
            { repo, el -> fromWire<LengthMode>(el.asString)?.let { repo.saveLengthMode(it.ordinal) } }
        ),
        Field(
            "globalMemories",
            { p -> globalMemoriesOf(p[KEY_GLOBAL_MEMORIES] ?: "[]") },
            { repo, el -> if (el.isJsonArray) repo.saveGlobalMemories(el.asJsonArray.map { it.asString }) }
        )
    )

    /** 安卓把全局记忆存成一段 JSON 数组**字符串**，线上是真正的数组 —— 这里拆开 */
    private fun globalMemoriesOf(raw: String): JsonArray? = runCatching {
        JsonArray().apply {
            com.google.gson.JsonParser.parseString(raw).asJsonArray
                .mapNotNull { if (it.isJsonPrimitive) it.asString else null }
                .forEach { add(it) }
        }
    }.getOrNull()

    // ============================================================
    //  读
    // ============================================================

    /** 本地当前这份（线上格式）。推上去、和云端比对，都用它 */
    suspend fun snapshot(repo: SettingsRepository): JsonObject {
        val p = repo.currentPreferences()
        val out = JsonObject()
        for (f in FIELDS) f.read(p)?.let { out.add(f.wire, it) }
        androidPrefs(p)?.let { out.add(ANDROID_PREFS, it) }
        return out
    }

    /**
     * 安卓专属的那几项。
     *
     * 这几项网页端没有对应物，所以**不推给 `apply`，也不从 `apply` 里读** ——
     * 只在这里整包进出。它们是纯粹的偏好（字号、音色、视觉开关），
     * 在另一台安卓上原样恢复即可，没什么可冲突的。
     */
    private fun androidPrefs(p: Preferences): JsonObject? {
        val o = JsonObject()
        p[KEY_FONT_SIZE]?.let { o.addProperty("fontSize", it) }
        p[KEY_CHAT_MODE]?.let { o.addProperty("chatMode", it) }
        p[KEY_VOICE_MODEL]?.let { o.addProperty("voiceModel", it) }
        p[KEY_TTS_VOICE]?.let { o.addProperty("ttsVoice", it) }
        p[KEY_TTS_SPEED]?.let { o.addProperty("ttsSpeed", it) }
        p[KEY_TTS_PITCH]?.let { o.addProperty("ttsPitch", it) }
        p[KEY_TTS_AUTO_PLAY]?.let { o.addProperty("ttsAutoPlay", it) }
        p[KEY_LIQUID_BACKDROP]?.let { o.addProperty("liquidBackdrop", it) }
        p[KEY_ADVANCED_MATERIAL]?.let { o.addProperty("advancedMaterial", it) }
        p[KEY_SYSTEM_DARK_THEME]?.let { o.addProperty("systemDarkTheme", it) }
        // 内置助理生成过没有。它必须同步：否则换一台设备登录，那台看到的是 false，
        // 会再生成一条同名的对话出来，用户侧栏里就有两个「Claude风格助理」了。
        p[KEY_CLAUDE_SEEDED]?.let { o.addProperty("claudeSeeded", it) }
        // 用户亲手删过没有。也得同步 —— 在 A 机删掉，B 机不该让它在下次冷启动时又长出来。
        p[KEY_CLAUDE_DELETED]?.let { o.addProperty("claudeDeleted", it) }
        return if (o.size() == 0) null else o
    }

    // ============================================================
    //  写
    // ============================================================

    /**
     * 把云端那份落到本地。**以云端为准**，只留一条例外：全局记忆取并集。
     *
     * 为什么不能照抄「云端覆盖本地」：用户在这台手机上刚记了一条长期记忆，
     * 同一轮里云端恰好有一条更新的设置（另一台设备刚改过），覆盖下去
     * 这条新记忆就没了 —— 而且它连推送的机会都没有，因为它被覆盖**之后**
     * 才轮到推送，推上去的是被覆盖后的那份。全局记忆是设置里唯一算「内容」的字段，
     * 取并集之后这条通路就再没有丢数据的可能。
     *
     * 调用方必须用 [SettingsRepository.suspendApply] 包住 —— 否则这几次写入
     * 会被写监听当成「用户改的」，立刻又推回云端。
     */
    suspend fun applyRemote(repo: SettingsRepository, remote: JsonObject) {
        val localGlobal = repo.currentPreferences()[KEY_GLOBAL_MEMORIES]?.let { globalMemoriesOf(it) }
        // 「跟随系统」是**本机**的语义：它按的正是**这台设备的**系统语言。
        // 而线上那份语言是**另一台设备/网页端**存的（网页端根本没有「跟随系统」这一档，
        // 它只会存 zh-Hans / zh-Hant / en）。本地选了跟随系统时再去套线上那份，
        // 就等于「我在我这台手机上选了跟随系统，却被网页端的语言强行改掉」——
        // 用户反馈的「我调的跟随系统，怎么老是给我切成 English」就是这么来的
        // （英文系统 + 网页端存的是 en ⇒ 每次同步都被写成 en，设置里那一项还看着像自己变的）。
        // 所以这一档不参与写入：本地是 system，云端说什么都不动它；
        // 本地是明确语言（en / zh-CN / zh-TW）时照旧以云端为准，跨设备改语言仍然有效。
        val localLocale = repo.currentPreferences()[KEY_LANGUAGE_CODE] ?: "system"

        for (f in FIELDS) {
            val el = remote.get(f.wire) ?: continue
            if (el.isJsonNull) continue
            if (f.wire == "locale" && localLocale == "system") continue
            if (f.wire == "globalMemories") {
                // 并集：两边各记了几条，丢哪边都不合适
                val local = localGlobal ?: JsonArray()
                val merged = JsonArray()
                val seen = HashSet<String>()
                for (a in listOf(local, el)) {
                    if (!a.isJsonArray) continue
                    for (x in a.asJsonArray) {
                        val s = if (x.isJsonPrimitive) x.asString else continue
                        if (s.isBlank() || !seen.add(s)) continue
                        merged.add(s)
                    }
                }
                repo.saveGlobalMemories(merged.map { it.asString })
            } else {
                f.write(repo, el)
            }
        }

        remote.getAsJsonObject(ANDROID_PREFS)?.let { applyAndroidPrefs(repo, it) }
    }

    private suspend fun applyAndroidPrefs(repo: SettingsRepository, o: JsonObject) {
        o.int("fontSize")?.let { repo.saveFontSize(it) }
        o.int("chatMode")?.let { repo.saveChatMode(it) }
        o.str("voiceModel")?.let { repo.saveVoiceModel(it) }
        o.str("ttsVoice")?.let { repo.saveTtsVoice(it) }
        o.float("ttsSpeed")?.let { repo.saveTtsSpeed(it) }
        o.float("ttsPitch")?.let { repo.saveTtsPitch(it) }
        o.boolean("ttsAutoPlay")?.let { repo.saveTtsAutoPlay(it) }
        o.boolean("liquidBackdrop")?.let { repo.saveLiquidBackdrop(it) }
        o.boolean("advancedMaterial")?.let { repo.saveAdvancedMaterial(it) }
        o.boolean("systemDarkTheme")?.let { repo.saveSystemDarkTheme(it) }
        // **只能单向置真**：这是「已经生成过」的闩，不是用户偏好。
        // 云端的 false 不能把本地的 true 抹掉 —— 抹掉就意味着用户删过的内置助理
        // 会在下次冷启动时长回来，而"删了就没了"是这个功能的承诺。
        if (o.boolean("claudeSeeded") == true && repo.currentPreferences()[KEY_CLAUDE_SEEDED] != true) {
            repo.saveClaudeSeeded(true)
        }
        // 同上，单向置真
        if (o.boolean("claudeDeleted") == true && repo.currentPreferences()[KEY_CLAUDE_DELETED] != true) {
            repo.saveClaudeDeleted(true)
        }
    }

    // ============================================================
    //  推送前的合体
    // ============================================================

    /**
     * 推上去的必须是我们和云端的**并集**，不能只有我们这份。
     *
     * PUT 是**整对象替换**：只推自己那几个键，就会把云端那些安卓不认识的键
     * （网页端的 `sidebarWidth`、`sidebarRail`、`chatStreamWidth`…）从云端抹掉。
     * 网页端本地还留着一份，下一轮它又会推回来，然后安卓再抹一次 ——
     * 两边就这么永远推来推去，永远收敛不了。
     *
     * 所以设置这条路必须先取回云端那份，合完再推。多一次往返，换一个不会来回震荡的同步。
     * [ANDROID_PREFS] 那一层也照同样规矩合。
     */
    fun unionForPush(remote: JsonObject?, ours: JsonObject): JsonObject {
        if (remote == null) return ours
        val merged = JsonObject()
        for (k in remote.keySet()) merged.add(k, remote.get(k))
        for (k in ours.keySet()) {
            val mine = ours.get(k)
            if (k == ANDROID_PREFS && mine.isJsonObject) {
                val rem = remote.getAsJsonObject(ANDROID_PREFS)
                val sub = JsonObject()
                if (rem != null) for (x in rem.keySet()) sub.add(x, rem.get(x))
                for (x in mine.asJsonObject.keySet()) sub.add(x, mine.asJsonObject.get(x))
                merged.add(k, sub)
            } else {
                merged.add(k, mine)
            }
        }
        return merged
    }

    // ---- 小工具：类型不对就当没有，别抛异常 ----

    private fun JsonObject.int(k: String): Int? =
        get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

    private fun JsonObject.float(k: String): Float? =
        get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat

    private fun JsonObject.str(k: String): String? =
        get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.boolean(k: String): Boolean? =
        get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
}
