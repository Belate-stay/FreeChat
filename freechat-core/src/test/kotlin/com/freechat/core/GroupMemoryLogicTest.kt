package com.freechat.core

import com.freechat.model.MemoryEntry
import org.junit.Assert.*
import org.junit.Test

class GroupMemoryLogicTest {
    private fun entry(id: String, memberId: String? = null, summary: String = id, timestamp: Long = 0L,
        sources: List<String> = listOf("source-$id")) = MemoryEntry(id = id, summary = summary,
        keywords = listOf(id), timestamp = timestamp, sourceMessageIds = sources, groupMemberId = memberId)

    @Test fun recallExcludesOtherMembersAndUnscopedLegacyEntries() {
        val rows = listOf(entry("a-secret", "a"), entry("b-secret", "b"), entry("legacy"), entry("blank", ""))
        assertEquals(listOf("a-secret"), GroupMemoryLogic.loadMember(rows, "a").map { it.id })
        assertTrue(GroupMemoryLogic.loadMember(rows, "").isEmpty())
        assertTrue(GroupMemoryLogic.loadMember(rows, "missing").isEmpty())
        for (highQuality in listOf(false, true)) {
            val context = GroupMemoryLogic.buildMemberMemoryContext(rows, "a", highQuality)
            assertTrue(context.contains("a-secret"))
            assertFalse(context.contains("b-secret"))
            assertFalse(context.contains("legacy"))
            assertFalse(context.contains("blank"))
        }
    }

    @Test fun identicalFactsNeverMergeAcrossMembersOrIntoLegacyBucket() {
        val same = "我周三去了图书馆"
        val a = entry("a", "a", same, sources = listOf("a-message"))
        val legacy = entry("legacy", summary = same)
        val rows = GroupMemoryLogic.appendMember(listOf(a, legacy), "b",
            entry("b", summary = same, sources = listOf("b-message")))
        assertEquals(3, rows.size)
        assertEquals(a, rows.single { it.id == "a" })
        assertEquals(legacy, rows.single { it.id == "legacy" })
        assertEquals("b", rows.single { it.id == "b" }.groupMemberId)
        assertEquals(listOf("b-message"), rows.single { it.id == "b" }.sourceMessageIds)
    }

    @Test fun duplicateWithinMemberMergesOnlyItsOwnProvenance() {
        val a = entry("a", "a", "同一件事", sources = listOf("a-old"))
        val b = entry("b", "b", "同一件事", sources = listOf("b-old"))
        val rows = GroupMemoryLogic.appendMember(listOf(a, b), "a",
            entry("new", summary = "同一件事", sources = listOf("a-new")))
        assertEquals(2, rows.size)
        assertEquals(setOf("a-old", "a-new"), rows.single { it.groupMemberId == "a" }.sourceMessageIds.toSet())
        assertEquals(b, rows.single { it.groupMemberId == "b" })
    }

    @Test fun trimAppliesOnlyToTargetBucketAndPreservesOtherOwnersExactly() {
        val a = (1..30).map { entry("a-$it", "a", timestamp = it.toLong()) }
        val b = (1..45).map { entry("b-$it", "b", timestamp = (100 + it).toLong()) }
        val legacy = entry("legacy")
        val rows = GroupMemoryLogic.appendMember(b + a + legacy, "a", entry("a-new", timestamp = 500))
        assertEquals(30, GroupMemoryLogic.loadMember(rows, "a").size)
        assertFalse(GroupMemoryLogic.loadMember(rows, "a").any { it.id == "a-1" })
        assertEquals(b, rows.filter { it.groupMemberId == "b" })
        assertEquals(legacy, rows.single { it.id == "legacy" })
        assertEquals(76, rows.size)
    }

    @Test fun highQualityQuotaAlsoRemainsMemberScoped() {
        val a = (1..400).map { entry("a-$it", "a", timestamp = it.toLong()) }
        val b = listOf(entry("b", "b"))
        val rows = GroupMemoryLogic.appendMember(a + b, "a", entry("a-new", timestamp = 500), highQuality = true)
        assertEquals(400, GroupMemoryLogic.loadMember(rows, "a").size)
        assertEquals(b, GroupMemoryLogic.loadMember(rows, "b"))
    }

    @Test fun memberContextFiltersDeletedSourcesBeforeRecall() {
        val deleted = entry("deleted-secret", "a", sources = listOf("gone"))
        val kept = entry("retained-fact", "a", sources = listOf("kept"))
        val safe = DeletionLogic.filterMemories(listOf(deleted, kept, entry("untracked", "a", sources = emptyList())), setOf("gone"))
        val context = GroupMemoryLogic.buildMemberMemoryContext(safe, "a")
        assertTrue(context.contains("retained-fact"))
        assertFalse(context.contains("deleted-secret"))
        assertFalse(context.contains("untracked"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun appendCannotCreateAnUnscopedMemberBucket() {
        GroupMemoryLogic.appendMember(emptyList(), "", entry("new"))
    }
}
