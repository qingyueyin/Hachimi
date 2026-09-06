package com.qing.hachimi.downloader

import android.content.Context
import com.qing.hachimi.data.local.DownloadHistoryManager
import com.qing.hachimi.data.local.DownloadRecord
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.model.SongTagMetadata
import com.qing.hachimi.data.repository.NeteaseRepository.Companion.coverOriginalUrl
import com.qing.hachimi.util.AppLogger
import com.qing.hachimi.util.AudioTagger
import com.qing.hachimi.util.StoragePermissionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val DOWNLOAD_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"

/**
 * Build a download request for 126.net CDN.
 * IMPORTANT: Do NOT send Cookie header to the CDN - the URL's authSecret
 * is the sole authentication. Sending cookies causes 403 rejection.
 */
private fun downloadRequest(url: String): Request {
    return Request.Builder()
        .url(url)
        .header("User-Agent", DOWNLOAD_USER_AGENT)
        .header("Referer", "https://music.163.com/")
        .build()
}

private fun extractFileExtension(url: String): String {
    val lower = url.lowercase()
    return when {
        ".flac" in lower -> ".flac"
        ".wav" in lower -> ".wav"
        ".alac" in lower -> ".alac"
        ".m4a" in lower -> ".m4a"
        ".ogg" in lower -> ".ogg"
        ".aac" in lower -> ".aac"
        ".mp3" in lower -> ".mp3"
        else -> ".mp3"
    }
}

private fun sanitizeUrlForLog(url: String): String = runCatching {
    url.toHttpUrl().newBuilder().query(null).fragment(null).build().toString()
}.getOrElse {
    url.substringBefore('?').substringBefore('#')
}

data class DownloadProgress(
    val songId: Long,
    val songName: String,
    val artists: String = "",
    val coverUrl: String = "",
    val progress: Float,
    val speedKBps: Float,
    val status: DownloadStatus,
    val createdAtMillis: Long = 0L,
    /** The last known destination still exists. A moved file remains completed. */
    val fileAvailable: Boolean = true,
    val metadataStatus: MetadataStatus = MetadataStatus.NOT_REQUESTED,
    /** 失败原因的可读描述，仅 FAILED 状态使用；为空时 UI 回退到通用文案。 */
    val errorMessage: String? = null,
)

enum class DownloadStatus { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }
enum class MetadataStatus { NOT_REQUESTED, WRITTEN, FAILED }

internal object DownloadFailure {
    const val NO_URL = "没有下载地址"
    const val NETWORK = "网络中断，请重试"
    const val STORAGE = "无法保存文件，请检查存储权限"
    const val INTERRUPTED = "下载中断"
}

/**
 * 本次会话下载批次的总体进度：已完成任务按 100% 计入，分母固定为批次任务数，
 * 因此单曲完成时总进度只会平滑上涨而不会回跳。
 */
fun Collection<DownloadProgress>.sessionTotalProgress(sessionStartedAt: Long): Float {
    val batch = filter { it.createdAtMillis >= sessionStartedAt }
    if (batch.isEmpty()) return 0f
    return sumOf { it.progress.coerceIn(0f, 1f).toDouble() }.toFloat() / batch.size
}

class DownloadEngine(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val applicationScope: CoroutineScope,
    private val historyManager: DownloadHistoryManager
) {

    companion object {
        private const val TAG = "DownloadEngine"
        private fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "${bytes}B"
                bytes < 1024 * 1024 -> "${bytes / 1024}KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
                else -> "${bytes / (1024 * 1024 * 1024)}GB"
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val _progressMap = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val progressMap: StateFlow<Map<Long, DownloadProgress>> = _progressMap

    private data class TaskInfo(
        val songId: Long,
        val name: String,
        val artists: String,
        val album: String,
        val dir: File,
        val urls: List<String>,
        val cookie: String?,
        val coverUrl: String?,
        var totalBytes: Long,
        var downloadedBytes: Long,
        @Volatile var status: DownloadStatus,
        var job: Job?,
        var qualityLabel: String = "标准",
        var fileExtension: String = ".mp3",
        var lyrics: String? = null,
        val tagMetadata: SongTagMetadata = SongTagMetadata(),
        val persistHistory: Boolean = true,
        val createdAtMillis: Long,
        @Volatile var metadataStatus: MetadataStatus = MetadataStatus.NOT_REQUESTED,
        @Volatile var errorMessage: String? = null,
    )

    private data class TaskTiming(
        var startTimeMs: Long = 0L,
        var lastUpdateMs: Long = 0L,
        var lastBytes: Long = 0L
    )

    private val tasks = ConcurrentHashMap<Long, TaskInfo>()
    private val timings = ConcurrentHashMap<Long, TaskTiming>()
    private val completedFilePaths = ConcurrentHashMap<Long, File>()
    private var semaphore = Semaphore(settingsManager.concurrentDownloads)
    private val chunkCount = 4
    private var currentMaxConcurrent = settingsManager.concurrentDownloads
    private var serviceStarted = false

    /** 本次进程启动时间，用于区分本次会话的任务与从历史恢复的记录 */
    val sessionStartedAt: Long = System.currentTimeMillis()

    init {
        val history = historyManager.loadAll()
        if (history.isNotEmpty()) {
            AppLogger.debug("DownloadEngine: restoring ${history.size} completed downloads from history")
            for (record in history) {
                val file = File(record.filePath)
                val exists = file.exists() && file.length() > 0L
                if (exists) {
                    completedFilePaths[record.songId] = file
                    AppLogger.debug("DownloadEngine: restored file for ${record.songName}: ${file.name} (${file.length()} bytes)")
                }
                // A history entry records a successful download. The user may have
                // moved the file since then; that is a missing location, not a failed task.
                val status = DownloadStatus.COMPLETED
                _progressMap.update { it + (record.songId to DownloadProgress(
                    songId = record.songId,
                    songName = record.songName,
                    artists = record.artists,
                    coverUrl = record.coverUrl,
                    progress = if (exists) 1f else 0f,
                    speedKBps = 0f,
                    status = status,
                    createdAtMillis = record.timestamp,
                    fileAvailable = exists,
                )) }
            }
            AppLogger.debug("DownloadEngine: restored ${history.size} records, existing files: ${completedFilePaths.size}")
        }
    }

    private fun ensureServiceRunning() {
        if (!serviceStarted) {
            serviceStarted = true
            DownloadForegroundService.start(context)
        }
    }

    private fun checkStopService() {
        val hasActive = tasks.values.any {
            it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.PENDING ||
                it.status == DownloadStatus.PAUSED
        }
        if (!hasActive && serviceStarted) {
            serviceStarted = false
            DownloadForegroundService.stop(context)
        }
    }

    private fun saveToHistory(task: TaskInfo, file: File) {
        if (!task.persistHistory) return
        // Allow other apps (e.g. Lyrico) to read/write the downloaded file
        file.setReadable(true, false)
        file.setWritable(true, false)
        // Scan into MediaStore so the file is indexed and accessible by other apps
        try {
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(file.absolutePath), null, null
            )
        } catch (_: Exception) {}
        val record = DownloadRecord(
            songId = task.songId,
            songName = task.name,
            artists = task.artists,
            album = task.album,
            coverUrl = task.coverUrl ?: "",
            qualityLabel = task.qualityLabel,
            filePath = file.absolutePath,
            timestamp = task.createdAtMillis,
        )
        historyManager.save(record)
    }

    private fun removeFromHistory(songId: Long) {
        historyManager.remove(songId)
    }

    fun updateMaxConcurrent(max: Int) {
        if (max != currentMaxConcurrent) {
            currentMaxConcurrent = max
            semaphore = Semaphore(max)
            AppLogger.info("Download concurrency updated to $max")
        }
    }

    val allTasks: Map<Long, DownloadProgress>
        get() = _progressMap.value

    private fun updateProgress(task: TaskInfo) {
        val timing = timings.getOrPut(task.songId) { TaskTiming() }
        val now = System.currentTimeMillis()
        if (timing.startTimeMs == 0L) timing.startTimeMs = now

        val speed = if (task.totalBytes > 0 && task.status == DownloadStatus.DOWNLOADING) {
            val elapsed = maxOf(now - timing.lastUpdateMs, 1L)
            val deltaBytes = task.downloadedBytes - timing.lastBytes
            if (elapsed > 0 && deltaBytes > 0) {
                (deltaBytes / 1024f) / (elapsed / 1000f)
            } else 0f
        } else 0f

        timing.lastUpdateMs = now
        timing.lastBytes = task.downloadedBytes

        _progressMap.update { it + (task.songId to DownloadProgress(
            songId = task.songId,
            songName = task.name,
            artists = task.artists,
            coverUrl = task.coverUrl ?: "",
            progress = if (task.totalBytes > 0) task.downloadedBytes.toFloat() / task.totalBytes else 0f,
            speedKBps = speed.coerceAtMost(99999f),
            status = task.status,
            createdAtMillis = task.createdAtMillis,
            metadataStatus = task.metadataStatus,
            errorMessage = if (task.status == DownloadStatus.FAILED) task.errorMessage else null,
        )) }
    }

    private fun updateStatus(
        task: TaskInfo,
        status: DownloadStatus,
        errorMessage: String? = null,
    ) {
        task.status = status
        task.errorMessage = if (status == DownloadStatus.FAILED) errorMessage else null
        updateProgress(task)
    }

    fun startDownload(
        songId: Long,
        name: String,
        artists: String,
        album: String = "",
        coverUrl: String? = null,
        url: String,
        dir: File,
        qualityLabel: String = "标准",
        cookie: String? = null,
        overwrite: Boolean = false,
        lyrics: String? = null,
        tagMetadata: SongTagMetadata = SongTagMetadata(),
        groupName: String? = null,
        applyFolderGrouping: Boolean = true,
        persistHistory: Boolean = true
    ) {
        AppLogger.debug("startDownload: songId=$songId, name=$name, url=${sanitizeUrlForLog(url)}, hasCookie=${cookie != null}, overwrite=$overwrite")
        // Skip if already in a terminal state
        if (tasks.containsKey(songId) && !overwrite) {
            val existing = tasks[songId]!!
            when (existing.status) {
                DownloadStatus.DOWNLOADING -> return
                DownloadStatus.PAUSED -> { resumeDownload(songId); return }
                DownloadStatus.COMPLETED -> return
                DownloadStatus.CANCELLED, DownloadStatus.FAILED -> { /* allow restart */ }
                else -> {}
            }
        }

        // If overwrite and task already exists, remove it first
        if (tasks.containsKey(songId) && overwrite) {
            removeTask(songId)
        }

        // 根据 folderNamingFormat 创建子文件夹
        val targetDir = if (applyFolderGrouping) {
            buildTargetDirectory(dir, artists, album, groupName, settingsManager.folderNamingFormat)
        } else {
            dir
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val ext = extractFileExtension(url)
        val fileName = settingsManager.buildFileName(name, artists, qualityLabel, album) + ext
        val file = File(targetDir, fileName)

        // Only skip if file exists AND size > 0 (already downloaded)
        if (file.exists() && file.length() > 0 && !overwrite) {
            val createdAtMillis = System.currentTimeMillis()
            completedFilePaths[songId] = file
            if (persistHistory) {
                historyManager.save(DownloadRecord(
                    songId = songId, songName = name, artists = artists,
                    album = album, coverUrl = coverUrl ?: "",
                    qualityLabel = qualityLabel,
                    filePath = file.absolutePath,
                    timestamp = createdAtMillis,
                ))
            }
            _progressMap.update { it + (songId to DownloadProgress(
                songId = songId, songName = name, artists = artists,
                coverUrl = coverUrl ?: "",
                progress = 1f,
                speedKBps = 0f,
                status = DownloadStatus.COMPLETED,
                createdAtMillis = createdAtMillis,
            )) }
            return
        }
        // Delete existing file if overwriting
        if (file.exists() && overwrite) {
            AppLogger.debug("Overwrite mode: deleting existing file $fileName")
            file.delete()
        }

        // 写入探测（非阻断）：记录兼容层授权状态，不阻塞下载流程。
        // 真正的权限不足由后续 IO 异常捕获，此处只做预警日志。
        StoragePermissionManager.probeCanWrite(targetDir).also { writable ->
            if (!writable) {
                AppLogger.warn("startDownload: probe write failed, MANAGE_EXTERNAL_STORAGE may not be granted: ${targetDir.absolutePath}")
            }
        }

        val createdAtMillis = System.currentTimeMillis()
        val task = TaskInfo(
            songId = songId, name = name, artists = artists, album = album, dir = targetDir,
            urls = listOf(url), cookie = cookie, coverUrl = coverUrl,
            totalBytes = 0L, downloadedBytes = 0L,
            status = DownloadStatus.PENDING, job = null,
            qualityLabel = qualityLabel,
            fileExtension = ext,
            lyrics = lyrics,
            tagMetadata = tagMetadata,
            persistHistory = persistHistory,
            createdAtMillis = createdAtMillis,
        )
        tasks[songId] = task
        _progressMap.update { it + (songId to DownloadProgress(
            songId = songId, songName = name, artists = artists,
            coverUrl = coverUrl ?: "",
            progress = 0f,
            speedKBps = 0f,
            status = DownloadStatus.PENDING,
            createdAtMillis = createdAtMillis,
        )) }

        ensureServiceRunning()
        val taskSemaphore = semaphore
        task.job = applicationScope.launch(Dispatchers.IO) {
            taskSemaphore.acquire()
            try {
                if (!awaitRunnable(task)) return@launch
                downloadWithChunks(task, file)
            } finally {
                taskSemaphore.release()
                checkStopService()
            }
        }
    }

    private suspend fun awaitRunnable(task: TaskInfo): Boolean {
        while (task.status == DownloadStatus.PAUSED) delay(200)
        return task.status != DownloadStatus.CANCELLED
    }

    private suspend fun downloadWithChunks(task: TaskInfo, file: File) {
        try {
            val url = task.urls.firstOrNull() ?: run {
                updateStatus(task, DownloadStatus.FAILED, DownloadFailure.NO_URL); return
            }

val headRequest = downloadRequest(url)
                .newBuilder()
                .head()
                .build()

            val headResponse = client.newCall(headRequest).execute()
            var totalBytes: Long
            var supportsRange: Boolean
            var responseCode: Int
            var acceptRanges: String?

            try {
                val body = headResponse.body
                totalBytes = body.contentLength()
                acceptRanges = headResponse.header("Accept-Ranges")
                supportsRange = headResponse.code == 206 || acceptRanges == "bytes"
                responseCode = headResponse.code
            } catch (e: Exception) {
                AppLogger.error("HEAD request failed", e)
                updateStatus(task, DownloadStatus.FAILED, DownloadFailure.NETWORK)
                return
            } finally {
                headResponse.close()
            }

            AppLogger.debug("HEAD request: code=$responseCode, contentLength=$totalBytes, acceptRanges=$acceptRanges")

            // CDN (126.net) often rejects HEAD but accepts GET; fallback to single-stream
            if (responseCode == 403 || responseCode >= 400) {
                AppLogger.warn("HEAD returned $responseCode, falling back to single-stream download")
                if (totalBytes > 0) task.totalBytes = totalBytes
                if (!awaitRunnable(task)) return
                singleStreamDownload(task, url, file)
                return
            }

            // Check if partial file exists from a previous failed download
            val existingPartial = findPartialFile(file)
            if (existingPartial != null && existingPartial.exists()) {
                AppLogger.debug("Found partial file: ${existingPartial.name}")
                // Delete partial files for a fresh start (range resume is handled per-chunk)
                cleanupChunksForFile(file)
            }

            if (totalBytes <= 0 || !supportsRange) {
                AppLogger.debug("Range requests not supported, falling back to single stream")
                if (totalBytes > 0) task.totalBytes = totalBytes
                singleStreamDownload(task, url, file)
                return
            }

            task.totalBytes = totalBytes
            if (!awaitRunnable(task)) return
            updateStatus(task, DownloadStatus.DOWNLOADING)

            AppLogger.debug("Starting multi-chunk download: $chunkCount chunks, total=${formatBytes(totalBytes)}, file=${file.name}")

            val chunkSize = totalBytes / chunkCount
            val chunkFiles = mutableListOf<File>()
            var chunkSuccess = true
            var shouldReturn = false

            coroutineScope {
                val deferreds = mutableListOf<Deferred<Long>>()

                for (i in 0 until chunkCount) {
                    val start = i * chunkSize
                    val end = if (i == chunkCount - 1) totalBytes - 1 else (i + 1) * chunkSize - 1
                    val chunkFile = File(file.parentFile, "${file.name}.part$i")
                    chunkFiles.add(chunkFile)

                    deferreds.add(async(Dispatchers.IO) {
                        downloadChunk(task, url, start, end, chunkFile, i)
                    })
                }

                for (deferred in deferreds) {
                    try {
                        val bytes = deferred.await()
                        task.downloadedBytes += bytes
                        updateProgress(task)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.error("Chunk download failed", e)
                        chunkSuccess = false
                        deferreds.forEach { it.cancel() }
                        break
                    }
                    if (task.status == DownloadStatus.CANCELLED) {
                        deferreds.forEach { it.cancel() }
                        cleanupChunks(chunkFiles)
                        shouldReturn = true
                        return@coroutineScope
                    }
                    if (task.status == DownloadStatus.PAUSED) {
                        if (!awaitRunnable(task)) {
                            shouldReturn = true
                            return@coroutineScope
                        }
                    }
                }
            }

            if (shouldReturn) return

            if (!chunkSuccess) {
                updateStatus(task, DownloadStatus.FAILED, DownloadFailure.INTERRUPTED)
                cleanupChunks(chunkFiles)
                return
            }

            try {
                // Validate all chunk files exist and have non-zero size before merging
                for (chunk in chunkFiles) {
                    if (!chunk.exists() || chunk.length() == 0L) {
                        throw IllegalStateException("Chunk ${chunk.name} is missing or empty")
                    }
                }

                mergeChunks(chunkFiles, file)

                // Verify the merged file was actually created and has content
                if (!file.exists() || file.length() == 0L) {
                    throw IllegalStateException("Merged file is missing or empty: ${file.name}")
                }

                cleanupChunks(chunkFiles)

                val actualSize = file.length()
                if (totalBytes > 0 && actualSize != totalBytes) {
                    AppLogger.warn("File size mismatch: expected=$totalBytes, actual=$actualSize for ${file.name}")
                    // Accept the file anyway - some servers report wrong Content-Length
                }

                // Update downloadedBytes to actual merged file size to ensure 100% progress
                task.downloadedBytes = actualSize
                if (totalBytes > 0) {
                    task.totalBytes = actualSize
                }
                task.metadataStatus = writeAudioTags(task)
                updateStatus(task, DownloadStatus.COMPLETED)
                // Track file path for opening
                completedFilePaths[task.songId] = file
                // Persist download history
                saveToHistory(task, file)

                updateProgress(task)

                AppLogger.debug("Download completed: ${file.name} ($actualSize bytes)")
            } catch (e: Exception) {
                AppLogger.error("Failed to finalize download", e)
                // If merge failed but the output file exists with content, treat it as complete
                // This handles edge cases where merge partially succeeds but throws afterwards
                if (file.exists() && file.length() > 0L) {
                    AppLogger.info("Merge threw exception but file exists with ${file.length()} bytes, marking as complete")
                    task.downloadedBytes = file.length()
                    if (totalBytes > 0) {
                        task.totalBytes = file.length()
                    }
                    task.metadataStatus = writeAudioTags(task)
                    updateStatus(task, DownloadStatus.COMPLETED)
                    completedFilePaths[task.songId] = file
                    saveToHistory(task, file)
                    updateProgress(task)
                } else {
                    updateStatus(task, DownloadStatus.FAILED, DownloadFailure.STORAGE)
                }
                // Always try to cleanup chunk files even if merge failed
                try { cleanupChunks(chunkFiles) } catch (_: Exception) {}
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.error("downloadWithChunks unexpected error", e)
            if (task.status != DownloadStatus.CANCELLED) {
                val message = if (e is java.io.IOException) DownloadFailure.STORAGE else DownloadFailure.NETWORK
                updateStatus(task, DownloadStatus.FAILED, message)
            }
        }
    }

    private fun findPartialFile(file: File): File? {
        val parent = file.parentFile ?: return null
        val partials = parent.listFiles { f ->
            f.name.startsWith("${file.name}.part") && f.exists()
        }
        return partials?.firstOrNull()
    }

    private fun cleanupChunksForFile(file: File) {
        val parent = file.parentFile ?: return
        parent.listFiles { f ->
            f.name.startsWith("${file.name}.part")
        }?.forEach { it.delete() }
    }

    private suspend fun downloadChunk(
        task: TaskInfo, url: String, start: Long, end: Long,
        chunkFile: File, index: Int
    ): Long {
        if (task.status == DownloadStatus.CANCELLED || task.status == DownloadStatus.PAUSED) return 0L

        var resumeFrom = 0L
        if (chunkFile.exists()) {
            resumeFrom = chunkFile.length()
            AppLogger.debug("[Chunk $index] Resuming from $resumeFrom (chunk file exists: ${chunkFile.name}, size=${chunkFile.length()} bytes)")
        } else {
            AppLogger.debug("[Chunk $index] Starting fresh: bytes $start-$end -> ${chunkFile.name}")
        }

        val actualStart = start + resumeFrom
        if (actualStart > end) {
            AppLogger.debug("[Chunk $index] Already complete: actualStart=$actualStart > end=$end")
            return resumeFrom
        }

val request = downloadRequest(url)
            .newBuilder()
            .header("Range", "bytes=$actualStart-$end")
            .build()

        val response = client.newCall(request).execute()
        try {
            if (!response.isSuccessful) {
                AppLogger.error("[Chunk $index] Failed: HTTP ${response.code}, Range: bytes=$actualStart-$end")
                throw Exception("Chunk $index failed: ${response.code}")
            }

            AppLogger.debug("[Chunk $index] Response: code=${response.code}, contentLength=${response.body.contentLength()}")

            val body = response.body
            val buffer = ByteArray(64 * 1024)
            var downloaded = resumeFrom

            RandomAccessFile(chunkFile, "rw").use { raf ->
                raf.seek(resumeFrom)
                body.byteStream().use { stream ->
                    var bytesRead: Int
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        if (task.status == DownloadStatus.CANCELLED) throw CancellationException("Cancelled")
                        while (task.status == DownloadStatus.PAUSED) {
                            delay(200)
                            if (task.status == DownloadStatus.CANCELLED) throw CancellationException("Cancelled")
                        }
                        raf.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                    }
                }
            }
            val chunkBytes = downloaded - resumeFrom
            AppLogger.debug("[Chunk $index] Completed: $chunkBytes bytes written to ${chunkFile.name}")
            return chunkBytes
        } finally {
            response.close()
        }
    }

    private suspend fun singleStreamDownload(task: TaskInfo, url: String, file: File) {
        val maxRetries = 3
        var attempt = 0
        var lastError: Exception? = null

        while (attempt < maxRetries) {
            if (!awaitRunnable(task)) return
            attempt++
            if (attempt > 1) {
                AppLogger.warn("[SingleStream] Retry $attempt/$maxRetries for ${file.name} (${lastError?.message})")
                delay(3000L * attempt) // backoff: 3s, 6s, 9s
            }

            try {
                val request = downloadRequest(url)
                AppLogger.debug("[SingleStream] Starting: file=${file.name}, attempt=$attempt, url=${sanitizeUrlForLog(url)}")

                client.newCall(request).execute().use { response ->
                    AppLogger.debug("[SingleStream] Response: code=${response.code}, contentLength=${response.body.contentLength()}")

                    if (!response.isSuccessful) {
                        AppLogger.error("[SingleStream] Failed with code ${response.code}")
                        lastError = Exception("HTTP ${response.code}")
                        continue // retry
                    }
                    val body = response.body
                    val totalBytes = body.contentLength()
                    if (totalBytes > 0) task.totalBytes = totalBytes
                    updateStatus(task, DownloadStatus.DOWNLOADING)

                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        body.byteStream().use { input ->
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                currentCoroutineContext().ensureActive()
                                if (task.status == DownloadStatus.CANCELLED) {
                                    file.delete()
                                    return
                                }
                                while (task.status == DownloadStatus.PAUSED) {
                                    delay(200)
                                    if (task.status == DownloadStatus.CANCELLED) {
                                        file.delete()
                                        return
                                    }
                                }
                                output.write(buffer, 0, bytesRead)
                                task.downloadedBytes += bytesRead
                                updateProgress(task)
                            }
                        }
                    }
                }
                // Success
                task.metadataStatus = writeAudioTags(task)
                updateStatus(task, DownloadStatus.COMPLETED)
                completedFilePaths[task.songId] = file
                saveToHistory(task, file)
                updateProgress(task)
                AppLogger.info("[SingleStream] Completed: ${file.name}")
                return

            } catch (e: CancellationException) {
                file.delete()
                throw e
            } catch (e: java.net.SocketException) {
                lastError = e
                AppLogger.warn("[SingleStream] Connection dropped (${e.message}), retrying...")
                file.delete()
                task.downloadedBytes = 0
                updateProgress(task)
            } catch (e: Exception) {
                AppLogger.error("[SingleStream] Download failed", e)
                lastError = e
                file.delete()
                task.downloadedBytes = 0
                updateProgress(task)
            }
        }

        AppLogger.error("[SingleStream] All $maxRetries retries exhausted for ${file.name}")
        updateStatus(task, DownloadStatus.FAILED, DownloadFailure.NETWORK)
    }

    private fun mergeChunks(chunks: List<File>, output: File) {
        val fos = FileOutputStream(output)
        java.io.BufferedOutputStream(fos, 64 * 1024).use { out ->
            for (chunk in chunks.sortedBy { it.name }) {
                java.io.BufferedInputStream(
                    java.io.FileInputStream(chunk),
                    64 * 1024
                ).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }
            }
            out.flush()
            fos.fd.sync()
        }
    }

    private fun cleanupChunks(chunks: List<File>) {
        chunks.forEach { if (it.exists()) it.delete() }
    }

    fun pauseDownload(songId: Long) {
        tasks[songId]?.let {
            if (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING) {
                it.status = DownloadStatus.PAUSED
                updateProgress(it)
            }
        }
    }

    fun resumeDownload(songId: Long) {
        val task = tasks[songId] ?: return
        val fileName = settingsManager.buildFileName(task.name, task.artists, task.qualityLabel, task.album) + task.fileExtension
        when (task.status) {
            DownloadStatus.PAUSED -> {
                task.status = DownloadStatus.DOWNLOADING
                updateProgress(task)
                ensureServiceRunning()
            }
            DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                val file = File(task.dir, fileName)
                val previousJob = task.job
                val taskSemaphore = semaphore
                task.downloadedBytes = 0
                task.totalBytes = 0
                updateStatus(task, DownloadStatus.PENDING)
                ensureServiceRunning()
                val restartJob = applicationScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                    previousJob?.join()
                    if (task.job !== coroutineContext[Job] || task.status != DownloadStatus.PENDING) return@launch
                    taskSemaphore.acquire()
                    try {
                        cleanupChunksForFile(file)
                        file.delete()
                        if (!awaitRunnable(task)) return@launch
                        downloadWithChunks(task, file)
                    } finally {
                        taskSemaphore.release()
                        checkStopService()
                    }
                }
                task.job = restartJob
                restartJob.start()
            }
            else -> {}
        }
    }

    fun cancelDownload(songId: Long) {
        tasks[songId]?.let {
            it.status = DownloadStatus.CANCELLED
            it.job?.cancel()
            updateProgress(it)
            checkStopService()
        }
    }

    fun removeTask(songId: Long) {
        val task = tasks[songId]
        val targetFile = completedFilePaths[songId] ?: task?.let {
            val fileName = settingsManager.buildFileName(it.name, it.artists, it.qualityLabel, it.album) + it.fileExtension
            File(it.dir, fileName)
        }
        if (task != null) {
            cancelDownload(songId)
            tasks.remove(songId)
        }
        targetFile?.delete()
        targetFile?.let(::cleanupChunksForFile)
        removeFromHistory(songId)
        completedFilePaths.remove(songId)
        timings.remove(songId)
        _progressMap.update { it - songId }
        checkStopService()
    }

    /** Remove a completed task from the app while keeping its audio file. */
    fun dismissCompletedRecord(songId: Long) {
        if (_progressMap.value[songId]?.status != DownloadStatus.COMPLETED) return
        tasks.remove(songId)?.job?.cancel()
        timings.remove(songId)
        completedFilePaths.remove(songId)
        removeFromHistory(songId)
        _progressMap.update { it - songId }
        checkStopService()
    }

    /** Clear every completed history entry without touching any audio file. */
    fun dismissAllCompletedRecords() {
        _progressMap.value.values
            .asSequence()
            .filter { it.status == DownloadStatus.COMPLETED }
            .map { it.songId }
            .toList()
            .forEach(::dismissCompletedRecord)
    }

    /** Get the file path for a completed download. */
    fun getCompletedFilePath(songId: Long): File? = completedFilePaths[songId]

    /** Get the resolved destination for an active or completed task. */
    fun getTargetFilePath(songId: Long): File? {
        completedFilePaths[songId]?.let { return it }
        val task = tasks[songId] ?: return null
        val fileName = settingsManager.buildFileName(
            task.name,
            task.artists,
            task.qualityLabel,
            task.album
        ) + task.fileExtension
        return File(task.dir, fileName)
    }

    fun getStatus(songId: Long): DownloadStatus? = tasks[songId]?.status

    /** Remove an internal task after its completed file has been moved elsewhere. */
    fun forgetTask(songId: Long) {
        tasks.remove(songId)
        timings.remove(songId)
        completedFilePaths.remove(songId)
        _progressMap.update { it - songId }
        checkStopService()
    }

    private fun writeAudioTags(task: TaskInfo): MetadataStatus {
        val hasLyrics = !task.lyrics.isNullOrBlank()

        // 如果既没有歌词要写，又没开启标签写入，直接返回
        if (!settingsManager.enableWriteTags) return MetadataStatus.NOT_REQUESTED

        return try {
            val cover = if (settingsManager.enableWriteTags) {
                task.coverUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { coverUrl ->
                        val originalUrl = coverOriginalUrl(coverUrl)
                        AppLogger.debug("writeAudioTags: downloading original cover from ${sanitizeUrlForLog(originalUrl)}")
                        runCatching {
                            val request = okhttp3.Request.Builder()
                                .url(originalUrl)
                                .header("User-Agent", DOWNLOAD_USER_AGENT)
                                .header("Referer", "https://music.163.com/")
                                .build()
                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    ArtworkPayload(
                                        data = response.body.bytes(),
                                        contentType = response.header("Content-Type"),
                                    )
                                } else {
                                    ArtworkPayload.EMPTY
                                }
                            }
                        }.getOrElse { e ->
                            AppLogger.warn("writeAudioTags: cover download skipped for ${task.name} - ${e.message}")
                            ArtworkPayload.EMPTY
                        }
                    } ?: ArtworkPayload.EMPTY
            } else ArtworkPayload.EMPTY

            val filePath = File(task.dir, settingsManager.buildFileName(task.name, task.artists, task.qualityLabel, task.album) + task.fileExtension).absolutePath
            AppLogger.debug("writeAudioTags: writing to $filePath (tags=${settingsManager.enableWriteTags}, lyrics=$hasLyrics)")

            AudioTagger.writeTags(
                filePath = filePath,
                title = task.name,
                artist = task.artists,
                album = task.album,
                coverData = cover.data,
                coverMimeType = cover.contentType,
                lyrics = task.lyrics,
                metadata = task.tagMetadata,
            ).fold(
                onSuccess = { MetadataStatus.WRITTEN },
                onFailure = { error ->
                    AppLogger.error("writeAudioTags failed for ${task.name}", error)
                    MetadataStatus.FAILED
                },
            )
        } catch (e: Exception) {
            AppLogger.error("writeAudioTags failed", e)
            MetadataStatus.FAILED
        }
    }

    private data class ArtworkPayload(
        val data: ByteArray,
        val contentType: String?,
    ) {
        companion object {
            val EMPTY = ArtworkPayload(ByteArray(0), null)
        }
    }

    /**
     * 根据 folderNamingFormat 构建目标文件夹
     */
    private fun buildTargetDirectory(
        baseDir: File,
        artists: String,
        album: String,
        groupName: String?,
        format: com.qing.hachimi.data.local.FolderNamingFormat
    ): File {
        return when (format) {
            com.qing.hachimi.data.local.FolderNamingFormat.NONE -> baseDir
            com.qing.hachimi.data.local.FolderNamingFormat.ARTIST_NAME -> {
                val artist = artists.split(",", "、", "/").firstOrNull()?.trim() ?: "未知歌手"
                File(baseDir, sanitizeFolderName(artist))
            }
            com.qing.hachimi.data.local.FolderNamingFormat.ALBUM_NAME -> {
                val albumName = album.ifBlank { "未知专辑" }
                File(baseDir, sanitizeFolderName(albumName))
            }
            com.qing.hachimi.data.local.FolderNamingFormat.ARTIST_ALBUM -> {
                val artist = artists.split(",", "、", "/").firstOrNull()?.trim() ?: "未知歌手"
                val albumName = album.ifBlank { "未知专辑" }
                File(baseDir, "${sanitizeFolderName(artist)} - ${sanitizeFolderName(albumName)}")
            }
            com.qing.hachimi.data.local.FolderNamingFormat.PLAYLIST_NAME -> {
                val playlistName = groupName?.takeIf { it.isNotBlank() }
                    ?: album.ifBlank { "未知歌单" }
                File(baseDir, sanitizeFolderName(playlistName))
            }
        }
    }

    /**
     * 清理文件夹名称中的非法字符
     */
    private fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }
}
