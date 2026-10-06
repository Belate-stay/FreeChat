package com.freechat.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cancelling the coroutine cancels the socket, including connection and response body reads. */
internal suspend fun Call.awaitText(maxBytes: Long = 1_048_576): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            try {
                val text = response.use {
                    val requestId = it.header("x-request-id") ?: it.header("x-tt-logid") ?: it.header("request-id").orEmpty()
                    val body = it.body ?: if (!it.isSuccessful) throw ApiFailure.fromResponse(it.code, "", requestId)
                        else throw IOException("Empty response")
                    val buffer = Buffer()
                    val source = body.source()
                    val limit = if (it.isSuccessful) maxBytes else minOf(maxBytes, 16_384L)
                    while (buffer.size <= limit) {
                        if (source.read(buffer, minOf(8192, limit + 1 - buffer.size)) == -1L) break
                    }
                    if (!it.isSuccessful) throw ApiFailure.fromResponse(it.code,
                        buffer.readUtf8(minOf(buffer.size, limit)),
                        requestId)
                    if (buffer.size > limit) throw IOException("Response too large")
                    buffer.readString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                }
                if (continuation.isActive) continuation.resume(text)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    })
}
