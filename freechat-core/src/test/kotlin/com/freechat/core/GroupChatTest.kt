package com.freechat.core

import com.freechat.model.*
import org.junit.Assert.*
import org.junit.Test

class GroupChatTest {
    private fun members(count: Int) = (1..count).map { GroupMember("m$it", CharacterPersona(name = "同名"), "c$it") }

    @Test fun groupAcceptsOneThroughFourDistinctMembersOnly() {
        assertFalse(GroupChatPolicy.isValid(GroupChatProfile()))
        assertTrue(GroupChatPolicy.isValid(GroupChatProfile(members = members(1))))
        assertTrue(GroupChatPolicy.isValid(GroupChatProfile(members = members(4))))
        assertFalse(GroupChatPolicy.isValid(GroupChatProfile(members = members(5))))
        assertFalse(GroupChatPolicy.isValid(GroupChatProfile(members = listOf(members(1)[0], members(1)[0]))))
    }

    @Test fun groupDefaultsHaveUnifiedLiveGlobalModels() {
        val group = GroupChatProfile(members = members(2))
        assertEquals("", group.languageModelId)
        assertEquals("", group.visionModelId)
        assertTrue(group.showMemberNames)
        assertEquals("我", group.userNickname)
        assertTrue(group.replyBufferEnabled)
        assertTrue(group.timePerception)
    }

    @Test fun memberNameVisibilityCanChangeDuringRepliesWithoutUnlockingOtherSettings() {
        val current = GroupChatProfile(members = members(2))
        assertTrue(GroupChatPolicy.canApplyUpdate(current, current.copy(showMemberNames = false), isReplying = true))
        val changes = listOf(current.copy(languageModelId = "other"), current.copy(visionModelId = "other"),
            current.copy(replyBufferEnabled = !current.replyBufferEnabled), current.copy(timePerception = !current.timePerception),
            current.copy(highQualityMemory = true), current.copy(deepThinking = true), current.copy(aiCreativity = 9f),
            current.copy(announcement = "新公告"), current.copy(userNickname = "新昵称"), current.copy(members = members(3)))
        changes.forEach { changed ->
            assertFalse(GroupChatPolicy.canApplyUpdate(current, changed, isReplying = true))
            assertFalse("A display change cannot smuggle reply changes", GroupChatPolicy.canApplyUpdate(
                current, changed.copy(showMemberNames = false), isReplying = true))
            assertTrue(GroupChatPolicy.canApplyUpdate(current, changed, isReplying = false))
        }
        assertFalse(GroupChatPolicy.canApplyUpdate(current, current.copy(members = members(5)), isReplying = false))
    }

    @Test fun memberAndMessageIdsPreserveIdentityWithDuplicateNames() {
        val group = GroupChatProfile(members = members(2))
        val message = Message(role = Role.ASSISTANT, content = "hi", groupMemberId = "m2",
            groupSenderName = "同名", groupTriggerMessageIds = listOf("trigger"), mentionedMemberIds = listOf("m1"))
        assertEquals("m2", group.members.single { it.id == message.groupMemberId }.id)
        assertEquals(listOf("trigger"), message.groupTriggerMessageIds)
    }
}
