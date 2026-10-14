package com.freechat.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.freechat.ui.animation.MotionIconButton as IconButton
import com.freechat.ui.animation.MotionTextButton as TextButton
import com.freechat.ui.animation.jellyOverscroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.BuildConfig
import com.freechat.data.AppLatest
import com.freechat.data.AppUpdateChecker
import com.freechat.data.ModelSelectionResolver
import java.io.File
import com.freechat.R
import com.freechat.i18n.AppLanguage
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.model.ColorTheme
import com.freechat.model.FontSize
import com.freechat.model.HeaderBarStyle
import com.freechat.model.InputBarState
import com.freechat.model.InputStyle
import com.freechat.model.LengthMode
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.TempMode
import com.freechat.model.ThemeMode
import com.freechat.model.ChatMode
import com.freechat.sync.Session
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.PageMotion
import com.freechat.ui.components.MarkdownText
import com.freechat.ui.components.ShareMenuRow
import com.freechat.ui.components.SheetActionRow
import com.freechat.ui.components.SheetOption
import com.freechat.ui.components.SheetPanel
import com.freechat.ui.components.CustomColorPicker
import com.freechat.ui.theme.LocalMonoFontFamily
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.selectedFill
import com.freechat.ui.theme.selectedSubText
import com.freechat.ui.theme.selectedText
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.components.HeaderIconButton
import com.freechat.ui.components.HeaderTextButton
import com.freechat.ui.components.NeumorphicSwitch as Switch
import com.freechat.ui.theme.frostedGlass
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeState
import com.freechat.viewmodel.ChatViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.components.TopBarBackdrop
import com.freechat.ui.components.TopBarBackdropSource
import com.freechat.util.saveBitmapToGallery
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

private enum class SubScreen { LANGUAGE_MODEL, IMAGE_MODEL, VISION_MODEL, TTS_MODEL, ASR_MODEL, AI_MEMORY, FONT, INPUT_BOX, ABOUT_FREECHAT, AUTHOR, ICON, FEEDBACK, MY_FEEDBACK, MY_SHARES, DEV_PASSWORD, DEV_OPTIONS }

/** 五个「模型池」二级页：顶部「添加模型」固定置顶，池子自己滚（1.1.29 工单④） */
/**
 * 1.1.29.3 ①：「添加模型」卡片改**悬浮置顶**——画在模糊带之上、覆盖滚动池；池内容上滑
 * 从卡片底下钻进高斯渐变模糊被盖住（同侧滑页对话列表与「新对话/搜索」固定卡片的关系）。
 * 分页符分隔线撤掉（工单点名）。模糊带高度见 [PoolHeaderBandDp]。
 */
private val PoolHeaderBandDp = 72.dp
private val SubScreen.isModelPool: Boolean get() = when (this) {
    SubScreen.LANGUAGE_MODEL, SubScreen.IMAGE_MODEL, SubScreen.VISION_MODEL,
    SubScreen.TTS_MODEL, SubScreen.ASR_MODEL -> true
    else -> false
}

/** 导航深度：主页=0，二级=1，三级=2 —— 页面过渡方向按深度增减，与 MainActivity 的 motionDepth 同一判据 */
private fun subScreenDepth(s: SubScreen?): Int = when (s) {
    null -> 0
    SubScreen.AUTHOR, SubScreen.DEV_PASSWORD, SubScreen.DEV_OPTIONS, SubScreen.MY_FEEDBACK -> 2
    else -> 1
}

/** 设置页磨砂玻璃的 HazeState，通过 CompositionLocal 提供给所有 SettingsRow（含二级页） */
internal val LocalSettingsHazeState = staticCompositionLocalOf<HazeState?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel = viewModel(),
    isDark: Boolean,
    onBack: () -> Unit,
    onOpenVoiceDebug: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenModelEditor: (ModelType, ModelInfo?) -> Unit = { _, _ -> },
    onOpenAgreement: () -> Unit = {},
    onOpenAuthorWords: () -> Unit = {},
    /** 1.1.20：更新提醒弹窗的「去更新」—— 直达「关于FreeChat」二级页（下载仍由用户在那页点） */
    openAboutOnLaunch: Boolean = false,
    /** 1.1.21：「作者的话」详情页的原路返回 —— 直达「作者」三级页（它的上级是关于FreeChat） */
    openAuthorOnLaunch: Boolean = false,
    /** 1.1.26.7：模型编辑/添加页原路返回 —— 直达来源模型列表二级页（不再跳过它直回设置主页） */
    openModelListOnLaunch: ModelType? = null,
    /** 1.1.26.7：滚动位置记忆（key=页面名，值=滚动像素）——由 MainActivity 持有，跨全屏旁路不丢 */
    levelScrolls: MutableMap<String, Int> = mutableMapOf(),
    onAboutConsumed: () -> Unit = {}
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedVisualModel by viewModel.selectedVisualModel.collectAsState()
    val selectedVisionModel by viewModel.selectedVisionModel.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()
    val customColorArgb by viewModel.customColorArgb.collectAsState()
    val tempMode by viewModel.tempMode.collectAsState()
    val lengthMode by viewModel.lengthMode.collectAsState()
    val enableWebSearch by viewModel.enableWebSearch.collectAsState()
    val showSearchSources by viewModel.showSearchSources.collectAsState()
    val showThinking by viewModel.showThinking.collectAsState()
    val collapseThinking by viewModel.collapseThinking.collectAsState()
    val autoSummarizeMemory by viewModel.autoSummarizeMemory.collectAsState()
    val useSystemFont by viewModel.useSystemFont.collectAsState()
    val globalMemories by viewModel.globalMemories.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val inputStyle by viewModel.inputStyle.collectAsState()
    val inputBarState by viewModel.inputBarState.collectAsState()
    val advancedMaterialSetting by viewModel.advancedMaterial.collectAsState()
    val headerBarStyle by viewModel.headerBarStyle.collectAsState()
    val advancedMaterial = LocalAdvancedMaterial.current
    val chatMode by viewModel.chatMode.collectAsState()
    val hazeState = rememberHazeState()
    val systemDarkTheme by viewModel.systemDarkTheme.collectAsState()
    val liquidBackdrop by viewModel.liquidBackdrop.collectAsState()
    val asrModel by viewModel.asrModel.collectAsState()
    val asrModels by viewModel.asrModels.collectAsState()
    val voiceModel by viewModel.voiceModel.collectAsState()
    val ttsModels by viewModel.ttsModels.collectAsState()

    var showThemePicker by remember { mutableStateOf(false) }
    var showColorThemePicker by remember { mutableStateOf(false) }
    var editCustomColor by remember { mutableStateOf(false) }
    var previewCustomArgb by remember { mutableIntStateOf(0xFF346C98.toInt()) }
    var showInputStylePicker by remember { mutableStateOf(false) }
    var showInputBarStatePicker by remember { mutableStateOf(false) }
    var showHeaderStylePicker by remember { mutableStateOf(false) }
    // 1.1.26.7：「我的分享」页共享状态（列表/多选/调节/动作菜单）——标题栏与浮层都在本页根 Box 渲染
    val myShares = remember { MySharesUiState() }
    LaunchedEffect(advancedMaterialSetting) {
        if (!advancedMaterialSetting) showHeaderStylePicker = false
    }
    var showTempPicker by remember { mutableStateOf(false) }
    var showLengthPicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showChatModePicker by remember { mutableStateOf(false) }
    var showFontSizePicker by remember { mutableStateOf(false) }
    var showGlobalMemoryEditor by remember { mutableStateOf(false) }
    /** 全局记忆输入框里那行字。**必须提升到这里**：面板是常驻组合、只是不可见，状态放在 if 里会被反复重建 */
    var newMemoryText by remember { mutableStateOf("") }
    var previewFontSize by remember { mutableStateOf(FontSize.MEDIUM) }
    var showWebSearchConfirm by remember { mutableStateOf(false) }
    var showSearchSettings by remember { mutableStateOf(false) }
    var showLiquidBackdropConfirm by remember { mutableStateOf(false) }
    val searchConfig by viewModel.searchConfig.collectAsState()
    /** 长按作者页二维码 → 保存弹层（同样是底部磨砂玻璃，渲染在下面根 Box 的末尾） */
    var showQrSaveSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val iconManager = remember(context) { com.freechat.data.LauncherIconManager(context) }
    val activeIcon = remember { iconManager.activeIcon() }
    var selectedIcon by rememberSaveable { mutableStateOf(activeIcon) }
    var savingIcon by remember { mutableStateOf(false) }
    var showIconRestartConfirm by rememberSaveable { mutableStateOf(false) }
    val saveIconAndRestart: () -> Unit = {
        if (!savingIcon && selectedIcon != activeIcon) {
            savingIcon = true
            scope.launch {
                val target = selectedIcon
                val saved = withContext(Dispatchers.IO) { iconManager.request(target) }
                if (saved) {
                    try {
                        viewModel.prepareForIconRestart()
                        com.freechat.IconRestartActivity.launch(context)
                    } catch (_: Exception) {
                        withContext(Dispatchers.IO) { iconManager.request(activeIcon) }
                        Toast.makeText(context, s.appIconSaveFailed, Toast.LENGTH_LONG).show()
                        savingIcon = false
                    }
                } else {
                    Toast.makeText(context, s.appIconSaveFailed, Toast.LENGTH_SHORT).show()
                    savingIcon = false
                }
            }
        }
    }
    val feedbackStore = remember(context) { com.freechat.data.FeedbackDraftStore(context) }
    var feedbackDraft by remember { mutableStateOf(feedbackStore.load()) }

    // 作者页二维码保存：取那张**白底原图**（author_qr.jpg）写进相册 ——
    // 透明的那张只负责在页面里好看，直接存出去在相册里背景会变黑、扫不出来。
    // 1.0.50 起不再逐像素二值化（那会把中间的 logo 烧成一块黑）。
    // API 29 往上写相册不需要任何权限；26~28 得现场跟系统要 WRITE_EXTERNAL_STORAGE。
    val saveQrNow: () -> Unit = {
        scope.launch {
            val bmp = withContext(Dispatchers.Default) { renderQrOnWhite(context.resources) }
            val ok = bmp?.let { withContext(Dispatchers.IO) { context.saveBitmapToGallery(it) } } == true
            Toast.makeText(
                context,
                if (ok) s.imageSavedTo("Pictures/FreeChat") else s.storageSaveFailed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val qrPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) saveQrNow()
        else Toast.makeText(context, s.storageSaveFailed, Toast.LENGTH_SHORT).show()
    }
    /** 弹层里点「保存到相册」：先关弹层，再按版本决定直接存还是要权限 */
    val requestQrSave: () -> Unit = {
        showQrSaveSheet = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) saveQrNow()
        else qrPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    // 1.1.20/1.1.21：详情页「原路返回」的深链闩 —— 进设置直达「关于FreeChat」或「作者」
    // （首次组合时进初值；消费完把闩交回去，免得下次进设置又被弹去二级页）
    // 1.1.26.7 追加模型列表闩：模型编辑/添加页返回直达来源模型列表，不再跳级。
    var subScreen by remember {
        mutableStateOf(
            if (openAuthorOnLaunch) SubScreen.AUTHOR
            else if (openAboutOnLaunch) SubScreen.ABOUT_FREECHAT
            else when (openModelListOnLaunch) {
                ModelType.LANGUAGE -> SubScreen.LANGUAGE_MODEL
                ModelType.VISUAL -> SubScreen.IMAGE_MODEL
                ModelType.VISION -> SubScreen.VISION_MODEL
                ModelType.TTS -> SubScreen.TTS_MODEL
                ModelType.ASR -> SubScreen.ASR_MODEL
                null -> null
            }
        )
    }
    LaunchedEffect(openAboutOnLaunch, openAuthorOnLaunch, openModelListOnLaunch) {
        if (openAboutOnLaunch || openAuthorOnLaunch || openModelListOnLaunch != null) onAboutConsumed()
    }

    // 1.1.21/1.1.26.7：滚动位置记忆。低级页返回高级页回到点进去那一屏（「从哪点进去还回到哪」）；
    // map 由 MainActivity 持有所以全屏旁路（更新日志/作者的话/模型编辑/AI语音）回来也不丢——
    // 唯一清空点是侧滑进设置（统一回顶部，用户拍板口径）。

    // 子页返回键：优先返回设置主列表
    // Read the actual route inside the callback: a fast Back can arrive before recomposition
    // updates an enabled=subScreen!=null handler, otherwise it skips this level entirely.
    BackHandler(enabled = com.freechat.ui.animation.LocalPageActive.current) {
        // 「我的分享」多选态：返回先退出多选，不跳页（对齐收藏页）
        if (subScreen == SubScreen.MY_SHARES && myShares.selectMode) {
            myShares.exitSelect()
            return@BackHandler
        }
        // 「我的反馈」从反馈页进来：返回先回反馈页，再回设置（页级返回键同款）
        when (subScreen) {
            null -> onBack()
            SubScreen.MY_FEEDBACK -> subScreen = SubScreen.FEEDBACK
            // 作者页/非开源二级页都只从「关于FreeChat」进，返回回聚合页
            SubScreen.AUTHOR, SubScreen.DEV_PASSWORD, SubScreen.DEV_OPTIONS -> subScreen = SubScreen.ABOUT_FREECHAT
            else -> subScreen = null
        }
    }

    // 关闭联网搜索的确认 —— 也是底部磨砂玻璃弹层，渲染在下面根 Box 的末尾。
    // 确认既然只有「仍然关闭」一条路，主按钮就直接写这句话（不再是「确定/取消」那种含糊的两个键）

    // 主题 / 色彩 / 回复温度 / 回复长度 / 语言 / 字号 / 全局记忆 —— 七个二级弹层，
    // 全部改成**窗口内**的底部磨砂玻璃弹层，实际渲染在下面根 Box 的末尾（见 SheetPanel）。
    // 不能再用 AlertDialog：它是一个独立窗口，看不到 App 自己画的内容，
    // 想「模糊背景」是做不到的 —— 独立窗口的 Dialog 只能把背景压暗，没有第二种选择。

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageTravelPx = with(LocalDensity.current) { com.freechat.ui.animation.MotionPolicy.PageTravelDp.dp.roundToPx() }

    CompositionLocalProvider(LocalSettingsHazeState provides hazeState) {
    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        TopBarBackdropSource(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBarHeightDp))
        PageMotion(
            targetState = subScreen,
            distancePx = pageTravelPx.toFloat(),
            forward = { initial, target -> subScreenDepth(target) >= subScreenDepth(initial) },
            label = "settings_subpage"
        ) { screen ->
        if (screen == null) {
            // ============ 主设置列表 ============
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
                    // 果冻边界冗余（1.1.26.6）：主设置列表同款——顶/底继续拖跟手位移，松手回弹
                    .jellyOverscroll()
                    .verticalScroll(com.freechat.ui.animation.rememberPersistentScrollState("main", levelScrolls))
                    .padding(top = HazeSpec.topContentPaddingDp(statusBarHeightDp), bottom = 32.dp)
                    .padding(horizontal = 16.dp),
                // 卡片间距。**别再用 Spacer 微调卡片之间的间隙** ——
                // spacedBy 是「相邻两个孩子之间」都插一份，写 Spacer(2.dp) 得到的是
                // 8 + 2 + 8 = 18dp 而不是 2dp。1.0.53 之前这里一半卡片隔 8dp、一半隔 18dp，
                // 就是九个 Spacer(2.dp) 这么来的（用户截图点名「前三项挨那么近，后三项又隔挺远」）。
                // 段与段之间（SectionLabel 之前）才是真正需要更大的地方，用下面那个 Spacer(8.dp) 撑。
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 账号入口不在这儿 —— 它是侧栏左下角那个圆头像（未登录是灰的默认人像）。
                // 设置页里再放一遍只会让人两处都去找。

                // ──── 模型选择 ────
                SectionLabel(Icons.Filled.SmartToy, s.sectionModels)

                // 1.1.29 工单④：五个模型入口**各自独立一行**（不再把生图/识图、合成/识别各并一栏），
                // 卡片与行距跟其它设置项完全一致（SettingsItem 同一套版式）。
                // 语音两行的名字一律从**用户自己的模型库里查**（1.0.50）——没选/模型被删就说「未选择」。
                SettingsRow {
                    SettingsItem(Icons.Filled.SmartToy, s.languageModel, selectedModel.displayName, colors,
                        onClick = { subScreen = SubScreen.LANGUAGE_MODEL })
                }
                SettingsRow {
                    SettingsItem(Icons.Filled.Image, s.imageGenModel, selectedVisualModel.displayName, colors,
                        onClick = { subScreen = SubScreen.IMAGE_MODEL })
                }
                SettingsRow {
                    SettingsItem(Icons.Filled.Visibility, s.visionModel, selectedVisionModel?.displayName ?: s.notSelected, colors,
                        onClick = { subScreen = SubScreen.VISION_MODEL })
                }
                SettingsRow {
                    SettingsItem(Icons.Filled.RecordVoiceOver, s.voiceTtsLabel,
                        ttsModels.find { it.id == voiceModel }?.displayName ?: s.notSelected, colors,
                        onClick = { subScreen = SubScreen.TTS_MODEL })
                }
                SettingsRow {
                    SettingsItem(Icons.Filled.Mic, s.voiceAsrLabel,
                        asrModels.find { it.id == asrModel }?.displayName ?: s.notSelected, colors,
                        onClick = { subScreen = SubScreen.ASR_MODEL })
                }

                // 段间距：与 12dp 的卡片间距叠出 12+8+12 = 32dp —— 比卡片间距明显大，SectionLabel 才立得住
                Spacer(modifier = Modifier.height(8.dp))

                // ──── AI 系统优化 ────
                SectionLabel(Icons.Filled.AutoAwesome, s.sectionAiOptimize)

                // Parent/child settings share one surface, divider, and disclosure motion.
                SettingsGroup(expanded = enableWebSearch && selectedModel.supportsWebSearch,
                    parent = {
                        SettingsItem(Icons.Filled.Language, s.webSearch,
                            if (enableWebSearch && !selectedModel.supportsWebSearch) s.webSearchUnsupported
                            else if (enableWebSearch) s.webSearchOn else s.webSearchOff,
                            colors = colors) {
                            Switch(checked = enableWebSearch && selectedModel.supportsWebSearch,
                                modifier = Modifier.semantics { contentDescription = s.webSearch },
                                onCheckedChange = { checked ->
                                    if (!checked) showWebSearchConfirm = true else viewModel.setEnableWebSearch(true)
                                }, enabled = selectedModel.supportsWebSearch, colors = switchColors(colors))
                        }
                    }) {
                    SettingsItem(Icons.Filled.Language, s.searchSource,
                        if (searchConfig.provider == com.freechat.data.SearchProvider.FREE) s.searchFree else searchConfig.provider.label,
                        colors, onClick = { showSearchSettings = true })
                    SettingsDivider(colors)
                    SettingsItem(Icons.Filled.Link, s.showSearchSources, s.showSearchSourcesDesc, colors) {
                        Switch(checked = showSearchSources,
                            modifier = Modifier.semantics { contentDescription = s.showSearchSources },
                            onCheckedChange = { viewModel.setShowSearchSources(it) }, colors = switchColors(colors))
                    }
                }

                // The preference belongs to the selected model; hiding the child never erases it.
                SettingsGroup(expanded = com.freechat.data.SettingsPresentationPolicy.deepThinkingChildren(
                    selectedModel.deepThinkingDefault, selectedModel.supportsDeepThinking),
                    parent = {
                        SettingsItem(Icons.Filled.AutoAwesome, s.deepThinkingMode,
                            if (selectedModel.supportsDeepThinking) s.deepThinkSettingDesc else s.deepThinkUnsupported,
                            colors) {
                            Switch(checked = selectedModel.deepThinkingDefault && selectedModel.supportsDeepThinking,
                                modifier = Modifier.semantics { contentDescription = s.deepThinkingMode },
                                onCheckedChange = { viewModel.setDeepThinkDefaultForModel(ModelSelectionResolver.selectionKey(selectedModel), selectedModel.modelType, it) },
                                enabled = selectedModel.supportsDeepThinking, colors = switchColors(colors))
                        }
                    }) {
                    SettingsItem(Icons.Filled.Psychology, s.showThinking, s.showThinkingDesc, colors) {
                        Switch(checked = showThinking,
                            modifier = Modifier.semantics { contentDescription = s.showThinking },
                            onCheckedChange = { viewModel.setShowThinking(it) },
                            enabled = selectedModel.supportsThinking, colors = switchColors(colors))
                    }
                    // 1.1.29.8 工单②：默认折叠思考过程——仅「显示思考过程」开启后显示
                    if (showThinking) {
                        SettingsItem(Icons.Filled.UnfoldLess, s.collapseThinking, s.collapseThinkingDesc, colors) {
                            Switch(checked = collapseThinking,
                                modifier = Modifier.semantics { contentDescription = s.collapseThinking },
                                onCheckedChange = { viewModel.setCollapseThinking(it) },
                                colors = switchColors(colors))
                        }
                    }
                }

                // These two choices are grouped visually, not coupled logically.
                SettingsGroup(parent = {
                    SettingsItem(Icons.Outlined.Thermostat, s.replyTemp, tempLabel(tempMode, s),
                        colors, onClick = { showTempPicker = true })
                }) {
                    SettingsItem(Icons.Outlined.ShortText, s.replyLength, lengthLabel(lengthMode, s),
                        colors, onClick = { showLengthPicker = true })
                }

                // 语音调试
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenVoiceDebug() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.RecordVoiceOver, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.aiVoice, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // AI 记忆（二级页入口）
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { subScreen = SubScreen.AI_MEMORY }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Bookmark, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.aiMemory, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // 段间距：与 12dp 的卡片间距叠出 12+8+12 = 32dp —— 比卡片间距明显大，SectionLabel 才立得住
                Spacer(modifier = Modifier.height(8.dp))

                // ──── 系统主题 ────
                SectionLabel(Icons.Filled.DarkMode, s.sectionSystemTheme)

                // 色彩
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            previewCustomArgb = customColorArgb
                            editCustomColor = colorTheme == ColorTheme.CUSTOM
                            showColorThemePicker = true
                        }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Palette, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.colorTheme, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(colorThemeLabel(colorTheme, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                SettingsRow {
                    Row(Modifier.fillMaxWidth().clickable { subScreen = SubScreen.ICON }
                        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Apps, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.appIcon, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            // 小字与全设置页统一（1.1.28）：Serif 12sp，别处全是这个样式
                            Text(iconLabel(selectedIcon, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // 字体（二级页入口）
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { subScreen = SubScreen.FONT }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FormatSize, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.font, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(fontSizeLabel(fontSize, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                SettingsRow {
                    SettingsItem(Icons.Filled.Keyboard, s.inputBox,
                        "${inputStyleLabel(inputStyle, s)} · ${inputBarStateLabel(inputBarState, s)}",
                        colors, onClick = { subScreen = SubScreen.INPUT_BOX })
                }

                SettingsGroup(expanded = advancedMaterialSetting, parent = {
                    SettingsItem(Icons.Filled.BlurOn, s.advancedMaterial, s.advancedMaterialDesc, colors) {
                        Switch(checked = advancedMaterialSetting,
                            modifier = Modifier.semantics { contentDescription = s.advancedMaterial },
                            onCheckedChange = { viewModel.setAdvancedMaterial(it) }, colors = switchColors(colors))
                    }
                }) {
                    SettingsItem(Icons.Filled.Layers, s.headerBarStyle,
                        if (headerBarStyle == HeaderBarStyle.CARD) s.headerBarStyleCard else s.headerBarStyleCutout,
                        colors, onClick = { showHeaderStylePicker = true })
                }

                // 流动炫彩
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Gradient, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.liquidBackdrop, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(s.liquidBackdropDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Switch(
                            checked = liquidBackdrop,
                            onCheckedChange = { if (it) showLiquidBackdropConfirm = true else viewModel.setLiquidBackdrop(false) },
                            colors = switchColors(colors)
                        )
                    }
                }

                // 段间距：与 12dp 的卡片间距叠出 12+8+12 = 32dp —— 比卡片间距明显大，SectionLabel 才立得住
                Spacer(modifier = Modifier.height(8.dp))

                // ──── 通用 ────
                SectionLabel(Icons.Filled.Tune, s.sectionGeneral)

                // 主题（主题模式）
                SettingsRow {
                    val icon = when (themeMode) {
                        ThemeMode.SYSTEM -> Icons.Filled.Update
                        ThemeMode.LIGHT -> Icons.Filled.LightMode
                        ThemeMode.DARK -> Icons.Filled.DarkMode
                        ThemeMode.DARK_OLED -> Icons.Filled.DarkMode
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { showThemePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.theme, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(themeLabel(themeMode, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // 语言
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { showLanguagePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Language, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.language, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(langLabel(appLanguage, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // 段间距：与 12dp 的卡片间距叠出 12+8+12 = 32dp —— 比卡片间距明显大，SectionLabel 才立得住
                Spacer(modifier = Modifier.height(8.dp))

                // ──── 关于 ────
                SectionLabel(Icons.Filled.Info, s.sectionAbout)
                SettingsRow {
                    Column {
                        // 「关于FreeChat」聚合页（1.1.10）：logo + 版本号 + 检查更新 + 更新日志/关于作者/用户协议三个入口
                        Row(
                            Modifier.fillMaxWidth().clickable { subScreen = SubScreen.ABOUT_FREECHAT }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.aboutFreeChat, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { subScreen = SubScreen.FEEDBACK }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.feedbackEntry, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary,
                                modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                        }
                        // 「我的分享」管理在线分享链接（撤销即时失效）——挂在账号下，未登录看不到入口
                        if (com.freechat.sync.Session.isLoggedIn()) {
                            HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                            Row(
                                Modifier.fillMaxWidth().clickable { subScreen = SubScreen.MY_SHARES }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.myShares, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary,
                                    modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        } else {
                // ============ 二级页面 ============
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
                    .then(if (screen == SubScreen.FEEDBACK) Modifier.imePadding().navigationBarsPadding() else Modifier)
                    // 果冻边界冗余（1.1.26.6）：全部二级页统一——滑到顶/底继续拖，页面跟手位移带阻尼，松手回弹。
                    // 1.1.29 模型池页例外（工单④）：滚动交给池子自己，固定头（添加模型+分页线）不跟弹 ——
                    // 外层一旦再滚就成嵌套滚动（戒律9：崩）且固定头随内容走，两头都不对。
                    // 1.1.29 关于页同例（工单⑥）：滚动收进页内，「获取更新」浮钮才能钉屏不跟滚。
                    .then(
                        if (screen.isModelPool || screen == SubScreen.ABOUT_FREECHAT) Modifier
                        else Modifier
                            .jellyOverscroll()
                            .verticalScroll(com.freechat.ui.animation.rememberPersistentScrollState("sub_" + screen.name, levelScrolls))
                    )
                    // 1.1.29.5 工单A/C：关于页的上下留白收进页内滚动内容（见 AboutFreeChatPage）——
                    // 外层一留白，滚动视口就缩在标题栏下：日志上滚被视口顶缘硬裁进不了模糊带（A）、
                    // 底部留白条吃内容（C）。其余二级页不动（模型池固定头定位靠这份外层留白）
                    .padding(top = if (screen == SubScreen.ABOUT_FREECHAT) 0.dp
                        else HazeSpec.topContentPaddingDp(statusBarHeightDp),
                        bottom = when (screen) {
                            SubScreen.ABOUT_FREECHAT -> 0.dp
                            SubScreen.ICON -> 112.dp
                            else -> 32.dp
                        })
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (screen) {
                    SubScreen.ABOUT_FREECHAT -> AboutFreeChatPage(
                        colors = colors, s = s,
                        levelScrolls = levelScrolls,
                        onOpenChangelog = onOpenChangelog,
                        onOpenAgreement = onOpenAgreement,
                        onOpenAuthor = { subScreen = SubScreen.AUTHOR },
                        onOpenDevPassword = { subScreen = SubScreen.DEV_PASSWORD },
                        onOpenDevOptions = { subScreen = SubScreen.DEV_OPTIONS }
                    )
                    // 非开源区二级页接线（1.1.26.2）
                    SubScreen.DEV_PASSWORD -> com.freechat.dev.DevPasswordPage(
                        colors = colors,
                        onUnlocked = { subScreen = SubScreen.ABOUT_FREECHAT },
                        onBack = { subScreen = SubScreen.ABOUT_FREECHAT }
                    )
                    SubScreen.DEV_OPTIONS -> com.freechat.dev.DevOptionsPage(
                        colors = colors,
                        onExit = { subScreen = SubScreen.ABOUT_FREECHAT }
                    )
                    // ===== 五个模型池页（1.1.29 工单④）：每类一栏，骨架统一 =====
                    SubScreen.LANGUAGE_MODEL -> {
                        val models by viewModel.languageModels.collectAsState()
                        val selected by viewModel.selectedModel.collectAsState()
                        ModelPoolPage(colors, s, "sub_LANGUAGE_MODEL", levelScrolls) {
                            models.forEach { model ->
                                ModelItemRow(model, ModelSelectionResolver.selectionKey(model) == ModelSelectionResolver.selectionKey(selected),
                                    { viewModel.selectLanguageModel(model) }, { onOpenModelEditor(ModelType.LANGUAGE, it) }, colors)
                            }
                        }
                    }
                    SubScreen.IMAGE_MODEL -> {
                        val models by viewModel.visualModels.collectAsState()
                        val selected by viewModel.selectedVisualModel.collectAsState()
                        ModelPoolPage(colors, s, "sub_IMAGE_MODEL", levelScrolls) {
                            models.forEach { model ->
                                ModelItemRow(model, model.id == selected.id,
                                    { viewModel.selectVisualModel(model) }, { onOpenModelEditor(ModelType.VISUAL, it) }, colors)
                            }
                        }
                    }
                    SubScreen.VISION_MODEL -> {
                        val models by viewModel.visionModels.collectAsState()
                        val selected by viewModel.selectedVisionModel.collectAsState()
                        ModelPoolPage(colors, s, "sub_VISION_MODEL", levelScrolls) {
                            if (models.isEmpty()) {
                                EmptyModelHint(s.addModelFirst, colors)
                            } else {
                                models.forEach { model ->
                                    ModelItemRow(model, model.id == selected?.id,
                                        { viewModel.selectVisionModel(model) }, { onOpenModelEditor(ModelType.VISION, it) }, colors)
                                }
                            }
                        }
                    }
                    SubScreen.TTS_MODEL -> {
                        val models by viewModel.ttsModels.collectAsState()
                        val selected by viewModel.voiceModel.collectAsState()
                        ModelPoolPage(colors, s, "sub_TTS_MODEL", levelScrolls) {
                            models.forEach { model ->
                                ModelItemRow(model, model.id == selected,
                                    { viewModel.selectTtsModel(model) }, { onOpenModelEditor(ModelType.TTS, it) }, colors)
                            }
                        }
                    }
                    SubScreen.ASR_MODEL -> {
                        val models by viewModel.asrModels.collectAsState()
                        val selected by viewModel.asrModel.collectAsState()
                        ModelPoolPage(colors, s, "sub_ASR_MODEL", levelScrolls) {
                            if (models.isEmpty()) {
                                EmptyModelHint(s.addModelFirst, colors)
                            } else {
                                models.forEach { model ->
                                    ModelItemRow(model, model.id == selected,
                                        { viewModel.selectAsrModel(model) }, { onOpenModelEditor(ModelType.ASR, it) }, colors)
                                }
                            }
                        }
                    }
                    SubScreen.AI_MEMORY -> AiMemoryPage(
                        viewModel = viewModel,
                        colors = colors,
                        s = s,
                        autoSummarizeMemory = autoSummarizeMemory,
                        globalMemories = globalMemories,
                        onEditGlobalMemory = { showGlobalMemoryEditor = true }
                    )
                    SubScreen.FONT -> FontPage(
                        viewModel = viewModel,
                        colors = colors,
                        s = s,
                        useSystemFont = useSystemFont,
                        fontSize = fontSize,
                        onOpenFontSize = {
                            previewFontSize = fontSize
                            showFontSizePicker = true
                        }
                    )
                    SubScreen.INPUT_BOX -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsRow {
                            SettingsItem(Icons.Filled.Keyboard, s.inputStyle, inputStyleLabel(inputStyle, s),
                                colors, onClick = { showInputStylePicker = true })
                        }
                        SettingsRow {
                            SettingsItem(Icons.Filled.VerticalAlignBottom, s.inputBarState, inputBarStateLabel(inputBarState, s),
                                colors, onClick = { showInputBarStatePicker = true })
                        }
                    }
                    SubScreen.AUTHOR -> AuthorPage(colors, s, isDark = isDark, onQrLongPress = { showQrSaveSheet = true }, onOpenAuthorWords = onOpenAuthorWords)
                    SubScreen.ICON -> IconPickerPage(selectedIcon, activeIcon, savingIcon, onSelect = { icon ->
                        if (!savingIcon) selectedIcon = icon
                    }, s = s)
                    SubScreen.FEEDBACK -> FeedbackPage(feedbackDraft, onTextChange = {
                        feedbackDraft = it
                        feedbackStore.save(it)
                    }, clientId = feedbackStore.clientId(), s = s)
                SubScreen.MY_FEEDBACK -> MyFeedbackScreen(s, clientId = feedbackStore.clientId())
                SubScreen.MY_SHARES -> MySharesScreen(s, myShares)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        }

        // 固定高度：实体标题栏与渐进模糊仅切换材质，不切换几何。
        // 1.1.29.3 ①：模型池页模糊带下延到「添加模型」卡片下方——内容上滑钻进高斯渐变被覆盖
        // （同侧滑页对话列表与「新对话/搜索」固定卡片的关系）。
        TopBarBackdrop(hazeState, colors.Background,
            if (subScreen?.isModelPool == true) HazeSpec.drawerTopBandHeightDp(statusBarHeightDp, PoolHeaderBandDp)
            else HazeSpec.topBandHeightDp(statusBarHeightDp))

        // 1.1.29.3 ①：「添加模型」悬浮置顶——画在模糊带之上、覆盖滚动池（分页线已撤）
        val poolAddType = when (subScreen) {
            SubScreen.LANGUAGE_MODEL -> ModelType.LANGUAGE
            SubScreen.IMAGE_MODEL -> ModelType.VISUAL
            SubScreen.VISION_MODEL -> ModelType.VISION
            SubScreen.TTS_MODEL -> ModelType.TTS
            SubScreen.ASR_MODEL -> ModelType.ASR
            else -> null
        }
        if (poolAddType != null) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(top = HazeSpec.topContentPaddingDp(statusBarHeightDp) + 4.dp)
                    .padding(horizontal = 16.dp)
            ) {
                AddModelButton({ onOpenModelEditor(poolAddType, null) }, colors, s)
            }
        }

        // ===== 悬浮标题栏（返回键 + 标题），固定悬浮顶部 =====
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 「我的分享」多选换头走 contentReplacement（与收藏页多选完全一致）：
            // Close + 已选择x项 + 撤销分享。selectMode 由长按卡片进入。
            if (subScreen == SubScreen.MY_SHARES && myShares.selectMode) {
            AnimatedContent(
                targetState = myShares.selectedIds.size,
                transitionSpec = { FreeChatAnimation.contentReplacement() },
                label = "settingsShareSelectHeader"
            ) { _ ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HeaderIconButton(onClick = { myShares.exitSelect() }) {
                        Icon(Icons.Filled.Close, s.cancel, tint = colors.TextPrimary)
                    }
                    Text(
                        s.selectedCount(myShares.selectedIds.size),
                        color = colors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.weight(1f))
                    HeaderTextButton(
                        onClick = { myShares.pendingRevoke = myShares.selectedItems() },
                        enabled = myShares.selectedIds.isNotEmpty()
                    ) {
                        Text(s.shareRevokeFull,
                            color = if (myShares.selectedIds.isEmpty()) colors.TextTertiary else colors.Primary)
                    }
                }
            }
            } else {
            HeaderIconButton(onClick = {
                if (subScreen == SubScreen.MY_FEEDBACK) subScreen = SubScreen.FEEDBACK
                else if (subScreen == SubScreen.AUTHOR || subScreen == SubScreen.DEV_PASSWORD || subScreen == SubScreen.DEV_OPTIONS) subScreen = SubScreen.ABOUT_FREECHAT
                else if (subScreen != null) subScreen = null
                else onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back, tint = colors.TextPrimary)
            }
            // 二级页切换时标题跟着换字（1.1.26 动画补缺）：contentReplacement 同款，页面本体早有转场。
            AnimatedContent(
                targetState = subScreen,
                transitionSpec = { FreeChatAnimation.contentReplacement() },
                label = "settingsTitle"
            ) { target ->
                Text(
                    when (target) {
                        SubScreen.LANGUAGE_MODEL -> s.languageModel
                        SubScreen.IMAGE_MODEL -> s.imageGenModel
                        SubScreen.VISION_MODEL -> s.visionModel
                        SubScreen.TTS_MODEL -> s.voiceTtsLabel
                        SubScreen.ASR_MODEL -> s.voiceAsrLabel
                        SubScreen.AI_MEMORY -> s.aiMemory
                        SubScreen.FONT -> s.font
                        SubScreen.INPUT_BOX -> s.inputBox
                        SubScreen.ABOUT_FREECHAT -> s.aboutFreeChat
                        SubScreen.AUTHOR -> s.authorAboutTitle
                        SubScreen.ICON -> s.appIcon
                        SubScreen.FEEDBACK -> s.feedbackTitle
                        SubScreen.MY_FEEDBACK -> s.feedbackMy
                        SubScreen.MY_SHARES -> s.myShares
                        SubScreen.DEV_PASSWORD -> com.freechat.dev.DevMode.TITLE_PASSWORD
                        SubScreen.DEV_OPTIONS -> com.freechat.dev.DevMode.TITLE_OPTIONS
                        null -> s.settings
                    },
                    color = colors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            // 「我的反馈」入口（1.0.95）：反馈页标题栏右侧——进去只能看，不能改不能删
            if (subScreen == SubScreen.FEEDBACK) {
                Spacer(Modifier.weight(1f))
                HeaderTextButton(onClick = { subScreen = SubScreen.MY_FEEDBACK }) {
                    Text(s.feedbackMy, style = MaterialTheme.typography.bodyMedium, color = colors.Primary)
                }
            }
            // 「我的分享」标题栏右侧「调节」按钮（1.1.26.7，与收藏页同款悬浮菜单卡片）
            if (subScreen == SubScreen.MY_SHARES) {
                Spacer(Modifier.weight(1f))
                HeaderIconButton(onClick = {
                    myShares.showTuneMenu = !myShares.showTuneMenu
                    if (myShares.showTuneMenu) myShares.tuneLevel = com.freechat.ui.components.TuneLevel.MAIN
                }) {
                    Icon(Icons.Filled.Tune, s.newRules, tint = colors.TextPrimary)
                }
            }
            }
        }

        AnimatedVisibility(
            visible = subScreen == SubScreen.ICON && selectedIcon != activeIcon,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = FreeChatAnimation.sheetEnter(), exit = FreeChatAnimation.sheetExit()
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                com.freechat.ui.animation.MotionButton(
                    onClick = {
                        if (viewModel.hasAnyActiveGeneration()) showIconRestartConfirm = true
                        else saveIconAndRestart()
                    },
                    enabled = !savingIcon,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.Primary, contentColor = colors.selectedText)
                ) { Text(if (savingIcon) s.appIconRestarting else s.save) }
            }
        }

        SheetPanel(
            visible = showIconRestartConfirm,
            onDismiss = { showIconRestartConfirm = false; selectedIcon = activeIcon },
            title = s.appIcon,
            colors = colors, isDark = isDark, advancedMaterial = advancedMaterial, hazeState = hazeState,
            confirmLabel = s.appIconRestartNow, confirmEnabled = !savingIcon,
            onConfirm = { showIconRestartConfirm = false; saveIconAndRestart() }
        ) {
            Text(s.appIconRestartWarning, color = colors.TextSecondary)
            SheetActionRow(s.appIconDoNotChange, colors, link = true) {
                showIconRestartConfirm = false
                selectedIcon = activeIcon
            }
        }

        // ===== 六个二级选择框（底部磨砂玻璃弹层，窗口内浮层）=====
        // 放在根 Box 的最后：它们要盖在所有东西之上（包括悬浮标题栏）。
        // 背景不压暗 —— 拦截层自己就是一层 haze，把背后的设置页**糊掉**，
        // 于视觉上「失焦」而不「变黑」，和 App 其它地方的材质是同一套语言。

        // 关闭联网搜索的确认。1.1.26.1 按钮改版：「仍然关闭」行内链接换成确定/取消一行双钮
        // （取消在左、确定在右主题色，与版本更新提示弹窗同款）——风险提示口吻不变，文字不动。
        // 点外面关掉弹层等于取消。
        SheetPanel(
            visible = showWebSearchConfirm,
            onDismiss = { showWebSearchConfirm = false },
            title = s.closeWebSearchTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.confirm,
            cancelLabel = s.cancel,
            onConfirm = {
                viewModel.setEnableWebSearch(false)
                showWebSearchConfirm = false
            }
        ) {
            Text(s.closeWebSearchDesc, color = colors.TextSecondary)
        }

        SheetPanel(showLiquidBackdropConfirm, { showLiquidBackdropConfirm = false },
            s.liquidBackdrop, colors, isDark, advancedMaterial, hazeState,
            confirmLabel = s.confirm, cancelLabel = s.cancel,
            onConfirm = {
                showLiquidBackdropConfirm = false
                viewModel.setLiquidBackdrop(true)
            }) {
            Text(s.liquidBackdropConfirm, color = colors.TextSecondary)
        }

        SearchSettingsPanel(showSearchSettings, searchConfig, viewModel::saveSearchConfig,
            { showSearchSettings = false }, colors, isDark, advancedMaterial, hazeState)

        SheetPanel(showHeaderStylePicker && advancedMaterialSetting, { showHeaderStylePicker = false },
            s.headerBarStyle, colors, isDark, advancedMaterial, hazeState) {
            HeaderBarStyle.entries.forEach { style ->
                SheetOption(
                    selected = style == headerBarStyle,
                    title = if (style == HeaderBarStyle.CARD) s.headerBarStyleCard else s.headerBarStyleCutout,
                    subtitle = if (style == HeaderBarStyle.CARD) s.headerBarStyleCardDesc else s.headerBarStyleCutoutDesc,
                    icon = if (style == HeaderBarStyle.CARD) Icons.Filled.RadioButtonChecked else Icons.Filled.BlurOn,
                    colors = colors,
                    onClick = { viewModel.setHeaderBarStyle(style); showHeaderStylePicker = false },
                )
            }
        }

        SheetPanel(showThemePicker, { showThemePicker = false }, s.themeMode, colors, isDark, advancedMaterial, hazeState) {
            ThemeMode.entries.forEach { mode ->
                val sel = mode == themeMode
                val icon = when (mode) {
                    ThemeMode.SYSTEM -> Icons.Filled.Update
                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                    ThemeMode.DARK -> Icons.Filled.DarkMode
                    ThemeMode.DARK_OLED -> Icons.Filled.DarkMode
                }
                SheetOption(
                    selected = sel,
                    title = themeLabel(mode, s),
                    subtitle = themeDesc(mode, s),
                    icon = icon,
                    colors = colors,
                    onClick = { viewModel.setThemeMode(mode) }
                ) {
                    // 跟随系统时：选择暗色主题用「深色」还是「黑色」
                    AnimatedVisibility(mode == ThemeMode.SYSTEM && sel,
                        enter = FreeChatAnimation.expandEnter(), exit = FreeChatAnimation.expandExit()) {
                        Column {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 30.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.systemDarkTheme, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            Spacer(Modifier.weight(1f))
                            DarkThemeChip(s.themeDark, !systemDarkTheme, colors) { viewModel.setSystemDarkTheme(false) }
                            Spacer(Modifier.width(6.dp))
                            DarkThemeChip(s.themeDarkOled, systemDarkTheme, colors) { viewModel.setSystemDarkTheme(true) }
                        }
                        }
                    }
                }
            }
        }

        SheetPanel(
            showColorThemePicker,
            { showColorThemePicker = false },
            s.colorTheme,
            colors,
            isDark,
            advancedMaterial,
            hazeState,
            confirmLabel = if (editCustomColor) s.customColorApply else null,
            onConfirm = if (editCustomColor) {
                {
                    viewModel.setCustomColorTheme(previewCustomArgb)
                    showColorThemePicker = false
                }
            } else null,
        ) {
            val travel = with(LocalDensity.current) { com.freechat.ui.animation.MotionPolicy.PageTravelDp.dp.roundToPx() }
            AnimatedContent(editCustomColor, modifier = Modifier.fillMaxWidth(),
                transitionSpec = { FreeChatAnimation.pageTransition(targetState, travel, animateSize = true) },
                label = "custom_color_page") { customPage ->
                Column(Modifier.fillMaxWidth()) {
            if (customPage) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { editCustomColor = false }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(s.customPresetColors, style = MaterialTheme.typography.bodySmall, color = colors.Primary)
                }
                CustomColorPicker(
                    argb = previewCustomArgb,
                    isDark = isDark,
                    isOled = themeMode == ThemeMode.DARK_OLED ||
                        (themeMode == ThemeMode.SYSTEM && isDark && systemDarkTheme),
                    colors = colors,
                    strings = s,
                    onChange = { previewCustomArgb = it },
                )
            } else {
                SheetOption(
                    selected = colorTheme == ColorTheme.CUSTOM,
                    title = s.colorThemeCustom,
                    icon = Icons.Filled.Palette,
                    colors = colors,
                    onClick = { editCustomColor = true }
                )
                ColorTheme.presetsInDisplayOrder.forEach { ct ->
                    SheetOption(
                        selected = ct == colorTheme,
                        title = colorThemeLabel(ct, s),
                        swatch = colorThemeSwatch(ct, customColorArgb),
                        colors = colors,
                        onClick = { viewModel.setColorTheme(ct); showColorThemePicker = false }
                    )
                }
            }
                }
            }
        }

        // 撤销确认（1.1.26.7 起单条/批量共用）：菜单「撤销分享」或标题栏批量按钮 → pendingRevoke → 确认即执行
        val revokeScope = androidx.compose.runtime.rememberCoroutineScope()
        run {
            val pending = myShares.pendingRevoke
            SheetPanel(pending.isNotEmpty(), { myShares.pendingRevoke = emptyList() },
                s.shareRevokeFull, colors, isDark, advancedMaterial, hazeState,
                confirmLabel = s.shareRevokeFull, confirmDanger = true, onConfirm = {
                    revokeScope.launch { myShares.executeRevoke(context, s) }
                }) {
                Text(
                    if (pending.size > 1) "${s.shareRevokeConfirm}（${s.selectedCount(pending.size)}）"
                    else s.shareRevokeConfirm,
                    style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary
                )
                SheetActionRow(s.cancel, colors) { myShares.pendingRevoke = emptyList() }
            }
        }

        // ===== 「我的分享」卡片动作菜单（查看内容 / 复制链接 / 撤销分享红字）=====
        run {
            val menuShare = myShares.menuShare
            SheetPanel(menuShare != null, { myShares.menuShare = null },
                menuShare?.title?.ifBlank { s.share } ?: s.share, colors, isDark, advancedMaterial, hazeState) {
                ShareMenuRow(Icons.Filled.Language, s.shareViewContent, colors) {
                    val share = myShares.menuShare ?: return@ShareMenuRow
                    myShares.menuShare = null
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(com.freechat.sync.shareWebUrl(share.id))))
                    }
                }
                ShareMenuRow(Icons.Filled.ContentCopy, s.shareLinkCopy, colors) {
                    val share = myShares.menuShare ?: return@ShareMenuRow
                    myShares.menuShare = null
                    val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clip.setPrimaryClip(android.content.ClipData.newPlainText("share_link", com.freechat.sync.shareWebUrl(share.id)))
                    Toast.makeText(context, s.copied, Toast.LENGTH_SHORT).show()
                }
                ShareMenuRow(Icons.Filled.DeleteOutline, s.shareRevokeFull, colors, labelColor = colors.ErrorRed) {
                    val share = myShares.menuShare ?: return@ShareMenuRow
                    myShares.menuShare = null
                    myShares.pendingRevoke = listOf(share)
                }
            }
        }

        // ===== 「我的分享」调节菜单（筛选对话 / 排序方式，与收藏页 TuneMenu 同款同动画）=====
        AnimatedVisibility(
            visible = subScreen == SubScreen.MY_SHARES && myShares.showTuneMenu && !myShares.selectMode,
            enter = fadeIn(FreeChatAnimation.overlayFadeIn),
            exit = fadeOut(FreeChatAnimation.overlayFadeOut)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { myShares.showTuneMenu = false }
            )
        }
        AnimatedVisibility(
            visible = subScreen == SubScreen.MY_SHARES && myShares.showTuneMenu && !myShares.selectMode,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = FreeChatAnimation.menuEnter(androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
            exit = FreeChatAnimation.menuExit(androidx.compose.ui.graphics.TransformOrigin(1f, 0f))
        ) {
            com.freechat.ui.components.TuneMenu(
                level = myShares.tuneLevel,
                onLevelChange = { myShares.tuneLevel = it },
                filterLabel = s.filterByConversation,
                sortLabel = s.sortBy,
                filterEntries = myShares.filterEntries(s),
                selectedFilterKey = myShares.effectiveFilter() ?: "",
                onFilterSelect = { myShares.filterKey = it; myShares.showTuneMenu = false },
                sortEntries = listOf(
                    com.freechat.ui.components.TuneEntry("CREATED_TIME", s.shareSortCreated),
                    com.freechat.ui.components.TuneEntry("TITLE", s.shareSortTitle)
                ),
                selectedSortKey = myShares.sortMode.name,
                onSortSelect = { myShares.sortMode = ShareSort.valueOf(it); myShares.showTuneMenu = false },
                colors = colors,
                s = s,
                hazeState = hazeState,
                isDark = isDark,
                advancedMaterial = advancedMaterial,
                topPadding = statusBarHeightDp + HazeSpec.TitleBarAreaDp + 4.dp
            )
        }

        SheetPanel(showInputStylePicker, { showInputStylePicker = false }, s.inputStyle, colors, isDark, advancedMaterial, hazeState) {
            InputStyle.entries.forEach { st ->
                SheetOption(
                    selected = st == inputStyle,
                    title = inputStyleLabel(st, s),
                    subtitle = when (st) {
                        InputStyle.COMPACT -> s.inputStyleCompactDesc
                        InputStyle.COMPLETE -> s.inputStyleCompleteDesc
                    },
                    colors = colors,
                    onClick = { viewModel.setInputStyle(st); showInputStylePicker = false }
                )
            }
        }

        SheetPanel(showInputBarStatePicker, { showInputBarStatePicker = false }, s.inputBarState, colors, isDark, advancedMaterial, hazeState) {
            InputBarState.entries.forEach { st ->
                SheetOption(
                    selected = st == inputBarState,
                    title = inputBarStateLabel(st, s),
                    subtitle = when (st) {
                        InputBarState.PINNED -> s.inputBarStatePinnedDesc
                        InputBarState.AUTO_HIDE -> s.inputBarStateAutoHideDesc
                    },
                    colors = colors,
                    onClick = { viewModel.setInputBarState(st); showInputBarStatePicker = false }
                )
            }
        }

        SheetPanel(showTempPicker, { showTempPicker = false }, s.replyTemp, colors, isDark, advancedMaterial, hazeState) {
            val tempIcons = mapOf(
                TempMode.AUTO to Icons.Filled.Update,
                TempMode.WARM to Icons.Filled.Favorite,
                TempMode.OBJECTIVE to Icons.Filled.Psychology
            )
            val tempDescs = mapOf(
                TempMode.AUTO to s.tempAutoDesc,
                TempMode.WARM to s.tempWarmDesc,
                TempMode.OBJECTIVE to s.tempObjectiveDesc
            )
            TempMode.entries.forEach { mode ->
                SheetOption(
                    selected = mode == tempMode,
                    title = tempLabel(mode, s),
                    subtitle = tempDescs[mode] ?: "",
                    icon = tempIcons[mode] ?: Icons.Filled.Update,
                    colors = colors,
                    onClick = { viewModel.setTempMode(mode); showTempPicker = false }
                )
            }
        }

        SheetPanel(showLengthPicker, { showLengthPicker = false }, s.replyLength, colors, isDark, advancedMaterial, hazeState) {
            val lengthIcons = mapOf(
                LengthMode.AUTO to Icons.Filled.Update,
                LengthMode.FULL to Icons.Filled.MenuOpen,
                LengthMode.CONCISE to Icons.Filled.ShortText
            )
            val lengthDescs = mapOf(
                LengthMode.AUTO to s.lengthAutoDesc,
                LengthMode.FULL to s.lengthFullDesc,
                LengthMode.CONCISE to s.lengthConciseDesc
            )
            LengthMode.entries.forEach { mode ->
                SheetOption(
                    selected = mode == lengthMode,
                    title = lengthLabel(mode, s),
                    subtitle = lengthDescs[mode] ?: "",
                    icon = lengthIcons[mode] ?: Icons.Filled.Update,
                    colors = colors,
                    onClick = { viewModel.setLengthMode(mode); showLengthPicker = false }
                )
            }
        }

        SheetPanel(showLanguagePicker, { showLanguagePicker = false }, s.selectLanguage, colors, isDark, advancedMaterial, hazeState) {
            val langIcons = mapOf(
                AppLanguage.SYSTEM to Icons.Filled.Update,
                AppLanguage.ZH_CN to Icons.Filled.Language,
                AppLanguage.ZH_TW to Icons.Filled.Language,
                AppLanguage.EN to Icons.Filled.Language
            )
            AppLanguage.entries.forEach { lang ->
                SheetOption(
                    selected = lang == appLanguage,
                    title = langLabel(lang, s),
                    icon = langIcons[lang] ?: Icons.Filled.Update,
                    colors = colors,
                    onClick = { viewModel.setLanguage(lang); showLanguagePicker = false }
                )
            }
        }

        // 字号：带实时预览 + 滑杆，所以不是选一行就关，而是「确定」才落盘
        SheetPanel(
            visible = showFontSizePicker,
            onDismiss = { showFontSizePicker = false },
            title = s.fontSize,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.confirm,
            onConfirm = {
                viewModel.setFontSize(previewFontSize)
                showFontSizePicker = false
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(4.dp))
                Text(
                    s.settingsSlogan,
                    fontSize = (14 * previewFontSize.scale).sp,
                    color = colors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("A", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextTertiary)
                    Slider(
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        value = previewFontSize.ordinal.toFloat(),
                        onValueChange = { v ->
                            val i = v.roundToInt().coerceIn(0, FontSize.entries.lastIndex)
                            previewFontSize = FontSize.entries[i]
                        },
                        valueRange = 0f..FontSize.entries.lastIndex.toFloat(),
                        steps = FontSize.entries.size - 2,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.Primary,
                            activeTrackColor = colors.Primary,
                            inactiveTrackColor = colors.SurfaceVariant
                        )
                    )
                    Text("A", style = MaterialTheme.typography.titleLarge, color = colors.TextTertiary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    fontSizePercent(previewFontSize),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                    color = colors.TextSecondary
                )
            }
        }

        // 全局记忆编辑（AI 记忆子页里点进来）—— 同样是底部磨砂玻璃弹层。
        // 这一块没有任何「选中」语义，就是一列记忆 + 一个输入框，所以走的是自定义 content。
        SheetPanel(
            visible = showGlobalMemoryEditor,
            onDismiss = { showGlobalMemoryEditor = false; newMemoryText = "" },
            title = s.globalMemory,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState
        ) {
            if (globalMemories.isEmpty()) {
                Text(
                    s.globalMemoryEmpty,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                    color = colors.TextSecondary
                )
            } else {
                globalMemories.forEach { mem ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(colors.AccentMuted)
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(mem, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.deleteGlobalMemory(mem) }) {
                            Icon(Icons.Filled.DeleteOutline, null, tint = colors.ErrorRed, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = newMemoryText,
                    onValueChange = { newMemoryText = it },
                    placeholder = { Text(s.globalMemoryHint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextTertiary) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                    modifier = Modifier.weight(1f),
                    maxLines = 3
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        viewModel.addGlobalMemory(newMemoryText)
                        newMemoryText = ""
                    },
                    enabled = newMemoryText.isNotBlank()
                ) { Text(s.globalMemoryAdd, color = colors.Primary, fontWeight = FontWeight.Bold) }
            }
        }

        // 长按作者页二维码 → 保存。和上面那几个弹层同一套材质（窗口内底部磨砂玻璃），
        // 内容区留空：标题说了要干什么，主按钮就是「保存到相册」，中间不需要再塞一句话。
        SheetPanel(
            visible = showQrSaveSheet,
            onDismiss = { showQrSaveSheet = false },
            title = s.saveQr,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.saveToGallery,
            onConfirm = { requestQrSave() }
        ) { }
    }
    }
}

// 二级选择框（主题/色彩/温度/长度/语言/字号）与全局记忆编辑器，统一用
// com.freechat.ui.components.SheetPanel —— 窗口内的底部磨砂玻璃弹层。
// 不能再用 AlertDialog：它是一个独立窗口，看不到 App 自己画的内容，
// 想「模糊背景」是做不到的，只能把背景压暗（就是用户说的「悬浮卡片背景加暗」）。
// 那六个弹层的实际渲染位置在下面根 Box 的末尾，这里只是说明。

// ============ 二级页面 ============

/**
 * 模型池二级页的统一骨架（1.1.29 工单④ → 1.1.29.3 ① 改悬浮）：滚动池铺满整区，
 * 头部留出悬浮卡片的位置；「添加模型」浮在模糊带上层（见 SettingsScreen 根 Box 的 overlay）。
 * 五个类型页共用一份骨架，池内容各填各的。
 */
@Composable
private fun ColumnScope.ModelPoolPage(
    colors: com.freechat.ui.theme.FreeChatColors,
    s: AppStrings,
    scrollKey: String,
    levelScrolls: MutableMap<String, Int>,
    pool: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            // 果冻阻尼回弹只作用于池子；悬浮头不跟弹（工单原话：不在阻尼回弹动画范畴内）
            .jellyOverscroll()
            .verticalScroll(com.freechat.ui.animation.rememberPersistentScrollState(scrollKey, levelScrolls)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 头部卡片区占位（4+48+12dp）：池子从卡片下方起步，上滑时内容从卡片底下钻过去
        Spacer(modifier = Modifier.height(64.dp))
        pool()
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// —— 模型列表通用组件 ——
@Composable
private fun AddModelButton(onClick: () -> Unit, colors: com.freechat.ui.theme.FreeChatColors, s: AppStrings) {
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = LocalSettingsHazeState.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.addModel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = colors.Primary)
        }
    }
}

@Composable
private fun EmptyModelHint(text: String, colors: com.freechat.ui.theme.FreeChatColors) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextTertiary)
    }
}

@Composable
private fun ModelItemRow(
    model: ModelInfo,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: (ModelInfo) -> Unit,
    colors: com.freechat.ui.theme.FreeChatColors
) {
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = LocalSettingsHazeState.current
    val s = LocalStrings.current
    // 选中态过渡动画与全 App 选项表同节奏（1.1.28，frostedCard 内部渐染 + 文字渐变）
    val titleColor by animateColorAsState(if (selected) colors.selectedText else colors.TextPrimary, com.freechat.ui.animation.FreeChatAnimation.colorTween, label = "modelRowTitle")
    val subColor by animateColorAsState(if (selected) colors.selectedSubText else colors.TextSecondary, com.freechat.ui.animation.FreeChatAnimation.colorTween, label = "modelRowSub")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp), selected = selected)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(model.displayName, fontFamily = LocalMonoFontFamily.current, style = MaterialTheme.typography.bodyLarge, color = titleColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                // 内置模型备注尾句「作者自用 API…」统一红字（1.1.28）
                Text(
                    com.freechat.ui.components.modelDescAnnotated(
                        com.freechat.i18n.localizedModelDesc(model, s),
                        base = subColor,
                        red = colors.ErrorRed
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp)
                )
            }
            // Beta 1.0.96: every built-in type is selectable, never editable.
            // 1.1.26.5：非开源区放行的内置编辑入口
            if (com.freechat.data.ModelAccessPolicy.canEdit(model) ||
                com.freechat.dev.DevMode.canEditBuiltIn(LocalContext.current)
            ) {
                IconButton(onClick = { onEdit(model) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Edit, s.editModel, tint = if (selected) colors.selectedSubText else colors.TextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AiMemoryPage(
    viewModel: ChatViewModel,
    colors: com.freechat.ui.theme.FreeChatColors,
    s: AppStrings,
    autoSummarizeMemory: Boolean,
    globalMemories: List<String>,
    onEditGlobalMemory: () -> Unit
) {
    Spacer(modifier = Modifier.height(4.dp))

    // Memory 总结
    SettingsRow {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(s.memorySummary, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                    Text(s.memorySummaryDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                }
            }
            Switch(
                checked = autoSummarizeMemory,
                onCheckedChange = { viewModel.setAutoSummarizeMemory(it) },
                colors = switchColors(colors)
            )
        }
    }

    // 全局记忆
    SettingsRow {
        Row(
            Modifier.fillMaxWidth().clickable { onEditGlobalMemory() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Bookmark, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(s.globalMemory, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                    Text(
                        if (globalMemories.isEmpty()) s.globalMemoryDesc
                        else "${globalMemories.size} · ${s.globalMemoryDesc}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                        color = colors.TextSecondary
                    )
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FontPage(
    viewModel: ChatViewModel,
    colors: com.freechat.ui.theme.FreeChatColors,
    s: AppStrings,
    useSystemFont: Boolean,
    fontSize: FontSize,
    onOpenFontSize: () -> Unit
) {
    Spacer(modifier = Modifier.height(4.dp))

    // 字体优化（开关反转：打开 = 使用内置 HYSongYunLangHei 字体）
    SettingsRow {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.TextFields, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(s.fontOptimize, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                    Text(s.fontOptimizeDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                }
            }
            Switch(
                checked = !useSystemFont,
                onCheckedChange = { viewModel.setUseSystemFont(!it) },
                colors = switchColors(colors)
            )
        }
    }

    // 字体大小
    SettingsRow {
        Row(
            Modifier.fillMaxWidth().clickable { onOpenFontSize() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FormatSize, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(s.fontSize, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                    Text(fontSizeLabel(fontSize, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

// ============ 关于 FreeChat（聚合页：logo + 版本 + 检查更新 + 三个入口）============
/**
 * 1.1.10 新增：原来散在设置页的「更新日志 / 关于作者 / 用户协议与使用条款」三项
 * 集成进「关于FreeChat」二级页；顶部 logo + 版本号 + 居中「检查更新」按钮。
 * 1.1.29 工单⑥：检查与下载拆开——「检查更新」只查；发现新版自动拉 Update.md 插在
 * logo↔选项卡之间（MarkdownText 保格式），版本号换新版号，按钮变「获取更新」并悬浮
 * （贴屏底但始终在选项卡上方），点击才下载安装。
 */
@Composable
private fun AboutFreeChatPage(
    colors: com.freechat.ui.theme.FreeChatColors,
    s: AppStrings,
    levelScrolls: MutableMap<String, Int>,
    onOpenChangelog: () -> Unit,
    onOpenAgreement: () -> Unit,
    onOpenAuthor: () -> Unit,
    onOpenDevPassword: () -> Unit = {},
    onOpenDevOptions: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    // 1.1.29 工单⑥：检查与下载拆开 —— 查到新版先展 Update.md，点「获取更新」才下载安装
    // 1.1.29.5：检测结果跨页保留（AboutUpdateStore）——重进页面走折叠态，不再整篇砸给用户
    var available by remember { mutableStateOf(AboutUpdateStore.latest) }
    var notes by remember { mutableStateOf(AboutUpdateStore.notes) }
    /** 本页会话刚查到=完整揭示（按钮滑落悬浮+日志展开）；重进（store 有货）=折叠态 */
    var sessionReveal by remember { mutableStateOf(false) }
    /** 重进折叠态：下箭头子菜单键的展开开关 */
    var streamExpanded by remember { mutableStateOf(false) }
    // 非开源区接线（1.1.26.2）：入口与解锁逻辑在非开源包，开源面只留调用点
    val devTapGate = remember { com.freechat.dev.DevTapGate() }

    /** 获取更新：下载 → 安装包校验（包名/编译版本号一致）→ 拉起系统安装器 */
    val getUpdate: () -> Unit = {
        val latest = available
        if (latest != null && !checking) {
            checking = true
            updateStatus = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    val file = File(context.cacheDir, "freechat_update.apk")
                    runCatching {
                        AppUpdateChecker.download(AppUpdateChecker.channelMarked(latest.apkUrl), file)
                        val pkg = AppUpdateChecker.archivePackageName(context.packageManager, file.absolutePath)
                        val code = AppUpdateChecker.archiveVersionCode(context.packageManager, file.absolutePath)
                        if (code == null || !AppUpdateChecker.archiveMatches(latest, pkg, code))
                            error("archive mismatch")
                        context.startActivity(AppUpdateChecker.installIntent(context, file))
                        Result.success("install")
                    }.getOrElse { Result.failure(it) }
                }
                updateStatus = when {
                    result.getOrNull() == "install" -> s.updateInstalling
                    result.exceptionOrNull()?.message == "archive mismatch" -> s.updateVerifyFailed
                    else -> s.updateFailed
                }
                checking = false
            }
        }
    }

    /** 检查更新：只查不下载；发现新版自动拉 Update.md 展示在 logo 与选项卡之间 */
    val checkUpdate: () -> Unit = {
        if (!checking && available == null) {
            checking = true
            updateStatus = s.updateChecking
            scope.launch {
                val latest = withContext(Dispatchers.IO) { AppUpdateChecker.fetchLatest(BuildConfig.APPLICATION_ID) }
                // 1.1.29.3 ②：开发者「版本获取更新效果测试」开启时永远判定有新版（只加不减：
                // 真实检测逻辑一字不动），供检验 Update.md 日志的显示与渲染
                val forceTest = com.freechat.dev.DevMode.testUpdateFetch(context)
                when {
                    latest == null && !forceTest -> updateStatus = s.updateFailed
                    !forceTest && !AppUpdateChecker.needsUpdate(BuildConfig.VERSION_CODE.toLong(), latest!!) ->
                        updateStatus = s.updateLatest
                    else -> {
                        val found = latest ?: AppLatest(
                            BuildConfig.APPLICATION_ID,
                            "Beta ${BuildConfig.VERSION_NAME}·更新测试",
                            BuildConfig.VERSION_CODE + 1L,
                            AppUpdateChecker.LATEST_URL
                        )
                        val text = withContext(Dispatchers.IO) { AppUpdateChecker.fetchUpdateNotes() }
                        available = found
                        notes = text
                        // 1.1.29.5：结果入跨页仓（重进折叠态）；本页会话=完整揭示
                        AboutUpdateStore.latest = found
                        AboutUpdateStore.notes = text
                        sessionReveal = true
                        updateStatus = null
                    }
                }
                checking = false
            }
        }
    }

    // ===== 1.1.29.8 工单①D：按钮=悬浮钉位 =====
    // 检查前跟锚走（中点原位）；揭示瞬间从原位**滑落**屏幕底部（520ms 非线性，看得见的过渡）；
    // 之后吸底悬浮跟随滚动，读到日志尾（锚入视口）入坞贴住，上滑出坞再吸回底部同位
    var btnAnchorY by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var boxBottomPx by remember { mutableFloatStateOf(0f) }
    var btnHeightPx by remember { mutableFloatStateOf(0f) }
    val glide = remember { Animatable(1f) }
    var glideFromY by remember { mutableFloatStateOf(Float.NaN) }
    val updateNotes = notes
    val reentry = updateNotes != null && !sessionReveal
    LaunchedEffect(updateNotes != null) {
        // 揭示旅程只跑一次（重进折叠态不跑——那颗是流内固定按钮）
        if (updateNotes != null && sessionReveal && glide.value >= 0.999f && btnAnchorY.isFinite()) {
            glideFromY = btnAnchorY
            glide.snapTo(0f)
            glide.animateTo(1f, tween(520, easing = FreeChatAnimation.arrivalEase))
        }
    }

    Box(Modifier.fillMaxSize()
        .onGloballyPositioned { boxBottomPx = it.positionInRoot().y + it.size.height }) {
        Column(
            // 滚动收进本页（工单⑥）；1.1.29.5 工单A/C：上下留白收进滚动内容，滚动视口=全屏——
            // 日志上滚进标题模糊带渐隐（A）、下滚盖满全屏不剩底部空白色块（C）
            modifier = Modifier.fillMaxSize()
                .jellyOverscroll()
                .verticalScroll(com.freechat.ui.animation.rememberPersistentScrollState("sub_ABOUT_FREECHAT", levelScrolls))
                // 1.1.26.1：logo 上方留出更大空白（原 56dp）+ 标题栏高度；只挪位置，尺寸一概不动
                .padding(top = HazeSpec.topContentPaddingDp(
                    androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()) + 56.dp,
                    bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 主 logo（简F）；非开源区门槛接线
            Image(
                painter = painterResource(R.drawable.freechat_icon_blue_f),
                contentDescription = "FreeChat",
                modifier = Modifier.size(112.dp).clip(RoundedCornerShape(28.dp))
                    .clickable {
                        if (devTapGate.onTap(System.currentTimeMillis())) {
                            // 已是解锁态则不再进二级流程（1.1.26.9），提示一句即可
                            if (com.freechat.dev.DevMode.isUnlocked(context)) {
                                android.widget.Toast.makeText(context, "当前已进入开发者模式", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                onOpenDevPassword()
                            }
                        }
                    }
            )
            Spacer(Modifier.height(20.dp))
            // Beta 版直书「Beta x.x.x」，Beta 标不能省（2026-10-11 用户点名）；正式版仍作「Version x.x.x」
            // 检测到新版后版本号就地换成新版号（工单⑥）
            val shownVersion = available?.versionName ?: BuildConfig.VERSION_NAME
            Text(
                if (shownVersion.startsWith("Beta")) shownVersion else "Version $shownVersion",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.TextSecondary
            )
            // ===== 1.1.29.8 工单①：更新区（B 间距呼吸 / D 过渡+吸底悬浮 / 重进折叠态）=====
            val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
            val aboutGapDp = ((screenHeightDp * 0.55f) - 330f).coerceAtLeast(120f)
            // 卡片下移/日志展开/按钮滑落同一条曲线同拍（arrivalEase 非线性，看得见的过渡）
            val moveSpec = tween<Dp>(520, easing = FreeChatAnimation.arrivalEase)

            if (reentry) {
                // ===== 重进折叠态（工单）：按钮固定 logo/版本号下方，下箭头子菜单展开文字流 =====
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = getUpdate,
                    enabled = !checking,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.Primary, contentColor = colors.selectedText)
                ) {
                    Text(if (checking) s.updateChecking else s.updateGet, style = MaterialTheme.typography.bodyLarge)
                }
                updateStatus?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.TextTertiary)
                }
                // 子菜单键：点按展开/收起更新内容文字流（翻转箭头示意开合）
                val arrowSpin by animateFloatAsState(
                    if (streamExpanded) 180f else 0f, tween(300, easing = FreeChatAnimation.arrivalEase),
                    label = "aboutStreamArrow")
                IconButton(onClick = { streamExpanded = !streamExpanded }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown, null, tint = colors.TextTertiary,
                        modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = arrowSpin }
                    )
                }
                AnimatedVisibility(
                    visible = streamExpanded,
                    enter = fadeIn(tween(450, easing = FreeChatAnimation.arrivalEase)) +
                        expandVertically(tween(520, easing = FreeChatAnimation.arrivalEase), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(280))
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(4.dp))
                        UpdateNotesStream(updateNotes.orEmpty(), s, colors)
                        Spacer(Modifier.height(28.dp)) // 展开尾部留白：呼吸感
                    }
                }
                Spacer(Modifier.height(28.dp))
            } else {
                val gapTop by animateDpAsState(
                    if (updateNotes == null) (aboutGapDp * 0.5f).dp else 20.dp, moveSpec, label = "aboutGapTop")
                val gapMid by animateDpAsState(
                    if (updateNotes == null) 0.dp else 28.dp, moveSpec, label = "aboutGapMid")
                val gapBottom by animateDpAsState(
                    if (updateNotes == null) (aboutGapDp * 0.5f).dp else 32.dp, moveSpec, label = "aboutGapBottom")
                Spacer(Modifier.height(gapTop))
                AnimatedVisibility(
                    visible = updateNotes != null,
                    enter = fadeIn(tween(450, easing = FreeChatAnimation.arrivalEase)) +
                        expandVertically(tween(520, easing = FreeChatAnimation.arrivalEase), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(280))
                ) {
                    UpdateNotesStream(updateNotes.orEmpty(), s, colors)
                }
                Spacer(Modifier.height(gapMid))
                // 流内按钮槽位（不可见锚）：检查前=中点原位；揭示后=日志尾入坞位
                Box(Modifier.fillMaxWidth().height(48.dp)
                    .onGloballyPositioned { btnAnchorY = it.positionInRoot().y })
                updateStatus?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.TextTertiary)
                }
                Spacer(Modifier.height(gapBottom))
            }
            SettingsRow {
                Column {
                    AboutEntryRow(s.changelog, colors) { onOpenChangelog() }
                    HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    AboutEntryRow(s.authorAboutTitle, colors) { onOpenAuthor() }
                    HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    AboutEntryRow(s.userAgreement, colors) { onOpenAgreement() }
                    // 解锁态才出现的附加项（非开源区控制）
                    if (com.freechat.dev.DevMode.isUnlocked(context)) {
                        HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                        AboutEntryRow(com.freechat.dev.DevMode.TITLE_OPTIONS, colors) { onOpenDevOptions() }
                    }
                }
            }
            // 「FreeChat从夯到拉」评分卡（1.1.28，非开源）：页面底部独立卡片，提交后粒子消散永不再现
            Spacer(Modifier.height(28.dp)) // 1.1.29.8：选项卡↔评分卡拉开呼吸留白（原 12dp）
            com.freechat.dev.RatingCard(
                colors = colors,
                hazeState = LocalSettingsHazeState.current,
                advancedMaterial = LocalAdvancedMaterial.current,
                isDark = colors.TextPrimary.luminance() > 0.5f
            )
        }

        // ===== 悬浮「检查更新/获取更新」按钮（重进折叠态不走这——那边是流内固定按钮）=====
        if (!reentry) {
            Button(
                onClick = { if (available == null) checkUpdate() else getUpdate() },
                enabled = !checking,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.Primary, contentColor = colors.selectedText),
                modifier = Modifier.align(Alignment.TopCenter)
                    .onGloballyPositioned { btnHeightPx = it.size.height.toFloat() }
                    .offset {
                        val h = if (btnHeightPx > 0f) btnHeightPx else 48.dp.toPx()
                        val floatY = (boxBottomPx - 24.dp.toPx() - h).coerceAtLeast(0f)
                        // 吸底悬浮（跟滚即跟）；锚进视口=入坞贴日志尾；上滑出坞再吸回底部同位
                        val sticky = kotlin.math.min(btnAnchorY, floatY)
                        val y = if (glide.value < 1f && glideFromY.isFinite())
                            androidx.compose.ui.util.lerp(glideFromY, sticky, glide.value) else sticky
                        IntOffset(0, y.roundToInt().coerceAtLeast(0))
                    }
            ) {
                Text(
                    if (checking) s.updateChecking else if (available == null) s.checkUpdate else s.updateGet,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

/**
 * 1.1.29.8：检测结果跨「关于」页会话保留（进程内存）——重进页面走折叠态，
 * 不再把整篇更新日志重新砸给用户。
 */
private object AboutUpdateStore {
    var latest: AppLatest? = null
    var notes: String? = null
}

/**
 * 更新内容文字流（1.1.29.8）：顶部「更新内容」主题色小标题（左对齐）+ 各大项前主题色竖条，
 * 与「更新日志」页 ChangelogSection 同一套版式。行格式=Update.md：`## 大项` / `- 条目` / `_附注_`。
 */
@Composable
private fun UpdateNotesStream(
    notes: String,
    s: AppStrings,
    colors: com.freechat.ui.theme.FreeChatColors
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            s.updateContentTitle,
            style = MaterialTheme.typography.titleMedium,
            color = colors.Primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(14.dp))
        parseUpdateSections(notes).forEach { (title, items) ->
            ChangelogSection(title, items, colors.Primary, colors)
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** Update.md → (大项标题, 条目)：`## ` 分节、`- ` 条目、`_附注_` 并入条目流。 */
private fun parseUpdateSections(notes: String): List<Pair<String, List<String>>> {
    val out = mutableListOf<Pair<String, MutableList<String>>>()
    var cur: MutableList<String>? = null
    notes.lines().forEach { raw ->
        val line = raw.trim()
        when {
            line.startsWith("## ") -> {
                val list = mutableListOf<String>()
                out.add(line.removePrefix("## ").trim() to list)
                cur = list
            }
            line.startsWith("- ") -> cur?.add(line.removePrefix("- ").removeSurrounding("_").trim())
            line.isBlank() -> {}
            else -> cur?.add(line.removeSurrounding("_").trim())
        }
    }
    return out.map { it.first to it.second.toList() }
}

@Composable
private fun AboutEntryRow(label: String, colors: com.freechat.ui.theme.FreeChatColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
    }
}

// ============ 关于作者 ============

/**
 * 「关于作者」页（1.0.49 重排过一次）。
 *
 * 排版意图（用户明确要求）：**大标题带渐变、上下都留足空间、问题与回答必须一眼分得开**。
 * 所以这一页刻意**不套 [SettingsRow] 卡片** —— 卡片会把留白切成一块一块，
 * 问答的呼吸感当场就没了。整页只走纯文本流，靠间距 + 竖条 + 颜色分层，
 * 语言与「版本更新」页保持一致（竖条 + 主题色粗体做小标题）：
 *
 *   大标题（渐变 32sp）→ 44dp
 *   每条问答：❙ 问题 15sp 粗体主题色 → 9dp → 回答 18sp/30sp 正文色（缩进 12dp）→ 30dp 到下一题
 *   外链 → 36dp → 「联系我」→ 二维码（透明底直接落在背景上，长按可保存）
 *
 * 问题与回答的区分用了三重：**竖条**（结构）、**颜色**（主题色 vs 正文色，
 * 纯白主题下是 #333333 对纯黑）、**缩进 + 字号**（回答缩进 12dp、大一号）。
 * 只靠颜色的话，纯白主题里两者都偏黑，看着还是一坨。
 *
 * 页面本身被外层包在 `padding(horizontal = 16.dp)` + `verticalScroll` 里，
 * 字号不在这里换算，直接写死 —— 这一页是给人读的，不该跟着聊天正文字号档位走
 * （那样小档位下标题会比回答还小）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AuthorPage(
    colors: com.freechat.ui.theme.FreeChatColors,
    s: AppStrings,
    isDark: Boolean = false,
    onQrLongPress: () -> Unit = {},
    onOpenAuthorWords: () -> Unit = {}
) {
    val context = LocalContext.current
    // 这一页里所有外链都走同一个口子，省得每处再写一遍 Intent + runCatching
    val open: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    // 1.0.50：标题上下都再放宽一档（36→60 / 44→72）—— 这一页只有标题 + 问答 + 链接三段，
    // 之前标题上下的留白跟正文段落之间差不多，一进页面标题像被挤在墙角。
    Spacer(Modifier.height(60.dp))

    // 大标题：**固定**的金→蓝渐变（1.0.50 改），不再跟主题色走。
    //
    // 原先取的是 `colors.Primary → Primary@50%`：主题色本身是**单色系**，渐变出来只是同色由深到浅，
    // 棕主题是两团棕、白主题几乎看不出渐变；而且十来套主题都得各看一眼会不会脏。
    // 现在钉死成金色→蓝色：色相跨得开，任何主题下都看得出「这是一条渐变」，
    // 深浅主题各一套明度（不靠透明度做浅色端 —— 淡到 50% 会透出背景，字会花）。
    //
    // 「柔和」的做法：两端都取**中明度、低饱和**的色（金 ≈ #C9A24E / 蓝 ≈ #5F8FD0，不是亮金亮蓝），
    // 中间不插停点、让 sRGB 自己插 —— 这两个色在色环上相隔不远不近，插值路径干净，不会掉进灰绿。
    // 字号 32→40sp、字重 Bold→Black（900）：标题是这一页唯一的视觉锚点，要压得住下面两栏问答。
    val titleGold = if (isDark) Color(0xFFE8C87A) else Color(0xFFC9A24E)
    val titleBlue = if (isDark) Color(0xFF8FB6E8) else Color(0xFF5F8FD0)
    Text(
        s.authorPageTitle,
        style = TextStyle(
            // 默认 start/end = 左上 → 右下（Offset.Infinite 由绘制边界推出来），斜向比横切更自然
            brush = Brush.linearGradient(listOf(titleGold, titleBlue)),
            fontSize = 40.sp,
            lineHeight = 50.sp,
            fontWeight = FontWeight.Black
        )
    )

    // 标题下面留一大段：题目与问答区之间要有呼吸，不然一进页面就顶到脸上
    Spacer(Modifier.height(72.dp))

    s.authorQa.forEachIndexed { idx, qa ->
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                // 问题前面的竖线：比小标题那条细一点、短一点，只做指引不喧宾夺主
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .width(3.dp)
                        .height(15.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.Primary.copy(alpha = 0.8f))
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    qa.q,
                    color = colors.Primary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(9.dp))
            // 回答：缩进到与问题文字左对齐（竖线 3 + 间距 9 = 12dp），
            // 字号比问题大一号、颜色用正文色 —— 结构 / 颜色 / 字号三重区分
            Text(
                qa.a,
                color = colors.TextPrimary,
                fontSize = 18.sp,
                lineHeight = 30.sp,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        if (idx != s.authorQa.lastIndex) Spacer(Modifier.height(30.dp))
    }

    // 「作者的话」（1.1.0）：问答区第四席 —— 长文不直接铺在页里，点进二级页看全文
    Spacer(Modifier.height(30.dp))
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .width(3.dp)
                    .height(15.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.Primary.copy(alpha = 0.8f))
            )
            Spacer(Modifier.width(9.dp))
            Text(
                s.authorWordsTitle,
                color = colors.Primary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            s.authorWordsOpen,
            color = colors.Primary,
            fontSize = 18.sp,
            lineHeight = 30.sp,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .padding(start = 12.dp)
                .clickable { onOpenAuthorWords() }
        )
    }

    // 问答区与「官方网页 / GitHub」两块链接之间：先空一段、再一条分割线、再空一段。
    // 这两段性质不同（正文 vs 外链），中间只隔空白时容易被读成同一段，细线把两段分开；
    // 线本身用主题色的低透明度，跟卡片描边一个路子，不抢眼。
    Spacer(Modifier.height(48.dp))
    HorizontalDivider(
        color = colors.Primary.copy(alpha = 0.18f),
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
    Spacer(Modifier.height(44.dp))

    AuthorLink(
        label = s.authorWebAddress,
        url = "https://freechater.com/",
        note = s.authorWebNote,
        colors = colors,
        onOpen = open
    )

    Spacer(Modifier.height(30.dp))

    AuthorLink(
        label = s.authorGithubRepo,
        url = "https://github.com/Belate-stay/FreeChat",
        note = s.authorGithubNote,
        colors = colors,
        onOpen = open
    )

    Spacer(Modifier.height(30.dp))

    // CSDN 主页（1.1.0）：官网与 GitHub 之间夹不进去、按用户口径放 GitHub 与联系区之间，同款外链样式
    AuthorLink(
        label = s.authorCsdn,
        url = "https://blog.csdn.net/weixin_51354748",
        note = s.authorCsdnNote,
        colors = colors,
        onOpen = open
    )

    Spacer(Modifier.height(36.dp))

    // ===== 联系我（二维码）=====
    Text(
        s.authorContact,
        color = colors.Primary,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(16.dp))
    // 二维码换成了**透明底**那张（用户抠过图），直接落在页面背景上，不再垫白卡片 ——
    // 白卡会在纯白主题里出现一圈「白上加白」的边，深色主题里更是一块刺眼的方块。
    // 只有深色主题需要垫一层浅底：模块是 75% 的黑，落在深色背景上等于看不见、扫不出来。
    // 长按弹保存弹层（弹层在 SettingsScreen 的根 Box 里，见 showQrSaveSheet）。
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isDark) Modifier.background(Color.White.copy(alpha = 0.9f)).padding(12.dp)
                        else Modifier
                    )
                    .combinedClickable(onClick = {}, onLongClick = onQrLongPress)
            ) {
                Image(
                    painter = painterResource(R.drawable.author_qr_t),
                    contentDescription = s.authorContact,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(196.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            // 长按这件事没人猜得到，写一行小字告诉用户
            Text(
                s.authorQrHint,
                color = colors.TextTertiary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * 一行「标签 + 可点链接 + 小注」。
 *
 * 链接用主题色 + 下划线，是为了让人一眼看出**能点**（这一页全是只读文字，
 * 不给点暗示的话没人会去戳）。
 */
@Composable
private fun AuthorLink(
    label: String,
    url: String,
    note: String,
    colors: com.freechat.ui.theme.FreeChatColors,
    onOpen: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            color = colors.Primary,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            url,
            color = colors.Primary,
            fontSize = 18.sp,
            lineHeight = 28.sp,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { onOpen(url) }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            note,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
            color = colors.TextSecondary
        )
    }
}

/**
 * 取出作者页二维码的**白底原图**（author_qr.jpg）交给保存用。
 *
 * 为什么不用展示的那张透明图：相册里透明背景会变成黑的（看图的 App 铺黑底），
 * 黑白颠倒的二维码扫不出来。展示用透明的，保存用这张白底的。
 *
 * 为什么不做二值化：透明那张的模块只有 75% 不透明度，按 alpha 阈值切会连中间那个 logo
 * 一起切 —— 圆标里的半透明像素全被烧成纯黑（1.0.49 的 bug，用户反馈「中间变成黑块」）。
 * 白底原图自带干净的黑白，直接解出来就是成品。
 */
private fun renderQrOnWhite(res: Resources): Bitmap? = runCatching {
    // 1.0.50：直接解那张**不透明原图**（author_qr.jpg，白底、和展示的透明那张是同一张码：
    // 逐模块比对过，相关度 0.999、二值化差异 0.43%，只差抗锯齿的灰边）。
    //
    // 上一版走的是「拿透明那张按 alpha 阈值二值化」：那会把**中间那个 logo** 按 alpha 切开 ——
    // 圆标里凡是半透明的像素一律被烧成纯黑，存出去的码中间糊成一块黑疙瘩（用户反馈）。
    // 原图本来就自带白底，解出来就是干净的黑白，不需要再自己拼。
    BitmapFactory.decodeResource(res, R.drawable.author_qr)
}.getOrNull()

// ============ 通用小组件 ============
@Composable
private fun ModelSelectCard(
    model: ModelInfo,
    selected: Boolean,
    colors: com.freechat.ui.theme.FreeChatColors,
    onClick: () -> Unit
) {
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = LocalSettingsHazeState.current
    val s = LocalStrings.current
    // 选中态过渡动画与全 App 选项表同节奏（1.1.28）
    val titleColor by animateColorAsState(if (selected) colors.selectedText else colors.TextPrimary, com.freechat.ui.animation.FreeChatAnimation.colorTween, label = "selCardTitle")
    val subColor by animateColorAsState(if (selected) colors.selectedSubText else colors.TextSecondary, com.freechat.ui.animation.FreeChatAnimation.colorTween, label = "selCardSub")
    Box(
        Modifier.fillMaxWidth()
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp), selected = selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    model.displayName,
                    fontFamily = LocalMonoFontFamily.current,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                // 内置模型备注尾句「作者自用 API…」统一红字（1.1.28）
                Text(
                    com.freechat.ui.components.modelDescAnnotated(
                        com.freechat.i18n.localizedModelDesc(model, s),
                        base = subColor,
                        red = colors.ErrorRed
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp)
                )
            }
        }
    }
}

@Composable
private fun DarkThemeChip(label: String, selected: Boolean, colors: com.freechat.ui.theme.FreeChatColors, onClick: () -> Unit) {
    // 选中态过渡动画与全 App 选项表同节奏（1.1.28）
    val fill by animateColorAsState(if (selected) colors.selectedFill else colors.Surface, com.freechat.ui.animation.FreeChatAnimation.colorTween, label = "chipFill")
    val textColor by animateColorAsState(if (selected) colors.selectedText else colors.TextSecondary, com.freechat.ui.animation.FreeChatAnimation.colorTween, label = "chipText")
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            // 选中态与全 App 的选项表同一支颜色（1.0.50 统一，见 selectedFill）
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun switchColors(colors: com.freechat.ui.theme.FreeChatColors) = SwitchDefaults.colors(
    checkedThumbColor = colors.OnPrimary,
    checkedTrackColor = colors.Primary,
    uncheckedThumbColor = colors.TextTertiary,
    uncheckedTrackColor = colors.SurfaceVariant
)

// ============ 辅助函数 ============

private fun tempLabel(mode: TempMode, s: AppStrings): String = when (mode) {
    TempMode.AUTO -> s.tempAuto
    TempMode.WARM -> s.tempWarm
    TempMode.OBJECTIVE -> s.tempObjective
}

private fun lengthLabel(mode: LengthMode, s: AppStrings): String = when (mode) {
    LengthMode.AUTO -> s.lengthAuto
    LengthMode.FULL -> s.lengthFull
    LengthMode.CONCISE -> s.lengthConcise
}

private fun themeLabel(mode: ThemeMode, s: AppStrings): String = when (mode) {
    ThemeMode.SYSTEM -> s.themeSystem
    ThemeMode.LIGHT -> s.themeLight
    ThemeMode.DARK -> s.themeDark
    ThemeMode.DARK_OLED -> s.themeDarkOled
}

private fun colorThemeLabel(ct: ColorTheme, s: AppStrings): String = when (ct) {
    ColorTheme.BROWN -> s.colorThemeBrown
    ColorTheme.BLUE -> s.colorThemeBlue
    ColorTheme.WHITE -> s.colorThemeWhite
    ColorTheme.CUSTOM -> s.colorThemeCustom
    ColorTheme.PINE -> s.colorThemePine
    ColorTheme.CORAL -> s.colorThemeCoral
}

private fun colorThemeSwatch(ct: ColorTheme, customArgb: Int): Color = when (ct) {
    ColorTheme.BROWN -> com.freechat.ui.theme.LightColors.Primary
    ColorTheme.BLUE -> com.freechat.ui.theme.BlueLightColors.Primary
    ColorTheme.WHITE -> Color.White
    ColorTheme.CUSTOM -> Color(customArgb)
    ColorTheme.PINE -> Color(0xFF26735A)
    ColorTheme.CORAL -> Color(0xFFA3444B)
}

private fun themeDesc(mode: ThemeMode, s: AppStrings): String = when (mode) {
    ThemeMode.SYSTEM -> s.themeSystemDesc
    ThemeMode.LIGHT -> s.themeLightDesc
    ThemeMode.DARK -> s.themeDarkDesc
    ThemeMode.DARK_OLED -> s.themeDarkOledDesc
}

private fun langLabel(lang: AppLanguage, s: AppStrings): String = when (lang) {
    AppLanguage.SYSTEM -> s.langSystem
    AppLanguage.ZH_CN -> s.langZhCN
    AppLanguage.ZH_TW -> s.langZhTW
    AppLanguage.EN -> s.langEn
}

private fun fontSizeLabel(fs: FontSize, s: AppStrings): String = when (fs) {
    FontSize.SMALL -> s.fontSizeSmall
    FontSize.MEDIUM -> s.fontSizeMedium
    FontSize.LARGE -> s.fontSizeLarge
    FontSize.XLARGE -> s.fontSizeXlarge
    FontSize.XXLARGE -> s.fontSizeXxlarge
}

private fun inputStyleLabel(style: InputStyle, s: AppStrings): String = when (style) {
    InputStyle.COMPACT -> s.inputStyleCompact
    InputStyle.COMPLETE -> s.inputStyleComplete
}

private fun inputBarStateLabel(state: InputBarState, s: AppStrings): String = when (state) {
    InputBarState.AUTO_HIDE -> s.inputBarStateAutoHide
    InputBarState.PINNED -> s.inputBarStatePinned
}

private fun fontSizePercent(fs: FontSize): String = "${(fs.scale * 100).roundToInt()}%"

/** A single card grows its children with the app-wide disclosure curve. */
@Composable
internal fun SettingsGroup(
    expanded: Boolean = true,
    parent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LocalFreeChatColors.current
    SettingsRow {
        Column {
            parent()
            AnimatedVisibility(expanded, enter = FreeChatAnimation.expandEnter(), exit = FreeChatAnimation.expandExit()) {
                Column {
                    SettingsDivider(colors)
                    content()
                }
            }
        }
    }
}

@Composable
internal fun SettingsDivider(colors: com.freechat.ui.theme.FreeChatColors) {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.Divider.copy(alpha = 0.4f))
}

@Composable
internal fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    colors: com.freechat.ui.theme.FreeChatColors,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .then(if (onClick != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
            if (!subtitle.isNullOrBlank()) Text(subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                color = colors.TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        if (trailing != null) trailing()
        else if (onClick != null) Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun SectionLabel(icon: ImageVector, title: String) {
    val colors = LocalFreeChatColors.current
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.TextPrimary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = colors.TextPrimary)
    }
}

@Composable
internal fun SettingsRow(content: @Composable () -> Unit) {
    val advancedMaterial = LocalAdvancedMaterial.current
    val colors = LocalFreeChatColors.current
    val hazeState = LocalSettingsHazeState.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp))
    ) {
        content()
    }
}
