package com.freechat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.LocalStrings
import com.freechat.ui.theme.LocalFreeChatColors

/**
 * AI 思考中动画 — Siri 式发光球体
 *
 * 一个柔边发光球，内部彩色流光沿有机轨道流动，中心高亮。
 * 球体外圈主题色光晕让它在浅/深主题下都自然融入背景。
 */
@Composable
fun TypingIndicator(isDark: Boolean) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        SiriOrb(modifier = Modifier.size(30.dp), isDark = isDark)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            s.thinking,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = colors.TextTertiary
        )
    }
}
