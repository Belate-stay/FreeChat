package com.freechat.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.R
import com.freechat.data.GenerationPhase
import com.freechat.i18n.LocalStrings
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.animation.MotionButton as Button
import com.freechat.ui.animation.MotionPolicy
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.theme.pageBackground
import kotlinx.coroutines.delay
import java.io.File

/**
 * 「新功能推荐！」更新导览（1.1.31；1.1.32/1.1.33 返修=演示流程 1:1 复刻真机 + 小卡适配）。
 *
 * 卡片内是**真实组件的实时渲染**——不是录屏不是脚本动画：消息入场走 messageEnter/bodyEnter
 * 同曲线、AI 正文走 [TypewriterText] 逐字显现、思考球是 [SiriOrb]、菜单是 ChatScreen 浮层
 * 同款几何与 menuEnter 枢轴动效、生成槽直接用真组件 [GeneratedImage]（占位粒子→相位推进→
 * 画幅变形 reveal 全走真机同一条管线，成图原比例不裁切）。
 * 1.1.33 工单：卡片小→UI 元素（菜单/生成槽）等比缩小但**文本不缩**；底部输入条删除，
 * 菜单直接在下方弹出；节奏整体放慢、第一轮对话与生图之间长顿（约 12s 全程）。
 * 播放期间锁定交互，结束后才亮出行动按钮。演示文案是产品示例内容（角色演绎片段），
 * 三语界面下不翻译；介绍与界面 chrome 三语。
 */
@Composable
fun FeatureRecPage(onDone: () -> Unit) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    var stage by remember { mutableIntStateOf(0) }
    // 时间轴：逐级推进；1.2.4 返修=用户消息后立刻出球（250ms 响应敏捷）、球多转一会，
    // 第一轮对话↔生图之间仍长顿（beat[3] 2.6s）；1.2.0 返修=思考 3.6s（三个状态各 1.2s）、
    // 生图 3.2s（相位 550ms/步）——太快显得假
    LaunchedEffect(Unit) {
        val beats = intArrayOf(400, 250, 3600, 2600, 1200, 900, 3200, 1800)
        for ((i, ms) in beats.withIndex()) {
            delay(ms.toLong())
            stage = i + 1
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        // 卡高自适应：内容（成图按原比例）优先，窄/矮屏收卡不裁内容不压行动按钮
        val cardH = minOf((maxWidth - 44.dp) / 0.62f, maxHeight - 280.dp).coerceAtLeast(300.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 20.dp, start = 22.dp, end = 22.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                s.featureRecTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            // 两行居中定稿（1.1.33 工单）：第一行的英文引号随模型名一起蓝；双引号用英文
            val blue = if (isSystemInDarkTheme()) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
            Text(
                buildAnnotatedString {
                    append(s.featureRecBodyBefore)
                    withStyle(SpanStyle(color = blue, fontWeight = FontWeight.SemiBold)) {
                        append("\"ChatGPT-Image-2.5\"")
                    }
                    append("\n")
                    append(s.featureRecBodyAfter)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )
            Spacer(Modifier.height(20.dp))
            // 演示卡：锁定交互（整卡吞点按，含键盘聚焦），卡高随屏自适应
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardH)
                    .frostedCard(null, colors, advancedMaterial = false, RoundedCornerShape(22.dp))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    }
            ) {
                FeatureRecDemo(stage, colors)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onDone,
                enabled = stage >= 8,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.Primary,
                    contentColor = colors.OnPrimary,
                    disabledContainerColor = colors.SurfaceVariant,
                    disabledContentColor = colors.TextTertiary
                )
            ) {
                Text(s.featureRecCta, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 演示片段文案（产品示例内容，角色演绎，固定中文不随语言包走）。 */
private const val DEMO_USER_TEXT =
    "我夹了一块糖醋里脊，放到她的碗中，嘴角轻扬一下：“吃饭还堵不住你的嘴。”眉眼温柔，话语中满是宠溺，像抹了蜜一般。"
private const val DEMO_AI_TEXT =
    "她鼓了鼓腮帮子，轻声“哼”了一声，将里脊肉塞进嘴里。吃得急，嘴角沾了些酱料。"

/** 消息级入场：与 [com.freechat.ui.animation.FreeChatAnimation] 的 messageEnter 同曲线（淡入+22dp 升起）。 */
@Composable
private fun demoMessageEnter(visible: Boolean, content: @Composable () -> Unit) {
    val risePx = with(androidx.compose.ui.platform.LocalDensity.current) { MotionPolicy.MessageRiseDp.dp.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(FreeChatAnimation.DURATION_MESSAGE, easing = FreeChatAnimation.arrivalEase)) +
            slideInVertically(tween(FreeChatAnimation.DURATION_MESSAGE, easing = FreeChatAnimation.arrivalEase)) { risePx },
        exit = fadeOut(FreeChatAnimation.overlayFadeOut)
    ) { content() }
}

/** AI 正文入场：与 ChatBubble 的 bodyEnter 同曲线（10dp 升起+淡入）。 */
@Composable
private fun demoBodyEnter(visible: Boolean, content: @Composable () -> Unit) {
    val risePx = with(androidx.compose.ui.platform.LocalDensity.current) { MotionPolicy.BodyRiseDp.dp.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        enter = FreeChatAnimation.bodyEnter(risePx),
        exit = fadeOut(FreeChatAnimation.overlayFadeOut)
    ) { content() }
}

/** 卡内演示：聊天界面一隅（消息流 + 常驻输入条 + 「+」菜单 + 生成槽 + 成图），随 [stage] 自动推进。 */
@Composable
private fun FeatureRecDemo(stage: Int, colors: FreeChatColors) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val motionOn = LocalMotionEnabled.current

    // 成图资源落缓存文件：生成槽走真组件 GeneratedImage，占位→变形→成图与真机同一条管线
    var scenePath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val f = File(context.cacheDir, "feature_rec_scene.png")
        if (!f.exists()) runCatching {
            context.resources.openRawResource(R.drawable.feature_rec_scene).use { input ->
                f.outputStream().use { out -> input.copyTo(out) }
            }
        }
        if (f.exists()) scenePath = f.absolutePath
    }

    // 生成占位相位推进（真机 GenerationPhase 同序）+ 槽下耗时计时
    var genPhase by remember { mutableStateOf(GenerationPhase.IMAGE_CONTEXT) }
    var genMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(stage) {
        if (stage == 6) {
            val t0 = android.os.SystemClock.elapsedRealtime()
            genPhase = GenerationPhase.IMAGE_CONTEXT
            genMs = 0
            for (p in listOf(
                GenerationPhase.IMAGE_REFERENCES, GenerationPhase.IMAGE_CONNECTING,
                GenerationPhase.IMAGE_GENERATING, GenerationPhase.IMAGE_RECEIVING
            )) {
                delay(550)
                genPhase = p
                genMs = android.os.SystemClock.elapsedRealtime() - t0
            }
        } else if (stage == 7) {
            val t0 = android.os.SystemClock.elapsedRealtime() - genMs
            while (true) {
                delay(100)
                genMs = android.os.SystemClock.elapsedRealtime() - t0
            }
        }
    }

    // 思考状态逐级推进（真机 generationPhase 同序：连接模型→理解内容→拟草回复，各 1.2s）
    var thinkPhase by remember { mutableStateOf(GenerationPhase.CONNECTING) }
    LaunchedEffect(stage) {
        if (stage == 2) {
            thinkPhase = GenerationPhase.CONNECTING
            delay(1200)
            thinkPhase = GenerationPhase.UNDERSTANDING
            delay(1200)
            thinkPhase = GenerationPhase.DRAFTING
        }
    }

    // 菜单项点按缩放：真机 PressNode pressScale 同参（0.96 + pressDown/pressRelease 弹簧）
    val rowPress by animateFloatAsState(
        targetValue = if (stage == 5) MotionPolicy.PressScale else 1f,
        animationSpec = if (stage == 5) FreeChatAnimation.pressDown else FreeChatAnimation.pressRelease,
        label = "rec_row_press"
    )
    val demoAiId = remember { "feature_rec_ai_" + java.util.UUID.randomUUID() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp)) {
            // —— 用户消息（右侧绿泡）——
            demoMessageEnter(stage >= 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier
                            .width(238.dp)
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp))
                            .background(colors.UserBubble)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(DEMO_USER_TEXT, style = MaterialTheme.typography.bodySmall,
                            color = colors.UserBubbleText, lineHeight = 19.sp)
                    }
                }
            }
            // 元素间距（1.1.33 返修：小卡里也要呼吸感，别把提示词/信息流/声明/图挤成一坨）
            Spacer(Modifier.height(22.dp))
            // —— AI 思考球 + 右侧思考状态（真组件，与 ChatBubble 的思考行 1:1 同构）→ 退场给正文让位 ——
            Box(Modifier.fillMaxWidth()) {
                // Column 链内嵌 Box 的 AnimatedVisibility 必须全限定名（ColumnScope 重载歧义）
                androidx.compose.animation.AnimatedVisibility(
                    visible = stage == 2,
                    enter = fadeIn(FreeChatAnimation.overlayFadeIn),
                    exit = fadeOut(FreeChatAnimation.overlayFadeOut)
                ) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SiriOrb(Modifier.size(30.dp), isDark = isDark)
                        Column {
                            AnimatedContent(thinkPhase, transitionSpec = { FreeChatAnimation.contentReplacement() },
                                label = "generation_stage") { p ->
                                Text(s.generationPhaseLabels[p].orEmpty(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = colors.TextSecondary)
                            }
                        }
                    }
                }
            }
            // —— AI 回复（bodyEnter 同曲线 + 逐字显现真组件）——
            demoBodyEnter(stage >= 3) {
                TypewriterText(
                    text = AnnotatedString(DEMO_AI_TEXT),
                    messageId = demoAiId,
                    timestampMs = System.currentTimeMillis(),
                    enabled = motionOn,
                    textStyle = MaterialTheme.typography.bodySmall,
                    color = colors.TextPrimary,
                    modifier = Modifier.fillMaxWidth().padding(end = 24.dp)
                )
            }
            // —— 场景图视觉行（点「生成当前场景图」后真机同款：声明行 + 生成槽 + 槽下署名）——
            Spacer(Modifier.height(20.dp))
            demoMessageEnter(stage >= 6) {
                Column(Modifier.fillMaxWidth().padding(end = 24.dp)) {
                    Text(s.sceneImageVisualOnly, style = MaterialTheme.typography.bodySmall,
                        color = colors.TextSecondary, modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp))
                    // 生成槽等比缩小（1.1.33：小卡里占位图/成图别占满行宽，文本不缩只缩元素）
                    Box(Modifier.fillMaxWidth(0.72f)) {
                        // 真组件生成槽：占位粒子→相位推进→画幅变形 reveal，成图 ContentScale.Fit 原比例
                        GeneratedImage(
                            source = if (stage >= 7) scenePath else null,
                            modifier = Modifier.padding(vertical = 4.dp),
                            generating = stage < 7,
                            phase = genPhase,
                            fromGeneration = true
                        )
                    }
                    if (stage < 8) {
                        // 生成中：真机在槽下预留控件位（34dp）+ 模型名/计时（ChatBubble 同款）
                        Spacer(Modifier.height(34.dp))
                        MessageAttribution("ChatGPT-Image-2.5", genMs, colors, showZeroTime = true)
                    } else {
                        // 完成：真机同款操作栏（删除/重新生成小图标）+ 署名
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).padding(6.dp),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.DeleteOutline, null, tint = colors.TextTertiary,
                                    modifier = Modifier.size(16.dp))
                            }
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).padding(6.dp),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Refresh, null, tint = colors.TextTertiary,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                        MessageAttribution("ChatGPT-Image-2.5", genMs, colors)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // （1.1.33 工单：底部输入条删除——下方直接弹出「生成当前场景图」选项）
        }

        // —— 「生成当前场景图」菜单：ChatScreen 浮层同款动效，几何等比缩小（小卡适配）、
        //    无输入条直接在下方弹出（1.1.33）——
        androidx.compose.animation.AnimatedVisibility(
            visible = stage == 4,
            modifier = Modifier.align(Alignment.BottomStart).offset {
                IntOffset(10.dp.roundToPx(), -24.dp.roundToPx())
            },
            enter = FreeChatAnimation.menuEnter(TransformOrigin(0f, 1f)),
            exit = FreeChatAnimation.menuExit(TransformOrigin(0f, 1f))
        ) {
            Box(
                Modifier
                    .padding(10.dp)
                    // 宽度随内容（取最大内蕴=文字单行宽）——文字恒一行不换行，三语都不折
                    .width(IntrinsicSize.Max)
                    // 拟物风卡片：与软件内卡片同一材质（frostedCard 新拟态面+柔影）
                    .frostedCard(null, colors, advancedMaterial = true, RoundedCornerShape(17.dp))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = rowPress; scaleY = rowPress }
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Image, null, tint = colors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(s.generateSceneImage, style = MaterialTheme.typography.bodyMedium,
                        color = colors.TextPrimary, maxLines = 1)
                }
            }
        }
    }
}

// （1.1.33 工单：底部输入条整段删除——下方直接弹出「生成当前场景图」选项）
