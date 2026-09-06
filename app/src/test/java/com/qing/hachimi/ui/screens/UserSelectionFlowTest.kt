package com.qing.hachimi.ui.screens

import com.qing.hachimi.data.api.CloudApi
import com.qing.hachimi.data.api.ListenDataApi
import com.qing.hachimi.data.local.CookieManager
import com.qing.hachimi.data.local.DiscoveryCacheManager
import com.qing.hachimi.data.local.SeenManager
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.model.Song
import com.qing.hachimi.data.model.SourceMode
import com.qing.hachimi.data.model.ArtistArea
import com.qing.hachimi.data.model.DiscoveryPage
import com.qing.hachimi.data.repository.NeteaseRepository
import com.qing.hachimi.downloader.DownloadEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class UserSelectionFlowTest {

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
    fun `cloud select all selects visible cloud songs`() {
        val viewModel = MyViewModel(
            repository = mock(),
            cookieManager = mock(),
            settingsManager = mock(),
            downloadEngine = mock(),
            discoveryCache = mock(),
        )
        val songs = listOf(
            cloudSong(1, "First"),
            cloudSong(2, "Second"),
        )
        setMyState(
            viewModel,
            MyUiState(section = MySection.CLOUD, cloudSongs = songs),
        )

        viewModel.toggleSelectAll()

        assertEquals(setOf(1L, 2L), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun `empty selection resolves to all visible songs for download`() {
        val songs = listOf(song(1, "First"), song(2, "Second"))

        assertEquals(songs, songsForDownload(songs, emptySet()))
        assertEquals(listOf(songs[1]), songsForDownload(songs, setOf(2L)))
    }

    @Test
    fun `download all from my home playlist starts preparation`() {
        assertDownloadAllPrepares(
            MyUiState(
                section = MySection.HOME,
                songs = listOf(song(1, "First"), song(2, "Second")),
                playlistName = "Playlist",
            ),
        )
    }

    @Test
    fun `download all from collected playlist starts preparation`() {
        assertDownloadAllPrepares(
            MyUiState(
                section = MySection.COLLECTED,
                songs = listOf(song(11, "Collected")),
                playlistName = "收藏歌单",
            ),
        )
    }

    @Test
    fun `download all from liked songs starts preparation`() {
        assertDownloadAllPrepares(
            MyUiState(
                section = MySection.LIKED,
                likedSongs = listOf(song(21, "Liked")),
            ),
        )
    }

    @Test
    fun `download all from cloud songs starts preparation`() {
        assertDownloadAllPrepares(
            MyUiState(
                section = MySection.CLOUD,
                cloudSongs = listOf(cloudSong(31, "Cloud")),
            ),
        )
    }

    @Test
    fun `download all from listen rank starts preparation`() {
        assertDownloadAllPrepares(
            MyUiState(
                section = MySection.RANK,
                rankType = 1,
                rankWeekEntries = listOf(record(41, "Rank")),
            ),
        )
    }

    @Test
    fun `download all from collected album songs starts preparation`() {
        assertDownloadAllPrepares(
            MyUiState(
                section = MySection.COLLECTED_ALBUMS,
                songs = listOf(song(51, "Album Track")),
                playlistName = "专辑",
            ),
        )
    }

    @Test
    fun `download all with no visible songs reports a status message`() {
        val viewModel = myViewModel()
        setMyState(viewModel, MyUiState(section = MySection.LIKED))

        runSongDownloadAction(
            selectedCount = 0,
            onSelectAll = viewModel::toggleSelectAll,
            onDownload = viewModel::downloadSelected,
        )

        assertTrue(viewModel.uiState.value.isPreparingDownloads.not())
        assertTrue(viewModel.uiState.value.statusMessage?.contains("没有") == true)
    }

    @Test
    fun `download all action selects first and downloads in one tap`() {
        val calls = mutableListOf<String>()

        runSongDownloadAction(
            selectedCount = 0,
            onSelectAll = { calls += "select" },
            onDownload = { calls += "download" },
        )

        assertEquals(listOf("select", "download"), calls)
    }

    @Test
    fun `recommend select all selects recommendation songs`() = runTest(dispatcher) {
        val repository = mock(NeteaseRepository::class.java)
        stubDiscoveryHome(repository)
        val cookieManager = mock(CookieManager::class.java)
        `when`(cookieManager.isLoggedIn()).thenReturn(true)
        val viewModel = DiscoverViewModel(
            repository = repository,
            cookieManager = cookieManager,
            settingsManager = mock(SettingsManager::class.java),
            discoveryCache = mock(DiscoveryCacheManager::class.java),
            seenManager = mock(SeenManager::class.java),
            downloadEngine = mock(DownloadEngine::class.java),
        )
        val songs = listOf(song(11, "First"), song(12, "Second"))
        setDiscoverState(
            viewModel,
            DiscoverUiState(sourceMode = SourceMode.RECOMMEND, songs = songs, sourceLabel = "推荐"),
        )

        viewModel.toggleSelectAll()

        assertEquals(setOf(11L, 12L), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun `logout clears private discovery and cloud content`() = runTest(dispatcher) {
        val repository = mock(NeteaseRepository::class.java)
        stubDiscoveryHome(repository)
        val cookieManager = mock(CookieManager::class.java)
        `when`(cookieManager.isLoggedIn()).thenReturn(true)
        val discoverViewModel = DiscoverViewModel(
            repository = repository,
            cookieManager = cookieManager,
            settingsManager = mock(SettingsManager::class.java),
            discoveryCache = mock(DiscoveryCacheManager::class.java),
            seenManager = mock(SeenManager::class.java),
            downloadEngine = mock(DownloadEngine::class.java),
        )
        val myViewModel = MyViewModel(
            repository = repository,
            cookieManager = cookieManager,
            settingsManager = mock(),
            downloadEngine = mock(),
            discoveryCache = mock(),
        )
        setDiscoverState(
            discoverViewModel,
            DiscoverUiState(
                sourceMode = SourceMode.PERSONAL_FM,
                recommendSongs = listOf(song(21, "Recommend")),
                fmSongs = listOf(song(22, "FM")),
                selectedIds = setOf(22L),
            ),
        )
        setMyState(
            myViewModel,
            MyUiState(
                cloudSongs = listOf(cloudSong(23, "Cloud")),
                songs = listOf(song(24, "Playlist")),
            ),
        )

        discoverViewModel.clearPrivateContent()
        myViewModel.clearAccountContent()

        assertEquals(SourceMode.RECOMMEND, discoverViewModel.uiState.value.sourceMode)
        assertTrue(discoverViewModel.uiState.value.recommendSongs.isEmpty())
        assertTrue(discoverViewModel.uiState.value.fmSongs.isEmpty())
        assertTrue(myViewModel.uiState.value.cloudSongs.isEmpty())
        assertTrue(myViewModel.uiState.value.songs.isEmpty())
    }

    private fun assertDownloadAllPrepares(state: MyUiState) {
        val viewModel = myViewModel()
        setMyState(viewModel, state)

        runSongDownloadAction(
            selectedCount = 0,
            onSelectAll = viewModel::toggleSelectAll,
            onDownload = viewModel::downloadSelected,
        )

        assertTrue(viewModel.uiState.value.isPreparingDownloads)
        assertTrue(viewModel.uiState.value.statusMessage?.isNotBlank() == true)
    }

    private fun myViewModel(): MyViewModel {
        val cookieManager = mock(CookieManager::class.java)
        `when`(cookieManager.isLoggedIn()).thenReturn(true)
        return MyViewModel(
            repository = mock(),
            cookieManager = cookieManager,
            settingsManager = mock(),
            downloadEngine = mock(),
            discoveryCache = mock(),
        )
    }

    private fun cloudSong(id: Long, name: String) = CloudApi.CloudSong(
        id = id,
        name = name,
        artists = "Artist",
        album = "Album",
        coverUrl = "",
    )

    private fun record(id: Long, name: String) = ListenDataApi.RecordEntry(
        song = com.qing.hachimi.data.api.PlaylistApi.SongInfo(
            id = id,
            name = name,
            artists = "Artist",
            album = "Album",
            coverUrl = "",
        ),
        playCount = 1,
    )

    private fun song(id: Long, name: String) = Song(
        id = id,
        name = name,
        artists = "Artist",
        album = "Album",
        coverUrl = "",
    )

    private suspend fun stubDiscoveryHome(repository: NeteaseRepository) {
        `when`(repository.getRecommendedPlaylists()).thenReturn(Result.success(emptyList()))
        `when`(repository.getChartList()).thenReturn(Result.success(emptyList()))
        `when`(repository.getNewAlbums(20, 0)).thenReturn(Result.success(emptyList()))
        `when`(repository.getDiscoveryArtists(ArtistArea.HOT, 30, 0))
            .thenReturn(Result.success(DiscoveryPage()))
        `when`(repository.getDiscoveryPodcasts(30, 0))
            .thenReturn(Result.success(DiscoveryPage()))
        `when`(repository.getDailyRecommend()).thenReturn(Result.success(emptyList()))
    }

    @Suppress("UNCHECKED_CAST")
    private fun setMyState(viewModel: MyViewModel, state: MyUiState) {
        val field = MyViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
        (field.get(viewModel) as MutableStateFlow<MyUiState>).value = state
    }

    @Suppress("UNCHECKED_CAST")
    private fun setDiscoverState(viewModel: DiscoverViewModel, state: DiscoverUiState) {
        val field = DiscoverViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
        (field.get(viewModel) as MutableStateFlow<DiscoverUiState>).value = state
    }
}
