package com.freechat.ui.adaptive

import com.freechat.i18n.AppStrings
import com.freechat.model.TabletDisplayMode
import com.freechat.model.TabletFloatingSide

internal val AppStrings.tabletDisplayOptimization
    get() = tabletText("平板显示优化", "平板顯示最佳化", "Tablet display")

internal val AppStrings.tabletPageDisplayLogic
    get() = tabletText("页面显示逻辑", "頁面顯示邏輯", "Page display")

internal val AppStrings.tabletFloatingPosition
    get() = tabletText("悬浮位置", "懸浮位置", "Floating position")

internal val AppStrings.tabletUnderlyingTouch
    get() = tabletText("底层触控", "底層觸控", "Underlying touch")

internal fun AppStrings.tabletDisplayModeLabel(mode: TabletDisplayMode): String = when (mode) {
    TabletDisplayMode.EMBEDDED -> tabletText("嵌入", "嵌入", "Embedded")
    TabletDisplayMode.FLOATING -> tabletText("悬浮", "懸浮", "Floating")
}

internal fun AppStrings.tabletFloatingSideLabel(side: TabletFloatingSide): String = when (side) {
    TabletFloatingSide.BOTH -> tabletText("两侧悬浮", "兩側懸浮", "Both sides")
    TabletFloatingSide.LEFT -> tabletText("左侧悬浮", "左側懸浮", "Left side")
    TabletFloatingSide.RIGHT -> tabletText("右侧悬浮", "右側懸浮", "Right side")
}
