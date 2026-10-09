package com.freechat.ui.components

import androidx.compose.ui.graphics.toArgb
import com.freechat.ui.theme.LightColors
import com.freechat.ui.theme.DarkColors
import com.google.gson.Gson
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Export the production paint inputs for software visual QA, not an Android screenshot. */
class ImageLoadingRenderTest {
    @Test fun sharedPaintTokensProduceLightAndDarkReferencesAtDifferentTimes() {
        val themes = listOf("light" to LightColors, "dark" to DarkColors)
        val snapshots = themes.map { (name, theme) ->
            val paint = ImageLoadingAppearance(theme)
            linkedMapOf("name" to name, "background" to theme.Background.toArgb(),
                "base" to paint.base.toArgb(), "dot" to paint.dot.toArgb(), "gloss" to paint.gloss.toArgb(),
                "columns" to ImageDotMotion.Columns,
                "frames" to listOf(0f, 8f, 16f).map { time ->
                    val frame = ImageDotMotion.frame(time)
                    linkedMapOf("time" to time,
                        "dots" to (0 until ImageDotMotion.Columns).flatMap { row ->
                            (0 until ImageDotMotion.Columns).map { column ->
                                val x = (column + .5f) / ImageDotMotion.Columns
                                val y = (row + .5f) / ImageDotMotion.Columns
                                listOf(x, y, frame.radius(x, y), frame.strength(x, y), frame.sheen(x, y),
                                    ImageDotMotion.edgeOpacity(x, y))
                            }
                        })
                })
        }
        val folder = File("build/qa/beta1108/paint-reference").apply { mkdirs() }
        val output = File(folder, "paint.json").apply { writeText(Gson().toJson(snapshots)) }
        assertTrue(output.length() > 0)
        assertTrue(ImageDotMotion.frame(16f).radius(.5f, .5f) > ImageDotMotion.MinRadius)
    }
}
