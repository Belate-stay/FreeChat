package com.freechat.data

import android.util.Log
import com.freechat.BuildConfig
import com.freechat.i18n.LocaleManager
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * SerpAPI 多 key 池 —— 「哪个有额度就用哪个，用完了自动换下一个」。
 *
 * 背景：SerpAPI 免费版是 **250 次/月/账号**（付费版很贵，作者不打算买），一个 key
 * 一个月根本不够用。所以注册多个免费账号、把 key 串成一个池子，理论额度 = 250 × key 数。
 *
 * 三条设计要点：
 *
 * 1. **不靠猜，靠查**。每个 key 的剩余额度可以直接问 SerpAPI 的 `/account` 接口
 *    （**不消耗搜索次数**，实测 1.3s 返回），字段 `plan_searches_left`、
 *    `plan_renewal_date`。所以进 App 先查一遍余额，把「哪个 key 有额度」变成已知量，
 *    而不是靠发一次搜索去撞 429。查到的结果缓存 [BALANCE_TTL_MS]，过期或撞到 429 再查。
 *
 * 2. **429 要分清是哪一种**。SerpAPI 的 429 有两种完全不同的含义，body 里的 error
 *    文案是唯一可靠区分：
 *      - `run out of searches`   → **额度用完**，这个 key 到 `plan_renewal_date` 前都没用，标记停用
 *      - `exceeded the hourly rate limit` → **限速**，额度还在，只是请求太快，换 key 或稍等即可
 *    原来代码只看 `!response.isSuccessful` 就返回空串，两种情况都变成「联网莫名其妙不灵」。
 *
 * 3. **不能静默失败**。全池都没额度时必须让用户看见原因（[SerpOutcome.errorText]），
 *    否则表现就是「AI 答得驴唇不对马嘴，但没有任何提示」——这正是之前用户报的
 *    「联网连不上」时最难受的地方。
 *
 * 加账号的步骤（三处，别漏）：secrets.properties 加一行 `SERPAPI_API_KEY_3` →
 * app/build.gradle.kts 加一条 buildConfigField → 下面的 [KEYS] 里加一个引用。
 */
object SerpApiPool {

    private const val TAG = "FreeChat"
    private const val BASE_URL = "https://serpapi.com/search"
    private const val ACCOUNT_URL = "https://serpapi.com/account"

    /** 余额查询结果的保鲜期：10 分钟内不重复查 */
    private const val BALANCE_TTL_MS = 10 * 60 * 1000L

    /** 搜索结果缓存 TTL：同一个问题短时间内重复问，不重复烧额度 */
    private const val CACHE_TTL_MS = 20 * 60 * 1000L
    private const val CACHE_MAX = 64

    /** 同一个 key 两次请求的最小间隔（免费版有每秒/每小时的速率限制，避免误撞 429） */
    private const val MIN_KEY_INTERVAL_MS = 1100L

    /** key 名单：顺序即优先级 */
    private val KEYS: List<String> = listOfNotNull(
        BuildConfig.SERPAPI_API_KEY.takeIf { it.isNotBlank() },
        BuildConfig.SERPAPI_API_KEY_2.takeIf { it.isNotBlank() },
    )

    val hasKeys: Boolean get() = KEYS.isNotEmpty()

    /** 单个 key 的运行时状态 */
    private class KeyState(
        /** 上次查到的剩余次数；-1 = 未知 */
        @Volatile var remaining: Int = -1,
        /** 额度恢复时间（epoch ms）；0 = 没有停用 */
        @Volatile var resetAt: Long = 0L,
        /** 该 key 归属的账号，便于日志区分 */
        @Volatile var account: String = "",
        @Volatile var lastUsedAt: Long = 0L,
        @Volatile var checkedAt: Long = 0L,
    ) {
        /**
         * 这个 key 现在还能不能用。
         *
         * 注意 `remaining == 0` 只在**不知道恢复日**时才等于停用：额度是按月重置的，
         * 本地这个 0 到了下个月就成了过期结论 —— 若还按它停用，账号早就满血复活了，
         * App 也会永远认为它没额度（免费版每月自动重置，用户不会手动来改设置）。
         * 所以只要有恢复日，就以恢复日为准：过了恢复日就当作「可用」，让它去试一次，
         * 试出来要么成功、要么再次 429 并刷新恢复日，自己会收敛。
         */
        fun exhausted(now: Long): Boolean =
            if (resetAt > 0L) now < resetAt else remaining == 0
    }

    private val states: Map<String, KeyState> = KEYS.associateWith { KeyState() }

    /** 命中缓存的结果：query(规范化) → (写入时间, 文本) */
    private val cache = object : LinkedHashMap<String, Pair<Long, String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?) =
            size > CACHE_MAX
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * 余额接口专用客户端。`/account` 只是个轻量查询，却和搜索共用 12 秒超时的话，
     * 网络不通时「进 App 预热 + 每次搜索前的余额核对」会白白拖住十几秒 —— 用户感知
     * 就是「一按发送就卡住」。这里压到 4 秒：查不到就当未知，照样去搜。
     */
    private val balanceClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    /** 全池均无额度时给用户看的文案（null = 还有可用 key） */
    @Volatile
    private var lastFatalError: String? = null

    /**
     * 进 App 时预热一次余额（`/account` 不耗搜索次数）。
     * 失败不抛异常：查不到就当「未知」，搜索时仍会尝试。
     */
    suspend fun warmUp() {
        if (!hasKeys) return
        withContext(Dispatchers.IO) {
            refreshBalances(force = true)
        }
    }

    /**
     * 查一遍所有 key 的余额。免费接口，可以放心调。
     * @param force 忽略保鲜期强制刷新
     */
    private fun refreshBalances(force: Boolean) {
        val now = System.currentTimeMillis()
        KEYS.forEach { key ->
            val st = states[key] ?: return@forEach
            if (!force && now - st.checkedAt < BALANCE_TTL_MS) return@forEach
            // 先记「查过」，再真去查：查失败（断网/超时）时如果不落这个时间戳，
            // 之后每一次搜索都会重新在这里等满 4 秒 × key 数，用户感知就是「按了发送卡住」
            st.checkedAt = now
            try {
                val req = Request.Builder().url("$ACCOUNT_URL?api_key=$key").build()
                balanceClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@forEach
                    val obj = JsonParser.parseString(resp.body?.string() ?: return@forEach).asJsonObject
                    val left = obj.get("plan_searches_left")?.takeUnless { it.isJsonNull }?.asInt ?: -1
                    val extra = obj.get("extra_credits")?.takeUnless { it.isJsonNull }?.asInt ?: 0
                    val total = if (left < 0) -1 else left + extra
                    st.remaining = total
                    st.account = obj.get("account_email")?.takeUnless { it.isJsonNull }?.asString ?: ""
                    // 额度为 0 时记下恢复日期，用来给用户一句准确的话（「X 月 X 日重置」）
                    if (total == 0) {
                        // 解析不出日期也得给个恢复日：没有恢复日 → exhausted() 会退化成
                        // 「remaining==0 就是永远停用」，账号下个月刷新了额度也救不回来
                        st.resetAt = parseRenewal(
                            obj.get("plan_renewal_date")?.takeUnless { it.isJsonNull }?.asString
                        ).takeIf { it > now } ?: (now + 12 * 3600_000L)
                    } else {
                        st.resetAt = 0L
                    }
                    Log.d(TAG, "SerpAPI balance: ${st.account} left=$total reset=${st.resetAt}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "SerpAPI balance check failed: ${e.message}")
            }
        }
        // 查完统一刷新一次「全池是否可用」的结论
        val usable = KEYS.any { states[it]?.exhausted(now) != true }
        lastFatalError = if (usable) null else buildExhaustedMessage(now)
    }

    /** 把 `2026-09-30` 解析成 epoch ms；解析不了返回 0 */
    private fun parseRenewal(date: String?): Long {
        if (date.isNullOrBlank()) return 0L
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .parse(date.trim())?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun buildExhaustedMessage(now: Long): String {
        val earliest = KEYS.mapNotNull { states[it]?.resetAt?.takeIf { t -> t > now } }.minOrNull()
        val s = LocaleManager.strings()
        val whenStr = earliest?.let {
            java.text.SimpleDateFormat(s.dateMd, java.util.Locale.getDefault()).format(java.util.Date(it))
        }
        return if (whenStr != null) {
            s.webQuotaExhausted(KEYS.size, whenStr)
        } else {
            s.webQuotaExhaustedNoDate
        }
    }

    /** 供 UI/日志展示的一句话状态 */
    fun quotaSummary(): String =
        KEYS.joinToString(" | ") { k ->
            val st = states[k] ?: return@joinToString ""
            "${st.account.ifBlank { "key" }}: ${if (st.remaining < 0) "未知" else "${st.remaining} 次"}"
        }

    /**
     * 给设置页显示的一句话额度：`剩余 480 次（2 个账号）`。
     * 会强制刷新一次余额（免费接口，不耗搜索次数），所以只在设置页可见时调用。
     *
     * 1.0.50：设置页那一行已经不再显示额度了（用户要求去掉，见 SettingsScreen 联网搜索），
     * 这个函数暂时没人调 —— 留着是因为余额接口、`webQuota*` 那几条文案都还在，
     * 以后想在别处（比如搜索失败提示里）报额度时直接用得上。
     */
    @Suppress("unused")
    suspend fun statusLine(): String = withContext(Dispatchers.IO) {
        val s = LocaleManager.strings()
        if (!hasKeys) return@withContext s.webQuotaNoKey
        refreshBalances(force = true)
        val known = KEYS.mapNotNull { states[it]?.remaining?.takeIf { r -> r >= 0 } }
        if (known.isEmpty()) return@withContext s.webQuotaUnknown
        s.webQuotaRemaining(known.sum(), KEYS.size)
    }

    /**
     * 搜索一次。会自动挑有额度的 key、撞到额度用完自动换下一个、命中缓存直接返回。
     *
     * @return [SerpOutcome]：text 空表示没搜到（原因看 kind/errorText）
     */
    suspend fun search(rawQuery: String): SerpOutcome = withContext(Dispatchers.IO) {
        if (!hasKeys) return@withContext SerpOutcome("", SerpErrorKind.NO_KEY, LocaleManager.strings().webNoKeyConfigured)

        val cacheKey = rawQuery.trim().lowercase()
        if (cacheKey.isEmpty()) return@withContext SerpOutcome("", SerpErrorKind.EMPTY)
        synchronized(cache) {
            cache[cacheKey]?.let { (at, text) ->
                if (System.currentTimeMillis() - at < CACHE_TTL_MS) {
                    Log.d(TAG, "SerpAPI cache hit: ${rawQuery.take(40)}")
                    return@withContext SerpOutcome(text)
                }
                cache.remove(cacheKey)
            }
        }

        val now = System.currentTimeMillis()
        // 只在「一个 key 的余额都不知道」时才去核对。
        // 余额已知的情况下每搜一次都多跑一趟 /account，是白白给每次联网加 1~2 秒延迟；
        // 本地估算与真实额度的偏差由 429 分支兜底纠正（撞到就换 key，不会卡死）。
        if (KEYS.all { (states[it]?.remaining ?: -1) < 0 }) refreshBalances(force = false)

        // 候选顺序：有额度的优先（按剩余从多到少），未知的垫后 —— 未知不代表没有，仍要试
        val ordered = KEYS.sortedByDescending { states[it]?.remaining ?: -1 }
        var rateLimited = false
        var networkError = ""

        for (key in ordered) {
            val st = states[key] ?: continue
            val t = System.currentTimeMillis()
            if (st.exhausted(t)) continue
            // 同 key 限速：两次请求别贴太近
            val wait = MIN_KEY_INTERVAL_MS - (t - st.lastUsedAt)
            if (wait > 0) kotlinx.coroutines.delay(wait)

            val text = fetch(key, rawQuery) ?: continue
            when (text.kind) {
                SerpErrorKind.NO_QUOTA -> {
                    // 额度用完 → 该 key 停用到恢复日，立刻换下一个
                    st.remaining = 0
                    st.checkedAt = t
                    st.resetAt = if (text.resetAt > 0) text.resetAt else t + 6 * 3600_000L
                    Log.w(TAG, "SerpAPI key exhausted (${st.account}), switching to next")
                    continue
                }
                SerpErrorKind.RATE_LIMIT -> {
                    rateLimited = true
                    st.lastUsedAt = t
                    Log.w(TAG, "SerpAPI rate limited (${st.account}), trying next key")
                    continue
                }
                SerpErrorKind.NETWORK -> {
                    networkError = text.errorText
                    st.lastUsedAt = t
                    continue
                }
                SerpErrorKind.EMPTY -> {
                    // 请求成功但没结果：这是「真的没搜到」，不是错误，不必换 key 重试
                    st.lastUsedAt = t
                    st.remaining = (st.remaining - 1).coerceAtLeast(0)
                    return@withContext SerpOutcome("", SerpErrorKind.EMPTY)
                }
                SerpErrorKind.NONE -> {
                    st.lastUsedAt = t
                    st.remaining = (st.remaining - 1).coerceAtLeast(0)
                    synchronized(cache) { cache[cacheKey] = System.currentTimeMillis() to text.text }
                    return@withContext SerpOutcome(text.text)
                }
                // NO_KEY 在这里不可能出现（fetch 只返回上面四种），补齐只为穷尽分支
                SerpErrorKind.NO_KEY -> continue
            }
        }

        // 全池走完都没拿到结果：给出准确原因（这是「联网不灵」时用户该看到的话）
        val usableLeft = KEYS.any { states[it]?.exhausted(System.currentTimeMillis()) != true }
        lastFatalError = if (usableLeft) null else buildExhaustedMessage(System.currentTimeMillis())
        val s = LocaleManager.strings()
        val err = when {
            // 还有可用 key 却没结果 → 多半是网络/限速，别说成「额度用完」
            usableLeft && networkError.isNotEmpty() -> s.webRequestFailed(networkError)
            usableLeft && rateLimited -> s.webTooFrequent
            lastFatalError != null -> lastFatalError!!
            else -> s.webUnavailable
        }
        SerpOutcome("", SerpErrorKind.NETWORK, err)
    }

    /** 底层单次请求（含结果解析）。返回 kind=NONE 时 raw 是注入给模型的文本 */
    private fun fetch(key: String, query: String): SerpOutcome? = try {
        // 时效性检测：时间敏感查询 → 优先最近一周结果（沿用原有策略）
        val needsFreshness = listOf(
            "最新", "今天", "现在", "目前", "当前", "最近", "刚刚", "实时",
            "今日", "本周", "这个月", "近日", "近期",
            "latest", "today", "now", "current", "recent", "breaking", "just now"
        ).any { query.contains(it, ignoreCase = true) }
        val tbsParam = if (needsFreshness) "&tbs=qdr:w" else ""

        val isOfficialQuery = listOf(
            "政策", "法规", "规定", "官方", "政府", "通知", "公告",
            "数据", "统计", "报告", "研究", "调查",
            "policy", "official", "government", "law", "regulation"
        ).any { query.contains(it, ignoreCase = true) }

        val url = "$BASE_URL?engine=google&q=${URLEncoder.encode(query, "UTF-8")}" +
                "&api_key=$key&num=10&hl=zh-CN&gl=cn&safe=active$tbsParam"
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // ★ 429 要分清「额度用完」和「限速」——body 文案是唯一可靠区分
                val body = try { response.body?.string() ?: "" } catch (e: Exception) { "" }
                val msg = try {
                    JsonParser.parseString(body).asJsonObject.get("error")?.asString ?: body
                } catch (e: Exception) { body }
                val low = msg.lowercase()
                return when {
                    low.contains("run out of searches") || low.contains("out of searches") ->
                        SerpOutcome("", SerpErrorKind.NO_QUOTA, msg, resetAt = queryRenewal(key))
                    response.code == 429 || low.contains("rate limit") ->
                        SerpOutcome("", SerpErrorKind.RATE_LIMIT, msg)
                    else -> SerpOutcome("", SerpErrorKind.NETWORK, "HTTP ${response.code} ${msg.take(120)}")
                }
            }
            val json = try {
                JsonParser.parseString(response.body?.string() ?: "").asJsonObject
            } catch (e: Exception) {
                return SerpOutcome("", SerpErrorKind.NETWORK, "返回内容解析失败")
            }
            val organic = json.getAsJsonArray("organic_results")
            if (organic == null || organic.isEmpty) {
                Log.w(TAG, "SerpAPI: no organic_results, keys=${json.keySet()}")
                return SerpOutcome("", SerpErrorKind.EMPTY)
            }
            val results = mutableListOf<String>()
            for (i in 0 until minOf(organic.size(), 10)) {
                val r = organic[i].asJsonObject
                val title = r.get("title")?.takeUnless { it.isJsonNull }?.asString ?: ""
                val snippet = r.get("snippet")?.takeUnless { it.isJsonNull }?.asString ?: ""
                val link = r.get("link")?.takeUnless { it.isJsonNull }?.asString ?: ""
                val date = r.get("date")?.takeUnless { it.isJsonNull }?.asString ?: ""
                // 假名过滤：日文结果直接跳过（原有逻辑）
                val isJapanese = "$title $snippet".any { it in '぀'..'ヿ' }
                if (title.isNotEmpty() && !isJapanese) {
                    results.add("${i + 1}. $title${if (date.isNotEmpty()) "（$date）" else ""}\n   $snippet\n   $link")
                }
            }
            if (results.isEmpty()) return SerpOutcome("", SerpErrorKind.EMPTY)

            val freshnessNote = if (needsFreshness) "（已过滤最近一周内容）" else ""
            val officialNote =
                if (isOfficialQuery) "这类问题请优先采纳官方机构发布的信息，自媒体转述只作参考。"
                else "请优先采纳权威来源；无法确认的信息要如实说「未查到」，不要据此推测。"
            SerpOutcome(
                "【联网搜索结果$freshnessNote】\n查询：${query.take(80)}\n\n" +
                        results.joinToString("\n\n") +
                        "\n\n（以上是搜索引擎返回的原始结果，供你参考。$officialNote）"
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "SerpAPI failed: ${e.message}")
        SerpOutcome("", SerpErrorKind.NETWORK, e.message ?: "网络异常")
    }

    /** 撞到 429 时顺手问一次恢复日期，好在提示里说清「几号能恢复」 */
    private fun queryRenewal(key: String): Long = try {
        val req = Request.Builder().url("$ACCOUNT_URL?api_key=$key").build()
        balanceClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) 0L else {
                val obj = JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
                parseRenewal(obj.get("plan_renewal_date")?.takeUnless { it.isJsonNull }?.asString)
            }
        }
    } catch (e: Exception) {
        0L
    }
}

/** 单次搜索的结果。text 为空时看 [kind]/[errorText] */
data class SerpOutcome(
    val text: String,
    val kind: SerpErrorKind = SerpErrorKind.NONE,
    val errorText: String = "",
    /** 额度用完时的恢复时间（epoch ms），0 = 未知 */
    val resetAt: Long = 0L,
)

enum class SerpErrorKind {
    /** 搜到了 */
    NONE,

    /** 请求成功但零结果 */
    EMPTY,

    /** 这个 key 额度用完 */
    NO_QUOTA,

    /** 请求太频繁（额度还在） */
    RATE_LIMIT,

    /** 网络/服务端异常 */
    NETWORK,

    /** 没配置 key */
    NO_KEY,
}
