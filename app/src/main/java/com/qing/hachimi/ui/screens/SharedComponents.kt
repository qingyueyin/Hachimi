package com.qing.hachimi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.core.*
import coil3.compose.AsyncImage
import com.qing.hachimi.R
import com.qing.hachimi.data.api.ChartInfo
import com.qing.hachimi.data.api.PlaylistApi
import com.qing.hachimi.data.model.*
import com.qing.hachimi.data.repository.NeteaseRepository.Companion.coverDisplayUrl
import com.qing.hachimi.downloader.DownloadProgress
import com.qing.hachimi.downloader.DownloadStatus
import com.qing.hachimi.ui.animation.directionalEntrance
import com.qing.hachimi.ui.theme.AppChip
import com.qing.hachimi.ui.theme.AppShapes
import com.qing.hachimi.ui.theme.AppSpacing
import com.qing.hachimi.util.AppLogger
import com.qing.hachimi.util.HapticLevel
import com.qing.hachimi.util.haptic
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.window.WindowListPopup
import top.yukonga.miuix.kmp.icon.extended.Close
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SecondaryPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
            minWidth = 40.dp,
            minHeight = 40.dp,
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Back,
                contentDescription = "返回",
                modifier = Modifier.size(21.dp).graphicsLayer { if (isRtl) scaleX = -1f },
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 页面大标题（首页/一级页面标题，与 44dp 标题栏高度对齐）
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun ScreenHeading(
    title: String,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold,
        )
        if (onRefresh != null) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                Icon(MiuixIcons.Refresh, "刷新")
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Shimmer Overlay
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun ShimmerOverlay(
    modifier: Modifier = Modifier,
    shimmerColor: Color = colorScheme.onSurface.copy(alpha = 0.08f)
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        shimmerColor,
                        shimmerColor.copy(alpha = 0.3f),
                        shimmerColor
                    ),
                    start = androidx.compose.ui.geometry.Offset(translateAnim, translateAnim),
                    end = androidx.compose.ui.geometry.Offset(translateAnim + 200f, translateAnim + 200f)
                )
            )
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// Input Mode Enum
// ═════════════════════════════════════════════════════════════════════════════

enum class InputMode(
    val label: String,
    val summary: String,
    val placeholder: String,
) {
    URL(
        label = "导入链接",
        summary = "网易云歌曲、专辑或歌单链接",
        placeholder = "粘贴网易云链接",
    ),
    SEARCH(
        label = "查找音乐",
        summary = "歌曲、歌手、专辑、歌单或播客",
        placeholder = "搜索歌曲、歌手",
    ),
}

internal object CoverRequestSize {
    const val ROW = 256
    const val LIST = 320
    const val GRID = 640
    const val HERO = 1080
}

@Composable
fun CoverPreviewDialog(
    imageUrl: String?,
    onDismissRequest: () -> Unit,
) {
    if (imageUrl.isNullOrBlank()) return
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val scope = rememberCoroutineScope()
        val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
            scale = newScale
            if (newScale > 1f) {
                offset += panChange
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
        ) {
            AsyncImage(
                model = coverDisplayUrl(imageUrl, CoverRequestSize.HERO),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onDismissRequest() },
                            onDoubleTap = {
                                val targetScale = if (scale > 1f) 1f else 2.5f
                                scope.launch {
                                    animate(
                                        scale,
                                        targetScale,
                                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                                    ) { value, _ ->
                                        scale = value
                                        if (targetScale == 1f) offset = Offset.Zero
                                    }
                                }
                            },
                        )
                    }
                    .transformable(transformableState)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
            )
            IconButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(MiuixIcons.Close, "关闭", tint = Color.White)
            }
        }
    }
}

@Composable
fun CollectionDetailHeader(
    detail: CollectionDetail?,
    loadedCount: Int,
    loading: Boolean,
) {
    val resolved = detail ?: CollectionDetail(
        kind = CollectionKind.PLAYLIST,
        title = "音乐详情",
    )
    val displayCount = maxOf(loadedCount, resolved.expectedItemCount)
    val cover = coverDisplayUrl(resolved.coverUrl, CoverRequestSize.HERO).takeUnless { it.isBlank() }
    val description = resolved.description.trim()
    val detailKey = "${resolved.kind.name}:${resolved.id}:${resolved.title}"
    var descriptionExpanded by remember(detailKey) { mutableStateOf(false) }
    var previewCover by remember(detailKey) { mutableStateOf(false) }
    val canExpandDescription = description.length > 80 || description.count { it == '\n' } >= 2
    val metadata = resolved.metadataText()

    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colorScheme.surfaceVariant)
                .clickable { previewCover = true },
        ) {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize().blur(28.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(colorScheme.surface.copy(alpha = 0.78f)),
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val placeholder = painterResource(R.drawable.ic_music_placeholder)
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = placeholder,
                    error = placeholder,
                )
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = resolved.title,
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (resolved.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = resolved.subtitle,
                            color = colorScheme.onSurfaceVariantActions,
                            style = MiuixTheme.textStyles.body2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            loading && displayCount == 0 -> "${resolved.kind.label} · 正在加载"
                            else -> "${resolved.kind.label} · $displayCount ${resolved.kind.itemUnit}"
                        },
                        color = colorScheme.primary,
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (metadata.isNotBlank()) {
            Text(
                text = metadata,
                color = colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.body2,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (description.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(
                    text = description,
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.body1,
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (canExpandDescription) {
                    Text(
                        text = if (descriptionExpanded) "收起" else "展开简介",
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { descriptionExpanded = !descriptionExpanded }
                            .padding(horizontal = AppSpacing.xs, vertical = 2.dp),
                        color = colorScheme.primary,
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        CoverPreviewDialog(
            imageUrl = if (previewCover) cover else null,
            onDismissRequest = { previewCover = false },
        )
    }
}

private fun CollectionDetail.metadataText(): String = buildList {
    if (playCount > 0) add("${compactCollectionCount(playCount)} 次播放")
    if (publishTime > 0) {
        val prefix = if (kind == CollectionKind.ALBUM) "发行" else "创建"
        add("$prefix ${formatCollectionDate(publishTime)}")
    }
    if (updateTime > 0 && formatCollectionDate(updateTime) != formatCollectionDate(publishTime)) {
        add("更新 ${formatCollectionDate(updateTime)}")
    }
    if (updateFrequency.isNotBlank()) add(updateFrequency)
    if (company.isNotBlank()) add(company)
    if (subtype.isNotBlank()) add(subtype)
    if (tags.isNotEmpty()) add(tags.joinToString(" / "))
}.joinToString(" · ")

private fun compactCollectionCount(value: Long): String = when {
    value >= 100_000_000 -> "%.1f亿".format(Locale.CHINA, value / 100_000_000.0).replace(".0亿", "亿")
    value >= 10_000 -> "%.1f万".format(Locale.CHINA, value / 10_000.0).replace(".0万", "万")
    else -> value.toString()
}

private fun formatCollectionDate(timestamp: Long): String {
    if (timestamp <= 0) return ""
    return SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(timestamp))
}

// ═════════════════════════════════════════════════════════════════════════════
// ═════════════════════════════════════════════════════════════════════════════
// Album List Header
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun AlbumListHeader(
    albumName: String,
    songCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = albumName,
                style = MiuixTheme.textStyles.title3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$songCount 首歌曲",
                style = MiuixTheme.textStyles.main,
                color = colorScheme.onSurfaceVariantActions
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Song List Item (with checkbox + download status)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun SongListItem(
    song: Song,
    isSelected: Boolean,
    progress: DownloadProgress?,
    onToggle: () -> Unit
) {
    val view = LocalView.current
    val status = progress?.status
    val bgColor = when (status) {
        DownloadStatus.COMPLETED -> colorScheme.secondaryContainer.copy(alpha = 0.4f)
        DownloadStatus.FAILED -> colorScheme.error.copy(alpha = 0.15f)
        else -> colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().directionalEntrance(song.id),
        colors = CardDefaults.defaultColors(color = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    view.haptic(HapticLevel.Light)
                    onToggle()
                }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                state = if (isSelected)
                    androidx.compose.ui.state.ToggleableState.On
                else
                    androidx.compose.ui.state.ToggleableState.Off,
                onClick = {
                    view.haptic(HapticLevel.Light)
                    onToggle()
                }
            )
            Spacer(Modifier.width(8.dp))

            val placeholderPainter = painterResource(R.drawable.ic_music_placeholder)
            val coverModel = coverDisplayUrl(song.coverUrl, CoverRequestSize.ROW).takeUnless { it.isBlank() }

            AsyncImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(AppShapes.small),
                contentScale = ContentScale.Crop,
                placeholder = placeholderPainter,
                error = placeholderPainter
            )
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.name,
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artists} · ${song.album}",
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))
            StatusBadge(status, progress?.progress ?: 0f)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Simple Song Item (no checkbox, for album details)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun SimpleSongItem(
    song: Song,
    progress: DownloadProgress?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.small)
            .background(colorScheme.surfaceVariant)
            .padding(10.dp)
            .directionalEntrance(song.id),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val placeholderPainter = painterResource(R.drawable.ic_music_placeholder)
        val coverModel = coverDisplayUrl(song.coverUrl, CoverRequestSize.ROW).takeUnless { it.isBlank() }

        AsyncImage(
            model = coverModel,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(AppShapes.small),
            contentScale = ContentScale.Crop,
            placeholder = placeholderPainter,
            error = placeholderPainter
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                style = MiuixTheme.textStyles.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artists} · ${song.album}",
                color = colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Song Row Item (simple list, no checkbox)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun SongRowItem(
    song: Song,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().directionalEntrance(song.id),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val placeholder = painterResource(R.drawable.ic_music_placeholder)
            AsyncImage(
                model = coverDisplayUrl(song.coverUrl, CoverRequestSize.ROW).takeUnless { it.isBlank() },
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(AppShapes.small),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.name,
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artists} · ${song.album}",
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Album List Item
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun AlbumListItem(
    album: AlbumResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().directionalEntrance(album.id),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val placeholder = painterResource(R.drawable.ic_music_placeholder)
            val cover = coverDisplayUrl(album.coverUrl, CoverRequestSize.LIST).takeUnless { it.isBlank() }

            AsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(AppShapes.small),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist,
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                        imageVector = MiuixIcons.ChevronForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colorScheme.primary,
                    )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Album Card (for search results)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun AlbumCard(album: AlbumResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().directionalEntrance(album.id),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val placeholder = painterResource(R.drawable.ic_music_placeholder)
            val cover = coverDisplayUrl(album.coverUrl, CoverRequestSize.LIST).takeUnless { it.isBlank() }

            AsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(AppShapes.small),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist,
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                        imageVector = MiuixIcons.ChevronForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colorScheme.primary,
                    )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Artist Card (for search results)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun ArtistCard(artist: ArtistResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().directionalEntrance(artist.id),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val placeholder = painterResource(R.drawable.ic_music_placeholder)
            val avatar = coverDisplayUrl(artist.avatarUrl, CoverRequestSize.LIST).takeUnless { it.isBlank() }

            AsyncImage(
                model = avatar,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(AppShapes.circle),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = buildString {
                    if (artist.alias.isNotBlank()) {
                        append(artist.alias)
                    }
                    if (artist.albumCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${artist.albumCount} 张专辑")
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = colorScheme.onSurfaceVariantActions,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                        imageVector = MiuixIcons.ChevronForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colorScheme.primary,
                    )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Song Controls (select all + download)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun SongControls(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    isPreparingDownloads: Boolean = false,
    pagination: (@Composable () -> Unit)? = null,
) {
    val view = LocalView.current
    val selectionState = when {
        selectedCount <= 0 -> ToggleableState.Off
        selectedCount >= totalCount -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Row(
            modifier = Modifier
                .clip(AppShapes.large)
                .clickable(enabled = totalCount > 0, onClick = onSelectAll)
                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(state = selectionState, onClick = null, enabled = totalCount > 0)
            Spacer(Modifier.width(AppSpacing.xs))
            Text(
                text = if (selectionState == ToggleableState.On) "取消全选" else "全选",
                style = MiuixTheme.textStyles.body2,
            )
        }
        Text(
            text = if (selectedCount == 0) "共 $totalCount 首" else "已选 $selectedCount 首",
            color = colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2,
            maxLines = 1,
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            pagination?.invoke()
        }
        IconButton(
            onClick = {
                AppLogger.debug("CLICK: download button pressed, selected=$selectedCount")
                view.haptic(HapticLevel.Strong)
                runSongDownloadAction(selectedCount, onSelectAll, onDownload)
            },
            enabled = totalCount > 0 && !isPreparingDownloads,
            modifier = Modifier.size(40.dp),
        ) {
            if (isPreparingDownloads) {
                CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = MiuixIcons.Download,
                    contentDescription = if (selectedCount == 0) "下载全部 $totalCount 首" else "下载已选 $selectedCount 首",
                    modifier = Modifier.size(20.dp),
                    tint = if (totalCount > 0) colorScheme.primary else colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
}

internal fun runSongDownloadAction(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
) {
    if (selectedCount == 0) onSelectAll()
    onDownload()
}

// ═════════════════════════════════════════════════════════════════════════════
// Status Badge (download progress indicator)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun StatusBadge(status: DownloadStatus?, progress: Float) {
    when (status) {
        DownloadStatus.DOWNLOADING -> {
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colorScheme.primary)
                    )
                }
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = colorScheme.primary,
                    style = MiuixTheme.textStyles.body2
                )
            }
        }
        DownloadStatus.COMPLETED -> Text(
            text = "已完成",
            color = colorScheme.primary,
            style = MiuixTheme.textStyles.body2
        )
        DownloadStatus.FAILED -> Text(
            text = "失败",
            color = colorScheme.error,
            style = MiuixTheme.textStyles.body2
        )
        DownloadStatus.PENDING -> Text(
            text = "待下载",
            color = colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2
        )
        DownloadStatus.PAUSED -> Text(
            text = "已暂停",
            color = colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2
        )
        else -> {}
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Category Tabs Bar (for search results)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun UnderlineTabsBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                val underlineWidth by animateDpAsState(
                    targetValue = if (isSelected) 24.dp else 0.dp,
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    label = "UnderlineTab",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = !isSelected) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariantActions,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .width(underlineWidth)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryTabsBar(
    selected: SearchCategory,
    onSelect: (SearchCategory) -> Unit,
    counts: Map<SearchCategory, Int> = emptyMap(),
) {
    UnderlineTabsBar(
        tabs = SearchCategory.entries.map { cat ->
            val count = counts[cat]
            if (count != null && count > 0) "${cat.label} $count" else cat.label
        },
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(SearchCategory.entries[it]) },
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// Loading Indicator
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun LoadingIndicator(text: String = "加载中...") {
    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = colorScheme.primary,
            style = MiuixTheme.textStyles.body2
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Empty States
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun DiscoveryLandingEmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "选择上方入口开始发现音乐",
            color = colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2
        )
    }
}

@Composable
fun SearchLandingEmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "输入链接解析，或搜索歌曲、歌手、专辑、歌单和播客",
            color = colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Chart List Section
// ═════════════════════════════════════════════════════════════════════════════

fun LazyGridScope.ChartListSection(
    charts: List<ChartInfo>,
    onChartClick: (ChartInfo) -> Unit,
) {
    items(charts.take(20), key = { it.id }) { chart ->
        ChartGridCard(chart = chart, onClick = { onChartClick(chart) })
    }
}

@Composable
fun ChartGridCard(chart: ChartInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .directionalEntrance(chart.id),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val placeholder = painterResource(R.drawable.ic_music_placeholder)
            val cover = coverDisplayUrl(chart.coverUrl, CoverRequestSize.GRID).takeUnless { it.isBlank() }
            var isLoading by remember { mutableStateOf(true) }

            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = cover,
                    contentDescription = chart.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = placeholder,
                    error = placeholder,
                    onSuccess = { isLoading = false },
                    onError = { isLoading = false }
                )

                if (isLoading) {
                    ShimmerOverlay()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = chart.name,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = chart.updateFrequency,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colorScheme.onSurfaceVariantActions,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// New Albums Section
// ═════════════════════════════════════════════════════════════════════════════

fun LazyGridScope.NewAlbumsSection(
    albums: List<AlbumResult>,
    onAlbumClick: (AlbumResult) -> Unit,
) {
    items(albums, key = { it.id }) { album ->
        AlbumGridCard(album = album, onClick = { onAlbumClick(album) })
    }
}

@Composable
fun AlbumGridCard(album: AlbumResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .directionalEntrance(album.id),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val placeholder = painterResource(R.drawable.ic_music_placeholder)
            val cover = coverDisplayUrl(album.coverUrl, CoverRequestSize.GRID).takeUnless { it.isBlank() }
            var isLoading by remember { mutableStateOf(true) }

            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = cover,
                    contentDescription = album.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = placeholder,
                    error = placeholder,
                    onSuccess = { isLoading = false },
                    onError = { isLoading = false }
                )

                if (isLoading) {
                    ShimmerOverlay()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = album.name,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colorScheme.onSurfaceVariantActions,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// User Playlists Section
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun PlaylistListItem(playlist: PlaylistApi.UserPlaylist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(AppShapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
            .directionalEntrance(playlist.id),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverDisplayUrl(playlist.coverUrl, CoverRequestSize.ROW).takeUnless { it.isBlank() },
            contentDescription = playlist.name,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_music_placeholder),
            error = painterResource(R.drawable.ic_music_placeholder),
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${playlist.trackCount} 首歌曲",
                style = MiuixTheme.textStyles.footnote1,
                color = colorScheme.onSurfaceVariantActions,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colorScheme.onSurfaceVariantActions,
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Hot Search Section
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun HotSearchSection(
    keywords: List<Pair<String, Int>>,
    onKeywordClick: (String) -> Unit
) {
    Column {
        Text(
            text = "热搜",
            style = MiuixTheme.textStyles.subtitle,
            color = colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (keywords.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                Text("暂无热搜", style = MiuixTheme.textStyles.body2)
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(keywords.take(10), key = { it.first }) { (keyword, _) ->
                    AppChip(
                        text = keyword,
                        selected = false,
                        onClick = { onKeywordClick(keyword) }
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Load More Button
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun LoadMoreButton(isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Text(
                text = "加载中...",
                color = colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.body2
            )
        } else {
            top.yukonga.miuix.kmp.basic.TextButton(text = "加载更多", onClick = onClick)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════

// ================================================================
// Personal FM Section
// ================================================================
@Composable
fun PersonalFmSection(
    songs: List<Song>,
    groupCount: Int,
    isLoading: Boolean,
    onLoadNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Private FM", style = MiuixTheme.textStyles.subtitle, color = colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
        if (isLoading && songs.isEmpty()) {
            LoadingIndicator(text = "Loading...")
        } else if (songs.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text("Tap button to start", style = MiuixTheme.textStyles.body2)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                var currentGroup = 1
                var songIndex = 0
                while (songIndex < songs.size) {
                    if (groupCount > 1) {
                        Text("Group $currentGroup", style = MiuixTheme.textStyles.body2, color = colorScheme.onSurfaceVariantActions, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    val end = minOf(songIndex + 10, songs.size)
                    for (i in songIndex until end) {
                        SongRowItem(song = songs[i], onClick = {})
                    }
                    songIndex = end
                    currentGroup++
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLoadNext, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
            Text(if (songs.isEmpty()) "Start FM" else "Next Group")
        }
    }
}// Search Input Bar
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun SearchInputBar(
    url: String,
    searchQuery: String,
    inputMode: InputMode,
    onInputModeChange: (InputMode) -> Unit,
    onUrlChange: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onParse: () -> Unit,
    onSearch: () -> Unit,
    isLoading: Boolean,
    isSearching: Boolean,
    interactionSource: MutableInteractionSource? = null,
) {
    val isBusy = if (inputMode == InputMode.URL) isLoading else isSearching
    val value = if (inputMode == InputMode.URL) url else searchQuery
    val onValueChange = if (inputMode == InputMode.URL) onUrlChange else onSearchQueryChange
    val actionEnabled = !isBusy && value.isNotBlank()
    val submit = {
        if (actionEnabled) {
            if (inputMode == InputMode.URL) onParse() else onSearch()
        }
    }

    TextField(
        value = value,
        onValueChange = onValueChange,
        label = inputMode.placeholder,
        useLabelAsPlaceholder = true,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isBusy,
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions(
            imeAction = if (inputMode == InputMode.URL) ImeAction.Go else ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(
            onGo = { submit() },
            onSearch = { submit() },
        ),
        leadingIcon = {
            SearchInputModeSelector(
                inputMode = inputMode,
                enabled = !isBusy,
                onInputModeChange = onInputModeChange,
            )
        },
        trailingIcon = {
            SearchInputAction(
                inputMode = inputMode,
                isBusy = isBusy,
                enabled = actionEnabled,
                onClick = submit,
            )
        },
    )
}

@Composable
private fun SearchInputModeSelector(
    inputMode: InputMode,
    enabled: Boolean,
    onInputModeChange: (InputMode) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val modes = remember { listOf(InputMode.SEARCH, InputMode.URL) }
    val modeIcon = if (inputMode == InputMode.SEARCH) MiuixIcons.Search else MiuixIcons.Link

    Box(modifier = Modifier.padding(start = 10.dp, end = 8.dp)) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(AppShapes.medium)
                .clickable(enabled = enabled) { showMenu = true }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = modeIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colorScheme.onSurfaceVariantActions,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = inputMode.label,
                color = if (enabled) colorScheme.onSurface else colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = MiuixIcons.ExpandMore,
                contentDescription = "切换输入方式",
                modifier = Modifier.size(14.dp),
                tint = colorScheme.primary,
            )
        }
        WindowListPopup(
            show = showMenu,
            enableWindowDim = false,
            onDismissRequest = { showMenu = false },
            minWidth = 200.dp,
        ) {
            ListPopupColumn {
                modes.forEachIndexed { index, mode ->
                    val icon = if (mode == InputMode.SEARCH) MiuixIcons.Search else MiuixIcons.Link
                    DropdownImpl(
                        item = DropdownItem(
                            text = mode.label,
                            summary = mode.summary,
                            icon = { modifier ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = modifier.size(20.dp),
                                )
                            },
                        ),
                        optionSize = modes.size,
                        isSelected = inputMode == mode,
                        index = index,
                        onSelectedIndexChange = {
                            onInputModeChange(modes[it])
                            showMenu = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchInputAction(
    inputMode: InputMode,
    isBusy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val actionLabel = when {
        isBusy && inputMode == InputMode.SEARCH -> "搜索中"
        isBusy -> "导入中"
        inputMode == InputMode.SEARCH -> "搜索"
        else -> "导入"
    }
    Row(
        modifier = Modifier
            .padding(start = 8.dp, end = 10.dp)
            .heightIn(min = 40.dp)
            .clip(AppShapes.medium)
            .clickable(enabled = enabled) {
                view.haptic(HapticLevel.Medium)
                onClick()
            }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isBusy) {
            CircularProgressIndicator(size = 17.dp, strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = if (inputMode == InputMode.SEARCH) MiuixIcons.Search else MiuixIcons.Link,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) colorScheme.primary else colorScheme.onSurfaceVariantActions,
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = actionLabel,
            color = if (enabled || isBusy) colorScheme.primary else colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
