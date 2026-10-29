package com.freechat.ui.components

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Wiring contracts complement the device check for physical haptics and avatar motion. */
class GroupNudgeFeedback1222Test {
    private fun bubble() = File("src/main/java/com/freechat/ui/components/ChatBubble.kt").readText()
    private fun frame() = bubble().substringAfter("private fun GroupMessageFrame(")
        .substringBefore("private fun GroupSystemRow(")

    @Test fun doubleTapAndAccessibilityNudgeShareFeedbackWhileLongPressStillMentions() {
        val frame = frame()
        assertTrue("Double tap uses the same feedback action as accessibility", frame.contains("onDoubleClick = nudgeMember"))
        assertTrue(frame.contains("CustomAccessibilityAction(s.groupNudgeMember) { nudgeMember(); true }"))
        assertTrue(frame.contains("onLongClick = { onMentionGroupMember?.invoke(memberId) }"))
        assertTrue("A single avatar tap remains inert", frame.contains("onClick = {}"))
    }

    @Test fun onePlatformPulseStartsPromptlyBeforeTheFiniteShakeAndBusinessCallback() {
        val frame = frame()
        val pulse = "view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)"
        assertEquals("Exactly one platform pulse per nudge action", 1, frame.windowed(pulse.length).count { it == pulse })
        assertTrue("Start feedback even when the callback synchronously changes the row",
            frame.contains("scope.launch(start = CoroutineStart.UNDISPATCHED)"))
        assertTrue(frame.indexOf(pulse) < frame.indexOf("nudgeRotation.animateTo("))
        assertTrue(frame.indexOf(pulse) < frame.indexOf("onNudgeGroupMember(memberId)"))
        assertFalse("Respect platform haptic settings", frame.contains("IGNORE_") || frame.contains("Vibrator"))
    }

    @Test fun avatarShakeUsesSharedDurationAndHonorsDisabledAnimations() {
        val frame = frame()
        assertTrue(frame.contains("if (motionEnabled)"))
        assertTrue(frame.contains("keyframes {"))
        assertTrue(frame.contains("durationMillis = FreeChatAnimation.DURATION_NORMAL"))
        assertTrue(frame.contains("rotationZ = if (motionEnabled) nudgeRotation.value else 0f"))
        assertFalse("Nudges must settle instead of looping", frame.contains("infiniteRepeatable") || frame.contains("rememberInfiniteTransition"))
    }

    @Test fun feedbackScopeIsDisposedWhenConversationMessageOrMemberChanges() {
        val frame = frame()
        assertTrue("A recycled row must not transfer its shake or coroutine to another member",
            frame.contains("key(conversationId, message.id, memberId) {"))
        val keyedState = frame.substringAfter("key(conversationId, message.id, memberId) {")
        assertTrue(keyedState.contains("remember { Animatable(0f) }"))
        assertTrue(keyedState.contains("rememberCoroutineScope()"))
    }

    @Test fun removedMembersAndMultiselectKeepTheirExistingInteractionGate() {
        val bubble = bubble()
        val frame = frame()
        assertTrue(bubble.contains("onMentionGroupMember.takeUnless { multiSelectEnabled }"))
        assertTrue(bubble.contains("onNudgeGroupMember.takeUnless { multiSelectEnabled }"))
        assertTrue(frame.contains("memberId != null && identity.isCurrentMember"))
        assertTrue(frame.contains("onNudgeGroupMember != null"))
        assertEquals("Business dispatch remains single and local to the accepted action", 1,
            frame.windowed("onNudgeGroupMember(memberId)".length).count { it == "onNudgeGroupMember(memberId)" })
    }
}
