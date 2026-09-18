package com.freechat.data

import android.util.Log
import com.freechat.model.MemoryEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 对话记忆系统 —— 「硬盘 + 内存」分层：
 *
 * - 硬盘（记忆文件）：长期记忆，分两层
 *   - 主线/重大事件（kind="plot"）：剧情走向、关系转变、关键承诺，长期不淘汰、检索时始终注入
 *   - 细节（kind="detail"）：普通闲聊细节，可滚动淘汰，按关键词命中才注入
 * - 内存（短期上下文）：当前对话的近期原始消息，由 ViewModel 直接喂给 LLM
 *
 * 「增强检索」（highQuality）打开后换成另一套策略：
 *   - 不做关键词筛选，主线全量 + 最近细节**按时间顺序**整块注入，每条都带日期
 *   - 长期记忆不再 30 条就淘汰，写到硬盘上的记忆几乎不清
 *   - 写入前做相似度去重：同一件事反复被总结时改写旧条目而不是堆新条目
 *   （重复条目 + 没有日期，正是「时间混乱、情节复现」的两个根源）
 */
class MemoryManager {

    private val gson = Gson()

    // 文件位置与读写一律走 LocalStore：它持进程级锁、写盘是原子的，
    // 而且写完会通知同步引擎「这个对话的记忆变了」。这里自己 File.writeText
    // 就等于绕开了那把锁 —— 同步把云端记忆落盘的同时，这一轮总结正在写同一个文件。
    private fun memoryFile(convId: String): File = LocalStore.memoryFile(convId)

    /** 读取某对话的全部记忆（读出来一律先过 [healed] 补全老档案，见那里的说明） */
    fun load(convId: String): List<MemoryEntry> = try {
        val text = LocalStore.readText(memoryFile(convId))
        if (text != null) {
            val type = object : TypeToken<List<MemoryEntry>>() {}.type
            gson.fromJson<List<MemoryEntry>>(text, type)?.map { it.healed() } ?: emptyList()
        } else emptyList()
    } catch (e: Exception) {
        Log.e("MemoryManager", "Failed to load memory", e)
        emptyList()
    }

    // 补全老档案用的是同包顶层的 MemoryEntry.healed()（见 AppJson.kt）——
    // 和云端拉下来的记忆走同一份实现，不再各写一份

    /**
     * 追加一条记忆。
     * 与已有条目高度相似时不新增，而是**改写那一条**（保留它原有的日期与 id）——
     * 否则同一件事会被反复总结成好几条，注入时看起来就像「同一情节反复发生」。
     * [highQuality] 为真时不做 30 条的激进淘汰。
     */
    fun append(convId: String, entry: MemoryEntry, highQuality: Boolean = false) {
        try {
            // 整段读-改-写包在锁里：中间被云端同步插进来一条记忆，这份结果就会把它盖掉。
            // 锁可重入，里面那些 readText/writeText 自己再拿一次没问题。
            LocalStore.locked {
                val list = load(convId).toMutableList()
                val dupIndex = list.indexOfFirst { similarity(it.summary, entry.summary) >= DUP_THRESHOLD }
                if (dupIndex >= 0) {
                    val old = list[dupIndex]
                    list[dupIndex] = old.copy(
                        summary = entry.summary,                       // 用最新的说法
                        keywords = (old.keywords + entry.keywords).distinct().take(12),
                        // 日期留在「第一次知道这件事」的那天，时间线不会因为重复提及而漂移；
                        // 但如果新条目带来了明确的「事件发生日期」，就用它
                        eventDate = entry.eventDate.ifBlank { old.eventDate },
                        kind = if (old.isPlot() || entry.isPlot()) "plot" else "detail"
                    )
                } else {
                    list.add(entry)
                }
                LocalStore.writeText(memoryFile(convId), gson.toJson(trim(list, highQuality)))
            }
        } catch (e: Exception) {
            Log.e("MemoryManager", "Failed to save memory", e)
        }
    }

    /** 删除对话记忆 */
    fun delete(convId: String) {
        LocalStore.deleteFile(memoryFile(convId))
    }

    /**
     * 删除 [sinceMs] 之后写入的记忆条目，返回删掉几条。
     *
     * 用途：叙事单发模式下改写最后一条提示词重发时，那一轮（旧提示词 + 旧回复）整轮作废，
     * 由它总结出来的记忆也必须一并撤掉——否则用户明明把那句话改掉了，AI 后面还是会
     * 记得「他当时说过 XXX」，就是「旧提示词被删除且不计入记忆」没做到的典型表现。
     *
     * 判定依据是**写入时间**（[MemoryEntry.timestamp]，即总结完成落盘的时刻）：
     * 一轮回复的记忆一定在该轮用户消息之后才写盘，所以「晚于这条用户消息」的条目就属于
     * 这一轮及之后。理论上的边界情况是上一轮的总结异步还没跑完、用户已经发了下一条——
     * 此时上一轮的记忆会被顺带删掉。叙事单发模式下两条消息之间必然隔着一整轮回复，
     * 撞上这个窗口的概率极低，且删掉的也只是上一轮的一条摘要，可以接受。
     */
    fun removeSince(convId: String, sinceMs: Long): Int {
        return try {
            LocalStore.locked {
                val list = load(convId)
                val kept = list.filter { it.timestamp < sinceMs }
                val removed = list.size - kept.size
                if (removed > 0) LocalStore.writeText(memoryFile(convId), gson.toJson(kept))
                removed
            }
        } catch (e: Exception) {
            Log.e("MemoryManager", "Failed to remove recent memory", e)
            0
        }
    }

    /**
     * 根据用户输入搜索相关记忆，摘要拼接为一段上下文。
     * 硬盘层（主线/重大事件）始终注入（最新 8 条）；细节层按关键词命中注入。
     */
    fun searchRelevant(convId: String, userInput: String, maxResults: Int = 5, plotLimit: Int = 8): String {
        val all = load(convId)
        if (all.isEmpty()) return ""

        val inputLower = userInput.lowercase()
        // 硬盘（主线/重大事件）：永远不忘，取最新 plotLimit 条
        val plot = all.filter { it.isPlot() }.takeLast(plotLimit)
        // 细节：关键词命中才注入
        val scoredDetail = all.filter { !it.isPlot() }.map { entry ->
            val matchCount = entry.keywords.count { kw -> inputLower.contains(kw.lowercase()) }
            entry to matchCount
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }

        val combined = dedupe((plot + scoredDetail)).distinctBy { it.id }
        if (combined.isEmpty()) return ""
        return combined.joinToString("\n") { "- ${dated(it)}" }
    }

    /** 构建用于 API 的记忆系统提示词片段（记忆是「增强」不是「必需」：这里出任何岔子都只等于本轮没有记忆） */
    fun buildMemoryContext(convId: String, userInput: String, highQuality: Boolean = false): String = try {
        if (!highQuality) {
            val memories = searchRelevant(convId, userInput)
            if (memories.isEmpty()) "" else "你对这个用户已知的信息（方括号里是这条信息对应的日期）：\n$memories"
        } else {
            buildHighQualityContext(convId)
        }
    } catch (e: Exception) {
        // 兜底：这段代码曾经把整个拟人模式拖垮过 —— 老档案里 eventDate 为 null 时 NPE，
        // 异常在拼请求之前抛出，用户看到的是「请求失败」，其实请求根本没发出去。
        // 记忆注入失败该退化成「这一轮没有记忆」，绝不能连回复一起拖死。
        Log.e("MemoryManager", "buildMemoryContext failed，本轮跳过记忆注入", e)
        ""
    }

    /** 增强检索：不做缩略、不做关键词筛，主线全量 + 最近细节按时间线整块注入 */
    private fun buildHighQualityContext(convId: String): String {
        val all = load(convId)
        if (all.isEmpty()) return ""
        val plot = all.filter { it.isPlot() }.takeLast(HQ_PLOT_LIMIT)
        val detail = all.filter { !it.isPlot() }.takeLast(HQ_DETAIL_LIMIT)
        val timeline = dedupe(plot + detail).sortedBy { it.timestamp }
        if (timeline.isEmpty()) return ""
        return "【长期记忆·完整档案——按时间顺序排列，最后一条最新】\n" +
            "方括号里是这件事的日期。回答前先对着时间线想清楚：\n" +
            "1. 已经发生过的事就是过去了，不要当成刚发生的新事，也不要重新演绎一遍同样的情节。\n" +
            "2. 同一件事只会出现一次（如果涉及时间或约定的变更，以最新那条为准）。\n" +
            "3. 时间、地点、人物、因果都按这里的记录来，不要记错、不要张冠李戴、更不要脑补记录里没有的事。\n" +
            timeline.joinToString("\n") { "- ${dated(it)}" }
    }

    /** 记忆条目 → 带日期的文本：优先用「事件发生的日期」，没有就用「记下这条的日期」 */
    private fun dated(e: MemoryEntry): String {
        val label = e.eventDate.ifBlank { dayFormat.format(Date(e.timestamp)) }
        return "[$label] ${e.summary}"
    }

    /** 注入前再杀一次重复（老档案里可能已经躺着同一件事的多条记录） */
    private fun dedupe(entries: List<MemoryEntry>): List<MemoryEntry> {
        val kept = ArrayList<MemoryEntry>(entries.size)
        for (e in entries.sortedBy { it.timestamp }) {
            if (kept.none { similarity(it.summary, e.summary) >= DUP_THRESHOLD }) kept.add(e)
        }
        return kept
    }

    /** 普通模式最多保留 30 条；增强检索几乎不清（只防无限膨胀），主线优先保留 */
    private fun trim(list: List<MemoryEntry>, highQuality: Boolean): List<MemoryEntry> {
        val cap = if (highQuality) HQ_TOTAL_CAP else NORMAL_TOTAL_CAP
        if (list.size <= cap) return list
        val plotCap = if (highQuality) HQ_PLOT_CAP else NORMAL_PLOT_CAP
        val plot = list.filter { it.isPlot() }.takeLast(plotCap)
        val detailQuota = (cap - plot.size).coerceAtLeast(0)
        val detail = list.filter { !it.isPlot() }.takeLast(detailQuota)
        return (detail + plot).sortedBy { it.timestamp }
    }

    companion object {
        /** 相似度达到这个值就认为是「同一件事」 */
        private const val DUP_THRESHOLD = 0.72

        private const val NORMAL_TOTAL_CAP = 30
        private const val NORMAL_PLOT_CAP = 20
        /** 增强检索：硬盘层几乎不淘汰 */
        private const val HQ_TOTAL_CAP = 400
        private const val HQ_PLOT_CAP = 300
        /** 增强检索单轮注入上限：主线 200 条 + 最近细节 60 条 */
        private const val HQ_PLOT_LIMIT = 200
        private const val HQ_DETAIL_LIMIT = 60

        private val dayFormat = SimpleDateFormat("yyyy年M月d日", Locale.CHINESE)

        /** 去掉标点/空白/大小写差异后的字符二元组 Jaccard 相似度（短文本退化为一句话包含关系） */
        private fun similarity(a: String, b: String): Double {
            val x = a.lowercase().filter { it.isLetterOrDigit() }
            val y = b.lowercase().filter { it.isLetterOrDigit() }
            if (x.isEmpty() || y.isEmpty()) return 0.0
            if (x == y) return 1.0
            if (x.length <= 8 || y.length <= 8) {
                return if (x.contains(y) || y.contains(x)) 0.9 else 0.0
            }
            val gx = HashSet<String>(x.length)
            val gy = HashSet<String>(y.length)
            for (i in 0 until x.length - 1) gx.add(x.substring(i, i + 2))
            for (i in 0 until y.length - 1) gy.add(y.substring(i, i + 2))
            var inter = 0
            for (g in gx) if (g in gy) inter++
            val union = gx.size + gy.size - inter
            return if (union == 0) 0.0 else inter.toDouble() / union
        }
    }
}
