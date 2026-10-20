package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TabletCharacterWizardContractTest {
    private val character get() = File("src/main/java/com/freechat/ui/screens/CharacterSetupScreen.kt").readText()

    @Test fun onlyNewEmbeddedTabletCharactersUseTheCategoryWizard() {
        val source = character
        assertTrue(source.contains("val tabletCreation = LocalTabletUi.current && !isEditing"))
        assertTrue(source.contains("workspaceKey = \"character_creation\""))
        assertTrue(source.contains("CharacterCreationCategory.entries"))
        for (category in listOf("MODE", "PERSONA", "SIMULATION")) {
            assertTrue(source.contains("CharacterCreationCategory.$category"))
        }
        assertTrue(source.contains("角色设定"))
        assertTrue(source.contains("角色人设"))
    }

    @Test fun modeSelectionUnlocksCategoriesAndNextRevealsPersona() {
        val source = character
        assertTrue(source.contains("mutableStateOf(CharacterCreationCategory.MODE)"))
        assertTrue(source.contains("category == CharacterCreationCategory.MODE || dialogueMode != null"))
        assertTrue(source.contains("enabled = enabled"))
        assertTrue(source.contains("selectedCreationCategory = CharacterCreationCategory.PERSONA"))
        assertTrue(source.contains("creationRevealTick++"))
    }

    @Test fun categoryPagesReuseOneDraftAndTheExistingSections() {
        val source = character
        assertEquals(1, Regex("val initialDraft = remember").findAll(source).count())
        assertEquals(1, Regex("var name by remember").findAll(source).count())
        assertTrue(source.contains("if (tabletCreation) {"))
        assertTrue(source.contains("if (creationCategory == CharacterCreationCategory.PERSONA)"))
        assertTrue(source.contains("else if (creationCategory == CharacterCreationCategory.SIMULATION)"))
        assertTrue(source.contains("else if (wideForm)"))
        assertTrue(source.contains("label = \"character_creation_category\""))
    }

    @Test fun characterImportRetainsSimulationFieldsAlongsidePersonaAndMode() {
        val fill = character.substringAfter("fun fillFormFromProfile(", "").substringBefore("val importPicker")
        for (field in listOf("langModelId = profile.languageModelId", "visionModelId = profile.visionModelId",
            "visualModelId = profile.visualModelId", "deepThinkingMode = profile.deepThinkingMode",
            "webSearch = profile.enableWebSearch", "plotLength = profile.plotLength",
            "replyBufferSeconds = profile.replyBufferSeconds", "replyBufferEnabled = profile.replyBufferEnabled",
            "sleepSimulation = profile.sleepSimulation", "timePerception = profile.timePerception",
            "highQualityMemory = profile.highQualityMemory", "deepThinking = profile.deepThinking",
            "aiCreativity = profile.aiCreativity", "proactiveEnabled = profile.proactiveEnabled",
            "enhancedSceneContinuity = profile.enhancedSceneContinuity", "imagePresetAspect = profile.imagePresetAspect",
            "imagePresetSubject = profile.imagePresetSubject", "supportingCast = profile.supportingCast",
            "worldRules = profile.worldRules", "userPersona = profile.userPersona")) {
            assertTrue("Import must retain $field", fill.contains(field))
        }
        assertTrue(fill.contains("if (adoptMode) dialogueMode = profile.dialogueMode"))
        assertTrue(fill.contains("selectedCreationCategory = CharacterCreationCategory.PERSONA"))
    }

    @Test fun everyCategoryKeepsDiscardAndLiveSaveValidation() {
        val source = character
        assertTrue(source.contains("BackHandler(enabled = pageActive) { requestBack() }"))
        assertTrue(source.contains("hasChanges -> showDiscardDialog = true"))
        assertTrue(source.contains("if (!isSavingBlocked && canSubmit()) return true"))
        val submit = source.substringAfter("fun submitDraft() {", "").substringBefore("var avatarCropSource")
        assertTrue(submit.contains("if (!savingAllowed()) return"))
        assertTrue(submit.contains("if (!canStart) return"))
        assertTrue(submit.contains("if (isEditing && !personaChanged)"))
        assertTrue(submit.contains("onSaveSimulation(buildSimulationProfile())"))
        assertTrue(submit.contains("onCreate(buildProfile())"))
        assertTrue(source.contains("enabled = canStart && !isSavingBlocked"))
    }
}
