package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class VoiceSpectrumCaptureTest {
    private fun recorder() = File("src/main/java/com/freechat/data/VoiceLevelRecorder.kt").readText()

    @Test fun microphoneCapturePublishesSpectrumFromItsRealPcm() {
        val source = recorder()
        assertTrue("Recording feedback needs real ordered bands", source.contains("val spectrum: StateFlow<VoiceSpectrumFrame>"))
        assertTrue("Frequency analysis must use the same PCM read for ASR",
            Regex("analyzer\\.analyze\\(buf, n\\)").containsMatchIn(source))
        assertTrue("The recorder must retain its compatible level flow", source.contains("val level: StateFlow<Float>"))
    }

    @Test fun startReportsWhetherTheMicrophoneReallyStarted() {
        val source = recorder()
        assertTrue("UI must not animate a failed recording", source.contains("fun start(): Boolean"))
        assertTrue("Initialized AudioRecord can still fail to enter recording state",
            source.contains("AudioRecord.RECORDSTATE_RECORDING"))
    }

    @Test fun cancellationDiscardsPcmWithoutWaitingOrCopyingIt() {
        val source = recorder()
        assertTrue("Gesture/lifecycle cancellation needs an immediate discard entry", source.contains("fun cancel()"))
        val cancel = source.substringAfter("fun cancel()").substringBefore("private fun")
        assertTrue("Cancel must stop the capture", cancel.contains("requestStop()"))
        assertTrue("Cancelled audio must be discarded", cancel.contains("pcmBuffer.reset()"))
        assertFalse("Cancel must not allocate an audio snapshot", cancel.contains("toByteArray()"))
        assertFalse("Cancel must not wait on the capture thread", cancel.contains("await("))
    }

    @Test fun stopIsBoundedWhileReleaseAndSessionIsolationStayInTheCaptureThread() {
        val source = recorder()
        assertFalse("Main-thread stop must not wait indefinitely for read", source.contains("done?.await()"))
        assertTrue("Old capture sessions must remain isolated", source.contains("myGen == generation"))
        assertTrue("AudioRecord release must always complete before signaling capture completion",
            Regex("finally\\s*\\{[\\s\\S]*rec\\.release\\(\\)[\\s\\S]*done\\.countDown\\(\\)").containsMatchIn(source))
    }
}
