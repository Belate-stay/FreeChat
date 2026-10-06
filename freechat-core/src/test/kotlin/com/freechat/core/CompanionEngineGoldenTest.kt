package com.freechat.core

import com.freechat.model.CharacterProfile
import com.freechat.model.DialogueMode
import com.freechat.model.Message
import com.freechat.model.Role
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * M1 第三刀 golden：请求装配次序、回复解析、节奏表——
 * 钉住 callCompanionApi 的装配合同（格式铁律压尾）、情绪标签解析边界与情绪化节奏表。
 */
class CompanionEngineGoldenTest {

    private fun msg(role: Role, content: String, ts: Long = 0L, images: List<String> = emptyList(), imageContext: String? = null) =
        Message(id = "m$ts$role", role = role, content = content, timestamp = ts, imagePaths = images, imageContext = imageContext)

    // ============ 装配次序 ============

    @Test
    fun assemblyOrderMatchesContractAndFormatRuleIsLast() {
        val history = listOf(msg(Role.USER, "在吗", 1000L), msg(Role.ASSISTANT, "在", 2000L))
        val messages = CompanionRequestBuilder.build(
            character = CharacterProfile(dialogueMode = DialogueMode.WECHAT),
            systemPrompt = "SYS", memoryContext = "MEM", deepPrepNote = "PREP", quoteText = "QUOTE",
            history = history, imageContext = "IMG", forceReply = true, lengthHint = "LEN",
            proactivePrompt = "FIRE", batchSize = 2, serpBlock = "SERP", searchGuidance = "GUIDE",
            includeReasoningContent = true
        )
        val contents = messages.map { it["content"] as String }
        // 全部材料都在（IMG 是嵌在长句里的，用 contains 找）
        assertTrue(contents[0] == "SYS")
        assertTrue("MEM" in contents && "PREP" in contents && "QUOTE" in contents)
        assertTrue(contents.any { it.contains("IMG") } && "LEN" in contents && "FIRE" in contents && "SERP" in contents && "GUIDE" in contents)
        // 次序：SYS → MEM → PREP → QUOTE → 历史 → IMG → 强制/长度/唤醒/多条 → SERP → GUIDE → 格式铁律
        val idxImg = contents.indexOfFirst { it.contains("IMG") }
        assertTrue(contents.indexOf("MEM") < contents.indexOf("PREP"))
        assertTrue(contents.indexOf("PREP") < contents.indexOf("QUOTE"))
        assertTrue(contents.indexOf("QUOTE") < contents.indexOfFirst { it.contains("在吗") })
        assertTrue(contents.indexOfFirst { it.contains("在吗") } < idxImg)
        assertTrue(idxImg < contents.indexOf("LEN"))
        assertTrue(contents.indexOf("SERP") < contents.indexOf("GUIDE"))
        // ★ 格式铁律压尾：模型开写前读到的最后一段话
        assertTrue(contents.last().contains("铁律"))
        // reasoning_content 只给 assistant 行且要求带上时
        val hist = messages.filter { it["role"] == "assistant" }
        assertTrue(hist.all { it.containsKey("reasoning_content") })
    }

    @Test
    fun assemblyOmitsAbsentMaterialsAndHistoryCarriesTimePrefix() {
        val history = listOf(msg(Role.USER, "早上好", 0L), msg(Role.USER, "人呢", 40 * 60_000L))
        val messages = CompanionRequestBuilder.build(
            character = CharacterProfile(dialogueMode = DialogueMode.WECHAT),
            systemPrompt = "SYS", history = history
        )
        val contents = messages.map { it["content"] as String }
        assertEquals(4, messages.size)                                   // SYS + 2 历史 + 恒压尾的格式铁律
        assertTrue(contents.last().contains("铁律"))
        assertTrue(contents[2].startsWith("（"))                          // 第二条带时间轴前缀（>30 分钟）
        // 图片占位映射
        val withImg = CompanionRequestBuilder.build(
            systemPrompt = "S", history = listOf(msg(Role.USER, "", 1L, images = listOf("/x.jpg"), imageContext = "一只猫"))
        )
        assertTrue((withImg[1]["content"] as String).contains("[这张图片的内容：一只猫]"))
        val imgOnly = CompanionRequestBuilder.build(
            systemPrompt = "S", history = listOf(msg(Role.USER, "", 1L, images = listOf("/x.jpg")))
        )
        assertEquals("[图片]", (imgOnly[1]["content"] as String).substringAfter("）"))   // 带时间轴前缀（原语义）
        // 引用 null 不注、非 null 空串也注（原 pendingQuoteText?.let 语义）
        assertEquals(2, CompanionRequestBuilder.build(systemPrompt = "S", quoteText = null).size)   // SYS + 铁律
        assertEquals(3, CompanionRequestBuilder.build(systemPrompt = "S", quoteText = "").size)     // SYS + 引用 + 铁律
    }

    // ============ 回复解析 ============

    @Test
    fun parserStripsProactiveThenEmotionThenBrackets() {
        // 标记前的正文保留（「晚点聊」是指令行前的人话，不是指令的一部分）
        val p = CompanionReplyParser.parse("[情绪:开心]\n好呀\n晚点聊 [主动智能] +30m | 约好了", narrativeMode = false)
        assertEquals("开心", p.emotion)
        assertEquals(listOf("好呀", "晚点聊"), p.bodyLines)
        assertNotNull(p.proactive)
        // 裸标签兼容；识别不了的情绪当正文
        val bare = CompanionReplyParser.parse("[害羞]\n没有啦", narrativeMode = false)
        assertEquals("害羞", bare.emotion)
        assertEquals(listOf("没有啦"), bare.bodyLines)
        val unknown = CompanionReplyParser.parse("[无语]\n..", narrativeMode = false)
        assertEquals("", unknown.emotion)
        assertTrue(unknown.bodyLines.any { it.contains("无语") } || unknown.bodyLines.contains(".."))
        // 正文里漏出的裸方括号标签剥掉
        val dirty = CompanionReplyParser.parse("今天 [敷衍] 不想动", narrativeMode = false)
        assertEquals(listOf("今天 不想动"), dirty.bodyLines.map { it.replace("  ", " ") }.map { it.trim() })
    }

    @Test
    fun parserKeepsNarrativeAsSingleSegmentWithoutEmotion() {
        val p = CompanionReplyParser.parse("她倚着窗。\n\n她轻声说：“慢些。”", narrativeMode = true)
        assertEquals("", p.emotion)
        assertEquals(1, p.bodyLines.size)                                // 整段一条，空行仅排版
        assertTrue(p.bodyLines[0].contains("她倚着窗"))
    }

    // ============ 节奏表 ============

    @Test
    fun rhythmSegmentCountFollowsMoodAndRegenOverridesAngerSilence() {
        val fixed = Random(42)
        // 叙事档最多 3 段
        assertEquals(3, CompanionRhythm.segmentCount(CompanionMood.NEUTRAL, narrative = true, regenerating = false, segmentTotal = 10, random = fixed))
        // 生气：重生成时不许沉默
        assertEquals(1, CompanionRhythm.segmentCount(CompanionMood.ANGRY, narrative = false, regenerating = true, segmentTotal = 5, random = fixed))
        // 消极情绪恒 1 条
        assertEquals(1, CompanionRhythm.segmentCount(CompanionMood.SAD, narrative = false, regenerating = false, segmentTotal = 5, random = fixed))
        assertEquals(1, CompanionRhythm.segmentCount(CompanionMood.DISMISSIVE, narrative = false, regenerating = false, segmentTotal = 5, random = fixed))
        // 兴奋/开心 2-3 条
        repeat(20) {
            val n = CompanionRhythm.segmentCount(CompanionMood.EXCITED, narrative = false, regenerating = false, segmentTotal = 9, random = fixed)
            assertTrue(n in 2..3)
        }
    }

    @Test
    fun rhythmIntervalsStayWithinEmotionBands() {
        val fixed = Random(7)
        repeat(20) {
            assertTrue(CompanionRhythm.segmentIntervalMs(CompanionMood.ANGRY, false, fixed) in 4000L..7000L)
            assertTrue(CompanionRhythm.segmentIntervalMs(CompanionMood.EXCITED, false, fixed) in 800L..2000L)
            assertTrue(CompanionRhythm.segmentIntervalMs(CompanionMood.NEUTRAL, true, fixed) in 1500L..3000L)
            assertTrue(CompanionRhythm.initialTypingDelayMs(fixed) in 1500L..4500L)
        }
    }
}
