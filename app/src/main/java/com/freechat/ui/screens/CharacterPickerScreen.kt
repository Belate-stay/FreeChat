package com.freechat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.freechat.data.CharacterLibraryStore
import com.freechat.i18n.*
import com.freechat.model.CharacterCard
import com.freechat.model.CharacterPersona
import com.freechat.ui.adaptive.FloatingWindowCloseHandler
import com.freechat.ui.adaptive.pageActionWidth
import com.freechat.ui.animation.MotionButton
import com.freechat.ui.animation.MotionTextButton
import com.freechat.ui.animation.LocalPageActive
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.rememberPersistentLazyListState
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.components.SheetPanel
import com.freechat.ui.theme.FreeChatTheme
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import dev.chrisbanes.haze.rememberHazeState

/** Null rejects the entire change; folder operations never partially select their contents. */
internal fun toggleCharacterSelection(selectedIds: Set<String>, candidateIds: Collection<String>, selectionLimit: Int): Set<String>? {
    val candidates = candidateIds.toSet()
    if (candidates.isEmpty()) return selectedIds.toSet()
    if (selectedIds.containsAll(candidates)) return selectedIds - candidates
    val next = selectedIds + candidates
    return next.takeIf { it.size <= selectionLimit }
}

@Composable
fun CharacterPickerScreen(
    isDark: Boolean,
    initialSelectedIds: Set<String> = emptySet(),
    selectionLimit: Int = 4,
    onConfirm: (List<CharacterCard>) -> Unit,
    onBack: () -> Unit,
    excludedCardIds: Set<String> = emptySet(),
    forStory: Boolean = false,
    selectedFolderId: String? = null,
    onSelectFolder: (String?) -> Unit = {},
    scrollMemory: MutableMap<String, Int> = mutableMapOf(),
) {
    val library by CharacterLibraryStore.state.collectAsState()
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    val advanced = LocalAdvancedMaterial.current
    val motion = LocalMotionEnabled.current
    val hazeState = rememberHazeState()
    val availableCards = library.cards.filterNot { it.id in excludedCardIds }
    val availableIds = availableCards.map { it.id }.toSet()
    var selectedIds by remember(initialSelectedIds) { mutableStateOf(initialSelectedIds.intersect(availableIds)) }
    // 工单④：文件夹层级提升到 MainActivity（角色库 selectedFolderId 同款先例）——
    // 确认进群设置/故事设置再返回，还在原来的文件夹里不跳回根页；重进大页由 navigateTo 清。
    val folderId = selectedFolderId
    var showLimitMessage by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var pendingWindowClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    val folder = library.folders.firstOrNull { it.id == folderId }
    val selectedCards = availableCards.filter { it.id in selectedIds }.distinctBy { it.id }
    val cards = availableCards.filter { card ->
        if (folder != null) card.folderId == folder.id
        else card.folderId == null || library.folders.none { it.id == card.folderId }
    }.sortedByDescending { it.updatedAt }
    val changed = selectedIds != initialSelectedIds.intersect(availableIds)
    LaunchedEffect(availableIds) { selectedIds = selectedIds.intersect(availableIds) }

    fun toggle(ids: Collection<String>) {
        val next = toggleCharacterSelection(selectedIds, ids, selectionLimit)
        if (next == null) showLimitMessage = true else selectedIds = next
    }
    fun requestBack() {
        showLimitMessage = false
        when {
            folder != null -> onSelectFolder(null)
            changed -> showDiscard = true
            else -> onBack()
        }
    }
    FloatingWindowCloseHandler { dismiss ->
        showLimitMessage = false
        if (changed) {
            pendingWindowClose = dismiss
            showDiscard = true
        } else dismiss()
    }

    Box(Modifier.fillMaxSize()) {
        CharacterLibraryPage(folder?.name ?: if (forStory) s.chooseStoryCharacters else s.chooseGroupCharacters, { requestBack() }, hazeState,
            listState = rememberPersistentLazyListState("picker", scrollMemory),
            bottomBar = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.characterSelectionCount(selectedIds.size, selectionLimit), color = colors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium)
                    MotionButton(onClick = { onConfirm(selectedCards) },
                        enabled = selectedIds.size in 1..selectionLimit && selectedCards.size == selectedIds.size,
                        modifier = Modifier.pageActionWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.Primary, contentColor = colors.OnPrimary)) {
                        Text(s.confirm, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }) {
            if (availableCards.isNotEmpty()) item {
                Text(if (forStory) s.multiCharacterStoryDesc else s.multiCharacterGroupDesc,
                    color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            item(key = "selected_characters") {
                AnimatedVisibility(visible = selectedCards.isNotEmpty(),
                    enter = if (motion) FreeChatAnimation.expandEnter() else androidx.compose.animation.EnterTransition.None,
                    exit = if (motion) FreeChatAnimation.expandExit() else androidx.compose.animation.ExitTransition.None) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(selectedCards, key = { it.id }) { card ->
                            Column(Modifier.animateItem(
                                fadeInSpec = if (motion) FreeChatAnimation.overlayFadeIn else snap(),
                                placementSpec = if (motion) FreeChatAnimation.controlSpec() else snap(),
                                fadeOutSpec = if (motion) FreeChatAnimation.overlayFadeOut else snap())
                                .width(76.dp).clip(RoundedCornerShape(12.dp)).clickable { toggle(listOf(card.id)) }.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LibraryCharacterAvatar(card.persona)
                                Text(card.persona.name, color = colors.TextPrimary, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (folder == null && availableCards.isNotEmpty()) {
                items(library.folders, key = { "folder_${it.id}" }) { entry ->
                    val folderIds = availableCards.filter { it.folderId == entry.id }.map { it.id }.toSet()
                    CharacterFolderRow(entry.name, folderIds.size, hazeState, onOpen = { onSelectFolder(entry.id) }) {
                        CharacterFolderSelectionAction(folderIds, selectedIds) { toggle(folderIds) }
                    }
                }
            }
            if (availableCards.isNotEmpty()) item {
                val folderIds = cards.map { it.id }.toSet()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(folder?.name ?: s.unclassifiedCharacters, Modifier.weight(1f),
                        color = colors.TextSecondary, style = MaterialTheme.typography.labelLarge)
                    CharacterFolderSelectionAction(folderIds, selectedIds) { toggle(folderIds) }
                }
            }
            if (availableCards.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(s.addCharactersFirst, color = colors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else if (cards.isEmpty()) {
                item { Text(s.characterFolderEmpty,
                    color = colors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            items(cards, key = { "card_${it.id}" }) { card ->
                CharacterLibraryCardRow(card, hazeState, onClick = { toggle(listOf(card.id)) },
                    selected = card.id in selectedIds, highlighted = card.id in selectedIds)
            }
        }
        BackHandler(enabled = LocalPageActive.current, onBack = { requestBack() })
        SheetPanel(showLimitMessage, { showLimitMessage = false }, s.chooseGroupCharacters,
            colors, isDark, advanced, hazeState, confirmLabel = s.confirm, onConfirm = { showLimitMessage = false }) {
            Text(s.characterSelectionLimit(selectionLimit), color = colors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        SheetPanel(showDiscard, { showDiscard = false; pendingWindowClose = null }, s.unsavedChangesTitle,
            colors, isDark, advanced, hazeState, confirmLabel = s.discardChanges, cancelLabel = s.cancel,
            confirmDanger = true, onConfirm = {
                showDiscard = false
                val close = pendingWindowClose
                pendingWindowClose = null
                if (close != null) close() else onBack()
            }) { Text(s.unsavedChangesMessage, color = colors.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
/** 2026-10-28 工单：整文件夹选择=方框勾选框（小方框、行内右对齐、点击即勾选），不再用文字按钮。 */
private fun CharacterFolderSelectionAction(ids: Set<String>, selectedIds: Set<String>, onClick: () -> Unit) {
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    val allSelected = ids.isNotEmpty() && selectedIds.containsAll(ids)
    Box(Modifier.padding(end = 6.dp)
        .toggleable(value = allSelected, enabled = ids.isNotEmpty(), role = Role.Checkbox, onValueChange = { onClick() })
        .padding(10.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
            .then(if (allSelected) Modifier.background(colors.Primary)
                else Modifier.border(1.5.dp, colors.TextTertiary, RoundedCornerShape(6.dp))),
            contentAlignment = Alignment.Center) {
            if (allSelected) Icon(Icons.Filled.Check, s.selectCharacterFolder,
                tint = colors.OnPrimary, modifier = Modifier.size(15.dp))
        }
    }
}

@Preview(name = "Selected characters", widthDp = 390)
@Composable
private fun CharacterPickerSelectionPreview() {
    FreeChatTheme {
        val colors = LocalFreeChatColors.current
        val hazeState = rememberHazeState()
        Column(Modifier.background(colors.Background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(LocalStrings.current.characterSelectionCount(2, 4), color = colors.TextPrimary,
                style = MaterialTheme.typography.titleMedium)
            CharacterLibraryCardRow(CharacterCard("preview", CharacterPersona(name = "林间", personalityText = "安静、坦率")), hazeState, {},
                selected = true, highlighted = true)
        }
    }
}
