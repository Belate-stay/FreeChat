package com.freechat.sync

import com.freechat.data.*
import com.freechat.model.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class StorySyncTest {
    private fun story() = StoryProfile(members = listOf(StoryMember("s1", CharacterPersona(name = "青", avatarPath = "img:0123456789abcdef"))),
        background = "风港", worldRules = "无魔法", languageModelId = "device-language", visualModelId = "device-image")
    @Test fun wireRetainsCastWorldAndMediaButOnlyLocalModelBindings() {
        LocalStore.setWriteListener(null)
        LocalStore.init(Files.createTempDirectory("story-sync-").toFile())
        val conv = Conversation(id = "story", mode = ChatMode.COMPANION, storyProfile = story())
        val wire = Wire.convToWire(conv)
        assertEquals("", wire.getAsJsonObject("storyProfile").get("languageModelId").asString)
        assertEquals("", wire.getAsJsonObject("storyProfile").get("visualModelId").asString)
        val remote = Wire.convFromWire(wire, null)!!
        assertEquals("s1", remote.storyProfile!!.members.single().id)
        assertEquals("风港", remote.storyProfile!!.background)
        assertEquals("", remote.storyProfile!!.languageModelId)
        assertEquals("device-language", Wire.convFromWire(wire, conv)!!.storyProfile!!.languageModelId)
    }
    @Test fun settingsCommitWinsOverAChatTimestampAndOldClientCannotEraseTheStory() {
        val a = Conversation(id = "s", title = "旧标题", updatedAt = 1000, storyProfile = story().copy(updatedAt = 10))
        val b = a.copy(title = "新标题", updatedAt = 800, storyProfile = story().copy(worldRules = "潮汐倒流", updatedAt = 20))
        val merged = Merge.mergeConv(a, b).conv
        assertEquals("潮汐倒流", merged.storyProfile!!.worldRules)
        assertEquals("新标题", merged.title)
        assertNull(merged.characterProfile)
        assertNull(merged.groupChat)
        assertEquals(a.storyProfile, Merge.mergeConv(a, a.copy(updatedAt = 2000, storyProfile = null)).conv.storyProfile)
    }
    @Test fun storySnapshotKeepsImagesAfterPoolDeletionAndReleasesFinalOwnershipOnDeletion() {
        LocalStore.setWriteListener(null)
        LocalStore.init(Files.createTempDirectory("story-owned-").toFile())
        val card = CharacterLibraryStore.add(story().members.single().persona)
        val conv = Conversation(id = "s", storyProfile = story())
        LocalStore.writeText(LocalStore.conversationsFile(), AppJson.gson.toJson(listOf(conv)))
        CharacterLibraryStore.deleteCard(card.id)
        assertTrue(ImageSync.isReferencedLocally("0123456789abcdef"))
        ConversationDeletion.removeLocal("s")
        assertFalse(ImageSync.isReferencedLocally("0123456789abcdef"))
    }
    @Test fun olderServersDeferStoryUploadsWithoutAcknowledgingThem() {
        assertFalse(SyncCapabilities(characterLibrary = 1, groupChat = 1).allows(SyncKind.CONV, storyConversation = true))
        assertTrue(SyncCapabilities(story = 1).allows(SyncKind.CONV, storyConversation = true))
    }
}
