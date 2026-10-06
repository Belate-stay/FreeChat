package com.freechat.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Base64

/** Android-side compatibility for the Images API; never interpret a task ID as a finished image. */
object ImageApiResponse {
    sealed interface Source {
        data class Remote(val url: String) : Source
        data class Encoded(val bytes: ByteArray) : Source
    }
    private const val MaxImageBytes = 24 * 1024 * 1024

    fun parse(body: String, endpoint: String): List<Source> {
        if (body.trimStart().startsWith("<")) throw ApiFailure(200, "wrong_endpoint")
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrElse {
            throw ApiFailure(200, "invalid_image_result")
        }
        if (root.get("error")?.isJsonNull == false || root.string("status").lowercase() in listOf("failed", "error") ||
            root.string("success") == "false") throw ApiFailure.fromResponse(200, body)
        val data = root.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
        val rows = data?.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject }
            ?: if (root.string("type") in listOf("image_generation.completed", "image_edit.completed")) listOf(root) else emptyList()
        val sources = rows.mapNotNull { row ->
            // GPT image responses commonly contain url:null + b64_json. JSON null is not a string.
            val encoded = row.string("b64_json").ifBlank { row.string("url").takeIf { it.startsWith("data:", true) }.orEmpty() }
            decode(encoded)?.let { Source.Encoded(it) } ?: remote(row.string("url"), endpoint)?.let { Source.Remote(it) }
        }
        if (sources.isEmpty()) throw ApiFailure(200, "empty_image_result")
        return sources
    }

    private fun JsonObject.string(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()

    internal fun remote(value: String, endpoint: String): String? {
        val url = when {
            value.startsWith("/") || value.startsWith("./") || value.startsWith("../") -> endpoint.toHttpUrlOrNull()?.resolve(value)
            else -> value.toHttpUrlOrNull()
        }
        return url?.takeIf { it.username.isEmpty() && it.password.isEmpty() }?.toString()
    }

    internal fun decode(value: String): ByteArray? {
        if (value.isBlank() || value.length > MaxImageBytes * 4L / 3 + 4096) return null
        val raw = if (value.startsWith("data:", true)) {
            val comma = value.indexOf(',')
            if (comma < 0 || !value.substring(0, comma).matches(Regex("data:image/[A-Za-z0-9.+-]+;base64", RegexOption.IGNORE_CASE))) return null
            value.substring(comma + 1)
        } else value
        return runCatching { Base64.getDecoder().decode(raw.filterNot(Char::isWhitespace)) }
            .getOrNull()?.takeIf { it.isNotEmpty() && it.size <= MaxImageBytes }
    }
}
