package com.freechat.ui.components

import java.util.Random
import kotlin.math.floor
import kotlin.math.sin

/** Deterministic, smoothly varying paths. No per-frame RNG, short repeating orbit or particle respawn. */
internal object ImageParticleMotion {
    const val Count = 40
    data class Particle(val seed: Int, val baseX: Float, val baseY: Float, val phase: Float,
        val speed: Float, val radius: Float, val tint: Int) {
        fun x(seconds: Float): Float = (baseX + .10f * noise(seconds * speed, seed) +
            .025f * sin(seconds * .16f + phase)).coerceIn(.025f, .975f)
        fun y(seconds: Float): Float = (baseY + .10f * noise(seconds * speed * .79f, seed + 71) +
            .03f * sin(seconds * .11f - phase)).coerceIn(.025f, .975f)
        fun alpha(seconds: Float): Float = .30f + .11f * noise(seconds * .12f, seed + 139)
    }
    fun create(): List<Particle> {
        val random = Random(0xFCEE991L)
        return List(Count) { i -> Particle(i * 97 + 11, .14f + random.nextFloat() * .72f,
            .14f + random.nextFloat() * .72f, random.nextFloat() * 6.283185f,
            .07f + random.nextFloat() * .07f, .6f + random.nextFloat() * .85f, i % 4) }
    }
    // Quintic interpolation gives continuous velocity AND acceleration across random control points.
    private fun noise(t: Float, seed: Int): Float {
        val cell = floor(t).toInt()
        val f = t - cell
        val eased = f * f * f * (f * (f * 6f - 15f) + 10f)
        fun value(at: Int): Float {
            var hash = seed xor (at * 0x45d9f3b)
            hash = (hash xor (hash ushr 16)) * 0x45d9f3b
            hash = hash xor (hash ushr 16)
            return (hash ushr 8) / 16777215f * 2f - 1f
        }
        val a = value(cell)
        return a + (value(cell + 1) - a) * eased
    }
}
