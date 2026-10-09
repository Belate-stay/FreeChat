package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class IconSaveRestartTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun choosingAnIconIsADraftUntilSave() {
        val screen = source("ui/screens/SettingsScreen.kt")
        val picker = screen.substringAfter("SubScreen.ICON -> IconPickerPage").substringBefore("SubScreen.FEEDBACK ->")
        assertFalse("Radio selection must not save or change launcher state", picker.contains("iconManager.request"))
        assertTrue("Explicit save must be a separate action", screen.contains("saveIconAndRestart"))
    }

    @Test fun savingHasAVisibleBottomActionAndWarnsAboutOtherConversations() {
        val screen = source("ui/screens/SettingsScreen.kt")
        assertTrue(screen.contains("selectedIcon != activeIcon"))
        assertTrue(screen.contains("viewModel.hasAnyActiveGeneration()"))
        assertTrue(screen.contains("showIconRestartConfirm"))
        assertTrue(screen.contains("onConfirm"))
    }

    @Test fun restartUsesAnInternalForegroundTrampolineNotPermissionDependentAlarms() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:name=\".IconRestartActivity\""))
        assertTrue(manifest.contains("android:process=\":icon_restart\""))
        val restart = File("src/main/java/com/freechat/IconRestartActivity.kt")
        assertTrue("A separate restart process is required", restart.isFile)
        val text = restart.readText()
        assertFalse(text.contains("AlarmManager"))
        assertTrue(text.contains("MainActivity::class.java"))
        assertTrue(text.contains("Process.killProcess"))
    }

    @Test fun restartProcessDoesNotStartTheSyncEngineOrChangeIconsPrematurely() {
        val app = source("FreeChatApp.kt")
        assertTrue(app.indexOf("isRestartProcess") in 0 until app.indexOf("LocalStore.init"))
    }
}
