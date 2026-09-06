package com.qing.hachimi.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.qing.hachimi.R
import com.qing.hachimi.data.api.CloudApi
import com.qing.hachimi.data.api.PlaylistApi
import com.qing.hachimi.data.repository.NeteaseRepository.Companion.coverDisplayUrl
import com.qing.hachimi.ui.animation.LocalScrollDirection
import com.qing.hachimi.ui.animation.directionalEntrance
import com.qing.hachimi.ui.animation.rememberScrollDirection
import com.qing.hachimi.ui.theme.AppShapes
import com.qing.hachimi.ui.theme.AppSpacing
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import com.qing.hachimi.data.api.ListenDataApi
import com.qing.hachimi.data.model.AlbumResult
import com.qing.hachimi.data.model.ArtistResult

private enum class MyLevel { HOME, LIST, SONGS }

private data class MyEntry(
    val label: String,
    val icon: ImageVector,
    val section: MySection,
)

private val myEntries = listOf(
    MyEntry("红心歌曲", MiuixIcons.Favorites, MySection.LIKED),
    MyEntry("我的歌单", MiuixIcons.Playlist, MySection.PLAYLISTS),
    MyEntry("收藏歌单", MiuixIcons.FavoritesFill, MySection.COLLECTED),
    MyEntry("云盘歌曲", MiuixIcons.CloudFill, MySection.CLOUD),
    MyEntry("最近播放", MiuixIcons.Recent, MySection.RECENT),
    MyEntry("听歌排行", MiuixIcons.TopDownloads, MySection.RANK),
    MyEntry("听歌足迹", MiuixIcons.Stopwatch, MySection.FOOTPRINT),
    MyEntry("收藏专辑", MiuixIcons.Album, MySection.COLLECTED_ALBUMS),
    MyEntry("收藏艺人", MiuixIcons.Music, MySection.COLLECTED_ARTISTS),
)

@Composable
fun MyTab(
    state: MyUiState,
    onOpenSection: (MySection) -> Unit,
    onBack: () -> Unit,
    onClearDetail: () -> Unit,
    onComingSoon: () -> Unit,
    onPlaylistClick: (PlaylistApi.UserPlaylist) -> Unit,
    onToggleSong: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onLoadMoreCloud: () -> Unit,
    onCloudSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSetRankType: (Int) -> Unit,
    onSetFootprintTab: (FootprintTab) -> Unit,
    onLoadMoreAlbums: () -> Unit,
    onLoadMoreArtists: () -> Unit,
    onAlbumClick: (AlbumResult) -> Unit,
    onArtistClick: (ArtistResult) -> Unit,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    isLoggedIn: Boolean = false,
    scrollToTopTrigger: Int = 0,
) {
    val horizontalDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val level = when {
        state.isPlaylistDetail -> MyLevel.SONGS
        state.section == MySection.HOME -> MyLevel.HOME
        else -> MyLevel.LIST
    }

    if (!isLoggedIn) {
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text("请先登录后使用", color = colorScheme.onSurfaceVariantActions)
        }
        return
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
        label = "MyLevel"
    ) { target ->
        when (target) {
            MyLevel.HOME -> MyHome(
                state = state,
                onOpenSection = onOpenSection,
                onPlaylistClick = onPlaylistClick,
                onRefresh = onRefresh,
                innerPadding = innerPadding,
                scrollToTopTrigger = scrollToTopTrigger,
            )
            MyLevel.LIST -> MyList(
                state = state,
                onOpenSection = onOpenSection,
                onBack = onBack,
                onPlaylistClick = onPlaylistClick,
                onToggleSong = onToggleSong,
                onSelectAll = onSelectAll,
                onDownload = onDownload,
                onLoadMoreCloud = onLoadMoreCloud,
                onCloudSearchQueryChange = onCloudSearchQueryChange,
                onRefresh = onRefresh,
                onSetRankType = onSetRankType,
                onSetFootprintTab = onSetFootprintTab,
                onLoadMoreAlbums = onLoadMoreAlbums,
                onLoadMoreArtists = onLoadMoreArtists,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                innerPadding = innerPadding,
            )
            MyLevel.SONGS -> PlaylistDetailPage(
                state = state,
                onBack = onClearDetail,
                onToggleSong = onToggleSong,
                onSelectAll = onSelectAll,
                onDownload = onDownload,
                innerPadding = innerPadding,
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// My Home
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun MyHome(
    state: MyUiState,
    onOpenSection: (MySection) -> Unit,
    onPlaylistClick: (PlaylistApi.UserPlaylist) -> Unit,
    onRefresh: () -> Unit,
    innerPadding: PaddingValues,
    scrollToTopTrigger: Int,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
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
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                ScreenHeading("我的", onRefresh)
                StatusMessage(state.statusMessage)
            }
        }
        item {
            UserProfileCard(
                profile = state.profile,
                playlistCount = state.myPlaylists.size,
                collectedCount = state.collectedPlaylists.size,
                cloudCount = state.cloudSongs.size,
                cloudHasMore = state.cloudHasMore,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                SectionHeading("快捷入口")
                EntryGrid(
                    onEntryClick = { entry -> onOpenSection(entry.section) },
                )
            }
        }
        if (state.myPlaylists.isNotEmpty()) {
            item {
                HorizontalSection(
                    title = "我的歌单",
                    items = state.myPlaylists.take(8),
                    onMore = { onOpenSection(MySection.PLAYLISTS) },
                    key = { it.id },
                ) { playlist ->
                    PlaylistCoverCard(playlist) { onPlaylistClick(playlist) }
                }
            }
        }
        if (state.collectedPlaylists.isNotEmpty()) {
            item {
                HorizontalSection(
                    title = "收藏歌单",
                    items = state.collectedPlaylists.take(8),
                    onMore = { onOpenSection(MySection.COLLECTED) },
                    key = { it.id },
                ) { playlist ->
                    PlaylistCoverCard(playlist) { onPlaylistClick(playlist) }
                }
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
private fun UserProfileCard(
    profile: UserProfile?,
    playlistCount: Int,
    collectedCount: Int,
    cloudCount: Int,
    cloudHasMore: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                val avatarUrl = profile?.avatarUrl.orEmpty()
                if (avatarUrl.isBlank()) {
                    Icon(
                        imageVector = MiuixIcons.Contacts,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = colorScheme.onSurfaceVariantActions,
                    )
                } else {
                    AsyncImage(
                        model = coverDisplayUrl(avatarUrl, CoverRequestSize.ROW).takeUnless { it.isBlank() },
                        contentDescription = profile?.nickname,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.ic_music_placeholder),
                        error = painterResource(R.drawable.ic_music_placeholder),
                    )
                }
            }
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile?.nickname?.takeIf { it.isNotBlank() } ?: "加载中...",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        if (playlistCount > 0) append("$playlistCount 个歌单")
                        if (collectedCount > 0) append(if (isNotEmpty()) " · " else "").append("$collectedCount 个收藏")
                        if (cloudCount > 0) append(if (isNotEmpty()) " · " else "").append("$cloudCount${if (cloudHasMore) "+" else ""} 首云盘")
                        if (isEmpty()) append("正在同步数据...")
                    },
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
private fun EntryGrid(
    onEntryClick: (MyEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        myEntries.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                row.forEach { entry ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .clickable { onEntryClick(entry) },
                        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(AppSpacing.sm),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                entry.icon,
                                null,
                                Modifier.size(23.dp),
                                tint = colorScheme.primary,
                            )
                            Spacer(Modifier.height(AppSpacing.xs))
                            Text(
                                entry.label,
                                style = MiuixTheme.textStyles.body2,
                                color = colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> HorizontalSection(
    title: String,
    items: List<T>,
    onMore: () -> Unit,
    key: (T) -> Any,
    content: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        SectionHeading(title, onMore)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            items(items, key = key) { item ->
                Box(Modifier.width(142.dp)) { content(item) }
            }
        }
    }
}

@Composable
private fun PlaylistCoverCard(playlist: PlaylistApi.UserPlaylist, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).directionalEntrance(playlist.id),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant),
    ) {
        Column {
            AsyncImage(
                model = coverDisplayUrl(playlist.coverUrl, CoverRequestSize.GRID).takeUnless { it.isBlank() },
                contentDescription = playlist.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_music_placeholder),
                error = painterResource(R.drawable.ic_music_placeholder),
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    playlist.name,
                    fontWeight = FontWeight.Medium,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${playlist.trackCount} 首",
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// My List (二级页: 歌单列表 / 收藏歌单 / 云盘 / 红心)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun MyList(
    state: MyUiState,
    onOpenSection: (MySection) -> Unit,
    onBack: () -> Unit,
    onPlaylistClick: (PlaylistApi.UserPlaylist) -> Unit,
    onToggleSong: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onLoadMoreCloud: () -> Unit,
    onCloudSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSetRankType: (Int) -> Unit,
    onSetFootprintTab: (FootprintTab) -> Unit,
    onLoadMoreAlbums: () -> Unit,
    onLoadMoreArtists: () -> Unit,
    onAlbumClick: (AlbumResult) -> Unit,
    onArtistClick: (ArtistResult) -> Unit,
    innerPadding: PaddingValues,
) {
    val sectionListStates = remember { mutableMapOf<MySection, LazyListState>() }
    Column(Modifier.fillMaxSize()) {
        ListTopBar(
            title = state.section.label,
            onBack = onBack,
            onRefresh = onRefresh,
            paddingTop = innerPadding.calculateTopPadding(),
        )
        StatusMessage(state.statusMessage)
        AnimatedContent(
            targetState = state.section,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                peerContentTransition()
            },
            label = "MySectionTransition",
        ) { section ->
            val listState = sectionListStates.getOrPut(section) { LazyListState() }
            when (section) {
            MySection.PLAYLISTS -> {
                val songs = state.myPlaylists
                if (songs.isEmpty() && state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator("加载中...")
                    }
                } else if (songs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无歌单", color = colorScheme.onSurfaceVariantActions)
                    }
                } else {
                    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = AppSpacing.md,
                            end = AppSpacing.md,
                            bottom = LocalMainBottomContentPadding.current,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(songs, key = { it.id }) { playlist ->
                            PlaylistListItem(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                        }
                    }
                    }
                }
            }
            MySection.COLLECTED -> {
                val songs = state.collectedPlaylists
                if (songs.isEmpty() && state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator("加载中...")
                    }
                } else if (songs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无收藏歌单", color = colorScheme.onSurfaceVariantActions)
                    }
                } else {
                    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = AppSpacing.md,
                            end = AppSpacing.md,
                            bottom = LocalMainBottomContentPadding.current,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(songs, key = { it.id }) { playlist ->
                            PlaylistListItem(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                        }
                    }
                    }
                }
            }
            MySection.LIKED -> LikedSongsList(
                state = state,
                listState = listState,
                onToggleSong = onToggleSong,
                onSelectAll = onSelectAll,
                onDownload = onDownload,
            )
            MySection.CLOUD -> CloudSongsList(
                state = state,
                listState = listState,
                onToggleSong = onToggleSong,
                onSelectAll = onSelectAll,
                onDownload = onDownload,
                onLoadMoreCloud = onLoadMoreCloud,
                onCloudSearchQueryChange = onCloudSearchQueryChange,
            )
            MySection.RANK -> RankList(
                state = state,
                listState = listState,
                onSetRankType = onSetRankType,
                onToggleSong = onToggleSong,
                onSelectAll = onSelectAll,
                onDownload = onDownload,
            )
            MySection.RECENT -> RecentList(
                state = state,
                listState = listState,
                onToggleSong = onToggleSong,
                onSelectAll = onSelectAll,
                onDownload = onDownload,
            )
            MySection.FOOTPRINT -> FootprintList(
                state = state,
                listState = listState,
                onSetFootprintTab = onSetFootprintTab,
                onToggleSong = onToggleSong,
                onSelectAll = onSelectAll,
                onDownload = onDownload,
            )
            MySection.COLLECTED_ALBUMS -> CollectedAlbumsList(
                state = state,
                listState = listState,
                onAlbumClick = onAlbumClick,
                onLoadMore = onLoadMoreAlbums,
            )
            MySection.COLLECTED_ARTISTS -> CollectedArtistsList(
                state = state,
                listState = listState,
                onArtistClick = onArtistClick,
                onLoadMore = onLoadMoreArtists,
            )
            MySection.HOME -> Unit
        }
        }
    }
}

@Composable
private fun LikedSongsList(
    state: MyUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onToggleSong: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
) {
    var currentPage by remember(state.likedSongs.size) { mutableIntStateOf(0) }
    var previousPage by remember(state.likedSongs.size) { mutableIntStateOf(0) }
    val pagedSongs = remember(state.likedSongs, currentPage) { listPage(state.likedSongs, currentPage) }
    LaunchedEffect(currentPage) {
        if (currentPage != previousPage) {
            listState.scrollToItem(0)
            previousPage = currentPage
        }
    }
    when {
        state.isLoading && state.likedSongs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator("加载中...")
        }
        state.likedSongs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无红心歌曲", color = colorScheme.onSurfaceVariantActions)
        }
        else -> {
            Column(Modifier.fillMaxSize()) {
                SongControls(
                    selectedCount = state.selectedIds.size,
                    totalCount = state.likedSongs.size,
                    onSelectAll = onSelectAll,
                    onDownload = onDownload,
                    isPreparingDownloads = state.isPreparingDownloads,
                    pagination = {
                        ListPageNavigator(
                            currentPage = currentPage,
                            itemCount = state.likedSongs.size,
                            onPageChange = { currentPage = it },
                            stateKey = "liked",
                        )
                    },
                )
                CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = AppSpacing.md,
                        end = AppSpacing.md,
                        bottom = LocalMainBottomContentPadding.current,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(pagedSongs, key = { it.id }) { song ->
                        SongListItem(
                            song = song,
                            isSelected = song.id in state.selectedIds,
                            progress = null,
                            onToggle = { onToggleSong(song.id) },
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun CloudSongsList(
    state: MyUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onToggleSong: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onLoadMoreCloud: () -> Unit,
    onCloudSearchQueryChange: (String) -> Unit,
) {
    // 过滤云盘歌曲
    val filteredCloudSongs = remember(state.cloudSongs, state.cloudSearchQuery) {
        if (state.cloudSearchQuery.isBlank()) {
            state.cloudSongs
        } else {
            state.cloudSongs.filter { song ->
                song.name.contains(state.cloudSearchQuery, ignoreCase = true) ||
                    song.artists.contains(state.cloudSearchQuery, ignoreCase = true) ||
                    song.album.contains(state.cloudSearchQuery, ignoreCase = true)
            }
        }
    }
    var cloudPage by remember(state.cloudSearchQuery) { mutableIntStateOf(0) }
    var previousCloudPage by remember(state.cloudSearchQuery) { mutableIntStateOf(0) }
    val pagedCloudSongs = remember(filteredCloudSongs, cloudPage) {
        listPage(filteredCloudSongs, cloudPage)
    }
    LaunchedEffect(cloudPage) {
        if (cloudPage != previousCloudPage) {
            listState.scrollToItem(0)
            previousCloudPage = cloudPage
        }
    }

    if (state.cloudSongs.isEmpty() && !state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无云盘歌曲", color = colorScheme.onSurfaceVariantActions)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = state.cloudSearchQuery,
            onValueChange = onCloudSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            label = "搜索云盘歌曲",
            trailingIcon = {
                if (state.cloudSearchQuery.isNotEmpty()) {
                    IconButton(
                        modifier = Modifier.padding(end = 8.dp),
                        onClick = { onCloudSearchQueryChange("") }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Clear,
                            contentDescription = "清除"
                        )
                    }
                }
            }
        )

        SongControls(
            selectedCount = state.selectedIds.size,
            totalCount = filteredCloudSongs.size,
            onSelectAll = onSelectAll,
            onDownload = onDownload,
            isPreparingDownloads = state.isPreparingDownloads,
            pagination = {
                ListPageNavigator(
                    currentPage = cloudPage,
                    itemCount = filteredCloudSongs.size,
                    onPageChange = { cloudPage = it },
                    stateKey = state.cloudSearchQuery,
                    hasMore = state.cloudHasMore,
                    isLoadingMore = state.isLoadingMore,
                    onLoadMore = onLoadMoreCloud,
                )
            },
        )
        CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = AppSpacing.md,
                end = AppSpacing.md,
                bottom = LocalMainBottomContentPadding.current,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(pagedCloudSongs, key = { it.id }) { cloudSong ->
                CloudSongCard(
                    cloudSong = cloudSong,
                    isSelected = cloudSong.id in state.selectedIds,
                    onToggle = { onToggleSong(cloudSong.id) },
                )
            }
        }
        }
    }
}

@Composable
private fun CloudSongCard(
    cloudSong: CloudApi.CloudSong,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().directionalEntrance(cloudSong.id),
        colors = CardDefaults.defaultColors(
            color = if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.3f)
            else colorScheme.surfaceVariant
        ),
        onClick = onToggle,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                state = if (isSelected) androidx.compose.ui.state.ToggleableState.On
                else androidx.compose.ui.state.ToggleableState.Off,
                onClick = onToggle,
            )
            Spacer(Modifier.width(8.dp))
            AsyncImage(
                model = coverDisplayUrl(cloudSong.coverUrl, CoverRequestSize.ROW).takeUnless { it.isBlank() },
                contentDescription = cloudSong.name,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_music_placeholder),
                error = painterResource(R.drawable.ic_music_placeholder),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(cloudSong.name, style = MiuixTheme.textStyles.body1, maxLines = 1)
                Text(
                    "${cloudSong.artists} · ${cloudSong.album}",
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Rank / Recent / Footprint 歌曲列表（带播放次数）
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun RecordSongsList(
    entries: List<ListenDataApi.RecordEntry>,
    isLoading: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectedIds: Set<Long>,
    isPreparingDownloads: Boolean,
    emptyText: String,
    stateKey: String,
    onToggleSong: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    tabBar: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    var page by remember(entries.size, stateKey) { mutableIntStateOf(0) }
    var previousPage by remember(entries.size, stateKey) { mutableIntStateOf(0) }
    val pagedEntries = remember(entries, page) { listPage(entries, page) }
    LaunchedEffect(page) {
        if (page != previousPage) {
            listState.scrollToItem(0)
            previousPage = page
        }
    }
    when {
        isLoading && entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator("加载中...")
        }
        entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = colorScheme.onSurfaceVariantActions)
        }
        else -> {
            Column(Modifier.fillMaxSize()) {
                tabBar?.invoke()
                header?.invoke()
                SongControls(
                    selectedCount = selectedIds.size,
                    totalCount = entries.size,
                    onSelectAll = onSelectAll,
                    onDownload = onDownload,
                    isPreparingDownloads = isPreparingDownloads,
                    pagination = {
                        ListPageNavigator(
                            currentPage = page,
                            itemCount = entries.size,
                            onPageChange = { page = it },
                            stateKey = stateKey,
                        )
                    },
                )
                CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = AppSpacing.md,
                        end = AppSpacing.md,
                        bottom = LocalMainBottomContentPadding.current,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(pagedEntries, key = { it.song.id }) { entry ->
                        RecordSongItem(
                            entry = entry,
                            isSelected = entry.song.id in selectedIds,
                            onToggle = { onToggleSong(entry.song.id) },
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun RecordSongItem(
    entry: ListenDataApi.RecordEntry,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val song = entry.song
    Card(
        modifier = Modifier.fillMaxWidth().directionalEntrance(song.id),
        colors = CardDefaults.defaultColors(
            color = if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.3f)
            else colorScheme.surfaceVariant
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                state = if (isSelected) androidx.compose.ui.state.ToggleableState.On
                else androidx.compose.ui.state.ToggleableState.Off,
                onClick = onToggle,
            )
            Spacer(Modifier.width(8.dp))
            AsyncImage(
                model = coverDisplayUrl(song.coverUrl, CoverRequestSize.ROW).takeUnless { it.isBlank() },
                contentDescription = song.name,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_music_placeholder),
                error = painterResource(R.drawable.ic_music_placeholder),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.name, style = MiuixTheme.textStyles.body1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${song.artists} · ${song.album}",
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.playCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    formatPlayCount(entry.playCount),
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }
    }
}

private fun formatPlayCount(count: Long): String = when {
    count >= 100_000_000 -> "%.1f亿".format(count / 100_000_000.0)
    count >= 10_000 -> "%.1f万".format(count / 10_000.0)
    else -> "$count"
}

@Composable
private fun RankList(
    state: MyUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSetRankType: (Int) -> Unit,
    onToggleSong: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
) {
    RecordSongsList(
        entries = state.rankEntries,
        isLoading = state.isLoading,
        listState = listState,
        selectedIds = state.selectedIds,
        isPreparingDownloads = state.isPreparingDownloads,
        emptyText = "暂无听歌排行数据",
        stateKey = "rank-${state.rankType}",
        onToggleSong = onToggleSong,
        onSelectAll = onSelectAll,
        onDownload = onDownload,
        tabBar = {
            UnderlineTabsBar(
                tabs = listOf("最近一周", "所有时间"),
                selectedIndex = if (state.rankType == 1) 0 else 1,
                onSelect = { onSetRankType(if (it == 0) 1 else 0) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        },
    )
}

@Composable
private fun RecentList(
    state: MyUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onToggleSong: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
) {
    RecordSongsList(
        entries = state.recentListens,
        isLoading = state.isLoading,
        listState = listState,
        selectedIds = state.selectedIds,
        isPreparingDownloads = state.isPreparingDownloads,
        emptyText = "暂无最近播放记录",
        stateKey = "recent",
        onToggleSong = onToggleSong,
        onSelectAll = onSelectAll,
        onDownload = onDownload,
    )
}

@Composable
private fun FootprintList(
    state: MyUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSetFootprintTab: (FootprintTab) -> Unit,
    onToggleSong: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
) {
    val entries = when (state.footprintTab) {
        FootprintTab.TODAY -> state.footprintToday
        FootprintTab.WEEK -> state.footprintWeek
        FootprintTab.MONTH -> state.footprintMonth
        FootprintTab.YEAR -> state.yearReport?.topSongs.orEmpty()
    }
    RecordSongsList(
        entries = entries,
        isLoading = state.isLoading,
        listState = listState,
        selectedIds = state.selectedIds,
        isPreparingDownloads = state.isPreparingDownloads,
        emptyText = "暂无听歌足迹数据",
        stateKey = "footprint-${state.footprintTab.name}",
        onToggleSong = onToggleSong,
        onSelectAll = onSelectAll,
        onDownload = onDownload,
        tabBar = {
            UnderlineTabsBar(
                tabs = FootprintTab.entries.map { it.label },
                selectedIndex = state.footprintTab.ordinal,
                onSelect = { onSetFootprintTab(FootprintTab.entries[it]) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        },
        header = {
            if (state.footprintTab == FootprintTab.YEAR) {
                state.yearReport?.let { YearReportCard(it) }
            }
        },
    )
}

@Composable
private fun YearReportCard(report: ListenDataApi.YearReport) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
            Text(
                text = "${if (report.year > 0) "${report.year} 年" else "年度"}听歌足迹",
                fontWeight = FontWeight.SemiBold,
                style = MiuixTheme.textStyles.body1,
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                YearStat("${formatPlayCount(report.songCount)}", "首歌曲")
                if (report.listenMinutes > 0) YearStat(formatDuration(report.listenMinutes), "听歌时长")
                if (report.artistCount > 0) YearStat(formatPlayCount(report.artistCount), "位歌手")
                if (report.albumCount > 0) YearStat(formatPlayCount(report.albumCount), "张专辑")
            }
        }
    }
}

@Composable
private fun YearStat(value: String, label: String) {
    Column {
        Text(value, fontWeight = FontWeight.SemiBold, style = MiuixTheme.textStyles.body1)
        Text(label, color = colorScheme.onSurfaceVariantActions, style = MiuixTheme.textStyles.footnote1)
    }
}

private fun formatDuration(minutes: Long): String = when {
    minutes >= 60 -> "${minutes / 60}小时${minutes % 60}分"
    else -> "$minutes 分"
}

// ═════════════════════════════════════════════════════════════════════════════
// 收藏中心（专辑 / 艺人）
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun CollectedAlbumsList(
    state: MyUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onAlbumClick: (AlbumResult) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (state.collectedAlbums.isEmpty() && state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator("加载中...")
        }
        return
    }
    if (state.collectedAlbums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无收藏专辑", color = colorScheme.onSurfaceVariantActions)
        }
        return
    }
    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpacing.md,
            end = AppSpacing.md,
            bottom = LocalMainBottomContentPadding.current,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(state.collectedAlbums, key = { it.id }) { album ->
            CollectionAlbumItem(album = album, onClick = { onAlbumClick(album) })
        }
        if (state.collectedAlbumsHasMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.isLoadingMore) "加载中..." else "加载更多",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !state.isLoadingMore, onClick = onLoadMore)
                            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                        color = colorScheme.primary,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun CollectionAlbumItem(album: AlbumResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).directionalEntrance(album.id),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = coverDisplayUrl(album.coverUrl, CoverRequestSize.ROW).takeUnless { it.isBlank() },
                contentDescription = album.name,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_music_placeholder),
                error = painterResource(R.drawable.ic_music_placeholder),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(album.name, style = MiuixTheme.textStyles.body1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    album.artist.ifBlank { "未知歌手" },
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CollectedArtistsList(
    state: MyUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onArtistClick: (ArtistResult) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (state.collectedArtists.isEmpty() && state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator("加载中...")
        }
        return
    }
    if (state.collectedArtists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无关注的歌手", color = colorScheme.onSurfaceVariantActions)
        }
        return
    }
    CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpacing.md,
            end = AppSpacing.md,
            bottom = LocalMainBottomContentPadding.current,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(state.collectedArtists, key = { it.id }) { artist ->
            CollectionArtistItem(artist = artist, onClick = { onArtistClick(artist) })
        }
        if (state.collectedArtistsHasMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.isLoadingMore) "加载中..." else "加载更多",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !state.isLoadingMore, onClick = onLoadMore)
                            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                        color = colorScheme.primary,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun CollectionArtistItem(artist: ArtistResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).directionalEntrance(artist.id),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = coverDisplayUrl(artist.avatarUrl, CoverRequestSize.ROW).takeUnless { it.isBlank() },
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_music_placeholder),
                    error = painterResource(R.drawable.ic_music_placeholder),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(artist.name, style = MiuixTheme.textStyles.body1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (artist.alias.isNotBlank()) {
                    Text(
                        artist.alias,
                        color = colorScheme.onSurfaceVariantActions,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Playlist Detail (三级页: 歌单内歌曲)
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun PlaylistDetailPage(
    state: MyUiState,
    onBack: () -> Unit,
    onToggleSong: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    innerPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    var playlistPage by remember(state.playlistName) { mutableIntStateOf(0) }
    var previousPlaylistPage by remember(state.playlistName) { mutableIntStateOf(0) }
    val pagedPlaylistSongs = remember(state.songs, playlistPage) { listPage(state.songs, playlistPage) }
    LaunchedEffect(playlistPage) {
        if (playlistPage != previousPlaylistPage) {
            listState.scrollToItem(0)
            previousPlaylistPage = playlistPage
        }
    }
    Column(Modifier.fillMaxSize()) {
        ListTopBar(
            title = state.playlistName.ifBlank { "歌单" },
            onBack = onBack,
            onRefresh = { },
            paddingTop = innerPadding.calculateTopPadding(),
            showRefresh = false,
        )
        StatusMessage(state.statusMessage)
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator("加载中...")
            }
        } else if (state.songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无歌曲", color = colorScheme.onSurfaceVariantActions)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                SongControls(
                    selectedCount = state.selectedIds.size,
                    totalCount = state.songs.size,
                    onSelectAll = onSelectAll,
                    onDownload = onDownload,
                    isPreparingDownloads = state.isPreparingDownloads,
                    pagination = {
                        ListPageNavigator(
                            currentPage = playlistPage,
                            itemCount = state.songs.size,
                            onPageChange = { playlistPage = it },
                            stateKey = state.playlistName,
                        )
                    },
                )
                CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppSpacing.md,
                        end = AppSpacing.md,
                        bottom = LocalMainBottomContentPadding.current,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(pagedPlaylistSongs, key = { it.id }) { song ->
                        SongListItem(
                            song = song,
                            isSelected = song.id in state.selectedIds,
                            progress = null,
                            onToggle = { onToggleSong(song.id) },
                        )
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
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Shared small components
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeading(title: String, onMore: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.SemiBold)
        if (onMore != null) {
            Text(
                "查看全部",
                modifier = Modifier.clickable(onClick = onMore).padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                color = colorScheme.primary,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}

@Composable
private fun ListTopBar(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    paddingTop: Dp,
    showRefresh: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = paddingTop, end = AppSpacing.sm)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(MiuixIcons.ChevronBackward, "返回")
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showRefresh) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                Icon(MiuixIcons.Refresh, "刷新", tint = colorScheme.onSurfaceVariantActions)
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String?) {
    if (message.isNullOrBlank()) return
    val isError = message.contains("失败") || message.contains("超时") || message.contains("异常")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            modifier = Modifier.weight(1f),
            color = if (isError) colorScheme.error else colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2,
        )
    }
}
