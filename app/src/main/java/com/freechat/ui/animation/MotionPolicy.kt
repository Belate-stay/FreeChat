package com.freechat.ui.animation

/** Bounded motion never scales with the height of a long answer or the size of its image. */
object MotionPolicy {
    const val MessageRiseDp = 22
    const val BodyRiseDp = 10
    const val PageTravelDp = 32
    const val LocatorTravelDp = 28
    const val LocatorGutterDp = 20
    const val PressScale = 0.96f
    const val HeaderPressScale = 0.94f
    const val MessageFreshnessMs = 4_000L

    fun messageAlpha(progress: Float) = progress.coerceIn(0f, 1f)
    fun messageTranslation(progress: Float, distancePx: Float) =
        distancePx.coerceAtLeast(0f) * (1f - progress.coerceIn(0f, 1f))

    /** 删除退场方向：AI 消息从屏幕左侧移出 (-1)，用户消息从右侧移出 (+1)。 */
    fun exitDirection(isUser: Boolean): Float = if (isUser) 1f else -1f

    /** 退场透明度：位移先走，透明度尾段才掉——不要和位移同步闪没。 */
    fun exitAlpha(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return 1f - ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
    }
}

/**
 * 重生成旁路：时间戳复用旧回复（保序），年龄窗会拒绝 claim。
 * 生成新回复 id 后 [mark]，两个账本的 claim 即使时间戳陈旧也放行一次。
 */
object AnimationReplay {
    private val pending = java.util.Collections.synchronizedSet(HashSet<String>())

    fun mark(id: String) {
        if (id.isNotBlank()) pending.add(id)
    }

    /** 一次性：消费后移除，第二次 claim 不会再放行。 */
    fun consume(id: String): Boolean = pending.remove(id)
}

/** A claim is recorded BEFORE animation starts: cancelled/offscreen entries never replay. */
class MessageEntranceLedger(initialIds: Collection<String>, private val openedAtMs: Long) {
    private val seen = initialIds.toMutableSet()

    fun claim(id: String, timestampMs: Long, nowMs: Long): Boolean {
        if (!seen.add(id)) return false
        if (AnimationReplay.consume(id)) return true
        val age = nowMs - timestampMs
        return timestampMs >= openedAtMs && age in 0..MotionPolicy.MessageFreshnessMs
    }
}

/** Sending the very first prompt creates a conversation before Compose observes its ID.
 * Do not seed that prompt as history; returning to that conversation still seeds all history.
 */
class FirstSendMotionIntent {
    private var pending: Pair<String, Long>? = null

    fun record(conversationId: String?, sentAtMs: Long) {
        pending = conversationId?.let { it to sentAtMs }
    }

    fun consume(conversationId: String?): Long? {
        val intent = pending
        pending = null
        return intent?.takeIf { it.first == conversationId }?.second
    }
}
