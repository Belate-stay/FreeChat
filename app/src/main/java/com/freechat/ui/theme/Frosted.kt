package com.freechat.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/**
 * 卡片统一材质：实色卡片 + 底部外阴影 + 顶部高光 + 描边。
 * 高级材质下更精致：明显的外阴影 + 顶部白色高光 + 浅描边；非高级材质回退实色。
 * selected=true 用主题色淡底（AccentMuted）表示选中态，绝不发黑。
 */
fun Modifier.frostedCard(
    hazeState: HazeState?,
    colors: FreeChatColors,
    advancedMaterial: Boolean,
    shape: Shape,
    blur: Dp = 24.dp,
    elevation: Dp = 8.dp,
    selected: Boolean = false,
    fallback: Color = colors.Surface
): Modifier {
    val isDark = colors.TextPrimary.luminance() > 0.5f
    return this
        .shadow(
            elevation = if (advancedMaterial) elevation else 2.dp,
            shape = shape,
            clip = advancedMaterial,  // 高级材质（Haze 磨砂）必须 clip=false 避免黑圈；非高级材质实色卡用 clip=true，避免阴影漏成硬边浅色块
            ambientColor = Color.Black.copy(alpha = 0.10f),
            spotColor = Color.Black.copy(alpha = 0.20f)
        )
        .clip(shape)
        .then(
            if (advancedMaterial) {
                Modifier
                    .background(if (selected) colors.AccentMuted else colors.SurfaceVariant)
                    .background(
                        // 顶部高光：淡淡一层白，向下渐隐，模拟玻璃反光
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.06f else 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            } else {
                Modifier.background(if (selected) colors.AccentMuted else fallback)
            }
        )
        .then(
            if (advancedMaterial) Modifier.border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.35f),
                shape
            ) else Modifier
        )
}
