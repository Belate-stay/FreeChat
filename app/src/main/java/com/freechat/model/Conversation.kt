package com.freechat.model

import java.util.UUID

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val isPinned: Boolean = false,
    val mode: ChatMode = ChatMode.STANDARD,
    val characterProfile: CharacterProfile? = null  // 拟人陪伴模式的人物档案
)

/** 记忆条目 — 来自一次对话的精简总结 */
data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val summary: String,         // 总结内容
    val keywords: List<String>,  // 关键词（便于搜索匹配）
    val timestamp: Long = System.currentTimeMillis(),
    val importance: Boolean = false,  // 重要记忆（关系转变/关键承诺等），不随数量上限淘汰
    val kind: String = "detail"       // "plot"=剧情主线/重大事件（硬盘，长期始终注入）；"detail"=普通细节（滚动）
) {
    /** 是否为「硬盘」层记忆（剧情主线/重大事件）：长期不淘汰、检索时始终注入 */
    fun isPlot(): Boolean = kind == "plot" || importance
}

/** 每对话「新规则」覆盖设置：字段为 null 表示该对话未定制、跟随全局默认；非 null 则覆盖全局 */
data class PerConvSettings(
    val languageModelId: String? = null,
    val visualModelId: String? = null,
    val visionModelId: String? = null,
    val enableWebSearch: Boolean? = null,
    val showThinking: Boolean? = null,
    val autoSummarizeMemory: Boolean? = null,
    val tempModeOrdinal: Int? = null,
    val lengthModeOrdinal: Int? = null
)
