package com.freechat.model

/** Stable IDs, not ordinals: adding an icon must never change an existing user's selection. */
enum class AppIcon(val id: String, val alias: String, val enabledByDefault: Boolean) {
    BLUE_F("blue_f", "LauncherBlueF", true),
    CLASSIC("classic", "LauncherClassic", false),
    LUNHUI("lunhui", "LauncherLunhui", false),
    GONGMING("gongming", "LauncherGongming", false),
    HUANMENG("huanmeng", "LauncherHuanmeng", false),
    XINSHENG("xinsheng", "LauncherXinsheng", false),
    RIXIANG("rixiang", "LauncherRixiang", false),
    HAILUO("hailuo", "LauncherHailuo", false);

    companion object {
        val default = BLUE_F
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: default
    }
}

/** Older Android versions must enable the destination before disabling other launch entries. */
fun AppIcon.activationOrder(): List<AppIcon> = listOf(this) + AppIcon.entries.filter { it != this }
