package com.freechat.data

import org.junit.Assert.*
import org.junit.Test

class ReasoningModePresentationTest {
    @Test fun companionNeverShowsReasoningEvenIfEverySwitchIsEnabled() {
        assertFalse(SettingsPresentationPolicy.reasoningVisible(true, true, true, companion = true))
    }

    @Test fun standardModeStillRespectsTheEffectivePreference() {
        assertTrue(SettingsPresentationPolicy.reasoningVisible(true, true, true, companion = false))
        assertFalse(SettingsPresentationPolicy.reasoningVisible(false, true, true, companion = false))
        assertFalse(SettingsPresentationPolicy.reasoningVisible(true, false, true, companion = false))
    }
}
