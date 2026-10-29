package com.freechat.core

import com.freechat.model.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class CharacterLibraryTest {
    @Test fun startChatEligibilityUsesPersistedSourceRatherThanCurrentConversationPresence() {
        val direct = CharacterCard("direct", CharacterPersona(name = "A"))
        val imported = direct.copy(id = "imported", sourceConversationId = "original-chat")
        assertTrue(direct.canStartConversation)
        assertTrue(direct.copy(sourceConversationId = "", sourceMemberId = " ").canStartConversation)
        assertFalse(imported.canStartConversation)
        assertFalse(direct.copy(sourceMemberId = "group-member").canStartConversation)
        val restored = Gson().fromJson(Gson().toJson(imported), CharacterCard::class.java)
        assertFalse(restored.copy(persona = restored.persona.copy(name = "Edited")).canStartConversation)
        assertFalse(Gson().toJson(direct).contains("canStartConversation"))
    }

    @Test fun cardsSerializeOnlyPersonaAndCreateFreshModeSettings() {
        val source = CharacterProfile(name = "角色", languageModelId = "private", visionModelId = "vision",
            proactiveEnabled = true, replyBufferEnabled = false, dialogueMode = DialogueMode.PLOT,
            deepThinking = true, enableWebSearch = true, personaPrompt = "人物秘密", openingLines = listOf("示例"))
        val persona = CharacterPersona.from(source)
        val json = Gson().toJson(CharacterCard("c1", persona))
        listOf("languageModelId", "visionModelId", "visualModelId", "proactiveEnabled", "replyBufferEnabled",
            "deepThinking", "enableWebSearch", "dialogueMode", "timePerception", "aiCreativity").forEach {
            assertFalse("Part 2 leaked into card: $it", json.contains(it))
        }
        for (mode in listOf(DialogueMode.WECHAT, DialogueMode.ACTION, DialogueMode.PLOT)) {
            val fresh = persona.toProfile(mode)
            assertEquals(mode, fresh.dialogueMode)
            assertEquals("", fresh.languageModelId)
            assertEquals("", fresh.visionModelId)
            assertFalse(fresh.proactiveEnabled)
            assertTrue(fresh.replyBufferEnabled)
            assertFalse(fresh.deepThinking)
            assertNull(fresh.enableWebSearch)
            assertEquals("人物秘密", fresh.personaPrompt)
        }
    }

    @Test fun copiedPersonaDoesNotShareMutableSourceLists() {
        val lines = mutableListOf("初始")
        val profile = CharacterProfile(name = "A", appearanceImagePaths = lines)
        val persona = CharacterPersona.from(profile)
        lines += "后来"
        assertEquals(listOf("初始"), persona.appearanceImagePaths)
        assertEquals(listOf("初始"), persona.toProfile().appearanceImagePaths)
    }

    @Test fun oldConversationJsonKeepsSingleChatDefaults() {
        val gson = Gson()
        assertNull(gson.fromJson("{\"id\":\"legacy\"}", Conversation::class.java).groupChat)
        val message = Message(role = Role.USER, content = "hello")
        assertNull(message.groupMemberId)
        assertEquals(emptyList<String>(), message.mentionedMemberIds)
        assertNull(MemoryEntry(summary = "old", keywords = emptyList()).groupMemberId)
    }
}
