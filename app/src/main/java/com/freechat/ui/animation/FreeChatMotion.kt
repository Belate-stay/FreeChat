package com.freechat.ui.animation

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val LocalMotionEnabled = staticCompositionLocalOf { true }

/** Also stops hand-written frame loops when Android's remove-animations preference changes. */
@Composable
fun rememberMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }.getOrDefault(true)
    var enabled by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        enabled = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

/** No rectangle/ripple leaks, no layout changes, no per-frame recomposition of the button. */
@Composable
fun Modifier.pressMotion(source: InteractionSource, pressedScale: Float = MotionPolicy.PressScale): Modifier {
    val pressed by source.collectIsPressedAsState()
    val enabled = LocalMotionEnabled.current
    val scale = animateFloatAsState(if (pressed && enabled) pressedScale else 1f,
        if (pressed) FreeChatAnimation.pressDown else FreeChatAnimation.pressRelease, label = "control_press")
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/** All foundation clickable/combinedClickable controls share press/release feedback. */
data class MotionIndication(val enabled: Boolean, val focusInk: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource, enabled, focusInk)
}

private class PressNode(private val source: InteractionSource, private val enabled: Boolean,
    private val focusInk: Color) : Modifier.Node(), DrawModifierNode {
    private val scale = Animatable(1f)
    private var focused = false
    override fun onAttach() {
        coroutineScope.launch {
            scale.snapTo(1f)
            val presses = mutableSetOf<PressInteraction.Press>()
            source.interactions.map { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses.add(interaction)
                    is PressInteraction.Release -> presses.remove(interaction.press)
                    is PressInteraction.Cancel -> presses.remove(interaction.press)
                    is FocusInteraction.Focus -> { focused = true; invalidateDraw() }
                    is FocusInteraction.Unfocus -> { focused = false; invalidateDraw() }
                }
                presses.isNotEmpty()
            }.distinctUntilChanged().collectLatest { pressed ->
                if (!enabled) scale.snapTo(1f)
                else scale.animateTo(if (pressed) MotionPolicy.PressScale else 1f,
                    if (pressed) FreeChatAnimation.pressDown else FreeChatAnimation.pressRelease)
            }
        }
    }
    override fun ContentDrawScope.draw() {
        scale(scale.value, scale.value) { this@draw.drawContent() }
        if (focused) drawRoundRect(focusInk, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f * density),
            style = Stroke(2f * density))
    }
    override fun onDetach() { focused = false }
}

@Composable
fun rememberMessageEntrance(id: String, timestamp: Long, ledger: MessageEntranceLedger): State<Float> {
    val motion = LocalMotionEnabled.current
    val fresh = remember(id, ledger) { ledger.claim(id, timestamp, System.currentTimeMillis()) }
    val progress = remember(id, ledger) { Animatable(if (fresh && motion) 0f else 1f) }
    LaunchedEffect(id, ledger, motion) {
        if (!motion) progress.snapTo(1f)
        else if (fresh) progress.animateTo(1f, FreeChatAnimation.messageEnterTween)
    }
    return progress.asState()
}

fun Modifier.messageEntrance(progress: State<Float>, distancePx: Float) = graphicsLayer {
    // Fade without allocating a bounded offscreen texture: the orb's feathered glow
    // and a card's soft shadow must not become a rectangle during the first frames.
    compositingStrategy = CompositingStrategy.ModulateAlpha
    alpha = MotionPolicy.messageAlpha(progress.value)
    translationY = MotionPolicy.messageTranslation(progress.value, distancePx)
}
