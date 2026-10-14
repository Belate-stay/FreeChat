package com.freechat.ui.components

import android.os.Build
import android.util.Log
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.animation.LocalMotionEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Time-driven parameters for the Siri orb. Pure JVM maths so tests can pin
 * boundedness, frame smoothness and the non-repeating Lissajous orbits.
 */
internal object SiriOrbMotion {
    /** Golden ratio — badly approximable by p/q, so orbits never visibly close. */
    const val PHI = 1.618033988749895
    const val SQRT2 = 1.4142135623730951

    /** [packParticle] 每粒子写入的 float 数：头 x,y,半径,强度 + 尾1 x,y + 尾2 x,y。 */
    const val PACK_STRIDE = 8

    /** 拖尾采样滞后（秒）：两点拖尾把轨道运动拉出光迹。 */
    val TRAIL_LAGS = floatArrayOf(0.07f, 0.15f)

    /** 五颗粒子固定色（紫/蓝/青/金/粉）——多彩光晕不随主题走。 */
    val particleColors = listOf(
        Color(0xFF9D6BFF),
        Color(0xFF4DA6FF),
        Color(0xFF3EE6C8),
        Color(0xFFFFB84D),
        Color(0xFFFF6BA0)
    )

    /** (freqX, freqY, speed) per glow blob; each freq pair mixes in PHI or SQRT2. */
    val blobParams = listOf(
        doubleArrayOf(1.0, 2.0 / PHI, 1.05),
        doubleArrayOf(1.3, 1.7 * PHI / 2.0, 0.90),
        doubleArrayOf(1.6, 0.8 * SQRT2, 1.25),
        doubleArrayOf(2.0, 1.0 * PHI, 0.78),
        doubleArrayOf(0.7, 1.4 * PHI / 2.0, 0.98)
    )

    data class Blob(val x: Float, val y: Float, val radius: Float)

    /** Halo breathing scale, bounded to [0.85, 1.15]. */
    fun pulse(t: Float): Float = 1f + 0.15f * sin(t * 2.0 * PI / 3.4).toFloat()

    /** Blob radius scale, bounded to [0.40, 1.00]. */
    fun breathe(t: Float, index: Int): Float =
        0.70f + 0.30f * sin(t * 2.1 + index * 2.4).toFloat()

    /** Orbit position and radius for one blob around (cx, cy). */
    fun blob(t: Float, index: Int, cx: Float, cy: Float, r: Float): Blob {
        val (a, b, speed) = blobParams[index]
        val ang = t * speed + index * 1.7
        val ox = sin(a * ang) * (r * 0.84)
        val oy = cos(b * ang) * (r * 0.74)
        return Blob((cx + ox).toFloat(), (cy + oy).toFloat(), r * 0.40f * breathe(t, index))
    }

    /** Continuous phase of one blob — strictly increasing in t, never wraps. */
    fun phase(t: Float, index: Int): Float =
        (t * blobParams[index][2] + index * 1.7).toFloat()

    /** 轨道位置（x,y）写进 [out] 的 [o]、[o+1]；[t] 可为过去时刻（拖尾采样）。 */
    fun particlePos(t: Float, index: Int, cx: Float, cy: Float, r: Float, out: FloatArray, o: Int) {
        val params = blobParams[index]
        // 非线性速率（1.1.32 二轮）：基速 + 慢波调制——粒子「留连—掠过」交替，比匀速高级
        val ang = t * params[2] +
            0.85f * sin(t * (0.21f + index * 0.067f) + index * 2.0f) + index * 1.7
        // 幅度 0.95/0.85：最大触及 hypot≈1.27r——粒子自由越出单位圈，不圈禁在球内
        out[o] = (cx + sin(params[0] * ang) * (r * 0.95)).toFloat()
        out[o + 1] = (cy + cos(params[1] * ang) * (r * 0.85)).toFloat()
    }

    /**
     * 打包一颗粒子的渲染参数（8 float）：头 x,y、半径、强度（0.5~1.0 随前后穿行深度起伏）
     * + 两个拖尾点 x,y。坐标系任意（shader 用归一化球坐标：圆心 0、半径 1）。零分配。
     */
    fun packParticle(t: Float, index: Int, cx: Float, cy: Float, r: Float, out: FloatArray, o: Int) {
        val params = blobParams[index]
        val ang = t * params[2] + index * 1.7
        // 前后穿行：深度波决定大小/亮度（绕到背面变小变暗），波频掺无理比不同步
        val depthK = (0.5 + 0.5 * sin(ang * 0.618 + index * 1.3)).toFloat()
        particlePos(t, index, cx, cy, r, out, o)
        out[o + 2] = r * (0.122f + 0.065f * depthK)
        out[o + 3] = 0.5f + 0.5f * depthK
        particlePos(t - TRAIL_LAGS[0], index, cx, cy, r, out, o + 4)
        particlePos(t - TRAIL_LAGS[1], index, cx, cy, r, out, o + 6)
    }
}

/**
 * Siri 式发光光雾 — AI 思考动画
 *
 * API 33+ 且硬件加速时走 AGSL RuntimeShader：柔雾底色（无球缘无圆框）+ 五颗自由环绕
 * 光粒子（重光晕+拖尾、前后穿行深度、可越出单位圈）+ 多彩呼吸光晕。否则退回 Canvas
 * （同款粒子观感：亮核+光晕+拖尾，同样无圆框）。
 *
 * 连续相位：withFrameNanos 驱动真实单调递增时间（不取模）；粒子轨道频率比掺入
 * 黄金比例/根号二（轨迹不闭合），速率再叠慢波（非线性，留连—掠过交替）。
 * reduced-motion 冻结在 t=0。
 */
@Composable
fun SiriOrb(modifier: Modifier = Modifier, isDark: Boolean = true) {
    val colors = LocalFreeChatColors.current
    // 光斑加色混合：深色用 Plus（鲜艳），浅色用 Screen（避免加色过曝发白）
    val glowBlend = if (isDark) BlendMode.Plus else BlendMode.Screen

    // 连续真实时间：从首帧起单调递增（Double 保证长时间运行精度足够，不取模）
    var startNanos by remember { mutableLongStateOf(0L) }
    var elapsedNanos by remember { mutableLongStateOf(0L) }
    val motionEnabled = LocalMotionEnabled.current
    LaunchedEffect(motionEnabled) {
        if (!motionEnabled) return@LaunchedEffect
        while (true) {
            withFrameNanos { nanos ->
                if (startNanos == 0L) startNanos = nanos
                elapsedNanos = nanos - startNanos
            }
        }
    }

    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { SiriOrbShader() }
            .onFailure { Log.w("FreeChatShader", "Siri orb uses canvas fallback", it) }
            .getOrNull() else null
    }
    // 主题色 + 邻近微光色：随主题算一次，帧内零分配
    val primary = colors.Primary
    val (glowA, glowB, glowC) = remember(primary) {
        Triple(
            lerp(primary, Color(0xFF9D6BFF), 0.55f),
            lerp(primary, Color(0xFF3EE6C8), 0.55f),
            lerp(primary, Color(0xFFFF6BA0), 0.45f)
        )
    }
    // 粒子轨道包：每帧原地覆写，帧内零分配
    val orbitPack = remember { FloatArray(5 * SiriOrbMotion.PACK_STRIDE) }

    Canvas(modifier) {
        val t = if (motionEnabled) (elapsedNanos / 1_000_000_000.0).toFloat() else 0f
        if (shader != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
            for (i in 0..4) SiriOrbMotion.packParticle(t, i, 0f, 0f, 1f, orbitPack, i * SiriOrbMotion.PACK_STRIDE)
            shader.draw(this, t, isDark, primary, glowA, glowB, glowC, orbitPack)
        } else {
            drawSiriOrbFallback(primary, glowBlend, t, orbitPack)
        }
    }
}

/** Canvas 降级路径：与 AGSL 同款粒子观感（亮核+彩色光晕+双点拖尾），运动参数走 [SiriOrbMotion]。 */
private fun DrawScope.drawSiriOrbFallback(
    primary: Color, glowBlend: BlendMode, t: Float, orbitPack: FloatArray
) {
    val c = Offset(size.width / 2f, size.height / 2f)
    // 单位半径 0.34×min 与 AGSL 同构图：所有绘制（含光晕圆）收在画布边缘内侧，
    // 渐变一律收到全透明——露出直角画布边界=「浅色方片」观感事故
    val r = min(size.width, size.height) * 0.34f
    val pulse = SiriOrbMotion.pulse(t)

    // ── 球体外圈光晕（双层：外层更宽更淡、内层更实，都止步画布边缘内）──
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.10f), primary.copy(alpha = 0f)),
            center = c,
            radius = (r * 1.4f * pulse)
        ),
        radius = (r * 1.4f * pulse),
        center = c
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.26f), primary.copy(alpha = 0f)),
            center = c,
            radius = (r * 1.1f * pulse)
        ),
        radius = (r * 1.1f * pulse),
        center = c
    )

    // ── 柔雾身体：径向渐变收到全透明——没有球缘没有圆框 ──
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF232030), Color(0xFF14141E), Color(0xFF0A0A10).copy(alpha = 0f)),
            center = Offset(c.x, c.y - r * 0.15f),
            radius = r * 1.2f
        ),
        radius = r * 1.2f,
        center = c
    )

    // ── 磨砂哑光玻璃：左上角柔和受光面（淡）──
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0f)),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.45f),
            radius = r * 1.0f
        ),
        radius = r * 0.8f,
        center = Offset(c.x - r * 0.35f, c.y - r * 0.45f)
    )

    // ── 五颗环绕光粒子：双点拖尾（淡）→ 彩色光晕 → 白热亮核（轨道再收 0.8，光晕圆不出画布）──
    for (i in 0..4) {
        val o = i * SiriOrbMotion.PACK_STRIDE
        SiriOrbMotion.packParticle(t, i, c.x, c.y, r * 0.8f, orbitPack, o)
        val col = SiriOrbMotion.particleColors[i]
        val headX = orbitPack[o]
        val headY = orbitPack[o + 1]
        val pr = orbitPack[o + 2]
        val inten = orbitPack[o + 3]
        // 拖尾两点
        for (k in 1 downTo 0) {
            val tx = orbitPack[o + 4 + k * 2]
            val ty = orbitPack[o + 5 + k * 2]
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        col.copy(alpha = 0.45f - 0.22f * k), col.copy(alpha = 0f)
                    ),
                    center = Offset(tx, ty),
                    radius = pr * (2.0f - 0.5f * k)
                ),
                radius = pr * (2.0f - 0.5f * k),
                center = Offset(tx, ty),
                blendMode = glowBlend
            )
        }
        // 彩色光晕（重而糊：宽晕+裙边；1.2.4 压高光保色彩）
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(col.copy(alpha = 0.8f * inten), col.copy(alpha = 0f)),
                center = Offset(headX, headY),
                radius = pr * 4.2f
            ),
            radius = pr * 4.2f,
            center = Offset(headX, headY),
            blendMode = glowBlend
        )
        // 亮核（压低白度：白热过曝会把五彩糊成一片白）
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f * inten), col.copy(alpha = 0f)),
                center = Offset(headX, headY),
                radius = pr * 1.15f
            ),
            radius = pr * 1.15f,
            center = Offset(headX, headY),
            blendMode = glowBlend
        )
    }

    // ── 中心微光核心 ──
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFFFFF).copy(alpha = 0.35f), Color(0xFFFFFFFF).copy(alpha = 0f)),
            center = c,
            radius = r * 0.5f
        ),
        radius = r * 0.5f,
        center = c,
        blendMode = glowBlend
    )
    // （1.1.32 二轮撤掉玻璃边缘描边环——圆形框观感事故）
}
