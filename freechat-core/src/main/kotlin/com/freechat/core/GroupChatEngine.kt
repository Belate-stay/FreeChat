package com.freechat.core

import com.freechat.model.*
import java.util.concurrent.CancellationException

data class GroupTurn(val conversationId: String, val turnId: String, val group: GroupChatProfile,
    val history: List<Message>, val triggerMessageIds: List<String>, val rules: String = "",
    val imageContext: String = "", val nowText: String = "")
data class GroupReplyStep(val id: String, val memberId: String)
data class GroupGeneratedReply(val content: String, val modelName: String,
    val sources: List<SearchCitation> = emptyList(), val reasoningContent: String = "")
data class GroupTurnResult(val steps: List<GroupReplyStep>, val failures: Int = 0, val cancelled: Boolean = false)

/** One user-triggered finite plan. No callback can start an autonomous follow-up turn. */
class GroupChatEngine(
    private val plan: suspend (GroupTurn) -> List<String>,
    private val generate: suspend (GroupTurn, GroupMember, List<Message>) -> GroupGeneratedReply,
    private val deliver: suspend (Message) -> Boolean,
    private val isValid: () -> Boolean,
    private val pause: suspend (Long) -> Unit,
    private val failureText: (Throwable) -> String,
    private val onStepFinished: suspend (GroupMember, List<Message>) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(turn: GroupTurn, retrySteps: List<GroupReplyStep>? = null): GroupTurnResult {
        require(GroupChatPolicy.isValid(turn.group)) { "Invalid group members" }
        if (!isValid()) return GroupTurnResult(emptyList(), cancelled = true)
        val steps = retrySteps ?: plan(turn).mapIndexed { index, id -> GroupReplyStep("${turn.turnId}:$index", id) }
        require(steps.all { step -> turn.group.members.any { it.id == step.memberId } }) { "Unknown group speaker" }
        val working = turn.history.filterNot { it.failed }.toMutableList()
        var failures = 0
        for (step in steps) {
            if (!isValid()) return GroupTurnResult(steps, failures, true)
            val member = turn.group.members.first { it.id == step.memberId }
            val started = now()
            val accepted = mutableListOf<Message>()
            try {
                val generated = generate(turn, member, working.toList())
                if (!isValid()) return GroupTurnResult(steps, failures, true)
                val parsed = CompanionReplyParser.parse(generated.content, false)
                if (parsed.silence) continue
                val segments = CompanionRhythm.prepareSegments(parsed.bodyLines, false)
                check(segments.isNotEmpty()) { "Model returned no reply body" }
                val delays = CompanionRhythm.deliveryDelaysMs(segments, false, now() - started)
                for ((index, content) in segments.withIndex()) {
                    if (!isValid()) return GroupTurnResult(steps, failures, true)
                    pause(delays[index])
                    if (!isValid()) return GroupTurnResult(steps, failures, true)
                    val voice = CompanionPrompts.extractVoice(content)
                    val message = Message(id = "${step.id}:$index", role = Role.ASSISTANT,
                        content = voice?.text ?: content, timestamp = now(), modelName = generated.modelName,
                        mode = ChatMode.COMPANION, thinkingTimeMs = now() - started,
                        searchSources = generated.sources, groupMemberId = member.id,
                        groupSenderName = member.persona.name, groupSenderAvatar = member.persona.avatarPath,
                        groupTurnId = turn.turnId, groupStepId = step.id,
                        groupTriggerMessageIds = turn.triggerMessageIds)
                    if (!deliver(message)) return GroupTurnResult(steps, failures, true)
                    working.add(message)
                    accepted.add(message)
                }
                if (accepted.isNotEmpty() && isValid()) {
                    try { onStepFinished(member, accepted.toList()) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Recall is optional; delivered speech remains successful. */ }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!isValid()) return GroupTurnResult(steps, failures, true)
                failures++
                val failed = Message(id = "${step.id}:failed:${java.util.UUID.randomUUID()}", role = Role.ASSISTANT,
                    content = failureText(error), timestamp = now(), mode = ChatMode.COMPANION, failed = true,
                    groupMemberId = member.id, groupSenderName = member.persona.name,
                    groupSenderAvatar = member.persona.avatarPath, groupTurnId = turn.turnId,
                    groupStepId = step.id, groupTriggerMessageIds = turn.triggerMessageIds)
                if (!deliver(failed)) return GroupTurnResult(steps, failures, true)
            }
        }
        return GroupTurnResult(steps, failures)
    }
}
