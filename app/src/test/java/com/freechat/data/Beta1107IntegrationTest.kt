package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Wiring guards complement the behavioral selector/request/store tests. */
class Beta1107IntegrationTest {
    private fun viewModel() = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()

    @Test fun allCustomVoiceModelsAreAvailableAndTheLocalPresetIsObserved() {
        val source = viewModel()
        assertTrue(source.contains("BuiltInVoiceModel.createAll(BuildConfig.MIMO_TTS_KEY)"))
        assertTrue(source.contains("settingsRepo.ttsPresetId.collect"))
    }

    @Test fun selectedVoiceIsUsedByTheActualChatSpeechPath() {
        val source = viewModel().substringAfter("fun speakMessage(").substringBefore("private fun currentTtsModel")
        assertTrue("The selected local voice must reach synthesis", source.contains("voicePresetRepo.playbackConfig"))
        assertTrue("Stock per-conversation overrides must not use a custom sample", source.contains("BuiltInVoiceModel.STOCK_API_ID"))
        assertTrue(source.contains("TtsController.reportConfigurationError"))
        assertTrue(source.contains("config, _ttsSpeed.value"))
    }

    @Test fun sceneReferencesAreRevalidatedAgainstFreshHistoryImmediatelyBeforeTheRequest() {
        val source = viewModel()
        val scene = source.substringAfter("private fun generateSceneImage(").substringBefore("private fun sceneImageError")
        assertTrue(scene.contains("SceneReferenceSelector.prepare"))
        assertTrue(scene.contains("SceneReferenceSelector.revalidate"))
        assertTrue(scene.contains("MessageDeletion.deletedIds(convId)"))
        assertTrue(scene.contains("sceneRequestFactory ="))
        val request = source.substringAfter("private suspend fun callDoubaoImageGen(").substringBefore("private data class GenText")
        assertTrue(request.indexOf("sceneRequestFactory?.invoke()") in 0 until request.indexOf("ImageApiRequest.body"))
    }
}
