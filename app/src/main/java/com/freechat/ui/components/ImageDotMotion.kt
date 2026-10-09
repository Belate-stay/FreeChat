package com.freechat.ui.components

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import com.freechat.data.GenerationPhase

/** Fixed cells share broad, continuous waves; no random paths or accumulated progress. */
internal object ImageDotMotion {
    const val Columns = 20
    const val MinRadius = .065f
    const val MaxRadius = .24f
    const val Corner = .14f
    const val Feather = .08f

    data class Frame(
        val ax: Float, val ay: Float, val aWeight: Float,
        val bx: Float, val by: Float, val bWeight: Float,
        val cx: Float, val cy: Float, val cWeight: Float,
        val sheenCenter: Float, val ramp: Float, val growthFloor: Float = 0f
    ) {
        fun strength(x: Float, y: Float): Float {
            val a = bump(x - ax, y - ay, .40f) * aWeight
            val b = bump(x - bx, y - by, .44f) * bWeight
            val c = bump(x - cx, y - cy, .38f) * cWeight
            val wave = (1f - (1f - a) * (1f - b) * (1f - c)).coerceIn(0f, 1f)
            return growthFloor + (1f - growthFloor) * wave
        }

        fun radius(x: Float, y: Float): Float =
            (MinRadius + (MaxRadius - MinRadius) * strength(x, y)).coerceIn(MinRadius, MaxRadius)

        fun sheen(x: Float, y: Float): Float =
            ramp * (1f - smoothStep(0f, .23f, abs(.72f * x + .28f * y - sheenCenter)))
    }

    // All trigonometry is evaluated once per draw, then sent to the shader as uniforms.
    // These are visual sizes for actual client events, not percentages or guesses about provider work.
    fun growthFor(phase: GenerationPhase): Float = when (phase) {
        GenerationPhase.IMAGE_CONNECTING -> .04f
        GenerationPhase.IMAGE_GENERATING -> .12f
        GenerationPhase.IMAGE_RECEIVING -> .62f
        else -> 0f // Unknown progress keeps the original coherent waves.
    }

    fun frame(seconds: Float, growthFloor: Float = 0f): Frame {
        val t = seconds.coerceAtLeast(0f)
        val ramp = smoothStep(0f, 1.8f, t)
        return Frame(
            .24f + .25f * sin(t * .16f), .32f + .22f * sin(t * .18f + .4f),
            ramp * (.55f + .40f * sin(t * .65f)),
            .66f + .21f * sin(t * .13f + 1.8f), .67f + .20f * sin(t * .15f + 3.1f),
            ramp * (.55f + .40f * sin(t * .55f + 2.2f)),
            .48f + .24f * sin(t * .12f + 3.9f), .45f + .23f * sin(t * .17f + 5.1f),
            ramp * (.55f + .40f * sin(t * .49f + 4.5f)),
            .50f + .65f * sin(t * .16f - 1.2f), ramp, growthFloor.coerceIn(0f, 1f)
        )
    }

    fun edgeOpacity(x: Float, y: Float): Float {
        val qx = abs(x - .5f) - (.5f - Corner)
        val qy = abs(y - .5f) - (.5f - Corner)
        val ox = max(qx, 0f)
        val oy = max(qy, 0f)
        val distance = sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - Corner
        return smoothStep(0f, Feather, -distance)
    }

    private fun bump(x: Float, y: Float, radius: Float): Float =
        1f - smoothStep(0f, radius, sqrt(x * x + y * y))

    internal fun smoothStep(from: Float, to: Float, value: Float): Float {
        val x = ((value - from) / (to - from)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
