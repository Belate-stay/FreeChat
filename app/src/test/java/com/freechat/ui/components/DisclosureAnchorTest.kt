package com.freechat.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DisclosureAnchorTest {
    @Test fun expansionKeepsTheHeaderAndAllEarlierContentAtTheSamePosition() {
        val plan = DisclosureAnchorPlan(2, 40, 96)
        listOf(96, 110, 180, 300, 560).forEach { height ->
            val upwardShift = plan.requestedOffset(height) - 40
            assertEquals(0, upwardShift)
            assertEquals(900, 900 - upwardShift)
        }
    }

    @Test fun veryLongThoughtsUnfoldDownwardsWithoutScrolling() {
        val plan = DisclosureAnchorPlan(3, 20, 96)
        assertEquals(20, plan.requestedOffset(1200))
        assertEquals(0, plan.trailingSpace(1200))
    }

    @Test fun collapseNeverManufacturesABlankTail() {
        val plan = DisclosureAnchorPlan(4, 600, 500)
        assertEquals(600, plan.requestedOffset(96))
        assertEquals(0, plan.trailingSpace(96))
    }

    @Test fun repeatedOrReversedAnimationFramesHaveNoAccumulatedScrollDrift() {
        val plan = DisclosureAnchorPlan(1, 70, 96)
        listOf(150, 200, 150, 200).forEach { height -> assertEquals(70, plan.requestedOffset(height)) }
        assertEquals(70, plan.requestedOffset(96))
    }

    @Test fun collapseAwayFromBottomNeedsNoAdditionalBlankSpace() {
        assertEquals(0, DisclosureAnchorPlan(1, 12, 500, availableBelow = 800).trailingSpace(96))
    }

    @Test fun collapseHasNoReservedTailAtAnyAnimationFrame() {
        val collapse = DisclosureAnchorPlan(2, 500, 1600, availableBelow = 1000)
        assertEquals(0, collapse.trailingSpace(1200))
        assertEquals(0, collapse.trailingSpace(222))
        assertEquals(0, collapse.trailingSpace(96))
    }

    @Test fun bottomExpansionRevealsFirstEntriesButNeverSkipsItsHeader() {
        val plan = DisclosureAnchorPlan(2, 40, 96, followBottom = true, maximumFollow = 360)
        assertEquals(40, plan.requestedOffset(96))
        assertEquals(144, plan.requestedOffset(200))
        assertEquals(400, plan.requestedOffset(2400))
        assertEquals(0, plan.trailingSpace(2400))
    }

    @Test fun fullExpansionAndCollapseRetraceTheSameOffsets() {
        val expand = DisclosureAnchorPlan(1, 40, 96, initialTrailingSpace = 300)
        val endOffset = expand.requestedOffset(396)
        val collapse = DisclosureAnchorPlan(1, endOffset, 396)
        listOf(396, 280, 180, 96).forEach { height ->
            assertEquals(expand.requestedOffset(height), collapse.requestedOffset(height))
            assertEquals(expand.trailingSpace(height), collapse.trailingSpace(height))
        }
    }
}
