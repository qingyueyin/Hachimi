package com.qing.hachimi.service

import com.qing.hachimi.data.api.RateLimitException
import com.qing.hachimi.data.api.UpgradeApi
import com.qing.hachimi.data.local.DownloadHistoryManager
import com.qing.hachimi.data.local.DownloadRecord
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** 一次「检查可升级版本」的结果：候选 + 因为网络/限流没能检查的歌曲数 */
data class UpgradeCheckResult(
    val candidates: List<Pair<SongInfo, OnlineVersion>>,
    val failedCount: Int,
)

class UpgradeService(
    val audioAnalyzer: AudioQualityAnalyzer,
    private val upgradeApi: UpgradeApi,
    private val historyManager: UpgradeHistoryManager,
    private val downloadEngine: DownloadEngine,
    private val downloadHistory: DownloadHistoryManager
) {

    companion object {
        private const val DOWNLOAD_TIMEOUT_MS = 15 * 60 * 1000L
        private const val DOWNLOAD_POLL_INTERVAL_MS = 100L
        private const val SEARCH_TIMEOUT_MS = 15_000L
        private const val MAX_CONCURRENT_SEARCHES = 3
        /** 连续这么多首都被限流，说明不是偶发，继续打只会让封禁更久 */
        private const val MAX_CONSECUTIVE_RATE_LIMITS = 3
        /** 按 ID 查音质连续失败这么多次（网络断了/接口异常），就别再空转几百次 */
        private const val MAX_CONSECUTIVE_CHECK_FAILURES = 10
        /** 下载历史里的歌名要和本地文件名对得上，才对它用「按 ID 直查」 */
        private const val MATCH_THRESHOLD = 0.7
        private const val RATE_LIMIT_COOLDOWN_MS = 3000L
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
     * onSongs 在 IO 线程调用，但它是 suspend 的：回调里直接改状态即可，
     * 调用方不用再起协程，也就不会出现「界面更新排在扫描结束之后」的乱序。
     */
    suspend fun scanFoldersIncremental(
        folders: List<File>,
        onProgress: (current: Int, total: Int, folderName: String) -> Unit = { _, _, _ -> },
        onSongs: suspend (folder: File, songs: List<SongInfo>) -> Unit = { _, _ -> }
    ): List<ScannedFolder> = withContext(Dispatchers.IO) {
        // 历史记录只读一次：isUpgraded 每次都重新解析整份 JSON，500 首会解析 500 遍
        val upgradedPaths = historyManager.upgradedFilePaths()
        folders.mapIndexed { index, folder ->
            onProgress(index + 1, folders.size, folder.name)
            val songs = scanFolder(folder) { batch -> onSongs(folder, batch) }
            val upgradeableCount = songs.count { song ->
                song.localFile.absolutePath !in upgradedPaths
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
    private suspend fun scanFolder(folder: File, onBatch: suspend (List<SongInfo>) -> Unit = {}): List<SongInfo> {
        if (!folder.exists() || !folder.isDirectory) {
            return emptyList()
        }

        val pending = mutableListOf<SongInfo>()
        // 序列保持惰性：for 循环每取一首才分析一首，取消扫描能立刻停下；
        // 先 toList() 会一口气把整盘分析完，中途取消要等全部分析结束才生效
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

        val songs = mutableListOf<SongInfo>()
        for (song in parsed) {
            currentCoroutineContext().ensureActive()
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
    ): UpgradeCheckResult = supervisorScope {
        val semaphore = Semaphore(MAX_CONCURRENT_SEARCHES)
        val completed = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val rateLimitStreak = AtomicInteger(0)
        val checkFailureStreak = AtomicInteger(0)
        val aborted = AtomicBoolean(false)
        // 下载历史里记过 songId 的歌可以直接查音质，不必走最容易限流的搜索
        val historyIndex = withContext(Dispatchers.IO) { downloadHistoryIndex() }
        val knownCount = songs.count { song ->
            historyIndex[song.localFile.absolutePath.lowercase()]?.let { matchesHistory(song, it) } == true
        }
        AppLogger.debug("升级检查：${songs.size} 首，其中 $knownCount 首可按 ID 直接查")

        val candidates = songs.map { song ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    // 已经确认被限流/连续失败，排队中的歌不再发请求
                    if (aborted.get()) {
                        failed.incrementAndGet()
                        return@withPermit null
                    }
                    try {
                        val historyRecord = historyIndex[song.localFile.absolutePath.lowercase()]
                            ?.takeIf { matchesHistory(song, it) }
                        val knownId = historyRecord?.songId
                        if (knownId != null) {
                            val online = upgradeApi.getQualityBySongId(knownId, cookies)
                            if (online == null) {
                                failed.incrementAndGet()
                                if (checkFailureStreak.incrementAndGet() >= MAX_CONSECUTIVE_CHECK_FAILURES) {
                                    AppLogger.warn("按 ID 查询音质连续失败，停止本次检查")
                                    aborted.set(true)
                                }
                                return@withPermit null
                            }
                            checkFailureStreak.set(0)
                            rateLimitStreak.set(0)
                            val version = OnlineVersion(
                                id = knownId,
                                name = song.name,
                                artists = song.artists,
                                album = song.album,
                                qualityLevel = online.qualityLevel,
                                qualityInfo = online.qualityInfo,
                                downloadUrl = null,
                            )
                            version.takeIf {
                                audioAnalyzer.compareQuality(song.qualityInfo, it.qualityInfo).isUpgradeable
                            }?.let { song to it }
                        } else {
                            val bestVersion = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                                searchOnline(song, cookies).firstOrNull { version ->
                                    audioAnalyzer.compareQuality(
                                        song.qualityInfo,
                                        version.qualityInfo,
                                    ).isUpgradeable
                                }
                            }
                            rateLimitStreak.set(0)
                            checkFailureStreak.set(0)
                            bestVersion?.let { song to it }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: RateLimitException) {
                        failed.incrementAndGet()
                        if (rateLimitStreak.incrementAndGet() >= MAX_CONSECUTIVE_RATE_LIMITS) {
                            aborted.set(true)
                            throw e
                        }
                        delay(RATE_LIMIT_COOLDOWN_MS)
                        null
                    } catch (e: Exception) {
                        failed.incrementAndGet()
                        rateLimitStreak.set(0)
                        AppLogger.error("检查在线音质失败: ${song.name}", e)
                        null
                    } finally {
                        onProgress(completed.incrementAndGet(), songs.size, song.name)
                    }
                }
            }
        }.awaitAll().filterNotNull()

        UpgradeCheckResult(candidates = candidates, failedCount = failed.get())
    }

    /** 下载历史里 文件路径 → 记录，用来跳过搜索；键统一小写，避免存储卷大小写写法不同 */
    private fun downloadHistoryIndex(): Map<String, DownloadRecord> = try {
        downloadHistory.loadAll()
            .asSequence()
            .filter { it.songId > 0L && it.filePath.isNotBlank() && it.songName.isNotBlank() }
            .associate { it.filePath.lowercase() to it }
    } catch (e: Exception) {
        AppLogger.error("读取下载历史失败，本次全部走搜索", e)
        emptyMap()
    }

    /**
     * 光看文件路径不够：用户手动改名后路径不变、旧 ID 还在，但文件已经是另一首歌了，
     * 拿旧 ID 去升级会把这首换成别人的歌。所以再核一次歌名——
     * 文件名可能是「歌名 - 歌手」也可能是「歌手 - 歌名」，两边都比一遍。
     */
    private fun matchesHistory(song: SongInfo, record: DownloadRecord): Boolean {
        val onlineName = record.songName.trim()
        if (onlineName.isEmpty()) return false
        return UpgradeApi.nameSimilarity(song.name, onlineName) >= MATCH_THRESHOLD ||
            UpgradeApi.nameSimilarity(song.artists, onlineName) >= MATCH_THRESHOLD
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

            val urlResult = upgradeApi.getSongUrl(
                targetVersion.id,
                targetVersion.qualityLevel,
                cookies
            )
            val downloadUrl = urlResult.getOrNull()

            if (downloadUrl == null) {
                return@withContext UpgradeResult(
                    success = false,
                    error = urlResult.exceptionOrNull()?.message ?: "无法获取下载链接",
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
                            status = UpgradeStatus.COMPLETED,
                            mode = mode
                        )
                    )
                    UpgradeResult(
                        success = true,
                        error = null,
                        originalFile = song.localFile,
                        upgradedFile = upgradedFile,
                        upgradedQuality = downloadedQuality
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
                            status = UpgradeStatus.COMPLETED,
                            mode = mode
                        )
                    )
                    UpgradeResult(
                        success = true,
                        error = null,
                        originalFile = song.localFile,
                        upgradedFile = upgradedFile,
                        upgradedQuality = downloadedQuality
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
                DownloadStatus.FAILED -> {
                    // 引擎已经知道是「网络中断」还是「被安全策略拦了」，别再只说一句失败
                    val reason = downloadEngine.getErrorMessage(taskId)
                    throw IllegalStateException(
                        if (reason.isNullOrBlank()) "升级文件下载失败" else "升级文件下载失败：$reason"
                    )
                }
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
    val upgradedFile: File?,
    /** 新文件的真实音质；界面要拿它刷新这一行，否则还显示升级前的旧音质 */
    val upgradedQuality: AudioQualityInfo? = null
)
