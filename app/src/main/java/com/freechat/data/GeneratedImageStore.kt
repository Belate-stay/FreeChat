package com.freechat.data

import com.freechat.sync.ImageCodec
import com.freechat.sync.ImageSync
import com.freechat.sync.Wire
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.File

/** Provider URLs are delivery links, not durable storage. Finish only after saving the bytes. */
object GeneratedImageStore {
    private const val MaxBytes = 24L * 1024 * 1024
    private val imageFields = setOf("imageUrls", "imagePaths", "quotedImagePath",
        "appearanceImagePath", "appearanceImagePaths")

    private fun remoteFile(url: String) = File(LocalStore.filesDir(),
        "gen_remote_${Wire.hashOf(url.toByteArray(Charsets.UTF_8))}.image")

    fun cachedSource(url: String): String? = if (url.startsWith("http://") || url.startsWith("https://")) {
        remoteFile(url).takeIf { it.isFile && it.length() > 0 }?.absolutePath
    } else null

    /** Call on IO. Never persist an HTML error page or mark a download as a finished image. */
    fun persist(source: ImageApiResponse.Source, client: OkHttpClient, targetAspect: Float? = null): String {
        if (source is ImageApiResponse.Source.Remote) cachedSource(source.url)?.let { return it }
        val bytes = when (source) {
            is ImageApiResponse.Source.Encoded -> source.bytes
            is ImageApiResponse.Source.Remote -> client.newCall(Request.Builder().url(source.url).build()).execute().use { response ->
                if (!response.isSuccessful) throw ApiFailure.fromResponse(response.code, "")
                val body = response.body ?: throw ApiFailure(200, "empty_image_result")
                if (body.contentLength() > MaxBytes) throw ApiFailure(413, "image_too_large")
                val buffer = Buffer()
                val input = body.source()
                while (!input.exhausted()) {
                    input.read(buffer, 8192)
                    if (buffer.size > MaxBytes) throw ApiFailure(413, "image_too_large")
                }
                buffer.readByteArray()
            }
        }
        if (bytes.isEmpty() || bytes.size > MaxBytes || !isImage(bytes)) throw ApiFailure(200, "invalid_image_result")
        // 供应商把非方形构图垫进方形画布时裁掉纯色垫边——先落本地的文件从源头就是真画幅。
        val stored = ImageCodec.trimUniformMargins(bytes, targetAspect) ?: bytes
        val file = when (source) {
            is ImageApiResponse.Source.Remote -> remoteFile(source.url)
            is ImageApiResponse.Source.Encoded -> File(LocalStore.filesDir(), "gen_${Wire.hashOf(stored)}.image")
        }
        LocalStore.writeBytes(file, stored)
        return file.absolutePath
    }

    private fun isImage(b: ByteArray): Boolean {
        fun ascii(start: Int, length: Int) = if (b.size >= start + length) String(b, start, length, Charsets.US_ASCII) else ""
        return (b.size > 24 && b.take(8).toByteArray().contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))) ||
            (b.size > 3 && b[0] == 0xff.toByte() && b[1] == 0xd8.toByte() && b[2] == 0xff.toByte()) ||
            ascii(0, 6) in setOf("GIF87a", "GIF89a") ||
            (ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP") ||
            (ascii(4, 4) == "ftyp" && ascii(8, 4) in setOf("avif", "avis", "heic", "heix", "mif1"))
    }

    /** Best-effort migration of retained legacy links; deletion always wins a racing download. */
    fun recover(url: String, client: OkHttpClient): String? {
        fun targets() = LocalStore.messageConvIds().map(LocalStore::messagesFile) + LocalStore.conversationsFile()
        fun contains(value: JsonElement, field: Boolean = false): Boolean = when {
            value.isJsonObject -> value.asJsonObject.entrySet().any { (key, child) -> contains(child, key in imageFields) }
            value.isJsonArray -> value.asJsonArray.any { contains(it, field) }
            field && value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString == url
            else -> false
        }
        fun retained() = targets().any { file -> runCatching {
            contains(JsonParser.parseString(LocalStore.readText(file) ?: "[]"))
        }.getOrDefault(false) }
        if (!LocalStore.locked { retained() }) return null
        val path = persist(ImageApiResponse.Source.Remote(url), client)
        return LocalStore.locked {
            if (!retained()) {
                ImageSync.releaseUnreferenced(listOf(path))
                return@locked null
            }
            fun replace(value: JsonElement, field: Boolean = false): JsonElement = when {
                value.isJsonObject -> value.asJsonObject.apply {
                    entrySet().toList().forEach { (key, child) -> add(key, replace(child, key in imageFields)) }
                }
                value.isJsonArray -> value.asJsonArray.apply {
                    for (index in 0 until size()) set(index, replace(get(index), field))
                }
                field && value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString == url ->
                    com.google.gson.JsonPrimitive(path)
                else -> value
            }
            targets().forEach { file -> LocalStore.transformText(file) { text ->
                val parsed = runCatching { JsonParser.parseString(text) }.getOrNull()
                if (parsed != null && contains(parsed)) replace(parsed).toString() else text
            } }
            path
        }
    }
}
