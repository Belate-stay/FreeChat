package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TabletNavigationContractTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun learningBlocksTheWholeWorkspaceAndBack() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("BackHandler(enabled = currentScreen == Screen.LEARNING)"))
        assertTrue(main.contains("val presentedScreen = if (effectiveScreen == Screen.LEARNING) Screen.CHARACTER_SETUP"))
        assertTrue(main.contains("visible = currentScreen == Screen.LEARNING"))
        assertTrue(main.contains("alpha = if (currentScreen == Screen.LEARNING) 0f else 1f"))
        assertTrue(main.contains("LocalPageActive provides (currentScreen != Screen.LEARNING)"))
    }

    @Test fun characterSaveTargetsOpenedConversationAndUsesFinalBusyGuard() {
        val main = source("MainActivity.kt")
        val vm = source("viewmodel/ChatViewModel.kt")
        assertTrue(main.contains("characterSetupConversationId"))
        assertTrue(main.contains("canSubmit ="))
        assertTrue(vm.contains("fun updateCharacter(conversationId: String"))
        assertTrue(vm.contains("fun isConversationBusy(conversationId: String?"))
    }

    @Test fun tabletUsesPaneDimensionsWithoutChangingDensity() {
        val chat = source("ui/screens/ChatScreen.kt")
        val host = source("ui/adaptive/TabletWorkspace.kt")
        assertTrue(chat.contains("LocalPaneSize.current"))
        assertFalse(host.contains("Density("))
        assertTrue(host.contains("maximumWindowMetrics"))
    }

    @Test fun narrowFavoritesStartsWithAnAccessibleList() {
        val main = source("MainActivity.kt")
        val favorites = main.substringAfter("workspaceKey = \"favorites\"").substringBefore("workspaceKey = \"models\"")
        assertTrue(favorites.contains("focused = if (page == Screen.FAVORITE_DETAIL) TabletPane.DETAIL else TabletPane.LIST"))
        assertTrue(favorites.contains("PaneExpandButton()"))
    }

    @Test fun supportingEditorCannotBeBypassedByChatShortcuts() {
        val main = source("MainActivity.kt")
        val chatRoute = main.substringAfter("Screen.CHAT -> ChatScreen(").substringBefore("Screen.SETTINGS ->")
        for (route in listOf("onOpenModelEditor =", "onOpenAsrEditor =", "onOpenAccount =", "onNewChat =")) {
            assertTrue("Missing live navigation guard for $route",
                chatRoute.substringAfter(route).substringBefore("},").contains("canNavigateFromChat()"))
        }
        val chat = source("ui/screens/ChatScreen.kt")
        assertTrue(chat.contains("onLongPressGenImage = if (navigationEnabled"))
        assertTrue(chat.contains("onLongPressWebSearch = if (navigationEnabled"))
    }

    @Test fun retainedTabletFormsDoNotResetWhenAPaneBecomesVisibleAgain() {
        assertTrue(source("ui/screens/CharacterSetupScreen.kt").contains("rememberPaneScrollState()"))
        assertTrue(source("ui/screens/NewRulesScreen.kt").contains("rememberPaneScrollState()"))
        val host = source("ui/adaptive/TabletWorkspace.kt")
        assertTrue(host.contains("if (LocalTabletUi.current) rememberScrollState() else rememberTopOnEntryScrollState()"))
    }

    @Test fun inactivePanesRejectKeyboardFocusAndKeys() {
        val host = source("ui/adaptive/TabletWorkspace.kt")
        assertTrue(host.contains("fun Modifier.paneInteraction(active: Boolean)"))
        assertTrue(host.contains("cancelFocusChange()"))
        assertTrue(host.contains("onPreviewKeyEvent { !active }"))
        assertTrue(host.contains("paneInteraction(active && panes.listVisible)"))
        assertTrue(host.contains("paneInteraction(active && panes.detailVisible)"))
    }

    @Test fun learningClearsFocusAndRejectsEvenAlreadyCapturedChatCallbacks() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("paneInteraction(currentScreen != Screen.LEARNING)"))
        assertTrue(main.contains("learningFocusManager.clearFocus(force = true)"))
        assertTrue(main.contains("currentScreen != Screen.LEARNING && (currentScreen == Screen.CHAT"))
        assertTrue(main.contains("tabletDisplayMode == TabletDisplayMode.EMBEDDED"))
        val chat = source("ui/screens/ChatScreen.kt")
        assertTrue(chat.substringAfter("fun sendNewMessage(text: String)").substringBefore("val timeGapMs")
            .contains("!latestPageActive.value || !canInteract()"))
        assertTrue(chat.substringAfter("onSend = send@").substringBefore("onStop =")
            .contains("return@send"))
        assertTrue(chat.substringAfter("onVoiceInput = voice@").substringBefore("onRecognizeVoice =")
            .contains("!latestPageActive.value || !canInteract()"))
    }

    @Test fun jumpToFunctionalSettingsAlsoRevealsTheHiddenFunctionalPane() {
        val character = source("ui/screens/CharacterSetupScreen.kt")
        assertTrue(character.contains("revealDetailKey = functionalRevealTick"))
        assertTrue(character.contains("functionalRevealTick++"))
        val host = source("ui/adaptive/TabletWorkspace.kt")
        assertTrue(host.contains("LaunchedEffect(revealDetailKey)"))
        assertTrue(host.contains("fullscreen = TabletLayoutPolicy.fullscreenAfterResize"))
    }

    @Test fun splitDividerFollowsTheMovingPaneInsteadOfCuttingAcrossIt() {
        val host = source("ui/adaptive/TabletWorkspace.kt")
        assertFalse(host.contains("Modifier.offset(x = natural.listWidth.dp)"))
        assertTrue(host.contains("IntOffset(rightX.value.roundToInt()"))
        assertTrue(host.contains("alpha = min(leftAlpha.value, rightAlpha.value)"))
    }

    @Test fun retainedChatEditLockDoesNotInterceptAnotherPagesBack() {
        val chat = source("ui/screens/ChatScreen.kt")
        assertTrue(chat.contains("BackHandler(enabled = latestPageActive.value && editingMessageId != null)"))
    }

    @Test fun floatingCardIsMeasuredInsideARelaxedFullWindowBox() {
        val layer = source("ui/adaptive/TabletDestinationLayer.kt")
        assertTrue(layer.contains("Box(Modifier.fillMaxSize(), propagateMinConstraints = false)"))
        assertTrue(layer.contains("LocalTabletUi provides !pageFloating"))
        assertTrue(layer.contains("if (page == destination)"))
    }

    @Test fun promptRewriteCannotSwitchToOrMutateAnotherConversation() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("listEnabled = editingPromptId == null"))
        assertTrue(main.contains("if (chatViewModel.editingPromptId.value != null) return@selectConversation"))
        assertTrue(main.contains("if (chatViewModel.editingPromptId.value != null) return@newChat"))
        assertTrue(main.contains("if (chatViewModel.editingPromptId.value != null) return@selectSearch"))
        val vm = source("viewmodel/ChatViewModel.kt").substringAfter("fun sendEditedMessage(")
            .substringBefore("// 标准模式")
        assertTrue(vm.contains("if (_messages.value.none { it.id == messageId && it.role == Role.USER }) return"))
    }

    @Test fun settingsDetoursShareAStableTransitionOwner() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("label = \"tablet_destination_route\""))
        assertTrue(main.contains("destinationContent(it)"))
    }

    @Test fun floatingShadowDoesNotTurnIntoAWhiteHaloInDarkMode() {
        val layer = source("ui/adaptive/TabletDestinationLayer.kt")
        assertFalse("Do not stack a platform shadow over the app's card material", layer.contains(".shadow("))
        assertTrue(layer.contains("neumorphTone(colors, dark, false)"))
        assertTrue(layer.contains("maxAlpha = if (dark) .04f else .025f"))
    }
}
