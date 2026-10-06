package com.freechat.core

import com.freechat.data.healed
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.freechat.model.PerConvSettings

/**
 * 删除墓碑的纯过滤逻辑（1.0.91 M1 第二刀从 MessageDeletion 平移，行为一字不变）。
 *
 * 语义合同（Beta811/DeletionAndSceneTest 钉死，动之前先跑那两套）：
 *  · 文本墓碑与视觉墓碑**分账**：删场景图绝不作废剧情记忆/氛围（视觉墓碑是文本墓碑的子集）；
 *  · 派生记忆任一来源被删即整条失效；**无可追溯来源的 legacy 记忆在有删除时一并清除**（防残留已删文本）；
 *  · 氛围快照来源被删或无来源（且存在删除）时整组清零。
 * 墓碑集合的存取（内存 map + 冷启动回放）在各端的状态层，这里只做纯过滤。
 */
object DeletionLogic {

    /** 视觉删除只影响视觉行本身——作废记忆的只有文本墓碑 */
    fun memoryDeletionIds(deletedIds: Set<String>, deletedVisualIds: Set<String>): Set<String> = deletedIds - deletedVisualIds

    fun filterMessages(rows: List<Message>, deleted: Set<String>): List<Message> =
        if (deleted.isEmpty()) rows else rows.filterNot { it.id in deleted }

    fun filterMemories(rows: List<MemoryEntry>, deleted: Set<String>): List<MemoryEntry> =
        if (deleted.isEmpty()) rows else rows.filter { m ->
            // Legacy summaries have no provenance. Retaining one risks retaining deleted text.
            m.sourceMessageIds.isNotEmpty() && m.sourceMessageIds.none { it in deleted }
        }

    fun atmosphere(settings: PerConvSettings, memoryDeletionIds: Set<String>): PerConvSettings {
        val safe = settings.healed()
        return if (memoryDeletionIds.isNotEmpty() && (safe.atmosphereSourceMessageIds.isEmpty() ||
            safe.atmosphereSourceMessageIds.any { it in memoryDeletionIds }))
            safe.copy(lastMood = "", moodAtMs = 0, atmosphere = "", warmth = 0, atmosphereSourceMessageIds = emptyList())
        else safe
    }
}
