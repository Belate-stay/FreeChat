package com.freechat.data

import com.freechat.model.MemoryEntry
import com.freechat.sync.Merge
import org.junit.Assert.*
import org.junit.Test

class CompanionHumanitySyncTest {
    @Test fun replyRetryWiringRetainsRoundMaterials() {
        // Source wiring only: JVM fake-provider tests separately exercise the retry protocol.
        val vm = java.io.File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()
        val generate = vm.substringAfter("private suspend fun generateCompanionReply(")
            .substringBefore("private fun extractJsonObject")
        assertEquals(1, Regex("val turnMaterials = CompanionTurnMaterials\\(\\)").findAll(generate).count())
        assertEquals(3, Regex("turn = turnMaterials").findAll(generate).count())
        val call = vm.substringAfter("private suspend fun callCompanionApi(").substringBefore("private fun modeFormatRule")
        assertTrue(call.contains("!turn.searchResolved"))
        assertTrue(call.contains("var searchSources = turn.sources") && call.contains("var serpBlock = turn.evidence"))
        assertTrue(call.contains("turn.evidence = serpBlock") && call.contains("turn.sources = searchSources"))
        assertTrue(call.contains("turn.deepPrep ?:"))
    }

    @Test fun oldPeersCannotStripRichMetadataFromTheSameUnchangedRecord() {
        val local = MemoryEntry(id = "m", summary = "明早复诊", keywords = emptyList(),
            sourceMessageIds = listOf("reply"), storyState = "店主私藏了信", roleKnowledge = "角色交出钥匙", openLoops = "复诊未完成")
        val legacy = local.copy(storyState = "", roleKnowledge = "", openLoops = "")
        assertEquals(local, Merge.mergeMemories(listOf(local), listOf(legacy)).single())
        assertTrue(Merge.mergeMemories(listOf(local), listOf(legacy), setOf("reply")).isEmpty())
        assertEquals("新事实", Merge.mergeMemories(listOf(local), listOf(legacy.copy(summary = "新事实"))).single().summary)
    }

    @Test fun modelManagedRecallUsesNewSlotsButNotNarratorSecretsUnlessExplicitlyRequested() {
        val entry = MemoryEntry(summary = "", keywords = emptyList(), storyState = "未说出口的秘密", roleKnowledge = "递过钥匙", openLoops = "复诊已完成")
        val role = ModelManagedMemory.buildContext(listOf(entry))
        assertTrue(role.contains("递过钥匙") && role.contains("复诊已完成"))
        assertFalse(role.contains("未说出口的秘密"))
        assertTrue(ModelManagedMemory.buildContext(listOf(entry), includeStoryState = true).contains("未说出口的秘密"))
    }
}
