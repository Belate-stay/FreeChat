package com.freechat.ui.adaptive

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freechat.i18n.LocalStrings
import com.freechat.ui.animation.LocalPageActive
import com.freechat.ui.components.HeaderIconButton
import com.freechat.ui.theme.LocalFreeChatColors

internal class FloatingWindowState {
    var dismiss: () -> Unit = {}
    var closeGuard: ((() -> Unit) -> Unit)? = null
    fun requestClose() = requestNavigation(dismiss)
    fun requestNavigation(action: () -> Unit) { closeGuard?.invoke(action) ?: action() }
}

internal val LocalFloatingWindow = staticCompositionLocalOf<FloatingWindowState?> { null }
internal val LocalFloatingWindowRoot = staticCompositionLocalOf { false }
/** Embedded tablet categories can also leave an unsaved form without pressing Back. */
internal val LocalFormNavigationGuard = staticCompositionLocalOf<FloatingWindowState?> { null }

/** One close target belongs to the window, not to its current nested page. */
@Composable
internal fun BoxScope.FloatingWindowChrome(window: FloatingWindowState) {
    val s = LocalStrings.current
    val colors = LocalFreeChatColors.current
    HeaderIconButton(onClick = window::requestClose,
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.8.dp),
        enabled = LocalPageActive.current) {
        Icon(Icons.Filled.Close, s.close, tint = colors.TextSecondary)
    }
}

/** Guard the active form only; an outgoing animated page must not own the X callback. */
@Composable
internal fun FloatingWindowCloseHandler(onClose: (() -> Unit) -> Unit) {
    val window = LocalFloatingWindow.current ?: LocalFormNavigationGuard.current
    val active = LocalPageActive.current
    val latest = rememberUpdatedState(onClose)
    DisposableEffect(window, active) {
        val guard: (() -> Unit) -> Unit = { action -> latest.value(action) }
        if (window != null && active) window.closeGuard = guard
        onDispose { if (window?.closeGuard === guard) window.closeGuard = null }
    }
}

@Composable
internal fun FloatingWindowBackButton(
    onClick: () -> Unit,
    root: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (LocalFloatingWindow.current != null && LocalFloatingWindowRoot.current && root)
        Spacer(Modifier.width(18.dp).height(48.dp))
    else HeaderIconButton(onClick = onClick, content = content)
}

/** Reserve the same 48dp touch target on every nested header without narrowing its body. */
@Composable
internal fun Modifier.floatingHeaderInset(): Modifier =
    if (LocalFloatingWindow.current != null) padding(end = 48.dp) else this
