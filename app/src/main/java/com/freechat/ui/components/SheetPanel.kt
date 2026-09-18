package com.freechat.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalMonoFontFamily
import com.freechat.ui.theme.frostedGlass
import com.freechat.ui.theme.linkInk
import com.freechat.ui.theme.liquidOpaqueBackground
import com.freechat.ui.theme.selectedFill
import com.freechat.ui.theme.selectedSubText
import com.freechat.ui.theme.selectedText
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * 底部磨砂玻璃弹层 —— 全 App 二级选择框 / 操作菜单的**唯一**外壳。
 *
 * 为什么不用 `ModalBottomSheet` 或 `AlertDialog`：两者都是**独立窗口**（Popup / Dialog），
 * 独立窗口看不到 App 自己已经画好的内容，所以「背景模糊」这件事它们做不到，
 * 只能把背景压暗 —— 那就是用户说的「悬浮卡片背景加暗」，一整类页面都得换掉。
 * 这一版要的恰恰是模糊不压暗，所以只能在**窗口内**自己搭一个：
 *   1. 拦截层：铺满整屏，自己就是一层 haze —— 把背后整页糊掉（不加深色遮罩），点它关掉；
 *   2. 卡片：从底部滑上来，用与侧滑页菜单/输入框同款 [frostedGlass]（真模糊 + 白 tint + 描边）。
 *
 * 高级材质关掉时退化成「深色遮罩 + 实色卡片」—— 那时候整个 App 都没有模糊可言，
 * 这里单独糊一层反而突兀。
 *
 * **调用位置有讲究**：它必须挂在页面根 `Box` 的**最后一个子节点**上（所以是 [BoxScope] 扩展，
 * 要靠 `align` 贴底）。挂进 Column / 滚动容器里会被父布局约束住，铺不满整屏。
 */
@Composable
fun BoxScope.SheetPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    colors: FreeChatColors,
    isDark: Boolean,
    advancedMaterial: Boolean,
    hazeState: HazeState,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    /** 主按钮不可用（密码还没填之类）。灰掉而不是隐藏 —— 位置一直在，用户知道该点哪 */
    confirmEnabled: Boolean = true,
    /** 危险操作（注销账号/删除）用红底，别让「注销」长得跟「确定」一模一样 */
    confirmDanger: Boolean = false,
    /** 内容区高度上限。选项多的选择器靠它收口，超出来的部分在面板内滚动 */
    maxContentHeight: androidx.compose.ui.unit.Dp = 420.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    // 返回键先关弹层（声明得比页面自己的 BackHandler 晚，所以由它先接）
    BackHandler(enabled = visible) { onDismiss() }

    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    AnimatedVisibility(
        visible = visible,
        // 遮罩比卡片早一点点起、晚一点点收：模糊先铺满，卡片再落上去，收的时候反过来
        enter = fadeIn(tween(200, easing = FreeChatAnimation.iosEaseOut)),
        exit = fadeOut(tween(160, easing = FreeChatAnimation.iosEaseIn))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (advancedMaterial) Modifier
                        // 衬底：流动炫彩下补不透明底 —— 整屏遮罩同理，源是透明的那层样本就磨不出东西，
                        // 遮罩只剩一点点罩色，背后正文依旧清清楚楚（见 liquidOpaqueBackground）
                        .liquidOpaqueBackground(colors.Background)
                        .hazeEffect(state = hazeState) {
                        blurRadius = 22.dp
                        inputScale = HazeInputScale.None
                        // 背景模糊而不是变暗：底色留空，只加一点点白/黑把对比拉开
                        backgroundColor = if (isDark) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.16f)
                    } else Modifier.background(Color.Black.copy(alpha = 0.32f))
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        // 弹层上滑：320ms 减速入位（稳稳停住，收尾不「撞墙」），
        // 收起 240ms 加速 —— 2026-09 用户点名「参考 iOS」的那一套节奏
        enter = slideInVertically(animationSpec = tween(320, easing = FreeChatAnimation.iosEaseOut)) { it } +
            fadeIn(tween(200, easing = FreeChatAnimation.iosEaseOut)),
        exit = slideOutVertically(animationSpec = tween(240, easing = FreeChatAnimation.iosEaseIn)) { it } +
            fadeOut(tween(160, easing = FreeChatAnimation.iosEaseIn))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (advancedMaterial) Modifier.frostedGlass(hazeState, isDark, shape, blur = 30.dp, elevation = 10.dp)
                    else Modifier.background(colors.Surface, shape)
                )
                // 底部安全区 = max(导航栏, 键盘)。**必须用 safeDrawing 而不是 navigationBarsPadding**：
                // 这个 App 是 adjustNothing + edge-to-edge（见 AndroidManifest / enableEdgeToEdge），
                // 系统不会把窗口顶上去，键盘弹出来时是我们自己让位。两条加起来写会重复计算
                // （ime 的高度里本来就含导航栏那一段），safeDrawing 取的是两者的并集，
                // 键盘关着时是导航栏、弹起来时正好是键盘，一格不多一格不少。
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp)
        ) {
            // 抓手：给「这是一张可以点外面关掉的弹层」一个看得见的提示
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.TextTertiary.copy(alpha = 0.35f))
            )
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxContentHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                content()
            }
            if (confirmLabel != null && onConfirm != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            (if (confirmDanger) colors.ErrorRed else colors.Primary)
                                .copy(alpha = if (confirmEnabled) 1f else 0.38f)
                        )
                        .clickable(enabled = confirmEnabled) { onConfirm() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(confirmLabel, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * 弹层里的一行选项。
 *
 * 选中态用**主题色实心底**（`selectedFill`，与弹层的「确定」按钮同一支颜色，
 * 纯白主题下就是一块近黑的板子），未选中一律透明 ——
 * 卡片本身就是一块玻璃，未选中的行再各自铺一层底色，材质就花了。
 *
 * 1.0.50：从这里拿掉了原来的 ✓ 图标。底色已经把「选中的是哪个」说完了，
 * 再挂个勾等于同时用两套语言；主题 / 色彩 / 语言 / 温度 / 长度 / 字号 / 模型
 * 这些选项表现在全都是同一个样子。
 */
@Composable
fun SheetOption(
    selected: Boolean,
    title: String,
    colors: FreeChatColors,
    onClick: () -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    /** 标题走等宽字体（模型名、MBTI 类型码这类"代码感"的字符串就用它） */
    monoTitle: Boolean = false,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.selectedFill else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = if (selected) colors.selectedText else colors.TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (selected) colors.selectedText else colors.TextPrimary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    // null = 沿用外层样式（字号/行高不动，只换字体族）
                    fontFamily = if (monoTitle) LocalMonoFontFamily.current else null
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                        color = if (selected) colors.selectedSubText else colors.TextSecondary
                    )
                }
            }
        }
        extra()
    }
}

/**
 * 弹层里的**操作行**（不是选项，是"点了就干一件事"）。头像菜单、分享菜单用它。
 *
 * 原来是 AlertDialog 里那种朴素的一行字，这里给上圆角、内边距和按下的底色，
 * 让它跟 [SheetOption] 排在一起时是同一套东西。
 *
 * [onClick] 故意留在**最后一个参数位**：这样 `SheetActionRow(文案, colors) { ... }` 的尾随
 * lambda 一定落在点击上。账号页那个「点了没反应」的老 bug 就是参数位错开一位的后果
 * （`ActionRow(文案, colors, false)` 顺手把 enabled 关成了 false），同样的坑不踩第二次。
 */
@Composable
fun SheetActionRow(
    label: String,
    colors: FreeChatColors,
    danger: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    /**
     * 蓝色 + 斜体 + 下划线：二级风险弹层里那两行「仍然开启 / 仍然关闭」用的。
     * 它们原来各写各的（一个红的、一个正文色），谁也不像「能点」——用户的原话是
     * 「太不显眼，根本不像一个可点的选项」。链接样式是全平台通用的「这里能点」符号，
     * 所以统一成这一种（见 [com.freechat.ui.theme.linkInk]）。
     */
    link: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                null,
                tint = if (danger) colors.ErrorRed else colors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            label,
            style = if (link) MaterialTheme.typography.bodyMedium.copy(
                fontStyle = FontStyle.Italic,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Medium
            ) else MaterialTheme.typography.bodyMedium,
            color = when {
                !enabled -> colors.TextTertiary
                link -> colors.linkInk
                danger -> colors.ErrorRed
                else -> colors.TextPrimary
            }
        )
    }
}
