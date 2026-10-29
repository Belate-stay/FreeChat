package com.freechat.ui.screens

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CharacterCardScope1225SourceTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun roleOnlyFormsHideConversationSectionsAndDoNotSaveHiddenDraftValues() {
        val screen = source("ui/screens/CharacterSetupScreen.kt")
        assertTrue(Regex("if \\(!personaOnly\\) \\{\\s+SectionLabel\\(Icons.Filled.Groups, s.supportingCast\\)").containsMatchIn(screen))
        assertTrue(screen.contains("if (!personaOnly && !isEditing)"))
        assertTrue(screen.contains("openingLines = if (personaOnly) emptyList() else"))
        listOf("supportingCast", "worldRules", "userPersona").forEach {
            assertTrue(screen.contains("$it = if (personaOnly) \"\" else $it.trim()"))
        }
        assertTrue(screen.contains("s.relationship"))
    }

    @Test fun storyOwnsUserIdentityAndCardPreviewDoesNotPresentWorldFields() {
        val story = source("ui/screens/StorySettingsScreen.kt")
        assertTrue(story.contains("StoryField(s.userPersona, draft.userPersona, s.storyUserPersonaHint"))
        assertTrue(story.contains("onDraft(draft.copy(userPersona = it))"))
        val preview = source("ui/screens/CharacterLibraryScreen.kt").substringAfter("private fun CharacterPersonaPreview(")
            .substringBefore("internal fun CharacterLibraryPage(")
        listOf("persona.userPersona", "persona.worldRules", "persona.supportingCast", "persona.openingLine").forEach {
            assertFalse(preview.contains(it))
        }
    }

    @Test fun cardLearningHasASeparateScopeWithoutChangingPrivateLearningDefaults() {
        val vm = source("viewmodel/ChatViewModel.kt")
        val library = vm.substringAfter("suspend fun generateLibraryPersona(").substringBefore("/** 深度分析角色设定")
        assertEquals(2, Regex("personaOnly = true").findAll(library).count())
        assertTrue(vm.contains("personaOnly: Boolean = false"))
        assertTrue(vm.contains("CharacterPersona.PROMPT_SCOPE"))
    }
}
