package com.freechat.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import java.io.File
import com.freechat.data.MiMoAsr
import com.freechat.data.VoiceLevelRecorder
import com.freechat.i18n.LocalStrings
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.LocalChatFontFamily
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeTint
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// 最短录音长度：16kHz/16bit 单声道 = 32000 字节/秒，0.25s ≈ 8000 字节
private const val MIN_PCM_BYTES = 8000

// 内置常用 emoji 列表（系统原生渲染，无需第三方素材库）
private val EMOJI_LIST = listOf(
    "😀", "😁", "😂", "🤣", "😅", "😊", "😍", "🥰", "😘", "😜", "🤪", "🤔",
    "🙄", "😏", "😬", "😭", "🥺", "😡", "🤯", "😱", "😴", "🥱", "😎", "🥳",
    "👍", "👎", "👌", "✌️", "🤝", "👏", "🙏", "💪", "🫡", "🤙", "👀", "🫶",
    "❤️", "💕", "💔", "💯", "🔥", "✨", "⭐", "🌟", "💫", "🎉", "🎊", "🤗",
    "🤭", "🫠", "💀", "😈", "👻", "🐶", "🐱", "🐰", "🌸", "🍀", "☀️", "🌙"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatInput(
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    isLoading: Boolean,
    isDark: Boolean,
    onAddImage: (() -> Unit)? = null,
    onAddFile: (() -> Unit)? = null,
    pendingImages: List<String> = emptyList(),
    isAddingImages: Boolean = false,
    onRemovePendingImage: (Int) -> Unit = {},
    pendingFiles: List<String> = emptyList(),
    isAddingFiles: Boolean = false,
    onRemovePendingFile: (Int) -> Unit = {},
    offsetYState: State<Float> = mutableFloatStateOf(0f),
    keyboardHeightDp: Dp = 0.dp,
    onInputFocused: () -> Unit = {},
    draftText: String = "",
    onDraftChanged: (String) -> Unit = {},
    onVoiceInput: (String) -> Unit = {},
    onRecognizeVoice: suspend (ByteArray) -> String? = { null },
    onRecordingChanged: (Boolean) -> Unit = {},
    hazeState: HazeState? = null,
    isCompanion: Boolean = false,
    quotedContent: String? = null,
    quotedImagePath: String? = null,
    onClearQuote: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val density = LocalDensity.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(draftText) }
    // 记录光标位置：全屏输入展开时把普通输入框的光标位置带过去，保证接着原位置继续输入
    var textSelection by remember { mutableStateOf(TextRange(0)) }
    // 保留 IME 组合区（composing）：语音转文字/拼音输入靠它维持连续输入，丢了会「录入一个字就退出」
    var textComposition by remember { mutableStateOf<TextRange?>(null) }
    // 仅在草稿非空且与当前文字不同时才恢复草稿（避免发送后 draftText 清空延迟导致文字残留）
    LaunchedEffect(draftText) {
        if (draftText != text) {
            text = draftText
            textSelection = TextRange(draftText.length)
            textComposition = null
        }
    }
    var hasFocus by remember { mutableStateOf(false) }
    val isEmpty = text.isBlank() && pendingImages.isEmpty()
    // 输入框形状：单行=胶囊(50dp)，多行/长文本=圆角矩形(20dp)，避免换行后左右变两个大「半圆」跑道
    val inputShape = if (text.contains('\n') || text.length > 25) RoundedCornerShape(20.dp) else RoundedCornerShape(50.dp)

    // 同步草稿
    LaunchedEffect(text) { onDraftChanged(text) }

    // 发送按钮弹性缩放
    var justSent by remember { mutableStateOf(false) }
    val sendScale by animateFloatAsState(
        targetValue = if (justSent) FreeChatAnimation.SEND_SCALE_PRESSED else 1f,
        animationSpec = FreeChatAnimation.sendPressSpring,
        finishedListener = { justSent = false },
        label = "send_scale"
    )

    val showPlusMenuState = remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    // 全屏输入卡片：文字 ≥4 行自动弹出，或长按输入框弹「全屏输入」选项后手动弹出
    var expanded by remember { mutableStateOf(false) }
    var showFullscreenMenu by remember { mutableStateOf(false) }
    val fullscreenFocusRequester = remember { FocusRequester() }
    val mainFocusRequester = remember { FocusRequester() }

    // 文字 ≥4 行时自动弹出全屏输入卡片
    LaunchedEffect(text) {
        val shouldExpand = text.count { it == '\n' } >= 3
        if (shouldExpand && !expanded) expanded = true
    }

    // 展开时自动把焦点切到全屏输入框（键盘保持、光标跟上）；缩回由缩回按钮切回主输入框
    LaunchedEffect(expanded) {
        if (expanded) fullscreenFocusRequester.requestFocus()
    }

    // ──── 语音录音状态 ────
    val context = LocalContext.current
    val recorder = remember { VoiceLevelRecorder() }
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    // 上滑取消的弧线：记录手指在窗口中的坐标（非 null 表示处于「移出语音键 → 松手取消」状态）
    var cancelFingerWinPos by remember { mutableStateOf<Offset?>(null) }
    // 语音键左上角在窗口中的坐标（用于把手指局部坐标换算成窗口坐标）
    var voiceBtnTopLeftWin by remember { mutableStateOf(Offset.Zero) }

    // 组件销毁时兜底停止录音并释放（切页/返回/切后台等场景，避免 AudioRecord 泄漏或线程悬挂）
    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) {
                recorder.stop()
                isRecording = false
                onRecordingChanged(false)
            }
        }
    }

    // 切后台（ON_PAUSE）时主动停止录音：Compose 不会因切后台触发 onDispose，
    // 若不主动停，录音线程会继续持有麦克风在后台运行，部分 OEM 会触发强杀/隐私弹窗
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && isRecording) {
                recorder.stop()
                isRecording = false
                cancelFingerWinPos = null
                onRecordingChanged(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 输入框收起动画：按住录音时 0→1，松手 1→0，非线性"灵动"曲线
    val collapse by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0f,
        animationSpec = FreeChatAnimation.voiceCollapseTween,
        label = "voice_collapse"
    )

    // 语音键背景色平滑过渡（消除「输入框收缩 vs 语音键变色」的瞬时割裂感）
    // 语音键三态颜色：空输入=深灰、有文字=深蓝、思考/录音中=红（均配磨砂玻璃外观）
    val voiceBtnColor by animateColorAsState(
        targetValue = when {
            isLoading -> colors.ErrorRed
            isRecording -> colors.ErrorRed
            isEmpty -> colors.Primary
            else -> Color(0xFF2B5AA0)
        },
        animationSpec = tween(200),
        label = "voice_btn_color"
    )
    // 图标颜色：高级材质下背景是透光磨砂（偏浅），图标用三态实色才看得清；非高级材质实色背景用 OnPrimary
    val iconTint = if (advancedMaterial) voiceBtnColor else colors.OnPrimary

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* 授予后用户再次长按即可录音 */ }

    val startRecording: () -> Boolean = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            // 未授权：收起键盘后弹权限框，本次手势直接结束（不进入录音流程，避免手势协程悬空）
            keyboardController?.hide()
            focusManager.clearFocus()
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            false
        } else {
            // 录音开始前收起键盘并清除焦点，冻结 IME 状态——避免录音中键盘高度变化导致
            // 取消弧线锚点错位，以及部分 OEM 上麦克风与 IME 同时工作冲突
            keyboardController?.hide()
            focusManager.clearFocus()
            isRecording = true
            recorder.start()
            onRecordingChanged(true)
            true
        }
    }

    // 松手：cancelled=true 表示手指移出了语音键（丢弃音频），否则识别发送
    val finishRecording: (Boolean) -> Unit = { cancelled ->
        val pcm = recorder.stop()
        isRecording = false
        cancelFingerWinPos = null
        onRecordingChanged(false)
        if (!cancelled && pcm.size >= MIN_PCM_BYTES) {
            scope.launch {
                val wav = MiMoAsr.pcmToWav(pcm)
                val text = onRecognizeVoice(wav)
                onVoiceInput(text ?: "")
            }
        }
    }

    // 输入框上移 + 更强阴影，强化「悬浮半空」感
    val bottomSpace = (keyboardHeightDp + 8.dp).coerceAtLeast(36.dp)

    // 发送动作
    val doSend = {
        if (isLoading && !isCompanion) onStop()  // 拟人模式思考中仍可发消息
        else if (!isEmpty) {
            justSent = true
            onDraftChanged("")  // 先清草稿再发送
            onSend(text.trim())
            text = ""
            textComposition = null
            expanded = false  // 发送后自动退出全屏输入框
            mainFocusRequester.requestFocus()  // 缩回后主输入框获焦，键盘保持不缩回
        }
    }

    Column(
        modifier = modifier
            // 用 layout 偏移而非 graphicsLayer，保证 onGloballyPositioned/positionInWindow 跟随滚动位置（弧线锚点不错位）
            .offset { IntOffset(0, offsetYState.value.roundToInt()) }
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = bottomSpace, top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ──── 图片候选区（悬浮在输入框上方，单行小缩略图，超出右滑）────
        if (pendingImages.isNotEmpty()) {
            ImageCandidateArea(
                images = pendingImages,
                isAdding = isAddingImages,
                onRemove = onRemovePendingImage,
                colors = colors,
                isDark = isDark,
                hazeState = hazeState,
                advancedMaterial = advancedMaterial
            )
            Spacer(Modifier.height(8.dp))
        }

        // ──── 文件候选区（悬浮在输入框上方，显示文件名列表，可删除；仅标准模式）────
        if (pendingFiles.isNotEmpty()) {
            FileCandidateArea(
                files = pendingFiles,
                isAdding = isAddingFiles,
                onRemove = onRemovePendingFile,
                colors = colors,
                isDark = isDark,
                hazeState = hazeState,
                advancedMaterial = advancedMaterial
            )
            Spacer(Modifier.height(8.dp))
        }

        // ──── 引用预览区（悬浮在输入框上方，最多两行；引用图片时显示图片缩略图，与图片候选区同款磨砂玻璃）────
        if (quotedContent != null || quotedImagePath != null) {
            QuotePreviewCard(
                content = quotedContent,
                imagePath = quotedImagePath,
                onClear = onClearQuote,
                colors = colors,
                isDark = isDark,
                hazeState = hazeState,
                advancedMaterial = advancedMaterial
            )
            Spacer(Modifier.height(8.dp))
        }

        // ──── 全屏输入卡片（文字超过 3 行自动弹出 / 长按手动弹出，磨砂玻璃同款，平滑过渡）────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
            exit = shrinkVertically(animationSpec = tween(180, easing = FastOutLinearInEasing)) + fadeOut(tween(160))
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp / 3).dp)
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .then(
                            if (advancedMaterial && hazeState != null) Modifier.hazeEffect(state = hazeState) {
                                blurRadius = 28.dp
                                inputScale = HazeInputScale.None
                                backgroundColor = if (isDark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.62f)
                                tints = listOf(HazeTint(if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.18f)))
                            } else Modifier.background(colors.InputBg)
                        )
                        .then(if (advancedMaterial) Modifier.border(1.dp, if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.6f), RoundedCornerShape(18.dp)) else Modifier)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.fullscreenInput, style = MaterialTheme.typography.labelMedium, color = colors.TextTertiary)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { expanded = false; mainFocusRequester.requestFocus() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.KeyboardArrowDown, "缩回", tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                        BasicTextField(
                            value = TextFieldValue(text, textSelection, if (expanded) textComposition else null),
                            onValueChange = { v -> text = v.text; textSelection = v.selection; textComposition = v.composition },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary, fontSize = 15.sp, lineHeight = 20.sp, fontFamily = LocalChatFontFamily.current),
                            cursorBrush = SolidColor(colors.Primary),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).verticalScroll(rememberScrollState()).focusRequester(fullscreenFocusRequester),
                            maxLines = Int.MAX_VALUE
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ──── 左侧输入框区域（录音时整体向右收缩融合，留白给波形）────
            Box(modifier = Modifier.weight(1f)) {
                // 输入框容器（含 + 按钮、输入框）：录音时 scaleX→0，整个「框框」右滑与语音键融合
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = 1f - collapse
                            alpha = 1f - collapse
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        }
                        .shadow(
                            elevation = if (advancedMaterial) 6.dp else 4.dp,
                            shape = inputShape
                        )
                        .clip(inputShape)
                        .then(
                            if (advancedMaterial && hazeState != null) {
                                Modifier.hazeEffect(state = hazeState) {
                                    blurRadius = 28.dp
                                    inputScale = HazeInputScale.None
                                    backgroundColor = if (isDark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.62f)
                                    tints = listOf(HazeTint(if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.18f)))
                                }
                            } else {
                                Modifier.background(colors.InputBg)
                            }
                        )
                        .then(
                            if (advancedMaterial) Modifier.border(1.dp, if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.6f), inputShape) else Modifier
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // + 按钮
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    // 键盘保持显示，直接弹菜单（菜单 focusable=false 不抢焦点，不会闪退）
                                    showPlusMenuState.value = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = s.addImage,
                                tint = colors.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 输入区
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .heightIn(min = 40.dp)
                                .pointerInput(Unit) {
                                    // 消费长按，阻止 BasicTextField 的文本选择工具栏弹出，改弹「全屏输入」选项
                                    detectTapGestures(onLongPress = { showFullscreenMenu = true })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicTextField(
                                value = TextFieldValue(if (expanded) "" else text, textSelection, if (expanded) null else textComposition),
                                onValueChange = { v -> text = v.text; textSelection = v.selection; textComposition = v.composition },
                                readOnly = expanded,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.TextPrimary, fontSize = 15.sp, lineHeight = 20.sp,
                                    fontFamily = LocalChatFontFamily.current
                                ),
                                cursorBrush = SolidColor(colors.Primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(mainFocusRequester)
                                    .onFocusChanged { focusState ->
                                        hasFocus = focusState.isFocused
                                        if (focusState.isFocused) {
                                            showEmojiPicker = false
                                            onInputFocused()
                                        }
                                    },
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (expanded) {
                                            Text(
                                                "全屏输入中...",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                                                color = colors.TextTertiary,
                                                modifier = Modifier.padding(top = 1.dp)
                                            )
                                        } else if (text.isEmpty() && pendingImages.isEmpty() && !hasFocus) {
                                            Text(
                                                if (isCompanion) s.companionPlaceholder else s.inputPlaceholder,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                                                color = colors.TextTertiary,
                                                modifier = Modifier.padding(top = 1.dp)
                                            )
                                        }
                                        inner()
                                    }
                                },
                                maxLines = 3
                            )
                            // 长按输入框弹出的「全屏输入」选项菜单
                            DropdownMenu(
                                expanded = showFullscreenMenu,
                                onDismissRequest = { showFullscreenMenu = false },
                                modifier = Modifier.background(colors.Surface),
                                properties = PopupProperties(focusable = false)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(s.fullscreenInput, color = colors.TextPrimary) },
                                    onClick = { showFullscreenMenu = false; expanded = true },
                                    leadingIcon = { Icon(Icons.Filled.OpenInFull, null, tint = colors.Primary, modifier = Modifier.size(20.dp)) }
                                )
                            }
                        }

                        // emoji 按钮（仅拟人模式显示，输入框内右侧，与左侧 + 键对称）
                        if (isCompanion) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (showEmojiPicker) {
                                            showEmojiPicker = false
                                        } else {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            showEmojiPicker = true
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("😊", fontSize = 20.sp)
                            }
                        }
                    }
                }

                // 波形：录音时从右侧展开，渐变波浪线条实时响应音量
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = collapse
                            alpha = collapse
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        }
                ) {
                    GradientWaveform(
                        levelFlow = recorder.level,
                        isDark = isDark,
                        colors = colors,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // ──── 语音/发送/停止键（独立，同位置同大小，图标随状态切换）────
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(sendScale)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .then(
                        if (advancedMaterial && hazeState != null) {
                            Modifier.hazeEffect(state = hazeState) {
                                blurRadius = 28.dp
                                inputScale = HazeInputScale.None
                                backgroundColor = voiceBtnColor.copy(alpha = if (isDark) 0.55f else 0.7f)
                                tints = listOf(HazeTint(if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.18f)))
                            }
                        } else {
                            Modifier.background(voiceBtnColor)
                        }
                    )
                    .then(if (advancedMaterial) Modifier.border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape) else Modifier)
                    .onGloballyPositioned { coords ->
                        voiceBtnTopLeftWin = coords.positionInWindow()
                    }
                    .then(
                        if (isEmpty && !isLoading) {
                            // 空输入框：按住录音，移出语音键出现取消弧线，松手位置决定发送/取消
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    try {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()
                                        if (!startRecording()) return@awaitEachGesture
                                        var fingerOutside = false
                                        var fingerWinPos = Offset.Zero
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val leavePx = with(density) { (20.dp + 8.dp).toPx() } // 语音键半径 + 余量
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                change.consume()
                                                break
                                            }
                                            // move 也 consume：让抽屉检测器看到 isConsumed 自取消，锁住侧滑
                                            change.consume()
                                            val dist = (change.position - center).getDistance()
                                            fingerOutside = dist > leavePx
                                            fingerWinPos = voiceBtnTopLeftWin + change.position
                                            cancelFingerWinPos = if (fingerOutside) fingerWinPos else null
                                        }
                                        cancelFingerWinPos = null
                                        finishRecording(fingerOutside)
                                    } finally {
                                        // 手势被取消（dispose/返回/切后台）时兜底清理，避免录音线程泄漏
                                        cancelFingerWinPos = null
                                        if (isRecording) {
                                            recorder.stop()
                                            isRecording = false
                                            onRecordingChanged(false)
                                        }
                                    }
                                }
                            }
                        } else {
                            Modifier.clickable(enabled = (isLoading || !isEmpty) && !isAddingImages) { doSend() }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading && !isCompanion -> Icon(Icons.Filled.Stop, "停止", tint = iconTint, modifier = Modifier.size(18.dp))
                    isEmpty -> Icon(Icons.Filled.Mic, "按住说话", tint = iconTint, modifier = Modifier.size(20.dp))
                    else -> Icon(Icons.Filled.ArrowUpward, "发送", tint = iconTint, modifier = Modifier.size(18.dp))
                }
            }
        }

        // ──── emoji 面板（输入框下方，替代键盘位置；展开/收起与键盘丝滑衔接）────
        AnimatedVisibility(
            visible = showEmojiPicker,
            enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180, easing = FastOutLinearInEasing)) +
                fadeOut(animationSpec = tween(160))
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.InputBg,
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp
                ) {
                    Column(Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) {
                        EMOJI_LIST.chunked(8).forEach { rowEmojis ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                rowEmojis.forEach { em ->
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { text += em },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(em, fontSize = 22.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ──── 下拉菜单（"+"" 弹出的菜单）────
        DropdownMenu(
            expanded = showPlusMenuState.value,
            onDismissRequest = { showPlusMenuState.value = false },
            modifier = Modifier.background(colors.Surface),
            properties = PopupProperties(focusable = false)
        ) {
            DropdownMenuItem(
                text = { Text(s.uploadImage, color = colors.TextPrimary) },
                onClick = { showPlusMenuState.value = false; onAddImage?.invoke() },
                leadingIcon = { Icon(Icons.Filled.Image, null, tint = colors.Primary, modifier = Modifier.size(20.dp)) }
            )
            if (onAddFile != null) {
                DropdownMenuItem(
                    text = { Text(s.uploadFile, color = colors.TextPrimary) },
                    onClick = { showPlusMenuState.value = false; onAddFile?.invoke() },
                    leadingIcon = { Icon(Icons.Filled.AttachFile, null, tint = colors.Primary, modifier = Modifier.size(20.dp)) }
                )
            }
        }

        // ──── 上滑取消弧线（始终跟随手指，红色光影 + 「松手取消」）────
        if (cancelFingerWinPos != null) {
            VoiceCancelArc(
                fingerWinPos = cancelFingerWinPos!!,
                cancelColor = colors.ErrorRed,
                density = density
            )
        }
    }
}

// ──── 图片候选区：悬浮卡片，单行小缩略图（约 7 张一屏，超出右滑），右上角可删除 ────
@Composable
private fun ImageCandidateArea(
    images: List<String>,
    isAdding: Boolean,
    onRemove: (Int) -> Unit,
    colors: FreeChatColors,
    isDark: Boolean,
    hazeState: HazeState?,
    advancedMaterial: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (advancedMaterial && hazeState != null) Modifier.hazeEffect(state = hazeState) {
                    blurRadius = 28.dp
                    inputScale = HazeInputScale.None
                    backgroundColor = if (isDark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.62f)
                    tints = listOf(HazeTint(if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.18f)))
                } else Modifier.background(colors.InputBg)
            )
            .then(if (advancedMaterial) Modifier.border(1.dp, if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.6f), RoundedCornerShape(18.dp)) else Modifier)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "图片 ${images.size}/9",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.TextTertiary
                )
                Spacer(Modifier.weight(1f))
                if (isAdding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colors.Primary
                    )
                }
            }

            // 单行小缩略图，从左往右，7 张内一屏放满，超过可右滑
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                images.forEachIndexed { idx, path ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        LocalImage(
                            path = path,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            targetMaxDim = 256
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { onRemove(idx) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "移除",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ──── 文件候选区：悬浮卡片，文件名列表 + 删除（仅标准模式上传文件）────
@Composable
private fun FileCandidateArea(
    files: List<String>,
    isAdding: Boolean,
    onRemove: (Int) -> Unit,
    colors: FreeChatColors,
    isDark: Boolean,
    hazeState: HazeState?,
    advancedMaterial: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (advancedMaterial && hazeState != null) Modifier.hazeEffect(state = hazeState) {
                    blurRadius = 28.dp
                    inputScale = HazeInputScale.None
                    backgroundColor = if (isDark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.62f)
                    tints = listOf(HazeTint(if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.18f)))
                } else Modifier.background(colors.InputBg)
            )
            .then(if (advancedMaterial) Modifier.border(1.dp, if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.6f), RoundedCornerShape(18.dp)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("文件 ${files.size}/3", style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary)
                Spacer(Modifier.weight(1f))
                if (isAdding) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.Primary)
                }
            }
            files.forEachIndexed { idx, name ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Description, null, tint = colors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.TextPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { onRemove(idx) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, "移除", tint = colors.TextTertiary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ──── 引用预览卡：输入框上方，左上角「引用」标签 + 最多两行引用内容（灰色）+ 删除叉 ────
@Composable
private fun QuotePreviewCard(
    content: String?,
    imagePath: String?,
    onClear: () -> Unit,
    colors: FreeChatColors,
    isDark: Boolean,
    hazeState: HazeState?,
    advancedMaterial: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (advancedMaterial && hazeState != null) Modifier.hazeEffect(state = hazeState) {
                    blurRadius = 28.dp
                    inputScale = HazeInputScale.None
                    backgroundColor = if (isDark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.62f)
                    tints = listOf(HazeTint(if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.18f)))
                } else Modifier.background(colors.InputBg)
            )
            .then(if (advancedMaterial) Modifier.border(1.dp, if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.6f), RoundedCornerShape(18.dp)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(colors.Primary)
            )
            Spacer(Modifier.width(8.dp))
            if (imagePath != null) {
                // 引用图片：显示图片缩略图（与图片候选区缩略图一致大小）
                LocalImage(
                    path = imagePath,
                    contentDescription = "引用图片",
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                    targetMaxDim = 256
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("引用", style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary)
                    Spacer(Modifier.height(2.dp))
                    Text("图片", style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text("引用", style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        content ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                        color = colors.TextSecondary,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, "取消引用", tint = colors.TextTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ──── 上滑取消弧线：Popup 定位到手指窗口坐标，弧线画在手指上方，跟随手指 ────
@Composable
private fun VoiceCancelArc(
    fingerWinPos: Offset,
    cancelColor: Color,
    density: androidx.compose.ui.unit.Density
) {
    val arcRadiusPx = with(density) { 60.dp.toPx() }
    Popup(
        popupPositionProvider = remember(fingerWinPos) { FixedPositionProvider(fingerWinPos) },
        properties = PopupProperties(focusable = false)
    ) {
        // Box 中心 = 弧形中心 = 手指上方一个半径处
        Box(
            modifier = Modifier
                .size(with(density) { (arcRadiusPx * 2).toDp() })
                .offset {
                    IntOffset(
                        -arcRadiusPx.roundToInt(),
                        (-arcRadiusPx * 2).roundToInt()
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f
                // 光晕层：宽而淡，模拟柔光
                drawArc(
                    color = cancelColor.copy(alpha = 0.10f),
                    startAngle = 225f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = with(density) { 9.dp.toPx() }, cap = StrokeCap.Round)
                )
                // 主层：细而清晰
                drawArc(
                    color = cancelColor.copy(alpha = 0.55f),
                    startAngle = 225f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round)
                )
            }
            Text(
                "松手取消",
                color = cancelColor.copy(alpha = 0.85f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
            )
        }
    }
}

private class FixedPositionProvider(private val pos: Offset) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = pos.x.roundToInt().coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = pos.y.roundToInt().coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

/**
 * 渐变波浪波形：底层彩色光斑加色光晕 + 上层 3 条谐波叠加的彩色波浪线。
 * 相位用真实 dt 变速（惯性/呼吸感），drawWithCache 缓存 Path（仅振幅变化才重建，消除掉帧主因）。
 */
@Composable
private fun GradientWaveform(
    levelFlow: StateFlow<Float>,
    isDark: Boolean,
    colors: FreeChatColors,
    modifier: Modifier = Modifier
) {
    val raw by levelFlow.collectAsState()
    val amplitude by animateFloatAsState(raw, FreeChatAnimation.voiceLevelTween, label = "wave_amp")

    val TAU = 2f * PI.toFloat()
    var phase0 by remember { mutableFloatStateOf(0f) }
    var phase1 by remember { mutableFloatStateOf(0f) }
    var phase2 by remember { mutableFloatStateOf(0f) }
    // 变速相位：真实 dt + 速度随时间/音量变速 → 惯性/呼吸感，而非匀速流水线
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last).coerceAtLeast(0L)) / 1e9f
                last = now
                val breath = 0.5f + 0.5f * sin(now / 1e9f * 0.35f)
                val vol = 0.55f + 0.45f * amplitude
                phase0 = (phase0 + 1.00f * breath * vol * dt) % TAU
                phase1 = (phase1 + 0.72f * breath * vol * dt) % TAU
                phase2 = (phase2 + 1.35f * breath * vol * dt) % TAU
            }
        }
    }

    // 主题分流调色（深色高亮、浅色更深更饱和，融合背景不突兀）
    val palette = if (isDark) listOf(
        Color(0xFF9D6BFF), Color(0xFF4DA6FF), Color(0xFF3EE6C8), Color(0xFFFFB84D), Color(0xFFFF6BA0)
    ) else listOf(
        Color(0xFF6A3DE8), Color(0xFF1F6FD0), Color(0xFF0FAE9A), Color(0xFFE8890B), Color(0xFFE0447C)
    )
    val glowBlend = if (isDark) BlendMode.Plus else BlendMode.Screen

    Box(modifier = modifier.drawWithCache {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val amp = (h * 0.44f) * (0.14f + 0.86f * amplitude)
        // 基础波长随音量：小声波密（频率高）、大声波疏（大波浪）
        val baseL = w * (0.85f + 0.95f * amplitude)
        val wavelengths = floatArrayOf(baseL * 0.80f, baseL * 1.00f, baseL * 1.25f)
        // 每条波振幅递减（层次感）
        val ampMuls = floatArrayOf(1.00f, 0.72f, 0.48f)

        // 三条波：谐波叠加（整数倍频率 → 周期=各自波长，平移循环无缝）
        val paths = (0 until 3).map { i ->
            val l = wavelengths[i]
            val k = 2f * PI.toFloat() / l
            Path().apply {
                val step = 6f
                var x = 0f
                var first = true
                val len = w + l
                while (x <= len) {
                    val shape = sin(x * k) * 0.62f +
                        sin(x * 2f * k + 1.7f) * 0.26f +
                        sin(x * 3f * k + 2.3f) * 0.12f
                    val y = centerY + amp * ampMuls[i] * shape
                    if (first) { moveTo(x, y); first = false } else lineTo(x, y)
                    x += step
                }
            }
        }

        // 光斑（光晕打底）：4 个彩色柔光斑，加色混合，缓慢漂移
        val blobs = listOf(
            Triple(0.22f, 0.38f, palette[0]),
            Triple(0.62f, 0.55f, palette[1]),
            Triple(0.42f, 0.28f, palette[2]),
            Triple(0.80f, 0.45f, palette[3])
        )

        onDrawBehind {
            val glowAlpha = 0.06f + 0.30f * amplitude
            // 光晕打底层
            blobs.forEachIndexed { i, (fx, fy, c) ->
                val cx = w * (fx + 0.06f * sin(phase0 * 0.40f + i * 1.7f))
                val cy = h * (fy + 0.08f * sin(phase1 * 0.30f + i * 2.3f))
                val r = h * 0.95f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c.copy(alpha = 0.28f * glowAlpha), c.copy(alpha = 0f)),
                        center = Offset(cx, cy),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                    blendMode = glowBlend
                )
            }
            // 波浪层：缓存 Path 平移循环（每帧只 3 次 drawPath，复用缓存）
            for (i in 0 until 3) {
                val l = wavelengths[i]
                val offset = (when (i) {
                    0 -> phase0; 1 -> phase1; else -> phase2
                } / TAU) * l
                val c0 = palette[i % palette.size]
                val c1 = palette[(i + 2) % palette.size]
                translate(left = -offset) {
                    drawPath(
                        path = paths[i],
                        brush = Brush.horizontalGradient(
                            0f to c0.copy(alpha = 0.85f),
                            0.5f to c1.copy(alpha = 0.95f),
                            1f to c0.copy(alpha = 0.85f)
                        ),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    })
}
