package com.freechat.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beta 1.1.32/1.1.33 契约：①AI 思考灵感球——五颗彩核光粒子（亮核+重光晕+拖尾+前后穿行）
 * 加色环绕，无球缘无圆框（**浅色方片回归钉**：画布级蒙版必须在直角边缘前收到零）；
 * ②球径 30dp 一致（新导览经 ChatBubble 复用）；③build.gradle.kts 版本号必须随工单升版（1.1.31 交付包里
 * 版本号停在 1.1.30X 的事故钉）；④导览页保持小卡可读、真实消息动画和思考状态，
 * 现推荐群聊与多角色故事，故事正文经 ChatBubble 复用真实逐字显现。
 */
class Beta1132Test {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun versionMatchesWorkOrder() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue("versionCode 必须随交付升版", gradle.contains("versionCode = 280"))
        assertTrue("versionName 必须是工单版本号；正式版不带前缀、显示层拼 Version——1.2.0 金标准", gradle.contains("versionName = \"1.2.3\""))
    }

    @Test fun releaseChangelogIsUserAuthored() {
        val log = File("../freechat-core/src/main/kotlin/com/freechat/model/Changelog.kt").readText()
        assertTrue("Version 1.2.0 正式日志已入库（用户原文）",
            log.contains("Version 1.2.0") && log.contains("图片预设") && log.contains("freechater.com"))
    }

    @Test fun orbRendersFiveColoredParticlesWithoutSquareFrame() {
        val shader = source("ui/components/SiriOrbShader.kt")
        assertTrue("五颗粒子色 uniform 全在", (0..4).all { shader.contains("u_pc$it") })
        assertTrue("每粒子带双点拖尾", (0..4).all { shader.contains("u_tr$it") })
        assertTrue("底色压暗让位粒子", shader.contains("0.10 + 0.20 * nebula"))
        assertTrue("发光体合成（粒子是光的一部分）", shader.contains("rgb + pts"))
        assertTrue("边缘羽化无轮廓", shader.contains("feather = exp(-r * r"))
        // 「浅色方片」回归钉：单位半径必须小于画布半宽 + 蒙版在画布边缘前收到零
        assertTrue("单位半径给粒子/光晕留羽化空间", shader.contains("* 0.34"))
        assertTrue("画布级羽化蒙版", shader.contains("mask = 1.0 - smoothstep"))
        assertTrue("发光必须乘蒙版", shader.contains("* half(mask)"))
    }

    @Test fun orbIsSizedForVisibleParticles() {
        for (path in listOf(
            "ui/components/TypingIndicator.kt",
            "ui/components/ChatBubble.kt"
        )) {
            val src = source(path)
            assertTrue("$path 必须用 30dp 球", src.contains("SiriOrb(") && src.contains("size(30.dp)"))
        }
        val rec = source("ui/components/FeatureRecPage.kt")
        assertTrue("导览通过真实消息组件使用相同思考球，不另造一份",
            rec.contains("ChatBubble(") && rec.contains("isStreaming = pending") &&
                rec.contains("generationLabel = if (pending)"))
    }

    @Test fun featureRecSmallCardForm() {
        val rec = source("ui/components/FeatureRecPage.kt")
        val bubble = source("ui/components/ChatBubble.kt")
        assertTrue("群聊消息与故事正文走真实组件",
            rec.contains("ChatBubble(") && rec.contains("groupChat = demoGroup") && rec.contains("textFlow = story"))
        assertTrue("故事正文经真实 ChatBubble 逐字显现并强调台词",
            rec.contains("emphasizeDialogue = story") && bubble.contains("TypewriterText(") &&
                bubble.contains("enabled = emphasizeDialogue && !message.isStreaming") && bubble.contains("if (textFlow)"))
        assertTrue("不使用裁切图片代替真实组件", !rec.contains("ContentScale.Crop") && !rec.contains("R.drawable.feature_rec_scene"))
        assertTrue("底部输入条已删除", !rec.contains("FeatureRecComposer"))
        assertTrue("演示小卡保持宽度上限与现有磨砂材质",
            rec.contains("widthIn(max = 420.dp)") &&
                rec.contains("frostedCard(null, colors, advancedMaterial = false, RoundedCornerShape(22.dp))"))
        assertTrue("消息入场仍用共享动画，文本不随卡片缩小",
            rec.contains("rememberMessageEntrance(") && rec.contains("Modifier.messageEntrance(") &&
                rec.contains("typography.bodyLarge") && !rec.contains("scaleX ="))
        assertTrue("原生横滑分页，按钮翻页用共享动效，减动效直接显示完整内容",
            rec.contains("HorizontalPager(") && rec.contains("animationSpec = FreeChatAnimation.pageTween") &&
                rec.contains("if (!motionOn)") && rec.contains("playback.stage = DEMO_LAST_STAGE") &&
                rec.contains("else pagerState.scrollToPage(1)"))
        // 1.2.0 返修：思考球旁带真机思考状态（连接模型→理解内容→拟草回复）1:1 同构
        assertTrue("思考状态 1:1 真机（labels 表+contentReplacement 逐级切换）",
            rec.contains("generationPhaseLabels") && bubble.contains("contentReplacement()") &&
                rec.contains("GenerationPhase.UNDERSTANDING") && rec.contains("GenerationPhase.DRAFTING") &&
                bubble.contains("AnimatedContent(generationLabel") && bubble.contains("SiriOrb(Modifier.size(30.dp)"))
    }
}
