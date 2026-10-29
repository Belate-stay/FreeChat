package com.freechat.ui.screens

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Complements store regressions with the screen's gesture and sheet lifetime wiring. */
class CharacterLibrary1224InteractionSourceTest {
    private fun source() = File("src/main/java/com/freechat/ui/screens/CharacterLibraryScreen.kt").readText()

    @Test fun folderRowsExposeTheSameLongPressSelectionAsUnclassifiedRows() {
        val source = source()
        val rows = source.substringAfter("items(cards, key =").substringBefore("SheetPanel(showBatchMove")
        assertFalse("Folder cards must receive the long press callback", rows.contains("onLongClick = if (folder == null)"))
        assertFalse("Folder cards must show the selection circles", rows.contains("selecting && folder == null"))
        assertFalse("Entering a folder must not immediately cancel its own selection",
            source.contains("if (activeFolderId != null) finishSelection()"))
        assertFalse("The selected count and header actions apply inside folders too",
            source.contains("selectionMode = selecting && folder == null"))
        val tap = rows.substringAfter("onClick = {").substringBefore("onLongClick =")
        assertFalse("Normal taps only preview; entering selection belongs to long press", tap.contains("selecting = true"))
        assertTrue(rows.contains("if (selecting) toggleSelection(card.id)"))
        for (contract in listOf(".size(20.dp)", ".clip(CircleShape)", "shape = CircleShape", "Modifier.size(13.dp)")) {
            assertTrue("Preserve the existing Favorites circle: $contract", rows.contains(contract))
        }
    }

    @Test fun batchActionsPassTheirSourceFolderAndOfferUnclassifiedAsADestination() {
        val batch = source().substringAfter("SheetPanel(showBatchMove").substringBefore("SheetPanel(showAdd")
        assertTrue("The store must validate the source folder from the active selection",
            batch.contains("CharacterLibraryStore.moveCards(selectedCardIds, entry.id, activeFolderId)"))
        assertTrue(batch.contains("CharacterLibraryStore.deleteCards(selectedCardIds, activeFolderId)"))
        assertTrue("Folder cards can move back to Unclassified", batch.contains("s.unclassifiedCharacters"))
        assertTrue(batch.contains("CharacterLibraryStore.moveCards(selectedCardIds, null, activeFolderId)"))
    }

    @Test fun importingKeepsTheSheetOpenForConsecutiveSelectionsUntilDismissed() {
        val source = source()
        val sheet = source.substringAfter("SheetPanel(showExisting,").substringBefore("private data class ExistingLibraryCharacter")
        val choose = sheet.substringAfter("onClick = {").substringBefore("}, icon =")
        assertTrue("Choosing still persists the persona to the current folder", choose.contains("CharacterLibraryStore.add(source.persona, folder?.id,"))
        assertFalse("Choosing must leave the import list open for another addition", choose.contains("showExisting = false"))
        assertTrue("Outside/back dismissal remains available", sheet.contains("{ showExisting = false }"))
        assertTrue("Already added sources remain visibly checked", sheet.contains("SheetOption(added,"))
    }

    @Test fun closingTargetSheetsRetainsTheirDisplayedPersonaFolderAndSelectionCount() {
        val source = source()
        for (target in listOf("previewCard", "movingCard", "menuFolder")) {
            assertTrue("Closing the sheet must retain $target through its exit animation",
                source.contains("rememberSheetContent($target)"))
        }
        val batchDelete = source.substringAfter("SheetPanel(showBatchDelete").substringBefore("SheetPanel(showAdd")
        assertFalse("The deletion exit must not change to zero selected cards",
            batchDelete.contains("s.deleteCharacterCardsMessage(selectedCardIds.size)"))
        assertTrue("Retain the displayed delete count", source.contains("rememberSheetContent(selectedCardIds.size.takeIf"))
    }

    @Test fun sharedPickerRowCanHighlightWithoutChangingBatchSelectionAppearance() {
        val row = source().substringAfter("internal fun CharacterLibraryCardRow(").substringBefore("internal fun LibraryCharacterAvatar(")
        assertTrue("Batch cards retain their normal fill by default", row.contains("highlighted: Boolean = false"))
        assertTrue("Reuse the card material's existing animated selectedFill", row.contains("selected = highlighted"))
        for (color in listOf("selectedText", "selectedSubText")) {
            assertTrue("Picker selection needs the existing $color color", row.contains("colors.$color"))
        }
        assertTrue("Card selection colors follow the app motion timing", row.contains("FreeChatAnimation.colorTween"))
    }

    @Test fun folderAndGroupSearchInputsUseTheSharedAppShape() {
        assertTrue("Folder name follows the shared input shape", source().contains("shape = FreeChatInputShape"))
        val navigation = File("src/main/java/com/freechat/ui/screens/CharacterLibraryNavigation.kt").readText()
        assertTrue("Group message search follows the shared input shape", navigation.contains("shape = FreeChatInputShape"))
    }
}
