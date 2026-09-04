package com.freechat.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.AppLanguage
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.model.ColorTheme
import com.freechat.model.FontSize
import com.freechat.model.LengthMode
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.TempMode
import com.freechat.model.ThemeMode
import com.freechat.model.ChatMode
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.theme.LocalMonoFontFamily
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.frostedCard
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeProgressive
import com.freechat.viewmodel.ChatViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

private enum class SubScreen { LANGUAGE_MODEL, VISUAL_MODELS, VOICE_MODELS, AI_MEMORY, FONT }

/** 设置页磨砂玻璃的 HazeState，通过 CompositionLocal 提供给所有 SettingsRow（含二级页） */
internal val LocalSettingsHazeState = staticCompositionLocalOf<HazeState?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel = viewModel(),
    isDark: Boolean,
    onBack: () -> Unit,
    onOpenVoiceDebug: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenModelEditor: (ModelType, ModelInfo?) -> Unit = { _, _ -> },
    onOpenAgreement: () -> Unit = {}
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedVisualModel by viewModel.selectedVisualModel.collectAsState()
    val selectedVisionModel by viewModel.selectedVisionModel.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()
    val tempMode by viewModel.tempMode.collectAsState()
    val lengthMode by viewModel.lengthMode.collectAsState()
    val enableWebSearch by viewModel.enableWebSearch.collectAsState()
    val showThinking by viewModel.showThinking.collectAsState()
    val autoSummarizeMemory by viewModel.autoSummarizeMemory.collectAsState()
    val useSystemFont by viewModel.useSystemFont.collectAsState()
    val globalMemories by viewModel.globalMemories.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val advancedMaterial by viewModel.advancedMaterial.collectAsState()
    val chatMode by viewModel.chatMode.collectAsState()
    val hazeState = rememberHazeState()
    val systemDarkTheme by viewModel.systemDarkTheme.collectAsState()
    val asrModel by viewModel.asrModel.collectAsState()
    val asrModels by viewModel.asrModels.collectAsState()

    var showThemePicker by remember { mutableStateOf(false) }
    var showColorThemePicker by remember { mutableStateOf(false) }
    var showTempPicker by remember { mutableStateOf(false) }
    var showLengthPicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showChatModePicker by remember { mutableStateOf(false) }
    var showFontSizePicker by remember { mutableStateOf(false) }
    var showGlobalMemoryEditor by remember { mutableStateOf(false) }
    var previewFontSize by remember { mutableStateOf(FontSize.MEDIUM) }
    var showWebSearchConfirm by remember { mutableStateOf(false) }

    var subScreen by remember { mutableStateOf<SubScreen?>(null) }
    // 主列表滚动状态提升到子页切换之外，返回时保持原滚动位置（跨页返回也保持）
    val mainScrollState = rememberScrollState(initial = viewModel.settingsScrollPosition.value)
    LaunchedEffect(mainScrollState) {
        snapshotFlow { mainScrollState.value }
            .collect { viewModel.saveSettingsScrollPosition(it) }
    }

    // 子页返回键：优先返回设置主列表
    BackHandler(enabled = subScreen != null) { subScreen = null }

    if (showWebSearchConfirm) {
        AlertDialog(
            onDismissRequest = { showWebSearchConfirm = false },
            containerColor = colors.Surface,
            title = { Text(s.closeWebSearchTitle, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text(s.closeWebSearchDesc, color = colors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setEnableWebSearch(false)
                    showWebSearchConfirm = false
                }) { Text(s.stillClose, color = colors.ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showWebSearchConfirm = false }) {
                    Text(s.keepOn, color = colors.Primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 全局记忆编辑弹窗（AI 记忆子页内触发）
    if (showGlobalMemoryEditor) {
        var newMemoryText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGlobalMemoryEditor = false },
            containerColor = colors.Surface,
            title = { Text(s.globalMemory, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (globalMemories.isEmpty()) {
                        Text(s.globalMemoryEmpty, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                    } else {
                        globalMemories.forEach { mem ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(colors.AccentMuted)
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(mem, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.deleteGlobalMemory(mem) }) {
                                    Icon(Icons.Filled.DeleteOutline, null, tint = colors.ErrorRed, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = newMemoryText,
                            onValueChange = { newMemoryText = it },
                            placeholder = { Text(s.globalMemoryHint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextTertiary) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                            modifier = Modifier.weight(1f),
                            maxLines = 3
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                viewModel.addGlobalMemory(newMemoryText)
                                newMemoryText = ""
                            },
                            enabled = newMemoryText.isNotBlank()
                        ) { Text(s.globalMemoryAdd, color = colors.Primary, fontWeight = FontWeight.Bold) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGlobalMemoryEditor = false }) { Text(s.close, color = colors.Primary) }
            }
        )
    }

    // 主题（含跟随系统时的暗色主题子选择）
    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            containerColor = colors.Surface,
            title = { Text(s.themeMode, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val sel = mode == themeMode
                        val icon = when (mode) {
                            ThemeMode.SYSTEM -> Icons.Filled.Update
                            ThemeMode.LIGHT -> Icons.Filled.LightMode
                            ThemeMode.DARK -> Icons.Filled.DarkMode
                            ThemeMode.DARK_OLED -> Icons.Filled.DarkMode
                        }
                        val desc = themeDesc(mode, s)
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (sel) colors.AccentMuted else colors.Surface)
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(icon, null, tint = if (sel) colors.Primary else colors.TextSecondary, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(themeLabel(mode, s), color = if (sel) colors.Primary else colors.TextPrimary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                            Text(desc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                                        }
                                    }
                                    if (sel) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                                }
                                // 跟随系统时：选择暗色主题用「深色」还是「黑色」
                                if (mode == ThemeMode.SYSTEM && sel) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 30.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(s.systemDarkTheme, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                                        Spacer(Modifier.weight(1f))
                                        DarkThemeChip(s.themeDark, !systemDarkTheme, colors) { viewModel.setSystemDarkTheme(false) }
                                        Spacer(Modifier.width(6.dp))
                                        DarkThemeChip(s.themeDarkOled, systemDarkTheme, colors) { viewModel.setSystemDarkTheme(true) }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showThemePicker = false }) { Text(s.close, color = colors.TextSecondary) } }
        )
    }

    // 色彩
    if (showColorThemePicker) {
        AlertDialog(
            onDismissRequest = { showColorThemePicker = false },
            containerColor = colors.Surface,
            title = { Text(s.colorTheme, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorTheme.entries.forEach { ct ->
                        val sel = ct == colorTheme
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (sel) colors.AccentMuted else colors.Surface)
                                .clickable { viewModel.setColorTheme(ct); showColorThemePicker = false }
                                .padding(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        colorThemeLabel(ct, s),
                                        color = if (sel) colors.Primary else colors.TextPrimary,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (sel) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showColorThemePicker = false }) { Text(s.close, color = colors.TextSecondary) } }
        )
    }

    // 回复温度
    if (showTempPicker) {
        val tempIcons = mapOf(
            TempMode.AUTO to Icons.Filled.Update,
            TempMode.WARM to Icons.Filled.Favorite,
            TempMode.OBJECTIVE to Icons.Filled.Psychology
        )
        val tempDescs = mapOf(
            TempMode.AUTO to s.tempAutoDesc,
            TempMode.WARM to s.tempWarmDesc,
            TempMode.OBJECTIVE to s.tempObjectiveDesc
        )
        AlertDialog(
            onDismissRequest = { showTempPicker = false },
            containerColor = colors.Surface,
            title = { Text(s.replyTemp, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TempMode.entries.forEach { mode ->
                        val sel = mode == tempMode
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (sel) colors.AccentMuted else colors.Surface)
                                .clickable { viewModel.setTempMode(mode); showTempPicker = false }
                                .padding(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(tempIcons[mode] ?: Icons.Filled.Update, null, tint = if (sel) colors.Primary else colors.TextSecondary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(tempLabel(mode, s), color = if (sel) colors.Primary else colors.TextPrimary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                        Text(tempDescs[mode] ?: "", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                                    }
                                }
                                if (sel) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTempPicker = false }) { Text(s.close, color = colors.TextSecondary) } }
        )
    }

    // 回复长度
    if (showLengthPicker) {
        val lengthIcons = mapOf(
            LengthMode.AUTO to Icons.Filled.Update,
            LengthMode.FULL to Icons.Filled.MenuOpen,
            LengthMode.CONCISE to Icons.Filled.ShortText
        )
        val lengthDescs = mapOf(
            LengthMode.AUTO to s.lengthAutoDesc,
            LengthMode.FULL to s.lengthFullDesc,
            LengthMode.CONCISE to s.lengthConciseDesc
        )
        AlertDialog(
            onDismissRequest = { showLengthPicker = false },
            containerColor = colors.Surface,
            title = { Text(s.replyLength, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LengthMode.entries.forEach { mode ->
                        val sel = mode == lengthMode
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (sel) colors.AccentMuted else colors.Surface)
                                .clickable { viewModel.setLengthMode(mode); showLengthPicker = false }
                                .padding(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(lengthIcons[mode] ?: Icons.Filled.Update, null, tint = if (sel) colors.Primary else colors.TextSecondary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(lengthLabel(mode, s), color = if (sel) colors.Primary else colors.TextPrimary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                        Text(lengthDescs[mode] ?: "", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                                    }
                                }
                                if (sel) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLengthPicker = false }) { Text(s.close, color = colors.TextSecondary) } }
        )
    }

    // 语言选择
    if (showLanguagePicker) {
        val langIcons = mapOf(
            AppLanguage.SYSTEM to Icons.Filled.Update,
            AppLanguage.ZH_CN to Icons.Filled.Language,
            AppLanguage.ZH_TW to Icons.Filled.Language,
            AppLanguage.EN to Icons.Filled.Language
        )
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            containerColor = colors.Surface,
            title = { Text(s.selectLanguage, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        val sel = lang == appLanguage
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (sel) colors.AccentMuted else colors.Surface)
                                .clickable { viewModel.setLanguage(lang); showLanguagePicker = false }
                                .padding(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(langIcons[lang] ?: Icons.Filled.Update, null, tint = if (sel) colors.Primary else colors.TextSecondary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(langLabel(lang, s), color = if (sel) colors.Primary else colors.TextPrimary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                                if (sel) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLanguagePicker = false }) { Text(s.close, color = colors.TextSecondary) } }
        )
    }

    // 字体大小（字体子页内触发）
    if (showFontSizePicker) {
        AlertDialog(
            onDismissRequest = { showFontSizePicker = false },
            containerColor = colors.Surface,
            title = { Text(s.fontSize, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "永远相信美好的事情即将发生",
                        fontSize = (14 * previewFontSize.scale).sp,
                        color = colors.TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("A", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextTertiary)
                        Slider(
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            value = previewFontSize.ordinal.toFloat(),
                            onValueChange = { v ->
                                val i = v.roundToInt().coerceIn(0, FontSize.entries.lastIndex)
                                previewFontSize = FontSize.entries[i]
                            },
                            valueRange = 0f..FontSize.entries.lastIndex.toFloat(),
                            steps = FontSize.entries.size - 2,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.Primary,
                                activeTrackColor = colors.Primary,
                                inactiveTrackColor = colors.SurfaceVariant
                            )
                        )
                        Text("A", style = MaterialTheme.typography.titleLarge, color = colors.TextTertiary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        fontSizePercent(previewFontSize),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                        color = colors.TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setFontSize(previewFontSize)
                    showFontSizePicker = false
                }) { Text(s.confirm, color = colors.Primary) }
            },
            dismissButton = {}
        )
    }

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val density = LocalDensity.current
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp).toPx() }

    CompositionLocalProvider(LocalSettingsHazeState provides hazeState) {
    Box(Modifier.fillMaxSize().background(colors.Background)) {
        AnimatedContent(
            targetState = subScreen,
            transitionSpec = {
                (fadeIn(FreeChatAnimation.pageFadeInFast) +
                    scaleIn(initialScale = 0.97f, animationSpec = tween(280, easing = FreeChatAnimation.easeOut))) togetherWith
                    (fadeOut(FreeChatAnimation.pageFadeOutFast) +
                        scaleOut(targetScale = 0.97f, animationSpec = tween(200, easing = FreeChatAnimation.easeOut)))
            },
            label = "settings_subpage"
        ) { screen ->
        if (screen == null) {
            // ============ 主设置列表 ============
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).background(colors.Background) else Modifier)
                    .verticalScroll(mainScrollState)
                    .padding(top = statusBarHeightDp + titleBarAreaDp + 8.dp, bottom = 32.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.height(0.dp))

                // ──── 模型选择 ────
                SectionLabel(Icons.Filled.SmartToy, s.sectionModels)

                // 语言模型
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { subScreen = SubScreen.LANGUAGE_MODEL }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.SmartToy, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.languageModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(
                                    selectedModel.displayName,
                                    fontFamily = LocalMonoFontFamily.current,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                                    color = colors.TextSecondary
                                )
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 视觉模型（生图 + 识图 合并为一栏）
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { subScreen = SubScreen.VISUAL_MODELS }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Image, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.visualModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(selectedVisualModel.displayName, fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                                    Text(selectedVisionModel?.displayName ?: "未添加", fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                                }
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 语音模型（只读二级页入口）
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { subScreen = SubScreen.VOICE_MODELS }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.RecordVoiceOver, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.voiceModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text("MiMo-V2.5-TTS", fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                                    Text(asrModels.find { it.id == asrModel }?.displayName ?: "未添加", fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                                }
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ──── AI 系统优化 ────
                SectionLabel(Icons.Filled.AutoAwesome, s.sectionAiOptimize)

                // 联网搜索
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Language, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.webSearch, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(
                                    if (enableWebSearch && !selectedModel.supportsWebSearch) s.webSearchUnsupported
                                    else if (enableWebSearch) s.webSearchOn
                                    else s.webSearchOff,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                                    color = if (!selectedModel.supportsWebSearch && enableWebSearch) colors.ErrorRed else colors.TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = enableWebSearch && selectedModel.supportsWebSearch,
                            onCheckedChange = { checked ->
                                if (!checked) showWebSearchConfirm = true
                                else viewModel.setEnableWebSearch(true)
                            },
                            enabled = selectedModel.supportsWebSearch,
                            colors = switchColors(colors)
                        )
                    }
                }

                // 显示思考过程（当前模型不支持时整项消失）
                if (selectedModel.supportsThinking) {
                    SettingsRow {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Psychology, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(s.showThinking, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                    Text(s.showThinkingDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                                }
                            }
                            Switch(
                                checked = showThinking,
                                onCheckedChange = { viewModel.setShowThinking(it) },
                                colors = switchColors(colors)
                            )
                        }
                    }
                }

                // 回复温度
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { showTempPicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Thermostat, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.replyTemp, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(tempLabel(tempMode, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 回复长度
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { showLengthPicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ShortText, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.replyLength, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(lengthLabel(lengthMode, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 语音调试
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenVoiceDebug() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.RecordVoiceOver, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.aiVoice, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // AI 记忆（二级页入口）
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { subScreen = SubScreen.AI_MEMORY }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Bookmark, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.aiMemory, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ──── 系统主题 ────
                SectionLabel(Icons.Filled.DarkMode, s.sectionSystemTheme)

                // 色彩
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { showColorThemePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Palette, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.colorTheme, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(colorThemeLabel(colorTheme, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 字体（二级页入口）
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { subScreen = SubScreen.FONT }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FormatSize, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.font, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(fontSizeLabel(fontSize, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 高级材质
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.BlurOn, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.advancedMaterial, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(s.advancedMaterialDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Switch(
                            checked = advancedMaterial,
                            onCheckedChange = { viewModel.setAdvancedMaterial(it) },
                            colors = switchColors(colors)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ──── 通用 ────
                SectionLabel(Icons.Filled.Tune, s.sectionGeneral)

                // 主题（主题模式）
                SettingsRow {
                    val icon = when (themeMode) {
                        ThemeMode.SYSTEM -> Icons.Filled.Update
                        ThemeMode.LIGHT -> Icons.Filled.LightMode
                        ThemeMode.DARK -> Icons.Filled.DarkMode
                        ThemeMode.DARK_OLED -> Icons.Filled.DarkMode
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { showThemePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.theme, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(themeLabel(themeMode, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 语言
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { showLanguagePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Language, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.language, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(langLabel(appLanguage, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ──── 关于 ────
                SectionLabel(Icons.Filled.Info, s.sectionAbout)
                SettingsRow {
                    Column {
                        // 版本（右侧箭头进入更新日志二级页）
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenChangelog() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.version, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Version 1.0.01", style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Belate-stay/FreeChat")))
                                }
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.author, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Belate", style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenAgreement() }.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.userAgreement, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        } else {
            // ============ 二级页面 ============
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).background(colors.Background) else Modifier)
                    .verticalScroll(rememberScrollState())
                    .padding(top = statusBarHeightDp + titleBarAreaDp + 8.dp, bottom = 32.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (screen) {
                    SubScreen.LANGUAGE_MODEL -> LanguageModelPage(viewModel, colors, s, onAdd = { onOpenModelEditor(ModelType.LANGUAGE, null) }, onEdit = { onOpenModelEditor(ModelType.LANGUAGE, it) })
                    SubScreen.VISUAL_MODELS -> VisualModelsPage(viewModel, colors, s, onAddVisual = { onOpenModelEditor(ModelType.VISUAL, null) }, onAddVision = { onOpenModelEditor(ModelType.VISION, null) }, onEdit = { onOpenModelEditor(it.modelType, it) })
                    SubScreen.VOICE_MODELS -> VoiceModelsPage(viewModel, colors, s, onAddTts = { onOpenModelEditor(ModelType.TTS, null) }, onAddAsr = { onOpenModelEditor(ModelType.ASR, null) }, onEdit = { onOpenModelEditor(it.modelType, it) })
                    SubScreen.AI_MEMORY -> AiMemoryPage(
                        viewModel = viewModel,
                        colors = colors,
                        s = s,
                        autoSummarizeMemory = autoSummarizeMemory,
                        globalMemories = globalMemories,
                        onEditGlobalMemory = { showGlobalMemoryEditor = true }
                    )
                    SubScreen.FONT -> FontPage(
                        viewModel = viewModel,
                        colors = colors,
                        s = s,
                        useSystemFont = useSystemFont,
                        fontSize = fontSize,
                        onOpenFontSize = {
                            previewFontSize = fontSize
                            showFontSizePicker = true
                        }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        }

        // ===== 顶部标题栏背景（高级材质开=真模糊+渐变渐隐，关=纯色） =====
        if (advancedMaterial) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp)
                    .hazeEffect(state = hazeState) {
                        blurRadius = HazeSpec.TopBlurRadius
                        inputScale = HazeInputScale.None
                        backgroundColor = Color.Transparent
                        progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 1f, endY = topBarHeightPx, endIntensity = 0f)
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp)
                    .background(colors.Background)
            )
        }

        // ===== 悬浮标题栏（返回键 + 标题），固定悬浮顶部 =====
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (subScreen != null) subScreen = null else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.TextPrimary)
            }
            Text(
                when (subScreen) {
                    SubScreen.LANGUAGE_MODEL -> s.languageModel
                    SubScreen.VISUAL_MODELS -> s.visualModel
                    SubScreen.VOICE_MODELS -> s.voiceModel
                    SubScreen.AI_MEMORY -> s.aiMemory
                    SubScreen.FONT -> s.font
                    null -> s.settings
                },
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
    }
}

// ============ 二级页面 ============

@Composable
private fun LanguageModelPage(
    viewModel: ChatViewModel, colors: com.freechat.ui.theme.FreeChatColors, s: AppStrings,
    onAdd: () -> Unit, onEdit: (ModelInfo) -> Unit
) {
    val languageModels by viewModel.languageModels.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    Spacer(modifier = Modifier.height(4.dp))
    AddModelButton(onAdd, colors, s)
    ModelListDivider(colors)
    languageModels.forEach { model ->
        ModelItemRow(model, model.id == selectedModel.id, { viewModel.selectLanguageModel(model) }, onEdit, colors)
    }
}

@Composable
private fun VisualModelsPage(
    viewModel: ChatViewModel, colors: com.freechat.ui.theme.FreeChatColors, s: AppStrings,
    onAddVisual: () -> Unit, onAddVision: () -> Unit, onEdit: (ModelInfo) -> Unit
) {
    val visualModels by viewModel.visualModels.collectAsState()
    val visionModels by viewModel.visionModels.collectAsState()
    val selectedVisualModel by viewModel.selectedVisualModel.collectAsState()
    val selectedVisionModel by viewModel.selectedVisionModel.collectAsState()

    Spacer(modifier = Modifier.height(4.dp))
    SectionLabel(Icons.Filled.Image, s.imageGenModel)
    AddModelButton(onAddVisual, colors, s)
    ModelListDivider(colors)
    visualModels.forEach { model ->
        ModelItemRow(model, model.id == selectedVisualModel.id, { viewModel.selectVisualModel(model) }, onEdit, colors)
    }

    Spacer(modifier = Modifier.height(16.dp))
    SectionLabel(Icons.Filled.Visibility, s.visionModel)
    AddModelButton(onAddVision, colors, s)
    ModelListDivider(colors)
    if (visionModels.isEmpty()) {
        EmptyModelHint(s.addModelFirst, colors)
    } else {
        visionModels.forEach { model ->
            ModelItemRow(model, model.id == selectedVisionModel?.id, { viewModel.selectVisionModel(model) }, onEdit, colors)
        }
    }
}

@Composable
private fun VoiceModelsPage(
    viewModel: ChatViewModel, colors: com.freechat.ui.theme.FreeChatColors, s: AppStrings,
    onAddTts: () -> Unit, onAddAsr: () -> Unit, onEdit: (ModelInfo) -> Unit
) {
    val ttsModels by viewModel.ttsModels.collectAsState()
    val asrModels by viewModel.asrModels.collectAsState()
    val voiceModel by viewModel.voiceModel.collectAsState()
    val asrModel by viewModel.asrModel.collectAsState()

    Spacer(modifier = Modifier.height(4.dp))
    SectionLabel(Icons.Filled.RecordVoiceOver, s.voiceModel)
    AddModelButton(onAddTts, colors, s)
    ModelListDivider(colors)
    ttsModels.forEach { model ->
        ModelItemRow(model, model.id == voiceModel, { viewModel.selectTtsModel(model) }, onEdit, colors)
    }

    Spacer(modifier = Modifier.height(16.dp))
    SectionLabel(Icons.Filled.Mic, s.voiceModel)
    AddModelButton(onAddAsr, colors, s)
    ModelListDivider(colors)
    if (asrModels.isEmpty()) {
        EmptyModelHint(s.addModelFirst, colors)
    } else {
        asrModels.forEach { model ->
            ModelItemRow(model, model.id == asrModel, { viewModel.selectAsrModel(model) }, onEdit, colors)
        }
    }
}

// —— 模型列表通用组件 ——
@Composable
private fun AddModelButton(onClick: () -> Unit, colors: com.freechat.ui.theme.FreeChatColors, s: AppStrings) {
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = LocalSettingsHazeState.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.addModel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = colors.Primary)
        }
    }
}

@Composable
private fun ModelListDivider(colors: com.freechat.ui.theme.FreeChatColors) {
    Column {
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun EmptyModelHint(text: String, colors: com.freechat.ui.theme.FreeChatColors) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextTertiary)
    }
}

@Composable
private fun ModelItemRow(
    model: ModelInfo,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: (ModelInfo) -> Unit,
    colors: com.freechat.ui.theme.FreeChatColors
) {
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = LocalSettingsHazeState.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp), selected = selected)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(model.displayName, fontFamily = LocalMonoFontFamily.current, style = MaterialTheme.typography.bodyLarge, color = if (selected) colors.Primary else colors.TextPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                Text(model.description, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
            }
            if (!model.isBuiltIn) {
                IconButton(onClick = { onEdit(model) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Edit, null, tint = colors.TextTertiary, modifier = Modifier.size(16.dp))
                }
            }
            if (selected) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AiMemoryPage(
    viewModel: ChatViewModel,
    colors: com.freechat.ui.theme.FreeChatColors,
    s: AppStrings,
    autoSummarizeMemory: Boolean,
    globalMemories: List<String>,
    onEditGlobalMemory: () -> Unit
) {
    Spacer(modifier = Modifier.height(4.dp))

    // Memory 总结
    SettingsRow {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(s.memorySummary, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                    Text(s.memorySummaryDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                }
            }
            Switch(
                checked = autoSummarizeMemory,
                onCheckedChange = { viewModel.setAutoSummarizeMemory(it) },
                colors = switchColors(colors)
            )
        }
    }

    Spacer(modifier = Modifier.height(2.dp))

    // 全局记忆
    SettingsRow {
        Row(
            Modifier.fillMaxWidth().clickable { onEditGlobalMemory() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Bookmark, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(s.globalMemory, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                    Text(
                        if (globalMemories.isEmpty()) s.globalMemoryDesc
                        else "${globalMemories.size} · ${s.globalMemoryDesc}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                        color = colors.TextSecondary
                    )
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FontPage(
    viewModel: ChatViewModel,
    colors: com.freechat.ui.theme.FreeChatColors,
    s: AppStrings,
    useSystemFont: Boolean,
    fontSize: FontSize,
    onOpenFontSize: () -> Unit
) {
    Spacer(modifier = Modifier.height(4.dp))

    // 字体优化（开关反转：打开 = 使用内置 HYSongYunLangHei 字体）
    SettingsRow {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.TextFields, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(s.fontOptimize, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                    Text(s.fontOptimizeDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                }
            }
            Switch(
                checked = !useSystemFont,
                onCheckedChange = { viewModel.setUseSystemFont(!it) },
                colors = switchColors(colors)
            )
        }
    }

    Spacer(modifier = Modifier.height(2.dp))

    // 字体大小
    SettingsRow {
        Row(
            Modifier.fillMaxWidth().clickable { onOpenFontSize() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FormatSize, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(s.fontSize, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                    Text(fontSizeLabel(fontSize, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

// ============ 通用小组件 ============

@Composable
private fun ModelSelectCard(
    model: ModelInfo,
    selected: Boolean,
    colors: com.freechat.ui.theme.FreeChatColors,
    onClick: () -> Unit
) {
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = LocalSettingsHazeState.current
    Box(
        Modifier.fillMaxWidth()
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp), selected = selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    model.displayName,
                    fontFamily = LocalMonoFontFamily.current,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) colors.Primary else colors.TextPrimary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(model.description, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
            }
            if (selected) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FixedVoiceModelCard(name: String, desc: String, colors: com.freechat.ui.theme.FreeChatColors) {
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = LocalSettingsHazeState.current
    Box(
        Modifier.fillMaxWidth()
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, fontFamily = LocalMonoFontFamily.current, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
            }
            Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DarkThemeChip(label: String, selected: Boolean, colors: com.freechat.ui.theme.FreeChatColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.AccentMuted else colors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
            color = if (selected) colors.Primary else colors.TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun switchColors(colors: com.freechat.ui.theme.FreeChatColors) = SwitchDefaults.colors(
    checkedThumbColor = colors.OnPrimary,
    checkedTrackColor = colors.Primary,
    uncheckedThumbColor = colors.TextTertiary,
    uncheckedTrackColor = colors.SurfaceVariant
)

// ============ 辅助函数 ============

private fun tempLabel(mode: TempMode, s: AppStrings): String = when (mode) {
    TempMode.AUTO -> s.tempAuto
    TempMode.WARM -> s.tempWarm
    TempMode.OBJECTIVE -> s.tempObjective
}

private fun lengthLabel(mode: LengthMode, s: AppStrings): String = when (mode) {
    LengthMode.AUTO -> s.lengthAuto
    LengthMode.FULL -> s.lengthFull
    LengthMode.CONCISE -> s.lengthConcise
}

private fun themeLabel(mode: ThemeMode, s: AppStrings): String = when (mode) {
    ThemeMode.SYSTEM -> s.themeSystem
    ThemeMode.LIGHT -> s.themeLight
    ThemeMode.DARK -> s.themeDark
    ThemeMode.DARK_OLED -> s.themeDarkOled
}

private fun colorThemeLabel(ct: ColorTheme, s: AppStrings): String = when (ct) {
    ColorTheme.BROWN -> s.colorThemeBrown
    ColorTheme.BLUE -> s.colorThemeBlue
    ColorTheme.WHITE -> s.colorThemeWhite
}

private fun themeDesc(mode: ThemeMode, s: AppStrings): String = when (mode) {
    ThemeMode.SYSTEM -> s.themeSystemDesc
    ThemeMode.LIGHT -> s.themeLightDesc
    ThemeMode.DARK -> s.themeDarkDesc
    ThemeMode.DARK_OLED -> s.themeDarkOledDesc
}

private fun langLabel(lang: AppLanguage, s: AppStrings): String = when (lang) {
    AppLanguage.SYSTEM -> s.langSystem
    AppLanguage.ZH_CN -> s.langZhCN
    AppLanguage.ZH_TW -> s.langZhTW
    AppLanguage.EN -> s.langEn
}

private fun fontSizeLabel(fs: FontSize, s: AppStrings): String = when (fs) {
    FontSize.SMALL -> s.fontSizeSmall
    FontSize.MEDIUM -> s.fontSizeMedium
    FontSize.LARGE -> s.fontSizeLarge
    FontSize.XLARGE -> s.fontSizeXlarge
    FontSize.XXLARGE -> s.fontSizeXxlarge
}

private fun fontSizePercent(fs: FontSize): String = "${(fs.scale * 100).roundToInt()}%"

@Composable
internal fun SectionLabel(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SettingsRow(content: @Composable () -> Unit) {
    val advancedMaterial = LocalAdvancedMaterial.current
    val colors = LocalFreeChatColors.current
    val hazeState = LocalSettingsHazeState.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp))
    ) {
        content()
    }
}
