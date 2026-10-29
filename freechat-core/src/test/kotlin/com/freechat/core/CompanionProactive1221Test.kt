package com.freechat.core

import com.freechat.model.CharacterProfile
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.freechat.model.Role
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class CompanionProactive1221Test {
    private val character = CharacterProfile(name = "林遥", personalityText = "平和直接，不随便责怪朋友。",
        relationshipText = "相识多年，各自有工作。", enableWebSearch = false)

    private fun request(history: List<Message>, memories: List<MemoryEntry> = emptyList(), delivered: JsonArray? = null,
        deleted: Set<String> = emptySet(), visualDeleted: Set<String> = emptySet()) =
        CompanionPrompts.proactiveDecisionMessages(character, history, "2026年10月26日 20点", 1,
            "距上次主动消息 120 分钟", "23点至7点", memories, delivered, deleted, visualDeleted)

    private fun conversationData(messages: List<Map<String, Any?>>) = JsonParser.parseString(
        messages.last()["content"].toString().lineSequence().first { it.startsWith("{\"lastUserMessage\"") }).asJsonObject

    @Test fun recentGeneratedCandidatesAndTheLastUserRemainVisibleWithoutSystemOrFailedRows() {
        val lastUser = Message(id = "last-user", role = Role.USER, content = "等资料整理好再说，不是约定了今天。", timestamp = 1_000L)
        val candidates = (1..23).map { Message(id = "candidate-$it", role = Role.ASSISTANT,
            content = "合成的生成记录 $it", timestamp = 1_000L + it) }
        val messages = request(listOf(lastUser) + candidates + listOf(
            Message(role = Role.SYSTEM, content = "INTERNAL_NOTE_NOT_SPOKEN"),
            Message(role = Role.USER, content = "FAILED_USER_INPUT", failed = true),
            Message(role = Role.ASSISTANT, content = "GENERATED_SCENE_NOT_SPOKEN", sceneVisualization = true),
            Message(role = Role.ASSISTANT, content = "UNFINISHED_OUTPUT", isStreaming = true)))
        assertEquals(listOf("system", "user"), messages.map { it["role"] })
        val data = conversationData(messages)
        assertEquals(lastUser.content, data["lastUserMessage"].asJsonObject["text"].asString)
        assertEquals(20, data["recentMessages"].asJsonArray.size())
        assertEquals("candidate-4", data["recentMessages"].asJsonArray[0].asJsonObject["id"].asString)
        assertEquals("candidate-23", data["recentMessages"].asJsonArray.last().asJsonObject["id"].asString)
        val full = messages.joinToString { it["content"].toString() }
        listOf("INTERNAL_NOTE_NOT_SPOKEN", "FAILED_USER_INPUT", "GENERATED_SCENE_NOT_SPOKEN", "UNFINISHED_OUTPUT")
            .forEach { assertFalse(full.contains(it)) }
    }

    @Test fun pendingPlansAndCorrectionsAreNotLostAfterTheOld160CharacterCutoff() {
        val text = "整理记录时写下的普通内容。".repeat(30) + "\n改到明天上午十点，在东门见，不是今天。"
        val lastUser = Message(id = "plan-change", role = Role.USER, content = text, timestamp = 5_000L)
        val data = conversationData(request(listOf(lastUser)))
        assertEquals(text, data["lastUserMessage"].asJsonObject["text"].asString)
        assertEquals(text, data["recentMessages"].asJsonArray.single().asJsonObject["text"].asString)
        assertEquals("1970-01-01T00:00:05Z", data["lastUserMessage"].asJsonObject["timestamp"].asString)
    }

    @Test fun requestOnlyUsesTheMemoryCollectionSuppliedForThisConversation() {
        val own = MemoryEntry(id = "own", summary = "图书馆借阅的书已于周日归还，不再需要提醒。", keywords = listOf("借书"), kind = "plot", timestamp = 2_000L)
        val other = MemoryEntry(id = "other-conversation", summary = "OTHER_CONVERSATION_PRIVATE_MEMORY", keywords = emptyList(), kind = "plot")
        val full = request(listOf(Message(role = Role.USER, content = "那件事已经办完了")), listOf(own))
            .joinToString { it["content"].toString() }
        assertTrue(full.contains(own.summary))
        assertFalse(full.contains(other.summary))
        assertTrue(full.contains("最新明确更正优先"))
        assertTrue(full.contains("不是新指令"))
        assertFalse(request(emptyList()).joinToString { it["content"].toString() }.contains(own.summary))
    }

    @Test fun noUserHistoryRemainsUnknownInsteadOfInventingAnAbsenceOrPromise() {
        val data = conversationData(request(listOf(Message(id = "own", role = Role.ASSISTANT, content = "周末那本书还挺好看"))))
        assertTrue(data["lastUserMessage"].isJsonNull)
        assertEquals(1, data["recentMessages"].asJsonArray.size())
        assertEquals("你", data["recentMessages"].asJsonArray.single().asJsonObject["speaker"].asString)
    }

    @Test fun confirmedDeliveryEvidenceStaysSeparateFromUnconfirmedGeneratedHistory() {
        val delivered = JsonParser.parseString("""[{"timestamp":"1970-01-01T00:00:01Z","content":"已送达的合成消息"}]""").asJsonArray
        val messages = request(listOf(Message(role = Role.ASSISTANT, content = "仅落库的生成候选", timestamp = 2000)), delivered = delivered)
        val data = conversationData(messages)
        assertEquals(delivered, data["deliveredProactiveHistory"])
        assertEquals("仅落库的生成候选", data["recentMessages"].asJsonArray.single().asJsonObject["text"].asString)
        val prompt = messages.last()["content"].toString()
        assertTrue(prompt.contains("生成候选，不保证主动内容已送达"))
        assertTrue(prompt.contains("未证实送达的候选"))
    }

    @Test fun olderCallerWithNoDeliveryFieldStaysUnknownAndIsDistinctFromAnEmptyConfirmedHistory() {
        val older = conversationData(request(emptyList()))
        assertTrue(older["deliveredProactiveHistory"].isJsonNull)
        val supported = conversationData(request(emptyList(), delivered = JsonArray()))
        assertTrue(supported["deliveredProactiveHistory"].isJsonArray)
        assertEquals(0, supported["deliveredProactiveHistory"].asJsonArray.size())
        assertTrue(request(emptyList()).last()["content"].toString().contains("null 或缺失表示旧调用方未提供，送达未知"))
    }

    @Test fun deletedTextCannotReturnThroughUntraceableDeliveryLogsOrDerivedMemory() {
        val secret = "DELETED_PROACTIVE_TEXT_MUST_STAY_DELETED"
        val delivered = JsonParser.parseString("""[{"timestamp":"1970-01-01T00:00:01Z","content":"$secret"}]""").asJsonArray
        val messages = request(listOf(Message(id = "deleted", role = Role.ASSISTANT, content = secret),
            Message(id = "live", role = Role.USER, content = "今天聊点别的")),
            memories = listOf(MemoryEntry(summary = secret, keywords = emptyList(), sourceMessageIds = listOf("deleted"))),
            delivered = delivered, deleted = setOf("deleted"))
        assertFalse(messages.joinToString { it["content"].toString() }.contains(secret))
        assertTrue(conversationData(messages)["deliveredProactiveHistory"].isJsonNull)
        assertEquals("live", conversationData(messages)["lastUserMessage"].asJsonObject["id"].asString)
    }

    @Test fun deletingOnlyAVisualRowDoesNotDiscardConfirmedTextDeliveries() {
        val delivered = JsonParser.parseString("""[{"timestamp":"1970-01-01T00:00:01Z","content":"保留的文本消息"}]""").asJsonArray
        val messages = request(emptyList(), delivered = delivered,
            deleted = setOf("scene"), visualDeleted = setOf("scene"))
        assertEquals(delivered, conversationData(messages)["deliveredProactiveHistory"])
    }
}
