package com.freechat.model

/**
 * 字体大小档位。共 5 档，固定倍率应用到全局 sp 字号（dp 布局不变，文字变大自动重排）。
 */
enum class FontSize(val scale: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.12f),
    XLARGE(1.24f),
    XXLARGE(1.4f)
}
