package com.freechat.ui.components

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source contracts complement the real phone/tablet playback checks for the startup guide. */
class FeatureRecommendation1223SourceTest {
    private fun source() = File("src/main/java/com/freechat/ui/components/FeatureRecPage.kt").readText()

    @Test fun groupRepliesPopCompleteWithoutThinkingAndDemoUserStaysAnonymous() {
        val page = source()
        assertTrue("群聊本质是微信聊天：角色回复整条直接弹出（头像+名称+气泡同时），不做思考前奏",
            page.contains("val pending = story && stage == arrivalStage"))
        assertTrue("思考演出只留给文本页", page.contains("val thinking = story && stage == 2"))
        assertFalse("群聊页不得再按 2/4/6 驻留思考", page.contains("stage in listOf(2, 4, 6)"))
        assertTrue("演示的「我」只用默认灰头像", page.contains("useDefaultUserAvatar = true"))
        for (line in listOf("晚上去吃自助餐吧", "轻奢一把", "吃啥？", "烤肉？披萨？牛排？", "都行",
                "陪对象呢没空", "去不了，导员非要让我当志愿者")) {
            assertTrue("用户定稿台词在场：$line", page.contains(line))
        }
        assertFalse("旧演示台词退场", page.contains("周末去山顶看日出"))
        assertTrue("逐条时间差：一条入场完再触发下一条（同人也逐条，不并行）",
            page.contains("DEMO_GROUP_LINE_STAGES = listOf(1, 2, 3, 4, 5, 6, 7)"))
        assertTrue("尽量一页装下：条距收紧", page.contains("Arrangement.spacedBy(8.dp)"))
        assertTrue("全屏导览必须选钮：返回键吞掉（防误退 App）",
            page.contains("BackHandler(enabled = !completed)"))
        assertTrue("功能名与简介拆行分样式（名加重蓝、简介小字弱化）",
            page.contains("private fun FeatureRecIntroduction(") &&
                page.contains("fontSize = 13.sp") && page.contains("fontSize = 14.sp"))
        assertTrue("文本页用户输入侧重剧情人物、AI 长文突出多角色且铺满卡片",
            page.contains("DEMO_STORY_INPUT") && page.contains("悬念留在蝴蝶上") &&
                page.contains("谁也别碰"))
        val bubble = File("src/main/java/com/freechat/ui/components/ChatBubble.kt").readText()
        val avatar = File("src/main/java/com/freechat/ui/components/GroupAvatar.kt").readText()
        assertTrue("ChatBubble 把匿名头像开关透传到群聊行", bubble.contains("useDefaultUserAvatar"))
        assertTrue("默认灰=未设置头像同款兜底，不读账号头像",
            avatar.contains("if (useDefaultUserAvatar) null"))
    }

    @Test fun recommendationsReplaceTheRetiredSceneImageWithTwoRealConversationModes() {
        val page = source()
        assertTrue("The first recommendation demonstrates real multi-character group messages",
            page.contains("GroupChatProfile(") && page.contains("GroupMember(") &&
                page.contains("groupChat = demoGroup"))
        assertTrue("The story uses the same narrative ChatBubble path as a real story",
            page.contains("ChatBubble(") && page.contains("textFlow = story") &&
                page.contains("emphasizeDialogue = story"))
        assertTrue("Synthetic messages retain each group member's identity", page.contains("groupMemberId ="))
        assertTrue("The recommendation renders companion messages", page.contains("ChatMode.COMPANION"))
        for (retired in listOf("R.drawable.feature_rec_scene", "GeneratedImage(", "ChatGPT-Image-2.5")) {
            assertFalse("The retired scene-image recommendation must not display: $retired", page.contains(retired))
        }
    }

    @Test fun recommendationsUseTheRealPagerAndAllowSwipingBeforeTheButtonsUnlock() {
        val page = source()
        assertTrue("The recommendation uses the real Compose horizontal pager",
            page.contains("HorizontalPager(") && page.contains("rememberPagerState") && page.contains("state = pagerState"))
        assertFalse("Waiting for a button must not disable swipes", page.contains("userScrollEnabled = false"))
        assertFalse("A demo touch shield must not swallow horizontal pager gestures", page.contains("detectTapGestures"))
        assertTrue("The Next button uses the same pager", page.contains("pagerState.animateScrollToPage(1"))
        assertTrue("Each page has a visible position", page.contains("pageIndex + 1") && page.contains("/ 2"))
    }

    @Test fun buttonsUnlockOnceAfterFourSecondsAndStayUnlockedAcrossPages() {
        val page = source()
        assertTrue("The foreground wait is four seconds", page.contains("FEATURE_CTA_WAIT_MS = 4_000L"))
        assertTrue("解锁是全局一次钟：「下一页」和「立即体验」同时亮", page.contains("dwellMs >= FEATURE_CTA_WAIT_MS"))
        assertFalse("翻页不得重新计时（2026-10-28 工单）：逐页 dwell 已废", page.contains("playback.dwellMs"))
        assertTrue(page.contains("enabled = canAdvance"))
        assertFalse("Full demo playback must not gate the button", page.contains("enabled = stage >= DEMO_LAST_STAGE"))
        assertTrue("解锁后常亮不闪：enabled 不跟 active/navigating 联动",
            page.contains("val canAdvance = dwellMs >= FEATURE_CTA_WAIT_MS && !completed") &&
                !page.contains("val canAdvance = active"))
        assertTrue("每页仍各自保留演示进度（但不各自计时解锁）",
            page.contains("List(FEATURE_REC_IDS.size) { FeatureRecPlaybackState() }") &&
                page.contains("playback.stageElapsedMs"))
        assertTrue("Time is measured while foreground",
            page.contains("SystemClock.elapsedRealtime()") &&
                page.contains("pagerState.settledPage.takeUnless { pagerState.isScrollInProgress }"))
    }

    @Test fun pageExposureHasSeparateStableIdsAndOnlyRunsForTheForegroundPage() {
        val page = source()
        assertTrue("Existing named onDone calls remain compatible",
            page.contains("onPageShown: (String) -> Unit = {}"))
        assertTrue(page.contains("\"builtin-feature-group-chat\""))
        assertTrue(page.contains("\"builtin-feature-story-text\""))
        assertTrue("Lifecycle changes cannot count hidden/background pages",
            page.contains("repeatOnLifecycle(Lifecycle.State.RESUMED)"))
        assertTrue("Exposure observes the settled pager page",
            page.contains("snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }") &&
                page.contains("if (scrolling) return@collectLatest"))
        assertTrue("Pause/resume and layout changes do not duplicate an impression",
            page.contains("shownPages.add(pageId)"))
        assertTrue("The latest external callback is used", page.contains("rememberUpdatedState(onPageShown)"))
    }

    @Test fun nextAndFinishChoicesUseStableIdsAndFinalCompletionOnlyHappensOnce() {
        val page = source()
        assertTrue("Page choice reporting is optional for existing callers",
            page.contains("onPageChoice: (String, String) -> Unit = { _, _ -> }"))
        assertTrue("The first page reports next and the last page reports done",
            page.contains("reportChoice(pageId, \"next\")") && page.contains("reportChoice(pageId, \"done\")"))
        assertTrue("The final completion is claimed before calling onDone",
            page.indexOf("completed = true") in 0 until page.indexOf("onDone()"))
        assertTrue("Rapid taps and hidden-page actions cannot finish or navigate twice",
            page.contains("if (completed || navigating") && page.contains("pageIndex != pagerState.settledPage"))
    }

    @Test fun actualSwipeChoicesAreDistinctAndProgrammedNextDoesNotDoubleCount() {
        val page = source()
        assertTrue(page.contains("\"swipe-next\""))
        assertTrue(page.contains("\"swipe-previous\""))
        assertTrue("The swipe is attributed to the page that was left", page.contains("FEATURE_REC_IDS[previousPage]"))
        assertTrue("Programmed Next is tagged until its settled-page change is consumed",
            page.contains("programmaticTarget = 1") && page.contains("if (programmaticTarget == pageIndex)"))
        assertTrue("Canceled navigation cannot suppress the next real swipe",
            page.contains("if (pagerState.settledPage != 1) programmaticTarget = null"))
    }

    @Test fun playbackPausesOffscreenAndReducedMotionImmediatelyProvidesReadableContent() {
        val page = source()
        assertTrue(page.contains("LocalLifecycleOwner.current"))
        assertTrue(page.contains("repeatOnLifecycle(Lifecycle.State.RESUMED)"))
        assertTrue(page.contains("LocalMotionEnabled.current"))
        assertTrue("Reduced motion provides full demo content without changing the dwell rule",
            page.contains("if (!motionOn)") && page.contains("playback.stage = DEMO_LAST_STAGE"))
        assertTrue("Only the foreground settled page can play",
            page.contains("val active = foreground && pageIndex == pagerState.settledPage && !pagerState.isScrollInProgress"))
        assertTrue("Real nested component motion stops offscreen",
            page.contains("LocalMotionEnabled provides (motionOn && active)"))
        assertTrue("Static offscreen previews do not retain running body-entry animations", page.contains("key(active)"))
        assertTrue("The demo can be scrolled while active without waiting for the full playback",
            page.contains("verticalScroll(demoScroll, enabled = active)"))
    }

    @Test fun revisitsRetainSyntheticMessageIdentityAndReadingPosition() {
        val page = source()
        assertTrue("Demo IDs and messages survive leaving a pager page",
            page.contains("playback.sessionId") && page.contains("playback.messages.getOrPut"))
        assertTrue("Returning does not replay message entrance", page.contains("playback.entranceLedger"))
        assertTrue("The preview retains its reading position", page.contains("val demoScroll = playback.scroll"))
        assertTrue("Revisiting the same playback stage must not force the reader back to the bottom",
            page.contains("playback.followedStage == stage") && page.contains("playback.followedStage = stage"))
    }

    @Test fun recommendationsRetainAdaptiveMaterialsTypographyAndLocalOnlyFixtures() {
        val page = source()
        assertTrue(page.contains("LocalTabletDevice.current"))
        assertTrue(page.contains("LocalFreeChatColors.current"))
        assertTrue(page.contains("frostedCard(null, colors, advancedMaterial = false, RoundedCornerShape(22.dp))"))
        assertTrue(page.contains("MaterialTheme.typography.bodyLarge"))
        assertTrue(page.contains("LocalDensity.current.fontScale"))
        assertTrue(page.contains("navigationBarsPadding()"))
        val landscape = page.substringAfter("if (split) {").substringBefore("} else {")
        assertTrue("The tablet keeps preview left and introduction/action right",
            landscape.indexOf("demonstration()") in 0 until landscape.indexOf("introduction(false)"))
        for (sideEffect in listOf("LocalStore", "CharacterLibraryStore", "ChatViewModel", "OkHttp", "openRawResource", "outputStream(")) {
            assertFalse("Preview fixtures must not read/write user content or call models: $sideEffect", page.contains(sideEffect))
        }
    }
}
