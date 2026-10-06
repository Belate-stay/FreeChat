package com.freechat.data

import com.freechat.model.CharacterProfile
import com.freechat.model.ColorTheme
import com.freechat.model.DialogueMode
import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.model.SearchCitation
import com.freechat.model.Conversation
import com.freechat.model.MemoryEntry
import com.freechat.model.PerConvSettings
import com.freechat.sync.Merge
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Beta811RegressionTest {
    @Test fun visualRegenerationCannotDeleteLegacyStoryMemoryOrAtmosphere() {
        val conv = Conversation(id = "ui811-memory-fixture", deletedMessageIds = listOf("picture"),
            deletedVisualMessageIds = listOf("picture"))
        MessageDeletion.register(conv)
        val legacy = MemoryEntry(summary = "尚未上船", keywords = emptyList())
        assertEquals(listOf(legacy), MessageDeletion.memories(conv.id, listOf(legacy)))
        val atmosphere = PerConvSettings(atmosphere = "等船")
        assertEquals(atmosphere, MessageDeletion.atmosphere(conv.id, atmosphere))
        assertTrue(MessageDeletion.messages(conv.id, listOf(Message(id = "picture", role = Role.ASSISTANT, content = "", sceneVisualization = true))).isEmpty())
        val merged = Merge.mergeConv(conv, conv.copy(updatedAt = conv.updatedAt + 1,
            deletedVisualMessageIds = emptyList())).conv
        assertEquals(listOf("picture"), merged.deletedVisualMessageIds)
        MessageDeletion.register(conv.copy(deletedMessageIds = listOf("picture", "story")))
        assertTrue(MessageDeletion.memories(conv.id, listOf(legacy)).isEmpty())
    }
    @Test fun wechatCannotRegenerateOrGenerateScenesIncludingMissingProfiles() {
        assertFalse(CompanionFeaturePolicy.supportsNarrativeActions(null))
        assertFalse(CompanionFeaturePolicy.supportsNarrativeActions(CharacterProfile(dialogueMode = DialogueMode.WECHAT)))
        assertTrue(CompanionFeaturePolicy.supportsNarrativeActions(CharacterProfile(dialogueMode = DialogueMode.ACTION)))
        assertTrue(CompanionFeaturePolicy.supportsNarrativeActions(CharacterProfile(dialogueMode = DialogueMode.PLOT)))
        assertTrue(CompanionFeaturePolicy.supportsNarrativeActions(CharacterProfile(plotSimulation = true)))
    }

    @Test fun themePickerChangesOrderWithoutChangingPersistedOrdinals() {
        assertEquals(0, ColorTheme.BROWN.ordinal)
        assertEquals(1, ColorTheme.BLUE.ordinal)
        assertEquals(2, ColorTheme.WHITE.ordinal)
        assertEquals(ColorTheme.WHITE, ColorTheme.presetsInDisplayOrder.last())
        assertEquals(5, ColorTheme.presetsInDisplayOrder.distinct().size)
        assertEquals("浅棕", ColorTheme.BROWN.label)
        assertEquals("浅蓝", ColorTheme.BLUE.label)
        assertEquals("黑白", ColorTheme.WHITE.label)
    }

    @Test fun thoughtContinuityKeepsEarlierRoundsDuringZeroDataRetries() {
        val first = ReasoningContinuity.join("", "先分析问题")
        assertEquals(first, ReasoningContinuity.join(first, ""))
        val second = ReasoningContinuity.join(first, "结合搜索结果")
        assertTrue(second.startsWith(first))
        assertEquals("先分析问题\n\n结合搜索结果\n\n得出结论", ReasoningContinuity.join(second, "得出结论"))
    }

    @Test fun citationsMoveOutOfBodyWithoutLosingTheirTitlesOrSources() {
        val content = "结论见[官方资料](https://example.org/report)，以及 <https://example.org/more>。"
        val display = SearchPresentation.forDisplay(content, listOf(SearchCitation("官方资料", "https://example.org/report")), false)
        assertFalse(display.answer.contains("https://"))
        assertTrue(display.answer.contains("官方资料"))
        assertEquals(2, display.sources.size)
        val naked = SearchPresentation.forDisplay("参考 https://www.example.org/a，继续说明。以及 www.example.net/b。", emptyList(), false)
        assertFalse(naked.answer.contains("https://"))
        assertFalse(naked.answer.contains("www."))
        assertTrue(naked.answer.contains("，继续说明。"))
        assertEquals(2, naked.sources.size)
        val footer = SearchPresentation.forDisplay("回答。\n\n[资料](https://example.org/report)", emptyList(), false)
        assertEquals("回答。", footer.answer)
        assertEquals(1, footer.sources.size)
    }

    @Test fun explicitlyRequestedUrlsStayUsableEvenWhenAlsoSearchSources() {
        assertTrue(SearchPresentation.linksRequested("给我 Android 官网网址"))
        assertFalse(SearchPresentation.linksRequested("介绍 Android，不需要链接"))
        assertFalse(SearchPresentation.linksRequested("河南软件工程专业合适的企业和岗位？"))
        val content = "[官网](https://example.org/)"
        assertEquals(content, SearchPresentation.forDisplay(content, listOf(SearchCitation("官网", "https://example.org/")), true).answer)
        val saved = AppJson.gson.fromJson(AppJson.gson.toJson(Message(role = Role.ASSISTANT,
            content = content, answerLinksRequested = true)), Message::class.java)
        assertEquals(true, saved.answerLinksRequested)
        assertNull(AppJson.gson.fromJson("{}", Message::class.java).answerLinksRequested)
    }

    @Test fun codeAndCopyableOriginalsKeepAllUrlsAndWhitespace() {
        val protected = "````text\r\n原文 [A](https://example.org/a)\r\n```\r\n````\r\n\n~~~sh\ncurl https://example.org/a\n~~~\n`https://example.org/code`\n"
        assertEquals(protected, SearchPresentation.forDisplay(protected, emptyList(), false).answer)
        assertTrue(SearchPresentation.forDisplay(protected, emptyList(), false).sources.isEmpty())
    }

    @Test fun referenceLinksAndBalancedUrlsBecomeSourcesButImagesRemainIntact() {
        val content = "依据 [报告][ref] 和 [第二份](https://example.org/a_(b))。\n[ref]: https://example.org/report\n![图](https://example.org/image.png)"
        val display = SearchPresentation.forDisplay(content, emptyList(), false)
        assertTrue(display.answer.contains("依据 报告 和 第二份"))
        assertFalse(display.answer.contains("[ref]:"))
        assertTrue(display.answer.contains("![图](https://example.org/image.png)"))
        assertEquals(2, display.sources.size)
    }

    @Test fun partialStreamingLinkNeverFlashesItsUrl() {
        val display = SearchPresentation.forDisplay("请参考[报告](https://example.org/r", emptyList(), false)
        assertEquals("请参考报告", display.answer)
    }

    @Test fun openAiReferencesUseMultipartEditsAndFullEndpointUrlsDoNotDuplicatePaths() {
        assertEquals("https://example.org/v1/images/edits", ImageApiRequest.endpoint("https://example.org/v1/images/generations", true))
        assertEquals("https://example.org/api/v3/images/generations", ImageApiRequest.endpoint("https://example.org/api/v3", false))
        val body = ImageApiRequest.body("fixture-image", "最新场景", false,
            listOf(ImageApiRequest.Reference(byteArrayOf(1, 2, 3), "image/png")))
        assertTrue(body.contentType().toString().startsWith("multipart/form-data"))
        val buffer = Buffer()
        body.writeTo(buffer)
        val wire = buffer.readUtf8()
        assertTrue(wire.contains("name=\"image[]\""))
        assertTrue(wire.contains("最新场景"))
        assertFalse(wire.contains("data:image"))
    }

    @Test fun seedreamReceivesAllCharacterReferencesAsJson() {
        assertTrue(ImageApiRequest.usesSeedream("doubao-seedream-4-0", "", false))
        assertTrue(ImageApiRequest.usesSeedream("ep-fixture", "https://ark.cn-beijing.volces.com/api/v3", false))
        assertFalse(ImageApiRequest.usesSeedream("fixture-image", "https://proxy.example.org/v1", false))
        val body = ImageApiRequest.body("seedream", "场景", true,
            listOf(ImageApiRequest.Reference(byteArrayOf(1), "image/png"), ImageApiRequest.Reference(byteArrayOf(2), "image/jpeg")))
        val buffer = Buffer(); body.writeTo(buffer)
        val json = AppJson.gson.fromJson(buffer.readUtf8(), com.google.gson.JsonObject::class.java)
        assertEquals(2, json.getAsJsonArray("image").size())
        assertFalse(json.has("n"))
    }

    @Test fun apiErrorsDistinguishSafetyQuotaAndParametersWithoutLeakingRawText() {
        val safety = ApiFailure.fromResponse(400, """{"error":{"code":"moderation_blocked","type":"image_generation_user_error","message":"private prompt"}}""", "fixture-request")
        assertEquals(ApiFailure.Kind.SAFETY, safety.kind)
        assertEquals("fixture-request", safety.requestId)
        assertFalse(safety.message.orEmpty().contains("private"))
        assertEquals(ApiFailure.Kind.QUOTA, ApiFailure.fromResponse(429, """{"error":{"code":"insufficient_quota"}}""").kind)
        assertEquals(ApiFailure.Kind.PARAMETERS, ApiFailure.fromResponse(400, "not JSON").kind)
        assertEquals(ApiFailure.Kind.AUTH, ApiFailure.fromResponse(401, "{}").kind)
        assertEquals(ApiFailure.Kind.AUTH, ApiFailure.fromResponse(200, """{"error":{"code":"invalid_api_key"}}""").kind)
        assertEquals(ApiFailure.Kind.SERVER, ApiFailure.fromResponse(503, "{}").kind)
        assertEquals(ApiFailure.Kind.EMPTY, ApiFailure(200, "empty_image_result").kind)
    }

    @Test fun cancellableHttpKeepsStatusCodeAndRequestIdForProviderDiagnostics() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(429).addHeader("x-request-id", "fixture-429")
            .setBody("""{"error":{"code":"rate_limit_exceeded","message":"not for display"}}"""))
        server.start()
        try {
            try {
                OkHttpClient().newCall(Request.Builder().url(server.url("/images/generations")).build()).awaitText()
                fail("Expected failure")
            } catch (error: ApiFailure) {
                assertEquals(429, error.status)
                assertEquals(ApiFailure.Kind.RATE_LIMIT, error.kind)
                assertEquals("fixture-429", error.requestId)
                assertFalse(error.message.orEmpty().contains("not for display"))
            }
        } finally { server.shutdown() }
    }
}
