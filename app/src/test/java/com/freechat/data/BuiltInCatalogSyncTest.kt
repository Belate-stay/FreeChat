package com.freechat.data

import com.freechat.model.ModelType
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * 云端模型目录同步 + 云端弹窗（2026-10-18）契约：
 *  · catalog 是权威：云端换模型（DeepSeek→Grok）页面跟着变；catalog 空/缺类=本地兜底；
 *  · 目录来的模型永远无 Key、指代理；
 *  · 弹窗：三语字段、版本/平台筛选、maxImpressions/cooldown 资格、priority 排序；
 *  · 源码钉：列表流挂 _catalogVersion、MainActivity 两种弹窗外壳都在。
 */
class BuiltInCatalogSyncTest {

    private val deepseek = BuiltInLanguageModel.create()
    private val grok = BuiltInCatalog.Entry("grok-4.6", "Grok-4.6", "LANGUAGE", "xAI 旗舰")

    @Test fun catalogTypeMappingCoversImageAlias() {
        assertEquals(ModelType.VISUAL, BuiltInCatalog.typeOf("IMAGE"))
        assertEquals(ModelType.VISUAL, BuiltInCatalog.typeOf("visual"))
        assertEquals(ModelType.TTS, BuiltInCatalog.typeOf("TTS"))
        assertEquals(ModelType.ASR, BuiltInCatalog.typeOf("asr"))
    }

    @Test fun catalogSwapReplacesTheBuiltInOnTheModelPage() {
        val merged = BuiltInCatalog.merge(listOf(deepseek), listOf(grok), ModelType.LANGUAGE)
        assertEquals(1, merged.size)
        assertEquals("grok-4.6", merged[0].id)
        assertEquals("Grok-4.6", merged[0].displayName)
        assertEquals("xAI 旗舰", merged[0].description)
        // 永远无 Key、指代理（云端换上游不用动客户端）
        assertEquals("", merged[0].apiKey)
        assertEquals(com.freechat.data.BuiltInProxy.BASE, merged[0].apiBaseUrl)
        assertTrue(merged[0].isBuiltIn)
    }

    @Test fun emptyCatalogFallsBackToLocalAndUncatalogedTypesStayLocal() {
        assertEquals(listOf(deepseek), BuiltInCatalog.merge(listOf(deepseek), emptyList(), ModelType.LANGUAGE))
        // catalog 只报 LANGUAGE 时，TTS 本地定义（音色设计/复刻类常量特性）原样保留、不混入别的类
        val tts = BuiltInVoiceModel.createAll()
        val merged = BuiltInCatalog.merge(tts, listOf(grok), ModelType.TTS)
        assertEquals(3, merged.size)
        assertTrue(merged.any { it.id == BuiltInVoiceModel.STOCK_ID })
        assertTrue(merged.any { it.id == BuiltInVoiceModel.DESIGN_ID })
    }

    @Test fun parseReadsDisplayFieldsOnly() {
        val list = BuiltInCatalog.parse(
            """{"models":[{"id":"m1","label":"模型一","type":"VISION","desc":"简介","url":"https://u.example",
              "deepThinking":true,"webSearch":false,
              "upstream":{"base":"https://secret.invalid","keyEnv":"KEY"}}]}"""
        )
        assertEquals(1, list.size)
        assertEquals("模型一", list[0].label)
        assertEquals("VISION", list[0].type)
        assertEquals("云端 URL 下发（填什么显示什么）", "https://u.example", list[0].url)
        assertEquals("能力声明下发", true, list[0].deepThinking)
        assertEquals(false, list[0].webSearch)
    }

    @Test fun capabilityDeclarationsOverrideLocalFallbackOnlyWhenPresent() {
        // 云端勾了「声明支持联网搜索」→ 覆盖本地 false；没声明深度思考 → 保持本地能力位
        val e = BuiltInCatalog.Entry("grok-4.6", "Grok", "LANGUAGE", "", deepThinking = null, webSearch = true)
        val m = BuiltInCatalog.toModelInfo(e, deepseek)
        assertEquals(true, m.supportsWebSearch)
        assertEquals(deepseek.supportsDeepThinking, m.supportsDeepThinking)
        // 显式声明不支持 → 云端为准
        val off = BuiltInCatalog.toModelInfo(e.copy(deepThinking = false, webSearch = false), deepseek)
        assertEquals(false, off.supportsDeepThinking)
        assertEquals(false, off.supportsWebSearch)
    }

    @Test fun urlFieldShowsCloudValueButRoutingNeverUsesItForBuiltIns() {
        // 「URL 填什么显示什么」：编辑页显示云端 URL；但内置请求恒走代理（customEndpoint 对
        // isBuiltIn 钉死 BuiltInProxy.BASE）——显示件填坏也弄不断模型（2026-10-18 空回复根因）
        val e = BuiltInCatalog.Entry("deepseek-flash", "X", "LANGUAGE", "", url = "https://api.deepseek.com")
        assertEquals("https://api.deepseek.com", BuiltInCatalog.toModelInfo(e, deepseek).apiBaseUrl)
        val vm = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()
        assertTrue("内置路由钉死代理", vm.contains("if (model.isBuiltIn)"))
        assertTrue(vm.contains("\${com.freechat.data.BuiltInProxy.BASE}/\$endpoint"))
    }

    @Test fun remoteNoticeParsePicksLocaleAndDefaultsButtons() {
        val list = RemoteNotices.parse(
            """{"notices":[
              {"id":"n1","style":"fullscreen","title":{"zh":"标题","en":"Title"},
               "body":{"zh":"正文 [链](https://a.example)","en":"Body"},
               "buttons":[{"id":"go","label":{"zh":"去"},"action":"navigate","page":"about"},
                          {"id":"no","label":{"zh":"不"},"action":"dismiss"}],
               "target":{"platform":"android","minVersion":"1.2.0.5","maxVersion":"1.2.0.7"},
               "maxImpressions":2,"cooldownHours":12,"priority":5},
              {"id":"n2","title":"纯字符串标题","body":"b"}]}"""
        )
        assertEquals(2, list.size)
        assertEquals("fullscreen", list[0].style)
        assertEquals("alert", list[1].style)
        assertEquals("标题", list[0].title)     // zh 优先
        assertEquals("纯字符串标题", list[1].title)
        assertEquals("去", list[0].buttons[0].label)
        assertEquals("navigate", list[0].buttons[0].action)
        assertEquals("about", list[0].buttons[0].page)
        assertEquals("manual", list[0].trigger)
        assertEquals(2, list[0].maxImpressions)
        // 没写 buttons 的默认给一个 dismiss 钮（强制弹窗永远有出口按钮可选）
        assertEquals("dismiss", list[1].buttons.single().action)
        assertEquals("版本区间是版本名字符串，空=不限", "", list[1].minVersion)
    }

    @Test fun eligibilityFiltersByVersionImpressionsCooldownAndSortsByPriority() {
        val n = RemoteNotices.Notice(
            id = "n", style = "alert", trigger = "manual", enabled = true, title = "t", body = "b", buttons = emptyList(),
            minVersion = "1.2.0.5", maxVersion = "1.2.0.7", platform = "android",
            maxImpressions = 2, cooldownHours = 12, priority = 5
        )
        assertTrue("版本名比较（带 Beta 前缀也能比）", RemoteNotices.targets(n, "Beta 1.2.0.6"))
        assertFalse(RemoteNotices.targets(n, "1.2.0.8"))
        assertFalse(RemoteNotices.targets(n.copy(platform = "web"), "1.2.0.6"))
        assertEquals("版本名打包可比较", true, RemoteNotices.packVersion("1.2.0.5") < RemoteNotices.packVersion("1.2.0.7"))
        assertEquals("老四位版本可比", true, RemoteNotices.packVersion("1.1.10") < RemoteNotices.packVersion("1.2.0"))

        val list = listOf(n, n.copy(id = "n2", priority = 9), n.copy(id = "n3", maxImpressions = 0))
        val out = RemoteNotices.eligible(list, "1.2.0.6",
            impressionCount = { if (it == "n") 2 else 0 },   // n 已弹满 2 次
            lastShownAt = { if (it == "n2") System.currentTimeMillis() else 0L }, // n2 刚弹过
            now = System.currentTimeMillis())
        // n 超次数、n2 没过 12h 冷却、n3 不限次数 → 只剩 n3
        assertEquals(listOf("n3"), out.map { it.id })

        val sorted = RemoteNotices.eligible(list, "1.2.0.6", { 0 }, { 0L }, 0L)
        assertEquals("priority 高的先弹", listOf("n2", "n", "n3"), sorted.map { it.id })
    }

    @Test fun wiringPins() {
        val vm = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()
        assertTrue("列表流要挂目录版本号才会实时重建", vm.contains("combine(_customModels, _builtInParams, _catalogVersion)"))
        assertTrue("启动要拉目录", vm.contains("refreshBuiltInCatalog()"))
        assertTrue("启动要拉云端弹窗", vm.contains("refreshRemoteNotices()"))
        val main = File("src/main/java/com/freechat/MainActivity.kt").readText()
        assertTrue("alert 外壳接线", main.contains("RemoteAlertDialog("))
        assertTrue("fullscreen 外壳接线", main.contains("RemoteFullscreenNotice("))
        assertTrue("弹出与选择留痕", main.contains("onRemoteNoticeShown") && main.contains("onRemoteNoticeChoice"))
        val gradle = File("build.gradle.kts").readText()
        assertFalse("五把 Key 不进 BuildConfig", gradle.contains("DEEPSEEK_API_KEY") || gradle.contains("CHATGPT_IMAGE_KEY"))
    }
}
