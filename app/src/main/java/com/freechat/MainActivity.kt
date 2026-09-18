package com.freechat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.res.ResourcesCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freechat.data.TtsController
import com.freechat.i18n.AppLanguage
import com.freechat.i18n.LocalStrings
import com.freechat.i18n.LocaleManager
import com.freechat.i18n.buildStrings
import com.freechat.model.ThemeMode
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.components.DrawerContent
import kotlin.math.roundToInt
import com.freechat.ui.components.SheetPanel
import com.freechat.model.ColorTheme
import com.freechat.ui.screens.ChangelogScreen
import com.freechat.ui.screens.FavoritesDetailScreen
import com.freechat.ui.screens.FavoritesScreen
import com.freechat.model.FavoriteItem
import com.freechat.ui.screens.NewRulesScreen
import com.freechat.ui.screens.NewChatModeScreen
import com.freechat.ui.screens.CharacterSetupScreen
import com.freechat.model.ChatMode
import com.freechat.model.CharacterProfile
import com.freechat.model.Conversation
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.ui.screens.ChatScreen
import com.freechat.ui.screens.SettingsScreen
import com.freechat.ui.screens.VoiceDebugScreen
import com.freechat.ui.screens.ModelEditorScreen
import com.freechat.ui.screens.AccountScreen
import com.freechat.ui.screens.AgreementScreen
import com.freechat.ui.screens.AgreementGateDialog
import com.freechat.ui.theme.FreeChatTheme
import com.freechat.ui.theme.Aurora
import com.freechat.ui.theme.LiquidBackdrop
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.LocalGlobalFontFamily
import com.freechat.ui.theme.LocalLiquidClock
import com.freechat.ui.theme.LocalLiquidFrame
import com.freechat.ui.theme.LocalLiquidMode
import com.freechat.ui.theme.LocalLiquidPageShift
import com.freechat.ui.theme.LiquidPageShift
import com.freechat.ui.theme.LocalLiquidPalette
import com.freechat.ui.theme.liquidPalette
import com.freechat.ui.theme.rememberLiquidClock
import com.freechat.ui.theme.pageBackground
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import com.freechat.ui.theme.resolveColors
import com.freechat.sync.SyncEngine
import com.freechat.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private enum class Screen { CHAT, SETTINGS, VOICE_DEBUG, CHANGELOG, NEW_RULES, NEW_CHAT_MODE, CHARACTER_SETUP, LEARNING, MODEL_EDITOR, AGREEMENT, FAVORITES, FAVORITE_DETAIL, ACCOUNT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 冷启动后首次渲染中文时，大体积字体（NotoSansCJK 10.7MB）的 glyph 首次 rasterize 会阻塞主线程
        // 产生 150-350ms 长帧（侧滑/问候语动画恰在此刻触发，显得掉帧）。后台预热字体文件加载，
        // 把字体的磁盘读取 + 解析移出主线程，后续渲染直接走系统字体缓存。
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                ResourcesCompat.getFont(this@MainActivity, R.font.notosanscjkmedium)
                ResourcesCompat.getFont(this@MainActivity, R.font.hysongyunlanghei)
            }
        }

        // 通知权限（Android 13+）：首次进入申请，允许后 AI 后台回复才能发系统通知
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        // 前台退到后台时暂停朗读（保留进度，回前台后点击继续）
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                TtsController.pause()
            }
        })

        setContent {
            val chatViewModel: ChatViewModel = viewModel()

            // 前台/后台切换：退后台且 AI 思考中时启动前台服务保活；回前台停止服务并清通知
            // 顺带当同步的开关：这轮不做后台同步（没引 WorkManager），所以「什么时候同步」
            // 就是「什么时候回到前台」+「什么时候离开前台」这两个点。
            // 离开时那一下是尽力而为 —— 进程随时可能被回收，但待推队列落了盘，丢不了。
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            chatViewModel.onAppForegroundChanged(true)
                            SyncEngine.syncSoon(0)
                        }
                        Lifecycle.Event.ON_STOP -> {
                            chatViewModel.onAppForegroundChanged(false)
                            SyncEngine.syncSoon(0)
                        }
                        else -> {}
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            val themeMode by chatViewModel.themeMode.collectAsState()
            val colorTheme by chatViewModel.colorTheme.collectAsState()
            val fontSize by chatViewModel.fontSize.collectAsState()
            val useSystemFont by chatViewModel.useSystemFont.collectAsState()
            val systemDarkTheme by chatViewModel.systemDarkTheme.collectAsState()
            val advancedMaterial by chatViewModel.advancedMaterial.collectAsState()
            val liquidBackdrop by chatViewModel.liquidBackdrop.collectAsState()
            val currentMode by chatViewModel.currentMode.collectAsState()
            val currentCharacter by chatViewModel.currentCharacter.collectAsState()
            val hasAgreedTerms by chatViewModel.hasAgreedTerms.collectAsState()
            val conversations by chatViewModel.conversations.collectAsState()
            val currentConvId by chatViewModel.currentConversationId.collectAsState()
            val searchQuery by chatViewModel.searchQuery.collectAsState()
            val searchResults by chatViewModel.searchResults.collectAsState()
            val favorites by chatViewModel.favorites.collectAsState()
            // 多选状态：标题栏「已选择 x 项」也读它
            val multiSelect by chatViewModel.multiSelect.collectAsState()
            val favoriteConvIds = remember(favorites) { favorites.map { it.conversation.id }.toSet() }

            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.DARK_OLED -> true
                ThemeMode.LIGHT -> false
            }
            val colors = resolveColors(themeMode, colorTheme, systemDarkTheme)

            // 流动炫彩配色：与 resolveColors 用同一套「纯黑」判定
            // （DARK_OLED 恒纯黑；SYSTEM 时只有「系统当前是深色」且用户选了「黑色」才算纯黑）
            val systemDarkNow = isSystemInDarkTheme()
            val isOled = themeMode == ThemeMode.DARK_OLED ||
                (themeMode == ThemeMode.SYSTEM && systemDarkNow && systemDarkTheme)
            val liquidColors = remember(colorTheme, isDark, isOled) {
                liquidPalette(colorTheme, isDark, isOled)
            }
            // 深色 / 纯黑主题下不渲染流动炫彩。
            // 理由有两条，都是硬理由：
            //   观感 —— 那两套底色本来就该是「一块安静的黑」，大面积柔光会把暗部统统推开，
            //           最后得到一片灰雾，远不如原样干净；
            //   纯黑 —— OLED 的省电来自「大面积像素不发光」，而流动炫彩恰好是全屏发光，
            //           开着它等于把这个特性整个抹掉。
            // 开关本身保留、状态也不改（回到浅色主题立刻恢复），只是这里不给它生效：
            // 于是各页面照旧铺原来的深色/黑色底，整条链路退化成加这个功能之前的样子。
            val liquidActive = liquidBackdrop && !isDark && !isOled
            // 全 App 共用的动画时钟：整个流动炫彩只有这一个帧回调。
            // 关着的时候连时钟都不建 —— 不给关闭状态留一帧的额外开销
            val liquidClock: State<Long>? = if (liquidActive) rememberLiquidClock() else null
            // 根内容区的矩形（屏幕矩形）。流动背景的几何按参照矩形算，所以「根背景」和
            // 「各页标题栏自己画的那块裁片」必须用**同一个**矩形 —— 在这里量一次、往下传，
            // 比让各自量自己可靠（标题栏只有一条，量自己就成了一幅压扁的渐变）。
            val liquidFrame = remember { androidx.compose.runtime.mutableStateOf(Rect.Zero) }

            var currentScreen by remember { mutableStateOf(Screen.CHAT) }
            var drawerOpen by remember { mutableStateOf(false) }
            var isDragging by remember { mutableStateOf(false) }
            // 「点对话后等竖条滑完再关抽屉」那笔待办（见 onSelectConversation）
            var pendingDrawerClose by remember { mutableStateOf<Job?>(null) }
            var settingsRowBounds by remember { mutableStateOf<Rect?>(null) }
            var favoritesRowBounds by remember { mutableStateOf<Rect?>(null) }
            var drawerNewChatRect by remember { mutableStateOf<Rect?>(null) }

            // ===== 页面切换动画状态 =====
            var pendingRevealScreen by remember { mutableStateOf<Screen?>(null) }

            val density = LocalDensity.current
            val config = LocalConfiguration.current
            val drawerWidthDp = config.screenWidthDp.dp * 0.9f
            val drawerWidthPx = with(density) { drawerWidthDp.toPx() }
            val overlapPx = with(density) { 12.dp.toPx() }
            // 防缝护栏：Chat 层左边缘恒比抽屉层右边缘再退 1dp，任意进度下两页都是「重叠」而非「裂缝」
            val seamGuardPx = with(density) { 1.dp.toPx() }
            val drawerBlurMaxPx = with(density) { 3.dp.toPx() }  // 抽屉滑入时 Chat 内容的最大高斯模糊（像素）：减半以减轻侧滑动画每帧的 GPU 模糊合成压力
            // 抽屉滑入模糊的**量化缓存**（1.0.49 性能）：
            // BlurEffect 每 new 一个就是一个新的原生 RenderEffect —— 半径哪怕只差 0.001px，
            // 框架也得重建一次全屏离屏模糊。上面那个 graphicsLayer 的 lambda 每帧都会跑，
            // 照原样写就是「侧滑期间每帧申请一个全屏离屏层」，手指跟着掉帧。
            // 把进度切成 33 档复用实例：半径步进 ~0.09dp，肉眼分不出，
            // 但拖动全程最多只重建 33 次，静止时更是恒等于同一个对象（图层不再失效）。
            val blurSteps = 32
            val blurEffectCache = remember(drawerBlurMaxPx) {
                Array(blurSteps + 1) { i ->
                    if (i == 0) null else {
                        val r = drawerBlurMaxPx * i / blurSteps
                        BlurEffect(r, r, TileMode.Decal)
                    }
                }
            }

            val drawerOffset = remember { Animatable(0f) }
            val coroutineScope = rememberCoroutineScope()
            val chatHazeState = rememberHazeState()
            // 侧滑页的 Haze 状态也提到这一层：那三个对话弹层画在根 Box 上，
            // 要糊的是侧滑页的列表内容，得跟侧滑页共用同一个 state 才采得到
            val drawerHazeState = rememberHazeState()

            // ──── 层的实时平移量 ────
            // 页面里已经不再有背景副本（1.0.50 拆掉了，见 hazeBackground：源节点的绘制会被 Haze 冻住），
            // 所以这段**当前没有消费者**，只把每层的位移以 lambda 形式挂在 CompositionLocal 上备用。
            // ⚠️ 一旦再有谁要在层内画「按屏幕坐标定位」的东西，就得用它反向抵消层位移；
            // 而且要读这个 lambda（而不是读「层上一帧写下的数字」），否则那一格永远不会失效重画。
            val chatShift: () -> Float = {
                (drawerOffset.value - overlapPx - seamGuardPx).coerceAtLeast(0f)
            }
            val drawerShift: () -> Float = { -drawerWidthPx + drawerOffset.value - overlapPx }
            // key 里带上被 lambda 捕获的那几个常量：它们随密度/屏宽变，变了就得换一份新的
            // （抽屉动画 drawerOffset 不在此列 —— lambda 是**调用时**才读它的）
            val liquidChatShift = remember(overlapPx, seamGuardPx) { LiquidPageShift(chatShift) }
            val liquidDrawerShift = remember(drawerWidthPx, overlapPx) { LiquidPageShift(drawerShift) }

            // ──── 键盘自动收起：侧滑打开抽屉时 ────
            val keyboardController = LocalSoftwareKeyboardController.current
            LaunchedEffect(drawerOpen) {
                if (drawerOpen) {
                    keyboardController?.hide()
                    // 抽屉又被打开了（包括用户手滑又拉开）：撤销「等竖条滑完就关」那笔待办，
                    // 否则 420ms 后会有一只旧协程把刚打开的抽屉关掉
                    pendingDrawerClose?.cancel()
                    pendingDrawerClose = null
                }
            }

            // 切换到非 Chat 页面（设置/语音调试）时暂停朗读
            LaunchedEffect(currentScreen) {
                if (currentScreen != Screen.CHAT) {
                    TtsController.pause()
                }
            }

            // ──── 新对话过渡动画 ────
            var newChatRevealTrigger by remember { mutableIntStateOf(0) }
            var characterSetupInitial by remember { mutableStateOf<CharacterProfile?>(null) }
            var learningUpdating by remember { mutableStateOf(false) }
            var modelEditorType by remember { mutableStateOf<ModelType?>(null) }
            var modelEditorEditing by remember { mutableStateOf<ModelInfo?>(null) }
            var modelEditorFromChat by remember { mutableStateOf(false) }
            var favoriteDetailItem by remember { mutableStateOf<FavoriteItem?>(null) }

            // ──── 侧滑页那三个对话弹层（重命名 / 删除 / 批量删除）────
            // 状态放在**这一层**而不是 DrawerContent 里：弹层要铺满整屏，只能挂在下面根 Box 的
            // 末尾；而侧滑层那一格只有 90% 屏宽（还带着 graphicsLayer 平移），装不下一个整屏弹层。
            // 抽屉只负责"举手"（onAskXxx），弹层在这里画。
            var drawerRenameTarget by remember { mutableStateOf<Conversation?>(null) }
            var drawerRenameText by remember { mutableStateOf("") }
            var drawerDeleteTarget by remember { mutableStateOf<Conversation?>(null) }
            var drawerBatchDeleteTargets by remember { mutableStateOf<List<Conversation>?>(null) }
            /** 自增一次 = 让侧滑页把多选收回（多选是它的内部状态，只能这样通知它） */
            var drawerExitSelectKey by remember { mutableIntStateOf(0) }
            // 抽屉一关，这三个弹层一起收回 —— 跟抽屉里那些状态一个道理：面板是常驻组合、只做平移动画，
            // 不清的话下次拉开还会原样浮出来，而且已经对着过期的那条对话了
            LaunchedEffect(drawerOpen) {
                if (!drawerOpen) {
                    drawerRenameTarget = null
                    drawerDeleteTarget = null
                    drawerBatchDeleteTargets = null
                }
            }

            // ──── 弹层专用模糊源（整屏）────
            // 侧滑页自己的 drawerHazeState 的源就是那张对话列表，宽度只有 90% 屏宽白。
            // 弹层的背景糊 + 卡片本身都采它，于是：卡片在侧滑页右边缘处裂成两半、
            // 左边那条 10% 的聊天页整个糊不到（1.0.49 报的「重命名卡片显示异常」）。
            // 这一层把「背景 + Chat 层 + 遮罩 + 侧滑层」整块抓成一张全屏图，
            // 弹层要糊就整屏一起糊，缝和色差一起消失。
            //
            // 只在有侧滑页弹层打开时才挂 hazeSource：没有消费者的时候挂了也是白录一整屏
            // （Haze 的源节点不看 blurEnabled，画了就录），白给 GPU 加活。
            val overlayHazeState = rememberHazeState()
            val drawerSheetOpen = drawerRenameTarget != null || drawerDeleteTarget != null ||
                drawerBatchDeleteTargets != null

            // 驱动动画 — 打开用 Spring，关闭用快速 Tween 消除后摇
            androidx.compose.runtime.LaunchedEffect(drawerOpen, isDragging) {
                if (!isDragging) {
                    if (drawerOpen) {
                        drawerOffset.animateTo(drawerWidthPx, FreeChatAnimation.drawerOpenSpring)
                    } else {
                        drawerOffset.animateTo(0f, FreeChatAnimation.drawerCloseTween)
                    }
                }
            }

            // 返回键导航栈
            BackHandler(enabled = currentScreen == Screen.SETTINGS || currentScreen == Screen.VOICE_DEBUG || currentScreen == Screen.CHANGELOG || currentScreen == Screen.NEW_RULES || currentScreen == Screen.NEW_CHAT_MODE || currentScreen == Screen.CHARACTER_SETUP || currentScreen == Screen.MODEL_EDITOR || currentScreen == Screen.AGREEMENT || currentScreen == Screen.FAVORITES || currentScreen == Screen.FAVORITE_DETAIL || currentScreen == Screen.ACCOUNT || drawerOpen || (currentScreen == Screen.CHAT && multiSelect.active)) {
                when {
                    currentScreen == Screen.VOICE_DEBUG -> {
                        currentScreen = Screen.SETTINGS
                    }
                    currentScreen == Screen.CHANGELOG -> {
                        currentScreen = Screen.SETTINGS
                    }
                    currentScreen == Screen.SETTINGS -> {
                        currentScreen = Screen.CHAT; drawerOpen = true
                    }
                    currentScreen == Screen.NEW_RULES -> {
                        currentScreen = Screen.CHAT
                    }
                    currentScreen == Screen.CHARACTER_SETUP -> {
                        currentScreen = Screen.NEW_CHAT_MODE
                    }
                    currentScreen == Screen.NEW_CHAT_MODE -> {
                        currentScreen = Screen.CHAT
                    }
                    currentScreen == Screen.MODEL_EDITOR -> {
                        currentScreen = if (modelEditorFromChat) Screen.CHAT else Screen.SETTINGS
                    }
                    currentScreen == Screen.AGREEMENT -> {
                        currentScreen = Screen.SETTINGS
                    }
                    currentScreen == Screen.ACCOUNT -> {
                        currentScreen = Screen.CHAT
                        drawerOpen = true
                    }
                    currentScreen == Screen.FAVORITE_DETAIL -> {
                        currentScreen = Screen.FAVORITES
                    }
                    currentScreen == Screen.FAVORITES -> {
                        currentScreen = Screen.CHAT
                        drawerOpen = true
                    }
                    drawerOpen -> { drawerOpen = false }
                    // 多选退出放最后 = 优先级最低：抽屉开着时返回键先关抽屉（看得见什么就关什么）
                    currentScreen == Screen.CHAT && multiSelect.active -> chatViewModel.exitMultiSelect()
                }
            }

            // 观察语言设置变化
            val appLanguage by chatViewModel.appLanguage.collectAsState()
            val currentStrings = buildStrings(LocaleManager.resolveLocale(appLanguage))

            // 语言变更时重新应用 Locale
            LaunchedEffect(appLanguage) {
                LocaleManager.applyLocale(this@MainActivity, appLanguage)
            }

            androidx.compose.runtime.CompositionLocalProvider(LocalStrings provides currentStrings, LocalAdvancedMaterial provides advancedMaterial, LocalLiquidMode provides liquidActive, LocalLiquidPalette provides liquidColors, LocalLiquidClock provides liquidClock, LocalLiquidFrame provides liquidFrame) {
            FreeChatTheme(themeMode = themeMode, colorTheme = colorTheme, fontSize = fontSize, useSystemFont = useSystemFont, systemDarkTheme = systemDarkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.Background)
                        .onSizeChanged { liquidFrame.value = Rect(0f, 0f, it.width.toFloat(), it.height.toFloat()) }
                ) {
                    // =============================================
                    // 「弹层模糊源」整块：背景 + Chat 层 + 遮罩 + 侧滑层，全部装在这一格。
                    //
                    // 这一格本身**不带任何位移**（它就是个 fillMaxSize 的 Box，里面的层各自平移），
                    // 所以 hazeSource 抓到的就是「屏幕上现在长什么样」，坐标天然对得上 ——
                    // 这正是它比「给会平移的 Chat 层再挂一个源」可靠的地方。
                    // 内容缩进维持原样没动（这几百行嵌套很深，重排一遍只会给 diff 添噪声）。
                    // =============================================
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (drawerSheetOpen) Modifier.hazeSource(overlayHazeState) else Modifier)
                    ) {
                    // =============================================
                    // 0. 流动炫彩背景层 — 不参与任何位移，恒钉在屏幕上
                    //    位置：根 Box 之上、Chat 层之下。侧滑时内容在它上面滑过，
                    //    露出的永远是同一幅背景 —— 这就是「抽屉滑动时背景不跟着走」的做法。
                    //    各页面在流动炫彩下**一律透明**（pageBackground 什么都不铺），隔着它看，
                    //    所以页面平移多少，背景都纹丝不动。唯一例外是高级材质下的模糊源节点
                    //    （hazeBackground）—— 它必须自己画一份，两份共用同一个全局动画时钟。
                    // =============================================
                    if (liquidActive) {
                        LiquidBackdrop(palette = liquidColors, clock = liquidClock)
                    }

                    // =============================================
                    // 1. Chat 主内容层 — graphicsLayer 右移（只重绘不重组）
                    // =============================================

                    // 这一层的位移补偿：层内的模糊源节点要按「屏幕坐标」画背景副本，
                    // Chat 层右移多少，层内的背景副本就反向挪回多少（见 LiquidPageShift）。
                    // 不补偿的话副本跟着层一起被搬走，接缝两侧露的是同一幅渐变的不同两段，
                    // 看着就是两块颜色不同的板子拼在一起、交界处一道裂痕。
                    CompositionLocalProvider(LocalLiquidPageShift provides liquidChatShift) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    // 抽屉层右边缘 R = -W + offset - overlapPx + W = offset - overlapPx
                                    // Chat 层左边缘必须与 R 同源并再退 seamGuardPx：L = offset - overlapPx - seamGuardPx
                                    // ⇒ 重叠 R - L = seamGuardPx ≥ 0，任意进度都不露根背景
                                    //（原式 (W-O)*p 在 p<1 时恒小于 R，会张开 O(1-p) 宽的实体裂缝，缝里露出根 Box 的纯背景）
                                    // coerceAtLeast(0f)：抽屉尚未滑出屏幕时不让 Chat 被左移，否则屏幕右缘会露 1dp 根背景
                                    // 层内副本用的是**同一个** chatShift（就是上面那个 lambda），不另算
                                    translationX = chatShift()
                                    // 高级材质：抽屉滑入时对 Chat 页内容做高斯模糊虚化，随侧滑动画逐渐加重（替代暗色遮罩）
                                    if (advancedMaterial) {
                                        // 量化到 33 档再取缓存实例（见上面 blurEffectCache 的说明）：
                                        // 半径不变时拿到的是**同一个** BlurEffect，图层不必重建。
                                        val blurProgress = (drawerOffset.value / drawerWidthPx).coerceIn(0f, 1f)
                                        renderEffect = blurEffectCache[(blurProgress * blurSteps + 0.5f).toInt()]
                                    }
                                }
                                .pointerInput(currentScreen) {
                                    if (currentScreen == Screen.CHAT) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { isDragging = true; coroutineScope.launch { drawerOffset.snapTo(drawerOffset.value) } },
                                            onDragEnd = {
                                                isDragging = false
                                                val threshold = drawerWidthPx * FreeChatAnimation.DRAWER_SWIPE_THRESHOLD
                                                if (drawerOpen) {
                                                    if (drawerOffset.value < drawerWidthPx - threshold) drawerOpen = false
                                                } else {
                                                    if (drawerOffset.value > threshold) drawerOpen = true
                                                }
                                            },
                                            onDragCancel = { isDragging = false },
                                            onHorizontalDrag = { _, dragAmount ->
                                                coroutineScope.launch {
                                                    drawerOffset.snapTo(
                                                        (drawerOffset.value + dragAmount).coerceIn(0f, drawerWidthPx)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                        ) {
                            // ===== 圆形展开动画过渡层 =====
                            // 当 pendingRevealScreen != null 时，先播圆形展开动画，再真正切换 screen
                            val effectiveScreen = pendingRevealScreen ?: currentScreen
    
    AnimatedContent(
        targetState = effectiveScreen,
        transitionSpec = {
            // Bug 5：统一过渡规格，避免分支导致首帧额外计算
            (fadeIn(FreeChatAnimation.pageFadeInFast) +
             scaleIn(initialScale = 0.97f, animationSpec = tween(280, easing = FreeChatAnimation.iosEaseOut))) togetherWith
            (fadeOut(FreeChatAnimation.pageFadeOutFast) +
             scaleOut(targetScale = 0.97f, animationSpec = tween(200, easing = FreeChatAnimation.iosEaseIn)))
        },
        label = "screen_reveal"
    ) { screen ->
        when (screen) {
            Screen.CHAT -> ChatScreen(
                viewModel = chatViewModel, isDark = isDark,
                onOpenDrawer = { drawerOpen = true },
                onOpenNewRules = { currentScreen = Screen.NEW_RULES },
                revealTrigger = newChatRevealTrigger,
                onRevealComplete = { newChatRevealTrigger = 0 },
                onNewChatReveal = { _ ->
                    newChatRevealTrigger++
                },
                onNewChat = { newChatRevealTrigger = 0; currentScreen = Screen.NEW_CHAT_MODE },
                onOpenCharacterSetup = {
                    characterSetupInitial = chatViewModel.currentCharacter.value
                    currentScreen = Screen.CHARACTER_SETUP
                },
                hazeState = chatHazeState,
                isDrawerOpen = drawerOpen,
                onOpenAsrEditor = {
                    modelEditorType = ModelType.ASR
                    modelEditorEditing = null
                    modelEditorFromChat = true
                    currentScreen = Screen.MODEL_EDITOR
                }
            )
            Screen.SETTINGS -> SettingsScreen(
                viewModel = chatViewModel, isDark = isDark,
                onBack = {
                    currentScreen = Screen.CHAT
                    drawerOpen = true
                    pendingRevealScreen = null
                },
                onOpenVoiceDebug = {
                    currentScreen = Screen.VOICE_DEBUG
                },
                onOpenChangelog = {
                    currentScreen = Screen.CHANGELOG
                },
                onOpenModelEditor = { type, editing ->
                    modelEditorType = type
                    modelEditorEditing = editing
                    modelEditorFromChat = false
                    currentScreen = Screen.MODEL_EDITOR
                },
                onOpenAgreement = {
                    currentScreen = Screen.AGREEMENT
                }
            )
            // 返回回侧栏（跟收藏一致）—— 账号入口在抽屉里，不在了设置页
            Screen.ACCOUNT -> AccountScreen(
                onBack = {
                    currentScreen = Screen.CHAT
                    drawerOpen = true
                }
            )
            Screen.VOICE_DEBUG -> VoiceDebugScreen(
                viewModel = chatViewModel, isDark = isDark,
                onBack = {
                    currentScreen = Screen.SETTINGS
                }
            )
            Screen.CHANGELOG -> ChangelogScreen(
                onBack = { currentScreen = Screen.SETTINGS }
            )
            Screen.MODEL_EDITOR -> ModelEditorScreen(
                viewModel = chatViewModel,
                modelType = modelEditorType ?: ModelType.LANGUAGE,
                editing = modelEditorEditing,
                isDark = isDark,
                onBack = { currentScreen = if (modelEditorFromChat) Screen.CHAT else Screen.SETTINGS }
            )
            Screen.AGREEMENT -> AgreementScreen(
                onBack = { currentScreen = Screen.SETTINGS }
            )
            Screen.FAVORITES -> FavoritesScreen(
                viewModel = chatViewModel,
                isDark = isDark,
                onBack = {
                    currentScreen = Screen.CHAT
                    drawerOpen = true
                    pendingRevealScreen = null
                },
                onOpenDetail = { item ->
                    favoriteDetailItem = item
                    currentScreen = Screen.FAVORITE_DETAIL
                }
            )
            Screen.FAVORITE_DETAIL -> {
                val detailItem = favoriteDetailItem
                if (detailItem != null) {
                    FavoritesDetailScreen(
                        viewModel = chatViewModel,
                        item = detailItem,
                        isDark = isDark,
                        onBack = { currentScreen = Screen.FAVORITES },
                        onUnfavorite = {
                            // 详情页展示的是「一整段连续收藏」，取消收藏也应该整段清掉，
                            // 只翻段首那一条的话回到列表页那一行不会消失、只是原地换成下一条
                            chatViewModel.unfavoriteItems(listOf(detailItem))
                            currentScreen = Screen.FAVORITES
                        },
                        onOpenOriginal = {
                            chatViewModel.switchToConversation(detailItem.conversation)
                            chatViewModel.requestScrollToMessage(detailItem.message.id)
                            currentScreen = Screen.CHAT
                            drawerOpen = false
                        }
                    )
                }
            }
            Screen.NEW_RULES -> NewRulesScreen(
                viewModel = chatViewModel,
                convId = currentConvId ?: "",
                isDark = isDark,
                onBack = { currentScreen = Screen.CHAT }
            )
            Screen.NEW_CHAT_MODE -> NewChatModeScreen(
                isDark = isDark,
                onBack = { currentScreen = Screen.CHAT },
                onSelectStandard = {
                    chatViewModel.newConversation(ChatMode.STANDARD, null)
                    currentScreen = Screen.CHAT
                },
                onSelectCompanion = {
                    characterSetupInitial = null
                    currentScreen = Screen.CHARACTER_SETUP
                }
            )
            Screen.CHARACTER_SETUP -> CharacterSetupScreen(
                viewModel = chatViewModel,
                isDark = isDark,
                onBack = { currentScreen = if (characterSetupInitial != null) Screen.CHAT else Screen.NEW_CHAT_MODE },
                onCreate = { profile ->
                    if (characterSetupInitial != null) {
                        val oldProfile = characterSetupInitial!!
                        chatViewModel.updateCurrentCharacter(profile)
                        // 进学习页：AI 根据修改点重新理解角色（不静默，给可见反馈）
                        learningUpdating = true
                        currentScreen = Screen.LEARNING
                        coroutineScope.launch {
                            val start = System.currentTimeMillis()
                            val enriched = chatViewModel.regeneratePersona(oldProfile, profile)
                            val remain = 1500L - (System.currentTimeMillis() - start)
                            if (remain > 0) kotlinx.coroutines.delay(remain)
                            // 人设学习失败提示（敏感内容被拒时 personaPrompt 为空）
                            if (enriched.personaPrompt.isBlank()) {
                                android.widget.Toast.makeText(this@MainActivity, currentStrings.roleLearningRejected, android.widget.Toast.LENGTH_LONG).show()
                            }
                            chatViewModel.updateCurrentCharacter(enriched)
                            currentScreen = Screen.CHAT
                        }
                    } else {
                        // 首次创建：进加载页，AI 深度学习人设再开始聊天
                        learningUpdating = false
                        currentScreen = Screen.LEARNING
                        coroutineScope.launch {
                            val start = System.currentTimeMillis()
                            val enriched = if (profile.personaPrompt.isNotBlank()) {
                                // 导入的角色已带完整人设提示词：原样使用，不重新学习生成
                                profile
                            } else {
                                chatViewModel.generatePersonaPrompt(profile)
                            }
                            // 人设学习失败提示：设定含敏感内容被模型拒绝时 personaPrompt 为空，明确告知（不再静默降级）
                            if (enriched.personaPrompt.isBlank()) {
                                android.widget.Toast.makeText(this@MainActivity, currentStrings.roleLearningRejected, android.widget.Toast.LENGTH_LONG).show()
                            }
                            // 学习页至少停留 1.5s，避免一闪而过
                            val minLearnMs = 1500L
                            val remain = minLearnMs - (System.currentTimeMillis() - start)
                            if (remain > 0) kotlinx.coroutines.delay(remain)
                            // 角色立即建好并落侧滑栏，直接进聊天页
                            chatViewModel.startCompanionConversation(enriched)
                            currentScreen = Screen.CHAT
                        }
                    }
                },
                onSaveSimulation = { profile ->
                    // 预览态只改了模拟设置：直接更新角色并返回，不触发学习页
                    chatViewModel.updateCurrentCharacter(profile)
                    currentScreen = Screen.CHAT
                },
                initial = characterSetupInitial
            )
            Screen.LEARNING -> PersonaLearningScreen(updating = learningUpdating)
        }
    }
    
    // 圆形展开动画完成回调
    LaunchedEffect(effectiveScreen, pendingRevealScreen) {
        if (pendingRevealScreen != null) {
            // 等待动画帧
            kotlinx.coroutines.delay(60)
            currentScreen = pendingRevealScreen!!
            pendingRevealScreen = null
        }
    }
                        }
                    }

                    // =============================================
                    // 2. 遮罩层 — graphicsLayer alpha 只重绘
                    //    高级材质下侧滑页与 Chat 平级并排、不做右侧变暗遮罩
                    //
                    //    ⚠️ 遮罩必须**从抽屉右边缘起**，不能铺满整屏：
                    //    炫彩开着而高级材质关着时，抽屉自己是透明的（只罩了一层 α≈0.07 的 drawerVeil），
                    //    全屏遮罩就会从抽屉底下透上来 —— 抽屉整页被压成一块灰黑，卡片却是实色的白，
                    //    看起来像「抽屉背景被染灰」。遮罩的本意只是压暗被抽屉推开的 Chat 页，
                    //    所以左边缘跟着抽屉右边缘走：两者同源（都是 drawerOffset - overlapPx）。
                    //
                    //    1.0.49 性能：这层改成**常驻组合**，不再用 `if (drawerOffset.value > 0f)` 包着。
                    //    那一句是在组合期读偏移量，手指拖侧滑时每帧都成立/失效，一帧重组的就是
                    //    根 Box 这一整段（几百行）。现在 alpha 只写在绘制 lambda 里、拖动时零重组。
                    //
                    //    ⚠️ 1.0.49 修复「进 App 就卡死、所有按钮和滑动全无反应」：
                    //    常驻组合之后，这一层那个铺满全屏的 pointerInput 节点就**永远挂在屏幕上**了。
                    //    Compose 的命中测试里，一个铺满屏幕、又带指针节点的**兄弟**（这一层画在 Chat 层之后
                    //    = 在它上面）会把下面那整个兄弟挡掉 —— 命中即止。所以「不 consume」救不了：
                    //    触摸压根走不到 Chat 页。原来 `if (drawerOffset.value > 0f)` 包着的时候，抽屉关着
                    //    这一层根本不存在，自然没事。
                    //    收成**条件挂载指针节点**：关闭态这一层是纯装饰，一个指针节点都没有；
                    //    而 drawerOpen 是**离散量**（一次手势最多翻转两次），读它进组合几乎不要钱 ——
                    //    真正每帧都在变的 drawerOffset 依旧只在上面那个绘制块里读。
                    // =============================================
                    run {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = if (!advancedMaterial) {
                                        (drawerOffset.value / drawerWidthPx)
                                            .coerceIn(0f, 1f) * FreeChatAnimation.SCRIM_MAX_ALPHA
                                    } else 0f
                                    // 抽屉右边缘 R = -W + offset - overlap + W = offset - overlap
                                    translationX = drawerOffset.value - overlapPx
                                }
                                .background(Color.Black)
                                .then(
                                    if (drawerOpen) Modifier
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) { drawerOpen = false }
                                        .pointerInput(Unit) {
                                            detectHorizontalDragGestures(
                                                onDragStart = { isDragging = true; coroutineScope.launch { drawerOffset.snapTo(drawerOffset.value) } },
                                                onDragEnd = {
                                                    isDragging = false
                                                    val threshold = drawerWidthPx * FreeChatAnimation.DRAWER_SWIPE_THRESHOLD
                                                    if (drawerOffset.value < drawerWidthPx - threshold) drawerOpen = false
                                                },
                                                onDragCancel = { isDragging = false },
                                                onHorizontalDrag = { _, dragAmount ->
                                                    coroutineScope.launch {
                                                        drawerOffset.snapTo(
                                                            (drawerOffset.value + dragAmount).coerceIn(0f, drawerWidthPx)
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    else Modifier
                                )
                        )
                    }

                    // =============================================
                    // 3. 抽屉层 — 布局期 offset 左滑入
                    // =============================================
                    // 这一层的位移补偿：层内的模糊源节点要按「屏幕坐标」画背景副本，
                    // 侧滑层左移多少，层内的背景副本就反向挪回多少（见 LiquidPageShift）。
                    // 不补偿的话副本跟着层一起被搬走，接缝两侧露的是同一幅渐变的不同两段，
                    // 看着就是两块颜色不同的板子拼在一起、交界处一道裂痕。
                    //
                    // ❗️必须用布局期 offset，不能用 graphicsLayer.translationX 平移（1.0.50 修）：
                    // Haze 的采样区域是按**根坐标**（源区域的 positionInRoot ∩ 根边界）算的，
                    // 只在收到位置回调时才刷新。抽屉是常驻组合、开合只改这一条平移，
                    // graphicsLayer 的平移只进绘制层、不触发位置回调 —— Haze 记下的源区域就永远
                    // 停在启动那一瞬的值（关着时抽屉在 −1194px 屏幕外），于是「源区域 ∩ 屏」恒为空集，
                    // 模糊层尺寸被夹成 0 → 模糊和罩色整块不画，卡片只剩一层半透明底色（「PPT 层」），
                    // 上下两条模糊带（背景本来就是透明的）直接消失。改成布局期 offset 后节点真实躺在
                    // 它显示的位置上，滑动时位置回调照常触发、采样几何全程有效。
                    // 代价：滑动期间每帧重新摆位一次（只摆位不重新测量），静止时零开销。
                    CompositionLocalProvider(LocalLiquidPageShift provides liquidDrawerShift) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(drawerWidthDp)
                                .offset {
                                    // 与层内副本同源：都是这个 drawerShift（副本在绘制期现调它）
                                    IntOffset(drawerShift().roundToInt(), 0)
                                }
                                .then(
                                    // 流动炫彩：侧滑页也不再自己铺底 —— 根背景层钉在屏幕上不动，
                                    // 抽屉在它上面滑过，于是「抽屉滑出时背景纹丝不动」，正是网页版的观感。
                                    // 注：圆角裁剪只在非高级材质那一支有，这一支没有 —— 炫彩开着时抽屉整块透明，
                                    // 裁或不裁都看不出来；炫彩关掉时它会是一块方角的实色板（现存差异，暂未动）。
                                    if (advancedMaterial) {
                                        if (liquidActive) Modifier else Modifier.background(colors.Background)
                                    } else {
                                        Modifier
                                            .clip(RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))
                                            .then(
                                                // 高级材质下抽屉内容本身是不透明的模糊源，不需要罩色；
                                                // 非高级材质下抽屉是透明的，盖一层极淡的玻璃色（drawerVeil 的 α≈0.07），
                                                // 只留一丝「这是一层抽屉」的暗示 —— 早先这里是 60% 不透明，
                                                // 于是抽屉与 Chat 页在接缝处成了两块颜色，看着就是裂痕。
                                                if (liquidActive) Modifier.background(liquidColors.drawerVeil)
                                                else Modifier.background(colors.Surface)
                                            )
                                    }
                                )
                                .pointerInput(drawerOpen) {
                                    if (drawerOpen) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { isDragging = true },
                                            onDragEnd = {
                                                isDragging = false
                                                val threshold = drawerWidthPx * FreeChatAnimation.DRAWER_SWIPE_THRESHOLD
                                                if (drawerOffset.value < drawerWidthPx - threshold) drawerOpen = false
                                            },
                                            onDragCancel = { isDragging = false },
                                            onHorizontalDrag = { _, dragAmount ->
                                                coroutineScope.launch {
                                                    drawerOffset.snapTo(
                                                        (drawerOffset.value + dragAmount).coerceIn(0f, drawerWidthPx)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                        ) {
    DrawerContent(
        conversations = conversations, currentId = currentConvId, isDark = isDark,
        drawerHazeState = drawerHazeState,
        searchQuery = searchQuery, searchResults = searchResults,
        favoriteConvIds = favoriteConvIds,
        onSearchQueryChange = { chatViewModel.updateSearchQuery(it) },
        onNewChat = {
            drawerOpen = false
            currentScreen = Screen.NEW_CHAT_MODE
        },
        onSelectConversation = { conv ->
            // 对话立刻切（后台已经切好了），抽屉**只等一下下**再关：
            // 立刻关的话竖条跟着整块侧滑页滑出屏幕左缘，A→B 那根竖条的滑行就白做了；
            // 等它滑完再关又成了肉眼可见的卡顿（用户要「点了就进 Chat 页」）。
            // 所以只等 INDICATOR_DRAWER_DELAY_MS —— 那时竖条已走完约九成，
            // 剩下的零头和面板一起滑出屏幕，看着是交接。时序推导见那个常量的说明。
            chatViewModel.switchToConversation(conv)
            pendingDrawerClose?.cancel()
            pendingDrawerClose = coroutineScope.launch {
                delay(FreeChatAnimation.INDICATOR_DRAWER_DELAY_MS.toLong())
                drawerOpen = false
                pendingDrawerClose = null
            }
        },
        onSelectSearchResult = { conv, msgId ->
            chatViewModel.switchToConversation(conv)
            chatViewModel.requestScrollToMessage(msgId)
            drawerOpen = false
        },
        onPinConversation = { conv -> chatViewModel.togglePinConversation(conv) },
        // 侧滑页多选：批量置顶 / 批量删除（各自只落盘一次）
        onBatchPinConversations = { ids, pinned -> chatViewModel.setPinnedConversations(ids, pinned) },
        // 三个弹层只在这里"举手"，弹层本体画在根 Box 末尾（原因见上面那几个 drawerXxx 状态）
        onAskRenameConversation = { conv -> drawerRenameTarget = conv; drawerRenameText = conv.title },
        onAskDeleteConversation = { conv -> drawerDeleteTarget = conv },
        onAskBatchDelete = { convs -> drawerBatchDeleteTargets = convs },
        drawerVisible = drawerOpen,
        exitSelectKey = drawerExitSelectKey,
        onOpenSettings = {
            drawerOpen = false
            pendingRevealScreen = Screen.SETTINGS
        },
        onOpenFavorites = {
            drawerOpen = false
            pendingRevealScreen = Screen.FAVORITES
        },
        onOpenAccount = {
            drawerOpen = false
            pendingRevealScreen = Screen.ACCOUNT
        },
        onSettingsRowPositioned = { rect -> settingsRowBounds = rect },
        onFavoritesRowPositioned = { rect -> favoritesRowBounds = rect },
        onNewChatRect = { rect -> drawerNewChatRect = rect }
    )
                        }
                    }
                    }  // ← 「弹层模糊源」整块结束（背景 / Chat 层 / 遮罩 / 侧滑层）

                    // =============================================
                    // 4. 共享 FreeChat 标题 — 仅在 Chat 页面，随抽屉流动
                    //    Chat: 黑字 18sp，与 hamburger 菜单垂直对齐
                    //    Drawer: 棕字 24sp，滑入抽屉顶部
                    //    letterSpacing 固定不变，避免过渡中文本宽度变化导致闪跳
                    // =============================================
                    if (currentScreen == Screen.CHAT) {
                        // 标题**必须**单独拆成一个 composable：它每帧都要读 drawerOffset
                        // （手指拖侧滑时每帧都变）。写在根 Box 这一层读，一帧重组的
                        // 就是根 Box 的整段内容 —— 拖拉时掉帧有一份是它贡献的。
                        ChatDrawerTitle(
                            drawerOffset = drawerOffset,
                            drawerWidthPx = drawerWidthPx,
                            density = density,
                            colors = colors,
                            titleText = when {
                                // 多选：标题让位给「已选择 x 项」，复用共享标题的字号/字距/位置/抽屉跟随
                                multiSelect.active -> currentStrings.selectedCount(multiSelect.selectedIds.size)
                                currentMode == ChatMode.COMPANION -> currentCharacter?.name?.takeIf { it.isNotBlank() } ?: "FreeChat"
                                else -> "FreeChat"
                            }
                        )
                    }

                    // =============================================
                    // 5. 首次进入用户协议门（勾选同意才放行，任何路径绕不过）
                    //
                    // 判据写成 `== false` 而不是 `!`：hasAgreedTerms 是三态
                    // （null = 还没从 DataStore 读出来）。老用户冷启动时它是 null 那几十毫秒，
                    // 用 `!` 会把这当成「没同意」先闪一下协议浮层再消失（1.0.53 修）。
                    // =============================================
                    when (hasAgreedTerms) {
                        // null：DataStore 还没读出结果。只铺一层不透明底色挡着 ——
                        // 老用户不会闪协议，新用户也不会先闪一下主界面再弹协议。
                        null -> Box(Modifier.fillMaxSize().pageBackground(colors.Background))
                        false -> {
                            var showAgreement by remember { mutableStateOf(false) }
                            Box(Modifier.fillMaxSize()) {
                                if (showAgreement) {
                                    AgreementScreen(onBack = { showAgreement = false })
                                } else {
                                    AgreementGateDialog(
                                        onAgree = { chatViewModel.agreeTerms() },
                                        onOpenAgreement = { showAgreement = true },
                                        colors = colors
                                    )
                                }
                            }
                        }
                        true -> Unit
                    }

                    // =============================================
                    // 6.（已删除）「这次更新」开屏介绍卡。
                    //   1.0.49 按用户要求整条撤掉：卡片、启动闩、DataStore 记录、文案全删。
                    //   设置里的「版本更新」页还在（那是另一回事，用户看改动走那一页）。
                    // =============================================

                    // =============================================
                    // 7. 侧滑页那三个对话弹层（重命名 / 删除 / 批量删除）
                    //    画在根 Box 的**最后**：要盖在所有层之上；也必须在根 Box 上画 ——
                    //    侧滑层那一格只有 90% 屏宽，弹层挂进去就铺不满整屏了。
                    //    模糊源用 overlayHazeState（整屏那一块），不是侧滑页自己的
                    //    drawerHazeState —— 后者只盖 90% 屏宽，卡片会在侧滑页右边缘裂成两半。
                    // =============================================
                    SheetPanel(
                        visible = drawerDeleteTarget != null,
                        onDismiss = { drawerDeleteTarget = null },
                        title = currentStrings.deleteConversation,
                        colors = colors,
                        isDark = isDark,
                        advancedMaterial = advancedMaterial,
                        hazeState = overlayHazeState,
                        confirmLabel = currentStrings.delete,
                        confirmDanger = true,
                        onConfirm = {
                            drawerDeleteTarget?.let { chatViewModel.deleteConversation(it) }
                            drawerDeleteTarget = null
                        }
                    ) {
                        val conv = drawerDeleteTarget
                        if (conv != null) {
                            Text(currentStrings.confirmDeleteConv(conv.title), color = colors.TextSecondary)
                            if (favoriteConvIds.contains(conv.id)) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    currentStrings.deleteFavoritesWarning,
                                    color = colors.ErrorRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    SheetPanel(
                        visible = drawerBatchDeleteTargets != null,
                        onDismiss = { drawerBatchDeleteTargets = null },
                        title = currentStrings.deleteConversation,
                        colors = colors,
                        isDark = isDark,
                        advancedMaterial = advancedMaterial,
                        hazeState = overlayHazeState,
                        confirmLabel = currentStrings.delete,
                        confirmDanger = true,
                        onConfirm = {
                            val targets = drawerBatchDeleteTargets
                            drawerBatchDeleteTargets = null
                            if (targets != null) {
                                chatViewModel.deleteConversations(targets)
                                // 删完把侧滑页从多选态收回：勾中的那几条已经没了，
                                // 留着「已选择 N 项」的空壳最莫名其妙
                                drawerExitSelectKey++
                            }
                        }
                    ) {
                        val targets = drawerBatchDeleteTargets
                        if (targets != null) {
                            Text(currentStrings.confirmDeleteConvs(targets.size), color = colors.TextSecondary)
                            // 选中的对话里有收藏内容时，跟单条删除一样给出额外警告
                            if (targets.any { favoriteConvIds.contains(it.id) }) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    currentStrings.deleteFavoritesWarning,
                                    color = colors.ErrorRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    SheetPanel(
                        visible = drawerRenameTarget != null,
                        onDismiss = { drawerRenameTarget = null },
                        title = currentStrings.renameConversation,
                        colors = colors,
                        isDark = isDark,
                        advancedMaterial = advancedMaterial,
                        hazeState = overlayHazeState,
                        confirmLabel = currentStrings.confirm,
                        onConfirm = {
                            val conv = drawerRenameTarget
                            drawerRenameTarget = null
                            if (conv != null) {
                                chatViewModel.renameConversation(conv, drawerRenameText.trim().ifEmpty { conv.title })
                            }
                        }
                    ) {
                        OutlinedTextField(
                            value = drawerRenameText,
                            onValueChange = { drawerRenameText = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.TextPrimary,
                                unfocusedTextColor = colors.TextPrimary,
                                focusedBorderColor = colors.Primary,
                                unfocusedBorderColor = colors.Divider
                            )
                        )
                    }
                }
            }
            } // CompositionLocalProvider
        }
    }
}

/**
 * 共享标题：Chat 页 22sp 黑字 → 侧滑页 27sp 主题色，位置/字号/颜色随抽屉进度插值。
 *
 * 拆成独立 composable 是为了**把每帧重组关在这一格**：它每帧都读 `drawerOffset.value`
 * （手指拖侧滑时每帧都变），写在根 Box 那层读，一帧重组的是根 Box 的整段内容。
 * 颜色/字号用真实插值而不是 graphicsLayer 缩放 —— 缩放的 Bold 笔画会被稀释。
 */
@Composable
private fun ChatDrawerTitle(
    drawerOffset: Animatable<Float, AnimationVector1D>,
    drawerWidthPx: Float,
    density: Density,
    colors: com.freechat.ui.theme.FreeChatColors,
    titleText: String
) {
    val titleProgress = (drawerOffset.value / drawerWidthPx).coerceIn(0f, 1f)

    // 位置插值：52dp(chat) → 16dp(drawer)
    val titleXDp = 52f + (16f - 52f) * titleProgress
    val titleColor = lerp(colors.TextPrimary, colors.Primary, titleProgress)
    val titleFontSize = (22f + 5f * titleProgress).sp
    val titleFont = if (titleText == "FreeChat") Aurora else LocalGlobalFontFamily.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Text(
            titleText,
            fontSize = titleFontSize,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = titleFont,
            color = titleColor,
            modifier = Modifier
                .graphicsLayer {
                    translationX = with(density) { titleXDp.dp.toPx() }
                    translationY = with(density) { 18.dp.toPx() }
                }
        )
    }
}

/** 首次创建角色的加载页：AI 深度学习人物设定、还原人物 */
@Composable
private fun PersonaLearningScreen(updating: Boolean = false) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    // 超过 1 分钟还没好才出现耐心提示，不显示已用时间（避免视觉冗余）
    var showLongWaitHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60_000L)
        showLongWaitHint = true
    }
    Box(
        modifier = Modifier.fillMaxSize().pageBackground(colors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = colors.Primary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                if (updating) s.updatingPersonaTitle else s.learningPersonaTitle,
                style = MaterialTheme.typography.titleMedium, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (updating) s.updatingPersonaDesc else s.learningPersonaDesc,
                style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary
            )
            if (showLongWaitHint) {
                Spacer(Modifier.height(16.dp))
                Text(
                    s.learningPersonaLongWait,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.TextTertiary
                )
            }
        }
    }
}
