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
 * 追加与角色冲突沿用线上协议；Android 额外用 CONV 内的 grow-only 删除 ID
 * 过滤消息、派生记忆。删除意图优先于旧设备的追加并集，服务端仍保存原有 JSON 对象格式。
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

    /**
     * 人设冲突判定的比较面 —— **只比设备无关的内容**。
     *
     * 1.0.99.3 修「登录后疯狂长冲突副本（套娃）」的根因之一：原来拿整份 [CharacterProfile]
     * 直接 [sameValue]，但 wire 往返**必然**重写头像三件套（推上去按文件算 `avatarHash`、
     * 拉回来 [Wire.adoptAvatar] 落盘又写一版），设备本地字段也被 [Wire.convFromWire] 从
     * local 回填 —— 两边永远不相等，每轮同步都误判「两台设备各改了人设」造一份副本；
     * 副本自己再走一趟 wire 又失配 → 副本的副本，标题层层叠「（冲突副本）（冲突副本）」。
     *
     * 判定口径 = 这份人设在**云端长相**（wire 投影）是否不同：头像三件套、设备本地字段
     * （外貌参考图路径、模型 id）都不参与 —— 头像本体走 avatarData 同步、外貌参考图走图片
     * 同步，各有一条通道，不该在这里凑「人设不同」。
     */
    fun samePersona(a: CharacterProfile?, b: CharacterProfile?): Boolean {
        if (a == null || b == null) return a === b
        fun wireView(p: CharacterProfile) = p.copy(
            avatarPath = "", avatarHash = "",
            appearanceImagePath = "", appearanceImagePaths = emptyList(), appearanceImageDescs = emptyList(),
            languageModelId = "", visionModelId = "", visualModelId = "", enhancedSceneContinuity = false
        )
        return sameValue(wireView(a), wireView(b))
    }

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
    fun mergeMessages(local: List<Message>, remote: List<Message>, deleted: Set<String> = emptySet()): List<Message> {
        val byId = LinkedHashMap<String, Message>(local.size + remote.size)
        for (m in local) if (m.id.isNotEmpty()) byId[m.id] = m
        for (m in remote) {
            if (m.id.isEmpty()) continue
            val prev = byId[m.id]
            byId[m.id] = if (prev != null) betterMessage(prev, m) else m
        }
        return byId.values.filterNot { it.id in deleted }.sortedWith { a, b -> cmpMessage(a, b) }
    }

    // ============================================================
    //  记忆
    // ============================================================

    /**
     * 同 id 取远端：记忆一旦生成就不再改动，两边同 id 必然内容一致，取谁都一样。
     * （真要是内容不同，说明两边各自总结出了同一个 id —— 不可能，id 是随机 UUID。）
     */
    fun mergeMemories(local: List<MemoryEntry>, remote: List<MemoryEntry>, deleted: Set<String> = emptySet()): List<MemoryEntry> {
        val byId = LinkedHashMap<String, MemoryEntry>(local.size + remote.size)
        for (m in local) if (m.id.isNotEmpty()) byId[m.id] = m
        for (m in remote) if (m.id.isNotEmpty()) byId[m.id] = m
        return com.freechat.data.MessageDeletion.filterMemories(byId.values.toList(), deleted).sortedBy { it.timestamp }
    }

    // ============================================================
    //  对话
    // ============================================================

    /** 设定冲突时另起的那条对话：只带设定，不带消息历史 */
    data class Alternate(val title: String, val mode: ChatMode, val profile: CharacterProfile)

    data class ConvMerge(val conv: Conversation, val alternate: Alternate?)

    fun mergeConv(local: Conversation, remote: Conversation): ConvMerge {
        val deleted = (local.deletedMessageIds + remote.deletedMessageIds).distinct().sorted()
        val visualDeleted = (local.deletedVisualMessageIds + remote.deletedVisualMessageIds).filter { it in deleted }.distinct().sorted()
        val lp = local.characterProfile
        val rp = remote.characterProfile
        // 1.0.99.3：判定面收窄到「云端长相」（samePersona）——头像指纹/设备本地字段的
        // wire 往返差异不再误判成人设冲突，冲突副本不会每轮同步长一条（见 samePersona 注释）。
        val profileDiffers = !samePersona(lp, rp)

        // 只有一边有设定 → 不是冲突，是有设定的那份更新（比如刚建好角色还没同步过）
        if (!profileDiffers || lp == null || rp == null) {
            val newer = if (remote.updatedAt >= local.updatedAt) remote else local
            // 1.0.99.3：人设整体取 [newer] 那份 —— 外貌参考图现在是同步内容（img 引用），
            // 不能再固定保 lp，否则远端新加的外貌图会被本地这份旧的吞掉。
            // samePersona 已保证两边「云端长相」一致，设备本地字段在 convFromWire 里回填过。
            return ConvMerge(newer.copy(characterProfile = newer.characterProfile ?: lp ?: rp, deletedMessageIds = deleted, deletedVisualMessageIds = visualDeleted), null)
        }

        val newer = if (remote.updatedAt >= local.updatedAt) remote else local
        val older = if (newer === remote) local else remote

        return ConvMerge(
            conv = newer.copy(deletedMessageIds = deleted, deletedVisualMessageIds = visualDeleted),
            alternate = Alternate(
                // 1.0.99.3：后缀不叠加 ——「（冲突副本）（冲突副本）…」的套娃标题到此为止
                title = conflictCopyTitle(older.title.ifBlank { newer.title }),
                mode = older.mode,
                profile = older.characterProfile ?: lp
            )
        )
    }

    /** 冲突副本标题：已有后缀就不再叠一层（套娃标题根治） */
    fun conflictCopyTitle(base: String): String {
        val suffix = com.freechat.i18n.LocaleManager.strings().conflictCopySuffix
        return if (base.endsWith(suffix)) base else base + suffix
    }
}
