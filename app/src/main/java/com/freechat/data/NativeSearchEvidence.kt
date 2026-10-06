package com.freechat.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Read provider metadata, never infer successful retrieval from the model's prose. */
internal object NativeSearchEvidence {
    fun citations(root: JsonObject): List<Pair<String, String>> {
        val sources = linkedMapOf<String, String>()
        fun collect(value: JsonElement?) {
            if (value == null || value.isJsonNull) return
            if (value.isJsonArray) { value.asJsonArray.forEach(::collect); return }
            if (!value.isJsonObject) return
            val obj = value.asJsonObject
            fun text(key: String) = obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val url = text("url").toHttpUrlOrNull()
            if (url != null) sources[url.toString()] = text("title").ifBlank { text("site_name") }.ifBlank { url.host }
            // MiMo: flat annotations; OpenAI-compatible: nested url_citation; DashScope: search_results.
            listOf("annotations", "url_citation", "search_results", "search_info").forEach { collect(obj.get(it)) }
        }
        collect(root.get("search_info"))
        collect(root.get("search_results"))
        collect(root.get("annotations"))
        root.getAsJsonArray("choices")?.forEach { choice ->
            if (choice.isJsonObject) {
                listOf("delta", "message").forEach { key ->
                    val item = choice.asJsonObject.get(key)
                    if (item?.isJsonObject == true) {
                        collect(item.asJsonObject.get("annotations"))
                        collect(item.asJsonObject.get("search_info"))
                    }
                }
            }
        }
        return sources.map { (url, title) -> title to url }
    }
}
