package com.freechat.ui.components

data class ChatViewportPosition(val index: Int, val offset: Int, val itemKey: String? = null) {
    fun resolveIndex(keys: List<String>): Int = keys.indexOf(itemKey).takeIf { it >= 0 }
        ?: index.coerceIn(0, keys.lastIndex.coerceAtLeast(0))
}

/** Owned by the chat ViewModel, never saved to disk or the Android saved-state registry. */
class ChatSessionState {
    private val viewports = mutableMapOf<String, ChatViewportPosition>()
    private val reasoning = mutableMapOf<Pair<String, String>, Boolean>()

    fun viewport(conversationId: String) = viewports[conversationId]
    fun saveViewport(conversationId: String, position: ChatViewportPosition) {
        viewports[conversationId] = position
    }

    fun reasoningExpanded(conversationId: String, messageId: String) =
        reasoning[conversationId to messageId] ?: true

    /** 只读「有没有手动记录」（null=没动过）——首帧定默认态用它，别用 ?:true 的兜底版（会把默认值和记录混在一起）。 */
    fun reasoningExpandedRecord(conversationId: String, messageId: String): Boolean? =
        reasoning[conversationId to messageId]

    fun setReasoningExpanded(conversationId: String, messageId: String, expanded: Boolean) {
        reasoning[conversationId to messageId] = expanded
    }
}
