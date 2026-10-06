package com.freechat.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.freechat.model.AppIcon
import com.freechat.model.activationOrder
import com.freechat.i18n.ZhCN
import com.freechat.i18n.ZhTW
import com.freechat.i18n.En
import org.junit.Assert.*
import org.junit.Test

class Beta83RegressionTest {
    @Test fun newInstallsUseBlueFAndUnknownSavedChoiceHasSafeDefault() {
        assertEquals(AppIcon.BLUE_F, AppIcon.default)
        assertEquals(AppIcon.BLUE_F, AppIcon.fromId(null))
        assertEquals(AppIcon.BLUE_F, AppIcon.fromId("removed-icon"))
        assertEquals(1, AppIcon.entries.count { it.enabledByDefault })
    }

    @Test fun iconIdsAndAliasesAreUniqueAndRoundtripWithoutOrdinals() {
        assertEquals(AppIcon.entries.size, AppIcon.entries.map { it.id }.distinct().size)
        assertEquals(AppIcon.entries.size, AppIcon.entries.map { it.alias }.distinct().size)
        AppIcon.entries.forEach { assertEquals(it, AppIcon.fromId(it.id)) }
        assertEquals("LauncherBlueF", AppIcon.BLUE_F.alias)
        assertEquals("LauncherClassic", AppIcon.CLASSIC.alias)
    }

    @Test fun preAndroid33ActivationNeverDisablesOldEntryBeforeEnablingTarget() {
        AppIcon.entries.forEach {
            assertEquals(it, it.activationOrder().first())
            assertEquals(AppIcon.entries.toSet(), it.activationOrder().toSet())
        }
    }

    @Test fun scrollProgressUsesHeightNotJustMessageIndex() {
        val g = ChatScrollGeometry(listOf(100f, 1000f, 100f), 0, 0, 0, 200)
        assertEquals(1000.0, g.scrollExtent, 0.01)
        assertEquals(0.1f, g.fraction(1, 0), 0.0001f)
        assertEquals(0.6f, g.fraction(1, 500), 0.0001f)
        assertEquals(ChatScrollTarget(1, 400), g.target(0.5f))
    }

    @Test fun mixedRowsPaddingAndSpacingShareTheSameSeekCoordinate() {
        val g = ChatScrollGeometry(listOf(32f, 500f, 80f, 1200f), 16, 100, 152, 700)
        for (f in listOf(0.1f, 0.3f, 0.5f, 0.9f)) {
            val target = g.target(f)
            assertEquals(f, g.fraction(target.index, target.offset), 0.001f)
        }
    }

    @Test fun scrollbarEndpointsAreExactEvenWithEstimatedHeights() {
        val g = ChatScrollGeometry(List(10000) { 240f }, 16, 100, 152, 1000)
        assertEquals(ChatScrollTarget(0, 0), g.target(0f))
        assertEquals(ChatScrollTarget(9999, 1_000_000), g.target(1f))
        assertEquals(ChatScrollTarget(0, 0), g.target(Float.NaN))
        assertEquals(1f, g.fraction(9999, Int.MAX_VALUE), 0f)
    }

    @Test fun emptyAndNonScrollableHistoryHaveStableProgress() {
        val empty = ChatScrollGeometry(emptyList(), 16, 0, 0, 500)
        assertEquals(ChatScrollTarget(0, 0), empty.target(0.7f))
        assertEquals(0f, empty.fraction(0, 0), 0f)
        val short = ChatScrollGeometry(listOf(100f, 100f), 0, 0, 0, 500)
        assertEquals(0.0, short.scrollExtent, 0.0)
        assertEquals(0f, short.fraction(1, 20), 0f)
    }

    @Test fun invalidEstimatedRowsNeverCreateNaNProgress() {
        val g = ChatScrollGeometry(listOf(Float.NaN, Float.POSITIVE_INFINITY, -200f, 800f), 16, 0, 0, 300)
        assertTrue(g.scrollExtent.isFinite())
        assertTrue(g.fraction(2, 10).isFinite())
        assertTrue(g.target(0.5f).index in 0..3)
    }

    @Test fun trackPositionClampsAndIdleTimeoutIsFiveSeconds() {
        assertEquals(5000L, QuickLocatePolicy.IdleMillis)
        assertEquals(0f, QuickLocatePolicy.trackFraction(-100f, 440f, 28f), 0f)
        assertEquals(0.5f, QuickLocatePolicy.trackFraction(220f, 440f, 28f), 0f)
        assertEquals(1f, QuickLocatePolicy.trackFraction(900f, 440f, 28f), 0f)
        assertEquals(0f, QuickLocatePolicy.trackFraction(Float.NaN, 440f, 28f), 0f)
        assertEquals(0f, QuickLocatePolicy.trackFraction(20f, 20f, 28f), 0f)
    }

    @Test fun wideImageFitsBothHorizontalEdgesWithBlackSpaceAbove() {
        val fit = ImagePreviewGeometry.fitRect(750f, 1668f, 1600f, 900f)
        assertEquals(0f, fit.left, 0f)
        assertEquals(750f, fit.right, 0f)
        assertEquals(421.875f, fit.height, 0.01f)
        assertTrue(fit.top > 500f)
    }

    @Test fun tallImageFitsFullWindowIncludingStatusArea() {
        val fit = ImagePreviewGeometry.fitRect(750f, 1668f, 600f, 1600f)
        assertEquals(0f, fit.top, 0f)
        assertEquals(1668f, fit.bottom, 0f)
        assertTrue(fit.left > 0f)
        val landscape = ImagePreviewGeometry.fitRect(1668f, 750f, 600f, 1600f)
        assertEquals(750f, landscape.height, 0.001f)
    }

    @Test fun baseScaleNeverAllowsHorizontalOrVerticalCanvasPan() {
        val fit = Rect(0f, 400f, 750f, 1200f)
        for (s in listOf(0.9f, 1f, 1.01f, Float.NaN))
            assertEquals(Offset.Zero, ImagePreviewGeometry.clampPan(Offset(999f, -999f), s, 750f, 1668f, fit))
    }

    @Test fun zoomedPanCannotExposeEmptyEdgesOrMoveAnAxisThatStillFits() {
        val fit = Rect(0f, 400f, 750f, 1200f)
        assertEquals(Offset(375f, 0f), ImagePreviewGeometry.clampPan(Offset(999f, 999f), 2f, 750f, 1668f, fit))
        assertEquals(Offset(-750f, -366f), ImagePreviewGeometry.clampPan(Offset(-9999f, -9999f), 3f, 750f, 1668f, fit))
        assertEquals(Offset.Zero, ImagePreviewGeometry.clampPan(Offset(Float.NaN, Float.POSITIVE_INFINITY), 2f, 750f, 1668f, fit))
    }

    @Test fun verticalDismissWorksBothDirectionsButSmallDragAndReverseFlingReturn() {
        assertTrue(ImagePreviewGeometry.shouldDismiss(220f, 0f, 1668f, 2f))
        assertTrue(ImagePreviewGeometry.shouldDismiss(-220f, 0f, 1668f, 2f))
        assertFalse(ImagePreviewGeometry.shouldDismiss(30f, 0f, 1668f, 2f))
        assertTrue(ImagePreviewGeometry.shouldDismiss(80f, 3000f, 1668f, 2f))
        assertFalse(ImagePreviewGeometry.shouldDismiss(80f, -3000f, 1668f, 2f))
        assertFalse(ImagePreviewGeometry.shouldDismiss(10f, 3000f, 1668f, 2f))
    }

    @Test fun feedbackAndIconGuidanceAreLocalizedAndMatchRealSubmission() {
        // 诚实口径跟着行为走（1.0.95 接了真后端）：文案必须明说「会发送给开发者」——
        // 反过来也不许谎称（这条测试的前身钉的是「不许谎称已发送」，行为反转后钉法跟着反转）
        for (s in listOf(ZhCN, ZhTW, En)) {
            assertTrue(s.feedbackTitle.isNotBlank())
            assertTrue(s.feedbackSendFailed.isNotBlank())
            assertTrue(s.feedbackSent.isNotBlank())
            assertTrue(s.appIconRestart.isNotBlank())
            assertTrue(s.quickLocate.isNotBlank())
        }
        assertTrue(ZhCN.feedbackDescription.contains("无需登录"))
        assertTrue(ZhCN.feedbackDescription.contains("发送给开发者"))
        assertTrue(ZhCN.feedbackSendFailed.contains("内容已保留"))
        assertTrue(ZhCN.appIconRestart.contains("重启"))
    }
}
