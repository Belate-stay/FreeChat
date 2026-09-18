package com.freechat.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// ============================================================================
//  材质系统
// ============================================================================
//
//  两条路线，一句话区分：
//    · 新拟态（凸起/凹进）——「从背景里挤出来的一块」，靠**双向光影**定形，卡面颜色≈背景颜色。
//    · 磨砂玻璃        ——「飘在内容上面的一片雾」，靠**模糊 + 半透明底**定形，用于悬浮菜单/弹层。
//
//  两者都用 [softShadow] 自绘阴影，**一律不用 `Modifier.shadow`**，原因见该函数。

/** 8 层同心圆角矩形叠出的柔光阴影（伪模糊）。层数越少越省、越多越顺；8 层在手机上已经看不出台阶。 */
private const val SHADOW_LAYERS = 8

/**
 * 自绘柔光阴影 —— **刻意不用 `Modifier.shadow`**，两个原因：
 *
 * 1. 系统阴影走 RenderNode 的 spot shadow，当节点内容是「自己绘制的一层」时（Haze 磨砂、
 *    或者半透明内容）它会退化成**一圈硬边灰环**。收藏页空态右上角那个菜单卡片就是这么坏的：
 *    底下没有内容可供采样时，那个灰环就明晃晃地露出来。
 * 2. 系统阴影只能往**右下**投，方向被写死。新拟态要的「左上高光」它压根画不出来。
 *
 * 这里用 N 层同心圆角矩形叠柔光：越往外越大、越淡，合成出一条平滑的衰减带。
 * 纯几何绘制，比一次真实高斯模糊便宜一个数量级，而且方向、颜色、强度全部可控。
 *
 * @param dx/dy   阴影相对卡片的位移（左上为负、右下为正）
 * @param spread  衰减带宽度
 * @param tone    阴影色 —— 新拟态的铁律：**必须接近背景色**，不能用纯黑/纯白
 * @param maxAlpha 最里那一层的 alpha。**注意它不是肉眼看到的黑度**：8 层在卡片边缘处
 *   全都叠在一起，实际黑度 ≈ maxAlpha × 2.4（第 0 层 0.07 → 边缘约 0.16）。
 *   手感上「边缘一圈太黑」的锅多半在这儿，而不是 dy/spread —— 调之前先按 ×2.4 估一下。
 */
fun Modifier.softShadow(
    shape: Shape,
    dx: Dp = 0.dp,
    dy: Dp = 4.dp,
    spread: Dp = 12.dp,
    tone: Color = Color.Black,
    maxAlpha: Float = 0.18f
): Modifier = this.drawBehind {
    val corner = cornerRadiusOf(shape)
    drawSoftShadow(corner, dx.toPx(), dy.toPx(), spread.toPx(), tone, maxAlpha)
}

/** [softShadow] 的画布实现（[drawBehind] 里只有 DrawScope，拿不到 Modifier 上下文） */
private fun DrawScope.drawSoftShadow(
    corner: CornerRadius,
    dx: Float,
    dy: Float,
    spread: Float,
    tone: Color,
    maxAlpha: Float
) {
    if (size.width <= 0f || size.height <= 0f || maxAlpha <= 0f) return
    for (i in 0 until SHADOW_LAYERS) {
        val t = i / (SHADOW_LAYERS - 1f)
        // (1-t)² 的衰减：靠里几乎实心、靠外迅速化开，这正是柔光该有的手感
        val alpha = maxAlpha * (1f - t) * (1f - t)
        if (alpha < 0.004f) continue
        val grow = spread * t
        drawRoundRect(
            color = tone.copy(alpha = alpha),
            topLeft = Offset(dx - grow, dy - grow),
            size = Size(size.width + grow * 2f, size.height + grow * 2f),
            cornerRadius = CornerRadius(
                (corner.x + grow).coerceAtLeast(0f),
                (corner.y + grow).coerceAtLeast(0f)
            )
        )
    }
}

/** 从任意 [Shape] 里取出圆角半径。方形/自定义形状一律当 0 处理，调用方不必关心。 */
private fun DrawScope.cornerRadiusOf(shape: Shape): CornerRadius =
    when (val o = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rounded -> o.roundRect.topLeftCornerRadius
        else -> CornerRadius.Zero
    }

/**
 * 卡片统一材质。
 *
 * 非高级材质：实色卡片 + 浅阴影，安稳、不抢戏。
 *
 * 高级材质：**新拟态（Neumorphism / Soft UI）**。四条原则一条不能少：
 *   ① 左上角受光立面 —— 左上打**亮**阴影（比背景亮、偏上偏左外投影）
 *   ② 右下角暗部立面 —— 右下打**暗**阴影（比背景暗、偏下偏右外投影）
 *   ③ 圆角过渡   —— 卡面与光影都走同一条圆角，不能有直角硬边
 *   ④ 卡面即背景 —— **卡面颜色必须等于背景颜色**，
 *      这是新拟态和「普通卡片」的唯一区别：卡片不是贴上去的，是从背景里挤出来的。
 *
 * ①②是同一件事的两半，**缺一条就散架**：只有暗影 = 普通的投影卡片（看着就是「浮」的），
 * 只有亮影 = 一圈发光。参数与画法见 [Neumorph.kt]。
 *
 * 早期版本两条都栽过：卡面写死 `SurfaceVariant`（比背景明显偏白的色）→ 一张白卡浮着；
 * 只画一条暗影 → 卡片没有「面」，只剩一圈脏阴影。
 *
 * [recessed] = true 出「凹进」效果（把高光与暗部对调，即 CSS 里的 inset），
 * 用于搜索框、输入槽这类「凹下去」的元素，与凸起的卡片形成主次。
 *
 * [neumorph] = false 用于 Popup / DropdownMenu 这类**独立窗口**里的卡片：那里的窗口是
 * 透明的，新拟态没有「背景」可以嵌进去，出来会是一块悬空的软糖。这类地方维持原来的实色卡。
 *
 * selected = true 用**主题色实心底**（`selectedFill`，与弹层的「确定」按钮同色）表示选中态。
 * 1.0.50 之前这里用的是 AccentMuted 淡底（「绝不发黑」），用户要求统一成确定按钮那口颜色，
 * 于是纯白主题下选中的卡片就是一块近黑的板子 —— 调用方的文字色也得跟着换成 `selectedText`。
 */
@Composable
fun Modifier.frostedCard(
    hazeState: HazeState?,
    colors: FreeChatColors,
    advancedMaterial: Boolean,
    shape: Shape,
    blur: Dp = 24.dp,
    elevation: Dp = 8.dp,
    selected: Boolean = false,
    fallback: Color = colors.Surface,
    neumorph: Boolean = true,
    /** 凹进（inset）：高光与暗部对调，元素看起来是「陷进去」的 */
    recessed: Boolean = false,
    /**
     * 卡面覆盖色。默认（null）走新拟态的铁律「卡面 = 背景色」；
     * 传值就整块换成它 —— 选项表里「选中」那张卡传 `colors.selectedFill`，
     * 于是底色就是确定按钮那口颜色，三档材质（高级材质 / 新拟态 / 朴素）都吃得到。
     */
    face: Color? = null,
    /** 紧凑档：小方块（选项 chip）用，位移与模糊按比例减小，否则 6dp 的厚度会吃掉整个 chip */
    compact: Boolean = false
): Modifier {
    val isDark = colors.TextPrimary.luminance() > 0.5f

    // 阴影强度见 [softShadow] 的注释：8 层叠起来边缘的真实黑度约是 maxAlpha 的 2.4 倍，
    // 所以这里的数值看着小，实际落影刚刚好。早先非高级材质这一段写的是 0.16 —— 边缘实际
    // 压到 0.38，卡片四周一圈又黑又紧的晕，用户的原话是「像黑眼圈」。现在 0.07（约 0.16 实际），
    // 并把 dy 加大一点让它变成「落影」而不是「四周的圈」。
    if (!advancedMaterial) {
        return this
            .softShadow(
                shape,
                dx = 0.dp, dy = 3.dp, spread = 9.dp,
                tone = Color.Black, maxAlpha = if (isDark) 0.11f else 0.07f
            )
            .clip(shape)
            // 选中态 = 主题色实心（1.0.50 统一，见 Color.kt 的 selectedFill）；face 显式给了就听 face 的
            .background(face ?: if (selected) colors.selectedFill else fallback)
    }

    if (!neumorph) {
        return this
            .softShadow(
                shape,
                dx = 0.dp, dy = elevation * 0.55f, spread = elevation * 1.5f,
                tone = Color.Black, maxAlpha = if (isDark) 0.13f else 0.08f
            )
            .clip(shape)
            .background(face ?: if (selected) colors.selectedFill else colors.SurfaceVariant)
            .background(
                // 顶部高光：淡淡一层白，向下渐隐，模拟玻璃反光
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.06f else 0.16f),
                        Color.Transparent
                    )
                )
            )
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.35f),
                shape
            )
    }

    // ── 新拟态 ──
    // 卡面 = 一道极轻的受光渐变（左上亮、右下暗），外加**两条方向相反的阴影**：
    // 左上一条亮的把受光面顶起来、右下一条暗的退到板子后面。原理与参数全在 [Neumorph.kt]。
    //
    // 前两稿各错一半：一稿卡面写死 `SurfaceVariant`（比背景明显白）+ 白描边 + 只有一条暗影
    // → 一张发光的白卡浮着；二稿把卡面整个拿掉（纯透明）+ 还是只有一条暗影 → 卡片没了「面」。
    //
    // 炫彩开着时卡面只留 25% 的纱：背景那幅光晕渐变原样穿过卡片，
    // 两条阴影的色相也跟着画面的冷暖光晕走 —— 卡与背景才是「一块料子」而不是「两块」。
    val liquid = if (LocalLiquidMode.current) LocalLiquidPalette.current else null
    val tone = neumorphTone(colors, isDark, selected, compact, liquid)

    return this
        // 外阴影（凸起）：只画在卡片外面（画进卡里会从半透明的卡面里透出来，见 Neumorph.kt）
        .then(if (recessed) Modifier else Modifier.drawBehind { drawNeumorph(shape, tone, recessed = false) })
        .clip(shape)
        .background(
            brush = Brush.linearGradient(listOf(tone.faceTop, tone.faceBottom)),
            shape = shape,
            alpha = tone.faceAlpha
        )
        // 覆盖色（选中态的主题色淡底）铺在这层「面」**上面**，是叠加不是替代
        .then(if (face != null) Modifier.background(face) else Modifier)
        // 内阴影（凹进）：必须被形状裁着画，所以放到 clip 与卡面之后
        .then(if (recessed) Modifier.drawBehind { drawNeumorph(shape, tone, recessed = true) } else Modifier)
}

/**
 * 透光不透物的磨砂哑光玻璃：真实高斯模糊（Haze）+ 白/黑半透明底 + 白色高光 tint + 白描边。
 *
 * 与 Chat 输入框同款质感，是全 App 统一的**悬浮层材质** —— 侧滑页的搜索/新对话、
 * 对话操作菜单、底部弹层、收藏页的调节菜单都该长这样，看起来才是同一套东西。
 *
 * 阴影用自绘的 [softShadow] 而不是 `Modifier.shadow`：后者在「底下没有内容可采样」时
 * 会退化成硬边灰环（收藏页空态那个菜单的坏法），自绘的不会。
 *
 * 注意：只能在有 hazeSource 的同一窗口内使用，Popup/DropdownMenu 独立窗口糊不到背景，别用这个
 * —— 独立窗口里的浮层请改用「页面内 overlay + 这个材质」。
 */
@Composable
fun Modifier.frostedGlass(
    hazeState: HazeState,
    isDark: Boolean,
    shape: Shape,
    blur: Dp = 28.dp,
    elevation: Dp = 8.dp,
    /**
     * 是否在玻璃下面垫一层不透明底（[liquidOpaqueBackground]，只在流动炫彩下画）。
     *
     * 默认要垫：Haze 的模糊样本是「源节点那一层」，炫彩下源节点是透明的，
     * 样本盖不住下面的正文，玻璃就成了半透明 PPT 图层（详见 [liquidOpaqueBackground]）。
     * 只有**写在 hazeSource 节点内部**的玻璃才必须传 false —— 源节点里画会动的东西会被冻住，
     * 那块玻璃连同整个列表都会停在第一帧（本 App 里就是列表行的右键菜单，见 DrawerContent）。
     */
    underlay: Boolean = true,
): Modifier {
    val base = LocalFreeChatColors.current.Background
    return this
        .softShadow(
            shape,
            dx = 0.dp,
            dy = elevation * 0.55f,
            spread = elevation * 1.6f,
            tone = Color.Black,
            maxAlpha = if (isDark) 0.13f else 0.07f
        )
        .clip(shape)
        .then(if (underlay) Modifier.liquidOpaqueBackground(base) else Modifier)
        .hazeEffect(state = hazeState) {
            blurRadius = blur
            inputScale = HazeInputScale.None
            // 哑光感来自「底色给足、tint 很淡」：底色太透会变成亮面玻璃，太实又丢了透光
            backgroundColor = if (isDark) Color.Black.copy(alpha = 0.46f) else Color.White.copy(alpha = 0.66f)
            tints = listOf(HazeTint(if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.16f)))
        }
        .border(1.dp, if (isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.58f), shape)
}
