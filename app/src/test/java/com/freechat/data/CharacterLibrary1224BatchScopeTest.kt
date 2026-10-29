package com.freechat.data

import com.freechat.model.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CharacterLibrary1224BatchScopeTest {
    private fun fresh() {
        LocalStore.setWriteListener(null)
        LocalStore.init(Files.createTempDirectory("library-v1224-").toFile())
        CharacterLibraryStore.reload()
    }

    @Test fun deletingFolderSelectionUsesLatestMembershipAndOneWrite() {
        fresh()
        val source = CharacterLibraryStore.createFolder("source")
        val other = CharacterLibraryStore.createFolder("other")
        val a = CharacterLibraryStore.add(CharacterPersona(name = "A"), source.id, sourceConversationId = "chat")
        val b = CharacterLibraryStore.add(CharacterPersona(name = "B"), source.id)
        val moved = CharacterLibraryStore.add(CharacterPersona(name = "Moved"), source.id)
        val keep = CharacterLibraryStore.add(CharacterPersona(name = "Keep"), other.id)
        CharacterLibraryStore.applyRemoteCard(moved.copy(folderId = other.id, updatedAt = moved.updatedAt + 1), false)
        val conversations = AppJson.gson.toJson(listOf(Conversation(id = "chat", characterProfile = a.persona.toProfile())))
        LocalStore.writeText(LocalStore.conversationsFile(), conversations)
        var writes = 0
        LocalStore.setWriteListener { if (it == LocalStore.CHARACTER_LIBRARY_KEY) writes++ }
        try {
            assertEquals(setOf(a.id, b.id), CharacterLibraryStore.deleteCards(setOf(a.id, b.id, moved.id, keep.id, "missing"), source.id))
            val state = CharacterLibraryStore.state.value
            assertEquals(setOf(moved.id, keep.id), state.cards.map { it.id }.toSet())
            assertEquals(setOf(a.id, b.id), state.deletedCardIds)
            assertEquals(conversations, LocalStore.readText(LocalStore.conversationsFile()))
            assertEquals(1, writes)
            assertTrue(CharacterLibraryStore.deleteCards(setOf(a.id, b.id), source.id).isEmpty())
            assertEquals(1, writes)
            CharacterLibraryStore.reload()
            assertEquals(state, CharacterLibraryStore.state.value)
        } finally {
            LocalStore.setWriteListener(null)
        }
    }

    @Test fun folderBatchMovesToAnotherFolderThenUnclassifiedWithoutTouchingOtherCards() {
        fresh()
        val source = CharacterLibraryStore.createFolder("source")
        val target = CharacterLibraryStore.createFolder("target")
        val a = CharacterLibraryStore.add(CharacterPersona(name = "A", personalityText = "Persona"), source.id,
            sourceConversationId = "chat", sourceMemberId = "member")
        val b = CharacterLibraryStore.add(CharacterPersona(name = "B"), source.id)
        val outside = CharacterLibraryStore.add(CharacterPersona(name = "Outside"))
        val ids = setOf(a.id, b.id, outside.id)
        var writes = 0
        LocalStore.setWriteListener { if (it == LocalStore.CHARACTER_LIBRARY_KEY) writes++ }
        try {
            assertEquals(setOf(a.id, b.id), CharacterLibraryStore.moveCards(ids, target.id, source.id))
            val moved = CharacterLibraryStore.state.value.cards.single { it.id == a.id }
            assertEquals(target.id, moved.folderId)
            assertEquals(a.persona, moved.persona)
            assertEquals(a.sourceConversationId, moved.sourceConversationId)
            assertEquals(a.sourceMemberId, moved.sourceMemberId)
            assertEquals(a.createdAt, moved.createdAt)
            assertTrue(moved.updatedAt > a.updatedAt)
            assertEquals(outside, CharacterLibraryStore.state.value.cards.single { it.id == outside.id })
            assertEquals(1, writes)
            assertEquals(setOf(a.id, b.id), CharacterLibraryStore.moveCards(ids, null, target.id))
            assertTrue(CharacterLibraryStore.state.value.cards.all { it.folderId == null })
            assertEquals(2, writes)
            assertTrue(CharacterLibraryStore.moveCards(ids, null, target.id).isEmpty())
            assertEquals(2, writes)
            assertFalse(LocalStore.conversationsFile().exists())
        } finally {
            LocalStore.setWriteListener(null)
        }
    }

    @Test fun missingSourceAndDestinationFoldersPreserveCardsWithoutWrites() {
        fresh()
        val source = CharacterLibraryStore.createFolder("source")
        val deleted = CharacterLibraryStore.createFolder("deleted")
        val card = CharacterLibraryStore.add(CharacterPersona(name = "A"), source.id)
        CharacterLibraryStore.deleteFolder(deleted.id)
        val before = CharacterLibraryStore.snapshot()
        var writes = 0
        LocalStore.setWriteListener { if (it == LocalStore.CHARACTER_LIBRARY_KEY) writes++ }
        try {
            assertTrue(CharacterLibraryStore.moveCards(setOf(card.id), "missing", source.id).isEmpty())
            assertTrue(CharacterLibraryStore.moveCards(setOf(card.id), deleted.id, source.id).isEmpty())
            assertTrue(CharacterLibraryStore.deleteCards(setOf(card.id), "missing").isEmpty())
            assertTrue(CharacterLibraryStore.moveCards(setOf(card.id), null, "missing").isEmpty())
            assertEquals(before, CharacterLibraryStore.snapshot())
            assertEquals(0, writes)
        } finally {
            LocalStore.setWriteListener(null)
        }
    }

    @Test fun consecutiveImportsIntoFolderAndUnclassifiedRemainIndependentAndDeduplicated() {
        fresh()
        val folder = CharacterLibraryStore.createFolder("target")
        val a = CharacterLibraryStore.add(CharacterPersona(name = "A"), folder.id, sourceConversationId = "a")
        val b = CharacterLibraryStore.add(CharacterPersona(name = "B"), folder.id, sourceConversationId = "b")
        val c = CharacterLibraryStore.add(CharacterPersona(name = "C"), sourceConversationId = "c")
        val again = CharacterLibraryStore.add(CharacterPersona(name = "A changed"), sourceConversationId = "a")
        assertEquals(a.id, again.id)
        assertEquals(listOf(a, b, c), CharacterLibraryStore.state.value.cards)
        CharacterLibraryStore.reload()
        assertEquals(listOf(a, b, c), CharacterLibraryStore.state.value.cards)
        assertFalse(LocalStore.conversationsFile().exists())
    }
}
