package com.freechat.data

/** 原生联网也是可选工具：不把“模型未搜索/没有引用”误判成检索故障。 */
object NativeSearchPolicy {
    fun parameters(mimo: Boolean): Map<String, Any?> = if (mimo) mapOf("tools" to listOf(mapOf(
        "type" to "web_search", "force_search" to false, "max_keyword" to 3
    ))) else mapOf("enable_search" to true, "search_options" to mapOf(
        "forced_search" to false, "enable_source" to true, "enable_citation" to true
    ))

    fun failed(enabled: Boolean, searchError: String, apiError: String?): Boolean = enabled &&
        (searchError.isNotBlank() || (apiError.orEmpty().contains("API error 400") &&
            Regex("search|web_search|plugin", RegexOption.IGNORE_CASE).containsMatchIn(apiError.orEmpty())))
}
