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
    val context = LocalContext.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        TopBarBackdropSource(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBarHeightDp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
                .verticalScroll(com.freechat.ui.animation.rememberTopOnEntryScrollState())
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
            // 官网链接块（1.1.0 加在须知门槛页；1.1.26.4 须知正文按产品定稿六条后挪来本页页脚）
            Text(s.agreementWebLabel, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
            Text(
                "https://freechater.com/",
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = if (isSystemInDarkTheme()) Color(0xFF8AB4F8) else Color(0xFF1A73E8),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://freechater.com/"))
                        )
                    }
                }
            )
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

/**
 * 首次进入的「使用前请阅读」（1.1.26.4 改**全屏页面**，不再是弹窗）：勾选同意 + 确定并继续
 * 才算进入软件；查看《用户协议与使用条款》不算进入——返回后仍在本页，启动提醒一律押后到
 * 真正进入之后。正文就是产品须知六条（语言包 agreementGateItems），序号悬挂缩进排版。
 */
@Composable
fun AgreementGatePage(
    onAgree: () -> Unit,
    onOpenAgreement: () -> Unit
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var agreed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = HazeSpec.topContentPaddingDp(statusBarHeightDp, 36.dp), bottom = 32.dp)
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                s.agreementGateHead,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.TextPrimary
            )
            Spacer(Modifier.height(20.dp))
            // 条目排版：序号一列悬挂、正文对齐，条目间留呼吸（工单点名「文字排版优化」）
            s.agreementGateItems.forEachIndexed { i, item ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.Primary,
                        modifier = Modifier.width(22.dp)
                    )
                    Text(
                        item,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.TextPrimary,
                        lineHeight = 26.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = agreed,
                    onCheckedChange = { agreed = it },
                    colors = CheckboxDefaults.colors(checkedColor = colors.Primary)
                )
                Text(
                    s.agreementAgreePrefix,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.TextPrimary,
                    modifier = Modifier.clickable { agreed = !agreed }
                )
                Text(
                    s.agreementDocName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.Primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onOpenAgreement() }
                )
            }
            Spacer(Modifier.height(16.dp))
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
