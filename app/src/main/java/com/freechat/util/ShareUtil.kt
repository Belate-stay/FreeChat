package com.freechat.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.CharacterStyle
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

/** 分享图的一段：标签（你 / FreeChat）+ Markdown 原文 */
data class ShareSegment(val label: String, val text: String, val labelColor: Int)

/** 伪粗体：单字重字体（鸿蒙宋韵朗黑）加粗靠合成 */
private class FakeBoldSpan : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint) { tp.isFakeBoldText = true }
}

/** 自定义 Typeface span（行内代码用等宽字体） */
private class CustomTypefaceSpan(val typeface: Typeface) : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint) { tp.typeface = typeface }
}

/**
 * 分享图生成器：把消息 Markdown 渲染成美化长图（对齐知乎/绿洲图片导出样式）。
 * 顶部 FreeChat（Aurora 字体 + 加粗）+ 正文 Markdown 排版（鸿蒙宋韵朗黑）+ 底部 logo + 二维码。
 * 宽度固定 1080px，高度随内容自适应。
 */
object ShareImageGenerator {
    private const val WIDTH = 1080
    private const val PAD = 80
    private const val PAD_TOP = 88
    private const val TEXT_COLOR = 0xFF1B1B1B.toInt()
    private const val FAINT_LINE = 0xFFE8E8E8.toInt()

    private const val BODY = 40f
    private const val LABEL = 34f
    private const val TITLE = 58f
    private const val LINE_SPACING = 12f

    private const val TITLE_AREA_H = 124
    private const val LABEL_H = 48
    private const val LABEL_GAP = 14
    private const val SEG_GAP = 56
    private const val SEP_GAP = 56
    private const val LOGO_SIZE = 72
    private const val QR_SIZE = 200
    private const val BOTTOM_PAD = 84

    fun generate(context: Context, segments: List<ShareSegment>, accentColor: Int): Bitmap {
        val contentW = WIDTH - PAD * 2

        val titleFont = runCatching { ResourcesCompat.getFont(context, R.font.aurora) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val bodyFont = runCatching { ResourcesCompat.getFont(context, R.font.hysongyunlanghei) }.getOrNull() ?: Typeface.DEFAULT
        val monoFont = runCatching { ResourcesCompat.getFont(context, R.font.consola) }.getOrNull() ?: Typeface.MONOSPACE

        // ===== 阶段1：排版，计算高度 =====
        val laidSegs = segments.map { seg ->
            val blocks = parseMarkdown(seg.text).ifEmpty { listOf(MdBlock.Paragraph(seg.text)) }
            LaidSeg(seg.label, seg.labelColor, blocks.map { layoutBlock(it, contentW, bodyFont, monoFont, accentColor) })
        }

        val bodyTop = PAD_TOP + TITLE_AREA_H
        var bodyH = 0f
        laidSegs.forEach { seg ->
            if (seg.label.isNotBlank()) bodyH += LABEL_H + LABEL_GAP
            bodyH += seg.blocks.fold(0f) { acc, b -> acc + b.height }
        }
        bodyH += (laidSegs.size - 1).coerceAtLeast(0) * SEG_GAP
        val bottomH = SEP_GAP + 2 + 52 + LOGO_SIZE + BOTTOM_PAD
        val totalH = (bodyTop + bodyH + bottomH).toInt()

        val bmp = Bitmap.createBitmap(WIDTH, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        // ===== 顶部标题 =====
        val titlePaint = TextPaint().apply {
            color = accentColor; textSize = TITLE; typeface = titleFont
            isAntiAlias = true; isFakeBoldText = true
        }
        canvas.drawText("FreeChat", PAD.toFloat(), (PAD_TOP + TITLE - 6).toFloat(), titlePaint)
        val accentLine = Paint().apply { color = accentColor; strokeWidth = 4f }
        canvas.drawLine(PAD.toFloat(), (PAD_TOP + TITLE_AREA_H - 46).toFloat(), (WIDTH - PAD).toFloat(), (PAD_TOP + TITLE_AREA_H - 46).toFloat(), accentLine)

        // ===== 正文 =====
        var y = bodyTop.toFloat()
        laidSegs.forEachIndexed { si, seg ->
            if (seg.label.isNotBlank()) {
                val labelPaint = TextPaint().apply {
                    color = seg.labelColor; textSize = LABEL; typeface = bodyFont
                    isAntiAlias = true; isFakeBoldText = true
                }
                canvas.drawText(seg.label, PAD.toFloat(), y + LABEL_H - 12, labelPaint)
                y += LABEL_H + LABEL_GAP
            }
            seg.blocks.forEach { blk -> y = blk.draw(canvas, PAD.toFloat(), y, contentW) }
            if (si < laidSegs.size - 1) y += SEG_GAP
        }

        // ===== 底部分隔线 =====
        val sepY = y + SEP_GAP
        val faint = Paint().apply { color = FAINT_LINE; strokeWidth = 2f }
        canvas.drawLine(PAD.toFloat(), sepY, (WIDTH - PAD).toFloat(), sepY, faint)

        // ===== 底部：logo + FreeChat 文字（左）+ 二维码（右）=====
        val footerTop = sepY + 52
        val logo = runCatching { BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher) }.getOrNull()
        if (logo != null) {
            val scaled = Bitmap.createScaledBitmap(logo, LOGO_SIZE, LOGO_SIZE, true)
            canvas.drawBitmap(scaled, PAD.toFloat(), footerTop, null)
        }
        val brandPaint = TextPaint().apply {
            color = TEXT_COLOR; textSize = 38f; typeface = titleFont; isAntiAlias = true
        }
        canvas.drawText("FreeChat", (PAD + LOGO_SIZE + 22).toFloat(), footerTop + LOGO_SIZE / 2f + 13, brandPaint)

        val qrRaw = BitmapFactory.decodeResource(context.resources, R.drawable.freechat_qc)
        val qr = Bitmap.createScaledBitmap(qrRaw, QR_SIZE, QR_SIZE, true)
        val qrX = WIDTH - PAD - QR_SIZE
        val qrY = footerTop + (LOGO_SIZE - QR_SIZE) / 2f
        canvas.drawBitmap(qr, qrX.toFloat(), qrY, null)

        return bmp
    }

    // ===== 排版 =====

    private data class LaidSeg(val label: String, val labelColor: Int, val blocks: List<LaidBlock>)

    private sealed class LaidBlock {
        abstract val height: Float
        abstract fun draw(canvas: Canvas, x: Float, y: Float, contentW: Int): Float

        class Text(val layout: StaticLayout, val topPad: Float = 0f, val bottomPad: Float = 0f) : LaidBlock() {
            override val height get() = layout.height + topPad + bottomPad
            override fun draw(canvas: Canvas, x: Float, y: Float, contentW: Int): Float {
                canvas.save(); canvas.translate(x, y + topPad); layout.draw(canvas); canvas.restore()
                return y + height
            }
        }

        class IndentText(val layout: StaticLayout, val indent: Float, val topPad: Float = 0f, val bottomPad: Float = 0f) : LaidBlock() {
            override val height get() = layout.height + topPad + bottomPad
            override fun draw(canvas: Canvas, x: Float, y: Float, contentW: Int): Float {
                canvas.save(); canvas.translate(x + indent, y + topPad); layout.draw(canvas); canvas.restore()
                return y + height
            }
        }

        class Quote(val layout: StaticLayout, val barColor: Int) : LaidBlock() {
            override val height get() = layout.height + 24f
            override fun draw(canvas: Canvas, x: Float, y: Float, contentW: Int): Float {
                val bar = Paint().apply { color = barColor }
                canvas.drawRoundRect(x, y + 4, x + 6, y + height - 8, 3f, 3f, bar)
                canvas.save(); canvas.translate(x + 22, y + 12); layout.draw(canvas); canvas.restore()
                return y + height
            }
        }

        class Code(val layout: StaticLayout, val bg: Int) : LaidBlock() {
            override val height get() = layout.height + 32f
            override fun draw(canvas: Canvas, x: Float, y: Float, contentW: Int): Float {
                val bgPaint = Paint().apply { color = bg }
                canvas.drawRoundRect(x - 14, y, x + contentW + 14, y + height, 14f, 14f, bgPaint)
                canvas.save(); canvas.translate(x, y + 16); layout.draw(canvas); canvas.restore()
                return y + height
            }
        }

        class Divider(val color: Int) : LaidBlock() {
            override val height = 44f
            override fun draw(canvas: Canvas, x: Float, y: Float, contentW: Int): Float {
                val p = Paint().apply { color = color; strokeWidth = 2f }
                canvas.drawLine(x, y + 22, x + contentW, y + 22, p)
                return y + height
            }
        }
    }

    private fun layoutBlock(block: MdBlock, contentW: Int, bodyFont: Typeface, monoFont: Typeface, accent: Int): LaidBlock {
        val basePaint = TextPaint().apply { color = TEXT_COLOR; typeface = bodyFont; isAntiAlias = true }
        return when (block) {
            is MdBlock.Heading -> {
                val size = when (block.level) { 1 -> 54f; 2 -> 50f; 3 -> 46f; 4 -> 43f; else -> 41f }
                val p = TextPaint().apply { color = TEXT_COLOR; textSize = size; typeface = bodyFont; isAntiAlias = true; isFakeBoldText = true }
                val l = StaticLayout.Builder.obtain(block.text, 0, block.text.length, p, contentW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(LINE_SPACING, 1f).setIncludePad(false).build()
                LaidBlock.Text(l, topPad = 22f, bottomPad = 10f)
            }
            is MdBlock.Paragraph -> {
                val p = TextPaint().apply { color = TEXT_COLOR; textSize = BODY; typeface = bodyFont; isAntiAlias = true }
                val sp = buildSpannable(block.text, bodyFont, monoFont)
                val l = StaticLayout.Builder.obtain(sp, 0, sp.length, p, contentW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(LINE_SPACING, 1.18f).setIncludePad(false).build()
                LaidBlock.Text(l, bottomPad = 12f)
            }
            is MdBlock.ListItem -> {
                val p = TextPaint().apply { color = TEXT_COLOR; textSize = BODY; typeface = bodyFont; isAntiAlias = true }
                val text = "${block.bullet}  ${block.text}" + if (block.body.isEmpty()) "" else "\n" + block.body.joinToString("\n") { "      $it" }
                val sp = buildSpannable(text, bodyFont, monoFont)
                val l = StaticLayout.Builder.obtain(sp, 0, sp.length, p, contentW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(LINE_SPACING, 1.18f).setIncludePad(false).build()
                LaidBlock.IndentText(l, indent = (block.indent * 26).toFloat(), bottomPad = 8f)
            }
            is MdBlock.Quote -> {
                val p = TextPaint().apply { color = TEXT_COLOR; textSize = BODY; typeface = bodyFont; isAntiAlias = true }
                val sp = buildSpannable(block.text, bodyFont, monoFont)
                val l = StaticLayout.Builder.obtain(sp, 0, sp.length, p, contentW - 22)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(LINE_SPACING, 1.18f).setIncludePad(false).build()
                LaidBlock.Quote(l, accent)
            }
            is MdBlock.CodeBlock -> {
                val p = TextPaint().apply { color = 0xFF333333.toInt(); textSize = 32f; typeface = monoFont; isAntiAlias = true }
                val l = StaticLayout.Builder.obtain(block.code, 0, block.code.length, p, contentW - 32)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(8f, 1f).setIncludePad(false).build()
                LaidBlock.Code(l, 0xFFF5F5F5.toInt())
            }
            is MdBlock.Divider -> LaidBlock.Divider(FAINT_LINE)
            is MdBlock.Table -> {
                val p = TextPaint().apply { color = TEXT_COLOR; textSize = 34f; typeface = monoFont; isAntiAlias = true }
                val lines = buildString {
                    append(block.headers.joinToString("  |  ") { it }).append('\n')
                    append(block.headers.joinToString("  |  ") { "—" }).append('\n')
                    block.rows.forEach { append(it.joinToString("  |  ")).append('\n') }
                }.trimEnd()
                val l = StaticLayout.Builder.obtain(lines, 0, lines.length, p, contentW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(8f, 1f).setIncludePad(false).build()
                LaidBlock.Text(l, topPad = 12f, bottomPad = 12f)
            }
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
    startActivity(Intent.createChooser(intent, "分享"))
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
    startActivity(Intent.createChooser(intent, "分享"))
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
