package com.freechat.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import com.freechat.data.GenerationPhase
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.i18n.multiCharacterGroup
import com.freechat.i18n.multiCharacterStory
import com.freechat.model.CharacterPersona
import com.freechat.model.ChatMode
import com.freechat.model.GroupChatProfile
import com.freechat.model.GroupMember
import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.ui.adaptive.LocalTabletDevice
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.animation.MessageEntranceLedger
import com.freechat.ui.animation.MotionButton as Button
import com.freechat.ui.animation.MotionPolicy
import com.freechat.ui.animation.messageEntrance
import com.freechat.ui.animation.rememberMessageEntrance
import com.freechat.ui.theme.AppBoxWithConstraints as BoxWithConstraints
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.theme.pageBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

private const val DEMO_LAST_STAGE = 8
private const val FEATURE_CTA_WAIT_MS = 4_000L
private val FEATURE_REC_IDS = listOf("builtin-feature-group-chat", "builtin-feature-story-text")
private val FEATURE_REC_BEATS = listOf(
    // 群聊：逐条时间差入场——每条 460ms 入场动画播完再起下一条（800ms/拍），7 条各占一拍
    intArrayOf(400, 800, 800, 800, 800, 800, 800, 800),
    // 文本：正文 50 字/秒逐字显现（1.1.29.1 提速钉），尾段放长给长文读完
    intArrayOf(400, 250, 1800, 1500, 1400, 1400, 1400, 1600),
)

private class FeatureRecPlaybackState {
    var stage by mutableIntStateOf(0)
    var stageElapsedMs by mutableLongStateOf(0L)
    val sessionId = "feature_rec_${UUID.randomUUID()}"
    val entranceLedger = MessageEntranceLedger(emptyList(), System.currentTimeMillis())
    val messages = mutableMapOf<Int, Message>()
    val scroll = ScrollState(0)
    var followedStage = -1
}

/** Two local conversation previews, rendered through the same message components as a real chat. */
@Composable
fun FeatureRecPage(
    onDone: () -> Unit,
    onPageShown: (String) -> Unit = {},
    onPageChoice: (String, String) -> Unit = { _, _ -> },
) {
    val colors = LocalFreeChatColors.current
    val motionOn = LocalMotionEnabled.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val foreground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val pagerState = rememberPagerState { FEATURE_REC_IDS.size }
    val pageStates = remember { List(FEATURE_REC_IDS.size) { FeatureRecPlaybackState() } }
    val scope = rememberCoroutineScope()
    var completed by remember { mutableStateOf(false) }
    var navigating by remember { mutableStateOf(false) }
    var previousPage by remember { mutableIntStateOf(pagerState.settledPage) }
    var programmaticTarget by remember { mutableStateOf<Int?>(null) }
    val shownPages = remember { mutableSetOf<String>() }
    val reportShown by rememberUpdatedState(onPageShown)
    val reportChoice by rememberUpdatedState(onPageChoice)

    LaunchedEffect(pagerState, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }.collectLatest { (pageIndex, scrolling) ->
                if (scrolling) return@collectLatest
                withFrameNanos { }
                if (pageIndex != previousPage) {
                    if (programmaticTarget == pageIndex) programmaticTarget = null
                    else reportChoice(FEATURE_REC_IDS[previousPage],
                        if (pageIndex > previousPage) "swipe-next" else "swipe-previous")
                    previousPage = pageIndex
                }
                if (!navigating && programmaticTarget != pageIndex) programmaticTarget = null
                val pageId = FEATURE_REC_IDS[pageIndex]
                if (shownPages.add(pageId)) reportShown(pageId)
            }
        }
    }
    // ===== 按钮解锁（2026-10-28 工单）：进推荐页起算一次 4s，「下一页」和「立即体验」同时解锁 =====
    // 全局只计这一次：翻页不重新计时；解锁后常亮不闪（enabled 不再跟 active/navigating 联动）。
    var dwellMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (dwellMs < FEATURE_CTA_WAIT_MS) {
                val startedAt = SystemClock.elapsedRealtime()
                delay(100)
                dwellMs += SystemClock.elapsedRealtime() - startedAt
            }
        }
    }
    // 全屏导览「必须选钮」纪律：返回键吞掉——原来裸奔时按返回整个 App 直接退出桌面。
    androidx.activity.compose.BackHandler(enabled = !completed) { com.freechat.ui.animation.BackGate.cancelPending() }
    LaunchedEffect(pagerState, motionOn, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { pagerState.settledPage.takeUnless { pagerState.isScrollInProgress } }.collectLatest { pageIndex ->
                if (pageIndex == null) return@collectLatest
                val playback = pageStates[pageIndex]
                val beats = FEATURE_REC_BEATS[pageIndex]
                if (!motionOn) playback.stage = DEMO_LAST_STAGE
                while (playback.stage < DEMO_LAST_STAGE) {
                    val startedAt = SystemClock.elapsedRealtime()
                    delay(100)
                    val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                    if (motionOn) {
                        playback.stageElapsedMs += elapsedMs
                        while (playback.stage < DEMO_LAST_STAGE && playback.stageElapsedMs >= beats[playback.stage]) {
                            playback.stageElapsedMs -= beats[playback.stage]
                            playback.stage += 1
                        }
                    }
                }
            }
        }
    }
    val advance: (Int) -> Unit = advance@{ pageIndex ->
        if (completed || navigating || !foreground || pagerState.isScrollInProgress ||
            pageIndex != pagerState.settledPage || dwellMs < FEATURE_CTA_WAIT_MS) return@advance
        val pageId = FEATURE_REC_IDS[pageIndex]
        if (pageIndex == 0) {
            navigating = true
            programmaticTarget = 1
            reportChoice(pageId, "next")
            scope.launch {
                try {
                    if (motionOn) pagerState.animateScrollToPage(1, animationSpec = FreeChatAnimation.pageTween)
                    else pagerState.scrollToPage(1)
                } finally {
                    if (pagerState.settledPage != 1) programmaticTarget = null
                    navigating = false
                }
            }
        } else {
            completed = true
            com.freechat.ui.animation.BackGate.cancelPending()
            reportChoice(pageId, "done")
            onDone()
        }
    }

    HorizontalPager(state = pagerState, beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize().pageBackground(colors.Background),
        key = { FEATURE_REC_IDS[it] }, overscrollEffect = null) { pageIndex ->
        val active = foreground && pageIndex == pagerState.settledPage && !pagerState.isScrollInProgress
        val playback = pageStates[pageIndex]
        // 解锁后两页常亮：不跟 active/navigating 联动（切换页面不再闪一下），连点由 advance 闸拦
        val canAdvance = dwellMs >= FEATURE_CTA_WAIT_MS && !completed
        CompositionLocalProvider(LocalMotionEnabled provides (motionOn && active)) {
            FeatureRecContent(pageIndex, playback, active, canAdvance, colors) { advance(pageIndex) }
        }
    }
}

@Composable
private fun FeatureRecContent(pageIndex: Int, playback: FeatureRecPlaybackState, active: Boolean,
    canAdvance: Boolean, colors: FreeChatColors, onAdvance: () -> Unit) {
    val s = LocalStrings.current
    val tablet = LocalTabletDevice.current
    val demoContent = remember(pageIndex) {
        movableContentOf<Boolean> { active ->
            // Recreate only component-local transitions; retained IDs, ledgers and scroll prevent replay.
            key(active) { FeatureRecDemo(pageIndex, playback, active) }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (tablet) {
            TabletFeatureRecContent(pageIndex, active, canAdvance, colors, demoContent, onAdvance)
            return@BoxWithConstraints
        }
        val shortWindow = maxHeight < 520.dp
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .then(if (shortWindow) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(top = 20.dp, start = 22.dp, end = 22.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Text(s.featureRecTitle,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 32.sp),
                fontWeight = FontWeight.Bold, color = colors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            FeatureRecIntroduction(pageIndex, centered = true,
                nameStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
                bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp))
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth()
                .then(if (shortWindow) Modifier.height(400.dp) else Modifier.weight(1f))
                .frostedCard(null, colors, advancedMaterial = false, RoundedCornerShape(22.dp))) {
                demoContent(active)
            }
            Spacer(Modifier.height(20.dp))
            FeatureRecAction(pageIndex, canAdvance, colors, onAdvance, tablet = false)
        }
    }
}

@Composable
private fun TabletFeatureRecContent(
    pageIndex: Int,
    active: Boolean,
    canAdvance: Boolean,
    colors: FreeChatColors,
    demoContent: @Composable (Boolean) -> Unit,
    onAdvance: () -> Unit,
) {
    val s = LocalStrings.current
    BoxWithConstraints(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 28.dp)
    ) {
        val fontScale = LocalDensity.current.fontScale
        val split = maxWidth >= 900.dp && maxWidth > maxHeight && maxHeight >= 520.dp && fontScale <= 1.3f
        val cardW = if (split) minOf(420.dp, (maxWidth - 48.dp) / 2, maxHeight * 0.62f / maxOf(1f, fontScale))
            else minOf(420.dp, maxWidth)
        val cardH = cardW / 0.62f * maxOf(1f, fontScale)
        val introduction: @Composable (Boolean) -> Unit = { centered ->
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
                Text(s.featureRecTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 36.sp),
                    fontWeight = FontWeight.Bold, color = colors.TextPrimary,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start)
                Spacer(Modifier.height(12.dp))
                FeatureRecIntroduction(pageIndex, centered,
                    nameStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                    bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp))
            }
        }
        val action: @Composable () -> Unit = {
            Box(Modifier.widthIn(max = 320.dp).fillMaxWidth()) {
                FeatureRecAction(pageIndex, canAdvance, colors, onAdvance, tablet = true)
            }
        }
        val demonstration: @Composable () -> Unit = {
            Box(Modifier.widthIn(max = 420.dp).width(cardW).height(cardH)
                .frostedCard(null, colors, advancedMaterial = false, RoundedCornerShape(22.dp))) {
                demoContent(active)
            }
        }
        if (split) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { demonstration() }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    introduction(false)
                    Spacer(Modifier.height(32.dp))
                    action()
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    introduction(true)
                    Spacer(Modifier.height(24.dp))
                    demonstration()
                    Spacer(Modifier.height(24.dp))
                }
                Spacer(Modifier.height(24.dp))
                action()
            }
        }
    }
}

/** 功能名 + 简介（2026-10-28 工单）：名是名（蓝色加重），简介是简介（小字弱化），行距各管各。 */
@Composable
private fun FeatureRecIntroduction(pageIndex: Int, centered: Boolean,
    nameStyle: androidx.compose.ui.text.TextStyle, bodyStyle: androidx.compose.ui.text.TextStyle) {
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    // 2026-10-28 返修：蓝字深浅按 App 主题背景亮度挑（readableLinkBlue），不再跟系统深色——
    // 浅色主题+系统深色时旧写法对比度只剩 2.1:1
    val blue = readableLinkBlue()
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Text(if (pageIndex == 0) s.multiCharacterGroup else s.multiCharacterStory,
        style = nameStyle, fontWeight = FontWeight.SemiBold, color = blue, textAlign = align)
    Spacer(Modifier.height(4.dp))
    Text(if (pageIndex == 0) s.featureRecGroupBody else s.featureRecStoryBody,
        style = bodyStyle, color = colors.TextSecondary, textAlign = align)
}

@Composable
private fun FeatureRecAction(pageIndex: Int, canAdvance: Boolean, colors: FreeChatColors,
    onAdvance: () -> Unit, tablet: Boolean) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${pageIndex + 1} / 2", style = MaterialTheme.typography.labelMedium, color = colors.TextTertiary)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAdvance, enabled = canAdvance,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.Primary, contentColor = colors.OnPrimary,
                disabledContainerColor = colors.SurfaceVariant, disabledContentColor = colors.TextTertiary)) {
            Text(if (pageIndex == 0) s.featureRecNext else s.featureRecCta,
                fontWeight = FontWeight.SemiBold, fontSize = if (tablet) 18.sp else 16.sp)
        }
    }
}

/** 演示片段文案：虚构角色与消息，不读取对话或角色池；与原导览一样保留中文故事示例。 */
private val DEMO_MEMBERS = listOf(
    GroupMember("rec-mo", CharacterPersona(name = "林墨", personalityText = "性格直爽，想到什么说什么。")),
    GroupMember("rec-xu", CharacterPersona(name = "许舟", personalityText = "说话直接，有事说事。")),
    GroupMember("rec-zhou", CharacterPersona(name = "周屿", personalityText = "在校学生，热心但常被杂事缠身。")),
)

/** 演示台词一条：senderId=null 表示「我」。2026-10-28 用户定稿——一条一条发，同人也逐条，不并行。 */
private data class DemoLine(val senderId: String?, val text: String)
private val DEMO_GROUP_SCRIPT = listOf(
    DemoLine(null, "晚上去吃自助餐吧"),
    DemoLine(null, "轻奢一把"),
    DemoLine("rec-mo", "吃啥？"),
    DemoLine("rec-mo", "烤肉？披萨？牛排？"),
    DemoLine(null, "都行"),
    DemoLine("rec-xu", "陪对象呢没空"),
    DemoLine("rec-zhou", "去不了，导员非要让我当志愿者"),
)
private val DEMO_GROUP_LINE_STAGES = listOf(1, 2, 3, 4, 5, 6, 7)
private const val DEMO_STORY_INPUT = "深夜的旧钟楼下，三个年轻人在雨里捡到一封没有署名的信。林墨性急，撕开就要念出来；许舟谨慎，抢过来坚持先报警；周屿一言不发，蹲下指着信封上那只湿透的蓝蝴蝶。写一段群像戏：夜色、雨声、争执都要有，结尾把悬念留在蝴蝶上。要有对话，也要有每个人的小动作。"
private const val DEMO_STORY_TEXT = "雨水顺着钟楼的檐角往下淌。林墨的手指已经挑开信封一角，被许舟一把按住：“别急——万一是什么证据呢？”他压低声音，眼睛却一直望着街口闪烁的警灯。\n\n周屿蹲下身，把伞倾向那枚蓝蝴蝶。蜡印浸了雨水也不褪色，翅膀的纹路细得像拿针尖画上去的。“你们看，”他轻声说，“和上周展览丢的那枚邮票，一模一样。”\n\n三个人同时安静下来，雨声忽然变得很响。林墨把信封举到路灯下，翻来覆去地看；许舟掏出手机，对着蝴蝶连拍了几张；周屿缓缓回过头——钟楼的门不知何时开了一道缝，门后的黑暗里，似乎也有一双眼睛，正一眨不眨地看着他们。\n\n“谁也别碰。”许舟把外套脱下来罩住信封，声音发紧，“等天亮，一起交给警察。”林墨咽了咽口水，把手缩了回来。"

@Composable
private fun FeatureRecDemo(pageIndex: Int, playback: FeatureRecPlaybackState, active: Boolean) {
    val s = LocalStrings.current
    // 演示气泡深浅跟 App 主题走（2026-10-28 返修：不跟系统深色，主题与系统不一致时气泡会画反）
    val isDark = LocalFreeChatColors.current.Background.luminance() < 0.5f
    val motionOn = LocalMotionEnabled.current
    val stage = playback.stage
    val story = pageIndex == 1
    val demoScroll = playback.scroll
    val sessionId = playback.sessionId
    val entranceLedger = playback.entranceLedger
    val demoGroup = remember { GroupChatProfile(members = DEMO_MEMBERS, userNickname = "我") }.takeUnless { story }
    // 2026-10-28 工单：群聊本质是微信聊天——角色回复不做「思考球/思考进程」前奏，
    // 头像+名称+气泡整条直接弹出；文本页保留逐字+思考态的生成感演示。
    val thinking = story && stage == 2
    val thinkingMs = if (thinking) playback.stageElapsedMs else 0L
    val thinkPhase = when {
        thinkingMs < 450 -> GenerationPhase.CONNECTING
        thinkingMs < 900 -> GenerationPhase.UNDERSTANDING
        else -> GenerationPhase.DRAFTING
    }
    LaunchedEffect(stage, active, motionOn) {
        if (!active || playback.followedStage == stage) return@LaunchedEffect
        playback.followedStage = stage
        withFrameNanos { }
        if (stage >= DEMO_LAST_STAGE) {
            if (motionOn) demoScroll.animateScrollTo(demoScroll.maxValue,
                tween(FreeChatAnimation.DURATION_MESSAGE, easing = FreeChatAnimation.arrivalEase))
            else demoScroll.scrollTo(demoScroll.maxValue)
        } else {
            snapshotFlow { demoScroll.maxValue }.collectLatest { bottom ->
                if (motionOn) demoScroll.animateScrollTo(bottom,
                    tween(FreeChatAnimation.DURATION_MESSAGE, easing = FreeChatAnimation.arrivalEase))
                else demoScroll.scrollTo(bottom)
            }
        }
    }

    val typography = MaterialTheme.typography
    MaterialTheme(typography = typography.copy(bodyMedium =
        if (LocalTabletDevice.current) typography.bodyLarge else typography.bodyMedium)) {
        // 2026-10-28 工单「尽量一页装下」：条距/留白收紧（7 条微信式短句整段同屏）
        Column(Modifier.fillMaxSize().verticalScroll(demoScroll, enabled = active)
            .padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (story) {
                if (stage >= 1) {
                    val userMessage = playback.messages.getOrPut(-1) {
                        Message(id = "${sessionId}_user", role = Role.USER, mode = ChatMode.COMPANION,
                            content = DEMO_STORY_INPUT)
                    }
                    FeatureRecMessage(userMessage, entranceLedger) { modifier ->
                        // 「我」的头像只用默认灰（2026-10-28 工单）：演示不暴露账号真头像
                        ChatBubble(message = userMessage, isDark = isDark, modifier = modifier, groupChat = demoGroup,
                            useDefaultUserAvatar = true)
                    }
                }
                val arrivalStage = 2
                if (stage >= arrivalStage) {
                    val pending = story && stage == arrivalStage
                    val message = playback.messages.getOrPut(0) {
                        Message(id = "${sessionId}_reply_0", role = Role.ASSISTANT,
                            content = "", mode = ChatMode.COMPANION, groupMemberId = null)
                    }.copy(content = if (pending) "" else DEMO_STORY_TEXT, isStreaming = pending)
                    FeatureRecMessage(message, entranceLedger) { modifier ->
                        ChatBubble(message = message, isDark = isDark, modifier = modifier,
                            groupChat = demoGroup, textFlow = story, emphasizeDialogue = story,
                            generationLabel = if (pending) s.generationPhaseLabels[thinkPhase] else null,
                            liveThinkingMs = if (pending) thinkingMs else 0L)
                    }
                }
            } else {
                // 群聊页（2026-10-28 用户定稿台词）：微信式短句连发，同拍一起弹、整条入场，无思考前奏
                DEMO_GROUP_SCRIPT.forEachIndexed { index, line ->
                    key(index) {
                        if (stage >= DEMO_GROUP_LINE_STAGES[index]) {
                            val message = playback.messages.getOrPut(index) {
                                Message(id = "${sessionId}_line_$index",
                                    role = if (line.senderId == null) Role.USER else Role.ASSISTANT,
                                    mode = ChatMode.COMPANION, content = line.text,
                                    groupMemberId = line.senderId)
                            }
                            FeatureRecMessage(message, entranceLedger) { modifier ->
                                ChatBubble(message = message, isDark = isDark, modifier = modifier,
                                    groupChat = demoGroup, useDefaultUserAvatar = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRecMessage(message: Message, ledger: MessageEntranceLedger,
    content: @Composable (Modifier) -> Unit) {
    val entrance = rememberMessageEntrance(message.id, message.timestamp, ledger)
    val rise = with(LocalDensity.current) { MotionPolicy.MessageRiseDp.dp.toPx() }
    content(Modifier.messageEntrance(entrance, rise))
}
