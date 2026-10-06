package com.freechat.ui.screens

import com.freechat.ui.components.HeaderIconButton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.LocalStrings
import com.freechat.model.ChangelogData
import com.freechat.model.ChangelogEntry
import com.freechat.ui.theme.LocalMonoFontFamily
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.HazeSpec
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.components.TopBarBackdrop
import com.freechat.ui.components.TopBarBackdropSource

/** 更新日志页：纯流式排版，无卡片；版本号 Consolas 放大，分类方向总结 + 主题色 + 竖条 + 有序列表，版本间分页线 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        TopBarBackdropSource(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBarHeightDp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(top = HazeSpec.topContentPaddingDp(statusBarHeightDp, 36.dp), bottom = 40.dp)
                .padding(horizontal = 20.dp)
        ) {
            s.changelogEntries.forEachIndexed { idx, entry ->
                ChangelogEntryView(entry, colors)
                if (idx != s.changelogEntries.lastIndex) {
                    HorizontalDivider(
                        color = colors.Divider.copy(alpha = 0.45f),
                        modifier = Modifier.padding(vertical = 28.dp)
                    )
                }
            }
        }

        // 固定高度：实体标题栏与渐进模糊仅切换材质，不切换几何。
        TopBarBackdrop(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBarHeightDp))

        // ===== 悬浮标题栏（返回键 + 标题） =====
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back, tint = colors.TextPrimary)
            }
            Text(
                s.changelog,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun ChangelogEntryView(entry: ChangelogEntry, colors: FreeChatColors) {
    Column {
        // 版本号（Consolas 放大）+ 日期
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                entry.version,
                fontFamily = LocalMonoFontFamily.current,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.TextPrimary
            )
            Text(entry.date, style = MaterialTheme.typography.bodySmall, color = colors.TextTertiary)
        }

        Spacer(Modifier.height(20.dp))

        entry.sections.forEachIndexed { idx, section ->
            ChangelogSection(section.title, section.items, colors.Primary, colors)
            if (idx != entry.sections.lastIndex) Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ChangelogSection(
    title: String,
    items: List<String>,
    accentColor: Color,
    colors: FreeChatColors
) {
    Column {
        // 分类标题：主题色 + 前导竖条，粗体放大以突出「更新方向」与详细更新点的层次
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = accentColor, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    "·",
                    style = MaterialTheme.typography.bodyLarge,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(16.dp)
                )
                Text(
                    item,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.TextPrimary,
                    lineHeight = 24.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
