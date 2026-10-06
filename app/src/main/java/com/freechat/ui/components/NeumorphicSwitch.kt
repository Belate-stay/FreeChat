package com.freechat.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.materialProgress
import com.freechat.ui.theme.neumorphShadow
import com.freechat.ui.theme.neumorphControlTone
import com.freechat.ui.theme.neumorphicFace
import com.freechat.ui.theme.softShadow

/** 同色凹槽、凸起滑钮；保持原有开关语义与至少 48dp 的点击高度。 */
@Composable
fun NeumorphicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: SwitchColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalFreeChatColors.current
    val material = materialProgress()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val position by animateDpAsState(if (checked) 25.dp else 3.dp,
        FreeChatAnimation.controlPositionSpring, label = "switch_thumb")
    val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f,
        if (pressed) FreeChatAnimation.pressDown else FreeChatAnimation.pressRelease, label = "switch_press")
    val checkAlpha by animateFloatAsState(if (checked) 1f else 0f,
        FreeChatAnimation.controlTween, label = "switch_check")
    val track by animateColorAsState(if (checked) colors.checkedTrackColor else colors.uncheckedTrackColor,
        animationSpec = FreeChatAnimation.controlSpec(), label = "switch_track")
    val thumb by animateColorAsState(if (checked) colors.checkedThumbColor else colors.uncheckedThumbColor,
        animationSpec = FreeChatAnimation.controlSpec(), label = "switch_color")
    val trackShape = RoundedCornerShape(15.dp)
    val trackTone = remember(track) { neumorphControlTone(track, raised = false) }
    val thumbTone = remember(thumb) { neumorphControlTone(thumb, raised = true) }
    Box(
        modifier.width(52.dp).height(48.dp)
            // 不使用未裁剪的默认矩形 indication；滑钮按压/位移动画提供点击反馈。
            .toggleable(checked, enabled = enabled, role = Role.Switch,
                interactionSource = source, indication = null, onValueChange = onCheckedChange)
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.width(52.dp).height(30.dp)
                .softShadow(trackShape, dy = 1.dp, spread = 3.dp, maxAlpha = 0.025f,
                    strength = { 1f - material() })
                .clip(trackShape)
                .neumorphicFace(trackTone, track, material, inheritLiquid = false)
                .neumorphShadow(trackShape, trackTone, recessed = true, strength = material)
                .drawWithCache {
                val opacity = material()
                val lighting = Brush.linearGradient(listOf(
                    Color.White.copy(alpha = 0.08f), Color.Transparent, Color.Black.copy(alpha = 0.04f)
                ))
                val edge = Brush.linearGradient(listOf(
                    Color.White.copy(alpha = 0.36f), Color.White.copy(alpha = 0.08f)
                ))
                val stroke = 0.7.dp.toPx()
                onDrawBehind {
                    drawRect(lighting, alpha = opacity)
                    drawRoundRect(edge, topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(15.dp.toPx() - stroke / 2f),
                        style = Stroke(stroke), alpha = opacity)
                }
            }
        ) {
            Box(
                Modifier.align(Alignment.CenterStart).offset { IntOffset(position.roundToPx(), 0) }.size(24.dp)
                    .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                    .neumorphShadow(CircleShape, thumbTone, recessed = false, strength = material)
                    .clip(CircleShape)
                    .neumorphicFace(thumbTone, thumb, material, inheritLiquid = false),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = theme.Primary,
                    modifier = Modifier.size(14.dp).graphicsLayer { alpha = checkAlpha })
            }
        }
    }
}
