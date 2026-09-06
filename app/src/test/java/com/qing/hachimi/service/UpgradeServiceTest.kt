package com.qing.hachimi.service

import com.qing.hachimi.data.model.*
import com.qing.hachimi.data.api.UpgradeApi
import com.qing.hachimi.data.local.UpgradeHistoryManager
import com.qing.hachimi.downloader.DownloadEngine
import com.qing.hachimi.downloader.DownloadStatus
import com.qing.hachimi.util.AudioQualityAnalyzer
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking

class UpgradeServiceTest {

    private fun quality(format: String, bitrate: Int) = AudioQualityInfo(
        format = format,
        bitrate = bitrate,
        sampleRate = 44_100,
        bitDepth = 16,
        encoder = "test",
        fileSize = 1,
        duration = 1
    )
    
    @Test
    fun `test isAudioFile with valid extensions`() {
        val service = UpgradeService(
            audioAnalyzer = mock(),
            upgradeApi = mock(),
            historyManager = mock(),
            downloadEngine = mock()
        )
        
        assertTrue(service.isAudioFile(File("test.mp3")))
        assertTrue(service.isAudioFile(File("test.flac")))
        assertTrue(service.isAudioFile(File("test.wav")))
        assertTrue(service.isAudioFile(File("test.aac")))
        assertTrue(service.isAudioFile(File("test.ogg")))
        assertTrue(service.isAudioFile(File("test.opus")))
    }
    
    @Test
    fun `test isAudioFile with invalid extensions`() {
        val service = UpgradeService(
            audioAnalyzer = mock(),
            upgradeApi = mock(),
            historyManager = mock(),
            downloadEngine = mock()
        )
        
        assertFalse(service.isAudioFile(File("test.txt")))
        assertFalse(service.isAudioFile(File("test.jpg")))
        assertFalse(service.isAudioFile(File("test.pdf")))
    }

    @Test
    fun `scan parses default Hachimi filename as song then artist`() = runBlocking {
        val analyzer = mock(AudioQualityAnalyzer::class.java)
        val history = mock(UpgradeHistoryManager::class.java)
        val folder = Files.createTempDirectory("hachimi-scan").toFile()
        val audioFile = File(folder, "Song Name - Artist Name.mp3").apply { writeText("audio") }
        `when`(analyzer.analyzeFile(audioFile)).thenReturn(quality("mp3", 320))

        try {
            val service = UpgradeService(analyzer, mock(), history, mock())

            val song = service.scanFolders(listOf(folder)).single().songs.single()

            assertEquals("Song Name", song.name)
            assertEquals("Artist Name", song.artists)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `scan includes audio files in nested folders`() = runBlocking {
        val analyzer = mock(AudioQualityAnalyzer::class.java)
        val history = mock(UpgradeHistoryManager::class.java)
        val folder = Files.createTempDirectory("hachimi-nested-scan").toFile()
        val nested = File(folder, "Artist/Album").apply { mkdirs() }
        val audioFile = File(nested, "Song - Artist.flac").apply { writeText("audio") }
        `when`(analyzer.analyzeFile(audioFile)).thenReturn(quality("flac", 1_000))

        try {
            val service = UpgradeService(analyzer, mock(), history, mock())

            val songs = service.scanFolders(listOf(folder)).single().songs

            assertEquals(listOf(audioFile), songs.map { it.localFile })
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `upgrade candidate checks run concurrently with a limit of three`() = runBlocking {
        val api = mock(UpgradeApi::class.java)
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val cookies = mapOf("MUSIC_U" to "token")
        val songs = (1..6).map { index ->
            val song = SongInfo(
                id = index.toLong(),
                name = "Song $index",
                artists = "Artist",
                album = "Album",
                localFile = File("Song $index - Artist.mp3"),
                qualityInfo = quality("mp3", 128),
            )
            val online = OnlineVersion(
                id = index.toLong(),
                name = song.name,
                artists = song.artists,
                album = song.album,
                qualityLevel = "lossless",
                qualityInfo = quality("flac", 1_000),
                downloadUrl = null,
            )
            `when`(api.searchSong(song.name, song.artists, cookies)).thenAnswer {
                val now = active.incrementAndGet()
                maxActive.updateAndGet { previous -> maxOf(previous, now) }
                try {
                    Thread.sleep(80)
                    listOf(online)
                } finally {
                    active.decrementAndGet()
                }
            }
            `when`(api.matchConfidence(song.name, song.artists, online)).thenReturn(1.0)
            song
        }
        val service = UpgradeService(AudioQualityAnalyzer(), api, mock(), mock())

        val candidates = service.findUpgradeCandidates(songs, cookies)

        assertEquals(6, candidates.size)
        assertEquals(3, maxActive.get())
    }

    @Test
    fun `auto replace preserves original when download fails`() = runBlocking {
        val api = mock(UpgradeApi::class.java)
        val history = mock(UpgradeHistoryManager::class.java)
        val engine = mock(DownloadEngine::class.java)
        val folder = Files.createTempDirectory("hachimi-upgrade-fail").toFile()
        val original = File(folder, "Song - Artist.mp3").apply { writeText("original") }
        val cookies = mapOf("MUSIC_U" to "token")
        val online = OnlineVersion(
            id = 42,
            name = "Song",
            artists = "Artist",
            album = "Album",
            qualityLevel = "lossless",
            qualityInfo = quality("flac", 1_000),
            downloadUrl = null
        )
        `when`(api.getSongUrl(online.id, online.qualityLevel, cookies)).thenReturn("https://example.test/song.flac")
        `when`(engine.getStatus(anyLong())).thenReturn(DownloadStatus.FAILED)

        try {
            val service = UpgradeService(mock(), api, history, engine)
            val result = service.upgradeSong(
                SongInfo(0, "Song", "Artist", "Album", original, quality("mp3", 320)),
                online,
                UpgradeMode.AUTO_REPLACE,
                cookies
            )

            assertFalse(result.success)
            assertTrue(original.exists())
            assertEquals("original", original.readText())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `auto replace rejects a downloaded file that is not higher quality`() = runBlocking {
        val api = mock(UpgradeApi::class.java)
        val analyzer = mock(AudioQualityAnalyzer::class.java)
        val engine = mock(DownloadEngine::class.java)
        val folder = Files.createTempDirectory("hachimi-upgrade-fallback").toFile()
        val original = File(folder, "Song - Artist.mp3").apply { writeText("original") }
        val downloaded = File(folder, "downloaded.mp3").apply { writeText("fallback") }
        val cookies = mapOf("MUSIC_U" to "token")
        val originalQuality = quality("mp3", 320)
        val fallbackQuality = quality("mp3", 128)
        val online = OnlineVersion(
            id = 42,
            name = "Song",
            artists = "Artist",
            album = "Album",
            qualityLevel = "lossless",
            qualityInfo = quality("flac", 1_000),
            downloadUrl = null,
        )
        `when`(api.getSongUrl(online.id, online.qualityLevel, cookies)).thenReturn("https://example.test/song.mp3")
        `when`(engine.getStatus(anyLong())).thenReturn(DownloadStatus.COMPLETED)
        `when`(engine.getCompletedFilePath(anyLong())).thenReturn(downloaded)
        `when`(analyzer.analyzeFile(downloaded)).thenReturn(fallbackQuality)
        `when`(analyzer.compareQuality(originalQuality, fallbackQuality)).thenReturn(
            QualityComparison(204, 185, -19, false)
        )

        try {
            val service = UpgradeService(analyzer, api, mock(), engine)

            val result = service.upgradeSong(
                SongInfo(0, "Song", "Artist", "Album", original, originalQuality),
                online,
                UpgradeMode.AUTO_REPLACE,
                cookies,
            )

            assertFalse(result.success)
            assertTrue(original.exists())
            assertEquals("original", original.readText())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `auto replace installs completed download and keeps backup`() = runBlocking {
        val api = mock(UpgradeApi::class.java)
        val history = mock(UpgradeHistoryManager::class.java)
        val engine = mock(DownloadEngine::class.java)
        val folder = Files.createTempDirectory("hachimi-upgrade-success").toFile()
        val original = File(folder, "Song - Artist.mp3").apply { writeText("original") }
        val downloaded = File(folder, "downloaded.flac").apply { writeText("upgraded") }
        val cookies = mapOf("MUSIC_U" to "token")
        val online = OnlineVersion(
            id = 42,
            name = "Song",
            artists = "Artist",
            album = "Album",
            qualityLevel = "lossless",
            qualityInfo = quality("flac", 1_000),
            downloadUrl = null
        )
        `when`(api.getSongUrl(online.id, online.qualityLevel, cookies)).thenReturn("https://example.test/song.flac")
        `when`(engine.getStatus(anyLong())).thenReturn(DownloadStatus.COMPLETED)
        `when`(engine.getCompletedFilePath(anyLong())).thenReturn(downloaded)

        val analyzer = mock(AudioQualityAnalyzer::class.java)
        val originalQuality = quality("mp3", 320)
        val upgradedQuality = quality("flac", 1_000)
        `when`(analyzer.analyzeFile(downloaded)).thenReturn(upgradedQuality)
        `when`(analyzer.compareQuality(originalQuality, upgradedQuality)).thenReturn(
            QualityComparison(204, 302, 98, true)
        )

        try {
            val service = UpgradeService(analyzer, api, history, engine)
            val result = service.upgradeSong(
                SongInfo(0, "Song", "Artist", "Album", original, originalQuality),
                online,
                UpgradeMode.AUTO_REPLACE,
                cookies
            )

            assertTrue(result.success)
            assertEquals("upgraded", result.upgradedFile?.readText())
            assertTrue(File(original.absolutePath + ".backup").exists())
            assertEquals("original", File(original.absolutePath + ".backup").readText())
        } finally {
            folder.deleteRecursively()
        }
    }
}
