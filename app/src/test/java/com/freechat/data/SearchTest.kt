package com.freechat.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class SearchTest {
    @Test fun recentMeaningAndMonthRolloverSurvivePlanning() {
        assertEquals(7, SearchPipeline.plan("最近 DeepSeek 有哪些新闻").recentDays)
        assertEquals("DeepSeek", SearchPipeline.plan("最近 DeepSeek 有哪些新闻").queries.first())
        val plan = SearchPipeline.plan("上个月 DeepSeek 新闻", LocalDate.of(2026, 1, 2))
        assertTrue(plan.queries.first().contains("2025-12"))
        assertNull(plan.recentDays)
        assertNull(SearchPipeline.plan("2024年 DeepSeek 新闻").recentDays)
    }

    @Test fun feedPreservesDescriptionDateAndPublisherUrl() {
        val entries = SearchSource.parseRss("""<rss><channel><item><title>A &amp; B</title><link>https://www.bing.com/news/apiclick.aspx?url=https%3A%2F%2Fexample.org%2Farticle%3Fa%3D1%26b%3D2</link><description><![CDATA[An <b>actual</b> news summary.]]></description><pubDate>Wed, 30 Sep 2026 01:00:00 GMT</pubDate></item></channel></rss>""", true)
        assertEquals(1, entries.size)
        assertEquals("A & B", entries[0].title)
        assertEquals("An actual news summary.", entries[0].snippet)
        assertEquals("https://example.org/article?a=1&b=2", entries[0].link)
        assertTrue(entries[0].date.contains("2026"))
        assertNotNull(entries[0].publishedAt)
    }

    @Test fun htmlAndExternalEntityFeedsAreRejected() {
        listOf("<html>Search</html>", "<!DOCTYPE rss SYSTEM 'file:///secret'><rss/>").forEach {
            assertTrue(runCatching { SearchSource.parseRss(it, false) }.isFailure)
        }
    }

    @Test fun relevantRecentArticleWinsAndUnrelatedHitsAreRemoved() {
        val now = System.currentTimeMillis()
        val old = SearchEntry("DeepSeek launch", "DeepSeek model", "https://a.test/old", publishedAt = now - 365L * 86400000)
        val fresh = SearchEntry("DeepSeek launch", "DeepSeek model", "https://b.test/new", publishedAt = now - 1000)
        val unrelated = SearchEntry("Cake recipe", "Food", "https://c.test/cake")
        val ranked = SearchPipeline.rank(listOf(old, fresh, unrelated), "DeepSeek 最新消息", 7, now)
        assertEquals(listOf(fresh), ranked)
    }

    @Test fun customAdaptersKeepKeysInExpectedHeadersAndUseTimeFilters() {
        for (provider in listOf(SearchProvider.TAVILY, SearchProvider.BRAVE, SearchProvider.SEARXNG, SearchProvider.FIRECRAWL)) {
            val req = SearchSource.customRequest(SearchConfig(provider, "https://search.example/api", "private-key"), "测试 query", 1)
            assertFalse(req.url.toString().contains("private-key"))
            assertEquals("private-key", if (provider == SearchProvider.BRAVE) req.header("X-Subscription-Token") else req.header("Authorization")?.removePrefix("Bearer "))
            if (provider == SearchProvider.TAVILY || provider == SearchProvider.FIRECRAWL) {
                val buf = Buffer(); req.body!!.writeTo(buf)
                val body = buf.readUtf8()
                assertTrue(body.contains("测试 query"))
                assertFalse(body.contains("private-key"))
                assertTrue(body.contains(if (provider == SearchProvider.TAVILY) "day" else "qdr:d"))
            }
        }
        assertNotNull(SearchConfig(SearchProvider.BRAVE, "http://example.org", "secret").validationError())
        assertFalse(SearchConfig(SearchProvider.BRAVE, "https://example.org", "secret").toString().contains("secret"))
    }

    @Test fun parseCustomResponsesAndDoNotAcceptErrorObjectsAsEmptyResults() {
        val tavily = SearchSource.parseJson("""{"results":[{"title":"News","url":"https://example.org","content":"Full summary","published_date":"Wed, 30 Sep 2026 01:00:00 GMT"}]}""", SearchProvider.TAVILY)
        assertNotNull(tavily.single().publishedAt)
        assertEquals("Full summary", tavily.single().snippet)
        assertEquals(1, SearchSource.parseJson("""{"web":{"results":[{"title":"News","url":"https://example.org","description":"Summary"}]}}""", SearchProvider.BRAVE).size)
        assertEquals(1, SearchSource.parseJson("""{"success":true,"data":{"web":[],"news":[{"title":"News","url":"https://example.org","snippet":"News summary"}]}}""", SearchProvider.FIRECRAWL).size)
        assertTrue(runCatching { SearchSource.parseJson("""{"error":"Unauthorized"}""", SearchProvider.TAVILY) }.isFailure)
    }

    @Test fun cancellingDuringBodyReadReallyCancelsHttpCall() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("late result").setBodyDelay(5, TimeUnit.SECONDS))
        server.start()
        val call = OkHttpClient().newCall(Request.Builder().url(server.url("/")).build())
        try {
            val started = System.nanoTime()
            assertNull(withTimeoutOrNull(200) { call.awaitText() })
            assertTrue(call.isCanceled())
            assertTrue((System.nanoTime() - started) / 1e6 < 1500)
        } finally { server.shutdown() }
    }

    @Test fun readerPrefersArticleAndSourcesRemainClickable() {
        val html = "<nav>Long navigation text that must never be included</nav><article><p>The original article with detailed factual information.</p></article>"
        assertEquals("The original article with detailed factual information.", WebReader.extractText(html))
        val content = SearchPresentation.withSources("Answer", listOf("A [source]" to "https://example.org/a"))
        assertTrue(content.contains("](https://example.org/a)"))
        assertEquals(content, SearchPresentation.withSources(content, listOf("source" to "https://example.org/a")))
        val unfinished = SearchPresentation.withSources("```text\n原文\n", listOf("report" to "https://example.org/report"))
        assertEquals("```text\n原文\n\n```\n\n[report](https://example.org/report)", unfinished)
    }
}
