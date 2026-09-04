package com.freechat.ui.theme

import androidx.compose.ui.graphics.Color

interface FreeChatColors {
    val Background: Color
    val Surface: Color
    val SurfaceVariant: Color
    val SurfaceDim: Color
    val Primary: Color
    val PrimaryVariant: Color
    val OnPrimary: Color
    val Accent: Color
    val AccentMuted: Color
    val TextPrimary: Color
    val TextSecondary: Color
    val TextTertiary: Color
    val UserBubble: Color
    val UserBubbleText: Color
    val AiBubble: Color
    val AiBubbleBorder: Color
    val AiBubbleText: Color
    val InputBg: Color
    val InputBorder: Color
    val InputFocused: Color
    val ChipBg: Color
    val ChipBorder: Color
    val ChipSelected: Color
    val ErrorRed: Color
    val SuccessGreen: Color
    val Divider: Color
    val Scrim: Color
    val NavBg: Color
    val StatusBar: Color
    val NavBar: Color
}

// ============================================================
//  莫兰迪暖棕（默认）
// ============================================================

object LightColors : FreeChatColors {
    override val Background = Color(0xFFF5F0EB)
    override val Surface = Color(0xFFEDE4DB)
    override val SurfaceVariant = Color(0xFFE0D5C8)
    override val SurfaceDim = Color(0xFFD8CDBF)
    override val Primary = Color(0xFFA0856B)
    override val PrimaryVariant = Color(0xFF8B7355)
    override val OnPrimary = Color(0xFFFFFFFF)
    override val Accent = Color(0xFFA0856B)
    override val AccentMuted = Color(0xFFE8DFD6)
    override val TextPrimary = Color(0xFF3D2E24)
    override val TextSecondary = Color(0xFF7A6958)
    override val TextTertiary = Color(0xFFA89888)
    override val UserBubble = Color(0xFFBA9C82)
    override val UserBubbleText = Color(0xFFFFFFFF)
    override val AiBubble = Color(0xFFFFFFFF)
    override val AiBubbleBorder = Color(0xFFD8CDBF)
    override val AiBubbleText = Color(0xFF3D2E24)
    override val InputBg = Color(0xFFFFFFFF)
    override val InputBorder = Color(0xFFD8CDBF)
    override val InputFocused = Color(0xFFA0856B)
    override val ChipBg = Color(0xFFFFFFFF)
    override val ChipBorder = Color(0xFFD8CDBF)
    override val ChipSelected = Color(0xFFE8DFD6)
    override val ErrorRed = Color(0xFFC8554E)
    override val SuccessGreen = Color(0xFF6B9E7A)
    override val Divider = Color(0xFFE0D5C8)
    override val Scrim = Color(0x33000000)
    override val NavBg = Color(0xFFEDE4DB)
    override val StatusBar = Color(0xFFF5F0EB)
    override val NavBar = Color(0xFFEDE4DB)
}

object DarkColors : FreeChatColors {
    override val Background = Color(0xFF1A1815)
    override val Surface = Color(0xFF24211D)
    override val SurfaceVariant = Color(0xFF2D2924)
    override val SurfaceDim = Color(0xFF1A1815)
    override val Primary = Color(0xFFC4A882)
    override val PrimaryVariant = Color(0xFFD4BFA0)
    override val OnPrimary = Color(0xFF1A1815)
    override val Accent = Color(0xFFC4A882)
    override val AccentMuted = Color(0xFF3D3429)
    override val TextPrimary = Color(0xFFF0EBE3)
    override val TextSecondary = Color(0xFFB8A898)
    override val TextTertiary = Color(0xFF8B7765)
    override val UserBubble = Color(0xFF8B7355)
    override val UserBubbleText = Color(0xFFF0EBE3)
    override val AiBubble = Color(0xFF24211D)
    override val AiBubbleBorder = Color(0xFF3D3832)
    override val AiBubbleText = Color(0xFFF0EBE3)
    override val InputBg = Color(0xFF1E1C19)
    override val InputBorder = Color(0xFF3D3832)
    override val InputFocused = Color(0xFFC4A882)
    override val ChipBg = Color(0xFF1E1C19)
    override val ChipBorder = Color(0xFF3D3832)
    override val ChipSelected = Color(0xFF2D2924)
    override val ErrorRed = Color(0xFFD9706A)
    override val SuccessGreen = Color(0xFF7DAE8C)
    override val Divider = Color(0xFF2D2924)
    override val Scrim = Color(0x66000000)
    override val NavBg = Color(0xFF24211D)
    override val StatusBar = Color(0xFF1A1815)
    override val NavBar = Color(0xFF24211D)
}

object OledDarkColors : FreeChatColors {
    override val Background = Color(0xFF000000)
    override val Surface = Color(0xFF0D0D0D)
    override val SurfaceVariant = Color(0xFF1A1A1A)
    override val SurfaceDim = Color(0xFF000000)
    override val Primary = Color(0xFFC4A882)
    override val PrimaryVariant = Color(0xFFD4BFA0)
    override val OnPrimary = Color(0xFF000000)
    override val Accent = Color(0xFFC4A882)
    override val AccentMuted = Color(0xFF2A2218)
    override val TextPrimary = Color(0xFFF0EBE3)
    override val TextSecondary = Color(0xFFB8A898)
    override val TextTertiary = Color(0xFF8B7765)
    override val UserBubble = Color(0xFF8B7355)
    override val UserBubbleText = Color(0xFFF0EBE3)
    override val AiBubble = Color(0xFF0D0D0D)
    override val AiBubbleBorder = Color(0xFF2A2A2A)
    override val AiBubbleText = Color(0xFFF0EBE3)
    override val InputBg = Color(0xFF111111)
    override val InputBorder = Color(0xFF2A2A2A)
    override val InputFocused = Color(0xFFC4A882)
    override val ChipBg = Color(0xFF111111)
    override val ChipBorder = Color(0xFF2A2A2A)
    override val ChipSelected = Color(0xFF1A1A1A)
    override val ErrorRed = Color(0xFFD9706A)
    override val SuccessGreen = Color(0xFF7DAE8C)
    override val Divider = Color(0xFF1A1A1A)
    override val Scrim = Color(0x88000000)
    override val NavBg = Color(0xFF0D0D0D)
    override val StatusBar = Color(0xFF000000)
    override val NavBar = Color(0xFF0D0D0D)
}

// ============================================================
//  莫兰迪浅蓝
// ============================================================

object BlueLightColors : FreeChatColors {
    override val Background = Color(0xFFEEF2F5)
    override val Surface = Color(0xFFE5EBF0)
    override val SurfaceVariant = Color(0xFFD8DFE6)
    override val SurfaceDim = Color(0xFFCFD7DF)
    override val Primary = Color(0xFF7B95A8)
    override val PrimaryVariant = Color(0xFF6B8396)
    override val OnPrimary = Color(0xFFFFFFFF)
    override val Accent = Color(0xFF7B95A8)
    override val AccentMuted = Color(0xFFE2E9F0)
    override val TextPrimary = Color(0xFF2A3340)
    override val TextSecondary = Color(0xFF6B7A8D)
    override val TextTertiary = Color(0xFF98A6B5)
    override val UserBubble = Color(0xFF96AFC0)
    override val UserBubbleText = Color(0xFFFFFFFF)
    override val AiBubble = Color(0xFFFFFFFF)
    override val AiBubbleBorder = Color(0xFFD8DFE6)
    override val AiBubbleText = Color(0xFF2A3340)
    override val InputBg = Color(0xFFFFFFFF)
    override val InputBorder = Color(0xFFD8DFE6)
    override val InputFocused = Color(0xFF7B95A8)
    override val ChipBg = Color(0xFFFFFFFF)
    override val ChipBorder = Color(0xFFD8DFE6)
    override val ChipSelected = Color(0xFFE2E9F0)
    override val ErrorRed = Color(0xFFC8554E)
    override val SuccessGreen = Color(0xFF6B9E7A)
    override val Divider = Color(0xFFD8DFE6)
    override val Scrim = Color(0x33000000)
    override val NavBg = Color(0xFFE5EBF0)
    override val StatusBar = Color(0xFFEEF2F5)
    override val NavBar = Color(0xFFE5EBF0)
}

object BlueDarkColors : FreeChatColors {
    override val Background = Color(0xFF171A1E)
    override val Surface = Color(0xFF1E2228)
    override val SurfaceVariant = Color(0xFF262B33)
    override val SurfaceDim = Color(0xFF171A1E)
    override val Primary = Color(0xFFA0B5C4)
    override val PrimaryVariant = Color(0xFFB8CAD6)
    override val OnPrimary = Color(0xFF171A1E)
    override val Accent = Color(0xFFA0B5C4)
    override val AccentMuted = Color(0xFF2D343E)
    override val TextPrimary = Color(0xFFEDF0F3)
    override val TextSecondary = Color(0xFFA8B5C2)
    override val TextTertiary = Color(0xFF7B8A9A)
    override val UserBubble = Color(0xFF7B95A8)
    override val UserBubbleText = Color(0xFFEDF0F3)
    override val AiBubble = Color(0xFF1E2228)
    override val AiBubbleBorder = Color(0xFF343A45)
    override val AiBubbleText = Color(0xFFEDF0F3)
    override val InputBg = Color(0xFF1A1D23)
    override val InputBorder = Color(0xFF343A45)
    override val InputFocused = Color(0xFFA0B5C4)
    override val ChipBg = Color(0xFF1A1D23)
    override val ChipBorder = Color(0xFF343A45)
    override val ChipSelected = Color(0xFF262B33)
    override val ErrorRed = Color(0xFFD9706A)
    override val SuccessGreen = Color(0xFF7DAE8C)
    override val Divider = Color(0xFF262B33)
    override val Scrim = Color(0x66000000)
    override val NavBg = Color(0xFF1E2228)
    override val StatusBar = Color(0xFF171A1E)
    override val NavBar = Color(0xFF1E2228)
}

object BlueOledDarkColors : FreeChatColors {
    override val Background = Color(0xFF000000)
    override val Surface = Color(0xFF0D0D0D)
    override val SurfaceVariant = Color(0xFF1A1A1A)
    override val SurfaceDim = Color(0xFF000000)
    override val Primary = Color(0xFFA0B5C4)
    override val PrimaryVariant = Color(0xFFB8CAD6)
    override val OnPrimary = Color(0xFF000000)
    override val Accent = Color(0xFFA0B5C4)
    override val AccentMuted = Color(0xFF1E2630)
    override val TextPrimary = Color(0xFFEDF0F3)
    override val TextSecondary = Color(0xFFA8B5C2)
    override val TextTertiary = Color(0xFF7B8A9A)
    override val UserBubble = Color(0xFF7B95A8)
    override val UserBubbleText = Color(0xFFEDF0F3)
    override val AiBubble = Color(0xFF0D0D0D)
    override val AiBubbleBorder = Color(0xFF2A2A2A)
    override val AiBubbleText = Color(0xFFEDF0F3)
    override val InputBg = Color(0xFF111111)
    override val InputBorder = Color(0xFF2A2A2A)
    override val InputFocused = Color(0xFFA0B5C4)
    override val ChipBg = Color(0xFF111111)
    override val ChipBorder = Color(0xFF2A2A2A)
    override val ChipSelected = Color(0xFF1A1A1A)
    override val ErrorRed = Color(0xFFD9706A)
    override val SuccessGreen = Color(0xFF7DAE8C)
    override val Divider = Color(0xFF1A1A1A)
    override val Scrim = Color(0x88000000)
    override val NavBg = Color(0xFF0D0D0D)
    override val StatusBar = Color(0xFF000000)
    override val NavBar = Color(0xFF0D0D0D)
}

// ============================================================
//  纯白
// ============================================================

object WhiteColors : FreeChatColors {
    override val Background = Color(0xFFFFFFFF)
    override val Surface = Color(0xFFF5F5F5)
    override val SurfaceVariant = Color(0xFFEBEBEB)
    override val SurfaceDim = Color(0xFFE0E0E0)
    override val Primary = Color(0xFF333333)
    override val PrimaryVariant = Color(0xFF555555)
    override val OnPrimary = Color(0xFFFFFFFF)
    override val Accent = Color(0xFF333333)
    override val AccentMuted = Color(0xFFF0F0F0)
    override val TextPrimary = Color(0xFF000000)
    override val TextSecondary = Color(0xFF555555)
    override val TextTertiary = Color(0xFF999999)
    override val UserBubble = Color(0xFFE8E8E8)
    override val UserBubbleText = Color(0xFF000000)
    override val AiBubble = Color(0xFFFFFFFF)
    override val AiBubbleBorder = Color(0xFFE0E0E0)
    override val AiBubbleText = Color(0xFF000000)
    override val InputBg = Color(0xFFFFFFFF)
    override val InputBorder = Color(0xFFE0E0E0)
    override val InputFocused = Color(0xFF333333)
    override val ChipBg = Color(0xFFFFFFFF)
    override val ChipBorder = Color(0xFFE0E0E0)
    override val ChipSelected = Color(0xFFF0F0F0)
    override val ErrorRed = Color(0xFFC8554E)
    override val SuccessGreen = Color(0xFF6B9E7A)
    override val Divider = Color(0xFFEBEBEB)
    override val Scrim = Color(0x33000000)
    override val NavBg = Color(0xFFF5F5F5)
    override val StatusBar = Color(0xFFFFFFFF)
    override val NavBar = Color(0xFFF5F5F5)
}

object WhiteDarkColors : FreeChatColors {
    override val Background = Color(0xFF1A1A1A)
    override val Surface = Color(0xFF242424)
    override val SurfaceVariant = Color(0xFF2E2E2E)
    override val SurfaceDim = Color(0xFF1A1A1A)
    override val Primary = Color(0xFFE0E0E0)
    override val PrimaryVariant = Color(0xFFF5F5F5)
    override val OnPrimary = Color(0xFF1A1A1A)
    override val Accent = Color(0xFFE0E0E0)
    override val AccentMuted = Color(0xFF2E2E2E)
    override val TextPrimary = Color(0xFFF5F5F5)
    override val TextSecondary = Color(0xFFB0B0B0)
    override val TextTertiary = Color(0xFF7A7A7A)
    override val UserBubble = Color(0xFF3A3A3A)
    override val UserBubbleText = Color(0xFFF5F5F5)
    override val AiBubble = Color(0xFF242424)
    override val AiBubbleBorder = Color(0xFF3A3A3A)
    override val AiBubbleText = Color(0xFFF5F5F5)
    override val InputBg = Color(0xFF1E1E1E)
    override val InputBorder = Color(0xFF3A3A3A)
    override val InputFocused = Color(0xFFE0E0E0)
    override val ChipBg = Color(0xFF1E1E1E)
    override val ChipBorder = Color(0xFF3A3A3A)
    override val ChipSelected = Color(0xFF2E2E2E)
    override val ErrorRed = Color(0xFFD9706A)
    override val SuccessGreen = Color(0xFF7DAE8C)
    override val Divider = Color(0xFF2E2E2E)
    override val Scrim = Color(0x66000000)
    override val NavBg = Color(0xFF242424)
    override val StatusBar = Color(0xFF1A1A1A)
    override val NavBar = Color(0xFF242424)
}

object WhiteOledDarkColors : FreeChatColors {
    override val Background = Color(0xFF000000)
    override val Surface = Color(0xFF0D0D0D)
    override val SurfaceVariant = Color(0xFF1A1A1A)
    override val SurfaceDim = Color(0xFF000000)
    override val Primary = Color(0xFFE0E0E0)
    override val PrimaryVariant = Color(0xFFF5F5F5)
    override val OnPrimary = Color(0xFF000000)
    override val Accent = Color(0xFFE0E0E0)
    override val AccentMuted = Color(0xFF1A1A1A)
    override val TextPrimary = Color(0xFFF5F5F5)
    override val TextSecondary = Color(0xFFB0B0B0)
    override val TextTertiary = Color(0xFF7A7A7A)
    override val UserBubble = Color(0xFF2A2A2A)
    override val UserBubbleText = Color(0xFFF5F5F5)
    override val AiBubble = Color(0xFF0D0D0D)
    override val AiBubbleBorder = Color(0xFF2A2A2A)
    override val AiBubbleText = Color(0xFFF5F5F5)
    override val InputBg = Color(0xFF111111)
    override val InputBorder = Color(0xFF2A2A2A)
    override val InputFocused = Color(0xFFE0E0E0)
    override val ChipBg = Color(0xFF111111)
    override val ChipBorder = Color(0xFF2A2A2A)
    override val ChipSelected = Color(0xFF1A1A1A)
    override val ErrorRed = Color(0xFFD9706A)
    override val SuccessGreen = Color(0xFF7DAE8C)
    override val Divider = Color(0xFF1A1A1A)
    override val Scrim = Color(0x88000000)
    override val NavBg = Color(0xFF0D0D0D)
    override val StatusBar = Color(0xFF000000)
    override val NavBar = Color(0xFF0D0D0D)
}
