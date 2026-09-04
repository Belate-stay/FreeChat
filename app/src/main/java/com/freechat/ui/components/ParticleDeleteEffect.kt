package com.freechat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/**
 * MIUI/HyperOS 风格粒子消散效果。
 *
 * 从目标 [originRect] 位置生成 ~40 个粒子向外飞散 + 重力下落。
 * 返回 shakeX State — 父组件读它做水平震动。
 */
@Composable
fun ParticleDeleteEffect(
    originRect: Rect,
    particleColor: Color,
    onFinished: () -> Unit
): State<Float> {
    val shakeX = remember { Animatable(0f) }
    val showCanvas = remember { mutableStateOf(true) }

    // 粒子状态列表
    var particles by remember {
        val rng = Random(System.currentTimeMillis())
        val cx = originRect.center.x
        val cy = originRect.center.y
        val sw = originRect.width.coerceAtLeast(60f)
        val sh = originRect.height.coerceAtLeast(60f)
        mutableStateOf(
            Array(45) {
                ParticleState(
                    x = cx + (rng.nextFloat() - 0.5f) * sw * 1.5f,
                    y = cy + (rng.nextFloat() - 0.5f) * sh * 1.5f,
                    vx = (rng.nextFloat() - 0.5f) * 1000f,
                    vy = -rng.nextFloat() * 700f - 250f,
                    radius = 2f + rng.nextFloat() * 5f,
                    alpha = 1f,
                    color = Color(
                        red = (particleColor.red + (rng.nextFloat() - 0.5f) * 0.3f).coerceIn(0f, 1f),
                        green = (particleColor.green + (rng.nextFloat() - 0.5f) * 0.3f).coerceIn(0f, 1f),
                        blue = (particleColor.blue + (rng.nextFloat() - 0.5f) * 0.3f).coerceIn(0f, 1f),
                    ),
                    life = 0.5f + rng.nextFloat() * 0.5f
                )
            }
        )
    }

    val gravity = 1300f
    val friction = 0.96f
    val totalDuration = 0.85f

    // 帧循环 — 更新粒子物理
    LaunchedEffect(Unit) {
        var startNanos = 0L
        var lastFrame = 0L
        var running = true
        withFrameNanos { nanos ->
            startNanos = nanos
            lastFrame = nanos
        }
        while (running) {
            withFrameNanos { nanos ->
                val dt = ((nanos - lastFrame) / 1_000_000_000f).coerceIn(0.005f, 0.05f)
                lastFrame = nanos
                val elapsed = (nanos - startNanos) / 1_000_000_000f

                particles = particles.map { p ->
                    val age = elapsed.coerceAtMost(totalDuration)
                    val alive = ((p.life - age / totalDuration) / p.life).coerceIn(0f, 1f)
                    p.copy(
                        x = p.x + p.vx * dt,
                        y = p.y + p.vy * dt,
                        vx = p.vx * friction,
                        vy = p.vy + gravity * dt,
                        alpha = alive,
                        radius = p.radius * (1f - age / totalDuration * 0.55f).coerceAtLeast(0.15f)
                    )
                }.toTypedArray()

                if (elapsed > totalDuration + 0.15f) {
                    showCanvas.value = false
                    onFinished()
                    running = false
                    return@withFrameNanos
                }
            }
        }
    }

    // 震动动画
    LaunchedEffect(Unit) {
        val intensity = 14f
        shakeX.animateTo(intensity, tween(85, easing = LinearEasing))
        shakeX.animateTo(-intensity * 0.75f, tween(70, easing = LinearEasing))
        shakeX.animateTo(intensity * 0.45f, tween(60, easing = LinearEasing))
        shakeX.animateTo(-intensity * 0.25f, tween(50, easing = LinearEasing))
        shakeX.animateTo(intensity * 0.1f, tween(40, easing = LinearEasing))
        shakeX.animateTo(0f, tween(30, easing = LinearEasing))
    }

    val currentParticles = particles

    if (showCanvas.value) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            currentParticles.forEach { p ->
                if (p.alpha > 0.015f && p.radius > 0.1f) {
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha * 0.15f),
                        radius = p.radius * 2.2f,
                        center = Offset(p.x, p.y)
                    )
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha * 0.45f),
                        radius = p.radius * 1.3f,
                        center = Offset(p.x, p.y)
                    )
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha),
                        radius = p.radius,
                        center = Offset(p.x, p.y)
                    )
                }
            }
        }
    }

    return shakeX.asState()
}

private data class ParticleState(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val alpha: Float,
    val color: Color,
    val life: Float
)
