package com.freechat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.freechat.i18n.AppStrings
import com.freechat.sync.ApiClient
import com.freechat.sync.Session
import com.freechat.sync.ShareInfo
import com.freechat.sync.ActiveShareList
import kotlinx.coroutines.CancellationException
import com.freechat.ui.theme.LocalFreeChatColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「我的分享」：已生成的在线网页链接列表 + 撤销。
 *
 * 撤销是即时的——服务端置 revoked 并删图片，链接立刻 404。
 * 这里刻意不做「编辑标题/续期」之类的杂活：分享是发完即忘的快照，管理面只需要「看得到、收得回」。
 */
@Composable
internal fun MySharesScreen(s: AppStrings, onRequestRevoke: (ShareInfo, () -> Unit) -> Unit) {
    val colors = LocalFreeChatColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shares by remember { mutableStateOf<List<ShareInfo>?>(null) }   // null = 加载中
    var loadFailed by remember { mutableStateOf(false) }
    val activeShares = remember { ActiveShareList() }
    var revokingIds by remember { mutableStateOf(emptySet<String>()) }
    var reloadVersion by remember { mutableIntStateOf(0) }

    suspend fun reload() {
        val version = ++reloadVersion
        val auth = withContext(Dispatchers.IO) { Session.loadAuth() }
        if (auth == null) {
            if (version == reloadVersion) shares = emptyList()
            return
        }
        loadFailed = false
        try {
            val rows = withContext(Dispatchers.IO) { ApiClient.listShares(auth.token) }
            if (version == reloadVersion) shares = activeShares.visible(rows)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (version == reloadVersion) {
                loadFailed = true
                if (shares == null) shares = emptyList()
            }
        }
    }

    fun requestRevoke(share: ShareInfo) {
        if (share.id in revokingIds) return
        onRequestRevoke(share) {
            if (share.id !in revokingIds) {
                revokingIds = revokingIds + share.id
                scope.launch {
                    try {
                        val auth = withContext(Dispatchers.IO) { Session.loadAuth() }
                        if (auth == null) {
                            Toast.makeText(context, s.notLoggedIn, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        withContext(Dispatchers.IO) { ApiClient.revokeShare(auth.token, share.id) }
                        activeShares.revoked(share.id)
                        shares = activeShares.visible(shares.orEmpty())
                        Toast.makeText(context, s.shareRevoked, Toast.LENGTH_SHORT).show()
                        reload()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: com.freechat.sync.ApiError) {
                        Toast.makeText(context, e.message ?: s.networkUnreachable, Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, s.networkUnreachable, Toast.LENGTH_SHORT).show()
                    } finally {
                        revokingIds = revokingIds - share.id
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    // ⚠️ 刻意**不自带 verticalScroll**：设置二级页的容器已经包了滚动，再套一层 = 嵌套垂直滚动，
    // ScrollNode measure 强校验「无限高度」当场崩（1.0.95「我的反馈」闪退根因；本页同款结构一并修）
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (loadFailed) s.networkUnreachable else s.shareLinkHint,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp).clickable(enabled = loadFailed) {
                scope.launch { reload() }
            }
        )
        Spacer(Modifier.height(8.dp))
        val list = shares
        when {
            list == null -> Row(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.Primary)
            }
            list.isEmpty() -> Text(
                if (loadFailed) s.networkUnreachable else s.mySharesEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.TextTertiary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
            else -> SettingsRow {
                Column {
                    list.forEachIndexed { index, share ->
                        if (index > 0) HorizontalDivider(color = colors.Divider.copy(alpha = 0.6f))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    share.title.ifBlank { s.share },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    SimpleDateFormat(s.dateYmdTime, Locale.getDefault()).format(Date(share.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.TextTertiary
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = share.id !in revokingIds) { requestRevoke(share) }
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (share.id in revokingIds) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.Primary)
                                else Icon(Icons.Filled.DeleteOutline, null, tint = colors.TextSecondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(s.shareRevoke, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

}
