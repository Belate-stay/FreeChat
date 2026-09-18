package com.freechat.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 接缝羽化 —— 让模糊带朝向「Chat 页 / 侧滑页之间那条边界」的一侧，模糊强度在 [featherPx] 内平滑衰减到 0。
 *
 * 为什么需要：两页的顶部模糊带高度不同（Chat = statusBar+64dp，侧滑页因有 134dp 固定头部 = statusBar+210dp），
 * 同一条 y 上两侧模糊强度不同，边界处会留下一条「上下明显、中间消失」的亮度台阶 —— 这条台阶在抽屉全开、
 * 几何缝隙已归零时依然存在，是「全开也能看到线」的成因。
 * 把贴缝一侧羽化到 0 后，缝两侧最近的一圈像素都退回**同一幅背景**，亮度剖面连续 ⇒ 竖线不可见。
 *
 * 前提：缝两侧的像素最终落在同一个背景上。
 *  · 普通模式：根 Box / Chat 页 / 侧滑页三处取同一个 colors.Background 且为不透明纯色。
 *    一旦有主题把背景改成渐变、半透明或图片，或把三处背景改成不同色，竖线会立刻复现。
 *  · 流动炫彩模式：背景由根 Box 画一次、各页一律透明（见 [pageBackground]），
 *    缝两侧退回的是同一张钉在屏幕上的根背景 —— 这个前提比纯色版本更强，因为连「三个地方取色是否一致」
 *    都不再需要人为保证，而且页面被平移时背景也不会跟着错位。
 *
 * 开销：只在绘制阶段做一次水平 alpha 遮罩（Offscreen 层 + DstIn），不读任何 State，不触发重组/重排。
 * [featherPx] ≤ 0.5f 时返回 this（不建离屏层），可作一键回退开关。
 *
 * @param featherPx 羽化宽度（px）
 * @param fromEnd true = 在右端淡出（侧滑页，缝在它右边缘）；false = 在左端淡出（Chat 页，缝在它左边缘）
 */
fun Modifier.seamFeather(featherPx: Float, fromEnd: Boolean): Modifier {
    if (featherPx <= 0.5f) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        // 用 drawWithCache 而不是 drawWithContent（1.0.49 性能）：停点表与 Brush 原来是在**每一帧的
        // 绘制块里**现搭的 —— ArrayList + toTypedArray + Brush.horizontalGradient 三个对象，
        // 而全 App 有四条羽化带，列表滚动时它们逐帧重画，这就是一份稳定的逐帧 GC 垃圾。
        // 缓存块只在**尺寸变化**时重跑一次（这里不读任何 State），绘制块里只剩一句 drawRect。
        .drawWithCache {
            val w = size.width
            val f = if (w <= 0f) 0f else (featherPx / w).coerceIn(0f, 1f)
            val brush = if (f <= 0f) null else buildSeamBrush(f, w, fromEnd)
            onDrawWithContent {
                drawContent()
                // 只保留 alpha 通道参与合成（DstIn）：模糊带内容按水平斜坡淡出
                if (brush != null) drawRect(brush = brush, blendMode = BlendMode.DstIn)
            }
        }
}

/** 水平羽化斜坡的 Brush（近似 smoothstep 的 5 停点）。只在接缝羽化的缓存块里调用。 */
private fun buildSeamBrush(f: Float, w: Float, fromEnd: Boolean): Brush {
    val ts = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    val as_ = floatArrayOf(1f, 0.85f, 0.5f, 0.15f, 0f)

    val stops = ArrayList<Pair<Float, Color>>(7)
    // 起点锚：让插值区间覆盖整个宽度，不依赖「首停点 > 0 时 Skia 自动钳制」的行为
    stops.add(0f to (if (fromEnd) Color.Black else Color.Transparent))
    for (i in ts.indices) {
        val pos = if (fromEnd) (1f - f) + f * ts[i] else f * ts[i]
        val alpha = if (fromEnd) as_[i] else 1f - as_[i]
        if (stops.last().first < pos - 1e-4f) stops.add(pos to Color.Black.copy(alpha = alpha))
    }
    // 终点锚
    if (stops.last().first < 1f - 1e-4f) {
        stops.add(1f to (if (fromEnd) Color.Transparent else Color.Black))
    }
    return Brush.horizontalGradient(*stops.toTypedArray(), startX = 0f, endX = w)
}
