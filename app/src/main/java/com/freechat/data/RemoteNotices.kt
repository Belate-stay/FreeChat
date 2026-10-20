package com.freechat.data

import android.content.Context
import com.freechat.BuildConfig
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 云端弹窗（2026-10-18）：GET /api/remote-notice —— 无需更新即可弹。两种样式：
 *  · alert      非强制提醒（「新版本更新」「角色冲突」同款外壳，可拒绝）；
 *  · fullscreen 强制全屏（「新功能介绍」同款：必须看完正文、必须选一个按钮）。
 * 正文支持 Markdown 超链接（可点跳转）。弹出与按钮选择经 POST /api/remote-notice/event
 * 留痕（站长看板：每个弹窗弹了几次、各按钮被选了几次）。
 *
 * 每台口径：maxImpressions = 最多弹几次（0=不限）；cooldownHours = 两次弹出的间隔小时。
 * 版本/平台筛选（target）在这里做，纯函数可测。
 */
object RemoteNotices {

    data class Button(
        val id: String,
        val label: String,
        val action: String,   // "dismiss" | "openUrl" | "navigate"
        val url: String,
        val page: String = "" // action=navigate 的落点页（settings/modelPool/about/changelog/drawer/…）
    )

    data class Notice(
        val id: String,
        val style: String,            // "alert" | "fullscreen"
        val trigger: String,          // "manual" | "update" | "copy" | "feature"（内置触发器的云控内容源）
        val enabled: Boolean,         // 内置触发器停用也下发——客户端靠它门控（缺位=回落本地默认）
        val title: String,
        val body: String,
        val buttons: List<Button>,
        val minVersion: String,       // 版本名区间（1.2.0.5 形态，空=不限）
        val maxVersion: String,
        val platform: String,
        val maxImpressions: Int,
        val cooldownHours: Int,
        val priority: Int
    )

    private const val PREFS = "remote_notices"
    private const val KEY_PREFIX_COUNT = "count_"     // 该弹窗在这台设备弹了几次
    private const val KEY_PREFIX_LAST = "last_"       // 上次弹出时刻

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .addInterceptor(BuiltInProxy.interceptor())
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** 三语字段挑当前语言（对象形态 {zh,en,ja}；也容忍纯字符串）。解析走 Gson（单测可跑）。 */
    internal fun pick(v: JsonElement?): String = when {
        v == null || v.isJsonNull -> ""
        v.isJsonPrimitive -> v.asString
        v.isJsonObject -> {
            val o = v.asJsonObject
            val lang = Locale.getDefault().language
            o.get(lang)?.asString.orEmpty().ifBlank { o.get("zh")?.asString.orEmpty() }
                .ifBlank { o.get("en")?.asString.orEmpty() }
        }
        else -> v.toString()
    }

    private fun str(m: JsonObject, key: String): String = m.get(key)?.asString.orEmpty()
    private fun intOr(m: JsonObject, key: String, def: Int): Int = m.get(key)?.asInt ?: def

    fun parse(json: String): List<Notice> = runCatching {
        val arr = JsonParser.parseString(json).asJsonObject.get("notices")?.asJsonArray ?: return emptyList()
        arr.map { parseOne(it.asJsonObject) }
    }.getOrDefault(emptyList())

    private fun parseOne(m: JsonObject): Notice {
        val target = m.get("target")?.asJsonObject ?: JsonObject()
        val buttons = m.get("buttons")?.asJsonArray
        return Notice(
            id = str(m, "id"),
            style = if (str(m, "style") == "fullscreen") "fullscreen" else "alert",
            trigger = str(m, "trigger").ifBlank { "manual" },
            enabled = m.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true,
            title = pick(m.get("title")),
            body = pick(m.get("body")),
            buttons = buttons?.map { b ->
                val x = b.asJsonObject
                Button(
                    id = str(x, "id").ifBlank { "b" },
                    label = pick(x.get("label")),
                    action = str(x, "action").ifBlank { "dismiss" },
                    url = str(x, "url"),
                    page = str(x, "page")
                )
            }?.ifEmpty { listOf(Button("dismiss", "知道了", "dismiss", "")) }
                ?: listOf(Button("dismiss", "知道了", "dismiss", "")), // 强制弹窗必须有出口钮
            minVersion = str(target, "minVersion"),
            maxVersion = str(target, "maxVersion"),
            platform = str(target, "platform"),
            maxImpressions = intOr(m, "maxImpressions", 0),
            cooldownHours = intOr(m, "cooldownHours", 0),
            priority = intOr(m, "priority", 0)
        )
    }

    /** 版本名打包成可比较数：「Beta 1.2.0.5」→ 1_002_000_005（每段 0-999，缺省 0）。 */
    fun packVersion(name: String): Long {
        val cleaned = name.trim().removePrefix("Beta").removePrefix("Version").trim()
        val parts = cleaned.split('.').map { seg -> seg.filter { it.isDigit() }.toIntOrNull() ?: 0 }
        val p = List(4) { parts.getOrElse(it) { 0 } }
        return p[0] * 1_000_000_000L + p[1] * 1_000_000L + p[2] * 1_000L + p[3]
    }

    /** 版本/平台筛选：只认安卓端，版本名落在区间里才弹（区间空=不限）。 */
    fun targets(n: Notice, versionName: String, platform: String = "android"): Boolean {
        val v = packVersion(versionName)
        return (n.platform.isBlank() || n.platform.equals(platform, ignoreCase = true)) &&
            (n.minVersion.isBlank() || v >= packVersion(n.minVersion)) &&
            (n.maxVersion.isBlank() || v <= packVersion(n.maxVersion))
    }

    /**
     * 弹出资格：过了版本筛选 + 没超 maxImpressions + 过了 cooldown。
     * 只看数据说话，排序（priority 降序）也在这里给。
     */
    fun eligible(
        list: List<Notice>,
        versionName: String,
        impressionCount: (String) -> Int,
        lastShownAt: (String) -> Long,
        now: Long
    ): List<Notice> = list
        .filter { targets(it, versionName) }
        .filter { it.maxImpressions <= 0 || impressionCount(it.id) < it.maxImpressions }
        .filter { it.cooldownHours <= 0 || lastShownAt(it.id) <= 0L || now - lastShownAt(it.id) >= it.cooldownHours * 3_600_000L }
        .sortedByDescending { it.priority }

    // ---- 每台设备的弹出记账（本地，云端负责统计口径）----

    fun impressionCount(context: Context, id: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_PREFIX_COUNT + id, 0)

    fun lastShownAt(context: Context, id: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_PREFIX_LAST + id, 0L)

    fun markShown(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_PREFIX_COUNT + id, impressionCount(context, id) + 1)
            .putLong(KEY_PREFIX_LAST + id, System.currentTimeMillis())
            .apply()
    }

    // ---- 网络 ----

    /** 拉云端弹窗（失败静默=这轮不弹）。 */
    suspend fun fetch(): List<Notice> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(BuiltInProxy.REMOTE_NOTICE_URL).get().build()).execute()
                .use { resp -> if (resp.code == 200) parse(resp.body?.string() ?: "") else emptyList() }
        }.getOrDefault(emptyList())
    }

    /** 弹出/选择留痕。event='impression' | 'choice'。尽力而为，失败不打扰。 */
    suspend fun report(noticeId: String, event: String, choice: String = "") {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val body = JsonObject().apply {
                    addProperty("noticeId", noticeId)
                    addProperty("event", event)
                    addProperty("choice", choice)
                    addProperty("version", BuildConfig.VERSION_NAME)
                    addProperty("versionCode", BuildConfig.VERSION_CODE)
                }.toString()
                client.newCall(
                    Request.Builder().url(BuiltInProxy.REMOTE_NOTICE_URL + "/event")
                        .post(body.toRequestBody(JSON_MEDIA)).build()
                ).execute().use { }
            }
        }
    }
}
