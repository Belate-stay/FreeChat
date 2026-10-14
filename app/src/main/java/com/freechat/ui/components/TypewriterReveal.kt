package com.freechat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.Text
import com.freechat.ui.animation.MotionPolicy

/**
 * 拟人叙事档（动作演绎/剧情补足）AI 回复的逐字显现（Beta 1.1.26）。
 * 只改「文字出现的过渡」：全量样式先算好再按字截取，最终态＝全量原文原样，显示零差异；
 * 标准模式的真流式与微信档的分条冒泡都不走这里。
 */
object TypewriterPolicy {
    /** 用户口径：1 秒 30 个字。 */
    const val CHARS_PER_SECOND = 50 // 1.1.29.1 ④：用户点名提速（30 太慢）

    fun durationMs(codePoints: Int): Long = codePoints * 1000L / CHARS_PER_SECOND

    fun visibleCodePoints(progress: Float, totalCodePoints: Int): Int =
        (totalCodePoints * progress.coerceIn(0f, 1f)).toInt().coerceIn(0, totalCodePoints)

    /** code point 序号 → UTF-16 偏移。emoji 是代理对，按 char 截会出半截乱码。 */
    fun utf16End(text: String, codePoints: Int): Int {
        val total = text.codePointCount(0, text.length)
        return if (codePoints >= total) text.length
        else text.offsetByCodePoints(0, codePoints.coerceAtLeast(0))
    }
}

/** 一次性账本（同 MessageEntranceLedger 纪律）：每条消息只演一次，取消/离屏/回看都不重播。 */
object TypewriterLedger {
    private val seen = java.util.Collections.synchronizedSet(HashSet<String>())

    fun claim(id: String, timestampMs: Long, nowMs: Long): Boolean {
        if (!seen.add(id)) return false
        if (com.freechat.ui.animation.AnimationReplay.consume(id)) return true
        val age = nowMs - timestampMs
        return age in 0..MotionPolicy.MessageFreshnessMs
    }
}

/** 逐字显现进度 0..1；不该演（老消息/回看/减动效）时恒 1f 整段立显。 */
@Composable
fun rememberTypewriterReveal(messageId: String, timestampMs: Long, text: String, enabled: Boolean): Float {
    var progress by remember(messageId) { mutableStateOf(1f) }
    LaunchedEffect(messageId, enabled, text) {
        if (!enabled || text.isEmpty()) {
            progress = 1f
            return@LaunchedEffect
        }
        val total = text.codePointCount(0, text.length)
        if (!TypewriterLedger.claim(messageId, timestampMs, System.currentTimeMillis())) {
            progress = 1f
            return@LaunchedEffect
        }
        progress = 0f
        try {
            animate(0f, 1f,
                animationSpec = tween(TypewriterPolicy.durationMs(total).toInt().coerceAtLeast(1),
                    easing = LinearEasing)) { value, _ -> progress = value }
        } finally {
            // 中断（离屏/换态）也落终态：内容永远完整，只是不再演。
            progress = 1f
        }
    }
    return progress
}

/** 拟人叙事档正文：样式全量算好再截取，引语/高亮 span 显现途中不跳变。 */
@Composable
fun TypewriterText(
    text: AnnotatedString,
    messageId: String,
    timestampMs: Long,
    enabled: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val reveal = rememberTypewriterReveal(messageId, timestampMs, text.text, enabled)
    val shown = if (reveal >= 1f) text else text.subSequence(
        0, TypewriterPolicy.utf16End(text.text,
            TypewriterPolicy.visibleCodePoints(reveal, text.text.codePointCount(0, text.text.length)))
    )
    Text(shown, style = textStyle, color = color, modifier = modifier)
}
