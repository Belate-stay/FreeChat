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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/** Deform only the composer layer during travel; the microphone has its own, fixed layer. */
@Composable
internal fun Modifier.voiceDroplet(progress: State<Float>, microphone: State<Offset>): Modifier {
    val origin = remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    val liquid = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { DropletShader() }
            .onFailure { Log.w("FreeChatShader", "Voice droplet uses scale fallback", it) }.getOrNull() else null
    }
    return onGloballyPositioned { origin.value = it.positionInWindow() }.graphicsLayer {
        val p = progress.value
        val pose = VoiceInputMotion.pose(p)
        val anchor = microphone.value - origin.value
        transformOrigin = TransformOrigin(
            if (size.width > 0) anchor.x / size.width else 1f,
            if (size.height > 0) anchor.y / size.height else .5f
        )
        scaleX = pose.scaleX
        scaleY = pose.scaleY
        alpha = pose.alpha
        renderEffect = if (Build.VERSION.SDK_INT >= 33 && p > .001f && p < .999f) {
            liquid?.apply(size.width, size.height, p, anchor.y)
        } else null
    }
}

@RequiresApi(33)
private class DropletShader {
    private val shader = RuntimeShader("""
        uniform shader content;
        uniform float2 size;
        uniform float progress;
        uniform float anchorY;
        half4 main(float2 point) {
            float x = point.x / max(size.x, 1.0);
            float squeeze = 1.0 - 0.68 * sin(3.14159265 * progress) *
                (1.0 - smoothstep(0.15, 1.0, x));
            float2 source = float2(point.x, anchorY + (point.y - anchorY) / squeeze);
            float mask = step(0.0, source.y) * step(source.y, size.y);
            return content.eval(source) * half(mask);
        }
    """.trimIndent())
    fun apply(width: Float, height: Float, progress: Float, anchorY: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("progress", progress)
        shader.setFloatUniform("anchorY", anchorY)
        // Image filters snapshot uniforms. Build after updates, only during the short travel animation.
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
