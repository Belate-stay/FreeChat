package com.freechat.data

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Each published band array is a new snapshot; consumers must treat it as read-only. */
data class VoiceSpectrumFrame(val level: Float, val bands: FloatArray)

/** 16 kHz mono PCM analysis; buffers, Hann window and FFT twiddles are reused by one capture thread. */
class VoiceSpectrumAnalyzer {
    private val window = DoubleArray(FFT_SIZE) { 0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1)) }
    private val powerScale = 2.0 / (FFT_SIZE * window.sumOf { it * it })
    private val reversed = IntArray(FFT_SIZE) { Integer.reverse(it) ushr (32 - 9) }
    private val cosine = DoubleArray(FFT_SIZE / 2) { cos(-2.0 * PI * it / FFT_SIZE) }
    private val sine = DoubleArray(FFT_SIZE / 2) { sin(-2.0 * PI * it / FFT_SIZE) }
    private val edges = IntArray(BAND_COUNT + 1) {
        if (it == BAND_COUNT) FFT_SIZE / 2 + 1
        else ceil(80.0 * 100.0.pow(it.toDouble() / BAND_COUNT) * FFT_SIZE / 16000.0).toInt()
    }
    private val real = DoubleArray(FFT_SIZE)
    private val imaginary = DoubleArray(FFT_SIZE)
    private val smoothedBands = FloatArray(BAND_COUNT)
    private var smoothedLevel = 0f

    fun analyze(samples: ShortArray, count: Int = samples.size): VoiceSpectrumFrame {
        require(count in 0..samples.size)
        val used = minOf(count, FFT_SIZE)
        val offset = count - used
        var mean = 0.0
        for (i in 0 until used) mean += samples[offset + i]
        if (used > 0) mean /= used
        real.fill(0.0)
        imaginary.fill(0.0)
        var squareSum = 0.0
        for (i in 0 until used) {
            val value = (samples[offset + i] - mean) / 32768.0
            squareSum += value * value
            real[reversed[i]] = value * window[i]
        }
        val rms = if (used == 0) 0.0 else sqrt(squareSum / used)
        val audible = rms > NOISE_FLOOR
        if (audible) fft()
        smoothedLevel = smooth(smoothedLevel, height(rms))
        for (band in 0 until BAND_COUNT) {
            var power = 0.0
            if (audible) {
                for (bin in edges[band] until edges[band + 1]) {
                    power += real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
                }
            }
            smoothedBands[band] = smooth(smoothedBands[band], height(sqrt(power * powerScale)))
        }
        return VoiceSpectrumFrame(smoothedLevel, smoothedBands.copyOf())
    }

    fun reset() {
        smoothedLevel = 0f
        smoothedBands.fill(0f)
        real.fill(0.0)
        imaginary.fill(0.0)
    }

    private fun height(rms: Double): Float = ((rms - NOISE_FLOOR) * 6.0).toFloat().coerceIn(0f, 1f)

    private fun smooth(current: Float, target: Float): Float =
        (current + (target - current) * if (target > current) 0.55f else 0.18f).coerceIn(0f, 1f)

    private fun fft() {
        var span = 2
        while (span <= FFT_SIZE) {
            val half = span / 2
            val stride = FFT_SIZE / span
            var start = 0
            while (start < FFT_SIZE) {
                for (i in 0 until half) {
                    val a = start + i
                    val b = a + half
                    val twiddle = i * stride
                    val re = cosine[twiddle] * real[b] - sine[twiddle] * imaginary[b]
                    val im = cosine[twiddle] * imaginary[b] + sine[twiddle] * real[b]
                    real[b] = real[a] - re
                    imaginary[b] = imaginary[a] - im
                    real[a] += re
                    imaginary[a] += im
                }
                start += span
            }
            span *= 2
        }
    }

    companion object {
        const val BAND_COUNT = 16
        private const val FFT_SIZE = 512
        private const val NOISE_FLOOR = 0.003
    }
}
