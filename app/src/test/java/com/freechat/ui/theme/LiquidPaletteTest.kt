package com.freechat.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.freechat.model.ColorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidPaletteTest {
    @Test
    fun accentPresetsDoNotChangeTheLiquidMaterialOrBackgroundPalette() {
        val reference = liquidPalette(ColorTheme.WHITE, false, false)
        ColorTheme.entries.forEach { assertEquals(reference, liquidPalette(it, false, false)) }
        assertTrue(reference.field.all { it.alpha == 1f })
        assertTrue(reference.glow.first().alpha > 0.7f)
        assertTrue(reference.bloom.first().alpha > 0.6f)
        assertTrue(reference.glow.first().blue - reference.glow.first().red > 0.15f)
        assertTrue(reference.bloom.first().blue - reference.bloom.first().green > 0.12f)
    }

    @Test
    fun colorfulBackgroundRetainsReadableSecondaryAndTertiaryText() {
        val palette = liquidPalette(ColorTheme.WHITE, false, false)
        val themes = listOf(LightColors, BlueLightColors, WhiteColors,
            customColors(0xFFDA2FBC.toInt(), false, false))
        // Conservative envelope: every stop combination, including overlapping light centers.
        palette.field.forEach { field ->
            palette.glow.forEach { glow ->
                palette.bloom.forEach { bloom ->
                    val background = bloom.compositeOver(glow.compositeOver(field))
                    listOf(background, LiquidFaceHighlight.compositeOver(background),
                        LiquidFaceShade.compositeOver(background)).forEach { surface ->
                        themes.forEach { theme ->
                            listOf(theme.TextPrimary, theme.TextSecondary, theme.TextTertiary).forEach { ink ->
                                assertTrue("contrast ${contrastRatio(ink, surface)} for $ink on $surface",
                                    contrastRatio(ink, surface) >= 4.5f)
                            }
                        }
                    }
                }
            }
        }
    }
}
