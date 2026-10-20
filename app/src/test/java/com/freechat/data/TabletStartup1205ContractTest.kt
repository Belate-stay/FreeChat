package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TabletStartup1205ContractTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()
    private val agreement get() = source("ui/screens/AgreementScreen.kt")
        .substringAfter("fun AgreementGatePage(")
    private val notice get() = source("ui/components/NoticeDialog.kt")
    private val feature get() = source("ui/components/FeatureRecPage.kt")

    @Test fun firstUseTabletGateHasAReadableColumnAndBoundedAction() {
        val gate = agreement
        assertTrue("Startup gates use device identity even outside a tablet pane", gate.contains("LocalTabletDevice.current"))
        assertTrue("Tablet reading must stop growing at 720dp", gate.contains("widthIn(max = 720.dp)"))
        assertTrue("The reading column is centered in the window", gate.contains(".align(Alignment.TopCenter)"))
        assertTrue("Tablet acceptance action is bounded", gate.contains("widthIn(max = 320.dp)"))
        val action = gate.substringAfter("onClick = onAgree").substringBefore("shape =")
        assertTrue("The bounded tablet acceptance action is centered in its reading column",
            action.contains("widthIn(max = 320.dp).align(Alignment.CenterHorizontally)"))
        assertTrue("Short windows still reach the acceptance controls", gate.contains(".verticalScroll(rememberScrollState())"))
        assertTrue("Tablet controls clear the system navigation area", gate.contains("navigationBarsPadding()"))
    }

    @Test fun firstUseGateKeepsTheOriginalAcceptanceAndDocumentActions() {
        val gate = agreement
        assertTrue(gate.contains("var agreed by remember { mutableStateOf(false) }"))
        assertTrue(gate.contains("s.agreementGateItems.forEachIndexed"))
        assertTrue(gate.contains("onCheckedChange = { agreed = it }"))
        assertTrue(gate.contains("Modifier.clickable { agreed = !agreed }"))
        assertTrue(gate.contains("Modifier.clickable { onOpenAgreement() }"))
        assertTrue(gate.contains("onClick = onAgree"))
        assertTrue(gate.contains("enabled = agreed"))
        assertTrue(gate.contains("s.agreementContinue"))
    }

    @Test fun tabletNoticeBoundsTheCardAndScrollsTheBodyBeforeTheActions() {
        val dialog = notice
        assertTrue(dialog.contains("LocalTabletDevice.current"))
        assertTrue("Dialog height must use its actual window constraints", dialog.contains("BoxWithConstraints("))
        assertTrue("The centered dialog is capped at 520dp", dialog.contains("widthIn(max = 520.dp)"))
        assertTrue(dialog.contains("heightIn(max = maxHeight)"))
        assertTrue("Long release notes scroll while actions stay available", dialog.contains("weight(1f, fill = false)"))
        assertTrue(dialog.contains(".verticalScroll(rememberScrollState())"))
        assertTrue("Short or enlarged-text windows need a whole-card scroll fallback", dialog.contains("scrollWholeCard"))
        assertTrue(dialog.contains("LocalDensity.current.fontScale"))
        assertTrue("Tablet buttons retain at least a 48dp target", dialog.contains("heightIn(min = 48.dp)"))
    }

    @Test fun noticeRemainsButtonsOnlyAndPassesItsCurrentCheckboxState() {
        val dialog = notice
        assertTrue(dialog.contains("remember(visible) { mutableStateOf(false) }"))
        assertTrue(dialog.contains("BackHandler(enabled = visible) {"))
        assertEquals("Only the dismiss button may dismiss the dialog", 1,
            Regex("onDismiss\\(checked\\)").findAll(dialog).count())
        assertEquals("Only the confirm button may confirm the dialog", 1,
            Regex("onConfirm\\(checked\\)").findAll(dialog).count())
        assertTrue(dialog.contains("onCheckedChange = { checked = it }"))
        assertTrue(dialog.contains(".clickable { checked = !checked }"))
        assertTrue(dialog.contains("FreeChatAnimation.dialogEnter()"))
        assertTrue(dialog.contains("FreeChatAnimation.dialogExit()"))
        assertTrue(dialog.contains(".frostedCard(hazeState, colors, advancedMaterial, shape"))
    }

    @Test fun tabletRecommendationUsesAProportionalDemoAndResponsiveReadingLayout() {
        val page = feature
        assertTrue(page.contains("LocalTabletDevice.current"))
        assertTrue("Phone content must retain a separate unchanged path", page.contains("if (tablet)"))
        assertTrue(page.contains("TabletFeatureRecContent("))
        val tablet = page.substringAfter("private fun TabletFeatureRecContent(")
            .substringBefore("/** 演示片段文案")
        assertTrue("Landscape uses separate reading and demonstration columns", tablet.contains("if (split)"))
        assertTrue(tablet.contains("Row("))
        assertTrue("The demo stays bounded instead of spanning the tablet", tablet.contains("widthIn(max = 420.dp)"))
        assertTrue("The action stays reachable with scrolling content", tablet.contains("weight(1f)"))
        assertTrue(tablet.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(tablet.contains("widthIn(max = 320.dp)"))
        assertTrue(tablet.contains("navigationBarsPadding()"))
        assertTrue("Large type affects the layout instead of shrinking text", tablet.contains("LocalDensity.current.fontScale"))
        val landscape = tablet.substringAfter("if (split) {").substringBefore("} else {")
        assertTrue("The preview is left and the title, introduction and action are right",
            landscape.indexOf("demonstration()") in 0 until landscape.indexOf("introduction(false)"))
        val introColumn = landscape.substringAfter("Column(")
        assertTrue("Landscape CTA is centered in the right intro column, not across the window",
            introColumn.substringBefore("{").contains("horizontalAlignment = Alignment.CenterHorizontally"))
        assertTrue("The landscape introduction stays left-aligned", introColumn.contains("introduction(false)"))
        assertTrue("The landscape action stays inside the intro column", introColumn.contains("action()"))
    }

    @Test fun recommendationKeepsTheExistingTimelineGateAndRealDemoComponents() {
        val page = feature
        assertTrue(page.contains("intArrayOf(400, 250, 3600, 2600, 1200, 900, 3200, 1800)"))
        assertTrue(page.contains("delay(ms.toLong())"))
        assertTrue(page.contains("stage = i + 1"))
        assertTrue(page.contains("enabled = stage >= 8"))
        assertTrue("Demo tap targets remain intercepted without blocking page scrolling",
            Regex("detectTapGestures\\(onTap = \\{\\}, onLongPress = \\{\\}\\)").findAll(page).count() >= 2)
        for (component in listOf("FeatureRecDemo(stage, colors)", "TypewriterText(", "SiriOrb(", "GeneratedImage(")) {
            assertTrue("Recommendation must keep the real $component", page.contains(component))
        }
        assertTrue(page.contains("FreeChatAnimation.menuEnter(TransformOrigin(0f, 1f))"))
        assertTrue(page.contains("source = if (stage >= 7) scenePath else null"))
    }
}
