package com.freechat.data

import com.freechat.ui.adaptive.FloatingWindowState
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TabletFloating1205ContractTest {
    private fun source(name: String) = File("src/main/java/com/freechat/$name").readText()

    @Test fun aDirtyFormInterceptsCloseUntilTheUserExplicitlyDiscards() {
        val window = FloatingWindowState()
        var closed = 0
        var asked = 0
        window.dismiss = { closed++ }
        window.requestClose()
        assertEquals(1, closed)
        window.closeGuard = { _ -> asked++ }
        window.requestClose()
        assertEquals(1, asked)
        assertEquals("Showing a confirmation is not permission to discard", 1, closed)
        window.dismiss()
        assertEquals("Discard closes the whole window, not just one nested page", 2, closed)
    }

    @Test fun eachWindowHasOnePersistentCloseActionAndRootPagesHideBack() {
        val chrome = File("src/main/java/com/freechat/ui/adaptive/FloatingWindowChrome.kt")
        assertTrue("A floating window needs shared chrome, independent of route depth", chrome.exists())
        val code = chrome.readText()
        assertTrue(code.contains("fun BoxScope.FloatingWindowChrome("))
        assertTrue(code.contains("Icons.Filled.Close"))
        assertTrue(code.contains("fun FloatingWindowBackButton("))
        assertTrue(code.contains("LocalFloatingWindowRoot.current && root"))
        assertTrue(code.contains("fun Modifier.floatingHeaderInset()"))

        val layer = source("ui/adaptive/TabletDestinationLayer.kt")
        assertTrue(layer.contains("if (pageFloating) FloatingWindowChrome("))
        assertTrue(layer.contains("window.dismiss = onClose"))
        val main = source("MainActivity.kt")
        assertTrue(main.contains("LocalFloatingWindowRoot provides"))
        assertTrue(main.contains("onClose = { navigateBack(Screen.CHAT) }"))
    }

    @Test fun closePreservesTheExistingUnsavedCharacterAndModelConfirmation() {
        val model = source("ui/screens/ModelEditorScreen.kt")
        assertTrue(model.contains("FloatingWindowCloseHandler"))
        assertTrue(model.contains("pendingWindowClose"))
        assertTrue(model.contains("showUnsaved = true"))
        val character = source("ui/screens/CharacterSetupScreen.kt")
        assertTrue(character.contains("FloatingWindowCloseHandler"))
        assertTrue(character.contains("pendingWindowClose"))
        assertTrue(character.contains("showUnsavedChanges = true"))
        assertTrue(character.contains("showDiscardDialog = true"))
    }

    @Test fun retainedCharacterFormAndModeLockStayBoundToTheOpenedConversation() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("key(characterSetupConversationId)"))
        assertTrue(main.contains("conversationHasMessages = conversations.any"))
        assertTrue(main.contains("it.id == characterSetupConversationId && it.messageCount > 0"))
        val character = source("ui/screens/CharacterSetupScreen.kt")
        assertTrue(character.contains("conversationHasMessages: Boolean? = null"))
        assertTrue(character.contains("modeLocked = isEditing && (conversationHasMessages ?: convMessages.isNotEmpty())"))
        assertTrue("Opening a different editor from the base must honor a dirty window",
            main.contains("fun requestBaseNavigation("))
        assertTrue(main.contains("floatingWindow.requestNavigation(guardedAction)"))
        assertTrue(main.substringAfter("onOpenCharacterSetup = {").substringBefore("hazeState = chatHazeState")
            .contains("requestBaseNavigation {"))
        val opening = main.substringAfter("onOpenCharacterSetup = {").substringBefore("hazeState = chatHazeState")
        assertTrue(opening.indexOf("val requestedProfile") < opening.indexOf("requestBaseNavigation {"))
        assertTrue(opening.contains("characterSetupConversationId = requestedConversationId"))
        assertTrue(opening.contains("characterSetupInitial = requestedProfile"))
        assertTrue(main.substringAfter("onOpenNewRules = {").substringBefore("onOpenModelEditor =")
            .contains("rulesConversationId = requestedConversationId"))
    }

    @Test fun baseTouchIsAnActualInteractionPolicyNotJustAVisualSetting() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("active = destination == null || (floating && tabletUnderlyingTouch)"))
        assertTrue(main.contains("currentScreen == Screen.CHAT || floatingBaseTouchable()"))
        assertTrue(main.contains("val keepWindow = floatingBaseTouchable()"))
        assertTrue(main.contains("if (!keepWindow) navigateTo(Screen.CHAT)"))
        val layer = source("ui/adaptive/TabletDestinationLayer.kt")
        assertTrue(layer.contains("if (!pageFloating || !allowBaseTouch)"))
        assertFalse("No heavy platform drop shadow", layer.contains(".shadow("))
        assertTrue(layer.contains("neumorphShadow(shape, neumorphTone("))
    }
}
