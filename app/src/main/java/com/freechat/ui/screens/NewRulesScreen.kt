package com.freechat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ShortText
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.model.LengthMode
import com.freechat.model.ModelInfo
import com.freechat.model.PerConvSettings
import com.freechat.model.TempMode
import com.freechat.ui.components.SheetOption
import com.freechat.ui.components.SheetPanel
import com.freechat.ui.theme.LocalMonoFontFamily
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.viewmodel.ChatViewModel
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.theme.pageHeaderBackground

/**
 * 「新规则」二级页：针对当前对话的模型选择 + AI 系统优化定制。
 * 每对话覆盖优先级高于全局默认；未设置的项跟随全局。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRulesScreen(
    viewModel: ChatViewModel,
    convId: String,
    isDark: Boolean,
    onBack: () -> Unit
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val density = LocalDensity.current

    // 全局默认
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedVisualModel by viewModel.selectedVisualModel.collectAsState()
    val selectedVisionModel by viewModel.selectedVisionModel.collectAsState()
    val enableWebSearch by viewModel.enableWebSearch.collectAsState()
    val showThinking by viewModel.showThinking.collectAsState()
    val autoSummarizeMemory by viewModel.autoSummarizeMemory.collectAsState()
    val tempMode by viewModel.tempMode.collectAsState()
    val lengthMode by viewModel.lengthMode.collectAsState()
    // 语音的**全局默认**（设置页里选的那份）。本页只在「这条对话没单独选过」时用它兜底。
    val voiceModel by viewModel.voiceModel.collectAsState()
    val asrModel by viewModel.asrModel.collectAsState()
    val ttsModels by viewModel.ttsModels.collectAsState()
    val asrModels by viewModel.asrModels.collectAsState()

    // 每对话覆盖
    val perConvMap by viewModel.perConvSettings.collectAsState()
    val per = perConvMap[convId] ?: PerConvSettings()

    // 内置对话（「Claude风格助理」）：只留模型 / 联网 / 思考三项，理由见下面那段的注释
    val conversations by viewModel.conversations.collectAsState()
    val builtInAssistant = conversations.find { it.id == convId }?.builtInAssistant?.isNotBlank() == true

    // 有效值 = 每对话覆盖（非 null）优先，否则全局默认
    val effModel = per.languageModelId?.let { id -> viewModel.languageModels.value.find { it.id == id } } ?: selectedModel
    val effVisual = per.visualModelId?.let { id -> viewModel.visualModels.value.find { it.id == id } } ?: selectedVisualModel
    val effVision = per.visionModelId?.let { id -> viewModel.visionModels.value.find { it.id == id } } ?: selectedVisionModel
    // 语音同理：覆盖里的 id 在库里找不到（模型被删了）就当没设，跟随全局 —— 跟 ViewModel 里
    // effectiveTtsModelId/effectiveAsrModelId 的判定保持一致，免得「这里显示 A、实际用 B」
    val effTts = per.ttsModelId?.let { id -> ttsModels.find { it.id == id } } ?: ttsModels.find { it.id == voiceModel }
    val effAsr = per.asrModelId?.let { id -> asrModels.find { it.id == id } } ?: asrModels.find { it.id == asrModel }
    val effSearch = per.enableWebSearch ?: enableWebSearch
    val effThinking = per.showThinking ?: showThinking
    val effAutoMem = per.autoSummarizeMemory ?: autoSummarizeMemory
    val effTemp = TempMode.entries.getOrElse(per.tempModeOrdinal ?: tempMode.ordinal) { TempMode.AUTO }
    val effLength = LengthMode.entries.getOrElse(per.lengthModeOrdinal ?: lengthMode.ordinal) { LengthMode.AUTO }

    fun set(transform: (PerConvSettings) -> PerConvSettings) {
        viewModel.updatePerConvSettings(convId, transform(per))
    }

    var showLangPicker by remember { mutableStateOf(false) }
    var showVisualPicker by remember { mutableStateOf(false) }
    var showVisionPicker by remember { mutableStateOf(false) }
    var showTtsPicker by remember { mutableStateOf(false) }
    var showAsrPicker by remember { mutableStateOf(false) }
    // 规则是边打边存的，所以本地留一份跟着输入走 —— 直接读 conv 那份的话，
    // 每敲一个字都要等一次落盘+状态回流，输入会顿
    var rulesText by remember(convId) {
        mutableStateOf(viewModel.conversations.value.find { it.id == convId }?.rules.orEmpty())
    }

    var showTempPicker by remember { mutableStateOf(false) }
    var showLengthPicker by remember { mutableStateOf(false) }

    // 规则输入框是整页**最后一个**控件。Compose 自带的「把光标滚进可视区」只保证光标那一行露出来，
    // 输入框自己的下边框还会压在键盘底下 —— 看着就像输入框插进了键盘里。所以拿到焦点、且键盘已经
    // 升上来时，显式把整个输入框（连同边框）bringIntoView，多滚那十几 dp。
    // 等 ime 高度真的变成非 0 再请求：键盘还没起来就请求，滚完高度又会变，等于白滚。
    var rulesFocused by remember { mutableStateOf(false) }
    val rulesBringIntoView = remember { BringIntoViewRequester() }
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    LaunchedEffect(rulesFocused, imeBottomPx > 0) {
        if (rulesFocused && imeBottomPx > 0) {
            // 让 imePadding 引起的这一轮布局先落定，再算「滚到哪才露全」
            delay(80)
            rulesBringIntoView.bringIntoView()
        }
    }

    BackHandler(enabled = true) { onBack() }

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp).toPx() }

    // 七个选择框（语言/生图/视觉/语音合成/语音识别模型、温度、长度）全都是**窗口内**的底部磨砂玻璃弹层，
    // 实际渲染在下面页面根 Box 的末尾。不能在这里 if (showX) 调用：
    // SheetPanel 是 BoxScope 扩展，得挂在根 Box 的最后一个子节点上才铺得满整屏、盖得住悬浮标题栏。
    // 不能再用 AlertDialog：它是一个独立窗口，看不到 App 自己画的内容，
    // 想「模糊背景」是做不到的，只能把背景压暗（就是用户说的「悬浮卡片背景加暗」）。

    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        CompositionLocalProvider(LocalSettingsHazeState provides hazeState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
                // 键盘让位（1.0.50）：最底下那个「规则」输入框原来一敲键盘就被盖住。
                // 必须在 verticalScroll **之前**给：这样缩的是视口本身，内容才滚得上得来；
                // 给在后面只是往滚动内容尾巴上添一段留白，视口还是被键盘压着的那一整屏。
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(top = statusBarHeightDp + titleBarAreaDp + 8.dp, bottom = 32.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ──── 模型选择 ────
            SectionLabel(Icons.Filled.SmartToy, s.sectionModels)

            SettingsRow {
                Row(
                    Modifier.fillMaxWidth().clickable { showLangPicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.SmartToy, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(s.languageModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text(effModel.displayName, fontFamily = LocalMonoFontFamily.current, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

            SettingsRow {
                Row(
                    Modifier.fillMaxWidth().clickable { showVisualPicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Image, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(s.imageGenModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text(effVisual.displayName, fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

            SettingsRow {
                Row(
                    Modifier.fillMaxWidth().clickable { showVisionPicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Visibility, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(s.visionModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text(effVision?.displayName ?: s.notSelected, fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

            // 语音这两行（1.0.50 重做）：
            // 原来是一行**写死**的「MiMo-V2.5-TTS · MiMo-V2.5-ASR」配一个常驻 ✓ ——
            // 名字是硬编码的，用户换成自己的语音模型它也不会变，却永远摆出一副「已选中」的样子。
            // 现在按 生图/识图 的规矩拆成两行（合成 / 识别），名字从用户自己的模型库里查，
            // 库里没有就显示「未选择」。
            //
            // 选出来的是**这条对话的**语音（PerConvSettings.ttsModelId / asrModelId）。本页只改这一条对话，
            // 设置页「语音模型」那两列仍然是全局默认、别的对话照用 —— 这正是「新规则只管这条对话」的语义。
            // 顶层没选过时这里显示的就是全局那份（见 effTts/effAsr），看着跟以前一样，但一选就落在本对话上。
            SettingsRow {
                Row(
                    Modifier.fillMaxWidth().clickable { showTtsPicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.RecordVoiceOver, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(s.voiceTtsLabel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text(effTts?.displayName ?: s.notSelected, fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

            SettingsRow {
                Row(
                    Modifier.fillMaxWidth().clickable { showAsrPicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Mic, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(s.voiceAsrLabel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text(effAsr?.displayName ?: s.notSelected, fontFamily = LocalMonoFontFamily.current, fontSize = 12.sp, color = colors.TextSecondary)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            // ──── AI 系统优化 ────
            SectionLabel(Icons.Filled.AutoAwesome, s.sectionAiOptimize)

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
                            Text(if (effSearch) s.webSearchOn else s.webSearchOff, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                        }
                    }
                    Switch(
                        checked = effSearch,
                        onCheckedChange = { v -> set { it.copy(enableWebSearch = v) } },
                        colors = switchColors(colors)
                    )
                }
            }

            if (effModel.supportsThinking) {
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
                            checked = effThinking,
                            onCheckedChange = { v -> set { it.copy(showThinking = v) } },
                            colors = switchColors(colors)
                        )
                    }
                }
            }

            // 内置「Claude风格助理」不显示这四项：温度、长度、AI 记忆对它没有意义（风格与篇幅由人设定死），
            // 「规则」更是会和它自己的人设打架。摆着只会让人以为调了有用。
            if (!builtInAssistant) {
                SettingsRow {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Bookmark, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(s.memorySummary, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(s.memorySummaryDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Switch(
                            checked = effAutoMem,
                            onCheckedChange = { v -> set { it.copy(autoSummarizeMemory = v) } },
                            colors = switchColors(colors)
                        )
                    }
                }

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
                                Text(tempLabel(effTemp, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

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
                                Text(lengthLabel(effLength, s), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // ──── 规则：这条对话的系统级提示词 ────
                // 放在最底下。上面那些开关都是"这次怎么回"，只有它管的是"这条对话从今往后按什么规矩来"，
                // 分量不一样，所以单独一块、不跟开关挤在一起。
                Spacer(Modifier.height(8.dp))
                SectionLabel(Icons.Filled.Rule, s.convRules)
                SettingsRow {
                    Column(
                        Modifier.fillMaxWidth()
                            .bringIntoViewRequester(rulesBringIntoView)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            s.convRulesDesc,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                            color = colors.TextSecondary
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = rulesText,
                            // 边打边存。这里没有"保存"按钮 —— 用户写了一半切走再回来，
                            // 发现白写了，是比多写两次盘糟得多的事
                            onValueChange = { v ->
                                rulesText = v
                                viewModel.updateConversationRules(convId, v)
                            },
                            placeholder = {
                                Text(s.convRulesHint, color = colors.TextTertiary, fontSize = 13.sp)
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                            modifier = Modifier.fillMaxWidth().onFocusChanged { rulesFocused = it.isFocused },
                            minLines = 3,
                            maxLines = 10,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.Primary,
                                unfocusedBorderColor = colors.InputBorder,
                                cursorColor = colors.Primary
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
        }

        // 顶部标题栏背景（高级材质开=真模糊，关=纯色）
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

        // 悬浮标题栏
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.TextPrimary)
            }
            Text(
                s.newRulesTitle,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
        }

        // ===== 七个二级选择框（底部磨砂玻璃弹层，窗口内浮层）=====
        // 放在根 Box 的最后：要盖在所有东西之上（包括悬浮标题栏）。拦截层本身就是一层 haze，
        // 把背后整页**糊掉**而不是压暗，和 App 其它地方的材质是同一套语言。

        // 这是窗口内的底部磨砂哑光玻璃弹层（原来用 AlertDialog：独立窗口糊不到背景，只能把背景压暗）
        SheetPanel(
            visible = showLangPicker,
            onDismiss = { showLangPicker = false },
            title = s.languageModel,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState
        ) {
            ModelPickerOptions(viewModel.languageModels.value, effModel, colors, s) { m ->
                set { it.copy(languageModelId = m.id) }
                showLangPicker = false
            }
        }

        // 这是窗口内的底部磨砂哑光玻璃弹层（原来用 AlertDialog：独立窗口糊不到背景，只能把背景压暗）
        SheetPanel(
            visible = showVisualPicker,
            onDismiss = { showVisualPicker = false },
            title = s.imageGenModel,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState
        ) {
            ModelPickerOptions(viewModel.visualModels.value, effVisual, colors, s) { m ->
                set { it.copy(visualModelId = m.id) }
                showVisualPicker = false
            }
        }

        // 这是窗口内的底部磨砂哑光玻璃弹层（原来用 AlertDialog：独立窗口糊不到背景，只能把背景压暗）
        SheetPanel(
            visible = showVisionPicker,
            onDismiss = { showVisionPicker = false },
            title = s.visionModel,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState
        ) {
            ModelPickerOptions(viewModel.visionModels.value, effVision, colors, s) { m ->
                set { it.copy(visionModelId = m.id) }
                showVisionPicker = false
            }
        }

        // 语音合成 —— 写的是**本对话的覆盖**（ttsModelId），不动全局那份
        // 这是窗口内的底部磨砂哑光玻璃弹层（原来用 AlertDialog：独立窗口糊不到背景，只能把背景压暗）
        SheetPanel(
            visible = showTtsPicker,
            onDismiss = { showTtsPicker = false },
            title = s.voiceTtsLabel,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState
        ) {
            ModelPickerOptions(ttsModels, effTts, colors, s) { m ->
                set { it.copy(ttsModelId = m.id) }
                showTtsPicker = false
            }
        }

        // 语音识别 —— 同上，写本对话的 asrModelId
        // 这是窗口内的底部磨砂哑光玻璃弹层（原来用 AlertDialog：独立窗口糊不到背景，只能把背景压暗）
        SheetPanel(
            visible = showAsrPicker,
            onDismiss = { showAsrPicker = false },
            title = s.voiceAsrLabel,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState
        ) {
            ModelPickerOptions(asrModels, effAsr, colors, s) { m ->
                set { it.copy(asrModelId = m.id) }
                showAsrPicker = false
            }
        }

        // 这是窗口内的底部磨砂哑光玻璃弹层（原来用 AlertDialog：独立窗口糊不到背景，只能把背景压暗）
        SheetPanel(
            visible = showTempPicker,
            onDismiss = { showTempPicker = false },
            title = s.replyTemp,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState
        ) {
            TempModeOptions(effTemp, colors, s) { m ->
                set { it.copy(tempModeOrdinal = m.ordinal) }
                showTempPicker = false
            }
        }

        // 这是窗口内的底部磨砂哑光玻璃弹层（原来用 AlertDialog：独立窗口糊不到背景，只能把背景压暗）
        SheetPanel(
            visible = showLengthPicker,
            onDismiss = { showLengthPicker = false },
            title = s.replyLength,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState
        ) {
            LengthModeOptions(effLength, colors, s) { m ->
                set { it.copy(lengthModeOrdinal = m.ordinal) }
                showLengthPicker = false
            }
        }
    }
}

/**
 * 模型选择的**选项行**。外壳（SheetPanel）挂在页面根 Box 的末尾，这里只出行 ——
 * 私有 composable 没法自己扛 BoxScope 的弹层，所以拆成「行」和「壳」两半。
 */
@Composable
private fun ModelPickerOptions(
    models: List<ModelInfo>,
    selected: ModelInfo?,
    colors: com.freechat.ui.theme.FreeChatColors,
    s: AppStrings,
    onSelect: (ModelInfo) -> Unit
) {
    // 空库：别把一个空壳弹层推上来（语音识别默认就是空的），给一句话
    if (models.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Text(s.addModelFirst, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextTertiary)
        }
        return
    }
    models.forEach { m ->
        SheetOption(
            selected = m.id == selected?.id,
            title = m.displayName,
            subtitle = com.freechat.i18n.localizedModelDesc(m, s),
            colors = colors,
            // 模型名是"代码感"的字符串，沿用原来的等宽字体
            monoTitle = true,
            onClick = { onSelect(m) }
        )
    }
}

/** 回复温度的选项行（外壳在页面根 Box 末尾，见 [ModelPickerOptions]） */
@Composable
private fun TempModeOptions(selected: TempMode, colors: com.freechat.ui.theme.FreeChatColors, s: AppStrings, onSelect: (TempMode) -> Unit) {
    val icons = mapOf(TempMode.AUTO to Icons.Filled.Update, TempMode.WARM to Icons.Filled.Favorite, TempMode.OBJECTIVE to Icons.Filled.Psychology)
    TempMode.entries.forEach { m ->
        SheetOption(
            selected = m == selected,
            title = tempLabel(m, s),
            colors = colors,
            onClick = { onSelect(m) },
            icon = icons[m] ?: Icons.Filled.Update
        )
    }
}

/** 回复长度的选项行（外壳在页面根 Box 末尾，见 [ModelPickerOptions]） */
@Composable
private fun LengthModeOptions(selected: LengthMode, colors: com.freechat.ui.theme.FreeChatColors, s: AppStrings, onSelect: (LengthMode) -> Unit) {
    LengthMode.entries.forEach { m ->
        SheetOption(
            selected = m == selected,
            title = lengthLabel(m, s),
            colors = colors,
            onClick = { onSelect(m) }
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
