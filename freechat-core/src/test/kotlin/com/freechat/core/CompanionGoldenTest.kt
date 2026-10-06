package com.freechat.core

import com.freechat.model.CharacterProfile
import com.freechat.model.DialogueMode
import com.freechat.model.Message
import com.freechat.model.PerConvSettings
import com.freechat.model.Role
import org.junit.Assert.*
import org.junit.Test

/**
 * M1 golden 表征测试：把拟人机制核心（提示词组装/时间轴/情绪衰减/创造力档位/历史裁剪/格式自检/
 * 主动智能编解码）的行为钉死在共享模块上——抽取前这些全无单测（覆盖缺口见更新日志 2026-10-04）。
 * 断言语义而非整串：改文案不炸测试，改行为必炸。
 */
class CompanionGoldenTest {

    private fun char(
        mode: Int = DialogueMode.WECHAT,
        name: String = "旅人",
        persona: String = "",
        personality: String = "",
        memory: String = "",
        hq: Boolean = false,
        proactive: Boolean = false
    ) = CharacterProfile(
        name = name, dialogueMode = mode, personaPrompt = persona,
        personalityText = personality, memoryPerception = memory,
        highQualityMemory = hq, proactiveEnabled = proactive
    )

    private fun msg(role: Role, content: String, ts: Long = 1000L, scene: Boolean = false) =
        Message(id = "m$ts$role", role = role, content = content, timestamp = ts, sceneVisualization = scene)

    // ============ 提示词组装 ============

    @Test
    fun promptStartsWithPersonaAndEndsWithTimeContext() {
        val p = CompanionPrompts.buildCompanionSystemPrompt(char(), listOf(msg(Role.USER, "你好")))
        assertTrue(p.startsWith("你是一个在微信上跟朋友聊天的人"))
        assertTrue(p.contains("现在是 "))                       // 时间上下文钉在尾部
        assertTrue(p.indexOf("现在是 ") > p.indexOf("【你的人设"))
    }

    @Test
    fun wechatModeHasEmotionTagRuleButNarrativeModesDoNot() {
        val wechat = CompanionPrompts.buildCompanionSystemPrompt(char(DialogueMode.WECHAT), emptyList())
        val action = CompanionPrompts.buildCompanionSystemPrompt(char(DialogueMode.ACTION), emptyList())
        val plot = CompanionPrompts.buildCompanionSystemPrompt(char(DialogueMode.PLOT), emptyList())
        assertTrue(wechat.contains("[情绪:开心]"))
        assertFalse(action.contains("[情绪:开心]"))
        assertFalse(plot.contains("[情绪:开心]"))
        // 三档台词引号纪律
        assertTrue(action.contains("「」"))
        assertTrue(plot.contains("双引号“”"))
    }

    @Test
    fun depollutionBlockOnlyWhenHistoryHasAssistantRows() {
        val without = CompanionPrompts.buildCompanionSystemPrompt(char(), listOf(msg(Role.USER, "你好")))
        val with = CompanionPrompts.buildCompanionSystemPrompt(char(), listOf(msg(Role.ASSISTANT, "在"), msg(Role.USER, "你好")))
        assertFalse(without.contains("只取内容，不取写法"))
        assertTrue(with.contains("只取内容，不取写法"))
    }

    @Test
    fun proactiveBlockOnlyForWechatWithSwitchOn() {
        val on = CompanionPrompts.buildCompanionSystemPrompt(char(proactive = true), emptyList())
        val off = CompanionPrompts.buildCompanionSystemPrompt(char(proactive = false), emptyList())
        val plotOn = CompanionPrompts.buildCompanionSystemPrompt(char(DialogueMode.PLOT, proactive = true), emptyList())
        assertTrue(on.contains("[主动智能]"))
        assertFalse(off.contains("[主动智能]"))
        assertFalse(plotOn.contains("[主动智能]"))   // 只在微信聊天档
        assertTrue(on.contains("你手头没有待触发的提醒"))
        val pending = CompanionPrompts.buildCompanionSystemPrompt(
            char(proactive = true), emptyList(), pendingReminder = PendingReminder(1791115000000L, "约好了三点")
        )
        assertTrue(pending.contains("你给自己定的提醒"))
        assertTrue(pending.contains("约好了三点"))
    }

    @Test
    fun atmosphereAndConvRulesBlocksAppearWhenProvided() {
        val p = CompanionPrompts.buildCompanionSystemPrompt(
            char(), emptyList(), convRules = "全程说文言文", timeState = TimeState(atmosphere = "等船")
        )
        assertTrue(p.contains("【你们最近的气氛】等船"))
        assertTrue(p.contains("全程说文言文"))
        // 对话规则在时间上下文之前
        assertTrue(p.indexOf("全程说文言文") < p.indexOf("现在是 "))
    }

    @Test
    fun highQualityMemoryInjectsRawTextAtTopPriority() {
        val hq = CompanionPrompts.buildCompanionSystemPrompt(char(hq = true, memory = "三年前在渡口认识"), emptyList())
        assertTrue(hq.contains("最高优先级的事实"))
        assertTrue(hq.contains("三年前在渡口认识"))
        val normal = CompanionPrompts.buildCompanionSystemPrompt(char(hq = false, memory = "三年前在渡口认识"), emptyList())
        assertTrue(normal.contains("记忆感知（用户原话）"))
        assertFalse(normal.contains("最高优先级的事实"))
    }

    // ============ 时间轴 ============

    @Test
    fun historyTimePrefixJumpsOnlyAfterThirtyMinutes() {
        val t0 = 1_700_000_000_000L
        assertEquals("", CompanionPrompts.historyTimePrefix(t0, t0 + 29 * 60_000L, narrative = false))
        assertTrue(CompanionPrompts.historyTimePrefix(t0, t0 + 31 * 60_000L, narrative = false).contains("（"))
        // 叙事档：相对时间词，不掺现实日期
        val word = CompanionPrompts.historyTimePrefix(t0, t0 + 5 * 60 * 60_000L, narrative = true)
        assertEquals("（几小时前）", word)
        assertEquals("（昨日）", CompanionPrompts.historyTimePrefix(t0, t0 + 30 * 60 * 60_000L, narrative = true))
        assertEquals("（三五日前）", CompanionPrompts.historyTimePrefix(t0, t0 + 3 * 24 * 60 * 60_000L, narrative = true))
        // 叙事档首条不标
        assertEquals("", CompanionPrompts.historyTimePrefix(0L, t0, narrative = true))
    }

    @Test
    fun narrativeTimeContextForbidsRealWorldDates() {
        val p = CompanionPrompts.buildTimeContext(emptyList(), narrative = true, timePerception = true)
        assertTrue(p.contains("绝对不要出现现实世界的日期"))
        assertFalse(p.contains("现在是"))
    }

    @Test
    fun wechatTimeContextCarriesGoodnightAndMissedSleepLines() {
        val now = 1_791_115_000_000L
        val state = TimeState(
            nowMillis = now, userSaidGoodnightAtMs = now - 10 * 60_000L,
            sleepingMissedCount = 3, sleepAtHour = 23, wakeAtHour = 7,
            moodAtMs = now - 50 * 3600_000L, atmosphere = "冷战过又和好"
        )
        val p = CompanionPrompts.buildTimeContext(listOf(msg(Role.USER, "在吗"), msg(Role.USER, "人呢")), timePerception = true, state = state)
        assertTrue(p.contains("说要去睡了/道了晚安"))
        assertTrue(p.contains("期间用户发了 3 条消息没回"))
        assertTrue(p.contains("距离你们上次聊天已经 2 天了"))
        assertTrue(p.contains("上次聊完时的气氛：冷战过又和好"))
    }

    // ============ 情绪 ============

    @Test
    fun moodResidueFadesAfterSixHours() {
        val per = PerConvSettings(lastMood = "ANGRY", moodAtMs = 1_000_000L)
        assertEquals(CompanionMood.ANGRY, moodWithResidue("哦哦", per, nowMillis = 1_000_000L + 30 * 60_000L))
        assertEquals(CompanionMood.NEUTRAL, moodWithResidue("哦哦", per, nowMillis = 1_000_000L + 361 * 60_000L))
        // 直接线索优先于快照
        assertEquals(CompanionMood.HAPPY, moodWithResidue("哈哈太好了", per, nowMillis = 1_000_000L))
    }

    @Test
    fun emotionLabelsAndLocalFallback() {
        assertEquals(CompanionMood.WRONGED, parseEmotionLabel("委屈"))
        assertNull(parseEmotionLabel("无语"))
        assertEquals(CompanionMood.ANGRY, detectCompanionMood("滚，别烦我"))
        assertEquals(CompanionMood.NEUTRAL, detectCompanionMood("今天天气不错"))
    }

    // ============ 创造力/温度 ============

    @Test
    fun creativityBandsFollowAnchors() {
        assertTrue(CompanionPrompts.buildCreativityRule(2f, DialogueMode.PLOT).contains("克制跟随"))
        assertTrue(CompanionPrompts.buildCreativityRule(4f, DialogueMode.PLOT).contains("轻微延伸"))
        assertTrue(CompanionPrompts.buildCreativityRule(5f, DialogueMode.PLOT).contains("适度主动"))
        assertTrue(CompanionPrompts.buildCreativityRule(8f, DialogueMode.PLOT).contains("明显主动"))
        assertTrue(CompanionPrompts.buildCreativityRule(9f, DialogueMode.PLOT).contains("大胆猎奇"))
        // 落地方式按档位收窄：微信档不许用路人体现创造力
        assertTrue(CompanionPrompts.buildCreativityRule(9f, DialogueMode.WECHAT).contains("不引入其他角色"))
    }

    @Test
    fun companionTemperatureLinearMap() {
        assertEquals(0.85, CompanionPrompts.companionTemperature(1f), 0.001)
        assertEquals(1.05, CompanionPrompts.companionTemperature(5f), 0.001)
        assertEquals(1.30, CompanionPrompts.companionTemperature(10f), 0.001)
        assertEquals(1.05, CompanionPrompts.companionTemperature(null), 0.001)
    }

    // ============ 历史裁剪 ============

    @Test
    fun pickHistoryStopsAtBudgetAndSkipsSceneRows() {
        val rows = (1..10).map { msg(Role.ASSISTANT, "字".repeat(200), ts = it.toLong(), scene = it == 5) }
        val picked = CompanionHistory.pickHistory(rows, budgetTokens = CompanionHistory.estimateTokens("字".repeat(200)) * 3)
        assertTrue(picked.size in 2..4)                       // 预算约 3 条
        assertTrue(picked.none { it.sceneVisualization })      // 场景图不进历史
        assertTrue(picked.last().timestamp >= picked.first().timestamp)
    }

    // ============ 格式自检 ============

    @Test
    fun formatViolationDetectsCrossModeQuoting() {
        assertTrue(CompanionPrompts.isFormatViolation("她「嗯」了一声", DialogueMode.PLOT))
        assertTrue(CompanionPrompts.isFormatViolation("她说“嗯”", DialogueMode.ACTION))
        assertTrue(CompanionPrompts.isFormatViolation("*我走到楼下*", DialogueMode.WECHAT))
        assertTrue(CompanionPrompts.isFormatViolation("（" + "旁白描写".repeat(5) + "）", DialogueMode.WECHAT))
        assertFalse(CompanionPrompts.isFormatViolation("到啦", DialogueMode.WECHAT))
        assertTrue(CompanionPrompts.modeFormatRule(DialogueMode.PLOT).contains("铁律"))
        assertTrue(CompanionPrompts.modeFormatRule(DialogueMode.PLOT, repair = true).contains("只动写法"))
    }

    // ============ 主动智能编解码 ============

    @Test
    fun proactiveDirectiveIsStrippedFromVisibleText() {
        val (text, sig) = stripProactiveDirective("那我三点找你\n[主动智能] 15:00 | 约好了三点")
        assertEquals("那我三点找你", text)
        assertNotNull(sig)
        assertEquals("约好了三点", sig!!.reason)
        // 同行混写：标记到行尾全是指令，标记前正文保留
        val (t2, s2) = stripProactiveDirective("好嘞 [主动智能] +30m | 待会儿再讲")
        assertEquals("好嘞", t2)
        val delta = s2!!.atMillis!! - System.currentTimeMillis()
        assertTrue("delta=$delta", delta in 29 * 60_000L..31 * 60_000L)
    }

    @Test
    fun proactivePayloadPriorityPrefersScheduleOverCancel() {
        val (_, sig) = stripProactiveDirective("[主动智能] 取消\n[主动智能] +30m | 改个时间")
        assertNotNull(sig)
        assertNotNull(sig!!.atMillis)      // 定时 > 取消
        val (_, cancel) = stripProactiveDirective("[主动智能] 取消 | 他来了")
        assertTrue(cancel!!.cancel)
    }

    @Test
    fun proactiveTimeParsingHandlesRelativeAndAbsolute() {
        val now = 1_791_115_000_000L
        assertEquals(now + 45 * 60_000L, parseProactiveTime("+45m", now))
        assertEquals(now + 2 * 3_600_000L, parseProactiveTime("+2h", now))
        assertNull(parseProactiveTime("45m", now))            // 裸相对时间不认
        assertEquals(now + 24 * 3_600_000L, parseProactiveTime("+48h", now))   // 压到 24h 视界
        // 12 小时制前缀矫正：「下午3:00」= 15:00 不是凌晨 3 点
        val abs = parseProactiveTime("下午3:00", now)!!
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = abs }
        assertEquals(15, cal.get(java.util.Calendar.HOUR_OF_DAY))
    }
}
