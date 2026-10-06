package com.freechat.core

import com.freechat.model.MemoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 对话记忆的纯逻辑层（1.0.91 M1 第二刀从 MemoryManager 平移，行为一字不变）。
 *
 * 分层语义（写入/检索策略的出处，动参数前先读）：
 *  - plot（主线/重大事件）：长期不淘汰、检索时始终注入（最新 [NORMAL_PLOT_LIMIT] 条）；
 *  - detail（细节）：关键词命中才注入，滚动淘汰；
 *  - 增强检索（highQuality）：不筛关键词，主线全量 + 最近细节**按时间线**整块注入，几乎不淘汰。
 *
 * 铁律（1.0.73 同义联想）：联想只提高「想起来」的概率，绝不提高「编出来」的概率——
 * 不到阈值一律不注入（「记错」比「忘了」更伤拟人感）。注入输出按时间线排列。
 * 存储不在这里：调用方把条目列表读进来、把结果写回去（Android=LocalStore，服务器=同步库）。
 */
object MemoryLogic {

    /** 相似度达到这个值就认为是「同一件事」 */
    const val DUP_THRESHOLD = 0.72

    const val NORMAL_TOTAL_CAP = 30
    const val NORMAL_PLOT_CAP = 20
    /** 增强检索：硬盘层几乎不淘汰 */
    const val HQ_TOTAL_CAP = 400
    const val HQ_PLOT_CAP = 300
    /** 增强检索单轮注入上限：主线 200 条 + 最近细节 60 条 */
    const val HQ_PLOT_LIMIT = 200
    const val HQ_DETAIL_LIMIT = 60
    /** 普通检索单轮注入上限 */
    const val NORMAL_PLOT_LIMIT = 8

    /** 去掉标点/空白/大小写差异后的字符二元组 Jaccard 相似度（短文本退化为一句话包含关系） */
    fun similarity(a: String, b: String): Double {
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

    /**
     * 追加一条记忆的纯逻辑：与已有条目高度相似时不新增，而是**改写那一条**
     * （保留它原有的日期与 id）——否则同一件事会被反复总结成好几条，
     * 注入时看起来就像「同一情节反复发生」。返回新列表。
     */
    fun upsert(list: List<MemoryEntry>, entry: MemoryEntry): List<MemoryEntry> {
        val dupIndex = list.indexOfFirst { similarity(it.summary, entry.summary) >= DUP_THRESHOLD }
        return if (dupIndex >= 0) {
            val old = list[dupIndex]
            val merged = old.copy(
                summary = entry.summary,                       // 用最新的说法
                keywords = (old.keywords + entry.keywords).distinct().take(12),
                // 日期留在「第一次知道这件事」的那天，时间线不会因为重复提及而漂移；
                // 但如果新条目带来了明确的「事件发生日期」，就用它
                eventDate = entry.eventDate.ifBlank { old.eventDate },
                kind = if (old.isPlot() || entry.isPlot()) "plot" else "detail",
                sourceMessageIds = (old.sourceMessageIds + entry.sourceMessageIds).distinct()
            )
            list.toMutableList().also { it[dupIndex] = merged }
        } else {
            list + entry
        }
    }

    /** 普通模式最多保留 30 条；增强检索几乎不清（只防无限膨胀），主线优先保留 */
    fun trim(list: List<MemoryEntry>, highQuality: Boolean): List<MemoryEntry> {
        val cap = if (highQuality) HQ_TOTAL_CAP else NORMAL_TOTAL_CAP
        if (list.size <= cap) return list
        val plotCap = if (highQuality) HQ_PLOT_CAP else NORMAL_PLOT_CAP
        val plot = list.filter { it.isPlot() }.takeLast(plotCap)
        val detailQuota = (cap - plot.size).coerceAtLeast(0)
        val detail = list.filter { !it.isPlot() }.takeLast(detailQuota)
        return (detail + plot).sortedBy { it.timestamp }
    }

    /** 注入前再杀一次重复（老档案里可能已经躺着同一件事的多条记录） */
    fun dedupe(entries: List<MemoryEntry>): List<MemoryEntry> {
        val kept = ArrayList<MemoryEntry>(entries.size)
        for (e in entries.sortedBy { it.timestamp }) {
            if (kept.none { similarity(it.summary, e.summary) >= DUP_THRESHOLD }) kept.add(e)
        }
        return kept
    }

    /** 记忆条目 → 带日期的文本：优先用「事件发生的日期」，没有就用「记下这条的日期」 */
    fun dated(e: MemoryEntry): String {
        val label = e.eventDate.ifBlank { SimpleDateFormat("yyyy年M月d日", Locale.CHINESE).format(Date(e.timestamp)) }
        return "[$label] ${e.summary}"
    }

    /**
     * 根据用户输入搜索相关记忆，摘要拼接为一段上下文。
     * 硬盘层（主线/重大事件）始终注入（最新 [NORMAL_PLOT_LIMIT] 条）；细节层按命中评分注入。
     * [associationLevel] 同义联想档（1-10）：1-2 纯字面包含；3 档起叠相似度通道（档位越高阈值越宽）。
     */
    fun searchRelevant(
        all: List<MemoryEntry>, userInput: String,
        maxResults: Int = 5, plotLimit: Int = NORMAL_PLOT_LIMIT, associationLevel: Float = 1f
    ): String {
        if (all.isEmpty()) return ""

        val inputLower = userInput.lowercase()
        // 硬盘（主线/重大事件）：永远不忘，取最新 plotLimit 条
        val plot = all.filter { it.isPlot() }.takeLast(plotLimit)
        // 细节：字面包含为主（写入侧的别名扩展让「喵星人」这类词天然可包含命中），
        // 3 档起叠摘要相似度（长文本 Jaccard），阈值宁紧勿松
        val simThreshold = when {
            associationLevel >= 9f -> 0.22
            associationLevel >= 6f -> 0.28
            associationLevel >= 3f -> 0.35
            else -> -1.0
        }
        val scoredDetail = all.filter { !it.isPlot() }.map { entry ->
            var score = entry.keywords.count { kw -> inputLower.contains(kw.lowercase()) } * 2f
            if (simThreshold > 0 && similarity(inputLower, entry.summary.lowercase()) >= simThreshold) score += 1.5f
            entry to score
        }.filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }

        val combined = dedupe((plot + scoredDetail)).distinctBy { it.id }.sortedBy { it.timestamp }
        if (combined.isEmpty()) return ""
        return combined.joinToString("\n") { "- ${dated(it)}" }
    }

    /** 增强检索：不做缩略、不做关键词筛，主线全量 + 最近细节按时间线整块注入 */
    fun buildHighQualityContext(all: List<MemoryEntry>): String {
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

    /** 构建记忆系统提示词片段（记忆是「增强」不是「必需」：出任何岔子都只等于本轮没有记忆，调用方兜底空串） */
    fun buildMemoryContext(all: List<MemoryEntry>, userInput: String, highQuality: Boolean = false, associationLevel: Float = 1f): String {
        return if (!highQuality) {
            val memories = searchRelevant(all, userInput, associationLevel = associationLevel)
            if (memories.isEmpty()) "" else "你对这个用户已知的信息（方括号里是这条信息对应的日期）：\n$memories"
        } else {
            buildHighQualityContext(all)
        }
    }

    /** 摘录器给不出关键词表时的老办法切词 */
    fun extractKeywords(text: String): List<String> {
        return text.split(Regex("[\\s，。！？,.!?、；：\"'（）()\\[\\]【】]+"))
            .filter { it.length in 2..8 }.distinct().take(8)
    }

    /**
     * 校验并转换模型给的事件日期 "2026-09-03" → "2026年9月3日"；不合规一律返回空串（宁可不标也不标错）。
     * [freeform]（叙事档，1.0.73）：世界历法日期是自由文本（「大雍三年三月初五」），只做假名/长度守卫，
     * 不强制 YYYY-MM-DD。
     */
    fun normalizeEventDate(raw: String, freeform: Boolean = false): String = try {
        if (freeform) {
            val t = raw.trim().take(24)
            if (t.isEmpty() || t.any { it in '぀'..'ヿ' }) "" else t
        } else {
            val text = raw.trim().take(10)
            if (!Regex("""^\d{4}-\d{1,2}-\d{1,2}$""").matches(text)) "" else {
                val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                val d = f.parse(text)
                val year = SimpleDateFormat("yyyy", Locale.US).format(d).toIntOrNull() ?: 0
                if (year !in 1970..2100) "" else SimpleDateFormat("yyyy年M月d日", Locale.CHINESE).format(d)
            }
        }
    } catch (_: Exception) { "" }
}
