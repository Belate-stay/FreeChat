package com.freechat.ui.theme

import com.freechat.model.HeaderBarStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderBarStyleTest {
    @Test fun persistedOrdinalsAreStable() {
        assertEquals(HeaderBarStyle.CARD, HeaderBarStyle.fromOrdinal(0))
        assertEquals(HeaderBarStyle.CUTOUT, HeaderBarStyle.fromOrdinal(1))
    }

    @Test fun invalidPreferenceKeepsExistingCards() {
        assertEquals(HeaderBarStyle.CARD, HeaderBarStyle.fromOrdinal(-1))
        assertEquals(HeaderBarStyle.CARD, HeaderBarStyle.fromOrdinal(Int.MAX_VALUE))
    }

    @Test fun cutoutHasNoBackingWhileKeepingMaterialIndependent() {
        assertEquals(0f, headerCardStrength(1f, 0f), 0f)
        assertEquals(1f, headerCardStrength(1f, 1f), 0f)
    }

    @Test fun ordinaryMaterialNeverShowsCardsRegardlessOfPreference() {
        assertEquals(0f, headerCardStrength(0f, 1f), 0f)
        assertEquals(0f, headerCardStrength(0f, 0f), 0f)
    }

    @Test fun bothTransitionsBlendSmoothlyWithoutOvershooting() {
        assertEquals(0.25f, headerCardStrength(0.5f, 0.5f), 0f)
        assertEquals(1f, headerCardStrength(2f, 2f), 0f)
        assertEquals(0f, headerCardStrength(-1f, 1f), 0f)
    }
}
