package com.freechat.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beta 1.0.95 回归（建议与反馈接真后端的文案面）：
 * 提交三态（发送中/已收到/失败保留草稿）+ 「我的反馈」入口 + 诚实口径（明说会发送）。
 * 2026-10-15 拍板改口径：用户可删自己的反馈；clientId 走 X-Client-Id 头。
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
        // 入口标题不许带编辑/删除字眼（删除是列表行内操作，不在标题）
        assertTrue(!zh.feedbackMy.contains("编辑") && !zh.feedbackMy.contains("删除"))
    }

    @Test
    fun myFeedbackSupportsDeleteAndClientIdHeader() {
        // 2026-10-15：clientId 走 X-Client-Id 头（查询串会进 nginx 日志）；删除 404 当已删（幂等）
        val api = java.io.File("src/main/java/com/freechat/sync/ApiClient.kt").readText()
        assertTrue("mine 不再带查询参数", api.contains("\"GET\", \"/feedback/mine\"") && !api.contains("/feedback/mine?clientId="))
        assertTrue("clientId 走请求头", api.contains("builder.header(\"X-Client-Id\", clientId)"))
        assertTrue("删自己的反馈：404 幂等", api.contains("suspend fun deleteFeedback") && api.contains("if (e.status != 404) throw e"))
        val mine = java.io.File("src/main/java/com/freechat/ui/screens/MyFeedbackScreen.kt").readText()
        assertTrue("行尾删除 + 乐观移除", mine.contains("DeleteOutline") && mine.contains("ApiClient.deleteFeedback"))
    }
}
