package com.freechat.model

import kotlin.math.abs

/**
 * 拟人陪伴模式的人物档案。
 * 角色名必填；性别/年龄/MBTI/性格/记忆感知共同塑造 AI 人物的心理思维逻辑与背景。
 */
data class CharacterProfile(
    val name: String = "",                       // 角色名称（必填）
    val gender: String = "",                     // 性别（男/女/空=未设置）
    val age: String = "",                        // 年龄（如 "25"，空=未设置）
    val avatarPath: String = "",                 // 头像本地路径（空=默认灰色小人）
    val mbtiType: String = "",                   // MBTI 类型（如 INTJ）
    val mbtiEI: Float = 0.5f,                    // 0=E(左) ↔ 1=I(右)
    val mbtiNS: Float = 0.5f,                    // 0=N(左) ↔ 1=S(右)
    val mbtiTF: Float = 0.5f,                    // 0=T(左) ↔ 1=F(右)
    val mbtiPJ: Float = 0.5f,                    // 0=P(左) ↔ 1=J(右)
    val personalityPresets: List<String> = emptyList(),  // 性格预设（温柔随和/理性冷静/幽默搞怪/直率犀利）
    val personalityText: String = "",            // 性格补充文字（与预设共同增强适配度）
    val memoryPerception: String = "",           // 记忆感知（用户自身情况/前提故事/旁白/人物关系等）
    val languageModelId: String = "",            // 每角色独立语言模型（空=跟随全局）
    val visionModelId: String = "",              // 每角色独立识图模型（空=跟随全局）
    val enableWebSearch: Boolean? = null,        // 每角色独立联网搜索（null=跟随全局）
    val appearanceText: String = "",             // 人物形象文字描述（身材/体型/外貌等）
    val appearanceImagePath: String = "",        // 【旧】人物形象参考图单路径（兼容 1.540 前数据，新代码勿写入）
    val appearanceImageDesc: String = "",        // 【旧】参考图识图结果（兼容 1.540 前数据）
    val appearanceImagePaths: List<String> = emptyList(),  // 人物形象参考图（最多 3 张）
    val appearanceImageDescs: List<String> = emptyList(),  // 每张参考图的识图结果（与 paths 一一对应）
    val replyBufferSeconds: Int = 3,             // 回复缓冲时长（秒，1-6）：连发消息后等这个窗口再统一回复
    val replyBufferEnabled: Boolean = true,      // 回复缓冲开关：关闭则无缓冲，发送即开始思考回复
    val openingLine: String = "",                // 【旧】开场白单条（兼容 1.540 前数据，新代码勿写入）
    val openingLines: List<String> = emptyList(), // 开场白多条（AI 逐条带延迟自然发出）
    val relationshipPreset: String = "",         // 人物关系预设（女朋友/男朋友/助理/同学/同事…，仅创建时选择）
    val relationshipText: String = "",           // 人物关系自定义补充文字（AI 学习理解后融入形象与记忆）
    val intimacy: Int = 0,                       // 亲密度 0-100（初始按预设映射，随聊天剧情动态演进）
    val relationshipStage: String = "",          // 当前关系阶段（初始=预设，随剧情演变：同学→暧昧→情侣→前任/朋友）
    val personaPrompt: String = "",              // AI 深度学习生成的人物专属系统提示词（空=用基础人设块）
    val plotSimulation: Boolean = false,         // 剧情模拟：开启后对话变剧情文本（环境/心理/语言/动作/旁白描写）
    val plotLength: Int = 1,                     // 剧情单次回复长度档：0=50字内 1=50-200字 2=200-500字 3=500-1000字
    val sleepSimulation: Boolean = false,        // 模拟作息：AI 有自身作息，睡觉时不回复、醒来后自然解释
    val highQualityMemory: Boolean = false       // 高质量检索回复：记忆感知原文注入 + 记忆写入原文摘录 + 检索喂原文（关=高精度概括，省 token）
) {
    /** 由四个维度倾向推导出 MBTI 类型字母；任一维度处于中间态（未选择）则整体视为「未设置」，返回空串 */
    fun deriveMbtiType(): String {
        if (listOf(mbtiEI, mbtiNS, mbtiTF, mbtiPJ).any { mbtiDimensionCentered(it) }) return ""
        return buildString {
            append(if (mbtiEI < 0.5f) 'E' else 'I')
            append(if (mbtiNS < 0.5f) 'N' else 'S')
            append(if (mbtiTF < 0.5f) 'T' else 'F')
            append(if (mbtiPJ < 0.5f) 'P' else 'J')
        }
    }

    /** 已选择的维度数量（非中间态） */
    fun mbtiSelectedCount(): Int =
        listOf(mbtiEI, mbtiNS, mbtiTF, mbtiPJ).count { !mbtiDimensionCentered(it) }

    /** 展示用四格字符串：每个维度按滑块位置显示对应字母，中间态显示 '-' */
    fun mbtiDisplay(): String = buildString {
        append(mbtiDimensionLetter(mbtiEI, 'E', 'I'))
        append(mbtiDimensionLetter(mbtiNS, 'N', 'S'))
        append(mbtiDimensionLetter(mbtiTF, 'T', 'F'))
        append(mbtiDimensionLetter(mbtiPJ, 'P', 'J'))
    }

    /** 迁移旧单值字段到新列表字段（幂等：旧数据升级后不丢形象图/开场白） */
    fun normalized(): CharacterProfile = copy(
        appearanceImagePaths = if (appearanceImagePaths.isEmpty() && appearanceImagePath.isNotBlank())
            listOf(appearanceImagePath) else appearanceImagePaths,
        appearanceImageDescs = if (appearanceImageDescs.isEmpty() && appearanceImageDesc.isNotBlank())
            listOf(appearanceImageDesc) else appearanceImageDescs,
        openingLines = if (openingLines.isEmpty() && openingLine.isNotBlank())
            listOf(openingLine) else openingLines
    )
}

/** MBTI 滑块「未选择」判定阈值：离中心 0.5 小于该值视为中间态（显示占位符、不算已选），避免手滑点一下就锁死字母 */
const val MBTI_CENTER_DEAD_ZONE = 0.06f

/** 判断某个维度是否处于「未选择」的中间态 */
fun mbtiDimensionCentered(v: Float): Boolean = abs(v - 0.5f) < MBTI_CENTER_DEAD_ZONE

/** 单维度字母：中间态返回 '-'，否则按 <0.5 取左字母、>=0.5 取右字母 */
fun mbtiDimensionLetter(v: Float, left: Char, right: Char): Char =
    if (mbtiDimensionCentered(v)) '-' else if (v < 0.5f) left else right

/** MBTI 预设类型 → 四维倾向强度（0=左字母，1=右字母；选中类型后联动滑块） */
val MBTI_PRESETS: Map<String, List<Float>> = mapOf(
    "INTJ" to listOf(0.75f, 0.25f, 0.25f, 0.75f),
    "INTP" to listOf(0.75f, 0.25f, 0.25f, 0.25f),
    "ENTJ" to listOf(0.25f, 0.25f, 0.25f, 0.75f),
    "ENTP" to listOf(0.25f, 0.25f, 0.25f, 0.25f),
    "INFJ" to listOf(0.75f, 0.25f, 0.75f, 0.75f),
    "INFP" to listOf(0.75f, 0.25f, 0.75f, 0.25f),
    "ENFJ" to listOf(0.25f, 0.25f, 0.75f, 0.75f),
    "ENFP" to listOf(0.25f, 0.25f, 0.75f, 0.25f),
    "ISTJ" to listOf(0.75f, 0.75f, 0.25f, 0.75f),
    "ISFJ" to listOf(0.75f, 0.75f, 0.75f, 0.75f),
    "ESTJ" to listOf(0.25f, 0.75f, 0.25f, 0.75f),
    "ESFJ" to listOf(0.25f, 0.75f, 0.75f, 0.75f),
    "ISTP" to listOf(0.75f, 0.75f, 0.25f, 0.25f),
    "ISFP" to listOf(0.75f, 0.75f, 0.75f, 0.25f),
    "ESTP" to listOf(0.25f, 0.75f, 0.25f, 0.25f),
    "ESFP" to listOf(0.25f, 0.75f, 0.75f, 0.25f)
)

/** 性格预设选项 */
val PERSONALITY_PRESETS = listOf("温柔随和", "理性冷静", "幽默搞怪", "直率犀利")

/** 人物关系预设（仅作大致方向参考，实际关系随聊天剧情动态演进，不可一概而论） */
val RELATIONSHIP_PRESETS = listOf(
    "女朋友", "男朋友", "闺蜜", "朋友", "同学", "同事", "网友", "助理", "老师", "学生", "兄弟", "陌生人"
)

// 亲密度不再写死映射：初始值由 AI 在生成人设时结合关系描述灵活判断（见 ChatViewModel.assessInitialIntimacy）

/**
 * 角色导出文件：只含「人物设定」里的文字设定。
 * 头像/形象参考图等本地文件、亲密度/关系阶段等运行时状态不导出；AI 生成的人设提示词 personaPrompt 随角色设定导出（导入后原样还原、不重新学习）。
 */
data class CharacterExport(
    val version: Int = 1,
    val name: String = "",
    val gender: String = "",
    val age: String = "",
    val mbtiType: String = "",
    val mbtiEI: Float = 0.5f,
    val mbtiNS: Float = 0.5f,
    val mbtiTF: Float = 0.5f,
    val mbtiPJ: Float = 0.5f,
    val personalityPresets: List<String> = emptyList(),
    val personalityText: String = "",
    val memoryPerception: String = "",
    val appearanceText: String = "",
    val relationshipPreset: String = "",
    val relationshipText: String = "",
    val openingLines: List<String> = emptyList(),
    val personaPrompt: String = ""
) {
    fun toProfile(): CharacterProfile = CharacterProfile(
        name = name, gender = gender, age = age,
        mbtiType = mbtiType, mbtiEI = mbtiEI, mbtiNS = mbtiNS, mbtiTF = mbtiTF, mbtiPJ = mbtiPJ,
        personalityPresets = personalityPresets,
        personalityText = personalityText,
        memoryPerception = memoryPerception,
        appearanceText = appearanceText,
        relationshipPreset = relationshipPreset,
        relationshipText = relationshipText,
        openingLines = openingLines,
        personaPrompt = personaPrompt
    )
}

/** 从角色档案导出「人物设定」文字设定 */
fun CharacterProfile.toExport(): CharacterExport = CharacterExport(
    name = name, gender = gender, age = age,
    mbtiType = mbtiType, mbtiEI = mbtiEI, mbtiNS = mbtiNS, mbtiTF = mbtiTF, mbtiPJ = mbtiPJ,
    personalityPresets = personalityPresets,
    personalityText = personalityText,
    memoryPerception = memoryPerception,
    appearanceText = appearanceText,
    relationshipPreset = relationshipPreset,
    relationshipText = relationshipText,
    openingLines = openingLines,
    personaPrompt = personaPrompt
)
