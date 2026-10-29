package com.freechat.core

import com.freechat.model.DialogueMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-19 语音消息格式契约：
 *  · <voice> 块跨行不拆（一条语音=一条消息，台词里带换行也整块保留）；
 *  · 文字/语音自由交替、可连发多条语音；
 *  · 语音行豁免格式自检（旧版「单行超 100 字=剧情写法」把长语音标签行判违规，
 *    格式自检一重写就把标签洗掉——短语音能发、长语音必哑的真凶）。
 */
class VoiceMessageFormatTest {

    @Test fun voiceBlocksStayAtomicWhenSplittingMessages() {
        val raw = "行，就这一次。\n<voice style=\"低声\">今天其实挺安静的，\n我一个人待了一天。</voice>\n你也别催啦。"
        val lines = CompanionReplyParser.splitMessagesKeepingVoiceBlocks(raw)
        assertEquals("文字+跨行语音+文字 = 3 条", 3, lines.size)
        assertTrue(lines[1].startsWith("<voice"))
        assertTrue(lines[1].endsWith("</voice>"))

        val parsed = CompanionReplyParser.parse(raw, narrativeMode = false)
        assertEquals(3, parsed.bodyLines.size)
        assertNull("文字行不是语音", CompanionPrompts.extractVoice(parsed.bodyLines[0]))
        val v = CompanionPrompts.extractVoice(parsed.bodyLines[1])
        assertEquals("今天其实挺安静的，\n我一个人待了一天。", v?.text)
        assertEquals("低声", v?.style)
    }

    @Test fun consecutiveVoiceMessagesAreEachTheirOwnBubble() {
        val raw = "<voice>第一条语音。</voice>\n<voice style=\"撒娇\">第二条语音……才怪！</voice>"
        val parsed = CompanionReplyParser.parse(raw, narrativeMode = false)
        assertEquals("连发两条语音 = 两条独立消息", 2, parsed.bodyLines.size)
        assertEquals("第一条语音。", CompanionPrompts.extractVoice(parsed.bodyLines[0])?.text)
        assertEquals("第二条语音……才怪！", CompanionPrompts.extractVoice(parsed.bodyLines[1])?.text)
        assertEquals("撒娇", CompanionPrompts.extractVoice(parsed.bodyLines[1])?.style)
    }

    @Test fun longVoiceLineIsNeverAFormatViolation() {
        val longVoice = "<voice>" + "长".repeat(150) + "</voice>"
        assertFalse("纯语音回复永不违规", CompanionPrompts.isFormatViolation(longVoice, DialogueMode.WECHAT))
        assertFalse("长语音行不参与超长行判定", CompanionPrompts.isFormatViolation("短句。\n$longVoice", DialogueMode.WECHAT))
        assertFalse("正式文字或长语音都不能仅凭长度判为串档",
            CompanionPrompts.isFormatViolation("长".repeat(120), DialogueMode.WECHAT))
    }
}
