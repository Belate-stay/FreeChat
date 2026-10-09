package com.freechat.companion

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class WechatBindingTest {
    private val validBody = JsonParser.parseString(
        """{"channel":"wechat","allowWechatEmoji":true,"wechatBinding":{"userId":"u1","boundAt":1700000000000}}"""
    ).asJsonObject

    @Test
    fun onlyTheExactInternalHeaderFromLoopbackCanSupplyBindingMetadata() {
        assertEquals(WechatBinding("u1", 1_700_000_000_000L), WechatBinding.fromTrustedRequest(validBody, "wechat", true))
        for (header in listOf(null, "", "app", "WECHAT", "wechat ")) {
            assertNull(WechatBinding.fromTrustedRequest(validBody, header, true))
        }
        assertNull(WechatBinding.fromTrustedRequest(validBody, "wechat", false))
    }

    @Test
    fun malformedOrNonIntegerBindingMetadataNeverEnablesTheChannel() {
        val invalid = listOf(
            "{}", """{"wechatBinding":null}""", """{"wechatBinding":[]}""",
            """{"wechatBinding":{"userId":"u1"}}""",
            """{"wechatBinding":{"userId":"","boundAt":1700000000000}}""",
            """{"wechatBinding":{"userId":1,"boundAt":1700000000000}}""",
            """{"wechatBinding":{"userId":"u1","boundAt":"1700000000000"}}""",
            """{"wechatBinding":{"userId":"u1","boundAt":0}}""",
            """{"wechatBinding":{"userId":"u1","boundAt":-1}}""",
            """{"wechatBinding":{"userId":"u1","boundAt":1.5}}""",
            """{"wechatBinding":{"userId":"u1","boundAt":999999999999999999999999}}""",
            """{"wechatBinding":{"userId":"u/1","boundAt":1700000000000}}"""
        )
        for (json in invalid) {
            assertNull(json, WechatBinding.fromTrustedRequest(JsonParser.parseString(json).asJsonObject, "wechat", true))
        }
    }
}
