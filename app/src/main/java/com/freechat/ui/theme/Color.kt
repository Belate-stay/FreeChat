package com.freechat.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Immutable
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
    /**
     * 输入框表面淡色（1.0.71）：与背景拉开一丝色差（纯白主题=米白）又不抢戏。
     * 磨砂分支以低透明度叠进 haze tints，保住哑光玻璃的透光；非磨砂直接做底色。
     */
    val inputSurface: Color
    /**
     * 输入框圆钮填充：与主题主色一致，配套图标色统一使用 OnPrimary。
     */
    val inputBtnFill: Color
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
    override val Background = Color(0xFFF2F4F7)
    override val Surface = Color(0xFFF3F5F7)
    override val SurfaceVariant = Color(0xFFE7EBEF)
    override val SurfaceDim = Color(0xFFD5DBE1)
    override val Primary = Color(0xFF80623F)
    override val PrimaryVariant = Color(0xFF695032)
    override val OnPrimary = Color(0xFFFFFFFF)
    override val Accent = Primary
    override val AccentMuted = Color(0xFFF0E8DA)
    override val TextPrimary = Color(0xFF24282D)
    override val TextSecondary = Color(0xFF50545B)
    override val TextTertiary = Color(0xFF555B64)
    override val UserBubble = Primary
    override val UserBubbleText = Color(0xFFFFFFFF)
    override val AiBubble = Color(0xFFFFFFFF)
    override val AiBubbleBorder = Color(0xFFD5DBE1)
    override val AiBubbleText = TextPrimary
    override val InputBg = Color(0xFFFFFFFF)
    override val inputSurface = Color(0xFFF6F8FA)
    override val inputBtnFill = Primary
    override val InputBorder = Color(0xFFC8D0D8)
    override val InputFocused = Primary
    override val ChipBg = Color(0xFFFFFFFF)
    override val ChipBorder = Color(0xFFD5DBE1)
    override val ChipSelected = AccentMuted
    override val ErrorRed = Color(0xFFC8554E)
    override val SuccessGreen = Color(0xFF6B9E7A)
    override val Divider = Color(0xFFE4E8EC)
    override val Scrim = Color(0x33000000)
    override val NavBg = Surface
    override val StatusBar = Background
    override val NavBar = Surface
}

object DarkColors : FreeChatColors {
    override val Background = Color(0xFF1A1A1A)
    override val Surface = Color(0xFF242424)
    override val SurfaceVariant = Color(0xFF2E2E2E)
    override val SurfaceDim = Background
    override val Primary = Color(0xFFD4B58C)
    override val PrimaryVariant = Color(0xFFE2C8A4)
    override val OnPrimary = Color(0xFF19191A)
    override val Accent = Primary
    override val AccentMuted = Color(0xFF383126)
    override val TextPrimary = Color(0xFFF5F5F5)
    override val TextSecondary = Color(0xFFBDBDBD)
    override val TextTertiary = Color(0xFFA1A1A1)
    override val UserBubble = Primary
    override val UserBubbleText = OnPrimary
    override val AiBubble = Surface
    override val AiBubbleBorder = Color(0xFF404040)
    override val AiBubbleText = TextPrimary
    override val InputBg = Color(0xFF1E1E1E)
    override val inputSurface = Color(0xFF262626)
    override val inputBtnFill = Primary
    override val InputBorder = Color(0xFF494949)
    override val InputFocused = Primary
    override val ChipBg = Color(0xFF1E1E1E)
    override val ChipBorder = Color(0xFF404040)
    override val ChipSelected = AccentMuted
    override val ErrorRed = Color(0xFFD9706A)
    override val SuccessGreen = Color(0xFF7DAE8C)
    override val Divider = Color(0xFF363636)
    override val Scrim = Color(0x66000000)
    override val NavBg = Surface
    override val StatusBar = Background
    override val NavBar = Surface
}

object OledDarkColors : FreeChatColors {
    override val Background = Color(0xFF000000)
    override val Surface = Color(0xFF0D0D0D)
    override val SurfaceVariant = Color(0xFF1A1A1A)
    override val SurfaceDim = Color(0xFF000000)
    override val Primary = Color(0xFFD4B58C)
    override val PrimaryVariant = Color(0xFFE2C8A4)
    override val OnPrimary = Color(0xFF000000)
    override val Accent = Primary
    override val AccentMuted = Color(0xFF2A2218)
    override val TextPrimary = Color(0xFFF5F5F5)
    override val TextSecondary = Color(0xFFBDBDBD)
    override val TextTertiary = Color(0xFFA1A1A1)
    override val UserBubble = Primary
    override val UserBubbleText = OnPrimary
    override val AiBubble = Color(0xFF0D0D0D)
    override val AiBubbleBorder = Color(0xFF2A2A2A)
    override val AiBubbleText = TextPrimary
    override val InputBg = Color(0xFF111111)
    override val inputSurface = Color(0xFF1A1A1A)
    override val inputBtnFill = Primary
    override val InputBorder = Color(0xFF2A2A2A)
    override val InputFocused = Primary
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
    override val Background = Color(0xFFF2F4F7)
    override val Surface = Color(0xFFF3F5F7)
    override val SurfaceVariant = Color(0xFFE7EBEF)
    override val SurfaceDim = Color(0xFFD5DBE1)
    override val Primary = Color(0xFF346C98)
    override val PrimaryVariant = Color(0xFF28577D)
    override val OnPrimary = Color(0xFFFFFFFF)
    override val Accent = Color(0xFF346C98)
    override val AccentMuted = Color(0xFFE1F0FA)
    override val TextPrimary = Color(0xFF24282D)
    override val TextSecondary = Color(0xFF50545B)
    override val TextTertiary = Color(0xFF555B64)
    override val UserBubble = Color(0xFF346C98)
    override val UserBubbleText = Color(0xFFFFFFFF)
    override val AiBubble = Color(0xFFFFFFFF)
    override val AiBubbleBorder = Color(0xFFD5DBE1)
    override val AiBubbleText = TextPrimary
    override val InputBg = Color(0xFFFFFFFF)
    override val inputSurface = Color(0xFFF6F8FA)
    override val inputBtnFill = Primary
    override val InputBorder = Color(0xFFC8D0D8)
    override val InputFocused = Primary
    override val ChipBg = Color(0xFFFFFFFF)
    override val ChipBorder = Color(0xFFD5DBE1)
    override val ChipSelected = Color(0xFFE1F0FA)
    override val ErrorRed = Color(0xFFC8554E)
    override val SuccessGreen = Color(0xFF6B9E7A)
    override val Divider = Color(0xFFE4E8EC)
    override val Scrim = Color(0x33000000)
    override val NavBg = Surface
    override val StatusBar = Background
    override val NavBar = Surface
}

object BlueDarkColors : FreeChatColors {
    override val Background = Color(0xFF1A1A1A)
    override val Surface = Color(0xFF242424)
    override val SurfaceVariant = Color(0xFF2E2E2E)
    override val SurfaceDim = Background
    override val Primary = Color(0xFF82BADE)
    override val PrimaryVariant = Color(0xFFA2D0EB)
    override val OnPrimary = Color(0xFF12191F)
    override val Accent = Primary
    override val AccentMuted = Color(0xFF243B4D)
    override val TextPrimary = Color(0xFFF5F5F5)
    override val TextSecondary = Color(0xFFBDBDBD)
    override val TextTertiary = Color(0xFFA1A1A1)
    override val UserBubble = Primary
    override val UserBubbleText = OnPrimary
    override val AiBubble = Surface
    override val AiBubbleBorder = Color(0xFF404040)
    override val AiBubbleText = TextPrimary
    override val InputBg = Color(0xFF1E1E1E)
    override val inputSurface = Color(0xFF262626)
    override val inputBtnFill = Primary
    override val InputBorder = Color(0xFF494949)
    override val InputFocused = Primary
    override val ChipBg = Color(0xFF1E1E1E)
    override val ChipBorder = Color(0xFF404040)
    override val ChipSelected = Color(0xFF243B4D)
    override val ErrorRed = Color(0xFFD9706A)
    override val SuccessGreen = Color(0xFF7DAE8C)
    override val Divider = Color(0xFF363636)
    override val Scrim = Color(0x66000000)
    override val NavBg = Surface
    override val StatusBar = Background
    override val NavBar = Surface
}

object BlueOledDarkColors : FreeChatColors {
    override val Background = Color(0xFF000000)
    override val Surface = Color(0xFF0D0D0D)
    override val SurfaceVariant = Color(0xFF1A1A1A)
    override val SurfaceDim = Color(0xFF000000)
    override val Primary = Color(0xFF82BADE)
    override val PrimaryVariant = Color(0xFFA2D0EB)
    override val OnPrimary = Color(0xFF000000)
    override val Accent = Primary
    override val AccentMuted = Color(0xFF1E2630)
    override val TextPrimary = Color(0xFFF5F5F5)
    override val TextSecondary = Color(0xFFBDBDBD)
    override val TextTertiary = Color(0xFFA1A1A1)
    override val UserBubble = Primary
    override val UserBubbleText = OnPrimary
    override val AiBubble = Color(0xFF0D0D0D)
    override val AiBubbleBorder = Color(0xFF2A2A2A)
    override val AiBubbleText = TextPrimary
    override val InputBg = Color(0xFF111111)
    override val inputSurface = Color(0xFF1A1A1A)
    override val inputBtnFill = Primary
    override val InputBorder = Color(0xFF2A2A2A)
    override val InputFocused = Primary
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
    override val Background = Color(0xFFF5F6F8)
    override val Surface = Color(0xFFF3F5F7)
    override val SurfaceVariant = Color(0xFFE7EBEF)
    override val SurfaceDim = Color(0xFFD5DBE1)
    override val Primary = Color(0xFF333333)
    override val PrimaryVariant = Color(0xFF555555)
    override val OnPrimary = Color(0xFFFFFFFF)
    override val Accent = Color(0xFF333333)
    override val AccentMuted = Color(0xFFF0F0F0)
    override val TextPrimary = Color(0xFF000000)
    override val TextSecondary = Color(0xFF555555)
    override val TextTertiary = Color(0xFF595959)
    override val UserBubble = Color(0xFFE7EBEF)
    override val UserBubbleText = Color(0xFF000000)
    override val AiBubble = Color(0xFFFFFFFF)
    override val AiBubbleBorder = Color(0xFFD5DBE1)
    override val AiBubbleText = Color(0xFF000000)
    override val InputBg = Color(0xFFFFFFFF)
    override val inputSurface = Color(0xFFF6F8FA)
    override val inputBtnFill: Color get() = Primary
    override val InputBorder = Color(0xFFC8D0D8)
    override val InputFocused = Color(0xFF333333)
    override val ChipBg = Color(0xFFFFFFFF)
    override val ChipBorder = Color(0xFFD5DBE1)
    override val ChipSelected = Color(0xFFF0F0F0)
    override val ErrorRed = Color(0xFFC8554E)
    override val SuccessGreen = Color(0xFF6B9E7A)
    override val Divider = Color(0xFFE4E8EC)
    override val Scrim = Color(0x33000000)
    override val NavBg = Color(0xFFF3F5F7)
    override val StatusBar = Color(0xFFFFFFFF)
    override val NavBar = Color(0xFFF3F5F7)
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
    override val TextTertiary = Color(0xFFA1A1A1)
    override val UserBubble = Color(0xFF3A3A3A)
    override val UserBubbleText = Color(0xFFF5F5F5)
    override val AiBubble = Color(0xFF242424)
    override val AiBubbleBorder = Color(0xFF3A3A3A)
    override val AiBubbleText = Color(0xFFF5F5F5)
    override val InputBg = Color(0xFF1E1E1E)
    override val inputSurface = Color(0xFF262626)
    override val inputBtnFill: Color get() = Primary
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
    override val TextTertiary = Color(0xFFA1A1A1)
    override val UserBubble = Color(0xFF2A2A2A)
    override val UserBubbleText = Color(0xFFF5F5F5)
    override val AiBubble = Color(0xFF0D0D0D)
    override val AiBubbleBorder = Color(0xFF2A2A2A)
    override val AiBubbleText = Color(0xFFF5F5F5)
    override val InputBg = Color(0xFF111111)
    override val inputSurface = Color(0xFF161616)
    override val inputBtnFill: Color get() = Primary
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

/** 自定义色只染交互重点；页面底色、正文、卡片仍使用可读性稳定的中性阶。 */
private class CustomAccentColors(
    base: FreeChatColors,
    override val Primary: Color,
    override val PrimaryVariant: Color,
    override val OnPrimary: Color,
    override val AccentMuted: Color,
) : FreeChatColors by base {
    override val Accent: Color = Primary
    override val UserBubble: Color = Primary
    override val UserBubbleText: Color = OnPrimary
    override val InputFocused: Color = Primary
    override val inputBtnFill: Color = Primary
    override val ChipSelected: Color = AccentMuted
}

/**
 * ARGB 中的透明度表示主题色的施加力度，不直接把半透明色交给按钮/气泡。
 * 后者必须是不透明的，才能在磨砂和流光背景上始终保持文字对比度。
 */
fun customColors(argb: Int, isDark: Boolean, isOled: Boolean): FreeChatColors {
    val base = when {
        isOled -> WhiteOledDarkColors
        isDark -> WhiteDarkColors
        else -> WhiteColors
    }
    val chosen = Color(argb)
    val neutralAccent = if (isDark) Color(0xFF828D98) else Color(0xFF505A64)
    var primary = chosen.compositeOver(neutralAccent)
    // 极亮或极暗的自选色仍需与邻近卡面区分，否则选中态会消失。
    val contrastTarget = if (isDark) Color.White else Color.Black
    repeat(24) {
        if (contrastRatio(primary, base.SurfaceVariant) < 4.5f) {
            primary = lerp(primary, contrastTarget, 0.12f)
        }
    }
    val onPrimary = readableForeground(primary)
    val variant = lerp(primary, contrastTarget, 0.14f)
    val muted = primary.copy(alpha = if (isDark) 0.16f else 0.08f).compositeOver(base.Surface)
    return CustomAccentColors(base, primary, variant, onPrimary, muted)
}

internal fun contrastRatio(a: Color, b: Color): Float {
    val light = maxOf(a.luminance(), b.luminance())
    val dark = minOf(a.luminance(), b.luminance())
    return (light + 0.05f) / (dark + 0.05f)
}

internal fun readableForeground(fill: Color): Color =
    if (contrastRatio(Color.White, fill) >= contrastRatio(Color.Black, fill)) Color.White else Color.Black

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

/** 副标题通过字号/字重分层，保留不透明前景色，避免自定义色下对比度下降。 */
val FreeChatColors.selectedSubText: Color get() = OnPrimary

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

/** 圆钮与选中项使用同一套主色/前景色，自定义色也保持可读。 */
val FreeChatColors.inputBtnIcon: Color
    get() = OnPrimary
