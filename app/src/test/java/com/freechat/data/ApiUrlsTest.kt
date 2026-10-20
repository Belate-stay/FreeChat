package com.freechat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-10-20 用户拍板：APK 里自定义模型的 URL **加不加后缀都能用，根路径填对即可**。
 * 病根先例：New API 网关要 /v1、DeepSeek 不要，三条拼接链规则还互相打架——
 * 同一 URL 一条通一条挂。统一规整层后矩阵全覆盖。
 */
class ApiUrlsTest {

    @Test fun bareDomainRootGetsV1() {
        // ② 裸域名根→自动补 /v1（OpenAI 兼容网关共同惯例）
        assertEquals("https://api2.qiuqiutoken.net/v1/images/generations",
            ApiUrls.endpoint("https://api2.qiuqiutoken.net", "images/generations"))
        assertEquals("https://api.deepseek.com/v1/chat/completions",
            ApiUrls.endpoint("https://api.deepseek.com", "/v1/chat/completions"))
    }

    @Test fun userSuffixesAreHonoredNotDoubled() {
        // ③ 已带版本段→原样；⑥ 尾斜杠/大小写归一；**绝不拼出 /v1/v1、/api/api**
        assertEquals("https://api2.qiuqiutoken.net/v1/images/generations",
            ApiUrls.endpoint("https://api2.qiuqiutoken.net/v1/", "images/generations"))
        assertEquals("https://gw.example.com/api/v1/chat/completions",
            ApiUrls.endpoint("https://gw.example.com/api/v1", "chat/completions"))
        assertEquals("https://gw.example.com/v2/chat/completions",
            ApiUrls.endpoint("https://gw.example.com/V2", "chat/completions"))
    }

    @Test fun apiPrefixGetsV1Too() {
        // ④ /api 结尾=New API 型网关前缀形态→补 /v1
        assertEquals("https://gw.example.com/api/v1/chat/completions",
            ApiUrls.endpoint("https://gw.example.com/api", "chat/completions"))
    }

    @Test fun customPathsAreLeftAlone() {
        // ⑤ 厂商自定义路径不猜（/openai、/gateway…）
        assertEquals("https://gw.example.com/openai/deployments/x/chat/completions",
            ApiUrls.endpoint("https://gw.example.com/openai/deployments/x", "chat/completions"))
        // ① 误粘完整端点→剥掉按规则重拼（粘全链接也对）
        assertEquals("https://api2.qiuqiutoken.net/v1/images/edits",
            ApiUrls.endpoint("https://api2.qiuqiutoken.net/v1/images/generations", "images/edits"))
        assertEquals("https://api.deepseek.com/v1/chat/completions",
            ApiUrls.endpoint("https://api.deepseek.com/v1/chat/completions/", "chat/completions"))
    }

    @Test fun chainsAllUseTheSameResolver() {
        // 三条链同一套规则（对话/语音/生图/获取列表），不再各拼各的
        val chat = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()
        val img = File("src/main/java/com/freechat/data/ImageApiRequest.kt").readText()
        val cat = File("src/main/java/com/freechat/data/ModelCatalog.kt").readText()
        assertTrue("对话/语音/ASR 链走 ApiUrls", chat.contains("com.freechat.data.ApiUrls.endpoint(model.apiBaseUrl, path)"))
        assertTrue("生图链走 ApiUrls", img.contains("ApiUrls.endpoint(base,"))
        assertTrue("获取列表链走 ApiUrls", cat.contains("fun resolveUrl(baseUrl: String, path: String): String = ApiUrls.endpoint(baseUrl, path)"))
    }

    @Test fun oldPinsStayCompatible() {
        // Beta811 旧契约同果：完整 generations 端点→edits；/api/v3 版本段原样
        assertEquals("https://example.org/v1/images/edits", ImageApiRequest.endpoint("https://example.org/v1/images/generations", true))
        assertEquals("https://example.org/api/v3/images/generations", ImageApiRequest.endpoint("https://example.org/api/v3", false))
    }
}
