package com.freechat.ui.screens

import com.freechat.i18n.storySettings

import com.freechat.ui.adaptive.LocalPaneSize
import com.freechat.ui.adaptive.LocalTabletUi
import com.freechat.ui.adaptive.PaneExpandButton
import com.freechat.ui.adaptive.TabletChatContent

import android.net.Uri
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.freechat.ui.theme.AppBoxWithConstraints as BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import com.freechat.ui.animation.MotionTextButton as TextButton
import com.freechat.ui.components.HeaderIconButton
import com.freechat.ui.components.HeaderTextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freechat.data.TtsController
import com.freechat.data.SearchPresentation
import com.freechat.i18n.LocalStrings
import com.freechat.i18n.groupSettings
import com.freechat.model.ChatMode
import com.freechat.model.DialogueMode
import com.freechat.model.InputBarState
import com.freechat.model.InputStyle
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.PerConvSettings
import com.freechat.model.Message
import com.freechat.model.MultiSelectAction
import com.freechat.model.Role
import com.freechat.model.isNarrativeMode
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.MessageEntranceLedger
import com.freechat.ui.animation.FirstSendMotionIntent
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.animation.MotionPolicy
import com.freechat.ui.animation.messageEntrance
import com.freechat.ui.animation.rememberMessageEntrance
import kotlinx.coroutines.CancellationException
import com.freechat.ui.components.*
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.LocalGlobalFontFamily
import com.freechat.ui.theme.LocalLatinFontFamily
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.frostedGlass
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.theme.seamFeather
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.freechat.ui.theme.materialHaze as hazeEffect
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeTint
import com.freechat.viewmodel.ChatViewModel
import com.freechat.util.ShareImageGenerator
import com.freechat.util.ShareSegment
import com.freechat.util.ShareLinkPayload
import com.freechat.util.saveBitmapToGallery
import com.freechat.util.shareBitmap
import com.freechat.util.shareLink
import com.freechat.util.shareMarkdown
import com.freechat.sync.ApiClient
import com.freechat.sync.ApiError
import com.freechat.sync.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.components.TopBarBackdrop
import com.freechat.ui.components.TopBarBackdropSource
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
    onOpenCharacterSetup: () -> Unit = {},
    onOpenGroupSettings: () -> Unit = {},
    onOpenStorySettings: () -> Unit = {},
    onOpenAsrEditor: () -> Unit = {},
    /** 长按「生成图片」快跳生图模型编辑页（1.0.71） */
    onOpenModelEditor: (ModelType, ModelInfo?) -> Unit = { _, _ -> },
    /** 「连接微信」未登录引导「登录使用」→ 登录/注册页（1.0.94） */
    onOpenAccount: () -> Unit = {},
    hazeState: HazeState,
    isDrawerOpen: Boolean = false,
    navigationEnabled: Boolean = true,
    canInteract: () -> Boolean = { true },
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val latestPageActive = rememberUpdatedState(com.freechat.ui.animation.LocalPageActive.current)
    var showPlusMenu by remember { mutableStateOf(false) }
    // 1.0.69：「+」菜单的「生成图片」一次性勾选（发出下一条即自动取消）
    val forceImageGen by viewModel.forceImageGen.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val playedVoiceIds by viewModel.playedVoiceIds.collectAsState()
    val currentConvId by viewModel.currentConversationId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedVisualModel by viewModel.selectedVisualModel.collectAsState()
    // 输入框样式（1.0.70）：完整样式下「生成图片」从 + 菜单独立到底行、长按菜单不再含「全屏输入」
    val inputStyle by viewModel.inputStyle.collectAsState()
    // 输入框状态（1.0.71）：永久固定 = 不触发隐藏动画
    val inputBarState by viewModel.inputBarState.collectAsState()
    // 流式三兄弟（思考过程 / 正文 / 用时）不再在顶层 collect：
    // 这里是整屏最大的重组作用域，而流式输出时正文是**每来一个字**变一次 ——
    // 原来每字都会作废整个 ChatScreen（LazyColumn 的 item 表被重跑一遍、每个可见气泡的内容 lambda 都是新实例，
    // 于是长对话里「一个字」触发的是所有可见气泡的重组）。
    // 真正的字符串下沉到用它的列表项里读，每字只重组正在长的那一条气泡。
    val liveReasoningState = viewModel.liveReasoning.collectAsState()
    val liveContentState = viewModel.liveContent.collectAsState()
    val isGeneratingImage by viewModel.isGeneratingImage.collectAsState()
    val imageGenerationPhase by viewModel.generationPhase.collectAsState()
    val generationReplyId by viewModel.generationReplyId.collectAsState()
    val generationModelName by viewModel.generationModelName.collectAsState()
    val pendingImages by viewModel.pendingImages.collectAsState()
    val isAddingImages by viewModel.isAddingImages.collectAsState()
    val attachmentNotice by viewModel.attachmentNotice.collectAsState()
    val quotedMessage by viewModel.quotedMessage.collectAsState()
    val pendingFiles by viewModel.pendingFiles.collectAsState()
    val isAddingFiles by viewModel.isAddingFiles.collectAsState()
    val ttsAutoPlay by viewModel.ttsAutoPlay.collectAsState()
    // 按**这条对话**算出来的「显示思考过程」：新规则里的覆盖优先，没设才跟随全局。
    // 不能用全局那个 showThinking —— 它和「新规则」冲突时说了不算（1.0.51 修）。
    val showThinking by viewModel.effectiveShowThinking.collectAsState()
    // 1.1.29.8 工单②：默认折叠思考过程（回复开流自动折起）
    val collapseThinking by viewModel.collapseThinking.collectAsState()
    val showSearchSources by viewModel.showSearchSources.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val currentCharacter by viewModel.currentCharacter.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val groupChat = conversations.firstOrNull { it.id == currentConvId }?.groupChat
    val storyProfile = conversations.firstOrNull { it.id == currentConvId }?.storyProfile
    val scrollTick by viewModel.scrollRequestTick.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showNoAsrDialog by remember { mutableStateOf(false) }
    var pendingSceneDeleteId by remember { mutableStateOf<String?>(null) }
    // 长按语音菜单（2026-10-19）：转文字（口播台词/识别文本本就随消息存着）/ 引用
    var voiceMenuTarget by remember { mutableStateOf<com.freechat.model.Message?>(null) }
    var revealedVoiceIds by remember { mutableStateOf(setOf<String>()) }
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
    var multiShareLinkUrl by remember { mutableStateOf<String?>(null) }
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

    // 在线网页链接分享：选中消息（正文+时间+图片转存）推到服务器，拿回免登录可看的公开链接。
    // 与长图守卫同思路：能放行的选择一定能渲染出来（20 条 / 3 万字 / 10MB，ShareLinkPayload 里与服务端同口径）；
    // 超限不退多选，去掉几条就能直接重试。需要登录（链接挂在账号下可撤销），未登录提示去登录、保留勾选。
    fun runMultiShareLink() {
        showMultiShareMenu = false
        val msgs = multiSelectedMessages()
        if (msgs.isEmpty()) { viewModel.exitMultiSelect(); return }
        val auth = Session.loadAuth()
        if (auth == null) {
            Toast.makeText(context, s.shareLinkNeedLogin, Toast.LENGTH_SHORT).show()
            return
        }
        val title = viewModel.currentConversationId.value
            ?.let { id -> viewModel.conversations.value.find { it.id == id }?.title }
            ?.takeIf { it.isNotBlank() } ?: "FreeChat"
        scope.launch {
            Toast.makeText(context, s.shareLinkCreating, Toast.LENGTH_SHORT).show()
            val built = ShareLinkPayload.build(title, msgs)
            when (built) {
                is ShareLinkPayload.Build.Ok -> {
                    val result = try {
                        ApiClient.createShare(auth.token, built.body)
                    } catch (e: ApiError) {
                        // 413（太大/空间满）等服务端已经把原因写在 message 里，直接展示
                        Toast.makeText(context, e.message ?: s.shareLinkCreateFailed, Toast.LENGTH_SHORT).show()
                        return@launch
                    } catch (_: Exception) {
                        Toast.makeText(context, s.shareLinkCreateFailed, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    multiShareLinkUrl = result.url
                    viewModel.exitMultiSelect()
                }
                is ShareLinkPayload.Build.TooManyMessages,
                is ShareLinkPayload.Build.TooManyChars -> {
                    Toast.makeText(context, s.shareLinkTooMuch, Toast.LENGTH_SHORT).show()
                }
                is ShareLinkPayload.Build.TooLarge -> {
                    Toast.makeText(context, s.shareImageTooLarge, Toast.LENGTH_SHORT).show()
                }
            }
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
    // Initial restoration is a snapshot, not a subscription to every saved scroll pixel.
    val savedInitial = remember(currentConvId) {
        currentConvId?.let { viewModel.chatSessionState.viewport(it) }
    }
    val listState = remember(currentConvId) {
        LazyListState(savedInitial?.index ?: Int.MAX_VALUE, savedInitial?.offset ?: 1_000_000)
    }
    var viewportRestored by remember(listState) { mutableStateOf(false) }
    val greeting = remember(s.localeCode) { viewModel.generateGreeting(s) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val paneSize = LocalPaneSize.current
    val tablet = LocalTabletUi.current
    val paneWidth = if (tablet && paneSize != androidx.compose.ui.unit.DpSize.Unspecified) paneSize.width else config.screenWidthDp.dp
    val paneHeight = if (tablet && paneSize != androidx.compose.ui.unit.DpSize.Unspecified) paneSize.height else config.screenHeightDp.dp
    val contentWidth = minOf(paneWidth, 800.dp)
    val contentGutter = ((paneWidth - contentWidth) / 2).coerceAtLeast(0.dp)
    val screenHeightPx = with(density) { paneHeight.toPx() }

    // 拟人模式：时间居中显示——首条消息与间隔 >5min 的消息前插入时间分割线
    val companionMode = currentMode == ChatMode.COMPANION
    // 对话模式：currentCharacter 在个别入口可能还是未迁移的原始对象，再过一次 normalized() 兜底
    // （1.0.28 前的老档案只写了 plotSimulation，dialogueMode 还是默认值）
    val dialogueMode = if (storyProfile != null) DialogueMode.PLOT else if (groupChat != null) DialogueMode.WECHAT
        else currentCharacter?.normalized()?.dialogueMode ?: DialogueMode.WECHAT
    // 剧情补足：AI 回复不套气泡框，以纯文本流呈现（用户消息仍有气泡，用于区分辨别）
    val textFlowAi = dialogueMode == DialogueMode.PLOT
    val narrativeActions = storyProfile != null || (groupChat == null && com.freechat.data.CompanionFeaturePolicy.supportsNarrativeActions(currentCharacter))
    val photoUploadAllowed = currentMode == ChatMode.STANDARD ||
        com.freechat.data.CharacterPresentationPolicy.usesPhotoRecognition(dialogueMode)
    val sceneLockedIds = remember(messages, companionMode) {
        if (companionMode) com.freechat.data.CompanionFeaturePolicy.sceneLockedMessageIds(messages) else emptySet()
    }
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
        messages.indexOfLast { it.role == com.freechat.model.Role.ASSISTANT && !it.sceneVisualization }
    }
    // 剧情编辑：改写最后一条用户消息
    // 改写提示词：点最后一条气泡即进入编辑态 —— 原文灌进输入框、键盘直接弹出，
    // 不再弹一个编辑对话框。编辑态下其余消息整体压暗，视觉焦点只留在那一条上。
    // 1.1.26.9：改写状态上收到 VM（MainActivity 的抽屉拖拽手势也要按它锁页面）。
    // 取消/退出一律清空输入框草稿，不还回旧草稿（用户拍板「退出改写后输入框中的文字一并清除不保留」）。
    val editingMessageId by viewModel.editingPromptId.collectAsState()
    var inputFocusTick by remember { mutableIntStateOf(0) }
    var mentionRequest by remember(currentConvId) { mutableStateOf<MentionSelectionRequest?>(null) }
    var mentionInsertion by remember(currentConvId) { mutableStateOf<MentionComposerInsertion?>(null) }
    var mentionRevision by remember(currentConvId) { mutableIntStateOf(0) }
    val mentionKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val mentionFocus = androidx.compose.ui.platform.LocalFocusManager.current
    // 1.1.29.1 ①：粒子粉碎退场——重生成/重回复删消息按最后一帧位置起爆（用户主动删除不走这）
    val shatterBursts = remember { mutableStateListOf<ShatterBurst>() }
    val msgRects = remember { mutableMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val msgIsUser = remember { mutableMapOf<String, Boolean>() }
    val shatterOrigin = remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var shatterSeq = remember { java.util.concurrent.atomic.AtomicLong(0) }
    LaunchedEffect(Unit) {
        viewModel.shatterRequests.collect { ids ->
            ids.forEach { id ->
                val rect = msgRects[id] ?: return@forEach
                shatterBursts += ShatterBurst(shatterSeq.incrementAndGet(), rect, msgIsUser[id] == true)
            }
        }
    }
    LaunchedEffect(sceneLockedIds) {
        val editing = viewModel.editingPromptId.value
        if (editing != null && editing in sceneLockedIds) {
            viewModel.setEditingPrompt(null)
            viewModel.currentConversationId.value?.let { viewModel.saveDraft(it, "") }
        }
    }
    // 改写中页面锁定：系统返回不退出改写、也不离开本页——出口只有「取消」钮或改写成功
    androidx.activity.compose.BackHandler(enabled = latestPageActive.value && editingMessageId != null) { }
    androidx.activity.compose.BackHandler(enabled = latestPageActive.value && showPlusMenu) { showPlusMenu = false }
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
    val conversationImages by key(currentConvId) {
        produceState(emptyList<PreviewImage>(), messages) {
            value = withContext(Dispatchers.IO) { ConversationImageGallery.images(messages) }
        }
    }
    val firstSendMotion = remember(viewModel) { FirstSendMotionIntent() }
    val entranceLedger = remember(currentConvId) {
        val sentAt = firstSendMotion.consume(currentConvId)
        MessageEntranceLedger(
            messages.filter { sentAt == null || it.timestamp < sentAt }.map { it.id },
            sentAt ?: System.currentTimeMillis())
    }
    fun sendNewMessage(text: String, voicePath: String? = null) {
        if (!latestPageActive.value || !canInteract()) return
        val creating = viewModel.currentConversationId.value == null
        val sentAt = System.currentTimeMillis()
        viewModel.sendMessage(text, voicePath)
        if (creating) firstSendMotion.record(viewModel.currentConversationId.value, sentAt)
    }
    val timeGapMs = 5 * 60 * 1000L
    val pendingReply = remember(generationReplyId, isLoading, companionMode, narrativeActions, isGeneratingImage, generationModelName, selectedModel) {
        generationReplyId?.takeIf { isLoading && (!companionMode || narrativeActions || isGeneratingImage) }?.let {
            Message(id = it, role = Role.ASSISTANT, content = "", isStreaming = true,
                modelName = generationModelName.ifBlank { selectedModel.displayName },
                mode = if (companionMode) ChatMode.COMPANION else ChatMode.STANDARD)
        }
    }
    val displayItems = remember(messages, companionMode, pendingReply) {
        buildChatDisplayItems(messages, companionMode, pendingReply, timeGapMs)
    }

    // 删除退场：被删行先按角色方向滑出屏幕，滑出后才真正移除（多选/重生成/场景图共用）。
    val exitingIds = remember { mutableStateOf<Set<String>>(emptySet()) }
    val exitProgress = remember { Animatable(1f) }
    val exitProgressState = remember(exitProgress) { exitProgress.asState() }
    var exitBusy by remember { mutableStateOf(false) }
    // 1.1.29.9 工单②：animateItem 的换位滑移只在真删除/结构变动时开——常开的话，
    // 流式增长、折叠收放、图片落位的每一次高度变化都带 320ms 滑移，滚动中读作「消息乱跳/错位」。
    var placementAnimOn by remember { mutableStateOf(false) }
    val motionOn = LocalMotionEnabled.current

    /**
     * 两阶段删除编排：先播方向性退场（AI 左 / 用户右），完成后才执行真正的删除/重生成。
     * 减动效直接删；退场期间再次触发忽略，防止与移除竞态。
     */
    suspend fun playExitThen(ids: Collection<String>, action: () -> Unit) {
        if (exitBusy) return
        val targets = ids.toSet()
        if (targets.isEmpty() || !motionOn) {
            action()
            return
        }
        exitBusy = true
        try {
            val rows = displayItems
            val delIdx = rows.indices.filter { rows[it] is MsgItem && (rows[it] as MsgItem).msg.id in targets }
            val firstDel = delIdx.minOrNull()
            val anchorId = firstDel?.let { d -> (0 until d).mapNotNull { rows[it] as? MsgItem }.lastOrNull()?.msg?.id }
            val visibleHit = listState.layoutInfo.visibleItemsInfo.any { it.key in targets }
            try {
                exitingIds.value = targets
                exitProgress.snapTo(0f)
                exitProgress.animateTo(1f, animationSpec = FreeChatAnimation.messageExitTween)
                // 先开门控再移除：移除触发的重组必须看见 placementAnimOn=true，否则换位瞬移
                placementAnimOn = true
                action()
                kotlinx.coroutines.delay(FreeChatAnimation.listPlacementTween.durationMillis.toLong())
            } finally {
                exitingIds.value = emptySet()
            }
            // 焦点回落：删除前可见且被删 → 平滑滚到「被删第一条的前一条」；删在最顶上则回剩余顶部
            if (visibleHit) {
                val fresh = buildChatDisplayItems(viewModel.messages.value, companionMode, null, timeGapMs)
                val target = anchorId?.let { id -> fresh.indexOfFirst { it is MsgItem && (it as MsgItem).msg.id == id } } ?: -1
                if (target >= 0) listState.animateScrollToItem(target)
                else if (anchorId == null && fresh.isNotEmpty()) listState.animateScrollToItem(0)
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            exitBusy = false
            placementAnimOn = false
        }
    }

    var showQuickLocate by remember(currentConvId) { mutableStateOf(false) }
    // 连接微信（1.0.93，从模拟设定页迁到标题栏）：WECHAT 档拟人对话专属
    var showWechatBind by remember(currentConvId) { mutableStateOf(false) }
    // 已接微信的对话（1.0.94 第六条）：默认收起输入框，只留「前往微信 ClawBot」告知；
    // 「若仍要对话请点击这里」把输入框请回来（只对本次打开生效，换对话重置）。
    // 三态：null=查询中（首帧先不摆输入框，免得闪一下又换成告知条）、true/false=查过了
    var wechatAttached by remember(currentConvId) {
        mutableStateOf(
            if (groupChat == null && companionMode && dialogueMode == com.freechat.model.DialogueMode.WECHAT) viewModel.wechatAttachedCached(currentConvId ?: "")
            else false
        )
    }
    var wechatInputRevealed by remember(currentConvId) { mutableStateOf(false) }
    var wechatBindTick by remember { mutableStateOf(0) }
    LaunchedEffect(currentConvId, wechatBindTick) {
        val id = currentConvId
        if (id != null && groupChat == null && companionMode && dialogueMode == com.freechat.model.DialogueMode.WECHAT) {
            wechatAttached = viewModel.wechatAttachedNow(id)
        } else {
            wechatAttached = false
        }
    }
    // The prefix model uses measured heights as rows are visited; estimates only fill unseen rows.
    // Unlike item-index progress this remains useful for mixed long replies, images and time labels.
    val quickLocateItems = remember(displayItems, contentWidth, density.density, density.fontScale) {
        val charsPerLine = ((contentWidth.value - 52) / (16f * density.fontScale)).toInt().coerceAtLeast(8)
        displayItems.map { item ->
            when (item) {
                is TimeItem -> ChatScrollItem("time_${item.timestamp}", 32f * density.density, item.timestamp)
                is MsgItem -> {
                    val message = item.msg
                    val lines = message.content.lineSequence().sumOf { (it.length + charsPerLine - 1) / charsPerLine + 1 }
                    val heightDp = (if (message.role == Role.USER) 48f else 84f) +
                        lines * 22f * density.fontScale +
                        (message.imagePaths.size + message.imageUrls.size) * 280f +
                        (if (message.reasoningContent.isNotBlank()) 44f else 0f)
                    ChatScrollItem(message.id, heightDp * density.density, message.timestamp)
                }
                else -> error("Unknown chat row")
            }
        }
    }
    // 「联网搜索」快捷键（完整样式底行，1.0.70；1.0.75 语义升级）：与「新规则」页**同一数据源** ——
    // 每对话覆盖优先、全局兜底；点按写的就是新规则那份 PerConvSettings，两处 UI 永远同值。
    // 首页（还没有对话）显示/写入的是**新对话规则草稿**（pendingNewConvSettings），不许动全局。
    val perConvMap by viewModel.perConvSettings.collectAsState()
    val pendingDraft by viewModel.pendingNewConvSettings.collectAsState()
    val globalWebSearch by viewModel.enableWebSearch.collectAsState()
    val effWebSearch = if (storyProfile != null) storyProfile.enableWebSearch ?: globalWebSearch else if (currentConvId != null) {
        perConvMap[currentConvId]?.enableWebSearch ?: globalWebSearch
    } else {
        pendingDraft.enableWebSearch ?: globalWebSearch
    }
    // 完整底行与简洁「+」菜单共用状态和入口，避免快捷开关改到不同设置层。
    val onToggleWebSearch: () -> Unit = {
        currentConvId?.let { id ->
            val per = perConvMap[id] ?: PerConvSettings()
            if (storyProfile != null) viewModel.updateStory(id, conversations.first { it.id == id }.title,
                storyProfile.copy(enableWebSearch = !effWebSearch))
            else viewModel.updatePerConvSettings(id, per.copy(enableWebSearch = !effWebSearch))
        } ?: run {
            // 首页只写新对话规则草稿，全局开关仍只在设置页修改。
            viewModel.togglePendingWebSearch()
        }
    }
    val onLongPressWebSearch: (() -> Unit)? =
        if (navigationEnabled && currentConvId != null && editingMessageId == null)
            (if (storyProfile != null) onOpenStorySettings else if (groupChat != null) onOpenGroupSettings else onOpenNewRules) else null
    val deepThinkOn = viewModel.effectiveDeepThinking()
    val deepThinkEnabled = viewModel.effectiveLangModel().supportsDeepThinking
    val onToggleDeepThink: () -> Unit = { viewModel.toggleDeepThinking() }
    val onLongPressDeepThink: (() -> Unit)? =
        if (!navigationEnabled || currentConvId == null || editingMessageId != null) null
        else if (storyProfile != null) onOpenStorySettings
        else if (groupChat != null) onOpenGroupSettings
        else if (companionMode) onOpenCharacterSetup else onOpenNewRules
    // 内置对话（「Claude风格助理」）：它是一条**已经存在的对话**，不是「新建对话」——
    // 空的时候给的是一张白页，不是那句「你好，我是 FreeChat」的问候语（那会让它看起来像刚新建的）
    val currentConversationTitle = conversations.firstOrNull { it.id == currentConvId }
        ?.title?.ifBlank { "FreeChat" } ?: "FreeChat"
    val isBuiltInConversation = conversations.any { it.id == currentConvId && it.builtInAssistant.isNotBlank() }
    // 输入框隐藏偏移：实测输入框真实高度（多行时更高，避免顶部漏出）；单行兜底 88dp
    var inputBoxMaxPx by remember { mutableFloatStateOf(with(density) { 88.dp.toPx() }) }

    // IME
    val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val keyboardVisible = imeBottomDp > 0.dp
    // 侧滑页打开时 Chat 页不应响应键盘：侧滑页搜索框唤出键盘，不应滚动/上移聊天文字流
    val paneHasFocus = com.freechat.ui.adaptive.LocalPaneHasFocus.current?.value != false
    val chatKeyboardDp = if (isDrawerOpen || (tablet && !paneHasFocus)) 0.dp else imeBottomDp
    val chatKeyboardVisible = chatKeyboardDp > 0.dp
    LaunchedEffect(keyboardVisible, imeBottomDp) {
        Log.d("FreeChat", "IME debug: bottom=${imeBottomDp.value} visible=$keyboardVisible")
    }

    // 贴底判定的布局常量：三项都是纯常量/纯派生，不随重组漂移，effect 捕获后无过期风险。
    // 静息留白随输入框样式走（1.0.70）：完整样式两行卡比简洁单行高出一截（约 36dp），
    // 留白不跟着加的话最后一条消息会被更高的输入框压住。与 contentPadding.bottom 共用这一个值（单一数据源）
    val restBottomPaddingDp = if (inputStyle == InputStyle.COMPLETE) 152.dp else ChatListRestBottomPaddingDp
    val restBottomPaddingPx = with(density) { restBottomPaddingDp.toPx() }.roundToInt()
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
    // Short tablet chats have no consumed scroll gesture to bring a hidden composer back.
    LaunchedEffect(tablet, listState) {
        if (!tablet) return@LaunchedEffect
        snapshotFlow {
            messages.isEmpty() || (viewportRestored && listState.layoutInfo.totalItemsCount > 0 &&
                !listState.canScrollBackward && !listState.canScrollForward)
        }.collect { cannotScroll -> if (cannotScroll) inputHidden = false }
    }
    val inputOffset = animateFloatAsState(
        targetValue = if (inputHidden && !keyboardVisible) inputBoxMaxPx else 0f,
        animationSpec = if (inputHidden && !keyboardVisible) FreeChatAnimation.inputHideTween
            else FreeChatAnimation.inputShowTween,
        label = "input_offset"
    )
    val greetingProgressState = remember { mutableFloatStateOf(0f) }

    SideEffect {
        greetingProgressState.floatValue = (imeBottomDp.value / 280f).coerceIn(0f, 1f)
    }

    // 录音中：锁定列表滚动 + 侧滑（侧滑由 ChatInput 手势 consume move 自锁定）
    var isRecording by remember { mutableStateOf(false) }
    LaunchedEffect(isDrawerOpen, multiSelect.active, isRecording, currentConvId, messages.isEmpty()) {
        if (isDrawerOpen || multiSelect.active || isRecording || messages.isEmpty()) showQuickLocate = false
    }

    // 键盘弹出：仅当用户本来就停在静息底线（末条消息底边贴着 116dp 留白线）时，文字流才跟随
    // 键盘上移实时滚到底（scrollToItem 瞬时跟随 ime 变化，与键盘/输入框并行、丝滑无停顿）。
    // 上翻阅读历史时不滚，阅读位置与本次会话的视口存档都不被打断。
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

    val streamFollow = rememberStreamingScrollFollow(listState, currentConvId, enabled = viewportRestored)
    // Follow real content growth only while the reader has chosen the conversation end.

    fun viewportPosition(): ChatViewportPosition = ChatViewportPosition(
        listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset,
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == listState.firstVisibleItemIndex }?.key as? String)
    val pageActive = com.freechat.ui.animation.LocalPageActive.current
    LaunchedEffect(listState, currentConvId, pageActive) {
        val id = currentConvId ?: return@LaunchedEffect
        if (!pageActive) return@LaunchedEffect
        snapshotFlow {
            if (viewportRestored && listState.layoutInfo.totalItemsCount > 0) viewportPosition() else null
        }.collect { position ->
            if (position != null) viewModel.chatSessionState.saveViewport(id, position)
        }
    }
    DisposableEffect(listState, currentConvId) {
        val id = currentConvId
        onDispose {
            if (viewportRestored && id != null && listState.layoutInfo.totalItemsCount > 0)
                viewModel.chatSessionState.saveViewport(id, viewportPosition())
        }
    }

    LaunchedEffect(inputBarState, pendingImages.size, quotedMessage, editingMessageId) {
        // 1.1.29.1 ③：进改写必须把输入框拉回来——自动隐藏态下点提示词重写，此前 inputHidden
        // 不翻回，输入框藏在屏外（键盘弹了也没用）；固定/隐藏两态都认这条
        if (inputBarState == InputBarState.PINNED || pendingImages.isNotEmpty() || quotedMessage != null || editingMessageId != null) inputHidden = false
    }

    LaunchedEffect(currentConvId, messages.isEmpty(), scrollTick) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!viewportRestored && !tablet) inputHidden = false

        val lastDisplayIdx = displayItems.lastIndex
        // 从搜索结果跳转：滚到目标消息（屏幕中上位置），随后微闪两下示意
        val targetId = viewModel.pendingScrollTarget.value
        if (targetId != null) {
            val targetIndex = displayItems.indexOfFirst { it is MsgItem && it.msg.id == targetId }
            if (targetIndex >= 0) {
                val centerOffset = (screenHeightPx * 0.4f).roundToInt()
                listState.scrollToItem(targetIndex, -centerOffset)
                viewportRestored = true
                highlightMsgId = targetId
                highlightKeyword = searchQuery.trim().ifBlank { null }
                flashAlpha.snapTo(0f)
                // 亮起 → 停一拍（让用户看清标红关键词）→ 淡出
                flashAlpha.animateTo(1f, FreeChatAnimation.overlayFadeIn)
                delay(700)
                flashAlpha.animateTo(0f, FreeChatAnimation.highlightOut)
                highlightMsgId = null
                highlightKeyword = null
            }
            viewModel.consumeScrollTarget()
        } else if (!viewportRestored) {
            if (savedInitial != null) {
                listState.scrollToItem(savedInitial.resolveIndex(quickLocateItems.map { it.key }), savedInitial.offset)
            } else {
                listState.scrollToItem(lastDisplayIdx, 1_000_000)  // 首次进入默认到最新位置（底部）
            }
            // Initial disclosure measurement can still grow the last row after this scroll.
            // First visits keep bottom intent; restored readers only follow if already at the end.
            streamFollow.onGenerationRequested(savedInitial == null || !listState.canScrollForward)
            viewportRestored = true
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
                viewModel.speakMessage(last.id, SearchPresentation.forDisplay(last.content, last.searchSources, last.answerLinksRequested).answer)
            }
        }
        wasLoading = isLoading
    }

    // ──── 草稿 ────
    val inputDrafts by viewModel.inputDrafts.collectAsState()
    val draftText = inputDrafts[currentConvId.orEmpty()].orEmpty()

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 顶部模糊的渐变终点（px）：endY 默认是无穷大，会导致 easing 曲线只用到 t≈0 一小段、模糊「秒没」。
    // 带高与 endY 同源；标题后方保持完整强度，下方加长渐隐，避免与滚入的文字流混杂。
    val topBandHeight = HazeSpec.topBandHeightDp(statusBarHeightDp)
    val disclosureScroll = remember(listState, currentConvId) {
        DisclosureScrollAnchor(listState, scope)
    }
    DisposableEffect(disclosureScroll) { onDispose { disclosureScroll.onUserScroll() } }
    val disclosureCallbacks = remember(disclosureScroll, streamFollow) {
        DisclosureCallbacks(
            onToggle = { id, height, top, expanding ->
                streamFollow.onDisclosureToggle(id)
                disclosureScroll.callbacks.onToggle(id, height, top, expanding)
            },
            onResize = disclosureScroll.callbacks.onResize,
            onSettled = { id ->
                disclosureScroll.callbacks.onSettled(id)
                streamFollow.onDisclosureSettled(id)
            },
        )
    }
    val scrollDistance = remember(currentConvId) { floatArrayOf(0f) }
    val onUserScroll by rememberUpdatedState<(Float) -> Unit> { delta ->
        disclosureScroll.onUserScroll()
        if (inputBarState != InputBarState.PINNED && pendingImages.isEmpty() && quotedMessage == null && editingMessageId == null) {
            if (scrollDistance[0] * delta < 0f) scrollDistance[0] = 0f
            scrollDistance[0] += delta
            if (scrollDistance[0] > with(density) { 64.dp.toPx() }) inputHidden = true
            else if (scrollDistance[0] < -with(density) { 64.dp.toPx() } || (!listState.canScrollForward && delta < 0f)) inputHidden = false
        }
    }
    val onUserGesture by rememberUpdatedState { streamFollow.onUserGesture() }
    val onUserMoved by rememberUpdatedState { streamFollow.onUserMoved() }
    val composerScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput &&
                    (available.y > 0f && listState.canScrollBackward || available.y < 0f && listState.canScrollForward)) onUserGesture()
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // 高度变化/请求滚动不属于手势。输入框只听主动滑动，不听展开与流式增长。
                if (source == NestedScrollSource.UserInput && consumed.y != 0f) {
                    onUserMoved()
                    onUserScroll(consumed.y)
                }
                return Offset.Zero
            }
        }
    }
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
        TabletChatContent(currentConvId, viewportRestored || messages.isEmpty()) {
        TopBarBackdropSource(hazeState, colors.Background, topBandHeight)
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
            // 1.1.26.8：已存在的空对话（删光消息后回来，currentConvId != null）显示为空聊天，
            // 不再顶替成首页问候语——它是一条真对话（可能带新规则/角色卡），不是 chat 首页。
            if (messages.isEmpty() && currentConvId == null && !companionMode && !isBuiltInConversation) {
                WelcomeGreeting(
                    greeting = greeting,
                    colors = colors,
                    keyboardProgressState = greetingProgressState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topBandHeight)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize().nestedScroll(composerScrollConnection),
                    state = listState,
                    userScrollEnabled = !isRecording,
                    contentPadding = PaddingValues(
                        start = 16.dp + contentGutter, end = 16.dp + contentGutter,
                        top = HazeSpec.newPageContentPaddingDp(statusBarHeightDp),
                        // 与原表达式逐项等价（只是把 静息 / 64 / 键盘 / 全屏输入四项收敛到单一数据源，见文件顶部常量注释）
                        // 静息那项随输入框样式走（完整 152dp / 简洁 116dp，见 restBottomPaddingDp）
                        bottom = restBottomPaddingDp +
                            (if (chatKeyboardVisible) chatKeyboardDp else 0.dp) +
                            (if (pendingImages.isNotEmpty() || quotedMessage != null) ChatCardStripExtraPaddingDp else 0.dp) +
                            with(density) { (fullscreenExtraPaddingPx + disclosureScroll.trailingSpacePx).toDp() }
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        itemsIndexed(displayItems, key = { _, item -> if (item is TimeItem) "time_${item.timestamp}" else (item as MsgItem).msg.id }) { _, item ->
                            // 删除/重排不再瞬移（1.1.26 补缺）：换位+淡出走 tween；fadeIn 关掉——
                            // 首次入场归 messageEntrance 账本，这里叠两层会晃。
                            // 1.1.29.9 工单②：换位滑移只在删除/粉碎窗口开（placementAnimOn）——
                            // 常开时任何高度变化都滑，滚动中消息上下漂=「错位」的病根。
                            Box(Modifier.animateItem(
                                fadeInSpec = null,
                                placementSpec = if (placementAnimOn || shatterBursts.isNotEmpty())
                                    FreeChatAnimation.listPlacementTween else null,
                                fadeOutSpec = FreeChatAnimation.listFadeOutTween
                            ).onGloballyPositioned { c ->
                                // 粉碎起爆点=消息最后一帧矩形（1.1.29.1 ①）；TimeItem 不记
                                if (item is MsgItem) {
                                    msgRects[item.msg.id] = c.boundsInRoot()
                                    msgIsUser[item.msg.id] = item.msg.role == com.freechat.model.Role.USER
                                }
                            }) {
                            when (item) {
                                is TimeItem -> if (editingMessageId != null)
                                    Box(Modifier.editDimVeil(colors)) { TimeDivider(item.timestamp) }
                                else TimeDivider(item.timestamp)
                                else -> {
                                    val msgItem = item as MsgItem
                                    val isFlashTarget = msgItem.msg.id == highlightMsgId && flashAlpha.value > 0f
                                    val flashIsAi = msgItem.msg.role == Role.ASSISTANT
                                    // 所有模式共用一次性入场。流式回复与落库使用同一 ID，历史翻阅不重播。
                                    val entrance = rememberMessageEntrance(msgItem.msg.id, msgItem.msg.timestamp, entranceLedger)
                                    val riseDistance = with(density) { MotionPolicy.MessageRiseDp.dp.toPx() }
                                    val isExiting = msgItem.msg.id in exitingIds.value
                                    Box(Modifier.then(
                                        if (isExiting) Modifier.messageExit(
                                            progress = exitProgressState,
                                            direction = MotionPolicy.exitDirection(msgItem.msg.role == Role.USER)
                                        ) else Modifier
                                    ).messageEntrance(entrance, riseDistance)) {
                                    ChatBubble(
                                        message = if (msgItem.msg.isStreaming) msgItem.msg.copy(
                                            content = if (isGeneratingImage) "" else liveContentState.value, reasoningContent = liveReasoningState.value,
                                            answerLinksRequested = SearchPresentation.linksRequested(messages.lastOrNull { it.role == Role.USER }?.content.orEmpty())
                                        ) else msgItem.msg,
                                        isDark = isDark,
                                        groupChat = groupChat,
                                        onMentionGroupMember = { id ->
                                            viewModel.mentionGroupMember(id)
                                            inputHidden = false
                                            val value = viewModel.getDraft(currentConvId)
                                            mentionInsertion = MentionComposerInsertion(value, value.length, ++mentionRevision)
                                        },
                                        onNudgeGroupMember = viewModel::nudgeGroupMember,
                                        imageGallery = conversationImages,
                                        generationLabel = if (msgItem.msg.isStreaming) generationStatus(viewModel) else null,
                                        liveThinkingMs = if (msgItem.msg.isStreaming) viewModel.thinkingTimeMs.collectAsState().value else 0L,
                                        liveImagePhase = imageGenerationPhase.takeIf { msgItem.msg.isStreaming && isGeneratingImage },
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
                                            // 改写提示词中：其余消息蒙一层半透明纱（1.1.29.9 工单①，
                                            // 撤掉高斯模糊——滚动重绘贵、且糊着难看）；纱不吃手势，翻记录照常
                                            editingMessageId != null && msgItem.msg.id != editingMessageId ->
                                                Modifier.editDimVeil(colors)
                                            else -> Modifier
                                            // 入场动画挂在整条链最外层：与上面的高亮/多选/压暗各占一层，
                                            // alpha 相乘而不是互相覆盖，改写提示词时刚发的那条也不会突然不透明
                                        },
                                        isThinking = false,
                                        showThinking = showThinking && !companionMode,
                                        collapseThinking = collapseThinking,
                                        sessionState = viewModel.chatSessionState,
                                        conversationId = currentConvId,
                                        voiceUnread = msgItem.msg.voiceText != null && !playedVoiceIds.contains(msgItem.msg.id),
                                        showSearchSources = showSearchSources,
                                        disclosureCallbacks = disclosureCallbacks,
                                        onSpeak = {
                                            val m = msgItem.msg
                                            val playingThis = TtsController.playingMessageId.value == m.id
                                            when {
                                                playingThis && TtsController.isPaused.value -> TtsController.resume()
                                                playingThis -> TtsController.pause()
                                                // 用户语音消息 = 录音原声重放（微信式）；AI 语音 = 按所选音色+语态合成
                                                m.voicePath != null -> {
                                                    viewModel.markVoicePlayed(m.id) // 播放即已读
                                                    val path = m.voicePath!!
                                                    scope.launch {
                                                        runCatching {
                                                            val bytes = withContext(Dispatchers.IO) { java.io.File(path).readBytes() }
                                                            TtsController.speakSingle(m.id, bytes, 1f, 1f)
                                                        }
                                                    }
                                                }
                                                else -> viewModel.speakMessage(m.id,
                                                    m.voiceText ?: SearchPresentation.forDisplay(m.content, m.searchSources, m.answerLinksRequested).answer,
                                                    m.voiceStyle ?: "")
                                            }
                                        },
                                        onRegenerate = when {
                                            isLoading || isTyping || exitBusy -> null
                                            groupChat != null && msgItem.msg.failed -> ({
                                                streamFollow.onGenerationRequested(!listState.canScrollForward)
                                                viewModel.retryGroupMessage(msgItem.msg.id)
                                            })
                                            msgItem.msg.sceneVisualization && companionMode && narrativeActions ->
                                                ({
                                                    streamFollow.onGenerationRequested(!listState.canScrollForward)
                                                    val sceneId = msgItem.msg.id
                                                    // 1.1.29.8 工单⑤：重生成只走粒子粉碎（VM 的
                                                    // shatterThenDelete 自带碎裂退场），不再叠播方向滑出
                                                    viewModel.regenerateSceneImage(sceneId)
                                                })
                                            !msgItem.msg.sceneVisualization && msgItem.index == lastAssistantIndex &&
                                                msgItem.msg.id !in sceneLockedIds &&
                                                (!companionMode || narrativeActions) -> ({
                                                    if (!exitBusy) {
                                                        streamFollow.onGenerationRequested(!listState.canScrollForward)
                                                        val aiIndex = msgItem.index
                                                        // 1.1.29.8 工单⑤：重生成只走粒子粉碎（VM 侧
                                                        // shatterThenDelete 整批碎裂），方向滑出退场删除——
                                                        // 两套删除动画叠加是多余的；用户主动删除仍走 playExitThen
                                                        viewModel.regenerate(aiIndex)
                                                    }
                                                })
                                            else -> null
                                        },
                                        onDeleteScene = if (msgItem.msg.sceneVisualization && !isLoading && !isTyping && !exitBusy) ({
                                            pendingSceneDeleteId = msgItem.msg.id
                                            showMultiDeleteConfirm = true
                                        }) else null,
                                        onQuote = if (msgItem.msg.isStreaming) null else ({ viewModel.quoteMessage(msgItem.msg) }),
                                        // 长按语音弹菜单（转文字/引用）；转文字态按消息 id 记账，展开后气泡下显示文本
                                        onVoiceMenu = if (msgItem.msg.voiceText != null || msgItem.msg.voicePath != null)
                                            ({ voiceMenuTarget = msgItem.msg }) else null,
                                        voiceTranscriptVisible = msgItem.msg.id in revealedVoiceIds,
                                        isFavorited = msgItem.msg.favorited,
                                        // 剧情补足的文字流：只作用于 AI 回复，用户消息照旧带气泡
                                        textFlow = textFlowAi,
                                        emphasizeDialogue = companionMode && narrativeActions,
                                        // 只有最后一条用户提示词可改：改历史消息会把后面整段上下文变成无效
                                        onEdit = if (!isLoading && !isTyping && editEnabled && msgItem.msg.id !in sceneLockedIds &&
                                            msgItem.index == lastUserMessageIndex &&
                                            msgItem.msg.content.isNotBlank()
                                        ) {
                                            {
                                                // 1.1.26.9：进入改写 = 原文进输入框 + 弹键盘 + 滚到可见。
                                                // 改写中再点这条提示词无动作——退出只认「取消」钮或改写成功（页面锁定）
                                                if (editingMessageId == null) {
                                                    currentConvId?.let { cid ->
                                                        viewModel.saveDraft(cid, msgItem.msg.content)
                                                    }
                                                    viewModel.setEditingPrompt(msgItem.msg.id)
                                                    inputFocusTick++   // 键盘直接弹出来
                                                    // 焦点跟到被改的提示词：点它时它在屏内，键盘一挤可视区就矮了；
                                                    // 把它顶回可视区，提示词/输入框/键盘尽量同屏
                                                    val lazyIdx = displayItems.indexOfFirst {
                                                        it is MsgItem && it.msg.id == msgItem.msg.id
                                                    }
                                                    if (lazyIdx >= 0) scope.launch { listState.animateScrollToItem(lazyIdx) }
                                                }
                                            }
                                        } else null,
                                        // 多选：三个入口各自带着「点了哪个动作」进多选；整条消息可点=勾选
                                        multiSelectEnabled = multiSelect.active && !msgItem.msg.isStreaming,
                                        onEnterMultiSelect = { action -> viewModel.enterMultiSelect(action, msgItem.msg.id) },
                                        onToggleSelect = { viewModel.toggleMultiSelect(msgItem.msg.id) }
                                    )
                                    }
                                }
                            }
                            }
                        }

                    }
                }
        }

            // 标题后方充分模糊，下缘连输出一起羽化；首屏正文起点不变。
            TopBarBackdrop(hazeState, colors.Background, topBandHeight,
                seamFromEnd = false)

            // ===== 悬浮标题栏按钮，固定悬浮在屏幕顶部，与「FreeChat」同一水平线 =====
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tablet) PaneExpandButton(chat = true) else HeaderIconButton(onClick = onOpenDrawer, enabled = editingMessageId == null) {
                    Icon(Icons.Outlined.Menu, s.openDrawer, tint = colors.TextPrimary, modifier = Modifier.size(24.dp))
                }
                if (tablet) Text(
                    when {
                        multiSelect.active -> s.selectedCount(multiSelect.selectedIds.size)
                        groupChat != null -> currentConversationTitle
                        companionMode -> currentCharacter?.name?.ifBlank { currentConversationTitle } ?: currentConversationTitle
                        else -> currentConversationTitle
                    },
                    modifier = Modifier.weight(1f), color = colors.TextPrimary,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge,
                ) else Spacer(Modifier.weight(1f))
                // 多选头换头走 contentReplacement（1.1.26 动画补缺），不再整组闪换。
                AnimatedContent(
                    targetState = multiSelect.active,
                    transitionSpec = { FreeChatAnimation.contentReplacement() },
                    label = "multiSelectHeader"
                ) { multiSelectActive ->
                if (multiSelectActive) {
                    // 多选态：右上角两个图标按钮收成一个文字按钮，文案 = 进入多选时点的那个动作。
                    // 左边的「已选择 x 项」由 MainActivity 的共享标题画在 x=52dp，这里留空。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderTextButton(
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
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    // 连接微信：微信聊天档拟人对话专属；位置=快速定位键左侧。
                    // 1.0.94：未登录也显示（点进去讲清「云同步互传、仅登录可用」+「登录使用」跳登录页），
                    // 按钮人人可见、能力登录才放行。
                    if (groupChat == null && companionMode && dialogueMode == com.freechat.model.DialogueMode.WECHAT) {
                        HeaderIconButton(onClick = { showWechatBind = true }, enabled = editingMessageId == null && navigationEnabled) {
                            com.freechat.ui.components.WechatIcon(Modifier.size(22.dp))
                        }
                    }
                    HeaderIconButton(onClick = { showQuickLocate = !showQuickLocate }, enabled = messages.isNotEmpty() && !isRecording && editingMessageId == null) {
                        Icon(Icons.Outlined.SwapVert, s.quickLocate,
                            tint = if (showQuickLocate) colors.Primary else colors.TextSecondary,
                            modifier = Modifier.size(22.dp))
                    }
                    // 每对话「调节」按钮：有对话内容时显示；拟人模式即使 0 消息也显示（可进入角色设定编辑）；
                    // 1.1.26.8：已存在的空对话也显示——用户删光消息保留对话就是为了新规则/角色卡
                    if (messages.isNotEmpty() || companionMode || currentConvId != null) {
                        HeaderIconButton(
                            onClick = {
                                if (storyProfile != null) onOpenStorySettings()
                                else if (groupChat != null) onOpenGroupSettings()
                                else if (currentMode == com.freechat.model.ChatMode.COMPANION) onOpenCharacterSetup()
                                else onOpenNewRules()
                            },
                            enabled = editingMessageId == null && navigationEnabled
                        ) {
                            Icon(Icons.Outlined.Tune, if (storyProfile != null) s.storySettings else if (groupChat != null) s.groupSettings else s.newRules,
                                tint = colors.TextSecondary, modifier = Modifier.size(22.dp))
                        }
                    }
                    }
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

            // ===== 第六条（1.0.94）：已接微信的对话默认不给输入框 =====
            // 微信侧看不到 FreeChat 客户端的发言，两边分叉着聊会乱——默认把输入框收起，
            // 讲清「建议前往微信 ClawBot 与该角色对话」；「若仍要对话请点击这里」（斜体蓝字下划线）再请回来。
            // 挂接状态查询中什么都不摆（摆了下一帧就换掉闪得难受）；提示条↔输入框的互换走 expand
            // 曲线（1.1.26 动画补缺），不再整条硬切。
            AnimatedVisibility(
                visible = wechatAttached == true && !wechatInputRevealed,
                enter = FreeChatAnimation.expandEnter(),
                exit = FreeChatAnimation.expandExit(),
                modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 800.dp)
            ) {
                WechatInputNoticeBar(
                    colors = colors,
                    hazeState = hazeState,
                    advancedMaterial = advancedMaterial,
                    onReveal = { wechatInputRevealed = true }
                )
            }
            AnimatedVisibility(
                visible = wechatAttached != null && (wechatAttached != true || wechatInputRevealed),
                enter = FreeChatAnimation.expandEnter(),
                exit = FreeChatAnimation.expandExit(),
                modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 800.dp)
            ) {
            val voiceConversationId = currentConvId
            // 1.1.29.5 追加单：输入框整棵子树豁免触点波纹（全站其余卡片/按钮走全局 PressNode 波纹）
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.foundation.LocalIndication provides
                    com.freechat.ui.animation.rememberMotionIndication(ripple = false)
            ) {
            ChatInput(
                onSend = send@{ text ->
                    if (!latestPageActive.value || !canInteract()) return@send
                    streamFollow.onGenerationRequested(!listState.canScrollForward)
                    val editing = editingMessageId
                    if (editing != null) {
                        // 改写后重发：旧提示词连同它的记忆一起作废，AI 按新提示词重新思考
                        viewModel.setEditingPrompt(null)
                        viewModel.sendEditedMessage(editing, text)
                    } else {
                        sendNewMessage(text)
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
                    // 1.1.26.9：进改写的聚焦不回底部——焦点要停在被改的提示词上（直读 flow 免一帧时差）
                    if (viewModel.editingPromptId.value == null && messages.isNotEmpty() && listState.isStuckToBottom(currentBottomPaddingPx, bottomTolerancePx)) {
                        // lastIdx 表达式保持原样：coerceAtLeast 只在 totalItemsCount==0（首帧未 measure）时生效，
                        // 是「获焦早于首次布局」的兜底，不是错位，不要动。
                        val lastIdx = messages.lastIndex.coerceAtLeast(listState.layoutInfo.totalItemsCount - 1)
                        if (lastIdx >= 0) scope.launch { listState.scrollToItem(lastIdx, 1_000_000) }
                    }
                },
                draftText = draftText,
                // 1.1.30 工单①：首页（无对话）草稿也落——切走再回首页照常显示未发送的提示词
                onDraftChanged = { txt -> viewModel.saveDraft(currentConvId, txt) },
                groupMentionDraft = viewModel.getGroupMentionDraft(currentConvId),
                mentionInsertion = mentionInsertion,
                onMentionQuery = if (groupChat == null) null else { value, caret ->
                    val id = currentConvId
                    if (id != null && viewModel.currentConversationId.value == id) {
                        viewModel.saveDraft(id, value)
                        val query = viewModel.getGroupMentionDraft(id)?.queryAt(caret)
                        if (query != null) {
                            mentionRequest = MentionSelectionRequest(id, value, query, caret)
                            mentionKeyboard?.hide()
                            mentionFocus.clearFocus()
                        }
                    }
                },
                onVoiceInput = voice@{ text, wav ->
                    if (!latestPageActive.value || !canInteract()) return@voice
                    // Selection updates in the VM before the next Compose frame; never deliver into another chat.
                    if (viewModel.currentConversationId.value != voiceConversationId) return@voice
                    if (text.isNotBlank()) {
                        streamFollow.onGenerationRequested(!listState.canScrollForward)
                        // 「允许语音回复」开启（且未连微信）：语音键发出去的就是语音消息（微信式可重放），
                        // 识别文本只进模型上下文做理解；否则照旧转文字发送
                        val asVoiceMessage = currentCharacter?.allowVoiceReply == true &&
                            viewModel.wechatAttachedCached(voiceConversationId ?: "") != true
                        if (asVoiceMessage) {
                            sendNewMessage(text, voicePath = viewModel.saveVoiceRecording(wav))
                        } else {
                            sendNewMessage(text)
                        }
                    }
                    // 认的是**这条对话**的语音识别模型（新规则里可以单独指定），不能再拿全局那份当准 ——
                    // 全局没配但这条对话配了的话，识别本来能成，提示「没模型」就把人挡在门外了
                    else if (!viewModel.hasAsrModel(voiceConversationId)) showNoAsrDialog = true
                    // 有模型但没识别出内容：以前静默没反应（用户以为「松手没发送」），补一句失败提示（1.1.29.2②）
                    else android.widget.Toast.makeText(context, s.voiceRecognizeFailed, android.widget.Toast.LENGTH_SHORT).show()
                },
                onRecognizeVoice = { wav ->
                    if (latestPageActive.value && canInteract() && viewModel.currentConversationId.value == voiceConversationId)
                        viewModel.recognizeVoiceInput(wav)
                    else null
                },
                onRecordingChanged = { isRecording = it },
                voiceSessionKey = voiceConversationId,
                hazeState = hazeState,
                isCompanion = currentMode == com.freechat.model.ChatMode.COMPANION,
                // 动作演绎 / 剧情补足：一次只发一条，发送后右侧键变终止键（点了撤回并退回输入框）
                narrativeSingleSend = currentMode == com.freechat.model.ChatMode.COMPANION &&
                    (storyProfile != null || currentCharacter?.isNarrativeMode() == true),
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
                inputStyle = inputStyle,
                forceImageGen = forceImageGen,
                onToggleForceImageGen = { viewModel.setForceImageGen(!forceImageGen) },
                webSearchOn = effWebSearch,
                onToggleWebSearch = onToggleWebSearch,
                onLongPressGenImage = if (navigationEnabled && editingMessageId == null && com.freechat.data.ModelAccessPolicy.canEdit(selectedVisualModel))
                    ({ onOpenModelEditor(ModelType.VISUAL, selectedVisualModel) }) else null,
                // 1.0.75：首页不给长按（传 null）；对话内长按 → 跳**该对话**的新规则页
                // 1.1.26.9：改写中页面锁定，长按导航一并停用（短按功能钮照常）
                onLongPressWebSearch = onLongPressWebSearch,
                deepThinkOn = deepThinkOn,
                // 1.0.75：首页也能点（写新对话规则草稿）——灰色只剩「模型不支持」一种情形。
                // 能力判定用**对话的生效模型**（新规则可能覆盖了语言模型），别拿全局选中模型当准
                deepThinkEnabled = deepThinkEnabled,
                onToggleDeepThink = onToggleDeepThink,
                onLongPressDeepThink = onLongPressDeepThink,
                editingHint = if (editingMessageId != null) s.editingPromptHint else null,
                onCancelEdit = {
                    // 1.1.26.9：取消 = 退出改写，输入框一并清除不保留（用户拍板）；再点提示词可重进
                    viewModel.setEditingPrompt(null)
                    currentConvId?.let { viewModel.saveDraft(it, "") }
                },
                quotedContent = quotedMessage?.content?.ifBlank { s.imageTag },
                quotedImagePath = quotedMessage?.imagePaths?.firstOrNull(),
                onClearQuote = { viewModel.clearQuote() },
                modifier = Modifier
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
            }
            }

            ChatQuickLocate(showQuickLocate, listState, quickLocateItems, currentConvId,
                onSeek = {
                    disclosureScroll.onUserScroll()
                    streamFollow.onUserGesture()
                    streamFollow.onUserMoved()
                    // Deliberately do not invoke the composer's scroll-to-hide handler.
                }, onDismiss = { showQuickLocate = false },
                modifier = Modifier.align(Alignment.CenterEnd)
                    .offset(y = -chatKeyboardDp / 2)
                    .height(minOf(220.dp, (paneHeight - topBandHeight - restBottomPaddingDp - chatKeyboardDp - 16.dp).coerceAtLeast(48.dp))))

            // ===== "+" 附件菜单（窗口内悬浮，高级材质下真磨砂玻璃糊住背后聊天内容；缩放+淡入淡出，从输入框左上角弹出） =====
            val plusMenuBottomPad = (chatKeyboardDp + 8.dp).coerceAtLeast(36.dp) + 60.dp
            val plusMenuMaxHeight = (paneHeight - topBandHeight - plusMenuBottomPad - 12.dp).coerceAtLeast(48.dp)
            // 透明拦截层：点击外部关闭（淡入淡出）
            AnimatedVisibility(
                visible = showPlusMenu,
            enter = fadeIn(FreeChatAnimation.overlayFadeIn),
            exit = fadeOut(FreeChatAnimation.overlayFadeOut)
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
                // 1.1.29.9 工单④：定位挪到节点 offset 上（别放进缩放内容里）——缩放枢轴按
                // 节点自身算，padding 内容会把枢轴甩到键盘底下、菜单「飞」进来；现在枢轴
                // 正落卡片左下角（=「+」键旁），与收藏页调节菜单同一套「从触发钮长出」。
                //12dp 均匀 padding 只为阴影溢出（Fade/scale 首帧不裁阴影）。
                modifier = Modifier.align(Alignment.BottomStart).offset {
                    IntOffset((8.dp + contentGutter).roundToPx(), -(plusMenuBottomPad - 12.dp).roundToPx())
                },
                enter = FreeChatAnimation.menuEnter(TransformOrigin(0f, 1f)),
                exit = FreeChatAnimation.menuExit(TransformOrigin(0f, 1f))
            ) {
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .width(180.dp)
                        .heightIn(max = plusMenuMaxHeight)
                        .then(
                            if (advancedMaterial) Modifier.frostedGlass(hazeState, isDark, RoundedCornerShape(20.dp), elevation = 6.dp)
                            else Modifier.background(colors.Surface, RoundedCornerShape(20.dp))
                        )
                ) {
                    Column(Modifier.clip(RoundedCornerShape(20.dp)).verticalScroll(rememberScrollState())) {
                        if (photoUploadAllowed) Row(
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
                            // 1.0.69「生成图片」：可勾选的一次性开关 —— 勾上后下一条发送不猜关键词、
                            // 直接走生图（带参考图=图生图），发出即自动取消。只在标准模式给：
                            // 拟人档没有生图链路，放了就是死键。再点一次 = 取消勾选。
                            // 1.0.70：完整样式曾把它挪到底行常驻；1.0.75 又**收回「+」菜单**（用户点名）——
                            // 两种样式统一在菜单里勾选，完整样式选中后底行尾部亮圆形图片图标作已选指示。
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    viewModel.setForceImageGen(!forceImageGen)
                                    showPlusMenu = false
                                }.padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (forceImageGen) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                    null,
                                    tint = colors.Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(s.generateImage, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                            }
                        } else if (narrativeActions) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoading && !isTyping) {
                                    streamFollow.onGenerationRequested(!listState.canScrollForward)
                                    showPlusMenu = false
                                    viewModel.generateCurrentSceneImage()
                                }.padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Image, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(s.generateSceneImage, style = MaterialTheme.typography.bodyMedium,
                                    color = if (isLoading || isTyping) colors.TextTertiary else colors.TextPrimary)
                            }
                        }
                        if (inputStyle == InputStyle.COMPACT) {
                            InputStateMenuItem(
                                label = s.deepThinkingMode,
                                checked = deepThinkOn,
                                enabled = deepThinkEnabled,
                                onToggle = onToggleDeepThink,
                                onLongPress = onLongPressDeepThink,
                                onDismiss = { showPlusMenu = false }
                            )
                            InputStateMenuItem(
                                label = s.webSearch,
                                checked = effWebSearch,
                                onToggle = onToggleWebSearch,
                                onLongPress = onLongPressWebSearch,
                                onDismiss = { showPlusMenu = false }
                            )
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
                enter = fadeIn(FreeChatAnimation.overlayFadeIn),
                exit = fadeOut(FreeChatAnimation.overlayFadeOut)
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
                // 1.1.29.9 工单④：同「+」菜单——定位走节点 offset，缩放枢轴=卡片左下角
                modifier = Modifier.align(Alignment.BottomStart).offset {
                    IntOffset(8.dp.roundToPx(), -(inputBoxMaxPx + 6.dp.roundToPx() - 12.dp.roundToPx()).roundToInt())
                },
                enter = FreeChatAnimation.menuEnter(TransformOrigin(0f, 1f)),
                exit = FreeChatAnimation.menuExit(TransformOrigin(0f, 1f))
            ) {
                Box(
                    modifier = Modifier
                        // 贴着输入框上沿弹出。高度**量的是输入框自己**（inputBoxMaxPx，别处已经量过），
                        // 不写死数字：全屏输入卡片展开时输入框会变高，写死就会让菜单盖在卡片上。
                        .padding(12.dp)
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
                        // 「全屏输入」只在简洁样式有 —— 完整样式全屏与标准输入已合二为一（就地长高），
                        // 菜单里只剩「粘贴」，再给一个「全屏输入」就是骗人的死键
                        if (inputStyle == InputStyle.COMPACT) Row(
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

            // 改写提示词不再用对话框：点气泡 → 原文进输入框、键盘直接弹出、其余消息压暗。
            // 原来的 AlertDialog 已经删掉（`s.editMessage` / `s.editMessageHint` 两个字符串保留，
            // 设置页的「发送示例」附近还在用同一套文案，删字符串会连带影响别处）。

            GroupMentionSheet(visible = mentionRequest != null, members = groupChat?.members.orEmpty(),
                onDismiss = {
                    mentionRequest?.takeIf { it.conversationId == viewModel.currentConversationId.value }?.let {
                        mentionInsertion = MentionComposerInsertion(it.text, it.caret, ++mentionRevision)
                    }
                    mentionRequest = null
                }, onPick = { memberId ->
                    mentionRequest?.takeIf { it.conversationId == viewModel.currentConversationId.value &&
                        it.text == viewModel.getDraft(it.conversationId) }?.let { request ->
                        viewModel.mentionGroupMemberAt(memberId, request.query.start, request.query.end)?.let { draft ->
                            val end = draft.validBindings().firstOrNull { it.start == request.query.start && it.memberId == memberId }?.end
                                ?: request.query.start
                            val caret = end + if (draft.text.getOrNull(end)?.isWhitespace() == true) 1 else 0
                            inputHidden = false
                            mentionInsertion = MentionComposerInsertion(draft.text, caret, ++mentionRevision)
                        }
                    }
                    mentionRequest = null
                }, colors = colors, isDark = isDark, advanced = advancedMaterial, hazeState = hazeState)

            val displayedAttachmentNotice = rememberSheetContent(attachmentNotice.takeIf { it.isNotBlank() }).orEmpty()
            val displayedVoiceTarget = rememberSheetContent(voiceMenuTarget)
            SheetPanel(visible = attachmentNotice.isNotBlank(), onDismiss = viewModel::clearAttachmentNotice,
                title = s.uploadFile, colors = colors, isDark = isDark,
                advancedMaterial = advancedMaterial, hazeState = hazeState,
                confirmLabel = s.confirm, onConfirm = viewModel::clearAttachmentNotice) {
                Text(displayedAttachmentNotice, color = colors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }

            // ===== 长按语音菜单（2026-10-19）：转文字（口播台词/识别文本本就随消息存着，展开即可）/ 引用 =====
            SheetPanel(
                visible = voiceMenuTarget != null,
                onDismiss = { voiceMenuTarget = null },
                title = s.voiceMenuTitle,
                colors = colors,
                isDark = isDark,
                advancedMaterial = advancedMaterial,
                hazeState = hazeState
            ) {
                val voiceTarget = displayedVoiceTarget
                if (voiceTarget != null) {
                    val revealed = voiceTarget.id in revealedVoiceIds
                    SheetActionRow(if (revealed) s.voiceToTextHide else s.voiceToText, colors) {
                        revealedVoiceIds =
                            if (revealed) revealedVoiceIds - voiceTarget.id else revealedVoiceIds + voiceTarget.id
                        voiceMenuTarget = null
                    }
                    SheetActionRow(s.quoteAction, colors) {
                        viewModel.quoteMessage(voiceTarget)
                        voiceMenuTarget = null
                    }
                }
            }

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
                ShareMenuRow(Icons.Filled.Link, s.shareAsLink, colors) { runMultiShareLink() }
            }

            // ===== 多选：批量删除确认（成对扩展 + 会清掉整段记忆，不可逆）=====
            SheetPanel(
                visible = showMultiDeleteConfirm,
                onDismiss = { showMultiDeleteConfirm = false; pendingSceneDeleteId = null },
                title = s.deleteMessage,
                colors = colors,
                isDark = isDark,
                advancedMaterial = advancedMaterial,
                hazeState = hazeState,
                confirmLabel = s.confirm,
                confirmDanger = true,
                onConfirm = {
                    showMultiDeleteConfirm = false
                    val sceneId = pendingSceneDeleteId
                    pendingSceneDeleteId = null
                    if (!exitBusy) {
                        val removed = sceneId?.let { setOf(it) } ?: multiSelect.selectedIds
                        scope.launch {
                            playExitThen(removed) {
                                if (sceneId != null) viewModel.deleteSceneImage(sceneId)
                                else viewModel.applyDeleteToSelection()
                            }
                        }
                    }
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

            // ===== 连接微信（标题栏入口；二维码本地编码；单实例顶替确认）=====
            WechatBindDialog(
                visible = showWechatBind && currentConvId != null,
                convId = currentConvId ?: "",
                colors = colors,
                onDismiss = { showWechatBind = false },
                onBoundChanged = { bound ->
                    viewModel.invalidateWeChatAttachment()
                    wechatBindTick++
                    // 连接微信成功 → 自动关闭「允许语音回复」（该功能不支持连接微信后使用）
                    if (bound && currentConvId != null) viewModel.disableVoiceReplyForWechat(currentConvId!!)
                    // 绑定/解绑都回到默认态：重新连上微信的对话，输入框再次默认收起
                    wechatInputRevealed = false
                },
                onLogin = { showWechatBind = false; onOpenAccount() },
                resolveConvTitle = { id -> conversations.find { it.id == id }?.title?.ifBlank { null } ?: id },
                // 开着「允许语音回复」时须知里多一条告知（连接微信不支持语音、连上自动关闭）
                voiceReplyActive = currentCharacter?.allowVoiceReply == true
            )

            // ===== 多选：在线网页链接生成结果（复制 / 系统分享）=====
            multiShareLinkUrl?.let { url ->
                ShareLinkResultDialog(
                    url = url,
                    onDismiss = { multiShareLinkUrl = null },
                    onCopy = {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("FreeChat share", url))
                        Toast.makeText(context, s.shareLinkCopied, Toast.LENGTH_SHORT).show()
                        multiShareLinkUrl = null
                    },
                    onShare = {
                        scope.launch {
                            withContext(Dispatchers.IO) { context.shareLink(url) }
                            multiShareLinkUrl = null
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
                confirmEnabled = navigationEnabled,
                onConfirm = {
                    showNoAsrDialog = false
                    onOpenAsrEditor()
                }
            ) {
                Text(s.noAsrModelDesc, color = colors.TextSecondary)
            }

            // ===== 1.1.29.1 ①：粒子粉碎层 —— 重生成/重回复删除的既有消息碎成粒子自由落体 =====
            if (shatterBursts.isNotEmpty()) {
                Box(
                    Modifier.fillMaxSize()
                        .onGloballyPositioned { shatterOrigin.value = it.positionInRoot() }
                ) {
                    shatterBursts.forEach { burst ->
                        androidx.compose.runtime.key(burst.id) {
                            com.freechat.ui.components.GravityParticles(
                                palette = if (burst.user) listOf(colors.UserBubble, colors.UserBubbleText)
                                else listOf(colors.AiBubble, colors.AiBubbleText, colors.AiBubbleBorder),
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (burst.rect.left - shatterOrigin.value.x).roundToInt(),
                                            (burst.rect.top - shatterOrigin.value.y).roundToInt()
                                        )
                                    }
                                    .size(
                                        with(density) { burst.rect.width.toDp() },
                                        with(density) { burst.rect.height.toDp() }
                                    ),
                                seed = burst.id
                            )
                            androidx.compose.runtime.LaunchedEffect(burst.id) {
                                kotlinx.coroutines.delay(2400)
                                shatterBursts.remove(burst)
                            }
                        }
                    }
                }
            }
        }
}

/** 1.1.29.1 ①：一蓬粉碎粒子的起爆参数（消息最后一帧矩形 + 气泡配色归属） */
private data class ShatterBurst(val id: Long, val rect: androidx.compose.ui.geometry.Rect, val user: Boolean)

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
    val baseTextStyle = remember(globalFont) {
        TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 35.sp, letterSpacing = 2.5.sp, fontFamily = globalFont)
    }
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = modifier.padding(horizontal = 28.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        val fitScale = remember(lines, randomIndents, baseTextStyle, textMeasurer, maxWidth, density) {
            with(density) {
                greetingFitScale(maxWidth.toPx(),
                    lines.map { textMeasurer.measure(it, baseTextStyle).size.width.toFloat() },
                    lines.indices.map { randomIndents.getOrElse(it) { 0 }.dp.toPx() })
            }
        }
        val textStyle = baseTextStyle.copy(fontSize = 35.sp * fitScale, letterSpacing = 2.5.sp * fitScale)
        val lineHeight = 54.sp * fitScale
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

/** Preserve the normal headline size; only fit lines which would be clipped in the scaled window. */
internal fun greetingFitScale(availableWidthPx: Float, lineWidths: List<Float>, indents: List<Float>): Float =
    lineWidths.indices.minOfOrNull { index ->
        ((availableWidthPx - indents.getOrElse(index) { 0f }).coerceAtLeast(1f) /
            lineWidths[index].coerceAtLeast(1f)).coerceAtMost(1f)
    } ?: 1f

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

/** Stable, localized state from real request/retrieval/stream events; no timer-based stage guesses. */
@Composable
private fun generationStatus(viewModel: ChatViewModel): String {
    val s = LocalStrings.current
    val phase by viewModel.generationPhase.collectAsState()
    return s.generationPhaseLabels[phase].orEmpty()
}

private fun formatElapsed(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> String.format("%.1fs", ms / 1000.0)
    else -> "${ms / 60_000}m${(ms % 60_000) / 1000}s"
}

// 拟人模式列表项：时间分割线 or 消息气泡
private data class TimeItem(val timestamp: Long)
private data class MsgItem(val index: Int, val msg: Message)

/** 与 ChatScreen 的 displayItems 记忆体同一套规则；删除完成后用最新 messages 重算锚点下标要用它。 */
private fun buildChatDisplayItems(
    messages: List<Message>,
    companionMode: Boolean,
    pendingReply: Message?,
    timeGapMs: Long
): List<Any> = buildList {
    messages.forEachIndexed { i, msg ->
        if (companionMode && (i == 0 || msg.timestamp - messages[i - 1].timestamp > timeGapMs)) {
            add(TimeItem(msg.timestamp))
        }
        add(MsgItem(i, msg))
    }
    // Same ID, same item and same reasoning disclosure through stream -> persisted reply.
    // Publishing the finished row before clearing loading must never render duplicate keys.
    if (pendingReply != null && messages.none { it.id == pendingReply.id }) {
        // Scene generation after a long pause needs its divider BEFORE the picture finishes.
        if (companionMode && (messages.isEmpty() || pendingReply.timestamp - messages.last().timestamp > timeGapMs))
            add(TimeItem(pendingReply.timestamp))
        add(MsgItem(-1, pendingReply))
    }
}

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

/**
 * 改写提示词时对其余内容的弱化：半透明纱盖层（1.1.29.9 工单①，替换旧高斯模糊）。
 * 纵向各扩半个条距、横向扩进 16dp 内容留白——消息间的缝隙一并盖住，免得露出未蒙底色成条纹。
 * 纱色分深浅（1.2.4 返修）：浅色主题保留白纱；深色主题用页面底色纱——白纱在深色下整屏
 * 发白，且底色纱在缝隙处与底色逐像素相等、天衣无缝。
 */
private fun Modifier.editDimVeil(colors: com.freechat.ui.theme.FreeChatColors): Modifier {
    val veil = if (colors.Background.luminance() < 0.5f) colors.Background.copy(alpha = 0.58f)
        else Color.White.copy(alpha = 0.58f)
    return this.drawWithContent {
        drawContent()
        val gapHalf = 8.dp.toPx()
        val overhang = 16.dp.toPx()
        drawRect(
            veil,
            topLeft = Offset(-overhang, -gapHalf),
            size = Size(size.width + overhang * 2f, size.height + gapHalf * 2f)
        )
    }
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

/**
 * 已接微信对话的输入区替代条（1.0.94 第六条）：
 * 「微信无法获取 FreeChat 客户端聊天记录……建议前往微信 ClawBot 与该角色对话」，
 * 末句「若仍要对话请点击这里」斜体+蓝+下划线明示可点——点了把输入框请回来（本次打开内有效）。
 * 观感对齐输入框卡片（同款 frosted 圆角卡），内容是告知不是输入。
 */
@Composable
private fun WechatInputNoticeBar(
    colors: com.freechat.ui.theme.FreeChatColors,
    hazeState: HazeState,
    advancedMaterial: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(22.dp), fallback = colors.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            s.wechatInputNotice,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 17.sp),
            color = colors.TextSecondary
        )
        Text(
            s.wechatInputReveal,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Serif, fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                textDecoration = TextDecoration.Underline
            ),
            // 用户点名「蓝色」：主题主色是绿的，链接按需求给固定蓝
            color = Color(0xFF4A8CF7),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onReveal() }.padding(vertical = 2.dp)
        )
    }
}
