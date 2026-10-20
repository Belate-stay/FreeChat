package com.freechat.model

/**
 * 显示大小档位。共 5 档，文字与 dp 卡片、控件、间距统一缩放，并按可用空间重排。
 */
enum class FontSize(val scale: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.12f),
    XLARGE(1.24f),
    XXLARGE(1.4f)
}
