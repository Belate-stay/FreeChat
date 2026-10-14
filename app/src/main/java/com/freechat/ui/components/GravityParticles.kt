package com.freechat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * 重力粒子粉碎（1.1.29.1 工单①②共用引擎，从 dev/RatingCard 的「从夯到拉」效果抽出）：
 * 初速向上一蓬 + 横向散开，重力拉下，尾段渐隐——HyperOS 卸载粉碎风，解析运动零逐帧分配。
 * 不裁剪：粒子可以飞出宿主边界落出屏幕外（调用方别加 clip）。
 */
@Composable
fun GravityParticles(
    palette: List<Color>,
    modifier: Modifier = Modifier,
    count: Int = 150,
    seed: Long = 20261013,
    minSizeDp: Float = 1.6f,
    maxSizeDp: Float = 5.8f,
    gravityDpPerS2: Float = 1500f,
    lifeSec: Float = 2.2f
) {
    val density = LocalDensity.current
    val cfg = remember(seed, count) {
        val rnd = java.util.Random(seed)
        val x0 = FloatArray(count); val y0 = FloatArray(count)
        val vx = FloatArray(count); val vy = FloatArray(count)
        val sz = FloatArray(count); val hue = FloatArray(count); val ph = FloatArray(count)
        repeat(count) { i ->
            x0[i] = rnd.nextFloat()
            y0[i] = rnd.nextFloat()
            vx[i] = (rnd.nextFloat() - 0.5f) * 420f   // dp/s 横向散
            vy[i] = -60f - rnd.nextFloat() * 320f      // dp/s 向上一蓬
            sz[i] = minSizeDp + rnd.nextFloat() * (maxSizeDp - minSizeDp)
            hue[i] = rnd.nextFloat()
            ph[i] = rnd.nextFloat()
        }
        ParticleCfg(x0, y0, vx, vy, sz, hue, ph)
    }
    val elapsed = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(seed, count) {
        val start = withFrameNanos { it }
        while (true) {
            val t = ((withFrameNanos { it } - start) / 1_000_000_000.0).toFloat()
            elapsed.floatValue = t
            if (t >= lifeSec) break
        }
    }
    Canvas(modifier) {
        val t = elapsed.floatValue
        if (t <= 0f) return@Canvas
        val w = size.width; val h = size.height
        val g = with(density) { gravityDpPerS2.dp.toPx() }
        val dpPx = with(density) { 1f.dp.toPx() }
        val n = cfg.x0.size
        repeat(n) { i ->
            val tt = (t + cfg.ph[i] * 0.12f)
            val x = cfg.x0[i] * w + cfg.vx[i] * dpPx * tt
            val y = cfg.y0[i] * h + cfg.vy[i] * dpPx * tt + 0.5f * g * tt * tt
            // 尾段渐隐（过半加速淡出），飞出屏幕的自然看不见
            val a = ((1f - (tt / lifeSec)) * 1.6f).coerceIn(0f, 1f) * (0.35f + 0.65f * cfg.hue[i])
            if (a <= 0.01f) return@repeat
            val r = cfg.sz[i] * dpPx * (1f - 0.25f * (tt / lifeSec))
            val color = palette[floor(cfg.hue[i] * palette.size).toInt().coerceIn(0, palette.size - 1)]
            drawCircle(color = color.copy(alpha = a), radius = r, center = Offset(x, y))
        }
    }
}

private class ParticleCfg(
    val x0: FloatArray, val y0: FloatArray, val vx: FloatArray, val vy: FloatArray,
    val sz: FloatArray, val hue: FloatArray, val ph: FloatArray
)
