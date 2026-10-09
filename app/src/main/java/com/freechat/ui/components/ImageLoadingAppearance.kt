package com.freechat.ui.components

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.freechat.ui.theme.FreeChatColors

/** Shared, immutable paint tokens for both generation and the final image's loading surface. */
internal class ImageLoadingAppearance(colors: FreeChatColors) {
    val dark = colors.TextPrimary.luminance() > .5f
    val base = lerp(colors.Surface, colors.Primary, .12f).copy(alpha = if (dark) .10f else .06f)
    val dot = lerp(colors.Primary, colors.TextSecondary, .18f).copy(alpha = .26f)
    val gloss = lerp(colors.Primary, colors.TextPrimary, .10f).copy(alpha = if (dark) .32f else .36f)
}
