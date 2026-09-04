package com.freechat.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.LocalStrings
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** 用户协议与免责声明页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgreementScreen(onBack: () -> Unit) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp).toPx() }

    Box(Modifier.fillMaxSize().background(colors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).background(colors.Background) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(top = statusBarHeightDp + titleBarAreaDp + 36.dp, bottom = 40.dp)
                .padding(horizontal = 22.dp)
        ) {
            Text(
                "免责声明与使用须知",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(32.dp))

            AgreementSection("1. 用途限制", colors) {
                AgreementParagraph(listOf(
                    "FreeChat 是一个开源的 AI 客户端工具，旨在为用户提供更灵活、个性化的 AI 交互体验。" to false,
                    "本软件仅限用于学习、研究及个人合法用途" to true,
                    "，不得用于任何违反中华人民共和国法律法规的活动。" to false
                ), colors)
            }

            AgreementSection("2. 用户责任", colors) {
                AgreementParagraph(listOf(
                    "用户需自行获取并配置 API Key，所有通过本软件发出的请求均视为" to false,
                    "用户本人行为" to true,
                    "。" to false
                ), colors)
                AgreementParagraph(listOf("用户应对其使用 AI 模型所产生的内容负全部责任，包括但不限于：" to false), colors)
                AgreementBullet("确保输入内容不违反法律法规", colors)
                AgreementBullet("对输出内容的合法性、准确性自行判断", colors)
                AgreementBullet("不利用本软件生成、传播违法或不良信息", colors)
            }

            AgreementSection("3. API 服务独立性", colors) {
                AgreementParagraph(listOf(
                    "本软件仅为客户端工具，" to false,
                    "不提供任何 AI 模型服务" to true,
                    "。所有 AI 能力均通过用户自行配置的第三方 API 实现，软件开发者不对第三方 API 服务的可用性、稳定性及内容安全性承担责任。" to false
                ), colors)
            }

            AgreementSection("4. 开源声明", colors) {
                AgreementParagraph(listOf(
                    "本项目基于 MIT License 开源，您可自由使用、修改、分发，但需保留原始版权声明。软件按" to false,
                    "「现状」" to true,
                    "提供，不提供任何明示或暗示的担保。" to false
                ), colors)
            }

            AgreementSection("5. 法律责任", colors) {
                AgreementParagraph(listOf(
                    "若用户将本软件用于任何违法用途，软件开发者不承担任何" to false,
                    "连带法律责任" to true,
                    "。" to false
                ), colors)
            }
        }

        // 顶部标题栏
        if (advancedMaterial) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp)
                    .hazeEffect(state = hazeState) {
                        blurRadius = HazeSpec.TopBlurRadius
                        inputScale = HazeInputScale.None
                        backgroundColor = Color.Transparent
                        progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 1f, endY = topBarHeightPx, endIntensity = 0f)
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp)
                    .background(colors.Background)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.TextPrimary)
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

@Composable
private fun AgreementParagraph(segments: List<Pair<String, Boolean>>, colors: FreeChatColors) {
    val annotated = buildAnnotatedString {
        segments.forEach { (text, bold) ->
            if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.Primary)) { append(text) }
            else append(text)
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
    var agreed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(colors.Background), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = colors.Surface,
            shadowElevation = 10.dp
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("使用前请阅读：", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                Text("1. FreeChat 是开源客户端工具，不提供 API 服务，需自行配置模型 API。", style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary, lineHeight = 22.sp)
                Text("2. FreeChat 内置的模型为作者本人自用的 API，并不保证随时有额度，可适当白嫖。", style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary, lineHeight = 22.sp)
                Text("3. 请勿使用本软件生成、传播违法违规内容。", style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary, lineHeight = 22.sp)
                Text("4. 本软件仅供学习研究使用，若商用则需自行评估法律风险。", style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary, lineHeight = 22.sp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("5. 发布页：", style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
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

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = agreed,
                        onCheckedChange = { agreed = it },
                        colors = CheckboxDefaults.colors(checkedColor = colors.Primary)
                    )
                    Text("我已阅读并同意", style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary, modifier = Modifier.clickable { agreed = !agreed })
                    Text(
                        "《用户协议》",
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
                    Text("确定并继续", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
