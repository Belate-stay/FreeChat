package com.freechat.data

import com.freechat.model.ModelInfo
import com.freechat.model.Provider
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class Beta1104ModelAndEmojiTest {
    @Test
    fun builtInModelUsesTheRequestedIdentityAndCannotBeEdited() {
        val model = BuiltInLanguageModel.create("fixture-not-a-secret")
        assertEquals("deepseek-flash", model.id)
        assertEquals("DeepSeek-V4.1-Flash", model.displayName)
        assertEquals("https://api.deepseek.com", model.apiBaseUrl)
        assertEquals("DeepSeek快速推理模型", model.description)
        assertEquals(Provider.CUSTOM, model.provider)
        assertTrue(model.supportsWebSearch)
        assertTrue(model.supportsDeepThinking)
        assertFalse("DeepSeek uses FreeChat function tools, not an unsupported hosted web_search parameter",
            model.supportsNativeSearch)
        assertFalse(ModelAccessPolicy.canEdit(model))
        val stale = model.copy(apiKey = "changed", apiBaseUrl = "https://wrong.invalid",
            displayName = "changed", supportsDeepThinking = false, isBuiltIn = false,
            deepThinkingDefault = true)
        assertEquals(model.copy(deepThinkingDefault = true), ModelAccessPolicy.withUsagePreference(model, stale))
    }

    private fun deepSeek(base: String = "https://api.deepseek.com") = ModelInfo(
        "deepseek-flash", "Fixture", Provider.CUSTOM, apiBaseUrl = base, supportsDeepThinking = true)

    @Test
    fun deepSeekThinkingSwitchUsesTheDocumentedProtocol() {
        val on = ModelThinkingPolicy.parameters(deepSeek(), true)
        assertEquals(mapOf("type" to "enabled"), on["thinking"])
        assertEquals("high", on["reasoning_effort"])
        assertFalse(on.containsKey("enable_thinking"))
        val off = ModelThinkingPolicy.parameters(deepSeek(), false)
        assertEquals(mapOf("type" to "disabled"), off["thinking"])
        assertFalse(off.containsKey("enable_thinking"))
        assertFalse(off.containsKey("reasoning_effort"))
    }

    @Test
    fun deepSeekVersionsAndCompleteEndpointsShareTheProtocolButOtherHostsKeepTheirOwnBehavior() {
        listOf("https://api.deepseek.com/v1", "https://api.deepseek.com/v1/chat/completions").forEach {
            assertEquals(mapOf("type" to "disabled"), ModelThinkingPolicy.parameters(deepSeek(it), false)["thinking"])
        }
        val custom = deepSeek("https://example.invalid")
        assertEquals(com.freechat.core.CompanionPrompts.deepThinkExtras(false), ModelThinkingPolicy.parameters(custom, false))
        assertEquals(com.freechat.core.CompanionPrompts.deepThinkExtras(true), ModelThinkingPolicy.parameters(custom, true))
    }

    @Test
    fun bothInputLayoutsAndThePanelUseTheWeChatOnlyGate() {
        val source = File("src/main/java/com/freechat/ui/components/ChatInput.kt").readText()
        assertTrue("Full and compact input must both use the same mode guard",
            Regex("if \\(emojiAvailable\\)").findAll(source).count() >= 2)
        assertTrue(source.contains("visible = emojiAvailable && showEmojiPicker"))
        assertTrue(source.contains("if (emojiAvailable) text += em"))
        assertTrue(source.contains("LaunchedEffect(emojiAvailable)"))
    }

    @Test
    fun emojiAvailabilityRequiresCompanionWeChatNotEitherNarrativeMode() {
        assertTrue(CompanionFeaturePolicy.supportsEmojiInput(isCompanion = true, narrativeSingleSend = false))
        assertFalse(CompanionFeaturePolicy.supportsEmojiInput(isCompanion = true, narrativeSingleSend = true))
        assertFalse(CompanionFeaturePolicy.supportsEmojiInput(isCompanion = false, narrativeSingleSend = false))
        assertFalse(CompanionFeaturePolicy.supportsEmojiInput(isCompanion = false, narrativeSingleSend = true))
    }

    @Test
    fun everyTextRequestWithAReasoningSwitchPassesItsActualModel() {
        val vm = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()
        assertFalse("There must be no legacy one-argument protocol helper",
            vm.contains("private fun deepThinkExtras(on: Boolean):"))
        assertTrue(vm.contains("ModelThinkingPolicy.parameters(model, on)"))
        assertTrue(vm.contains("BuiltInLanguageModel.create(BuildConfig.DEEPSEEK_API_KEY)"))
    }

    @Test
    fun companionReasoningIsSavedForTheNextThinkingToolTurn() {
        val vm = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()
        assertTrue("Companion API reasoning must not be discarded before storing assistant history",
            vm.contains("reply.optString(\"reasoning_content\").orEmpty()"))
        assertTrue("Split replies should retain the real reasoning on the first segment",
            vm.contains("reasoningContent = if (i == 0) reply.reasoningContent else \"\""))
    }

    @Test
    fun companionTransportKeepsParsingAndReasoningSeparateWithoutChangingReplySegments() {
        val parsed = com.freechat.core.CompanionReplyParser.parse("[情绪:开心]\n刚到食堂\n你想吃什么？", false)
        val body = com.freechat.core.CompanionReply(parsed.emotion, parsed.bodyLines, parsed.proactive)
        val reply = CompanionApiReply(body, "fixture model reasoning")
        assertEquals(body.segments, reply.segments)
        assertEquals(body.emotion, reply.emotion)
        assertEquals(body.proactive, reply.proactive)
        assertEquals("fixture model reasoning", reply.reasoningContent)
        assertEquals("", CompanionApiReply(body).reasoningContent)
    }
}
