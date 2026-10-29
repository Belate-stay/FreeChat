package com.freechat.core

typealias GroupMentionBinding = com.freechat.model.GroupMention

data class GroupMentionQuery(val start: Int, val end: Int, val search: String)

/** Bindings move with untouched text; edits through a mention remove its hidden target. */
data class GroupMentionDraft(val text: String = "", val bindings: List<GroupMentionBinding> = emptyList()) {
    fun queryAt(cursor: Int, selectionEnd: Int = cursor): GroupMentionQuery? {
        if (cursor != selectionEnd || cursor !in 1..text.length) return null
        val start = text.lastIndexOf('@', cursor - 1)
        if (start < 0 || validBindings().any { cursor > it.start && cursor <= it.end }) return null
        val search = text.substring(start + 1, cursor)
        if (search.any(::tokenBoundary)) return null
        // Only text already entered before the caret belongs to this mention query.
        return GroupMentionQuery(start, cursor, search)
    }

    fun mentionAt(memberId: String, name: String, start: Int, end: Int): GroupMentionDraft {
        if (memberId.isBlank() || name.isBlank() || start !in text.indices || end !in start + 1..text.length || text[start] != '@') return this
        val label = "@${name.trim()}"
        val value = text.replaceRange(start, end, label + if (end == text.length) " " else "")
        val kept = edit(value).bindings.filterNot { it.start < start + label.length && it.end > start }
        return GroupMentionDraft(value, (kept + GroupMentionBinding(memberId, label, start, start + label.length)).sortedBy { it.start })
    }

    fun mention(memberId: String, name: String): GroupMentionDraft {
        val label = "@${name.trim()}"
        val separator = if (text.isNotEmpty() && !text.last().isWhitespace()) " " else ""
        val start = text.length + separator.length
        return copy(text = text + separator + label + " ", bindings = bindings +
            GroupMentionBinding(memberId, label, start, start + label.length))
    }

    fun edit(value: String): GroupMentionDraft {
        if (value == text) return this
        if (value.trim() == text.trim()) {
            val offset = value.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0) -
                text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            return GroupMentionDraft(value, bindings.mapNotNull { binding ->
                binding.copy(start = binding.start + offset, end = binding.end + offset).takeIf {
                    it.start >= 0 && it.end <= value.length && value.substring(it.start, it.end) == it.label
                }
            })
        }
        var prefix = 0
        while (prefix < text.length && prefix < value.length && text[prefix] == value[prefix]) prefix++
        // A newly inserted @ can share a prefix with an intact old mention; don't split its identity.
        prefix = validBindings().filter { prefix > it.start && prefix < it.end }.minOfOrNull { it.start } ?: prefix
        var suffix = 0
        while (suffix < text.length - prefix && suffix < value.length - prefix &&
            text[text.lastIndex - suffix] == value[value.lastIndex - suffix]) suffix++
        val oldEnd = text.length - suffix
        val delta = value.length - text.length
        val kept = bindings.mapNotNull { binding ->
            val next = when {
                binding.end <= prefix -> binding
                binding.start >= oldEnd -> binding.copy(start = binding.start + delta, end = binding.end + delta)
                else -> null
            }
            next?.takeIf { it.start >= 0 && it.end <= value.length && value.substring(it.start, it.end) == it.label }
        }
        return GroupMentionDraft(value, kept)
    }

    fun validBindings(): List<GroupMentionBinding> = bindings.filter {
        it.memberId.isNotBlank() && it.label.startsWith('@') && it.start >= 0 && it.end > it.start &&
            it.end <= text.length && text.substring(it.start, it.end) == it.label
    }

    fun memberIds(): List<String> = validBindings().map { it.memberId }.distinct()
}

private fun tokenBoundary(char: Char): Boolean = char.isWhitespace() || char in "@，。！？、；：,.!?;:()（）[]【】"
