package com.freechat.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidFrameTest {
    @Test
    fun cardsAndHeaderUseTheSameScreenCoordinatesEvenWhenScrollingOrTranslated() {
        val screen = Rect(0f, 0f, 1080f, 2400f)
        val origins = listOf(Offset.Zero, Offset(48f, 315f), Offset(640f, -100f), Offset(-972f, 1200f))
        val globalPoint = Offset(730f, 880f)
        origins.forEach { origin ->
            val frame = liquidFrameAtOrigin(screen, origin)
            val localPoint = globalPoint - origin
            assertEquals(screen.size, frame.size)
            assertEquals(globalPoint.x - screen.left, localPoint.x - frame.left, 0.001f)
            assertEquals(globalPoint.y - screen.top, localPoint.y - frame.top, 0.001f)
        }
    }

    @Test
    fun rootInsetsArePreservedWithoutDoubleCompensatingPageTranslation() {
        val screen = Rect(12f, 50f, 1092f, 2450f)
        val origin = Offset(300f, 180f)
        assertEquals(Rect(-288f, -130f, 792f, 2270f), liquidFrameAtOrigin(screen, origin))
    }
}
