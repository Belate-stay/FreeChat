package com.freechat.data

import com.freechat.model.CharacterProfile
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.PerConvSettings
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Immutable, request-local bindings. They never write back to the global selections. */
data class RequestModels(val language: ModelInfo, val visual: ModelInfo, val vision: ModelInfo?) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RequestModels>
}

object ModelSelectionResolver {
    // Device-local selection identity only. ModelInfo.id remains the provider's API ID.
    // This new built-in must not shadow an existing custom deepseek-flash configuration.
    private const val DEEPSEEK_BUILT_IN = "@freechat/builtin/language/deepseek-flash"

    fun selectionKey(model: ModelInfo): String =
        if (model.isBuiltIn && model.modelType == ModelType.LANGUAGE && model.id == "deepseek-flash")
            DEEPSEEK_BUILT_IN else model.id

    fun find(id: String?, type: ModelType, catalog: List<ModelInfo>): ModelInfo? {
        if (id.isNullOrBlank()) return null
        val candidates = catalog.filter { it.modelType == type }
        if (id == DEEPSEEK_BUILT_IN)
            return candidates.find { it.isBuiltIn && selectionKey(it) == id }
        // Raw IDs saved before this built-in existed belong to the user's custom model.
        if (type == ModelType.LANGUAGE && id == "deepseek-flash")
            candidates.find { !it.isBuiltIn && it.id == id }?.let { return it }
        return candidates.find { it.id == id }
    }

    fun followsGlobal(id: String?, type: ModelType, catalog: List<ModelInfo>): Boolean =
        find(id, type, catalog) == null

    fun resolve(global: RequestModels, catalog: List<ModelInfo>, per: PerConvSettings? = null,
                character: CharacterProfile? = null): RequestModels {
        // Companion profiles and standard-mode New Rules are independent override layers.
        val languageId = if (character != null) character.languageModelId else per?.languageModelId
        val visualId = if (character != null) character.visualModelId else per?.visualModelId
        val visionId = if (character != null) character.visionModelId else per?.visionModelId
        return RequestModels(
            find(languageId, ModelType.LANGUAGE, catalog) ?: global.language,
            find(visualId, ModelType.VISUAL, catalog) ?: global.visual,
            find(visionId, ModelType.VISION, catalog) ?: global.vision,
        )
    }
}
