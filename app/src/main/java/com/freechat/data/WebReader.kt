package com.freechat.data

import kotlinx.coroutines.CancellationException
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object WebReader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2500, TimeUnit.MILLISECONDS)
        .callTimeout(2800, TimeUnit.MILLISECONDS)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
            if (addresses.any { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress || (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc) })
                throw UnknownHostException("Only public web sources are supported")
            }
        }).build()

    suspend fun fetchText(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return ""
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty() || parsed.port !in listOf(80, 443)) return ""
        return try {
            val html = client.newCall(Request.Builder().url(parsed)
                .header("User-Agent", "FreeChat/1.0.76 (web source reader)").build()).awaitText()
            extractText(html)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { "" }
    }

    internal fun extractText(html: String): String {
        val cleaned = html.replace(Regex("<(script|style|nav|header|footer|noscript|svg)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), " ")
        val article = Regex("<(article|main)\\b[^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(cleaned)?.groupValues?.get(2) ?: cleaned
        return article.replace(Regex("</?(p|div|br|li|h[1-6]|tr)\\b[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .split('\n').map(SearchSource::cleanHtml).filter { it.length >= 20 }.joinToString("\n").take(5000)
    }
}
