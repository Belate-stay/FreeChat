package com.freechat.model

/** 进入多选时点的那个动作 —— 决定标题右侧那个文字按钮上写什么、点下去干什么。 */
enum class MultiSelectAction { SHARE, DELETE, FAVORITE }

/**
 * 多选态。放在 model 包（而不是 ui.components）：
 * ChatBubble（ui.components）、ChatScreen（ui.screens）、ChatViewModel（viewmodel）三边都要用，
 * 放 ui 会造成 viewmodel → ui 的反向依赖。
 *
 * @param active 是否处于多选态
 * @param action 进入时的动作（SHARE / DELETE / FAVORITE）
 * @param selectedIds 已勾选消息的 id（配对勾选，问与答一起进）
 */
data class MultiSelectState(
    val active: Boolean = false,
    val action: MultiSelectAction? = null,
    val selectedIds: Set<String> = emptySet()
)
