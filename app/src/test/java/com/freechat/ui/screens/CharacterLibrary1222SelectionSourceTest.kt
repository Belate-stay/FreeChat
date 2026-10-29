package com.freechat.ui.screens

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Selection chrome is checked here; card/conversation ownership is covered by store tests. */
class CharacterLibrary1222SelectionSourceTest {
    private fun source() = File("src/main/java/com/freechat/ui/screens/CharacterLibraryScreen.kt").readText()

    @Test fun rootSelectionUsesTheFavoritesCountAndKeepsActionsInTheHeader() {
        val page = source().substringBefore("SheetPanel(showBatchMove")
        assertTrue("Use the same localized count as Favorites", page.contains("s.selectedCount(selectedCardIds.size)"))
        val actions = page.substringAfter("actions = {").substringBefore("item { Text(s.characterPersonaOnlyNote")
        assertTrue("Move belongs in the header", actions.contains("showBatchMove = true"))
        assertTrue("Delete belongs in the header", actions.contains("showBatchDelete = true"))
        assertTrue("Use the established compact header actions", actions.contains("HeaderIconButton("))
        assertTrue("Move needs a selection and destination", actions.contains("selectedCardIds.isNotEmpty() && library.folders.isNotEmpty()"))
        assertTrue("Delete needs a selection", actions.contains("enabled = selectedCardIds.isNotEmpty()"))
        assertFalse("Selection must leave the full page height to cards", page.contains("bottomBar ="))
        assertFalse("There is no second footer count", page.contains("s.characterBatchSelectionCount("))
        assertFalse("Close replaces Done", page.contains("s.librarySelectionDone"))
        assertFalse("No separate Clear action", page.contains("s.libraryClearSelection"))
    }

    @Test fun selectionHeaderUsesCloseWhileNormalPagesKeepTheirFloatingBackButton() {
        val source = source()
        assertTrue("Folder and unclassified pages share the selection header", source.contains("selectionMode = selecting,"))
        val page = source.substringAfter("internal fun CharacterLibraryPage(").substringBefore("internal fun CharacterFolderRow(")
        assertTrue("Existing picker callers keep normal navigation", page.contains("selectionMode: Boolean = false"))
        assertTrue("Close exits selection through the existing back handler", page.contains("HeaderIconButton(onClick = onBack)"))
        assertTrue(page.contains("Icon(Icons.Filled.Close, s.cancel"))
        assertTrue(page.contains("FloatingWindowBackButton(onClick = onBack)"))
        assertTrue("Header replacement uses app motion", page.contains("FreeChatAnimation.contentReplacement()"))
        assertTrue("Selection matches Favorites without an extra expand action", page.contains("if (!selecting) PaneExpandButton()"))
    }

    @Test fun compactSelectionCircleMatchesFavoritesWithoutMaterialCheckboxPadding() {
        val rows = source().substringAfter("items(cards, key =").substringBefore("SheetPanel(showBatchMove")
        assertFalse("Material Checkbox reserves unnecessary row width", rows.contains("Checkbox("))
        for (contract in listOf("AnimatedVisibility(", "FreeChatAnimation.expandEnter()", "FreeChatAnimation.expandExit()",
            "animateColorAsState(", "FreeChatAnimation.colorTween", ".size(20.dp)", ".clip(CircleShape)",
            "width = 1.5.dp", "shape = CircleShape", "Icon(Icons.Filled.Check", "Modifier.size(13.dp)")) {
            assertTrue("Favorites selection circle contract missing: $contract", rows.contains(contract))
        }
    }

    @Test fun finalDeselectionExitsAndFolderNavigationKeepsTheExistingSafetyRules() {
        val source = source()
        assertTrue("Toggling the last card off exits selection", source.contains("if (next.isEmpty()) finishSelection() else selectedIds = next"))
        assertTrue("Long press toggles an already active selection", source.contains("if (selecting) toggleSelection(card.id)"))
        assertTrue(source.contains("selecting -> finishSelection()"))
        assertTrue(source.contains("folder != null -> onSelectFolder(null)"))
        assertTrue(source.contains("FloatingWindowCloseHandler { close -> if (selecting) finishSelection() else close() }"))
        assertTrue(source.contains("onOpen = { finishSelection(); onSelectFolder(entry.id) }"))
        assertTrue(source.contains("CharacterLibraryStore.moveCards(selectedCardIds, entry.id, activeFolderId)"))
        assertTrue(source.contains("CharacterLibraryStore.deleteCards(selectedCardIds, activeFolderId)"))
    }
}
