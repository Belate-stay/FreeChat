package com.freechat.ui.animation

import com.freechat.ui.components.TypewriterLedger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationReplayTest {
    @Test fun markThenConsumeIsOneShot() {
        AnimationReplay.mark("replay-once")
        assertTrue(AnimationReplay.consume("replay-once"))
        assertFalse(AnimationReplay.consume("replay-once"))
    }

    @Test fun consumeWithoutMarkIsFalse() {
        assertFalse(AnimationReplay.consume("never-marked"))
    }

    @Test fun blankIdIsIgnored() {
        AnimationReplay.mark("")
        assertFalse(AnimationReplay.consume(""))
    }

    @Test fun staleTimestampStillAnimatesWhenMarked() {
        AnimationReplay.mark("stale-replay")
        val ledger = MessageEntranceLedger(emptyList(), openedAtMs = 1_000)
        // 时间戳年龄 9s > MessageFreshnessMs，若无旁路本应拒绝
        assertTrue(ledger.claim("stale-replay", timestampMs = 1_000, nowMs = 10_000))
        // 一次性：同 id 第二次不再播
        assertFalse(ledger.claim("stale-replay", timestampMs = 1_000, nowMs = 10_010))
    }

    @Test fun markedIdIgnoresOpenedAtGateToo() {
        AnimationReplay.mark("before-open")
        val ledger = MessageEntranceLedger(emptyList(), openedAtMs = 5_000)
        assertTrue(ledger.claim("before-open", timestampMs = 1_000, nowMs = 2_000))
    }

    @Test fun typewriterLedgerAlsoHonorsReplayMark() {
        AnimationReplay.mark("typewriter-replay")
        assertTrue(TypewriterLedger.claim("typewriter-replay", timestampMs = 1_000, nowMs = 99_000))
        assertFalse(TypewriterLedger.claim("typewriter-replay", timestampMs = 1_000, nowMs = 99_010))
    }

    @Test fun unmarkedStaleTypewriterStillRefuses() {
        assertFalse(TypewriterLedger.claim("typewriter-stale-unmarked", timestampMs = 1_000, nowMs = 99_000))
    }

    @Test fun exitDirectionMapsRoleToScreenSide() {
        // 用户消息从右侧移出 (+1)，AI 消息从左侧移出 (-1)
        assertTrue(MotionPolicy.exitDirection(isUser = true) > 0f)
        assertTrue(MotionPolicy.exitDirection(isUser = false) < 0f)
    }

    @Test fun exitAlphaLagsTranslationAndStaysBounded() {
        // 起步阶段保持不透明（位移先走），尾段才掉到 0
        assertTrue(MotionPolicy.exitAlpha(0f) == 1f)
        assertTrue(MotionPolicy.exitAlpha(0.35f) == 1f)
        assertTrue(MotionPolicy.exitAlpha(1f) == 0f)
        var previous = Float.POSITIVE_INFINITY
        for (i in 0..20) {
            val a = MotionPolicy.exitAlpha(i / 20f)
            assertTrue(a in 0f..1f && a <= previous)
            previous = a
        }
    }
}
