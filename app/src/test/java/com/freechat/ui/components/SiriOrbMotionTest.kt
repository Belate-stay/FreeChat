package com.freechat.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SiriOrbMotionTest {
    private val radius = 30f

    @Test fun parametersStayBoundedDuringLongGeneration() {
        // Bounds carry a hair of slack: float sin() can miss exact ±1 at the edges.
        for (seconds in listOf(0f, .5f, 2f, 8f, 100f, 3600f)) {
            assertTrue("pulse out of bounds at $seconds", SiriOrbMotion.pulse(seconds) in .849f..1.151f)
            for (index in SiriOrbMotion.blobParams.indices) {
                assertTrue("breathe out of bounds at $seconds", SiriOrbMotion.breathe(seconds, index) in .399f..1.001f)
                val blob = SiriOrbMotion.blob(seconds, index, radius, radius, radius)
                assertTrue("blob radius out of bounds at $seconds", blob.radius in radius * .159f..radius * .401f)
                assertTrue("blob x left the orbit at $seconds",
                    blob.x in radius - radius * .841f..radius + radius * .841f)
                assertTrue("blob y left the orbit at $seconds",
                    blob.y in radius - radius * .741f..radius + radius * .741f)
                assertTrue("phase negative at $seconds", SiriOrbMotion.phase(seconds, index) >= 0f)
            }
        }
    }

    @Test fun motionIsSmoothAcrossRenderingFrames() {
        val dt = .016f
        for (step in 0..240) {
            val t = step * .25f
            assertTrue("pulse jumped at $t", abs(SiriOrbMotion.pulse(t + dt) - SiriOrbMotion.pulse(t)) < .01f)
            for (index in SiriOrbMotion.blobParams.indices) {
                assertTrue("breathe jumped at $t",
                    abs(SiriOrbMotion.breathe(t + dt, index) - SiriOrbMotion.breathe(t, index)) < .02f)
                val a = SiriOrbMotion.blob(t, index, radius, radius, radius)
                val b = SiriOrbMotion.blob(t + dt, index, radius, radius, radius)
                assertTrue("blob jumped at $t", abs(a.x - b.x) < .05f * radius)
                assertTrue("blob jumped at $t", abs(a.y - b.y) < .05f * radius)
                assertTrue("blob radius jumped at $t", abs(a.radius - b.radius) < .02f * radius)
            }
        }
    }

    @Test fun motionIsDeterministic() {
        for (seconds in listOf(0f, 3.7f, 120f, 3600f)) {
            assertEquals(SiriOrbMotion.pulse(seconds), SiriOrbMotion.pulse(seconds), 0f)
            for (index in SiriOrbMotion.blobParams.indices) {
                assertEquals(SiriOrbMotion.blob(seconds, index, radius, radius, radius),
                    SiriOrbMotion.blob(seconds, index, radius, radius, radius))
                assertEquals(SiriOrbMotion.phase(seconds, index), SiriOrbMotion.phase(seconds, index), 0f)
            }
        }
    }

    @Test fun particlesAreVisibleAndRoamFree() {
        val pack = FloatArray(SiriOrbMotion.PACK_STRIDE)
        for (seconds in listOf(0f, 1f, 8f, 100f, 3600f)) {
            for (index in SiriOrbMotion.blobParams.indices) {
                SiriOrbMotion.packParticle(seconds, index, radius, radius, radius, pack, 0)
                val coreR = pack[2]
                val inten = pack[3]
                // 亮核不能小到看不见（旧版粒子≈噪声尘点的根因）
                assertTrue("particle core too small at $seconds", coreR >= radius * 0.10f)
                assertTrue("particle core too big at $seconds", coreR <= radius * 0.19f)
                assertTrue("intensity out of bounds at $seconds", inten in 0.49f..1.01f)
                // 自由越界但不失控：Lissajous 矩形 0.95/0.85 的可达域
                assertTrue("particle x out of envelope at $seconds",
                    pack[0] in radius - radius * 0.96f..radius + radius * 0.96f)
                assertTrue("particle y out of envelope at $seconds",
                    pack[1] in radius - radius * 0.86f..radius + radius * 0.86f)
            }
            // 任意时段都有大扫掠 = 幅度够大（不能缩在中心一小团）
            var farthest = 0f
            for (index in SiriOrbMotion.blobParams.indices) {
                for (si in 0..24) {
                    val tt = seconds + si * 0.25f
                    SiriOrbMotion.packParticle(tt, index, radius, radius, radius, pack, 0)
                    val dx = pack[0] - radius
                    val dy = pack[1] - radius
                    farthest = maxOf(farthest, kotlin.math.hypot(dx, dy))
                }
            }
            assertTrue("orbit amplitude too small near $seconds", farthest >= radius * 0.70f)
        }
        // 粒子不圈禁在单位圈内：全时段扫掠必须有越界时刻（用户点名「可以移出圆圈」）
        var maxReach = 0f
        for (index in SiriOrbMotion.blobParams.indices) {
            for (si in 0..120) {
                val tt = si * 0.25f
                SiriOrbMotion.packParticle(tt, index, radius, radius, radius, pack, 0)
                val dx = pack[0] - radius
                val dy = pack[1] - radius
                maxReach = maxOf(maxReach, kotlin.math.hypot(dx, dy))
            }
        }
        assertTrue("particles never leave the unit circle", maxReach > radius * 1.0f)
    }

    @Test fun particlesMoveSmoothlyAcrossFrames() {
        val packA = FloatArray(SiriOrbMotion.PACK_STRIDE)
        val packB = FloatArray(SiriOrbMotion.PACK_STRIDE)
        val dt = .016f
        for (step in 0..240) {
            val t = step * .25f
            for (index in SiriOrbMotion.blobParams.indices) {
                SiriOrbMotion.packParticle(t, index, radius, radius, radius, packA, 0)
                SiriOrbMotion.packParticle(t + dt, index, radius, radius, radius, packB, 0)
                // 非线性速率叠了慢波，峰值速度高一档——仍是连续运动（无跳变）
                assertTrue("particle jumped at $t", abs(packA[0] - packB[0]) < .06f * radius)
                assertTrue("particle jumped at $t", abs(packA[1] - packB[1]) < .06f * radius)
                assertTrue("core radius jumped at $t", abs(packA[2] - packB[2]) < .02f * radius)
                assertTrue("intensity jumped at $t", abs(packA[3] - packB[3]) < .05f)
            }
        }
    }

    @Test fun trailsSampleThePastAlongTheSameOrbit() {
        val pack = FloatArray(SiriOrbMotion.PACK_STRIDE)
        val past = FloatArray(2)
        for (seconds in listOf(2f, 30f, 500f)) {
            for (index in SiriOrbMotion.blobParams.indices) {
                SiriOrbMotion.packParticle(seconds, index, radius, radius, radius, pack, 0)
                for (k in 0..1) {
                    SiriOrbMotion.particlePos(
                        seconds - SiriOrbMotion.TRAIL_LAGS[k], index, radius, radius, radius, past, 0)
                    assertEquals("trail ${k + 1} x must be the orbit at t-lag",
                        past[0], pack[4 + k * 2], 1e-4f)
                    assertEquals("trail ${k + 1} y must be the orbit at t-lag",
                        past[1], pack[5 + k * 2], 1e-4f)
                }
            }
        }
    }

    @Test fun orbitsNeverCloseIntoAVisibleLoop() {
        SiriOrbMotion.blobParams.forEachIndexed { index, params ->
            val (a, b, speed) = params
            assertTrue("speed must stay positive", speed > 0.0)
            val ratio = a / b
            assertTrue("freq ratio $ratio is too close to a simple rational",
                poorlyApproximated(ratio, maxQ = 12, tol = 1e-3))
            // Phase is strictly increasing — the driver never wraps back to its start.
            assertTrue(SiriOrbMotion.phase(3600f, index) > SiriOrbMotion.phase(0f, index))
        }
    }

    @Test fun shaderSourceKeepsAgslDiscipline() {
        val src = SiriOrbShaderSource.Agsl
        assertTrue("simplex noise is required for organic flow", src.contains("snoise"))
        assertTrue("banding dither is required — AGSL has no fwidth()", src.contains("bandingNoise"))
        assertTrue("dither must actually be applied", src.contains("bandingNoise(fragCoord)"))
        assertFalse("AGSL has no fwidth()", src.contains("fwidth"))
        for (uniform in listOf(
            "uniform float2 u_size", "uniform float u_time", "uniform float u_dark",
            "layout(color) uniform half4 u_primary", "layout(color) uniform half4 u_glowA",
            "layout(color) uniform half4 u_glowB", "layout(color) uniform half4 u_glowC",
            "uniform float4 u_p0", "uniform float4 u_p4", "uniform float4 u_tr0",
            "layout(color) uniform half4 u_pc0", "layout(color) uniform half4 u_pc4"
        )) {
            assertTrue("missing $uniform", src.contains(uniform))
        }
        assertTrue("particles need whitened hot cores", src.contains("particleLight"))
        assertTrue("particle halo must use heavy blurred exp falloff", src.contains("exp(-d / (pr * 2.4))"))
        assertTrue("particle skirt feathers the glow edge", src.contains("skirt = exp(-d / (pr * 5.5))"))
        assertFalse("球缘/圆形框已撤（羽化底色取代硬边）", src.contains("glass = smoothstep"))
        assertTrue(src.contains("half4 main(float2 fragCoord)"))
        // Nested """ would break the Kotlin raw string that carries the AGSL.
        assertEquals(0, src.split("\"\"\"").size - 1)
    }

    /** True when no p/q with q <= maxQ lands within tol of [ratio]. */
    private fun poorlyApproximated(ratio: Double, maxQ: Int, tol: Double): Boolean {
        for (q in 1..maxQ) for (p in 0..maxQ * 2) {
            if (abs(ratio - p.toDouble() / q) < tol) return false
        }
        return true
    }
}
