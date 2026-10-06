package com.freechat.ui.screens

import com.freechat.ui.components.HeaderIconButton

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import com.freechat.ui.animation.MotionButton as Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.freechat.i18n.LocalStrings
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.components.TopBarBackdrop
import com.freechat.ui.components.TopBarBackdropSource

/** 用户协议与免责声明页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgreementScreen(onBack: () -> Unit) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        TopBarBackdropSource(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBarHeightDp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(top = HazeSpec.topContentPaddingDp(statusBarHeightDp, 36.dp), bottom = 40.dp)
                .padding(horizontal = 22.dp)
        ) {
            // 协议正文整段整段地跟着语言包走（`**…**` 之间加粗，`· ` 开头当条目），
            // 这样三种语言各写各的，不用在代码里拼句子——拼出来的语序一定有一门是别扭的。
            // 1.1.0：页内不再重复大标题 —— 顶栏的「免责声明」就是唯一标题，正文直接上顺。
            Spacer(Modifier.height(24.dp))
            s.agreementSections.forEach { section ->
                AgreementSection(section.title, colors) {
                    section.paragraphs.forEach { p ->
                        if (p.startsWith("· ")) AgreementBullet(p.removePrefix("· "), colors)
                        else AgreementParagraph(p, colors)
                    }
                }
            }
        }

        TopBarBackdrop(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBarHeightDp))

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back, tint = colors.TextPrimary)
            }
            Text(
                s.userAgreement,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun AgreementSection(title: String, colors: FreeChatColors, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.Primary)
        Spacer(Modifier.height(10.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

/** 把 `**强调**` 解析成加粗片段；其余按普通字渲染 */
@Composable
private fun AgreementParagraph(text: String, colors: FreeChatColors) {
    val annotated = buildAnnotatedString {
        text.split("**").forEachIndexed { i, part ->
            // 奇数段落 = 被 ** 包住的那一段
            if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.Primary)) { append(part) }
            else append(part)
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary, lineHeight = 26.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AgreementBullet(text: String, colors: FreeChatColors) {
    Text(
        "· $text",
        style = MaterialTheme.typography.bodyLarge,
        color = colors.TextPrimary,
        lineHeight = 26.sp,
        modifier = Modifier.padding(start = 12.dp)
    )
    Spacer(Modifier.height(4.dp))
}

/** 首次进入的用户协议门槛（勾选同意才放行，任何路径绕不过） */
@Composable
fun AgreementGateDialog(
    onAgree: () -> Unit,
    onOpenAgreement: () -> Unit,
    colors: FreeChatColors
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    var agreed by remember { mutableStateOf(false) }

    // 以悬浮窗形式叠加在主页上方——半透明遮罩 + 居中可滚动卡片
    Dialog(onDismissRequest = { /* 禁点外部关闭 */ }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = colors.Surface,
            shadowElevation = 10.dp
        ) {
            Column(
                Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(s.agreementGateHead, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                s.agreementGateItems.forEach { item ->
                    Text(item, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary, lineHeight = 22.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.agreementReleasePage, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                    Text(
                        "https://github.com/Belate-stay/FreeChat",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = if (isSystemInDarkTheme()) Color(0xFF8AB4F8) else Color(0xFF1A73E8),
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Belate-stay/FreeChat"))) }
                        }
                    )
                }
                // 官网与发布页同款排版（标签一行 + 斜体下划线链接），不算须知条目、不编号
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.agreementWebLabel, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                    Text(
                        "https://118.178.227.178/",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = if (isSystemInDarkTheme()) Color(0xFF8AB4F8) else Color(0xFF1A73E8),
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://118.178.227.178/"))) }
                        }
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = agreed,
                        onCheckedChange = { agreed = it },
                        colors = CheckboxDefaults.colors(checkedColor = colors.Primary)
                    )
                    Text(s.agreementAgreePrefix, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary, modifier = Modifier.clickable { agreed = !agreed })
                    Text(
                        s.agreementDocName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.Primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOpenAgreement() }
                    )
                }

                Button(
                    onClick = onAgree,
                    enabled = agreed,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.Primary,
                        contentColor = colors.OnPrimary,
                        disabledContainerColor = colors.SurfaceVariant,
                        disabledContentColor = colors.TextTertiary
                    )
                ) {
                    Text(s.agreementContinue, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
