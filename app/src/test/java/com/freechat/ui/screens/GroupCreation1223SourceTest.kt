package com.freechat.ui.screens

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class GroupCreation1223SourceTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun pickerUsesCardSelectionWithoutCheckControls() {
        val picker = source("ui/screens/CharacterPickerScreen.kt")
        assertFalse("No Material checkboxes in this picker", picker.contains("import androidx.compose.material3.Checkbox"))
        assertFalse(picker.contains("import androidx.compose.material3.TriStateCheckbox"))
        assertFalse(picker.contains("CharacterSelectionCircle"))
        assertFalse(picker.contains("triStateToggleable"))
        assertTrue(picker.contains("selected = card.id in selectedIds"))
    }

    @Test fun selectedStripAnimatesItsVisibilityAndKeyedItems() {
        val picker = source("ui/screens/CharacterPickerScreen.kt")
        assertTrue(picker.contains("item(key = \"selected_characters\")"))
        assertTrue(picker.contains("AnimatedVisibility"))
        assertTrue(picker.contains("animateItem("))
        assertTrue(picker.contains("LocalMotionEnabled.current"))
        assertTrue(picker.contains("items(selectedCards, key = { it.id })"))
    }

    @Test fun confirmingRolesOpensDraftSettingsWithoutCreatingConversation() {
        val activity = source("MainActivity.kt")
        val picker = activity.substringAfter("Screen.CHARACTER_PICKER -> {").substringBefore("Screen.STORY_SETUP -> {")
        assertFalse("Choosing members alone must not create a conversation", picker.contains("startGroupConversation("))
        assertTrue(picker.contains("GroupChatPolicy.fromCards(cards)"))
        assertTrue(picker.contains("navigateTo(Screen.GROUP_SETTINGS)"))
        assertTrue(picker.contains("initialSelectedIds ="))
        assertTrue(activity.contains("creating = creatingGroup"))
        assertTrue(activity.contains("onStart ="))
    }

    @Test fun draftSettingsRequireANameBeforeExplicitStart() {
        val settings = source("ui/screens/GroupSettingsScreen.kt")
        assertTrue(settings.contains("creating: Boolean = false"))
        assertTrue(settings.contains("GroupChatPolicy.canStart(title, group)"))
        assertTrue(settings.contains("s.startCharacterChat"))
        assertTrue(settings.contains("showInitialName"))
        assertTrue(settings.contains("s.groupRemarkRequiredHint"))
        val vm = source("viewmodel/ChatViewModel.kt").substringAfter("fun startGroupConversation(").substringBefore("fun startStoryConversation(")
        assertTrue(vm.contains("GroupChatPolicy.canStart(title, group)"))
        assertTrue(vm.contains("title = title.trim()"))
        assertFalse("A title must not be generated from member names", vm.contains("joinToString"))
    }

    @Test fun returningFromNewGroupSettingsRestoresThePickerParentAfterAddingMembers() {
        val activity = source("MainActivity.kt")
        val back = activity.substringAfter("val backFromGroupSettings:").substringBefore("fun openCharacterEditor(")
        assertTrue("New drafts must restore the picker's outer parent instead of forming a back loop",
            back.contains("groupPickerBack = Screen.NEW_CHAT_MODE"))
        assertTrue(back.contains("navigateBack(Screen.CHARACTER_PICKER)"))
        assertTrue(activity.contains("currentScreen == Screen.GROUP_SETTINGS -> backFromGroupSettings()"))
        assertTrue(activity.contains("onBack = backFromGroupSettings"))
    }

    @Test fun storyGenerationControlsUseTheSpecificImageModelLabel() {
        val story = source("ui/screens/StorySettingsScreen.kt")
        assertFalse(story.contains("s.visualModel"))
        assertEquals(2, Regex("s\\.imageGenModel").findAll(story).count())
    }

    @Test fun contributionCardUsesViewportBottomPlacementAndReadableCopy() {
        val settings = source("ui/screens/SettingsScreen.kt")
        assertTrue(settings.contains("heightIn(min = if (screen == SubScreen.BUILTIN_CHARACTER_POOL)"))
        val pool = settings.substringAfter("SubScreen.BUILTIN_CHARACTER_POOL -> {").substringBefore("SubScreen.TABLET_DISPLAY ->")
        assertTrue(pool.contains("weight(1f)"))
        assertTrue(pool.contains("CharacterContributionCard(characterContribution)"))
        val card = source("ui/screens/CharacterContributionCard.kt")
        assertTrue(card.contains("textAlign = TextAlign.Start"))
        assertTrue(card.contains("style = MaterialTheme.typography.bodyMedium"))
    }
}
