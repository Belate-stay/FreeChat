package com.freechat.data

import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.Provider

/** A single immutable definition for initial selection and the built-in catalog. */
object BuiltInLanguageModel {
    /** 1.1.28：内置备注统一「简介，作者自用 API，不保证随时在线」，尾句渲染层染红（无句号）。 */
    const val RED_TAIL = "作者自用 API，不保证随时在线"

    fun create(apiKey: String): ModelInfo = ModelInfo(
        id = "deepseek-flash", displayName = "DeepSeek-V4.1-Flash", provider = Provider.CUSTOM,
        description = "DeepSeek 快速推理模型，$RED_TAIL", apiBaseUrl = "https://api.deepseek.com",
        apiKey = apiKey, supportsWebSearch = true, modelType = ModelType.LANGUAGE,
        supportsDeepThinking = true, supportsNativeSearch = false, isBuiltIn = true
    )

    /**
     * 内置识图模型（1.1.28 新增）：与语言模型同一家 API 共用 Key，只作识图用。
     * id 与语言档分开（备注表按 id 查，档位文案不同）；普通用户不可查看/编辑（isBuiltIn）。
     * 1.1.29：深度思考**默认开启**（用户工单）——能力位置真 + 默认置真，
     * 识图请求按识图模型自己的默认取值（用户显式关闭仍可关，见 callVisionChat）。
     */
    fun createVision(apiKey: String): ModelInfo = ModelInfo(
        id = "deepseek-flash-vision", displayName = "DeepSeek-V4.1-Flash", provider = Provider.CUSTOM,
        description = "DeepSeek 图像识别模型，$RED_TAIL", apiBaseUrl = "https://api.deepseek.com",
        apiKey = apiKey, supportsWebSearch = false, modelType = ModelType.VISION,
        supportsDeepThinking = true, deepThinkingDefault = true, supportsNativeSearch = false, isBuiltIn = true
    )
}
