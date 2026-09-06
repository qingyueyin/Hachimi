package com.qing.hachimi.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.qing.hachimi.R
import com.qing.hachimi.data.repository.NeteaseRepository.Companion.coverDisplayUrl
import com.qing.hachimi.downloader.DownloadProgress
import com.qing.hachimi.downloader.DownloadStatus
import com.qing.hachimi.downloader.MetadataStatus
import com.qing.hachimi.ui.animation.LocalScrollDirection
import com.qing.hachimi.ui.animation.directionalEntrance
import com.qing.hachimi.ui.animation.rememberScrollDirection
import com.qing.hachimi.ui.theme.AppShapes
import com.qing.hachimi.ui.theme.AppSpacing
import com.qing.hachimi.util.HapticLevel
import com.qing.hachimi.util.haptic
import java.util.Locale
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun DownloadsTab(
    downloadProgress: Map<Long, DownloadProgress>,
    onPause: (Long) -> Unit = {},
    onResume: (Long) -> Unit = {},
    onCancel: (Long) -> Unit = {},
    onRemoveActiveTask: (Long) -> Unit = {},
    onDismissCompleted: (Long) -> Unit = {},
    onDeleteFile: (Long) -> Unit = {},
    onOpenFile: (Long) -> Unit = {},
    onPauseAll: (Set<Long>) -> Unit = {},
    onResumeAll: (Set<Long>) -> Unit = {},
    onRetryAll: (Set<Long>) -> Unit = {},
    onDismissCompletedAll: () -> Unit = {},
    innerPadding: PaddingValues = PaddingValues(0.dp),
    scrollToTopTrigger: Int = 0,
    sessionStartedAt: Long = 0L,
) {
    var selectedView by remember { mutableStateOf(DownloadView.ACTIVE) }
    var deleteCandidate by remember { mutableStateOf<DownloadProgress?>(null) }
    val allTasks = downloadProgress.values
    val activeTasks = remember(downloadProgress) { DownloadUiModel.activeTasks(allTasks) }
    val completedTasks = remember(downloadProgress) { DownloadUiModel.completedTasks(allTasks) }
    val pauseTargets = remember(downloadProgress) { DownloadUiModel.pauseTargets(allTasks) }
    val resumeTargets = remember(downloadProgress) { DownloadUiModel.resumeTargets(allTasks) }
    val retryTargets = remember(downloadProgress) { DownloadUiModel.retryTargets(allTasks) }
    val completedTargets = remember(downloadProgress) { DownloadUiModel.completedTargets(allTasks) }
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = AppSpacing.md),
        ) {
            ScreenHeading("下载")
            UnderlineTabsBar(
                tabs = listOf("进行中 ${activeTasks.size}", "已完成 ${completedTasks.size}"),
                selectedIndex = selectedView.ordinal,
                onSelect = { selectedView = DownloadView.entries[it] },
                modifier = Modifier.padding(top = AppSpacing.xs),
            )
            Spacer(Modifier.height(AppSpacing.sm))

            if (selectedView == DownloadView.ACTIVE) {
                ActiveSummary(allTasks, sessionStartedAt)
                if (pauseTargets.isNotEmpty() || resumeTargets.isNotEmpty() || retryTargets.isNotEmpty()) {
                    Spacer(Modifier.height(AppSpacing.sm))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        if (pauseTargets.isNotEmpty()) {
                            item {
                                BatchActionButton(
                                    label = "全部暂停",
                                    icon = MiuixIcons.Pause,
                                    onClick = { onPauseAll(pauseTargets) },
                                )
                            }
                        }
                        if (resumeTargets.isNotEmpty()) {
                            item {
                                BatchActionButton(
                                    label = "全部继续",
                                    icon = MiuixIcons.Play,
                                    onClick = { onResumeAll(resumeTargets) },
                                )
                            }
                        }
                        if (retryTargets.isNotEmpty()) {
                            item {
                                BatchActionButton(
                                    label = "全部重试",
                                    icon = MiuixIcons.Refresh,
                                    onClick = { onRetryAll(retryTargets) },
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${completedTasks.size} 个文件",
                        style = MiuixTheme.textStyles.body1,
                        color = colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.weight(1f),
                    )
                    if (completedTargets.isNotEmpty()) {
                        BatchActionButton(
                            label = "清除记录",
                            icon = MiuixIcons.Clear,
                            onClick = onDismissCompletedAll,
                        )
                    }
                }
            }
            Spacer(Modifier.height(AppSpacing.sm))
        }

        AnimatedContent(
            targetState = selectedView,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                peerContentTransition()
            },
            label = "DownloadViewTransition",
        ) { view ->
            val tasks = if (view == DownloadView.ACTIVE) activeTasks else completedTasks
            val groupedTasks = remember(tasks) { DownloadUiModel.groupByArtist(tasks) }
            CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
            Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AppSpacing.md,
                    end = AppSpacing.md,
                    top = AppSpacing.xs,
                    bottom = LocalMainBottomContentPadding.current,
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                if (tasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (view == DownloadView.ACTIVE) {
                                    "当前没有进行中的任务"
                                } else {
                                    "还没有完成的下载"
                                },
                                color = colorScheme.onSurfaceVariantActions,
                                style = MiuixTheme.textStyles.body1,
                            )
                        }
                    }
                } else if (view == DownloadView.COMPLETED) {
                    groupedTasks.forEach { (artist, groupTasks) ->
                        item(key = "artist:$artist") {
                            ArtistGroupHeader(artist, groupTasks.size)
                        }
                        items(groupTasks, key = { it.songId }) { progress ->
                            DownloadTaskItem(
                                progress = progress,
                                onPause = onPause,
                                onResume = onResume,
                                onCancel = onCancel,
                                onRemoveActiveTask = onRemoveActiveTask,
                                onDismissCompleted = onDismissCompleted,
                                onDeleteFile = { deleteCandidate = progress },
                                onOpenFile = onOpenFile,
                            )
                        }
                    }
                } else {
                    items(tasks, key = { it.songId }) { progress ->
                        DownloadTaskItem(
                            progress = progress,
                            onPause = onPause,
                            onResume = onResume,
                            onCancel = onCancel,
                            onRemoveActiveTask = onRemoveActiveTask,
                            onDismissCompleted = onDismissCompleted,
                            onDeleteFile = { deleteCandidate = progress },
                            onOpenFile = onOpenFile,
                        )
                    }
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
            }
            }
        }
    }

    WindowDialog(
        show = deleteCandidate != null,
        title = "删除下载文件",
        summary = deleteCandidate?.let { "“${it.songName}”将从设备中删除，此操作不可撤销。" },
        onDismissRequest = { deleteCandidate = null },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(text = "取消", onClick = { deleteCandidate = null })
            Spacer(Modifier.width(AppSpacing.sm))
            Button(
                onClick = {
                    deleteCandidate?.songId?.let(onDeleteFile)
                    deleteCandidate = null
                },
                colors = ButtonDefaults.buttonColors(
                    color = colorScheme.error,
                    contentColor = colorScheme.onError,
                ),
            ) {
                Text("删除文件")
            }
        }
    }
}

@Composable
private fun ArtistGroupHeader(artist: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = artist,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Text(
            text = "$count 首",
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantActions,
        )
    }
}

@Composable
private fun ActiveSummary(tasks: Collection<DownloadProgress>, sessionStartedAt: Long) {
    val downloading = tasks.count { it.status == DownloadStatus.DOWNLOADING }
    val pending = tasks.count { it.status == DownloadStatus.PENDING }
    val paused = tasks.count { it.status == DownloadStatus.PAUSED }
    val needsAttention = tasks.count {
        it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED
    }
    val progressingCount = downloading + pending + paused

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = buildList {
                if (downloading > 0) add("$downloading 下载中")
                if (pending > 0) add("$pending 等待")
                if (paused > 0) add("$paused 已暂停")
                if (needsAttention > 0) add("$needsAttention 需处理")
            }.joinToString(" · ").ifBlank { "没有待处理任务" },
            style = MiuixTheme.textStyles.body1,
            color = colorScheme.onSurfaceVariantActions,
        )
        if (progressingCount > 0) {
            Spacer(Modifier.height(AppSpacing.xs))
            val total = DownloadUiModel.totalProgress(tasks, sessionStartedAt)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = total,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    text = "${(total * 100).toInt()}%",
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
}

@Composable
private fun BatchActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Button(
        onClick = {
            view.haptic(HapticLevel.Medium)
            onClick()
        },
        minWidth = 0.dp,
        minHeight = 36.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            color = colorScheme.surfaceVariant,
            contentColor = colorScheme.onSurface,
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(AppSpacing.xs))
        Text(label, style = MiuixTheme.textStyles.body2)
    }
}

@Composable
private fun DownloadTaskItem(
    progress: DownloadProgress,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onRemoveActiveTask: (Long) -> Unit,
    onDismissCompleted: (Long) -> Unit,
    onDeleteFile: (Long) -> Unit,
    onOpenFile: (Long) -> Unit,
) {
    val backgroundColor = when (progress.status) {
        DownloadStatus.FAILED -> colorScheme.error.copy(alpha = 0.12f)
        DownloadStatus.CANCELLED -> colorScheme.surfaceVariant.copy(alpha = 0.72f)
        else -> colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().directionalEntrance(progress.songId),
        colors = CardDefaults.defaultColors(color = backgroundColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(AppSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val placeholder = painterResource(R.drawable.ic_music_placeholder)
                AsyncImage(
                    model = coverDisplayUrl(progress.coverUrl, CoverRequestSize.ROW).takeUnless(String::isBlank),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(AppShapes.small),
                    contentScale = ContentScale.Crop,
                    placeholder = placeholder,
                    error = placeholder,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = progress.songName,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (progress.artists.isNotBlank()) {
                        Text(
                            text = progress.artists,
                            style = MiuixTheme.textStyles.body2,
                            color = colorScheme.onSurfaceVariantActions,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = statusText(progress),
                        style = MiuixTheme.textStyles.body2,
                        color = if (progress.status == DownloadStatus.FAILED ||
                            progress.metadataStatus == MetadataStatus.FAILED
                        ) {
                            colorScheme.error
                        } else {
                            colorScheme.onSurfaceVariantActions
                        },
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(AppSpacing.xs))
                DownloadActions(
                    progress = progress,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onRemoveActiveTask = onRemoveActiveTask,
                    onDismissCompleted = onDismissCompleted,
                    onDeleteFile = onDeleteFile,
                    onOpenFile = onOpenFile,
                )
            }

            if (progress.status == DownloadStatus.DOWNLOADING ||
                progress.status == DownloadStatus.PENDING ||
                progress.status == DownloadStatus.PAUSED
            ) {
                Spacer(Modifier.height(AppSpacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colorScheme.surface),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.progress.coerceIn(0f, 1f))
                            .background(
                                if (progress.status == DownloadStatus.PAUSED) {
                                    colorScheme.onSurfaceVariantActions
                                } else {
                                    colorScheme.primary
                                }
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadActions(
    progress: DownloadProgress,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onRemoveActiveTask: (Long) -> Unit,
    onDismissCompleted: (Long) -> Unit,
    onDeleteFile: (Long) -> Unit,
    onOpenFile: (Long) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (progress.status) {
            DownloadStatus.DOWNLOADING -> {
                TaskAction("暂停", MiuixIcons.Pause) { onPause(progress.songId) }
                TaskAction("取消", MiuixIcons.Close) { onCancel(progress.songId) }
            }
            DownloadStatus.PENDING -> {
                TaskAction("取消", MiuixIcons.Close) { onCancel(progress.songId) }
            }
            DownloadStatus.PAUSED -> {
                TaskAction("继续", MiuixIcons.Play) { onResume(progress.songId) }
                TaskAction("取消", MiuixIcons.Close) { onCancel(progress.songId) }
            }
            DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                TaskAction("重试", MiuixIcons.Refresh) { onResume(progress.songId) }
                TaskAction("移除任务", MiuixIcons.Clear) { onRemoveActiveTask(progress.songId) }
            }
            DownloadStatus.COMPLETED -> {
                if (progress.fileAvailable) {
                    TaskAction("打开文件", MiuixIcons.Folder) { onOpenFile(progress.songId) }
                    TaskAction("移除记录", MiuixIcons.Clear) { onDismissCompleted(progress.songId) }
                    TaskAction("删除文件", MiuixIcons.Delete, tint = colorScheme.error) {
                        onDeleteFile(progress.songId)
                    }
                } else {
                    TaskAction("移除记录", MiuixIcons.Clear) { onDismissCompleted(progress.songId) }
                }
            }
        }
    }
}

@Composable
private fun TaskAction(
    label: String,
    icon: ImageVector,
    tint: Color = colorScheme.onSurfaceVariantActions,
    onClick: () -> Unit,
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    Box(modifier = Modifier.hoverable(hoverInteraction)) {
        IconButton(
            onClick = onClick,
            minWidth = 36.dp,
            minHeight = 36.dp,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(19.dp),
                tint = tint,
            )
        }
        if (isHovered) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -44),
                properties = PopupProperties(focusable = false),
            ) {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurface,
                    modifier = Modifier
                        .background(colorScheme.surface, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private fun statusText(progress: DownloadProgress): String = when (progress.status) {
    DownloadStatus.PENDING -> "等待中 · ${(progress.progress * 100).toInt()}%"
    DownloadStatus.DOWNLOADING -> if (progress.speedKBps > 0f) {
        "下载中 · ${String.format(Locale.ROOT, "%.1f", progress.speedKBps)} KB/s"
    } else {
        "下载中 · ${(progress.progress * 100).toInt()}%"
    }
    DownloadStatus.PAUSED -> "已暂停 · ${(progress.progress * 100).toInt()}%"
    DownloadStatus.COMPLETED -> when {
        !progress.fileAvailable -> "文件位置已改变"
        progress.metadataStatus == MetadataStatus.FAILED -> "已完成 · 标签写入失败"
        else -> "已完成"
    }
    DownloadStatus.FAILED -> DownloadUiModel.failureLabel(progress)
    DownloadStatus.CANCELLED -> "已取消"
}
