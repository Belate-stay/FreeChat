package com.freechat.model

/** Only the title-bar button backing changes; blur and the system status icons stay unchanged. */
enum class HeaderBarStyle {
    CARD,
    CUTOUT;

    companion object {
        fun fromOrdinal(ordinal: Int): HeaderBarStyle = entries.getOrElse(ordinal) { CARD }
    }
}
