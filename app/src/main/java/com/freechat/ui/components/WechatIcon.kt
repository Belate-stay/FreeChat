package com.freechat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * 微信风图标（1.0.93）：两只叠在一起的绿色对话气泡 + 两只白眼睛——
 * Material 图标库没有微信品牌标（品牌图受版权限制不随包分发），
 * 这是 Canvas 直绘的「微信感」近似：大泡（左下）+ 小泡（右上）的双泡剪影。
 */
@Composable
fun WechatIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val green = Color(0xFF07C160)
        // 小气泡（右上，先画在下层）
        drawRoundRect(
            color = green,
            topLeft = Offset(w * 0.50f, h * 0.06f),
            size = Size(w * 0.46f, h * 0.40f),
            cornerRadius = CornerRadius(w * 0.15f)
        )
        // 大气泡（左下，盖住小泡左下角形成叠压）
        drawRoundRect(
            color = green,
            topLeft = Offset(w * 0.02f, h * 0.30f),
            size = Size(w * 0.64f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.19f)
        )
        // 大泡的眼睛（微信感的关键两粒白点）
        drawCircle(Color.White, radius = w * 0.045f, center = Offset(w * 0.23f, h * 0.56f))
        drawCircle(Color.White, radius = w * 0.045f, center = Offset(w * 0.44f, h * 0.56f))
    }
}
