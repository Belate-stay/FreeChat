package com.freechat.ui.screens

import com.freechat.ui.components.HeaderIconButton
import com.freechat.ui.components.TopBarBackdrop
import com.freechat.ui.components.TopBarBackdropSource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.freechat.ui.components.NeumorphicSwitch as Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.ui.components.SheetOption
import com.freechat.ui.components.SheetPanel
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.viewmodel.ChatViewModel
import com.freechat.data.BuiltInVoiceModel
import com.freechat.data.SettingsRepository
import com.freechat.data.TtsController
import com.freechat.data.VoicePreset
import com.freechat.data.VoicePresetRepository
import com.freechat.ui.animation.MotionPolicy
import com.freechat.ui.animation.PageMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** 语音调试子页面 — 与主设置页保持一致的排版布局与配色 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDebugScreen(
    viewModel: ChatViewModel = viewModel(),
    isDark: Boolean,
    onBack: () -> Unit
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val ttsAutoPlay by viewModel.ttsAutoPlay.collectAsState()
    val ttsVoice by viewModel.ttsVoice.collectAsState()
    val ttsSpeed by viewModel.ttsSpeed.collectAsState()
    val ttsPitch by viewModel.ttsPitch.collectAsState()
    val voiceModel by viewModel.voiceModel.collectAsState()

    val context = LocalContext.current
    val settings = remember(context) { SettingsRepository(context) }
    val voiceRepository = remember(context) { VoicePresetRepository.get(context) }
    val customVoices by voiceRepository.presets.collectAsState()
    val selectedPresetId by settings.ttsPresetId.collectAsState(initial = "")
    val customSelectionActive = voiceModel.equals(BuiltInVoiceModel.CLONE_ID, ignoreCase = true) ||
        voiceModel.equals(BuiltInVoiceModel.DESIGN_ID, ignoreCase = true)
    val selectedPreset = customVoices.find { it.id == selectedPresetId }.takeIf { customSelectionActive }
    val storageError by voiceRepository.storageError.collectAsState()
    val ttsError by TtsController.lastError.collectAsState()
    val scope = rememberCoroutineScope()
    var showAdvancedVoice by remember { mutableStateOf(false) }
    var deletingPreset by remember { mutableStateOf<VoicePreset?>(null) }
    var localVoiceNotice by remember { mutableStateOf<String?>(null) }
    var localVoiceError by remember { mutableStateOf<String?>(null) }

    fun choosePreset(preset: VoicePreset, after: () -> Unit = {}) {
        localVoiceError = null
        TtsController.clearError()
        scope.launch {
            try {
                    settings.saveTtsPresetId(preset.id)
                    viewModel.setVoiceModel(BuiltInVoiceModel.CLONE_ID)
                    localVoiceNotice = "已使用音色「${preset.name}」。"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { localVoiceError = "音色选择保存失败，请在列表中重试。" }
            finally { after() }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (TtsController.playingMessageId.value?.startsWith("mimo-voice-preset-") == true) TtsController.stop()
        }
    }

    val pageTravel = with(LocalDensity.current) { MotionPolicy.PageTravelDp.dp.roundToPx() }
    PageMotion(targetState = showAdvancedVoice, distancePx = pageTravel.toFloat(),
        forward = { _, target -> target }, label = "advanced_voice_page") { advancedPage ->
        if (advancedPage) {
            MiMoVoiceSettingsPage(onBack = { showAdvancedVoice = false }, onSaved = { preset ->
                choosePreset(preset) { showAdvancedVoice = false }
            })
        } else {

    var showVoicePicker by remember { mutableStateOf(false) }

    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBandHeight = HazeSpec.topBandHeightDp(statusBarHeight)

    // 与主设置页共用标题栏位置、渐进模糊、按钮材质和内容留白。
    CompositionLocalProvider(LocalSettingsHazeState provides hazeState) {
    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        TopBarBackdropSource(hazeState, colors.Background, topBandHeight)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(top = HazeSpec.topContentPaddingDp(statusBarHeight), bottom = 32.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 自动朗读
            SectionLabel(Icons.Filled.RecordVoiceOver, s.voiceAutoPlay)
            SettingsRow {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.VolumeUp, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(s.voiceAutoPlay, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                            Text(s.voiceAutoPlayDesc, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
                        }
                    }
                    Switch(
                        checked = ttsAutoPlay,
                        onCheckedChange = { viewModel.setTtsAutoPlay(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.OnPrimary,
                            checkedTrackColor = colors.Primary,
                            uncheckedThumbColor = colors.TextTertiary,
                            uncheckedTrackColor = colors.SurfaceVariant
                        )
                    )
                }
            }

            // 段间距：与 12dp 的卡片间距叠出 32dp（同设置主列表）
            Spacer(modifier = Modifier.height(8.dp))

            // 音色
            SectionLabel(Icons.Filled.Face, s.voiceTone)
            SettingsRow {
                Row(
                    Modifier.fillMaxWidth().clickable { showVoicePicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Face, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(s.voiceTone, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                            Text(selectedPreset?.name ?: voiceLabel(ttsVoice, s), style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

            SettingsRow {
                Row(Modifier.fillMaxWidth().clickable {
                    if (TtsController.playingMessageId.value?.startsWith("mimo-voice-preset-") == true) TtsController.stop()
                    showAdvancedVoice = true
                }
                    .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("高级自定义", color = colors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text("Beta", Modifier.clip(RoundedCornerShape(4.dp)).background(colors.SurfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp), color = colors.Primary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Text("根据提示词或参考音频创建自己的音色", color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

            if (customVoices.isNotEmpty()) {
                SectionLabel(Icons.Filled.RecordVoiceOver, "本机自定义音色")
                SettingsRow {
                    Column {
                        customVoices.forEach { preset ->
                            Row(Modifier.fillMaxWidth().clickable {
                                choosePreset(preset)
                            }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(preset.name, color = colors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                    Text(if (customSelectionActive && preset.id == selectedPresetId) "当前使用" else "点击使用",
                                        color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        try {
                                            TtsController.speakSingle("mimo-voice-preset-${preset.id}",
                                                voiceRepository.readSample(preset.preview).bytes(), ttsSpeed, ttsPitch)
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (error: Exception) { localVoiceError = error.message ?: "本机试听音频无法读取。" }
                                    }
                                }) { Icon(Icons.Filled.PlayArrow, "试听 ${preset.name}", tint = colors.Primary) }
                                IconButton(onClick = { deletingPreset = preset }) {
                                    Icon(Icons.Filled.DeleteOutline, "删除 ${preset.name}", tint = colors.TextTertiary)
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { TtsController.stop() }, modifier = Modifier.align(Alignment.End)) { Text("停止试听") }
                Text("音色和参考音频仅保存在本机。使用时会将参考音频发送给 MiMo。", color = colors.TextTertiary,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
            }

            (storageError ?: localVoiceError ?: ttsError?.message ?: localVoiceNotice)?.let { text ->
                Text(text, color = if (storageError != null || localVoiceError != null || ttsError != null) MaterialTheme.colorScheme.error else colors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 语速
            SectionLabel(Icons.Filled.Speed, s.voiceSpeed)
            SettingsRow {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Speed, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(s.voiceSpeed, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                        }
                        Text(formatSpeed(ttsSpeed), style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = ttsSpeed,
                        onValueChange = { viewModel.setTtsSpeed(it) },
                        valueRange = 0.5f..3.0f,
                        steps = 24,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.Primary,
                            activeTrackColor = colors.Primary,
                            inactiveTrackColor = colors.SurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 音调
            SectionLabel(Icons.Filled.GraphicEq, s.voicePitch)
            SettingsRow {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.GraphicEq, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(s.voicePitch, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                        }
                        Text(formatPitch(ttsPitch), style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = ttsPitch,
                        onValueChange = { viewModel.setTtsPitch(it) },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.Primary,
                            activeTrackColor = colors.Primary,
                            inactiveTrackColor = colors.SurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

    TopBarBackdrop(hazeState, colors.Background, topBandHeight)
    Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(top = 8.8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        HeaderIconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back, tint = colors.TextPrimary)
        }
        Text(s.voiceDebug, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleLarge)
    }

    // 音色选择 —— 窗口内的底部磨砂哑光玻璃弹层。
    // 原来是 AlertDialog：独立窗口看不到本页自己画的内容，「模糊背景」根本做不到，
    // 只能把背景压暗（就是「悬浮卡片背景加暗」那种效果）。现在换成真模糊、不压暗。
    SheetPanel(
        visible = showVoicePicker,
        onDismiss = { showVoicePicker = false },
        title = s.voiceTone,
        colors = colors,
        isDark = isDark,
        advancedMaterial = advancedMaterial,
        hazeState = hazeState,
        maxContentHeight = 360.dp
    ) {
        viewModel.ttsVoices.forEach { voice ->
            SheetOption(
                selected = voice == ttsVoice && !customSelectionActive,
                title = voiceLabel(voice, s),
                colors = colors,
                onClick = {
                    localVoiceError = null
                    scope.launch {
                        try {
                            settings.saveTtsPresetId("")
                            viewModel.setTtsVoice(voice)
                            viewModel.setVoiceModel(BuiltInVoiceModel.STOCK_ID)
                            TtsController.clearError()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { localVoiceError = "音色选择保存失败，请重试。" }
                    }
                    showVoicePicker = false
                }
            )
        }
        customVoices.forEach { preset ->
            SheetOption(selected = customSelectionActive && preset.id == selectedPresetId, title = preset.name, colors = colors,
                onClick = {
                    choosePreset(preset)
                    showVoicePicker = false
                })
        }
    }

    deletingPreset?.let { preset ->
        AlertDialog(onDismissRequest = { deletingPreset = null }, title = { Text("删除音色「${preset.name}」？") },
            text = { Text("这个音色的本机配置和未被其他音色使用的参考音频会一起删除。") },
            confirmButton = {
                TextButton(onClick = {
                    deletingPreset = null
                    scope.launch {
                        try {
                            TtsController.stop()
                            voiceRepository.delete(preset.id)
                            if (selectedPresetId == preset.id) {
                                settings.saveTtsPresetId("")
                                if (customSelectionActive) viewModel.setVoiceModel(BuiltInVoiceModel.STOCK_ID)
                            }
                            localVoiceNotice = "已删除本机音色「${preset.name}」。"
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { localVoiceError = error.message ?: "删除音色失败，请稍后重试。" }
                    }
                }) { Text("删除") }
            }, dismissButton = { TextButton(onClick = { deletingPreset = null }) { Text("取消") } })
    }
    }
    }
        }
    }
}

private fun voiceLabel(id: String, s: AppStrings): String =
    if (id == "mimo_default") s.voiceDefault else id

private fun formatSpeed(v: Float): String = String.format("%.1fx", v)

private fun formatPitch(v: Float): String = String.format("%.2f", v)
