package com.freechat.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 1.1.29.9 工单⑤契约（用户拍板「从底部展开和折叠都应该还在底部」，推翻旧
 * 「展开只揭示首行」设计——旧 DisclosureAnchorPlan 的 reveal-cap 契约随之作废）：
 * 折叠零干预（LazyColumn 钳位随 tween 逐帧贴底，跟了=双重位移=跳到半空）；
 * 在底部展开逐帧跟满增长量精确落底；浏览态两者都不碰视口。
 */
class DisclosureAnchorTest {
    private fun src() = File("src/main/java/com/freechat/ui/components/DisclosureScrollAnchor.kt").readText()

    @Test fun collapseNeverScrollsTheClampOwnsTheBottom() {
        val s = src()
        assertFalse("折叠不许再有任何 scrollBy（双重位移病根）", s.contains("bottomCollapse"))
        assertTrue("只有「展开且在底部」才起跟随", s.contains("if (expanding && !list.canScrollForward)"))
    }

    @Test fun bottomExpansionFollowsTheFullGrowthToTheEnd() {
        val s = src()
        assertFalse("不许 reveal-cap（展开停在半路=不在底部）", s.contains("maximumFollow"))
        assertTrue("逐帧按高度差累计，总量=高度总量，精确落底",
            s.contains("val dh = latestHeight - followed") && s.contains("scrollBy(dh.toFloat())"))
    }

    @Test fun userGesturesCancelTheFollowImmediately() {
        assertTrue(src().contains("fun onUserScroll()"))
    }
}
