package com.freechat.companion

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * 微信大脑的上游模型配置（2026-10-22）：吃 cloud.json 模型池 ——
 * 优先 id='wechat' 的**微信专属条目**（看板「模型管理」带「微信」标，独立 Key），
 * 逐字段兜底吃 id='deepseek-flash' 条目，再兜底吃构造默认（env 时代的旧行为）。
 * 每次调用现读，看台保存下一秒生效，不必重启本服务；配置坏掉不挡回复。
 * 字段口径与 Node 代理一致：key = upstream.apiKey 优先、退回 upstream.keyEnv 对应环境变量；
 * base 填完整接口地址（…/chat/completions 结尾）就原样请求，填根地址才补接口段。
 */
object BrainUpstream {
    /** 与 Node 的 FREECHAT_CLOUD_CONFIG 同一口径（默认 /var/lib/freechat/cloud.json） */
    internal var configPathOverride: String? = null
    private val configPath: String
        get() = configPathOverride ?: System.getenv("FREECHAT_CLOUD_CONFIG") ?: "/var/lib/freechat/cloud.json"

    /** 微信专属条目的保留 id（normalize 种子同名；看板「微信」标也认它） */
    const val WECHAT_ENTRY_ID = "wechat"

    data class Resolved(
        /** 生效条目的池 id（账本 model 列；wechat 条目配置了就记 'wechat'，Tokens消耗 里微信独立成行） */
        val entryId: String,
        val baseUrl: String,
        val modelId: String,
        val apiKey: String,
        /** upstream.path：手写在 cloud.json 里的接口段（表单不设），空 = /chat/completions */
        val path: String = ""
    )

    /** 每次调用现读（人聊频率下这文件很小，比缓存省心；改配置下一秒就生效） */
    fun resolve(defaults: Resolved): Resolved {
        val cfg = parse() ?: return defaults
        return Resolved(
            entryId = cfg.entryId.ifEmpty { defaults.entryId },
            baseUrl = cfg.baseUrl.ifEmpty { defaults.baseUrl },
            modelId = cfg.modelId.ifEmpty { defaults.modelId },
            apiKey = cfg.apiKey.ifEmpty { defaults.apiKey },
            path = cfg.path.ifEmpty { defaults.path }
        )
    }

    private fun parse(): Resolved? = runCatching {
        val root = JsonParser.parseString(File(configPath).readText(Charsets.UTF_8)).asJsonObject
        val models = root.getAsJsonArray("models") ?: return null
        fun up(id: String): JsonObject? = models.firstOrNull { el ->
            el?.asJsonObject?.str("id") == id
        }?.asJsonObject?.getAsJsonObject("upstream")
        val wechat = up(WECHAT_ENTRY_ID)
        val flash = up(CompanionBrain.BRAIN_MODEL.id)
        // 「独立生效」的判据是**有自己的 Key**（用户拍板「单独设置 key」）——
        // 种子条目预填的 base/model 只是表单便利，不算独立；没 Key 整套兜底吃 flash 条目
        val wechatOn = keyOf(wechat).isNotEmpty()
        val primary = if (wechatOn) wechat else flash
        val fallback = if (wechatOn) flash else null
        Resolved(
            entryId = when {
                wechatOn -> WECHAT_ENTRY_ID
                flash != null -> CompanionBrain.BRAIN_MODEL.id
                else -> ""
            },
            baseUrl = primary.str("base").ifEmpty { fallback.str("base") },
            modelId = primary.str("model").ifEmpty { fallback.str("model") },
            apiKey = keyOf(primary).ifEmpty { keyOf(fallback) },
            path = primary.str("path").ifEmpty { fallback.str("path") }
        )
    }.getOrNull()

    private fun keyOf(up: JsonObject?): String {
        val inline = up.str("apiKey")
        if (inline.isNotEmpty()) return inline
        val env = up.str("keyEnv")
        return if (env.isEmpty()) "" else System.getenv(env).orEmpty().trim()
    }

    private fun JsonObject?.str(k: String): String =
        this?.get(k)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().trim()

    /** 与 Node 的 requestUrl 同语义：完整接口地址原样用，根地址才补接口段（绝不自动加 /v1） */
    internal fun requestUrl(base: String, path: String = ""): String {
        val b = base.trim().trimEnd('/')
        return if (ENDPOINT_SUFFIX.containsMatchIn(b)) b else b + (path.ifEmpty { "/chat/completions" })
    }

    private val ENDPOINT_SUFFIX =
        Regex("/(?:chat/completions|images/(?:generations|edits)|audio/(?:speech|transcriptions))/?$", RegexOption.IGNORE_CASE)
}
