package com.freechat.ui.components

/** One reserved image slot, no empty frame between the moving dots and decoded result. */
internal object ImageRevealPolicy {
    const val CornerDp = 12
    fun imageAlpha(progress: Float): Float = ImageDotMotion.smoothStep(.14f, .94f, progress.coerceIn(0f, 1f))
    fun dotAlpha(progress: Float): Float = 1f - imageAlpha(progress)

    /**
     * 过渡起点（1.1.26 追修）：占位→图片的变形只属于「生成完成到达」这一刻（亲眼见证的活生成
     * 槽位）。其余情形——历史图/滑回/回看——凡画幅已记进 [ImageRevealLedger] 的一律从终态 1
     * 直接显示（不重播占位粒子、不重播变形）；画幅未知的首见图从 0 起步但走快速落位曲线，
     * 保 1.1.21.5 的「不跳变」，且只此一次。
     */
    fun revealStart(fromGeneration: Boolean, aspectKnown: Boolean): Float =
        if (fromGeneration || !aspectKnown) 0f else 1f
    fun radius(progress: Float, strength: Float): Float {
        val original = ImageDotMotion.MinRadius + (ImageDotMotion.MaxRadius - ImageDotMotion.MinRadius) * strength
        val merge = ImageDotMotion.smoothStep(.02f, .82f, progress.coerceIn(0f, 1f))
        return original + (.74f - original) * merge
    }

    /**
     * 占位图定死 1:1；图片到达后沿 reveal 曲线长成**模型返回的真实画幅**（1.1.21.5 起槽位
     * 变形覆盖所有图片路径——不再解码完瞬间跳变）。比例只认返回图的实测像素，不猜提示词；
     * 信息栏随槽位底边同步下移/上移。
     *
     * 1.1.26 追修：这条过渡只在「生成完成到达」时播放一次（[revealStart] 起点 0）；
     * 其余情形 progress 恒 1，槽位首帧即真画幅，不重播占位。
     */
    fun slotRatio(progress: Float, intrinsic: Float): Float {
        val target = intrinsic.coerceIn(.05f, 20f)
        val morph = ImageDotMotion.smoothStep(.02f, .82f, progress.coerceIn(0f, 1f))
        return 1f + (target - 1f) * morph
    }

    fun fit(aspect: Float): Pair<Float, Float> {
        val safe = aspect.coerceIn(.05f, 20f)
        return minOf(safe, 1f) to minOf(1f / safe, 1f)
    }

    /** Fraction of the slot the image fills (width, height) — canvas-shape aware, unlike [fit]. */
    fun fitIn(width: Float, height: Float, aspect: Float): Pair<Float, Float> =
        fit(aspect * height / width)

    /**
     * Converges onto the image's fit rect inside the current slot. When the slot itself has
     * morphed to the result aspect (progress ≥ 1), that rect is the whole slot — no letterbox.
     */
    fun frame(progress: Float, width: Float, height: Float, aspect: Float, corner: Float): Frame {
        val fitted = fitIn(width, height, aspect)
        val morph = ImageDotMotion.smoothStep(.02f, .82f, progress)
        val halfWidth = width * .5f * (1f + (fitted.first - 1f) * morph)
        val halfHeight = height * .5f * (1f + (fitted.second - 1f) * morph)
        val startCorner = minOf(width, height) * ImageDotMotion.Corner
        return Frame(halfWidth, halfHeight,
            minOf(startCorner + (corner - startCorner) * morph, halfWidth, halfHeight),
            minOf(width, height) * ImageDotMotion.Feather * (1f - morph) + .8f * morph)
    }

    data class Frame(val halfWidth: Float, val halfHeight: Float, val corner: Float, val feather: Float) {
        fun opacity(x: Float, y: Float, width: Float, height: Float): Float {
            val qx = kotlin.math.abs(x - width * .5f) - halfWidth + corner
            val qy = kotlin.math.abs(y - height * .5f) - halfHeight + corner
            val ox = maxOf(qx, 0f)
            val oy = maxOf(qy, 0f)
            val distance = kotlin.math.sqrt(ox * ox + oy * oy) + minOf(maxOf(qx, qy), 0f) - corner
            return ImageDotMotion.smoothStep(0f, feather, -distance)
        }
    }
}

/**
 * 画幅账本（1.1.26 追修，同 TypewriterLedger 一次性纪律）：每张图的实测画幅只测一次，
 * 之后滑回/回看直接以真画幅首帧成形——不再从 1:1 起步、不重播过渡。
 */
object ImageRevealLedger {
    private val aspects = java.util.concurrent.ConcurrentHashMap<String, Float>()

    fun aspectOf(source: String): Float? = aspects[source]

    fun record(source: String, width: Int, height: Int) {
        if (width > 0 && height > 0) aspects[source] = (width.toFloat() / height).coerceIn(0.05f, 20f)
    }
}
