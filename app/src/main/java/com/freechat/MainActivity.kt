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
import androidx.compose.runtime.*
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.freechat.model.ColorTheme
import com.freechat.ui.screens.ChangelogScreen
import com.freechat.ui.screens.NewRulesScreen
import com.freechat.ui.screens.NewChatModeScreen
import com.freechat.ui.screens.CharacterSetupScreen
import com.freechat.model.ChatMode
import com.freechat.model.CharacterProfile
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.ui.screens.ChatScreen
import com.freechat.ui.screens.SettingsScreen
import com.freechat.ui.screens.VoiceDebugScreen
import com.freechat.ui.screens.ModelEditorScreen
import com.freechat.ui.screens.AgreementScreen
import com.freechat.ui.screens.AgreementGateDialog
import com.freechat.ui.theme.FreeChatTheme
import com.freechat.ui.theme.Aurora
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.LocalGlobalFontFamily
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import com.freechat.ui.theme.resolveColors
import com.freechat.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private enum class Screen { CHAT, SETTINGS, VOICE_DEBUG, CHANGELOG, NEW_RULES, NEW_CHAT_MODE, CHARACTER_SETUP, LEARNING, MODEL_EDITOR, AGREEMENT }

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
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> chatViewModel.onAppForegroundChanged(true)
                        Lifecycle.Event.ON_STOP -> chatViewModel.onAppForegroundChanged(false)
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
            val currentMode by chatViewModel.currentMode.collectAsState()
            val currentCharacter by chatViewModel.currentCharacter.collectAsState()
            val hasAgreedTerms by chatViewModel.hasAgreedTerms.collectAsState()
            val conversations by chatViewModel.conversations.collectAsState()
            val currentConvId by chatViewModel.currentConversationId.collectAsState()
            val searchQuery by chatViewModel.searchQuery.collectAsState()
            val searchResults by chatViewModel.searchResults.collectAsState()

            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.DARK_OLED -> true
                ThemeMode.LIGHT -> false
            }
            val colors = resolveColors(themeMode, colorTheme, systemDarkTheme)

            var currentScreen by remember { mutableStateOf(Screen.CHAT) }
            var drawerOpen by remember { mutableStateOf(false) }
            var isDragging by remember { mutableStateOf(false) }
            var settingsRowBounds by remember { mutableStateOf<Rect?>(null) }
            var drawerNewChatRect by remember { mutableStateOf<Rect?>(null) }

            // ===== 页面切换动画状态 =====
            var pendingRevealScreen by remember { mutableStateOf<Screen?>(null) }

            val density = LocalDensity.current
            val config = LocalConfiguration.current
            val drawerWidthDp = config.screenWidthDp.dp * 0.9f
            val drawerWidthPx = with(density) { drawerWidthDp.toPx() }
            val overlapPx = with(density) { 12.dp.toPx() }
            val drawerBlurMaxPx = with(density) { 3.dp.toPx() }  // 抽屉滑入时 Chat 内容的最大高斯模糊（像素）：减半以减轻侧滑动画每帧的 GPU 模糊合成压力

            val drawerOffset = remember { Animatable(0f) }
            val coroutineScope = rememberCoroutineScope()
            val chatHazeState = rememberHazeState()

            // ──── 键盘自动收起：侧滑打开抽屉时 ────
            val keyboardController = LocalSoftwareKeyboardController.current
            LaunchedEffect(drawerOpen) {
                if (drawerOpen) {
                    keyboardController?.hide()
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
            BackHandler(enabled = currentScreen == Screen.SETTINGS || currentScreen == Screen.VOICE_DEBUG || currentScreen == Screen.CHANGELOG || currentScreen == Screen.NEW_RULES || currentScreen == Screen.NEW_CHAT_MODE || currentScreen == Screen.CHARACTER_SETUP || currentScreen == Screen.MODEL_EDITOR || currentScreen == Screen.AGREEMENT || drawerOpen) {
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
                    drawerOpen -> { drawerOpen = false }
                }
            }

            // 观察语言设置变化
            val appLanguage by chatViewModel.appLanguage.collectAsState()
            val currentStrings = buildStrings(LocaleManager.resolveLocale(appLanguage))

            // 语言变更时重新应用 Locale
            LaunchedEffect(appLanguage) {
                LocaleManager.applyLocale(this@MainActivity, appLanguage)
            }

            androidx.compose.runtime.CompositionLocalProvider(LocalStrings provides currentStrings, LocalAdvancedMaterial provides advancedMaterial) {
            FreeChatTheme(themeMode = themeMode, colorTheme = colorTheme, fontSize = fontSize, useSystemFont = useSystemFont, systemDarkTheme = systemDarkTheme) {
                Box(
                    modifier = Modifier.fillMaxSize().background(colors.Background)
                ) {
                    // =============================================
                    // 1. Chat 主内容层 — graphicsLayer 右移（只重绘不重组）
                    // =============================================

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = (drawerWidthPx - overlapPx) * (drawerOffset.value / drawerWidthPx)
                                // 高级材质：抽屉滑入时对 Chat 页内容做高斯模糊虚化，随侧滑动画逐渐加重（替代暗色遮罩）
                                if (advancedMaterial) {
                                    val blurProgress = (drawerOffset.value / drawerWidthPx).coerceIn(0f, 1f)
                                    renderEffect = if (blurProgress > 0f) {
                                        BlurEffect(blurProgress * drawerBlurMaxPx, blurProgress * drawerBlurMaxPx, TileMode.Decal)
                                    } else null
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
                                 scaleIn(initialScale = 0.97f, animationSpec = tween(280, easing = FastOutSlowInEasing))) togetherWith
                                (fadeOut(FreeChatAnimation.pageFadeOutFast) +
                                 scaleOut(targetScale = 0.97f, animationSpec = tween(200, easing = FastOutSlowInEasing)))
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
                                                chatViewModel.updateCurrentCharacter(enriched)
                                                currentScreen = Screen.CHAT
                                            }
                                        } else {
                                            // 首次创建：进加载页，AI 深度学习人设再开始聊天
                                            learningUpdating = false
                                            currentScreen = Screen.LEARNING
                                            coroutineScope.launch {
                                                val start = System.currentTimeMillis()
                                                val enriched = chatViewModel.generatePersonaPrompt(profile)
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

                    // =============================================
                    // 2. 遮罩层 — graphicsLayer alpha 只重绘
                    //    高级材质下侧滑页与 Chat 平级并排、不做右侧变暗遮罩
                    // =============================================
                    if (!advancedMaterial && drawerOffset.value > 0f) {
                        val scrimAlpha = (drawerOffset.value / drawerWidthPx)
                            .coerceIn(0f, 1f) * FreeChatAnimation.SCRIM_MAX_ALPHA
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = scrimAlpha }
                                .background(Color.Black)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { drawerOpen = false }
                                .pointerInput(drawerOpen) {
                                    if (drawerOpen) {
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
                                }
                        )
                    }

                    // =============================================
                    // 3. 抽屉层 — graphicsLayer 左滑入
                    // =============================================
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(drawerWidthDp)
                            .graphicsLayer {
                                translationX = -drawerWidthPx + drawerOffset.value - overlapPx
                            }
                            .then(
                                if (advancedMaterial) Modifier.background(colors.Background)
                                else Modifier
                                    .clip(RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))
                                    .background(colors.Surface)
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
                            searchQuery = searchQuery, searchResults = searchResults,
                            onSearchQueryChange = { chatViewModel.updateSearchQuery(it) },
                            onNewChat = {
                                drawerOpen = false
                                currentScreen = Screen.NEW_CHAT_MODE
                            },
                            onSelectConversation = { conv ->
                                chatViewModel.switchToConversation(conv); drawerOpen = false
                            },
                            onDeleteConversation = { conv -> chatViewModel.deleteConversation(conv) },
                            onRenameConversation = { conv, t -> chatViewModel.renameConversation(conv, t) },
                            onPinConversation = { conv -> chatViewModel.togglePinConversation(conv) },
                            onOpenSettings = {
                                drawerOpen = false
                                pendingRevealScreen = Screen.SETTINGS
                            },
                            onSettingsRowPositioned = { rect -> settingsRowBounds = rect },
                            onNewChatRect = { rect -> drawerNewChatRect = rect }
                        )
                    }

                    // =============================================
                    // 4. 共享 FreeChat 标题 — 仅在 Chat 页面，随抽屉流动
                    //    Chat: 黑字 18sp，与 hamburger 菜单垂直对齐
                    //    Drawer: 棕字 24sp，滑入抽屉顶部
                    //    letterSpacing 固定不变，避免过渡中文本宽度变化导致闪跳
                    // =============================================
                    if (currentScreen == Screen.CHAT) {
                        val titleProgress = (drawerOffset.value / drawerWidthPx).coerceIn(0f, 1f)

                        // 位置插值
                        val titleChatXDp = 52f
                        val titleDrawerXDp = 16f
                        val titleXDp = titleChatXDp + (titleDrawerXDp - titleChatXDp) * titleProgress

                        // 颜色插值
                        val titleColor = lerp(colors.TextPrimary, colors.Primary, titleProgress)

                        // 字号插值：22sp(chat) → 27sp(drawer)，用真实字号而非 graphicsLayer 缩放，避免 Bold 笔画被缩放稀释
                        val titleFontSize = (22f + 5f * titleProgress).sp

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                        ) {
                            val titleText = if (currentMode == ChatMode.COMPANION) currentCharacter?.name?.takeIf { it.isNotBlank() } ?: "FreeChat" else "FreeChat"
                            val titleFont = if (titleText == "FreeChat") Aurora else LocalGlobalFontFamily.current
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

                    // =============================================
                    // 5. 首次进入用户协议门（勾选同意才放行，任何路径绕不过）
                    // =============================================
                    if (!hasAgreedTerms) {
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
                }
            }
            } // CompositionLocalProvider
        }
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
        modifier = Modifier.fillMaxSize().background(colors.Background),
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
