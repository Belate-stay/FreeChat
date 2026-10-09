package com.freechat.data

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class VoiceSpectrumAnalyzerTest {
    @Test fun silenceAndDcDoNotMoveTheSpectrum() {
        listOf(ShortArray(512), ShortArray(512) { 12000 }).forEach { pcm ->
            val frame = Analyzer().analyze(pcm)
            assertEquals(0f, frame.level, 0f)
            assertEquals(16, frame.bands.size)
            assertTrue(frame.bands.all { it == 0f })
        }
    }

    @Test fun sinePeaksMoveFromLowToHighBandsWithFrequency() {
        val peaks = listOf(125.0, 500.0, 2000.0, 6000.0).map { frequency ->
            val analyzer = Analyzer()
            var frame = analyzer.analyze(sine(frequency, 0.15))
            repeat(10) { frame = analyzer.analyze(sine(frequency, 0.15)) }
            assertTrue("A real sine must produce visible frequency energy", frame.bands.max() > 0.1f)
            frame.bands.indices.maxByOrNull { frame.bands[it] }!!
        }
        assertTrue("Bands must remain ordered by frequency: $peaks",
            peaks.zipWithNext().all { (low, high) -> low < high })
    }

    @Test fun louderPcmRaisesLevelAndBandHeightWithoutPerFramePeakNormalization() {
        val quiet = settled(sine(1000.0, 0.02))
        val loud = settled(sine(1000.0, 0.08))
        assertTrue(loud.level > quiet.level * 2f)
        assertTrue(loud.bands.max() > quiet.bands.max() * 2f)
        assertTrue("Quiet frames must keep their absolute loudness", quiet.bands.max() < 0.5f)
    }

    @Test fun dcOffsetDoesNotChangeVoiceLoudnessOrFrequency() {
        val centered = settled(sine(1000.0, 0.08))
        val biased = settled(sine(1000.0, 0.08, dc = 0.2))
        assertEquals(centered.level, biased.level, 0.002f)
        assertArrayEquals(centered.bands, biased.bands, 0.002f)
    }

    @Test fun attackAndReleaseAreSmoothAndEventuallyReturnToSilence() {
        val analyzer = Analyzer()
        val pcm = sine(1000.0, 0.08)
        val first = analyzer.analyze(pcm)
        var settled = first
        repeat(12) { settled = analyzer.analyze(pcm) }
        assertTrue(first.level > 0f && first.level < settled.level)
        assertTrue(first.bands.max() < settled.bands.max())
        var release = analyzer.analyze(ShortArray(512))
        assertTrue(release.level > 0f && release.level < settled.level)
        assertTrue(release.bands.max() > 0f && release.bands.max() < settled.bands.max())
        repeat(36) {
            val next = analyzer.analyze(ShortArray(512))
            assertTrue(next.level <= release.level)
            assertTrue(next.bands.zip(release.bands).all { (a, b) -> a <= b })
            release = next
        }
        assertTrue(release.level < 0.002f)
        assertTrue(release.bands.all { it < 0.002f })
    }

    @Test fun clippedInputStaysFiniteAndNormalized() {
        val analyzer = Analyzer()
        repeat(12) {
            val frame = analyzer.analyze(ShortArray(512) { if (it % 3 == 0) Short.MIN_VALUE else Short.MAX_VALUE })
            assertTrue(frame.level.isFinite() && frame.level in 0f..1f)
            assertTrue(frame.bands.all { it.isFinite() && it in 0f..1f })
        }
    }

    @Test fun analysisUsesOnlyValidPcmAndNeverMutatesPublishedFrames() {
        val pcm = sine(1000.0, 0.08)
        val original = pcm.copyOf()
        val oversized = pcm + ShortArray(512) { Short.MAX_VALUE }
        val expected = Analyzer().analyze(pcm)
        val analyzer = Analyzer()
        val actual = analyzer.analyze(oversized, pcm.size)
        assertEquals(expected.level, actual.level, 0f)
        assertArrayEquals(expected.bands, actual.bands, 0f)
        val published = actual.bands.copyOf()
        val later = analyzer.analyze(sine(6000.0, 0.1))
        assertNotSame(actual.bands, later.bands)
        assertArrayEquals(published, actual.bands, 0f)
        assertArrayEquals(original, pcm)
        val empty = Analyzer().analyze(oversized, 0)
        assertEquals(0f, empty.level, 0f)
        assertTrue(empty.bands.all { it == 0f })
    }

    @Test fun resetRemovesThePreviousRecordingAttackAndReleaseState() {
        val analyzer = Analyzer()
        repeat(10) { analyzer.analyze(sine(1000.0, 0.15)) }
        analyzer.reset()
        val silence = analyzer.analyze(ShortArray(512))
        assertEquals(0f, silence.level, 0f)
        assertTrue(silence.bands.all { it == 0f })
        val fresh = Analyzer().analyze(sine(500.0, 0.08))
        val restarted = analyzer.analyze(sine(500.0, 0.08))
        assertEquals(fresh.level, restarted.level, 0f)
        assertArrayEquals(fresh.bands, restarted.bands, 0f)
    }

    private fun sine(frequency: Double, amplitude: Double, dc: Double = 0.0) = ShortArray(512) {
        ((sin(2.0 * PI * frequency * it / 16000.0) * amplitude + dc) * 32767.0).toInt().toShort()
    }

    private fun settled(pcm: ShortArray): Frame {
        val analyzer = Analyzer()
        var frame = analyzer.analyze(pcm)
        repeat(12) { frame = analyzer.analyze(pcm) }
        return frame
    }

    private data class Frame(val level: Float, val bands: FloatArray)

    // Reflection keeps the initial missing-feature failure an assertion, so RED compiles.
    private class Analyzer {
        private val type = runCatching { Class.forName("com.freechat.data.VoiceSpectrumAnalyzer") }.getOrNull().also {
            assertNotNull("Microphone PCM needs a real VoiceSpectrumAnalyzer", it)
        }!!
        private val delegate = type.getDeclaredConstructor().newInstance()

        fun analyze(pcm: ShortArray, count: Int = pcm.size): Frame {
            val result = type.getMethod("analyze", ShortArray::class.java, Integer.TYPE).invoke(delegate, pcm, count)
            return Frame(result.javaClass.getMethod("getLevel").invoke(result) as Float,
                result.javaClass.getMethod("getBands").invoke(result) as FloatArray)
        }

        fun reset() { type.getMethod("reset").invoke(delegate) }
    }
}
