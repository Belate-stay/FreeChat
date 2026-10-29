package com.freechat.companion

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** 看板「模型管理」→ 微信大脑的配置直读：wechat 专属条目优先、逐字段兜底、缺省回落、URL 拼接口段。 */
class BrainUpstreamTest {

    private val defaults = BrainUpstream.Resolved(
        entryId = "deepseek-flash", baseUrl = "https://api.deepseek.com",
        modelId = "deepseek-flash", apiKey = "sk-default"
    )

    @After
    fun tearDown() {
        BrainUpstream.configPathOverride = null
    }

    private fun writeCfg(json: String) {
        val f = File.createTempFile("cloud", ".json")
        f.writeText(json)
        f.deleteOnExit()
        BrainUpstream.configPathOverride = f.absolutePath
    }

    @Test
    fun flashEntryOverridesDefaults() {
        writeCfg(
            """{"models":[{"id":"deepseek-flash","upstream":{"base":"https://api.example.com","model":"real-model","apiKey":"sk-new"}}]}"""
        )
        val r = BrainUpstream.resolve(defaults)
        assertEquals("deepseek-flash", r.entryId)
        assertEquals("https://api.example.com", r.baseUrl)
        assertEquals("real-model", r.modelId)
        assertEquals("sk-new", r.apiKey)
    }

    @Test
    fun wechatEntryWinsAndFallsBackPerField() {
        writeCfg(
            """{"models":[
              {"id":"deepseek-flash","upstream":{"base":"https://flash.example","model":"flash-model","apiKey":"sk-flash"}},
              {"id":"wechat","upstream":{"base":"","model":"","apiKey":"sk-wechat"}}
            ]}"""
        )
        val r = BrainUpstream.resolve(defaults)
        assertEquals("wechat", r.entryId)                 // 微信独立生效 → 账本 model 列记 wechat
        assertEquals("https://flash.example", r.baseUrl)  // base 留空逐字段兜底吃 flash 条目
        assertEquals("flash-model", r.modelId)
        assertEquals("sk-wechat", r.apiKey)               // Key 用微信自己的
    }

    @Test
    fun wechatEntryWithoutOwnKeyDoesNotCount() {
        writeCfg(
            """{"models":[
              {"id":"deepseek-flash","upstream":{"base":"https://flash.example","model":"flash-model","apiKey":"sk-flash"}},
              {"id":"wechat","upstream":{"base":"https://api.deepseek.com","model":"m","apiKey":""}}
            ]}"""
        )
        val r = BrainUpstream.resolve(defaults)
        assertEquals("deepseek-flash", r.entryId)          // 只有 base 没自己的 Key = 没独立，整套吃 flash
        assertEquals("sk-flash", r.apiKey)
    }

    @Test
    fun missingFileOrEmptyPoolFallsBackToDefaults() {
        BrainUpstream.configPathOverride = "Z:/definitely/missing/cloud.json"
        assertEquals(defaults, BrainUpstream.resolve(defaults))
        writeCfg("""{"models":[]}""")
        assertEquals(defaults, BrainUpstream.resolve(defaults))
    }

    @Test
    fun partialEntryFillsHolesFromDefaults() {
        writeCfg("""{"models":[{"id":"deepseek-flash","upstream":{"base":"https://only-base.example","model":"","apiKey":""}}]}""")
        val r = BrainUpstream.resolve(defaults)
        assertEquals("https://only-base.example", r.baseUrl)
        assertEquals("deepseek-flash", r.modelId)
        assertEquals("sk-default", r.apiKey)
    }

    @Test
    fun requestUrlAppendsChatPathUnlessFullEndpoint() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            BrainUpstream.requestUrl("https://api.deepseek.com/")
        )
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            BrainUpstream.requestUrl("https://api.deepseek.com/chat/completions")
        )
        assertEquals(
            "https://x.example/api/v3/chat/completions",
            BrainUpstream.requestUrl("https://x.example/api/v3", "/chat/completions")
        )
    }
}
