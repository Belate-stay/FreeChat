package com.freechat.ui.theme

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeumorphControlTest {
    @Test
    fun checkedTracksStayAccentColoredWithTightDirectionalLighting() {
        listOf(LightColors, BlueLightColors, WhiteColors, DarkColors,
            customColors(0xFF7E42B3.toInt(), false, false)).forEach { colors ->
            val track = neumorphControlTone(colors.Primary, raised = false)
            assertEquals(1.dp, track.offset)
            assertEquals(2.dp, track.blur)
            assertTrue(track.light.luminance() >= colors.Primary.luminance())
            assertTrue(track.dark.luminance() <= colors.Primary.luminance())
            assertTrue(kotlin.math.abs(track.faceTop.luminance() - colors.Primary.luminance()) < 0.08f)
            val thumb = neumorphControlTone(colors.OnPrimary, raised = true)
            assertTrue(contrastRatio(colors.Primary, thumb.faceTop) >= 4.5f)
            assertTrue(contrastRatio(colors.Primary, thumb.faceBottom) >= 4.5f)
        }
    }
}
