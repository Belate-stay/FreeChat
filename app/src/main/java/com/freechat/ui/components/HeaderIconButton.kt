package com.freechat.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.freechat.ui.animation.pressMotion
import com.freechat.ui.animation.MotionPolicy
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.LocalHeaderCardProgress
import com.freechat.ui.theme.headerCardStrength
import com.freechat.ui.theme.materialProgress
import com.freechat.ui.theme.neumorphShadow
import com.freechat.ui.theme.neumorphTone
import com.freechat.ui.theme.neumorphicFace

/** 48dp 命中区；卡片样式为 34dp 圆面，镂空/普通材质只有图标，命中区和位置不变。 */
@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    HeaderActionButton(onClick, modifier, enabled, icon = true, content)
}

/** Text actions use the same 34dp material face and 48dp touch target as header icons. */
@Composable
fun HeaderTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    HeaderActionButton(onClick, modifier, enabled, icon = false, content)
}

@Composable
private fun HeaderActionButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = LocalFreeChatColors.current
    val progress = materialProgress()
    val cardProgress = LocalHeaderCardProgress.current
    // Read animation state only in the drawing modifiers, not by recomposing every page per frame.
    val backingProgress = { headerCardStrength(progress(), cardProgress?.value ?: 1f) }
    val source = remember { MutableInteractionSource() }
    val tone = remember(colors) {
        neumorphTone(colors, colors.TextPrimary.luminance() > 0.5f, false, compact = true)
            .copy(offset = 1.25.dp, blur = 2.5.dp)
    }
    val faceShape = if (icon) CircleShape else RoundedCornerShape(17.dp)
    Box(
        modifier
            .then(if (icon) Modifier.size(48.dp) else Modifier.heightIn(min = 48.dp).padding(horizontal = 7.dp))
            .clip(if (icon) CircleShape else RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, interactionSource = source, indication = null,
                role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.then(if (icon) Modifier.size(34.dp) else Modifier.defaultMinSize(minWidth = 34.dp, minHeight = 34.dp))
                .pressMotion(source, MotionPolicy.HeaderPressScale)
                .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                .neumorphShadow(faceShape, tone, recessed = false, strength = backingProgress)
                .clip(faceShape)
                .neumorphicFace(tone, Color.Transparent, backingProgress)
                .then(if (icon) Modifier else Modifier.padding(horizontal = 12.dp, vertical = 4.dp)),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
