package com.freechat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freechat.data.AppJson
import com.freechat.data.CharacterLibraryStore
import com.freechat.data.LocalStore
import com.freechat.data.MessageDeletion
import com.freechat.i18n.*
import com.freechat.model.Message
import com.freechat.ui.components.SheetPanel
import com.freechat.ui.components.SheetOption
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.FreeChatInputShape
import com.google.gson.reflect.TypeToken
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CharacterFolderDestinationSheet(visible: Boolean, initialFolderId: String?, isDark: Boolean,
    onDismiss: () -> Unit, onConfirm: (String?) -> Unit, hazeState: dev.chrisbanes.haze.HazeState) {
    val state by CharacterLibraryStore.state.collectAsState()
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    var folder by remember(visible) { mutableStateOf(initialFolderId?.takeIf { id -> state.folders.any { it.id == id } }) }
    Box(Modifier.fillMaxSize()) {
    SheetPanel(visible, onDismiss, s.storeInCharacterLibrary, colors, isDark,
        LocalAdvancedMaterial.current, hazeState, confirmLabel = s.save, onConfirm = { onConfirm(folder) }) {
        Text(s.characterPersonaOnlyNote, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        SheetOption(folder == null, s.unclassifiedCharacters, colors, onClick = { folder = null })
        state.folders.forEach { entry ->
            SheetOption(folder == entry.id, entry.name, colors, onClick = { folder = entry.id })
        }
    }
    }
}

/** Search is scoped at the file boundary, so private chats cannot appear in group results. */
@Composable
fun GroupConversationSearchScreen(conversationId: String, onSelect: (String) -> Unit, onBack: () -> Unit) {
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    var query by remember(conversationId) { mutableStateOf("") }
    var rows by remember(conversationId) { mutableStateOf(emptyList<Message>()) }
    LaunchedEffect(conversationId) {
        rows = withContext(Dispatchers.IO) {
            runCatching {
                val type = object : TypeToken<List<Message>>() {}.type
                val text = LocalStore.readText(LocalStore.messagesFile(conversationId))
                MessageDeletion.messages(conversationId, text?.let { AppJson.gson.fromJson<List<Message>>(it, type) }.orEmpty())
            }.getOrDefault(emptyList())
        }
    }
    val results = remember(query, rows) {
        if (query.isBlank()) emptyList() else rows.filter { !it.failed && it.content.contains(query.trim(), ignoreCase = true) }
    }
    CharacterLibraryPage(s.groupSearch, onBack, rememberHazeState()) {
        item {
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(s.groupSearch, color = colors.TextTertiary) },
                shape = FreeChatInputShape)
        }
        items(results, key = { it.id }) { message ->
            Column(Modifier.fillMaxWidth().clickable { onSelect(message.id) }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!message.groupSenderName.isNullOrBlank()) Text(message.groupSenderName.orEmpty(),
                    color = colors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Text(message.content, color = colors.TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 5)
            }
            HorizontalDivider(color = colors.Divider)
        }
    }
}
