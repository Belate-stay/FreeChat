package com.freechat.ui.adaptive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.freechat.ui.theme.AppBoxWithConstraints as BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.animation.LocalPageActive
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.neumorphTone
import com.freechat.ui.theme.neumorphShadow
import com.freechat.ui.theme.softShadow
import com.freechat.ui.theme.liquidOpaqueBackground
import kotlin.math.roundToInt

internal data class TabletSceneMotion(val list: State<Float>, val detail: State<Float>)
internal val LocalTabletSceneMotion = compositionLocalOf<TabletSceneMotion?> { null }

/** Placement keeps Haze and touch coordinates aligned with the real moving pane. */
@Composable
internal fun TabletMovingPane(side: TabletPane, content: @Composable () -> Unit) {
    val motion = LocalTabletSceneMotion.current
    val colors = LocalFreeChatColors.current
    val travel = if (side == TabletPane.LIST) motion?.list else motion?.detail
    Box(Modifier.fillMaxSize().clipToBounds()) {
        CompositionLocalProvider(LocalTabletSceneMotion provides null) {
            Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(((travel?.value ?: 0f) * placeable.width).roundToInt(), 0)
                }
            }.then(if (motion != null) Modifier.background(colors.Background).liquidOpaqueBackground(colors.Background) else Modifier)) { content() }
        }
    }
}

/** Changing embedded/floating presentation does not replace the destination's composition. */
@Composable
internal fun <T : Any> TabletDestinationLayer(
    destination: T?,
    floating: Boolean,
    side: TabletPane,
    back: Boolean = false,
    allowBaseTouch: Boolean = false,
    onClose: () -> Unit = {},
    windowState: FloatingWindowState? = null,
    content: @Composable (T) -> Unit,
) {
    val enabled = LocalMotionEnabled.current
    val parentActive = LocalPageActive.current
    val colors = LocalFreeChatColors.current
    val window = windowState ?: remember { FloatingWindowState() }
    val pages = updateTransition(destination, label = "tablet_destination")
    val transforms = remember { mutableMapOf<T?, ContentTransform>() }
    LaunchedEffect(pages.currentState, pages.targetState, pages.isRunning) {
        if (!pages.isRunning) transforms.keys.retainAll(setOf(pages.targetState))
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight
        pages.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { transforms.getOrPut(targetState) {
                ContentTransform(EnterTransition.None, ExitTransition.KeepUntilTransitionsFinished, sizeTransform = null)
            } },
        ) { page ->
            SideEffect { transforms[page]?.targetContentZIndex = if ((page == destination) != back) 1f else 0f }
            if (page != null) {
                // Keep the outgoing page's original presentation, especially forced-embedded creation.
                var retainedFloating by remember { mutableStateOf(floating) }
                var retainedSide by remember { mutableStateOf(side) }
                SideEffect { if (page == destination) { retainedFloating = floating; retainedSide = side } }
                val pageFloating = if (page == destination) floating else retainedFloating
                val pageSide = if (page == destination) side else retainedSide
                val bounds = TabletNavigationPolicy.floatingBounds(windowWidth.value, windowHeight.value, pageSide)
                val spec = if (enabled) FreeChatAnimation.tabletPane else snap()
                val x by animateDpAsState(if (pageFloating) bounds.x.dp else 0.dp, spec, label = "panel_x")
                val y by animateDpAsState(if (pageFloating) bounds.y.dp else 0.dp, spec, label = "panel_y")
                val w by animateDpAsState(if (pageFloating) bounds.width.dp else windowWidth, spec, label = "panel_width")
                val h by animateDpAsState(if (pageFloating) bounds.height.dp else windowHeight, spec, label = "panel_height")
                val radius by animateDpAsState(if (pageFloating) 30.dp else 0.dp, spec, label = "panel_radius")
                val elevation by animateDpAsState(if (pageFloating) 8.dp else 0.dp, spec, label = "panel_shadow")
                fun travel(state: EnterExitState, pane: TabletPane): Float = when (state) {
                    EnterExitState.Visible -> 0f
                    EnterExitState.PreEnter -> TabletNavigationPolicy.pageTravel(pane, entering = true, back)
                    EnterExitState.PostExit -> TabletNavigationPolicy.pageTravel(pane, entering = false, back)
                }
                val left = transition.animateFloat(transitionSpec = {
                    if (!enabled) snap() else FreeChatAnimation.tabletLayer
                }, label = "left_page_edge") { if (enabled) travel(it, TabletPane.LIST) else 0f }
                val right = transition.animateFloat(transitionSpec = {
                    if (!enabled) snap() else FreeChatAnimation.tabletLayer
                }, label = "right_page_edge") { if (enabled) travel(it, TabletPane.DETAIL) else 0f }
                val card = transition.animateFloat(transitionSpec = {
                    if (!enabled) snap()
                    else if (targetState == EnterExitState.Visible) FreeChatAnimation.tabletFloatingEnter
                    else FreeChatAnimation.tabletLayerOut
                }, label = "floating_page_edge") {
                    if (!enabled || it == EnterExitState.Visible) 0f
                    else TabletNavigationPolicy.pageTravel(pageSide, it == EnterExitState.PreEnter, back)
                }
                val opacity = transition.animateFloat(transitionSpec = {
                    if (!enabled) snap() else FreeChatAnimation.overlayFadeIn
                }, label = "floating_page_alpha") { if (it == EnterExitState.Visible) 1f else 0f }
                val active = parentActive && page == destination
                SideEffect { window.dismiss = onClose }
                // AnimatedContent gives its direct child full-window minimum constraints.
                // Relax those for the card; size() cannot shrink an already fixed-width child.
                Box(Modifier.fillMaxSize(), propagateMinConstraints = false) {
                // No scrim/blur. Block the workspace only when underlying touch is disabled.
                if (!pageFloating || !allowBaseTouch) Box(Modifier.fillMaxSize().paneInteraction(false))
                Box(Modifier.offset { IntOffset(x.roundToPx(), y.roundToPx()) }.size(w, h)
                    .graphicsLayer {
                        if (pageFloating) {
                            translationX = card.value * (size.width + 24.dp.toPx())
                            alpha = opacity.value
                        }
                    }
                    .floatingPanelShadow(RoundedCornerShape(radius), elevation)
                    .clip(RoundedCornerShape(radius))
                    .then(if (pageFloating) Modifier.background(colors.Background) else Modifier)
                    .paneInteraction(active)
                    .then(if (pageFloating) Modifier.consumeWindowInsets(WindowInsets.systemBars) else Modifier)
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(
                            LocalTabletUi provides !pageFloating,
                            LocalPaneControls provides null,
                            LocalPaneSize provides DpSize(maxWidth, maxHeight),
                            LocalPageActive provides active,
                            LocalFloatingWindow provides if (pageFloating) window else null,
                            LocalTabletSceneMotion provides if (pageFloating) null else TabletSceneMotion(left, right),
                        ) {
                            content(page)
                            if (pageFloating) FloatingWindowChrome(window)
                        }
                    }
                }
                }
            } else {
                // Retain the stationary overlay while a newly selected home page covers it.
                val hold = transition.animateFloat(transitionSpec = {
                    if (!enabled) snap() else FreeChatAnimation.tabletLayer
                }, label = "home_cover_hold") { if (it == EnterExitState.Visible) 0f else 1f }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = hold.value * 0f })
            }
        }
    }
}

/** Same calibrated shadow vocabulary as the existing cards, not stacked black platform shadows. */
@Composable
private fun Modifier.floatingPanelShadow(shape: RoundedCornerShape, elevation: Dp): Modifier {
    val colors = LocalFreeChatColors.current
    val dark = colors.TextPrimary.luminance() > .5f
    val strength = { (elevation / 8.dp).coerceIn(0f, 1f) }
    return if (LocalAdvancedMaterial.current)
        neumorphShadow(shape, neumorphTone(colors, dark, false), recessed = false, strength = strength)
    else softShadow(shape, dy = 1.dp, spread = 5.dp, maxAlpha = if (dark) .04f else .025f, strength = strength)
}
