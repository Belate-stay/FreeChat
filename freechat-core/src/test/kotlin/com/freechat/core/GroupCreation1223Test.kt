package com.freechat.core

import com.freechat.model.*
import java.lang.reflect.InvocationTargetException
import org.junit.Assert.*
import org.junit.Test

class GroupCreation1223Test {
    private fun cards(count: Int = 2) = (1..count).map { index ->
        CharacterCard("card-$index", CharacterPersona(name = "角色$index", personaPrompt = "原始人设$index"))
    }
    private fun fromCards(cards: List<CharacterCard>): GroupChatProfile {
        val method = GroupChatPolicy::class.java.methods.find { it.name == "fromCards" }
        assertNotNull("Group construction must copy persona snapshots into a settings draft", method)
        return try { method!!.invoke(GroupChatPolicy, cards) as GroupChatProfile }
        catch (error: InvocationTargetException) { throw error.cause!! }
    }
    private fun canStart(title: String, group: GroupChatProfile): Boolean {
        val method = GroupChatPolicy::class.java.methods.find { it.name == "canStart" }
        assertNotNull("A named, valid group is required before persistence", method)
        return method!!.invoke(GroupChatPolicy, title, group) as Boolean
    }

    @Test fun emptyAndWhitespaceNamesCannotStartAGroup() {
        val group = GroupChatProfile(members = listOf(GroupMember("member", CharacterPersona(name = "角色"))))
        assertFalse(canStart("", group))
        assertFalse(canStart(" \n\t　", group))
        assertTrue(canStart(" 周末书店 ", group))
    }
    @Test fun nameAloneCannotStartAnEmptyOrInvalidGroup() {
        assertFalse(canStart("书店", GroupChatProfile()))
        assertFalse(canStart("书店", GroupChatProfile(members = listOf(GroupMember("member", CharacterPersona(name = ""))))))
    }
    @Test fun cardSelectionCreatesIndependentDefaultGroupSnapshots() {
        val cards = cards()
        val group = fromCards(cards)
        assertTrue(GroupChatPolicy.isValid(group))
        assertEquals(cards.map { it.id }, group.members.map { it.sourceCardId })
        group.members.zip(cards).forEach { (member, card) ->
            assertEquals(card.persona, member.persona)
            assertNotSame(card.persona, member.persona)
        }
        assertEquals("", group.languageModelId)
        assertEquals("", group.visionModelId)
        val edited = group.copy(members = group.members.map { it.copy(persona = it.persona.copy(personaPrompt = "群内改动")) })
        assertNotEquals(cards.first().persona.personaPrompt, edited.members.first().persona.personaPrompt)
        assertEquals("原始人设1", cards.first().persona.personaPrompt)
    }
    @Test fun fourCharactersAreValidAndDuplicateEmptyOrExcessSelectionIsRejected() {
        assertEquals(4, fromCards(cards(4)).members.size)
        listOf(emptyList(), cards(5), listOf(cards().first(), cards().first())).forEach { invalid ->
            try { fromCards(invalid); fail("Invalid selection must not produce a group draft") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
