package com.freechat.ui.adaptive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.freechat.ui.theme.AppBoxWithConstraints as BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.animation.LocalPageActive
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.liquidOpaqueBackground
import kotlin.math.roundToInt

/** One continuous list–Chat–auxiliary canvas; the real Chat never changes composition slots. */
@Composable
fun TabletChatCanvas(
    supporting: Boolean,
    active: Boolean,
    listEnabled: Boolean = true,
    modeSelection: Boolean,
    arrivalKey: Int,
    detailPage: String? = null,
    detailBack: Boolean = false,
    listPane: @Composable () -> Unit,
    chatPane: @Composable () -> Unit,
    auxiliaryPane: @Composable () -> Unit,
    modePane: @Composable () -> Unit,
    detailPane: @Composable (String) -> Unit = {},
) {
    val motion = LocalMotionEnabled.current
    val colors = LocalFreeChatColors.current
    val focus = LocalFocusManager.current
    val parentActive = LocalPageActive.current
    val scope = rememberCoroutineScope()
    val chatCover = remember(scope) { TabletChatCover(scope) }
    SideEffect { chatCover.motionEnabled = motion }
    DisposableEffect(chatCover) { onDispose { chatCover.cancel() } }
    val overlayPage = if (modeSelection) "NEW_CHAT_MODE" else detailPage
    var homeFullscreen by remember { mutableStateOf<TabletPane?>(null) }
    var supportFullscreen by remember { mutableStateOf<TabletPane?>(null) }
    val listFocus = remember { mutableStateOf(false) }
    val chatFocus = remember { mutableStateOf(false) }
    val auxFocus = remember { mutableStateOf(false) }
    val spec = if (motion) FreeChatAnimation.tabletLayer else snap()
    val camera = animateFloatAsState(if (supporting) 1f else 0f, spec, label = "chat_camera")
    val supportTarget = rememberUpdatedState(supporting)
    val retainAux by remember { derivedStateOf { supportTarget.value || camera.value > 0f } }
    // Route arrivals cover Account/new-chat pages; conversation arrivals move content only.
    val routeArrival = remember(arrivalKey) { Animatable(if (arrivalKey > 0 && motion) 1f else 0f) }
    LaunchedEffect(routeArrival, motion) {
        if (motion) routeArrival.animateTo(0f, FreeChatAnimation.tabletLayer) else routeArrival.snapTo(0f)
    }
    LaunchedEffect(supporting) { supportFullscreen = null; if (supporting) homeFullscreen = null }
    LaunchedEffect(active, overlayPage) {
        if (!active || overlayPage != null) {
            chatCover.cancel()
            focus.clearFocus(force = true)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val natural = TabletLayoutPolicy.panes(maxWidth.value)
        val home = TabletLayoutPolicy.panes(maxWidth.value, fullscreen = homeFullscreen)
        val support = TabletLayoutPolicy.panes(maxWidth.value, fullscreen = supportFullscreen)
        val narrow = maxWidth < 600.dp
        var wasNarrow by remember { mutableStateOf(narrow) }
        val supportNarrow = narrow
        var wasSupportNarrow by remember { mutableStateOf(supportNarrow) }
        LaunchedEffect(narrow, supportNarrow) {
            homeFullscreen = TabletLayoutPolicy.fullscreenAfterResize(wasNarrow, narrow, homeFullscreen)
            supportFullscreen = TabletLayoutPolicy.fullscreenAfterResize(wasSupportNarrow, supportNarrow, supportFullscreen)
            wasNarrow = narrow
            wasSupportNarrow = supportNarrow
        }
        val chatVisible = if (supporting) support.listVisible else home.detailVisible
        val listActive = parentActive && active && listEnabled && !supporting && home.listVisible
        val chatActive = parentActive && active && overlayPage == null && chatVisible
        val auxActive = parentActive && active && supporting && support.detailVisible
        LaunchedEffect(listActive, chatActive, auxActive) {
            if ((!listActive && listFocus.value) || (!chatActive && chatFocus.value) || (!auxActive && auxFocus.value))
                focus.clearFocus(force = true)
        }
        BackHandler(enabled = parentActive && active && (if (supporting) supportFullscreen else homeFullscreen) != null) {
            if (supporting) supportFullscreen = null else homeFullscreen = null
        }
        val showChat = { homeFullscreen = if (narrow) TabletPane.DETAIL else null }
        val listControls = PaneControls(!home.detailVisible,
            toggle = { homeFullscreen = if (narrow) TabletPane.DETAIL else if (homeFullscreen == TabletPane.LIST) null else TabletPane.LIST },
            singlePane = narrow, showDetail = showChat)
        val chatControls = if (supporting) PaneControls(!support.detailVisible,
            toggle = { supportFullscreen = if (supportFullscreen == TabletPane.LIST) null else TabletPane.LIST },
            singlePane = false,
            showDetail = { supportFullscreen = null })
        else PaneControls(!home.listVisible,
            toggle = { homeFullscreen = if (narrow) TabletPane.LIST else if (homeFullscreen == TabletPane.DETAIL) null else TabletPane.DETAIL },
            singlePane = narrow, showDetail = showChat)
        val targetChatWidth = natural.detailWidth
        val homeFrame = TabletNavigationPolicy.stableChatFrame(maxWidth.value, false, !home.listVisible)
        val supportFrame = TabletNavigationPolicy.stableChatFrame(maxWidth.value, true, !support.detailVisible)
        val targetChatX = supportFrame.chatX
        val homeChatX = if (!home.detailVisible) maxWidth.value else homeFrame.chatX
        val homeListX = if (home.listVisible) 0f else -natural.listWidth - 1f
        val homeX = animateFloatAsState(homeChatX, spec, label = "home_chat_x")
        val homeList = animateFloatAsState(homeListX, spec, label = "home_list_x")
        val supportX = animateFloatAsState(targetChatX, spec, label = "support_chat_x")
        val chatAlpha = animateFloatAsState(if (chatVisible) 1f else 0f, spec, label = "canvas_chat_alpha")
        val auxAlpha = animateFloatAsState(if (supporting && support.detailVisible) 1f else 0f, spec, label = "canvas_aux_alpha")
        val homeSeamAlpha = animateFloatAsState(if (home.split) 1f else 0f, spec, label = "home_seam_alpha")
        val listWidthPx = with(density) { home.listWidth.dp.roundToPx() }
        val auxWidth = if (narrow) maxWidth.value else natural.listWidth
        fun currentFrame() = TabletNavigationPolicy.cameraBetween(
            homeList.value, -home.listWidth - 1f, homeX.value, targetChatWidth,
            supportX.value, targetChatWidth, camera.value,
        )

        Layout(modifier = Modifier.fillMaxSize(), content = {
            CompositionLocalProvider(LocalPaneSize provides DpSize(home.listWidth.dp, maxHeight),
                LocalPaneControls provides listControls, LocalPaneHasFocus provides listFocus,
                LocalTabletChatCover provides chatCover,
                LocalPageActive provides listActive) {
                Box(Modifier.fillMaxSize().clipToBounds().onFocusChanged { listFocus.value = it.hasFocus }
                    .paneInteraction(listActive)) { TabletMovingPane(TabletPane.LIST) { listPane() } }
            }
            CompositionLocalProvider(LocalPaneSize provides DpSize(targetChatWidth.dp, maxHeight),
                LocalPaneControls provides chatControls, LocalPaneHasFocus provides chatFocus,
                LocalTabletChatCover provides chatCover,
                LocalPageActive provides (parentActive && active && chatVisible)) {
                TabletMovingPane(TabletPane.DETAIL) {
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    Box(Modifier.fillMaxSize().onFocusChanged { chatFocus.value = it.hasFocus }
                        .zIndex(if (overlayPage == null && !detailBack) 2f else 0f)
                        .paneInteraction(chatActive).graphicsLayer {
                            translationX = routeArrival.value * size.width
                            alpha = chatAlpha.value
                        }.background(colors.Background).liquidOpaqueBackground(colors.Background)) {
                        CompositionLocalProvider(LocalPageActive provides chatActive) { chatPane() }
                    }
                    TabletPageMotion(overlayPage, back = detailBack,
                        modifier = Modifier.zIndex(1f), opaque = false, label = "chat_detail_overlay") { page ->
                        if (page != null) {
                            Box(Modifier.fillMaxSize().background(colors.Background).liquidOpaqueBackground(colors.Background)
                                .paneInteraction(parentActive && active && page == overlayPage)) {
                                if (page == "NEW_CHAT_MODE") modePane() else detailPane(page)
                            }
                        }
                    }
                }
                }
            }
            CompositionLocalProvider(LocalPaneSize provides DpSize(auxWidth.dp, maxHeight),
                LocalPaneControls provides null, LocalPaneHasFocus provides auxFocus,
                LocalPageActive provides auxActive) {
                Box(Modifier.fillMaxSize().clipToBounds().onFocusChanged { auxFocus.value = it.hasFocus }
                    .paneInteraction(auxActive).graphicsLayer { alpha = auxAlpha.value }.background(colors.Background)) {
                    if (retainAux) auxiliaryPane()
                }
            }
        }) { measurables, available ->
            val stableChatWidthPx = with(density) { targetChatWidth.dp.roundToPx() }.coerceAtLeast(1)
            val left = measurables[0].measure(Constraints.fixed(listWidthPx, available.maxHeight))
            val chat = measurables[1].measure(Constraints.fixed(stableChatWidthPx, available.maxHeight))
            val aux = measurables[2].measure(Constraints.fixed(with(density) { auxWidth.dp.roundToPx() }, available.maxHeight))
            layout(available.maxWidth, available.maxHeight) {
                val current = currentFrame()
                val chatXPx = with(density) { current.chatX.dp.roundToPx() }
                left.placeRelative(with(density) { current.listX.dp.roundToPx() }, 0)
                chat.placeRelative(chatXPx, 0)
                aux.placeRelative(chatXPx + chat.width + with(density) { camera.value.dp.roundToPx() }, 0)
            }
        }
        // The old seam travels out with the sidebar while the new seam enters with the auxiliary.
        // They never teleport from the left Chat edge to the right edge on the first frame.
        Box(Modifier.offset {
            IntOffset(with(density) { (currentFrame().chatX - 1f).dp.roundToPx() }, 0)
        }.width(1.dp).fillMaxHeight().graphicsLayer { alpha = (1f - camera.value) * homeSeamAlpha.value }
            .background(colors.TextTertiary.copy(alpha = .12f)))
        // Divider and auxiliary origin derive from the same animated Chat edge, never separate clocks.
        Box(Modifier.offset {
            IntOffset(with(density) { currentFrame().dividerX.dp.roundToPx() }, 0)
        }.width(1.dp).fillMaxHeight().graphicsLayer { alpha = camera.value * auxAlpha.value }
            .background(colors.TextTertiary.copy(alpha = .12f)))
    }
}
