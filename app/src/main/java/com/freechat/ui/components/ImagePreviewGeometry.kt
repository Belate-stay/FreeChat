package com.freechat.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.min

/** Pure geometry, shared by pinch/drag/double-tap and regression tests. All values are pixels. */
object ImagePreviewGeometry {
    fun fitRect(viewportWidth: Float, viewportHeight: Float, imageWidth: Float, imageHeight: Float): Rect {
        val vw = viewportWidth.coerceAtLeast(1f)
        val vh = viewportHeight.coerceAtLeast(1f)
        val iw = imageWidth.takeIf { it.isFinite() && it > 0 } ?: vw
        val ih = imageHeight.takeIf { it.isFinite() && it > 0 } ?: vh
        val factor = min(vw / iw, vh / ih)
        val w = iw * factor
        val h = ih * factor
        return Rect((vw - w) / 2, (vh - h) / 2, (vw + w) / 2, (vh + h) / 2)
    }

    fun clampPan(offset: Offset, scale: Float, viewportWidth: Float, viewportHeight: Float, fitted: Rect): Offset {
        if (!scale.isFinite() || scale <= 1.01f) return Offset.Zero
        val maxX = ((fitted.width * scale - viewportWidth) / 2).coerceAtLeast(0f)
        val maxY = ((fitted.height * scale - viewportHeight) / 2).coerceAtLeast(0f)
        return Offset((if (offset.x.isFinite()) offset.x else 0f).coerceIn(-maxX, maxX),
            (if (offset.y.isFinite()) offset.y else 0f).coerceIn(-maxY, maxY))
    }

    fun shouldDismiss(distance: Float, velocity: Float, viewportHeight: Float, density: Float): Boolean {
        val threshold = min(110f * density, viewportHeight * 0.18f)
        return abs(distance) >= threshold ||
            (abs(distance) > 24f * density && abs(velocity) > 1200f * density && distance * velocity > 0f)
    }
}
