package com.freechat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import com.freechat.ui.animation.MotionTextButton as TextButton
import com.freechat.ui.animation.MotionIconButton as IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.freechat.data.SearchConfig
import com.freechat.data.SearchPipeline
import com.freechat.data.SearchProvider
import com.freechat.i18n.LocalStrings
import com.freechat.ui.components.SheetOption
import com.freechat.ui.components.SheetPanel
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.theme.FreeChatColors
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

@Composable
internal fun BoxScope.SearchSettingsPanel(
    visible: Boolean,
    saved: SearchConfig, onSave: suspend (SearchConfig) -> Unit, onDismiss: () -> Unit,
    colors: FreeChatColors, isDark: Boolean, advancedMaterial: Boolean, hazeState: HazeState
) {
    val s = LocalStrings.current
    var draft by remember { mutableStateOf(saved) }
    var revealKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var testJob by remember { mutableStateOf<Job?>(null) }
    // 常驻外壳让 SheetPanel 完成退场；打开时从已保存配置开始，不保留未保存的草稿。
    LaunchedEffect(visible) {
        if (visible) {
            draft = saved; revealKey = false; status = ""; failed = false
        } else {
            testJob?.cancelAndJoin()
            testJob = null
        }
    }
    val error = draft.validationError()
    SheetPanel(visible, onDismiss, s.searchSource, colors, isDark, advancedMaterial, hazeState,
        confirmLabel = s.save, confirmEnabled = error == null && !busy, maxContentHeight = 560.dp,
        onConfirm = {
            scope.launch {
                busy = true
                try { onSave(draft); onDismiss() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { status = s.searchSaveFailed; failed = true }
                finally { busy = false }
            }
        }) {
        Text(s.searchSourceDesc, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        SearchProvider.entries.forEach { provider ->
            SheetOption(title = if (provider == SearchProvider.FREE) s.searchFree else provider.label,
                selected = draft.provider == provider, colors = colors, onClick = {
                    if (!busy && provider != draft.provider) {
                        // Never reuse a provider's secret for another host or protocol.
                        draft = if (provider == saved.provider) saved else SearchConfig(provider, provider.defaultUrl)
                        status = ""; revealKey = false
                    }
                })
        }
        AnimatedVisibility(visible = draft.provider != SearchProvider.FREE,
            enter = FreeChatAnimation.expandEnter(), exit = FreeChatAnimation.expandExit()) {
          Column {
            Spacer(Modifier.height(8.dp))
            if (draft.provider == SearchProvider.ANYSEARCH) {
                Text(s.searchAnySearchDesc, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(draft.endpoint, { draft = draft.copy(endpoint = it); status = "" },
                label = { Text(s.searchEndpoint) }, singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(draft.apiKey, { draft = draft.copy(apiKey = it); status = "" },
                label = { Text(if (draft.provider in listOf(SearchProvider.ANYSEARCH, SearchProvider.SEARXNG, SearchProvider.FIRECRAWL)) s.searchOptionalKey else "API Key") },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { revealKey = !revealKey }) {
                    Icon(if (revealKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "API Key")
                } })
            if (error != null && draft.endpoint.isNotBlank()) Text(error, color = colors.ErrorRed, style = MaterialTheme.typography.bodySmall)
          }
        }
        Spacer(Modifier.height(12.dp))
        Text(s.searchTestCost, color = colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = !busy && error == null, onClick = {
            testJob = scope.launch {
                busy = true; status = ""
                val started = System.nanoTime()
                try {
                    SearchPipeline.clearCache()
                    val result = SearchPipeline.search("Kotlin Android", config = draft)
                    failed = result.entries.isEmpty()
                    status = if (failed) result.notice else "${s.searchTestOk} · ${result.entries.size} · ${"%.1f".format((System.nanoTime() - started) / 1e9)} s"
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { failed = true; status = s.searchSaveFailed }
                finally { busy = false }
            }
        }) { Text(if (busy) s.searchTesting else s.searchTest) }
        if (status.isNotBlank()) Text(status, color = if (failed) colors.ErrorRed else colors.TextPrimary,
            style = MaterialTheme.typography.bodySmall)
    }
}
