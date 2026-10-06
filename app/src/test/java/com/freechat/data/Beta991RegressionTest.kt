package com.freechat.data

import com.freechat.i18n.En
import com.freechat.i18n.ZhCN
import com.freechat.i18n.ZhTW
import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.ui.components.ImageParticleMotion
import androidx.compose.animation.EnterExitState
import com.freechat.ui.animation.pageTranslation
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class Beta991RegressionTest {
    @Test fun generationTimeIncludesTheToolDecisionAndTheEntireImageRequest() {
        var now = 10_000L
        val timer = GenerationTimer { now }
        now += 2_000 // language model chose a tool
        val old = Message(role = Role.ASSISTANT, content = "", thinkingTimeMs = timer.elapsedMs(),
            imageUrls = listOf("/generated.png"), modelName = "Image")
        now += 30_000 // image model and response decoding
        val result = timer.complete(old)
        assertEquals(32_000L, result.thinkingTimeMs)
        assertEquals(old.copy(thinkingTimeMs = 32_000L), result)
    }

    @Test fun eachRegenerationHasItsOwnTimerAndZeroDurationIsValid() {
        var now = 100L
        val original = GenerationTimer { now }
        now += 33_000
        val retry = GenerationTimer { now }
        assertEquals(33_000L, original.elapsedMs())
        assertEquals(0L, retry.elapsedMs())
        now += 45_000
        assertEquals(45_000L, retry.elapsedMs())
    }

    @Test fun anySearchWorksWithoutAKeyAndUsesItsDocumentedJsonProtocol() {
        val config = SearchConfig(SearchProvider.ANYSEARCH, SearchProvider.ANYSEARCH.defaultUrl)
        assertNull(config.validationError())
        val request = SearchSource.customRequest(config, "河南 软件 招聘", null)
        assertEquals("POST", request.method)
        assertNull(request.header("Authorization"))
        assertEquals("https://api.anysearch.com/v1/search", request.url.toString())
        val buffer = Buffer(); request.body!!.writeTo(buffer)
        val body = JsonParser.parseString(buffer.readUtf8()).asJsonObject
        assertEquals("河南 软件 招聘", body.get("query").asString)
        assertEquals(10, body.get("max_results").asInt)
        assertEquals("cn", body.get("zone").asString)
        assertEquals("zh-CN", body.get("language").asString)
        assertEquals("json", body.get("format").asString)
        assertFalse(body.has("api_key"))
        assertFalse(body.has("tag"))
    }

    @Test fun userOwnedKeyIsHeaderOnlyAndNeverReusedForAnotherSource() {
        val request = SearchSource.customRequest(SearchConfig(SearchProvider.ANYSEARCH,
            "https://api.anysearch.com/v1/search", "qa-private-key"), "Compose animation", 7)
        assertEquals("Bearer qa-private-key", request.header("Authorization"))
        val buffer = Buffer(); request.body!!.writeTo(buffer)
        val raw = buffer.readUtf8()
        assertFalse(raw.contains("qa-private-key"))
        val body = JsonParser.parseString(raw).asJsonObject
        assertEquals("intl", body.get("zone").asString)
        assertTrue(body.get("query").asString.contains(LocalDate.now().toString()))
        assertFalse(body.has("time_range"))
        assertFalse(body.has("tbs"))
    }

    @Test fun structuredContentAndOptionalDatesAreKeptWithoutInventingAFreshTimestamp() {
        val body = """{"code":0,"data":{"results":[{"title":"郑州软件招聘","url":"https://example.org/jobs",
            "snippet":"相关岗位","content":"<p>企业详细说明</p>"},{"title":"","url":"https://official.example.org"}]}}"""
        val result = SearchSource.parseJson(body, SearchProvider.ANYSEARCH)
        assertEquals(2, result.size)
        assertEquals("相关岗位", result[0].snippet)
        assertEquals("企业详细说明", result[0].content)
        assertNull(result[0].publishedAt)
        assertEquals("official.example.org", result[1].title)
    }

    @Test fun unsafeLinksAndFailedBusinessEnvelopesAreNeverInformationSources() {
        val result = SearchSource.parseJson("""{"code":0,"data":{"results":[
            {"title":"bad","url":"javascript:alert(1)"},
            {"title":"bad","url":"https://key:secret@example.org"}]}}""", SearchProvider.ANYSEARCH)
        assertTrue(result.isEmpty())
        val error = runCatching { SearchSource.parseJson(
            """{"code":-1,"message":"api_key=qa-sensitive-credential"}""", SearchProvider.ANYSEARCH) }.exceptionOrNull()
        assertNotNull(error)
        assertFalse(error!!.message.orEmpty().contains("qa-sensitive-credential"))
    }

    @Test fun quotaErrorNeverExposesAnonymousAutoRegistrationCredentials() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(402)
            .setBody("""{"code":-1,"message":"username=qa-user\npassword=qa-password\napi_key=qa-key"}"""))
        server.start()
        try {
            val error = runCatching { OkHttpClient().newCall(Request.Builder().url(server.url("/")).build()).awaitText() }
                .exceptionOrNull() as ApiFailure
            assertEquals(ApiFailure.Kind.QUOTA, error.kind)
            for (secret in listOf("qa-user", "qa-password", "qa-key")) assertFalse(error.message.orEmpty().contains(secret))
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun irrelevantAnySearchEmploymentResultsStillFailTheExistingRelevanceGate() {
        val hits = SearchSource.parseJson("""{"code":0,"data":{"results":[
            {"title":"郑州软件工程师招聘","url":"https://example.org/jobs","snippet":"河南应届本科软件开发"},
            {"title":"新加坡约会软件","url":"https://example.org/dating","snippet":"上线新闻"}]}}""", SearchProvider.ANYSEARCH)
        val ranked = SearchPipeline.rank(hits, "河南 软件工程 应届生 招聘", null)
        assertEquals(listOf("https://example.org/jobs"), ranked.map { it.link })
    }

    @Test fun particleFieldIsBoundedStableAndNotARepeatingOrbit() {
        val particles = ImageParticleMotion.create()
        assertEquals(ImageParticleMotion.Count, particles.size)
        assertTrue(particles.size <= 96)
        assertEquals(particles, ImageParticleMotion.create())
        for (p in particles) for (seconds in 0..200) {
            val t = seconds.toFloat()
            assertTrue(p.x(t).isFinite() && p.y(t).isFinite())
            assertTrue(p.x(t) in -.2f..1.2f && p.y(t) in -.2f..1.2f)
            assertTrue(p.alpha(t) in 0f..1f)
            assertTrue(abs(p.x(t + .016f) - p.x(t)) < .002f)
            assertTrue(abs(p.y(t + .016f) - p.y(t)) < .002f)
        }
        assertTrue(particles.any { abs(it.x(0f) - it.x(7.2f)) > .01f })
    }

    @Test fun anySearchDescriptionAndIconNamesExistInEveryLanguage() {
        for (s in listOf(ZhCN, ZhTW, En)) {
            assertTrue(s.searchAnySearchDesc.isNotBlank())
        }
        assertEquals("简F", ZhCN.appIconBlue)
        assertEquals("菱星", ZhCN.appIconClassic)
    }

    @Test fun fullPageLayerMotionKeepsTheSameDirectionAndNeverScalesWithPageHeight() {
        assertEquals(32f, pageTranslation(EnterExitState.PreEnter, true, 32f), 0f)
        assertEquals(0f, pageTranslation(EnterExitState.Visible, true, 32f), 0f)
        assertEquals(-32f / 3f, pageTranslation(EnterExitState.PostExit, true, 32f), 0f)
        assertEquals(-32f, pageTranslation(EnterExitState.PreEnter, false, 32f), 0f)
        assertEquals(32f / 3f, pageTranslation(EnterExitState.PostExit, false, 32f), 0f)
        assertEquals(0f, pageTranslation(EnterExitState.PreEnter, true, -10f), 0f)
    }
}
