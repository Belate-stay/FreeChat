package com.freechat.companion

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * freechat-companion 入口（M2 服务器大脑）。
 *
 * 只监听 127.0.0.1（默认端口 3100，env FREECHAT_COMPANION_PORT 可改）——
 * 对外暴露是 nginx / M3 微信通道的事。端点：
 *   GET  /health           → {ok:true}
 *   POST /companion/reply  → {convId, userText[, messages, batchId]} 生成一轮并写回同步库
 *                          → {slept, silence, emotion, segments, messageIds}
 *     内部微信通道还带 X-FreeChat-Channel: wechat 与 wechatBinding:{userId,boundAt}；
 *     只接受回环来源，随后核对同步库的当前绑定。App 代理重建请求头，客户端 JSON 无法开启此许可。
 *   GET  /companion/buffer?convId= → {bufferMs}  该对话角色的回复缓冲窗口（0=关，微信通道攒消息用）
 *   POST /companion/cancel → {batchId}  通道层判这批不发了：弃写 / 已落库未发出的清掉半截
 *
 * 配置走环境变量（systemd EnvironmentFile=/etc/freechat/companion.env）：
 *   FREECHAT_DB=/var/lib/freechat/freechat.db
 *   DEEPSEEK_API_KEY=sk-…        （内置 deepseek-flash 代调，1.1.30X 换代；
 *                                  旧 XIAOMI_API_KEY 仍兜底认，但那把是小米的钥——
 *                                  部署时 companion.env 必须换成 DeepSeek 的 Key）
 */
fun main() {
    val dbPath = System.getenv("FREECHAT_DB") ?: "/var/lib/freechat/freechat.db"
    val apiKey = System.getenv("DEEPSEEK_API_KEY")
        ?: System.getenv("XIAOMI_API_KEY")
        ?: error("DEEPSEEK_API_KEY is required")
    val port = (System.getenv("FREECHAT_COMPANION_PORT") ?: "3100").toInt()

    val store = CompanionStore(dbPath)
    val completer = MimoChatCompleter(apiKey = apiKey)
    val brain = CompanionBrain(store, completer)

    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    server.executor = Executors.newFixedThreadPool(4)

    server.createContext("/health") { ex ->
        val body = """{"ok":true,"ts":${System.currentTimeMillis()}}"""
        ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        ex.sendResponseHeaders(200, body.toByteArray().size.toLong())
        ex.responseBody.use { it.write(body.toByteArray()) }
    }

    server.createContext("/companion/reply") { ex ->
        if (ex.requestMethod != "POST") {
            respond(ex, 405, """{"error":"method_not_allowed"}""")
            return@createContext
        }
        try {
            val raw = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val body = JsonParser.parseString(raw).asJsonObject
            val channelHeader = ex.requestHeaders.getFirst(WechatBinding.CHANNEL_HEADER)
            val loopbackPeer = ex.remoteAddress.address.isLoopbackAddress
            val wechatBinding = WechatBinding.fromTrustedRequest(body, channelHeader, loopbackPeer)
            if (loopbackPeer && channelHeader == "wechat" && wechatBinding == null) {
                respond(ex, 400, """{"error":"bad_request"}""")
                return@createContext
            }
            val convId = body.get("convId")?.asString.orEmpty()
            val userText = body.get("userText")?.asString.orEmpty()
            val userMessageId = body.get("userMessageId")?.takeIf { it.isJsonPrimitive }?.asString
            val skipUserWrite = body.get("skipUserWrite")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
            val batchId = body.get("batchId")?.takeIf { it.isJsonPrimitive }?.asString
            // 1.0.99.4 微信缓冲批：逐条带稳定 id（幂等落库），batchSize 天然等于条数
            val messages = body.getAsJsonArray("messages")?.mapNotNull { el ->
                val o = el?.asJsonObject ?: return@mapNotNull null
                val id = o.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val text = o.get("text")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                CompanionBrain.UserMsg(id, text)
            }.orEmpty()
            val hasInput = if (messages.isNotEmpty()) messages.all { it.text.isNotBlank() } else userText.isNotBlank()
            if (!store.safeId(convId) || !hasInput || userText.length > 20_000 ||
                messages.size > 64 || messages.any { it.text.length > 20_000 }) {
                respond(ex, 400, """{"error":"bad_request"}""")
                return@createContext
            }
            val result = kotlinx.coroutines.runBlocking {
                brain.reply(convId, userText, userMessageId, skipUserWrite, batchId, messages,
                    wechatBinding = wechatBinding)
            }
            val out = JsonObject()
            out.addProperty("slept", result.slept)
            out.addProperty("silence", result.silence)
            out.addProperty("emotion", result.emotion)
            out.add("segments", com.google.gson.Gson().toJsonTree(result.segments))
            out.add("messageIds", com.google.gson.Gson().toJsonTree(result.messageIds))
            respond(ex, 200, out.toString())
        } catch (e: IllegalArgumentException) {
            respond(ex, 404, """{"error":"not_found","message":${com.google.gson.Gson().toJson(e.message ?: "not found")}}""")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 过期闸门把这批作废了（并批重等）：给通道层一个「这批不算」的信号，新批马上到
            respond(ex, 409, """{"error":"superseded"}""")
        } catch (e: Exception) {
            System.err.println("companion.reply failed: ${e.message}")
            respond(ex, 500, """{"error":"internal","message":${com.google.gson.Gson().toJson(e.message ?: "internal")}}""")
        }
    }

    server.createContext("/companion/buffer") { ex ->
        val query = ex.requestURI.query.orEmpty()
        val convId = query.split('&').mapNotNull {
            val kv = it.split('=', limit = 2)
            if (kv.size == 2 && kv[0] == "convId") java.net.URLDecoder.decode(kv[1], "UTF-8") else null
        }.firstOrNull().orEmpty()
        val ch = if (store.safeId(convId)) store.load(convId)?.conv?.characterProfile?.normalized() else null
        // 回复缓冲窗口（App 的 processCompanionBuffer 同语义）：只属于「微信聊天」档；
        // 开关默认开、时长 1-6 秒默认 3。0 = 关闭缓冲（即收即答）。
        val wechatMode = (ch?.dialogueMode ?: com.freechat.model.DialogueMode.WECHAT) == com.freechat.model.DialogueMode.WECHAT
        val enabled = wechatMode && ch?.replyBufferEnabled != false
        val seconds = if (enabled) ch?.replyBufferSeconds?.coerceIn(1, 6) ?: 3 else 0
        respond(ex, 200, """{"bufferMs":${seconds * 1000}}""")
    }

    server.createContext("/companion/cancel") { ex ->
        if (ex.requestMethod != "POST") {
            respond(ex, 405, """{"error":"method_not_allowed"}""")
            return@createContext
        }
        try {
            val raw = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val batchId = JsonParser.parseString(raw).asJsonObject
                .get("batchId")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val ok = kotlinx.coroutines.runBlocking { brain.cancel(batchId) }
            respond(ex, 200, """{"ok":$ok}""")
        } catch (e: Exception) {
            respond(ex, 500, """{"error":"internal"}""")
        }
    }

    server.createContext("/companion/proactive/decide") { ex ->
        if (ex.requestMethod != "POST") {
            respond(ex, 405, """{"error":"method_not_allowed"}""")
            return@createContext
        }
        try {
            val raw = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val body = JsonParser.parseString(raw).asJsonObject
            val convId = body.get("convId")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if (!store.safeId(convId)) {
                respond(ex, 400, """{"error":"bad_request"}""")
                return@createContext
            }
            // 2026-10-20 云端主动智能：人设/关系/近况自取（材料在信息最全的大脑侧），调用方只给时间与配额
            val objs = store.load(convId)
            if (objs == null) {
                respond(ex, 404, """{"error":"not_found"}""")
                return@createContext
            }
            val ch = objs.conv.characterProfile?.normalized() ?: com.freechat.model.CharacterProfile()
            val recent = objs.messages.takeLast(20).joinToString("\n") { m ->
                val who = if (m.role == com.freechat.model.Role.USER) "TA" else "你"
                "[$who ${java.time.Instant.ofEpochMilli(m.timestamp)}] ${m.content.replace('\n', ' ').take(160)}"
            }
            val prompt = com.freechat.core.CompanionPrompts.proactiveDecide(
                ch = ch,
                recentLines = recent,
                nowText = body.get("nowText")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                newTopicQuotaLeft = body.get("newTopicQuotaLeft")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                lastProactiveText = body.get("lastProactiveText")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                sleepWindow = body.get("sleepWindow")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            )
            val out = kotlinx.coroutines.runBlocking {
                completer.complete(
                    listOf(
                        mapOf("role" to "system", "content" to "你是主动决策器。只输出 JSON，不要围栏、不要多余文字。"),
                        mapOf("role" to "user", "content" to prompt)
                    ),
                    0.8
                )
            }
            val decision = parseProactiveDecision(out)
            // 硬-2（2026-10-20）：台词先落 msgs 箱再回管道去发微信（大脑是箱的唯一写入方）
            val o = JsonParser.parseString(decision).asJsonObject
            if (o.get("action")?.asString == "message") {
                val contents = o.get("content")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }.orEmpty()
                if (contents.isNotEmpty()) {
                    val ids = kotlinx.coroutines.runBlocking { brain.persistProactiveMessages(convId, contents) }
                    o.add("messageIds", JsonParser.parseString(
                        ids.joinToString(",", "[", "]") { com.google.gson.Gson().toJson(it) }))
                    respond(ex, 200, o.toString())
                    return@createContext
                }
            }
            respond(ex, 200, decision)
        } catch (e: Exception) {
            respond(ex, 500, """{"error":"internal"}""")
        }
    }

    Runtime.getRuntime().addShutdownHook(Thread { server.stop(0); store.close() })
    println("freechat-companion listening on 127.0.0.1:$port db=$dbPath")
    server.start()
}

private fun respond(ex: com.sun.net.httpserver.HttpExchange, code: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
    ex.sendResponseHeaders(code, bytes.size.toLong())
    ex.responseBody.use { it.write(bytes) }
}

/** 模型输出→决策 JSON：剥围栏、截取首个 {…}、枚举校验；解析失败一律回落 none（宁可不发）。 */
private fun parseProactiveDecision(out: String): String {
    val s = out.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val start = s.indexOf('{'); val end = s.lastIndexOf('}')
    if (start < 0 || end <= start) return """{"action":"none","kind":"new_topic","reason":"unparseable"}"""
    val o = runCatching { JsonParser.parseString(s.substring(start, end + 1)).asJsonObject }.getOrNull()
        ?: return """{"action":"none","kind":"new_topic","reason":"unparseable"}"""
    val action = o.get("action")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        .let { if (it in setOf("none", "message", "wait")) it else "none" }
    val kind = o.get("kind")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        .let { if (it in setOf("new_topic", "continuation", "anchor")) it else "new_topic" }
    val delay = o.get("delayMinutes")?.takeIf { it.isJsonPrimitive }?.asInt?.coerceIn(0, 24 * 60) ?: 0
    val content = o.get("content")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString?.trim()?.takeIf { t -> t.isNotBlank() } }
        ?.take(2).orEmpty()
    val reason = o.get("reason")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().take(200)
    if (action == "message" && content.isEmpty()) return """{"action":"none","kind":"$kind","reason":"empty_content"}"""
    val contentJson = content.joinToString(",", "[", "]") { com.google.gson.Gson().toJson(it) }
    return """{"action":"$action","kind":"$kind","delayMinutes":$delay,"content":$contentJson,"reason":${com.google.gson.Gson().toJson(reason)}}"""
}
