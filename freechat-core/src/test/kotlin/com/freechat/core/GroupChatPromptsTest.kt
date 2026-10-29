package com.freechat.core

import com.freechat.model.*
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class GroupChatPromptsTest {
    private fun member(id: String, secret: String) = GroupMember(id,
        CharacterPersona.from(CharacterProfile(name = "同名", personalityText = secret)), "card-$id")
    private val a = member("a", "A_PRIVATE_FACT")
    private val b = member("b", "B_PRIVATE_FACT")
    private val group = GroupChatProfile(members = listOf(a, b), userNickname = "青禾",
        announcement = "周末读书会", languageModelId = "group-language", visionModelId = "group-vision")

    @Test fun actorOnlyReceivesOwnPersonaAndOwnMemory() {
        val request = GroupChatPrompts.actorMessages(group, a, emptyList(), "A_PRIVATE_MEMORY", "保持群聊自然", "现在")
        val text = request.joinToString { it["content"].toString() }
        assertTrue(text.contains("A_PRIVATE_FACT"))
        assertTrue(text.contains("A_PRIVATE_MEMORY"))
        assertFalse(text.contains("B_PRIVATE_FACT"))
        assertTrue(text.contains("周末读书会"))
        assertTrue(text.contains("青禾"))
    }

    @Test fun sameNameSpeakersKeepTheirStableIdentityInHistory() {
        val history = listOf(
            Message(id = "u", role = Role.USER, content = "在吗", mentionedMemberIds = listOf("b")),
            Message(id = "a1", role = Role.ASSISTANT, content = "我刚下楼", groupMemberId = "a"),
            Message(id = "b1", role = Role.ASSISTANT, content = "我还在图书馆", groupMemberId = "b"))
        val request = GroupChatPrompts.actorMessages(group, a, history, "", "", "现在")
        assertTrue(request.any { it["role"] == "assistant" && it["content"] == "我刚下楼" })
        val other = request.first { it["role"] == "user" && it["content"].toString().contains("我还在图书馆") }
        assertEquals("user", other["role"])
        assertTrue(other["content"].toString().contains("\"id\":\"b\""))
        assertTrue(request.any { it["content"].toString().contains("\"mentions\":[\"b\"]") })
    }

    @Test fun plannerCanLeaveEverybodySilentAndCanReturnToAnEarlierSpeaker() {
        assertEquals(emptyList<String>(), GroupChatPrompts.parsePlan("{\"speakers\":[]}", group))
        assertEquals(listOf("a", "b", "a"), GroupChatPrompts.parsePlan(
            "```json\n{\"speakers\":[\"a\",\"a\",\"b\",\"a\"]}\n```", group))
        val prompt = GroupChatPrompts.plannerMessages(group, emptyList(), "", "现在").joinToString { it["content"].toString() }
        assertTrue(prompt.contains("离线"))
        assertTrue("被点名一般要接（2026-10-28 返修：@ 不再是不回话的挡箭牌）", prompt.contains("一般要接"))
        assertTrue(prompt.contains("只安排本轮"))
    }

    @Test fun plannerRejectsInventedMembersInsteadOfSendingAsSomebodyElse() {
        try {
            GroupChatPrompts.parsePlan("{\"speakers\":[\"not-a-member\"]}", group)
            fail("Invented member must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun nudgeIsAnAttributedPublicEventNotAnInstructionOrGuaranteedReply() {
        val event = Message(role = Role.SYSTEM, content = "青禾拍了拍同名", groupEventType = "nudge",
            groupTargetMemberId = "b", groupTriggerMessageIds = listOf("nudge-source"))
        val request = GroupChatPrompts.actorMessages(group, a, listOf(event), "", "", "现在")
        val row = request.first { it["role"] == "user" && it["content"].toString().contains("\"event\":\"nudge\"") }
        assertEquals("user", row["role"])
        assertTrue(row["content"].toString().contains("\"target\":\"b\""))
    }

    @Test fun latestSharedSpeechAnchorUsesTheActualMemberNotTheLatestUserOrFailure() {
        val history = listOf(Message(id = "u", role = Role.USER, content = "约个时间"),
            Message(id = "a", role = Role.ASSISTANT, content = "我三点才下课", groupMemberId = "a"),
            Message(id = "b", role = Role.ASSISTANT, content = "那改成四点", groupMemberId = "b"),
            Message(role = Role.ASSISTANT, content = "网络失败", groupMemberId = "a", failed = true),
            Message(role = Role.ASSISTANT, content = "场景图", sceneVisualization = true))
        val request = GroupChatPrompts.actorMessages(group, a, history, "A_PRIVATE_MEMORY", "", "现在")
        val context = request.first { it["content"].toString().startsWith("【群内公开资料") }["content"].toString()
        val latest = JsonParser.parseString(context.substringAfter('\n')).asJsonObject.getAsJsonObject("latestPublicMessage")
        assertNotNull("Actors need a public, current speaker anchor", latest)
        assertEquals("b", latest.getAsJsonObject("sender")["id"].asString)
        assertEquals("那改成四点", latest["text"].asString)
        assertFalse(context.contains("B_PRIVATE_FACT"))
    }
}
