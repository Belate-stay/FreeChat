package com.freechat.ui.screens

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.freechat.ui.theme.FreeChatColors

internal fun characterHintColor(colors: FreeChatColors) = colors.TextSecondary.copy(alpha = 0.84f)

/** Role fields share a compact shape and a deliberately smaller, quieter hint layer. */
@Composable
internal fun CharacterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    placeholder: (@Composable () -> Unit)? = null,
    colors: TextFieldColors,
) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        modifier = modifier.defaultMinSize(minHeight = 48.dp), enabled = enabled,
        singleLine = singleLine, minLines = minLines, maxLines = maxLines,
        textStyle = textStyle, shape = RoundedCornerShape(12.dp), colors = colors,
        placeholder = placeholder?.let { hint ->
            { ProvideTextStyle(MaterialTheme.typography.bodySmall) { hint() } }
        })
}
