package com.freechat.data

import com.freechat.model.*
import com.freechat.sync.DirtyRevisionFence
import com.freechat.sync.Merge
import com.freechat.sync.PerConvBridge
import com.freechat.sync.Relay
import com.freechat.sync.Wire
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class DeletionAndSceneTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun msg(id: String, role: Role = Role.ASSISTANT, time: Long = 10) =
        Message(id = id, role = role, content = id, timestamp = time)
    private fun memory(id: String, sources: List<String> = emptyList()) =
        MemoryEntry(id = id, summary = id, keywords = emptyList(), sourceMessageIds = sources)

    @Test fun olderCloudRowsCannotReviveDeletedRepliesOrFavorites() {
        val old = msg("old").copy(favorited = true)
        val fresh = msg("new", time = 20)
        assertEquals(listOf(fresh), Merge.mergeMessages(listOf(fresh), listOf(old), setOf("old")))
        assertEquals(emptyList<Message>(), Merge.mergeMessages(emptyList(), listOf(old), setOf("old")))
    }

    @Test fun deletionIdsSurviveNewerMetadataFromAnotherDevice() {
        val local = Conversation(id = "c", updatedAt = 10, deletedMessageIds = listOf("a"))
        val remote = local.copy(updatedAt = 20, title = "new title", deletedMessageIds = listOf("b"))
        val merged = Merge.mergeConv(local, remote).conv
        assertEquals(listOf("a", "b"), merged.deletedMessageIds)
        assertEquals("new title", merged.title)
    }

    @Test fun derivedAndUntraceableLegacyMemoriesArePurgedButUnrelatedMemoriesSurvive() {
        val rows = listOf(memory("legacy"), memory("gone", listOf("u", "a")), memory("keep", listOf("u2", "a2")))
        assertEquals(listOf("keep"), Merge.mergeMemories(rows, rows, setOf("a")).map { it.id })
        assertEquals(rows.size, MessageDeletion.filterMemories(rows, emptySet()).size)
    }

    @Test fun cancellingMiddleCompanionBubbleRegeneratesTheEntireReplyBatch() {
        val rows = listOf(msg("u", Role.USER), msg("a1"), msg("a2"), msg("a3"), msg("later-u", Role.USER), msg("later-a"))
        val plan = requireNotNull(RegenerationPlan.from(rows, 2))
        assertEquals(listOf("a1", "a2", "a3"), plan.removedReplies.map { it.id })
        assertEquals(listOf("u"), plan.context.map { it.id })
        assertEquals(listOf("u"), plan.userMessages.map { it.id })
        assertEquals(1, plan.replyStart)
    }

    @Test fun sceneImagesAreNeitherRegenerationTargetsNorLanguageContext() {
        val scene = msg("scene").copy(sceneVisualization = true, imageUrls = listOf("https://example.invalid/image.png"))
        val rows = listOf(msg("u", Role.USER), msg("a1"), scene, msg("a2"), msg("u2", Role.USER), msg("a3"))
        assertNull(RegenerationPlan.from(rows, 2))
        assertEquals(listOf("a1", "a2"), RegenerationPlan.from(rows, 3)!!.removedReplies.map { it.id })
        assertFalse(RegenerationPlan.from(rows, 5)!!.context.any { it.sceneVisualization })
    }

    @Test fun scenePromptPreservesFullSettingMemoriesAndOriginalStoryButNoVisualOnlyEvents() {
        val character = CharacterProfile(name = "灯", gender = "女", age = "22", appearanceText = "银发绿衣",
            personalityText = "谨慎", memoryPerception = "旧友", personaPrompt = "档案原文",
            supportingCast = "师姐", worldRules = "青石城", userPersona = "黑衣旅人",
            appearanceImageDescs = listOf("绿色眼睛"), openingLines = listOf("渡口相逢"))
        val history = (0..50).map { msg("story-$it", Role.USER, it.toLong()) } +
            msg("current-scene").copy(content = "正在雨夜渡口等船") +
            msg("must-not-be-story").copy(sceneVisualization = true, imageUrls = listOf("https://example.invalid/scene.png"))
        val prompt = SceneImagePrompt.build(character, history, listOf(memory("记忆事实")), listOf("全局人物事实"), "对话规则原文")
        listOf("银发绿衣", "旧友", "档案原文", "师姐", "青石城", "黑衣旅人", "绿色眼睛", "渡口相逢", "记忆事实",
            "story-0", "story-50", "正在雨夜渡口等船", "全局人物事实", "对话规则原文").forEach { assertTrue(it, prompt.contains(it)) }
        assertFalse(prompt.contains("must-not-be-story"))
        assertFalse(prompt.contains("example.invalid"))
    }

    @Test fun missingAndExplicitNullFieldsInOldJsonHaveSafeDefaults() {
        val conv = AppJson.gson.fromJson("{\"id\":\"c\",\"deletedMessageIds\":null}", Conversation::class.java).healed()
        val entry = AppJson.gson.fromJson("{\"summary\":\"old\",\"sourceMessageIds\":null}", MemoryEntry::class.java).healed()
        val pc = AppJson.gson.fromJson("{\"atmosphereSourceMessageIds\":null}", PerConvSettings::class.java).healed()
        assertTrue(conv.deletedMessageIds.isEmpty())
        assertTrue(entry.sourceMessageIds.isEmpty())
        assertTrue(pc.atmosphereSourceMessageIds.isEmpty())
        assertEquals("", AppJson.gson.fromJson("{}", CharacterProfile::class.java).visualModelId)
        assertFalse(AppJson.gson.fromJson("{}", Message::class.java).sceneVisualization)
    }

    @Test fun lateUploadAcknowledgementDoesNotClearADeletionMadeWhileUploading() {
        val fence = DirtyRevisionFence()
        val sent = fence.changed("msgs:c")
        fence.changed("msgs:c")
        assertFalse(fence.matches("msgs:c", sent))
        assertTrue(fence.matches("msgs:c", fence.current("msgs:c")))
        assertEquals(0L, fence.current("mems:c"))
    }

    @Test fun customImageModelFromOlderAppHasNonNullOptionalParameters() {
        val model = AppJson.gson.fromJson("{\"id\":\"my-image-model\",\"displayName\":\"Image\",\"provider\":\"CUSTOM\",\"modelType\":\"VISUAL\",\"genStyle\":null}", ModelInfo::class.java).healed()
        assertEquals("", model.genStyle)
        assertEquals("", model.genResolution)
        assertEquals("", model.genAspectRatio)
    }

    @Test fun diskRelayPurgesRowsAndMemoriesEvenWhenStaleRemoteDataArrivesAfterDeletion() = runBlocking {
        LocalStore.init(temporary.newFolder())
        LocalStore.setWriteListener(null)
        val id = UUID.randomUUID().toString()
        val conv = Conversation(id = id, messageCount = 2)
        val stale = listOf(msg("u", Role.USER), msg("a").copy(favorited = true))
        LocalStore.writeText(LocalStore.conversationsFile(), AppJson.gson.toJson(listOf(conv)))
        LocalStore.writeText(LocalStore.messagesFile(id), AppJson.gson.toJson(stale))
        LocalStore.writeText(LocalStore.memoryFile(id), AppJson.gson.toJson(listOf(memory("secret", listOf("a")))))
        PerConvStore.put(id, PerConvSettings(atmosphere = "deleted emotion", lastMood = "ANGRY", moodAtMs = 2,
            atmosphereSourceMessageIds = listOf("a")))
        Relay.applyConversations(listOf(Relay.ConvOp.Upsert(conv.copy(messageCount = 1, deletedMessageIds = listOf("a")))))
        Relay.applyMessages(id, stale)
        Relay.applyMemories(id, listOf(memory("secret", listOf("a"))))
        assertEquals(listOf("u"), Relay.messagesFromDisk(id).map { it.id })
        assertTrue(Relay.memories(id).isEmpty())
        assertFalse(LocalStore.readText(LocalStore.messagesFile(id))!!.contains("\"id\":\"a\""))
        assertFalse(LocalStore.readText(LocalStore.memoryFile(id))!!.contains("secret"))
        assertEquals("", PerConvStore.load()[id]!!.atmosphere)
        // Transport shapes stay compatible with the existing opaque JSON-object server.
        assertTrue(Wire.msgsToWire(Relay.messagesFromDisk(id)).isJsonArray)
        val cleared = PerConvBridge.toWire(PerConvStore.load()[id]!!)
        assertFalse(PerConvBridge.unionForPush(PerConvBridge.toWire(PerConvSettings(atmosphere = "old")), cleared).has("atmosphere"))
    }
}
