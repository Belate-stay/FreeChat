package com.freechat.core

import com.freechat.model.MemoryEntry
import com.freechat.model.PerConvSettings
import org.junit.Assert.*
import org.junit.Test

/**
 * M1 第二刀 golden：记忆合并/淘汰/检索、日期校验、摘录器解析、删除过滤——
 * 行为合同来自 MemoryManager/MessageDeletion/summarizeExchange 的既有语义（Beta811/DeletionAndSceneTest 护航面）。
 */
class MemoryGoldenTest {

    private fun entry(
        summary: String, ts: Long = 0L, kind: String = "detail",
        keywords: List<String> = emptyList(), date: String = "", sources: List<String> = emptyList(), id: String = summary
    ) = MemoryEntry(id = id, summary = summary, keywords = keywords, timestamp = ts, kind = kind, eventDate = date, sourceMessageIds = sources)

    // ============ 相似度与合并 ============

    @Test
    fun similarityShortTextFallsBackToContainment() {
        assertEquals(1.0, MemoryLogic.similarity("猫", "猫"), 0.001)
        assertEquals(0.9, MemoryLogic.similarity("养了一只猫", "猫"), 0.001)   // 短文本包含
        assertEquals(0.0, MemoryLogic.similarity("猫", "狗"), 0.001)
    }

    @Test
    fun upsertRewritesDuplicateKeepingFirstKnownDateAndId() {
        val old = entry("养了一只叫咪咪的猫", ts = 1, date = "2026年9月1日", sources = listOf("a"), id = "id-1", keywords = listOf("猫"))
        val new = entry("养了一只叫咪咪的猫（三花）", ts = 2, date = "", sources = listOf("b"), keywords = listOf("咪咪"), kind = "plot")
        val merged = MemoryLogic.upsert(listOf(old), new)
        assertEquals(1, merged.size)                                    // 不堆新条目
        assertEquals("id-1", merged[0].id)                              // 保留原 id
        assertEquals("2026年9月1日", merged[0].eventDate)                // 日期不因重复提及漂移
        assertEquals("plot", merged[0].kind)                            // 任一是主线则升主线
        assertEquals(setOf("a", "b"), merged[0].sourceMessageIds.toSet())
        assertTrue(merged[0].keywords.containsAll(listOf("猫", "咪咪")))
        // 新条目带明确事件日期时用新的
        val dated = MemoryLogic.upsert(listOf(old), new.copy(eventDate = "2026年9月5日"))
        assertEquals("2026年9月5日", dated[0].eventDate)
        // 不相似就追加
        assertEquals(2, MemoryLogic.upsert(listOf(old), entry("今天吃了火锅")).size)
    }

    @Test
    fun trimCapsDifferBetweenNormalAndHighQuality() {
        val many = (1..50).map { entry("条目$it", ts = it.toLong(), kind = if (it % 2 == 0) "plot" else "detail") }
        val normal = MemoryLogic.trim(many, highQuality = false)
        assertTrue(normal.size <= 30)
        assertTrue(normal.filter { it.isPlot() }.size <= 20)
        val hq = MemoryLogic.trim(many, highQuality = true)
        assertEquals(50, hq.size)                                       // 400 条内不淘汰
    }

    // ============ 检索 ============

    /** 互不相似的摘要（重复单字铺开）——模板化造句会被相似度去重误判成同一件事（那是数据长相不是逻辑） */
    private fun distinctSummary(prefix: String, n: Int): String = prefix + ('一'.code + n).toChar().toString().repeat(12)

    @Test
    fun searchAlwaysInjectsLatestPlotAndScoresDetails() {
        val plot = (1..10).map { entry(distinctSummary("主线", it), ts = it.toLong(), kind = "plot") }
        val detail = listOf(
            entry("用户家里养了一只叫咪咪的三花猫", ts = 20, keywords = listOf("猫")),
            entry("用户特别喜欢下雨天散步的感觉", ts = 21, keywords = listOf("雨")),
            entry("用户上周换了一份新的工作", ts = 22, keywords = listOf("工作"))
        )
        val out = MemoryLogic.searchRelevant(plot + detail, "我家猫今天生病了", maxResults = 5)
        // 主线永远注入（最新 8 条）+ 命中的细节（猫）
        assertTrue(out.contains(distinctSummary("主线", 10)))
        assertTrue(out.contains("三花猫"))
        assertFalse(out.contains("新的工作"))
        assertFalse(out.contains(distinctSummary("主线", 1)))            // 只取最新 8 条主线
        // 时间线排序：ts 小的在前
        assertTrue(out.indexOf(distinctSummary("主线", 3)) < out.indexOf(distinctSummary("主线", 10)))
    }

    @Test
    fun associationOnlyRaisesRecallNotConfabulation() {
        val detail = listOf(entry("三年前在渡口认识的", ts = 1, keywords = listOf("渡口")))
        // 1-2 档纯字面：不命中就是不注入
        val literal = MemoryLogic.searchRelevant(detail, "我们怎么认识的", associationLevel = 1f)
        assertEquals("", literal)
        // 3 档起相似度通道：相似才注入，不相似仍然不注入（宁可漏不可错）
        val wide = MemoryLogic.searchRelevant(detail, "三年前在渡口认识的旧事", associationLevel = 4f)
        assertTrue(wide.contains("渡口"))
        val unrelated = MemoryLogic.searchRelevant(detail, "今晚吃什么火锅好呢想吃辣的", associationLevel = 10f)
        assertEquals("", unrelated)
    }

    @Test
    fun highQualityContextIsFullTimelineWithHeader() {
        val plot = (1..250).map { entry(distinctSummary("主线", it), ts = it.toLong(), kind = "plot") }
        val detail = (1..80).map { entry(distinctSummary("细节", it), ts = 1000L + it, kind = "detail") }
        val out = MemoryLogic.buildHighQualityContext(plot + detail)
        assertTrue(out.startsWith("【长期记忆·完整档案"))
        assertTrue(out.contains(distinctSummary("主线", 250)))
        assertTrue(out.contains(distinctSummary("细节", 80)))
        assertFalse(out.contains(distinctSummary("主线", 1)))             // 200 条上限（1-50 出局）
        assertTrue(out.contains(distinctSummary("主线", 51)))
    }

    // ============ 日期与摘录解析 ============

    @Test
    fun eventDateStrictAndFreeform() {
        assertEquals("2026年9月3日", MemoryLogic.normalizeEventDate("2026-09-03"))
        assertEquals("", MemoryLogic.normalizeEventDate("昨天"))
        assertEquals("", MemoryLogic.normalizeEventDate("2026-13-45"))  // 非法月日解析失败
        // 世界历法 freeform：保住「大雍三年三月初五」，杀假名；超长是**截断到 24**不是拒绝（原语义）
        assertEquals("大雍三年三月初五", MemoryLogic.normalizeEventDate("大雍三年三月初五", freeform = true))
        assertEquals("", MemoryLogic.normalizeEventDate("がちや", freeform = true))
        assertEquals("x".repeat(24), MemoryLogic.normalizeEventDate("x".repeat(40), freeform = true))
    }

    @Test
    fun parseDraftHandlesJsonKanaAndFallback() {
        val ok = MemoryExtractor.parseDraft(
            """{"summary":"用户9月3日开学","kind":"plot","date":"2026-09-03","keywords":["开学","入学"],"mood":"平静","atmosphere":"聊得很平","warmth":5}"""
        )
        assertEquals("用户9月3日开学", ok.summary)
        assertEquals("plot", ok.kind)
        assertEquals("2026年9月3日", ok.date)
        assertEquals(listOf("开学", "入学"), ok.keywords)
        assertEquals("平静", ok.mood)
        assertEquals(2, ok.warmth)                                       // clamp 到 -2..2
        // 假名污染 → 空草稿
        assertEquals("", MemoryExtractor.parseDraft("これは日本語のまとめ").summary)
        // 没有 JSON → 退化为纯总结（detail）；围栏/前后缀容错
        val fallback = MemoryExtractor.parseDraft("好的，总结如下：\n```\n{\"summary\":\"吃了火锅\",\"kind\":\"plot\"}\n```")
        assertEquals("吃了火锅", fallback.summary)
        assertEquals("plot", fallback.kind)
        // 叙事档日期走 freeform
        val world = MemoryExtractor.parseDraft("""{"summary":"进京赶考","kind":"plot","date":"大雍三年三月初五"}""", plotMode = true)
        assertEquals("大雍三年三月初五", world.date)
    }

    @Test
    fun extractorPromptSwitchesVariantAndBoundary() {
        val hq = MemoryExtractor.buildMessages("你好", "在", highQuality = true, assocLevel = 4f)
        assertTrue(hq[0]["content"]!!.contains("记忆摘录器"))
        assertTrue(hq[0]["content"]!!.contains("5-8 个词"))
        val normal = MemoryExtractor.buildMessages("你好", "在", highQuality = false, assocLevel = 1f)
        assertTrue(normal[0]["content"]!!.contains("记忆归纳器"))
        assertTrue(normal[0]["content"]!!.contains("2-3 个原词"))
        val plot = MemoryExtractor.buildMessages("你好", "在", highQuality = false, plotMode = true)
        assertTrue(plot[0]["content"]!!.contains("信息边界"))
        assertTrue(plot[0]["content"]!!.contains("世界设定的历法"))
        assertFalse(normal[0]["content"]!!.contains("信息边界"))
    }

    // ============ 删除过滤 ============

    @Test
    fun visualDeletionNeverPurgesMemories() {
        val deleted = setOf("picture", "story")
        val visual = setOf("picture")
        val memIds = DeletionLogic.memoryDeletionIds(deleted, visual)
        assertEquals(setOf("story"), memIds)                            // 只有文本墓碑作废记忆
        val legacy = entry("尚未上船", sources = emptyList())
        // 只删图：legacy 记忆与氛围原样保留
        val visualOnly = DeletionLogic.memoryDeletionIds(setOf("picture"), setOf("picture"))
        assertEquals(listOf(legacy), DeletionLogic.filterMemories(listOf(legacy), visualOnly))
        val per = PerConvSettings(atmosphere = "等船", atmosphereSourceMessageIds = listOf("picture"))
        assertEquals("等船", DeletionLogic.atmosphere(per, visualOnly).atmosphere)
    }

    @Test
    fun derivedAndLegacyMemoriesPurgeOnTextDeletion() {
        val memIds = setOf("story")
        val derived = entry("他们登船了", sources = listOf("story", "other"))
        val unrelated = entry("用户喜欢猫", sources = listOf("keep"))
        val legacy = entry("旧档案记忆", sources = emptyList())
        val out = DeletionLogic.filterMemories(listOf(derived, unrelated, legacy), memIds)
        assertEquals(listOf(unrelated), out)                            // 来源被删+legacy 清除，无关保留
        // 氛围：来源被删 / 无来源（且有删除）都清零
        val cleared1 = DeletionLogic.atmosphere(PerConvSettings(atmosphere = "等船", atmosphereSourceMessageIds = listOf("story")), memIds)
        assertEquals("", cleared1.atmosphere)
        val cleared2 = DeletionLogic.atmosphere(PerConvSettings(atmosphere = "等船"), memIds)
        assertEquals("", cleared2.atmosphere)
        val kept = DeletionLogic.atmosphere(PerConvSettings(atmosphere = "等船", atmosphereSourceMessageIds = listOf("keep")), memIds)
        assertEquals("等船", kept.atmosphere)
    }
}
