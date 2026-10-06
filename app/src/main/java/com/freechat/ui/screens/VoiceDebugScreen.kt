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
                            Text(voiceLabel(ttsVoice, s), style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
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
                selected = voice == ttsVoice,
                title = voiceLabel(voice, s),
                colors = colors,
                onClick = {
                    viewModel.setTtsVoice(voice)
                    showVoicePicker = false
                }
            )
        }
    }
    }
    }
}

private fun voiceLabel(id: String, s: AppStrings): String =
    if (id == "mimo_default") s.voiceDefault else id

private fun formatSpeed(v: Float): String = String.format("%.1fx", v)

private fun formatPitch(v: Float): String = String.format("%.2f", v)
