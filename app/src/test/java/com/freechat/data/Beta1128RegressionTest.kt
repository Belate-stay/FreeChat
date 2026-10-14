package com.freechat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beta 1.1.26.8 契约：①断触修复+阻尼铺开（更新日志/用户协议除外）；
 * ②非高级材质侧滑页实体背景板内容隔离；③删空消息不删对话（保留新规则/角色卡）。
 */
class Beta1128RegressionTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun jellyCoversNamedPagesButNotReadingPages() {
        // 阻尼铺开点名页：AI语音/语音调试、高级自定义语音、模型编辑、账户
        for (p in listOf(
            "ui/screens/VoiceDebugScreen.kt",
            "ui/screens/MiMoVoiceSettingsPage.kt",
            "ui/screens/ModelEditorScreen.kt",
            "ui/screens/AccountScreen.kt"
        )) {
            assertTrue("$p 必须挂 jellyOverscroll", source(p).contains("jellyOverscroll()"))
        }
        // 更新日志/用户协议是阅读页，用户点名不做
        assertFalse("更新日志不做阻尼", source("ui/screens/ChangelogScreen.kt").contains("jellyOverscroll"))
        assertFalse("用户协议不做阻尼", source("ui/screens/AgreementScreen.kt").contains("jellyOverscroll"))
    }

    @Test fun drawerIsSolidWhenAdvancedMaterialOff() {
        val main = source("MainActivity.kt")
        val drawer = source("ui/components/DrawerContent.kt")
        assertTrue("抽屉面板壳非高级材质无条件实体（炫彩下也不用 veil）",
            main.contains("背景板") && main.contains(".background(colors.Surface)"))
        assertFalse("旧 drawerVeil 透明罩已删", main.contains("liquidColors.drawerVeil"))
        assertTrue("B 支整列实体底", drawer.contains(".fillMaxSize()\n                .background(colors.Surface)"))
        assertTrue("头部实体板 + zIndex 内容隔离",
            drawer.contains(".zIndex(1f)") && drawer.contains(".background(colors.Surface)"))
        // 1.1.29.5 用户返修拍板：底部三选项撤独立色块（色差难看），隔离改由整行 Surface 实体底承担
        assertFalse("底部按钮不再带独立色块", drawer.contains(".background(colors.SurfaceVariant, RoundedCornerShape(10.dp))"))
        assertTrue("FreeChat 标题垫实体板（非高级材质）",
            main.contains("titlePlateAlpha") && main.contains("drawBehind"))
    }

    @Test fun emptyingMessagesNeverDeletesTheConversation() {
        val vm = source("viewmodel/ChatViewModel.kt")
        assertFalse("旧显示过滤器（0消息+台账=藏掉）已删",
            vm.contains("rows.filter { it.messageCount > 0 || it.mode == ChatMode.COMPANION || it.deletedMessageIds.isEmpty() }"))
        assertTrue("对话流原样透传", vm.contains("val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()"))
        val chat = source("ui/screens/ChatScreen.kt")
        assertTrue("已存在空对话显示空聊天（不顶替首页问候语）",
            chat.contains("messages.isEmpty() && currentConvId == null && !companionMode"))
        assertTrue("空对话也能进新规则/角色卡", chat.contains("messages.isNotEmpty() || companionMode || currentConvId != null"))
    }
}
