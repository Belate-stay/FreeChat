package com.freechat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.LocalStrings
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalChatFontFamily
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

object ImageLoadingMotion {
    const val DotMillis = 600L
    fun dots(step: Int) = ".".repeat(Math.floorMod(step, 4))
}

/** Quiet fog-lit particles: a soft material coming into focus, not a spinner or a starfield. */
@Composable
fun ImageGenerationPlaceholder(colors: FreeChatColors, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Box(modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp, top = 4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center) {
            ImageGenerationParticles(colors, Modifier.matchParentSize())
            LoadingImageCaption(s.generatingImage, colors)
        }
    }
}

@Composable
internal fun ImageGenerationParticles(colors: FreeChatColors, modifier: Modifier = Modifier) {
    val enabled = LocalMotionEnabled.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled, lifecycle) {
        if (!enabled) { seconds.floatValue = 0f; return@LaunchedEffect }
        // Off-screen lazy items are disposed; backgrounding also stops the frame clock completely.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (isActive) withFrameNanos { now ->
                if (previous != 0L) seconds.floatValue += ((now - previous) / 1e9f).coerceIn(0f, .05f)
                previous = now
            }
        }
    }
    val appearance = remember(colors) { ImageLoadingAppearance(colors) }
    val dark = appearance.dark
    val particles = remember { ImageParticleMotion.create() }
    val palette = appearance.palette
    val base = remember(appearance) { Brush.linearGradient(appearance.base) }
    val unit = with(LocalDensity.current) { 1.dp.toPx() }
    // Unit-radius shaders are cached; only their canvas transforms change per frame.
    // No bitmap noise, blur passes, rotating geometry or per-frame shader allocation.
    val mist = remember(appearance) { palette.take(3).map { color ->
        val opacity = appearance.mistOpacity
        Brush.radialGradient(0f to color.copy(alpha = opacity),
            .3f to color.copy(alpha = opacity * .72f),
            .7f to color.copy(alpha = opacity * .16f),
            1f to color.copy(alpha = 0f), center = Offset.Zero, radius = 1f)
    } }
    // Flowing gradient: 3 soft color bands drifting at different speeds (cached brushes).
    val flowBrushes = remember(appearance) {
        val flowOpacity = if (appearance.dark) .09f else .40f
        listOf(
            Brush.radialGradient(0f to palette[0].copy(alpha = flowOpacity),
                .5f to palette[1].copy(alpha = flowOpacity * .45f),
                1f to Color.Transparent, center = Offset.Zero, radius = 1f),
            Brush.radialGradient(0f to palette[2].copy(alpha = flowOpacity * .85f),
                .4f to palette[3].copy(alpha = flowOpacity * .40f),
                1f to Color.Transparent, center = Offset.Zero, radius = 1f),
            Brush.radialGradient(0f to palette[1].copy(alpha = flowOpacity * .70f),
                .45f to palette[0].copy(alpha = flowOpacity * .30f),
                1f to Color.Transparent, center = Offset.Zero, radius = 1f)
        )
    }
    // Read the clock ONLY in drawing, not composition/layout or the containing message list.
    Canvas(modifier) {
        drawRect(base)
        val time = seconds.floatValue
        // Slowly drifting gradient blobs — aurora-like soft light flow.
        flowBrushes.forEachIndexed { index, brush ->
            val drift = particles[(index * 17 + 5) % particles.size]
            val cx = drift.x(time * .20f) * size.width
            val cy = drift.y(time * .20f) * size.height
            val breathScale = 1f + .12f * sin(time * (.08f + index.toFloat() * .02f))
            val r = size.maxDimension * (1.2f + index.toFloat() * .15f) * breathScale
            withTransform({ translate(cx, cy); scale(r, r, pivot = Offset.Zero) }) {
                drawCircle(brush, radius = 1f, center = Offset.Zero)
            }
        }
        // Original mist layer (kept for depth).
        mist.forEachIndexed { index, brush ->
            val drift = particles[index * 13]
            val center = Offset(drift.x(time * .55f) * size.width, drift.y(time * .55f) * size.height)
            val radius = size.maxDimension * (.66f + index * .06f)
            withTransform({ translate(center.x, center.y); scale(radius, radius, pivot = Offset.Zero) }) {
                drawCircle(brush, radius = 1f, center = Offset.Zero)
            }
        }
        // Particles with soft glow halo and subtle parallax by size.
        particles.forEachIndexed { index, particle ->
            val parallax = 1f + (particle.radius - 1f) * .015f
            val center = Offset(particle.x(time * parallax) * size.width,
                particle.y(time * parallax) * size.height)
            val color = palette[particle.tint]
            val alpha = particle.alpha(time) * if (dark) .86f else .72f
            // Outer glow halo: larger, softer, dimmer than the core.
            drawCircle(color.copy(alpha = alpha * .10f), particle.radius * 3.0f * unit, center)
            drawCircle(color.copy(alpha = alpha * .25f), particle.radius * 1.8f * unit, center)
            // Core particle.
            drawCircle(color.copy(alpha = alpha), particle.radius * unit, center)
            // Bright core highlight (every 5th particle).
            if (index % 5 == 0) drawCircle(lerp(color, Color.White, .6f).copy(alpha = alpha * .5f),
                particle.radius * .32f * unit, center)
        }
    }
}

@Composable
private fun LoadingImageCaption(label: String, colors: FreeChatColors) {
    val enabled = LocalMotionEnabled.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var dotStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(enabled, lifecycle) {
        dotStep = if (enabled) 0 else 3
        if (enabled) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { delay(ImageLoadingMotion.DotMillis); dotStep = (dotStep + 1) % 4 }
        }
    }
    // A fixed dot slot avoids moving the whole caption as 0–3 dots cycle.
    // 1.0.99.3: faded caption — subdued, not prominent.
    val appearance = remember(colors) { ImageLoadingAppearance(colors) }
    val ink = appearance.caption.copy(alpha = appearance.captionAlpha)
    val captionStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = LocalChatFontFamily.current, fontSize = 15.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal, letterSpacing = .4.sp)
    Row(Modifier.semantics { contentDescription = label }.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.trimEnd('.', '…'), style = captionStyle, color = ink, modifier = Modifier.weight(1f, fill = false))
        // Always lay out all three dots, including at large font sizes; only their ink changes.
        Text(buildAnnotatedString {
            val visible = ImageLoadingMotion.dots(dotStep)
            append(visible)
            withStyle(SpanStyle(color = Color.Transparent)) { append(".".repeat(3 - visible.length)) }
        }, style = captionStyle, color = ink)
    }
}
