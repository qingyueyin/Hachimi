package com.qing.hachimi.ui.screens

import com.qing.hachimi.data.local.AccountHistoryManager
import com.qing.hachimi.data.local.CookieManager
import com.qing.hachimi.data.local.DiscoveryCacheManager
import com.qing.hachimi.data.local.FolderNamingFormat
import com.qing.hachimi.data.local.NamingFormat
import com.qing.hachimi.data.local.SeenManager
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.model.SourceMode
import com.qing.hachimi.data.repository.NeteaseRepository
import com.qing.hachimi.downloader.DownloadEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationUserFlowTest {

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
    fun `all browse sections are public while private fm requires login`() {
        val publicModes = availableDiscoverSourceModes(isLoggedIn = false)
        val loggedInModes = availableDiscoverSourceModes(isLoggedIn = true)

        assertTrue(SourceMode.RECOMMEND in publicModes)
        assertTrue(SourceMode.PLAYLISTS in publicModes)
        assertTrue(SourceMode.PODCASTS in publicModes)
        assertTrue(SourceMode.ARTISTS in publicModes)
        assertFalse(SourceMode.PERSONAL_FM in publicModes)
        assertTrue(SourceMode.RECOMMEND in loggedInModes)
        assertTrue(SourceMode.PERSONAL_FM in loggedInModes)
    }

    @Test
    fun `deep link navigation request is observable by the main screen`() {
        val viewModel = mainViewModel()

        viewModel.requestSearchTab()

        assertEquals(1L, viewModel.uiState.value.searchTabRequestId)
    }

    private fun mainViewModel(): MainViewModel {
        val settings = mock(SettingsManager::class.java).also {
            `when`(it.quality).thenReturn("exhigh")
            `when`(it.downloadDir).thenReturn("")
            `when`(it.defaultDownloadDir).thenReturn("build/test-downloads")
            `when`(it.namingFormat).thenReturn(NamingFormat.SONG_ARTIST)
            `when`(it.folderNamingFormat).thenReturn(FolderNamingFormat.NONE)
            `when`(it.customNamingTemplate).thenReturn("")
            `when`(it.artistDelimiter).thenReturn("、")
            `when`(it.getFilteredQualityKeys()).thenReturn(listOf("exhigh"))
        }
        val cookies = mock(CookieManager::class.java).also {
            `when`(it.isLoggedIn()).thenReturn(false)
        }
        return MainViewModel(
            repository = mock(NeteaseRepository::class.java),
            cookieManager = cookies,
            downloadEngine = mock(DownloadEngine::class.java),
            settingsManager = settings,
            seenManager = mock(SeenManager::class.java),
            discoveryCache = mock(DiscoveryCacheManager::class.java),
            accountHistory = mock(AccountHistoryManager::class.java),
        )
    }
}
