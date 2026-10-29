package com.freechat.data

import com.freechat.model.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CharacterLibraryStoreTest {
    private fun fresh() {
        LocalStore.setWriteListener(null)
        LocalStore.init(Files.createTempDirectory("library-v1216-").toFile())
        CharacterLibraryStore.reload()
    }

    @Test fun importsDeduplicateSourcesAndNeverCreateConversations() {
        fresh()
        val first = CharacterLibraryStore.add(CharacterPersona(name = "A"), sourceConversationId = "chat")
        val again = CharacterLibraryStore.add(CharacterPersona(name = "changed"), sourceConversationId = "chat")
        assertEquals(first.id, again.id)
        assertEquals(1, CharacterLibraryStore.state.value.cards.size)
        assertFalse(LocalStore.conversationsFile().exists())
        CharacterLibraryStore.reload()
        assertEquals(first, CharacterLibraryStore.state.value.cards.single())
    }

    @Test fun deleteFolderMovesCardsToUnclassifiedAndKeepsTombstones() {
        fresh()
        val folder = CharacterLibraryStore.createFolder("朋友")
        val other = CharacterLibraryStore.createFolder("同事")
        val a = CharacterLibraryStore.add(CharacterPersona(name = "A"), folder.id)
        val b = CharacterLibraryStore.add(CharacterPersona(name = "B"), folder.id)
        CharacterLibraryStore.moveCard(b.id, other.id)
        CharacterLibraryStore.deleteFolder(folder.id)
        val state = CharacterLibraryStore.state.value
        assertNull(state.cards.single { it.id == a.id }.folderId)
        assertEquals(other.id, state.cards.single { it.id == b.id }.folderId)
        assertTrue(state.deletedFolderIds.contains(folder.id))
        CharacterLibraryStore.deleteCard(a.id)
        assertTrue(CharacterLibraryStore.state.value.deletedCardIds.contains(a.id))
        assertFalse(CharacterLibraryStore.update(a))
    }

    @Test fun independentSnapshotsAndTombstonesSurviveReload() {
        fresh()
        val card = CharacterLibraryStore.add(CharacterPersona(name = "Original"))
        val snapshot = card.persona.toProfile()
        assertTrue(CharacterLibraryStore.update(card.copy(persona = card.persona.copy(name = "Edited"))))
        assertEquals("Original", snapshot.name)
        CharacterLibraryStore.deleteCard(card.id)
        CharacterLibraryStore.reload()
        assertTrue(CharacterLibraryStore.state.value.cards.isEmpty())
        assertTrue(CharacterLibraryStore.state.value.deletedCardIds.contains(card.id))
    }

    @Test fun staleUiFolderTargetsFallBackOnlyAfterExplicitDeletion() {
        fresh()
        val folder = CharacterLibraryStore.createFolder("gone")
        CharacterLibraryStore.deleteFolder(folder.id)
        val card = CharacterLibraryStore.add(CharacterPersona(name = "A"), folder.id)
        assertNull(card.folderId)
        CharacterLibraryStore.moveCard(card.id, folder.id)
        assertNull(CharacterLibraryStore.state.value.cards.single().folderId)
    }

    @Test fun batchDeletionUsesLatestUnclassifiedCardsAndWritesOnlyOnce() {
        fresh()
        val folder = CharacterLibraryStore.createFolder("keep")
        val a = CharacterLibraryStore.add(CharacterPersona(name = "A"), sourceConversationId = "source")
        val b = CharacterLibraryStore.add(CharacterPersona(name = "B"))
        val keep = CharacterLibraryStore.add(CharacterPersona(name = "Keep"))
        val moved = CharacterLibraryStore.add(CharacterPersona(name = "Moved"))
        val deleted = CharacterLibraryStore.add(CharacterPersona(name = "Deleted"))
        CharacterLibraryStore.applyRemoteCard(moved.copy(folderId = folder.id, updatedAt = moved.updatedAt + 1), false)
        CharacterLibraryStore.applyRemoteCardDeletion(deleted.id)
        val conversations = AppJson.gson.toJson(listOf(Conversation(id = "source", characterProfile = a.persona.toProfile())))
        LocalStore.writeText(LocalStore.conversationsFile(), conversations)
        var writes = 0
        LocalStore.setWriteListener { if (it == LocalStore.CHARACTER_LIBRARY_KEY) writes++ }
        try {
            val selected = setOf(a.id, b.id, moved.id, deleted.id, "missing")
            assertEquals(setOf(a.id, b.id), CharacterLibraryStore.deleteUnclassifiedCards(selected))
            val state = CharacterLibraryStore.state.value
            assertEquals(setOf(keep.id, moved.id), state.cards.map { it.id }.toSet())
            assertEquals(folder.id, state.cards.single { it.id == moved.id }.folderId)
            assertEquals(setOf(a.id, b.id, deleted.id), state.deletedCardIds)
            assertEquals(conversations, LocalStore.readText(LocalStore.conversationsFile()))
            assertEquals(1, writes)
            assertTrue(CharacterLibraryStore.deleteUnclassifiedCards(selected).isEmpty())
            assertEquals(1, writes)
            CharacterLibraryStore.reload()
            assertEquals(state, CharacterLibraryStore.state.value)
        } finally {
            LocalStore.setWriteListener(null)
        }
    }

    @Test fun batchMoveKeepsPersonasAndIgnoresCardsAlreadyMovedBySync() {
        fresh()
        val target = CharacterLibraryStore.createFolder("target")
        val other = CharacterLibraryStore.createFolder("other")
        val a = CharacterLibraryStore.add(CharacterPersona(name = "A"), sourceConversationId = "source")
        val b = CharacterLibraryStore.add(CharacterPersona(name = "B"))
        val stale = CharacterLibraryStore.add(CharacterPersona(name = "Stale"))
        CharacterLibraryStore.applyRemoteCard(stale.copy(folderId = other.id, updatedAt = stale.updatedAt + 1), false)
        val before = CharacterLibraryStore.state.value
        var writes = 0
        LocalStore.setWriteListener { if (it == LocalStore.CHARACTER_LIBRARY_KEY) writes++ }
        try {
            val selected = setOf(a.id, b.id, stale.id, "missing")
            assertEquals(setOf(a.id, b.id), CharacterLibraryStore.moveUnclassifiedCards(selected, target.id))
            val after = CharacterLibraryStore.state.value
            for (card in listOf(a, b)) {
                val moved = after.cards.single { it.id == card.id }
                assertEquals(target.id, moved.folderId)
                assertEquals(card.persona, moved.persona)
                assertEquals(card.sourceConversationId, moved.sourceConversationId)
                assertEquals(card.createdAt, moved.createdAt)
                assertTrue(moved.updatedAt > card.updatedAt)
            }
            assertEquals(before.cards.single { it.id == stale.id }, after.cards.single { it.id == stale.id })
            assertEquals(before.folders, after.folders)
            assertEquals(before.deletedCardIds, after.deletedCardIds)
            assertEquals(1, writes)
            assertTrue(CharacterLibraryStore.moveUnclassifiedCards(selected, target.id).isEmpty())
            assertEquals(1, writes)
            assertFalse(LocalStore.conversationsFile().exists())
        } finally {
            LocalStore.setWriteListener(null)
        }
    }

    @Test fun batchMoveToMissingOrDeletedFolderPreservesAllDataWithoutWriting() {
        fresh()
        val deleted = CharacterLibraryStore.createFolder("gone")
        val card = CharacterLibraryStore.add(CharacterPersona(name = "A"))
        CharacterLibraryStore.deleteFolder(deleted.id)
        val before = CharacterLibraryStore.snapshot()
        var writes = 0
        LocalStore.setWriteListener { if (it == LocalStore.CHARACTER_LIBRARY_KEY) writes++ }
        try {
            assertTrue(CharacterLibraryStore.moveUnclassifiedCards(setOf(card.id), "missing").isEmpty())
            assertTrue(CharacterLibraryStore.moveUnclassifiedCards(setOf(card.id), deleted.id).isEmpty())
            assertTrue(CharacterLibraryStore.deleteUnclassifiedCards(emptySet()).isEmpty())
            assertTrue(CharacterLibraryStore.deleteUnclassifiedCards(setOf("missing")).isEmpty())
            assertEquals(before, CharacterLibraryStore.snapshot())
            assertEquals(0, writes)
        } finally {
            LocalStore.setWriteListener(null)
        }
    }
}
