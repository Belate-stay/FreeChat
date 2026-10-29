package com.freechat.core

/** Lossless delivery policy shared by App and the server brain. */
object CompanionRhythm {

    /** Preserve natural message boundaries; only narrative mode joins paragraphs. */
    fun prepareSegments(segments: List<String>, narrative: Boolean): List<String> {
        val rows = segments.map { it.trim() }.filter { it.isNotEmpty() }
        if (narrative) return listOf(rows.joinToString("\n\n")).filter { it.isNotEmpty() }
        return rows
    }

    /** Minimum reading/composition rhythm, not a second random model wait. */
    fun deliveryDelaysMs(segments: List<String>, narrative: Boolean, elapsedMs: Long): List<Long> =
        segments.mapIndexed { index, text ->
            val chars = CompanionPrompts.extractVoice(text)?.text?.length ?: text.length
            if (index == 0) {
                val minimum = if (narrative) 0L else (450L + chars * 24L).coerceAtMost(1800L)
                (minimum - elapsedMs.coerceAtLeast(0L)).coerceAtLeast(0L)
            } else (400L + chars * 32L).coerceAtMost(2600L)
        }

    /** Validate optional plans from newer servers; old servers use the same deterministic fallback. */
    fun validatedDelaysMs(plan: List<Long>?, segments: List<String>, narrative: Boolean, elapsedMs: Long): List<Long> =
        plan?.takeIf { it.size == segments.size && it.all { ms -> ms in 0L..3000L } }?.mapIndexed { index, ms ->
            if (index == 0) minOf(ms, deliveryDelaysMs(segments, narrative, elapsedMs).first()) else ms
        }
            ?: deliveryDelaysMs(segments, narrative, elapsedMs)

}
