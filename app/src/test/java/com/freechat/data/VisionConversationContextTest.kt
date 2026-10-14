package com.freechat.data

import org.junit.Assert.*
import org.junit.Test

class VisionConversationContextTest {
    @Test fun multimodalTurnKeepsRulesMemoryAndEarlierConversation() {
        val context = listOf(
            mapOf<String, Any?>("role" to "system", "content" to "PERSONA RULE MEMORY"),
            mapOf("role" to "user", "content" to "我的名字是小林"),
            mapOf("role" to "assistant", "content" to "记得，我们在挑裙子"),
            mapOf("role" to "user", "content" to "这件怎么样？"))
        val result = VisionConversationContext.attach(context, listOf("IMAGE" to "image/png"), "这件怎么样？")
        assertEquals(context.take(3), result.take(3))
        assertEquals(4, result.size)
        val parts = result.last()["content"] as List<*>
        assertEquals("image_url", (parts[0] as Map<*, *>)["type"])
        assertTrue(parts.toString().contains("这件怎么样？"))
        assertEquals("这件怎么样？", context.last()["content"])
    }

    @Test fun relatedHistoryIsRetainedWhenToolQuestionDiffersFromLatestUserText() {
        val context = listOf(mapOf<String, Any?>("role" to "user", "content" to "结合刚才我说的喜好评价一下"))
        val result = VisionConversationContext.attach(context, listOf("IMAGE" to "image/jpeg"), "请分析衣服款式")
        val parts = result.last()["content"] as List<*>
        val text = (parts.last() as Map<*, *>)["text"].toString()
        assertTrue(text.contains("结合刚才我说的喜好"))
        assertTrue(text.contains("请分析衣服款式"))
        assertEquals(1, result.size)
    }

    @Test fun standaloneReferenceAnalysisDoesNotInventConversation() {
        val result = VisionConversationContext.attach(emptyList(), listOf("IMAGE" to "image/png"), "描述外貌")
        assertEquals(1, result.size)
        assertEquals("user", result.single()["role"])
    }
}
