package com.freechat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class CompanionHttpTest {
    @Test fun successfulDelayedBodyKeepsTheWholeResponse() = runBlocking {
        MockWebServer().use { server ->
            val body = """{"choices":[{"message":{"content":"完整正文","tool_calls":null}}]}"""
            server.enqueue(MockResponse().setResponseCode(201).setBody(body).setBodyDelay(80, TimeUnit.MILLISECONDS))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/chat/completions")).build())
            assertEquals(201 to body, withContext(Dispatchers.IO) { call.awaitCompanionResponse() })
        }
    }

    @Test fun rejectedToolRequestRetainsRawStatusAndBodyForExistingFallbackDetection() = runBlocking {
        MockWebServer().use { server ->
            val body = """{"error":{"message":"tools are not supported"}}"""
            server.enqueue(MockResponse().setResponseCode(400).setBody(body))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/chat/completions")).build())
            assertEquals(400 to body, withContext(Dispatchers.IO) { call.awaitCompanionResponse() })
        }
    }

    @Test fun cancellationStopsDelayedHeadersWithoutWaitingForModelCompletion() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeadersDelay(2, TimeUnit.SECONDS).setBody("late reply"))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/chat/completions")).build())
            val started = System.nanoTime()
            assertNull(withTimeoutOrNull(150) { withContext(Dispatchers.IO) { call.awaitCompanionResponse() } })
            assertTrue("Cancellation must close the actual model request", call.isCanceled())
            assertTrue("Cancellation must not wait for delayed headers", (System.nanoTime() - started) / 1_000_000 < 1500)
        }
    }

    @Test fun cancellationAlsoStopsReadingAnAlreadyStartedResponseBody() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("late reply").setBodyDelay(2, TimeUnit.SECONDS))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/chat/completions")).build())
            val started = System.nanoTime()
            assertNull(withTimeoutOrNull(150) { withContext(Dispatchers.IO) { call.awaitCompanionResponse() } })
            assertTrue("Cancellation during response body read must cancel the socket", call.isCanceled())
            assertTrue("Cancellation must not wait for delayed body", (System.nanoTime() - started) / 1_000_000 < 1500)
        }
    }

    @Test fun interruptedBodyIsAnErrorNotSuccessfulPartialJson() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(4096)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/chat/completions")).build())
            try {
                withContext(Dispatchers.IO) { call.awaitCompanionResponse() }
                fail("Truncated model response must fail")
            } catch (_: IOException) { }
        }
    }
}
