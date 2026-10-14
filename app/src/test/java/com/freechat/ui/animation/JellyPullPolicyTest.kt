package com.freechat.ui.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyPullPolicyTest {
    @Test fun pullFollowsTheFingerThenSaturatesWithoutHittingAWall() {
        val d = 260f
        assertEquals(0f, JellyPullPolicy.offset(0f, d), 0f)
        // 小幅 1:1 跟手（30px 手指位移 ≥ 28px 页面位移）
        assertTrue(JellyPullPolicy.offset(30f, d) >= 28f)
        // 大幅渐近封顶：永不越界、无硬撞墙（单调且上界 d）
        var prev = 0f
        for (x in 0..4000 step 25) {
            val y = JellyPullPolicy.offset(x.toFloat(), d)
            assertTrue("monotone at $x", y >= prev)
            assertTrue("bounded at $x", y <= d)
            prev = y
        }
        // 极限拉伸渐近封顶（float 下 tanh 饱和到 1.0，上界触达但绝不越过）
        assertTrue(JellyPullPolicy.offset(10_000f, d) <= d)
    }

    @Test fun overshootPassesSmoothlyThroughZero() {
        // 果冻回弹过冲时 raw 为负：奇函数对称，回弹不撞零点
        val d = 260f
        assertEquals(-JellyPullPolicy.offset(80f, d), JellyPullPolicy.offset(-80f, d), 0.001f)
        assertEquals(0f, JellyPullPolicy.offset(0f, 0f), 0f) // degenerate 维度不炸
    }
}
