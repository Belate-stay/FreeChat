package com.freechat.core

import com.freechat.model.CharacterProfile
import com.freechat.model.DialogueMode
import com.freechat.model.Message
import com.freechat.model.Role
import org.junit.Assert.*
import org.junit.Test

class WechatEmojiTest {
    @Test
    fun defaultPromptDisablesWechatTokensEvenWhenHistoryUsesThem() {
        val history = listOf(Message(id = "old-wechat", role = Role.ASSISTANT, content = "[抠鼻]"))
        val prompt = CompanionPrompts.buildCompanionSystemPrompt(
            CharacterProfile(dialogueMode = DialogueMode.WECHAT), history
        )
        assertTrue(prompt.contains("【当前通道表情规则】"))
        assertTrue(prompt.contains("当前不是已连接的微信消息通道"))
        assertTrue(prompt.contains("历史记录"))
    }

    @Test
    fun narrativeReplyRemovesWechatTokensWithoutChangingParagraphsOrProactiveSignal() {
        val parsed = CompanionReplyParser.parse(
            "她说：“收到[微笑]。”\n\n她收起手机[抠鼻]。\n[主动智能] 取消 | 已经见面",
            narrativeMode = true
        )
        assertEquals("", parsed.emotion)
        assertEquals(listOf("她说：“收到。”\n\n她收起手机。"), parsed.bodyLines)
        assertTrue(parsed.proactive!!.cancel)
    }

    @Test
    fun connectedWechatKeepsKnownEmojiAndStripsUnknownBracketTags() {
        val parsed = CompanionReplyParser.parse(
            "[情绪:开心]\n收到[抠鼻] 还是你[微笑][Emm][OK]\n[未知]好[敷衍]",
            narrativeMode = false, allowWechatEmoji = true
        )
        assertEquals("开心", parsed.emotion)
        assertEquals(listOf("收到[抠鼻] 还是你[微笑][Emm][OK]", "好"), parsed.bodyLines)
    }

    @Test
    fun enabledWechatDoesNotLeakUnknownCustomEmojiNamesLongerThanLegacyLabels() {
        val parsed = CompanionReplyParser.parse(
            "[情绪:平静]\n收到[微笑][自定义很长的表情包名字]",
            narrativeMode = false, allowWechatEmoji = true
        )
        assertEquals(listOf("收到[微笑]"), parsed.bodyLines)
    }

    @Test
    fun emojiOnlyFirstLineIsBodyEvenWhenItsNameIsAlsoAnEmotion() {
        for (token in listOf("[微笑]", "[抠鼻]", "[委屈]", "[难过]", "[害羞]")) {
            val parsed = CompanionReplyParser.parse(token, narrativeMode = false, allowWechatEmoji = true)
            assertEquals(token, "", parsed.emotion)
            assertEquals(listOf(token), parsed.bodyLines)
        }
    }

    @Test
    fun emojiDoesNotChangeExplicitEmotionOrProactiveDirectiveSemantics() {
        val parsed = CompanionReplyParser.parse(
            "[情绪:委屈]\n[微笑] [主动智能] 取消 | 已经回来",
            narrativeMode = false, allowWechatEmoji = true
        )
        assertEquals("委屈", parsed.emotion)
        assertEquals(listOf("[微笑]"), parsed.bodyLines)
        assertTrue(parsed.proactive!!.cancel)
        val legacy = CompanionReplyParser.parse("[开心]\n嗯[微笑]", false, true)
        assertEquals("开心", legacy.emotion)
        assertEquals(listOf("嗯[微笑]"), legacy.bodyLines)
    }

    @Test
    fun allKnownNamesRoundTripOnlyOnEnabledWechatDialogue() {
        assertEquals(109, WechatEmojiTokens.names.size)
        for (name in WechatEmojiTokens.names) {
            val token = "[$name]"
            assertEquals(listOf(token), CompanionReplyParser.parse("[情绪:平静]\n$token", false, true).bodyLines)
            assertTrue(CompanionReplyParser.parse("[情绪:平静]\n$token", false).bodyLines.isEmpty())
            assertTrue(CompanionReplyParser.parse(token, true, true).bodyLines.isEmpty())
        }
        assertFalse(WechatEmojiTokens.isKnownToken("[开心]"))
        assertFalse(WechatEmojiTokens.isKnownToken("[敷衍]"))
        assertFalse(WechatEmojiTokens.isKnownToken("[自定义表情]"))
    }

    @Test
    fun enabledPromptExplainsTheKnownWechatTokensWithoutContradictingItsPermission() {
        val prompt = CompanionPrompts.buildCompanionSystemPrompt(
            CharacterProfile(dialogueMode = DialogueMode.WECHAT), emptyList(), allowWechatEmoji = true
        )
        assertTrue(prompt.contains("已连接的微信消息通道"))
        assertTrue(prompt.contains("[抠鼻]"))
        assertTrue(prompt.contains("[微笑]"))
        assertTrue(prompt.contains("白名单"))
        assertFalse(prompt.contains("当前不是已连接的微信消息通道"))
        assertFalse(prompt.contains("绝对禁止用方括号文字当表情"))
        assertFalse(prompt.contains("正文里绝对不要出现任何方括号标签"))
    }

    @Test
    fun narrativePromptNeverEnablesWechatEmojiEvenWithTheCapability() {
        for (mode in listOf(DialogueMode.ACTION, DialogueMode.PLOT)) {
            val prompt = CompanionPrompts.buildCompanionSystemPrompt(
                CharacterProfile(dialogueMode = mode), emptyList(), allowWechatEmoji = true
            )
            assertTrue(prompt.contains("禁止在正文输出微信方括号表情词"))
            assertFalse(prompt.contains("可使用已知微信内置表情"))
        }
    }
}
