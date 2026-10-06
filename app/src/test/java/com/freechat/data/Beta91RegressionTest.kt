package com.freechat.data

import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.util.ShareLinkPayload
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Beta 1.0.91 回归：
 * ① 预览态「未输入」占位（文案存在性）；
 * ③ 在线网页链接分享的载荷守卫与形状（与 FreeChatServer/src/share.js 同口径）。
 */
class Beta91RegressionTest {

    private fun msg(role: Role = Role.USER, content: String = "你好", ts: Long = 1728000000000L, id: String = "m") =
        Message(id = id, role = role, content = content, timestamp = ts)

    @Test
    fun notEnteredPlaceholderExistsInAllLocales() {
        // 预览态空项占位三语齐全——缺一语言就会退化成空白或英文串
        assertEquals("未输入", com.freechat.i18n.buildStrings("zh-CN").notEntered)
        assertEquals("未輸入", com.freechat.i18n.buildStrings("zh-TW").notEntered)
        assertEquals("Not entered", com.freechat.i18n.buildStrings("en").notEntered)
    }

    @Test
    fun linkShareGuardsMatchServerLimits() = runBlocking {
        // 超条数
        val tooMany = ShareLinkPayload.build("t", (1..21).map { msg(id = "m$it") })
        assertTrue(tooMany is ShareLinkPayload.Build.TooManyMessages)
        // 超字数（3 万字）
        val tooLong = ShareLinkPayload.build("t", listOf(msg(content = "字".repeat(30001))))
        assertTrue(tooLong is ShareLinkPayload.Build.TooManyChars)
        // 20 条 + 3 万字边界内放行
        val ok = ShareLinkPayload.build("t", (1..20).map { msg(id = "m$it", content = "字".repeat(1500)) })
        assertTrue(ok is ShareLinkPayload.Build.Ok)
    }

    @Test
    fun linkSharePayloadShapeMatchesContract() = runBlocking {
        val built = ShareLinkPayload.build(
            title = "T".repeat(250),
            messages = listOf(
                msg(Role.USER, "问", 1000L, "a"),
                msg(Role.ASSISTANT, "答", 2000L, "b")
            )
        )
        val body = (built as ShareLinkPayload.Build.Ok).body
        // 标题截断 200
        assertEquals(200, body.get("title").asString.length)
        val messages: JsonArray = body.getAsJsonArray("messages")
        assertEquals(2, messages.size())
        val m0: JsonObject = messages[0].asJsonObject
        val m1: JsonObject = messages[1].asJsonObject
        assertEquals("user", m0.get("role").asString)
        assertEquals("assistant", m1.get("role").asString)
        assertEquals("问", m0.get("content").asString)
        assertEquals("答", m1.get("content").asString)
        assertEquals(1000L, m0.get("timestamp").asLong)
        assertEquals(2000L, m1.get("timestamp").asLong)
        // 无图消息不产生图片条目
        assertEquals(0, body.getAsJsonArray("images").size())
    }
}
