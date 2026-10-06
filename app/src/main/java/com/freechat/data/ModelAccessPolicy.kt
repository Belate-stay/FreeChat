package com.freechat.data

import com.freechat.model.ModelInfo

/** Built-in definitions are immutable; usage switches are not model edits. */
object ModelAccessPolicy {
    fun canEdit(model: ModelInfo?): Boolean = model?.isBuiltIn != true

    /** Ignore legacy editor overrides, retaining only the global per-model usage preference. */
    fun withUsagePreference(base: ModelInfo, stored: ModelInfo?): ModelInfo {
        if (!base.isBuiltIn) return base
        val matching = stored?.takeIf { it.id == base.id && it.modelType == base.modelType }
        return base.copy(deepThinkingDefault = base.supportsDeepThinking &&
            (matching?.deepThinkingDefault ?: base.deepThinkingDefault))
    }
}
