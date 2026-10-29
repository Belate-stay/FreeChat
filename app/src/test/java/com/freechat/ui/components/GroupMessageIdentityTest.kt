package com.freechat.ui.components

import com.freechat.model.CharacterPersona
import com.freechat.model.GroupChatProfile
import com.freechat.model.GroupMember
import com.freechat.model.Message
import com.freechat.model.Role
import org.junit.Assert.*
import org.junit.Test

class GroupMessageIdentityTest {
    private fun member(id: String, avatar: String) = GroupMember(id,
        CharacterPersona(name = "同名", avatarPath = avatar))

    @Test fun duplicateNamesResolveByMemberId() {
        val group = GroupChatProfile(members = listOf(member("one", "one.jpg"), member("two", "two.jpg")))
        val message = Message(role = Role.ASSISTANT, content = "你好", groupMemberId = "two",
            groupSenderName = "旧昵称", groupSenderAvatar = "old.jpg")
        val identity = GroupMessageIdentity.resolve(message, group)
        assertEquals("two", identity.memberId)
        assertEquals("同名", identity.name)
        assertEquals("two.jpg", identity.avatarPath)
        assertTrue(identity.isCurrentMember)
    }

    @Test fun removedMemberUsesStoredSnapshotWithoutReassigningToSameName() {
        val group = GroupChatProfile(members = listOf(member("new", "new.jpg")))
        val message = Message(role = Role.ASSISTANT, content = "你好", groupMemberId = "removed",
            groupSenderName = "同名", groupSenderAvatar = "removed.jpg")
        val identity = GroupMessageIdentity.resolve(message, group)
        assertEquals("removed", identity.memberId)
        assertEquals("同名", identity.name)
        assertEquals("removed.jpg", identity.avatarPath)
        assertFalse(identity.isCurrentMember)
    }

    @Test fun blankAvatarOnCurrentMemberDoesNotRestoreOldSnapshot() {
        val group = GroupChatProfile(members = listOf(member("one", "")))
        val message = Message(role = Role.ASSISTANT, content = "你好", groupMemberId = "one",
            groupSenderName = "旧昵称", groupSenderAvatar = "old.jpg")
        assertEquals("", GroupMessageIdentity.resolve(message, group).avatarPath)
    }

    @Test fun compositeRowsIncludeEveryAllowedMemberExactlyOnce() {
        assertEquals(listOf(1), groupAvatarRows(0))
        assertEquals(listOf(1), groupAvatarRows(1))
        assertEquals(listOf(2), groupAvatarRows(2))
        assertEquals(listOf(1, 2), groupAvatarRows(3))
        assertEquals(listOf(2, 2), groupAvatarRows(4))
    }
}
