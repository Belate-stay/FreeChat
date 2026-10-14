package com.freechat.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 只做一件事：**在底部展开**思考/信息源时把视口钉在内容末端（1.1.29.9 工单⑤二次返修）。
 *
 * 折叠**不跟**：收缩让总内容变短，LazyColumn 的钳位在测量当帧就把视口逐帧拉回末端 ——
 * 钳位量恰好等于折叠 tween 每帧的收缩量，天然丝滑贴底；再加跟随就是**双重位移**
 * （每帧多退一格、一路退到半空才停 —— 实机截图「折叠后不在底部、闪跳」的病根）。
 * 浏览态（不在底部）折叠同理零干预：不过冲时钳位是 no-op，锚点天然保留。
 *
 * 展开在底部时没有钳位可用（内容变长不会过冲），必须主动逐帧把增长量推成滚动 ——
 * 用 onResize 报的高度差累计（跟随总量=高度总量，最终精确落在末端），不用缓动逼近：
 * 缓动追移动目标会在动画结束时停在半路。
 */
class DisclosureScrollAnchor(private val list: LazyListState, private val scope: CoroutineScope) {
    val trailingSpacePx = 0
    private var activeId: String? = null
    private var latestHeight = 0
    private var settled = false
    private var follower: Job? = null

    val callbacks = DisclosureCallbacks(
        onToggle = { id, height, _, expanding ->
            follower?.cancel()
            clear()
            // 折叠零干预（钳位贴底）；非底部展开零干预（浏览锚点）
            if (expanding && !list.canScrollForward) {
                activeId = id
                latestHeight = height
                settled = false
                followBottom(id)
            }
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
    }

    private fun followBottom(id: String) {
        follower = scope.launch {
            try {
                // One mutually exclusive scroll session, at most one update per display frame.
                // A user's gesture cancels it.
                list.scroll {
                    var followed = latestHeight
                    while (activeId == id) {
                        withFrameNanos { }
                        if (activeId != id) break
                        val dh = latestHeight - followed
                        if (dh > 0) {
                            scrollBy(dh.toFloat())
                            followed = latestHeight
                        }
                        if (settled) break
                    }
                }
            } finally {
                if (activeId == id) clear()
            }
        }
    }

    private fun clear() { activeId = null }
}
