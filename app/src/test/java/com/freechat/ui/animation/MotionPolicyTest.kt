package com.freechat.ui.animation

import org.junit.Assert.*
import org.junit.Test

class MotionPolicyTest {
    @Test fun firstPromptOfNewConversationIsNotSeededAsHistory() {
        val intent = FirstSendMotionIntent()
        intent.record("new", 10_000)
        val openedAt = intent.consume("new")!!
        val ledger = MessageEntranceLedger(emptyList(), openedAt)
        assertTrue(ledger.claim("first-prompt", 10_005, 10_020))
    }

    @Test fun returningToNewConversationDoesNotReuseFirstSendIntent() {
        val intent = FirstSendMotionIntent()
        intent.record("new", 10_000)
        assertEquals(10_000L, intent.consume("new"))
        assertNull(intent.consume("new"))
        val history = MessageEntranceLedger(listOf("first-prompt"), 10_040)
        assertFalse(history.claim("first-prompt", 10_005, 10_050))
    }

    @Test fun unrelatedNavigationOrRejectedSendCannotReuseIntent() {
        val intent = FirstSendMotionIntent()
        intent.record("new", 10_000)
        assertNull(intent.consume("other"))
        assertNull(intent.consume("new"))
        intent.record(null, 10_020)
        assertNull(intent.consume(null))
    }

    @Test fun historyNeverAnimatesEvenWhenAConversationWasJustOpened() {
        val ledger = MessageEntranceLedger(listOf("old"), 10_000)
        assertFalse(ledger.claim("old", 10_000, 10_010))
        assertFalse(ledger.claim("loaded-later", 9_999, 10_010))
    }

    @Test fun allNewMessagesShareOnceOnlyEntranceRegardlessOfTheirUiMode() {
        val ledger = MessageEntranceLedger(emptyList(), 10_000)
        for (id in listOf("standard-user", "wechat-user", "wechat-ai", "plot-ai", "action-ai", "image-wait")) {
            assertTrue(ledger.claim(id, 10_020, 10_040))
            assertFalse(ledger.claim(id, 10_020, 10_060))
        }
    }

    @Test fun streamToPersistedReplyNeverRestartsTheSameEntry() {
        val ledger = MessageEntranceLedger(emptyList(), 1_000)
        assertTrue(ledger.claim("reply-id", 1_100, 1_110))
        assertFalse(ledger.claim("reply-id", 1_900, 1_910))
    }

    @Test fun cancelledAnimationAndScrollOffscreenCannotReplay() {
        val ledger = MessageEntranceLedger(emptyList(), 1_000)
        assertTrue(ledger.claim("cancelled", 1_100, 1_110))
        assertFalse(ledger.claim("cancelled", 1_100, 1_140))
        assertFalse(ledger.claim("cancelled", 1_100, 5_000))
    }

    @Test fun staleOrFutureDatedMessageIsDisplayedWithoutMotion() {
        val ledger = MessageEntranceLedger(emptyList(), 1_000)
        assertFalse(ledger.claim("stale", 1_000, 5_001))
        assertFalse(ledger.claim("future-clock", 9_000, 5_000))
    }

    @Test fun newConversationHasItsOwnEntranceLedger() {
        val first = MessageEntranceLedger(emptyList(), 1_000)
        val second = MessageEntranceLedger(emptyList(), 2_000)
        assertTrue(first.claim("id", 1_010, 1_020))
        assertTrue(second.claim("id", 2_010, 2_020))
    }

    @Test fun messageMotionIsMonotonicBoundedAndNotDependentOnAnswerHeight() {
        var previousY = Float.POSITIVE_INFINITY
        var previousAlpha = -1f
        for (i in 0..100) {
            val p = i / 100f
            val y = MotionPolicy.messageTranslation(p, 44f)
            val alpha = MotionPolicy.messageAlpha(p)
            assertTrue(y in 0f..44f && y <= previousY)
            assertTrue(alpha in 0f..1f && alpha >= previousAlpha)
            previousY = y; previousAlpha = alpha
        }
        assertEquals(0f, MotionPolicy.messageTranslation(1.1f, 44f), 0f)
        assertEquals(44f, MotionPolicy.messageTranslation(-0.1f, 44f), 0f)
        assertEquals(0f, MotionPolicy.messageTranslation(0f, -12f), 0f)
    }

    @Test fun floatingLocatorKeepsComfortableEdgeGutterAndControlsUseSubtlePress() {
        assertEquals(12, MotionPolicy.LocatorGutterDp)
        assertTrue(MotionPolicy.LocatorTravelDp > MotionPolicy.LocatorGutterDp)
        assertTrue(MotionPolicy.PressScale in 0.94f..0.98f)
        assertTrue(MotionPolicy.HeaderPressScale in 0.92f..0.96f)
    }
}
