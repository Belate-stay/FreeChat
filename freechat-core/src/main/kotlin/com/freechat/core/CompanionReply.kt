package com.freechat.core

import com.freechat.model.SearchCitation

/**
 * 拟人回复的结构化结果与解析（1.0.91 M1 第三刀从 ChatViewModel 平移，行为一字不变）。
 * 解析只做纯文本侧（指令剥离/情绪标签/分条/方括号清洗）；「格式自检+重写」是独立 HTTP 小调用，留在端上。
 */

/** 拟人模式结构化回复：模型输出的情绪标签 + 分条回复 + 主动智能指令 */
data class CompanionReply(
    val emotion: String, val segments: List<String>, val proactive: ProactiveSignal? = null,
    val sources: List<SearchCitation> = emptyList()
)

/** [CompanionReplyParser.parse] 的产出：正文行 + 情绪 + 指令（sources 由端上合并检索证据后补） */
data class ParsedCompanionReply(
    val emotion: String, val bodyLines: List<String>, val proactive: ProactiveSignal?
)

object CompanionReplyParser {

    /**
     * 模型原文 → 结构化。顺序是行为的一部分：
     * ① 主动智能指令行先剥掉（它是指令不是正文，绝不能出现在气泡里）；
     * ② 叙事档不解析情绪标签、整段一条（空行仅排版）；
     * ③ 微信档解析首行情绪标签（标准 [情绪:开心]，兼容裸 [开心]，识别不了当正文留着）；
     * ④ 剥掉正文里漏出的裸方括号标签（[敷衍] 这类不是 emoji，绝不显示）。
     */
    fun parse(rawOrig: String, narrativeMode: Boolean): ParsedCompanionReply {
        val (raw, proactiveSignal) = stripProactiveDirective(rawOrig)

        val emotion: String
        val bodyLines: List<String>
        if (narrativeMode) {
            emotion = ""
            bodyLines = listOf(raw.trim()).filter { it.isNotEmpty() }
        } else {
            val lines = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val emotionTag = Regex("""^\s*\[(?:情绪|emotion)?\s*[:：]?\s*([^\]]+)]\s*$""")
            val first = lines.firstOrNull()
            val parsed = first?.let { emotionTag.find(it) }?.let { m ->
                val label = m.groupValues[1].trim()
                if (parseEmotionLabel(label) != null) label else null
            }
            emotion = parsed ?: ""
            val rawBodyLines = if (parsed != null) lines.drop(1) else lines
            val bracketToken = Regex("""\[[^\]]{1,6}]\s*""")
            bodyLines = rawBodyLines.map { it.replace(bracketToken, "").trim() }.filter { it.isNotEmpty() }
        }
        return ParsedCompanionReply(emotion, bodyLines, proactiveSignal)
    }
}
