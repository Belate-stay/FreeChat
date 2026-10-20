package com.freechat.ui.components

import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.ui.theme.AppPopup as Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.LocalMotionEnabled
import kotlin.math.cos
import kotlin.math.sin

/**
 * AGSL source for the voice cancel ring：SDF 细弧（圆头反走样）。
 * Kept free of Android types so JVM tests can pin the shader discipline (no fwidth / dither).
 * 实机返修：**不要光晕**（用户点名「光晕太丑」），弧线调细 —— 只留干净细弧+环绕红字。
 */
internal object VoiceCancelRingShaderSource {
    /**
     * 弧 SDF：角内=到环线距离、角外=到两端点距离；core=圆头描边反走样。
     * AGSL 无 fwidth()：bandingNoise 抖动防细弧边缘出灰阶。
     */
    val Agsl = """
        float bandingNoise(vec2 fragCoord) {
          return 1. / 256. * (fract(sin(dot(.014 * fragCoord, vec2(12.9898, 78.233))) * 43758.5453123) - .5);
        }

        uniform float2 u_center;
        uniform float u_radius;
        uniform float u_angle;
        uniform float u_half;
        uniform float u_stroke;
        uniform float u_alpha;
        layout(color) uniform half4 u_color;

        half4 main(float2 fragCoord) {
          float2 p = fragCoord - u_center;
          float r = length(p);
          float theta = atan(p.y, p.x);
          float dAng = abs(atan(sin(theta - u_angle), cos(theta - u_angle)));
          float2 e1 = u_center + u_radius * vec2(cos(u_angle + u_half), sin(u_angle + u_half));
          float2 e2 = u_center + u_radius * vec2(cos(u_angle - u_half), sin(u_angle - u_half));
          float dist = abs(r - u_radius);
          if (dAng > u_half) dist = min(length(fragCoord - e1), length(fragCoord - e2));

          float core = 1.0 - smoothstep(u_stroke - 1.0, u_stroke + 1.0, dist);
          float a = clamp(core * 0.92, 0.0, 1.0) * u_alpha;
          float d = bandingNoise(fragCoord);
          a = clamp(a + d * 0.6, 0.0, 1.0);
          return half4(u_color.rgb * half(a), half(a));
        }
    """.trimIndent()
}

@RequiresApi(33)
internal class VoiceCancelRingShader {
    private val shader = RuntimeShader(VoiceCancelRingShaderSource.Agsl)
    private val brush = ShaderBrush(shader)

    fun draw(
        scope: DrawScope,
        center: Offset,
        radiusPx: Float,
        angleRad: Float,
        halfAngleRad: Float,
        strokePx: Float,
        alpha: Float,
        color: Color
    ) {
        shader.setFloatUniform("u_center", center.x, center.y)
        shader.setFloatUniform("u_radius", radiusPx)
        shader.setFloatUniform("u_angle", angleRad)
        shader.setFloatUniform("u_half", halfAngleRad)
        shader.setFloatUniform("u_stroke", strokePx)
        shader.setFloatUniform("u_alpha", alpha)
        shader.setColorUniform("u_color", color.toArgb())
        with(scope) { drawRect(brush) }
    }
}

/** 取消环的鲜红（设计稿红，不随主题 ErrorRed 走暗）。 */
internal val VoiceCancelRingRed = Color(0xFFE5484D)

/**
 * 弧与环绕文字的排布参数（dp 量纲，实机观感调这里）。
 * 弧中点在射线上距指尖 gapDp（远到手指不遮），触控点仍在环内（gap < 2R）。
 */
internal data class VoiceCancelRingStyle(
    val gapDp: Float = 64f,
    val ringRadiusDp: Float = 40f,
    val halfAngleDeg: Float = 50f,
    val strokeDp: Float = 1.6f,
    val textExtraDp: Float = 14f,
    val textSp: Float = 12f,
    val charStepDeg: Float = 13f
)

/**
 * 语音拖出取消指示（1.1.29.9 工单⑥，实机两轮返修）：红弧+环绕红字「松开取消发送」沿
 * 「语音键→指尖」射线摆在指尖**前方足够远处**（手指不遮显示），触控点仍在环内；
 * 弧的扇区朝拖出方向、随指尖逐帧实时平移旋转。黄圈（识别范围）、蓝圈（触控点）、
 * 参考线一律不渲染 —— 只有红环+红字。
 */
@Composable
internal fun VoiceCancelRing(
    originWin: Offset,
    fingerWin: Offset,
    cancelColor: Color,
    label: String,
    style: VoiceCancelRingStyle = VoiceCancelRingStyle(),
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val motionOn = LocalMotionEnabled.current
    // 出现淡入（组合即新建，Animatable 从 0 起）；animateFloatAsState 首帧直接落在终值=没动画
    val enter = remember { androidx.compose.animation.core.Animatable(if (motionOn) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (motionOn) enter.animateTo(1f, FreeChatAnimation.controlTween)
    }
    val enterAlpha = enter.value
    val agsl = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { VoiceCancelRingShader() }
            .onFailure { Log.w("FreeChatShader", "Voice cancel ring uses arc-stack fallback", it) }
            .getOrNull() else null
    }
    val placement = with(density) {
        VoiceInputMotion.cancelRingPlacement(
            originX = originWin.x, originY = originWin.y,
            fingerX = fingerWin.x, fingerY = fingerWin.y,
            gapPx = style.gapDp.dp.toPx(),
            ringRadiusPx = style.ringRadiusDp.dp.toPx(),
            textExtraPx = style.textExtraDp.dp.toPx()
        )
    }
    val halfAngle = Math.toRadians(style.halfAngleDeg.toDouble()).toFloat()
    val labelPaint = remember(label, style, cancelColor) {
        android.graphics.Paint().apply {
            color = cancelColor.copy(alpha = 0.92f).toArgb()
            textSize = with(density) { style.textSp.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    // 全窗 Popup：指尖可到屏幕任何角落，坐标=窗口坐标，画布原点即窗口原点
    Popup(
        popupPositionProvider = remember { FixedOriginProvider() },
        properties = PopupProperties(focusable = false)
    ) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val alpha = enterAlpha
            val radiusPx = placement.ringRadiusPx * (0.88f + 0.12f * alpha)
            val center = Offset(placement.centerX, placement.centerY)
            if (agsl != null) {
                agsl.draw(
                    this, center, radiusPx, placement.angleRad, halfAngle,
                    strokePx = with(density) { style.strokeDp.dp.toPx() },
                    alpha = alpha, color = cancelColor
                )
            } else {
                drawCancelRingFallback(
                    center = center, radiusPx = radiusPx, angleRad = placement.angleRad,
                    halfAngle = halfAngle, style = style, alpha = alpha, color = cancelColor
                )
            }
            // 环绕红字：逐字沿 textRadius 圆弧排布，字的「上」朝外（=切线旋转）
            val textR = placement.textRadiusPx * (0.88f + 0.12f * alpha)
            val step = Math.toRadians(style.charStepDeg.toDouble()).toFloat()
            val n = label.length
            val drawCtx = drawContext.canvas.nativeCanvas
            labelPaint.alpha = (255 * alpha * 0.92f).toInt().coerceIn(0, 255)
            for (i in 0 until n) {
                val a = placement.angleRad + (i - (n - 1) / 2f) * step
                val px = center.x + cos(a) * textR
                val py = center.y + sin(a) * textR
                drawCtx.save()
                drawCtx.rotate(Math.toDegrees(a.toDouble()).toFloat() + 90f, px, py)
                drawCtx.drawText(label[i].toString(), px, py + labelPaint.textSize * 0.35f, labelPaint)
                drawCtx.restore()
            }
        }
    }
}

/** API<33 降级：细主弧（同款几何、同款排布；无光晕，与 AGSL 同观感）。 */
private fun DrawScope.drawCancelRingFallback(
    center: Offset,
    radiusPx: Float,
    angleRad: Float,
    halfAngle: Float,
    style: VoiceCancelRingStyle,
    alpha: Float,
    color: Color
) {
    val strokePx = style.strokeDp.dp.toPx()
    val startDeg = Math.toDegrees((angleRad - halfAngle).toDouble()).toFloat()
    val sweepDeg = Math.toDegrees((halfAngle * 2.0)).toFloat()
    drawArc(
        color = color.copy(alpha = 0.92f * alpha),
        startAngle = startDeg, sweepAngle = sweepDeg, useCenter = false,
        topLeft = Offset(center.x - radiusPx, center.y - radiusPx),
        size = androidx.compose.ui.geometry.Size(radiusPx * 2f, radiusPx * 2f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    )
}

/** 固定 (0,0)：画布=整窗，一切几何走窗口坐标，不受钳位挪动。 */
private class FixedOriginProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset.Zero
}
