package com.freechat.ui.screens

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source contracts cover navigation ownership; runtime UI checks remain separate. */
class CharacterLibrary1216SourceWiringTest {
    private fun source(name: String) = File("src/main/java/com/freechat/ui/screens/$name.kt")
        .takeIf { it.exists() }?.readText().orEmpty()

    @Test fun libraryUsesPersonaOnlyImportsAndIndependentNavigationCallbacks() {
        val library = source("CharacterLibraryScreen")
        for (contract in listOf("onEditCard:", "onStartChat:",
            "CharacterLibraryStore.state", "CharacterPersona.from(", "CharacterLibraryStore.add(")) {
            assertTrue("Library contract missing: $contract", library.contains(contract))
        }
        assertFalse(library.contains("startCompanionConversation("))
        assertFalse(library.contains("getMemories("))
        assertFalse("New roles are created from the sidebar flow", library.contains("onCreateCard"))
    }

    @Test fun settingsPreservesStoredCategoryOrdinalsAndDisplaysLibraryAfterModels() {
        val settings = source("SettingsScreen")
        val enumBody = settings.substringAfter("private enum class SettingsCategory(val icon: ImageVector) {")
            .substringBefore("fun title")
        val names = Regex("(?m)^    ([A-Z_]+)\\(Icons").findAll(enumBody).map { it.groupValues[1] }.toList()
        assertEquals(listOf("TABLET_DISPLAY", "MODELS", "AI_OPTIMIZATION", "PERSONALIZATION", "GENERAL", "ABOUT"), names.take(6))
        assertEquals("CHARACTER_LIBRARY", names.lastOrNull())
        val displayOrder = settings.substringAfter("private val settingsCategoryDisplayOrder = listOf(")
            .substringBefore(")")
        assertTrue(displayOrder.indexOf("SettingsCategory.CHARACTER_LIBRARY") > displayOrder.indexOf("SettingsCategory.MODELS"))
        assertTrue(displayOrder.indexOf("SettingsCategory.CHARACTER_LIBRARY") < displayOrder.indexOf("SettingsCategory.AI_OPTIMIZATION"))
        val models = settings.substringAfter("// ──── 模型选择 ────").substringBefore("// ──── 角色库 ────")
        assertFalse(models.contains("onOpenCharacterLibrary"))
        val library = settings.substringAfter("// ──── 角色库 ────").substringBefore("// ──── AI 系统优化 ────")
        assertTrue(library.contains("onOpenCharacterLibrary"))
        assertTrue(library.contains("SubScreen.BUILTIN_CHARACTER_POOL"))
        assertTrue(settings.contains("CHARACTER_LIBRARY -> s.characterLibrary"))
        assertFalse(settings.contains("if (category == SettingsCategory.CHARACTER_LIBRARY) onOpenCharacterLibrary()"))
    }

    @Test fun personaOnlyEditorHidesModeAndPartTwoAndKeepsASeparateStoreAction() {
        val setup = source("CharacterSetupScreen")
        assertTrue(setup.contains("personaOnly: Boolean = false"))
        assertTrue(setup.contains("creatingNew: Boolean = false"))
        assertTrue(setup.contains("val isEditing = initial != null && !creatingNew"))
        assertTrue(setup.contains("onStoreInPool: (CharacterProfile) -> Unit"))
        assertTrue(setup.contains("if (!personaOnly && !tabletCreation)"))
        assertTrue(setup.contains("if (!personaOnly) functionalSection()"))
        val store = setup.substringAfter("fun storeDraft() {").substringBefore("\n    }")
        assertTrue(store.contains("if (!savingAllowed()) return"))
        assertTrue(store.contains("if (!canStart) return"))
        assertTrue(store.contains("onStoreInPool("))
        assertFalse(store.contains("onCreate("))
        assertFalse(store.contains("modeMissing"))
        val submit = setup.substringAfter("fun submitDraft() {").substringBefore("\n    }")
        assertFalse(submit.contains("onStoreInPool("))
        assertTrue(setup.contains("s.storeInCharacterLibrary"))
        assertTrue(setup.contains("s.startCharacterChat"))
    }

    @Test fun groupCreationIsAnIndependentConversationChoice() {
        val setup = source("CharacterSetupScreen")
        assertFalse(setup.contains("onCreateGroup"))
        val modes = source("DialogueModeScreen")
        assertFalse(modes.contains("s.multiCharacterGroup"))
        assertTrue(modes.contains("FreeChatAnimation.expandEnter()"))
        assertTrue(modes.contains("FreeChatAnimation.expandExit()"))
        assertTrue(source("NewChatModeScreen").contains("onSelectGroup"))
    }

    @Test fun pickerKeepsTheApprovedContractAndExplainsRejectedSelections() {
        val picker = source("CharacterPickerScreen")
        for (contract in listOf("initialSelectedIds: Set<String> = emptySet()", "selectionLimit: Int = 4",
            "onConfirm: (List<CharacterCard>) -> Unit", "CharacterLibraryStore.state",
            "toggleCharacterSelection(", "s.characterSelectionLimit(", "selectedIds.size")) {
            assertTrue("Picker contract missing: $contract", picker.contains(contract))
        }
        assertFalse("Selection must never silently truncate", picker.contains(".take(selectionLimit)"))
    }

    @Test fun menusAndPreviewUseFolderScopeAndPersistedOrigin() {
        val library = source("CharacterLibraryScreen")
        val addMenu = library.substringAfter("SheetPanel(showAdd").substringBefore("SheetPanel(menuFolder")
        assertFalse(addMenu.contains("s.createCharacterCard"))
        assertTrue(addMenu.contains("s.addExistingCharacter"))
        assertTrue(addMenu.contains("if (folder == null)"))
        assertTrue(library.contains("if (card.canStartConversation)"))
        val preview = library.substringAfter("private fun CharacterPersonaPreview(").substringBefore("internal fun CharacterLibraryPage(")
        assertTrue(preview.contains("maxLines = 2, overflow = TextOverflow.Ellipsis"))
        val host = File("src/main/java/com/freechat/MainActivity.kt").readText()
        assertTrue(host.contains("if (card.canStartConversation)"))
        assertTrue(host.contains("startInEditMode = characterEditorCardId != null"))
        assertTrue(host.contains("characterLibraryFolderId"))
        assertTrue(library.contains("onSelectFolder"))
        assertFalse(library.contains("::requestBack"))
    }
}
