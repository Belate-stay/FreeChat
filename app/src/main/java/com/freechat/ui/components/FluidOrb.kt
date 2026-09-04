package com.freechat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.freechat.ui.animation.FreeChatAnimation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 流体圆点加载动画 — 参考 Siri Fluid Dots（metaball 融合效果）
 *
 * N 个彩色圆点绕中心轨道旋转，颜色随色相环流动。
 * 使用径向渐变 + 加色混合（Plus），重叠处变亮形成融球质感。
 */
@Composable
fun FluidOrb(modifier: Modifier = Modifier, dotCount: Int = 6) {
    val transition = rememberInfiniteTransition(label = "fluid_orb")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = FreeChatAnimation.fluidOrbCycle,
            repeatMode = RepeatMode.Restart
        ),
        label = "fluid_orb_phase"
    )

    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minDim = min(size.width, size.height)
        val radius = minDim * 0.16f
        val orbitBase = minDim * 0.19f
        val tau = 2f * PI.toFloat()

        for (i in 0 until dotCount) {
            val fi = i.toFloat()
            val ang = fi / dotCount * tau + phase * tau
            val orbit = orbitBase + sin(phase * tau * 2f + fi * 1.3f) * (minDim * 0.03f)
            val pos = Offset(
                center.x + orbit * cos(ang),
                center.y + orbit * sin(ang)
            )
            val hue = ((fi / dotCount) + phase) % 1f
            val color = Color.hsv(hue * 360f, 0.72f, 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = 0f)),
                    center = pos,
                    radius = radius
                ),
                radius = radius,
                center = pos,
                blendMode = BlendMode.Plus
            )
        }
    }
}
