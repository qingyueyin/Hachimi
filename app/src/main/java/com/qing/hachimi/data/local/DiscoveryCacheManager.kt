package com.qing.hachimi.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.qing.hachimi.data.api.ChartInfo
import com.qing.hachimi.data.api.CloudApi
import com.qing.hachimi.data.api.PlaylistApi
import com.qing.hachimi.data.model.AlbumResult
import com.qing.hachimi.data.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 发现页数据缓存管理器。
 * 为每种数据存储指纹（hash）和序列化数据，避免上游未变化时重复更新 UI。
 *
 * 指纹规则（只取标识性字段，忽略可能频繁波动但无实际意义的数据）：
 * - 排行榜: hash(sorted "id:name")
 * - 热搜: hash(sorted keywords)
 * - 新专辑: hash(sorted albumIds)
 * - 我的歌单: hash(sorted "playlistId:trackCount")
 * - 每日推荐: hash(sorted songIds)
 */
class DiscoveryCacheManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hachimi_disco_cache", Context.MODE_PRIVATE)

    companion object {
        private const val CHARTS_CACHE_KEY = "charts_v2"
        // 缓存有效期
        private const val CACHE_VALIDITY_DEFAULT_MS = 5 * 60 * 1000L      // 默认 5 分钟
        private const val CACHE_VALIDITY_CLOUD_MS = 24 * 60 * 60 * 1000L  // 云盘歌曲 24 小时
        private const val CACHE_VALIDITY_PLAYLISTS_MS = 30 * 60 * 1000L   // 用户歌单 30 分钟
    }

    // ── Fingerprint get/set ──

    private fun getFingerprint(key: String): String = prefs.getString("fp_$key", "") ?: ""
    private fun setFingerprint(key: String, fp: String) = prefs.edit().putString("fp_$key", fp).apply()
    private fun getTimestamp(key: String): Long = prefs.getLong("ts_$key", 0L)
    private fun setTimestamp(key: String, ts: Long) = prefs.edit().putLong("ts_$key", ts).apply()

    /**
     * 压缩 JSON 字符串为 Base64 编码的 GZIP 数据
     */
    private fun compress(json: String): String {
        return try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val gzipOutputStream = GZIPOutputStream(byteArrayOutputStream)
            gzipOutputStream.write(json.toByteArray(Charsets.UTF_8))
            gzipOutputStream.close()
            Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            json // 压缩失败则返回原文
        }
    }

    /**
     * 解压 Base64 编码的 GZIP 数据为 JSON 字符串
     */
    private fun decompress(compressed: String?): String? {
        if (compressed.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(compressed, Base64.NO_WRAP)
            val byteArrayInputStream = ByteArrayInputStream(bytes)
            val gzipInputStream = GZIPInputStream(byteArrayInputStream)
            val decompressed = gzipInputStream.readBytes().toString(Charsets.UTF_8)
            gzipInputStream.close()
            decompressed
        } catch (e: Exception) {
            compressed // 解压失败则返回原文（可能是旧版本未压缩的数据）
        }
    }

    private fun getJson(key: String): String? = decompress(prefs.getString("json_$key", null))
    private fun setJson(key: String, json: String) = prefs.edit().putString("json_$key", compress(json)).apply()

    private fun saveWithFingerprint(key: String, fp: String, json: String) {
        prefs.edit().apply {
            putString("fp_$key", fp)
            putLong("ts_$key", System.currentTimeMillis())
            putString("json_$key", compress(json))
            apply()
        }
    }

    /**
     * 检查缓存是否有效（未过期）
     */
    private fun isCacheValid(key: String, validityMs: Long = CACHE_VALIDITY_DEFAULT_MS): Boolean {
        val timestamp = getTimestamp(key)
        if (timestamp == 0L) return false
        val age = System.currentTimeMillis() - timestamp
        return age < validityMs
    }

    // ── Fingerprint computation ──

    private fun fp(vararg parts: String): String {
        return parts.sorted().joinToString("/").hashCode().toString()
    }

    // ── Charts ──

    fun isChartsCacheValid(): Boolean = isCacheValid(CHARTS_CACHE_KEY)

    fun chartFingerprint(charts: List<ChartInfo>): String =
        fp(*charts.map { "${it.id}:${it.name}" }.toTypedArray())

    fun getChartFingerprint(): String = getFingerprint(CHARTS_CACHE_KEY)
    fun chartFingerprintMatches(fp: String): Boolean =
        fp == getFingerprint(CHARTS_CACHE_KEY) && fp.isNotEmpty()

    fun getCachedCharts(): List<ChartInfo>? = parseCharts(getJson(CHARTS_CACHE_KEY))

    fun saveCharts(charts: List<ChartInfo>) {
        val fp = fp(*charts.map { "${it.id}:${it.name}" }.toTypedArray())
        val json = JSONArray().apply {
            charts.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("coverUrl", c.coverUrl)
                    put("description", c.description)
                    put("updateFrequency", c.updateFrequency)
                    put("trackCount", c.trackCount)
                    put("playCount", c.playCount)
                    put("updateTime", c.updateTime)
                })
            }
        }.toString()
        saveWithFingerprint(CHARTS_CACHE_KEY, fp, json)
    }

    private fun parseCharts(json: String?): List<ChartInfo>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChartInfo(
                    id = o.optLong("id"),
                    name = o.optString("name"),
                    coverUrl = o.optString("coverUrl"),
                    description = o.optString("description"),
                    updateFrequency = o.optString("updateFrequency"),
                    trackCount = o.optInt("trackCount"),
                    playCount = o.optLong("playCount"),
                    updateTime = o.optLong("updateTime"),
                )
            }
        } catch (_: Exception) { null }
    }

    // ── Hot Search ──

    fun hotSearchFingerprint(keywords: List<Pair<String, Int>>): String =
        fp(*keywords.map { it.first }.toTypedArray())

    fun getHotSearchFingerprint(): String = getFingerprint("hotsearch")
    fun hotSearchFingerprintMatches(fp: String): Boolean = fp == getFingerprint("hotsearch") && fp.isNotEmpty()

    fun getCachedHotSearch(): List<Pair<String, Int>>? = parseHotSearch(getJson("hotsearch"))

    fun saveHotSearch(keywords: List<Pair<String, Int>>) {
        val fp = fp(*keywords.map { it.first }.toTypedArray())
        val json = JSONArray().apply {
            keywords.forEach { (kw, score) ->
                put(JSONObject().apply {
                    put("keyword", kw)
                    put("score", score)
                })
            }
        }.toString()
        saveWithFingerprint("hotsearch", fp, json)
    }

    private fun parseHotSearch(json: String?): List<Pair<String, Int>>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.optString("keyword") to o.optInt("score")
            }
        } catch (_: Exception) { null }
    }

    // ── New Albums ──

    fun isNewAlbumsCacheValid(): Boolean = isCacheValid("newalbums")

    fun newAlbumsFingerprint(albums: List<AlbumResult>): String =
        fp(*albums.map { it.id.toString() }.toTypedArray())

    fun getNewAlbumsFingerprint(): String = getFingerprint("newalbums")
    fun newAlbumsFingerprintMatches(fp: String): Boolean = fp == getFingerprint("newalbums") && fp.isNotEmpty()

    fun getCachedNewAlbums(): List<AlbumResult>? = parseAlbums(getJson("newalbums"))

    fun saveNewAlbums(albums: List<AlbumResult>) {
        val fp = fp(*albums.map { it.id.toString() }.toTypedArray())
        val json = JSONArray().apply {
            albums.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("name", a.name)
                    put("artist", a.artist)
                    put("coverUrl", a.coverUrl)
                    put("publishTime", a.publishTime)
                })
            }
        }.toString()
        saveWithFingerprint("newalbums", fp, json)
    }

    private fun parseAlbums(json: String?): List<AlbumResult>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AlbumResult(
                    id = o.optLong("id"),
                    name = o.optString("name"),
                    artist = o.optString("artist"),
                    coverUrl = o.optString("coverUrl"),
                    publishTime = o.optLong("publishTime")
                )
            }
        } catch (_: Exception) { null }
    }

    // ── User Playlists ──

    fun isUserPlaylistsCacheValid(): Boolean = isCacheValid("userplaylists", CACHE_VALIDITY_PLAYLISTS_MS)

    fun userPlaylistsFingerprint(playlists: List<PlaylistApi.UserPlaylist>): String =
        fp(*playlists.map { "${it.id}:${it.trackCount}" }.toTypedArray())

    fun getUserPlaylistsFingerprint(): String = getFingerprint("userplaylists")
    fun userPlaylistsFingerprintMatches(fp: String): Boolean = fp == getFingerprint("userplaylists") && fp.isNotEmpty()

    fun getCachedUserPlaylists(): List<PlaylistApi.UserPlaylist>? = parsePlaylists(getJson("userplaylists"))

    fun saveUserPlaylists(playlists: List<PlaylistApi.UserPlaylist>) {
        val fp = fp(*playlists.map { "${it.id}:${it.trackCount}" }.toTypedArray())
        val json = JSONArray().apply {
            playlists.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("coverUrl", p.coverUrl)
                    put("trackCount", p.trackCount)
                    put("creator", p.creator)
                })
            }
        }.toString()
        saveWithFingerprint("userplaylists", fp, json)
    }

    private fun parsePlaylists(json: String?): List<PlaylistApi.UserPlaylist>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PlaylistApi.UserPlaylist(
                    id = o.optLong("id"),
                    name = o.optString("name"),
                    coverUrl = o.optString("coverUrl"),
                    trackCount = o.optInt("trackCount"),
                    creator = o.optString("creator")
                )
            }
        } catch (_: Exception) { null }
    }

    // ── Daily Recommend ──

    fun isRecommendCacheValid(): Boolean = isCacheValid("recommend")

    fun recommendFingerprint(songs: List<Song>): String =
        fp(*songs.map { it.id.toString() }.toTypedArray())

    fun getRecommendFingerprint(): String = getFingerprint("recommend")
    fun recommendFingerprintMatches(fp: String): Boolean = fp == getFingerprint("recommend") && fp.isNotEmpty()

    fun getCachedRecommend(): List<Song>? = parseSongs(getJson("recommend"))

    fun saveRecommend(songs: List<Song>) {
        val fp = fp(*songs.map { it.id.toString() }.toTypedArray())
        val json = JSONArray().apply {
            songs.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("artists", s.artists)
                    put("album", s.album)
                    put("coverUrl", s.coverUrl)
                })
            }
        }.toString()
        saveWithFingerprint("recommend", fp, json)
    }

    private fun parseSongs(json: String?): List<Song>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Song(
                    id = o.optLong("id"),
                    name = o.optString("name"),
                    artists = o.optString("artists"),
                    album = o.optString("album"),
                    coverUrl = o.optString("coverUrl")
                )
            }
        } catch (_: Exception) { null }
    }

    // ── Cloud Songs ──

    fun isCloudSongsCacheValid(): Boolean = isCacheValid("cloudsongs", CACHE_VALIDITY_CLOUD_MS)

    fun cloudSongsFingerprint(cloudSongs: List<CloudApi.CloudSong>): String =
        fp(*cloudSongs.map { it.id.toString() }.toTypedArray())

    fun getCloudSongsFingerprint(): String = getFingerprint("cloudsongs")
    fun cloudSongsFingerprintMatches(fp: String): Boolean = fp == getFingerprint("cloudsongs") && fp.isNotEmpty()

    fun getCachedCloudSongs(): List<CloudApi.CloudSong>? = parseCloudSongs(getJson("cloudsongs"))

    fun saveCloudSongs(cloudSongs: List<CloudApi.CloudSong>) {
        val fp = fp(*cloudSongs.map { it.id.toString() }.toTypedArray())
        val json = JSONArray().apply {
            cloudSongs.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("artists", c.artists)
                    put("album", c.album)
                    put("coverUrl", c.coverUrl)
                })
            }
        }.toString()
        saveWithFingerprint("cloudsongs", fp, json)
    }

    private fun parseCloudSongs(json: String?): List<CloudApi.CloudSong>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CloudApi.CloudSong(
                    id = o.optLong("id"),
                    name = o.optString("name"),
                    artists = o.optString("artists"),
                    album = o.optString("album"),
                    coverUrl = o.optString("coverUrl")
                )
            }
        } catch (_: Exception) { null }
    }

    // ── Clear ──

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
