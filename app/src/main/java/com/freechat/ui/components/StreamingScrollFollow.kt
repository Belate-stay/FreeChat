package com.freechat.ui.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import com.freechat.ui.animation.FreeChatAnimation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.conflate

/** Intent survives content growth, but never survives a reader's deliberate gesture. */
class BottomFollowPolicy {
    var following: Boolean = false
        private set
    private var initialized = false
    private var reading = false
    private var gesture = false
    private var gestureMoved = false
    private var disclosure: String? = null

    fun observe(atBottom: Boolean, scrolling: Boolean) {
        if (!initialized) { initialized = true; following = atBottom && !reading && disclosure == null }
        if (gesture && scrolling) gestureMoved = true
        if (gesture && gestureMoved && !scrolling) {
            gesture = false
            reading = !atBottom
            following = atBottom && disclosure == null
        }
        // Returning to the actual end (including initial navigation) resumes following.
        // A false geometry reading during streamed growth MUST NOT revoke existing intent.
        if (atBottom && !scrolling && !reading && disclosure == null) following = true
    }

    fun onUserGesture() {
        if (!gesture) gestureMoved = false
        reading = true; gesture = true; following = false
    }
    fun onUserMoved() { gestureMoved = true }
    fun onDisclosureToggle(id: String) { disclosure = id; following = false }
    fun onDisclosureSettled(id: String) { if (disclosure == id) disclosure = null }
    fun onGenerationRequested(atBottom: Boolean) {
        initialized = true
        reading = !atBottom
        gesture = false
        disclosure = null
        following = atBottom
    }
}

/** One update per rendered frame, outside measurement; no synthetic trailing spacer. */
@Composable
fun rememberStreamingScrollFollow(list: LazyListState, conversationId: String?): BottomFollowPolicy {
    val policy = remember(list, conversationId) { BottomFollowPolicy() }
    LaunchedEffect(policy) {
        snapshotFlow { list.layoutInfo to list.isScrollInProgress }.conflate().collect { (_, scrolling) ->
            policy.observe(!list.canScrollForward, scrolling)
            if (policy.following && !scrolling && list.canScrollForward) {
                withFrameNanos { }
                // Input arriving during that frame has priority over the scheduled scroll.
                if (!policy.following || list.isScrollInProgress || !list.canScrollForward) return@collect
                try {
                    val info = list.layoutInfo
                    val lastIndex = info.totalItemsCount - 1
                    if (lastIndex < 0) return@collect
                    val last = info.visibleItemsInfo.lastOrNull { it.index == lastIndex }
                    if (last == null) list.scrollToItem(lastIndex, 1_000_000)
                    else coroutineScope {
                        val motion = launch {
                            list.animateScrollBy((last.offset + last.size + info.afterContentPadding - info.viewportEndOffset)
                                .coerceAtLeast(0).toFloat(), FreeChatAnimation.streamFollowTween)
                        }
                        try {
                            // A disclosure tap/locator intent may not acquire the scroll mutex yet.
                            // Stop within a display frame instead of finishing a queued auto-scroll.
                            while (motion.isActive) {
                                withFrameNanos { }
                                if (!policy.following) motion.cancel()
                            }
                        } finally { motion.cancel() }
                    }
                } catch (_: CancellationException) {
                    // MutatorMutex cancels programmatic scrolling for a gesture; screen disposal
                    // must still cancel the observer itself.
                    currentCoroutineContext().ensureActive()
                }
            }
        }
    }
    return policy
}
