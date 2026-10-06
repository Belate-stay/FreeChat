package com.freechat.model

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
    DARK_OLED("黑色")
}

enum class ColorTheme(val label: String) {
    BROWN("浅棕"),
    BLUE("浅蓝"),
    WHITE("黑白"),
    CUSTOM("自定义"),
    PINE("松绿"),
    CORAL("珊瑚红");

    companion object {
        // Persisted ordinals and wire names must remain unchanged. Only the picker order changes.
        val presetsInDisplayOrder = listOf(BROWN, BLUE, PINE, CORAL, WHITE)
    }
}
