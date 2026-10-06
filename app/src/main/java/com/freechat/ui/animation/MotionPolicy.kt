package com.freechat.ui.animation

/** Bounded motion never scales with the height of a long answer or the size of its image. */
object MotionPolicy {
    const val MessageRiseDp = 22
    const val BodyRiseDp = 10
    const val PageTravelDp = 32
    const val LocatorTravelDp = 28
    const val LocatorGutterDp = 12
    const val PressScale = 0.96f
    const val HeaderPressScale = 0.94f
    const val MessageFreshnessMs = 4_000L

    fun messageAlpha(progress: Float) = progress.coerceIn(0f, 1f)
    fun messageTranslation(progress: Float, distancePx: Float) =
        distancePx.coerceAtLeast(0f) * (1f - progress.coerceIn(0f, 1f))
}

/** A claim is recorded BEFORE animation starts: cancelled/offscreen entries never replay. */
class MessageEntranceLedger(initialIds: Collection<String>, private val openedAtMs: Long) {
    private val seen = initialIds.toMutableSet()

    fun claim(id: String, timestampMs: Long, nowMs: Long): Boolean {
        if (!seen.add(id)) return false
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
