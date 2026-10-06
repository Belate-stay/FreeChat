package com.freechat.model

/**
 * 输入框样式（1.0.70）。
 *
 * [COMPACT] 简洁 —— 1.0.69 及以前的现状：单行胶囊输入框，超过 3 行自动弹全屏输入卡片。
 * [COMPLETE] 完整 —— 全屏输入与标准输入合二为一：两行完整输入框（上行文字、下行状态快捷键），
 *            文字换行就地向上长高（封顶到原全屏输入高度后内部滚动），不再弹全屏卡片。
 *
 * ordinal 即 DataStore 里的存值（0/1），老数据缺键读出 0 = 简洁 = 现状，零迁移。
 */
enum class InputStyle {
    COMPACT,
    COMPLETE
}

/**
 * 输入框状态（1.0.71）。
 *
 * [AUTO_HIDE] 自动隐藏 —— 现状：上滑回翻旧消息时隐藏输入框（含底部渐变模糊层），下滑回到底部恢复。
 * [PINNED]    永久固定 —— 输入框固定在屏幕下方，不触发隐藏动画（与「引用/图片卡片在场时固定显示」同款语义）。
 *
 * ordinal 即 DataStore 里的存值（0/1），老数据缺键读出 0 = 自动隐藏 = 现状，零迁移。
 */
enum class InputBarState {
    AUTO_HIDE,
    PINNED
}
