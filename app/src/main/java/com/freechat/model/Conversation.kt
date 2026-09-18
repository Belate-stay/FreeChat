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
    val characterProfile: CharacterProfile? = null,  // 拟人陪伴模式的人物档案
    /**
     * 这个对话的**系统级提示词** —— 用户在「新规则」页底部自己写的。
     *
     * 跟拟人模式的「世界规则」是两回事，别混：那个是角色档案的一部分，只在情感陪伴里生效；
     * 这个是"这条对话按什么规矩来"，**两种模式都注入** —— 用户写了"只回英文"或者
     * "每次先给结论"，不该因为换了个模式就失效。
     *
     * 之所以挂在 [Conversation] 上、而不是塞进每对话设置那张表：它是这条对话的内容本身。
     * 对话同步到另一台设备时，规矩得跟着一起过去；而每对话设置那张表是本机的等价物。
     */
    val rules: String = "",
    /**
     * 非空表示这是内置对话，值是内置项的标记（见 [com.freechat.data.BuiltInAssistant.KEY]）。
     *
     * 除了系统提示词里换成它自己的人设之外，它和普通对话没有任何区别 ——
     * 能改名、能删、能同步。**删了就真的没了**：要不要重建记在设置的 `claude_seeded` 上，
     * 那个键置真之后不再回退，所以不会下次启动又长出来。
     */
    val builtInAssistant: String = ""
)

/** 记忆条目 — 来自一次对话的精简总结 */
data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val summary: String,         // 总结内容
    val keywords: List<String>,  // 关键词（便于搜索匹配）
    val timestamp: Long = System.currentTimeMillis(),
    val kind: String = "detail", // "plot"=剧情主线/重大事件（硬盘，长期始终注入）；"detail"=普通细节（滚动）
    /** 这件事本身发生在哪一天（"2026年9月3日"）；用户没说清就留空，注入时退回 timestamp 那天。
     *  增强检索模式下由摘录器填写——「上周三去了南京」这种，日期是上周三而不是今天。 */
    val eventDate: String = ""
) {
    /** 是否为「硬盘」层记忆（剧情主线/重大事件）：长期不淘汰、检索时始终注入 */
    fun isPlot(): Boolean = kind == "plot"
}

/** 每对话「新规则」覆盖设置：字段为 null 表示该对话未定制、跟随全局默认；非 null 则覆盖全局 */
data class PerConvSettings(
    val languageModelId: String? = null,
    val visualModelId: String? = null,
    val visionModelId: String? = null,
    /**
     * 语音合成 / 语音识别模型（1.0.50 补）。
     *
     * 语音原本是**全 App 共用**的一项（只有设置页那一份），「新规则」里选它会连带改掉别的对话 ——
     * 那是这一页唯一一处「改了会影响全局」的地方，与「新规则＝只管这条对话」的语义冲突，所以补上这两个字段。
     * 老档案里没有这两个键：类型是 `String?` 且读取处一律 `?.let`/`?:`，「缺键＝null＝跟随全局」正好是想要的语义。
     */
    val ttsModelId: String? = null,
    val asrModelId: String? = null,
    val enableWebSearch: Boolean? = null,
    val showThinking: Boolean? = null,
    val autoSummarizeMemory: Boolean? = null,
    val tempModeOrdinal: Int? = null,
    val lengthModeOrdinal: Int? = null
)

/** 侧滑搜索的单条结果：一条命中关键词的消息（含上下文预览与关键词高亮范围） */
data class SearchResultItem(
    val conversation: Conversation,
    val messageId: String,
    val preview: String,      // 关键词前后文字预览（单行，截断处加省略号）
    val matchStart: Int,      // 关键词在 preview 中的起始下标（用于标红）
    val matchEnd: Int,        // 关键词在 preview 中的结束下标
    val timestamp: Long       // 消息发送时间
)
