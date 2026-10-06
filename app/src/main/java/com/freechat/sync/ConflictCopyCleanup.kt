package com.freechat.sync

import com.freechat.model.Conversation

/**
 * 1.0.99.3：存量「冲突副本」套娃清理（纯函数，调用方负责删除）。
 *
 * 背景（见 [Merge.samePersona]）：1.0.99.2 及以前的冲突判定拿整份人设比，头像指纹等
 * wire 往返字段永远对不齐，每轮同步都误判冲突造一份副本；副本自己再走一趟 wire 又失配，
 * 于是副本的副本层层套娃（标题「（冲突副本）（冲突副本）…」），云端 seq 递增又把每份
 * 拉回来再判一次 —— 登录几分钟能长出十几条。
 *
 * 清理口径（用户拍板「自动清理：保留原对话和最多一份干净副本」）：
 *  · **有聊天记录的一律不删** —— 数据安全压过整洁，哪怕它是套娃；
 *  · 嵌套深度 ≥2 的（套娃产物）删；
 *  · 深度 =1 的干净副本按组**最多留一份**（优先有记录的、其次与原对话人设真不同的、再次最新的）；
 *  · 无后缀的原对话永远不动。
 */
object ConflictCopyCleanup {

    /** 三个语言版本的后缀都认 —— 副本是当时语言下造的，清理时语言未必还是那个 */
    private val SUFFIXES = listOf("（冲突副本）", "（衝突副本）", " (conflict copy)")

    data class TitleInfo(val base: String, val depth: Int)

    fun analyze(title: String): TitleInfo {
        var base = title
        var depth = 0
        while (true) {
            val s = SUFFIXES.firstOrNull { base.endsWith(it) } ?: break
            base = base.dropLast(s.length)
            depth++
        }
        return TitleInfo(base, depth)
    }

    /**
     * @param hasMessages 这条对话有没有聊天记录（调用方按 id 查消息文件）
     * @return 可安全删除的对话 id 列表
     */
    fun staleCopies(convs: List<Conversation>, hasMessages: (Conversation) -> Boolean): List<String> {
        val stale = mutableListOf<String>()
        for ((_, group) in convs.groupBy { analyze(it.title).base }) {
            val copies = group.filter { analyze(it.title).depth > 0 }
            if (copies.isEmpty()) continue
            val original = group.firstOrNull { analyze(it.title).depth == 0 }
            for (c in copies.filter { analyze(it.title).depth >= 2 }) {
                if (!hasMessages(c)) stale += c.id
            }
            val clean = copies.filter { analyze(it.title).depth == 1 }
            val keep = clean.sortedWith(
                compareByDescending<Conversation> { hasMessages(it) }
                    .thenByDescending { !Merge.samePersona(original?.characterProfile, it.characterProfile) }
                    .thenByDescending { it.updatedAt }
            ).firstOrNull()
            for (c in clean) {
                if (c.id != keep?.id && !hasMessages(c)) stale += c.id
            }
        }
        return stale
    }
}
