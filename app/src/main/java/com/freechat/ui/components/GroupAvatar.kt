package com.freechat.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.freechat.model.GroupChatProfile
import com.freechat.model.Message
import com.freechat.sync.AvatarStore
import com.freechat.ui.theme.LocalFreeChatColors
import java.io.File

internal data class GroupMessageIdentity(
    val memberId: String?, val name: String, val avatarPath: String, val isCurrentMember: Boolean,
) {
    companion object {
        fun resolve(message: Message, group: GroupChatProfile): GroupMessageIdentity {
            val member = group.members.firstOrNull { it.id == message.groupMemberId }
            return GroupMessageIdentity(message.groupMemberId,
                member?.persona?.name ?: message.groupSenderName.orEmpty(),
                member?.persona?.avatarPath ?: message.groupSenderAvatar.orEmpty(), member != null)
        }
    }
}

internal fun groupAvatarRows(memberCount: Int): List<Int> = when (memberCount.coerceIn(0, 4)) {
    0, 1 -> listOf(1)
    2 -> listOf(2)
    3 -> listOf(1, 2)
    else -> listOf(2, 2)
}

/** A custom group image is independent of the roster; clearing it reveals the current composite. */
@Composable
fun GroupAvatar(group: GroupChatProfile, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val colors = LocalFreeChatColors.current
    val members = group.members.take(4)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(10.dp)).background(colors.SurfaceVariant)
        .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)) {
        if (group.avatarPath.isNotBlank()) {
            GroupParticipantAvatar(group.avatarPath, "", Modifier.fillMaxSize())
        } else {
            val rows = groupAvatarRows(members.size)
            val tileSize = if (members.size > 1) (minOf(maxWidth, maxHeight) - 6.dp) / 2 else minOf(maxWidth, maxHeight)
            Column(Modifier.fillMaxSize().padding(if (members.size > 1) 2.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                var offset = 0
                rows.forEach { count ->
                    val row = members.drop(offset).take(count)
                    offset += count
                    Row(Modifier.height(tileSize).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)) {
                        if (row.isEmpty()) GroupParticipantAvatar("", "", Modifier.size(tileSize))
                        row.forEach { member ->
                            GroupParticipantAvatar(member.persona.avatarPath, member.persona.name,
                                Modifier.size(tileSize))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupParticipantAvatar(avatarPath: String, name: String, modifier: Modifier = Modifier) {
    val colors = LocalFreeChatColors.current
    var unavailable by remember(avatarPath) { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(colors.AccentMuted), contentAlignment = Alignment.Center) {
        if (avatarPath.isNotBlank() && !unavailable) {
            AsyncImage(model = File(avatarPath), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(), onError = { unavailable = true })
        } else if (name.isNotBlank()) {
            Text(name.take(1), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium,
                color = colors.Primary)
        } else {
            Icon(Icons.Filled.Person, null, tint = colors.Primary, modifier = Modifier.fillMaxSize(0.55f))
        }
    }
}

@Composable
internal fun GroupUserAvatar(modifier: Modifier = Modifier, useDefaultUserAvatar: Boolean = false) {
    val colors = LocalFreeChatColors.current
    val bytes by AvatarStore.current.collectAsState()
    // 演示页的「我」只出未设置头像的默认灰（2026-10-28 工单：不暴露账号真头像）
    val bitmap = remember(bytes, useDefaultUserAvatar) {
        if (useDefaultUserAvatar) null
        else bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(colors.AccentMuted), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.Person, null, tint = colors.Primary, modifier = Modifier.fillMaxSize(0.55f))
    }
}
