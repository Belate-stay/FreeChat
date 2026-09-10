package com.freechat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.freechat.i18n.LocalStrings
import com.freechat.model.*
import com.freechat.ui.theme.LocalMonoFontFamily
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.frostedCard
import com.freechat.viewmodel.ChatViewModel
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * 人物设定页：拟人陪伴角色的创建与配置（也用于再次编辑已有角色）。
 * 角色名必填；性别/年龄/MBTI（类型+四维滑块联动）/性格/记忆感知共同塑造人物。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterSetupScreen(
    viewModel: ChatViewModel,
    isDark: Boolean,
    onBack: () -> Unit,
    onCreate: (CharacterProfile) -> Unit,
    onSaveSimulation: (CharacterProfile) -> Unit = {},
    initial: CharacterProfile? = null
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    val context = LocalContext.current
    val normInitial = initial?.normalized()

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var gender by remember { mutableStateOf(initial?.gender ?: "") }
    var age by remember { mutableStateOf(initial?.age ?: "") }
    var avatarPath by remember { mutableStateOf(initial?.avatarPath ?: "") }
    var mbtiType by remember { mutableStateOf(initial?.mbtiType ?: "") }
    var mbtiEI by remember { mutableFloatStateOf(initial?.mbtiEI ?: 0.5f) }
    var mbtiNS by remember { mutableFloatStateOf(initial?.mbtiNS ?: 0.5f) }
    var mbtiTF by remember { mutableFloatStateOf(initial?.mbtiTF ?: 0.5f) }
    var mbtiPJ by remember { mutableFloatStateOf(initial?.mbtiPJ ?: 0.5f) }
    var presets by remember { mutableStateOf(initial?.personalityPresets?.toSet() ?: emptySet()) }
    var personalityText by remember { mutableStateOf(initial?.personalityText ?: "") }
    var memory by remember { mutableStateOf(initial?.memoryPerception ?: "") }
    var langModelId by remember { mutableStateOf(initial?.languageModelId ?: "") }
    var webSearch by remember { mutableStateOf(initial?.enableWebSearch) }
    var replyBufferSeconds by remember { mutableIntStateOf(initial?.replyBufferSeconds ?: 3) }
    var replyBufferEnabled by remember { mutableStateOf(initial?.replyBufferEnabled ?: true) }
    var visionModelId by remember { mutableStateOf(initial?.visionModelId ?: "") }
    var appearanceText by remember { mutableStateOf(initial?.appearanceText ?: "") }
    // 人物形象多图（最多 3 张）+ 对应识图结果
    var appearanceImagePaths by remember { mutableStateOf(normInitial?.appearanceImagePaths ?: emptyList()) }
    var appearanceImageDescs by remember { mutableStateOf(normInitial?.appearanceImageDescs ?: emptyList()) }
    // 多条开场白（至少留一条空输入框，保存时过滤空项）
    var openingLines by remember { mutableStateOf(normInitial?.openingLines?.ifEmpty { listOf("") } ?: listOf("")) }
    // 人物关系（创建时可选，编辑时只读显示）
    var relationshipPreset by remember { mutableStateOf(normInitial?.relationshipPreset ?: "") }
    var relationshipText by remember { mutableStateOf(normInitial?.relationshipText ?: "") }
    var relationshipStage by remember { mutableStateOf(normInitial?.relationshipStage ?: "") }
    var intimacy by remember { mutableIntStateOf(normInitial?.intimacy ?: 0) }
    var plotSimulation by remember { mutableStateOf(normInitial?.plotSimulation ?: false) }
    var plotLength by remember { mutableIntStateOf(normInitial?.plotLength ?: 1) }
    var sleepSimulation by remember { mutableStateOf(normInitial?.sleepSimulation ?: false) }
    var highQualityMemory by remember { mutableStateOf(normInitial?.highQualityMemory ?: false) }
    // 导入角色时带回的 AI 人设提示词（personaPrompt），创建时原样使用、不重新生成
    var importedPersonaPrompt by remember { mutableStateOf<String?>(null) }

    var showMbtiPicker by remember { mutableStateOf(false) }
    var showLangPicker by remember { mutableStateOf(false) }
    var showVisionPicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    // 编辑模式状态（仅编辑已有角色时用）：预览态=人物设定项只读灰色，编辑态=可改
    var isEditMode by remember { mutableStateOf(false) }
    var showAvatarMenu by remember { mutableStateOf(false) }
    var showAvatarPreview by remember { mutableStateOf(false) }
    var showEditConfirm by remember { mutableStateOf(false) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var showUnsavedChanges by remember { mutableStateOf(false) }
    var showMbtiIncomplete by remember { mutableStateOf(false) }

    fun deriveType(): String = buildString {
        append(if (mbtiEI < 0.5f) 'E' else 'I')
        append(if (mbtiNS < 0.5f) 'N' else 'S')
        append(if (mbtiTF < 0.5f) 'T' else 'F')
        append(if (mbtiPJ < 0.5f) 'P' else 'J')
    }

    // 展示用四格：每个维度按滑块位置显示对应字母，中间态显示 '-'（未选择）
    fun deriveDisplay(): String = buildString {
        append(mbtiDimensionLetter(mbtiEI, 'E', 'I'))
        append(mbtiDimensionLetter(mbtiNS, 'N', 'S'))
        append(mbtiDimensionLetter(mbtiTF, 'T', 'F'))
        append(mbtiDimensionLetter(mbtiPJ, 'P', 'J'))
    }

    // 已选维度数量；1..3 表示「只选了一半」，保存时需拦截提示
    fun mbtiSelectedCount(): Int =
        listOf(mbtiEI, mbtiNS, mbtiTF, mbtiPJ).count { !mbtiDimensionCentered(it) }

    fun selectMbtiType(type: String) {
        mbtiType = type
        val v = MBTI_PRESETS[type] ?: return
        mbtiEI = v[0]; mbtiNS = v[1]; mbtiTF = v[2]; mbtiPJ = v[3]
    }

    val isEditing = initial != null
    // 人物设定项是否可编辑：创建时或编辑态下可编辑；编辑已有角色的预览态只读
    val canEditPersona = !isEditing || isEditMode
    // 预览态（编辑已有角色但尚未进入编辑）：人物设定项只读灰色展示，直观区分「只能看不能改」
    val previewOnly = isEditing && !isEditMode

    fun resetToInitial() {
        name = initial?.name ?: ""
        gender = initial?.gender ?: ""
        age = initial?.age ?: ""
        avatarPath = initial?.avatarPath ?: ""
        mbtiType = initial?.mbtiType ?: ""
        mbtiEI = initial?.mbtiEI ?: 0.5f
        mbtiNS = initial?.mbtiNS ?: 0.5f
        mbtiTF = initial?.mbtiTF ?: 0.5f
        mbtiPJ = initial?.mbtiPJ ?: 0.5f
        presets = initial?.personalityPresets?.toSet() ?: emptySet()
        personalityText = initial?.personalityText ?: ""
        memory = initial?.memoryPerception ?: ""
        appearanceText = initial?.appearanceText ?: ""
        appearanceImagePaths = normInitial?.appearanceImagePaths ?: emptyList()
        appearanceImageDescs = normInitial?.appearanceImageDescs ?: emptyList()
        openingLines = normInitial?.openingLines?.ifEmpty { listOf("") } ?: listOf("")
        relationshipPreset = normInitial?.relationshipPreset ?: ""
        relationshipText = normInitial?.relationshipText ?: ""
        relationshipStage = normInitial?.relationshipStage ?: ""
        intimacy = normInitial?.intimacy ?: 0
        replyBufferSeconds = initial?.replyBufferSeconds ?: 3
        replyBufferEnabled = initial?.replyBufferEnabled ?: true
        plotSimulation = normInitial?.plotSimulation ?: false
        plotLength = normInitial?.plotLength ?: 1
        sleepSimulation = normInitial?.sleepSimulation ?: false
        highQualityMemory = normInitial?.highQualityMemory ?: false
        langModelId = initial?.languageModelId ?: ""
        visionModelId = initial?.visionModelId ?: ""
        webSearch = initial?.enableWebSearch
    }

    fun buildProfile() = CharacterProfile(
        name = name.trim(),
        gender = gender, age = age, avatarPath = avatarPath,
        mbtiType = if (mbtiSelectedCount() == 4) deriveType() else "",
        mbtiEI = mbtiEI, mbtiNS = mbtiNS, mbtiTF = mbtiTF, mbtiPJ = mbtiPJ,
        personalityPresets = PERSONALITY_PRESETS.filter { it in presets },
        personalityText = personalityText.trim(),
        memoryPerception = memory.trim(),
        languageModelId = langModelId,
        visionModelId = visionModelId,
        enableWebSearch = webSearch,
        appearanceText = appearanceText.trim(),
        appearanceImagePaths = appearanceImagePaths,
        appearanceImageDescs = appearanceImageDescs,
        replyBufferSeconds = replyBufferSeconds,
        replyBufferEnabled = replyBufferEnabled,
        openingLines = openingLines.map { it.trim() }.filter { it.isNotEmpty() },
        relationshipPreset = relationshipPreset,
        relationshipText = relationshipText.trim(),
        intimacy = if (initial == null) 0 else intimacy,  // 创建时由 AI 生成人设时判断，编辑时保持
        relationshipStage = if (initial == null) relationshipPreset else relationshipStage,
        personaPrompt = importedPersonaPrompt ?: (initial?.personaPrompt ?: ""),
        plotSimulation = plotSimulation,
        plotLength = plotLength,
        sleepSimulation = sleepSimulation,
        highQualityMemory = highQualityMemory
    )
    val hasChanges = initial == null || buildProfile() != initial
    // 模拟设置项是否有变动（预览态可直接改模拟设置，改动后需底部「保存设置」+ 返回时弹未保存提示）
    val simulationChanged = initial != null && (
        replyBufferSeconds != initial.replyBufferSeconds ||
        replyBufferEnabled != initial.replyBufferEnabled ||
        plotSimulation != initial.plotSimulation ||
        plotLength != initial.plotLength ||
        sleepSimulation != initial.sleepSimulation ||
        highQualityMemory != initial.highQualityMemory ||
        langModelId != initial.languageModelId ||
        visionModelId != initial.visionModelId ||
        webSearch != initial.enableWebSearch
    )

    fun requestBack() {
        when {
            !isEditing -> onBack()  // 创建模式：直接返回
            isEditMode -> if (hasChanges) showUnsavedChanges = true else { isEditMode = false; resetToInitial() }
            simulationChanged -> showUnsavedChanges = true  // 预览态改过模拟设置：弹未保存提示
            else -> onBack()  // 编辑模式预览态无改动：直接返回
        }
    }

    BackHandler(enabled = isEditing) { requestBack() }

    val canStart = name.isNotBlank()
    val mbtiIncomplete = mbtiSelectedCount() in 1..3
    var avatarCropSource by remember { mutableStateOf<Uri?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) avatarCropSource = uri
    }

    // 角色导入：从 JSON 文件读回人物设定（文字设定）并回填表单
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
            val profile = viewModel.importCharacterFromJson(json)
            if (profile != null) {
                name = profile.name
                gender = profile.gender
                age = profile.age
                mbtiType = profile.mbtiType
                mbtiEI = profile.mbtiEI
                mbtiNS = profile.mbtiNS
                mbtiTF = profile.mbtiTF
                mbtiPJ = profile.mbtiPJ
                presets = profile.personalityPresets.toSet()
                personalityText = profile.personalityText
                memory = profile.memoryPerception
                appearanceText = profile.appearanceText
                relationshipPreset = profile.relationshipPreset
                relationshipText = profile.relationshipText
                openingLines = profile.openingLines.ifEmpty { listOf("") }
                importedPersonaPrompt = profile.personaPrompt.ifBlank { null }
                android.widget.Toast.makeText(context, s.importSuccess, android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, s.importFailed, android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, s.importFailed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 角色导出：生成 JSON 文件后走系统分享
    fun exportCharacter() {
        try {
            val json = viewModel.exportCharacterJson(buildProfile())
            val file = File(context.cacheDir, "${name.trim().ifBlank { "character" }}.freechat.json")
            file.writeText(json)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.freechat.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, s.exportCharacter))
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, s.exportFailed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(s.unsavedBackTitle, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text(s.unsavedBackMessage, color = colors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) { Text(s.discard, color = colors.ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(s.cancel, color = colors.TextSecondary) }
            },
            containerColor = colors.Surface
        )
    }

    // 确定启动编辑
    if (showEditConfirm) {
        AlertDialog(
            onDismissRequest = { showEditConfirm = false },
            title = { Text(s.editConfirmTitle, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text(s.editConfirmMessage, color = colors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showEditConfirm = false; isEditMode = true }) { Text(s.confirm, color = colors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showEditConfirm = false }) { Text(s.cancel, color = colors.TextSecondary) }
            },
            containerColor = colors.Surface
        )
    }

    // 确认保存
    if (showSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text(s.saveConfirmTitle, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text(s.saveConfirmMessage, color = colors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showSaveConfirm = false
                    if (canStart && !mbtiIncomplete) onCreate(buildProfile())
                    else if (mbtiIncomplete) showMbtiIncomplete = true
                }) { Text(s.confirm, color = colors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirm = false }) { Text(s.cancel, color = colors.TextSecondary) }
            },
            containerColor = colors.Surface
        )
    }

    // 更改未保存
    if (showUnsavedChanges) {
        AlertDialog(
            onDismissRequest = { showUnsavedChanges = false },
            title = { Text(s.unsavedChangesTitle, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text(s.unsavedChangesMessage, color = colors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedChanges = false
                    resetToInitial()
                    if (isEditMode) isEditMode = false else onBack()
                }) { Text(s.discardChanges, color = colors.ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedChanges = false }) { Text(s.cancel, color = colors.TextSecondary) }
            },
            containerColor = colors.Surface
        )
    }

    // MBTI 未完整提示（选了一半维度就保存时拦截）
    if (showMbtiIncomplete) {
        AlertDialog(
            onDismissRequest = { showMbtiIncomplete = false },
            title = { Text(s.mbtiIncompleteTitle, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text(s.mbtiIncompleteMessage, color = colors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showMbtiIncomplete = false }) { Text(s.confirm, color = colors.Primary) }
            },
            containerColor = colors.Surface
        )
    }

    // 头像三选项
    if (showAvatarMenu) {
        AlertDialog(
            onDismissRequest = { showAvatarMenu = false },
            title = { Text(s.avatar, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (avatarPath.isNotBlank()) {
                        TextButton(onClick = { showAvatarMenu = false; showAvatarPreview = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(s.viewAvatar, color = colors.TextPrimary)
                        }
                        TextButton(onClick = { showAvatarMenu = false; avatarCropSource = Uri.fromFile(File(avatarPath)) }, modifier = Modifier.fillMaxWidth()) {
                            Text(s.editAvatar, color = colors.TextPrimary)
                        }
                    }
                    TextButton(onClick = { showAvatarMenu = false; avatarPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text(s.changeAvatar, color = colors.TextPrimary)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAvatarMenu = false }) { Text(s.cancel, color = colors.TextSecondary) } },
            containerColor = colors.Surface
        )
    }

    // 查看头像（放大）
    if (showAvatarPreview && avatarPath.isNotBlank()) {
        Dialog(onDismissRequest = { showAvatarPreview = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))
                    .clickable { showAvatarPreview = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = File(avatarPath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    // 把 content URI 复制到 filesDir，返回本地路径
    fun copyUriToFile(uri: Uri, prefix: String): String? = runCatching {
        val file = File(context.filesDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { out -> input.copyTo(out) }
        }
        file.absolutePath
    }.getOrNull()

    // 人物形象参考图（不裁切，直接上传，最多 3 张，保存时交给识图 AI 分析）
    val appearanceImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            copyUriToFile(uri, "appearance")?.let {
                if (appearanceImagePaths.size < 3) {
                    appearanceImagePaths = appearanceImagePaths + it
                    appearanceImageDescs = appearanceImageDescs + ""  // 新图待识别
                }
            }
        }
    }

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp).toPx() }

    if (showMbtiPicker) {
        MbtiTypeDialog(current = mbtiType, colors = colors, onSelect = { selectMbtiType(it); showMbtiPicker = false }, onDismiss = { showMbtiPicker = false })
    }
    if (showLangPicker) {
        LangModelDialog(viewModel, langModelId, colors, s, { id -> langModelId = id; showLangPicker = false }, { showLangPicker = false })
    }
    if (showVisionPicker) {
        VisionModelDialog(viewModel, visionModelId, colors, s, { id -> visionModelId = id; showVisionPicker = false }, { showVisionPicker = false })
    }
    if (avatarCropSource != null) {
        CropImageDialog(
            sourceUri = avatarCropSource!!,
            onDone = { path -> avatarPath = path; avatarCropSource = null },
            onDismiss = { avatarCropSource = null }
        )
    }

    Box(Modifier.fillMaxSize().background(colors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).background(colors.Background) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(top = statusBarHeightDp + titleBarAreaDp + 32.dp, bottom = 40.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ──── 头像（预览态只读；点击弹查看/编辑/更换三选项） ────
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(colors.SurfaceVariant)
                        .clickable(enabled = canEditPersona) { showAvatarMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarPath.isNotBlank()) {
                        AsyncImage(
                            model = File(avatarPath),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Filled.Person, null, tint = colors.TextTertiary, modifier = Modifier.size(40.dp))
                    }
                    if (canEditPersona) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape)
                                .background(colors.Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.PhotoCamera, null, tint = colors.OnPrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // ──── 角色名称 ────
            SectionLabel(Icons.Filled.Badge, s.characterName)
            GlassCard(hazeState, advancedMaterial, colors) {
                if (previewOnly) {
                    ReadonlyField(name, s.characterNameHint, colors)
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        enabled = canEditPersona,
                        placeholder = { Text(s.characterNameHint, color = colors.TextTertiary) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.TextPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedColors(colors)
                    )
                }
            }

            // ──── 基本信息（性别 / 年龄 / 人物关系） ────
            SectionLabel(Icons.Filled.Info, s.basicInfo)
            GlassCard(hazeState, advancedMaterial, colors) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 性别（独立一行，chip 可点选、再点取消；预览态只显示当前值纯文字）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.gender, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary, modifier = Modifier.width(56.dp))
                        Spacer(Modifier.width(8.dp))
                        if (previewOnly) {
                            Text(gender.ifBlank { "未设置" }, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("男", "女").forEach { g ->
                                    val sel = gender == g
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) colors.Primary.copy(alpha = 0.18f) else colors.SurfaceVariant.copy(alpha = 0.5f))
                                            .border(1.dp, if (sel) colors.Primary.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(10.dp))
                                            .clickable(enabled = canEditPersona) { gender = if (sel) "" else g }
                                            .padding(horizontal = 18.dp, vertical = 8.dp)
                                    ) {
                                        Text(g, color = if (sel) colors.Primary else colors.TextSecondary, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                    // 年龄（独立一行，不重叠）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.age, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary, modifier = Modifier.width(56.dp))
                        Spacer(Modifier.width(8.dp))
                        if (previewOnly) {
                            ReadonlyField(age, s.ageHint, colors)
                        } else {
                            OutlinedTextField(
                                value = age,
                                onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) },
                                singleLine = true,
                                enabled = canEditPersona,
                                placeholder = { Text(s.ageHint, color = colors.TextTertiary) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                                modifier = Modifier.width(140.dp),
                                colors = outlinedColors(colors)
                            )
                        }
                    }
                    // 分页线
                    HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f))
                    // 人物关系（创建时可选，编辑时只读，随剧情自动演进）
                    if (isEditing) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.relationshipStage, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary, modifier = Modifier.width(72.dp))
                                Text(
                                    relationshipStage.ifBlank { relationshipPreset.ifBlank { "未设定" } },
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.Primary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.intimacy, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary, modifier = Modifier.width(72.dp))
                                Text("$intimacy/100", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = colors.Primary, modifier = Modifier.width(56.dp))
                                LinearProgressIndicator(
                                    progress = { intimacy / 100f },
                                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = colors.Primary,
                                    trackColor = colors.SurfaceVariant
                                )
                            }
                            Text(s.relationshipReadonlyHint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextTertiary)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(s.relationshipPresetLabel, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            RELATIONSHIP_PRESETS.chunked(4).forEach { rowPresets ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    rowPresets.forEach { p ->
                                        val sel = relationshipPreset == p
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (sel) colors.Primary.copy(alpha = 0.18f) else colors.SurfaceVariant.copy(alpha = 0.5f))
                                                .border(1.dp, if (sel) colors.Primary.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(10.dp))
                                                .clickable { relationshipPreset = if (sel) "" else p }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(p, fontSize = 13.sp, color = if (sel) colors.Primary else colors.TextSecondary)
                                        }
                                    }
                                    repeat(4 - rowPresets.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            OutlinedTextField(
                                value = relationshipText,
                                onValueChange = { relationshipText = it },
                                placeholder = { Text(s.relationshipHint, color = colors.TextTertiary) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                colors = outlinedColors(colors)
                            )
                        }
                    }
                }
            }

            // ──── 人物性格 ────
            SectionLabel(Icons.Filled.Favorite, s.personality)
            GlassCard(hazeState, advancedMaterial, colors) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(s.personalityPresetLabel, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                    if (previewOnly) {
                        // 预览态：只显示已选预设纯文字，不显示选项框
                        Text(
                            presets.joinToString("、").ifBlank { "未设置" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.TextSecondary
                        )
                    } else {
                        // 2×2 等宽网格，每个选项框一样宽，不再被挤
                        PERSONALITY_PRESETS.chunked(2).forEach { rowPresets ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                rowPresets.forEach { p ->
                                    val sel = p in presets
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (sel) colors.Primary.copy(alpha = 0.18f) else colors.SurfaceVariant.copy(alpha = 0.5f))
                                            .border(1.dp, if (sel) colors.Primary.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(12.dp))
                                            .clickable(enabled = canEditPersona) { presets = if (sel) presets - p else presets + p }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(p, fontSize = 13.sp, color = if (sel) colors.Primary else colors.TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                    if (previewOnly) {
                        ReadonlyField(personalityText, s.personalityHint, colors)
                    } else {
                        OutlinedTextField(
                            value = personalityText,
                            onValueChange = { personalityText = it },
                            enabled = canEditPersona,
                            placeholder = { Text(s.personalityHint, color = colors.TextTertiary) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            colors = outlinedColors(colors)
                        )
                    }
                }
            }

            // ──── 人物形象（可选项：文字 + 参考图最多 3 张，AI 理解后作为形象参考） ────
            SectionLabel(Icons.Filled.Face, s.appearance)
            GlassCard(hazeState, advancedMaterial, colors) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (previewOnly) {
                        ReadonlyField(appearanceText, s.appearanceHint, colors)
                    } else {
                        OutlinedTextField(
                            value = appearanceText,
                            onValueChange = { appearanceText = it },
                            enabled = canEditPersona,
                            placeholder = { Text(s.appearanceHint, color = colors.TextTertiary) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            colors = outlinedColors(colors)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        appearanceImagePaths.forEachIndexed { idx, path ->
                            Box {
                                AsyncImage(
                                    model = File(path),
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .clickable(enabled = canEditPersona) {
                                            appearanceImagePaths = appearanceImagePaths.filterIndexed { i, _ -> i != idx }
                                            appearanceImageDescs = appearanceImageDescs.filterIndexed { i, _ -> i != idx }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        if (appearanceImagePaths.size < 3) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.SurfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, colors.Divider.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .clickable(enabled = canEditPersona) { appearanceImagePicker.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Add, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                                    Text(s.appearanceImage, fontSize = 11.sp, color = colors.Primary)
                                }
                            }
                        }
                    }
                }
            }

            // ──── 记忆感知 ────
            SectionLabel(Icons.Filled.Bookmark, s.memoryPerception)
            GlassCard(hazeState, advancedMaterial, colors) {
                if (previewOnly) {
                    ReadonlyField(memory, s.memoryPerceptionHint, colors)
                } else {
                    OutlinedTextField(
                        value = memory,
                        onValueChange = { memory = it },
                        enabled = canEditPersona,
                        placeholder = { Text(s.memoryPerceptionHint, color = colors.TextTertiary) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = outlinedColors(colors)
                    )
                }
            }

            // ──── MBTI 类型（非必填，放靠后位置） ────
            SectionLabel(Icons.Filled.Psychology, s.mbtiType)
            GlassCard(hazeState, advancedMaterial, colors) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(colors.AccentMuted.copy(alpha = if (previewOnly) 0.3f else 0.6f))
                            .clickable(enabled = canEditPersona) { showMbtiPicker = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            deriveDisplay(),
                            fontFamily = LocalMonoFontFamily.current,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (previewOnly) colors.TextTertiary else colors.Primary
                        )
                        if (!previewOnly) Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(20.dp))
                    }
                    // 四维滑块：中间为 0、往两边颜色加深（程度从中间向两侧）；预览态不显示选项滑块
                    if (!previewOnly) {
                        MbtiDimension("E", "I", mbtiEI, canEditPersona) { v -> mbtiEI = if (mbtiDimensionCentered(v)) 0.5f else v; mbtiType = deriveType() }
                        MbtiDimension("N", "S", mbtiNS, canEditPersona) { v -> mbtiNS = if (mbtiDimensionCentered(v)) 0.5f else v; mbtiType = deriveType() }
                        MbtiDimension("T", "F", mbtiTF, canEditPersona) { v -> mbtiTF = if (mbtiDimensionCentered(v)) 0.5f else v; mbtiType = deriveType() }
                        MbtiDimension("P", "J", mbtiPJ, canEditPersona) { v -> mbtiPJ = if (mbtiDimensionCentered(v)) 0.5f else v; mbtiType = deriveType() }
                    }
                }
            }

            // ──── 开场白（放人物设定区，MBTI 后；仅创建时设置，可多条，编辑页不显示）────
            if (!isEditing) {
                SectionLabel(Icons.Filled.Chat, s.openingLine)
                GlassCard(hazeState, advancedMaterial, colors) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        openingLines.forEachIndexed { idx, line ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = line,
                                    onValueChange = { v -> openingLines = openingLines.toMutableList().also { it[idx] = v } },
                                    placeholder = { Text(s.openingLineHint, color = colors.TextTertiary) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                                    modifier = Modifier.weight(1f),
                                    minLines = 1,
                                    colors = outlinedColors(colors)
                                )
                                IconButton(onClick = { openingLines = openingLines.filterIndexed { i, _ -> i != idx } }) {
                                    Icon(Icons.Filled.Close, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        TextButton(onClick = { openingLines = openingLines + "" }) {
                            Icon(Icons.Filled.Add, null, tint = colors.Primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(s.openingLineAdd, color = colors.Primary, fontSize = 13.sp)
                        }
                    }
                }
            }

            // 人物设定 / 模拟设置 之间用分页线分隔，保留空间区分
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colors.Divider.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))

            // ──── 高质量检索回复（模拟设置第一位，创建/编辑都可随时切换）────
            SectionLabel(Icons.Filled.TravelExplore, s.highQualityMemory)
            GlassCard(hazeState, advancedMaterial, colors) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.highQualityMemory, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                        Text(s.highQualityMemoryDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                    }
                    Switch(
                        checked = highQualityMemory,
                        onCheckedChange = { highQualityMemory = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.OnPrimary,
                            checkedTrackColor = colors.Primary,
                            uncheckedThumbColor = colors.TextTertiary,
                            uncheckedTrackColor = colors.SurfaceVariant
                        )
                    )
                }
            }

            // ──── 剧情模式（模拟设置；开启后对话变为剧情共创，作息/回复缓冲仅存在于拟人化微信聊天） ────
            SectionLabel(Icons.Filled.AutoStories, s.plotSimulation)
            GlassCard(hazeState, advancedMaterial, colors) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.plotSimulation, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text(s.plotSimulationDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                        }
                        Switch(
                            checked = plotSimulation,
                            onCheckedChange = { plotSimulation = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.OnPrimary,
                                checkedTrackColor = colors.Primary,
                                uncheckedThumbColor = colors.TextTertiary,
                                uncheckedTrackColor = colors.SurfaceVariant
                            )
                        )
                    }
                    if (plotSimulation) {
                        HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f))
                        Text(s.plotLength, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(s.plotLengthShort, s.plotLengthMid, s.plotLengthLong, s.plotLengthExtraLong)
                                .withIndex().chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { (idx, label) ->
                                            val sel = plotLength == idx
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (sel) colors.Primary.copy(alpha = 0.18f) else colors.SurfaceVariant.copy(alpha = 0.5f))
                                                    .border(1.dp, if (sel) colors.Primary.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(10.dp))
                                                    .clickable { plotLength = idx }
                                                    .padding(vertical = 9.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(label, fontSize = 12.sp, color = if (sel) colors.Primary else colors.TextSecondary)
                                            }
                                        }
                                        if (row.size < 2) Spacer(Modifier.weight(1f))
                                    }
                                }
                        }
                        // 例句引导：展示剧情模式该发什么样的提示词、AI 会怎么回（避免当成普通微信聊天）
                        HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.SurfaceVariant.copy(alpha = 0.4f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(s.plotExampleTitle, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = colors.TextPrimary)
                            Text(
                                "你：${s.plotExampleUser}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 18.sp),
                                color = colors.TextSecondary
                            )
                            Text(
                                "AI：${s.plotExampleAi}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 18.sp),
                                color = colors.TextSecondary
                            )
                        }
                    }
                }
            }

            // 回复缓冲 / 作息模拟 仅存在于「拟人化微信聊天」；开启剧情模式后关闭并不再显示
            if (!plotSimulation) {
                // ──── 回复缓冲（连发消息合并理解的等待窗口；可关，关闭后发送即开始思考）────
                SectionLabel(Icons.Filled.Schedule, s.replyBuffer)
                GlassCard(hazeState, advancedMaterial, colors) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.replyBufferEnable, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Text(s.replyBufferDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                            Switch(
                                checked = replyBufferEnabled,
                                onCheckedChange = { replyBufferEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.OnPrimary,
                                    checkedTrackColor = colors.Primary,
                                    uncheckedThumbColor = colors.TextTertiary,
                                    uncheckedTrackColor = colors.SurfaceVariant
                                )
                            )
                        }
                        if (replyBufferEnabled) {
                            HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${replyBufferSeconds}s", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.Primary)
                                Spacer(Modifier.width(14.dp))
                                Slider(
                                    value = replyBufferSeconds.toFloat(),
                                    onValueChange = { replyBufferSeconds = it.roundToInt().coerceIn(1, 6) },
                                    valueRange = 1f..6f,
                                    steps = 4,
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.Primary,
                                        activeTrackColor = colors.Primary,
                                        inactiveTrackColor = colors.SurfaceVariant
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // ──── 作息模拟（创建和编辑都可改） ────
                SectionLabel(Icons.Filled.Bedtime, s.sleepSimulation)
                GlassCard(hazeState, advancedMaterial, colors) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.sleepSimulation, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text(s.sleepSimulationDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                        }
                        Switch(
                            checked = sleepSimulation,
                            onCheckedChange = { sleepSimulation = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.OnPrimary,
                                checkedTrackColor = colors.Primary,
                                uncheckedThumbColor = colors.TextTertiary,
                                uncheckedTrackColor = colors.SurfaceVariant
                            )
                        )
                    }
                }
            }

            // ──── 语言模型 / 联网搜索（每角色独立） ────
            SectionLabel(Icons.Filled.SmartToy, s.languageModel)
            GlassCard(hazeState, advancedMaterial, colors) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showLangPicker = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.languageModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text(
                                if (langModelId.isBlank()) "跟随全局" else viewModel.languageModels.value.find { it.id == langModelId }?.displayName ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                                color = colors.TextSecondary
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 14.dp))
                    // 识图模型（每角色独立）
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showVisionPicker = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.visionModel, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                            Text(
                                if (visionModelId.isBlank()) "跟随全局" else viewModel.visionModels.value.find { it.id == visionModelId }?.displayName ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                                color = colors.TextSecondary
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Language, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(s.webSearch, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                        }
                        Switch(
                            checked = webSearch ?: viewModel.enableWebSearch.collectAsState().value,
                            onCheckedChange = { webSearch = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.OnPrimary,
                                checkedTrackColor = colors.Primary,
                                uncheckedThumbColor = colors.TextTertiary,
                                uncheckedTrackColor = colors.SurfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ──── 底部按钮（仅创建时显示；编辑时用右上角「保存」按钮） ────
            if (!isEditing) {
                Button(
                    onClick = {
                        if (!canStart) return@Button
                        if (mbtiIncomplete) { showMbtiIncomplete = true; return@Button }
                        onCreate(buildProfile())
                    },
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.Primary,
                        contentColor = colors.OnPrimary,
                        disabledContainerColor = colors.SurfaceVariant,
                        disabledContentColor = colors.TextTertiary
                    )
                ) {
                    Text(s.startChat, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            // 预览态直接改了模拟设置：底部显示「保存设置」，无改动则不显示
            if (isEditing && !isEditMode && simulationChanged) {
                Button(
                    onClick = { onSaveSimulation(buildProfile()) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.Primary,
                        contentColor = colors.OnPrimary
                    )
                ) {
                    Text(s.saveSettings, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 顶部标题栏背景
        if (advancedMaterial) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp)
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
                    .background(colors.Background)
            )
        }

        // 悬浮标题栏（返回键 + 标题 + 编辑/保存按钮）
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { requestBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.TextPrimary)
            }
            Text(
                s.characterSetup,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.weight(1f))
            if (isEditing) {
                IconButton(onClick = { exportCharacter() }) {
                    Icon(Icons.Filled.Share, s.exportCharacter, tint = colors.TextSecondary, modifier = Modifier.size(22.dp))
                }
            } else {
                IconButton(onClick = { importPicker.launch("*/*") }) {
                    Icon(Icons.Filled.Upload, s.importCharacter, tint = colors.TextSecondary, modifier = Modifier.size(22.dp))
                }
            }
            if (isEditing) {
                IconButton(onClick = {
                    if (isEditMode) showSaveConfirm = true else showEditConfirm = true
                }) {
                    Icon(
                        if (isEditMode) Icons.Filled.Check else Icons.Filled.Edit,
                        contentDescription = if (isEditMode) s.save else s.edit,
                        tint = colors.Primary
                    )
                }
            }
        }
    }
}

/** MBTI 单维度滑块：中间为 0、往两边颜色加深（程度条从中心向两侧延伸，默认在中间） */
@Composable
private fun MbtiDimension(left: String, right: String, value: Float, enabled: Boolean = true, onChange: (Float) -> Unit) {
    val colors = LocalFreeChatColors.current
    val trackHeight = 4.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(left, fontFamily = LocalMonoFontFamily.current, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = if (enabled && value < 0.5f) colors.Primary else colors.TextTertiary)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .padding(horizontal = 6.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { pos ->
                        onChange((pos.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { pos -> onChange((pos.x / size.width).coerceIn(0f, 1f)) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            onChange((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                val th = trackHeight.toPx()
                // 背景轨道（全宽淡色）
                drawRoundRect(
                    color = colors.SurfaceVariant,
                    topLeft = Offset(0f, cy - th / 2f),
                    size = Size(size.width, th),
                    cornerRadius = CornerRadius(th / 2f, th / 2f)
                )
                // 程度条：从中心(0.5)向 value 方向延伸，越远离中心越"深"
                val cx = size.width * 0.5f
                val vx = size.width * value.coerceIn(0f, 1f)
                val startX = minOf(cx, vx)
                val endX = maxOf(cx, vx)
                if (endX - startX > 0.5f) {
                    drawRoundRect(
                        color = if (enabled) colors.Primary else colors.TextTertiary,
                        topLeft = Offset(startX, cy - th / 2f),
                        size = Size(endX - startX, th),
                        cornerRadius = CornerRadius(th / 2f, th / 2f)
                    )
                }
                // 中心刻度（暗示 0 点）
                drawLine(
                    color = colors.Divider,
                    start = Offset(cx, cy - 7.dp.toPx()),
                    end = Offset(cx, cy + 7.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
                // 滑块 thumb
                drawCircle(color = if (enabled) colors.Primary else colors.TextTertiary, radius = 8.dp.toPx(), center = Offset(vx, cy))
            }
        }
        Text(right, fontFamily = LocalMonoFontFamily.current, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = if (enabled && value >= 0.5f) colors.Primary else colors.TextTertiary)
    }
}

/** 磨砂玻璃卡片（高级材质）或实色卡片（非高级材质）：统一透光磨砂 + 阴影，与输入框同款 */
@Composable
private fun GlassCard(hazeState: dev.chrisbanes.haze.HazeState, advancedMaterial: Boolean, colors: com.freechat.ui.theme.FreeChatColors, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun outlinedColors(colors: com.freechat.ui.theme.FreeChatColors) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = colors.TextPrimary,
    unfocusedTextColor = colors.TextPrimary,
    focusedBorderColor = colors.Primary,
    unfocusedBorderColor = colors.Divider,
    cursorColor = colors.Primary
)

/** 预览态只读文本：无输入框、灰色，直观告诉用户「只能看不能改」；空值显示灰色占位 */
@Composable
private fun ReadonlyField(value: String, placeholder: String, colors: com.freechat.ui.theme.FreeChatColors) {
    Text(
        value.ifBlank { placeholder },
        style = MaterialTheme.typography.bodyMedium,
        color = if (value.isBlank()) colors.TextTertiary else colors.TextSecondary
    )
}

/** MBTI 16 类型选择对话框 */
@Composable
private fun MbtiTypeDialog(current: String, colors: com.freechat.ui.theme.FreeChatColors, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val types = listOf(
        listOf("INTJ", "INTP", "ENTJ", "ENTP"),
        listOf("INFJ", "INFP", "ENFJ", "ENFP"),
        listOf("ISTJ", "ISFJ", "ESTJ", "ESFJ"),
        listOf("ISTP", "ISFP", "ESTP", "ESFP")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Surface,
        title = { Text("MBTI 类型", color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { t ->
                            val sel = t == current
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) colors.AccentMuted else colors.SurfaceVariant.copy(alpha = 0.5f))
                                    .clickable { onSelect(t) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(t, fontFamily = LocalMonoFontFamily.current, fontSize = 13.sp, color = if (sel) colors.Primary else colors.TextPrimary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = colors.TextSecondary) } }
    )
}

/** 语言模型选择对话框（每角色独立） */
@Composable
private fun LangModelDialog(viewModel: ChatViewModel, current: String, colors: com.freechat.ui.theme.FreeChatColors, s: com.freechat.i18n.AppStrings, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Surface,
        title = { Text(s.languageModel, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (current.isBlank()) colors.AccentMuted else colors.SurfaceVariant.copy(alpha = 0.5f))
                        .clickable { onSelect("") }
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (current.isBlank()) "跟随全局（默认）" else "跟随全局", color = if (current.isBlank()) colors.Primary else colors.TextPrimary, fontWeight = if (current.isBlank()) FontWeight.Bold else FontWeight.Normal)
                        if (current.isBlank()) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                    }
                }
                viewModel.languageModels.value.forEach { m ->
                    val sel = m.id == current
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (sel) colors.AccentMuted else colors.SurfaceVariant.copy(alpha = 0.5f))
                            .clickable { onSelect(m.id) }
                            .padding(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.displayName, fontFamily = LocalMonoFontFamily.current, color = if (sel) colors.Primary else colors.TextPrimary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                Text(m.description, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                            if (sel) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel, color = colors.TextSecondary) } }
    )
}

/** 识图模型选择对话框（每角色独立） */
@Composable
private fun VisionModelDialog(viewModel: ChatViewModel, current: String, colors: com.freechat.ui.theme.FreeChatColors, s: com.freechat.i18n.AppStrings, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Surface,
        title = { Text(s.visionModel, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (current.isBlank()) colors.AccentMuted else colors.SurfaceVariant.copy(alpha = 0.5f))
                        .clickable { onSelect("") }
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (current.isBlank()) "跟随全局（默认）" else "跟随全局", color = if (current.isBlank()) colors.Primary else colors.TextPrimary, fontWeight = if (current.isBlank()) FontWeight.Bold else FontWeight.Normal)
                        if (current.isBlank()) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                    }
                }
                viewModel.visionModels.value.forEach { m ->
                    val sel = m.id == current
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (sel) colors.AccentMuted else colors.SurfaceVariant.copy(alpha = 0.5f))
                            .clickable { onSelect(m.id) }
                            .padding(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.displayName, fontFamily = LocalMonoFontFamily.current, color = if (sel) colors.Primary else colors.TextPrimary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                Text(m.description, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                            if (sel) Icon(Icons.Filled.Check, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel, color = colors.TextSecondary) } }
    )
}

/** 头像裁切弹窗：方形裁剪 + 双指缩放/单指平移，无第三方依赖 */
@Composable
private fun CropImageDialog(
    sourceUri: Uri,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current

    // 加载 bitmap（降采样到 2048 以内，避免 OOM）
    // 支持 file:// 与 content:// 两种 Uri（编辑头像用 file://，更换头像用 content://）
    fun openInput(uri: Uri): java.io.InputStream? =
        if (uri.scheme == "file") runCatching { File(uri.path!!).inputStream() }.getOrNull()
        else context.contentResolver.openInputStream(uri)

    val bitmap = remember(sourceUri) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openInput(sourceUri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / sample > 2048 || opts.outHeight / sample > 2048) sample *= 2
            val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
            openInput(sourceUri)?.use { BitmapFactory.decodeStream(it, null, opts2) }
        }.getOrNull()
    }

    if (bitmap == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var tx by remember { mutableFloatStateOf(0f) }
    var ty by remember { mutableFloatStateOf(0f) }

    val frameSizeDp = (config.screenWidthDp - 32).dp
    val frameSizePx = with(density) { frameSizeDp.toPx() }
    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    val cover = maxOf(frameSizePx / bw, frameSizePx / bh)
    val centerX = (frameSizePx - bw * cover) / 2f
    val centerY = (frameSizePx - bh * cover) / 2f
    val half = frameSizePx / 2f

    fun cropAndSave() {
        // 屏幕坐标 → 源 bitmap 像素坐标
        fun toSrcX(screenX: Float) = (((screenX - tx - half) / scale) + half - centerX) / cover
        fun toSrcY(screenY: Float) = (((screenY - ty - half) / scale) + half - centerY) / cover
        val left = toSrcX(0f).coerceIn(0f, bw)
        val top = toSrcY(0f).coerceIn(0f, bh)
        val right = toSrcX(frameSizePx).coerceIn(0f, bw)
        val bottom = toSrcY(frameSizePx).coerceIn(0f, bh)
        val w = (right - left).toInt()
        val h = (bottom - top).toInt()
        if (w <= 0 || h <= 0) return
        val cropped = runCatching { Bitmap.createBitmap(bitmap, left.toInt(), top.toInt(), w, h) }.getOrNull() ?: return
        val file = File(context.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
        runCatching {
            FileOutputStream(file).use { out -> cropped.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            cropped.recycle()
            file.absolutePath
        }.getOrNull()?.let { onDone(it) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            // 图片区（方形，ContentScale.Crop 铺满 + 用户缩放平移）
            Box(
                modifier = Modifier
                    .size(frameSizeDp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            tx += pan.x
                            ty += pan.y
                        }
                    }
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = tx
                            translationY = ty
                        },
                    contentScale = ContentScale.Crop
                )
            }
            // 底部操作
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                TextButton(onClick = onDismiss) { Text("取消", color = Color.White) }
                Button(onClick = { cropAndSave() }) { Text("确定") }
            }
        }
    }
}
