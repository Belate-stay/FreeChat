package com.freechat.model

import java.util.UUID

data class GroupMember(val id: String, val persona: CharacterPersona, val sourceCardId: String = "")

data class GroupChatProfile(
    val members: List<GroupMember> = emptyList(),
    val userNickname: String = "我", val announcement: String = "", val announcementAt: Long = 0,
    val avatarPath: String = "", val avatarHash: String = "", val showMemberNames: Boolean = true,
    val languageModelId: String = "", val visionModelId: String = "",
    val replyBufferEnabled: Boolean = true, val replyBufferSeconds: Int = 3,
    val timePerception: Boolean = true, val highQualityMemory: Boolean = false,
    /** 深度推演；模型原生深度思考由本群 PerConvSettings 按模型单独保存。 */
    val deepThinking: Boolean = false, val aiCreativity: Float = 6f,
    /** Explicit settings commits, independent of the last message timestamp. */
    val updatedAt: Long = 0,
)

object GroupChatPolicy {
    const val MAX_MEMBERS = 4

    fun fromCards(cards: List<CharacterCard>): GroupChatProfile {
        require(cards.size in 1..MAX_MEMBERS && cards.all { it.id.isNotBlank() } && cards.map { it.id }.distinct().size == cards.size)
        val group = GroupChatProfile(members = cards.map { GroupMember(UUID.randomUUID().toString(), it.persona.copy(), it.id) })
        require(isValid(group))
        return group
    }

    fun canStart(title: String, group: GroupChatProfile): Boolean = title.isNotBlank() && isValid(group)

    fun canApplyUpdate(current: GroupChatProfile, updated: GroupChatProfile, isReplying: Boolean): Boolean =
        isValid(updated) && (!isReplying || updated.copy(showMemberNames = current.showMemberNames) == current)

    fun isValid(group: GroupChatProfile): Boolean = group.members.size in 1..MAX_MEMBERS &&
        group.members.all { it.id.isNotBlank() && it.persona.name.isNotBlank() } &&
        group.members.map { it.id }.distinct().size == group.members.size
}
