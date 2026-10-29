package com.freechat.i18n

val AppStrings.imageReadyNotification: String get() = when (localeCode) {
    "en" -> "Your image is ready"
    "zh-TW" -> "圖片已生成"
    else -> "图片已生成"
}
