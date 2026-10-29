package com.freechat.ui.screens

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class Creation1219SourceTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()
    @Test fun sidebarHasSeparateMaterialActionsAndPrivateModeHasNoGroupCreation() {
        val drawer = source("ui/components/DrawerContent.kt")
        assertTrue(drawer.contains("onNewCharacter"))
        assertTrue(drawer.contains("s.createConversation"))
        assertTrue(drawer.contains("s.createCharacter"))
        assertFalse(source("ui/screens/DialogueModeScreen.kt").contains("onCreateGroup"))
        val modes = source("ui/screens/NewChatModeScreen.kt")
        assertTrue(modes.contains("onSelectGroup"))
        assertTrue(modes.contains("onSelectStory"))
    }
    @Test fun roleOnlyCreationHasAnExclusiveStoreActionAndStoryHasSeparateRoute() {
        val setup = source("ui/screens/CharacterSetupScreen.kt")
        assertTrue(setup.contains("if (personaOnly) s.storeInCharacterLibrary else s.startCharacterChat"))
        assertFalse(setup.contains("TextButton(onClick = { storeDraft() }"))
        val activity = source("MainActivity.kt")
        assertTrue(activity.contains("Screen.STORY_SETUP"))
        assertTrue(activity.contains("selectionLimit = if (storyPicker) StoryPolicy.MAX_MEMBERS"))
    }
}
