package com.freechat.data

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 本地录音采集器：AudioRecord 读取麦克风 PCM（16kHz/16bit/单声道），
 * 同一份 PCM 驱动电平和频谱动画，并累积字节供松手后的 ASR 识别。
 *
 * 线程模型（根治「主线程 stop vs 录音线程 read」竞态与旧线程复活）：
 * - 活跃 AudioRecord 的 release 由录音线程完成；stop/cancel 可打断它的阻塞 read。
 * - cancel() 只发出停止信号并丢弃 PCM；stop() 有限等待，调用方可放在 IO 线程。
 * - 旧线程释放前不允许 start，有限等待不会让两个代次同时持有麦克风。
 * - 每次 start 递增 generation，录音线程退出前校验代次，旧线程绝不会被新录音复活。
 */
class VoiceLevelRecorder {

    private val _level = MutableStateFlow(0f)
    /** 实时电平，0 静音 ~ 1 满幅，供波形条 collectAsState */
    val level: StateFlow<Float> = _level.asStateFlow()
    private val _spectrum = MutableStateFlow(VoiceSpectrumFrame(0f, FloatArray(VoiceSpectrumAnalyzer.BAND_COUNT)))
    val spectrum: StateFlow<VoiceSpectrumFrame> = _spectrum.asStateFlow()

    private val pcmBuffer = ByteArrayOutputStream()
    /** 最大累积 PCM 上限：60s × 32000 字节/秒 ≈ 1.92MB，防止长按录音 OOM 打崩进程 */
    private val maxPcmBytes = 60 * 32000

    @Volatile
    private var running = false

    /** stop/cancel 用它打断阻塞中的 read；release 仍由录音线程负责。 */
    @Volatile
    private var record: AudioRecord? = null

    /** 代次：每次 start 递增，录音线程退出前校验，杜绝旧线程被新 running=true 复活 */
    @Volatile
    private var generation = 0

    private var captureThread: Thread? = null
    private var threadDone: CountDownLatch? = null

    /** 开始录音。重复调用无副作用。 */
    @Synchronized
    fun start(): Boolean {
        if (running) return true
        if (threadDone?.count == 1L) return false
        _level.value = 0f
        _spectrum.value = VoiceSpectrumFrame(0f, FloatArray(VoiceSpectrumAnalyzer.BAND_COUNT))
        synchronized(pcmBuffer) { pcmBuffer.reset() }

        val minBuf = runCatching {
            AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        }.getOrElse { return false }
        if (minBuf <= 0) return false
        val bufSize = maxOf(minBuf, 2560)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
        } catch (_: SecurityException) {
            // Permission may be revoked after the UI check; never start a capture session.
            null
        } catch (e: Exception) {
            null
        }
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            rec?.release()
            return false
        }
        try {
            rec.startRecording()
        } catch (_: SecurityException) {
            rec.release()
            return false
        } catch (e: Exception) {
            rec.release()
            return false
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            runCatching { rec.stop() }
            rec.release()
            return false
        }

        generation++
        val myGen = generation
        record = rec
        running = true
        val done = CountDownLatch(1)
        threadDone = done

        captureThread = thread(isDaemon = true, name = "voice-capture") {
            try {
                val analyzer = VoiceSpectrumAnalyzer()
                val buf = ShortArray(640) // 40ms @16kHz：频谱约 25Hz
                val byteBuf = ByteArray(buf.size * 2)
                while (running && myGen == generation) {
                    val n = try {
                        rec.read(buf, 0, buf.size)
                    } catch (e: Exception) {
                        Log.w("VoiceLevelRecorder", "read failed", e)
                        break
                    }
                    if (n <= 0) {
                        if (n < 0) break  // ERROR_DEAD_OBJECT / ERROR_INVALID_OPERATION 等，出错退出
                        Thread.sleep(1)   // 无数据时让渡 CPU，避免高速空转加剧 ANR
                        continue
                    }
                    if (!running || myGen != generation) break
                    val frame = analyzer.analyze(buf, n)
                    for (i in 0 until n) {
                        val s = buf[i].toInt()
                        // little-endian 16bit PCM
                        byteBuf[i * 2] = (s and 0xFF).toByte()
                        byteBuf[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    synchronized(pcmBuffer) {
                        if (running && myGen == generation) {
                            _level.value = frame.level
                            _spectrum.value = frame
                            val remaining = maxPcmBytes - pcmBuffer.size()
                            if (remaining > 0) pcmBuffer.write(byteBuf, 0, minOf(n * 2, remaining))
                        }
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (t: Throwable) {
                // 兜底：OOM 等 Error 不再走默认 handler 打崩整个进程
                Log.e("VoiceLevelRecorder", "capture thread crashed", t)
            } finally {
                try {
                    rec.stop()
                } catch (_: Exception) {
                }
                try {
                    rec.release()
                } catch (_: Exception) {
                }
                synchronized(pcmBuffer) {
                    if (myGen == generation) {
                        running = false
                        record = null
                        _level.value = 0f
                        _spectrum.value = VoiceSpectrumFrame(0f, FloatArray(VoiceSpectrumAnalyzer.BAND_COUNT))
                    }
                }
                done.countDown()
            }
        }
        return true
    }

    /** 停止录音，返回累积的 PCM 字节（16kHz/16bit/单声道，little-endian）。 */
    fun stop(): ByteArray {
        val (done, pcm) = synchronized(this) {
            requestStop()
            threadDone to synchronized(pcmBuffer) { pcmBuffer.toByteArray().also { pcmBuffer.reset() } }
        }
        try {
            done?.await(120, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return pcm
    }

    /** 取消录音：不复制 PCM，不等待录音线程，适合手势取消及页面销毁。 */
    @Synchronized
    fun cancel() {
        requestStop()
        synchronized(pcmBuffer) { pcmBuffer.reset() }
    }

    private fun requestStop() {
        synchronized(pcmBuffer) {
            running = false
            _level.value = 0f
            _spectrum.value = VoiceSpectrumFrame(0f, FloatArray(VoiceSpectrumAnalyzer.BAND_COUNT))
        }
        runCatching { record?.stop() }
        captureThread?.interrupt()
    }
}
