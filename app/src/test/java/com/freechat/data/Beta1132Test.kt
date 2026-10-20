package com.freechat.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beta 1.1.32/1.1.33 契约：①AI 思考灵感球——五颗彩核光粒子（亮核+重光晕+拖尾+前后穿行）
 * 加色环绕，无球缘无圆框（**浅色方片回归钉**：画布级蒙版必须在直角边缘前收到零）；
 * ②球径 30dp 三处调用点一致；③build.gradle.kts 版本号必须随工单升版（1.1.31 交付包里
 * 版本号停在 1.1.30X 的事故钉）；④导览页演示 1.1.33 形态：元素等比缩小（文本不缩）、
 * 底部输入条删除、节奏放慢+对话↔生图长顿、生成槽真管线不裁切。
 */
class Beta1132Test {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun versionMatchesWorkOrder() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue("versionCode 必须随交付升版", gradle.contains("versionCode = 259"))
        assertTrue("versionName 必须是工单版本号（不带前缀，显示层拼 Version——1.2.0 先例）", gradle.contains("versionName = \"1.2.1\""))
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
            "ui/components/ChatBubble.kt",
            "ui/components/FeatureRecPage.kt"
        )) {
            val src = source(path)
            assertTrue("$path 必须用 30dp 球", src.contains("SiriOrb(") && src.contains("size(30.dp)"))
        }
    }

    @Test fun featureRecSmallCardForm() {
        val rec = source("ui/components/FeatureRecPage.kt")
        assertTrue("生成槽走真组件生成→变形管线", rec.contains("fromGeneration = true"))
        assertTrue("AI 正文逐字显现真组件", rec.contains("TypewriterText("))
        assertTrue("成图不裁切（GeneratedImage 内走 Fit，演示不得再 Crop）", !rec.contains("ContentScale.Crop"))
        assertTrue("底部输入条已删除", !rec.contains("FeatureRecComposer"))
        assertTrue("生成槽等比缩小（0.72 行宽）", rec.contains("fillMaxWidth(0.72f)"))
        assertTrue("菜单拟物风卡片+宽度随内容（文字恒一行）",
            rec.contains("width(IntrinsicSize.Max)") && rec.contains("maxLines = 1"))
        assertTrue("菜单仍从左下枢轴长出", rec.contains("menuEnter(TransformOrigin(0f, 1f))"))
        assertTrue("节奏放慢+对话↔生图长顿 2.6s", rec.contains("2600"))
        // 1.2.0 返修：思考球旁带真机思考状态（连接模型→理解内容→拟草回复）1:1 同构
        assertTrue("思考状态 1:1 真机（labels 表+contentReplacement 逐级切换）",
            rec.contains("generationPhaseLabels") && rec.contains("contentReplacement()") &&
                rec.contains("GenerationPhase.UNDERSTANDING") && rec.contains("GenerationPhase.DRAFTING"))
    }
}
