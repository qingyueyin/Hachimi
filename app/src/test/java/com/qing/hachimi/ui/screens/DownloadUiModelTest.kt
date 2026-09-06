package com.qing.hachimi.ui.screens

import com.qing.hachimi.downloader.DownloadProgress
import com.qing.hachimi.downloader.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadUiModelTest {

    @Test
    fun `active tasks are ordered by actionable status then newest`() {
        val tasks = listOf(
            progress(1, DownloadStatus.FAILED, 300),
            progress(2, DownloadStatus.DOWNLOADING, 100),
            progress(3, DownloadStatus.DOWNLOADING, 200),
            progress(4, DownloadStatus.COMPLETED, 400),
            progress(5, DownloadStatus.CANCELLED, 500),
            progress(6, DownloadStatus.PAUSED, 600),
            progress(7, DownloadStatus.PENDING, 700),
        )

        assertEquals(
            listOf(3L, 2L, 7L, 6L, 1L, 5L),
            DownloadUiModel.activeTasks(tasks).map { it.songId },
        )
    }

    @Test
    fun `bulk targets only include applicable task states`() {
        val tasks = listOf(
            progress(1, DownloadStatus.DOWNLOADING, 1),
            progress(2, DownloadStatus.PENDING, 2),
            progress(3, DownloadStatus.PAUSED, 3),
            progress(4, DownloadStatus.FAILED, 4),
            progress(5, DownloadStatus.CANCELLED, 5),
            progress(6, DownloadStatus.COMPLETED, 6),
        )

        assertEquals(setOf(1L, 2L), DownloadUiModel.pauseTargets(tasks))
        assertEquals(setOf(3L), DownloadUiModel.resumeTargets(tasks))
        assertEquals(setOf(4L, 5L), DownloadUiModel.retryTargets(tasks))
        assertEquals(setOf(6L), DownloadUiModel.completedTargets(tasks))
    }

    @Test
    fun `average progress ignores failed cancelled and completed tasks`() {
        val tasks = listOf(
            progress(1, DownloadStatus.DOWNLOADING, 1, 0.25f),
            progress(2, DownloadStatus.PENDING, 2, 0f),
            progress(3, DownloadStatus.PAUSED, 3, 0.5f),
            progress(4, DownloadStatus.FAILED, 4, 0.9f),
            progress(5, DownloadStatus.CANCELLED, 5, 0.8f),
            progress(6, DownloadStatus.COMPLETED, 6, 1f),
        )

        assertEquals(0.25f, DownloadUiModel.averageActiveProgress(tasks), 0.0001f)
    }

    @Test
    fun `failure label uses specific message then falls back`() {
        val withReason = progress(1, DownloadStatus.FAILED, 1).copy(errorMessage = "网络中断，请重试")
        val blank = progress(2, DownloadStatus.FAILED, 2).copy(errorMessage = "  ")
        val missing = progress(3, DownloadStatus.FAILED, 3)

        assertEquals("网络中断，请重试", DownloadUiModel.failureLabel(withReason))
        assertEquals("下载失败", DownloadUiModel.failureLabel(blank))
        assertEquals("下载失败", DownloadUiModel.failureLabel(missing))
    }

    private fun progress(
        id: Long,
        status: DownloadStatus,
        createdAtMillis: Long,
        value: Float = 0f,
    ) = DownloadProgress(
        songId = id,
        songName = "Song $id",
        progress = value,
        speedKBps = 0f,
        status = status,
        createdAtMillis = createdAtMillis,
    )
}
