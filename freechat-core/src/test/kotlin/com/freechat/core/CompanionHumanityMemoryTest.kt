package com.freechat.core

import com.freechat.data.AppJson
import com.freechat.data.healed
import com.freechat.model.MemoryEntry
import org.junit.Assert.*
import org.junit.Test

class CompanionHumanityMemoryTest {
    private fun rich(id: String, state: String, knowledge: String, loops: String): MemoryEntry =
        AppJson.gson.fromJson("""{"id":"$id","summary":"","keywords":["钥匙","医院"],"timestamp":1000,"kind":"plot","sourceMessageIds":["user-1","reply-1"],"storyState":"$state","roleKnowledge":"$knowledge","openLoops":"$loops"}""", MemoryEntry::class.java).healed()

    @Test fun extractionReceivesTheAcceptedReplyBeyondItsFirstTwoHundredCharacters() {
        val ending = "沈岚交出车钥匙，答应明早八点陪许川去医院。"
        val reply = "雨落在屋檐。".repeat(80) + ending
        val request = MemoryExtractor.buildMessages("接着写。", reply, true, plotMode = true)
        assertTrue("The accepted action/commitment was silently cut off", request.last().getValue("content").contains(ending))
    }

    @Test fun extractionSeparatesNarratorStateRoleKnowledgeAndPromiseChanges() {
        val prompt = MemoryExtractor.buildMessages("她不知道第三个人已经离城。", "她交出钥匙，约定明早见。", true, plotMode = true)
            .joinToString("\n") { it.getValue("content") }
        for (field in listOf("storyState", "roleKnowledge", "openLoops")) assertTrue("Missing narrative slot: $field", prompt.contains(field))
        assertTrue(prompt.contains("已完成"))
        assertTrue(prompt.contains("取消"))
    }

    @Test fun optionalStateSurvivesParsingAndExplicitNullLegacyHealing() {
        val parsed = MemoryExtractor.parseDraft("""{"summary":"","storyState":"店主私下离城","roleKnowledge":"角色交出钥匙","openLoops":"明早复诊未完成"}""", true)
        val raw = AppJson.gson.toJsonTree(parsed).asJsonObject
        assertEquals("店主私下离城", raw.get("storyState")?.asString)
        assertEquals("角色交出钥匙", raw.get("roleKnowledge")?.asString)
        val legacy = AppJson.gson.fromJson("""{"summary":"旧事实","keywords":[],"storyState":null,"roleKnowledge":null,"openLoops":null}""", MemoryEntry::class.java).healed()
        val healed = AppJson.gson.toJsonTree(legacy).asJsonObject
        for (field in listOf("storyState", "roleKnowledge", "openLoops")) assertEquals("", healed.get(field)?.asString)
    }

    @Test fun roleRecallDoesNotBecomeOmniscient() {
        val entry = rich("one", "店主私下离城", "角色交出钥匙", "明早复诊未完成")
        val context = MemoryLogic.buildHighQualityContext(listOf(entry))
        assertTrue(context.contains("角色交出钥匙"))
        assertTrue(context.contains("明早复诊未完成"))
        assertFalse(context.contains("店主私下离城"))
    }

    @Test fun stateChangesAreNotSwallowedByIdenticalOrEmptySummaries() {
        val pending = rich("one", "修车铺", "交出钥匙", "复诊未完成")
        val fulfilled = rich("two", "医院门口", "已陪同复诊", "复诊已完成")
        val stored = MemoryLogic.upsert(listOf(pending), fulfilled)
        assertEquals(2, stored.size)
        assertEquals(2, MemoryLogic.dedupe(stored).size)
        assertEquals(1, MemoryLogic.dedupe(listOf(pending, pending.copy(id = "same-content"))).size)
        val remaining = DeletionLogic.filterMemories(stored, setOf("reply-1"))
        assertTrue("Rich state must obey the same source deletion contract", remaining.isEmpty())
    }

    @Test fun absentOptionalFieldsRemainReadableWithoutReplacingOldFacts() {
        val entry = AppJson.gson.fromJson("""{"id":"old","summary":"以前借过一本书","keywords":["书"],"timestamp":1000}""", MemoryEntry::class.java).healed()
        assertTrue(MemoryLogic.buildHighQualityContext(listOf(entry)).contains("以前借过一本书"))
        val malformed = MemoryExtractor.parseDraft("""{"summary":"事实","storyState":[],"roleKnowledge":{},"openLoops":7}""", true)
        val raw = AppJson.gson.toJsonTree(malformed).asJsonObject
        assertEquals("事实", malformed.summary)
        for (field in listOf("storyState", "roleKnowledge", "openLoops")) assertEquals("", raw.get(field)?.asString)
        assertEquals("not-json", MemoryExtractor.parseDraft("not-json").summary)
        val broken = MemoryExtractor.parseDraft("""{"summary":"","storyState":"角色没听见的秘密","roleKnowledge":"""", true)
        assertEquals("", broken.summary)
    }

    @Test fun similarWordingWithADifferentFactRemainsChronologicallyDistinct() {
        val older = MemoryEntry(id = "old", summary = "用户目前在南京一家做手机游戏的公司从事美术设计工作", keywords = emptyList(), timestamp = 100)
        val newer = older.copy(id = "new", summary = older.summary.replace("南京", "北京"), timestamp = 200)
        val rows = MemoryLogic.upsert(listOf(newer), older)
        assertEquals(2, rows.size)
        assertEquals(newer, rows.maxBy { it.timestamp })
        assertEquals(2, MemoryLogic.dedupe(rows).size)
    }

    @Test fun highQualityOriginalAndUnknownSecretsCannotBeRewrittenIntoKnownFacts() {
        val draft = MemoryExtractor.parseDraft("""{"summary":"用户正在医院","roleKnowledge":"角色交出钥匙。角色不知道柜中有信。","storyState":"柜中有信"}""",
            plotMode = true, highQuality = true, userText = "还在修车铺")
        assertEquals("", draft.summary)
        assertEquals("角色交出钥匙。", draft.roleKnowledge)
        assertEquals("柜中有信", draft.storyState)
    }

    @Test fun highQualityPlainTextFallbackStillRequiresTheUsersActualWords() {
        assertEquals("", MemoryExtractor.parseDraft("用户已经到了医院", highQuality = true, userText = "还在修车铺").summary)
        assertEquals("还在修车铺", MemoryExtractor.parseDraft("还在修车铺", highQuality = true, userText = "我还在修车铺").summary)
        assertEquals("legacy text", MemoryExtractor.parseDraft("legacy text").summary)
    }
}
