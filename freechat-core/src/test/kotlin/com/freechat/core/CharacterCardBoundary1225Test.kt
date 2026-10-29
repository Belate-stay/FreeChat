package com.freechat.core

import com.freechat.data.AppJson
import com.freechat.data.healed
import com.freechat.model.*
import org.junit.Assert.*
import org.junit.Test

class CharacterCardBoundary1225Test {
    private val dialogueKeys = listOf("openingLine", "openingLines", "supportingCast", "worldRules", "userPersona")
    private val legacyPersona = """{"name":"澄川","personalityText":"安静而坦率","relationshipPreset":"朋友","relationshipText":"同学","personaPrompt":"人物的措辞与性格","openingLine":"OLD_OPEN","openingLines":["OLD_OPENS"],"supportingCast":"OLD_CAST","worldRules":"OLD_RULES","userPersona":"OLD_USER"}"""

    @Test fun importingPrivateCharacterKeepsRelationshipButDropsDialogueSettings() {
        val original = CharacterProfile(name = "澄川", personalityText = "坦率", relationshipPreset = "朋友",
            relationshipText = "同学", personaPrompt = "人物措辞", openingLine = "PRIVATE_OPEN",
            openingLines = listOf("PRIVATE_OPENS"), supportingCast = "PRIVATE_CAST", worldRules = "PRIVATE_RULES", userPersona = "PRIVATE_USER")
        val before = AppJson.gson.toJson(original)
        val card = CharacterPersona.from(original)
        val json = AppJson.gson.toJsonTree(card).asJsonObject
        dialogueKeys.forEach { assertFalse("Card schema must not contain $it", json.has(it)) }
        assertEquals("朋友", card.relationshipPreset)
        assertEquals("同学", card.relationshipText)
        assertEquals("人物措辞", card.personaPrompt)
        val fresh = card.toProfile()
        assertTrue(fresh.openingLine.isBlank() && fresh.openingLines.isEmpty())
        assertTrue(fresh.supportingCast.isBlank() && fresh.worldRules.isBlank() && fresh.userPersona.isBlank())
        assertEquals("Source private conversation must remain intact", before, AppJson.gson.toJson(original))
        val privatePrompt = CompanionPrompts.buildCompanionSystemPrompt(original, emptyList())
        listOf("PRIVATE_CAST", "PRIVATE_RULES", "PRIVATE_USER").forEach { assertTrue(privatePrompt.contains(it)) }
    }

    @Test fun oldCardFieldsCannotReenterGroupOrStoryPrompts() {
        val persona = AppJson.gson.fromJson(legacyPersona, CharacterPersona::class.java)
        val card = CharacterCard("card", persona)
        val group = GroupChatPolicy.fromCards(listOf(card))
        val story = StoryPolicy.fromCards(listOf(card))
        val prompts = listOf(StoryPrompts.system(story),
            GroupChatPrompts.plannerMessages(group, emptyList(), "", "now").toString(),
            GroupChatPrompts.actorMessages(group, group.members.single(), emptyList(), "", "", "now").toString())
        prompts.forEach { prompt ->
            listOf("OLD_OPEN", "OLD_OPENS", "OLD_CAST", "OLD_RULES", "OLD_USER").forEach { assertFalse(prompt.contains(it)) }
        }
        assertEquals("同学", story.members.single().persona.relationshipText)
        val serialized = AppJson.gson.toJsonTree(persona).asJsonObject
        dialogueKeys.forEach { assertFalse(serialized.has(it)) }
    }

    @Test fun storyUserIdentityBelongsToStoryRatherThanIndividualCards() {
        val story = AppJson.gson.fromJson("""{"userPersona":"STORY_USER 独立的玩家形象","members":[{"id":"a","persona":{"name":"澄川"}}]}""", StoryProfile::class.java).healed()
        val json = AppJson.gson.toJsonTree(story).asJsonObject
        assertTrue("Story schema must retain userPersona", json.has("userPersona"))
        assertEquals("STORY_USER 独立的玩家形象", json["userPersona"].asString)
        assertTrue(StoryPrompts.system(story).contains("STORY_USER"))
        assertTrue(StoryPrompts.memoryGuidance(story).contains("STORY_USER"))
        assertFalse(StoryPrompts.cast(story).contains("STORY_USER"))
        assertFalse(AppJson.gson.toJson(story.members).contains("STORY_USER"))
    }

    @Test fun oldStoriesAndExplicitNullDefaultToNoUserIdentity() {
        listOf("{}", """{"userPersona":null}""").forEach { raw ->
            val story = AppJson.gson.fromJson(raw, StoryProfile::class.java).healed()
            val json = AppJson.gson.toJsonTree(story).asJsonObject
            assertTrue(json.has("userPersona"))
            assertEquals("", json["userPersona"].asString)
        }
    }
}
