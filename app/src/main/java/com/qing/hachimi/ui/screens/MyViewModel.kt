package com.qing.hachimi.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.qing.hachimi.data.api.CloudApi
import com.qing.hachimi.data.api.ListenDataApi
import com.qing.hachimi.data.api.PlaylistApi
import com.qing.hachimi.data.local.CookieManager
import com.qing.hachimi.data.local.DiscoveryCacheManager
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.model.Song
import com.qing.hachimi.data.repository.NeteaseRepository
import com.qing.hachimi.downloader.DownloadEngine
import com.qing.hachimi.downloader.DownloadPreparation
import com.qing.hachimi.util.AppLogger

enum class MySection(val label: String) {
    HOME("我的"),
    LIKED("红心歌曲"),
    PLAYLISTS("我的歌单"),
    COLLECTED("收藏歌单"),
    CLOUD("云盘歌曲"),
    RANK("听歌排行"),
    RECENT("最近播放"),
    FOOTPRINT("听歌足迹"),
    COLLECTED_ALBUMS("收藏专辑"),
    COLLECTED_ARTISTS("收藏艺人"),
}

internal fun songsForDownload(allSongs: List<Song>, selectedIds: Set<Long>): List<Song> =
    if (selectedIds.isEmpty()) allSongs else allSongs.filter { it.id in selectedIds }

enum class FootprintTab(val label: String) {
    TODAY("今日"),
    WEEK("本周"),
    MONTH("本月"),
    YEAR("年度"),
}

data class UserProfile(
    val nickname: String = "",
    val avatarUrl: String = "",
)

data class MyUiState(
    val section: MySection = MySection.HOME,
    val profile: UserProfile? = null,
    val userPlaylists: List<PlaylistApi.UserPlaylist> = emptyList(),
    val likedSongs: List<Song> = emptyList(),
    val cloudSongs: List<CloudApi.CloudSong> = emptyList(),
    val cloudOffset: Int = 0,
    val cloudHasMore: Boolean = true,
    val cloudSearchQuery: String = "",  // 云盘歌曲搜索关键词
    // 听歌排行（type=1 最近一周 / type=0 所有时间）
    val rankType: Int = 1,
    val rankAllEntries: List<ListenDataApi.RecordEntry> = emptyList(),
    val rankWeekEntries: List<ListenDataApi.RecordEntry> = emptyList(),
    // 最近播放
    val recentListens: List<ListenDataApi.RecordEntry> = emptyList(),
    // 听歌足迹
    val footprintTab: FootprintTab = FootprintTab.TODAY,
    val footprintToday: List<ListenDataApi.RecordEntry> = emptyList(),
    val footprintWeek: List<ListenDataApi.RecordEntry> = emptyList(),
    val footprintMonth: List<ListenDataApi.RecordEntry> = emptyList(),
    val yearReport: ListenDataApi.YearReport? = null,
    // 收藏中心
    val collectedAlbums: List<com.qing.hachimi.data.model.AlbumResult> = emptyList(),
    val collectedAlbumsOffset: Int = 0,
    val collectedAlbumsHasMore: Boolean = true,
    val collectedArtists: List<com.qing.hachimi.data.model.ArtistResult> = emptyList(),
    val collectedArtistsOffset: Int = 0,
    val collectedArtistsHasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isPreparingDownloads: Boolean = false,
    val statusMessage: String? = null,
    // Currently viewing songs (playlist detail / album detail / artist top)
    val songs: List<Song> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val playlistName: String = ""
) {
    val myPlaylists: List<PlaylistApi.UserPlaylist>
        get() = userPlaylists.filter { !it.subscribed }
    val collectedPlaylists: List<PlaylistApi.UserPlaylist>
        get() = userPlaylists.filter { it.subscribed }
    val rankEntries: List<ListenDataApi.RecordEntry>
        get() = if (rankType == 0) rankAllEntries else rankWeekEntries
    val isPlaylistDetail: Boolean
        get() = songs.isNotEmpty()
}

class MyViewModel(
    private val repository: NeteaseRepository,
    private val cookieManager: CookieManager,
    private val settingsManager: SettingsManager,
    private val downloadEngine: DownloadEngine,
    private val discoveryCache: DiscoveryCacheManager
) : ViewModel() {

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(MyUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<MyUiState> = _uiState

    private suspend fun cookies() = cookieManager.getCookiesWithAnonFallback()
    private suspend fun awaitToken() { if (!cookieManager.isLoggedIn() && !cookieManager.hasUsableToken()) cookieManager.tokenReady.await() }

    /** 进入【我的】主页：并行加载用户信息、歌单、云盘（均有缓存兜底） */
    fun openHome() {
        val state = _uiState.value
        if (state.section == MySection.HOME && state.userPlaylists.isNotEmpty()) return
        _uiState.value = state.copy(
            section = MySection.HOME,
            songs = emptyList(),
            selectedIds = emptySet(),
            playlistName = "",
            statusMessage = null,
        )
        if (state.profile == null) loadProfile()
        if (state.userPlaylists.isEmpty()) loadMyPlaylists()
        if (state.cloudSongs.isEmpty()) loadCloudSongs()
    }

    fun openSection(section: MySection) {
        when (section) {
            MySection.HOME -> openHome()
            MySection.LIKED -> {
                _uiState.value = _uiState.value.copy(
                    section = MySection.LIKED,
                    songs = emptyList(),
                    selectedIds = emptySet(),
                    playlistName = "",
                    statusMessage = null,
                )
                loadLikedSongs()
            }
            MySection.PLAYLISTS -> {
                _uiState.value = _uiState.value.copy(
                    section = MySection.PLAYLISTS,
                    songs = emptyList(),
                    selectedIds = emptySet(),
                    playlistName = "",
                    statusMessage = null,
                )
                if (_uiState.value.userPlaylists.isEmpty()) loadMyPlaylists()
            }
            MySection.COLLECTED -> {
                _uiState.value = _uiState.value.copy(
                    section = MySection.COLLECTED,
                    songs = emptyList(),
                    selectedIds = emptySet(),
                    playlistName = "",
                    statusMessage = null,
                )
                if (_uiState.value.userPlaylists.isEmpty()) loadMyPlaylists()
            }
            MySection.CLOUD -> {
                _uiState.value = _uiState.value.copy(
                    section = MySection.CLOUD,
                    songs = emptyList(),
                    selectedIds = emptySet(),
                    playlistName = "",
                    statusMessage = null,
                )
                if (_uiState.value.cloudSongs.isEmpty()) loadCloudSongs()
            }
            MySection.RANK -> {
                _uiState.value = _uiState.value.copy(
                    section = MySection.RANK,
                    songs = emptyList(),
                    selectedIds = emptySet(),
                    playlistName = "",
                    statusMessage = null,
                )
                loadRank()
            }
            MySection.RECENT -> {
                _uiState.value = _uiState.value.copy(
                    section = MySection.RECENT,
                    songs = emptyList(),
                    selectedIds = emptySet(),
                    playlistName = "",
                    statusMessage = null,
                )
                if (_uiState.value.recentListens.isEmpty()) loadRecentListens()
            }
            MySection.FOOTPRINT -> {
                _uiState.value = _uiState.value.copy(
                    section = MySection.FOOTPRINT,
                    songs = emptyList(),
                    selectedIds = emptySet(),
                    playlistName = "",
                    statusMessage = null,
                )
                loadFootprint()
            }
            MySection.COLLECTED_ALBUMS -> {
                _uiState.value = _uiState.value.copy(
                    section = MySection.COLLECTED_ALBUMS,
                    songs = emptyList(),
                    selectedIds = emptySet(),
                    playlistName = "",
                    statusMessage = null,
                )
                if (_uiState.value.collectedAlbums.isEmpty()) loadCollectedAlbums()
            }
            MySection.COLLECTED_ARTISTS -> {
                _uiState.value = _uiState.value.copy(
                    section = MySection.COLLECTED_ARTISTS,
                    songs = emptyList(),
                    selectedIds = emptySet(),
                    playlistName = "",
                    statusMessage = null,
                )
                if (_uiState.value.collectedArtists.isEmpty()) loadCollectedArtists()
            }
        }
    }

    fun backToHome() {
        _uiState.value = _uiState.value.copy(
            section = MySection.HOME,
            songs = emptyList(),
            selectedIds = emptySet(),
            playlistName = "",
            statusMessage = null,
        )
    }

    fun showComingSoon() {
        _uiState.value = _uiState.value.copy(statusMessage = "即将上线，敬请期待")
    }

    // ── Profile ──
    private fun loadProfile() {
        if (!cookieManager.isLoggedIn()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val status = repository.loginApi().checkLoginStatus(cookieManager.getCookies())
                if (status.isLoggedIn) {
                    if (status.userId > 0) cookieManager.saveUserId(status.userId)
                    _uiState.value = _uiState.value.copy(
                        profile = UserProfile(
                            nickname = status.nickname,
                            avatarUrl = status.avatarUrl,
                        )
                    )
                }
            } catch (e: Exception) {
                AppLogger.error("[MyVM] loadProfile failed", e)
            }
        }
    }

    // ── My Playlists ──
    fun loadMyPlaylists() {
        var userId = cookieManager.getUserId()
        AppLogger.info("[MyVM] loadMyPlaylists: userId=$userId, isLoggedIn=${cookieManager.isLoggedIn()}")
        if (userId <= 0L && cookieManager.isLoggedIn()) {
            // userId not saved yet (e.g. previous EAPI failure). Try fetching now.
            AppLogger.info("[MyVM] loadMyPlaylists: userId missing, attempting to fetch via checkLoginStatus...")
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "获取用户信息中...")
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val status = repository.loginApi().checkLoginStatus(cookieManager.getCookies())
                    if (status.isLoggedIn && status.userId > 0) {
                        cookieManager.saveUserId(status.userId)
                        userId = status.userId
                        AppLogger.info("[MyVM] loadMyPlaylists: fetched userId=$userId, proceeding")
                    }
                } catch (e: Exception) {
                    AppLogger.error("[MyVM] checkLoginStatus failed", e)
                }
                doLoadMyPlaylists(userId)
            }
            return
        }
        doLoadMyPlaylists(userId)
    }

    private fun doLoadMyPlaylists(userId: Long) {
        if (userId <= 0L) {
            AppLogger.warn("[MyVM] loadMyPlaylists: userId <= 0, aborting")
            _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "未登录或无法获取用户信息")
            return
        }

        // 检查缓存
        if (discoveryCache.isUserPlaylistsCacheValid()) {
            val cached = discoveryCache.getCachedUserPlaylists()
            if (cached != null && cached.isNotEmpty()) {
                AppLogger.info("[MyVM] loadMyPlaylists: using cache, ${cached.size} playlists")
                _uiState.value = _uiState.value.copy(userPlaylists = cached, isLoading = false)
                return
            }
        }

        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            AppLogger.debug("[MyVM] loadMyPlaylists: calling repository.getUserPlaylists($userId)")
            repository.getUserPlaylists(userId).fold(
                onSuccess = { playlists ->
                    AppLogger.info("[MyVM] loadMyPlaylists: SUCCESS, got ${playlists.size} playlists")
                    _uiState.value = _uiState.value.copy(userPlaylists = playlists, isLoading = false)
                    discoveryCache.saveUserPlaylists(playlists)
                },
                onFailure = { e ->
                    AppLogger.error("[MyVM] loadMyPlaylists: FAILED - ${e.message}", e)
                    _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "获取歌单失败: ${e.message}")
                }
            )
        }
    }

    fun loadPlaylistSongs(playlist: PlaylistApi.UserPlaylist) {
        AppLogger.info("[MyVM] loadPlaylistSongs: id=${playlist.id} name=${playlist.name}")
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            repository.getPlaylistSongs(playlist.id.toString()).fold(
                onSuccess = { songs ->
                    AppLogger.info("[MyVM] loadPlaylistSongs: SUCCESS, got ${songs.size} songs")
                    _uiState.value = _uiState.value.copy(
                        songs = songs,
                        isLoading = false,
                        selectedIds = emptySet(),
                        playlistName = playlist.name,
                        statusMessage = "加载成功，共 ${songs.size} 首"
                    )
                },
                onFailure = { e ->
                    AppLogger.error("[MyVM] loadPlaylistSongs: FAILED - ${e.message}", e)
                    _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "加载歌单失败: ${e.message}")
                }
            )
        }
    }

    // ── Liked Songs (红心歌曲) ──
    fun loadLikedSongs() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.likedSongs.isNotEmpty()) return
        val userId = cookieManager.getUserId()
        if (userId <= 0L) {
            _uiState.value = _uiState.value.copy(statusMessage = "未登录或无法获取用户信息")
            return
        }
        _uiState.value = state.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            repository.getLikedSongs(userId).fold(
                onSuccess = { songs ->
                    AppLogger.info("[MyVM] loadLikedSongs: SUCCESS, got ${songs.size} songs")
                    _uiState.value = _uiState.value.copy(
                        likedSongs = songs,
                        isLoading = false,
                        statusMessage = "共 ${songs.size} 首红心歌曲",
                    )
                },
                onFailure = { e ->
                    AppLogger.error("[MyVM] loadLikedSongs: FAILED - ${e.message}", e)
                    _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "获取红心歌曲失败: ${e.message}")
                }
            )
        }
    }

    // ── Cloud Songs ──
    fun loadCloudSongs() {
        AppLogger.info("[MyVM] loadCloudSongs: limit=30 offset=0, isLoggedIn=${cookieManager.isLoggedIn()}")

        // 检查缓存
        if (discoveryCache.isCloudSongsCacheValid()) {
            val cached = discoveryCache.getCachedCloudSongs()
            if (cached != null && cached.isNotEmpty()) {
                AppLogger.info("[MyVM] loadCloudSongs: using cache, ${cached.size} songs")
                _uiState.value = _uiState.value.copy(
                    cloudSongs = cached,
                    isLoading = false,
                    cloudOffset = cached.size,
                    cloudHasMore = cached.size >= 30
                )
                return
            }
        }

        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null, cloudOffset = 0, cloudHasMore = true)
        viewModelScope.launch {
            repository.getCloudSongs(30, 0).fold(
                onSuccess = { songs ->
                    val hasMore = songs.size >= 30
                    AppLogger.info("[MyVM] loadCloudSongs: SUCCESS, got ${songs.size} songs, hasMore=$hasMore")
                    _uiState.value = _uiState.value.copy(
                        cloudSongs = songs,
                        isLoading = false,
                        cloudOffset = songs.size,
                        cloudHasMore = hasMore
                    )
                    discoveryCache.saveCloudSongs(songs)
                },
                onFailure = { e ->
                    AppLogger.error("[MyVM] loadCloudSongs: FAILED - ${e.message}", e)
                    _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "获取云盘歌曲失败: ${e.message}")
                }
            )
        }
    }

    // ── Listen Rank (听歌排行) ──
    fun setRankType(type: Int) {
        val state = _uiState.value
        if (state.rankType == type) return
        _uiState.value = state.copy(rankType = type, selectedIds = emptySet())
        if (type == 0 && state.rankAllEntries.isEmpty()) loadRank()
        if (type == 1 && state.rankWeekEntries.isEmpty()) loadRank()
    }

    fun loadRank() {
        val userId = cookieManager.getUserId()
        if (userId <= 0L) {
            _uiState.value = _uiState.value.copy(statusMessage = "未登录或无法获取用户信息")
            return
        }
        val state = _uiState.value
        val needAll = state.rankAllEntries.isEmpty()
        val needWeek = state.rankWeekEntries.isEmpty()
        if (!needAll && !needWeek) return
        _uiState.value = state.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            val allResult = if (needAll) repository.getUserRecord(userId, 0) else null
            val weekResult = if (needWeek) repository.getUserRecord(userId, 1) else null
            val errors = mutableListOf<String>()
            allResult?.onFailure { errors.add("全部排行: ${it.message}") }
            weekResult?.onFailure { errors.add("一周排行: ${it.message}") }
            _uiState.value = _uiState.value.copy(
                rankAllEntries = allResult?.getOrNull() ?: state.rankAllEntries,
                rankWeekEntries = weekResult?.getOrNull() ?: state.rankWeekEntries,
                isLoading = false,
                statusMessage = if (errors.isNotEmpty()) "获取听歌排行失败: ${errors.joinToString("；")}" else null,
            )
        }
    }

    // ── Recent Listens (最近播放) ──
    fun loadRecentListens() {
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            repository.getRecentListen().fold(
                onSuccess = { list ->
                    _uiState.value = _uiState.value.copy(
                        recentListens = list,
                        isLoading = false,
                        statusMessage = if (list.isEmpty()) "暂无最近播放记录" else "共 ${list.size} 条记录",
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "获取最近播放失败: ${e.message}",
                    )
                },
            )
        }
    }

    // ── Footprint (听歌足迹) ──
    fun setFootprintTab(tab: FootprintTab) {
        val state = _uiState.value
        if (state.footprintTab == tab) return
        _uiState.value = state.copy(footprintTab = tab, selectedIds = emptySet())
        val loaded = when (tab) {
            FootprintTab.TODAY -> state.footprintToday.isNotEmpty()
            FootprintTab.WEEK -> state.footprintWeek.isNotEmpty()
            FootprintTab.MONTH -> state.footprintMonth.isNotEmpty()
            FootprintTab.YEAR -> state.yearReport != null
        }
        if (!loaded) loadFootprint()
    }

    fun loadFootprint() {
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            val todayResult = if (state.footprintToday.isEmpty()) repository.getTodaySongRank() else null
            val weekResult = if (state.footprintWeek.isEmpty()) repository.getSongPlayRank("week") else null
            val monthResult = if (state.footprintMonth.isEmpty()) repository.getSongPlayRank("month") else null
            val yearResult = if (state.yearReport == null) repository.getYearReport() else null
            val errors = mutableListOf<String>()
            todayResult?.onFailure { errors.add("今日: ${it.message}") }
            weekResult?.onFailure { errors.add("本周: ${it.message}") }
            monthResult?.onFailure { errors.add("本月: ${it.message}") }
            yearResult?.onFailure { errors.add("年度: ${it.message}") }
            _uiState.value = _uiState.value.copy(
                footprintToday = todayResult?.getOrNull() ?: state.footprintToday,
                footprintWeek = weekResult?.getOrNull() ?: state.footprintWeek,
                footprintMonth = monthResult?.getOrNull() ?: state.footprintMonth,
                yearReport = yearResult?.getOrNull() ?: state.yearReport,
                isLoading = false,
                statusMessage = if (errors.isNotEmpty()) "获取听歌足迹失败: ${errors.joinToString("；")}" else null,
            )
        }
    }

    // ── Collections (收藏中心) ──
    fun loadCollectedAlbums() {
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            repository.getSubscribedAlbums().fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        collectedAlbums = page.items,
                        collectedAlbumsOffset = page.items.size,
                        collectedAlbumsHasMore = page.hasMore,
                        isLoading = false,
                        statusMessage = if (page.items.isEmpty()) "暂无收藏专辑" else "共 ${page.total.coerceAtLeast(page.items.size)} 张收藏专辑",
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "获取收藏专辑失败: ${e.message}",
                    )
                },
            )
        }
    }

    fun loadMoreCollectedAlbums() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.collectedAlbumsHasMore) return
        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            repository.getSubscribedAlbums(offset = state.collectedAlbumsOffset).fold(
                onSuccess = { page ->
                    val all = state.collectedAlbums + page.items
                    _uiState.value = _uiState.value.copy(
                        collectedAlbums = all,
                        collectedAlbumsOffset = all.size,
                        collectedAlbumsHasMore = page.hasMore,
                        isLoadingMore = false,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        statusMessage = "加载更多失败: ${e.message}",
                    )
                },
            )
        }
    }

    fun loadCollectedArtists() {
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            repository.getSubscribedArtists().fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        collectedArtists = page.items,
                        collectedArtistsOffset = page.items.size,
                        collectedArtistsHasMore = page.hasMore,
                        isLoading = false,
                        statusMessage = if (page.items.isEmpty()) "暂无关注的歌手" else "共 ${page.total.coerceAtLeast(page.items.size)} 位关注的歌手",
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "获取收藏艺人失败: ${e.message}",
                    )
                },
            )
        }
    }

    fun loadMoreCollectedArtists() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.collectedArtistsHasMore) return
        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            repository.getSubscribedArtists(offset = state.collectedArtistsOffset).fold(
                onSuccess = { page ->
                    val all = state.collectedArtists + page.items
                    _uiState.value = _uiState.value.copy(
                        collectedArtists = all,
                        collectedArtistsOffset = all.size,
                        collectedArtistsHasMore = page.hasMore,
                        isLoadingMore = false,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        statusMessage = "加载更多失败: ${e.message}",
                    )
                },
            )
        }
    }

    /** 收藏专辑详情（进入歌曲列表） */
    fun loadCollectedAlbumSongs(album: com.qing.hachimi.data.model.AlbumResult) {
        AppLogger.info("[MyVM] loadCollectedAlbumSongs: id=${album.id} name=${album.name}")
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            repository.getAlbumDetail(album.id.toString()).fold(
                onSuccess = { content ->
                    _uiState.value = _uiState.value.copy(
                        songs = content.songs,
                        isLoading = false,
                        selectedIds = emptySet(),
                        playlistName = content.detail.title,
                        statusMessage = "共 ${content.songs.size} 首",
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "加载专辑失败: ${e.message}",
                    )
                },
            )
        }
    }

    /** 收藏艺人详情（热门歌曲 50 首） */
    fun loadCollectedArtistSongs(artist: com.qing.hachimi.data.model.ArtistResult) {
        AppLogger.info("[MyVM] loadCollectedArtistSongs: id=${artist.id} name=${artist.name}")
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            repository.getArtistTopSongs(artist.id).fold(
                onSuccess = { songs ->
                    _uiState.value = _uiState.value.copy(
                        songs = songs,
                        isLoading = false,
                        selectedIds = emptySet(),
                        playlistName = artist.name,
                        statusMessage = "共 ${songs.size} 首热门歌曲",
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "加载艺人歌曲失败: ${e.message}",
                    )
                },
            )
        }
    }

    // ── Downloads ──
    fun downloadSelected() {
        val state = _uiState.value
        if (state.isPreparingDownloads) return
        val songs = songsForDownload(currentSectionSongs(), state.selectedIds)
        if (songs.isEmpty()) {
            _uiState.value = state.copy(statusMessage = "没有可下载的歌曲")
            return
        }
        val isCloud = state.section == MySection.CLOUD && !state.isPlaylistDetail
        val groupName = when {
            state.isPlaylistDetail -> state.playlistName.ifBlank { state.section.label }
            state.section == MySection.LIKED -> "红心歌曲"
            isCloud -> "云盘"
            else -> state.section.label
        }
        AppLogger.info("[MyVM] downloadSelected: section=${state.section} detail=${state.isPlaylistDetail} songs=${songs.size}")
        _uiState.value = state.copy(isPreparingDownloads = true, statusMessage = "正在获取下载链接...")
        viewModelScope.launch {
            try {
                awaitToken()
                val options = if (isCloud) {
                    DownloadPreparation.optionsFrom(
                        settings = settingsManager,
                        qualityLabel = "云盘",
                        cookie = cookieManager.getRawCookies(),
                        groupName = groupName,
                    )
                } else {
                    DownloadPreparation.optionsFrom(
                        settings = settingsManager,
                        cookie = cookieManager.getRawCookies(),
                        groupName = groupName,
                    )
                }
                val result = if (isCloud) {
                    DownloadPreparation.enqueueSongs(
                        songs = songs,
                        repository = repository,
                        settings = settingsManager,
                        engine = downloadEngine,
                        options = options,
                        urlProvider = { repository.getCloudDownloadUrl(it.id) },
                    )
                } else {
                    DownloadPreparation.enqueueSongs(
                        songs = songs,
                        repository = repository,
                        settings = settingsManager,
                        engine = downloadEngine,
                        options = options,
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isPreparingDownloads = false,
                    selectedIds = if (result.added > 0) emptySet() else _uiState.value.selectedIds,
                    statusMessage = result.message,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("[MyVM] preparing downloads failed", e)
                _uiState.value = _uiState.value.copy(
                    isPreparingDownloads = false,
                    statusMessage = "准备下载失败: ${e.message}",
                )
            }
        }
    }

    /** 当前分区展示的歌曲列表（下载/全选共用） */
    fun currentSectionSongs(): List<Song> {
        val state = _uiState.value
        return when (state.section) {
            MySection.LIKED -> state.likedSongs
            MySection.CLOUD -> state.cloudSongs
                .filter { song ->
                    state.cloudSearchQuery.isBlank() ||
                        song.name.contains(state.cloudSearchQuery, ignoreCase = true) ||
                        song.artists.contains(state.cloudSearchQuery, ignoreCase = true) ||
                        song.album.contains(state.cloudSearchQuery, ignoreCase = true)
                }
                .map { Song(it.id, it.name, it.artists, it.album, it.coverUrl) }
            MySection.RANK -> state.rankEntries.map { recordToSong(it) }
            MySection.RECENT -> state.recentListens.map { recordToSong(it) }
            MySection.FOOTPRINT -> when (state.footprintTab) {
                FootprintTab.TODAY -> state.footprintToday
                FootprintTab.WEEK -> state.footprintWeek
                FootprintTab.MONTH -> state.footprintMonth
                FootprintTab.YEAR -> state.yearReport?.topSongs.orEmpty()
            }.map { recordToSong(it) }
            else -> if (state.isPlaylistDetail) state.songs else emptyList()
        }
    }

    private fun recordToSong(it: ListenDataApi.RecordEntry) =
        Song(it.song.id, it.song.name, it.song.artists, it.song.album, it.song.coverUrl)

    fun loadMoreCloudSongs() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.cloudHasMore) return
        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            repository.getCloudSongs(30, state.cloudOffset).fold(
                onSuccess = { newSongs ->
                    val all = state.cloudSongs + newSongs
                    _uiState.value = _uiState.value.copy(
                        cloudSongs = all,
                        isLoadingMore = false,
                        cloudOffset = all.size,
                        cloudHasMore = newSongs.size >= 30
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoadingMore = false, statusMessage = "加载更多失败: ${e.message}")
                }
            )
        }
    }

    // ── Selection ──
    fun toggleSongSelection(songId: Long) {
        val current = _uiState.value.selectedIds
        _uiState.value = _uiState.value.copy(
            selectedIds = if (songId in current) current - songId else current + songId
        )
    }

    fun toggleSelectAll() {
        val state = _uiState.value
        val all = currentSectionSongs().map { it.id }.toSet()
        val current = state.selectedIds
        val allSelected = current.containsAll(all) && all.isNotEmpty()
        _uiState.value = _uiState.value.copy(selectedIds = if (allSelected) emptySet() else all)
    }

    fun updateCloudSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(cloudSearchQuery = query, selectedIds = emptySet())
    }

    fun clearSongs() {
        _uiState.value = _uiState.value.copy(songs = emptyList(), selectedIds = emptySet(), playlistName = "")
    }

    fun resetToHome() {
        backToHome()
    }

    fun clearAccountContent() {
        _uiState.value = MyUiState()
    }

    fun refreshCurrentSection() {
        val state = _uiState.value
        when (state.section) {
            MySection.HOME -> {
                _uiState.value = state.copy(
                    userPlaylists = emptyList(),
                    cloudSongs = emptyList(),
                    cloudOffset = 0,
                    cloudHasMore = true,
                    profile = null,
                )
                openHome()
            }
            MySection.PLAYLISTS, MySection.COLLECTED -> {
                _uiState.value = state.copy(userPlaylists = emptyList())
                loadMyPlaylists()
            }
            MySection.LIKED -> {
                _uiState.value = state.copy(likedSongs = emptyList())
                loadLikedSongs()
            }
            MySection.CLOUD -> {
                _uiState.value = state.copy(cloudSongs = emptyList(), cloudOffset = 0, cloudHasMore = true)
                loadCloudSongs()
            }
            MySection.RANK -> {
                _uiState.value = state.copy(rankAllEntries = emptyList(), rankWeekEntries = emptyList())
                loadRank()
            }
            MySection.RECENT -> {
                _uiState.value = state.copy(recentListens = emptyList())
                loadRecentListens()
            }
            MySection.FOOTPRINT -> {
                _uiState.value = state.copy(
                    footprintToday = emptyList(),
                    footprintWeek = emptyList(),
                    footprintMonth = emptyList(),
                    yearReport = null,
                )
                loadFootprint()
            }
            MySection.COLLECTED_ALBUMS -> {
                _uiState.value = state.copy(collectedAlbums = emptyList(), collectedAlbumsOffset = 0, collectedAlbumsHasMore = true)
                loadCollectedAlbums()
            }
            MySection.COLLECTED_ARTISTS -> {
                _uiState.value = state.copy(collectedArtists = emptyList(), collectedArtistsOffset = 0, collectedArtistsHasMore = true)
                loadCollectedArtists()
            }
        }
    }

    fun clearStatus() { _uiState.value = _uiState.value.copy(statusMessage = null) }

}
