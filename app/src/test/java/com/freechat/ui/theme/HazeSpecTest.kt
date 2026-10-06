package com.freechat.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HazeSpecTest {
    @Test
    fun titleGradientMovesUpWithoutCompressingItsAcceptedCurve() {
        listOf(0.dp, 24.dp, 48.dp).forEach { inset ->
            val titleBottom = HazeSpec.titleBarHeightDp(inset)
            val blurBottom = HazeSpec.topBandHeightDp(inset)
            assertEquals(inset + 48.dp, titleBottom)
            assertEquals(titleBottom + 32.dp, blurBottom)
            assertEquals(24.dp, (titleBottom + 56.dp) - blurBottom)
            assertEquals(inset - 24.dp, HazeSpec.topFadeStartDp(blurBottom))
            assertEquals(104.dp, blurBottom - HazeSpec.topFadeStartDp(blurBottom))
            assertTrue(HazeSpec.topFadeStartDp(blurBottom) < titleBottom)
        }
    }

    @Test
    fun pageLayoutPaddingIsIndependentOfBlurExtent() {
        listOf(0.dp, 24.dp, 48.dp).forEach { inset ->
            listOf(8.dp, 16.dp, 20.dp, 24.dp, 32.dp, 36.dp, 72.dp).forEach { originalGap ->
                val bodyStart = HazeSpec.topContentPaddingDp(inset, originalGap)
                assertEquals(HazeSpec.titleBarHeightDp(inset) + originalGap, bodyStart)
            }
            // 原本已有充足留白的页面不额外加高。
            assertEquals(inset + 48.dp + 72.dp, HazeSpec.topContentPaddingDp(inset, 72.dp))
        }
    }

    @Test
    fun drawerFadeEndsBelowNewChatButBeforeInitialPinnedGroup() {
        val gapAbove = 24.dp
        val cardHeight = 40.dp
        val cardGap = 10.dp
        val gapBelow = HazeSpec.DrawerHeaderBottomGapDp
        assertEquals(24.dp, gapBelow)
        listOf(0.dp, 24.dp, 48.dp).forEach { inset ->
            val newChatTop = HazeSpec.titleBarHeightDp(inset) + gapAbove + cardHeight + cardGap
            val fixedHeader = gapAbove + cardHeight + cardGap + cardHeight + gapBelow
            val listStart = HazeSpec.drawerTopBandHeightDp(inset, fixedHeader)
            assertEquals(newChatTop + cardHeight + gapBelow, listStart)
            val fadeStart = HazeSpec.topFadeStartDp(listStart, HazeSpec.DrawerTopFadeZoneDp)
            assertTrue(fadeStart < newChatTop)
            assertTrue(fadeStart >= HazeSpec.titleBarHeightDp(inset))
            // 初始分组从 listStart 起排布，覆盖层到此归零并裁剪，不会模糊下方文字。
            assertEquals(104.dp, listStart - fadeStart)
        }
    }

    @Test
    fun selectionHeaderShrinksBlurTogetherWithListPadding() {
        val selectionHeader = 24.dp + 40.dp + HazeSpec.DrawerHeaderBottomGapDp
        listOf(0.dp, 24.dp, 48.dp).forEach { inset ->
            val blurBottom = HazeSpec.drawerTopBandHeightDp(inset, selectionHeader)
            assertEquals(HazeSpec.titleBarHeightDp(inset) + selectionHeader, blurBottom)
            val fadeStart = HazeSpec.topFadeStartDp(blurBottom, HazeSpec.DrawerTopFadeZoneDp)
            // 多选头部较短，长渐变可向标题内延伸 16dp，但不改变标题或初始列表位置。
            assertEquals(HazeSpec.titleBarHeightDp(inset) - 16.dp, fadeStart)
            assertEquals(104.dp, blurBottom - fadeStart)
        }
    }

    @Test
    fun shortStatusBarsKeepTheSamePhysicalGradientLength() {
        assertEquals((-48).dp, HazeSpec.topFadeStartDp(16.dp, 64.dp))
        assertEquals(48.dp, HazeSpec.topFadeStartDp(72.dp, 24.dp))
        assertEquals((-32).dp, HazeSpec.topFadeStartDp(72.dp))
    }

    @Test
    fun gradientIsMonotonicAndClamped() {
        assertEquals(0f, HazeSpec.TopBlurEasing.transform(-1f), 0f)
        assertEquals(1f, HazeSpec.TopBlurEasing.transform(2f), 0f)
        assertEquals(0.5f, HazeSpec.TopBlurEasing.transform(0.5f), 0f)
        var previous = 0f
        for (index in 0..1000) {
            val fraction = index / 1000f
            val value = HazeSpec.TopBlurEasing.transform(fraction)
            assertTrue(value in 0f..1f)
            assertTrue(value >= previous)
            previous = value
        }
    }

    @Test
    fun gradientMeetsClearAndFullBlurRegionsWithoutASlopeJump() {
        val step = 0.0001f
        val startSlope = HazeSpec.TopBlurEasing.transform(step) / step
        val endSlope = (1f - HazeSpec.TopBlurEasing.transform(1f - step)) / step
        assertTrue(startSlope < 0.002f)
        assertTrue(endSlope < 0.002f)
        // 刚进入 2dp 时仍接近清晰，避免短线性渐变迅速变糊。
        val enteringIntensity = 1f - HazeSpec.TopBlurEasing.transform(1f - 2f / 104f)
        assertTrue(enteringIntensity > 0f && enteringIntensity < 0.0003f)
    }

    @Test
    fun titleAndCardEdgesHaveEnoughBlurWithoutASteepRamp() {
        val titleCenterIntensity = 1f - HazeSpec.TopBlurEasing.transform(1f - 56f / 104f)
        val titleBottomIntensity = 1f - HazeSpec.TopBlurEasing.transform(1f - 32f / 104f)
        val newChatBottomIntensity = 1f - HazeSpec.TopBlurEasing.transform(1f - 24f / 104f)
        assertTrue(titleCenterIntensity > 0.54f)
        assertTrue(titleBottomIntensity > 0.26f)
        assertTrue(newChatBottomIntensity > 0.17f)
        for (positionDp in 1..104) {
            val previous = HazeSpec.TopBlurEasing.transform((positionDp - 1) / 104f)
            val current = HazeSpec.TopBlurEasing.transform(positionDp / 104f)
            // 旧偏置曲线中段每 dp 约 0.033；现在整段最多 1.2/104，低约 65%。
            assertTrue(current - previous < 0.0116f)
        }
    }

    @Test
    fun underlayFeatherIsSymmetricAndMonotonic() {
        assertEquals(0f, HazeSpec.smoothFadeProgress(-1f), 0f)
        assertEquals(1f, HazeSpec.smoothFadeProgress(2f), 0f)
        var previous = 0f
        for (index in 0..1000) {
            val fraction = index / 1000f
            val value = HazeSpec.smoothFadeProgress(fraction)
            assertTrue(value in 0f..1f && value >= previous)
            assertEquals(1f, value + HazeSpec.smoothFadeProgress(1f - fraction), 0.000001f)
            previous = value
        }
    }

    @Test
    fun featheredUnderlayEndsBeforeTheFirstSettingsCard() {
        listOf(0.dp, 24.dp, 48.dp).forEach { inset ->
            val underlayEnd = HazeSpec.titleBarHeightDp(inset) + HazeSpec.HeaderUnderlayFeatherDp
            assertTrue(underlayEnd < HazeSpec.topBandHeightDp(inset))
            assertTrue(underlayEnd < HazeSpec.topContentPaddingDp(inset, 40.dp))
            assertTrue(underlayEnd < HazeSpec.drawerTopBandHeightDp(inset, 24.dp + 40.dp + HazeSpec.DrawerHeaderBottomGapDp))
        }
    }

    @Test
    fun outputFeatherFadesTheWholeEffectToZeroWithinTheExtendedBand() {
        assertEquals(36.dp, HazeSpec.TopOutputFeatherDp)
        assertEquals(0f, HazeSpec.topOutputAlpha(-1f), 0f)
        assertEquals(0f, HazeSpec.topOutputAlpha(0f), 0f)
        assertEquals(1f, HazeSpec.topOutputAlpha(1f), 0f)
        assertEquals(1f, HazeSpec.topOutputAlpha(2f), 0f)
        listOf(0.dp, 24.dp, 48.dp).forEach { inset ->
            val featherStart = HazeSpec.topBandHeightDp(inset) - HazeSpec.TopOutputFeatherDp
            // 曲线整体上移 24dp，而非缩短末端 36dp 的柔和羽化。
            assertEquals(HazeSpec.titleBarHeightDp(inset) - 4.dp, featherStart)
            assertTrue(HazeSpec.TopOutputFeatherDp < HazeSpec.TopProgressiveSpanDp)
            assertTrue(HazeSpec.TopOutputFeatherDp < HazeSpec.DrawerTopFadeZoneDp)
        }
    }

    @Test
    fun outputFeatherHasGentleEdgesAndNoReverseAlphaSteps() {
        val step = 0.0001f
        assertTrue(HazeSpec.topOutputAlpha(step) / step < 0.000001f)
        assertTrue((1f - HazeSpec.topOutputAlpha(1f - step)) / step < 0.000001f)
        var previous = 0f
        for (index in 0..1000) {
            val value = HazeSpec.topOutputAlpha(index / 1000f)
            assertTrue(value in 0f..1f && value >= previous)
            previous = value
        }
        // 最下缘即使模糊核存在整像素跳变，整个结果也仅有极小权重。
        assertTrue(HazeSpec.topOutputAlpha(2f / 36f) < 0.0016f)
        assertTrue(HazeSpec.topOutputAlpha(4f / 36f) < 0.012f)
        assertTrue(HazeSpec.topOutputAlpha(8f / 36f) < 0.077f)
        assertEquals(0.5f, HazeSpec.topOutputAlpha(18f / 36f), 0f)
    }

    @Test
    fun edgeBlendIsMuchWeakerButKeepsTheInnerTitleBlur() {
        fun intensity(depthDp: Float) = 1f - HazeSpec.TopBlurEasing.transform(1f - depthDp / 104f)
        fun blended(depthDp: Float) = intensity(depthDp) * HazeSpec.topOutputAlpha(depthDp / 36f)
        assertTrue(blended(2f) < 0.0000005f)
        assertTrue(blended(4f) < intensity(4f) * 0.012f)
        assertTrue(blended(8f) < intensity(8f) * 0.077f)
        assertEquals(intensity(56f), blended(56f), 0f)
        assertEquals(intensity(80f), blended(80f), 0f)
        var previous = 0f
        for (index in 0..1040) {
            val value = blended(index / 10f)
            assertTrue(value in 0f..1f && value >= previous)
            assertTrue(value - previous < 0.0018f)
            previous = value
        }
    }

    @Test
    fun strongBlurBuildsOverMultipleTextLinesRatherThanOneNarrowStrip() {
        fun blended(depthDp: Float): Float =
            (1f - HazeSpec.TopBlurEasing.transform(1f - depthDp / 104f)) *
                HazeSpec.topOutputAlpha(depthDp / 36f)
        val depths = (0..1040).map { it / 10f }
        val mildDepth = depths.first { blended(it) >= 0.1f }
        val strongDepth = depths.first { blended(it) >= 0.9f }
        // 主要可见变化展开到超过 60dp，而非在一行 20dp 左右的距离内突然加深。
        assertTrue(strongDepth - mildDepth > 60f)
        assertTrue(blended(12f) < 0.01f)
        assertTrue(blended(24f) < 0.15f)
        assertTrue(blended(56f) >= 0.54f)
    }

    @Test
    fun curveJoinsTheUniformMiddleWithoutSlopeOrCurvatureJumps() {
        val step = 0.0001f
        val cap = 1f / 6f
        listOf(cap, 1f - cap).forEach { join ->
            val at = HazeSpec.TopBlurEasing.transform(join)
            val leftSlope = (at - HazeSpec.TopBlurEasing.transform(join - step)) / step
            val rightSlope = (HazeSpec.TopBlurEasing.transform(join + step) - at) / step
            assertEquals(1.2f, leftSlope, 0.002f)
            assertEquals(leftSlope, rightSlope, 0.002f)
        }
        val middleSteps = (2..9).map { index ->
            HazeSpec.TopBlurEasing.transform(index / 12f + 1f / 12f) -
                HazeSpec.TopBlurEasing.transform(index / 12f)
        }
        middleSteps.forEach { assertEquals(0.1f, it, 0.000001f) }
        // 首尾近似三次启动：二阶导数也接近零，没有接到常量区时的折角。
        assertTrue(HazeSpec.TopBlurEasing.transform(step) / (step * step) < 0.01f)
    }
}
