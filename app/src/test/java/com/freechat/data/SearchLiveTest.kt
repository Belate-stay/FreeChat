package com.freechat.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicitly enabled public-network smoke test; never uses model keys or paid search credentials. */
class SearchLiveTest {
    @Test fun publicSourcesReturnUsableResultsWithinDeadline() = runBlocking {
        assumeTrue(System.getenv("FREECHAT_LIVE_SEARCH") == "1")
        for (query in listOf("DeepSeek 最新新闻", "最近中国科技新闻", "OpenAI latest news")) {
            SearchPipeline.clearCache()
            val started = System.nanoTime()
            val result = SearchPipeline.search(query)
            val ms = (System.nanoTime() - started) / 1_000_000
            println("QUERY: $query | ${ms}ms | ${result.entries.size} sources | ${result.notice}")
            result.entries.forEach { println("${it.date} | ${it.title} | ${it.link}") }
            val planned = SearchPipeline.plan(query)
            runCatching { SearchSource.publicSearch(planned.queries.first(), true, planned.recentDays) }
                .onSuccess { hits -> println("NEWS: ${hits.size} | ${hits.take(3).map { it.date + " " + it.title }}") }
                .onFailure { error -> println("NEWS ERROR: ${error.javaClass.simpleName}: ${error.message}") }
            if (result.entries.isEmpty()) {
                listOf(false, true).forEach { news ->
                    runCatching { SearchSource.publicSearch(planned.queries.first(), news, planned.recentDays) }
                        .onSuccess { hits -> println("RAW $news: ${hits.take(6).map { it.title }}") }
                        .onFailure { error -> println("RAW $news ERROR: ${error.javaClass.simpleName}: ${error.message}") }
                }
                runCatching { SearchSource.publicSearch(planned.queries.first(), true, null) }
                    .onSuccess { hits -> println("RAW NEWS WITHOUT FILTER: ${hits.take(6).map { it.title }}") }
                    .onFailure { error -> println("RAW NEWS WITHOUT FILTER ERROR: ${error.javaClass.simpleName}: ${error.message}") }
            }
            assertTrue("Exceeded search deadline: ${ms}ms", ms < 10_000)
            assertTrue("No usable results: $query / ${result.notice}", result.entries.isNotEmpty())
            assertTrue(result.text.contains("<search_sources>"))
            assertTrue("Undated recent results were presented without warning", result.entries.any { it.publishedAt != null } ||
                result.notice.contains("发布日期"))
            if (query.contains("中国科技")) {
                assertTrue("Direct Chinese news feeds returned no dated matching article",
                    result.entries.any { it.publishedAt != null })
            }
        }
    }
}
