package com.freechat.ui.components

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source wiring contracts only; these do not replace Compose interaction or visual checks. */
class ChatUsability1215SourceWiringTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()
    private fun chat() = source("ui/screens/ChatScreen.kt")
    private fun plusMenu() = chat().substringAfter("// ===== \"+\" 附件菜单")
        .substringBefore("// ===== 长按输入框")

    @Test fun everyChatHeaderOmitsNewChatWithoutRemovingDrawerCreation() {
        val header = chat().substringAfter("// ===== 悬浮标题栏")
            .substringBefore("// ===== 底部渐进模糊")
        assertFalse("The common phone/tablet and standard/companion header must omit new chat",
            header.contains("s.newChat") || header.contains("Icons.Outlined.Create"))
        val drawer = source("ui/components/DrawerContent.kt")
        assertTrue("Both drawer layouts retain the existing creation action",
            Regex("NewChatButton\\(onNewChat, onNewChatRect").findAll(drawer).count() == 2)
        val activity = source("MainActivity.kt")
        assertTrue("The drawer still opens the mode picker", activity.contains("onNewChatRect ="))
    }

    @Test fun headerOnlyNewChatRevealWiringIsRemoved() {
        val screen = chat()
        listOf("onNewChatReveal", "chatNewChatRect", "revealTrigger", "revealPhase", "revealAnim")
            .forEach { assertFalse("Header-only state must not survive: $it", screen.contains(it)) }
        val activity = source("MainActivity.kt")
        assertFalse("The removed header cannot retain a trigger in its caller", activity.contains("newChatRevealTrigger"))
        val chatCall = activity.substringAfter("Screen.CHAT -> ChatScreen(")
            .substringBefore("onOpenCharacterSetup =")
        assertFalse("The removed header callback cannot survive in its caller", chatCall.contains("onNewChat ="))
    }

    @Test fun onlyCompactPlusMenuAddsBothFeatureControls() {
        val menu = plusMenu()
        val compact = menu.substringAfter("if (inputStyle == InputStyle.COMPACT) {", "")
        assertTrue("Compact + must expose deep thinking", compact.contains("label = s.deepThinkingMode"))
        assertTrue("Compact + must expose web search", compact.contains("label = s.webSearch"))
        assertEquals("Complete keeps its existing bottom-row controls without duplicate menu rows",
            2, Regex("InputStateMenuItem\\(").findAll(menu).count())
        val otherMenu = chat().substringAfter("// ===== 长按输入框")
            .substringBefore("// 改写提示词")
        assertFalse("These controls belong in +, not the paste/fullscreen menu", otherMenu.contains("InputStateMenuItem("))
    }

    @Test fun compactAndCompleteReuseTheSameFeatureStateAndCallbacks() {
        val complete = chat().substringAfter("ChatInput(").substringBefore("ChatQuickLocate(")
        val menu = plusMenu()
        fun argument(block: String, name: String): String? =
            Regex("\\b$name\\s*=\\s*([A-Za-z][A-Za-z0-9_]*)\\s*[,\\n]")
                .find(block)?.groupValues?.get(1)
        fun menuRow(label: String) = Regex("InputStateMenuItem\\(\\s*label = s\\.$label,([\\s\\S]*?)\\n\\s*\\)")
            .find(menu)?.groupValues?.get(1) ?: ""
        fun same(label: String, completeArg: String, menuArg: String) {
            val shared = argument(complete, completeArg)
            assertNotNull("$completeArg must be a shared value/callback", shared)
            assertEquals("$label must reuse $completeArg", shared, argument(menuRow(label), menuArg))
        }
        same("deepThinkingMode", "deepThinkOn", "checked")
        same("deepThinkingMode", "deepThinkEnabled", "enabled")
        same("deepThinkingMode", "onToggleDeepThink", "onToggle")
        same("deepThinkingMode", "onLongPressDeepThink", "onLongPress")
        same("webSearch", "webSearchOn", "checked")
        same("webSearch", "onToggleWebSearch", "onToggle")
        same("webSearch", "onLongPressWebSearch", "onLongPress")
    }

    @Test fun compactControlsPreserveSettingScopeAndCompanionSaveEntryPoint() {
        val screen = chat()
        val shared = screen.substringAfter("val onToggleWebSearch: () -> Unit =", "")
            .substringBefore("// 内置对话")
        assertTrue("Existing conversations write their own override", shared.contains("updatePerConvSettings(id, per.copy(enableWebSearch = !effWebSearch))"))
        assertTrue("The empty home page writes its pending conversation draft", shared.contains("togglePendingWebSearch()"))
        assertFalse("A shortcut must not update the global switch", shared.contains("setEnableWebSearch("))
        assertTrue("Deep thinking keeps the VM's existing per-mode save behavior",
            shared.contains("val onToggleDeepThink: () -> Unit = { viewModel.toggleDeepThinking() }"))
        assertTrue("Capability uses the effective conversation model",
            shared.contains("val deepThinkEnabled = viewModel.effectiveLangModel().supportsDeepThinking"))
        assertTrue("Reasoning long press keeps the companion settings route",
            shared.contains("else if (companionMode) onOpenCharacterSetup else onOpenNewRules"))
        assertTrue("Long press remains unavailable while editing or before a conversation exists",
            shared.contains("!navigationEnabled || currentConvId == null || editingMessageId != null"))
    }

    @Test fun menuTogglesExposeStateAndKeepLargeTextInsideThePane() {
        val input = source("ui/components/ChatInput.kt")
        val row = input.substringAfter("internal fun InputStateMenuItem(", "")
            .substringBefore("/** Fixed controls")
        assertTrue("TalkBack needs the toggle value, not color alone", row.contains("toggleableState ="))
        assertTrue("TalkBack needs a native checkbox role", row.contains("role = Role.Checkbox"))
        assertTrue("Unsupported capabilities must disable interaction", row.contains("enabled = enabled"))
        assertTrue("Menu rows retain a 48dp target", row.contains("heightIn(min = 48.dp)"))
        assertTrue("Long press invokes the same settings callback", row.contains("onLongPress?.let"))
        assertTrue("Rows must dismiss after activating", row.contains("onDismiss()"))
        assertFalse("Long translated labels and large text can wrap", row.contains("maxLines = 1"))
        val menu = plusMenu()
        assertTrue("Additional rows must remain scrollable in a short pane", menu.contains("verticalScroll(rememberScrollState())"))
        assertTrue("The menu must use available pane height", menu.contains("heightIn(max = plusMenuMaxHeight)"))
        assertTrue("Existing material and motion must remain", menu.contains("frostedGlass(hazeState") && menu.contains("FreeChatAnimation.menuEnter"))
    }
}
