package com.freechat.data

import com.freechat.ui.adaptive.TabletNavigationPolicy
import com.freechat.ui.adaptive.TabletPane
import org.junit.Assert.*
import org.junit.Test

class TabletDisplayNavigationTest {
    @Test fun cameraStartsAtTheActualHomeLayout() {
        val frame = TabletNavigationPolicy.camera(1280f, 340f, 610f, 0f)
        assertEquals(0f, frame.listX, .001f)
        assertEquals(341f, frame.chatX, .001f)
        assertEquals(939f, frame.chatWidth, .001f)
        assertEquals(1280f, frame.auxiliaryX, .001f)
    }

    @Test fun cameraEndsWithChatAndAuxiliaryPaneWithoutASeam() {
        val frame = TabletNavigationPolicy.camera(1280f, 340f, 610f, 1f)
        assertEquals(-341f, frame.listX, .001f)
        assertEquals(0f, frame.chatX, .001f)
        assertEquals(610f, frame.chatWidth, .001f)
        assertEquals(611f, frame.auxiliaryX, .001f)
        assertEquals(610f, frame.dividerX, .001f)
    }

    @Test fun dividerIsAlwaysPushedByTheChatEdge() {
        for (step in 0..20) {
            val frame = TabletNavigationPolicy.camera(800f, 240f, 384f, step / 20f)
            assertEquals(frame.chatX + frame.chatWidth, frame.dividerX, .001f)
            assertEquals(frame.dividerX + step / 20f, frame.auxiliaryX, .001f)
        }
    }

    @Test fun floatingCardIsPortraitAndInsetAtBothEdges() {
        for ((width, height) in listOf(1280f to 800f, 800f to 1280f, 500f to 700f)) {
            val left = TabletNavigationPolicy.floatingBounds(width, height, TabletPane.LIST)
            val right = TabletNavigationPolicy.floatingBounds(width, height, TabletPane.DETAIL)
            assertTrue(left.width < left.height)
            assertTrue(left.x >= 20f && left.y >= 20f)
            assertTrue(right.x + right.width <= width - 20f)
            assertEquals(left.width, right.width, .001f)
            assertEquals(left.height, right.height, .001f)
        }
    }

    @Test fun paneBackReversesEntryAtItsOwnScreenEdge() {
        assertEquals(-1f, TabletNavigationPolicy.paneTravel(TabletPane.LIST, entering = true))
        assertEquals(1f, TabletNavigationPolicy.paneTravel(TabletPane.DETAIL, entering = true))
        assertEquals(-1f, TabletNavigationPolicy.paneTravel(TabletPane.LIST, entering = false))
        assertEquals(1f, TabletNavigationPolicy.paneTravel(TabletPane.DETAIL, entering = false))
    }

    @Test fun coverMovesOnlyTheIncomingPageAndBackMovesOnlyTheOutgoingPage() {
        for (side in TabletPane.entries) {
            val edge = if (side == TabletPane.LIST) -1f else 1f
            assertEquals(edge, TabletNavigationPolicy.pageTravel(side, entering = true, back = false))
            assertEquals(0f, TabletNavigationPolicy.pageTravel(side, entering = false, back = false))
            assertEquals(0f, TabletNavigationPolicy.pageTravel(side, entering = true, back = true))
            assertEquals(edge, TabletNavigationPolicy.pageTravel(side, entering = false, back = true))
        }
    }
}
