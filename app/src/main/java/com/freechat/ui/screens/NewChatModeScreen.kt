package com.freechat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.LocalStrings
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.frostedCard
import androidx.compose.ui.text.style.TextAlign
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 新对话模式选择页：标准问答 vs 拟人陪伴。
 * 高级材质下标题栏渐进高斯模糊、卡片磨砂哑光玻璃。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatModeScreen(
    isDark: Boolean,
    onBack: () -> Unit,
    onSelectStandard: () -> Unit,
    onSelectCompanion: () -> Unit
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val density = LocalDensity.current

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp).toPx() }

    Box(Modifier.fillMaxSize().background(colors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).background(colors.Background) else Modifier)
                .padding(top = statusBarHeightDp + titleBarAreaDp + 24.dp, bottom = 40.dp)
                .padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ModeCard(
                icon = Icons.Filled.SmartToy,
                title = s.standardMode,
                desc = s.chatModeDesc,
                hazeState = hazeState,
                onClick = onSelectStandard
            )
            Spacer(Modifier.height(24.dp))
            ModeCard(
                icon = Icons.Filled.Favorite,
                title = s.companionMode,
                desc = s.companionModeDesc,
                hazeState = hazeState,
                onClick = onSelectCompanion
            )
        }

        // 顶部标题栏背景（高级材质开=真模糊，关=纯色）
        if (advancedMaterial) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp)
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

        // 悬浮标题栏
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
                s.chooseChatMode,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

/** 模式卡片：正方形圆角卡（等大居中），高级材质下磨砂哑光玻璃 */
@Composable
private fun ModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    hazeState: dev.chrisbanes.haze.HazeState,
    onClick: () -> Unit
) {
    val colors = LocalFreeChatColors.current
    val advancedMaterial = LocalAdvancedMaterial.current

    Box(
        modifier = Modifier
            .size(200.dp)
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.Primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = colors.Primary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}
