package com.freechat.data

import com.freechat.model.CharacterProfile
import com.freechat.model.DialogueMode
import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.sync.ImageSync
import com.freechat.sync.Merge
import com.freechat.sync.Wire
import com.google.gson.JsonParser
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Beta1107RegressionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Before fun freshStore() {
        LocalStore.init(temporary.newFolder())
        LocalStore.setWriteListener(null)
    }

    private fun image(name: String, bytes: ByteArray): String =
        File(LocalStore.filesDir(), name).apply { writeBytes(bytes) }.absolutePath

    private fun scene(id: String, source: String, at: Long = 1) =
        Message(id = id, role = Role.ASSISTANT, content = "视觉呈现", timestamp = at,
            sceneVisualization = true, imageUrls = listOf(source))

    private fun profile(paths: List<String> = emptyList(), enabled: Boolean = true) =
        CharacterProfile(name = "旅人", dialogueMode = DialogueMode.PLOT,
            enhancedSceneContinuity = enabled, appearanceImagePaths = paths)

    @Test fun oldProfilesKeepSceneContinuityOff() {
        val profile = AppJson.gson.fromJson("{}", CharacterProfile::class.java)
        val json = JsonParser.parseString(AppJson.gson.toJson(profile)).asJsonObject
        assertTrue("Old profiles need an explicit false continuity default", json.has("enhancedSceneContinuity"))
        assertFalse(json.get("enhancedSceneContinuity").asBoolean)
    }

    @Test fun explicitContinuityChoiceSurvivesSerializationAndNormalization() {
        val profile = AppJson.gson.fromJson("""{"name":"旅人","enhancedSceneContinuity":true}""", CharacterProfile::class.java).normalized()
        val json = JsonParser.parseString(AppJson.gson.toJson(profile)).asJsonObject
        assertTrue("Explicit continuity must survive a profile round trip", json.has("enhancedSceneContinuity"))
        assertTrue(json.get("enhancedSceneContinuity").asBoolean)
    }

    @Test fun currentTextOutranksEarlierSceneImagesAndVisualRowsRemainOutsideStory() {
        val profile = AppJson.gson.fromJson("""{"name":"旅人","enhancedSceneContinuity":true}""", CharacterProfile::class.java)
        val prompt = SceneImagePrompt.build(profile, listOf(
            Message(id = "latest-story", role = Role.USER, content = "换上红色外套，放下手中的伞"),
            Message(id = "visual-only", role = Role.ASSISTANT, content = "蓝衣与雨伞的旧图描述",
                sceneVisualization = true, imageUrls = listOf("https://example.invalid/old-scene.png"))
        ), emptyList())
        assertTrue(prompt.contains("最新剧情文字和人物设定优先于较早场景参考图"))
        assertTrue(prompt.contains("换装、增减道具"))
        assertTrue(prompt.contains("换上红色外套，放下手中的伞"))
        assertFalse(prompt.contains("蓝衣与雨伞的旧图描述"))
        assertFalse(prompt.contains("example.invalid"))
        assertTrue(prompt.contains("生成图片本身不属于剧情"))
    }

    @Test fun continuityBelongsToEditableSimulationSettingsWithoutChangingPersona() {
        val original = profile(enabled = false).copy(personaPrompt = "已学习的人设", personalityText = "原设定")
        val draft = original.copy(enhancedSceneContinuity = true, personaPrompt = "未保存的编辑", personalityText = "未保存的编辑")
        val saved = CharacterPresentationPolicy.withSimulationSettings(original, draft)
        assertEquals(original.copy(enhancedSceneContinuity = true), saved)
        assertTrue(Merge.samePersona(original, saved))
        assertFalse(Merge.samePersona(original, saved.copy(personalityText = "新的设定")))
        assertFalse(CharacterPresentationPolicy.personaEditable(existing = true, editing = false))
    }

    @Test fun offKeepsAllThreeCharacterReferencesWithoutAddingScenes() {
        val characters = (1..3).map { image("character-$it.png", byteArrayOf(it.toByte())) }
        val oldScene = scene("scene", image("scene.png", byteArrayOf(8)))
        val refs = SceneReferenceSelector.prepare(profile(characters, enabled = false), listOf(oldScene))
        assertEquals(characters, refs.map { it.source })
        assertTrue(refs.none { it.sceneMessageId != null })
    }

    @Test fun threeCharactersReserveOneSlotForNewestRetainedScene() {
        val characters = (1..3).map { image("character-$it.png", byteArrayOf(it.toByte())) }
        val older = scene("older", image("older.png", byteArrayOf(7)), at = 10)
        val latest = scene("latest", image("latest.png", byteArrayOf(8)), at = 20)
        val refs = SceneReferenceSelector.prepare(profile(characters), listOf(latest, older))
        assertEquals(listOf(characters[0], characters[1], latest.imageUrls.single()), refs.map { it.source })
        assertEquals(listOf(null, null, "latest"), refs.map { it.sceneMessageId })
        assertEquals(3, refs.size)
    }

    @Test fun thirdCharacterAliasCanOccupyTheReservedSceneSlotWithoutDuplicatingSelectedCharacters() {
        val characters = (1..3).map { image("character-$it.png", byteArrayOf(it.toByte())) }
        val latest = scene("latest", characters[2], at = 20)
        val refs = SceneReferenceSelector.prepare(profile(characters), listOf(latest))
        assertEquals(listOf(null, null, "latest"), refs.map { it.sceneMessageId })
        assertEquals(characters, refs.map { it.source })
        assertEquals(3, refs.size)
    }

    @Test fun oneCharacterCanUseTwoRecentScenesButAnOlderThirdNeverOverflowsRequest() {
        val character = image("character.png", byteArrayOf(1))
        val rows = (1..3).map { scene("scene-$it", image("scene-$it.png", byteArrayOf((it + 5).toByte())), it.toLong()) }
        val refs = SceneReferenceSelector.prepare(profile(listOf(character)), rows)
        assertEquals(listOf(null, "scene-3", "scene-2"), refs.map { it.sceneMessageId })
        assertEquals(3, refs.size)
        val sceneOnly = SceneReferenceSelector.prepare(profile(), rows)
        assertEquals(listOf("scene-3", "scene-2"), sceneOnly.map { it.sceneMessageId })
    }

    @Test fun deletedFailedStreamingMissingAndOrdinaryPicturesCannotBeContinuityInputs() {
        val retained = scene("retained", image("retained.png", byteArrayOf(1)), 1)
        val excluded = listOf(
            scene("deleted", image("deleted.png", byteArrayOf(2)), 9),
            scene("failed", image("failed.png", byteArrayOf(3)), 8).copy(failed = true),
            scene("streaming", image("streaming.png", byteArrayOf(4)), 7).copy(isStreaming = true),
            scene("missing", File(LocalStore.filesDir(), "missing.png").absolutePath, 6),
            scene("empty", image("empty.png", byteArrayOf()), 5),
            scene("ordinary", image("ordinary.png", byteArrayOf(5)), 4).copy(sceneVisualization = false),
            scene("user", image("user.png", byteArrayOf(6)), 3).copy(role = Role.USER),
            scene("no-image", "", 2)
        )
        val refs = SceneReferenceSelector.prepare(profile(), listOf(retained) + excluded, setOf("deleted"))
        assertEquals(listOf("retained"), refs.map { it.sceneMessageId })
    }

    @Test fun noUsableSceneLeavesThirdCharacterSlotAvailable() {
        val characters = (1..3).map { image("character-$it.png", byteArrayOf(it.toByte())) }
        val missing = scene("missing", "img:0123456789abcdef")
        assertEquals(characters, SceneReferenceSelector.prepare(profile(characters), listOf(missing)).map { it.source })
    }

    @Test fun canonicalContentIdentitiesCollapseOriginalCacheRefAndFileUriAliases() {
        val original = image("original.png", byteArrayOf(4, 5, 6))
        val reference = ImageSync.refForLocal(original)!!
        val cache = ImageSync.cacheFile(ImageSync.hashOfRef(reference)).apply { writeBytes(byteArrayOf(9, 9)) }.absolutePath
        val rows = listOf(
            scene("old", cache, 1),
            scene("new", reference, 2).copy(imageUrls = listOf(reference, cache, File(original).toURI().toString(), original))
        )
        val refs = SceneReferenceSelector.prepare(profile(), rows)
        assertEquals(1, refs.size)
        assertEquals("new", refs.single().sceneMessageId)
        assertEquals(original, refs.single().source)
        assertArrayEquals(byteArrayOf(4, 5, 6), refs.single().reference.bytes)

        val other = scene("other", image("other.png", byteArrayOf(8)), 3)
        val withCharacter = SceneReferenceSelector.prepare(profile(listOf(original, cache, reference)), rows + other)
        assertEquals(listOf(null, "other"), withCharacter.map { it.sceneMessageId })
        assertEquals(original, withCharacter.first().source)
    }

    @Test fun imageSyncRefWithPresentCacheLoadsBytesAsAnImageReference() {
        val bytes = byteArrayOf(2, 3, 4)
        val hash = Wire.hashOf(bytes)
        val cache = ImageSync.cacheFile(hash).apply { writeBytes(bytes) }
        val refs = SceneReferenceSelector.prepare(profile(), listOf(scene("scene", ImageSync.refOf(hash))))
        assertEquals(cache.absolutePath, refs.single().source)
        assertArrayEquals(bytes, refs.single().reference.bytes)
        assertEquals("image/jpeg", refs.single().reference.mime)
    }

    @Test fun remoteScenesAreDownloadedAndFailedOptionalDownloadsDoNotRemoveCharacterReferences() {
        MockWebServer().use { server ->
            val bytes = byteArrayOf(6, 7, 8)
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(bytes)))
            val characters = (1..3).map { image("character-$it.png", byteArrayOf(it.toByte())) }
            val refs = SceneReferenceSelector.prepare(profile(characters), listOf(
                scene("older", server.url("/older.png").toString(), 1),
                scene("latest-missing", server.url("/missing.png").toString(), 2)
            ))
            assertEquals(listOf(null, null, "older"), refs.map { it.sceneMessageId })
            assertArrayEquals(bytes, refs.last().reference.bytes)
            assertEquals("image/png", refs.last().reference.mime)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun missingRequiredCharacterReferenceFailsInsteadOfBeingSilentlyDropped() {
        val missing = File(LocalStore.filesDir(), "missing-character.png").absolutePath
        val history = listOf(scene("scene", image("scene.png", byteArrayOf(3))))
        try {
            SceneReferenceSelector.prepare(profile(listOf(missing)), history)
            fail("A configured character image must remain a required reference")
        } catch (_: SceneImageFailure.ReferenceFailure) {
            // Optional scene failures cannot hide a broken explicit character reference.
        }
    }

    @Test fun duplicateRemoteContentDoesNotOccupyASecondReferenceSlot() {
        MockWebServer().use { server ->
            val characterBytes = byteArrayOf(1, 2, 3)
            val sceneBytes = byteArrayOf(6, 7, 8)
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(characterBytes)))
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(sceneBytes)))
            val character = image("character.png", characterBytes)
            val refs = SceneReferenceSelector.prepare(profile(listOf(character)), listOf(
                scene("older", server.url("/different.png").toString(), 1),
                scene("same-character", server.url("/copy.png").toString(), 2)
            ))
            assertEquals(listOf(null, "older"), refs.map { it.sceneMessageId })
            assertArrayEquals(sceneBytes, refs.last().reference.bytes)
        }
    }

    @Test fun failedOptionalDownloadLeavesAllThreeRequiredCharactersAvailable() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            val characters = (1..3).map { image("character-$it.png", byteArrayOf(it.toByte())) }
            val refs = SceneReferenceSelector.prepare(profile(characters), listOf(
                scene("missing-remote", server.url("/missing.png").toString())
            ))
            assertEquals(characters, refs.map { it.source })
            assertTrue(refs.none { it.sceneMessageId != null })
        }
    }

    @Test fun optionalSceneLoadingStopsAfterSixAttemptsAndRestoresThirdCharacter() {
        MockWebServer().use { server ->
            repeat(6) { server.enqueue(MockResponse().setResponseCode(404)) }
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(byteArrayOf(9))))
            val characters = (1..3).map { image("character-$it.png", byteArrayOf(it.toByte())) }
            val history = (1..7).map { scene("remote-$it", server.url("/scene-$it.png").toString(), it.toLong()) }
            val refs = SceneReferenceSelector.prepare(profile(characters), history)
            assertEquals(characters, refs.map { it.source })
            assertTrue(refs.none { it.sceneMessageId != null })
            assertEquals(6, server.requestCount)
        }
    }

    @Test fun successfulHttpErrorPageIsSkippedInsteadOfBecomingAnImageReference() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>expired image</html>"))
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(byteArrayOf(7, 8))))
            val character = image("character.png", byteArrayOf(1))
            val refs = SceneReferenceSelector.prepare(profile(listOf(character)), listOf(
                scene("retained-image", server.url("/image.png").toString(), 1),
                scene("expired-page", server.url("/expired.png").toString(), 2)
            ))
            assertEquals(listOf(null, "retained-image"), refs.map { it.sceneMessageId })
            assertArrayEquals(byteArrayOf(7, 8), refs.last().reference.bytes)
        }
    }

    @Test fun freshlyDeletedOrRemovedScenesAreRevalidatedBeforeUpload() {
        val character = image("character.png", byteArrayOf(1))
        val older = scene("older", image("older.png", byteArrayOf(2)), 1)
        val latest = scene("latest", image("latest.png", byteArrayOf(3)), 2)
        val prepared = SceneReferenceSelector.prepare(profile(listOf(character)), listOf(older, latest))
        assertEquals(3, prepared.size)
        assertEquals(listOf(null, "older"),
            SceneReferenceSelector.revalidate(prepared, listOf(older, latest), setOf("latest")).map { it.sceneMessageId })
        assertEquals(listOf(null), SceneReferenceSelector.revalidate(prepared, emptyList()).map { it.sceneMessageId })
        assertEquals(listOf(null, "older"),
            SceneReferenceSelector.revalidate(prepared, listOf(older, latest.copy(failed = true))).map { it.sceneMessageId })
        assertEquals(listOf(null, "older"),
            SceneReferenceSelector.revalidate(prepared, listOf(older, latest.copy(imageUrls = emptyList()))).map { it.sceneMessageId })
    }

    @Test fun regeneratingASceneCannotReuseItsRemovedVisualReference() {
        val removed = scene("regenerated-old", image("removed.png", byteArrayOf(1)), 2)
        val retained = scene("retained", image("retained.png", byteArrayOf(2)), 1)
        val refs = SceneReferenceSelector.prepare(profile(), listOf(removed, retained), setOf(removed.id))
        assertEquals(listOf("retained"), refs.map { it.sceneMessageId })
        assertEquals(emptyList<SceneReferenceSelector.PreparedReference>(),
            SceneReferenceSelector.prepare(profile(), listOf(removed), setOf(removed.id)))
    }
}
