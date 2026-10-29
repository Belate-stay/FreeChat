package com.freechat.core

import org.junit.Assert.*
import org.junit.Test

class GroupMentionDraftTest {
    @Test fun choosingAtTheCaretReplacesOnlyTheActiveToken() {
        val original = GroupMentionDraft("前文 @ 后文")
        val at = original.text.indexOf('@')
        val query = original.queryAt(at + 1)!!
        assertEquals("", query.search)
        val picked = original.mentionAt("b", "林予", query.start, query.end)
        assertEquals("前文 @林予 后文", picked.text)
        assertEquals(listOf("b"), picked.memberIds())
        assertEquals("@林予", picked.text.substring(picked.bindings.single().start, picked.bindings.single().end))
        assertEquals(original, original.mentionAt("b", "林予", -1, 99))
    }

    @Test fun freshlyInsertedAtCannotConsumeTheFollowingUnspacedProse() {
        val draft = GroupMentionDraft("请和@一起出发")
        val query = draft.queryAt(3)!!
        assertEquals("请和@青一起出发", draft.mentionAt("a", "青", query.start, query.end).text)
    }

    @Test fun queryExcludesBoundMentionsAndNonCollapsedSelections() {
        val bound = GroupMentionDraft().mention("a", "同名")
        assertNull(bound.queryAt(2))
        assertNull(GroupMentionDraft("@林").queryAt(2, 1))
        assertNull(GroupMentionDraft("普通文字").queryAt(2))
        assertEquals("林", GroupMentionDraft("@林").queryAt(2)!!.search)
        assertNull(GroupMentionDraft("@林\n你好").queryAt(6))
    }

    @Test fun caretInsertionPreservesOtherSelectedIdsAndMovesTheirRanges() {
        val first = GroupMentionDraft().mention("a", "同名")
        val editing = first.edit("@候选 " + first.text)
        val query = editing.queryAt(1)!!
        val picked = editing.mentionAt("b", "同名", query.start, query.end)
        assertEquals(listOf("b", "a"), picked.memberIds())
        assertEquals(listOf("b", "a"), picked.edit("前缀 " + picked.text).memberIds())
        assertNull(picked.queryAt(picked.bindings.first().end))
    }

    @Test fun sentMentionLabelsAndRangesSurviveSerializationAndOldMessagesHaveNone() {
        val draft = GroupMentionDraft("你好 ").mention("member-b", "同名")
        val row = com.freechat.model.Message(role = com.freechat.model.Role.USER, content = draft.text,
            mentionedMemberIds = draft.memberIds(), groupMentions = draft.validBindings())
        val restored = com.freechat.data.AppJson.gson.fromJson(com.freechat.data.AppJson.gson.toJson(row), com.freechat.model.Message::class.java)
        assertEquals(draft.bindings, restored.groupMentions)
        assertEquals("@同名", restored.groupMentions.single().label)
        val old = com.freechat.data.AppJson.gson.fromJson("{\"role\":\"USER\",\"content\":\"@普通文字\"}", com.freechat.model.Message::class.java)
        assertTrue(old.groupMentions.isEmpty())
    }

    @Test fun trimmingWhitespaceAtBothEndsKeepsTheMentionIdentity() {
        val draft = GroupMentionDraft("  ").mention("a", "同名")
        assertEquals(listOf("a"), draft.edit(draft.text.trim()).memberIds())
    }
    @Test fun sameNameMentionsBindToSelectedIdsAndSurviveUnrelatedTyping() {
        val first = GroupMentionDraft("你好 ").mention("b", "同名")
        assertEquals(listOf("b"), first.memberIds())
        val typed = first.edit(first.text + "有空吗")
        assertEquals(listOf("b"), typed.memberIds())
        val both = typed.mention("a", "同名")
        assertEquals(listOf("b", "a"), both.memberIds())
    }

    @Test fun removingOrEditingTheMentionClearsItsHiddenTarget() {
        val draft = GroupMentionDraft().mention("a", "角色")
        assertEquals(emptyList<String>(), draft.edit("随便聊聊").memberIds())
        assertEquals(emptyList<String>(), draft.edit(draft.text.replace("@角色", "角色")).memberIds())
    }

    @Test fun editingBeforeMentionAdjustsItsRangeWithoutChangingTheMember() {
        val draft = GroupMentionDraft("之前 ").mention("a", "角色")
        val edited = draft.edit("更早一点 " + draft.text)
        assertEquals(listOf("a"), edited.memberIds())
        assertEquals(emptyList<String>(), edited.edit(edited.text.replace("角色", "另一个人")).memberIds())
    }
}
