package com.freechat.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beta 1.0.95 回归（建议与反馈接真后端的文案面）：
 * 提交三态（发送中/已收到/失败保留草稿）+ 「我的反馈」只读入口 + 诚实口径（明说会发送）。
 */
class Beta95RegressionTest {

    @Test
    fun feedbackSendStatesExistInAllLocales() {
        listOf("zh-CN", "zh-TW", "en").forEach { locale ->
            val s = com.freechat.i18n.buildStrings(locale)
            assertTrue("$locale sending", s.feedbackSending.isNotBlank())
            assertTrue("$locale sent", s.feedbackSent.isNotBlank())
            assertTrue("$locale sendFailed", s.feedbackSendFailed.isNotBlank())
            assertTrue("$locale myFeedback", s.feedbackMy.isNotBlank())
            assertTrue("$locale myFeedbackEmpty", s.feedbackMyEmpty.isNotBlank())
        }
    }

    @Test
    fun feedbackCopyMatchesRealBackendBehavior() {
        // 文案=行为的镜像：真发送就必须写明发送（含设备排查信息），失败必须写明草稿保留
        val zh = com.freechat.i18n.buildStrings("zh-CN")
        assertTrue(zh.feedbackDescription.contains("发送给开发者"))
        assertTrue(zh.feedbackDescription.contains("设备"))
        assertTrue(zh.feedbackSendFailed.contains("保留"))
        // 「我的反馈」是只读视图（拍板「无法编辑无法删除」）——入口标题不许带编辑/删除字眼
        assertTrue(!zh.feedbackMy.contains("编辑") && !zh.feedbackMy.contains("删除"))
    }
}
