package com.qing.hachimi.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qing.hachimi.data.api.ChartInfo
import com.qing.hachimi.data.api.ArtistAuthenticationRequiredException
import com.qing.hachimi.data.local.CookieManager
import com.qing.hachimi.data.local.DiscoveryCacheManager
import com.qing.hachimi.data.local.SeenManager
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.model.AlbumResult
import com.qing.hachimi.data.model.AlbumResultDeduplicator
import com.qing.hachimi.data.model.ArtistArea
import com.qing.hachimi.data.model.ArtistAbout
import com.qing.hachimi.data.model.ArtistDetailSection
import com.qing.hachimi.data.model.ArtistProfile
import com.qing.hachimi.data.model.ArtistResult
import com.qing.hachimi.data.model.CollectionContent
import com.qing.hachimi.data.model.CollectionDetail
import com.qing.hachimi.data.model.CollectionKind
import com.qing.hachimi.data.model.DiscoveryPlaylist
import com.qing.hachimi.data.model.PlaylistCategory
import com.qing.hachimi.data.model.PodcastChannel
import com.qing.hachimi.data.model.Song
import com.qing.hachimi.data.model.SourceMode
import com.qing.hachimi.data.api.StyleTag
import com.qing.hachimi.data.repository.NeteaseRepository
import com.qing.hachimi.downloader.DownloadEngine
import com.qing.hachimi.downloader.DownloadPreparation
import com.qing.hachimi.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class DiscoverUiState(
    val sourceMode: SourceMode = SourceMode.RECOMMEND,
    val previousSourceMode: SourceMode = SourceMode.RECOMMEND,
    val preSongSourceMode: SourceMode = SourceMode.RECOMMEND,
    val chartList: List<ChartInfo> = emptyList(),
    val recommendSongs: List<Song> = emptyList(),
    val recommendedPlaylists: List<DiscoveryPlaylist> = emptyList(),
    val discoveryPlaylists: List<DiscoveryPlaylist> = emptyList(),
    val playlistCategory: PlaylistCategory = PlaylistCategory.ALL,
    val playlistOffset: Int = 0,
    val playlistsHasMore: Boolean = true,
    val podcastChannels: List<PodcastChannel> = emptyList(),
    val podcastOffset: Int = 0,
    val podcastsHasMore: Boolean = true,
    val artists: List<ArtistResult> = emptyList(),
    val artistArea: ArtistArea = ArtistArea.HOT,
    val artistOffset: Int = 0,
    val artistsHasMore: Boolean = true,
    val newAlbumsList: List<AlbumResult> = emptyList(),
    val newAlbumsOffset: Int = 0,
    val newAlbumsHasMore: Boolean = true,
    val newSongs: List<Song> = emptyList(),
    val newSongsArea: Int = 0,
    val isLoadingNewSongs: Boolean = false,
    val styleTags: List<StyleTag> = emptyList(),
    val isLoadingStyleTags: Boolean = false,
    val playlistTag: String? = null,  // 曲风歌单选中标签（非空时优先于 playlistCategory）
    val hotSearchKeywords: List<Pair<String, Int>> = emptyList(),
    val activeArtist: ArtistResult? = null,
    val artistProfile: ArtistProfile? = null,
    val artistParentSourceMode: SourceMode = SourceMode.ARTISTS,
    val artistDetailSection: ArtistDetailSection = ArtistDetailSection.HOT_SONGS,
    val artistDetailSongs: List<Song> = emptyList(),
    val artistDetailAlbums: List<AlbumResult> = emptyList(),
    val artistAlbumOffset: Int = 0,
    val artistAlbumsHasMore: Boolean = true,
    val artistAlbumsLoaded: Boolean = false,
    val artistAbout: ArtistAbout = ArtistAbout(),
    val artistAboutLoaded: Boolean = false,
    val isLoadingArtistAlbums: Boolean = false,
    val isLoadingArtistAbout: Boolean = false,
    val artistSectionMessage: String? = null,
    val activePodcast: PodcastChannel? = null,
    val collectionDetail: CollectionDetail? = null,
    val detailOffset: Int = 0,
    val detailHasMore: Boolean = false,
    val songs: List<Song> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val playlistName: String = "",
    val sourceLabel: String = "",
    val isLoading: Boolean = false,
    val isLoadingRecommendations: Boolean = false,
    val isLoadingCharts: Boolean = false,
    val isLoadingPlaylists: Boolean = false,
    val isLoadingPodcasts: Boolean = false,
    val isLoadingArtists: Boolean = false,
    val isLoadingAlbums: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingMoreDetail: Boolean = false,
    val statusMessage: String? = null,
    val gridColumns: Int = 2,
    val fmSongs: List<Song> = emptyList(),
    val fmHistoryGroups: List<List<Song>> = emptyList(),
    val fmGroupCount: Int = 0,
    val isLoadingFm: Boolean = false,
    val fmThreshold: Int = 3,
    val isPreparingDownloads: Boolean = false,
) {
    val isDetailView: Boolean get() = songs.isNotEmpty() || sourceLabel.isNotBlank() || collectionDetail != null
    val canNavigateBack: Boolean get() = isDetailView || sourceMode != SourceMode.RECOMMEND
    val isArtistPage: Boolean
        get() = sourceMode == SourceMode.ARTIST_DETAIL && sourceLabel == "歌手" && activeArtist != null
    val shouldReturnToSearch: Boolean
        get() = isArtistPage && artistParentSourceMode == SourceMode.SEARCH
    val navigationTitle: String get() = when {
        playlistName.isNotBlank() -> playlistName
        sourceMode == SourceMode.CHARTS -> "排行榜"
        sourceMode == SourceMode.PLAYLISTS -> if (playlistTag != null) "曲风歌单" else "歌单广场"
        sourceMode == SourceMode.PODCASTS -> "播客"
        sourceMode == SourceMode.ARTISTS -> "歌手"
        sourceMode == SourceMode.NEW_ALBUMS -> "新碟上架"
        sourceMode == SourceMode.NEW_SONGS -> "新歌速递"
        sourceMode == SourceMode.STYLE_PLAYLISTS -> "曲风歌单"
        sourceMode == SourceMode.PERSONAL_FM -> "私人 FM"
        else -> "发现"
    }
}

class DiscoverViewModel(
    private val repository: NeteaseRepository,
    private val cookieManager: CookieManager,
    private val settingsManager: SettingsManager,
    private val discoveryCache: DiscoveryCacheManager,
    private val seenManager: SeenManager,
    private val downloadEngine: DownloadEngine,
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 30
        private const val ALBUM_PAGE_SIZE = 20
        private const val ARTIST_ALBUM_PAGE_SIZE = 30
        private const val REQUEST_TIMEOUT_MS = 12_000L
    }

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState
    private var retryDetail: (() -> Unit)? = null
    private var detailRequestId = 0L

    init {
        val columns = settingsManager.getGridColumns(gridPageKey(SourceMode.RECOMMEND))
        val cachedAlbums = discoveryCache.getCachedNewAlbums().orEmpty()
        _uiState.value = _uiState.value.copy(
            gridColumns = columns,
            chartList = discoveryCache.getCachedCharts().orEmpty(),
            newAlbumsList = cachedAlbums,
            newAlbumsOffset = cachedAlbums.size,
            newAlbumsHasMore = cachedAlbums.size >= ALBUM_PAGE_SIZE,
            recommendSongs = discoveryCache.getCachedRecommend().orEmpty(),
        )
        loadHomeContent()
    }

    private suspend fun awaitToken() {
        if (!cookieManager.isLoggedIn() && !cookieManager.hasUsableToken()) {
            cookieManager.tokenReady.await()
        }
    }

    private suspend fun <T> timedRequest(block: suspend () -> Result<T>): Result<T> = try {
        withTimeout(REQUEST_TIMEOUT_MS) {
            awaitToken()
            block()
        }
    } catch (e: TimeoutCancellationException) {
        Result.failure(Exception("请求超时，请检查网络后重试", e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun loadHomeContent(forceRefresh: Boolean = false) {
        loadRecommendedPlaylists(forceRefresh)
        loadCharts(forceRefresh)
        loadNewAlbums(forceRefresh)
        loadArtists(forceRefresh = forceRefresh)
        loadPodcasts(forceRefresh = forceRefresh)
        if (cookieManager.isLoggedIn()) loadRecommend(forceRefresh)
    }

    fun setSourceMode(mode: SourceMode) {
        val current = _uiState.value.sourceMode
        markCurrentSeen(current)
        if (mode == current) return
        detailRequestId++
        _uiState.value = _uiState.value.copy(
            previousSourceMode = current,
            sourceMode = mode,
            gridColumns = settingsManager.getGridColumns(gridPageKey(mode)),
            songs = emptyList(),
            selectedIds = emptySet(),
            playlistName = "",
            sourceLabel = "",
            collectionDetail = null,
            activePodcast = null,
            statusMessage = null,
        )
        loadSourceIfNeeded(mode)
    }

    private fun loadSourceIfNeeded(mode: SourceMode) {
        when (mode) {
            SourceMode.RECOMMEND -> loadHomeContent()
            SourceMode.CHARTS -> loadCharts()
            SourceMode.PLAYLISTS -> loadPlaylists()
            SourceMode.PODCASTS -> loadPodcasts()
            SourceMode.ARTISTS -> loadArtists()
            SourceMode.NEW_ALBUMS -> loadNewAlbums()
            SourceMode.NEW_SONGS -> loadNewSongs()
            SourceMode.STYLE_PLAYLISTS -> loadStyleTags()
            SourceMode.PERSONAL_FM -> loadFmSongs()
            else -> Unit
        }
    }

    private fun refreshCurrentSource(mode: SourceMode) {
        when (mode) {
            SourceMode.RECOMMEND -> loadHomeContent(forceRefresh = true)
            SourceMode.CHARTS -> loadCharts(forceRefresh = true)
            SourceMode.PLAYLISTS -> loadPlaylists(forceRefresh = true)
            SourceMode.PODCASTS -> loadPodcasts(forceRefresh = true)
            SourceMode.ARTISTS -> loadArtists(forceRefresh = true)
            SourceMode.NEW_ALBUMS -> loadNewAlbums(forceRefresh = true)
            SourceMode.NEW_SONGS -> loadNewSongs(forceRefresh = true)
            SourceMode.STYLE_PLAYLISTS -> loadStyleTags(forceRefresh = true)
            SourceMode.PERSONAL_FM -> loadFmSongs()
            else -> Unit
        }
    }

    fun refreshCurrentSource() = refreshCurrentSource(_uiState.value.sourceMode)

    fun loadRecommendedPlaylists(forceRefresh: Boolean = false) {
        val state = _uiState.value
        if (state.isLoadingRecommendations || (!forceRefresh && state.recommendedPlaylists.isNotEmpty())) return
        _uiState.value = state.copy(isLoadingRecommendations = true, statusMessage = null)
        viewModelScope.launch {
            timedRequest { repository.getRecommendedPlaylists() }.fold(
                onSuccess = { playlists ->
                    _uiState.value = _uiState.value.copy(
                        recommendedPlaylists = playlists,
                        isLoadingRecommendations = false,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingRecommendations = false,
                        statusMessage = "获取推荐失败: ${error.message}",
                    )
                },
            )
        }
    }

    fun loadCharts(forceRefresh: Boolean = false) {
        val state = _uiState.value
        if (!forceRefresh && discoveryCache.isChartsCacheValid() && state.chartList.isNotEmpty()) return
        if (state.isLoadingCharts) return
        _uiState.value = state.copy(isLoadingCharts = true, statusMessage = null)
        viewModelScope.launch {
            timedRequest { repository.getChartList() }.fold(
                onSuccess = { charts ->
                    _uiState.value = _uiState.value.copy(chartList = charts, isLoadingCharts = false)
                    if (charts.isNotEmpty()) discoveryCache.saveCharts(charts)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingCharts = false,
                        statusMessage = "获取榜单失败: ${error.message}",
                    )
                },
            )
        }
    }

    fun loadChartSongs(chartId: Long, chartName: String) =
        loadChartSongs(ChartInfo(id = chartId, name = chartName, coverUrl = ""))

    fun loadChartSongs(chart: ChartInfo) {
        retryDetail = { loadChartSongs(chart) }
        val initialDetail = CollectionDetail(
            kind = CollectionKind.CHART,
            id = chart.id,
            title = chart.name,
            coverUrl = chart.coverUrl,
            description = chart.description,
            expectedItemCount = chart.trackCount,
            playCount = chart.playCount,
            updateTime = chart.updateTime,
            updateFrequency = chart.updateFrequency,
        )
        val requestId = beginDetail(_uiState.value.sourceMode, "榜单", chart.name, initialDetail)
        viewModelScope.launch {
            timedRequest { repository.getChartSongs(chart.id) }.fold(
                onSuccess = { result ->
                    if (!isCurrentDetailRequest(requestId)) return@fold
                    val detail = initialDetail.copy(
                        title = result.chartName.ifBlank { initialDetail.title },
                        coverUrl = result.coverUrl.ifBlank { initialDetail.coverUrl },
                        description = result.description.ifBlank { initialDetail.description },
                        expectedItemCount = result.trackCount.takeIf { it > 0 }
                            ?: initialDetail.expectedItemCount,
                        playCount = result.playCount.takeIf { it > 0 } ?: initialDetail.playCount,
                        updateTime = result.updateTime.takeIf { it > 0 } ?: initialDetail.updateTime,
                        updateFrequency = result.updateFrequency.ifBlank { initialDetail.updateFrequency },
                    )
                    showSongs(result.songs, detail.title, detail)
                },
                onFailure = { if (isCurrentDetailRequest(requestId)) failDetail("加载榜单失败", it) },
            )
        }
    }

    fun loadRecommend(forceRefresh: Boolean = false) {
        val state = _uiState.value
        if (!cookieManager.isLoggedIn()) return
        if (!forceRefresh && discoveryCache.isRecommendCacheValid() && state.recommendSongs.isNotEmpty()) return
        if (!forceRefresh && state.recommendSongs.isNotEmpty()) return
        viewModelScope.launch {
            timedRequest { repository.getDailyRecommend() }.fold(
                onSuccess = { songs ->
                    _uiState.value = _uiState.value.copy(recommendSongs = songs)
                    if (songs.isNotEmpty()) discoveryCache.saveRecommend(songs)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(statusMessage = "每日推荐加载失败: ${error.message}")
                },
            )
        }
    }

    fun loadRecommendSongsToPlaylist() {
        val songs = _uiState.value.recommendSongs
        if (songs.isEmpty()) return
        val detail = CollectionDetail(
            kind = CollectionKind.PLAYLIST,
            title = "每日推荐",
            subtitle = "根据你的音乐口味生成",
            coverUrl = songs.firstOrNull()?.coverUrl.orEmpty(),
            expectedItemCount = songs.size,
        )
        beginDetail(SourceMode.RECOMMEND, "推荐", "每日推荐", detail)
        showSongs(songs, "每日推荐", detail)
    }

    fun loadPlaylistSongs(playlist: DiscoveryPlaylist) {
        retryDetail = { loadPlaylistSongs(playlist) }
        val initialDetail = playlist.toCollectionDetail()
        val requestId = beginDetail(_uiState.value.sourceMode, "歌单", playlist.name, initialDetail)
        viewModelScope.launch {
            timedRequest { repository.getPlaylistDetail(playlist.id.toString()) }.fold(
                onSuccess = { if (isCurrentDetailRequest(requestId)) showCollection(it) },
                onFailure = { if (isCurrentDetailRequest(requestId)) failDetail("加载歌单失败", it) },
            )
        }
    }

    fun setPlaylistCategory(category: PlaylistCategory) {
        if (category == _uiState.value.playlistCategory && _uiState.value.playlistTag == null) return
        _uiState.value = _uiState.value.copy(
            playlistCategory = category,
            playlistTag = null,
            discoveryPlaylists = emptyList(),
            playlistOffset = 0,
            playlistsHasMore = true,
        )
        loadPlaylists(forceRefresh = true)
    }

    /** 选中曲风标签，进入该曲风的歌单列表 */
    fun selectStyleTag(tag: StyleTag) {
        val current = _uiState.value
        if (current.playlistTag == tag.name) return
        markCurrentSeen(current.sourceMode)
        detailRequestId++
        _uiState.value = _uiState.value.copy(
            previousSourceMode = current.sourceMode,
            sourceMode = SourceMode.PLAYLISTS,
            playlistTag = tag.name,
            songs = emptyList(),
            selectedIds = emptySet(),
            playlistName = "",
            sourceLabel = "",
            collectionDetail = null,
            activePodcast = null,
            discoveryPlaylists = emptyList(),
            playlistOffset = 0,
            playlistsHasMore = true,
            statusMessage = null,
        )
        loadPlaylists(forceRefresh = true)
    }

    fun loadPlaylists(forceRefresh: Boolean = false) {
        val state = _uiState.value
        if (state.isLoadingPlaylists || (!forceRefresh && state.discoveryPlaylists.isNotEmpty())) return
        _uiState.value = state.copy(
            isLoadingPlaylists = true,
            statusMessage = null,
            playlistOffset = if (forceRefresh) 0 else state.playlistOffset,
        )
        val tag = state.playlistTag
        viewModelScope.launch {
            timedRequest {
                if (tag != null) repository.getDiscoveryPlaylistsByTag(tag, PAGE_SIZE, 0)
                else repository.getDiscoveryPlaylists(state.playlistCategory, PAGE_SIZE, 0)
            }.fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        discoveryPlaylists = page.items,
                        playlistOffset = page.nextOffset,
                        playlistsHasMore = page.hasMore,
                        isLoadingPlaylists = false,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingPlaylists = false,
                        statusMessage = "获取歌单失败: ${error.message}",
                    )
                },
            )
        }
    }

    /** 新歌速递：areaId 0全部 7华语 96欧美 8日本 16韩国 */
    fun setNewSongsArea(area: Int) {
        if (area == _uiState.value.newSongsArea) return
        _uiState.value = _uiState.value.copy(
            newSongsArea = area,
            newSongs = emptyList(),
            selectedIds = emptySet(),
        )
        loadNewSongs(forceRefresh = true)
    }

    fun loadNewSongs(forceRefresh: Boolean = false) {
        val state = _uiState.value
        if (state.isLoadingNewSongs || (!forceRefresh && state.newSongs.isNotEmpty())) return
        _uiState.value = state.copy(isLoadingNewSongs = true, statusMessage = null)
        val area = state.newSongsArea
        viewModelScope.launch {
            timedRequest { repository.getNewSongs(area) }.fold(
                onSuccess = { songs ->
                    _uiState.value = _uiState.value.copy(
                        newSongs = songs,
                        isLoadingNewSongs = false,
                        selectedIds = emptySet(),
                        statusMessage = if (songs.isEmpty()) "暂无新歌" else null,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingNewSongs = false,
                        statusMessage = "获取新歌失败: ${error.message}",
                    )
                },
            )
        }
    }

    fun loadStyleTags(forceRefresh: Boolean = false) {
        val state = _uiState.value
        if (state.isLoadingStyleTags || (!forceRefresh && state.styleTags.isNotEmpty())) return
        _uiState.value = state.copy(isLoadingStyleTags = true, statusMessage = null)
        viewModelScope.launch {
            timedRequest { repository.getStyleTags() }.fold(
                onSuccess = { tags ->
                    _uiState.value = _uiState.value.copy(
                        styleTags = tags,
                        isLoadingStyleTags = false,
                        statusMessage = if (tags.isEmpty()) "暂无曲风标签" else null,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingStyleTags = false,
                        statusMessage = "获取曲风失败: ${error.message}",
                    )
                },
            )
        }
    }

    fun loadPodcasts(forceRefresh: Boolean = false) {
        val state = _uiState.value
        if (state.isLoadingPodcasts || (!forceRefresh && state.podcastChannels.isNotEmpty())) return
        _uiState.value = state.copy(isLoadingPodcasts = true, statusMessage = null)
        viewModelScope.launch {
            timedRequest { repository.getDiscoveryPodcasts(PAGE_SIZE, 0) }.fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        podcastChannels = page.items,
                        podcastOffset = page.nextOffset,
                        podcastsHasMore = page.hasMore,
                        isLoadingPodcasts = false,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingPodcasts = false,
                        statusMessage = "获取播客失败: ${error.message}",
                    )
                },
            )
        }
    }

    fun loadPodcastPrograms(channel: PodcastChannel) {
        retryDetail = { loadPodcastPrograms(channel) }
        val initialDetail = channel.toCollectionDetail()
        val requestId = beginDetail(SourceMode.PODCASTS, "播客", channel.name, initialDetail)
        _uiState.value = _uiState.value.copy(activePodcast = channel)
        viewModelScope.launch {
            val detail = runCatching { repository.getPodcastDetail(channel.id).getOrNull() }.getOrNull()
            if (detail != null && isCurrentDetailRequest(requestId)) {
                _uiState.value = _uiState.value.copy(
                    activePodcast = detail,
                    collectionDetail = detail.toCollectionDetail(),
                )
            }
        }
        viewModelScope.launch {
            timedRequest { repository.getPodcastPrograms(channel) }.fold(
                onSuccess = { page ->
                    if (!isCurrentDetailRequest(requestId)) return@fold
                    _uiState.value = _uiState.value.copy(
                        songs = page.items,
                        selectedIds = emptySet(),
                        playlistName = channel.name,
                        detailOffset = page.nextOffset,
                        detailHasMore = page.hasMore,
                        isLoading = false,
                        statusMessage = if (page.items.isEmpty()) "这个播客暂时没有可下载节目" else null,
                    )
                },
                onFailure = { if (isCurrentDetailRequest(requestId)) failDetail("加载播客节目失败", it) },
            )
        }
    }

    fun loadArtists(forceRefresh: Boolean = false) {
        val state = _uiState.value
        if (state.isLoadingArtists || (!forceRefresh && state.artists.isNotEmpty())) return
        val area = state.artistArea
        _uiState.value = state.copy(isLoadingArtists = true, statusMessage = null)
        viewModelScope.launch {
            timedRequest { repository.getDiscoveryArtists(area, PAGE_SIZE, 0) }.fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        artists = page.items,
                        artistOffset = page.nextOffset,
                        artistsHasMore = page.hasMore,
                        isLoadingArtists = false,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingArtists = false,
                        statusMessage = "获取歌手失败: ${error.message}",
                    )
                },
            )
        }
    }

    fun setArtistArea(area: ArtistArea) {
        if (area == _uiState.value.artistArea) return
        _uiState.value = _uiState.value.copy(
            artistArea = area,
            artists = emptyList(),
            artistOffset = 0,
            artistsHasMore = true,
        )
        loadArtists(forceRefresh = true)
    }

    fun loadArtistDetail(
        artist: ArtistResult,
        parentSourceMode: SourceMode? = null,
    ) {
        val previous = _uiState.value
        val parent = parentSourceMode ?: if (previous.sourceMode == SourceMode.ARTIST_DETAIL) {
            previous.artistParentSourceMode
        } else {
            previous.sourceMode
        }
        retryDetail = { loadArtistDetail(artist, parent) }
        val requestId = beginDetail(parent, "歌手", artist.name)
        _uiState.value = _uiState.value.copy(
            sourceMode = SourceMode.ARTIST_DETAIL,
            artistParentSourceMode = parent,
            activeArtist = artist,
            artistProfile = artist.toFallbackProfile(),
            artistDetailSection = ArtistDetailSection.HOT_SONGS,
            artistDetailSongs = emptyList(),
            artistDetailAlbums = emptyList(),
            artistAlbumOffset = 0,
            artistAlbumsHasMore = true,
            artistAlbumsLoaded = false,
            artistAbout = ArtistAbout(),
            artistAboutLoaded = false,
            isLoadingArtistAlbums = false,
            isLoadingArtistAbout = false,
            artistSectionMessage = null,
        )
        viewModelScope.launch {
            val (profileResult, songsResult) = coroutineScope {
                val profile = async { timedRequest { repository.getArtistProfile(artist.id) } }
                val songs = async { timedRequest { repository.getArtistTopSongs(artist.id) } }
                profile.await() to songs.await()
            }
            if (!isCurrentDetailRequest(requestId)) return@launch
            val songs = songsResult.getOrDefault(emptyList()).distinctBy { it.id }
            _uiState.value = _uiState.value.copy(
                artistProfile = profileResult.getOrElse { artist.toFallbackProfile() },
                songs = songs,
                artistDetailSongs = songs,
                selectedIds = emptySet(),
                playlistName = artist.name,
                isLoading = false,
                statusMessage = when {
                    songsResult.isFailure -> "热门歌曲加载失败: ${songsResult.exceptionOrNull()?.message}"
                    songs.isEmpty() -> "暂无可下载歌曲"
                    else -> null
                },
            )
        }
    }

    fun loadArtistDetail(artistId: Long, artistName: String) {
        loadArtistDetail(ArtistResult(id = artistId, name = artistName))
    }

    fun setArtistDetailSection(section: ArtistDetailSection) {
        val state = _uiState.value
        if (!state.isArtistPage || state.artistDetailSection == section) return
        _uiState.value = state.copy(artistDetailSection = section, artistSectionMessage = null)
        when (section) {
            ArtistDetailSection.HOT_SONGS -> Unit
            ArtistDetailSection.ALBUMS -> if (!state.artistAlbumsLoaded) loadArtistAlbums()
            ArtistDetailSection.ABOUT -> if (!state.artistAboutLoaded) loadArtistAbout()
        }
    }

    fun loadMoreArtistSection() {
        when (_uiState.value.artistDetailSection) {
            ArtistDetailSection.ALBUMS -> loadArtistAlbums()
            else -> Unit
        }
    }

    private fun loadArtistAlbums(reset: Boolean = false) {
        val state = _uiState.value
        val artist = state.activeArtist ?: return
        if (state.isLoadingArtistAlbums || (!reset && state.artistAlbumsLoaded && !state.artistAlbumsHasMore)) return
        val offset = if (reset) 0 else state.artistAlbumOffset
        val requestId = detailRequestId
        _uiState.value = state.copy(isLoadingArtistAlbums = true, artistSectionMessage = null)
        viewModelScope.launch {
            timedRequest { repository.getArtistAlbumsPage(artist.id, ARTIST_ALBUM_PAGE_SIZE, offset) }.fold(
                onSuccess = { page ->
                    if (!isCurrentDetailRequest(requestId)) return@fold
                    val current = _uiState.value
                    _uiState.value = current.copy(
                        artistDetailAlbums = AlbumResultDeduplicator.canonicalize(
                            if (reset) page.items else current.artistDetailAlbums + page.items,
                        ),
                        artistAlbumOffset = page.nextOffset,
                        artistAlbumsHasMore = page.hasMore,
                        artistAlbumsLoaded = true,
                        isLoadingArtistAlbums = false,
                        artistSectionMessage = if (current.artistDetailSection == ArtistDetailSection.ALBUMS) {
                            if (page.items.isEmpty() && offset == 0) "暂无专辑" else null
                        } else {
                            current.artistSectionMessage
                        },
                    )
                },
                onFailure = { error ->
                    if (!isCurrentDetailRequest(requestId)) return@fold
                    val current = _uiState.value
                    _uiState.value = current.copy(
                        isLoadingArtistAlbums = false,
                        artistSectionMessage = if (current.artistDetailSection == ArtistDetailSection.ALBUMS) {
                            "专辑加载失败: ${error.message}"
                        } else {
                            current.artistSectionMessage
                        },
                    )
                },
            )
        }
    }

    private fun loadArtistAbout() {
        val state = _uiState.value
        val artist = state.activeArtist ?: return
        if (state.isLoadingArtistAbout) return
        val requestId = detailRequestId
        _uiState.value = state.copy(isLoadingArtistAbout = true, artistSectionMessage = null)
        viewModelScope.launch {
            val (aboutResult, similarResult) = coroutineScope {
                val about = async { timedRequest { repository.getArtistIntroduction(artist.id) } }
                val similar = async { timedRequest { repository.getSimilarArtists(artist.id) } }
                about.await() to similar.await()
            }
            if (!isCurrentDetailRequest(requestId)) return@launch
            val profileBrief = _uiState.value.artistProfile?.briefDescription.orEmpty()
            val about = aboutResult.getOrDefault(ArtistAbout(briefDescription = profileBrief))
            val similarError = similarResult.exceptionOrNull()
            val current = _uiState.value
            _uiState.value = current.copy(
                artistAbout = about.copy(
                    briefDescription = about.briefDescription.ifBlank { profileBrief },
                    similarArtists = similarResult.getOrDefault(emptyList()),
                    similarArtistsMessage = when (similarError) {
                        is ArtistAuthenticationRequiredException -> similarError.message
                        null -> if (similarResult.getOrDefault(emptyList()).isEmpty()) "暂无相似歌手" else null
                        else -> "相似歌手加载失败: ${similarError.message}"
                    },
                ),
                artistAboutLoaded = aboutResult.isSuccess,
                isLoadingArtistAbout = false,
                artistSectionMessage = if (current.artistDetailSection == ArtistDetailSection.ABOUT) {
                    aboutResult.exceptionOrNull()?.let { "简介加载失败: ${it.message}" }
                } else {
                    current.artistSectionMessage
                },
            )
        }
    }

    fun loadNewAlbums(forceRefresh: Boolean = false) {
        val state = _uiState.value
        if (!forceRefresh && discoveryCache.isNewAlbumsCacheValid() && state.newAlbumsList.isNotEmpty()) return
        if (state.isLoadingAlbums) return
        _uiState.value = state.copy(isLoadingAlbums = true, statusMessage = null)
        viewModelScope.launch {
            timedRequest { repository.getNewAlbums(ALBUM_PAGE_SIZE, 0) }.fold(
                onSuccess = { albums ->
                    _uiState.value = _uiState.value.copy(
                        newAlbumsList = albums,
                        newAlbumsOffset = albums.size,
                        newAlbumsHasMore = albums.size >= ALBUM_PAGE_SIZE,
                        isLoadingAlbums = false,
                    )
                    if (albums.isNotEmpty()) discoveryCache.saveNewAlbums(albums)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingAlbums = false,
                        statusMessage = "获取新碟失败: ${error.message}",
                    )
                },
            )
        }
    }

    fun loadAlbumFromResult(album: AlbumResult) {
        retryDetail = { loadAlbumFromResult(album) }
        val initialDetail = CollectionDetail(
            kind = CollectionKind.ALBUM,
            id = album.id,
            title = album.name,
            subtitle = album.artist,
            coverUrl = album.coverUrl,
            publishTime = album.publishTime,
        )
        val requestId = beginDetail(_uiState.value.sourceMode, "专辑", album.name, initialDetail)
        viewModelScope.launch {
            timedRequest { repository.getAlbumDetail(album.id.toString()) }.fold(
                onSuccess = { if (isCurrentDetailRequest(requestId)) showCollection(it) },
                onFailure = { if (isCurrentDetailRequest(requestId)) failDetail("加载专辑失败", it) },
            )
        }
    }

    fun loadMoreCurrentSource() {
        val state = _uiState.value
        if (state.isLoadingMore) return
        when (state.sourceMode) {
            SourceMode.PLAYLISTS -> loadMorePlaylists(state)
            SourceMode.PODCASTS -> loadMorePodcasts(state)
            SourceMode.ARTISTS -> loadMoreArtists(state)
            SourceMode.NEW_ALBUMS -> loadMoreAlbums(state)
            else -> Unit
        }
    }

    private fun loadMorePlaylists(state: DiscoverUiState) {
        if (!state.playlistsHasMore) return
        _uiState.value = state.copy(isLoadingMore = true)
        val tag = state.playlistTag
        viewModelScope.launch {
            timedRequest {
                if (tag != null) repository.getDiscoveryPlaylistsByTag(tag, PAGE_SIZE, state.playlistOffset)
                else repository.getDiscoveryPlaylists(state.playlistCategory, PAGE_SIZE, state.playlistOffset)
            }.fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        discoveryPlaylists = mergeById(state.discoveryPlaylists, page.items) { it.id },
                        playlistOffset = page.nextOffset,
                        playlistsHasMore = page.hasMore,
                        isLoadingMore = false,
                    )
                },
                onFailure = { loadMoreFailure(it) },
            )
        }
    }

    private fun loadMorePodcasts(state: DiscoverUiState) {
        if (!state.podcastsHasMore) return
        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            timedRequest { repository.getDiscoveryPodcasts(PAGE_SIZE, state.podcastOffset) }.fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        podcastChannels = mergeById(state.podcastChannels, page.items) { it.id },
                        podcastOffset = page.nextOffset,
                        podcastsHasMore = page.hasMore,
                        isLoadingMore = false,
                    )
                },
                onFailure = { loadMoreFailure(it) },
            )
        }
    }

    private fun loadMoreArtists(state: DiscoverUiState) {
        if (!state.artistsHasMore) return
        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            timedRequest { repository.getDiscoveryArtists(state.artistArea, PAGE_SIZE, state.artistOffset) }.fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        artists = mergeById(state.artists, page.items) { it.id },
                        artistOffset = page.nextOffset,
                        artistsHasMore = page.hasMore,
                        isLoadingMore = false,
                    )
                },
                onFailure = { loadMoreFailure(it) },
            )
        }
    }

    private fun loadMoreAlbums(state: DiscoverUiState) {
        if (!state.newAlbumsHasMore) return
        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            timedRequest { repository.getNewAlbums(ALBUM_PAGE_SIZE, state.newAlbumsOffset) }.fold(
                onSuccess = { albums ->
                    _uiState.value = _uiState.value.copy(
                        newAlbumsList = mergeById(state.newAlbumsList, albums) { it.id },
                        newAlbumsOffset = state.newAlbumsOffset + albums.size,
                        newAlbumsHasMore = albums.size >= ALBUM_PAGE_SIZE,
                        isLoadingMore = false,
                    )
                },
                onFailure = { loadMoreFailure(it) },
            )
        }
    }

    fun loadMorePodcastPrograms() {
        val state = _uiState.value
        val channel = state.activePodcast ?: return
        if (state.isLoadingMoreDetail || !state.detailHasMore) return
        _uiState.value = state.copy(isLoadingMoreDetail = true)
        viewModelScope.launch {
            timedRequest { repository.getPodcastPrograms(channel, PAGE_SIZE, state.detailOffset) }.fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        songs = mergeById(state.songs, page.items) { it.id },
                        detailOffset = page.nextOffset,
                        detailHasMore = page.hasMore,
                        isLoadingMoreDetail = false,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMoreDetail = false,
                        statusMessage = "加载更多节目失败: ${error.message}",
                    )
                },
            )
        }
    }

    fun loadHotSearch(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value.hotSearchKeywords.isNotEmpty()) return
        viewModelScope.launch {
            timedRequest { repository.getHotSearch() }.onSuccess { keywords ->
                _uiState.value = _uiState.value.copy(hotSearchKeywords = keywords)
                discoveryCache.saveHotSearch(keywords)
            }
        }
    }

    fun loadFmSongs() {
        if (_uiState.value.isLoadingFm) return
        _uiState.value = _uiState.value.copy(
            isLoadingFm = true,
            fmSongs = emptyList(),
            fmHistoryGroups = emptyList(),
            fmGroupCount = 0,
        )
        viewModelScope.launch {
            timedRequest { repository.getPersonalFm(10) }.fold(
                onSuccess = { songs ->
                    _uiState.value = _uiState.value.copy(
                        fmSongs = songs,
                        fmHistoryGroups = listOf(songs),
                        fmGroupCount = 1,
                        isLoadingFm = false,
                        statusMessage = null,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingFm = false,
                        statusMessage = "FM 加载失败: ${error.message}",
                    )
                },
            )
        }
    }

    fun loadNextFmGroup() {
        val state = _uiState.value
        if (state.isLoadingFm) return
        _uiState.value = state.copy(isLoadingFm = true)
        viewModelScope.launch {
            timedRequest { repository.getPersonalFm(10) }.fold(
                onSuccess = { songs ->
                    val history = (state.fmHistoryGroups + listOf(songs)).takeLast(state.fmThreshold)
                    _uiState.value = _uiState.value.copy(
                        fmSongs = history.flatten().distinctBy { it.id },
                        fmHistoryGroups = history,
                        fmGroupCount = history.size,
                        isLoadingFm = false,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingFm = false,
                        statusMessage = "FM 加载失败: ${error.message}",
                    )
                },
            )
        }
    }

    fun toggleSongSelection(songId: Long) {
        val selected = _uiState.value.selectedIds
        _uiState.value = _uiState.value.copy(
            selectedIds = if (songId in selected) selected - songId else selected + songId,
        )
    }

    fun toggleSelectAll() {
        val state = _uiState.value
        val all = selectableSongs(state).map { it.id }.toSet()
        val allSelected = all.isNotEmpty() && state.selectedIds.containsAll(all)
        _uiState.value = state.copy(selectedIds = if (allSelected) emptySet() else all)
    }

    fun setGridColumns(columns: Int) {
        if (columns !in 1..4) return
        val page = gridPageKey(_uiState.value.sourceMode)
        settingsManager.setGridColumns(page, columns)
        _uiState.value = _uiState.value.copy(gridColumns = columns)
    }

    private fun gridPageKey(mode: SourceMode): String = when (mode) {
        SourceMode.PLAYLISTS -> SettingsManager.GridPage.PLAYLISTS
        SourceMode.PODCASTS -> SettingsManager.GridPage.PODCASTS
        SourceMode.NEW_ALBUMS -> SettingsManager.GridPage.NEW_ALBUMS
        else -> SettingsManager.GridPage.DEFAULT
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    fun retry() {
        val state = _uiState.value
        when {
            state.isArtistPage -> when (state.artistDetailSection) {
                ArtistDetailSection.HOT_SONGS -> state.activeArtist?.let {
                    loadArtistDetail(it, state.artistParentSourceMode)
                }
                ArtistDetailSection.ALBUMS -> loadArtistAlbums()
                ArtistDetailSection.ABOUT -> loadArtistAbout()
            }
            state.sourceLabel.isNotBlank() -> retryDetail?.invoke()
            else -> refreshCurrentSource()
        }
    }

    fun resetToHome() {
        detailRequestId++
        _uiState.value = _uiState.value.copy(
            sourceMode = SourceMode.RECOMMEND,
            previousSourceMode = SourceMode.RECOMMEND,
            preSongSourceMode = SourceMode.RECOMMEND,
            songs = emptyList(),
            fmSongs = emptyList(),
            fmHistoryGroups = emptyList(),
            fmGroupCount = 0,
            selectedIds = emptySet(),
            playlistName = "",
            sourceLabel = "",
            activeArtist = null,
            artistProfile = null,
            artistParentSourceMode = SourceMode.ARTISTS,
            artistDetailSection = ArtistDetailSection.HOT_SONGS,
            artistDetailSongs = emptyList(),
            artistDetailAlbums = emptyList(),
            artistAlbumOffset = 0,
            artistAlbumsHasMore = true,
            artistAlbumsLoaded = false,
            artistAbout = ArtistAbout(),
            artistAboutLoaded = false,
            isLoadingArtistAlbums = false,
            isLoadingArtistAbout = false,
            artistSectionMessage = null,
            activePodcast = null,
            collectionDetail = null,
            detailOffset = 0,
            detailHasMore = false,
            isLoading = false,
            isLoadingFm = false,
            statusMessage = null,
        )
        retryDetail = null
    }

    fun clearPrivateContent() {
        detailRequestId++
        _uiState.value = _uiState.value.copy(
            sourceMode = SourceMode.RECOMMEND,
            previousSourceMode = SourceMode.RECOMMEND,
            preSongSourceMode = SourceMode.RECOMMEND,
            songs = emptyList(),
            recommendSongs = emptyList(),
            fmSongs = emptyList(),
            fmHistoryGroups = emptyList(),
            fmGroupCount = 0,
            selectedIds = emptySet(),
            playlistName = "",
            sourceLabel = "",
            activeArtist = null,
            artistProfile = null,
            artistParentSourceMode = SourceMode.ARTISTS,
            artistDetailSection = ArtistDetailSection.HOT_SONGS,
            artistDetailSongs = emptyList(),
            artistDetailAlbums = emptyList(),
            artistAlbumOffset = 0,
            artistAlbumsHasMore = true,
            artistAlbumsLoaded = false,
            artistAbout = ArtistAbout(),
            artistAboutLoaded = false,
            isLoadingArtistAlbums = false,
            isLoadingArtistAbout = false,
            artistSectionMessage = null,
            activePodcast = null,
            collectionDetail = null,
            isLoading = false,
            isLoadingFm = false,
            statusMessage = null,
        )
        retryDetail = null
    }

    fun goBack() {
        detailRequestId++
        val state = _uiState.value
        if (
            state.sourceMode == SourceMode.ARTIST_DETAIL &&
            state.sourceLabel == "专辑" &&
            state.activeArtist != null
        ) {
            _uiState.value = state.copy(
                songs = state.artistDetailSongs,
                selectedIds = emptySet(),
                playlistName = state.activeArtist.name,
                sourceLabel = "歌手",
                collectionDetail = null,
                detailOffset = 0,
                detailHasMore = false,
                isLoading = false,
                statusMessage = null,
            )
            retryDetail = { loadArtistDetail(state.activeArtist, state.artistParentSourceMode) }
        } else if (state.isArtistPage || (state.sourceMode == SourceMode.ARTIST_DETAIL && state.isLoading)) {
            val destination = state.artistParentSourceMode.takeUnless { it == SourceMode.SEARCH }
                ?: SourceMode.RECOMMEND
            _uiState.value = state.copy(
                sourceMode = destination,
                previousSourceMode = SourceMode.ARTIST_DETAIL,
                songs = emptyList(),
                selectedIds = emptySet(),
                playlistName = "",
                sourceLabel = "",
                collectionDetail = null,
                activeArtist = null,
                artistProfile = null,
                artistDetailSection = ArtistDetailSection.HOT_SONGS,
                artistDetailSongs = emptyList(),
                artistDetailAlbums = emptyList(),
                artistAlbumOffset = 0,
                artistAlbumsHasMore = true,
                artistAlbumsLoaded = false,
                artistAbout = ArtistAbout(),
                artistAboutLoaded = false,
                isLoadingArtistAlbums = false,
                isLoadingArtistAbout = false,
                artistSectionMessage = null,
                detailOffset = 0,
                detailHasMore = false,
                isLoading = false,
                statusMessage = null,
            )
            retryDetail = null
        } else if (state.isDetailView || state.isLoading) {
            _uiState.value = state.copy(
                songs = emptyList(),
                selectedIds = emptySet(),
                playlistName = "",
                sourceLabel = "",
                collectionDetail = null,
                activePodcast = null,
                detailOffset = 0,
                detailHasMore = false,
                isLoading = false,
                statusMessage = null,
                sourceMode = state.preSongSourceMode,
                previousSourceMode = state.sourceMode,
            )
            retryDetail = null
        } else if (state.sourceMode != SourceMode.RECOMMEND) {
            _uiState.value = state.copy(
                previousSourceMode = state.sourceMode,
                sourceMode = SourceMode.RECOMMEND,
                statusMessage = null,
            )
        }
    }

    fun downloadSelected() {
        val state = _uiState.value
        if (state.isPreparingDownloads) return
        val songs = selectableSongs(state).filter { it.id in state.selectedIds }
        if (songs.isEmpty()) {
            _uiState.value = state.copy(statusMessage = "请至少选择一首歌曲")
            return
        }
        _uiState.value = state.copy(isPreparingDownloads = true, statusMessage = "正在获取下载链接...")
        viewModelScope.launch {
            try {
                awaitToken()
                val options = DownloadPreparation.optionsFrom(
                    settings = settingsManager,
                    quality = if (state.sourceLabel == "播客") "standard" else settingsManager.quality,
                    cookie = cookieManager.getRawCookies(),
                    groupName = state.playlistName.ifBlank { state.sourceLabel },
                ).let { configured ->
                    if (state.sourceLabel == "播客") {
                        configured.copy(
                            downloadLyrics = false,
                            embedLyrics = false,
                            saveTlLrc = false,
                            saveRomaLrc = false,
                            saveYrc = false,
                        )
                    } else {
                        configured
                    }
                }
                val result = DownloadPreparation.enqueueSongs(
                    songs = songs,
                    repository = repository,
                    settings = settingsManager,
                    engine = downloadEngine,
                    options = options,
                )
                _uiState.value = _uiState.value.copy(
                    isPreparingDownloads = false,
                    selectedIds = if (result.added > 0) emptySet() else _uiState.value.selectedIds,
                    statusMessage = result.message,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("[DiscoverVM] preparing downloads failed", e)
                _uiState.value = _uiState.value.copy(
                    isPreparingDownloads = false,
                    statusMessage = "准备下载失败: ${e.message}",
                )
            }
        }
    }

    private fun beginDetail(
        source: SourceMode,
        label: String,
        name: String,
        detail: CollectionDetail? = null,
    ): Long {
        val requestId = ++detailRequestId
        _uiState.value = _uiState.value.copy(
            preSongSourceMode = source,
            isLoading = true,
            songs = emptyList(),
            selectedIds = emptySet(),
            playlistName = name,
            sourceLabel = label,
            collectionDetail = detail,
            activePodcast = null,
            detailOffset = 0,
            detailHasMore = false,
            statusMessage = null,
        )
        return requestId
    }

    private fun isCurrentDetailRequest(requestId: Long): Boolean = requestId == detailRequestId

    private fun showSongs(songs: List<Song>, name: String, detail: CollectionDetail? = null) {
        val uniqueSongs = songs.distinctBy { it.id }
        _uiState.value = _uiState.value.copy(
            songs = uniqueSongs,
            selectedIds = emptySet(),
            playlistName = name,
            collectionDetail = detail ?: _uiState.value.collectionDetail?.copy(
                expectedItemCount = _uiState.value.collectionDetail?.expectedItemCount
                    ?.takeIf { it > 0 }
                    ?: uniqueSongs.size,
            ),
            isLoading = false,
            statusMessage = if (songs.isEmpty()) "暂无可下载歌曲" else null,
        )
    }

    private fun showCollection(content: CollectionContent) {
        showSongs(content.songs, content.detail.title, content.detail)
    }

    private fun failDetail(prefix: String, error: Throwable) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            statusMessage = "$prefix: ${error.message}",
        )
    }

    private fun loadMoreFailure(error: Throwable) {
        _uiState.value = _uiState.value.copy(
            isLoadingMore = false,
            statusMessage = "加载更多失败: ${error.message}",
        )
    }

    private fun selectableSongs(state: DiscoverUiState): List<Song> = when (state.sourceMode) {
        SourceMode.PERSONAL_FM -> state.fmSongs
        SourceMode.NEW_SONGS -> state.newSongs
        else -> state.songs
    }

    private fun markCurrentSeen(mode: SourceMode) {
        when (mode) {
            SourceMode.CHARTS -> seenManager.markAllSeenCharts(_uiState.value.chartList.map { it.id })
            SourceMode.NEW_ALBUMS -> seenManager.markAllSeenAlbums(_uiState.value.newAlbumsList.map { it.id })
            else -> Unit
        }
    }

    private fun <T> mergeById(existing: List<T>, incoming: List<T>, id: (T) -> Long): List<T> =
        (existing + incoming).distinctBy(id)

    private fun ArtistResult.toFallbackProfile(): ArtistProfile = ArtistProfile(
        id = id,
        name = name,
        coverUrl = avatarUrl,
        avatarUrl = avatarUrl,
        aliases = alias.split('、', ',').map(String::trim).filter(String::isNotBlank),
        albumCount = albumCount,
    )
}

private fun DiscoveryPlaylist.toCollectionDetail() = CollectionDetail(
    kind = CollectionKind.PLAYLIST,
    id = id,
    title = name,
    subtitle = creator,
    coverUrl = coverUrl,
    description = description,
    expectedItemCount = trackCount,
    playCount = playCount,
    publishTime = createTime,
    updateTime = updateTime,
    tags = tags,
)

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
