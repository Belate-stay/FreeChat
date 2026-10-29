package com.freechat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 数学公式小排版（2026-10-28 工单）：标准模式 AI 输出里的公式全部正常显示。
 * LaTeX 子集 → 共享语法树；行内（$…$）走 span 特技（上标基线移、根号上划线、分数升降），
 * 块级（$$…$$）走真排版（叠式分数、根号横线、上下限、矩阵网格）。解析不动就按原文兜底。
 */
internal sealed class MathNode {
    data class Sym(val text: String, val upright: Boolean = false) : MathNode()
    data class Seq(val items: List<MathNode>) : MathNode()
    data class Frac(val num: MathNode, val den: MathNode) : MathNode()
    data class Sqrt(val index: MathNode?, val body: MathNode) : MathNode()
    data class Overline(val body: MathNode) : MathNode()
    data class Script(val base: MathNode, val sup: MathNode?, val sub: MathNode?) : MathNode()
    /** 环境网格（matrix/cases/aligned…）：rows 行、每行若干格；left/right 为包裹符号（可空）。 */
    data class Grid(val rows: List<List<MathNode>>, val left: String, val right: String) : MathNode()
    /** display 内的 `\\` 换行。 */
    object Break : MathNode()
}

// ========== 符号表（LaTeX 命令 → Unicode，工单口径「全部」） ==========
private val GREEK = mapOf(
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε",
    "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ",
    "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
    "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ", "sigma" to "σ",
    "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ", "phi" to "φ", "varphi" to "ϕ",
    "chi" to "χ", "psi" to "ψ", "omega" to "ω",
    "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ",
    "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
)
private val MATH_SYMBOLS = mapOf(
    "pm" to "±", "mp" to "∓", "times" to "×", "div" to "÷", "cdot" to "·", "cdotp" to "·",
    "ast" to "∗", "star" to "⋆", "circ" to "∘", "bullet" to "∙", "setminus" to "∖",
    "cup" to "∪", "cap" to "∩", "sqcup" to "⊔", "sqcap" to "⊓",
    "subset" to "⊂", "supset" to "⊃", "subseteq" to "⊆", "supseteq" to "⊇",
    "in" to "∈", "notin" to "∉", "ni" to "∋", "emptyset" to "∅", "varnothing" to "∅",
    "neg" to "¬", "lnot" to "¬", "land" to "∧", "lor" to "∨", "wedge" to "∧", "vee" to "∨",
    "forall" to "∀", "exists" to "∃", "nexists" to "∄",
    "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔", "Longrightarrow" to "⟹",
    "implies" to "⇒", "iff" to "⇔", "to" to "→", "rightarrow" to "→", "leftarrow" to "←",
    "leftrightarrow" to "↔", "mapsto" to "↦", "longrightarrow" to "⟶", "uparrow" to "↑",
    "downarrow" to "↓", "nearrow" to "↗", "searrow" to "↘",
    "approx" to "≈", "equiv" to "≡", "neq" to "≠", "ne" to "≠", "sim" to "∼", "simeq" to "≃",
    "cong" to "≅", "propto" to "∝", "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥",
    "ll" to "≪", "gg" to "≫", "prec" to "≺", "succ" to "≻", "perp" to "⊥", "parallel" to "∥",
    "mid" to "∣", "nmid" to "∤", "angle" to "∠", "triangle" to "△", "square" to "□",
    "oplus" to "⊕", "ominus" to "⊖", "otimes" to "⊗", "odot" to "⊙", "circleddash" to "⊝",
    "degree" to "°", "prime" to "′", "dprime" to "″", "circ" to "∘",
    "infty" to "∞", "partial" to "∂", "nabla" to "∇", "hbar" to "ℏ", "ell" to "ℓ",
    "Re" to "ℜ", "Im" to "ℑ", "aleph" to "ℵ", "hslash" to "ℏ", "wp" to "℘",
    "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮", "oiint" to "∯",
    "sum" to "∑", "prod" to "∏", "coprod" to "∐", "bigcup" to "⋃", "bigcap" to "⋂",
    "bigvee" to "⋁", "bigwedge" to "⋀", "bigoplus" to "⨁", "bigotimes" to "⨂",
    "lim" to "lim", "argmax" to "argmax", "argmin" to "argmin",
    "ldots" to "…", "cdots" to "⋯", "vdots" to "⋮", "ddots" to "⋱", "dots" to "…",
    "because" to "∵", "therefore" to "∴", "checkmark" to "✓", "dagger" to "†", "ddagger" to "‡",
    "clubsuit" to "♣", "heartsuit" to "♥", "spadesuit" to "♠", "diamondsuit" to "♢",
    "langle" to "⟨", "rangle" to "⟩", "lfloor" to "⌊", "rfloor" to "⌋", "lceil" to "⌈", "rceil" to "⌉",
    "vert" to "|", "Vert" to "‖", "backslash" to "\\", "S" to "§", "P" to "¶",
    "alefsym" to "ℵ", "empty" to "∅", "blacksquare" to "■", "blacktriangle" to "▲",
    "leqslant" to "⩽", "geqslant" to "⩾", "lessapprox" to "⪅", "gtrapprox" to "⪆",
    "lll" to "⋘", "ggg" to "⋙", "triangleq" to "≜", "coloneqq" to "≔",
    "rightharpoonup" to "⇀", "leftharpoonup" to "↼", "rightleftharpoons" to "⇌",
    "vartriangleleft" to "⊲", "vartriangleright" to "⊳", "trianglelefteq" to "⊴",
    "triangleqright" to "⊵", "triangleq" to "≜",
)
private val UPSHIFT_COMMANDS = mapOf(
    "sin" to "sin", "cos" to "cos", "tan" to "tan", "cot" to "cot", "sec" to "sec", "csc" to "csc",
    "arcsin" to "arcsin", "arccos" to "arccos", "arctan" to "arctan",
    "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh", "coth" to "coth",
    "log" to "log", "ln" to "ln", "lg" to "lg", "exp" to "exp", "det" to "det", "dim" to "dim",
    "ker" to "ker", "hom" to "hom", "gcd" to "gcd", "deg" to "deg", "mod" to "mod",
    "min" to "min", "max" to "max", "sup" to "sup", "inf" to "inf", "lim" to "lim",
    "limsup" to "lim sup", "liminf" to "lim inf",
)
private val BLACKBOARD = mapOf(
    "R" to "ℝ", "C" to "ℂ", "N" to "ℕ", "Z" to "ℤ", "Q" to "ℚ", "P" to "ℙ", "H" to "ℍ", "E" to "𝔼",
)

// ========== 解析 ==========
internal fun parseMath(src: String): MathNode = try {
    val seq = MathScanner(src).parseSeq(null)
    if (seq.size == 1) seq[0] else MathNode.Seq(seq)
} catch (_: Throwable) {
    // 模型输出形状不可控：Exception 之外还有递归 StackOverflowError，一律原文兜底绝不崩
    MathNode.Sym(src, upright = true)
}

private class MathScanner(private val s: String) {
    private var i = 0

    private fun skipSpace() { while (i < s.length && s[i] in " \t") i++ }

    /** 解析到 stop 字符（吃掉它）或结尾。 */
    fun parseSeq(stop: Char?): List<MathNode> {
        val out = mutableListOf<MathNode>()
        while (i < s.length) {
            val c = s[i]
            if (stop != null && c == stop) { i++; return out }
            when {
                c == '\\' && i + 1 < s.length && s[i + 1] == '\\' -> {
                    out.add(MathNode.Break)
                    i += 2
                }
                c == '&' -> { out.add(MathNode.Sym(" ", upright = true)); i++ }
                c in " \t\n" -> { i++ }
                c == '%' -> { out.add(MathNode.Sym("%", upright = true)); i++ }
                c == '^' || c == '_' -> { out.add(MathNode.Sym(c.toString(), upright = true)); i++ }
                else -> out.add(parsePrimary())
            }
        }
        return out
    }

    /** 原子 + 上下标后缀（x^2_i 合成一个 Script）。 */
    private fun parsePrimary(): MathNode {
        val base = parseOperand()
        var sup: MathNode? = null
        var sub: MathNode? = null
        while (i < s.length && (s[i] == '^' || s[i] == '_')) {
            val isSup = s[i] == '^'
            i++
            // 上下标只吃单个原子/组：x^2_i 的 ^2 与 _i 必须各归各位，不许互相吞并（2026-10-28 返修）
            val value = parseOperand()
            if (isSup) sup = value else sub = value
        }
        return if (sup != null || sub != null) MathNode.Script(base, sup, sub) else base
    }

    /** 单个原子（组/命令/字符/数字），不再吞后随上下标。 */
    private fun parseOperand(): MathNode = when {
        i >= s.length -> MathNode.Sym("")
        s[i] == '{' -> { i++; MathNode.Seq(parseSeq('}')) }
        s[i] == '\\' -> parseCommand()
        s[i].isLetter() -> { val c = s[i]; i++; MathNode.Sym(c.toString()) }
        s[i].isDigit() -> {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == ',')) i++
            MathNode.Sym(s.substring(start, i), upright = true)
        }
        else -> { val c = s[i]; i++; MathNode.Sym(c.toString(), upright = true) }
    }

    private fun group(): MathNode {
        skipSpace()
        return if (i < s.length && s[i] == '{') { i++; MathNode.Seq(parseSeq('}')) }
        else parsePrimary()
    }

    /** \text{…} 原文取组：按深度扫到配对 }，空格原样保留。 */
    private fun rawGroupText(): String {
        skipSpace()
        if (i >= s.length || s[i] != '{') return ""
        i++
        val start = i
        var depth = 1
        while (i < s.length && depth > 0) {
            val c = s[i]
            if (c == '\\') { i++; if (i < s.length) i++; continue }
            if (c == '{') depth++ else if (c == '}') depth--
            if (depth == 0) break
            i++
        }
        val raw = s.substring(start, i)
        if (i < s.length && s[i] == '}') i++
        return raw
    }

    /** \left/\right 后的定界符：单字符或转义命令（\{ \} \langle …）。 */
    private fun delimiterText(): String {
        skipSpace()
        if (i >= s.length) return ""
        if (s[i] != '\\') {
            val d = s[i]; i++
            return when (d) {
                '{' -> "{"; '}' -> "}"; '.' -> ""
                else -> d.toString()
            }
        }
        val node = parseCommand()
        return textOf(node)
    }

    private fun parseCommand(): MathNode {
        i++ // 吃掉 \
        if (i >= s.length) return MathNode.Sym("\\", upright = true)
        val c = s[i]
        if (!c.isLetter()) {
            // \, \; \: \! 距离；\\ 换行；\$ \_ \% \{ \} 转义
            i++
            return when (c) {
                ',' , ';' , ':' , ' ' -> MathNode.Sym(" ", upright = true)
                '!' -> MathNode.Sym("", upright = true)
                '\\' -> MathNode.Break
                '$', '_', '%', '{', '}', '#', '&', '^' -> MathNode.Sym(c.toString(), upright = true)
                else -> MathNode.Sym(c.toString(), upright = true)
            }
        }
        val start = i
        while (i < s.length && s[i].isLetter()) i++
        val name = s.substring(start, i)
        return when {
            name in setOf("frac", "dfrac", "tfrac", "cfrac") -> MathNode.Frac(group(), group())
            name == "sqrt" -> {
                skipSpace()
                val index = if (i < s.length && s[i] == '[') { i++; MathNode.Seq(parseSeq(']')) } else null
                MathNode.Sqrt(index, group())
            }
            name == "overline" -> MathNode.Overline(group())
            name in setOf("text", "textrm", "mathrm", "operatorname", "mathbf", "mathit", "mathsf", "mathtt", "mathnormal", "mbox") -> {
                // 原文取到配对 }：空格是 \text 的命（"if and only if" 不能拼成 "ifandonlyif"，2026-10-28 返修）
                MathNode.Sym(rawGroupText(), upright = true)
            }
            name == "mathbb" || name == "Bbb" -> {
                val raw = rawGroupText()
                MathNode.Sym(raw.map { BLACKBOARD[it.toString()] ?: it.toString() }.joinToString(""), upright = true)
            }
            name == "left" || name == "right" -> MathNode.Sym(delimiterText(), upright = true)
            name in setOf("big", "Big", "bigg", "Bigg", "bigl", "Bigl", "bigr", "Bigr",
                "biggl", "Biggl", "biggr", "Biggr") -> {
                skipSpace()
                if (i < s.length && !s[i].isLetter()) { val d = s[i]; i++; MathNode.Sym(d.toString(), upright = true) }
                else MathNode.Sym("", upright = true)
            }
            name in setOf("quad", "qquad", "displaystyle", "textstyle", "scriptstyle",
                "notag", "nonumber", "limits", "nolimits", "left.", "right.") -> MathNode.Sym(" ", upright = true)
            name.startsWith("begin") || name.startsWith("end") -> parseEnvironment(name.startsWith("begin"))
            name in MATH_SYMBOLS -> {
                val sym = MATH_SYMBOLS.getValue(name)
                MathNode.Sym(sym, upright = sym.none { it.isLetter() })
            }
            name in GREEK -> MathNode.Sym(GREEK.getValue(name))
            name in UPSHIFT_COMMANDS -> MathNode.Sym(UPSHIFT_COMMANDS.getValue(name), upright = true)
            else -> MathNode.Sym(name.replace('_', ' '), upright = true)
        }
    }

    /** \begin{env}…\end{env}：matrix/cases/aligned 系按 \\ 分行、& 分格。 */
    private fun parseEnvironment(begin: Boolean): MathNode {
        skipSpace()
        if (i < s.length && s[i] == '{') {
            val envStart = i + 1
            val close = s.indexOf('}', i)
            if (close > 0) i = close + 1 else i = s.length
            val env = s.substring(envStart, (close).coerceAtLeast(envStart))
            if (!begin) return MathNode.Sym("", upright = true)
            val (left, right) = when (env) {
                "pmatrix" -> "(" to ")"
                "bmatrix" -> "[" to "]"
                "vmatrix" -> "|" to "|"
                "Vmatrix" -> "‖" to "‖"
                "matrix" -> "" to ""
                "cases" -> "{" to ""
                else -> "" to ""
            }
            val rows = mutableListOf<MutableList<MathNode>>()
            var cells = mutableListOf<MathNode>()
            var buf = StringBuilder()
            var braceDepth = 0
            var envDepth = 0
            fun flushCell() {
                cells.add(parseMath(buf.toString()))
                buf = StringBuilder()
            }
            var j = i
            while (j < s.length) {
                val c = s[j]
                if (c == '\\' && j + 1 < s.length) {
                    val nxt = s[j + 1]
                    if (nxt == '\\' && braceDepth == 0 && envDepth == 0) {
                        flushCell(); rows.add(cells); cells = mutableListOf(); j += 2; continue
                    }
                    // \{ \} 转义字符不参与深度计数（2026-10-28 返修）
                    if (nxt == '{' || nxt == '}') { buf.append(c).append(nxt); j += 2; continue }
                    if (s.startsWith("\\begin{", j)) { envDepth++; buf.append("\\begin{"); j += 7; continue }
                    if (s.startsWith("\\end{", j)) {
                        val close = s.indexOf('}', j + 5)
                        val cmd = if (close > 0) s.substring(j, close + 1) else s.substring(j)
                        if (envDepth == 0) {
                            flushCell(); rows.add(cells)
                            i = j + cmd.length
                            // 尾部 \\ 收尾不留空行（2026-10-28 返修）
                            while (rows.isNotEmpty() && rows.last().all { it is MathNode.Seq && it.items.isEmpty() }) {
                                rows.removeAt(rows.size - 1)
                            }
                            return MathNode.Grid(rows, left, right)
                        }
                        envDepth--
                        buf.append(cmd); j += cmd.length; continue
                    }
                    buf.append(c).append(nxt); j += 2; continue
                }
                if (c == '{') braceDepth++
                if (c == '}' && braceDepth > 0) braceDepth--
                if (braceDepth == 0 && c == '&') { flushCell(); j++; continue }
                buf.append(c); j++
            }
            flushCell(); rows.add(cells)
            i = s.length
            return MathNode.Grid(rows, left, right)
        }
        return MathNode.Sym("", upright = true)
    }

    private fun textOf(node: MathNode): String = when (node) {
        is MathNode.Sym -> node.text
        is MathNode.Seq -> node.items.joinToString("") { textOf(it) }
        is MathNode.Break -> " "
        else -> ""
    }
}

/** 行内/块级共用的「公式 → 朗读纯文本」：TTS 与复制兜底用。 */
internal fun mathToPlainText(src: String): String {
    fun walk(n: MathNode, sb: StringBuilder) {
        when (n) {
            is MathNode.Sym -> sb.append(n.text)
            is MathNode.Seq -> n.items.forEach { walk(it, sb) }
            is MathNode.Frac -> { walk(n.num, sb); sb.append('⁄'); walk(n.den, sb) }
            is MathNode.Sqrt -> { n.index?.let { walk(it, sb) }; sb.append('√'); walk(n.body, sb) }
            is MathNode.Overline -> walk(n.body, sb)
            is MathNode.Script -> { walk(n.base, sb); n.sup?.let { walk(it, sb) }; n.sub?.let { walk(it, sb) } }
            is MathNode.Grid -> {
                sb.append(n.left)
                n.rows.forEach { r -> r.forEach { walk(it, sb); sb.append(' ') }; sb.append(' ') }
                sb.append(n.right)
            }
            is MathNode.Break -> sb.append(' ')
        }
    }
    val sb = StringBuilder()
    walk(parseMath(src), sb)
    return sb.toString()
}

// ========== 行内渲染（AnnotatedString span 特技） ==========
internal fun AnnotatedString.Builder.appendMathInline(node: MathNode, fs: TextUnit) {
    when (node) {
        is MathNode.Sym -> append(node.text)
        is MathNode.Seq -> node.items.forEach { appendMathInline(it, fs) }
        is MathNode.Break -> append(' ')
        is MathNode.Frac -> {
            // 行内分数：升分子 ⁄ 降分母（真排版留给块级）
            withStyle(SpanStyle(baselineShift = BaselineShift(0.26f), fontSize = (fs.value * 0.8f).sp)) {
                appendMathInline(node.num, (fs.value * 0.8f).sp)
            }
            append('⁄')
            withStyle(SpanStyle(baselineShift = BaselineShift(-0.26f), fontSize = (fs.value * 0.8f).sp)) {
                appendMathInline(node.den, (fs.value * 0.8f).sp)
            }
        }
        is MathNode.Sqrt -> {
            append('√')
            if (node.index != null) {
                withStyle(SpanStyle(baselineShift = BaselineShift(0.45f), fontSize = (fs.value * 0.62f).sp)) {
                    appendMathInline(node.index, (fs.value * 0.62f).sp)
                }
            }
            // 行内没有上划线可画：复合根号体用括号界定（√(b²-4ac)），单原子裸写（√2）
            val compound = node.body !is MathNode.Sym
            if (compound) append('(')
            appendMathInline(node.body, fs)
            if (compound) append(')')
        }
        is MathNode.Overline -> appendMathInline(node.body, fs)
        is MathNode.Script -> {
            appendMathInline(node.base, fs)
            node.sup?.let {
                withStyle(SpanStyle(baselineShift = BaselineShift(0.34f), fontSize = (fs.value * 0.7f).sp)) {
                    appendMathInline(it, (fs.value * 0.7f).sp)
                }
            }
            node.sub?.let {
                withStyle(SpanStyle(baselineShift = BaselineShift(-0.3f), fontSize = (fs.value * 0.7f).sp)) {
                    appendMathInline(it, (fs.value * 0.7f).sp)
                }
            }
        }
        is MathNode.Grid -> {
            append(node.left)
            node.rows.forEachIndexed { r, row ->
                if (r > 0) append("  ")
                row.forEachIndexed { c, cell ->
                    if (c > 0) append(", ")
                    appendMathInline(cell, fs)
                }
            }
            append(node.right)
        }
    }
}

// ========== 块级渲染（真排版：叠式分数/根号横线/上下限/网格） ==========
@Composable
internal fun MathFormula(node: MathNode, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    when (node) {
        is MathNode.Sym -> Text(node.text, style = style, color = color,
            fontStyle = if (node.upright) FontStyle.Normal else FontStyle.Italic, modifier = modifier)
        is MathNode.Seq -> Row(modifier, verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            node.items.forEach { MathFormula(it, style, color) }
        }
        is MathNode.Break -> {}
        is MathNode.Frac -> Row(modifier.width(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                MathFormula(node.num, style, color, Modifier.padding(horizontal = 3.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(color))
                MathFormula(node.den, style, color, Modifier.padding(horizontal = 3.dp))
            }
        }
        is MathNode.Sqrt -> Row(modifier, verticalAlignment = Alignment.Bottom) {
            node.index?.let {
                // 缩放必须成对缩 lineHeight（2026-10-28 返修：只缩字号行盒不缩=上下标不升不降）
                MathFormula(it, style.copy(fontSize = style.fontSize * 0.62f,
                    lineHeight = style.fontSize * 0.62f * 1.15f), color, Modifier.padding(bottom = 6.dp))
            }
            Text("√", style = style, color = color)
            Row(Modifier.width(IntrinsicSize.Min), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
                    MathFormula(node.body, style, color, Modifier.padding(horizontal = 2.dp))
                }
            }
        }
        is MathNode.Overline -> Row(modifier.width(IntrinsicSize.Min), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(color))
                MathFormula(node.body, style, color)
            }
        }
        is MathNode.Script -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            MathFormula(node.base, style, color)
            // 上下标成对缩放 fontSize+lineHeight、按 base 中线分置上下（2026-10-28 返修）
            Column(verticalArrangement = Arrangement.spacedBy(0.dp), horizontalAlignment = Alignment.Start) {
                node.sup?.let {
                    MathFormula(it, style.copy(fontSize = style.fontSize * 0.66f,
                        lineHeight = style.fontSize * 0.66f * 1.15f), color, Modifier.padding(bottom = 2.dp))
                }
                node.sub?.let {
                    MathFormula(it, style.copy(fontSize = style.fontSize * 0.66f,
                        lineHeight = style.fontSize * 0.66f * 1.15f), color)
                }
            }
        }
        is MathNode.Grid -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            val big = style.copy(fontSize = style.fontSize * 1.35f, lineHeight = style.fontSize * 1.35f * 1.15f)
            if (node.left.isNotEmpty()) Text(node.left, style = big, color = color)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                node.rows.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { cell -> MathFormula(cell, style, color) }
                    }
                }
            }
            if (node.right.isNotEmpty()) Text(node.right, style = big, color = color)
        }
    }
}

/** MarkdownText 的块级公式入口：整段居中、超宽横滑；display 的 `\\` 分行（2026-10-28 返修）。 */
@Composable
internal fun MathFormulaBlock(source: String, textColor: Color, modifier: Modifier = Modifier) {
    val rows = remember(source) {
        val items = when (val n = parseMath(source)) {
            is MathNode.Seq -> n.items
            else -> listOf(n)
        }
        val out = mutableListOf<MathNode>()
        val buf = mutableListOf<MathNode>()
        items.forEach {
            if (it is MathNode.Break) { out.add(MathNode.Seq(buf.toList())); buf.clear() } else buf.add(it)
        }
        out.add(MathNode.Seq(buf.toList()))
        out.filterNot { it is MathNode.Seq && it.items.isEmpty() }
    }
    Column(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(verticalAlignment = Alignment.Bottom) {
                MathFormula(row, MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp),
                    textColor, Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
