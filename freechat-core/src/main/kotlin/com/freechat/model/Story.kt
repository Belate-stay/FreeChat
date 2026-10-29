package com.freechat.model

import java.util.UUID

/** A cast snapshot belongs to one story. Pool edits never rewrite an ongoing novel. */
data class StoryMember(val id: String, val persona: CharacterPersona, val sourceCardId: String = "")

data class StoryProfile(
    val members: List<StoryMember> = emptyList(),
    val background: String = "", val worldRules: String = "", val openingSituation: String = "",
    val userPersona: String = "",
    val languageModelId: String = "", val visualModelId: String = "",
    val enableWebSearch: Boolean? = null, val deepThinkingMode: Boolean = true,
    val plotLength: Int = 1, val aiCreativity: Float = 6f,
    val highQualityMemory: Boolean = false, val deepThinking: Boolean = false,
    val enhancedSceneContinuity: Boolean = false,
    val updatedAt: Long = 0,
)

object StoryPolicy {
    const val MAX_MEMBERS = 8
    fun isValid(story: StoryProfile): Boolean = story.members.size in 1..MAX_MEMBERS &&
        story.members.all { it.id.isNotBlank() && it.persona.name.isNotBlank() } &&
        story.members.map { it.id }.distinct().size == story.members.size

    fun fromCards(cards: List<CharacterCard>): StoryProfile {
        require(cards.size in 1..MAX_MEMBERS && cards.map { it.id }.distinct().size == cards.size)
        return StoryProfile(members = cards.map { StoryMember(UUID.randomUUID().toString(),
            CharacterPersona.from(it.persona.toProfile(DialogueMode.PLOT)), it.id) }).also { require(isValid(it)) }
    }
}
