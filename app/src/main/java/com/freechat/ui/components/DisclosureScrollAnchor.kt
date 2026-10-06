package com.freechat.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.withFrameNanos
import com.freechat.ui.animation.FreeChatAnimation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Browsing keeps its anchor. At the conversation end an expansion may reveal its first entries. */
data class DisclosureAnchorPlan(
    val itemIndex: Int,
    val scrollOffset: Int,
    val initialHeight: Int,
    val initialTrailingSpace: Int = 0,
    val availableBelow: Int = 0,
    val followBottom: Boolean = false,
    val maximumFollow: Int = 0,
) {
    fun requestedOffset(height: Int) = scrollOffset + if (followBottom)
        (height - initialHeight).coerceIn(0, maximumFollow.coerceAtLeast(0)) else 0
    // Never manufacture scroll extent after a collapse; LazyColumn naturally clamps to its real end.
    fun trailingSpace(@Suppress("UNUSED_PARAMETER") height: Int) = 0
}

/** 只在用户折叠动画中保持原锚点；生成内容增长和普通重测量绝不接管视口。 */
class DisclosureScrollAnchor(private val list: LazyListState, private val scope: CoroutineScope) {
    val trailingSpacePx = 0
    private var activeId: String? = null
    private var plan: DisclosureAnchorPlan? = null
    private var bottomCollapse = false
    private var latestHeight = 0
    private var settled = false
    private var follower: Job? = null
    private val openedAtBottom = mutableSetOf<String>()
    val callbacks = DisclosureCallbacks(
        onToggle = { id, height, headerTop, expanding ->
            follower?.cancel()
            activeId = id
            val layout = list.layoutInfo
            val atEnd = !list.canScrollForward
            val lastMessageVisible = layout.visibleItemsInfo.lastOrNull()?.index == layout.totalItemsCount - 1
            bottomCollapse = !expanding && (atEnd || (id in openedAtBottom && lastMessageVisible))
            if (expanding && atEnd) openedAtBottom.add(id)
            if (!expanding) openedAtBottom.remove(id)
            latestHeight = height
            settled = false
            val reveal = (headerTop - layout.viewportSize.height * 0.34f).toInt().coerceAtLeast(0)
            plan = DisclosureAnchorPlan(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset,
                height, followBottom = expanding && atEnd, maximumFollow = reveal)
            if (bottomCollapse || expanding && atEnd) followAnimation(id, plan!!)
        },
        onResize = { id, height ->
            // Measurement only reports geometry. Never request another measurement from inside
            // onSizeChanged: that feedback loop remeasured the long message twice every frame.
            if (id == activeId) latestHeight = height
        },
        onSettled = { id ->
            if (id == activeId) {
                settled = true
                if (follower?.isActive != true) clear()
            }
        },
    )
    fun onUserScroll() {
        follower?.cancel()
        clear()
        // A manual gesture chooses a new browsing focus; do not reuse an old bottom-follow intent.
        openedAtBottom.clear()
    }
    private fun followAnimation(id: String, anchor: DisclosureAnchorPlan) {
        follower = scope.launch {
            try {
                val progress = Animatable(0f)
                val timing = if (bottomCollapse) null else launch {
                    progress.animateTo(1f, FreeChatAnimation.disclosureFollowTween)
                }
                // One mutually exclusive scroll session, at most one update per display frame.
                // A user's gesture cancels it. Ordinary browsing uses LazyColumn's stable keys
                // directly, so all content above a disclosure stays exactly where it was.
                list.scroll {
                    var applied = 0f
                    while (activeId == id) {
                        withFrameNanos { }
                        if (activeId != id) break
                        if (bottomCollapse) {
                            if (list.canScrollForward) scrollBy(list.layoutInfo.viewportSize.height.toFloat() * 2f)
                        } else {
                            // A 2,000px thought can exceed the 400px reveal cap in its very first
                            // spring frame. Directly following that height looked like two jumps.
                            // Ease the bounded reveal independently; never scroll beyond real content.
                            val desired = (anchor.requestedOffset(latestHeight) - anchor.scrollOffset) * progress.value
                            val delta = desired - applied
                            if (kotlin.math.abs(delta) >= 0.5f) applied += scrollBy(delta)
                        }
                        if (settled && timing?.isActive != true) break
                    }
                }
            } finally {
                if (activeId == id) clear()
            }
        }
    }
    private fun clear() { activeId = null; plan = null; bottomCollapse = false }
}
