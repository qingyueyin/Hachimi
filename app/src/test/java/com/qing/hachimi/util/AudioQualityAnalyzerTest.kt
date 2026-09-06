package com.qing.hachimi.util

import com.qing.hachimi.data.model.AudioQualityInfo
import org.junit.Test
import org.junit.Assert.*

class AudioQualityAnalyzerTest {

    private val analyzer = AudioQualityAnalyzer()

    @Test
    fun `test calculateScore with FLAC format`() {
        val quality = AudioQualityInfo(
            format = "flac",
            bitrate = 1000,
            sampleRate = 44100,
            bitDepth = 16,
            encoder = "FLAC",
            fileSize = 10000000,
            duration = 180000
        )

        val score = analyzer.calculateScore(quality)
        assertEquals(302, score)
    }

    @Test
    fun `test calculateScore with MP3 format`() {
        val quality = AudioQualityInfo(
            format = "mp3",
            bitrate = 320,
            sampleRate = 44100,
            bitDepth = 16,
            encoder = "LAME",
            fileSize = 8000000,
            duration = 180000
        )

        val score = analyzer.calculateScore(quality)
        assertEquals(204, score)
    }

    @Test
    fun `test compareQuality with upgradeable`() {
        val current = AudioQualityInfo(
            format = "mp3",
            bitrate = 128,
            sampleRate = 44100,
            bitDepth = 16,
            encoder = "LAME",
            fileSize = 5000000,
            duration = 180000
        )

        val available = AudioQualityInfo(
            format = "flac",
            bitrate = 1000,
            sampleRate = 44100,
            bitDepth = 16,
            encoder = "FLAC",
            fileSize = 10000000,
            duration = 180000
        )

        val comparison = analyzer.compareQuality(current, available)
        assertTrue(comparison.isUpgradeable)
        assertTrue(comparison.improvement > 0)
    }

    @Test
    fun `test getQualityLevel`() {
        assertEquals("超高质量", analyzer.getQualityLevel(250))
        assertEquals("高质量", analyzer.getQualityLevel(180))
        assertEquals("中质量", analyzer.getQualityLevel(120))
        assertEquals("低质量", analyzer.getQualityLevel(80))
    }
}
