package com.freechat.ui.screens

import android.net.Uri
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freechat.data.TtsController
import com.freechat.i18n.LocalStrings
import com.freechat.model.ChatMode
import com.freechat.model.DialogueMode
import com.freechat.model.Message
import com.freechat.model.MultiSelectAction
import com.freechat.model.Role
import com.freechat.model.isNarrativeMode
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.components.*
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.LocalGlobalFontFamily
import com.freechat.ui.theme.LocalLatinFontFamily
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.frostedGlass
import com.freechat.ui.theme.seamFeather
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeTint
import com.freechat.viewmodel.ChatViewModel
import com.freechat.util.ShareImageGenerator
import com.freechat.util.ShareSegment
import com.freechat.util.saveBitmapToGallery
import com.freechat.util.shareBitmap
import com.freechat.util.shareMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.theme.pageHeaderBackground
import com.freechat.ui.theme.liquidOpaqueBackground
import com.freechat.ui.theme.liquidSourceBackdrop
import com.freechat.ui.theme.LocalLiquidMode

// ──────────── 底部留白单一数据源 ────────────
//	contentPadding.bottom = ChatListRestBottomPaddingDp
//	                      + (有引用/图片卡片 ? ChatCardStripExtraPaddingDp : 0.dp)
//	                      + (键盘可见 ? chatKeyboardDp : 0.dp)
//	                      + (全屏输入展开 ? 实测增量 : 0.dp)
// 贴底判定（LazyListState.isStuckToBottom）量的是「末条 item 底边 → 列表容器底边」的距离 d，
// d 对 padding 变化免疫（见 isStuckToBottom 的注释），所以调用点必须显式说明「问的是哪一帧的底部」：
//   · 键盘 Effect → 键盘增量取**变化前**的值（本次新增的键盘留白不算底部）
//   · 卡片 Effect → 卡片条增量取**变化前**的值（卡片条正是本次新增的量）
//   · 全屏输入 Effect → 全屏增量取**变化前**的值（同上，它就是本次新增的量）
//   · 输入框聚焦 → 四项之和的**当前**值（问的就是「此刻是否停在底部」）
// ⚠️ 硬约束：以后若给 contentPadding.bottom 增加第五个来源（新悬浮条 / 录音条 / TTS 条…），
//    必须同时决定它在上面几个调用点算「变化前」还是「变化后」：
//    锚点偏小（一律按「变化前」）→ 判定偏松，允许的误判窗口正好等于该项增量（上翻一点就被拽回底部）；
//    锚点偏大（新来源一律按「变化后」）→ 判定偏严，本次该跟随的跟随会被整个丢掉（内容被挡住）。
//    两个方向都是静默失效，不会崩、只会偶发乱滚或不动。
private val ChatListRestBottomPaddingDp = 116.dp   // 静止态底部留白
private val ChatCardStripExtraPaddingDp = 64.dp    // 引用 / 图片卡片条
private val ChatBottomToleranceDp = 2.dp           // dp→px 舍入容差，不可取 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    isDark: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenNewRules: () -> Unit = {},
    onNewChatReveal: ((Rect) -> Unit)? = null,
    revealTrigger: Int = 0,
    onRevealComplete: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onOpenCharacterSetup: () -> Unit = {},
    onOpenAsrEditor: () -> Unit = {},
    hazeState: HazeState,
    isDrawerOpen: Boolean = false
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    var showPlusMenu by remember { mutableStateOf(false) }
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    // 流式三兄弟（思考过程 / 正文 / 用时）不再在顶层 collect：
    // 这里是整屏最大的重组作用域，而流式输出时正文是**每来一个字**变一次 ——
    // 原来每字都会作废整个 ChatScreen（LazyColumn 的 item 表被重跑一遍、每个可见气泡的内容 lambda 都是新实例，
    // 于是长对话里「一个字」触发的是所有可见气泡的重组）。
    // 现在顶层只读两个 derivedStateOf 出来的布尔量（只有「空 ↔ 非空」翻转时才通知），
    // 真正的字符串下沉到**用它的那个列表项**里读 —— 每字只重组正在长的那一条气泡。
    val liveReasoningState = viewModel.liveReasoning.collectAsState()
    val liveContentState = viewModel.liveContent.collectAsState()
    val hasLiveContent by remember { derivedStateOf { liveContentState.value.isNotEmpty() } }
    val hasLiveReasoning by remember { derivedStateOf { liveReasoningState.value.isNotEmpty() } }
    val isGeneratingImage by viewModel.isGeneratingImage.collectAsState()
    val pendingImages by viewModel.pendingImages.collectAsState()
    val isAddingImages by viewModel.isAddingImages.collectAsState()
    val quotedMessage by viewModel.quotedMessage.collectAsState()
    val pendingFiles by viewModel.pendingFiles.collectAsState()
    val isAddingFiles by viewModel.isAddingFiles.collectAsState()
    val ttsAutoPlay by viewModel.ttsAutoPlay.collectAsState()
    // 按**这条对话**算出来的「显示思考过程」：新规则里的覆盖优先，没设才跟随全局。
    // 不能用全局那个 showThinking —— 它和「新规则」冲突时说了不算（1.0.51 修）。
    val showThinking by viewModel.effectiveShowThinking.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val currentCharacter by viewModel.currentCharacter.collectAsState()
    val scrollTick by viewModel.scrollRequestTick.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showNoAsrDialog by remember { mutableStateOf(false) }
    // 搜索跳转：目标消息高亮微闪 + 关键词标红
    var highlightMsgId by remember { mutableStateOf<String?>(null) }
    var highlightKeyword by remember { mutableStateOf<String?>(null) }
    val flashAlpha = remember { Animatable(0f) }

    // ===== 多选：状态在 VM（MainActivity 的标题也要读），这里只管交互与批量动作 =====
    val multiSelect by viewModel.multiSelect.collectAsState()
    val context = LocalContext.current

    // 联网失败要说出来：额度用完/网络断了以前是静默的，用户只看到「AI 答得不对」，
    // 完全不知道是没搜到。这里一次性提示完就清空，不重复打扰。
    val webSearchNotice by viewModel.webSearchNotice.collectAsState()
    LaunchedEffect(webSearchNotice) {
        if (webSearchNotice.isNotBlank()) {
            Toast.makeText(context, webSearchNotice, Toast.LENGTH_LONG).show()
            viewModel.clearWebSearchNotice()
        }
    }
    // 批量分享的本地状态（Kotlin 局部声明只对之后可见，必须声明在引用它们的函数之前）
    var showMultiShareMenu by remember { mutableStateOf(false) }
    var showMultiDeleteConfirm by remember { mutableStateOf(false) }
    var multiPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    // 长图是 1080×总高 的 ARGB_8888 位图，ShareImageGenerator.MAX_H = 20000px（1080×20000×4 ≈ 86MB，
    // 这就是不能再调高的原因）。可用正文高 = 20000 − 顶部 212 − 页脚 338 = 19450px。
    // 字数上限按【最坏排版】反推：用户气泡内宽 664px、正文 40px 全角 → 16 字/行、行高 74px ≈ 4.63px/字，
    // 再加每条气泡内边距 52px、条间距 104px。20 条时 4.63×C + 20×52 + 19×104 + 550 ≤ 19450 → C ≤ 3552。
    // 取 3000 留出标题/列表/段落间距余量：**守卫放行的选择必须一定能渲染出来**，
    // 否则用户会拿到「允许选 → 生成失败」的矛盾结果（旧值 6000 在中文排版下必然超顶）。
    val maxMultiShare = 20
    val maxMultiShareChars = 3000

    // 分享一律**去掉生成失败的那几条**：失败提示（"请求失败：连接超时"）不是内容，
    // 发出去对方只会莫名其妙；选中的全是失败消息时，下面那两个函数会按"没东西可发"提前退出
    fun multiSelectedMessages(): List<Message> = viewModel.selectedMessagesInOrder().filterNot { it.failed }

    fun runMultiShareImage() {
        showMultiShareMenu = false
        val msgs = multiSelectedMessages()
        if (msgs.isEmpty()) { viewModel.exitMultiSelect(); return }
        val totalChars = msgs.sumOf { it.content.length }
        if (msgs.size > maxMultiShare || totalChars > maxMultiShareChars) {
            Toast.makeText(context, s.shareTooMuch, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val segments = msgs.map { m ->
                ShareSegment(text = m.content.ifBlank { s.imageTag }, isUser = m.role == Role.USER)
            }
            val bmp = withContext(Dispatchers.IO) {
                ShareImageGenerator.generate(context, segments, colors.Primary.toArgb())
            }
            if (bmp == null) {
                // 不退出多选：与上面「选中的内容太多」那条守卫一致，保住用户的勾选，
                // 去掉几条就能直接重试，而不是从头再一条条勾
                Toast.makeText(context, s.shareImageTooLarge, Toast.LENGTH_SHORT).show()
            } else {
                multiPreviewBitmap = bmp
            }
        }
    }

    fun runMultiShareMarkdown() {
        showMultiShareMenu = false
        val msgs = multiSelectedMessages()
        if (msgs.isEmpty()) { viewModel.exitMultiSelect(); return }
        val md = msgs.joinToString("\n\n---\n\n") { m -> m.content.ifBlank { s.imageTag } }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { context.shareMarkdown(md) }
            if (!ok) Toast.makeText(context, s.shareFailed, Toast.LENGTH_SHORT).show()
            viewModel.exitMultiSelect()
        }
    }

    // 右上角那个文字按钮：文案随进入时的动作变，点下去执行批量动作
    val onExecuteMultiAction: () -> Unit = {
        when (multiSelect.action) {
            MultiSelectAction.FAVORITE -> viewModel.applyFavoriteToSelection()
            MultiSelectAction.DELETE -> showMultiDeleteConfirm = true
            MultiSelectAction.SHARE -> showMultiShareMenu = true
            null -> {}
        }
    }

    // 恢复到该对话上次的滚动位置（跨页面切换返回后不回顶部）
    val savedInitial = viewModel.currentConversationId.value?.let { viewModel.chatScrollPositions.value[it] }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedInitial?.first ?: 0,
        initialFirstVisibleItemScrollOffset = savedInitial?.second ?: 0
    )
    val greeting = remember(s.localeCode) { viewModel.generateGreeting(s) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    // 拟人模式：时间居中显示——首条消息与间隔 >5min 的消息前插入时间分割线
    val companionMode = currentMode == ChatMode.COMPANION
    // 对话模式：currentCharacter 在个别入口可能还是未迁移的原始对象，再过一次 normalized() 兜底
    // （1.0.28 前的老档案只写了 plotSimulation，dialogueMode 还是默认值）
    val dialogueMode = currentCharacter?.normalized()?.dialogueMode ?: DialogueMode.WECHAT
    // 剧情补足：AI 回复不套气泡框，以纯文本流呈现（用户消息仍有气泡，用于区分辨别）
    val textFlowAi = dialogueMode == DialogueMode.PLOT
    // 二次编辑（改写最后一条用户消息 → 作废其后的回复重新生成）：
    // 动作演绎与剧情补足都提供；微信聊天档暂不提供，留给之后的「撤回」
    // 可改写的档位：标准模式 + 动作演绎/剧情补足，**微信聊天档除外**。
    // 微信档一次接一次地连续发消息，单拎某一条出来改写没有意义（而且那条往往已经带着
    // 一整轮的上下文中和过了）；另外三档一次只发一条，改哪条最有用是明确的。
    val editEnabled = currentMode != com.freechat.model.ChatMode.COMPANION || dialogueMode != DialogueMode.WECHAT
    // 两个 indexOfLast 是 O(n) 全表扫。它们原来每次重组都跑一遍，而流式输出期间
    // ChatScreen 是**每个字**重组一次 —— 长对话里这就是每字 O(n)。
    // 结果只取决于 messages，用 remember 钉住（列表结构相同则 equals 相等，不会重算）。
    val lastUserMessageIndex = remember(messages) {
        messages.indexOfLast { it.role == com.freechat.model.Role.USER }
    }
    // 「重新生成」只给最后一条 AI 回复，历史回复没有这个按钮：
    // 往前翻几条就点一次重生成、把后面整段对话都变成废纸，是典型的误操作来源。
    val lastAssistantIndex = remember(messages) {
        messages.indexOfLast { it.role == com.freechat.model.Role.ASSISTANT }
    }
    // 剧情编辑：改写最后一条用户消息
    // 改写提示词：点最后一条气泡即进入编辑态 —— 原文灌进输入框、键盘直接弹出，
    // 不再弹一个编辑对话框。编辑态下其余消息整体压暗，视觉焦点只留在那一条上。
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    // 进入编辑态之前的草稿：取消编辑时要原样还回去，不能把用户原本写了一半的东西冲掉
    var draftBeforeEdit by remember { mutableStateOf("") }
    var inputFocusTick by remember { mutableIntStateOf(0) }
    // 长按输入框叫出来的「全屏输入」菜单。菜单画在这一层而不是输入框里 ——
    // 磨砂玻璃要糊住背后的聊天内容，就得跟聊天内容待在同一个窗口（Popup 是独立窗口，糊不到）。
    var showFullscreenMenu by remember { mutableStateOf(false) }
    // 点了菜单里的「全屏输入」→ 自增一下，输入框那边看到就展开（与 inputFocusTick 同一套写法）
    var inputFullscreenTick by remember { mutableIntStateOf(0) }
    // 点了菜单里的「粘贴」→ 同样是自增信号，真正的插入在 ChatInput 里做（文字和光标都住那边）
    var inputPasteTick by remember { mutableIntStateOf(0) }
    // 菜单弹出来的那一刻剪贴板里有没有文字：没有就把「粘贴」置灰，别让用户点了没反应。
    // 只在弹菜单时读一次 —— 安卓 10 往上后台读剪贴板要挨系统白眼（返回空），
    // 而菜单是用户自己点出来的，这会儿本应用正握有焦点，读得到。
    var clipboardHasText by remember { mutableStateOf(false) }
    LaunchedEffect(showFullscreenMenu) {
        if (showFullscreenMenu) {
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            clipboardHasText = runCatching {
                cm?.primaryClip?.takeIf { it.itemCount > 0 }?.let { clip ->
                    clip.getItemAt(0).coerceToText(context).toString().isNotBlank()
                } ?: false
            }.getOrDefault(false)
        }
    }
    // 入场动画只播一次的凭据：播过的消息 id 记在这里。
    // 没有它的话，懒加载列表把消息滑出屏幕再滑回来就会重播一遍动画——一眼就假。
    val entrancePlayed = remember { mutableSetOf<String>() }
    val timeGapMs = 5 * 60 * 1000L
    val displayItems = remember(messages, companionMode) {
        buildList {
            messages.forEachIndexed { i, msg ->
                if (companionMode && (i == 0 || msg.timestamp - messages[i - 1].timestamp > timeGapMs)) {
                    add(TimeItem(msg.timestamp))
                }
                add(MsgItem(i, msg))
            }
        }
    }

    val currentConvId by viewModel.currentConversationId.collectAsState()
    // 内置对话（「Claude风格助理」）：它是一条**已经存在的对话**，不是「新建对话」——
    // 空的时候给的是一张白页，不是那句「你好，我是 FreeChat」的问候语（那会让它看起来像刚新建的）
    val conversations by viewModel.conversations.collectAsState()
    val isBuiltInConversation = conversations.any { it.id == currentConvId && it.builtInAssistant.isNotBlank() }
    // 输入框隐藏偏移：实测输入框真实高度（多行时更高，避免顶部漏出）；单行兜底 88dp
    var inputBoxMaxPx by remember { mutableFloatStateOf(with(density) { 88.dp.toPx() }) }

    // ──── 新对话过渡动画 ────
    val revealAnim = remember { Animatable(0f) }
    var revealPhase by remember { mutableIntStateOf(0) }
    LaunchedEffect(revealTrigger) {
        if (revealTrigger > 0 && messages.isNotEmpty()) {
            revealPhase = 1
            revealAnim.snapTo(0f)
            revealAnim.animateTo(1f, tween(220, easing = FreeChatAnimation.iosEaseOut))
            onNewChat()
            revealPhase = 2
            revealAnim.animateTo(0f, tween(250, easing = FreeChatAnimation.iosEaseIn))
            revealPhase = 0
            onRevealComplete()  // 重置触发信号，避免返回 Chat 时 LaunchedEffect 重放导致误切新对话
        }
    }

    // IME
    val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val keyboardVisible = imeBottomDp > 0.dp
    // 侧滑页打开时 Chat 页不应响应键盘：侧滑页搜索框唤出键盘，不应滚动/上移聊天文字流
    val chatKeyboardDp = if (isDrawerOpen) 0.dp else imeBottomDp
    val chatKeyboardVisible = chatKeyboardDp > 0.dp
    LaunchedEffect(keyboardVisible, imeBottomDp) {
        Log.d("FreeChat", "IME debug: bottom=${imeBottomDp.value} visible=$keyboardVisible")
    }

    // 贴底判定的布局常量：三项都是纯常量/纯派生，不随重组漂移，effect 捕获后无过期风险
    val restBottomPaddingPx = with(density) { ChatListRestBottomPaddingDp.toPx() }.roundToInt()
    val bottomTolerancePx = with(density) { ChatBottomToleranceDp.toPx() }.roundToInt()
    val cardStripPaddingPx = with(density) { ChatCardStripExtraPaddingDp.toPx() }.roundToInt()
    val hasCardStrip = pendingImages.isNotEmpty() || quotedMessage != null
    // 与 LazyColumn contentPadding.bottom 逐项等价（键盘那项同样只认 IME 可见、不认 isDrawerOpen，
    // 因为列表留白就是这么算的；isDrawerOpen 只决定 Chat 页要不要响应键盘滚动）
    val keyboardPaddingPx =
        if (chatKeyboardVisible) with(density) { chatKeyboardDp.toPx() }.roundToInt() else 0
    // 全屏输入卡片展开后输入框会长高一截，这一截必须补进列表底部留白，
    // 否则展开时最后一条消息被卡片压住、怎么滑都露不出来。
    // 高度**量出来**而不是写死：卡片高度在「内容 80dp」到「屏幕 1/3」之间浮动，
    // 写死必然有一头对不上（写小了下不去，写大了底部空一大块）。
    // 量的是「当前输入框高度 − 收起时的高度」，差值就是这一截增量。
    var composerExpanded by remember { mutableStateOf(false) }
    var composerHeightPx by remember { mutableIntStateOf(0) }
    var composerCollapsedHeightPx by remember { mutableIntStateOf(0) }
    val fullscreenExtraPaddingPx =
        if (composerExpanded) (composerHeightPx - composerCollapsedHeightPx).coerceAtLeast(0) else 0
    val currentBottomPaddingPx =
        restBottomPaddingPx + keyboardPaddingPx + (if (hasCardStrip) cardStripPaddingPx else 0) +
            fullscreenExtraPaddingPx

    // 输入框是否因上滑回翻旧消息而隐藏（滑动方向触发，非按比例跟随）
    var inputHidden by remember { mutableStateOf(false) }
    val inputOffset = animateFloatAsState(
        targetValue = if (inputHidden && !keyboardVisible) inputBoxMaxPx else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "input_offset"
    )
    val greetingProgressState = remember { mutableFloatStateOf(0f) }

    SideEffect {
        greetingProgressState.floatValue = (imeBottomDp.value / 280f).coerceIn(0f, 1f)
    }

    // 录音中：锁定列表滚动 + 侧滑（侧滑由 ChatInput 手势 consume move 自锁定）
    var isRecording by remember { mutableStateOf(false) }

    // 键盘弹出：仅当用户本来就停在静息底线（末条消息底边贴着 116dp 留白线）时，文字流才跟随
    // 键盘上移实时滚到底（scrollToItem 瞬时跟随 ime 变化，与键盘/输入框并行、丝滑无停顿）。
    // 上翻阅读历史时不滚，阅读位置与 chatScrollPositions 存档都不被打断。
    // 不用 !canScrollForward：键盘弹出那一帧 contentPadding.bottom 变大，它当场翻 true，
    // 会把贴底用户判成「不贴底」——这正是本次要修的病根。
    // 锚点必须是**弹出前**的留白：本 Effect 在留白已经变大之后才跑，那时的几何量与
    // 「键盘开着、用户在键盘高度内上翻阅读」完全相同（同一状态、两种意图，几何量分不出来），
    // 拿新留白当锚点就会把后者也判成贴底，把正在阅读的用户往下拽一个键盘高度。
    // 仅 Chat 页响应（isDrawerOpen 时 chatKeyboardDp=0，侧滑页搜索框唤出键盘不触发滚动）
    // 初值 = 首帧的当前值，不是 0：Chat 页可能是「挂着引用卡片/键盘已弹出」时重建的
    // （从设置页返回、切对话回来），那时首帧就已经带着留白，当 0 处理会把恢复的阅读位置误判成贴底。
    var keyboardPaddingBeforePx by remember { mutableIntStateOf(keyboardPaddingPx) }
    LaunchedEffect(chatKeyboardDp) {
        val prevKeyboardPx = keyboardPaddingBeforePx
        keyboardPaddingBeforePx = keyboardPaddingPx
        if (chatKeyboardDp > 0.dp && messages.isNotEmpty()) {
            val lastIdx = listState.layoutInfo.totalItemsCount - 1
            // 卡片条在本事件里没变，属于「弹出前就有的留白」，照常计入
            val anchorPx =
                restBottomPaddingPx + (if (hasCardStrip) cardStripPaddingPx else 0) + prevKeyboardPx
            if (lastIdx >= 0 && listState.isStuckToBottom(anchorPx, bottomTolerancePx)) {
                listState.scrollToItem(lastIdx, Int.MAX_VALUE)
            }
        }
    }

    // 引用/图片卡片出现时，仅当用户本来就停在静息底线才自动滚到底，保证卡片不遮挡最底部文字流；
    // 上翻阅读时保持原位不动（卡片是浮层，不改变列表内容位置）。
    // 锚点 = 卡片出现前的留白 = 静息 + 键盘 + **上一次**的卡片条（卡片条增量本身是本次事件新增的，
    // 不能计入，否则「键盘/卡片条高度内上翻阅读」会被判成贴底）。
    // 初值同上：首帧就挂着卡片时，「变化前的卡片条」= 卡片条本身（它是既有状态，不是本次新增的）
    var cardStripBeforePx by remember { mutableIntStateOf(if (hasCardStrip) cardStripPaddingPx else 0) }
    LaunchedEffect(quotedMessage, pendingImages.size) {
        val prevCardStripPx = cardStripBeforePx
        cardStripBeforePx = if (hasCardStrip) cardStripPaddingPx else 0
        if (messages.isNotEmpty() && (quotedMessage != null || pendingImages.isNotEmpty())) {
            val lastIdx = listState.layoutInfo.totalItemsCount - 1
            val anchorPx = restBottomPaddingPx + keyboardPaddingPx + prevCardStripPx
            if (lastIdx >= 0 && listState.isStuckToBottom(anchorPx, bottomTolerancePx)) {
                listState.scrollToItem(lastIdx, Int.MAX_VALUE)
            }
        }
    }

    // 全屏输入卡片展开/收起：与「引用卡片」同一个道理 —— 只有用户本来就停在底部才跟随滚到底，
    // 上翻阅读时不动。锚点同样取**展开前**的留白（这一截增量正是本次事件新增的，不能计入）。
    // 收起（expanded=false）不滚：留白变小不会挡住任何东西，强行滚一下反而会把正在读的位置拽走。
    var fullscreenBeforePx by remember { mutableIntStateOf(0) }
    LaunchedEffect(composerExpanded, fullscreenExtraPaddingPx) {
        val prevFullscreenPx = fullscreenBeforePx
        fullscreenBeforePx = fullscreenExtraPaddingPx
        if (composerExpanded && messages.isNotEmpty()) {
            val lastIdx = listState.layoutInfo.totalItemsCount - 1
            val anchorPx =
                restBottomPaddingPx + keyboardPaddingPx +
                    (if (hasCardStrip) cardStripPaddingPx else 0) + prevFullscreenPx
            if (lastIdx >= 0 && listState.isStuckToBottom(anchorPx, bottomTolerancePx)) {
                listState.scrollToItem(lastIdx, Int.MAX_VALUE)
            }
        }
    }

    var prevConvId by remember { mutableStateOf<String?>(null) }
    // 用户是否停在底部：AI 回复仅当用户本就停在底部才跟随滚到底，上翻阅读时不打断位置
    // ⚠️ 禁止用 isStuckToBottom 替换它：两者问的不是同一件事。
    //    atBottom =「此刻还能不能继续往下滚」（live 的 !canScrollForward），问的是「用户是否钉在最新内容上」——
    //    新内容追加到视口下方时它当场翻 true，这正是「该不该跟随新内容」需要的信号；
    //    isStuckToBottom =「用户此刻是否停在底部留白线上」，是个纯位置量：内容变长后它反而变 false
    //    （d 随内容变长而变小），拿它做新消息跟随会让流式回复在第一段之后就不再跟随。
    val atBottom = remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }
            .collect { canScroll -> atBottom.value = !canScroll }
    }

    // 流式跟随（1.0.51）：思考过程与正文都是**逐字**长出来的，列表得跟着往下走 ——
    // 否则新字全落在屏幕外，「实时看着它思考」就是一句空话（原来只有整条消息落地才滚一次，
    // 一轮里长出来的内容不跟）。两个讲究：
    //  · 流式状态放在 snapshotFlow 里读：顶层 collect 流式三兄弟会让整屏每来一个字重组一次
    //    （见文件上方那段注释），这里只订阅、不参与组合，零重组；
    //  · 复用 inputHidden 这个**既有**信号判断「用户正在回翻历史」：生成中他一上滑就立刻停跟，
    //    滑回底部它自己复位、跟随随之恢复 —— 绝不在人阅读时把人拽回底部。
    //  · isScrollInProgress：手指正按着屏幕（拖拽/惯性）时一律让位，不跟用户抢滚动权。
    LaunchedEffect(isLoading) {
        if (!isLoading) return@LaunchedEffect
        snapshotFlow { liveReasoningState.value.length + liveContentState.value.length }
            .collect {
                if (!inputHidden && !listState.isScrollInProgress) {
                    val lastIdx = listState.layoutInfo.totalItemsCount - 1
                    if (lastIdx >= 0) listState.scrollToItem(lastIdx, Int.MAX_VALUE)
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            currentConvId?.takeIf { messages.isNotEmpty() }?.let { id -> viewModel.saveChatScrollPosition(id, index, offset) }
        }
    }

    LaunchedEffect(listState.canScrollForward, keyboardVisible) {
        if (!keyboardVisible && !listState.canScrollForward && messages.isNotEmpty()) {
            inputHidden = false
        }
    }

    LaunchedEffect(currentConvId, messages.size, scrollTick) {
        val isConvSwitch = currentConvId != prevConvId
        prevConvId = currentConvId
        inputHidden = false
        if (messages.isEmpty()) return@LaunchedEffect

        val lastDisplayIdx = displayItems.lastIndex
        // 从搜索结果跳转：滚到目标消息（屏幕中上位置），随后微闪两下示意
        val targetId = viewModel.pendingScrollTarget.value
        if (targetId != null) {
            val targetIndex = displayItems.indexOfFirst { it is MsgItem && it.msg.id == targetId }
            if (targetIndex >= 0) {
                val centerOffset = (screenHeightPx * 0.4f).roundToInt()
                listState.scrollToItem(targetIndex, -centerOffset)
                highlightMsgId = targetId
                highlightKeyword = searchQuery.trim().ifBlank { null }
                flashAlpha.snapTo(0f)
                // 亮起 → 停一拍（让用户看清标红关键词）→ 淡出
                flashAlpha.animateTo(1f, tween(200, easing = FreeChatAnimation.iosEaseOut))
                delay(700)
                flashAlpha.animateTo(0f, tween(520, easing = FreeChatAnimation.iosEaseOut))
                highlightMsgId = null
                highlightKeyword = null
            }
            viewModel.consumeScrollTarget()
        } else if (isConvSwitch) {
            val saved = currentConvId?.let { viewModel.chatScrollPositions.value[it] }
            if (saved != null && saved.first in 0..lastDisplayIdx) {
                listState.scrollToItem(saved.first, saved.second)
            } else {
                listState.scrollToItem(lastDisplayIdx, 1_000_000)  // 首次进入默认到最新位置（底部）
            }
        } else if (lastDisplayIdx >= 0 && atBottom.value) {
            // 仅当用户本就停在底部时才跟随新消息滚到底；上翻阅读时不打断位置
            listState.scrollToItem(lastDisplayIdx, 1_000_000)
        }
    }

    // 切换/新建对话时暂停朗读（保留进度，回到原对话可继续）
    LaunchedEffect(currentConvId) {
        TtsController.pause()
        // 换对话就退出多选：选中的 id 属于旧对话，留着会「已选择 3 项」却一条都没高亮
        viewModel.exitMultiSelect()
    }

    // 消息列表变动后剔除失效 id（regenerate 会换掉消息 id；剔空自动退出，避免标题数字虚高）
    LaunchedEffect(messages) {
        viewModel.pruneMultiSelect()
    }

    // 滑动方向触发：上滑（回翻旧消息）隐藏输入框，下滑（看新消息）显示。
    // 加累计位移阈值：小范围抖动不触发，明确大幅滑动才切换，避免"过灵敏"。
    LaunchedEffect(listState) {
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        var accumulatedUp = 0
        var accumulatedDown = 0
        val thresholdPx = with(density) { 64.dp.toPx() }.roundToInt()
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                // 有引用/图片卡片时，输入框固定显示、不触发隐藏动画
                if (pendingImages.isNotEmpty() || quotedMessage != null) {
                    inputHidden = false
                    prevIndex = index
                    prevOffset = offset
                    return@collect
                }
                val goingUp = index < prevIndex || (index == prevIndex && offset < prevOffset)
                val goingDown = index > prevIndex || (index == prevIndex && offset > prevOffset)
                if (index != prevIndex) {
                    // 跨过一个 item = 明确的大幅滑动，直接判定
                    if (goingUp) inputHidden = true
                    else if (goingDown) inputHidden = false
                    accumulatedUp = 0
                    accumulatedDown = 0
                } else {
                    val delta = offset - prevOffset
                    if (delta < 0) {
                        accumulatedUp += -delta
                        accumulatedDown = 0
                        if (accumulatedUp > thresholdPx) inputHidden = true
                    } else if (delta > 0) {
                        accumulatedDown += delta
                        accumulatedUp = 0
                        if (accumulatedDown > thresholdPx) inputHidden = false
                    }
                }
                if (!listState.canScrollForward) {  // 回到底部 → 显示
                    inputHidden = false
                    accumulatedUp = 0
                    accumulatedDown = 0
                }
                prevIndex = index
                prevOffset = offset
            }
    }

    val chatNewChatRect = remember { mutableStateOf<Rect?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addPendingImages(uris)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addPendingFile(uris)
        }
    }

    // ──── 自动朗读：AI 回复完成瞬间（isLoading true→false）朗读最后一条 AI 消息 ────
    var wasLoading by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading, messages.lastOrNull()?.id, ttsAutoPlay) {
        if (ttsAutoPlay && wasLoading && !isLoading) {
            val last = messages.lastOrNull()
            if (last != null && last.role == com.freechat.model.Role.ASSISTANT) {
                viewModel.speakMessage(last.id, last.content)
            }
        }
        wasLoading = isLoading
    }

    // ──── 草稿 ────
    val draftText = viewModel.getDraft(currentConvId)

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = HazeSpec.TitleBarAreaDp
    // 顶部模糊的渐变终点（px）：endY 默认是无穷大，会导致 easing 曲线只用到 t≈0 一小段、模糊「秒没」。
    // 带高与 endY 同源（HazeSpec.topBandHeightDp），侧滑页调同一函数，两侧规格才可能逐像素一致。
    val topBandHeight = HazeSpec.topBandHeightDp(statusBarHeightDp)
    val topBarHeightPx = with(density) { topBandHeight.toPx() }
    // 底部带高 = 底部渐变 endY，禁止再出现第二个数字（历史 bug：带子写死 88dp、endY 用 96dp）
    val bottomBandHeight = HazeSpec.bottomBandHeightDp()
    val bottomBarHeightPx = with(density) { bottomBandHeight.toPx() }
    // 接缝羽化宽度（px）：模糊带贴缝一侧的强度在这段距离内衰减到 0
    val seamFeatherPx = with(density) { HazeSpec.SeamFeatherDp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pageBackground(colors.Background)
    ) {
        // ===== 底部渐隐区的「采样垫底」：垫在内容源节点下面的一小块不透明流光副本 =====
        // 炫彩下页面源是透明的 → Haze 抓到的样本只有字没有底 → 磨完盖不住下面清晰的正文，
        // 底部那条渐进模糊带就成了「墨汁晕开、但内容还读得出来」。这块让样本自己带上底。
        // 详细原理与 zIndex 的讲究见 liquidSourceBackdrop 的注释。它跟着输入框一起上下移
        // （必须与下面那条带子同一个 offset，否则两者错位）。
        if (advancedMaterial && LocalLiquidMode.current) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomBandHeight)
                    .offset { IntOffset(0, inputOffset.value.roundToInt()) }
                    .hazeSource(state = hazeState, zIndex = -1f)
                    .liquidSourceBackdrop(colors.Background)
            )
        }

        // ===== 内容层：高级材质下沉浸到状态栏之下 =====
        // 问候语只在标准问答模式显示；拟人模式（含新建角色 0 消息）走空聊天列表，等待用户发第一条
        // 模糊源必须是**同一个节点**：WelcomeGreeting 与 LazyColumn 原来各挂一个 hazeSource，
        // 进对话时分支一换，挂源的那个节点连同那份录制一起被换掉 —— 顶部模糊带就这么不见了，
        // 动一下（滚一下）才回来。恒定的一层 Box 挂源，两个分支在它里面换，源就不会断。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
        ) {
            if (messages.isEmpty() && !companionMode && !isBuiltInConversation) {
                WelcomeGreeting(
                    greeting = greeting,
                    colors = colors,
                    keyboardProgressState = greetingProgressState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = statusBarHeightDp + titleBarAreaDp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    state = listState,
                    userScrollEnabled = !isRecording,
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = statusBarHeightDp + titleBarAreaDp + 8.dp,
                        // 与原表达式逐项等价（只是把 116 / 64 / 键盘 / 全屏输入四项收敛到单一数据源，见文件顶部常量注释）
                        bottom = ChatListRestBottomPaddingDp +
                            (if (chatKeyboardVisible) chatKeyboardDp else 0.dp) +
                            (if (pendingImages.isNotEmpty() || quotedMessage != null) ChatCardStripExtraPaddingDp else 0.dp) +
                            with(density) { fullscreenExtraPaddingPx.toDp() }
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        itemsIndexed(displayItems, key = { _, item -> if (item is TimeItem) "time_${item.timestamp}" else (item as MsgItem).msg.id }) { _, item ->
                            when (item) {
                                is TimeItem -> TimeDivider(item.timestamp)
                                else -> {
                                    val msgItem = item as MsgItem
                                    val isFlashTarget = msgItem.msg.id == highlightMsgId && flashAlpha.value > 0f
                                    val flashIsAi = msgItem.msg.role == Role.ASSISTANT
                                    // 入场动画：只给「有气泡框、且是这一轮刚刚送达」的消息。
                                    //  · 用户提示词：恒有气泡，恒播。
                                    //  · 拟人模式（微信聊天/动作演绎）的 AI 回复：整条回复生成完才落库，
                                    //    所以气泡「出现」的那一刻就是「送达」，播动画正合适。
                                    //  · 标准问答的 AI 回复：正文是边生成边流进 LiveContentBubble 的，
                                    //    落库时只是把流式气泡换成正式气泡。这时再播一次入场，
                                    //    用户会看到同一条回复「先消失、再重新滑进来」——所以不播。
                                    //  · 剧情补足：AI 回复是纯文字流没有气泡，也不播。
                                    val hasBubble = msgItem.msg.role == Role.USER ||
                                        (msgItem.msg.mode == ChatMode.COMPANION && !textFlowAi)
                                    val entrance = if (hasBubble) {
                                        rememberMessageEntrance(
                                            msgItem.msg.id, msgItem.msg.timestamp, entrancePlayed
                                        )
                                    } else null
                                    val riseDistance = with(density) { 14.dp.toPx() }
                                    val entranceMod = if (entrance == null) Modifier else Modifier.graphicsLayer {
                                        // 在绘制层读 State：动画期间只重绘这一条，不重组长列表项
                                        val p = entrance.value
                                        val eased = p * p * (3f - 2f * p)   // smoothstep，起步轻、收尾稳
                                        alpha = p
                                        translationY = riseDistance * (1f - eased)
                                        val sc = 0.975f + 0.025f * eased
                                        scaleX = sc
                                        scaleY = sc
                                    }
                                    ChatBubble(
                                        message = msgItem.msg,
                                        isDark = isDark,
                                        highlightKeyword = if (isFlashTarget) highlightKeyword else null,
                                        highlightColor = if (isFlashTarget && highlightKeyword != null)
                                            colors.ErrorRed.copy(alpha = 0.95f * flashAlpha.value)
                                        else null,
                                        modifier = when {
                                            isFlashTarget -> {
                                                // AI 消息左侧、用户消息右侧：锚定侧深、向对侧渐浅，横向渐变顶满屏幕，适配主题色不突兀
                                                val overhangPx = with(density) { 16.dp.toPx() }
                                                val peak = if (advancedMaterial) 0.26f else 0.18f
                                                val glow = colors.Primary.copy(alpha = peak * flashAlpha.value)
                                                Modifier
                                                    .fillMaxWidth()
                                                    .drawBehind {
                                                        val brush = if (flashIsAi)
                                                            Brush.horizontalGradient(0f to glow, 0.8f to Color.Transparent)
                                                        else
                                                            Brush.horizontalGradient(0.2f to Color.Transparent, 1f to glow)
                                                        drawRect(
                                                            brush = brush,
                                                            topLeft = Offset(-overhangPx, 0f),
                                                            size = Size(size.width + overhangPx * 2f, size.height)
                                                        )
                                                    }
                                            }
                                            // 多选勾选：与搜索定位闪烁同一套语言（同锚定侧、同横向渐变、同 overhang），
                                            // 只是峰值更低——勾选是「淡淡的图层」，闪一下的是全屏高亮
                                            multiSelect.active && msgItem.msg.id in multiSelect.selectedIds -> {
                                                val overhangPx = with(density) { 16.dp.toPx() }
                                                val peak = if (advancedMaterial) 0.20f else 0.14f
                                                val glow = colors.Primary.copy(alpha = peak)
                                                Modifier
                                                    .fillMaxWidth()
                                                    .drawBehind {
                                                        val brush = if (flashIsAi)
                                                            Brush.horizontalGradient(0f to glow, 0.8f to Color.Transparent)
                                                        else
                                                            Brush.horizontalGradient(0.2f to Color.Transparent, 1f to glow)
                                                        drawRect(
                                                            brush = brush,
                                                            topLeft = Offset(-overhangPx, 0f),
                                                            size = Size(size.width + overhangPx * 2f, size.height)
                                                        )
                                                    }
                                            }
                                            // 改写提示词中：其余消息整体压暗，让「正在改的是哪一条」一眼可见。
                                            // 用 alpha 而不是蒙层：蒙层会吃掉滚动和长按手势，改写时用户往往还要上下翻着看
                                            editingMessageId != null && msgItem.msg.id != editingMessageId ->
                                                Modifier.graphicsLayer { alpha = 0.28f }
                                            else -> Modifier
                                            // 入场动画挂在整条链最外层：与上面的高亮/多选/压暗各占一层，
                                            // alpha 相乘而不是互相覆盖，改写提示词时刚发的那条也不会突然不透明
                                        }.then(entranceMod),
                                        isThinking = false,
                                        showThinking = showThinking,
                                        onSpeak = {
                                            val playingThis = TtsController.playingMessageId.value == msgItem.msg.id
                                            when {
                                                playingThis && TtsController.isPaused.value -> TtsController.resume()
                                                playingThis -> TtsController.pause()
                                                else -> viewModel.speakMessage(msgItem.msg.id, msgItem.msg.content)
                                            }
                                        },
                                        onRegenerate = if (msgItem.index == lastAssistantIndex) {
                                            { viewModel.regenerate(msgItem.index) }
                                        } else null,
                                        onQuote = { viewModel.quoteMessage(msgItem.msg) },
                                        isFavorited = msgItem.msg.favorited,
                                        // 剧情补足的文字流：只作用于 AI 回复，用户消息照旧带气泡
                                        textFlow = textFlowAi,
                                        // 只有最后一条用户提示词可改：改历史消息会把后面整段上下文变成无效
                                        onEdit = if (!isLoading && editEnabled &&
                                            msgItem.index == lastUserMessageIndex &&
                                            msgItem.msg.content.isNotBlank()
                                        ) {
                                            {
                                                if (editingMessageId == msgItem.msg.id) {
                                                    // 再点一次 = 取消编辑，把原来的草稿还回去
                                                    editingMessageId = null
                                                    currentConvId?.let { viewModel.saveDraft(it, draftBeforeEdit) }
                                                } else {
                                                    currentConvId?.let { cid ->
                                                        draftBeforeEdit = viewModel.getDraft(cid)
                                                        viewModel.saveDraft(cid, msgItem.msg.content)
                                                    }
                                                    editingMessageId = msgItem.msg.id
                                                    inputFocusTick++   // 键盘直接弹出来
                                                }
                                            }
                                        } else null,
                                        // 多选：三个入口各自带着「点了哪个动作」进多选；整条消息可点=勾选
                                        multiSelectEnabled = multiSelect.active,
                                        onEnterMultiSelect = { action -> viewModel.enterMultiSelect(action, msgItem.msg.id) },
                                        onToggleSelect = { viewModel.toggleMultiSelect(msgItem.msg.id) }
                                    )
                                }
                            }
                        }

                        if (isLoading && currentMode != com.freechat.model.ChatMode.COMPANION) {
                            if (isGeneratingImage) {
                                item(key = "image_gen_placeholder") { ImageGeneratingPlaceholder(colors) }
                            }
                            // 灵动光球：思考中始终显示，直到正文开始流出才消失（不受「显示思考过程」开关影响）
                            if (!hasLiveContent && !isGeneratingImage) {
                                item(key = "typing") {
                                    // 用时秒表按 1Hz 变；在这个 item 自己的作用域里读，只有这一行跟着走
                                    val thinkMs by viewModel.thinkingTimeMs.collectAsState()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        SiriOrb(modifier = Modifier.size(30.dp), isDark = isDark)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            formatElapsed(thinkMs),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = colors.TextTertiary
                                        )
                                    }
                                }
                            }
                            if (showThinking && hasLiveReasoning) {
                                item(key = "live_reasoning") { LiveReasoningCard(liveReasoningState.value, colors) }
                            }
                            if (hasLiveContent) {
                                item(key = "live_content") {
                                    val thinkMs by viewModel.thinkingTimeMs.collectAsState()
                                    LiveContentBubble(liveContentState.value, selectedModel.displayName, thinkMs, isDark)
                                }
                            }
                        }
                    }
                }
        }

            // ===== 顶部标题栏背景 =====
            // 高级材质开：Haze 真高斯模糊 + 渐变渐隐（文字越接近顶部越模糊+渐隐）
            // 高级材质关：纯色顶栏（非沉浸，内容滚动被遮挡）
            if (advancedMaterial) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(topBandHeight)
                        .pageHeaderBackground(colors.Background)
                        // 接缝羽化：左边缘正是与侧滑页之间的接缝，模糊强度衰减到 0 → 缝两侧与缝本身同色
                        .seamFeather(seamFeatherPx, fromEnd = false)
                        .hazeEffect(state = hazeState) {
                            blurRadius = HazeSpec.TopBlurRadius
                            inputScale = HazeInputScale.None
                            backgroundColor = Color.Transparent
                            progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 1f, endY = topBarHeightPx, endIntensity = 0f)
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* 隔离点击：顶部模糊区下的内容不可点 */ }
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(statusBarHeightDp + titleBarAreaDp)
                        // 标题栏必须**不透明**（正文滚上来要被挡住）。炫彩开着时 pageBackground 是空操作
                        // —— 整页都透明，标题区就跟着透了。改用 pageHeaderBackground：炫彩关=这块底色本身，
                        // 炫彩开=钉在屏幕上的一份流光副本（本层会被侧滑平移，副本自带反向补偿）。
                        .pageHeaderBackground(colors.Background)
                )
            }

            // ===== 悬浮标题栏按钮（菜单 + 新对话），固定悬浮在屏幕顶部，与「FreeChat」同一水平线 =====
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Outlined.Menu, null, tint = colors.TextPrimary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
                if (multiSelect.active) {
                    // 多选态：右上角两个图标按钮收成一个文字按钮，文案 = 进入多选时点的那个动作。
                    // 左边的「已选择 x 项」由 MainActivity 的共享标题画在 x=52dp，这里留空。
                    TextButton(
                        onClick = onExecuteMultiAction,
                        enabled = multiSelect.selectedIds.isNotEmpty()
                    ) {
                        Text(
                            text = when (multiSelect.action) {
                                MultiSelectAction.SHARE -> s.share
                                MultiSelectAction.DELETE -> s.deleteMessage
                                MultiSelectAction.FAVORITE -> s.favorite
                                null -> ""
                            },
                            color = if (multiSelect.selectedIds.isNotEmpty()) colors.Primary else colors.TextTertiary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                } else {
                    // 每对话「调节」按钮：有对话内容时显示；拟人模式即使 0 消息也显示（可进入角色设定编辑）
                    if (messages.isNotEmpty() || companionMode) {
                        IconButton(onClick = {
                            if (currentMode == com.freechat.model.ChatMode.COMPANION) onOpenCharacterSetup()
                            else onOpenNewRules()
                        }) {
                            Icon(Icons.Outlined.Tune, s.newRules, tint = colors.TextSecondary, modifier = Modifier.size(22.dp))
                        }
                    }
                    // 新建图标：**已经在一个新对话里就不显示**（1.0.53）。
                    // chat 首页那张问候语页就是这个状态 —— 眼前这条对话一条消息都还没有，
                    // 右上角再摆一个「新建」是自相矛盾的（点了也只是把同样的空对话重开一遍）。
                    // 内置对话（Claude 风格助理）不算「新对话」：它是一条早就存在的对话，
                    // 空的时候给的是白页，那里留着新建是合理的。
                    if (messages.isNotEmpty() || isBuiltInConversation) {
                        IconButton(
                            onClick = {
                                val rect = chatNewChatRect.value
                                if (rect != null && onNewChatReveal != null) {
                                    onNewChatReveal(rect)
                                } else {
                                    onNewChat()
                                }
                            },
                            modifier = Modifier.onGloballyPositioned { coords ->
                                val pos = coords.positionInRoot()
                                val size = coords.size
                                chatNewChatRect.value = Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height)
                            }
                        ) {
                            Icon(Icons.Outlined.Create, s.newChat, tint = colors.TextSecondary, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // ===== 底部渐进模糊（高级材质）：贴屏幕最底，文字向下滑动渐隐（底部糊→输入框顶清） =====
            if (advancedMaterial) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(bottomBandHeight)
                        .offset { IntOffset(0, inputOffset.value.roundToInt()) }
                        // 1.0.50 尾巴：这里原来垫的是 liquidOpaqueBackground（一条与 progressive
                        // 同斜率的渐显衬底）。它只能把正文**压暗**，压不掉那条半透明的模糊层
                        // —— 用户看到的还是「墨汁晕开、内容仍可读」。现在衬底挪到了**源节点下面**
                        // 那一块（见上面 liquidSourceBackdrop），让磨出来的层本身不透明，
                        // 才真的把清晰正文替换掉。这里不能再垫了：两层叠起来正文会被吃掉两遍，
                        // 比非炫彩更早隐没。
                        // 接缝羽化：底部带子同样横跨接缝，左边缘强度归零（该处被输入框卡片遮住，观感零影响）
                        .seamFeather(seamFeatherPx, fromEnd = false)
                        .hazeEffect(state = hazeState) {
                            blurRadius = HazeSpec.BottomBlurRadius
                            inputScale = HazeInputScale.None
                            backgroundColor = Color.Transparent
                            progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 0f, endY = bottomBarHeightPx, endIntensity = 1f)
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* 隔离点击：底部模糊区下的内容不可点 */ }
                )
            }

            ChatInput(
                onSend = { text ->
                    val editing = editingMessageId
                    if (editing != null) {
                        // 改写后重发：旧提示词连同它的记忆一起作废，AI 按新提示词重新思考
                        editingMessageId = null
                        viewModel.sendEditedMessage(editing, text)
                    } else {
                        viewModel.sendMessage(text)
                    }
                },
                onStop = { viewModel.stopGeneration() },
                isLoading = isLoading,
                isDark = isDark,
                onAddImage = { imagePicker.launch("image/*") },
                onAddFile = if (currentMode == com.freechat.model.ChatMode.STANDARD) {
                    { filePicker.launch("*/*") }
                } else null,
                onPlusClick = { showPlusMenu = true },
                pendingImages = pendingImages.map { it.path },
                isAddingImages = isAddingImages,
                onRemovePendingImage = { idx -> viewModel.removePendingImage(idx) },
                pendingFiles = pendingFiles.map { it.name },
                isAddingFiles = isAddingFiles,
                onRemovePendingFile = { idx -> viewModel.removePendingFile(idx) },
                offsetYState = inputOffset,
                keyboardHeightDp = chatKeyboardDp,
                onInputFocused = {
                    // 与键盘 Effect 同源，但问的不是同一件事：聚焦问的是「此刻是否停在底部」，
                    // 所以锚点用三项之和的当前值。聚焦瞬间 IME 尚未弹出，键盘那项还是弹出前的值，
                    // 「键盘增量不算底部」天然成立。上翻阅读时不动 —— 用户反馈「非底部时点输入框被拽回底部」的病根。
                    if (messages.isNotEmpty() && listState.isStuckToBottom(currentBottomPaddingPx, bottomTolerancePx)) {
                        // lastIdx 表达式保持原样：coerceAtLeast 只在 totalItemsCount==0（首帧未 measure）时生效，
                        // 是「获焦早于首次布局」的兜底，不是错位，不要动。
                        val lastIdx = messages.lastIndex.coerceAtLeast(listState.layoutInfo.totalItemsCount - 1)
                        if (lastIdx >= 0) scope.launch { listState.scrollToItem(lastIdx, 1_000_000) }
                    }
                },
                draftText = draftText,
                onDraftChanged = { txt -> currentConvId?.let { viewModel.saveDraft(it, txt) } },
                onVoiceInput = { text ->
                    if (text.isNotBlank()) viewModel.sendMessage(text)
                    // 认的是**这条对话**的语音识别模型（新规则里可以单独指定），不能再拿全局那份当准 ——
                    // 全局没配但这条对话配了的话，识别本来能成，提示「没模型」就把人挡在门外了
                    else if (!viewModel.hasAsrModel(currentConvId)) showNoAsrDialog = true
                },
                onRecognizeVoice = { wav -> viewModel.recognizeVoiceInput(wav) },
                onRecordingChanged = { isRecording = it },
                hazeState = hazeState,
                isCompanion = currentMode == com.freechat.model.ChatMode.COMPANION,
                // 动作演绎 / 剧情补足：一次只发一条，发送后右侧键变终止键（点了撤回并退回输入框）
                narrativeSingleSend = currentMode == com.freechat.model.ChatMode.COMPANION &&
                    currentCharacter?.isNarrativeMode() == true,
                onRetract = {
                    // 撤回后紧接着把焦点请回输入框：退回的提示词就是拿来改的，
                    // 光标不落上去的话用户还得自己再点一下（超过 3 行会自动转全屏输入框）
                    viewModel.retractCurrentRound()
                    inputFocusTick++
                },
                focusTick = inputFocusTick,
                onFullscreenMenuRequest = { showFullscreenMenu = true },
                fullscreenTick = inputFullscreenTick,
                pasteTick = inputPasteTick,
                onExpandedChanged = { composerExpanded = it },
                editingHint = if (editingMessageId != null) s.editingPromptHint else null,
                onCancelEdit = {
                    editingMessageId = null
                    currentConvId?.let { viewModel.saveDraft(it, draftBeforeEdit) }
                },
                quotedContent = quotedMessage?.content?.ifBlank { s.imageTag },
                quotedImagePath = quotedMessage?.imagePaths?.firstOrNull(),
                onClearQuote = { viewModel.clearQuote() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { coords ->
                        val h = coords.size.height
                        if (h > 0) {
                            inputBoxMaxPx = maxOf(h.toFloat(), with(density) { 88.dp.toPx() })
                            // 全屏输入那一截增量靠这两个数相减得到（见 fullscreenExtraPaddingPx）：
                            // 收起时的高度只在「没展开」时记录，否则卡片一开就把基准顶高了，增量恒为 0
                            composerHeightPx = h
                            if (!composerExpanded) composerCollapsedHeightPx = h
                        }
                    }
            )

            // ===== "+" 附件菜单（窗口内悬浮，高级材质下真磨砂玻璃糊住背后聊天内容；缩放+淡入淡出，从输入框左上角弹出） =====
            // 透明拦截层：点击外部关闭（淡入淡出）
            AnimatedVisibility(
                visible = showPlusMenu,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showPlusMenu = false }
                )
            }
            AnimatedVisibility(
                visible = showPlusMenu,
                modifier = Modifier.align(Alignment.BottomStart),
                enter = FreeChatAnimation.menuEnter(TransformOrigin(0f, 1f)),
                exit = FreeChatAnimation.menuExit(TransformOrigin(0f, 1f))
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 20.dp, bottom = (chatKeyboardDp + 8.dp).coerceAtLeast(36.dp) + 60.dp)
                        .width(180.dp)
                        .then(
                            if (advancedMaterial) Modifier.frostedGlass(hazeState, isDark, RoundedCornerShape(20.dp), elevation = 6.dp)
                            else Modifier.background(colors.Surface, RoundedCornerShape(20.dp))
                        )
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showPlusMenu = false; imagePicker.launch("image/*") }.padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Image, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(s.uploadImage, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                        }
                        if (currentMode == com.freechat.model.ChatMode.STANDARD) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showPlusMenu = false; filePicker.launch("*/*") }.padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.AttachFile, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(s.uploadFile, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                            }
                        }
                    }
                }
            }

            // ===== 长按输入框 → 内置操作菜单（粘贴 / 全屏输入）（窗口内悬浮，与「+」附件菜单同一套材质与动效）=====
            // 「粘贴」是 1.0.49 加进来的：系统原生那条工具条已被主输入框掐掉（见 ChatInput 的
            // SuppressedTextToolbar），剪贴板改从这张卡片进 —— 免得两条叠在一起点不动。
            // 透明拦截层：点任何别的地方都关掉它。铺满整屏，所以背后的聊天列表也点得到，
            // 不会出现「点了半天关不掉」的那种恼人手感。
            AnimatedVisibility(
                visible = showFullscreenMenu,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showFullscreenMenu = false }
                )
            }
            AnimatedVisibility(
                visible = showFullscreenMenu,
                modifier = Modifier.align(Alignment.BottomStart),
                enter = FreeChatAnimation.menuEnter(TransformOrigin(0f, 1f)),
                exit = FreeChatAnimation.menuExit(TransformOrigin(0f, 1f))
            ) {
                Box(
                    modifier = Modifier
                        // 贴着输入框上沿弹出。高度**量的是输入框自己**（inputBoxMaxPx，别处已经量过），
                        // 不写死数字：全屏输入卡片展开时输入框会变高，写死就会让菜单盖在卡片上。
                        .padding(start = 20.dp, bottom = with(density) { inputBoxMaxPx.toDp() } + 6.dp)
                        .width(180.dp)
                        .then(
                            if (advancedMaterial) Modifier.frostedGlass(hazeState, isDark, RoundedCornerShape(20.dp), elevation = 6.dp)
                            else Modifier.background(colors.Surface, RoundedCornerShape(20.dp))
                        )
                ) {
                    Column {
                        // 「粘贴」：系统原生那条已经被主输入框掐掉了（见 SuppressedTextToolbar），
                        // 剪贴板改从这儿进来。剪贴板空的就置灰——点了没反应比置灰更让人困惑。
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = clipboardHasText) {
                                    showFullscreenMenu = false
                                    // 自增信号：输入框那边看到就把剪贴板文字插到光标处
                                    inputPasteTick++
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.ContentPaste,
                                null,
                                tint = if (clipboardHasText) colors.Primary else colors.TextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                s.paste,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (clipboardHasText) colors.TextPrimary else colors.TextTertiary
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showFullscreenMenu = false
                                    // 自增信号：输入框那边看到就展开全屏卡片（展开后焦点由它自己接管）
                                    inputFullscreenTick++
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.OpenInFull, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(s.fullscreenInput, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                        }
                    }
                }
            }

            if (revealPhase > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = revealAnim.value }
                        .pageBackground(colors.Background)
                )
            }

            // 改写提示词不再用对话框：点气泡 → 原文进输入框、键盘直接弹出、其余消息压暗。
            // 原来的 AlertDialog 已经删掉（`s.editMessage` / `s.editMessageHint` 两个字符串保留，
            // 设置页的「发送示例」附近还在用同一套文案，删字符串会连带影响别处）。

            // ===== 多选：批量分享方式选择 =====
            // 三个多选弹层都换成了窗口内的底部磨砂哑光玻璃弹层：原来的 AlertDialog 是独立窗口，
            // 看不到聊天页自己画的内容，"模糊背景"做不到，只能把背景压暗。
            SheetPanel(
                visible = showMultiShareMenu,
                onDismiss = { showMultiShareMenu = false },
                title = s.share,
                colors = colors,
                isDark = isDark,
                advancedMaterial = advancedMaterial,
                hazeState = hazeState
            ) {
                ShareMenuRow(Icons.Filled.Image, s.shareAsImage, colors) { runMultiShareImage() }
                ShareMenuRow(Icons.Filled.Description, s.shareAsMarkdown, colors) { runMultiShareMarkdown() }
            }

            // ===== 多选：批量删除确认（成对扩展 + 会清掉整段记忆，不可逆）=====
            SheetPanel(
                visible = showMultiDeleteConfirm,
                onDismiss = { showMultiDeleteConfirm = false },
                title = s.deleteMessage,
                colors = colors,
                isDark = isDark,
                advancedMaterial = advancedMaterial,
                hazeState = hazeState,
                confirmLabel = s.confirm,
                confirmDanger = true,
                onConfirm = {
                    showMultiDeleteConfirm = false
                    viewModel.applyDeleteToSelection()
                }
            ) {
                Text(s.batchDeleteConfirm, color = colors.TextSecondary)
            }

            // ===== 多选：批量分享长图预览 =====
            multiPreviewBitmap?.let { bmp ->
                ShareImagePreviewDialog(
                    bitmap = bmp,
                    onDismiss = { multiPreviewBitmap = null },
                    onShare = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { context.shareBitmap(bmp) }
                            if (!ok) Toast.makeText(context, s.shareFailed, Toast.LENGTH_SHORT).show()
                            multiPreviewBitmap = null
                            viewModel.exitMultiSelect()
                        }
                    },
                    onSave = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { context.saveBitmapToGallery(bmp) }
                            Toast.makeText(context, if (ok) s.savedOk else s.saveFailedShort, Toast.LENGTH_SHORT).show()
                            multiPreviewBitmap = null
                            viewModel.exitMultiSelect()
                        }
                    }
                )
            }

            // 未添加语音识别模型：弹层提示 + 一步直达添加
            SheetPanel(
                visible = showNoAsrDialog,
                onDismiss = { showNoAsrDialog = false },
                title = s.noAsrModelTitle,
                colors = colors,
                isDark = isDark,
                advancedMaterial = advancedMaterial,
                hazeState = hazeState,
                confirmLabel = s.goAddAction,
                onConfirm = {
                    showNoAsrDialog = false
                    onOpenAsrEditor()
                }
            ) {
                Text(s.noAsrModelDesc, color = colors.TextSecondary)
            }
        }
}

@Composable
private fun WelcomeGreeting(
    greeting: String,
    colors: FreeChatColors,
    keyboardProgressState: MutableFloatState,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val lines = splitGreeting(greeting)

    val randomIndents = remember {
        val rng = Random(System.currentTimeMillis() / 15000)
        lines.indices.map { index ->
            if (index == 0) 0 else 18 + rng.nextInt(22)
        }
    }

    val globalFont = LocalGlobalFontFamily.current
    val textStyle = remember(globalFont) {
        TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 35.sp, letterSpacing = 2.5.sp, fontFamily = globalFont)
    }
    val lineHeight = remember { 54.sp }

    Box(
        modifier = modifier.padding(horizontal = 28.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Layout(
            modifier = Modifier.graphicsLayer {
                val kp = keyboardProgressState.floatValue
                scaleX = lerp(1f, 22f / 35f, kp)
                scaleY = lerp(1f, 22f / 35f, kp)
                translationY = screenHeightPx * lerp(0.34f, 0.25f, kp)
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
            content = {
                lines.forEach { line ->
                    Text(line, style = textStyle.copy(lineHeight = lineHeight), color = colors.TextPrimary, softWrap = false)
                }
            }
        ) { measurables, constraints ->
            val kp = keyboardProgressState.floatValue
            val placeables = measurables.map { it.measure(constraints) }
            if (placeables.isEmpty()) return@Layout layout(0, 0) {}

            val maxSpacingPx = with(density) { 6.dp.toPx() }
            // 合并后行间连接间距（英文单词间需要空隙）
            val mergedJoinPx = with(density) { 12.sp.toPx() }

            val splitContentH: Int = placeables.sumOf { it.height } +
                ((placeables.size - 1).toFloat() * maxSpacingPx).roundToInt()
            val mergedContentH: Int = placeables.maxOf { it.height }

            // 合并后的总宽：各段宽度 + 连接间距（用于计算 block 居中偏移）
            val mergedFullW: Float = placeables.sumOf { it.width }.toFloat() +
                (placeables.size - 1).toFloat() * mergedJoinPx
            val maxIndentPx = with(density) { randomIndents.maxOfOrNull { it.dp.toPx() } ?: 0f }
            val contentSpan = maxOf(
                placeables[0].width.toFloat(),
                maxIndentPx + (placeables.getOrNull(1)?.width ?: 0).toFloat()
            )
            // 布局宽度 = max(合并后总宽, 拆分展开宽)，确保不超出
            val layoutW = maxOf(mergedFullW, contentSpan).roundToInt()

            layout(layoutW, maxOf(splitContentH, mergedContentH)) {
                val spacingPx = (maxSpacingPx * (1f - kp))
                val blockOffset = (layoutW - contentSpan) / 2f

                placeables.forEachIndexed { index, placeable ->
                    val indentPx = with(density) { randomIndents.getOrElse(index) { 0 }.dp.toPx() }
                    val indentAmount = indentPx * (1f - kp)
                    val splitX = indentAmount + blockOffset

                    // 合并 x：累加前序宽度 + 连接间距
                    var mergedX = 0f
                    for (j in 0 until index) {
                        mergedX += placeables[j].width + (mergedJoinPx * kp)
                    }

                    var splitY = 0f
                    for (j in 0 until index) { splitY += placeables[j].height + spacingPx }

                    val x = lerp(splitX, mergedX, kp).roundToInt()
                    val y = lerp(splitY, 0f, kp).roundToInt()
                    placeable.place(x, y)
                }
            }
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}

/**
 * 新消息的入场进度 0→1（**只有气泡形态的消息**才有，见调用处的 hasBubble）。
 *
 * 参考 iOS 短信：内容从自身下方一点点升起来、同时淡入，位移很小、速度很快，
 * 是「轻轻地落位」而不是「弹进来」。所以：
 *  · 位移只给 14dp —— 再多就成了「飘」，会显得拖沓；
 *  · 用阻尼略低的弹簧（dampingRatio 0.78）而不是线性/缓出曲线，收尾会有一点点回弹余地，
 *    这就是「灵动」的来源；stiffness 给 Medium，全程约 300ms 落地；
 *  · 透明度用同一进度线性跟随，不做额外延迟——分离的时序会让人觉得卡了一下；
 *  · 再叠一个 2.5% 的缩放（0.975 → 1）。纯位移+淡入在大屏上偏「平」，
 *    这点缩放让气泡像是「从远处轻轻落到手上」，是 iOS 那套质感里最容易被忽略、
 *    但去掉就会明显变廉价的一层。
 *
 * [played] 记录已经播过的消息 id：懒加载列表把消息滑出屏幕再滑回来时会重新组合，
 * 没有这个集合就会重播一遍，历史消息一屏屏往外弹，非常廉价。
 * 时间戳是第二道保险：只有「刚刚到达」的消息才播，进老对话不该有任何动画。
 */
@Composable
private fun rememberMessageEntrance(msgId: String, timestamp: Long, played: MutableSet<String>): State<Float> {
    val isNew = remember(msgId) {
        msgId !in played && System.currentTimeMillis() - timestamp < 4000L
    }
    val progress = remember(msgId) { Animatable(if (isNew) 0f else 1f) }
    LaunchedEffect(msgId) {
        if (isNew) {
            progress.animateTo(
                1f,
                spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
            )
        }
        played.add(msgId)
    }
    // 返回 State 而不是当前值：调用方在 graphicsLayer 的绘制 lambda 里读它，
    // 动画期间就只重绘、不重组，长列表不会因为一条消息入场而整屏重组
    return progress.asState()
}

private fun splitGreeting(text: String): List<String> {
    val regex = Regex("""([，。！？；、～~,.!?;:])""")
    val matches = regex.findAll(text).toList()
    if (matches.isEmpty()) {
        // 英文/无标点文本 — 在空格处或中间拆分
        return if (text.length > 12) {
            val spaceIdx = text.indexOf(' ', text.length / 3)
            if (spaceIdx in 4 until text.length - 2) {
                listOf(text.substring(0, spaceIdx), text.substring(spaceIdx + 1))
            } else {
                val mid = text.length / 2
                listOf(text.substring(0, mid), text.substring(mid))
            }
        } else {
            listOf(text)
        }
    }
    val parts = mutableListOf<String>()
    var lastEnd = 0
    for (m in matches) {
        val end = m.range.last + 1
        val segment = text.substring(lastEnd, end).trim()
        if (segment.isNotEmpty()) parts.add(segment)
        lastEnd = end
    }
    if (lastEnd < text.length) {
        val tail = text.substring(lastEnd).trim()
        if (tail.isNotEmpty()) parts.add(tail)
    }
    return parts.ifEmpty { listOf(text) }
}

@Composable
private fun LiveReasoningCard(reasoning: String, colors: FreeChatColors) {
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    // 默认**展开**（1.0.51 改）：思考过程要边想边看得见，而不是折起来只留一个标题，
    // 等回复落地才在正式气泡里「唰」地全展开。用户要的就是看着它一行行长出来。
    var expanded by remember { mutableStateOf(true) }

    // 卡片整体居中、左右对称（限制最大宽度并水平居中）；去掉脑图标与「思考中」文字，实时用时即进度
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (advancedMaterial) colors.Surface.copy(alpha = 0.7f) else colors.Primary.copy(alpha = 0.1f))
                    .then(if (advancedMaterial) Modifier.border(1.dp, colors.Divider.copy(alpha = 0.3f), RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.thinkingProcess, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.Primary)
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    null,
                    tint = colors.Primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.SurfaceVariant.copy(alpha = 0.35f)).padding(12.dp)
                ) {
                    Text(reasoning, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.TextSecondary, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun LiveContentBubble(content: String, modelName: String, elapsedMs: Long, isDark: Boolean) {
    val colors = LocalFreeChatColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp)) {
        MarkdownText(content = content, textColor = colors.AiBubbleText, codeBgColor = colors.SurfaceVariant,
            quoteBarColor = colors.Primary.copy(alpha = 0.5f), dividerColor = colors.Divider)
        Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(modelName, style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalLatinFontFamily.current), color = colors.TextTertiary)
            if (elapsedMs > 0) Text(formatElapsed(elapsedMs), style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalLatinFontFamily.current), color = colors.TextTertiary)
        }
    }
}

@Composable
private fun ImageGeneratingPlaceholder(colors: FreeChatColors) {
    val s = LocalStrings.current

    Box(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp, top = 4.dp)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
            .background(colors.SurfaceVariant), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 深色圆形"屏幕"承载发光圆点，浅/深主题下都清晰
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A0A0C))
                ) {
                    FluidOrb(modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(18.dp))
                Text(s.generatingImage, color = colors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun formatElapsed(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> String.format("%.1fs", ms / 1000.0)
    else -> "${ms / 60_000}m${(ms % 60_000) / 1000}s"
}

// 拟人模式列表项：时间分割线 or 消息气泡
private data class TimeItem(val timestamp: Long)
private data class MsgItem(val index: Int, val msg: Message)

/**
 * 几何贴底判定：末条 item 底边到「列表容器底边」的像素距离 ≥ [bottomPaddingPx] − 容差。
 *
 * [bottomPaddingPx] 是**被问的那一帧**的底部留白（静息 116dp + 该事件发生**之前**的键盘/卡片条增量），
 * 不是常量、也不是当前值 —— 三个调用点各传各的锚点，理由见文件顶部注释。
 *
 * 为什么不用 canScrollForward：它 = `index < itemsCount || currentMainAxisOffset > maxOffset`，
 * 而 `maxOffset = mainAxisAvailableSize = 容器高 − topPadding − bottomPadding`
 * （LazyList.kt:329-331 / LazyListMeasure.kt:167、441）。键盘弹起使 bottomPadding 变大，
 * maxOffset 立刻变小 ⇒ canScrollForward 立刻 true，与用户是否移动无关（判定被污染）。
 *
 * 本判定用的 d = viewportEndOffset − (last.offset + last.size)：
 *   viewportEndOffset = maxOffset + afterContentPadding            (LazyListMeasure.kt:469)
 *                     = (H − top − bottom) + bottom = H − top      ← bottomPadding 被完全抵消
 *   item 的 offset/size 只由 firstVisibleItemScrollOffset（LazyListState 跨 remeasure 保持）
 *   与内容尺寸决定，不含 bottomPadding 项 ⇒ d 只由滚动位置决定，padding 变化时逐像素不变。
 *
 * ⚠️ 记 S =「相对静息底线多滚了多少」（停在静息底线 S=0，滚到当前留白线 S = 当前留白 − 静息，
 *    上翻阅读 S<0）。d = 静息 + S，S 只随滚动变、不随留白变 ⇒ 本判定等价于
 *    「S ≥ 锚点 − 静息 − 容差」。于是「用户是否停在底部」必须补全成「停在**哪一帧的**底部」：
 *      (a) 用户停在底部时留白刚变大：S 还停在静息值，而当前留白线在 S = 本次增量处；
 *      (b) 用户在这次留白里上翻 u（0 ≤ u ≤ 本次增量）：S 落在静息值…增量之间，与 (a) 同一状态空间。
 *    (a) 该跟随、(b) 不该跟随，几何量完全相同，只有「留白变化」与「用户滚动」的先后能区分。
 *    · 锚点取静态 116dp ⇒ 等价于「S ≥ −容差」，把 (b) 整段（最多增量那么高）也算成贴底：
 *      上翻一点就被拽回底部（用户反馈的病根）。误判幅度 = 当前留白 − 静息 = 本次增量。
 *    · 锚点取**当前**留白 ⇒ 等价于「S ≥ 本次增量 − 容差」，方向相反地偏严：(a) 那一帧的跟随
 *      被整个丢掉，刚变大的留白会把最底部内容挡住。
 *    · 所以锚点必须取**变化前**的留白（= 静息 + 本次事件的其它来源）；见文件顶部三处锚点说明。
 *
 * item 的尾随间距恒为 0（LazyList.kt:365-368）⇒ 静止态滚到底时 d 精确等于该帧的留白，
 * 这就是「最新消息刚在输入框上方」那条视觉底线，阈值无需额外补偿。
 */
private fun LazyListState.isStuckToBottom(bottomPaddingPx: Int, tolerancePx: Int): Boolean {
    val info = layoutInfo
    val lastIndex = info.totalItemsCount - 1
    if (lastIndex < 0) return false
    // 按 index 查找而非 .last()：末条未被组合（用户已上翻很远）→ 一定不在底部；
    // 同时免疫 beyond-bounds 额外 item。
    val last = info.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return false
    return info.viewportEndOffset - (last.offset + last.size) >= bottomPaddingPx - tolerancePx
}

@Composable
private fun TimeDivider(timestamp: Long) {
    val colors = LocalFreeChatColors.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            formatMessageTime(timestamp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = colors.TextTertiary
        )
    }
}
