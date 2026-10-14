package com.freechat.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * AGSL source for the directional exit trail. Kept free of Android types so JVM
 * tests can pin the shader discipline (multi-tap blur / no fwidth / restrained lift).
 */
internal object MessageExitShaderSource {
    /**
     * Directional motion trail: 5 taps along the travel axis (trail sits behind),
     * weight falls off with distance. Tail gets a tiny brightness lift — restrained,
     * never particles or rainbow. No fwidth(): AGSL has no derivatives here.
     */
    val Agsl = """
        uniform shader content;
        uniform float2 size;
        uniform float progress;
        uniform float dir;
        half4 main(float2 point) {
            float blur = 22.0 * progress;
            half4 acc = half4(0.0);
            float wsum = 0.0;
            for (int i = 0; i < 5; i++) {
                float t = float(i) / 4.0;
                float offset = -dir * blur * t;
                float2 src = float2(clamp(point.x + offset, 0.0, max(size.x - 1.0, 0.0)), point.y);
                float w = 1.0 - 0.55 * t;
                acc += content.eval(src) * half(w);
                wsum += w;
            }
            half4 c = acc / half(max(wsum, 0.001));
            float lift = 0.06 * progress;
            c.rgb = c.rgb * half(1.0 + lift);
            return c;
        }
    """.trimIndent()
}

/** Directional smear on a row while it slides off; translation+fade alone is the API<33 fallback. */
@Composable
internal fun Modifier.messageExit(progress: State<Float>, direction: Float): Modifier {
    val trail = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { MessageExitShader() }
            .onFailure { Log.w("FreeChatShader", "Message exit uses translation+fade fallback", it) }
            .getOrNull() else null
    }
    return graphicsLayer {
        val p = progress.value
        translationX = p * size.width * direction
        alpha = com.freechat.ui.animation.MotionPolicy.exitAlpha(p)
        renderEffect = if (Build.VERSION.SDK_INT >= 33 && p > 0.001f && p < 0.999f) {
            trail?.apply(size.width, size.height, p, direction)
        } else null
    }
}

@RequiresApi(33)
private class MessageExitShader {
    private val shader = RuntimeShader(MessageExitShaderSource.Agsl)
    fun apply(width: Float, height: Float, progress: Float, direction: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("progress", progress)
        shader.setFloatUniform("dir", direction)
        // Image filters snapshot uniforms. Build after updates, only during the short travel animation.
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
