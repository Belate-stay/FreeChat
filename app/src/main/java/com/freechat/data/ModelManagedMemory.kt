package com.freechat.data

import com.freechat.core.MemoryLogic
import com.freechat.model.MemoryEntry

/** Android recall has no user-selected association level or keyword/similarity gate. */
object ModelManagedMemory {
    fun buildContext(entries: List<MemoryEntry>, highQuality: Boolean = false, includeStoryState: Boolean = false): String =
        MemoryLogic.buildModelManagedContext(entries.map { it.healed() }, highQuality, includeStoryState)
}
