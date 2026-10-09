package com.freechat.data

import com.freechat.model.ModelInfo
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Protocol-level reasoning parameters, separate from global/per-conversation usage selection. */
object ModelThinkingPolicy {
    fun parameters(model: ModelInfo, enabled: Boolean): Map<String, Any?> {
        if (model.apiBaseUrl.toHttpUrlOrNull()?.host == "api.deepseek.com") {
            return mapOf("thinking" to mapOf("type" to if (enabled) "enabled" else "disabled")) +
                if (enabled) mapOf("reasoning_effort" to "high") else emptyMap()
        }
        return com.freechat.core.CompanionPrompts.deepThinkExtras(enabled)
    }
}
