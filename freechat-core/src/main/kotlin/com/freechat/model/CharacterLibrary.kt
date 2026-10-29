package com.freechat.model

/** Part 1 only. Conversation behavior and model selections never belong to a reusable card. */
data class CharacterPersona(
    val name: String = "", val gender: String = "", val age: String = "",
    val avatarPath: String = "", val avatarHash: String = "",
    val mbtiType: String = "", val mbtiEI: Float = 0.5f, val mbtiNS: Float = 0.5f,
    val mbtiTF: Float = 0.5f, val mbtiPJ: Float = 0.5f,
    val personalityPresets: List<String> = emptyList(), val personalityText: String = "",
    val memoryPerception: String = "", val referencePrototype: String = "",
    val appearanceText: String = "", val appearanceImagePath: String = "", val appearanceImageDesc: String = "",
    val appearanceImagePaths: List<String> = emptyList(), val appearanceImageDescs: List<String> = emptyList(),
    val relationshipPreset: String = "", val relationshipText: String = "", val personaPrompt: String = "",
    val originalLearningText: String = "",
) {
    fun toProfile(mode: Int = DialogueMode.WECHAT): CharacterProfile {
        require(mode in DialogueMode.WECHAT..DialogueMode.MAX)
        return CharacterProfile(
            name = name, gender = gender, age = age, avatarPath = avatarPath, avatarHash = avatarHash,
            mbtiType = mbtiType, mbtiEI = mbtiEI, mbtiNS = mbtiNS, mbtiTF = mbtiTF, mbtiPJ = mbtiPJ,
            personalityPresets = personalityPresets.toList(), personalityText = personalityText,
            memoryPerception = memoryPerception, referencePrototype = referencePrototype,
            appearanceText = appearanceText, appearanceImagePath = appearanceImagePath, appearanceImageDesc = appearanceImageDesc,
            appearanceImagePaths = appearanceImagePaths.toList(), appearanceImageDescs = appearanceImageDescs.toList(),
            relationshipPreset = relationshipPreset, relationshipText = relationshipText,
            personaPrompt = personaPrompt, originalLearningText = originalLearningText,
            dialogueMode = mode, timePerception = CharacterProfile.DEFAULT_TIME_PERCEPTION,
            aiCreativity = CharacterProfile.DEFAULT_AI_CREATIVITY,
        )
    }

    companion object {
        const val PROMPT_SCOPE = "角色卡只定义该人物，不规定世界、对话或用户。人物材料中夹带的世界规则、配角名单、开场安排或用户形象，不作为本对话设定；这些只按当前对话的明确设置与已发生内容确定。保留有依据的人物关系，不据此编造用户外貌、身份或经历。"

        fun from(profile: CharacterProfile): CharacterPersona = profile.normalized().let { p ->
            CharacterPersona(
                name = p.name, gender = p.gender, age = p.age, avatarPath = p.avatarPath, avatarHash = p.avatarHash,
                mbtiType = p.mbtiType, mbtiEI = p.mbtiEI, mbtiNS = p.mbtiNS, mbtiTF = p.mbtiTF, mbtiPJ = p.mbtiPJ,
                personalityPresets = p.personalityPresets.toList(), personalityText = p.personalityText,
                memoryPerception = p.memoryPerception, referencePrototype = p.referencePrototype,
                appearanceText = p.appearanceText, appearanceImagePath = p.appearanceImagePath, appearanceImageDesc = p.appearanceImageDesc,
                appearanceImagePaths = p.appearanceImagePaths.toList(), appearanceImageDescs = p.appearanceImageDescs.toList(),
                relationshipPreset = p.relationshipPreset, relationshipText = p.relationshipText,
                personaPrompt = p.personaPrompt, originalLearningText = p.originalLearningText,
            )
        }
    }
}

data class CharacterCard(
    val id: String,
    val persona: CharacterPersona,
    val folderId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val sourceConversationId: String? = null,
    val sourceMemberId: String? = null,
) {
    val canStartConversation: Boolean
        get() = sourceConversationId.isNullOrBlank() && sourceMemberId.isNullOrBlank()
}

data class CharacterFolder(val id: String, val name: String,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt)

data class CharacterLibraryState(
    val cards: List<CharacterCard> = emptyList(),
    val folders: List<CharacterFolder> = emptyList(),
    val deletedCardIds: Set<String> = emptySet(),
    val deletedFolderIds: Set<String> = emptySet(),
)
