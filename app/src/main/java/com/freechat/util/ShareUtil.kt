package com.freechat.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.freechat.R
import com.freechat.ui.components.MdBlock
import com.freechat.ui.components.parseMarkdown
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 分享图的一段。
 * [isUser] = true → 渲染为右对齐、淡主题色底的圆角气泡；false → 通栏左对齐文字流。
 * 逐段的「你 / FreeChat」标签已整体移除，角色只由 [isUser] 决定（用户通过气泡即可区分）。
 */
data class ShareSegment(val text: String, val isUser: Boolean = false)

/** 伪粗体：单字重字体（鸿蒙宋韵朗黑）加粗靠合成。同 CustomTypefaceSpan 的理由，测量期也要生效 */
private class FakeBoldSpan : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) { tp.isFakeBoldText = true }
    override fun updateMeasureState(tp: TextPaint) { tp.isFakeBoldText = true }
}

/**
 * 自定义 Typeface span（行内代码用等宽字体）。
 * ★ 必须继承 MetricAffectingSpan 而不是 CharacterStyle：
 * StaticLayout 换行测量只看 MetricAffectingSpan（updateMeasureState），
 * 纯 CharacterStyle 只在绘制时改 paint，测量用的还是正文字体 —— 等宽字实际比正文宽，
 * 于是量出来的行宽偏小、行内代码会顶出气泡。
 */
private class CustomTypefaceSpan(val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) { tp.typeface = typeface }
    override fun updateMeasureState(tp: TextPaint) { tp.typeface = typeface }
}

// ===== 排版基础工具（与 ShareImageGenerator 的私有常量无关，故放文件顶层）=====

/**
 * 行高增量。Android 的 `setLineSpacing(add, mult)` 公式是 `span * mult + add`，
 * 其中 span = descent − ascent（不是 textSize！）。这里由 fontMetrics 反推 add，
 * 使「实得行高 = textSize × ratio」，跨字体稳定。
 */
private fun lineSpacingFor(paint: TextPaint, ratio: Float): Float {
    val fm = paint.fontMetrics
    return (paint.textSize * ratio - (fm.descent - fm.ascent)).coerceAtLeast(0f)
}

private fun buildLayout(text: CharSequence, paint: TextPaint, width: Int, ratio: Float): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(lineSpacingFor(paint, ratio), 1f)
        .setIncludePad(false)
        .build()

/** StaticLayout 实占宽（排除行尾空白）。getLineMax 名义上 API 29+，低版本回退 getLineWidth 仅作防御 */
private fun StaticLayout.naturalWidth(): Float {
    var w = 0f
    for (i in 0 until lineCount) {
        val lw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) getLineMax(i) else getLineWidth(i)
        if (lw > w) w = lw
    }
    return w
}

/** 主题色按 ratio 混白；画布纯白不透明，输出 alpha 恒为 FF */
private fun tint(color: Int, ratio: Float): Int = Color.rgb(
    (Color.red(color) * ratio + 255f * (1f - ratio)).toInt().coerceIn(0, 255),
    (Color.green(color) * ratio + 255f * (1f - ratio)).toInt().coerceIn(0, 255),
    (Color.blue(color) * ratio + 255f * (1f - ratio)).toInt().coerceIn(0, 255)
)

/** 深色主题 Primary 是浅色，画在纯白画布上近乎隐形 → 按 WCAG 相对亮度迭代压暗（保色相） */
private fun accentOnWhite(accent: Int): Int {
    var c = accent
    var i = 0
    while (relLuminance(c) > 0.28 && i++ < 10) {
        c = Color.rgb(
            (Color.red(c) * 0.88f).toInt(),
            (Color.green(c) * 0.88f).toInt(),
            (Color.blue(c) * 0.88f).toInt()
        )
    }
    return Color.rgb(Color.red(c), Color.green(c), Color.blue(c))   // 强制 alpha = FF
}

private fun relLuminance(c: Int): Double {
    fun ch(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
}

/**
 * 分享图生成器：把消息 Markdown 渲染成美化长图。
 * 顶部品牌 FreeChat + 正文（用户消息 = 右对齐淡主题色圆角气泡 / AI 消息 = 通栏左对齐文字流）
 * + 底部分隔线 + 页脚（logo + 品牌 + 二维码）。
 * 宽度固定 1080px，高度随内容自适应；画布纯白不透明，所有颜色自行计算 ARGB。
 */
object ShareImageGenerator {
    private const val WIDTH = 1080
    private const val PAD = 80
    private const val PAD_TOP = 88                  // 保持原值，不 churn
    private const val MAX_H = 20000                 // 防 OOM 硬顶

    private const val TEXT_COLOR = 0xFF1B1B1B.toInt()
    private const val FAINT_LINE = 0xFFE8E8E8.toInt()
    private const val DIVIDER_COLOR = 0xFFDDDDDD.toInt()   // 气泡内也看得见
    private const val CODE_BG = 0xFFF5F5F5.toInt()         // AI 通栏路径代码底色
    private const val CODE_TEXT = 0xFF333333.toInt()
    private const val SEG_RULE_COLOR = 0xFFDCDCDC.toInt()

    // 字号
    private const val BODY = 40f
    private const val MONO = 32f
    private const val TABLE_SIZE = 34f
    private const val TITLE = 58f
    private const val BRAND_SIZE = 38f

    // 行高比（em，语义等同 Compose lineHeight）
    // 字体 hysongyunlanghei: upm 1000 / asc 916 / desc -301 → span = 1.217em，40px → 48.68px
    private const val RATIO_BODY  = 1.85f    // 40px → 74.0px（旧值 1.74em；App 正文 1.75em）
    private const val RATIO_HEAD  = 1.45f    // App 标题 1.40em
    private const val RATIO_CODE  = 1.45f    // 旧值 1.25em
    private const val RATIO_TABLE = 1.45f

    // 段 / 块间距（px，按 40/15 = 2.667 折算自 App 的 dp）
    private const val PARA_GAP        = 30f  // 0.75em；App 段距 10dp(0.67em)，旧值 12px(0.30em)
    private const val LIST_ITEM_GAP   = 16f
    private const val LIST_ITEM_TOP   = 6f
    private const val QUOTE_PAD_V     = 20f
    private const val CODE_PAD_V      = 26f
    private const val CODE_INSET      = 14f  // 代码文字相对底块的左右内缩
    private const val TABLE_PAD_V     = 18f
    private const val HEAD_TOP_GAP    = 48f  // App 18dp
    private const val HEAD_TOP_GAP_SM = 27f  // App 10dp
    private const val HEAD_BOTTOM_GAP = 16f  // App  6dp
    private const val DIVIDER_H       = 66f  // App 12dp×2 + 2px

    // 消息段之间 = 空行 + 分页符（多消息批量分享时生效）
    private const val SEG_GAP          = 104f
    private const val PAGE_MARK_HALF_W = 90f  // 居中 180px 短横线

    // 用户气泡
    private const val BUBBLE_PAD_H     = 36f   // App 14dp
    private const val BUBBLE_PAD_V     = 26f   // App 10dp
    private const val BUBBLE_RADIUS    = 42f   // App 16dp × 2.667
    private const val BUBBLE_TAIL_R    = 11f   // App  4dp × 2.667（右下尾巴）
    private const val BUBBLE_MIN_W     = 240f
    private const val BUBBLE_MAX_RATIO = 0.80f // 常规硬上限 → 736px
    private const val BUBBLE_SLACK     = 6f    // 折行容差，防最后一字被挤下去
    private const val BUBBLE_SHRINK    = true  // false = 用户气泡统一占满 736px
    private const val BUBBLE_FILL_TINT   = 0.16f
    private const val BUBBLE_BORDER_TINT = 0.34f
    private const val BUBBLE_CODE_TINT   = 0.30f

    // 页脚
    private const val TITLE_AREA_H  = 124      // 保持原值
    private const val SEP_GAP       = 56
    private const val FOOTER_GAP    = 48
    private const val BOTTOM_PAD    = 72
    private const val LOGO_SIZE     = 64
    private const val QR_SIZE       = 160      // 200 → 160（线性 −20%，面积 −36%）
    private const val BRAND_BLOCK_H = 64
    private val FOOTER_H = maxOf(QR_SIZE, BRAND_BLOCK_H)   // = 160，改 QR 无需动高度算式

    /** @return 生成的分享长图；内容过多导致位图分配失败时返回 null（调用方负责提示） */
    fun generate(context: Context, segments: List<ShareSegment>, accentColor: Int): Bitmap? {
        val contentW = WIDTH - PAD * 2                                 // 920
        val accent = accentOnWhite(accentColor)                        // 白底可读墨色
        val bubbleFill = tint(accent, BUBBLE_FILL_TINT)
        val bubbleBorder = tint(accent, BUBBLE_BORDER_TINT)
        val bubbleCodeBg = tint(accent, BUBBLE_CODE_TINT)

        val titleFont = runCatching { ResourcesCompat.getFont(context, R.font.aurora) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val bodyFont = runCatching { ResourcesCompat.getFont(context, R.font.hysongyunlanghei) }.getOrNull() ?: Typeface.DEFAULT
        val monoFont = runCatching { ResourcesCompat.getFont(context, R.font.consola) }.getOrNull() ?: Typeface.MONOSPACE

        val maxBubbleW = contentW * BUBBLE_MAX_RATIO                   // 736  常规硬上限
        val probeW = (maxBubbleW - BUBBLE_PAD_H * 2).toInt()           // 664  第一遍排版宽

        // ===== 阶段 1：排版。宽度与高度只在这里算一次 =====
        val laidSegs: List<LaidSeg> = segments.map { seg ->
            val blocks = parseMarkdown(seg.text).ifEmpty { listOf(MdBlock.Paragraph(seg.text)) }
            if (!seg.isUser) {
                LaidSeg(
                    blocks = blocks.map { layoutBlock(it, contentW, bodyFont, monoFont, accent, CODE_BG) },
                    contentW = contentW, bubbleW = 0f, fill = 0, border = 0
                )
            } else {
                // 第一遍：按 probeW 探自然宽
                val probe = blocks.map { layoutBlock(it, probeW, bodyFont, monoFont, accent, bubbleCodeBg) }
                var natural = 0f
                probe.forEach {
                    val w = it.naturalWidth
                    if (w != Float.MAX_VALUE && w > natural) natural = w
                }

                // 常规文本 natural ≤ probeW → 气泡硬顶 736；
                // 仅当存在不可断 token（natural > probeW）才放宽到 contentW 兜底
                val overflowing = natural > probeW
                val upper = if (overflowing) contentW.toFloat() else maxBubbleW
                val bubbleW = if (BUBBLE_SHRINK) {
                    (natural + BUBBLE_PAD_H * 2f + BUBBLE_SLACK)
                        .coerceIn(minOf(BUBBLE_MIN_W, upper), upper)
                } else {
                    upper
                }
                val innerW = (bubbleW - BUBBLE_PAD_H * 2f).toInt().coerceAtLeast(120)

                // 第二遍：按最终内宽定稿。高度以这一遍为准
                LaidSeg(
                    blocks = blocks.map { layoutBlock(it, innerW, bodyFont, monoFont, accent, bubbleCodeBg) },
                    contentW = innerW, bubbleW = bubbleW, fill = bubbleFill, border = bubbleBorder
                )
            }
        }

        // ===== 阶段 2：高度。唯一来源 = LaidSeg.height =====
        val bodyTop = PAD_TOP + TITLE_AREA_H
        var bodyH = 0f
        laidSegs.forEach { bodyH += it.height }
        bodyH += (laidSegs.size - 1).coerceAtLeast(0) * SEG_GAP
        val bottomH = SEP_GAP + 2 + FOOTER_GAP + FOOTER_H + BOTTOM_PAD
        // ★ 超硬顶一律判失败，不再静默截断：
        //   Bitmap 画布越界不抛异常，画上去就没了 —— 原来的 coerceIn(1, MAX_H) 会产出一张
        //   正文被从中间切断、页脚整块消失的长图，用户看不出哪里不对。
        //   这里直接返回 null，交给上层提示「图片过大，生成失败，请分几次分享」。
        val fullH = (bodyTop + bodyH + bottomH).toInt()
        if (fullH > MAX_H) return null
        val totalH = fullH.coerceAtLeast(1)

        val bmp = try {
            Bitmap.createBitmap(WIDTH, totalH, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            // 1080 × 总高 的 ARGB_8888：批量分享选中的消息多时，这一张图能瞬间要几十 MB。
            // 宁可直接失败让上层提示「图片过大」，也不要 OutOfMemoryError 把 App 带走。
            return null
        }
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        // ===== 顶部品牌 =====
        val titlePaint = TextPaint().apply {
            color = accent; textSize = TITLE; typeface = titleFont
            isAntiAlias = true; isFakeBoldText = true
        }
        canvas.drawText("FreeChat", PAD.toFloat(), (PAD_TOP + TITLE - 6).toFloat(), titlePaint)
        val ruleY = (PAD_TOP + TITLE_AREA_H - 46).toFloat()
        canvas.drawLine(PAD.toFloat(), ruleY, (WIDTH - PAD).toFloat(), ruleY,
            Paint().apply { color = accent; strokeWidth = 4f; isAntiAlias = true })

        // ===== 正文：无标签。i>0 时先加段距、在段距正中画分页符（不占高度）=====
        val segRule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SEG_RULE_COLOR; strokeWidth = 2f }
        var y = bodyTop.toFloat()
        laidSegs.forEachIndexed { i, seg ->
            if (i > 0) {
                y += SEG_GAP
                drawPageMark(canvas, y - SEG_GAP / 2f, segRule)
            }
            y = seg.draw(canvas, y)
        }

        // ===== 底部分隔线 =====
        val sepY = y + SEP_GAP
        canvas.drawLine(PAD.toFloat(), sepY, (WIDTH - PAD).toFloat(), sepY,
            Paint().apply { color = FAINT_LINE; strokeWidth = 2f; isAntiAlias = true })

        // ===== 页脚：等高带，logo / 品牌 / QR 全部带内垂直居中；QR 不再压线 =====
        val footerTop = sepY + 2 + FOOTER_GAP
        val logoY = footerTop + (FOOTER_H - LOGO_SIZE) / 2f
        runCatching { BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher) }.getOrNull()?.let { logo ->
            val scaled = if (logo.width == LOGO_SIZE && logo.height == LOGO_SIZE) logo
                         else Bitmap.createScaledBitmap(logo, LOGO_SIZE, LOGO_SIZE, true)
            canvas.drawBitmap(scaled, PAD.toFloat(), logoY, null)
        }
        val brandPaint = TextPaint().apply {
            color = TEXT_COLOR; textSize = BRAND_SIZE; typeface = titleFont; isAntiAlias = true
        }
        canvas.drawText("FreeChat", (PAD + LOGO_SIZE + 22).toFloat(),
            logoY + LOGO_SIZE / 2f + 13f, brandPaint)

        // 二维码：512 → 320 → 160 两段式降采样，避免单段 3.2× 把圆点模块糊掉
        val qrRaw = runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.freechat_qc) }.getOrNull()
        if (qrRaw != null && !qrRaw.isRecycled) {
            val half = Bitmap.createScaledBitmap(qrRaw, QR_SIZE * 2, QR_SIZE * 2, true)
            val qr = Bitmap.createScaledBitmap(half, QR_SIZE, QR_SIZE, true)
            if (half !== qr && half !== qrRaw) half.recycle()
            if (qrRaw !== half && qrRaw !== qr) qrRaw.recycle()
            canvas.drawBitmap(qr, (WIDTH - PAD - QR_SIZE).toFloat(),
                footerTop + (FOOTER_H - QR_SIZE) / 2f, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        return bmp
    }

    /** 消息之间的分页符：居中 180px 短横线，明显短于通栏 Divider，落在 SEG_GAP 留白正中 */
    private fun drawPageMark(canvas: Canvas, centerY: Float, paint: Paint) {
        canvas.drawLine(WIDTH / 2f - PAGE_MARK_HALF_W, centerY,
            WIDTH / 2f + PAGE_MARK_HALF_W, centerY, paint)
    }

    // ===== 排版 =====

    private class LaidSeg(
        val blocks: List<LaidBlock>,
        val contentW: Int,      // 排版宽：AI = 920，用户 = innerW；draw 时透传给子块
        val bubbleW: Float,     // > 0 表示用户气泡
        val fill: Int,
        val border: Int
    ) {
        private val contentH: Float =
            blocks.fold(0f) { a, b -> a + b.height } - (blocks.lastOrNull()?.trailingGap ?: 0f)

        /** 高度单一来源：测量循环与绘制循环都只读这个 val */
        val height: Float = contentH + if (bubbleW > 0f) BUBBLE_PAD_V * 2f else 0f

        fun draw(canvas: Canvas, y: Float): Float {
            if (bubbleW <= 0f) {
                // ⚠️ 必须累积块的返回值（= 块底边）当下一块的 y。
                //   1.0.23 重写时这里漏了累积，所有块都画在同一个 y 上：段落互相覆盖（只剩最上面
                //   那几行看得见），而 height 照旧为每块预留高度 → 图中间一大片空白。别再改成常量 y。
                var cy = y
                for (b in blocks) cy = b.draw(canvas, PAD.toFloat(), cy, contentW)
                return y + height
            }
            val left = WIDTH - PAD - bubbleW
            val right = left + bubbleW
            val bottom = y + height
            // addRoundRect 的 radii 顺序：[TLx,TLy,TRx,TRy,BRx,BRy,BLx,BLy]，索引 4/5 = 右下尾巴
            val radii = floatArrayOf(
                BUBBLE_RADIUS, BUBBLE_RADIUS,
                BUBBLE_RADIUS, BUBBLE_RADIUS,
                BUBBLE_TAIL_R, BUBBLE_TAIL_R,
                BUBBLE_RADIUS, BUBBLE_RADIUS
            )
            val fillPath = Path().apply { addRoundRect(left, y, right, bottom, radii, Path.Direction.CW) }
            canvas.drawPath(fillPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
            val borderPath = Path().apply { addRoundRect(left + 1f, y + 1f, right - 1f, bottom - 1f, radii, Path.Direction.CW) }
            canvas.drawPath(borderPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2f; color = border
            })
            var cy = y + BUBBLE_PAD_V
            val cx = left + BUBBLE_PAD_H
            for (b in blocks) cy = b.draw(canvas, cx, cy, contentW)   // ← 传排版宽，不是气泡宽
            return bottom
        }
    }

    private sealed class LaidBlock {
        abstract val height: Float
        /** 末块要扣掉的尾部间距（气泡底 / 段末不留多余空） */
        open val trailingGap: Float get() = 0f
        /** 自然所需宽度；全宽元素返回 MAX_VALUE（第一遍探宽时忽略） */
        open val naturalWidth: Float get() = Float.MAX_VALUE
        abstract fun draw(canvas: Canvas, x: Float, y: Float, contentW: Int): Float

        class Text(val layout: StaticLayout, val topPad: Float = 0f, val bottomPad: Float = 0f) : LaidBlock() {
            override val height get() = layout.height + topPad + bottomPad
            override val trailingGap get() = bottomPad
            override val naturalWidth get() = layout.naturalWidth()
            override fun draw(canvas: Canvas, x: Float, y: Float, contentW: Int): Float {
                canvas.save(); canvas.translate(x, y + topPad); layout.draw(canvas); canvas.restore()
                return y + height
            }
        }

        class IndentText(val layout: StaticLayout, val indent: Float, val topPad: Float = 0f, val bottomPad: Float = 0f) : LaidBlock() {
            override val height get() = layout.height + topPad + bottomPad
            override val trailingGap get() = bottomPad
            override val naturalWidth get() = indent + layout.naturalWidth()
            override fun draw(canvas: Canvas, x: Float, y: Float, contentW: Int): Float {
                canvas.save(); canvas.translate(x + indent, y + topPad); layout.draw(canvas); canvas.restore()
                return y + height
            }
        }

        class Quote(val layout: StaticLayout, val barColor: Int) : LaidBlock() {
            override val height get() = layout.height + QUOTE_PAD_V * 2
            override val naturalWidth get() = 22f + layout.naturalWidth()
            override fun draw(canvas: Canvas, x: Float, y: Float, contentW: Int): Float {
                canvas.drawRoundRect(x, y + 2f, x + 6f, y + height - 2f, 3f, 3f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = barColor })
                canvas.save(); canvas.translate(x + 22f, y + QUOTE_PAD_V); layout.draw(canvas); canvas.restore()
                return y + height
            }
        }

        class Code(val layout: StaticLayout, val bg: Int) : LaidBlock() {
            override val height get() = layout.height + CODE_PAD_V * 2
            override val naturalWidth get() = CODE_INSET * 2 + layout.naturalWidth()
            override fun draw(canvas: Canvas, x: Float, y: Float, contentW: Int): Float {
                // 背景正好占满排版宽，文字左右各内缩 CODE_INSET —— 绝不越出内容边距
                canvas.drawRoundRect(x, y, x + contentW, y + height, 14f, 14f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
                canvas.save(); canvas.translate(x + CODE_INSET, y + CODE_PAD_V); layout.draw(canvas); canvas.restore()
                return y + height
            }
        }

        class Divider(val color: Int) : LaidBlock() {
            override val height = DIVIDER_H
            override fun draw(canvas: Canvas, x: Float, y: Float, contentW: Int): Float {
                canvas.drawLine(x, y + DIVIDER_H / 2f, x + contentW, y + DIVIDER_H / 2f,
                    Paint().apply { color = color; strokeWidth = 2f; isAntiAlias = true })
                return y + height
            }
        }
    }

    private fun layoutBlock(
        block: MdBlock, contentW: Int, bodyFont: Typeface, monoFont: Typeface,
        accent: Int, codeBg: Int
    ): LaidBlock = when (block) {
        is MdBlock.Heading -> {
            val size = when (block.level) { 1 -> 54f; 2 -> 50f; 3 -> 46f; 4 -> 43f; else -> 41f }
            val p = TextPaint().apply {
                color = TEXT_COLOR; textSize = size; typeface = bodyFont; isAntiAlias = true; isFakeBoldText = true
            }
            // ★ 标题文本同样要走 buildSpannable：模型常写「## **标题**」，原样绘制会把 ** 当字符画出来
            LaidBlock.Text(
                buildLayout(buildSpannable(block.text, bodyFont, monoFont), p, contentW, RATIO_HEAD),
                topPad = if (block.level <= 2) HEAD_TOP_GAP else HEAD_TOP_GAP_SM,
                bottomPad = HEAD_BOTTOM_GAP
            )
        }
        is MdBlock.Paragraph -> {
            val p = TextPaint().apply { color = TEXT_COLOR; textSize = BODY; typeface = bodyFont; isAntiAlias = true }
            LaidBlock.Text(
                buildLayout(buildSpannable(block.text, bodyFont, monoFont), p, contentW, RATIO_BODY),
                bottomPad = PARA_GAP
            )
        }
        is MdBlock.ListItem -> {
            val p = TextPaint().apply { color = TEXT_COLOR; textSize = BODY; typeface = bodyFont; isAntiAlias = true }
            val text = "${block.bullet}  ${block.text}" +
                if (block.body.isEmpty()) "" else "\n" + block.body.joinToString("\n") { "      $it" }
            val indent = (block.indent * 26).toFloat()
            LaidBlock.IndentText(
                buildLayout(
                    buildSpannable(text, bodyFont, monoFont), p,
                    (contentW - indent.toInt()).coerceAtLeast(1), RATIO_BODY
                ),
                indent = indent,
                topPad = LIST_ITEM_TOP, bottomPad = LIST_ITEM_GAP
            )
        }
        is MdBlock.Quote -> {
            val p = TextPaint().apply { color = TEXT_COLOR; textSize = BODY; typeface = bodyFont; isAntiAlias = true }
            LaidBlock.Quote(
                buildLayout(
                    buildSpannable(block.text, bodyFont, monoFont), p,
                    (contentW - 22).coerceAtLeast(1), RATIO_BODY
                ),
                accent
            )
        }
        is MdBlock.CodeBlock -> {
            val p = TextPaint().apply { color = CODE_TEXT; textSize = MONO; typeface = monoFont; isAntiAlias = true }
            LaidBlock.Code(
                buildLayout(block.code, p, (contentW - (CODE_INSET * 2).toInt()).coerceAtLeast(1), RATIO_CODE),
                codeBg
            )
        }
        is MdBlock.Divider -> LaidBlock.Divider(DIVIDER_COLOR)
        is MdBlock.Table -> {
            val p = TextPaint().apply { color = TEXT_COLOR; textSize = TABLE_SIZE; typeface = monoFont; isAntiAlias = true }
            // ★ 单元格里的 **加粗** / `代码` 也要解析，否则表里会直接出现 ** 字面量。
            //   但必须【逐格】解析再拼行：buildSpannable 的强调符号是全串配对，
            //   整行一起解析会让分散在两格里的字面 * 或 _ 配成一对而被吃掉
            //   （如 | user_name | created_at | 会画成 username | createdat）。
            fun cell(s: String) = buildSpannable(s, bodyFont, monoFont)
            // 用 SpannableStringBuilder.append(CharSequence) 拼接（它会把源 span 一起复制过来）；
            // 不能用 joinToString —— 它内部走 StringBuilder，span 会被丢掉
            fun joinCells(cells: List<String>): CharSequence = SpannableStringBuilder().apply {
                cells.forEachIndexed { i, c ->
                    if (i > 0) append("  |  ")
                    append(cell(c))
                }
            }
            val lines = SpannableStringBuilder().apply {
                append(joinCells(block.headers)).append('\n')
                append(block.headers.joinToString("  |  ") { "—" }).append('\n')
                block.rows.forEach { append(joinCells(it)).append('\n') }
            }.trimEnd()
            LaidBlock.Text(
                buildLayout(lines, p, contentW, RATIO_TABLE),
                topPad = TABLE_PAD_V, bottomPad = TABLE_PAD_V
            )
        }
    }

    /** inline Markdown → Spannable（**加粗** / *斜体* / `代码` / ~~删除线~~ / ++下划线++ / <u>下划线</u>） */
    private fun buildSpannable(text: String, bodyFont: Typeface, monoFont: Typeface): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        var rem = text
        while (rem.isNotEmpty()) {
            when {
                rem.startsWith("**") -> rem = appendSpan(rem, "**", sb) { s, e -> sb.setSpan(FakeBoldSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                rem.startsWith("__") -> rem = appendSpan(rem, "__", sb) { s, e -> sb.setSpan(FakeBoldSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                rem.startsWith("~~") -> rem = appendSpan(rem, "~~", sb) { s, e -> sb.setSpan(StrikethroughSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                rem.startsWith("++") -> rem = appendSpan(rem, "++", sb) { s, e -> sb.setSpan(UnderlineSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                rem.startsWith("<u>") -> rem = appendSpan(rem, "<u>", "</u>", sb) { s, e -> sb.setSpan(UnderlineSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                rem.startsWith("`") -> rem = appendSpan(rem, "`", sb) { s, e -> sb.setSpan(CustomTypefaceSpan(monoFont), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                rem.startsWith("*") && !rem.startsWith("**") -> rem = appendSpan(rem, "*", sb) { s, e -> sb.setSpan(StyleSpan(Typeface.ITALIC), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                rem.startsWith("_") && !rem.startsWith("__") -> rem = appendSpan(rem, "_", sb) { s, e -> sb.setSpan(StyleSpan(Typeface.ITALIC), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                else -> {
                    val next = listOfNotNull(
                        rem.indexOf("**").takeIf { it >= 0 },
                        rem.indexOf("__").takeIf { it >= 0 },
                        rem.indexOf("~~").takeIf { it >= 0 },
                        rem.indexOf("++").takeIf { it >= 0 },
                        rem.indexOf("<u>").takeIf { it >= 0 },
                        rem.indexOf('`').takeIf { it >= 0 },
                        rem.indexOf('*').takeIf { it >= 0 && !rem.startsWith("**") },
                        rem.indexOf('_').takeIf { it >= 0 && !rem.startsWith("__") }
                    ).minOrNull()
                    when {
                        next == null -> { sb.append(rem); rem = "" }
                        next > 0 -> { sb.append(rem.substring(0, next)); rem = rem.substring(next) }
                        else -> { sb.append(rem[0]); rem = rem.substring(1) }
                    }
                }
            }
        }
        return sb
    }

    private fun appendSpan(rem: String, open: String, sb: SpannableStringBuilder, style: (Int, Int) -> Unit): String {
        val end = rem.indexOf(open, open.length)
        if (end < 0) { sb.append(rem[0]); return rem.substring(1) }
        val s = sb.length; sb.append(rem.substring(open.length, end)); style(s, sb.length)
        return rem.substring(end + open.length)
    }

    private fun appendSpan(rem: String, open: String, close: String, sb: SpannableStringBuilder, style: (Int, Int) -> Unit): String {
        val end = rem.indexOf(close, open.length)
        if (end < 0) { sb.append(rem[0]); return rem.substring(1) }
        val s = sb.length; sb.append(rem.substring(open.length, end)); style(s, sb.length)
        return rem.substring(end + close.length)
    }
}

// ===== 分享 / 保存（文件 IO，调用方请放在 Dispatchers.IO）=====

/** 把 Bitmap 写成 cache 临时文件并走系统分享，返回是否成功 */
fun Context.shareBitmap(bitmap: Bitmap): Boolean = try {
    val file = File(cacheDir, "share_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    val uri = FileProvider.getUriForFile(this, "com.freechat.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, com.freechat.i18n.LocaleManager.strings().shareAction))
    true
} catch (_: Exception) { false }

/** 把 Bitmap 保存到 Pictures/FreeChat，返回是否成功 */
fun Context.saveBitmapToGallery(bitmap: Bitmap): Boolean {
    return try {
        val bytes = ByteArrayOutputStream().use { bos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, bos); bos.toByteArray()
        }
        val filename = "FreeChat_${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FreeChat")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) return false
            contentResolver.openOutputStream(uri)?.use { o -> o.write(bytes) } ?: return false
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FreeChat")
            dir.mkdirs()
            FileOutputStream(File(dir, filename)).use { it.write(bytes) }
        }
        true
    } catch (_: Exception) { false }
}

/** 把 Markdown 文本写成 .md 文件并系统分享，返回是否成功 */
fun Context.shareMarkdown(text: String): Boolean = try {
    val file = File(cacheDir, "share_${System.currentTimeMillis()}.md")
    file.writeText(text)
    val uri = FileProvider.getUriForFile(this, "com.freechat.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, com.freechat.i18n.LocaleManager.strings().shareAction))
    true
} catch (_: Exception) { false }

/** 把 Markdown 文本保存到 Downloads/FreeChat，返回是否成功 */
fun Context.saveMarkdown(text: String): Boolean {
    return try {
        val filename = "FreeChat_${System.currentTimeMillis()}.md"
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "text/markdown")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FreeChat")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) return false
            contentResolver.openOutputStream(uri)?.use { o -> o.write(bytes) } ?: return false
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FreeChat")
            dir.mkdirs()
            FileOutputStream(File(dir, filename)).use { it.write(bytes) }
        }
        true
    } catch (_: Exception) { false }
}
