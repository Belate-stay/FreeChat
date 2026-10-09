package com.freechat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.freechat.ui.animation.LocalMotionEnabled
import kotlinx.coroutines.isActive

/** Read the returned state in drawing, not composition: a frame never remeasures the chat. */
@Composable
internal fun rememberRenderSeconds(active: Boolean = true): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val motionEnabled = LocalMotionEnabled.current
    LaunchedEffect(lifecycle, motionEnabled, active) {
        if (active && motionEnabled) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previous = 0L
                while (isActive) {
                    withFrameNanos { now ->
                        if (previous != 0L) seconds.floatValue +=
                            ((now - previous) / 1_000_000_000f).coerceAtMost(.05f)
                        previous = now
                    }
                }
            }
        }
    }
    return seconds
}
