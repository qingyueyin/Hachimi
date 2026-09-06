package com.qing.hachimi.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qing.hachimi.data.local.CookieManager
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.model.*
import com.qing.hachimi.data.repository.NeteaseRepository
import com.qing.hachimi.downloader.DownloadEngine
import com.qing.hachimi.downloader.DownloadPreparation
import com.qing.hachimi.util.AppLogger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class SearchUiState(
    val searchQuery: String = "",
    val searchHistory: List<String> = emptyList(),
    val suggestions: List<SearchSuggestion> = emptyList(),
    val isLoadingSuggestions: Boolean = false,
    val suggestionsVisible: Boolean = false,
    val songs: List<Song> = emptyList(),
    val searchArtists: List<ArtistResult> = emptyList(),
    val searchAlbums: List<AlbumResult> = emptyList(),
    val searchPlaylists: List<DiscoveryPlaylist> = emptyList(),
    val searchPodcasts: List<PodcastChannel> = emptyList(),
    val searchResults: SearchResults = SearchResults(),
    val searchCategory: SearchCategory = SearchCategory.SONGS,
    val parentSearchCategory: SearchCategory = SearchCategory.SONGS,
    val selectedIds: Set<Long> = emptySet(),
    val hasSearched: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val statusMessage: String? = null,
    val playlistName: String = "",
    val playlistUrl: String = "",
    val sourceLabel: String = "",
    val isDetailView: Boolean = false,
    val detailHeader: SearchDetailHeader? = null,
    val detailOffset: Int = 0,
    val detailHasMore: Boolean = false,
    val isPreparingDownloads: Boolean = false,
    val lastRequestType: SearchRequestType = SearchRequestType.SEARCH,
    val lastAlbum: AlbumResult? = null,
    val lastPlaylist: DiscoveryPlaylist? = null,
    val lastPodcast: PodcastChannel? = null,
)

typealias SearchDetailHeader = CollectionDetail
typealias SearchDetailKind = CollectionKind

enum class SearchRequestType {
    SEARCH,
    URL,
    ALBUM,
    PLAYLIST,
    PODCAST,
}

class SearchViewModel(
    private val repository: NeteaseRepository,
    private val cookieManager: CookieManager,
    private val settingsManager: SettingsManager,
    private val downloadEngine: DownloadEngine
) : ViewModel() {

    val uiState = kotlinx.coroutines.flow.MutableStateFlow(SearchUiState())
    private var detailRequestId = 0L
    private var suggestionJob: kotlinx.coroutines.Job? = null
    private val suggestionCache = kotlin.collections.mutableMapOf<String, List<SearchSuggestion>>()

    init {
        uiState.value = uiState.value.copy(searchHistory = settingsManager.searchHistory)
    }

    private suspend fun awaitToken() {
        if (!cookieManager.isLoggedIn() && !cookieManager.hasUsableToken()) {
            cookieManager.tokenReady.await()
        }
    }

    fun updateSearchQuery(q: String) {
        uiState.value = uiState.value.copy(searchQuery = q)
        fetchSuggestions(q)
    }
    fun updatePlaylistUrl(u: String) { uiState.value = uiState.value.copy(playlistUrl = u) }
    fun setSearchCategory(c: SearchCategory) { uiState.value = uiState.value.copy(searchCategory = c) }
    fun clearStatus() { uiState.value = uiState.value.copy(statusMessage = null) }

    private fun fetchSuggestions(q: String) {
        suggestionJob?.cancel()
        val trimmed = q.trim()
        if (trimmed.length < 1 || uiState.value.isSearching || uiState.value.isDetailView) {
            uiState.value = uiState.value.copy(suggestions = emptyList(), suggestionsVisible = false)
            return
        }
        suggestionCache[trimmed]?.let { cached ->
            uiState.value = uiState.value.copy(suggestions = cached, suggestionsVisible = true, isLoadingSuggestions = false)
            return
        }
        uiState.value = uiState.value.copy(suggestionsVisible = true, isLoadingSuggestions = true)
        suggestionJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            try {
                awaitToken()
                withTimeout(SUGGEST_TIMEOUT_MS) {
                    repository.getSearchSuggestions(trimmed).fold(
                        onSuccess = { list ->
                            if (uiState.value.searchQuery.trim() == trimmed) {
                                suggestionCache[trimmed] = list
                                uiState.value = uiState.value.copy(
                                    suggestions = list,
                                    suggestionsVisible = list.isNotEmpty(),
                                    isLoadingSuggestions = false,
                                )
                            }
                        },
                        onFailure = {
                            if (uiState.value.searchQuery.trim() == trimmed) {
                                uiState.value = uiState.value.copy(suggestions = emptyList(), suggestionsVisible = false, isLoadingSuggestions = false)
                            }
                        },
                    )
                }
            } catch (e: TimeoutCancellationException) {
                if (uiState.value.searchQuery.trim() == trimmed) {
                    uiState.value = uiState.value.copy(suggestions = emptyList(), suggestionsVisible = false, isLoadingSuggestions = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (uiState.value.searchQuery.trim() == trimmed) {
                    uiState.value = uiState.value.copy(suggestions = emptyList(), suggestionsVisible = false, isLoadingSuggestions = false)
                }
            }
        }
    }

    fun searchFromSuggestion(suggestion: SearchSuggestion) {
        suggestionJob?.cancel()
        uiState.value = uiState.value.copy(
            searchQuery = suggestion.keyword,
            suggestions = emptyList(),
            suggestionsVisible = false,
            isLoadingSuggestions = false,
        )
        search(preferredCategory = suggestion.category)
    }

    fun searchFromHistory(q: String) {
        updateSearchQuery(q)
        search()
    }

    fun removeSearchHistory(q: String) {
        val history = uiState.value.searchHistory.filterNot { it == q }
        settingsManager.searchHistory = history
        uiState.value = uiState.value.copy(searchHistory = history)
    }

    fun clearSearchHistory() {
        settingsManager.searchHistory = emptyList()
        uiState.value = uiState.value.copy(searchHistory = emptyList())
    }

    private fun addSearchHistory(q: String) {
        val history = (listOf(q) + uiState.value.searchHistory.filterNot { it == q }).take(20)
        settingsManager.searchHistory = history
        uiState.value = uiState.value.copy(searchHistory = history)
    }

    fun goBack() {
        detailRequestId++
        val s = uiState.value
        val hasParentSearch = s.searchResults.run {
            songs.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty() ||
                playlists.isNotEmpty() || podcasts.isNotEmpty()
        }
        if (hasParentSearch) {
            uiState.value = s.copy(
                songs = s.searchResults.songs,
                searchAlbums = s.searchResults.albums,
                searchArtists = s.searchResults.artists,
                searchPlaylists = s.searchResults.playlists,
                searchPodcasts = s.searchResults.podcasts,
                searchCategory = s.parentSearchCategory,
                playlistName = "搜索结果: ${s.searchQuery}",
                sourceLabel = "搜索",
                statusMessage = buildMsg(s.searchResults),
                isDetailView = false,
                isSearching = false,
                isLoadingMore = false,
                detailHeader = null,
                detailOffset = 0,
                detailHasMore = false,
                selectedIds = emptySet(),
            )
        } else {
            uiState.value = s.copy(
                songs = emptyList(),
                selectedIds = emptySet(),
                playlistName = "",
                sourceLabel = "搜索",
                isDetailView = false,
                isSearching = false,
                isLoadingMore = false,
                detailHeader = null,
                detailOffset = 0,
                detailHasMore = false,
            )
        }
    }

    fun resetToHome() {
        detailRequestId++
        uiState.value = SearchUiState(
            searchQuery = uiState.value.searchQuery,
            playlistUrl = uiState.value.playlistUrl,
        )
    }

    fun retry() {
        val s = uiState.value
        when (s.lastRequestType) {
            SearchRequestType.SEARCH -> search()
            SearchRequestType.URL -> parseUrl()
            SearchRequestType.ALBUM -> s.lastAlbum?.let(::loadAlbumFromResult)
            SearchRequestType.PLAYLIST -> s.lastPlaylist?.let(::loadPlaylistFromResult)
            SearchRequestType.PODCAST -> s.lastPodcast?.let(::loadPodcastFromResult)
        }
    }

    fun downloadSelected() {
        val state = uiState.value
        if (state.isPreparingDownloads) return
        val songs = state.songs.filter { it.id in state.selectedIds }
        if (songs.isEmpty()) return
        AppLogger.info("[SearchVM] downloadSelected: ${songs.size} songs")
        uiState.value = state.copy(
            isPreparingDownloads = true,
            statusMessage = "正在获取下载链接...",
        )
        viewModelScope.launch {
            try {
                awaitToken()
                val result = DownloadPreparation.enqueueSongs(
                    songs = songs,
                    repository = repository,
                    settings = settingsManager,
                    engine = downloadEngine,
                    options = DownloadPreparation.optionsFrom(
                        settings = settingsManager,
                        cookie = cookieManager.getRawCookies(),
                        groupName = state.playlistName.ifBlank { state.sourceLabel },
                    ),
                )
                uiState.value = uiState.value.copy(
                    isPreparingDownloads = false,
                    selectedIds = if (result.added > 0) emptySet() else uiState.value.selectedIds,
                    statusMessage = result.message,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("[SearchVM] preparing downloads failed", e)
                uiState.value = uiState.value.copy(
                    isPreparingDownloads = false,
                    statusMessage = "准备下载失败: ${e.message}",
                )
            }
        }
    }

    fun toggleSelectAll() {
        val all = uiState.value.songs.map { it.id }.toSet()
        val cur = uiState.value.selectedIds
        uiState.value = uiState.value.copy(selectedIds = if (cur.containsAll(all) && all.isNotEmpty()) emptySet() else all)
    }

    fun toggleSongSelection(id: Long) {
        val cur = uiState.value.selectedIds
        uiState.value = uiState.value.copy(selectedIds = if (id in cur) cur - id else cur + id)
    }

    fun search(preferredCategory: SearchCategory? = null) {
        val q = uiState.value.searchQuery.trim()
        if (q.isBlank() || uiState.value.isSearching) return
        suggestionJob?.cancel()
        uiState.value = uiState.value.copy(
            hasSearched = true,
            isSearching = true,
            statusMessage = null,
            sourceLabel = "搜索",
            isDetailView = false,
            detailHeader = null,
            lastRequestType = SearchRequestType.SEARCH,
            suggestions = emptyList(),
            suggestionsVisible = false,
            isLoadingSuggestions = false,
        )
        viewModelScope.launch {
            try {
                awaitToken()
                withTimeout(SEARCH_TIMEOUT_MS) {
                    repository.searchAll(q).fold(
                        onSuccess = { r ->
                            addSearchHistory(q)
                            val processed = SearchResultProcessor.process(q, r)
                            val result = processed.results
                            val effectiveCategory = preferredCategory?.takeIf { pc ->
                                when (pc) {
                                    SearchCategory.SONGS -> result.songs.isNotEmpty()
                                    SearchCategory.ARTISTS -> result.artists.isNotEmpty()
                                    SearchCategory.ALBUMS -> result.albums.isNotEmpty()
                                    SearchCategory.PLAYLISTS -> result.playlists.isNotEmpty()
                                    SearchCategory.PODCASTS -> result.podcasts.isNotEmpty()
                                }
                            } ?: processed.initialCategory
                            uiState.value = uiState.value.copy(
                                songs = result.songs,
                                searchResults = result,
                                searchArtists = result.artists,
                                searchAlbums = result.albums,
                                searchPlaylists = result.playlists,
                                searchPodcasts = result.podcasts,
                                searchCategory = effectiveCategory,
                                parentSearchCategory = effectiveCategory,
                                isSearching = false, selectedIds = emptySet(),
                                playlistName = "搜索结果: $q", statusMessage = buildMsg(result)
                            )
                        },
                        onFailure = { e -> uiState.value = uiState.value.copy(isSearching = false, statusMessage = "搜索失败: ${e.message}") }
                    )
                }
            } catch (e: TimeoutCancellationException) {
                uiState.value = uiState.value.copy(isSearching = false, statusMessage = "搜索超时，请检查网络后重试")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("[SearchVM] search failed unexpectedly", e)
                uiState.value = uiState.value.copy(isSearching = false, statusMessage = "搜索失败: ${e.message}")
            }
        }
    }

    fun parseUrl() {
        val u = uiState.value.playlistUrl.trim()
        if (u.isBlank()) { uiState.value = uiState.value.copy(statusMessage = "请输入链接"); return }
        if (uiState.value.isSearching) return
        val link = NeteaseLinkParser.parse(u)
        val directId = u.takeIf { it.all(Char::isDigit) }
        uiState.value = uiState.value.copy(lastRequestType = SearchRequestType.URL)
        when {
            link?.type == NeteaseLinkParser.Type.SONG -> fetchSong(link.id)
            link?.type == NeteaseLinkParser.Type.ALBUM -> fetchAlbum(link.id)
            link?.type == NeteaseLinkParser.Type.PLAYLIST -> fetchPlaylist(link.id)
            directId != null -> fetchPlaylist(directId)
            else -> uiState.value = uiState.value.copy(statusMessage = "链接格式错误")
        }
    }

    fun loadMoreSearch() {
        val s = uiState.value
        if (s.isLoadingMore) return
        if (s.isDetailView) {
            loadMorePodcastDetail(s)
            return
        }
        uiState.value = s.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                awaitToken()
                withTimeout(LOAD_MORE_TIMEOUT_MS) {
                    when (s.searchCategory) {
                        SearchCategory.SONGS -> if (s.searchResults.hasMoreSongs) {
                            repository.loadMoreSongs(s.searchQuery, s.searchResults.songOffset).fold(
                                onSuccess = { r ->
                                    val merged = SearchResultProcessor.mergeSongs(uiState.value.searchResults, r)
                                    uiState.value = uiState.value.copy(
                                        songs = merged.songs,
                                        searchResults = merged,
                                        isLoadingMore = false,
                                    )
                                },
                                onFailure = { uiState.value = uiState.value.copy(isLoadingMore = false, statusMessage = "加载更多失败") }
                            )
                        } else uiState.value = uiState.value.copy(isLoadingMore = false)
                        SearchCategory.ALBUMS -> if (s.searchResults.hasMoreAlbums) {
                            val albumPage = s.searchResults.albumArtistId?.let { artistId ->
                                repository.loadMoreArtistAlbums(artistId, s.searchResults.albumOffset)
                            } ?: repository.loadMoreAlbums(s.searchQuery, s.searchResults.albumOffset)
                            albumPage.fold(
                                onSuccess = { r ->
                                    val merged = SearchResultProcessor.mergeAlbums(uiState.value.searchResults, r)
                                    uiState.value = uiState.value.copy(
                                        searchAlbums = merged.albums,
                                        searchResults = merged,
                                        isLoadingMore = false,
                                    )
                                },
                                onFailure = { uiState.value = uiState.value.copy(isLoadingMore = false, statusMessage = "加载更多失败") }
                            )
                        } else uiState.value = uiState.value.copy(isLoadingMore = false)
                        SearchCategory.ARTISTS -> if (s.searchResults.hasMoreArtists) {
                            repository.loadMoreArtists(s.searchQuery, s.searchResults.artistOffset).fold(
                                onSuccess = { r ->
                                    val merged = SearchResultProcessor.mergeArtists(uiState.value.searchResults, r)
                                    uiState.value = uiState.value.copy(
                                        searchArtists = merged.artists,
                                        searchResults = merged,
                                        isLoadingMore = false,
                                    )
                                },
                                onFailure = { uiState.value = uiState.value.copy(isLoadingMore = false, statusMessage = "加载更多失败") }
                            )
                        } else uiState.value = uiState.value.copy(isLoadingMore = false)
                        SearchCategory.PLAYLISTS -> if (s.searchResults.hasMorePlaylists) {
                            repository.loadMorePlaylists(s.searchQuery, s.searchResults.playlistOffset).fold(
                                onSuccess = { result ->
                                    val merged = SearchResultProcessor.mergePlaylists(
                                        uiState.value.searchResults,
                                        result,
                                    )
                                    uiState.value = uiState.value.copy(
                                        searchPlaylists = merged.playlists,
                                        searchResults = merged,
                                        isLoadingMore = false,
                                    )
                                },
                                onFailure = {
                                    uiState.value = uiState.value.copy(
                                        isLoadingMore = false,
                                        statusMessage = "加载更多失败",
                                    )
                                },
                            )
                        } else uiState.value = uiState.value.copy(isLoadingMore = false)
                        SearchCategory.PODCASTS -> if (s.searchResults.hasMorePodcasts) {
                            repository.loadMorePodcasts(s.searchQuery, s.searchResults.podcastOffset).fold(
                                onSuccess = { result ->
                                    val merged = SearchResultProcessor.mergePodcasts(
                                        uiState.value.searchResults,
                                        result,
                                    )
                                    uiState.value = uiState.value.copy(
                                        searchPodcasts = merged.podcasts,
                                        searchResults = merged,
                                        isLoadingMore = false,
                                    )
                                },
                                onFailure = {
                                    uiState.value = uiState.value.copy(
                                        isLoadingMore = false,
                                        statusMessage = "加载更多失败",
                                    )
                                },
                            )
                        } else uiState.value = uiState.value.copy(isLoadingMore = false)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                uiState.value = uiState.value.copy(isLoadingMore = false, statusMessage = "加载超时，请重试")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("[SearchVM] load more failed unexpectedly", e)
                uiState.value = uiState.value.copy(isLoadingMore = false, statusMessage = "加载失败: ${e.message}")
            }
        }
    }

    private fun loadMorePodcastDetail(state: SearchUiState) {
        val podcast = state.lastPodcast ?: return
        if (state.detailHeader?.kind != SearchDetailKind.PODCAST || !state.detailHasMore) return
        val requestId = detailRequestId
        uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                awaitToken()
                withTimeout(LOAD_MORE_TIMEOUT_MS) {
                    repository.getPodcastPrograms(podcast, 50, state.detailOffset).fold(
                        onSuccess = { page ->
                            if (requestId != detailRequestId || !uiState.value.isDetailView) return@fold
                            uiState.value = uiState.value.copy(
                                songs = (uiState.value.songs + page.items).distinctBy { it.id },
                                detailOffset = page.nextOffset,
                                detailHasMore = page.hasMore,
                                isLoadingMore = false,
                            )
                        },
                        onFailure = {
                            if (requestId == detailRequestId) {
                                uiState.value = uiState.value.copy(
                                    isLoadingMore = false,
                                    statusMessage = "加载更多失败",
                                )
                            }
                        },
                    )
                }
            } catch (e: TimeoutCancellationException) {
                if (requestId == detailRequestId) {
                    uiState.value = uiState.value.copy(
                        isLoadingMore = false,
                        statusMessage = "加载超时，请重试",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestId == detailRequestId) {
                    uiState.value = uiState.value.copy(
                        isLoadingMore = false,
                        statusMessage = "加载失败: ${e.message}",
                    )
                }
            }
        }
    }

    fun loadAlbumFromResult(album: AlbumResult) {
        if (uiState.value.isSearching) return
        val requestId = beginDetail(
            header = SearchDetailHeader(
                kind = SearchDetailKind.ALBUM,
                id = album.id,
                title = album.name,
                subtitle = album.artist,
                coverUrl = album.coverUrl,
                publishTime = album.publishTime,
            ),
            requestType = SearchRequestType.ALBUM,
            lastAlbum = album,
        )
        viewModelScope.launch {
            try {
                awaitToken()
                withTimeout(FETCH_TIMEOUT_MS) {
                    repository.getAlbumDetail(album.id.toString()).fold(
                        onSuccess = { content -> finishDetail(requestId, content) },
                        onFailure = { error -> failDetail(requestId, "加载失败: ${error.message}") },
                    )
                }
            } catch (e: TimeoutCancellationException) {
                failDetail(requestId, "加载超时，请重试")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("[SearchVM] album load failed unexpectedly", e)
                failDetail(requestId, "加载失败: ${e.message}")
            }
        }
    }

    fun loadPlaylistFromResult(playlist: DiscoveryPlaylist) {
        if (uiState.value.isSearching) return
        val requestId = beginDetail(
            header = SearchDetailHeader(
                kind = SearchDetailKind.PLAYLIST,
                id = playlist.id,
                title = playlist.name,
                subtitle = playlist.creator,
                coverUrl = playlist.coverUrl,
                description = playlist.description,
                expectedItemCount = playlist.trackCount,
                playCount = playlist.playCount,
                publishTime = playlist.createTime,
                updateTime = playlist.updateTime,
                tags = playlist.tags,
            ),
            requestType = SearchRequestType.PLAYLIST,
            lastPlaylist = playlist,
        )
        viewModelScope.launch {
            try {
                awaitToken()
                withTimeout(FETCH_TIMEOUT_MS) {
                    repository.getPlaylistDetail(playlist.id.toString()).fold(
                        onSuccess = { content -> finishDetail(requestId, content) },
                        onFailure = { error -> failDetail(requestId, "加载失败: ${error.message}") },
                    )
                }
            } catch (e: TimeoutCancellationException) {
                failDetail(requestId, "加载超时，请重试")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("[SearchVM] playlist load failed unexpectedly", e)
                failDetail(requestId, "加载失败: ${e.message}")
            }
        }
    }

    fun loadPodcastFromResult(podcast: PodcastChannel) {
        if (uiState.value.isSearching) return
        val requestId = beginDetail(
            header = SearchDetailHeader(
                kind = SearchDetailKind.PODCAST,
                id = podcast.id,
                title = podcast.name,
                subtitle = listOf(podcast.host, podcast.category)
                    .filter(String::isNotBlank)
                    .joinToString(" · "),
                coverUrl = podcast.coverUrl,
                description = podcast.description,
                expectedItemCount = podcast.programCount,
                playCount = podcast.playCount,
                publishTime = podcast.createTime,
                updateTime = podcast.updateTime,
            ),
            requestType = SearchRequestType.PODCAST,
            lastPodcast = podcast,
        )
        viewModelScope.launch {
            val detail = runCatching { repository.getPodcastDetail(podcast.id).getOrNull() }.getOrNull()
            if (detail != null && requestId == detailRequestId && uiState.value.isDetailView) {
                uiState.value = uiState.value.copy(detailHeader = detail.toCollectionDetail())
            }
        }
        viewModelScope.launch {
            try {
                awaitToken()
                withTimeout(FETCH_TIMEOUT_MS) {
                    repository.getPodcastPrograms(podcast, 50, 0).fold(
                        onSuccess = { page ->
                            if (requestId != detailRequestId || !uiState.value.isDetailView) return@fold
                            uiState.value = uiState.value.copy(
                                songs = page.items,
                                selectedIds = emptySet(),
                                searchCategory = SearchCategory.SONGS,
                                playlistName = podcast.name,
                                statusMessage = if (page.items.isEmpty()) "暂无节目" else null,
                                detailOffset = page.nextOffset,
                                detailHasMore = page.hasMore,
                                isSearching = false,
                            )
                        },
                        onFailure = { error -> failDetail(requestId, "加载失败: ${error.message}") },
                    )
                }
            } catch (e: TimeoutCancellationException) {
                failDetail(requestId, "加载超时，请重试")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("[SearchVM] podcast load failed unexpectedly", e)
                failDetail(requestId, "加载失败: ${e.message}")
            }
        }
    }

    private fun beginDetail(
        header: SearchDetailHeader,
        requestType: SearchRequestType,
        lastAlbum: AlbumResult? = null,
        lastPlaylist: DiscoveryPlaylist? = null,
        lastPodcast: PodcastChannel? = null,
    ): Long {
        val state = uiState.value
        val requestId = ++detailRequestId
        uiState.value = state.copy(
            isSearching = true,
            isLoadingMore = false,
            sourceLabel = header.kind.label,
            isDetailView = true,
            detailHeader = header,
            detailOffset = 0,
            detailHasMore = false,
            parentSearchCategory = if (state.isDetailView) {
                state.parentSearchCategory
            } else {
                state.searchCategory
            },
            lastRequestType = requestType,
            lastAlbum = lastAlbum,
            lastPlaylist = lastPlaylist,
            lastPodcast = lastPodcast,
            selectedIds = emptySet(),
            statusMessage = null,
        )
        return requestId
    }

    private fun finishDetail(requestId: Long, content: CollectionContent) {
        if (requestId != detailRequestId || !uiState.value.isDetailView) return
        val songs = content.songs.distinctBy { it.id }
        uiState.value = uiState.value.copy(
            songs = songs,
            isSearching = false,
            selectedIds = emptySet(),
            searchCategory = SearchCategory.SONGS,
            playlistName = content.detail.title,
            detailHeader = content.detail,
            statusMessage = if (songs.isEmpty()) "暂无可下载内容" else null,
        )
    }

    private fun failDetail(requestId: Long, message: String) {
        if (requestId != detailRequestId || !uiState.value.isDetailView) return
        uiState.value = uiState.value.copy(isSearching = false, isLoadingMore = false, statusMessage = message)
    }

    private fun fetchPlaylist(id: String) {
        val requestId = beginDetail(
            header = SearchDetailHeader(
                kind = SearchDetailKind.PLAYLIST,
                id = id.toLongOrNull() ?: 0,
                title = "歌单",
                subtitle = "网易云音乐",
            ),
            requestType = SearchRequestType.URL,
        )
        viewModelScope.launch {
            try {
                awaitToken()
                withTimeout(FETCH_TIMEOUT_MS) {
                    repository.getPlaylistDetail(id).fold(
                        onSuccess = { content -> finishDetail(requestId, content) },
                        onFailure = { error -> failDetail(requestId, "解析失败: ${error.message}") },
                    )
                }
            } catch (e: TimeoutCancellationException) {
                failDetail(requestId, "解析超时，请重试")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("[SearchVM] playlist parsing failed unexpectedly", e)
                failDetail(requestId, "解析失败: ${e.message}")
            }
        }
    }

    private fun fetchAlbum(id: String) {
        val requestId = beginDetail(
            header = SearchDetailHeader(
                kind = SearchDetailKind.ALBUM,
                id = id.toLongOrNull() ?: 0,
                title = "专辑",
                subtitle = "网易云音乐",
            ),
            requestType = SearchRequestType.URL,
        )
        viewModelScope.launch {
            try {
                awaitToken()
                withTimeout(FETCH_TIMEOUT_MS) {
                    repository.getAlbumDetail(id).fold(
                        onSuccess = { content -> finishDetail(requestId, content) },
                        onFailure = { error -> failDetail(requestId, "解析失败: ${error.message}") },
                    )
                }
            } catch (e: TimeoutCancellationException) {
                failDetail(requestId, "解析超时，请重试")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("[SearchVM] album parsing failed unexpectedly", e)
                failDetail(requestId, "解析失败: ${e.message}")
            }
        }
    }

    private fun fetchSong(id: String) {
        val requestId = beginDetail(
            header = SearchDetailHeader(
                kind = SearchDetailKind.SONG,
                id = id.toLongOrNull() ?: 0,
                title = "歌曲",
                subtitle = "网易云音乐",
                expectedItemCount = 1,
            ),
            requestType = SearchRequestType.URL,
        )
        viewModelScope.launch {
            try {
                awaitToken()
                withTimeout(FETCH_TIMEOUT_MS) {
                    repository.getSingleSong(id).fold(
                        onSuccess = { song ->
                            if (requestId != detailRequestId || !uiState.value.isDetailView) return@fold
                            uiState.value = uiState.value.copy(
                                songs = listOf(song),
                                isSearching = false,
                                selectedIds = setOf(song.id),
                                playlistName = song.name,
                                detailHeader = uiState.value.detailHeader?.copy(
                                    title = song.name,
                                    subtitle = "${song.artists} · ${song.album}",
                                    coverUrl = song.coverUrl,
                                ),
                                statusMessage = null,
                            )
                        },
                        onFailure = { error -> failDetail(requestId, "解析失败: ${error.message}") },
                    )
                }
            } catch (e: TimeoutCancellationException) {
                failDetail(requestId, "解析超时，请重试")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("[SearchVM] song parsing failed unexpectedly", e)
                failDetail(requestId, "解析失败: ${e.message}")
            }
        }
    }

    companion object {
        private const val SEARCH_TIMEOUT_MS = 10_000L
        private const val LOAD_MORE_TIMEOUT_MS = 10_000L
        private const val FETCH_TIMEOUT_MS = 15_000L
        private const val SUGGEST_TIMEOUT_MS = 5_000L
        private const val SUGGEST_DEBOUNCE_MS = 300L

        private fun buildMsg(r: SearchResults): String {
            val p = mutableListOf<String>()
            if (r.songs.isNotEmpty()) p.add("${r.songs.size} 首歌曲")
            if (r.albums.isNotEmpty()) p.add("${r.albums.size} 张专辑")
            if (r.artists.isNotEmpty()) p.add("${r.artists.size} 位歌手")
            if (r.playlists.isNotEmpty()) p.add("${r.playlists.size} 个歌单")
            if (r.podcasts.isNotEmpty()) p.add("${r.podcasts.size} 个播客")
            return if (p.isEmpty()) "未找到" else "找到 " + p.joinToString("、")
        }
    }
}

private fun PodcastChannel.toCollectionDetail() = CollectionDetail(
    kind = CollectionKind.PODCAST,
    id = id,
    title = name,
    subtitle = listOf(host, category).filter(String::isNotBlank).joinToString(" · "),
    coverUrl = coverUrl,
    description = description,
    expectedItemCount = programCount,
    playCount = playCount,
    publishTime = createTime,
    updateTime = updateTime,
)
