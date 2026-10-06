package com.freechat.core

import com.freechat.model.Message

/**
 * 历史装载与 token 预算（纯函数，1.0.91 M1 从 ChatViewModel 平移，行为一字不变）。
 *
 * 预算语义：历史只分到有效上下文的 6 成（系统提示词 + 记忆注入 + 生成余量共占 4 成，
 * 把历史装满会在真实 API 上直接顶爆 >context 400）；标准调用点再 coerceAtMost(30_000)
 * （1.0.74 提速刀），拟人档不封顶（质量优先，用户点名）。
 */
object CompanionHistory {

    fun estimateTokens(s: String): Int = (s.length + 1) / 2 + 8

    /** 历史预算（tokens）：增强检索开 = 1M，否则 = 256K */
    fun historyBudgetTokens(enhanced: Boolean): Int {
        val ctx = if (enhanced) 1_000_000 else 256_000
        return (ctx.toLong() * 3 / 5).toInt()
    }

    /** 从最新往回装历史，装满预算为止（1.0.71 起统一 token 裁剪，不再有条数窗口）；跳过场景图视觉行 */
    fun pickHistory(working: List<Message>, budgetTokens: Int): List<Message> {
        val picked = ArrayDeque<Message>()
        var used = 0
        for (m in working.asReversed()) {
            if (m.sceneVisualization) continue
            val t = estimateTokens(m.content)
            if (used + t > budgetTokens && picked.isNotEmpty()) break
            picked.addFirst(m)
            used += t
        }
        return picked.toList()
    }
}
