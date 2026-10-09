package com.freechat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.freechat.data.*
import com.freechat.ui.components.HeaderIconButton
import com.freechat.ui.components.TopBarBackdrop
import com.freechat.ui.components.TopBarBackdropSource
import com.freechat.ui.animation.LocalPageActive
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.theme.pageBackground
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Creation is an explicit API action; uploading, recording, editing and naming alone are local. */
@Composable
fun MiMoVoiceSettingsPage(onBack: () -> Unit, onSaved: (VoicePreset) -> Unit) {
    val context = LocalContext.current
    val colors = LocalFreeChatColors.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.TextPrimary,
        unfocusedTextColor = colors.TextPrimary,
        focusedBorderColor = colors.Primary,
        unfocusedBorderColor = colors.Divider,
        cursorColor = colors.Primary
    )
    val repository = remember(context) { VoicePresetRepository.get(context) }
    val recorder = remember { VoiceSampleRecorder() }
    val capture by recorder.state.collectAsState()
    val apiError by TtsController.lastError.collectAsState()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    val pageActive = LocalPageActive.current
    val drafts = remember { mutableSetOf<VoiceSampleRef>() }
    var mode by remember { mutableStateOf<VoiceCreationMode?>(null) }
    var prompt by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var previewText by remember { mutableStateOf("你好，很高兴能用这个声音与你交流。希望这段熟悉的声音，陪你度过轻松的一天。") }
    var reference by remember { mutableStateOf<VoiceSampleRef?>(null) }
    var completed by remember { mutableStateOf<VoiceCreationPreview?>(null) }
    var name by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var workGeneration by remember { mutableIntStateOf(0) }
    var workJob by remember { mutableStateOf<Job?>(null) }
    val busy = creating || importing || finishing || saving || previewing || !pageActive

    fun beginWork(): Int {
        workJob?.cancel()
        workGeneration += 1
        return workGeneration
    }

    fun clearPreview() {
        val old = completed
        completed = null
        name = ""
        old?.let {
            val disposable = setOf(it.preview, it.reference).filterNot { ref -> ref == reference }.toSet()
            drafts.removeAll(disposable)
            repository.discardLater(disposable)
        }
        TtsController.stop()
        TtsController.clearError()
    }

    fun replaceReference(next: VoiceSampleRef) {
        val old = reference
        clearPreview()
        reference = next
        drafts += next
        consent = false
        if (old == next) {
            // Every import acquires a lease; retain the draft's original one only.
            repository.discardLater(setOf(next))
        } else if (old != null) {
            drafts -= old
            repository.discardLater(setOf(old))
        }
    }

    fun cancelDraft() {
        workJob?.cancel()
        workGeneration += 1
        workJob = null
        recorder.cancel()
        TtsController.stop()
        repository.discardLater(drafts.toSet())
        drafts.clear()
        reference = null
        completed = null
        consent = false
        notice = null
        name = ""
        creating = false
        importing = false
        finishing = false
        saving = false
        previewing = false
        TtsController.clearError()
    }

    fun goBack() {
        cancelDraft()
        if (mode == null) onBack() else mode = null
    }

    fun finishRecording() {
        if (finishing || mode != VoiceCreationMode.AUDIO) return
        val generation = beginWork()
        finishing = true
        notice = null
        workJob = scope.launch {
            try {
                val wav = recorder.finish()
                val next = repository.importSample(ByteArrayInputStream(wav))
                replaceReference(next)
                notice = "录音已保存到本机，可创建音色。"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { notice = error.message ?: "录音保存失败，请重新录制。" }
            finally { if (generation == workGeneration) finishing = false }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (pageActive && mode == VoiceCreationMode.AUDIO && !busy) {
            if (granted) {
                notice = null
                if (recorder.start()) { clearPreview(); consent = false }
            }
            else notice = "未获得麦克风权限。你可以重新允许权限，或直接上传音频。"
        }
    }
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (pageActive && uri != null && mode == VoiceCreationMode.AUDIO && !busy && !capture.isRecording) {
            val generation = beginWork()
            importing = true
            notice = null
            workJob = scope.launch {
                try {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw VoiceConfigurationException("无法打开这段音频，请重新选择文件。")
                    val next = repository.importSample(input)
                    replaceReference(next)
                    notice = "参考音频已复制到本机私有目录。"
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { notice = error.message ?: "音频导入失败，请选择 MP3 或 WAV 文件。" }
                finally { if (generation == workGeneration) importing = false }
            }
        }
    }

    BackHandler(enabled = pageActive, onBack = ::goBack)
    LaunchedEffect(Unit) { TtsController.clearError() }
    LaunchedEffect(capture.reachedLimit) { if (capture.reachedLimit && mode == VoiceCreationMode.AUDIO) finishRecording() }
    LaunchedEffect(capture.error) {
        if (capture.error != null && !capture.isRecording) {
            notice = capture.error
            recorder.cancel()
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && recorder.state.value.isRecording) {
                recorder.cancel()
                notice = "录音已取消。重新录制后再创建音色。"
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            workJob?.cancel()
            recorder.cancel()
            TtsController.stop()
            repository.discardLater(drafts.toSet())
        }
    }

    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBandHeight = HazeSpec.topBandHeightDp(statusBarHeight)
    CompositionLocalProvider(LocalSettingsHazeState provides hazeState) {
        Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
            TopBarBackdropSource(hazeState, colors.Background, topBandHeight)
            Column(
                Modifier.fillMaxSize()
                    .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
                    .verticalScroll(rememberScrollState())
                    .padding(top = HazeSpec.topContentPaddingDp(statusBarHeight), bottom = 36.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (mode == null) {
                    Text("创建自己的声音", color = colors.TextPrimary, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("选择模拟方式，创建成功后可以试听、命名并保存为本机音色。", color = colors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall)
                    AdvancedVoiceChoice("根据提示词模拟", "用文字描述音色、年龄、质感与语气。", Icons.Filled.Edit) {
                        mode = VoiceCreationMode.PROMPT
                    }
                    AdvancedVoiceChoice("根据音频模拟", "上传 MP3、WAV，或录制自己的声音。", Icons.Filled.Mic) {
                        mode = VoiceCreationMode.AUDIO
                    }
                    Text("音色配置和参考音频仅保存在本机，暂不参与账号同步、导出或系统备份。", color = colors.TextTertiary,
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    SectionLabel(if (mode == VoiceCreationMode.PROMPT) Icons.Filled.Edit else Icons.Filled.Mic,
                        if (mode == VoiceCreationMode.PROMPT) "根据提示词模拟" else "根据音频模拟")
                    SettingsRow {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (mode == VoiceCreationMode.PROMPT) {
                                Text("音色描述", color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                CharacterTextField(value = prompt, onValueChange = {
                                    if (it.length <= 4000) { prompt = it; clearPreview() }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7,
                                    placeholder = { Text("例如：年轻女性，嗓音温柔清亮，语速舒缓，像深夜电台主持人。", color = characterHintColor(colors)) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary), colors = fieldColors)
                                Text("描述年龄、声线和表达方式，中英文均可。${prompt.length}/4000",
                                    color = colors.TextTertiary, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("使用本人或已获授权的声音。录音至少 3 秒，最长 60 秒会自动停止。", color = colors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { upload.launch("audio/*") }, enabled = !busy && !capture.isRecording) {
                                        Icon(Icons.Filled.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("上传音频")
                                    }
                                    if (capture.isRecording) {
                                        Button(onClick = ::finishRecording, enabled = !busy) { Text("停止录音") }
                                    } else {
                                        OutlinedButton(onClick = {
                                            notice = null
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                if (recorder.start()) { clearPreview(); consent = false }
                                            } else permission.launch(Manifest.permission.RECORD_AUDIO)
                                        }, enabled = !busy) { Text("录制声音") }
                                    }
                                }
                                if (capture.isRecording) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("正在录音 ${(capture.recordedMs / 1000)} / 60 秒", color = colors.Primary)
                                        TextButton(onClick = { recorder.cancel(); notice = "录音已取消。" }) { Text("取消录音") }
                                    }
                                    LinearProgressIndicator(progress = { (capture.recordedMs / 60000f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                }
                                reference?.let { ref ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("已选择本机参考音频", color = colors.TextPrimary)
                                            Text("${if (ref.mimeType == "audio/wav") "WAV" else "MP3"} · ${ref.sizeBytes / 1024} KB",
                                                color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(enabled = !busy && !capture.isRecording, onClick = {
                                            clearPreview()
                                            reference = null
                                            drafts -= ref
                                            repository.discardLater(setOf(ref))
                                            notice = "参考音频已从本机移除。"
                                        }) { Text("移除") }
                                    }
                                }
                                Text("仅支持完整有效的 MP3 或 WAV。文件的 Base64 编码上限为 10 MB。", color = colors.TextTertiary,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text("说话风格（可选）", color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            CharacterTextField(value = style, onValueChange = { if (it.length <= 1000) { style = it; clearPreview() } },
                                enabled = !busy && !capture.isRecording, modifier = Modifier.fillMaxWidth(), maxLines = 3,
                                placeholder = { Text("例如：语气自然，轻声交流。", color = characterHintColor(colors)) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary), colors = fieldColors)
                            Text("试听文本", color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            CharacterTextField(value = previewText, onValueChange = { if (it.length <= 300) { previewText = it; clearPreview() } },
                                enabled = !busy && !capture.isRecording, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                                placeholder = { Text("输入这段声音要朗读的文本。", color = characterHintColor(colors)) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary), colors = fieldColors)
                            Text("${previewText.length}/300", color = colors.TextTertiary, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth().clickable(enabled = !busy && !capture.isRecording) { consent = !consent },
                                verticalAlignment = Alignment.Top) {
                                Checkbox(consent, onCheckedChange = { consent = it }, enabled = !busy && !capture.isRecording)
                                Text(if (mode == VoiceCreationMode.PROMPT)
                                    "我同意将描述发送给 MiMo，并在以后朗读时向 MiMo 发送生成的参考音频。"
                                    else "我有权使用这段声音，同意在创建和以后朗读时向 MiMo 发送参考音频。",
                                    Modifier.weight(1f).padding(top = 12.dp), color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(enabled = !busy && !capture.isRecording && consent && previewText.isNotBlank() &&
                                (if (mode == VoiceCreationMode.PROMPT) prompt.isNotBlank() else reference != null),
                                onClick = {
                                    val generation = beginWork()
                                    val creationMode = mode ?: return@Button
                                    val currentReference = reference
                                    val promptSnapshot = prompt
                                    val styleSnapshot = style
                                    val textSnapshot = previewText
                                    clearPreview()
                                    notice = null
                                    creating = true
                                    workJob = scope.launch {
                                        try {
                                            val config = if (creationMode == VoiceCreationMode.PROMPT) {
                                                MiMoVoiceConfig(modelId = BuiltInVoiceModel.DESIGN_API_ID, designPrompt = promptSnapshot,
                                                    speakerStyle = styleSnapshot)
                                            } else {
                                                val ref = currentReference ?: throw VoiceConfigurationException("请先上传或录制参考音频。")
                                                MiMoVoiceConfig(modelId = BuiltInVoiceModel.CLONE_API_ID, speakerStyle = styleSnapshot,
                                                    sample = repository.readSample(ref))
                                            }
                                            val audio = TtsController.synthesizePreview(textSnapshot, config)
                                            if (audio != null) {
                                                val preview = repository.preparePreview(creationMode, if (creationMode == VoiceCreationMode.PROMPT) promptSnapshot else "",
                                                    styleSnapshot, currentReference, audio)
                                                drafts += preview.reference
                                                drafts += preview.preview
                                                completed = preview
                                                notice = "创建成功，可以试听并命名保存。"
                                                TtsController.speakSingle("mimo-custom-voice-preview", audio, 1f, 1f)
                                            }
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (error: Exception) { notice = error.message ?: "创建失败，请重试。" }
                                        finally { if (generation == workGeneration) creating = false }
                                    }
                                }, modifier = Modifier.fillMaxWidth()) {
                                if (creating) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.OnPrimary)
                                    Spacer(Modifier.width(8.dp)); Text("正在创建语音…")
                                } else Text(if (completed == null) "创建/试听" else "重新创建")
                            }
                            if (creating) TextButton(onClick = {
                                workJob?.cancel(); workGeneration += 1; TtsController.stop(); creating = false; notice = "创建已取消。"
                            }, modifier = Modifier.align(Alignment.End)) { Text("取消创建") }
                            if (importing || finishing || saving || previewing) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }

                    completed?.let { preview ->
                        SectionLabel(Icons.Filled.CheckCircle, "保存新音色")
                        SettingsRow {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("试听满意后为音色命名。以后朗读会使用这次保存的参考音频模拟同一声线。", color = colors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(enabled = !busy, onClick = {
                                        val generation = beginWork()
                                        previewing = true
                                        notice = null
                                        workJob = scope.launch {
                                            try { TtsController.speakSingle("mimo-custom-voice-preview", repository.readSample(preview.preview).bytes(), 1f, 1f) }
                                            catch (cancelled: CancellationException) { throw cancelled }
                                            catch (error: Exception) { notice = error.message ?: "试听失败，请重新创建。" }
                                            finally { if (generation == workGeneration) previewing = false }
                                        }
                                    }) { Icon(Icons.Filled.PlayArrow, null); Text("再次试听") }
                                    TextButton(onClick = { TtsController.stop() }) { Text("停止试听") }
                                }
                                Text("音色名称", color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                CharacterTextField(value = name, onValueChange = { if (it.length <= 40) name = it }, enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    placeholder = { Text("例如：温柔电台", color = characterHintColor(colors)) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary), colors = fieldColors)
                                Button(enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                                    val generation = beginWork()
                                    saving = true
                                    notice = null
                                    workJob = scope.launch {
                                        try {
                                            val saved = repository.save(name, preview)
                                            drafts.remove(preview.reference)
                                            drafts.remove(preview.preview)
                                            repository.discardLater(setOf(preview.reference, preview.preview))
                                            reference = null
                                            consent = false
                                            completed = null
                                            onSaved(saved)
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (error: Exception) { notice = error.message ?: "保存音色失败，请重试。" }
                                        finally { if (generation == workGeneration) saving = false }
                                    }
                                }) { Text("保存并使用") }
                            }
                        }
                    }
                    (apiError?.message ?: notice)?.let {
                        Text(it, Modifier.padding(horizontal = 8.dp), color = if (apiError == null) colors.TextSecondary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("创建由 MiMo 在线合成完成，模型权限和可用额度取决于当前账号。音色以本机配置保存，每次朗读都会重新发送参考音频。",
                        color = colors.TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
            TopBarBackdrop(hazeState, colors.Background, topBandHeight)
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(top = 8.8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                HeaderIconButton(onClick = { if (pageActive) goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = colors.TextPrimary) }
                Text("高级自定义", color = colors.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun AdvancedVoiceChoice(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val colors = LocalFreeChatColors.current
    SettingsRow {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = colors.Primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(description, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
