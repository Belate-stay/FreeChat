package com.freechat.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.freechat.i18n.En
import com.freechat.i18n.ZhCN
import com.freechat.i18n.ZhTW
import com.freechat.model.TabletDisplayMode
import com.freechat.model.TabletFloatingSide
import com.freechat.ui.adaptive.tabletDisplayModeLabel
import com.freechat.ui.adaptive.tabletDisplayOptimization
import com.freechat.ui.adaptive.tabletFloatingSideLabel
import com.freechat.ui.adaptive.tabletFloatingPosition
import com.freechat.ui.adaptive.tabletUnderlyingTouch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class TabletDisplayPreferencesTest {
    private val modeKey = stringPreferencesKey("tablet_display_mode")
    private val sideKey = stringPreferencesKey("tablet_floating_side")
    private val touchKey = booleanPreferencesKey("tablet_underlying_touch")

    @After fun clearPendingWrites() {
        SettingsRepository.releasePendingLocal(SettingsRepository.pendingLocalSnapshot())
    }

    @Test fun missingPreferencesUseEmbeddedDisplayAndBothFloatingSides() = runBlocking {
        val repository = SettingsRepository(InMemoryPreferences())
        assertEquals(TabletDisplayMode.EMBEDDED, repository.tabletDisplayMode.first())
        assertEquals(TabletFloatingSide.BOTH, repository.tabletFloatingSide.first())
        assertTrue(repository.tabletUnderlyingTouch.first())
    }

    @Test fun unrecognizedPreferencesFallBackWithoutThrowing() = runBlocking {
        val repository = SettingsRepository(InMemoryPreferences(preferencesOf(
            modeKey to "future-mode",
            sideKey to "future-side",
        )))
        assertEquals(TabletDisplayMode.EMBEDDED, repository.tabletDisplayMode.first())
        assertEquals(TabletFloatingSide.BOTH, repository.tabletFloatingSide.first())
    }

    @Test fun modeAndSidePersistByStableNamesAndReloadAsTypedPreferences() = runBlocking {
        val store = InMemoryPreferences()
        val repository = SettingsRepository(store)
        repository.saveTabletDisplayMode(TabletDisplayMode.FLOATING)
        repository.saveTabletFloatingSide(TabletFloatingSide.RIGHT)

        assertEquals("FLOATING", store.data.first()[modeKey])
        assertEquals("RIGHT", store.data.first()[sideKey])
        val reloaded = SettingsRepository(store)
        assertEquals(TabletDisplayMode.FLOATING, reloaded.tabletDisplayMode.first())
        assertEquals(TabletFloatingSide.RIGHT, reloaded.tabletFloatingSide.first())
    }

    @Test fun returningToEmbeddedPreservesTheChosenFloatingSide() = runBlocking {
        val repository = SettingsRepository(InMemoryPreferences())
        repository.saveTabletFloatingSide(TabletFloatingSide.LEFT)
        repository.saveTabletUnderlyingTouch(false)
        repository.saveTabletDisplayMode(TabletDisplayMode.FLOATING)
        repository.saveTabletDisplayMode(TabletDisplayMode.EMBEDDED)
        assertEquals(TabletDisplayMode.EMBEDDED, repository.tabletDisplayMode.first())
        assertEquals(TabletFloatingSide.LEFT, repository.tabletFloatingSide.first())
        assertFalse(repository.tabletUnderlyingTouch.first())
        repository.saveTabletDisplayMode(TabletDisplayMode.FLOATING)
        assertEquals(TabletFloatingSide.LEFT, repository.tabletFloatingSide.first())
        assertFalse(repository.tabletUnderlyingTouch.first())
    }

    @Test fun underlyingTouchPersistsBothValuesAndReloads() = runBlocking {
        val store = InMemoryPreferences()
        val repository = SettingsRepository(store)
        repository.saveTabletUnderlyingTouch(false)
        assertEquals(false, store.data.first()[touchKey])
        assertFalse(SettingsRepository(store).tabletUnderlyingTouch.first())
        repository.saveTabletUnderlyingTouch(true)
        assertEquals(true, store.data.first()[touchKey])
        assertTrue(SettingsRepository(store).tabletUnderlyingTouch.first())
    }

    @Test fun tabletDisplayLabelsFollowTheSelectedAppLanguage() {
        assertEquals("平板显示优化", ZhCN.tabletDisplayOptimization)
        assertEquals("平板顯示最佳化", ZhTW.tabletDisplayOptimization)
        assertEquals("Tablet display", En.tabletDisplayOptimization)
        assertEquals("悬浮", ZhCN.tabletDisplayModeLabel(TabletDisplayMode.FLOATING))
        assertEquals("嵌入", ZhCN.tabletDisplayModeLabel(TabletDisplayMode.EMBEDDED))
        assertEquals("懸浮", ZhTW.tabletDisplayModeLabel(TabletDisplayMode.FLOATING))
        assertEquals("嵌入", ZhTW.tabletDisplayModeLabel(TabletDisplayMode.EMBEDDED))
        assertEquals("Floating", En.tabletDisplayModeLabel(TabletDisplayMode.FLOATING))
        assertEquals("Embedded", En.tabletDisplayModeLabel(TabletDisplayMode.EMBEDDED))
        assertEquals("悬浮位置", ZhCN.tabletFloatingPosition)
        assertEquals("懸浮位置", ZhTW.tabletFloatingPosition)
        assertEquals("Floating position", En.tabletFloatingPosition)
        assertEquals("底层触控", ZhCN.tabletUnderlyingTouch)
        assertEquals("底層觸控", ZhTW.tabletUnderlyingTouch)
        assertEquals("Underlying touch", En.tabletUnderlyingTouch)
        assertEquals("Both sides", En.tabletFloatingSideLabel(TabletFloatingSide.BOTH))
        assertEquals("左侧悬浮", ZhCN.tabletFloatingSideLabel(TabletFloatingSide.LEFT))
        assertEquals("右側懸浮", ZhTW.tabletFloatingSideLabel(TabletFloatingSide.RIGHT))
    }

    private class InMemoryPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }
}
