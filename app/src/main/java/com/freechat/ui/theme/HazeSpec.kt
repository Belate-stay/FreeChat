package com.freechat.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 高级材质高斯模糊统一规格 —— Chat 页与侧滑页共用，保证「一模一样」。
 * 模糊做淡：只弱化文字、不做「不可读」的强模糊。
 */
object HazeSpec {
    /** 顶部标题栏模糊半径（8dp 太淡看不见 → 16dp，磨砂玻璃肉眼清晰可感） */
    val TopBlurRadius = 16.dp

    /** 顶部模糊在标题栏下方多延伸的渐隐区 */
    val TopFadeZoneDp = 16.dp

    /** 底部模糊半径（3dp 几乎不可察觉 → 10dp） */
    val BottomBlurRadius = 10.dp

    /** 底部模糊层高度 */
    val BottomFadeHeightDp = 96.dp
}
