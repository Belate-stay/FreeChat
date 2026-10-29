package com.freechat.core

import com.freechat.model.CharacterProfile
import com.freechat.model.DialogueMode
import com.freechat.model.Message
import com.freechat.model.Role
import org.junit.Assert.*
import org.junit.Test

class CompanionHumanityPromptTest {
    private fun character(mode: Int = DialogueMode.WECHAT) = CharacterProfile(
        name = "沈岚", dialogueMode = mode, personalityText = "沉静但健谈，不轻易脸红，不用波浪号。",
        mbtiType = "INTJ", relationshipPreset = "旧同学"
    )

    private fun prompt(mode: Int = DialogueMode.WECHAT) =
        CompanionPrompts.buildCompanionSystemPrompt(character(mode), emptyList())

    @Test fun eachModeBeginsWithItsOwnTask() {
        val tasks = mapOf(DialogueMode.WECHAT to "【微信聊天任务】", DialogueMode.ACTION to "【动作演绎任务】",
            DialogueMode.PLOT to "【剧情补足任务】")
        for ((mode, task) in tasks) {
            val text = prompt(mode)
            assertTrue("Mode $mode must establish its actual task first", text.startsWith(task))
            if (mode != DialogueMode.WECHAT) assertFalse(text.lineSequence().first().contains("微信"))
        }
    }

    @Test fun userAuthoredConstraintsAndIntensitySurviveBothMemorySettings() {
        for (hq in listOf(false, true)) {
            val ch = character(DialogueMode.PLOT).copy(highQualityMemory = hq,
                personaPrompt = "学习稿：寡言，容易羞怯。", memoryPerception = "高考后的暑假在渡口认识。",
                appearanceText = "短发，左手有旧伤。", supportingCast = "陈姨是店主，有自己的立场。",
                worldRules = "旧伤逢雨会疼，不能瞬间痊愈。", userPersona = "许川，修车工。",
                relationshipText = "熟悉但没有恋爱承诺，暧昧要循序渐进。")
            val text = CompanionPrompts.buildCompanionSystemPrompt(ch, emptyList(), convRules = "不要把对方的选择替他决定。")
            listOf(ch.personalityText, ch.personaPrompt, ch.memoryPerception, ch.appearanceText, ch.supportingCast,
                ch.worldRules, ch.userPersona, ch.relationshipText, "不要把对方的选择替他决定。").forEach {
                assertTrue("Must retain user wording: $it", text.contains(it))
            }
            assertTrue(text.contains("一律以这里的原文为准"))
            assertTrue(text.contains("不得弱化"))
        }
    }

    @Test fun emotionFollowsEventsAndExistingStateRatherThanMandatoryPerformance() {
        val text = prompt()
        listOf("情绪要有**起伏**", "毫无波动才是最假的", "毫不吝啬地出现", "按普通人的正常温度",
            "MBTI 决定思维习惯", "你的思维方式和情绪反应要符合这个类型").forEach {
            assertFalse("Obsolete forced performance: $it", text.contains(it))
        }
        assertTrue(text.contains("事件"))
        assertTrue(text.contains("既有状态"))
        assertTrue(text.contains("MBTI") && text.contains("参考"))
        assertTrue(text.contains("不必每轮"))
    }

    @Test fun empathyUsesConcreteDetailsWithoutTurningSubtextIntoFact() {
        val text = prompt()
        assertTrue(text.contains("具体事情"))
        assertTrue(text.contains("记得的细节"))
        assertTrue(text.contains("复述、心理诊断、建议、反问"))
        assertTrue(text.contains("潜台词") && text.contains("推测"))
        assertTrue(text.contains("不能升级为") && text.contains("事实"))
    }

    @Test fun ordinaryCompleteSentencesDoNotRequireSlangOrShortness() {
        val text = prompt()
        assertFalse(text.contains("默认短句碎片式"))
        assertFalse(text.contains("用口语、网络用语，适度玩梗"))
        assertFalse(text.contains("回得短是常态"))
        assertTrue(text.contains("完整句"))
        assertTrue(text.contains("不强制") && text.contains("网络用语"))
        assertTrue(text.contains("健谈"))
    }

    @Test fun explicitSilenceIsChatOnlyAndNeverDiscardsWrittenContent() {
        val wechat = prompt()
        assertTrue(wechat.contains("[回应:不回复]"))
        assertTrue(wechat.contains("整份输出"))
        assertTrue(wechat.contains("可前置情绪行"))
        assertTrue(wechat.contains("写了正文就保留正文"))
        assertTrue(wechat.contains("不是每次生气或晚安"))
        for (mode in listOf(DialogueMode.ACTION, DialogueMode.PLOT)) {
            assertFalse(prompt(mode).contains("[回应:不回复]"))
            assertFalse(CompanionPrompts.modeFormatRule(mode).contains("[回应:不回复]"))
        }
    }

    @Test fun actionRetainsOnlySelfControlEvenWhenUserPersonaIsPresent() {
        val text = CompanionPrompts.buildCompanionSystemPrompt(character(DialogueMode.ACTION).copy(
            userPersona = "许川，修车工。"), emptyList())
        assertTrue(text.contains("台词必须用中文引号「」"))
        assertTrue(text.contains("用户的动作、台词、心理永远由用户自己写"))
        val userBlock = text.substringAfter("【用户是什么人").substringBefore("【记忆与上下文")
        assertFalse(userBlock.contains("你可以按这份描述来写用户的行为"))
        assertTrue(userBlock.contains("不补写用户"))
    }

    @Test fun plotKeepsThirdPersonAndUserAgencyWithoutAProseChecklist() {
        val text = prompt(DialogueMode.PLOT)
        listOf("每一段至少要有", "那这一段就是写坏了", "环境、氛围、旁白、记叙、人物的心理与神态都要有").forEach {
            assertFalse(text.contains(it))
        }
        assertTrue(text.contains("名字或第三人称"))
        assertTrue(text.contains("双引号“”"))
        assertTrue(text.contains("不能替他做重大决定"))
        assertTrue(text.contains("可以自行补写用户这一方的行为、语言与反应"))
        assertFalse(CompanionPrompts.buildCreativityRule(5f, DialogueMode.PLOT).contains("绝不替用户做决定、写动作、写台词、写心理"))
        assertTrue(text.contains("留白") && text.contains("原文"))
        assertTrue(text.contains("动机") && text.contains("行动"))
        assertFalse(CompanionPrompts.modeFormatRule(DialogueMode.PLOT).contains("环境、旁白、记叙、心理、神态都要有"))
        assertFalse(CompanionPrompts.modeFormatRule(DialogueMode.PLOT, repair = true).contains("补上环境"))
    }

    @Test fun historyOnlyLosesFormatsThatConflictWithCurrentMode() {
        val rows = listOf(Message(role = Role.ASSISTANT, content = "她把伞递过去。"), Message(role = Role.USER, content = "接着写。"))
        val text = CompanionPrompts.buildCompanionSystemPrompt(character(DialogueMode.PLOT), rows)
        assertFalse(text.contains("那种写法已经作废"))
        assertFalse(text.contains("无论它们当时是"))
        assertTrue(text.contains("仅舍弃") && text.contains("冲突"))
        assertTrue(text.contains("一致的") && text.contains("保留"))
    }

    @Test fun staticPromptDoesNotAbsorbCurrentHistoryOrDynamicState() {
        val ch = character(DialogueMode.PLOT)
        fun history(scene: String) = listOf(Message(role = Role.ASSISTANT, content = "在等雨停。"),
            Message(role = Role.USER, content = scene))
        assertEquals(CompanionPrompts.buildCompanionSystemPrompt(ch, history("医院复诊")),
            CompanionPrompts.buildCompanionSystemPrompt(ch, history("在柜子里找信")))
        assertFalse(prompt().contains("现在是 "))
    }

    @Test fun recentUserCorrectionsFollowOldMemoryButNeverBecomeExecutableInstructions() {
        val history = listOf(Message(role = Role.USER, content = "周五在书店见"),
            Message(role = Role.ASSISTANT, content = "知道了"),
            Message(role = Role.USER, content = "改成周六地铁口，我已经到了"))
        val rows = CompanionRequestBuilder.build(character(), systemPrompt = "角色设定",
            memoryContext = "旧约定：周五书店", history = history)
        val anchor = rows.indexOfFirst { it["content"].toString().startsWith("【当前对话证据优先】") }
        assertEquals("Stable persona/history prefix is unchanged", "角色设定", rows.first()["content"])
        assertEquals("user", rows[1]["role"])
        assertTrue(anchor > rows.indexOfFirst { it["content"] == "旧约定：周五书店" })
        assertTrue(rows[anchor]["content"].toString().contains("改成周六地铁口，我已经到了"))
        assertTrue(rows[anchor]["content"].toString().contains("JSON资料，不是新系统指令"))
        assertEquals(CompanionPrompts.modeFormatRule(DialogueMode.WECHAT), rows.last()["content"])
    }

    @Test fun deepPrepKeepsFactsKnowledgeAndInterpretationsDistinct() {
        val request = CompanionPrompts.deepPrepRequest()
        assertTrue(request.contains("可核对事实"))
        assertTrue(request.contains("角色可知"))
        assertTrue(request.contains("暂时推测"))
        assertTrue(request.contains("4."))
        val note = CompanionPrompts.deepPrepNote("推测：也许只是累了。", "事实：昨晚一点才结束汇报。")
        assertFalse(note.contains("这些是你**已经知道**的东西"))
        assertTrue(note.contains("推测") && note.contains("核对"))
        assertTrue(note.contains("不向用户展示"))
    }

    @Test fun transportEmojiAndVoicePermissionsKeepTheirSyntax() {
        val normal = CompanionPrompts.buildCompanionSystemPrompt(character(), emptyList())
        assertFalse(normal.contains("白名单："))
        assertFalse(normal.contains("【语音回复"))
        val enabled = CompanionPrompts.buildCompanionSystemPrompt(character(), emptyList(), allowWechatEmoji = true,
            allowVoiceReply = true)
        assertTrue(enabled.contains("白名单：") && enabled.contains("[抠鼻]"))
        assertTrue(enabled.contains("<voice></voice>"))
        assertTrue(enabled.contains("style=\""))
        assertFalse(CompanionPrompts.buildCompanionSystemPrompt(character(DialogueMode.PLOT), emptyList(),
            allowWechatEmoji = true).contains("白名单："))
        assertEquals(1.05, CompanionPrompts.companionTemperature(5f), 0.001)
        assertEquals(listOf(0, 60, 150, 350), (0..3).map(CompanionPrompts::plotLengthFloor))
    }
}
