package com.freechat.data

import com.freechat.model.*
import com.freechat.sync.Wire
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CharacterCardBoundary1225Test {
    private val dialogueKeys = listOf("openingLine", "openingLines", "supportingCast", "worldRules", "userPersona")

    @Test fun legacyPoolAndContributionSerializationExcludeConversationFields() {
        LocalStore.setWriteListener(null)
        LocalStore.init(Files.createTempDirectory("persona-boundary-").toFile())
        LocalStore.writeText(LocalStore.characterLibraryFile(), """{"cards":[{"id":"old","persona":{"name":"角色","relationshipText":"老朋友","worldRules":"OLD_RULES","userPersona":"OLD_USER","supportingCast":"OLD_CAST","openingLine":"OLD_OPEN","openingLines":["OLD_OPEN"]}}]}""")
        CharacterLibraryStore.reload()
        val card = CharacterLibraryStore.state.value.cards.single()
        val wire = Wire.cardToWire(card).getAsJsonObject("persona")
        dialogueKeys.forEach { assertFalse(wire.has(it)) }
        val contribution = CharacterContributionPayload.build("64bcc76e-b58a-43c4-a29f-ac2169ab7332",
            "552de162-6e50-4553-9a33-e88e4b4ec8a4", "Synthetic Model", "1.2.2.5", card.persona) { null }.getAsJsonObject("persona")
        dialogueKeys.forEach { assertFalse(contribution.has(it)) }
        assertEquals("老朋友", contribution["relationshipText"].asString)
    }

    @Test fun currentSceneUsesTheStoryUserIdentity() {
        val story = AppJson.gson.fromJson("""{"userPersona":"用户穿着USER_SCENE_COAT","members":[{"id":"a","persona":{"name":"澄川"}}]}""", StoryProfile::class.java)
        assertTrue(SceneImagePrompt.build(story, emptyList(), emptyList()).contains("USER_SCENE_COAT"))
    }

    @Test fun storyUserIdentitySurvivesWireRoundTrip() {
        val story = AppJson.gson.fromJson("""{"userPersona":"USER_STORY_SYNC","members":[{"id":"a","persona":{"name":"澄川"}}]}""", StoryProfile::class.java)
        val conversation = Conversation(id = "story", mode = ChatMode.COMPANION, storyProfile = story)
        val restored = Wire.convFromWire(Wire.convToWire(conversation), null)
        assertNotNull(restored)
        assertEquals("USER_STORY_SYNC", AppJson.gson.toJsonTree(restored!!.storyProfile).asJsonObject["userPersona"]?.asString)
    }
}
