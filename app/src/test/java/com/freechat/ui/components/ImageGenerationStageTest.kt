package com.freechat.ui.components

import com.freechat.data.GenerationPhase
import org.junit.Assert.*
import org.junit.Test

class ImageGenerationStageTest {
    @Test fun supportedClientStagesIncreaseTheDotFloorNotAClaimedPercentage() {
        fun growth(phase: GenerationPhase) = ImageDotMotion.growthFor(phase)
        assertEquals(0f, growth(GenerationPhase.IDLE), 0f)
        assertEquals(0f, growth(GenerationPhase.IMAGE_CONTEXT), 0f)
        assertTrue(growth(GenerationPhase.IMAGE_CONNECTING) <= growth(GenerationPhase.IMAGE_GENERATING))
        assertTrue(growth(GenerationPhase.IMAGE_GENERATING) < growth(GenerationPhase.IMAGE_RECEIVING))
    }

    @Test fun receivedResultsRaiseTheBaselineWhileKeepingLocalWavesAndBoundedDots() {
        val floor = ImageDotMotion.growthFor(GenerationPhase.IMAGE_RECEIVING)
        val early = ImageDotMotion.frame(5f)
        val receiving = ImageDotMotion.frame(5f, floor)
        val grid = (0..19).flatMap { row -> (0..19).map { col -> (col + .5f) / 20 to (row + .5f) / 20 } }
        val strengths = grid.map { (x, y) ->
            assertTrue(receiving.radius(x, y) >= early.radius(x, y))
            assertTrue(receiving.radius(x, y) <= ImageDotMotion.MaxRadius)
            receiving.strength(x, y)
        }
        assertTrue(strengths.max() - strengths.min() > .05f)
    }

    @Test fun unsupportedProvidersDoNotInventProgressFromElapsedTime() {
        val file = java.io.File("src/main/java/com/freechat/ui/components/ImageGenerationPlaceholder.kt").readText()
        assertTrue("Rendering must receive the actual request phase", file.contains("phase: GenerationPhase"))
        assertFalse(file.contains("estimatedDuration"))
        assertFalse(file.contains("progressPercent"))
    }

    @Test fun pagerIdentityIsASaveableStringAndKeepsRepeatedSourcesDistinct() {
        val source = java.io.File("src/main/java/com/freechat/ui/components/FullScreenImagePreview.kt").readText()
        assertTrue(source.contains("key = { images[it].key }"))
        assertNotEquals(PreviewImage("first", "same.png").key, PreviewImage("second", "same.png").key)
        assertEquals("first:same.png", PreviewImage("first", "same.png").key)
    }
}
