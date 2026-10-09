package com.freechat.sync

import com.freechat.data.AppJson
import com.freechat.data.LocalStore
import com.freechat.data.ConversationDeletion
import com.freechat.data.MemoryManager
import com.freechat.data.PerConvStore
import com.freechat.model.CharacterProfile
import com.freechat.model.Conversation
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.freechat.model.PerConvSettings
import com.freechat.model.Role
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ConversationDeletionRegressionTest {
    @Before fun freshStore() {
        LocalStore.init(Files.createTempDirectory("conversation-delete").toFile())
        LocalStore.setWriteListener(null)
    }

    private fun seed(conv: Conversation, messages: List<Message> = emptyList()) {
        val rows = Relay.conversationsFromDisk().filterNot { it.id == conv.id } + conv
        LocalStore.writeText(LocalStore.conversationsFile(), AppJson.gson.toJson(rows))
        LocalStore.writeText(LocalStore.messagesFile(conv.id), AppJson.gson.toJson(messages))
        LocalStore.writeText(LocalStore.memoryFile(conv.id), "[]")
        PerConvStore.put(conv.id, PerConvSettings(enableWebSearch = true))
        LocalStore.writeText(LocalStore.avatarHashFile(conv.id), "avatar-hash")
    }

    @Test fun deletingConversationRemovesAllItsLocalPayloads() = runBlocking {
        seed(Conversation(id = "remove-all", characterProfile = CharacterProfile(name = "Fixture")))
        Relay.applyConversations(listOf(Relay.ConvOp.Remove("remove-all")))
        assertTrue(Relay.conversationsFromDisk().isEmpty())
        assertFalse("The message file must be deleted with its conversation", LocalStore.messagesFile("remove-all").exists())
        assertFalse(LocalStore.memoryFile("remove-all").exists())
        assertFalse(LocalStore.avatarHashFile("remove-all").exists())
        assertFalse(PerConvStore.load().containsKey("remove-all"))
    }

    @Test fun lateCloudUpsertAndBackgroundWritesCannotResurrectDeletedConversation() = runBlocking {
        val old = Conversation(id = "late-cloud", characterProfile = CharacterProfile(name = "Old persona"))
        val oldMessage = Message(id = "late-message", role = Role.ASSISTANT, content = "Old reply")
        seed(old, listOf(oldMessage))
        Relay.applyConversations(listOf(Relay.ConvOp.Remove(old.id)))
        // Covers both a late cloud response and an API completion arriving after removal.
        Relay.applyConversations(listOf(Relay.ConvOp.Upsert(old)))
        Relay.applyMessages(old.id, listOf(oldMessage))
        Relay.applyMemories(old.id, listOf(MemoryEntry(summary = "Old memory", keywords = emptyList())))
        Relay.applyPerConv(old.id, JsonObject().apply { addProperty("enableWebSearch", true) })
        LocalStore.writeText(LocalStore.messagesFile(old.id), AppJson.gson.toJson(listOf(oldMessage)))
        LocalStore.writeText(LocalStore.memoryFile(old.id), "[]")
        PerConvStore.put(old.id, PerConvSettings(enableWebSearch = true))
        assertTrue("An old cloud CONV payload must not recreate the row", Relay.conversationsFromDisk().isEmpty())
        assertFalse(LocalStore.messagesFile(old.id).exists())
        assertFalse(LocalStore.memoryFile(old.id).exists())
        assertFalse(PerConvStore.load().containsKey(old.id))
    }

    @Test fun deletionRemovesGeneratedOriginalAndCacheButPreservesSharedAndExternalFiles() = runBlocking {
        val root = LocalStore.filesDir()
        val own = File(root, "generated.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val shared = File(root, "shared.png").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val outside = Files.createTempFile("user-gallery", ".png").toFile().apply { writeBytes(byteArrayOf(7)) }
        val ownHash = Wire.hashOf(own.readBytes())
        val sharedHash = Wire.hashOf(shared.readBytes())
        ImageSync.cacheFile(ownHash).writeBytes(byteArrayOf(11))
        ImageSync.cacheFile(sharedHash).writeBytes(byteArrayOf(12))
        seed(Conversation(id = "removed"), listOf(Message(role = Role.ASSISTANT, content = "",
            imageUrls = listOf(own.absolutePath, shared.absolutePath, outside.absolutePath))))
        seed(Conversation(id = "survives"), listOf(Message(role = Role.USER, content = "",
            imagePaths = listOf(ImageSync.refOf(sharedHash)))))
        Relay.applyConversations(listOf(Relay.ConvOp.Remove("removed")))
        assertFalse("Generated originals in imageUrls must also be reclaimed", own.exists())
        assertFalse("The compressed cloud cache must not keep an unused image alive", ImageSync.cacheFile(ownHash).exists())
        assertTrue("The same image in another conversation must survive", shared.exists())
        assertTrue(ImageSync.cacheFile(sharedHash).exists())
        assertTrue("Never delete a user's external original", outside.exists())
        assertEquals(listOf("survives"), Relay.conversationsFromDisk().map { it.id })
    }

    @Test fun lateWholeFileSnapshotsCannotPutDeletedRowsBack() = runBlocking {
        val old = Conversation(id = "stale-snapshot")
        val survivor = Conversation(id = "live-conversation")
        seed(old)
        seed(survivor)
        Relay.applyConversations(listOf(Relay.ConvOp.Remove(old.id)))
        // A second holder still has an older list: the storage boundary must fence it too.
        LocalStore.writeText(LocalStore.conversationsFile(), AppJson.gson.toJson(listOf(old, survivor)))
        PerConvStore.save(mapOf(old.id to PerConvSettings(), survivor.id to PerConvSettings()))
        assertEquals(listOf(survivor.id), Relay.conversationsFromDisk().map { it.id })
        assertEquals(setOf(survivor.id), PerConvStore.load().keys)
        LocalStore.init(LocalStore.filesDir())
        Relay.applyConversations(listOf(Relay.ConvOp.Upsert(old)))
        assertEquals("The deletion fence must survive a fresh store load", listOf(survivor.id), Relay.conversationsFromDisk().map { it.id })
    }

    @Test fun deletionIntentImmediatelyHidesDataBeforeBackgroundCleanupRuns() {
        val id = "cleanup-pending"
        seed(Conversation(id = id), listOf(Message(role = Role.ASSISTANT, content = "Must not still show", favorited = true)))
        LocalStore.writeText(LocalStore.memoryFile(id), AppJson.gson.toJson(listOf(
            MemoryEntry(summary = "Old private memory", keywords = emptyList()))))
        ConversationDeletion.record(id)
        assertTrue("A deleted chat must not appear in a read or favorites scan during IO cleanup", Relay.messagesFromDisk(id).isEmpty())
        assertTrue(MemoryManager().load(id).isEmpty())
    }

    @Test fun cleanupRemovesPrivateAttachmentsAndPersonaImagesWithoutDeletingSharedAvatar() = runBlocking {
        val root = LocalStore.filesDir()
        val avatar = File(root, "avatar-shared.jpg").apply { writeBytes(byteArrayOf(1)) }
        val portrait = File(root, "appearance-own.jpg").apply { writeBytes(byteArrayOf(2)) }
        val attachment = File(root, "attachment.json").apply { writeText("private persona") }
        seed(Conversation(id = "persona", characterProfile = CharacterProfile(avatarPath = avatar.path,
            appearanceImagePaths = listOf(portrait.path))), listOf(Message(role = Role.USER, content = "", attachmentPath = attachment.path)))
        seed(Conversation(id = "friend", characterProfile = CharacterProfile(avatarPath = avatar.path)))
        Relay.applyConversations(listOf(Relay.ConvOp.Remove("persona")))
        assertTrue(avatar.exists())
        assertFalse(portrait.exists())
        assertFalse(attachment.exists())
    }

    @Test fun restartResumesPendingCleanupEvenAfterAnOldHolderSavesTheFilteredConversationList() {
        val image = File(LocalStore.filesDir(), "pending-persona.jpg").apply { writeBytes(byteArrayOf(8)) }
        val old = Conversation(id = "interrupted-delete", characterProfile = CharacterProfile(avatarPath = image.path))
        seed(old)
        ConversationDeletion.record(old.id)
        LocalStore.writeText(LocalStore.conversationsFile(), "[]")
        LocalStore.init(LocalStore.filesDir())
        assertEquals(setOf(old.id), ConversationDeletion.pendingIds())
        ConversationDeletion.removeLocal(old.id)
        assertFalse(image.exists())
        assertFalse(LocalStore.messagesFile(old.id).exists())
        assertTrue(ConversationDeletion.pendingIds().isEmpty())
        assertTrue(ConversationDeletion.contains(old.id))
    }
}
