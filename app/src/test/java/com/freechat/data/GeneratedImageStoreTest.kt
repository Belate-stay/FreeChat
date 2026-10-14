package com.freechat.data

import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.sync.ImageSync
import com.freechat.sync.Wire
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class GeneratedImageStoreTest {
    private val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJ1sAAAAASUVORK5CYII=")

    @Before fun freshStore() {
        LocalStore.init(Files.createTempDirectory("generated-image-local").toFile())
        LocalStore.setWriteListener(null)
    }

    @Test fun remoteResultIsDurableAndCanBeReadAfterProviderDisappears() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png)))
            val url = server.url("/temporary.png").toString()
            val path = GeneratedImageStore.persist(ImageApiResponse.Source.Remote(url), OkHttpClient())
            assertTrue(File(path).isFile)
            assertArrayEquals(png, File(path).readBytes())
            assertEquals(path, GeneratedImageStore.cachedSource(url))
            assertEquals(path, GeneratedImageStore.persist(ImageApiResponse.Source.Remote(url), OkHttpClient()))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun remoteHtmlIsNotSavedAsASuccessfulImage() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>expired</html>"))
            val url = server.url("/expired.png").toString()
            assertThrows(ApiFailure::class.java) {
                GeneratedImageStore.persist(ImageApiResponse.Source.Remote(url), OkHttpClient())
            }
            assertNull(GeneratedImageStore.cachedSource(url))
            assertEquals(0, LocalStore.filesDir().listFiles().orEmpty().size)
        }
    }

    @Test fun localCopyOfLegacyUrlIsUsedForDisplayAndWireIdentity() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(png)))
            val url = server.url("/old.png").toString()
            val path = GeneratedImageStore.persist(ImageApiResponse.Source.Remote(url), OkHttpClient())
            assertEquals(listOf(path), ImageSync.canonicalEntries(listOf(url, path)))
            val row = Wire.msgsToWire(listOf(Message(id = "m", role = Role.ASSISTANT, content = "", imageUrls = listOf(url))))[0].asJsonObject
            assertEquals(ImageSync.refForLocal(path), row.getAsJsonArray("imageUrls")[0].asString)
        }
    }

    @Test fun releasingLastReferenceDeletesOriginalAndSyncCopyButNotSharedImage() {
        val path = GeneratedImageStore.persist(ImageApiResponse.Source.Encoded(png), OkHttpClient())
        val ref = requireNotNull(ImageSync.ensureCached(path))
        val hash = ImageSync.hashOfRef(ref)
        LocalStore.writeText(LocalStore.messagesFile("other"), AppJson.gson.toJson(listOf(
            Message(id = "other", role = Role.ASSISTANT, content = "", imageUrls = listOf(path)))))
        assertTrue(ImageSync.releaseUnreferenced(listOf(path)).isEmpty())
        assertTrue(File(path).isFile)
        LocalStore.writeText(LocalStore.messagesFile("other"), "[]")
        assertEquals(setOf(hash), ImageSync.releaseUnreferenced(listOf(path)))
        assertFalse(File(path).exists())
        assertFalse(ImageSync.cacheFile(hash).exists())
    }

    @Test fun legacyRecoveryDoesNotResurrectDeletedMessageOrRewriteBodyText() {
        MockWebServer().use { server ->
            val url = server.url("/old.png").toString()
            server.enqueue(MockResponse().setBody(Buffer().write(png)))
            LocalStore.writeText(LocalStore.messagesFile("c"), AppJson.gson.toJson(listOf(
                Message(id = "m", role = Role.ASSISTANT, content = url, imageUrls = listOf(url)))))
            val path = GeneratedImageStore.recover(url, OkHttpClient())
            val row = AppJson.gson.fromJson(LocalStore.readText(LocalStore.messagesFile("c")), Array<Message>::class.java).single()
            assertEquals(url, row.content)
            assertEquals(listOf(path), row.imageUrls)
            LocalStore.writeText(LocalStore.messagesFile("c"), "[]")
            ImageSync.releaseUnreferenced(listOf(requireNotNull(path)))
            assertNull(GeneratedImageStore.recover(url, OkHttpClient()))
            assertEquals("[]", LocalStore.readText(LocalStore.messagesFile("c")))
        }
    }

    @Test fun deletingLegacyMessageDuringDownloadWinsAndRemovesTheDownloadedBytes() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(png))
                .setBodyDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS))
            val url = server.url("/racing.png").toString()
            LocalStore.writeText(LocalStore.messagesFile("c"), AppJson.gson.toJson(listOf(
                Message(id = "m", role = Role.ASSISTANT, content = "", imageUrls = listOf(url)))))
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                val recovering = executor.submit<String?> { GeneratedImageStore.recover(url, OkHttpClient()) }
                assertNotNull(server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS))
                LocalStore.writeText(LocalStore.messagesFile("c"), "[]")
                assertNull(recovering.get(3, java.util.concurrent.TimeUnit.SECONDS))
                assertNull(GeneratedImageStore.cachedSource(url))
                assertEquals("[]", LocalStore.readText(LocalStore.messagesFile("c")))
            } finally { executor.shutdownNow() }
        }
    }
}
