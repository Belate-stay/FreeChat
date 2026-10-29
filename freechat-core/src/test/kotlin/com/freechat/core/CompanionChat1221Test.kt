package com.freechat.core

import com.freechat.model.*
import org.junit.Assert.*
import org.junit.Test

class CompanionChat1221Test {
    private val friend = CharacterProfile(name = "林遥", personalityText = "平和、直接，熟人之间随意。",
        relationshipPreset = "朋友", relationshipText = "相识多年，各自有工作。", enableWebSearch = false)

    @Test fun proactiveKeepsUserWordingEvenWhenALearnedPersonaExists() {
        val ch = friend.copy(personaPrompt = "学习稿：活泼，习惯打趣。",
            personalityText = "说话礼貌正式，用完整句和规范标点，不称呼对方为小朋友。",
            personalityPresets = listOf("理性冷静"), memoryPerception = "你们在社区读书会认识。",
            worldRules = "你在市图书馆工作。", userPersona = "对方是刚入职的编辑。")
        val prompt = CompanionPrompts.proactiveDecide(ch, "TA：下周见", "2026年10月26日 15点", 2,
            "距上次主动消息 90 分钟", "23点至7点")
        listOf(ch.personaPrompt, ch.personalityText, ch.memoryPerception, ch.worldRules, ch.userPersona,
            ch.relationshipText, "理性冷静").forEach { assertTrue("Missing original context: $it", prompt.contains(it)) }
        assertTrue(prompt.contains("用户原文优先"))
        assertTrue(prompt.contains("正式"))
    }

    @Test fun proactiveUsesConcreteReasonsWithoutMakingSilenceAnEventOrAMessageLimit() {
        val recent = "[TA 2026-10-24T12:00:00Z] 我去整理资料\n[你 2026-10-25T12:00:00Z] 上次那本书我看完了"
        val prompt = CompanionPrompts.proactiveDecide(friend, recent, "2026年10月26日 20点", 2,
            "距上次主动消息 1440 分钟", "23点至7点")
        assertTrue(prompt.contains(recent))
        assertTrue(prompt.contains("1440 分钟"))
        assertTrue(prompt.contains("没回复不等于"))
        assertTrue(prompt.contains("同一个催促换个说法"))
        assertTrue(prompt.contains("约定是否已兑现、取消或改期"))
        assertTrue(prompt.contains("条数和长短由内容与人设决定"))
        assertFalse(prompt.contains("1~2 条"))
        assertFalse(prompt.contains("一到两条短消息"))
        assertFalse(prompt.contains("约定到点才催"))
    }

    @Test fun ordinaryRepliesCanLeaveThoughtsUnfinishedWithoutChangingFormalPersonas() {
        for (personality in listOf("话少，直接回答，不爱解释。", "表达礼貌正式，使用完整句和规范标点。")) {
            val ch = friend.copy(personalityText = personality)
            val prompt = CompanionPrompts.buildCompanionSystemPrompt(ch, emptyList())
            assertTrue(prompt.contains(personality))
            assertTrue(prompt.contains("【日常表达"))
            assertTrue(prompt.contains("不必替每句话补全因果"))
            assertTrue(prompt.contains("不知道对方在做什么时"))
            assertTrue(prompt.contains("明确人设与用户原文优先"))
            assertTrue(prompt.contains("不设固定条数"))
        }
    }

    @Test fun timeDataDoesNotTurnMessageIntervalsIntoAccusationsOrCurrentIdleTime() {
        val now = 1_792_900_000_000L
        val history = listOf(Message(role = Role.USER, content = "刚出门", timestamp = now - 73 * 3_600_000L),
            Message(role = Role.USER, content = "路上碰到同事", timestamp = now - 72 * 3_600_000L))
        for (perception in listOf(false, true)) {
            val prompt = CompanionPrompts.buildTimeContext(history, timePerception = perception, state = TimeState(nowMillis = now))
            assertTrue(prompt.contains("最近两条用户消息相隔约 1 小时"))
            assertTrue(prompt.contains("时间间隔只说明经过多久"))
            assertFalse(prompt.contains("1 小时前才回你"))
            assertFalse(prompt.contains("适当抱怨或调侃"))
        }
    }

    @Test fun groupActorsInheritNaturalExpressionWithoutSharingPrivatePersona() {
        val own = GroupMember("own", CharacterPersona.from(friend.copy(personalityText = "正式礼貌，称呼对方为您。")))
        val other = GroupMember("other", CharacterPersona.from(friend.copy(name = "温树", personalityText = "OTHER_PRIVATE_TRAIT")))
        val messages = GroupChatPrompts.actorMessages(GroupChatProfile(members = listOf(own, other)), own,
            listOf(Message(role = Role.USER, content = "明天几点出发")), "本群自己的约定：明早十点。", "", "现在")
        val prompt = messages.joinToString("\n") { it["content"].toString() }
        assertTrue(prompt.contains("【日常表达"))
        assertTrue(prompt.contains("正式礼貌，称呼对方为您。"))
        assertTrue(prompt.contains("本群自己的约定：明早十点。"))
        assertFalse(prompt.contains("OTHER_PRIVATE_TRAIT"))
    }

    @Test fun casualExpressionDoesNotReplaceTheTwoNarrativeModes() {
        for (mode in listOf(DialogueMode.ACTION, DialogueMode.PLOT)) {
            val prompt = CompanionPrompts.buildCompanionSystemPrompt(friend.copy(dialogueMode = mode), emptyList())
            assertFalse(prompt.contains("【日常表达"))
            assertTrue(CompanionPrompts.modeFormatRule(mode).contains("一整段"))
        }
    }
}
