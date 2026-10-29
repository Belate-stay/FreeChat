package com.freechat.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regressions for unsampled footer controls and full-page layouts inside a scrolling sheet. */
class SheetLayer1222SourceTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun sheetBackdropAndSurfaceOccludeControlsOutsideTheHazeSource() {
        val sheet = source("ui/components/SheetPanel.kt")
        val backdrop = sheet.substringAfter("if (advancedMaterial) Modifier").substringBefore(".drawBehind")
        assertTrue("The modal must cover unregistered fixed controls before drawing sampled blur",
            backdrop.indexOf(".background(colors.Background)") in 0 until backdrop.indexOf(".hazeEffect"))
        val surface = sheet.substringAfter("// Keep the entire shadow").substringBefore(".windowInsetsPadding")
        assertTrue("The sheet face must never expose crisp unsampled widgets", surface.contains(".background(colors.Surface, shape)"))
    }

    @Test fun sheetReservesRoomForTitleAndActionsBeforeAllocatingScrollableBody() {
        val sheet = source("ui/components/SheetPanel.kt")
        assertTrue(sheet.contains(".weight(1f, fill = false)"))
        assertTrue(sheet.contains(".heightIn(max = maxContentHeight)"))
        assertTrue(sheet.contains(".verticalScroll(rememberScrollState())"))
        assertFalse("Confirmation labels must grow with text rather than clipping", sheet.contains(".height(40.dp)"))
    }

    @Test fun pickerFooterIsPartOfTheSingleBodyRecording() {
        val page = source("ui/screens/CharacterLibraryScreen.kt").substringAfter("internal fun CharacterLibraryPage(")
            .substringBefore("internal fun CharacterFolderRow")
        val column = page.substringAfter("Column(Modifier.fillMaxSize()")
        assertTrue(column.substringBefore("LazyColumn").contains(".hazeSource(hazeState)"))
        assertFalse("The nested list must not record separately from its footer",
            column.substringAfter("LazyColumn").substringBefore("contentPadding").contains(".hazeSource"))
    }

    @Test fun embeddedContributionChoicesDoNotPaintFullPageCardsInsideTheSheet() {
        val picker = source("ui/screens/CharacterContributionCard.kt").substringAfter("SheetPanel(active && state.picking")
            .substringBefore("SheetPanel(active && state.confirming")
        assertFalse(picker.contains("PageMotion("))
        assertFalse(picker.contains("CharacterFolderRow("))
        assertFalse(picker.contains("CharacterLibraryCardRow("))
        assertTrue(picker.contains("SheetActionRow("))
        assertTrue(picker.contains("FreeChatAnimation.contentReplacement()"))
    }

    @Test fun groupWindowCloseClearsSelectionSheetsBeforeUnsavedPrompt() {
        val leave = source("ui/screens/GroupSettingsScreen.kt").substringAfter("fun requestLeave(action: () -> Unit) {")
            .substringBefore("fun goBack()")
        listOf("modelType = null", "showAvatarOptions = false", "removeMemberId = null").forEach {
            assertTrue("Stale $it must be cleared before leave", leave.indexOf(it) in 0 until leave.indexOf("if (dirty)"))
        }
    }

    @Test fun characterWindowCloseClearsLaterModeWarningsAndWechatChoicesToo() {
        val source = source("ui/screens/CharacterSetupScreen.kt")
        val dismiss = source.substringAfter("fun dismissChoiceSheets() {").substringBefore("fun requestBack()")
        listOf("showModeLocked", "showModeMissing", "showDialogueModePicker", "showImportMismatch",
            "showWechatBind", "showWechatRebindConfirm", "showMbtiPicker", "showLangPicker", "showVisionPicker",
            "showVisualPicker", "showVoiceTimbreSheet", "showImagePresetSheet", "showAvatarMenu",
            "showAvatarPreview", "showEditConfirm", "showSaveConfirm", "showMbtiIncomplete", "showShareSheet").forEach {
            assertTrue("Leaving must close $it before showing an unsaved prompt", dismiss.contains("$it = false"))
        }
        val close = source.substringAfter("FloatingWindowCloseHandler { dismiss ->").substringBefore("val build")
        assertTrue(close.indexOf("dismissChoiceSheets()") in 0 until close.indexOf("if (hasChanges)"))
    }

    @Test fun initialChatAndUnpaddedSettingsContentClearTheHeaderWithoutChangingTheGlobalRule() {
        val chat = source("ui/screens/ChatScreen.kt").substringAfter("userScrollEnabled = !isRecording,").substringBefore("verticalArrangement")
        assertTrue(chat.contains("top = HazeSpec.newPageContentPaddingDp(statusBarHeightDp)"))
        val settings = source("ui/screens/SettingsScreen.kt")
        assertTrue(settings.contains("else HazeSpec.newPageContentPaddingDp(statusBarHeightDp)"))
        assertTrue("Model pools retain their separate fixed add-button geometry",
            settings.contains("if (screen.isModelPool) HazeSpec.topContentPaddingDp(statusBarHeightDp)"))
        assertTrue("About's existing larger logo gap stays as authored", settings.contains("calculateTopPadding(), 64.dp)"))
        val spec = source("ui/theme/HazeSpec.kt")
        assertTrue("No global body shift", spec.contains("titleBarHeightDp(statusBarDp) + originalGap"))
    }
}
