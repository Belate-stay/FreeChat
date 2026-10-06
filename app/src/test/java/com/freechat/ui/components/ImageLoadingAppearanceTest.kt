package com.freechat.ui.components

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.freechat.ui.theme.*
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ImageLoadingAppearanceTest {
    // 1.0.99.3 user decision: caption faded to feel subdued; readability contract adjusted accordingly.
    @Test fun fadedCaptionStaysVisibleWithoutDominating() {
        val themes = listOf(LightColors, DarkColors, OledDarkColors, BlueLightColors,
            BlueDarkColors, BlueOledDarkColors, WhiteColors, WhiteDarkColors, WhiteOledDarkColors) +
            listOf(0xff000000.toInt(), 0xffffffff.toInt(), 0xff005fff.toInt(), 0xffff0055.toInt()).flatMap {
                argb -> listOf(customColors(argb, false, false), customColors(argb, true, false),
                    customColors(argb, true, true))
            }
        val problems = mutableListOf<String>()
        for (theme in themes) {
            val appearance = ImageLoadingAppearance(theme)
            // Caption alpha must be in the faded-but-readable range.
            assertTrue("${theme.javaClass.simpleName}: captionAlpha ${appearance.captionAlpha} not in 0.5..0.8",
                appearance.captionAlpha in 0.5f..0.8f)
            // Caption ink contrast against misty background (alpha dims but ink must stay distinguishable).
            var minimum = Float.POSITIVE_INFINITY
            for (base in appearance.base) {
                var background = base
                for (tint in appearance.palette.take(3)) {
                    background = tint.copy(alpha = appearance.mistOpacity).compositeOver(background)
                    val fg = appearance.caption.luminance()
                    val bg = background.luminance()
                    val ratio = (max(fg, bg) + .05f) / (min(fg, bg) + .05f)
                    minimum = min(minimum, ratio)
                }
            }
            if (minimum < 2.5f) problems += "${theme.javaClass.simpleName}: $minimum"
        }
        assertTrue("Faded caption ink needs 2.5:1 contrast against misty background: $problems", problems.isEmpty())
    }
}
