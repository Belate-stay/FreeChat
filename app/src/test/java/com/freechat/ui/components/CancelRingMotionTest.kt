package com.freechat.ui.components

import com.freechat.ui.theme.SoftShadowShaderSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CancelRingMotionTest {

    @Test fun ringClearsTheFingerOnTheRayAndKeepsTheTouchPointInside() {
        // 实机二轮返修契约：弧中点=指尖+gap（在射线上、离指头远到不遮），触控点仍在环内（gap<2R）
        val p = VoiceInputMotion.cancelRingPlacement(
            originX = 0f, originY = 0f, fingerX = 100f, fingerY = 0f,
            gapPx = 64f, ringRadiusPx = 40f, textExtraPx = 14f
        )
        // 弧中点 = 环心 + u·R = 指尖 + gap
        assertEquals(164f, p.centerX + p.ringRadiusPx, 0.01f)
        assertEquals(0f, p.centerY, 0.01f)
        // 触控点在环内
        val distCenterToFinger = kotlin.math.abs(p.centerX - 100f)
        assertTrue("触控点必须在圆环内部", distCenterToFinger < p.ringRadiusPx)
        assertTrue("文字排在红环外圈", p.textRadiusPx > p.ringRadiusPx)
    }

    @Test fun arcFacesAwayFromTheVoiceButtonAlongTheRay() {
        val ox = 40f; val oy = 800f
        val fx = 220f; val fy = 300f
        val p = VoiceInputMotion.cancelRingPlacement(ox, oy, fx, fy, gapPx = 64f, ringRadiusPx = 40f, textExtraPx = 14f)
        // 角度=「语音键→指尖」射线方向（弧扇区以此为对称轴朝外，整组恒在射线上）
        val expect = Math.atan2((fy - oy).toDouble(), (fx - ox).toDouble()).toFloat()
        assertEquals(expect, p.angleRad, 0.01f)
    }

    @Test fun degenerateTouchOnCenterFallsBackToUpward() {
        val p = VoiceInputMotion.cancelRingPlacement(
            originX = 50f, originY = 50f, fingerX = 50f, fingerY = 50f,
            gapPx = 64f, ringRadiusPx = 40f, textExtraPx = 14f
        )
        assertEquals(-Math.PI.toFloat() / 2f, p.angleRad, 0.001f)
    }

    @Test fun cancelRingShaderSourceKeepsAgslDiscipline() {
        val src = VoiceCancelRingShaderSource.Agsl
        assertFalse("AGSL has no fwidth()", src.contains("fwidth"))
        assertTrue("banding dither is required for the soft halo", src.contains("bandingNoise"))
        assertTrue("dither must actually be applied", src.contains("bandingNoise(fragCoord)"))
        assertTrue(src.contains("u_alpha"))
    }

    @Test fun softShadowShaderSourceKeepsAgslDiscipline() {
        val src = SoftShadowShaderSource.Agsl
        assertFalse("AGSL has no fwidth()", src.contains("fwidth"))
        assertTrue("banding dither is required — 灰阶断层就是它治的", src.contains("bandingNoise"))
        assertTrue(src.contains("sdRoundRect"))
    }

    @Test fun shadowBodyStaysGluedToTheCardWithoutAGapRing() {
        // 实机「阴影是白的/变高光」根因：step(0,d) 把卡面与位移影体的重叠带清零，
        // 影子整体脱开、卡片周围留一圈底色白缝。重叠带必须按满黑度（d≤0 取 0）画。
        val src = SoftShadowShaderSource.Agsl
        assertTrue("SDF 内部取 0 继续衰减（重叠带满黑度）", src.contains("max(d, 0.0)"))
        assertFalse("绝不许再用 step(0.0, d) 清零内部", src.contains("step(0.0, d)"))
    }

    @Test fun layerStackFallbackScalesAlphaSoTheNewDepthMatchesTheOld() {
        // 观感黑度守恒：层数 8→32，每层 alpha 按 8/N 折算（Frosted.kt 的 SHADOW_LAYER_SCALE）
        val frosted = java.io.File("src/main/java/com/freechat/ui/theme/Frosted.kt").readText()
        assertTrue(frosted.contains("SHADOW_LAYER_SCALE = 8f / SHADOW_LAYERS"))
        assertTrue(frosted.contains("private const val SHADOW_LAYERS = 32"))
    }
}
