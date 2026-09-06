package com.qing.hachimi.downloader

import android.content.Context
import com.qing.hachimi.data.local.DownloadHistoryManager
import com.qing.hachimi.data.local.DownloadRecord
import com.qing.hachimi.data.local.FolderNamingFormat
import com.qing.hachimi.data.local.SettingsManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class DownloadEngineTest {

    @Test
    fun `embedded artwork requests the original cover instead of the display thumbnail`() = runBlocking {
        val audio = ByteArray(64 * 1024) { (it % 251).toByte() }
        val coverQuery = AtomicReference<String?>()
        val serverExecutor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = serverExecutor
            createContext("/song.mp3") { exchange -> serveAudio(exchange, audio, AtomicInteger()) }
            createContext("/cover.jpg") { exchange ->
                coverQuery.set(exchange.requestURI.rawQuery)
                val cover = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
                exchange.sendResponseHeaders(200, cover.size.toLong())
                exchange.responseBody.use { it.write(cover) }
            }
            start()
        }
        val folder = Files.createTempDirectory("hachimi-original-cover").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = mock(SettingsManager::class.java).apply {
            `when`(concurrentDownloads).thenReturn(1)
            `when`(folderNamingFormat).thenReturn(FolderNamingFormat.NONE)
            `when`(buildFileName("Song", "Artist", "standard", "Album")).thenReturn("song")
            `when`(enableWriteTags).thenReturn(true)
        }
        val engine = DownloadEngine(
            mock(Context::class.java),
            settings,
            scope,
            mock(DownloadHistoryManager::class.java),
        )

        try {
            engine.startDownload(
                songId = 81,
                name = "Song",
                artists = "Artist",
                album = "Album",
                coverUrl = "http://127.0.0.1:${server.address.port}/cover.jpg?token=kept&param=640y640",
                url = "http://127.0.0.1:${server.address.port}/song.mp3",
                dir = folder,
                qualityLabel = "standard",
            )
            waitForStatus(engine, DownloadStatus.COMPLETED, AtomicInteger(), songId = 81)

            assertEquals("token=kept", coverQuery.get())
        } finally {
            engine.removeTask(81)
            scope.cancel()
            server.stop(0)
            serverExecutor.shutdownNow()
            folder.deleteRecursively()
        }
    }

    @Test
    fun `resume continues existing download without starting another session`() = runBlocking {
        val content = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val headRequests = AtomicInteger()
        val serverExecutor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = serverExecutor
            createContext("/song.mp3") { exchange ->
                serveAudio(exchange, content, headRequests)
            }
            start()
        }
        val folder = Files.createTempDirectory("hachimi-download").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = mock(SettingsManager::class.java).apply {
            `when`(concurrentDownloads).thenReturn(1)
            `when`(folderNamingFormat).thenReturn(FolderNamingFormat.NONE)
            `when`(buildFileName("Song", "Artist", "standard", "Album")).thenReturn("song")
            `when`(enableWriteTags).thenReturn(false)
        }
        assertEquals(1, settings.concurrentDownloads)
        val engine = DownloadEngine(
            mock(Context::class.java),
            settings,
            scope,
            mock(DownloadHistoryManager::class.java)
        )

        try {
            engine.startDownload(
                songId = 1,
                name = "Song",
                artists = "Artist",
                album = "Album",
                url = "http://127.0.0.1:${server.address.port}/song.mp3",
                dir = folder,
                qualityLabel = "standard"
            )
            waitForStatus(engine, DownloadStatus.DOWNLOADING, headRequests)

            engine.pauseDownload(1)
            waitForStatus(engine, DownloadStatus.PAUSED, headRequests)
            delay(100)
            engine.resumeDownload(1)
            waitForStatus(engine, DownloadStatus.COMPLETED, headRequests)

            assertEquals(1, headRequests.get())
            assertArrayEquals(content, File(folder, "song.mp3").readBytes())
        } finally {
            engine.removeTask(1)
            scope.cancel()
            server.stop(0)
            serverExecutor.shutdownNow()
            folder.deleteRecursively()
        }
    }

    @Test
    fun `cancelled download can be retried from the downloads screen`() = runBlocking {
        val content = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val headRequests = AtomicInteger()
        val serverExecutor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = serverExecutor
            createContext("/song.mp3") { exchange ->
                serveAudio(exchange, content, headRequests)
            }
            start()
        }
        val folder = Files.createTempDirectory("hachimi-cancel-retry").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = mock(SettingsManager::class.java).apply {
            `when`(concurrentDownloads).thenReturn(1)
            `when`(folderNamingFormat).thenReturn(FolderNamingFormat.NONE)
            `when`(buildFileName("Song", "Artist", "standard", "Album")).thenReturn("song")
            `when`(enableWriteTags).thenReturn(false)
        }
        val engine = DownloadEngine(
            mock(Context::class.java),
            settings,
            scope,
            mock(DownloadHistoryManager::class.java),
        )

        try {
            engine.startDownload(
                songId = 2,
                name = "Song",
                artists = "Artist",
                album = "Album",
                url = "http://127.0.0.1:${server.address.port}/song.mp3",
                dir = folder,
                qualityLabel = "standard",
            )
            waitForStatus(engine, DownloadStatus.DOWNLOADING, headRequests, songId = 2)

            engine.cancelDownload(2)
            assertEquals(DownloadStatus.CANCELLED, engine.getStatus(2))
            engine.resumeDownload(2)
            waitForStatus(engine, DownloadStatus.COMPLETED, headRequests, songId = 2)

            assertArrayEquals(content, File(folder, "song.mp3").readBytes())
        } finally {
            engine.removeTask(2)
            scope.cancel()
            server.stop(0)
            serverExecutor.shutdownNow()
            folder.deleteRecursively()
        }
    }

    @Test
    fun `dismiss completed record keeps downloaded file`() = runBlocking {
        val content = ByteArray(64 * 1024) { (it % 251).toByte() }
        val serverExecutor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = serverExecutor
            createContext("/song.mp3") { exchange ->
                serveAudio(exchange, content, AtomicInteger())
            }
            start()
        }
        val folder = Files.createTempDirectory("hachimi-dismiss-record").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = mock(SettingsManager::class.java).apply {
            `when`(concurrentDownloads).thenReturn(1)
            `when`(folderNamingFormat).thenReturn(FolderNamingFormat.NONE)
            `when`(buildFileName("Song", "Artist", "standard", "Album")).thenReturn("song")
            `when`(enableWriteTags).thenReturn(false)
        }
        val history = mock(DownloadHistoryManager::class.java)
        val engine = DownloadEngine(mock(Context::class.java), settings, scope, history)

        try {
            engine.startDownload(
                songId = 41,
                name = "Song",
                artists = "Artist",
                album = "Album",
                url = "http://127.0.0.1:${server.address.port}/song.mp3",
                dir = folder,
                qualityLabel = "standard",
            )
            waitForStatus(engine, DownloadStatus.COMPLETED, AtomicInteger(), songId = 41)
            val file = File(folder, "song.mp3")
            assertTrue(file.exists())

            engine.dismissCompletedRecord(41)

            assertTrue(file.exists())
            assertFalse(engine.progressMap.value.containsKey(41))
            verify(history).remove(41)
        } finally {
            engine.removeTask(41)
            scope.cancel()
            server.stop(0)
            serverExecutor.shutdownNow()
            folder.deleteRecursively()
        }
    }

    @Test
    fun `delete restored completed download removes its file`() {
        val folder = Files.createTempDirectory("hachimi-delete-restored").toFile()
        val file = File(folder, "restored.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = mock(SettingsManager::class.java).apply {
            `when`(concurrentDownloads).thenReturn(1)
        }
        val history = mock(DownloadHistoryManager::class.java).apply {
            `when`(loadAll()).thenReturn(
                listOf(
                    DownloadRecord(
                        songId = 42,
                        songName = "Restored",
                        filePath = file.absolutePath,
                    )
                )
            )
        }
        val engine = DownloadEngine(mock(Context::class.java), settings, scope, history)

        try {
            assertEquals(DownloadStatus.COMPLETED, engine.progressMap.value[42]?.status)

            engine.removeTask(42)

            assertFalse(file.exists())
            assertFalse(engine.progressMap.value.containsKey(42))
            verify(history).remove(42)
        } finally {
            scope.cancel()
            folder.deleteRecursively()
        }
    }

    @Test
    fun `restored record stays completed when the file was moved`() {
        val folder = Files.createTempDirectory("hachimi-moved-file").toFile()
        val history = mock(DownloadHistoryManager::class.java).apply {
            `when`(loadAll()).thenReturn(
                listOf(
                    DownloadRecord(
                        songId = 43,
                        songName = "Moved",
                        artists = "Artist",
                        filePath = File(folder, "old-location.mp3").absolutePath,
                    )
                )
            )
        }
        val settings = mock(SettingsManager::class.java).apply {
            `when`(concurrentDownloads).thenReturn(1)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val engine = DownloadEngine(mock(Context::class.java), settings, scope, history)

        try {
            val progress = engine.progressMap.value[43]
            assertEquals(DownloadStatus.COMPLETED, progress?.status)
            assertFalse(progress?.fileAvailable ?: true)
        } finally {
            scope.cancel()
            folder.deleteRecursively()
        }
    }

    @Test
    fun `clearing completed records removes history but keeps files`() {
        val folder = Files.createTempDirectory("hachimi-clear-history").toFile()
        val first = File(folder, "first.mp3").apply { writeBytes(byteArrayOf(1)) }
        val second = File(folder, "second.mp3").apply { writeBytes(byteArrayOf(2)) }
        val history = mock(DownloadHistoryManager::class.java).apply {
            `when`(loadAll()).thenReturn(
                listOf(
                    DownloadRecord(songId = 51, songName = "First", filePath = first.absolutePath),
                    DownloadRecord(songId = 52, songName = "Second", filePath = second.absolutePath),
                )
            )
        }
        val settings = mock(SettingsManager::class.java).apply {
            `when`(concurrentDownloads).thenReturn(1)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val engine = DownloadEngine(mock(Context::class.java), settings, scope, history)

        try {
            engine.dismissAllCompletedRecords()

            assertTrue(first.exists())
            assertTrue(second.exists())
            assertTrue(engine.progressMap.value.isEmpty())
            verify(history).remove(51)
            verify(history).remove(52)
        } finally {
            scope.cancel()
            folder.deleteRecursively()
        }
    }

    @Test
    fun `single stream completion attempts tag writing and exposes failure`() = runBlocking {
        val content = ByteArray(64 * 1024) { (it % 251).toByte() }
        val requests = AtomicInteger()
        val serverExecutor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = serverExecutor
            createContext("/invalid.mp3") { exchange -> serveAudio(exchange, content, requests) }
            start()
        }
        val folder = Files.createTempDirectory("hachimi-tag-status").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = mock(SettingsManager::class.java).apply {
            `when`(concurrentDownloads).thenReturn(1)
            `when`(folderNamingFormat).thenReturn(FolderNamingFormat.NONE)
            `when`(buildFileName("Song", "Artist", "standard", "Album")).thenReturn("song")
            `when`(enableWriteTags).thenReturn(true)
        }
        val engine = DownloadEngine(
            mock(Context::class.java),
            settings,
            scope,
            mock(DownloadHistoryManager::class.java),
        )

        try {
            engine.startDownload(
                songId = 61,
                name = "Song",
                artists = "Artist",
                album = "Album",
                url = "http://127.0.0.1:${server.address.port}/invalid.mp3",
                dir = folder,
                qualityLabel = "standard",
            )
            waitForStatus(engine, DownloadStatus.COMPLETED, requests, songId = 61)

            assertEquals(MetadataStatus.FAILED, engine.progressMap.value[61]?.metadataStatus)
        } finally {
            engine.removeTask(61)
            scope.cancel()
            server.stop(0)
            serverExecutor.shutdownNow()
            folder.deleteRecursively()
        }
    }

    private suspend fun waitForStatus(
        engine: DownloadEngine,
        expected: DownloadStatus,
        headRequests: AtomicInteger,
        songId: Long = 1,
    ) {
        val reached = withTimeoutOrNull(20_000) {
            while (true) {
                val actual = engine.getStatus(songId)
                if (actual == expected) return@withTimeoutOrNull true
                if (actual == DownloadStatus.FAILED || actual == DownloadStatus.CANCELLED ||
                    (actual == DownloadStatus.COMPLETED && expected != DownloadStatus.COMPLETED)
                ) {
                    error("Expected $expected but task reached $actual")
                }
                delay(10)
            }
        }
        if (reached != true) {
            error(
                "Timed out waiting for $expected; actual=${engine.getStatus(songId)}, " +
                    "headRequests=${headRequests.get()}"
            )
        }
    }

    private fun serveAudio(exchange: HttpExchange, content: ByteArray, headRequests: AtomicInteger) {
        if (exchange.requestMethod == "HEAD") {
            headRequests.incrementAndGet()
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
            return
        }

        exchange.responseHeaders.add("Content-Length", content.size.toString())
        exchange.sendResponseHeaders(200, content.size.toLong())
        exchange.responseBody.use { output ->
            var offset = 0
            while (offset < content.size) {
                val count = minOf(4 * 1024, content.size - offset)
                output.write(content, offset, count)
                output.flush()
                offset += count
                Thread.sleep(5)
            }
        }
    }
}
