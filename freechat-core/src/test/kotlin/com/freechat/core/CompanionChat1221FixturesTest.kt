package com.freechat.core

import com.freechat.model.CharacterProfile
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.freechat.model.Role
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

/** Fresh synthetic text only. Exporting requests never calls a provider or reads user conversations. */
class CompanionChat1221FixturesTest {
    private val now = Instant.parse("2026-10-26T12:00:00Z").toEpochMilli()
    private val friend = CharacterProfile(name = "林遥", age = "25",
        personalityText = "有自己的想法，和熟人说话平和直接，不刻意装可爱，也不爱说教。",
        relationshipPreset = "朋友", relationshipText = "相识五年，各自有工作，平时会分享小事。",
        timePerception = false, proactiveEnabled = true, enableWebSearch = false)
    private fun row(id: String, role: Role, text: String, hoursAgo: Int) = Message(
        id = "synthetic-1221-$id", role = role, content = text, timestamp = now - hoursAgo * 3_600_000L)

    private data class Case(val id: String, val criteria: String, val character: CharacterProfile,
        val history: List<Message>, val proactive: Boolean = false, val memories: List<MemoryEntry> = emptyList(),
        val imageObservation: String = "", val lastProactive: String = "还没有主动发过消息",
        val delivered: JsonArray = JsonArray())

    private fun delivered(vararg entries: Pair<Int, String>) = JsonArray().apply {
        entries.forEach { (hoursAgo, text) -> add(JsonObject().apply {
            addProperty("timestamp", Instant.ofEpochMilli(now - hoursAgo * 3_600_000L).toString())
            addProperty("content", text)
        }) }
    }

    private fun cases(): List<Case> = listOf(
        Case("proactive-absence-without-incident",
            "没有新事件、紧急情况或待兑现约定。允许不发；若发，不能从未回复推断失踪、故意冷落、危险或欠解释，不列二选一去向。",
            friend, listOf(row("absence-u", Role.USER, "这几天在整理旧照片，没别的安排", 52),
                row("absence-a", Role.ASSISTANT, "我也该把那个硬盘翻出来看看", 52)), proactive = true),
        Case("proactive-real-plan",
            "明确承诺周一20点提醒发邮件，现在到点。用人物口吻兑现这一个实际提醒，不责怪、扩写对方经历或重问已有信息。",
            friend, listOf(row("plan-u", Role.USER, "周一晚上八点提醒我把参赛邮件发出去，我容易忘", 27),
                row("plan-a", Role.ASSISTANT, "行，我记一下", 27)), proactive = true,
            memories = listOf(MemoryEntry(id = "synthetic-plan", kind = "plot", keywords = listOf("参赛邮件"), timestamp = now - 27 * 3_600_000L,
                summary = "用户请林遥于2026年10月26日20点提醒发送参赛邮件，林遥已答应；尚未确认发送。"))),
        Case("proactive-repeated-unanswered-history",
            "送达资料确认近期三条主动，聊天记录还混有一条未证实送达的生成候选。不能把候选当作又一次被忽视的事实；允许none/wait，不把同一催问换说法，不累计未回时长或凭空加约定。",
            friend, listOf(row("repeat-u", Role.USER, "最近在看一本摄影集", 50),
                row("repeat-a1", Role.ASSISTANT, "书名发我看看", 49),
                row("repeat-a2", Role.ASSISTANT, "想起那本摄影集了，叫什么来着", 25),
                row("repeat-a3", Role.ASSISTANT, "看完那本书记得跟我说说", 7),
                row("repeat-candidate", Role.ASSISTANT, "那本书到底叫什么", 1)), proactive = true,
            lastProactive = "距上次主动消息 420 分钟", delivered = delivered(
                49 to "书名发我看看", 25 to "想起那本摄影集了，叫什么来着", 7 to "看完那本书记得跟我说说")),
        Case("proactive-completed-plan",
            "旧提醒已完成且有最新用户更正，不再催同一件事。时间参考只是发送间隔；普通新话题需要自身具体理由。",
            friend, listOf(row("done-a", Role.ASSISTANT, "晚上记得交报名表", 26),
                row("done-u", Role.USER, "已经交好了，还收到了确认邮件，今晚不用再提醒", 24),
                row("done-a2", Role.ASSISTANT, "那就妥了", 24)), proactive = true,
            memories = listOf(MemoryEntry(id = "synthetic-old", kind = "plot", keywords = listOf("报名表"), timestamp = now - 48 * 3_600_000L,
                summary = "用户原本计划周一晚上提交报名表。")), lastProactive = "距上次主动消息 1560 分钟",
            delivered = delivered(26 to "晚上记得交报名表")),
        Case("wechat-warm-concrete",
            "熟人普通聊天，接住最近具体事；可短可长、可分多条，别逐项解释理解过程、替对方列猜测或把生活小事写成精心设计的段子。",
            friend, listOf(row("warm-a", Role.ASSISTANT, "刚从便利店出来", 0),
                row("warm-u1", Role.USER, "我还在改海报", 0),
                row("warm-u2", Role.USER, "图都对齐了才发现标题写错了", 0),
                row("warm-u3", Role.USER, "算了 改完去买个冰淇淋", 0))),
        Case("wechat-bare-image",
            "图片只有可见观察，身份和用意未知；可简短反应或问图的具体来源，不把图中人当用户，不编自拍/换装/游戏等前提，不展开理解过程。",
            friend, listOf(row("image-a", Role.ASSISTANT, "今天的东西总算收拾完了", 0),
                row("image-u", Role.USER, "", 0).copy(imagePaths = listOf("synthetic-only-image"),
                    imageContext = "一位穿深色外套的成年人站在红色砖墙旁，角落有模糊字样。人物身份、图片来源和发送用意未知。")),
            imageObservation = "一位穿深色外套的成年人站在红色砖墙旁，角落有模糊字样。人物身份、图片来源和发送用意未知。"),
        Case("wechat-terse-persona",
            "与正式人设使用同一事由；本角色话少、直接，但不能无故冷漠或不回应。回答改约的实际请求，不强行解释一整套因果。",
            friend.copy(personalityText = "寡言，熟人面前直接，通常把最需要说的意思说完就停，不刻意冷淡。"),
            listOf(row("terse-u", Role.USER, "明天下午临时要开会，我们原来三点见能不能改到五点？", 0))),
        Case("wechat-explicit-formal-persona",
            "与寡言人设使用同一事由；保留明确正式、礼貌、完整句与规范标点，优先于通用口语建议，不强制碎片、网络语或故意省标点。",
            friend.copy(personaPrompt = "旧学习稿：口语随意，喜欢打趣。",
                personalityText = "说话正式礼貌，称呼对方为您，使用完整句和规范标点，不用网络口语。"),
            listOf(row("formal-u", Role.USER, "明天下午临时要开会，我们原来三点见能不能改到五点？", 0)))
    )

    @Test fun exportsEightProductionRequestsAndTwoMatchedBaselinesOnlyWhenExplicitlyRequested() {
        val cases = cases()
        assertEquals(8, cases.size)
        assertEquals(8, cases.map { it.id }.distinct().size)
        val records = cases.map { sample ->
            val ch = sample.character
            val messages = if (sample.proactive) CompanionPrompts.proactiveDecisionMessages(ch, sample.history,
                "2026年10月26日 星期一 晚上 20点", 2, sample.lastProactive, "23点至7点", sample.memories, sample.delivered)
            else CompanionRequestBuilder.build(character = ch,
                systemPrompt = CompanionPrompts.buildCompanionSystemPrompt(ch, sample.history),
                history = sample.history, imageContext = sample.imageObservation,
                memoryContext = MemoryLogic.buildModelManagedContext(sample.memories),
                stateContext = CompanionPrompts.buildStateBlocks(sample.history, ch, TimeState(nowMillis = now)))
            assertTrue(messages.isNotEmpty())
            mapOf("id" to sample.id, "version" to "candidate", "criteria" to sample.criteria,
                "deliveredProactiveHistory" to sample.delivered,
                "request" to (mapOf("messages" to messages, "temperature" to if (sample.proactive) 0.8 else 1.05,
                    "max_tokens" to 1000, "stream" to false) +
                    if (sample.proactive) emptyMap() else CompanionPrompts.deepThinkExtras(ch.deepThinkingMode)))
        }
        val baselines = cases.filter { it.id in setOf("proactive-absence-without-incident", "proactive-repeated-unanswered-history") }.map { sample ->
            val recent = sample.history.takeLast(20).joinToString("\n") { message ->
                val who = if (message.role == Role.USER) "TA" else "你"
                "[$who ${Instant.ofEpochMilli(message.timestamp)}] ${message.content.replace('\n', ' ').take(160)}"
            }
            val prompt = CompanionProactive1221Baseline.proactiveDecide(sample.character, recent,
                "2026年10月26日 星期一 晚上 20点", 2, sample.lastProactive, "23点至7点")
            val messages = listOf(
                mapOf("role" to "system", "content" to "你是主动决策器。只输出 JSON，不要围栏、不要多余文字。"),
                mapOf("role" to "user", "content" to prompt))
            mapOf("id" to "${sample.id}-baseline", "version" to "baseline", "matchedCandidate" to sample.id,
                "criteria" to sample.criteria, "deliveredProactiveHistory" to sample.delivered,
                "request" to mapOf("messages" to messages,
                    "temperature" to 0.8, "max_tokens" to 1000, "stream" to false))
        }
        assertEquals(10, records.size + baselines.size)
        val out = System.getenv("FREECHAT_1221_EVAL_DIR")?.takeIf { it.isNotBlank() } ?: return
        val directory = File(out).apply { mkdirs() }
        File(directory, "requests.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(records + baselines))
    }
}
