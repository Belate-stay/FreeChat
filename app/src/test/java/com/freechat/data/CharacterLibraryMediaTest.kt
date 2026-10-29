package com.freechat.data

import com.freechat.model.*
import com.freechat.sync.ImageSync
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CharacterLibraryMediaTest {
    private fun fresh(): File = Files.createTempDirectory("library-media-v1216-").toFile().also {
        LocalStore.setWriteListener(null)
        LocalStore.init(it)
    }

    @Test fun deletingChatKeepsLibraryOwnedAvatarAndFinalCardDeletionReleasesIt() {
        val dir = fresh()
        val avatar = File(dir, "avatar_shared.jpg").apply { writeBytes(byteArrayOf(3, 2, 1)) }
        val card = CharacterLibraryStore.add(CharacterPersona(name = "A", avatarPath = avatar.absolutePath))
        val conversation = Conversation(id = "chat", characterProfile = card.persona.toProfile())
        LocalStore.writeText(LocalStore.conversationsFile(), AppJson.gson.toJson(listOf(conversation)))
        ConversationDeletion.removeLocal("chat")
        assertTrue("Card still owns the reused avatar", avatar.isFile)
        CharacterLibraryStore.deleteCard(card.id)
        assertFalse("Final owner deletion releases payload", avatar.exists())
    }

    @Test fun imageReachabilityIncludesLibraryAndNestedGroupAndHistoricalSenderAvatars() {
        val dir = fresh()
        val hash = "0123456789abcdef"
        val ref = "img:$hash"
        val card = CharacterLibraryStore.add(CharacterPersona(name = "A", avatarPath = ref))
        assertTrue(ImageSync.isReferencedLocally(hash))
        val member = GroupMember("member", card.persona)
        LocalStore.writeText(LocalStore.conversationsFile(), AppJson.gson.toJson(listOf(
            Conversation(id = "group", groupChat = GroupChatProfile(members = listOf(member))))))
        CharacterLibraryStore.deleteCard(card.id)
        assertTrue(ImageSync.isReferencedLocally(hash))
        LocalStore.writeText(LocalStore.conversationsFile(), "[]")
        LocalStore.writeText(LocalStore.messagesFile("group"), AppJson.gson.toJson(listOf(
            Message(role = Role.ASSISTANT, content = "old", groupSenderAvatar = ref))))
        assertTrue(ImageSync.isReferencedLocally(hash))
        assertTrue(dir.isDirectory)
    }

    @Test fun remoteCardUpdateReleasesTheFinalOriginalAndSyncPayload() {
        val dir = fresh()
        val original = File(dir, "remote_old_avatar.jpg").apply { writeBytes(byteArrayOf(7, 4, 1)) }
        val ref = ImageSync.ensureCachedFromFile(original.absolutePath)!!
        val payload = ImageSync.cacheFile(ImageSync.hashOfRef(ref))
        val card = CharacterLibraryStore.add(CharacterPersona(name = "A", avatarPath = original.absolutePath))
        CharacterLibraryStore.applyRemoteCard(card.copy(persona = card.persona.copy(avatarPath = ""), updatedAt = card.updatedAt + 1), false)
        assertFalse("A remote replacement must release the final original", original.exists())
        assertFalse("A remote replacement must release the final sync payload", payload.exists())
    }

    @Test fun remoteCardUpdateKeepsSharedMediaUntilTheFinalOwnerChanges() {
        val dir = fresh()
        val original = File(dir, "remote_shared_avatar.jpg").apply { writeBytes(byteArrayOf(9, 5, 2)) }
        val ref = ImageSync.ensureCachedFromFile(original.absolutePath)!!
        val payload = ImageSync.cacheFile(ImageSync.hashOfRef(ref))
        val a = CharacterLibraryStore.add(CharacterPersona(name = "A", avatarPath = original.absolutePath))
        val b = CharacterLibraryStore.add(CharacterPersona(name = "B", avatarPath = original.absolutePath))
        CharacterLibraryStore.applyRemoteCard(a.copy(persona = a.persona.copy(avatarPath = ""), updatedAt = a.updatedAt + 1), false)
        assertTrue("The second card still owns the original", original.exists())
        assertTrue("The second card still owns the sync payload", payload.exists())
        CharacterLibraryStore.applyRemoteCard(b.copy(persona = b.persona.copy(avatarPath = ""), updatedAt = b.updatedAt + 1), false)
        assertFalse("The final remote owner no longer uses the original", original.exists())
        assertFalse("The final remote owner no longer uses the sync payload", payload.exists())
    }

    @Test fun batchDeletionReleasesFinalSharedMediaAndKeepsConversationOwnedMedia() {
        val dir = fresh()
        val shared = File(dir, "batch_shared.jpg").apply { writeBytes(byteArrayOf(5, 8, 1)) }
        val ref = ImageSync.ensureCachedFromFile(shared.absolutePath)!!
        val payload = ImageSync.cacheFile(ImageSync.hashOfRef(ref))
        val a = CharacterLibraryStore.add(CharacterPersona(name = "A", avatarPath = shared.absolutePath))
        val b = CharacterLibraryStore.add(CharacterPersona(name = "B", avatarPath = shared.absolutePath))
        val retained = File(dir, "batch_conversation.jpg").apply { writeBytes(byteArrayOf(6, 9, 2)) }
        val c = CharacterLibraryStore.add(CharacterPersona(name = "C", avatarPath = retained.absolutePath),
            sourceConversationId = "source")
        val conversations = AppJson.gson.toJson(listOf(Conversation(id = "source", characterProfile = c.persona.toProfile())))
        LocalStore.writeText(LocalStore.conversationsFile(), conversations)

        assertEquals(setOf(a.id, b.id, c.id), CharacterLibraryStore.deleteUnclassifiedCards(setOf(a.id, b.id, c.id)))

        assertFalse("Removing both final card owners releases the original", shared.exists())
        assertFalse("Removing both final card owners releases the cached payload", payload.exists())
        assertTrue("An existing conversation still owns its image", retained.isFile)
        assertEquals(conversations, LocalStore.readText(LocalStore.conversationsFile()))
    }
}
