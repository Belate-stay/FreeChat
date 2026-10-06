package com.freechat.companion

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 模型调用面（可注入——测试用假实现；线上是 [MimoChatCompleter]） */
interface ChatCompleter {
    /**
     * 返回 choices[0].message.content 文本；非 2xx / 解析失败抛异常。
     * 协程取消必须立刻中断在途请求（1.0.99.4 过期闸门靠它掐掉被并批的生成）。
     */
    suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?> = emptyMap()): String
}

/**
 * 内置 mimo-v2.6-flash 代调（方案 v2 产品第 3 条：凭据零配置、成本用户自担）。
 * OpenAI 兼容 chat/completions，非流式；深度思考「尽力关」参数组照 [com.freechat.core.CompanionPrompts.deepThinkExtras]。
 * 可取消（enqueue + invokeOnCancellation → call.cancel()）：过期闸门一掐就断，
 * 被并批的旧生成不白烧 180 秒、也不挡新批的锁。
 */
class MimoChatCompleter(
    private val baseUrl: String = "https://api.xiaomimimo.com/v1",
    private val apiKey: String,
    private val modelId: String = "mimo-v2.6-flash"
) : ChatCompleter {

    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)   // 深度推理模型给足等待（与 App 的 180s 同口径）
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>): String =
        suspendCancellableCoroutine { cont ->
            val payload = JsonObject()
            payload.addProperty("model", modelId)
            payload.add("messages", com.google.gson.Gson().toJsonTree(messages))
            payload.addProperty("stream", false)
            payload.addProperty("temperature", temperature)
            for ((k, v) in extras) payload.add(k, com.google.gson.Gson().toJsonTree(v))
            val call = client.newCall(
                Request.Builder().url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(json))
                    .build()
            )
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(IllegalStateException("API error: ${e.message}"))
                }

                override fun onResponse(call: Call, resp: Response) {
                    resp.use { r ->
                        val body = r.body?.string().orEmpty()
                        if (!r.isSuccessful) {
                            cont.resumeWithException(IllegalStateException("API error ${r.code}: ${body.take(200)}"))
                            return
                        }
                        val content = runCatching {
                            JsonParser.parseString(body).asJsonObject
                                .getAsJsonArray("choices")?.get(0)?.asJsonObject
                                ?.getAsJsonObject("message")?.get("content")?.asString?.trim().orEmpty()
                        }.getOrElse {
                            cont.resumeWithException(IllegalStateException("API parse error: ${body.take(200)}"))
                            return
                        }
                        cont.resume(content)
                    }
                }
            })
        }
}
