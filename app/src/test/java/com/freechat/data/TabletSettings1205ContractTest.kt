package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TabletSettings1205ContractTest {
    private val settings get() = File("src/main/java/com/freechat/ui/screens/SettingsScreen.kt").readText()
    private val tabletPreferences get() = settings.substringAfter("private fun TabletDisplayPreferences(")
        .substringBefore("private fun SettingsCategoryPane(")

    @Test fun displayLogicIsOneStandardSettingsItemWithConditionalChildren() {
        val preferences = tabletPreferences
        assertTrue(preferences.contains("SettingsGroup(expanded = displayMode == TabletDisplayMode.FLOATING"))
        assertTrue(preferences.contains("SettingsItem(Icons.Filled.TabletAndroid, s.tabletPageDisplayLogic"))
        assertTrue(preferences.contains("s.tabletDisplayModeLabel(displayMode)"))
        assertTrue(preferences.contains("onClick = onOpenDisplayMode"))
        assertFalse("Inline radio rows must be replaced by the existing selection sheet", preferences.contains("TabletDisplayChoice("))

        val position = preferences.indexOf("s.tabletFloatingPosition")
        val touch = preferences.indexOf("s.tabletUnderlyingTouch")
        assertTrue("Floating position precedes underlying touch", position >= 0 && touch > position)
        assertTrue(preferences.substring(position, touch).contains("SettingsDivider(colors)"))
        assertTrue(preferences.contains("s.tabletFloatingSideLabel(floatingSide)"))
        assertTrue(preferences.contains("onClick = onOpenFloatingSide"))
        assertTrue(preferences.contains("Switch(checked = underlyingTouch"))
        assertTrue(preferences.contains("onCheckedChange = onUnderlyingTouch"))
        assertTrue("The touch switch needs an accessibility label", preferences.contains("contentDescription = s.tabletUnderlyingTouch"))
    }

    @Test fun displayAndPositionUseStandardSelectionSheetsAndDismissOnNavigation() {
        val source = settings
        assertTrue(source.contains("SheetPanel(showTabletDisplayModePicker"))
        assertTrue(source.contains("listOf(TabletDisplayMode.FLOATING, TabletDisplayMode.EMBEDDED).forEach"))
        assertTrue(source.contains("title = s.tabletDisplayModeLabel(mode)"))
        assertTrue(source.contains("viewModel.setTabletDisplayMode(mode); showTabletDisplayModePicker = false"))
        assertTrue(source.contains("SheetPanel(showTabletFloatingSidePicker && tabletDisplayMode == TabletDisplayMode.FLOATING"))
        assertTrue(source.contains("TabletFloatingSide.entries.forEach"))
        assertTrue(source.contains("title = s.tabletFloatingSideLabel(side)"))
        assertTrue(source.contains("viewModel.setTabletFloatingSide(side); showTabletFloatingSidePicker = false"))
        val cleanup = source.substringAfter("val dismissPageOverlays: () -> Unit = {")
            .substringBefore("\n    }")
        assertTrue(cleanup.contains("showTabletDisplayModePicker = false"))
        assertTrue(cleanup.contains("showTabletFloatingSidePicker = false"))
    }

    @Test fun underlyingTouchDefaultsOnAndUsesTheExistingPersistencePath() {
        val repository = File("src/main/java/com/freechat/data/SettingsRepository.kt").readText()
        assertTrue(repository.contains("KEY_TABLET_UNDERLYING_TOUCH = booleanPreferencesKey(\"tablet_underlying_touch\")"))
        val preference = repository.substringAfter("val tabletUnderlyingTouch: Flow<Boolean>")
            .substringBefore(".distinctUntilChanged()")
        assertTrue(preference.contains("prefs[KEY_TABLET_UNDERLYING_TOUCH] ?: true"))
        val save = repository.substringAfter("suspend fun saveTabletUnderlyingTouch(enabled: Boolean)")
            .substringBefore("\n    }")
        assertTrue(save.contains("edit { prefs -> prefs[KEY_TABLET_UNDERLYING_TOUCH] = enabled }"))

        val viewModel = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()
        assertTrue(viewModel.contains("val tabletUnderlyingTouch: StateFlow<Boolean> = settingsRepo.tabletUnderlyingTouch"))
        assertTrue(viewModel.substringAfter("val tabletUnderlyingTouch: StateFlow<Boolean>")
            .substringBefore("\n\n").contains(".stateIn(viewModelScope, SharingStarted.Eagerly, true)"))
        assertTrue(viewModel.contains("settingsRepo.saveTabletUnderlyingTouch(enabled)"))
        assertTrue(settings.contains("viewModel.tabletUnderlyingTouch.collectAsState()"))
        assertTrue(settings.contains("viewModel::setTabletUnderlyingTouch"))
    }

    @Test fun onlyEmbeddedDetailBodiesHideTheirRedundantLeadingCategoryLabels() {
        val source = settings
        for ((icon, label) in listOf(
            "SmartToy" to "sectionModels", "AutoAwesome" to "sectionAiOptimize",
            "DarkMode" to "sectionSystemTheme", "Tune" to "sectionGeneral",
        )) {
            assertTrue("Phone headings must survive for $label", source.contains(
                "if (!showTabletCategories) SectionLabel(Icons.Filled.$icon, s.$label)"))
        }
        assertTrue(tabletPreferences.contains("showSectionLabel: Boolean = true"))
        assertTrue(tabletPreferences.contains("if (showSectionLabel) SectionLabel(Icons.Filled.TabletAndroid, s.tabletPageDisplayLogic)"))
        val embedded = source.substringAfter("if (showTabletCategories && selectedCategory == SettingsCategory.TABLET_DISPLAY)")
            .substringBefore("// ──── 模型选择")
        assertTrue(embedded.contains("showSectionLabel = false"))
        val floating = source.substringAfter("SubScreen.TABLET_DISPLAY -> if (isTabletDevice)")
            .substringBefore("SubScreen.ABOUT_FREECHAT ->")
        assertTrue("The dedicated floating page must not repeat the item name above itself", floating.contains("showSectionLabel = false"))
        assertTrue("Existing About content stays in use", source.contains("SubScreen.ABOUT_FREECHAT -> AboutFreeChatPage("))
    }
}
