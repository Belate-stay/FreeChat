package com.freechat.data

import com.freechat.core.CompanionReply
import com.freechat.core.CompanionReplyParser
import com.freechat.model.SearchCitation
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CompanionRequestRegressionTest {
    private fun message(fields: String) = JsonParser.parseString(fields).asJsonObject
    private val original = CompanionApiReply(
        CompanionReply("平静", listOf("原有的完整正文"), listOf(SearchCitation("来源", "https://example.org"))),
        "原有推理"
    )

    @Test fun explicitNullToolCallsDoNotDiscardSuccessfulNarrativeText() {
        val root = message("""{"choices":[{"message":{"role":"assistant","content":"描写。\n「台词。」","reasoning_content":"推理","tool_calls":null},"finish_reason":"stop"}]}""")
        val reply = root.getAsJsonArray("choices")[0].asJsonObject.getAsJsonObject("message")
        assertNull(ChatToolCalls.read(reply))
        assertEquals(listOf("描写。\n「台词。」"), CompanionReplyParser.parse(reply["content"].asString, true).bodyLines)
        assertEquals("推理", reply["reasoning_content"].asString)
    }

    @Test fun missingAndEmptyToolCallsAreBothAccepted() {
        assertNull(ChatToolCalls.read(null))
        assertNull(ChatToolCalls.read(message("""{"content":"正常回复"}""")))
        assertEquals(0, ChatToolCalls.read(message("""{"tool_calls":[]}"""))!!.size())
    }

    @Test fun actualToolsAndStreamArgumentFragmentsRemainIntact() {
        val whole = message("""{"tool_calls":[{"id":"call-1","type":"function","function":{"name":"search","arguments":"{\"query\":\"天气\"}"}}]}""")
        val tool = ChatToolCalls.read(whole)!![0].asJsonObject
        assertEquals("call-1", tool["id"].asString)
        assertEquals("search", tool.getAsJsonObject("function")["name"].asString)
        assertEquals("""{"query":"天气"}""", tool.getAsJsonObject("function")["arguments"].asString)
        val delta = message("""{"tool_calls":[{"index":0,"function":{"arguments":"片段"}}]}""")
        assertEquals("片段", ChatToolCalls.read(delta)!![0].asJsonObject.getAsJsonObject("function")["arguments"].asString)
    }

    @Test fun nullStreamToolFieldsDoNotInterruptTextChunks() {
        val delta = message("""{"content":"继续写出的正文","tool_calls":null}""")
        assertNull(ChatToolCalls.read(delta))
        assertEquals("继续写出的正文", delta["content"].asString)
    }

    @Test fun unrelatedToolFieldTypesAreNotBlindlyCastToAnArray() {
        for (value in listOf("{}", "false", "\"none\"")) {
            assertNull(ChatToolCalls.read(message("""{"content":"正文","tool_calls":$value}""")))
        }
    }

    @Test fun failedLengthRefinementKeepsTextReasoningAndSources() = runBlocking {
        val selected = original.refineOrKeep { throw IOException("fixture read failure") }
        assertSame(original, selected)
        assertEquals(listOf("原有的完整正文"), selected.segments)
        assertEquals("原有推理", selected.reasoningContent)
        assertEquals(original.sources, selected.sources)
    }

    @Test fun emptyOrSilentLengthRefinementDoesNotEraseOriginal() = runBlocking {
        for (candidate in listOf(
            CompanionApiReply(CompanionReply("", emptyList())),
            CompanionApiReply(CompanionReply("", listOf(" "))),
            CompanionApiReply(CompanionReply("", emptyList(), silence = true))
        )) assertSame(original, original.refineOrKeep { candidate })
    }

    @Test fun successfulLengthRefinementReplacesTheOriginalOnce() = runBlocking {
        var attempts = 0
        val expanded = original.copy(body = original.body.copy(segments = listOf("补写后的完整正文")))
        assertSame(expanded, original.refineOrKeep { attempts++; expanded })
        assertEquals(1, attempts)
    }

    @Test fun cancellationNeverFallsBackToPublishingAnOldReply() = runBlocking {
        try {
            original.refineOrKeep { throw CancellationException("fixture cancel") }
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertEquals("fixture cancel", expected.message)
        }
    }

    @Test fun socketFailureAfterJobCancellationStillPropagatesCancellation() = runBlocking {
        val task = async {
            original.refineOrKeep {
                currentCoroutineContext().cancel()
                throw IOException("socket was cancelled")
            }
            fail("Cancelled generation must not publish the original reply")
        }
        try { task.await(); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertTrue(task.isCancelled)
    }
}
