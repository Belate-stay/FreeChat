package com.freechat.data

import com.freechat.model.DialogueMode
import com.freechat.model.CharacterProfile

/** Mode-specific controls never erase a hidden model choice or reference material. */
object CharacterPresentationPolicy {
    fun usesImageGeneration(mode: Int?) = mode == DialogueMode.ACTION || mode == DialogueMode.PLOT
    fun usesPhotoRecognition(mode: Int?) = mode == DialogueMode.WECHAT
    // Only the persona/prompt half is locked in preview; simulation controls stay editable.
    fun personaEditable(existing: Boolean, editing: Boolean) = !existing || editing
    fun timeEnabled(perception: Boolean, legacySleep: Boolean) = perception || legacySleep

    /** Saving the unlocked half must not normalize, trim, or regenerate any locked persona field. */
    fun withSimulationSettings(original: CharacterProfile, draft: CharacterProfile) = original.copy(
        dialogueMode = draft.dialogueMode,
        plotSimulation = draft.plotSimulation,
        plotLength = draft.plotLength,
        replyBufferSeconds = draft.replyBufferSeconds,
        replyBufferEnabled = draft.replyBufferEnabled,
        sleepSimulation = draft.sleepSimulation,
        timePerception = draft.timePerception,
        highQualityMemory = draft.highQualityMemory,
        deepThinking = draft.deepThinking,
        aiCreativity = draft.aiCreativity,
        proactiveEnabled = draft.proactiveEnabled,
        languageModelId = draft.languageModelId,
        visionModelId = draft.visionModelId,
        visualModelId = draft.visualModelId,
        enhancedSceneContinuity = draft.enhancedSceneContinuity,
        deepThinkingMode = draft.deepThinkingMode,
        enableWebSearch = draft.enableWebSearch,
    )

    // Buffered conversation is not an alternating one-question/one-answer script.
    val wechatExampleOrder = listOf(true to 0, false to 0, false to 1, true to 1, true to 2, false to 2)
}
