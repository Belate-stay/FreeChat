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
