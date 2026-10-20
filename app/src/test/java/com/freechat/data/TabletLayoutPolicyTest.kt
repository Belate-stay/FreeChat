package com.freechat.data

import com.freechat.ui.adaptive.TabletLayoutPolicy
import com.freechat.ui.adaptive.TabletPane
import org.junit.Assert.*
import org.junit.Test

class TabletLayoutPolicyTest {
    @Test fun phoneLandscapeIsNotATablet() {
        assertFalse(TabletLayoutPolicy.isTablet(393))
        assertTrue(TabletLayoutPolicy.isTablet(800))
    }

    @Test fun portraitAndLandscapeBothKeepUsefulColumns() {
        for (width in listOf(600f, 800f, 1280f)) {
            val panes = TabletLayoutPolicy.panes(width, balanced = false)
            assertTrue(panes.split)
            assertTrue(panes.listWidth >= 220f)
            assertTrue(panes.detailWidth >= 360f)
            assertEquals(width, panes.listWidth + panes.detailWidth + panes.gap, .01f)
        }
    }

    @Test fun narrowTabletWindowKeepsTheFocusedPane() {
        val panes = TabletLayoutPolicy.panes(480f, focused = TabletPane.LIST)
        assertFalse(panes.split)
        assertEquals(480f, panes.listWidth, .01f)
        assertFalse(panes.detailVisible)
    }

    @Test fun fullscreenAndRestoringDoNotLoseTheOtherPane() {
        val full = TabletLayoutPolicy.panes(1000f, fullscreen = TabletPane.DETAIL)
        assertEquals(1000f, full.detailWidth, .01f)
        assertFalse(full.listVisible)
        assertTrue(full.listWidth >= 220f)
        assertTrue(TabletLayoutPolicy.panes(1000f).split)
    }

    @Test fun supportingEditorGetsEnoughWidthInPortrait() {
        val panes = TabletLayoutPolicy.panes(800f, balanced = true)
        assertTrue(panes.listWidth >= 360f)
        assertTrue(panes.detailWidth >= 360f)
    }

    @Test fun growingANarrowWindowRestoresSplitRatherThanPersistingItsNavigationOverride() {
        assertNull(TabletLayoutPolicy.fullscreenAfterResize(true, false, TabletPane.DETAIL))
        assertNull(TabletLayoutPolicy.fullscreenAfterResize(true, false, TabletPane.LIST))
        assertEquals(TabletPane.DETAIL, TabletLayoutPolicy.fullscreenAfterResize(false, false, TabletPane.DETAIL))
        assertEquals(TabletPane.LIST, TabletLayoutPolicy.fullscreenAfterResize(true, true, TabletPane.LIST))
    }
}
