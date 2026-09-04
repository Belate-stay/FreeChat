package com.freechat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.LocalStrings
import com.freechat.model.Conversation
import com.freechat.model.ChatMode
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.frostedCard
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerContent(
    conversations: List<Conversation>,
    currentId: String?,
    isDark: Boolean,
    searchQuery: String,
    searchResults: List<Conversation>,
    onSearchQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectConversation: (Conversation) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    onRenameConversation: (Conversation, String) -> Unit,
    onPinConversation: (Conversation) -> Unit,
    onOpenSettings: () -> Unit,
    onSettingsRowPositioned: ((Rect) -> Unit)? = null,
    onNewChatRect: ((Rect) -> Unit)? = null
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // 侧滑页独立 Haze 状态：历史对话列表作为模糊源，顶部/底部做与 Chat 页同规格的高斯模糊
    val drawerHazeState = rememberHazeState()

    val filteredConversations = if (searchQuery.isBlank()) conversations else searchResults

    // 搜索时不分组；平时按时间分类分组展示（@Composable 函数，须在 LazyColumn 构建 lambda 之外调用）
    val groups = if (searchQuery.isBlank()) groupConversations(filteredConversations) else null

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp).toPx() }
    val bottomBarHeightPx = with(density) { HazeSpec.BottomFadeHeightDp.toPx() }

    if (advancedMaterial) {
        // ===== 高级材质：沉浸式文字流 + 上下高斯模糊，搜索/新对话融入列表 =====
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.Background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = drawerHazeState)
                    .background(colors.Background),
                contentPadding = PaddingValues(
                    start = 8.dp, end = 8.dp,
                    top = statusBarHeightDp + titleBarAreaDp + 24.dp,
                    bottom = 96.dp
                )
            ) {
                item(key = "search") {
                    SearchBox(searchQuery, onSearchQueryChange, drawerHazeState, Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(10.dp))
                }
                item(key = "new_chat") {
                    NewChatButton(onNewChat, onNewChatRect, drawerHazeState, Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(12.dp))
                }
                conversationItems(filteredConversations, groups, currentId, isDark, onSelectConversation, onDeleteConversation, onRenameConversation, onPinConversation, searchQuery, s, drawerHazeState)
            }

            // 顶部渐变模糊（与 Chat 页同规格）
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp)
                    .hazeEffect(state = drawerHazeState) {
                        blurRadius = HazeSpec.TopBlurRadius
                        inputScale = HazeInputScale.None
                        backgroundColor = Color.Transparent
                        progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 1f, endY = topBarHeightPx, endIntensity = 0f)
                    }
            )

            // 底部渐变模糊（与 Chat 页同规格）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(HazeSpec.BottomFadeHeightDp)
                    .hazeEffect(state = drawerHazeState) {
                        blurRadius = HazeSpec.BottomBlurRadius
                        inputScale = HazeInputScale.None
                        backgroundColor = Color.Transparent
                        progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 0f, endY = bottomBarHeightPx, endIntensity = 1f)
                    }
            )

            // 悬浮设置按钮（左下角磨砂胶囊）
            FloatingSettingsButton(
                onClick = onOpenSettings,
                onPositioned = onSettingsRowPositioned,
                hazeState = drawerHazeState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 20.dp)
            )
        }
    } else {
        // ===== 非高级材质：原布局（固定搜索/新对话/底部设置，非沉浸） =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.Surface)
                .statusBarsPadding()
                .padding(start = 8.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            // 留出顶部空间给共享 FreeChat 标题
            Spacer(Modifier.height(36.dp))
            Spacer(Modifier.height(16.dp))

            SearchBox(searchQuery, onSearchQueryChange, drawerHazeState, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(10.dp))
            NewChatButton(onNewChat, onNewChatRect, drawerHazeState, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))

            HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    conversationItems(filteredConversations, groups, currentId, isDark, onSelectConversation, onDeleteConversation, onRenameConversation, onPinConversation, searchQuery, s, drawerHazeState)
                }
            }

            HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            SettingsRowFull(onOpenSettings, onSettingsRowPositioned)
        }
    }
}

/** 对话列表（含空态），供两种布局复用 */
private fun androidx.compose.foundation.lazy.LazyListScope.conversationItems(
    filteredConversations: List<Conversation>,
    groups: List<ConvGroup>?,
    currentId: String?,
    isDark: Boolean,
    onSelectConversation: (Conversation) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    onRenameConversation: (Conversation, String) -> Unit,
    onPinConversation: (Conversation) -> Unit,
    searchQuery: String,
    s: com.freechat.i18n.AppStrings,
    hazeState: HazeState
) {
    if (filteredConversations.isEmpty()) {
        item(key = "empty") {
            val colors = LocalFreeChatColors.current
            Box(
                modifier = Modifier
                    .fillParentMaxWidth()
                    .fillParentMaxHeight(0.7f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.List, null, tint = colors.TextTertiary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (searchQuery.isNotBlank()) s.noSearchResults else s.noConversations,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.TextTertiary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else if (groups != null) {
        groups.forEach { group ->
            item(key = "grp_${group.key}") { GroupHeader(group.label) }
            items(group.items, key = { it.id }) { conv ->
                ConversationItem(
                    conversation = conv,
                    isActive = conv.id == currentId,
                    isDark = isDark,
                    hazeState = hazeState,
                    onClick = { onSelectConversation(conv) },
                    onDelete = { onDeleteConversation(conv) },
                    onRename = { newTitle -> onRenameConversation(conv, newTitle) },
                    onPin = { onPinConversation(conv) }
                )
            }
        }
    } else {
        items(filteredConversations, key = { it.id }) { conv ->
            ConversationItem(
                conversation = conv,
                isActive = conv.id == currentId,
                isDark = isDark,
                hazeState = hazeState,
                onClick = { onSelectConversation(conv) },
                onDelete = { onDeleteConversation(conv) },
                onRename = { newTitle -> onRenameConversation(conv, newTitle) },
                onPin = { onPinConversation(conv) }
            )
        }
    }
    item { Spacer(Modifier.height(8.dp)) }
}

/** 紧凑搜索框：高度与新对话按钮一致（44dp），磨砂玻璃 + 阴影，BasicTextField 无默认高 */
@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp), fallback = colors.SurfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, "搜索", tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(s.searchHistory, style = MaterialTheme.typography.bodySmall, color = colors.TextTertiary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                    cursorBrush = SolidColor(colors.Primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, "清除", tint = colors.TextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** 新建对话按钮：圆角卡，44dp 高，磨砂玻璃 + 阴影，与搜索框一致 */
@Composable
private fun NewChatButton(
    onNewChat: () -> Unit,
    onNewChatRect: ((Rect) -> Unit)?,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp), fallback = colors.Primary.copy(alpha = 0.12f))
            .clickable { onNewChat() }
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                val size = coords.size
                onNewChatRect?.invoke(Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height))
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, s.newChat, tint = colors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                s.newChat,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.Primary
            )
        }
    }
}

/** 底部设置行（非高级材质，全宽固定） */
@Composable
private fun SettingsRowFull(
    onOpenSettings: () -> Unit,
    onSettingsRowPositioned: ((Rect) -> Unit)?
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpenSettings() }
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                val size = coords.size
                onSettingsRowPositioned?.invoke(Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height))
            }
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Settings, s.settings, tint = colors.TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(s.settings, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
        }
    }
}

/** 悬浮设置按钮：左下角小号磨砂玻璃胶囊，与 Chat 页悬浮输入框同款质感 */
@Composable
private fun FloatingSettingsButton(
    onClick: () -> Unit,
    onPositioned: ((Rect) -> Unit)?,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                val size = coords.size
                onPositioned?.invoke(Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height))
            }
            .shadow(
                elevation = if (advancedMaterial) 8.dp else 6.dp,
                shape = RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (advancedMaterial) Modifier.hazeEffect(state = hazeState) {
                    blurRadius = 24.dp
                    inputScale = HazeInputScale.None
                    backgroundColor = colors.Surface.copy(alpha = 0.5f)
                    tints = listOf(HazeTint(colors.Surface.copy(alpha = 0.4f)))
                } else Modifier.background(colors.InputBg)
            )
            .then(
                if (advancedMaterial) Modifier.border(1.dp, colors.InputBorder.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Settings, s.settings, tint = colors.TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.settings, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: Conversation,
    isActive: Boolean,
    isDark: Boolean,
    hazeState: HazeState,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    // 非高级材质保留原卡片底；高级材质走磨砂玻璃（透光不透物，与输入框同款）
    val bgColor by animateColorAsState(
        targetValue = if (isActive) colors.Primary.copy(alpha = 0.10f) else colors.Surface,
        animationSpec = tween(250),
        label = "conv_bg"
    )

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(conversation.title) }
    var itemPositionY by remember { mutableFloatStateOf(0f) }

    // 长按菜单 — 定位在长按项旁边
    if (showMenu) {
        androidx.compose.ui.window.Popup(
            alignment = Alignment.TopStart,
            offset = androidx.compose.ui.unit.IntOffset(
                x = 0,
                y = itemPositionY.toInt()
            ),
            onDismissRequest = { showMenu = false }
        ) {
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = colors.Surface,
                shadowElevation = 8.dp,
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMenu = false
                                renameText = conversation.title
                                showRenameDialog = true
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.DriveFileRenameOutline, null, tint = colors.TextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(s.renameConversation, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                    }
                    HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMenu = false
                                onPin()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (conversation.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            null,
                            tint = colors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (conversation.isPinned) s.unpinConversation else s.pinConversation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.TextPrimary
                        )
                    }
                    HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteDialog = true; showMenu = false }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.DeleteOutline, null, tint = colors.ErrorRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(s.deleteConversation, style = MaterialTheme.typography.bodyMedium, color = colors.ErrorRed)
                    }
                }
            }
        }
    }

    // 删除确认弹窗
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = colors.Surface,
            title = { Text(s.deleteConversation, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text(s.confirmDeleteConv(conversation.title), color = colors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text(s.delete, color = colors.ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(s.cancel, color = colors.TextSecondary)
                }
            }
        )
    }

    // 重命名弹窗
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = colors.Surface,
            title = {
                Text(s.renameConversation, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold)
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.TextPrimary,
                        unfocusedTextColor = colors.TextPrimary,
                        focusedBorderColor = colors.Primary,
                        unfocusedBorderColor = colors.Divider
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(renameText.trim().ifEmpty { conversation.title })
                        showRenameDialog = false
                    }
                ) {
                    Text(s.confirm, color = colors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(s.cancel, color = colors.TextSecondary)
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    itemPositionY = coords.positionInRoot().y
                }
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (advancedMaterial) Modifier.hazeEffect(state = hazeState) {
                        blurRadius = 24.dp
                        inputScale = HazeInputScale.None
                        backgroundColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.55f)
                        tints = mutableListOf<HazeTint>(
                            HazeTint(if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.15f))
                        ).apply {
                            if (isActive) add(HazeTint(colors.Primary.copy(alpha = 0.10f)))
                        }
                    } else Modifier.background(bgColor)
                )
                .then(
                    if (advancedMaterial) Modifier.border(1.dp, if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    else Modifier
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) colors.Primary.copy(alpha = 0.18f)
                        else colors.SurfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                val avatar = conversation.characterProfile?.avatarPath
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = File(avatar),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        if (conversation.mode == ChatMode.COMPANION) Icons.Filled.Person else Icons.Filled.Chat,
                        contentDescription = null,
                        tint = if (isActive) colors.Primary else colors.TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.isPinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            null,
                            tint = colors.Primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        conversation.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isActive) colors.Primary else colors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    formatLastMessageTime(conversation.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.TextTertiary,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = s.delete,
                    tint = colors.TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 最后对话时间格式化：xxxx/xx/xx  xx：xx（年/月/日 时：分） */
private fun formatLastMessageTime(timestamp: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return String.format(
        java.util.Locale.US,
        "%04d/%02d/%02d  %02d：%02d",
        c.get(java.util.Calendar.YEAR),
        c.get(java.util.Calendar.MONTH) + 1,
        c.get(java.util.Calendar.DAY_OF_MONTH),
        c.get(java.util.Calendar.HOUR_OF_DAY),
        c.get(java.util.Calendar.MINUTE)
    )
}

/** 对话分组：置顶 / 今天 / 昨天 / 7天内 / 30天内 / 更早之前 */
private data class ConvGroup(val key: String, val label: String, val items: List<Conversation>)

@Composable
private fun groupConversations(convs: List<Conversation>): List<ConvGroup> {
    val s = LocalStrings.current
    val dayMs = 24L * 3600 * 1000L
    val startOfToday = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startOfYesterday = startOfToday - dayMs
    val startOf7Days = startOfToday - 7 * dayMs
    val startOf30Days = startOfToday - 30 * dayMs

    fun category(t: Long): String = when {
        t >= startOfToday -> "today"
        t >= startOfYesterday -> "yesterday"
        t >= startOf7Days -> "week"
        t >= startOf30Days -> "month"
        else -> "earlier"
    }

    val pinned = convs.filter { it.isPinned }.sortedByDescending { it.updatedAt }
    val unpinned = convs.filter { !it.isPinned }.sortedByDescending { it.updatedAt }

    val groups = mutableListOf<ConvGroup>()
    if (pinned.isNotEmpty()) groups.add(ConvGroup("pinned", s.groupPinned, pinned))
    listOf(
        "today" to s.groupToday,
        "yesterday" to s.groupYesterday,
        "week" to s.groupWithin7Days,
        "month" to s.groupWithin30Days,
        "earlier" to s.groupEarlier
    ).forEach { (key, label) ->
        val items = unpinned.filter { category(it.updatedAt) == key }
        if (items.isNotEmpty()) groups.add(ConvGroup(key, label, items))
    }
    return groups
}

/** 分组标题 — 别样效果字体，与对话项区分 */
@Composable
private fun GroupHeader(label: String) {
    val colors = LocalFreeChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.Primary)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            ),
            color = colors.TextSecondary
        )
    }
}
