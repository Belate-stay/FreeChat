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
        val particles = ImageParticleMotion.create()
        val themes = listOf("light" to LightColors, "dark" to DarkColors)
        val snapshots = themes.map { (name, theme) ->
            val paint = ImageLoadingAppearance(theme)
            linkedMapOf("name" to name, "background" to theme.Background.toArgb(),
                "base" to paint.base.map { it.toArgb() }, "palette" to paint.palette.map { it.toArgb() },
                "caption" to paint.caption.toArgb(), "captionAlpha" to paint.captionAlpha,
                "mistOpacity" to paint.mistOpacity,
                "frames" to listOf(0f, 8f, 16f).map { time ->
                    linkedMapOf("time" to time,
                        "mist" to (0..2).map { index ->
                            val drift = particles[index * 13]
                            listOf(drift.x(time * .55f), drift.y(time * .55f), .66f + index * .06f)
                        },
                        "particles" to particles.mapIndexed { index, p ->
                            listOf(p.x(time), p.y(time), p.radius, p.tint,
                                p.alpha(time) * if (paint.dark) .86f else .72f, index % 5 == 0)
                        })
                })
        }
        val folder = File("build/qa/beta992/paint-reference").apply { mkdirs() }
        val output = File(folder, "paint.json").apply { writeText(Gson().toJson(snapshots)) }
        assertTrue(output.length() > 0)
        assertTrue(particles.any { it.x(0f) != it.x(16f) || it.y(0f) != it.y(16f) })
    }
}
