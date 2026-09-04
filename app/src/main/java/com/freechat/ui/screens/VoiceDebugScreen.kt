package com.freechat.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.viewmodel.ChatViewModel

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.voiceDebug, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 13.6.dp)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(top = 13.6.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.Background),
                modifier = Modifier.height(96.dp)
            )
        },
        containerColor = colors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(0.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(2.dp))

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
    }

    // 音色选择
    if (showVoicePicker) {
        AlertDialog(
            onDismissRequest = { showVoicePicker = false },
            containerColor = colors.Surface,
            title = { Text(s.voiceTone, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    viewModel.ttsVoices.forEach { voice ->
                        val sel = voice == ttsVoice
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (sel) colors.AccentMuted else colors.Surface)
                                .clickable {
                                    viewModel.setTtsVoice(voice)
                                    showVoicePicker = false
                                }
                                .padding(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    voiceLabel(voice, s),
                                    color = if (sel) colors.Primary else colors.TextPrimary,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                )
                                if (sel) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showVoicePicker = false }) { Text(s.close, color = colors.TextSecondary) } }
        )
    }
}

private fun voiceLabel(id: String, s: AppStrings): String =
    if (id == "mimo_default") s.voiceDefault else id

private fun formatSpeed(v: Float): String = String.format("%.1fx", v)

private fun formatPitch(v: Float): String = String.format("%.2f", v)
