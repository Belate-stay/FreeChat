package com.freechat.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.freechat.model.GroupMention
import org.junit.Assert.*
import org.junit.Test

class GroupMentionTextTest {
    @Test fun onlyBoundMatchingRangesReceiveTheSelectedTheme() {
        val text = "你好 @林予，@普通文字"
        val ink = Color(0xFF26745E)
        val fill = ink.copy(alpha = 0.12f)
        val result = groupMentionText(text, listOf(GroupMention("member-a", "@林予", 3, 6)), ink, fill)
        assertEquals(text, result.text)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles.single()
        assertEquals(3, span.start)
        assertEquals(6, span.end)
        assertEquals(ink, span.item.color)
        assertEquals(fill, span.item.background)
        assertEquals(FontWeight.SemiBold, span.item.fontWeight)
    }

    @Test fun staleOrInvalidRangesDoNotHighlightUnrelatedText() {
        val invalid = listOf(GroupMention("a", "@旧名", 0, 3), GroupMention("a", "@新名", -1, 99))
        assertTrue(groupMentionText("@新名", invalid, Color.Blue, Color.Transparent).spanStyles.isEmpty())
    }
}
