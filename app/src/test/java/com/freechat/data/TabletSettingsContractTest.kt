package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TabletSettingsContractTest {
    private val settings get() = File("src/main/java/com/freechat/ui/screens/SettingsScreen.kt").readText()

    @Test fun fullTabletSettingsKeepsCategoryAndDetailPanes() {
        val source = settings
        assertTrue(source.contains("LocalTabletUi.current && !tabletModelPool"))
        assertTrue(source.contains("TabletWorkspace("))
        assertTrue(source.contains("SettingsCategory.entries"))
        assertTrue(source.contains("selectedCategory = category"))
        for (category in listOf("MODELS", "AI_OPTIMIZATION", "PERSONALIZATION", "GENERAL", "ABOUT")) {
            assertTrue("Missing tablet category $category", source.contains("SettingsCategory.$category"))
        }
    }

    @Test fun tabletCategoriesReuseLocalizedExistingSections() {
        val source = settings
        for (label in listOf("sectionModels", "sectionAiOptimize", "sectionSystemTheme", "sectionGeneral", "sectionAbout")) {
            assertTrue("Missing existing section label $label", source.contains("s.$label"))
        }
        assertTrue(source.contains("if (!showTabletCategories || selectedCategory == SettingsCategory.MODELS)"))
        assertTrue(source.contains("if (!showTabletCategories || selectedCategory == SettingsCategory.AI_OPTIMIZATION)"))
        assertTrue(source.contains("if (!showTabletCategories || selectedCategory == SettingsCategory.PERSONALIZATION)"))
        assertTrue(source.contains("if (!showTabletCategories || selectedCategory == SettingsCategory.GENERAL)"))
        assertTrue(source.contains("if (!showTabletCategories || selectedCategory == SettingsCategory.ABOUT)"))
    }

    @Test fun mountedModelEditorPoolTracksRequestedModelType() {
        val source = settings
        assertTrue(source.contains("LaunchedEffect(openModelListOnLaunch, tabletModelPool)"))
        assertTrue(source.contains("if (tabletModelPool && openModelListOnLaunch != null)"))
        assertTrue(source.contains("subScreen = modelPoolScreen(openModelListOnLaunch)"))
        assertTrue(source.contains("ModelAccessPolicy.canEdit(model)"))
    }

    @Test fun tabletSettingsPreservesMotionScrollKeysAndPaneExpansion() {
        val source = settings
        assertTrue(source.contains("PageMotion("))
        assertTrue(source.contains("FreeChatAnimation.contentReplacement()"))
        assertTrue(source.contains("if (showTabletCategories) \"settings_main_\" + selectedCategory.name else \"main\""))
        assertTrue(source.contains("\"sub_\" + screen.name, levelScrolls"))
        assertTrue(source.contains("tabletReadingGutter()"))
        assertTrue("Both pane headers need expansion controls", Regex("PaneExpandButton\\(\\)").findAll(source).count() >= 2)
    }

    @Test fun selectedTabletCategorySurvivesStandalonePageDetours() {
        val source = settings
        assertTrue(source.contains("SettingsCategory.entries.getOrNull(levelScrolls[\"settings_selected_category\"]"))
        assertTrue(source.contains("levelScrolls[\"settings_selected_category\"] = selectedCategory.ordinal"))
        assertFalse("Only the root drawer entry clears saved settings state", source.contains("levelScrolls.clear()"))
    }

    @Test fun categoryNavigationDismissesPreviousPageOverlaysAndTemporaryInput() {
        val source = settings
        assertTrue(source.contains("if (selectedCategory != category || subScreen != null)"))
        assertTrue(source.contains("dismissPageOverlays()"))
        assertTrue(source.contains("if (showTabletCategories) dismissPageOverlays()"))
        val cleanup = source.substringAfter("val dismissPageOverlays: () -> Unit = {", "")
            .substringBefore("\n    }")
        for (flag in listOf("showThemePicker", "showColorThemePicker", "showInputStylePicker",
            "showInputBarStatePicker", "showHeaderStylePicker", "showTempPicker", "showLengthPicker",
            "showLanguagePicker", "showChatModePicker", "showFontSizePicker", "showGlobalMemoryEditor",
            "showWebSearchConfirm", "showSearchSettings", "showLiquidBackdropConfirm", "showQrSaveSheet",
            "showIconRestartConfirm")) {
            assertTrue("Navigation must dismiss $flag", cleanup.contains("$flag = false"))
        }
        assertTrue(cleanup.contains("newMemoryText = \"\""))
        assertTrue(cleanup.contains("if (showIconRestartConfirm) selectedIcon = activeIcon"))
        assertTrue(cleanup.contains("myShares.resetOverlays()"))
        assertTrue(cleanup.contains("focusManager.clearFocus()"))
    }

    @Test fun categoryPaneBackLeavesSettingsInsteadOfChangingHiddenDetail() {
        val listPane = settings.substringAfter("listPane = {", "").substringBefore("detailPane = detailPane")
        assertTrue(listPane.contains("onBack = {"))
        val categoryBack = listPane.substringAfter("onBack = {", "").substringBefore("}")
        assertTrue(categoryBack.contains("dismissPageOverlays()"))
        assertTrue(categoryBack.contains("onBack()"))
        assertFalse(categoryBack.contains("navigateBack"))
    }

    @Test fun tabletDisplayCategoryIsFirstButTheInitialDetailStaysOnModels() {
        val source = settings
        val categories = source.substringAfter("private enum class SettingsCategory(val icon: ImageVector) {")
            .substringBefore("fun title")
        assertTrue(categories.indexOf("TABLET_DISPLAY(") in 0 until categories.indexOf("MODELS("))
        assertTrue(source.contains("levelScrolls[\"settings_selected_category\"] ?: SettingsCategory.MODELS.ordinal"))
    }

    @Test fun phoneShapedTabletSettingsKeepsTheOptimizationEntryWithoutCategoryPanes() {
        val source = settings
        assertTrue(source.contains("val isTabletDevice = LocalTabletDevice.current || LocalTabletUi.current"))
        assertTrue(source.contains("if (isTabletDevice && !showTabletCategories)"))
        assertTrue(source.contains("subScreen = SubScreen.TABLET_DISPLAY"))
        assertTrue(source.contains("SubScreen.TABLET_DISPLAY ->"))
        assertTrue(source.contains("LocalTabletUi.current && !tabletModelPool"))
    }

    @Test fun tabletFloatingPositionUsesTheExistingConditionalDisclosureAndImmediateSetters() {
        val source = settings
        assertTrue(source.contains("expanded = displayMode == TabletDisplayMode.FLOATING"))
        assertTrue(source.contains("TabletFloatingSide.entries.forEach"))
        assertTrue(source.contains("viewModel.setTabletDisplayMode(mode)"))
        assertTrue(source.contains("viewModel.setTabletFloatingSide(side)"))
        val vm = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()
        assertTrue(vm.contains("val tabletDisplayMode: StateFlow<TabletDisplayMode>"))
        assertTrue(vm.contains("val tabletFloatingSide: StateFlow<TabletFloatingSide>"))
        assertTrue(vm.contains("settingsRepo.saveTabletDisplayMode(mode)"))
        assertTrue(vm.contains("settingsRepo.saveTabletFloatingSide(side)"))
    }
}
