package com.freechat.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.freechat.BuildConfig
import com.freechat.i18n.AppStrings
import com.freechat.sync.ApiClient
import com.freechat.sync.Session
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.theme.selectedText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 「建议与反馈」（1.0.95 接真后端）：提交即发送到服务器——**不要求登录**，
 * 登录了就顺手把反馈挂到账号上；失败保留草稿可重试。成功后清草稿。
 *
 * 诚实口径跟着行为走（Beta83 那条「文案不许谎称已发送」的测试随行为反转改钉
 * 「文案不许谎称不发送」）：正文明确写清「会发送给开发者 + 附带设备排查信息」。
 */
@Composable
internal fun FeedbackPage(
    text: String,
    onTextChange: (String) -> Unit,
    clientId: String,
    s: AppStrings
) {
    val colors = LocalFreeChatColors.current
    val advanced = LocalAdvancedMaterial.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    // 结果播报：sent / failed / null（提交中或还没提交）
    var result by remember { mutableStateOf<String?>(null) }

    Text(s.feedbackDescription, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
    SettingsRow {
        OutlinedTextField(value = text, onValueChange = { result = null; onTextChange(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            label = { Text(s.feedbackLabel) }, placeholder = { Text(s.feedbackHint) },
            minLines = 6, maxLines = 12, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.TextPrimary,
                unfocusedTextColor = colors.TextPrimary, focusedLabelColor = colors.Primary,
                unfocusedLabelColor = colors.TextSecondary, focusedBorderColor = colors.Primary,
                unfocusedBorderColor = colors.Divider, cursorColor = colors.Primary))
    }
    Spacer(Modifier.height(12.dp))
    val enabled = text.isNotBlank() && !sending
    Box(Modifier.fillMaxWidth().frostedCard(LocalSettingsHazeState.current, colors, advanced,
        RoundedCornerShape(16.dp), face = if (text.isNotBlank()) colors.Primary else colors.Surface)
        .clip(RoundedCornerShape(16.dp))
        .clickable(enabled = enabled, role = Role.Button) {
            val content = text
            sending = true
            result = null
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val auth = Session.loadAuth()
                        ApiClient.submitFeedback(content, clientId, deviceSummary(), token = auth?.token)
                    }.isSuccess
                }
                sending = false
                result = if (ok) s.feedbackSent else s.feedbackSendFailed
                if (ok) onTextChange("")   // 清草稿（已落服务器，本机留着没意义）
            }
        }
        .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(
            if (sending) s.feedbackSending else s.feedbackSubmit,
            color = if (enabled || sending) colors.selectedText else colors.TextTertiary,
            style = MaterialTheme.typography.titleSmall
        )
    }
    AnimatedVisibility(result != null, enter = FreeChatAnimation.expandEnter(), exit = FreeChatAnimation.expandExit()) {
        Text(result.orEmpty(), style = MaterialTheme.typography.bodyMedium,
            color = if (result == s.feedbackSent) colors.Primary else colors.TextSecondary,
            modifier = Modifier.padding(4.dp).semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/**
 * 随反馈附带的设备摘要（服务器反馈页给站长看「设备等信息」）：
 * 品牌机型 · 系统版本(SDK) · 应用版本 · 语言。一行、可读、无隐私内容（不带序列号/广告标识）。
 */
private fun deviceSummary(): String {
    val brand = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercaseChar() }
    return "$brand ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${BuildConfig.VERSION_NAME} · ${Locale.getDefault().toLanguageTag()}"
}
