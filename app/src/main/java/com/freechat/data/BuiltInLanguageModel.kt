package com.freechat.data

import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.Provider

/** A single immutable definition for initial selection and the built-in catalog. */
object BuiltInLanguageModel {
    fun create(apiKey: String): ModelInfo = ModelInfo(
        id = "deepseek-flash", displayName = "DeepSeek-V4.1-Flash", provider = Provider.CUSTOM,
        description = "DeepSeek快速推理模型", apiBaseUrl = "https://api.deepseek.com",
        apiKey = apiKey, supportsWebSearch = true, modelType = ModelType.LANGUAGE,
        supportsDeepThinking = true, supportsNativeSearch = false, isBuiltIn = true
    )
}
