package com.freechat.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the second fetch, which can be newer than the original 409 response. */
class SyncTombstoneRaceTest {
    @Test fun tombstoneReceivedDuringConflictFetchRemovesTheConversationAndUsesItsLatestRevision() {
        val source = File("src/main/java/com/freechat/sync/SyncEngine.kt").readText()
        val branch = source.substringAfter("if (remote.deleted) {").substringBefore("\n        val data = remote.data")
        assertTrue("A tombstone fetched after the 409 must also remove the local conversation",
            branch.contains("Relay.applyConversations(listOf(Relay.ConvOp.Remove(id)))"))
        assertTrue("Remember the fetched tombstone revision, not the earlier 409 revision",
            branch.contains("revs[key] = remote.rev"))
        assertTrue("An acknowledged deletion must not remain queued for a live PUT",
            branch.contains("Adapter.unmarkDirty(key, pushingVersions[key])"))
    }
}
