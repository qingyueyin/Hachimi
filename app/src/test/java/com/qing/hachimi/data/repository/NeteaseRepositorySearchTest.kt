package com.qing.hachimi.data.repository

import com.qing.hachimi.data.api.AlbumApi
import com.qing.hachimi.data.api.ArtistApi
import com.qing.hachimi.data.api.CloudApi
import com.qing.hachimi.data.api.CollectionApi
import com.qing.hachimi.data.api.DiscoveryApi
import com.qing.hachimi.data.api.LoginApi
import com.qing.hachimi.data.api.ListenDataApi
import com.qing.hachimi.data.api.NeteaseApi
import com.qing.hachimi.data.api.PlaylistApi
import com.qing.hachimi.data.api.SearchAlbumsResult
import com.qing.hachimi.data.api.SearchApi
import com.qing.hachimi.data.api.SearchArtistsResult
import com.qing.hachimi.data.api.SearchPlaylistsResult
import com.qing.hachimi.data.api.SearchPodcastsResult
import com.qing.hachimi.data.api.SearchSongsResult
import com.qing.hachimi.data.api.SongApi
import com.qing.hachimi.data.local.CookieManager
import com.qing.hachimi.data.model.AlbumResult
import com.qing.hachimi.data.model.ArtistResult
import com.qing.hachimi.data.model.DiscoveryPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class NeteaseRepositorySearchTest {

    @Test
    fun `exact artist query uses artist discography for album results`() = runBlocking {
        val searchApi = mock(SearchApi::class.java)
        val artistApi = mock(ArtistApi::class.java)
        val cookies = emptyMap<String, String>()
        val artist = ArtistResult(id = 48161, name = "Ariana Grande")
        val petal = AlbumResult(
            id = 390031118,
            name = "petal",
            artist = "Ariana Grande",
            publishTime = 1785427201000,
        )
        `when`(searchApi.searchSongs("Ariana Grande", cookies, 30, 0))
            .thenReturn(SearchSongsResult())
        `when`(searchApi.searchAlbums("Ariana Grande", cookies, 20, 0))
            .thenReturn(SearchAlbumsResult(albums = listOf(AlbumResult(1, "模糊结果"))))
        `when`(searchApi.searchArtists("Ariana Grande", cookies, 20, 0))
            .thenReturn(SearchArtistsResult(artists = listOf(artist)))
        `when`(searchApi.searchPlaylists("Ariana Grande", cookies, 20, 0))
            .thenReturn(SearchPlaylistsResult())
        `when`(searchApi.searchPodcasts("Ariana Grande", cookies, 20, 0))
            .thenReturn(SearchPodcastsResult())
        `when`(artistApi.getAlbumsPage(48161, cookies, 50, 0))
            .thenReturn(DiscoveryPage(items = listOf(petal), nextOffset = 50, hasMore = true))
        val repository = repository(searchApi, artistApi, cookies)

        val result = repository.searchAll("Ariana Grande").getOrThrow()

        assertEquals(listOf(390031118L), result.albums.map { it.id })
        assertEquals(48161L, result.albumArtistId)
        assertEquals(50, result.albumOffset)
        assertTrue(result.hasMoreAlbums)
        verify(artistApi).getAlbumsPage(48161, cookies, 50, 0)
        Unit
    }

    private fun repository(
        searchApi: SearchApi,
        artistApi: ArtistApi,
        cookies: Map<String, String>,
    ): NeteaseRepository {
        val cookieManager = mock(CookieManager::class.java)
        `when`(cookieManager.getCookiesWithAnonFallback()).thenReturn(cookies)
        return NeteaseRepository(
            api = mock(NeteaseApi::class.java),
            cookieManager = cookieManager,
            songApi = mock(SongApi::class.java),
            playlistApi = mock(PlaylistApi::class.java),
            searchApi = searchApi,
            albumApi = mock(AlbumApi::class.java),
            loginApi = mock(LoginApi::class.java),
            artistApi = artistApi,
            discoveryApi = mock(DiscoveryApi::class.java),
            cloudApi = mock(CloudApi::class.java),
            listenDataApi = mock(ListenDataApi::class.java),
            collectionApi = mock(CollectionApi::class.java),
        )
    }
}
