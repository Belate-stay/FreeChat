package com.freechat.data

import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.Provider

/** The route is supplied by the app (built-in proxy), never by editable model metadata. */
object BuiltInVoiceModel {
    const val STOCK_ID = "MiMo-V2.5-TTS"
    const val DESIGN_ID = "MiMo-V2.5-TTS-VoiceDesign"
    const val CLONE_ID = "MiMo-V2.5-TTS-VoiceClone"
    const val ASR_ID = "MiMo-V2.5-ASR"
    const val STOCK_API_ID = "mimo-v2.5-tts"
    const val DESIGN_API_ID = "mimo-v2.5-tts-voicedesign"
    const val CLONE_API_ID = "mimo-v2.5-tts-voiceclone"
    const val ASR_API_ID = "mimo-v2.5-asr"
    // 2026-10-18：Key 不进包、不指厂商 —— 一律走内置模型代理（见 BuiltInProxy）
    const val API_BASE = BuiltInProxy.BASE
    const val ENDPOINT = "$API_BASE/chat/completions"

    /**
     * 1.1.28：备注统一「简介，作者自用 API，不保证随时在线」尾句（渲染层染红、无句号）。
     * 音色设计/音色复刻**不进模型池**（哪儿都不显示，只在「自定义语音」页调用）——
     * 池子收 STOCK（见 ChatViewModel 的过滤），这两条只留定义给自定义语音/调试页用。
     */
    fun createAll(): List<ModelInfo> = listOf(
        create(STOCK_ID, "MiMo-V2.5-TTS", "Xiaomi 语音合成模型，${BuiltInLanguageModel.RED_TAIL}"),
        create(DESIGN_ID, "MiMo 音色设计", "根据音色描述创建语音。需账号具有该模型权限。"),
        create(CLONE_ID, "MiMo 音色复刻", "根据参考音频合成语音。需账号具有该模型权限。")
    )

    /** 内置语音识别模型（1.1.28 新增）：备注口径同所有内置模型。 */
    fun createAsr(): ModelInfo = ModelInfo(
        id = ASR_ID, displayName = "MiMo-V2.5-ASR", provider = Provider.XIAOMI,
        description = "Xiaomi 语音识别模型，${BuiltInLanguageModel.RED_TAIL}",
        supportsWebSearch = false, supportsThinking = false, modelType = ModelType.ASR,
        apiBaseUrl = API_BASE, apiKey = "", isBuiltIn = true
    )

    private fun create(id: String, name: String, description: String) = ModelInfo(
        id = id, displayName = name, provider = Provider.XIAOMI, description = description,
        supportsWebSearch = false, supportsThinking = false, modelType = ModelType.TTS,
        apiBaseUrl = API_BASE, apiKey = "", isBuiltIn = true
    )

    fun apiModelId(id: String): String = when (id.lowercase()) {
        STOCK_ID.lowercase(), STOCK_API_ID -> STOCK_API_ID
        DESIGN_ID.lowercase(), DESIGN_API_ID -> DESIGN_API_ID
        CLONE_ID.lowercase(), CLONE_API_ID -> CLONE_API_ID
        else -> throw VoiceConfigurationException("不支持的 MiMo 语音模型，请重新选择音色。")
    }
}
