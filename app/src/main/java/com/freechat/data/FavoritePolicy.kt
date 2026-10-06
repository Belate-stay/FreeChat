package com.freechat.data

import com.freechat.model.Message

/** The favorites list represents consecutive runs; removing a run never deletes chat content. */
object FavoritePolicy {
    fun unfavoriteRuns(messages: List<Message>, selectedIds: Set<String>): List<Message> {
        if (selectedIds.isEmpty()) return messages
        val clear = HashSet<String>()
        var start = 0
        while (start < messages.size) {
            if (!messages[start].favorited) { start++; continue }
            var end = start + 1
            while (end < messages.size && messages[end].favorited) end++
            if ((start until end).any { messages[it].id in selectedIds }) {
                for (i in start until end) clear.add(messages[i].id)
            }
            start = end
        }
        return if (clear.isEmpty()) messages else messages.map {
            if (it.id in clear) it.copy(favorited = false) else it
        }
    }
}
