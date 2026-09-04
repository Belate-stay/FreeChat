package com.freechat.ui.screens

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.platform.LocalDensity
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
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** 更新日志页：纯流式排版，无卡片；版本号 Consolas 放大，分类方向总结 + 主题色 + 竖条 + 有序列表，版本间分页线 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val topFadeZoneDp = HazeSpec.TopFadeZoneDp
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + topFadeZoneDp).toPx() }

    Box(Modifier.fillMaxSize().background(colors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).background(colors.Background) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(top = statusBarHeightDp + titleBarAreaDp + 36.dp, bottom = 40.dp)
                .padding(horizontal = 20.dp)
        ) {
            ChangelogData.entries.forEachIndexed { idx, entry ->
                ChangelogEntryView(entry, colors)
                if (idx != ChangelogData.entries.lastIndex) {
                    HorizontalDivider(
                        color = colors.Divider.copy(alpha = 0.45f),
                        modifier = Modifier.padding(vertical = 28.dp)
                    )
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

        // ===== 悬浮标题栏（返回键 + 标题） =====
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
