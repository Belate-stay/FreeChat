package com.freechat.data

/** A tool round / protocol fallback continues the same reply, not a new visible thought card. */
object ReasoningContinuity {
    fun join(previousRounds: String, currentRound: String): String = when {
        previousRounds.isBlank() -> currentRound
        currentRound.isBlank() -> previousRounds
        else -> previousRounds.trimEnd() + "\n\n" + currentRound
    }
}
