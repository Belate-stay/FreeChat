package com.freechat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beta 1.1.29.8 契约（七点工单）：①关于页吸底悬浮按钮+重进折叠+更新内容竖条
 * ②默认折叠思考过程 ③AI语音页排版下移 ④侧滑竖条首帧不闪跳 ⑤重生成只走粉碎不叠滑出
 * ⑥（实机目检为主）⑦设备型号+IP 回传（非公开代码）。
 */
class Beta11298Test {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun aboutButtonFloatsStickyAndReentryCollapses() {
        val settings = source("ui/screens/SettingsScreen.kt")
        assertTrue("吸底悬浮：锚进视口入坞、出坞吸回屏底同位（min(锚Y, 屏底)）",
            settings.contains("val sticky = kotlin.math.min(btnAnchorY, floatY)"))
        assertTrue("揭示瞬间按钮从原位滑落（520ms 非线性旅程），之后跟滚即跟",
            settings.contains("glide.animateTo(1f, tween(520, easing = FreeChatAnimation.arrivalEase))") &&
                settings.contains("androidx.compose.ui.util.lerp(glideFromY, sticky, glide.value)"))
        assertTrue("检测结果跨页保留，重进走折叠态",
            settings.contains("AboutUpdateStore") && settings.contains("val reentry = updateNotes != null && !sessionReveal"))
        assertTrue("折叠态：按钮固定版本号下方 + 下箭头子菜单展开文字流",
            settings.contains("streamExpanded = !streamExpanded") && settings.contains("Icons.Filled.KeyboardArrowDown"))
    }

    @Test fun updateStreamMatchesChangelogStyle() {
        val settings = source("ui/screens/SettingsScreen.kt")
        val changelog = source("ui/screens/ChangelogScreen.kt")
        assertTrue("「更新内容」主题色小标题 + 各大项复用 ChangelogSection 竖条版式",
            settings.contains("s.updateContentTitle") && settings.contains("ChangelogSection(title, items, colors.Primary, colors)"))
        assertTrue("ChangelogSection 已提为 internal 供关于页复用", changelog.contains("internal fun ChangelogSection"))
        assertTrue("解析 Update.md：## 分节 / - 条目", settings.contains("line.startsWith(\"## \")") && settings.contains("line.startsWith(\"- \")"))
    }

    @Test fun collapseThinkingSettingAutoFoldsOnReply() {
        val repo = source("data/SettingsRepository.kt")
        val settings = source("ui/screens/SettingsScreen.kt")
        val bubble = source("ui/components/ChatBubble.kt")
        assertTrue("开关默认开启、仅显示思考过程开启后可见",
            repo.contains("prefs[KEY_COLLAPSE_THINKING] ?: true") && settings.contains("if (showThinking)"))
        assertTrue("回复开流（或输出完毕）自动折起，只折一次",
            bubble.contains("replyStarted = message.content.orEmpty().isNotBlank() || !message.isStreaming") &&
                bubble.contains("autoCollapsed"))
        assertTrue("关掉=修改前逻辑（默认展开）", bubble.contains("?: !(collapseThinking && replyStarted)"))
    }

    @Test fun voicePageTopClearsBlurAndIndicatorFirstFrameIsFrameZero() {
        val voice = source("ui/screens/MiMoVoiceSettingsPage.kt")
        val drawer = source("ui/components/DrawerContent.kt")
        assertTrue("③语音页整体下移出模糊渐隐带（首屏与二级页同吃）",
            voice.contains("HazeSpec.topContentPaddingDp(statusBarHeight, 32.dp)"))
        assertTrue("④未装帧按出现第 0 帧画（左缘圆点），不再闪终点位",
            drawer.contains("isAppearFlight() -> target") &&
                drawer.contains("isAppearFlight() -> -dotPx * 0.5f") &&
                drawer.contains("if (installedId[0] == null && isAppearFlight()) 0f"))
    }

    @Test fun regenerateShattersWithoutSlide() {
        val chat = source("ui/screens/ChatScreen.kt")
        // ⑤重生成不再叠播方向滑出：playExitThen 调用只剩用户删除一处（定义行用 lookbehind 排除）
        val exits = Regex("(?<!fun )playExitThen\\(").findAll(chat).count()
        assertTrue("⑤playExitThen 调用只剩用户删除一处（重生成/重回复走粒子粉碎）", exits == 1)
        assertTrue("⑤粉碎仍由 VM 侧 shatterThenDelete 承担",
            source("viewmodel/ChatViewModel.kt").contains("shatterThenDelete(convId, plan.removedReplies"))
        // ⑦内部契约钉在 test/dev/DevSourceContractsTest
    }
}
