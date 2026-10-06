package com.freechat.data

import com.freechat.model.Message
import com.freechat.model.Role

data class RegenerationPlan(val replyStart: Int, val userMessages: List<Message>,
    val context: List<Message>, val removedReplies: List<Message>) {
    companion object {
        fun from(rows: List<Message>, index: Int): RegenerationPlan? {
            if (index !in rows.indices || rows[index].role != Role.ASSISTANT || rows[index].sceneVisualization) return null
            var start = index
            while (start > 0 && rows[start - 1].role == Role.ASSISTANT) start--
            if (start == 0 || rows[start - 1].role != Role.USER) return null
            var userStart = start - 1
            while (userStart > 0 && rows[userStart - 1].role == Role.USER) userStart--
            var end = index + 1
            while (end < rows.size && rows[end].role == Role.ASSISTANT) end++
            return RegenerationPlan(start, rows.subList(userStart, start),
                rows.take(start).filterNot { it.sceneVisualization },
                rows.subList(start, end).filterNot { it.sceneVisualization })
        }
    }
}
