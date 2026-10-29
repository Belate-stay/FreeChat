package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Device checks separately exercise actual service, delivery and notification navigation. */
class BackgroundReply1219SourceTest {
    private fun source(path: String) = File("src/main/$path").readText()

    @Test fun notificationsAreConversationScopedAndPermissionFailureCannotBreakDelivery() {
        val service = source("java/com/freechat/ReplyService.kt")
        assertTrue(service.contains("conversationId: String"))
        assertTrue(service.contains("EXTRA_CONVERSATION_ID"))
        assertTrue(service.contains("nm.notify(conversationId, REPLY_NOTIF_ID"))
        assertTrue(service.contains(".setOnlyAlertOnce(true)"))
        assertTrue(service.contains("POST_NOTIFICATIONS"))
        assertTrue(service.contains("areNotificationsEnabled()"))
    }

    @Test fun groupsAndNonStreamingWaitsUseTheSharedBackgroundLifecycle() {
        val vm = source("java/com/freechat/viewmodel/ChatViewModel.kt")
        val group = vm.substringAfter("private suspend fun runGroupTurn(").substringBefore("private suspend fun groupModelReply(")
        assertTrue(group.contains("notifyReplyIfBackground(convId"))
        assertTrue(vm.contains("keepAliveHeartbeat"))
        val state = vm.substringAfter("fun onAppForegroundChanged(").substringBefore("private fun markGenerationStarted(")
        assertFalse(state.contains("cancel("))
        assertFalse(state.contains("stopGeneration("))
    }

    @Test fun reopeningAndBackCannotCreateAnUnrelatedGenerationOwner() {
        val manifest = source("AndroidManifest.xml")
        assertTrue(manifest.contains("android:launchMode=\"singleTop\""))
        val main = source("java/com/freechat/MainActivity.kt")
        assertTrue(main.contains("override fun onNewIntent("))
        assertTrue(main.contains("ReplyService.EXTRA_CONVERSATION_ID"))
        assertTrue(main.contains("moveTaskToBack(true)"))
        assertFalse(main.contains("BackHandler(enabled = currentScreen == Screen.LEARNING) { }"))
    }

    @Test fun personaLearningAndAcceptedReplyMemoryShareTheTaskLifetime() {
        val vm = source("java/com/freechat/viewmodel/ChatViewModel.kt")
        assertTrue(vm.contains("backgroundWorkCount"))
        assertTrue(vm.contains("launchBackgroundWork(resolveModels(convId, character))"))
        assertTrue(vm.contains("launchBackgroundWork(models)"))
        assertTrue(vm.contains("generatePersonaPrompt(profile: CharacterProfile, personaOnly: Boolean = false): CharacterProfile = withBackgroundWork"))
        assertTrue(vm.contains("regeneratePersona(old: CharacterProfile, new: CharacterProfile, personaOnly: Boolean = false): CharacterProfile = withBackgroundWork"))
    }
    @Test fun simultaneousConversationsCannotShareJobsCallsOrTemporaryGlobalSettings() {
        val vm = source("java/com/freechat/viewmodel/ChatViewModel.kt")
        assertTrue(vm.contains("standardJobs[convId]"))
        assertTrue(vm.contains("generationCalls[convId]"))
        assertFalse(vm.contains("private var streamJob"))
        assertTrue(vm.contains("activeRoundStarts[convId]"))
        assertFalse(vm.contains("private var activeRoundConvId"))
        assertTrue(vm.contains("requestPreferences.asContextElement(applyPerConvSettings(convId))"))
        val preferences = vm.substringAfter("private fun applyPerConvSettings(").substringBefore("// ========== 问候语")
        assertFalse(Regex("_(enableWebSearch|showThinking|tempMode|lengthMode|autoSummarizeMemory)\\.value\\s*=").containsMatchIn(preferences))
    }

}
