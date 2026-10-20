package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TabletActionLayoutContractTest {
    private fun source(path: String) = File("src/main/java/com/freechat/ui/$path").readText()

    @Test fun standaloneTabletActionsHaveACenteredSurfaceWithABoundedWidth() {
        val layout = source("adaptive/TabletWorkspace.kt").substringAfter("fun Modifier.pageActionWidth()")
        assertTrue("Standalone actions need a shared sizing rule", layout.contains("LocalTabletUi.current"))
        assertTrue(layout.contains("wrapContentWidth(Alignment.CenterHorizontally)"))
        assertTrue(layout.contains("widthIn(max = 320.dp)"))
        assertTrue("Phone-shaped panels keep the old width", layout.contains("else fillMaxWidth()"))
    }

    @Test fun standalonePageActionsUseTheRuleWithoutNarrowingTheirForms() {
        val callers = mapOf(
            "AccountScreen.kt" to 1,
            "FeedbackPage.kt" to 1,
            "ModelEditorScreen.kt" to 4,
            "FavoritesDetailScreen.kt" to 1,
            "SettingsScreen.kt" to 2,
            "CharacterSetupScreen.kt" to 3,
            "DialogueModeScreen.kt" to 1,
            "MiMoVoiceSettingsPage.kt" to 2,
        )
        callers.forEach { (file, count) ->
            val page = source("screens/$file")
            assertEquals(file, count, Regex("\\.pageActionWidth\\(\\)").findAll(page).count())
            assertTrue("The form must retain its usable width: $file", page.contains(".fillMaxWidth()"))
        }
    }
}
