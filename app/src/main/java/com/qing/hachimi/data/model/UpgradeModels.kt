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

/** 扫描过程中把新发现的一批歌曲并入对应文件夹；文件夹不存在就新建 */
fun List<ScannedFolder>.appendSongs(folder: File, songs: List<SongInfo>): List<ScannedFolder> {
    if (songs.isEmpty()) return this
    val index = indexOfFirst { it.folder == folder }
    return if (index < 0) {
        this + ScannedFolder(folder = folder, songs = songs, upgradeableCount = songs.size)
    } else {
        toMutableList().also {
            val old = it[index]
            it[index] = old.copy(songs = old.songs + songs)
        }
    }
}

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
