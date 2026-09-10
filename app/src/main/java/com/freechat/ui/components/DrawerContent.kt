package com.freechat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.LocalStrings
import com.freechat.model.Conversation
import com.freechat.model.ChatMode
import com.freechat.model.SearchResultItem
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import java.io.File
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.theme.frostedGlass
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
    searchResults: List<SearchResultItem>,
    favoriteConvIds: Set<String> = emptySet(),
    onSearchQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectConversation: (Conversation) -> Unit,
    onSelectSearchResult: (Conversation, String) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    onRenameConversation: (Conversation, String) -> Unit,
    onPinConversation: (Conversation) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onSettingsRowPositioned: ((Rect) -> Unit)? = null,
    onFavoritesRowPositioned: ((Rect) -> Unit)? = null,
    onNewChatRect: ((Rect) -> Unit)? = null
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // 侧滑页独立 Haze 状态：历史对话列表作为模糊源，顶部/底部做与 Chat 页同规格的高斯模糊
    val drawerHazeState = rememberHazeState()

    // 长按对话菜单：窗口内 overlay（非 Popup），高级材质下才能真磨砂玻璃糊住背后列表
    var showLongPressMenu by remember { mutableStateOf(false) }
    var longPressConv by remember { mutableStateOf<Conversation?>(null) }
    var longPressY by remember { mutableStateOf(0f) }
    // 重命名 / 删除确认弹窗（从 ConversationItem 上提到这里，随菜单统一管理）
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }

    val openLongPressMenu: (Conversation, Float) -> Unit = { conv, y ->
        longPressConv = conv
        longPressY = y
        showLongPressMenu = true
    }
    val requestDelete: (Conversation) -> Unit = { deleteTarget = it }

    // 搜索时不分组、走消息级结果；平时按时间分类分组展示（@Composable 函数，须在 LazyColumn 构建 lambda 之外调用）
    val groups = if (searchQuery.isBlank()) groupConversations(conversations) else null

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val bottomBarHeightPx = with(density) { HazeSpec.BottomFadeHeightDp.toPx() }
    // 固定悬浮头部（搜索框 + 新对话）高度：标题栏下方留白 + 搜索框 + 间距 + 新对话 + 底部间距
    val headerGapTopDp = 24.dp
    val headerCardHeightDp = 44.dp
    val headerCardGapDp = 10.dp
    val headerGapBottomDp = 12.dp
    val headerTotalDp = headerGapTopDp + headerCardHeightDp + headerCardGapDp + headerCardHeightDp + headerGapBottomDp
    // 顶部模糊层界限下移：覆盖固定头部到底部，保证卡片内容可读；fadeExtend 让渐隐带再往下延伸，列表靠近新对话就开始虚化
    val fadeExtendDp = 28.dp
    val topBlurHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + headerTotalDp + fadeExtendDp).toPx() }

    if (advancedMaterial) {
        // ===== 高级材质：沉浸式文字流 + 上下高斯模糊，搜索/新对话固定悬浮（透光不透物磨砂玻璃） =====
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.Background)
        ) {
            // 对话列表（模糊源）：从固定头部下方开始，滚动时内容钻到头部之下
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = drawerHazeState)
                    .background(colors.Background),
                contentPadding = PaddingValues(
                    start = 8.dp, end = 8.dp,
                    top = statusBarHeightDp + titleBarAreaDp + headerTotalDp,
                    bottom = 96.dp
                )
            ) {
                if (searchQuery.isNotBlank()) {
                    searchResultItems(searchResults, s, onSelectSearchResult)
                } else {
                    conversationItems(conversations, groups, currentId, isDark, onSelectConversation, openLongPressMenu, requestDelete, favoriteConvIds, s, drawerHazeState)
                }
            }

            // 顶部渐变模糊（界限下移到固定头部底部，保证卡片内容可读）
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp + headerTotalDp + fadeExtendDp)
                    .hazeEffect(state = drawerHazeState) {
                        blurRadius = HazeSpec.TopBlurRadius
                        inputScale = HazeInputScale.None
                        backgroundColor = Color.Transparent
                        progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 1f, endY = topBlurHeightPx, endIntensity = 0f)
                    }
            )
            // 点击隔离层：只覆盖原头部区域（延伸渐隐区内容仍可点）
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp + headerTotalDp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* 隔离点击：标题栏区域不可点（搜索/新对话在其上，仍可点） */ }
            )

            // 固定悬浮头部：搜索框 + 新对话（透光不透物磨砂玻璃，列表在其下滚动）
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = titleBarAreaDp + headerGapTopDp)
            ) {
                SearchBox(searchQuery, onSearchQueryChange, drawerHazeState, isDark, Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(headerCardGapDp))
                NewChatButton(onNewChat, onNewChatRect, drawerHazeState, isDark, Modifier.padding(horizontal = 16.dp))
            }

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
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* 隔离点击：底部模糊区下的内容不可点 */ }
            )

            // 悬浮设置 + 收藏按钮（左下角磨砂胶囊，收藏在设置右侧，两者紧挨留间距）
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingDrawerButton(
                    icon = Icons.Filled.Settings,
                    label = s.settings,
                    onClick = onOpenSettings,
                    onPositioned = onSettingsRowPositioned,
                    hazeState = drawerHazeState
                )
                FloatingDrawerButton(
                    icon = Icons.Filled.Favorite,
                    label = s.favorites,
                    onClick = onOpenFavorites,
                    onPositioned = onFavoritesRowPositioned,
                    hazeState = drawerHazeState
                )
            }
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

            SearchBox(searchQuery, onSearchQueryChange, drawerHazeState, isDark, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(10.dp))
            NewChatButton(onNewChat, onNewChatRect, drawerHazeState, isDark, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))

            HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    if (searchQuery.isNotBlank()) {
                        searchResultItems(searchResults, s, onSelectSearchResult)
                    } else {
                        conversationItems(conversations, groups, currentId, isDark, onSelectConversation, openLongPressMenu, requestDelete, favoriteConvIds, s, drawerHazeState)
                    }
                }
            }

            HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            SettingsFavoritesRowFull(onOpenSettings, onOpenFavorites, onSettingsRowPositioned, onFavoritesRowPositioned)
        }
    }

    // ===== 长按对话菜单（窗口内 overlay + scrim + 真磨砂玻璃，定位在长按项旁，缩放淡入） =====
    DrawerLongPressMenu(
        conversation = longPressConv,
        itemY = longPressY,
        visible = showLongPressMenu,
        isDark = isDark,
        advancedMaterial = advancedMaterial,
        hazeState = drawerHazeState,
        onDismiss = { showLongPressMenu = false },
        onRename = { conv -> showLongPressMenu = false; renameTarget = conv; renameText = conv.title },
        onPin = { conv -> showLongPressMenu = false; onPinConversation(conv) },
        onDelete = { conv -> showLongPressMenu = false; deleteTarget = conv }
    )

    // ===== 删除确认弹窗 =====
    deleteTarget?.let { conv ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = colors.Surface,
            title = { Text(s.deleteConversation, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(s.confirmDeleteConv(conv.title), color = colors.TextSecondary)
                    if (favoriteConvIds.contains(conv.id)) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.deleteFavoritesWarning, color = colors.ErrorRed, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDeleteConversation(conv); deleteTarget = null }) {
                    Text(s.delete, color = colors.ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(s.cancel, color = colors.TextSecondary)
                }
            }
        )
    }

    // ===== 重命名弹窗 =====
    renameTarget?.let { conv ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = colors.Surface,
            title = { Text(s.renameConversation, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
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
                        onRenameConversation(conv, renameText.trim().ifEmpty { conv.title })
                        renameTarget = null
                    }
                ) {
                    Text(s.confirm, color = colors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(s.cancel, color = colors.TextSecondary)
                }
            }
        )
    }
}

/** 对话列表（含空态），供两种布局复用 */
private fun androidx.compose.foundation.lazy.LazyListScope.conversationItems(
    conversations: List<Conversation>,
    groups: List<ConvGroup>?,
    currentId: String?,
    isDark: Boolean,
    onSelectConversation: (Conversation) -> Unit,
    onLongPressMenu: (Conversation, Float) -> Unit,
    onDeleteRequest: (Conversation) -> Unit,
    favoriteConvIds: Set<String>,
    s: com.freechat.i18n.AppStrings,
    hazeState: HazeState
) {
    if (conversations.isEmpty()) {
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
                        s.noConversations,
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
                    onLongPressMenu = onLongPressMenu,
                    onDeleteRequest = onDeleteRequest,
                    hasFavorites = favoriteConvIds.contains(conv.id)
                )
            }
        }
    }
    item { Spacer(Modifier.height(8.dp)) }
}

/** 搜索结果列表（消息级）：对话名 + 关键词预览 + 时间，无头像无删除键 */
private fun androidx.compose.foundation.lazy.LazyListScope.searchResultItems(
    results: List<SearchResultItem>,
    s: com.freechat.i18n.AppStrings,
    onSelect: (Conversation, String) -> Unit
) {
    if (results.isEmpty()) {
        item(key = "search_empty") {
            val colors = LocalFreeChatColors.current
            Box(
                modifier = Modifier
                    .fillParentMaxWidth()
                    .fillParentMaxHeight(0.7f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.noSearchResults,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.TextTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        items(results, key = { "${it.conversation.id}_${it.messageId}" }) { item ->
            SearchResultRow(item = item, onClick = { onSelect(item.conversation, item.messageId) })
        }
    }
    item { Spacer(Modifier.height(8.dp)) }
}

/** 单条搜索结果卡片：三行（对话名 / 预览[关键词标红] / 时间），点击跳转到该消息 */
@Composable
private fun SearchResultRow(item: SearchResultItem, onClick: () -> Unit) {
    val colors = LocalFreeChatColors.current
    val annotated = buildAnnotatedString {
        append(item.preview)
        if (item.matchStart in 0 until item.preview.length && item.matchEnd in item.matchStart..item.preview.length) {
            addStyle(SpanStyle(color = colors.ErrorRed, fontWeight = FontWeight.SemiBold), item.matchStart, item.matchEnd)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            item.conversation.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            annotated,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 13.sp),
            color = colors.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            formatLastMessageTime(item.timestamp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = colors.TextTertiary.copy(alpha = 0.75f)
        )
    }
}

/** 紧凑搜索框：高度与新对话按钮一致（44dp），磨砂玻璃 + 阴影，BasicTextField 无默认高 */
@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    hazeState: HazeState,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(
                if (advancedMaterial) Modifier.frostedGlass(hazeState, isDark, RoundedCornerShape(14.dp), elevation = 6.dp)
                else Modifier.frostedCard(null, colors, false, RoundedCornerShape(14.dp), fallback = colors.SurfaceVariant)
            )
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
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(
                if (advancedMaterial) Modifier.frostedGlass(hazeState, isDark, RoundedCornerShape(14.dp), elevation = 6.dp)
                else Modifier.frostedCard(null, colors, false, RoundedCornerShape(14.dp), fallback = colors.AccentMuted)
            )
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

/** 底部设置/收藏行（非高级材质，全宽固定，左设置右收藏） */
@Composable
private fun SettingsFavoritesRowFull(
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onSettingsRowPositioned: ((Rect) -> Unit)?,
    onFavoritesRowPositioned: ((Rect) -> Unit)?
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    Row(modifier = Modifier.fillMaxWidth()) {
        // 左：设置
        Box(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    val size = coords.size
                    onSettingsRowPositioned?.invoke(Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height))
                }
                .clickable { onOpenSettings() }
                .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, s.settings, tint = colors.TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(s.settings, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
            }
        }
        // 右：收藏
        Box(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    val size = coords.size
                    onFavoritesRowPositioned?.invoke(Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height))
                }
                .clickable { onOpenFavorites() }
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Favorite, s.favorites, tint = colors.TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(s.favorites, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
            }
        }
    }
}

/** 悬浮抽屉按钮（设置/收藏）：左下角小号磨砂玻璃胶囊，与 Chat 页悬浮输入框同款质感 */
@Composable
private fun FloatingDrawerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    onPositioned: ((Rect) -> Unit)?,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
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
            Icon(icon, label, tint = colors.TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
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
    onLongPressMenu: (Conversation, Float) -> Unit,
    onDeleteRequest: (Conversation) -> Unit,
    hasFavorites: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current

    var itemPositionY by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    itemPositionY = coords.positionInRoot().y
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { onLongPressMenu(conversation, itemPositionY) }
                )
                .padding(start = 16.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧激活指示条（细竖条，替代卡片高亮；非激活透明占位保持对齐）
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isActive) colors.Primary else Color.Transparent)
            )
            Spacer(Modifier.width(10.dp))
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
                onClick = { onDeleteRequest(conversation) },
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

/** 侧滑页长按对话菜单：窗口内 overlay + scrim + 真磨砂玻璃（高级材质），定位在长按项旁，缩放淡入淡出 */
@Composable
private fun DrawerLongPressMenu(
    conversation: Conversation?,
    itemY: Float,
    visible: Boolean,
    isDark: Boolean,
    advancedMaterial: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onRename: (Conversation) -> Unit,
    onPin: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    // 菜单预估高度（3 项 + 2 分割线 + 上下 padding），贴底时向上收
    val menuHeightPx = with(density) { 168.dp.toPx() }
    val topSafePx = with(density) { 80.dp.toPx() }
    val menuX = with(density) { 24.dp.toPx() }.roundToInt()
    val clampedY = itemY.coerceIn(topSafePx, (screenHeightPx - menuHeightPx - topSafePx).coerceAtLeast(topSafePx)).roundToInt()

    // 拦截层：点击外部关闭（淡入淡出）
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(120))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )
    }

    // 菜单本体：缩放 + 淡入淡出
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(initialScale = 0.9f, transformOrigin = TransformOrigin(0f, 0f), animationSpec = tween(180, easing = FastOutSlowInEasing)) +
            fadeIn(tween(150)),
        exit = scaleOut(targetScale = 0.92f, transformOrigin = TransformOrigin(0f, 0f), animationSpec = tween(140, easing = FastOutLinearInEasing)) +
            fadeOut(tween(120))
    ) {
        conversation?.let { conv ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(menuX, clampedY) }
                    .width(180.dp)
                    .then(
                        if (advancedMaterial) Modifier.frostedGlass(hazeState, isDark, RoundedCornerShape(16.dp), elevation = 8.dp)
                        else Modifier.background(colors.Surface, RoundedCornerShape(16.dp))
                    )
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRename(conv) }
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
                            .clickable { onPin(conv) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (conv.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            null,
                            tint = colors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (conv.isPinned) s.unpinConversation else s.pinConversation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.TextPrimary
                        )
                    }
                    HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDelete(conv) }
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
