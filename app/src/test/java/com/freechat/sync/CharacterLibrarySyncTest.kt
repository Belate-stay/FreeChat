package com.freechat.sync

import com.freechat.data.*
import com.freechat.model.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CharacterLibrarySyncTest {
    private fun fresh() {
        LocalStore.setWriteListener(null)
        LocalStore.init(Files.createTempDirectory("library-sync-v1216-").toFile())
    }

    @Test fun concurrentDifferentCardEditsAndSameCardPersonaConflictsKeepBoth() {
        fresh()
        val local = CharacterLibraryStore.add(CharacterPersona(name = "A", personaPrompt = "local"))
        CharacterLibraryStore.add(CharacterPersona(name = "B"))
        CharacterLibraryStore.applyRemoteCard(local.copy(persona = local.persona.copy(personaPrompt = "remote")), true)
        val cards = CharacterLibraryStore.state.value.cards
        assertEquals(3, cards.size)
        assertEquals(setOf("local", "remote"), cards.filter { it.persona.name.startsWith("A") }.map { it.persona.personaPrompt }.toSet())
        CharacterLibraryStore.applyRemoteCard(local.copy(persona = local.persona.copy(personaPrompt = "remote")), true)
        assertEquals("Same conflict must not make copies again", 3, CharacterLibraryStore.state.value.cards.size)
    }

    @Test fun deletedFolderDoesNotUndoConcurrentMoveAndUnknownFolderIsRetained() {
        fresh()
        val old = CharacterLibraryStore.createFolder("old")
        val target = CharacterLibraryStore.createFolder("target")
        val card = CharacterLibraryStore.add(CharacterPersona(name = "A"), old.id)
        CharacterLibraryStore.moveCard(card.id, target.id)
        CharacterLibraryStore.applyRemoteFolderDeletion(old.id)
        assertEquals(target.id, CharacterLibraryStore.state.value.cards.single().folderId)
        CharacterLibraryStore.applyRemoteCard(CharacterCard("late-card", CharacterPersona(name = "B"), "not-yet-pulled"), false)
        assertEquals("not-yet-pulled", CharacterLibraryStore.state.value.cards.single { it.id == "late-card" }.folderId)
        CharacterLibraryStore.applyRemoteCardDeletion(card.id)
        CharacterLibraryStore.applyRemoteCard(card, true)
        assertFalse(CharacterLibraryStore.state.value.cards.any { it.id == card.id })
    }

    @Test fun cardAndGroupWireRoundTripRetainsPersonaIdentityAndFreshGroupModels() {
        fresh()
        val card = CharacterLibraryStore.add(CharacterPersona(name = "A", personaPrompt = "secret"))
        assertEquals(card, Wire.cardFromWire(Wire.cardToWire(card)))
        val group = Conversation(id = "group", groupChat = GroupChatProfile(
            members = listOf(GroupMember("stable-id", card.persona)), languageModelId = "device-local"))
        val remote = Wire.convFromWire(Wire.convToWire(group), null)!!
        assertEquals("stable-id", remote.groupChat!!.members.single().id)
        assertEquals("", remote.groupChat!!.languageModelId)
        assertEquals("secret", remote.groupChat!!.members.single().persona.personaPrompt)
    }

    @Test fun switchingAccountsRestoresOnlyThatAccountsPoolAndTombstones() {
        fresh()
        val local = CharacterLibraryStore.add(CharacterPersona(name = "offline"))
        CharacterLibraryStore.switchAccount("alice")
        assertEquals(local.id, CharacterLibraryStore.state.value.cards.single().id)
        CharacterLibraryStore.deleteCard(local.id)
        CharacterLibraryStore.switchAccount("bob")
        assertTrue(CharacterLibraryStore.state.value.cards.isEmpty())
        assertTrue(CharacterLibraryStore.state.value.deletedCardIds.isEmpty())
        CharacterLibraryStore.add(CharacterPersona(name = "Bob"))
        CharacterLibraryStore.switchAccount("alice")
        assertTrue(CharacterLibraryStore.state.value.cards.isEmpty())
        assertTrue(CharacterLibraryStore.state.value.deletedCardIds.contains(local.id))
    }

    @Test fun absentCapabilitiesDeferOnlyNewObjectsAndProtectedMedia() {
        val old = SyncCapabilities()
        assertFalse(old.allows(SyncKind.CARD))
        assertFalse(old.allows(SyncKind.CARDFOLDER))
        assertFalse(old.allows(SyncKind.CONV, groupConversation = true))
        assertFalse(old.allows(SyncKind.IMG, protectedImage = true))
        assertTrue(old.allows(SyncKind.CONV))
        assertTrue(old.allows(SyncKind.IMG))
        val current = SyncCapabilities(characterLibrary = 1, groupChat = 1)
        assertTrue(current.allows(SyncKind.CARD))
        assertTrue(current.allows(SyncKind.CONV, groupConversation = true))
    }

    @Test fun memoriesWithSameTextOrIdNeverMergeAcrossMembers() {
        val a = MemoryEntry(id = "same", summary = "shared words", keywords = emptyList(), groupMemberId = "a")
        val b = a.copy(groupMemberId = "b")
        assertEquals(setOf("a", "b"), Merge.mergeMemories(listOf(a), listOf(b)).map { it.groupMemberId }.toSet())
        assertEquals("a", Merge.mergeMemories(listOf(a), listOf(a.copy(groupMemberId = null))).single().groupMemberId)
    }

    @Test fun freshCloudGroupSettingsDoNotLoseToANewerLocalChatTimestamp() {
        val group = GroupChatProfile(members = listOf(GroupMember("a", CharacterPersona(name = "A"))))
        val local = Conversation(id = "g", updatedAt = 900, groupChat = group.copy(announcement = "old"))
        val remote = local.copy(updatedAt = 800, groupChat = group.copy(announcement = "published", announcementAt = 1000))
        assertEquals("published", Merge.mergeConv(local, remote).conv.groupChat!!.announcement)
    }
}
