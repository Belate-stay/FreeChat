package com.freechat.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalLiquidMode
import com.freechat.ui.theme.hazeEdgeFeather
import com.freechat.ui.theme.liquidSourceBackdrop
import com.freechat.ui.theme.materialHaze
import com.freechat.ui.theme.materialProgress
import com.freechat.ui.theme.pageHeaderBackground
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** 放在正文之前：流光作为样本的底，不覆盖清晰正文，也不把顶部变成不透明贴片。 */
@Composable
fun BoxScope.TopBarBackdropSource(state: HazeState, base: Color, height: Dp) {
    if (LocalAdvancedMaterial.current && LocalLiquidMode.current) {
        // 采样底多留一个核半径，防止下缘采到透明像素而产生色带；这不是可见模糊区。
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(height + HazeSpec.TopBlurRadius)
            .hazeSource(state, zIndex = -1f).liquidSourceBackdrop(base))
    }
}

/**
 * 从正文到标题用 104dp 连续增加真实模糊半径，标题下方适度延长，标题/按钮仍在原位置。
 * 遮挡面保障材质切换中标题不透出清晰正文，但其下缘也必须羽化，不能留下实心板的硬边。
 * 最后 36dp 还将模糊输出渐隐到原始画面，减弱微小核/噪点在裁剪下缘形成的整宽分界线。
 * 侧栏的终点仍在固定悬浮内容下方、初始历史列表上方。
 */
@Composable
fun BoxScope.TopBarBackdrop(
    state: HazeState,
    base: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    fadeHeight: Dp = HazeSpec.TopProgressiveSpanDp,
    solidHeight: Dp = height - HazeSpec.TopFadeZoneDp,
    seamFromEnd: Boolean? = null,
) {
    val advanced = LocalAdvancedMaterial.current
    val density = LocalDensity.current
    val startY = with(density) { HazeSpec.topFadeStartDp(height, fadeHeight).toPx() }
    val endY = with(density) { height.toPx() }
    val outputFeatherPx = with(density) { HazeSpec.TopOutputFeatherDp.toPx() }
    val seamFeatherPx = if (seamFromEnd == null) 0f else with(density) { HazeSpec.SeamFeatherDp.toPx() }
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(height).clipToBounds()) {
        if (advanced) {
            val progress = materialProgress()
            val solidEndPx = with(density) { solidHeight.toPx() }
            val featherPx = with(density) { HazeSpec.HeaderUnderlayFeatherDp.toPx() }
            Box(Modifier.fillMaxWidth()
                .height((solidHeight + HazeSpec.HeaderUnderlayFeatherDp).coerceAtMost(height))
                .drawWithCache {
                    // 只在绘制阶段观察进度，不让所有页面逐帧重组。稳态缓存蒙版。
                    val feather = featherPx * progress().coerceIn(0f, 1f)
                    val fadeStart = (solidEndPx - feather).coerceAtLeast(0f)
                    val fadeEnd = (solidEndPx + feather).coerceAtMost(size.height)
                    val mask = if (fadeEnd > fadeStart) Brush.verticalGradient(
                        colors = List(33) { index ->
                            Color.Black.copy(alpha = 1f - HazeSpec.smoothFadeProgress(index / 32f))
                        },
                        startY = fadeStart,
                        endY = fadeEnd,
                    ) else null
                    val layerPaint = Paint()
                    onDrawWithContent {
                        drawIntoCanvas { canvas ->
                            // 限于标题的小图层；这里只做边缘合成，不再增加高斯模糊采样。
                            canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                            drawContent()
                            if (mask != null) drawRect(mask, blendMode = BlendMode.DstIn)
                            else drawRect(Color.Black,
                                topLeft = Offset(0f, solidEndPx),
                                size = androidx.compose.ui.geometry.Size(size.width, (size.height - solidEndPx).coerceAtLeast(0f)),
                                blendMode = BlendMode.Clear)
                            canvas.restore()
                        }
                    }
                }
                .pageHeaderBackground(base))
        } else {
            Box(Modifier.fillMaxWidth().height(solidHeight).pageHeaderBackground(base))
        }
        if (advanced) Box(Modifier.fillMaxWidth().height(height).then(modifier)
            .hazeEdgeFeather(bottomFeatherPx = outputFeatherPx,
                seamFeatherPx = seamFeatherPx, fromEnd = seamFromEnd ?: false)
            .materialHaze(state) {
                blurRadius = HazeSpec.TopBlurRadius
                backgroundColor = Color.Transparent
                progressive = HazeProgressive.verticalGradient(easing = HazeSpec.TopBlurEasing,
                    startY = startY, startIntensity = 1f, endY = endY, endIntensity = 0f)
            })
        // Drawing blur alone is not a hit-test surface. Claim the entire visible header band,
        // including its feather, before the actual header controls are drawn above this sibling.
        // No clickable/semantics node: an inert shield must not become an empty TalkBack button.
        Box(Modifier.fillMaxWidth().height(if (advanced) height else solidHeight)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            })
    }
}
