package com.freechat.data

import android.os.SystemClock
import com.freechat.model.Message

/** One monotonic clock for the entire request, including planning, tools and image decoding. */
internal class GenerationTimer(private val clock: () -> Long = { SystemClock.elapsedRealtime() }) {
    private val startedAt = clock()
    fun elapsedMs(): Long = (clock() - startedAt).coerceAtLeast(0L)
    // Stamp after the final tool completes, never with the duration of the tool-decision call.
    fun complete(message: Message): Message = message.copy(thinkingTimeMs = elapsedMs())
}
