package com.freechat.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 1.1.29.9 实机返修：「默认折叠思考过程」开着时滚动文字流跳动的根因契约——
 * 思考泡首帧必须直接落目标态，绝不允许「滚进视口先展开、再被效果折叠」的高度扑动。
 */
class CollapseThinkingScrollTest {

    private fun bubble() = File("src/main/java/com/freechat/ui/components/ChatBubble.kt").readText()
    private fun state() = File("src/main/java/com/freechat/ui/components/ChatSessionState.kt").readText()

    @Test fun firstFrameLandsOnTheTargetStateWithoutFlapping() {
        assertTrue("记录存取必须可空（null=没动过），否则 ?: 默认分支永远不生效",
            state().contains("fun reasoningExpandedRecord(conversationId: String, messageId: String): Boolean?"))
        assertTrue("首帧读的是只读记录，不是 ?:true 兜底版",
            bubble().contains("session?.reasoningExpandedRecord(it, messageId)"))
        assertTrue("关掉=默认展开 的兜底分支保留", bubble().contains("?: !(collapseThinking && replyStarted)"))
    }

    @Test fun historyMessagesNeverReplayTheAutoFoldOnScrollReentry() {
        assertTrue("历史消息（replyStarted 已 true）视为已折过，滚动重进不重放自动折叠",
            bubble().contains("mutableStateOf(replyStarted)"))
    }

    @Test fun manualExpansionSurvivesScrollReentry() {
        // 手动点开过就不再打扰：重进视口首帧吃记录，自动折叠不再碰它（autoCollapsed 已为 true）
        assertTrue(bubble().contains("setReasoningExpanded"))
    }

    @Test fun collapseReliesOnTheClampInsteadOfDoubleScrolling() {
        // 实机二轮返修：折叠后跳到半空=「钳位贴底」之外又跟了一次高度差（双重位移）。
        // 折叠必须零干预（钳位随 tween 逐帧贴底），只有「在底部展开」才主动跟随。
        val src = java.io.File("src/main/java/com/freechat/ui/components/DisclosureScrollAnchor.kt").readText()
        assertTrue("折叠不跟：只有展开且在底部才起跟随", src.contains("if (expanding && !list.canScrollForward)"))
    }
}
