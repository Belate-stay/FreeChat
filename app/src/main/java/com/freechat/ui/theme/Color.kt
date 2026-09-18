package com.freechat.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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
    override val InputBg = Color(0xFFFBF5EE)
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
    override val InputBg = Color(0xFFF5F9FC)
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

// ============================================================
//  选项表 / 菜单里「选中项」的统一配色（1.0.50）
// ============================================================

/**
 * 选中项的底色：**就是「确定」按钮那口颜色**（`SheetPanel` 的 confirm 按钮用的
 * `colors.Primary` + 白字）。所以纯白主题下选中项是一块近黑的实心板子，
 * 和确定按钮一模一样 —— 用户的要求原话。
 *
 * 在这之前，各处选中态是各写各的：`SheetOption` 和 `frostedCard` 都用 AccentMuted
 * （一层几乎看不出的淡底）+ 一个 ✓ 图标；模型卡又是另一套。1.0.50 起统一成这一处定义、
 * 各选项表引用它 —— 顺带把所有 ✓ 都去掉（底色已经说清楚了，再挂个勾是两套语言）。
 */
val FreeChatColors.selectedFill: Color get() = Primary

/** 选中项上的字：用主题自带的 OnPrimary，深色主题下自动翻成深字，不再写死白 */
val FreeChatColors.selectedText: Color get() = OnPrimary

/** 选中项上的副标题：同一支颜色压到 72%，够读、又不跟主标题抢 */
val FreeChatColors.selectedSubText: Color get() = OnPrimary.copy(alpha = 0.72f)

// ============================================================
//  行内「链接」式的操作文字（1.0.53）
// ============================================================

/**
 * 二级风险弹层里那两行「仍然开启 / 仍然关闭」的颜色 —— **蓝色**（用户点名的：带下划线的
 * 蓝字是「这里可以点」的通用符号，比正文色或红字都显眼得多，一眼就认得出是可点的选项）。
 *
 * 刻意**不跟主题**：Primary 在暖棕主题里是棕色，和弹层里那个主按钮撞成一片，反而更看不出
 * 哪个是行内链接。深浅两套值只是为了让各自底色上都读得清，判据沿用全 App 那一条
 * `TextPrimary.luminance() > 0.5f`（见 DrawerContent / Frosted 里的用法）。
 */
val LinkBlueLight = Color(0xFF2E6BD6)
val LinkBlueDark = Color(0xFF7FB0FF)

/** 当前主题下该用的链接蓝（深色主题用亮一档的，浅色主题用深一档的） */
val FreeChatColors.linkInk: Color
    get() = if (TextPrimary.luminance() > 0.5f) LinkBlueDark else LinkBlueLight
