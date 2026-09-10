package com.freechat.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.model.FavoriteItem
import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.ui.components.LocalImage
import com.freechat.ui.components.MarkdownText
import com.freechat.ui.components.cjkLineBreak
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.frostedGlass
import com.freechat.viewmodel.ChatViewModel
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 收藏详情：
 * - 仅收藏了一条独立消息 → 只显示这条本身，平铺在背景上（无气泡框）。
 * - 连续多条消息都被收藏 → 合并成一个「对话」，逐条气泡框显示并分别标注主语（你 / FreeChat）。
 * 高级材质下：标题栏真高斯模糊；底部「跳转到原对话」悬浮按钮做透光不透物磨砂玻璃（同输入框质感）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesDetailScreen(
    viewModel: ChatViewModel,
    item: FavoriteItem,
    isDark: Boolean,
    onBack: () -> Unit,
    onUnfavorite: () -> Unit,
    onOpenOriginal: () -> Unit
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val density = LocalDensity.current
    val hazeState = rememberHazeState()
    // 连续被收藏的消息段（向前向后扩展相邻 favorited 消息）
    val run = remember(item) { viewModel.favoriteRun(item.conversation.id, item.message.id) }
    val multi = run.size >= 2
    var showUnfavoriteConfirm by remember { mutableStateOf(false) }
    // 图片全屏预览状态
    var previewImage by remember { mutableStateOf<String?>(null) }
    var previewIsLocal by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val onPreviewImage: (String, Boolean) -> Unit = { src, isLocal ->
        previewImage = src
        previewIsLocal = isLocal
    }
    val onOpenAttachment: (String?) -> Unit = { path ->
        if (path != null) openAttachment(context, path)
    }

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val topFadeZoneDp = HazeSpec.TopFadeZoneDp
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + topFadeZoneDp).toPx() }
    val bottomBarHeightPx = with(density) { HazeSpec.BottomFadeHeightDp.toPx() }

    Box(Modifier.fillMaxSize().background(colors.Background)) {
        // ===== 内容区（可滚动，高级材质下作为模糊源，沉浸到标题栏之下） =====
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).background(colors.Background) else Modifier),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = statusBarHeightDp + titleBarAreaDp + 8.dp,
                bottom = 120.dp
            ),
            verticalArrangement = if (multi) Arrangement.spacedBy(12.dp) else Arrangement.Top
        ) {
            if (multi) {
                // 连续收藏 = 对话，逐条气泡框
                items(run, key = { it.id }) { msg ->
                    DetailMessageBubble(msg, colors, s, onPreviewImage, onOpenAttachment)
                }
            } else {
                // 单条收藏 = 平铺文字流，无气泡框
                item(key = "flat") {
                    run.firstOrNull()?.let { msg -> DetailMessageFlat(msg, colors, s, onPreviewImage, onOpenAttachment) }
                }
            }
        }

        // ===== 顶部标题栏背景（高级材质开=真模糊+渐变渐隐，关=纯色） =====
        if (advancedMaterial) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp + topFadeZoneDp)
                    .hazeEffect(state = hazeState) {
                        blurRadius = HazeSpec.TopBlurRadius
                        inputScale = HazeInputScale.None
                        backgroundColor = Color.Transparent
                        progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 1f, endY = topBarHeightPx, endIntensity = 0f)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* 隔离点击：顶部模糊区下的内容不可点 */ }
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp)
                    .background(colors.Background)
            )
        }

        // ===== 悬浮标题栏（返回 + 标题 + 右侧已收藏图标） =====
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.TextPrimary)
            }
            Text(
                s.favoriteDetail,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.weight(1f))
            // 已收藏图标：爱心划一道（HeartBroken），点击弹确认后取消收藏
            IconButton(onClick = { showUnfavoriteConfirm = true }) {
                Icon(
                    Icons.Filled.HeartBroken,
                    contentDescription = s.unfavorite,
                    tint = colors.ErrorRed,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // ===== 底部渐进模糊（高级材质）：贴屏幕最底，文字向下滑动渐隐（底部糊→按钮顶清），与 Chat 页同规格 =====
        if (advancedMaterial) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(HazeSpec.BottomFadeHeightDp)
                    .hazeEffect(state = hazeState) {
                        blurRadius = HazeSpec.BottomBlurRadius
                        inputScale = HazeInputScale.None
                        backgroundColor = Color.Transparent
                        progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 0f, endY = bottomBarHeightPx, endIntensity = 1f)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* 隔离点击：底部模糊区下的内容不可点 */ }
            )
        }

        // ===== 底部悬浮「跳转到原对话」按钮（磨砂哑光玻璃，圆角/位置与 Chat 输入框一致） =====
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            if (advancedMaterial) {
                // 磨砂哑光玻璃：与 Chat 输入框同款 frostedGlass（Haze 真模糊 + 白 tint + 描边）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .frostedGlass(hazeState, isDark, RoundedCornerShape(50.dp), elevation = 6.dp)
                        .clickable(onClick = onOpenOriginal),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        s.openOriginal,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.Primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Button(
                    onClick = onOpenOriginal,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.Primary,
                        contentColor = colors.OnPrimary
                    )
                ) {
                    Text(s.openOriginal, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // 取消收藏确认弹窗
    if (showUnfavoriteConfirm) {
        AlertDialog(
            onDismissRequest = { showUnfavoriteConfirm = false },
            containerColor = colors.Surface,
            title = { Text(s.unfavorite, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text(s.confirmUnfavorite, color = colors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showUnfavoriteConfirm = false; onUnfavorite() }) {
                    Text(s.confirm, color = colors.ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnfavoriteConfirm = false }) {
                    Text(s.cancel, color = colors.TextSecondary)
                }
            }
        )
    }

    // 图片全屏预览（点击图片弹出，点任意处关闭）
    if (previewImage != null) {
        FullScreenImagePreview(
            source = previewImage!!,
            isLocal = previewIsLocal,
            onDismiss = { previewImage = null }
        )
    }
}

/** 对话模式：带气泡框的单条消息（主语徽章 + 时间 + 正文） */
@Composable
private fun DetailMessageBubble(msg: Message, colors: FreeChatColors, s: AppStrings, onPreviewImage: (String, Boolean) -> Unit, onOpenAttachment: (String?) -> Unit) {
    val isUser = msg.role == Role.USER
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isUser) colors.SurfaceVariant else colors.Surface)
            .padding(14.dp)
    ) {
        DetailMessageHeader(msg, colors, s)
        Spacer(Modifier.height(10.dp))
        MessageBody(msg, colors, isUser, onPreviewImage, onOpenAttachment)
    }
}

/** 单条模式：无气泡框，直接平铺到背景 */
@Composable
private fun DetailMessageFlat(msg: Message, colors: FreeChatColors, s: AppStrings, onPreviewImage: (String, Boolean) -> Unit, onOpenAttachment: (String?) -> Unit) {
    val isUser = msg.role == Role.USER
    Column(Modifier.fillMaxWidth()) {
        DetailMessageHeader(msg, colors, s)
        Spacer(Modifier.height(10.dp))
        MessageBody(msg, colors, isUser, onPreviewImage, onOpenAttachment)
    }
}

/** 主语徽章（你 / FreeChat，胶囊样式与正文明显区分）+ 发送时间 */
@Composable
private fun DetailMessageHeader(msg: Message, colors: FreeChatColors, s: AppStrings) {
    val isUser = msg.role == Role.USER
    val time = remember(msg.timestamp) {
        SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINESE).format(Date(msg.timestamp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colors.Primary.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                if (isUser) s.roleUser else s.roleAi,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.Primary
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(time, style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary)
    }
}

/** 正文：用户纯文本 / AI Markdown 渲染，含图片与附件 */
@Composable
private fun MessageBody(
    msg: Message,
    colors: FreeChatColors,
    isUser: Boolean,
    onPreviewImage: (String, Boolean) -> Unit,
    onOpenAttachment: (String?) -> Unit
) {
    if (isUser) {
        Text(
            msg.content,
            style = MaterialTheme.typography.bodyMedium.copy(lineBreak = cjkLineBreak),
            color = colors.TextPrimary
        )
    } else {
        MarkdownText(
            content = msg.content,
            textColor = colors.AiBubbleText,
            codeBgColor = colors.SurfaceVariant,
            quoteBarColor = colors.Primary.copy(alpha = 0.5f),
            dividerColor = colors.Divider
        )
    }

    // 图片（AI 生图 remote URL / 本地文件 + 用户上传本地图）：本地图固定高度 Crop（与 Chat 页同款，稳定显示），remote 用 Coil FillWidth 自动等高；点击全屏预览
    if (msg.imageUrls.isNotEmpty() || msg.imagePaths.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            msg.imageUrls.forEach { url ->
                if (url.startsWith("/")) {
                    LocalImage(
                        path = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)).clickable { onPreviewImage(url, true) },
                        contentScale = ContentScale.Crop,
                        targetMaxDim = 2048
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPreviewImage(url, false) },
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
            msg.imagePaths.forEach { path ->
                LocalImage(
                    path = path,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)).clickable { onPreviewImage(path, true) },
                    contentScale = ContentScale.Crop,
                    targetMaxDim = 2048
                )
            }
        }
    }

    // 附件（点击打开编辑）
    if (msg.attachmentName != null) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.SurfaceVariant.copy(alpha = 0.6f))
                .clickable { onOpenAttachment(msg.attachmentPath) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📎 ${msg.attachmentName}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(
                "点击打开",
                style = MaterialTheme.typography.labelSmall,
                color = colors.TextTertiary
            )
        }
    }
}

/** 全屏图片预览：点任意处关闭 */
@Composable
private fun FullScreenImagePreview(source: String, isLocal: Boolean, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            if (isLocal) {
                LocalImage(
                    path = source,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                    targetMaxDim = 2048
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(source).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

/** 打开附件（docx/xlsx/pptx/pdf 等），交给系统对应应用 */
private fun openAttachment(context: Context, path: String) {
    try {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, "文件不存在或已被删除", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "com.freechat.fileprovider", file)
        val mime = when (file.extension.lowercase()) {
            "pptx", "ppt" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xlsx", "xls" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "docx", "doc" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "pdf" -> "application/pdf"
            "txt", "md" -> "text/plain"
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开文件：${e.message}", Toast.LENGTH_SHORT).show()
    }
}
