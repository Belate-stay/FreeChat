package com.freechat.ui.components

import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 数学公式显示（2026-10-28 工单）：公式/数学符号全部正常显示的解析与渲染契约。 */
class MathTextTest {

    @Test fun symbolsTranslateToUnicode() {
        val plain = mathToPlainText("""\infty \int \sum \prod \partial \nabla \pm \times \cdot \leq \geq \neq \approx \equiv \to \Rightarrow \in \subset \cup \cap \forall \exists \alpha \beta \pi \theta \lambda \Omega""")
        for (ch in listOf("∞", "∫", "∑", "∏", "∂", "∇", "±", "×", "·", "≤", "≥", "≠", "≈", "≡", "→", "⇒", "∈", "⊂", "∪", "∩", "∀", "∃", "α", "β", "π", "θ", "λ", "Ω")) {
            assertTrue("符号必须落成 Unicode：$ch（得到 $plain）", plain.contains(ch))
        }
    }

    @Test fun percentFractionsRootsAndScriptsReadNormally() {
        assertEquals("50%", mathToPlainText("""50\%"""))
        assertEquals("1⁄2", mathToPlainText("""\frac{1}{2}"""))
        assertTrue(mathToPlainText("""\sqrt{2}""").startsWith("√"))
        val inline = buildAnnotatedString { appendMathInline(parseMath("""\frac{a+b}{2}"""), 15.sp) }.text
        assertEquals("行内/块级降级同形（升分子⁄降分母）", "a+b⁄2", inline)
    }

    @Test fun parserBuildsTheExpectedTree() {
        assertTrue(parseMath("""\frac{1}{2}""") is MathNode.Frac)
        assertTrue(parseMath("""x^2""") is MathNode.Script)
        assertTrue(parseMath("""\sqrt[3]{x}""") is MathNode.Sqrt)
        assertTrue(parseMath("""\overline{AB}""") is MathNode.Overline)
        val grid = parseMath("""\begin{pmatrix} a & b \\ c & d \end{pmatrix}""")
        assertTrue("matrix/cases 环境解析成网格", grid is MathNode.Grid)
        grid as MathNode.Grid
        assertEquals("pmatrix 自带括号", "(", grid.left)
        assertEquals(2, grid.rows.size)
        val script = parseMath("""a_{i}^{2}""")
        assertTrue("上下标连写各归各位（2026-10-28 返修）", script is MathNode.Script)
        script as MathNode.Script
        assertTrue(script.sup != null && script.sub != null)
    }

    @Test fun textKeepsSpacesAndDelimitersTranslate() {
        assertEquals("if and only if", mathToPlainText("""\text{if and only if}"""))
        assertEquals("{x}", mathToPlainText("""\left\{x\right\}"""))
        assertTrue(mathToPlainText("""\left\langle x \right\rangle""").contains("⟨"))
    }

    @Test fun moneyIsNotMath() {
        assertEquals(-1, findInlineMathEnd("\$5 和 \$10"))
        assertEquals(-1, findInlineMathEnd("价格 \$ 5"))
        assertTrue(findInlineMathEnd("\$x^2\$ 的解") > 1)
    }

    @Test fun unknownCommandsDegradeToReadableTextNotCrash() {
        val plain = mathToPlainText("""\weirdcommand{x}""")
        assertTrue(plain.isNotBlank())
        assertEquals("数学解析绝不能抛", "1⁄2", mathToPlainText("""\frac{1}{2}"""))
    }

    @Test fun markdownPipelineWiresMathBlocksAndInline() {
        val parser = File("src/main/java/com/freechat/ui/components/MarkdownParser.kt").readText()
        assertTrue("块级公式进解析", parser.contains("data class Math(val source: String)") &&
            parser.contains("blocks.add(MdBlock.Math("))
        val text = File("src/main/java/com/freechat/ui/components/MarkdownText.kt").readText()
        assertTrue("块级走真排版", text.contains("MathFormulaBlock(block.source, textColor)"))
        assertTrue("行内 \$…\$ 走 span 渲染", text.contains("appendMathInline(parseMath("))
        assertTrue("金额不算公式", text.contains("fun findInlineMathEnd"))
        val math = File("src/main/java/com/freechat/ui/components/MathText.kt").readText()
        assertTrue("块级叠式分数/根号真横线", math.contains("IntrinsicSize.Min") &&
            math.contains("Box(Modifier.fillMaxWidth().height(1.dp).background(color))"))
    }
}
