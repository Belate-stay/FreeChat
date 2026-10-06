package com.freechat.ui.screens

import com.freechat.ui.components.HeaderIconButton

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import com.freechat.ui.animation.MotionButton as Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    // Defense in depth for stale navigation/state restored from an earlier version.
    if (!com.freechat.data.ModelAccessPolicy.canEdit(editing)) {
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
    var showDelete by remember { mutableStateOf(false) }

    fun tryBack() {
        if (hasChanges) showUnsaved = true else onBack()
    }
    BackHandler(enabled = true) { tryBack() }

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
        if (isAdd) viewModel.addCustomModel(m) else viewModel.updateCustomModel(editing!!.id, editing.modelType, m)
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
                .verticalScroll(rememberScrollState())
                .padding(top = HazeSpec.topContentPaddingDp(statusBarHeightDp, 16.dp), bottom = 40.dp)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            run {
                // 1.0.71 顺序调整（用户点名）：名称 → URL地址 → API Key → 模型 ID → 获取模型列表 → 备注 → 上下文长度
                EditorField(s.modelName, s.modelNameHint, name, { name = it }, colors)
                EditorField(s.apiUrlLabel, s.apiUrlHint, apiUrl, { apiUrl = it }, colors)
                EditorField(s.apiKeyLabel, s.apiKeyHint, apiKey, { apiKey = it }, colors, isPassword = true)
                EditorField(s.modelIdLabel, s.modelIdHint, modelId, { modelId = it }, colors)

                // ===== 获取模型列表（cc-switch 式；取不到照样手填） =====
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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

            if (advancedMaterial) Box(Modifier.fillMaxWidth().height(52.dp)
                .editorSurface(colors, RoundedCornerShape(16.dp), selected = requiredFilled,
                    fallback = if (requiredFilled) colors.Primary else colors.SurfaceVariant)
                .clickable(enabled = requiredFilled, role = androidx.compose.ui.semantics.Role.Button) { save() },
                contentAlignment = Alignment.Center) {
                Text(s.saveModel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    color = if (requiredFilled) colors.OnPrimary else colors.TextTertiary)
            } else {
                Button(onClick = { save() }, enabled = requiredFilled,
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.Primary,
                        contentColor = colors.OnPrimary, disabledContainerColor = colors.SurfaceVariant,
                        disabledContentColor = colors.TextTertiary)) {
                    Text(s.saveModel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        TopBarBackdrop(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBarHeightDp))

        // 悬浮标题栏
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderIconButton(onClick = { tryBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back, tint = colors.TextPrimary)
            }
            Text(
                if (isAdd) s.addModel else s.editModel,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (!isAdd) {
                HeaderIconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Filled.DeleteOutline, s.deleteModel, tint = colors.ErrorRed, modifier = Modifier.size(22.dp))
                }
            }
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
            onDismiss = { showUnsaved = false },
            title = s.unsavedTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.delete,
            // 危险操作：确认键红底，别让它长得跟普通「确定」一样
            confirmDanger = true,
            onConfirm = { showUnsaved = false; onBack() }
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
    clip(shape).background(if (selected) colors.selectedFill else fallback)
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
    enabled: Boolean = true
) {
    val advanced = LocalAdvancedMaterial.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary, fontWeight = FontWeight.Medium)
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
