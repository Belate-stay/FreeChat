package com.freechat.data

import com.freechat.model.CharacterProfile
import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.ui.components.BottomFollowPolicy
import com.google.gson.JsonParser
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class Beta812RegressionTest {
    private fun wire(seedream: Boolean = false) = Buffer().also {
        ImageApiRequest.body("gpt-image-2.5", "用户指定水彩，横向构图", seedream).writeTo(it)
    }.readUtf8()

    @Test fun generationRequestOnlySendsEssentialFieldsAndPreservesUserPrompt() {
        for (seedream in listOf(false, true)) {
            val json = JsonParser.parseString(wire(seedream)).asJsonObject
            assertEquals(setOf("model", "prompt"), json.keySet())
            assertEquals("gpt-image-2.5", json.get("model").asString)
            assertEquals("用户指定水彩，横向构图", json.get("prompt").asString)
        }
    }

    @Test fun multipartDoesNotReintroduceLegacyPresets() {
        val buffer = Buffer()
        ImageApiRequest.body("gpt-image-2.5", "当前场景", false,
            listOf(ImageApiRequest.Reference(byteArrayOf(1, 2, 3), "image/png"))).writeTo(buffer)
        val wire = buffer.readUtf8()
        assertTrue(wire.contains("name=\"image[]\""))
        for (field in listOf("size", "quality", "style", "aspect_ratio", "resolution", "n"))
            assertFalse(wire.contains("name=\"$field\""))
    }

    @Test fun scenePromptLeavesFormatToTheModelWithoutOverridingKnownFacts() {
        val prompt = SceneImagePrompt.build(CharacterProfile(name = "旅人", appearanceText = "银发，水彩风格"),
            listOf(Message(role = Role.USER, content = "窗边看雪")), emptyList())
        assertTrue(prompt.contains("自主选择合适的画幅比例、分辨率和视觉风格"))
        assertTrue(prompt.contains("银发，水彩风格"))
        assertFalse(prompt.contains("1024x1024"))
    }

    private val user = Message(id = "u", role = Role.USER, content = "雨夜等船")
    private val ai = Message(id = "a", role = Role.ASSISTANT, content = "站在岸边")
    private val scene = Message(id = "scene", role = Role.ASSISTANT, content = "视觉呈现",
        sceneVisualization = true, imageUrls = listOf("/fixture.png"))

    @Test fun successfulSceneLocksBothPromptAndTheReplyItVisualized() {
        assertEquals(setOf("u", "a"), CompanionFeaturePolicy.sceneLockedMessageIds(listOf(user, ai, scene)))
    }

    @Test fun sceneDeletionOnlyTargetsTheVisualRecordIncludingAFailedOne() {
        val rows = listOf(user, ai, scene.copy(failed = true))
        assertEquals(setOf("scene"), CompanionFeaturePolicy.sceneDeletionIds(rows, "scene"))
        assertTrue(CompanionFeaturePolicy.sceneDeletionIds(rows, "u").isEmpty())
        assertTrue(CompanionFeaturePolicy.sceneDeletionIds(rows, "a").isEmpty())
        assertTrue(CompanionFeaturePolicy.sceneDeletionIds(rows, "missing").isEmpty())
    }

    @Test fun failedPendingAndOrdinaryImagesDoNotFreezeTheScene() {
        for (row in listOf(scene.copy(failed = true), scene.copy(isStreaming = true),
            scene.copy(imageUrls = emptyList()), scene.copy(sceneVisualization = false)))
            assertTrue(CompanionFeaturePolicy.sceneLockedMessageIds(listOf(user, ai, row)).isEmpty())
    }

    @Test fun continuationRemainsEditableAndDeletingAllRelevantScenesUnlocks() {
        val next = user.copy(id = "next", content = "上船")
        assertEquals(setOf("u", "a"), CompanionFeaturePolicy.sceneLockedMessageIds(listOf(user, ai, scene, next)))
        assertTrue(CompanionFeaturePolicy.sceneLockedMessageIds(listOf(user, ai, next)).isEmpty())
        assertEquals(setOf("u", "a"), CompanionFeaturePolicy.sceneLockedMessageIds(listOf(user, ai, scene.copy(id = "other"))))
    }

    @Test fun textualStagesAreMonotonicAndOnlyExplicitEventsAdvanceThem() {
        var phase = GenerationPhase.CONNECTING
        phase = phase.advance(GenerationPhase.UNDERSTANDING)
        assertEquals(GenerationPhase.UNDERSTANDING, phase.advance(GenerationPhase.CONNECTING))
        assertEquals(GenerationPhase.DRAFTING, phase.advance(GenerationPhase.DRAFTING))
        phase = phase.advance(GenerationPhase.SEARCHING).advance(GenerationPhase.DEEP_RETRIEVAL)
        assertEquals(GenerationPhase.DEEP_RETRIEVAL, phase.advance(GenerationPhase.SEARCHING))
        assertEquals(GenerationPhase.DRAFTING, phase.advance(GenerationPhase.DRAFTING))
    }

    @Test fun imageToolCanStartAfterTextDecisionButOldTextDoesNotRegressImageProgress() {
        val phase = GenerationPhase.DRAFTING.advance(GenerationPhase.IMAGE_CONTEXT)
            .advance(GenerationPhase.IMAGE_CONNECTING).advance(GenerationPhase.IMAGE_GENERATING)
        assertEquals(GenerationPhase.IMAGE_GENERATING, phase.advance(GenerationPhase.UNDERSTANDING))
        assertEquals(GenerationPhase.IMAGE_GENERATING, phase.advance(GenerationPhase.IMAGE_CONNECTING))
        assertEquals(GenerationPhase.IMAGE_RECEIVING, phase.advance(GenerationPhase.IMAGE_RECEIVING))
    }

    @Test fun bottomIntentSurvivesGrowingReasoningAndContentAndFinalHandoff() {
        val policy = BottomFollowPolicy()
        policy.observe(true, false)
        repeat(20) { policy.observe(false, false); assertTrue(policy.following) }
    }

    @Test fun startingFromHistoryNeverFollowsNewContent() {
        val policy = BottomFollowPolicy()
        policy.observe(false, false)
        repeat(20) { policy.observe(false, false); assertFalse(policy.following) }
        policy.onGenerationRequested(false)
        assertFalse(policy.following)
    }

    @Test fun gestureCancelsBeforeMovementAndDoesNotResumeOnStaleBottomGeometry() {
        val policy = BottomFollowPolicy()
        policy.observe(true, false)
        policy.onUserGesture()
        policy.observe(true, false) // Pre-scroll frame; geometry still at the old end.
        assertFalse(policy.following)
        policy.observe(false, true)
        policy.observe(false, false)
        repeat(20) { policy.observe(false, false); assertFalse(policy.following) }
    }

    @Test fun returningToActualBottomResumesOnlyAfterTheGestureSettles() {
        val policy = BottomFollowPolicy()
        policy.observe(false, false)
        policy.onUserGesture()
        policy.observe(true, true)
        assertFalse(policy.following)
        policy.observe(true, false)
        assertTrue(policy.following)
        policy.observe(false, false)
        assertTrue(policy.following)
    }

    @Test fun fastGestureStillSettlesWhenSnapshotConflationSkipsItsMovingFrame() {
        val policy = BottomFollowPolicy()
        policy.observe(true, false)
        policy.onUserGesture()
        policy.onUserMoved()
        policy.observe(false, false)
        assertFalse(policy.following)
        policy.onUserGesture()
        policy.onUserMoved()
        policy.observe(true, false)
        assertTrue(policy.following)
    }

    @Test fun disclosureAnimationDoesNotCompeteWithStreamingFollower() {
        val policy = BottomFollowPolicy()
        policy.observe(true, false)
        policy.onDisclosureToggle("sources")
        policy.observe(true, false)
        assertFalse(policy.following)
        policy.onDisclosureSettled("unrelated-reasoning")
        policy.observe(true, false)
        assertFalse(policy.following)
        policy.onDisclosureSettled("sources")
        policy.observe(false, false)
        assertFalse(policy.following)
        policy.observe(true, false)
        assertTrue(policy.following)
    }
}
