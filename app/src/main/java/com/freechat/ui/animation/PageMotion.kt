package com.freechat.ui.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Outgoing pages remain drawn during fading, but must not handle navigation events. */
internal val LocalPageActive = compositionLocalOf { true }

internal fun pageTranslation(state: EnterExitState, forward: Boolean, distancePx: Float): Float {
    val distance = distancePx.coerceAtLeast(0f) * if (forward) 1f else -1f
    return when (state) {
        EnterExitState.PreEnter -> distance
        EnterExitState.Visible -> 0f
        EnterExitState.PostExit -> -distance / 3f
    }
}

/** Full-screen fade/slide without per-frame placement of the page's layout/haze tree.
 *
 * The child's Transition owns BOTH properties: AnimatedContent therefore retains outgoing
 * content until its exit completes, and rapid reversals resume from the current values.
 * Read the animated values only in the layer, never while composing or measuring a page.
 * Compact controls whose size really changes continue to use the normal size transition.
 */
@Composable
fun <T> PageMotion(
    targetState: T,
    distancePx: Float,
    forward: (T, T) -> Boolean,
    modifier: Modifier = Modifier,
    label: String = "page_motion",
    content: @Composable (T) -> Unit,
) {
    val enabled = LocalMotionEnabled.current
    val parentActive = LocalPageActive.current
    val pages = updateTransition(targetState, label = label)
    pages.AnimatedContent(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
        transitionSpec = { ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null) },
    ) { page ->
        val advances = forward(pages.segment.initialState, pages.segment.targetState)
        val travel = transition.animateFloat(label = "page_layer_x", transitionSpec = {
            if (!enabled) snap()
            else if (targetState == EnterExitState.Visible) FreeChatAnimation.pageLayerSlide
            else FreeChatAnimation.pageLayerSlideOut
        }) { state -> if (enabled) pageTranslation(state, advances, distancePx) else 0f }
        val opacity = transition.animateFloat(label = "page_layer_alpha", transitionSpec = {
            if (!enabled) snap()
            else if (targetState == EnterExitState.Visible) FreeChatAnimation.overlayFadeIn
            else FreeChatAnimation.pageFadeOutFast
        }) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        CompositionLocalProvider(LocalPageActive provides (parentActive && page == pages.targetState)) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX = travel.value
                alpha = opacity.value
            }) { content(page) }
        }
    }
}
