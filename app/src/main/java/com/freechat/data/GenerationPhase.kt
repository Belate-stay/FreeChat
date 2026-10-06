package com.freechat.data

/** Actual client/API events, not estimates of a provider's hidden internal work. */
enum class GenerationPhase(val order: Int, val image: Boolean = false) {
    IDLE(0), CONNECTING(1), UNDERSTANDING(2), SEARCHING(3), DEEP_RETRIEVAL(4), DRAFTING(5),
    IMAGE_CONTEXT(1, true), IMAGE_REFERENCES(2, true), IMAGE_CONNECTING(3, true),
    IMAGE_GENERATING(4, true), IMAGE_RECEIVING(5, true);

    fun advance(next: GenerationPhase): GenerationPhase = when {
        next == IDLE -> this
        this == IDLE || (!image && next.image) -> next
        image != next.image || next.order < order -> this
        else -> next
    }
}
