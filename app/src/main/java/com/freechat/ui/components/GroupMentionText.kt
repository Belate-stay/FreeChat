package com.freechat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.freechat.core.GroupMentionDraft
import com.freechat.core.GroupMentionQuery
import com.freechat.i18n.LocalStrings
import com.freechat.i18n.groupSelectMention
import com.freechat.model.GroupMember
import com.freechat.model.GroupMention
import com.freechat.ui.theme.FreeChatColors
import dev.chrisbanes.haze.HazeState
import java.io.File

data class MentionComposerInsertion(val text: String, val caret: Int, val revision: Int)
data class MentionSelectionRequest(val conversationId: String, val text: String, val query: GroupMentionQuery, val caret: Int)

internal fun groupMentionText(text: String, bindings: List<GroupMention>, ink: Color, fill: Color): AnnotatedString =
    AnnotatedString.Builder(text).apply {
        GroupMentionDraft(text, bindings).validBindings().forEach {
            addStyle(SpanStyle(color = ink, background = fill, fontWeight = FontWeight.SemiBold), it.start, it.end)
        }
    }.toAnnotatedString()

@Composable
fun BoxScope.GroupMentionSheet(visible: Boolean, members: List<GroupMember>, onDismiss: () -> Unit,
    onPick: (String) -> Unit, colors: FreeChatColors, isDark: Boolean, advanced: Boolean, hazeState: HazeState) {
    val s = LocalStrings.current
    SheetPanel(visible, onDismiss, s.groupSelectMention, colors, isDark, advanced, hazeState) {
        members.forEach { member ->
            Row(Modifier.fillMaxWidth().clickable { onPick(member.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(colors.Primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center) {
                    if (member.persona.avatarPath.isBlank()) Icon(Icons.Filled.Person, null, tint = colors.TextSecondary)
                    else AsyncImage(model = member.persona.avatarPath.let { if (File(it).exists()) File(it) else it },
                        contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Text(member.persona.name, color = colors.TextPrimary, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
