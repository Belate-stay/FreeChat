package com.freechat.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.freechat.ui.animation.MotionIconButton as IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.freechat.i18n.LocalStrings
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.LocalMotionEnabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlin.math.abs
import kotlin.math.roundToInt

/** At fit scale horizontal swipes page the gallery; zoomed images pan within their bounds. */
@Composable
fun FullScreenImagePreview(images: List<PreviewImage>, initialIndex: Int,
    originRect: Rect?, onDownload: (PreviewImage) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val motionEnabled = LocalMotionEnabled.current
    val latestDismiss by rememberUpdatedState(onDismiss)
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }
    val initialImage = remember { images[initialIndex] }
    var selectedImage by remember { mutableStateOf(initialImage) }
    LaunchedEffect(images) {
        pagerState.scrollToPage(images.indexOf(selectedImage).coerceAtLeast(0))
        snapshotFlow { pagerState.currentPage }.collect { selectedImage = images[it] }
    }
    val currentImage = images[pagerState.currentPage.coerceIn(images.indices)]
    val imageSource = currentImage.source
    val imageSizes = remember { mutableStateMapOf<String, Size>() }
    var scale by remember(currentImage) { mutableFloatStateOf(1f) }
    var pan by remember(currentImage) { mutableStateOf(Offset.Zero) }
    var dismissDrag by remember(currentImage) { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    var gestureAnimation by remember { mutableStateOf<Job?>(null) }
    var singleTap by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(currentImage) { singleTap?.cancel(); gestureAnimation?.cancel() }
    LaunchedEffect(Unit) {
        if (motionEnabled) progress.animateTo(1f, FreeChatAnimation.imageExpandSpring)
        else progress.snapTo(1f)
    }
    fun close() {
        if (!closing) {
            closing = true
            singleTap?.cancel()
            gestureAnimation?.cancel()
            scope.launch {
                if (motionEnabled) progress.animateTo(0f, FreeChatAnimation.imageShrinkTween)
                else progress.snapTo(0f)
                latestDismiss()
            }
        }
    }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            // Only the dialog window changes: dismissing it restores the chat's system bars intact.
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                it.statusBarColor = AndroidColor.TRANSPARENT
                it.navigationBarColor = AndroidColor.TRANSPARENT
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    it.isStatusBarContrastEnforced = false
                    it.isNavigationBarContrastEnforced = false
                }
                if (Build.VERSION.SDK_INT >= 28) it.attributes = it.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                it.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
            onDispose { }
        }
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
            val viewportW = with(density) { maxWidth.toPx() }
            val viewportH = with(density) { maxHeight.toPx() }
            val currentSize = imageSizes[imageSource] ?: Size.Zero
            val currentFit = ImagePreviewGeometry.fitRect(viewportW, viewportH, currentSize.width, currentSize.height)
            LaunchedEffect(viewportW, viewportH, currentFit, currentImage) {
                pan = ImagePreviewGeometry.clampPan(pan, scale, viewportW, viewportH, currentFit)
            }
            val p = progress.value.coerceIn(0f, 1f)
            val dragFraction = (abs(dismissDrag) / viewportH).coerceIn(0f, 0.6f)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (p * (1f - dragFraction)).coerceIn(0f, 1f))))
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(),
                userScrollEnabled = scale <= 1.01f && !closing && p >= 0.99f && dismissDrag == 0f,
                key = { images[it].key }, overscrollEffect = null) { pageIndex ->
                val image = images[pageIndex]
                val imageSize = imageSizes[image.source] ?: Size.Zero
                val fitted = ImagePreviewGeometry.fitRect(viewportW, viewportH, imageSize.width, imageSize.height)
                val origin = originRect.takeIf { image == initialImage } ?:
                    Rect(viewportW / 2 - 60, viewportH / 2 - 60, viewportW / 2 + 60, viewportH / 2 + 60)
                val active = pageIndex == pagerState.currentPage
                Box(Modifier.fillMaxSize().pointerInput(viewportW, viewportH, fitted, image.source, currentImage) {
                    var lastTapTime = 0L
                    var lastTapPosition = Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (closing || progress.value < 0.99f || pageIndex != pagerState.currentPage) return@awaitEachGesture
                        singleTap?.cancel()
                        gestureAnimation?.cancel()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var total = Offset.Zero
                        var transformed = false
                        var horizontal = false
                        var vertical = false
                        var moved = false
                        var canceled = false
                        var change = down
                        do {
                            val event = awaitPointerEvent()
                            change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.any { it.isConsumed }) { canceled = true; break }
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val delta = event.calculatePan()
                            total += delta
                            if (total.getDistance() > viewConfiguration.touchSlop) moved = true
                            if (event.changes.count { it.pressed } >= 2) {
                                transformed = true
                                dismissDrag = 0f
                                val nextScale = (scale * event.calculateZoom()).coerceIn(1f, FreeChatAnimation.IMAGE_MAX_SCALE)
                                val ratio = nextScale / scale
                                val focal = event.calculateCentroid(useCurrent = false) - Offset(viewportW / 2, viewportH / 2)
                                pan = ImagePreviewGeometry.clampPan(pan * ratio + delta + focal * (1f - ratio),
                                    nextScale, viewportW, viewportH, fitted)
                                scale = nextScale
                                event.changes.forEach { it.consume() }
                            } else if (scale > 1.01f) {
                                if (moved) transformed = true
                                pan = ImagePreviewGeometry.clampPan(pan + delta, scale, viewportW, viewportH, fitted)
                                if (moved) event.changes.forEach { it.consume() }
                            } else if (!transformed) {
                                if (!vertical && !horizontal) {
                                    if (abs(total.y) > viewConfiguration.touchSlop && abs(total.y) > abs(total.x) * 1.2f) vertical = true
                                    else if (abs(total.x) > viewConfiguration.touchSlop) horizontal = true
                                }
                                if (vertical) {
                                    pan = Offset.Zero
                                    dismissDrag = total.y
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        if (vertical) {
                            singleTap?.cancel()
                            if (!canceled && ImagePreviewGeometry.shouldDismiss(dismissDrag, tracker.calculateVelocity().y,
                                    viewportH, density.density)) close()
                            else if (!motionEnabled) dismissDrag = 0f
                            else gestureAnimation = scope.launch {
                                val back = Animatable(dismissDrag)
                                back.animateTo(0f, FreeChatAnimation.controlTween) { dismissDrag = value }
                            }
                        } else if (!moved && !transformed && !canceled) {
                            val time = change.uptimeMillis
                            if (lastTapTime != 0L && time - lastTapTime <= viewConfiguration.doubleTapTimeoutMillis &&
                                (down.position - lastTapPosition).getDistance() < 48.dp.toPx()) {
                                singleTap?.cancel()
                                lastTapTime = 0L
                                val oldScale = scale
                                val targetScale = if (oldScale > 1.01f) 1f else FreeChatAnimation.IMAGE_DOUBLE_TAP_SCALE
                                val focal = down.position - Offset(viewportW / 2, viewportH / 2)
                                val oldPan = pan
                                if (!motionEnabled) {
                                    scale = targetScale
                                    pan = ImagePreviewGeometry.clampPan(oldPan * (targetScale / oldScale) + focal * (1f - targetScale / oldScale),
                                        targetScale, viewportW, viewportH, fitted)
                                } else gestureAnimation = scope.launch {
                                    Animatable(oldScale).animateTo(targetScale, FreeChatAnimation.materialTween) {
                                        scale = value
                                        pan = ImagePreviewGeometry.clampPan(oldPan * (value / oldScale) + focal * (1f - value / oldScale),
                                            value, viewportW, viewportH, fitted)
                                    }
                                }
                            } else {
                                lastTapTime = time
                                lastTapPosition = down.position
                                singleTap?.cancel()
                                val timeout = viewConfiguration.doubleTapTimeoutMillis
                                singleTap = scope.launch { delay(timeout); if (scale <= 1.01f) close() }
                            }
                        } else singleTap?.cancel()
                    }
                }) {
                val imgW = (origin.width + (fitted.width - origin.width) * p).coerceAtLeast(1f)
                val imgH = (origin.height + (fitted.height - origin.height) * p).coerceAtLeast(1f)
                val x = origin.left + (fitted.left - origin.left) * p
                val y = origin.top + (fitted.top - origin.top) * p
                val drawnScale = 1f + ((if (active) scale else 1f) * (1f - dragFraction * 0.2f) - 1f) * p
                val imageModifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(with(density) { imgW.toDp() }, with(density) { imgH.toDp() })
                    .graphicsLayer {
                        scaleX = drawnScale; scaleY = drawnScale
                        translationX = if (active) pan.x * p else 0f
                        translationY = if (active) (pan.y + dismissDrag) * p else 0f
                    }.clip(RoundedCornerShape(12.dp * (1f - p)))
                if (image.isLocal) LocalImage(image.source, s.imagePreview, imageModifier, ContentScale.Crop, 2048,
                    onImageSize = { width, height -> imageSizes[image.source] = Size(width.toFloat(), height.toFloat()) })
                else AsyncImage(ImageRequest.Builder(context).data(image.source).crossfade(false).build(), s.imagePreview,
                    modifier = imageModifier, contentScale = ContentScale.Crop,
                    onSuccess = { result ->
                        imageSizes[image.source] = Size(result.result.drawable.intrinsicWidth.toFloat(),
                            result.result.drawable.intrinsicHeight.toFloat())
                    })
                }
            }
            if (p > 0.6f) {
                val alpha = ((p - 0.6f) / 0.4f) * (1f - dragFraction)
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
                    .graphicsLayer { this.alpha = alpha }, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { close() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, s.close, tint = Color.White) }
                    IconButton(onClick = { onDownload(currentImage) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Download, s.downloadImage, tint = Color.White) }
                }
                if (images.size > 1) {
                    fun pageTo(index: Int) {
                        singleTap?.cancel()
                        gestureAnimation?.cancel()
                        scope.launch {
                            if (motionEnabled) pagerState.animateScrollToPage(index, animationSpec = FreeChatAnimation.materialTween)
                            else pagerState.scrollToPage(index)
                        }
                    }
                    Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp)
                        .graphicsLayer { this.alpha = alpha }, verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { pageTo(pagerState.currentPage - 1) }, modifier = Modifier.size(48.dp),
                            enabled = pagerState.currentPage > 0 && !pagerState.isScrollInProgress && !closing) {
                            Icon(Icons.Filled.KeyboardArrowLeft, s.previousImage, tint = Color.White)
                        }
                        Text("${pagerState.currentPage + 1}/${images.size}", color = Color.White,
                            style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { pageTo(pagerState.currentPage + 1) }, modifier = Modifier.size(48.dp),
                            enabled = pagerState.currentPage < images.lastIndex && !pagerState.isScrollInProgress && !closing) {
                            Icon(Icons.Filled.KeyboardArrowRight, s.nextImage, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
