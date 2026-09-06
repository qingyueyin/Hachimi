package com.qing.hachimi.ui.screens

import com.qing.hachimi.downloader.DownloadProgress
import com.qing.hachimi.downloader.DownloadStatus
import com.qing.hachimi.downloader.sessionTotalProgress

enum class DownloadView {
    ACTIVE,
    COMPLETED,
}

object DownloadUiModel {
    private val activePriority = mapOf(
        DownloadStatus.DOWNLOADING to 0,
        DownloadStatus.PENDING to 1,
        DownloadStatus.PAUSED to 2,
        DownloadStatus.FAILED to 3,
        DownloadStatus.CANCELLED to 4,
    )

    fun activeTasks(tasks: Collection<DownloadProgress>): List<DownloadProgress> = tasks
        .filter { it.status != DownloadStatus.COMPLETED }
        .sortedWith(
            compareBy<DownloadProgress> { activePriority[it.status] ?: Int.MAX_VALUE }
                .thenByDescending { it.createdAtMillis }
                .thenByDescending { it.songId }
        )

    fun completedTasks(tasks: Collection<DownloadProgress>): List<DownloadProgress> = tasks
        .filter { it.status == DownloadStatus.COMPLETED }
        .sortedWith(
            compareByDescending<DownloadProgress> { it.createdAtMillis }
                .thenByDescending { it.songId }
        )

    fun pauseTargets(tasks: Collection<DownloadProgress>): Set<Long> = tasks
        .filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }
        .mapTo(linkedSetOf()) { it.songId }

    fun resumeTargets(tasks: Collection<DownloadProgress>): Set<Long> = tasks
        .filter { it.status == DownloadStatus.PAUSED }
        .mapTo(linkedSetOf()) { it.songId }

    fun retryTargets(tasks: Collection<DownloadProgress>): Set<Long> = tasks
        .filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
        .mapTo(linkedSetOf()) { it.songId }

    fun completedTargets(tasks: Collection<DownloadProgress>): Set<Long> = tasks
        .filter { it.status == DownloadStatus.COMPLETED }
        .mapTo(linkedSetOf()) { it.songId }

    fun failureLabel(progress: DownloadProgress): String =
        progress.errorMessage?.takeIf { it.isNotBlank() } ?: "下载失败"

    /** 进行中任务的总体进度：仅计入可操作的 DOWNLOADING / PENDING / PAUSED，忽略失败/取消/已完成 */
    fun averageActiveProgress(tasks: Collection<DownloadProgress>): Float {
        val active = tasks.filter {
            it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.PENDING ||
                it.status == DownloadStatus.PAUSED
        }
        if (active.isEmpty()) return 0f
        return active.sumOf { it.progress.toDouble() }.toFloat() / active.size
    }

    /** 按歌手分组已完成任务，组按最近完成时间排序 */
    fun groupByArtist(tasks: List<DownloadProgress>): List<Pair<String, List<DownloadProgress>>> = tasks
        .groupBy { it.artists.ifBlank { "未知歌手" } }
        .entries
        .sortedByDescending { it.value.maxOf { p -> p.createdAtMillis } }
        .map { it.key to it.value }

    /**
     * 本次会话所有任务的总体进度：已完成任务按 100% 计入，分母始终为整个批次的
     * 任务数，因此单曲完成时进度只会上涨而不会回跳。
     */
    fun totalProgress(tasks: Collection<DownloadProgress>, sessionStartedAt: Long): Float =
        tasks.sessionTotalProgress(sessionStartedAt)
}
