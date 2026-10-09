/*
 * Adapted from DotGridAgsl.kt in AndreFrelicot/paper-shaders-android.
 * Copyright 2026 Andre Frelicot. Licensed under the Apache License, Version 2.0.
 * Original algorithms and visual design are based on paper-design/shaders by Paper.
 * Modified for FreeChat: fixed circular cells, coherent breathing, gloss and feathered edges.
 * Full license and attribution: assets/licenses/paper-shaders-android/.
 */
package com.freechat.ui.components

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb

@RequiresApi(33)
internal class ImageDotShader {
    private val shader = RuntimeShader(Source)
    private val brush = ShaderBrush(shader)

    fun updatePalette(appearance: ImageLoadingAppearance) {
        shader.setColorUniform("baseColor", appearance.base.toArgb())
        shader.setColorUniform("dotColor", appearance.dot.toArgb())
        shader.setColorUniform("glossColor", appearance.gloss.toArgb())
    }

    fun draw(scope: DrawScope, frame: ImageDotMotion.Frame, cell: Float, originY: Float) {
        shader.setFloatUniform("resolution", scope.size.width, scope.size.height)
        shader.setFloatUniform("cellSize", cell)
        shader.setFloatUniform("originY", originY)
        shader.setFloatUniform("regionA", frame.ax, frame.ay, frame.aWeight)
        shader.setFloatUniform("regionB", frame.bx, frame.by, frame.bWeight)
        shader.setFloatUniform("regionC", frame.cx, frame.cy, frame.cWeight)
        shader.setFloatUniform("sheen", frame.sheenCenter, frame.ramp)
        shader.setFloatUniform("growthFloor", frame.growthFloor)
        with(scope) { drawRect(brush) }
    }

    private companion object {
        val Source = """
            uniform float2 resolution;
            uniform float cellSize;
            uniform float originY;
            uniform float3 regionA;
            uniform float3 regionB;
            uniform float3 regionC;
            uniform float2 sheen;
            uniform float growthFloor;
            layout(color) uniform half4 baseColor;
            layout(color) uniform half4 dotColor;
            layout(color) uniform half4 glossColor;

            float field(float2 p) {
                float a = (1.0 - smoothstep(0.0, 0.40, length(p - regionA.xy))) * regionA.z;
                float b = (1.0 - smoothstep(0.0, 0.44, length(p - regionB.xy))) * regionB.z;
                float c = (1.0 - smoothstep(0.0, 0.38, length(p - regionC.xy))) * regionC.z;
                return mix(growthFloor, 1.0, clamp(1.0 - (1.0 - a) * (1.0 - b) * (1.0 - c), 0.0, 1.0));
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / resolution;
                float2 q = abs(uv - 0.5) - 0.36;
                float distanceToEdge = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 0.14;
                float edgeOpacity = smoothstep(0.0, 0.08, -distanceToEdge);

                float2 grid = (fragCoord - float2(0.0, originY)) / cellSize;
                float2 center = ((floor(grid) + 0.5) * cellSize + float2(0.0, originY)) / resolution;
                float strength = field(center);
                float gloss = sheen.y * (1.0 - smoothstep(0.0, 0.23,
                    abs(dot(center, float2(0.72, 0.28)) - sheen.x)));

                // Circular cell/fract distance and antialiased mask adapted from DotGridAgsl.
                float2 local = (fract(grid) - 0.5) * cellSize;
                float radius = cellSize * mix(0.065, 0.24, strength);
                float mask = 1.0 - smoothstep(radius - 0.75, radius + 0.75, length(local));
                half4 ink = mix(dotColor, glossColor, 0.28 * strength + 0.42 * gloss);
                float dotAlpha = mask * ink.a * edgeOpacity;
                float baseAlpha = baseColor.a * edgeOpacity * (1.0 - dotAlpha);
                return half4(baseColor.rgb * baseAlpha + ink.rgb * dotAlpha, baseAlpha + dotAlpha);
            }
        """.trimIndent()
    }
}
