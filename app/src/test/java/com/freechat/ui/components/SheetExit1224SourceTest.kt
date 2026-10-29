package com.freechat.ui.components

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SheetExit1224SourceTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun storyModelAndWarningContentOutliveTheirDismissState() {
        val story = source("ui/screens/StorySettingsScreen.kt")
        assertTrue(story.contains("rememberSheetContent(modelType)"))
        assertTrue(story.contains("rememberSheetContent(pendingHeavy)"))
        assertFalse(story.contains("val type = modelType"))
        assertTrue(story.contains("SheetPanel(modelType != null"))
        assertTrue(story.contains("when (displayedHeavy)"))
    }

    @Test fun groupModelPickerDoesNotFallBackToLanguageDuringExit() {
        val group = source("ui/screens/GroupSettingsScreen.kt")
        assertTrue(group.contains("rememberSheetContent(modelType)"))
        assertTrue(group.contains("SheetPanel(modelType != null"))
        assertFalse(group.contains("val activeModelType = modelType"))
    }

    @Test fun sharedShellKeepsTheTitleWhileItsExitIsVisible() {
        val panel = source("ui/components/SheetPanel.kt")
        assertTrue(panel.contains("rememberSheetContent(title.takeIf { visible })"))
        assertTrue(panel.contains("displayedTitle"))
    }

    @Test fun otherTransientDialogsKeepTheirNoticeTargetsAndCountsDuringExit() {
        val activity = source("MainActivity.kt")
        assertTrue(activity.contains("rememberSheetContent(drawerDeleteTarget)"))
        assertTrue(activity.contains("rememberSheetContent(drawerBatchDeleteTargets)"))
        val chat = source("ui/screens/ChatScreen.kt")
        assertTrue(chat.contains("rememberSheetContent(voiceMenuTarget)"))
        assertTrue(chat.contains("rememberSheetContent(attachmentNotice.takeIf { it.isNotBlank() })"))
        val settings = source("ui/screens/SettingsScreen.kt")
        assertTrue(settings.contains("rememberSheetContent(pending.takeIf { it.isNotEmpty() })"))
    }

    @Test fun deletionWarningsDoNotChangeWhenTheirUnderlyingRecordsAreRemoved() {
        val activity = source("MainActivity.kt")
        assertTrue(activity.contains("val displayedDeleteFavoriteWarning = rememberSheetContent("))
        assertTrue(activity.contains("val displayedBatchFavoriteWarning = rememberSheetContent("))
        val group = source("ui/screens/GroupSettingsScreen.kt")
        assertTrue(group.contains("val displayedRemovalAtLimit = rememberSheetContent("))
        assertTrue(group.contains("if (displayedRemovalAtLimit) Text(s.groupMembersLimit"))
    }

    @Test fun globalBackDoesNotSkipPickerFolderOrOverrideItsOwnHandler() {
        val activity = source("MainActivity.kt")
        val back = activity.substringAfter("// 返回键导航栈").substringBefore("// Learning is an exclusive task")
        assertTrue(back.contains("currentScreen != Screen.CHARACTER_PICKER"))
        assertFalse(back.contains("currentScreen == Screen.CHARACTER_PICKER -> navigateBack(groupPickerBack)"))
    }
}
