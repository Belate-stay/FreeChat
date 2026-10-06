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
    fun followsGlobal(id: String?, type: ModelType, catalog: List<ModelInfo>): Boolean =
        id.isNullOrBlank() || catalog.none { it.id == id && it.modelType == type }

    fun resolve(global: RequestModels, catalog: List<ModelInfo>, per: PerConvSettings? = null,
                character: CharacterProfile? = null): RequestModels {
        // Companion profiles and standard-mode New Rules are independent override layers.
        val languageId = if (character != null) character.languageModelId else per?.languageModelId
        val visualId = if (character != null) character.visualModelId else per?.visualModelId
        val visionId = if (character != null) character.visionModelId else per?.visionModelId
        fun override(id: String?, type: ModelType): ModelInfo? =
            id?.takeIf { it.isNotBlank() }?.let { modelId -> catalog.find { it.id == modelId && it.modelType == type } }
        return RequestModels(
            override(languageId, ModelType.LANGUAGE) ?: global.language,
            override(visualId, ModelType.VISUAL) ?: global.visual,
            override(visionId, ModelType.VISION) ?: global.vision,
        )
    }
}
