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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.freechat.i18n.LocalStrings
import com.freechat.model.*
import com.freechat.proactive.ProactiveScheduler
import com.freechat.ui.components.SheetActionRow
import com.freechat.ui.components.SheetOption
import com.freechat.ui.components.SheetPanel
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalMonoFontFamily
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.theme.selectedFill
import com.freechat.ui.theme.selectedText
import com.freechat.viewmodel.ChatViewModel
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.theme.pageHeaderBackground

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
    var referencePrototype by remember { mutableStateOf(initial?.referencePrototype ?: "") }
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
    // 对话模式（1.0.53）：创建时**必选**，所以创建页的初值是 null（三个选项一个都不选中，
    // 不选不给建）；编辑页永远带着已定的档位进来。发出第一条消息之后锁定，见 modeLocked。
    var dialogueMode by remember { mutableStateOf<Int?>(normInitial?.dialogueMode) }
    var plotLength by remember { mutableIntStateOf(normInitial?.plotLength ?: 1) }
    var sleepSimulation by remember { mutableStateOf(normInitial?.sleepSimulation ?: false) }
    var highQualityMemory by remember { mutableStateOf(normInitial?.highQualityMemory ?: false) }
    var deepThinking by remember { mutableStateOf(normInitial?.deepThinking ?: false) }
    // 新建默认 6.0（1.0.64 起）；编辑老角色时用的是它自己档案里的值（可能还是 5.0，不动它）
    var aiCreativity by remember {
        mutableFloatStateOf(normInitial?.aiCreativity ?: CharacterProfile.DEFAULT_AI_CREATIVITY)
    }
    var proactiveEnabled by remember { mutableStateOf(normInitial?.proactiveEnabled ?: false) }
    // 模拟设定扩增（1.0.34）：配角 / 规则 / 用户形象。都是「一句话交代清楚」的自由文本，
    // 不做卡片、不做列表 —— 用户想写多少人物、多少条规矩，都写在一个框里，交给 AI 读。
    var supportingCast by remember { mutableStateOf(initial?.supportingCast ?: "") }
    var worldRules by remember { mutableStateOf(initial?.worldRules ?: "") }
    var userPersona by remember { mutableStateOf(initial?.userPersona ?: "") }
    // 精准到点依赖系统的「闹钟和提醒」权限。没授权也能用，只是到点会有几分钟浮动，
    // 所以这里只是提示、不阻断。用户从系统设置页返回时要重新读一次（否则点了授权回来还显示未授权）
    val setupContext = LocalContext.current
    var exactAlarmOk by remember { mutableStateOf(ProactiveScheduler.canScheduleExact(setupContext)) }
    val setupLifecycle = LocalLifecycleOwner.current
    DisposableEffect(setupLifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmOk = ProactiveScheduler.canScheduleExact(setupContext)
            }
        }
        setupLifecycle.lifecycle.addObserver(obs)
        onDispose { setupLifecycle.lifecycle.removeObserver(obs) }
    }
    // 导入角色时带回的 AI 人设提示词（personaPrompt），创建时原样使用、不重新生成
    var importedPersonaPrompt by remember { mutableStateOf<String?>(null) }

    var showMbtiPicker by remember { mutableStateOf(false) }
    var showLangPicker by remember { mutableStateOf(false) }
    var showVisionPicker by remember { mutableStateOf(false) }
    /** 正在等「代价确认」的那个重开关：点了开启先弹浮层，确认后才真的打开（见 HeavyToggle） */
    var pendingHeavy by remember { mutableStateOf<HeavyToggle?>(null) }
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
        referencePrototype = initial?.referencePrototype ?: ""
        appearanceText = initial?.appearanceText ?: ""
        appearanceImagePaths = normInitial?.appearanceImagePaths ?: emptyList()
        appearanceImageDescs = normInitial?.appearanceImageDescs ?: emptyList()
        openingLines = normInitial?.openingLines?.ifEmpty { listOf("") } ?: listOf("")
        relationshipPreset = normInitial?.relationshipPreset ?: ""
        relationshipText = normInitial?.relationshipText ?: ""
        replyBufferSeconds = initial?.replyBufferSeconds ?: 3
        replyBufferEnabled = initial?.replyBufferEnabled ?: true
        dialogueMode = normInitial?.dialogueMode
        plotLength = normInitial?.plotLength ?: 1
        sleepSimulation = normInitial?.sleepSimulation ?: false
        highQualityMemory = normInitial?.highQualityMemory ?: false
        deepThinking = normInitial?.deepThinking ?: false
        aiCreativity = normInitial?.aiCreativity ?: CharacterProfile.DEFAULT_AI_CREATIVITY
        proactiveEnabled = normInitial?.proactiveEnabled ?: false
        supportingCast = initial?.supportingCast ?: ""
        worldRules = initial?.worldRules ?: ""
        userPersona = initial?.userPersona ?: ""
        langModelId = initial?.languageModelId ?: ""
        visionModelId = initial?.visionModelId ?: ""
        webSearch = initial?.enableWebSearch
    }

    fun buildProfile() = CharacterProfile(
        name = name.trim(),
        gender = gender, age = age, avatarPath = avatarPath,
        // 头像的内容指纹是**跨设备共用同一张脸**的凭据，编辑档案时必须原样带过去。
        // 漏了它后果很隐蔽：这张档案在用户眼里一切正常，但下一次同步会被当成
        // 「本地从来没有过头像」，用户把头像删掉这件事就传不到另一台设备
        //（换台设备登录，一张早该消失的旧头像还挂在那儿）。
        avatarHash = initial?.avatarHash ?: "",
        mbtiType = if (mbtiSelectedCount() == 4) deriveType() else "",
        mbtiEI = mbtiEI, mbtiNS = mbtiNS, mbtiTF = mbtiTF, mbtiPJ = mbtiPJ,
        personalityPresets = PERSONALITY_PRESETS.filter { it in presets },
        personalityText = personalityText.trim(),
        memoryPerception = memory.trim(),
        referencePrototype = referencePrototype.trim(),
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
        personaPrompt = importedPersonaPrompt ?: (initial?.personaPrompt ?: ""),
        // plotSimulation 是 1.0.28 前的旧字段，保留写回以便迁移幂等（否则改成「微信聊天」后又被迁回剧情补足）
        plotSimulation = dialogueMode == DialogueMode.PLOT,
        // 走到这里一定已经选过档位了（创建页不选就不给建），兜底给微信聊天
        dialogueMode = dialogueMode ?: DialogueMode.WECHAT,
        plotLength = plotLength,
        sleepSimulation = sleepSimulation,
        highQualityMemory = highQualityMemory,
        // 母开关关掉时把子开关一并落成 false：留着一个 true 在档案里，
        // 哪天用户又打开增强检索，它会莫名地自己生效
        deepThinking = deepThinking && highQualityMemory,
        aiCreativity = aiCreativity,
        proactiveEnabled = proactiveEnabled,
        supportingCast = supportingCast.trim(),
        worldRules = worldRules.trim(),
        userPersona = userPersona.trim()
    )
    val hasChanges = initial == null || buildProfile() != initial
    // 模拟设置项是否有变动（预览态可直接改模拟设置，改动后需底部「保存设置」+ 返回时弹未保存提示）
    val simulationChanged = initial != null && (
        replyBufferSeconds != initial.replyBufferSeconds ||
        replyBufferEnabled != initial.replyBufferEnabled ||
        dialogueMode != normInitial?.dialogueMode ||
        plotLength != initial.plotLength ||
        sleepSimulation != initial.sleepSimulation ||
        highQualityMemory != initial.highQualityMemory ||
        (deepThinking && highQualityMemory) != initial.deepThinking ||
        aiCreativity != initial.aiCreativity ||
        proactiveEnabled != initial.proactiveEnabled ||
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
    // 对话模式（1.0.53）：
    //  · modeMissing —— 创建时还没选档位（不选不给建，点了「创建角色」会拦一道提示）；
    //  · modeLocked  —— 这段对话已经聊起来了，档位锁死。判据就是「当前对话有没有消息」：
    //    历史记录是原样喂给模型的，里面有另一种写法的旧回复时，模型会照着自己以前的写法来，
    //    这正是用户报的「切换了却不变」的根因 —— 与其让用户改了却没效果，不如明说不能改。
    val modeMissing = !isEditing && dialogueMode == null
    val convMessages by viewModel.messages.collectAsState()
    val modeLocked = isEditing && convMessages.isNotEmpty()
    var showModeMissing by remember { mutableStateOf(false) }
    var showModeLocked by remember { mutableStateOf(false) }
    // 创建时每换一档弹一次的提醒（只说「选了就不能改」，不解释原因）
    var showModePickWarn by remember { mutableStateOf(false) }
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
                referencePrototype = profile.referencePrototype
                appearanceText = profile.appearanceText
                relationshipPreset = profile.relationshipPreset
                relationshipText = profile.relationshipText
                openingLines = profile.openingLines.ifEmpty { listOf("") }
                // 1.0.34 新增的三块必须一起回填：导出那边早就写了，导入这边漏读，
                // 结果就是「文件里有、导进来没了」——用户只会以为导出坏了。
                supportingCast = profile.supportingCast
                worldRules = profile.worldRules
                userPersona = profile.userPersona
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

    // 放弃修改 / 进入编辑 / 确认保存 / 更改未保存 / MBTI 未选完 / 头像菜单 —— 六个确认框与菜单，
    // 原来都是 AlertDialog。AlertDialog 是**独立窗口**，看不到本页自己画好的内容，所以「模糊背景」
    // 这件事它做不到，只能把背景压暗（就是「悬浮卡片背景加暗」那种效果）。
    // 现在统一换成**窗口内**的底部磨砂玻璃弹层 SheetPanel，实际渲染在下面根 Box 的末尾
    // —— 只有挂在那儿才盖得住整页（含悬浮标题栏），挂这里会被后续布局压在下面。

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

    // MBTI / 语言模型 / 识图模型三个选择器原本也在这里弹 AlertDialog，现在同样是窗口内的
    // 磨砂玻璃弹层，渲染在下面根 Box 的末尾（见 SheetPanel）。

    if (avatarCropSource != null) {
        com.freechat.ui.components.AvatarCropDialog(
            sourceUri = avatarCropSource!!,
            // 角色卡的头像就是一条本地文件路径，成品即终点，不需要留原图
            onDone = { path, _ -> avatarPath = path; avatarCropSource = null },
            onDismiss = { avatarCropSource = null }
        )
    }

    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
                // 键盘让位（1.0.50）：App 是 adjustNothing + edge-to-edge，系统不会顶窗口，
                // 得自己把视口按键盘高度缩回来。**必须放在 verticalScroll 之前** ——
                // 放后面就成了「滚动内容里的一段留白」，视口还是被键盘盖着的那一整屏，
                // 光标该被挡住还是被挡住。放前面 = 可视区真的变矮，内容才有地方滚上来。
                .imePadding()
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
                        .frostedCard(
                            hazeState, colors, advancedMaterial, CircleShape,
                            compact = true,
                            fallback = colors.SurfaceVariant
                        )
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
                    // 属性名（小、灰）与属性值（大、亮）刻意拉开：左边是标签，右边才是内容
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FieldLabel(s.gender, colors)
                        Spacer(Modifier.width(8.dp))
                        if (previewOnly) {
                            Text(
                                gender.ifBlank { s.notSet }
                                    .let { presetDisplay(it, s.genderKeys, s.genderLabels) },
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.Primary
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                s.genderKeys.forEachIndexed { gi, g ->
                                    val sel = gender == g
                                    Box(
                                        modifier = Modifier
                                            .frostedCard(
                                                hazeState, colors, advancedMaterial, RoundedCornerShape(10.dp),
                                                compact = true,
                                                fallback = if (sel) colors.Primary.copy(alpha = 0.18f) else colors.SurfaceVariant.copy(alpha = 0.5f),
                                                face = if (sel) colors.Primary.copy(alpha = 0.18f) else null
                                            )
                                            .border(1.dp, if (sel) colors.Primary.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(10.dp))
                                            .clickable(enabled = canEditPersona) { gender = if (sel) "" else g }
                                            .padding(horizontal = 18.dp, vertical = 8.dp)
                                    ) {
                                        Text(s.genderLabels.getOrElse(gi) { g }, color = if (sel) colors.Primary else colors.TextSecondary, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                    // 年龄（独立一行，不重叠）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FieldLabel(s.age, colors)
                        Spacer(Modifier.width(8.dp))
                        if (previewOnly) {
                            Text(
                                age.ifBlank { s.ageHint },
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (age.isBlank()) colors.TextTertiary else colors.Primary
                            )
                        } else {
                            OutlinedTextField(
                                value = age,
                                onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) },
                                singleLine = true,
                                enabled = canEditPersona,
                                placeholder = { Text(s.ageHint, color = colors.TextTertiary) },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.Primary, fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.width(140.dp),
                                colors = outlinedColors(colors)
                            )
                        }
                    }
                    // 分页线
                    HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f))
                    // 人物关系（创建时可选，编辑时只读）：只显示用户设定的关系与细节，不显示任何亲密度数值
                    if (isEditing) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FieldLabel(s.relationship, colors)
                                Text(
                                    relationshipPreset.ifBlank { s.notSet }
                                        .let { presetDisplay(it, RELATIONSHIP_PRESETS, s.relationshipPresetLabels) },
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.Primary
                                )
                            }
                            if (relationshipText.isNotBlank()) {
                                Text(
                                    relationshipText,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif, fontSize = 13.sp),
                                    color = colors.TextSecondary
                                )
                            }
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
                                                .frostedCard(
                                                    hazeState, colors, advancedMaterial, RoundedCornerShape(10.dp),
                                                    compact = true,
                                                    fallback = if (sel) colors.Primary.copy(alpha = 0.18f) else colors.SurfaceVariant.copy(alpha = 0.5f),
                                                    face = if (sel) colors.Primary.copy(alpha = 0.18f) else null
                                                )
                                                .border(1.dp, if (sel) colors.Primary.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(10.dp))
                                                .clickable { relationshipPreset = if (sel) "" else p }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(presetDisplay(p, RELATIONSHIP_PRESETS, s.relationshipPresetLabels), fontSize = 13.sp, color = if (sel) colors.Primary else colors.TextSecondary)
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
                    // 分页线 + 参考原型（可选）：填网络上已有的知名角色名，人设写得太少时由 AI 联网搜索补全
                    // 排版与上面的「人物关系 / 年龄」一致：左标签、右取值
                    HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FieldLabel(s.referencePrototypeLabel, colors)
                            Spacer(Modifier.width(8.dp))
                            if (previewOnly) {
                                Text(
                                    referencePrototype.ifBlank { s.notSet },
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (referencePrototype.isBlank()) colors.TextTertiary else colors.Primary
                                )
                            } else {
                                OutlinedTextField(
                                    value = referencePrototype,
                                    onValueChange = { referencePrototype = it },
                                    singleLine = true,
                                    enabled = canEditPersona,
                                    placeholder = { Text(s.referencePrototypeHint, color = colors.TextTertiary) },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.Primary, fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.weight(1f),
                                    colors = outlinedColors(colors)
                                )
                            }
                        }
                        if (!previewOnly) {
                            Text(
                                s.referencePrototypeDesc,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                                color = colors.TextTertiary
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
                            presets.map { presetDisplay(it, PERSONALITY_PRESETS, s.personalityPresetLabels) }
                                .joinToString(s.listSeparator).ifBlank { s.notSet },
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
                                            .frostedCard(
                                                hazeState, colors, advancedMaterial, RoundedCornerShape(12.dp),
                                                compact = true,
                                                fallback = if (sel) colors.Primary.copy(alpha = 0.18f) else colors.SurfaceVariant.copy(alpha = 0.5f),
                                                face = if (sel) colors.Primary.copy(alpha = 0.18f) else null
                                            )
                                            .border(1.dp, if (sel) colors.Primary.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(12.dp))
                                            .clickable(enabled = canEditPersona) { presets = if (sel) presets - p else presets + p }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(presetDisplay(p, PERSONALITY_PRESETS, s.personalityPresetLabels), fontSize = 13.sp, color = if (sel) colors.Primary else colors.TextSecondary)
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

            // ──── 配角 ────
            // 不做「一人一张卡」的结构化表单：那要求用户先把人物关系想清楚再填，
            // 大部分人宁可不填。一个输入框、爱怎么写怎么写，AI 一样能读懂。
            SectionLabel(Icons.Filled.Groups, s.supportingCast)
            GlassCard(hazeState, advancedMaterial, colors) {
                if (previewOnly) {
                    ReadonlyField(supportingCast, s.supportingCastHint, colors)
                } else {
                    OutlinedTextField(
                        value = supportingCast,
                        onValueChange = { supportingCast = it },
                        enabled = canEditPersona,
                        placeholder = { Text(s.supportingCastHint, color = colors.TextTertiary) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = outlinedColors(colors)
                    )
                }
            }

            // ──── 规则 ────
            SectionLabel(Icons.Filled.Gavel, s.worldRules)
            GlassCard(hazeState, advancedMaterial, colors) {
                if (previewOnly) {
                    ReadonlyField(worldRules, s.worldRulesHint, colors)
                } else {
                    OutlinedTextField(
                        value = worldRules,
                        onValueChange = { worldRules = it },
                        enabled = canEditPersona,
                        placeholder = { Text(s.worldRulesHint, color = colors.TextTertiary) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = outlinedColors(colors)
                    )
                }
            }

            // ──── 用户形象 ────
            SectionLabel(Icons.Filled.Person, s.userPersona)
            GlassCard(hazeState, advancedMaterial, colors) {
                if (previewOnly) {
                    ReadonlyField(userPersona, s.userPersonaHint, colors)
                } else {
                    OutlinedTextField(
                        value = userPersona,
                        onValueChange = { userPersona = it },
                        enabled = canEditPersona,
                        placeholder = { Text(s.userPersonaHint, color = colors.TextTertiary) },
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

            // ──── 对话模式（模拟设置；微信聊天/动作演绎/剧情补足三选一，取代 1.0.28 前的「剧情模式」开关） ────
            SectionLabel(Icons.Filled.AutoStories, s.dialogueMode)
            GlassCard(hazeState, advancedMaterial, colors) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text(s.dialogueMode, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.TextPrimary)
                        Text(s.dialogueModeDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                    }
                    // 三档竖排选项：整组先在一层**凹槽**里（新拟态的 recessed），每项再从槽底凸出来 ——
                    // 「凹槽装凸块」是新拟态标准的层次写法，比原来那层灰内衬更能说明「这一组是一件事」。
                    // 凹槽本身不吃底色（卡面 = 背景色），流动炫彩照样穿过去。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .frostedCard(
                                hazeState, colors, advancedMaterial, RoundedCornerShape(12.dp),
                                recessed = true,
                                fallback = colors.Background.copy(alpha = 0.55f)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple(DialogueMode.WECHAT, s.dialogueModeWechat, s.dialogueModeWechatDesc),
                            Triple(DialogueMode.ACTION, s.dialogueModeAction, s.dialogueModeActionDesc),
                            Triple(DialogueMode.PLOT, s.dialogueModePlot, s.dialogueModePlotDesc)
                        ).forEach { (mode, title, desc) ->
                            val sel = dialogueMode == mode
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .frostedCard(
                                        hazeState, colors, advancedMaterial, RoundedCornerShape(10.dp),
                                        fallback = if (sel) colors.Primary.copy(alpha = 0.22f) else colors.SurfaceVariant,
                                        face = if (sel) colors.Primary.copy(alpha = 0.22f) else null
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            sel -> colors.Primary.copy(alpha = 0.55f)
                                            // 高级材质下未选中项靠阴影定形、不要描边；非高级材质维持原来那条细线
                                            advancedMaterial -> Color.Transparent
                                            else -> colors.Divider.copy(alpha = 0.7f)
                                        },
                                        RoundedCornerShape(10.dp)
                                    )
                                    // 锁死了就点不动：点上去弹一句「为什么不能改」，而不是默默没反应。
                                    // 创建时每换一档弹一次提醒（1.0.53）：选之前先说清「选了就不能改」；
                                    // 只在真的换档时弹（重复点已选中的那一档不弹），编辑态不弹（那时已经建好了）。
                                    .clickable {
                                        when {
                                            modeLocked -> showModeLocked = true
                                            dialogueMode == mode -> Unit
                                            !isEditing -> {
                                                dialogueMode = mode
                                                showModePickWarn = true
                                            }
                                            else -> dialogueMode = mode
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (sel) colors.Primary else colors.TextPrimary
                                    )
                                    Text(
                                        desc,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 17.sp),
                                        color = colors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    // 锁定后的说明：把「为什么不能改」讲在明面上，省得用户以为开关坏了
                    if (modeLocked) {
                        Text(
                            s.dialogueModeLockedHint,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 17.sp),
                            color = colors.TextTertiary
                        )
                    }
                    // 单次回复长度：动作演绎/剧情补足才需要（微信聊天靠逐条回复控制节奏，不设此档；
                    // 还没选档位时也不显示 —— 选了再出现，选择本身才有分量）
                    if (dialogueMode == DialogueMode.ACTION || dialogueMode == DialogueMode.PLOT) {
                        HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .frostedCard(
                                    hazeState, colors, advancedMaterial, RoundedCornerShape(12.dp),
                                    recessed = true,
                                    fallback = colors.Background.copy(alpha = 0.55f)
                                )
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(s.plotLength, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            listOf(s.plotLengthShort, s.plotLengthMid, s.plotLengthLong, s.plotLengthExtraLong)
                                .withIndex().chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { (idx, label) ->
                                            val sel = plotLength == idx
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .frostedCard(
                                                        hazeState, colors, advancedMaterial, RoundedCornerShape(10.dp),
                                                        compact = true,
                                                        fallback = if (sel) colors.Primary.copy(alpha = 0.22f) else colors.SurfaceVariant,
                                                        face = if (sel) colors.Primary.copy(alpha = 0.22f) else null
                                                    )
                                                    .border(
                                                        1.dp,
                                                        when {
                                                            sel -> colors.Primary.copy(alpha = 0.55f)
                                                            advancedMaterial -> Color.Transparent
                                                            else -> colors.Divider.copy(alpha = 0.7f)
                                                        },
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable { plotLength = idx }
                                                    .padding(vertical = 9.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(label, fontSize = 13.sp, color = if (sel) colors.Primary else colors.TextPrimary)
                                            }
                                        }
                                        if (row.size < 2) Spacer(Modifier.weight(1f))
                                    }
                                }
                        }
                    }
                    // 例句引导：展示该档该发什么样的提示词、AI 会怎么回（两档叙事模式的差别看例子最直观）。
                    // 还没选档位时整块不出现（1.0.53）—— 空着三行选项的时候摆一份「某一档」的样例，
                    // 反倒像是在暗示「默认就是它」。
                    if (dialogueMode != null) {
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
                        if (dialogueMode == DialogueMode.WECHAT) {
                            // 微信模式两侧都是**一条条短消息**，所以照聊天气泡的样子排：
                            // 用户在右、AI 在左，一一交替 —— 光写两段长文本反而看不出「逐条」是什么样。
                            val users = s.wechatExampleUser
                            val ais = s.wechatExampleAi
                            val turns = maxOf(users.size, ais.size)
                            repeat(turns) { i ->
                                users.getOrNull(i)?.let { ChatExampleBubble(it, fromUser = true, colors = colors) }
                                ais.getOrNull(i)?.let { ChatExampleBubble(it, fromUser = false, colors = colors) }
                            }
                        } else {
                            val userText = if (dialogueMode == DialogueMode.PLOT) s.plotExampleUser else s.actionExampleUser
                            val aiText = if (dialogueMode == DialogueMode.PLOT) s.plotExampleAi else s.actionExampleAi
                            ChatExampleBubble(userText, fromUser = true, colors = colors, withLabel = true)
                            ChatExampleBubble(aiText, fromUser = false, colors = colors, withLabel = true)
                        }
                    }
                    }
                }
            }

            // ──── 增强检索（原「高质量检索回复」；创建/编辑都可随时切换）────
            SectionLabel(Icons.Filled.TravelExplore, s.highQualityMemory)
            GlassCard(hazeState, advancedMaterial, colors) {
                // **必须包一层 Column**：GlassCard 内部是 Box，直接并列两个子节点会**叠在一起**
                // —— 表现就是说明文字盖在标题上、标题跑到小字底下。旁边几张卡都包了，就这张漏了。
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.highQualityMemory, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                        Switch(
                            checked = highQualityMemory,
                            onCheckedChange = { on ->
                                // 打开要过一道确认（代价见下面那个浮层）；关闭是白嫖，直接关
                                if (on) pendingHeavy = HeavyToggle.RETRIEVAL
                                else {
                                    highQualityMemory = false
                                    deepThinking = false
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.OnPrimary,
                                checkedTrackColor = colors.Primary,
                                uncheckedThumbColor = colors.TextTertiary,
                                uncheckedTrackColor = colors.SurfaceVariant
                            )
                        )
                    }
                    // 说明放整行下方：跟在标题右侧会被开关挤到第二行，这样才排得下一行
                    Text(
                        s.highQualityMemoryDesc,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 17.sp),
                        color = colors.TextSecondary
                    )

                    // 第二级开关：多轮编排。**只在增强检索开着的时候才出现** ——
                    // 它的第一步（第一趟推演 + 二次检索）本来就是建立在增强检索那套
                    // 全文记忆之上的，母开关关着，它就只是个白花一次调用的空壳。
                    androidx.compose.animation.AnimatedVisibility(visible = highQualityMemory) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 12.dp),
                                color = colors.Divider.copy(alpha = 0.4f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.deepThinking, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                Switch(
                                    checked = deepThinking,
                                    // 同样要先确认：它比增强检索更贵（一趟推演 + 二次检索）
                                    onCheckedChange = { on -> if (on) pendingHeavy = HeavyToggle.DEEP_THINKING else deepThinking = false },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.OnPrimary,
                                        checkedTrackColor = colors.Primary,
                                        uncheckedThumbColor = colors.TextTertiary,
                                        uncheckedTrackColor = colors.SurfaceVariant
                                    )
                                )
                            }
                            Text(
                                s.deepThinkingDesc,
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 17.sp),
                                color = colors.TextSecondary
                            )
                        }
                    }
                }
            }

            // ──── AI创造力（模拟设置；聊天模拟与剧情模式共用）────
            SectionLabel(Icons.Filled.AutoAwesome, s.aiCreativity)
            GlassCard(hazeState, advancedMaterial, colors) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.aiCreativity, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                    Text(
                        s.aiCreativityDesc,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                        color = colors.TextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            String.format(java.util.Locale.US, "%.1f", aiCreativity),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.Primary
                        )
                        Spacer(Modifier.width(14.dp))
                        // 自绘连续滑块（与 MBTI 维度滑块同款轨道）。不用 Material3 Slider：
                        // 它要 0.1 精度就得 steps=89，而 steps>0 时默认轨道会画出 89 个刻度点 → 「一串小圆点」
                        CreativitySlider(
                            value = aiCreativity,
                            label = s.aiCreativity,
                            modifier = Modifier.weight(1f)
                        ) { v -> aiCreativity = v }
                    }
                }
            }

            // ──── 主动智能（创建和编辑都可改） ────
            //
            // ★ 只在「微信聊天」档出现。主动智能是「角色过一会儿自己发消息找你」，
            // 这只有在微信式的一来一回里才成立 —— 小说/动作演绎是用户在推进剧情，
            // 角色跳出来主动说话只会打断节奏，所以另外两档直接不给这个开关、运行时也不排闹钟。
            if (dialogueMode == DialogueMode.WECHAT) {
                SectionLabel(Icons.Filled.NotificationsActive, s.proactive)
                GlassCard(hazeState, advancedMaterial, colors) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.proactive, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                                    BetaTag(s.betaTag, colors)
                                }
                                Text(s.proactiveDesc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextSecondary)
                            }
                            Switch(
                                checked = proactiveEnabled,
                                onCheckedChange = { proactiveEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.OnPrimary,
                                    checkedTrackColor = colors.Primary,
                                    uncheckedThumbColor = colors.TextTertiary,
                                    uncheckedTrackColor = colors.SurfaceVariant
                                )
                            )
                        }
                        // 没拿到「闹钟和提醒」权限也能用，只是到点会有几分钟浮动 —— 提示而非阻断
                        if (proactiveEnabled && !exactAlarmOk) {
                            Text(
                                s.proactiveExactHint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .clickable {
                                        ProactiveScheduler.exactAlarmSettingsIntent(setupContext)?.let { intent ->
                                            runCatching { setupContext.startActivity(intent) }
                                        }
                                    },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                                color = colors.Primary
                            )
                        }
                    }
                }
            }
            // 回复缓冲 / 作息模拟 / 主动智能 都只属于「微信聊天」档；
            // 切到动作演绎/剧情补足后既不在 UI 出现，运行时也会跳过。
            if (dialogueMode == DialogueMode.WECHAT) {
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
                                if (langModelId.isBlank()) s.followGlobal else viewModel.languageModels.value.find { it.id == langModelId }?.displayName ?: "",
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
                                if (visionModelId.isBlank()) s.followGlobal else viewModel.visionModels.value.find { it.id == visionModelId }?.displayName ?: "",
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
                        if (modeMissing) { showModeMissing = true; return@Button }
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

        // ===== 九个弹层：窗口内的底部磨砂玻璃面板（SheetPanel）=====
        // 全部放在根 Box 的最后 —— 要盖住所有东西（含上面的悬浮标题栏）。
        // 背景**不压暗**：拦截层自己就是一层 haze，把背后的设定页糊掉，视觉上「失焦」而不「变黑」。
        // 原来这九个都是 AlertDialog：独立窗口看不到本页自己画的内容，糊不了背景，只能压暗
        // （就是「悬浮卡片背景加暗」那种效果）。

        // 放弃修改确认（创建模式返回时）。放弃是**不可逆**的，主按钮保持红色
        SheetPanel(
            visible = showDiscardDialog,
            onDismiss = { showDiscardDialog = false },
            title = s.unsavedBackTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.discard,
            confirmDanger = true,
            onConfirm = { showDiscardDialog = false; onBack() }
        ) {
            Text(s.unsavedBackMessage, color = colors.TextSecondary)
        }

        // 确定启动编辑
        SheetPanel(
            visible = showEditConfirm,
            onDismiss = { showEditConfirm = false },
            title = s.editConfirmTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.confirm,
            onConfirm = { showEditConfirm = false; isEditMode = true }
        ) {
            Text(s.editConfirmMessage, color = colors.TextSecondary)
        }

        // 确认保存
        SheetPanel(
            visible = showSaveConfirm,
            onDismiss = { showSaveConfirm = false },
            title = s.saveConfirmTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.confirm,
            onConfirm = {
                showSaveConfirm = false
                if (canStart && !mbtiIncomplete && !modeMissing) onCreate(buildProfile())
                else if (mbtiIncomplete) showMbtiIncomplete = true
                else if (modeMissing) showModeMissing = true
            }
        ) {
            Text(s.saveConfirmMessage, color = colors.TextSecondary)
        }

        // 更改未保存。放弃更改同样不可逆，主按钮保持红色
        SheetPanel(
            visible = showUnsavedChanges,
            onDismiss = { showUnsavedChanges = false },
            title = s.unsavedChangesTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.discardChanges,
            confirmDanger = true,
            onConfirm = {
                showUnsavedChanges = false
                resetToInitial()
                if (isEditMode) isEditMode = false else onBack()
            }
        ) {
            Text(s.unsavedChangesMessage, color = colors.TextSecondary)
        }

        // 对话模式还没选（1.0.53）：创建时必须先选一档，点了「创建角色」拦一道
        SheetPanel(
            visible = showModeMissing,
            onDismiss = { showModeMissing = false },
            title = s.dialogueModeRequiredTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.confirm,
            onConfirm = { showModeMissing = false }
        ) {
            Text(s.dialogueModeRequiredDesc, color = colors.TextSecondary)
        }

        // 创建时选中某一档（1.0.53）：弹一句「选了就不能改」，点确定收掉，不做任何拦截
        SheetPanel(
            visible = showModePickWarn,
            onDismiss = { showModePickWarn = false },
            title = s.dialogueMode,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.confirm,
            onConfirm = { showModePickWarn = false }
        ) {
            Text(s.dialogueModePickWarn, color = colors.TextSecondary)
        }

        // 对话模式已锁定（1.0.53）：聊起来之后点档位弹这个，说清「改不了 + 要走导出导入」
        SheetPanel(
            visible = showModeLocked,
            onDismiss = { showModeLocked = false },
            title = s.dialogueModeLockedTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.confirm,
            onConfirm = { showModeLocked = false }
        ) {
            Text(s.dialogueModeLockedDesc, color = colors.TextSecondary)
        }

        // MBTI 未完整提示（选了一半维度就保存时拦截）
        SheetPanel(
            visible = showMbtiIncomplete,
            onDismiss = { showMbtiIncomplete = false },
            title = s.mbtiIncompleteTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.confirm,
            onConfirm = { showMbtiIncomplete = false }
        ) {
            Text(s.mbtiIncompleteMessage, color = colors.TextSecondary)
        }

        // 头像三选项（查看 / 编辑 / 更换；查看与编辑只在已有头像时可点）
        SheetPanel(
            visible = showAvatarMenu,
            onDismiss = { showAvatarMenu = false },
            title = s.avatar,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState
        ) {
            if (avatarPath.isNotBlank()) {
                SheetActionRow(s.viewAvatar, colors, onClick = { showAvatarMenu = false; showAvatarPreview = true })
                SheetActionRow(s.editAvatar, colors, onClick = { showAvatarMenu = false; avatarCropSource = Uri.fromFile(File(avatarPath)) })
            }
            SheetActionRow(s.changeAvatar, colors, onClick = { showAvatarMenu = false; avatarPicker.launch("image/*") })
        }

        // MBTI 16 型选择
        MbtiTypeSheet(
            visible = showMbtiPicker,
            current = mbtiType,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            onSelect = { selectMbtiType(it); showMbtiPicker = false },
            onDismiss = { showMbtiPicker = false }
        )

        // 语言模型选择（每角色独立）
        LangModelSheet(
            visible = showLangPicker,
            viewModel = viewModel,
            current = langModelId,
            colors = colors,
            s = s,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            onSelect = { id -> langModelId = id; showLangPicker = false },
            onDismiss = { showLangPicker = false }
        )

        // 识图模型选择（每角色独立）
        VisionModelSheet(
            visible = showVisionPicker,
            viewModel = viewModel,
            current = visionModelId,
            colors = colors,
            s = s,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            onSelect = { id -> visionModelId = id; showVisionPicker = false },
            onDismiss = { showVisionPicker = false }
        )

        // 增强检索 / 深度推演 的「代价确认」（1.0.50）
        //
        // 这两个开关一打开，每轮对话要多跑一两次模型调用（写记忆的原文、检索喂原文、多轮推演），
        // token 和等待时间都是成倍地涨。原来只有开关下面那行小字提一句，等于没说 ——
        // 用户是在**点开的那一瞬间**该知道代价，所以拦一个浮层：红字点明代价，两个选择都摆在台面上。
        //
        // 按钮分配跟「关闭联网搜索」那张保持一致：主按钮站**现状**(取消)，行内那个才是「仍然开启」。
        // 行内那个用链接样式（蓝色斜体下划线）—— 1.0.53 起两个二级风险弹层统一（见 SheetActionRow 的 link）。
        SheetPanel(
            visible = pendingHeavy != null,
            onDismiss = { pendingHeavy = null },
            title = if (pendingHeavy == HeavyToggle.DEEP_THINKING) s.deepThinking else s.highQualityMemory,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.cancel,
            onConfirm = { pendingHeavy = null }
        ) {
            Text(
                buildAnnotatedString {
                    append(s.heavyWarnHead)
                    // 代价那半句上红 —— 整句里最该被看见的就是它
                    withStyle(SpanStyle(color = colors.ErrorRed, fontWeight = FontWeight.SemiBold)) {
                        append(s.heavyWarnRed)
                    }
                    append(s.heavyWarnTail)
                },
                color = colors.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            SheetActionRow(s.stillEnable, colors, link = true) {
                when (pendingHeavy) {
                    HeavyToggle.RETRIEVAL -> highQualityMemory = true
                    HeavyToggle.DEEP_THINKING -> deepThinking = true
                    null -> Unit
                }
                pendingHeavy = null
            }
        }
    }
}

/**
 * 打开前要先讲代价的两个重开关：
 * 增强检索（[CharacterProfile.highQualityMemory]）与深度推演（[CharacterProfile.deepThinking]）。
 */
private enum class HeavyToggle { RETRIEVAL, DEEP_THINKING }

/** AI创造力取值区间与精度：1.0~10.0，落点吸附在十分位（与旧 Slider 的 steps=89 等价） */
private const val CREATIVITY_MIN = 1f
private const val CREATIVITY_MAX = 10f

/** 1.0~10.0 → 0~1 的线性映射 */
private fun creativityToFraction(v: Float): Float =
    ((v - CREATIVITY_MIN) / (CREATIVITY_MAX - CREATIVITY_MIN)).coerceIn(0f, 1f)

/** 0~1 → 1.0~10.0，吸附到 0.1 */
private fun fractionToCreativity(f: Float): Float =
    Math.round((CREATIVITY_MIN + f.coerceIn(0f, 1f) * (CREATIVITY_MAX - CREATIVITY_MIN)) * 10f) / 10f

/**
 * AI创造力滑块：与 MBTI 维度滑块同一套 Canvas 手绘连续轨道（无刻度点、无分段）。
 * 与 MBTI 的差别只在于本滑块没有「中间态」——程度条从最左端延伸，而不是从中心向两侧延伸。
 */
@Composable
private fun CreativitySlider(value: Float, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onChange: (Float) -> Unit) {
    val colors = LocalFreeChatColors.current
    val trackHeight = 4.dp
    Box(
        modifier = modifier
            .height(36.dp)
            // 左右留出 thumb 半径，避免拖到两端时圆点被裁掉一半
            .padding(horizontal = 8.dp)
            // 手绘 Canvas 不会自己产生无障碍节点（pointerInput 也不算）：不补语义的话读屏用户
            // 根本聚焦不到这个滑块，更没法调值。补上范围/当前值 + setProgress 让读屏能上下滑调整。
            .semantics {
                contentDescription = label
                stateDescription = String.format(java.util.Locale.US, "%.1f", value)
                progressBarRangeInfo = ProgressBarRangeInfo(value, CREATIVITY_MIN..CREATIVITY_MAX, 89)
                if (enabled) setProgress { target ->
                    onChange(Math.round(target.coerceIn(CREATIVITY_MIN, CREATIVITY_MAX) * 10f) / 10f)
                    true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { pos -> onChange(fractionToCreativity(pos.x / size.width)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { pos -> onChange(fractionToCreativity(pos.x / size.width)) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onChange(fractionToCreativity(change.position.x / size.width))
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
            // 程度条：从最左端延伸到当前值
            val vx = size.width * creativityToFraction(value)
            if (vx > 0.5f) {
                drawRoundRect(
                    color = if (enabled) colors.Primary else colors.TextTertiary,
                    topLeft = Offset(0f, cy - th / 2f),
                    size = Size(vx, th),
                    cornerRadius = CornerRadius(th / 2f, th / 2f)
                )
            }
            // 滑块 thumb
            drawCircle(color = if (enabled) colors.Primary else colors.TextTertiary, radius = 8.dp.toPx(), center = Offset(vx, cy))
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

/**
 * 「发送示例」里的一条消息。
 *
 * 三种对话模式共用一个气泡：用户在右、AI 在左，一眼就能看出谁在说话 ——
 * 原来那种「你：…… AI：……」纯文本的写法，在微信模式下完全体现不出「逐条短消息」
 * 的节奏（两段长文本排在一起，看着还以为是剧情补足）。
 *
 * [withLabel] 是给叙事模式留的：那两档内容太长、没有气泡的对话感，
 * 只靠左右对齐看不出是两段不同的东西，所以额外挂一个「你 / AI」的前缀。
 */
/**
 * 预设项（性别 / 人物关系 / 性格）的**显示名**与**存储值**是分开的。
 *
 * 存储值必须是中文常量：它们写进 CharacterProfile 后会被同步到服务器、也要跟旧档案比对
 * （选中态判定是 `gender == g` 这种字符串相等）。一旦把存的值跟着界面语言走，老用户档案里的
 * 「男」对不上英文包里的 "Male"，选项就全变未选中了。所以只翻译**显示**的那一层。
 */
private fun presetDisplay(key: String, keys: List<String>, labels: List<String>): String {
    val i = keys.indexOf(key)
    return if (i >= 0) labels.getOrElse(i) { key } else key
}

@Composable
private fun ChatExampleBubble(
    text: String,
    fromUser: Boolean,
    colors: com.freechat.ui.theme.FreeChatColors,
    withLabel: Boolean = false
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (fromUser) colors.Primary.copy(alpha = 0.22f) else colors.Background.copy(alpha = 0.75f))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            if (withLabel) {
                Text(
                    if (fromUser) s.chatExampleYou else s.chatExampleAi,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                    color = if (fromUser) colors.Primary else colors.TextTertiary
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.TextSecondary
            )
        }
    }
}

/** 预览态只读文本：无输入框、灰色，直观告诉用户「只能看不能改」；空值显示灰色占位 */@Composable
private fun ReadonlyField(value: String, placeholder: String, colors: com.freechat.ui.theme.FreeChatColors) {
    Text(
        value.ifBlank { placeholder },
        style = MaterialTheme.typography.bodyMedium,
        color = if (value.isBlank()) colors.TextTertiary else colors.TextSecondary
    )
}

/**
 * 基本信息卡里的属性标签（性别 / 年龄 / 人物关系 / 参考原型）。
 * 固定宽度左对齐，字号比属性值小一号、颜色暗一档 —— 一眼能分清「哪边是标签、哪边是内容」。
 */
@Composable
private fun FieldLabel(text: String, colors: com.freechat.ui.theme.FreeChatColors) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = colors.TextTertiary,
        modifier = Modifier.width(72.dp)
    )
}

/**
 * MBTI 16 类型选择 —— 窗口内的底部磨砂玻璃弹层。
 * 原来是 AlertDialog：独立窗口看不到本页自己画的内容，糊不到背景，只能压暗。
 * 类型码改用 [SheetOption]，选中态由它统一给（主题色淡底 + 主题色文字 + 对勾）。
 */
@Composable
private fun BoxScope.MbtiTypeSheet(
    visible: Boolean,
    current: String,
    colors: com.freechat.ui.theme.FreeChatColors,
    isDark: Boolean,
    advancedMaterial: Boolean,
    hazeState: HazeState,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    val types = listOf(
        listOf("INTJ", "INTP", "ENTJ", "ENTP"),
        listOf("INFJ", "INFP", "ENFJ", "ENFP"),
        listOf("ISTJ", "ISFJ", "ESTJ", "ESFJ"),
        listOf("ISTP", "ISFP", "ESTP", "ESFP")
    )
    SheetPanel(
        visible = visible,
        onDismiss = onDismiss,
        title = s.mbtiType,
        colors = colors,
        isDark = isDark,
        advancedMaterial = advancedMaterial,
        hazeState = hazeState
    ) {
        // 16 个类型码排**四列**，跟原来那个 4×4 网格一样一屏看全 ——
        // 全宽 SheetOption 会把它摊成 16 行、要滚动才看得见靠后的类型，那是倒退。
        // 选中态与全 App 的选项表统一（1.0.50）：主题色实心底 + OnPrimary 文字（见 selectedFill）。
        types.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { t ->
                    val sel = t == current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) colors.selectedFill else Color.Transparent)
                            .clickable { onSelect(t) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            t,
                            fontFamily = LocalMonoFontFamily.current,
                            color = if (sel) colors.selectedText else colors.TextPrimary,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/**
 * 语言模型选择（每角色独立）—— 窗口内的底部磨砂玻璃弹层。
 * 原来是 AlertDialog：独立窗口糊不到背景，只能压暗；现在换成真模糊、不压暗的 SheetPanel，
 * 选项行交给 [SheetOption]（选中态、对勾都由它统一给）。
 */
@Composable
private fun BoxScope.LangModelSheet(
    visible: Boolean,
    viewModel: ChatViewModel,
    current: String,
    colors: com.freechat.ui.theme.FreeChatColors,
    s: com.freechat.i18n.AppStrings,
    isDark: Boolean,
    advancedMaterial: Boolean,
    hazeState: HazeState,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    SheetPanel(
        visible = visible,
        onDismiss = onDismiss,
        title = s.languageModel,
        colors = colors,
        isDark = isDark,
        advancedMaterial = advancedMaterial,
        hazeState = hazeState
    ) {
        SheetOption(
            selected = current.isBlank(),
            title = if (current.isBlank()) s.followGlobalDefault else s.followGlobal,
            colors = colors,
            onClick = { onSelect("") }
        )
        viewModel.languageModels.value.forEach { m ->
            SheetOption(
                selected = m.id == current,
                title = m.displayName,
                subtitle = com.freechat.i18n.localizedModelDesc(m, s),
                colors = colors,
                // 模型名是"代码感"的字符串，沿用原来的等宽字体
                monoTitle = true,
                onClick = { onSelect(m.id) }
            )
        }
    }
}

/**
 * 识图模型选择（每角色独立）—— 窗口内的底部磨砂玻璃弹层（同 [LangModelSheet]，
 * 原来也是 AlertDialog：独立窗口看不到本页内容，糊不到背景，只能压暗）。
 */
@Composable
private fun BoxScope.VisionModelSheet(
    visible: Boolean,
    viewModel: ChatViewModel,
    current: String,
    colors: com.freechat.ui.theme.FreeChatColors,
    s: com.freechat.i18n.AppStrings,
    isDark: Boolean,
    advancedMaterial: Boolean,
    hazeState: HazeState,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    SheetPanel(
        visible = visible,
        onDismiss = onDismiss,
        title = s.visionModel,
        colors = colors,
        isDark = isDark,
        advancedMaterial = advancedMaterial,
        hazeState = hazeState
    ) {
        SheetOption(
            selected = current.isBlank(),
            title = if (current.isBlank()) s.followGlobalDefault else s.followGlobal,
            colors = colors,
            onClick = { onSelect("") }
        )
        viewModel.visionModels.value.forEach { m ->
            SheetOption(
                selected = m.id == current,
                title = m.displayName,
                subtitle = com.freechat.i18n.localizedModelDesc(m, s),
                colors = colors,
                monoTitle = true,
                onClick = { onSelect(m.id) }
            )
        }
    }
}

/**
 * 功能名后面的测试阶段角标（「主动智能 Beta」）。
 *
 * 比正文小一号、压着主题色，底下只垫一层很淡的色 —— 它是标注，不是能点的东西，
 * 所以不给点击态、也不给阴影（本页到处都是浮雕卡片，角标一旦立体起来就会被当成按钮）。
 */
@Composable
private fun BetaTag(text: String, colors: FreeChatColors) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(shape)
            .background(colors.Primary.copy(alpha = 0.13f))
            .border(0.5.dp, colors.Primary.copy(alpha = 0.32f), shape)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.4.sp),
            fontWeight = FontWeight.Medium,
            color = colors.Primary
        )
    }
}

