package com.freechat.ui.components

import org.junit.Assert.*
import org.junit.Test

class TypewriterRevealTest {

    @Test fun thirtyCharactersPerSecondPacesTheReveal() {
        // 用户口径：1 秒 30 个字。
        assertEquals(1800L, TypewriterPolicy.durationMs(90)) // 1.1.29.1 ④：50 字/秒
        assertEquals(0L, TypewriterPolicy.durationMs(0))
    }

    @Test fun revealIsMonotoneAndBounded() {
        assertEquals(0, TypewriterPolicy.visibleCodePoints(0f, 90))
        assertEquals(45, TypewriterPolicy.visibleCodePoints(.5f, 90))
        assertEquals(90, TypewriterPolicy.visibleCodePoints(1f, 90))
        assertEquals(0, TypewriterPolicy.visibleCodePoints(-1f, 90))
        assertEquals(90, TypewriterPolicy.visibleCodePoints(2f, 90))
    }

    @Test fun truncationCountsCodePointsSoEmojiNeverSplit() {
        // emoji 是 UTF-16 代理对：按 char 截会出半截乱码，必须按 code point。
        val s = "🐱ab"
        assertEquals(0, TypewriterPolicy.utf16End(s, 0))
        assertEquals(2, TypewriterPolicy.utf16End(s, 1)) // 截完仍是完整的猫
        assertEquals(3, TypewriterPolicy.utf16End(s, 2))
        assertEquals(s.length, TypewriterPolicy.utf16End(s, 99))
    }

    @Test fun aMessagePlaysOnlyOnceAndOldMessagesNeverPlay() {
        val now = System.currentTimeMillis()
        val fresh = "tw-fresh-${System.nanoTime()}"
        assertTrue(TypewriterLedger.claim(fresh, now - 100, now))
        assertFalse(TypewriterLedger.claim(fresh, now - 100, now)) // 回看不重播
        // 历史消息（超过新鲜窗）与未来时间戳都不演。
        val window = com.freechat.ui.animation.MotionPolicy.MessageFreshnessMs
        assertFalse(TypewriterLedger.claim("tw-old-${System.nanoTime()}", now - window - 1, now))
        assertFalse(TypewriterLedger.claim("tw-future-${System.nanoTime()}", now + 60_000, now))
    }
}
