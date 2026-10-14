package com.freechat.ui.components

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

internal object VoiceInputMotion {
    data class Pose(val scaleX: Float, val scaleY: Float, val alpha: Float)

    fun pose(progress: Float): Pose {
        val p = progress.coerceIn(0f, 1f)
        return Pose((1f - p).coerceAtLeast(.002f), 1f - .88f * p * p,
            1f - smoothStep(.48f, .98f, p))
    }

    fun taper(progress: Float, x: Float): Float =
        1f - .68f * sin(PI * progress.coerceIn(0f, 1f)).toFloat() *
            (1f - smoothStep(.15f, 1f, x))

    fun isInsideButton(x: Float, y: Float, width: Float, height: Float, slop: Float): Boolean {
        val dx = x - width / 2f
        val dy = y - height / 2f
        return sqrt(dx * dx + dy * dy) <= minOf(width, height) / 2f + slop
    }

    /** 取消指示器（红环+环绕红字）的摆放几何。angleRad=「语音键→指尖」射线方向，弧与文字关于它对称。 */
    data class CancelRingPlacement(
        val centerX: Float,
        val centerY: Float,
        val angleRad: Float,
        val ringRadiusPx: Float,
        val textRadiusPx: Float
    )

    /**
     * 红环几何（1.1.29.9 工单⑥，实机两轮返修）：沿「语音键→指尖」射线越过指尖 gapPx 处取
     * 弧中点 E（离指头足够远、不被手指盖住），环心 C = E − u·R；**触控点在环内** ⟺ gap < 2R
     * （gap=64/R=40 满足）。弧的扇区朝拖出方向、随指尖实时转，整组恒在射线上。
     * 指尖=语音键圆心的退化给默认朝上，避免 atan2(0,0) 抖方向。
     */
    fun cancelRingPlacement(
        originX: Float, originY: Float,
        fingerX: Float, fingerY: Float,
        gapPx: Float, ringRadiusPx: Float, textExtraPx: Float
    ): CancelRingPlacement {
        val dx = fingerX - originX
        val dy = fingerY - originY
        val d = sqrt(dx * dx + dy * dy)
        val angle = if (d < 1f) -Math.PI.toFloat() / 2f
            else Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
        val ux = if (d < 1f) 0f else dx / d
        val uy = if (d < 1f) -1f else dy / d
        return CancelRingPlacement(
            centerX = fingerX + ux * (gapPx - ringRadiusPx),
            centerY = fingerY + uy * (gapPx - ringRadiusPx),
            angleRad = angle,
            ringRadiusPx = ringRadiusPx,
            textRadiusPx = ringRadiusPx + textExtraPx
        )
    }

    fun bandAt(bands: FloatArray, x: Float): Float {
        if (bands.isEmpty()) return 0f
        val at = x.coerceIn(0f, 1f) * (bands.size - 1)
        val left = at.toInt()
        val right = (left + 1).coerceAtMost(bands.lastIndex)
        return (bands[left] + (bands[right] - bands[left]) * (at - left)).coerceIn(0f, 1f)
    }

    private fun smoothStep(low: Float, high: Float, value: Float): Float {
        val p = ((value - low) / (high - low)).coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }
}
