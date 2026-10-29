package com.freechat.sync

import com.freechat.data.AppJson
import com.freechat.data.healed
import com.freechat.model.GroupMention
import com.freechat.model.Message
import com.freechat.model.Role
import org.junit.Assert.*
import org.junit.Test

class GroupMentionSyncTest {
    private fun tagged() = Message(id = "m", role = Role.USER, content = "@林予",
        mentionedMemberIds = listOf("member-a"), groupMentions = listOf(GroupMention("member-a", "@林予", 0, 3)))

    @Test fun anOldClientEchoCannotEraseMatchingHistoricalHighlight() {
        val local = tagged()
        val oldClient = local.copy(mentionedMemberIds = emptyList(), groupMentions = emptyList())
        val merged = Merge.mergeMessages(listOf(local), listOf(oldClient)).single()
        assertEquals(local.groupMentions, merged.groupMentions)
        assertEquals(local.mentionedMemberIds, merged.mentionedMemberIds)
    }

    @Test fun changedBodyCannotRecoverTheRemovedMentionTarget() {
        val edited = tagged().copy(content = "这条消息已经删去了成员标签", mentionedMemberIds = emptyList(), groupMentions = emptyList())
        val merged = Merge.mergeMessages(listOf(edited), listOf(tagged())).single()
        assertEquals(edited.content, merged.content)
        assertTrue(merged.mentionedMemberIds.isEmpty())
        assertTrue(merged.groupMentions.isEmpty())
    }

    @Test fun anExplicitRemoteNullIsHealedToAnEmptyMentionList() {
        val row = AppJson.gson.fromJson("{\"role\":\"USER\",\"content\":\"hi\",\"groupMentions\":null}", Message::class.java).healed()
        assertNotNull(row.groupMentions)
        assertTrue(row.groupMentions.isEmpty())
    }
}
