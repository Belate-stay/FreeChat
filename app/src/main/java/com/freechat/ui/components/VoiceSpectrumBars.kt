package com.freechat.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.freechat.data.VoiceSpectrumFrame
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.theme.LocalFreeChatColors
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.exp

/** Solid/live bars and a quieter trailing layer share real low→high FFT bands, not random heights. */
@Composable
internal fun VoiceSpectrumBars(
    spectrum: StateFlow<VoiceSpectrumFrame>,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val frame = spectrum.collectAsState()
    val seconds = rememberRenderSeconds()
    val animate = LocalMotionEnabled.current
    val colors = LocalFreeChatColors.current
    val palette = remember(colors.Primary, isDark) {
        val tones = listOf(Color(0xFF55BAA5), Color(0xFF71BBDD), Color(0xFF9D96DB), Color(0xFFD898BB))
        tones.map { lerp(it, colors.Primary, .30f) }
    }
    val smoothing = remember { SpectrumDrawingState() }
    val renderer = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { SpectrumShader() }
            .onFailure { Log.w("FreeChatShader", "Voice spectrum uses Canvas fallback", it) }.getOrNull() else null
    }
    Canvas(modifier) {
        smoothing.update(frame.value.bands, seconds.value, animate)
        val count = (size.width / 7.5.dp.toPx()).toInt().coerceIn(32, 64)
        val step = size.width / count
        val width = step * .48f
        val baseline = size.height - 3.dp.toPx()
        val maxHeight = size.height * .88f
        val minHeight = 3.dp.toPx()
        if (Build.VERSION.SDK_INT >= 33 && renderer != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
            renderer.update(size.width, size.height, count.toFloat(), minHeight, maxHeight,
                baseline, smoothing.live, smoothing.trail, palette, if (isDark) .86f else .78f)
            drawRect(renderer.brush)
        } else {
            for (i in 0 until count) {
                val x = i / (count - 1f)
                val left = (i + .5f) * step - width / 2f
                val colorAt = x * (palette.size - 1)
                val colorIndex = colorAt.toInt().coerceAtMost(palette.lastIndex - 1)
                val ink = lerp(palette[colorIndex], palette[colorIndex + 1], colorAt - colorIndex)
                val backHeight = minHeight + maxHeight * VoiceInputMotion.bandAt(smoothing.trail, x)
                val height = minHeight + maxHeight * VoiceInputMotion.bandAt(smoothing.live, x)
                drawRoundRect(ink.copy(alpha = .19f), Offset(left + step * .18f, baseline - backHeight),
                    Size(width, backHeight), CornerRadius(width * .24f))
                drawRoundRect(ink.copy(alpha = if (isDark) .86f else .78f),
                    Offset(left, baseline - height), Size(width, height), CornerRadius(width * .24f))
            }
        }
    }
}

private class SpectrumDrawingState {
    val live = FloatArray(16)
    val trail = FloatArray(16)
    private var previous = -1f

    fun update(target: FloatArray, seconds: Float, animate: Boolean) {
        val dt = if (previous < 0) 1f / 60f else (seconds - previous).coerceIn(0f, .05f)
        previous = seconds
        val attack = 1f - exp(-dt / .048f)
        val release = 1f - exp(-dt / .11f)
        val tail = 1f - exp(-dt / .22f)
        for (i in live.indices) {
            val next = target.getOrElse(i) { 0f }
            if (animate) {
                live[i] += (next - live[i]) * if (next > live[i]) attack else release
                trail[i] += (live[i] - trail[i]) * tail
            } else {
                // Reduced motion removes decorative easing, never live microphone feedback.
                live[i] = next
                trail[i] = next
            }
        }
    }
}

@RequiresApi(33)
private class SpectrumShader {
    private val shader = RuntimeShader("""
        uniform float2 size;
        uniform float count;
        uniform float minimum;
        uniform float maximum;
        uniform float baseline;
        uniform float opacity;
        uniform float bands[16];
        uniform float trail[16];
        layout(color) uniform half4 tint0;
        layout(color) uniform half4 tint1;
        layout(color) uniform half4 tint2;
        layout(color) uniform half4 tint3;
        float bar(float2 point, float left, float width, float height) {
            float2 center = float2(left + width * 0.5, baseline - height * 0.5);
            float radius = width * 0.24;
            float2 q = abs(point - center) - float2(width, height) * 0.5 + radius;
            float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            return 1.0 - smoothstep(-0.65, 0.65, d);
        }
        // AGSL requires constant uniform-array indices; select a segment before interpolation.
        float2 sampleBands(float at) {
            ${(0 until 15).joinToString("\n") { i ->
                "${if (i == 0) "if" else "else if"} (at < ${i + 1}.0) return mix(float2(bands[$i], trail[$i]), " +
                    "float2(bands[${i + 1}], trail[${i + 1}]), at - $i.0);"
            }}
            return float2(bands[15], trail[15]);
        }
        half4 main(float2 point) {
            float stepSize = size.x / count;
            float column = floor(point.x / stepSize);
            float x = clamp(column / (count - 1.0), 0.0, 1.0);
            float at = x * 15.0;
            float2 sampled = sampleBands(at);
            float width = stepSize * 0.48;
            float left = (column + 0.5) * stepSize - width * 0.5;
            float foreground = bar(point, left, width, minimum + maximum * sampled.x) * opacity;
            float background = bar(point, left + stepSize * 0.18, width, minimum + maximum * sampled.y) * 0.19;
            float alpha = foreground + background * (1.0 - foreground);
            half3 ink = x < 0.333333 ? mix(tint0.rgb, tint1.rgb, half(x * 3.0)) :
                x < 0.666667 ? mix(tint1.rgb, tint2.rgb, half(x * 3.0 - 1.0)) :
                mix(tint2.rgb, tint3.rgb, half(x * 3.0 - 2.0));
            return half4(ink * half(alpha), half(alpha));
        }
    """.trimIndent())
    val brush = ShaderBrush(shader)
    fun update(width: Float, height: Float, count: Float, minimum: Float, maximum: Float,
               baseline: Float, bands: FloatArray, trail: FloatArray, palette: List<Color>, opacity: Float) {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("count", count)
        shader.setFloatUniform("minimum", minimum)
        shader.setFloatUniform("maximum", maximum)
        shader.setFloatUniform("baseline", baseline)
        shader.setFloatUniform("opacity", opacity)
        shader.setFloatUniform("bands", bands)
        shader.setFloatUniform("trail", trail)
        palette.forEachIndexed { i, color -> shader.setColorUniform("tint$i", color.toArgb()) }
    }
}
