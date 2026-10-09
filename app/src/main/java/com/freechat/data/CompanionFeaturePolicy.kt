package com.freechat.data

import com.freechat.model.CharacterProfile
import com.freechat.model.DialogueMode
import com.freechat.model.Message

/** Both the UI and the operation guard use the same boundary, including migrated profiles. */
object CompanionFeaturePolicy {
    /** Emoji is a messaging feature, not a narrative creation feature. */
    fun supportsEmojiInput(isCompanion: Boolean, narrativeSingleSend: Boolean): Boolean =
        isCompanion && !narrativeSingleSend

    fun sceneDeletionIds(rows: List<Message>, messageId: String): Set<String> =
        rows.firstOrNull { it.id == messageId && it.sceneVisualization && it.role == com.freechat.model.Role.ASSISTANT }
            ?.let { setOf(it.id) }.orEmpty()

    fun supportsNarrativeActions(character: CharacterProfile?): Boolean =
        character?.normalized()?.dialogueMode in listOf(DialogueMode.ACTION, DialogueMode.PLOT)

    /** A successful picture freezes the text it visualized, not later continuation or failed jobs. */
    fun sceneLockedMessageIds(rows: List<Message>): Set<String> {
        val boundary = rows.indexOfLast { it.sceneVisualization && !it.failed &&
            !it.isStreaming && it.imageUrls.orEmpty().isNotEmpty() }
        if (boundary < 0) return emptySet()
        return rows.take(boundary).filterNot { it.sceneVisualization }.mapTo(HashSet()) { it.id }
    }
}
