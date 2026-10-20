package com.freechat.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.adaptive.LocalTabletDevice
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.theme.liquidOpaqueBackground
import com.freechat.ui.theme.materialProgress
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import com.freechat.ui.theme.materialHaze as hazeEffect

/**
 * 居中提醒弹窗（1.1.20：冲突副本清理提醒 / 检查更新提醒）—— 全 App 启动提醒的**唯一**外壳。
 *
 * 与 [SheetPanel] 同一套玻璃语言，为什么也不用 `AlertDialog`：独立窗口看不到 App 已经
 * 画好的内容，「背景高斯模糊」做不到，只能压暗（见 SheetPanel 头注）。这里照抄它的方案：
 *   1. 拦截层：铺满整屏的一层 haze —— 把背后整页**糊掉**（高级材质关掉时退化成压暗）；
 *   2. 卡片：居中落位，[frostedGlass]（真模糊 + 浅 tint + 描边），缩放淡入、加速淡出。
 *
 * 按钮布局（2026-10-10 用户拍板）：**主按钮在右、主题色实底**（去清理/去更新）引导优先；
 * 副按钮在左、中性浅底（暂不）。勾选项钉在**左下角**（永不提醒/近期不再提醒）。
 * 勾选项的最终状态在任意关闭路径上都算数 —— 勾了就是勾了，点哪都带着。
 *
 * **调用位置**：和 [SheetPanel] 一样挂在页面根 `Box` 的末尾（[BoxScope] 扩展），
 * 且要把它的可见性并进 hazeSource 的激活条件（见 MainActivity 的 drawerSheetOpen）。
 */
@Composable
fun BoxScope.NoticeDialog(
    visible: Boolean,
    title: String,
    body: String,
    /** 主按钮文案（去清理 / 去更新）—— 右侧、主题色 */
    confirmLabel: String,
    /** 副按钮文案（暂不）—— 左侧、中性浅底 */
    dismissLabel: String,
    /** 左下角勾选项文案（永不提醒 / 近期不再提醒） */
    checkboxLabel: String,
    colors: FreeChatColors,
    isDark: Boolean,
    advancedMaterial: Boolean,
    hazeState: HazeState,
    /** 关闭并执行主行动。checkboxChecked = 关闭那一刻勾选项的状态 */
    onConfirm: (checkboxChecked: Boolean) -> Unit,
    /** 关闭（暂不 / 点外面 / 返回键）。checkboxChecked 同上 */
    onDismiss: (checkboxChecked: Boolean) -> Unit
) {
    val tablet = LocalTabletDevice.current
    val fontScale = LocalDensity.current.fontScale
    // 勾选项是这一次弹窗的临时状态：重新弹出时回到未勾
    var checked by remember(visible) { mutableStateOf(false) }
    // 1.1.26.4：弹窗**只认按钮**——返回键吞掉不做响应（工单：只能点「取消」按钮消除）
    BackHandler(enabled = visible) { /* 吞掉返回键，防误触退出 */ }

    val shape = RoundedCornerShape(20.dp)
    val progress = materialProgress()

    // —— 背景模糊拦截层：**点它没有任何响应**（1.1.26.4 工单：边缘点击不退出，只认按钮）——
    // 遮罩比卡片早一点起、晚一点收：模糊先铺满，卡片再落上去（与 SheetPanel 同一节奏）
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
                            // 背景模糊而不是变暗：底色留空，只加一点白/黑把对比拉开
                            backgroundColor = if (isDark) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.16f)
                        } else Modifier
                )
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.32f * (1f - progress()))) }
        )
    }
    // —— 居中卡片：标题 / 正文 / 按钮行（暂不 白 · 主行动 主题色）/ 左下角勾选项 ——
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.Center),
        enter = FreeChatAnimation.dialogEnter(),
        exit = FreeChatAnimation.dialogExit()
    ) {
        BoxWithConstraints(
            modifier = if (tablet) Modifier.systemBarsPadding().padding(24.dp) else Modifier
        ) {
            // 正常窗口只滚正文；矮窗口或大字号让整卡可滚，按钮和勾选项仍能到达。
            val scrollWholeCard = tablet && (maxHeight < 400.dp || fontScale >= 1.5f)
            Column(
                modifier = Modifier
                    .then(if (tablet) Modifier.widthIn(max = 520.dp).heightIn(max = maxHeight) else Modifier)
                    .fillMaxWidth()
                    .padding(horizontal = if (tablet) 0.dp else 34.dp)
                    // 卡片材质与输入框/设置卡同源（frostedCard：新拟态双影 + 校准落影）。
                    // 早先用弹层的 frostedGlass 10dp、降级还无影 —— 阴影比站内卡片糙一截（2026-10-11 用户点名）
                    .frostedCard(hazeState, colors, advancedMaterial, shape, blur = 24.dp, elevation = 8.dp)
                    .then(if (scrollWholeCard) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                Text(
                    title,
                    color = colors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    body,
                    color = colors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    modifier = if (tablet && !scrollWholeCard) Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()) else Modifier
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 副按钮（暂不）：中性浅底 —— 浅色主题下就是「白色」
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (tablet) Modifier.heightIn(min = 48.dp) else Modifier.height(40.dp))
                            .clip(RoundedCornerShape(13.dp))
                            .background(colors.Surface)
                            .border(1.dp, colors.InputBorder, RoundedCornerShape(13.dp))
                            .clickable { onDismiss(checked) }
                            .then(if (tablet) Modifier.padding(horizontal = 8.dp, vertical = 10.dp) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(dismissLabel, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary,
                            fontWeight = FontWeight.Medium, textAlign = if (tablet) TextAlign.Center else TextAlign.Unspecified)
                    }
                    // 主按钮（去清理/去更新）：右侧 + 主题色实底，位置与颜色双重引导
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (tablet) Modifier.heightIn(min = 48.dp) else Modifier.height(40.dp))
                            .clip(RoundedCornerShape(13.dp))
                            .background(colors.Primary)
                            .clickable { onConfirm(checked) }
                            .then(if (tablet) Modifier.padding(horizontal = 8.dp, vertical = 10.dp) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(confirmLabel, style = MaterialTheme.typography.bodyMedium, color = colors.OnPrimary,
                            fontWeight = FontWeight.SemiBold, textAlign = if (tablet) TextAlign.Center else TextAlign.Unspecified)
                    }
                }
                Spacer(Modifier.height(6.dp))
                // 左下角勾选项（永不提醒 / 近期不再提醒）—— 勾选框缩到 0.85 倍（2026-10-11 用户点名偏大）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { checked = it },
                        colors = CheckboxDefaults.colors(checkedColor = colors.Primary),
                        modifier = Modifier.scale(0.85f)
                    )
                    Text(
                        checkboxLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.TextSecondary,
                        modifier = Modifier.clickable { checked = !checked }
                            .then(if (tablet) Modifier.heightIn(min = 48.dp)
                                .wrapContentHeight(Alignment.CenterVertically) else Modifier)
                    )
                }
            }
        }
    }
}
