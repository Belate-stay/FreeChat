package com.freechat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.AppStrings
import com.freechat.sync.ApiClient
import com.freechat.sync.Session
import com.freechat.ui.theme.LocalFreeChatColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 「我的反馈」里的一条 */
private data class FeedbackItem(val content: String, val createdAt: Long, val device: String)

/**
 * 「我的反馈」（1.0.95）：本设备/本账号提交过的反馈，**只读**——
 * 拍板原话「无法编辑无法删除就只能查看」，所以不给任何编辑/删除入口，只有内容与时间。
 * 认领口径同服务端：user_id 或本机 clientId 命中都算（匿名发的也看得见）。
 */
@Composable
internal fun MyFeedbackScreen(s: AppStrings, clientId: String) {
    val colors = LocalFreeChatColors.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<FeedbackItem>?>(null) }   // null = 加载中
    var loadFailed by remember { mutableStateOf(false) }

    suspend fun reload() {
        loadFailed = false
        items = try {
            val token = withContext(Dispatchers.IO) { Session.loadAuth()?.token }
            val arr = withContext(Dispatchers.IO) { ApiClient.myFeedback(clientId, token) }
            arr.map { e ->
                val o = e.asJsonObject
                FeedbackItem(
                    content = o.get("content")?.asString.orEmpty(),
                    createdAt = o.get("createdAt")?.asLong ?: 0L,
                    device = o.get("device")?.asString.orEmpty()
                )
            }
        } catch (_: Exception) {
            loadFailed = true
            emptyList()
        }
    }

    LaunchedEffect(Unit) { reload() }

    // ⚠️ 刻意**不自带 verticalScroll**：设置二级页的容器（SettingsScreen）已经包了滚动，
    // 再套一层 = 嵌套垂直滚动，ScrollNode measure 强校验「无限高度」当场崩（1.0.95 实机闪退根因）。
    Column(Modifier.fillMaxWidth()) {
        // 只在载入失败时给一行可点重试；正常时不需要说明——列表本身就是全部内容（只读，无杂活）
        if (loadFailed) {
            Text(s.networkUnreachable, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp).clickable { scope.launch { reload() } })
        }
        Spacer(Modifier.height(8.dp))
        val list = items
        when {
            list == null -> Row(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.Primary)
            }
            list.isEmpty() -> Text(
                if (loadFailed) s.networkUnreachable else s.feedbackMyEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.TextTertiary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
            else -> SettingsRow {
                Column {
                    list.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = colors.Divider.copy(alpha = 0.6f))
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                item.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.TextPrimary
                            )
                            Text(
                                SimpleDateFormat(s.dateYmdTime, Locale.getDefault()).format(Date(item.createdAt)) +
                                    if (item.device.isNotBlank()) " · ${item.device}" else "",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp),
                                color = colors.TextTertiary
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
