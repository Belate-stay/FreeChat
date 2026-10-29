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

/** 一次模型调用的回执（文本 + 账本要的量：模型串/耗时/usage，2026-10-22 微信进账本） */
data class ChatResult(
    val text: String,
    /** 上游请求里发出去的模型串（账本 upstream 列；model 列恒记「微信通道」） */
    val upstreamModel: String = "",
    val ms: Long = 0,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val toolCalls: List<Map<String, Any?>> = emptyList(),
    val reasoningContent: String = ""
)

/** 模型调用面（可注入——测试用假实现；线上是 [MimoChatCompleter]） */
interface ChatCompleter {
    /**
     * 返回 choices[0].message.content 文本与 usage；非 2xx / 解析失败抛异常。
     * 协程取消必须立刻中断在途请求（1.0.99.4 过期闸门靠它掐掉被并批的生成）。
     */
    suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?> = emptyMap()): ChatResult
}

/**
 * 内置 DeepSeek-V4.1-Flash（deepseek-flash）代调（1.1.30X 换代，替代 mimo-v2.6-flash）。
 * OpenAI 兼容 chat/completions，非流式；深度思考「尽力开/关」参数组照 [com.freechat.core.CompanionPrompts.deepThinkExtras]。
 * 可取消（enqueue + invokeOnCancellation → call.cancel()）：过期闸门一掐就断，
 * 被并批的旧生成不白烧 180 秒、也不挡新批的锁。
 *
 * 2026-10-22：上游 Key/URL/模型改吃 cloud.json 模型池的 BRAIN_MODEL 条目（看板「模型管理」热改），
 * 构造参数退为默认兜底——配置读不到时行为与旧版（env key + 内置地址）一字不变。
 */
class MimoChatCompleter(
    private val baseUrl: String = "https://api.deepseek.com",
    private val apiKey: String,
    private val modelId: String = "deepseek-flash"
) : ChatCompleter {

    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)   // 深度推理模型给足等待（与 App 的 180s 同口径）
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>): ChatResult =
        suspendCancellableCoroutine { cont ->
            // 逐次现读 cloud.json（看板「模型管理」保存即生效，不必重启本服务）
            val cfg = BrainUpstream.resolve(BrainUpstream.Resolved(CompanionBrain.BRAIN_MODEL.id, baseUrl, modelId, apiKey))
            val payload = JsonObject()
            payload.addProperty("model", cfg.modelId)
            payload.add("messages", com.google.gson.Gson().toJsonTree(messages))
            payload.addProperty("stream", false)
            payload.addProperty("temperature", temperature)
            for ((k, v) in extras) payload.add(k, com.google.gson.Gson().toJsonTree(v))
            val t0 = System.currentTimeMillis()
            val call = client.newCall(
                Request.Builder().url(BrainUpstream.requestUrl(cfg.baseUrl, cfg.path))
                    .addHeader("Authorization", "Bearer ${cfg.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(json))
                    .build()
            )
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("API error: ${e.message}"))
                }

                override fun onResponse(call: Call, resp: Response) {
                    try { resp.use { r ->
                        if (!cont.isActive) return
                        val body = r.body?.string().orEmpty()
                        if (!r.isSuccessful) {
                            cont.resumeWithException(IllegalStateException("API error ${r.code}: ${body.take(200)}"))
                            return
                        }
                        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrElse {
                            cont.resumeWithException(IllegalStateException("API parse error: ${body.take(200)}"))
                            return
                        }
                        val content = runCatching {
                            root.getAsJsonArray("choices")?.get(0)?.asJsonObject
                                ?.getAsJsonObject("message")?.get("content")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
                        }.getOrElse {
                            cont.resumeWithException(IllegalStateException("API parse error: ${body.take(200)}"))
                            return
                        }
                        // usage（DeepSeek 非流式在顶层）只作记账，抠不到记 0，不挡回复
                        val usage = root.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject
                        val message = root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject?.getAsJsonObject("message")
                        val calls = message?.get("tool_calls")?.takeIf { it.isJsonArray }?.let {
                            com.google.gson.Gson().fromJson<List<Map<String, Any?>>>(it,
                                object : com.google.gson.reflect.TypeToken<List<Map<String, Any?>>>() {}.type)
                        }.orEmpty()
                        cont.resume(ChatResult(
                            text = content,
                            upstreamModel = cfg.modelId,
                            ms = System.currentTimeMillis() - t0,
                            promptTokens = usage?.get("prompt_tokens")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                            completionTokens = usage?.get("completion_tokens")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                            toolCalls = calls,
                            reasoningContent = message?.get("reasoning_content")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                        ))
                    } } catch (e: Exception) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }
            })
        }
}
