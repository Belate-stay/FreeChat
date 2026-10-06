package com.freechat.ui.components

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
    val lines = text.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    var inCode = false
    var fenceChar = '`'
    var fenceLength = 3
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
            blocks.add(MdBlock.Paragraph(softJoin(pendingParagraphLines).trim()))
            pendingParagraphLines.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()

        // CommonMark fences: a closing fence has the same character and at least the opening length.
        // A shorter fence inside a Markdown document is literal text and must survive copying.
        val marker = trimmed.firstOrNull()
        val run = if (marker == '`' || marker == '~') trimmed.takeWhile { it == marker }.length else 0
        if (inCode) {
            if (marker == fenceChar && run >= fenceLength && trimmed.drop(run).isBlank()) {
                blocks.add(MdBlock.CodeBlock(codeBuf.joinToString("\n"), codeLang))
                codeBuf.clear()
                codeLang = ""
                inCode = false
            } else codeBuf.add(line)
            continue
        }
        if (run >= 3) {
            flushTable()
            flushParagraph()
            inCode = true
            fenceChar = marker!!
            fenceLength = run
            codeLang = trimmed.drop(run).trim()
            continue
        }

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
    if (inCode) blocks.add(MdBlock.CodeBlock(codeBuf.joinToString("\n"), codeLang))
    return mergeParagraphs(blocks)
}

/**
 * 段落里的单换行按 Markdown 的规矩当**软换行** —— 合成一行，交给排版按屏幕宽度重新折。
 *
 * 原先的做法是保留 "\n"、渲染时再换成 `"  \n"`（Markdown 的硬换行），于是
 * **模型自己在哪折的行，屏幕上就在哪断**。模型是按它那边的宽度折的，到手机上
 * 经常一句话走到一半、后面空半行再接着写 —— 用户点名要的就是这个别再来。
 *
 * 接缝处要不要补空格看两侧是不是 CJK：中文之间补空格会凭空多出一道缝，
 * 英文之间不补又会把两个词粘成一个。
 */
private fun softJoin(lines: List<String>): String {
    if (lines.size <= 1) return lines.firstOrNull().orEmpty()
    val sb = StringBuilder()
    for (line in lines) {
        if (sb.isEmpty()) { sb.append(line); continue }
        val prev = sb.last()
        val next = line.firstOrNull()
        val glue = if (isCjk(prev) && (next == null || isCjk(next))) "" else " "
        sb.append(glue).append(line)
    }
    return sb.toString()
}

private fun isCjk(ch: Char): Boolean {
    val c = ch.code
    return c in 0x2E80..0x9FFF || c in 0x3000..0x303F || c in 0xFF00..0xFFEF || c in 0xAC00..0xD7AF
}

private fun parseTableRow(line: String): List<String> {
    return line.trim('|').split("|").map { it.trim() }
}

private fun mergeParagraphs(blocks: List<MdBlock>): List<MdBlock> {
    // 段落保持独立成块：空行在渲染时体现为段间距，修复「空行被吞」导致排版拥挤
    return blocks
        .map { b -> if (b is MdBlock.Paragraph) MdBlock.Paragraph(b.text.trim()) else b }
        .filter { it !is MdBlock.Paragraph || it.text.isNotEmpty() }
}

/** Prose wraps visually, while the underlying text (and copy action) keeps every newline and space. */
internal fun isProseBlock(lang: String): Boolean =
    lang.substringBefore(' ').lowercase() in setOf("text", "txt", "plaintext", "plain", "prose", "essay", "正文", "原文", "文案")

