package com.freechat.ui.components

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb

/**
 * AGSL source for the Siri-style thinking orb. Kept free of Android types so JVM
 * tests can pin the shader discipline (snoise / dither / no fwidth / stable uniforms).
 *
 * 1.1.32 视觉重做（两轮）：一轮=五颗彩核光粒子+拖尾+深度穿行，底色压暗；二轮（用户返修）=
 * **去掉一切圆形框**（球缘/玻璃环全撤，底色指数羽化成柔雾）、光晕加重加糊（宽指数裙边）、
 * 粒子自由越界不圈禁、输出=发光体合成（亮处自然实、暗处自然虚）。
 */
internal object SiriOrbShaderSource {
    /**
     * Ashima 2D simplex noise (widely published public implementation) plus a
     * banding dither — required because AGSL has no fwidth(), so we cannot use
     * derivative-based antialiasing and soft radial gradients will band without it.
     */
    val Agsl = """
        vec3 permute(vec3 x) { return mod(((x * 34.0) + 1.0) * x, 289.0); }
        float snoise(vec2 v) {
          const vec4 C = vec4(0.211324865405187, 0.366025403784439,
            -0.577350269189626, 0.024390243902439);
          vec2 i = floor(v + dot(v, C.yy));
          vec2 x0 = v - i + dot(i, C.xx);
          vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
          vec4 x12 = x0.xyxy + C.xxzz;
          x12.xy -= i1;
          i = mod(i, 289.0);
          vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0))
            + i.x + vec3(0.0, i1.x, 1.0));
          vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy),
              dot(x12.zw, x12.zw)), 0.0);
          m = m * m;
          m = m * m;
          vec3 x = 2.0 * fract(p * C.www) - 1.0;
          vec3 h = abs(x) - 0.5;
          vec3 ox = floor(x + 0.5);
          vec3 a0 = x - ox;
          m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
          vec3 g;
          g.x = a0.x * x0.x + h.x * x0.y;
          g.yz = a0.yz * x12.xz + h.yz * x12.yw;
          return 130.0 * dot(m, g);
        }

        float bandingNoise(vec2 fragCoord) {
          return 1. / 256. * (fract(sin(dot(.014 * fragCoord, vec2(12.9898, 78.233))) * 43758.5453123) - .5);
        }

        uniform float2 u_size;
        uniform float u_time;
        uniform float u_dark;
        layout(color) uniform half4 u_primary;
        layout(color) uniform half4 u_glowA;
        layout(color) uniform half4 u_glowB;
        layout(color) uniform half4 u_glowC;
        uniform float4 u_p0;
        uniform float4 u_p1;
        uniform float4 u_p2;
        uniform float4 u_p3;
        uniform float4 u_p4;
        uniform float4 u_tr0;
        uniform float4 u_tr1;
        uniform float4 u_tr2;
        uniform float4 u_tr3;
        uniform float4 u_tr4;
        layout(color) uniform half4 u_pc0;
        layout(color) uniform half4 u_pc1;
        layout(color) uniform half4 u_pc2;
        layout(color) uniform half4 u_pc3;
        layout(color) uniform half4 u_pc4;

        // 一颗光粒子：柔核 + 重而糊的彩色光晕（glow 主晕 + skirt 裙边双指数羽化，边缘无棱）。
        // 1.2.4 返修：高光压低（白核混合 0.7→0.3、核权 1.35→0.85）——白热过曝会把五彩糊成一片白。
        half3 particleLight(float2 p, float2 q, float pr, float inten, half3 col) {
          float d = length(p - q);
          float core = exp(-(d * d) / (pr * pr * 0.55));
          float glow = exp(-d / (pr * 2.4));
          float skirt = exp(-d / (pr * 5.5));
          half3 c = mix(col, half3(1.0, 0.99, 0.96), half(core * 0.3));
          return c * half((core * 0.85 + glow * 0.95 + skirt * 0.4) * inten);
        }

        half4 main(float2 fragCoord) {
          float2 center = u_size * 0.5;
          // 单位半径 < 画布半宽（0.34 而非 0.5）：给粒子越界与光晕羽化留出走完的空间。
          // 旧版 radius=半宽 + 宽衰减 + 发光体 alpha = 整块画布带透明度 → 露出方形画布边界
          // （用户实机报「浅色方片」的根因，测试钉死）。
          float unit = min(u_size.x, u_size.y) * 0.34;
          float2 p = (fragCoord - center) / unit;
          float r = length(p);
          float t = u_time;
          float glow = mix(0.72, 1.18, u_dark);
          // 画布级羽化蒙版：一切发光在画布边缘前收到零——绝不允许露出方形边界
          float mask = 1.0 - smoothstep(0.8, 1.4, r);

          // 柔雾底色：指数羽化到零——没有球缘没有圆框
          float feather = exp(-r * r * 2.2);
          float n1 = snoise(p * 1.7 + vec2(t * 0.11, t * 0.07));
          float n2 = snoise(p * 2.9 + vec2(-t * 0.13, t * 0.17) + vec2(2.3, 5.7));
          float nebula = clamp(0.5 + 0.34 * n1 + 0.22 * n2, 0.0, 1.0);
          float drift = snoise(p * 0.9 + vec2(t * 0.043, -t * 0.037) + vec2(1.7, 8.2));
          float swirl = 0.5 + 0.5 * sin(drift * 2.4 + t * 0.08);
          float swirl2 = 0.5 + 0.5 * sin(drift * 1.9 - t * 0.06 + 2.1);
          half3 neb = mix(mix(u_primary.rgb, u_glowA.rgb, half(swirl)),
            mix(u_glowB.rgb, u_glowC.rgb, half(swirl2)), half(nebula));
          half3 rgb = neb * half((0.10 + 0.20 * nebula) * glow * feather);

          // —— 五颗自由环绕光粒子（可越出单位圈）：重光晕+双点拖尾，加色 ——
          // 1.2.4 返修：深色不再过亮（1.2 曾把深色糊成一片白）；叠加后按最大通道软压缩，
          // 保色相——多粒子同区时亮度压下去、五颜六色照常透出来
          float pScale = mix(0.90, 0.95, u_dark);
          half3 pts = particleLight(p, u_p0.xy, u_p0.z, u_p0.w, u_pc0.rgb);
          pts += particleLight(p, u_tr0.xy, u_p0.z * 0.72, u_p0.w * 0.42, u_pc0.rgb);
          pts += particleLight(p, u_tr0.zw, u_p0.z * 0.52, u_p0.w * 0.16, u_pc0.rgb);
          pts += particleLight(p, u_p1.xy, u_p1.z, u_p1.w, u_pc1.rgb);
          pts += particleLight(p, u_tr1.xy, u_p1.z * 0.72, u_p1.w * 0.42, u_pc1.rgb);
          pts += particleLight(p, u_tr1.zw, u_p1.z * 0.52, u_p1.w * 0.16, u_pc1.rgb);
          pts += particleLight(p, u_p2.xy, u_p2.z, u_p2.w, u_pc2.rgb);
          pts += particleLight(p, u_tr2.xy, u_p2.z * 0.72, u_p2.w * 0.42, u_pc2.rgb);
          pts += particleLight(p, u_tr2.zw, u_p2.z * 0.52, u_p2.w * 0.16, u_pc2.rgb);
          pts += particleLight(p, u_p3.xy, u_p3.z, u_p3.w, u_pc3.rgb);
          pts += particleLight(p, u_tr3.xy, u_p3.z * 0.72, u_p3.w * 0.42, u_pc3.rgb);
          pts += particleLight(p, u_tr3.zw, u_p3.z * 0.52, u_p3.w * 0.16, u_pc3.rgb);
          pts += particleLight(p, u_p4.xy, u_p4.z, u_p4.w, u_pc4.rgb);
          pts += particleLight(p, u_tr4.xy, u_p4.z * 0.72, u_p4.w * 0.42, u_pc4.rgb);
          pts += particleLight(p, u_tr4.zw, u_p4.z * 0.52, u_p4.w * 0.16, u_pc4.rgb);
          // 保色软压缩：按最大通道整体缩（色相不动），叠加过曝处压亮度不糊白
          float pk = max(pts.r, max(pts.g, pts.b));
          pts *= half(1.0 / (1.0 + 0.5 * pk));

          // 整体外圈：多彩呼吸光晕（羽化到零，不构成环）
          float halo = exp(-r * r * 1.05);
          half3 haloTint = mix(mix(u_primary.rgb, u_glowA.rgb, half(swirl)),
            mix(u_glowB.rgb, u_glowC.rgb, half(swirl2)), half(0.5 + 0.5 * sin(t * 0.13)));
          half3 light = (rgb + pts * half(pScale) + haloTint * half(halo * 0.30 * glow)) * half(mask);

          // 发光体合成：亮处自然实、暗处自然虚——边缘永远是渐隐不是轮廓
          float lum = max(light.r, max(light.g, light.b));
          float a = clamp(lum * 0.82, 0.0, 1.0);
          half3 outRgb = light;
          float outA = a;

          float d = bandingNoise(fragCoord);
          outRgb += half3(half(d));
          outA = clamp(outA + d * 0.5, 0.0, 1.0);
          return half4(outRgb, half(outA));
        }
    """.trimIndent()
}

@RequiresApi(33)
internal class SiriOrbShader {
    private val shader = RuntimeShader(SiriOrbShaderSource.Agsl)
    private val brush = ShaderBrush(shader)

    /**
     * [orbit] 每粒子 8 float（[SiriOrbMotion.PACK_STRIDE]）：头 x,y,半径,强度 + 尾1 x,y + 尾2 x,y，
     * 归一化球坐标（圆心 0、半径 1，粒子可越界），由 [SiriOrbMotion.packParticle] 填好。
     */
    fun draw(
        scope: DrawScope, time: Float, isDark: Boolean,
        primary: Color, glowA: Color, glowB: Color, glowC: Color, orbit: FloatArray
    ) {
        shader.setFloatUniform("u_size", scope.size.width, scope.size.height)
        shader.setFloatUniform("u_time", time)
        shader.setFloatUniform("u_dark", if (isDark) 1f else 0f)
        shader.setColorUniform("u_primary", primary.toArgb())
        shader.setColorUniform("u_glowA", glowA.toArgb())
        shader.setColorUniform("u_glowB", glowB.toArgb())
        shader.setColorUniform("u_glowC", glowC.toArgb())
        for (i in 0..4) {
            val o = i * SiriOrbMotion.PACK_STRIDE
            shader.setFloatUniform("u_p$i", orbit[o], orbit[o + 1], orbit[o + 2], orbit[o + 3])
            shader.setFloatUniform("u_tr$i", orbit[o + 4], orbit[o + 5], orbit[o + 6], orbit[o + 7])
            shader.setColorUniform("u_pc$i", SiriOrbMotion.particleColors[i].toArgb())
        }
        with(scope) { drawRect(brush) }
    }
}
