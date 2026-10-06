package com.freechat.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class SearchProvider(val label: String, val defaultUrl: String) {
    FREE("免费公开搜索", ""),
    ANYSEARCH("AnySearch", "https://api.anysearch.com/v1/search"),
    TAVILY("Tavily", "https://api.tavily.com/search"),
    BRAVE("Brave Search", "https://api.search.brave.com/res/v1/web/search"),
    SEARXNG("SearXNG", ""),
    FIRECRAWL("Firecrawl", "https://api.firecrawl.dev/v2/search")
}

/** Local-only; never include this object in sync or model/character exports. */
data class SearchConfig(
    val provider: SearchProvider = SearchProvider.FREE,
    val endpoint: String = "",
    val apiKey: String = ""
) {
    fun validationError(): String? {
        if (provider == SearchProvider.FREE) return null
        val url = endpoint.trim().toHttpUrlOrNull() ?: return "请输入完整的 HTTPS 搜索接口地址"
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null)
            return "接口需使用 HTTPS，地址中不要包含密码、查询参数或片段"
        if (provider in listOf(SearchProvider.TAVILY, SearchProvider.BRAVE) && apiKey.isBlank()) return "请填写搜索服务的 API Key"
        if (apiKey.any { it == '\r' || it == '\n' }) return "API Key 不能包含换行"
        return null
    }
    // Do not accidentally log credentials through a generated data-class toString().
    override fun toString(): String = "SearchConfig(provider=$provider)"
}
