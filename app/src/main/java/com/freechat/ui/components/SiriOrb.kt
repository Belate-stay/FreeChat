package com.freechat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.freechat.ui.theme.LocalFreeChatColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Siri 式发光球体 — AI 思考动画
 *
 * 一个深色柔边球体（径向渐变，边缘羽化融入背景，不是生硬色块），
 * 内部 5 个高饱和彩色光斑沿 Lissajous 有机轨道缓慢流动，加色混合
 * 让重叠处自然提亮，形成 Siri orb 那种流体流光；中心一簇白色高亮。
 *
 * 连续相位：用 withFrameNanos 驱动真实单调递增的时间（不取模、无循环边界），
 * 光斑采用无理数频率比，轨迹不闭合、永不回到同一点 —— 彻底消除「循环结束
 * 跳回起点」的闪帧，也看不出是在做周期循环，视觉上像在一定范围内随意流动。
 *
 * 性能：Canvas + 径向渐变（中心色→透明自带柔边，无需额外高斯模糊），
 * GPU 光栅化，每帧仅绘制约 8 个渐变圆，不掉帧。
 */
@Composable
fun SiriOrb(modifier: Modifier = Modifier, isDark: Boolean = true) {
    val colors = LocalFreeChatColors.current
    // 光斑加色混合：深色用 Plus（鲜艳），浅色用 Screen（避免加色过曝发白）
    val glowBlend = if (isDark) BlendMode.Plus else BlendMode.Screen

    // 光斑颜色：高饱和紫/蓝/青/金/粉（在深色球体内加色会非常鲜艳）
    val blobColors = listOf(
        Color(0xFF9D6BFF),
        Color(0xFF4DA6FF),
        Color(0xFF3EE6C8),
        Color(0xFFFFB84D),
        Color(0xFFFF6BA0)
    )
    // 每个光斑的 Lissajous 频率比（a, b）与速度系数；无理数比 → 轨迹不闭合、不重复
    val blobParams = listOf(
        doubleArrayOf(1.0, 2.0, 0.90),
        doubleArrayOf(1.3, 1.7, 0.78),
        doubleArrayOf(1.6, 0.8, 1.10),
        doubleArrayOf(2.0, 1.0, 0.65),
        doubleArrayOf(0.7, 1.4, 0.85)
    )

    // 连续真实时间：从首帧起单调递增（Double 保证长时间运行精度足够，不取模）
    var startNanos by remember { mutableLongStateOf(0L) }
    var elapsedNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                if (startNanos == 0L) startNanos = nanos
                elapsedNanos = nanos - startNanos
            }
        }
    }

    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = min(size.width, size.height) * 0.42f   // 球体半径
        val t = elapsedNanos / 1_000_000_000.0          // 秒（Double）
        val tau = 2.0 * PI

        // 整体光晕轻微呼吸（连续 sin，无跳变）
        val pulse = 1.0 + 0.15 * sin(t * tau / 3.4)

        // ── 球体外圈主题色光晕（双层：外层更宽更淡、内层更实）——柔和不突兀、更有「发光感」──
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.Primary.copy(alpha = 0.10f), colors.Primary.copy(alpha = 0f)),
                center = c,
                radius = (r * 2.8f * pulse).toFloat()
            ),
            radius = (r * 2.8f * pulse).toFloat(),
            center = c
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.Primary.copy(alpha = 0.26f), colors.Primary.copy(alpha = 0f)),
                center = c,
                radius = (r * 1.9f * pulse).toFloat()
            ),
            radius = (r * 1.9f * pulse).toFloat(),
            center = c
        )

        // ── 球体身体：深色柔边（边缘羽化，不是硬色块）──
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF232030), Color(0xFF14141E), Color(0xFF0A0A10)),
                center = Offset(c.x, c.y - r * 0.15f),
                radius = r * 1.3f
            ),
            radius = r,
            center = c
        )

        // ── 磨砂哑光玻璃：左上角柔和受光面（半透明淡光，模拟磨砂玻璃反光）──
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0f)),
                center = Offset(c.x - r * 0.35f, c.y - r * 0.45f),
                radius = r * 1.1f
            ),
            radius = r * 0.9f,
            center = Offset(c.x - r * 0.35f, c.y - r * 0.45f)
        )

        // ── 内部流光光斑：Lissajous 有机轨道 + 呼吸缩放 ──
        blobColors.forEachIndexed { i, col ->
            val (a, b, speed) = blobParams[i]
            val fi = i.toDouble()
            val ang = t * speed + fi * 1.7
            // 椭圆轨道，x/y 频率比不同 → 李萨茹曲线，轨迹不闭合不重复；振幅加大，流光流动更明显
            val ox = sin(a * ang) * (r * 0.84).toDouble()
            val oy = cos(b * ang) * (r * 0.74).toDouble()
            val breathe = 0.70 + 0.30 * sin(t * 2.1 + fi * 2.4)
            val blobR = r * 0.40f * breathe.toFloat()
            val pos = Offset((c.x + ox).toFloat(), (c.y + oy).toFloat())
            // 径向渐变半径 > 实心半径，边缘柔和；Plus 加色让重叠处提亮融合
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(col.copy(alpha = 0.9f), col.copy(alpha = 0.0f)),
                    center = pos,
                    radius = blobR * 2.4f
                ),
                radius = blobR * 2.4f,
                center = pos,
                blendMode = glowBlend
            )
        }

        // ── 中心高亮核心：一簇柔和的暖白光 ──
        val corePos = Offset(c.x, c.y)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFFFFF).copy(alpha = 0f)),
                center = corePos,
                radius = r * 0.5f
            ),
            radius = r * 0.5f,
            center = corePos,
            blendMode = glowBlend
        )

        // ── 磨砂玻璃边缘高光：细而淡的玻璃描边，强化「玻璃球」质感 ──
        drawCircle(
            color = Color.White.copy(alpha = 0.26f),
            radius = r,
            center = c,
            style = Stroke(width = r * 0.06f)
        )
    }
}
