package com.freechat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ShortText
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.model.LengthMode
import com.freechat.model.ModelInfo
import com.freechat.model.PerConvSettings
import com.freechat.model.TempMode
import com.freechat.ui.theme.LocalMonoFontFamily
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.viewmodel.ChatViewModel
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 「新规则」二级页：针对当前对话的模型选择 + AI 系统优化定制。
 * 每对话覆盖优先级高于全局默认；未设置的项跟随全局。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRulesScreen(
    viewModel: ChatViewModel,
    convId: String,
    isDark: Boolean,
    onBack: () -> Unit
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val density = LocalDensity.current

    // 全局默认
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedVisualModel by viewModel.selectedVisualModel.collectAsState()
    val selectedVisionModel by viewModel.selectedVisionModel.collectAsState()
    val enableWebSearch by viewModel.enableWebSearch.collectAsState()
    val showThinking by viewModel.showThinking.collectAsState()
    val autoSummarizeMemory by viewModel.autoSummarizeMemory.collectAsState()
    val tempMode by viewModel.tempMode.collectAsState()
    val lengthMode by viewModel.lengthMode.collectAsState()

    // 每对话覆盖
    val perConvMap by viewModel.perConvSettings.collectAsState()
    val per = perConvMap[convId] ?: PerConvSettings()

    // 有效值 = 每对话覆盖（非 null）优先，否则全局默认
    val effModel = per.languageModelId?.let { id -> viewModel.languageModels.value.find { it.id == id } } ?: selectedModel
    val effVisual = per.visualModelId?.let { id -> viewModel.visualModels.value.find { it.id == id } } ?: selectedVisualModel
    val effVision = per.visionModelId?.let { id -> viewModel.visionModels.value.find { it.id == id } } ?: selectedVisionModel
    val effSearch = per.enableWebSearch ?: enableWebSearch
    val effThinking = per.showThinking ?: showThinking
    val effAutoMem = per.autoSummarizeMemory ?: autoSummarizeMemory
    val effTemp = TempMode.entries.getOrElse(per.tempModeOrdinal ?: tempMode.ordinal) { TempMode.AUTO }
    val effLength = LengthMode.entries.getOrElse(per.lengthModeOrdinal ?: lengthMode.ordinal) { LengthMode.AUTO }

    fun set(transform: (PerConvSettings) -> PerConvSettings) {
        viewModel.updatePerConvSettings(convId, transform(per))
    }

    var showLangPicker by remember { mutableStateOf(false) }
    var showVisualPicker by remember { mutableStateOf(false) }
    var showVisionPicker by remember { mutableStateOf(false) }
    var showTempPicker by remember { mutableStateOf(false) }
    var showLengthPicker by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { onBack() }

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp).toPx() }

    // 语言模型选择
    if (showLangPicker) {
        ModelPickerDialog(s.languageModel, viewModel.languageModels.value, effModel, colors, s, { m ->
            set { it.copy(languageModelId = m.id) }
            showLangPicker = false
        }, { showLangPicker = false })
    }
    if (showVisualPicker) {
        ModelPickerDialog(s.imageGenModel, viewModel.visualModels.value, effVisual, colors, s, { m ->
            set { it.copy(visualModelId = m.id) }
            showVisualPicker = false
        }, { showVisualPicker = false })
    }
    if (showVisionPicker) {
        ModelPickerDialog(s.visionModel, viewModel.visionModels.value, effVision, colors, s, { m ->
            set { it.copy(visionModelId = m.id) }
            showVisionPicker = false
        }, { showVisionPicker = false })
    }
    if (showTempPicker) {
        TempModePicker(effTemp, colors, s, { m ->
            set { it.copy(tempModeOrdinal = m.ordinal) }
            showTempPicker = false
        }, { showTempPicker = false })
    }
    if (showLengthPicker) {
        LengthModePicker(effLength, colors, s, { m ->
            set { it.copy(lengthModeOrdinal = m.ordinal) }
            showLengthPicker = false
        }, { showLengthPicker = false })
    }

    Box(Modifier.fillMaxSize().background(colors.Background)) {
        CompositionLocalProvider(LocalSettingsHazeState provides hazeState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).background(colors.Background) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(top = statusBarHeightDp + titleBarAreaDp + 8.dp, bottom = 32.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ──── 模型选择 ────
            SectionLabel(Icons.Filled.SmartToy, s.sectionModels)

            SettingsRow {
                Row(
                    Modifier.fillMaxWidth().clickable { showLangPicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.SmartToy, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(s.languageModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text(effModel.displayName, fontFamily = LocalMonoFontFamily.current, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

            SettingsRow {
                Row(
                    Modifier.fillMaxWidth().clickable { showVisualPicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Image, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(s.imageGenModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text(effVisual.displayName, fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

            SettingsRow {
                Row(
                    Modifier.fillMaxWidth().clickable { showVisionPicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Visibility, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(s.visionModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text(effVision?.displayName ?: "未添加", fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

            SettingsRow {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.RecordVoiceOver, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(s.voiceModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text("MiMo-V2.5-TTS · MiMo-V2.5-ASR", fontFamily = LocalMonoFontFamily.current, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                        }
                    }
                    Icon(Icons.Filled.Check, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            // ──── AI 系统优化 ────
            SectionLabel(Icons.Filled.AutoAwesome, s.sectionAiOptimize)

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
                            Text(if (effSearch) s.webSearchOn else s.webSearchOff, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                        }
                    }
                    Switch(
                        checked = effSearch,
                        onCheckedChange = { v -> set { it.copy(enableWebSearch = v) } },
                        colors = switchColors(colors)
                    )
                }
            }

            if (effModel.supportsThinking) {
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
                            checked = effThinking,
                            onCheckedChange = { v -> set { it.copy(showThinking = v) } },
                            colors = switchColors(colors)
                        )
                    }
                }
            }

            SettingsRow {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Bookmark, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(s.aiMemory, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text(s.memorySummaryDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                        }
                    }
                    Switch(
                        checked = effAutoMem,
                        onCheckedChange = { v -> set { it.copy(autoSummarizeMemory = v) } },
                        colors = switchColors(colors)
                    )
                }
            }

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
                            Text(tempLabel(effTemp, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

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
                            Text(lengthLabel(effLength, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
        }

        // 顶部标题栏背景（高级材质开=真模糊，关=纯色）
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

        // 悬浮标题栏
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.TextPrimary)
            }
            Text(
                s.newRulesTitle,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun ModelPickerDialog(
    title: String,
    models: List<ModelInfo>,
    selected: ModelInfo?,
    colors: com.freechat.ui.theme.FreeChatColors,
    s: AppStrings,
    onSelect: (ModelInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Surface,
        title = { Text(title, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                models.forEach { m ->
                    val sel = m.id == selected?.id
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (sel) colors.AccentMuted else colors.Surface)
                            .clickable { onSelect(m) }
                            .padding(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.displayName, fontFamily = LocalMonoFontFamily.current, color = if (sel) colors.Primary else colors.TextPrimary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                Text(m.description, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                            if (sel) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.close, color = colors.TextSecondary) } }
    )
}

@Composable
private fun TempModePicker(selected: TempMode, colors: com.freechat.ui.theme.FreeChatColors, s: AppStrings, onSelect: (TempMode) -> Unit, onDismiss: () -> Unit) {
    val icons = mapOf(TempMode.AUTO to Icons.Filled.Update, TempMode.WARM to Icons.Filled.Favorite, TempMode.OBJECTIVE to Icons.Filled.Psychology)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Surface,
        title = { Text(s.replyTemp, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TempMode.entries.forEach { m ->
                    val sel = m == selected
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (sel) colors.AccentMuted else colors.Surface)
                            .clickable { onSelect(m) }
                            .padding(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icons[m] ?: Icons.Filled.Update, null, tint = if (sel) colors.Primary else colors.TextSecondary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(tempLabel(m, s), color = if (sel) colors.Primary else colors.TextPrimary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            }
                            if (sel) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.close, color = colors.TextSecondary) } }
    )
}

@Composable
private fun LengthModePicker(selected: LengthMode, colors: com.freechat.ui.theme.FreeChatColors, s: AppStrings, onSelect: (LengthMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Surface,
        title = { Text(s.replyLength, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LengthMode.entries.forEach { m ->
                    val sel = m == selected
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (sel) colors.AccentMuted else colors.Surface)
                            .clickable { onSelect(m) }
                            .padding(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(lengthLabel(m, s), color = if (sel) colors.Primary else colors.TextPrimary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            if (sel) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.close, color = colors.TextSecondary) } }
    )
}

@Composable
private fun switchColors(colors: com.freechat.ui.theme.FreeChatColors) = SwitchDefaults.colors(
    checkedThumbColor = colors.OnPrimary,
    checkedTrackColor = colors.Primary,
    uncheckedThumbColor = colors.TextTertiary,
    uncheckedTrackColor = colors.SurfaceVariant
)

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
