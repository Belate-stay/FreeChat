package com.freechat.ui.screens

import com.freechat.ui.components.HeaderIconButton

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.*
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.AppBoxWithConstraints
import com.freechat.ui.adaptive.tabletReadingGutter
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.frostedCard
import androidx.compose.ui.text.style.TextAlign
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.components.TopBarBackdrop
import com.freechat.ui.components.TopBarBackdropSource

/**
 * 四个全宽设置卡片式入口；仅放大显示时允许滚动，不截断可读内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatModeScreen(
    isDark: Boolean,
    onBack: () -> Unit,
    onSelectStandard: () -> Unit,
    onSelectCompanion: () -> Unit,
    onSelectGroup: () -> Unit,
    onSelectStory: () -> Unit,
    enlargedDisplay: Boolean = false,
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        TopBarBackdropSource(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBarHeightDp))
        AppBoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding()
            .padding(top = HazeSpec.newPageContentPaddingDp(statusBarHeightDp, 24.dp))) {
            val verticalGap = if (maxHeight < 400.dp) 8.dp else 24.dp
            val rowGap = if (maxHeight < 400.dp) 8.dp else 16.dp
            val rowHeight = ((maxHeight - verticalGap * 2 - rowGap * 3) / 4).coerceIn(0.dp, 128.dp)
            Column(Modifier.fillMaxSize()
                .then(if (enlargedDisplay) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp + tabletReadingGutter(), vertical = verticalGap),
                verticalArrangement = Arrangement.spacedBy(rowGap)) {
                    val modes = listOf(
                        Triple(Icons.Filled.SmartToy, s.standardMode to s.standardConversationDesc, onSelectStandard),
                        Triple(Icons.Filled.Favorite, s.companionMode to s.companionModeDesc, onSelectCompanion),
                        Triple(Icons.Filled.Groups, s.multiCharacterGroup to s.multiCharacterGroupDesc, onSelectGroup),
                        Triple(Icons.Filled.AutoStories, s.multiCharacterStory to s.multiCharacterStoryDesc, onSelectStory))
                    modes.forEach { (icon, text, action) ->
                        ModeCard(icon, text.first, text.second, hazeState, action,
                            modifier = if (enlargedDisplay) Modifier.heightIn(min = 128.dp) else Modifier.height(rowHeight),
                            compact = !enlargedDisplay && rowHeight < 112.dp,
                            showDescription = enlargedDisplay || rowHeight >= 72.dp)
                    }
            }
        }

        TopBarBackdrop(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBarHeightDp))

        // 悬浮标题栏
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
                s.createConversation,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

/** 与设置页同款底材、圆角和整行触控，图标与标题说明左右排布。 */
@Composable
private fun ModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    hazeState: dev.chrisbanes.haze.HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showDescription: Boolean = true,
) {
    val colors = LocalFreeChatColors.current
    val advancedMaterial = LocalAdvancedMaterial.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .then(if (showDescription) Modifier else Modifier.semantics { contentDescription = desc })
            .padding(horizontal = if (compact) 16.dp else 20.dp, vertical = if (compact) 8.dp else 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)) {
            Box(
                modifier = Modifier
                    .size(if (compact) 28.dp else 48.dp)
                    .clip(RoundedCornerShape(if (compact) 8.dp else 12.dp))
                    .background(colors.Primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = colors.Primary, modifier = Modifier.size(if (compact) 18.dp else 24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
                    .copy(lineBreak = LineBreak.Heading),
                    color = colors.TextPrimary, fontWeight = FontWeight.SemiBold,
                    maxLines = if (compact) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                if (showDescription) {
                    Text(desc, style = MaterialTheme.typography.bodySmall.copy(
                        lineBreak = LineBreak.Paragraph.copy(strategy = LineBreak.Strategy.Balanced)),
                        color = colors.TextSecondary, textAlign = TextAlign.Start,
                        maxLines = if (compact) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
