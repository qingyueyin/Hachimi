package com.qing.hachimi.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.qing.hachimi.R
import com.qing.hachimi.data.model.*
import com.qing.hachimi.data.repository.NeteaseRepository.Companion.coverDisplayUrl
import com.qing.hachimi.downloader.DownloadProgress
import com.qing.hachimi.ui.animation.LocalScrollDirection
import com.qing.hachimi.ui.animation.directionalEntrance
import com.qing.hachimi.ui.animation.rememberScrollDirection
import com.qing.hachimi.ui.theme.AppShapes
import com.qing.hachimi.ui.theme.AppSpacing
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * Standalone Search tab that uses SearchViewModel state.
 * No longer shares state with DiscoverTab.
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SearchTab(
    state: SearchUiState,
    downloadProgress: Map<Long, DownloadProgress>,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onParseUrl: (String) -> Unit = {},
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onSetSearchCategory: (SearchCategory) -> Unit,
    onLoadMore: () -> Unit,
    onAlbumClick: (AlbumResult) -> Unit,
    onArtistClick: (ArtistResult) -> Unit,
    onPlaylistClick: (DiscoveryPlaylist) -> Unit,
    onPodcastClick: (PodcastChannel) -> Unit,
    onDownload: () -> Unit = {},
    onRetry: () -> Unit = {},
    onHistoryClick: (String) -> Unit = {},
    onRemoveHistory: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onSuggestionClick: (SearchSuggestion) -> Unit = {},
    innerPadding: PaddingValues = PaddingValues(0.dp),
    sortOption: SongSortOption = SongSortOption.DEFAULT,
    sortAscending: Boolean = true,
    scrollToTopTrigger: Int = 0,
) {
    var inputMode by remember { mutableStateOf(InputMode.SEARCH) }
    var urlInput by remember { mutableStateOf("") }
    val searchInteraction = remember { MutableInteractionSource() }
    val isInputFocused by searchInteraction.collectIsFocusedAsState()

    val sortedSongs = remember(state.songs, sortOption, sortAscending) {
        state.songs.sortedByOption(sortOption, sortAscending)
    }

    val resultPageKey = "${state.searchQuery}:${state.searchCategory.name}"
    val resultListState = rememberLazyListState()
    var currentPage by remember(resultPageKey) { mutableIntStateOf(0) }
    var previousPage by remember(resultPageKey) { mutableIntStateOf(0) }

    var inputBarBottomY by remember { mutableIntStateOf(0) }
    var boxTopY by remember { mutableIntStateOf(0) }
    var panelVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.suggestionsVisible, isInputFocused, inputMode, state.isSearching) {
        if (state.suggestionsVisible && isInputFocused && inputMode == InputMode.SEARCH && !state.isSearching && state.suggestions.isNotEmpty()) {
            panelVisible = true
        } else if (!isInputFocused) {
            delay(150)
            panelVisible = false
        }
    }

    val horizontalDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val suppressNav = LocalSuppressNavTransition.current
    AnimatedContent(
        targetState = state.isDetailView,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            hierarchicalNavigationTransition(
                isForward = targetState,
                layoutDirection = horizontalDirection,
                suppress = suppressNav,
            )
        },
        label = "SearchDetailTransition",
    ) { isDetail ->
        if (isDetail) {
            SearchCollectionDetail(
                state = state,
                songs = sortedSongs,
                downloadProgress = downloadProgress,
                onToggleSelect = onToggleSelect,
                onSelectAll = onSelectAll,
                onDownload = onDownload,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                innerPadding = innerPadding,
                scrollToTopTrigger = scrollToTopTrigger,
                sortKey = "$sortOption:$sortAscending",
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { boxTopY = it.positionInWindow().y.toInt() }
            ) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(
                start = 12.dp, end = 12.dp,
                top = innerPadding.calculateTopPadding()
            )
        ) {
            // Search/URL input bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        inputBarBottomY = it.positionInWindow().y.toInt() + it.size.height
                    }
            ) {
                SearchInputBar(
                    url = urlInput,
                    searchQuery = state.searchQuery,
                    inputMode = inputMode,
                    onInputModeChange = { inputMode = it },
                    onUrlChange = { urlInput = it },
                    onSearchQueryChange = onSearchQueryChange,
                    onParse = { onParseUrl(urlInput) },
                    onSearch = onSearch,
                    isLoading = state.isSearching,
                    isSearching = state.isSearching,
                    interactionSource = searchInteraction,
                )
            }
            Spacer(Modifier.height(12.dp))

            // Status message (errors only)
            state.statusMessage?.let { msg ->
                val isError = msg.contains("失败") || msg.contains("错误") || msg.contains("超时")
                if (isError) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = msg,
                            color = colorScheme.error,
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "重试",
                            color = colorScheme.primary,
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .clickable(onClick = onRetry)
                        )
                    }
                }
            }

            if (state.hasSearched) {
                val counts = remember(state.searchResults) {
                    buildMap {
                        if (state.searchResults.songs.isNotEmpty()) put(SearchCategory.SONGS, state.searchResults.songs.size)
                        if (state.searchResults.albums.isNotEmpty()) put(SearchCategory.ALBUMS, state.searchResults.albums.size)
                        if (state.searchResults.artists.isNotEmpty()) put(SearchCategory.ARTISTS, state.searchResults.artists.size)
                        if (state.searchResults.playlists.isNotEmpty()) put(SearchCategory.PLAYLISTS, state.searchResults.playlists.size)
                        if (state.searchResults.podcasts.isNotEmpty()) put(SearchCategory.PODCASTS, state.searchResults.podcasts.size)
                    }
                }
                CategoryTabsBar(
                    selected = state.searchCategory,
                    onSelect = onSetSearchCategory,
                    counts = counts,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (shouldShowSearchSongControls(state)) {
                SongControls(
                    selectedCount = state.selectedIds.size,
                    totalCount = sortedSongs.size,
                    onSelectAll = onSelectAll,
                    onDownload = onDownload,
                    isPreparingDownloads = state.isPreparingDownloads,
                    pagination = {
                        ListPageNavigator(
                            currentPage = currentPage,
                            itemCount = sortedSongs.size,
                            onPageChange = { currentPage = it },
                            stateKey = resultPageKey,
                            hasMore = state.searchResults.hasMoreSongs,
                            isLoadingMore = state.isLoadingMore,
                            onLoadMore = onLoadMore,
                        )
                    },
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        // Results list
        AnimatedContent(
            targetState = state.searchCategory,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                peerContentTransition()
            },
            label = "SearchCategoryTransition"
        ) { cat ->
            LaunchedEffect(scrollToTopTrigger) {
                if (scrollToTopTrigger > 0) {
                    resultListState.animateScrollToItem(0)
                }
            }
            LaunchedEffect(currentPage) {
                if (currentPage != previousPage) {
                    resultListState.scrollToItem(0)
                    previousPage = currentPage
                }
            }
            CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(resultListState).value) {
            Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = resultListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp, top = 4.dp,
                    bottom = LocalMainBottomContentPadding.current
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.isSearching) {
                    item { LoadingIndicator(text = "搜索中...") }
                }

                when (cat) {
                    SearchCategory.SONGS -> {
                        items(listPage(sortedSongs, currentPage), key = { it.id }) { song ->
                            SongListItem(
                                song = song,
                                isSelected = song.id in state.selectedIds,
                                progress = downloadProgress[song.id],
                                onToggle = { onToggleSelect(song.id) }
                            )
                        }
                    }
                    SearchCategory.ALBUMS -> {
                        items(listPage(state.searchAlbums, currentPage), key = { it.id }) { album ->
                            AlbumCard(album = album, onClick = { onAlbumClick(album) })
                        }
                        item {
                            ListPageNavigator(
                                currentPage = currentPage,
                                itemCount = state.searchAlbums.size,
                                onPageChange = { currentPage = it },
                                stateKey = resultPageKey,
                                hasMore = state.searchResults.hasMoreAlbums,
                                isLoadingMore = state.isLoadingMore,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                    SearchCategory.ARTISTS -> {
                        items(listPage(state.searchArtists, currentPage), key = { it.id }) { artist ->
                            ArtistCard(artist = artist, onClick = { onArtistClick(artist) })
                        }
                        item {
                            ListPageNavigator(
                                currentPage = currentPage,
                                itemCount = state.searchArtists.size,
                                onPageChange = { currentPage = it },
                                stateKey = resultPageKey,
                                hasMore = state.searchResults.hasMoreArtists,
                                isLoadingMore = state.isLoadingMore,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                    SearchCategory.PLAYLISTS -> {
                        items(listPage(state.searchPlaylists, currentPage), key = { it.id }) { playlist ->
                            PlaylistSearchResult(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                            )
                        }
                        item {
                            ListPageNavigator(
                                currentPage = currentPage,
                                itemCount = state.searchPlaylists.size,
                                onPageChange = { currentPage = it },
                                stateKey = resultPageKey,
                                hasMore = state.searchResults.hasMorePlaylists,
                                isLoadingMore = state.isLoadingMore,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                    SearchCategory.PODCASTS -> {
                        items(listPage(state.searchPodcasts, currentPage), key = { it.id }) { podcast ->
                            PodcastSearchResult(
                                podcast = podcast,
                                onClick = { onPodcastClick(podcast) },
                            )
                        }
                        item {
                            ListPageNavigator(
                                currentPage = currentPage,
                                itemCount = state.searchPodcasts.size,
                                onPageChange = { currentPage = it },
                                stateKey = resultPageKey,
                                hasMore = state.searchResults.hasMorePodcasts,
                                isLoadingMore = state.isLoadingMore,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                }

                if (!state.hasSearched && !state.isSearching) {
                    item {
                        SearchHistorySection(
                            history = state.searchHistory,
                            onHistoryClick = onHistoryClick,
                            onRemoveHistory = onRemoveHistory,
                            onClearHistory = onClearHistory,
                        )
                    }
                } else if (!state.isSearching && state.isCurrentCategoryEmpty(sortedSongs)) {
                    item { SearchCategoryEmptyState(state.searchCategory.label) }
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(resultListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
            }
            }
        }
    }

        if (panelVisible && state.suggestions.isNotEmpty()) {
            SuggestionPanel(
                query = state.searchQuery,
                suggestions = state.suggestions,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, end = 12.dp)
                    .offset { IntOffset(0, inputBarBottomY - boxTopY + 12.dp.toPx().toInt()) },
                onSuggestionClick = onSuggestionClick,
                onSearchQuery = onSearch,
            )
            }
        }
    }
}
}

internal fun shouldShowSearchSongControls(state: SearchUiState): Boolean =
    state.searchCategory == SearchCategory.SONGS && state.songs.isNotEmpty()

private fun SearchUiState.isCurrentCategoryEmpty(sortedSongs: List<Song>): Boolean = when (searchCategory) {
    SearchCategory.SONGS -> sortedSongs.isEmpty()
    SearchCategory.ARTISTS -> searchArtists.isEmpty()
    SearchCategory.ALBUMS -> searchAlbums.isEmpty()
    SearchCategory.PLAYLISTS -> searchPlaylists.isEmpty()
    SearchCategory.PODCASTS -> searchPodcasts.isEmpty()
}

@Composable
private fun SuggestionPanel(
    query: String,
    suggestions: List<SearchSuggestion>,
    modifier: Modifier = Modifier,
    onSuggestionClick: (SearchSuggestion) -> Unit,
    onSearchQuery: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .clip(AppShapes.medium)
            .background(colorScheme.surfaceVariant.copy(alpha = 0.98f))
    ) {
        SuggestionRow(
            icon = MiuixIcons.Search,
            title = "搜索 \"$query\"",
            highlight = query,
            onClick = onSearchQuery,
            divider = true,
        )
        suggestions.forEach { suggestion ->
            SuggestionRow(
                icon = suggestion.category.icon,
                title = suggestion.keyword,
                highlight = query,
                subtitle = suggestion.category.label,
                onClick = { onSuggestionClick(suggestion) },
                divider = suggestion != suggestions.last(),
            )
        }
    }
}

private val SearchCategory.icon: ImageVector
    get() = when (this) {
        SearchCategory.SONGS -> MiuixIcons.Music
        SearchCategory.ARTISTS -> MiuixIcons.Contacts
        SearchCategory.ALBUMS -> MiuixIcons.Album
        SearchCategory.PLAYLISTS -> MiuixIcons.Playlist
        SearchCategory.PODCASTS -> MiuixIcons.Mic
    }

@Composable
private fun SuggestionRow(
    icon: ImageVector,
    title: String,
    highlight: String,
    onClick: () -> Unit,
    divider: Boolean = false,
    subtitle: String? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colorScheme.onSurfaceVariantActions,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = buildAnnotatedString {
                    val index = title.indexOf(highlight)
                    if (highlight.isNotBlank() && index >= 0) {
                        append(title, 0, index)
                        withStyle(SpanStyle(color = colorScheme.primary)) {
                            append(title, index, index + highlight.length)
                        }
                        append(title, index + highlight.length, title.length)
                    } else {
                        append(title)
                    }
                },
                style = MiuixTheme.textStyles.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (subtitle != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colorScheme.onSurfaceVariantActions,
                )
            }
        }
        if (divider) {
            HorizontalDivider(color = colorScheme.dividerLine)
        }
    }
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun SearchCollectionDetail(
    state: SearchUiState,
    songs: List<Song>,
    downloadProgress: Map<Long, DownloadProgress>,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    innerPadding: PaddingValues,
    scrollToTopTrigger: Int = 0,
    sortKey: Any? = null,
) {
    val listState = rememberLazyListState()
    val detailKey = remember(state.detailHeader) {
        state.detailHeader?.let { "${it.kind.name}:${it.id}:${it.title}" } ?: state.playlistName
    }
    var currentPage by remember(detailKey, sortKey) { mutableIntStateOf(0) }
    var previousPage by remember(detailKey, sortKey) { mutableIntStateOf(0) }
    val pagedSongs = remember(songs, currentPage) { listPage(songs, currentPage) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }
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
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        item {
            CollectionDetailHeader(
                detail = state.detailHeader,
                loadedCount = songs.size,
                loading = state.isSearching,
            )
        }
        state.statusMessage?.let { message ->
            item { SearchDetailStatus(message, onRetry) }
        }
        if (state.isSearching) {
            item { LoadingIndicator("正在加载${state.detailHeader?.kind?.label.orEmpty()}...") }
        } else if (songs.isEmpty()) {
            item { SearchCategoryEmptyState("可下载内容") }
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
                            isLoadingMore = state.isLoadingMore,
                            onLoadMore = onLoadMore,
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

@Composable
private fun SearchDetailStatus(message: String, onRetry: () -> Unit) {
    val isError = message.contains("失败") || message.contains("错误") || message.contains("超时")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = if (isError) colorScheme.error else colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2,
        )
        if (isError) {
            Text(
                text = "重试",
                modifier = Modifier.clickable(onClick = onRetry).padding(AppSpacing.sm),
                color = colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PlaylistSearchResult(playlist: DiscoveryPlaylist, onClick: () -> Unit) {
    val subtitle = buildList {
        if (playlist.creator.isNotBlank()) add(playlist.creator)
        if (playlist.trackCount > 0) add("${playlist.trackCount} 首")
        if (playlist.playCount > 0) add("${compactSearchCount(playlist.playCount)} 次播放")
    }.joinToString(" · ").ifBlank { "歌单" }
    SearchMediaResultRow(playlist.name, subtitle, playlist.coverUrl, playlist.id, onClick)
}

@Composable
private fun PodcastSearchResult(podcast: PodcastChannel, onClick: () -> Unit) {
    val subtitle = buildList {
        if (podcast.host.isNotBlank()) add(podcast.host)
        if (podcast.category.isNotBlank()) add(podcast.category)
        if (podcast.programCount > 0) add("${podcast.programCount} 期")
    }.joinToString(" · ").ifBlank { "播客" }
    SearchMediaResultRow(podcast.name, subtitle, podcast.coverUrl, podcast.id, onClick)
}

@Composable
private fun SearchMediaResultRow(
    title: String,
    subtitle: String,
    coverUrl: String,
    key: Any,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().directionalEntrance(key),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val placeholder = painterResource(R.drawable.ic_music_placeholder)
            AsyncImage(
                model = coverDisplayUrl(coverUrl, CoverRequestSize.LIST).takeUnless { it.isBlank() },
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                        imageVector = MiuixIcons.ChevronForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colorScheme.primary,
                    )
        }
    }
}

@Composable
private fun SearchCategoryEmptyState(category: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "没有找到相关$category",
            color = colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
private fun SearchHistorySection(
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (history.isEmpty()) {
        SearchLandingEmptyState()
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "搜索历史",
                color = colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "清空",
                color = colorScheme.primary,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.clickable(onClick = onClearHistory),
            )
        }
        history.forEach { q ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHistoryClick(q) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = q,
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "×",
                    color = colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.title3,
                    modifier = Modifier.clickable { onRemoveHistory(q) }.padding(start = 8.dp, end = 4.dp),
                )
            }
        }
    }
}

private fun compactSearchCount(value: Long): String = when {
    value >= 100_000_000 -> "${value / 100_000_000}亿"
    value >= 10_000 -> "${value / 10_000}万"
    else -> value.toString()
}
