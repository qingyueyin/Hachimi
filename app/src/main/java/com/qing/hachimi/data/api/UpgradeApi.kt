package com.qing.hachimi.data.api

import com.qing.hachimi.data.model.OnlineVersion
import com.qing.hachimi.data.model.AudioQualityInfo
import com.qing.hachimi.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class UpgradeApi(
    private val songApi: SongApi,
    private val searchApi: SearchApi
) {

    private suspend fun <T> retry(
        times: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 10000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                AppLogger.warn("重试失败: ${e.message}")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        return block()
    }

    suspend fun searchSong(
        songName: String,
        artistName: String,
        cookies: Map<String, String>
    ): List<OnlineVersion> = withContext(Dispatchers.IO) {
        try {
            retry {
                val query = "$songName $artistName"
                val searchResults = searchApi.searchSongs(query, cookies, limit = 10)

                searchResults.songs.map { song ->
                    OnlineVersion(
                        id = song.id,
                        name = song.name,
                        artists = song.artists,
                        album = song.album,
                        qualityLevel = "standard",
                        qualityInfo = AudioQualityInfo(
                            format = "unknown",
                            bitrate = 0,
                            sampleRate = 0,
                            bitDepth = 0,
                            encoder = "",
                            fileSize = 0,
                            duration = 0
                        ),
                        downloadUrl = null
                    )
                }.filter { version ->
                    matchConfidence(songName, artistName, version) >= 0.7
                }.take(3).mapNotNull { version ->
                    val quality = songApi.getQualityInfo(version.id.toString(), cookies)
                        ?: return@mapNotNull null
                    version.copy(
                        qualityLevel = quality["bestLevel"] as? String ?: return@mapNotNull null,
                        qualityInfo = quality.toAudioQualityInfo(),
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.error("搜索歌曲失败: $songName - $artistName", e)
            emptyList()
        }
    }

    suspend fun getSongUrl(
        songId: Long,
        level: String,
        cookies: Map<String, String>
    ): String? = withContext(Dispatchers.IO) {
        try {
            retry {
                val result = songApi.getUrl(songId.toString(), level, cookies)
                result?.url
            }
        } catch (e: Exception) {
            AppLogger.error("获取歌曲URL失败: $songId", e)
            null
        }
    }

    suspend fun getQualityInfo(
        songId: Long,
        cookies: Map<String, String>
    ): AudioQualityInfo? = withContext(Dispatchers.IO) {
        try {
            retry {
                val qualityInfo = songApi.getQualityInfo(songId.toString(), cookies)
                if (qualityInfo != null) {
                    qualityInfo.toAudioQualityInfo()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            AppLogger.error("获取音质信息失败: $songId", e)
            null
        }
    }

    fun matchConfidence(songName: String, artistName: String, onlineVersion: OnlineVersion): Double {
        val nameSimilarity = calculateStringSimilarity(songName, onlineVersion.name)
        val artistSimilarity = calculateStringSimilarity(artistName, onlineVersion.artists)

        return (nameSimilarity * 0.7 + artistSimilarity * 0.3)
    }

    private fun Map<String, Any>.toAudioQualityInfo() = AudioQualityInfo(
        format = this["type"] as? String ?: "unknown",
        bitrate = (this["bitrate"] as? Number)?.toInt() ?: 0,
        sampleRate = (this["sampleRate"] as? Number)?.toInt() ?: 0,
        bitDepth = (this["bitDepth"] as? Number)?.toInt() ?: 0,
        encoder = this["encoder"] as? String ?: "",
        fileSize = (this["size"] as? Number)?.toLong() ?: 0L,
        duration = (this["duration"] as? Number)?.toLong() ?: 0L,
    )

    private fun calculateStringSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1

        if (longer.isEmpty()) return 1.0

        val longerLength = longer.length
        return (longerLength - editDistance(longer, shorter)) / longerLength.toDouble()
    }

    private fun editDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else if (j > 0) {
                    var newValue = costs[j - 1]
                    if (s1[i - 1] != s2[j - 1]) {
                        newValue = minOf(minOf(newValue, lastValue), costs[j]) + 1
                    }
                    costs[j - 1] = lastValue
                    lastValue = newValue
                }
            }
            if (i > 0) costs[s2.length] = lastValue
        }
        return costs[s2.length]
    }
}
