package com.freechat.sync

import com.freechat.data.AppJson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 账号与同步的 API 客户端。
 *
 * 只走 **HTTPS**：服务端的 80 端口对 `/api` 一律回 `403 https_required` ——
 * 明文 HTTP 上传密码和令牌不可接受。这也是为什么地址写死成 `https://`，
 * 而不是留个可以配成 http 的开关。
 *
 * 不复用 `ChatViewModel` 那个 OkHttpClient：那个客户端是为「长流式回复」调的
 * （读超时 180 秒、连接池只留 1 分钟），同步这边都是几百毫秒的短请求，
 * 混在一起只会让两边的参数互相将就。
 */
object ApiClient {

    /** 官方账号与同步服务器；域名已过审，使用域名的 HTTPS 证书。 */
    const val BASE = "https://freechater.com/api"

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)   // 首次登录要把本地全部推一遍，写入侧要给够
        .retryOnConnectionFailure(true)
        .build()

    // ============================================================
    //  底座
    // ============================================================

    private fun bodyOf(value: Any?): RequestBody? {
        if (value == null) return null
        val json = if (value is JsonElement) AppJson.gson.toJson(value) else AppJson.gson.toJson(value)
        return json.toRequestBody(JSON)
    }

    private suspend fun call(
        method: String,
        path: String,
        token: String? = null,
        body: Any? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(BASE + path)
        if (token != null) builder.header("Authorization", "Bearer $token")
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(bodyOf(body) ?: "{}".toRequestBody(JSON))
            "PUT" -> builder.put(bodyOf(body) ?: "{}".toRequestBody(JSON))
            // DELETE 的 rev 在 body 里（不是 query）—— 服务端就是这么读的
            "DELETE" -> builder.delete(bodyOf(body))
            else -> error("unsupported method $method")
        }

        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            // 网络根本没通。**必须和「服务器说你不对」分开**：这个等会儿自己会好，
            // 那个要么清令牌、要么提示用户。
            throw ApiError(0, "network_error", e.message ?: com.freechat.i18n.LocaleManager.strings().networkUnreachable)
        }

        response.use { res ->
            val text = try { res.body?.string().orEmpty() } catch (_: Exception) { "" }
            val json = try {
                if (text.isBlank()) JsonObject() else JsonParser.parseString(text).asJsonObject
            } catch (_: Exception) {
                JsonObject()
            }
            if (!res.isSuccessful) {
                val code = json.get("error")?.takeIf { it.isJsonPrimitive }?.asString ?: "http_error"
                val msg = json.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: com.freechat.i18n.LocaleManager.strings().requestFailedWith(res.code)
                // 409 时服务端把云端当前那一条的元信息放在 current 里 ——
                // 够判断「云端是不是已经把这条删了」，省一次往返
                val current = json.get("current")
                    ?.takeIf { it.isJsonObject }
                    ?.let { runCatching { AppJson.gson.fromJson(it, ObjectMeta::class.java) }.getOrNull() }
                throw ApiError(res.code, code, msg, current)
            }
            json
        }
    }

    private inline fun <reified T> JsonObject.into(): T = AppJson.gson.fromJson(this, T::class.java)

    // ============================================================
    //  账号
    // ============================================================

    /**
     * 设备名 —— 进账号页那个「登录中的设备」列表。
     *
     * 安卓这边能报的是**认证型号**（`Build.MODEL`，比如小米的 `2201122C`），比浏览器 UA 精确得多，
     * 所以自报优先；服务端只在客户端没报时才按 User-Agent 猜（见 server 的 `deviceLabel`）。
     *
     * 品牌名首字母大写是刻意的：`MANUFACTURER` 是 `Xiaomi`，而用户列表里两种写法混着看很别扭。
     * 全小写的 `Build.DEVICE`（代号，如 `zeus`）**不报** —— 它是给内核用的代号，
     * 用户对着它认不出自己的手机，报上去只会让列表更难读。
     */
    private val deviceName: String by lazy {
        val brand = android.os.Build.MANUFACTURER.orEmpty().trim().replaceFirstChar { it.uppercase() }
        val model = android.os.Build.MODEL.orEmpty().trim()
        val os = "Android ${android.os.Build.VERSION.RELEASE}".trim()
        // 有些厂商的 MODEL 本来就带着品牌（"Xiaomi 14"），那就别再加一遍前缀
        val head = when {
            model.isBlank() -> brand
            brand.isBlank() || model.startsWith(brand, ignoreCase = true) -> model
            else -> "$brand $model"
        }
        listOf(head, os).filter { it.isNotBlank() }.joinToString(" · ")
    }

    suspend fun register(username: String, password: String): AuthResult =
        call(
            "POST", "/auth/register",
            body = mapOf("username" to username, "password" to password, "device" to deviceName)
        ).into()

    suspend fun login(username: String, password: String): AuthResult =
        call(
            "POST", "/auth/login",
            body = mapOf("username" to username, "password" to password, "device" to deviceName)
        ).into()

    suspend fun logout(token: String) {
        call("POST", "/auth/logout", token = token)
    }

    suspend fun recover(username: String, recoveryCode: String, newPassword: String): AuthResult =
        call(
            "POST", "/auth/recover",
            body = mapOf(
                "username" to username,
                "recoveryCode" to recoveryCode,
                "newPassword" to newPassword,
                "device" to deviceName
            )
        ).into()

    suspend fun changePassword(token: String, oldPassword: String, newPassword: String) {
        call("POST", "/auth/password", token = token, body = mapOf("oldPassword" to oldPassword, "newPassword" to newPassword))
    }

    suspend fun regenerateRecoveryCode(token: String, password: String): String =
        call("POST", "/auth/recovery-code", token = token, body = mapOf("password" to password))
            .get("recoveryCode")?.asString.orEmpty()

    suspend fun me(token: String): Pair<SyncUser, UsageInfo> {
        val json = call("GET", "/me", token = token)
        return json.getAsJsonObject("user").into<SyncUser>() to json.getAsJsonObject("usage").into<UsageInfo>()
    }

    suspend fun deleteAccount(token: String, password: String) {
        call("DELETE", "/account", token = token, body = mapOf("password" to password))
    }

    // ============ 资料：用户名 / 头像 ============

    suspend fun changeUsername(token: String, username: String): SyncUser =
        call("POST", "/me/username", token = token, body = mapOf("username" to username))
            .getAsJsonObject("user").into()

    /** 头像走 base64 明文进 JSON（不搞 multipart）：服务端存的就是密文之外的原始字节，够用且两端都好写 */
    suspend fun putAvatar(token: String, mime: String, base64: String): Long =
        call("PUT", "/me/avatar", token = token, body = mapOf("mime" to mime, "data" to base64))
            .get("rev")?.asLong ?: 0L

    /** 没头像时服务端回 404，这里当成「就是没有」返回 null，不抛 */
    suspend fun getAvatar(token: String): Pair<String, String>? = try {
        val json = call("GET", "/me/avatar", token = token)
        val mime = json.get("mime")?.asString.orEmpty()
        val data = json.get("data")?.asString.orEmpty()
        if (data.isBlank()) null else mime to data
    } catch (e: ApiError) {
        if (e.status == 404) null else throw e
    }

    suspend fun deleteAvatar(token: String) {
        call("DELETE", "/me/avatar", token = token)
    }

    suspend fun listTokens(token: String): List<TokenInfo> {
        val arr = call("GET", "/me/tokens", token = token).getAsJsonArray("tokens")
        return arr.map { AppJson.gson.fromJson(it, TokenInfo::class.java) }
    }

    suspend fun revokeToken(token: String, id: String) {
        call("DELETE", "/me/tokens/$id", token = token)
    }

    // ============================================================
    //  同步
    // ============================================================

    /**
     * 拉取「游标之后发生的变化」。
     *
     * `since` 是**服务端自增的 seq**，不是客户端时间戳 —— 多设备时钟不可信，
     * 用本地时间做游标会在两端时钟有偏差时静默漏掉一批改动。
     */
    suspend fun changes(token: String, since: Long, limit: Int = 200): ChangesResult =
        call("GET", "/sync/changes?since=$since&limit=$limit", token = token).into()

    /**
     * 一次 `/sync/fetch` 最多能要多少条。
     *
     * **必须与服务端的 `MAX_FETCH_ITEMS`（FreeChatServer/src/sync.js）保持一致**：
     * 超一条，整个请求就是 400 `too_many_items`，一条都拿不到。
     *
     * 这就是那个「同步错误」的来源 —— 拉取是把一批改动**一次性**取回来的
     * （一条一条取会把往返次数乘上对象数），而一批可能有几百条，
     * 于是每次同步都在这个上限上撞死，界面上永远显示「同步错误」。
     * 服务端把上限设成 50 是为了别让单个请求的响应体无限大，这个约束本身是合理的，
     * 客户端该做的是**分批**，而不是指望服务端放宽。
     */
    private const val FETCH_CHUNK = 50

    suspend fun fetchObjects(token: String, items: List<Pair<String, String>>): List<SyncObject> {
        if (items.isEmpty()) return emptyList()
        if (items.size <= FETCH_CHUNK) return fetchObjectsOnce(token, items)
        // 分批取。返回顺序与分批方式都无所谓 —— 调用方按 kind+id 归并，不依赖次序
        val out = ArrayList<SyncObject>(items.size)
        for (chunk in items.chunked(FETCH_CHUNK)) out += fetchObjectsOnce(token, chunk)
        return out
    }

    private suspend fun fetchObjectsOnce(token: String, items: List<Pair<String, String>>): List<SyncObject> {
        val payload = JsonObject()
        val arr = com.google.gson.JsonArray()
        for ((kind, id) in items) {
            val o = JsonObject()
            o.addProperty("kind", kind)
            o.addProperty("id", id)
            arr.add(o)
        }
        payload.add("items", arr)
        val json = call("POST", "/sync/fetch", token = token, body = payload)
        return json.getAsJsonArray("objects").map { AppJson.gson.fromJson(it, SyncObject::class.java) }
    }

    suspend fun listAll(token: String, limit: Int = 500): List<ObjectMeta> {
        val arr = call("GET", "/sync/list?limit=$limit", token = token).getAsJsonArray("objects")
        return arr.map { AppJson.gson.fromJson(it, ObjectMeta::class.java) }
    }

    suspend fun putObject(token: String, kind: String, id: String, rev: Long, updatedAt: Long, data: JsonElement): PutResult {
        val body = JsonObject()
        body.addProperty("rev", rev)
        body.addProperty("updatedAt", updatedAt)
        body.add("data", data)
        return call("PUT", "/sync/objects/$kind/$id", token = token, body = body).into()
    }

    suspend fun deleteObject(token: String, kind: String, id: String, rev: Long): PutResult {
        return call("DELETE", "/sync/objects/$kind/$id", token = token, body = mapOf("rev" to rev)).into()
    }

    suspend fun health(): Boolean =
        runCatching { call("GET", "/health").get("ok")?.asBoolean == true }.getOrDefault(false)

    // ============================================================
    //  分享（在线网页链接）
    // ============================================================

    /**
     * 创建一次「在线网页分享」。body 形状见 ShareLinkPayload.build()：
     * { title, messages: [{role, content, timestamp}], images: [{messageIndex, mime, data}] }。
     * 服务端校验条数/字数/大小，超限回 413 payload_too_large（message 里明说原因，直接展示给用户）。
     */
    suspend fun createShare(token: String, body: JsonObject): ShareLinkResult =
        call("POST", "/share", token = token, body = body).into()

    suspend fun listShares(token: String): List<ShareInfo> =
        call("GET", "/share", token = token).into<ShareListResult>().shares

    /** 撤销分享：服务端置 revoked 并删图片，链接随即 404。幂等 */
    suspend fun revokeShare(token: String, id: String) {
        call("DELETE", "/share/$id", token = token)
    }

    // ============================================================
    //  微信 ClawBot（M3，登录特权）
    // ============================================================

    /** 取官方二维码（iLink get_bot_qrcode 转发）。[imgContent] 是二维码图 URL 或内容 */
    suspend fun wechatBindStart(token: String): Pair<String, String> {
        val o = call("POST", "/wechat/bind/start", token = token, body = JsonObject())
        return (o.get("qrcode")?.asString ?: "") to (o.get("qrcode_img_content")?.asString ?: "")
    }

    /**
     * 轮询扫码状态（wait/scaned/confirmed/expired/need_verifycode/…）。confirmed 时服务端已落库并拉起收发。
     * [verifyCode]：need_verifycode 分支的二次验证码（1.0.94 补上——服务器一直收，App 之前没传，
     * 验证码分支是个死胡同）；空串=纯轮询。
     */
    suspend fun wechatBindStatus(token: String, convId: String, verifyCode: String = ""): String {
        val q = buildString {
            append("/wechat/bind/status?conv_id=$convId")
            if (verifyCode.isNotBlank()) append("&verify_code=").append(java.net.URLEncoder.encode(verifyCode, "UTF-8"))
        }
        return call("GET", q, token = token).get("status")?.asString ?: "wait"
    }

    suspend fun wechatUnbind(token: String) {
        call("POST", "/wechat/unbind", token = token, body = JsonObject())
    }

    /** 绑定概览：bound / conv_id / paused_until */
    suspend fun wechatStatus(token: String): JsonObject = call("GET", "/wechat/status", token = token)

    /**
     * 大脑生成（M4 收口）：接微信的角色 App 里聊也走服务器生成。
     * 经 FreeChatServer 鉴权代理到 127.0.0.1:3100 的大脑（手机直接够不到回环）。
     * [skipUserWrite] = true：用户消息由 App 本地落盘+同步，大脑不重复写（收口语义）。
     * 返回 { slept, silence, emotion, segments: [...], messageIds: [...] }。
     */
    suspend fun companionReply(token: String, convId: String, userText: String, skipUserWrite: Boolean = false): JsonObject {
        val body = JsonObject()
        body.addProperty("convId", convId)
        body.addProperty("userText", userText)
        body.addProperty("skipUserWrite", skipUserWrite)
        return call("POST", "/companion/reply", token = token, body = body)
    }

    // ============================================================
    //  建议与反馈（1.0.95：可匿名提交；登录则顺手挂账号）
    // ============================================================

    /**
     * 提交反馈。**不带令牌**——未登录也能发（拍板）；登录态多带一份 Authorization，
     * 服务器把反馈挂到账号上。[device] = 机型/系统/应用版本/语言 的一行摘要。
     */
    suspend fun submitFeedback(content: String, clientId: String, device: String, token: String? = null): JsonObject {
        val body = JsonObject()
        body.addProperty("content", content)
        body.addProperty("clientId", clientId)
        body.addProperty("device", device)
        return call("POST", "/feedback", token = token, body = body)
    }

    /** 我的反馈（只读清单）：user_id 或 clientId 命中都算；未登录只带 clientId */
    suspend fun myFeedback(clientId: String, token: String? = null): JsonArray {
        val o = call(
            "GET",
            "/feedback/mine?clientId=${java.net.URLEncoder.encode(clientId, "UTF-8")}",
            token = token
        )
        return o.getAsJsonArray("feedbacks") ?: JsonArray()
    }
}
