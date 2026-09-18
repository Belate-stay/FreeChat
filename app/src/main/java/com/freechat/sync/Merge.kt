package com.freechat.sync

import com.freechat.data.AppJson
import com.freechat.model.CharacterProfile
import com.freechat.model.ChatMode
import com.freechat.model.Conversation
import com.freechat.model.MemoryEntry
import com.freechat.model.Message

/**
 * 冲突合并 —— **纯函数，不碰磁盘**，方便单独推理。
 *
 * 这是 `FreeChatWeb/src/core/sync/merge.ts` 的逐条移植。两端的合并策略必须**完全一致**，
 * 否则同一场冲突在网页上和在手机上会合出两个不同的结果，然后互相推来推去永远收敛不了。
 *
 * 总原则：**宁可多留，不可少留**。服务端只会报 409，永远不会替用户做覆盖，
 * 怎么合是这里的责任。
 *
 *  - **消息 / 记忆**：本质是「按条追加」。两台设备各聊各的，合起来 = 全部，
 *    按 id 取并集按时间排，一个字都不会丢，也就不需要冲突副本。
 *
 *  - **角色设定**：最难的一个。**不能**按对话的 `updatedAt` 判断谁新 ——
 *    那个字段会被「聊天」不停顶掉：一台设备刚聊过天（updatedAt 很新）但设定是旧的，
 *    另一台只改了设定没聊天（updatedAt 旧），比大小就会把新改的设定丢掉。
 *    既然判断不了谁新，就两份都留。
 *
 *  - **设置**：纯偏好，以云端为准（见 [SettingsBridge]）。
 */
object Merge {

    // ============================================================
    //  工具
    // ============================================================

    /**
     * 键序无关的比较。
     *
     * 直接拿字符串比是**不行**的：Gson 序列化一个 Map/对象时键的顺序不保证，
     * 同一条对话本地序列化一遍和云端解析再序列化一遍，键序可能不同 ——
     * 那会被判成「不一样」，于是每轮同步都白发一次 PUT。
     */
    fun sameValue(a: Any?, b: Any?): Boolean =
        Wire.stableJson(AppJson.gson.toJsonTree(a)) == Wire.stableJson(AppJson.gson.toJsonTree(b))

    // ============================================================
    //  消息
    // ============================================================

    /**
     * 同 id 两条时留哪条。
     *
     * 先看是不是正在流式输出：**没在流的那条更完整** —— 另存一版正在吐字吐到一半的，
     * 会把已经收完的完整回复换成一个残句。
     * 都完整就留内容长的（正文 + 思考过程一起算）；还是一样长就留远端那条。
     */
    private fun betterMessage(a: Message, b: Message): Message {
        if (a.isStreaming != b.isStreaming) return if (a.isStreaming) b else a
        val la = a.content.length + a.reasoningContent.length
        val lb = b.content.length + b.reasoningContent.length
        if (la != lb) return if (la > lb) a else b
        return b
    }

    private fun cmpMessage(a: Message, b: Message): Int {
        if (a.timestamp != b.timestamp) return a.timestamp.compareTo(b.timestamp)
        return a.id.compareTo(b.id)
    }

    /** 按 id 取并集并按时间排序 —— 两台设备各聊几句，合起来就是全部 */
    fun mergeMessages(local: List<Message>, remote: List<Message>): List<Message> {
        val byId = LinkedHashMap<String, Message>(local.size + remote.size)
        for (m in local) if (m.id.isNotEmpty()) byId[m.id] = m
        for (m in remote) {
            if (m.id.isEmpty()) continue
            val prev = byId[m.id]
            byId[m.id] = if (prev != null) betterMessage(prev, m) else m
        }
        return byId.values.sortedWith { a, b -> cmpMessage(a, b) }
    }

    // ============================================================
    //  记忆
    // ============================================================

    /**
     * 同 id 取远端：记忆一旦生成就不再改动，两边同 id 必然内容一致，取谁都一样。
     * （真要是内容不同，说明两边各自总结出了同一个 id —— 不可能，id 是随机 UUID。）
     */
    fun mergeMemories(local: List<MemoryEntry>, remote: List<MemoryEntry>): List<MemoryEntry> {
        val byId = LinkedHashMap<String, MemoryEntry>(local.size + remote.size)
        for (m in local) if (m.id.isNotEmpty()) byId[m.id] = m
        for (m in remote) if (m.id.isNotEmpty()) byId[m.id] = m
        return byId.values.sortedBy { it.timestamp }
    }

    // ============================================================
    //  对话
    // ============================================================

    /** 设定冲突时另起的那条对话：只带设定，不带消息历史 */
    data class Alternate(val title: String, val mode: ChatMode, val profile: CharacterProfile)

    data class ConvMerge(val conv: Conversation, val alternate: Alternate?)

    fun mergeConv(local: Conversation, remote: Conversation): ConvMerge {
        val lp = local.characterProfile
        val rp = remote.characterProfile
        val profileDiffers = !sameValue(lp, rp)

        // 只有一边有设定 → 不是冲突，是有设定的那份更新（比如刚建好角色还没同步过）
        if (!profileDiffers || lp == null || rp == null) {
            val newer = if (remote.updatedAt >= local.updatedAt) remote else local
            return ConvMerge(newer.copy(characterProfile = lp ?: rp), null)
        }

        val newer = if (remote.updatedAt >= local.updatedAt) remote else local
        val older = if (newer === remote) local else remote

        return ConvMerge(
            conv = newer,
            alternate = Alternate(
                title = older.title.ifBlank { newer.title } +
                    com.freechat.i18n.LocaleManager.strings().conflictCopySuffix,
                mode = older.mode,
                profile = older.characterProfile ?: lp
            )
        )
    }
}
