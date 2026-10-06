package com.freechat.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight

/** Includes quotation marks, nested/multiline speech, and optionally the unfinished live quote. */
object NarrativeDialogueText {
    fun ranges(text: String, streaming: Boolean = false): List<IntRange> {
        val open = ArrayDeque<Pair<Char, Int>>()
        val result = mutableListOf<IntRange>()
        text.forEachIndexed { index, char ->
            when (char) {
                '“', '「' -> open.addLast(char to index)
                '”', '」' -> {
                    val expected = if (char == '”') '“' else '「'
                    if (open.lastOrNull()?.first == expected) {
                        val start = open.removeLast().second
                        if (open.isEmpty()) result.add(start..index)
                    }
                }
            }
        }
        if (streaming && open.isNotEmpty()) result.add(open.first().second..text.lastIndex)
        return result
    }

    fun emphasize(base: AnnotatedString, accent: Color, monochrome: Boolean,
        isDark: Boolean, streaming: Boolean = false): AnnotatedString {
        val spans = ranges(base.text, streaming)
        if (spans.isEmpty()) return base
        val style = SpanStyle(
            color = if (monochrome) (if (isDark) Color.White else Color.Black) else accent,
            fontWeight = if (monochrome) FontWeight.Bold else null,
        )
        return AnnotatedString.Builder(base).apply {
            spans.forEach { addStyle(style, it.first, it.last + 1) }
        }.toAnnotatedString()
    }
}
