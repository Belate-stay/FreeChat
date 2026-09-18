package com.freechat.util

import com.freechat.model.Message
import com.freechat.model.Role

/**
 * 返回 [index] 所属「一整轮对话」的全部下标（升序，含自身）。
 *
 * 判定与 ChatViewModel 里既有的成对删除（`deleteMessagePair`）**严格等价**，抽出来是为了让
 * 「勾选」「删除」「收藏」「分享」四处共用同一套配对规则，不会出现「删的是两条、勾的是一条」这种偏差：
 *  - USER：向前合并连续 USER（图片在前、文字在后的那种），再向后吸收紧跟的一条 ASSISTANT；
 *  - 其它（ASSISTANT / SYSTEM）：向前吸收连续 USER，再加自身；
 *  - 越界返回空列表。
 */
fun pairedIndices(messages: List<Message>, index: Int): List<Int> {
    if (index !in messages.indices) return emptyList()
    val out = sortedSetOf<Int>()
    if (messages[index].role == Role.USER) {
        // 向前合并连续的用户消息（图片在前、文本在后，同属一轮）
        var cursor = index
        while (cursor - 1 >= 0 && messages[cursor - 1].role == Role.USER) {
            cursor--
        }
        for (i in cursor..index) out.add(i)
        // 向后吸收紧跟的 AI 回复
        if (index + 1 < messages.size && messages[index + 1].role == Role.ASSISTANT) {
            out.add(index + 1)
        }
    } else {
        // 向前吸收连续用户消息 + 自身
        var cursor = index - 1
        while (cursor >= 0 && messages[cursor].role == Role.USER) {
            out.add(cursor)
            cursor--
        }
        out.add(index)
    }
    return out.toList()
}

/** [pairedIndices] 的 id 版本 —— 多选态存的是 id（消息列表会变，下标会漂）。 */
fun pairedIds(messages: List<Message>, index: Int): Set<String> =
    pairedIndices(messages, index).mapTo(mutableSetOf()) { messages[it].id }
