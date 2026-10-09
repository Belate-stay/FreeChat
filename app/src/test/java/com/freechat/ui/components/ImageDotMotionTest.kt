package com.freechat.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ImageDotMotionTest {
    private val points = (0 until ImageDotMotion.Columns).flatMap { row ->
        (0 until ImageDotMotion.Columns).map { column ->
            (column + .5f) / ImageDotMotion.Columns to (row + .5f) / ImageDotMotion.Columns
        }
    }

    @Test fun firstFrameIsAUniformGridOfSmallDots() {
        val first = ImageDotMotion.frame(0f)
        assertEquals(20, ImageDotMotion.Columns)
        for ((x, y) in points) {
            assertEquals(ImageDotMotion.MinRadius, first.radius(x, y), 0f)
            assertEquals(0f, first.strength(x, y), 0f)
            assertEquals(0f, first.sheen(x, y), 0f)
        }
    }

    @Test fun laterFramesHaveCoherentRegionsInsteadOfUniformBreathing() {
        for (seconds in listOf(3f, 7f, 12f, 19f)) {
            val frame = ImageDotMotion.frame(seconds)
            val radii = points.map { (x, y) -> frame.radius(x, y) }
            assertTrue("At $seconds seconds, a local region should visibly expand",
                radii.max() > ImageDotMotion.MinRadius + .06f)
            assertTrue("At $seconds seconds, different regions must have different sizes",
                radii.max() - radii.min() > .06f)
        }
    }

    @Test fun neighbouringDotsGrowTogetherWithoutRandomCellJumps() {
        val spacing = 1f / ImageDotMotion.Columns
        for (seconds in listOf(2f, 8f, 16f, 35f)) {
            val frame = ImageDotMotion.frame(seconds)
            for ((x, y) in points) {
                val radius = frame.radius(x, y)
                assertTrue(abs(radius - frame.radius(x + spacing, y)) < .06f)
                assertTrue(abs(radius - frame.radius(x, y + spacing)) < .06f)
            }
        }
    }

    @Test fun expandedRegionLaterContracts() {
        val trajectories = listOf(.25f to .25f, .5f to .5f, .75f to .75f).map { (x, y) ->
            (2..80).map { ImageDotMotion.frame(it.toFloat()).radius(x, y) }
        }
        assertTrue("The wave must grow and recede, rather than accumulate progress",
            trajectories.any { radii ->
                val peak = radii.indices.maxBy { radii[it] }
                radii[peak] - radii.first() > .04f &&
                    radii.drop(peak + 1).any { radii[peak] - it > .04f }
            })
    }

    @Test fun motionIsSmoothAcrossRenderingFrames() {
        for (step in 0..240) {
            val seconds = step * .25f
            val a = ImageDotMotion.frame(seconds)
            val b = ImageDotMotion.frame(seconds + .016f)
            for ((x, y) in points) {
                assertTrue("Dot radius jumped at $seconds", abs(a.radius(x, y) - b.radius(x, y)) < .004f)
                assertTrue("Gloss jumped at $seconds", abs(a.sheen(x, y) - b.sheen(x, y)) < .015f)
            }
        }
    }

    @Test fun dotsAndFeatheredEdgesStayBoundedDuringLongGeneration() {
        for (seconds in listOf(0f, .5f, 2f, 8f, 100f, 3600f)) {
            val frame = ImageDotMotion.frame(seconds)
            for ((x, y) in points) {
                assertTrue(frame.radius(x, y) in ImageDotMotion.MinRadius..ImageDotMotion.MaxRadius)
                assertTrue(frame.strength(x, y) in 0f..1f)
                assertTrue(frame.sheen(x, y) in 0f..1f)
                assertTrue(ImageDotMotion.edgeOpacity(x, y) in 0f..1f)
            }
        }
        assertEquals(0f, ImageDotMotion.edgeOpacity(0f, .5f), .00001f)
        assertEquals(0f, ImageDotMotion.edgeOpacity(1f, .5f), .00001f)
        assertEquals(0f, ImageDotMotion.edgeOpacity(0f, 0f), .00001f)
        assertEquals(0f, ImageDotMotion.edgeOpacity(1f, 1f), .00001f)
        assertTrue(ImageDotMotion.edgeOpacity(.04f, .5f) in .01f.. .99f)
        assertEquals(1f, ImageDotMotion.edgeOpacity(.5f, .5f), 0f)
        assertTrue(ImageDotMotion.edgeOpacity(.06f, .06f) < ImageDotMotion.edgeOpacity(.06f, .5f))
    }
}
