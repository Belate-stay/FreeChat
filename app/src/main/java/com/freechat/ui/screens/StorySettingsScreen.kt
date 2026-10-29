package com.freechat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freechat.data.ModelSelectionResolver
import com.freechat.i18n.*
import com.freechat.model.*
import com.freechat.ui.adaptive.FloatingWindowCloseHandler
import com.freechat.ui.adaptive.pageActionWidth
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.LocalPageActive
import com.freechat.ui.animation.MotionButton
import com.freechat.ui.components.*
import com.freechat.ui.theme.*
import dev.chrisbanes.haze.rememberHazeState

/** The entire draft is held by the route so editing a cast member cannot discard world edits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorySettingsScreen(
    title: String, draft: StoryProfile, dirty: Boolean, creating: Boolean, isBusy: Boolean, isDark: Boolean,
    onTitle: (String) -> Unit, onDraft: (StoryProfile) -> Unit, onEditMember: (String) -> Unit,
    onSave: () -> Unit, onBack: () -> Unit,
    languageModels: List<ModelInfo>, visualModels: List<ModelInfo>,
    globalLanguage: ModelInfo, globalVisual: ModelInfo, globalSearch: Boolean,
    scrollMemory: MutableMap<String, Int> = mutableMapOf(),
) {
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    val haze = rememberHazeState()
    var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    var modelType by remember { mutableStateOf<ModelType?>(null) }
    var pendingHeavy by remember { mutableStateOf<String?>(null) }
    var showWorldCompatibility by remember { mutableStateOf(false) }
    fun leave(action: () -> Unit) {
        showWorldCompatibility = false
        modelType = null
        pendingHeavy = null
        if (dirty) pendingLeave = action else action()
    }
    BackHandler(LocalPageActive.current) { leave(onBack) }
    FloatingWindowCloseHandler { leave(it) }
    val language = ModelSelectionResolver.find(draft.languageModelId, ModelType.LANGUAGE, languageModels) ?: globalLanguage
    val visual = ModelSelectionResolver.find(draft.visualModelId, ModelType.VISUAL, visualModels) ?: globalVisual
    CompositionLocalProvider(LocalSettingsHazeState provides haze) {
    Box(Modifier.fillMaxSize()) {
        CharacterLibraryPage(s.storySettings, { leave(onBack) }, haze,
            listState = com.freechat.ui.animation.rememberPersistentLazyListState("storySetup", scrollMemory)) {
            item { Text(s.storyNarrationHint, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary) }
            item { StoryField(s.storyName, title, s.storyNameHint, !isBusy, onTitle) }
            item {
                SettingsRow { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.storyCast, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        draft.members.forEach { member ->
                            Column(Modifier.width(76.dp).clickable(enabled = !isBusy) { onEditMember(member.id) }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LibraryCharacterAvatar(member.persona, 48.dp)
                                Text(member.persona.name, color = colors.TextPrimary, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Text(s.storyCastHint, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
                } }
            }
            item { StoryField(s.storyBackground, draft.background, s.storyBackgroundHint, !isBusy,
                { onDraft(draft.copy(background = it)) }, lines = 3) }
            item { StoryField(s.storyRules, draft.worldRules, s.storyRulesHint, !isBusy,
                { onDraft(draft.copy(worldRules = it)) }, lines = 3) }
            item { StoryField(s.userPersona, draft.userPersona, s.storyUserPersonaHint, !isBusy,
                { onDraft(draft.copy(userPersona = it)) }, lines = 3) }
            item { StoryField(s.storyOpening, draft.openingSituation, s.storyOpeningHint, !isBusy,
                { onDraft(draft.copy(openingSituation = it)) }, lines = 3) }
            item { SettingsRow { Column {
                SettingsItem(Icons.Filled.SmartToy, s.languageModel,
                    (if (draft.languageModelId.isBlank()) s.followGlobal + " · " else "") + language.displayName, colors,
                    onClick = { if (!isBusy) modelType = ModelType.LANGUAGE })
                SettingsDivider(colors)
                SettingsItem(Icons.Filled.Image, s.imageGenModel,
                    (if (draft.visualModelId.isBlank()) s.followGlobal + " · " else "") + visual.displayName, colors,
                    onClick = { if (!isBusy) modelType = ModelType.VISUAL })
                SettingsDivider(colors)
                StorySwitch(s.enhancedSceneContinuity, draft.enhancedSceneContinuity, !isBusy,
                    description = s.enhancedSceneContinuityDesc, beta = true) {
                    if (it) pendingHeavy = "scene" else onDraft(draft.copy(enhancedSceneContinuity = false))
                }
            } } }
            item { PlotLengthCard(draft.plotLength, { if (!isBusy) onDraft(draft.copy(plotLength = it)) },
                haze, LocalAdvancedMaterial.current, colors) }
            item { SettingsRow { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.aiCreativity, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                Text(s.aiCreativityDesc, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
                Text(String.format(java.util.Locale.US, "%.1f", draft.aiCreativity), color = colors.Primary)
                Slider(value = draft.aiCreativity.coerceIn(0f, 10f), onValueChange = { onDraft(draft.copy(aiCreativity = it)) },
                    enabled = !isBusy, valueRange = 0f..10f)
            } } }
            item { SettingsRow { Column {
                StorySwitch(s.highQualityMemory, draft.highQualityMemory, !isBusy, description = s.highQualityMemoryDesc) {
                    if (it) pendingHeavy = "memory" else onDraft(draft.copy(highQualityMemory = false, deepThinking = false))
                }
                AnimatedVisibility(draft.highQualityMemory, enter = FreeChatAnimation.expandEnter(), exit = FreeChatAnimation.expandExit()) {
                    Column { SettingsDivider(colors); StorySwitch(s.deepThinking, draft.deepThinking, !isBusy, description = s.deepThinkingDesc) {
                        if (it) pendingHeavy = "prep" else onDraft(draft.copy(deepThinking = false)) } }
                }
            } } }
            item { SettingsRow { Column {
                StorySwitch(s.deepThinkingMode, draft.deepThinkingMode, !isBusy && language.supportsDeepThinking) {
                    onDraft(draft.copy(deepThinkingMode = it)) }
                SettingsDivider(colors)
                StorySwitch(s.webSearch, draft.enableWebSearch ?: globalSearch, !isBusy) { onDraft(draft.copy(enableWebSearch = it)) }
            } } }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp).navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isBusy) Text(characterSaveBlockedMessage(s.localeCode), color = colors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall)
                    MotionButton(onClick = {
                        if (!isBusy && title.isNotBlank() && StoryPolicy.isValid(draft) && (creating || dirty)) {
                            if (creating) showWorldCompatibility = true else onSave()
                        }
                    }, enabled = !isBusy && title.isNotBlank() && StoryPolicy.isValid(draft) && (creating || dirty),
                        modifier = Modifier.pageActionWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.Primary, contentColor = colors.OnPrimary)) {
                        Text(if (creating) s.startCharacterChat else s.saveSettings, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        val type = rememberSheetContent(modelType)
        val list = if (type == ModelType.VISUAL) visualModels else languageModels
        val currentId = if (type == ModelType.VISUAL) draft.visualModelId else draft.languageModelId
        SheetPanel(modelType != null, { modelType = null }, if (type == ModelType.VISUAL) s.imageGenModel else s.languageModel,
            colors, isDark, LocalAdvancedMaterial.current, haze) {
            fun select(id: String) {
                if (modelType == null) return
                if (!isBusy) onDraft(if (type == ModelType.VISUAL) draft.copy(visualModelId = id) else draft.copy(languageModelId = id))
                modelType = null
            }
            SheetOption(currentId.isBlank(), s.followGlobal, colors, onClick = { select("") })
            list.forEach { model -> SheetOption(ModelSelectionResolver.selectionKey(model) == currentId, model.displayName, colors,
                onClick = { select(ModelSelectionResolver.selectionKey(model)) }) }
        }
        val displayedHeavy = rememberSheetContent(pendingHeavy)
        SheetPanel(pendingHeavy != null, { pendingHeavy = null }, when (displayedHeavy) {
            "scene" -> s.enhancedSceneContinuity; "prep" -> s.deepThinking; else -> s.highQualityMemory },
            colors, isDark, LocalAdvancedMaterial.current, haze, confirmLabel = s.confirm, cancelLabel = s.cancel,
            onConfirm = {
                if (!isBusy && pendingHeavy != null) onDraft(when (pendingHeavy) {
                    "scene" -> draft.copy(enhancedSceneContinuity = true)
                    "prep" -> draft.copy(deepThinking = true)
                    else -> draft.copy(highQualityMemory = true)
                })
                pendingHeavy = null
            }) { Text(if (displayedHeavy == "scene") s.sceneContinuityConfirm else s.heavyWarnHead + s.heavyWarnRed + s.heavyWarnTail,
                color = colors.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
        SheetPanel(showWorldCompatibility, { showWorldCompatibility = false }, s.storyWorldCompatibilityTitle,
            colors, isDark, LocalAdvancedMaterial.current, haze,
            confirmLabel = s.confirm, cancelLabel = s.cancel,
            confirmEnabled = !isBusy && title.isNotBlank() && StoryPolicy.isValid(draft), onConfirm = {
                if (showWorldCompatibility && creating && !isBusy && title.isNotBlank() && StoryPolicy.isValid(draft)) {
                    showWorldCompatibility = false
                    onSave()
                }
            }) { Text(s.storyWorldCompatibilityMessage, color = colors.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
        SheetPanel(pendingLeave != null, { pendingLeave = null }, s.unsavedChangesTitle,
            colors, isDark, LocalAdvancedMaterial.current, haze,
            confirmLabel = s.discardChanges, cancelLabel = s.cancel, confirmDanger = true, onConfirm = {
                val action = pendingLeave; pendingLeave = null; action?.invoke()
            }) { Text(s.unsavedChangesMessage, color = colors.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
    }
    }
}

@Composable
private fun StoryField(label: String, value: String, hint: String, enabled: Boolean, onChange: (String) -> Unit, lines: Int = 1) {
    val colors = LocalFreeChatColors.current
    SettingsRow { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = colors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), enabled = enabled,
            placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.TextTertiary.copy(alpha = 0.7f)) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
            minLines = lines, maxLines = if (lines == 1) 2 else 8, shape = FreeChatInputShape)
    } }
}

@Composable
private fun StorySwitch(label: String, checked: Boolean, enabled: Boolean, description: String? = null,
    beta: Boolean = false, onChange: (Boolean) -> Unit) {
    val colors = LocalFreeChatColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f, fill = false), color = colors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                if (beta) BetaTag(LocalStrings.current.betaTag, colors)
            }
            if (description != null) Text(description, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, onChange, enabled = enabled)
    }
}
