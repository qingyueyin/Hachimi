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

/** 升级成功的单条结果，用于就地更新列表 */
data class UpgradedSong(
    val original: SongInfo,
    val newFile: File,
    val newQuality: AudioQualityInfo?
)

/**
 * 升级成功后把列表里的旧条目替换成新文件，并同步选中路径。
 *
 * 不更新 [SongInfo.qualityInfo] 的话，列表还显示升级前的码率，
 * 再点一次检查会重复报「可升级」，点升级又会被服务端拒绝。
 */
fun List<ScannedFolder>.applyUpgrades(
    upgrades: List<UpgradedSong>,
    selected: Set<String>
): Pair<List<ScannedFolder>, Set<String>> {
    if (upgrades.isEmpty()) return this to selected
    val byOldPath = upgrades.associateBy { it.original.localFile.absolutePath }
    val folders = map { folder ->
        folder.copy(
            songs = folder.songs.map { song ->
                val upgrade = byOldPath[song.localFile.absolutePath] ?: return@map song
                song.copy(
                    name = upgrade.newFile.nameWithoutExtension.substringBefore(" - "),
                    album = upgrade.newFile.parentFile?.name ?: song.album,
                    localFile = upgrade.newFile,
                    qualityInfo = upgrade.newQuality ?: song.qualityInfo,
                )
            }
        )
    }
    val newSelected = selected
        .map { path -> byOldPath[path]?.newFile?.absolutePath ?: path }
        .toSet()
    return folders to newSelected
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
