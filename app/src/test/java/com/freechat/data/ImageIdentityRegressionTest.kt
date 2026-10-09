package com.freechat.data

import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.sync.ImageSync
import com.freechat.sync.Wire
import com.freechat.ui.components.ImageDisplayPolicy
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** One visual result can have an original, a compressed cache and an unresolved sync ref. */
class ImageIdentityRegressionTest {
    @Before fun freshStore() {
        LocalStore.init(Files.createTempDirectory("image-identity").toFile())
        LocalStore.setWriteListener(null)
    }

    private fun original(name: String, bytes: ByteArray): String =
        File(LocalStore.filesDir(), name).apply { writeBytes(bytes) }.absolutePath

    private fun ref(path: String): String = ImageSync.refForLocal(path)!!

    private fun cache(ref: String): String =
        ImageSync.cacheFile(ImageSync.hashOfRef(ref)).apply {
            // Deliberately different bytes: the cache is compressed, its filename carries identity.
            writeBytes(byteArrayOf(10, 20, 30))
        }.absolutePath

    private fun message(urls: List<String>, scene: Boolean = false) =
        Message(id = "generated", role = Role.ASSISTANT, content = "",
            imageUrls = urls, sceneVisualization = scene)

    private fun pull(remote: Message, local: Message?): Message =
        Wire.msgsFromWire(AppJson.gson.toJsonTree(listOf(remote)), listOfNotNull(local)).single()

    @Test fun standardGenerationKeepsOneOriginalInsteadOfItsCompressedCache() {
        val path = original("gen_standard.png", byteArrayOf(1, 2, 3))
        val reference = ref(path)
        cache(reference)

        assertEquals(listOf(path), pull(message(listOf(reference)), message(listOf(path))).imageUrls)
    }

    @Test fun sceneGenerationKeepsOneOriginalInsteadOfItsCompressedCache() {
        val path = original("gen_scene.png", byteArrayOf(4, 5, 6))
        val reference = ref(path)
        cache(reference)

        assertEquals(listOf(path), pull(message(listOf(reference), true), message(listOf(path), true)).imageUrls)
    }

    @Test fun identicalOriginalBytesUnderDifferentNamesStillOccupyOneSlot() {
        val path = original("gen_a.png", byteArrayOf(7, 8))
        val copy = original("gen_copy.png", byteArrayOf(7, 8))
        val reference = ref(path)
        cache(reference)

        assertEquals(listOf(path), pull(message(listOf(reference)), message(listOf(path, copy))).imageUrls)
    }

    @Test fun distinctImagesKeepRemoteOrderAndAppendLocalOnlyImages() {
        val first = original("gen_first.png", byteArrayOf(1))
        val second = original("gen_second.png", byteArrayOf(2))
        val localOnly = original("gen_local.png", byteArrayOf(3))
        val firstRef = ref(first)
        val secondRef = ref(second)
        cache(firstRef)
        cache(secondRef)
        val url = "https://example.org/result.png"

        val remote = message(listOf(secondRef, url, firstRef))
        val mine = message(listOf(first, second, localOnly, url))
        assertEquals(listOf(second, url, first, localOnly), pull(remote, mine).imageUrls)
    }

    @Test fun repeatedSyncRepairsPreviouslyPersistedDuplicatesIdempotently() {
        val path = original("gen_persisted.png", byteArrayOf(8, 9))
        val reference = ref(path)
        val cached = cache(reference)
        val remote = message(listOf(reference, reference))
        var local = message(listOf(cached, path, reference))

        repeat(3) {
            local = pull(remote, local)
            assertEquals(listOf(path), local.imageUrls)
        }
    }

    @Test fun unresolvedRefStaysOneSlotAndHealsWhenCacheArrives() {
        val reference = ImageSync.refOf("0123456789abcdef")
        val remote = message(listOf(reference))
        val unresolved = pull(remote, message(listOf(reference)))
        assertEquals(listOf(reference), unresolved.imageUrls)

        val cached = ImageSync.materialize(ImageSync.hashOfRef(reference), byteArrayOf(3, 2, 1))!!
        assertEquals(listOf(cached), pull(remote, unresolved).imageUrls)
    }

    @Test fun uploadedRefHealsEvenWhenTheLocalMessageAlreadyExists() {
        val reference = ImageSync.refOf("fedcba9876543210")
        val remote = Message(id = "upload", role = Role.USER, content = "look",
            imagePaths = listOf(reference))
        val unresolved = pull(remote, remote)
        assertEquals(listOf(reference), unresolved.imagePaths)

        val cached = ImageSync.materialize(ImageSync.hashOfRef(reference), byteArrayOf(5, 4))!!
        assertEquals(listOf(cached), pull(remote, unresolved).imagePaths)
    }

    @Test fun newDeviceCollapsesRepeatedRefsWithoutLosingDifferentImages() {
        val first = ImageSync.refOf("0123456789abcdef")
        val second = ImageSync.refOf("fedcba9876543210")
        val firstCache = cache(first)
        val secondCache = cache(second)

        assertEquals(listOf(firstCache, secondCache), pull(message(listOf(first, first, second)), null).imageUrls)
    }

    @Test fun differentRemoteUrlsStayDifferentEvenWithContentAddressedFilenames() {
        val first = "https://first.example.org/img_0123456789abcdef.jpg"
        val second = "https://second.example.org/img_0123456789abcdef.jpg"
        assertEquals(listOf(first, second), pull(message(listOf(first, second)), message(listOf(first))).imageUrls)
    }

    @Test fun pushingHistoricalDuplicatesProducesOneSyncRef() {
        val path = original("gen_outbound.png", byteArrayOf(11, 12))
        val reference = ref(path)
        val cached = cache(reference)
        val wire = Wire.msgsToWire(listOf(message(listOf(cached, path, reference))))[0].asJsonObject

        assertEquals(listOf(reference), wire.getAsJsonArray("imageUrls").map { it.asString })
    }

    @Test fun savedDuplicateImagesDisplayOnceWithoutAnotherSync() {
        val path = original("gen_saved.png", byteArrayOf(13, 14))
        val reference = ref(path)
        val cached = cache(reference)
        val saved = AppJson.gson.fromJson(
            AppJson.gson.toJson(message(listOf(cached, path, reference), true)), Message::class.java
        )

        assertEquals(listOf(path), ImageDisplayPolicy.generatedImages(saved))
    }

    @Test fun originalAlreadyDisplaysBeforeItsSyncCacheExists() {
        val path = original("gen_not_uploaded.png", byteArrayOf(15, 16))
        val reference = ref(path)

        assertEquals(listOf(path), ImageDisplayPolicy.generatedImages(message(listOf(reference, path))))
        assertNull("Display normalization must not write or compress an upload cache",
            ImageSync.cachePathIfPresent(ImageSync.hashOfRef(reference)))
    }

    @Test fun staleCachePathResolvesByIdentityInTheCurrentStore() {
        val reference = ImageSync.refOf("0123456789abcdef")
        val cached = cache(reference)
        val stale = "/old/device/files/img_0123456789abcdef.jpg"

        assertEquals(listOf(cached), ImageDisplayPolicy.generatedImages(message(listOf(stale, reference))))
    }

    @Test fun liveOriginalStillBlocksImageTombstoneAfterDuplicateRemoval() {
        val path = original("gen_referenced.png", byteArrayOf(17, 18))
        val hash = ImageSync.hashOfRef(ref(path))
        LocalStore.writeText(LocalStore.messagesFile("original-only"),
            AppJson.gson.toJson(listOf(message(listOf(path)))))

        assertTrue("An original retained as the only image source must protect its content object",
            ImageSync.isReferencedLocally(hash))
    }

    @Test fun originalInAppearanceImageFieldsAlsoBlocksImageTombstone() {
        val path = original("appearance_original.png", byteArrayOf(19, 20))
        val hash = ImageSync.hashOfRef(ref(path))
        LocalStore.writeText(LocalStore.conversationsFile(), AppJson.gson.toJson(listOf(
            mapOf("characterProfile" to mapOf("appearanceImagePaths" to listOf(path)))
        )))

        assertTrue(ImageSync.isReferencedLocally(hash))
    }

    @Test fun originalMentionedOnlyInMessageTextDoesNotBlockTombstone() {
        val path = original("gen_mentioned.png", byteArrayOf(21, 22))
        val hash = ImageSync.hashOfRef(ref(path))
        LocalStore.writeText(LocalStore.messagesFile("text-only"), AppJson.gson.toJson(listOf(
            message(emptyList()).copy(content = "This example mentions $path")
        )))

        assertFalse(ImageSync.isReferencedLocally(hash))
    }

    @Test fun differentOriginalBytesDoNotProtectAnUnreferencedImageObject() {
        val referenced = original("gen_live.png", byteArrayOf(23))
        val unused = original("gen_unused.png", byteArrayOf(24))
        LocalStore.writeText(LocalStore.messagesFile("different-original"),
            AppJson.gson.toJson(listOf(message(listOf(referenced)))))

        assertFalse(ImageSync.isReferencedLocally(ImageSync.hashOfRef(ref(unused))))
    }

    @Test fun chatBubbleUsesCanonicalImagesFromAnIoDispatcherForSavedMessages() {
        val source = File("src/main/java/com/freechat/ui/components/ChatBubble.kt").readText()
        val asyncCanonicalization = Regex(
            "withContext\\(Dispatchers\\.IO\\)\\s*\\{\\s*ImageDisplayPolicy\\.generatedImages\\(message\\)\\s*}"
        )
        assertTrue("Saved duplicates must be canonicalized away from the UI thread", asyncCanonicalization.containsMatchIn(source))
        assertFalse("Rendering raw URLs would show the original and its cache twice",
            source.contains("message.imageUrls.forEach"))
    }
}
