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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.freechat.ui.animation.MotionIconButton as IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
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
import com.freechat.model.InputStyle
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.LocalMotionEnabled
import androidx.compose.ui.draw.alpha
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.inputBtnIcon
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.LocalChatFontFamily
import com.freechat.ui.theme.selectedFill
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.theme.floatingSurface
import com.freechat.ui.theme.rememberGlassBackdropProbe
import com.freechat.ui.theme.glassProbeBounds
import com.freechat.ui.theme.softShadow
import com.freechat.ui.theme.liquidOpaqueBackground
import dev.chrisbanes.haze.HazeState
import com.freechat.ui.theme.materialHaze as hazeEffect
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeTint
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

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

// =============================================
// 「哑巴」文本工具条：让主输入框永远不弹安卓原生那一条（复制/粘贴/自动填充）。
//
// 为什么必须有它：Compose 的 BasicTextField 一被长按，就会通过 LocalTextToolbar 让系统
// 弹一条悬浮小条。我们自己的「长按 = 叫出全屏输入菜单」同时也在弹卡片，两条叠在一起，
// 用户截图里那句「粘贴 / 自动填充」就是它 —— 而且它是系统窗口，画在我们卡片上面，点不动。
//
// 做法是把这个 CompositionLocal 换成空实现：BasicTextField 照常调用，只是没人接活。
// 代价是主输入框里没有「复制/剪切/全选」了 —— 用户要的就是这个（粘贴已经做成内置菜单项）。
// 全屏输入卡片的输入框**不套**这个，那边长按仍然走系统原生那一条，粘贴还能用。
// =============================================
private object SuppressedTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
    override fun hide() = Unit
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) = Unit
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatInput(
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    isLoading: Boolean,
    isDark: Boolean,
    onAddImage: (() -> Unit)? = null,
    onAddFile: (() -> Unit)? = null,
    onPlusClick: () -> Unit = {},
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
    voiceSessionKey: String? = null,
    hazeState: HazeState? = null,
    isCompanion: Boolean = false,
    /**
     * 叙事单发模式（动作演绎 / 剧情补足）：
     * 一次只发一条、AI 回一条，发送后右侧按键立刻变成**终止键**。
     * 点终止键 = 撤回刚发出去的那条消息 + 立刻掐断 AI 的思考与生成 + 把提示词放回输入框。
     * 微信聊天档不走这条路（那边是连续发送，没有终止键）。
     */
    narrativeSingleSend: Boolean = false,
    onRetract: () -> Unit = {},
    /**
     * 「把焦点抢到输入框」的信号：每次自增都让输入框获焦、键盘弹出。
     * 点气泡改写提示词时用——要求是「点了气泡键盘直接弹出来」，而不是再弹一个编辑对话框。
     */
    focusTick: Int = 0,
    /**
     * 长按输入框 → 请求「全屏输入」菜单。
     *
     * 菜单**不在这里渲染**：它要的那层磨砂哑光玻璃必须和聊天内容在**同一个窗口**里
     * 才糊得住（Popup 是独立窗口，采不到背景，只能退化成一块实色卡片），
     * 所以交给 ChatScreen 用「窗口内浮层」去画。这里只负责把「用户长按了」报上去。
     */
    onFullscreenMenuRequest: () -> Unit = {},
    /**
     * 「展开全屏输入」的信号（长按菜单里点了那一下）：每次自增就展开。
     * 与 [focusTick] 同一套「自增信号」的写法 —— 用布尔开关的话，用完之后还得有人负责复位。
     */
    fullscreenTick: Int = 0,
    /**
     * 「内置粘贴」的信号（长按菜单里点了那一下）：每次自增就把剪贴板文字插到光标处。
     *
     * 为什么粘在输入框这一层做、而不是让 ChatScreen 直接改草稿：文字住在这一层
     * （`text`/`textSelection`/`textComposition` 三件套是这里的私有状态），外面只报意图，
     * 免得两边各存一份光标位置、粘贴完光标跑到别处去。
     * 与 [focusTick]/[fullscreenTick] 同一套「自增信号」的写法 —— 用布尔开关还得有人负责复位。
     */
    pasteTick: Int = 0,
    /**
     * 全屏输入卡片的展开/收起上报。
     *
     * 列表底部留白要按它加一截冗余 —— 卡片展开时输入框整体变高，不给列表补上这一截，
     * 最后一条消息就被卡片压住、怎么滑都露不出来。留白的高度由调用方**实测**，
     * 所以这里只报「开没开」，不报高度。
     */
    onExpandedChanged: (Boolean) -> Unit = {},
    /**
     * 输入框样式（1.0.70）：简洁 = 现状（单行胶囊 + 超 3 行自动全屏卡片）；
     * 完整 = 全屏与标准输入合二为一 —— 两行完整输入框，上行文字换行就地向上长高
     * （封顶到原全屏输入高度后内部滚动），下行 [+] + 状态快捷键 + 语音/发送圆钮。
     */
    inputStyle: InputStyle = InputStyle.COMPACT,
    /**
     * 「生成图片」（仅标准模式）：一次性强制生图，发出即自动灭。
     * 1.0.75：底行**文字键收回「+」菜单**（两种样式都在菜单里勾选），完整样式选中后在
     * 底行深度思考/联网搜索后面亮一个圆形图片图标作已选指示 —— 与菜单项共用同一个 [forceImageGen]。
     */
    forceImageGen: Boolean = false,
    onToggleForceImageGen: () -> Unit = {},
    /**
     * 「联网搜索」状态键（完整样式底行）：与「新规则」页同源同值（每对话覆盖优先，跟随全局兜底），
     * 点按即写每对话覆盖 —— 两处 UI 天然对齐，不存在「这边开那边关」。
     */
    webSearchOn: Boolean = false,
    onToggleWebSearch: () -> Unit = {},
    /**
     * 长按状态快捷键快跳该项设置页（1.0.71，完整样式底行）：
     * 「生成图片」→ 生图模型编辑页；「联网搜索」→ 新规则页（当前对话）。
     * 1.0.75：首页（无对话）**不给长按** —— 两个都是可空，传 null 即不响应长按。
     */
    onLongPressGenImage: (() -> Unit)? = null,
    onLongPressWebSearch: (() -> Unit)? = null,
    /**
     * 深度思考（1.0.74，通用推理控制；1.0.75 语义升级）：点按切换（标准档写双键新规则、
     * 拟人档写角色档案，与各自设置页互通；首页写新对话规则草稿），长按快跳设置页
     * （标准→新规则、拟人→模拟设置；首页不给长按）。模型不支持时灰色不可用。
     */
    deepThinkOn: Boolean = false,
    deepThinkEnabled: Boolean = false,
    onToggleDeepThink: () -> Unit = {},
    onLongPressDeepThink: (() -> Unit)? = null,
    /** 非 null 表示正处于「改写提示词」状态，显示提示条（含取消入口） */
    editingHint: String? = null,
    onCancelEdit: () -> Unit = {},
    quotedContent: String? = null,
    quotedImagePath: String? = null,
    onClearQuote: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val density = LocalDensity.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val (glassProbe, glassDarkness) = rememberGlassBackdropProbe(hazeState, advancedMaterial, isDark)
    val glassText = androidx.compose.ui.graphics.lerp(colors.TextPrimary, Color(0xFFF4F5F7), glassDarkness)
    val glassSecondary = androidx.compose.ui.graphics.lerp(colors.TextSecondary, Color(0xFFE1E4E8), glassDarkness)
    val glassPlaceholder = androidx.compose.ui.graphics.lerp(colors.TextTertiary, Color(0xFFC9CFD6), glassDarkness)
    val glassCursor = androidx.compose.ui.graphics.lerp(colors.Primary, Color(0xFFE9EDF3), glassDarkness)
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
    // 1.0.70 完整样式：全屏输入与标准输入合二为一（无全屏卡片，就地长高 + 底行状态快捷键）
    val isComplete = inputStyle == InputStyle.COMPLETE
    // 完整样式文字区的高度上限：整卡封顶到「现在的全屏输入高度」（屏高 1/3，全屏卡片同款上限）。
    // 卡内下行 + 内边距合计约 70dp，剩下归文字区；小屏兜底 96dp，再矮就不成「两行」了
    val maxTextAreaHeight = ((LocalConfiguration.current.screenHeightDp / 3).dp - 70.dp).coerceAtLeast(96.dp)
    // 输入框形状：完整样式恒为圆角矩形（两行卡）；简洁样式单行=胶囊(50dp)，多行/长文本=圆角矩形(20dp)，
    // 避免换行后左右变两个大「半圆」跑道
    val inputShape = if (isComplete || text.contains('\n') || text.length > 25) RoundedCornerShape(24.dp) else RoundedCornerShape(50.dp)

    // 同步草稿
    LaunchedEffect(text) { onDraftChanged(text) }

    // 发送按钮弹性缩放
    var justSent by remember { mutableStateOf(false) }
    val sendScale by animateFloatAsState(
        targetValue = if (justSent) FreeChatAnimation.SEND_SCALE_PRESSED else 1f,
        animationSpec = FreeChatAnimation.sendPressSpring,
        label = "send_scale"
    )
    // State correctness does not depend on an animation-end callback (including animation scale 0).
    LaunchedEffect(justSent) { if (justSent) { kotlinx.coroutines.delay(120); justSent = false } }

    val emojiAvailable = com.freechat.data.CompanionFeaturePolicy.supportsEmojiInput(isCompanion, narrativeSingleSend)
    var showEmojiPicker by remember { mutableStateOf(false) }
    LaunchedEffect(emojiAvailable) { if (!emojiAvailable) showEmojiPicker = false }
    // 全屏输入卡片：原输入框超过 3 行自动弹出，或长按输入框弹「全屏输入」选项后手动弹出
    var expanded by remember { mutableStateOf(false) }
    // 长按那一下要「吃掉」主输入框自己的选字手势，理由见下面 blockSelection 的注释
    var blockSelection by remember { mutableStateOf(false) }
    val fullscreenFocusRequester = remember { FocusRequester() }
    // 长按菜单里点了「全屏输入」。展开本身由上面那条 LaunchedEffect(expanded) 负责抢焦点，
    // 这里只管把开关拨过去（顺带把主输入框的选区清干净，免得展开后光标位置看着突兀）
    LaunchedEffect(fullscreenTick) {
        if (fullscreenTick > 0) {
            blockSelection = false
            expanded = true
        }
    }
    val mainFocusRequester = remember { FocusRequester() }
    // 内置粘贴（长按菜单里那一项）。系统原生工具条已被 SuppressedTextToolbar 掐掉，
    // 「粘贴」只能走这条路：插在光标处（没聚焦过就续在末尾），插完把焦点和光标还给输入框。
    val clipboardManager = LocalClipboardManager.current
    LaunchedEffect(pasteTick) {
        if (pasteTick > 0) {
            val pasted = clipboardManager.getText()?.text
            if (!pasted.isNullOrEmpty()) {
                val at = if (hasFocus) textSelection.min.coerceIn(0, text.length) else text.length
                val end = if (hasFocus) textSelection.max.coerceIn(at, text.length) else text.length
                text = text.substring(0, at) + pasted + text.substring(end)
                textSelection = TextRange(at + pasted.length)
                textComposition = null
                blockSelection = false
                mainFocusRequester.requestFocus()
            }
        }
    }
    val textMeasurer = rememberTextMeasurer()
    // 原输入框的实际文字宽度（px）：按「原输入框的行数」判断是否弹出，而非全屏宽度或 \n 个数
    var mainFieldWidthPx by remember { mutableIntStateOf(0) }

    val inputTextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = glassText, fontSize = 15.sp, lineHeight = 20.sp,
        fontFamily = LocalChatFontFamily.current
    )
    // 用原输入框宽度测量文字软换行后的真实行数
    val wrappedLineCount = remember(text, mainFieldWidthPx) {
        if (mainFieldWidthPx <= 0) 1
        else textMeasurer.measure(
            text = AnnotatedString(text),
            style = inputTextStyle,
            softWrap = true,
            constraints = Constraints(maxWidth = mainFieldWidthPx)
        ).lineCount
    }

    // 原输入框超过 3 行（进入第 4 行）时自动弹出全屏输入卡片。
    // 完整样式没有全屏卡片 —— 它就地长高（封顶后内部滚动），这条自动弹只属于简洁样式
    LaunchedEffect(wrappedLineCount) {
        if (!isComplete && wrappedLineCount > 3 && !expanded) expanded = true
    }

    // 展开时自动把焦点切到全屏输入框（键盘保持、光标跟上）；缩回由缩回按钮切回主输入框
    LaunchedEffect(expanded) {
        onExpandedChanged(expanded)
        if (expanded) fullscreenFocusRequester.requestFocus()
    }

    // 外部请求聚焦（点气泡改写提示词）：超过 3 行会走上面的自动全屏，交回给全屏框抢焦点，
    // 两个 requestFocus 同时打会互相打断，所以这里按行数分派，不要都调。
    LaunchedEffect(focusTick) {
        if (focusTick <= 0) return@LaunchedEffect
        if (!isComplete && wrappedLineCount > 3) expanded = true
        else mainFocusRequester.requestFocus()
    }

    // ──── 语音录音状态 ────
    val context = LocalContext.current
    val recorder = remember { VoiceLevelRecorder() }
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var recognitionJob by remember { mutableStateOf<Job?>(null) }
    var finishingCapture by remember { mutableStateOf(false) }
    var voiceAnchorHeld by remember { mutableStateOf(false) }
    val recordingChanged by rememberUpdatedState(onRecordingChanged)
    val normalBottomSpace = (keyboardHeightDp + 8.dp).coerceAtLeast(36.dp)
    var recordingBottomSpace by remember { mutableStateOf(normalBottomSpace) }
    // 上滑取消的弧线：记录手指在窗口中的坐标（非 null 表示处于「移出语音键 → 松手取消」状态）
    var cancelFingerWinPos by remember { mutableStateOf<Offset?>(null) }
    // 语音键左上角在窗口中的坐标（用于把手指局部坐标换算成窗口坐标）
    var voiceBtnTopLeftWin by remember { mutableStateOf(Offset.Zero) }
    val microphoneCenter = remember { mutableStateOf(Offset.Zero) }

    fun cancelRecording() {
        recognitionJob?.cancel()
        recorder.cancel()
        isRecording = false
        cancelFingerWinPos = null
        recordingChanged(false)
    }

    // 组件销毁时兜底停止录音并释放（切页/返回/切后台等场景，避免 AudioRecord 泄漏或线程悬挂）
    DisposableEffect(voiceSessionKey) {
        onDispose { cancelRecording() }
    }

    // 切后台（ON_PAUSE）时主动停止录音：Compose 不会因切后台触发 onDispose，
    // 若不主动停，录音线程会继续持有麦克风在后台运行，部分 OEM 会触发强杀/隐私弹窗
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) cancelRecording()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 输入框收起动画：按住录音时 0→1，松手 1→0，非线性"灵动"曲线
    val motionEnabled = LocalMotionEnabled.current
    val collapse = animateFloatAsState(
        targetValue = if (isRecording) 1f else 0f,
        animationSpec = if (!motionEnabled) snap() else if (isRecording)
            FreeChatAnimation.voiceCollapseTween else FreeChatAnimation.voiceRestoreTween,
        label = "voice_collapse"
    )

    // 1.0.71：语音/发送/终止键**实色填充** = 主题对比色（棕=深蓝、蓝=深红、纯白=选中卡片同款深色），
    // 不再三态变色、不再磨砂哑光玻璃。图标按按钮亮度取黑白（暗色主题的浅灰钮配深图标）。
    val iconTint = colors.inputBtnIcon

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* 授予后用户再次长按即可录音 */ }

    val startRecording: () -> Boolean = start@{
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            // 未授权：收起键盘后弹权限框，本次手势直接结束（不进入录音流程，避免手势协程悬空）
            keyboardController?.hide()
            focusManager.clearFocus()
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return@start false
        }
        if (finishingCapture) return@start false
        if (!recorder.start()) return@start false
        // Freeze the microphone's vertical anchor while the IME leaves.
        recognitionJob?.cancel()
        recordingBottomSpace = normalBottomSpace
        voiceAnchorHeld = true
        showEmojiPicker = false
        keyboardController?.hide()
        focusManager.clearFocus()
        isRecording = true
        recordingChanged(true)
        true
    }

    // 松手：cancelled=true 表示手指移出了语音键（丢弃音频），否则识别发送
    val finishRecording: (Boolean) -> Unit = { cancelled ->
        isRecording = false
        cancelFingerWinPos = null
        recordingChanged(false)
        if (cancelled) {
            recorder.cancel()
        } else {
            finishingCapture = true
            recognitionJob = scope.launch {
                try {
                    val pcm = withContext(Dispatchers.IO) { recorder.stop() }
                    finishingCapture = false
                    if (pcm.size >= MIN_PCM_BYTES) {
                        val wav = withContext(Dispatchers.Default) { MiMoAsr.pcmToWav(pcm) }
                        val recognized = onRecognizeVoice(wav)
                        ensureActive()
                        onVoiceInput(recognized ?: "")
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } finally {
                    finishingCapture = false
                }
            }
        }
    }

    val currentStartRecording = rememberUpdatedState(startRecording)
    val currentFinishRecording = rememberUpdatedState(finishRecording)
    LaunchedEffect(isRecording, motionEnabled) {
        if (!isRecording && voiceAnchorHeld) {
            if (motionEnabled) kotlinx.coroutines.delay(FreeChatAnimation.voiceRestoreTween.durationMillis.toLong())
            voiceAnchorHeld = false
        }
    }
    // 保持按住时的坐标；松手后随输入框舒展平滑回到键盘收起后的位置，不跳一下。
    val restoringBottomSpace = animateDpAsState(
        targetValue = if (isRecording) recordingBottomSpace else normalBottomSpace,
        animationSpec = if (!voiceAnchorHeld || isRecording || !motionEnabled) snap() else
            tween(FreeChatAnimation.voiceRestoreTween.durationMillis, easing = FreeChatAnimation.voiceRestoreTween.easing),
        label = "voice_anchor_restore"
    )
    val bottomSpace = if (voiceAnchorHeld) restoringBottomSpace.value else normalBottomSpace

    // 生成中：右侧按键是否为「终止键」。
    //  · 标准模式：一直是终止键（原来的行为）
    //  · 动作演绎 / 剧情补足：也是终止键 —— 单发模式下正在生成时不可能有「再发一条」的需求，
    //    那个位置就该让给终止键，而不是继续显示发送箭头让用户误以为能插话
    //  · 微信聊天：不显示终止键，思考中仍可继续发消息（真人也是这样的）
    val showStopKey = isLoading && (!isCompanion || narrativeSingleSend)

    // 发送动作
    val doSend = {
        if (showStopKey) {
            // 叙事单发：撤回 + 掐断 + 把提示词退回输入框，方便直接改完重发
            if (narrativeSingleSend) onRetract() else onStop()
        } else if (!isEmpty) {
            justSent = true
            onDraftChanged("")  // 先清草稿再发送
            onSend(text.trim())
            text = ""
            textComposition = null
            expanded = false  // 发送后自动退出全屏输入框
            mainFocusRequester.requestFocus()  // 缩回后主输入框获焦，键盘保持不缩回
        }
    }

    // ──── 语音/发送/停止圆钮（1.0.70 抽出）：简洁样式挂在输入框右侧，完整样式叠在卡片底行右端 ────
    // 手势（按住录音 + 上滑取消弧线）、发送弹性缩放全在这里，两种样式共用同一份逻辑。
    // 1.0.71：实色填充主题对比色圆钮（不再磨砂）；[elevated] 只给简洁样式的外置钮留投影，
    // 完整样式的钮贴在卡片表面（去悬浮），且保持「收起时留在原地」——松手取消的按压目标不跟着跑。
    @Composable
    fun VoiceSendButton(modifier: Modifier = Modifier, elevated: Boolean = true) {
        Box(
            modifier = modifier
                // 按压缩放走绘制层（1.0.49）：`Modifier.scale(sendScale)` 是在**组合期**读的，
                // 弹簧那 200ms 里这个按钮的整棵子树每帧重组一次。graphicsLayer 的 lambda 只在
                // 绘制阶段读，缩放值一变只重绘 —— 手感一模一样，重组没了。
                .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                .then(
                    if (elevated) Modifier.softShadow(CircleShape, dy = 2.dp, spread = 5.dp,
                        maxAlpha = if (isDark) 0.08f else 0.035f) else Modifier
                )
                .clip(CircleShape)
                // 实色填充 = 主题对比色（1.0.71 工作单）：全主题、全材质统一，磨砂/描边一并去除
                .background(colors.inputBtnFill)
                .onGloballyPositioned { coords ->
                    voiceBtnTopLeftWin = coords.positionInWindow()
                    microphoneCenter.value = voiceBtnTopLeftWin + Offset(coords.size.width / 2f, coords.size.height / 2f)
                }
                .then(
                    // 单发模式生成中：即使输入框里已经有字，这个键也必须是终止键，
                    // 所以这里先把 showStopKey 排除掉，走下面的 clickable 分支
                    if (isEmpty && !isLoading && !showStopKey) {
                        // 空输入框：按住录音，移出语音键出现取消弧线，松手位置决定发送/取消
                        Modifier.pointerInput(voiceSessionKey) {
                            awaitEachGesture {
                                try {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                                    held.consume()
                                    if (!currentStartRecording.value()) return@awaitEachGesture
                                    var fingerOutside = false
                                    var releasedNormally = false
                                    val slop = with(density) { 8.dp.toPx() }
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        // UP 自身也有坐标：不能沿用上一帧 MOVE，否则快速滑出松手会误发送。
                                        fingerOutside = !VoiceInputMotion.isInsideButton(
                                            change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat(), slop)
                                        if (!change.pressed) {
                                            releasedNormally = change.changedToUp()
                                            change.consume()
                                            break
                                        }
                                        if (change.isConsumed) break
                                        // move 也 consume：让抽屉检测器看到 isConsumed 自取消，锁住侧滑
                                        change.consume()
                                        cancelFingerWinPos = if (fingerOutside) voiceBtnTopLeftWin + change.position else null
                                    }
                                    cancelFingerWinPos = null
                                    if (isRecording) currentFinishRecording.value(!releasedNormally || fingerOutside)
                                } finally {
                                    // 手势被取消（dispose/返回/切后台）时兜底清理，避免录音线程泄漏
                                    cancelFingerWinPos = null
                                    if (isRecording) {
                                        cancelRecording()
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier.clickable(enabled = (showStopKey || !isEmpty) && !isAddingImages) { doSend() }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(targetState = when { showStopKey -> 0; isEmpty -> 1; else -> 2 },
                transitionSpec = { FreeChatAnimation.contentReplacement() }, label = "composer_action_icon") { action ->
                when (action) {
                    0 -> Icon(Icons.Filled.Stop, s.stopAction, tint = iconTint, modifier = Modifier.size(18.dp))
                    1 -> Icon(Icons.Filled.Mic, s.holdToTalk, tint = iconTint, modifier = Modifier.size(20.dp))
                    else -> Icon(Icons.Filled.ArrowUpward, s.sendAction, tint = iconTint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    Box(
        modifier = modifier
            // 用 layout 偏移而非 graphicsLayer，保证 onGloballyPositioned/positionInWindow 跟随滚动位置（弧线锚点不错位）
            .offset { IntOffset(0, offsetYState.value.roundToInt()) }
            .fillMaxWidth()
    ) {
        // matchParentSize 不参与父布局测量：频谱只覆盖屏幕底部，不推动聊天记录或语音键。
        Box(Modifier.matchParentSize()) {
            AnimatedVisibility(
                visible = isRecording,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp).height(144.dp),
                enter = if (motionEnabled) fadeIn(FreeChatAnimation.voiceBarsEnter) +
                    slideInVertically(tween(320, delayMillis = 180, easing = FreeChatAnimation.arrivalEase)) { it / 3 } else EnterTransition.None,
                exit = if (motionEnabled) fadeOut(FreeChatAnimation.voiceBarsExit) else ExitTransition.None
            ) {
                VoiceSpectrumBars(recorder.spectrum, isDark, Modifier.fillMaxSize())
            }
        }
        Column(
            modifier = Modifier
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

        // ──── 改写提示词提示条 ────
        // 必须有这个「取消」：改写态下输入框装的是那条旧提示词，如果用户改主意了直接清空输入框，
        // 之后他打的任何新消息都会顶掉那条旧提示词而不是追加 —— 那是个没有出口的死状态。
        if (editingHint != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.Primary.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Edit, null, tint = colors.Primary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        editingHint,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.Primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        s.cancel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.Primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onCancelEdit)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
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

        // ──── 全屏输入槽（文字超过 3 行自动弹出 / 长按手动弹出，同色凹陷材质，平滑过渡）────
        AnimatedVisibility(
            visible = expanded,
            // 展开用减速入位、收起用加速离场（iOS 那一套）：展开时稳稳铺开，收起时干脆让位
            enter = FreeChatAnimation.expandEnter(),
            exit = FreeChatAnimation.expandExit()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp / 3).dp)
                        .floatingSurface(hazeState, isDark, RoundedCornerShape(24.dp), backdropDarkness = glassDarkness)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.fullscreenInput, style = MaterialTheme.typography.labelMedium, color = glassSecondary)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { expanded = false; mainFocusRequester.requestFocus() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.KeyboardArrowDown, s.collapseInput, tint = glassSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        BasicTextField(
                            value = TextFieldValue(text, textSelection, if (expanded) textComposition else null),
                            onValueChange = { v -> text = v.text; textSelection = v.selection; textComposition = v.composition },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = glassText, fontSize = 15.sp, lineHeight = 20.sp, fontFamily = LocalChatFontFamily.current),
                            cursorBrush = SolidColor(glassCursor),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).verticalScroll(rememberScrollState()).focusRequester(fullscreenFocusRequester),
                            maxLines = Int.MAX_VALUE
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (isComplete) {
            // ──── 完整样式（1.0.70）：全屏输入与标准输入合二为一 ────
            // 一张卡片两行：上行文字（换行自动向上长高，封顶到原全屏输入高度后内部滚动），
            // 下行 [+] [生成图片] [联网搜索] … 语音/发送圆钮叠在右下角。
            // 材质与简洁样式同款：悬浮哑光玻璃。录音时整卡横向收起露波形，圆钮不跟着收（松手要按得到）。
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .voiceDroplet(collapse, microphoneCenter)
                        .glassProbeBounds(glassProbe)
                        .floatingSurface(hazeState, isDark, inputShape, backdropDarkness = glassDarkness)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        // ── 上行：文字输入（换行就地向上长高，封顶内部滚动）──
                        // 「哑巴工具条」同简洁样式：长按不再叠系统原生条，剪贴板走长按菜单的内置「粘贴」
                        CompositionLocalProvider(LocalTextToolbar provides SuppressedTextToolbar) {
                            BasicTextField(
                                value = TextFieldValue(text, textSelection, textComposition),
                                onValueChange = { v ->
                                    text = v.text
                                    // 长按后的选区一律不收（见 blockSelection 的说明）
                                    textSelection = if (blockSelection) textSelection else v.selection
                                    textComposition = v.composition
                                },
                                textStyle = inputTextStyle,
                                cursorBrush = SolidColor(glassCursor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 6.dp, end = 2.dp)
                                    .heightIn(min = 24.dp, max = maxTextAreaHeight)
                                    .verticalScroll(rememberScrollState())
                                    .onGloballyPositioned { coords -> mainFieldWidthPx = coords.size.width }
                                    .focusRequester(mainFocusRequester)
                                    .onFocusChanged { focusState ->
                                        hasFocus = focusState.isFocused
                                        if (focusState.isFocused) {
                                            showEmojiPicker = false
                                            onInputFocused()
                                        }
                                    }
                                    // 长按输入框 → 内置菜单（完整样式下菜单里只剩「粘贴」—— 全屏已合二为一）。
                                    // Initial 趟拦截的理由同简洁样式：Main 趟永远轮不到外层（BasicTextField 先认长按）
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val down = awaitFirstDown(
                                                    requireUnconsumed = false,
                                                    pass = PointerEventPass.Initial
                                                )
                                                blockSelection = false
                                                val longPressed = try {
                                                    withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                                        while (true) {
                                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                            if (!change.pressed) break
                                                        }
                                                        false
                                                    }
                                                } catch (_: PointerEventTimeoutCancellationException) {
                                                    true
                                                }
                                                if (longPressed) {
                                                    blockSelection = true
                                                    onFullscreenMenuRequest()
                                                    while (true) {
                                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                                        event.changes.forEach { it.consume() }
                                                        if (event.changes.none { it.pressed }) break
                                                    }
                                                }
                                            }
                                        }
                                    },
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (text.isEmpty() && pendingImages.isEmpty() && !hasFocus) {
                                            Text(
                                                if (isCompanion) s.companionPlaceholder else s.inputPlaceholder,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                                                color = glassPlaceholder,
                                                modifier = Modifier.padding(top = 1.dp)
                                            )
                                        }
                                        inner()
                                    }
                                },
                                maxLines = Int.MAX_VALUE
                            )
                        }

                        // ── 下行：[+] [状态快捷键] … 语音/发送圆钮 ──
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // + 按钮（与简洁样式同款）
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { onPlusClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = s.attachmentMenu,
                                    tint = glassSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            // 「深度思考」状态键（1.0.74，1.0.75 语义升级）：与新规则页双键互通；
                            // 模型不支持 → 灰色。首页也能点（写「新对话规则草稿」，见 ChatScreen）
                            InputStateChip(
                                label = s.deepThinkingMode,
                                checked = deepThinkOn,
                                onToggle = onToggleDeepThink,
                                colors = colors,
                                onLongPress = onLongPressDeepThink,
                                uncheckedInk = glassText,
                                enabled = deepThinkEnabled
                            ) { checked ->
                                Icon(
                                    Icons.Filled.Psychology,
                                    null,
                                    tint = if (checked) colors.OnPrimary else glassSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            Spacer(Modifier.width(8.dp))
                            // 「联网搜索」状态键：与「新规则」页同源同值（每对话覆盖优先），快捷调节
                            InputStateChip(
                                label = s.webSearch,
                                checked = webSearchOn,
                                onToggle = onToggleWebSearch,
                                colors = colors,
                                onLongPress = onLongPressWebSearch,
                                uncheckedInk = glassText
                            ) { checked ->
                                Icon(
                                    Icons.Filled.Language,
                                    null,
                                    tint = if (checked) colors.OnPrimary else glassSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // 「生成图片」已选指示（1.0.75，仅标准模式）：底行文字键收回「+」菜单后，
                            // 选中时在深度思考/联网搜索后面亮一个**圆形图片图标** —— 不再显示四个字。
                            // 点按=取消（一次性语义不变，发完自动灭）；长按=跳生图模型编辑页（1.0.71 快跳保留）
                            if (forceImageGen && !isCompanion) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.Primary)
                                        .combinedClickable(
                                            onClick = onToggleForceImageGen,
                                            onLongClick = onLongPressGenImage
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Image,
                                        null,
                                        tint = colors.OnPrimary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            // 仅微信聊天有 emoji：叙事两档不显示，也不能打开面板。
                            if (emojiAvailable) {
                                Spacer(Modifier.width(4.dp))
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

                            Spacer(Modifier.weight(1f))
                            // 语音/发送圆钮的悬浮占位：圆钮叠在卡片右下角（见下面 VoiceSendButton）
                            Spacer(Modifier.width(44.dp))
                        }
                    }
                }

                // 语音/发送圆钮：叠在卡片底行右端（内容整体收起时不跟着走，松手取消始终按得到）
                VoiceSendButton(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp)
                        .size(40.dp),
                    elevated = false  // 1.0.71 去悬浮：平贴卡片表面，不再投影
                )
            }
        } else Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ──── 左侧输入框区域（录音时整体向右收缩融合，留白给波形）────
            Box(modifier = Modifier.weight(1f)) {
                // 输入框容器（含 + 按钮、输入框）：录音时 scaleX→0，整个「框框」右滑与语音键融合
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .voiceDroplet(collapse, microphoneCenter)
                        .glassProbeBounds(glassProbe)
                        .floatingSurface(hazeState, isDark, inputShape, backdropDarkness = glassDarkness)
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
                                    // 键盘保持显示，直接弹菜单
                                    onPlusClick()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = s.attachmentMenu,
                                tint = glassSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 输入区
                        // 主输入框外面套一层「哑巴工具条」（见上面 SuppressedTextToolbar 的说明）：
                        // 长按不再叠出系统那条「粘贴/自动填充」，剪贴板走长按菜单里的内置「粘贴」项。
                        CompositionLocalProvider(LocalTextToolbar provides SuppressedTextToolbar) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                                    .heightIn(min = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicTextField(
                                    value = TextFieldValue(if (expanded) "" else text, textSelection, if (expanded) null else textComposition),
                                    onValueChange = { v ->
                                        text = v.text
                                        // 长按之后由「选词」带回来的选区一律不收（见 blockSelection 的说明）：
                                        // 把旧的光标位置原样传回去，选区一直是收拢的，系统选字工具条就没理由出现
                                        textSelection = if (blockSelection) textSelection else v.selection
                                        textComposition = v.composition
                                    },
                                    readOnly = expanded,
                                    textStyle = inputTextStyle,
                                cursorBrush = SolidColor(glassCursor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { coords -> mainFieldWidthPx = coords.size.width }
                                        .focusRequester(mainFocusRequester)
                                        .onFocusChanged { focusState ->
                                            hasFocus = focusState.isFocused
                                            if (focusState.isFocused) {
                                                showEmojiPicker = false
                                                onInputFocused()
                                            }
                                        }
                                        // 长按输入框 → 全屏输入菜单。
                                        //
                                        // **必须在 Initial 这一趟拦**。事件在 Main 趟是「从最里层往上」走的，
                                        // BasicTextField 自己的选字手势会先把长按认下来，挂在外层 Box 上的
                                        // detectTapGestures 永远等不到那一下 —— 这就是之前「长按没反应」的全部原因。
                                        // Initial 是「从外往里」，我们是它外面最近的一圈，第一个拿到。
                                        //
                                        // 检测器自己写、不用 detectTapGestures：那个 API 只能挂 Main 趟，
                                        // 而 AwaitPointerEventScope.withTimeout 是 Compose 给指针作用域准备的
                                        // 计时器（超时抛 PointerEventTimeoutCancellationException），
                                        // 在 Initial 趟里正好用得上。
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val down = awaitFirstDown(
                                                        requireUnconsumed = false,
                                                        pass = PointerEventPass.Initial
                                                    )
                                                    // 每一轮新手势都解除封锁：上一下长按的余波不带到下一次
                                                    blockSelection = false
                                                    val longPressed = try {
                                                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                                            while (true) {
                                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                                // 抬手了：这是普通点击（放光标），不是长按
                                                                if (!change.pressed) break
                                                            }
                                                            false
                                                        }
                                                    } catch (_: PointerEventTimeoutCancellationException) {
                                                        true
                                                    }
                                                    if (longPressed) {
                                                        blockSelection = true
                                                        onFullscreenMenuRequest()
                                                        // 剩下的这段手势一直吃到抬手：拖拽/选字都不该再传给
                                                        // BasicTextField —— 长按在我们这儿的意思是「叫出全屏输入」，
                                                        // 不能再同时选一段词、弹一条复制粘贴工具条压在菜单上
                                                        while (true) {
                                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                                            event.changes.forEach { it.consume() }
                                                            if (event.changes.none { it.pressed }) break
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    decorationBox = { inner ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (expanded) {
                                                Text(
                                                    s.fullscreenInputting,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                                                    color = glassPlaceholder,
                                                    modifier = Modifier.padding(top = 1.dp)
                                                )
                                            } else if (text.isEmpty() && pendingImages.isEmpty() && !hasFocus) {
                                                Text(
                                                    if (isCompanion) s.companionPlaceholder else s.inputPlaceholder,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                                                    color = glassPlaceholder,
                                                    modifier = Modifier.padding(top = 1.dp)
                                                )
                                            }
                                            inner()
                                        }
                                    },
                                    maxLines = 3
                                )
                                // 长按弹出的「全屏输入」菜单不在这儿画：它要的磨砂玻璃得和背景同一个窗口，
                                // 见 onFullscreenMenuRequest 的说明，实际渲染在 ChatScreen 的窗口内浮层里
                            }
                        }

                        // emoji 按钮（仅拟人的微信聊天档，与左侧 + 键对称）
                        if (emojiAvailable) {
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

            }

            Spacer(Modifier.width(8.dp))

            // ──── 语音/发送/停止键（独立，同位置同大小，图标随状态切换）────
            VoiceSendButton(Modifier.size(40.dp))
        }

        // ──── emoji 面板（输入框下方，替代键盘位置；展开/收起与键盘丝滑衔接）────
        AnimatedVisibility(
            visible = emojiAvailable && showEmojiPicker,
            enter = FreeChatAnimation.expandEnter(),
            exit = FreeChatAnimation.expandExit()
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
                                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { if (emojiAvailable) text += em },
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
}

// ──── 完整样式底行的状态快捷键（1.0.70）：☐/☑ 或图标 + 文字，选中 = 主题色实心底（全 App 统一选中态语言）────
@Composable
private fun InputStateChip(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    colors: FreeChatColors,
    /** 长按快跳该项设置页（1.0.71）；null = 不响应长按 */
    onLongPress: (() -> Unit)? = null,
    /** 1.0.74 灰色不可用态（模型不支持 / 无对话可写） */
    enabled: Boolean = true,
    uncheckedInk: Color = colors.TextPrimary,
    icon: @Composable (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .softShadow(RoundedCornerShape(50), dy = 1.dp, spread = 3.dp, maxAlpha = if (checked) 0.045f else 0.025f)
            .clip(RoundedCornerShape(50))
            .background(if (checked) colors.selectedFill else Color.Transparent)
            .then(
                if (checked) Modifier.border(0.75.dp,
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.White.copy(alpha = 0.34f),
                        Color.White.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.12f))), RoundedCornerShape(50))
                else Modifier.border(1.dp, colors.Divider.copy(alpha = 0.6f), RoundedCornerShape(50))
            )
            .alpha(if (enabled) 1f else 0.38f)
            .combinedClickable(enabled = enabled, onClick = onToggle, onLongClick = if (enabled) onLongPress else null)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon(checked)
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (checked) colors.OnPrimary else uncheckedInk
        )
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
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .floatingSurface(hazeState, isDark, RoundedCornerShape(18.dp), fallback = colors.InputBg)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    s.imageCountLabel(images.size, 9),
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
                                contentDescription = s.removeAction,
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
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .floatingSurface(hazeState, isDark, RoundedCornerShape(18.dp), fallback = colors.InputBg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.fileCountLabel(files.size, 3), style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary)
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
                        Icon(Icons.Filled.Close, s.removeAction, tint = colors.TextTertiary, modifier = Modifier.size(14.dp))
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
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .floatingSurface(hazeState, isDark, RoundedCornerShape(18.dp), fallback = colors.InputBg)
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
                    contentDescription = s.quotedImage,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                    targetMaxDim = 256
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.quoteAction, style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary)
                    Spacer(Modifier.height(2.dp))
                    Text(s.imageLabel, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(s.quoteAction, style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary)
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
                Icon(Icons.Filled.Close, s.cancelQuote, tint = colors.TextTertiary, modifier = Modifier.size(16.dp))
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
    val s = LocalStrings.current
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
                s.releaseToCancel,
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
