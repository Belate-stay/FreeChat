package com.freechat.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeEffect

/** 根节点维护一次材质动画；只在绘制/效果更新时读取，不驱动整页逐帧重组。 */
val LocalMaterialProgress = staticCompositionLocalOf<State<Float>?> { null }

/** One shared transition for button backings on every page; icons never fade with the backing. */
val LocalHeaderCardProgress = staticCompositionLocalOf<State<Float>?> { null }

internal fun headerCardStrength(material: Float, card: Float): Float =
    material.coerceIn(0f, 1f) * card.coerceIn(0f, 1f)

@Composable
internal fun materialProgress(): () -> Float {
    val progress = LocalMaterialProgress.current
    val enabled = LocalAdvancedMaterial.current
    return { progress?.value ?: if (enabled) 1f else 0f }
}

/** 全局玻璃与高斯模糊随材质开关平滑变化，流光不参与材质选择。 */
@Composable
fun Modifier.materialHaze(state: HazeState, block: HazeEffectScope.() -> Unit): Modifier {
    val progress = materialProgress()
    return hazeEffect(state = state) {
        block()
        // 玻璃保持半尺寸采样。渐进模糊在下缘趋于清晰，不能把正常文字先降采样糊掉；
        // 渐进带限定在标题/侧栏固定悬浮区域，保留全尺寸以保证下缘文字清晰。
        inputScale = if (progressive != null) HazeInputScale.None else HazeInputScale.Fixed(0.5f)
        noiseFactor = 0.025f
        alpha = progress()
    }
}
