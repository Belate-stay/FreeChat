package com.freechat.ui.adaptive

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.liquidOpaqueBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val LocalTabletChatCover = staticCompositionLocalOf<TabletChatCover?> { null }

/** Keep one outgoing image, not a second live Chat with duplicate effects and API callbacks. */
internal class TabletChatCover(private val scope: CoroutineScope) {
    val offset = Animatable(0f)
    var snapshot by mutableStateOf<ImageBitmap?>(null)
        private set
    var capture: (suspend () -> ImageBitmap?)? = null
    var motionEnabled = true
        set(value) { if (field != value) { field = value; if (!value) cancel() } }
    var contentKey by mutableStateOf<String?>(null)
    var ready by mutableStateOf(false)
    private var switchJob: Job? = null
    private var request = 0

    fun switchTo(key: String?, action: () -> Boolean) {
        val token = ++request
        switchJob?.cancel()
        if (key == contentKey || !motionEnabled) { snapshot = null; action(); return }
        switchJob = scope.launch {
            try {
                val old = try { capture?.invoke() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                snapshot = old
                offset.snapTo(if (old != null) 1f else 0f)
                if (!action()) return@launch
                if (old != null) {
                    snapshotFlow { ready && contentKey == key }.first { it }
                    offset.animateTo(0f, FreeChatAnimation.tabletLayer)
                }
            } finally {
                if (request == token) {
                    snapshot = null
                    offset.snapTo(0f)
                }
            }
        }
    }

    fun cancel() { ++request; switchJob?.cancel(); snapshot = null }
}

/** Only the content/header moves. The composer is a sibling outside this recording boundary. */
@Composable
internal fun TabletChatContent(key: String?, ready: Boolean, content: @Composable BoxScope.() -> Unit) {
    val cover = LocalTabletChatCover.current
    if (cover == null) { Box(Modifier.fillMaxSize(), content = content); return }
    val layer = rememberGraphicsLayer()
    val drawn = remember { booleanArrayOf(false) }
    val colors = LocalFreeChatColors.current
    DisposableEffect(cover, layer) {
        cover.capture = { if (drawn[0]) layer.toImageBitmap() else null }
        onDispose { cover.capture = null; cover.cancel() }
    }
    SideEffect { cover.contentKey = key; cover.ready = ready }
    Box(Modifier.fillMaxSize().clipToBounds().drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
        drawn[0] = true
    }) {
        cover.snapshot?.let { image ->
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        Box(Modifier.fillMaxSize().layout { measurable, constraints ->
            val page = measurable.measure(constraints)
            layout(page.width, page.height) {
                page.placeRelative((if (cover.snapshot != null) cover.offset.value * page.width else 0f).roundToInt(), 0)
            }
        }.background(colors.Background).liquidOpaqueBackground(colors.Background), content = content)
    }
}
