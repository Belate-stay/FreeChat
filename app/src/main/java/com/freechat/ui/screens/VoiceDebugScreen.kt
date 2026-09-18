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
import com.freechat.ui.components.SheetOption
import com.freechat.ui.components.SheetPanel
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.viewmodel.ChatViewModel
import com.freechat.ui.theme.LocalLiquidMode
import com.freechat.ui.theme.pageHeaderBackground
import androidx.compose.ui.graphics.Color
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

    // 外层套一个 Box：音色选择弹层要挂在它最后一个子节点上（SheetPanel 靠 align 贴底、
    // 铺满整屏，挂进 Scaffold 的 Column 里会被约束住）
    Box(Modifier.fillMaxSize()) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.voiceDebug, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 13.6.dp)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(top = 13.6.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .height(96.dp)
                    // 高级材质：顶栏真·透明（沉浸，内容从标题下穿过去）。
                    // 关掉高级材质后顶栏必须**挡住**内容（跟其它页面同一条规矩）——
                    // 所以这里不能用颜色，得用 pageHeaderBackground：炫彩关=这块底色本身，
                    // 炫彩开=钉在屏幕上的一份流光副本，两种情况下都与页面自身上下同色。
                    .then(if (advancedMaterial) Modifier else Modifier.pageHeaderBackground(colors.Background))
            )
        },
        containerColor = if (LocalLiquidMode.current) Color.Transparent else colors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                // 弹层要糊的是这一页的内容，所以这一页得先当一次模糊源
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState) else Modifier),
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

private fun voiceLabel(id: String, s: AppStrings): String =
    if (id == "mimo_default") s.voiceDefault else id

private fun formatSpeed(v: Float): String = String.format("%.1fx", v)

private fun formatPitch(v: Float): String = String.format("%.2f", v)
