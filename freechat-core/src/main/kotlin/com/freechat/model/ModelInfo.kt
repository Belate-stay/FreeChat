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
    // ===== 1.0.74 深度思考（通用推理控制，适配所有大模型） =====
    // 能力位：该模型有没有「深度思考/重推理」选项（获取模型列表时自动探测，编辑页可手动改）。
    // 老数据缺字段 = false = 不支持 = 输入框按钮灰色，零迁移。
    val supportsDeepThinking: Boolean = false,
    // ===== 1.0.75 语义升级：这就是「全局设置-AI系统优化」里那个**绑定模型**的深度思考开关 =====
    // 用户在某模型打开，切走再切回仍是开；模型之间互不影响；**所有模型默认关**（用户点名）。
    // 对话级「新规则」优先级更高（双键 (对话×模型) 覆盖，缺席 = 跟随这个值）。
    // 字段名保持不动（1.0.74 落盘的 JSON 键就是它，改名要多背一份迁移）；1.0.75 一次性迁移会把
    // 老数据里的 true 全部归零 —— 语义从「默认值」变成「用户的显式选择」，老的 true 都不是用户选的。
    val deepThinkingDefault: Boolean = false,
    // ===== 1.0.75 内置联网搜索（组合拳之一：模型自带搜索优先，SerpAPI 只兜底） =====
    // 能力位：这个模型的网关自带联网搜索（小米 web_search 工具 / enable_search 类参数）。
    // 内置小米模型恒 true（provider 路由兜着）；自定义模型探测 + 编辑页手动勾选。老数据缺字段 = false。
    val supportsNativeSearch: Boolean = false,
    // 用户自定义模型：填 API 地址 + Key（provider = CUSTOM 时生效）；内置模型为空走 provider 路由
    val apiBaseUrl: String = "",
    val apiKey: String = "",
    // 内置基础模型（随应用发布，不可删除）；用户自己添加的为 false，可编辑/删除
    val isBuiltIn: Boolean = false,
    // ===== 1.0.69 生图参数（VISUAL）。空 = 旧的写死默认（Doubao 1920x1920 / 自定义 1024x1024） =====
    // 比例："1:1" / "4:3" / …；自动获取到的官方比例原样存
    val genAspectRatio: String = "", // Legacy wire compatibility only; ignored by image requests.
    // 分辨率：短边像素档（"1024"/"1536"/"2048"）或官方/自定义的显式 "宽x高"（如 "1920x1920"）
    val genResolution: String = "",
    // 预设风格：内置 key（photo/anime/…）或获取到的官方风格名；空 = 无。以提示词方式附加，不保证生效
    val genStyle: String = ""
)

enum class Provider(val displayName: String) {
    DOUBAO("Doubao"),
    XIAOMI("Xiaomi"),
    CUSTOM("自定义")
}
