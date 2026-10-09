package com.freechat.data

import org.junit.Assert.*
import org.junit.Test

class PlainToolProtocolTest {
    private val tools = listOf(mapOf("type" to "function", "function" to mapOf(
        "name" to "generate_image", "description" to "Generate an image", "parameters" to mapOf("type" to "object"))))
    private val payload = "<freechat_tool>{\"name\":\"generate_image\",\"arguments\":{\"prompt\":\"一只白猫\"}}</freechat_tool>"

    @Test fun ordinaryTextCanRequestAConfiguredToolWithoutNativeFunctionCalling() {
        val result = PlainToolProtocol.parse(payload, tools)
        assertNotNull(result)
        assertEquals("generate_image", result!!.name)
        val args = result.arguments
        assertEquals("一只白猫", args["prompt"].asString)
        assertTrue(PlainToolProtocol.instruction(tools).toString().contains("generate_image"))
    }

    @Test fun partialToolCommandsNeverFlashInTheUserVisibleReply() {
        for (end in 1..payload.length) assertEquals("", PlainToolProtocol.visibleContent(payload.take(end)))
        assertEquals("你好", PlainToolProtocol.visibleContent("你好"))
        assertEquals("<b>例子</b>", PlainToolProtocol.visibleContent("<b>例子</b>"))
    }

    @Test fun ordinaryQuotesUnknownToolsAndMalformedPayloadsCannotExecute() {
        assertNull(PlainToolProtocol.parse("代码例子：$payload", tools))
        assertNull(PlainToolProtocol.parse(payload.replace("generate_image", "delete_conversation"), tools))
        assertNull(PlainToolProtocol.parse("<freechat_tool>{}</freechat_tool>", tools))
        assertNull(PlainToolProtocol.parse("<freechat_tool>{\"name\":\"generate_image\",\"arguments\":\"bad\"}</freechat_tool>", tools))
    }

    @Test fun onlyActualToolProtocolRejectionsPermitAFallbackRetry() {
        assertEquals(true, PlainToolProtocol.rejectedNativeTools("API error 400: tools is not supported"))
        assertEquals(true, PlainToolProtocol.rejectedNativeTools("HTTP 422: unknown parameter tool_choice"))
        assertEquals(false, PlainToolProtocol.rejectedNativeTools("API error 401: invalid key for tools"))
        assertEquals(false, PlainToolProtocol.rejectedNativeTools("API error 429: too many tool requests"))
        assertEquals(false, PlainToolProtocol.rejectedNativeTools("connection timed out"))
    }

    @Test fun theFallbackAlsoRemovesUnsupportedNativeToolMessageRoles() {
        val messages = listOf(mapOf<String, Any?>("role" to "assistant", "tool_calls" to listOf(
            mapOf("function" to mapOf("name" to "generate_image", "arguments" to "{\"prompt\":\"cat\"}")))),
            mapOf("role" to "tool", "tool_call_id" to "call1", "content" to "generated"))
        val compatible = PlainToolProtocol.compatibleMessages(messages)
        assertFalse(compatible.any { it["role"] == "tool" || it.containsKey("tool_calls") })
        assertNotNull(PlainToolProtocol.parse(compatible.first()["content"].toString(), tools))
        assertTrue(compatible.last()["content"].toString().contains("generated"))
    }

    @Test fun aSingleFencedToolCommandIsCompatibleButNeverVisibleWhileStreaming() {
        val fenced = "```json\n$payload\n```"
        assertNotNull(PlainToolProtocol.parse(fenced, tools))
        for (end in 1..fenced.length) assertEquals("", PlainToolProtocol.visibleContent(fenced.take(end)))
    }
}
