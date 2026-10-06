package com.freechat.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.freechat.ui.components.buildStyledLine

/** Keep inline formatting and literal code consistent in chat, single/bulk shares, and saved images. */
internal fun shareStyledLine(text: String) = buildStyledLine(
    text, Color(0xFF1B1B1B), FontWeight.Normal, 40.sp,
    linkColor = Color(0xFF0759B5),
)
