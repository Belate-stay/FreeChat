package com.freechat.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.freechat.data.SettingsRepository
import com.freechat.model.ColorTheme
import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

class ThemeSyncRegressionTest {
    @Before
    @After
    fun clearPendingTestWrites() {
        SettingsRepository.releasePendingLocal(SettingsRepository.pendingLocalSnapshot())
    }

    @Test
    fun rootThemeReadsOneSnapshotAndSettersDoNotOptimisticallyRaceTheStore() {
        val root = File("src/main/java/com/freechat/MainActivity.kt").readText()
        val vm = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()
        assertTrue("The global palette must consume a single theme/color snapshot",
            root.contains("chatViewModel.themeSelection.collectAsState()"))
        assertFalse("Do not replace theme state before the async persistence acknowledges it",
            vm.contains("_colorTheme.value = ct"))
        assertFalse("The color pigment cannot race a separate theme collector",
            vm.contains("settingsRepo.customColorArgb.collect"))
    }

    @Test
    fun userChoiceDuringAnInFlightRemoteApplyIsStillALocalWriteAndSurvivesOldRemoteData() = runBlocking {
        val store = RecordingPreferences(preferencesOf(SettingsRepository.KEY_COLOR_THEME to ColorTheme.BLUE.ordinal))
        val repo = SettingsRepository(store)
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val remoteJob = launch {
            SettingsRepository.suspendApply {
                entered.complete(Unit)
                resume.await()
                repo.saveColorTheme(ColorTheme.WHITE.ordinal)
            }
        }
        entered.await()
        repo.saveColorTheme(ColorTheme.PINE.ordinal)
        resume.complete(Unit)
        remoteJob.join()
        assertEquals("A user's write must not be mistaken for a remote write while synchronization is running",
            ColorTheme.PINE.ordinal, repo.currentPreferences()[SettingsRepository.KEY_COLOR_THEME])
        assertTrue(SettingsRepository.pendingLocalSnapshot().containsKey(SettingsRepository.KEY_COLOR_THEME.name))
    }

    @Test
    fun selectingAnExistingCustomColorProtectsItsPigmentEvenIfOnlyThemeIdentityChanged() = runBlocking {
        val chosen = 0xFF927EBB.toInt()
        val repo = SettingsRepository(RecordingPreferences(preferencesOf(
            SettingsRepository.KEY_COLOR_THEME to ColorTheme.BLUE.ordinal,
            SettingsRepository.KEY_CUSTOM_COLOR_ARGB to chosen
        )))
        repo.saveCustomTheme(chosen)
        // The color itself didn't change locally; it must still be protected with its theme.
        SettingsRepository.suspendApply {
            SettingsBridge.applyRemote(repo, remoteTheme("CUSTOM", 0xFF346C98.toInt()))
        }
        assertEquals(ColorTheme.CUSTOM.ordinal, repo.currentPreferences()[SettingsRepository.KEY_COLOR_THEME])
        assertEquals("The theme and pigment form one pending user choice", chosen,
            repo.currentPreferences()[SettingsRepository.KEY_CUSTOM_COLOR_ARGB])
    }

    @Test
    fun repeatedRemotePineNeverEmitsTheWebBlackWhiteFallback() = runBlocking {
        val store = RecordingPreferences(preferencesOf(SettingsRepository.KEY_COLOR_THEME to ColorTheme.PINE.ordinal))
        val repo = SettingsRepository(store)
        val remote = remoteTheme("PINE")
        repeat(3) { SettingsRepository.suspendApply { SettingsBridge.applyRemote(repo, remote) } }
        assertTrue("Every persisted theme must stay PINE, not WHITE then PINE",
            store.writes.all { it[SettingsRepository.KEY_COLOR_THEME] == ColorTheme.PINE.ordinal })
    }

    @Test
    fun customThemeAndItsNewColorAreStoredInOneSnapshot() = runBlocking {
        val store = RecordingPreferences(preferencesOf(
            SettingsRepository.KEY_COLOR_THEME to ColorTheme.BLUE.ordinal,
            SettingsRepository.KEY_CUSTOM_COLOR_ARGB to 0xFF346C98.toInt()
        ))
        val repo = SettingsRepository(store)
        val desired = 0xFF927EBB.toInt()
        SettingsRepository.suspendApply { SettingsBridge.applyRemote(repo, remoteTheme("CUSTOM", desired)) }
        assertTrue("There must be no intermediate WHITE theme or CUSTOM paired with an old color",
            store.writes.all { it[SettingsRepository.KEY_COLOR_THEME] == ColorTheme.CUSTOM.ordinal &&
                it[SettingsRepository.KEY_CUSTOM_COLOR_ARGB] == desired })
    }

    @Test
    fun blackWhiteSnapshotExplicitlyReplacesASpecialTheme() = runBlocking {
        val repo = SettingsRepository(RecordingPreferences(preferencesOf(
            SettingsRepository.KEY_COLOR_THEME to ColorTheme.WHITE.ordinal
        )))
        val snapshot = SettingsBridge.snapshot(repo)
        assertEquals("WHITE", snapshot.getAsJsonObject(SettingsBridge.ANDROID_PREFS)
            ?.get("androidColorTheme")?.asString)
    }

    private fun remoteTheme(name: String, argb: Int? = null) = JsonObject().apply {
        addProperty("themeFamily", "WHITE")
        add(SettingsBridge.ANDROID_PREFS, JsonObject().apply {
            addProperty("androidColorTheme", name)
            argb?.let { addProperty("customColorArgb", it) }
        })
    }

    private class RecordingPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        val writes = mutableListOf<Preferences>()
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            writes += next
            state.value = next
            return next
        }
    }

    @Test
    fun selectingBlackWhiteDoesNotCarryAnOldAndroidAccentIntoTheNextPull() {
        val remote = JsonObject().apply {
            addProperty("themeFamily", "WHITE")
            addProperty("sidebarWidth", 288)
            add(SettingsBridge.ANDROID_PREFS, JsonObject().apply {
                addProperty("androidColorTheme", "PINE")
                addProperty("futurePreference", true)
            })
        }
        // A legacy/current snapshot omits androidColorTheme for the common themes.
        val ours = JsonObject().apply {
            addProperty("themeFamily", "WHITE")
            add(SettingsBridge.ANDROID_PREFS, JsonObject().apply { addProperty("fontSize", 18) })
        }

        val merged = SettingsBridge.unionForPush(remote, ours)
        val android = merged.getAsJsonObject(SettingsBridge.ANDROID_PREFS)
        assertTrue("An old PINE override must not undo the newly selected black/white theme",
            !android.has("androidColorTheme") || android.get("androidColorTheme").asString == "WHITE")
        assertEquals(288, merged.get("sidebarWidth").asInt)
        assertTrue(android.get("futurePreference").asBoolean)
        assertEquals(18, android.get("fontSize").asInt)
    }

    @Test
    fun explicitNewAndroidAccentReplacesThePreviousOneAndKeepsUnknownPreferences() {
        val remote = JsonObject().apply {
            add(SettingsBridge.ANDROID_PREFS, JsonObject().apply {
                addProperty("androidColorTheme", "CORAL")
                addProperty("futurePreference", true)
            })
        }
        val ours = JsonObject().apply {
            addProperty("themeFamily", "WHITE")
            add(SettingsBridge.ANDROID_PREFS, JsonObject().apply { addProperty("androidColorTheme", "PINE") })
        }
        val android = SettingsBridge.unionForPush(remote, ours).getAsJsonObject(SettingsBridge.ANDROID_PREFS)
        assertEquals("PINE", android.get("androidColorTheme").asString)
        assertTrue(android.get("futurePreference").asBoolean)
    }
}
