package com.freechat.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.model.Conversation
import com.freechat.model.FavoriteItem
import com.freechat.model.Role
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.components.markdownToPlainText
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.theme.pageHeaderBackground

/** 收藏夹排序方式：收藏时间（消息发送时间倒序）/ 最近一次对话时间（对话 updatedAt 倒序） */
private enum class FavSort { FAVORITE_TIME, CONV_TIME }

/** 收藏条目的选择键：与 LazyColumn 的 item key 同构（对话 + 消息） */
private fun favKey(item: FavoriteItem): String = "${item.conversation.id}_${item.message.id}"

/** 调节菜单层级：主菜单 / 筛选对话 / 排序方式 */
private enum class TuneLevel { MAIN, FILTER, SORT }

/** 收藏夹页：平铺所有被收藏的消息（每条带所属对话名），标题栏右侧「调节」按钮承载筛选/排序，可点进「收藏详情」 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: ChatViewModel,
    isDark: Boolean,
    onBack: () -> Unit,
    onOpenDetail: (FavoriteItem) -> Unit
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val favorites by viewModel.favorites.collectAsState()
    // 进入收藏页时刷新一次，保证「最近一次对话时间」用的是最新对话状态
    LaunchedEffect(Unit) { viewModel.refreshFavorites() }
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val topFadeZoneDp = HazeSpec.TopFadeZoneDp
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + topFadeZoneDp).toPx() }

    // 筛选/排序状态
    var filterConvId by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(FavSort.FAVORITE_TIME) }
    // 调节菜单状态
    var showTuneMenu by remember { mutableStateOf(false) }
    var tuneLevel by remember { mutableStateOf(TuneLevel.MAIN) }

    // ===== 多选：长按任一条进入，可批量取消收藏（整段取消，见 ChatViewModel.unfavoriteItems） =====
    var selectMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    fun exitSelect() {
        selectMode = false
        selectedKeys = emptySet()
    }
    // 多选态下返回键先退出多选，而不是直接离开收藏夹
    BackHandler(enabled = selectMode) { exitSelect() }

    // 有收藏的对话（去重，供筛选下拉）
    val convWithFavs = remember(favorites) { favorites.map { it.conversation }.distinctBy { it.id } }
    // 选中对话已无收藏时回退到「全部」
    val effectiveFilter = filterConvId?.takeIf { id -> convWithFavs.any { it.id == id } }

    // 过滤 + 排序后的展示列表
    val displayList = remember(favorites, effectiveFilter, sortMode) {
        val filtered = if (effectiveFilter == null) favorites else favorites.filter { it.conversation.id == effectiveFilter }
        when (sortMode) {
            FavSort.FAVORITE_TIME -> filtered.sortedByDescending { it.message.timestamp }
            FavSort.CONV_TIME -> filtered.sortedByDescending { it.conversation.updatedAt }
        }
    }
    // 选中的条目从「当前看得见的列表」反查：多选态下调节菜单不可用，列表不会中途变样
    val selectedItems = displayList.filter { favKey(it) in selectedKeys }
    val toggleSelectFav: (FavoriteItem) -> Unit = { fav ->
        val k = favKey(fav)
        val next = if (k in selectedKeys) selectedKeys - k else selectedKeys + k
        // 取消到空就自动退出多选，省得留一个「已选择 0 项」的空壳
        if (next.isEmpty()) exitSelect() else selectedKeys = next
    }
    // 长按进多选：顺手把调节菜单收掉，否则两个浮层会叠在一起
    val enterSelectFav: (FavoriteItem) -> Unit = { fav ->
        showTuneMenu = false
        selectMode = true
        selectedKeys = setOf(favKey(fav))
    }

    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        // ──── 模糊源：整页一格，空态也在内 ────
        // 原来源挂在 LazyColumn 上 —— 收藏为空时那个列表根本不存在，于是右上角那张磨砂菜单卡片
        // 采不到任何东西，直接退化成半透明灰片（1.0.49 报的「菜单卡片整体灰黑」就是这个）。
        // 现在源包住「空态 / 列表」整块，两种状态都有东西可采。
        // 源里**不能**包含采样它的那几个效果层（顶栏渐变、TuneMenu 本身），否则自己糊自己 ——
        // 所以顶栏和菜单仍然挂在这一格外面。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
        ) {
            if (favorites.isEmpty()) {
                // 空态
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.FavoriteBorder, null, tint = colors.TextTertiary, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(s.noFavorites, style = MaterialTheme.typography.bodyMedium, color = colors.TextTertiary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = statusBarHeightDp + titleBarAreaDp + 20.dp,
                        bottom = 40.dp
                    )
                ) {
                    if (displayList.isEmpty()) {
                        item(key = "filtered_empty") {
                            Box(
                                modifier = Modifier.fillParentMaxWidth().padding(top = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(s.noFavorites, style = MaterialTheme.typography.bodyMedium, color = colors.TextTertiary)
                            }
                        }
                    } else {
                        items(displayList, key = { favKey(it) }) { fav ->
                            FavoriteRow(
                                item = fav,
                                colors = colors,
                                s = s,
                                selectMode = selectMode,
                                isSelected = favKey(fav) in selectedKeys,
                                // 多选态：点击 = 勾选；非多选态：点击进详情、长按进多选
                                onClick = { if (selectMode) toggleSelectFav(fav) else onOpenDetail(fav) },
                                onLongPress = { if (selectMode) toggleSelectFav(fav) else enterSelectFav(fav) }
                            )
                        }
                    }
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
                    .pageHeaderBackground(colors.Background)
                    .hazeEffect(state = hazeState) {
                        blurRadius = HazeSpec.TopBlurRadius
                        inputScale = HazeInputScale.None
                        backgroundColor = Color.Transparent
                        progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 1f, endY = topBarHeightPx, endIntensity = 0f)
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp)
                    // 标题栏必须**不透明**（正文滚上来要被挡住）。炫彩开着时 pageBackground 是空操作
                    // —— 整页都透明，标题区就跟着透了。改用 pageHeaderBackground：炫彩关=这块底色本身，
                    // 炫彩开=钉在屏幕上的一份流光副本，两种情况下都与页面自身上下同色。
                    .pageHeaderBackground(colors.Background)
            )
        }

        // ===== 悬浮标题栏（返回键 + 标题 + 右侧调节按钮） =====
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectMode) {
                // 多选态：返回键换成「取消」（退出多选，不离开收藏夹）
                IconButton(onClick = { exitSelect() }) {
                    Icon(Icons.Filled.Close, s.cancel, tint = colors.TextPrimary)
                }
                Text(
                    s.selectedCount(selectedItems.size),
                    color = colors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                // 批量取消收藏：一次整段取消，不需要二次确认（随时可以再从聊天页收藏回来）
                TextButton(
                    onClick = {
                        viewModel.unfavoriteItems(selectedItems)
                        exitSelect()
                    },
                    enabled = selectedItems.isNotEmpty()
                ) {
                    Text(s.unfavorite, color = if (selectedItems.isEmpty()) colors.TextTertiary else colors.Primary)
                }
            } else {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.TextPrimary)
                }
                Text(
                    s.favorites,
                    color = colors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.weight(1f))
                // 调节按钮：点击开/关二级菜单
                IconButton(onClick = {
                    showTuneMenu = !showTuneMenu
                    if (showTuneMenu) tuneLevel = TuneLevel.MAIN
                }) {
                    Icon(Icons.Filled.Tune, s.newRules, tint = colors.TextPrimary)
                }
            }
        }

        // ===== 调节菜单（窗口内悬浮，高级材质下真磨砂玻璃糊住背后列表；从右上角缩放淡入） =====
        AnimatedVisibility(
            visible = showTuneMenu && !selectMode,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120))
        ) {
            // 全屏透明拦截层：点击菜单外部关闭
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showTuneMenu = false }
            )
        }
        AnimatedVisibility(
            visible = showTuneMenu && !selectMode,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = FreeChatAnimation.menuEnter(TransformOrigin(1f, 0f)),
            exit = FreeChatAnimation.menuExit(TransformOrigin(1f, 0f))
        ) {
            TuneMenu(
                level = tuneLevel,
                onLevelChange = { tuneLevel = it },
                convWithFavs = convWithFavs,
                filterConvId = effectiveFilter,
                onFilterSelect = { filterConvId = it; showTuneMenu = false },
                sortMode = sortMode,
                onSortSelect = { sortMode = it; showTuneMenu = false },
                colors = colors,
                s = s,
                hazeState = hazeState,
                isDark = isDark,
                advancedMaterial = advancedMaterial,
                topPadding = statusBarHeightDp + titleBarAreaDp + 4.dp
            )
        }
    }
}

/** 调节按钮弹出的二级菜单：主菜单（筛选对话/排序方式）↔ 子菜单（对话列表/排序选项），带滑动淡入过渡 */
@Composable
private fun TuneMenu(
    modifier: Modifier = Modifier,
    level: TuneLevel,
    onLevelChange: (TuneLevel) -> Unit,
    convWithFavs: List<Conversation>,
    filterConvId: String?,
    onFilterSelect: (String?) -> Unit,
    sortMode: FavSort,
    onSortSelect: (FavSort) -> Unit,
    colors: FreeChatColors,
    s: AppStrings,
    hazeState: HazeState,
    isDark: Boolean,
    advancedMaterial: Boolean,
    topPadding: Dp
) {
    Box(
        modifier = modifier
            .padding(top = topPadding, end = 12.dp)
            .width(220.dp)
            .then(
                if (advancedMaterial) Modifier.frostedGlass(hazeState, isDark, RoundedCornerShape(20.dp), elevation = 6.dp)
                else Modifier.background(colors.Surface, RoundedCornerShape(20.dp))
            )
    ) {
        AnimatedContent(
            targetState = level,
            transitionSpec = {
                val forward = targetState != TuneLevel.MAIN
                val enter = if (forward) slideInHorizontally { it / 2 } + fadeIn() else slideInHorizontally { -it / 3 } + fadeIn()
                val exit = if (forward) slideOutHorizontally { -it / 3 } + fadeOut() else slideOutHorizontally { it / 2 } + fadeOut()
                (enter togetherWith exit).using(SizeTransform(clip = false))
            },
            label = "tune_menu"
        ) { lvl ->
            when (lvl) {
                TuneLevel.MAIN -> Column {
                    TuneMenuParentItem(s.filterByConversation, colors) { onLevelChange(TuneLevel.FILTER) }
                    TuneMenuParentItem(s.sortBy, colors) { onLevelChange(TuneLevel.SORT) }
                }
                TuneLevel.FILTER -> Column {
                    TuneMenuHeader(s.filterByConversation, colors) { onLevelChange(TuneLevel.MAIN) }
                    TuneMenuItem(s.filterAll, selected = filterConvId == null, colors) {
                        onFilterSelect(null)
                    }
                    convWithFavs.forEach { conv ->
                        TuneMenuItem(com.freechat.i18n.localizedConvTitle(conv, s), selected = filterConvId == conv.id, colors) {
                            onFilterSelect(conv.id)
                        }
                    }
                }
                TuneLevel.SORT -> Column {
                    TuneMenuHeader(s.sortBy, colors) { onLevelChange(TuneLevel.MAIN) }
                    TuneMenuItem(s.sortFavoriteTime, selected = sortMode == FavSort.FAVORITE_TIME, colors) {
                        onSortSelect(FavSort.FAVORITE_TIME)
                    }
                    TuneMenuItem(s.sortConvTime, selected = sortMode == FavSort.CONV_TIME, colors) {
                        onSortSelect(FavSort.CONV_TIME)
                    }
                }
            }
        }
    }
}

/** 主菜单项：文字 + 右侧箭头（进入子菜单） */
@Composable
private fun TuneMenuParentItem(label: String, colors: FreeChatColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
    }
}

/** 子菜单项：文字 + （选中时）对勾 */
@Composable
private fun TuneMenuItem(label: String, selected: Boolean, colors: FreeChatColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.Primary else colors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(18.dp))
        }
    }
}

/** 子菜单顶部返回头：返回箭头 + 标题，点击回到主菜单 */
@Composable
private fun TuneMenuHeader(label: String, colors: FreeChatColors, onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.Primary
            )
        }
        HorizontalDivider(color = colors.Divider.copy(alpha = 0.5f))
    }
}

/** 单条收藏：所属对话名（加大加粗）+ 内容预览（宋体弱化）+ 时间，点击进详情；长按进多选 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(
    item: FavoriteItem,
    colors: FreeChatColors,
    s: AppStrings,
    selectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val msg = item.message
    val raw = if (msg.role == Role.USER) msg.content else markdownToPlainText(msg.content)
    val hasImg = msg.imagePaths.isNotEmpty() || msg.imageUrls.isNotEmpty()
    val hasAtt = msg.attachmentName != null
    val display = when {
        raw.isNotBlank() -> {
            val prefix = buildString {
                if (hasImg) append("${s.imageTag} ")
                if (hasAtt) append("${s.fileTag} ")
            }
            prefix + raw.trim()
        }
        hasImg -> s.imageTag
        hasAtt -> "${s.fileTag} ${msg.attachmentName}"
        else -> s.emptyMessage
    }
    val time = remember(msg.timestamp) {
        SimpleDateFormat(s.dateMdTime, Locale.getDefault()).format(Date(msg.timestamp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (msg.role == Role.USER) colors.Primary.copy(alpha = 0.5f) else colors.Divider)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // 所属对话名（加大加粗、主题色）
            Text(
                com.freechat.i18n.localizedConvTitle(item.conversation, s),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            // 内容预览（宋体 + 视觉弱化）
            Text(
                display,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 13.sp),
                color = colors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = colors.TextTertiary
            )
        }
        // 多选态：右侧勾选圈（自绘，与侧滑页多选同一套视觉）
        if (selectMode) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.Primary else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) colors.Primary else colors.TextTertiary.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Filled.Check, null, tint = colors.Background, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}
