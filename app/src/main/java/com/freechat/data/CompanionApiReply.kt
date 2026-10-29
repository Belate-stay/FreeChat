package com.freechat.data

import com.freechat.core.CompanionReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Android transport detail; shared companion/server response parsing remains unchanged. */
data class CompanionApiReply(val body: CompanionReply, val reasoningContent: String = "") {
    val emotion get() = body.emotion
    val segments get() = body.segments
    val sources get() = body.sources
    val silence get() = body.silence

    /** Optional rewriting must not erase a usable reply, nor revive a cancelled generation. */
    suspend fun refineOrKeep(refine: suspend () -> CompanionApiReply): CompanionApiReply = try {
        val refined = refine()
        currentCoroutineContext().ensureActive()
        refined.takeIf { !it.silence && it.segments.any(String::isNotBlank) } ?: this
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        this
    }
}
