package com.freechat.data

import com.freechat.model.CharacterProfile
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.freechat.model.Role

/** Only text/known facts go into the picture. It is never a new event in the story. */
object SceneImagePrompt {
    fun build(character: CharacterProfile, history: List<Message>, memories: List<MemoryEntry>,
        globalMemories: List<String> = emptyList(), conversationRules: String = "",
        characterReferenceCount: Int = 0, sceneReferenceCount: Int = 0): String = buildString {
        appendLine("你是场景视觉化模型。根据下列人物档案、记忆和按时间顺序的剧情原文，直接生成一张当前场景图。")
        appendLine("只还原最后一段已经发生的情境，不延续或改写剧情，不将早期事件误当成当前场景。")
        appendLine("人物年龄、性别、面容、发色、服饰、关系、动作、所在环境必须与设定和最新原文一致。")
        appendLine("未说明的镜头构图可合理补足，但不要改变已知事实，不画对话界面、文字水印或拼接时间线。")
        appendLine("根据当前情境、人物设定与参考图，自主选择合适的画幅比例、分辨率和视觉风格；人物或用户明确要求的风格优先，不套用固定预设。")
        // 图片预设（1.1.26.6）：用户声明的画幅/主体随提示词一并送达；模型照不照做不管
        imagePresetPrompts(character).forEach { appendLine(it) }
        if (character.enhancedSceneContinuity) {
            appendLine("【增强同元延续】最新剧情文字和人物设定优先于较早场景参考图。较早场景图只辅助服装、配饰和道具的视觉连续性，不构成剧情事实或记忆。")
            appendLine("换装、增减道具、位置和动作变化必须按最新文字更新；不要照搬旧图中的姿态、地点或已经改变的物件。未被文字改变的视觉细节可延续。")
            if (sceneReferenceCount > 0) {
                appendLine("本次参考图按顺序排列：前 $characterReferenceCount 张为人物形象参考，后 $sceneReferenceCount 张为最近保留的场景参考；场景图由新到旧。")
            }
        }
        appendLine("【完整人物与世界设定】")
        appendLine("角色：${character.name}；性别：${character.gender}；年龄：${character.age}；原型：${character.referencePrototype}")
        appendLine("MBTI：${character.mbtiType}；性格：${character.personalityPresets.joinToString("、")} ${character.personalityText}")
        appendLine("外貌：${character.appearanceText}")
        (character.appearanceImageDescs + listOf(character.appearanceImageDesc)).filter { it.isNotBlank() }.distinct()
            .forEach { appendLine("形象参考描述：$it") }
        appendLine("关系：${character.relationshipPreset} ${character.relationshipText}")
        appendLine("前提故事与记忆感知：${character.memoryPerception}")
        appendLine("人物专属设定：${character.personaPrompt}")
        appendLine("配角：${character.supportingCast}")
        appendLine("世界规则：${character.worldRules}")
        appendLine("用户形象：${character.userPersona}")
        appendLine("开场设定：${character.openingLines.joinToString("\n")}")
        CharacterOriginalLearning.prompt(character, CharacterOriginalLearning.Purpose.IMAGE)
            .takeIf { it.isNotBlank() }?.let { appendLine(it) }
        if (conversationRules.isNotBlank()) appendLine("对话规则：$conversationRules")
        if (globalMemories.isNotEmpty()) {
            appendLine("【用户设定的全局记忆】")
            globalMemories.forEach { appendLine(it) }
        }
        appendLine("【对话记忆】")
        memories.sortedBy { it.timestamp }.forEach { appendLine("${it.eventDate} ${it.summary}") }
        appendLine("【剧情原文，末尾即当前场景】")
        history.filterNot { it.sceneVisualization || it.failed || it.isStreaming }.forEach { m ->
            appendLine("${if (m.role == Role.USER) "用户" else character.name}：${m.content}")
            m.imageContext?.takeIf { it.isNotBlank() }?.let { appendLine("用户图片内容：$it") }
        }
        appendLine("请将以上最后一个明确场景视觉化。生成图片本身不属于剧情，也不造成任何后续事件。")
    }

    /**
     * 图片预设（1.1.26.6）→ 提示词。用户在模拟设置里声明的画幅/主体，每次生成场景图随提示词
     * 一并发给生图模型（「仅作为提示词上传，实际效果由模型决定」——照不照做不管）。
     * 无声明（0）不写入任何内容。文案按工单给的意思展开成更完整的构图指令。
     */
    fun imagePresetPrompts(character: CharacterProfile): List<String> {
        val out = mutableListOf<String>()
        when (character.imagePresetAspect) {
            1 -> out += "【画幅声明】画面采用 1:1 方形画幅，四边均衡、主体居中，适合对称构图与特写。"
            2 -> out += "【画幅声明】画面采用 4:3 横向画幅（横幅），视野开阔，适合容纳环境全貌、双人并肩与场景氛围铺陈。"
            3 -> out += "【画幅声明】画面采用 4:3 纵向画幅（竖幅），上下空间充裕，适合人物全身立绘与高耸、纵深的背景。"
            4 -> out += "【画幅声明】画面采用 3:2 横向画幅（横幅），经典电影感比例，适合叙事性场景与横向动作展开。"
            5 -> out += "【画幅声明】画面采用 3:2 纵向画幅（竖幅），挺拔修长，适合突出人物主体的姿态与纵向延伸的背景。"
        }
        when (character.imagePresetSubject) {
            1 -> out += "【主体声明】画面还原整个场景：优先呈现环境全貌与多角色融合，主角以全身姿态融入场景，" +
                "各角色与环境元素完整均衡，不裁掉关键人物与场景细节。"
            2 -> out += "【主体声明】画面以主角个人特写为主：聚焦主角本人的神态、动作与情绪刻画，以中近景为主，" +
                "背景虚化或简化处理，其余角色与环境退居次要。"
        }
        return out
    }
}
