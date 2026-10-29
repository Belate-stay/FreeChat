package com.freechat.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.freechat.data.CharacterLibraryStore
import com.freechat.i18n.*
import com.freechat.model.CharacterCard
import com.freechat.model.CharacterPersona
import com.freechat.model.Conversation
import com.freechat.ui.adaptive.FloatingWindowBackButton
import com.freechat.ui.adaptive.FloatingWindowCloseHandler
import com.freechat.ui.adaptive.PaneExpandButton
import com.freechat.ui.adaptive.floatingHeaderInset
import com.freechat.ui.adaptive.tabletReadingGutter
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.LocalPageActive
import com.freechat.ui.animation.PageMotion
import com.freechat.ui.animation.jellyOverscroll
import com.freechat.ui.animation.rememberPersistentLazyListState
import com.freechat.ui.components.HeaderIconButton
import com.freechat.ui.components.SheetActionRow
import com.freechat.ui.components.SheetOption
import com.freechat.ui.components.SheetPanel
import com.freechat.ui.components.rememberSheetContent
import com.freechat.ui.components.TopBarBackdrop
import com.freechat.ui.components.TopBarBackdropSource
import com.freechat.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.io.File

/** Cards have no conversation identity; only the host creates or edits conversations. */
@Composable
fun CharacterLibraryScreen(
    isDark: Boolean,
    conversations: List<Conversation> = emptyList(),
    onEditCard: (cardId: String) -> Unit,
    onStartChat: (CharacterCard) -> Unit,
    onBack: () -> Unit,
    selectedFolderId: String? = null,
    onSelectFolder: (String?) -> Unit = {},
    scrollMemory: MutableMap<String, Int> = mutableMapOf(),
) {
    val library by CharacterLibraryStore.state.collectAsState()
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    val advanced = LocalAdvancedMaterial.current
    val context = LocalContext.current
    val hazeState = rememberHazeState()
    val folder = library.folders.firstOrNull { it.id == selectedFolderId }
    val activeFolderId = folder?.id
    LaunchedEffect(selectedFolderId, folder?.id) {
        if (selectedFolderId != null && folder == null) onSelectFolder(null)
    }
    var showAdd by remember { mutableStateOf(false) }
    var showExisting by remember { mutableStateOf(false) }
    var folderMenuId by remember { mutableStateOf<String?>(null) }
    var showFolderEditor by remember { mutableStateOf(false) }
    var editedFolderId by remember { mutableStateOf<String?>(null) }
    var folderName by remember { mutableStateOf("") }
    var deleteFolderId by remember { mutableStateOf<String?>(null) }
    var previewId by remember { mutableStateOf<String?>(null) }
    var moveId by remember { mutableStateOf<String?>(null) }
    var deleteCardId by remember { mutableStateOf<String?>(null) }
    var selecting by remember(activeFolderId) { mutableStateOf(false) }
    var selectedIds by remember(activeFolderId) { mutableStateOf(emptySet<String>()) }
    var showBatchMove by remember(activeFolderId) { mutableStateOf(false) }
    var showBatchDelete by remember(activeFolderId) { mutableStateOf(false) }
    val cardIds = library.cards.filter { card ->
        if (activeFolderId != null) card.folderId == activeFolderId
        else card.folderId == null || library.folders.none { it.id == card.folderId }
    }.map { it.id }.toSet()
    val selectedCardIds = selectedIds.intersect(cardIds)
    val batchSelectionCount = rememberSheetContent(selectedCardIds.size.takeIf {
        (showBatchMove || showBatchDelete) && selectedCardIds.isNotEmpty()
    }) ?: 0
    val folderSlidePx = with(LocalDensity.current) { 64.dp.toPx() }
    val previewCard = library.cards.firstOrNull { it.id == previewId }
    val movingCard = library.cards.firstOrNull { it.id == moveId }
    val menuFolder = library.folders.firstOrNull { it.id == folderMenuId }
    val previewContent = rememberSheetContent(previewCard)
    val movingContent = rememberSheetContent(movingCard)
    val menuFolderContent = rememberSheetContent(menuFolder)

    fun finishSelection() {
        selecting = false
        selectedIds = emptySet()
        showBatchMove = false
        showBatchDelete = false
    }
    fun toggleSelection(id: String) {
        val next = if (id in selectedIds) selectedIds - id else selectedIds + id
        if (next.isEmpty()) finishSelection() else selectedIds = next
    }
    LaunchedEffect(activeFolderId, cardIds) {
        selectedIds = selectedIds.intersect(cardIds)
        if (selectedIds.isEmpty()) finishSelection()
    }
    val requestBack: () -> Unit = {
        when {
            selecting -> finishSelection()
            folder != null -> onSelectFolder(null)
            else -> onBack()
        }
    }
    FloatingWindowCloseHandler { close -> if (selecting) finishSelection() else close() }
    fun editFolder(id: String?, name: String) {
        editedFolderId = id
        folderName = name
        folderMenuId = null
        showFolderEditor = true
    }

    // 工单④：列表滚动记忆——进编辑/预览再返回，列表回到点进去的那一行（重进大页由 navigateTo 清）。
    val listState = rememberPersistentLazyListState("library", scrollMemory)

    Box(Modifier.fillMaxSize()) {
        PageMotion(activeFolderId, distancePx = folderSlidePx, forward = { _, target -> target != null },
            label = "characterLibraryFolder") { displayedFolderId ->
            val folder = library.folders.firstOrNull { it.id == displayedFolderId }
            val cards = library.cards.filter { card ->
                if (folder != null) card.folderId == folder.id
                else card.folderId == null || library.folders.none { it.id == card.folderId }
            }.sortedByDescending { it.updatedAt }
                CharacterLibraryPage(if (selecting) s.selectedCount(selectedCardIds.size)
                    else folder?.name ?: s.myCharacters, requestBack, hazeState,
                    listState = listState,
                    selectionMode = selecting,
                    actions = {
                        if (selecting) {
                            HeaderIconButton(onClick = { showBatchMove = true },
                                enabled = selectedCardIds.isNotEmpty() && library.folders.isNotEmpty()) {
                                Icon(Icons.Filled.DriveFileMove, s.moveCharacterCard, tint = colors.Primary)
                            }
                            HeaderIconButton(onClick = { showBatchDelete = true }, enabled = selectedCardIds.isNotEmpty()) {
                                Icon(Icons.Filled.Delete, s.delete, tint = colors.ErrorRed)
                            }
                        } else HeaderIconButton(onClick = { showAdd = true }) {
                            Icon(Icons.Filled.Add, s.libraryAdd, tint = colors.TextPrimary)
                        }
                    }) {
                    item { Text(s.characterPersonaOnlyNote, color = colors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp)) }
                    if (folder == null) {
                        if (library.folders.isNotEmpty()) item {
                            LibrarySectionHeading(s.characterFolders, library.folders.size.toString())
                        }
                        items(library.folders, key = { "folder_${it.id}" }) { entry ->
                            CharacterFolderRow(entry.name, library.cards.count { it.folderId == entry.id }, hazeState,
                                onOpen = { finishSelection(); onSelectFolder(entry.id) }) {
                                HeaderIconButton(onClick = { folderMenuId = entry.id }) {
                                    Icon(Icons.Filled.MoreHoriz, s.moreActions, tint = colors.TextSecondary)
                                }
                            }
                        }
                        item {
                            Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                                if (library.folders.isNotEmpty()) {
                                    HorizontalDivider(color = colors.Divider)
                                    Spacer(Modifier.height(24.dp))
                                }
                                LibrarySectionHeading(s.unclassifiedCharacters, s.characterCount(cards.size))
                            }
                        }
                    }
                    if (cards.isEmpty()) {
                        item { Text(when {
                            folder != null -> s.characterFolderEmpty
                            library.cards.isEmpty() -> s.characterLibraryEmpty
                            else -> s.unclassifiedCharactersEmpty
                        }, color = colors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 24.dp)) }
                    }
                    items(cards, key = { "card_${it.id}" }) { card ->
                        CharacterLibraryCardRow(card, hazeState, onClick = {
                            if (selecting) toggleSelection(card.id)
                            else previewId = card.id
                        }, onLongClick = {
                            if (selecting) toggleSelection(card.id)
                            else {
                                selecting = true
                                selectedIds = setOf(card.id)
                            }
                        }, selected = if (selecting) card.id in selectedCardIds else null) {
                            AnimatedVisibility(visible = selecting,
                                enter = FreeChatAnimation.expandEnter(), exit = FreeChatAnimation.expandExit()) {
                                val isSelected = card.id in selectedCardIds
                                val fill by animateColorAsState(
                                    if (isSelected) colors.Primary else Color.Transparent,
                                    FreeChatAnimation.colorTween, label = "characterCheckFill")
                                val stroke by animateColorAsState(
                                    if (isSelected) colors.Primary else colors.TextTertiary.copy(alpha = 0.6f),
                                    FreeChatAnimation.colorTween, label = "characterCheckStroke")
                                Box(Modifier.padding(top = 2.dp).size(20.dp).clip(CircleShape).background(fill)
                                    .border(width = 1.5.dp, color = stroke, shape = CircleShape),
                                    contentAlignment = Alignment.Center) {
                                    if (isSelected) Icon(Icons.Filled.Check, null, tint = colors.Background, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                }
        }

        SheetPanel(showBatchMove && selecting, { showBatchMove = false }, s.moveCharacterCard,
            colors, isDark, advanced, hazeState) {
            Text(s.characterBatchSelectionCount(batchSelectionCount), color = colors.TextSecondary,
                style = MaterialTheme.typography.bodySmall)
            if (activeFolderId != null) {
                SheetOption(false, s.unclassifiedCharacters, colors, onClick = {
                    val moved = CharacterLibraryStore.moveCards(selectedCardIds, null, activeFolderId)
                    showBatchMove = false
                    if (moved.isNotEmpty()) finishSelection()
                }, icon = Icons.Filled.FolderOpen)
            }
            library.folders.filter { it.id != activeFolderId }.forEach { entry ->
                SheetOption(false, entry.name, colors, onClick = {
                    val moved = CharacterLibraryStore.moveCards(selectedCardIds, entry.id, activeFolderId)
                    showBatchMove = false
                    if (moved.isNotEmpty()) finishSelection()
                }, icon = Icons.Filled.Folder)
            }
        }
        SheetPanel(showBatchDelete && selecting, { showBatchDelete = false }, s.deleteCharacterCards,
            colors, isDark, advanced, hazeState, confirmLabel = s.delete, cancelLabel = s.cancel,
            confirmEnabled = selectedCardIds.isNotEmpty(), confirmDanger = true, onConfirm = {
                if (showBatchDelete && selectedCardIds.isNotEmpty()) {
                    showBatchDelete = false
                    CharacterLibraryStore.deleteCards(selectedCardIds, activeFolderId)
                    finishSelection()
                }
            }) { Text(s.deleteCharacterCardsMessage(batchSelectionCount), color = colors.TextSecondary) }
        SheetPanel(showAdd, { showAdd = false }, s.myCharacters, colors, isDark, advanced, hazeState) {
            SheetActionRow(s.addExistingCharacter, colors, icon = Icons.Filled.PersonSearch) {
                showAdd = false
                showExisting = true
            }
            if (folder == null) {
                SheetActionRow(s.newCharacterFolder, colors, icon = Icons.Filled.CreateNewFolder) {
                    showAdd = false
                    editFolder(null, "")
                }
            }
        }
        SheetPanel(menuFolder != null, { folderMenuId = null }, menuFolderContent?.name.orEmpty(),
            colors, isDark, advanced, hazeState) {
            SheetActionRow(s.renameCharacterFolder, colors, icon = Icons.Filled.Edit) {
                menuFolder?.let { editFolder(it.id, it.name) }
            }
            SheetActionRow(s.deleteCharacterFolder, colors, danger = true, icon = Icons.Filled.Delete) {
                menuFolder?.let {
                    deleteFolderId = it.id
                    folderMenuId = null
                }
            }
        }
        SheetPanel(showFolderEditor, { showFolderEditor = false },
            if (editedFolderId == null) s.newCharacterFolder else s.renameCharacterFolder,
            colors, isDark, advanced, hazeState, confirmLabel = s.save,
            confirmEnabled = folderName.isNotBlank(), onConfirm = {
                val id = editedFolderId
                if (id == null) CharacterLibraryStore.createFolder(folderName)
                else CharacterLibraryStore.renameFolder(id, folderName)
                showFolderEditor = false
            }) {
            OutlinedTextField(folderName, { folderName = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(s.characterFolderName) }, singleLine = true, shape = FreeChatInputShape)
        }
        SheetPanel(deleteFolderId != null, { deleteFolderId = null }, s.deleteCharacterFolder,
            colors, isDark, advanced, hazeState, confirmLabel = s.delete, cancelLabel = s.cancel,
            confirmDanger = true, onConfirm = {
                deleteFolderId?.let { CharacterLibraryStore.deleteFolder(it) }
                deleteFolderId = null
            }) { Text(s.deleteCharacterFolderMessage, color = colors.TextSecondary) }
        SheetPanel(previewCard != null, { previewId = null }, previewContent?.persona?.name.orEmpty(),
            colors, isDark, advanced, hazeState) {
            previewContent?.let { card ->
                Text(if (card.canStartConversation) s.libraryCreatedSource else s.libraryImportedSource,
                    color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                CharacterPersonaPreview(card.persona)
                if (card.canStartConversation) {
                    SheetActionRow(s.startCharacterChat, colors, icon = Icons.Filled.Chat) {
                        previewCard?.let {
                            previewId = null
                            onStartChat(it)
                        }
                    }
                }
                SheetActionRow(s.edit, colors, icon = Icons.Filled.Edit) {
                    previewCard?.let {
                        previewId = null
                        onEditCard(it.id)
                    }
                }
                SheetActionRow(s.moveCharacterCard, colors, icon = Icons.Filled.DriveFileMove) {
                    previewCard?.let {
                        previewId = null
                        moveId = it.id
                    }
                }
                SheetActionRow(s.deleteCharacterCard, colors, danger = true, icon = Icons.Filled.Delete) {
                    previewCard?.let {
                        previewId = null
                        deleteCardId = it.id
                    }
                }
            }
        }
        SheetPanel(movingCard != null, { moveId = null }, s.moveCharacterCard, colors, isDark, advanced, hazeState) {
            SheetOption(movingContent?.folderId == null, s.unclassifiedCharacters, colors, onClick = {
                movingCard?.let { CharacterLibraryStore.moveCard(it.id, null) }
                moveId = null
            }, icon = Icons.Filled.FolderOpen)
            library.folders.forEach { entry ->
                SheetOption(movingContent?.folderId == entry.id, entry.name, colors, onClick = {
                    movingCard?.let { CharacterLibraryStore.moveCard(it.id, entry.id) }
                    moveId = null
                }, icon = Icons.Filled.Folder)
            }
        }
        SheetPanel(deleteCardId != null, { deleteCardId = null }, s.deleteCharacterCard,
            colors, isDark, advanced, hazeState, confirmLabel = s.delete, cancelLabel = s.cancel,
            confirmDanger = true, onConfirm = {
                deleteCardId?.let { CharacterLibraryStore.deleteCard(it) }
                deleteCardId = null
            }) { Text(s.deleteCharacterCardMessage, color = colors.TextSecondary) }
        SheetPanel(showExisting, { showExisting = false }, s.addExistingCharacter, colors, isDark, advanced, hazeState) {
            Text(s.existingCharacterNote, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            val sources = conversations.flatMap { conversation ->
                val privateCharacter = conversation.characterProfile?.takeIf { conversation.groupChat == null }?.let {
                    listOf(ExistingLibraryCharacter(conversation.id, null, conversation.title, CharacterPersona.from(it)))
                }.orEmpty()
                privateCharacter + conversation.groupChat?.members.orEmpty().map {
                    ExistingLibraryCharacter(conversation.id, it.id, conversation.title, it.persona)
                } + conversation.storyProfile?.members.orEmpty().map {
                    ExistingLibraryCharacter(conversation.id, it.id, conversation.title, it.persona)
                }
            }.filter { it.persona.name.isNotBlank() }
            if (sources.isEmpty()) Text(s.noExistingCharacters, color = colors.TextSecondary)
            sources.forEach { source ->
                val added = library.cards.any { it.sourceConversationId == source.conversationId && it.sourceMemberId == source.memberId }
                SheetOption(added, source.persona.name, colors, subtitle = source.conversationTitle, onClick = {
                    if (!added) {
                        CharacterLibraryStore.add(source.persona, folder?.id,
                            sourceConversationId = source.conversationId, sourceMemberId = source.memberId)
                        Toast.makeText(context, s.characterAddedToLibrary, Toast.LENGTH_SHORT).show()
                    }
                }, icon = if (added) Icons.Filled.Check else Icons.Filled.PersonAdd)
            }
        }
    }
}

private data class ExistingLibraryCharacter(val conversationId: String, val memberId: String?,
    val conversationTitle: String, val persona: CharacterPersona)

@Composable
private fun LibrarySectionHeading(title: String, count: String) {
    val colors = LocalFreeChatColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            color = colors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(count, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
    }
}

@Composable
private fun CharacterPersonaPreview(persona: CharacterPersona) {
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LibraryCharacterAvatar(persona, 56.dp)
        Text(listOf(persona.gender, persona.age, persona.mbtiType).filter { it.isNotBlank() }.joinToString(" · "),
            color = colors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    listOf(s.personality to (persona.personalityPresets + persona.personalityText).filter { it.isNotBlank() }.joinToString(" · "),
        s.memoryPerception to persona.memoryPerception, s.appearance to persona.appearanceText,
        s.relationship to listOf(persona.relationshipPreset, persona.relationshipText).filter { it.isNotBlank() }.joinToString(" · "),
        s.originalLearning to persona.originalLearningText).filter { it.second.isNotBlank() }.forEach { (label, value) ->
        Text(label, color = colors.TextSecondary, style = MaterialTheme.typography.labelLarge)
        Text(value, color = colors.TextPrimary, style = MaterialTheme.typography.bodyMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Text(s.characterPersonaOnlyNote, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun CharacterLibraryPage(
    title: String,
    onBack: () -> Unit,
    hazeState: HazeState,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    selectionMode: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    val advanced = LocalAdvancedMaterial.current
    val statusHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BackHandler(enabled = LocalPageActive.current, onBack = onBack)
    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        TopBarBackdropSource(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusHeight))
        Column(Modifier.fillMaxSize()
            .then(if (advanced) Modifier.hazeSource(hazeState).hazeBackground(colors.Background) else Modifier)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().jellyOverscroll(), state = listState,
            contentPadding = PaddingValues(start = 16.dp + tabletReadingGutter(), end = 16.dp + tabletReadingGutter(),
                top = HazeSpec.newPageContentPaddingDp(statusHeight, 24.dp), bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        if (bottomBar != null) Box(Modifier.fillMaxWidth()
            .background(colors.Background).navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.Center) { bottomBar() }
        }
        TopBarBackdrop(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusHeight))
        AnimatedContent(targetState = selectionMode,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(top = 8.8.dp).floatingHeaderInset(),
            transitionSpec = { FreeChatAnimation.contentReplacement() }, label = "characterLibraryHeader") { selecting ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (selecting) HeaderIconButton(onClick = onBack) {
                    Icon(Icons.Filled.Close, s.cancel, tint = colors.TextPrimary)
                } else FloatingWindowBackButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back, tint = colors.TextPrimary)
                }
                Text(title, Modifier.weight(1f), color = colors.TextPrimary, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                actions()
                if (!selecting) PaneExpandButton()
            }
        }
    }
}

@Composable
internal fun CharacterFolderRow(name: String, count: Int, hazeState: HazeState,
    onOpen: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    Row(Modifier.fillMaxWidth().frostedCard(hazeState, colors, LocalAdvancedMaterial.current, RoundedCornerShape(16.dp),
        fallback = colors.SurfaceVariant), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 72.dp).clickable(onClick = onOpen).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Folder, null, tint = colors.Primary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, color = colors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(s.characterCount(count), color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        trailing()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CharacterLibraryCardRow(card: CharacterCard, hazeState: HazeState, onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null, selected: Boolean? = null,
    highlighted: Boolean = false,
    trailing: @Composable () -> Unit = {}) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val textColor by animateColorAsState(if (highlighted) colors.selectedText else colors.TextPrimary,
        FreeChatAnimation.colorTween, label = "characterCardText")
    val subTextColor by animateColorAsState(if (highlighted) colors.selectedSubText else colors.TextSecondary,
        FreeChatAnimation.colorTween, label = "characterCardSubText")
    Row(Modifier.fillMaxWidth().frostedCard(hazeState, colors, LocalAdvancedMaterial.current, RoundedCornerShape(16.dp),
        selected = highlighted, fallback = colors.SurfaceVariant).combinedClickable(onClick = onClick, onLongClick = onLongClick,
            onLongClickLabel = if (onLongClick != null) s.selectCharacterCards else null,
            role = if (selected != null) Role.Checkbox else null)
        .then(if (selected != null) Modifier.semantics { this.selected = selected } else Modifier)
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LibraryCharacterAvatar(card.persona)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.persona.name, color = textColor, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium)
            val description = card.persona.personalityText.ifBlank { card.persona.personalityPresets.joinToString(" · ") }
            if (description.isNotBlank()) Text(description, color = subTextColor,
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
internal fun LibraryCharacterAvatar(persona: CharacterPersona, size: Dp = 44.dp) {
    val colors = LocalFreeChatColors.current
    Box(Modifier.size(size).clip(CircleShape).background(colors.AccentMuted), contentAlignment = Alignment.Center) {
        if (persona.avatarPath.isNotBlank()) AsyncImage(File(persona.avatarPath), null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.Person, null, tint = colors.TextSecondary, modifier = Modifier.size(size * 0.55f))
    }
}

@Preview(name = "Character library cards", widthDp = 390)
@Preview(name = "Character library cards dark", widthDp = 390, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Character library large text", widthDp = 600, fontScale = 1.4f)
@Composable
private fun CharacterLibraryCardsPreview() {
    FreeChatTheme {
        Column(Modifier.background(LocalFreeChatColors.current.Background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val hazeState = rememberHazeState()
            CharacterFolderRow("故事中的朋友", 3, hazeState, {})
            CharacterLibraryCardRow(CharacterCard("preview", CharacterPersona(name = "林间", personalityText = "安静、坦率，喜欢认真听人说话")), hazeState, {})
        }
    }
}
