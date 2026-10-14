package com.freechat.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 侧滑页「当前对话」竖条：果冻跟随 + 首页点选水滴滑入（1.1.29 工单⑦ 圆点→竖条形变）的纪律钉子。 */
class IndicatorDropletMotionTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun jellyOffsetIsSharedWithTheIndicatorBar() {
        val jelly = source("ui/animation/JellyPull.kt")
        val drawer = source("ui/components/DrawerContent.kt")
        assertTrue("JellyOverscroll exposes the applied translation as a readable offset",
            jelly.contains("offsetPx: androidx.compose.runtime.MutableFloatState") &&
                jelly.contains("offsetPx.floatValue = y"))
        assertTrue("Both drawer layouts feed the shared offset into their jelly layer",
            drawer.split("jellyOverscroll(offsetPx = jellyOffsetPx)").size - 1 == 2)
        assertTrue("The bar rides the same offset so it never detaches from its row",
            drawer.contains("(y + jellyY.value).roundToInt()"))
    }

    @Test fun appearSlidesDotInFromScreenEdgeThenLandsAsBar() {
        val drawer = source("ui/components/DrawerContent.kt")
        val anim = source("ui/animation/FreeChatAnimation.kt")
        assertTrue("Appear is the home-to-selection transition (no previous row)",
            drawer.contains("} else if (indicatorLastId == null) {"))
        assertTrue("Dot starts with its center on the screen left edge",
            drawer.contains("indicatorDeltaX.floatValue = -(indicatorStartPadPx + indicatorDotPx * 0.5f)"))
        assertTrue("Slow-then-fast nonlinear rate, fast overall, soft landing (180ms)",
            anim.contains("val indicatorAppearTween = tween<Float>(") &&
                anim.contains("INDICATOR_APPEAR_MS = 180") &&
                anim.contains("CubicBezierEasing(0.55f, 0.0f, 0.35f, 1.0f)"))
        assertTrue("Boot into a chat still lands instantly (no entrance on first paint)",
            drawer.contains("if (!indicatorReady) {"))
    }

    @Test fun dotToBarMorphSharesOneProgressWithTranslation() {
        val drawer = source("ui/components/DrawerContent.kt")
        assertTrue("Width and height interpolate dot→bar on the same eased progress",
            drawer.contains("val w = dotPx + (barWPx - dotPx) * p") &&
                drawer.contains("val h = dotPx + (heightPx - dotPx) * p"))
        assertTrue("Corner radius stays half-min-side: circle on the way in, capsule on arrival",
            drawer.contains("kotlin.math.min(w, h) / 2f, kotlin.math.min(w, h) / 2f"))
        assertTrue("「水滴指动画不指形态」：液尾与 spring 过冲全部退场",
            !drawer.contains("DrawerDropletShader") &&
                !drawer.contains("indicatorAppearSpec") &&
                !source("ui/animation/FreeChatAnimation.kt").contains("indicatorAppearSpec"))
        assertTrue("换行滑行不受影响：中段拉长 30% 照旧",
            drawer.contains("1f + 0.30f *") && drawer.contains("if (isAppearFlight())"))
    }
}
