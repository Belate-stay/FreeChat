package com.freechat.ui.adaptive

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
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.animation.LocalPageActive
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.liquidOpaqueBackground
import kotlin.math.roundToInt

/** Cover keeps the old page still; Back lifts the same incoming page off its parent. */
@Composable
fun <T> TabletPageMotion(
    targetState: T,
    back: Boolean,
    modifier: Modifier = Modifier,
    opaque: Boolean = true,
    label: String = "tablet_page",
    content: @Composable (T) -> Unit,
) {
    val enabled = LocalMotionEnabled.current
    val parentActive = LocalPageActive.current
    val colors = LocalFreeChatColors.current
    val pages = updateTransition(targetState, label = label)
    val transforms = remember { mutableMapOf<T, ContentTransform>() }
    LaunchedEffect(pages.currentState, pages.targetState, pages.isRunning) {
        if (!pages.isRunning) transforms.keys.retainAll(setOf(pages.targetState))
    }
    pages.AnimatedContent(
        modifier = modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            transforms.getOrPut(targetState) {
                ContentTransform(EnterTransition.None, ExitTransition.KeepUntilTransitionsFinished, sizeTransform = null)
            }
        },
    ) { page ->
        val travel = transition.animateFloat(transitionSpec = {
            if (!enabled) snap() else if (back) FreeChatAnimation.tabletLayerOut else FreeChatAnimation.tabletLayer
        }, label = "pane_edge") { state ->
            if (!enabled || state == EnterExitState.Visible) 0f
            else TabletNavigationPolicy.pageTravel(TabletPane.DETAIL, state == EnterExitState.PreEnter, back)
        }
        val active = parentActive && page == pages.targetState
        SideEffect { transforms[page]?.targetContentZIndex = if ((page == pages.targetState) != back) 1f else 0f }
        CompositionLocalProvider(LocalPageActive provides active) {
            Box(Modifier.fillMaxSize()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative((travel.value * placeable.width).roundToInt(), 0)
                    }
                }
                .paneInteraction(active)
                .then(if (opaque) Modifier.background(colors.Background).liquidOpaqueBackground(colors.Background) else Modifier)
            ) { content(page) }
        }
    }
}
