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
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.BuildConfig
import com.freechat.R
import com.freechat.data.SerpApiPool
import com.freechat.i18n.AppLanguage
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.model.ColorTheme
import com.freechat.model.FontSize
import com.freechat.model.LengthMode
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.TempMode
import com.freechat.model.ThemeMode
import com.freechat.model.ChatMode
import com.freechat.sync.Session
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.components.SheetActionRow
import com.freechat.ui.components.SheetOption
import com.freechat.ui.components.SheetPanel
import com.freechat.ui.theme.LocalMonoFontFamily
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.selectedFill
import com.freechat.ui.theme.selectedSubText
import com.freechat.ui.theme.selectedText
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.theme.frostedGlass
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeProgressive
import com.freechat.viewmodel.ChatViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.theme.pageHeaderBackground
import com.freechat.util.saveBitmapToGallery
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

private enum class SubScreen { LANGUAGE_MODEL, VISUAL_MODELS, VOICE_MODELS, AI_MEMORY, FONT, AUTHOR }

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
    onOpenAgreement: () -> Unit = {}
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedVisualModel by viewModel.selectedVisualModel.collectAsState()
    val selectedVisionModel by viewModel.selectedVisionModel.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()
    val tempMode by viewModel.tempMode.collectAsState()
    val lengthMode by viewModel.lengthMode.collectAsState()
    val enableWebSearch by viewModel.enableWebSearch.collectAsState()
    val showThinking by viewModel.showThinking.collectAsState()
    val autoSummarizeMemory by viewModel.autoSummarizeMemory.collectAsState()
    val useSystemFont by viewModel.useSystemFont.collectAsState()
    val globalMemories by viewModel.globalMemories.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val advancedMaterial by viewModel.advancedMaterial.collectAsState()
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
    /** 长按作者页二维码 → 保存弹层（同样是底部磨砂玻璃，渲染在下面根 Box 的末尾） */
    var showQrSaveSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

    var subScreen by remember { mutableStateOf<SubScreen?>(null) }
    // 主列表滚动状态提升到子页切换之外，返回时保持原滚动位置（跨页返回也保持）
    val mainScrollState = rememberScrollState(initial = viewModel.settingsScrollPosition.value)
    LaunchedEffect(mainScrollState) {
        snapshotFlow { mainScrollState.value }
            .collect { viewModel.saveSettingsScrollPosition(it) }
    }

    // 子页返回键：优先返回设置主列表
    BackHandler(enabled = subScreen != null) { subScreen = null }

    // 关闭联网搜索的确认 —— 也是底部磨砂玻璃弹层，渲染在下面根 Box 的末尾。
    // 确认既然只有「仍然关闭」一条路，主按钮就直接写这句话（不再是「确定/取消」那种含糊的两个键）

    // 主题 / 色彩 / 回复温度 / 回复长度 / 语言 / 字号 / 全局记忆 —— 七个二级弹层，
    // 全部改成**窗口内**的底部磨砂玻璃弹层，实际渲染在下面根 Box 的末尾（见 SheetPanel）。
    // 不能再用 AlertDialog：它是一个独立窗口，看不到 App 自己画的内容，
    // 想「模糊背景」是做不到的 —— 独立窗口的 Dialog 只能把背景压暗，没有第二种选择。

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val density = LocalDensity.current
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp).toPx() }

    CompositionLocalProvider(LocalSettingsHazeState provides hazeState) {
    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        AnimatedContent(
            targetState = subScreen,
            transitionSpec = {
                (fadeIn(FreeChatAnimation.pageFadeInFast) +
                    scaleIn(initialScale = 0.97f, animationSpec = tween(280, easing = FreeChatAnimation.iosEaseOut))) togetherWith
                    (fadeOut(FreeChatAnimation.pageFadeOutFast) +
                        scaleOut(targetScale = 0.97f, animationSpec = tween(200, easing = FreeChatAnimation.iosEaseIn)))
            },
            label = "settings_subpage"
        ) { screen ->
        if (screen == null) {
            // ============ 主设置列表 ============
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
                    .verticalScroll(mainScrollState)
                    .padding(top = statusBarHeightDp + titleBarAreaDp + 8.dp, bottom = 32.dp)
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

                // 语言模型
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { subScreen = SubScreen.LANGUAGE_MODEL }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.SmartToy, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.languageModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(
                                    selectedModel.displayName,
                                    fontFamily = LocalMonoFontFamily.current,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                                    color = colors.TextSecondary
                                )
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // 视觉模型（生图 + 识图 合并为一栏）
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { subScreen = SubScreen.VISUAL_MODELS }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Image, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.visualModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(selectedVisualModel.displayName, fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                                    Text(selectedVisionModel?.displayName ?: s.notSelected, fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                                }
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // 语音模型（只读二级页入口）
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { subScreen = SubScreen.VOICE_MODELS }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.RecordVoiceOver, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.voiceModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    // 1.0.50：这两行的名字一律从**用户自己的模型库里查**。
                                    // 上面那行原来是写死的 "MiMo-V2.5-TTS" —— 用户换了自己的语音模型，
                                    // 这里照样显示 MiMo，等于在说谎。查不到（没选 / 模型被删）就说「未选择」。
                                    Text(ttsModels.find { it.id == voiceModel }?.displayName ?: s.notSelected, fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                                    Text(asrModels.find { it.id == asrModel }?.displayName ?: s.notSelected, fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                                }
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // 段间距：与 12dp 的卡片间距叠出 12+8+12 = 32dp —— 比卡片间距明显大，SectionLabel 才立得住
                Spacer(modifier = Modifier.height(8.dp))

                // ──── AI 系统优化 ────
                SectionLabel(Icons.Filled.AutoAwesome, s.sectionAiOptimize)

                // 联网搜索
                // 1.0.50：这一行不再显示「剩余 xxx 次（2 个账号）」的额度明细了 ——
                // 它让这一行比同区的开关行高出一行、整列卡片对不齐（用户点名要去掉）。
                // 额度用完时搜索本身仍会失败并给出原因（SerpApiPool 那条路没动）。
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Language, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.webSearch, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(
                                    if (enableWebSearch && !selectedModel.supportsWebSearch) s.webSearchUnsupported
                                    else if (enableWebSearch) s.webSearchOn
                                    else s.webSearchOff,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                                    color = if (!selectedModel.supportsWebSearch && enableWebSearch) colors.ErrorRed else colors.TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = enableWebSearch && selectedModel.supportsWebSearch,
                            onCheckedChange = { checked ->
                                if (!checked) showWebSearchConfirm = true
                                else viewModel.setEnableWebSearch(true)
                            },
                            enabled = selectedModel.supportsWebSearch,
                            colors = switchColors(colors)
                        )
                    }
                }

                // 显示思考过程（当前模型不支持时整项消失）
                if (selectedModel.supportsThinking) {
                    SettingsRow {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Psychology, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(s.showThinking, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                    Text(s.showThinkingDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                                }
                            }
                            Switch(
                                checked = showThinking,
                                onCheckedChange = { viewModel.setShowThinking(it) },
                                colors = switchColors(colors)
                            )
                        }
                    }
                }

                // 回复温度
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { showTempPicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Thermostat, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.replyTemp, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(tempLabel(tempMode, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // 回复长度
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().clickable { showLengthPicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ShortText, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.replyLength, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(lengthLabel(lengthMode, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
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
                        Modifier.fillMaxWidth().clickable { showColorThemePicker = true }
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

                // 高级材质
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.BlurOn, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.advancedMaterial, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(s.advancedMaterialDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Switch(
                            checked = advancedMaterial,
                            onCheckedChange = { viewModel.setAdvancedMaterial(it) },
                            colors = switchColors(colors)
                        )
                    }
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
                            onCheckedChange = { viewModel.setLiquidBackdrop(it) },
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
                        // 版本（右侧箭头进入更新日志二级页）
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenChangelog() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.version, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            // 原来是直接跳 GitHub。现在改成进「关于作者」二级页 ——
                            // 一跳走，用户就离开了 App，想问的问题一个都看不到（仓库地址在那一页里照样点得到）
                            Modifier.fillMaxWidth().clickable { subScreen = SubScreen.AUTHOR }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.author, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Belate", style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenAgreement() }.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.userAgreement, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
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
                    .verticalScroll(rememberScrollState())
                    .padding(top = statusBarHeightDp + titleBarAreaDp + 8.dp, bottom = 32.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (screen) {
                    SubScreen.LANGUAGE_MODEL -> LanguageModelPage(viewModel, colors, s, onAdd = { onOpenModelEditor(ModelType.LANGUAGE, null) }, onEdit = { onOpenModelEditor(ModelType.LANGUAGE, it) })
                    SubScreen.VISUAL_MODELS -> VisualModelsPage(viewModel, colors, s, onAddVisual = { onOpenModelEditor(ModelType.VISUAL, null) }, onAddVision = { onOpenModelEditor(ModelType.VISION, null) }, onEdit = { onOpenModelEditor(it.modelType, it) })
                    SubScreen.VOICE_MODELS -> VoiceModelsPage(viewModel, colors, s, onAddTts = { onOpenModelEditor(ModelType.TTS, null) }, onAddAsr = { onOpenModelEditor(ModelType.ASR, null) }, onEdit = { onOpenModelEditor(it.modelType, it) })
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
                    SubScreen.AUTHOR -> AuthorPage(colors, s, isDark = isDark, onQrLongPress = { showQrSaveSheet = true })
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        }

        // ===== 顶部标题栏背景（高级材质开=真模糊+渐变渐隐，关=纯色） =====
        if (advancedMaterial) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp)
                    .pageHeaderBackground(colors.Background)
                    .hazeEffect(state = hazeState) {
                        blurRadius = HazeSpec.TopBlurRadius
                        inputScale = HazeInputScale.None
                        backgroundColor = Color.Transparent
                        progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 1f, endY = topBarHeightPx, endIntensity = 0f)
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp)
                    // 标题栏必须**不透明**（正文滚上来要被挡住）。炫彩开着时 pageBackground 是空操作
                    // —— 整页都透明，标题区就跟着透了。改用 pageHeaderBackground：炫彩关=这块底色本身，
                    // 炫彩开=钉在屏幕上的一份流光副本，两种情况下都与页面自身上下同色。
                    .pageHeaderBackground(colors.Background)
            )
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
            IconButton(onClick = { if (subScreen != null) subScreen = null else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.TextPrimary)
            }
            Text(
                when (subScreen) {
                    SubScreen.LANGUAGE_MODEL -> s.languageModel
                    SubScreen.VISUAL_MODELS -> s.visualModel
                    SubScreen.VOICE_MODELS -> s.voiceModel
                    SubScreen.AI_MEMORY -> s.aiMemory
                    SubScreen.FONT -> s.font
                    SubScreen.AUTHOR -> s.authorAboutTitle
                    null -> s.settings
                },
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
        }

        // ===== 六个二级选择框（底部磨砂玻璃弹层，窗口内浮层）=====
        // 放在根 Box 的最后：它们要盖在所有东西之上（包括悬浮标题栏）。
        // 背景不压暗 —— 拦截层自己就是一层 haze，把背后的设置页**糊掉**，
        // 于视觉上「失焦」而不「变黑」，和 App 其它地方的材质是同一套语言。

        // 关闭联网搜索的确认。两个选择都留在台面上：主按钮是「保持开启」（原来是加粗的主体色那个），
        // 「仍然关闭」做成一行**蓝色斜体下划线**的行内操作（1.0.53 起统一成链接样式 ——
        // 原先是红字，用户反馈「太不显眼、根本不像一个可点的选项」）；
        // 点外面关掉弹层同样等于保持开启。
        SheetPanel(
            visible = showWebSearchConfirm,
            onDismiss = { showWebSearchConfirm = false },
            title = s.closeWebSearchTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.keepOn,
            onConfirm = { showWebSearchConfirm = false }
        ) {
            Text(s.closeWebSearchDesc, color = colors.TextSecondary)
            Spacer(Modifier.height(4.dp))
            SheetActionRow(s.stillClose, colors, link = true) {
                viewModel.setEnableWebSearch(false)
                showWebSearchConfirm = false
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
                    if (mode == ThemeMode.SYSTEM && sel) {
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

        SheetPanel(showColorThemePicker, { showColorThemePicker = false }, s.colorTheme, colors, isDark, advancedMaterial, hazeState) {
            ColorTheme.entries.forEach { ct ->
                SheetOption(
                    selected = ct == colorTheme,
                    title = colorThemeLabel(ct, s),
                    colors = colors,
                    onClick = { viewModel.setColorTheme(ct); showColorThemePicker = false }
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

@Composable
private fun LanguageModelPage(
    viewModel: ChatViewModel, colors: com.freechat.ui.theme.FreeChatColors, s: AppStrings,
    onAdd: () -> Unit, onEdit: (ModelInfo) -> Unit
) {
    val languageModels by viewModel.languageModels.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    Spacer(modifier = Modifier.height(4.dp))
    AddModelButton(onAdd, colors, s)
    ModelListDivider(colors)
    languageModels.forEach { model ->
        ModelItemRow(model, model.id == selectedModel.id, { viewModel.selectLanguageModel(model) }, onEdit, colors)
    }
}

@Composable
private fun VisualModelsPage(
    viewModel: ChatViewModel, colors: com.freechat.ui.theme.FreeChatColors, s: AppStrings,
    onAddVisual: () -> Unit, onAddVision: () -> Unit, onEdit: (ModelInfo) -> Unit
) {
    val visualModels by viewModel.visualModels.collectAsState()
    val visionModels by viewModel.visionModels.collectAsState()
    val selectedVisualModel by viewModel.selectedVisualModel.collectAsState()
    val selectedVisionModel by viewModel.selectedVisionModel.collectAsState()

    Spacer(modifier = Modifier.height(4.dp))
    SectionLabel(Icons.Filled.Image, s.imageGenModel)
    AddModelButton(onAddVisual, colors, s)
    ModelListDivider(colors)
    visualModels.forEach { model ->
        ModelItemRow(model, model.id == selectedVisualModel.id, { viewModel.selectVisualModel(model) }, onEdit, colors)
    }

    Spacer(modifier = Modifier.height(16.dp))
    SectionLabel(Icons.Filled.Visibility, s.visionModel)
    AddModelButton(onAddVision, colors, s)
    ModelListDivider(colors)
    if (visionModels.isEmpty()) {
        EmptyModelHint(s.addModelFirst, colors)
    } else {
        visionModels.forEach { model ->
            ModelItemRow(model, model.id == selectedVisionModel?.id, { viewModel.selectVisionModel(model) }, onEdit, colors)
        }
    }
}

@Composable
private fun VoiceModelsPage(
    viewModel: ChatViewModel, colors: com.freechat.ui.theme.FreeChatColors, s: AppStrings,
    onAddTts: () -> Unit, onAddAsr: () -> Unit, onEdit: (ModelInfo) -> Unit
) {
    val ttsModels by viewModel.ttsModels.collectAsState()
    val asrModels by viewModel.asrModels.collectAsState()
    val voiceModel by viewModel.voiceModel.collectAsState()
    val asrModel by viewModel.asrModel.collectAsState()

    Spacer(modifier = Modifier.height(4.dp))
    SectionLabel(Icons.Filled.RecordVoiceOver, s.voiceModel)
    AddModelButton(onAddTts, colors, s)
    ModelListDivider(colors)
    ttsModels.forEach { model ->
        ModelItemRow(model, model.id == voiceModel, { viewModel.selectTtsModel(model) }, onEdit, colors)
    }

    Spacer(modifier = Modifier.height(16.dp))
    SectionLabel(Icons.Filled.Mic, s.voiceModel)
    AddModelButton(onAddAsr, colors, s)
    ModelListDivider(colors)
    if (asrModels.isEmpty()) {
        EmptyModelHint(s.addModelFirst, colors)
    } else {
        asrModels.forEach { model ->
            ModelItemRow(model, model.id == asrModel, { viewModel.selectAsrModel(model) }, onEdit, colors)
        }
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
private fun ModelListDivider(colors: com.freechat.ui.theme.FreeChatColors) {
    Column {
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(12.dp))
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp), face = if (selected) colors.selectedFill else null)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(model.displayName, fontFamily = LocalMonoFontFamily.current, style = MaterialTheme.typography.bodyLarge, color = if (selected) colors.selectedText else colors.TextPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                Text(com.freechat.i18n.localizedModelDesc(model, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = if (selected) colors.selectedSubText else colors.TextSecondary)
            }
            if (!model.isBuiltIn) {
                IconButton(onClick = { onEdit(model) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Edit, null, tint = if (selected) colors.selectedSubText else colors.TextTertiary, modifier = Modifier.size(16.dp))
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
    onQrLongPress: () -> Unit = {}
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
        url = "https://118.178.227.178/",
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
    Box(
        Modifier.fillMaxWidth()
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp), face = if (selected) colors.selectedFill else null)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    model.displayName,
                    fontFamily = LocalMonoFontFamily.current,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) colors.selectedText else colors.TextPrimary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(com.freechat.i18n.localizedModelDesc(model, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = if (selected) colors.selectedSubText else colors.TextSecondary)
            }
        }
    }
}

@Composable
private fun DarkThemeChip(label: String, selected: Boolean, colors: com.freechat.ui.theme.FreeChatColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            // 选中态与全 App 的选项表同一支颜色（1.0.50 统一，见 selectedFill）
            .background(if (selected) colors.selectedFill else colors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
            color = if (selected) colors.selectedText else colors.TextSecondary,
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

private fun fontSizePercent(fs: FontSize): String = "${(fs.scale * 100).roundToInt()}%"

@Composable
internal fun SectionLabel(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
