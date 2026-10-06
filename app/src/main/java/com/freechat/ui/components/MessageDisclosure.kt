package com.freechat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freechat.i18n.LocalStrings
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFontScale
import com.freechat.ui.theme.LocalFreeChatColors

@Stable
class DisclosureCallbacks(
    val onToggle: (String, Int, Float, Boolean) -> Unit = { _, _, _, _ -> },
    val onResize: (String, Int) -> Unit = { _, _ -> },
    val onSettled: (String) -> Unit = {},
) {
    companion object { val None = DisclosureCallbacks() }
}

private class DisclosureGeometry { var height = 0; var headerTop = 0f }

/** 思考过程与信息源共用容器、箭头、显隐动画及可访问状态；布局锚定由聊天视口处理。 */
@Composable
fun MessageDisclosure(
    id: String,
    title: String,
    initiallyExpanded: Boolean,
    callbacks: DisclosureCallbacks = DisclosureCallbacks.None,
    live: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalFreeChatColors.current
    val advanced = LocalAdvancedMaterial.current
    val s = LocalStrings.current
    val scale = LocalFontScale.current
    val geometry = remember(id) { DisclosureGeometry() }
    var expanded by rememberSaveable(id) { mutableStateOf(initiallyExpanded) }
    val transition = remember(id) { MutableTransitionState(expanded) }
    transition.targetState = expanded
    val arrowRotation = animateFloatAsState(if (expanded) 180f else 0f,
        FreeChatAnimation.controlTween, label = "disclosure_arrow")
    LaunchedEffect(transition.isIdle, transition.currentState, expanded) {
        if (transition.isIdle && transition.currentState == expanded) {
            // With system animations disabled the transition becomes idle during composition,
            // before its final size is measured. Keep the anchor until that layout has run.
            // Two frame boundaries also let requestScrollToItem apply its pending remeasure.
            withFrameNanos { }
            withFrameNanos { }
            callbacks.onSettled(id)
        }
    }
    val shape = RoundedCornerShape(if (live) 12.dp else 10.dp)
    // 保留原有小标题和半透明磨砂卡片；只有箭头/展开时间线接入全局动画。
    val surface = if (advanced) colors.Surface.copy(alpha = 0.7f)
        else if (live) colors.Primary.copy(alpha = 0.1f) else colors.SurfaceVariant.copy(alpha = 0.6f)
    val card = Modifier.clip(shape).background(surface)
        .then(if (advanced) Modifier.border(1.dp, colors.Divider.copy(alpha = 0.3f), shape) else Modifier)
    Box(Modifier.fillMaxWidth().padding(horizontal = if (live) 8.dp else 0.dp,
        vertical = if (live) 4.dp else 2.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = (340 * scale).dp).fillMaxWidth()
            .onSizeChanged { geometry.height = it.height; callbacks.onResize(id, it.height) }
            .then(if (live) Modifier else card)) {
            Row(Modifier.fillMaxWidth()
                .then(if (live) card else Modifier)
                .onGloballyPositioned { geometry.headerTop = it.positionInRoot().y }
                .semantics { stateDescription = if (expanded) s.disclosureExpanded else s.disclosureCollapsed }
                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                    callbacks.onToggle(id, geometry.height, geometry.headerTop, !expanded)
                    expanded = !expanded
                }
                .padding(horizontal = if (live) 12.dp else 10.dp, vertical = if (live) 8.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold, color = colors.Primary)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.KeyboardArrowDown,
                    null, tint = if (live) colors.Primary else colors.TextTertiary,
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = arrowRotation.value })
            }
            AnimatedVisibility(visibleState = transition,
                enter = FreeChatAnimation.disclosureEnter(), exit = FreeChatAnimation.disclosureExit()) {
                Column(if (live) Modifier.fillMaxWidth().padding(top = 4.dp).clip(shape)
                    .background(colors.SurfaceVariant.copy(alpha = 0.35f)).padding(12.dp)
                    else Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
        }
    }
}
