package com.freechat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.Provider
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.viewmodel.ChatViewModel
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** 添加 / 编辑用户自定义模型页（5 类模型通用） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelEditorScreen(
    viewModel: ChatViewModel,
    modelType: ModelType,
    editing: ModelInfo?,
    isDark: Boolean,
    onBack: () -> Unit
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val density = LocalDensity.current

    val isAdd = editing == null

    var name by remember(editing) { mutableStateOf(editing?.displayName ?: "") }
    var apiKey by remember(editing) { mutableStateOf(editing?.apiKey ?: "") }
    var modelId by remember(editing) { mutableStateOf(editing?.id ?: "") }
    var apiUrl by remember(editing) { mutableStateOf(editing?.apiBaseUrl ?: "") }
    var note by remember(editing) { mutableStateOf(editing?.description ?: "") }

    val requiredFilled = apiKey.isNotBlank() && modelId.isNotBlank() && apiUrl.isNotBlank()
    val hasChanges = name != (editing?.displayName ?: "") ||
        apiKey != (editing?.apiKey ?: "") ||
        modelId != (editing?.id ?: "") ||
        apiUrl != (editing?.apiBaseUrl ?: "") ||
        note != (editing?.description ?: "")

    var showUnsaved by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    fun tryBack() {
        if (hasChanges) showUnsaved = true else onBack()
    }
    BackHandler(enabled = true) { tryBack() }

    fun save() {
        val m = ModelInfo(
            id = modelId.trim(),
            displayName = name.trim().ifBlank { modelId.trim() },
            provider = Provider.CUSTOM,
            description = note.trim(),
            supportsWebSearch = modelType == ModelType.LANGUAGE,
            modelType = modelType,
            supportsThinking = modelType == ModelType.LANGUAGE,
            apiBaseUrl = apiUrl.trim(),
            apiKey = apiKey.trim(),
            isBuiltIn = false
        )
        if (isAdd) viewModel.addCustomModel(m) else viewModel.updateCustomModel(editing!!.id, editing.modelType, m)
        onBack()
    }

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp).toPx() }

    Box(Modifier.fillMaxSize().background(colors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).background(colors.Background) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(top = statusBarHeightDp + titleBarAreaDp + 16.dp, bottom = 40.dp)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EditorField(s.modelName, s.modelNameHint, name, { name = it }, colors)
            EditorField(s.apiKeyLabel, s.apiKeyHint, apiKey, { apiKey = it }, colors, isPassword = true)
            EditorField(s.modelIdLabel, s.modelIdHint, modelId, { modelId = it }, colors)
            EditorField(s.apiUrlLabel, s.apiUrlHint, apiUrl, { apiUrl = it }, colors)
            EditorField(s.modelNote, s.modelNoteHint, note, { note = it }, colors)

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { save() },
                enabled = requiredFilled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.Primary,
                    contentColor = colors.OnPrimary,
                    disabledContainerColor = colors.SurfaceVariant,
                    disabledContentColor = colors.TextTertiary
                )
            ) {
                Text(s.saveModel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }

        // 顶部标题栏（高级材质真模糊 + 渐变渐隐）
        if (advancedMaterial) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp + HazeSpec.TopFadeZoneDp)
                    .hazeEffect(state = hazeState) {
                        blurRadius = HazeSpec.TopBlurRadius
                        inputScale = HazeInputScale.None
                        backgroundColor = Color.Transparent
                        progressive = HazeProgressive.verticalGradient(easing = androidx.compose.animation.core.LinearEasing, startY = 0f, startIntensity = 1f, endY = topBarHeightPx, endIntensity = 0f)
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp)
                    .background(colors.Background)
            )
        }

        // 悬浮标题栏
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { tryBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.TextPrimary)
            }
            Text(
                if (isAdd) s.addModel else s.editModel,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (!isAdd) {
                IconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Filled.DeleteOutline, null, tint = colors.ErrorRed, modifier = Modifier.size(22.dp))
                }
            }
        }
    }

    if (showUnsaved) {
        AlertDialog(
            onDismissRequest = { showUnsaved = false },
            containerColor = colors.Surface,
            title = { Text(s.unsavedTitle, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text(s.unsavedMessage, color = colors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showUnsaved = false; onBack() }) {
                    Text(s.delete, color = colors.ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsaved = false }) { Text(s.cancel, color = colors.TextSecondary) }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = colors.Surface,
            title = { Text(s.deleteModel, color = colors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text(s.deleteWarning, color = colors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    editing?.let { viewModel.deleteCustomModel(it.id, it.modelType) }
                    onBack()
                }) { Text(s.delete, color = colors.ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(s.cancel, color = colors.TextSecondary) }
            }
        )
    }
}

@Composable
private fun EditorField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    colors: FreeChatColors,
    isPassword: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.sp), color = colors.TextTertiary) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.TextPrimary,
                unfocusedTextColor = colors.TextPrimary,
                focusedBorderColor = colors.Primary,
                unfocusedBorderColor = colors.Divider,
                cursorColor = colors.Primary,
                focusedContainerColor = colors.Surface,
                unfocusedContainerColor = colors.Surface
            )
        )
    }
}
