package com.qing.hachimi.ui.screens

import com.qing.hachimi.data.local.CookieManager
import com.qing.hachimi.data.local.DiscoveryCacheManager
import com.qing.hachimi.data.local.SeenManager
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.api.ChartSongsResult
import com.qing.hachimi.data.model.ArtistArea
import com.qing.hachimi.data.model.AlbumResult
import com.qing.hachimi.data.model.ArtistDetailSection
import com.qing.hachimi.data.model.ArtistProfile
import com.qing.hachimi.data.model.ArtistResult
import com.qing.hachimi.data.model.CollectionContent
import com.qing.hachimi.data.model.CollectionDetail
import com.qing.hachimi.data.model.CollectionKind
import com.qing.hachimi.data.model.DiscoveryPage
import com.qing.hachimi.data.model.DiscoveryPlaylist
import com.qing.hachimi.data.model.PlaylistCategory
import com.qing.hachimi.data.model.SourceMode
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

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
    fun `playlist paging advances by server cursor and removes duplicate ids`() = runTest(dispatcher) {
        val repository = repositoryWithHomeContent()
        `when`(repository.getDiscoveryPlaylists(PlaylistCategory.ALL, 30, 0)).thenReturn(
            Result.success(
                DiscoveryPage(
                    items = listOf(playlist(1, "第一页")),
                    nextOffset = 30,
                    hasMore = true,
                ),
            ),
        )
        `when`(repository.getDiscoveryPlaylists(PlaylistCategory.ALL, 30, 30)).thenReturn(
            Result.success(
                DiscoveryPage(
                    items = listOf(playlist(1, "重复"), playlist(2, "第二页")),
                    nextOffset = 60,
                    hasMore = false,
                ),
            ),
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.setSourceMode(SourceMode.PLAYLISTS)
        advanceUntilIdle()
        viewModel.loadMoreCurrentSource()
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.uiState.value.discoveryPlaylists.map { it.id })
        assertEquals(60, viewModel.uiState.value.playlistOffset)
        assertFalse(viewModel.uiState.value.playlistsHasMore)
    }

    @Test
    fun `category back returns to recommendation home`() = runTest(dispatcher) {
        val viewModel = viewModel(repositoryWithHomeContent())
        advanceUntilIdle()

        viewModel.setSourceMode(SourceMode.CHARTS)
        assertTrue(viewModel.uiState.value.canNavigateBack)
        viewModel.goBack()

        assertEquals(SourceMode.RECOMMEND, viewModel.uiState.value.sourceMode)
        assertFalse(viewModel.uiState.value.canNavigateBack)
    }

    @Test
    fun `chart opened from home returns directly to home`() = runTest(dispatcher) {
        val chartId = 18_176_153_161L
        val repository = repositoryWithHomeContent()
        `when`(repository.getChartSongs(chartId)).thenReturn(
            Result.success(
                ChartSongsResult(
                    songs = listOf(song(1, "飙升歌曲")),
                    chartName = "飙升榜",
                    description = "每天更新的热门歌曲",
                    updateFrequency = "每天更新",
                ),
            ),
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.loadChartSongs(chartId, "飙升榜")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDetailView)
        assertEquals("每天更新的热门歌曲", viewModel.uiState.value.collectionDetail?.description)
        assertEquals("每天更新", viewModel.uiState.value.collectionDetail?.updateFrequency)

        viewModel.goBack()

        assertEquals(SourceMode.RECOMMEND, viewModel.uiState.value.sourceMode)
        assertFalse(viewModel.uiState.value.canNavigateBack)
    }

    @Test
    fun `chart opened from chart category returns to chart category`() = runTest(dispatcher) {
        val repository = repositoryWithHomeContent()
        `when`(repository.getChartSongs(19723756)).thenReturn(
            Result.success(ChartSongsResult(songs = listOf(song(1, "飙升歌曲")), chartName = "飙升榜")),
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.setSourceMode(SourceMode.CHARTS)
        viewModel.loadChartSongs(19723756, "飙升榜")
        advanceUntilIdle()
        viewModel.goBack()

        assertEquals(SourceMode.CHARTS, viewModel.uiState.value.sourceMode)
        assertTrue(viewModel.uiState.value.canNavigateBack)
    }

    @Test
    fun `album opened from artist returns to the same artist page`() = runTest(dispatcher) {
        val repository = repositoryWithHomeContent()
        val artist = ArtistResult(id = 48161, name = "Ariana Grande", avatarUrl = "avatar")
        val hotSong = song(1, "热门歌曲")
        val albumSong = song(2, "专辑歌曲")
        val album = AlbumResult(
            id = 390031118,
            name = "petal",
            artist = artist.name,
            publishTime = 1785427201000,
        )
        `when`(repository.getArtistProfile(artist.id)).thenReturn(
            Result.success(ArtistProfile(id = artist.id, name = artist.name, coverUrl = "hero")),
        )
        `when`(repository.getArtistTopSongs(artist.id)).thenReturn(Result.success(listOf(hotSong)))
        `when`(repository.getArtistAlbumsPage(artist.id, 30, 0)).thenReturn(
            Result.success(
                DiscoveryPage(
                    items = listOf(
                        album,
                        album.copy(id = 390031297, publishTime = 1785427200000),
                    ),
                    nextOffset = 2,
                    hasMore = false,
                ),
            ),
        )
        `when`(repository.getAlbumDetail(album.id.toString())).thenReturn(
            Result.success(
                CollectionContent(
                    detail = CollectionDetail(
                        kind = CollectionKind.ALBUM,
                        id = album.id,
                        title = album.name,
                        subtitle = album.artist,
                        description = "专辑简介",
                        publishTime = album.publishTime,
                        company = "Republic Records",
                        subtype = "录音室版",
                    ),
                    songs = listOf(albumSong),
                ),
            ),
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.loadArtistDetail(artist, SourceMode.SEARCH)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isArtistPage)
        assertTrue(viewModel.uiState.value.shouldReturnToSearch)

        viewModel.setArtistDetailSection(ArtistDetailSection.ALBUMS)
        advanceUntilIdle()
        assertEquals(listOf(390031118L), viewModel.uiState.value.artistDetailAlbums.map { it.id })

        viewModel.loadAlbumFromResult(album)
        advanceUntilIdle()
        assertEquals(listOf(albumSong), viewModel.uiState.value.songs)
        assertEquals("Republic Records", viewModel.uiState.value.collectionDetail?.company)

        viewModel.goBack()
        assertTrue(viewModel.uiState.value.isArtistPage)
        assertEquals(artist, viewModel.uiState.value.activeArtist)
        assertEquals(listOf(hotSong), viewModel.uiState.value.songs)
        assertEquals(null, viewModel.uiState.value.collectionDetail)
    }

    private suspend fun repositoryWithHomeContent(): NeteaseRepository {
        val repository = mock(NeteaseRepository::class.java)
        `when`(repository.getRecommendedPlaylists()).thenReturn(Result.success(emptyList()))
        `when`(repository.getChartList()).thenReturn(Result.success(emptyList()))
        `when`(repository.getNewAlbums(20, 0)).thenReturn(Result.success(emptyList()))
        `when`(repository.getDiscoveryArtists(ArtistArea.HOT, 30, 0))
            .thenReturn(Result.success(DiscoveryPage()))
        `when`(repository.getDiscoveryPodcasts(30, 0))
            .thenReturn(Result.success(DiscoveryPage()))
        return repository
    }

    private fun viewModel(repository: NeteaseRepository): DiscoverViewModel {
        val cookies = mock(CookieManager::class.java).also {
            `when`(it.isLoggedIn()).thenReturn(false)
            `when`(it.hasUsableToken()).thenReturn(true)
        }
        val settings = mock(SettingsManager::class.java).also {
            `when`(it.getGridColumns(anyString())).thenReturn(2)
        }
        return DiscoverViewModel(
            repository = repository,
            cookieManager = cookies,
            settingsManager = settings,
            discoveryCache = mock(DiscoveryCacheManager::class.java),
            seenManager = mock(SeenManager::class.java),
            downloadEngine = mock(DownloadEngine::class.java),
        )
    }

    private fun playlist(id: Long, name: String) = DiscoveryPlaylist(id = id, name = name)

    private fun song(id: Long, name: String) = Song(
        id = id,
        name = name,
        artists = "Artist",
        album = "Album",
        coverUrl = "",
    )
}
