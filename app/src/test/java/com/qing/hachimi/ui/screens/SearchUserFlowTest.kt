package com.qing.hachimi.ui.screens

import com.qing.hachimi.data.api.SearchAlbumsResult
import com.qing.hachimi.data.local.CookieManager
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.model.AlbumResult
import com.qing.hachimi.data.model.CollectionContent
import com.qing.hachimi.data.model.CollectionDetail
import com.qing.hachimi.data.model.CollectionKind
import com.qing.hachimi.data.model.DiscoveryPage
import com.qing.hachimi.data.model.DiscoveryPlaylist
import com.qing.hachimi.data.model.PodcastChannel
import com.qing.hachimi.data.model.SearchResults
import com.qing.hachimi.data.model.SearchCategory
import com.qing.hachimi.data.model.Song
import com.qing.hachimi.data.repository.NeteaseRepository
import com.qing.hachimi.downloader.DownloadEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class SearchUserFlowTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `back from album restores the parent search results`() = runTest(dispatcher) {
        val repository = mock(NeteaseRepository::class.java)
        val cookieManager = loggedInCookieManager()
        val parentSong = song(1, "Parent")
        val detailSong = song(2, "Detail")
        val parentResults = SearchResults(songs = listOf(parentSong))
        `when`(repository.getAlbumDetail("9")).thenReturn(
            Result.success(albumContent(9, "Album", listOf(detailSong))),
        )
        val viewModel = searchViewModel(repository, cookieManager)
        viewModel.uiState.value = SearchUiState(
            searchQuery = "query",
            songs = parentResults.songs,
            searchResults = parentResults,
        )

        viewModel.loadAlbumFromResult(AlbumResult(id = 9, name = "Album"))
        advanceUntilIdle()
        viewModel.goBack()

        assertEquals(parentResults.songs, viewModel.uiState.value.songs)
    }

    @Test
    fun `pasted album link loads the requested album id`() = runTest(dispatcher) {
        val repository = mock(NeteaseRepository::class.java)
        val cookieManager = loggedInCookieManager()
        val albumSong = song(390031118, "petal")
        `when`(repository.getAlbumDetail("390031118")).thenReturn(
            Result.success(
                albumContent(390031118, "petal", listOf(albumSong)).copy(
                    detail = CollectionDetail(
                        kind = CollectionKind.ALBUM,
                        id = 390031118,
                        title = "petal",
                        subtitle = "Ariana Grande",
                        description = "专辑简介",
                        publishTime = 1785427200000,
                        company = "Republic Records",
                        subtype = "录音室版",
                    ),
                ),
            ),
        )
        val viewModel = searchViewModel(repository, cookieManager)

        viewModel.updatePlaylistUrl("https://music.163.com/#/album?id=390031118")
        viewModel.parseUrl()
        advanceUntilIdle()

        assertEquals(listOf(albumSong), viewModel.uiState.value.songs)
        assertEquals("petal", viewModel.uiState.value.detailHeader?.title)
        assertEquals("专辑简介", viewModel.uiState.value.detailHeader?.description)
        assertEquals("Republic Records", viewModel.uiState.value.detailHeader?.company)
    }

    @Test
    fun `retry after a search error repeats the search rather than an old url`() = runTest(dispatcher) {
        val repository = mock(NeteaseRepository::class.java)
        val cookieManager = loggedInCookieManager()
        `when`(repository.searchAll("current query")).thenReturn(Result.success(SearchResults()))
        `when`(repository.getPlaylistDetail("123")).thenReturn(
            Result.success(playlistContent(123, "歌单", emptyList())),
        )
        val viewModel = searchViewModel(repository, cookieManager)
        viewModel.uiState.value = SearchUiState(
            searchQuery = "current query",
            playlistUrl = "123",
            sourceLabel = "搜索",
            statusMessage = "搜索失败",
        )

        viewModel.retry()
        advanceUntilIdle()

        verify(repository).searchAll("current query")
    }

    @Test
    fun `unexpected search error stops the loading indicator`() = runTest(dispatcher) {
        val repository = mock(NeteaseRepository::class.java)
        val cookieManager = loggedInCookieManager()
        `when`(repository.searchAll("query")).thenThrow(IllegalStateException("broken response"))
        val viewModel = searchViewModel(repository, cookieManager)
        viewModel.updateSearchQuery("query")

        viewModel.search()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isSearching)
        assertEquals("搜索失败: broken response", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun `download preparation ignores repeated taps`() {
        val repository = mock(NeteaseRepository::class.java)
        val viewModel = searchViewModel(repository, loggedInCookieManager())
        viewModel.uiState.value = SearchUiState(
            songs = listOf(song(1, "Song")),
            selectedIds = setOf(1L),
            isPreparingDownloads = true,
        )

        viewModel.downloadSelected()

        verifyNoInteractions(repository)
    }

    @Test
    fun `song controls remain available in album detail`() {
        val state = SearchUiState(
            songs = listOf(song(1, "Detail")),
            searchCategory = com.qing.hachimi.data.model.SearchCategory.SONGS,
            isDetailView = true,
        )

        assertEquals(true, shouldShowSearchSongControls(state))
    }

    @Test
    fun `exact artist album paging stays on artist discography`() = runTest(dispatcher) {
        val repository = mock(NeteaseRepository::class.java)
        val nextAlbum = AlbumResult(id = 390031118, name = "petal", artist = "Ariana Grande")
        `when`(repository.loadMoreArtistAlbums(48161, 50)).thenReturn(
            Result.success(
                SearchAlbumsResult(
                    albums = listOf(nextAlbum),
                    nextOffset = 100,
                    hasMore = false,
                ),
            ),
        )
        val viewModel = searchViewModel(repository, loggedInCookieManager())
        viewModel.uiState.value = SearchUiState(
            searchQuery = "Ariana Grande",
            searchCategory = SearchCategory.ALBUMS,
            searchResults = SearchResults(
                albumOffset = 50,
                hasMoreAlbums = true,
                albumArtistId = 48161,
            ),
        )

        viewModel.loadMoreSearch()
        advanceUntilIdle()

        assertEquals(listOf(390031118L), viewModel.uiState.value.searchAlbums.map { it.id })
        verify(repository).loadMoreArtistAlbums(48161, 50)
    }

    @Test
    fun `back from playlist restores the playlist search tab`() = runTest(dispatcher) {
        val repository = mock(NeteaseRepository::class.java)
        val playlist = DiscoveryPlaylist(
            id = 13563122536,
            name = "Ariana Grande 巡演歌单",
            creator = "创建者",
            trackCount = 26,
        )
        val detailSong = song(2, "Detail")
        `when`(repository.getPlaylistDetail(playlist.id.toString()))
            .thenReturn(Result.success(playlistContent(playlist.id, playlist.name, listOf(detailSong))))
        val viewModel = searchViewModel(repository, loggedInCookieManager())
        viewModel.uiState.value = SearchUiState(
            searchQuery = "Ariana Grande",
            searchPlaylists = listOf(playlist),
            searchResults = SearchResults(playlists = listOf(playlist)),
            searchCategory = SearchCategory.PLAYLISTS,
        )

        viewModel.loadPlaylistFromResult(playlist)
        advanceUntilIdle()

        assertEquals(SearchDetailKind.PLAYLIST, viewModel.uiState.value.detailHeader?.kind)
        assertEquals(listOf(detailSong), viewModel.uiState.value.songs)

        viewModel.goBack()

        assertEquals(SearchCategory.PLAYLISTS, viewModel.uiState.value.searchCategory)
        assertEquals(listOf(playlist), viewModel.uiState.value.searchPlaylists)
    }

    @Test
    fun `podcast detail keeps paging within the selected channel`() = runTest(dispatcher) {
        val repository = mock(NeteaseRepository::class.java)
        val podcast = PodcastChannel(
            id = 526811594,
            name = "Ariana Grande混音电台",
            host = "主播",
            programCount = 241,
        )
        val first = song(1, "第一期")
        val second = song(2, "第二期")
        `when`(repository.getPodcastPrograms(podcast, 50, 0)).thenReturn(
            Result.success(DiscoveryPage(items = listOf(first), nextOffset = 1, hasMore = true)),
        )
        `when`(repository.getPodcastPrograms(podcast, 50, 1)).thenReturn(
            Result.success(DiscoveryPage(items = listOf(second), nextOffset = 2, hasMore = false)),
        )
        val viewModel = searchViewModel(repository, loggedInCookieManager())
        viewModel.uiState.value = SearchUiState(
            searchQuery = "Ariana Grande",
            searchPodcasts = listOf(podcast),
            searchResults = SearchResults(podcasts = listOf(podcast)),
            searchCategory = SearchCategory.PODCASTS,
        )

        viewModel.loadPodcastFromResult(podcast)
        advanceUntilIdle()
        viewModel.loadMoreSearch()
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.uiState.value.songs.map { it.id })
        assertEquals(false, viewModel.uiState.value.detailHasMore)
        verify(repository).getPodcastPrograms(podcast, 50, 1)
    }

    private fun loggedInCookieManager() = mock(CookieManager::class.java).also {
        `when`(it.isLoggedIn()).thenReturn(true)
    }

    private fun searchViewModel(repository: NeteaseRepository, cookieManager: CookieManager) =
        SearchViewModel(
            repository = repository,
            cookieManager = cookieManager,
            settingsManager = mock(SettingsManager::class.java),
            downloadEngine = mock(DownloadEngine::class.java),
        )

    private fun song(id: Long, name: String) = Song(
        id = id,
        name = name,
        artists = "Artist",
        album = "Album",
        coverUrl = "",
    )

    private fun albumContent(id: Long, title: String, songs: List<Song>) = CollectionContent(
        detail = CollectionDetail(kind = CollectionKind.ALBUM, id = id, title = title),
        songs = songs,
    )

    private fun playlistContent(id: Long, title: String, songs: List<Song>) = CollectionContent(
        detail = CollectionDetail(kind = CollectionKind.PLAYLIST, id = id, title = title),
        songs = songs,
    )
}
