package com.freechat.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 高级材质高斯模糊统一规格 —— Chat 页与侧滑页共用，保证「一模一样」。
 * 模糊做淡：只弱化文字、不做「不可读」的强模糊。
 */
object HazeSpec {
    /** 顶部标题栏模糊半径（18dp 太重 → 8dp，淡淡的弱化即可） */
    val TopBlurRadius = 8.dp

    /** 顶部模糊在标题栏下方多延伸的渐隐区（32dp → 12dp，避免误伤第一条消息） */
    val TopFadeZoneDp = 12.dp

    /** 底部模糊半径（5dp → 3dp，几乎不可察觉） */
    val BottomBlurRadius = 3.dp

    /** 底部模糊层高度 */
    val BottomFadeHeightDp = 88.dp
}
