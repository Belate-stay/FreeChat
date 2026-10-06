package com.freechat.data

import org.junit.Assert.*
import org.junit.Test

class NativeSearchPolicyTest {
    @Test fun mimoAndDashScopeBothExposeAutomaticSearchNotForcedSearch() {
        val mimo = NativeSearchPolicy.parameters(true)
        val tool = (mimo["tools"] as List<*>).first() as Map<*, *>
        assertEquals("web_search", tool["type"])
        assertEquals(false, tool["force_search"])
        assertFalse(tool.containsKey("web_search"))
        val qwen = NativeSearchPolicy.parameters(false)
        assertEquals(true, qwen["enable_search"])
        assertEquals(false, (qwen["search_options"] as Map<*, *>)["forced_search"])
    }
    @Test fun skippingSearchDoesNotCauseFallbackOrASecondGeneration() {
        assertFalse(NativeSearchPolicy.failed(true, "", null))
        assertFalse(NativeSearchPolicy.failed(false, "search failed", null))
        assertFalse(NativeSearchPolicy.failed(true, "", "API error 401 Unauthorized"))
        assertFalse(NativeSearchPolicy.failed(true, "", "API error 500 Service failure"))
    }
    @Test fun onlyExplicitSearchFailureTriggersTheCompatibilityPath() {
        assertTrue(NativeSearchPolicy.failed(true, "Keyword extraction model timed out", null))
        assertTrue(NativeSearchPolicy.failed(true, "", "API error 400 unsupported enable_search"))
    }
}
