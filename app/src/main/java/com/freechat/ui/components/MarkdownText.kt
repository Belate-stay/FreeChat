package com.freechat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.ui.theme.LocalChatFontFamily
import com.freechat.ui.theme.LocalFontScale
import com.freechat.ui.theme.LocalLatinFontFamily
import kotlin.math.max

/** CJK 标点禁则：标点（，。！？：；等）不出现在行首，悬挂到上一行行尾。Strict 映射 LINE_BREAK_STYLE_STRICT。 */
internal val cjkLineBreak = LineBreak(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Strict,
    wordBreak = LineBreak.WordBreak.Default
)

// ========== 块结构 ==========
internal sealed class MdBlock {
    data class Heading(val text: String, val level: Int) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class ListItem(val text: String, val bullet: String, val indent: Int, val body: MutableList<String> = mutableListOf()) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class CodeBlock(val code: String, val lang: String) : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
    object Divider : MdBlock()
}

// ========== 解析器 ==========
internal fun parseMarkdown(text: String): List<MdBlock> {
    val lines = text.split("\n")
    val blocks = mutableListOf<MdBlock>()
    var inCode = false
    val codeBuf = mutableListOf<String>()
    var codeLang = ""
    var tableLines = mutableListOf<String>()

    fun flushTable() {
        if (tableLines.size >= 2) {
            val headers = parseTableRow(tableLines[0])
            val rows = tableLines.drop(2).mapNotNull { line ->
                val row = parseTableRow(line)
                if (row.isNotEmpty()) row else null
            }
            if (headers.isNotEmpty()) {
                blocks.add(MdBlock.Table(headers, rows))
            }
        }
        tableLines.clear()
    }

    // 收集连续段落行，用于识别行内标题
    var pendingParagraphLines = mutableListOf<String>()
    fun flushParagraph() {
        if (pendingParagraphLines.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(pendingParagraphLines.joinToString("\n").trim()))
            pendingParagraphLines.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()

        // 代码块
        if (trimmed.startsWith("```")) {
            flushTable()
            flushParagraph()
            if (inCode) {
                if (codeBuf.isNotEmpty()) {
                    blocks.add(MdBlock.CodeBlock(codeBuf.joinToString("\n"), codeLang))
                    codeBuf.clear()
                    codeLang = ""
                }
                inCode = false
            } else {
                inCode = true
                codeLang = trimmed.removePrefix("```").trim()
            }
            continue
        }
        if (inCode) { codeBuf.add(line); continue }

        // 表格积累
        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            flushParagraph()
            tableLines.add(trimmed)
            continue
        } else if (tableLines.isNotEmpty()) {
            flushTable()
        }

        if (trimmed.isBlank()) {
            flushTable()
            flushParagraph()
            continue
        }

        // 分割线
        if (trimmed.matches(Regex("""^-{3,}$""")) || trimmed.matches(Regex("""^\*{3,}$"""))) {
            flushParagraph()
            blocks.add(MdBlock.Divider); continue
        }

        // 标题 — 支持 # 后有无空格均可
        val headingMatch = Regex("""^(#{1,5})\s*(.*)""").find(trimmed)
        if (headingMatch != null) {
            val content = headingMatch.groupValues[2].trim()
            if (content.isNotEmpty()) {
                flushParagraph()
                val level = headingMatch.groupValues[1].length
                blocks.add(MdBlock.Heading(content, level)); continue
            }
        }

        // 无序列表 — 支持 - * + 三种符号
        val ulMatch = Regex("""^(\s*)([-*+])\s+(.*)""").find(line)
        if (ulMatch != null) {
            flushParagraph()
            val indent = ulMatch.groupValues[1].length / 2
            val marker = ulMatch.groupValues[2]
            val bullet = when (marker) {
                "-" -> "•"
                "+" -> "▪"
                else -> "•"
            }
            blocks.add(MdBlock.ListItem(ulMatch.groupValues[3], bullet, indent)); continue
        }

        // 有序列表
        val olMatch = Regex("""^(\s*)(\d+)[.)]\s+(.*)""").find(line)
        if (olMatch != null) {
            flushParagraph()
            val indent = olMatch.groupValues[1].length / 2
            blocks.add(MdBlock.ListItem(olMatch.groupValues[3], "${olMatch.groupValues[2]}.", indent)); continue
        }

        // 引用
        if (trimmed.startsWith("> ")) {
            flushParagraph()
            blocks.add(MdBlock.Quote(trimmed.removePrefix("> "))); continue
        }
        if (trimmed.startsWith(">")) {
            flushParagraph()
            blocks.add(MdBlock.Quote(trimmed.removePrefix(">").trim())); continue
        }

        // 列表项下方的缩进续行 → 作为该要点的解释正文（缩进 + 加大行距）
        val lastBlock = blocks.lastOrNull()
        if (lastBlock is MdBlock.ListItem && trimmed.isNotEmpty() &&
            (line.startsWith(" ") || line.startsWith("\t"))
        ) {
            lastBlock.body.add(trimmed)
            continue
        }

        // 段落行 — 累积
        pendingParagraphLines.add(line)
    }

    flushTable()
    flushParagraph()
    if (codeBuf.isNotEmpty()) blocks.add(MdBlock.CodeBlock(codeBuf.joinToString("\n"), codeLang))
    return mergeParagraphs(blocks)
}

private fun parseTableRow(line: String): List<String> {
    return line.trim('|').split("|").map { it.trim() }
}

/** 估算单元格文本显示宽度（dp）：CJK/全角字符按 13、半角按 7，用于表格定列宽，保证横平竖直对齐 */
private fun tableTextWidthDp(text: String): Float {
    var w = 0f
    for (ch in text) {
        val c = ch.code
        w += if (c in 0x2E80..0x9FFF || c in 0x3000..0x303F || c in 0xFF00..0xFFEF || c in 0xAC00..0xD7AF) 13f else 7f
    }
    return w
}

private fun mergeParagraphs(blocks: List<MdBlock>): List<MdBlock> {
    // 段落保持独立成块：空行在渲染时体现为段间距，修复「空行被吞」导致排版拥挤
    return blocks
        .map { b -> if (b is MdBlock.Paragraph) MdBlock.Paragraph(b.text.trim()) else b }
        .filter { it !is MdBlock.Paragraph || it.text.isNotEmpty() }
}

// ========== 主组件 ==========
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color,
    codeBgColor: Color,
    quoteBarColor: Color,
    dividerColor: Color,
    highlightKeyword: String? = null,
    highlightColor: Color = Color.Unspecified
) {
    val blocks = remember(content) { parseMarkdown(content) }
    val baseSize = 15.sp
    val scale = LocalFontScale.current  // 字号联动：段距随字号缩放
    val chatFont = LocalChatFontFamily.current  // 聊天字体：用户提示词 + AI 回复（汉字/英文/数字统一用该字体）

    Column(modifier = modifier) {
        for ((index, block) in blocks.withIndex()) {
            val isLast = index == blocks.lastIndex
            val prevBlock = if (index > 0) blocks[index - 1] else null

            // 不同块类型之间空行；相邻段落（空行）之间留更大间距
            if (prevBlock != null) {
                if (prevBlock is MdBlock.Paragraph && block is MdBlock.Paragraph) {
                    Spacer(Modifier.height((10 * scale).dp))
                } else if (prevBlock::class != block::class) {
                    Spacer(Modifier.height((8 * scale).dp))
                }
            }

            when (block) {
                is MdBlock.Heading -> {
                    val fs = when (block.level) {
                        1 -> 22.sp; 2 -> 20.sp; 3 -> 17.sp; 4 -> 16.sp; else -> 15.sp
                    }
                    Spacer(modifier = Modifier.height(if (block.level <= 2) (18 * scale).dp else (10 * scale).dp))
                    SelectionContainer {
                        Text(
                            buildStyledLine(block.text, textColor, FontWeight.Bold, fs, chatFont, highlightKeyword, highlightColor),
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = fs * 1.4f
                        )
                    }
                    Spacer(modifier = Modifier.height(if (block.level <= 3) (6 * scale).dp else (2 * scale).dp))
                }

                is MdBlock.CodeBlock -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .clip(RoundedCornerShape(8.dp))
                            .background(codeBgColor)
                            .padding(12.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                block.code,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace, fontSize = 13.sp
                                ),
                                color = textColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                is MdBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Box(Modifier.width(3.dp).heightIn(min = 20.dp).background(quoteBarColor))
                        Spacer(Modifier.width(8.dp))
                        RichText(block.text, textColor.copy(alpha = 0.85f), codeBgColor,
                            textAlign = TextAlign.Start, fontFamily = chatFont,
                            highlightKeyword = highlightKeyword, highlightColor = highlightColor)
                    }
                }

                is MdBlock.ListItem -> {
                    val startPad = (block.indent * 16 + 4).dp
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = startPad, top = 3.dp, bottom = 3.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                block.bullet,
                                color = textColor.copy(alpha = 0.5f),
                                fontSize = baseSize,
                                modifier = Modifier.width(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            RichText(block.text, textColor, codeBgColor, textAlign = TextAlign.Start, fontFamily = chatFont,
                                highlightKeyword = highlightKeyword, highlightColor = highlightColor)
                        }
                        // 要点下方的解释正文（进一步缩进）
                        block.body.forEach { bodyLine ->
                            Spacer(Modifier.height(2.dp))
                            Box(Modifier.fillMaxWidth().padding(start = 20.dp)) {
                                RichText(bodyLine, textColor, codeBgColor, textAlign = TextAlign.Start, fontFamily = chatFont,
                                    highlightKeyword = highlightKeyword, highlightColor = highlightColor)
                            }
                        }
                    }
                }

                is MdBlock.Table -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    // ===== 表格 — 每列固定宽度 + 格内换行，保证竖方向横平竖直对齐（去卡片框架/斑马纹，表头加粗 + 细分隔线，横向滑动） =====
                    val colCount = block.headers.size
                    val colWidths = remember(block.headers, block.rows) {
                        (0 until colCount).map { ci ->
                            val header = block.headers.getOrElse(ci) { "" }
                            val widest = (block.rows.map { it.getOrElse(ci) { "" } } + header)
                                .maxByOrNull { tableTextWidthDp(it) } ?: ""
                            tableTextWidthDp(widest).coerceIn(44f, 340f) // dp
                        }
                    }
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier.horizontalScroll(scrollState)
                    ) {
                        // 表头（加粗、无背景，融入聊天底色；固定列宽，超长折行）
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            block.headers.forEachIndexed { ci, h ->
                                Box(
                                    modifier = Modifier
                                        .width(colWidths[ci].dp)
                                        .padding(horizontal = 8.dp)
                                ) {
                                    SelectionContainer {
                                        Text(
                                            h, color = textColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            softWrap = true
                                        )
                                    }
                                }
                            }
                        }
                        // 表头下细分隔线（淡色、无框）
                        Box(Modifier.width(colWidths.sum().dp).height(1.dp).background(dividerColor.copy(alpha = 0.6f)))
                        // 数据行（无交替背景；固定列宽，长文本格内换行）
                        block.rows.forEachIndexed { _, row ->
                            Row(modifier = Modifier.padding(vertical = 5.dp)) {
                                row.take(colCount).forEachIndexed { ci, cell ->
                                    Box(
                                        modifier = Modifier
                                            .width(colWidths[ci].dp)
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        RichText(
                                            cell, textColor, codeBgColor,
                                            fs = 12.sp,
                                            lineHeight = 20.sp,
                                            fontFamily = chatFont,
                                            highlightKeyword = highlightKeyword,
                                            highlightColor = highlightColor
                                        )
                                    }
                                }
                                // 补齐缺少的列
                                repeat(colCount - row.size) {
                                    Spacer(Modifier.width(colWidths.getOrElse(row.size) { 60f }.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                is MdBlock.Divider -> {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                    Spacer(Modifier.height(12.dp))
                }

                is MdBlock.Paragraph -> {
                    if (block.text.isNotBlank()) {
                        RichText(
                            block.text.replace("\n", "  \n"), textColor, codeBgColor,
                            textAlign = TextAlign.Start,  // 两端对齐，视觉上每行结尾更整齐
                            fontFamily = chatFont,
                            highlightKeyword = highlightKeyword, highlightColor = highlightColor
                        )
                    }
                }
            }
        }
    }
}

// ========== 行内格式化 — CJK 等宽优化 ==========
@Composable
private fun RichText(
    text: String, baseColor: Color, codeBg: Color,
    fs: androidx.compose.ui.unit.TextUnit = 15.sp,
    textAlign: TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit? = null,
    fontFamily: FontFamily = FontFamily.Default,
    highlightKeyword: String? = null,
    highlightColor: Color = Color.Unspecified
) {
    SelectionContainer {
        Text(
            buildStyledLineCJK(stripInlineMarkers(text), baseColor, FontWeight.Normal, fs, fontFamily, highlightKeyword, highlightColor),
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                lineBreak = cjkLineBreak
            ),
            textAlign = textAlign ?: TextAlign.Start,
            lineHeight = lineHeight ?: (fs * 1.75f),
            letterSpacing = 0.15.sp,
            softWrap = true
        )
    }
}

/** 剥离可能在行内残留的 markdown 标记头 */
private fun stripInlineMarkers(text: String): String {
    return text
        .replace(Regex("""(^|\n)\s*#{1,5}\s*"""), "$1")  // 行内残留的 # 标记
}

/** CJK 优化版：统一行高 + 微间距，混合中英文时行尾更整齐 */
private fun buildStyledLineCJK(
    text: String, baseColor: Color, baseWeight: FontWeight,
    fs: androidx.compose.ui.unit.TextUnit,
    fontFamily: FontFamily = FontFamily.Default,
    highlightKeyword: String? = null,
    highlightColor: Color = Color.Unspecified
) = buildStyledLine(text, baseColor, baseWeight, fs, fontFamily, highlightKeyword, highlightColor)

private fun buildStyledLine(
    text: String, baseColor: Color, baseWeight: FontWeight,
    fs: androidx.compose.ui.unit.TextUnit,
    fontFamily: FontFamily = FontFamily.Default,
    highlightKeyword: String? = null,
    highlightColor: Color = Color.Unspecified
) = buildAnnotatedString {
    pushStyle(SpanStyle(color = baseColor, fontWeight = baseWeight, fontSize = fs, fontFamily = fontFamily))
    var rem = text
    while (rem.isNotEmpty()) {
        // ~~删除线~~
        if (rem.startsWith("~~")) {
            val end = rem.indexOf("~~", 2)
            if (end > 0) {
                withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                    append(rem.substring(2, end))
                }
                rem = rem.substring(end + 2); continue
            }
        }
        // **加粗** — 必须先于 * 检查
        if (rem.startsWith("**")) {
            val end = rem.indexOf("**", 2)
            if (end > 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(rem.substring(2, end)) }
                rem = rem.substring(end + 2); continue
            }
        }
        // __加粗__ (备选语法)
        if (rem.startsWith("__")) {
            val end = rem.indexOf("__", 2)
            if (end > 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(rem.substring(2, end)) }
                rem = rem.substring(end + 2); continue
            }
        }
        // `行内代码`
        if (rem.startsWith("`")) {
            val end = rem.indexOf('`', 1)
            if (end > 0) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = (fs.value - 1).sp)) {
                    append(rem.substring(1, end))
                }
                rem = rem.substring(end + 1); continue
            }
        }
        // *斜体* — 确保不是 ** 的一部分
        if (rem.startsWith("*") && !rem.startsWith("**")) {
            val end = rem.indexOf("*", 1)
            if (end > 1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(rem.substring(1, end)) }
                rem = rem.substring(end + 1); continue
            }
        }
        // _斜体_ (备选语法)
        if (rem.startsWith("_") && !rem.startsWith("__")) {
            val end = rem.indexOf("_", 1)
            if (end > 1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(rem.substring(1, end)) }
                rem = rem.substring(end + 1); continue
            }
        }
        // ++下划线++
        if (rem.startsWith("++")) {
            val end = rem.indexOf("++", 2)
            if (end > 0) {
                withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) { append(rem.substring(2, end)) }
                rem = rem.substring(end + 2); continue
            }
        }
        // <u>下划线</u>
        if (rem.startsWith("<u>")) {
            val end = rem.indexOf("</u>", 3)
            if (end > 0) {
                withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) { append(rem.substring(3, end)) }
                rem = rem.substring(end + 4); continue
            }
        }

        // 找下一个特殊 token
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

        if (next != null && next > 0) {
            appendHighlighted(rem.substring(0, next), highlightKeyword, highlightColor)
            rem = rem.substring(next)
        } else if (next == null) {
            appendHighlighted(rem, highlightKeyword, highlightColor); rem = ""
        } else {
            // next == 0 but no rule matched — skip char to avoid infinite loop
            appendHighlighted(rem[0].toString(), highlightKeyword, highlightColor); rem = rem.substring(1)
        }
    }
    pop()
}

/** 在追加普通文本时，把搜索关键词用高亮色标红（大小写不敏感）；无关键词/颜色未指定则原样追加 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendHighlighted(
    text: String, keyword: String?, color: Color
) {
    if (text.isEmpty()) return
    if (keyword.isNullOrBlank() || color == Color.Unspecified) { append(text); return }
    val lower = text.lowercase()
    val kw = keyword.lowercase()
    var from = 0
    while (from < text.length) {
        val idx = lower.indexOf(kw, from)
        if (idx < 0) { append(text.substring(from)); return }
        if (idx > from) append(text.substring(from, idx))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) {
            append(text.substring(idx, idx + keyword.length))
        }
        from = idx + keyword.length
    }
}

/** 把普通文本按「URL / 英文 token」与「中文」分段，分别用 latinFont 和 cjkFont 追加，实现混合字体 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendWithFontSplit(
    text: String,
    cjkFont: FontFamily,
    latinFont: FontFamily
) {
    if (text.isEmpty()) return
    val regex = Regex("""(https?://[^\s]+|www\.[^\s]+|[A-Za-z0-9][A-Za-z0-9\-._/]*)""")
    var last = 0
    for (m in regex.findAll(text)) {
        if (m.range.first > last) {
            withStyle(SpanStyle(fontFamily = cjkFont)) { append(text.substring(last, m.range.first)) }
        }
        withStyle(SpanStyle(fontFamily = latinFont)) { append(m.value) }
        last = m.range.last + 1
    }
    if (last < text.length) {
        withStyle(SpanStyle(fontFamily = cjkFont)) { append(text.substring(last)) }
    }
}

/** 把 Markdown 全文转成纯文本（去除格式标识符，保留块结构与显示顺序），用于复制 */
fun markdownToPlainText(content: String): String {
    val blocks = parseMarkdown(content)
    if (blocks.isEmpty()) return content
    return blocks.joinToString("\n") { block ->
        when (block) {
            is MdBlock.Heading -> inlinePlain(block.text)
            is MdBlock.Paragraph -> inlinePlain(block.text)
            is MdBlock.ListItem -> {
                val head = "${block.bullet} ${inlinePlain(block.text)}"
                if (block.body.isEmpty()) head
                else head + "\n" + block.body.joinToString("\n") { "    ${inlinePlain(it)}" }
            }
            is MdBlock.Quote -> "> ${inlinePlain(block.text)}"
            is MdBlock.CodeBlock -> block.code
            is MdBlock.Table -> buildString {
                append(block.headers.joinToString(" | "))
                block.rows.forEach { append("\n").append(it.joinToString(" | ")) }
            }
            is MdBlock.Divider -> "---"
        }
    }
}

/** 行内纯文本：复用 buildStyledLine 的去除逻辑，确保与显示完全一致 */
private fun inlinePlain(text: String): String =
    buildStyledLine(stripInlineMarkers(text), Color.Unspecified, FontWeight.Normal, 15.sp).text
