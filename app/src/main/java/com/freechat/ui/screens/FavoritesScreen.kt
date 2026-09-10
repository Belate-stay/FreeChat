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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Tune
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
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.model.Conversation
import com.freechat.model.FavoriteItem
import com.freechat.model.Role
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

/** 收藏夹排序方式：收藏时间（消息发送时间倒序）/ 最近一次对话时间（对话 updatedAt 倒序） */
private enum class FavSort { FAVORITE_TIME, CONV_TIME }

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

    Box(Modifier.fillMaxSize().background(colors.Background)) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).background(colors.Background) else Modifier),
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
                    items(displayList, key = { "${it.conversation.id}_${it.message.id}" }) { fav ->
                        FavoriteRow(
                            item = fav,
                            colors = colors,
                            s = s,
                            onClick = { onOpenDetail(fav) }
                        )
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
                    .background(colors.Background)
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

        // ===== 调节菜单（窗口内悬浮，高级材质下真磨砂玻璃糊住背后列表；从右上角缩放淡入） =====
        AnimatedVisibility(
            visible = showTuneMenu,
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
            visible = showTuneMenu,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = scaleIn(initialScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f), animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                fadeIn(tween(150)),
            exit = scaleOut(targetScale = 0.92f, transformOrigin = TransformOrigin(1f, 0f), animationSpec = tween(140, easing = FastOutLinearInEasing)) +
                fadeOut(tween(120))
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
                        TuneMenuItem(conv.title, selected = filterConvId == conv.id, colors) {
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

/** 单条收藏：所属对话名（加大加粗）+ 内容预览（宋体弱化）+ 时间，点击进详情 */
@Composable
private fun FavoriteRow(
    item: FavoriteItem,
    colors: FreeChatColors,
    s: AppStrings,
    onClick: () -> Unit
) {
    val msg = item.message
    val raw = if (msg.role == Role.USER) msg.content else markdownToPlainText(msg.content)
    val hasImg = msg.imagePaths.isNotEmpty() || msg.imageUrls.isNotEmpty()
    val hasAtt = msg.attachmentName != null
    val display = when {
        raw.isNotBlank() -> {
            val prefix = buildString {
                if (hasImg) append("[图片] ")
                if (hasAtt) append("[文件] ")
            }
            prefix + raw.trim()
        }
        hasImg -> "[图片]"
        hasAtt -> "[文件] ${msg.attachmentName}"
        else -> "（空消息）"
    }
    val time = remember(msg.timestamp) {
        SimpleDateFormat("M月d日 HH:mm", Locale.CHINESE).format(Date(msg.timestamp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                item.conversation.title,
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
    }
}
