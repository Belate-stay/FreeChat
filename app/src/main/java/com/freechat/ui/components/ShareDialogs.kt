package com.freechat.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.freechat.i18n.LocalStrings
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalFreeChatColors

/**
 * 分享相关的公共弹窗件。
 * 从 ChatBubble.kt 抽出来是为了让 ChatScreen 的批量分享复用同一套观感
 * （单条分享的入口已经并进多选，这两个组件的唯一使用者变成了批量分享）。
 */

/** 分享菜单的一行：图标 + 文字 */
@Composable
internal fun ShareMenuRow(
    icon: ImageVector,
    label: String,
    colors: FreeChatColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
    }
}

/**
 * 生成好的分享长图全屏预览 + 分享 / 保存。
 * 无状态：颜色与文案内部自取，分享与保存的行为由调用方注入。
 */
@Composable
internal fun ShareImagePreviewDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Text(s.share, color = colors.Primary, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Text(s.saveImage, color = colors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
