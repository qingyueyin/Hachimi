package com.qing.hachimi.data.api

import com.qing.hachimi.data.model.OnlineVersion
import com.qing.hachimi.data.model.AudioQualityInfo
import com.qing.hachimi.data.api.SearchSongsResult
import com.qing.hachimi.data.model.Song
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import kotlinx.coroutines.runBlocking

class UpgradeApiTest {

    @Test
    fun `test matchConfidence with exact match`() {
        val api = UpgradeApi(
            songApi = mock(),
            searchApi = mock()
        )

        val onlineVersion = OnlineVersion(
            id = 12345,
            name = "测试歌曲",
            artists = "测试艺术家",
            album = "测试专辑",
            qualityLevel = "standard",
            qualityInfo = AudioQualityInfo(
                format = "mp3",
                bitrate = 320,
                sampleRate = 44100,
                bitDepth = 16,
                encoder = "LAME",
                fileSize = 8000000,
                duration = 180000
            ),
            downloadUrl = null
        )

        val confidence = api.matchConfidence("测试歌曲", "测试艺术家", onlineVersion)
        assertTrue(confidence >= 0.7)
    }

    @Test
    fun `test matchConfidence with partial match`() {
        val api = UpgradeApi(
            songApi = mock(),
            searchApi = mock()
        )

        val onlineVersion = OnlineVersion(
            id = 12345,
            name = "测试歌曲 (Live)",
            artists = "测试艺术家",
            album = "测试专辑",
            qualityLevel = "standard",
            qualityInfo = AudioQualityInfo(
                format = "mp3",
                bitrate = 320,
                sampleRate = 44100,
                bitDepth = 16,
                encoder = "LAME",
                fileSize = 8000000,
                duration = 180000
            ),
            downloadUrl = null
        )

        val confidence = api.matchConfidence("测试歌曲", "测试艺术家", onlineVersion)
        assertTrue(confidence >= 0.5)
    }

    @Test
    fun `test searchSong returns results`() = runBlocking {
        val mockSearchApi = mock(SearchApi::class.java)
        val mockSongApi = mock(SongApi::class.java)

        val mockSong = Song(
            id = 12345,
            name = "测试歌曲",
            artists = "测试艺术家",
            album = "测试专辑",
            coverUrl = "http://example.com/cover.jpg"
        )

        val mockSearchResult = SearchSongsResult(
            songs = listOf(mockSong),
            total = 1,
            hasMore = false
        )

        `when`(mockSearchApi.searchSongs("测试歌曲 测试艺术家", emptyMap(), 10, 0))
            .thenReturn(mockSearchResult)
        `when`(mockSongApi.getQualityInfo("12345", emptyMap())).thenReturn(
            mapOf(
                "bestLevel" to "lossless",
                "type" to "flac",
                "bitrate" to 1_000,
                "sampleRate" to 44_100,
                "bitDepth" to 16,
                "size" to 10_000_000L
            )
        )

        val api = UpgradeApi(
            songApi = mockSongApi,
            searchApi = mockSearchApi
        )

        val results = api.searchSong("测试歌曲", "测试艺术家", emptyMap())

        assertEquals(1, results.size)
        assertEquals(12345, results[0].id)
        assertEquals("测试歌曲", results[0].name)
        assertEquals("lossless", results[0].qualityLevel)
        assertEquals("flac", results[0].qualityInfo.format)
        assertEquals(1_000, results[0].qualityInfo.bitrate)
    }

    @Test
    fun `test searchSong handles exception`() = runBlocking {
        val mockSearchApi = mock(SearchApi::class.java)
        val mockSongApi = mock(SongApi::class.java)

        `when`(mockSearchApi.searchSongs(anyString(), anyMap(), anyInt(), anyInt()))
            .thenThrow(RuntimeException("网络错误"))

        val api = UpgradeApi(
            songApi = mockSongApi,
            searchApi = mockSearchApi
        )

        val results = api.searchSong("测试歌曲", "测试艺术家", emptyMap())

        assertTrue(results.isEmpty())
    }

    @Test
    fun `test getSongUrl returns url`() = runBlocking {
        val mockSongApi = mock(SongApi::class.java)
        val mockSearchApi = mock(SearchApi::class.java)

        val mockResult = SongApi.SongUrlResult(
            id = 12345,
            url = "http://example.com/song.mp3",
            level = "standard",
            size = 8000000,
            type = "mp3"
        )

        `when`(mockSongApi.getUrl("12345", "standard", emptyMap()))
            .thenReturn(mockResult)

        val api = UpgradeApi(
            songApi = mockSongApi,
            searchApi = mockSearchApi
        )

        val url = api.getSongUrl(12345, "standard", emptyMap())

        assertEquals("http://example.com/song.mp3", url)
    }

    @Test
    fun `test getSongUrl returns null on exception`() = runBlocking {
        val mockSongApi = mock(SongApi::class.java)
        val mockSearchApi = mock(SearchApi::class.java)

        `when`(mockSongApi.getUrl(anyString(), anyString(), anyMap()))
            .thenThrow(RuntimeException("网络错误"))

        val api = UpgradeApi(
            songApi = mockSongApi,
            searchApi = mockSearchApi
        )

        val url = api.getSongUrl(12345, "standard", emptyMap())

        assertNull(url)
    }

    @Test
    fun `test getQualityInfo returns quality info`() = runBlocking {
        val mockSongApi = mock(SongApi::class.java)
        val mockSearchApi = mock(SearchApi::class.java)

        val qualityMap = mapOf(
            "type" to "flac",
            "bitrate" to 1000,
            "sampleRate" to 44100,
            "bitDepth" to 16,
            "encoder" to "FLAC",
            "size" to 10000000L,
            "duration" to 180000L
        )

        `when`(mockSongApi.getQualityInfo("12345", emptyMap()))
            .thenReturn(qualityMap)

        val api = UpgradeApi(
            songApi = mockSongApi,
            searchApi = mockSearchApi
        )

        val qualityInfo = api.getQualityInfo(12345, emptyMap())

        assertNotNull(qualityInfo)
        assertEquals("flac", qualityInfo?.format)
        assertEquals(1000, qualityInfo?.bitrate)
        assertEquals(44100, qualityInfo?.sampleRate)
        assertEquals(16, qualityInfo?.bitDepth)
        assertEquals("FLAC", qualityInfo?.encoder)
        assertEquals(10000000L, qualityInfo?.fileSize)
        assertEquals(180000L, qualityInfo?.duration)
    }

    @Test
    fun `test getQualityInfo returns null on exception`() = runBlocking {
        val mockSongApi = mock(SongApi::class.java)
        val mockSearchApi = mock(SearchApi::class.java)

        `when`(mockSongApi.getQualityInfo(anyString(), anyMap()))
            .thenThrow(RuntimeException("网络错误"))

        val api = UpgradeApi(
            songApi = mockSongApi,
            searchApi = mockSearchApi
        )

        val qualityInfo = api.getQualityInfo(12345, emptyMap())

        assertNull(qualityInfo)
    }
}
