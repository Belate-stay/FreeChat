package com.freechat.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 高级材质高斯模糊统一规格 —— Chat 页与侧滑页共用，保证「一模一样」。
 * 模糊做淡：只弱化文字、不做「不可读」的强模糊。
 *
 * 单一真源原则：
 * - 带高与 HazeProgressive 的 endY 必须由同一表达式产出，禁止「带子 88dp、endY 96dp」式硬切；
 * - 标题栏高度、渐隐区、模糊半径、接缝羽化宽度两侧共用一个常量。
 */
object HazeSpec {
    /** 顶部标题栏模糊半径（8dp 太淡看不见 → 16dp，磨砂玻璃肉眼清晰可感） */
    val TopBlurRadius = 16.dp

    /** 顶部模糊在标题栏下方多延伸的渐隐区（Chat 页及其余页面沿用） */
    val TopFadeZoneDp = 16.dp

    /** 侧滑页专用：固定头部下方额外延伸的渐隐区（原先为 DrawerContent 内的局部魔法数 28.dp） */
    val TopFadeExtendDp = 28.dp

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
     * 侧滑页顶部模糊带（statusBar+210dp，为盖住 134dp 固定头部）比 Chat 页（statusBar+64dp）
     * 高 3.3 倍，同一条 y 上两侧模糊强度不同，边界处会留下一条「上下明显、中间消失」的亮度台阶。
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
     * 顶部模糊带高度 = 状态栏 + 标题栏 + 页面自有固定内容区 + 渐隐区。
     * 带高与 endY 必须由这同一个函数产出：两者只要差一点，渐变就会在带子末端被硬切一刀。
     */
    fun topBandHeightDp(
        statusBarDp: Dp,
        contentZoneDp: Dp = 0.dp,
        fadeExtendDp: Dp = TopFadeZoneDp
    ): Dp = statusBarDp + TitleBarAreaDp + contentZoneDp + fadeExtendDp

    /** 底部模糊带高度（= 底部渐变 endY 的唯一来源） */
    fun bottomBandHeightDp(): Dp = BottomFadeHeightDp
}
