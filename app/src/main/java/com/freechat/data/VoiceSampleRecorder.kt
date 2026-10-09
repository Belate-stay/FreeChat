package com.freechat.data

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class VoiceCaptureState(
    val isRecording: Boolean = false,
    val recordedMs: Long = 0,
    val reachedLimit: Boolean = false,
    val error: String? = null
)

/** Bounded local PCM capture for a voice reference. Stopping/disposing never calls a remote API. */
class VoiceSampleRecorder {
    private class Capture(val recorder: AudioRecord) {
        @Volatile var running = true
        @Volatile var discarded = false
        val bytes = ByteArrayOutputStream()
        val done = CountDownLatch(1)
    }

    private val lock = Any()
    @Volatile private var active: Capture? = null
    private val _state = MutableStateFlow(VoiceCaptureState())
    val state: StateFlow<VoiceCaptureState> = _state.asStateFlow()

    fun start(): Boolean = synchronized(lock) {
        if (active != null) {
            _state.value = _state.value.copy(error = "上次录音尚未结束，请稍后重试。")
            return@synchronized false
        }
        val recorder = try {
            val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) throw IllegalStateException("unsupported input")
            AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 4096) * 2)
        } catch (_: SecurityException) {
            _state.value = VoiceCaptureState(error = "麦克风权限不可用，请允许权限后重新录音。")
            return@synchronized false
        } catch (_: Exception) {
            _state.value = VoiceCaptureState(error = "无法打开麦克风，请稍后重试或上传音频。")
            return@synchronized false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            _state.value = VoiceCaptureState(error = "无法打开麦克风，请稍后重试或上传音频。")
            return@synchronized false
        }
        try { recorder.startRecording() }
        catch (_: Exception) {
            recorder.release()
            _state.value = VoiceCaptureState(error = "无法开始录音，请检查麦克风权限。")
            return@synchronized false
        }
        val capture = Capture(recorder)
        active = capture
        _state.value = VoiceCaptureState(isRecording = true)
        thread(isDaemon = true, name = "mimo-voice-reference") {
            try {
                val buffer = ShortArray(1024)
                val encoded = ByteArray(buffer.size * 2)
                val maximumBytes = VoiceRecordingPolicy.MAX_SECONDS * VoiceRecordingPolicy.PCM_BYTES_PER_SECOND
                while (capture.running && !capture.discarded) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read < 0) {
                        if (capture.running) _state.value = _state.value.copy(error = "录音被中断，请重新录制或上传音频。")
                        break
                    }
                    if (read == 0) continue
                    repeat(read) { index ->
                        val value = buffer[index].toInt()
                        encoded[index * 2] = value.toByte()
                        encoded[index * 2 + 1] = (value shr 8).toByte()
                    }
                    val allowed = minOf(read * 2, maximumBytes - capture.bytes.size())
                    if (allowed > 0) capture.bytes.write(encoded, 0, allowed)
                    val ms = capture.bytes.size().toLong() * 1000 / VoiceRecordingPolicy.PCM_BYTES_PER_SECOND
                    val full = capture.bytes.size() >= maximumBytes
                    _state.value = VoiceCaptureState(isRecording = !full && capture.running && !capture.discarded,
                        recordedMs = ms, reachedLimit = full)
                    if (full) break
                }
            } catch (_: Exception) {
                if (!capture.discarded) _state.value = _state.value.copy(error = "录音失败，请重新录制或上传音频。")
            } finally {
                capture.running = false
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                _state.value = _state.value.copy(isRecording = false)
                capture.done.countDown()
                if (capture.discarded) synchronized(lock) { if (active === capture) active = null }
            }
        }
        true
    }

    suspend fun finish(): ByteArray = withContext(Dispatchers.IO) {
        val capture = synchronized(lock) { active }
            ?: throw VoiceConfigurationException("没有可保存的录音，请重新开始录音。")
        capture.running = false
        runCatching { capture.recorder.stop() }
        if (!capture.done.await(10, TimeUnit.SECONDS)) {
            throw VoiceConfigurationException("录音尚未结束，请稍后重试。")
        }
        synchronized(lock) { if (active === capture) active = null }
        _state.value.error?.let { throw VoiceConfigurationException(it) }
        if (capture.discarded) throw VoiceConfigurationException("这段录音已取消，请重新录制。")
        VoiceRecordingPolicy.wavFromPcm(capture.bytes.toByteArray())
    }

    fun cancel() {
        val capture = synchronized(lock) { active }
        capture?.let {
            it.discarded = true
            it.running = false
            runCatching { it.recorder.stop() }
            if (it.done.count == 0L) synchronized(lock) { if (active === it) active = null }
        }
        _state.value = VoiceCaptureState()
    }
}
