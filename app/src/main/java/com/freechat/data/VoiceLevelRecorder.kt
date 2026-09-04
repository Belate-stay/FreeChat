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
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * 本地录音采集器：AudioRecord 读取麦克风 PCM（16kHz/16bit/单声道），
 * 一边实时算归一化电平（驱动波形动画），一边累积 PCM 字节（松手后交给 ASR 识别）。
 *
 * 线程模型（根治「主线程 stop vs 录音线程 read」竞态与旧线程复活）：
 * - AudioRecord 的 stop/release 全部收口在录音线程内部完成；
 * - 主线程 stop() 只做三件事：置 running=false、调用 record.stop() 打断阻塞中的 read、
 *   用 CountDownLatch 无超时等待录音线程真正退出。不再有限时 join，杜绝「超时后旧线程
 *   被新的 running=true 唤醒、与新录音并发抢麦克风」的崩溃。
 * - 每次 start 递增 generation，录音线程退出前校验代次，旧线程绝不会被新录音复活。
 */
class VoiceLevelRecorder {

    private val _level = MutableStateFlow(0f)
    /** 实时电平，0 静音 ~ 1 满幅，供波形条 collectAsState */
    val level: StateFlow<Float> = _level.asStateFlow()

    private val pcmBuffer = ByteArrayOutputStream()
    /** 最大累积 PCM 上限：60s × 32000 字节/秒 ≈ 1.92MB，防止长按录音 OOM 打崩进程 */
    private val maxPcmBytes = 60 * 32000

    @Volatile
    private var running = false

    /** 主线程 stop() 时用它打断阻塞中的 read；只由主线程写，录音线程不碰它 */
    @Volatile
    private var record: AudioRecord? = null

    /** 代次：每次 start 递增，录音线程退出前校验，杜绝旧线程被新 running=true 复活 */
    private var generation = 0

    private var captureThread: Thread? = null
    private var threadDone: CountDownLatch? = null

    /** 开始录音。重复调用无副作用。 */
    @Synchronized
    fun start() {
        if (running) return
        _level.value = 0f
        synchronized(pcmBuffer) { pcmBuffer.reset() }

        val minBuf = AudioRecord.getMinBufferSize(
            16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, 1600) // ~50ms @16kHz
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufSize * 2
            )
        } catch (e: Exception) {
            null
        }
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            rec?.release()
            return
        }
        try {
            rec.startRecording()
        } catch (e: Exception) {
            rec.release()
            return
        }

        generation++
        val myGen = generation
        record = rec
        running = true
        val done = CountDownLatch(1)
        threadDone = done

        captureThread = thread(isDaemon = true, name = "voice-capture") {
            try {
                val buf = ShortArray(bufSize)
                val byteBuf = ByteArray(bufSize * 2)
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
                    var sum = 0.0
                    for (i in 0 until n) {
                        val v = buf[i].toDouble()
                        sum += v * v
                        val s = buf[i].toInt()
                        // little-endian 16bit PCM
                        byteBuf[i * 2] = (s and 0xFF).toByte()
                        byteBuf[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    val rms = sqrt(sum / n)
                    val raw = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                    // 非线性放大低音量，让小声说话也有可见波形
                    _level.value = (raw * 6f).coerceAtMost(1f)
                    synchronized(pcmBuffer) {
                        if (pcmBuffer.size() < maxPcmBytes) {
                            pcmBuffer.write(byteBuf, 0, n * 2)
                        }
                    }
                }
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
                done.countDown()
            }
        }
    }

    /** 停止录音，返回累积的 PCM 字节（16kHz/16bit/单声道，little-endian）。 */
    @Synchronized
    fun stop(): ByteArray {
        running = false
        _level.value = 0f
        // 打断阻塞中的 read（AudioRecord.stop 线程安全，会让 read 立即返回）
        runCatching { record?.stop() }
        val done = threadDone
        record = null
        // 无超时等待录音线程真正退出（它在 finally 里 stop/release 后 countDown）
        try {
            done?.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        captureThread = null
        threadDone = null
        return synchronized(pcmBuffer) { pcmBuffer.toByteArray() }
    }
}
