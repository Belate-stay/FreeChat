package com.freechat.sync

import com.freechat.model.LengthMode
import com.freechat.model.PerConvSettings
import com.freechat.model.TempMode
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * 每对话「新规则」↔ 线上格式。
 *
 * ## 只同步一半，另一半**永远不出这台设备**
 *
 * [PerConvSettings] 里是两类完全不同的东西，混在一起传是把两头都做坏：
 *
 * | 字段 | 去哪 |
 * |---|---|
 * | `enableWebSearch` / `showThinking` / `autoSummarizeMemory` / `tempMode` / `lengthMode` | **同步** —— 它们是这条对话「怎么回话」的规矩，换台设备该照旧生效 |
 * | `languageModelId` / `visualModelId` / `visionModelId` / `ttsModelId` / `asrModelId` | **不同步** —— 存的是**本机**自定义模型的 id，另一台上没有这个模型，同步过去只会选中一个点不通的项 |
 *
 * 这跟 `SettingsBridge` 里「模型选择不同步」是同一条理由，只是这里还多一层：
 * 同一个账号下网页端和安卓端**各自的模型库根本不一样**，传过去连"点不通"都算不上，
 * 是纯粹的脏数据。
 *
 * ## 缺席 = 跟随全局
 *
 * 这一层用的是三态：**键不在 = 这条对话没定制这一项**（跟随全局默认），
 * 键在且是 false = 用户明确关掉了。所以推的时候 `null` 的键要**整个省掉**，
 * 不能补个 false 上去 —— 那会把「跟随全局」偷偷变成「明确关闭」，
 * 用户在另一台设备上改全局开关时这条对话就不跟着动了。
 *
 * 也因此拉下来时是**整份替换那半**：远端没有的键就是"没定制"，本地也得跟着回到没定制。
 */
object PerConvBridge {

    // 名字跟全局设置那张表（[SettingsBridge]）**故意用同一套** —— 同一件事在两端、
    // 在两个同步对象里都该叫同一个名字，否则以后加字段时两处对不上没人看得出来。
    const val K_WEB_SEARCH = "enableWebSearch"
    const val K_SHOW_THINKING = "showThinking"
    const val K_AUTO_MEM = "autoSummarizeMemory"
    const val K_TEMP = "tempMode"
    const val K_LENGTH = "lengthMode"

    /** 参与同步的键。**这里就是白名单** —— 不在表里的字段（那几个模型 id）连读都不会去读 */
    private val SYNCED = listOf(K_WEB_SEARCH, K_SHOW_THINKING, K_AUTO_MEM, K_TEMP, K_LENGTH)

    // ============================================================
    //  本地 → 线上
    // ============================================================

    /** 这台设备认得的、且参与同步的那半（null 的键整个省掉，见类注释） */
    fun toWire(s: PerConvSettings): JsonObject {
        val o = JsonObject()
        s.enableWebSearch?.let { o.add(K_WEB_SEARCH, JsonPrimitive(it)) }
        s.showThinking?.let { o.add(K_SHOW_THINKING, JsonPrimitive(it)) }
        s.autoSummarizeMemory?.let { o.add(K_AUTO_MEM, JsonPrimitive(it)) }
        s.tempModeOrdinal?.let { i -> TempMode.entries.getOrNull(i) }
            ?.let { o.add(K_TEMP, JsonPrimitive(SettingsBridge.tempModeWire(it))) }
        s.lengthModeOrdinal?.let { i -> LengthMode.entries.getOrNull(i) }
            ?.let { o.add(K_LENGTH, JsonPrimitive(SettingsBridge.lengthModeWire(it))) }
        return o
    }

    /** 同上，但接受"这条对话压根没有定制"（null）—— 用来比"到底变没变" */
    fun syncedHalf(s: PerConvSettings?): JsonObject = if (s == null) JsonObject() else toWire(s)

    // ============================================================
    //  线上 → 本地
    // ============================================================

    /**
     * 把云端那半套回本地这条：**只有这 5 项以云端为准**，模型 id 那半原样不动。
     *
     * `Boolean`/枚举解析不出来就当"没定制"（置 null），不保留本地旧值 ——
     * 因为远端那份是**整份**状态，它没有的键就是"那边也没定制"。
     * 换成"解析失败就留旧值"的话，另一台设备上取消的定制在这台永远取消不掉。
     */
    fun mergeIntoLocal(local: PerConvSettings, remote: JsonObject): PerConvSettings = local.copy(
        enableWebSearch = remote.bool(K_WEB_SEARCH),
        showThinking = remote.bool(K_SHOW_THINKING),
        autoSummarizeMemory = remote.bool(K_AUTO_MEM),
        tempModeOrdinal = remote.str(K_TEMP)?.let { SettingsBridge.tempModeFromWire(it) }?.ordinal,
        lengthModeOrdinal = remote.str(K_LENGTH)?.let { SettingsBridge.lengthModeFromWire(it) }?.ordinal
    )

    /** 推之前先并上云端那份：PUT 是整对象替换，丢掉不认识的键就会和别的端来回抹（同 [SettingsBridge.unionForPush]） */
    fun unionForPush(remote: JsonObject?, ours: JsonObject): JsonObject {
        val merged = JsonObject()
        if (remote != null) {
            for (k in remote.keySet()) merged.add(k, remote.get(k))
            // 我们**明确没定制**的那些键要删掉，不能留在云端那份里 —— 见类注释「缺席 = 跟随全局」
            for (k in SYNCED) if (!ours.has(k)) merged.remove(k)
        }
        for (k in ours.keySet()) merged.add(k, ours.get(k))
        return merged
    }

    private fun JsonObject.bool(k: String): Boolean? =
        get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

    private fun JsonObject.str(k: String): String? =
        get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
