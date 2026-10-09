package com.freechat.data

import com.freechat.i18n.En
import com.freechat.i18n.ZhCN
import com.freechat.i18n.ZhTW
import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.ui.components.ChatScrollGeometry
import com.freechat.ui.components.ChatScrollItem
import com.freechat.ui.components.ImageDisplayPolicy
import com.freechat.ui.components.QuickLocatePolicy
import org.junit.Assert.*
import org.junit.Test

class Beta99RegressionTest {
    private val endpoint = "https://image.example.org/v1/images/generations"
    private fun message(id: String, favorite: Boolean = true) = Message(id = id,
        role = Role.ASSISTANT, content = "完整剧情 $id", reasoningContent = "完整推理 $id", favorited = favorite,
        imageUrls = listOf("https://example.org/$id.png"), timestamp = 1234L)

    @Test fun removingFavoriteRetainsEveryMessageAndItsContents() {
        val before = listOf(message("a"), message("b"), message("c", false), message("d"))
        val after = FavoritePolicy.unfavoriteRuns(before, setOf("a"))
        assertEquals(before.size, after.size)
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(before.map { it.copy(favorited = false) }, after.map { it.copy(favorited = false) })
        assertFalse(after[0].favorited)
        assertFalse(after[1].favorited)
        assertTrue(after[3].favorited)
    }

    @Test fun removingMultipleRunsNeverLeavesANewFavoriteHead() {
        val before = listOf(message("a"), message("b"), message("c", false), message("d"), message("e"))
        assertTrue(FavoritePolicy.unfavoriteRuns(before, setOf("b", "d")).none { it.favorited })
    }

    @Test fun staleOrEmptyFavoriteSelectionIsANonDestructiveNoOp() {
        val before = listOf(message("a", false), message("b"))
        assertSame(before, FavoritePolicy.unfavoriteRuns(before, setOf("removed", "a")))
        assertSame(before, FavoritePolicy.unfavoriteRuns(before, emptySet()))
    }

    @Test fun unfavoritingRetainsLiveStreamAndSceneVisualMetadata() {
        val live = message("live").copy(isStreaming = true, content = "正在回复的最新内容", sceneVisualization = true)
        assertEquals(live.copy(favorited = false), FavoritePolicy.unfavoriteRuns(listOf(live), setOf("live")).single())
    }

    @Test fun nullUrlDoesNotMaskGptImageBase64() {
        val source = ImageApiResponse.parse("""{"data":[{"url":null,"b64_json":"AQID"}]}""", endpoint).single()
        assertArrayEquals(byteArrayOf(1, 2, 3), (source as ImageApiResponse.Source.Encoded).bytes)
    }

    @Test fun base64IsPreferredToAnExpiringRemoteLink() {
        val source = ImageApiResponse.parse("""{"data":[{"url":"https://example.org/a.png","b64_json":"AQID"}]}""", endpoint).single()
        assertTrue(source is ImageApiResponse.Source.Encoded)
    }

    @Test fun urlMayContainADataImageRatherThanHttp() {
        val source = ImageApiResponse.parse("""{"data":[{"url":"data:image/png;base64,AQID"}]}""", endpoint).single()
        assertArrayEquals(byteArrayOf(1, 2, 3), (source as ImageApiResponse.Source.Encoded).bytes)
    }

    @Test fun relativeProviderLinkIsResolvedAgainstActualEndpoint() {
        assertEquals(ImageApiResponse.Source.Remote("https://image.example.org/assets/a.png?token=test"),
            ImageApiResponse.parse("""{"data":[{"url":"/assets/a.png?token=test"}]}""", endpoint).single())
    }

    @Test fun completedImageEventHasTheSameBase64Contract() {
        val source = ImageApiResponse.parse("""{"type":"image_generation.completed","b64_json":"AQID"}""", endpoint).single()
        assertTrue(source is ImageApiResponse.Source.Encoded)
    }

    @Test fun invalidBase64CanFallBackToAValidRemoteImage() {
        val source = ImageApiResponse.parse("""{"data":[{"url":"https://example.org/a.png","b64_json":"invalid!"}]}""", endpoint).single()
        assertTrue(source is ImageApiResponse.Source.Remote)
    }

    @Test fun whitespaceInBase64IsAcceptedButTextDataUrisAreNot() {
        assertArrayEquals(byteArrayOf(1, 2, 3), ImageApiResponse.decode(" A Q I D\n"))
        assertNull(ImageApiResponse.decode("data:text/html;base64,AQID"))
        assertNull(ImageApiResponse.decode(""))
    }

    @Test fun taskAcknowledgementOrNullDataIsNotReportedAsSuccess() {
        for (body in listOf("""{"status":"processing","task_id":"pending"}""",
            """{"data":null}""", """{"data":[{"url":null,"b64_json":null}]}""", """{"data":[]}""")) {
            val failure = runCatching { ImageApiResponse.parse(body, endpoint) }.exceptionOrNull()
            assertTrue(failure is ApiFailure)
            assertEquals(ApiFailure.Kind.EMPTY, (failure as ApiFailure).kind)
        }
    }

    @Test fun providerRejectionKeepsItsSpecificReasonInsteadOfGuessingSafety() {
        val error = runCatching {
            ImageApiResponse.parse("""{"error":{"code":"insufficient_quota","message":"余额不足"}}""", endpoint)
        }.exceptionOrNull() as ApiFailure
        assertEquals(ApiFailure.Kind.QUOTA, error.kind)
    }

    @Test fun loginPageOrInvalidJsonIsNotAnImage() {
        assertEquals(ApiFailure.Kind.ENDPOINT, (runCatching {
            ImageApiResponse.parse("<html>login</html>", endpoint)
        }.exceptionOrNull() as ApiFailure).kind)
        assertEquals(ApiFailure.Kind.EMPTY, (runCatching {
            ImageApiResponse.parse("not json", endpoint)
        }.exceptionOrNull() as ApiFailure).kind)
    }

    @Test fun unsafeSchemesOrEmbeddedCredentialsAreNotImageLinks() {
        for (url in listOf("file:///etc/passwd", "javascript:alert(1)", "https://key:secret@example.org/a.png")) {
            assertNull(ImageApiResponse.remote(url, endpoint))
        }
    }

    @Test fun generatedImagesAlwaysHaveAValidReservedAspectRatio() {
        assertEquals(1f, ImageDisplayPolicy.aspectRatio(0, 0), 0f)
        assertEquals(1f, ImageDisplayPolicy.aspectRatio(-1, 100), 0f)
        assertEquals(2f / 3f, ImageDisplayPolicy.aspectRatio(1024, 1536), 0.001f)
        assertTrue(ImageDisplayPolicy.isLocal("/data/user/0/com.freechat/files/gen.png"))
        assertFalse(ImageDisplayPolicy.isLocal("https://example.org/a.png"))
    }

    @Test fun oldImageOnlyRecordsWithoutMediaDoNotRenderAsAnEmptyReply() {
        val missing = message("old").copy(content = "", imageUrls = emptyList(), modelName = "ChatGPT-Image-2.5")
        assertTrue(ImageDisplayPolicy.hasMissingResult(missing))
        assertTrue(ImageDisplayPolicy.hasMissingResult(missing.copy(sceneVisualization = true,
            content = "当前场景图 · 仅提供视觉呈现，不写入剧情记忆", modelName = "Custom")))
        assertFalse(ImageDisplayPolicy.hasMissingResult(missing.copy(imageUrls = listOf("https://example.org/a.png"))))
    }

    @Test fun missingImageNoticeNeverReplacesThinkingTextErrorsOrUserMessages() {
        val missing = message("old").copy(content = "", imageUrls = emptyList(), modelName = "ChatGPT-Image-2.5")
        assertFalse(ImageDisplayPolicy.hasMissingResult(missing.copy(isStreaming = true)))
        assertFalse(ImageDisplayPolicy.hasMissingResult(missing.copy(failed = true)))
        assertFalse(ImageDisplayPolicy.hasMissingResult(missing.copy(role = Role.USER)))
        assertFalse(ImageDisplayPolicy.hasMissingResult(missing.copy(modelName = "DeepSeek")))
        assertFalse(ImageDisplayPolicy.hasMissingResult(missing.copy(content = "正常文字回复")))
    }

    @Test fun dateAtSeekTargetMatchesTheScrolledConversationRow() {
        val rows = listOf(ChatScrollItem("time", 30f, 1000L), ChatScrollItem("a", 400f, 2000L),
            ChatScrollItem("b", 1000f, 3000L))
        val g = ChatScrollGeometry(rows.map { it.estimatedHeight }, 0, 0, 0, 300)
        assertEquals(2000L, QuickLocatePolicy.timestampAt(rows, g.target(0.2f).index))
        assertEquals(3000L, QuickLocatePolicy.timestampAt(rows, g.target(1f).index))
        assertNull(QuickLocatePolicy.timestampAt(emptyList(), 0))
        assertNull(QuickLocatePolicy.timestampAt(listOf(ChatScrollItem("invalid", 10f)), 0))
    }

    @Test fun thumbDoesNotJumpWhenGrabbedOffCenter() {
        assertEquals(5f, QuickLocatePolicy.grabOffset(105f, 100f, 26f), 0f)
        assertEquals(-8f, QuickLocatePolicy.grabOffset(92f, 100f, 26f), 0f)
        assertEquals(0f, QuickLocatePolicy.grabOffset(160f, 100f, 26f), 0f)
        assertEquals(0f, QuickLocatePolicy.grabOffset(Float.NaN, 100f, 26f), 0f)
    }

    @Test fun shortLandscapeRailStillReachesBothEndsAndContainsTheGrownThumb() {
        for (height in listOf(48f, 96f, 220f)) {
            val inset = QuickLocatePolicy.insetForHeight(height, 24f)
            assertTrue(height - inset * 2f > 0f)
            assertEquals(0f, QuickLocatePolicy.trackFraction(inset, height, inset), 0f)
            assertEquals(1f, QuickLocatePolicy.trackFraction(height - inset, height, inset), 0f)
            assertTrue(QuickLocatePolicy.thumbHalfHeight(inset, 16f) * 1.22f < inset)
        }
        assertEquals(24f, QuickLocatePolicy.insetForHeight(220f, 24f), 0f)
        assertEquals(0f, QuickLocatePolicy.insetForHeight(Float.NaN, 24f), 0f)
    }

    @Test fun newPresentationStringsAreAvailableInAllLanguages() {
        for (s in listOf(ZhCN, ZhTW, En)) {
            assertTrue(s.quickLocateTimeFormat.isNotBlank())
            assertTrue(s.unfavoriteKeepsOriginal.isNotBlank())
            assertTrue(s.generatedImageLoading.isNotBlank())
            assertTrue(s.generatedImageUnavailable.isNotBlank())
            assertTrue(s.generatedImageMissing.isNotBlank())
            assertTrue(s.generatedImageNoResult.isNotBlank())
            assertTrue(s.retryImageLoading.isNotBlank())
        }
    }
}
