package com.freechat.data

import com.freechat.i18n.buildStrings
import com.freechat.model.DialogueMode
import com.freechat.sync.ActiveShareList
import com.freechat.sync.ShareInfo
import com.freechat.ui.animation.FreeChatAnimation
import org.junit.Assert.*
import org.junit.Test

class Beta97RegressionTest {
    @Test fun photoAndSceneModelControlsAreMutuallyExclusiveByMode() {
        assertTrue(CharacterPresentationPolicy.usesPhotoRecognition(DialogueMode.WECHAT))
        assertFalse(CharacterPresentationPolicy.usesImageGeneration(DialogueMode.WECHAT))
        for (mode in listOf(DialogueMode.ACTION, DialogueMode.PLOT)) {
            assertTrue(CharacterPresentationPolicy.usesImageGeneration(mode))
            assertFalse(CharacterPresentationPolicy.usesPhotoRecognition(mode))
        }
        assertFalse(CharacterPresentationPolicy.usesImageGeneration(null))
        assertFalse(CharacterPresentationPolicy.usesPhotoRecognition(null))
    }

    @Test fun personaInputsAreReadOnlyUntilEditingIsExplicitlyEnabled() {
        assertFalse(CharacterPresentationPolicy.personaEditable(existing = true, editing = false))
        assertTrue(CharacterPresentationPolicy.personaEditable(existing = true, editing = true))
        assertTrue(CharacterPresentationPolicy.personaEditable(existing = false, editing = false))
    }

    @Test fun legacySleepStillAppearsEnabledRatherThanHidingItsBehavior() {
        assertFalse(CharacterPresentationPolicy.timeEnabled(false, false))
        assertTrue(CharacterPresentationPolicy.timeEnabled(false, true))
        assertTrue(CharacterPresentationPolicy.timeEnabled(true, false))
        assertTrue(CharacterPresentationPolicy.timeEnabled(true, true))
    }

    @Test fun bufferedWechatExampleKeepsTheRequestedSixTurnOrder() {
        val s = buildStrings("zh-CN")
        val lines = CharacterPresentationPolicy.wechatExampleOrder.map { (user, index) ->
            (if (user) "用户：" else "AI：") + (if (user) s.wechatExampleUser else s.wechatExampleAi)[index]
        }
        assertEquals(listOf("用户：吃饭了吗？", "AI：刚到食堂", "AI：你说我是吃螺狮粉还是猪脚饭？",
            "用户：猪脚饭！", "用户：超好吃！", "AI：好的那我就吃螺狮粉了"), lines)
    }

    @Test fun originalLearningOptionalMarkerLivesInTheHintNotTheHeadingInEveryLocale() {
        for (locale in listOf("zh-CN", "zh-TW", "en")) {
            val s = buildStrings(locale)
            assertFalse(s.originalLearning.contains("可选") || s.originalLearning.contains("可選") || s.originalLearning.contains("optional"))
            assertTrue(s.originalLearningHint.endsWith("（可选）") || s.originalLearningHint.endsWith("（可選）") || s.originalLearningHint.endsWith("(optional)"))
            assertTrue(s.timePerceptionConfirm.isNotBlank())
            assertTrue(s.chooseDialogueMode.isNotBlank())
            assertEquals(3, s.wechatExampleUser.size)
            assertEquals(3, s.wechatExampleAi.size)
        }
    }

    @Test fun simplifiedTimeCopyMatchesTheRequestedWording() {
        val s = buildStrings("zh-CN")
        assertEquals("角色拥有独立人格、独立生活与作息，与现实时间轴所匹配", s.timePerceptionDesc)
        assertEquals("AI拥有自己的作息，意味着AI可能不回复消息。", s.timePerceptionConfirm)
    }

    @Test fun revokedServerRowsNeverAppearInMyShares() {
        val state = ActiveShareList()
        assertEquals(listOf("active"), state.visible(listOf(
            ShareInfo(id = "revoked", revoked = true), ShareInfo(id = "active"))).map { it.id })
    }

    @Test fun successfulRevokeSurvivesAStaleInFlightReload() {
        val state = ActiveShareList()
        val stale = listOf(ShareInfo(id = "a"), ShareInfo(id = "b"))
        state.revoked("a")
        assertEquals(listOf("b"), state.visible(stale).map { it.id })
        state.revoked("b")
        assertTrue(state.visible(stale).isEmpty())
    }

    @Test fun repeatedRevokeIsIdempotentAndFailureWithoutConfirmationDoesNotEraseRows() {
        val state = ActiveShareList()
        val rows = listOf(ShareInfo(id = "a"), ShareInfo(id = "b"))
        assertEquals(rows, state.visible(rows))
        state.revoked("a"); state.revoked("a")
        assertEquals(listOf("b"), state.visible(rows).map { it.id })
    }

    @Test fun motionIsPerceptibleBoundedAndUsesARealNonlinearArrivalCurve() {
        assertTrue(FreeChatAnimation.DURATION_MESSAGE in 420..520)
        assertTrue(FreeChatAnimation.DURATION_DISCLOSURE in 340..440)
        assertTrue(FreeChatAnimation.DURATION_COLLAPSE < FreeChatAnimation.DURATION_DISCLOSURE)
        assertEquals(FreeChatAnimation.DURATION_DISCLOSURE, FreeChatAnimation.disclosureFollowTween.durationMillis)
        assertTrue(FreeChatAnimation.inputShowTween.durationMillis > FreeChatAnimation.DURATION_REPLACE)
        val quarter = FreeChatAnimation.arrivalEase.transform(0.25f)
        assertTrue(quarter > 0.25f && quarter < 0.85f)
        var previous = -1f
        for (i in 0..100) {
            val value = FreeChatAnimation.arrivalEase.transform(i / 100f)
            assertTrue(value in 0f..1f && value >= previous)
            previous = value
        }
    }
}
