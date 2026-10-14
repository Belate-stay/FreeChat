package com.freechat.ui.components

import org.junit.Assert.*
import org.junit.Test

class ImageRevealPolicyTest {
    @Test fun revealHasNoBlankFrameAndDotsJoinBeforeDisappearing() {
        assertEquals(0f, ImageRevealPolicy.imageAlpha(0f), 0f)
        assertEquals(1f, ImageRevealPolicy.dotAlpha(0f), 0f)
        assertEquals(1f, ImageRevealPolicy.imageAlpha(1f), 0f)
        assertEquals(0f, ImageRevealPolicy.dotAlpha(1f), 0f)
        assertTrue(ImageRevealPolicy.radius(.8f, .12f) > .5f)
        for (step in 0..100) {
            val progress = step / 100f
            assertTrue(ImageRevealPolicy.imageAlpha(progress) + ImageRevealPolicy.dotAlpha(progress) >= .95f)
        }
    }

    @Test fun generationSlotMorphsFromSquareToTheResultFrameOnReveal() {
        // 占位图定死 1:1；图片到达后长成**实测画幅**——所有路径同一条过渡（含场景图/历史图），
        // 比例只认模型返回图的像素，不猜提示词。
        assertEquals(1f, ImageRevealPolicy.slotRatio(0f, .5f), 0f)
        assertEquals(.5f, ImageRevealPolicy.slotRatio(1f, .5f), 0f)
        assertEquals(2f, ImageRevealPolicy.slotRatio(1f, 2f), 0f)
        assertEquals(1f, ImageRevealPolicy.slotRatio(1f, 1f), 0f)
        var previous = 1f
        for (step in 0..100) {
            val slot = ImageRevealPolicy.slotRatio(step / 100f, .5f)
            assertTrue("portrait morph is monotone inside [target, 1]", slot in .5f..1f && slot <= previous)
            previous = slot
        }
    }

    @Test fun resultFrameIsFullBleedOnceTheSlotReachesItsAspect() {
        // At the result aspect the fit rect IS the slot: the info bar hugs the image, no blank bands.
        assertEquals(1f to 1f, ImageRevealPolicy.fitIn(400f, 500f, .8f))
        assertEquals(1f to 1f, ImageRevealPolicy.fitIn(800f, 400f, 2f))
        // Mid-morph the fit rect keeps the image's true aspect (no distortion while the slot unfolds).
        val (w, h) = ImageRevealPolicy.fitIn(400f, 400f, .8f)
        assertEquals(.8f, w * 400f / (h * 400f), 0.001f)
        val full = ImageRevealPolicy.frame(1f, 400f, 500f, .8f, 12f)
        assertEquals(200f, full.halfWidth, 0f)
        assertEquals(250f, full.halfHeight, 0f)
    }

    @Test fun roundedMaskConvergesToPortraitAndLandscapeWithinTheGivenSlot() {
        assertEquals(.8f to 1f, ImageRevealPolicy.fit(.8f))
        assertEquals(1f to .5f, ImageRevealPolicy.fit(2f))
        val start = ImageRevealPolicy.frame(0f, 400f, 400f, .8f, 12f)
        val end = ImageRevealPolicy.frame(1f, 400f, 400f, .8f, 12f)
        assertEquals(200f, start.halfWidth, 0f)
        assertEquals(160f, end.halfWidth, 0f)
        assertEquals(200f, end.halfHeight, 0f)
        assertEquals(12f, end.corner, 0f)
        assertEquals(0f, end.opacity(40f, 0f, 400f, 400f), 0f)
        assertEquals(1f, end.opacity(200f, 200f, 400f, 400f), 0f)
        for (step in 0..100) {
            val shape = ImageRevealPolicy.frame(step / 100f, 400f, 400f, .8f, 12f)
            assertTrue(shape.halfWidth in 160f..200f)
            assertTrue(shape.feather > 0f)
            assertEquals(1f, shape.opacity(200f, 200f, 400f, 400f), 0f)
        }
    }

    @Test fun transitionBelongsToTheGenerationMomentOnly() {
        // 亲历生成完成：从占位开演。历史图/回看：画幅已知直接终态、画幅未知才落位一次。
        assertEquals(0f, ImageRevealPolicy.revealStart(fromGeneration = true, aspectKnown = true), 0f)
        assertEquals(0f, ImageRevealPolicy.revealStart(fromGeneration = true, aspectKnown = false), 0f)
        assertEquals(1f, ImageRevealPolicy.revealStart(fromGeneration = false, aspectKnown = true), 0f)
        assertEquals(0f, ImageRevealPolicy.revealStart(fromGeneration = false, aspectKnown = false), 0f)
    }

    @Test fun aspectLedgerRemembersEachFrameOnlyOnce() {
        val key = "img:ledger-test"
        assertNull(ImageRevealLedger.aspectOf(key))
        ImageRevealLedger.record(key, 2000, 1000)
        assertEquals(2f, ImageRevealLedger.aspectOf(key)!!, 0.001f)
        ImageRevealLedger.record(key, 0, 0)   // 非法尺寸不写入、不覆盖
        ImageRevealLedger.record(key, 10, -1)
        assertEquals(2f, ImageRevealLedger.aspectOf(key)!!, 0.001f)
        assertNull(ImageRevealLedger.aspectOf("img:never-seen"))
    }
}
