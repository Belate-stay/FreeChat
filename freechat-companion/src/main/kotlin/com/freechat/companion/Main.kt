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
 *   XIAOMI_API_KEY=sk-…          （内置 mimo 代调，方案 v2「凭据零配置、成本用户自担」）
 */
fun main() {
    val dbPath = System.getenv("FREECHAT_DB") ?: "/var/lib/freechat/freechat.db"
    val apiKey = System.getenv("XIAOMI_API_KEY") ?: error("XIAOMI_API_KEY is required")
    val port = (System.getenv("FREECHAT_COMPANION_PORT") ?: "3100").toInt()

    val store = CompanionStore(dbPath)
    val brain = CompanionBrain(store, MimoChatCompleter(apiKey = apiKey))

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
