package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TabletSettings1204ContractTest {
    private val settings get() = File("src/main/java/com/freechat/ui/screens/SettingsScreen.kt").readText()

    @Test fun embeddedAboutRendersTheExistingPageWithoutAnotherAboutEntry() {
        val source = settings
        assertTrue(source.contains("val embeddedAbout = showTabletCategories && screen == null && selectedCategory == SettingsCategory.ABOUT"))
        assertTrue(source.contains("if (screen == null && !embeddedAbout)"))
        assertTrue(source.contains("SubScreen.ABOUT_FREECHAT -> AboutFreeChatPage("))
        assertTrue(source.contains("onOpenFeedback = if (embeddedAbout)"))
        assertTrue(source.contains("onOpenMyShares = if (embeddedAbout)"))
        // Phone and floating tablet settings keep their original main-list entry.
        assertTrue(source.contains("if (!showTabletCategories || selectedCategory == SettingsCategory.ABOUT)"))
        assertTrue(source.contains("openSubScreen(SubScreen.ABOUT_FREECHAT)"))
    }

    @Test fun mergedFeedbackAndLoggedInSharesStayInsideTheExistingEntryCardBeforeRating() {
        val about = settings.substringAfter("private fun AboutFreeChatPage(")
            .substringBefore("private object AboutUpdateStore")
        assertTrue(about.contains("onOpenFeedback: (() -> Unit)? = null"))
        assertTrue(about.contains("onOpenMyShares: (() -> Unit)? = null"))
        val agreement = about.indexOf("AboutEntryRow(s.userAgreement")
        val developer = about.indexOf("AboutEntryRow(com.freechat.dev.DevMode.TITLE_OPTIONS")
        val feedback = about.indexOf("AboutEntryRow(s.feedbackEntry")
        val shares = about.indexOf("AboutEntryRow(s.myShares")
        val rating = about.indexOf("com.freechat.dev.RatingCard(")
        assertTrue("Feedback follows the existing developer row", feedback > developer && developer > agreement)
        assertTrue("Shares follow feedback and precede the rating card", shares > feedback && rating > shares)
        assertTrue(about.substring(developer, shares).contains("Session.isLoggedIn()"))
        assertTrue("Existing update animation must remain", about.contains("glide.animateTo(1f, tween(520, easing = FreeChatAnimation.arrivalEase))"))
        assertTrue("Existing update scroll identity must remain", about.contains("rememberPersistentScrollState(\"sub_ABOUT_FREECHAT\", levelScrolls)"))
    }

    @Test fun authorAndDeveloperBackReturnToTheMergedTabletCategory() {
        val source = settings
        assertTrue(source.contains("SubScreen.AUTHOR, SubScreen.DEV_PASSWORD, SubScreen.DEV_OPTIONS ->"))
        assertTrue(source.contains("if (showTabletCategories) null else SubScreen.ABOUT_FREECHAT"))
        assertTrue(source.contains("onUnlocked = { returnToAbout() }"))
        assertTrue(source.contains("onExit = { returnToAbout() }"))
    }

    @Test fun externalTabletDetailsHaveAnOptionalRouteAndRetainedContentSlot() {
        val source = settings
        assertTrue(source.contains("tabletDetailKey: String? = null"))
        assertTrue(source.contains("tabletDetailBack: Boolean = false"))
        assertTrue(source.contains("tabletDetailContent: (@Composable () -> Unit)? = null"))
        assertTrue(source.contains("onTabletCategorySelected: () -> Unit = {}"))
        assertTrue("Outgoing routes need their own content snapshot", source.contains("remember(tabletDetailKey)"))
        assertTrue(source.contains("retainedTabletDetailContent"))
        val categorySelection = source.substringAfter("}) { category ->").substringBefore("detailPane = detailPane")
        assertTrue("Every category click closes an external detail", categorySelection.contains("onTabletCategorySelected()"))
        assertTrue("Underlying screen is retained during an external detour", source.contains("var subScreen by remember"))
        assertFalse(source.contains("LaunchedEffect(tabletDetailKey) {\n        subScreen = null"))
    }

    @Test fun externalDetailOwnsTheWholeRightPaneAndDisablesUnderlyingInteraction() {
        val source = settings
        assertTrue(source.contains("if (page.externalContent != null)"))
        assertTrue(source.contains("page.externalContent.invoke()"))
        assertTrue(source.contains("else settingsPage(page.screen to page.category)"))
        assertTrue(source.contains(".paneInteraction(settingsActive)"))
        assertTrue(source.contains("LocalPageActive provides settingsActive"))
        assertTrue(source.contains("BackHandler(enabled = settingsActive"))
        assertTrue(source.contains("focusManager.clearFocus()"))
    }

    @Test fun categoryPaneDoesNotInterceptSystemBackForAnExternalDetail() {
        val categoryPane = settings.substringAfter("private fun SettingsCategoryPane(")
            .substringBefore("private fun ColumnScope.ModelPoolPage(")
        assertFalse("Only the right detail or root route should handle system Back", categoryPane.contains("BackHandler("))
        assertTrue("The visible category-header Back button stays available", categoryPane.contains("HeaderIconButton(onClick = onBack)"))
    }

    @Test fun embeddedPagesUseExplicitCoverAndBackIntentWhilePhonesKeepPageMotion() {
        val source = settings
        assertTrue(source.contains("TabletPageMotion("))
        assertTrue(source.contains("PageMotion("))
        assertTrue(source.contains("forward = { initial, target -> subScreenDepth(target.first) >= subScreenDepth(initial.first) }"))
        val back = source.substringAfter("val navigateBack: () -> Unit = {").substringBefore("val statusBarHeightDp")
        assertTrue(back.contains("tabletSubpageBack = true"))
        val categorySelection = source.substringAfter("}) { category ->").substringBefore("detailPane = detailPane")
        assertTrue(categorySelection.contains("tabletSubpageBack = false"))
        assertTrue(source.contains("fun openSubScreen(screen: SubScreen)"))
        val open = source.substringAfter("fun openSubScreen(screen: SubScreen)").substringBefore("\n    }")
        assertTrue(open.contains("tabletSubpageBack = false"))
        assertTrue(source.contains("back = tabletPageBack"))
    }
}
