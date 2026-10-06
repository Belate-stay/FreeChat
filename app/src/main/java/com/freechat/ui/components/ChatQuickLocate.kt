package com.freechat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.freechat.i18n.LocalStrings
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.MotionPolicy
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.LocalColorTheme
import com.freechat.model.ColorTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun ChatQuickLocate(visible: Boolean, list: LazyListState, items: List<ChatScrollItem>, conversationId: String?,
    onSeek: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    val advanced = LocalAdvancedMaterial.current
    val density = LocalDensity.current
    val isDark = colors.TextPrimary.luminance() > 0.5f
    val monochrome = LocalColorTheme.current == ColorTheme.WHITE
    val thumbColor = if (monochrome) Color.Black else colors.Primary
    val railColor = if (monochrome && isDark) Color(0xFFDDE1E5)
        else lerp(colors.Surface, colors.TextPrimary, if (isDark) 0.10f else 0.05f)
    val travel = with(density) { MotionPolicy.LocatorTravelDp.dp.roundToPx() }
    val latestDismiss by rememberUpdatedState(onDismiss)
    var dragging by remember(conversationId) { mutableStateOf(false) }
    var activity by remember(conversationId) { mutableIntStateOf(0) }
    // Any real position change (including a normal swipe/fling or bottom-follow) restarts the timer.
    LaunchedEffect(visible, dragging, activity, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.isScrollInProgress) {
        if (visible && !dragging && !list.isScrollInProgress) {
            delay(QuickLocatePolicy.IdleMillis)
            latestDismiss()
        }
    }
    AnimatedVisibility(visible, modifier,
        enter = FreeChatAnimation.quickLocateEnter(travel),
        exit = FreeChatAnimation.quickLocateExit(travel)) {
        val measured = remember(conversationId) { mutableMapOf<String, Pair<Float, Int>>() }
        var geometry by remember(conversationId) { mutableStateOf(ChatScrollGeometry(emptyList(), 0, 0, 0, 0)) }
        val latestGeometry by rememberUpdatedState(geometry)
        val latestSeek by rememberUpdatedState(onSeek)
        val seeks = remember(conversationId) { Channel<Float>(Channel.CONFLATED) }
        var requestedFraction by remember(conversationId) { mutableFloatStateOf(0f) }
        var trackHeight by remember { mutableIntStateOf(1) }
        var lastLayoutSignature by remember { mutableStateOf<List<Int>>(emptyList()) }
        LaunchedEffect(items, list, conversationId) {
            val keys = items.mapTo(HashSet()) { it.key }
            measured.keys.retainAll(keys)
            var refresh = true
            snapshotFlow { list.layoutInfo }.conflate().collect { layout ->
                if (layout.totalItemsCount != items.size) return@collect
                val signature = listOf(layout.beforeContentPadding, layout.afterContentPadding,
                    layout.viewportSize.height, layout.mainAxisItemSpacing, layout.totalItemsCount)
                var changed = refresh || signature != lastLayoutSignature
                layout.visibleItemsInfo.forEach { item ->
                    val spec = items.getOrNull(item.index) ?: return@forEach
                    val next = spec.estimatedHeight to item.size.coerceAtLeast(1)
                    if (measured[spec.key] != next) { measured[spec.key] = next; changed = true }
                }
                if (changed || geometry.itemCount != items.size) {
                    refresh = false
                    lastLayoutSignature = signature
                    geometry = ChatScrollGeometry(items.map { spec ->
                        measured[spec.key]?.takeIf { it.first == spec.estimatedHeight }?.second?.toFloat() ?: spec.estimatedHeight
                    }, layout.mainAxisItemSpacing, layout.beforeContentPadding, layout.afterContentPadding, layout.viewportSize.height)
                }
            }
        }
        LaunchedEffect(seeks, list) {
            for (fraction in seeks) {
                withFrameNanos { }
                val target = latestGeometry.target(fraction)
                if (latestGeometry.itemCount > 0 && list.layoutInfo.totalItemsCount > target.index) {
                    try { list.scrollToItem(target.index, target.offset) }
                    catch (_: CancellationException) {
                        // A normal list gesture may take over the scroll mutex, not kill the seeker.
                        currentCoroutineContext().ensureActive()
                    }
                }
            }
        }
        DisposableEffect(seeks) { onDispose { seeks.close(); dragging = false } }
        val position by remember(list, conversationId) { derivedStateOf {
            when {
                !list.canScrollBackward -> 0f
                !list.canScrollForward -> 1f
                else -> geometry.fraction(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
            }
        } }
        fun request(fraction: Float) {
            activity++
            requestedFraction = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
            latestSeek()
            seeks.trySend(requestedFraction)
        }
        val shown = if (dragging) requestedFraction else position
        val thumbScale by animateFloatAsState(if (dragging) 1f else 0f,
            FreeChatAnimation.thumbSpring, label = "quick_locate_thumb")
        val inset = QuickLocatePolicy.insetForHeight(trackHeight.toFloat(), with(density) { 24.dp.toPx() })
        val thumbHalf = QuickLocatePolicy.thumbHalfHeight(inset, with(density) { 16.dp.toPx() })
        val thumbY = ((trackHeight - inset * 2f).coerceAtLeast(0f) * shown + inset).roundToInt()
        val timeIndex by remember(list, conversationId) { derivedStateOf {
            // Offscreen heights are estimates. Label the row actually on screen, not a second
            // predicted seek target after those estimates change during the drag.
            list.firstVisibleItemIndex
        } }
        val timestamp = QuickLocatePolicy.timestampAt(items, timeIndex)
        val dateLabel = remember(timestamp, s.quickLocateTimeFormat) {
            timestamp?.let { SimpleDateFormat(s.quickLocateTimeFormat, Locale.getDefault()).format(Date(it)) }.orEmpty()
        }
        val trackShape = RoundedCornerShape(50)
        // The date bubble has room to the left, but ONLY the 48dp rail region captures gestures.
        // The visual rail is deliberately slender; it does not require a precision tap.
        Box(Modifier.fillMaxHeight().width(260.dp).padding(end = MotionPolicy.LocatorGutterDp.dp)) {
          Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(48.dp)
            .onSizeChanged { trackHeight = it.height }
            .semantics {
                contentDescription = s.quickLocate
                if (dateLabel.isNotEmpty()) stateDescription = dateLabel
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                setProgress { request(it); true }
                customActions = listOf(CustomAccessibilityAction(s.locateStart) { request(0f); true },
                    CustomAccessibilityAction(s.locateEnd) { request(1f); true })
            }.focusable().onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionUp -> { request(position - 0.05f); true }
                    Key.DirectionDown -> { request(position + 0.05f); true }
                    Key.MoveHome -> { request(0f); true }
                    Key.MoveEnd -> { request(1f); true }
                    else -> false
                }
            }.pointerInput(conversationId, inset) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val center = ((size.height - inset * 2f).coerceAtLeast(0f) * position + inset)
                    val grab = QuickLocatePolicy.grabOffset(down.position.y, center, with(density) { 26.dp.toPx() })
                    dragging = true
                    down.consume()
                    request(QuickLocatePolicy.trackFraction(down.position.y - grab, size.height.toFloat(), inset))
                    try {
                        var pressed: Boolean
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            request(QuickLocatePolicy.trackFraction(change.position.y - grab, size.height.toFloat(), inset))
                            event.changes.forEach { it.consume() }
                            pressed = change.pressed
                        } while (pressed)
                    } finally { dragging = false; activity++ }
                }
            }) {
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(10.dp)
                .shadow(if (advanced) 6.dp else 4.dp, trackShape, clip = false,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.6f else 0.3f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.6f else 0.3f))
                .background(railColor, trackShape))
            Box(Modifier.align(Alignment.TopEnd)
                .offset { IntOffset(-with(density) { 2.dp.roundToPx() }, thumbY - thumbHalf.roundToInt()) }
                .size(width = 6.dp, height = with(density) { (thumbHalf * 2f).toDp() })
                .graphicsLayer { scaleX = 1f + thumbScale * 0.5f; scaleY = 1f + thumbScale * 0.22f }
                .shadow(2.dp, trackShape, clip = false)
                .background(thumbColor, trackShape))
          }
          AnimatedVisibility(dragging && dateLabel.isNotEmpty(), modifier = Modifier.align(Alignment.TopEnd)
              .offset { IntOffset(-with(density) { 28.dp.roundToPx() },
                  (thumbY - with(density) { 23.dp.roundToPx() }).coerceIn(0, (trackHeight - with(density) { 46.dp.roundToPx() }).coerceAtLeast(0))) },
              enter = FreeChatAnimation.menuEnter(TransformOrigin(1f, 0.5f)),
              exit = FreeChatAnimation.menuExit(TransformOrigin(1f, 0.5f))) {
            Text(dateLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                color = colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
                    .shadow(8.dp, RoundedCornerShape(50), clip = false,
                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.6f else 0.35f),
                        spotColor = Color.Black.copy(alpha = if (isDark) 0.6f else 0.35f))
                    .background(colors.Surface, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}
