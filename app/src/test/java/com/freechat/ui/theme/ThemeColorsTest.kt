package com.freechat.ui.theme

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorsTest {
    private val presets = listOf(
        LightColors, BlueLightColors, WhiteColors,
        DarkColors, BlueDarkColors, WhiteDarkColors,
        OledDarkColors, BlueOledDarkColors, WhiteOledDarkColors,
        customColors(0xFF26735A.toInt(), false, false),
        customColors(0xFF26735A.toInt(), true, false),
        customColors(0xFF26735A.toInt(), true, true),
        customColors(0xFFA3444B.toInt(), false, false),
        customColors(0xFFA3444B.toInt(), true, false),
        customColors(0xFFA3444B.toInt(), true, true),
    )

    @Test
    fun presetTextAndSelectionsRemainReadable() {
        presets.forEach { colors ->
            assertTrue("${colors.javaClass.simpleName} selection", contrastRatio(colors.OnPrimary, colors.Primary) >= 4.5f)
            assertTrue("${colors.javaClass.simpleName} bubble", contrastRatio(colors.UserBubbleText, colors.UserBubble) >= 4.5f)
            assertTrue("${colors.javaClass.simpleName} body", contrastRatio(colors.TextPrimary, colors.Background) >= 4.5f)
            assertTrue("${colors.javaClass.simpleName} secondary", contrastRatio(colors.TextSecondary, colors.Surface) >= 4.5f)
            assertTrue("${colors.javaClass.simpleName} tertiary", contrastRatio(colors.TextTertiary, colors.Surface) >= 4.5f)
        }
    }

    @Test
    fun customColorsKeepReadableControlsAtExtremeHuesAndOpacities() {
        val rgb = listOf(0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x800080)
        val alpha = listOf(0, 32, 128, 255)
        for (dark in listOf(false, true)) for (oled in listOf(false, true)) {
            if (oled && !dark) continue
            for (color in rgb) for (a in alpha) {
                val colors = customColors((a shl 24) or color, dark, oled)
                assertTrue("accent $color/$a/$dark/$oled", contrastRatio(colors.OnPrimary, colors.Primary) >= 4.5f)
                assertTrue("visibility $color/$a/$dark/$oled", contrastRatio(colors.Primary, colors.SurfaceVariant) >= 4.5f)
                assertTrue("body $color/$a/$dark/$oled", contrastRatio(colors.TextPrimary, colors.Background) >= 4.5f)
            }
        }
    }

    @Test
    fun opacityChangesAccentStrengthWithoutMakingControlsTransparent() {
        val transparent = customColors(0x00346C98, false, false)
        val opaque = customColors(0xFF346C98.toInt(), false, false)
        assertNotEquals(transparent.Primary, opaque.Primary)
        assertTrue(transparent.Primary.alpha == 1f)
        assertTrue(opaque.Primary.alpha == 1f)
    }
}
