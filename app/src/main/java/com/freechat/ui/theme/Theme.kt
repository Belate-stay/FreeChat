package com.freechat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import com.freechat.model.ColorTheme
import com.freechat.model.FontSize
import com.freechat.model.ThemeMode
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.animation.MotionIndication
import com.freechat.ui.animation.rememberMotionEnabled

private fun buildLightScheme(colors: FreeChatColors) = lightColorScheme(
    primary = colors.Primary,
    onPrimary = colors.OnPrimary,
    primaryContainer = colors.AccentMuted,
    onPrimaryContainer = colors.TextPrimary,
    secondary = colors.PrimaryVariant,
    onSecondary = readableForeground(colors.PrimaryVariant),
    secondaryContainer = colors.ChipSelected,
    onSecondaryContainer = colors.TextPrimary,
    background = colors.Background,
    onBackground = colors.TextPrimary,
    surface = colors.Surface,
    onSurface = colors.TextPrimary,
    surfaceVariant = colors.SurfaceVariant,
    onSurfaceVariant = colors.TextSecondary,
    outline = colors.InputBorder,
    outlineVariant = colors.Divider,
    error = colors.ErrorRed,
    onError = readableForeground(colors.ErrorRed),
    scrim = colors.Scrim
)

private fun buildDarkScheme(colors: FreeChatColors) = darkColorScheme(
    primary = colors.Primary,
    onPrimary = colors.OnPrimary,
    primaryContainer = colors.AccentMuted,
    onPrimaryContainer = colors.TextPrimary,
    secondary = colors.PrimaryVariant,
    onSecondary = readableForeground(colors.PrimaryVariant),
    secondaryContainer = colors.ChipSelected,
    onSecondaryContainer = colors.TextPrimary,
    background = colors.Background,
    onBackground = colors.TextPrimary,
    surface = colors.Surface,
    onSurface = colors.TextPrimary,
    surfaceVariant = colors.SurfaceVariant,
    onSurfaceVariant = colors.TextSecondary,
    outline = colors.InputBorder,
    outlineVariant = colors.Divider,
    error = colors.ErrorRed,
    onError = readableForeground(colors.ErrorRed),
    scrim = colors.Scrim
)

/**
 * 根据 [themeMode]（亮度）和 [colorTheme]（色板）解析出最终配色
 */
@Composable
fun resolveColors(
    themeMode: ThemeMode,
    colorTheme: ColorTheme,
    systemDarkTheme: Boolean = false,
    customColorArgb: Int = 0xFF346C98.toInt(),
): FreeChatColors {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.DARK_OLED -> true
    }
    // isOled：DARK_OLED 恒 OLED；SYSTEM 时仅当「系统当前是深色」且用户选了「黑色」才 OLED——
    // 否则系统浅色时会误切纯黑（这是「跟随系统 + 黑色」的 bug 根因）
    val isOled = themeMode == ThemeMode.DARK_OLED || (themeMode == ThemeMode.SYSTEM && systemDark && systemDarkTheme)

    return remember(colorTheme, isDark, isOled, customColorArgb) { when (colorTheme) {
        ColorTheme.BROWN -> when {
            isOled -> OledDarkColors
            isDark -> DarkColors
            else -> LightColors
        }
        ColorTheme.BLUE -> when {
            isOled -> BlueOledDarkColors
            isDark -> BlueDarkColors
            else -> BlueLightColors
        }
        ColorTheme.WHITE -> when {
            isOled -> WhiteOledDarkColors
            isDark -> WhiteDarkColors
            else -> WhiteColors
        }
        ColorTheme.CUSTOM -> customColors(customColorArgb, isDark, isOled)
        ColorTheme.PINE -> customColors(0xFF26735A.toInt(), isDark, isOled)
        ColorTheme.CORAL -> customColors(0xFFA3444B.toInt(), isDark, isOled)
    } }
}

@Composable
fun FreeChatTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorTheme: ColorTheme = ColorTheme.BROWN,
    fontSize: FontSize = FontSize.MEDIUM,
    useSystemFont: Boolean = false,
    systemDarkTheme: Boolean = false,
    customColorArgb: Int = 0xFF346C98.toInt(),
    content: @Composable () -> Unit
) {
    val colors = resolveColors(themeMode, colorTheme, systemDarkTheme, customColorArgb)

    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.DARK_OLED -> true
    }

    val scheme = remember(colors, isDark) {
        if (isDark) buildDarkScheme(colors) else buildLightScheme(colors)
    }
    val motionEnabled = rememberMotionEnabled()
    val indication = remember(motionEnabled, colors.Primary) { MotionIndication(motionEnabled, colors.Primary) }

    // 字体缩放：仅改 fontScale，sp 字号全局缩放；density 不变，dp 布局（间距/尺寸）保持原样，
    // 文字变大时自动换行重排，不会挤坏排版。scale 为 null → 跟随系统，不覆盖。
    val density = LocalDensity.current
    val fontScale = fontSize.scale

    // 字体优化开关（useSystemFont=false 表示开启内置字体包）：关闭则全部回退系统默认字体
    val globalFont = if (useSystemFont) FontFamily.Default else NotoSansCJK
    val monoFont = if (useSystemFont) FontFamily.Default else Consolas

    CompositionLocalProvider(
        LocalFreeChatColors provides colors,
        LocalColorTheme provides colorTheme,
        LocalFontScale provides fontSize.scale,
        LocalChatFontFamily provides (if (useSystemFont) FontFamily.Default else HYSongYunLangHei),
        LocalLatinFontFamily provides (if (useSystemFont) FontFamily.Default else HFSandBeach),
        LocalGlobalFontFamily provides globalFont,
        LocalMonoFontFamily provides monoFont,
        LocalDensity provides Density(density.density, fontScale = fontScale)
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = buildTypography(globalFont)
        ) {
            // MaterialTheme provides its own default indication, so install ours INSIDE it.
            CompositionLocalProvider(LocalMotionEnabled provides motionEnabled, LocalIndication provides indication) { content() }
        }
    }
}

/** CompositionLocal for FreeChatColors, accessible by all child composables */
val LocalFreeChatColors = staticCompositionLocalOf<FreeChatColors> { LightColors }

/** Keep the selected preset identity; grayscale CUSTOM is not the BLACK/WHITE preset. */
val LocalColorTheme = staticCompositionLocalOf { ColorTheme.BROWN }

/** 当前字体缩放倍率（0.85~1.4），供 dp 布局随字号联动（气泡宽度/间距等自适应） */
val LocalFontScale = staticCompositionLocalOf { 1f }

/** 高级材质开关：开启时全屏应用半透明液态玻璃质感（液态玻璃模拟） */
val LocalAdvancedMaterial = staticCompositionLocalOf { false }
