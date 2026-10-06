package com.freechat.ui.components

import kotlin.math.roundToInt

data class ChatScrollItem(val key: String, val estimatedHeight: Float, val timestamp: Long = 0L)
data class ChatScrollTarget(val index: Int, val offset: Int)

/** Cached prefix lengths: movement is O(1), seeking O(log n), never a scan of the conversation. */
class ChatScrollGeometry(heights: List<Float>, spacing: Int, beforePadding: Int, afterPadding: Int, viewport: Int) {
    private val prefix = DoubleArray(heights.size + 1)
    val itemCount = heights.size
    val scrollExtent: Double
    init {
        heights.forEachIndexed { i, height ->
            prefix[i + 1] = prefix[i] + (if (height.isFinite()) height.coerceAtLeast(1f) else 1f) +
                if (i < heights.lastIndex) spacing.coerceAtLeast(0) else 0
        }
        scrollExtent = (prefix.last() + beforePadding.coerceAtLeast(0) + afterPadding.coerceAtLeast(0) -
            viewport.coerceAtLeast(0)).coerceAtLeast(0.0)
    }
    fun fraction(index: Int, offset: Int): Float {
        if (itemCount == 0 || scrollExtent <= 0) return 0f
        return ((prefix[index.coerceIn(0, itemCount - 1)] + offset.coerceAtLeast(0)) / scrollExtent)
            .coerceIn(0.0, 1.0).toFloat()
    }
    fun target(fraction: Float): ChatScrollTarget {
        if (itemCount == 0) return ChatScrollTarget(0, 0)
        val f = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
        if (f <= 0f) return ChatScrollTarget(0, 0)
        // The true end is exact even while off-screen item heights are still estimates.
        if (f >= 1f) return ChatScrollTarget(itemCount - 1, 1_000_000)
        val distance = scrollExtent * f
        var low = 0
        var high = itemCount - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (prefix[middle] <= distance) low = middle else high = middle - 1
        }
        return ChatScrollTarget(low, (distance - prefix[low]).coerceIn(0.0, Int.MAX_VALUE.toDouble()).roundToInt())
    }
}

object QuickLocatePolicy {
    const val IdleMillis = 5_000L
    /** Landscape/keyboard can leave a short rail. Always reserve real travel between the ends. */
    fun insetForHeight(height: Float, preferredInset: Float): Float =
        if (height.isFinite() && preferredInset.isFinite() && height > 0f)
            minOf(preferredInset.coerceAtLeast(0f), height / 4f) else 0f

    fun thumbHalfHeight(inset: Float, preferredHalf: Float): Float =
        minOf(preferredHalf.coerceAtLeast(0f), inset.coerceAtLeast(0f) * 0.72f)

    fun trackFraction(y: Float, height: Float, inset: Float): Float {
        if (!y.isFinite() || !height.isFinite() || height <= inset * 2f) return 0f
        return ((y - inset) / (height - inset * 2f)).coerceIn(0f, 1f)
    }

    /** Grabbing the thumb retains its position; tapping elsewhere on the rail seeks immediately. */
    fun grabOffset(y: Float, center: Float, hitRadius: Float): Float =
        if (y.isFinite() && center.isFinite() && kotlin.math.abs(y - center) <= hitRadius) y - center else 0f

    fun timestampAt(items: List<ChatScrollItem>, index: Int): Long? =
        items.getOrNull(index.coerceIn(0, items.lastIndex.coerceAtLeast(0)))?.timestamp?.takeIf { it > 0L }
}
