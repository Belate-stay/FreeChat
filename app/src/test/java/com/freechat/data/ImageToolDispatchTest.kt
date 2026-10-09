package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ImageToolDispatchTest {
    private val vm get() = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()

    @Test fun nativeToolSupportIsNotDisabledForEveryMiMoModel() {
        val policy = vm.substringAfter("private fun supportsFunctionTools(").substringBefore("private fun blockFunctionTools(")
        assertFalse("MiMo supports tool calls; only an actual rejected request should disable them",
            policy.contains("Provider.XIAOMI"))
    }

    @Test fun toolRejectionFallbackIsIndependentOfTheSearchSwitch() {
        val fallback = vm.substringAfter("apiFailure?.let { failure ->").substringBefore("throw failure")
        assertTrue("Every tool rejection needs the text-protocol fallback", fallback.contains("unsupportedTools(failure)"))
        assertTrue("Search being off must not disable compatibility", fallback.contains("blockFunctionTools(model)"))
        assertFalse("The fallback cannot require a search tool", fallback.contains("tools.any"))
    }

    @Test fun imageToolCallsUseTheSameContextAwareImageExecutor() {
        val executor = vm.substringAfter("private suspend fun executeToolCall(").substringBefore("private suspend fun executeImageTask(")
        assertTrue("Native and compatible calls must retain prompt/reference context", executor.contains("ImageTaskRouting.forTool("))
        assertTrue(executor.contains("executeImageTask("))
    }

    @Test fun ordinaryToolPayloadsAreHiddenAndExecutedForAPIsWithoutNativeTools() {
        assertTrue(vm.contains("PlainToolProtocol.instruction("))
        assertTrue(vm.contains("PlainToolProtocol.parse("))
        assertTrue(vm.contains("PlainToolProtocol.visibleContent("))
    }

    @Test fun nonStreamingRepliesAreReadBeforeTheSseReaderConsumesTheBody() {
        val streaming = vm.substringAfter("private suspend fun callDeepSeekApiStreaming(")
            .substringBefore("// ========== 生图 API")
        assertTrue("Gateways may honor the request but return ordinary JSON", streaming.contains("nonStreamingBody"))
        assertTrue(streaming.indexOf("nonStreamingBody") < streaming.indexOf("while (!source.exhausted())"))
    }

    @Test fun visionAlsoKeepsOrdinaryJsonRepliesAndCancellation() {
        val vision = vm.substringAfter("private suspend fun callVisionChat(").substringBefore("// ========== 文件理解")
        assertTrue(vision.contains("nonStreamingBody"))
        assertTrue(vision.contains("catch (cancelled: CancellationException)"))
    }

    @Test fun regenerationRetainsTheImageTaskEvenWithoutImageWordsInTheUserPrompt() {
        val regenerate = vm.substringAfter("fun regenerate(aiIndex:").substringBefore("private fun separateCitationLinks(")
        assertTrue(regenerate.contains("imagePrompt"))
        assertTrue(regenerate.contains("ImageTaskRouting.regeneration(text,"))
    }
}
