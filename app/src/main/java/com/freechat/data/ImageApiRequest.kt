package com.freechat.data

import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

object ImageApiRequest {
    data class Reference(val bytes: ByteArray, val mime: String)

    fun usesSeedream(modelId: String, apiBaseUrl: String, builtinDoubao: Boolean): Boolean =
        builtinDoubao || modelId.contains("seedream", ignoreCase = true) ||
            apiBaseUrl.toHttpUrlOrNull()?.host?.let { it.startsWith("ark.") && it.endsWith(".volces.com") } == true

    fun endpoint(base: String, edit: Boolean): String {
        val root = base.trim().trimEnd('/').replace(Regex("/(?:images/(?:generations|edits)|chat/completions)$"), "")
        val suffix = if (edit) "images/edits" else "images/generations"
        return if (Regex("/(?:v[0-9]+|api/v[0-9]+)$", RegexOption.IGNORE_CASE).containsMatchIn(root)) "$root/$suffix"
            else "$root/v1/$suffix"
    }

    /** Seedream takes data URLs in JSON; OpenAI-compatible image edits take multipart image[]. */
    fun body(model: String, prompt: String, seedream: Boolean,
        references: List<Reference> = emptyList()): RequestBody {
        require(references.size <= 3)
        if (!seedream && references.isNotEmpty()) {
            return MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", model).addFormDataPart("prompt", prompt)
                .apply {
                    references.forEachIndexed { index, ref ->
                        val extension = when (ref.mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                        addFormDataPart("image[]", "reference_$index.$extension", ref.bytes.toRequestBody(ref.mime.toMediaType()))
                    }
                }.build()
        }
        // Keep provider defaults: even "auto" and n=1 are not universally supported.
        // Legacy model presets must never enter either JSON or multipart requests.
        val values = linkedMapOf<String, Any>("model" to model, "prompt" to prompt)
        if (references.isNotEmpty()) {
            val images = references.map { "data:${it.mime};base64,${java.util.Base64.getEncoder().encodeToString(it.bytes)}" }
            values["image"] = if (images.size == 1) images.single() else images
        }
        return Gson().toJson(values).toRequestBody("application/json; charset=utf-8".toMediaType())
    }
}
