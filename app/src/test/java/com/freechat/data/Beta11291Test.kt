package com.freechat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beta 1.1.29.1 工单契约（补做批）：①重生成/重回复删除→粒子粉碎（用户主动删除不动）；
 * ③自动隐藏下重写提示词弹输入框；④逐字提速；⑤阴影常驻（1.1.29 ② 钉在 DefaultsAndModels1128Test）。
 * （②⑥等非开源组件的内部契约在 test/dev/ 里，不随 push 上 GitHub。）
 */
class Beta11291Test {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun regenerateFamilyDeletesShatterUsersDoNot() {
        val vm = source("viewmodel/ChatViewModel.kt")
        val screen = source("ui/screens/ChatScreen.kt")
        assertTrue("重生成/重回复/图片重生成走 shatterThenDelete",
            vm.contains("private fun shatterThenDelete") &&
                vm.split("shatterThenDelete(").size - 1 >= 5) // 定义+4 触发点
        assertTrue("粉碎信号带被删 id", vm.contains("_shatterRequests.tryEmit"))
        assertTrue("用户主动删除不走粉碎（原动画保留）",
            vm.contains("deleteMessageIds(it, setOf(msgs[index].id))") &&
                vm.contains("deleteMessageIds(it, valid.mapTo"))
        assertTrue("粒子层按消息最后一帧矩形起爆",
            screen.contains("ShatterBurst(") && screen.contains("msgRects[item.msg.id] = c.boundsInRoot()"))
        assertTrue("共用「从夯到拉」重力粒子引擎", screen.contains("com.freechat.ui.components.GravityParticles"))
    }

    @Test fun rewritePromptAlwaysPopsInputAndTypingIsFaster() {
        val screen = source("ui/screens/ChatScreen.kt")
        assertTrue("进改写把输入框拉回来（自动隐藏态也认）",
            screen.contains("|| editingMessageId != null) inputHidden = false"))
        assertTrue("改写期间滚动不再把输入框藏回去",
            screen.contains("quotedMessage == null && editingMessageId == null)"))
        val tw = source("ui/components/TypewriterReveal.kt")
        assertTrue("逐字动画提速到 50 字/秒", tw.contains("CHARS_PER_SECOND = 50"))
    }
}
