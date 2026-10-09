package com.freechat.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VoicePresetRepositoryTest {
    @Test
    fun designedPreviewBecomesTheStableCloneReferenceAfterNamingAndReloading() = runBlocking {
        val directory = Files.createTempDirectory("freechat-voice-test-").toFile()
        try {
            val repo = VoicePresetRepository(directory)
            val preview = repo.preparePreview(VoiceCreationMode.PROMPT, "温柔女声", "平静", null,
                MiMoVoiceRequestTest.wavFixture())
            assertTrue(repo.presets.value.isEmpty())
            val saved = repo.save("  夜间电台  ", preview)
            repo.discardSamples(setOf(preview.reference, preview.preview))
            assertEquals("夜间电台", saved.name)
            assertEquals(preview.preview, saved.reference)
            val reloaded = VoicePresetRepository(directory)
            assertEquals(saved, reloaded.presets.value.single())
            val config = reloaded.playbackConfig(saved.id)
            assertEquals(BuiltInVoiceModel.CLONE_API_ID, config.modelId)
            assertEquals("温柔女声", config.designPrompt)
            assertEquals("平静", config.speakerStyle)
            assertEquals(saved.reference.contentHash, config.sample!!.contentHash)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun audioCreatedVoiceKeepsTheAuthorizedOriginalReference() = runBlocking {
        val directory = Files.createTempDirectory("freechat-voice-test-").toFile()
        try {
            val repo = VoicePresetRepository(directory)
            val reference = repo.importSample(ByteArrayInputStream(MiMoVoiceRequestTest.wavFixture(1)))
            val preview = repo.preparePreview(VoiceCreationMode.AUDIO, "", "自然", reference,
                MiMoVoiceRequestTest.wavFixture(2))
            val saved = repo.save("自己的声音", preview)
            repo.discardSamples(setOf(preview.reference, preview.preview))
            assertEquals(reference, saved.reference)
            assertNotEquals(saved.reference.contentHash, saved.preview.contentHash)
            assertEquals(reference.contentHash, repo.playbackConfig(saved.id).sample!!.contentHash)
            assertFalse("Metadata must store a bounded reference rather than inline base64",
                File(directory, "presets.json").readText().contains("base64"))
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun discardedDraftDeletesPrivateCopiesButDoesNotDeleteASavedSharedReference() = runBlocking {
        val directory = Files.createTempDirectory("freechat-voice-test-").toFile()
        try {
            val repo = VoicePresetRepository(directory)
            val preview = repo.preparePreview(VoiceCreationMode.PROMPT, "沉稳", "", null, MiMoVoiceRequestTest.wavFixture())
            val preset = repo.save("沉稳", preview)
            val discarded = repo.importSample(ByteArrayInputStream(MiMoVoiceRequestTest.wavFixture(3)))
            repo.discardSamples(setOf(preview.preview, discarded))
            assertEquals(preset.reference.contentHash, repo.readSample(preset.reference).contentHash)
            assertFalse(File(directory, "samples/${discarded.fileName}").exists())
            repo.delete(preset.id)
            assertTrue(repo.presets.value.isEmpty())
            assertFalse(File(directory, "samples/${preset.reference.fileName}").exists())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun releasingDuplicateImportKeepsTheCurrentDraftUntilItsFinalCancellation() = runBlocking {
        val directory = Files.createTempDirectory("freechat-voice-test-").toFile()
        try {
            val repo = VoicePresetRepository(directory)
            val current = repo.importSample(ByteArrayInputStream(MiMoVoiceRequestTest.wavFixture()))
            val repeated = repo.importSample(ByteArrayInputStream(MiMoVoiceRequestTest.wavFixture()))
            assertEquals("The same audio shares its private file but each import owns a lease", current, repeated)
            val privateCopy = File(directory, "samples/${current.fileName}")

            repo.discardSamples(setOf(repeated))
            assertTrue("Releasing the redundant import must retain the user's current sample", privateCopy.exists())
            assertEquals(current.contentHash, repo.readSample(current).contentHash)

            repo.discardSamples(setOf(current))
            assertFalse("Cancelling the remaining draft must remove its unsaved private audio", privateCopy.exists())
            assertTrue(repo.presets.value.isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun audioReferenceRemovalDoesNotConsumeANewDraftUsingTheSameAudio() = runBlocking {
        val directory = Files.createTempDirectory("freechat-voice-test-").toFile()
        try {
            val repo = VoicePresetRepository(directory)
            val original = repo.importSample(ByteArrayInputStream(MiMoVoiceRequestTest.wavFixture()))
            val completed = repo.preparePreview(VoiceCreationMode.AUDIO, "", "", original,
                MiMoVoiceRequestTest.wavFixture(1))

            // Preview cleanup excludes the attached original; its explicit removal owns one release.
            repo.discardSamples(setOf(completed.preview))
            val nextDraft = repo.importSample(ByteArrayInputStream(MiMoVoiceRequestTest.wavFixture()))
            repo.discardSamples(setOf(original))

            val sharedCopy = File(directory, "samples/${nextDraft.fileName}")
            assertTrue("Delayed removal of the old reference must preserve the new draft's lease", sharedCopy.exists())
            assertEquals(nextDraft.contentHash, repo.readSample(nextDraft).contentHash)
            assertFalse(File(directory, "samples/${completed.preview.fileName}").exists())
            repo.discardSamples(setOf(nextDraft))
            assertFalse("Only cancellation of the new draft may remove its private audio", sharedCopy.exists())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun replacementWithPreviousPreviewAudioKeepsItsNewDraftLease() = runBlocking {
        val directory = Files.createTempDirectory("freechat-voice-test-").toFile()
        try {
            val repo = VoicePresetRepository(directory)
            val original = repo.importSample(ByteArrayInputStream(MiMoVoiceRequestTest.wavFixture()))
            val completed = repo.preparePreview(VoiceCreationMode.AUDIO, "", "", original,
                MiMoVoiceRequestTest.wavFixture(1))
            val nextDraft = repo.importSample(ByteArrayInputStream(MiMoVoiceRequestTest.wavFixture(1)))
            assertEquals("The imported replacement may share the previous preview's private audio", completed.preview, nextDraft)

            repo.discardSamples(setOf(completed.preview))
            repo.discardSamples(setOf(original))

            val nextCopy = File(directory, "samples/${nextDraft.fileName}")
            assertFalse(File(directory, "samples/${original.fileName}").exists())
            assertTrue("Releasing the old preview must preserve the imported replacement's lease", nextCopy.exists())
            assertEquals(nextDraft.contentHash, repo.readSample(nextDraft).contentHash)
            repo.discardSamples(setOf(nextDraft))
            assertFalse("The replacement remains a draft and is removed on cancellation", nextCopy.exists())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun deletingASavedPresetDoesNotConsumeASharedUnfinishedDraftLease() = runBlocking {
        val directory = Files.createTempDirectory("freechat-voice-test-").toFile()
        try {
            val repo = VoicePresetRepository(directory)
            val completed = repo.preparePreview(VoiceCreationMode.PROMPT, "温柔自然", "", null,
                MiMoVoiceRequestTest.wavFixture())
            val saved = repo.save("本机音色", completed)
            repo.discardSamples(setOf(completed.reference, completed.preview))
            val draft = repo.importSample(ByteArrayInputStream(MiMoVoiceRequestTest.wavFixture()))
            assertEquals("A new unfinished draft may share a saved preset's private sample", saved.reference, draft)

            repo.delete(saved.id)

            val sharedCopy = File(directory, "samples/${draft.fileName}")
            assertTrue("Deleting a saved preset owns no draft lease and must leave the unfinished draft readable", sharedCopy.exists())
            assertTrue(repo.presets.value.isEmpty())
            assertEquals(draft.contentHash, repo.readSample(draft).contentHash)
            repo.discardSamples(setOf(draft))
            assertFalse("Only cancellation of the unfinished draft may release its sample", sharedCopy.exists())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun savingPresetKeepsItsDraftLeaseUntilUiCompletionOrCancellationCleanup() = runBlocking {
        val directory = Files.createTempDirectory("freechat-voice-test-").toFile()
        try {
            val repo = VoicePresetRepository(directory)
            val completed = repo.preparePreview(VoiceCreationMode.PROMPT, "温柔自然", "", null,
                MiMoVoiceRequestTest.wavFixture())
            val saved = repo.save("已提交音色", completed)
            val nextDraft = repo.importSample(ByteArrayInputStream(MiMoVoiceRequestTest.wavFixture()))

            // Save committed, but its UI completion was cancelled: UI still owns this one cleanup.
            repo.discardSamples(setOf(completed.reference, completed.preview))
            repo.delete(saved.id)

            val sharedCopy = File(directory, "samples/${nextDraft.fileName}")
            assertTrue("Save must not pre-release the UI's lease and let its cancellation cleanup consume a later draft", sharedCopy.exists())
            assertEquals(nextDraft.contentHash, repo.readSample(nextDraft).contentHash)
            repo.discardSamples(setOf(nextDraft))
            assertFalse("The later draft's own cancellation releases the remaining lease", sharedCopy.exists())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun samplePathsCannotEscapeThePrivateDirectoryAndModifiedBytesAreRejected() = runBlocking {
        val directory = Files.createTempDirectory("freechat-voice-test-").toFile()
        try {
            val repo = VoicePresetRepository(directory)
            val ref = repo.importSample(ByteArrayInputStream(MiMoVoiceRequestTest.wavFixture()))
            try { repo.readSample(ref.copy(fileName = "../external.wav")); fail("Traversal must be rejected") }
            catch (_: VoiceConfigurationException) { }
            File(directory, "samples/${ref.fileName}").writeBytes(MiMoVoiceRequestTest.wavFixture(1))
            try { repo.readSample(ref); fail("A changed reference must not silently produce a different voice") }
            catch (error: VoiceConfigurationException) { assertTrue(error.message.orEmpty().contains("参考音频")) }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun missingPresetAndBlankNamesProduceClearErrors() = runBlocking {
        val directory = Files.createTempDirectory("freechat-voice-test-").toFile()
        try {
            val repo = VoicePresetRepository(directory)
            try { repo.playbackConfig("missing"); fail("Missing preset must fail") }
            catch (error: VoiceConfigurationException) { assertTrue(error.message.orEmpty().contains("音色")) }
            val preview = repo.preparePreview(VoiceCreationMode.PROMPT, "明亮", "", null, MiMoVoiceRequestTest.wavFixture())
            try { repo.save("   ", preview); fail("Blank names must fail") }
            catch (error: VoiceConfigurationException) { assertTrue(error.message.orEmpty().contains("名称")) }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun selectedPresetDefaultsToStockAndOnlyPersistsAnOpaqueLocalId() = runBlocking {
        val preferences = MemoryPreferences()
        val settings = SettingsRepository(preferences)
        assertEquals("", settings.ttsPresetId.first())
        settings.saveTtsPresetId("fixture-preset-id")
        assertEquals("fixture-preset-id", settings.ttsPresetId.first())
        val snapshot = preferences.data.first().asMap()
        assertEquals(1, snapshot.size)
        assertEquals("tts_local_preset_id", snapshot.keys.single().name)
        assertEquals("fixture-preset-id", snapshot.values.single())
    }

    private class MemoryPreferences : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }
}
