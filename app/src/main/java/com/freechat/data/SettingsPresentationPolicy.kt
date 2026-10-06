package com.freechat.data

/** Parent switches gate effects, not the user's stored choices for their children. */
object SettingsPresentationPolicy {
    const val DEFAULT_LIQUID_BACKDROP = false
    fun liquidBackdrop(saved: Boolean?) = saved ?: DEFAULT_LIQUID_BACKDROP
    fun deepThinkingChildren(enabled: Boolean, supported: Boolean) = enabled && supported
    fun reasoningVisible(requested: Boolean, deepEnabled: Boolean, supported: Boolean) =
        requested && deepEnabled && supported
}
