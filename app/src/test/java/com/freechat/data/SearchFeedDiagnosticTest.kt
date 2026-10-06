package com.freechat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Manual network diagnostics; not part of the normal test gate. */
class SearchFeedDiagnosticTest {
    @Test fun inspectPublicFeedVariants() = runBlocking {
        assumeTrue(System.getenv("FREECHAT_LIVE_DIAGNOSTIC") == "1")
        val query = "%E4%B8%AD%E5%9B%BD%E7%A7%91%E6%8A%80"
        val urls = listOf(
            "https://www.bing.com/news/search?q=$query&format=RSS&setlang=en-US",
            "https://www.bing.com/news/search?q=$query&format=RSS&mkt=en-US&cc=us",
            "https://www.bing.com/news/search?q=$query&format=rss&setmkt=en-US",
            "https://www.bing.com/news/search?format=RSS&q=$query",
            "https://www.ithome.com/rss/",
            "https://www.solidot.org/index.rss",
            "https://api.gdeltproject.org/api/v2/doc/doc?query=DeepSeek&mode=artlist&maxrecords=10&timespan=1week&format=json"
        )
        val client = OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS).build()
        urls.map { url -> async(Dispatchers.IO) {
            val result = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    "${response.code} ${response.request.url} ${response.peekBody(110).string().replace(Regex("\\s+"), " ")}" }
            }.getOrElse { "${it.javaClass.simpleName}: ${it.message}" }
            "$url => $result"
        } }.awaitAll().forEach(::println)
    }
}
