package com.freechat.ui.components

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.freechat.i18n.LocalStrings
import com.freechat.model.Message
import com.freechat.model.Role as ChatRole
import com.freechat.sync.ImageSync
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.data.GenerationPhase
import com.freechat.ui.theme.LocalFreeChatColors
import java.io.File
import kotlin.math.roundToInt

/**
 * Height follows the animated aspect in the layout phase: the reveal clock invalidates
 * measurement of this slot only — no per-frame recomposition of the message.
 */
private fun Modifier.morphingAspect(aspect: () -> Float): Modifier = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val height = (width / aspect().coerceIn(.05f, 20f)).roundToInt()
        .coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

/**
 * Sizes to the image's fit rect inside the slot — the exact rect the dot mask converges onto —
 * so the rounded clip lands on the image's own edges at every frame of the reveal.
 */
private fun Modifier.fitMorph(imageAspect: () -> Float): Modifier = layout { measurable, constraints ->
    val fitted = ImageRevealPolicy.fitIn(
        constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), imageAspect())
    val width = (constraints.maxWidth * fitted.first).roundToInt()
        .coerceIn(constraints.minWidth, constraints.maxWidth)
    val height = (constraints.maxHeight * fitted.second).roundToInt()
        .coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

object ImageDisplayPolicy {
    fun aspectRatio(width: Int, height: Int): Float =
        if (width > 0 && height > 0) (width.toFloat() / height).coerceIn(0.05f, 20f) else 1f
    fun isLocal(source: String) = source.startsWith("/") || File(source).isAbsolute

    /** Also handles saved rows from versions that persisted both original and compressed cache. Call on IO. */
    fun generatedImages(message: Message): List<String> = ImageSync.canonicalEntries(message.imageUrls.orEmpty())

    /** Old image-only records may have lost the result or arrived without device-local media. */
    fun hasMissingResult(message: Message): Boolean =
        message.role == ChatRole.ASSISTANT && !message.isStreaming && !message.failed &&
            message.imageUrls.isEmpty() && message.imagePaths.isEmpty() && message.attachmentPath == null &&
            (message.sceneVisualization || (message.content.isBlank() &&
                message.modelName.orEmpty().contains(Regex("image|seedream|dall[-_ ]?e", RegexOption.IGNORE_CASE))))
}

private enum class ImageDisplayState { LOADING, READY, ERROR }

@Composable
fun MissingGeneratedImageNotice(modifier: Modifier = Modifier) {
    val colors = LocalFreeChatColors.current
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = colors.TextSecondary)
        Text(LocalStrings.current.generatedImageNoResult, color = colors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium)
    }
}

/** A failed or still-loading image must never collapse into an empty assistant row. */
@Composable
fun GeneratedImage(source: String?, modifier: Modifier = Modifier, generating: Boolean = false,
    phase: GenerationPhase = GenerationPhase.IDLE, fromGeneration: Boolean = false, onClick: () -> Unit = {}) {
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    val context = LocalContext.current
    val local = source?.let(ImageDisplayPolicy::isLocal) == true
    var state by remember(source) { mutableStateOf(ImageDisplayState.LOADING) }
    // 画幅账本：测过一次的图直接以真画幅首帧成形，不从 1:1 起步。
    val knownAspect = source?.let(ImageRevealLedger::aspectOf)
    var ratio by remember(source) { mutableFloatStateOf(knownAspect ?: 1f) }
    var attempt by remember(source) { mutableIntStateOf(0) }
    val motion = LocalMotionEnabled.current
    // 1.1.21.5：到达过渡 1:1→真画幅，比例只认实测像素（onImageSize/onState），不猜提示词。
    // 1.1.26 追修：这条过渡只在「生成完成到达」演一次（revealStart）；历史图/滑回/回看
    // 一律直接终态——不重播占位粒子与变形（终态=真画幅首帧，画幅走 ImageRevealLedger）。
    val reveal = remember(source) { Animatable(ImageRevealPolicy.revealStart(fromGeneration, knownAspect != null)) }
    var overlayFinished by remember(source) { mutableStateOf(!fromGeneration && knownAspect != null) }
    LaunchedEffect(state, generating, source, motion) {
        if (state == ImageDisplayState.READY && !generating) {
            if (reveal.value < 1f) {
                // 生成到达走全长到达曲线；历史图首见（画幅未知）只求快速落位不跳变。
                if (motion) reveal.animateTo(1f,
                    if (fromGeneration) FreeChatAnimation.imageCompletion else FreeChatAnimation.materialTween)
                else reveal.snapTo(1f)
            }
            overlayFinished = true
        }
    }
    val shape = RoundedCornerShape(ImageRevealPolicy.CornerDp.dp)
    Box(modifier.fillMaxWidth().morphingAspect { ImageRevealPolicy.slotRatio(reveal.value, ratio) }.clip(shape)
        .then(if (fromGeneration) Modifier else Modifier.background(colors.SurfaceVariant))
        .clickable(enabled = state == ImageDisplayState.READY, role = Role.Button, onClickLabel = s.imagePreview, onClick = onClick)) {
      Box(Modifier.align(Alignment.Center).fitMorph { ratio }
          .clip(shape).graphicsLayer { alpha = ImageRevealPolicy.imageAlpha(reveal.value) }) {
        key(source, attempt) {
            if (source == null) Spacer(Modifier.matchParentSize())
            else if (local) LocalImage(source, s.imagePreview, Modifier.matchParentSize(), ContentScale.Fit,
                targetMaxDim = 2048,
                onImageSize = { w, h ->
                    ratio = ImageDisplayPolicy.aspectRatio(w, h)
                    ImageRevealLedger.record(source, w, h)
                },
                onLoadResult = { state = if (it) ImageDisplayState.READY else ImageDisplayState.ERROR })
            else AsyncImage(
                model = remember(source, attempt) {
                    ImageRequest.Builder(context).data(source)
                        .crossfade(0).build()
                },
                contentDescription = s.imagePreview, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Fit,
                onState = { imageState ->
                    when (imageState) {
                        is AsyncImagePainter.State.Success -> {
                            val drawable = imageState.result.drawable
                            ratio = ImageDisplayPolicy.aspectRatio(drawable.intrinsicWidth, drawable.intrinsicHeight)
                            ImageRevealLedger.record(source, drawable.intrinsicWidth, drawable.intrinsicHeight)
                            state = ImageDisplayState.READY
                        }
                        is AsyncImagePainter.State.Error -> state = ImageDisplayState.ERROR
                        else -> Unit
                    }
                })
        }
      }
        // 占位粒子只属于「生成槽位」——历史图/回看不显示占位图（1.1.26 追修）。
        if (fromGeneration && (state == ImageDisplayState.LOADING || (!overlayFinished && state != ImageDisplayState.ERROR))) {
            ImageGenerationParticles(colors, Modifier.matchParentSize().graphicsLayer {
                alpha = ImageRevealPolicy.dotAlpha(reveal.value)
            }, phase, completion = { reveal.value }, resultAspect = ratio)
        }
        if (state == ImageDisplayState.ERROR) {
            Column(Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = colors.TextSecondary)
                Text(if (local) s.generatedImageMissing else s.generatedImageUnavailable,
                    color = colors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                if (!local) TextButton(onClick = { state = ImageDisplayState.LOADING; attempt++ }) {
                    Text(s.retryImageLoading, color = colors.Primary)
                }
            }
        }
    }
}
