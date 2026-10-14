package com.freechat.data

import com.freechat.model.CharacterExport
import com.freechat.model.ChatMode
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.freechat.model.PerConvSettings
import com.freechat.model.Role
import com.freechat.model.SearchCitation
import com.freechat.model.toExport
import org.junit.Assert.*
import org.junit.Test

/** 纯 JVM：只测 ShareExport 编解码/过滤/重映射，不碰 Android Context */
class ShareExportTest {

    private fun msg(
        id: String,
        content: String = "hi",
        role: Role = Role.USER,
        images: Boolean = false,
        attachment: Boolean = false,
        scene: Boolean = false
    ) = Message(
        id = id, role = role, content = content, timestamp = 100L,
        modelName = "m1", thinkingTimeMs = 5L, reasoningContent = "why",
        quotedText = "q", mode = ChatMode.COMPANION, favorited = true, failed = false,
        searchSources = listOf(SearchCitation("t", "u")),
        imageUrls = if (images) listOf("http://x") else emptyList(),
        imagePaths = if (images) listOf("/a.jpg") else emptyList(),
        attachmentPath = if (attachment) "/a.pptx" else null,
        attachmentName = if (attachment) "a.pptx" else null,
        quotedImagePath = if (images) "/q.jpg" else null,
        imageContext = if (images) "ctx" else null,
        imagePrompt = if (images) "prompt" else null,
        imageReferenceMessageId = if (images) "ref" else null,
        sceneVisualization = scene
    )

    private val profile = com.freechat.model.CharacterProfile(
        name = "旅人", dialogueMode = 2, plotLength = 3,
        personalityText = "冷静", avatarPath = "/av.jpg",
        appearanceImagePaths = listOf("/ap1.jpg")
    )

    private fun fullPackage(): String {
        val shell = ShareExport.ShareConversation(
            id = "c1", title = "t1", mode = ChatMode.COMPANION.ordinal,
            createdAt = 1, updatedAt = 2,
            profile = profile.toExport(),
            messages = listOfNotNull(ShareExport.toShareMessage(msg("m1", "hello", images = true))),
            deletedMessageIds = listOf("d1"), deletedVisualMessageIds = listOf("v1")
        )
        val mem = ShareExport.ShareMemory(
            convId = "c1",
            entries = listOf(MemoryEntry(id = "e1", summary = "s", keywords = listOf("k"),
                sourceMessageIds = listOf("m1"))),
            mood = PerConvSettings(lastMood = "happy", warmth = 2, atmosphere = "warm",
                atmosphereSourceMessageIds = listOf("m1"))
        )
        return requireNotNull(
            ShareExport.build(ShareExport.Options(), "旅人",
                ShareExport.personaFrom(profile, ShareExport.encodeBase64(byteArrayOf(1, 2)),
                    listOf(ShareExport.ShareImage(ShareExport.encodeBase64(byteArrayOf(9))))),
                listOf(shell), listOf(mem), exportedAt = 42L)
        )
    }

    @Test fun fullSelectRoundTripsThreeSectionsAndKeepsIds() {
        val json = fullPackage()
        val pkg = requireNotNull(ShareExport.parse(json))
        assertEquals(ShareExport.KIND, pkg.kind)
        assertEquals("旅人", pkg.name)
        assertNotNull("persona must be present", pkg.persona)
        assertEquals(1, pkg.conversations.size)
        assertEquals("c1", pkg.conversations[0].id)
        assertEquals(listOf("d1"), pkg.conversations[0].deletedMessageIds)
        assertEquals("m1", pkg.conversations[0].messages.single().id)
        assertEquals("hello", pkg.conversations[0].messages.single().content)
        assertEquals(1, pkg.memories.size)
        assertEquals("c1", pkg.memories[0].convId)
        assertEquals(listOf("m1"), pkg.memories[0].entries.single().sourceMessageIds)
        assertEquals("happy", pkg.memories[0].mood?.lastMood)
        // persona 扩展字段
        assertEquals(2, pkg.persona!!.get("dialogueMode").asInt)
        assertEquals(3, pkg.persona!!.get("plotLength").asInt)
        assertNotNull(ShareExport.personaAvatar(pkg.persona))
        assertEquals(1, ShareExport.personaImages(pkg.persona).size)
    }

    @Test fun chatsOnlyHasNoPersonaNorMemoriesAndIsTextOnly() {
        val shell = ShareExport.ShareConversation(
            id = "c1", messages = listOfNotNull(ShareExport.toShareMessage(msg("m1", "hello", images = true)))
        )
        val json = requireNotNull(ShareExport.build(ShareExport.Options(persona = false, chats = true, memories = false), "n", null, listOf(shell), emptyList()))
        val pkg = requireNotNull(ShareExport.parse(json))
        assertNull(pkg.persona)
        assertTrue(pkg.memories.isEmpty())
        val m = pkg.conversations[0].messages.single()
        assertEquals("hello", m.content)
        // ShareMessage 本身就没有图字段；还原成 Message 也不带图
        val restored = ShareExport.toMessage(m)
        assertTrue(restored.imageUrls.isEmpty())
        assertTrue(restored.imagePaths.isEmpty())
        assertNull(restored.attachmentPath)
        assertFalse(restored.isStreaming)
    }

    @Test fun textFilterDropsImageFieldsVisualRowsAndPureImageMessages() {
        val rows = listOf(
            msg("keep", "text"),
            msg("withImg", "caption", images = true),
            msg("withAtt", "file", attachment = true),
            msg("visual", "x", scene = true),
            msg("pureImg", "", images = true)
        )
        val kept = rows.mapNotNull { ShareExport.toShareMessage(it) }
        assertEquals(listOf("keep", "withImg", "withAtt"), kept.map { it.id })
        assertTrue(kept.none { it.id == "visual" })
        assertTrue(kept.none { it.id == "pureImg" })
        val restored = kept.map { ShareExport.toMessage(it) }
        restored.forEach {
            assertTrue(it.imageUrls.isEmpty())
            assertTrue(it.imagePaths.isEmpty())
            assertNull(it.attachmentPath)
            assertNull(it.quotedImagePath)
            assertNull(it.imageContext)
            assertNull(it.imagePrompt)
            assertNull(it.imageReferenceMessageId)
            assertFalse(it.isStreaming)
            assertFalse(it.sceneVisualization)
        }
    }

    @Test fun buildRequiresAtLeastOneOption() {
        assertNull(ShareExport.build(ShareExport.Options(persona = false, chats = false, memories = false), "n", null, emptyList(), emptyList()))
        assertNotNull(ShareExport.build(ShareExport.Options(chats = false, memories = false), "n",
            ShareExport.personaFrom(profile, null, emptyList()), emptyList(), emptyList()))
    }

    @Test fun sceneRowsTravelWithImagesOnlyWhenChecked() {
        val scene = ShareExport.ShareImage(ShareExport.encodeBase64(byteArrayOf(7)))
        val rows = listOf(msg("t1", "before"), msg("v1", "x", scene = true), msg("t2", "after"))
        // 勾了场景图：视觉行按原位置带着图进序列
        val kept = rows.mapNotNull { m ->
            ShareExport.toShareMessage(m, if (m.sceneVisualization) scene else null)
        }
        assertEquals(listOf("t1", "v1", "t2"), kept.map { it.id })
        val v = kept[1]
        assertTrue(v.sceneVisualization)
        assertEquals(ShareExport.encodeBase64(byteArrayOf(7)), v.sceneImage?.data)
        // 还原：落盘路径进 imageUrls（助手行渲染读它）、sceneVisualization 旗标立起
        val restored = ShareExport.toMessage(v, "/scene.jpg")
        assertTrue(restored.sceneVisualization)
        assertEquals(listOf("/scene.jpg"), restored.imageUrls)
        assertTrue(restored.imagePaths.isEmpty())
        // 没勾：视觉行整条丢弃（文字行照旧）
        val without = rows.mapNotNull { ShareExport.toShareMessage(it) }
        assertEquals(listOf("t1", "t2"), without.map { it.id })
    }

    @Test fun appearanceDescSurvivesRoundTrip() {
        val persona = ShareExport.personaFrom(profile, null,
            listOf(ShareExport.ShareImage(ShareExport.encodeBase64(byteArrayOf(9)), "image/jpeg", "红发")))
        assertEquals("红发", ShareExport.personaImages(persona).single().desc)
    }

    @Test fun legacyCharacterExportStillParses() {
        val json = AppJson.gson.toJson(CharacterExport(name = "陈以沫", personalityText = "测试角色"))
        assertNull("legacy file must not look like a share package", ShareExport.parse(json))
        assertFalse(ShareExport.isShareFormat(json))
        val parsed = requireNotNull(ShareExport.parseLegacyCharacter(json))
        assertEquals("陈以沫", parsed.name)
        assertEquals("测试角色", parsed.personalityText)
        assertNull("missing name still rejects", ShareExport.parseLegacyCharacter("""{"personalityText":"x"}"""))
    }

    @Test fun convIdRemapPointsMemoriesAtNewIdsAndKeepsEntries() {
        val pkg = requireNotNull(ShareExport.parse(fullPackage()))
        val remapped = ShareExport.remapConvIds(pkg, mapOf("c1" to "new-9"))
        assertEquals("new-9", remapped.conversations[0].id)
        assertEquals("new-9", remapped.memories[0].convId)
        assertEquals("e1", remapped.memories[0].entries.single().id)
        assertEquals(listOf("m1"), remapped.memories[0].entries.single().sourceMessageIds)
        assertEquals("happy", remapped.memories[0].mood?.lastMood)
    }

    @Test fun base64RoundTrip() {
        val bytes = byteArrayOf(0, 1, 2, 255.toByte(), 254.toByte())
        assertArrayEquals(bytes, ShareExport.decodeBase64(ShareExport.encodeBase64(bytes)))
        assertNull(ShareExport.decodeBase64("!!not-base64!!"))
    }
}
