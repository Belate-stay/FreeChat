package com.freechat.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freechat.R
import com.freechat.i18n.AppStrings
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.model.AppIcon
import com.freechat.ui.theme.LocalFreeChatColors

internal fun iconLabel(icon: AppIcon, s: AppStrings) = when (icon) {
    AppIcon.BLUE_F -> s.appIconBlue
    AppIcon.CLASSIC -> s.appIconClassic
    AppIcon.LUNHUI -> s.appIconLunhui
    AppIcon.GONGMING -> s.appIconGongming
    AppIcon.HUANMENG -> s.appIconHuanmeng
    AppIcon.XINSHENG -> s.appIconXinsheng
    AppIcon.RIXIANG -> s.appIconRixiang
    AppIcon.HAILUO -> s.appIconHailuo
}

// 预览一律用 PNG 原图：launcher 的 @mipmap/ic_launcher_* 是 adaptive-icon **XML**，
// painterResource 会把它当矢量图解析，一组合就抛异常（1.0.99.4 修「点图标就闪退」）。
// 自适应图标的 inset 16.6% 是桌面上的取景补偿，预览里不需要（跟简F 同口径）。
private fun iconRes(icon: AppIcon): Int = when (icon) {
    AppIcon.BLUE_F -> R.drawable.freechat_icon_blue_f
    AppIcon.CLASSIC -> R.mipmap.ic_launcher          // classic 是位图 mipmap，painterResource 能吃
    AppIcon.LUNHUI -> R.drawable.freechat_icon_lunhui
    AppIcon.GONGMING -> R.drawable.freechat_icon_gongming
    AppIcon.HUANMENG -> R.drawable.freechat_icon_huanmeng
    AppIcon.XINSHENG -> R.drawable.freechat_icon_xinsheng
    AppIcon.RIXIANG -> R.drawable.freechat_icon_rixiang
    AppIcon.HAILUO -> R.drawable.freechat_icon_hailuo
}

@Composable
internal fun IconPickerPage(selected: AppIcon, active: AppIcon, saving: Boolean, onSelect: (AppIcon) -> Unit, s: AppStrings) {
    val colors = LocalFreeChatColors.current
    Text(s.appIconRestart, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
    AppIcon.entries.forEach { icon ->
        SettingsRow {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .selectable(selected = selected == icon, enabled = !saving, role = Role.RadioButton, onClick = { onSelect(icon) })
                .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(iconRes(icon)),
                    contentDescription = null, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(iconLabel(icon, s), style = MaterialTheme.typography.bodyLarge,
                        color = colors.TextPrimary, fontWeight = if (selected == icon) FontWeight.SemiBold else FontWeight.Normal)
                    AnimatedVisibility(active == icon || selected == icon,
                        enter = FreeChatAnimation.expandEnter(), exit = FreeChatAnimation.expandExit()) {
                        Column {
                            Spacer(Modifier.height(4.dp))
                            Text(if (active == icon) s.appIconActive else s.appIconSelected,
                                style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(if (selected == icon) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                    null, tint = if (selected == icon) colors.Primary else colors.TextTertiary, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
