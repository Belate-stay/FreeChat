package com.freechat.ui.components

import org.junit.Assert.*
import org.junit.Test

class VoiceInputMotionTest {
    @Test fun composerStartsFullSizeAndEndsInvisibleWithoutOvershooting() {
        val full = VoiceInputMotion.pose(0f)
        assertEquals(1f, full.scaleX, 0f)
        assertEquals(1f, full.scaleY, 0f)
        assertEquals(1f, full.alpha, 0f)
        val hidden = VoiceInputMotion.pose(1f)
        assertEquals(0f, hidden.alpha, 0f)
        assertTrue(hidden.scaleX < .01f)
        for (i in 0..100) {
            val pose = VoiceInputMotion.pose(i / 100f)
            assertTrue(pose.scaleX in 0f..1f && pose.scaleY in 0f..1f && pose.alpha in 0f..1f)
        }
    }

    @Test fun taperIsIdentityAtBothEndpointsAndNarrowsTheFarEndDuringTravel() {
        for (x in listOf(0f, .3f, .8f, 1f)) {
            assertEquals(1f, VoiceInputMotion.taper(0f, x), .0001f)
            assertEquals(1f, VoiceInputMotion.taper(1f, x), .0001f)
        }
        assertTrue(VoiceInputMotion.taper(.5f, .1f) < VoiceInputMotion.taper(.5f, .9f))
        assertTrue(VoiceInputMotion.taper(.5f, .1f) > .2f)
    }

    @Test fun finalReleasePositionUsesTheRealButtonAndSmallTouchTolerance() {
        assertTrue(VoiceInputMotion.isInsideButton(20f, 20f, 40f, 40f, 8f))
        assertTrue(VoiceInputMotion.isInsideButton(20f, -5f, 40f, 40f, 8f))
        assertFalse(VoiceInputMotion.isInsideButton(20f, -12f, 40f, 40f, 8f))
        assertFalse(VoiceInputMotion.isInsideButton(60f, 20f, 40f, 40f, 8f))
        assertFalse(VoiceInputMotion.isInsideButton(0f, 0f, 40f, 40f, 0f))
        // The complete and compact input styles need not have identical measured bounds.
        assertTrue(VoiceInputMotion.isInsideButton(26f, 26f, 52f, 52f, 8f))
    }

    @Test fun visualBarsInterpolateOrderedActualBandsAndRemainFlatAtSilence() {
        val silent = FloatArray(16)
        for (i in 0..100) assertEquals(0f, VoiceInputMotion.bandAt(silent, i / 100f), 0f)
        val low = FloatArray(16).also { it[1] = 1f }
        val high = FloatArray(16).also { it[14] = 1f }
        assertTrue(VoiceInputMotion.bandAt(low, 1f / 15f) > VoiceInputMotion.bandAt(low, 14f / 15f))
        assertTrue(VoiceInputMotion.bandAt(high, 14f / 15f) > VoiceInputMotion.bandAt(high, 1f / 15f))
        assertEquals(.5f, VoiceInputMotion.bandAt(FloatArray(16) { .5f }, .4f), .0001f)
        assertEquals(0f, VoiceInputMotion.bandAt(floatArrayOf(), .5f), 0f)
    }
}
