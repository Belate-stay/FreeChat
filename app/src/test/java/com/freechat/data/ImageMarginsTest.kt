package com.freechat.data

import org.junit.Assert.*
import org.junit.Test

class ImageMarginsTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val content = 0xFF336699.toInt()

    /** 造一张图：fill 返回 (x, y) 处的像素。 */
    private fun image(width: Int, height: Int, fill: (Int, Int) -> Int): IntArray =
        IntArray(width * height) { i -> fill(i % width, i / width) }

    @Test fun trimsTopAndBottomPaddingOfLandscapeContent() {
        // 图 #3 的几何：5:3 内容（300x180）垫进 300x300 方形画布，上下各 60px 纯白。
        val px = image(300, 300) { _, y -> if (y < 60 || y >= 240) white else content }
        val trim = ImageMargins.trim(300, 300, px, 5f / 3f)
        assertEquals(ImageMargins.Trim(0, 60, 0, 60), trim)
    }

    @Test fun trimsLeftAndRightPaddingOfPortraitContent() {
        // 图 #1 的几何：3:4 内容（225x300）垫进 300x300，左右各 37~38px 纯白。
        val px = image(300, 300) { x, _ -> if (x < 37 || x >= 263) white else content }
        val trim = ImageMargins.trim(300, 300, px, 3f / 4f)
        assertEquals(ImageMargins.Trim(37, 0, 37, 0), trim)
    }

    @Test fun blackLetterboxBarsTrimToo() {
        val px = image(300, 300) { _, y -> if (y < 60 || y >= 240) black else content }
        assertEquals(ImageMargins.Trim(0, 60, 0, 60), ImageMargins.trim(300, 300, px, 5f / 3f))
    }

    @Test fun aFrameOnAllFourSidesIsDesignNotPadding() {
        // 相框/留白海报：四边都是纯白——不动。
        val px = image(300, 300) { x, y -> if (x < 20 || x >= 280 || y < 20 || y >= 280) white else content }
        assertNull(ImageMargins.trim(300, 300, px, 1f))
    }

    @Test fun aSingleUniformEdgeIsContentNotPadding() {
        // 天空/纸面：只有上边纯白——不动。
        val px = image(300, 300) { _, y -> if (y < 60) white else content }
        assertNull(ImageMargins.trim(300, 300, px, 5f / 3f))
    }

    @Test fun texturedEdgesDoNotCountAsBars() {
        // 逐行变化的内容（真实画面）不裁。
        val px = image(300, 300) { x, y -> if (x > 200 && y > 200) white else content }
        assertNull(ImageMargins.trim(300, 300, px, 5f / 3f))
    }

    @Test fun trimmingMustMoveCloserToTheRequestedAspect() {
        // 目标就是当前画幅——裁了反而更远，视为画面自带构图，不动。
        val px = image(300, 300) { _, y -> if (y < 60 || y >= 240) white else content }
        assertNull(ImageMargins.trim(300, 300, px, 1f))
        // 目标 5:3 → 裁完 300x180 正中，动手。
        assertNotNull(ImageMargins.trim(300, 300, px, 5f / 3f))
    }

    @Test fun noTargetAspectStillTrimsUniformPadding() {
        // 场景图等未指定比例的生成也一样不能带垫边。
        val px = image(300, 300) { _, y -> if (y < 60 || y >= 240) white else content }
        assertEquals(ImageMargins.Trim(0, 60, 0, 60), ImageMargins.trim(300, 300, px, null))
    }

    @Test fun mixedPolarityRowsAreNotBars() {
        // 黑白混排的边条不是纯色垫边。
        val px = image(300, 300) { x, y -> if (y < 60) (if (x % 2 == 0) white else black) else content }
        assertNull(ImageMargins.trim(300, 300, px, 5f / 3f))
    }
}
