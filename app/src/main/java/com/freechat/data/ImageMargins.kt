package com.freechat.data

import kotlin.math.abs
import kotlin.math.max

/**
 * 生成结果的「纯色垫边」识别（1.1.21.3 白边根治）：
 *
 * 供应商常把非方形构图垫进方形画布——内容按要求比例居中，上下或左右填纯白/纯黑。
 * 只认「单轴、两侧、逐行纯色」的垫边：四边都有纯色边更像相框/证件照留白（不动），
 * 单侧有边或带纹理的边缘更像真实内容（不动）。有目标比例时再加一道保险——
 * 裁完必须比裁前更接近目标比例，否则视为画面自身构图。
 */
internal object ImageMargins {

    data class Trim(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** 一整行/列全是近纯白或近纯黑才算垫边（240/20 阈值容纳 JPEG 噪点与浅灰垫色，且整行同极性）。 */
    fun isBarLine(pixels: IntArray): Boolean {
        if (pixels.isEmpty()) return false
        val firstWhite = ((pixels[0] ushr 16) and 0xFF) >= 240
        for (p in pixels) {
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            val white = minOf(r, g, b) >= 240
            val black = maxOf(r, g, b) <= 20
            if (!white && !black) return false
            if (white != firstWhite) return false
        }
        return true
    }

    fun trim(width: Int, height: Int, pixels: IntArray, targetAspect: Float? = null): Trim? {
        if (width <= 0 || height <= 0 || pixels.size != width * height) return null
        fun row(y: Int) = IntArray(width) { pixels[y * width + it] }
        fun col(x: Int) = IntArray(height) { pixels[it * width + x] }
        fun run(line: (Int) -> IntArray, count: Int, fromStart: Boolean, minBar: Int): Int {
            var n = 0
            while (n < count) {
                val i = if (fromStart) n else count - 1 - n
                if (!isBarLine(line(i))) break
                n++
            }
            return if (n >= minBar) n else 0
        }
        val minBarW = max(2, width / 50)
        val minBarH = max(2, height / 50)
        val top = run(::row, height, true, minBarH)
        val bottom = run(::row, height, false, minBarH)
        val left = run(::col, width, true, minBarW)
        val right = run(::col, width, false, minBarW)
        // 垫边只出现在单轴；单侧不裁——单边纯色更可能是画面内容（天空、纸面）。
        val horizontal = left > 0 && right > 0 && top == 0 && bottom == 0
        val vertical = top > 0 && bottom > 0 && left == 0 && right == 0
        if (!horizontal && !vertical) return null
        val trim = if (vertical) Trim(0, top, 0, bottom) else Trim(left, 0, right, 0)
        val w = width - trim.left - trim.right
        val h = height - trim.top - trim.bottom
        if (w < width * 3 / 10 || h < height * 3 / 10) return null
        if (targetAspect != null && targetAspect > 0f) {
            val after = w.toFloat() / h
            val before = width.toFloat() / height
            if (abs(after - targetAspect) >= abs(before - targetAspect)) return null
        }
        return trim
    }
}
