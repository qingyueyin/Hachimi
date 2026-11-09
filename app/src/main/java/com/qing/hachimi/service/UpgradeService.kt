package com.qing.hachimi.service

import com.qing.hachimi.data.api.UpgradeApi
import com.qing.hachimi.data.local.UpgradeHistoryManager
import com.qing.hachimi.data.model.*
import com.qing.hachimi.downloader.DownloadEngine
import com.qing.hachimi.downloader.DownloadStatus
import com.qing.hachimi.util.AudioQualityAnalyzer
import com.qing.hachimi.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class UpgradeService(
    val audioAnalyzer: AudioQualityAnalyzer,
    private val upgradeApi: UpgradeApi,
    private val historyManager: UpgradeHistoryManager,
    private val downloadEngine: DownloadEngine
) {

    companion object {
        private const val DOWNLOAD_TIMEOUT_MS = 15 * 60 * 1000L
        private const val DOWNLOAD_POLL_INTERVAL_MS = 100L
        private const val SEARCH_TIMEOUT_MS = 15_000L
        private const val MAX_CONCURRENT_SEARCHES = 3
        private const val EMIT_EVERY = 20
        private val AUDIO_FORMATS = setOf("mp3", "flac", "wav", "aac", "ogg", "opus", "m4a", "wma", "ape", "wv")
    }

    private val searchCache = ConcurrentHashMap<String, List<OnlineVersion>>()

    /** 本次创建过的临时目录；中途取消/异常时下面没有执行 tempDir.delete()，在这里统一清掉 */
    private val seenTempDirs: MutableSet<File> = java.util.Collections.synchronizedSet(mutableSetOf())

    suspend fun scanFolders(
        folders: List<File>,
        onProgress: (current: Int, total: Int, folderName: String) -> Unit = { _, _, _ -> }
    ): List<ScannedFolder> = scanFoldersIncremental(folders, onProgress)

    /**
     * 扫描文件夹并逐首回调结果，方便界面边扫边显示。
     * onSongs 在 IO 线程调用，调用方自行切回主线程更新界面。
     */
    suspend fun scanFoldersIncremental(
        folders: List<File>,
        onProgress: (current: Int, total: Int, folderName: String) -> Unit = { _, _, _ -> },
        onSongs: (folder: File, songs: List<SongInfo>) -> Unit = { _, _ -> }
    ): List<ScannedFolder> = withContext(Dispatchers.IO) {
        folders.mapIndexed { index, folder ->
            onProgress(index + 1, folders.size, folder.name)
            val songs = scanFolder(folder) { batch -> onSongs(folder, batch) }
            val upgradeableCount = songs.count { song ->
                !historyManager.isUpgraded(song.localFile)
            }

            ScannedFolder(
                folder = folder,
                songs = songs,
                upgradeableCount = upgradeableCount
            )
        }
    }

    /**
     * @param onBatch 每分析完一首就追加到批缓冲，缓冲满 [EMIT_EVERY] 条回调一次；
     *                500 首的歌单如果只在结束后一次性交给界面，会连续构建几百行内容、直接卡住主线程。
     */
    private fun scanFolder(folder: File, onBatch: (List<SongInfo>) -> Unit = {}): List<SongInfo> {
        if (!folder.exists() || !folder.isDirectory) {
            return emptyList()
        }

        val pending = mutableListOf<SongInfo>()
        val parsed = folder.walkTopDown()
            .filter { it.isFile && isAudioFile(it) }
            .map { file ->
                try {
                    val qualityInfo = audioAnalyzer.analyzeFile(file)
                    val fileName = file.nameWithoutExtension

                    val parts = fileName.split(" - ", limit = 2)
                    val name = parts.getOrNull(0)?.trim().orEmpty().ifBlank { fileName }
                    val artist = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "未知艺术家" }

                    SongInfo(
                        id = 0,
                        name = name,
                        artists = artist,
                        album = file.parentFile?.name ?: "未知专辑",
                        localFile = file,
                        qualityInfo = qualityInfo
                    )
                } catch (e: Exception) {
                    AppLogger.error("解析文件失败: ${file.name}", e)
                    null
                }
            }
            .filterNotNull()
            .toList()

        val songs = mutableListOf<SongInfo>()
        for (song in parsed) {
            songs += song
            pending += song
            if (pending.size >= EMIT_EVERY) {
                onBatch(pending.toList())
                pending.clear()
            }
        }
        if (pending.isNotEmpty()) onBatch(pending.toList())
        return songs
    }

    suspend fun searchOnline(song: SongInfo, cookies: Map<String, String>): List<OnlineVersion> {
        return withContext(Dispatchers.IO) {
            val cacheKey = buildString {
                append(cookies["MUSIC_U"]?.hashCode() ?: 0)
                append('|')
                append(song.name.trim().lowercase())
                append('|')
                append(song.artists.trim().lowercase())
            }
            searchCache[cacheKey]?.let { return@withContext it }

            val results = upgradeApi.searchSong(song.name, song.artists, cookies)

            val matched = results.filter { onlineVersion ->
                val confidence = upgradeApi.matchConfidence(song.name, song.artists, onlineVersion)
                confidence >= 0.7
            }.sortedByDescending { onlineVersion ->
                audioAnalyzer.calculateScore(onlineVersion.qualityInfo)
            }
            searchCache[cacheKey] = matched
            matched
        }
    }

    suspend fun findUpgradeCandidates(
        songs: List<SongInfo>,
        cookies: Map<String, String>,
        onProgress: (current: Int, total: Int, songName: String) -> Unit = { _, _, _ -> },
    ): List<Pair<SongInfo, OnlineVersion>> = supervisorScope {
        val semaphore = Semaphore(MAX_CONCURRENT_SEARCHES)
        val completed = AtomicInteger(0)

        songs.map { song ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        val bestVersion = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                            searchOnline(song, cookies).firstOrNull { version ->
                                audioAnalyzer.compareQuality(
                                    song.qualityInfo,
                                    version.qualityInfo,
                                ).isUpgradeable
                            }
                        }
                        bestVersion?.let { song to it }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.error("检查在线音质失败: ${song.name}", e)
                        null
                    } finally {
                        onProgress(completed.incrementAndGet(), songs.size, song.name)
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun upgradeSong(
        song: SongInfo,
        targetVersion: OnlineVersion,
        mode: UpgradeMode,
        cookies: Map<String, String>
    ): UpgradeResult = withContext(Dispatchers.IO) {
        val downloadTaskId = -(targetVersion.id.coerceAtLeast(1L))
        try {
            if (mode != UpgradeMode.AUTO_REPLACE && mode != UpgradeMode.KEEP_ORIGINAL) {
                return@withContext UpgradeResult(
                    success = false,
                    error = "暂不支持该升级模式",
                    originalFile = song.localFile,
                    upgradedFile = null
                )
            }

            val downloadUrl = upgradeApi.getSongUrl(
                targetVersion.id,
                targetVersion.qualityLevel,
                cookies
            )

            if (downloadUrl == null) {
                return@withContext UpgradeResult(
                    success = false,
                    error = "无法获取下载链接",
                    originalFile = song.localFile,
                    upgradedFile = null
                )
            }

            val tempDir = File(song.localFile.parentFile, "upgrade_temp")
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                throw IllegalStateException("无法创建升级临时目录")
            }
            seenTempDirs.add(tempDir)
            // 音频格式已知，直接按它命名：CDN 链接没有扩展名时，按链接猜会被存成 .mp3，后面的音质检测就失效了
            val expectedFormat = downloadUrl.substringBefore('?')
                .substringAfterLast('/')
                .substringAfterLast('.')
                .lowercase()
                .takeIf { it in AUDIO_FORMATS }
            downloadEngine.startDownload(
                songId = downloadTaskId,
                name = song.name,
                artists = song.artists,
                album = song.album,
                url = downloadUrl,
                dir = tempDir,
                qualityLabel = targetVersion.qualityLevel,
                overwrite = true,
                applyFolderGrouping = false,
                persistHistory = false,
                expectedFormat = expectedFormat,
            )

            val downloadedFile = awaitDownloadedFile(downloadTaskId)
            val downloadedQuality = audioAnalyzer.analyzeFile(downloadedFile)
            val qualityComparison = audioAnalyzer.compareQuality(
                song.qualityInfo,
                downloadedQuality,
            )
            if (!qualityComparison.isUpgradeable) {
                downloadedFile.delete()
                throw IllegalStateException("服务器返回的文件音质未高于原文件")
            }

            val result = when (mode) {
                UpgradeMode.AUTO_REPLACE -> {
                    val (backupFile, upgradedFile) = installReplacement(song.localFile, downloadedFile)
                    historyManager.addRecord(
                        UpgradeHistoryManager.UpgradeRecord(
                            originalFile = backupFile,
                            upgradedFile = upgradedFile,
                            originalQuality = "${song.qualityInfo.format} ${song.qualityInfo.bitrate}kbps",
                            upgradedQuality = "${downloadedQuality.format} ${downloadedQuality.bitrate}kbps",
                            timestamp = System.currentTimeMillis(),
                            status = UpgradeStatus.COMPLETED
                        )
                    )
                    UpgradeResult(
                        success = true,
                        error = null,
                        originalFile = song.localFile,
                        upgradedFile = upgradedFile
                    )
                }
                UpgradeMode.KEEP_ORIGINAL -> {
                    val upgradedFile = installAlongsideOriginal(
                        song.localFile,
                        downloadedFile,
                        targetVersion.qualityLevel
                    )
                    historyManager.addRecord(
                        UpgradeHistoryManager.UpgradeRecord(
                            originalFile = song.localFile,
                            upgradedFile = upgradedFile,
                            originalQuality = "${song.qualityInfo.format} ${song.qualityInfo.bitrate}kbps",
                            upgradedQuality = "${downloadedQuality.format} ${downloadedQuality.bitrate}kbps",
                            timestamp = System.currentTimeMillis(),
                            status = UpgradeStatus.COMPLETED
                        )
                    )
                    UpgradeResult(
                        success = true,
                        error = null,
                        originalFile = song.localFile,
                        upgradedFile = upgradedFile
                    )
                }
            }

            downloadEngine.forgetTask(downloadTaskId)
            tempDir.delete()
            seenTempDirs.remove(tempDir)
            result
        } catch (e: CancellationException) {
            downloadEngine.removeTask(downloadTaskId)
            cleanTempDirQuietly(song)
            throw e
        } catch (e: Exception) {
            downloadEngine.removeTask(downloadTaskId)
            cleanTempDirQuietly(song)
            AppLogger.error("升级失败: ${song.name}", e)
            UpgradeResult(
                success = false,
                error = e.message ?: "未知错误",
                originalFile = song.localFile,
                upgradedFile = null
            )
        }
    }

    /** 升级中断时清掉临时目录；只在本次确实创建过、且目录已经没有别的文件时才删 */
    private fun cleanTempDirQuietly(song: SongInfo) {
        val tempDir = File(song.localFile.parentFile, "upgrade_temp")
        if (!seenTempDirs.remove(tempDir)) return
        try {
            tempDir.deleteRecursively()
        } catch (e: Exception) {
            AppLogger.error("清理升级临时目录失败", e)
        }
    }

    private suspend fun awaitDownloadedFile(taskId: Long): File = withTimeout(DOWNLOAD_TIMEOUT_MS) {
        while (true) {
            when (downloadEngine.getStatus(taskId)) {
                DownloadStatus.COMPLETED -> {
                    val file = downloadEngine.getCompletedFilePath(taskId)
                    if (file != null && file.exists() && file.length() > 0L) return@withTimeout file
                    throw IllegalStateException("下载完成但文件不存在")
                }
                DownloadStatus.FAILED -> throw IllegalStateException("升级文件下载失败")
                DownloadStatus.CANCELLED -> throw CancellationException("升级文件下载已取消")
                null -> throw IllegalStateException("升级下载任务未创建")
                else -> delay(DOWNLOAD_POLL_INTERVAL_MS)
            }
        }
        error("unreachable")
    }

    private fun installReplacement(original: File, downloaded: File): Pair<File, File> {
        require(original.exists()) { "原文件不存在" }
        require(downloaded.exists() && downloaded.length() > 0L) { "升级文件不存在或为空" }

        val extension = downloaded.extension.ifBlank { original.extension }
        val target = if (extension.equals(original.extension, ignoreCase = true)) {
            original
        } else {
            File(original.parentFile, "${original.nameWithoutExtension}.$extension")
        }
        require(target == original || !target.exists()) { "目标文件已存在: ${target.name}" }

        val backup = nextAvailableFile(File(original.absolutePath + ".backup"))
        moveFile(original, backup)
        try {
            moveFile(downloaded, target)
        } catch (error: Exception) {
            runCatching { moveFile(backup, original) }
            throw error
        }
        return backup to target
    }

    private fun installAlongsideOriginal(original: File, downloaded: File, quality: String): File {
        require(downloaded.exists() && downloaded.length() > 0L) { "升级文件不存在或为空" }
        val extension = downloaded.extension.ifBlank { original.extension }
        val candidate = File(
            original.parentFile,
            "${original.nameWithoutExtension} [$quality].$extension"
        )
        val target = nextAvailableFile(candidate)
        moveFile(downloaded, target)
        return target
    }

    private fun nextAvailableFile(candidate: File): File {
        if (!candidate.exists()) return candidate
        var index = 1
        while (true) {
            val next = File(candidate.parentFile, "${candidate.name}.$index")
            if (!next.exists()) return next
            index++
        }
    }

    private fun moveFile(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    suspend fun batchUpgrade(
        songs: List<Pair<SongInfo, OnlineVersion>>,
        mode: UpgradeMode,
        cookies: Map<String, String>
    ): Flow<UpgradeProgress> = flow {
        val total = songs.size
        var current = 0

        for ((song, onlineVersion) in songs) {
            current++
            emit(UpgradeProgress(
                current = current,
                total = total,
                currentSong = song.name,
                speed = 0,
                estimatedTimeRemaining = 0
            ))
            upgradeSong(song, onlineVersion, mode, cookies)
        }
    }

    fun isAudioFile(file: File): Boolean {
        val audioExtensions = setOf("mp3", "flac", "wav", "aac", "ogg", "opus", "m4a", "wma", "ape", "wv")
        return file.extension.lowercase() in audioExtensions
    }
}

data class UpgradeResult(
    val success: Boolean,
    val error: String?,
    val originalFile: File,
    val upgradedFile: File?
)
