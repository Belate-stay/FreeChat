package com.freechat.data

import com.freechat.model.Message

/** Sync sorts by timestamp then ID; adjacent rows from one send must not tie on random UUIDs. */
internal object MessageBatchOrder {
    fun after(history: List<Message>, drafts: List<Message>, now: Long = System.currentTimeMillis()): List<Message> {
        if (drafts.isEmpty()) return emptyList()
        val latest = history.maxOfOrNull { it.timestamp }
        val first = maxOf(now - drafts.size + 1, latest?.plus(1) ?: Long.MIN_VALUE)
        return drafts.mapIndexed { index, message -> message.copy(timestamp = first + index) }
    }
}
