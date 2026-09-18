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
    /**
     * 头像内容指纹（16 位十六进制，随角色卡同步 —— 见 `sync/Wire` 的「角色头像」一节）。
     *
     * 一个字段两个身份：
     *  · **线上身份** —— 这是哪一张图。文件在哪儿（绝对路径）出不了这台设备，但"是哪一张脸"必须出得去。
     *  · **本机记账** —— "我这边曾经有过头像"。有它才分得清「本来就没有头像」和「用户把头像删了」：
     *    后者要告诉云端把头像清掉，前者一个字都不能说（否则一台还没下到头像的设备会把云端那张抹了）。
     *
     * 空串 = 从没见过这张脸（老数据、或压根没设过头像）。
     */
    val avatarHash: String = "",
    val mbtiType: String = "",                   // MBTI 类型（如 INTJ）
    val mbtiEI: Float = 0.5f,                    // 0=E(左) ↔ 1=I(右)
    val mbtiNS: Float = 0.5f,                    // 0=N(左) ↔ 1=S(右)
    val mbtiTF: Float = 0.5f,                    // 0=T(左) ↔ 1=F(右)
    val mbtiPJ: Float = 0.5f,                    // 0=P(左) ↔ 1=J(右)
    val personalityPresets: List<String> = emptyList(),  // 性格预设（温柔随和/理性冷静/幽默搞怪/直率犀利）
    val personalityText: String = "",            // 性格补充文字（与预设共同增强适配度）
    val memoryPerception: String = "",           // 记忆感知（用户自身情况/前提故事/旁白/人物关系等）
    val referencePrototype: String = "",         // 参考原型（可选）：网络上已有的知名角色名，用户设定不足时联网搜索作补充
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
    val personaPrompt: String = "",              // AI 深度学习生成的人物专属系统提示词（空=用基础人设块）
    val plotSimulation: Boolean = false,         // 【旧】剧情模式开关：仅作 1.0.28 前的存档迁移输入，新代码勿写入（已由 dialogueMode 取代）
    val dialogueMode: Int = 0,                   // 对话模式：0=微信聊天 1=动作演绎 2=剧情补足（见 DialogueMode）
    val plotLength: Int = 1,                     // 叙事单次回复长度档：0=50字内 1=50-200字 2=200-500字 3=500-1000字
    val sleepSimulation: Boolean = false,        // 模拟作息：AI 有自身作息，睡觉时不回复、醒来后自然解释
    val highQualityMemory: Boolean = false,      // 高质量检索回复：记忆感知原文注入 + 记忆写入原文摘录 + 检索喂原文（关=高精度概括，省 token）
    /**
     * 多轮编排（「深度推演」）：只在 [highQualityMemory] 打开时可选。
     *
     * 开了之后，每轮回话变成两趟：先让模型**以这个角色的身份在心里过一遍**
     * （对方真正在说什么、此刻什么情绪、有哪些相关往事必须用上、这一轮打算怎么应对），
     * 顺手让它报几个"还需要回想的关键词"，拿这几个词**再检索一次记忆**；
     * 然后才带着这份盘算和第二次检索的结果去写正式回复。
     *
     * 代价是每轮多一次 API 调用（延迟大致翻倍、token 明显上升），所以它才藏在
     * 「增强检索」下面当第二级开关 —— 不是默认该开的。
     */
    val deepThinking: Boolean = false,
    val aiCreativity: Float = 5f,                // AI创造力 1.0-10.0（0.1 精度，默认 5）：越高越主动引入新话题/新角色/新剧情；4=旧版手感，>8 允许猎奇反直觉，<3 完全由用户主导
    val proactiveEnabled: Boolean = false,       // 主动智能：TA 会自己在合适的时间主动找你（本地定时唤醒，非服务器推送），由模型判断时机与要不要开口，明显增加 token 消耗
    // ── 模拟设定扩增（1.0.34）：把「这个世界有谁、按什么规矩运转、用户是个什么人」交给用户 ──
    val supportingCast: String = "",             // 配角：这个世界里的其他人（关系/基本信息/前言故事），一个输入框装全部，可以写很多人
    val worldRules: String = "",                 // 规则：世界观与故事框架（等级体系/门派/势力/硬性设定）+ 人物习惯、环境、条件
    val userPersona: String = ""                 // 用户形象：用户自己的性别/身份/家庭条件/角色背景（AI 扮演用户时的依据）
) {
    companion object {
        /**
         * 新建角色时的 AI创造力默认值（1.0.64 起 6.0，之前是 5.0）。
         *
         * ⚠ 注意它和上面 `aiCreativity` 那个**字段默认值**不是一回事，后者故意留在 5f：
         * 老角色档案里没有这个键的（这个选项出现之前建的档），Gson 读出来就是字段默认值 ——
         * 那是它们创建当年的默认值，也是用户一直看到的值。把字段默认值一起改成 6f，
         * 等于顺手把这些老角色也改了，那不是这次要干的事。
         * 「新建时用几」只由这个常量决定，落点在 [com.freechat.ui.screens.CharacterSetupScreen]。
         */
        const val DEFAULT_AI_CREATIVITY = 6f
    }

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

    /**
     * 迁移旧字段到新字段（幂等：旧数据升级后不丢形象图/开场白/对话模式）。
     * 剧情模式（plotSimulation=true）在 1.0.28 前就是「小说文本流 + 旁白 + 不限角色」，
     * 语义与新的「剧情补足」完全一致，故直接迁移到该档。
     */
    fun normalized(): CharacterProfile = copy(
        appearanceImagePaths = if (appearanceImagePaths.isEmpty() && appearanceImagePath.isNotBlank())
            listOf(appearanceImagePath) else appearanceImagePaths,
        appearanceImageDescs = if (appearanceImageDescs.isEmpty() && appearanceImageDesc.isNotBlank())
            listOf(appearanceImageDesc) else appearanceImageDescs,
        openingLines = if (openingLines.isEmpty() && openingLine.isNotBlank())
            listOf(openingLine) else openingLines,
        // 老档案没有 dialogueMode 键（Gson 落默认 0），靠 plotSimulation 补上真正的档位。
        // 保存时 buildProfile 会同步写回 plotSimulation，故迁移不会反复触发。
        dialogueMode = when {
            dialogueMode !in 0..DialogueMode.MAX -> if (plotSimulation) DialogueMode.PLOT else DialogueMode.WECHAT
            plotSimulation && dialogueMode == DialogueMode.WECHAT -> DialogueMode.PLOT
            else -> dialogueMode
        }
    )
}

/**
 * 对话模式（角色级）：决定 AI 回复的形态。取代 1.0.28 前的 plotSimulation 布尔开关——
 * 那个开关只区分「微信聊天 / 剧情」，而剧情那一档实际混着两种诉求（只演自己 vs 写整个场景）。
 */
object DialogueMode {
    /** 微信聊天：像真人发微信，逐条回复、带情绪标签，可有回复缓冲与作息模拟 */
    const val WECHAT = 0
    /** 动作演绎：只写该角色的语言/动作/神态/心理，不写其他角色、不写旁白与剧情补充 */
    const val ACTION = 1
    /** 剧情补足：可写旁白、环境、其他角色，像小说正文一样的文本流 */
    const val PLOT = 2
    const val MAX = PLOT
}

/** 是否为叙事形态（动作演绎 / 剧情补足）：两者都不是微信消息流，回复是整段文本而非逐条 */
fun CharacterProfile.isNarrativeMode(): Boolean = dialogueMode != DialogueMode.WECHAT

/** 是否为「剧情补足」：唯一允许旁白与其他角色出场的形态（主角之外的角色只在这一档被展开写） */
fun CharacterProfile.isPlotMode(): Boolean = dialogueMode == DialogueMode.PLOT

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

// 亲疏尺度不再写死：没有亲密度数值、没有按等级映射的关系模板，由 AI 结合人物设定与上下文自行判断（见 ChatViewModel.buildCompanionSystemPrompt 的关系块）

/** 参考原型生效门槛：用户自己的人设文字少于此长度时，才联网搜索参考原型作重点补全；否则仅作细节补充 */
const val PROTOTYPE_SPARSE_THRESHOLD = 120

/** 「参考原型」需要联网搜索的条件：填了原型名，且用户自己写的人设文字寥寥无几 */
fun CharacterProfile.prototypeNeedsSearch(): Boolean =
    referencePrototype.isNotBlank() && personaTextLength() < PROTOTYPE_SPARSE_THRESHOLD

/**
 * 用户手写的「人物设定」类文字总长度，用于判断设定是否足够详实（够详实就不联网搜参考原型）。
 * 计入性格补充/记忆感知/人物形象/性格预设 + 人物关系的补充细节：
 * 关系细节也是用户手写的设定文字，写得多就说明用户想以自己的版本为准。
 * 不计开场白（那是台词，不描述这个人是谁）。
 */
fun CharacterProfile.personaTextLength(): Int =
    personalityText.trim().length + memoryPerception.trim().length +
        appearanceText.trim().length + personalityPresets.sumOf { it.length } +
        relationshipText.trim().length +
        // 1.0.34 新增的三块同样是用户手写的设定，写得多就说明用户想以自己的版本为准
        supportingCast.trim().length + worldRules.trim().length + userPersona.trim().length

/**
 * 角色导出文件：只含「人物设定」里的文字设定。
 * 头像/形象参考图等本地文件不导出；AI 生成的人设提示词 personaPrompt 随角色设定导出（导入后原样还原、不重新学习）。
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
    val referencePrototype: String = "",
    val appearanceText: String = "",
    val relationshipPreset: String = "",
    val relationshipText: String = "",
    val openingLines: List<String> = emptyList(),
    val personaPrompt: String = "",
    // 1.0.34 新增：老导出文件没有这三个键 → Gson 落空串，导入后就是「没填」，符合预期
    val supportingCast: String = "",
    val worldRules: String = "",
    val userPersona: String = ""
) {
    fun toProfile(): CharacterProfile = CharacterProfile(
        name = name, gender = gender, age = age,
        mbtiType = mbtiType, mbtiEI = mbtiEI, mbtiNS = mbtiNS, mbtiTF = mbtiTF, mbtiPJ = mbtiPJ,
        personalityPresets = personalityPresets,
        personalityText = personalityText,
        memoryPerception = memoryPerception,
        referencePrototype = referencePrototype,
        appearanceText = appearanceText,
        relationshipPreset = relationshipPreset,
        relationshipText = relationshipText,
        openingLines = openingLines,
        personaPrompt = personaPrompt,
        supportingCast = supportingCast,
        worldRules = worldRules,
        userPersona = userPersona
    )
}

/** 从角色档案导出「人物设定」文字设定 */
fun CharacterProfile.toExport(): CharacterExport = CharacterExport(
    name = name, gender = gender, age = age,
    mbtiType = mbtiType, mbtiEI = mbtiEI, mbtiNS = mbtiNS, mbtiTF = mbtiTF, mbtiPJ = mbtiPJ,
    personalityPresets = personalityPresets,
    personalityText = personalityText,
    memoryPerception = memoryPerception,
    referencePrototype = referencePrototype,
    appearanceText = appearanceText,
    relationshipPreset = relationshipPreset,
    relationshipText = relationshipText,
    openingLines = openingLines,
    personaPrompt = personaPrompt,
    supportingCast = supportingCast,
    worldRules = worldRules,
    userPersona = userPersona
)
