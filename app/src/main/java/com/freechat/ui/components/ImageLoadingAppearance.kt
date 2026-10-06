package com.freechat.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.freechat.ui.theme.FreeChatColors

/** Shared, immutable paint tokens for both generation and the final image's loading surface. */
internal class ImageLoadingAppearance(colors: FreeChatColors) {
    val dark = colors.TextPrimary.luminance() > .5f
    val palette = listOf(lerp(colors.Primary, Color(0xFF8ACAD8), .78f), Color(0xFFABA6D0),
        Color(0xFFD1B7C6), lerp(colors.Primary, Color(0xFFA6CDDC), .84f))
    val base = listOf(lerp(colors.Surface, palette[0], if (dark) .08f else .14f),
        lerp(colors.Surface, palette[1], if (dark) .06f else .07f),
        lerp(colors.Surface, palette[2], if (dark) .06f else .12f))
    // Several clouds overlap. Keep the dark composition dim enough for its secondary caption.
    val mistOpacity = if (dark) .045f else .20f
    val caption = if (dark) colors.TextSecondary else lerp(colors.TextSecondary, colors.TextPrimary, .12f)
    // 1.0.99.3 user decision: "图片生成中" caption faded to feel subdued, not prominent.
    val captionAlpha = if (dark) .62f else .60f
}
