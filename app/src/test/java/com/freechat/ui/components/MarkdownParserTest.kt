package com.freechat.ui.components

import org.junit.Assert.*
import org.junit.Test

class MarkdownParserTest {
    @Test fun proseContentIsIsolatedAndPreservesExactWhitespace() {
        val body = "  第一段。\n\n    第二段。  "
        val blocks = parseMarkdown("解释说明。\n\n```text 作文\n$body\n```\n\n修改建议。")
        assertEquals(3, blocks.size)
        assertEquals(body, (blocks[1] as MdBlock.CodeBlock).code)
        assertTrue(isProseBlock((blocks[1] as MdBlock.CodeBlock).lang))
        assertFalse(isProseBlock("kotlin"))
    }

    @Test fun outerFenceCanContainFencedMarkdownAndCopyDoesNotLoseIt() {
        val body = "# Readme\n\n```kotlin\nval n = 1\n```"
        val block = parseMarkdown("````markdown\n$body\n````").single() as MdBlock.CodeBlock
        assertEquals(body, block.code)
    }

    @Test fun streamingOpenFenceRendersWithoutClosingAndRetainsBlankLines() {
        val block = parseMarkdown("```text\n原文\n\n").single() as MdBlock.CodeBlock
        assertEquals("原文\n\n", block.code)
        assertTrue(parseMarkdown("```text").single() is MdBlock.CodeBlock)
        assertEquals("  x\n    y", (parseMarkdown("~~~python\r\n  x\r\n    y\r\n~~~").single() as MdBlock.CodeBlock).code)
    }
}
