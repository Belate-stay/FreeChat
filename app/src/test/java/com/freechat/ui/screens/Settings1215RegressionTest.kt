package com.freechat.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Settings1215RegressionTest {
    private val modelPools = listOf("LANGUAGE_MODEL", "IMAGE_MODEL", "VISION_MODEL", "TTS_MODEL", "ASR_MODEL")
    private val screenClass = Class.forName("com.freechat.ui.screens.SubScreen")
    private val settingsClass = Class.forName("com.freechat.ui.screens.SettingsScreenKt")
    private val settingsSource get() = File("src/main/java/com/freechat/ui/screens/SettingsScreen.kt").readText()

    // Reflection keeps the settings-only navigation types private while exercising the real pure logic.
    private fun screen(name: String?): Any? = name?.let { route ->
        screenClass.enumConstants.orEmpty().first { (it as Enum<*>).name == route }
    }

    private fun backScreen(current: String?, origin: String?, tabletCategories: Boolean): String? {
        val method = settingsClass.declaredMethods.firstOrNull { it.name == "settingsBackScreen" }
        assertNotNull("Settings back routing must retain the API tutorial origin", method)
        method!!.isAccessible = true
        return (method.invoke(null, screen(current), screen(origin), tabletCategories) as Enum<*>?)?.name
    }

    @Test fun tutorialBackReturnsToEachOriginatingModelPoolOnPhoneAndTablet() {
        for (tabletCategories in listOf(false, true)) {
            for (origin in modelPools) {
                assertEquals(origin, backScreen("API_TUTORIAL", origin, tabletCategories))
            }
        }
    }

    @Test fun theNextBackReturnsFromTheModelPoolToSettings() {
        for (tabletCategories in listOf(false, true)) {
            for (origin in modelPools) {
                val returnedPool = backScreen("API_TUTORIAL", origin, tabletCategories)
                assertEquals(null, backScreen(returnedPool, null, tabletCategories))
            }
        }
    }

    @Test fun existingNestedSettingsPagesKeepTheirParents() {
        for (tabletCategories in listOf(false, true)) {
            assertEquals("FEEDBACK", backScreen("MY_FEEDBACK", null, tabletCategories))
            for (screen in listOf("AUTHOR", "DEV_PASSWORD", "DEV_OPTIONS")) {
                assertEquals(if (tabletCategories) null else "ABOUT_FREECHAT", backScreen(screen, null, tabletCategories))
            }
            assertEquals(null, backScreen("INPUT_BOX", null, tabletCategories))
        }
    }

    @Test fun tutorialMotionDepthIsOneLevelBelowItsModelPool() {
        val depth = settingsClass.declaredMethods.single { it.name == "subScreenDepth" }
        depth.isAccessible = true
        val tutorialDepth = depth.invoke(null, screen("API_TUTORIAL")) as Int
        for (origin in modelPools) {
            assertEquals((depth.invoke(null, screen(origin)) as Int) + 1, tutorialDepth)
        }
    }

    @Test fun sourceWiringSharesHardwareAndHeaderBackAndHandlesTutorialBeforePoolHostExit() {
        val source = settingsSource
        assertTrue(source.contains("BackHandler(enabled = settingsActive, onBack = navigateBack)"))
        assertTrue(source.contains("FloatingWindowBackButton(onClick = navigateBack"))
        val openSubScreen = source.substringAfter("fun openSubScreen(screen: SubScreen) {").substringBefore("\n    }")
        assertTrue("Capture the current pool when entering the tutorial", openSubScreen.contains("apiTutorialOrigin") &&
            openSubScreen.contains("screen == SubScreen.API_TUTORIAL") && openSubScreen.contains("subScreen"))
        val back = source.substringAfter("val navigateBack: () -> Unit = {").substringBefore("\n    }")
        val tutorialAt = back.indexOf("subScreen == SubScreen.API_TUTORIAL")
        val poolExitAt = back.indexOf("tabletModelPool")
        assertTrue("A nested tutorial returns before an embedded pool exits its host", tutorialAt >= 0 && poolExitAt > tutorialAt)
        assertTrue("Back uses the tested parent resolver", back.contains("settingsBackScreen(subScreen, apiTutorialOrigin, showTabletCategories)"))
    }

    @Test fun sourceWiringPersonalizationKeepsRequestedOrderAndInputBoxHasNoSubtitle() {
        val personalization = settingsSource.substringAfter("selectedCategory == SettingsCategory.PERSONALIZATION) {")
            .substringBefore("selectedCategory == SettingsCategory.GENERAL)")
        val labels = listOf("s.inputBox", "s.appIcon", "s.font,", "s.colorTheme,", "s.advancedMaterial,", "s.headerBarStyle,", "s.liquidBackdrop,")
        var previous = -1
        for (label in labels) {
            val at = personalization.indexOf(label)
            assertTrue("Personalization entry $label must follow the previous entry", at > previous)
            previous = at
        }
        assertTrue("The input-box entry is title-only", Regex("SettingsItem\\(Icons\\.Filled\\.Keyboard, s\\.inputBox,\\s*null,").containsMatchIn(personalization))
    }
}
