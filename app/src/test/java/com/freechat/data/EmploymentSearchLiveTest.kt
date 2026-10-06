package com.freechat.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.Proxy
import java.net.ProxySelector
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.URI
import java.io.IOException

/** 只访问免费公开入口，不读取任何模型或付费搜索 key。 */
class EmploymentSearchLiveTest {
    @Test fun henanEmploymentRetrievalIsRelevantAndBounded() = runBlocking {
        assumeTrue(System.getenv("FREECHAT_LIVE_EMPLOYMENT") == "1")
        // 此开发机的系统代理不被 JVM 自动读取；仅显式实测时使用已验证的本机代理。
        // 不改变 Android 网络配置，不读代理密码；MockWebServer 的回环请求不走代理。
        System.getenv("FREECHAT_TEST_PROXY_PORT")?.toIntOrNull()?.takeIf { it in 1..65535 }?.let { port ->
            val previous = ProxySelector.getDefault()
            ProxySelector.setDefault(object : ProxySelector() {
                override fun select(uri: URI): List<Proxy> = if (uri.scheme == "https" && uri.host !in listOf("localhost", "127.0.0.1", "::1"))
                    listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))) else previous?.select(uri) ?: listOf(Proxy.NO_PROXY)
                override fun connectFailed(uri: URI, address: SocketAddress, error: IOException) { previous?.connectFailed(uri, address, error) }
            })
        }
        val question = "河南公办二本软件工程专业,在河南内部有什么合适的岗位或者企业推荐？"
        val intent = SearchIntent(listOf("河南 软件开发 校招 本科", "郑州 软件公司 招聘", "河南 软件园 企业"),
            listOf(listOf("河南", "郑州", "洛阳"), listOf("软件", "开发", "测试", "Java")), news = false)
        SearchPipeline.clearCache()
        val start = System.nanoTime()
        val result = SearchPipeline.search(question, intent = intent)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("Employment search: ${ms}ms / ${result.entries.size} relevant sources / ${result.notice}")
        result.entries.forEach { println("${it.title} | ${it.link}") }
        if (result.entries.isEmpty()) {
            runCatching { SearchSource.duckDuckGoSearch(intent.queries.first()) }
                .onSuccess { println("DuckDuckGo raw: ${it.size} / ${it.take(3).map { entry -> entry.title }}") }
                .onFailure { println("DuckDuckGo error: ${it.javaClass.simpleName} / ${it.message}") }
        }
        assertTrue("No relevant free results: ${result.notice}", result.entries.isNotEmpty())
        assertTrue(ms < 9500)
        assertTrue(result.entries.none { it.title.contains("机票") || it.title.contains("新加坡") || it.title.contains("油气") })
        assertTrue(result.text.contains("<search_sources>"))
    }
}
