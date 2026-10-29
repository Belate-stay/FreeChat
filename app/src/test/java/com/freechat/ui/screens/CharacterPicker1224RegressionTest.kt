package com.freechat.ui.screens

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CharacterPicker1224RegressionTest {
    @Test fun backCallbacksRefreshCapturedFolderAndSelectionState() {
        val picker = File("src/main/java/com/freechat/ui/screens/CharacterPickerScreen.kt").readText()
        assertFalse("Local function references compare equal despite changed captures; BackHandler must receive a fresh state-aware lambda",
            picker.contains("::requestBack"))
        assertTrue(picker.contains("onBack = { requestBack() }"))
    }
    private fun picker() = File("src/main/java/com/freechat/ui/screens/CharacterPickerScreen.kt").readText()

    @Test fun cardSelectionHasNoCheckControlsAndKeepsTheAnimatedAvatarStrip() {
        val source = picker()
        for (control in listOf("CharacterSelectionCircle", "CharacterFolderCheckbox", "triStateToggleable", "ToggleableState")) {
            assertFalse("Picker selection is expressed by the card fill, not $control", source.contains(control))
        }
        val cards = source.substringAfter("items(cards, key =").substringBefore("SheetPanel(showLimitMessage")
        assertTrue("Tapping a card toggles that card", cards.contains("onClick = { toggle(listOf(card.id)) }"))
        assertTrue("The shared row receives the selected state for its fill", cards.contains("highlighted = card.id in selectedIds"))
        assertTrue(source.contains("item(key = \"selected_characters\")"))
        assertTrue(source.contains("items(selectedCards, key = { it.id })"))
        assertTrue(source.contains("animateItem("))
        assertTrue(source.contains("clickable { toggle(listOf(card.id)) }"))
    }

    @Test fun wholeFolderSelectionUsesASquareCheckboxInTheFolderRow() {
        val source = picker()
        assertTrue("Folder rows keep their normal open action", source.contains("onOpen = { onSelectFolder(entry.id) }"))
        assertTrue("Whole-folder selection remains wired in the folder row", source.contains("CharacterFolderSelectionAction("))
        val action = source.substringAfter("private fun CharacterFolderSelectionAction(").substringBefore("@Preview")
        assertTrue("方框勾选框：点击即勾选（2026-10-28 返修，替换文字按钮）",
            action.contains("toggleable(") && action.contains("Role.Checkbox"))
        assertTrue("不要太大：22dp 方框、行内右对齐", action.contains("size(22.dp)"))
        assertTrue("勾选态=主题色实心+对勾", action.contains("Icons.Filled.Check") && action.contains("colors.Primary"))
        assertTrue("空文件夹不可勾", action.contains("enabled = ids.isNotEmpty()"))
    }

    @Test fun systemAndToolbarBackShareTheFolderFirstHandlerBeforeTheSheets() {
        val source = picker()
        val back = source.substringAfter("fun requestBack() {").substringBefore("FloatingWindowCloseHandler")
        assertTrue(back.contains("folder != null -> onSelectFolder(null)"))
        assertTrue("A folder pop must happen before discarding the draft", back.indexOf("folder != null") < back.indexOf("changed ->"))
        assertFalse("Returning to picker root must keep all selected cards", back.contains("selectedIds ="))
        assertTrue(source.contains("{ requestBack() }, hazeState"))
        val pageEnd = source.substringAfter("items(cards, key =").substringBefore("SheetPanel(showLimitMessage")
        assertTrue("The active picker explicitly owns system Back after its page content",
            pageEnd.contains("BackHandler(enabled = LocalPageActive.current, onBack = { requestBack() })"))
    }

    @Test fun deselectingACardPreservesSelectionsFromOtherFolders() {
        assertEquals(setOf("folder-b-card"),
            toggleCharacterSelection(setOf("folder-a-card", "folder-b-card"), listOf("folder-a-card"), 4))
    }

    @Test fun selectingAFolderKeepsSelectionsFromOtherFoldersAndRejectsOverflow() {
        val selected = setOf("folder-a-card", "folder-b-card")
        assertEquals(setOf("folder-a-card", "folder-b-card", "folder-c-card"),
            toggleCharacterSelection(selected, listOf("folder-c-card"), 4))
        assertNull(toggleCharacterSelection(selected, listOf("c-one", "c-two", "c-three"), 4))
        assertEquals(setOf("folder-a-card", "folder-b-card"), selected)
    }
}
