package com.freechat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beta 1.1.29.5 契约：①关于页滚动视口全高（日志进标题模糊带/底部无空白色块）
 * ②更新区流式排布+日志淡入展开过渡 ③侧滑底部三板拉开间距（防砂漏缺口）
 * ④全站触点波纹（输入框豁免、标题栏按钮只波不缩）。
 */
class Beta11295Test {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun aboutPageScrollsFullScreenSoLogEntersTopBlur() {
        val settings = source("ui/screens/SettingsScreen.kt")
        assertTrue("外层对关于页跳过上下留白（A/C：滚动视口必须全高）",
            settings.contains("top = if (screen == SubScreen.ABOUT_FREECHAT) 0.dp") &&
                settings.contains("SubScreen.ABOUT_FREECHAT -> 0.dp"))
        assertTrue("上下留白收进页内滚动内容（padding 在 verticalScroll 之后=可滚走）",
            settings.contains("calculateTopPadding()) + 56.dp") && settings.contains("bottom = 32.dp"))
    }

    @Test fun updateAreaFlowsAndFadesInsteadOfFloatingClamp() {
        val settings = source("ui/screens/SettingsScreen.kt")
        assertFalse("浮钮钳位机构已拆（压字/瞬移根子）",
            settings.contains("optionsTopPx") || settings.contains("悬浮「获取更新」"))
        assertTrue("按钮与选项卡随日志展开同曲线下移（三段间距同 tween；1.1.29.8 提速长到 520ms）",
            settings.contains("val moveSpec = tween<Dp>(520, easing = FreeChatAnimation.arrivalEase)") &&
                settings.contains("animateDpAsState") && settings.contains("aboutGapBottom"))
        assertTrue("日志整体淡入+纵向展开",
            settings.contains("expandVertically(tween(520, easing = FreeChatAnimation.arrivalEase), expandFrom = Alignment.Top)") &&
                settings.contains("fadeIn(tween(450, easing = FreeChatAnimation.arrivalEase))"))
        assertTrue("检查更新↔获取更新同一颗按钮就地换字",
            settings.contains("if (checking) s.updateChecking else if (available == null) s.checkUpdate else s.updateGet"))
    }

    @Test fun drawerBottomButtonsSitDirectlyOnBackdrop() {
        val drawer = source("ui/components/DrawerContent.kt")
        // 用户拍板（返修）：三选项不要独立色块，直接落在整行实体背景板上
        assertFalse("三选项不再带 SurfaceVariant 独立色块（色差/接缝难看）",
            drawer.contains(".background(colors.SurfaceVariant, RoundedCornerShape(10.dp))"))
        assertTrue("整行 Surface 实体底保留（列表果冻平移仍被兜住）",
            drawer.contains(".background(colors.Surface)"))
    }

    @Test fun touchRippleIsGlobalWithInputBoxExempt() {
        val motion = source("ui/animation/FreeChatMotion.kt")
        val theme = source("ui/theme/Theme.kt")
        val chat = source("ui/screens/ChatScreen.kt")
        val header = source("ui/components/HeaderIconButton.kt")
        assertTrue("PressNode 从触控点画一层淡波，限定在组件边界内",
            motion.contains("Brush.radialGradient") && motion.contains("clipRect") &&
                motion.contains("waveOrigin = interaction.pressPosition"))
        assertTrue("波纹/缩放独立开关（自带缩放的按钮只波不缩）",
            motion.contains("val ripple: Boolean = true") && motion.contains("val pressScale: Boolean = true"))
        assertTrue("波纹墨色随主题走中性 TextPrimary", theme.contains("MotionIndication(motionEnabled, colors.Primary, colors.TextPrimary)"))
        assertTrue("输入框整棵子树豁免波纹",
            chat.contains("rememberMotionIndication(ripple = false)"))
        assertTrue("标题栏按钮（镂空/卡片）只波不缩",
            header.contains("rememberMotionIndication(pressScale = false)"))
    }
}
