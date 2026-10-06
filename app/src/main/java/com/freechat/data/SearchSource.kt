package com.freechat.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

data class SearchEntry(val title: String, val snippet: String, val link: String, val date: String = "",
    val rank: Int = 0, val fromNews: Boolean = false, val publishedAt: Long? = null,
    val content: String = "")

internal object SearchSource {
    private val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS).callTimeout(5500, TimeUnit.MILLISECONDS)
        // A user-supplied endpoint receives its own key only; never forward it across redirects.
        .followRedirects(false).followSslRedirects(false).build()
    // Public Bing feeds do redirect by locale; these requests contain no credentials.
    private val publicClient = client.newBuilder().followRedirects(true).followSslRedirects(true).build()
    private val googleClient = publicClient.newBuilder().connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS).callTimeout(3, TimeUnit.SECONDS).build()
    @Volatile private var bingNewsUnavailableUntil = 0L

    /** 普通网页入口保留真实关键词；RSS 在部分地区忽略中文主题，不再用它搜索网页。 */
    suspend fun publicWebSearch(query: String): List<SearchEntry> = coroutineScope {
        val results = listOf(async { safePublic { bingWebSearch(query) } }, async { safePublic {
            duckDuckGoSearch(query)
        } }).awaitAll()
        require(results.any { it != null }) { "Public sources unavailable" }
        results.filterNotNull().flatten()
    }
    private suspend fun safePublic(block: suspend () -> List<SearchEntry>): List<SearchEntry>? = try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }

    internal suspend fun bingWebSearch(query: String): List<SearchEntry> {
        val url = "https://www.bing.com/search".toHttpUrlOrNull()!!.newBuilder().addQueryParameter("q", query).build()
        val html = publicClient.newCall(Request.Builder().url(url)
            .header("User-Agent", "FreeChat/1.0.80.1")
            .header("Accept", "text/html").build()).awaitText()
        require(!Regex("id=[\"']b_captcha|class=[\"'][^\"']*captcha", RegexOption.IGNORE_CASE).containsMatchIn(html)) {
            "Public source requires verification"
        }
        return parseBingWeb(html)
    }

    internal fun parseBingWeb(html: String): List<SearchEntry> {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        // b_algo 是自然结果。广告 b_ad、导航、侧边推荐不能计入信息源。
        val results = Regex("<li\\b[^>]*class=[\"'][^\"']*\\bb_algo\\b[^\"']*[\"'][^>]*>(.*?)</li>", options)
            .findAll(html)
        return results.mapIndexedNotNull { index, match ->
            val heading = Regex("<h2\\b[^>]*>(.*?)</h2>", options).find(match.groupValues[1]) ?: return@mapIndexedNotNull null
            val anchor = Regex("<a\\b[^>]*>(.*?)</a>", options).find(heading.groupValues[1]) ?: return@mapIndexedNotNull null
            val href = Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(anchor.value)?.groupValues?.get(1)
                ?.replace("&amp;", "&") ?: return@mapIndexedNotNull null
            val target = href.toHttpUrlOrNull() ?: return@mapIndexedNotNull null
            val link = if (target.host == "bing.com" || target.host.endsWith(".bing.com")) {
                // 正常结果 /ck/a 的 u=a1+base64url 含原始地址；无需访问追踪跳转。
                val encoded = target.queryParameter("u")?.takeIf { it.startsWith("a1") }?.drop(2) ?: return@mapIndexedNotNull null
                runCatching { String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8).toHttpUrlOrNull() }.getOrNull()
                    ?: return@mapIndexedNotNull null
            } else target
            if (link.username.isNotEmpty() || link.password.isNotEmpty() || link.host == "bing.com" || link.host.endsWith(".bing.com"))
                return@mapIndexedNotNull null
            val snippetHtml = Regex("<p\\b[^>]*>(.*?)</p>", options)
                .find(match.groupValues[1].substringAfter(heading.value))?.groupValues?.get(1).orEmpty()
            val date = Regex("<span\\b[^>]*class=[\"'][^\"']*\\bnews_dt\\b[^\"']*[\"'][^>]*>(.*?)</span>", options)
                .find(snippetHtml)?.groupValues?.get(1)?.let(::cleanHtml).orEmpty()
            val published = runCatching { LocalDate.parse(date, DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
            val title = cleanHtml(anchor.groupValues[1])
            if (title.isBlank()) null else SearchEntry(title, cleanHtml(snippetHtml), link.toString(), date, index, publishedAt = published)
        }.take(20).toList()
    }

    internal suspend fun duckDuckGoSearch(query: String): List<SearchEntry> {
        val url = "https://html.duckduckgo.com/html/".toHttpUrlOrNull()!!.newBuilder().addQueryParameter("q", query).build()
        val html = publicClient.newCall(Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (compatible; FreeChat/1.0.80.1)")
            .header("Accept", "text/html").build()).awaitText()
        require(!html.contains("anomaly-modal", true)) { "Public source requires verification" }
        return parseDuckDuckGo(html)
    }

    internal fun parseDuckDuckGo(html: String): List<SearchEntry> {
        // 只读自然结果的标题与相邻摘要，验证码、广告、导航链接均不当作来源。
        val anchors = Regex("<a\\b[^>]*class=[\"'][^\"']*\\bresult__a\\b[^\"']*[\"'][^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html).toList()
        return anchors.mapIndexedNotNull { index, match ->
            val href = Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(match.value)?.groupValues?.get(1)
                ?.replace("&amp;", "&") ?: return@mapIndexedNotNull null
            val target = (if (href.startsWith("//")) "https:$href" else href).toHttpUrlOrNull() ?: return@mapIndexedNotNull null
            val link = if (target.host == "duckduckgo.com" || target.host.endsWith(".duckduckgo.com"))
                target.queryParameter("uddg")?.toHttpUrlOrNull() ?: return@mapIndexedNotNull null else target
            if (link.username.isNotEmpty() || link.password.isNotEmpty()) return@mapIndexedNotNull null
            val end = anchors.getOrNull(index + 1)?.range?.first ?: html.length
            val nearby = html.substring(match.range.last + 1, end)
            val snippet = Regex("<(?:a|div|span)\\b[^>]*class=[\"'][^\"']*\\bresult__snippet\\b[^\"']*[\"'][^>]*>(.*?)</(?:a|div|span)>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(nearby)?.groupValues?.get(1).orEmpty()
            val title = cleanHtml(match.groupValues[1])
            if (title.isBlank()) null else SearchEntry(title, cleanHtml(snippet), link.toString(), rank = index)
        }.take(20)
    }

    suspend fun publicSearch(query: String, news: Boolean, recentDays: Int?): List<SearchEntry> {
        var googleError = ""
        if (news) {
            try {
                val zh = query.any { it in '\u4e00'..'\u9fff' }
                val url = "https://news.google.com/rss/search".toHttpUrlOrNull()!!.newBuilder()
                    .addQueryParameter("q", query + (recentDays?.let { " when:${it}d" } ?: ""))
                    .addQueryParameter("hl", if (zh) "zh-CN" else "en-US")
                    .addQueryParameter("gl", if (zh) "CN" else "US")
                    .addQueryParameter("ceid", if (zh) "CN:zh-Hans" else "US:en")
                    .build()
                val entries = parseRss(googleClient.newCall(Request.Builder().url(url)
                    .header("Accept", "application/rss+xml, application/xml, text/xml").build()).awaitText(), true)
                if (entries.isNotEmpty()) return entries
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { googleError = "Google ${e.javaClass.simpleName}: ${e.message.orEmpty().take(80)}" }
            if (System.currentTimeMillis() < bingNewsUnavailableUntil) {
                throw IllegalArgumentException(googleError)
            }
        }
        val base = if (news) "https://www.bing.com/news/search" else "https://www.bing.com/search"
        // Lowercase format + mkt=zh-CN returned an HTML shell in the 1.0.75 path.
        val url = base.toHttpUrlOrNull()!!.newBuilder().addQueryParameter("q", query)
            .addQueryParameter("format", "RSS").addQueryParameter("setlang", "en-US")
        if (news && recentDays != null) url.addQueryParameter("qft", "interval=\"${if (recentDays <= 1) 7 else if (recentDays <= 7) 8 else 9}\"")
        val req = Request.Builder().url(url.build())
            .header("Accept", "application/rss+xml, application/xml, text/xml")
            .header("User-Agent", "FreeChat/1.0.76 RSS reader")
            .build()
        val body = try { publicClient.newCall(req).awaitText() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (googleError.isNotBlank()) throw IllegalArgumentException("$googleError; Bing ${e.javaClass.simpleName}: ${e.message.orEmpty().take(80)}")
            throw e
        }
        return try { parseRss(body, news) }
        catch (e: IllegalArgumentException) {
            // Some locales return the news HTML shell when qft is present, even with format=RSS.
            if (!news || recentDays == null) throw e
            val unfiltered = req.newBuilder().url(req.url.newBuilder().removeAllQueryParameters("qft").build()).build()
            try { parseRss(publicClient.newCall(unfiltered).awaitText(), true) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (again: Exception) {
                bingNewsUnavailableUntil = System.currentTimeMillis() + 10 * 60_000L
                if (googleError.isNotBlank()) throw IllegalArgumentException("$googleError; Bing ${again.javaClass.simpleName}: ${again.message.orEmpty().take(80)}")
                throw again
            }
        }
    }

    internal fun customRequest(config: SearchConfig, query: String, days: Int?): Request {
        require(config.validationError() == null) { config.validationError().orEmpty() }
        val url = config.endpoint.trim().toHttpUrlOrNull()!!
        val timeRange = days?.let { if (it <= 1) "day" else if (it <= 7) "week" else "month" }
        val request = Request.Builder().header("Accept", "application/json")
        when (config.provider) {
            SearchProvider.ANYSEARCH -> {
                val chinese = query.any { it in '\u4e00'..'\u9fff' }
                // No undocumented time-filter parameters. Preserve the explicit date range in the query.
                val datedQuery = if (days == null) query else {
                    val today = LocalDate.now()
                    "$query (${today.minusDays((days - 1).coerceAtLeast(0).toLong())} – $today)"
                }
                val body = mapOf("query" to datedQuery, "max_results" to 10, "format" to "json",
                    "zone" to if (chinese) "cn" else "intl", "language" to if (chinese) "zh-CN" else "en")
                request.url(url).post(Gson().toJson(body).toRequestBody("application/json".toMediaType()))
                // Anonymous free access is the built-in path. Only a user's own optional key is sent.
                if (config.apiKey.isNotBlank()) request.header("Authorization", "Bearer ${config.apiKey.trim()}")
            }
            SearchProvider.TAVILY -> {
                val body = mutableMapOf<String, Any>("query" to query, "search_depth" to "basic", "max_results" to 10,
                    "include_answer" to false, "include_raw_content" to false, "include_published_date" to true)
                if (timeRange != null) body["time_range"] = timeRange
                request.url(url).header("Authorization", "Bearer ${config.apiKey.trim()}")
                    .post(Gson().toJson(body).toRequestBody("application/json".toMediaType()))
            }
            SearchProvider.BRAVE -> {
                val target = url.newBuilder().addQueryParameter("q", query).addQueryParameter("count", "10")
                    .addQueryParameter("text_decorations", "false")
                if (days != null) target.addQueryParameter("freshness", if (days <= 1) "pd" else if (days <= 7) "pw" else "pm")
                request.url(target.build()).header("X-Subscription-Token", config.apiKey.trim())
            }
            SearchProvider.SEARXNG -> {
                val target = url.newBuilder().addQueryParameter("q", query).addQueryParameter("format", "json")
                if (timeRange != null) target.addQueryParameter("time_range", timeRange)
                request.url(target.build())
                if (config.apiKey.isNotBlank()) request.header("Authorization", "Bearer ${config.apiKey.trim()}")
            }
            SearchProvider.FIRECRAWL -> {
                val body = mutableMapOf<String, Any>("query" to query, "limit" to 5,
                    "sources" to if (days == null) listOf("web") else listOf("web", "news"))
                if (days != null) body["tbs"] = if (days <= 1) "qdr:d" else if (days <= 7) "qdr:w" else "qdr:m"
                request.url(url).post(Gson().toJson(body).toRequestBody("application/json".toMediaType()))
                if (config.apiKey.isNotBlank()) request.header("Authorization", "Bearer ${config.apiKey.trim()}")
            }
            SearchProvider.FREE -> error("Use publicSearch")
        }
        return request.build()
    }

    suspend fun customSearch(config: SearchConfig, query: String, days: Int?): List<SearchEntry> =
        parseJson(client.newCall(customRequest(config, query, days)).awaitText(), config.provider)

    internal fun parseJson(body: String, provider: SearchProvider): List<SearchEntry> {
        val root = JsonParser.parseString(body).asJsonObject
        val array = when (provider) {
            SearchProvider.ANYSEARCH -> {
                require(root.get("code")?.takeIf { it.isJsonPrimitive }?.asInt == 0) {
                    "Search API reported a failure" // Never echo auto-registration credentials or provider messages.
                }
                root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("results")?.takeIf { it.isJsonArray }?.asJsonArray
            }
            SearchProvider.BRAVE -> root.getAsJsonObject("web")?.getAsJsonArray("results")
            SearchProvider.FIRECRAWL -> {
                require(root.get("success")?.asBoolean != false) { "Search API reported a failure" }
                val data = root.get("data")
                if (data?.isJsonArray == true) data.asJsonArray else data?.takeIf { it.isJsonObject }?.asJsonObject?.let { groups ->
                    val web = groups.getAsJsonArray("web")
                    val news = groups.getAsJsonArray("news")
                    if (web == null && news == null) null else com.google.gson.JsonArray().apply {
                        web?.let { addAll(it) }; news?.let { addAll(it) }
                    }
                }
            }
            else -> root.getAsJsonArray("results")
        }
        require(array != null) { "Search API response has no results array" }
        fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        return array.take(20).mapIndexedNotNull { i, item ->
            if (!item.isJsonObject) return@mapIndexedNotNull null
            val e = item.asJsonObject
            val url = e.text("url").toHttpUrlOrNull() ?: return@mapIndexedNotNull null
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) return@mapIndexedNotNull null
            val date = e.text("published_date").ifBlank { e.text("publishedDate") }.ifBlank { e.text("page_age") }.ifBlank { e.text("date") }
            val time = runCatching { Instant.parse(date).toEpochMilli() }.getOrNull()
                ?: runCatching { ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            val content = if (provider == SearchProvider.ANYSEARCH) cleanHtml(e.text("content")).take(5000) else ""
            val snippet = if (provider == SearchProvider.ANYSEARCH) e.text("snippet").ifBlank { content }
                else e.text("content").ifBlank { e.text("description") }.ifBlank { e.text("snippet") }.ifBlank { e.text("markdown") }
            SearchEntry(cleanHtml(e.text("title")).ifBlank {
                if (provider == SearchProvider.ANYSEARCH) url.host else ""
            }, cleanHtml(snippet).take(5000), url.toString(), date, i, publishedAt = time, content = content)
        }.filter { it.title.isNotBlank() }
    }

    internal fun parseRss(xml: String, news: Boolean, maxItems: Int = 20): List<SearchEntry> {
        require(xml.contains(Regex("<rss[\\s>]", RegexOption.IGNORE_CASE))) {
            "Source returned non-RSS: ${xml.take(70).replace(Regex("\\s+"), " ")}"
        }
        require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) { "Invalid RSS document" }
        val builder = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        val items = builder.parse(InputSource(StringReader(xml))).getElementsByTagName("item")
        fun Element.value(name: String) = getElementsByTagName(name).item(0)?.textContent?.trim().orEmpty()
        return (0 until minOf(items.length, maxItems)).mapNotNull { i ->
            val item = items.item(i) as? Element ?: return@mapNotNull null
            val title = cleanHtml(item.value("title"))
            val rawUrl = item.value("link").toHttpUrlOrNull() ?: return@mapNotNull null
            val url = if (rawUrl.host == "bing.com" || rawUrl.host.endsWith(".bing.com"))
                rawUrl.queryParameter("url")?.toHttpUrlOrNull() ?: rawUrl else rawUrl
            // Web RSS pubDate can be the index/feed timestamp, not the article's publication date.
            val date = if (news) item.value("pubDate") else ""
            val time = runCatching { ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            if (title.isBlank()) null else SearchEntry(title, cleanHtml(item.value("description")), url.toString(), date, i, news, time)
        }
    }

    internal fun cleanHtml(raw: String): String = raw.replace(Regex("<[^>]+>"), " ")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")
        .replace(Regex("&#(x[0-9a-fA-F]+|[0-9]+);")) { m ->
            val v = m.groupValues[1]
            val point = if (v.startsWith("x")) v.drop(1).toIntOrNull(16) else v.toIntOrNull()
            if (point != null && Character.isValidCodePoint(point)) String(Character.toChars(point)) else m.value
        }.replace(Regex("\\s+"), " ").trim()
}
