package com.qing.hachimi.data.repository

import com.qing.hachimi.data.api.*
import com.qing.hachimi.data.local.CookieManager
import com.qing.hachimi.data.model.*
import com.qing.hachimi.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private data class SearchBatch(
    val songs: Result<SearchSongsResult>,
    val albums: Result<SearchAlbumsResult>,
    val artists: Result<SearchArtistsResult>,
    val playlists: Result<SearchPlaylistsResult>,
    val podcasts: Result<SearchPodcastsResult>,
)

class NeteaseRepository(
    private val api: NeteaseApi,
    private val cookieManager: CookieManager,
    private val songApi: SongApi,
    private val playlistApi: PlaylistApi,
    private val searchApi: SearchApi,
    private val albumApi: AlbumApi,
    private val loginApi: LoginApi,
    private val artistApi: ArtistApi,
    private val discoveryApi: DiscoveryApi,
    private val cloudApi: CloudApi,
    private val listenDataApi: ListenDataApi,
    private val collectionApi: CollectionApi
) {

    private fun cookies() = cookieManager.getCookiesWithAnonFallback()

    suspend fun getPlaylistDetail(playlistId: String): Result<CollectionContent> = withContext(Dispatchers.IO) {
        try {
            AppLogger.debug("getPlaylistDetail: id=$playlistId")
            val info = playlistApi.getDetail(playlistId, cookies())
                ?: return@withContext Result.failure(Exception("无法获取歌单信息 (ID: $playlistId)"))
            val songs = info.songs.map { toSong(it) }
            AppLogger.debug("getPlaylistDetail: got ${songs.size} songs")
            Result.success(
                CollectionContent(
                    detail = CollectionDetail(
                        kind = CollectionKind.PLAYLIST,
                        id = info.id,
                        title = info.name,
                        subtitle = info.creator,
                        coverUrl = normalizeCoverUrl(info.coverUrl),
                        description = info.description,
                        expectedItemCount = info.trackCount.takeIf { it > 0 } ?: songs.size,
                        playCount = info.playCount,
                        publishTime = info.createTime,
                        updateTime = info.updateTime,
                        tags = info.tags,
                    ),
                    songs = songs,
                ),
            )
        } catch (e: Exception) {
            AppLogger.error("getPlaylistDetail failed", e)
            Result.failure(e)
        }
    }

    suspend fun getPlaylistSongs(playlistId: String): Result<List<Song>> =
        getPlaylistDetail(playlistId).map(CollectionContent::songs)

    suspend fun getSongUrl(songId: String, quality: String): Result<String> = withContext(Dispatchers.IO) {
        // Quality fallback: try selected quality first, then step down to lower qualities
        val fallbackOrder = NeteaseApi.fallbackQuality(quality)
        var lastError: String? = null

        for (q in fallbackOrder) {
            try {
                AppLogger.debug("getSongUrl: id=$songId quality=$q (trying)")
                val result = songApi.getUrl(songId, q, cookies())
                    ?: continue

                val url = result.url
                if (url.isNullOrBlank() || url == "null") {
                    AppLogger.warn("getSongUrl: url is null/blank for $songId at quality=$q, trying next")
                    continue
                }
                if (!url.startsWith("http")) {
                    AppLogger.warn("getSongUrl: invalid url for $songId: $url, trying next")
                    continue
                }
                val secureUrl = if (url.startsWith("http://")) "https://${url.substring(7)}" else url
                AppLogger.debug("getSongUrl: got url for $songId at quality=$q")
                return@withContext Result.success(secureUrl)
            } catch (e: Exception) {
                lastError = e.message
                AppLogger.warn("getSongUrl: quality=$q failed for $songId, trying next: ${e.message}")
            }
        }

        AppLogger.error("getSongUrl: all qualities failed for $songId, last error: $lastError")
        return@withContext Result.failure(Exception("所有音质均不可用，可能VIP限制，请尝试登录后重试"))
    }

    suspend fun getLyric(songId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val lyric = songApi.getLyric(songId, cookies())
                ?: return@withContext Result.failure(Exception("无法获取歌词 (ID: $songId)"))
            Result.success(lyric)
        } catch (e: Exception) {
            AppLogger.error("getLyric failed", e)
            Result.failure(e)
        }
    }

    suspend fun getLyricFull(songId: String): SongApi.LyricResult? = withContext(Dispatchers.IO) {
        try {
            songApi.getLyricFull(songId, cookies())
        } catch (e: Exception) {
            AppLogger.error("getLyricFull failed", e)
            null
        }
    }

    suspend fun search(keywords: String, limit: Int = 30): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            AppLogger.debug("search: keywords=$keywords limit=$limit")
            val result = searchApi.searchSongs(keywords, cookies(), limit)
            AppLogger.debug("search: got ${result.songs.size} results (total=$result.total)")
            if (result.songs.isEmpty()) {
                return@withContext Result.failure(Exception("未找到搜索结果: $keywords"))
            }
            Result.success(result.songs)
        } catch (e: Exception) {
            AppLogger.error("search failed for '$keywords'", e)
            Result.failure(Exception("搜索失败: ${e.message}"))
        }
    }

    suspend fun getSearchSuggestions(keywords: String): Result<List<SearchSuggestion>> = withContext(Dispatchers.IO) {
        try {
            Result.success(discoveryApi.getSearchSuggestions(keywords, cookies()))
        } catch (e: Exception) {
            AppLogger.warn("getSearchSuggestions failed for '$keywords': ${e.message}")
            Result.success(emptyList())
        }
    }

    suspend fun searchAll(keywords: String): Result<SearchResults> = withContext(Dispatchers.IO) {
        try {
            AppLogger.debug("searchAll: keywords=$keywords")
            val requestCookies = cookies()
            val batch = coroutineScope {
                val songsRequest = async { runCatching { searchApi.searchSongs(keywords, requestCookies, 30, 0) } }
                val albumsRequest = async { runCatching { searchApi.searchAlbums(keywords, requestCookies, 20, 0) } }
                val artistsRequest = async { runCatching { searchApi.searchArtists(keywords, requestCookies, 20, 0) } }
                val playlistsRequest = async { runCatching { searchApi.searchPlaylists(keywords, requestCookies, 20, 0) } }
                val podcastsRequest = async { runCatching { searchApi.searchPodcasts(keywords, requestCookies, 20, 0) } }
                SearchBatch(
                    songs = songsRequest.await(),
                    albums = albumsRequest.await(),
                    artists = artistsRequest.await(),
                    playlists = playlistsRequest.await(),
                    podcasts = podcastsRequest.await(),
                )
            }
            val failures = listOf(batch.songs, batch.albums, batch.artists, batch.playlists, batch.podcasts)
                .mapNotNull { it.exceptionOrNull() }
            if (failures.size == 5) throw failures.first()
            failures.forEach { AppLogger.warn("searchAll: partial category failure: ${it.message}") }

            val songs = batch.songs.getOrDefault(SearchSongsResult())
            val albums = batch.albums.getOrDefault(SearchAlbumsResult())
            val artists = batch.artists.getOrDefault(SearchArtistsResult())
            val playlists = batch.playlists.getOrDefault(SearchPlaylistsResult())
            val podcasts = batch.podcasts.getOrDefault(SearchPodcastsResult())
            val exactArtist = artists.artists.firstOrNull { matchesExactArtist(keywords, it) }
            val artistAlbumPage = exactArtist?.let { artist ->
                runCatching {
                    artistApi.getAlbumsPage(
                        artistId = artist.id,
                        cookies = requestCookies,
                        limit = SEARCH_ARTIST_ALBUM_PAGE_SIZE,
                        offset = 0,
                    )
                }.onFailure { error ->
                    AppLogger.warn("searchAll: artist albums fallback failed for ${artist.id}: ${error.message}")
                }.getOrNull()
            }
            val resolvedAlbums = artistAlbumPage?.items ?: albums.albums
            AppLogger.debug(
                "searchAll: songs=${songs.songs.size}, albums=${albums.albums.size}, " +
                    "artists=${artists.artists.size}, playlists=${playlists.playlists.size}, " +
                    "podcasts=${podcasts.podcasts.size}",
            )
            Result.success(SearchResults(
                songs = songs.songs.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) },
                artists = artists.artists.map { it.copy(avatarUrl = normalizeCoverUrl(it.avatarUrl)) },
                albums = resolvedAlbums.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) },
                playlists = playlists.playlists.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) },
                podcasts = podcasts.podcasts.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) },
                hasMoreSongs = songs.hasMore,
                hasMoreArtists = artists.hasMore,
                hasMoreAlbums = artistAlbumPage?.hasMore ?: albums.hasMore,
                hasMorePlaylists = playlists.hasMore,
                hasMorePodcasts = podcasts.hasMore,
                songOffset = songs.nextOffset,
                artistOffset = artists.nextOffset,
                albumOffset = artistAlbumPage?.nextOffset ?: albums.nextOffset,
                playlistOffset = playlists.nextOffset,
                podcastOffset = podcasts.nextOffset,
                albumArtistId = artistAlbumPage?.let { exactArtist.id },
            ))
        } catch (e: Exception) {
            AppLogger.error("searchAll failed for '$keywords'", e)
            Result.failure(Exception("搜索失败: ${e.message}"))
        }
    }

    suspend fun loadMoreSongs(keywords: String, offset: Int): Result<SearchSongsResult> = withContext(Dispatchers.IO) {
        try {
            val result = searchApi.searchSongs(keywords, cookies(), 30, offset)
            Result.success(SearchSongsResult(
                songs = result.songs.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) },
                total = result.total,
                nextOffset = result.nextOffset,
                hasMore = result.hasMore
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadMoreAlbums(keywords: String, offset: Int): Result<SearchAlbumsResult> = withContext(Dispatchers.IO) {
        try {
            val result = searchApi.searchAlbums(keywords, cookies(), 20, offset)
            Result.success(SearchAlbumsResult(
                albums = result.albums.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) },
                total = result.total,
                nextOffset = result.nextOffset,
                hasMore = result.hasMore
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSongTagMetadata(songId: String): SongTagMetadata = withContext(Dispatchers.IO) {
        coroutineScope {
            val detailRequest = async {
                runCatching { songApi.getDetail(songId, cookies()) }
                    .onFailure { AppLogger.warn("getSongTagMetadata detail failed for $songId: ${it.message}") }
                    .getOrNull()
            }
            val creatorRequest = async {
                runCatching { songApi.getCreators(songId, cookies()) }
                    .onFailure { AppLogger.warn("getSongTagMetadata creators failed for $songId: ${it.message}") }
                    .getOrNull()
            }
            val detail = detailRequest.await()
            val creators = creatorRequest.await()
            SongTagMetadata(
                albumArtist = detail?.albumArtist.orEmpty(),
                aliases = detail?.aliases.orEmpty(),
                translatedTitles = detail?.translatedTitles.orEmpty(),
                trackNumber = detail?.trackNumber,
                trackTotal = detail?.trackTotal,
                discNumber = detail?.discNumber,
                discTotal = detail?.discTotal,
                releaseYear = detail?.releaseYear,
                lyricists = creators?.lyricists.orEmpty(),
                composers = creators?.composers.orEmpty(),
                arrangers = creators?.arrangers.orEmpty(),
                producers = creators?.producers.orEmpty(),
                mixers = creators?.mixers.orEmpty(),
                engineers = creators?.engineers.orEmpty(),
                remixers = creators?.remixers.orEmpty(),
            )
        }
    }

    suspend fun loadMoreArtistAlbums(artistId: Long, offset: Int): Result<SearchAlbumsResult> = withContext(Dispatchers.IO) {
        try {
            val page = artistApi.getAlbumsPage(
                artistId = artistId,
                cookies = cookies(),
                limit = SEARCH_ARTIST_ALBUM_PAGE_SIZE,
                offset = offset,
            )
            Result.success(
                SearchAlbumsResult(
                    albums = page.items.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) },
                    nextOffset = page.nextOffset,
                    hasMore = page.hasMore,
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadMoreArtists(keywords: String, offset: Int): Result<SearchArtistsResult> = withContext(Dispatchers.IO) {
        try {
            val result = searchApi.searchArtists(keywords, cookies(), 20, offset)
            Result.success(SearchArtistsResult(
                artists = result.artists.map { it.copy(avatarUrl = normalizeCoverUrl(it.avatarUrl)) },
                total = result.total,
                nextOffset = result.nextOffset,
                hasMore = result.hasMore
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAlbumDetail(albumId: String): Result<CollectionContent> = withContext(Dispatchers.IO) {
        try {
            AppLogger.debug("getAlbumDetail: id=$albumId")
            val info = albumApi.getDetail(albumId, cookies())
                ?: return@withContext Result.failure(Exception("无法获取专辑信息 (ID: $albumId)"))
            val songs = info.songs.map { toSong(it) }
            AppLogger.debug("getAlbumDetail: got ${songs.size} songs")
            Result.success(
                CollectionContent(
                    detail = CollectionDetail(
                        kind = CollectionKind.ALBUM,
                        id = info.id,
                        title = info.name,
                        subtitle = info.artist,
                        coverUrl = normalizeCoverUrl(info.coverUrl),
                        description = info.description,
                        expectedItemCount = info.size.takeIf { it > 0 } ?: songs.size,
                        publishTime = info.publishTime,
                        company = info.company,
                        subtype = info.subtype,
                    ),
                    songs = songs,
                ),
            )
        } catch (e: Exception) {
            AppLogger.error("getAlbumDetail failed", e)
            Result.failure(e)
        }
    }

    suspend fun getAlbumSongs(albumId: String): Result<List<Song>> =
        getAlbumDetail(albumId).map(CollectionContent::songs)

    suspend fun getSingleSong(songId: String): Result<Song> = withContext(Dispatchers.IO) {
        try {
            AppLogger.debug("getSingleSong: id=$songId")
            val detail = songApi.getDetail(songId, cookies())
                ?: return@withContext Result.failure(Exception("无法获取歌曲信息 (ID: $songId)"))
            AppLogger.debug("getSingleSong: ${detail.name}")
            Result.success(
                Song(
                    id = detail.id,
                    name = detail.name,
                    artists = detail.artists,
                    album = detail.album,
                    coverUrl = normalizeCoverUrl(detail.coverUrl)
                )
            )
        } catch (e: Exception) {
            AppLogger.error("getSingleSong failed", e)
            Result.failure(e)
        }
    }

    fun loginApi(): LoginApi = loginApi

    fun cookieManager(): CookieManager = cookieManager

    suspend fun qrKey(): LoginApi.QrResult? = withContext(Dispatchers.IO) {
        try {
            loginApi.qrKey()
        } catch (e: Exception) {
            AppLogger.error("qrKey failed", e)
            null
        }
    }

    suspend fun checkQr(unikey: String, sessionCookies: Map<String, String> = emptyMap()): LoginApi.QrStatus = withContext(Dispatchers.IO) {
        try {
            loginApi.checkQr(unikey, sessionCookies)
        } catch (e: Exception) {
            AppLogger.error("checkQr failed", e)
            LoginApi.QrStatus(-1, message = "检查状态失败: ${e.message}")
        }
    }

    // ── Discovery methods ──

    suspend fun getChartList(): Result<List<ChartInfo>> = withContext(Dispatchers.IO) {
        try {
            Result.success(discoveryApi.getChartList(cookies()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecommendedPlaylists(limit: Int = 12): Result<List<DiscoveryPlaylist>> =
        withContext(Dispatchers.IO) {
            runCatching {
                discoveryApi.getRecommendedPlaylists(cookies(), limit).map { playlist ->
                    playlist.copy(coverUrl = normalizeCoverUrl(playlist.coverUrl))
                }
            }.onFailure { AppLogger.error("getRecommendedPlaylists failed", it) }
        }

    suspend fun getDiscoveryPlaylists(
        category: PlaylistCategory,
        limit: Int = 30,
        offset: Int = 0,
    ): Result<DiscoveryPage<DiscoveryPlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            discoveryApi.getPlaylists(cookies(), category.apiValue, limit, offset).let { page ->
                page.copy(items = page.items.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) })
            }
        }.onFailure { AppLogger.error("getDiscoveryPlaylists failed", it) }
    }

    /** 按曲风标签加载歌单（playlist/list 的 cat 支持动态曲风名） */
    suspend fun getDiscoveryPlaylistsByTag(
        tag: String,
        limit: Int = 30,
        offset: Int = 0,
    ): Result<DiscoveryPage<DiscoveryPlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            discoveryApi.getPlaylists(cookies(), tag, limit, offset).let { page ->
                page.copy(items = page.items.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) })
            }
        }.onFailure { AppLogger.error("getDiscoveryPlaylistsByTag($tag) failed", it) }
    }

    suspend fun getDiscoveryArtists(
        area: ArtistArea,
        limit: Int = 30,
        offset: Int = 0,
    ): Result<DiscoveryPage<ArtistResult>> = withContext(Dispatchers.IO) {
        runCatching {
            discoveryApi.getArtists(cookies(), area, limit, offset).let { page ->
                page.copy(items = page.items.map { it.copy(avatarUrl = normalizeCoverUrl(it.avatarUrl)) })
            }
        }.onFailure { AppLogger.error("getDiscoveryArtists failed", it) }
    }

    suspend fun getDiscoveryPodcasts(
        limit: Int = 30,
        offset: Int = 0,
    ): Result<DiscoveryPage<PodcastChannel>> = withContext(Dispatchers.IO) {
        runCatching {
            discoveryApi.getPodcasts(cookies(), limit, offset).let { page ->
                page.copy(items = page.items.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) })
            }
        }.onFailure { AppLogger.error("getDiscoveryPodcasts failed", it) }
    }

    suspend fun getPodcastPrograms(
        channel: PodcastChannel,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<DiscoveryPage<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            discoveryApi.getPodcastPrograms(channel, cookies(), limit, offset).let { page ->
                page.copy(items = page.items.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) })
            }
        }.onFailure { AppLogger.error("getPodcastPrograms failed", it) }
    }

    suspend fun getPodcastDetail(id: Long): Result<PodcastChannel> = withContext(Dispatchers.IO) {
        runCatching {
            discoveryApi.getPodcastDetail(id, cookies()).let { podcast ->
                podcast.copy(coverUrl = normalizeCoverUrl(podcast.coverUrl))
            }
        }.onFailure { AppLogger.error("getPodcastDetail failed", it) }
    }

    suspend fun getChartSongs(chartId: Long): Result<ChartSongsResult> = withContext(Dispatchers.IO) {
        try {
            val result = discoveryApi.getChartSongs(chartId, cookies())
            Result.success(result.copy(
                songs = result.songs.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) }
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDailyRecommend(): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val songs = discoveryApi.getDailyRecommendSongs(cookies())
            Result.success(songs.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNewAlbums(limit: Int = 20, offset: Int = 0): Result<List<AlbumResult>> = withContext(Dispatchers.IO) {
        try {
            Result.success(discoveryApi.getNewAlbums(cookies(), limit, offset)
                .map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHotSearch(): Result<List<Pair<String, Int>>> = withContext(Dispatchers.IO) {
        try {
            Result.success(discoveryApi.getHotSearchKeywords(cookies()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // -- Personal FM methods --
    suspend fun getPersonalFm(limit: Int = 10): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val payload = org.json.JSONObject().put("limit", limit).toString()
            val raw = api.weapiPost("v1/radio/get", payload, cookies(), callTimeoutMs = 10_000L)
            val obj = org.json.JSONObject(raw)
            if (obj.optLong("code") != 200L) {
                val msg = obj.optString("message").ifEmpty { "绉佷汉FM鑾峰彇澶辫触" }
                return@withContext Result.failure(Exception(msg))
            }
            val dataArr = obj.optJSONArray("data") ?: return@withContext Result.success(emptyList())
            val songs = (0 until dataArr.length()).map { i ->
                val s = dataArr.getJSONObject(i)
                val al = s.optJSONObject("al")
                val ar = s.optJSONArray("ar")
                Song(
                    id = s.optLong("id"),
                    name = s.optString("name"),
                    artists = if (ar != null) {
                        (0 until ar.length()).joinToString("、") { ar.getJSONObject(it).optString("name") }
                    } else "",
                    album = al?.optString("name") ?: "",
                    coverUrl = normalizeCoverUrl(al?.optString("picUrl") ?: "")
                )
            }
            Result.success(songs)
        } catch (e: Exception) {
            AppLogger.error("getPersonalFm failed", e)
            Result.failure(e)
        }
    }

    // -- Cloud disk methods --
    suspend fun getCloudSongs(limit: Int = 30, offset: Int = 0): Result<List<CloudApi.CloudSong>> = withContext(Dispatchers.IO) {
        try {
            cloudApi.getCloudSongs(limit, offset, cookies()).map { result ->
                result.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) }
            }
        } catch (e: Exception) {
            AppLogger.error("getCloudSongs failed", e)
            Result.failure(e)
        }
    }

    suspend fun getCloudDownloadUrl(songId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val info = cloudApi.getCloudDownloadUrl(songId, cookies())
            if (info == null || info.url.isNullOrBlank()) {
                return@withContext Result.failure(Exception("鏃犳硶鑾峰彇浜戠洏涓嬭浇閾炬帴"))
            }
            val secureUrl = if (info.url.startsWith("http://")) "https://${info.url.substring(7)}" else info.url
            Result.success(secureUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    // ── User playlist methods ──

    suspend fun getUserPlaylists(userId: Long, limit: Int = 50, offset: Int = 0): Result<List<PlaylistApi.UserPlaylist>> = withContext(Dispatchers.IO) {
        try {
            val list = playlistApi.getUserPlaylists(userId, cookies(), limit, offset)
            Result.success(list.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLikedSongs(userId: Long): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val list = playlistApi.getLikedSongs(userId, cookies())
            Result.success(list.map { toSong(it) })
        } catch (e: Exception) {
            AppLogger.error("getLikedSongs failed", e)
            Result.failure(e)
        }
    }

    // ── Artist detail methods ──

    suspend fun getArtistTopSongs(artistId: Long): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val songs = artistApi.getTopSongs(artistId, cookies())
            Result.success(songs.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getArtistProfile(artistId: Long): Result<ArtistProfile> = withContext(Dispatchers.IO) {
        try {
            val profile = artistApi.getProfile(artistId, cookies())
            Result.success(
                profile.copy(
                    coverUrl = normalizeCoverUrl(profile.coverUrl),
                    avatarUrl = normalizeCoverUrl(profile.avatarUrl),
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadMorePlaylists(keywords: String, offset: Int): Result<SearchPlaylistsResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                searchApi.searchPlaylists(keywords, cookies(), 20, offset).let { result ->
                    result.copy(
                        playlists = result.playlists.map {
                            it.copy(coverUrl = normalizeCoverUrl(it.coverUrl))
                        },
                    )
                }
            }
        }

    suspend fun loadMorePodcasts(keywords: String, offset: Int): Result<SearchPodcastsResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                searchApi.searchPodcasts(keywords, cookies(), 20, offset).let { result ->
                    result.copy(
                        podcasts = result.podcasts.map {
                            it.copy(coverUrl = normalizeCoverUrl(it.coverUrl))
                        },
                    )
                }
            }
        }

    suspend fun getArtistAlbumsPage(
        artistId: Long,
        limit: Int = 30,
        offset: Int = 0,
    ): Result<DiscoveryPage<AlbumResult>> = withContext(Dispatchers.IO) {
        try {
            val page = artistApi.getAlbumsPage(artistId, cookies(), limit, offset)
            Result.success(page.copy(items = page.items.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getArtistAlbums(artistId: Long, limit: Int = 50, offset: Int = 0): Result<List<AlbumResult>> = withContext(Dispatchers.IO) {
        try {
            val albums = artistApi.getAlbums(artistId, cookies(), limit, offset)
            Result.success(albums.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getArtistIntroduction(artistId: Long): Result<ArtistAbout> = withContext(Dispatchers.IO) {
        try {
            Result.success(artistApi.getIntroduction(artistId, cookies()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSimilarArtists(artistId: Long): Result<List<ArtistResult>> = withContext(Dispatchers.IO) {
        try {
            val artists = artistApi.getSimilarArtists(artistId, cookies())
            Result.success(artists.map { it.copy(avatarUrl = normalizeCoverUrl(it.avatarUrl)) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun toSong(it: PlaylistApi.SongInfo) = Song(it.id, it.name, it.artists, it.album, normalizeCoverUrl(it.coverUrl))

    private fun toRecordEntry(it: ListenDataApi.RecordEntry) =
        it.copy(song = it.song.copy(coverUrl = normalizeCoverUrl(it.song.coverUrl)))

    // ── Listen data methods（听歌排行/最近播放/听歌足迹） ──

    suspend fun getUserRecord(userId: Long, type: Int): Result<List<ListenDataApi.RecordEntry>> = withContext(Dispatchers.IO) {
        try {
            Result.success(listenDataApi.getUserRecord(userId, type, cookies()).map { toRecordEntry(it) })
        } catch (e: Exception) {
            AppLogger.error("getUserRecord failed", e)
            Result.failure(e)
        }
    }

    suspend fun getRecentListen(): Result<List<ListenDataApi.RecordEntry>> = withContext(Dispatchers.IO) {
        try {
            Result.success(listenDataApi.getRecentListen(cookies()).map { toRecordEntry(it) })
        } catch (e: Exception) {
            AppLogger.error("getRecentListen failed", e)
            Result.failure(e)
        }
    }

    suspend fun getTodaySongRank(): Result<List<ListenDataApi.RecordEntry>> = withContext(Dispatchers.IO) {
        try {
            Result.success(listenDataApi.getTodaySongRank(cookies()).map { toRecordEntry(it) })
        } catch (e: Exception) {
            AppLogger.error("getTodaySongRank failed", e)
            Result.failure(e)
        }
    }

    suspend fun getSongPlayRank(type: String): Result<List<ListenDataApi.RecordEntry>> = withContext(Dispatchers.IO) {
        try {
            Result.success(listenDataApi.getSongPlayRank(type, cookies()).map { toRecordEntry(it) })
        } catch (e: Exception) {
            AppLogger.error("getSongPlayRank failed", e)
            Result.failure(e)
        }
    }

    suspend fun getYearReport(): Result<ListenDataApi.YearReport?> = withContext(Dispatchers.IO) {
        try {
            val report = listenDataApi.getYearReport(cookies())
            Result.success(report?.let {
                it.copy(topSongs = it.topSongs.map { entry -> toRecordEntry(entry) })
            })
        } catch (e: Exception) {
            AppLogger.error("getYearReport failed", e)
            Result.failure(e)
        }
    }

    suspend fun getAnnualSummary(year: String): Result<ListenDataApi.YearReport?> = withContext(Dispatchers.IO) {
        try {
            val report = listenDataApi.getAnnualSummary(year, cookies())
            Result.success(report?.let {
                it.copy(topSongs = it.topSongs.map { entry -> toRecordEntry(entry) })
            })
        } catch (e: Exception) {
            AppLogger.error("getAnnualSummary failed", e)
            Result.failure(e)
        }
    }

    // ── Collection methods（收藏中心） ──

    suspend fun getSubscribedAlbums(limit: Int = 25, offset: Int = 0): Result<CollectionApi.CollectionPage<AlbumResult>> = withContext(Dispatchers.IO) {
        try {
            val page = collectionApi.getSubscribedAlbums(cookies(), limit, offset)
            Result.success(
                page.copy(items = page.items.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) })
            )
        } catch (e: Exception) {
            AppLogger.error("getSubscribedAlbums failed", e)
            Result.failure(e)
        }
    }

    suspend fun getSubscribedArtists(limit: Int = 25, offset: Int = 0): Result<CollectionApi.CollectionPage<ArtistResult>> = withContext(Dispatchers.IO) {
        try {
            val page = collectionApi.getSubscribedArtists(cookies(), limit, offset)
            Result.success(
                page.copy(items = page.items.map { it.copy(avatarUrl = normalizeCoverUrl(it.avatarUrl)) })
            )
        } catch (e: Exception) {
            AppLogger.error("getSubscribedArtists failed", e)
            Result.failure(e)
        }
    }

    // ── Discovery extensions（新歌速递/曲风） ──

    suspend fun getNewSongs(areaId: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val songs = discoveryApi.getNewSongs(cookies(), areaId)
            Result.success(songs.map { it.copy(coverUrl = normalizeCoverUrl(it.coverUrl)) })
        } catch (e: Exception) {
            AppLogger.error("getNewSongs failed", e)
            Result.failure(e)
        }
    }

    suspend fun getStyleTags(): Result<List<StyleTag>> = withContext(Dispatchers.IO) {
        try {
            Result.success(discoveryApi.getStyleTags(cookies()))
        } catch (e: Exception) {
            AppLogger.error("getStyleTags failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val DEFAULT_COVER_SIZE = 640
        private const val SEARCH_ARTIST_ALBUM_PAGE_SIZE = 50
        private val SEARCH_PUNCTUATION = Regex("[\\p{P}]+")
        private val SEARCH_WHITESPACE = Regex("\\s+")

        private fun matchesExactArtist(query: String, artist: ArtistResult): Boolean {
            val normalizedQuery = normalizeSearchText(query)
            if (normalizedQuery == normalizeSearchText(artist.name)) return true
            return artist.alias
                .split('、', ',', '/')
                .any { normalizedQuery == normalizeSearchText(it) }
        }

        private fun normalizeSearchText(value: String): String = Normalizer
            .normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(SEARCH_PUNCTUATION, " ")
            .trim()
            .replace(SEARCH_WHITESPACE, " ")

        /**
         * Normalize cover URLs for reliable loading:
         * - Ensure HTTPS protocol (required for Android cleartext restrictions)
         * - Convert p1/p3/p4 CDN to p2 (p1/p3 may not support HTTPS well)
         * - Add size param if missing (required by Netease CDN)
         */
        private fun normalizeCoverUrl(url: String): String {
            if (url.isBlank()) return ""

            var normalized = url

            // Convert p1/p3/p4 CDN to p2 (all support HTTPS)
            for (cdn in listOf("p1", "p3", "p4")) {
                if (normalized.startsWith("http://$cdn.music.126.net/")) {
                    normalized = normalized.replaceFirst("http://$cdn.music.126.net/", "https://p2.music.126.net/")
                    break
                } else if (normalized.startsWith("https://$cdn.music.126.net/")) {
                    normalized = normalized.replaceFirst("https://$cdn.music.126.net/", "https://p2.music.126.net/")
                    break
                }
            }

            // Convert remaining http to https
            if (normalized.startsWith("http://")) {
                normalized = "https://" + normalized.substring(7)
            }

            // Add size param if no query params exist (CDN requires it)
            if (normalized.isNotEmpty() && !normalized.contains("?")) {
                normalized += "?param=${DEFAULT_COVER_SIZE}y${DEFAULT_COVER_SIZE}"
            }

            return normalized
        }

        /** 替换封面 URL 中的 param 参数为指定尺寸 (网易云 CDN 支持 param 参数) */
        fun coverDisplayUrl(url: String, size: Int = DEFAULT_COVER_SIZE): String {
            if (url.isBlank()) return ""
            if (url.contains("param=")) {
                return url.replace(Regex("param=\\d+y\\d+"), "param=${size}y${size}")
            }
            if (url.contains("music.126.net")) {
                return if (url.contains("?")) "$url&param=${size}y${size}" else "$url?param=${size}y${size}"
            }
            return url
        }

        /** Remove only NetEase's resize instruction so tag artwork uses the source asset. */
        fun coverOriginalUrl(url: String): String {
            if (url.isBlank()) return ""
            val parsed = url.toHttpUrlOrNull() ?: return url
            return parsed.newBuilder()
                .removeAllQueryParameters("param")
                .build()
                .toString()
        }
    }
}
