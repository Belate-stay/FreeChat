package com.freechat.ui.screens

import com.freechat.ui.adaptive.FloatingWindowBackButton
import com.freechat.ui.adaptive.FloatingWindowCloseHandler
import com.freechat.ui.adaptive.floatingHeaderInset

import com.freechat.ui.adaptive.pageActionWidth

import com.freechat.ui.components.HeaderIconButton

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.freechat.ui.animation.jellyOverscroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import com.freechat.ui.animation.MotionButton as Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.data.ModelCatalog
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.Provider
import com.freechat.ui.components.SheetPanel
import com.freechat.ui.components.NeumorphicSwitch
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.selectedFill
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.theme.floatingSurface
import com.freechat.viewmodel.ChatViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.components.TopBarBackdrop
import com.freechat.ui.components.TopBarBackdropSource
import kotlinx.coroutines.launch

/**
 * 添加 / 编辑模型页（5 类模型通用）。
 *
 * 1.0.69 新增三块（用户工作单）：
 *  1.「获取模型列表」—— 按 API 地址自动识别服务商并拉 `/v1/models`，点选即填模型 ID；
 *     取不到显示「获取失败」，手填继续，页面有免责声明。
 *  2.「模型上下文」+「声明支持 1M」两件套（语言/识图）—— 照 cc-switch 的能力声明样式，
 *     UI 换成 FreeChat 自家卡片材质。上下文按估算 token 裁剪历史；1M 声明联动拟人档增强检索锁定。
 * 生图只配置接入信息，不猜测服务商支持的尺寸或风格参数。
 *
 * Beta 1.0.96：只供自定义模型添加与编辑。内置模型定义不可编辑，密钥也不在此页展示。
 */

/** 1.1.29.3 ①：添加页「导入已有多模态模型」卡片下缘的模糊带余量（16 上留白+48 卡+12 下留白） */
private val ImportHeaderBandDp = 76.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelEditorScreen(
    viewModel: ChatViewModel,
    modelType: ModelType,
    editing: ModelInfo?,
    isDark: Boolean,
    onBack: () -> Unit
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    // Defense in depth for stale navigation/state restored from an earlier version.
    // 1.1.26.5：非开源区放行的内置模型编辑（进页查看/编辑，API Key 不显示）。
    if (!com.freechat.data.ModelAccessPolicy.canEdit(editing) &&
        !com.freechat.dev.DevMode.canEditBuiltIn(context)
    ) {
        LaunchedEffect(editing) { onBack() }
        return
    }
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val scope = rememberCoroutineScope()

    val isAdd = editing == null

    var name by remember(editing) { mutableStateOf(editing?.displayName ?: "") }
    var apiKey by remember(editing) { mutableStateOf(editing?.apiKey ?: "") }
    var modelId by remember(editing) { mutableStateOf(editing?.id ?: "") }
    var apiUrl by remember(editing) { mutableStateOf(editing?.apiBaseUrl ?: "") }
    var note by remember(editing) { mutableStateOf(editing?.description ?: "") }

    // 1.0.75 深度思考：只剩「支持」能力位（探测 + 手动勾选）—— 开/关搬到全局设置绑定模型
    //（用户拍板：编辑页删掉「默认开/关」开关，一处一职）
    var supportsDeepThink by remember(editing) { mutableStateOf(editing?.supportsDeepThinking ?: false) }
    // 手动动过勾选框 = true；新增模型没动过时勾选框跟着模型 id 的启发式走（= 「添加时自动识别」）
    var deepThinkTouched by remember(editing) { mutableStateOf(editing != null) }
    // 1.0.75 内置联网搜索能力位（组合拳：原生优先、搜索管线兜底）
    var nativeSearch by remember(editing) { mutableStateOf(editing?.supportsNativeSearch ?: false) }

    // ===== 「获取模型列表」的拉取结果（尽力解析；空 + failed = 获取失败） =====
    var fetchState by remember { mutableStateOf(0) }   // 0=未拉 1=拉取中 2=失败 3=成功
    var fetched by remember { mutableStateOf<List<ModelCatalog.FetchedModel>>(emptyList()) }
    var showPicker by remember { mutableStateOf(false) }

    val requiredFilled = apiKey.isNotBlank() && modelId.isNotBlank() && apiUrl.isNotBlank()
    // 显示/落盘都用这个值：手动动过用手动的；新增没动过时跟 id 的启发式走（添加时自动识别）
    val effectiveSupportsDeepThink = if (isAdd && !deepThinkTouched)
        com.freechat.data.ModelCatalog.guessDeepThinking(modelId) else supportsDeepThink
    val hasChanges = name != (editing?.displayName ?: "") ||
        apiKey != (editing?.apiKey ?: "") ||
        modelId != (editing?.id ?: "") ||
        apiUrl != (editing?.apiBaseUrl ?: "") ||
        note != (editing?.description ?: "") ||
        effectiveSupportsDeepThink != (editing?.supportsDeepThinking ?: false) ||
        nativeSearch != (editing?.supportsNativeSearch ?: false)

    var showUnsaved by remember { mutableStateOf(false) }
    var pendingWindowClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDelete by remember { mutableStateOf(false) }

    // ===== 1.1.26.5 「导入已有多模态模型」：跨五个模型池快取信息免重输（B 提示框先弹） =====
    var showImportHint by remember { mutableStateOf(false) }
    var showImportPicker by remember { mutableStateOf(false) }
    val languageModels by viewModel.languageModels.collectAsState()
    val visualModels by viewModel.visualModels.collectAsState()
    val visionModels by viewModel.visionModels.collectAsState()
    val ttsModels by viewModel.ttsModels.collectAsState()
    val asrModels by viewModel.asrModels.collectAsState()
    val importSources = languageModels + visualModels + visionModels + ttsModels + asrModels

    fun tryBack() {
        if (hasChanges) showUnsaved = true else onBack()
    }
    BackHandler(enabled = com.freechat.ui.animation.LocalPageActive.current) { tryBack() }
    FloatingWindowCloseHandler { dismiss ->
        if (hasChanges) {
            pendingWindowClose = dismiss
            showUnsaved = true
        } else dismiss()
    }

    fun save() {
        val m = ModelInfo(
            id = modelId.trim(),
            displayName = name.trim().ifBlank { modelId.trim() },
            provider = Provider.CUSTOM,
            description = note.trim(),
            supportsWebSearch = modelType == ModelType.LANGUAGE,
            modelType = modelType,
            supportsThinking = modelType == ModelType.LANGUAGE,
            apiBaseUrl = apiUrl.trim(),
            apiKey = apiKey.trim(),
            isBuiltIn = false,
            supportsDeepThinking = effectiveSupportsDeepThink,
            // 深度思考开/关 1.0.75 起只在全局设置里调（绑定模型）：编辑时保留档案里已有的值，新模型默认关
            deepThinkingDefault = editing?.deepThinkingDefault ?: false,
            supportsNativeSearch = nativeSearch
        )
        if (editing?.isBuiltIn == true) {
            // 非开源区放行的内置编辑：保留内置身份/provider/密钥（密钥框对内置不显示），
            // 能力位也维持内置定义原值——只让名称/备注/URL 这些外观接入信息生效
            viewModel.updateBuiltInModel(m.copy(
                isBuiltIn = true,
                provider = editing.provider,
                apiKey = editing.apiKey,
                supportsWebSearch = editing.supportsWebSearch,
                supportsThinking = editing.supportsThinking,
                supportsDeepThinking = editing.supportsDeepThinking,
                deepThinkingDefault = editing.deepThinkingDefault,
                supportsNativeSearch = editing.supportsNativeSearch
            ))
        } else if (isAdd) viewModel.addCustomModel(m) else viewModel.updateCustomModel(editing!!.id, editing.modelType, m)
        onBack()
    }

    fun fetchCatalog() {
        if (fetchState == 1) return
        val (base, key) = viewModel.editorFetchEndpoint(editing?.provider ?: Provider.CUSTOM, apiUrl, apiKey)
        if (base.isBlank() || key.isBlank()) { fetchState = 2; return }
        fetchState = 1
        scope.launch {
            val r = ModelCatalog.fetchModels(base, key)
            r.onSuccess { list ->
                fetched = list
                fetchState = 3
                showPicker = true
            }.onFailure {
                fetched = emptyList()
                fetchState = 2
            }
        }
    }

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        TopBarBackdropSource(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBarHeightDp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
                .padding(horizontal = 20.dp + com.freechat.ui.adaptive.tabletReadingGutter())
        ) {
            // 1.1.26.5：添加页顶部「导入已有多模态模型」——先弹模态适配提醒，再从五个模型池选模型快填。
            // 1.1.29.3 ①：改**悬浮置顶**——画在模糊带之上、覆盖滚动表单（同模型池页「添加模型」），
            // 分页线撤掉；表单自己滚、头部留出卡片占位。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .jellyOverscroll()
                    .verticalScroll(com.freechat.ui.animation.rememberTopOnEntryScrollState())
                    // 2026-10-19 返修三进（图4→图5）：顶留白必须挪进滚动内容（关于页 A 同款病根）——
                    // 挂外层=滚动视口缩在标题栏下，上滑内容被视口顶缘硬裁、进不了模糊带（顶部糊不出残影）。
                    .padding(top = HazeSpec.topContentPaddingDp(statusBarHeightDp, 16.dp), bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // 2026-10-19 返修二进：头部占位必须在**滚动区内部**——挂在滚动区外时，表单被压到
            // 模糊带下缘就被视口硬裁（「API Key」齐腰切断的真凶），内容钻不进高斯渐变、糊不了。
            // 占位进滚动内容后，行上滑一路钻进带子里被高斯模糊盖住（模型池页同款结构）。
            if (isAdd) {
                Spacer(Modifier.height(60.dp))
            }
            run {
                // 1.0.71 顺序调整（用户点名）：名称 → URL地址 → API Key → 模型 ID → 获取模型列表 → 备注 → 上下文长度
                // 1.1.29 工单⑤：必填项标签后缀红星，输入框内不再写「（必填）/（选填）」
                EditorField(s.modelName, s.modelNameHint, name, { name = it }, colors)
                EditorField(s.apiUrlLabel, s.apiUrlHint, apiUrl, { apiUrl = it }, colors, required = true)
                // 1.1.26.5：内置模型不显示 API Key（密钥是内置注入的，想显示也显示不了）
                if (editing?.isBuiltIn != true) {
                    EditorField(s.apiKeyLabel, s.apiKeyHint, apiKey, { apiKey = it }, colors, isPassword = true, required = true)
                }
                EditorField(s.modelIdLabel, s.modelIdHint, modelId, { modelId = it }, colors, required = true)

                // ===== 获取模型列表（cc-switch 式；取不到照样手填） =====
                Box(
                    modifier = Modifier
                        .pageActionWidth()
                        .height(48.dp)
                        .editorSurface(colors, RoundedCornerShape(14.dp))
                        .clickable { fetchCatalog() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CloudDownload, null,
                            tint = if (fetchState == 1) colors.TextTertiary else colors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (fetchState) {
                                1 -> s.fetchingModels
                                else -> s.fetchModelsLabel
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (fetchState == 1) colors.TextTertiary else colors.Primary
                        )
                    }
                }
                if (fetchState == 2) {
                    Text(
                        s.fetchFailed,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                        color = colors.ErrorRed
                    )
                }

                EditorField(s.modelNote, s.modelNoteHint, note, { note = it }, colors)
            }

            // ===== 深度思考（1.0.75：只剩「支持」能力位；开/关在全局设置里绑定模型，开关已删） =====
            if (modelType == ModelType.LANGUAGE || modelType == ModelType.VISION) {
              EditorSectionLabel(s.modelCapabilitiesLabel, colors)
              Text(s.modelCapabilitiesDesc,
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                  color = colors.TextSecondary)
              EditorCard(colors) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EditorCapabilityToggle(
                        checked = effectiveSupportsDeepThink,
                        onCheckedChange = { supportsDeepThink = it; deepThinkTouched = true },
                        colors = colors
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(s.modelDeepThinkingLabel, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                }
              }
            }

            // ===== 内置联网搜索（1.0.75 组合拳：模型自带搜索优先、搜索管线只兜底） =====
            // 只对语言模型有意义（生图/语音没有联网问答链路）
            if (modelType == ModelType.LANGUAGE) {
                EditorCard(colors) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EditorCapabilityToggle(
                            checked = nativeSearch,
                            onCheckedChange = { nativeSearch = it },
                            colors = colors
                        )
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.nativeSearchLabel, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                            Text(s.nativeSearchDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (advancedMaterial) Box(Modifier.pageActionWidth().height(52.dp)
                .editorSurface(colors, RoundedCornerShape(16.dp), selected = requiredFilled,
                    fallback = if (requiredFilled) colors.Primary else colors.SurfaceVariant)
                .clickable(enabled = requiredFilled, role = androidx.compose.ui.semantics.Role.Button) { save() },
                contentAlignment = Alignment.Center) {
                Text(s.saveModel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    color = if (requiredFilled) colors.OnPrimary else colors.TextTertiary)
            } else {
                Button(onClick = { save() }, enabled = requiredFilled,
                    modifier = Modifier.pageActionWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.Primary,
                        contentColor = colors.OnPrimary, disabledContainerColor = colors.SurfaceVariant,
                        disabledContentColor = colors.TextTertiary)) {
                    Text(s.saveModel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            }
        }

        // 1.1.29.3 ①：添加页模糊带下延到「导入已有多模态模型」卡片下方（内容上滑钻进高斯渐变被覆盖）
        // 2026-10-19 返修：solidHeight 默认值 = 带高-渐隐区，带子下延时**实心底板跟着长高**，
        // 卡片整区被实色板盖死（前两项滑到顶部被实色挡完、API Key 那行滑上来才见模糊）——
        // 底板钉回标题栏（同侧滑页「搜索/新对话后方也模糊」的写法），卡片区只剩高斯渐变。
        TopBarBackdrop(hazeState, colors.Background,
            if (isAdd) HazeSpec.drawerTopBandHeightDp(statusBarHeightDp, ImportHeaderBandDp)
            else HazeSpec.topBandHeightDp(statusBarHeightDp),
            solidHeight = HazeSpec.titleBarHeightDp(statusBarHeightDp))

        // 1.1.29.3 ①：「导入已有多模态模型」悬浮置顶——画在模糊带之上、覆盖滚动表单（分页线已撤）
        if (isAdd) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = HazeSpec.topContentPaddingDp(statusBarHeightDp, 16.dp))
                    .padding(horizontal = 20.dp)
                    .pageActionWidth()
                    .height(48.dp)
                    // 2026-10-19 返修：悬浮卡是「正文上方的操作区」，高级材质走 floatingSurface
                    // 透光不透物磨砂玻璃（旧 editorSurface 实色卡面把滑到其下的表单项整块挡死）；
                    // 非高级材质保持原实色卡零变化。
                    .then(
                        if (advancedMaterial) Modifier.floatingSurface(hazeState, isDark, RoundedCornerShape(14.dp))
                        else Modifier.editorSurface(colors, RoundedCornerShape(14.dp))
                    )
                    .clickable { showImportHint = true },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Download, null,
                        tint = colors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        s.importModelEntry,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.Primary
                    )
                }
            }
        }

        // 悬浮标题栏
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp).floatingHeaderInset(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingWindowBackButton(onClick = { tryBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back, tint = colors.TextPrimary)
            }
            Text(
                if (isAdd) s.addModel else s.editModel,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (!isAdd && editing?.isBuiltIn != true) {
                HeaderIconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Filled.DeleteOutline, s.deleteModel, tint = colors.ErrorRed, modifier = Modifier.size(22.dp))
                }
            }
            com.freechat.ui.adaptive.PaneExpandButton()
        }

        // 获取成功 → 模型选择弹层：点选即填
        SheetPanel(
            visible = showPicker,
            onDismiss = { showPicker = false },
            title = s.pickModel,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                fetched.forEach { fm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                modelId = fm.id
                                if (name.isBlank()) name = fm.id
                                // 深度思考/内置联网能力探测结果一并带出（用户可改）——
                                // 落定即算「已识别」，勾选框不再跟着 id 变
                                supportsDeepThink = fm.supportsDeepThinking
                                deepThinkTouched = true
                                nativeSearch = fm.supportsNativeSearch
                                showPicker = false
                                fetchState = 3
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(fm.id, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                        }
                    }
                }
            }
        }

        // 这是窗口内的底部磨砂哑光玻璃弹层（原来用 AlertDialog：独立窗口糊不到背景，只能把背景压暗）
        // 点外面 / 返回键 = 原来的 onDismissRequest（留在本页继续编辑）
        SheetPanel(
            visible = showUnsaved,
            onDismiss = { showUnsaved = false; pendingWindowClose = null },
            title = s.unsavedTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.discardChanges,
            cancelLabel = when (s.localeCode) {
                "zh-CN" -> "继续编辑"
                "zh-TW" -> "繼續編輯"
                else -> "Continue editing"
            },
            // 危险操作：确认键红底，别让它长得跟普通「确定」一样
            confirmDanger = true,
            onConfirm = {
                showUnsaved = false
                val close = pendingWindowClose
                pendingWindowClose = null
                if (close != null) close() else onBack()
            }
        ) {
            Text(s.unsavedMessage, color = colors.TextSecondary)
        }

        // 这是窗口内的底部磨砂哑光玻璃弹层（原来用 AlertDialog：独立窗口糊不到背景，只能把背景压暗）
        // 点外面 / 返回键 = 原来的 onDismissRequest（不删除）
        SheetPanel(
            visible = showDelete,
            onDismiss = { showDelete = false },
            title = s.deleteModel,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.delete,
            // 危险操作：确认键红底，别让它长得跟普通「确定」一样
            confirmDanger = true,
            onConfirm = {
                showDelete = false
                editing?.let { viewModel.deleteCustomModel(it.id, it.modelType) }
                onBack()
            }
        ) {
            Text(s.deleteWarning, color = colors.TextSecondary)
        }

        // 1.1.26.5 B：导入前先弹模态适配提醒——避免拿纯生图模型当语音/语言模型使，AI 不予回复
        SheetPanel(
            visible = showImportHint,
            onDismiss = { showImportHint = false },
            title = s.importModelHintTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.importModelContinue,
            cancelLabel = s.cancel,
            onConfirm = {
                showImportHint = false
                showImportPicker = true
            }
        ) {
            Text(s.importModelHintBody, color = colors.TextSecondary)
        }

        // 五个模型池全量列出：点选即把信息填进输入框（可再改）；内置只给「获取」，不给详情
        SheetPanel(
            visible = showImportPicker,
            onDismiss = { showImportPicker = false },
            title = s.importModelPickerTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                importSources.forEach { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                name = m.displayName
                                modelId = m.id
                                apiUrl = m.apiBaseUrl
                                apiKey = m.apiKey
                                note = m.description
                                showImportPicker = false
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.displayName, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                            Text(
                                when (m.modelType) {
                                    ModelType.LANGUAGE -> s.languageModel
                                    ModelType.VISUAL -> s.imageGenModel
                                    ModelType.VISION -> s.visionModel
                                    ModelType.TTS -> s.voiceTtsLabel
                                    ModelType.ASR -> s.voiceAsrLabel
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.TextSecondary
                            )
                        }
                        if (m.isBuiltIn) {
                            Text(s.importBuiltinTag, style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSectionLabel(text: String, colors: FreeChatColors) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = colors.TextPrimary
    )
}

@Composable
private fun EditorCapabilityToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, colors: FreeChatColors) {
    if (LocalAdvancedMaterial.current) NeumorphicSwitch(checked, onCheckedChange,
        SwitchDefaults.colors(checkedThumbColor = colors.OnPrimary, checkedTrackColor = colors.Primary,
            uncheckedThumbColor = colors.TextTertiary, uncheckedTrackColor = colors.SurfaceVariant,
            uncheckedBorderColor = colors.Divider))
    else Checkbox(checked, onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = colors.Primary,
        uncheckedColor = colors.TextTertiary, checkmarkColor = colors.OnPrimary))
}

/** Same double shadows, lighting and animated color field used by settings and character cards. */
@Composable
private fun Modifier.editorSurface(colors: FreeChatColors, shape: RoundedCornerShape,
    recessed: Boolean = false, compact: Boolean = false, selected: Boolean = false,
    fallback: Color = colors.Surface): Modifier = if (LocalAdvancedMaterial.current) {
    frostedCard(null, colors, true, shape, recessed = recessed, compact = compact, selected = selected,
        fallback = fallback)
} else {
    // 选中态过渡动画与全 App 选项表同节奏（1.1.28）；边框只在未选中时在场，随透明度走硬切可接受
    val fill by androidx.compose.animation.animateColorAsState(
        if (selected) colors.selectedFill else fallback, com.freechat.ui.animation.FreeChatAnimation.colorTween, label = "editorSel")
    clip(shape).background(fill)
        .then(if (selected) Modifier else Modifier.border(1.dp, colors.Divider.copy(alpha = 0.5f), shape))
}

/** 新拟态卡面：与正文字段同级的一块「面」，参数区装在里面 */
@Composable
private fun EditorCard(colors: FreeChatColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .editorSurface(colors, RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content
    )
}

/** 免责声明：小字灰句，统一挂在每块参数区底部 */
@Composable
private fun DisclaimerText(text: String, colors: FreeChatColors) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 11.sp, lineHeight = 16.sp),
        color = colors.TextTertiary
    )
}

@Composable
private fun EditorField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    colors: FreeChatColors,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    // 1.1.29 工单⑤：必填不再写进输入框预输入文案，改为标签后缀红星（如「API Key *」）；没星=选填
    required: Boolean = false
) {
    val advanced = LocalAdvancedMaterial.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary, fontWeight = FontWeight.Medium)
            if (required) {
                Text(" *", style = MaterialTheme.typography.bodyMedium, color = colors.ErrorRed, fontWeight = FontWeight.Medium)
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextTertiary) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().then(if (advanced)
                Modifier.editorSurface(colors, RoundedCornerShape(14.dp), recessed = true) else Modifier),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.TextPrimary,
                unfocusedTextColor = colors.TextPrimary,
                focusedBorderColor = if (advanced) colors.Primary.copy(alpha = 0.5f) else colors.Primary,
                unfocusedBorderColor = if (advanced) Color.Transparent else colors.Divider,
                cursorColor = colors.Primary,
                focusedContainerColor = if (advanced) Color.Transparent else colors.Surface,
                unfocusedContainerColor = if (advanced) Color.Transparent else colors.Surface,
                disabledContainerColor = if (advanced) Color.Transparent else colors.Surface
            )
        )
    }
}
