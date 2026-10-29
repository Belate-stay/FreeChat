package com.freechat.core

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class CompanionHumanityReplyTest {
    @Suppress("UNCHECKED_CAST")
    private fun prepare(lines: List<String>, narrative: Boolean = false): List<String> {
        val method = CompanionRhythm::class.java.methods.firstOrNull { it.name == "prepareSegments" }
            ?: throw AssertionError("Missing lossless reply preparation")
        return method.invoke(CompanionRhythm, lines, narrative) as List<String>
    }

    @Suppress("UNCHECKED_CAST")
    private fun delays(lines: List<String>, narrative: Boolean = false, elapsedMs: Long = 0): List<Long> {
        val method = CompanionRhythm::class.java.methods.firstOrNull { it.name == "deliveryDelaysMs" }
            ?: throw AssertionError("Missing content-sensitive delivery schedule")
        return method.invoke(CompanionRhythm, lines, narrative, elapsedMs) as List<Long>
    }

    @Test fun allWrittenMeaningAndNaturalMessageBoundariesSurvive() {
        val input = listOf("我已经出门了", "地铁停运，改坐公交", "所以会晚十分钟，不是让你一直等")
        val output = prepare(input)
        assertEquals(input, output)
        for (line in input) assertTrue("Lost actual meaning: $line", output.any { line in it })
        assertEquals(output, prepare(input))
    }

    @Test fun distinctVoiceCarriersAndTextAreNeverDiscardedOrBroken() {
        val lines = listOf("<voice style=\"轻快\">已经出门了</voice>", "路上信号不好", "<voice>到地方再说</voice>")
        assertEquals(lines, prepare(lines))
        val blank = prepare(listOf("", "一条", "  "))
        assertEquals(listOf("一条"), blank)
    }

    @Test fun narrativeParagraphsRemainOneCompleteText() {
        val lines = listOf("沈岚把钥匙递给许川。", "“明早八点。”她说。", "许川点头，收好钥匙。")
        val output = prepare(lines, narrative = true)
        assertEquals(1, output.size)
        for (line in lines) assertTrue(output.single().contains(line))
    }

    @Test fun realGenerationTimePaysTheFirstMessageDelay() {
        val lines = listOf("好", "路上有点堵，我到门口了再给你发消息")
        val fresh = delays(lines)
        val alreadyWaited = delays(lines, elapsedMs = 30_000)
        assertEquals(lines.size, fresh.size)
        assertEquals(0L, alreadyWaited.first())
        assertEquals(fresh.drop(1), alreadyWaited.drop(1))
        assertTrue(fresh.all { it in 0L..3000L })
        assertEquals(fresh, delays(lines))
        assertTrue(delays(listOf("好", "好"))[1] < delays(listOf("好", "说清一件事".repeat(35)))[1])
        assertEquals(listOf(0L), delays(listOf("一段完整的剧情"), narrative = true, elapsedMs = 30_000))
    }

    @Test fun silenceIsExplicitControlNotAnEmptyOrAngryResponse() {
        val deliberate = CompanionReplyParser.parse("[情绪:生气]\n[回应:不回复]", false)
        assertTrue(Gson().toJsonTree(deliberate).asJsonObject.get("silence")?.asBoolean == true)
        assertTrue(deliberate.bodyLines.isEmpty())
        val empty = CompanionReplyParser.parse("", false)
        assertFalse(Gson().toJsonTree(empty).asJsonObject.get("silence")?.asBoolean == true)
        val angry = CompanionReplyParser.parse("[情绪:生气]\n这事我不同意\n晚点再说理由", false)
        assertEquals(listOf("这事我不同意", "晚点再说理由"), angry.bodyLines)
    }

    @Test fun aControlAlongsideActualSpeechCannotSilenceThatSpeech() {
        val p = CompanionReplyParser.parse("[情绪:平静]\n[回应:不回复]\n我看到了，晚点回你", false)
        assertEquals(listOf("我看到了，晚点回你"), p.bodyLines)
        assertFalse(Gson().toJsonTree(p).asJsonObject.get("silence")?.asBoolean == true)
        val quoted = CompanionReplyParser.parse("你发来的“[回应:不回复]”是什么意思", false)
        assertFalse(quoted.bodyLines.isEmpty())
        assertEquals(listOf("你发来的“[回应:不回复]”是什么意思"), quoted.bodyLines)
        assertFalse(Gson().toJsonTree(quoted).asJsonObject.get("silence")?.asBoolean == true)
        val narrative = CompanionReplyParser.parse("[回应:不回复]", true)
        assertFalse(Gson().toJsonTree(narrative).asJsonObject.get("silence")?.asBoolean == true)
    }

    @Test fun aServerPlanCannotIgnoreAlreadyElapsedNetworkTime() {
        assertEquals(listOf(0L, 700L), CompanionRhythm.validatedDelaysMs(listOf(1500L, 700L), listOf("好", "到了"), false, 30_000))
    }
}
