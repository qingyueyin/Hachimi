package com.qing.hachimi.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.qing.hachimi.MainActivity
import com.qing.hachimi.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DownloadForegroundService : Service() {

    private val downloadEngine: DownloadEngine by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        AppLogger.debug("DownloadForegroundService: created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification(downloadEngine.allTasks))
                notificationJob?.cancel()
                notificationJob = serviceScope.launch {
                    downloadEngine.progressMap.collectLatest { map ->
                        updateNotification(map)
                    }
                }
                AppLogger.debug("DownloadForegroundService: started")
            }
            ACTION_UPDATE -> {
                val notification = buildNotification(downloadEngine.allTasks)
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                AppLogger.debug("DownloadForegroundService: stopped")
            }
        }
        return START_NOT_STICKY
    }

    private fun updateNotification(progress: Map<Long, DownloadProgress>) {
        val notification = buildNotification(progress)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)

        val hasActive = progress.values.any {
            it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.PENDING ||
                it.status == DownloadStatus.PAUSED
        }
        if (!hasActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            AppLogger.debug("DownloadForegroundService: auto-stopped (no active downloads)")
        }
    }

    private fun buildNotification(progress: Map<Long, DownloadProgress>): Notification {
        val active = progress.values.filter { it.status == DownloadStatus.DOWNLOADING }
        val totalActive = active.size
        val totalPending = progress.values.count { it.status == DownloadStatus.PENDING }
        val totalPaused = progress.values.count { it.status == DownloadStatus.PAUSED }
        val totalCompleted = progress.values.count { it.status == DownloadStatus.COMPLETED }
        val totalFailed = progress.values.count { it.status == DownloadStatus.FAILED }

        // 批次总进度：已完成任务按 100% 计入，避免单曲完成时进度条回跳
        val overallProgress = progress.values.sessionTotalProgress(downloadEngine.sessionStartedAt)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = buildString {
            if (totalActive > 0) {
                append("正在下载 $totalActive 首")
                if (totalPending > 0) append("，$totalPending 首等待")
            } else if (totalPending > 0) {
                append("等待下载 $totalPending 首")
            } else if (totalPaused > 0) {
                append("已暂停 $totalPaused 首")
            } else if (totalCompleted > 0) {
                append("已完成 $totalCompleted 首")
            }
            if (totalFailed > 0) append("，$totalFailed 首失败")
        }

        val hasOngoing = totalActive > 0 || totalPending > 0 || totalPaused > 0

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hachimi")
            .setContentText(body)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(hasOngoing)
            .apply {
                if (totalActive > 0) {
                    setProgress(100, (overallProgress * 100).toInt(), false)
                } else {
                    setProgress(0, 0, false)
                }
            }
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "下载进度",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示歌曲下载进度"
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        notificationJob?.cancel()
        serviceScope.cancel()
        AppLogger.debug("DownloadForegroundService: destroyed")
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "download_progress"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.qing.hachimi.action.START_DOWNLOAD"
        const val ACTION_UPDATE = "com.qing.hachimi.action.UPDATE_DOWNLOAD"
        const val ACTION_STOP = "com.qing.hachimi.action.STOP_DOWNLOAD"

        fun start(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
