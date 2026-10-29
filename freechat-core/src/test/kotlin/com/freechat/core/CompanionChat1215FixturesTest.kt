package com.freechat.core

import com.freechat.model.CharacterProfile
import com.freechat.model.DialogueMode
import com.freechat.model.Message
import com.freechat.model.Role
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Opt-in export of newly authored synthetic text only; no screenshots, faces or user files. */
class CompanionChat1215FixturesTest {
    @Test fun exportFourMatchedSyntheticConversationRequests() {
        val out = System.getenv("FREECHAT_1215_EVAL_DIR")?.takeIf { it.isNotBlank() } ?: return
        val base = CharacterProfile(name = "顾宁", gender = "女", age = "25", dialogueMode = DialogueMode.WECHAT,
            personalityText = "冷静、直率，有自己的生活，对熟悉的人亲切自然，不喜欢说教。",
            relationshipPreset = "朋友", relationshipText = "认识多年，平时聊天随意。", timePerception = false, enableWebSearch = false)
        data class Case(val id: String, val character: CharacterProfile, val turns: List<String>, val image: String = "")
        val cases = listOf(
            Case("unexplained-photo", base.copy(relationshipPreset = "女朋友", relationshipText = "交往两年，关系稳定。"),
                listOf("刚收拾完桌子", "[用户发来一张未附说明的图片]"), "真人女性古装肖像；人物身份、图片来源及发送用意未知。"),
            Case("casual-night", base, listOf("我刚把杯子洗了", "我一点困意也没", "再聊几句呗")),
            Case("calm-not-cold", base, listOf("我在楼下等你", "走到路口才发现伞忘拿了")),
            Case("explicit-formal-register", base.copy(personalityText = "语言严谨、礼貌克制，明确要求正式完整的句子和规范标点；解释知识时说清必要因果，不用网络口头语。"),
                listOf("请用容易理解的方式说明，为什么零不能作除数？"))
        )
        assertEquals(4, cases.size)
        val records = cases.map { sample ->
            val history = sample.turns.mapIndexed { i, text -> Message(id = "synthetic-1215-$i",
                role = if (sample.turns.size > 1 && i == 0) Role.ASSISTANT else Role.USER,
                content = text, timestamp = 1_792_851_200_000L + i * 1000) }
            val messages = CompanionRequestBuilder.build(character = sample.character,
                systemPrompt = CompanionPrompts.buildCompanionSystemPrompt(sample.character, history),
                history = history, imageContext = sample.image,
                memoryContext = if (sample.id == "unexplained-photo") "用户明确说过自己是男性。" else "")
            mapOf("id" to sample.id, "request" to (mapOf("messages" to messages, "stream" to false,
                "temperature" to 1.05, "max_tokens" to 800) + CompanionPrompts.deepThinkExtras(false)))
        }
        File(out).mkdirs()
        File(out, "requests.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(records))
    }
}
