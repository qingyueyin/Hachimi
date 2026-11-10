package com.qing.hachimi.data.api

import com.qing.hachimi.data.model.OnlineVersion
import com.qing.hachimi.data.model.AudioQualityInfo
import com.qing.hachimi.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** 网易云搜索接口限流（「操作频繁」）。立刻重试只会让封禁更久，交给调用方决定是否停下 */
class RateLimitException : IOException("网易云提示操作频繁")

class UpgradeApi(
    private val songApi: SongApi,
    private val searchApi: SearchApi
) {

    companion object {
        /** 搜索请求的最小间隔：并发打满会触发「操作频繁」，整个检查都会失败 */
        private const val MIN_SEARCH_INTERVAL_MS = 1000L
        private const val RISK_CONTROL_CODE = -460L
        private const val URL_ATTEMPTS = 2
        private const val RISK_CONTROL_RETRY_DELAY_MS = 3000L
        private const val RISK_CONTROL_MESSAGE = "网易云提示网络环境异常（-460），请切换网络或稍后再试"
    }

    private val searchGate = Mutex()
    private var lastSearchAt = 0L

    /** 所有搜索请求共用一个节流闸门，保证两次请求至少隔 [MIN_SEARCH_INTERVAL_MS] */
    private suspend fun awaitSearchSlot() {
        searchGate.withLock {
            if (lastSearchAt != 0L) {
                val wait = MIN_SEARCH_INTERVAL_MS - (System.nanoTime() - lastSearchAt) / 1_000_000
                if (wait > 0) delay(wait)
            }
            lastSearchAt = System.nanoTime()
        }
    }

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: RateLimitException) {
                throw e
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
                awaitSearchSlot()
                val query = "$songName $artistName"
                val searchResults = try {
                    searchApi.searchSongs(query, cookies, limit = 10)
                } catch (e: IOException) {
                    if (e.message?.contains("操作频繁") == true) throw RateLimitException()
                    throw e
                }

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
        } catch (e: RateLimitException) {
            AppLogger.warn("搜索被限流: $songName - $artistName")
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 交给调用方计数：搜不到和「因为网络失败没检查」要在结果里分开说
            AppLogger.error("搜索歌曲失败: $songName - $artistName", e)
            throw e
        }
    }

    /**
     * 取下载链接。返回 [Result] 而不是 null，是为了把「风控 -460」和「没有链接」分开告诉用户。
     */
    suspend fun getSongUrl(
        songId: Long,
        level: String,
        cookies: Map<String, String>
    ): Result<String> = withContext(Dispatchers.IO) {
        repeat(URL_ATTEMPTS) { attempt ->
            val result = try {
                songApi.getUrl(songId.toString(), level, cookies)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("获取歌曲URL失败: $songId", e)
                return@withContext Result.failure(IOException("获取下载链接失败，请检查网络后重试"))
            }

            if (result == null) {
                return@withContext Result.failure(IOException("获取下载链接失败，请检查网络后重试"))
            }
            if (result.code == RISK_CONTROL_CODE) {
                AppLogger.warn("获取下载链接被风控拦截(-460): $songId")
                if (attempt < URL_ATTEMPTS - 1) delay(RISK_CONTROL_RETRY_DELAY_MS)
                return@repeat
            }
            val url = result.url
            if (url.isNullOrBlank()) {
                return@withContext Result.failure(IOException("网易云没有返回这首歌的下载链接（可能需要会员或已下架）"))
            }
            // 明文链接会被 Android 的网络安全策略直接拦掉，126.net 的 CDN 支持 https
            return@withContext Result.success(
                if (url.startsWith("http://")) "https://${url.substring(7)}" else url
            )
        }
        Result.failure(IOException(RISK_CONTROL_MESSAGE))
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
