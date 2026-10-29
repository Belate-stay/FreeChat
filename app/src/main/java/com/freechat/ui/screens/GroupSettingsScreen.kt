package com.freechat.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freechat.data.ModelSelectionResolver
import com.freechat.i18n.*
import com.freechat.model.GroupChatPolicy
import com.freechat.model.GroupChatProfile
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.ui.adaptive.FloatingWindowBackButton
import com.freechat.ui.adaptive.FloatingWindowCloseHandler
import com.freechat.ui.adaptive.PaneExpandButton
import com.freechat.ui.adaptive.floatingHeaderInset
import com.freechat.ui.adaptive.paneImePadding
import com.freechat.ui.adaptive.pageActionWidth
import com.freechat.ui.adaptive.tabletReadingGutter
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.MotionButton
import com.freechat.ui.animation.LocalPageActive
import com.freechat.ui.animation.jellyOverscroll
import com.freechat.ui.components.*
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.FreeChatInputShape
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.theme.pageBackground
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private enum class GroupSettingsPage { OVERVIEW, REMARK, ANNOUNCEMENT, NICKNAME, REPLIES }

/** All mutations are explicit, target-bound submissions; member editors never receive source cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupSettingsScreen(
    conversationId: String,
    title: String,
    group: GroupChatProfile,
    isBusy: Boolean,
    isDark: Boolean,
    onSaveGroup: (String, GroupChatProfile) -> Unit,
    onRename: (String, String) -> Unit,
    onEditMember: (String) -> Unit,
    onChooseMembers: () -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    languageModels: List<ModelInfo> = emptyList(),
    visionModels: List<ModelInfo> = emptyList(),
    globalLanguageModel: ModelInfo? = null,
    globalVisionModel: ModelInfo? = null,
    deepThinkingEnabled: Boolean = false,
    onSetDeepThinking: (Boolean) -> Unit = {},
    webSearchEnabled: Boolean = false,
    onSetWebSearch: (Boolean) -> Unit = {},
    creating: Boolean = false,
    onStart: () -> Unit = {},
    scrollMemory: MutableMap<String, Int> = mutableMapOf(),
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val readingGutter = if (creating) tabletReadingGutter() else 0.dp
    var page by rememberSaveable(conversationId) { mutableStateOf(GroupSettingsPage.OVERVIEW) }
    var textDraft by rememberSaveable(conversationId) { mutableStateOf("") }
    var originalText by rememberSaveable(conversationId) { mutableStateOf("") }
    var replyDraft by remember(conversationId) { mutableStateOf(group) }
    var originalReply by remember(conversationId) { mutableStateOf(group) }
    var nativeDeepDraft by rememberSaveable(conversationId) { mutableStateOf(deepThinkingEnabled) }
    var searchDraft by rememberSaveable(conversationId) { mutableStateOf(webSearchEnabled) }
    var originalNativeDeep by rememberSaveable(conversationId) { mutableStateOf(deepThinkingEnabled) }
    var originalSearch by rememberSaveable(conversationId) { mutableStateOf(webSearchEnabled) }
    var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showAvatarOptions by remember { mutableStateOf(false) }
    var cropSource by remember { mutableStateOf<Uri?>(null) }
    var modelType by remember { mutableStateOf<ModelType?>(null) }
    var memberEditMode by rememberSaveable(conversationId) { mutableStateOf(false) }
    var removeMemberId by remember(conversationId) { mutableStateOf<String?>(null) }
    var showInitialName by rememberSaveable(conversationId) { mutableStateOf(creating && title.isBlank()) }
    var initialNameDraft by rememberSaveable(conversationId) { mutableStateOf("") }
    val removingMember = group.members.firstOrNull { it.id == removeMemberId }
    LaunchedEffect(isBusy) {
        if (isBusy) { memberEditMode = false; removeMemberId = null }
    }
    val explicitLanguage = ModelSelectionResolver.find(group.languageModelId, ModelType.LANGUAGE, languageModels)
    val explicitVision = ModelSelectionResolver.find(group.visionModelId, ModelType.VISION, visionModels)
    val selectedLanguage = explicitLanguage ?: globalLanguageModel
    val selectedVision = explicitVision ?: globalVisionModel
    val dirty = when (page) {
        GroupSettingsPage.OVERVIEW -> false
        GroupSettingsPage.REPLIES -> replyDraft != originalReply || nativeDeepDraft != originalNativeDeep || searchDraft != originalSearch
        else -> textDraft != originalText
    }
    fun requestLeave(action: () -> Unit) {
        modelType = null
        showAvatarOptions = false
        removeMemberId = null
        if (dirty) pendingLeave = action else action()
    }
    fun goBack() {
        if (memberEditMode) memberEditMode = false
        else requestLeave { if (page == GroupSettingsPage.OVERVIEW) onBack() else page = GroupSettingsPage.OVERVIEW }
    }
    fun showReplyLock() = Toast.makeText(context, s.groupSettingLockedDuringReply, Toast.LENGTH_SHORT).show()
    fun whenIdle(action: () -> Unit) {
        if (isBusy) showReplyLock() else action()
    }
    fun openEditor(target: GroupSettingsPage, value: String = "") {
        if (isBusy && target != GroupSettingsPage.REMARK) {
            showReplyLock()
            return
        }
        textDraft = value
        originalText = value
        if (target == GroupSettingsPage.REPLIES) {
            replyDraft = group
            originalReply = group
            nativeDeepDraft = deepThinkingEnabled
            originalNativeDeep = deepThinkingEnabled
            searchDraft = webSearchEnabled
            originalSearch = webSearchEnabled
        }
        page = target
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !isBusy) cropSource = uri
    }
    BackHandler(enabled = LocalPageActive.current) { goBack() }
    FloatingWindowCloseHandler { close -> requestLeave(close) }

    if (cropSource != null) AvatarCropDialog(
        sourceUri = cropSource!!,
        onDone = { path, _ ->
            if (!isBusy) onSaveGroup(conversationId, group.copy(avatarPath = path, avatarHash = ""))
            cropSource = null
        },
        onDismiss = { cropSource = null },
        onError = { Toast.makeText(context, s.avatarCropFailed, Toast.LENGTH_SHORT).show() },
    )

    CompositionLocalProvider(LocalSettingsHazeState provides hazeState) {
    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        TopBarBackdropSource(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBar))
        AnimatedContent(page, transitionSpec = { FreeChatAnimation.contentReplacement() }, label = "groupSettingsPage") { displayedPage ->
            val scroll = com.freechat.ui.animation.rememberPersistentScrollState("groupSettings", scrollMemory)
            Column(Modifier.fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(hazeState).hazeBackground(colors.Background) else Modifier)
                .paneImePadding().jellyOverscroll().verticalScroll(scroll)
                .padding(top = HazeSpec.newPageContentPaddingDp(statusBar), bottom = 32.dp)
                .padding(horizontal = 16.dp + readingGutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (displayedPage == GroupSettingsPage.OVERVIEW) {
                    SettingsRow {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(s.groupMembers, style = MaterialTheme.typography.labelLarge, color = colors.TextSecondary)
                            BoxWithConstraints(Modifier.fillMaxWidth().animateContentSize(FreeChatAnimation.controlSpec())) {
                                val columns = if (maxWidth >= 312.dp) 5 else 4
                                val count = 1 + group.members.size + if (memberEditMode) 1 else 0
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    (0 until count).toList().chunked(columns).forEach { row ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            row.forEach { index ->
                                                val member = group.members.getOrNull(index - 1)
                                                key(member?.id ?: if (index == 0) "user" else "add") {
                                                    val enterEditing = { whenIdle { memberEditMode = true } }
                                                    when {
                                                        index == 0 -> GroupMemberCell(group.userNickname, Modifier.weight(1f),
                                                            onClick = { if (!memberEditMode) openEditor(GroupSettingsPage.NICKNAME, group.userNickname) },
                                                            onLongClick = enterEditing) { GroupUserAvatar(Modifier.size(48.dp)) }
                                                        member != null -> GroupMemberCell(member.persona.name, Modifier.weight(1f),
                                                            editing = memberEditMode, onRemove = { whenIdle { removeMemberId = member.id } },
                                                            onClick = { if (!memberEditMode) whenIdle { onEditMember(member.id) } },
                                                            onLongClick = enterEditing) {
                                                            GroupParticipantAvatar(member.persona.avatarPath, member.persona.name, Modifier.size(48.dp))
                                                        }
                                                        else -> GroupMemberCell(s.groupAddMembers, Modifier.weight(1f),
                                                            enabled = !isBusy && group.members.size < GroupChatPolicy.MAX_MEMBERS,
                                                            onClick = { whenIdle { onChooseMembers() } }, onLongClick = {}) {
                                                            Icon(Icons.Filled.Add, null, tint = if (group.members.size >= GroupChatPolicy.MAX_MEMBERS)
                                                                colors.TextTertiary else colors.Primary,
                                                                modifier = Modifier.size(48.dp).padding(10.dp))
                                                        }
                                                    }
                                                }
                                            }
                                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                            Text(if (memberEditMode) s.groupMemberEditingHint else s.groupMemberEditHint,
                                style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
                        }
                    }
                    SettingsRow {
                        Row(Modifier.fillMaxWidth().clickable(enabled = !isBusy) { showAvatarOptions = true }
                            .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            GroupAvatar(group = group, modifier = Modifier.size(64.dp), contentDescription = s.groupAvatar)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.groupAvatar, color = colors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                                Text(s.groupMemberCount(group.members.size + 1), color = colors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = colors.TextTertiary)
                        }
                    }
                    if (isBusy) Text(s.groupWaitToSave, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    SettingsRow {
                        Column {
                            SettingsItem(Icons.Filled.Edit, s.groupRemark, title.ifBlank { s.groupRemarkRequiredHint }, colors,
                                onClick = { openEditor(GroupSettingsPage.REMARK, title) })
                            SettingsDivider(colors)
                            SettingsItem(Icons.Filled.Campaign, s.groupAnnouncement,
                                group.announcement.trim().take(120).replace('\n', ' ').ifBlank { s.groupNoAnnouncement }, colors,
                                onClick = { openEditor(GroupSettingsPage.ANNOUNCEMENT, group.announcement) })
                            SettingsDivider(colors)
                            SettingsItem(Icons.Filled.Person, s.groupUserNickname, group.userNickname, colors,
                                onClick = { openEditor(GroupSettingsPage.NICKNAME, group.userNickname) })
                        }
                    }
                    SettingsRow {
                        Column {
                            SettingsItem(Icons.Filled.SmartToy, s.languageModel,
                                if (group.languageModelId.isBlank()) listOfNotNull(s.followGlobal, selectedLanguage?.displayName).joinToString(" · ")
                                else explicitLanguage?.displayName ?: group.languageModelId, colors,
                                onClick = { whenIdle { modelType = ModelType.LANGUAGE } })
                            SettingsDivider(colors)
                            SettingsItem(Icons.Filled.ImageSearch, s.visionModel,
                                if (group.visionModelId.isBlank()) listOfNotNull(s.followGlobal, selectedVision?.displayName).joinToString(" · ")
                                else explicitVision?.displayName ?: group.visionModelId, colors,
                                onClick = { whenIdle { modelType = ModelType.VISION } })
                            SettingsDivider(colors)
                            SettingsItem(Icons.Filled.Tune, s.groupReplySettings, s.groupModelsHint, colors,
                                onClick = { openEditor(GroupSettingsPage.REPLIES) })
                        }
                    }
                    SettingsRow {
                        Row(Modifier.fillMaxWidth().semantics { disabled() }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = colors.TextTertiary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(s.groupProactivePreview, style = MaterialTheme.typography.bodyLarge, color = colors.TextTertiary)
                                    Surface(color = colors.TextTertiary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                        Text(s.groupInternalTest, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary)
                                    }
                                }
                                Text(s.groupProactivePreviewDesc, style = MaterialTheme.typography.bodySmall, color = colors.TextTertiary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = false, enabled = false, onCheckedChange = {})
                        }
                    }
                    SettingsRow {
                        Column {
                            if (!creating) {
                                SettingsItem(Icons.Filled.Search, s.groupSearch, null, colors, onClick = onSearch)
                                SettingsDivider(colors)
                            }
                            SettingsItem(Icons.Filled.Badge, s.groupShowMemberNames, s.groupShowMemberNamesHint, colors, trailing = {
                                Switch(checked = group.showMemberNames,
                                    onCheckedChange = { onSaveGroup(conversationId, group.copy(showMemberNames = it)) })
                            })
                        }
                    }
                    if (creating) {
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            MotionButton(onClick = onStart, enabled = GroupChatPolicy.canStart(title, group),
                                modifier = Modifier.pageActionWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(25.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.Primary, contentColor = colors.OnPrimary)) {
                                Text(s.startCharacterChat, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                } else if (displayedPage == GroupSettingsPage.REPLIES) {
                    Text(s.groupModelsHint, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
                    SettingsRow {
                        Column {
                            GroupSwitch(s.replyBufferEnable, s.replyBufferDesc, replyDraft.replyBufferEnabled, !isBusy) {
                                replyDraft = replyDraft.copy(replyBufferEnabled = it)
                            }
                            AnimatedVisibility(replyDraft.replyBufferEnabled, enter = FreeChatAnimation.expandEnter(), exit = FreeChatAnimation.expandExit()) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text("${replyDraft.replyBufferSeconds} s", color = colors.TextPrimary)
                                    Slider(value = replyDraft.replyBufferSeconds.toFloat().coerceIn(1f, 6f),
                                        onValueChange = { replyDraft = replyDraft.copy(replyBufferSeconds = it.roundToInt()) },
                                        valueRange = 1f..6f, steps = 4, enabled = !isBusy)
                                }
                            }
                            SettingsDivider(colors)
                            GroupSwitch(s.timePerception, s.timePerceptionDesc, replyDraft.timePerception, !isBusy) {
                                replyDraft = replyDraft.copy(timePerception = it)
                            }
                        }
                    }
                    SettingsRow {
                        Column {
                            GroupSwitch(s.highQualityMemory, s.highQualityMemoryDesc, replyDraft.highQualityMemory, !isBusy) {
                                replyDraft = replyDraft.copy(highQualityMemory = it, deepThinking = replyDraft.deepThinking && it)
                            }
                            AnimatedVisibility(replyDraft.highQualityMemory, enter = FreeChatAnimation.expandEnter(), exit = FreeChatAnimation.expandExit()) {
                                Column {
                                    SettingsDivider(colors)
                                    GroupSwitch(s.deepThinking, s.deepThinkingDesc, replyDraft.deepThinking, !isBusy) {
                                        replyDraft = replyDraft.copy(deepThinking = it)
                                    }
                                }
                            }
                        }
                    }
                    SettingsRow {
                        Column(Modifier.padding(16.dp)) {
                            Text(s.aiCreativity, color = colors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                            Text(s.aiCreativityDesc, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Text(String.format(java.util.Locale.US, "%.1f", replyDraft.aiCreativity), color = colors.Primary)
                            Slider(value = replyDraft.aiCreativity.coerceIn(0f, 10f), enabled = !isBusy,
                                onValueChange = { replyDraft = replyDraft.copy(aiCreativity = it) }, valueRange = 0f..10f)
                        }
                    }
                    SettingsRow {
                        Column {
                            GroupSwitch(s.deepThinkingMode, s.deepThinkingModeDesc, nativeDeepDraft,
                                !isBusy && selectedLanguage?.supportsDeepThinking == true) { nativeDeepDraft = it }
                            SettingsDivider(colors)
                            GroupSwitch(s.webSearch, null, searchDraft, !isBusy) { searchDraft = it }
                        }
                    }
                    if (isBusy) Text(s.groupWaitToSave, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(when (displayedPage) {
                        GroupSettingsPage.REMARK -> if (creating) s.groupRemarkRequiredHint else s.groupRemarkHint
                        GroupSettingsPage.ANNOUNCEMENT -> s.groupAnnouncementHint
                        else -> s.groupNicknameHint
                    }, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
                    OutlinedTextField(value = textDraft, onValueChange = { textDraft = it }, modifier = Modifier.fillMaxWidth(),
                        shape = FreeChatInputShape,
                        label = { Text(when (displayedPage) {
                            GroupSettingsPage.REMARK -> s.groupRemark
                            GroupSettingsPage.ANNOUNCEMENT -> s.groupAnnouncement
                            else -> s.groupUserNickname
                        }) }, minLines = if (displayedPage == GroupSettingsPage.ANNOUNCEMENT) 6 else 1)
                    if (displayedPage == GroupSettingsPage.ANNOUNCEMENT && group.announcementAt > 0) {
                        Text(s.groupUpdatedAt(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(group.announcementAt))),
                            style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
                    }
                    if (isBusy && displayedPage != GroupSettingsPage.REMARK)
                        Text(s.groupWaitToSave, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        TopBarBackdrop(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBar))
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(top = 8.8.dp).floatingHeaderInset(),
            verticalAlignment = Alignment.CenterVertically) {
            FloatingWindowBackButton(onClick = { goBack() }, root = page == GroupSettingsPage.OVERVIEW) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back, tint = colors.TextPrimary)
            }
            Text(when (page) {
                GroupSettingsPage.OVERVIEW -> s.groupSettings
                GroupSettingsPage.REMARK -> s.groupRemark
                GroupSettingsPage.ANNOUNCEMENT -> s.groupAnnouncement
                GroupSettingsPage.NICKNAME -> s.groupUserNickname
                GroupSettingsPage.REPLIES -> s.groupReplySettings
            }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                color = colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PaneExpandButton()
            if (page == GroupSettingsPage.OVERVIEW && memberEditMode) {
                HeaderTextButton(onClick = { memberEditMode = false }) {
                    Text(s.groupFinishManaging, color = colors.Primary)
                }
            }
            if (page != GroupSettingsPage.OVERVIEW) {
                val canSave = dirty && (page == GroupSettingsPage.REMARK || !isBusy) &&
                    (page == GroupSettingsPage.ANNOUNCEMENT || page == GroupSettingsPage.REPLIES || textDraft.isNotBlank())
                HeaderTextButton(enabled = canSave, onClick = {
                    if (page == GroupSettingsPage.REMARK) onRename(conversationId, textDraft.trim())
                    else if (!isBusy) {
                        when (page) {
                            GroupSettingsPage.ANNOUNCEMENT -> onSaveGroup(conversationId,
                                group.copy(announcement = textDraft.trim(), announcementAt = System.currentTimeMillis()))
                            GroupSettingsPage.NICKNAME -> onSaveGroup(conversationId, group.copy(userNickname = textDraft.trim()))
                            GroupSettingsPage.REPLIES -> {
                                onSaveGroup(conversationId, group.copy(replyBufferEnabled = replyDraft.replyBufferEnabled,
                                    replyBufferSeconds = replyDraft.replyBufferSeconds, timePerception = replyDraft.timePerception,
                                    highQualityMemory = replyDraft.highQualityMemory, deepThinking = replyDraft.deepThinking && replyDraft.highQualityMemory,
                                    aiCreativity = replyDraft.aiCreativity))
                                onSetDeepThinking(nativeDeepDraft)
                                onSetWebSearch(searchDraft)
                            }
                            else -> Unit
                        }
                    }
                    page = GroupSettingsPage.OVERVIEW
                }) {
                    Text(if (page == GroupSettingsPage.ANNOUNCEMENT) s.groupPublish else s.save,
                        color = if (canSave) colors.Primary else colors.TextTertiary)
                }
            }
        }

        SheetPanel(creating && showInitialName, { showInitialName = false }, s.groupRemark, colors, isDark,
            advancedMaterial, hazeState, confirmLabel = s.confirm, cancelLabel = s.cancel,
            confirmEnabled = initialNameDraft.isNotBlank(), onConfirm = {
                if (initialNameDraft.isNotBlank()) {
                    showInitialName = false
                    onRename(conversationId, initialNameDraft.trim())
                }
            }) {
            OutlinedTextField(initialNameDraft, { initialNameDraft = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, placeholder = { Text(s.groupRemarkRequiredHint, color = colors.TextTertiary,
                    style = MaterialTheme.typography.bodySmall) }, shape = FreeChatInputShape,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary))
        }
        SheetPanel(showAvatarOptions, { showAvatarOptions = false }, s.groupAvatar, colors, isDark, advancedMaterial, hazeState) {
            SheetOption(false, s.changeAvatar, colors, onClick = { showAvatarOptions = false; if (!isBusy) imagePicker.launch("image/*") })
            if (group.avatarPath.isNotBlank()) SheetOption(false, s.groupUseCompositeAvatar, colors, onClick = {
                if (!isBusy) onSaveGroup(conversationId, group.copy(avatarPath = "", avatarHash = ""))
                showAvatarOptions = false
            })
        }
        val activeModelType = rememberSheetContent(modelType)
        val models = if (activeModelType == ModelType.VISION) visionModels else languageModels
        val currentModelId = if (activeModelType == ModelType.VISION) group.visionModelId else group.languageModelId
        SheetPanel(modelType != null, { modelType = null },
            if (activeModelType == ModelType.VISION) s.visionModel else s.languageModel, colors, isDark, advancedMaterial, hazeState) {
            fun select(id: String) {
                if (modelType == null) return
                if (isBusy) {
                    showReplyLock()
                    return
                }
                onSaveGroup(conversationId, if (activeModelType == ModelType.VISION)
                    group.copy(visionModelId = id) else group.copy(languageModelId = id))
                modelType = null
            }
            SheetOption(currentModelId.isBlank(), s.followGlobalDefault, colors, onClick = { select("") })
            models.forEach { model ->
                SheetOption(ModelSelectionResolver.selectionKey(model) == currentModelId, model.displayName, colors,
                    onClick = { select(ModelSelectionResolver.selectionKey(model)) }, monoTitle = true)
            }
            Text(s.groupModelsHint, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary,
                modifier = Modifier.padding(12.dp))
        }
        val displayedRemovingMember = rememberSheetContent(removingMember)
        val displayedRemovalAtLimit = rememberSheetContent(
            (group.members.size <= 1).takeIf { removingMember != null }) == true
        SheetPanel(removingMember != null, { removeMemberId = null }, displayedRemovingMember?.persona?.name.orEmpty(), colors, isDark, advancedMaterial, hazeState,
            confirmLabel = s.groupRemoveMembers, cancelLabel = s.cancel,
            confirmEnabled = !isBusy && removingMember != null && group.members.size > 1,
            onConfirm = {
                val remaining = group.members.filterNot { it.id == removeMemberId }
                if (!isBusy && remaining.size in 1..GroupChatPolicy.MAX_MEMBERS)
                    onSaveGroup(conversationId, group.copy(members = remaining))
                removeMemberId = null
            }) {
            Text(s.groupRemoveMemberConfirm, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (displayedRemovalAtLimit) Text(s.groupMembersLimit, color = colors.TextSecondary,
                style = MaterialTheme.typography.bodySmall)
        }
        SheetPanel(pendingLeave != null, { pendingLeave = null }, s.unsavedBackTitle, colors, isDark, advancedMaterial, hazeState,
            confirmLabel = s.discard, cancelLabel = s.cancel, onConfirm = {
                val action = pendingLeave
                pendingLeave = null
                action?.invoke()
            }) { Text(s.unsavedBackMessage, color = colors.TextSecondary) }
    }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GroupMemberCell(name: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    editing: Boolean = false, onRemove: (() -> Unit)? = null,
    onClick: () -> Unit, onLongClick: () -> Unit, avatar: @Composable () -> Unit) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val rotation = if (editing && onRemove != null) {
        rememberInfiniteTransition(label = "memberEditing").animateFloat(-1.8f, 1.8f,
            infiniteRepeatable(FreeChatAnimation.memberEditWiggle, RepeatMode.Reverse), label = "avatarWiggle")
    } else null
    Column(modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(enabled = enabled,
        role = androidx.compose.ui.semantics.Role.Button, onClick = onClick, onLongClick = onLongClick)
        .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(48.dp)) {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation?.value ?: 0f }) { avatar() }
            androidx.compose.animation.AnimatedVisibility(editing && onRemove != null,
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
                enter = FreeChatAnimation.dialogEnter(), exit = FreeChatAnimation.dialogExit()) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(colors.Surface)
                    .border(0.6.dp, colors.Divider, CircleShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onRemove?.invoke() },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, s.groupRemoveMemberNamed(name),
                        tint = colors.TextSecondary, modifier = Modifier.size(12.dp))
                }
            }
        }
        Text(name, style = MaterialTheme.typography.labelMedium, color = if (enabled) colors.TextPrimary else colors.TextTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, minLines = 2,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GroupSwitch(title: String, subtitle: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalFreeChatColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onCheckedChange = onChange, enabled = enabled)
    }
}
