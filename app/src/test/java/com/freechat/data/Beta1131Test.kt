package com.freechat.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beta 1.1.31 契约：①「新功能推荐！」导览页（卡片内真实组件自动演示、播放期锁交互、
 * 模型名蓝字、成图为静态资源不现场调 AI）；②开发者「新功能推荐弹窗测试」开=每次弹、
 * 关=更新后首次进入弹一次（版本号记账）；隐私政策改动已全量回退（不再有隐私入口/勾选）。
 */
class Beta1131Test {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun featureRecDemoUsesRealComponentsAndLockedPlayback() {
        val rec = source("ui/components/FeatureRecPage.kt")
        assertTrue("思考球/生成槽都是真组件", rec.contains("SiriOrb(") && rec.contains("GeneratedImage("))
        assertTrue("播放期锁定交互（不可点改）", rec.contains("awaitPointerEvent("))
        assertTrue("结束后才亮行动按钮", rec.contains("enabled = stage >= 8"))
        assertTrue("成图是打包资源，不现场调 AI", rec.contains("R.drawable.feature_rec_scene"))
        assertTrue("演示节奏（用户消息后立刻出球+球久转+长顿）", rec.contains("intArrayOf(400, 250, 2400"))
    }

    @Test fun modelNameIsHighlightedInBlue() {
        val rec = source("ui/components/FeatureRecPage.kt")
        assertTrue("模型名蓝色突出", rec.contains("ChatGPT-Image-2.5") && rec.contains("SpanStyle(color = blue"))
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
