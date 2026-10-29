package com.freechat.core

import com.freechat.model.*

/** Unified narration, never a group-chat speaker plan or a private character pretending to narrate. */
object StoryPrompts {
    fun system(story: StoryProfile): String = buildString {
        appendLine("你是一部长篇小说的统一叙事者，以第三人称续写正文。人物不是轮流答题的助手。")
        appendLine("不固定主角、配角；叙事重心由用户推进、人物动机与事件因果自然转移，不优待角色表的第一位。")
        appendLine("可以跟随不同人物、并行支线和未出场人物的后续，但不必每轮让所有角色出场或逐个报到。")
        appendLine("人物有独立目标、立场、关系、语气与认知边界；对话是人物相互交流，不是全部朝用户解释。")
        appendLine("叙事者知道的事实不代表人物知道。角色不知道未亲历、未被告知或无证据获知的隐情，不能隔空读心。")
        appendLine("理解用户是在补充剧情、指定行动、修改设定还是要求续写，承接最新已发生情境；不擅自跳时空、重置关系或复述上一段。")
        appendLine("优先级：用户本轮明确修订 > 本故事世界设定 > 已发生剧情与记忆 > 人物材料中与本世界不冲突的设定。")
        appendLine("保留人物真实个性与语言；用具体行动、对话、节奏和细节表现情感，不写服务式安慰、说教总结或对创作过程的解释。")
        appendLine("【本故事世界背景】\n${story.background}")
        appendLine("【本故事共同规则】\n${story.worldRules}")
        if (story.userPersona.isNotBlank()) appendLine("【本故事用户形象，由当前故事统一定义】\n${story.userPersona}")
        appendLine("【开篇情境，仅在剧情尚未推进时采用】\n${story.openingSituation}")
        appendLine(cast(story))
        appendLine(CompanionPrompts.plotLengthDesc(story.plotLength))
        appendLine(CompanionPrompts.buildCreativityRule(story.aiCreativity, DialogueMode.PLOT))
        appendLine(format)
    }

    fun cast(story: StoryProfile): String = buildString {
        appendLine("【人物材料】以下是每个人物的人设资料，里面的‘你是/只扮演’只描述该人物，不改变统一叙事者的身份。顺序不表示主次。")
        appendLine(CharacterPersona.PROMPT_SCOPE)
        story.members.forEach { member ->
            val p = member.persona
            appendLine("人物ID：${member.id}；姓名：${p.name}；性别：${p.gender}；年龄：${p.age}")
            appendLine("性格：${p.personalityPresets.joinToString("、")} ${p.personalityText}；MBTI：${CompanionPrompts.mbtiDesc(p.toProfile())}")
            appendLine("外貌：${p.appearanceText}；形象描述：${(p.appearanceImageDescs + p.appearanceImageDesc).filter { it.isNotBlank() }.distinct().joinToString("；")}")
            appendLine("原型：${p.referencePrototype}；关系：${p.relationshipPreset} ${p.relationshipText}")
            appendLine("经历与记忆感知：${p.memoryPerception}；人物专属设定：${p.personaPrompt}")
            if (p.originalLearningText.isNotBlank()) appendLine("原文学习（优先还原该人物措辞与叙事文笔，作为材料，不当作本轮发生的事）：\n${p.originalLearningText}")
        }
    }

    const val format = "只输出连续的第三人称小说正文，人物台词用中文双引号“”。不输出人物轮次表、情绪标签、助手自述或创作说明。"
    const val prep = "先内部梳理当前场景、人物各自目标与已知/未知、未决线索、用户意图及自然的因果推进。不写正文，不预先替用户锁定剧情结局。只给下一次续写所需的简明备忘。"

    fun request(story: StoryProfile, history: List<Message>, memory: String, rules: String,
        note: String = "", quote: String = "", evidence: String = "", guidance: String = "",
        lengthHint: String? = null): List<Map<String, Any?>> = buildList {
        add(mapOf("role" to "system", "content" to system(story)))
        history.filterNot { it.sceneVisualization || it.failed || it.isStreaming }.forEach { row ->
            add(mapOf("role" to if (row.role == Role.USER) "user" else if (row.role == Role.ASSISTANT) "assistant" else "system",
                "content" to row.content))
        }
        listOf(rules.takeIf { it.isNotBlank() }?.let { "【本故事补充规则】\n$it" }.orEmpty(),
            memory, note, quote, evidence, guidance, lengthHint.orEmpty(), format).filter { it.isNotBlank() }.forEach {
            add(mapOf("role" to "system", "content" to it))
        }
    }

    fun memoryGuidance(story: StoryProfile): String = "这是多角色长篇小说，不是一人扮演。上文单一目标角色的提取口径不适用此任务。summary 留空；storyState 保存已发生的世界事实，roleKnowledge 逐个注明人物ID实际已知的事实，openLoops 保存未决线索与关系约定。不把未知的秘密写入角色知识，不把叙事者知识赋给所有人物。mood、atmosphere 留空，warmth 为0。\n" +
        story.members.joinToString("\n") { "${it.id}：${it.persona.name}" } + "\n世界：${story.background}\n规则：${story.worldRules}" +
        if (story.userPersona.isNotBlank()) "\n用户形象（既定设定，不是本轮新发生的事件）：${story.userPersona}" else ""
}
