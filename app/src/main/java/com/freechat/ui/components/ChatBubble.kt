package com.freechat.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.freechat.data.TtsController
import com.freechat.i18n.LocalStrings
import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalChatFontFamily
import com.freechat.ui.theme.LocalLatinFontFamily
import com.freechat.ui.theme.LocalFontScale
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.io.File
import java.io.FileOutputStream
import java.net.URL

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: Message,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    liveThinkingMs: Long = 0L,
    isThinking: Boolean = false,
    showThinking: Boolean = true,
    onDelete: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onSpeak: (() -> Unit)? = null,
    onQuote: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val isUser = message.role == Role.USER
    val isCompanion = message.mode == com.freechat.model.ChatMode.COMPANION
    val context = LocalContext.current
    val scale = LocalFontScale.current  // 字号联动：气泡宽度随字号缩放
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    var fullscreenIsLocal by remember { mutableStateOf(false) }
    var thumbnailRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // 按 URL 记录每张生成图片的屏幕位置，展开动画从正确的缩略图位置弹出
    val thumbnailRects = remember { mutableMapOf<String, androidx.compose.ui.geometry.Rect>() }

    // 复制消息到剪贴板：AI 回复先去掉 Markdown 标识符再复制（纯文本，排列与显示一致）
    val copyMessage = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = if (isUser) message.content else markdownToPlainText(message.content)
        clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
        Toast.makeText(context, s.copied, Toast.LENGTH_SHORT).show()
    }

    // 语音朗读状态 — 播放中/合成中
    val playingId by TtsController.playingMessageId.collectAsState()
    val loadingId by TtsController.loadingMessageId.collectAsState()
    val paused by TtsController.isPaused.collectAsState()
    val playFraction by TtsController.playFraction.collectAsState()
    val bufferFraction by TtsController.bufferFraction.collectAsState()
    val isBuffering by TtsController.isBuffering.collectAsState()
    val isSpeaking = playingId == message.id
    val isSpeechLoading = loadingId == message.id

    // 删除确认框 + 淡出收起动画
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    LaunchedEffect(dismissing) {
        if (dismissing) {
            delay(280)  // 等 exit 动画（fadeOut + shrinkVertically 260ms）播完再真正删除
            onDelete?.invoke()
        }
    }

    // ===== 删除确认框 =====
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(s.deleteMessage, color = colors.TextPrimary) },
            text = { Text(s.deleteMessageConfirm, color = colors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    dismissing = true
                }) { Text(s.confirm, color = colors.ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(s.cancel, color = colors.TextSecondary) }
            },
            containerColor = colors.Surface
        )
    }

    // ===== 全屏图片预览 =====
    if (fullscreenImage != null) {
        ImagePreviewDialog(
            imageSource = if (fullscreenIsLocal) File(fullscreenImage!!) else fullscreenImage!!,
            isLocal = fullscreenIsLocal,
            downloadRef = fullscreenImage!!,
            originRect = thumbnailRect,
            onDismiss = { fullscreenImage = null }
        )
    }

    AnimatedVisibility(
        visible = !dismissing,
        enter = slideInVertically(
            animationSpec = FreeChatAnimation.messageSlideIn,
            initialOffsetY = { (it * FreeChatAnimation.BUBBLE_RISE_FRACTION).toInt() }
        ) + fadeIn(FreeChatAnimation.messageFadeTween),
        exit = fadeOut(tween(260)) + shrinkVertically(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top
        ),
        modifier = modifier
    ) {
        if (isUser) {
            // ========== 用户消息 — 图片网格（若有）+ 文本气泡 + 长按 ==========
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                // 上传的图片（先横排后纵排，每行最多 3 张；单张更大展示）
                if (message.imagePaths.orEmpty().isNotEmpty()) {
                    val cellSize = if (message.imagePaths.size == 1) 200.dp else 100.dp
                    message.imagePaths.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { path ->
                                LocalImage(
                                    path = path,
                                    contentDescription = s.imagePreview,
                                    modifier = Modifier
                                        .size(cellSize)
                                        .clip(RoundedCornerShape(12.dp))
                                        .onGloballyPositioned { coords ->
                                            val pos = coords.positionInWindow()
                                            val size = coords.size
                                            thumbnailRects[path] = androidx.compose.ui.geometry.Rect(
                                                pos.x, pos.y, pos.x + size.width, pos.y + size.height
                                            )
                                        }
                                        .clickable {
                                            thumbnailRect = thumbnailRects[path]
                                            fullscreenImage = path
                                            fullscreenIsLocal = true
                                        },
                                    contentScale = ContentScale.Crop,
                                    targetMaxDim = 1024
                                )
                            }
                        }
                    }
                }

                // 上传的文件（附件 chip）
                if (message.attachmentPath != null) {
                    FileAttachmentCard(
                        name = message.attachmentName ?: "文件",
                        isUser = true,
                        onClick = { openFile(context, message.attachmentPath!!) }
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // 文本气泡（纯图片消息不显示气泡）— 长按唤醒系统原生文字选中
                if (message.content.isNotBlank()) {
                    Column(horizontalAlignment = Alignment.End) {
                        // 引用缩略（弱化显示，与用户提示词区分）
                        if (message.quotedText != null || message.quotedImagePath != null) {
                            QuotedThumbnail(message.quotedText, message.quotedImagePath, colors)
                            Spacer(Modifier.height(4.dp))
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            // 剧情模式：用户最后一条消息左下角显示「编辑」图标（改写提示词重新生成）
                            if (onEdit != null) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp, bottom = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(onClick = onEdit)
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Edit, s.editMessage, tint = colors.TextTertiary, modifier = Modifier.size(14.dp))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .widthIn(max = (320 * scale).dp)
                                    .clip(
                                        if (isCompanion) RoundedCornerShape(16.dp)
                                        else RoundedCornerShape(
                                            topStart = 16.dp, topEnd = 16.dp,
                                            bottomStart = 16.dp, bottomEnd = 4.dp
                                        )
                                    )
                                    .then(if (isCompanion) Modifier.combinedClickable(onClick = {}, onLongClick = { onQuote?.invoke() }) else Modifier)
                                    .background(if (isCompanion) colors.SurfaceVariant.copy(alpha = 0.6f) else colors.UserBubble)
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                SelectionContainer {
                                    Text(message.content, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalChatFontFamily.current), color = if (isCompanion) colors.TextPrimary else colors.UserBubbleText)
                                }
                            }
                        }
                        // 用户消息操作栏 — 复制 / 删除（拟人模式不显示）
                        if (!isCompanion) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MessageActionIcon(onClick = { copyMessage() }) {
                                    Icon(Icons.Filled.ContentCopy, s.copyMessage, tint = colors.TextTertiary, modifier = Modifier.size(16.dp))
                                }
                                if (onQuote != null) {
                                    MessageActionIcon(onClick = { onQuote() }) {
                                        Icon(Icons.Filled.FormatQuote, "引用", tint = colors.TextTertiary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (onDelete != null) {
                                    MessageActionIcon(onClick = { showDeleteConfirm = true }) {
                                        Icon(Icons.Filled.DeleteOutline, s.deleteMessage, tint = colors.TextTertiary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ========== AI 回复 — 推理卡全宽居中，正文左偏移 ==========
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 24.dp)
            ) {
                // 推理过程显示：与正文同列上下排列（修复思考卡与正文重叠），受「显示思考过程」开关控制
                if (showThinking && message.reasoningContent.orEmpty().isNotEmpty()) {
                    ReasoningBubble(message.reasoningContent.orEmpty(), colors)
                    Spacer(Modifier.height(8.dp))
                }

                // AI 正文
                if (isCompanion) {
                    Text(
                        message.content,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalChatFontFamily.current),
                        color = colors.TextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(onClick = {}, onLongClick = { onQuote?.invoke() })
                            .background(colors.SurfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                } else {
                    MarkdownText(
                        content = message.content,
                        textColor = colors.AiBubbleText,
                        codeBgColor = colors.SurfaceVariant,
                        quoteBarColor = colors.Primary.copy(alpha = 0.5f),
                        dividerColor = colors.Divider
                    )
                }

                // 生成的文档（可点击打开编辑）
                if (message.attachmentPath != null) {
                    Spacer(Modifier.height(8.dp))
                    FileAttachmentCard(
                        name = message.attachmentName ?: "文档",
                        isUser = false,
                        onClick = { openFile(context, message.attachmentPath!!) }
                    )
                }

                // 生图结果 — 点击可全屏预览
                if (message.imageUrls.orEmpty().isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    message.imageUrls.forEach { url ->
                        val isLocalFile = url.startsWith("/")
                        if (isLocalFile) {
                            LocalImage(
                                path = url,
                                contentDescription = s.imagePreview,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .onGloballyPositioned { coords ->
                                        val pos = coords.positionInWindow()
                                        val size = coords.size
                                        thumbnailRects[url] = androidx.compose.ui.geometry.Rect(
                                            pos.x, pos.y, pos.x + size.width, pos.y + size.height
                                        )
                                    }
                                    .clickable {
                                        thumbnailRect = thumbnailRects[url]
                                        fullscreenImage = url
                                        fullscreenIsLocal = true
                                    },
                                contentScale = ContentScale.FillWidth,
                                targetMaxDim = 2048
                            )
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = s.imagePreview,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .onGloballyPositioned { coords ->
                                        val pos = coords.positionInWindow()
                                        val size = coords.size
                                        thumbnailRects[url] = androidx.compose.ui.geometry.Rect(
                                            pos.x, pos.y, pos.x + size.width, pos.y + size.height
                                        )
                                    }
                                    .clickable {
                                        thumbnailRect = thumbnailRects[url]
                                        fullscreenImage = url
                                    },
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }

                // 操作栏 + 模型名（拟人模式不显示，模拟真实气泡对话）
                if (!isCompanion) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MessageActionIcon(onClick = { copyMessage() }) {
                            Icon(Icons.Filled.ContentCopy, s.copyMessage, tint = colors.TextTertiary, modifier = Modifier.size(16.dp))
                        }
                        if (onDelete != null) {
                            MessageActionIcon(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Filled.DeleteOutline, s.deleteMessage, tint = colors.TextTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                        if (onRegenerate != null) {
                            MessageActionIcon(onClick = { onRegenerate() }) {
                                Icon(Icons.Filled.Refresh, s.regenerate, tint = colors.TextTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                        if (onQuote != null) {
                            MessageActionIcon(onClick = { onQuote() }) {
                                Icon(Icons.Filled.FormatQuote, "引用", tint = colors.TextTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                        if (onSpeak != null) {
                            MessageActionIcon(onClick = { onSpeak() }) {
                                if (isSpeechLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        strokeWidth = 2.dp,
                                        color = colors.TextTertiary
                                    )
                                } else {
                                    Icon(
                                        if (isSpeaking) (if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause) else Icons.Filled.VolumeUp,
                                        if (isSpeaking) (if (paused) s.resume else s.pause) else s.speak,
                                        tint = if (isSpeaking) colors.Primary else colors.TextTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            SpeechProgressBar(
                                visible = isSpeaking,
                                fraction = if (isSpeaking) playFraction else 0f,
                                bufferFraction = if (isSpeaking) bufferFraction else 0f,
                                isBuffering = isBuffering,
                                colors = colors
                            )
                        }
                    }

                    // 模型名 + 思考时间
                    if (message.modelName != null) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(message.modelName, style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalLatinFontFamily.current), color = colors.TextTertiary)
                            val timeMs = if (isThinking && liveThinkingMs > 0) liveThinkingMs else message.thinkingTimeMs
                            if (timeMs > 0) {
                                Text(formatThinkingTime(timeMs), style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalLatinFontFamily.current), color = colors.TextTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ========== 全屏图片预览 — 从缩略图展开/缩回 + 手势缩放 ==========
@Composable
private fun ImagePreviewDialog(
    imageSource: Any,
    isLocal: Boolean,
    downloadRef: String,
    originRect: androidx.compose.ui.geometry.Rect?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()

    // 手势状态
    var gestureScale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // 展开/缩回动画进度: 0f=缩略图, 1f=全屏
    var isClosing by remember { mutableStateOf(false) }
    val animProgress = remember { Animatable(0f) }

    // 屏幕尺寸
    val config = LocalConfiguration.current
    val screenW = config.screenWidthDp.dp
    val screenH = config.screenHeightDp.dp
    val screenWPx = with(LocalDensity.current) { screenW.toPx() }
    val screenHPx = with(LocalDensity.current) { screenH.toPx() }

    // 缩略图/起始位置
    val origin = originRect ?: androidx.compose.ui.geometry.Rect(
        screenWPx / 2f - 60f, screenHPx / 2f - 60f,
        screenWPx / 2f + 60f, screenHPx / 2f + 60f
    )
    val originW = origin.width.coerceAtLeast(1f)
    val originH = origin.height.coerceAtLeast(1f)
    val originCX = origin.left + originW / 2f
    val originCY = origin.top + originH / 2f
    val targetCX = screenWPx / 2f
    val targetCY = screenHPx / 2f

    // 启动展开动画
    LaunchedEffect(Unit) {
        animProgress.animateTo(1f, FreeChatAnimation.imageExpandSpring)
    }

    // 缩回动画
    fun startClose() {
        if (!isClosing) {
            isClosing = true
            scope.launch {
                animProgress.animateTo(0f, FreeChatAnimation.imageShrinkTween)
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = { startClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        gestureScale = (gestureScale * zoom).coerceIn(
                            FreeChatAnimation.IMAGE_MIN_SCALE,
                            FreeChatAnimation.IMAGE_MAX_SCALE
                        )
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            gestureScale = if (gestureScale > 1.5f) 1f else FreeChatAnimation.IMAGE_DOUBLE_TAP_SCALE
                        },
                        onTap = {
                            if (gestureScale <= 1.05f) startClose()
                        }
                    )
                }
        ) {
            val p = animProgress.value
            val pEased = CubicBezierEasing(0.32f, 0.0f, 0.67f, 0.0f).transform(p)

            // 图片: 从缩略图位置/尺寸插值到全屏
            val imgW = (originW + (screenWPx - originW) * pEased).coerceAtLeast(1f)
            val imgH = (originH + (screenHPx - originH) * pEased).coerceAtLeast(1f)
            val imgX = originCX + (targetCX - originCX) * pEased - imgW / 2f
            val imgY = originCY + (targetCY - originCY) * pEased - imgH / 2f
            val cornerRadius = 12.dp * (1f - pEased)

            val density = LocalDensity.current
            val imgModifier = Modifier
                .offset { IntOffset(imgX.roundToInt(), imgY.roundToInt()) }
                .size(with(density) { imgW.toDp() }, with(density) { imgH.toDp() })
                .clip(RoundedCornerShape(cornerRadius))
                .graphicsLayer {
                    scaleX = gestureScale
                    scaleY = gestureScale
                    translationX = offsetX
                    translationY = offsetY
                }

            if (isLocal) {
                LocalImage(
                    path = downloadRef,
                    contentDescription = s.imagePreview,
                    modifier = imgModifier,
                    contentScale = ContentScale.Fit,
                    targetMaxDim = 2048
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageSource)
                        .crossfade(true)
                        .build(),
                    contentDescription = s.imagePreview,
                    modifier = imgModifier,
                    contentScale = ContentScale.Fit
                )
            }

            // 顶层按钮（动画完成后显示）
            if (p > 0.6f) {
                val btnAlpha = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)

                // 右上角下载按钮（无灰色背景）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 16.dp)
                        .graphicsLayer { alpha = btnAlpha }
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (isLocal) saveLocalImage(context, downloadRef)
                                else downloadImage(context, downloadRef)
                            }
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = s.downloadImage,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 左上角关闭按钮
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(top = 8.dp, start = 16.dp)
                        .graphicsLayer { alpha = btnAlpha }
                ) {
                    IconButton(
                        onClick = { startClose() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 保存图片字节到 Pictures/FreeChat（API 29+ 用 MediaStore，否则直接写文件） */
private suspend fun saveImageBytes(context: Context, bytes: ByteArray) {
    withContext(Dispatchers.IO) {
        try {
            val filename = "FreeChat_${System.currentTimeMillis()}.jpg"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/FreeChat"
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                )
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        out.write(bytes)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "图片已保存到 Pictures/FreeChat", Toast.LENGTH_SHORT).show()
                    }
                } ?: withContext(Dispatchers.Main) {
                    Toast.makeText(context, "保存失败，请检查存储空间", Toast.LENGTH_SHORT).show()
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "FreeChat"
                )
                dir.mkdirs()
                FileOutputStream(File(dir, filename)).use { it.write(bytes) }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "图片已保存到 Pictures/FreeChat", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** 下载图片到 Pictures 目录 */
private suspend fun downloadImage(context: Context, url: String) {
    withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            val bytes = connection.getInputStream().use { it.readBytes() }
            saveImageBytes(context, bytes)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** 保存本地图片（用户上传的图）到 Pictures 目录 */
private suspend fun saveLocalImage(context: Context, path: String) {
    withContext(Dispatchers.IO) {
        try {
            val src = File(path)
            if (!src.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "图片不存在或已被删除", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }
            saveImageBytes(context, src.readBytes())
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** 推理过程折叠卡片 — 折叠时只显示标题+箭头，不显示任何思考内容 */
@Composable
private fun ReasoningBubble(reasoning: String, colors: FreeChatColors) {
    val s = LocalStrings.current
    val scale = LocalFontScale.current
    val advancedMaterial = LocalAdvancedMaterial.current
    var expanded by remember { mutableStateOf(true) }

    // 卡片整体居中、左右对称（限制最大宽度并水平居中）；高级材质下磨砂哑光玻璃，去脑图标
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = (340 * scale).dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (advancedMaterial) colors.Surface.copy(alpha = 0.7f) else colors.SurfaceVariant.copy(alpha = 0.6f))
                .then(if (advancedMaterial) Modifier.border(1.dp, colors.Divider.copy(alpha = 0.3f), RoundedCornerShape(10.dp)) else Modifier)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.thinkingProcess, style = MaterialTheme.typography.labelSmall, color = colors.Primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    null,
                    tint = colors.TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Text(
                    reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.TextSecondary,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                )
            }
        }
    }
}

private fun formatThinkingTime(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> String.format("%.1fs", ms / 1000.0)
    else -> "${ms / 60_000}m${(ms % 60_000) / 1000}s"
}

/** 消息发送时间（具体到分）：今天只显示时分，跨天/跨年带日期 */
fun formatMessageTime(timestamp: Long): String {
    val now = java.util.Calendar.getInstance()
    val msg = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val time = String.format("%02d:%02d", msg.get(java.util.Calendar.HOUR_OF_DAY), msg.get(java.util.Calendar.MINUTE))
    val sameDay = now.get(java.util.Calendar.YEAR) == msg.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == msg.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay) return time
    val sameYear = now.get(java.util.Calendar.YEAR) == msg.get(java.util.Calendar.YEAR)
    return if (sameYear) String.format("%d月%d日 %s", msg.get(java.util.Calendar.MONTH) + 1, msg.get(java.util.Calendar.DAY_OF_MONTH), time)
    else String.format("%d年%d月%d日 %s", msg.get(java.util.Calendar.YEAR), msg.get(java.util.Calendar.MONTH) + 1, msg.get(java.util.Calendar.DAY_OF_MONTH), time)
}

/** 用 FileProvider 打开本地文件（docx/xlsx/pptx/pdf 等），交给系统对应应用编辑 */
private fun openFile(context: Context, path: String) {
    try {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, "文件不存在或已被删除", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.freechat.fileprovider", file)
        val mime = when (file.extension.lowercase()) {
            "pptx", "ppt" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xlsx", "xls" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "docx", "doc" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "pdf" -> "application/pdf"
            "txt", "md" -> "text/plain"
            else -> "*/*"
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开文件：${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/** 文件附件卡片（用户上传 / AI 生成的原生文档） */
@Composable
private fun FileAttachmentCard(name: String, isUser: Boolean, onClick: () -> Unit) {
    val colors = LocalFreeChatColors.current
    val icon = when (name.substringAfterLast('.', "").lowercase()) {
        "pptx", "ppt" -> Icons.Filled.Slideshow
        "xlsx", "xls" -> Icons.Filled.TableChart
        "docx", "doc" -> Icons.Filled.Description
        "pdf" -> Icons.Filled.PictureAsPdf
        "txt", "md" -> Icons.Filled.Notes
        else -> Icons.Filled.AttachFile
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.SurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = colors.Primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(if (isUser) "附件" else "点击打开编辑", style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary)
            }
        }
    }
}

/** 引用缩略（弱化显示）：左竖条 + 灰色文字 / 图片小缩略图，与用户提示词区分 */
@Composable
private fun QuotedThumbnail(quotedText: String?, quotedImagePath: String?, colors: FreeChatColors) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.SurfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(2.dp).height(18.dp).clip(RoundedCornerShape(1.dp)).background(colors.TextTertiary.copy(alpha = 0.6f)))
            Spacer(Modifier.width(6.dp))
            if (quotedImagePath != null) {
                LocalImage(
                    path = quotedImagePath,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                    targetMaxDim = 128
                )
            } else {
                Text(
                    quotedText ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 11.sp),
                    color = colors.TextTertiary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 消息下方的小操作图标按钮 — 无背景、低调，点击区略大于图标便于点按 */
@Composable
private fun MessageActionIcon(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** 朗读进度条 — 喇叭右侧淡入展开，可拖动 seek（范围=已合成部分，Plan B 灰条），缓冲时白条呼吸 */
@Composable
private fun SpeechProgressBar(
    visible: Boolean,
    fraction: Float,
    bufferFraction: Float,
    isBuffering: Boolean,
    colors: FreeChatColors
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + expandHorizontally(tween(240, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(180)) + shrinkHorizontally(tween(200, easing = FastOutSlowInEasing))
    ) {
        // 缓冲时白条 alpha 呼吸提示（未缓冲时不用该动画，transition 懒驱动无额外开销）
        val bufferTransition = rememberInfiniteTransition(label = "ttsBuffer")
        val bufferAlpha by bufferTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
            label = "ttsBufferAlpha"
        )
        val barAlpha = if (isBuffering) bufferAlpha else 1f

        Box(
            modifier = Modifier
                .width(76.dp)
                .height(18.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            TtsController.seekTo((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                        },
                        onHorizontalDrag = { change, _ ->
                            TtsController.seekTo((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(colors.Divider)
            ) {
                // 灰条：已合成缓存区（Plan B 灰条）
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(bufferFraction.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(colors.Primary.copy(alpha = 0.28f))
                )
                // 白条：播放进度
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(colors.Primary.copy(alpha = barAlpha))
                )
            }
        }
    }
}
