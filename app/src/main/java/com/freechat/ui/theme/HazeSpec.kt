package com.freechat.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 高级材质高斯模糊统一规格：固定内容后方充分模糊，靠近可读正文时连续降到零。
 * 主页面与侧栏共用半径/羽化，但模糊范围分别由标题栏与固定悬浮头部推导。
 *
 * 单一真源原则：
 * - 带高与 HazeProgressive 的 endY 必须由同一表达式产出，禁止「带子 88dp、endY 96dp」式硬切；
 * - 标题栏、固定卡片、初始列表位置不随材质开关改变；模糊层只覆盖滚入该区域的正文。
 */
object HazeSpec {
    /** 顶部标题栏模糊半径（8dp 太淡看不见 → 16dp，磨砂玻璃肉眼清晰可感） */
    val TopBlurRadius = 16.dp

    /** 整段渐变上移 24dp，收回正文占用；104dp 曲线及末端 36dp 羽化均保持不变。 */
    val TopFadeZoneDp = 32.dp

    /** 保持已校准的 104dp 物理渐变长度，不因下缘上移而压缩曲线。 */
    val TopProgressiveSpanDp = 104.dp

    /** 侧栏在固定悬浮头部内使用同样的长渐变；终点不越过列表初始起点。 */
    val DrawerTopFadeZoneDp = 104.dp

    /** 最后 36dp 连模糊结果、噪点及采样色差一起渐隐，淡入段也随半径渐变拉长。 */
    val TopOutputFeatherDp = 36.dp

    /** 卡片位置不动，仅下方留白增加 12dp；列表同量后移，置顶分组初始仍不进入模糊层。 */
    val DrawerHeaderBottomGapDp = 24.dp

    /** 遮挡面的边缘向两侧羽化；材质切换时随进度展开，普通标题栏仍保持原高度。 */
    val HeaderUnderlayFeatherDp = 24.dp

    /** 两端一、二阶导数为零，中间匀速变化；不把模糊增长挤到 S 曲线中段。 */
    val TopBlurEasing = Easing(::topBlurFadeProgress)

    fun topBlurFadeProgress(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        // 首尾各 1/6 以 smoothstep 平滑加减速，中间 2/3 的斜率恒为 1.2。
        // 比三次/五次 S 曲线的 1.5/1.875 峰值更均匀；每 dp 最大变化仅 1.2/104。
        // 对称计算避免接近清晰端时浮点相消造成微小反向跳变。
        val x = if (t <= 0.5f) t else 1f - t
        val cap = 1f / 6f
        val slope = 1f / (1f - cap)
        val value = if (x < cap) {
            val q = x / cap
            slope * cap * q * q * q * (1f - 0.5f * q)
        } else slope * (x - 0.5f * cap)
        return if (t <= 0.5f) value else 1f - value
    }

    /** 遮挡面/输出透明度独立使用五次曲线，两端一、二阶导数为零。 */
    fun smoothFadeProgress(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        // 对称计算避免 t 接近 1 时多项式相消，使最下缘也保持单调且精确归零。
        val x = if (t <= 0.5f) t else 1f - t
        val value = x * x * x * (x * (6f * x - 15f) + 10f)
        return if (t <= 0.5f) value else 1f - value
    }

    /** 从清晰下缘向内的输出不透明度；两端一、二阶导数为零，标题区域仍保持完整模糊。 */
    fun topOutputAlpha(fractionFromBottom: Float): Float = smoothFadeProgress(fractionFromBottom)

    /** 标题栏内容区高度 —— 两页共用（原先 ChatScreen / DrawerContent 两处各写 48.dp） */
    val TitleBarAreaDp = 48.dp

    /** 底部模糊半径（3dp 几乎不可察觉 → 10dp） */
    val BottomBlurRadius = 10.dp

    /** 底部模糊层高度 —— 同时也是底部渐变 endY，禁止再出现第二个数字 */
    val BottomFadeHeightDp = 96.dp

    /**
     * 接缝羽化宽度：模糊带贴缝一侧的模糊强度在这段距离内衰减到 0。
     *
     * 缝本身没有内容可模糊、无法被填色，只能让「缝 / 缝左 / 缝右」三处都退化成同一背景色。
     * 主页面和侧栏按各自固定内容高度模糊，但流光仍使用相同的全局坐标，
     * 此处只保留贴缝处的细微羽化，不用扩大它掩盖纵向范围差异。
     *
     * **只能开到 8dp。** 羽化是从「带子贴缝那条边」往里衰减的，Chat 页的文字流从 x=16dp 开始、
     * 输入框卡片也从 16dp 开始 —— 羽化一旦超过 16dp，最先出现的几个字、卡片的左边缘就落在
     * 半衰减区里，看起来就是「左边没糊上」。色差那条线的主因（副本跟着页面平移）已在
     * [LocalLiquidPageShift] 里从根上解决，这里只留一点点余量兜底，不再靠它掩盖问题。
     *
     * 置 0.dp 即整个 `seamFeather` 修饰符失效（完全不建离屏层），作为一键回退开关。
     */
    val SeamFeatherDp = 8.dp

    /**
     * 主页面顶部模糊带 = 状态栏 + 标题栏 + 渐隐区。
     * 带高与 endY 必须由这同一个函数产出：两者只要差一点，渐变就会在带子末端被硬切一刀。
     */
    fun topBandHeightDp(statusBarDp: Dp): Dp = titleBarHeightDp(statusBarDp) + TopFadeZoneDp

    /** 普通实心标题栏的高度不跟随高级材质模糊区增长。 */
    fun titleBarHeightDp(statusBarDp: Dp): Dp = statusBarDp + TitleBarAreaDp

    /** 侧栏下缘恰好到最后一张固定卡片下方的留白，不额外侵入「置顶」分组。 */
    fun drawerTopBandHeightDp(statusBarDp: Dp, fixedHeaderDp: Dp): Dp =
        titleBarHeightDp(statusBarDp) + fixedHeaderDp

    /** 起点可延伸到视口上方：只平移曲线，不在短状态栏/横屏下重新压缩羽化。 */
    fun topFadeStartDp(bandHeight: Dp, fadeHeight: Dp = TopProgressiveSpanDp): Dp =
        bandHeight - fadeHeight

    /** 页面原有布局留白独立于模糊带，避免每次调模糊范围都把设置项往下推。 */
    fun topContentPaddingDp(statusBarDp: Dp, originalGap: Dp = 8.dp): Dp =
        titleBarHeightDp(statusBarDp) + originalGap

    /** 底部模糊带高度（= 底部渐变 endY 的唯一来源） */
    fun bottomBandHeightDp(): Dp = BottomFadeHeightDp
}
