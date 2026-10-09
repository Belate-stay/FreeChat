package com.freechat.ui.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.freechat.i18n.LocalStrings
import com.freechat.ui.theme.FreeChatColors
import com.freechat.data.GenerationPhase
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.LocalMotionEnabled
import kotlin.math.max
import kotlin.math.roundToInt

/** An ordered dot field softly breathes while the image is being generated. */
@Composable
fun ImageGenerationPlaceholder(colors: FreeChatColors, modifier: Modifier = Modifier, phase: GenerationPhase = GenerationPhase.IDLE) {
    val s = LocalStrings.current
    Box(modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp, top = 4.dp)
        .semantics { contentDescription = s.generatingImage }) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            ImageGenerationParticles(colors, Modifier.matchParentSize(), phase)
        }
    }
}

@Composable
internal fun ImageGenerationParticles(colors: FreeChatColors, modifier: Modifier = Modifier, phase: GenerationPhase = GenerationPhase.IDLE) {
    val view = LocalView.current
    var visible by remember { mutableStateOf(false) }
    val seconds = rememberRenderSeconds(active = visible)
    val stageSize = animateFloatAsState(ImageDotMotion.growthFor(phase),
        if (LocalMotionEnabled.current) FreeChatAnimation.materialTween else androidx.compose.animation.core.snap(), label = "imageStageSize")
    val appearance = remember(colors) { ImageLoadingAppearance(colors) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) ImageDotShader() else null
    }
    SideEffect {
        if (Build.VERSION.SDK_INT >= 33) shader?.updatePalette(appearance)
    }
    Canvas(modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        visible = bounds.width > 0f && bounds.height > 0f && bounds.right > 0f && bounds.bottom > 0f &&
            bounds.left < view.width && bounds.top < view.height
    }) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        // The frame clock invalidates drawing only; the message stays stable.
        val frame = ImageDotMotion.frame(seconds.value, stageSize.value)
        val cell = size.width / ImageDotMotion.Columns
        val rows = (size.height / cell).roundToInt().coerceAtLeast(1)
        val originY = (size.height - rows * cell) * .5f
        if (Build.VERSION.SDK_INT >= 33 && shader != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
            shader.draw(this, frame, cell, originY)
        } else {
            drawImageDotFallback(frame, appearance, cell, originY, rows)
        }
    }
}

private fun DrawScope.drawImageDotFallback(
    frame: ImageDotMotion.Frame, appearance: ImageLoadingAppearance, cell: Float, originY: Float, rows: Int
) {
    // Nested translucent fills approximate the same rounded-distance feather on software/older Android.
    var previous = 0f
    for (layer in 0 until 16) {
        val inset = ImageDotMotion.Feather * layer / 16f
        val opacity = ImageDotMotion.smoothStep(0f, 1f, (layer + 1f) / 16f)
        drawRoundRect(appearance.base.copy(alpha = appearance.base.alpha * (opacity - previous)),
            topLeft = Offset(size.width * inset, size.height * inset),
            size = Size(size.width * (1f - 2f * inset), size.height * (1f - 2f * inset)),
            cornerRadius = CornerRadius(size.width * max(0f, ImageDotMotion.Corner - inset),
                size.height * max(0f, ImageDotMotion.Corner - inset)))
        previous = opacity
    }
    for (row in 0 until rows) for (column in 0 until ImageDotMotion.Columns) {
        val center = Offset((column + .5f) * cell, (row + .5f) * cell + originY)
        val x = center.x / size.width
        val y = center.y / size.height
        val opacity = ImageDotMotion.edgeOpacity(x, y)
        if (opacity <= 0f) continue
        val strength = frame.strength(x, y)
        val ink = lerp(appearance.dot, appearance.gloss, .28f * strength + .42f * frame.sheen(x, y))
        drawCircle(ink.copy(alpha = ink.alpha * opacity),
            cell * (ImageDotMotion.MinRadius + (ImageDotMotion.MaxRadius - ImageDotMotion.MinRadius) * strength), center)
    }
}
