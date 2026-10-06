package com.freechat.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeumorphToneTest {
    private val themes = listOf(
        LightColors, BlueLightColors, WhiteColors, DarkColors, BlueDarkColors, WhiteDarkColors,
        OledDarkColors, BlueOledDarkColors, WhiteOledDarkColors,
        customColors(0xFFDA2FBC.toInt(), false, false),
    )

    @Test
    fun raisedMaterialSharesBackgroundAndRetainsOpposingShadows() {
        themes.forEach { colors ->
            val dark = colors.TextPrimary.luminance() > 0.5f
            val tone = neumorphTone(colors, dark, selected = false)
            assertTrue(tone.light.luminance() > tone.dark.luminance())
            assertTrue(tone.offset.value > 0f && tone.blur > tone.offset)
            assertEquals(1f, tone.faceAlpha)
            val base = if (colors.Background.luminance() < 0.02f) colors.Surface else colors.Background
            assertTrue(kotlin.math.abs(tone.faceTop.luminance() - base.luminance()) < 0.12f)
            assertTrue(kotlin.math.abs(tone.faceBottom.luminance() - base.luminance()) < 0.12f)
            listOf(tone.faceTop, tone.faceBottom).forEach { face ->
                assertTrue("body ${colors.javaClass.simpleName}", contrastRatio(colors.TextPrimary, face) >= 4.5f)
                assertTrue("secondary ${colors.javaClass.simpleName}", contrastRatio(colors.TextSecondary, face) >= 4.5f)
                assertTrue("tertiary ${colors.javaClass.simpleName}", contrastRatio(colors.TextTertiary, face) >= 4.5f)
            }
        }
    }

    @Test
    fun compactMaterialIsSolidAndHasProportionalDepth() {
        val tone = neumorphTone(BlueLightColors, false, selected = false)
        assertTrue(tone.light.luminance() > tone.dark.luminance())
        assertEquals(1f, tone.faceAlpha)
        assertTrue(tone.offset.value >= 4f)
        val compact = neumorphTone(BlueLightColors, false, selected = false, compact = true)
        assertEquals(1f, compact.faceAlpha)
        assertTrue(compact.offset < tone.offset)
        assertTrue(compact.blur < tone.blur)
    }
}
