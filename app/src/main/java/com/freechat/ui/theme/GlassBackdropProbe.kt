package com.freechat.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import com.freechat.ui.animation.FreeChatAnimation
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Stable
class GlassBackdropProbe(initialDarkness: Float = 0f) {
    var bounds by mutableStateOf(Rect.Zero)
    var targetDarkness by mutableFloatStateOf(initialDarkness)
}

/** 16×10 GPU readback, at most 1.5 Hz, only for the visible composer. Never capture the full screen. */
@Composable
fun rememberGlassBackdropProbe(haze: HazeState?, enabled: Boolean, isDark: Boolean): Pair<GlassBackdropProbe, Float> {
    val probe = remember(haze) { GlassBackdropProbe(if (isDark) 1f else 0f) }
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val base = LocalFreeChatColors.current.Background
    val view = LocalView.current
    LaunchedEffect(haze, enabled, isDark) {
        if (!enabled || haze == null) { probe.targetDarkness = if (isDark) 1f else 0f; return@LaunchedEffect }
        while (isActive) {
            val box = probe.bounds
            if (box.width > 0 && box.height > 0 && view.isShown && view.hasWindowFocus()) {
                runCatching {
                    val location = IntArray(2).also(view::getLocationOnScreen)
                    val screenBox = box.translate(Offset(location[0].toFloat(), location[1].toFloat()))
                    val sources = haze.areas.filter { it.positionOnScreen.isSpecified && it.contentLayer != null }
                        .sortedBy { it.zIndex }
                    layer.record(density, direction, IntSize(16, 10)) {
                        drawRect(base)
                        scale(16f / screenBox.width, 10f / screenBox.height, pivot = Offset.Zero) {
                            sources.forEach { area ->
                                translate(area.positionOnScreen.x - screenBox.left, area.positionOnScreen.y - screenBox.top) {
                                    area.contentLayer?.let { drawLayer(it) }
                                }
                            }
                        }
                    }
                    val pixels = layer.toImageBitmap().toPixelMap()
                    var luminance = 0f
                    for (y in 0 until pixels.height) for (x in 0 until pixels.width) luminance += pixels[x, y].luminance()
                    val mean = luminance / (pixels.width * pixels.height)
                    probe.targetDarkness = ((0.38f - mean) / 0.24f).coerceIn(0f, 1f)
                }
            }
            delay(700)
        }
    }
    val darkness by animateFloatAsState(probe.targetDarkness, FreeChatAnimation.materialTween, label = "glass-backdrop-tone")
    return probe to darkness
}

fun Modifier.glassProbeBounds(probe: GlassBackdropProbe): Modifier = onGloballyPositioned { probe.bounds = it.boundsInWindow() }
