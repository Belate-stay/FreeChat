package com.freechat.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 1.0.69：模型目录的「自动识别并获取」。
 *
 * 用户自定义模型什么服务商都可能有，官方预设没有统一标准 —— 所以这里只做**尽力解析**：
 * 按 OpenAI `/v1/models` 的返回结构读，顺带扫几个常见网关（OpenRouter / NewAPI 等）会带的
 * `context_length` / `supported_*` 元数据。**读得到就带出来，读不到就「获取失败」**，
 * 用户照样能手填（页面上有免责声明：不保证生效）。
 *
 * 端点解析沿用 ChatViewModel.customEndpoint 的同一套规则（根路径 / 带版本 / 完整端点都认），
 * 这里独立一份是因为编辑页拿不到 ModelInfo 也要能发请求。
 */
object ModelCatalog {

    /** 生图比例预设（官方通用集合；获取到官方比例列表时整组替换） */
    val IMAGE_ASPECTS = listOf("1:1", "4:3", "3:4", "16:9", "9:16", "2:3", "3:2")

    /** 生图分辨率预设：短边像素档（获取到官方显式尺寸时整组替换为 "宽x高"） */
    val IMAGE_RESOLUTIONS = listOf("1024", "1536", "2048")

    /**
     * 预设风格：key → 提示词附加词（模型认不认随缘，页面上有免责声明）。
     * UI 展示名走 AppStrings.imageStyleLabels（按 key 本地化），存进档案的是 key。
     */
    val STYLE_PROMPTS = linkedMapOf(
        "photo" to "写实照片风格",
        "anime" to "动漫插画风格",
        "oil" to "油画风格",
        "watercolor" to "水彩风格",
        "threeD" to "3D渲染风格",
        "flat" to "扁平插画风格",
        "pixel" to "像素风",
        "cyber" to "赛博朋克风格",
        "ink" to "水墨国风"
    )

    /** 获取到的一条模型目录项（能解析到什么就带什么，其余为空） */
    data class FetchedModel(
        val id: String,
        val aspectRatios: List<String> = emptyList(),
        val sizes: List<String> = emptyList(),
        val styles: List<String> = emptyList(),
        val supportsDeepThinking: Boolean = false,
        /** 1.0.75：网关声明了自带联网搜索（web_search 元数据）→ 内置联网优先、搜索管线只兜底 */
        val supportsNativeSearch: Boolean = false
    )

    /** 与 ChatViewModel.customEndpoint 同一套端点解析规则 */
    fun resolveUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trim().trimEnd('/')
        val endpoint = path.trim('/').substringAfter('/')
        return when {
            base.endsWith("/$endpoint") -> base
            hasApiVersionSegment(base) -> "$base/$endpoint"
            else -> base + path
        }
    }

    private fun hasApiVersionSegment(base: String): Boolean =
        Regex("""/(?:v[0-9]+|api/v[0-9]+)$""", RegexOption.IGNORE_CASE).containsMatchIn(base)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * GET {base}/models 尽力解析。
     * 失败（非 2xx / 结构不认识 / 一条都解析不出）一律走失败分支 —— 由页面显示「获取失败」。
     */
    suspend fun fetchModels(baseUrl: String, apiKey: String): Result<List<FetchedModel>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = resolveUrl(baseUrl, "/v1/models")
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get().build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val root = JsonParser.parseString(body).asJsonObject
                    val arr = root.getAsJsonArray("data") ?: root.getAsJsonArray("models")
                        ?: error("no model list")
                    val out = arr.mapNotNull { parseModel(it) }
                    if (out.isEmpty()) error("empty model list") else out
                }
            }
        }

    /**
     * 手输模型 ID 时的快速猜测（编辑页「添加大模型时自动识别」用，1.0.75）：
     * 没有网关元数据可看，只走 ID 启发式。猜的可能不准 —— 勾选框随时可手动改。
     */
    fun guessDeepThinking(id: String): Boolean =
        id.isNotBlank() && looksLikeDeepThinking(id, JsonObject(), JsonObject())

    /**
     * 深度思考能力启发式探测（1.0.74，适配所有大模型）：先看网关元数据有没有显式声明，
     * 没有就按模型 ID 特征猜（reasoner/r1/thinking/o系/QwQ 这类）。猜的可能不准 ——
     * 编辑页的勾选框可以手动改，探测只是省用户的事。
     */
    private fun looksLikeDeepThinking(id: String, meta: JsonObject, obj: JsonObject): Boolean {
        val caps = (meta.subObject("capabilities") ?: meta).toString().lowercase()
        if ("reasoning" in caps || "thinking" in caps || "deep_thinking" in caps) return true
        val declared = listOf(meta.optInt("reasoning"), obj.optInt("reasoning"),
            meta.optInt("supports_reasoning"), obj.optInt("supports_reasoning")).any { it == 1 }
        if (declared) return true
        val s = id.lowercase()
        return "reasoner" in s || "thinking" in s || "think" in s || "qwq" in s ||
            "o1" in s || "o3" in s || "o4" in s || s.contains("-r1") || s.endsWith("-r")
    }

    /** 单条模型元数据的尽力解析：id 必有，其余看缘分 */
    private fun parseModel(el: JsonElement): FetchedModel? {
        if (!el.isJsonObject) return null
        val obj = el.asJsonObject
        val id = obj.optString("id").orEmpty().ifBlank { obj.optString("model").orEmpty() }
        if (id.isBlank()) return null
        // 有的网关把元数据塞在 meta / 顶层，两处都扫
        val meta = obj.subObject("meta") ?: obj.subObject("metadata") ?: obj
        val ratios = meta.stringList("supported_aspect_ratios", "aspect_ratios", "supported_ratios")
        val sizes = meta.stringList("supported_sizes", "sizes", "supported_resolutions")
        val styles = meta.stringList("supported_styles", "styles", "style_presets")
        // 内置联网搜索（1.0.75）：只认显式声明（web_search 元数据/支持位），不瞎猜 ——
        // 猜错的代价是「不预搜 → 答完才发现没搜」的白跑一轮，宁可让用户手动勾
        val caps = (meta.subObject("capabilities") ?: meta).toString().lowercase()
        val nativeSearch = "web_search" in caps || "websearch" in caps ||
            meta.optInt("supports_web_search") == 1 || obj.optInt("supports_web_search") == 1
        return FetchedModel(id, ratios, sizes, styles, looksLikeDeepThinking(id, meta, obj), nativeSearch)
    }

    // ---- JSON 小工具（与 Wire.kt 同风格：裸强转会崩，一律可空解析） ----

    private fun JsonObject.optInt(key: String): Int =
        get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.let {
            runCatching { it.asInt }.getOrNull()
        } ?: 0

    private fun JsonObject.optString(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.let {
            runCatching { it.asString }.getOrNull()
        }

    private fun JsonObject.subObject(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.stringList(vararg keys: String): List<String> {
        for (k in keys) {
            val arr = get(k)?.takeIf { it.isJsonArray }?.asJsonArray ?: continue
            val out = arr.mapNotNull { e ->
                if (e.isJsonPrimitive) runCatching { e.asString }.getOrNull() else null
            }.filter { it.isNotBlank() }
            if (out.isNotEmpty()) return out
        }
        return emptyList()
    }

    /**
     * 生图 size 的最终取值："宽x高" 显式优先；"短边像素" 档按比例换算（min 边对齐短边，取整到 16 的倍数）。
     * 两者都没有（老数据）→ 沿用旧的写死默认（内置 Doubao 1920x1920 / 自定义 1024x1024）。
     */
    fun computeSize(aspectRatio: String, resolution: String, legacyDefault: String): String {
        val res = resolution.trim()
        if (res.isBlank() && aspectRatio.isBlank()) return legacyDefault
        // 显式 "宽x高"（官方预设 / 自定义）原样发 —— 换算反而会把官方档位改坏
        if (res.contains('x', ignoreCase = true)) return res.lowercase()
        val short = res.toIntOrNull() ?: return legacyDefault
        if (aspectRatio.isBlank()) return "${short}x${short}"
        val parts = aspectRatio.split(':')
        val rw = parts.getOrNull(0)?.toIntOrNull() ?: return "${short}x${short}"
        val rh = parts.getOrNull(1)?.toIntOrNull() ?: return "${short}x${short}"
        if (rw <= 0 || rh <= 0) return "${short}x${short}"
        val (w, h) = if (rw <= rh) short to (short * rh / rw) else (short * rw / rh) to short
        // 取整到 16 的倍数：不少生图服务按 16 对齐，奇数尺寸会被打回
        return "${snap16(w)}x${snap16(h)}"
    }

    private fun snap16(v: Int): Int = ((v + 8) / 16) * 16
}
