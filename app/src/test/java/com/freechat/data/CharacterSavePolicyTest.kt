package com.freechat.data

import com.freechat.model.CharacterProfile
import com.freechat.model.DialogueMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSavePolicyTest {
    private val original = CharacterProfile(
        name = "Original",
        personaPrompt = "Learned persona",
        originalLearningText = "Original prose",
        languageModelId = "language-before",
        visionModelId = "vision-before",
        visualModelId = "visual-before",
        enableWebSearch = false,
    )

    @Test fun unchangedDraftHasNoPendingChanges() {
        assertFalse(CharacterSavePolicy.hasPersonaChanges(original, original))
        assertFalse(CharacterSavePolicy.hasSimulationChanges(original, original))
    }

    @Test fun functionalControlsDoNotRequirePersonaLearning() {
        val draft = original.copy(
            dialogueMode = DialogueMode.PLOT,
            plotSimulation = true,
            plotLength = 3,
            replyBufferSeconds = 6,
            replyBufferEnabled = false,
            sleepSimulation = true,
            timePerception = true,
            highQualityMemory = true,
            deepThinking = true,
            aiCreativity = 8f,
            proactiveEnabled = true,
            languageModelId = "language-after",
            visionModelId = "vision-after",
            visualModelId = "visual-after",
            enhancedSceneContinuity = true,
            imagePresetAspect = 2,
            imagePresetSubject = 1,
            deepThinkingMode = false,
            enableWebSearch = true,
        )

        assertFalse(CharacterSavePolicy.hasPersonaChanges(original, draft))
        assertTrue(CharacterSavePolicy.hasSimulationChanges(original, draft))
    }

    @Test fun personaDraftRequiresLearningWithoutChangingFunctionalSettings() {
        for (draft in listOf(
            original.copy(name = "Edited"),
            original.copy(avatarPath = "/avatar.jpg"),
            original.copy(personalityText = "Edited personality"),
            original.copy(memoryPerception = "Edited memory"),
            original.copy(appearanceText = "Edited appearance"),
            original.copy(relationshipText = "Edited relationship"),
            original.copy(originalLearningText = "Edited prose"),
            original.copy(supportingCast = "Edited cast"),
            original.copy(worldRules = "Edited rules"),
            original.copy(userPersona = "Edited user"),
        )) {
            assertTrue(CharacterSavePolicy.hasPersonaChanges(original, draft))
            assertFalse(CharacterSavePolicy.hasSimulationChanges(original, draft))
        }
    }

    @Test fun mixedDraftKeepsBothChangeKinds() {
        val draft = original.copy(name = "Edited", languageModelId = "language-after")

        assertTrue(CharacterSavePolicy.hasPersonaChanges(original, draft))
        assertTrue(CharacterSavePolicy.hasSimulationChanges(original, draft))
    }

    @Test fun savingWaitsUntilThinkingAndTypingHaveBothFinished() {
        assertTrue(CharacterSavePolicy.canSave(isThinking = false, isTyping = false))
        assertFalse(CharacterSavePolicy.canSave(isThinking = true, isTyping = false))
        assertFalse(CharacterSavePolicy.canSave(isThinking = false, isTyping = true))
        assertFalse(CharacterSavePolicy.canSave(isThinking = true, isTyping = true))
    }
}
