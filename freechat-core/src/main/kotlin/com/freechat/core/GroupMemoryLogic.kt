package com.freechat.core

import com.freechat.model.MemoryEntry

/** A member's recall and retention quota never include another member's private bucket. */
object GroupMemoryLogic {
    fun loadMember(entries: List<MemoryEntry>, memberId: String): List<MemoryEntry> =
        if (memberId.isBlank()) emptyList() else entries.filter { it.groupMemberId == memberId }

    fun appendMember(entries: List<MemoryEntry>, memberId: String, entry: MemoryEntry,
        highQuality: Boolean = false): List<MemoryEntry> {
        require(memberId.isNotBlank())
        val own = MemoryLogic.trim(MemoryLogic.upsert(loadMember(entries, memberId),
            entry.copy(groupMemberId = memberId)), highQuality)
        return entries.filterNot { it.groupMemberId == memberId } + own
    }

    fun buildMemberMemoryContext(entries: List<MemoryEntry>, memberId: String,
        highQuality: Boolean = false): String =
        MemoryLogic.buildModelManagedContext(loadMember(entries, memberId), highQuality, false)
}
