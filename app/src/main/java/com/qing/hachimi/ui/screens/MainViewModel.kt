package com.qing.hachimi.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qing.hachimi.data.api.*
import com.qing.hachimi.data.local.AccountHistory
import com.qing.hachimi.data.local.AccountHistoryManager
import com.qing.hachimi.data.local.CookieManager
import com.qing.hachimi.data.local.DiscoveryCacheManager
import com.qing.hachimi.data.local.FolderNamingFormat
import com.qing.hachimi.data.local.NamingFormat
import com.qing.hachimi.data.local.SeenManager
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.model.*
import com.qing.hachimi.data.repository.NeteaseRepository
import com.qing.hachimi.downloader.DownloadEngine
import com.qing.hachimi.downloader.DownloadPreparation
import com.qing.hachimi.ui.theme.ColorMode
import com.qing.hachimi.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class MainUiState(
    val isLoggedIn: Boolean = false,
    val userId: Long = 0L,
    val playlistUrl: String = "",
    val songs: List<Song> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val playlistName: String = "",
    val quality: String = "exhigh",
    val downloadDir: String = "",
    val sourceLabel: String = "",
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val fuckAiMode: Boolean = false,
    val namingFormat: NamingFormat = NamingFormat.SONG_ARTIST,
    val downloadLyrics: Boolean = true,
    val folderNamingFormat: FolderNamingFormat = FolderNamingFormat.NONE,
    val enableWriteTags: Boolean = false,
    val saveTlLrc: Boolean = false,
    val saveRomaLrc: Boolean = false,
    val saveYrc: Boolean = false,
    val customNamingTemplate: String = "",
    val artistDelimiter: String = "/",
    val qualityPickerOptions: List<String> = emptyList(),
    val colorMode: Int = 5,
    val blurEffect: Boolean = true,
    val liquidGlass: Boolean = false,
    val searchCategory: SearchCategory = SearchCategory.SONGS,
    val searchResults: SearchResults = SearchResults(),
    val searchArtists: List<ArtistResult> = emptyList(),
    val searchAlbums: List<AlbumResult> = emptyList(),
    val isLoadingMore: Boolean = false,
    // Discovery state
    val sourceMode: SourceMode = SourceMode.SEARCH,
    val previousSourceMode: SourceMode = SourceMode.SEARCH,
    val chartList: List<ChartInfo> = emptyList(),
    val recommendSongs: List<Song> = emptyList(),
    val newAlbumsList: List<AlbumResult> = emptyList(),
    val newAlbumsOffset: Int = 0,
    val newAlbumsHasMore: Boolean = true,
    val isLoadingMoreAlbums: Boolean = false,
    val userPlaylists: List<PlaylistApi.UserPlaylist> = emptyList(),
    val hotSearchKeywords: List<Pair<String, Int>> = emptyList(),
    val artistDetailName: String = "",
    val artistDetailAlbums: List<AlbumResult> = emptyList(),
    val gridColumns: Int = 2,
    val fmSongs: List<Song> = emptyList(),
    val fmHistoryGroups: List<List<Song>> = emptyList(),
    val fmGroupCount: Int = 0,
    val isLoadingFm: Boolean = false,
    val searchTabRequestId: Long = 0L,
    val scrollToTopTrigger: Int = 0,
    val scrollToTopTab: String = "",
    val currentTab: String = "DISCOVER",
)

class MainViewModel(
    private val repository: NeteaseRepository,
    private val cookieManager: CookieManager,
    private val downloadEngine: DownloadEngine,
    private val settingsManager: SettingsManager,
    private val seenManager: SeenManager,
    private val discoveryCache: DiscoveryCacheManager,
    private val accountHistory: AccountHistoryManager
) : ViewModel() {

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(MainUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<MainUiState> = _uiState

    private val _qrCodeImage = kotlinx.coroutines.flow.MutableStateFlow<BooleanArray?>(null)
    val qrCodeImage: kotlinx.coroutines.flow.StateFlow<BooleanArray?> = _qrCodeImage

    private val _qrCodeSize = kotlinx.coroutines.flow.MutableStateFlow(0)
    val qrCodeSize: kotlinx.coroutines.flow.StateFlow<Int> = _qrCodeSize

    private val _qrStatusMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val qrStatusMessage: kotlinx.coroutines.flow.StateFlow<String?> = _qrStatusMessage

    private var qrPollJob: kotlinx.coroutines.Job? = null
    private var isPreparingDownloads = false

    val downloadProgress = downloadEngine.progressMap
    val downloadSessionStartedAt = downloadEngine.sessionStartedAt

    init {
        val quality = settingsManager.quality
        val dir = if (settingsManager.downloadDir.isNotBlank())
            settingsManager.downloadDir else settingsManager.defaultDownloadDir

        // Check for expired login session
        if (!cookieManager.invalidateIfExpired()) {
            AppLogger.info("Login session expired, clearing state")
        }

        _uiState.value = _uiState.value.copy(
            isLoggedIn = cookieManager.isLoggedIn(),
            userId = cookieManager.getUserId(),
            quality = quality,
            downloadDir = dir,
            fuckAiMode = settingsManager.fuckAiMode,
            namingFormat = settingsManager.namingFormat,
            downloadLyrics = settingsManager.downloadLyrics,
            folderNamingFormat = settingsManager.folderNamingFormat,
            enableWriteTags = settingsManager.enableWriteTags,
            saveTlLrc = settingsManager.saveTlLrc,
            saveRomaLrc = settingsManager.saveRomaLrc,
            saveYrc = settingsManager.saveYrc,
            customNamingTemplate = settingsManager.customNamingTemplate,
            artistDelimiter = settingsManager.artistDelimiter,
            qualityPickerOptions = settingsManager.getFilteredQualityKeys(),
            colorMode = settingsManager.themeColorMode,
            blurEffect = settingsManager.blurEffect,
            liquidGlass = settingsManager.liquidGlass,
            gridColumns = settingsManager.gridColumns
        )
        // Initialize anonymous token at startup (ensures MUSIC_A is available for non-logged-in users)
        initAnonymousToken()
        // Preload cached discovery data so it shows instantly when user visits
        preloadDiscoveryCache()
    }

    private fun preloadDiscoveryCache() {
        viewModelScope.launch(Dispatchers.IO) {
            discoveryCache.getCachedCharts()?.let { cached ->
                _uiState.value = _uiState.value.copy(chartList = cached)
            }
            discoveryCache.getCachedHotSearch()?.let { cached ->
                _uiState.value = _uiState.value.copy(hotSearchKeywords = cached)
            }
            discoveryCache.getCachedNewAlbums()?.let { cached ->
                _uiState.value = _uiState.value.copy(newAlbumsList = cached)
            }
            discoveryCache.getCachedUserPlaylists()?.let { cached ->
                _uiState.value = _uiState.value.copy(userPlaylists = cached)
            }
            discoveryCache.getCachedRecommend()?.let { cached ->
                _uiState.value = _uiState.value.copy(recommendSongs = cached)
            }
            AppLogger.debug("Discovery cache preloaded")
        }
    }

    private fun initAnonymousToken() {
        // Skip if user is already logged in or we already have a usable token
        if (cookieManager.isLoggedIn()) return
        if (cookieManager.hasUsableToken()) return

        // Prevent double-fetch if init was already triggered
        if (!cookieManager.markTokenInitStarted()) return

        // Get anonymous token in background on IO dispatcher
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.loginApi().registerAnonimous()
                if (result != null) {
                    cookieManager.saveAnonToken(result.musicA)
                    // Also save other cookies from anon response (like NMTID, WEVNSM, etc.)
                    cookieManager.saveCookies(result.cookies)
                    AppLogger.info("Anonymous token initialized successfully")
                } else {
                    AppLogger.warn("Failed to initialize anonymous token")
                }
            } catch (e: Exception) {
                AppLogger.error("initAnonymousToken exception", e)
            } finally {
                cookieManager.signalTokenInitComplete()
            }
        }
    }

    /**
     * Waits for anonymous token initialization to complete before proceeding.
     * This prevents race conditions where API calls are made before MUSIC_A is available.
     */
    private suspend fun awaitTokenReady() {
        // If already logged in or have usable token, no need to wait
        if (cookieManager.isLoggedIn() || cookieManager.hasUsableToken()) return
        cookieManager.tokenReady.await()
    }

    fun updatePlaylistUrl(url: String) {
        _uiState.value = _uiState.value.copy(playlistUrl = url)
    }

    fun updateQuality(quality: String) {
        settingsManager.quality = quality
        _uiState.value = _uiState.value.copy(quality = quality)
    }

    fun updateDownloadDir(path: String) {
        _uiState.value = _uiState.value.copy(downloadDir = path)
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleFuckAiMode() {
        val newValue = !_uiState.value.fuckAiMode
        settingsManager.fuckAiMode = newValue
        val filteredKeys = settingsManager.getFilteredQualityKeys()
        val currentQuality = _uiState.value.quality
        val newQuality = if (currentQuality !in filteredKeys) filteredKeys.firstOrNull() ?: "exhigh"
            else currentQuality
        if (newQuality != currentQuality) {
            settingsManager.quality = newQuality
        }
        _uiState.value = _uiState.value.copy(
            fuckAiMode = newValue,
            qualityPickerOptions = filteredKeys,
            quality = newQuality
        )
    }

    fun setNamingFormat(format: NamingFormat) {
        settingsManager.namingFormat = format
        _uiState.value = _uiState.value.copy(namingFormat = format)
    }

    fun toggleDownloadLyrics() {
        val newValue = !_uiState.value.downloadLyrics
        settingsManager.downloadLyrics = newValue
        _uiState.value = _uiState.value.copy(downloadLyrics = newValue)
    }

    fun setFolderNamingFormat(format: FolderNamingFormat) {
        settingsManager.folderNamingFormat = format
        _uiState.value = _uiState.value.copy(folderNamingFormat = format)
    }

    fun setGridColumns(columns: Int) {
        if (columns !in 1..4) return
        settingsManager.gridColumns = columns
        _uiState.value = _uiState.value.copy(gridColumns = columns)
    }

    // Legacy: kept for backward compatibility
    fun toggleCreatePlaylistFolder() {
        val current = _uiState.value.folderNamingFormat
        val newValue = current != FolderNamingFormat.NONE
        settingsManager.folderNamingFormat = if (newValue) FolderNamingFormat.NONE else FolderNamingFormat.PLAYLIST_NAME
        _uiState.value = _uiState.value.copy(folderNamingFormat = if (newValue) FolderNamingFormat.NONE else FolderNamingFormat.PLAYLIST_NAME)
    }

    fun toggleEnableWriteTags() {
        val newValue = !_uiState.value.enableWriteTags
        settingsManager.enableWriteTags = newValue
        _uiState.value = _uiState.value.copy(enableWriteTags = newValue)
    }

    fun toggleSaveTlLrc() {
        val newValue = !_uiState.value.saveTlLrc
        settingsManager.saveTlLrc = newValue
        _uiState.value = _uiState.value.copy(saveTlLrc = newValue)
    }

    fun toggleSaveRomaLrc() {
        val newValue = !_uiState.value.saveRomaLrc
        settingsManager.saveRomaLrc = newValue
        _uiState.value = _uiState.value.copy(saveRomaLrc = newValue)
    }

    fun toggleSaveYrc() {
        val newValue = !_uiState.value.saveYrc
        settingsManager.saveYrc = newValue
        _uiState.value = _uiState.value.copy(saveYrc = newValue)
    }

    fun setCustomNamingTemplate(template: String) {
        settingsManager.customNamingTemplate = template
        _uiState.value = _uiState.value.copy(customNamingTemplate = template)
    }

    fun setArtistDelimiter(delimiter: String) {
        val sanitized = delimiter.replace("\\", "/")
        settingsManager.artistDelimiter = sanitized
        _uiState.value = _uiState.value.copy(artistDelimiter = sanitized)
    }

    fun updateColorMode(mode: Int) {
        AppLogger.info("Theme mode changed to: $mode")
        settingsManager.themeColorMode = mode
        _uiState.value = _uiState.value.copy(colorMode = mode)
    }

    fun updateThemeBaseMode(baseMode: Int) {
        val current = ColorMode.fromValue(_uiState.value.colorMode)
        val normalizedBase = baseMode.coerceIn(0, 2)
        val nextMode = if (current.isMonet) normalizedBase + 3 else normalizedBase
        updateColorMode(nextMode)
    }

    fun toggleMonetTheme() {
        val current = ColorMode.fromValue(_uiState.value.colorMode)
        val nextMode = if (current.isMonet) current.toNonMonetMode() else current.toMonetMode()
        updateColorMode(nextMode)
    }

    fun toggleBlurEffect() {
        val newValue = !_uiState.value.blurEffect
        settingsManager.blurEffect = newValue
        _uiState.value = _uiState.value.copy(blurEffect = newValue)
    }

    fun toggleLiquidGlass() {
        val newValue = !_uiState.value.liquidGlass
        settingsManager.liquidGlass = newValue
        _uiState.value = _uiState.value.copy(liquidGlass = newValue)
    }

    fun cancelQRLogin() {
        qrPollJob?.cancel()
        qrPollJob = null
        _qrCodeImage.value = null
        _qrStatusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        qrPollJob?.cancel()
    }

    fun startQRLogin() {
        qrPollJob?.cancel()
        qrPollJob = viewModelScope.launch {
            try {
                // Step 1: Get unikey
                AppLogger.debug("QR login: requesting key")
                val qrResult = repository.qrKey()
                if (qrResult == null) {
                    AppLogger.error("QR login: failed to get key")
                    _qrStatusMessage.value = "获取二维码失败"
                    return@launch
                }

                // Generate QR code bitmap
                AppLogger.debug("QR login: key=${qrResult.unikey}")
                val qrResult2 = generateQrCode("https://music.163.com/login?codekey=${qrResult.unikey}")
                if (qrResult2 != null) {
                    _qrCodeImage.value = qrResult2.first
                    _qrCodeSize.value = qrResult2.second
                }
                _qrStatusMessage.value = "等待扫码..."

                // Step 2: Poll for scan status
                while (true) {
                    kotlinx.coroutines.delay(2000)
                    val status = repository.checkQr(qrResult.unikey, qrResult.sessionCookies)
                    _qrStatusMessage.value = status.message
                    if (status.code == 803 && status.cookies.isNotEmpty()) {
                        // Login successful
                        AppLogger.info("QR login: success")
                        cookieManager.saveCookies(status.cookies)
                        saveCurrentUserId()
                        _uiState.value = _uiState.value.copy(
                            isLoggedIn = true,
                            statusMessage = "登录成功"
                        )
                        _qrCodeImage.value = null
                        _qrStatusMessage.value = null
                        qrPollJob = null
                        return@launch
                    } else if (status.code < 0) {
                        AppLogger.warn("QR login: expired or failed, code=${status.code}")
                        _qrStatusMessage.value = "二维码已过期，请重试"
                        _qrCodeImage.value = null
                        qrPollJob = null
                        return@launch
                    }
                }
            } catch (e: Exception) {
                AppLogger.error("QR login: exception", e)
                _qrStatusMessage.value = "登录异常: ${e.message}"
                _qrCodeImage.value = null
                qrPollJob = null
            }
        }
    }

    private fun generateQrCode(content: String): Pair<BooleanArray, Int>? {
        return try {
            val hints = mapOf(
                com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
                com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
            )
            val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(content, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400, hints)
            val size = bitMatrix.width
            val pixels = BooleanArray(size * size)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    pixels[y * size + x] = bitMatrix[x, y]
                }
            }
            pixels to size
        } catch (_: Exception) {
            null
        }
    }

    fun toggleSongSelection(songId: Long) {
        val current = _uiState.value.selectedIds
        _uiState.value = _uiState.value.copy(
            selectedIds = if (songId in current) current - songId else current + songId
        )
    }

    fun toggleSelectAll() {
        val all = _uiState.value.songs.map { it.id }.toSet()
        val current = _uiState.value.selectedIds
        val allSelected = current.containsAll(all) && all.isNotEmpty()
        _uiState.value = _uiState.value.copy(
            selectedIds = if (allSelected) emptySet() else all
        )
    }

    fun parseUrl() {
        val url = _uiState.value.playlistUrl.trim()
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "请输入链接或搜索关键词")
            return
        }

        val link = NeteaseLinkParser.parse(url)

        when {
            link?.type == NeteaseLinkParser.Type.SONG -> parseSingleSong(link.id)
            link?.type == NeteaseLinkParser.Type.ALBUM -> parseAlbum(link.id)
            link?.type == NeteaseLinkParser.Type.PLAYLIST -> parsePlaylist(link.id)
            url.all(Char::isDigit) -> parsePlaylist(url)
            else -> parsePlaylist(url)
        }
    }

    private fun clearSearchState() {
        _uiState.value = _uiState.value.copy(
            searchResults = SearchResults(),
            searchArtists = emptyList(),
            searchAlbums = emptyList(),
            searchCategory = SearchCategory.SONGS
        )
    }

    private fun parsePlaylist(id: String) {
        clearSearchState()
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null, sourceLabel = "歌单")
        viewModelScope.launch {
            awaitTokenReady()
            repository.getPlaylistSongs(id).fold(
                onSuccess = { songs ->
                    _uiState.value = _uiState.value.copy(
                        songs = songs, isLoading = false,
                        selectedIds = emptySet(),
                        playlistName = "歌单 · ${songs.size}首",
                        statusMessage = "解析成功，共 ${songs.size} 首歌曲"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "解析失败: ${e.message}"
                    )
                }
            )
        }
    }

    private fun parseAlbum(id: String) {
        clearSearchState()
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null, sourceLabel = "专辑")
        viewModelScope.launch {
            awaitTokenReady()
            repository.getAlbumSongs(id).fold(
                onSuccess = { songs ->
                    _uiState.value = _uiState.value.copy(
                        songs = songs, isLoading = false,
                        selectedIds = emptySet(),
                        playlistName = "专辑 · ${songs.size}首",
                        statusMessage = "解析成功，共 ${songs.size} 首歌曲"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "解析失败: ${e.message}"
                    )
                }
            )
        }
    }

    private fun parseSingleSong(id: String) {
        clearSearchState()
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null, sourceLabel = "单曲")
        viewModelScope.launch {
            awaitTokenReady()
            repository.getSingleSong(id).fold(
                onSuccess = { song ->
                    _uiState.value = _uiState.value.copy(
                        songs = listOf(song), isLoading = false,
                        selectedIds = setOf(song.id),
                        playlistName = song.name,
                        statusMessage = "解析成功"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "解析失败: ${e.message}"
                    )
                }
            )
        }
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return
        _uiState.value = _uiState.value.copy(isSearching = true, statusMessage = null, sourceLabel = "搜索")
        viewModelScope.launch {
            awaitTokenReady()
            repository.searchAll(query).fold(
                onSuccess = { results ->
                    val isEmpty = results.songs.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty()
                    val msg = if (isEmpty) {
                        "未找到相关结果"
                    } else {
                        buildString {
                            append("找到 ")
                            val parts = mutableListOf<String>()
                            if (results.songs.isNotEmpty()) parts.add("${results.songs.size} 首歌曲")
                            if (results.albums.isNotEmpty()) parts.add("${results.albums.size} 张专辑")
                            if (results.artists.isNotEmpty()) parts.add("${results.artists.size} 位歌手")
                            append(parts.joinToString("、"))
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        songs = results.songs,
                        searchResults = results,
                        searchArtists = results.artists,
                        searchAlbums = results.albums,
                        searchCategory = if (results.songs.isNotEmpty()) SearchCategory.SONGS
                            else if (results.albums.isNotEmpty()) SearchCategory.ALBUMS
                            else SearchCategory.ARTISTS,
                        isSearching = false,
                        selectedIds = emptySet(),
                        playlistName = "搜索结果: $query",
                        statusMessage = msg
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        statusMessage = "搜索失败: ${e.message}"
                    )
                }
            )
        }
    }

    fun setSearchCategory(category: SearchCategory) {
        _uiState.value = _uiState.value.copy(searchCategory = category)
    }

    fun loadMoreSearch() {
        val state = _uiState.value
        if (state.isLoadingMore) return
        val query = state.searchQuery.trim()
        if (query.isBlank()) return

        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            awaitTokenReady()
            when (state.searchCategory) {
                SearchCategory.SONGS -> {
                    if (!state.searchResults.hasMoreSongs) {
                        _uiState.value = _uiState.value.copy(isLoadingMore = false)
                        return@launch
                    }
                    repository.loadMoreSongs(query, state.searchResults.songOffset).fold(
                        onSuccess = { result ->
                            val allSongs = _uiState.value.songs + result.songs
                            _uiState.value = _uiState.value.copy(
                                songs = allSongs,
                                isLoadingMore = false,
                                searchResults = _uiState.value.searchResults.copy(
                                    songs = allSongs,
                                    songOffset = _uiState.value.searchResults.songOffset + result.songs.size,
                                    hasMoreSongs = result.hasMore
                                )
                            )
                        },
                        onFailure = {
                            _uiState.value = _uiState.value.copy(
                                isLoadingMore = false,
                                statusMessage = "加载更多失败"
                            )
                        }
                    )
                }
                SearchCategory.ARTISTS -> {
                    if (!state.searchResults.hasMoreArtists) {
                        _uiState.value = _uiState.value.copy(isLoadingMore = false)
                        return@launch
                    }
                    repository.loadMoreArtists(query, state.searchResults.artistOffset).fold(
                        onSuccess = { result ->
                            val allArtists = _uiState.value.searchArtists + result.artists
                            _uiState.value = _uiState.value.copy(
                                searchArtists = allArtists,
                                isLoadingMore = false,
                                searchResults = _uiState.value.searchResults.copy(
                                    artists = allArtists,
                                    artistOffset = _uiState.value.searchResults.artistOffset + result.artists.size,
                                    hasMoreArtists = result.hasMore
                                )
                            )
                        },
                        onFailure = {
                            _uiState.value = _uiState.value.copy(
                                isLoadingMore = false,
                                statusMessage = "加载更多失败"
                            )
                        }
                    )
                }
                SearchCategory.ALBUMS -> {
                    if (!state.searchResults.hasMoreAlbums) {
                        _uiState.value = _uiState.value.copy(isLoadingMore = false)
                        return@launch
                    }
                    repository.loadMoreAlbums(query, state.searchResults.albumOffset).fold(
                        onSuccess = { result ->
                            val allAlbums = _uiState.value.searchAlbums + result.albums
                            _uiState.value = _uiState.value.copy(
                                searchAlbums = allAlbums,
                                isLoadingMore = false,
                                searchResults = _uiState.value.searchResults.copy(
                                    albums = allAlbums,
                                    albumOffset = _uiState.value.searchResults.albumOffset + result.albums.size,
                                    hasMoreAlbums = result.hasMore
                                )
                            )
                        },
                        onFailure = {
                            _uiState.value = _uiState.value.copy(
                                isLoadingMore = false,
                                statusMessage = "加载更多失败"
                            )
                        }
                    )
                }
                SearchCategory.PLAYLISTS,
                SearchCategory.PODCASTS -> {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
            }
        }
    }

    fun startDownload() {
        val state = _uiState.value
        val songs = state.songs.filter { it.id in state.selectedIds }
        if (songs.isEmpty()) {
            _uiState.value = state.copy(statusMessage = "请至少选择一首歌曲")
            return
        }
        if (isPreparingDownloads) return
        isPreparingDownloads = true
        _uiState.value = state.copy(statusMessage = "正在准备下载...")

        viewModelScope.launch {
            try {
                awaitTokenReady()
                val result = DownloadPreparation.enqueueSongs(
                    songs = songs,
                    repository = repository,
                    settings = settingsManager,
                    engine = downloadEngine,
                    options = DownloadPreparation.optionsFrom(
                        settings = settingsManager,
                        quality = state.quality,
                        qualityLabel = NeteaseApi.QUALITY_MAP[state.quality] ?: "标准",
                        cookie = cookieManager.getRawCookies(),
                        groupName = state.sourceLabel.takeIf {
                            settingsManager.folderNamingFormat == FolderNamingFormat.PLAYLIST_NAME
                        },
                    ),
                )
                _uiState.value = _uiState.value.copy(
                    selectedIds = if (result.added > 0) emptySet() else _uiState.value.selectedIds,
                    statusMessage = result.message,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusMessage = "准备下载失败: ${e.message}")
            } finally {
                isPreparingDownloads = false
            }
        }
    }


    private fun saveCurrentUserId() {
        viewModelScope.launch(Dispatchers.IO) {
            val status = repository.loginApi().checkLoginStatus(cookieManager.getCookies())
            if (status.isLoggedIn) {
                cookieManager.saveUserId(status.userId)
                AppLogger.info("Saved userId: ${status.userId}")
            }
        }
    }

    fun saveCookies(cookieStr: String): Boolean {
        val result = cookieManager.parseAndSaveSmart(cookieStr)
        if (result.success) {
            discoveryCache.clearAll()
            saveCurrentUserId()
        }
        _uiState.value = _uiState.value.copy(
            isLoggedIn = cookieManager.isLoggedIn(),
            userId = cookieManager.getUserId(),
            statusMessage = result.message
        )
        return result.success
    }

    fun saveCookieMap(cookies: Map<String, String>): Boolean {
        val result = cookieManager.saveCookieMapForLogin(cookies)
        if (result.success) {
            discoveryCache.clearAll()
            saveCurrentUserId()
            // 保存账号历史（异步）
            viewModelScope.launch(Dispatchers.IO) {
                saveAccountHistory(cookies)
            }
        }
        _uiState.value = _uiState.value.copy(
            isLoggedIn = cookieManager.isLoggedIn(),
            userId = cookieManager.getUserId(),
            statusMessage = result.message
        )
        return result.success
    }

    fun saveMusicUDirect(musicU: String): Boolean {
        val result = cookieManager.saveMusicUDirect(musicU)
        if (result.success) {
            discoveryCache.clearAll()
            saveCurrentUserId()
        }
        _uiState.value = _uiState.value.copy(
            isLoggedIn = cookieManager.isLoggedIn(),
            userId = cookieManager.getUserId(),
            statusMessage = result.message
        )
        return result.success
    }

    fun logout() {
        cookieManager.clearCookies()
        discoveryCache.clearAll()
        _uiState.value = _uiState.value.copy(
            isLoggedIn = false, userId = 0L, songs = emptyList(),
            selectedIds = emptySet(), playlistName = "", sourceLabel = "",
            searchResults = SearchResults(),
            searchArtists = emptyList(),
            searchAlbums = emptyList(),
            searchCategory = SearchCategory.SONGS,
            sourceMode = SourceMode.SEARCH,
            chartList = emptyList(),
            recommendSongs = emptyList(),
            newAlbumsList = emptyList(),
            userPlaylists = emptyList(),
            hotSearchKeywords = emptyList(),
            artistDetailName = "",
            artistDetailAlbums = emptyList()
        )
    }

    fun getExportAccountData(): com.qing.hachimi.data.local.CookieManager.AccountExportData {
        return cookieManager.exportAccountData()
    }

    fun clearSeenItems() {
        seenManager.clearAll()
        discoveryCache.clearAll()
        // Clear discovery state so items will reload next time user visits
        _uiState.value = _uiState.value.copy(
            chartList = emptyList(),
            newAlbumsList = emptyList(),
            userPlaylists = emptyList(),
            statusMessage = "已重置显示记录，下次刷新时将重新显示"
        )
    }

    fun clearCache(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Clear Coil image cache
                coil3.SingletonImageLoader.get(context).diskCache?.clear()
                coil3.SingletonImageLoader.get(context).memoryCache?.clear()
                // Clear app external cache
                val cacheDir = context.externalCacheDir
                if (cacheDir != null && cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(statusMessage = "缓存已清除")
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(statusMessage = "清除缓存失败: ${e.message}")
                }
            }
        }
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    fun scrollToTop() {
        _uiState.value = _uiState.value.copy(
            scrollToTopTrigger = _uiState.value.scrollToTopTrigger + 1,
            scrollToTopTab = _uiState.value.currentTab
        )
    }

    fun updateCurrentTab(tab: String) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun requestSearchTab() {
        _uiState.value = _uiState.value.copy(
            searchTabRequestId = _uiState.value.searchTabRequestId + 1L
        )
    }

    fun pauseDownload(songId: Long) = downloadEngine.pauseDownload(songId)
    fun resumeDownload(songId: Long) = downloadEngine.resumeDownload(songId)
    fun pauseDownloads(songIds: Set<Long>) = songIds.forEach(downloadEngine::pauseDownload)
    fun resumeDownloads(songIds: Set<Long>) = songIds.forEach(downloadEngine::resumeDownload)
    fun cancelDownload(songId: Long) {
        downloadEngine.cancelDownload(songId)
        val current = _uiState.value.selectedIds
        if (songId in current) {
            _uiState.value = _uiState.value.copy(selectedIds = current - songId)
        }
    }
    fun dismissCompletedDownload(songId: Long) = downloadEngine.dismissCompletedRecord(songId)
    fun dismissCompletedDownloads() = downloadEngine.dismissAllCompletedRecords()
    fun removeDownload(songId: Long) = downloadEngine.removeTask(songId)

    fun loadAlbumFromResult(album: AlbumResult) {
        val prevMode = _uiState.value.sourceMode
        clearSearchState()
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null, sourceLabel = "专辑")
        viewModelScope.launch {
            awaitTokenReady()
            repository.getAlbumSongs(album.id.toString()).fold(
                onSuccess = { songs ->
                    _uiState.value = _uiState.value.copy(
                        songs = songs, isLoading = false,
                        selectedIds = emptySet(),
                        playlistName = "专辑 · ${songs.size}首",
                        statusMessage = "解析成功，共 ${songs.size} 首歌曲",
                        previousSourceMode = if (prevMode != SourceMode.SEARCH) prevMode else SourceMode.SEARCH
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "解析失败: ${e.message}"
                    )
                }
            )
        }
    }

    fun loadArtistDetail(artist: ArtistResult) {
        val prevMode = _uiState.value.sourceMode
        clearSearchState()
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            statusMessage = null,
            sourceLabel = "歌手",
            artistDetailName = artist.name,
            artistDetailAlbums = emptyList(),
            songs = emptyList()
        )
        viewModelScope.launch {
            val songsResult = repository.getArtistTopSongs(artist.id)
            val albumsResult = repository.getArtistAlbums(artist.id, 20, 0)
            val songs = songsResult.getOrNull() ?: emptyList()
            val albums = albumsResult.getOrNull() ?: emptyList()
            _uiState.value = _uiState.value.copy(
                songs = songs,
                artistDetailAlbums = albums,
                artistDetailName = artist.name,
                isLoading = false,
                selectedIds = emptySet(),
                statusMessage = "加载成功",
                sourceMode = SourceMode.ARTIST_DETAIL,
                previousSourceMode = if (prevMode != SourceMode.SEARCH) prevMode else SourceMode.SEARCH,
                searchCategory = SearchCategory.SONGS
            )
        }
    }

    @Deprecated("Replaced by loadArtistDetail")
    fun searchForArtist(artist: ArtistResult) {
        _uiState.value = _uiState.value.copy(
            searchQuery = artist.name,
            isSearching = true,
            statusMessage = null,
            sourceLabel = "搜索"
        )
        viewModelScope.launch {
            awaitTokenReady()
            repository.searchAll(artist.name).fold(
                onSuccess = { results ->
                    val msg = buildString {
                        append("找到 ")
                        val parts = mutableListOf<String>()
                        if (results.songs.isNotEmpty()) parts.add("${results.songs.size} 首歌曲")
                        if (results.albums.isNotEmpty()) parts.add("${results.albums.size} 张专辑")
                        if (results.artists.isNotEmpty()) parts.add("${results.artists.size} 位歌手")
                        append(parts.joinToString("、"))
                    }
                    _uiState.value = _uiState.value.copy(
                        songs = results.songs,
                        searchResults = results,
                        searchArtists = results.artists,
                        searchAlbums = results.albums,
                        searchCategory = SearchCategory.SONGS,
                        isSearching = false,
                        selectedIds = emptySet(),
                        playlistName = "搜索结果: ${artist.name}",
                        statusMessage = msg
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        statusMessage = "搜索失败: ${e.message}"
                    )
                }
            )
        }
    }

    // ── Discovery methods ──

    fun setSourceMode(mode: SourceMode) {
        val current = _uiState.value.sourceMode
        // Mark current visible items as seen before switching away
        markCurrentSectionSeen(current)
        // If tapping the same mode again, force refresh
        if (mode == current && mode != SourceMode.SEARCH) {
            refreshCurrentSource(mode)
            return
        }
        clearSearchState()
        _uiState.value = _uiState.value.copy(sourceMode = mode, previousSourceMode = SourceMode.SEARCH)
        when (mode) {
            SourceMode.CHARTS -> loadChartsIfNeeded()
            SourceMode.RECOMMEND -> loadRecommendIfNeeded()
            SourceMode.NEW_ALBUMS -> loadNewAlbumsIfNeeded()
            SourceMode.MY_PLAYLISTS -> loadMyPlaylistsIfNeeded()
            
            SourceMode.PERSONAL_FM -> loadFmSongs()
            else -> {}
        }
    }

    private fun refreshCurrentSource(mode: SourceMode) {
        when (mode) {
            SourceMode.CHARTS -> loadCharts()
            SourceMode.RECOMMEND -> loadRecommend()
            SourceMode.NEW_ALBUMS -> loadNewAlbums()
            SourceMode.MY_PLAYLISTS -> loadMyPlaylists()
            
            SourceMode.PERSONAL_FM -> loadFmSongs()
            else -> {}
        }
    }

    private fun markCurrentSectionSeen(mode: SourceMode) {
        when (mode) {
            SourceMode.CHARTS -> {
                val ids = _uiState.value.chartList.map { it.id }
                if (ids.isNotEmpty()) seenManager.markAllSeenCharts(ids)
            }
            SourceMode.NEW_ALBUMS -> {
                val ids = _uiState.value.newAlbumsList.map { it.id }
                if (ids.isNotEmpty()) seenManager.markAllSeenAlbums(ids)
            }
            SourceMode.MY_PLAYLISTS -> {
                val ids = _uiState.value.userPlaylists.map { it.id }
                if (ids.isNotEmpty()) seenManager.markAllSeenPlaylists(ids)
            }
            else -> {}
        }
    }

    // ── Cached-or-fetch helpers (used on first visit) ──

    private fun loadChartsIfNeeded() {
        if (_uiState.value.chartList.isNotEmpty()) return
        val cached = discoveryCache.getCachedCharts()
        if (cached != null) {
            _uiState.value = _uiState.value.copy(chartList = cached, isLoading = false)
        }
        loadCharts()
    }

    private fun loadRecommendIfNeeded() {
        if (_uiState.value.recommendSongs.isNotEmpty()) return
        val cached = discoveryCache.getCachedRecommend()
        if (cached != null) {
            _uiState.value = _uiState.value.copy(recommendSongs = cached, isLoading = false)
        }
        loadRecommend()
    }

    private fun loadNewAlbumsIfNeeded() {
        if (_uiState.value.newAlbumsList.isNotEmpty()) return
        val cached = discoveryCache.getCachedNewAlbums()
        if (cached != null) {
            _uiState.value = _uiState.value.copy(newAlbumsList = cached, isLoading = false)
        }
        loadNewAlbums()
    }

    private fun loadMyPlaylistsIfNeeded() {
        if (_uiState.value.userPlaylists.isNotEmpty()) return
        val cached = discoveryCache.getCachedUserPlaylists()
        if (cached != null) {
            _uiState.value = _uiState.value.copy(userPlaylists = cached, isLoading = false)
        }
        loadMyPlaylists()
    }

    private fun loadHotSearchIfNeeded() {
        if (_uiState.value.hotSearchKeywords.isNotEmpty()) return
        val cached = discoveryCache.getCachedHotSearch()
        if (cached != null) {
            _uiState.value = _uiState.value.copy(hotSearchKeywords = cached, isLoading = false)
        }
        loadHotSearch()
    }

    fun goBack() {
        val prev = _uiState.value.previousSourceMode
        if (prev != SourceMode.SEARCH && prev != SourceMode.ARTIST_DETAIL) {
            _uiState.value = _uiState.value.copy(
                sourceMode = prev,
                previousSourceMode = SourceMode.SEARCH,
                searchCategory = SearchCategory.SONGS
            )
        } else {
            clearSearchState()
            _uiState.value = _uiState.value.copy(
                sourceMode = SourceMode.SEARCH,
                previousSourceMode = SourceMode.SEARCH,
                songs = emptyList(),
                selectedIds = emptySet(),
                playlistName = "",
                sourceLabel = "",
                searchCategory = SearchCategory.SONGS,
                artistDetailName = "",
                artistDetailAlbums = emptyList()
            )
        }
    }

    fun loadCharts(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value.chartList.isNotEmpty()) return
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            repository.getChartList().fold(
                onSuccess = { charts ->
                    val fp = discoveryCache.chartFingerprint(charts)
                    if (!forceRefresh && _uiState.value.chartList.isNotEmpty() && discoveryCache.chartFingerprintMatches(fp)) {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        AppLogger.debug("Charts fingerprint unchanged, skipping UI update")
                        return@launch
                    }
                    val unseen = charts
                    _uiState.value = _uiState.value.copy(chartList = unseen, isLoading = false)
                    discoveryCache.saveCharts(charts)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "获取榜单失败: ${e.message}")
                }
            )
        }
    }

    fun loadChartSongs(chartId: Long, chartName: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null, sourceLabel = "榜单")
        viewModelScope.launch {
            repository.getChartSongs(chartId).fold(
                onSuccess = { result ->
                    _uiState.value = _uiState.value.copy(
                        songs = result.songs,
                        isLoading = false,
                        selectedIds = emptySet(),
                        playlistName = "$chartName · ${result.songs.size}首",
                        statusMessage = "加载成功，共 ${result.songs.size} 首",
                        sourceMode = SourceMode.SEARCH,
                        searchCategory = SearchCategory.SONGS,
                        previousSourceMode = SourceMode.CHARTS
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "加载榜单歌曲失败: ${e.message}"
                    )
                }
            )
        }
    }

    fun loadRecommend(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value.recommendSongs.isNotEmpty()) return
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            repository.getDailyRecommend().fold(
                onSuccess = { songs ->
                    val fp = discoveryCache.recommendFingerprint(songs)
                    if (!forceRefresh && _uiState.value.recommendSongs.isNotEmpty() && discoveryCache.recommendFingerprintMatches(fp)) {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        AppLogger.debug("Recommend fingerprint unchanged, skipping UI update")
                        return@launch
                    }
                    _uiState.value = _uiState.value.copy(recommendSongs = songs, isLoading = false)
                    discoveryCache.saveRecommend(songs)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "获取推荐失败: ${e.message}")
                }
            )
        }
    }

    fun loadRecommendSongsToPlaylist() {
        val songs = _uiState.value.recommendSongs
        if (songs.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            songs = songs,
            isLoading = false,
            selectedIds = emptySet(),
            playlistName = "每日推荐 · ${songs.size}首",
            statusMessage = "加载成功，共 ${songs.size} 首",
            sourceMode = SourceMode.SEARCH,
            searchCategory = SearchCategory.SONGS,
            previousSourceMode = SourceMode.RECOMMEND
        )
    }

    fun loadNewAlbums(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value.newAlbumsList.isNotEmpty()) return
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            repository.getNewAlbums(20, 0).fold(
                onSuccess = { albums ->
                    val fp = discoveryCache.newAlbumsFingerprint(albums)
                    if (!forceRefresh && _uiState.value.newAlbumsList.isNotEmpty() && discoveryCache.newAlbumsFingerprintMatches(fp)) {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        AppLogger.debug("NewAlbums fingerprint unchanged, skipping UI update")
                        return@launch
                    }
                    val allAlbums = albums
                    _uiState.value = _uiState.value.copy(newAlbumsList = allAlbums, isLoading = false, newAlbumsOffset = allAlbums.size, newAlbumsHasMore = albums.size >= 20)
                    discoveryCache.saveNewAlbums(albums)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "获取新专辑失败: ${e.message}")
                }
            )
        }
    }

    fun loadMoreNewAlbums() {
        val s = _uiState.value
        if (s.isLoadingMoreAlbums || !s.newAlbumsHasMore) return
        _uiState.value = s.copy(isLoadingMoreAlbums = true)
        viewModelScope.launch {
            repository.getNewAlbums(20, s.newAlbumsOffset).fold(
                onSuccess = { albums ->
                    val all = _uiState.value.newAlbumsList + albums
                    _uiState.value = _uiState.value.copy(
                        newAlbumsList = all,
                        isLoadingMoreAlbums = false,
                        newAlbumsOffset = s.newAlbumsOffset + albums.size,
                        newAlbumsHasMore = albums.size >= 20
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoadingMoreAlbums = false, statusMessage = "鍔犺浇鏇村澶辫触: ${e.message}")
                }
            )
        }
    }

    fun loadMyPlaylists(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value.userPlaylists.isNotEmpty()) return
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null)
        val userId = cookieManager.getUserId()
        if (userId <= 0L) {
            _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "未登录或无法获取用户信息")
            return
        }
        viewModelScope.launch {
            repository.getUserPlaylists(userId).fold(
                onSuccess = { playlists ->
                    val fp = discoveryCache.userPlaylistsFingerprint(playlists)
                    if (!forceRefresh && _uiState.value.userPlaylists.isNotEmpty() && discoveryCache.userPlaylistsFingerprintMatches(fp)) {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        AppLogger.debug("UserPlaylists fingerprint unchanged, skipping UI update")
                        return@launch
                    }
                    val unseen = playlists
                    _uiState.value = _uiState.value.copy(userPlaylists = unseen, isLoading = false)
                    discoveryCache.saveUserPlaylists(playlists)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "获取歌单失败: ${e.message}")
                }
            )
        }
    }

    fun loadUserPlaylistSongs(playlist: PlaylistApi.UserPlaylist) {
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null, sourceLabel = playlist.name)
        viewModelScope.launch {
            repository.getPlaylistSongs(playlist.id.toString()).fold(
                onSuccess = { songs ->
                    _uiState.value = _uiState.value.copy(
                        songs = songs,
                        isLoading = false,
                        selectedIds = emptySet(),
                        playlistName = "歌单 · ${songs.size}首",
                        statusMessage = "加载成功，共 ${songs.size} 首",
                        sourceMode = SourceMode.SEARCH,
                        searchCategory = SearchCategory.SONGS,
                        previousSourceMode = SourceMode.MY_PLAYLISTS
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "加载歌单失败: ${e.message}"
                    )
                }
            )
        }
    }

    fun loadHotSearch(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value.hotSearchKeywords.isNotEmpty()) return
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null)
        viewModelScope.launch {
            repository.getHotSearch().fold(
                onSuccess = { keywords ->
                    val fp = discoveryCache.hotSearchFingerprint(keywords)
                    if (!forceRefresh && _uiState.value.hotSearchKeywords.isNotEmpty() && discoveryCache.hotSearchFingerprintMatches(fp)) {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        AppLogger.debug("HotSearch fingerprint unchanged, skipping UI update")
                        return@launch
                    }
                    _uiState.value = _uiState.value.copy(hotSearchKeywords = keywords, isLoading = false)
                    discoveryCache.saveHotSearch(keywords)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "获取热搜失败: ${e.message}")
                }
            )
        }
    }

    // -- Personal FM --
    fun loadFmSongs() {
        _uiState.value = _uiState.value.copy(isLoadingFm = true, fmSongs = emptyList(), fmHistoryGroups = emptyList(), fmGroupCount = 0)
        viewModelScope.launch {
            repository.getPersonalFm(10).fold(
                onSuccess = { songs ->
                    _uiState.value = _uiState.value.copy(fmSongs = songs, fmHistoryGroups = listOf(songs), fmGroupCount = 1, isLoadingFm = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoadingFm = false, statusMessage = "FM 鍔犺浇澶辫触: ${e.message}")
                }
            )
        }
    }

    fun loadNextFmGroup() {
        val s = _uiState.value
        if (s.isLoadingFm) return
        _uiState.value = s.copy(isLoadingFm = true)
        viewModelScope.launch {
            repository.getPersonalFm(10).fold(
                onSuccess = { newSongs ->
                    val history = s.fmHistoryGroups.toMutableList()
                    history.add(newSongs)
                    var all = s.fmSongs.toMutableList()
                    all.addAll(newSongs)
                    if (history.size > 3) {
                        history.removeAt(0)
                        all = history.flatten().toMutableList()
                    }
                    _uiState.value = _uiState.value.copy(fmSongs = all, fmHistoryGroups = history, fmGroupCount = history.size, isLoadingFm = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoadingFm = false, statusMessage = "FM 鍔犺浇澶辫触: ${e.message}")
                }
            )
        }
    }

    fun hotSearchClick(keyword: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = keyword,
            sourceMode = SourceMode.SEARCH
        )
        search()
    }

    fun loadArtistDetail(artistId: Long, artistName: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null, sourceLabel = "歌手")
        viewModelScope.launch {
            val songsResult = repository.getArtistTopSongs(artistId)
            val albumsResult = repository.getArtistAlbums(artistId, 20, 0)
            val songs = songsResult.getOrNull() ?: emptyList()
            val albums = albumsResult.getOrNull() ?: emptyList()
            _uiState.value = _uiState.value.copy(
                songs = songs,
                artistDetailAlbums = albums,
                artistDetailName = artistName,
                isLoading = false,
                selectedIds = emptySet(),
                playlistName = "歌手: $artistName · ${songs.size}首",
                statusMessage = "加载成功",
                sourceMode = SourceMode.ARTIST_DETAIL,
                searchCategory = SearchCategory.SONGS
            )
        }
    }

    fun importAccountData(data: String): Boolean {
        if (data.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "数据不能为空")
            return false
        }

        // 解析导出的数据格式
        val musicU = extractField(data, "MUSIC_U")

        if (musicU.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "未找到 MUSIC_U 数据")
            return false
        }

        // 尝试导入
        val result = cookieManager.saveMusicUDirect(musicU)
        if (result.success) {
            saveCurrentUserId()
            _uiState.value = _uiState.value.copy(
                isLoggedIn = cookieManager.isLoggedIn(),
                userId = cookieManager.getUserId(),
                statusMessage = "导入成功"
            )
            return true
        } else {
            _uiState.value = _uiState.value.copy(statusMessage = result.message)
            return false
        }
    }

    private fun extractField(data: String, fieldName: String): String {
        val patterns = listOf(
            Regex("\\[$fieldName\\]\\s*\\n([^\\[\\n]+)"),
            Regex("$fieldName:\\s*([^\\n]+)"),
            Regex("\\[$fieldName\\]\\s*([^\\[\\n]+)")
        )
        for (pattern in patterns) {
            val match = pattern.find(data)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return ""
    }

    /**
     * 保存账号到历史记录
     */
    private suspend fun saveAccountHistory(cookies: Map<String, String>) {
        try {
            val loginStatus = repository.loginApi().checkLoginStatus(cookies)
            if (loginStatus.isLoggedIn && loginStatus.userId > 0) {
                val account = AccountHistory(
                    userId = loginStatus.userId,
                    nickname = loginStatus.nickname.ifBlank { "用户${loginStatus.userId}" },
                    cookieData = cookies
                )
                accountHistory.saveAccount(account)
                AppLogger.info("[MainVM] Saved account history: ${account.nickname}")
            }
        } catch (e: Exception) {
            AppLogger.error("[MainVM] Failed to save account history", e)
        }
    }

    /**
     * 获取历史账号列表
     */
    fun getAccountHistory(): List<AccountHistory> {
        return accountHistory.getAccounts()
    }

    /**
     * 删除历史账号
     */
    fun deleteAccountHistory(userId: Long) {
        accountHistory.deleteAccount(userId)
    }

    /**
     * 切换到历史账号
     */
    fun switchToAccount(account: AccountHistory): Boolean {
        val result = cookieManager.saveCookieMapForLogin(account.cookieData)
        if (result.success) {
            discoveryCache.clearAll()
            saveCurrentUserId()
            _uiState.value = _uiState.value.copy(
                isLoggedIn = cookieManager.isLoggedIn(),
                userId = cookieManager.getUserId(),
                statusMessage = "已切换到 ${account.nickname}"
            )
        } else {
            _uiState.value = _uiState.value.copy(
                statusMessage = "切换失败: ${result.message}"
            )
        }
        return result.success
    }
}
