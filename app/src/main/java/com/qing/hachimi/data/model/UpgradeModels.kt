package com.qing.hachimi.data.model

import java.io.File

data class AudioQualityInfo(
    val format: String,
    val bitrate: Int,
    val sampleRate: Int,
    val bitDepth: Int,
    val encoder: String,
    val fileSize: Long,
    val duration: Long
)

data class SongInfo(
    val id: Long,
    val name: String,
    val artists: String,
    val album: String,
    val localFile: File,
    val qualityInfo: AudioQualityInfo
)

data class OnlineVersion(
    val id: Long,
    val name: String,
    val artists: String,
    val album: String,
    val qualityLevel: String,
    val qualityInfo: AudioQualityInfo,
    val downloadUrl: String?
)

data class ScannedFolder(
    val folder: File,
    val songs: List<SongInfo>,
    val upgradeableCount: Int
)

data class UpgradeProgress(
    val current: Int,
    val total: Int,
    val currentSong: String,
    val speed: Long,
    val estimatedTimeRemaining: Long
)

data class QualityComparison(
    val currentScore: Int,
    val availableScore: Int,
    val improvement: Int,
    val isUpgradeable: Boolean
)

enum class UpgradeMode {
    AUTO_REPLACE,
    MANUAL_CONFIRM,
    KEEP_ORIGINAL,
    BATCH_PROCESS
}

enum class UpgradeStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    SKIPPED
}
