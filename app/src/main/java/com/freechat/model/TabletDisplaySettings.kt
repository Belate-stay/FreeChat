package com.freechat.model

enum class TabletDisplayMode {
    EMBEDDED, FLOATING;

    companion object {
        fun fromStored(value: String?): TabletDisplayMode =
            entries.firstOrNull { it.name == value } ?: EMBEDDED
    }
}

enum class TabletFloatingSide {
    BOTH, LEFT, RIGHT;

    companion object {
        fun fromStored(value: String?): TabletFloatingSide =
            entries.firstOrNull { it.name == value } ?: BOTH
    }
}
