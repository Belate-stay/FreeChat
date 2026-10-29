package com.freechat.companion

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CompanionToolResponseTest {
    @Test fun truncatedSuccessfulResponseCompletesWithAnErrorInsteadOfHanging() {
        // HttpServer keeps an under-filled fixed-length exchange open; close the TCP peer
        // explicitly so this fixture tests a truncated response, not a server that stalls.
        val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val responder = thread(isDaemon = true) {
            server.accept().use { socket ->
                socket.soTimeout = 2000
                val input = socket.getInputStream()
                val headers = StringBuilder()
                while (!headers.endsWith("\r\n\r\n")) {
                    val next = input.read()
                    check(next >= 0)
                    headers.append(next.toChar())
                }
                val length = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE)
                    .find(headers)?.groupValues?.get(1)?.toInt() ?: 0
                repeat(length) { check(input.read() >= 0) }
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\n{".toByteArray())
                    flush()
                }
            }
        }
        try {
            val error = runBlocking {
                runCatching { kotlinx.coroutines.withTimeout(2000) {
                    MimoChatCompleter("http://127.0.0.1:${server.localPort}", "fixture-not-a-key").complete(emptyList(), 0.1, emptyMap())
                } }.exceptionOrNull()
            }
            assertNotNull(error)
            assertFalse("Callback abandoned the continuation", error is kotlinx.coroutines.TimeoutCancellationException)
        } finally { server.close(); responder.join(1000) }
    }

    @Test fun openAiNullContentToolResponseKeepsToolAndReasoning() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            val response = """{"choices":[{"message":{"content":null,"reasoning_content":"需要核对事实","tool_calls":[{"id":"q1","type":"function","function":{"name":"search_web","arguments":"{\"queries\":[\"杭州预警\"]}"}}]}}]}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val reply = runBlocking {
                MimoChatCompleter("http://127.0.0.1:${server.address.port}", "fixture-not-a-key").complete(
                    listOf(mapOf("role" to "user", "content" to "查杭州预警")), 0.1, emptyMap())
            }
            assertEquals("", reply.text)
            assertEquals("需要核对事实", reply.reasoningContent)
            assertEquals("q1", reply.toolCalls.single()["id"])
        } finally { server.stop(0) }
    }

    @Test fun malformedToolPayloadCompletesWithAnErrorInsteadOfHanging() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            val response = """{"choices":[{"message":{"content":null,"tool_calls":["not-a-tool-object"]}}]}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val error = runBlocking {
                runCatching { kotlinx.coroutines.withTimeout(2000) {
                    MimoChatCompleter("http://127.0.0.1:${server.address.port}", "fixture-not-a-key")
                        .complete(emptyList(), 0.1, emptyMap())
                } }.exceptionOrNull()
            }
            assertNotNull(error)
            assertFalse("Malformed tools must fail the caller promptly", error is kotlinx.coroutines.TimeoutCancellationException)
        } finally { server.stop(0) }
    }
}
