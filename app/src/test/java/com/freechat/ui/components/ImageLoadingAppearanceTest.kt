package com.freechat.ui.components

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.freechat.ui.theme.*
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageLoadingAppearanceTest {
    @Test fun darkDotsAreSubduedButNotLostInTheBase() {
        for (theme in listOf(DarkColors, OledDarkColors, BlueDarkColors, BlueOledDarkColors,
            WhiteDarkColors, WhiteOledDarkColors)) {
            val appearance = ImageLoadingAppearance(theme)
            val base = appearance.base.compositeOver(theme.Background)
            val dot = appearance.dot.compositeOver(base)
            val ratio = (dot.luminance() + .05f) / (base.luminance() + .05f)
            assertTrue("Dark point field needs a visible silhouette: $ratio", ratio >= 1.35f)
        }
    }

}
