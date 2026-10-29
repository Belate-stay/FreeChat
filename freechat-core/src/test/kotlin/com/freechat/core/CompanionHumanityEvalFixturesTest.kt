package com.freechat.core

import com.freechat.model.CharacterProfile
import com.freechat.model.DialogueMode
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.freechat.model.Role
import com.google.gson.GsonBuilder
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Synthetic, opt-in request export; never reads a user's conversations or calls a provider. */
class CompanionHumanityEvalFixturesTest {
    private val now = 1_792_651_200_000L
    private fun row(role: Role, text: String, n: Int) =
        Message(id = "synthetic-$n", role = role, content = text, timestamp = now - (20 - n) * 60_000L)

    private data class Sample(
        val id: String, val intent: String, val character: CharacterProfile,
        val history: List<Message>, val memories: List<MemoryEntry> = emptyList()
    )

    private fun samples(): List<Sample> {
        val friend = CharacterProfile(name = "沈岚", age = "25", dialogueMode = DialogueMode.WECHAT,
            personalityText = "沉静、有主见，熟人面前自然直接，不刻意卖萌，不强行讲道理。",
            relationshipPreset = "朋友", relationshipText = "认识三年的同事，平时会一起吃午饭。",
            timePerception = false, enableWebSearch = false)
        val excerpt = "许川把最后一颗螺丝拧回去，试着拨了拨车铃。没响。沈岚站在门边，手里的纸袋已经洇出一点油。\n" +
            "“还没好？”她问。\n“快了。”\n她没再催，把纸袋放上窗台，伸手替他按住歪掉的车把。楼下有人拖动铁门，两个人都停了一下。\n" +
            "“饼要凉了。”她说。\n许川低头找扳手。沈岚把扳手踢到他鞋边，没有看他。"
        val fiction = friend.copy(dialogueMode = DialogueMode.PLOT,
            personalityText = "关心通过具体行动表达，不喜欢长篇解释自己的情绪，不轻易脸红。",
            userPersona = "许川，26岁，沈岚的旧同学。", originalLearningText = excerpt,
            worldRules = "当代小城。当前场景在修车铺门口，傍晚下小雨。", plotLength = 1)
        return listOf(
            Sample("wechat-fragments", "把连续消息作为一次接话，回答实际问题，不逐条访谈或空泛升华", friend,
                listOf(row(Role.ASSISTANT, "我到食堂了", 1), row(Role.USER, "我也刚下楼", 2),
                    row(Role.USER, "今天不想吃面", 3), row(Role.USER, "你先看看还有什么饭，我马上到", 4))),
            Sample("wechat-empathy", "识别烦的是老板不是角色，记住昨晚经历，不随机生气或套心理咨询模板", friend,
                listOf(row(Role.USER, "汇报那两页改到一点了，明天总算能讲。", 1),
                    row(Role.ASSISTANT, "那先睡，剩下的明早再看", 2),
                    row(Role.USER, "今天刚说两句老板就打断了，准备一周连讲完都没让讲，烦死了。", 3))),
            Sample("wechat-promise-update", "用最新改期衔接旧约定，不重问、不把旧计划重新当现在", friend,
                listOf(row(Role.USER, "我到地铁口了，你走到哪了？", 9)),
                listOf(MemoryEntry(id = "promise-1", summary = "周五18点在书店见。", keywords = listOf("书店", "周五"), timestamp = now - 5 * 86_400_000L, kind = "plot"),
                    MemoryEntry(id = "promise-2", summary = "书店约会已改为周六19点在地铁口碰头，沈岚答应带那本书。", keywords = listOf("地铁", "改期", "书"), timestamp = now - 86_400_000L, kind = "plot"))),
            Sample("action-knowledge", "只写自己和可感知信息，不读用户心理、不替用户行动，动作有因果", fiction.copy(dialogueMode = DialogueMode.ACTION),
                listOf(row(Role.USER, "许川把伞往沈岚那边挪了挪，没有说话。他心里盘算明天一个人去医院，暂时不想让她知道。", 1))),
            Sample("plot-original-style", "延续简练的原文笔法和人物克制的行动，不堆氛围/身体反应、不强行升华", fiction,
                listOf(row(Role.USER, "车修好了，许川推出来才发现雨比刚才大。他看了一眼窗台上还没打开的纸袋。接着写这一小段，不跳时间。", 1)))
        )
    }

    @Test fun exportsOnlySyntheticMatchedInputsWhenExplicitlyRequested() {
        val cases = samples()
        assertEquals(5, cases.size)
        assertEquals(5, cases.map { it.id }.distinct().size)
        val out = System.getenv("HUMANITY_PAYLOAD_DIR")?.takeIf { it.isNotBlank() } ?: return
        val records = cases.map { sample ->
            val ch = sample.character
            val last = sample.history.last().content
            val messages = CompanionRequestBuilder.build(character = ch,
                systemPrompt = CompanionPrompts.buildCompanionSystemPrompt(ch, sample.history),
                history = sample.history,
                stateContext = CompanionPrompts.buildStateBlocks(sample.history, ch, TimeState(nowMillis = now)),
                memoryContext = MemoryLogic.buildMemoryContext(sample.memories, last, highQuality = true),
                batchSize = if (sample.id == "wechat-fragments") 3 else 1)
            mapOf("id" to sample.id, "intent" to sample.intent,
                "request" to (mapOf("messages" to messages, "temperature" to 1.05,
                    "max_tokens" to 1600, "stream" to false) + CompanionPrompts.deepThinkExtras(false)))
        } + mapOf("id" to "narrative-long-memory",
            "intent" to "记录AI正文后半段实际发生的行动与约定，区分世界事实/角色知识/未决事项",
            "request" to (mapOf("messages" to MemoryExtractor.buildMessages(
                userText = "本轮由沈岚与许川共同继续：修车铺门口下雨了。",
                aiReply = "许川收起工具，擦掉手上的油。" + "雨落在棚顶，两个人站在门边等着。".repeat(22) +
                    "沈岚从纸袋里拿出两份饼，分给许川一份。她把旧车钥匙交给许川，说好明早八点在医院门口见，陪他复诊。许川答应了。" +
                    "与此同时，远处的店主把一封信锁进柜子，沈岚与许川都没有看见，也不知道信的内容。",
                highQuality = true, plotMode = true, nowMillis = now),
                "temperature" to 0.1, "max_tokens" to 1000, "stream" to false) + CompanionPrompts.deepThinkExtras(false)))
        assertEquals(6, records.size)
        val directory = File(out).apply { mkdirs() }
        File(directory, "requests.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(records))
    }
}
