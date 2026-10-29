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

    @Test fun recommendationKeepsRealPlaybackButUnlocksButtonsAfterIndependentForegroundDwell() {
        val page = feature
        val bubble = source("ui/components/ChatBubble.kt")
        val lastStage = Regex("DEMO_LAST_STAGE = (\\d+)").find(page)!!.groupValues[1].toInt()
        val timelines = Regex("intArrayOf\\(([^)]*)\\)").findAll(page)
            .map { match -> match.groupValues[1].split(',').map { it.trim().toInt() } }.toList()
        assertEquals("Both recommendations have their own playback timeline", 2, timelines.size)
        assertTrue("Both real demos retain their full delayed playback independently of the CTA",
            timelines.all { it.size == lastStage && it.all { delayMs -> delayMs > 0 } && it.sum() >= 5000 })
        assertTrue(page.contains("playback.stageElapsedMs += elapsedMs"))
        assertTrue(page.contains("playback.stage += 1"))
        assertTrue("Each page has its own four-second foreground wait, not a full-playback requirement",
            page.contains("FEATURE_CTA_WAIT_MS = 4_000L") && page.contains("dwellMs >= FEATURE_CTA_WAIT_MS") &&
                page.contains("enabled = canAdvance") && !page.contains("enabled = stage >= DEMO_LAST_STAGE"))
        assertTrue("Revisits retain the same playback without inheriting another page's progress（停留解锁为全局一次钟，2026-10-28）",
            page.contains("List(FEATURE_REC_IDS.size) { FeatureRecPlaybackState() }") &&
                !page.contains("playback.dwellMs"))
        assertTrue("Native pager swipes remain available during playback and the button wait",
            page.contains("HorizontalPager(") && !page.contains("userScrollEnabled = false") &&
                !page.contains("detectTapGestures"))
        for (component in listOf("FeatureRecDemo(pageIndex, playback, active)", "ChatBubble(", "rememberMessageEntrance(")) {
            assertTrue("Recommendation must keep the real $component", page.contains(component))
        }
        assertTrue("Group messages retain real member identities",
            page.contains("groupChat = demoGroup") && page.contains("groupMemberId = line.senderId"))
        assertTrue("Stories use shared narrative typography, typewriter and dialogue emphasis",
            page.contains("textFlow = story") && page.contains("emphasizeDialogue = story") &&
                bubble.contains("TypewriterText(") && bubble.contains("SiriOrb("))
        assertTrue(page.contains("animationSpec = FreeChatAnimation.pageTween"))
        assertTrue("Playback and nested effects stop in the background or on another pager page",
            page.contains("repeatOnLifecycle(Lifecycle.State.RESUMED)") &&
                page.contains("pagerState.settledPage.takeUnless { pagerState.isScrollInProgress }") &&
                page.contains("LocalMotionEnabled provides (motionOn && active)"))
        assertTrue("Reduced motion shows the complete demo and uses immediate button navigation",
            page.contains("if (!motionOn)") && page.contains("playback.stage = DEMO_LAST_STAGE") &&
                page.contains("else pagerState.scrollToPage(1)"))
        assertFalse("The retired scene-image recommendation cannot return", page.contains("R.drawable.feature_rec_scene"))
    }
}
