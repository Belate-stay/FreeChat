package com.freechat.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** These integration guards deliberately fail against the previous stock-only implementation. */
class MiMoCustomVoiceRegressionTest {
    @Test
    fun synthesisUsesTheSelectedVoiceConfigurationRatherThanTheHardcodedStockModel() {
        val source = File("src/main/java/com/freechat/data/TtsController.kt").readText()
        assertTrue("MiMo synthesis must construct the request from its selected voice configuration",
            source.contains("MiMoVoiceRequest.create("))
        assertTrue("TTS failures need a visible state rather than a silent null result",
            source.contains("val lastError: StateFlow<MiMoTtsError?>"))
    }

    @Test
    fun speechCacheCannotReuseAStockVoiceAfterChangingTheCustomReferenceOrStyle() {
        val source = File("src/main/java/com/freechat/data/TtsController.kt").readText()
        assertTrue("Speech cache identity must include custom voice configuration",
            source.contains("MiMoVoiceRequest.cacheIdentity("))
        assertFalse("A Java text hash and stock voice alone cannot identify a custom voice",
            source.contains("val cacheKey = \"\$messageId|\$voice|\${text.hashCode()}\""))
    }

    @Test
    fun aiVoicePageOffersTheRequestedAdvancedCreationEntry() {
        val source = File("src/main/java/com/freechat/ui/screens/VoiceDebugScreen.kt").readText()
        assertTrue("The AI voice page needs an advanced custom voice entry",
            source.contains("高级自定义"))
        assertTrue("The advanced entry must identify the feature as Beta", source.contains("Beta"))
        val advanced = File("src/main/java/com/freechat/ui/screens/MiMoVoiceSettingsPage.kt")
        assertTrue("The advanced creation flow must be available from the voice page", advanced.exists())
        val flow = advanced.readText()
        assertTrue(flow.contains("根据提示词模拟"))
        assertTrue(flow.contains("根据音频模拟"))
        assertTrue("Microphone denial must be handled in the creation flow",
            flow.contains("ActivityResultContracts.RequestPermission()"))
    }

    @Test
    fun repeatedIdenticalUploadReleasesItsNewLeaseInsteadOfHidingItInTheDraftSet() {
        val source = File("src/main/java/com/freechat/ui/screens/MiMoVoiceSettingsPage.kt").readText()
        val replacement = source.substringAfter("fun replaceReference(next: VoiceSampleRef)")
            .substringBefore("fun cancelDraft()")
        assertTrue("Each import acquires a lease: an identical replacement must release its new lease while retaining the original draft",
            Regex("if\\s*\\(old == next\\)\\s*\\{[^}]*repository\\.discardLater\\(setOf\\(next\\)\\)")
                .containsMatchIn(replacement))
    }

    @Test
    fun advancedCreationFieldsReuseTheCharacterInputStyle() {
        val source = File("src/main/java/com/freechat/ui/screens/MiMoVoiceSettingsPage.kt").readText()
        assertTrue("New voice fields must reuse the character page's rounded input component",
            source.contains("CharacterTextField("))
        assertFalse("New voice fields must not fall back to the default outlined shape",
            source.contains("OutlinedTextField("))
        assertTrue("Placeholders must use the character page's quieter hint color",
            source.contains("characterHintColor(colors)"))
        listOf("prompt", "style", "previewText", "name").forEach { value ->
            assertTrue("The $value field must use the shared character field styling",
                Regex("CharacterTextField\\(\\s*value = $value\\b").containsMatchIn(source))
        }
    }

    @Test
    fun removingAnAudioReferenceClearsItsPreviewBeforeReleasingItsOnlyDraftLease() {
        val source = File("src/main/java/com/freechat/ui/screens/MiMoVoiceSettingsPage.kt").readText()
        val removal = source.substringAfter("TextButton(enabled = !busy && !capture.isRecording, onClick = {")
            .substringBefore("}) { Text(\"移除\") }")
        val clearPreview = removal.indexOf("clearPreview()")
        val detachReference = removal.indexOf("reference = null")
        assertTrue("Clear the completed preview while the original reference is still attached, so it is not released twice",
            clearPreview >= 0 && clearPreview < detachReference)
        assertTrue("Reference removal must release the original draft lease exactly once",
            Regex("repository\\.discardLater\\(setOf\\(ref\\)\\)").findAll(removal).count() == 1)
    }

    @Test
    fun replacingAnAudioReferenceClearsItsPreviewBeforeRegisteringTheNewDraft() {
        val source = File("src/main/java/com/freechat/ui/screens/MiMoVoiceSettingsPage.kt").readText()
        val replacement = source.substringAfter("fun replaceReference(next: VoiceSampleRef)")
            .substringBefore("fun cancelDraft()")
        val clearPreview = replacement.indexOf("clearPreview()")
        val changeReference = replacement.indexOf("reference = next")
        val registerDraft = replacement.indexOf("drafts += next")
        assertTrue("Clear the previous AUDIO preview before changing its attached reference, avoiding a second original release",
            clearPreview >= 0 && clearPreview < changeReference)
        assertTrue("Register the new draft after preview cleanup, especially when the imported bytes equal the old preview",
            clearPreview >= 0 && clearPreview < registerDraft)
    }

    @Test
    fun successfulSaveReleasesUiDraftsOnceBeforeNavigationAndClearsTheirDescriptor() {
        val source = File("src/main/java/com/freechat/ui/screens/MiMoVoiceSettingsPage.kt").readText()
        val success = source.substringAfter("val saved = repository.save(name, preview)")
            .substringBefore("} catch (cancelled: CancellationException)")
        val removeReference = success.indexOf("drafts.remove(preview.reference)")
        val removePreview = success.indexOf("drafts.remove(preview.preview)")
        val releaseDrafts = success.indexOf("repository.discardLater(setOf(preview.reference, preview.preview))")
        val clearReference = success.indexOf("reference = null")
        val clearConsent = success.indexOf("consent = false")
        val navigate = success.indexOf("onSaved(saved)")
        assertTrue("After save returns, detach the UI-owned drafts before scheduling their single release",
            removeReference >= 0 && removePreview >= 0 && releaseDrafts > removeReference && releaseDrafts > removePreview)
        assertTrue("Clear the saved reference and consent before asynchronous selection/navigation can leave this page visible",
            clearReference > releaseDrafts && clearConsent > releaseDrafts && navigate > clearReference && navigate > clearConsent)
        assertFalse("Successful save must not clear its preview through an additional sample release",
            success.contains("clearPreview()"))
    }
}
