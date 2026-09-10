package com.freechat.ui.screens

import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalConfiguration
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
import com.freechat.model.Message
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.components.*
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.LocalGlobalFontFamily
import com.freechat.ui.theme.LocalLatinFontFamily
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.frostedGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeTint
import com.freechat.viewmodel.ChatViewModel
import kotlin.random.Random
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val liveReasoning by viewModel.liveReasoning.collectAsState()
    val liveContent by viewModel.liveContent.collectAsState()
    val thinkingTimeMs by viewModel.thinkingTimeMs.collectAsState()
    val isGeneratingImage by viewModel.isGeneratingImage.collectAsState()
    val pendingImages by viewModel.pendingImages.collectAsState()
    val isAddingImages by viewModel.isAddingImages.collectAsState()
    val quotedMessage by viewModel.quotedMessage.collectAsState()
    val pendingFiles by viewModel.pendingFiles.collectAsState()
    val isAddingFiles by viewModel.isAddingFiles.collectAsState()
    val ttsAutoPlay by viewModel.ttsAutoPlay.collectAsState()
    val showThinking by viewModel.showThinking.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val currentCharacter by viewModel.currentCharacter.collectAsState()
    val asrModel by viewModel.asrModel.collectAsState()
    val scrollTick by viewModel.scrollRequestTick.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showNoAsrDialog by remember { mutableStateOf(false) }
    // 搜索跳转：目标消息高亮微闪 + 关键词标红
    var highlightMsgId by remember { mutableStateOf<String?>(null) }
    var highlightKeyword by remember { mutableStateOf<String?>(null) }
    val flashAlpha = remember { Animatable(0f) }

    // 恢复到该对话上次的滚动位置（跨页面切换返回后不回顶部）
    val savedInitial = viewModel.currentConversationId.value?.let { viewModel.chatScrollPositions.value[it] }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedInitial?.first ?: 0,
        initialFirstVisibleItemScrollOffset = savedInitial?.second ?: 0
    )
    val greeting = remember(s.localeCode) { viewModel.generateGreeting(s) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    // 拟人模式：时间居中显示——首条消息与间隔 >5min 的消息前插入时间分割线
    val companionMode = currentMode == ChatMode.COMPANION
    val plotMode = currentCharacter?.plotSimulation == true
    val lastUserMessageIndex = messages.indexOfLast { it.role == com.freechat.model.Role.USER }
    // 剧情编辑：改写最后一条用户消息
    var showEditDialog by remember { mutableStateOf(false) }
    var editDraft by remember { mutableStateOf("") }
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
    // 输入框隐藏偏移：实测输入框真实高度（多行时更高，避免顶部漏出）；单行兜底 88dp
    var inputBoxMaxPx by remember { mutableFloatStateOf(with(density) { 88.dp.toPx() }) }

    // ──── 新对话过渡动画 ────
    val revealAnim = remember { Animatable(0f) }
    var revealPhase by remember { mutableIntStateOf(0) }
    LaunchedEffect(revealTrigger) {
        if (revealTrigger > 0 && messages.isNotEmpty()) {
            revealPhase = 1
            revealAnim.snapTo(0f)
            revealAnim.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
            onNewChat()
            revealPhase = 2
            revealAnim.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
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

    // 键盘弹出：文字流跟随键盘上移实时滚到底（scrollToItem 瞬时跟随 ime 变化，与键盘/输入框并行、丝滑无停顿）
    // 仅 Chat 页响应（isDrawerOpen 时 chatKeyboardDp=0，侧滑页搜索框唤出键盘不触发滚动）
    LaunchedEffect(chatKeyboardDp) {
        if (chatKeyboardDp > 0.dp && messages.isNotEmpty()) {
            val lastIdx = listState.layoutInfo.totalItemsCount - 1
            if (lastIdx >= 0) {
                listState.scrollToItem(lastIdx, Int.MAX_VALUE)
            }
        }
    }

    // 引用/图片卡片出现时，自动滚到底部，保证卡片不遮挡最底部文字流
    LaunchedEffect(quotedMessage, pendingImages.size) {
        if (messages.isNotEmpty() && (quotedMessage != null || pendingImages.isNotEmpty())) {
            val lastIdx = listState.layoutInfo.totalItemsCount - 1
            if (lastIdx >= 0) {
                listState.scrollToItem(lastIdx, Int.MAX_VALUE)
            }
        }
    }

    var prevConvId by remember { mutableStateOf<String?>(null) }
    // 用户是否停在底部：AI 回复仅当用户本就停在底部才跟随滚到底，上翻阅读时不打断位置
    val atBottom = remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }
            .collect { canScroll -> atBottom.value = !canScroll }
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
                flashAlpha.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
                delay(700)
                flashAlpha.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
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
    val titleBarAreaDp = 48.dp
    // 顶部模糊的渐变终点（px）：endY 默认是无穷大，会导致 easing 曲线只用到 t≈0 一小段、模糊「秒没」。
    // 显式填真实高度 + 顶部多伸渐隐区（HazeSpec.TopFadeZoneDp），让「满糊→清晰」的过渡有足够距离、感知不到分界。
    val topFadeZoneDp = HazeSpec.TopFadeZoneDp
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + topFadeZoneDp).toPx() }
    val bottomBarHeightPx = with(density) { HazeSpec.BottomFadeHeightDp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Background)
    ) {
        // ===== 内容层：高级材质下沉浸到状态栏之下 =====
        // 问候语只在标准问答模式显示；拟人模式（含新建角色 0 消息）走空聊天列表，等待用户发第一条
        if (messages.isEmpty() && !companionMode) {
            WelcomeGreeting(
                greeting = greeting,
                colors = colors,
                keyboardProgressState = greetingProgressState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).background(colors.Background) else Modifier)
                    .padding(top = statusBarHeightDp + titleBarAreaDp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).background(colors.Background) else Modifier),
                state = listState,
                userScrollEnabled = !isRecording,
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = statusBarHeightDp + titleBarAreaDp + 8.dp,
                    bottom = (if (chatKeyboardVisible) chatKeyboardDp + 116.dp else 116.dp) +
                        if (pendingImages.isNotEmpty() || quotedMessage != null) 64.dp else 0.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                    itemsIndexed(displayItems, key = { _, item -> if (item is TimeItem) "time_${item.timestamp}" else (item as MsgItem).msg.id }) { _, item ->
                        when (item) {
                            is TimeItem -> TimeDivider(item.timestamp)
                            else -> {
                                val msgItem = item as MsgItem
                                val isFlashTarget = msgItem.msg.id == highlightMsgId && flashAlpha.value > 0f
                                val flashIsAi = msgItem.msg.role == com.freechat.model.Role.ASSISTANT
                                ChatBubble(
                                    message = msgItem.msg,
                                    isDark = isDark,
                                    highlightKeyword = if (isFlashTarget) highlightKeyword else null,
                                    highlightColor = if (isFlashTarget && highlightKeyword != null)
                                        colors.ErrorRed.copy(alpha = 0.95f * flashAlpha.value)
                                    else null,
                                    modifier = if (isFlashTarget) {
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
                                    } else Modifier,
                                    isThinking = false,
                                    showThinking = showThinking,
                                    onDelete = {
                                        viewModel.deleteMessagePair(msgItem.index)
                                    },
                                    onSpeak = {
                                        val playingThis = TtsController.playingMessageId.value == msgItem.msg.id
                                        when {
                                            playingThis && TtsController.isPaused.value -> TtsController.resume()
                                            playingThis -> TtsController.pause()
                                            else -> viewModel.speakMessage(msgItem.msg.id, msgItem.msg.content)
                                        }
                                    },
                                    onRegenerate = { viewModel.regenerate(msgItem.index) },
                                    onQuote = { viewModel.quoteMessage(msgItem.msg) },
                                    onFavorite = { currentConvId?.let { viewModel.toggleFavorite(it, msgItem.msg.id) } },
                                    isFavorited = msgItem.msg.favorited,
                                    onEdit = if (plotMode && msgItem.index == lastUserMessageIndex && msgItem.msg.role == com.freechat.model.Role.USER && msgItem.msg.content.isNotBlank()) {
                                        {
                                            editDraft = msgItem.msg.content
                                            showEditDialog = true
                                        }
                                    } else null
                                )
                            }
                        }
                    }

                    if (isLoading && currentMode != com.freechat.model.ChatMode.COMPANION) {
                        if (isGeneratingImage) {
                            item(key = "image_gen_placeholder") { ImageGeneratingPlaceholder(colors) }
                        }
                        // 灵动光球：思考中始终显示，直到正文开始流出才消失（不受「显示思考过程」开关影响）
                        if (liveContent.isEmpty() && !isGeneratingImage) {
                            item(key = "typing") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    SiriOrb(modifier = Modifier.size(30.dp), isDark = isDark)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        formatElapsed(thinkingTimeMs),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = colors.TextTertiary
                                    )
                                }
                            }
                        }
                        if (showThinking && liveReasoning.isNotEmpty()) {
                            item(key = "live_reasoning") { LiveReasoningCard(liveReasoning, colors) }
                        }
                        if (liveContent.isNotEmpty()) {
                            item(key = "live_content") { LiveContentBubble(liveContent, selectedModel.displayName, thinkingTimeMs, isDark) }
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
                        .height(statusBarHeightDp + titleBarAreaDp + topFadeZoneDp)
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
                        .background(colors.Background)
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
                // 每对话「调节」按钮：有对话内容时显示；拟人模式即使 0 消息也显示（可进入角色设定编辑）
                if (messages.isNotEmpty() || companionMode) {
                    IconButton(onClick = {
                        if (currentMode == com.freechat.model.ChatMode.COMPANION) onOpenCharacterSetup()
                        else onOpenNewRules()
                    }) {
                        Icon(Icons.Outlined.Tune, s.newRules, tint = colors.TextSecondary, modifier = Modifier.size(22.dp))
                    }
                }
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

            // ===== 底部渐进模糊（高级材质）：贴屏幕最底，文字向下滑动渐隐（底部糊→输入框顶清） =====
            if (advancedMaterial) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(88.dp)
                        .offset { IntOffset(0, inputOffset.value.roundToInt()) }
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
                onSend = { text -> viewModel.sendMessage(text) },
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
                    if (messages.isNotEmpty() && !listState.canScrollForward) {
                        val lastIdx = messages.lastIndex.coerceAtLeast(listState.layoutInfo.totalItemsCount - 1)
                        if (lastIdx >= 0) scope.launch { listState.scrollToItem(lastIdx, 1_000_000) }
                    }
                },
                draftText = draftText,
                onDraftChanged = { txt -> currentConvId?.let { viewModel.saveDraft(it, txt) } },
                onVoiceInput = { text ->
                    if (text.isNotBlank()) viewModel.sendMessage(text)
                    else if (asrModel.isEmpty()) showNoAsrDialog = true
                },
                onRecognizeVoice = { wav -> viewModel.recognizeVoiceInput(wav) },
                onRecordingChanged = { isRecording = it },
                hazeState = hazeState,
                isCompanion = currentMode == com.freechat.model.ChatMode.COMPANION,
                quotedContent = quotedMessage?.content?.ifBlank { "[图片]" },
                quotedImagePath = quotedMessage?.imagePaths?.firstOrNull(),
                onClearQuote = { viewModel.clearQuote() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { coords ->
                        val h = coords.size.height.toFloat()
                        if (h > 0f) inputBoxMaxPx = maxOf(h, with(density) { 88.dp.toPx() })
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
                enter = scaleIn(initialScale = 0.9f, transformOrigin = TransformOrigin(0f, 1f), animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                    fadeIn(tween(150)),
                exit = scaleOut(targetScale = 0.92f, transformOrigin = TransformOrigin(0f, 1f), animationSpec = tween(140, easing = FastOutLinearInEasing)) +
                    fadeOut(tween(120))
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

            if (revealPhase > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = revealAnim.value }
                        .background(colors.Background)
                )
            }

            // 剧情模式：编辑最后一条用户消息（改写提示词重新生成）
            if (showEditDialog) {
                AlertDialog(
                    onDismissRequest = { showEditDialog = false },
                    title = { Text(s.editMessage, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
                    text = {
                        OutlinedTextField(
                            value = editDraft,
                            onValueChange = { editDraft = it },
                            placeholder = { Text(s.editMessageHint, color = colors.TextTertiary) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.TextPrimary,
                                unfocusedTextColor = colors.TextPrimary,
                                focusedBorderColor = colors.Primary,
                                unfocusedBorderColor = colors.Divider,
                                cursorColor = colors.Primary
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showEditDialog = false
                            viewModel.editCompanionLastMessage(editDraft)
                        }) { Text(s.confirm, color = colors.Primary) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditDialog = false }) { Text(s.cancel, color = colors.TextSecondary) }
                    },
                    containerColor = colors.Surface
                )
            }

            // 未添加语音识别模型：弹窗提示 + 一步直达添加
            if (showNoAsrDialog) {
                AlertDialog(
                    onDismissRequest = { showNoAsrDialog = false },
                    title = { Text("未添加语音识别模型", color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
                    text = { Text("请先添加语音识别模型后再使用语音输入。", color = colors.TextSecondary) },
                    confirmButton = {
                        TextButton(onClick = {
                            showNoAsrDialog = false
                            onOpenAsrEditor()
                        }) { Text("去添加", color = colors.Primary) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNoAsrDialog = false }) { Text(s.cancel, color = colors.TextSecondary) }
                    },
                    containerColor = colors.Surface
                )
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
    var expanded by remember { mutableStateOf(false) }

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
