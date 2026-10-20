package com.freechat.ui.adaptive

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import com.freechat.ui.theme.AppBoxWithConstraints as BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.freechat.i18n.LocalStrings
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.animation.LocalPageActive
import com.freechat.ui.animation.rememberTopOnEntryScrollState
import com.freechat.ui.components.HeaderIconButton
import com.freechat.ui.theme.LocalFreeChatColors
import kotlin.math.min
import kotlin.math.roundToInt

val LocalTabletUi = staticCompositionLocalOf { false }
/** Device identity remains true inside a phone-shaped floating panel. */
val LocalTabletDevice = staticCompositionLocalOf { false }
val LocalPaneSize = compositionLocalOf { DpSize.Unspecified }
val LocalPaneHasFocus = compositionLocalOf<State<Boolean>?> { null }

data class PaneControls(
    val expanded: Boolean,
    val toggle: () -> Unit,
    val singlePane: Boolean = false,
    val showDetail: () -> Unit = {},
)
val LocalPaneControls = compositionLocalOf<PaneControls?> { null }

@Composable
fun rememberTabletDevice(activity: Activity): Boolean {
    val configuration = LocalConfiguration.current
    // Real density, never the app's text scale. Maximum metrics survive split-screen resizing.
    val smallest = if (Build.VERSION.SDK_INT >= 30) {
        val bounds = activity.windowManager.maximumWindowMetrics.bounds
        (min(bounds.width(), bounds.height()) / activity.resources.displayMetrics.density).roundToInt()
    } else configuration.smallestScreenWidthDp
    return TabletLayoutPolicy.isTablet(smallest)
}

@Composable
fun PaneExpandButton(chat: Boolean = false) {
    if (!chat) return
    val controls = LocalPaneControls.current ?: return
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    HeaderIconButton(onClick = controls.toggle) {
        Icon(when {
            controls.singlePane -> Icons.AutoMirrored.Outlined.ViewSidebar
            controls.expanded -> Icons.Outlined.FullscreenExit
            else -> Icons.Outlined.Fullscreen
        }, if (controls.singlePane) s.tabletText("切换面板", "切換面板", "Switch pane")
            else if (controls.expanded) s.restorePanes else s.expandPane,
            tint = colors.TextSecondary, modifier = Modifier.size(22.dp))
    }
}

/** Both panes stay composed: full-screen is a presentation change, not a new route or form. */
@Composable
fun TabletWorkspace(
    workspaceKey: Any,
    balanced: Boolean = false,
    focused: TabletPane = TabletPane.DETAIL,
    revealDetailKey: Int = 0,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
) {
    var fullscreen by remember(workspaceKey) { mutableStateOf<TabletPane?>(null) }
    var previousFocus by remember(workspaceKey) { mutableStateOf(focused) }
    LaunchedEffect(focused) {
        if (previousFocus != focused) {
            fullscreen = null
            previousFocus = focused
        }
    }
    val motion = LocalMotionEnabled.current
    val active = LocalPageActive.current
    val colors = LocalFreeChatColors.current
    val leftFocused = remember { mutableStateOf(false) }
    val rightFocused = remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val panes = TabletLayoutPolicy.panes(maxWidth.value, balanced, fullscreen, focused)
        val natural = TabletLayoutPolicy.panes(maxWidth.value, balanced)
        val widthPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val leftWidthPx = with(density) { panes.listWidth.dp.roundToPx() }
        val rightWidthPx = with(density) { panes.detailWidth.dp.roundToPx() }
        val dividerWidthPx = with(density) { 1.dp.roundToPx() }
        val normalRight = with(density) { (natural.listWidth + natural.gap).dp.toPx() }
        val animation = if (motion) FreeChatAnimation.tabletLayer else snap()
        val leftX = animateFloatAsState(if (panes.listVisible) 0f else -leftWidthPx.toFloat(), animation, label = "tablet_list_x")
        val rightX = animateFloatAsState(when {
            !panes.detailVisible -> widthPx
            !panes.listVisible -> 0f
            else -> normalRight
        }, animation, label = "tablet_detail_x")
        val leftAlpha = animateFloatAsState(if (panes.listVisible) 1f else 0f, animation, label = "tablet_list_alpha")
        val rightAlpha = animateFloatAsState(if (panes.detailVisible) 1f else 0f, animation, label = "tablet_detail_alpha")
        val narrow = maxWidth.value < if (balanced) 720f else 600f
        var previousNarrow by remember(workspaceKey) { mutableStateOf(narrow) }
        LaunchedEffect(narrow) {
            fullscreen = TabletLayoutPolicy.fullscreenAfterResize(previousNarrow, narrow, fullscreen)
            previousNarrow = narrow
        }
        LaunchedEffect(revealDetailKey) {
            if (revealDetailKey > 0) fullscreen = if (narrow) TabletPane.DETAIL else null
        }
        val toggleList = { fullscreen = if (narrow) TabletPane.DETAIL
            else if (fullscreen == TabletPane.LIST) null else TabletPane.LIST }
        val toggleDetail = { fullscreen = if (narrow) TabletPane.LIST
            else if (fullscreen == TabletPane.DETAIL) null else TabletPane.DETAIL }
        val showDetail = { fullscreen = if (narrow) TabletPane.DETAIL else null }
        BackHandler(enabled = active && fullscreen != null) { fullscreen = null }
        LaunchedEffect(panes.listVisible, panes.detailVisible) {
            if ((!panes.listVisible && leftFocused.value) || (!panes.detailVisible && rightFocused.value))
                focusManager.clearFocus()
        }
        // Width is measured once for each target layout, not on every animation frame. Placement
        // moves the real Haze source bounds; translating only its draw layer would stale sampling.
        Layout(modifier = Modifier.fillMaxSize(), content = {
            CompositionLocalProvider(
                LocalTabletUi provides true,
                LocalPaneSize provides DpSize(panes.listWidth.dp, maxHeight),
                LocalPaneControls provides PaneControls(!panes.detailVisible, toggleList, narrow, showDetail),
                LocalPaneHasFocus provides leftFocused,
                LocalPageActive provides (active && panes.listVisible),
            ) {
                Box(Modifier.fillMaxSize().clipToBounds().onFocusChanged { leftFocused.value = it.hasFocus }
                    .paneInteraction(active && panes.listVisible)
                    .graphicsLayer { alpha = leftAlpha.value }
                ) { TabletMovingPane(TabletPane.LIST) { listPane() } }
            }
            CompositionLocalProvider(
                LocalTabletUi provides true,
                LocalPaneSize provides DpSize(panes.detailWidth.dp, maxHeight),
                LocalPaneControls provides PaneControls(!panes.listVisible, toggleDetail, narrow, showDetail),
                LocalPaneHasFocus provides rightFocused,
                LocalPageActive provides (active && panes.detailVisible),
            ) {
                Box(Modifier.fillMaxSize().clipToBounds().onFocusChanged { rightFocused.value = it.hasFocus }
                    .paneInteraction(active && panes.detailVisible)
                    .graphicsLayer { alpha = rightAlpha.value }
                ) { TabletMovingPane(TabletPane.DETAIL) { detailPane() } }
            }
        }) { measurables, available ->
            val left = measurables[0].measure(Constraints.fixed(leftWidthPx, available.maxHeight))
            val right = measurables[1].measure(Constraints.fixed(rightWidthPx, available.maxHeight))
            layout(available.maxWidth, available.maxHeight) {
                left.placeRelative(leftX.value.roundToInt(), 0)
                right.placeRelative(rightX.value.roundToInt(), 0)
            }
        }
        Box(Modifier.offset { IntOffset(rightX.value.roundToInt() - dividerWidthPx, 0) }.width(1.dp)
            .fillMaxHeight().graphicsLayer { alpha = min(leftAlpha.value, rightAlpha.value) }
            .background(colors.TextTertiary.copy(alpha = .12f)))
    }
}

fun Modifier.paneInteraction(active: Boolean): Modifier = this
    .focusProperties {
        canFocus = active
        onEnter = { if (!active) cancelFocusChange() }
    }
    .onPreviewKeyEvent { !active }
    .focusGroup()
    .then(if (active) Modifier else Modifier.clearAndSetSemantics { }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    })

@Composable
fun Modifier.paneImePadding(): Modifier = if (!LocalTabletUi.current || LocalPaneHasFocus.current?.value != false)
    imePadding() else this

/** Constrain the action surface, not its form; phone-shaped panels keep their original layout. */
@Composable
fun Modifier.pageActionWidth(): Modifier = if (LocalTabletUi.current)
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 320.dp).fillMaxWidth()
    else fillMaxWidth()

/** A retained pane becoming visible again is not a fresh settings-page entry. */
@Composable
fun rememberPaneScrollState(): ScrollState =
    if (LocalTabletUi.current) rememberScrollState() else rememberTopOnEntryScrollState()

/** Scope a non-split tablet screen without changing its saved form or the system's DPI. */
@Composable
fun TabletPage(maxContentWidth: Dp? = null, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
    BoxWithConstraints(Modifier.widthIn(max = maxContentWidth ?: Dp.Infinity).fillMaxSize()) {
        CompositionLocalProvider(LocalPaneSize provides DpSize(maxWidth, maxHeight)) {
            TabletMovingPane(TabletPane.DETAIL) { content() }
        }
    }
    }
}

@Composable
fun tabletReadingGutter(maxWidth: Dp = 800.dp): Dp {
    val size = LocalPaneSize.current
    return if (LocalTabletUi.current && size != DpSize.Unspecified)
        ((size.width - maxWidth) / 2).coerceAtLeast(0.dp) else 0.dp
}
