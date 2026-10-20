package com.freechat.data

import android.content.Context
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.Provider
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 内置模型云端目录（2026-10-18）：GET /api/ai/catalog ——「模型选择」页打开即拉，
 * 名称/ID/类型/备注以云端为准（云端把 DeepSeek 换成 Grok，页面就变），本地定义只做
 * 断网兜底。上游映射与 Key 在服务器端（cloud.json），这里只见显示字段。
 *
 * 权威口径：catalog 里出现的类型以 catalog 为准重建（同 id 保留本地能力位）；
 * catalog 没有的类型（音色设计/复刻这类常量特性）保留本地定义。
 */
object BuiltInCatalog {

    data class Entry(
        val id: String,
        val label: String,
        val type: String,
        val desc: String,
        // 云端配置的上游 URL（只给编辑页显示——内置请求恒走代理，见 BuiltInProxy；用户口径：填什么显示什么）
        val url: String = "",
        // 能力声明（云端勾选，2026-10-18）：null=没声明（用本地兜底能力位），true/false=云端为准
        val deepThinking: Boolean? = null,
        val webSearch: Boolean? = null
    )

    private const val PREFS = "built_in_catalog"
    private const val KEY_JSON = "json"

    private val client = OkHttpClient.Builder()
        .addInterceptor(BuiltInProxy.interceptor())
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cache: List<Entry>? = null

    /** 目录类型串 → ModelType（云配里 IMAGE 是 VISUAL 的别名） */
    fun typeOf(type: String): ModelType = when (type.trim().uppercase()) {
        "LANGUAGE" -> ModelType.LANGUAGE
        "VISUAL", "IMAGE" -> ModelType.VISUAL
        "VISION" -> ModelType.VISION
        "TTS" -> ModelType.TTS
        "ASR" -> ModelType.ASR
        else -> ModelType.LANGUAGE
    }

    // 解析走 Gson（项目既有的 JSON 库，单测可跑；org.json 在单测里是安卓桩会抛）
    fun parse(json: String): List<Entry> = runCatching {
        val arr = JsonParser.parseString(json).asJsonObject.get("models")?.asJsonArray ?: return emptyList()
        arr.map { e ->
            val m = e.asJsonObject
            Entry(
                id = m.get("id").asString,
                label = m.get("label")?.asString.orEmpty().ifBlank { m.get("id").asString },
                type = m.get("type")?.asString.orEmpty().ifBlank { "LANGUAGE" },
                desc = m.get("desc")?.asString.orEmpty(),
                url = m.get("url")?.asString.orEmpty(),
                deepThinking = m.get("deepThinking")?.takeIf { it.isJsonPrimitive }?.asBoolean,
                webSearch = m.get("webSearch")?.takeIf { it.isJsonPrimitive }?.asBoolean
            )
        }
    }.getOrDefault(emptyList())

    /** 目录 → ModelInfo：断网兜底用本地 [fallback] 的能力位，显示字段以目录为准，永远无 Key。 */
    fun toModelInfo(e: Entry, fallback: ModelInfo?): ModelInfo {
        val t = typeOf(e.type)
        val base = fallback ?: ModelInfo(
            id = e.id, displayName = e.label,
            provider = if (t == ModelType.TTS || t == ModelType.ASR) Provider.XIAOMI else Provider.CUSTOM,
            description = e.desc, apiBaseUrl = BuiltInProxy.BASE, apiKey = "",
            modelType = t,
            supportsWebSearch = t == ModelType.LANGUAGE,
            supportsDeepThinking = t == ModelType.LANGUAGE || t == ModelType.VISION,
            isBuiltIn = true
        )
        return base.copy(
            id = e.id,
            displayName = e.label,
            description = e.desc.ifBlank { fallback?.description ?: "" },
            // URL 栏显示云端配置的上游地址原样（用户口径：填什么显示什么）——
            // 只是显示：内置请求恒走代理 BuiltInProxy.BASE（customEndpoint 对 isBuiltIn 钉死），
            // 这里改坏也弄不断模型
            apiBaseUrl = e.url.ifBlank { BuiltInProxy.BASE },
            apiKey = "",
            isBuiltIn = true,
            // 能力声明：云端勾了才覆盖本地能力位（没声明保持兜底）
            supportsDeepThinking = e.deepThinking ?: base.supportsDeepThinking,
            supportsWebSearch = e.webSearch ?: base.supportsWebSearch
        )
    }

    /**
     * 合并（按类型）：catalog 里有该类型的条目 = 权威（换模型/下架实时生效，同 id 保留本地
     * 能力位）；catalog 没报这个类型 = 本地定义原样兜底。catalog 全空同理。
     */
    fun merge(local: List<ModelInfo>, catalog: List<Entry>, type: ModelType): List<ModelInfo> {
        val forType = catalog.filter { typeOf(it.type) == type }
        if (forType.isEmpty()) return local
        return forType.map { e -> toModelInfo(e, local.find { it.id == e.id }) }
    }

    /** 当前目录（内存缓存 → 落盘缓存）。 */
    fun entries(context: Context): List<Entry> {
        cache?.let { return it }
        val loaded = runCatching {
            parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, "") ?: "")
        }.getOrDefault(emptyList())
        cache = loaded
        return loaded
    }

    /** 拉一次云端目录。true = 有更新（调用方据此刷新列表）。失败静默=继续用兜底。 */
    suspend fun refresh(context: Context): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val resp = client.newCall(Request.Builder().url(BuiltInProxy.CATALOG_URL).get().build()).execute()
            resp.use {
                if (it.code != 200) return@runCatching false
                val text = it.body?.string() ?: return@runCatching false
                val list = parse(text)
                if (list.isEmpty()) return@runCatching false
                val changed = cache != list
                cache = list
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_JSON, text).apply()
                changed
            }
        }.getOrDefault(false)
    }
}
