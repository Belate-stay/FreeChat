package com.freechat.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.freechat.ui.animation.MotionTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.freechat.i18n.LocalStrings
import com.freechat.sync.ApiClient
import com.freechat.sync.Session
import com.freechat.ui.theme.FreeChatColors
import com.freechat.util.QrEncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 「连接微信」弹窗（1.0.93 迁到 Chat 标题栏；1.0.94 加 Beta 标注/功能使用须知/登录引导/验证码输入）。
 *
 * 三态：
 *  · 未连接 → 官方扫码授权流（iLink 二维码本地编码成图——接口给的是「码内容」不是图片）；
 *  · 已连接本对话 → 显示已连接的微信账号 + 断开连接；
 *  · 已连接**别的**对话 → 单实例确认：Clawbot 只有一个，继续则用本对话顶替旧绑定（旧对话随之断开）。
 *
 * 1.0.94 补齐：
 *  · 标题带 Beta 角标 + 测试阶段提示（该功能并不稳定，话说在前面）；
 *  · 「功能使用须知」五条常驻弹窗底部（云同步/内置模型/头像备注/微信仅是 Bridge/延迟与稳定性）；
 *  · 未登录也可打开（标题栏按钮不再只给登录用户）——显示须知第一条 + 「登录使用」跳登录注册页；
 *  · need_verifycode 是**真分支**不是死胡同：给出验证码输入框 + 提交（1.0.91 只会显示一句提示，
 *    轮询永远空转——M5 摸底的实机教训）。
 */
@Composable
internal fun WechatBindDialog(
    visible: Boolean,
    convId: String,
    colors: FreeChatColors,
    onDismiss: () -> Unit,
    onBoundChanged: () -> Unit,
    onLogin: () -> Unit = {},
    resolveConvTitle: (String) -> String = { it }
) {
    if (!visible) return
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf("loading") }   // loading / qr / connected / takeover / login
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var qrText by remember { mutableStateOf("") }
    var bindState by remember { mutableStateOf("wait") }
    var verifyInput by remember { mutableStateOf("") }
    var connectedTo by remember { mutableStateOf("") }
    var otherConvTitle by remember { mutableStateOf("") }
    var otherConvId by remember { mutableStateOf("") }

    // 轮询扫码状态：confirmed 收口；终态（过期/失败/要验证码）停轮——need_verifycode 等用户输码，
    // 不再空转把服务端长轮询打成流水（提交后回到 wait/scaned 再续轮）
    fun pollLoop(authToken: String) {
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(2000)
                val st = runCatching { ApiClient.wechatBindStatus(authToken, convId) }.getOrNull() ?: "wait"
                bindState = st
                if (st == "confirmed") {
                    phase = "connected"
                    connectedTo = ""
                    onBoundChanged()
                    break
                }
                if (st == "expired" || st == "verify_code_blocked" || st == "failed" || st == "need_verifycode") break
            }
        }
    }

    fun startQrFlow(authToken: String) {
        phase = "qr"
        bindState = "wait"
        qrBitmap = null
        qrText = ""
        verifyInput = ""
        scope.launch(Dispatchers.IO) {
            val qr = runCatching { ApiClient.wechatBindStart(authToken) }.getOrNull()
            if (qr == null) {
                bindState = "failed"
                return@launch
            }
            // 码内容优先整段编码（确认链接）；空了退回码值
            val content = qr.second.ifBlank { qr.first }
            qrBitmap = QrEncode.bitmap(content)
            qrText = qr.first
            pollLoop(authToken)
        }
    }

    /** 提交二次验证码（need_verifycode）：带码再查一次；回到 wait/scaned 就续轮 */
    fun submitVerifyCode() {
        val auth = Session.loadAuth() ?: return
        val code = verifyInput.trim()
        if (code.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val st = runCatching { ApiClient.wechatBindStatus(auth.token, convId, code) }.getOrNull() ?: "failed"
            bindState = st
            when (st) {
                "confirmed" -> {
                    phase = "connected"
                    connectedTo = ""
                    onBoundChanged()
                }
                "wait", "scaned" -> pollLoop(auth.token)   // 验证通过、等最终确认
                else -> Unit                                 // 仍要验证码/失败：留在原地再试
            }
        }
    }

    LaunchedEffect(visible, convId) {
        val auth = Session.loadAuth()
        if (auth == null) {
            // 未登录（1.0.94）：标题栏按钮人人可见，进来先讲清「云同步数据互传、仅登录可用」
            phase = "login"
            return@LaunchedEffect
        }
        val st = runCatching { ApiClient.wechatStatus(auth.token) }.getOrNull() ?: return@LaunchedEffect
        val bound = st.get("bound")?.asBoolean == true
        val boundConv = st.get("conv_id")?.asString.orEmpty()
        connectedTo = st.get("ilink_user_id")?.asString.orEmpty()
        when {
            !bound -> startQrFlow(auth.token)
            boundConv == convId -> phase = "connected"
            else -> {
                // 单实例：另一个对话占着 ClawBot，确认后顶替
                otherConvId = boundConv
                otherConvTitle = resolveConvTitle(boundConv)
                phase = "takeover"
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.Surface)
                .heightIn(max = 580.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 标题 + Beta 角标（测试阶段标注，1.0.94）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.wechatBind, style = MaterialTheme.typography.titleMedium, color = colors.TextPrimary)
                BetaChip(colors)
            }
            Text(s.wechatBetaHint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextTertiary)

            when (phase) {
                "loading" -> Text(s.wechatBindWaiting, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)

                // 未登录（1.0.94）：须知第一条 + 「登录使用」跳登录/注册页
                "login" -> {
                    Text(s.wechatNotice1, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onLogin() }.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(s.wechatLoginUse, style = MaterialTheme.typography.bodyMedium, color = colors.Primary, fontWeight = FontWeight.SemiBold)
                    }
                }

                "connected" -> {
                    Text(
                        if (connectedTo.isNotBlank()) s.wechatConnectedTo(connectedTo) else s.wechatBindConfirmed,
                        style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary
                    )
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val auth = Session.loadAuth()
                                scope.launch(Dispatchers.IO) {
                                    runCatching { auth?.let { ApiClient.wechatUnbind(it.token) } }
                                    onBoundChanged()
                                }
                                onDismiss()
                            }.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(s.wechatBindUnbind, style = MaterialTheme.typography.bodyMedium, color = colors.ErrorRed)
                    }
                }

                "takeover" -> {
                    Text(s.wechatTakeoverDesc(otherConvTitle.ifBlank { otherConvId }),
                        style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text(s.cancel, color = colors.TextSecondary)
                        }
                        TextButton(onClick = {
                            Session.loadAuth()?.let { startQrFlow(it.token) }
                        }, modifier = Modifier.weight(1f)) {
                            Text(s.wechatBind, color = colors.Primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                else -> {
                    Text(
                        when (bindState) {
                            "scaned" -> s.wechatBindScanned
                            "confirmed" -> s.wechatBindConfirmed
                            "expired" -> s.wechatBindExpired
                            "need_verifycode" -> s.wechatBindNeedVerify
                            "failed" -> s.wechatBindFailed
                            else -> s.wechatBindWaiting
                        },
                        style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary
                    )
                    val bmp = qrBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = s.wechatBindQrTitle,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else if (qrText.isNotEmpty()) {
                        // 编码失败的兜底：把码值亮出来（可复制），不至于空白
                        Text(
                            qrText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.TextPrimary,
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.SurfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp)
                        )
                    }
                    Text(s.wechatBindQrTitle, style = MaterialTheme.typography.bodySmall, color = colors.TextTertiary)

                    // need_verifycode（1.0.94 真分支）：验证码输入 + 提交
                    if (bindState == "need_verifycode") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BasicTextField(
                                value = verifyInput,
                                onValueChange = { v -> verifyInput = v.filter { it.isDigit() }.take(8) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                                cursorBrush = SolidColor(colors.Primary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.SurfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                            TextButton(onClick = { submitVerifyCode() }, enabled = verifyInput.isNotBlank()) {
                                Text(s.wechatVerifySubmit, color = if (verifyInput.isNotBlank()) colors.Primary else colors.TextTertiary,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (verifyInput.isBlank()) {
                            Text(s.wechatVerifyPlaceholder, style = MaterialTheme.typography.bodySmall, color = colors.TextTertiary)
                        }
                    }
                }
            }

            // 功能使用须知（1.0.94）：五条常驻——云同步/内置模型/头像备注/微信仅 Bridge/延迟与稳定性
            HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f))
            Text(s.wechatNoticeTitle, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = colors.TextPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(s.wechatNotice1, s.wechatNotice2, s.wechatNotice3, s.wechatNotice4, s.wechatNotice5)
                    .forEachIndexed { i, line ->
                        Text(
                            "${i + 1}. $line",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 17.sp),
                            color = colors.TextSecondary
                        )
                    }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

/**
 * 「Beta」测试阶段角标（与模拟设定页的主动智能角标同款观感）。
 * 标注不是按钮：不给点击态不给阴影，压主题色淡底。
 */
@Composable
private fun BetaChip(colors: FreeChatColors) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(shape)
            .background(colors.Primary.copy(alpha = 0.13f))
            .border(0.5.dp, colors.Primary.copy(alpha = 0.32f), shape)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            "Beta",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.4.sp),
            fontWeight = FontWeight.Medium,
            color = colors.Primary
        )
    }
}
