package com.qing.hachimi.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImage
import com.qing.hachimi.R
import com.qing.hachimi.data.api.ChartInfo
import com.qing.hachimi.data.model.AlbumResult
import com.qing.hachimi.data.model.ArtistArea
import com.qing.hachimi.data.model.ArtistDetailSection
import com.qing.hachimi.data.model.ArtistResult
import com.qing.hachimi.data.model.DiscoveryPlaylist
import com.qing.hachimi.data.model.PlaylistCategory
import com.qing.hachimi.data.model.PodcastChannel
import com.qing.hachimi.data.model.SongSortOption
import com.qing.hachimi.data.model.SourceMode
import com.qing.hachimi.data.api.StyleTag
import com.qing.hachimi.data.model.sortedByOption
import com.qing.hachimi.data.repository.NeteaseRepository.Companion.coverDisplayUrl
import com.qing.hachimi.downloader.DownloadProgress
import com.qing.hachimi.ui.animation.LocalScrollDirection
import com.qing.hachimi.ui.animation.directionalEntrance
import com.qing.hachimi.ui.animation.rememberScrollDirection
import com.qing.hachimi.ui.theme.AppChip
import com.qing.hachimi.ui.theme.AppShapes
import com.qing.hachimi.ui.theme.AppSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowListPopup

private enum class DiscoverLevel { HOME, CATEGORY, DETAIL }

private data class DiscoverDestination(
    val label: String,
    val mode: SourceMode,
    val icon: ImageVector,
)

private val destinations = listOf(
    DiscoverDestination("排行榜", SourceMode.CHARTS, MiuixIcons.TopDownloads),
    DiscoverDestination("歌单", SourceMode.PLAYLISTS, MiuixIcons.Playlist),
    DiscoverDestination("播客", SourceMode.PODCASTS, MiuixIcons.Mic),
    DiscoverDestination("歌手", SourceMode.ARTISTS, MiuixIcons.Contacts),
    DiscoverDestination("新碟", SourceMode.NEW_ALBUMS, MiuixIcons.Album),
    DiscoverDestination("新歌", SourceMode.NEW_SONGS, MiuixIcons.Music),
)

internal fun availableDiscoverSourceModes(isLoggedIn: Boolean): List<SourceMode> = buildList {
    add(SourceMode.RECOMMEND)
    addAll(destinations.map { it.mode })
    if (isLoggedIn) add(SourceMode.PERSONAL_FM)
}

@Composable
fun DiscoverTab(
    uiState: DiscoverUiState,
    isLoggedIn: Boolean,
    downloadProgress: Map<Long, DownloadProgress>,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit = {},
    onSetSourceMode: (SourceMode) -> Unit,
    onChartClick: (ChartInfo) -> Unit = {},
    onLoadRecommendSongs: () -> Unit = {},
    onPlaylistClick: (DiscoveryPlaylist) -> Unit = {},
    onPodcastClick: (PodcastChannel) -> Unit = {},
    onArtistClick: (ArtistResult) -> Unit = {},
    onAlbumClick: (AlbumResult) -> Unit = {},
    onSetPlaylistCategory: (PlaylistCategory) -> Unit = {},
    onSetArtistArea: (ArtistArea) -> Unit = {},
    onSetNewSongsArea: (Int) -> Unit = {},
    onSelectStyleTag: (StyleTag) -> Unit = {},
    onSetArtistDetailSection: (ArtistDetailSection) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onLoadMoreDetail: () -> Unit = {},
    onLoadMoreArtistSection: () -> Unit = {},
    onLoadNextFmGroup: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onRetry: () -> Unit = {},
    innerPadding: PaddingValues = PaddingValues(0.dp),
    sortOption: SongSortOption = SongSortOption.DEFAULT,
    sortAscending: Boolean = true,
    scrollToTopTrigger: Int = 0,
) {
    val horizontalDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val level = when {
        uiState.isDetailView || uiState.isLoading -> DiscoverLevel.DETAIL
        uiState.sourceMode == SourceMode.RECOMMEND -> DiscoverLevel.HOME
        else -> DiscoverLevel.CATEGORY
    }
    val sortedSongs = remember(uiState.songs, sortOption, sortAscending) {
        uiState.songs.sortedByOption(sortOption, sortAscending)
    }
    val suppressNav = LocalSuppressNavTransition.current
    AnimatedContent(
        targetState = level,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            hierarchicalNavigationTransition(
                isForward = targetState.ordinal > initialState.ordinal,
                layoutDirection = horizontalDirection,
                suppress = suppressNav,
            )
        },
        label = "DiscoverLevel",
    ) { target ->
        when (target) {
            DiscoverLevel.HOME -> DiscoverHome(
                state = uiState,
                isLoggedIn = isLoggedIn,
                onOpen = onSetSourceMode,
                onChartClick = onChartClick,
                onDailyClick = onLoadRecommendSongs,
                onPlaylistClick = onPlaylistClick,
                onPodcastClick = onPodcastClick,
                onArtistClick = onArtistClick,
                onAlbumClick = onAlbumClick,
                onRefresh = onRefresh,
                onRetry = onRetry,
                innerPadding = innerPadding,
                scrollToTopTrigger = scrollToTopTrigger,
            )
            DiscoverLevel.CATEGORY -> DiscoverCategory(
                state = uiState,
                downloadProgress = downloadProgress,
                onToggleSelect = onToggleSelect,
                onSelectAll = onSelectAll,
                onDownload = onDownload,
                onChartClick = onChartClick,
                onPlaylistClick = onPlaylistClick,
                onPodcastClick = onPodcastClick,
                onArtistClick = onArtistClick,
                onAlbumClick = onAlbumClick,
                onSetPlaylistCategory = onSetPlaylistCategory,
                onSetArtistArea = onSetArtistArea,
                onSetNewSongsArea = onSetNewSongsArea,
                onSelectStyleTag = onSelectStyleTag,
                onLoadMore = onLoadMore,
                onLoadNextFmGroup = onLoadNextFmGroup,
                onRefresh = onRefresh,
                onRetry = onRetry,
                innerPadding = innerPadding,
            )
            DiscoverLevel.DETAIL -> {
                val detailKey = if (uiState.isArtistPage) {
                    "artist:${uiState.activeArtist?.id}"
                } else {
                    val detail = uiState.collectionDetail
                    if (detail != null) "songs:${detail.kind.name}:${detail.id}"
                    else "songs:${uiState.playlistName}"
                }
                AnimatedContent(
                    targetState = detailKey,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        peerContentTransition()
                    },
                    label = "DiscoverDetail",
                ) { key ->
                    if (key.startsWith("artist:")) {
                        ArtistDetailPage(
                            state = uiState,
                            songs = sortedSongs,
                            downloadProgress = downloadProgress,
                            onToggleSelect = onToggleSelect,
                            onSelectAll = onSelectAll,
                            onDownload = onDownload,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            onSetSection = onSetArtistDetailSection,
                            onLoadMore = onLoadMoreArtistSection,
                            onRetry = onRetry,
                            innerPadding = innerPadding,
                        )
                    } else {
                        DiscoverSongDetail(
                            state = uiState,
                            songs = sortedSongs,
                            downloadProgress = downloadProgress,
                            onToggleSelect = onToggleSelect,
                            onSelectAll = onSelectAll,
                            onDownload = onDownload,
                            onLoadMoreDetail = onLoadMoreDetail,
                            onRetry = onRetry,
                            innerPadding = innerPadding,
                            sortKey = "$sortOption:$sortAscending",
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun DiscoverHome(
    state: DiscoverUiState,
    isLoggedIn: Boolean,
    onOpen: (SourceMode) -> Unit,
    onChartClick: (ChartInfo) -> Unit,
    onDailyClick: () -> Unit,
    onPlaylistClick: (DiscoveryPlaylist) -> Unit,
    onPodcastClick: (PodcastChannel) -> Unit,
    onArtistClick: (ArtistResult) -> Unit,
    onAlbumClick: (AlbumResult) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    innerPadding: PaddingValues,
    scrollToTopTrigger: Int = 0,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }
    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpacing.md,
            end = AppSpacing.md,
            top = innerPadding.calculateTopPadding(),
            bottom = LocalMainBottomContentPadding.current,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        item {
            ScreenHeading("精选推荐", onRefresh)
            StatusMessage(state.statusMessage, onRetry)
        }
        item { DestinationGrid(onOpen) }
        if (isLoggedIn) {
            item {
                LoggedInActions(
                    hasDailySongs = state.recommendSongs.isNotEmpty(),
                    onDailyClick = onDailyClick,
                )
            }
        }
        item {
            HorizontalSection(
                title = "推荐歌单",
                items = state.recommendedPlaylists,
                loading = state.isLoadingRecommendations,
                onMore = { onOpen(SourceMode.PLAYLISTS) },
                key = { it.id },
            ) { playlist -> DiscoveryPlaylistCard(playlist) { onPlaylistClick(playlist) } }
        }
        item {
            HorizontalSection(
                title = "热门榜单",
                items = state.chartList.take(8),
                loading = state.isLoadingCharts,
                onMore = { onOpen(SourceMode.CHARTS) },
                key = { it.id },
            ) { chart -> ChartGridCard(chart) { onChartClick(chart) } }
        }
        item {
            HorizontalSection(
                title = "新碟上架",
                items = state.newAlbumsList.take(8),
                loading = state.isLoadingAlbums,
                onMore = { onOpen(SourceMode.NEW_ALBUMS) },
                key = { it.id },
            ) { album -> AlbumGridCard(album) { onAlbumClick(album) } }
        }
        item {
            HorizontalSection(
                title = "热门播客",
                items = state.podcastChannels.take(8),
                loading = state.isLoadingPodcasts,
                onMore = { onOpen(SourceMode.PODCASTS) },
                key = { it.id },
            ) { podcast -> PodcastCard(podcast) { onPodcastClick(podcast) } }
        }
        item {
            HorizontalSection(
                title = "热门歌手",
                items = state.artists.take(8),
                loading = state.isLoadingArtists,
                onMore = { onOpen(SourceMode.ARTISTS) },
                key = { it.id },
            ) { artist -> ArtistDiscoveryCard(artist) { onArtistClick(artist) } }
        }
    }
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
    }
}
}

@Composable
private fun DestinationGrid(onOpen: (SourceMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        destinations.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                row.forEach { destination ->
                    Card(
                        modifier = Modifier.weight(1f).height(72.dp).clickable { onOpen(destination.mode) },
                        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(AppSpacing.sm),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(destination.icon, null, Modifier.size(23.dp), tint = colorScheme.primary)
                            Spacer(Modifier.height(AppSpacing.xs))
                            Text(destination.label, style = MiuixTheme.textStyles.body2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoggedInActions(
    hasDailySongs: Boolean,
    onDailyClick: () -> Unit,
) {
    QuickAction(
        modifier = Modifier.fillMaxWidth(),
        title = "每日推荐",
        subtitle = if (hasDailySongs) "打开今日歌曲" else "正在准备",
        icon = MiuixIcons.FavoritesFill,
        enabled = hasDailySongs,
        onClick = onDailyClick,
    )
}

@Composable
private fun QuickAction(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.height(72.dp).clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = colorScheme.primary)
            }
            Spacer(Modifier.width(AppSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    subtitle,
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun <T> HorizontalSection(
    title: String,
    items: List<T>,
    loading: Boolean,
    onMore: () -> Unit,
    key: (T) -> Any,
    content: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        SectionHeading(title, onMore)
        when {
            items.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                items(items, key = key) { item ->
                    Box(Modifier.width(142.dp)) { content(item) }
                }
            }
            loading -> LoadingIndicator("正在加载...")
            else -> Text(
                "暂时没有内容",
                color = colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(vertical = AppSpacing.lg),
            )
        }
    }
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun DiscoverCategory(
    state: DiscoverUiState,
    downloadProgress: Map<Long, DownloadProgress>,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onChartClick: (ChartInfo) -> Unit,
    onPlaylistClick: (DiscoveryPlaylist) -> Unit,
    onPodcastClick: (PodcastChannel) -> Unit,
    onArtistClick: (ArtistResult) -> Unit,
    onAlbumClick: (AlbumResult) -> Unit,
    onSetPlaylistCategory: (PlaylistCategory) -> Unit,
    onSetArtistArea: (ArtistArea) -> Unit,
    onSetNewSongsArea: (Int) -> Unit,
    onSelectStyleTag: (StyleTag) -> Unit,
    onLoadMore: () -> Unit,
    onLoadNextFmGroup: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    innerPadding: PaddingValues,
) {
    if (state.sourceMode == SourceMode.PERSONAL_FM) {
        FmCategory(
            state,
            downloadProgress,
            onToggleSelect,
            onSelectAll,
            onDownload,
            onLoadNextFmGroup,
            onRetry,
            innerPadding,
        )
        return
    }
    if (state.sourceMode == SourceMode.NEW_SONGS) {
        NewSongsCategory(
            state = state,
            downloadProgress = downloadProgress,
            onToggleSelect = onToggleSelect,
            onSelectAll = onSelectAll,
            onDownload = onDownload,
            onSetNewSongsArea = onSetNewSongsArea,
            onRetry = onRetry,
            innerPadding = innerPadding,
        )
        return
    }
    if (state.sourceMode == SourceMode.STYLE_PLAYLISTS) {
        StyleCategory(
            state = state,
            onSelectStyleTag = onSelectStyleTag,
            onRetry = onRetry,
            innerPadding = innerPadding,
        )
        return
    }
    if (state.sourceMode == SourceMode.CHARTS || state.sourceMode == SourceMode.ARTISTS) {
        DiscoverListCategory(
            state = state,
            onChartClick = onChartClick,
            onArtistClick = onArtistClick,
            onSetArtistArea = onSetArtistArea,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            innerPadding = innerPadding,
        )
        return
    }
    val loading = sourceLoading(state)
    val hasMore = sourceHasMore(state)
    val itemCount = sourceItemCount(state)
    val gridState = rememberLazyGridState()
    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(gridState).value) {
    Box(modifier = Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(state.gridColumns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpacing.md,
            end = AppSpacing.md,
            top = innerPadding.calculateTopPadding(),
            bottom = LocalMainBottomContentPadding.current,
        ),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                StatusMessage(state.statusMessage, onRetry)
                when (state.sourceMode) {
                    SourceMode.PLAYLISTS -> FilterRow(
                        values = PlaylistCategory.entries,
                        selected = state.playlistCategory,
                        label = { it.label },
                        onSelect = onSetPlaylistCategory,
                    )
                    SourceMode.ARTISTS -> FilterRow(
                        values = ArtistArea.entries,
                        selected = state.artistArea,
                        label = { it.label },
                        onSelect = onSetArtistArea,
                    )
                    else -> Unit
                }
                Spacer(Modifier.height(AppSpacing.xs))
            }
        }
        when (state.sourceMode) {
            SourceMode.CHARTS -> items(state.chartList, key = { it.id }) { chart ->
                ChartGridCard(chart) { onChartClick(chart) }
            }
            SourceMode.PLAYLISTS -> items(state.discoveryPlaylists, key = { it.id }) { playlist ->
                DiscoveryPlaylistCard(playlist) { onPlaylistClick(playlist) }
            }
            SourceMode.PODCASTS -> items(state.podcastChannels, key = { it.id }) { podcast ->
                PodcastCard(podcast) { onPodcastClick(podcast) }
            }
            SourceMode.ARTISTS -> items(state.artists, key = { it.id }) { artist ->
                ArtistDiscoveryCard(artist) { onArtistClick(artist) }
            }
            SourceMode.NEW_ALBUMS -> items(state.newAlbumsList, key = { it.id }) { album ->
                AlbumGridCard(album) { onAlbumClick(album) }
            }
            else -> Unit
        }
        if (loading && itemCount == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) { LoadingIndicator("正在加载...") }
        } else if (itemCount == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyMessage("暂时没有内容") }
        }
        if (hasMore && itemCount > 0 && state.statusMessage?.startsWith("加载更多失败") != true) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AutoLoadMore(state.isLoadingMore, onLoadMore)
            }
        }
    }
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(gridState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
    }
}
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun NewSongsCategory(
    state: DiscoverUiState,
    downloadProgress: Map<Long, DownloadProgress>,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onSetNewSongsArea: (Int) -> Unit,
    onRetry: () -> Unit,
    innerPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    val areas = listOf(
        0 to "全部",
        7 to "华语",
        96 to "欧美",
        8 to "日本",
        16 to "韩国",
    )
    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpacing.md,
            end = AppSpacing.md,
            top = innerPadding.calculateTopPadding(),
            bottom = LocalMainBottomContentPadding.current,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        item {
            StatusMessage(state.statusMessage, onRetry)
        }
        item {
            FilterRow(
                values = areas,
                selected = areas.first { it.first == state.newSongsArea },
                label = { it.second },
                onSelect = { onSetNewSongsArea(it.first) },
            )
            Spacer(Modifier.height(AppSpacing.xs))
        }
        if (state.newSongs.isNotEmpty()) {
            item { SongControls(state.selectedIds.size, state.newSongs.size, onSelectAll, onDownload, state.isPreparingDownloads) }
            items(state.newSongs, key = { it.id }) { song ->
                SongListItem(song, song.id in state.selectedIds, downloadProgress[song.id]) { onToggleSelect(song.id) }
            }
        } else if (state.isLoadingNewSongs) {
            item { LoadingIndicator("正在加载...") }
        } else {
            item { EmptyMessage("暂时没有新歌") }
        }
    }
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
    }
}
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun StyleCategory(
    state: DiscoverUiState,
    onSelectStyleTag: (StyleTag) -> Unit,
    onRetry: () -> Unit,
    innerPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpacing.md,
            end = AppSpacing.md,
            top = innerPadding.calculateTopPadding(),
            bottom = LocalMainBottomContentPadding.current,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        item {
            StatusMessage(state.statusMessage, onRetry)
        }
        if (state.styleTags.isNotEmpty()) {
            item {
                Text(
                    text = "按曲风浏览歌单",
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    state.styleTags.forEach { tag ->
                        AppChip(tag.name) { onSelectStyleTag(tag) }
                    }
                }
            }
        } else if (state.isLoadingStyleTags) {
            item { LoadingIndicator("正在加载...") }
        } else {
            item { EmptyMessage("暂无曲风标签") }
        }
    }
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
    }
}
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun FmCategory(
    state: DiscoverUiState,
    downloadProgress: Map<Long, DownloadProgress>,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onLoadNextFmGroup: () -> Unit,
    onRetry: () -> Unit,
    innerPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpacing.md,
            end = AppSpacing.md,
            top = innerPadding.calculateTopPadding(),
            bottom = LocalMainBottomContentPadding.current,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        item {
            StatusMessage(state.statusMessage, onRetry)
        }
        if (state.fmSongs.isNotEmpty()) {
            item { SongControls(state.selectedIds.size, state.fmSongs.size, onSelectAll, onDownload, state.isPreparingDownloads) }
            items(state.fmSongs, key = { it.id }) { song ->
                SongListItem(song, song.id in state.selectedIds, downloadProgress[song.id]) { onToggleSelect(song.id) }
            }
            item {
                Button(
                    onClick = onLoadNextFmGroup,
                    enabled = !state.isLoadingFm,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.isLoadingFm) "正在加载" else "换一组") }
            }
        } else if (state.isLoadingFm) {
            item { LoadingIndicator("正在加载...") }
        } else {
            item { EmptyMessage("暂无可用歌曲") }
        }
    }
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
    }
}
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun DiscoverListCategory(
    state: DiscoverUiState,
    onChartClick: (ChartInfo) -> Unit,
    onArtistClick: (ArtistResult) -> Unit,
    onSetArtistArea: (ArtistArea) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    innerPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    val loading = sourceLoading(state)
    val hasMore = sourceHasMore(state)
    val itemCount = sourceItemCount(state)
    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpacing.md,
            end = AppSpacing.md,
            top = innerPadding.calculateTopPadding(),
            bottom = LocalMainBottomContentPadding.current,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        item {
            Column {
                StatusMessage(state.statusMessage, onRetry)
                if (state.sourceMode == SourceMode.ARTISTS) {
                    FilterRow(
                        values = ArtistArea.entries,
                        selected = state.artistArea,
                        label = { it.label },
                        onSelect = onSetArtistArea,
                    )
                }
                Spacer(Modifier.height(AppSpacing.xs))
            }
        }
        when (state.sourceMode) {
            SourceMode.CHARTS -> items(state.chartList, key = { it.id }) { chart ->
                ChartListItem(chart) { onChartClick(chart) }
            }
            SourceMode.ARTISTS -> items(state.artists, key = { it.id }) { artist ->
                ArtistListItem(artist) { onArtistClick(artist) }
            }
            else -> Unit
        }
        if (loading && itemCount == 0) {
            item { LoadingIndicator("正在加载...") }
        } else if (itemCount == 0) {
            item { EmptyMessage("暂时没有内容") }
        }
        if (hasMore && itemCount > 0 && state.statusMessage?.startsWith("加载更多失败") != true) {
            item { AutoLoadMore(state.isLoadingMore, onLoadMore) }
        }
    }
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
    }
}
}

@Composable
private fun ChartListItem(chart: ChartInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(AppShapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
            .directionalEntrance(chart.id),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverDisplayUrl(chart.coverUrl, CoverRequestSize.ROW).takeUnless { it.isBlank() },
            contentDescription = chart.name,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_music_placeholder),
            error = painterResource(R.drawable.ic_music_placeholder),
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = chart.name,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    if (chart.updateFrequency.isNotBlank()) append(chart.updateFrequency)
                    if (chart.playCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${compactCount(chart.playCount)} 次播放")
                    }
                    if (isEmpty()) append("${chart.trackCount} 首歌曲")
                },
                style = MiuixTheme.textStyles.footnote1,
                color = colorScheme.onSurfaceVariantActions,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

@Composable
private fun ArtistListItem(artist: ArtistResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(AppShapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
            .directionalEntrance(artist.id),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverDisplayUrl(artist.avatarUrl, CoverRequestSize.ROW).takeUnless { it.isBlank() },
            contentDescription = artist.name,
            modifier = Modifier.size(56.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_music_placeholder),
            error = painterResource(R.drawable.ic_music_placeholder),
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = artist.alias.ifBlank {
                    if (artist.albumCount > 0) "${artist.albumCount} 张专辑" else "歌手"
                },
                style = MiuixTheme.textStyles.footnote1,
                color = colorScheme.onSurfaceVariantActions,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

@Composable
private fun ArtistDetailPage(
    state: DiscoverUiState,
    songs: List<com.qing.hachimi.data.model.Song>,
    downloadProgress: Map<Long, DownloadProgress>,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onAlbumClick: (AlbumResult) -> Unit,
    onArtistClick: (ArtistResult) -> Unit,
    onSetSection: (ArtistDetailSection) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    innerPadding: PaddingValues,
) {
    when (state.artistDetailSection) {
        ArtistDetailSection.HOT_SONGS -> ArtistHotSongs(
            state = state,
            songs = songs,
            downloadProgress = downloadProgress,
            onToggleSelect = onToggleSelect,
            onSelectAll = onSelectAll,
            onDownload = onDownload,
            onSetSection = onSetSection,
            onRetry = onRetry,
            innerPadding = innerPadding,
        )
        ArtistDetailSection.ALBUMS -> ArtistAlbums(
            state = state,
            onAlbumClick = onAlbumClick,
            onSetSection = onSetSection,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            innerPadding = innerPadding,
        )
        ArtistDetailSection.ABOUT -> ArtistAbout(
            state = state,
            onArtistClick = onArtistClick,
            onSetSection = onSetSection,
            onRetry = onRetry,
            innerPadding = innerPadding,
        )
    }
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun ArtistHotSongs(
    state: DiscoverUiState,
    songs: List<com.qing.hachimi.data.model.Song>,
    downloadProgress: Map<Long, DownloadProgress>,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onSetSection: (ArtistDetailSection) -> Unit,
    onRetry: () -> Unit,
    innerPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    val pageKey = state.activeArtist?.id ?: state.artistProfile?.name.orEmpty()
    var currentPage by remember(pageKey) { mutableIntStateOf(0) }
    var previousPage by remember(pageKey) { mutableIntStateOf(0) }
    val pagedSongs = remember(songs, currentPage) { listPage(songs, currentPage) }
    LaunchedEffect(currentPage) {
        if (currentPage != previousPage) {
            listState.scrollToItem(1)
            previousPage = currentPage
        }
    }
    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = artistDetailPadding(innerPadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        item {
            ArtistDetailLead(state, onSetSection)
            StatusMessage(state.statusMessage, onRetry)
        }
        if (state.isLoading) {
            item { LoadingIndicator("正在加载热门歌曲...") }
        } else if (songs.isEmpty()) {
            item { EmptyMessage("暂无热门歌曲") }
        } else {
            item {
                SongControls(
                    selectedCount = state.selectedIds.size,
                    totalCount = songs.size,
                    onSelectAll = onSelectAll,
                    onDownload = onDownload,
                    isPreparingDownloads = state.isPreparingDownloads,
                    pagination = {
                        ListPageNavigator(
                            currentPage = currentPage,
                            itemCount = songs.size,
                            onPageChange = { currentPage = it },
                            stateKey = pageKey,
                        )
                    },
                )
            }
            items(pagedSongs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isSelected = song.id in state.selectedIds,
                    progress = downloadProgress[song.id],
                    onToggle = { onToggleSelect(song.id) },
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

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun ArtistAlbums(
    state: DiscoverUiState,
    onAlbumClick: (AlbumResult) -> Unit,
    onSetSection: (ArtistDetailSection) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    innerPadding: PaddingValues,
) {
    val gridState = rememberLazyGridState()
    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(gridState).value) {
    Box(modifier = Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = artistDetailPadding(innerPadding),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ArtistDetailLead(state, onSetSection)
                StatusMessage(state.artistSectionMessage, onRetry)
            }
        }
        items(state.artistDetailAlbums, key = { it.id }) { album ->
            AlbumGridCard(album) { onAlbumClick(album) }
        }
        if (state.isLoadingArtistAlbums && state.artistDetailAlbums.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { LoadingIndicator("正在加载专辑...") }
        } else if (state.artistAlbumsLoaded && state.artistDetailAlbums.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyMessage("暂无专辑") }
        }
        if (
            state.artistAlbumsLoaded &&
            state.artistAlbumsHasMore &&
            state.artistSectionMessage?.startsWith("专辑加载失败") != true
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AutoLoadMore(state.isLoadingArtistAlbums, onLoadMore)
            }
        }
    }
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(gridState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
    }
}
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun ArtistAbout(
    state: DiscoverUiState,
    onArtistClick: (ArtistResult) -> Unit,
    onSetSection: (ArtistDetailSection) -> Unit,
    onRetry: () -> Unit,
    innerPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = artistDetailPadding(innerPadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        item {
            ArtistDetailLead(state, onSetSection)
            StatusMessage(state.artistSectionMessage, onRetry)
        }
        if (state.isLoadingArtistAbout) {
            item { LoadingIndicator("正在加载艺人介绍...") }
        } else {
            val brief = state.artistAbout.briefDescription.ifBlank {
                state.artistProfile?.briefDescription.orEmpty()
            }
            if (brief.isNotBlank()) {
                item { ArtistTextSection("艺人介绍", brief) }
            }
            items(state.artistAbout.introductions, key = { "${it.title}:${it.text.hashCode()}" }) { section ->
                ArtistTextSection(section.title, section.text)
            }
            if (state.artistAbout.similarArtists.isNotEmpty()) {
                item {
                    Text(
                        "相似歌手",
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = AppSpacing.sm),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        contentPadding = PaddingValues(end = AppSpacing.xxl),
                    ) {
                        items(state.artistAbout.similarArtists, key = { it.id }) { artist ->
                            Box(Modifier.width(142.dp)) {
                                ArtistDiscoveryCard(artist) { onArtistClick(artist) }
                            }
                        }
                    }
                }
            }
            state.artistAbout.similarArtistsMessage?.let { message ->
                item {
                    Text(
                        message,
                        color = colorScheme.onSurfaceVariantActions,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
            if (
                state.artistAboutLoaded &&
                brief.isBlank() &&
                state.artistAbout.introductions.isEmpty() &&
                state.artistAbout.similarArtists.isEmpty()
            ) {
                item { EmptyMessage("暂无艺人介绍") }
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

@Composable
private fun ArtistDetailLead(
    state: DiscoverUiState,
    onSetSection: (ArtistDetailSection) -> Unit,
) {
    val artist = state.activeArtist ?: return
    val profile = state.artistProfile
    val secondaryNames = buildList {
        addAll(profile?.translatedNames.orEmpty())
        addAll(profile?.aliases.orEmpty())
        if (isEmpty() && artist.alias.isNotBlank()) add(artist.alias)
    }.distinct()
    val counts = buildList {
        profile?.songCount?.takeIf { it > 0 }?.let { add("$it 首歌曲") }
        profile?.albumCount?.takeIf { it > 0 }?.let { add("$it 张专辑") }
    }
    var previewCover by remember { mutableStateOf(false) }
    val coverUrl = profile?.coverUrl.orEmpty().ifBlank { artist.avatarUrl }
    Column(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = coverDisplayUrl(coverUrl, CoverRequestSize.HERO).takeUnless { it.isBlank() },
            contentDescription = profile?.name ?: artist.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .clip(RoundedCornerShape(8.dp))
                .clickable { previewCover = true },
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_music_placeholder),
            error = painterResource(R.drawable.ic_music_placeholder),
        )
        Spacer(Modifier.height(AppSpacing.sm))
        if (secondaryNames.isNotEmpty()) {
            Text(
                secondaryNames.joinToString(" · "),
                color = colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        profile?.identity?.takeIf(String::isNotBlank)?.let { identity ->
            Text(
                identity,
                color = colorScheme.primary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        if (counts.isNotEmpty()) {
            Text(
                counts.joinToString(" · "),
                color = colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        Spacer(Modifier.height(AppSpacing.md))
        TabRow(
            tabs = ArtistDetailSection.entries.map { it.label },
            selectedTabIndex = state.artistDetailSection.ordinal,
            onTabSelected = { index -> onSetSection(ArtistDetailSection.entries[index]) },
        )
        Spacer(Modifier.height(AppSpacing.sm))
        CoverPreviewDialog(
            imageUrl = if (previewCover) coverUrl else null,
            onDismissRequest = { previewCover = false },
        )
    }
}

@Composable
private fun ArtistTextSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(title, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.SemiBold)
        Text(
            body,
            color = colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body1,
        )
    }
}

@Composable
private fun artistDetailPadding(innerPadding: PaddingValues) = PaddingValues(
    start = AppSpacing.md,
    end = AppSpacing.md,
    top = innerPadding.calculateTopPadding(),
    bottom = LocalMainBottomContentPadding.current,
)

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun DiscoverSongDetail(
    state: DiscoverUiState,
    songs: List<com.qing.hachimi.data.model.Song>,
    downloadProgress: Map<Long, DownloadProgress>,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onLoadMoreDetail: () -> Unit,
    onRetry: () -> Unit,
    innerPadding: PaddingValues,
    sortKey: Any? = null,
) {
    val listState = rememberLazyListState()
    val detailKey = remember(state.collectionDetail, state.playlistName) {
        state.collectionDetail?.let { "${it.kind.name}:${it.id}:${it.title}" }
            ?: "${state.sourceMode.name}:${state.playlistName}"
    }
    var currentPage by remember(detailKey, sortKey) { mutableIntStateOf(0) }
    var previousPage by remember(detailKey, sortKey) { mutableIntStateOf(0) }
    val pagedSongs = remember(songs, currentPage) { listPage(songs, currentPage) }
    LaunchedEffect(currentPage) {
        if (currentPage != previousPage) {
            listState.scrollToItem(1)
            previousPage = currentPage
        }
    }
    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpacing.md,
            end = AppSpacing.md,
            top = innerPadding.calculateTopPadding(),
            bottom = LocalMainBottomContentPadding.current,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        item {
            CollectionDetailHeader(
                detail = state.collectionDetail ?: com.qing.hachimi.data.model.CollectionDetail(
                    kind = when (state.sourceLabel) {
                        "榜单" -> com.qing.hachimi.data.model.CollectionKind.CHART
                        "专辑" -> com.qing.hachimi.data.model.CollectionKind.ALBUM
                        "播客" -> com.qing.hachimi.data.model.CollectionKind.PODCAST
                        else -> com.qing.hachimi.data.model.CollectionKind.PLAYLIST
                    },
                    title = state.playlistName.ifBlank { state.sourceLabel },
                ),
                loadedCount = songs.size,
                loading = state.isLoading,
            )
            StatusMessage(state.statusMessage, onRetry)
        }
        if (state.isLoading) {
            item { LoadingIndicator("正在加载...") }
        } else if (songs.isEmpty()) {
            item { EmptyMessage("暂无可下载内容") }
        } else {
            item {
                SongControls(
                    selectedCount = state.selectedIds.size,
                    totalCount = songs.size,
                    onSelectAll = onSelectAll,
                    onDownload = onDownload,
                    isPreparingDownloads = state.isPreparingDownloads,
                    pagination = {
                        ListPageNavigator(
                            currentPage = currentPage,
                            itemCount = songs.size,
                            onPageChange = { currentPage = it },
                            stateKey = detailKey,
                            hasMore = state.detailHasMore,
                            isLoadingMore = state.isLoadingMoreDetail,
                            onLoadMore = onLoadMoreDetail,
                        )
                    },
                )
            }
            items(pagedSongs, key = { it.id }) { song ->
                SongListItem(song, song.id in state.selectedIds, downloadProgress[song.id]) { onToggleSelect(song.id) }
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

@Composable
private fun SectionHeading(title: String, onMore: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.SemiBold)
        Text(
            "查看全部",
            modifier = Modifier.clickable(onClick = onMore).padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            color = colorScheme.primary,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
private fun StatusMessage(message: String?, onRetry: () -> Unit) {
    if (message.isNullOrBlank()) return
    val isError = message.contains("失败") || message.contains("超时") || message.contains("异常")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            modifier = Modifier.weight(1f),
            color = if (isError) colorScheme.error else colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2,
        )
        if (isError) {
            Text(
                "重试",
                modifier = Modifier.clickable(onClick = onRetry).padding(AppSpacing.sm),
                color = colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun <T> FilterRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(end = AppSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        items(values) { value ->
            AppChip(label(value), selected == value) { onSelect(value) }
        }
    }
}

@Composable
internal fun DiscoverCategoryActions(
    sourceMode: SourceMode,
    columns: Int,
    onRefresh: () -> Unit,
    onSetColumns: (Int) -> Unit,
) {
    IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
        Icon(MiuixIcons.Refresh, "刷新")
    }
    if (
        sourceMode == SourceMode.PLAYLISTS ||
        sourceMode == SourceMode.PODCASTS ||
        sourceMode == SourceMode.NEW_ALBUMS
    ) {
        GridColsMenu(columns, onSetColumns)
    }
}

@Composable
private fun AutoLoadMore(loading: Boolean, onLoadMore: () -> Unit) {
    LaunchedEffect(loading) {
        if (!loading) onLoadMore()
    }
    LoadingIndicator(if (loading) "正在加载更多..." else "继续加载")
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(text, color = colorScheme.onSurfaceVariantActions)
    }
}

@Composable
private fun DiscoveryPlaylistCard(
    playlist: DiscoveryPlaylist,
    onClick: () -> Unit,
) {
    DiscoveryCoverCard(
        title = playlist.name,
        subtitle = playlist.creator.ifBlank {
            when {
                playlist.trackCount > 0 -> "${playlist.trackCount} 首"
                playlist.playCount > 0 -> "${compactCount(playlist.playCount)} 次播放"
                else -> "歌单"
            }
        },
        coverUrl = playlist.coverUrl,
        entranceKey = playlist.id,
        onClick = onClick,
    )
}

@Composable
private fun PodcastCard(
    podcast: PodcastChannel,
    onClick: () -> Unit,
) {
    DiscoveryCoverCard(
        title = podcast.name,
        subtitle = podcast.host.ifBlank { podcast.category.ifBlank { "${podcast.programCount} 期" } },
        coverUrl = podcast.coverUrl,
        entranceKey = podcast.id,
        onClick = onClick,
    )
}

@Composable
private fun ArtistDiscoveryCard(
    artist: ArtistResult,
    onClick: () -> Unit,
) {
    DiscoveryCoverCard(
        title = artist.name,
        subtitle = artist.alias.ifBlank {
            if (artist.albumCount > 0) "${artist.albumCount} 张专辑" else "歌手"
        },
        coverUrl = artist.avatarUrl,
        entranceKey = artist.id,
        onClick = onClick,
    )
}

@Composable
private fun DiscoveryCoverCard(
    title: String,
    subtitle: String,
    coverUrl: String,
    entranceKey: Any,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).directionalEntrance(entranceKey),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant),
    ) {
        Column {
            AsyncImage(
                model = coverDisplayUrl(coverUrl, CoverRequestSize.GRID).takeUnless { it.isBlank() },
                contentDescription = title,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_music_placeholder),
                error = painterResource(R.drawable.ic_music_placeholder),
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    title,
                    fontWeight = FontWeight.Medium,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GridColsMenu(columns: Int, onSetColumns: (Int) -> Unit) {
    var show by remember { mutableStateOf(false) }
    val options = listOf(1, 2, 3, 4)
    Box {
        IconButton(onClick = { show = true }, modifier = Modifier.size(40.dp)) {
            Icon(MiuixIcons.Tune, "网格列数")
        }
        WindowListPopup(show = show, onDismissRequest = { show = false }, minWidth = 140.dp) {
            ListPopupColumn {
                options.forEachIndexed { index, value ->
                    DropdownImpl(
                        text = "$value 列",
                        optionSize = options.size,
                        isSelected = columns == value,
                        index = index,
                        onSelectedIndexChange = {
                            onSetColumns(options[it])
                            show = false
                        },
                    )
                }
            }
        }
    }
}

private fun sourceLoading(state: DiscoverUiState): Boolean = when (state.sourceMode) {
    SourceMode.CHARTS -> state.isLoadingCharts
    SourceMode.PLAYLISTS -> state.isLoadingPlaylists
    SourceMode.PODCASTS -> state.isLoadingPodcasts
    SourceMode.ARTISTS -> state.isLoadingArtists
    SourceMode.NEW_ALBUMS -> state.isLoadingAlbums
    SourceMode.NEW_SONGS -> state.isLoadingNewSongs
    else -> false
}

private fun sourceHasMore(state: DiscoverUiState): Boolean = when (state.sourceMode) {
    SourceMode.PLAYLISTS -> state.playlistsHasMore
    SourceMode.PODCASTS -> state.podcastsHasMore
    SourceMode.ARTISTS -> state.artistsHasMore
    SourceMode.NEW_ALBUMS -> state.newAlbumsHasMore
    else -> false
}

private fun sourceItemCount(state: DiscoverUiState): Int = when (state.sourceMode) {
    SourceMode.CHARTS -> state.chartList.size
    SourceMode.PLAYLISTS -> state.discoveryPlaylists.size
    SourceMode.PODCASTS -> state.podcastChannels.size
    SourceMode.ARTISTS -> state.artists.size
    SourceMode.NEW_ALBUMS -> state.newAlbumsList.size
    SourceMode.NEW_SONGS -> state.newSongs.size
    else -> 0
}

private fun compactCount(value: Long): String = when {
    value >= 100_000_000 -> "${value / 100_000_000}亿"
    value >= 10_000 -> "${value / 10_000}万"
    else -> value.toString()
}
