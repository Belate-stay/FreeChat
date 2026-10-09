package com.freechat.ui.components

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ImageDotWiringTest {
    @Test fun generationPlaceholderDoesNotExposeAnImageFrame() {
        val source = File("src/main/java/com/freechat/ui/components/ImageGenerationPlaceholder.kt").readText()
        val generation = source.substringAfter("fun ImageGenerationPlaceholder(")
            .substringBefore("internal fun ImageGenerationParticles(")

        assertFalse("The generating image must blend into the chat instead of clipping to a frame",
            generation.contains(".clip("))
        assertFalse("No visible border around the generating image", generation.contains(".border("))
        assertFalse("No elevated frame around the generating image", generation.contains(".shadow("))
    }
}
