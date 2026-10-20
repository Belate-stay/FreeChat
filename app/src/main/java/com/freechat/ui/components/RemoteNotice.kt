package com.freechat.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import com.freechat.ui.theme.AppBoxWithConstraints as BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freechat.data.RemoteNotices
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.theme.liquidOpaqueBackground
import com.freechat.ui.theme.materialProgress
import com.freechat.ui.theme.pageBackground
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import com.freechat.ui.theme.materialHaze as hazeEffect

/**
 * 云端弹窗（2026-10-18）两种外壳：
 *  · [RemoteAlertDialog]      —— alert 非强制：与「新版本更新」「角色冲突」（[NoticeDialog]）
 *    同一套玻璃语言，按钮可拒绝（配置里的 dismiss 钮就是拒绝口）；
 *  · [RemoteFullscreenNotice] —— fullscreen 强制：与「新功能介绍」（FeatureRecPage）同款
 *    全屏底 + 拟物卡，**必须看完正文（滚到底/短文直接算看完）才能按按钮、必须选一个**。
 * 正文走 [MarkdownText]：超链接可点跳转（LinkAnnotation 管道与聊天正文同款）。
 */

@Composable
private fun NoticeButtonRow(
    buttons: List<RemoteNotices.Button>,
    colors: FreeChatColors,
    enabled: Boolean,
    onButton: (RemoteNotices.Button) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        buttons.forEach { b ->
            val primary = b.action != "dismiss"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (primary) colors.Primary else colors.Surface)
                    .then(if (primary) Modifier else Modifier.border(1.dp, colors.InputBorder, RoundedCornerShape(13.dp)))
                    .clickable(enabled = enabled) { onButton(b) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    b.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (primary) colors.OnPrimary else colors.TextPrimary,
                    fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun NoticeBody(notice: RemoteNotices.Notice, colors: FreeChatColors, modifier: Modifier = Modifier) {
    MarkdownText(
        content = notice.body,
        modifier = modifier,
        textColor = colors.TextSecondary,
        codeBgColor = colors.SurfaceVariant,
        quoteBarColor = colors.Primary.copy(alpha = 0.5f),
        dividerColor = colors.Divider
    )
}

/** alert 非强制提醒：NoticeDialog 同款外壳（可拒绝）。 */
@Composable
fun BoxScope.RemoteAlertDialog(
    visible: Boolean,
    notice: RemoteNotices.Notice?,
    colors: FreeChatColors,
    isDark: Boolean,
    advancedMaterial: Boolean,
    hazeState: HazeState,
    onButton: (RemoteNotices.Button) -> Unit
) {
    if (notice == null) return
    val progress = materialProgress()
    // 与 NoticeDialog 同纪律：只认按钮，返回键与边缘点击都不关（拒绝走 dismiss 钮）
    BackHandler(enabled = visible) { }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(FreeChatAnimation.overlayFadeIn),
        exit = fadeOut(FreeChatAnimation.overlayFadeOut)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (advancedMaterial) Modifier
                        .liquidOpaqueBackground(colors.Background, strength = progress)
                        .hazeEffect(state = hazeState) {
                            blurRadius = 22.dp
                            inputScale = HazeInputScale.None
                            backgroundColor = if (isDark) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.16f)
                        } else Modifier
                )
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.32f * (1f - progress()))) }
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.Center),
        enter = FreeChatAnimation.dialogEnter(),
        exit = FreeChatAnimation.dialogExit()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 34.dp)
                .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(20.dp), blur = 24.dp, elevation = 8.dp)
                .padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            Text(
                notice.title,
                color = colors.TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(9.dp))
            NoticeBody(
                notice, colors,
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            )
            Spacer(Modifier.height(24.dp))
            NoticeButtonRow(notice.buttons, colors, enabled = true, onButton = onButton)
        }
    }
}

/** fullscreen 强制全屏：FeatureRecPage 同款底（pageBackground+拟物卡），看完必选。 */
@Composable
fun RemoteFullscreenNotice(
    notice: RemoteNotices.Notice,
    colors: FreeChatColors,
    onButton: (RemoteNotices.Button) -> Unit
) {
    // 强制：返回键吞掉，出口只有按钮
    BackHandler { }
    val scroll = rememberScrollState()
    // 「必须看完」：短文（滚不动）直接算看完；长文滚到底解锁按钮
    var readThrough by remember(notice.id) { mutableStateOf(false) }
    LaunchedEffect(notice.id) {
        if (!scroll.canScrollForward) readThrough = true
    }
    LaunchedEffect(scroll.value, scroll.maxValue) {
        if (scroll.maxValue > 0 && scroll.value >= scroll.maxValue - 8) readThrough = true
    }
    BoxWithConstraints(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .frostedCard(null, colors, advancedMaterial = true, RoundedCornerShape(22.dp))
                    .padding(horizontal = 22.dp, vertical = 22.dp)
            ) {
                Text(
                    notice.title,
                    color = colors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(12.dp))
                NoticeBody(
                    notice, colors,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp)
                        .verticalScroll(scroll)
                )
                Spacer(Modifier.height(18.dp))
                // 看完才亮按钮（未解锁=压暗，同导览页「播完才能继续」的口径）
                Box(modifier = Modifier.alpha(if (readThrough) 1f else 0.4f)) {
                    NoticeButtonRow(notice.buttons, colors, enabled = readThrough, onButton = onButton)
                }
                if (!readThrough) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "请阅读完正文后选择",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.TextSecondary
                    )
                }
            }
        }
    }
}
