package com.freechat.model

enum class ModelType {
    LANGUAGE,  // 文本语言模型
    VISUAL,    // 文生图/图生图模型
    VISION,    // 识图/视觉理解模型
    TTS,       // 语音合成模型
    ASR        // 语音识别模型
}

data class ModelInfo(
    val id: String,
    val displayName: String,
    val provider: Provider,
    val description: String = "",
    val supportsWebSearch: Boolean = true,
    val modelType: ModelType = ModelType.LANGUAGE,
    val supportsThinking: Boolean = true,
    // 用户自定义模型：填 API 地址 + Key（provider = CUSTOM 时生效）；内置模型为空走 provider 路由
    val apiBaseUrl: String = "",
    val apiKey: String = "",
    // 内置基础模型（随应用发布，不可删除）；用户自己添加的为 false，可编辑/删除
    val isBuiltIn: Boolean = false
)

enum class Provider(val displayName: String) {
    DOUBAO("Doubao"),
    XIAOMI("Xiaomi"),
    CUSTOM("自定义")
}
