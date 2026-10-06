package com.freechat.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.freechat.i18n.LocalStrings
import com.freechat.model.Message
import com.freechat.model.Role as ChatRole
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.theme.LocalFreeChatColors
import java.io.File

object ImageDisplayPolicy {
    fun aspectRatio(width: Int, height: Int): Float =
        if (width > 0 && height > 0) (width.toFloat() / height).coerceIn(0.05f, 20f) else 1f
    fun isLocal(source: String) = source.startsWith("/") || File(source).isAbsolute

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
fun GeneratedImage(source: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    val context = LocalContext.current
    val local = ImageDisplayPolicy.isLocal(source)
    var state by remember(source) { mutableStateOf(ImageDisplayState.LOADING) }
    var ratio by remember(source) { mutableFloatStateOf(1f) }
    var attempt by remember(source) { mutableIntStateOf(0) }
    Box(modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(12.dp))
        .background(colors.SurfaceVariant)
        .clickable(enabled = state == ImageDisplayState.READY, role = Role.Button, onClickLabel = s.imagePreview, onClick = onClick)) {
        key(source, attempt) {
            if (local) LocalImage(source, s.imagePreview, Modifier.matchParentSize(), ContentScale.Fit,
                targetMaxDim = 2048,
                onImageSize = { w, h -> ratio = ImageDisplayPolicy.aspectRatio(w, h) },
                onLoadResult = { state = if (it) ImageDisplayState.READY else ImageDisplayState.ERROR })
            else AsyncImage(
                model = ImageRequest.Builder(context).data(source).crossfade(FreeChatAnimation.DURATION_REPLACE).build(),
                contentDescription = s.imagePreview, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Fit,
                onState = { imageState ->
                    when (imageState) {
                        is AsyncImagePainter.State.Success -> {
                            val drawable = imageState.result.drawable
                            ratio = ImageDisplayPolicy.aspectRatio(drawable.intrinsicWidth, drawable.intrinsicHeight)
                            state = ImageDisplayState.READY
                        }
                        is AsyncImagePainter.State.Error -> state = ImageDisplayState.ERROR
                        else -> Unit
                    }
                })
        }
        if (state == ImageDisplayState.LOADING) {
            ImageGenerationParticles(colors, Modifier.matchParentSize())
            Text(s.generatedImageLoading, color = colors.TextPrimary, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center).padding(24.dp))
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
