package com.freechat.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beta 1.1.31 契约：①「新功能推荐！」导览页（卡片内真实组件自动演示、横滑不锁定、按钮等前台停留四秒、
 * 功能名蓝字、群聊与故事用虚构消息本地演示不现场调 AI）；②开发者「新功能推荐弹窗测试」开=每次弹、
 * 关=更新后首次进入弹一次（版本号记账）；隐私政策改动已全量回退（不再有隐私入口/勾选）。
 */
class Beta1131Test {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun featureRecDemoUsesRealComponentsWithForegroundDwellAndFreeSwiping() {
        val rec = source("ui/components/FeatureRecPage.kt")
        val bubble = source("ui/components/ChatBubble.kt")
        assertTrue("群聊与故事复用真实消息组件和思考球",
            rec.contains("ChatBubble(") && rec.contains("groupChat = demoGroup") &&
                rec.contains("textFlow = story") && bubble.contains("SiriOrb("))
        assertTrue("原生横向分页始终允许往返，不再用遮挡吞掉手势",
            rec.contains("HorizontalPager(") && !rec.contains("detectTapGestures") &&
                !rec.contains("userScrollEnabled = false"))
        assertTrue("按钮按四秒前台停留开放，不等待完整演示结束",
            rec.contains("FEATURE_CTA_WAIT_MS = 4_000L") && rec.contains("dwellMs >= FEATURE_CTA_WAIT_MS") &&
                rec.contains("enabled = canAdvance") && !rec.contains("enabled = stage >= DEMO_LAST_STAGE"))
        assertTrue("虚构角色与消息仅在本地构造，不再读场景图资源或调用模型",
            rec.contains("CharacterPersona(") && rec.contains("Message(") &&
                !rec.contains("R.drawable.feature_rec_scene") && !rec.contains("ChatViewModel") &&
                !rec.contains("OkHttp"))
        assertTrue("两页各自保留演示进度（停留解锁 2026-10-28 起为全局一次钟），退到后台或滑动中暂停",
            rec.contains("List(FEATURE_REC_IDS.size) { FeatureRecPlaybackState() }") &&
                rec.contains("playback.stageElapsedMs") && !rec.contains("playback.dwellMs") &&
                rec.contains("pagerState.settledPage.takeUnless { pagerState.isScrollInProgress }") &&
                rec.contains("repeatOnLifecycle(Lifecycle.State.RESUMED)"))
    }

    @Test fun featureNamesAreHighlightedInBlue() {
        val rec = source("ui/components/FeatureRecPage.kt")
        assertTrue("当前推荐的功能名蓝色突出（2026-10-28：名加重蓝、简介拆行小字弱化）",
            rec.contains("s.multiCharacterGroup else s.multiCharacterStory") && rec.contains("color = blue"))
        assertTrue("旧拼串简介已退役", !rec.contains("SpanStyle(color = blue"))
    }

    @Test fun featureRecGateIsOncePerVersionUnlessDevTestIsOn() {
        val main = source("MainActivity.kt")
        assertTrue("开发者测试开关直通", main.contains("DevMode.testFeatureRec(this@MainActivity)"))
        assertTrue("普通用户按版本记账只弹一次", main.contains("prefs.getInt(\"last_shown\", 0) < com.freechat.BuildConfig.VERSION_CODE"))
        assertTrue("与启动提醒互斥", main.contains("noticePicked != null || featureRecVisible"))
    }

    @Test fun privacyPolicyChangesAreFullyReverted() {
        val strings = source("i18n/AppStrings.kt")
        assertTrue("隐私政策正文与字段已删", !strings.contains("privacyPolicySections") && !strings.contains("privacyDocName"))
        val screen = source("ui/screens/AgreementScreen.kt")
        assertTrue("须知勾选回到单文档", !screen.contains("onOpenPrivacy"))
    }
}
