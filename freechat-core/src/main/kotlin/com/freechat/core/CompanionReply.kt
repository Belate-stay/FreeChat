package com.freechat.core

import com.freechat.model.SearchCitation

/**
 * 拟人回复的结构化结果与解析（1.0.91 M1 第三刀从 ChatViewModel 平移，行为一字不变）。
 * 解析只做纯文本侧（指令剥离/情绪标签/分条/方括号清洗）；「格式自检+重写」是独立 HTTP 小调用，留在端上。
 */

/** 拟人模式结构化回复：模型输出的情绪标签 + 分条回复（2026-10-20 本地主动指令链退役——云端主动智能不走输出指令） */
data class CompanionReply(
    val emotion: String, val segments: List<String>,
    val sources: List<SearchCitation> = emptyList(),
    val silence: Boolean = false
)

/** [CompanionReplyParser.parse] 的产出：正文行 + 情绪（sources 由端上合并检索证据后补） */
data class ParsedCompanionReply(
    val emotion: String, val bodyLines: List<String>, val silence: Boolean = false
)

object CompanionReplyParser {

    /**
     * 模型原文 → 结构化。顺序是行为的一部分：
     * ① 叙事档不解析情绪标签、整段一条（空行仅排版）；
     * ② 微信档解析首行情绪标签（标准 [情绪:开心]，兼容裸 [开心]，识别不了当正文留着）；
     * ③ 默认剥掉正文里的短方括号标签；已验证的微信通道仅保留已知内置表情。
     *    表情词与情绪同名时（如 [委屈]），允许微信表情的通道以正文表情为准；
     *    显式 [情绪:委屈] 按原有规则解析。
     */
    fun parse(
        rawOrig: String,
        narrativeMode: Boolean,
        allowWechatEmoji: Boolean = false
    ): ParsedCompanionReply {
        val raw = rawOrig

        val emotion: String
        val bodyLines: List<String>
        if (narrativeMode) {
            emotion = ""
            bodyLines = listOf(WechatEmojiTokens.stripKnownTokens(raw).trim()).filter { it.isNotEmpty() }
        } else {
            val lines = splitMessagesKeepingVoiceBlocks(raw)
            val emotionTag = Regex("""^\s*\[(?:情绪|emotion)?\s*[:：]?\s*([^\]]+)]\s*$""")
            val first = lines.firstOrNull()
            val parsed = first?.takeUnless { allowWechatEmoji && WechatEmojiTokens.isKnownToken(it) }
                ?.let { emotionTag.find(it) }?.let { m ->
                val label = m.groupValues[1].trim()
                if (parseEmotionLabel(label) != null) label else null
            }
            emotion = parsed ?: ""
            val rawBodyLines = if (parsed != null) lines.drop(1) else lines
            val silenceRow = Regex("^\\[回应[:：]不回复]$")
            // Only an entire control-only result is deliberate silence. Written speech always wins.
            val controls = rawBodyLines.filter { silenceRow.matches(it.trim()) }
            if (controls.isNotEmpty() && rawBodyLines.all { silenceRow.matches(it.trim()) }) {
                return ParsedCompanionReply(emotion, emptyList(), silence = true)
            }
            val bracketToken = if (allowWechatEmoji) Regex("""\[[^\[\]\r\n]+]\s*""") else
                Regex("""\[[^\]]{1,6}]\s*""")
            bodyLines = rawBodyLines.filterNot { silenceRow.matches(it.trim()) }.map { line ->
                // 语音消息行原样保留——标签里的台词不做方括号清洗（2026-10-19）
                if (line.startsWith("<voice", ignoreCase = true)) line
                else line.replace(bracketToken) { match ->
                    if (silenceRow.matches(match.value.trim()) ||
                        (allowWechatEmoji && WechatEmojiTokens.isKnownToken(match.value.trimEnd()))) match.value else ""
                }.trim()
            }.filter { it.isNotEmpty() }
        }
        return ParsedCompanionReply(emotion, bodyLines)
    }

    /**
     * 分条（2026-10-19）：`<voice>…</voice>` 块**跨行不可拆**——一条语音就是一条消息，
     * 哪怕台词里带换行；块外的文字照旧按行分条。文字/语音可任意交替、可连发多条语音。
     */
    internal fun splitMessagesKeepingVoiceBlocks(raw: String): List<String> {
        val out = mutableListOf<String>()
        val block = Regex("<voice[\\s\\S]*?</voice>", RegexOption.IGNORE_CASE)
        var rest = raw
        while (true) {
            val m = block.find(rest) ?: break
            rest.substring(0, m.range.first).split("\n").map { it.trim() }
                .filter { it.isNotEmpty() }.forEach { out.add(it) }
            out.add(m.value.trim())
            rest = rest.substring(m.range.last + 1)
        }
        rest.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach { out.add(it) }
        return out
    }
}
