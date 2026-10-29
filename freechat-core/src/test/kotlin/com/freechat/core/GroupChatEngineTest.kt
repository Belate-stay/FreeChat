package com.freechat.core

import com.freechat.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.*

class GroupChatEngineTest {
    private val a = GroupMember("a", CharacterPersona.from(CharacterProfile(name = "甲")))
    private val b = GroupMember("b", CharacterPersona.from(CharacterProfile(name = "乙")))
    private val group = GroupChatProfile(members = listOf(a, b))
    private val input = Message(id = "user-1", role = Role.USER, content = "聊会儿")
    private fun turn() = GroupTurn("conversation", "turn", group, listOf(input), listOf(input.id))
    private fun <T> immediate(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return requireNotNull(outcome) { "Test unexpectedly suspended" }.getOrThrow()
    }

    @Test fun naturalHundredBubbleReplyIsPreservedAndAttributed() = immediate {
        val rows = (1..100).map { "第${it}条" }
        val delivered = mutableListOf<Message>()
        val engine = GroupChatEngine({ listOf("a") }, { _, _, _ -> GroupGeneratedReply(rows.joinToString("\n"), "群模型") },
            { delivered.add(it); true }, { true }, {}, { "请求失败" })
        val result = engine.run(turn())
        assertFalse(result.cancelled)
        assertEquals(rows, delivered.map { it.content })
        assertEquals(100, delivered.map { it.id }.distinct().size)
        assertTrue(delivered.all { it.groupMemberId == "a" && it.groupTriggerMessageIds == listOf("user-1") })
    }

    @Test fun deliberateSilenceDoesNotCreateAFailureOrFallbackBubble() = immediate {
        val delivered = mutableListOf<Message>()
        val engine = GroupChatEngine({ listOf("a", "b") }, { _, _, _ -> GroupGeneratedReply("[回应:不回复]", "群模型") },
            { delivered.add(it); true }, { true }, {}, { "请求失败" })
        assertEquals(0, engine.run(turn()).failures)
        assertTrue(delivered.isEmpty())
    }

    @Test fun newlyArrivedUserInputCancelsUnsentOutputButKeepsAlreadyDeliveredMessages() = immediate {
        var active = true
        val delivered = mutableListOf<Message>()
        val engine = GroupChatEngine({ listOf("a", "b") }, { _, _, _ -> GroupGeneratedReply("等下\n我拿个东西", "群模型") },
            { delivered.add(it); active = false; true }, { active }, {}, { "请求失败" })
        val result = engine.run(turn())
        assertTrue(result.cancelled)
        assertEquals(listOf("等下"), delivered.map { it.content })
    }

    @Test fun nextSpeakerSeesAcceptedPreviousSpeechAndAFailedStepCanRetryWithoutReplayingOthers() = immediate {
        val delivered = mutableListOf<Message>()
        var fail = true
        var plans = 0
        val engine = GroupChatEngine({ plans++; listOf("a", "b") }, { _, member, history ->
            if (member.id == "b") {
                assertTrue(history.any { it.groupMemberId == "a" && it.content == "已经出门" })
                if (fail) error("fake provider failure")
            }
            GroupGeneratedReply(if (member.id == "a") "已经出门" else "那我也下楼", "群模型")
        }, { delivered.add(it); true }, { true }, {}, { "网络连接失败" })
        val result = engine.run(turn())
        assertEquals(1, result.failures)
        assertEquals("b", delivered.single { it.failed }.groupMemberId)
        fail = false
        val retry = turn().copy(history = listOf(input) + delivered.filterNot { it.failed })
        engine.run(retry, listOf(result.steps.last()))
        assertEquals(1, plans)
        assertEquals(1, delivered.count { it.content == "已经出门" })
        assertEquals(1, delivered.count { it.content == "那我也下楼" })
    }

    @Test fun membersCanReplyToEachOtherAndReturnToAnEarlierSpeakerWithSharedAcceptedHistory() = immediate {
        val privateA = a.copy(persona = a.persona.copy(personalityText = "A_PRIVATE"))
        val privateB = b.copy(persona = b.persona.copy(personalityText = "B_PRIVATE"))
        val sharedGroup = group.copy(members = listOf(privateA, privateB))
        val output = listOf("我三点才下课", "那等你下课再走", "好，我到时叫你")
        val delivered = mutableListOf<Message>()
        val contexts = mutableListOf<String>()
        val engine = GroupChatEngine({ listOf("a", "b", "a") }, { _, member, history ->
            assertEquals(delivered.map { it.id }, history.filter { it.role == Role.ASSISTANT }.map { it.id })
            val material = GroupChatPrompts.actorMessages(sharedGroup, member, history, "", "", "现在")
                .joinToString("\n") { it["content"].toString() }
            assertFalse(material.contains(if (member.id == "a") "B_PRIVATE" else "A_PRIVATE"))
            contexts.add(material)
            GroupGeneratedReply(output[delivered.size], "群模型")
        }, { delivered.add(it); true }, { true }, {}, { "请求失败" })
        assertEquals(0, engine.run(turn().copy(group = sharedGroup)).failures)
        assertEquals(listOf("a", "b", "a"), delivered.map { it.groupMemberId })
        assertTrue(contexts[1].contains(output[0]))
        assertTrue(contexts[2].contains(output[0]) && contexts[2].contains(output[1]))
    }

    @Test fun memoryFailureAfterDeliveryDoesNotInventFailedReplyOrBlockNextSpeaker() = immediate {
        val delivered = mutableListOf<Message>()
        val summarizedMembers = mutableListOf<String>()
        val engine = GroupChatEngine({ listOf("a", "b") }, { _, member, _ ->
            GroupGeneratedReply(if (member.id == "a") "我到了" else "看到你了", "群模型")
        }, { delivered.add(it); true }, { true }, {}, { "记忆失败不该冒充角色失败" },
            onStepFinished = { member, _ ->
                summarizedMembers.add(member.id)
                if (member.id == "a") error("fake memory extraction failure")
            })
        val result = engine.run(turn())
        assertEquals(0, result.failures)
        assertFalse(result.cancelled)
        assertEquals(listOf("我到了", "看到你了"), delivered.map { it.content })
        assertFalse(delivered.any { it.failed })
        assertEquals(listOf("a", "b"), summarizedMembers)
    }

    @Test(expected = java.util.concurrent.CancellationException::class)
    fun cancellationDuringMemoryCompletionStillPropagates() = immediate {
        val engine = GroupChatEngine({ listOf("a") }, { _, _, _ -> GroupGeneratedReply("已发送", "群模型") },
            { true }, { true }, {}, { "请求失败" }, onStepFinished = { _, _ ->
                throw java.util.concurrent.CancellationException("turn cancelled")
            })
        engine.run(turn())
        Unit
    }

    @Test fun repeatedFailedStepGetsANewRowSoDeletedFailureCannotSuppressRetryFeedback() = immediate {
        val delivered = mutableListOf<Message>()
        val engine = GroupChatEngine({ listOf("a") }, { _, _, _ -> error("offline failure") },
            { delivered.add(it); true }, { true }, {}, { "请求失败" })
        val first = engine.run(turn())
        engine.run(turn(), first.steps)
        assertEquals(2, delivered.size)
        assertEquals(2, delivered.map { it.id }.distinct().size)
        assertEquals(1, delivered.map { it.groupStepId }.distinct().size)
    }
}
