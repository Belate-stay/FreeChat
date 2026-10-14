package com.freechat.companion

import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 大脑侧单发联网检索（1.1.30X「默认开启联网搜索」）。
 *
 * 协议与 FreeChatServer search.js 的 ANYSEARCH 分支逐字一致（免 Key、匿名免费）：
 * POST {query, max_results, format:'json', language} → 解析出 {title,url,snippet} 列表。
 * 搜索是锦上添花：任何失败/超时一律返回空串（不挡回复生成），5s 硬超时。
 * 结果拼成「信息源」块并入上下文材料（与 App 的 SearchPresentation 折叠框同口径：
 * 只给材料不给指令，写不写进回复由人设自己决定）。
 */
object BrainSearch {
    private const val ENDPOINT = "https://api.anysearch.com/v1/search"
    private const val MAX_RESULTS = 5

    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    /** 检索 [query] 并拼成信息源块；失败/无结果返回空串。 */
    fun infoBlock(query: String): String = runCatching { search(query) }.getOrDefault("")

    private fun search(query: String): String {
        if (query.isBlank()) return ""
        val body = com.google.gson.JsonObject().apply {
            addProperty("query", query.take(200))
            addProperty("max_results", MAX_RESULTS)
            addProperty("format", "json")
            addProperty("language", if (query.contains(Regex("[一-鿿]"))) "zh-CN" else "en")
        }
        val req = Request.Builder().url(ENDPOINT)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(body.toString().toRequestBody(json))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return ""
            val raw = resp.body?.string().orEmpty()
            val results = runCatching {
                JsonParser.parseString(raw).asJsonObject.getAsJsonArray("results")
            }.getOrNull() ?: return ""
            val lines = results.mapNotNull { el ->
                val o = el.asJsonObject
                val title = o.get("title")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                val url = o.get("url")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                val snippet = o.get("snippet")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                if (title.isBlank() && snippet.isBlank()) null
                else "- ${title.take(80)}：${snippet.take(160)}（$url）"
            }.take(MAX_RESULTS)
            if (lines.isEmpty()) return ""
            return "\n\n【联网检索资料】\n" + lines.joinToString("\n")
        }
    }
}
