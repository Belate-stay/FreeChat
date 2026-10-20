package com.freechat.ui.screens

import com.freechat.ui.adaptive.pageActionWidth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freechat.data.CharacterPresentationPolicy
import com.freechat.i18n.LocalStrings
import com.freechat.model.DialogueMode
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.animation.rememberTopOnEntryScrollState
import com.freechat.ui.animation.MotionButton
import com.freechat.ui.components.HeaderIconButton
import com.freechat.ui.components.TopBarBackdrop
import com.freechat.ui.components.TopBarBackdropSource
import com.freechat.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** A separate page with its own scroll position and title, not a section of the role form. */
@Composable
internal fun DialogueModeScreen(
    mode: Int?,
    plotLength: Int,
    onSelect: (Int) -> Unit,
    onPlotLengthChange: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onImport: () -> Unit,
    hazeState: HazeState,
) {
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    val advanced = LocalAdvancedMaterial.current
    val statusHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BackHandler(enabled = com.freechat.ui.animation.LocalPageActive.current, onBack = onBack)
    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        TopBarBackdropSource(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusHeight))
        Column(
            Modifier.fillMaxSize()
                .then(if (advanced) Modifier.hazeSource(hazeState).hazeBackground(colors.Background) else Modifier)
                .verticalScroll(rememberTopOnEntryScrollState())
                .padding(top = HazeSpec.topContentPaddingDp(statusHeight, 24.dp), bottom = 32.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(s.dialogueModeDesc, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
            DialogueModeOptions(mode, onSelect, hazeState)
            AnimatedVisibility(mode != null, enter = FreeChatAnimation.expandEnter(), exit = FreeChatAnimation.expandExit()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AnimatedContent(mode, transitionSpec = { FreeChatAnimation.contentReplacement() }, label = "mode_example") { selected ->
                        Column(Modifier.fillMaxWidth().background(colors.SurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.plotExampleTitle, style = MaterialTheme.typography.labelLarge, color = colors.TextPrimary)
                            if (selected == DialogueMode.WECHAT) {
                                CharacterPresentationPolicy.wechatExampleOrder.forEach { (user, index) ->
                                    (if (user) s.wechatExampleUser else s.wechatExampleAi).getOrNull(index)?.let {
                                        ChatExampleBubble(it, fromUser = user, colors = colors)
                                    }
                                }
                            } else if (selected != null) {
                                ChatExampleBubble(if (selected == DialogueMode.PLOT) s.plotExampleUser else s.actionExampleUser,
                                    fromUser = true, colors = colors, withLabel = true)
                                ChatExampleBubble(if (selected == DialogueMode.PLOT) s.plotExampleAi else s.actionExampleAi,
                                    fromUser = false, colors = colors, withLabel = true)
                            }
                        }
                    }
                    AnimatedVisibility(CharacterPresentationPolicy.usesImageGeneration(mode),
                        enter = FreeChatAnimation.expandEnter(), exit = FreeChatAnimation.expandExit()) {
                        PlotLengthCard(plotLength, onPlotLengthChange, hazeState, advanced, colors)
                    }
                    Text(s.dialogueModePickWarn, style = MaterialTheme.typography.bodySmall,
                        color = colors.TextSecondary, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            MotionButton(onClick = onNext, enabled = mode != null, modifier = Modifier.pageActionWidth().heightIn(min = 50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.Primary, contentColor = colors.OnPrimary),
                shape = RoundedCornerShape(25.dp)) {
                Text(s.modeNext, style = MaterialTheme.typography.titleMedium)
            }
        }
        TopBarBackdrop(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusHeight))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.8.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back, tint = colors.TextPrimary) }
            Text(s.chooseDialogueMode, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, color = colors.TextPrimary)
            HeaderIconButton(onClick = onImport) { Icon(Icons.Filled.Upload, s.importCharacter, tint = colors.TextSecondary) }
            com.freechat.ui.adaptive.PaneExpandButton()
        }
    }
}

@Composable
internal fun DialogueModeOptions(mode: Int?, onSelect: (Int) -> Unit, hazeState: HazeState) {
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    val advanced = LocalAdvancedMaterial.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            Triple(DialogueMode.WECHAT, s.dialogueModeWechat, s.dialogueModeWechatDesc),
            Triple(DialogueMode.ACTION, s.dialogueModeAction, s.dialogueModeActionDesc),
            Triple(DialogueMode.PLOT, s.dialogueModePlot, s.dialogueModePlotDesc),
        ).forEach { (value, title, description) ->
            val selected = mode == value
            // 选中态过渡动画（1.1.28）：淡主题色铺面叠在卡面上渐染，标题色同步渐变，不再硬切
            val tint by androidx.compose.animation.animateColorAsState(
                if (selected) colors.Primary.copy(alpha = 0.16f) else Color.Transparent,
                com.freechat.ui.animation.FreeChatAnimation.colorTween, label = "modeTint")
            val titleColor by androidx.compose.animation.animateColorAsState(
                if (selected) colors.Primary else colors.TextPrimary,
                com.freechat.ui.animation.FreeChatAnimation.colorTween, label = "modeTitle")
            Column(Modifier.fillMaxWidth()
                .frostedCard(hazeState, colors, advanced, RoundedCornerShape(16.dp),
                    fallback = colors.SurfaceVariant)
                .background(tint, RoundedCornerShape(16.dp))
                .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton,
                    onClick = { onSelect(value) })
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = titleColor)
                Text(description, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
            }
        }
    }
}
