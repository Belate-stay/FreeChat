package com.freechat.ui.animation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beta 1.1.26.8 契约：快速反复滑动不许断触。
 * ①手指一动必须叫停回弹 spring（否则 settle 每帧覆写 raw 与手指抢值，列表滚不动）；
 * ②onPreFling 不许吞 fling（吞掉=滑动过程中突然停住）。
 */
class JellyOverscrollContinuityTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun newTouchCancelsSettleBeforeReclaimingStretch() {
        val jelly = source("ui/animation/JellyPull.kt")
        val preScroll = jelly.substringAfter("override fun onPreScroll")
            .substringBefore("override fun onPostScroll")
        assertTrue("手指一动先取消回弹动画", preScroll.contains("settleJob?.cancel()"))
        assertTrue("取消发生在回收拉伸量之前",
            preScroll.indexOf("settleJob?.cancel()") < preScroll.indexOf("raw += eat"))
    }

    @Test fun flingIsNeverSwallowedByTheOverscrollConnection() {
        val jelly = source("ui/animation/JellyPull.kt")
        val preFling = jelly.substringAfter("override suspend fun onPreFling")
            .substringBefore("override suspend fun onPostFling")
        assertFalse("不许吞 fling（吞=反复快滑突然停）", preFling.contains("return available"))
        assertTrue("拉伸时只并行回弹，fling 交还列表", preFling.contains("if (raw != 0f) settle()"))
    }
}
