package com.freechat.ui.components

import com.freechat.ui.animation.FreeChatAnimation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoiceInputContractTest {
    private fun input() = File("src/main/java/com/freechat/ui/components/ChatInput.kt").readText()

    @Test fun collapseHasAPerceptibleSlowStartAndAcceleratesIntoTheMic() {
        val motion = FreeChatAnimation.voiceCollapseTween
        assertTrue("A 280ms fade is not the requested droplet transition", motion.durationMillis >= 400)
        assertTrue("Keep the first quarter almost full size", motion.easing.transform(.25f) < .10f)
        assertTrue("Late motion must be faster than the beginning",
            motion.easing.transform(.95f) - motion.easing.transform(.75f) >
                motion.easing.transform(.25f) - motion.easing.transform(.05f))
    }

    @Test fun restorationHasItsOwnNonBouncingArrivalCurve() {
        val source = File("src/main/java/com/freechat/ui/animation/FreeChatAnimation.kt").readText()
        assertTrue("Restore is not the collapse curve played backwards", source.contains("voiceRestoreTween"))
        assertTrue(input().contains("FreeChatAnimation.voiceRestoreTween"))
    }

    @Test fun dropletFilterSnapshotsUpdatedUniformsInsteadOfAnEmptySize() {
        val source = File("src/main/java/com/freechat/ui/components/VoiceInputShader.kt").readText()
        val apply = source.substringAfter("fun apply(")
        assertFalse("A cached image filter holds the initial zero-size uniform snapshot",
            source.contains("private val effect = RenderEffect.createRuntimeShaderEffect"))
        assertTrue(apply.indexOf("RenderEffect.createRuntimeShaderEffect") > apply.indexOf("shader.setFloatUniform(\"anchorY\""))
    }

    @Test fun liveVoiceRenderingIsNotAnAlwaysRunningSineWave() {
        val source = input()
        assertFalse("The old invisible waveform runs two frame loops at idle", source.contains("GradientWaveform("))
        assertTrue("Spectrum uses actual captured PCM", source.contains("recorder.spectrum"))
        assertTrue("Show bottom bars only while recording or fading out", source.contains("VoiceSpectrumBars("))
    }

    @Test fun recordingCanOnlyBecomeActiveAfterTheMicrophoneStartsSuccessfully() {
        assertTrue("Microphone failure cannot hide the composer", input().contains("if (!recorder.start())"))
    }

    @Test fun releaseUsesTheActualUpPositionAndDoesNotSendOnLostPointer() {
        val source = input()
        assertTrue("UP position can cross the button without a final MOVE", source.contains("VoiceInputMotion.isInsideButton("))
        assertTrue("Lost pointer is not a successful release", source.contains("releasedNormally"))
    }

    @Test fun systemCancellationIsNotATouchReleaseThatCanSendAudio() {
        assertTrue("ACTION_CANCEL can arrive as consumed pressed=false; only an unconsumed UP sends",
            input().contains("change.changedToUp()"))
    }

    @Test fun theStablePointerCoroutineUsesCurrentCaptureCallbacks() {
        val source = input()
        assertTrue("IME height must not be captured by the first microphone touch",
            source.contains("rememberUpdatedState(startRecording)"))
        assertTrue(source.contains("rememberUpdatedState(finishRecording)"))
    }

    @Test fun changingChatOrLeavingThePageCancelsPendingRecognition() {
        val source = input()
        assertTrue("Keep capture tied to its conversation", source.contains("DisposableEffect(voiceSessionKey"))
        assertTrue("Dispose/pause must cancel recognition as well as capture", source.contains("recognitionJob?.cancel()"))
        val screen = File("src/main/java/com/freechat/ui/screens/ChatScreen.kt").readText()
        assertTrue("Capture a plain conversation id, not the live Compose State delegate",
            screen.contains("val voiceConversationId = currentConvId"))
        assertTrue(screen.contains("voiceSessionKey = voiceConversationId"))
        assertTrue("A VM chat switch can precede disposal; guard recognition and delivery synchronously",
            screen.contains("viewModel.currentConversationId.value != voiceConversationId") &&
                screen.contains("viewModel.currentConversationId.value == voiceConversationId"))
    }

    @Test fun ordinaryKeyboardMovementDoesNotUseTheVoiceRestorationTween() {
        assertTrue(input().contains("if (voiceAnchorHeld) restoringBottomSpace.value else normalBottomSpace"))
    }

    @Test fun speechRecognitionReusesTheExistingCancellableSocketPath() {
        val source = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()
            .substringAfter("private suspend fun transcribeAudioOpenAi")
            .substringBefore("val selectedLanguage")
        assertTrue("A cancelled gesture/page must not leave a blocking request alive", source.contains(".awaitText("))
        assertFalse(source.contains(".execute()"))
        assertTrue(source.contains("catch (e: CancellationException)"))
    }
}
