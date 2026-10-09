package com.freechat.data

import com.freechat.model.ColorTheme

/** Both parts of the palette come from the same persisted preference snapshot. */
data class ThemeSelection(
    val theme: ColorTheme = ColorTheme.WHITE,
    val customColorArgb: Int = 0xFF346C98.toInt()
)
