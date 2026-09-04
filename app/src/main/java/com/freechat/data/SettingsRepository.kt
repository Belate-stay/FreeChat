package com.freechat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.freechat.model.ModelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "freechat_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val KEY_SELECTED_VISUAL_MODEL = stringPreferencesKey("selected_visual_model")
        private val KEY_SELECTED_VISION_MODEL = stringPreferencesKey("selected_vision_model")
        private val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        private val KEY_COLOR_THEME = intPreferencesKey("color_theme")
        private val KEY_TEMP_MODE = intPreferencesKey("temp_mode")
        private val KEY_LENGTH_MODE = intPreferencesKey("length_mode")
        private val KEY_PINNED_IDS = stringSetPreferencesKey("pinned_conv_ids")
        private val KEY_ENABLE_WEB_SEARCH = booleanPreferencesKey("enable_web_search")
        private val KEY_SHOW_THINKING = booleanPreferencesKey("show_thinking")
        private val KEY_LANGUAGE_CODE = stringPreferencesKey("language_code")
        private val KEY_FONT_SIZE = intPreferencesKey("font_size_v2")
        private val KEY_VOICE_MODEL = stringPreferencesKey("voice_model")
        private val KEY_TTS_VOICE = stringPreferencesKey("tts_voice")
        private val KEY_TTS_SPEED = floatPreferencesKey("tts_speed_v3")
        private val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        private val KEY_TTS_AUTO_PLAY = booleanPreferencesKey("tts_auto_play")
        private val KEY_AUTO_SUMMARIZE_MEMORY = booleanPreferencesKey("auto_summarize_memory")
        private val KEY_USE_SYSTEM_FONT = booleanPreferencesKey("use_system_font")
        private val KEY_GLOBAL_MEMORIES = stringPreferencesKey("global_memories")
        private val KEY_ADVANCED_MATERIAL = booleanPreferencesKey("advanced_material")
        private val KEY_SYSTEM_DARK_THEME = booleanPreferencesKey("system_dark_theme")
        private val KEY_CHAT_MODE = intPreferencesKey("chat_mode")
        private val KEY_CUSTOM_MODELS = stringPreferencesKey("custom_models")
        private val KEY_HAS_AGREED_TERMS = booleanPreferencesKey("has_agreed_terms")
        private val KEY_ASR_MODEL = stringPreferencesKey("asr_model")
    }

    val selectedModelId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_MODEL] ?: ""
    }

    val selectedVisualModelId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_VISUAL_MODEL] ?: ""
    }

    val selectedVisionModelId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_VISION_MODEL] ?: ""
    }

    val themeModeOrdinal: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: 0
    }

    val colorThemeOrdinal: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_COLOR_THEME] ?: 0
    }

    val tempModeOrdinal: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_TEMP_MODE] ?: 0
    }

    val lengthModeOrdinal: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_LENGTH_MODE] ?: 0
    }

    val pinnedConversationIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_PINNED_IDS] ?: emptySet()
    }

    val enableWebSearch: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_WEB_SEARCH] ?: true  // 默认开启
    }

    val showThinking: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOW_THINKING] ?: false  // 默认关闭：不显示思考过程
    }

    val languageCode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE_CODE] ?: "system"
    }

    val fontSizeOrdinal: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_FONT_SIZE] ?: 1  // 1 = MEDIUM（标准）
    }

    val voiceModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_VOICE_MODEL] ?: "MiMo-V2.5-TTS"
    }

    val ttsVoice: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTS_VOICE] ?: "mimo_default"
    }

    val ttsSpeed: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTS_SPEED] ?: 1.0f
    }

    val ttsPitch: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTS_PITCH] ?: 1.0f
    }

    val ttsAutoPlay: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTS_AUTO_PLAY] ?: false  // 默认关闭自动朗读
    }

    val autoSummarizeMemory: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_SUMMARIZE_MEMORY] ?: true  // 默认开启：自动总结对话以巩固 AI 记忆
    }

    val useSystemFont: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_USE_SYSTEM_FONT] ?: false  // 默认关闭：使用自定义聊天字体
    }

    // 全局记忆点（多条），存 JSON 数组字符串以保留顺序
    val globalMemories: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_GLOBAL_MEMORIES] ?: "[]"
        runCatching { com.google.gson.Gson().fromJson(raw, Array<String>::class.java).toList() }
            .getOrElse { emptyList() }
    }

    val advancedMaterial: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ADVANCED_MATERIAL] ?: false  // 默认关闭：高级材质渲染效果未接入
    }

    val systemDarkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SYSTEM_DARK_THEME] ?: false  // 默认深色（false=深色，true=黑色）
    }

    val chatModeOrdinal: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_CHAT_MODE] ?: 0  // 0 = STANDARD（标准问答）
    }

    // 用户自定义模型（JSON 数组），5 类模型共用一份列表，按 modelType 区分
    val customModels: Flow<List<ModelInfo>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CUSTOM_MODELS] ?: "[]"
        runCatching {
            val listType = object : com.google.gson.reflect.TypeToken<List<ModelInfo>>() {}.type
            com.google.gson.Gson().fromJson<List<ModelInfo>>(raw, listType)
        }.getOrElse { emptyList() }
    }

    // 是否已同意用户协议与免责声明（首次进入的门槛）
    val hasAgreedTerms: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HAS_AGREED_TERMS] ?: false
    }

    // 语音识别模型 id（无内置，默认空 = 未添加）
    val asrModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ASR_MODEL] ?: ""
    }

    suspend fun saveSelectedModel(modelId: String) {
        context.dataStore.edit { prefs -> prefs[KEY_SELECTED_MODEL] = modelId }
    }

    suspend fun saveSelectedVisualModel(modelId: String) {
        context.dataStore.edit { prefs -> prefs[KEY_SELECTED_VISUAL_MODEL] = modelId }
    }

    suspend fun saveSelectedVisionModel(modelId: String) {
        context.dataStore.edit { prefs -> prefs[KEY_SELECTED_VISION_MODEL] = modelId }
    }

    suspend fun saveThemeMode(ordinal: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = ordinal }
    }

    suspend fun saveColorTheme(ordinal: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_COLOR_THEME] = ordinal }
    }

    suspend fun saveTempMode(ordinal: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_TEMP_MODE] = ordinal }
    }

    suspend fun saveLengthMode(ordinal: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_LENGTH_MODE] = ordinal }
    }

    suspend fun savePinnedIds(ids: Set<String>) {
        context.dataStore.edit { prefs -> prefs[KEY_PINNED_IDS] = ids }
    }

    suspend fun saveEnableWebSearch(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ENABLE_WEB_SEARCH] = enabled }
    }

    suspend fun saveShowThinking(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SHOW_THINKING] = enabled }
    }

    suspend fun saveLanguageCode(code: String) {
        context.dataStore.edit { prefs -> prefs[KEY_LANGUAGE_CODE] = code }
    }

    suspend fun saveFontSize(ordinal: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_FONT_SIZE] = ordinal }
    }

    suspend fun saveVoiceModel(model: String) {
        context.dataStore.edit { prefs -> prefs[KEY_VOICE_MODEL] = model }
    }

    suspend fun saveTtsVoice(voice: String) {
        context.dataStore.edit { prefs -> prefs[KEY_TTS_VOICE] = voice }
    }

    suspend fun saveTtsSpeed(speed: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_TTS_SPEED] = speed }
    }

    suspend fun saveTtsPitch(pitch: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_TTS_PITCH] = pitch }
    }

    suspend fun saveTtsAutoPlay(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_TTS_AUTO_PLAY] = auto }
    }

    suspend fun saveAutoSummarizeMemory(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_SUMMARIZE_MEMORY] = enabled }
    }

    suspend fun saveUseSystemFont(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_USE_SYSTEM_FONT] = enabled }
    }

    suspend fun saveGlobalMemories(memories: List<String>) {
        val raw = com.google.gson.Gson().toJson(memories)
        context.dataStore.edit { prefs -> prefs[KEY_GLOBAL_MEMORIES] = raw }
    }

    suspend fun saveAdvancedMaterial(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ADVANCED_MATERIAL] = enabled }
    }

    suspend fun saveSystemDarkTheme(isBlack: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SYSTEM_DARK_THEME] = isBlack }
    }

    suspend fun saveChatMode(ordinal: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_CHAT_MODE] = ordinal }
    }

    suspend fun saveCustomModels(models: List<ModelInfo>) {
        val raw = com.google.gson.Gson().toJson(models)
        context.dataStore.edit { prefs -> prefs[KEY_CUSTOM_MODELS] = raw }
    }

    suspend fun saveHasAgreedTerms(agreed: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_HAS_AGREED_TERMS] = agreed }
    }

    suspend fun saveAsrModel(modelId: String) {
        context.dataStore.edit { prefs -> prefs[KEY_ASR_MODEL] = modelId }
    }
}
