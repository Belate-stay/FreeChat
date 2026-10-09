package com.freechat.data

import com.freechat.model.CharacterProfile
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.PerConvSettings
import com.freechat.model.Provider
import com.freechat.sync.PerConvBridge
import com.freechat.sync.Wire
import com.freechat.model.Conversation
import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BuiltInSelectionRegressionTest {
    private val builtIn = BuiltInLanguageModel.create("fixture-not-a-secret")
    private val custom = builtIn.copy(isBuiltIn = false, displayName = "My relay",
        apiBaseUrl = "https://fixture.invalid", apiKey = "fixture-relay", deepThinkingDefault = true)
    private val visual = ModelInfo("fixture-image", "Fixture image", Provider.CUSTOM, modelType = ModelType.VISUAL)
    private val global = RequestModels(builtIn, visual, null)
    private val catalog = listOf(builtIn, custom, visual)

    @Test fun legacyCustomIdMustNotBeShadowedByTheNewBuiltInInRules() {
        assertEquals(custom, ModelSelectionResolver.resolve(global, catalog,
            PerConvSettings(languageModelId = custom.id)).language)
    }

    @Test fun legacyCustomIdMustNotBeShadowedByTheNewBuiltInInCharacters() {
        assertEquals(custom, ModelSelectionResolver.resolve(global, catalog,
            character = CharacterProfile(languageModelId = custom.id)).language)
    }

    @Test fun builtInTokenRestoresTheBuiltInWithoutChangingItsApiId() {
        val token = ModelSelectionResolver.selectionKey(builtIn)
        assertNotEquals(custom.id, token)
        assertEquals(custom.id, ModelSelectionResolver.selectionKey(custom))
        assertEquals("deepseek-flash", builtIn.id)
        assertEquals(builtIn, ModelSelectionResolver.find(token, ModelType.LANGUAGE, catalog))
        assertEquals(builtIn, ModelSelectionResolver.resolve(global, catalog,
            PerConvSettings(languageModelId = token)).language)
        assertEquals(builtIn, ModelSelectionResolver.resolve(global, catalog,
            character = CharacterProfile(languageModelId = token)).language)
        assertFalse(ModelSelectionResolver.followsGlobal(token, ModelType.LANGUAGE, catalog))
    }

    @Test fun legacyRawIdStillResolvesWhenNoCustomModelExistsAndDoesNotCrossTypes() {
        val noCustom = listOf(builtIn, visual)
        assertEquals(builtIn, ModelSelectionResolver.find(builtIn.id, ModelType.LANGUAGE, noCustom))
        assertNull(ModelSelectionResolver.find(ModelSelectionResolver.selectionKey(builtIn), ModelType.VISUAL, catalog))
        assertTrue(ModelSelectionResolver.followsGlobal("removed-model", ModelType.LANGUAGE, catalog))
    }

    @Test fun reservedBuiltInTokenCannotBeHijackedByACustomApiId() {
        val token = ModelSelectionResolver.selectionKey(builtIn)
        val spoof = custom.copy(id = token)
        assertEquals(builtIn, ModelSelectionResolver.find(token, ModelType.LANGUAGE, listOf(spoof, builtIn)))
        assertNull(ModelSelectionResolver.find(token, ModelType.LANGUAGE, listOf(spoof)))
    }

    @Test fun unrelatedModelTypesAndIdsKeepTheirExistingCatalogPrecedence() {
        val unrelatedBuiltIn = builtIn.copy(id = "unrelated-language")
        val unrelatedCustom = custom.copy(id = "unrelated-language")
        val visualBuiltIn = visual.copy(isBuiltIn = true)
        assertEquals(unrelatedBuiltIn, ModelSelectionResolver.find(unrelatedBuiltIn.id, ModelType.LANGUAGE,
            listOf(unrelatedBuiltIn, unrelatedCustom)))
        assertEquals(visualBuiltIn, ModelSelectionResolver.find(visual.id, ModelType.VISUAL,
            listOf(visualBuiltIn, visual)))
    }

    @Test fun sameApiIdGetsIndependentReasoningPreferencesAndRefreshIdentities() {
        val builtinKey = ModelSelectionResolver.selectionKey(builtIn)
        val customKey = ModelSelectionResolver.selectionKey(custom)
        val settings = PerConvSettings(deepThinkByModel = mapOf(builtinKey to false, customKey to true))
        assertEquals(false, settings.deepThinkByModel[builtinKey])
        assertEquals(true, settings.deepThinkByModel[customKey])
        val updated = custom.copy(displayName = "Updated relay", deepThinkingDefault = false)
        val updatedCatalog = listOf(builtIn, updated, visual)
        assertEquals(updated, ModelSelectionResolver.find(customKey, ModelType.LANGUAGE, updatedCatalog))
        assertEquals(builtIn, ModelSelectionResolver.find(builtinKey, ModelType.LANGUAGE, updatedCatalog))
        assertEquals(builtIn, ModelSelectionResolver.find(builtinKey, ModelType.LANGUAGE, listOf(builtIn, visual)))
    }

    @Test fun selectionTokensRemainLocalAndCloudRestoreKeepsThem() {
        val token = ModelSelectionResolver.selectionKey(builtIn)
        val settings = PerConvSettings(languageModelId = token, deepThinkByModel = mapOf(token to true))
        assertFalse(PerConvBridge.toWire(settings).toString().contains(token))
        assertEquals(settings, PerConvBridge.mergeIntoLocal(settings, JsonObject()))
        val character = CharacterProfile(name = "Fixture", languageModelId = token)
        val conversation = Conversation(characterProfile = character)
        val wire = Wire.convToWire(conversation)
        assertEquals("", wire.getAsJsonObject("characterProfile").get("languageModelId").asString)
        assertEquals(token, Wire.convFromWire(wire, conversation)!!.characterProfile!!.languageModelId)
    }

    @Test fun allLanguagePickersAndPreferenceWritersUseSelectionKeys() {
        fun source(path: String) = File("src/main/java/com/freechat/$path").readText()
        val vm = source("viewmodel/ChatViewModel.kt")
        assertTrue(vm.contains("saveSelectedModel(ModelSelectionResolver.selectionKey(model))"))
        assertTrue(vm.contains("get(ModelSelectionResolver.selectionKey(model))"))
        assertTrue(vm.contains("!_selectedModel.value.isBuiltIn && _selectedModel.value.id == modelId"))
        assertFalse(vm.contains("modelsOfType(ModelType.LANGUAGE).find { it.id == savedId }"))
        assertFalse(vm.contains("modelsOfType(ModelType.LANGUAGE).find { it.id == id }"))
        assertTrue(source("ui/screens/SettingsScreen.kt").contains("selectionKey(selectedModel)"))
        assertTrue(source("ui/screens/NewRulesScreen.kt").contains("languageModelId = ModelSelectionResolver.selectionKey(m)"))
        assertTrue(source("ui/screens/CharacterSetupScreen.kt").contains("onSelect(ModelSelectionResolver.selectionKey(m))"))
    }
}
