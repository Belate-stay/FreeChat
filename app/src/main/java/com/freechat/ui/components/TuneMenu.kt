package com.freechat.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freechat.i18n.AppStrings
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.MotionPolicy
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.frostedGlass
import dev.chrisbanes.haze.HazeState

/** 调节菜单层级：主菜单 / 筛选 / 排序 */
enum class TuneLevel { MAIN, FILTER, SORT }

/** 调节菜单的一个可选项（key 供选中判定，label 为展示名） */
data class TuneEntry(val key: String, val label: String)

/**
 * 标题栏「调节」按钮弹出的二级悬浮菜单（收藏页 / 我的分享页共用一套观感）：
 * 主菜单（筛选对话 / 排序方式）↔ 子菜单，带滑动淡入过渡；高级材质下真磨砂玻璃糊住背后列表。
 *
 * 从 FavoritesScreen 抽出（1.1.26.7）：两页的菜单卡片必须功能、显示、动画完全一致，共用一份实现。
 */
@Composable
fun TuneMenu(
    modifier: Modifier = Modifier,
    level: TuneLevel,
    onLevelChange: (TuneLevel) -> Unit,
    filterLabel: String,
    sortLabel: String,
    filterEntries: List<TuneEntry>,
    selectedFilterKey: String?,
    onFilterSelect: (String?) -> Unit,
    sortEntries: List<TuneEntry>,
    selectedSortKey: String,
    onSortSelect: (String) -> Unit,
    colors: FreeChatColors,
    s: AppStrings,
    hazeState: HazeState,
    isDark: Boolean,
    advancedMaterial: Boolean,
    topPadding: Dp
) {
    val travel = with(LocalDensity.current) { MotionPolicy.PageTravelDp.dp.roundToPx() }
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .padding(top = topPadding, end = 12.dp, start = 16.dp, bottom = 16.dp)
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
                FreeChatAnimation.pageTransition(forward, travel, animateSize = true)
            },
            label = "tune_menu"
        ) { lvl ->
            when (lvl) {
                TuneLevel.MAIN -> Column {
                    TuneMenuParentItem(filterLabel, colors) { onLevelChange(TuneLevel.FILTER) }
                    TuneMenuParentItem(sortLabel, colors) { onLevelChange(TuneLevel.SORT) }
                }
                TuneLevel.FILTER -> Column {
                    TuneMenuHeader(filterLabel, colors) { onLevelChange(TuneLevel.MAIN) }
                    filterEntries.forEach { entry ->
                        TuneMenuItem(entry.label, selected = entry.key == selectedFilterKey, colors) {
                            onFilterSelect(entry.key.ifEmpty { null })
                        }
                    }
                }
                TuneLevel.SORT -> Column {
                    TuneMenuHeader(sortLabel, colors) { onLevelChange(TuneLevel.MAIN) }
                    sortEntries.forEach { entry ->
                        TuneMenuItem(entry.label, selected = entry.key == selectedSortKey, colors) {
                            onSortSelect(entry.key)
                        }
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
