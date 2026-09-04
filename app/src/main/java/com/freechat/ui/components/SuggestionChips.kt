package com.freechat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freechat.model.ModelInfo
import com.freechat.ui.theme.*

@Composable
fun SuggestionChips(
    suggestions: List<String>,
    onClick: (String) -> Unit,
    isDark: Boolean
) {
    val colors = if (isDark) DarkColors else LightColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { suggestion ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, colors.ChipBorder, RoundedCornerShape(12.dp))
                    .clickable { onClick(suggestion) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.TextSecondary
                )
            }
        }
    }
}

@Composable
fun ModelChip(
    model: ModelInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val colors = if (isDark) DarkColors else LightColors
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.Primary else colors.ChipBorder,
        animationSpec = tween(200),
        label = "chip_border"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                model.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) colors.Primary else colors.TextPrimary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            if (model.description.isNotEmpty()) {
                Text(
                    model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.TextTertiary
                )
            }
        }
    }
}
