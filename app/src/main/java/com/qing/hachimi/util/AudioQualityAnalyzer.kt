package com.qing.hachimi.util

import com.qing.hachimi.data.model.AudioQualityInfo
import com.qing.hachimi.data.model.QualityComparison
import org.jaudiotagger.audio.AudioFileIO
import java.io.File

class AudioQualityAnalyzer {

    fun analyzeFile(file: File): AudioQualityInfo {
        return try {
            val audioFile = AudioFileIO.read(file)
            val audioHeader = audioFile.audioHeader

            AudioQualityInfo(
                format = audioHeader.format.lowercase(),
                bitrate = audioHeader.bitRateAsNumber.toInt(),
                sampleRate = audioHeader.sampleRateAsNumber,
                bitDepth = audioHeader.bitsPerSample,
                encoder = audioHeader.encodingType ?: "",
                fileSize = file.length(),
                duration = audioHeader.trackLength.toLong()
            )
        } catch (e: Exception) {
            AppLogger.error("分析音频文件失败: ${file.name}", e)
            AudioQualityInfo(
                format = file.extension.lowercase(),
                bitrate = 0,
                sampleRate = 0,
                bitDepth = 0,
                encoder = "",
                fileSize = file.length(),
                duration = 0
            )
        }
    }

    fun compareQuality(current: AudioQualityInfo, available: AudioQualityInfo): QualityComparison {
        val currentScore = calculateScore(current)
        val availableScore = calculateScore(available)
        val improvement = availableScore - currentScore

        return QualityComparison(
            currentScore = currentScore,
            availableScore = availableScore,
            improvement = improvement,
            isUpgradeable = improvement > 0
        )
    }

    fun calculateScore(quality: AudioQualityInfo): Int {
        var score = 0

        when (quality.format.lowercase()) {
            "flac", "wav", "aiff" -> score += 100
            "alac", "ape" -> score += 90
            "aac", "m4a" -> score += 80
            "mp3" -> score += 70
            "ogg", "opus" -> score += 75
        }

        score += (quality.bitrate * 0.1).toInt()
        score += (quality.sampleRate.toDouble() / 1000 * 0.5).toInt()
        score += (quality.bitDepth * 5)

        return score
    }

    fun getQualityLevel(score: Int): String {
        return when {
            score >= 200 -> "超高质量"
            score >= 150 -> "高质量"
            score >= 100 -> "中质量"
            else -> "低质量"
        }
    }
}
