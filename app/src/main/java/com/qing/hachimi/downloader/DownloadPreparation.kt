package com.qing.hachimi.downloader

import com.qing.hachimi.data.api.NeteaseApi
import com.qing.hachimi.data.api.SongApi
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.model.Song
import com.qing.hachimi.data.model.SongTagMetadata
import com.qing.hachimi.data.repository.NeteaseRepository
import com.qing.hachimi.util.AppLogger
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** One download preparation path shared by search, discovery, library and legacy screens. */
object DownloadPreparation {
    data class Options(
        val quality: String,
        val qualityLabel: String,
        val downloadLyrics: Boolean,
        val embedLyrics: Boolean,
        val saveTlLrc: Boolean,
        val saveRomaLrc: Boolean,
        val saveYrc: Boolean,
        val artistDelimiter: String,
        val writeTags: Boolean = false,
        val cookie: String? = null,
        val groupName: String? = null,
        val urlTimeoutMs: Long = 15_000L,
        val lyricTimeoutMs: Long = 15_000L,
        val metadataTimeoutMs: Long = 15_000L,
    )

    data class Lyrics(
        val embedded: String?,
        val sidecar: String?,
        val formattedArtists: String,
    )

    data class BatchResult(val added: Int, val failed: Int) {
        val message: String
            get() = when {
                added == 0 -> "获取下载链接失败，请重试"
                failed > 0 -> "已添加 $added 首，$failed 首失败"
                else -> "已添加 $added 首到下载队列"
            }
    }

    fun optionsFrom(
        settings: SettingsManager,
        quality: String = settings.quality,
        qualityLabel: String = NeteaseApi.QUALITY_MAP[quality] ?: "标准",
        cookie: String? = null,
        groupName: String? = null,
    ) = Options(
        quality = quality,
        qualityLabel = qualityLabel,
        downloadLyrics = settings.downloadLyrics,
        embedLyrics = settings.enableWriteTags && settings.embedLyrics,
        saveTlLrc = settings.saveTlLrc,
        saveRomaLrc = settings.saveRomaLrc,
        saveYrc = settings.saveYrc,
        artistDelimiter = settings.artistDelimiter,
        writeTags = settings.enableWriteTags,
        cookie = cookie,
        groupName = groupName,
    )

    fun buildLyrics(song: Song, lyrics: SongApi.LyricResult?, options: Options): Lyrics {
        val formattedArtists = song.artists
            .split("、", "/", ",")
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(options.artistDelimiter)
            .ifBlank { song.artists }
        if (lyrics == null) return Lyrics(null, null, formattedArtists)

        val base = if (options.saveYrc && lyrics.yrc.isNotBlank()) lyrics.yrc else lyrics.lrc
        val translation = if (options.saveYrc && lyrics.ytlrc.isNotBlank()) {
            lyrics.ytlrc
        } else {
            lyrics.tlyric
        }
        val romanization = if (options.saveYrc && lyrics.yromalrc.isNotBlank()) {
            lyrics.yromalrc
        } else {
            lyrics.romalrc
        }
        val content = buildString {
            append(base)
            if (options.saveTlLrc && translation.isNotBlank()) append("\n\n$translation")
            if (options.saveRomaLrc && romanization.isNotBlank()) append("\n\n$romanization")
        }.trim()
        val taggedContent = content.takeIf(String::isNotBlank)?.let {
            "[ti:${song.name}]\n[ar:$formattedArtists]\n[al:${song.album}]\n\n$it"
        }
        return Lyrics(
            embedded = taggedContent.takeIf { options.embedLyrics },
            sidecar = taggedContent.takeIf { options.downloadLyrics },
            formattedArtists = formattedArtists,
        )
    }

    suspend fun enqueueSongs(
        songs: List<Song>,
        repository: NeteaseRepository,
        settings: SettingsManager,
        engine: DownloadEngine,
        options: Options = optionsFrom(settings),
        urlProvider: suspend (Song) -> Result<String> = {
            repository.getSongUrl(it.id.toString(), options.quality)
        },
    ): BatchResult = coroutineScope {
        val directory = settings.getEffectiveDownloadDir()
        val semaphore = Semaphore(MAX_CONCURRENT_PREPARATIONS)
        val outcomes = songs.map { song ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    enqueueOne(song, directory, repository, settings, engine, options, urlProvider)
                }
            }
        }.awaitAll()
        BatchResult(
            added = outcomes.count { it },
            failed = outcomes.count { !it },
        )
    }

    private suspend fun enqueueOne(
        song: Song,
        directory: File,
        repository: NeteaseRepository,
        settings: SettingsManager,
        engine: DownloadEngine,
        options: Options,
        urlProvider: suspend (Song) -> Result<String>,
    ): Boolean {
        return try {
            coroutineScope {
                val urlRequest = async {
                    withTimeout(options.urlTimeoutMs) { urlProvider(song) }.getOrThrow()
                }
                val lyricRequest = async {
                    if (options.embedLyrics || options.downloadLyrics) {
                        optionalRequest("lyrics", song, options.lyricTimeoutMs) {
                            repository.getLyricFull(song.id.toString())
                        }
                    } else {
                        null
                    }
                }
                val metadataRequest = async {
                    if (options.writeTags) {
                        optionalRequest("metadata", song, options.metadataTimeoutMs) {
                            repository.getSongTagMetadata(song.id.toString())
                        } ?: SongTagMetadata()
                    } else {
                        SongTagMetadata()
                    }
                }

                val url = urlRequest.await()
                val lyricPayload = buildLyrics(song, lyricRequest.await(), options)
                engine.startDownload(
                    songId = song.id,
                    name = song.name,
                    artists = lyricPayload.formattedArtists,
                    album = song.album,
                    coverUrl = song.coverUrl,
                    url = url,
                    dir = directory,
                    qualityLabel = options.qualityLabel,
                    cookie = options.cookie,
                    lyrics = lyricPayload.embedded,
                    tagMetadata = metadataRequest.await(),
                    groupName = options.groupName,
                )
                lyricPayload.sidecar?.let { content ->
                    writeSidecar(song, lyricPayload.formattedArtists, content, directory, settings, engine, options)
                }
                true
            }
        } catch (e: TimeoutCancellationException) {
            AppLogger.warn("Download preparation timed out for ${song.name}")
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn("Download preparation failed for ${song.name}: ${e.message}")
            false
        }
    }

    private suspend fun <T> optionalRequest(
        kind: String,
        song: Song,
        timeoutMs: Long,
        block: suspend () -> T,
    ): T? = try {
        withTimeout(timeoutMs) { block() }
    } catch (e: TimeoutCancellationException) {
        AppLogger.warn("Optional $kind request timed out for ${song.name}")
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.warn("Optional $kind request failed for ${song.name}: ${e.message}")
        null
    }

    private suspend fun writeSidecar(
        song: Song,
        formattedArtists: String,
        content: String,
        directory: File,
        settings: SettingsManager,
        engine: DownloadEngine,
        options: Options,
    ) = withContext(Dispatchers.IO) {
        val audioFile = engine.getTargetFilePath(song.id)
        val lrcFile = if (audioFile != null) {
            File(audioFile.parentFile, audioFile.nameWithoutExtension + ".lrc")
        } else {
            File(
                directory,
                settings.buildFileName(song.name, formattedArtists, options.qualityLabel, song.album) + ".lrc",
            )
        }
        lrcFile.parentFile?.mkdirs()
        lrcFile.writeText(content, Charsets.UTF_8)
    }

    private const val MAX_CONCURRENT_PREPARATIONS = 5
}
