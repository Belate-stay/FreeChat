package com.freechat.sync

/** A response for an older upload must not acknowledge a newer local deletion/edit. */
internal class DirtyRevisionFence {
    private val revisions = mutableMapOf<String, Long>()
    @Synchronized fun changed(key: String): Long = (current(key) + 1).also { revisions[key] = it }
    @Synchronized fun current(key: String): Long = revisions[key] ?: 0
    @Synchronized fun matches(key: String, revision: Long?): Boolean = revision == null || current(key) == revision
}
