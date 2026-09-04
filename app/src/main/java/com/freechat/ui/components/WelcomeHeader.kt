package com.freechat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.ui.theme.*

/** 简洁版欢迎头部 — 仅保留大号时间问候语 */
@Composable
fun WelcomeHeader(isDark: Boolean, greeting: String) {
    val colors = if (isDark) DarkColors else LightColors

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp))
        Text(
            greeting,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 36.sp
            ),
            color = colors.Primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "想聊点什么？",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
