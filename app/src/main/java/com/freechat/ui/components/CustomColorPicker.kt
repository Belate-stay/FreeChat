package com.freechat.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.freechat.i18n.AppStrings
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.customColors
import kotlin.math.roundToInt

/** 可触摸的饱和度/亮度色盘，辅以无障碍可操作的滑杆；颜色原值始终以 ARGB 保存。 */
@Composable
fun CustomColorPicker(
    argb: Int,
    isDark: Boolean,
    isOled: Boolean,
    colors: FreeChatColors,
    strings: AppStrings,
    onChange: (Int) -> Unit,
) {
    // HSV 单独保留：RGB 为灰或纯黑时不含色相信息，不能每次从 ARGB 反解，
    // 否则先把饱和度/亮度拉到 0，再拉回去会丢失用户刚选的色相。
    val initial = remember { FloatArray(3).also { AndroidColor.colorToHSV(argb, it) } }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var brightness by remember { mutableFloatStateOf(initial[2]) }
    var opacity by remember { mutableFloatStateOf(AndroidColor.alpha(argb) / 255f) }
    val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val applied = customColors(argb, isDark, isOled)

    fun change(h: Float = hue, s: Float = saturation, v: Float = brightness, a: Float = opacity) {
        hue = h.coerceIn(0f, 359.9f)
        saturation = s.coerceIn(0f, 1f)
        brightness = v.coerceIn(0f, 1f)
        opacity = a.coerceIn(0f, 1f)
        val rgb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
        val alpha = (opacity * 255).roundToInt()
        onChange((alpha shl 24) or (rgb and 0x00FFFFFF))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(174.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, colors.InputBorder, RoundedCornerShape(12.dp))
                .pointerInput(hue, opacity) {
                    detectTapGestures { at ->
                        change(s = at.x / size.width, v = 1f - at.y / size.height)
                    }
                }
                .pointerInput(hue, opacity) {
                    detectDragGestures(
                        onDragStart = { at -> change(s = at.x / size.width, v = 1f - at.y / size.height) },
                        onDrag = { drag, _ ->
                            change(s = drag.position.x / size.width, v = 1f - drag.position.y / size.height)
                            drag.consume()
                        }
                    )
                }
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val marker = Offset(saturation * size.width, (1f - brightness) * size.height)
            drawCircle(Color.Black.copy(alpha = 0.55f), 10.dp.toPx(), marker, style = Stroke(4.dp.toPx()))
            drawCircle(Color.White, 10.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
        }

        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            drawRoundRect(
                Brush.horizontalGradient(
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
            )
        }
        ColorSlider(strings.customHue, hue, 0f..360f, applied) { change(h = it) }
        ColorSlider(strings.customSaturation, saturation, 0f..1f, applied) { change(s = it) }
        ColorSlider(strings.customBrightness, brightness, 0f..1f, applied) { change(v = it) }
        ColorSlider(strings.customOpacity, opacity, 0f..1f, applied) { change(a = it) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorPreview(strings.customColorPreview, Color(argb), colors, Modifier.weight(1f))
            ColorPreview(strings.customColorApplied, applied.Primary, colors, Modifier.weight(1f))
        }
        Text(
            text = "#%08X".format(argb),
            style = MaterialTheme.typography.labelMedium,
            color = colors.TextSecondary
        )
        Text(
            text = strings.customColorHint,
            style = MaterialTheme.typography.bodySmall,
            color = colors.TextSecondary
        )
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    colors: FreeChatColors,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.TextPrimary)
        Spacer(Modifier.weight(1f))
        val percent = if (range.endInclusive == 360f) "${value.roundToInt()}°" else "${(value * 100).roundToInt()}%"
        Text(percent, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
    }
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = colors.Primary,
            activeTrackColor = colors.Primary,
            inactiveTrackColor = colors.SurfaceDim,
        )
    )
}

@Composable
private fun ColorPreview(label: String, swatch: Color, colors: FreeChatColors, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .background(colors.Surface, RoundedCornerShape(10.dp))
                .background(swatch, RoundedCornerShape(10.dp))
                .border(1.dp, colors.InputBorder, RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.TextSecondary)
    }
}
