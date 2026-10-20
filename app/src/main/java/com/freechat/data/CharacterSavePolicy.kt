package com.freechat.data

import com.freechat.model.CharacterProfile

object CharacterSavePolicy {
    fun hasPersonaChanges(original: CharacterProfile, draft: CharacterProfile) =
        CharacterPresentationPolicy.withSimulationSettings(original, draft) != draft

    fun hasSimulationChanges(original: CharacterProfile, draft: CharacterProfile) =
        CharacterPresentationPolicy.withSimulationSettings(original, draft) != original

    fun canSave(isThinking: Boolean, isTyping: Boolean) = !isThinking && !isTyping
}
