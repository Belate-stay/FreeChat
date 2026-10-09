package com.freechat.data

import com.freechat.core.CompanionReply

/** Android transport detail; shared companion/server response parsing remains unchanged. */
data class CompanionApiReply(val body: CompanionReply, val reasoningContent: String = "") {
    val emotion get() = body.emotion
    val segments get() = body.segments
    val proactive get() = body.proactive
    val sources get() = body.sources
}
