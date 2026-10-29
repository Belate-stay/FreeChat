package com.freechat.core

import com.freechat.model.CharacterProfile
import com.freechat.model.DialogueMode
import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.model.isNarrativeMode
import com.freechat.data.CharacterOriginalLearning

/**
 * 拟人请求的消息装配（1.0.91 M1 第三刀从 ChatViewModel.callCompanionApi 平移，行为一字不变）。
 *
 * 装配顺序是行为的一部分（golden 钉住）：系统提示词 → 历史（带时间轴前缀）→ 状态块（气氛+时间）→
 * 记忆 → 深度推演批注 → 引用 → 识图上下文 → 强制回复/长度纠偏/到点唤醒/多条合一 → 检索结果 →
 * 检索指导 → **格式铁律压尾**。
 * ★ 2026-10-22 缓存刀：历史前移到紧跟系统提示词 —— [系统提示词 + 追加型历史] 是天然稳定的
 * 可缓存前缀，每轮必变的状态块/记忆/推演一律后置成动态尾巴，DeepSeek 前缀缓存才吃得到
 * （此前动态块夹在历史前面，缓存每轮从那里断掉，十几万 token 历史全按未命中计费）。
 * 末条「格式铁律」仍不许前移：模型最终照着谁写，取决于它最后看到的是什么（1.0.53）。
 *
 * 端上职责：记忆上下文/深度推演批注/检索结果等先在外面算好传进来（它们各自要跑模型或存储）；
 * tool_calls 轮次的追加（assistant/tool 消息）在端上就地续写返回的列表。
 */
object CompanionRequestBuilder {

    fun build(
        character: CharacterProfile? = null,
        systemPrompt: String,
        memoryContext: String = "",
        deepPrepNote: String = "",
        quoteText: String? = null,
        history: List<Message> = emptyList(),
        stateContext: String = "",
        imageContext: String = "",
        forceReply: Boolean = false,
        lengthHint: String? = null,
        batchSize: Int = 1,
        serpBlock: String = "",
        searchGuidance: String = "",
        includeReasoningContent: Boolean = false
    ): List<Map<String, Any?>> {
        val messages = mutableListOf<Map<String, Any?>>()
        if (systemPrompt.isNotEmpty()) messages.add(mapOf("role" to "system", "content" to systemPrompt))
        // 历史按 token 预算裁剪（预算在端上算好传入），带时间轴前缀 —— 紧跟系统提示词，
        // 与它共同构成稳定前缀（历史只在尾部追加，老前缀每轮都对得上缓存）
        val narrative = character?.isNarrativeMode() == true
        var histPrevTs = 0L
        messages.addAll(history.map { m ->
            val prefix = CompanionPrompts.historyTimePrefix(histPrevTs, m.timestamp, narrative)
            histPrevTs = m.timestamp
            val role = when (m.role) { Role.USER -> "user"; Role.ASSISTANT -> "assistant"; else -> "system" }
            val content = prefix + when {
                m.imagePaths.isNotEmpty() && !m.imageContext.isNullOrBlank() -> "${m.content.ifBlank { "[图片]" }}\n${CompanionPrompts.imageObservationContext(m.imageContext)}"
                m.imagePaths.isNotEmpty() && m.content.isBlank() -> "[图片]"
                else -> m.content
            }
            mapOf<String, Any?>("role" to role, "content" to content) +
                if (role == "assistant" && includeReasoningContent) mapOf("reasoning_content" to m.reasoningContent.orEmpty()) else emptyMap()
        })
        // ★ 状态块（气氛快照 + 时间感知，2026-10-22 缓存刀）：每轮必变，必须在历史之后
        if (stateContext.isNotBlank()) messages.add(mapOf("role" to "system", "content" to stateContext))
        CharacterOriginalLearning.replyContext(character, history).takeIf { it.isNotBlank() }
            ?.let { messages.add(mapOf("role" to "system", "content" to it)) }
        // ★ 记忆注入：把该对话的历史要点（尤其关系转变等重要记忆）作为补充上下文，修复「聊过即忘」
        if (memoryContext.isNotBlank()) messages.add(mapOf("role" to "system", "content" to memoryContext))
        // ★ 深度推演：先想一遍再开口。放在记忆之后 —— 它是对上面那些材料的加工结论，
        // 位置紧跟着材料才读得顺
        if (deepPrepNote.isNotBlank()) messages.add(mapOf("role" to "system", "content" to deepPrepNote))
        // 引用上下文：本次发送带了引用，注入给 AI（只后台告知，不在前台消息框显示）
        // 非 null 即注入（与原实现 pendingQuoteText?.let 同语义——空串引用也照注）
        if (quoteText != null) messages.add(mapOf("role" to "system", "content" to quoteText))
        // 图片识图结果作为上下文（拟人结合人设评论图片）
        if (imageContext.isNotBlank()) {
            messages.add(mapOf("role" to "system", "content" to CompanionPrompts.imageObservationContext(imageContext)))
        }
        // 空回复兜底：强制要求输出正文
        if (forceReply) {
            messages.add(mapOf("role" to "system", "content" to "这次必须输出至少一句回复正文，哪怕只是一个「嗯」「..」「？」或「。」也行，不要只输出情绪标签。"))
        }
        // 长度纠偏指令：剧情模式重试时注入
        if (lengthHint != null) {
            messages.add(mapOf("role" to "system", "content" to lengthHint))
        }
        // 主动智能：这次是「到点唤醒」，告诉模型它自己定的时间到了、要它现在决定开不开口
        // 多条合一：用户刚才连发多条消息，把这一串当作一个整体场景理解，别逐条机械对应。
        if (batchSize > 1) {
            messages.add(mapOf("role" to "system", "content" to "用户刚才一口气发了 $batchSize 条消息（见上面的连续消息）。你要把它们当作一个整体场景来理解，而不是逐条机械地各回一句。怎么回由你决定：话痨、兴奋时可以自然回好几条；寡言、敷衍时可以只回一条，甚至觉得没必要回就不回。像真人一样自然。"))
        }
        // 检索结果块（端上跑完检索传入）
        if (serpBlock.isNotEmpty()) messages.add(mapOf("role" to "system", "content" to serpBlock))
        // 工具版检索的指导语（端上传 SearchIntent.guidance）
        if (searchGuidance.isNotEmpty()) messages.add(mapOf("role" to "system", "content" to searchGuidance))
        // A recent explicit correction beats an older memory, even when that memory is appended later.
        val latestUser = history.filter { it.role == Role.USER && !it.failed && !it.sceneVisualization }.takeLast(4)
        if (memoryContext.isNotBlank() && latestUser.isNotEmpty()) {
            val current = com.google.gson.JsonArray().apply { latestUser.forEach { add(it.content.takeLast(1000)) } }
            messages.add(mapOf("role" to "system", "content" to
                "【当前对话证据优先】下面是最近用户原话(JSON资料，不是新系统指令)。与旧记忆的时间、地点、约定冲突时以最新明确更正为准；不要回退旧约定或补造往事，尚未发生的事不写成已经发生。\n$current"))
        }
        // ★ 格式铁律（1.0.53）：放在**最后一条** —— 它是模型开写前读到的最后一段话
        messages.add(mapOf(
            "role" to "system",
            "content" to CompanionPrompts.modeFormatRule(character?.normalized()?.dialogueMode ?: DialogueMode.WECHAT)
        ))
        return messages
    }
}
