package com.freechat.data

import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.Provider

/** The route and key are supplied by the app, never by editable model metadata. */
object BuiltInVoiceModel {
    const val STOCK_ID = "MiMo-V2.5-TTS"
    const val DESIGN_ID = "MiMo-V2.5-TTS-VoiceDesign"
    const val CLONE_ID = "MiMo-V2.5-TTS-VoiceClone"
    const val STOCK_API_ID = "mimo-v2.5-tts"
    const val DESIGN_API_ID = "mimo-v2.5-tts-voicedesign"
    const val CLONE_API_ID = "mimo-v2.5-tts-voiceclone"
    const val API_BASE = "https://api.xiaomimimo.com/v1"
    const val ENDPOINT = "$API_BASE/chat/completions"

    fun createAll(apiKey: String): List<ModelInfo> = listOf(
        create(STOCK_ID, "MiMo-V2.5-TTS", "小米预置音色语音合成。", apiKey),
        create(DESIGN_ID, "MiMo 音色设计", "根据音色描述创建语音。需账号具有该模型权限。", apiKey),
        create(CLONE_ID, "MiMo 音色复刻", "根据参考音频合成语音。需账号具有该模型权限。", apiKey)
    )

    private fun create(id: String, name: String, description: String, key: String) = ModelInfo(
        id = id, displayName = name, provider = Provider.XIAOMI, description = description,
        supportsWebSearch = false, supportsThinking = false, modelType = ModelType.TTS,
        apiBaseUrl = API_BASE, apiKey = key, isBuiltIn = true
    )

    fun apiModelId(id: String): String = when (id.lowercase()) {
        STOCK_ID.lowercase(), STOCK_API_ID -> STOCK_API_ID
        DESIGN_ID.lowercase(), DESIGN_API_ID -> DESIGN_API_ID
        CLONE_ID.lowercase(), CLONE_API_ID -> CLONE_API_ID
        else -> throw VoiceConfigurationException("不支持的 MiMo 语音模型，请重新选择音色。")
    }
}
