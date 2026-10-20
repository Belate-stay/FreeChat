package com.freechat.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxWithConstraints as PlatformBoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog as PlatformDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup as PlatformPopup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.freechat.model.FontSize
import kotlin.math.roundToInt

/** Scale text and dp geometry together; never change the device's display metrics. */
fun appDisplayDensity(system: Density, fontSize: FontSize): Density =
    Density(system.density * fontSize.scale, fontScale = 1f)

fun appDisplayDp(systemDp: Int, fontSize: FontSize): Int =
    (systemDp / fontSize.scale).roundToInt()

/** Pixel constraints can stay unchanged during a live display-size change. Do not reuse old dp. */
@Composable
fun AppBoxWithConstraints(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    val density = LocalDensity.current
    PlatformBoxWithConstraints(modifier, contentAlignment, propagateMinConstraints) {
        val scope = remember(this, density) { AppBoxConstraintsScope(this, density) }
        scope.content()
    }
}

internal class AppBoxConstraintsScope(
    private val scope: BoxWithConstraintsScope,
    private val density: Density,
) : BoxWithConstraintsScope by scope {
    override val minWidth: Dp get() = with(density) { constraints.minWidth.toDp() }
    override val minHeight: Dp get() = with(density) { constraints.minHeight.toDp() }
    override val maxWidth: Dp get() = with(density) {
        if (constraints.hasBoundedWidth) constraints.maxWidth.toDp() else Dp.Infinity
    }
    override val maxHeight: Dp get() = with(density) {
        if (constraints.hasBoundedHeight) constraints.maxHeight.toDp() else Dp.Infinity
    }
}

/** Native window roots reset platform locals; restore the caller's app scale inside them. */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    PlatformDialog(onDismissRequest, properties) {
        CompositionLocalProvider(LocalDensity provides density, LocalConfiguration provides configuration,
            content = content)
    }
}

@Composable
fun AppPopup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    PlatformPopup(popupPositionProvider, onDismissRequest, properties) {
        CompositionLocalProvider(LocalDensity provides density, LocalConfiguration provides configuration,
            content = content)
    }
}

/** Retain the existing Material confirmation slots without creating an unscaled inner window. */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    AppDialog(onDismissRequest) {
        Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor, tonalElevation = AlertDialogDefaults.TonalElevation) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
                }
                Spacer(Modifier.height(16.dp))
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp,
                    androidx.compose.ui.Alignment.End)) {
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}
