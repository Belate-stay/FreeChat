package com.freechat.ui.adaptive

import com.freechat.i18n.AppStrings

internal fun AppStrings.tabletText(zh: String, tw: String, en: String): String = when (localeCode) {
    "en" -> en
    "zh-TW" -> tw
    else -> zh
}

internal val AppStrings.expandPane get() = tabletText("全屏显示此面板", "全螢幕顯示此面板", "Expand this pane")
internal val AppStrings.restorePanes get() = tabletText("恢复双栏", "恢復雙欄", "Restore split view")
internal val AppStrings.selectFavorite get() = tabletText("选择收藏以阅读", "選擇收藏以閱讀", "Select a favorite to read")
