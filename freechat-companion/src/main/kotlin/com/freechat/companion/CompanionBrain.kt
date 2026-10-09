package com.freechat.companion

import com.freechat.core.CompanionHistory
import com.freechat.core.CompanionMood
import com.freechat.core.CompanionPrompts
import com.freechat.core.CompanionReplyParser
import com.freechat.core.CompanionRequestBuilder
import com.freechat.core.CompanionRhythm
import com.freechat.core.MemoryExtractor
import com.freechat.core.MemoryLogic
import com.freechat.core.TimeState
import com.freechat.core.detectGoodnight
import com.freechat.core.generateSleepSchedule
import com.freechat.core.moodWithResidue
import com.freechat.core.parseEmotionLabel
import com.freechat.data.AppJson
import com.freechat.model.CharacterProfile
import com.freechat.model.ChatMode
import com.freechat.model.DialogueMode
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.Provider
import com.freechat.model.isNarrativeMode
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 一轮拟人生成（M2 大脑）：收用户文本 → 走与 App **同一份** freechat-core 机制出回复 → 写回同步库。
 *
 * 与 ChatViewModel.generateCompanionReply 的差异只有「没有 UI」：没有输入延迟/逐条打字/通知，
 * 分条的「真人节奏」收窄为条数语义（间隔与打字是 M3/M4 渠道侧的事）；作息/情绪/记忆/氛围全保留。
 * 每对话一把锁串行（方案 v2：生成收口服务器、每对话生成锁串行）。
 *
 * 1.0.99.4 回复缓冲上微信：
 *  · **批量输入**（[UserMsg]）：通道层的缓冲窗口把连发攒成一批喂进来，逐条幂等落库
 *   （稳定 id，打断重走窗口不写双份），提示词侧按 App 的多条合一带序号；
 *  · **过期闸门**（[RoundGate]）：被并批/被中止的生成弃写（[cancel]），已落库未发出的
 *    「清掉半截」删出箱（App 的 deleteMessageIds 同语义）——不在消息箱里留孤儿回复；
 *  · **深度推演**（两趟心演）补全：deepThinking ∧ 高质量记忆时先推演再开口，与 App 同条件。
 */
class CompanionBrain(
    private val store: CompanionStore,
    private val completer: ChatCompleter
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    // 记忆摘录的后台作用域（1.0.99.3 提速：摘录不挡回复返回，见 replyLocked 第 6 步）
    private val memoryScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    // 作息/晚安瞬时态：与 CVM 的内存 map 同语义（进程内、按对话隔离；重启丢掉无害——只是少一次「昨晚睡着了」）
    private val userSaidGoodnightAt = ConcurrentHashMap<String, Long>()
    private val sleepDateKey = ConcurrentHashMap<String, String>()
    private val sleepAtHour = ConcurrentHashMap<String, Int>()
    private val wakeAtHour = ConcurrentHashMap<String, Int>()
    private val sleepingMissedMessages = ConcurrentHashMap<String, Int>()

    /** 缓冲窗口攒下的一条用户消息（稳定 id：通道层按原始消息 id 生成，重走窗口幂等） */
    data class UserMsg(val id: String, val text: String)

    /**
     * 一批生成的过期闸门（1.0.99.4）。状态机只有两步，全局唯一锁在 [this] 上：
     *  · [tryCommit]：落库前定案 —— 已作废则弃写；定案后此轮必算数；
     *  · [tryCancel]：通道层判这批不发了 —— 未定案则弃写；已定案则由调用方「清掉半截」。
     */
    private class RoundGate(val batchId: String, val convId: String) {
        @Volatile var responded = false
        private var cancelled = false
        private val persistedIds = mutableListOf<String>()
        private val jobs = mutableListOf<Job>()

        val createdAtMs: Long = System.currentTimeMillis()

        @Synchronized fun tryCommit(): Boolean {
            if (cancelled) return false
            responded = true
            return true
        }

        /** @return true=还没落库（弃写即够）；false=已定案（调用方要清掉半截） */
        @Synchronized fun tryCancel(): Boolean {
            if (responded) return false
            cancelled = true
            return true
        }

        @Synchronized fun notePersisted(id: String) { persistedIds += id }

        @Synchronized fun drainPersisted(): List<String> {
            val out = persistedIds.toList()
            persistedIds.clear()
            return out
        }

        @Synchronized fun addJob(job: Job?) { job?.let { jobs += it } }

        @Synchronized fun isCancelled(): Boolean = cancelled

        /** 中止这轮的模型调用/摘录（并批重等：旧批的生成是白烧的，立刻掐掉省排队） */
        @Synchronized fun cancelJobs() { jobs.forEach { it.cancel() } }
    }

    // batchId → 闸门（留到通道层 cancel 到达为止；超 10 分钟的随手清掉）
    private val gates = ConcurrentHashMap<String, RoundGate>()
    // convId → 在途闸门（打断在途：新批一到就作废旧批的生成）
    private val active = ConcurrentHashMap<String, RoundGate>()

    data class ReplyResult(
        val slept: Boolean = false,
        val silence: Boolean = false,     // 生气不回 / 主动空回复 / 被并批弃写
        val emotion: String = "",
        val segments: List<String> = emptyList(),
        /** 回复消息的 id（M4 收口：App 瘦客户端按这些 id 逐条揭示，同步合并同 id 不重复） */
        val messageIds: List<String> = emptyList()
    )

    companion object {
        /** 与 App 内置条目一致（ChatViewModel builtInList）：内置 mimo 代调、深度思考位默认关 */
        val MIMO = ModelInfo(
            "mimo-v2.6-flash", "MiMo-V2.6-Flash", Provider.XIAOMI,
            "Xiaomi深度推理模型，作者自用API，不保证随时在线，可适当白嫖。",
            supportsWebSearch = true, modelType = ModelType.LANGUAGE, isBuiltIn = true
        )

        // pcset 线上键（与 PerConvBridge 的常量同名——改一处必须同步另一处）
        private const val K_AUTO_MEM = "autoSummarizeMemory"
        private const val K_MOOD = "lastMood"
        private const val K_MOOD_AT = "moodAtMs"
        private const val K_ATMOSPHERE = "atmosphere"
        private const val K_WARMTH = "warmth"
        private const val K_ATMO_SOURCES = "atmosphereSourceMessageIds"
    }

    /**
     * 生成一轮。[convId] 须已过 [CompanionStore.safeId]；对话必须带角色档案（拟人）。
     *
     * 旧通道（App 瘦客户端/兼容）：[userText] 单条文本，[userMessageId] 指定用户消息 id（幂等），
     * [skipUserWrite]=App 自管用户消息落盘、大脑只把 [userText] 入上下文。
     * 新通道（1.0.99.4 微信缓冲批）：[messages] 逐条带稳定 id 幂等落库，[batchId] 是过期闸门 id
     *（通道层并批中止时按 id 调 [cancel]）。
     * [wechatBinding] 只由受信任微信路由提供；仍须校验同步库里的当前绑定，不能由用户文本开启。
     */
    suspend fun reply(
        convId: String,
        userText: String = "",
        userMessageId: String? = null,
        skipUserWrite: Boolean = false,
        batchId: String? = null,
        messages: List<UserMsg> = emptyList(),
        wechatBinding: WechatBinding? = null
    ): ReplyResult {
        evictStaleGates()
        val gate = RoundGate(batchId ?: CompanionStore.newMessageId(), convId)
        gates[gate.batchId] = gate
        val prev = active.put(convId, gate)
        // 打断在途（App「清掉半截回复重新统一理解」同语义）：旧批还没定案就作废它，
        // 烧到一半的模型调用立刻掐掉，别挡着新批排队。
        if (prev != null && prev !== gate) {
            if (prev.tryCancel()) prev.cancelJobs()
        }
        gate.addJob(currentCoroutineContext()[Job])
        val lock = locks.computeIfAbsent(convId) { Mutex() }
        try {
            return lock.withLock {
                replyLocked(convId, gate, userText, userMessageId, skipUserWrite, messages, wechatBinding)
            }
        } finally {
            active.remove(convId, gate)
        }
    }

    /**
     * 通道层判这批作废（1.0.99.4：缓冲并批中止 / 定案前断开）——幂等。
     * 没走到落库的弃写即够；已落库未发出的把孤儿回复删出箱（消息还没发出去，删了不心疼；
     * 已发出的批次通道层不会调到这里 —— 开发送即定案是通道层的承诺）。
     */
    suspend fun cancel(batchId: String): Boolean {
        val gate = gates.remove(batchId) ?: return false
        gate.cancelJobs()
        if (gate.tryCancel()) return true
        val ids = gate.drainPersisted()
        if (ids.isNotEmpty()) discardReplies(gate.convId, ids)
        return true
    }

    /** 清掉半截回复：整箱重写时把这批 id 剔掉（msgs 箱是全量记录箱，剔掉即消失，同删消息语义） */
    private suspend fun discardReplies(convId: String, ids: List<String>) {
        val lock = locks.computeIfAbsent(convId) { Mutex() }
        lock.withLock {
            repeat(5) { attempt ->
                val objs = store.load(convId) ?: return
                val drop = ids.toSet()
                if (objs.messages.none { it.id in drop }) return
                try {
                    store.writeBox(objs.userId, "msgs", convId, objs.revs["msgs"] ?: 0L,
                        store.messagesToWire(objs.messages.filterNot { it.id in drop }), System.currentTimeMillis())
                    return
                } catch (_: CompanionStore.RevConflict) {
                    kotlinx.coroutines.delay(30L * (attempt + 1))
                }
            }
        }
    }

    private fun evictStaleGates() {
        val cutoff = System.currentTimeMillis() - 10 * 60 * 1000
        gates.values.removeAll { it.createdAtMs < cutoff }
    }

    private suspend fun replyLocked(
        convId: String,
        gate: RoundGate,
        userText: String,
        userMessageId: String?,
        skipUserWrite: Boolean,
        messages: List<UserMsg>,
        wechatBinding: WechatBinding?
    ): ReplyResult {
        val objs = store.load(convId) ?: throw IllegalArgumentException("conversation not found: $convId")
        val character = objs.conv.characterProfile?.normalized()
            ?: throw IllegalArgumentException("conversation has no character profile: $convId")
        require(wechatBinding == null || store.hasActiveWechatBinding(objs.userId, convId, wechatBinding)) {
            "wechat binding is no longer active"
        }
        val allowWechatEmoji = wechatBinding != null && character.dialogueMode == DialogueMode.WECHAT
        fun ensureWechatBindingActive() {
            if (wechatBinding != null && !store.hasActiveWechatBinding(objs.userId, convId, wechatBinding)) {
                throw CancellationException("wechat binding changed during generation")
            }
        }
        val nowMs = System.currentTimeMillis()

        // 1) 用户消息（skipUserWrite=收口模式：只入上下文不落库，落库由 App 的正常同步负责）
        //    新通道逐条幂等落库：重走窗口带着同样的稳定 id 进来，箱里已有就不再写。
        val batch: List<UserMsg> = messages.ifEmpty {
            if (userText.isNotBlank()) listOf(UserMsg(userMessageId ?: CompanionStore.newMessageId(), userText))
            else emptyList()
        }
        // 本轮喂给模型/记忆/摘录的合成文本（App 的 processCompanionBuffer 同格式：多条带序号）
        val turnText = if (messages.size > 1)
            messages.mapIndexed { i, m -> "${i + 1}. ${m.text}" }.joinToString("\n")
        else batch.firstOrNull()?.text.orEmpty()

        val working = objs.messages.sortedBy { it.timestamp }.toMutableList()
        var userTs = nowMs
        val newUsers = mutableListOf<Message>()
        for (um in batch) {
            if (objs.messages.any { it.id == um.id }) continue          // 幂等：箱里已有
            val msg = Message(
                id = um.id, role = com.freechat.model.Role.USER,
                content = um.text, timestamp = userTs++, mode = ChatMode.COMPANION
            )
            // ⚠️ 收口模式（skipUserWrite）：这条合成用户消息只入上下文、不落库——
            // 用户消息的落盘归 App 的正常同步，这里带下去就是双份。
            if (!skipUserWrite) newUsers += msg
            working += msg
        }
        working.sortBy { it.timestamp }
        if (newUsers.isNotEmpty()) persistMessages(convId, newUsers, wechatBinding)

        // 2) 作息：睡觉窗口内沉默不回复（记漏回，醒后提示词里自然解释）
        if (isSleepingNow(convId, character)) {
            sleepingMissedMessages[convId] = (sleepingMissedMessages[convId] ?: 0) + 1
            return ReplyResult(slept = true)
        }
        if (batch.any { detectGoodnight(it.text) }) userSaidGoodnightAt[convId] = nowMs

        // 3) 走共享机制出回复（空回复兜底/篇幅纠偏与 App 同款）
        val narrative = character.isNarrativeMode()
        val hq = character.highQualityMemory
        val assoc = character.synonymAssociation
        val autoMem = objs.pcset.get(K_AUTO_MEM)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true

        // —— 材料先算好再装配（与 App 的 callCompanionApi 同构）——
        val timeState = TimeState(
            nowMillis = nowMs,
            userSaidGoodnightAtMs = userSaidGoodnightAt[convId] ?: 0L,
            sleepingMissedCount = sleepingMissedMessages[convId] ?: 0,
            sleepAtHour = sleepAtHour[convId] ?: -1,
            wakeAtHour = wakeAtHour[convId] ?: -1,
            moodAtMs = objs.pcset.get(K_MOOD_AT)?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
            atmosphere = objs.pcset.get(K_ATMOSPHERE)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        )
        val systemPrompt = CompanionPrompts.buildCompanionSystemPrompt(
            character = character, messages = working,
            convRules = objs.conv.rules.orEmpty(), pendingReminder = null, timeState = timeState,
            allowWechatEmoji = allowWechatEmoji
        )
        val memoryContext = if (autoMem) MemoryLogic.buildMemoryContext(objs.memories, turnText, hq, assoc) else ""
        val history = CompanionHistory.pickHistory(working, CompanionHistory.historyBudgetTokens(hq))
        // 深度推演（1.0.99.4 补大脑）：deepThinking ∧ 高质量记忆才跑（App 的 callCompanionApi 同条件）
        ensureWechatBindingActive()
        val deepPrepNote = if (character.deepThinking && hq)
            runDeepPrepPass(character, systemPrompt, working, objs.memories, turnText, assoc, autoMem) else ""

        suspend fun generate(forceReply: Boolean, lengthHint: String? = null): com.freechat.core.ParsedCompanionReply {
            ensureWechatBindingActive()
            val request = CompanionRequestBuilder.build(
                character = character, systemPrompt = systemPrompt, memoryContext = memoryContext,
                deepPrepNote = deepPrepNote, history = history, forceReply = forceReply, lengthHint = lengthHint,
                batchSize = batch.size, includeReasoningContent = false
            )
            val temperature = CompanionPrompts.companionTemperature(character.aiCreativity)
            // 深度思考「尽力关」：与 App 的 companionDeepThink 同语义（角色开关 ∧ 模型能力位）
            val deepThink = character.deepThinkingMode && MIMO.supportsDeepThinking
            val raw = completer.complete(request, temperature, CompanionPrompts.deepThinkExtras(deepThink))
            ensureWechatBindingActive()
            return CompanionReplyParser.parse(raw, narrative, allowWechatEmoji = allowWechatEmoji)
        }

        var parsed = generate(forceReply = false)
        if (parsed.bodyLines.isEmpty()) parsed = generate(forceReply = true)
        if (parsed.bodyLines.isEmpty()) {
            // 仍空：与 App 同款发省略号（自然无语，不显示「（空回复）」）
            ensureWechatBindingActive()
            if (!gate.tryCommit()) return ReplyResult(emotion = "", silence = true)
            val ellipsis = Message(
                id = CompanionStore.newMessageId(), role = com.freechat.model.Role.ASSISTANT,
                content = "…", timestamp = System.currentTimeMillis(), modelName = MIMO.displayName, mode = ChatMode.COMPANION
            )
            working += ellipsis
            gate.notePersisted(ellipsis.id)
            persistMessages(convId, listOf(ellipsis), wechatBinding)
            return ReplyResult(emotion = "", segments = listOf("…"), messageIds = listOf(ellipsis.id))
        }
        if (narrative && CompanionPrompts.countChars(parsed.bodyLines.joinToString("")) <
            CompanionPrompts.plotLengthFloor(character.plotLength)
        ) {
            val retried = generate(forceReply = true, lengthHint = "上次回复太短了，明显达不到当前「单次回复长度」档应有的体量。请把这一段按该档的篇幅写足。")
            if (retried.bodyLines.isNotEmpty()) parsed = retried
        }

        // 4) 情绪 → 条数（叙事档整段最多 3 段；微信档情绪化条数，生气可能不回）
        val mood = if (narrative) CompanionMood.NEUTRAL else
            (parseEmotionLabel(parsed.emotion) ?: moodWithResidue(turnText, perFrom(objs.pcset), nowMs))
        val maxCount = CompanionRhythm.segmentCount(mood, narrative, regenerating = false, segmentTotal = parsed.bodyLines.size)
        val toShow = parsed.bodyLines.take(maxCount)
        if (toShow.isEmpty()) return ReplyResult(emotion = parsed.emotion, silence = true)

        // 5) 分条落库（时间戳逐条 +1 保序）
        //    落库前最后看一眼过期闸门：被并批/被中止 → 弃写，不在箱里留孤儿回复
        ensureWechatBindingActive()
        if (!gate.tryCommit()) return ReplyResult(emotion = parsed.emotion, silence = true)
        val baseTs = System.currentTimeMillis()
        val replyIds = mutableListOf<String>()
        val replyMsgs = mutableListOf<Message>()
        toShow.forEachIndexed { i, seg ->
            val id = CompanionStore.newMessageId()
            replyIds += id
            val m = Message(
                id = id, role = com.freechat.model.Role.ASSISTANT,
                content = seg, timestamp = baseTs + i, modelName = MIMO.displayName, mode = ChatMode.COMPANION
            )
            working += m
            replyMsgs += m
            gate.notePersisted(id)
        }
        ensureWechatBindingActive()
        persistMessages(convId, replyMsgs, wechatBinding)
        sleepingMissedMessages[convId] = 0

        // 6) 记忆摘录 + 氛围快照（与 App 的 summarizeAndRemember 同款；摘录失败不拖回复）
        //
        // 1.0.99.3 提速：摘录是**另一整个模型调用**，原来挡在回复返回之前 ——
        // 微信侧要等「回复 + 摘录」两次模型往返才发第一条消息。App 本地是回复展示后
        // `viewModelScope.launch` 异步摘录，这里对齐同语义：回复落库立即返回，摘录进后台。
        // 1.0.99.4：摘录的模型调用挪到锁外（App 的摘录本来就不挡下一轮）——
        // 否则打断重等/下一轮回复要在锁上排完整个摘录往返；写箱仍持锁保 rev 单调。
        val replyText = toShow.joinToString("\n")
        gate.addJob(memoryScope.launch {
            try {
                extractAndRemember(convId, gate, turnText, replyText, batch.map { it.id }, replyIds, narrative, hq, assoc, autoMem)
            } catch (e: CancellationException) {
                throw e   // 戒律 7：被并批作废的摘录以取消收场，不许装作正常完成
            } catch (_: Exception) {
                // 记忆是增强不是必需：摘录挂了这轮照样算完
            }
        })
        return ReplyResult(emotion = parsed.emotion, segments = toShow, messageIds = replyIds)
    }

    /** 写一箱，rev 冲突就重读重试（[rebuild] 拿最新箱内容算出这一把要写什么） */
    private suspend fun writeBoxRetrying(convId: String, kind: String, rebuild: (CompanionStore.ConvObjects) -> String?) {
        repeat(5) { attempt ->
            val objs = store.load(convId) ?: return
            val json = rebuild(objs) ?: return
            try {
                store.writeBox(objs.userId, kind, convId, objs.revs[kind] ?: 0L, json, System.currentTimeMillis())
                return
            } catch (_: CompanionStore.RevConflict) {
                kotlinx.coroutines.delay(30L * (attempt + 1))
            }
        }
    }

    /**
     * 「深度推演」第一趟（1.0.99.4 补大脑）：先以这个角色的身份在心里过一遍，再开口。
     * 与 App 的 runDeepPrepPass 同语义，推演指令/批注在 freechat-core 同一份（同码纪律）。
     * 任何一步失败都返回空串 —— 这只是锦上添花，不能因为它把正经回复搞挂了。
     */
    private suspend fun runDeepPrepPass(
        character: CharacterProfile,
        systemPrompt: String,
        working: List<Message>,
        memories: List<MemoryEntry>,
        turnText: String,
        assoc: Float,
        autoMem: Boolean
    ): String = runCatching {
        val msgs = mutableListOf<Map<String, Any?>>()
        // 用同一份人设：这一趟要「以角色的身份」想，不给它人设就只是在做阅读理解
        if (systemPrompt.isNotEmpty()) msgs.add(mapOf("role" to "system", "content" to systemPrompt))
        // 记忆给全（高质量档）：这一趟的目的之一就是找出「相关的往事」
        if (autoMem) {
            MemoryLogic.buildMemoryContext(memories, turnText, highQuality = true, associationLevel = assoc)
                .takeIf { it.isNotBlank() }?.let { msgs.add(mapOf("role" to "system", "content" to it)) }
        }
        var prepPrevTs = 0L
        msgs.addAll(CompanionHistory.pickHistory(working, CompanionHistory.historyBudgetTokens(enhanced = true)).map { m ->
            val prefix = CompanionPrompts.historyTimePrefix(prepPrevTs, m.timestamp, character.isNarrativeMode())
            prepPrevTs = m.timestamp
            mapOf<String, Any?>(
                "role" to when (m.role) { com.freechat.model.Role.USER -> "user"; com.freechat.model.Role.ASSISTANT -> "assistant"; else -> "system" },
                "content" to prefix + m.content
            )
        })
        msgs.add(mapOf("role" to "system", "content" to CompanionPrompts.deepPrepRequest()))

        val note = completer.complete(msgs, CompanionPrompts.companionTemperature(character.aiCreativity)).trim()
        if (note.isBlank()) return@runCatching ""

        // 第 4 行是它自己报的检索词 —— 拿它再检索一次记忆。
        // 这是整件事里最值钱的一步：第一遍检索是按用户这句话做的，而「该想起什么」
        // 往往不在这句话的字面里（对方说「随便」，真正相关的是三天前那句「我周三有空」）。
        val query = note.lines()
            .firstOrNull { it.trimStart().startsWith("4.") }
            ?.removePrefix("4.")?.trim().orEmpty()
            .takeIf { it.isNotBlank() && it != "无" }
        val second = query?.let {
            runCatching { MemoryLogic.buildMemoryContext(memories, it, highQuality = false, associationLevel = assoc) }.getOrNull()
        }.orEmpty()

        CompanionPrompts.deepPrepNote(note, second)
    }.getOrDefault("")

    /**
     * 记忆摘录 + 氛围快照写回。模型调用在锁外（1.0.99.4：不许挡下一轮回复/打断重等），
     * 写箱整体持对话锁（rev 单调 + 与回复写回互斥）。
     */
    private suspend fun extractAndRemember(
        convId: String, gate: RoundGate, turnText: String, replyText: String,
        userMsgIds: List<String>, replyIds: List<String>,
        narrative: Boolean, hq: Boolean, assoc: Float, autoMem: Boolean
    ) {
        val draft = try {
            MemoryExtractor.parseDraft(
                completer.complete(
                    MemoryExtractor.buildMessages(turnText, replyText, hq, plotMode = narrative, assocLevel = assoc),
                    0.1, CompanionPrompts.deepThinkExtras(false)
                ),
                plotMode = narrative
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return   // 记忆是增强不是必需：摘录挂了这轮照样算完（与 App 的兜底语义一致）
        }
        // 被并批/被中止：这轮都弃写了，摘录更不能留（引用的回复 id 马上就要删出箱）
        if (gate.isCancelled()) return
        val lock = locks.computeIfAbsent(convId) { Mutex() }
        lock.withLock {
            try {
                if (draft.summary.isNotEmpty()) {
                    val keywords = draft.keywords.filter { it.isNotBlank() }.ifEmpty { MemoryLogic.extractKeywords(turnText) }
                    val entry = MemoryEntry(
                        summary = draft.summary, keywords = keywords, kind = draft.kind,
                        eventDate = draft.date,
                        sourceMessageIds = userMsgIds + replyIds
                    )
                    // 重读最新箱再 upsert（rev 冲突自愈）：摘录是异步进来的，
                    // 不能用生成时那份旧快照，更不能盖掉 App 并发推上来的记忆
                    writeBoxRetrying(convId, "mems") { objs ->
                        AppJson.gson.toJson(MemoryLogic.trim(MemoryLogic.upsert(objs.memories, entry), hq))
                    }
                }
                if (draft.mood.isNotBlank() || draft.atmosphere.isNotBlank()) {
                    writeBoxRetrying(convId, "pcset") { objs ->
                        val o = objs.pcset.deepCopy()
                        if (draft.mood.isNotBlank()) o.addProperty(K_MOOD, draft.mood)
                        o.addProperty(K_MOOD_AT, System.currentTimeMillis())
                        if (draft.atmosphere.isNotBlank()) o.addProperty(K_ATMOSPHERE, draft.atmosphere)
                        if (draft.warmth != 0) o.addProperty(K_WARMTH, draft.warmth)
                        o.add(K_ATMO_SOURCES, JsonArray().apply { userMsgIds.forEach { add(it) } })
                        o.toString()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 记忆是增强不是必需：摘录挂了这轮照样算完（与 App 的兜底语义一致）
            }
        }
    }

    /**
     * 写回 msgs 箱（1.0.99.4b 冲突自愈，对齐 putObject 的 409 语义）：
     * rev 对不上=生成期间 App 推过同一箱 —— 重读最新箱、**只追加本轮新增**的消息
     * （fresh 优先：App 刚做的删除/修改绝不被旧快照回滚），rev 对上为止。
     * 大脑对 msgs 箱只增不改，追加式合并不会复活删除、不会盖掉并发写。
     */
    private suspend fun persistMessages(convId: String, additions: List<Message>, wechatBinding: WechatBinding? = null) {
        repeat(5) { attempt ->
            val fresh = store.load(convId) ?: return
            if (wechatBinding != null && !store.hasActiveWechatBinding(fresh.userId, convId, wechatBinding)) {
                throw CancellationException("wechat binding changed during persistence retry")
            }
            try {
                val known = fresh.messages.mapTo(HashSet()) { it.id }
                val merged = (fresh.messages + additions.filterNot { it.id in known }).sortedBy { it.timestamp }
                store.writeBox(fresh.userId, "msgs", convId, fresh.revs["msgs"] ?: 0L,
                    store.messagesToWire(merged), System.currentTimeMillis(), requiredWechatBinding = wechatBinding)
                return
            } catch (_: CompanionStore.RevConflict) {
                kotlinx.coroutines.delay(30L * (attempt + 1))
            }
        }
    }

    private fun perFrom(pcset: JsonObject): com.freechat.model.PerConvSettings =
        com.freechat.model.PerConvSettings(
            lastMood = pcset.get(K_MOOD)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            moodAtMs = pcset.get(K_MOOD_AT)?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
        )

    /** 当前是否在 AI 的睡觉窗口内（与 CVM.isSleepingNow 同语义：叙事档无作息；判定 = timePerception || sleepSimulation） */
    private fun isSleepingNow(convId: String, character: CharacterProfile?): Boolean {
        val ch = character ?: return false
        if (ch.isNarrativeMode()) return false
        if (!(ch.timePerception || ch.sleepSimulation)) return false
        val now = Calendar.getInstance()
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        if (sleepDateKey[convId] != dateKey) {
            val (s, w) = generateSleepSchedule(ch)
            sleepDateKey[convId] = dateKey
            sleepAtHour[convId] = s
            wakeAtHour[convId] = w
        }
        val sh = sleepAtHour[convId] ?: return false
        val wh = wakeAtHour[convId] ?: return false
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return if (sh <= wh) hour in sh until wh else hour >= sh || hour < wh
    }
}
